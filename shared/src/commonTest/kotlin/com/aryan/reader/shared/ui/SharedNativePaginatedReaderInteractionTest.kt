package com.aryan.reader.shared.ui

import com.aryan.reader.shared.HighlightColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedNativePaginatedReaderInteractionTest {
    @Test
    fun `word selection trims punctuation around long press range`() {
        val range = sharedNativeReaderTrimmedWordRange(
            text = "\"Reader,\" she said.",
            start = 0,
            end = 9
        )

        assertNotNull(range)
        assertEquals(1, range.start)
        assertEquals(7, range.end)
    }

    @Test
    fun `word selection ignores punctuation only range`() {
        val range = sharedNativeReaderTrimmedWordRange(
            text = "...",
            start = 0,
            end = 3
        )

        assertNull(range)
    }

    @Test
    fun `highlight for native selection keeps desktop locator offsets`() {
        val selection = SharedNativeReaderTextSelection(
            chapterIndex = 2,
            pageIndex = 7,
            startOffset = 120,
            endOffset = 136,
            text = "selected passage"
        )

        val highlight = sharedNativeReaderHighlightForSelection(selection, HighlightColor.YELLOW)

        assertEquals("desktop:2:120:136", highlight.cfi)
        assertEquals(2, highlight.chapterIndex)
        assertEquals(7, highlight.locator.pageIndex)
        assertEquals(120, highlight.locator.startOffset)
        assertEquals(136, highlight.locator.endOffset)
        assertEquals("selected passage", highlight.locator.textQuote)
    }
}
