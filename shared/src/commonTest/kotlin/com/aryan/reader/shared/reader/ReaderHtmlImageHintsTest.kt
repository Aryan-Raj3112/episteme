package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderHtmlImageHintsTest {

    @Test
    fun `loading hints are added without touching existing attributes`() {
        val html = """<p>a</p><img src="../images/a.png" alt="A">"""

        val out = html.withReaderImageLoadingHints()

        assertTrue(out.contains("""loading="eager""""))
        assertTrue(out.contains("""decoding="async""""))
        assertTrue(out.contains("""src="../images/a.png""""))
        assertTrue(out.contains("""alt="A""""))
    }

    @Test
    fun `loading hints survive angle brackets inside quoted attributes`() {
        val html = """<img src="a.png" alt="x > y">"""

        val out = html.withReaderImageLoadingHints()

        assertTrue(out.contains("""alt="x > y""""))
        assertTrue(out.contains("""loading="eager""""))
    }

    @Test
    fun `loading hints preserve self-closing tags and existing values`() {
        val html = """<img src="a.png" loading="eager" />"""

        val out = html.withReaderImageLoadingHints()

        assertTrue(out.contains("""loading="eager""""))
        assertTrue(out.contains("""decoding="async""""))
        assertTrue(out.endsWith("/>"))
        assertFalse(out.contains("/ loading="))
    }

    @Test
    fun `author loading choice is preserved`() {
        val html = """<img src="a.png" loading="lazy">"""

        val out = html.withReaderImageLoadingHints()

        assertTrue(out.contains("""loading="lazy""""))
        assertFalse(out.contains("eager"))
    }

    @Test
    fun `png bounds come from IHDR`() {
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x03,
            0x08, 0x02, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        )

        assertEquals(2 to 3, parseReaderImageBounds(bytes))
    }

    @Test
    fun `gif bmp jpeg and webp bounds parse`() {
        val gif = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
            0x04, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00
        )
        assertEquals(4 to 5, parseReaderImageBounds(gif))

        val bmp = ByteArray(30).also {
            it[0] = 0x42
            it[1] = 0x4D
            it[14] = 40
            it[18] = 6
            it[22] = 7
        }
        assertEquals(6 to 7, parseReaderImageBounds(bmp))

        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08,
            0x00, 0x05, 0x00, 0x03,
            0x01, 0x11, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
        assertEquals(3 to 5, parseReaderImageBounds(jpeg))

        val webp = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
            0x56, 0x50, 0x38, 0x58, 0x0A, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        assertEquals(1 to 1, parseReaderImageBounds(webp))
    }

    @Test
    fun `garbage and truncated data yield no bounds`() {
        assertNull(parseReaderImageBounds("not an image".encodeToByteArray()))
        assertNull(parseReaderImageBounds(byteArrayOf(1, 2, 3)))
        assertNull(parseReaderImageBounds(ByteArray(0)))
    }

    @Test
    fun `dimensions are injected from resolved bytes only`() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x03,
            0x08, 0x02, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        )
        val html = """<img src="../images/a.png"><img src="b.svg"><img src="data:image/png;base64,SGk=">""" +
            """<img src="https://example.com/c.png"><img src="d.png" width="9" height="9">"""
        val resolve = { src: String -> if (src.endsWith("a.png")) png else null }

        val out = html.withReaderImageDimensions(resolve)

        assertTrue(out.contains("""src="../images/a.png" width="2" height="3""""))
        assertFalse(out.contains("b.svg\" width="))
        assertFalse(out.contains("SGk=\" width="))
        assertFalse(out.contains("c.png\" width="))
        assertTrue(out.contains("""src="d.png" width="9" height="9""""))
    }
}
