package com.aryan.reader.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.ui.SharedAppTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopReaderWindowStateTest {

    @Test
    fun `desktop starts on library instead of home`() {
        assertEquals(SharedAppTab.LIBRARY, DesktopInitialAppTab)
    }

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

    @Test
    fun `reader window uses persisted size instead of hardcoded fallback`() {
        val snapshot = DesktopWindowStateSnapshot(
            placement = DesktopSavedWindowPlacement.FLOATING,
            widthDp = 1340f,
            heightDp = 840f
        )

        val size = snapshot.toWindowSize(DesktopReaderWindowDefaultSize)

        assertEquals(1340.dp, size.width)
        assertEquals(840.dp, size.height)
    }

    @Test
    fun `reader window defaults preserve previous detached reader size`() {
        assertEquals(1120.dp, DesktopReaderWindowDefaultSize.width)
        assertEquals(760.dp, DesktopReaderWindowDefaultSize.height)
    }

    @Test
    fun `reader window persistence ignores fullscreen snapshots`() {
        val snapshot = DesktopWindowStateSnapshot(
            placement = DesktopSavedWindowPlacement.FULLSCREEN,
            widthDp = 1920f,
            heightDp = 1080f
        )

        assertEquals(WindowPlacement.Floating, snapshot.toReaderWindowPlacement())
        assertNull(snapshot.toPersistableReaderWindowSnapshot())
    }

    @Test
    fun `native webview text reader resets surface when switching from vertical to paginated`() {
        assertTrue(
            shouldResetDesktopTextReaderWindowSurface(
                previousMode = ReaderReadingMode.VERTICAL,
                currentMode = ReaderReadingMode.PAGINATED,
                usesNativeWebView = true
            )
        )
    }

    @Test
    fun `text reader surface reset is limited to native webview vertical to paginated switches`() {
        assertFalse(
            shouldResetDesktopTextReaderWindowSurface(
                previousMode = ReaderReadingMode.PAGINATED,
                currentMode = ReaderReadingMode.VERTICAL,
                usesNativeWebView = true
            )
        )
        assertFalse(
            shouldResetDesktopTextReaderWindowSurface(
                previousMode = ReaderReadingMode.VERTICAL,
                currentMode = ReaderReadingMode.PAGINATED,
                usesNativeWebView = false
            )
        )
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
