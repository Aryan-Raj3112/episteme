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
    fun `createSmartShelf stores trimmed shared rules and rejects blank definitions`() {
        val definition = SmartCollectionDefinition(
            rules = listOf(
                SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, "  dune  "),
                SmartRule(SmartField.AUTHOR, SmartOperator.CONTAINS, " ")
            )
        )

        val result = SharedLibraryEditor.createSmartShelf(
            state = SharedReaderScreenState(),
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            name = "  Smart Picks  ",
            definition = definition,
            nowMillis = 7L
        )

        requireNotNull(result)
        val shelf = result.shelfRecords.single()
        val decoded = SmartCollectionEngine.fromJson(shelf.smartRulesJson)
        assertEquals(ShelfRecord("smart_7", "Smart Picks", isSmart = true, smartRulesJson = shelf.smartRulesJson), shelf)
        assertEquals(listOf(SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, "dune")), decoded?.rules)
        assertEquals("Created smart shelf \"Smart Picks\".", result.state.bannerMessage?.message)
        assertNull(
            SharedLibraryEditor.createSmartShelf(
                state = SharedReaderScreenState(),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                name = "Blank",
                definition = SmartCollectionDefinition(rules = listOf(SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, " "))),
                nowMillis = 8L
            )
        )
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

    @Test
    fun `removeFolder removes folder books tabs pins refs and synced folder metadata`() {
        val folderBook = book("folder_book").copy(sourceFolder = "C:/Books")
        val otherBook = book("other")
        val folder = Shelf(
            id = "folder_C:/Books",
            name = "Books",
            type = ShelfType.FOLDER,
            books = listOf(folderBook)
        )
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(folderBook, otherBook),
            selectedBookIds = setOf("folder_book", "other"),
            pinnedHomeBookIds = setOf("folder_book"),
            pinnedLibraryBookIds = setOf("folder_book", "other"),
            openTabIds = listOf("folder_book", "other"),
            activeTabBookId = "folder_book",
            syncedFolders = listOf(SyncedFolder("C:/Books", "Books", lastScanTime = 1L)),
            libraryFilters = LibraryFilters(sourceFolders = setOf("C:/Books"))
        )
        val refs = listOf(
            BookShelfRef(bookId = "folder_book", shelfId = "manual", addedAt = 1L),
            BookShelfRef(bookId = "other", shelfId = "manual", addedAt = 2L)
        )

        val result = SharedLibraryEditor.removeFolder(state, emptyList(), refs, folder)

        requireNotNull(result)
        assertEquals(listOf("other"), result.state.rawLibraryBooks.ids())
        assertEquals(setOf("other"), result.state.selectedBookIds)
        assertTrue(result.state.pinnedHomeBookIds.isEmpty())
        assertEquals(setOf("other"), result.state.pinnedLibraryBookIds)
        assertEquals(listOf("other"), result.state.openTabIds)
        assertNull(result.state.activeTabBookId)
        assertTrue(result.state.syncedFolders.isEmpty())
        assertTrue(result.state.libraryFilters.sourceFolders.isEmpty())
        assertEquals(listOf("other"), result.shelfRefs.map { it.bookId })
    }

    @Test
    fun `markBookOpened marks book recent and updates timestamp`() {
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(
                book("opened").copy(isRecent = false, timestamp = 1L),
                book("other").copy(isRecent = false, timestamp = 2L)
            )
        )

        val result = SharedLibraryEditor.markBookOpened(state, "opened", nowMillis = 99L)

        assertTrue(result.rawLibraryBooks.first { it.id == "opened" }.isRecent)
        assertEquals(99L, result.rawLibraryBooks.first { it.id == "opened" }.timestamp)
        assertTrue(!result.rawLibraryBooks.first { it.id == "other" }.isRecent)
        assertEquals(2L, result.rawLibraryBooks.first { it.id == "other" }.timestamp)
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
