package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `opening and closing a mobile book keeps tab and selected book state aligned`() {
        val first = book(id = "first", timestamp = 1L)
        val second = book(id = "second", timestamp = 2L)
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(first, second))
            .withMobileBookOpened(first)
            .withMobileBookOpened(second)

        assertEquals(listOf("first", "second"), state.openTabIds)
        assertEquals("second", state.activeTabBookId)
        assertEquals("second", state.selectedBookId)

        val closed = state.withMobileBookClosed("second")

        assertEquals(listOf("first"), closed.openTabIds)
        assertEquals("first", closed.activeTabBookId)
        assertEquals("first", closed.selectedBookId)
        assertEquals(first.path, closed.selectedUriString)
        assertEquals(first.type, closed.selectedFileType)
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
