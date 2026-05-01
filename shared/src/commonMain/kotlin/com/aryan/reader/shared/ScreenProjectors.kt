package com.aryan.reader.shared

data class SharedHomeScreenModel(
    val recentBooks: List<BookItem>,
    val openTabs: List<BookItem>,
    val selectedBooks: List<BookItem>,
    val isContextualModeActive: Boolean,
    val deviceLimitState: DeviceLimitReachedState,
    val isEmpty: Boolean,
    val isLibraryEmpty: Boolean
)

fun SharedReaderScreenState.toHomeScreenModel(): SharedHomeScreenModel {
    val homeRecentBooks = recentBooks.filter { it.isRecent }
    return SharedHomeScreenModel(
        recentBooks = homeRecentBooks,
        openTabs = openTabs,
        selectedBooks = rawLibraryBooks.filter { it.id in selectedBookIds },
        isContextualModeActive = selectedBookIds.isNotEmpty(),
        deviceLimitState = deviceLimitState,
        isEmpty = homeRecentBooks.isEmpty() && (!isTabsEnabled || openTabs.isEmpty()),
        isLibraryEmpty = rawLibraryBooks.isEmpty()
    )
}

data class SharedLibraryScreenModel(
    val selectedBooks: List<BookItem>,
    val isContextualModeActive: Boolean,
    val selectedShelves: Set<String>,
    val isShelfContextualModeActive: Boolean,
    val sortOrder: SortOrder,
    val shelves: List<Shelf>,
    val rawLibraryBooks: List<BookItem>,
    val containsFolderItemsInSelection: Boolean,
    val isSearchActive: Boolean,
    val searchQuery: String
)

fun SharedReaderScreenState.toLibraryScreenModel(): SharedLibraryScreenModel {
    val selected = rawLibraryBooks.filter { it.id in selectedBookIds }
    return SharedLibraryScreenModel(
        selectedBooks = selected,
        isContextualModeActive = selectedBookIds.isNotEmpty(),
        selectedShelves = selectedShelfIds,
        isShelfContextualModeActive = selectedShelfIds.isNotEmpty(),
        sortOrder = sortOrder,
        shelves = shelves,
        rawLibraryBooks = rawLibraryBooks,
        containsFolderItemsInSelection = selected.any { it.sourceFolder != null },
        isSearchActive = isSearchActive,
        searchQuery = searchQuery
    )
}
