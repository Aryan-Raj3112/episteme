package com.aryan.reader.shared.reader

import java.nio.charset.Charset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTextDecodingJvmTest {

    private fun String.encodedAs(charsetName: String): ByteArray =
        toByteArray(charset(charsetName))

    @Test
    fun decodesGbkChineseExactly() {
        val text = "中文测试：这是一段较长的简体中文文本，用于字符集检测，第一章 读书使人进步。"
        assertEquals(text, SharedTextDecoding.decode(text.encodedAs("GBK")))
    }

    @Test
    fun decodesGb18030ExtensionCharacters() {
        val text = " GBK 扩展区字符：𠀀𠀁 之后是普通中文内容。"
        assertEquals(
            text.trim(),
            SharedTextDecoding.decode(text.trim().encodedAs("GB18030"))
        )
    }

    @Test
    fun decodesBig5TraditionalChineseExactly() {
        val text = "繁體中文測試：這是一段較長的繁體中文文字，用於字元集偵測，讀書使人進步。"
        assertEquals(text, SharedTextDecoding.decode(text.encodedAs("Big5")))
    }

    @Test
    fun decodesShiftJisJapaneseExactly() {
        val text = "日本語のテキストです。文字コードの判定を確認するための、やや長い文章を収めています。"
        assertEquals(text, SharedTextDecoding.decode(text.encodedAs("Shift_JIS")))
    }

    @Test
    fun decodesEucKrKoreanExactly() {
        val text = "한국어 텍스트입니다. 문자 집합 판별을 확인하기 위한 다소 긴 문장을 담고 있습니다."
        assertEquals(text, SharedTextDecoding.decode(text.encodedAs("EUC-KR")))
    }

    @Test
    fun decodesWindows1252EuropeanTextExactly() {
        val text = "Café Münchén – déjà vu, naïve façade über alle Maßen gerettet. " +
            "Diese Zeile wiederholt sich, damit die Erkennung genügend Daten hat. " +
            "Café Münchén – déjà vu, naïve façade über alle Maßen gerettet."
        assertEquals(text, SharedTextDecoding.decode(text.encodedAs("windows-1252")))
    }

    @Test
    fun toleratesTruncatedMultibyteTail() {
        val text = "中文内容在结尾处被截断"
        val bytes = text.encodedAs("GBK").copyOfRange(0, text.encodedAs("GBK").size - 1)
        val decoded = SharedTextDecoding.decode(bytes)
        assertTrue(decoded.startsWith("中文"), "unexpected decode: $decoded")
    }

    @Test
    fun randomBinaryDoesNotThrow() {
        val bytes = ByteArray(8192)
        Random(42).nextBytes(bytes)
        val decoded = SharedTextDecoding.decode(bytes)
        // Any result is acceptable as long as decoding is stable and lossless-ish.
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun utf16WithoutBomStillDetectedOnJvm() {
        val text = "BOM-less UTF-16 payload with 中文 content."
        val units = StringBuilder(text).toString()
        val bytes = ByteArray(units.length * 2)
        units.forEachIndexed { index, char ->
            bytes[index * 2] = (char.code and 0xFF).toByte()
            bytes[index * 2 + 1] = ((char.code ushr 8) and 0xFF).toByte()
        }
        assertEquals(text, SharedTextDecoding.decode(bytes))
    }
}
