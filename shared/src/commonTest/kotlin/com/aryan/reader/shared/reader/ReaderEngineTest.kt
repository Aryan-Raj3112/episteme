package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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
    fun `createSession reuses paginated pages for the same book and settings`() {
        val engine = ReaderEngine()
        val book = longBook()

        val first = engine.createSession(book)
        val second = engine.createSession(book)

        assertSame(first.reader.pages, second.reader.pages)
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

    @Test
    fun `resolveLink returns external target for web urls`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook())

        val target = engine.resolveLink(session, "https://example.com/page", sourceChapterIndex = 0)

        assertTrue(target is ReaderLinkTarget.External)
        target as ReaderLinkTarget.External
        assertEquals("https://example.com/page", target.url)
    }

    @Test
    fun `resolveLink normalizes scheme-less web links`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook())

        val target = engine.resolveLink(session, "www.example.com/page", sourceChapterIndex = 0)

        assertTrue(target is ReaderLinkTarget.External)
        target as ReaderLinkTarget.External
        assertEquals("https://www.example.com/page", target.url)
    }

    @Test
    fun `resolveLink maps relative epub href to target chapter locator`() {
        val engine = ReaderEngine()
        val targetText = "Intro target paragraph"
        val session = engine.createSession(
            SharedEpubBook(
                id = "links",
                fileName = "links.epub",
                title = "Links",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Source chapter",
                        baseHref = "Text/one.xhtml"
                    ),
                    SharedEpubChapter(
                        id = "two",
                        title = "Two",
                        plainText = targetText,
                        semanticBlocks = listOf(
                            SemanticParagraph(
                                text = targetText,
                                spans = emptyList(),
                                style = CssStyle(),
                                elementId = "target",
                                cfi = null,
                                startCharOffsetInSource = 6
                            )
                        ),
                        baseHref = "Text/two.xhtml"
                    )
                )
            )
        )

        val target = engine.resolveLink(session, "two.xhtml?unused=1#target", sourceChapterIndex = 0)

        assertTrue(target is ReaderLinkTarget.Internal)
        target as ReaderLinkTarget.Internal
        assertEquals(1, target.locator.chapterIndex)
        assertEquals(6, target.locator.startOffset)
    }

    @Test
    fun `resolveLink maps intercepted about blank fragment to source chapter locator`() {
        val engine = ReaderEngine()
        val text = "Source target paragraph"
        val session = engine.createSession(
            SharedEpubBook(
                id = "links",
                fileName = "links.epub",
                title = "Links",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = text,
                        semanticBlocks = listOf(
                            SemanticParagraph(
                                text = text,
                                spans = emptyList(),
                                style = CssStyle(),
                                elementId = "spot",
                                cfi = null,
                                startCharOffsetInSource = 7
                            )
                        ),
                        baseHref = "Text/one.xhtml"
                    )
                )
            )
        )

        val target = engine.resolveLink(session, "about:blank#spot", sourceChapterIndex = 0)

        assertTrue(target is ReaderLinkTarget.Internal)
        target as ReaderLinkTarget.Internal
        assertEquals(0, target.locator.chapterIndex)
        assertEquals(7, target.locator.startOffset)
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
