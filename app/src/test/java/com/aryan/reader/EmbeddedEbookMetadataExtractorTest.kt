package com.aryan.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class EmbeddedEbookMetadataExtractorTest {

    @Test
    fun `epub extracts text metadata and explicitly referenced cover image`() {
        val coverBytes = onePixelPngBytes()
        val epubBytes = zipBytes(
            "META-INF/container.xml" to """
                <container>
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray(Charsets.UTF_8),
            "OEBPS/content.opf" to """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <metadata>
                        <dc:title>Folder EPUB</dc:title>
                        <dc:creator>Octavia Butler</dc:creator>
                        <dc:description>&lt;p&gt;Folder summary&lt;/p&gt;</dc:description>
                        <meta content="Patternist" name="calibre:series"/>
                        <meta content="3" name="calibre:series_index"/>
                        <meta name="cover" content="cover-image"/>
                    </metadata>
                    <manifest>
                        <item id="cover-image" href="images/cover.png" media-type="image/png"/>
                    </manifest>
                </package>
            """.trimIndent().toByteArray(Charsets.UTF_8),
            "OEBPS/images/cover.png" to coverBytes
        )

        val metadata = EmbeddedEbookMetadataExtractor.extract(
            type = FileType.EPUB,
            displayName = "folder.epub",
            openStream = { ByteArrayInputStream(epubBytes) }
        )

        assertEquals("Folder EPUB", metadata.title)
        assertEquals("Octavia Butler", metadata.author)
        assertEquals("<p>Folder summary</p>", metadata.description)
        assertEquals("Patternist", metadata.seriesName)
        assertEquals(3.0, metadata.seriesIndex)
        val cover = metadata.cover
        assertNotNull(cover)
        assertEquals("png", cover!!.extension)
        assertArrayEquals(coverBytes, cover.bytes)
    }

    @Test
    fun `fb2 extracts coverpage binary without parsing book body`() {
        val coverBytes = onePixelPngBytes()
        val fb2 = """
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
                <description>
                    <title-info>
                        <book-title>Folder FB2</book-title>
                        <author>
                            <first-name>Ursula</first-name>
                            <last-name>Le Guin</last-name>
                        </author>
                        <annotation><image l:href="#not-cover.png"/></annotation>
                        <coverpage><image l:href="#cover.png"/></coverpage>
                    </title-info>
                </description>
                <body><section><p>Body text should not matter.</p></section></body>
                <binary id="not-cover.png" content-type="image/png">${Base64.getEncoder().encodeToString(ByteArray(0))}</binary>
                <binary id="cover.png" content-type="image/png">${Base64.getEncoder().encodeToString(coverBytes)}</binary>
            </FictionBook>
        """.trimIndent()

        val metadata = EmbeddedEbookMetadataExtractor.extract(
            type = FileType.FB2,
            displayName = "folder.fb2",
            openStream = { ByteArrayInputStream(fb2.toByteArray(Charsets.UTF_8)) }
        )

        assertEquals("Folder FB2", metadata.title)
        assertEquals("Ursula Le Guin", metadata.author)
        val cover = metadata.cover
        assertNotNull(cover)
        assertEquals("png", cover!!.extension)
        assertArrayEquals(coverBytes, cover.bytes)
    }

    @Test
    fun `mobi extracts EXTH text metadata and embedded cover record`() {
        val coverBytes = onePixelPngBytes()
        val mobiBytes = minimalMobiBytes(
            title = "Folder MOBI",
            author = "N K Jemisin",
            coverBytes = coverBytes
        )

        val metadata = EmbeddedEbookMetadataExtractor.extract(
            type = FileType.MOBI,
            displayName = "folder.mobi",
            openStream = { ByteArrayInputStream(mobiBytes) }
        )

        assertEquals("Folder MOBI", metadata.title)
        assertEquals("N K Jemisin", metadata.author)
        val cover = metadata.cover
        assertNotNull(cover)
        assertEquals("png", cover!!.extension)
        assertArrayEquals(coverBytes, cover.bytes)
    }

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

    private fun minimalMobiBytes(title: String, author: String, coverBytes: ByteArray): ByteArray {
        val exthRecords = listOf(
            exthStringRecord(99, title),
            exthStringRecord(100, author),
            exthIntRecord(201, 0)
        )
        val exthSize = 12 + exthRecords.sumOf { it.size }
        val mobiHeaderLength = 232
        val record0 = ByteArray(16 + mobiHeaderLength + exthSize)
        putU16(record0, 0, 1)
        putU32(record0, 4, 0)
        putU16(record0, 8, 0)
        putU16(record0, 12, 0)
        putAscii(record0, 16, "MOBI")
        putU32(record0, 20, mobiHeaderLength)
        putU32(record0, 16 + 12, 65001)
        putU32(record0, 16 + 68, 0)
        putU32(record0, 16 + 72, 0)
        putU32(record0, 16 + 92, 1)

        val exthOffset = 16 + mobiHeaderLength
        putAscii(record0, exthOffset, "EXTH")
        putU32(record0, exthOffset + 4, exthSize)
        putU32(record0, exthOffset + 8, exthRecords.size)
        var cursor = exthOffset + 12
        exthRecords.forEach { record ->
            record.copyInto(record0, cursor)
            cursor += record.size
        }

        val palmHeader = ByteArray(78 + 8 * 2)
        putU16(palmHeader, 76, 2)
        val record0Offset = palmHeader.size
        val coverOffset = record0Offset + record0.size
        putU32(palmHeader, 78, record0Offset)
        putU32(palmHeader, 86, coverOffset)

        return palmHeader + record0 + coverBytes
    }

    private fun exthStringRecord(type: Int, value: String): ByteArray {
        val data = value.toByteArray(Charsets.UTF_8)
        return exthRecord(type, data)
    }

    private fun exthIntRecord(type: Int, value: Int): ByteArray {
        val data = ByteArray(4)
        putU32(data, 0, value)
        return exthRecord(type, data)
    }

    private fun exthRecord(type: Int, data: ByteArray): ByteArray {
        val record = ByteArray(8 + data.size)
        putU32(record, 0, type)
        putU32(record, 4, record.size)
        data.copyInto(record, 8)
        return record
    }

    private fun putAscii(target: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(target, offset)
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 1] = (value and 0xFF).toByte()
    }

    private fun putU32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    private fun onePixelPngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        )
    }
}
