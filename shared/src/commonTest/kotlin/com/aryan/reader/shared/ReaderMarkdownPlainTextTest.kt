package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderMarkdownPlainTextTest {
    @Test
    fun stripsInlineMarkersForTts() {
        assertEquals(
            "Hello bold and italic.",
            readerMarkdownPlainText("Hello **bold** and *italic*.")
        )
    }

    @Test
    fun replacesLinksWithLabels() {
        assertEquals(
            "See docs for details.",
            readerMarkdownPlainText("See [docs](https://example.com) for details.")
        )
    }

    @Test
    fun joinsBlocksWithBlankLines() {
        assertEquals(
            "Title\n\nFirst paragraph.\n\nitem one\nitem two",
            readerMarkdownPlainText("# Title\n\nFirst paragraph.\n\n- item one\n- item two")
        )
    }

    @Test
    fun blankMarkdownFallsBackToTrimmedInput() {
        assertEquals("", readerMarkdownPlainText("   "))
    }

    @Test
    fun aiResultStateCarriesQueryAndUsage() {
        val state = ReaderAiResultState(
            title = ReaderAiFeature.DEFINE.displayName,
            text = "A **word**.",
            isLoading = false,
            queryText = "word",
            cost = 0.0,
            freeRemaining = 9,
        )
        assertTrue(state.hasContent)
        assertEquals("word", state.queryText)
        assertEquals(0.0, state.cost)
        assertEquals(9, state.freeRemaining)
        assertFalse(ReaderAiResultState().hasContent)
    }
}
