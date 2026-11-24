package com.aryan.reader.shared

import com.aryan.reader.shared.ui.openTabsForSwitcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tab order is shared state: the library switcher and both readers' tab strips list the same
 * order, so a drag in one place has to mean the same movement in the other.
 */
class SharedTabOrderTest {

    @Test
    fun `moving down lands after the target`() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").movedOpenTab("a", "c"))
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedOpenTab("a", "b"))
    }

    @Test
    fun `moving up lands before the target`() {
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").movedOpenTab("c", "a"))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").movedOpenTab("c", "b"))
    }

    @Test
    fun `unknown ids and the same slot leave the order alone`() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, ids.movedOpenTab("missing", "b"))
        assertEquals(ids, ids.movedOpenTab("a", "missing"))
        assertEquals(ids, ids.movedOpenTab("b", "b"))
    }

    @Test
    fun `the reducer moves one set and keeps both active tabs`() {
        val state = AppTabState(
            fixedLayout = AppTabGroup(openBookIds = listOf("p1", "p2", "p3"), activeBookId = "p2"),
            text = AppTabGroup(openBookIds = listOf("t1", "t2"), activeBookId = "t2"),
        )

        val moved = state.reduce(AppAction.BookTabMoved(ReaderTabKind.FIXED_LAYOUT, "p1", "p3"))

        assertEquals(listOf("p2", "p3", "p1"), moved.fixedLayout.openBookIds)
        assertEquals("p2", moved.fixedLayout.activeBookId)
        assertEquals(listOf("t1", "t2"), moved.text.openBookIds)
        assertEquals("t2", moved.text.activeBookId)
        assertEquals(state, state.reduce(AppAction.BookTabMoved(ReaderTabKind.FIXED_LAYOUT, "p1", "p1")))
    }

    @Test
    fun `the mobile state keeps the derived book lists in tab order`() {
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(epub("t1"), epub("t2"), epub("t3")),
            textOpenTabs = listOf(epub("t1"), epub("t2"), epub("t3")),
            textOpenTabIds = listOf("t1", "t2", "t3"),
            activeTextTabBookId = "t1",
        )

        val moved = state.withMobileTabMoved(ReaderTabKind.TEXT, "t1", "t3")

        assertEquals(listOf("t2", "t3", "t1"), moved.textOpenTabIds)
        assertEquals(listOf("t2", "t3", "t1"), moved.textOpenTabs.map { it.id })
        assertEquals("t1", moved.activeTextTabBookId)
        // The switcher reads the same order the reader strip will show.
        assertEquals(listOf("t2", "t3", "t1"), moved.openTabsForSwitcher().map { it.bookId })
    }

    @Test
    fun `moving a closed tab is a no-op`() {
        val state = AppTabState(fixedLayout = AppTabGroup(openBookIds = listOf("p1"), activeBookId = "p1"))

        assertEquals(state, state.reduce(AppAction.BookTabMoved(ReaderTabKind.FIXED_LAYOUT, "gone", "p1")))
    }

    private fun epub(id: String) = BookItem(
        id = id,
        path = "/books/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
    )
}
