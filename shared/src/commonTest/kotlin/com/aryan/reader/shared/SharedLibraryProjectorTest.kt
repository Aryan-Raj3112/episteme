package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedLibraryProjectorTest {

    @Test
    fun `SharedLibraryStateProjector removes persisted duplicates for the same file path`() {
        val original = book(id = "original", title = "Book").copy(path = "/var/mobile/imports/book.epub")
        val duplicate = original.copy(
            id = "original_1",
            path = "/private/var/mobile/imports/book.epub",
            timestamp = original.timestamp - 1,
        )

        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(),
                booksFromStore = listOf(original, duplicate),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList(),
            )
        )

        assertEquals(listOf("original"), result.rawLibraryBooks.ids())
        assertEquals(listOf("original"), result.libraryBooks.ids())
    }

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
                sortOrder = SortOrder.TITLE_ASC
            )
        )

        assertEquals(listOf("new"), model.recentBooks.ids())
        assertEquals(listOf("old", "archived"), model.selectedBooks.ids())
        assertFalse(model.isEmpty)
    }

    @Test
    fun `LibraryProjector imports only new files and maps extensions and folders`() {
        val projector = LibraryProjector()
        val state = LibraryState(books = listOf(book("C:/books/existing.pdf", displayName = "existing.pdf", isRecent = false)))

        val result = projector.withImportedFiles(
            state,
            listOf(
                ImportedFile(name = "existing.pdf", path = "C:/books/existing.pdf", size = 1L),
                ImportedFile(name = "notes.md", path = "C:/books/notes.md", size = 2L, sourceFolder = "C:/books"),
                ImportedFile(name = "mystery.bin", path = null, size = 3L)
            )
        )

        assertEquals(listOf("C:/books/notes.md", "C:/books/existing.pdf"), result.books.ids())
        assertEquals(FileType.MD, result.books[0].type)
        assertEquals("C:/books", result.books[0].sourceFolder)
        assertFalse(result.books[0].isRecent)
        assertTrue(projector.home(result).recentBooks.isEmpty())
        assertEquals("Imported 1 file. Reader support comes later.", result.message)
        assertEquals("desktop_imported_file_count_reader_support_later", result.messageText?.name)

        val unsupportedOnly = projector.withImportedFiles(
            state,
            listOf(ImportedFile(name = "mystery.bin", path = null, size = 3L))
        )
        assertEquals(state.books.ids(), unsupportedOnly.books.ids())
        assertEquals("No supported files were imported.", unsupportedOnly.message)
    }

    @Test
    fun `SharedLibraryStateProjector prunes stale selections tabs and shelf state`() {
        val existing = book("existing")
        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(
                    selectedBookIds = setOf("existing", "missing"),
                    openTabIds = listOf("missing", "existing"),
                    activeTabBookId = "missing",
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
        assertEquals(listOf("existing"), result.openTabIds)
        assertNull(result.activeTabBookId)
        assertNull(result.viewingShelfId)
        assertFalse(result.isAddingBooksToShelf)
        assertTrue(result.selectedShelfIds.isEmpty())
    }

    @Test
    fun `SharedLibraryStateProjector keeps pinned home and library books first`() {
        val older = book("older", title = "Zulu", timestamp = 1L)
        val newer = book("newer", title = "Alpha", timestamp = 2L)

        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(
                    rawLibraryBooks = listOf(older, newer),
                    pinnedHomeBookIds = setOf("older"),
                    pinnedLibraryBookIds = setOf("older"),
                    sortOrder = SortOrder.TITLE_ASC
                ),
                booksFromStore = listOf(older, newer),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )

        assertEquals(listOf("older", "newer"), result.recentBooks.ids())
        assertEquals(listOf("older", "newer"), result.libraryBooks.ids())
    }

    @Test
    fun `SharedLibraryStateProjector keeps recent books ordered by recency when library sort changes`() {
        val olderTitleFirst = book("alpha", title = "Alpha", timestamp = 1L)
        val newerTitleLast = book("zulu", title = "Zulu", timestamp = 3L)
        val middleNotRecent = book("middle", title = "Middle", timestamp = 2L, isRecent = false)

        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(sortOrder = SortOrder.TITLE_ASC),
                booksFromStore = listOf(olderTitleFirst, newerTitleLast, middleNotRecent),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )

        assertEquals(listOf("alpha", "middle", "zulu"), result.libraryBooks.ids())
        assertEquals(listOf("zulu", "alpha"), result.recentBooks.ids())
    }

    @Test
    fun `shared app actions manage tabs and pins`() {
        val opened = SharedReaderScreenState()
            .reduce(AppAction.BookTabOpened("one"))
            .reduce(AppAction.BookTabOpened("two"))
            .reduce(AppAction.HomePinToggled("one"))
            .reduce(AppAction.LibraryPinToggled("two"))

        assertTrue(opened.isTabsEnabled)
        assertEquals(listOf("one", "two"), opened.openTabIds)
        assertEquals("two", opened.activeTabBookId)
        assertEquals(setOf("one"), opened.pinnedHomeBookIds)
        assertEquals(setOf("two"), opened.pinnedLibraryBookIds)

        val reactivated = opened.reduce(AppAction.BookTabOpened("one"))

        assertEquals(listOf("one", "two"), reactivated.openTabIds)
        assertEquals("one", reactivated.activeTabBookId)

        val closedActive = opened.reduce(AppAction.BookTabClosed("two"))

        assertEquals(listOf("one"), closedActive.openTabIds)
        assertEquals("one", closedActive.activeTabBookId)
        assertTrue(closedActive.reduce(AppAction.TabsEnabledChanged(false)).openTabIds.isEmpty())
    }

    @Test
    fun `projected tabs keep only twenty distinct available books`() {
        val pdfs = (1..(MAX_OPEN_PDF_TABS + 2)).map { index ->
            book("pdf-$index").copy(type = FileType.PDF)
        }
        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(
                    openTabIds = pdfs.map { it.id } + pdfs.first().id,
                    activeTabBookId = pdfs.last().id,
                ),
                booksFromStore = pdfs,
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList(),
            )
        )

        assertEquals(pdfs.take(MAX_OPEN_PDF_TABS).map { it.id }, result.openTabIds)
        assertNull(result.activeTabBookId)
        assertEquals(result.openTabIds, result.openTabs.map { it.id })
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
        val rootFolder = result.shelves.first { it.id == "folder_content://library" }
        val nestedFolder = result.shelves.first { it.id == "folder_content://library::Nested" }
        assertEquals(listOf("folder"), rootFolder.books.ids())
        assertTrue(rootFolder.directBooks.isEmpty())
        assertEquals(listOf(nestedFolder.id), rootFolder.childShelfIds)
        assertEquals(rootFolder.id, nestedFolder.parentShelfId)
        assertEquals(listOf("folder"), nestedFolder.directBooks.ids())
        assertEquals(listOf("loose", "tagged"), result.shelves.first { it.id == "unshelved" }.books.ids())
    }

    @Test
    fun `shelf add candidates match Android unshelved and all books sources`() {
        val alreadyAdded = book("already")
        val loose = book("loose")
        val otherShelfBook = book("other")
        val duplicateLoose = loose.copy(path = loose.path)
        val shelves = listOf(
            Shelf("target", "Target", ShelfType.MANUAL, books = listOf(alreadyAdded)),
            Shelf("other_shelf", "Other", ShelfType.MANUAL, books = listOf(otherShelfBook)),
            Shelf("unshelved", "Unshelved", ShelfType.MANUAL, books = listOf(loose, duplicateLoose)),
        )
        val allBooks = listOf(alreadyAdded, loose, otherShelfBook, duplicateLoose)

        assertEquals(
            listOf("loose"),
            booksAvailableForShelfAddition(
                allLibraryBooks = allBooks,
                shelves = shelves,
                shelfId = "target",
                source = AddBooksSource.UNSHELVED,
            ).ids(),
        )
        assertEquals(
            listOf("loose", "other"),
            booksAvailableForShelfAddition(
                allLibraryBooks = allBooks,
                shelves = shelves,
                shelfId = "target",
                source = AddBooksSource.ALL_BOOKS,
            ).ids(),
        )
    }

    @Test
    fun `SharedLibraryStateProjector ignores persisted shelf using reserved unshelved id`() {
        val loose = book("loose")

        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(),
                booksFromStore = listOf(loose),
                shelfRecords = listOf(ShelfRecord("unshelved", "Legacy shelf")),
                shelfRefs = listOf(BookShelfRef(bookId = loose.id, shelfId = "unshelved", addedAt = 1L)),
                tags = emptyList()
            )
        )

        assertEquals(1, result.shelves.count { it.id == "unshelved" })
        assertEquals("Unshelved", result.shelves.first { it.id == "unshelved" }.name)
        assertEquals(listOf("loose"), result.shelves.first { it.id == "unshelved" }.books.ids())
    }

    @Test
    fun `SharedLibraryStateProjector auto creates synced folder fallback shelves from source folders`() {
        val folderBook = book(
            id = "folder_book",
            sourceFolder = "C:/Library",
            path = "C:/Library/Nested/Book.epub"
        )

        val result = SharedLibraryStateProjector(
            SharedFolderPathResolver { item ->
                if (item.id == "folder_book") listOf("Nested") else emptyList()
            }
        ).project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(),
                booksFromStore = listOf(folderBook),
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )

        assertEquals(listOf("C:/Library"), result.syncedFolders.map { it.uriString })
        assertEquals("Library", result.syncedFolders.single().name)
        assertEquals(listOf("folder_book"), result.shelves.first { it.id == "folder_C:/Library" }.books.ids())
        assertEquals(listOf("folder_book"), result.shelves.first { it.id == "folder_C:/Library::Nested" }.directBooks.ids())
    }

    @Test
    fun `SharedLibraryStateProjector builds smart shelves from shared rules`() {
        val smartRules = SmartCollectionEngine.toJson(
            SmartCollectionDefinition(
                rules = listOf(
                    SmartRule(SmartField.FILE_TYPE, SmartOperator.EQUALS, "PDF"),
                    SmartRule(SmartField.PROGRESS, SmartOperator.GREATER_THAN, "75")
                )
            )
        )
        val matching = book("matching", type = FileType.PDF, progressPercentage = 90f)
        val wrongType = book("wrong_type", type = FileType.EPUB, progressPercentage = 90f)
        val wrongProgress = book("wrong_progress", type = FileType.PDF, progressPercentage = 20f)

        val result = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = SharedReaderScreenState(sortOrder = SortOrder.TITLE_ASC),
                booksFromStore = listOf(wrongType, wrongProgress, matching),
                shelfRecords = listOf(ShelfRecord("smart", "Almost Done PDFs", isSmart = true, smartRulesJson = smartRules)),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )

        val smartShelf = result.shelves.first { it.id == "smart" }
        assertEquals(ShelfType.SMART, smartShelf.type)
        assertEquals(listOf("matching"), smartShelf.books.ids())
        assertEquals(listOf("wrong_progress", "wrong_type"), result.shelves.first { it.id == "unshelved" }.books.ids())
    }

    @Test
    fun `SharedReaderScreenState withImportedFiles dedupes imports and reports duplicates`() {
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(book("/books/existing.epub", isRecent = false)))

        val imported = state.withImportedFiles(
            listOf(
                ImportedBookFile(name = "existing.epub", uriString = null, localPath = "/books/existing.epub", size = 1L),
                ImportedBookFile(name = "new.pdf", uriString = "content://new", localPath = null, size = 2L, sourceFolder = "content://folder")
            ),
            now = 10L
        )
        val duplicateOnly = imported.withImportedFiles(
            listOf(ImportedBookFile(name = "new.pdf", uriString = "content://new", localPath = null, size = 2L)),
            now = 20L
        )
        val unsupportedOnly = imported.withImportedFiles(
            listOf(ImportedBookFile(name = "archive.zip", uriString = null, localPath = "/books/archive.zip", size = 2L)),
            now = 30L
        )

        assertEquals(listOf("content://new", "/books/existing.epub"), imported.rawLibraryBooks.ids())
        assertEquals(FileType.PDF, imported.rawLibraryBooks.first().type)
        assertEquals("content://folder", imported.rawLibraryBooks.first().sourceFolder)
        assertEquals(11L, imported.rawLibraryBooks.first().timestamp)
        assertFalse(imported.rawLibraryBooks.first().isRecent)
        val projected = SharedLibraryStateProjector().project(
            SharedLibraryProjectionInput(
                state = imported,
                booksFromStore = imported.rawLibraryBooks,
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = emptyList()
            )
        )
        assertTrue(projected.recentBooks.isEmpty())
        assertEquals("Imported 1 file.", imported.bannerMessage?.message)
        assertEquals("desktop_imported_file_count", imported.bannerMessage?.text?.name)
        assertEquals("Those files are already in the library.", duplicateOnly.bannerMessage?.message)
        assertEquals(imported.rawLibraryBooks.ids(), unsupportedOnly.rawLibraryBooks.ids())
        assertEquals("No supported files were imported.", unsupportedOnly.bannerMessage?.message)
    }

    @Test
    fun `shared filters treat in app storage separately from opds streams`() {
        val localBook = book("local", sourceFolder = null, path = "file:///local/book.epub")
        val streamedBook = book("streamed", sourceFolder = null, path = "opds-pse://book")
        val syncedBook = book("synced", sourceFolder = "content://sync", path = "content://synced")

        assertEquals(
            listOf("local"),
            applyLibraryFilters(
                listOf(localBook, streamedBook, syncedBook),
                LibraryFilters(sourceFolders = setOf(IN_APP_STORAGE_SOURCE))
            ).ids()
        )
        assertEquals(
            listOf("synced"),
            applyLibraryFilters(
                listOf(localBook, streamedBook, syncedBook),
                LibraryFilters(sourceFolders = setOf("content://sync"))
            ).ids()
        )
    }

    @Test
    fun `shared sort keeps books without authors last`() {
        val unknown = book("unknown", title = null, author = null, displayName = "Zulu.epub")
        val known = book("known", title = null, author = "Ada", displayName = "Beta.epub")
        val title = book("title", title = "Omega", author = "Grace", displayName = "Alpha.epub")

        assertEquals(listOf("known", "title", "unknown"), sortBooks(listOf(unknown, known, title), SortOrder.AUTHOR_ASC).ids())
    }

    @Test
    fun `shared sort supports newest and oldest date added`() {
        val oldest = book("oldest", timestamp = 10L)
        val newest = book("newest", timestamp = 30L)
        val middle = book("middle", timestamp = 20L)

        assertEquals(listOf("newest", "middle", "oldest"), sortBooks(listOf(oldest, newest, middle), SortOrder.DATE_ADDED_NEWEST).ids())
        assertEquals(listOf("oldest", "middle", "newest"), sortBooks(listOf(oldest, newest, middle), SortOrder.DATE_ADDED_OLDEST).ids())
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
        assertEquals(FileType.CBT, "comic.cbt".toFileType())
        assertEquals(FileType.UNKNOWN, "archive.zip".toFileType())
    }

    @Test
    fun `synced folder addition follows android ten-folder cap`() {
        fun folders(count: Int) = (0 until count).map { index ->
            SyncedFolder(uriString = "folder-$index", name = "Folder $index", lastScanTime = 0L)
        }

        assertTrue(canAddSyncedFolder(folders(MAX_SYNCED_FOLDER_COUNT - 1)))
        assertFalse(canAddSyncedFolder(folders(MAX_SYNCED_FOLDER_COUNT)))
        assertTrue(
            canAddSyncedFolder(
                folders(MAX_SYNCED_FOLDER_COUNT - 1) +
                    SyncedFolder(uriString = " folder-0 ", name = "Duplicate", lastScanTime = 0L)
            )
        )
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
        path: String? = "/library/$displayName",
        seriesName: String? = null,
        seriesIndex: Double? = null,
        tags: List<Tag> = emptyList()
    ) = BookItem(
        id = id,
        path = path,
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
