package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.shared.FileType
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedJvmBookLoaderTest {
    @Test
    fun `docx loader extracts core metadata and body text`() = withTempDir { dir ->
        val file = File(dir, "sample.docx")
        writeZip(file) {
            text(
                "docProps/core.xml",
                """
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>Portable DOCX</dc:title>
                  <dc:creator>Casey Writer</dc:creator>
                </cp:coreProperties>
                """.trimIndent()
            )
            text(
                "word/document.xml",
                """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>Hello from DOCX.</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.trimIndent()
            )
        }

        val book = SharedJvmBookLoader.load(file, FileType.DOCX)

        assertEquals("Portable DOCX", book.title)
        assertEquals("Casey Writer", book.author)
        assertTrue(book.chapters.single().plainText.contains("Hello from DOCX."))
    }

    @Test
    fun `odt loader extracts metadata and document text`() = withTempDir { dir ->
        val file = File(dir, "sample.odt")
        writeZip(file) {
            text(
                "meta.xml",
                """
                <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <office:meta>
                    <dc:title>Portable ODT</dc:title>
                    <dc:creator>Open Author</dc:creator>
                  </office:meta>
                </office:document-meta>
                """.trimIndent()
            )
            text(
                "content.xml",
                """
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body>
                    <office:text>
                      <text:h text:outline-level="1">ODT Heading</text:h>
                      <text:p>Hello from ODT.</text:p>
                    </office:text>
                  </office:body>
                </office:document-content>
                """.trimIndent()
            )
        }

        val book = SharedJvmBookLoader.load(file, FileType.ODT)

        assertEquals("Portable ODT", book.title)
        assertEquals("Open Author", book.author)
        assertTrue(book.chapters.single().plainText.contains("Hello from ODT."))
    }

    @Test
    fun `fb2 loader splits readable sections`() = withTempDir { dir ->
        val file = File(dir, "sample.fb2").apply {
            writeText(
                """
                <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <author><first-name>Ada</first-name><last-name>Byron</last-name></author>
                      <book-title>Portable FB2</book-title>
                    </title-info>
                  </description>
                  <body>
                    <section>
                      <title><p>First Section</p></title>
                      <p>Hello from FB2.</p>
                    </section>
                  </body>
                </FictionBook>
                """.trimIndent()
            )
        }

        val book = SharedJvmBookLoader.load(file, FileType.FB2)

        assertEquals("Portable FB2", book.title)
        assertEquals("Ada Byron", book.author)
        assertEquals("First Section", book.chapters.single().title)
        assertTrue(book.chapters.single().plainText.contains("Hello from FB2."))
    }

    @Test
    fun `mobi loader reads uncompressed palmdoc text records`() = withTempDir { dir ->
        val file = File(dir, "sample.mobi").apply {
            writeBytes(
                minimalMobi(
                    "<html><body><p>Hello from MOBI.</p></body></html>".toByteArray(Charsets.UTF_8)
                )
            )
        }

        val book = SharedJvmBookLoader.load(file, FileType.MOBI)

        assertEquals("sample", book.title)
        assertTrue(book.chapters.single().plainText.contains("Hello from MOBI."))
    }

    @Test
    fun `mobi loader reads bundled huff cdic sample`() {
        val file = findRepoFile("app/src/main/cpp/libmobi/tests/samples/sample-unicode-huffdic.mobi")

        val book = SharedJvmBookLoader.load(file, FileType.MOBI)

        assertEquals("Libmobi", book.title)
        assertTrue(book.chapters.joinToString("\n") { it.plainText }.length > 100)
    }

    @Test
    fun `html loader splits generated pdf reflow page breaks`() = withTempDir { dir ->
        val file = File(dir, "source_reflow.html").apply {
            writeText(
                """
                <!DOCTYPE html>
                <html>
                <head><title>Source (Reflow)</title></head>
                <body>
                <section><p class="page-marker">-- Page 1 --</p><p>First page text.</p></section>
                <page-break></page-break>
                <section><p class="page-marker">-- Page 2 --</p><p>Second page text.</p></section>
                </body>
                </html>
                """.trimIndent()
            )
        }

        val book = SharedJvmBookLoader.load(file, FileType.HTML)

        assertEquals("Source (Reflow)", book.title)
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].plainText.contains("First page text."))
        assertEquals("Page 2", book.chapters[1].title)
        assertTrue(book.chapters[1].plainText.contains("Second page text."))
    }

    @Test
    fun `epub loader keeps embedded images in semantic pagination blocks`() = withTempDir { dir ->
        val file = File(dir, "image-book.epub")
        writeImageEpub(file)

        val book = SharedJvmBookLoader.loadEpub(file)
        val image = book.chapters.single().semanticBlocks.filterIsInstance<SemanticImage>().single()

        assertTrue(image.path.startsWith("data:image/png;base64,"))
        assertEquals("Pixel", image.altText)
    }

    @Test
    fun `epub loader can skip semantic blocks for vertical fast path`() = withTempDir { dir ->
        val file = File(dir, "image-book.epub")
        writeImageEpub(file)

        val book = SharedJvmBookLoader.loadEpub(file, parseSemanticBlocks = false)
        val chapter = book.chapters.single()

        assertTrue(chapter.semanticBlocks.isEmpty())
        assertTrue(chapter.htmlContent.contains("data:image/png;base64,"))
        assertTrue(chapter.plainText.contains("Before"))
        assertTrue(chapter.plainText.contains("After"))
    }

    @Test
    fun `epub loader can prepare html for selected vertical chapters only`() = withTempDir { dir ->
        val file = File(dir, "two-chapters.epub")
        writeTwoChapterEpub(file)

        val book = SharedJvmBookLoader.loadEpub(
            file = file,
            parseSemanticBlocks = false,
            preparedHtmlChapterRange = 1..1
        )

        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].plainText.contains("First chapter text"))
        assertTrue(book.chapters[0].htmlContent.isBlank())
        assertTrue(book.chapters[1].plainText.contains("Second chapter text"))
        assertTrue(book.chapters[1].htmlContent.contains("Second chapter text"))
    }

    @Test
    fun `epub loader does not inline stylesheet font resources`() = withTempDir { dir ->
        val file = File(dir, "font-book.epub")
        writeImageEpub(file)

        val css = SharedJvmBookLoader.loadEpub(file).css.values.single()

        assertTrue(css.contains("fonts/reader.woff2"))
        assertTrue(!css.contains("data:font/woff2"))
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("reader-shared-loader").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun findRepoFile(path: String): File {
        return generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(8)
            .map { File(it, path) }
            .firstOrNull { it.isFile }
            ?: error("Missing test fixture: $path")
    }

    private fun writeZip(file: File, block: ZipBuilder.() -> Unit) {
        ZipOutputStream(file.outputStream()).use { zip ->
            ZipBuilder(zip).block()
        }
    }

    private fun writeImageEpub(file: File) {
        writeZip(file) {
            text(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path="OPS/content.opf"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            )
            text(
                "OPS/content.opf",
                """
                <package>
                  <metadata>
                    <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Image Book</dc:title>
                  </metadata>
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="style" href="styles/book.css" media-type="text/css"/>
                    <item id="font" href="styles/fonts/reader.woff2" media-type="font/woff2"/>
                    <item id="pixel" href="images/pixel.png" media-type="image/png"/>
                  </manifest>
                  <spine>
                    <itemref idref="chapter"/>
                  </spine>
                </package>
                """.trimIndent()
            )
            text(
                "OPS/chapter.xhtml",
                """
                <html>
                  <body>
                    <h1>One</h1>
                    <p>Before</p>
                    <img src="images/pixel.png" alt="Pixel"/>
                    <p>After</p>
                  </body>
                </html>
                """.trimIndent()
            )
            text(
                "OPS/styles/book.css",
                """
                @font-face {
                  font-family: "Fixture Serif";
                  src: url("fonts/reader.woff2") format("woff2");
                }
                body { font-family: "Fixture Serif"; }
                """.trimIndent()
            )
            bytes("OPS/images/pixel.png", onePixelPng)
            bytes("OPS/styles/fonts/reader.woff2", byteArrayOf(0, 1, 2, 3))
        }
    }

    private fun writeTwoChapterEpub(file: File) {
        writeZip(file) {
            text(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path="OPS/content.opf"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            )
            text(
                "OPS/content.opf",
                """
                <package>
                  <metadata>
                    <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Two Chapters</dc:title>
                  </metadata>
                  <manifest>
                    <item id="first" href="first.xhtml" media-type="application/xhtml+xml"/>
                    <item id="second" href="second.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="first"/>
                    <itemref idref="second"/>
                  </spine>
                </package>
                """.trimIndent()
            )
            text(
                "OPS/first.xhtml",
                """
                <html><body><h1>First</h1><p>First chapter text.</p></body></html>
                """.trimIndent()
            )
            text(
                "OPS/second.xhtml",
                """
                <html><body><h1>Second</h1><p>Second chapter text.</p></body></html>
                """.trimIndent()
            )
        }
    }

    private fun minimalMobi(textRecord: ByteArray): ByteArray {
        val record0 = ByteArray(16)
        record0.writeU16(0, 1)
        record0.writeU32(4, textRecord.size)
        record0.writeU16(8, 1)
        record0.writeU16(10, 4096)
        record0.writeU16(12, 0)

        val record0Offset = 78 + 16
        val record1Offset = record0Offset + record0.size
        val header = ByteArray(record0Offset)
        header.writeU16(76, 2)
        header.writeU32(78, record0Offset)
        header.writeU32(86, record1Offset)
        return header + record0 + textRecord
    }

    private fun ByteArray.writeU16(offset: Int, value: Int) {
        this[offset] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 1] = (value and 0xFF).toByte()
    }

    private fun ByteArray.writeU32(offset: Int, value: Int) {
        this[offset] = ((value ushr 24) and 0xFF).toByte()
        this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 3] = (value and 0xFF).toByte()
    }

    private class ZipBuilder(private val zip: ZipOutputStream) {
        fun text(path: String, value: String) {
            bytes(path, value.toByteArray(Charsets.UTF_8))
        }

        fun bytes(path: String, value: ByteArray) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(value)
            zip.closeEntry()
        }
    }

    private val onePixelPng: ByteArray =
        Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=")
}
