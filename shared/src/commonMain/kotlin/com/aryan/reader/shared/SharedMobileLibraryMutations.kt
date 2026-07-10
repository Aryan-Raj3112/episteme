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

    val existingIds = rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
    val addedBooks = books
        .distinctBy { it.id }
        .filterNot { it.id in existingIds }
    if (addedBooks.isEmpty()) return SharedMobileImportResult(this, emptyList())

    val nextRawBooks = addedBooks + rawLibraryBooks
    return SharedMobileImportResult(
        state = copy(
            rawLibraryBooks = nextRawBooks,
            recentBooks = (addedBooks + recentBooks).distinctBy { it.id },
            libraryBooks = nextRawBooks,
            bannerMessage = BannerMessage(message ?: "Added ${addedBooks.size} book(s)")
        ),
        addedBooks = addedBooks
    )
}

fun SharedReaderScreenState.withMobileBookOpened(book: BookItem): SharedReaderScreenState {
    val tabState = withMobileBookTabOpened(book.id)
    return tabState.copy(
        selectedBookId = book.id,
        selectedUriString = book.path,
        selectedFileType = book.type,
        bannerMessage = null
    )
}

fun SharedReaderScreenState.withMobileBookTabOpened(bookId: String): SharedReaderScreenState {
    return reduce(AppAction.BookTabOpened(bookId))
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
    return reduce(AppAction.BookTabClosed(bookId))
}
