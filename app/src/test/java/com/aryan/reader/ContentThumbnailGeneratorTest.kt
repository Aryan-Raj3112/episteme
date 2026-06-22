package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.RecentFileItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ContentThumbnailGeneratorTest {
    private lateinit var root: File
    private lateinit var generator: ContentThumbnailGenerator

    @Before
    fun setUp() {
        root = Files.createTempDirectory("reader-content-thumbnails").toFile()
        generator = ContentThumbnailGenerator(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `html without cover gets content thumbnail`() = runTest {
        val file = root.resolve("chapter.html").apply {
            writeText(
                """
                    <!doctype html>
                    <html><head><title>Ignored title</title></head>
                    <body><h1>Opening Chapter</h1><p>This paragraph should appear in the preview thumbnail.</p></body></html>
                """.trimIndent()
            )
        }

        val thumbnail = generator.generate(itemFor(file, FileType.HTML))

        assertNotNull(thumbnail)
        thumbnail?.recycle()
    }

    @Test
    fun `odt without embedded package thumbnail gets content thumbnail`() = runTest {
        val file = root.resolve("notes.odt")
        writeZip(
            file,
            "content.xml" to """
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:text><text:p>ODT body text for the generated content preview.</text:p></office:text></office:body>
                </office:document-content>
            """.trimIndent()
        )

        val thumbnail = generator.generate(itemFor(file, FileType.ODT))

        assertNotNull(thumbnail)
        thumbnail?.recycle()
    }

    @Test
    fun `docx gets content thumbnail from document body`() = runTest {
        val file = root.resolve("draft.docx")
        writeZip(
            file,
            "word/document.xml" to """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>DOCX body text for the generated content preview.</w:t></w:r></w:p></w:body>
                </w:document>
            """.trimIndent()
        )

        val thumbnail = generator.generate(itemFor(file, FileType.DOCX))

        assertNotNull(thumbnail)
        thumbnail?.recycle()
    }

    @Test
    fun `epub does not get generated content thumbnail fallback`() = runTest {
        val file = root.resolve("no-cover.epub")
        writeZip(
            file,
            "META-INF/container.xml" to """
                <container>
                  <rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles>
                </container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata><dc:title>No Cover EPUB</dc:title></metadata>
                  <manifest><item id="chap1" href="chapter.xhtml" media-type="application/xhtml+xml" /></manifest>
                </package>
            """.trimIndent(),
            "OEBPS/chapter.xhtml" to "<html><body><p>Readable EPUB content should not become a cover.</p></body></html>"
        )

        val thumbnail = generator.generate(itemFor(file, FileType.EPUB))

        assertNull(thumbnail)
    }
    private fun itemFor(file: File, type: FileType): RecentFileItem {
        return RecentFileItem(
            bookId = file.name,
            uriString = Uri.fromFile(file).toString(),
            type = type,
            displayName = file.name,
            timestamp = 1L,
            isRecent = false,
            sourceFolderUri = "content://folder"
        )
    }

    private fun writeZip(target: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
