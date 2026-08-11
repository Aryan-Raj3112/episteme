package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedMobileLibraryMutationsTest {
    @Test
    fun `synced folder mutations preserve Android add update and removal policy`() {
        val existing = SyncedFolder(
            uriString = "folder://existing",
            name = "Existing",
            lastScanTime = 1L,
            allowedFileTypes = setOf(FileType.PDF, FileType.EPUB),
        )

        assertEquals(SyncedFolderAddDecision.INVALID_URI, syncedFolderAddDecision(listOf(existing), "  "))
        assertEquals(
            SyncedFolderAddDecision.ALREADY_SYNCED,
            syncedFolderAddDecision(listOf(existing), existing.uriString),
        )
        assertEquals(
            SyncedFolderAddDecision.LIMIT_REACHED,
            syncedFolderAddDecision(
                List(MAX_SYNCED_FOLDER_COUNT) { index -> existing.copy(uriString = "folder://$index") },
                "folder://new",
            ),
        )

        val added = listOf(existing).withSyncedFolder(
            existing.copy(uriString = "  folder://new  ", name = "New"),
        )
        assertEquals(listOf("folder://existing", "folder://new"), added.map { it.uriString })

        val disabled = added.withSyncedFolderLocalSync(existing.uriString, enabled = false)
        assertFalse(disabled.first().localSyncEnabled)
        assertTrue(disabled.last().localSyncEnabled)

        val filtered = disabled.withSyncedFolderFileTypes(
            uriString = existing.uriString,
            requestedFileTypes = setOf(FileType.PDF, FileType.UNKNOWN, FileType.MOBI),
            supportedFileTypes = setOf(FileType.PDF, FileType.MOBI),
        )
        assertEquals(setOf(FileType.PDF, FileType.MOBI), filtered.first().allowedFileTypes)
        assertEquals(added.last().allowedFileTypes, filtered.last().allowedFileTypes)

        assertEquals(listOf("folder://new"), filtered.withoutSyncedFolder(existing.uriString).map { it.uriString })
    }

    @Test
    fun `mobile folder scans apply only to new or locally enabled folders`() {
        val enabled = SyncedFolder(
            uriString = "folder://enabled",
            name = "Enabled",
            lastScanTime = 0L,
            localSyncEnabled = true,
        )
        val disabled = enabled.copy(
            uriString = "folder://disabled",
            name = "Disabled",
            localSyncEnabled = false,
        )

        assertTrue(shouldApplyMobileFolderScan(null))
        assertTrue(shouldApplyMobileFolderScan(enabled))
        assertFalse(shouldApplyMobileFolderScan(disabled))
    }

    @Test
    fun `mobile folder file type changes immediately remove excluded books everywhere`() {
        val folder = SyncedFolder(
            uriString = "folder://library",
            name = "Library",
            lastScanTime = 1L,
            allowedFileTypes = setOf(FileType.PDF, FileType.EPUB),
        )
        val pdf = BookItem("pdf", "/managed/pdf", FileType.PDF, "book.pdf", timestamp = 1L, sourceFolder = folder.name)
        val epub = BookItem("epub", "/managed/epub", FileType.EPUB, "book.epub", timestamp = 2L, sourceFolder = folder.name)
        val independent = BookItem("local", "/imports/local", FileType.EPUB, "local.epub", timestamp = 3L)
        val shelf = Shelf(
            id = "shelf",
            name = "Shelf",
            type = ShelfType.MANUAL,
            books = listOf(pdf, epub, independent),
            directBooks = listOf(pdf, epub, independent),
        )
        val state = SharedReaderScreenState(
            syncedFolders = listOf(folder),
            rawLibraryBooks = listOf(pdf, epub, independent),
            libraryBooks = listOf(pdf, epub, independent),
            recentBooks = listOf(pdf, epub, independent),
            openTabs = listOf(pdf, epub),
            openTabIds = listOf(pdf.id, epub.id),
            activeTabBookId = pdf.id,
            selectedBookId = pdf.id,
            selectedBookIds = setOf(pdf.id, independent.id),
            booksSelectedForAdding = setOf(pdf.id, independent.id),
            pinnedHomeBookIds = setOf(pdf.id, independent.id),
            pinnedLibraryBookIds = setOf(pdf.id, independent.id),
            shelves = listOf(shelf),
        )

        val update = state.withMobileFolderFileTypes(folder, setOf(FileType.EPUB))

        assertEquals(setOf(pdf.id), update.removedBookIds)
        assertEquals(listOf(epub.id, independent.id), update.state.rawLibraryBooks.map { it.id })
        assertEquals(listOf(epub.id), update.state.openTabIds)
        assertNull(update.state.activeTabBookId)
        assertNull(update.state.selectedBookId)
        assertEquals(setOf(independent.id), update.state.selectedBookIds)
        assertEquals(setOf(independent.id), update.state.booksSelectedForAdding)
        assertEquals(setOf(independent.id), update.state.pinnedHomeBookIds)
        assertEquals(setOf(independent.id), update.state.pinnedLibraryBookIds)
        assertEquals(listOf(epub.id, independent.id), update.state.shelves.single().books.map { it.id })
        assertEquals(setOf(FileType.EPUB), update.state.syncedFolders.single().allowedFileTypes)
    }

    @Test
    fun `mobile import batch reports added duplicate unsupported and native copy failures`() {
        val outcome = planMobileImportBatch(
            files = listOf(
                ImportedBookFile(name = "new.epub", uriString = null, localPath = "/imports/new.epub", size = 1L, id = "new"),
                ImportedBookFile(name = "again.pdf", uriString = null, localPath = "/imports/again.pdf", size = 1L, id = "existing"),
                ImportedBookFile(name = "notes.xyz", uriString = null, localPath = "/imports/notes.xyz", size = 1L, id = "unknown"),
            ),
            existingBookIds = setOf("existing"),
            failedCount = 2,
            nowMillis = 100L,
        )

        assertEquals(1, outcome.counts.addedCount)
        assertEquals(1, outcome.counts.duplicateCount)
        assertEquals(1, outcome.counts.unsupportedCount)
        assertEquals(2, outcome.counts.failedCount)
        assertEquals(listOf("new"), outcome.plan.importedBooks.map { it.id })
    }

    @Test
    fun `single mobile selection opens a new import or its existing duplicate`() {
        val existing = BookItem(
            id = "existing",
            path = "/library/existing.epub",
            type = FileType.EPUB,
            displayName = "Existing",
            timestamp = 1L,
        )
        val imported = planMobileImportBatch(
            files = listOf(
                ImportedBookFile(
                    name = "new.epub",
                    uriString = null,
                    localPath = "/imports/new.epub",
                    size = 1L,
                    id = "new",
                )
            ),
            existingBookIds = setOf(existing.id),
        )
        val duplicate = planMobileImportBatch(
            files = listOf(
                ImportedBookFile(
                    name = "existing-copy.epub",
                    uriString = null,
                    localPath = "/imports/existing-copy.epub",
                    size = 1L,
                    id = existing.id,
                )
            ),
            existingBookIds = setOf(existing.id),
        )

        assertEquals("new", imported.singleSelectionOpenBook(listOf(existing))?.id)
        assertEquals(existing, duplicate.singleSelectionOpenBook(listOf(existing)))
    }

    @Test
    fun `bulk or failed mobile selections do not auto open a book`() {
        val bulk = planMobileImportBatch(
            files = listOf(
                ImportedBookFile("one.epub", null, "/imports/one.epub", 1L, id = "one"),
                ImportedBookFile("two.epub", null, "/imports/two.epub", 1L, id = "two"),
            ),
            existingBookIds = emptySet(),
        )
        val partial = planMobileImportBatch(
            files = listOf(
                ImportedBookFile("one.epub", null, "/imports/one.epub", 1L, id = "one"),
            ),
            existingBookIds = emptySet(),
            failedCount = 1,
        )

        assertNull(bulk.singleSelectionOpenBook(emptyList()))
        assertNull(partial.singleSelectionOpenBook(emptyList()))
    }

    @Test
    fun `mobile open preflight removes a missing folder book before cloud handling`() {
        val folderBook = BookItem(
            id = "folder-book",
            path = "/folders/Books/missing.epub",
            type = FileType.EPUB,
            displayName = "Missing",
            timestamp = 1L,
            isAvailable = false,
            sourceFolder = "Books",
        )

        assertEquals(
            MobileBookOpenPreflightAction.REMOVE_MISSING_FOLDER_BOOK,
            mobileBookOpenPreflightAction(
                book = folderBook,
                localFileExists = false,
                canDownload = true,
            ),
        )
        assertEquals(
            MobileBookOpenPreflightAction.DOWNLOAD,
            mobileBookOpenPreflightAction(
                book = folderBook.copy(sourceFolder = null),
                localFileExists = false,
                canDownload = true,
            ),
        )
    }

    @Test
    fun `mobile open preflight distinguishes missing location from unavailable content`() {
        val availableWithoutPath = BookItem(
            id = "missing-location",
            path = null,
            type = FileType.PDF,
            displayName = "Missing",
            timestamp = 1L,
            isAvailable = true,
        )

        assertEquals(
            MobileBookOpenPreflightAction.SHOW_MISSING_LOCATION,
            mobileBookOpenPreflightAction(
                book = availableWithoutPath,
                localFileExists = false,
                canDownload = false,
            ),
        )
        assertEquals(
            MobileBookOpenPreflightAction.SHOW_UNAVAILABLE,
            mobileBookOpenPreflightAction(
                book = availableWithoutPath.copy(isAvailable = false),
                localFileExists = false,
                canDownload = false,
            ),
        )
        assertEquals(
            MobileBookOpenPreflightAction.OPEN,
            mobileBookOpenPreflightAction(
                book = availableWithoutPath.copy(path = "/imports/book.pdf"),
                localFileExists = true,
                canDownload = false,
            ),
        )
    }

    @Test
    fun `mobile library search ignores late query callbacks after close`() {
        val active = SharedReaderScreenState()
            .withMobileLibrarySearchActive(true)
            .withMobileLibrarySearchQuery("reader")
        val closed = active.withMobileLibrarySearchActive(false)
        val lateCallback = closed.withMobileLibrarySearchQuery("reader!")

        assertTrue(active.isSearchActive)
        assertEquals("reader", active.searchQuery)
        assertFalse(closed.isSearchActive)
        assertEquals("", closed.searchQuery)
        assertEquals(closed, lateCallback)
    }

    @Test
    fun `folder sync toggle requests cloud work only when both toggles are enabled`() {
        assertTrue(shouldRequestCloudSyncAfterFolderSyncChange(true, true))
        assertFalse(shouldRequestCloudSyncAfterFolderSyncChange(true, false))
        assertFalse(shouldRequestCloudSyncAfterFolderSyncChange(false, true))
        assertFalse(shouldRequestCloudSyncAfterFolderSyncChange(false, false))
    }

    @Test
    fun `mobile folder scan queue preserves every folder and replaces only duplicate pending work`() {
        val first = SharedMobileFolderScanResult(" First ", emptyList())
        val second = SharedMobileFolderScanResult("Second", emptyList())
        val newerFirst = SharedMobileFolderScanResult("First", emptyList(), succeeded = false)

        val queued = enqueueMobileFolderScan(
            enqueueMobileFolderScan(emptyList(), first),
            second,
        )
        val replaced = enqueueMobileFolderScan(queued, newerFirst)

        assertEquals(listOf("First", "Second"), queued.map { it.folderName })
        assertEquals(listOf("Second", "First"), replaced.map { it.folderName })
        assertFalse(replaced.last().succeeded)
        assertEquals(queued, enqueueMobileFolderScan(queued, first.copy(folderName = " ")))
    }

    @Test
    fun `external file close policy matches Android managed-copy lifecycle`() {
        assertEquals(
            MobileExternalFileCloseAction.PROMPT,
            mobileExternalFileCloseAction("ASK"),
        )
        assertEquals(
            MobileExternalFileCloseAction.KEEP,
            mobileExternalFileCloseAction("KEEP"),
        )
        assertEquals(
            MobileExternalFileCloseAction.KEEP,
            mobileExternalFileCloseAction("COPY"),
        )
        assertEquals(
            MobileExternalFileCloseAction.DELETE,
            mobileExternalFileCloseAction("DELETE"),
        )
        assertEquals(
            MobileExternalFileCloseAction.DELETE,
            mobileExternalFileCloseAction("TEMPORARY"),
        )
        assertEquals(
            MobileExternalFileCloseAction.KEEP,
            mobileExternalFileCloseAction("unknown"),
        )
        assertEquals(
            MobileExternalFileCloseAction.KEEP,
            mobileExternalFileCloseAction(null),
        )
        assertEquals(
            MobileExternalFileCloseAction.DELETE,
            mobileExternalFileCloseAction("KEEP", isTemporarySession = true),
        )
    }

    @Test
    fun `external open routing matches the Android activity decision`() {
        assertEquals(
            MobileExternalOpenAction.OPEN_TEMPORARY,
            mobileExternalOpenAction("TEMPORARY"),
        )
        listOf(null, "ASK", "KEEP", "COPY", "DELETE", "temporary", "unknown").forEach { behavior ->
            assertEquals(
                MobileExternalOpenAction.OPEN_LIBRARY_COPY,
                mobileExternalOpenAction(behavior),
            )
        }
    }

    @Test
    fun `mobile import outcome reducer counts every terminal result`() {
        val counts = listOf(
            MobileImportOutcome.ADDED,
            MobileImportOutcome.ADDED,
            MobileImportOutcome.DUPLICATE,
            MobileImportOutcome.UNSUPPORTED,
            MobileImportOutcome.FAILED,
        ).fold(SharedImportOutcomeCounts()) { current, outcome -> current.record(outcome) }

        assertEquals(
            SharedImportOutcomeCounts(
                addedCount = 2,
                duplicateCount = 1,
                unsupportedCount = 1,
                failedCount = 1,
            ),
            counts,
        )
    }

    @Test
    fun `mobile library navigation restores only an existing shelf`() {
        val shelf = Shelf("reading", "Reading", ShelfType.MANUAL, books = emptyList())
        val state = SharedReaderScreenState(shelves = listOf(shelf))

        val restored = state.withRestoredMobileLibraryNavigation(
            restoredShelfId = " reading ",
            restoredIsAddingBooks = true,
            restoredAddBooksSource = AddBooksSource.ALL_BOOKS,
        )
        val stale = state.withRestoredMobileLibraryNavigation(
            restoredShelfId = "deleted",
            restoredIsAddingBooks = true,
            restoredAddBooksSource = AddBooksSource.ALL_BOOKS,
        )

        assertEquals(shelf.id, restored.viewingShelfId)
        assertTrue(restored.isAddingBooksToShelf)
        assertEquals(AddBooksSource.ALL_BOOKS, restored.addBooksSource)
        assertNull(stale.viewingShelfId)
        assertFalse(stale.isAddingBooksToShelf)
        assertEquals(AddBooksSource.UNSHELVED, stale.addBooksSource)
    }

    @Test
    fun `mobile PDF tab policy matches Android twenty tab limit`() {
        val full = (1..MAX_OPEN_PDF_TABS).map { "book-$it" }

        assertFalse(canOpenMobilePdfTab(full, "new-book"))
        assertTrue(canOpenMobilePdfTab(full, "book-10"))
        assertTrue(canOpenMobilePdfTab(full + " book-10 ", "book-10"))
        assertFalse(canOpenMobilePdfTab(full, " "))
        assertTrue(canOpenMobilePdfTab(full.dropLast(1), "new-book"))
    }

    @Test
    fun `book identity migration preserves library references`() {
        val legacy = book(id = "legacy-path-id")
        val shelf = Shelf(
            id = "favorites",
            name = "Favorites",
            type = ShelfType.MANUAL,
            books = listOf(legacy),
            directBooks = listOf(legacy),
        )
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(legacy),
            libraryBooks = listOf(legacy),
            recentBooks = listOf(legacy),
            openTabs = listOf(legacy),
            selectedBookIds = setOf(legacy.id),
            pinnedHomeBookIds = setOf(legacy.id),
            pinnedLibraryBookIds = setOf(legacy.id),
            openTabIds = listOf(legacy.id),
            activeTabBookId = legacy.id,
            shelves = listOf(shelf),
            cloudBookTombstones = listOf(CloudBookTombstone(legacy.id, FileType.EPUB.name, 2L)),
        )

        val migrated = state.withMigratedMobileBookIdentity(legacy.id, "sha256-id")

        assertEquals(listOf("sha256-id"), migrated.rawLibraryBooks.map { it.id })
        assertEquals(setOf("sha256-id"), migrated.selectedBookIds)
        assertEquals(setOf("sha256-id"), migrated.pinnedHomeBookIds)
        assertEquals(listOf("sha256-id"), migrated.openTabIds)
        assertEquals("sha256-id", migrated.activeTabBookId)
        assertEquals("sha256-id", migrated.shelves.single().books.single().id)
        assertTrue(migrated.cloudBookTombstones.isEmpty())
    }

    @Test
    fun `mobile imports add only new books to every mobile library collection`() {
        val existing = book(id = "existing", timestamp = 1L)
        val added = book(id = "added", timestamp = 2L)
        val result = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing),
            recentBooks = listOf(existing),
            libraryBooks = listOf(existing)
        ).withMobileImportedBooks(listOf(existing, added))

        assertEquals(listOf("added"), result.addedBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.rawLibraryBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.recentBooks.map { it.id })
        assertEquals(listOf("added", "existing"), result.state.libraryBooks.map { it.id })
        assertEquals("Added 1 book(s)", result.state.bannerMessage?.message)
    }

    @Test
    fun `mobile imports reject the same file even when its generated id differs`() {
        val existing = book(id = "ios_import_book", timestamp = 1L).copy(path = "/imports/book.epub")
        val duplicate = existing.copy(id = "ios_import_book_1", timestamp = 2L)

        val result = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing),
            recentBooks = listOf(existing),
            libraryBooks = listOf(existing),
        ).withMobileImportedBooks(listOf(duplicate))

        assertTrue(result.addedBooks.isEmpty())
        assertEquals(listOf("ios_import_book"), result.state.rawLibraryBooks.map { it.id })
    }

    @Test
    fun `reimporting a newer deleted book clears its cloud tombstone`() {
        val replacement = book(id = "book", timestamp = 200L)
        val result = SharedReaderScreenState(
            cloudBookTombstones = listOf(
                CloudBookTombstone(
                    bookId = replacement.id,
                    type = replacement.type.name,
                    deletedAt = 100L,
                )
            )
        ).withMobileImportedBooks(listOf(replacement))

        assertEquals(listOf(replacement), result.addedBooks)
        assertEquals(emptyList(), result.state.cloudBookTombstones)
    }

    @Test
    fun `opening and closing a mobile book keeps tab and selected book state aligned`() {
        val first = book(id = "first", timestamp = 1L)
        val second = book(id = "second", timestamp = 2L)
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(first, second))
            .withMobileBookOpened(first)
            .withMobileBookOpened(second)

        assertEquals(listOf("first", "second"), state.openTabIds)
        assertEquals(listOf("first", "second"), state.openTabs.map { it.id })
        assertEquals("second", state.activeTabBookId)
        assertEquals("second", state.selectedBookId)

        val closed = state.withMobileBookClosed("second")

        assertEquals(listOf("first"), closed.openTabIds)
        assertEquals(listOf("first"), closed.openTabs.map { it.id })
        assertEquals("first", closed.activeTabBookId)
        assertEquals("first", closed.selectedBookId)
        assertEquals(first.path, closed.selectedUriString)
        assertEquals(first.type, closed.selectedFileType)
    }

    @Test
    fun `opening a book restores it to recents and moves it to the front`() {
        val first = book(id = "first", timestamp = 1L)
        val second = book(id = "second", timestamp = 2L)
        val hidden = first.copy(isRecent = false)

        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(hidden, second),
            libraryBooks = listOf(hidden, second),
            recentBooks = listOf(second),
        ).withMobileBookOpened(hidden, openedAt = 99L)

        assertEquals(listOf("first", "second"), state.recentBooks.map { it.id })
        assertEquals(99L, state.recentBooks.first().timestamp)
        assertEquals(true, state.recentBooks.first().isRecent)
        assertEquals(99L, state.rawLibraryBooks.first { it.id == "first" }.timestamp)
    }

    @Test
    fun `opening non pdf books does not create an active tab like android`() {
        val mobi = book(id = "mobi").copy(type = FileType.MOBI, path = "/books/mobi.mobi")

        val state = SharedReaderScreenState(rawLibraryBooks = listOf(mobi))
            .withMobileBookOpened(mobi)

        assertEquals(emptyList(), state.openTabIds)
        assertEquals(emptyList(), state.openTabs)
        assertEquals("mobi", state.selectedBookId)
    }

    @Test
    fun `opening a pdf while tabs are disabled does not enable or create tabs`() {
        val pdf = book(id = "pdf")

        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(pdf),
            isTabsEnabled = false,
        ).withMobileBookOpened(pdf)

        assertEquals(false, state.isTabsEnabled)
        assertEquals(emptyList(), state.openTabIds)
        assertEquals(emptyList(), state.openTabs)
    }

    @Test
    fun `tab-only mutations preserve reader selection for platform navigation owners`() {
        val original = SharedReaderScreenState(
            selectedBookId = "selected",
            selectedUriString = "/books/selected.pdf",
            selectedFileType = FileType.PDF
        )

        val opened = original.withMobileBookTabOpened("other")
        val closed = opened.withMobileBookTabClosed("other")

        assertEquals("selected", closed.selectedBookId)
        assertEquals("/books/selected.pdf", closed.selectedUriString)
        assertEquals(FileType.PDF, closed.selectedFileType)
        assertEquals(emptyList(), closed.openTabIds)
    }

    @Test
    fun `closing the final mobile book clears the selected reader identity`() {
        val only = book(id = "only")
        val closed = SharedReaderScreenState(rawLibraryBooks = listOf(only))
            .withMobileBookOpened(only)
            .withMobileBookClosed(only.id)

        assertNull(closed.selectedBookId)
        assertNull(closed.selectedUriString)
        assertNull(closed.selectedFileType)
    }

    @Test
    fun `reader session restore requires a matching available library book and type`() {
        val available = book(id = "available").copy(path = "/books/available.pdf")
        val missing = book(id = "missing").copy(path = null, isAvailable = false)
        val books = listOf(available, missing)

        assertEquals(
            available,
            resolveMobileReaderSessionBook(books, " available ", FileType.PDF),
        )
        assertNull(resolveMobileReaderSessionBook(books, "available", FileType.EPUB))
        assertNull(resolveMobileReaderSessionBook(books, "missing", FileType.PDF))
        assertNull(resolveMobileReaderSessionBook(books, "unknown", FileType.PDF))
        assertNull(resolveMobileReaderSessionBook(books, " ", FileType.PDF))
        assertNull(resolveMobileReaderSessionBook(books, "available", null))
    }

    @Test
    fun `restored and intentionally closed reader sessions update only reader identity`() {
        val pdf = book(id = "pdf").copy(path = "/books/pdf.pdf")
        val original = SharedReaderScreenState(
            rawLibraryBooks = listOf(pdf),
            openTabIds = listOf(pdf.id),
            activeTabBookId = pdf.id,
            bannerMessage = BannerMessage("old"),
        )

        val restored = original.withRestoredMobileReaderSession(pdf)
        val closed = restored.withoutMobileReaderSession()

        assertEquals(pdf.id, restored.selectedBookId)
        assertEquals(pdf.path, restored.selectedUriString)
        assertEquals(pdf.type, restored.selectedFileType)
        assertNull(restored.bannerMessage)
        assertNull(closed.selectedBookId)
        assertNull(closed.selectedUriString)
        assertNull(closed.selectedFileType)
        assertEquals(original.openTabIds, closed.openTabIds)
        assertEquals(original.activeTabBookId, closed.activeTabBookId)
        assertEquals(original.rawLibraryBooks, closed.rawLibraryBooks)
    }

    @Test
    fun `temporary external session never enters library recents shelves or tabs`() {
        val stored = book(id = "stored")
        val temporary = book(id = "temporary").copy(path = "/tmp/temporary.pdf")
        val original = SharedReaderScreenState(
            rawLibraryBooks = listOf(stored),
            libraryBooks = listOf(stored),
            recentBooks = listOf(stored),
            openTabIds = listOf(stored.id),
            activeTabBookId = stored.id,
        )

        val opened = original.withMobileTemporaryBookOpened(temporary)

        assertEquals(listOf(stored), opened.rawLibraryBooks)
        assertEquals(listOf(stored), opened.libraryBooks)
        assertEquals(listOf(stored), opened.recentBooks)
        assertEquals(listOf(stored.id), opened.openTabIds)
        assertEquals(temporary.id, opened.selectedBookId)
        assertEquals(temporary.path, opened.selectedUriString)

        val closed = opened.withMobileTemporaryBookClosed(temporary.id)

        assertNull(closed.selectedBookId)
        assertNull(closed.selectedUriString)
        assertNull(closed.selectedFileType)
        assertEquals(listOf(stored), closed.rawLibraryBooks)
        assertEquals(listOf(stored), closed.recentBooks)
        assertEquals(listOf(stored.id), closed.openTabIds)
        assertEquals(stored.id, closed.activeTabBookId)
    }

    private fun book(id: String, timestamp: Long = 0L): BookItem {
        return BookItem(
            id = id,
            path = "/books/$id.pdf",
            type = FileType.PDF,
            displayName = "$id.pdf",
            timestamp = timestamp
        )
    }
}
