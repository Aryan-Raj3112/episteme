package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedTextDecodingTest {

    private fun utf16Bytes(text: String, littleEndian: Boolean, withBom: Boolean): ByteArray {
        val bom = when {
            !withBom -> ByteArray(0)
            littleEndian -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            else -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        }
        val units = ByteArray(text.length * 2)
        text.forEachIndexed { index, char ->
            val unit = char.code
            val low = (unit and 0xFF).toByte()
            val high = ((unit ushr 8) and 0xFF).toByte()
            if (littleEndian) {
                units[index * 2] = low
                units[index * 2 + 1] = high
            } else {
                units[index * 2] = high
                units[index * 2 + 1] = low
            }
        }
        return bom + units
    }

    private fun utf32Bytes(codePoint: Int, littleEndian: Boolean, withBom: Boolean): ByteArray {
        val bom = when {
            !withBom -> ByteArray(0)
            littleEndian -> byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
            else -> byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        }
        val unit = ByteArray(4)
        for (index in 0 until 4) {
            val shift = if (littleEndian) index * 8 else (3 - index) * 8
            unit[index] = ((codePoint ushr shift) and 0xFF).toByte()
        }
        return bom + unit
    }

    @Test
    fun decodesUtf8WithoutBom() {
        val text = "中文测试 Hello"
        assertEquals(text, SharedTextDecoding.decode(text.encodeToByteArray()))
    }

    @Test
    fun stripsUtf8Bom() {
        val text = "\uFEFF中文测试"
        assertEquals("中文测试", SharedTextDecoding.decode(text.encodeToByteArray()))
    }

    @Test
    fun decodesUtf16LeWithBom() {
        val text = "A中𝄞z"
        assertEquals(text, SharedTextDecoding.decode(utf16Bytes(text, littleEndian = true, withBom = true)))
    }

    @Test
    fun decodesUtf16BeWithBom() {
        val text = "A中𝄞z"
        assertEquals(text, SharedTextDecoding.decode(utf16Bytes(text, littleEndian = false, withBom = true)))
    }

    @Test
    fun decodesUtf32LeWithBom() {
        assertEquals("𐀀", SharedTextDecoding.decode(utf32Bytes(0x10000, littleEndian = true, withBom = true)))
        assertEquals("中", SharedTextDecoding.decode(utf32Bytes(0x4E2D, littleEndian = true, withBom = true)))
    }

    @Test
    fun decodesUtf32BeWithBom() {
        assertEquals("𐀀", SharedTextDecoding.decode(utf32Bytes(0x10000, littleEndian = false, withBom = true)))
        assertEquals("中", SharedTextDecoding.decode(utf32Bytes(0x4E2D, littleEndian = false, withBom = true)))
    }

    @Test
    fun detectsBomLessUtf16Le() {
        val text = "Hello World!"
        assertEquals(text, SharedTextDecoding.decode(utf16Bytes(text, littleEndian = true, withBom = false)))
    }

    @Test
    fun detectsBomLessUtf16Be() {
        val text = "Hello World!"
        assertEquals(text, SharedTextDecoding.decode(utf16Bytes(text, littleEndian = false, withBom = false)))
    }

    @Test
    fun decodesGbkChineseWithoutReplacementChars() {
        // "中文一二三四五六七八九十测试" encoded in GBK/GB2312.
        val gbkBytes = byteArrayOf(
            0xD6.toByte(), 0xD0.toByte(),
            0xCE.toByte(), 0xC4.toByte(),
            0xD2.toByte(), 0xBB.toByte(),
            0xB6.toByte(), 0xFE.toByte(),
            0xC8.toByte(), 0xFD.toByte(),
            0xCB.toByte(), 0xC4.toByte(),
            0xCE.toByte(), 0xE5.toByte(),
            0xC1.toByte(), 0xF9.toByte(),
            0xC6.toByte(), 0xDF.toByte(),
            0xB0.toByte(), 0xCB.toByte(),
            0xBE.toByte(), 0xC5.toByte(),
            0xCA.toByte(), 0xAE.toByte(),
            0xB2.toByte(), 0xE2.toByte(),
            0xCA.toByte(), 0xD4.toByte()
        )
        val decoded = SharedTextDecoding.decode(gbkBytes)
        assertTrue('中' in decoded, "expected readable Han characters, got: $decoded")
        assertFalse('\uFFFD' in decoded, "unexpected replacement characters: $decoded")
    }

    @Test
    fun decodesEmptyBytesToEmptyString() {
        assertEquals("", SharedTextDecoding.decode(ByteArray(0)))
    }

    @Test
    fun decodesArbitraryBinaryWithoutThrowing() {
        val junk = byteArrayOf(
            0x01, 0x02, 0x03, 0x7F,
            0x81.toByte(), 0x40, 0xFE.toByte(), 0xFF.toByte()
        )
        val decoded = SharedTextDecoding.decode(junk)
        assertFalse(decoded.isEmpty())
    }
}
