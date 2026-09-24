package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageInfoTest {
    private val book = SharedEpubBook(
        id = "book",
        fileName = "book.epub",
        title = "Book",
        chapters = listOf(
            SharedEpubChapter(id = "short", title = "Short", plainText = "a".repeat(100)),
            SharedEpubChapter(id = "long", title = "Long", plainText = "b".repeat(900))
        )
    )
    private val pages = listOf(
        ReaderPage(0, 0, "Short", "a".repeat(100), 0, 100),
        ReaderPage(1, 1, "Long", "b".repeat(300), 0, 300),
        ReaderPage(2, 1, "Long", "b".repeat(300), 300, 600),
        ReaderPage(3, 1, "Long", "b".repeat(300), 600, 900)
    )

    @Test
    fun reportsPageWithinCurrentChapter() {
        val info = sharedReaderPageInfo(book, pages, currentPageIndex = 2)

        assertEquals(2, info?.currentPageInChapter)
        assertEquals(3, info?.totalPagesInChapter)
    }

    @Test
    fun progressUsesTextOffsetsRatherThanGlobalPageCount() {
        assertEquals(10.0, sharedReaderPageInfo(book, pages, currentPageIndex = 1)?.progressPercent)
        assertEquals(40.0, sharedReaderPageInfo(book, pages, currentPageIndex = 2)?.progressPercent)
    }

    @Test
    fun finalPageAlwaysReportsOneHundredPercent() {
        assertEquals(100.0, sharedReaderPageInfo(book, pages, currentPageIndex = 3)?.progressPercent)
    }

    @Test
    fun liveLocatorDrivesVerticalPageAndProgressWhenStoredPageIsStale() {
        val info = sharedReaderPageInfo(
            book = book,
            pages = pages,
            currentPageIndex = 0,
            locator = ReaderLocator(chapterIndex = 1, startOffset = 450)
        )

        assertEquals(2, info?.currentPageInChapter)
        assertEquals(55.0, info?.progressPercent)
    }

    @Test
    fun spreadChapterFollowsNormalizedStartNotOddTransient() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )
        // Spread (2,3): page 2 in Short, page 3 in Long — first page wins (Android).
        val spreadPages = listOf(
            ReaderPage(0, 0, "Short", "a", 0, 10),
            ReaderPage(1, 0, "Short", "a", 10, 20),
            ReaderPage(2, 0, "Short", "a", 20, 30),
            ReaderPage(3, 1, "Long", "b", 0, 300),
            ReaderPage(4, 1, "Long", "b", 300, 600)
        )
        // Odd transient index 3 normalizes to spread start 2 (Short), not Long.
        assertEquals(0, sharedPaginatedSpreadChapterIndex(spreadPages, 3, settings))
        assertEquals(0, sharedPaginatedSpreadChapterIndex(spreadPages, 2, settings))
        assertEquals(1, sharedPaginatedSpreadChapterIndex(spreadPages, 4, settings))
    }

    @Test
    fun spreadPositionLabelShowsRangeInsideChapter() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )
        val spreadPages = listOf(
            ReaderPage(0, 0, "Short", "a", 0, 10),
            ReaderPage(1, 0, "Short", "a", 10, 20),
            ReaderPage(2, 1, "Long", "b", 0, 100),
            ReaderPage(3, 1, "Long", "b", 100, 200),
            ReaderPage(4, 1, "Long", "b", 200, 300)
        )
        // Spread (0,1) both Short positions 1-2.
        assertEquals("1-2", sharedPaginatedSpreadPositionLabel(spreadPages, 0, settings))
        // Spread (2,3) both Long positions 1-2.
        assertEquals("1-2", sharedPaginatedSpreadPositionLabel(spreadPages, 2, settings))
        // Odd transient still resolves to the spread start range.
        assertEquals("1-2", sharedPaginatedSpreadPositionLabel(spreadPages, 3, settings))
    }

    @Test
    fun spreadPositionLabelTrimsToFirstChapterOnBoundary() {
        val settings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )
        // Spread (2,3) straddles: page 2 is Short 3/3, page 3 is Long 1/2.
        val boundaryPages = listOf(
            ReaderPage(0, 0, "Short", "a", 0, 10),
            ReaderPage(1, 0, "Short", "a", 10, 20),
            ReaderPage(2, 0, "Short", "a", 20, 30),
            ReaderPage(3, 1, "Long", "b", 0, 100),
            ReaderPage(4, 1, "Long", "b", 100, 200)
        )
        assertEquals(0, sharedPaginatedSpreadChapterIndex(boundaryPages, 2, settings))
        assertEquals("3", sharedPaginatedSpreadPositionLabel(boundaryPages, 2, settings))
    }
}
