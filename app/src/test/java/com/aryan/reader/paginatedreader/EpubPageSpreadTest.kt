package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPageSpreadTest {

    @Test
    fun `single mode is identity`() {
        assertEquals(5, EpubPageSpread.spreadCount(5, false))
        assertEquals(3, EpubPageSpread.spreadToBookPage(3, 5, false))
        assertEquals(3, EpubPageSpread.bookPageToSpread(3, 5, false))
        assertEquals(listOf(3), EpubPageSpread.visibleBookPages(3, 5, false))
        assertEquals("4", EpubPageSpread.pageRangeLabel(3, 5, false))
    }

    @Test
    fun `spread count halves odd totals`() {
        assertEquals(0, EpubPageSpread.spreadCount(0, true))
        assertEquals(1, EpubPageSpread.spreadCount(1, true))
        assertEquals(1, EpubPageSpread.spreadCount(2, true))
        assertEquals(2, EpubPageSpread.spreadCount(3, true))
        assertEquals(3, EpubPageSpread.spreadCount(5, true))
        assertEquals(50, EpubPageSpread.spreadCount(100, true))
    }

    @Test
    fun `spread to book maps to even start clamped to total`() {
        assertEquals(0, EpubPageSpread.spreadToBookPage(0, 5, true))
        assertEquals(2, EpubPageSpread.spreadToBookPage(1, 5, true))
        assertEquals(4, EpubPageSpread.spreadToBookPage(2, 5, true))
        // Out-of-range spreads clamp to the last book page.
        assertEquals(4, EpubPageSpread.spreadToBookPage(99, 5, true))
        assertEquals(0, EpubPageSpread.spreadToBookPage(-3, 5, true))
    }

    @Test
    fun `book to spread halves the index`() {
        assertEquals(0, EpubPageSpread.bookPageToSpread(0, 5, true))
        assertEquals(0, EpubPageSpread.bookPageToSpread(1, 5, true))
        assertEquals(1, EpubPageSpread.bookPageToSpread(2, 5, true))
        assertEquals(2, EpubPageSpread.bookPageToSpread(4, 5, true))
        assertEquals(2, EpubPageSpread.bookPageToSpread(99, 5, true))
    }

    @Test
    fun `visible pages pair up with single tail`() {
        assertEquals(listOf(0, 1), EpubPageSpread.visibleBookPages(0, 5, true))
        assertEquals(listOf(2, 3), EpubPageSpread.visibleBookPages(1, 5, true))
        assertEquals(listOf(4), EpubPageSpread.visibleBookPages(2, 5, true))
        assertEquals(emptyList<Int>(), EpubPageSpread.visibleBookPages(0, 0, true))
    }

    @Test
    fun `display order reverses for right-to-left`() {
        assertEquals(
            listOf(1, 0),
            EpubPageSpread.visibleBookPagesForDisplay(0, 5, true, isRightToLeft = true)
        )
        assertEquals(
            listOf(0, 1),
            EpubPageSpread.visibleBookPagesForDisplay(0, 5, true, isRightToLeft = false)
        )
        assertEquals(
            listOf(4),
            EpubPageSpread.visibleBookPagesForDisplay(5, 5, true, isRightToLeft = true)
        )
    }

    @Test
    fun `range labels show pairs`() {
        assertEquals("1-2", EpubPageSpread.pageRangeLabel(0, 5, true))
        assertEquals("3-4", EpubPageSpread.pageRangeLabel(1, 5, true))
        assertEquals("5", EpubPageSpread.pageRangeLabel(2, 5, true))
    }

    @Test
    fun `round trip preserves the containing spread`() {
        val total = 7
        for (book in 0 until total) {
            val spread = EpubPageSpread.bookPageToSpread(book, total, true)
            val visible = EpubPageSpread.visibleBookPages(spread, total, true)
            assert(visible.contains(book)) { "book=$book spread=$spread visible=$visible" }
        }
    }
}
