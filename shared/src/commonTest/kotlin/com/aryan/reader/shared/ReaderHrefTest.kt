package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderHrefTest {
    @Test
    fun `protocol relative href becomes https external link`() {
        assertEquals("https://example.com/book", normalizeReaderHref("  //example.com/book  "))
        assertTrue(isReaderExternalHref("//example.com/book"))
        assertEquals("https", readerHrefScheme("//example.com/book"))
    }

    @Test
    fun `sms is classified with other external reader schemes`() {
        assertTrue(isReaderExternalHref("sms:+15551212?body=Hello"))
        assertTrue(isReaderExternalHref("mailto:reader@example.com"))
        assertTrue(isReaderExternalHref("tel:+15551212"))
        assertTrue(isReaderExternalHref("geo:37.7,-122.4"))
    }

    @Test
    fun `relative and fragment hrefs remain internal`() {
        assertEquals("chapter-two.xhtml#start", normalizeReaderHref(" chapter-two.xhtml#start "))
        assertFalse(isReaderExternalHref("chapter-two.xhtml#start"))
        assertFalse(isReaderExternalHref("#start"))
        assertFalse(isReaderExternalHref("javascript:alert(1)"))
    }
}
