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
}
