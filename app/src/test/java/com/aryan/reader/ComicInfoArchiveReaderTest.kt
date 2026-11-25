package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ComicInfoArchiveReaderTest {

    @Test
    fun `only comic archive types are readable`() {
        assertTrue(ComicInfoArchiveReader.canRead(FileType.CBZ))
        assertTrue(ComicInfoArchiveReader.canRead(FileType.CBR))
        assertTrue(ComicInfoArchiveReader.canRead(FileType.CB7))
        assertTrue(ComicInfoArchiveReader.canRead(FileType.CBT))
        assertFalse(ComicInfoArchiveReader.canRead(FileType.EPUB))
        assertFalse(ComicInfoArchiveReader.canRead(FileType.PDF))
    }

    @Test
    fun `cbz reads a nested lowercase sidecar`() {
        val cbzBytes = zipBytes(
            "Batman Vol 1/comicinfo.xml" to """
                <ComicInfo>
                    <Title>Court of Owls</Title>
                    <Series>Batman</Series>
                    <Number>5</Number>
                    <Summary>Owls everywhere.</Summary>
                    <Writer>Scott Snyder</Writer>
                </ComicInfo>
            """.trimIndent().toByteArray(Charsets.UTF_8),
            "Batman Vol 1/001.png" to onePixelPngBytes()
        )

        val metadata = readCbz(cbzBytes)

        assertEquals("Court of Owls", metadata?.title)
        assertEquals("Scott Snyder", metadata?.author)
        assertEquals("Batman", metadata?.seriesName)
        assertEquals(5.0, metadata?.seriesIndex)
        assertEquals("Owls everywhere.", metadata?.description)
    }

    @Test
    fun `cbz without a sidecar yields no metadata`() {
        assertNull(readCbz(zipBytes("001.png" to onePixelPngBytes())))
    }

    private fun readCbz(bytes: ByteArray) = ComicInfoArchiveReader.read(
        type = FileType.CBZ,
        openStream = { ByteArrayInputStream(bytes) },
        openFileDescriptor = { null },
        cacheDir = Files.createTempDirectory("comicinfo-reader").toFile()
    )

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun onePixelPngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        )
    }
}
