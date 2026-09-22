package com.aryan.reader.shared.reader

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `rewrite replaces explicit cover image and preserves metadata`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(source, includeCover = true)

        val result = SharedEpubMetadataEditor.rewrite(
            source = source,
            destination = output,
            update = update().copy(cover = SharedEpubCoverUpdate("new-cover".toByteArray(), "png"))
        )

        assertEquals("New Title", result.title)
        ZipFile(output).use { zip ->
            val coverEntry = assertNotNull(zip.getEntry("OEBPS/images/cover.png"))
            assertEquals("new-cover", zip.getInputStream(coverEntry).reader().readText())
            val opf = zip.getInputStream(assertNotNull(zip.getEntry("OEBPS/content.opf"))).reader().readText()
            assertTrue(opf.contains("properties=\"cover-image\""))
            assertTrue(opf.contains("name=\"cover\""))
        }
    }

    @Test
    fun `read cover returns original cover for restore`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        writeEpub(source, includeCover = true, coverBytes = "original-cover".toByteArray())

        val cover = assertNotNull(SharedEpubMetadataEditor.readCover(source))

        assertEquals("png", cover.extension)
        assertEquals("original-cover", cover.bytes.toString(Charsets.UTF_8))
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

    @Test
    fun `read metadata resolves epub3 series collection written as prefixed metas`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        writeEpub(
            target = source,
            version = "3.0",
            metadata = """
                <metadata>
                  <dc:title>Exhalation: Stories</dc:title>
                  <dc:creator>Ted Chiang</dc:creator>
                  <meta content="2019-04-18T00:00:00Z" name="created"/>
                  <opf:meta property="belongs-to-collection" id="id-2">1, aryan</opf:meta>
                  <opf:meta refines="#id-2" property="collection-type">series</opf:meta>
                  <opf:meta refines="#id-2" property="group-position">1</opf:meta>
                </metadata>
            """.trimIndent()
        )

        val snapshot = assertNotNull(SharedEpubMetadataEditor.readMetadata(source))

        assertEquals("Exhalation: Stories", snapshot.title)
        assertEquals("Ted Chiang", snapshot.author)
        assertEquals("1, aryan", snapshot.seriesName)
        assertEquals(1.0, snapshot.seriesIndex)
    }

    @Test
    fun `rewrite updates epub3 series collection so the reader sees the new value`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(
            target = source,
            version = "3.0",
            metadata = """
                <metadata>
                  <dc:title>Old</dc:title>
                  <opf:meta property="belongs-to-collection" id="id-2">1, aryan</opf:meta>
                  <opf:meta refines="#id-2" property="collection-type">series</opf:meta>
                  <opf:meta refines="#id-2" property="group-position">1</opf:meta>
                </metadata>
            """.trimIndent()
        )

        val result = SharedEpubMetadataEditor.rewrite(source, output, update())

        assertEquals("New Series", result.seriesName)
        assertEquals(2.5, result.seriesIndex)
        val opf = readOpf(output)
        assertTrue(opf.contains("belongs-to-collection"))
        assertTrue(opf.contains("New Series"))
        assertTrue(opf.contains("name=\"calibre:series\""))
        assertEquals(result, SharedEpubMetadataEditor.readMetadata(output))
    }

    @Test
    fun `rewrite clears only the series collection when series is removed`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(
            target = source,
            version = "3.0",
            metadata = """
                <metadata>
                  <dc:title>Old</dc:title>
                  <opf:meta property="belongs-to-collection" id="id-2">1, aryan</opf:meta>
                  <opf:meta refines="#id-2" property="collection-type">series</opf:meta>
                  <opf:meta refines="#id-2" property="group-position">1</opf:meta>
                  <opf:meta property="belongs-to-collection" id="set1">Boxed Sets</opf:meta>
                  <opf:meta refines="#set1" property="collection-type">set</opf:meta>
                </metadata>
            """.trimIndent()
        )

        val result = SharedEpubMetadataEditor.rewrite(
            source,
            output,
            update().copy(seriesName = null, seriesIndex = null)
        )

        assertNull(result.seriesName)
        assertNull(result.seriesIndex)
        val opf = readOpf(output)
        assertFalse(opf.contains("1, aryan"))
        assertFalse(opf.contains("name=\"calibre:series\""))
        assertTrue(opf.contains("Boxed Sets"))
    }

    @Test
    fun `rewrite does not add epub3 series collection to version 2 packages`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "output.epub")
        writeEpub(
            target = source,
            metadata = """
                <metadata>
                  <dc:title>Old</dc:title>
                  <meta name="calibre:series" content="Old Series"/>
                  <meta name="calibre:series_index" content="1"/>
                </metadata>
            """.trimIndent()
        )

        val result = SharedEpubMetadataEditor.rewrite(source, output, update())

        assertEquals("New Series", result.seriesName)
        assertEquals(2.5, result.seriesIndex)
        assertFalse(readOpf(output).contains("belongs-to-collection"))
    }

    private fun readOpf(epub: File): String = ZipFile(epub).use { zip ->
        zip.getInputStream(assertNotNull(zip.getEntry("OEBPS/content.opf"))).reader().readText()
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
        """.trimIndent(),
        includeCover: Boolean = false,
        coverBytes: ByteArray = "old-cover".toByteArray(),
        version: String? = null
    ) {
        val versionAttribute = version?.let { """ version="$it"""" } ?: ""
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles></container>"""
            )
            zip.putText(
                "OEBPS/content.opf",
                """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/"$versionAttribute>
                  $metadata
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" />
                    ${if (includeCover) "<item id=\"cover-image\" href=\"images/cover.png\" media-type=\"image/png\" properties=\"cover-image\" />" else ""}
                  </manifest>
                  <spine><itemref idref="chapter" /></spine>
                </package>
                """.trimIndent()
            )
            zip.putText("OEBPS/chapter.xhtml", "chapter")
            if (includeCover) {
                zip.putBytes("OEBPS/images/cover.png", coverBytes)
            }
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
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
        val dir = createTempDirectory("epub-metadata-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
