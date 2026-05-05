package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderEngineTest {

    @Test
    fun `createSession restores page and valid bookmarks`() {
        val engine = ReaderEngine()
        val book = longBook()
        val restored = engine.createSession(
            book = book,
            initialPageIndex = 2,
            bookmarks = listOf(
                ReaderBookmark("keep", pageIndex = 1, chapterTitle = "One", preview = "Valid"),
                ReaderBookmark("drop", pageIndex = 200, chapterTitle = "One", preview = "Invalid")
            )
        )

        assertEquals(2, restored.reader.currentPageIndex)
        assertEquals(listOf("keep"), restored.bookmarks.map { it.id })
    }

    @Test
    fun `search returns every match on a page`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Alpha beta alpha gamma ALPHA."
                    )
                )
            )
        )

        val searched = engine.search(session, "alpha")

        assertEquals(3, searched.searchResults.size)
        assertEquals(listOf(0, 11, 23), searched.searchResults.map { it.matchIndex })
        assertTrue(searched.searchResults.all { it.pageIndex == 0 })

        val secondMatch = engine.goToSearchResult(searched, 1)

        assertEquals(1, secondMatch.activeSearchResultIndex)
    }

    private fun longBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "long",
            fileName = "long.epub",
            title = "Long",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = List(280) { "This paragraph gives the paginator enough text to create several pages." }
                        .joinToString("\n\n")
                )
            )
        )
    }
}
