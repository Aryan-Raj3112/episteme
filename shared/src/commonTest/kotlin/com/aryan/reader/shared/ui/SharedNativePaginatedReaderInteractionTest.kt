package com.aryan.reader.shared.ui

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
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

    @Test
    fun `highlight for block selection keeps android style cfi and locator offsets`() {
        val selection = SharedNativeReaderTextSelection(
            chapterIndex = 1,
            pageIndex = 4,
            startOffset = 105,
            endOffset = 220,
            text = "selected across blocks",
            startPageIndex = 4,
            endPageIndex = 4,
            startBlockIndex = 8,
            endBlockIndex = 10,
            startBlockCharOffset = 100,
            endBlockCharOffset = 200,
            startLocalOffset = 5,
            endLocalOffset = 20,
            startBaseCfi = "/4/2/8",
            endBaseCfi = "/4/2/10"
        )

        val highlight = sharedNativeReaderHighlightForSelection(selection, HighlightColor.GREEN)

        assertEquals("/4/2/8:5|/4/2/10:20", highlight.cfi)
        assertEquals(1, highlight.chapterIndex)
        assertEquals(4, highlight.locator.pageIndex)
        assertEquals(105, highlight.locator.startOffset)
        assertEquals(220, highlight.locator.endOffset)
        assertEquals(8, highlight.locator.blockIndex)
        assertEquals(105, highlight.locator.charOffset)
        assertEquals("selected across blocks", highlight.locator.textQuote)
    }

    @Test
    fun `native paginated keeps same chapter cfi highlights available for block mapping`() {
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "/4/2:3|/4/2:8",
            text = "alpha",
            color = HighlightColor.YELLOW,
            chapterIndex = 2
        )
        val page = ReaderPage(
            pageIndex = 20,
            chapterIndex = 2,
            chapterTitle = "Chapter",
            text = "alpha beta",
            startOffset = 100,
            endOffset = 110
        )

        val visible = sharedNativeVisibleHighlightsForPage(listOf(highlight), page)

        assertEquals(listOf(highlight), visible)
    }

    @Test
    fun `native highlight mapping prefers locator offsets before cfi offsets`() {
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "/4/2:0|/4/2:5",
            text = "target",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 8,
                endOffset = 14,
                textQuote = "target",
                cfi = "/4/2:0|/4/2:5"
            )
        )

        val range = sharedNativeHighlightRangeForBlock(
            highlight = highlight,
            blockCfi = "/4/2",
            textStartOffset = 0,
            textLength = "prefix  target suffix".length,
            text = "prefix  target suffix"
        )

        assertEquals(8, range?.start)
        assertEquals(14, range?.end)
    }

    @Test
    fun `native highlight mapping can use android style block locator`() {
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "android-locator:0:42:108",
            text = "target",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                blockIndex = 42,
                charOffset = 108,
                textQuote = "target",
                cfi = "android-locator:0:42:108"
            )
        )

        val range = sharedNativeHighlightRangeForBlock(
            highlight = highlight,
            blockCfi = "/4/2",
            blockIndex = 42,
            blockCharOffset = 100,
            textStartOffset = 100,
            textLength = "prefix  target suffix".length,
            text = "prefix  target suffix"
        )

        assertEquals(8, range?.start)
        assertEquals(14, range?.end)
    }

    @Test
    fun `native highlight mapping prefers block locator before overlapping offsets`() {
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "android-locator:0:42:108",
            text = "target",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 0,
                endOffset = 6,
                blockIndex = 42,
                charOffset = 108,
                textQuote = "target",
                cfi = "android-locator:0:42:108"
            )
        )

        val range = sharedNativeHighlightRangeForBlock(
            highlight = highlight,
            blockCfi = "/4/2",
            blockIndex = 42,
            blockCharOffset = 100,
            textStartOffset = 0,
            textLength = "prefix  target suffix".length,
            text = "prefix  target suffix"
        )

        assertEquals(8, range?.start)
        assertEquals(14, range?.end)
    }
}
