package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CssGeneratedContentTest {
    @Test
    fun `hex escapes decode and consume one optional terminator whitespace`() {
        assertEquals("\u200C", decodeCssStringToken("\\200c"))
        assertEquals("\u200Cnext", decodeCssStringToken("\\200c next"))
        assertEquals("A", decodeCssStringToken("\\41"))
        assertEquals("😀", decodeCssStringToken("\\1F600"))
    }

    @Test
    fun `simple escapes and escaped newlines follow css string rules`() {
        assertEquals("a\"b\\c", decodeCssStringToken("a\\\"b\\\\c"))
        assertEquals("ab", decodeCssStringToken("a\\\nb"))
        assertEquals("ab", decodeCssStringToken("a\\\r\nb"))
    }

    @Test
    fun `invalid code points become replacement characters`() {
        assertEquals("\uFFFD", decodeCssStringToken("\\0"))
        assertEquals("\uFFFD", decodeCssStringToken("\\110000"))
        assertEquals("\uFFFD", decodeCssStringToken("\\D800"))
    }

    @Test
    fun `generated content materializes strings and attributes`() {
        assertEquals(
            "\u200C Chapter 7",
            materializeCssGeneratedContent("\"\\200c  \" attr(data-label)") { name ->
                if (name == "data-label") "Chapter 7" else null
            }
        )
        assertEquals(" ", materializeCssGeneratedContent("\" \"") { null })
        assertNull(materializeCssGeneratedContent("none") { "unexpected" })
    }
}
