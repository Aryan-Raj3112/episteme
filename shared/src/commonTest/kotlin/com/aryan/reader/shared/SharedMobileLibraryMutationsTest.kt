package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedMobileLibraryMutationsTest {

    @Test
    fun `mobile imports add only new books to every mobile library collection`() {
        val existing = book(id = "existing", timestamp = 1L)
        val added = book(id = "added", timestamp = 2L)
        val result = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing),
            recentBooks = listOf(existing),
            libraryBooks = listOf(existing)
        ).withMobileImportedBooks(listOf(existing, added))

        assertEquals(listOf("added"), result.addedBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.rawLibraryBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.recentBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.libraryBooks.map { it.id })
        assertEquals("Added 1 book(s)", result.state.bannerMessage?.message)
    }

    @Test
    fun `mobile imports reject the same file even when its generated id differs`() {
        val existing = book(id = "ios_import_book", timestamp = 1L).copy(path = "/imports/book.epub")
        val duplicate = existing.copy(id = "ios_import_book_1", timestamp = 2L)

        val result = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing),
            recentBooks = listOf(existing),
            libraryBooks = listOf(existing),
        ).withMobileImportedBooks(listOf(duplicate))

        assertTrue(result.addedBooks.isEmpty())
        assertEquals(listOf("ios_import_book"), result.state.rawLibraryBooks.map { it.id })
    }

    @Test
    fun `opening and closing a mobile book keeps tab and selected book state aligned`() {
        val first = book(id = "first", timestamp = 1L)
        val second = book(id = "second", timestamp = 2L)
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(first, second))
            .withMobileBookOpened(first)
            .withMobileBookOpened(second)

        assertEquals(listOf("first", "second"), state.openTabIds)
        assertEquals(listOf("first", "second"), state.openTabs.map { it.id })
        assertEquals("second", state.activeTabBookId)
        assertEquals("second", state.selectedBookId)

        val closed = state.withMobileBookClosed("second")

        assertEquals(listOf("first"), closed.openTabIds)
        assertEquals(listOf("first"), closed.openTabs.map { it.id })
        assertEquals("first", closed.activeTabBookId)
        assertEquals("first", closed.selectedBookId)
        assertEquals(first.path, closed.selectedUriString)
        assertEquals(first.type, closed.selectedFileType)
    }

    @Test
    fun `opening a book restores it to recents and moves it to the front`() {
        val first = book(id = "first", timestamp = 1L)
        val second = book(id = "second", timestamp = 2L)
        val hidden = first.copy(isRecent = false)

        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(hidden, second),
            libraryBooks = listOf(hidden, second),
            recentBooks = listOf(second),
        ).withMobileBookOpened(hidden, openedAt = 99L)

        assertEquals(listOf("first", "second"), state.recentBooks.map { it.id })
        assertEquals(99L, state.recentBooks.first().timestamp)
        assertEquals(true, state.recentBooks.first().isRecent)
        assertEquals(99L, state.rawLibraryBooks.first { it.id == "first" }.timestamp)
    }

    @Test
    fun `opening non pdf books does not create an active tab like android`() {
        val mobi = book(id = "mobi").copy(type = FileType.MOBI, path = "/books/mobi.mobi")

        val state = SharedReaderScreenState(rawLibraryBooks = listOf(mobi))
            .withMobileBookOpened(mobi)

        assertEquals(emptyList(), state.openTabIds)
        assertEquals(emptyList(), state.openTabs)
        assertEquals("mobi", state.selectedBookId)
    }

    @Test
    fun `opening a pdf while tabs are disabled does not enable or create tabs`() {
        val pdf = book(id = "pdf")

        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(pdf),
            isTabsEnabled = false,
        ).withMobileBookOpened(pdf)

        assertEquals(false, state.isTabsEnabled)
        assertEquals(emptyList(), state.openTabIds)
        assertEquals(emptyList(), state.openTabs)
    }

    @Test
    fun `tab-only mutations preserve reader selection for platform navigation owners`() {
        val original = SharedReaderScreenState(
            selectedBookId = "selected",
            selectedUriString = "/books/selected.pdf",
            selectedFileType = FileType.PDF
        )

        val opened = original.withMobileBookTabOpened("other")
        val closed = opened.withMobileBookTabClosed("other")

        assertEquals("selected", closed.selectedBookId)
        assertEquals("/books/selected.pdf", closed.selectedUriString)
        assertEquals(FileType.PDF, closed.selectedFileType)
        assertEquals(emptyList(), closed.openTabIds)
    }

    @Test
    fun `closing the final mobile book clears the selected reader identity`() {
        val only = book(id = "only")
        val closed = SharedReaderScreenState(rawLibraryBooks = listOf(only))
            .withMobileBookOpened(only)
            .withMobileBookClosed(only.id)

        assertNull(closed.selectedBookId)
        assertNull(closed.selectedUriString)
        assertNull(closed.selectedFileType)
    }

    private fun book(id: String, timestamp: Long = 0L): BookItem {
        return BookItem(
            id = id,
            path = "/books/$id.pdf",
            type = FileType.PDF,
            displayName = "$id.pdf",
            timestamp = timestamp
        )
    }
}
