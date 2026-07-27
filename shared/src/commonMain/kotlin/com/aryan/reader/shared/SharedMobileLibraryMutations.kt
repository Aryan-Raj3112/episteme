package com.aryan.reader.shared

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
