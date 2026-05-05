package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedLibraryEditorTest {

    @Test
    fun `clean helpers trim names and reject blank values`() {
        assertEquals("Favorites", SharedLibraryEditor.cleanShelfName("  Favorites  "))
        assertEquals("Reference", SharedLibraryEditor.cleanTagName(" Reference "))
        assertNull(SharedLibraryEditor.cleanShelfName("   "))
        assertNull(SharedLibraryEditor.cleanTagName(""))
        assertTrue(SharedLibraryEditor.canMutateShelf("manual"))
        assertTrue(!SharedLibraryEditor.canMutateShelf("unshelved"))
        assertTrue(!SharedLibraryEditor.canMutateShelf(" "))
        assertEquals(setOf("a", "b"), SharedLibraryEditor.cleanBookIds(listOf(" a ", "", "b", "a")))
    }

    @Test
    fun `create records trim input and reject blank ids`() {
        val shelf = SharedLibraryEditor.createShelfRecord("  Manual  ", " shelf ")
        val tag = SharedLibraryEditor.createTag("  Sci-Fi  ", " tag ", color = 7)

        assertEquals(ShelfRecord(id = "shelf", name = "Manual"), shelf)
        assertEquals(Tag(id = "tag", name = "Sci-Fi", color = 7), tag)
        assertNull(SharedLibraryEditor.createShelfRecord("Manual", " "))
        assertNull(SharedLibraryEditor.createTag(" ", "tag"))
    }

    @Test
    fun `removeSelectedBooks removes books and shelf refs then clears selection`() {
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(book("keep"), book("remove")),
            selectedBookIds = setOf("remove")
        )
        val refs = listOf(
            BookShelfRef(bookId = "keep", shelfId = "manual", addedAt = 1L),
            BookShelfRef(bookId = "remove", shelfId = "manual", addedAt = 2L)
        )

        val result = SharedLibraryEditor.removeSelectedBooks(state, shelfRecords = emptyList(), shelfRefs = refs)

        requireNotNull(result)
        assertEquals(listOf("keep"), result.state.rawLibraryBooks.ids())
        assertTrue(result.state.selectedBookIds.isEmpty())
        assertEquals(listOf("keep"), result.shelfRefs.map { it.bookId })
        assertEquals("Removed 1 book(s) from the library.", result.state.bannerMessage?.message)
    }

    @Test
    fun `addSelectedBooksToShelf adds only missing refs and clears selection`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("existing", "new"))
        val refs = listOf(BookShelfRef(bookId = "existing", shelfId = "manual", addedAt = 1L))

        val result = SharedLibraryEditor.addSelectedBooksToShelf(
            state = state,
            shelfRecords = listOf(ShelfRecord("manual", "Manual")),
            shelfRefs = refs,
            shelfId = "manual",
            nowMillis = 5L
        )

        requireNotNull(result)
        assertTrue(result.state.selectedBookIds.isEmpty())
        assertEquals(
            listOf(
                BookShelfRef(bookId = "existing", shelfId = "manual", addedAt = 1L),
                BookShelfRef(bookId = "new", shelfId = "manual", addedAt = 5L)
            ),
            result.shelfRefs
        )
        assertEquals("Added 1 book(s) to shelf.", result.state.bannerMessage?.message)
    }

    @Test
    fun `tagSelectedBooks reuses matching tags case insensitively`() {
        val favorite = Tag(id = "favorite", name = "Favorite")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(book("one"), book("two", tags = listOf(favorite))),
            allTags = listOf(favorite),
            selectedBookIds = setOf("one", "two")
        )

        val result = SharedLibraryEditor.tagSelectedBooks(
            state = state,
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            tagName = " favorite ",
            nowMillis = 10L
        )

        requireNotNull(result)
        assertEquals(listOf(favorite), result.state.allTags)
        assertEquals(listOf(favorite), result.state.rawLibraryBooks.first { it.id == "one" }.tags)
        assertEquals(listOf(favorite), result.state.rawLibraryBooks.first { it.id == "two" }.tags)
        assertTrue(result.state.selectedBookIds.isEmpty())
    }

    @Test
    fun `updateBookMetadata updates book timestamp and merges tags`() {
        val old = book("book", title = "Old")
        val newTag = Tag("new", "New")

        val result = SharedLibraryEditor.updateBookMetadata(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(old)),
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            updated = old.copy(title = "New", tags = listOf(newTag)),
            nowMillis = 99L
        )

        val updatedBook = result.state.rawLibraryBooks.single()
        assertEquals("New", updatedBook.title)
        assertEquals(99L, updatedBook.timestamp)
        assertEquals(listOf(newTag), result.state.allTags)
        assertEquals("Updated \"New\".", result.state.bannerMessage?.message)
    }

    private fun book(
        id: String,
        title: String? = id,
        tags: List<Tag> = emptyList()
    ) = BookItem(
        id = id,
        path = "/library/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        title = title,
        tags = tags
    )

    private fun List<BookItem>.ids() = map { it.id }
}
