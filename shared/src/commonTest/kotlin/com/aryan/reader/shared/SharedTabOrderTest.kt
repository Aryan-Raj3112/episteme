package com.aryan.reader.shared

import com.aryan.reader.shared.ui.openTabDragEdgeOverflow
import com.aryan.reader.shared.ui.openTabsForSwitcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tab order is shared state: the library switcher and both readers' tab strips list the same
 * order, so a drag in one place has to mean the same movement in the other.
 */
class SharedTabOrderTest {

    @Test
    fun `moving down lands after the target and moving up lands before it`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), ids.movedItem(0, 2))
        assertEquals(listOf("b", "a", "c"), ids.movedItem(0, 1))
        assertEquals(listOf("c", "a", "b"), ids.movedItem(2, 0))
        assertEquals(listOf("a", "c", "b"), ids.movedItem(2, 1))
    }

    @Test
    fun `an unchanged slot or an unknown index leaves the order alone`() {
        val ids = listOf("a", "b", "c")
        assertSame(ids, ids.movedItem(1, 1))
        assertSame(ids, ids.movedItem(-1, 0))
        assertSame(ids, ids.movedItem(0, 3))
    }

    @Test
    fun `a dragged order applies to the open tabs and never drops one opened mid-drag`() {
        val open = listOf("a", "b", "c")

        assertEquals(listOf("c", "b", "a"), open.reorderedOpenTabs(listOf("c", "b", "a")))
        // "d" is not open here and "b" was not in the dragged list: unknown ids are ignored, open
        // tabs the drag never mentioned stay at the end.
        assertEquals(listOf("c", "a", "b"), open.reorderedOpenTabs(listOf("d", "c", "a")))
        assertEquals(listOf("c", "a", "b"), open.reorderedOpenTabs(listOf("c", "a", "c")))
    }

    @Test
    fun `the reducer reorders one set and keeps both active tabs`() {
        val state = AppTabState(
            fixedLayout = AppTabGroup(openBookIds = listOf("p1", "p2", "p3"), activeBookId = "p2"),
            text = AppTabGroup(openBookIds = listOf("t1", "t2"), activeBookId = "t2"),
        )

        val moved = state.reduce(AppAction.BookTabsReordered(ReaderTabKind.FIXED_LAYOUT, listOf("p2", "p3", "p1")))

        assertEquals(listOf("p2", "p3", "p1"), moved.fixedLayout.openBookIds)
        assertEquals("p2", moved.fixedLayout.activeBookId)
        assertEquals(listOf("t1", "t2"), moved.text.openBookIds)
        assertEquals("t2", moved.text.activeBookId)
        assertSame(state, state.reduce(AppAction.BookTabsReordered(ReaderTabKind.FIXED_LAYOUT, listOf("p1", "p2", "p3"))))
    }

    @Test
    fun `a stale order cannot drop a tab that is still open`() {
        val state = AppTabState(
            fixedLayout = AppTabGroup(openBookIds = listOf("p1", "p2", "p3"), activeBookId = "p1"),
        )

        val moved = state.reduce(AppAction.BookTabsReordered(ReaderTabKind.FIXED_LAYOUT, listOf("p3", "p1")))

        assertEquals(listOf("p3", "p1", "p2"), moved.fixedLayout.openBookIds)
        assertEquals("p1", moved.fixedLayout.activeBookId)
    }

    @Test
    fun `the mobile state keeps the derived book lists in the dragged order`() {
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(epub("t1"), epub("t2"), epub("t3")),
            textOpenTabs = listOf(epub("t1"), epub("t2"), epub("t3")),
            textOpenTabIds = listOf("t1", "t2", "t3"),
            activeTextTabBookId = "t1",
        )

        val moved = state.withMobileTabsReordered(ReaderTabKind.TEXT, listOf("t2", "t3", "t1"))

        assertEquals(listOf("t2", "t3", "t1"), moved.textOpenTabIds)
        assertEquals(listOf("t2", "t3", "t1"), moved.textOpenTabs.map { it.id })
        assertEquals("t1", moved.activeTextTabBookId)
        // The switcher reads the same order the reader strip will show.
        assertEquals(listOf("t2", "t3", "t1"), moved.openTabsForSwitcher().map { it.bookId })
    }

    @Test
    fun `pushing the lifted row into the window edge scrolls by the overflow only`() {
        val edge = 48f
        // Comfortably inside the window: nothing to scroll.
        assertEquals(0f, openTabDragEdgeOverflow(200f, 0, 480, edge))
        // Sitting exactly on the band's inner edges still counts as inside.
        assertEquals(0f, openTabDragEdgeOverflow(48f, 0, 480, edge))
        assertEquals(0f, openTabDragEdgeOverflow(432f, 0, 480, edge))
        // Past them the window follows the push one to one, in either direction.
        assertEquals(30f, openTabDragEdgeOverflow(462f, 0, 480, edge))
        assertEquals(-20f, openTabDragEdgeOverflow(28f, 0, 480, edge))
        // A row pushed right out of the window asks for the whole way back.
        assertEquals(120f, openTabDragEdgeOverflow(552f, 0, 480, edge))
    }

    private fun epub(id: String) = BookItem(
        id = id,
        path = "/books/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
    )
}
