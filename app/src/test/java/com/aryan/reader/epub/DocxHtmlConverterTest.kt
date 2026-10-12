package com.aryan.reader.epub

import android.content.Context
import com.aryan.reader.FileType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows

/**
 * [DocxHtmlConverter] routing: native bridge first, mammoth fallback.
 *
 * The fakes stand in for the real bridge, which needs a device — its `.so`
 * only ships in the APK. The last test goes through [SingleFileImporter] with
 * the real bridge, which is unavailable on the JVM, so it proves the fallback
 * keeps DOCX import working wherever native code cannot load.
 */
class DocxHtmlConverterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `oxide html is used when the bridge succeeds`() {
        val file = temp.newFile("source.docx")
        file.writeBytes("fake-docx-bytes".toByteArray())
        var rendered: ByteArray? = null
        val oxide = DocxHtmlConverter.OxideRenderer { bytes ->
            rendered = bytes
            "<p>Hello <strong>bold</strong></p>"
        }

        val html = DocxHtmlConverter.convert(file, "Hello.docx", oxide)

        assertEquals("<p>Hello <strong>bold</strong></p>", html)
        assertArrayEquals("fake-docx-bytes".toByteArray(), rendered)
    }

    @Test
    fun `null oxide output falls back to mammoth`() {
        val file = writeMinimalDocx("fallback-null.docx", "Fallback")

        val html = DocxHtmlConverter.convert(file, "Fallback.docx", DocxHtmlConverter.OxideRenderer { null })

        assertTrue(html.contains("Fallback"))
        assertTrue(html.contains("bold"))
        assertTrue(html.contains("<strong>"))
    }

    @Test
    fun `throwing oxide falls back to mammoth`() {
        val file = writeMinimalDocx("fallback-throw.docx", "Fallback")
        val oxide = DocxHtmlConverter.OxideRenderer { throw RuntimeException("bridge boom") }

        val html = DocxHtmlConverter.convert(file, "Fallback.docx", oxide)

        assertTrue(html.contains("Fallback"))
        assertTrue(html.contains("bold"))
    }

    @Test
    fun `blank oxide output falls back to mammoth`() {
        val file = writeMinimalDocx("fallback-blank.docx", "Fallback")
        val oxide = DocxHtmlConverter.OxideRenderer { "  " }

        val html = DocxHtmlConverter.convert(file, "Fallback.docx", oxide)

        assertTrue(html.contains("Fallback"))
        assertTrue(html.contains("bold"))
    }

    @Test
    fun `disabled flag skips oxide entirely`() {
        val file = writeMinimalDocx("flag-off.docx", "Flagged")
        var called = false
        val oxide = DocxHtmlConverter.OxideRenderer {
            called = true
            "<p>sentinel</p>"
        }
        try {
            DocxHtmlConverter.preferOfficeOxide = false

            val html = DocxHtmlConverter.convert(file, "Flagged.docx", oxide)

            assertFalse(called)
            assertFalse(html.contains("sentinel"))
            assertTrue(html.contains("Flagged"))
            assertTrue(html.contains("<strong>"))
        } finally {
            DocxHtmlConverter.preferOfficeOxide = true
        }
    }

    @Test
    fun `image gate falls back when oxide drops embedded images`() {
        val file = writeImageDocx("gate-fallback.docx")
        // Oxide succeeded on text but lost the picture — the exact office_oxide
        // 0.1.13 behaviour for align-positioned floating anchors.
        val oxide = DocxHtmlConverter.OxideRenderer { "<p>Pictured Hello</p>" }

        val html = DocxHtmlConverter.convert(file, "Pictured.docx", oxide)

        assertTrue(html.contains("Pictured Hello"))
        assertTrue("mammoth should embed the dropped picture", html.contains("data:image/png;base64,"))
    }

    @Test
    fun `image gate keeps oxide html that already embeds images`() {
        val file = writeImageDocx("gate-kept.docx")
        val oxideHtml = "<p>Pictured</p><img src=\"data:image/png;base64,AAA=\" alt=\"\" />"
        val oxide = DocxHtmlConverter.OxideRenderer { oxideHtml }

        assertEquals(oxideHtml, DocxHtmlConverter.convert(file, "Pictured.docx", oxide))
    }

    @Test
    fun `docx import falls back to mammoth when the native bridge is unavailable`() = runTest {
        val importer = SingleFileImporter(contextWithCache(temp.newFolder("docx-fallback-cache")))

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream(minimalDocxBytes("Fallback")),
            type = FileType.DOCX,
            originalBookNameHint = "Fallback.docx",
            bookId = "docx-fallback-book",
        )

        assertEquals("Fallback", book.title)
        assertEquals(1, book.chapters.size)
        val chapter = book.chapters.single()
        assertTrue(chapter.plainTextContent.contains("Fallback"))
        assertTrue(chapter.plainTextContent.contains("bold"))
    }

    @Test
    fun `doc converts through oxide with no mammoth fallback`() {
        val file = temp.newFile("source.doc")
        file.writeBytes("fake-doc-bytes".toByteArray())
        var rendered: ByteArray? = null
        val oxide = DocxHtmlConverter.OxideRenderer { bytes ->
            rendered = bytes
            "<p>Legacy</p><hr /><hr />" +
                "<img src=\"data:image/x-emf;base64,QUJD\" alt=\"\" />" +
                "<img src=\"data:image/jpeg;base64,/9j/\" alt=\"\" />"
        }

        val html = DocxHtmlConverter.convertDoc(file, "Legacy.doc", oxide)

        assertEquals(
            "<p>Legacy</p><hr /><img src=\"data:image/jpeg;base64,/9j/\" alt=\"\" />",
            html
        )
        assertArrayEquals("fake-doc-bytes".toByteArray(), rendered)
    }

    @Test
    fun `doc throws a clean error when oxide cannot read it`() {
        val file = temp.newFile("unreadable.doc")
        file.writeBytes("not a document".toByteArray())

        val error = assertThrows(IllegalStateException::class.java) {
            DocxHtmlConverter.convertDoc(file, "Unreadable.doc", DocxHtmlConverter.OxideRenderer { null })
        }
        assertTrue(error.message!!.contains("DOC"))
    }

    @Test
    fun `doc strips metafile images but keeps raster ones`() {
        val html = "<p>a</p>" +
            "<img src=\"data:image/x-emf;base64,QUJD\" alt=\"\" />" +
            "<img src=\"data:image/jpeg;base64,/9j/\" alt=\"\" />" +
            "<img src=\"data:image/x-wmf;base64,QUJD\" alt=\"\" />" +
            "<img src=\"data:image/png;base64,iVBOR\" alt=\"\" />" +
            "<img src=\"data:image/tiff;base64,SUks\" alt=\"\" />"

        val cleaned = stripUnrenderableImages(html, "charts.doc")

        assertTrue(cleaned.contains("data:image/jpeg;base64"))
        assertTrue(cleaned.contains("data:image/png;base64"))
        assertFalse(cleaned.contains("x-emf"))
        assertFalse(cleaned.contains("x-wmf"))
        assertFalse(cleaned.contains("image/tiff"))
    }

    @Test
    fun `doc with disabled flag throws instead of calling oxide`() {
        val file = temp.newFile("flag-off.doc")
        file.writeBytes("fake-doc-bytes".toByteArray())
        var called = false
        val oxide = DocxHtmlConverter.OxideRenderer {
            called = true
            "<p>sentinel</p>"
        }
        try {
            DocxHtmlConverter.preferOfficeOxide = false
            assertThrows(IllegalStateException::class.java) {
                DocxHtmlConverter.convertDoc(file, "Flagged.doc", oxide)
            }
            assertFalse(called)
        } finally {
            DocxHtmlConverter.preferOfficeOxide = true
        }
    }

    @Test
    fun `redundant horizontal rules collapse to one`() {
        assertEquals(
            "<p>a</p><hr /><p>b</p>",
            collapseRedundantBreaks("<p>a</p><hr /><hr/>\n<HR><p>b</p>")
        )
        assertEquals(
            "<p>a</p><hr /><p>b</p>",
            collapseRedundantBreaks("<p>a</p><hr /><p>b</p>")
        )
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return context
    }

    private fun writeMinimalDocx(name: String, bodyWord: String): File {
        val file = temp.newFile(name)
        file.writeBytes(minimalDocxBytes(bodyWord))
        return file
    }

    /** A DOCX with one inline 1px PNG, wired through rels like Word writes it. */
    private fun writeImageDocx(name: String): File {
        val png = java.util.Base64.getDecoder().decode(ONE_PX_PNG_BASE64)
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putAndWrite("[Content_Types].xml", CONTENT_TYPES_WITH_PNG)
            zip.putAndWrite("_rels/.rels", RELS)
            zip.putAndWrite("word/document.xml", IMAGE_DOCUMENT)
            zip.putAndWrite("word/_rels/document.xml.rels", IMAGE_RELS)
            zip.putNextEntry(ZipEntry("word/media/image1.png"))
            zip.write(png)
            zip.closeEntry()
        }
        val file = temp.newFile(name)
        file.writeBytes(buffer.toByteArray())
        return file
    }

    private fun minimalDocxBytes(bodyWord: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putAndWrite("[Content_Types].xml", CONTENT_TYPES)
            zip.putAndWrite("_rels/.rels", RELS)
            zip.putAndWrite("word/document.xml", documentXml(bodyWord))
        }
        return buffer.toByteArray()
    }

    private fun ZipOutputStream.putAndWrite(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun documentXml(bodyWord: String): String =
        DOCUMENT_OPEN +
            "<w:p><w:r><w:t xml:space=\"preserve\">$bodyWord </w:t></w:r>" +
            "<w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r></w:p>" +
            DOCUMENT_CLOSE

    companion object {
        private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\"" +
            " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\"" +
            " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"

        private const val RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\"" +
            " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"" +
            " Target=\"word/document.xml\"/>" +
            "</Relationships>"

        private const val DOCUMENT_OPEN = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>"

        private const val DOCUMENT_CLOSE = "</w:body></w:document>"

        private const val ONE_PX_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

        private const val CONTENT_TYPES_WITH_PNG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\"" +
            " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
            "<Override PartName=\"/word/document.xml\"" +
            " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"

        private const val IMAGE_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rIdImg\"" +
            " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\"" +
            " Target=\"media/image1.png\"/>" +
            "</Relationships>"

        private const val IMAGE_DOCUMENT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"" +
            " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"" +
            " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">" +
            "<w:body>" +
            "<w:p><w:r><w:t xml:space=\"preserve\">Pictured Hello</w:t></w:r></w:p>" +
            "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"9525\" cy=\"9525\"/>" +
            "<wp:docPr id=\"1\" name=\"Picture 1\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:nvPicPr><pic:cNvPr id=\"1\" name=\"image1.png\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdImg\"" +
            " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" +
            "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>" +
            "<a:ext cx=\"9525\" cy=\"9525\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic>" +
            "</wp:inline></w:drawing></w:r></w:p>" +
            "</w:body></w:document>"
    }
}
