package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ui.SharedAppTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReaderWindowStateTest {

    @Test
    fun `opening a new reader creates a window`() {
        val opening = readerOpening("book-1", requestId = 1)

        val decision = emptyList<DesktopReaderWindowState>().openOrFocusDesktopReaderWindow(
            opening = opening,
            force = false
        )

        assertTrue(decision.shouldStartOpen)
        assertEquals(listOf("book-1"), decision.windows.map { it.bookId })
        assertEquals(1L, decision.windows.single().focusRequestId)
    }

    @Test
    fun `opening an already open reader focuses the existing window`() {
        val opening = readerOpening("book-1", requestId = 1)
        val first = emptyList<DesktopReaderWindowState>()
            .openOrFocusDesktopReaderWindow(opening, force = false)
            .windows

        val decision = first.openOrFocusDesktopReaderWindow(
            opening = readerOpening("book-1", requestId = 2),
            force = false
        )

        assertFalse(decision.shouldStartOpen)
        assertEquals(1, decision.windows.size)
        assertEquals(2L, decision.windows.single().focusRequestId)
        assertEquals(1L, decision.windows.single().opening.requestId)
    }

    @Test
    fun `forcing an already open reader replaces the opening request`() {
        val opening = readerOpening("book-1", requestId = 1)
        val first = emptyList<DesktopReaderWindowState>()
            .openOrFocusDesktopReaderWindow(opening, force = false)
            .windows

        val decision = first.openOrFocusDesktopReaderWindow(
            opening = readerOpening("book-1", requestId = 2),
            force = true
        )

        assertTrue(decision.shouldStartOpen)
        assertEquals(1, decision.windows.size)
        assertEquals(2L, decision.windows.single().opening.requestId)
        assertEquals(2L, decision.windows.single().focusRequestId)
    }

    private fun readerOpening(bookId: String, requestId: Long): DesktopReaderOpening {
        return DesktopReaderOpening(
            requestId = requestId,
            bookId = bookId,
            title = "Book $bookId",
            formatLabel = FileType.EPUB.name,
            returnTab = SharedAppTab.LIBRARY
        )
    }
}
