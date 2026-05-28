package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator
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
    fun `visible locator and bookmarks prefer android style cfi when semantic blocks provide it`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            SharedEpubBook(
                id = "semantic",
                fileName = "semantic.epub",
                title = "Semantic",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Alpha beta",
                        semanticBlocks = listOf(
                            SemanticParagraph(
                                text = "Alpha beta",
                                spans = emptyList(),
                                style = CssStyle(),
                                elementId = null,
                                cfi = "/4/2/2",
                                startCharOffsetInSource = 0
                            )
                        )
                    )
                )
            )
        )

        val bookmarked = engine.toggleBookmark(session)

        assertEquals("/4/2/2:0", session.navigationLocator?.cfi)
        assertEquals("/4/2/2:0", bookmarked.bookmarks.single().locator.cfi)
    }

    @Test
    fun `visual settings update does not repaginate or move current page`() {
        val engine = ReaderEngine()
        val session = engine.goToPage(engine.createSession(longBook()), 1)
        val oldPages = session.reader.pages
        val oldPageIndex = session.reader.currentPageIndex

        val updated = engine.updateSettings(
            session,
            session.reader.settings.copy(
                darkMode = true,
                themeId = "night",
                backgroundColorArgb = 0xFF101010L,
                textColorArgb = 0xFFEFEFEFL,
                textureId = "paper",
                textureAlpha = 0.25f
            )
        )

        assertSame(oldPages, updated.reader.pages)
        assertEquals(oldPageIndex, updated.reader.currentPageIndex)
        assertEquals("night", updated.reader.settings.themeId)
    }

    @Test
    fun `vertical page navigation uses scroll page locator for webview slider sync`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            book = longBook(),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )

        val moved = engine.goToPage(session, 1)

        assertEquals(1, moved.reader.currentPageIndex)
        assertEquals("desktop-scroll-page:1", moved.navigationLocator?.cfi)
        assertEquals(1, moved.navigationLocator?.pageIndex)
    }

    @Test
    fun `visible page sync stores stable vertical locator cfi instead of scroll metrics`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            book = longBook(),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )
        val page = session.reader.pages[1]
        val locator = ReaderLocator(
            chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            startOffset = page.startOffset + 24,
            endOffset = page.startOffset + 24,
            cfi = "desktop-scroll:120:800:desktop:${page.chapterIndex}:${page.startOffset + 24}:${page.startOffset + 24}"
        )

        val synced = engine.syncVisiblePage(session, page.pageIndex, locator)

        assertEquals("desktop:${page.chapterIndex}:${page.startOffset + 24}:${page.startOffset + 24}", synced.navigationLocator?.cfi)
        assertEquals(locator.startOffset, synced.navigationLocator?.startOffset)
    }

    @Test
    fun `createSession restores precise locator ahead of fallback page index`() {
        val engine = ReaderEngine()
        val book = longBook()
        val base = engine.createSession(book)
        val targetPage = base.reader.pages.getOrNull(2) ?: error("Expected multiple pages")
        val locator = ReaderLocator(
            chapterIndex = targetPage.chapterIndex,
            pageIndex = targetPage.pageIndex,
            startOffset = targetPage.startOffset + 12,
            endOffset = targetPage.startOffset + 12,
            cfi = "desktop:${targetPage.chapterIndex}:${targetPage.startOffset + 12}:${targetPage.startOffset + 12}"
        )

        val restored = engine.createSession(
            book = book,
            initialPageIndex = 0,
            initialLocator = locator
        )

        assertEquals(targetPage.pageIndex, restored.navigationLocator?.pageIndex)
        assertEquals(targetPage.pageIndex, restored.reader.currentPageIndex)
        assertEquals(locator.startOffset, restored.navigationLocator?.startOffset)
    }

    @Test
    fun `layout settings update keeps precise visible locator across reading modes`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook())
        val targetPage = session.reader.pages.getOrNull(1) ?: error("Expected multiple pages")
        val visibleLocator = ReaderLocator(
            chapterIndex = targetPage.chapterIndex,
            pageIndex = targetPage.pageIndex,
            startOffset = targetPage.startOffset + 40,
            endOffset = targetPage.startOffset + 40,
            textQuote = "visible text",
            cfi = "desktop:${targetPage.chapterIndex}:${targetPage.startOffset + 40}:${targetPage.startOffset + 40}"
        )
        val synced = engine.syncVisiblePage(session, targetPage.pageIndex, visibleLocator)

        val updated = engine.updateSettings(
            synced,
            synced.reader.settings.copy(
                readingMode = ReaderReadingMode.VERTICAL,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
                fontSize = synced.reader.settings.fontSize + 4
            )
        )

        val page = updated.reader.currentPage ?: error("Expected current page")
        assertEquals(visibleLocator.startOffset, updated.navigationLocator?.startOffset)
        assertTrue(visibleLocator.startOffset!! in page.startOffset..page.endOffset)
    }

    @Test
    fun `two page spread keeps right page locator while normalizing visible spread start`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook())
        val targetPage = session.reader.pages.getOrNull(3) ?: error("Expected multiple pages")
        val locator = ReaderLocator(
            chapterIndex = targetPage.chapterIndex,
            pageIndex = targetPage.pageIndex,
            startOffset = targetPage.startOffset + 20,
            endOffset = targetPage.startOffset + 20,
            cfi = "desktop:${targetPage.chapterIndex}:${targetPage.startOffset + 20}:${targetPage.startOffset + 20}"
        )
        val synced = engine.syncVisiblePage(session, targetPage.pageIndex, locator)

        val updated = engine.updateSettings(
            synced,
            synced.reader.settings.copy(
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
            )
        )

        assertEquals(targetPage.pageIndex - 1, updated.reader.currentPageIndex)
        assertEquals(targetPage.pageIndex, updated.navigationLocator?.pageIndex)
        assertEquals(locator.startOffset, updated.navigationLocator?.startOffset)
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
        assertEquals(-1, searched.activeSearchResultIndex)

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

    @Test
    fun `jump navigation records locator history and can step back and forward`() {
        val engine = ReaderEngine()
        val session = engine.createSession(multiChapterBook())

        val second = engine.jumpToChapter(session, 1)
        val third = engine.jumpToChapter(second, 2)
        val back = engine.jumpBack(third)
        val forward = engine.jumpForward(back)

        assertEquals(1, third.jumpHistory.backLocator?.chapterIndex)
        assertEquals(1, back.reader.currentPage?.chapterIndex)
        assertEquals(0, back.jumpHistory.backLocator?.chapterIndex)
        assertEquals(2, back.jumpHistory.forwardLocator?.chapterIndex)
        assertEquals(2, forward.reader.currentPage?.chapterIndex)
        assertTrue(engine.clearJumpHistory(forward).jumpHistory.locators.isEmpty())
    }

    @Test
    fun `paginated mode does not record or use jump history`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            book = multiChapterBook(),
            settings = ReaderSettings(readingMode = ReaderReadingMode.PAGINATED)
        )

        val jumped = engine.jumpToChapter(session, 1)
        val verticalWithHistory = engine.jumpToChapter(engine.createSession(multiChapterBook()), 1)
        val switchedToPaginated = engine.updateSettings(
            verticalWithHistory,
            verticalWithHistory.reader.settings.copy(readingMode = ReaderReadingMode.PAGINATED)
        )
        val withLegacyHistory = jumped.copy(
            jumpHistory = ReaderJumpHistory()
                .record(
                    currentLocator = ReaderLocator(chapterIndex = 0, cfi = "desktop:0:0:0"),
                    targetLocator = ReaderLocator(chapterIndex = 1, cfi = "desktop:1:0:0"),
                    chapterCount = 3
                )
        )
        val back = engine.jumpBack(withLegacyHistory)

        assertTrue(jumped.jumpHistory.locators.isEmpty())
        assertTrue(switchedToPaginated.jumpHistory.locators.isEmpty())
        assertEquals(jumped.reader.currentPageIndex, back.reader.currentPageIndex)
        assertTrue(back.jumpHistory.locators.isEmpty())
    }

    @Test
    fun `replacePages uses captured reflow anchor when no newer navigation happened`() {
        val engine = ReaderEngine()
        val book = manualRangeBook()
        val oldPages = listOf(
            ReaderPage(0, 0, "One", "first", 0, 100),
            ReaderPage(1, 0, "One", "second", 100, 200)
        )
        val newPages = listOf(
            ReaderPage(0, 0, "One", "first expanded", 0, 140),
            ReaderPage(1, 0, "One", "second shifted", 140, 260)
        )
        val session = engine.createSession(book).copy(
            reader = PaginatedReaderState(book, oldPages, currentPageIndex = 1),
            navigationLocator = ReaderLocator(chapterIndex = 0, pageIndex = 0, startOffset = 20, endOffset = 20),
            navigationRequestId = 4L
        )
        val reflowAnchor = ReaderLocator(chapterIndex = 0, pageIndex = 1, startOffset = 160, endOffset = 160)

        val replaced = engine.replacePages(
            state = session,
            pages = newPages,
            reflowAnchor = reflowAnchor,
            navigationRequestIdAtReflowStart = 4L
        )

        assertEquals(1, replaced.reader.currentPageIndex)
        assertEquals(1, replaced.navigationLocator?.pageIndex)
        assertEquals(160, replaced.navigationLocator?.startOffset)
    }

    @Test
    fun `replacePages resolves page start anchors to the page after a touching boundary`() {
        val engine = ReaderEngine()
        val book = manualRangeBook()
        val pages = listOf(
            ReaderPage(0, 0, "One", "first", 0, 100),
            ReaderPage(1, 0, "One", "second", 100, 200),
            ReaderPage(2, 0, "One", "third", 200, 300)
        )
        val session = engine.createSession(book).copy(
            reader = PaginatedReaderState(book, pages, currentPageIndex = 1),
            navigationLocator = ReaderLocator(chapterIndex = 0, pageIndex = 1, startOffset = 100, endOffset = 100),
            navigationRequestId = 8L
        )

        val replaced = engine.replacePages(
            state = session,
            pages = pages,
            reflowAnchor = session.navigationLocator,
            navigationRequestIdAtReflowStart = 8L
        )

        assertEquals(1, replaced.reader.currentPageIndex)
        assertEquals(1, replaced.navigationLocator?.pageIndex)
        assertEquals(100, replaced.navigationLocator?.startOffset)
    }

    @Test
    fun `replacePages resolves android block locator after measured pagination`() {
        val engine = ReaderEngine()
        val book = manualRangeBook()
        val initialSession = engine.createSession(
            book = book,
            initialLocator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                blockIndex = 42,
                charOffset = 160,
                cfi = "android-locator:0:42:160"
            )
        )
        val measuredPages = listOf(
            ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "first",
                startOffset = 0,
                endOffset = 100,
                semanticBlocks = listOf(SemanticParagraph("first", emptyList(), CssStyle(), null, null, 0, 7))
            ),
            ReaderPage(
                pageIndex = 1,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "target",
                startOffset = 150,
                endOffset = 210,
                semanticBlocks = listOf(SemanticParagraph("target", emptyList(), CssStyle(), null, null, 150, 42))
            )
        )

        val replaced = engine.replacePages(
            state = initialSession,
            pages = measuredPages,
            reflowAnchor = initialSession.navigationLocator,
            navigationRequestIdAtReflowStart = initialSession.navigationRequestId
        )

        assertEquals(1, replaced.reader.currentPageIndex)
        assertEquals(1, replaced.navigationLocator?.pageIndex)
        assertEquals(42, replaced.navigationLocator?.blockIndex)
        assertEquals(160, replaced.navigationLocator?.charOffset)
    }

    @Test
    fun `replacePages lets newer explicit navigation override reflow anchor`() {
        val engine = ReaderEngine()
        val book = manualRangeBook()
        val oldPages = listOf(
            ReaderPage(0, 0, "One", "first", 0, 100),
            ReaderPage(1, 0, "One", "second", 100, 200)
        )
        val newPages = listOf(
            ReaderPage(0, 0, "One", "first expanded", 0, 140),
            ReaderPage(1, 0, "One", "second shifted", 140, 260)
        )
        val session = engine.createSession(book).copy(
            reader = PaginatedReaderState(book, oldPages, currentPageIndex = 0),
            navigationLocator = ReaderLocator(chapterIndex = 0, pageIndex = 0, startOffset = 20, endOffset = 20),
            navigationRequestId = 5L
        )
        val staleReflowAnchor = ReaderLocator(chapterIndex = 0, pageIndex = 1, startOffset = 160, endOffset = 160)

        val replaced = engine.replacePages(
            state = session,
            pages = newPages,
            reflowAnchor = staleReflowAnchor,
            navigationRequestIdAtReflowStart = 4L
        )

        assertEquals(0, replaced.reader.currentPageIndex)
        assertEquals(0, replaced.navigationLocator?.pageIndex)
        assertEquals(20, replaced.navigationLocator?.startOffset)
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

    private fun multiChapterBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "multi",
            fileName = "multi.epub",
            title = "Multi",
            chapters = listOf(
                SharedEpubChapter(id = "one", title = "One", plainText = "First chapter text."),
                SharedEpubChapter(id = "two", title = "Two", plainText = "Second chapter text."),
                SharedEpubChapter(id = "three", title = "Three", plainText = "Third chapter text.")
            )
        )
    }

    private fun manualRangeBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "manual",
            fileName = "manual.epub",
            title = "Manual",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = List(300) { "x" }.joinToString("")
                )
            )
        )
    }
}
