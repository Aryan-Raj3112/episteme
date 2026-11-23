@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class IosComicInfoPresentationTest {
    @Test
    fun readsComicInfoMetadataAndKeepsTheFirstPageAsCover() {
        val directory = temporaryDirectory()
        val archivePath = "$directory/batman-05.cbz"
        val coverBytes = byteArrayOf(1, 2, 3, 4)
        try {
            val entries = linkedMapOf(
                "ComicInfo.xml" to COMIC_INFO_XML.encodeToByteArray(),
                "001.png" to coverBytes,
            )
            writeIosZipArchive(archivePath, entries.keys.toList(), entries::get)

            val presentation = extractIosBookPresentation(book(archivePath, FileType.CBZ))

            assertComicInfo(presentation)
            assertContentEquals(coverBytes, presentation.coverBytes)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }

    @Test
    fun readsComicInfoFromCbrAndCb7ThroughLibarchive() {
        val directory = temporaryDirectory()
        try {
            val cbrPath = "$directory/batman-05.cbr"
            writeFixture(cbrPath, Base64.decode(CBR_WITH_COMIC_INFO_FIXTURE))
            assertComicInfo(extractIosBookPresentation(book(cbrPath, FileType.CBR)))

            val cb7Path = "$directory/batman-05.cb7"
            writeFixture(cb7Path, Base64.decode(CB7_WITH_COMIC_INFO_FIXTURE))
            assertComicInfo(extractIosBookPresentation(book(cb7Path, FileType.CB7)))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }

    @Test
    fun readsComicInfoFromCbtThroughLibarchive() {
        val directory = temporaryDirectory()
        val cbtPath = "$directory/batman-05.cbt"
        try {
            writeFixture(
                cbtPath,
                tarBytes(
                    listOf(
                        "ComicInfo.xml" to COMIC_INFO_XML.encodeToByteArray(),
                        "001.png" to byteArrayOf(1, 2, 3, 4),
                    ),
                ),
            )
            assertComicInfo(extractIosBookPresentation(book(cbtPath, FileType.CBT)))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }

    private fun assertComicInfo(presentation: IosBookPresentation) {
        assertEquals("Court of Owls", presentation.title)
        assertEquals("Scott Snyder", presentation.author)
        assertEquals("Batman", presentation.seriesName)
        assertEquals(5.0, presentation.seriesIndex)
        assertEquals("Owls everywhere.", presentation.description)
        assertTrue(assertNotNull(presentation.coverBytes).isNotEmpty())
    }

    private fun book(path: String, type: FileType) = BookItem(
        id = "comicinfo-${type.name.lowercase()}",
        path = path,
        type = type,
        displayName = "batman-05.${type.name.lowercase()}",
        timestamp = 1L,
    )

    private fun temporaryDirectory(): String {
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/reader-comicinfo-${Random.nextInt()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return directory
    }

    private fun writeFixture(path: String, bytes: ByteArray) {
        val file = fopen(path, "wb") ?: error("Could not open $path")
        try {
            val size = bytes.size.toULong()
            bytes.usePinned { pinned ->
                check(fwrite(pinned.addressOf(0), 1uL, size, file) == size)
            }
        } finally {
            fclose(file)
        }
    }

    private fun tarBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        fun padded(size: Int): Int = (size + 511) / 512 * 512
        val out = ByteArray(entries.sumOf { 512 + padded(it.second.size) } + 1024)
        var offset = 0
        entries.forEach { (name, data) ->
            val header = ByteArray(512)
            name.encodeToByteArray().copyInto(header, 0)
            header.writeOctal(100, 8, 0b111_101_100L) // 0755
            header.writeOctal(108, 8, 0L)
            header.writeOctal(116, 8, 0L)
            header.writeOctal(124, 12, data.size.toLong())
            header.writeOctal(136, 12, 0L)
            header[156] = '0'.code.toByte()
            "ustar".encodeToByteArray().copyInto(header, 257)
            "00".encodeToByteArray().copyInto(header, 263)
            for (index in 148..155) header[index] = ' '.code.toByte()
            val checksum = header.sumOf { it.toInt() and 0xFF }
            header.writeOctal(148, 6, checksum.toLong())
            header[154] = 0
            header[155] = ' '.code.toByte()
            header.copyInto(out, offset)
            offset += 512
            data.copyInto(out, offset)
            offset += padded(data.size)
        }
        return out
    }

    private fun ByteArray.writeOctal(offset: Int, length: Int, value: Long) {
        val digits = value.toString(8).padStart(length - 1, '0').take(length - 1)
        digits.encodeToByteArray().copyInto(this, offset)
        this[offset + length - 1] = 0
    }

    private companion object {
        private const val COMIC_INFO_XML = """
            <ComicInfo>
              <Title>Court of Owls</Title>
              <Series>Batman</Series>
              <Number>5</Number>
              <Summary>Owls everywhere.</Summary>
              <Writer>Scott Snyder</Writer>
            </ComicInfo>
        """

        private const val CBR_WITH_COMIC_INFO_FIXTURE =
            "UmFyIRoHAQDFGjMyAwEAACbRu/0RAwYcBBwARVFAgwAABFJBUlNyYXJzLzAuMTMuMAptYXJrZXItc2NoZW1hPTEKAgcVo0ACAyPUAQTUASDMlT7ZAAANQ29taWNJbmZvLnhtbCICAMBBhC9G3knVh9k+FM8og97ZjcdWiZ0E6z29piKZIY8mPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPENvbWljSW5mbz4KICA8VGl0bGU+Q291cnQgb2YgT3dsczwvVGl0bGU+CiAgPFNlcmllcz5CYXRtYW48L1Nlcmllcz4KICA8TnVtYmVyPjU8L051bWJlcj4KICA8U3VtbWFyeT5Pd2xzIGV2ZXJ5d2hlcmUuPC9TdW1tYXJ5PgogIDxXcml0ZXI+U2NvdHQgU255ZGVyPC9Xcml0ZXI+CjwvQ29taWNJbmZvPgqmAVwkOAIDI0QERCBZqVCQAAAHMDAxLnBuZyICAKZ1EE3wvRhp6SYAIaF1Q6sKBxoJoX1XAlnYn5KFApVTiVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYIIZsjo1AwUAAA=="

        private const val CB7_WITH_COMIC_INFO_FIXTURE =
            "N3q8ryccAAIC71Rn/QAAAAAAAABnAAAAAAAAAD12J+jgANMArF0AHg/LhxHYzmaRD4Meyv17M9R/6bfaKDF2JWYgTSoJbYm6XYkbkElKQJBx/Gu+CMtOKW9I8TBlkVbu00OzkI1IpE9VkPCcypGIVw5PEUbnQqqtJFp5FkU1Q1D+A6BacpSb6uLgRZXVWmQO8e15WaGBHkWfeGSC+72L7A/hwu0cKyMzP9SwhMYjrHUHtsLEcmdImENcDEkmC7Uke+Phy5FMMTLh61SP74GME414rAABAESJUE5HDQoaCgAAAA1JSERSAAAAAQAAAAEIBAAAALUcDAIAAAALSURBVHjaY/z/HwADAwIA77+n2wAAAAAASUVORK5CYIIAAQQGAAIJgLRJCgEBdwS3q57eCAAHCwIAASEhARYBISEBFgyA1EUKAcyVPtnpQd3sAAgAAAUCES0AQwBvAG0AaQBjAEkAbgBmAG8ALgB4AG0AbAAAADAAMAAxAC4AcABuAGcAAAAAAA=="
    }
}
