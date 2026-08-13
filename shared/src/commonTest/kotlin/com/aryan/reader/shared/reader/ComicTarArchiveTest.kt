package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ComicTarArchiveTest {
    @Test
    fun readsRegularFilesAndPrefixPaths() {
        val first = tarEntry(name = "001.jpg", data = byteArrayOf(1, 2, 3))
        val second = tarEntry(name = "002.png", prefix = "chapter", data = byteArrayOf(4, 5))
        val archive = first + second + ByteArray(1024)

        val entries = archive.readComicTarEntries()

        assertEquals(listOf("001.jpg", "chapter/002.png"), entries.map { it.first })
        assertContentEquals(byteArrayOf(1, 2, 3), entries[0].second)
        assertContentEquals(byteArrayOf(4, 5), entries[1].second)
    }

    @Test
    fun ignoresDirectoryEntries() {
        val archive = tarEntry(name = "pages/", data = byteArrayOf(), type = '5') + ByteArray(1024)

        assertEquals(emptyList(), archive.readComicTarEntries())
    }

    private fun tarEntry(
        name: String,
        prefix: String = "",
        data: ByteArray,
        type: Char = '0',
    ): ByteArray {
        val header = ByteArray(512)
        header.writeAscii(0, name)
        header.writeAscii(124, data.size.toString(8).padStart(11, '0') + '\u0000')
        header[156] = type.code.toByte()
        header.writeAscii(345, prefix)
        val padding = ByteArray((512 - data.size % 512) % 512)
        return header + data + padding
    }

    private fun ByteArray.writeAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(this, offset)
    }
}
