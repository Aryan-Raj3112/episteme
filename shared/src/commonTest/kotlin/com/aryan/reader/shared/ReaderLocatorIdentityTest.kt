package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What counts as "the same highlight", when re-highlighting an existing selection.
 *
 * `processAndAddHighlight` updates in place when it decides two selections are the same highlight, and
 * appends otherwise. Getting that wrong in one direction loses data silently, so both directions are
 * pinned here.
 */
class ReaderLocatorIdentityTest {

    @Test
    fun `the same stretch twice is the same highlight`() {
        val first = ReaderLocator(
            chapterIndex = 0,
            blockIndex = 3,
            charOffset = 12,
            endOffset = 20,
            textQuote = "beta gamma"
        )

        assertTrue(first.sameLocation(first.copy()))
    }

    @Test
    fun `a longer selection over the same start is a different highlight`() {
        // This is the case that lost data: "beta" then "beta gamma" share a block and a start, so
        // matching on those alone made the second replace the first along with its colour and note.
        val shorter = ReaderLocator(
            chapterIndex = 0,
            blockIndex = 3,
            charOffset = 12,
            endOffset = 16,
            textQuote = "beta"
        )
        val longer = ReaderLocator(
            chapterIndex = 0,
            blockIndex = 3,
            charOffset = 12,
            endOffset = 21,
            textQuote = "beta gamma"
        )

        assertFalse(shorter.sameLocation(longer))
        assertFalse(longer.sameLocation(shorter))
    }

    @Test
    fun `a different start in the same block is a different highlight`() {
        val first = ReaderLocator(chapterIndex = 0, blockIndex = 3, charOffset = 12, endOffset = 16)
        val second = ReaderLocator(chapterIndex = 0, blockIndex = 3, charOffset = 13, endOffset = 17)

        assertFalse(first.sameLocation(second))
    }

    @Test
    fun `the same block in two chapters is never the same highlight`() {
        val first = ReaderLocator(chapterIndex = 0, blockIndex = 3, charOffset = 12, endOffset = 16)
        val second = ReaderLocator(chapterIndex = 1, blockIndex = 3, charOffset = 12, endOffset = 16)

        assertFalse(first.sameLocation(second))
    }

    @Test
    fun `extent falls back to the selected text when there is no end`() {
        // A WebView-created locator carries only the selected text and a DOM position.
        val short = ReaderLocator(chapterIndex = 0, cfi = "/2/4", textQuote = "beta")
        val long = ReaderLocator(chapterIndex = 0, cfi = "/2/4", textQuote = "beta gamma")

        assertEquals(4, short.selectedLength)
        assertEquals(10, long.selectedLength)
        assertFalse(short.sameLocation(long))
    }

    @Test
    fun `an extent that cannot be read makes a duplicate rather than an overwrite`() {
        // A collapsed locator with no quote and no end: there is no way to tell it apart from a
        // different collapsed locator, so it must not claim to be one.
        val collapsed = ReaderLocator(chapterIndex = 0, blockIndex = 3, charOffset = 12)
        val other = ReaderLocator(chapterIndex = 0, blockIndex = 3, charOffset = 12)

        assertEquals(null, collapsed.selectedLength)
        assertFalse(collapsed.sameLocation(other))
    }

    @Test
    fun `a collapsed range is length zero`() {
        val collapsed = ReaderLocator(chapterIndex = 0, startOffset = 40, endOffset = 40)

        assertEquals(0, collapsed.selectedLength)
        assertTrue(collapsed.sameLocation(collapsed.copy()))
    }

    @Test
    fun `equal text ranges are the same highlight`() {
        val first = ReaderLocator(chapterIndex = 2, startOffset = 100, endOffset = 118)
        val second = ReaderLocator(chapterIndex = 2, startOffset = 100, endOffset = 118)

        assertTrue(first.sameLocation(second))
        assertEquals(18, first.selectedLength)
    }

    @Test
    fun `one page number alone is not enough`() {
        // Page numbers shift with font size and margins, so a page match was never a position. Two
        // highlights can share a page number and cover different text.
        val first = ReaderLocator(chapterIndex = 0, pageIndex = 5, textQuote = "beta")
        val second = ReaderLocator(chapterIndex = 0, pageIndex = 5, textQuote = "gamma delta")

        assertFalse(first.sameLocation(second))
        assertTrue(first.sameLocation(ReaderLocator(chapterIndex = 0, pageIndex = 5, textQuote = "beta")))
    }
}