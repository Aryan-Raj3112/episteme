package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderSpreadLayoutTest {

    @Test
    fun `single page mode keeps direct page indexes`() {
        val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.SINGLE)

        assertEquals(3, ReaderSpreadLayout.normalizePageIndex(3, pageCount = 10, settings = settings))
        assertEquals(listOf(3), ReaderSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals("4", ReaderSpreadLayout.pageRangeLabel(3, pageCount = 10, settings = settings))
    }

    @Test
    fun `two page mode normalizes direct jumps to the spread start`() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )

        assertEquals(2, ReaderSpreadLayout.normalizePageIndex(3, pageCount = 10, settings = settings))
        assertEquals(listOf(2, 3), ReaderSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals("3-4", ReaderSpreadLayout.pageRangeLabel(3, pageCount = 10, settings = settings))
        assertEquals(2, ReaderSpreadLayout.sliderPositionForPage(3, pageCount = 10, settings = settings))
        assertEquals(3, ReaderSpreadLayout.pageNumberForSliderPosition(2, pageCount = 10, settings = settings))
    }

    @Test
    fun `right to left pagination reverses only the displayed spread order`() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
            rightToLeftPagination = true
        )

        assertEquals(listOf(2, 3), ReaderSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals(listOf(3, 2), ReaderSpreadLayout.visiblePageIndicesForDisplay(3, pageCount = 10, settings = settings))
        assertEquals(4, ReaderSpreadLayout.nextPageIndex(2, pageCount = 10, settings = settings))
        assertEquals(0, ReaderSpreadLayout.previousPageIndex(2, pageCount = 10, settings = settings))
    }

    @Test
    fun `two page mode advances by spread and clamps odd final page`() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )

        assertEquals(3, ReaderSpreadLayout.sliderStepCount(pageCount = 5, settings = settings))
        assertEquals(2, ReaderSpreadLayout.nextPageIndex(0, pageCount = 5, settings = settings))
        assertEquals(4, ReaderSpreadLayout.nextPageIndex(2, pageCount = 5, settings = settings))
        assertEquals(listOf(4), ReaderSpreadLayout.visiblePageIndices(4, pageCount = 5, settings = settings))
        assertEquals(5, ReaderSpreadLayout.pageNumberForSliderPosition(3, pageCount = 5, settings = settings))
        assertFalse(ReaderSpreadLayout.canGoNext(4, pageCount = 5, settings = settings))
        assertTrue(ReaderSpreadLayout.canGoNext(2, pageCount = 5, settings = settings))
    }

    @Test
    fun `two page progress uses the visible spread end`() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )
        val state = PaginatedReaderState(
            book = SharedEpubBook("book", "book.epub", "Book", chapters = emptyList()),
            pages = (0 until 4).map { index ->
                ReaderPage(index, chapterIndex = 0, chapterTitle = "One", text = "$index", startOffset = index, endOffset = index + 1)
            },
            currentPageIndex = 2,
            settings = settings
        )

        assertEquals(100f, state.progress)
    }

    @Test
    fun `spread mode is ignored in vertical reading`() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.VERTICAL,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )

        assertEquals(3, ReaderSpreadLayout.normalizePageIndex(3, pageCount = 10, settings = settings))
        assertEquals(listOf(3), ReaderSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals(1, ReaderSpreadLayout.pageStep(settings))
    }
}
