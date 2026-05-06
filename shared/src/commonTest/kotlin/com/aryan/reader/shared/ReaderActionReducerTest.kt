package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderReadingMode
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

        val lastPage = previous.reduce(ReaderAction.GoToProgress(1f), engine)
        assertEquals(lastPage.reader.pages.lastIndex, lastPage.reader.currentPageIndex)

        val chapterTwo = lastPage.reduce(ReaderAction.GoToChapter(1), engine)
        assertEquals(1, chapterTwo.reader.currentPage?.chapterIndex)

        val searched = chapterTwo.reduce(ReaderAction.SearchChanged("needle"), engine)
        assertTrue(searched.searchResults.size >= 2)
        assertTrue(searched.activeSearchResultIndex >= 0)

        val nextSearch = searched.reduce(ReaderAction.NextSearchResult, engine)
        assertEquals((searched.activeSearchResultIndex + 1) % searched.searchResults.size, nextSearch.activeSearchResultIndex)

        val directSearch = searched.reduce(ReaderAction.GoToSearchResult(0), engine)
        assertEquals(0, directSearch.activeSearchResultIndex)

        val bookmarked = directSearch.reduce(ReaderAction.ToggleBookmark, engine)
        assertEquals(listOf(directSearch.reader.currentPageIndex), bookmarked.bookmarks.map { it.pageIndex })

        val unbookmarked = bookmarked.reduce(ReaderAction.ToggleBookmark, engine)
        assertTrue(unbookmarked.bookmarks.isEmpty())
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
