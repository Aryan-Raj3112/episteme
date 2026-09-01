package com.aryan.reader.shared.reader

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTextDecodingStreamTest {

    private fun String.encodedAs(charsetName: String): ByteArray =
        toByteArray(Charset.forName(charsetName))

    private fun streamingDecode(bytes: ByteArray): String =
        openLenientDecodedReader(ByteArrayInputStream(bytes)).use { it.readText() }

    private fun assertStreamMatchesDecode(bytes: ByteArray) {
        val expected = SharedTextDecoding.decode(bytes)
        assertEquals(expected, streamingDecode(bytes))
    }

    @Test
    fun utf8MatchesWholeFileDecode() {
        val text = "Plain UTF-8 text with 中文 and emoji 📖 across several lines.\nSecond line."
        assertStreamMatchesDecode(text.encodeToByteArray())
    }

    @Test
    fun utf8WithBomMatchesWholeFileDecode() {
        val text = "BOM-marked UTF-8 中文 payload."
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.encodeToByteArray()
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun utf16LeWithBomMatchesWholeFileDecode() {
        val text = "UTF-16LE BOM 中文 payload."
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun utf16BeWithBomMatchesWholeFileDecode() {
        val text = "UTF-16BE BOM 中文 payload."
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun bomLessUtf16LeMatchesWholeFileDecode() {
        val text = "BOM-less UTF-16LE payload with 中文 content and more prose to settle detection."
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun bomLessUtf16BeMatchesWholeFileDecode() {
        val text = "BOM-less UTF-16BE payload with 中文 content and more prose to settle detection."
        val bytes = text.toByteArray(Charsets.UTF_16BE)
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun utf32LeWithBomMatchesWholeFileDecode() {
        val text = "UTF-32LE BOM 中文 payload with an astral char 𐀀 included."
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00) +
            text.toByteArray(Charset.forName("UTF-32LE"))
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun utf32BeWithBomMatchesWholeFileDecode() {
        val text = "UTF-32BE BOM 中文 payload with an astral char 𐀀 included."
        val bytes = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()) +
            text.toByteArray(Charset.forName("UTF-32BE"))
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun gbkMatchesWholeFileDecode() {
        val text = "中文测试：这是一段较长的简体中文文本，用于字符集检测，第一章 读书使人进步。"
        assertStreamMatchesDecode(text.encodedAs("GBK"))
    }

    @Test
    fun latin1FallbackMatchesWholeFileDecodeForBinary() {
        // Bytes that are neither valid UTF-8 nor detectable as a legacy charset
        // must fall through to the per-byte Latin-1 mapping exactly like decode().
        val bytes = ByteArray(512) { index -> ((index * 31 + 5) and 0xFF).toByte() }
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun emptySourceDecodesToEmptyText() {
        assertEquals("", streamingDecode(ByteArray(0)))
    }

    @Test
    fun utf32OutOfRangeScalarsAreDroppedLikeWholeFileDecode() {
        // A scalar above the Unicode range is dropped by the manual decoder; the
        // streaming reader must match instead of emitting garbage surrogates.
        val valid = "中文 prefix"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00) +
            valid.toByteArray(Charset.forName("UTF-32LE")) +
            byteArrayOf(0x00, 0x00, 0x20.toByte(), 0x00) + // 0x00200000 > 0x10FFFF
            " 中".toByteArray(Charset.forName("UTF-32LE"))
        val expected = SharedTextDecoding.decode(bytes)
        assertTrue(expected.startsWith("中文 prefix"))
        assertEquals(expected, streamingDecode(bytes))
    }

    @Test
    fun largeUtf8FileSurvivesSampleCutMidSequence() {
        // Larger than the 1MB detection sample and ending in multi-byte CJK chars,
        // so the sample boundary cuts in the middle of a UTF-8 sequence.
        val line = "这一行中文内容会被重复很多次，用来生成超过检测样本大小的大文件。"
        val text = buildString {
            repeat(12_000) { index ->
                append(line)
                append(" #")
                append(index)
                append('\n')
                if (index % 500 == 0) append('\n')
            }
        }
        val bytes = text.encodeToByteArray()
        assertTrue(bytes.size > 1024 * 1024)
        assertEquals(text, streamingDecode(bytes))
    }

    @Test
    fun largeBomLessUtf16FileStreamsThroughSample() {
        val line = "BOM-less UTF-16 line with 中文 content repeated to exceed the sample size."
        val text = buildString {
            repeat(9_000) { index ->
                append(line)
                append(" #")
                append(index)
                append('\n')
            }
        }
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        assertTrue(bytes.size > 1024 * 1024)
        assertEquals(text, streamingDecode(bytes))
    }

    @Test
    fun largeGbkFileMatchesSampledDetection() {
        val line = "简体中文内容重复出现，用于超过检测样本的字节数，验证流式解码与整体解码一致。"
        val text = buildString {
            repeat(16_000) { index ->
                append(line)
                append(" 第")
                append(index)
                append("行\n")
            }
        }
        val bytes = text.encodedAs("GBK")
        assertTrue(bytes.size > 1024 * 1024)
        assertStreamMatchesDecode(bytes)
    }

    @Test
    fun closingReaderClosesSource() {
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(0)) {
            override fun close() {
                closed = true
            }
        }
        openLenientDecodedReader(source).use { it.readText() }
        assertTrue(closed)
    }
}
