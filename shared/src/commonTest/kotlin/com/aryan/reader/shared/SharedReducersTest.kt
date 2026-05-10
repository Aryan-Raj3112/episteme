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
}
