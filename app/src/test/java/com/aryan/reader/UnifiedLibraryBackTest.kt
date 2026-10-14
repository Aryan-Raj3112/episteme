package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedLibraryBackTest {

    @Test
    fun `selection clears before search and shelf`() {
        assertEquals(
            LibraryBackAction.CLEAR_SELECTION,
            nextLibraryBackAction(hasSelection = true, isSearchActive = true, inShelf = true),
        )
    }

    @Test
    fun `open keyboard hides before search clears`() {
        assertEquals(
            LibraryBackAction.HIDE_KEYBOARD,
            nextLibraryBackAction(
                hasSelection = false,
                isSearchActive = true,
                inShelf = true,
                isKeyboardOpen = true,
            ),
        )
    }

    @Test
    fun `active search clears before shelf up`() {
        assertEquals(
            LibraryBackAction.CLEAR_SEARCH,
            nextLibraryBackAction(hasSelection = false, isSearchActive = true, inShelf = true),
        )
    }

    @Test
    fun `shelf up only when nothing else to unwind`() {
        assertEquals(
            LibraryBackAction.SHELF_UP,
            nextLibraryBackAction(hasSelection = false, isSearchActive = false, inShelf = true),
        )
    }

    @Test
    fun `no layers means no interception`() {
        assertEquals(
            LibraryBackAction.NONE,
            nextLibraryBackAction(hasSelection = false, isSearchActive = false, inShelf = false),
        )
    }
}
