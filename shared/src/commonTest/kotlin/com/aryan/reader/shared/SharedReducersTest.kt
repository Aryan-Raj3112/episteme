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
}
