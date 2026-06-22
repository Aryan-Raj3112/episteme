package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import java.awt.Color
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopFolderMetadataExtractorTest {
    @Test
    fun `direct imported epub gets text metadata and embedded cover`() = withCoverCacheDir { tempDir ->
        val epub = File(tempDir, "direct.epub")
        writeEpub(
            target = epub,
            opf = """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>Direct EPUB</dc:title>
                    <dc:creator>Ada Lovelace</dc:creator>
                    <dc:description>&lt;p&gt;Metadata summary&lt;/p&gt;</dc:description>
                    <meta name="calibre:series" content="Computing Notes" />
                    <meta name="calibre:series_index" content="2" />
                    <meta name="cover" content="cover-image" />
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="images/cover.png" media-type="image/png" />
                  </manifest>
                </package>
            """.trimIndent()
        )
        val book = bookFor(epub, FileType.EPUB)

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("Direct EPUB", enriched.title)
        assertEquals("Ada Lovelace", enriched.author)
        assertEquals("<p>Metadata summary</p>", enriched.description)
        assertEquals("Computing Notes", enriched.seriesName)
        assertEquals(2.0, enriched.seriesIndex)
        assertEquals("Direct EPUB", enriched.originalTitle)
        assertEquals("Ada Lovelace", enriched.originalAuthor)
        assertEquals("Computing Notes", enriched.originalSeriesName)
        assertEquals(2.0, enriched.originalSeriesIndex)
        assertEquals("<p>Metadata summary</p>", enriched.originalDescription)
        assertEquals(epub.lastModified(), enriched.fileContentModifiedTimestamp)
        assertTrue(enriched.folderTextMetadataParsed)
        assertTrue(File(assertNotNull(enriched.coverImagePath)).isFile)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `opened epub gets embedded cover`() = withCoverCacheDir { tempDir ->
        val epub = File(tempDir, "opened.epub")
        writeEpub(
            target = epub,
            opf = """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>Opened EPUB</dc:title>
                    <dc:creator>Mary Shelley</dc:creator>
                    <meta name="cover" content="cover-image" />
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="images/cover.png" media-type="image/png" />
                  </manifest>
                </package>
            """.trimIndent()
        )
        val book = bookFor(epub, FileType.EPUB, title = null)

        val enriched = DesktopFolderMetadataExtractor.enrichOpenedBook(book)

        assertEquals("Opened EPUB", enriched.title)
        assertEquals("Mary Shelley", enriched.author)
        assertTrue(enriched.folderTextMetadataParsed)
        assertTrue(File(assertNotNull(enriched.coverImagePath)).isFile)
    }
    @Test
    fun `opened epub preserves existing file cover path`() = withCoverCacheDir { tempDir ->
        val epub = File(tempDir, "existing-cover.epub")
        writeEpubWithoutCoverMetadata(epub)
        val existingCover = File(tempDir, "existing.png").apply { writeBytes(onePixelPngBytes()) }
        val book = bookFor(epub, FileType.EPUB, title = null).copy(coverImagePath = existingCover.absolutePath)

        val enriched = DesktopFolderMetadataExtractor.enrichOpenedBook(book)

        assertEquals(existingCover.absolutePath, enriched.coverImagePath)
        assertTrue(existingCover.isFile)
        assertTrue(enriched.folderTextMetadataParsed)
    }

    @Test
    fun `direct imported epub without embedded cover does not get content preview cover`() = withCoverCacheDir { tempDir ->
        val epub = File(tempDir, "no-cover.epub")
        writeEpubWithoutCoverMetadata(epub)
        val book = bookFor(epub, FileType.EPUB, title = null)

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("No Cover EPUB", enriched.title)
        assertTrue(enriched.folderTextMetadataParsed)
        assertEquals(null, enriched.coverImagePath)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(0, result.stats.coversUpdated)
    }

    @Test
    fun `direct imported text file gets content preview cover`() = withCoverCacheDir { tempDir ->
        val textFile = File(tempDir, "notes.txt").apply { writeText("Notes") }
        val book = bookFor(textFile, FileType.TXT, title = "Notes")

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("Notes", enriched.title)
        assertFalse(enriched.folderTextMetadataParsed)
        assertContentPreviewCover(enriched.coverImagePath)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `direct imported docx gets text metadata and content preview cover`() = withCoverCacheDir { tempDir ->
        val docx = File(tempDir, "direct.docx")
        writeDocx(
            target = docx,
            title = "Direct DOCX",
            author = "Grace Hopper",
            bodyText = "Portable desktop document text."
        )
        val book = bookFor(docx, FileType.DOCX, title = null)

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("Direct DOCX", enriched.title)
        assertEquals("Grace Hopper", enriched.author)
        assertTrue(enriched.folderTextMetadataParsed)
        assertContentPreviewCover(enriched.coverImagePath)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `direct imported html without cover gets content preview cover`() = withCoverCacheDir { tempDir ->
        val html = File(tempDir, "article.html").apply {
            writeText("""
                <html>
                  <head><title>HTML Article</title></head>
                  <body><h1>Chapter One</h1><p>Visible HTML content for the thumbnail.</p></body>
                </html>
            """.trimIndent())
        }
        val book = bookFor(html, FileType.HTML, title = null)

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("HTML Article", enriched.title)
        assertTrue(enriched.folderTextMetadataParsed)
        assertContentPreviewCover(enriched.coverImagePath)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `direct imported html replaces legacy generated cover path with content preview cover`() = withCoverCacheDir { tempDir ->
        val html = File(tempDir, "legacy.html").apply {
            writeText("""
                <html>
                  <head><title>Legacy HTML</title></head>
                  <body><p>Legacy cover should be replaced with content text.</p></body>
                </html>
            """.trimIndent())
        }
        val legacyCover = legacyCoverFileFor(tempDir, html).apply {
            parentFile.mkdirs()
            writeBytes(onePixelPngBytes())
        }
        val book = bookFor(html, FileType.HTML, title = null).copy(
            coverImagePath = legacyCover.absolutePath,
            folderTextMetadataParsed = true
        )

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertTrue(enriched.folderTextMetadataParsed)
        assertContentPreviewCover(enriched.coverImagePath)
        assertTrue(assertNotNull(enriched.coverImagePath).contains("content_cover_"))
        assertFalse(legacyCover.isFile)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `opened odt without package thumbnail gets content preview cover`() = withCoverCacheDir { tempDir ->
        val odt = File(tempDir, "opened.odt")
        writeOdt(odt, "Visible ODT content for the thumbnail.")
        val book = bookFor(odt, FileType.ODT, title = "opened")

        val enriched = DesktopFolderMetadataExtractor.enrichOpenedBook(book)

        assertTrue(enriched.folderTextMetadataParsed)
        assertContentPreviewCover(enriched.coverImagePath)
    }
    private fun withCoverCacheDir(block: (File) -> Unit) {
        val tempDir = Files.createTempDirectory("reader-desktop-covers").toFile()
        val oldCacheDir = System.getProperty("reader.cover.cache.dir")
        System.setProperty("reader.cover.cache.dir", File(tempDir, "covers").absolutePath)
        try {
            block(tempDir)
        } finally {
            if (oldCacheDir == null) {
                System.clearProperty("reader.cover.cache.dir")
            } else {
                System.setProperty("reader.cover.cache.dir", oldCacheDir)
            }
            tempDir.deleteRecursively()
        }
    }

    private fun bookFor(
        file: File,
        type: FileType,
        title: String? = file.nameWithoutExtension
    ): BookItem {
        return BookItem(
            id = file.absolutePath,
            path = file.absolutePath,
            type = type,
            displayName = file.name,
            timestamp = 1L,
            title = title,
            fileSize = file.length(),
            isRecent = false
        )
    }

    private fun writeEpub(target: File, opf: String) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putText(
                "META-INF/container.xml",
                """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" />
                      </rootfiles>
                    </container>
                """.trimIndent()
            )
            zip.putText("OEBPS/content.opf", opf)
            zip.putBytes("OEBPS/images/cover.png", onePixelPngBytes())
        }
    }

    private fun legacyCoverFileFor(tempDir: File, file: File): File {
        val hash = Integer.toUnsignedString(file.absolutePath.hashCode())
        return File(File(tempDir, "covers"), "cover_$hash.png")
    }
    private fun writeEpubWithoutCoverMetadata(target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putText(
                "META-INF/container.xml",
                """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" />
                      </rootfiles>
                    </container>
                """.trimIndent()
            )
            zip.putText(
                "OEBPS/content.opf",
                """
                    <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <metadata>
                        <dc:title>No Cover EPUB</dc:title>
                      </metadata>
                      <manifest>
                        <item id="chap1" href="chapter.xhtml" media-type="application/xhtml+xml" />
                      </manifest>
                    </package>
                """.trimIndent()
            )
            zip.putText("OEBPS/chapter.xhtml", "<html><body><p>Readable EPUB text.</p></body></html>")
        }
    }

    private fun writeOdt(target: File, bodyText: String) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putText(
                "content.xml",
                """
                    <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                      <office:body><office:text><text:p>$bodyText</text:p></office:text></office:body>
                    </office:document-content>
                """.trimIndent()
            )
        }
    }

    private fun assertContentPreviewCover(path: String?) {
        val coverFile = File(assertNotNull(path))
        assertTrue(coverFile.isFile)
        val image = ImageIO.read(coverFile)
        assertEquals(480, image.width)
        assertEquals(720, image.height)
        assertEquals(Color(226, 220, 209).rgb, image.getRGB(9, 10))
    }

    private fun writeDocx(target: File, title: String, author: String, bodyText: String) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putText(
                "docProps/core.xml",
                """
                    <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                        xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>$title</dc:title>
                      <dc:creator>$author</dc:creator>
                    </cp:coreProperties>
                """.trimIndent()
            )
            zip.putText(
                "word/document.xml",
                """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>$bodyText</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                """.trimIndent()
            )
        }
    }

    private fun ZipOutputStream.putText(name: String, value: String) {
        putBytes(name, value.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.putBytes(name: String, value: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(value)
        closeEntry()
    }

    private fun onePixelPngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        )
    }
}
