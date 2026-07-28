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
        assertEquals("1 book removed from library.", result.state.bannerMessage?.message)
        assertEquals("banner_books_removed_library", result.state.bannerMessage?.text?.name)
    }

    @Test
    fun `removeBooksFromShelf preserves library books and clears only manual shelf membership`() {
        val keep = book("keep")
        val remove = book("remove")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(keep, remove),
            libraryBooks = listOf(keep, remove),
            selectedBookIds = setOf("remove"),
            shelves = listOf(
                Shelf(
                    id = "manual",
                    name = "Reading",
                    type = ShelfType.MANUAL,
                    books = listOf(keep, remove),
                    directBooks = listOf(keep, remove),
                )
            ),
        )

        val result = SharedLibraryEditor.removeBooksFromShelf(state, "manual", setOf("remove"))

        requireNotNull(result)
        assertEquals(listOf("keep", "remove"), result.rawLibraryBooks.ids())
        assertEquals(listOf("keep", "remove"), result.libraryBooks.ids())
        assertEquals(listOf("keep"), result.shelves.single().books.ids())
        assertEquals(listOf("keep"), result.shelves.single().directBooks.ids())
        assertTrue(result.selectedBookIds.isEmpty())
        assertEquals("Removed 1 book from \"Reading\".", result.bannerMessage?.message)
    }

    @Test
    fun `addBooksToShelvesInState deduplicates membership across selected manual shelves`() {
        val existing = book("existing")
        val added = book("added")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing, added),
            selectedBookIds = setOf("existing", "added"),
            shelves = listOf(
                Shelf(
                    id = "first",
                    name = "First",
                    type = ShelfType.MANUAL,
                    books = listOf(existing),
                    directBooks = listOf(existing),
                ),
                Shelf(id = "second", name = "Second", type = ShelfType.MANUAL, books = emptyList()),
                Shelf(id = "folder", name = "Folder", type = ShelfType.FOLDER, books = emptyList()),
            ),
        )

        val result = SharedLibraryEditor.addBooksToShelvesInState(
            state = state,
            bookIds = setOf("existing", "added", "missing"),
            shelfIds = setOf("first", "second", "folder", "missing"),
        )

        requireNotNull(result)
        assertEquals(listOf("existing", "added"), result.shelves[0].books.ids())
        assertEquals(listOf("existing", "added"), result.shelves[1].books.ids())
        assertTrue(result.shelves[2].books.isEmpty())
        assertTrue(result.selectedBookIds.isEmpty())
        assertEquals("3 shelf entries added.", result.bannerMessage?.message)
    }

    @Test
    fun `removeBooksFromRecentsInState preserves library files shelves and tabs`() {
        val removed = book("removed").copy(
            path = "/managed/removed.epub",
            isRecent = true,
            metadataModifiedTimestamp = 5L,
        )
        val kept = book("kept").copy(isRecent = true)
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(removed, kept),
            libraryBooks = listOf(removed, kept),
            recentBooks = listOf(removed, kept),
            openTabs = listOf(removed),
            selectedBookIds = setOf("removed"),
            pinnedHomeBookIds = setOf("removed"),
            shelves = listOf(
                Shelf("manual", "Manual", ShelfType.MANUAL, books = listOf(removed))
            ),
        )

        val result = SharedLibraryEditor.removeBooksFromRecentsInState(
            state = state,
            bookIds = setOf("removed", "missing"),
            nowMillis = 20L,
        )

        requireNotNull(result)
        val stored = result.rawLibraryBooks.first { it.id == "removed" }
        assertEquals("/managed/removed.epub", stored.path)
        assertTrue(!stored.isRecent)
        assertEquals(20L, stored.metadataModifiedTimestamp)
        assertEquals(listOf("removed", "kept"), result.libraryBooks.ids())
        assertEquals(listOf("kept"), result.recentBooks.ids())
        assertTrue(!result.openTabs.single().isRecent)
        assertTrue(!result.shelves.single().books.single().isRecent)
        assertEquals(setOf("removed"), result.pinnedHomeBookIds)
        assertTrue(result.selectedBookIds.isEmpty())
    }

    @Test
    fun `state shelf management mutates only mutable manual shelves and preserves books`() {
        val book = book("kept").copy(path = "/folder/kept.pdf", sourceFolder = "/folder")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(book),
            libraryBooks = listOf(book),
            shelves = listOf(
                Shelf("manual", "Manual", ShelfType.MANUAL, books = listOf(book)),
                Shelf("smart", "Smart", ShelfType.SMART, books = listOf(book)),
                Shelf("folder", "Folder", ShelfType.FOLDER, books = listOf(book)),
                Shelf("unshelved", "Unshelved", ShelfType.MANUAL, books = emptyList()),
            ),
            selectedShelfIds = setOf("manual", "smart", "folder", "unshelved"),
            viewingShelfId = "manual",
        )

        val renamed = SharedLibraryEditor.renameShelfInState(state, "manual", "  Reading  ")
        requireNotNull(renamed)
        assertEquals("Reading", renamed.shelves.first { it.id == "manual" }.name)
        assertNull(SharedLibraryEditor.renameShelfInState(state, "smart", "Changed"))
        assertNull(SharedLibraryEditor.renameShelfInState(state, "unshelved", "Changed"))

        val deleted = SharedLibraryEditor.deleteShelvesInState(
            state,
            setOf("manual", "smart", "folder", "unshelved"),
        )
        requireNotNull(deleted)
        assertEquals(listOf("smart", "folder", "unshelved"), deleted.shelves.map { it.id })
        assertEquals(listOf("kept"), deleted.rawLibraryBooks.ids())
        assertEquals("/folder/kept.pdf", deleted.rawLibraryBooks.single().path)
        assertEquals(listOf("kept"), deleted.libraryBooks.ids())
        assertTrue(deleted.selectedShelfIds.isEmpty())
        assertNull(deleted.viewingShelfId)
    }

    @Test
    fun `contextual pinning pins mixed selections and unpins selections that are all pinned`() {
        val state = SharedReaderScreenState(
            selectedBookIds = setOf("one", "two"),
            pinnedHomeBookIds = setOf("one", "outside"),
            pinnedLibraryBookIds = setOf("one", "two", "outside"),
        )

        val home = SharedLibraryEditor.toggleSelectedPinsInState(
            state = state,
            bookIds = state.selectedBookIds,
            isHome = true,
        )
        requireNotNull(home)
        assertEquals(setOf("one", "two", "outside"), home.pinnedHomeBookIds)
        assertTrue(home.selectedBookIds.isEmpty())

        val library = SharedLibraryEditor.toggleSelectedPinsInState(
            state = state,
            bookIds = state.selectedBookIds,
            isHome = false,
        )
        requireNotNull(library)
        assertEquals(setOf("outside"), library.pinnedLibraryBookIds)
        assertTrue(library.selectedBookIds.isEmpty())
    }

    @Test
    fun `select all toggles exactly the visible book set`() {
        val selected = SharedLibraryEditor.toggleVisibleBookSelectionInState(
            state = SharedReaderScreenState(selectedBookIds = setOf("stale")),
            visibleBookIds = listOf(" visible_one ", "visible_two", "visible_one"),
        )
        assertEquals(setOf("visible_one", "visible_two"), selected.selectedBookIds)

        val cleared = SharedLibraryEditor.toggleVisibleBookSelectionInState(
            state = selected.copy(selectedBookIds = selected.selectedBookIds + "outside"),
            visibleBookIds = listOf("visible_one", "visible_two"),
        )
        assertTrue(cleared.selectedBookIds.isEmpty())

        val empty = SharedLibraryEditor.toggleVisibleBookSelectionInState(
            state = selected,
            visibleBookIds = emptySet(),
        )
        assertTrue(empty.selectedBookIds.isEmpty())
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
        assertEquals("1 book added to shelf.", result.state.bannerMessage?.message)
        assertEquals("banner_books_added_to_shelf", result.state.bannerMessage?.text?.name)
    }

    @Test
    fun `addBooksToShelves adds missing refs to multiple shelves and can keep selection`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("selected"))
        val refs = listOf(BookShelfRef(bookId = "one", shelfId = "manual_a", addedAt = 1L))

        val result = SharedLibraryEditor.addBooksToShelves(
            state = state,
            shelfRecords = listOf(ShelfRecord("manual_a", "A"), ShelfRecord("manual_b", "B")),
            shelfRefs = refs,
            bookIds = listOf(" one ", "two", "one"),
            shelfIds = listOf("manual_a", "manual_b", "unshelved", " "),
            clearSelection = false,
            nowMillis = 9L
        )

        requireNotNull(result)
        assertEquals(setOf("selected"), result.state.selectedBookIds)
        assertEquals(
            listOf(
                BookShelfRef(bookId = "one", shelfId = "manual_a", addedAt = 1L),
                BookShelfRef(bookId = "two", shelfId = "manual_a", addedAt = 9L),
                BookShelfRef(bookId = "one", shelfId = "manual_b", addedAt = 9L),
                BookShelfRef(bookId = "two", shelfId = "manual_b", addedAt = 9L)
            ),
            result.shelfRefs
        )
        assertEquals("3 shelf entries added.", result.state.bannerMessage?.message)
    }

    @Test
    fun `addBooksToShelves clears selection when requested`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("one"))

        val result = SharedLibraryEditor.addBooksToShelves(
            state = state,
            shelfRecords = listOf(ShelfRecord("manual", "Manual")),
            shelfRefs = emptyList(),
            bookIds = listOf("one"),
            shelfIds = listOf("manual"),
            clearSelection = true,
            nowMillis = 10L
        )

        requireNotNull(result)
        assertTrue(result.state.selectedBookIds.isEmpty())
        assertEquals(
            listOf(BookShelfRef(bookId = "one", shelfId = "manual", addedAt = 10L)),
            result.shelfRefs
        )
    }

    @Test
    fun `replaceShelfBooks only rewrites target shelf refs`() {
        val state = SharedReaderScreenState(
            shelves = listOf(Shelf("manual", "Manual", ShelfType.MANUAL, emptyList()))
        )
        val refs = listOf(
            BookShelfRef(bookId = "old", shelfId = "manual", addedAt = 1L),
            BookShelfRef(bookId = "keep", shelfId = "other", addedAt = 2L)
        )

        val result = SharedLibraryEditor.replaceShelfBooks(
            state = state,
            shelfRecords = listOf(ShelfRecord("manual", "Manual")),
            shelfRefs = refs,
            shelfId = "manual",
            bookIds = listOf("new", "new", " "),
            nowMillis = 7L
        )

        requireNotNull(result)
        assertEquals(
            listOf(
                BookShelfRef(bookId = "keep", shelfId = "other", addedAt = 2L),
                BookShelfRef(bookId = "new", shelfId = "manual", addedAt = 7L)
            ),
            result.shelfRefs
        )
        assertEquals("Updated \"Manual\" with 1 book.", result.state.bannerMessage?.message)
    }

    @Test
    fun `createShelfWithBooks creates shelf refs and clears selection`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("one", "two"))

        val result = SharedLibraryEditor.createShelfWithBooks(
            state = state,
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            name = "  Favorites  ",
            bookIds = listOf("one", "two", "one"),
            nowMillis = 12L
        )

        requireNotNull(result)
        assertTrue(result.state.selectedBookIds.isEmpty())
        assertEquals(listOf(ShelfRecord("shelf_12", "Favorites")), result.shelfRecords)
        assertEquals(
            listOf(
                BookShelfRef(bookId = "one", shelfId = "shelf_12", addedAt = 12L),
                BookShelfRef(bookId = "two", shelfId = "shelf_12", addedAt = 12L)
            ),
            result.shelfRefs
        )
        assertEquals("Created shelf \"Favorites\" with 2 books.", result.state.bannerMessage?.message)
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
        assertEquals("banner_smart_shelf_created", result.state.bannerMessage?.text?.name)
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
    fun `deleteTag removes tag from books all tags and active filters`() {
        val favorite = Tag(id = "favorite", name = "Favorite")
        val keep = Tag(id = "keep", name = "Keep")
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(
                book("one", tags = listOf(favorite, keep)),
                book("two", tags = listOf(favorite)),
                book("three", tags = listOf(keep))
            ),
            allTags = listOf(favorite, keep),
            libraryFilters = LibraryFilters(tagIds = setOf("favorite", "keep"))
        )

        val result = SharedLibraryEditor.deleteTag(
            state = state,
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            tagId = " favorite "
        )

        requireNotNull(result)
        assertEquals(listOf(keep), result.state.allTags)
        assertEquals(listOf(keep), result.state.rawLibraryBooks.first { it.id == "one" }.tags)
        assertTrue(result.state.rawLibraryBooks.first { it.id == "two" }.tags.isEmpty())
        assertEquals(listOf(keep), result.state.rawLibraryBooks.first { it.id == "three" }.tags)
        assertEquals(setOf("keep"), result.state.libraryFilters.tagIds)
        assertEquals("Deleted tag \"Favorite\".", result.state.bannerMessage?.message)
        assertEquals("banner_tag_deleted", result.state.bannerMessage?.text?.name)
    }

    @Test
    fun `contextual tag assignment preserves unrelated tags and selection`() {
        val favorite = Tag(id = "favorite", name = "Favorite")
        val keep = Tag(id = "keep", name = "Keep")
        val one = book("one", tags = listOf(keep)).copy(metadataModifiedTimestamp = 2L)
        val two = book("two", tags = listOf(favorite, keep)).copy(metadataModifiedTimestamp = 3L)
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(one, two),
            libraryBooks = listOf(one, two),
            recentBooks = listOf(one),
            openTabs = listOf(two),
            shelves = listOf(
                Shelf(
                    id = "manual",
                    name = "Manual",
                    type = ShelfType.MANUAL,
                    books = listOf(one, two),
                    directBooks = listOf(one, two),
                )
            ),
            allTags = listOf(favorite, keep),
            selectedBookIds = setOf("one", "two"),
        )

        val assigned = SharedLibraryEditor.toggleTagForBooksInState(
            state = state,
            tagId = favorite.id,
            bookIds = state.selectedBookIds,
            assign = true,
            nowMillis = 20L,
        )

        requireNotNull(assigned)
        assertEquals(setOf(favorite, keep), assigned.rawLibraryBooks.first { it.id == "one" }.tags.toSet())
        assertEquals(setOf(favorite, keep), assigned.rawLibraryBooks.first { it.id == "two" }.tags.toSet())
        assertEquals(20L, assigned.rawLibraryBooks.first { it.id == "one" }.metadataModifiedTimestamp)
        assertEquals(3L, assigned.rawLibraryBooks.first { it.id == "two" }.metadataModifiedTimestamp)
        assertEquals(state.selectedBookIds, assigned.selectedBookIds)
        assertTrue(assigned.libraryBooks.all { favorite in it.tags })
        assertTrue(favorite in assigned.recentBooks.single().tags)
        assertTrue(favorite in assigned.openTabs.single().tags)
        assertTrue(assigned.shelves.single().books.all { favorite in it.tags })
        assertTrue(assigned.shelves.single().directBooks.all { favorite in it.tags })

        val removed = SharedLibraryEditor.toggleTagForBooksInState(
            state = assigned,
            tagId = favorite.id,
            bookIds = assigned.selectedBookIds,
            assign = false,
            nowMillis = 30L,
        )

        requireNotNull(removed)
        assertTrue(removed.rawLibraryBooks.all { favorite !in it.tags })
        assertTrue(removed.rawLibraryBooks.all { keep in it.tags })
        assertTrue(removed.rawLibraryBooks.all { it.metadataModifiedTimestamp == 30L })
        assertEquals(state.selectedBookIds, removed.selectedBookIds)
    }

    @Test
    fun `contextual tag creation assigns selected books and global deletion clears projections`() {
        val keep = Tag(id = "keep", name = "Keep")
        val one = book("one", tags = listOf(keep))
        val two = book("two")
        val tagShelf = Shelf(id = "tag_keep", name = "Keep", type = ShelfType.TAG, books = listOf(one))
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(one, two),
            libraryBooks = listOf(one, two),
            shelves = listOf(tagShelf),
            allTags = listOf(keep),
            selectedBookIds = setOf("two"),
            libraryFilters = LibraryFilters(tagIds = setOf("keep")),
        )

        val created = SharedLibraryEditor.createAndAssignTagInState(
            state = state,
            name = "  New Tag  ",
            bookIds = state.selectedBookIds,
            nowMillis = 40L,
        )

        requireNotNull(created)
        val newTag = created.allTags.first { it.name == "New Tag" }
        assertEquals(listOf(newTag), created.rawLibraryBooks.first { it.id == "two" }.tags)
        assertEquals(listOf(keep), created.rawLibraryBooks.first { it.id == "one" }.tags)
        assertEquals(state.selectedBookIds, created.selectedBookIds)

        val deleted = SharedLibraryEditor.deleteTagInState(
            state = created,
            tagId = keep.id,
            nowMillis = 50L,
        )

        requireNotNull(deleted)
        assertTrue(deleted.rawLibraryBooks.none { keep in it.tags })
        assertTrue(deleted.libraryBooks.none { keep in it.tags })
        assertTrue(deleted.shelves.none { it.id == "tag_keep" })
        assertTrue(deleted.allTags.none { it.id == keep.id })
        assertTrue(keep.id !in deleted.libraryFilters.tagIds)
        assertEquals(50L, deleted.rawLibraryBooks.first { it.id == "one" }.metadataModifiedTimestamp)
        assertEquals("Deleted tag \"Keep\".", deleted.bannerMessage?.message)
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
        assertEquals("Removed folder \"Books\" and 1 book from the app.", result.state.bannerMessage?.message)
        assertEquals("banner_folder_removed_with_book_count", result.state.bannerMessage?.text?.name)
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
