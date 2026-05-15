package com.aryan.reader.shared.reader

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedEpubMetadataEditorTest {
    @Test
    fun `rewrite updates existing OPF metadata`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(source, metadata = """
            <metadata>
              <dc:title>Old</dc:title>
              <dc:creator>Old Author</dc:creator>
              <dc:description>Old summary</dc:description>
              <meta name="calibre:series" content="Old Series" />
              <meta name="calibre:series_index" content="1" />
            </metadata>
        """.trimIndent())

        val result = SharedEpubMetadataEditor.rewrite(
            source = source,
            destination = output,
            update = update()
        )

        assertEquals("New Title", result.title)
        assertEquals("New Author", result.author)
        assertEquals("New summary", result.description)
        assertEquals("New Series", result.seriesName)
        assertEquals(2.5, result.seriesIndex)
        assertEquals(result, SharedEpubMetadataEditor.readMetadata(output))
    }

    @Test
    fun `rewrite creates missing metadata elements`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(source, metadata = "<metadata />")

        val result = SharedEpubMetadataEditor.rewrite(source, output, update())

        assertEquals("New Title", result.title)
        assertEquals("New Author", result.author)
        assertEquals("New summary", result.description)
        assertEquals("New Series", result.seriesName)
        assertEquals(2.5, result.seriesIndex)
    }

    @Test
    fun `rewrite preserves non OPF zip entries`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(source)

        SharedEpubMetadataEditor.rewrite(source, output, update())

        ZipFile(output).use { zip ->
            assertEquals("chapter", zip.getInputStream(assertNotNull(zip.getEntry("OEBPS/chapter.xhtml"))).reader().readText())
        }
    }

    @Test
    fun `rewrite keeps mimetype first and stored`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(source)

        SharedEpubMetadataEditor.rewrite(source, output, update())

        ZipInputStream(output.inputStream()).use { zip ->
            val first = assertNotNull(zip.nextEntry)
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
        }
    }

    @Test
    fun `rewrite in place rejects invalid epub without replacing source`() = withTempDir { dir ->
        val source = File(dir, "broken.epub").apply { writeText("not an epub") }
        val backup = File(dir, "backup.epub")

        assertFailsWith<Exception> {
            SharedEpubMetadataEditor.rewriteInPlace(source, backup, update())
        }

        assertEquals("not an epub", source.readText())
        assertTrue(!backup.exists())
    }

    private fun update(): SharedEpubMetadataUpdate {
        return SharedEpubMetadataUpdate(
            title = "New Title",
            author = "New Author",
            description = "New summary",
            seriesName = "New Series",
            seriesIndex = 2.5
        )
    }

    private fun writeEpub(
        target: File,
        metadata: String = """
            <metadata>
              <dc:title>Old</dc:title>
            </metadata>
        """.trimIndent()
    ) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles></container>"""
            )
            zip.putText(
                "OEBPS/content.opf",
                """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  $metadata
                  <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" /></manifest>
                  <spine><itemref idref="chapter" /></spine>
                </package>
                """.trimIndent()
            )
            zip.putText("OEBPS/chapter.xhtml", "chapter")
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putStoredText(name: String, text: String) {
        val bytes = text.toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDir(prefix = "epub-metadata-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
