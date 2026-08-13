package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderBookmark
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderBookmarkPresentationTest {
    @Test
    fun `drawer keeps first bookmark for each portable location`() {
        val first = bookmark("first", chapter = 1, start = 30)
        val duplicate = bookmark("duplicate", chapter = 1, start = 30)
        val other = bookmark("other", chapter = 1, start = 80)

        assertEquals(
            listOf(first, other),
            listOf(first, duplicate, other).deduplicatedReaderBookmarks(),
        )
    }

    @Test
    fun `active bookmark matches nearby text block before page fallback`() {
        val nearby = ReaderBookmark(
            id = "nearby",
            pageIndex = 5,
            chapterTitle = "Chapter",
            preview = "Nearby",
            locator = ReaderLocator(
                chapterIndex = 2,
                pageIndex = 5,
                blockIndex = 8,
                charOffset = 100,
            ),
        )
        val pageOnly = bookmark("page", chapter = 1, start = 30).copy(pageIndex = 5)

        assertEquals(
            nearby,
            listOf(pageOnly, nearby).matchingReaderBookmark(
                locator = ReaderLocator(
                    chapterIndex = 2,
                    pageIndex = 5,
                    blockIndex = 8,
                    charOffset = 240,
                ),
                visiblePageIndex = 5,
            ),
        )
    }

    @Test
    fun `active bookmark falls back to visible page`() {
        val saved = bookmark("page", chapter = 1, start = 30).copy(pageIndex = 7)
        assertEquals(
            saved,
            listOf(saved).matchingReaderBookmark(
                locator = ReaderLocator(chapterIndex = 3, pageIndex = 7),
                visiblePageIndex = 7,
            ),
        )
    }

    @Test
    fun `toggle removal clears every bookmark matching the visible page`() {
        val first = bookmark("first", chapter = 1, start = 30).copy(pageIndex = 7)
        val duplicate = bookmark("duplicate", chapter = 1, start = 80).copy(pageIndex = 7)
        val other = bookmark("other", chapter = 2, start = 30).copy(pageIndex = 8)

        assertEquals(
            listOf(other),
            listOf(first, duplicate, other).withoutMatchingReaderBookmarks(
                locator = ReaderLocator(chapterIndex = 1, pageIndex = 7),
                visiblePageIndex = 7,
            ),
        )
    }

    private fun bookmark(id: String, chapter: Int, start: Int) = ReaderBookmark(
        id = id,
        pageIndex = 2,
        chapterTitle = "Chapter",
        preview = id,
        locator = ReaderLocator(
            chapterIndex = chapter,
            startOffset = start,
            endOffset = start,
        ),
    )
}
