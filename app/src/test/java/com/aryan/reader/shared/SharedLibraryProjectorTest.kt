package com.aryan.reader.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLibraryProjectorTest {

    @Test
    fun `LibraryProjector searches filters sorts and builds selected library model`() {
        val tag = Tag("favorite", "Favorite")
        val matching = book(
            id = "matching",
            title = "Clean Android",
            author = "Ada",
            type = FileType.PDF,
            progressPercentage = 50f,
            sourceFolder = "/books",
            tags = listOf(tag),
            timestamp = 3L
        )
        val wrongTag = book("wrong_tag", title = "Clean Kotlin", type = FileType.PDF, progressPercentage = 50f)
        val wrongStatus = book("wrong_status", title = "Clean Done", type = FileType.PDF, progressPercentage = 100f, tags = listOf(tag))

        val model = LibraryProjector().library(
            LibraryState(
                books = listOf(wrongTag, matching, wrongStatus),
                searchQuery = "clean",
                sortOrder = SortOrder.TITLE_ASC,
                filters = LibraryFilters(
                    fileTypes = setOf(FileType.PDF),
                    sourceFolders = setOf("/books"),
                    readStatus = ReadStatusFilter.IN_PROGRESS,
                    tagIds = setOf(tag.id)
                ),
                selectedBookIds = setOf("matching", "missing")
            )
        )

        assertEquals(listOf("matching"), model.books.ids())
        assertEquals(listOf("matching"), model.selectedBooks.ids())
        assertEquals(SortOrder.TITLE_ASC, model.sortOrder)
        assertEquals("clean", model.searchQuery)
        assertTrue(model.filters.isActive)
    }

    @Test
    fun `LibraryProjector home limits sorted recent books and keeps selected books`() {
        val model = LibraryProjector().home(
            LibraryState(
                books = listOf(
                    book("old", timestamp = 1L),
                    book("new", timestamp = 3L),
                    book("archived", timestamp = 2L, isRecent = false)
                ),
                selectedBookIds = setOf("old", "archived"),
                recentLimit = 1,
                sortOrder = SortOrder.RECENT
            )
        )

        assertEquals(listOf("new"), model.recentBooks.ids())
        assertEquals(listOf("old", "archived"), model.selectedBooks.ids())
        assertFalse(model.isEmpty)
    }

    @Test
    fun `LibraryProjector imports only new files and maps extensions and folders`() {
        val projector = LibraryProjector()
        val state = LibraryState(books = listOf(book("C:/books/existing.pdf", displayName = "existing.pdf")))

        val result = projector.withImportedFiles(
            state,
            listOf(
                ImportedFile(name = "existing.pdf", path = "C:/books/existing.pdf", size = 1L),
                ImportedFile(name = "notes.md", path = "C:/books/notes.md", size = 2L),
                ImportedFile(name = "mystery.bin", path = null, size = 3L)
            )
        )

        assertEquals(listOf("C:/books/notes.md", "mystery.bin", "C:/books/existing.pdf"), result.books.ids())
        assertEquals(FileType.MD, result.books[0].type)
        assertEquals("C:/books", result.books[0].sourceFolder)
        assertEquals(FileType.UNKNOWN, result.books[1].type)
        assertEquals("Imported 2 file(s). Reader support comes later.", result.message)
    }

    @Test
    fun `SharedLibraryStateProjector prunes stale selections tabs and shelf state`() {
        val existing = book("existing")
        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(
                    selectedBookIds = setOf("existing", "missing"),
                    openTabIds = listOf("missing", "existing"),
                    viewingShelfId = "missing_shelf",
                    isAddingBooksToShelf = true,
                    selectedShelfIds = setOf("missing_shelf")
                ),
                booksFromStore = listOf(existing),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )

        assertEquals(setOf("existing"), result.selectedBookIds)
        assertEquals(listOf("existing"), result.openTabs.ids())
        assertNull(result.viewingShelfId)
        assertFalse(result.isAddingBooksToShelf)
        assertTrue(result.selectedShelfIds.isEmpty())
    }

    @Test
    fun `SharedLibraryStateProjector builds manual tag series folder and unshelved shelves`() {
        val tag = Tag("favorite", "Favorite")
        val manual = book("manual")
        val tagged = book("tagged", tags = listOf(tag))
        val seriesOne = book("series_1", seriesName = "Saga", seriesIndex = 1.0)
        val seriesTwo = book("series_2", seriesName = "Saga", seriesIndex = 2.0)
        val folderBook = book("folder", sourceFolder = "content://library")
        val loose = book("loose")

        val result = SharedLibraryStateProjector(
            SharedFolderPathResolver { item ->
                if (item.id == "folder") listOf("Nested") else emptyList()
            }
        ).project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(
                    syncedFolders = listOf(SyncedFolder("content://library", "Library", lastScanTime = 1L)),
                    sortOrder = SortOrder.TITLE_ASC
                ),
                booksFromStore = listOf(tagged, seriesTwo, loose, folderBook, manual, seriesOne),
                shelfRecords = listOf(ShelfRecord("manual_shelf", "Manual")),
                shelfRefs = listOf(BookShelfRef(bookId = "manual", shelfId = "manual_shelf", addedAt = 1L)),
                tags = listOf(tag)
            )
        )

        assertEquals(listOf("manual"), result.shelves.first { it.id == "manual_shelf" }.books.ids())
        assertEquals(listOf("tagged"), result.shelves.first { it.id == "tag_favorite" }.books.ids())
        assertEquals(listOf("series_1", "series_2"), result.shelves.first { it.id == "series_Saga" }.books.ids())
        assertEquals(listOf("folder"), result.shelves.first { it.id == "folder_content://library" }.books.ids())
        assertEquals(listOf("folder"), result.shelves.first { it.id == "folder_content://library::Nested" }.directBooks.ids())
        assertEquals(listOf("loose", "tagged"), result.shelves.first { it.id == "unshelved" }.books.ids())
    }

    @Test
    fun `SharedReaderScreenState withImportedFiles dedupes imports and reports duplicates`() {
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(book("/books/existing.epub")))

        val imported = state.withImportedFiles(
            listOf(
                ImportedBookFile(name = "existing.epub", uriString = null, localPath = "/books/existing.epub", size = 1L),
                ImportedBookFile(name = "new.pdf", uriString = "content://new", localPath = null, size = 2L)
            ),
            now = 10L
        )
        val duplicateOnly = imported.withImportedFiles(
            listOf(ImportedBookFile(name = "new.pdf", uriString = "content://new", localPath = null, size = 2L)),
            now = 20L
        )

        assertEquals(listOf("content://new", "/books/existing.epub"), imported.rawLibraryBooks.ids())
        assertEquals(FileType.PDF, imported.rawLibraryBooks.first().type)
        assertEquals(11L, imported.rawLibraryBooks.first().timestamp)
        assertEquals("Imported 1 file(s).", imported.bannerMessage?.message)
        assertEquals("Those files are already in the library.", duplicateOnly.bannerMessage?.message)
    }

    @Test
    fun `shared screen models expose home and library derived state`() {
        val folderBook = book("folder", sourceFolder = "/books")
        val recent = book("recent")
        val state = SharedReaderScreenState(
            recentBooks = listOf(recent),
            openTabs = listOf(folderBook),
            rawLibraryBooks = listOf(folderBook, recent),
            selectedBookIds = setOf("folder"),
            selectedShelfIds = setOf("manual"),
            isTabsEnabled = true,
            deviceLimitState = DeviceLimitReachedState(isLimitReached = true),
            searchQuery = "folder",
            isSearchActive = true
        )

        val home = state.toHomeScreenModel()
        val library = state.toLibraryScreenModel()

        assertEquals(listOf("recent"), home.recentBooks.ids())
        assertEquals(listOf("folder"), home.openTabs.ids())
        assertEquals(listOf("folder"), home.selectedBooks.ids())
        assertTrue(home.isContextualModeActive)
        assertFalse(home.isEmpty)
        assertFalse(home.isLibraryEmpty)
        assertTrue(home.deviceLimitState.isLimitReached)

        assertEquals(listOf("folder"), library.selectedBooks.ids())
        assertEquals(setOf("manual"), library.selectedShelves)
        assertTrue(library.containsFolderItemsInSelection)
        assertTrue(library.isSearchActive)
        assertEquals("folder", library.searchQuery)
    }

    @Test
    fun `toFileType maps known document and archive extensions case insensitively`() {
        assertEquals(FileType.PDF, "REPORT.PDF".toFileType())
        assertEquals(FileType.HTML, "page.htm".toFileType())
        assertEquals(FileType.CBZ, "comic.cbz".toFileType())
        assertEquals(FileType.UNKNOWN, "archive.zip".toFileType())
    }

    private fun book(
        id: String,
        displayName: String = "$id.epub",
        type: FileType = FileType.EPUB,
        title: String? = id,
        author: String? = null,
        timestamp: Long = 1L,
        progressPercentage: Float? = null,
        isRecent: Boolean = true,
        fileSize: Long = 0L,
        sourceFolder: String? = null,
        seriesName: String? = null,
        seriesIndex: Double? = null,
        tags: List<Tag> = emptyList()
    ) = BookItem(
        id = id,
        path = "/library/$displayName",
        type = type,
        displayName = displayName,
        timestamp = timestamp,
        title = title,
        author = author,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        fileSize = fileSize,
        sourceFolder = sourceFolder,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        tags = tags
    )

    private fun List<BookItem>.ids() = map { it.id }
}
