package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderSearchMatchingTest {
    @Test
    fun `matches Android case-insensitive word-start behavior`() {
        assertEquals(
            listOf(0, 11, 18, 27),
            readerWordStartMatchOffsets("Reader pre-reader READERLY reader", "reader"),
        )
    }

    @Test
    fun `blank query has no matches`() {
        assertEquals(emptyList(), readerWordStartMatchOffsets("Reader", "  "))
    }

    @Test
    fun `windowed scan only reports matches that start inside the window`() {
        val text = "needle filler needle"

        // The window ends right after the first match: a match that starts past
        // it belongs to the next window, a match that starts inside still counts
        // even when the window cuts it short.
        assertEquals(
            listOf(0, 14),
            readerWordStartMatchOffsets(text, "needle", 0, text.length, null),
        )
        assertEquals(
            listOf(0),
            readerWordStartMatchOffsets(text, "needl", 0, 6, null),
        )
    }

    @Test
    fun `window start carries the character before it`() {
        // "xneedle" must not become a word start when a window starts mid-word.
        assertEquals(
            emptyList(),
            readerWordStartMatchOffsets("needle", "needle", 0, 6, charBeforeText = 'x'),
        )
        assertEquals(
            listOf(0),
            readerWordStartMatchOffsets("needle", "needle", 0, 6, charBeforeText = ' '),
        )
    }

    @Test
    fun `match quote keeps context on both sides and collapses whitespace`() {
        val text = "alpha beta\n\n  needle   gamma delta"
        val start = text.indexOf("needle")
        val quote = readerSearchMatchQuote(text, start, "needle".length, contextChars = 8)

        assertEquals("beta needle gamma", quote)
    }

    @Test
    fun `compacted text drops whitespace and case`() {
        assertEquals("theswordwasbroken", readerSearchCompactText("The  sword\n\twas broken"))
    }

    @Test
    fun `context comparison reports the shared tail and head`() {
        assertEquals(6, readerSearchCommonSuffixLength("the broken", "broken"))
        assertEquals(0, readerSearchCommonSuffixLength("the", "other"))
        assertEquals(6, readerSearchCommonPrefixLength("broken blade", "broken"))
        assertEquals(0, readerSearchCommonPrefixLength("broken", "sunken"))
    }

    @Test
    fun `block boundary tag set stays the contract every consumer flattens with`() {
        // The WebView script keeps the mirrored list; a divergence here is what
        // shifts hit ordinals between the results list and the painted page.
        assertTrue("p" in READER_SEARCH_BLOCK_BOUNDARY_TAGS)
        assertTrue("br" in READER_SEARCH_BLOCK_BOUNDARY_TAGS)
        assertEquals(setOf("script", "style", "noscript", "template"), READER_SEARCH_SKIPPED_TAGS)
    }
}
