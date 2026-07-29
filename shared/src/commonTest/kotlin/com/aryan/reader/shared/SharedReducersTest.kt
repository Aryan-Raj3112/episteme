package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedReducersTest {

    @Test
    fun `book selection can be replaced in one reducer action`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("old"))

        val result = state.reduce(LibraryAction.BookSelectionReplaced(setOf("one", "two")))

        assertEquals(setOf("one", "two"), result.selectedBookIds)
    }

    @Test
    fun `visible selection helper selects visible books and clears when all are selected`() {
        val visibleBooks = listOf(
            BookItem("one", "/books/one.epub", FileType.EPUB, "one.epub", timestamp = 1L),
            BookItem("two", "/books/two.epub", FileType.EPUB, "two.epub", timestamp = 2L)
        )

        val selected = SharedReaderScreenState()
            .replaceBookSelectionWithVisibleBooks(visibleBooks)

        assertEquals(setOf("one", "two"), selected.selectedBookIds)
        assertEquals(
            emptySet(),
            selected.replaceBookSelectionWithVisibleBooks(visibleBooks).selectedBookIds
        )
    }

    @Test
    fun `custom font imports enter shared application state`() {
        val font = CustomFontItem(
            id = "font-1",
            displayName = "Reader Serif",
            fileName = "ReaderSerif.ttf",
            fileExtension = "ttf",
            path = "/fonts/ReaderSerif.ttf",
            timestamp = 10L
        )

        val result = SharedReaderScreenState().reduce(AppAction.CustomFontsChanged(listOf(font)))

        assertEquals(listOf(font), result.customFonts)
    }

    @Test
    fun `disabling or closing all tabs clears ids active tab and materialized tabs together`() {
        val tab = BookItem(
            id = "pdf",
            path = "/books/pdf.pdf",
            type = FileType.PDF,
            displayName = "pdf.pdf",
            timestamp = 1L,
        )
        val state = SharedReaderScreenState(
            openTabIds = listOf(tab.id),
            activeTabBookId = tab.id,
            openTabs = listOf(tab),
        )

        val disabled = state.reduce(AppAction.TabsEnabledChanged(false))
        val closedAll = state.reduce(AppAction.AllTabsClosed)

        assertEquals(false, disabled.isTabsEnabled)
        assertEquals(emptyList(), disabled.openTabIds)
        assertEquals(null, disabled.activeTabBookId)
        assertEquals(emptyList(), disabled.openTabs)
        assertEquals(emptyList(), closedAll.openTabIds)
        assertEquals(null, closedAll.activeTabBookId)
        assertEquals(emptyList(), closedAll.openTabs)
    }
}
