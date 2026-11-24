package com.aryan.reader.shared

const val MAX_OPEN_TABS_PER_KIND = 20

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

/**
 * Common diagnostics tag for the iOS imported-folder scan pipeline (bookmark
 * resolution, security-scoped copy, managed-copy swap, scan consumption).
 * Swift logs with a `Logger(category:)` of the same value and forwards lines
 * through `ReaderIosBridge.logFolderScanDiagnostic`, so filtering the exported
 * diagnostics for this single tag shows the whole pipeline.
 */
const val LOCAL_FOLDER_SCAN_LOG_TAG = "LocalFolderScan"

/**
 * Common diagnostics tag for the cover pipeline, end to end: pending-extraction
 * queue -> extraction result -> cover file write -> path resolution -> library
 * render lookup. A cover that is missing on screen is indistinguishable from one
 * that was never generated unless every hand-off is logged, and the stages live
 * in different files, so they share this one tag.
 *
 * Ungated, like [LOCAL_FOLDER_SCAN_LOG_TAG]: an active cover bug should not also
 * require flipping a hidden diagnostics preference. Volume is per book and per
 * render lookup, which is bounded by what the library can show.
 */
const val LIBRARY_COVER_LOG_TAG = "LibraryCover"

data class SharedMobileFolderScanResult(
    val folderName: String,
    val files: List<SharedFolderScannedFile>,
    val succeeded: Boolean = true,
    val scanStatus: LocalFolderScanStatus = LocalFolderScanStatus.COMPLETE,
    /**
     * Native failure detail (`access_denied`, `bookmark_failed`, …). Used only
     * to pick the user-facing message; the scan itself never acts on it.
     */
    val scanDetail: String = "",
)

/**
 * Parses the scan-status raw value handed over by native code. Unknown or
 * blank values map to [LocalFolderScanStatus.PARTIAL]: a scan whose
 * completeness cannot be proven must never infer deletions.
 */
fun parseLocalFolderScanStatus(raw: String?): LocalFolderScanStatus {
    return when (raw?.trim()?.uppercase()) {
        "COMPLETE" -> LocalFolderScanStatus.COMPLETE
        "PARTIAL" -> LocalFolderScanStatus.PARTIAL
        "UNAVAILABLE" -> LocalFolderScanStatus.UNAVAILABLE
        "NOT_SCANNED" -> LocalFolderScanStatus.NOT_SCANNED
        else -> LocalFolderScanStatus.PARTIAL
    }
}

/**
 * The status the sync engine must honor. A failed scan never reconciles
 * deletions, regardless of the reported status.
 */
val SharedMobileFolderScanResult.effectiveScanStatus: LocalFolderScanStatus
    get() = if (!succeeded) LocalFolderScanStatus.UNAVAILABLE else scanStatus

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

/**
 * Banner text for a folder scan that could not be applied.
 *
 * A folder whose access grant is gone (link lost, permission refused — iOS
 * reports both through the security-scope pipeline) needs the user to re-link
 * it; telling them to try again would be a dead end. Everything else keeps the
 * existing "try again later" wording.
 */
fun mobileFolderScanFailureMessage(folderName: String, scanDetail: String): String {
    val detail = scanDetail.lowercase()
    val linkLost = LINK_FAILURE_MARKERS.any { it in detail }
    return if (linkLost) {
        "$folderName is no longer accessible. Remove the folder and add it again to restore access."
    } else {
        "Could not refresh $folderName; keeping the previous scan"
    }
}

private val LINK_FAILURE_MARKERS = listOf("access_denied", "bookmark_failed", "no_bookmark", "bookmark.reissue_failed")

/**
 * Whether a linked folder whose access grant is gone should be reported to the
 * user as needing a re-link.
 *
 * Only when the folder still holds books and none of them is readable: a
 * legacy folder still served by its app-managed copy (or one whose books were
 * all deleted) has nothing to warn about. The grant is what makes in-place
 * reading possible, so an unlinked folder with unreadable books is a dead end
 * until the user picks it again.
 */
fun mobileFolderNeedsRelink(
    isLinked: Boolean,
    bookPaths: List<String?>,
    isReadable: (String?) -> Boolean,
): Boolean = !isLinked && bookPaths.isNotEmpty() && bookPaths.none(isReadable)

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
        textOpenTabs = textOpenTabs.withoutRemoved(),
        textOpenTabIds = textOpenTabIds.filterNot { it in removedBookIds },
        activeTextTabBookId = activeTextTabBookId?.takeUnless { it in removedBookIds },
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

fun canOpenMobileTab(openTabIds: Collection<String>, bookId: String): Boolean {
    val normalizedBookId = bookId.trim()
    if (normalizedBookId.isBlank()) return false
    val currentTabIds = openTabIds.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return tabSetAccepts(currentTabIds, normalizedBookId, MAX_OPEN_TABS_PER_KIND)
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
 *
 * [localFileExists] must be a positive confirmation that the file is gone, not
 * a failure to prove it is present. A caller that cannot resolve a path (a
 * security-scoped provider ref whose scope is not currently held, for example)
 * must pass `true`: reporting `false` here removes the book from the library,
 * which is unrecoverable for a file the user still has on disk.
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

/**
 * Library "search inside files" toggle. False preserves metadata-only
 * search; hosts bulk-index on enable and clear content state on disable.
 */
fun SharedReaderScreenState.withMobileLibrarySearchInsideFiles(
    enabled: Boolean,
): SharedReaderScreenState {
    if (searchInsideFiles == enabled) return this
    return reduce(LibraryAction.SearchInsideFilesChanged(enabled))
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

/**
 * Adds a decoded audiobook to both Listen and the normal mobile library.
 * Android's importer persists these two projections in one Room transaction;
 * this shared mutation gives iOS the same all-or-nothing state transition.
 */
fun SharedReaderScreenState.withAudiobookImportedToLibrary(
    request: SharedAudiobookImportRequest,
): SharedAudiobookImportResult {
    val normalizedPath = request.filePath.trim()
    val normalizedName = request.displayName.trim()
    if (request.bookId.isBlank() || normalizedPath.isBlank() || normalizedName.isBlank()) {
        return SharedAudiobookImportResult(
            state = this,
            status = SharedAudiobookImportStatus.INVALID,
            message = "Audiobook location or name is missing",
        )
    }

    val audiobook = request.copy(
        bookId = request.bookId.trim(),
        filePath = normalizedPath,
        displayName = normalizedName,
        format = request.format.trim().ifBlank { "AUDIOBOOK" },
    ).toAudiobook()
    val normalizedIdentity = normalizedPath.normalizedAudiobookPathIdentity()
    val duplicateAudiobook = audiobooks.firstOrNull { existing ->
        existing.bookId == audiobook.bookId ||
            existing.filePath.normalizedAudiobookPathIdentity() == normalizedIdentity
    }
    val duplicateLibraryBook = rawLibraryBooks.firstOrNull { existing ->
        existing.id == audiobook.bookId ||
            existing.path?.normalizedAudiobookPathIdentity() == normalizedIdentity
    }
    if (duplicateAudiobook != null || duplicateLibraryBook != null) {
        return SharedAudiobookImportResult(
            state = this,
            status = SharedAudiobookImportStatus.DUPLICATE,
            audiobook = duplicateAudiobook,
            libraryBook = duplicateLibraryBook
                ?: duplicateAudiobook?.let { existing ->
                    rawLibraryBooks.firstOrNull { it.id == existing.bookId }
                },
            message = "This audiobook is already in the library",
        )
    }

    val libraryBook = audiobook.toSharedLibraryBook(
        displayName = normalizedName,
        fileSize = request.fileSize,
        isAvailable = request.isAvailable,
    )
    val importedState = withAudiobookImported(audiobook)
    val libraryResult = importedState.withMobileImportedBooks(listOf(libraryBook))
    if (libraryResult.addedBooks.isEmpty()) {
        return SharedAudiobookImportResult(
            state = this,
            status = SharedAudiobookImportStatus.DUPLICATE,
            audiobook = audiobook,
            libraryBook = rawLibraryBooks.firstOrNull { it.id == audiobook.bookId },
            message = "This audiobook is already in the library",
        )
    }
    return SharedAudiobookImportResult(
        state = libraryResult.state,
        status = SharedAudiobookImportStatus.ADDED,
        audiobook = audiobook,
        libraryBook = libraryResult.addedBooks.single(),
    )
}

private fun String.normalizedAudiobookPathIdentity(): String =
    trim().removePrefix("file://").removePrefix("/private")

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
    // Active tabs belong to the reader that will show them, and opening a book must not turn the
    // feature back on after the user disabled it.
    val tabKind = openedBook.readerTabKind()
    val tabState = if (tabKind != null && openedState.isTabsEnabled) {
        openedState.withMobileBookTabOpened(tabKind, openedBook.id)
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
        textOpenTabs = textOpenTabs.filterNot { it.id == bookId },
        textOpenTabIds = textOpenTabIds.filterNot { it == bookId },
        activeTextTabBookId = activeTextTabBookId?.takeUnless { it == bookId },
        selectedBookId = selectedBookId?.takeUnless { it == bookId },
        selectedUriString = selectedUriString?.takeUnless { selectedBookId == bookId },
        selectedFileType = selectedFileType.takeUnless { selectedBookId == bookId },
    )
}

fun SharedReaderScreenState.withMobileBookTabOpened(
    kind: ReaderTabKind,
    bookId: String,
): SharedReaderScreenState = reduce(AppAction.BookTabOpened(kind, bookId)).withRecomputedTabBooks()

/**
 * Closes one tab in [kind] and, when that tab was the reader's active one, moves the reader to the
 * next tab of the **same** kind. Closing a tab of the other kind never touches what is on screen.
 */
fun SharedReaderScreenState.withMobileBookClosed(
    kind: ReaderTabKind,
    bookId: String,
): SharedReaderScreenState {
    val tabState = withMobileBookTabClosed(kind, bookId)
    if (selectedBookId != bookId) return tabState
    val nextBook = tabState.activeTabFor(kind)?.let { nextId ->
        tabState.rawLibraryBooks.firstOrNull { it.id == nextId }
    }
    return tabState.copy(
        selectedBookId = nextBook?.id,
        selectedUriString = nextBook?.path,
        selectedFileType = nextBook?.type,
    )
}

fun SharedReaderScreenState.withMobileBookTabClosed(
    kind: ReaderTabKind,
    bookId: String,
): SharedReaderScreenState = reduce(AppAction.BookTabClosed(kind, bookId)).withRecomputedTabBooks()

fun SharedReaderScreenState.withMobileBookTabsClosed(kind: ReaderTabKind): SharedReaderScreenState =
    reduce(AppAction.BookTabsClosed(kind)).withRecomputedTabBooks()

/**
 * Reorders [kind]'s tabs, which is also the order the reader's tab strip shows. Moving a tab to
 * where [targetBookId] sits keeps the readers' active tabs untouched.
 */
fun SharedReaderScreenState.withMobileTabMoved(
    kind: ReaderTabKind,
    bookId: String,
    targetBookId: String,
): SharedReaderScreenState =
    reduce(AppAction.BookTabMoved(kind, bookId, targetBookId)).withRecomputedTabBooks()

/**
 * Disabling tabs parks each set on its active tab instead of forgetting it, so re-enabling
 * restores both readers. The shared reducer owns the rule; this wrapper only keeps the derived
 * book lists in step.
 */
fun SharedReaderScreenState.withMobileTabsEnabled(enabled: Boolean): SharedReaderScreenState =
    reduce(AppAction.TabsEnabledChanged(enabled)).withRecomputedTabBooks()

/**
 * Drops tab ids that no longer exist in the library and enforces [MAX_OPEN_TABS_PER_KIND] per set,
 * mirroring Android's `reconcileTabState` before every tab open/switch/close. Passive pruning only:
 * the reader selection is left to the explicit close/remove paths.
 */
fun SharedReaderScreenState.withReconciledMobileTabs(
    maxOpenTabs: Int = MAX_OPEN_TABS_PER_KIND,
): SharedReaderScreenState {
    val availableIds = rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
    val fixedLayout = AppTabGroup(openTabIds, activeTabBookId).reconciled(availableIds, maxOpenTabs)
    val text = AppTabGroup(textOpenTabIds, activeTextTabBookId).reconciled(availableIds, maxOpenTabs)
    if (
        fixedLayout.openBookIds == openTabIds &&
        fixedLayout.activeBookId == activeTabBookId &&
        text.openBookIds == textOpenTabIds &&
        text.activeBookId == activeTextTabBookId
    ) {
        return this
    }
    return copy(
        openTabIds = fixedLayout.openBookIds,
        activeTabBookId = fixedLayout.activeBookId,
        textOpenTabIds = text.openBookIds,
        activeTextTabBookId = text.activeBookId,
    ).withRecomputedTabBooks()
}

/** Rebuilds both derived tab book lists from their ids after a tab-state change. */
private fun SharedReaderScreenState.withRecomputedTabBooks(): SharedReaderScreenState = copy(
    openTabs = openTabIds.mapNotNull { id -> rawLibraryBooks.firstOrNull { it.id == id } },
    textOpenTabs = textOpenTabIds.mapNotNull { id -> rawLibraryBooks.firstOrNull { it.id == id } },
)

/** The tab [kind]'s reader is showing, which is not always the book the app is showing. */
fun SharedReaderScreenState.activeTabFor(kind: ReaderTabKind): String? = when (kind) {
    ReaderTabKind.FIXED_LAYOUT -> activeTabBookId
    ReaderTabKind.TEXT -> activeTextTabBookId
}

fun SharedReaderScreenState.openTabIdsFor(kind: ReaderTabKind): List<String> = when (kind) {
    ReaderTabKind.FIXED_LAYOUT -> openTabIds
    ReaderTabKind.TEXT -> textOpenTabIds
}
