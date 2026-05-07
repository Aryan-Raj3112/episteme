package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        assertTrue(enriched.folderTextMetadataParsed)
        assertTrue(File(assertNotNull(enriched.coverImagePath)).isFile)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
    }

    @Test
    fun `direct imported text file gets generated cover`() = withCoverCacheDir { tempDir ->
        val textFile = File(tempDir, "notes.txt").apply { writeText("Notes") }
        val book = bookFor(textFile, FileType.TXT, title = "Notes")

        val result = DesktopFolderMetadataExtractor.enrichImportedBooks(
            books = listOf(book),
            importedBookIds = setOf(book.id)
        )

        val enriched = result.books.single()
        assertEquals("Notes", enriched.title)
        assertFalse(enriched.folderTextMetadataParsed)
        assertTrue(File(assertNotNull(enriched.coverImagePath)).isFile)
        assertEquals(1, result.stats.updatedBooks)
        assertEquals(1, result.stats.coversUpdated)
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
