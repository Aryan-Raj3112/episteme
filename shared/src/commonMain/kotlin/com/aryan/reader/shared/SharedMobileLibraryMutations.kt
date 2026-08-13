package com.aryan.reader.shared

const val MAX_OPEN_PDF_TABS = 20

enum class MobileExternalOpenAction {
    OPEN_LIBRARY_COPY,
    OPEN_TEMPORARY,
}

/**
 * Android routes only the persisted TEMPORARY value to its temporary reader
 * activity. All other values use the normal import/open flow.
 */
fun mobileExternalOpenAction(behavior: String?): MobileExternalOpenAction =
    if (behavior == "TEMPORARY") {
        MobileExternalOpenAction.OPEN_TEMPORARY
    } else {
        MobileExternalOpenAction.OPEN_LIBRARY_COPY
    }

enum class MobileExternalFileCloseAction {
    KEEP,
    PROMPT,
    DELETE,
}

fun mobileExternalFileCloseAction(
    behavior: String?,
    isTemporarySession: Boolean = false,
): MobileExternalFileCloseAction {
    if (isTemporarySession) return MobileExternalFileCloseAction.DELETE
    return when (behavior) {
        "KEEP", "COPY" -> MobileExternalFileCloseAction.KEEP
        "DELETE", "TEMPORARY" -> MobileExternalFileCloseAction.DELETE
        "ASK" -> MobileExternalFileCloseAction.PROMPT
        else -> MobileExternalFileCloseAction.KEEP
    }
}

fun shouldRequestCloudSyncAfterFolderSyncChange(
    folderSyncEnabled: Boolean,
    cloudSyncEnabled: Boolean,
): Boolean = folderSyncEnabled && cloudSyncEnabled

data class SharedMobileFolderScanResult(
    val folderName: String,
    val files: List<SharedFolderScannedFile>,
    val succeeded: Boolean = true,
)

/**
 * Keeps folders with the same provider display name independently addressable.
 * The first folder retains Android's existing display name and later collisions
 * receive the smallest available numeric suffix.
 */
fun availableMobileFolderName(
    preferredName: String,
    existingNames: Collection<String>,
): String {
    val baseName = preferredName.trim().ifBlank { "Folder" }
    val usedNames = existingNames.toHashSet()
    if (baseName !in usedNames) return baseName
    var suffix = 2
    while ("$baseName ($suffix)" in usedNames) suffix += 1
    return "$baseName ($suffix)"
}

fun enqueueMobileFolderScan(
    queue: List<SharedMobileFolderScanResult>,
    result: SharedMobileFolderScanResult,
): List<SharedMobileFolderScanResult> {
    val folderName = result.folderName.trim()
    if (folderName.isBlank()) return queue
    return queue.filterNot { it.folderName == folderName } + result.copy(folderName = folderName)
}

/**
 * Android's folder worker only includes locally enabled folders. Native iOS
 * discovery can still deliver a queued result for a disabled bookmark, so the
 * shared state boundary must discard it.
 *
 * A missing configuration is accepted because it represents the first scan of
 * a newly selected folder.
 */
fun shouldApplyMobileFolderScan(configuredFolder: SyncedFolder?): Boolean =
    configuredFolder?.localSyncEnabled != false

data class SharedMobileFolderFileTypesUpdate(
    val state: SharedReaderScreenState,
    val removedBookIds: Set<String>,
)

/**
 * Android removes books excluded by a folder filter before scheduling the
 * rescan. Doing the mutation here keeps iOS correct even when native folder
 * discovery subsequently fails.
 */
fun SharedReaderScreenState.withMobileFolderFileTypes(
    folder: SyncedFolder,
    allowedFileTypes: Set<FileType>,
): SharedMobileFolderFileTypesUpdate {
    val removedBookIds = rawLibraryBooks
        .asSequence()
        .filter { it.sourceFolder == folder.uriString || it.sourceFolder == folder.name }
        .filter { it.type !in allowedFileTypes }
        .mapTo(linkedSetOf()) { it.id }
    fun List<BookItem>.withoutRemoved(): List<BookItem> =
        filterNot { it.id in removedBookIds }
    val updatedState = copy(
        syncedFolders = syncedFolders.map { configured ->
            if (configured.uriString == folder.uriString) {
                configured.copy(allowedFileTypes = allowedFileTypes)
            } else {
                configured
            }
        },
        rawLibraryBooks = rawLibraryBooks.withoutRemoved(),
        libraryBooks = libraryBooks.withoutRemoved(),
        recentBooks = recentBooks.withoutRemoved(),
        openTabs = openTabs.withoutRemoved(),
        openTabIds = openTabIds.filterNot { it in removedBookIds },
        activeTabBookId = activeTabBookId?.takeUnless { it in removedBookIds },
        selectedBookId = selectedBookId?.takeUnless { it in removedBookIds },
        selectedBookIds = selectedBookIds - removedBookIds,
        booksSelectedForAdding = booksSelectedForAdding - removedBookIds,
        pinnedHomeBookIds = pinnedHomeBookIds - removedBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds - removedBookIds,
        shelves = shelves.map { shelf ->
            shelf.copy(
                books = shelf.books.withoutRemoved(),
                directBooks = shelf.directBooks.withoutRemoved(),
            )
        },
    )
    return SharedMobileFolderFileTypesUpdate(
        state = updatedState,
        removedBookIds = removedBookIds,
    )
}

fun canOpenMobilePdfTab(openTabIds: Collection<String>, bookId: String): Boolean {
    val normalizedBookId = bookId.trim()
    if (normalizedBookId.isBlank()) return false
    val currentTabIds = openTabIds.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return normalizedBookId in currentTabIds || currentTabIds.size < MAX_OPEN_PDF_TABS
}

/**
 * Platform-neutral state changes used when a phone app imports or opens books.
 *
 * Native file pickers, persistence, and reader engines remain platform-owned;
 * this keeps the visible mobile library state identical once either platform
 * has completed those operations.
 */
data class SharedMobileImportResult(
    val state: SharedReaderScreenState,
    val addedBooks: List<BookItem>
)

data class SharedMobileImportBatchOutcome(
    val plan: SharedImportPlan,
    val counts: SharedImportOutcomeCounts,
)

/**
 * Android routes a one-item picker result through its regular open/import path:
 * a new book opens after import, while selecting the same content again opens
 * the existing library item. Multi-select remains a library-only bulk import.
 */
fun SharedMobileImportBatchOutcome.singleSelectionOpenBook(
    existingBooks: List<BookItem>,
): BookItem? {
    val selectedCount = counts.addedCount +
        counts.duplicateCount +
        counts.unsupportedCount +
        counts.failedCount
    if (selectedCount != 1) return null

    val decision = plan.decisions.singleOrNull() ?: return null
    return when (decision.status) {
        SharedImportDecisionStatus.IMPORTABLE ->
            plan.importedBooks.singleOrNull { it.id == decision.id }
        SharedImportDecisionStatus.DUPLICATE ->
            existingBooks.firstOrNull { it.id == decision.id }
        SharedImportDecisionStatus.UNSUPPORTED -> null
    }
}

enum class MobileBookOpenPreflightAction {
    OPEN,
    REMOVE_MISSING_FOLDER_BOOK,
    SHOW_MISSING_LOCATION,
    DOWNLOAD,
    SHOW_UNAVAILABLE,
}

/**
 * Mirrors Android's recent-item click ordering. Folder-backed entries are
 * checked before availability so a file removed outside the app is cleaned
 * from the library instead of being mistaken for a cloud-only download.
 */
fun mobileBookOpenPreflightAction(
    book: BookItem,
    localFileExists: Boolean,
    canDownload: Boolean,
): MobileBookOpenPreflightAction {
    if (
        !book.sourceFolder.isNullOrBlank() &&
        !book.path.isNullOrBlank() &&
        !localFileExists
    ) {
        return MobileBookOpenPreflightAction.REMOVE_MISSING_FOLDER_BOOK
    }
    if (book.isAvailable) {
        return if (book.path.isNullOrBlank()) {
            MobileBookOpenPreflightAction.SHOW_MISSING_LOCATION
        } else {
            MobileBookOpenPreflightAction.OPEN
        }
    }
    return if (canDownload) {
        MobileBookOpenPreflightAction.DOWNLOAD
    } else {
        MobileBookOpenPreflightAction.SHOW_UNAVAILABLE
    }
}

fun SharedReaderScreenState.withMobileLibrarySearchActive(
    active: Boolean,
): SharedReaderScreenState {
    return if (active) {
        copy(isSearchActive = true)
    } else {
        copy(isSearchActive = false, searchQuery = "")
    }
}

/**
 * Android discards IME callbacks delivered after search has closed. Keeping
 * this guard shared prevents a hidden query from being restored by a late
 * native text-field event on iOS.
 */
fun SharedReaderScreenState.withMobileLibrarySearchQuery(
    query: String,
): SharedReaderScreenState {
    return if (isSearchActive) {
        reduce(LibraryAction.SearchChanged(query))
    } else {
        this
    }
}

fun planMobileImportBatch(
    files: List<ImportedBookFile>,
    existingBookIds: Set<String>,
    failedCount: Int = 0,
    nowMillis: Long = currentTimestamp(),
): SharedMobileImportBatchOutcome {
    val plan = SharedImportPlanner.plan(
        files = files,
        existingBookIds = existingBookIds,
        platform = ReaderPlatform.IOS,
        nowMillis = nowMillis,
    )
    return SharedMobileImportBatchOutcome(
        plan = plan,
        counts = plan.outcomeCounts(failedCount),
    )
}

fun SharedReaderScreenState.withAudiobookImported(
    audiobook: SharedAudiobook,
): SharedReaderScreenState {
    if (audiobook.bookId.isBlank()) return this
    if (audiobooks.any { it.bookId == audiobook.bookId && it.filePath == audiobook.filePath }) return this
    return copy(
        audiobooks = (listOf(audiobook) + audiobooks.filterNot { it.bookId == audiobook.bookId })
            .sortedByDescending { it.addedAt },
    )
}

fun SharedReaderScreenState.withAudiobookPosition(
    bookId: String,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    lastListenedAt: Long,
): SharedReaderScreenState {
    if (audiobooks.none { it.bookId == bookId }) return this
    return copy(
        audiobooks = audiobooks.map { audiobook ->
            if (audiobook.bookId == bookId) {
                audiobook.copy(
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = if (durationMs > 0L) durationMs else audiobook.durationMs,
                    playbackSpeed = if (speed > 0f) speed else audiobook.playbackSpeed,
                    lastListenedAt = lastListenedAt,
                )
            } else {
                audiobook
            }
        },
    )
}

fun SharedReaderScreenState.withAudiobookRemoved(bookId: String): SharedReaderScreenState {
    return copy(audiobooks = audiobooks.filterNot { it.bookId == bookId })
}

fun SharedReaderScreenState.withMobileImportedBooks(
    books: List<BookItem>,
    message: String? = null
): SharedMobileImportResult {
    if (books.isEmpty()) return SharedMobileImportResult(this, emptyList())

    val existingIdentities = rawLibraryBooks.mapTo(mutableSetOf()) { it.sharedLibraryIdentity() }
    val addedBooks = books
        .distinctBy { it.sharedLibraryIdentity() }
        .filterNot { it.sharedLibraryIdentity() in existingIdentities }
    if (addedBooks.isEmpty()) return SharedMobileImportResult(this, emptyList())

    val nextRawBooks = addedBooks + rawLibraryBooks
    return SharedMobileImportResult(
        state = copy(
            rawLibraryBooks = nextRawBooks,
            cloudBookTombstones = cloudBookTombstones.filterNot { tombstone ->
                val replacement = addedBooks.firstOrNull { it.id == tombstone.bookId }
                replacement != null && replacement.timestamp > tombstone.deletedAt
            },
            recentBooks = (addedBooks + recentBooks).distinctBy { it.id },
            libraryBooks = nextRawBooks,
            bannerMessage = BannerMessage(message ?: "Added ${addedBooks.size} book(s)")
        ),
        addedBooks = addedBooks
    )
}

fun SharedReaderScreenState.withMigratedMobileBookIdentity(
    oldId: String,
    newId: String,
): SharedReaderScreenState {
    if (oldId.isBlank() || newId.isBlank() || oldId == newId) return this
    if (rawLibraryBooks.none { it.id == oldId } || rawLibraryBooks.any { it.id == newId }) return this

    fun BookItem.migrated() = if (id == oldId) copy(id = newId) else this
    fun List<BookItem>.migrated() = map(BookItem::migrated)
    fun Set<String>.migrated() = mapTo(mutableSetOf()) { if (it == oldId) newId else it }
    return copy(
        rawLibraryBooks = rawLibraryBooks.migrated(),
        libraryBooks = libraryBooks.migrated(),
        recentBooks = recentBooks.migrated(),
        openTabs = openTabs.migrated(),
        selectedBookIds = selectedBookIds.migrated(),
        pinnedHomeBookIds = pinnedHomeBookIds.migrated(),
        pinnedLibraryBookIds = pinnedLibraryBookIds.migrated(),
        openTabIds = openTabIds.map { if (it == oldId) newId else it }.distinct(),
        activeTabBookId = if (activeTabBookId == oldId) newId else activeTabBookId,
        shelves = shelves.map { shelf ->
            shelf.copy(
                books = shelf.books.migrated(),
                directBooks = shelf.directBooks.migrated(),
            )
        },
        cloudBookTombstones = cloudBookTombstones.filterNot { it.bookId == oldId },
    )
}

fun SharedReaderScreenState.withMobileBookOpened(
    book: BookItem,
    openedAt: Long = currentTimestamp(),
): SharedReaderScreenState {
    val storedBook = rawLibraryBooks.firstOrNull { it.id == book.id } ?: book
    val openedBook = storedBook.copy(timestamp = openedAt, isRecent = true)
    fun List<BookItem>.withOpenedBook(): List<BookItem> =
        listOf(openedBook) + filterNot { it.id == openedBook.id }

    val openedState = copy(
        rawLibraryBooks = rawLibraryBooks.map { if (it.id == openedBook.id) openedBook else it },
        libraryBooks = libraryBooks.map { if (it.id == openedBook.id) openedBook else it },
        recentBooks = recentBooks.withOpenedBook(),
        shelves = shelves.map { shelf ->
            shelf.copy(
                books = shelf.books.map { if (it.id == openedBook.id) openedBook else it },
                directBooks = shelf.directBooks.map { if (it.id == openedBook.id) openedBook else it },
            )
        },
    )
    // Match Android: active tabs are a PDF-only affordance and opening a book must
    // not turn the feature back on after the user disabled it.
    val tabState = if (openedBook.type == FileType.PDF && openedState.isTabsEnabled) {
        openedState.withMobileBookTabOpened(openedBook.id)
    } else {
        openedState
    }
    return tabState.copy(
        selectedBookId = openedBook.id,
        selectedUriString = openedBook.path,
        selectedFileType = openedBook.type,
        bannerMessage = null
    )
}

fun resolveMobileReaderSessionBook(
    books: Collection<BookItem>,
    bookId: String?,
    fileType: FileType?,
): BookItem? {
    val normalizedBookId = bookId?.trim().orEmpty()
    if (normalizedBookId.isBlank() || fileType == null) return null
    return books.firstOrNull { book ->
        book.id == normalizedBookId &&
            book.type == fileType &&
            book.isAvailable &&
            !book.path.isNullOrBlank()
    }
}

fun SharedReaderScreenState.withRestoredMobileReaderSession(book: BookItem): SharedReaderScreenState {
    val storedBook = resolveMobileReaderSessionBook(rawLibraryBooks, book.id, book.type) ?: return this
    return copy(
        selectedBookId = storedBook.id,
        selectedUriString = storedBook.path,
        selectedFileType = storedBook.type,
        bannerMessage = null,
    )
}

fun SharedReaderScreenState.withoutMobileReaderSession(): SharedReaderScreenState {
    return copy(
        selectedBookId = null,
        selectedUriString = null,
        selectedFileType = null,
    )
}

fun SharedReaderScreenState.withRestoredMobileLibraryNavigation(
    restoredShelfId: String?,
    restoredIsAddingBooks: Boolean,
    restoredAddBooksSource: AddBooksSource,
): SharedReaderScreenState {
    val shelfId = restoredShelfId
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.takeIf { candidate -> shelves.any { it.id == candidate } }
    return copy(
        viewingShelfId = shelfId,
        isAddingBooksToShelf = restoredIsAddingBooks && shelfId != null,
        addBooksSource = if (shelfId != null) restoredAddBooksSource else AddBooksSource.UNSHELVED,
    )
}

/**
 * Opens a session-only external book without adding it to the durable library,
 * recents, shelves, or PDF tabs.
 */
fun SharedReaderScreenState.withMobileTemporaryBookOpened(book: BookItem): SharedReaderScreenState {
    return copy(
        selectedBookId = book.id,
        selectedUriString = book.path,
        selectedFileType = book.type,
        bannerMessage = null,
    )
}

fun SharedReaderScreenState.withMobileTemporaryBookClosed(bookId: String): SharedReaderScreenState {
    return copy(
        recentBooks = recentBooks.filterNot { it.id == bookId },
        openTabs = openTabs.filterNot { it.id == bookId },
        openTabIds = openTabIds.filterNot { it == bookId },
        activeTabBookId = activeTabBookId?.takeUnless { it == bookId },
        selectedBookId = selectedBookId?.takeUnless { it == bookId },
        selectedUriString = selectedUriString?.takeUnless { selectedBookId == bookId },
        selectedFileType = selectedFileType.takeUnless { selectedBookId == bookId },
    )
}

fun SharedReaderScreenState.withMobileBookTabOpened(bookId: String): SharedReaderScreenState {
    val updated = reduce(AppAction.BookTabOpened(bookId))
    return updated.copy(
        openTabs = updated.openTabIds.mapNotNull { id ->
            updated.rawLibraryBooks.firstOrNull { it.id == id }
        },
    )
}

fun SharedReaderScreenState.withMobileBookClosed(bookId: String): SharedReaderScreenState {
    val tabState = withMobileBookTabClosed(bookId)
    return if (selectedBookId == bookId) {
        tabState.copy(
            selectedBookId = tabState.activeTabBookId,
            selectedUriString = tabState.rawLibraryBooks.find { it.id == tabState.activeTabBookId }?.path,
            selectedFileType = tabState.rawLibraryBooks.find { it.id == tabState.activeTabBookId }?.type
        )
    } else {
        tabState
    }
}

fun SharedReaderScreenState.withMobileBookTabClosed(bookId: String): SharedReaderScreenState {
    val updated = reduce(AppAction.BookTabClosed(bookId))
    return updated.copy(
        openTabs = updated.openTabIds.mapNotNull { id ->
            updated.rawLibraryBooks.firstOrNull { it.id == id }
        },
    )
}
