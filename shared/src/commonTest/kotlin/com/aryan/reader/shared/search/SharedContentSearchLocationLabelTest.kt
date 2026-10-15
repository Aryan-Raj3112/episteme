package com.aryan.reader.shared.search

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ui.sharedContentSearchLocationLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedContentSearchLocationLabelTest {

    private fun hit(chapterIndex: Int? = null, pageIndex: Int? = null) = SharedGlobalSearchHit(
        bookId = "b",
        chunkIndex = 0,
        locator = ReaderLocator(chapterIndex = chapterIndex, pageIndex = pageIndex),
        snippet = "s",
        score = 1.0,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
    )

    @Test
    fun `chapter only labels chapter 1-based`() {
        assertEquals("Chapter 3", sharedContentSearchLocationLabel(hit(chapterIndex = 2)))
    }

    @Test
    fun `page only labels page 1-based`() {
        assertEquals("Page 5", sharedContentSearchLocationLabel(hit(pageIndex = 4)))
    }

    @Test
    fun `chapter and page combine`() {
        assertEquals(
            "Chapter 2 · Page 7",
            sharedContentSearchLocationLabel(hit(chapterIndex = 1, pageIndex = 6)),
        )
    }

    @Test
    fun `no location is blank`() {
        assertEquals("", sharedContentSearchLocationLabel(hit()))
    }
}
