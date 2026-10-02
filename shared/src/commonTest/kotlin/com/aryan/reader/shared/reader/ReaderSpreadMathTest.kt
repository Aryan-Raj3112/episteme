package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parity item B5 made this the single source of spread arithmetic for both
 * platforms. The page-indexed half is what [ReaderSpreadLayout] serves; the
 * spread-indexed half is what Android's pager needs, and it moved here too so
 * the two bases cannot drift apart inside one file.
 */
class ReaderSpreadMathTest {

    @Test
    fun `spread count is zero for an empty book but slider still has one step`() {
        for (twoPage in MODES) {
            assertEquals(0, ReaderSpreadMath.spreadCount(0, twoPage))
            assertEquals(0, ReaderSpreadMath.spreadCount(-3, twoPage))
            assertEquals(1, ReaderSpreadMath.sliderStepCount(0, twoPage))
            assertEquals(1, ReaderSpreadMath.sliderStepCount(-3, twoPage))
        }
    }

    @Test
    fun `spread count halves the page count only in two page mode`() {
        assertEquals(5, ReaderSpreadMath.spreadCount(5, false))
        assertEquals(3, ReaderSpreadMath.spreadCount(5, true))
        assertEquals(1, ReaderSpreadMath.spreadCount(1, true))
        assertEquals(1, ReaderSpreadMath.spreadCount(2, true))
        assertEquals(2, ReaderSpreadMath.spreadCount(3, true))
    }

    @Test
    fun `normalize aligns to the spread start and clamps out of range input`() {
        assertEquals(3, ReaderSpreadMath.normalizePageIndex(3, 10, false))
        assertEquals(9, ReaderSpreadMath.normalizePageIndex(99, 10, false))
        assertEquals(0, ReaderSpreadMath.normalizePageIndex(-4, 10, false))

        assertEquals(2, ReaderSpreadMath.normalizePageIndex(3, 10, true))
        assertEquals(8, ReaderSpreadMath.normalizePageIndex(99, 10, true))
        assertEquals(0, ReaderSpreadMath.normalizePageIndex(-4, 10, true))
        assertEquals(0, ReaderSpreadMath.normalizePageIndex(4, 0, true))
    }

    @Test
    fun `visible indices are empty for an empty book and never overrun the last page`() {
        assertEquals(emptyList(), ReaderSpreadMath.visiblePageIndices(0, 0, false))
        assertEquals(emptyList(), ReaderSpreadMath.visiblePageIndices(0, 0, true))
        assertEquals(listOf(3), ReaderSpreadMath.visiblePageIndices(3, 10, false))
        assertEquals(listOf(2, 3), ReaderSpreadMath.visiblePageIndices(3, 10, true))
        assertEquals(listOf(8, 9), ReaderSpreadMath.visiblePageIndices(8, 10, true))
        // 99 clamps to 9, which then aligns down to the spread starting at 8.
        assertEquals(listOf(8, 9), ReaderSpreadMath.visiblePageIndices(99, 10, true))
    }

    @Test
    fun `display order reverses for right to left only`() {
        assertEquals(listOf(2, 3), ReaderSpreadMath.visiblePageIndicesForDisplay(3, 10, true, false))
        assertEquals(listOf(3, 2), ReaderSpreadMath.visiblePageIndicesForDisplay(3, 10, true, true))
        assertEquals(listOf(3), ReaderSpreadMath.visiblePageIndicesForDisplay(3, 10, false, true))
    }

    @Test
    fun `page range label collapses a single page and spans a pair`() {
        assertEquals("4", ReaderSpreadMath.pageRangeLabel(3, 10, false))
        assertEquals("3-4", ReaderSpreadMath.pageRangeLabel(3, 10, true))
        assertEquals("9-10", ReaderSpreadMath.pageRangeLabel(9, 10, true))
        assertEquals("1", ReaderSpreadMath.pageRangeLabel(0, 0, true))
    }

    @Test
    fun `slider positions round trip through page numbers`() {
        for (twoPage in MODES) {
            for (pageCount in 1..30) {
                val steps = ReaderSpreadMath.sliderStepCount(pageCount, twoPage)
                for (position in 1..steps) {
                    val pageNumber = ReaderSpreadMath.pageNumberForSliderPosition(position, pageCount, twoPage)
                    assertEquals(
                        position,
                        ReaderSpreadMath.sliderPositionForPage(pageNumber - 1, pageCount, twoPage),
                        "pageIndex from slider $position of $steps (twoPage=$twoPage)"
                    )
                }
            }
        }
    }

    @Test
    fun `slider positions are always inside the track`() {
        for (twoPage in MODES) {
            for (pageCount in 0..10) {
                val steps = ReaderSpreadMath.sliderStepCount(pageCount, twoPage)
                for (pageIndex in -5..(pageCount + 5)) {
                    val position = ReaderSpreadMath.sliderPositionForPage(pageIndex, pageCount, twoPage)
                    assertTrue(
                        position in 1..steps,
                        "$pageIndex/$pageCount (twoPage=$twoPage) gave $position"
                    )
                }
            }
        }
    }

    @Test
    fun `next and previous step by one page or one spread`() {
        assertEquals(3, ReaderSpreadMath.nextPageIndex(2, 10, false))
        assertEquals(4, ReaderSpreadMath.nextPageIndex(2, 10, true))
        assertEquals(1, ReaderSpreadMath.previousPageIndex(2, 10, false))
        assertEquals(0, ReaderSpreadMath.previousPageIndex(2, 10, true))
        assertEquals(1, ReaderSpreadMath.pageStep(false))
        assertEquals(2, ReaderSpreadMath.pageStep(true))
    }

    @Test
    fun `can go next is false at the end of the book`() {
        assertFalse(ReaderSpreadMath.canGoNext(9, 10, false))
        assertTrue(ReaderSpreadMath.canGoNext(8, 10, false))
        assertFalse(ReaderSpreadMath.canGoNext(8, 10, true))
        assertTrue(ReaderSpreadMath.canGoNext(6, 10, true))
        assertFalse(ReaderSpreadMath.canGoNext(0, 1, false))
        assertFalse(ReaderSpreadMath.canGoNext(0, 0, false))
    }

    @Test
    fun `spread index translates to an even book page inside range`() {
        assertEquals(0, ReaderSpreadMath.spreadToBookPage(0, 5, true))
        assertEquals(2, ReaderSpreadMath.spreadToBookPage(1, 5, true))
        assertEquals(4, ReaderSpreadMath.spreadToBookPage(2, 5, true))
        assertEquals(4, ReaderSpreadMath.spreadToBookPage(99, 5, true))
        assertEquals(0, ReaderSpreadMath.spreadToBookPage(-3, 5, true))
        assertEquals(0, ReaderSpreadMath.spreadToBookPage(4, 0, true))

        assertEquals(3, ReaderSpreadMath.spreadToBookPage(3, 5, false))
        assertEquals(0, ReaderSpreadMath.spreadToBookPage(0, 5, false))
    }

    @Test
    fun `book page maps back to the spread that contains it`() {
        assertEquals(0, ReaderSpreadMath.bookPageToSpread(0, 5, true))
        assertEquals(0, ReaderSpreadMath.bookPageToSpread(1, 5, true))
        assertEquals(1, ReaderSpreadMath.bookPageToSpread(2, 5, true))
        assertEquals(2, ReaderSpreadMath.bookPageToSpread(4, 5, true))
        assertEquals(2, ReaderSpreadMath.bookPageToSpread(99, 5, true))
        assertEquals(3, ReaderSpreadMath.bookPageToSpread(3, 5, false))
        assertEquals(0, ReaderSpreadMath.bookPageToSpread(4, 0, true))
    }

    @Test
    fun `normalize spread index clamps into the spread range`() {
        assertEquals(0, ReaderSpreadMath.normalizeSpreadIndex(-1, 5, true))
        assertEquals(2, ReaderSpreadMath.normalizeSpreadIndex(99, 5, true))
        assertEquals(1, ReaderSpreadMath.normalizeSpreadIndex(1, 5, true))
        assertEquals(4, ReaderSpreadMath.normalizeSpreadIndex(99, 5, false))
        assertEquals(0, ReaderSpreadMath.normalizeSpreadIndex(1, 0, true))
    }

    @Test
    fun `raw spread is never negative and ignores the page count`() {
        assertEquals(0, ReaderSpreadMath.rawBookPageToSpread(-5, false))
        assertEquals(3, ReaderSpreadMath.rawBookPageToSpread(3, false))
        assertEquals(0, ReaderSpreadMath.rawBookPageToSpread(-5, true))
        assertEquals(1, ReaderSpreadMath.rawBookPageToSpread(3, true))
        assertEquals(250, ReaderSpreadMath.rawBookPageToSpread(500, true))
    }

    private companion object {
        val MODES = listOf(false, true)
    }
}
