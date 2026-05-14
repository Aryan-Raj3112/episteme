package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderActionReducerTest {

    @Test
    fun `reader actions navigate search and toggle bookmarks through shared reducer`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())
        assertTrue(session.reader.pages.size > 2)

        val pageTwo = session.reduce(ReaderAction.NextPage, engine)
        assertEquals(1, pageTwo.reader.currentPageIndex)

        val previous = pageTwo.reduce(ReaderAction.PreviousPage, engine)
        assertEquals(0, previous.reader.currentPageIndex)

        val pageByNumber = previous.reduce(ReaderAction.GoToPageNumber(2), engine)
        assertEquals(1, pageByNumber.reader.currentPageIndex)

        val lastPage = previous.reduce(ReaderAction.GoToProgress(1f), engine)
        assertEquals(lastPage.reader.pages.lastIndex, lastPage.reader.currentPageIndex)

        val chapterTwo = lastPage.reduce(ReaderAction.GoToChapter(1), engine)
        assertEquals(1, chapterTwo.reader.currentPage?.chapterIndex)

        val searched = chapterTwo.reduce(ReaderAction.SearchChanged("needle"), engine)
        assertTrue(searched.searchResults.size >= 2)
        assertEquals(-1, searched.activeSearchResultIndex)
        assertEquals(chapterTwo.reader.currentPageIndex, searched.reader.currentPageIndex)

        val nextSearch = searched.reduce(ReaderAction.NextSearchResult, engine)
        assertEquals(
            searched.searchResults.indexOfFirst { it.pageIndex >= searched.reader.currentPageIndex },
            nextSearch.activeSearchResultIndex
        )

        val directSearch = searched.reduce(ReaderAction.GoToSearchResult(0), engine)
        assertEquals(0, directSearch.activeSearchResultIndex)

        val bookmarked = directSearch.reduce(ReaderAction.ToggleBookmark, engine)
        assertEquals(listOf(directSearch.reader.currentPageIndex), bookmarked.bookmarks.map { it.pageIndex })

        val unbookmarked = bookmarked.reduce(ReaderAction.ToggleBookmark, engine)
        assertTrue(unbookmarked.bookmarks.isEmpty())
    }

    @Test
    fun `search options and search chrome state are owned by shared reducer`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            book = SharedEpubBook(
                id = "search",
                fileName = "search.epub",
                title = "Search",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Alpha alphabet alpha ALPHA"
                    )
                )
            ),
            settings = compactSettings()
        )

        val opened = session.reduce(ReaderAction.SearchOpened, engine)
        val caseSensitive = opened
            .reduce(ReaderAction.SearchOptionsChanged(ReaderSearchOptions(matchCase = true)), engine)
            .reduce(ReaderAction.SearchChanged("alpha"), engine)
        val wholeWords = caseSensitive
            .reduce(
                ReaderAction.SearchOptionsChanged(
                    ReaderSearchOptions(matchCase = true, wholeWords = true)
                ),
                engine
            )
        val hiddenPanel = wholeWords.reduce(ReaderAction.SearchResultsPanelToggled, engine)
        val closed = hiddenPanel.reduce(ReaderAction.SearchClosed, engine)

        assertTrue(opened.isSearchActive)
        assertTrue(opened.showSearchResultsPanel)
        assertEquals(2, caseSensitive.searchResults.size)
        assertEquals(-1, caseSensitive.activeSearchResultIndex)
        assertEquals(session.reader.currentPageIndex, caseSensitive.reader.currentPageIndex)
        assertEquals(1, wholeWords.searchResults.size)
        assertEquals(false, hiddenPanel.showSearchResultsPanel)
        assertEquals("", closed.searchQuery)
        assertTrue(closed.searchResults.isEmpty())
        assertEquals(-1, closed.activeSearchResultIndex)
        assertTrue(closed.showSearchResultsPanel)
    }

    @Test
    fun `search navigation resumes from page position after page slider moves off a match`() {
        val engine = ReaderEngine()
        val book = SharedEpubBook(
            id = "spaced-search",
            fileName = "spaced.epub",
            title = "Spaced",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = buildString {
                        append("needle\n\n")
                        repeat(320) { index ->
                            append("Paragraph ")
                            append(index)
                            append(" contains filler words for pagination only.\n\n")
                        }
                        append("final needle")
                    }
                )
            )
        )
        val session = engine.createSession(book, settings = compactSettings())
        val searched = session.reduce(ReaderAction.SearchChanged("needle"), engine)
        val middlePage = searched.reader.pages.indices.first { pageIndex ->
            searched.searchResults.none { result -> result.pageIndex == pageIndex }
        }

        val moved = searched.reduce(ReaderAction.GoToPage(middlePage), engine)
        val next = moved.reduce(ReaderAction.NextSearchResult, engine)
        val previous = moved.reduce(ReaderAction.PreviousSearchResult, engine)

        assertEquals(2, searched.searchResults.size)
        assertEquals(-1, moved.activeSearchResultIndex)
        assertTrue(moved.canGoToPreviousSearchResult)
        assertTrue(moved.canGoToNextSearchResult)
        assertEquals(1, next.activeSearchResultIndex)
        assertEquals(0, previous.activeSearchResultIndex)
    }

    @Test
    fun `settings theme and render actions update shared reader settings`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())

        val settings = session.reader.settings.copy(fontSize = 24, pageWidth = 900, textAlign = SharedReaderTextAlign.CENTER)
        val changed = session.reduce(ReaderAction.SettingsChanged(settings), engine)
        assertEquals(24, changed.reader.settings.fontSize)
        assertEquals(900, changed.reader.settings.pageWidth)
        assertEquals(SharedReaderTextAlign.CENTER, changed.reader.settings.textAlign)

        val vertical = changed.reduce(ReaderAction.RenderModeChanged(RenderMode.VERTICAL_SCROLL), engine)
        assertEquals(ReaderReadingMode.VERTICAL, vertical.reader.settings.readingMode)

        val dark = vertical.reduce(
            ReaderAction.ThemeChanged(
                ReaderTheme(
                    id = "dark",
                    name = "Dark",
                    backgroundColor = Color.Black,
                    textColor = Color.White,
                    isDark = true
                )
            ),
            engine
        )
        assertTrue(dark.reader.settings.darkMode)
        assertEquals(-16777216L, dark.reader.settings.backgroundColorArgb)
        assertEquals(-1L, dark.reader.settings.textColorArgb)
    }

    @Test
    fun `annotation actions use shared locators for navigation and edits`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())
            .reduce(ReaderAction.GoToPage(1), engine)
        val page = session.reader.currentPage ?: error("Expected current page")
        val locator = ReaderLocator(
            chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            startOffset = page.startOffset + 4,
            endOffset = page.startOffset + 18,
            textQuote = "shared locator",
            cfi = "desktop:${page.chapterIndex}:${page.startOffset + 4}:${page.startOffset + 18}"
        )

        val highlighted = session.reduce(
            ReaderAction.HighlightCreated(
                UserHighlight(
                    id = "highlight-1",
                    cfi = locator.cfi ?: "desktop",
                    text = "shared locator",
                    color = HighlightColor.YELLOW,
                    chapterIndex = page.chapterIndex,
                    locator = locator
                )
            ),
            engine
        )
        val noted = highlighted.reduce(ReaderAction.HighlightUpdated("highlight-1", note = "Keep this"), engine)
        val recolored = noted.reduce(ReaderAction.HighlightUpdated("highlight-1", color = HighlightColor.GREEN), engine)
        val jumped = session.reduce(ReaderAction.GoToLocator(locator), engine)
        val deleted = recolored.reduce(ReaderAction.HighlightDeleted("highlight-1"), engine)

        assertEquals(locator.startOffset, highlighted.highlights.single().locator.startOffset)
        assertEquals("Keep this", recolored.highlights.single().note)
        assertEquals(HighlightColor.GREEN, recolored.highlights.single().color)
        assertEquals(page.pageIndex, jumped.reader.currentPageIndex)
        assertEquals(locator.startOffset, jumped.navigationLocator?.startOffset)
        assertEquals(locator.endOffset, jumped.navigationLocator?.endOffset)
        assertTrue(deleted.highlights.isEmpty())
    }

    @Test
    fun `reader navigation stores locator for vertical scroll targets`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())
        val secondPage = session.reduce(ReaderAction.GoToPage(1), engine)
        val secondChapter = secondPage.reduce(ReaderAction.GoToChapter(1), engine)
        val search = secondChapter.reduce(ReaderAction.SearchChanged("needle"), engine)
        val searchTarget = search.searchResults.first()
        val jumpedToSearch = search.reduce(ReaderAction.GoToSearchResult(0), engine)

        assertEquals(secondPage.reader.currentPage?.startOffset, secondPage.navigationLocator?.startOffset)
        assertEquals(1, secondChapter.navigationLocator?.chapterIndex)
        assertEquals(searchTarget.locator.startOffset, jumpedToSearch.navigationLocator?.startOffset)
        assertEquals(searchTarget.locator.endOffset, jumpedToSearch.navigationLocator?.endOffset)
    }

    @Test
    fun `visible page sync updates slider position without creating navigation request`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())
        val navigated = session.reduce(ReaderAction.GoToPage(1), engine)
        val requestId = navigated.navigationRequestId
        val synced = navigated.reduce(ReaderAction.VisiblePageChanged(3), engine)

        assertEquals(3, synced.reader.currentPageIndex)
        assertEquals(requestId, synced.navigationRequestId)
        assertEquals(navigated.navigationLocator, synced.navigationLocator)
    }

    @Test
    fun `visible locator sync feeds top visible bookmark location`() {
        val engine = ReaderEngine()
        val session = engine.createSession(longBook(), settings = compactSettings())
        val page = session.reader.pages[1]
        val locator = ReaderLocator(
            chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            startOffset = page.startOffset + 25,
            endOffset = page.startOffset + 25,
            textQuote = "top visible text",
            cfi = "desktop:${page.chapterIndex}:${page.startOffset + 25}:${page.startOffset + 25}"
        )

        val synced = session.reduce(ReaderAction.VisiblePageChanged(page.pageIndex, locator), engine)
        val bookmarked = synced.reduce(ReaderAction.ToggleBookmark, engine)

        assertEquals(locator.startOffset, synced.navigationLocator?.startOffset)
        assertEquals(locator.startOffset, bookmarked.bookmarks.single().locator.startOffset)
        assertEquals("top visible text", bookmarked.bookmarks.single().preview)
        assertTrue(bookmarked.reduce(ReaderAction.ToggleBookmark, engine).bookmarks.isEmpty())
    }

    @Test
    fun `format action maps Android style reader appearance to shared reader settings`() {
        val engine = ReaderEngine()
        val session = engine.createSession(
            book = longBook(),
            settings = compactSettings().copy(darkMode = true, readingMode = ReaderReadingMode.VERTICAL, pageWidth = 812)
        )

        val updated = session.reduce(
            ReaderAction.FormatChanged(
                FormatSettings(
                    fontSize = 1.5f,
                    lineHeight = 1.2f,
                    paragraphGap = 0.8f,
                    imageSize = 1.3f,
                    horizontalMargin = 0.5f,
                    verticalMargin = 2.0f,
                    font = ReaderFont.ROBOTO_MONO,
                    customPath = null,
                    textAlign = ReaderTextAlign.JUSTIFY
                )
            ),
            engine
        )

        assertEquals(27, updated.reader.settings.fontSize)
        assertEquals(1.74f, updated.reader.settings.lineSpacing, 0.0001f)
        assertEquals(96, updated.reader.settings.margin)
        assertEquals(24, updated.reader.settings.resolvedHorizontalMargin)
        assertEquals(96, updated.reader.settings.resolvedVerticalMargin)
        assertEquals(0.8f, updated.reader.settings.paragraphSpacing, 0.0001f)
        assertEquals(1.3f, updated.reader.settings.imageScale, 0.0001f)
        assertEquals("Mono", updated.reader.settings.fontFamily)
        assertEquals(SharedReaderTextAlign.JUSTIFY, updated.reader.settings.textAlign)
        assertTrue(updated.reader.settings.darkMode)
        assertEquals(ReaderReadingMode.VERTICAL, updated.reader.settings.readingMode)
        assertEquals(812, updated.reader.settings.pageWidth)
    }

    private fun compactSettings(): ReaderSettings {
        return ReaderSettings(fontSize = 14, margin = 16, lineSpacing = 1.1f, pageWidth = 560)
    }

    private fun longBook(): SharedEpubBook {
        val repeated = List(240) { index ->
            "Paragraph $index gives the paginator enough text to create several pages with a needle hidden inside."
        }.joinToString("\n\n")
        return SharedEpubBook(
            id = "long",
            fileName = "long.epub",
            title = "Long",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = repeated
                ),
                SharedEpubChapter(
                    id = "two",
                    title = "Two",
                    plainText = "Second chapter starts here. Another needle appears for search navigation. $repeated"
                )
            )
        )
    }
}
