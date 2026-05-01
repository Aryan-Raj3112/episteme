package com.aryan.reader.shared

fun LibraryState.reduce(action: LibraryAction): LibraryState {
    return when (action) {
        is LibraryAction.SearchChanged -> copy(searchQuery = action.query)
        is LibraryAction.SortChanged -> copy(sortOrder = action.sortOrder)
        is LibraryAction.FiltersChanged -> copy(filters = action.filters)
        is LibraryAction.BookSelectionToggled -> {
            val selected = if (action.bookId in selectedBookIds) {
                selectedBookIds - action.bookId
            } else {
                selectedBookIds + action.bookId
            }
            copy(selectedBookIds = selected)
        }
        LibraryAction.SelectionCleared -> copy(selectedBookIds = emptySet())
        is LibraryAction.ShelfSelectionToggled -> this
        LibraryAction.ShelfSelectionCleared -> this
        is LibraryAction.LibraryPageChanged -> this
        is LibraryAction.RecentLimitChanged -> copy(recentLimit = action.limit)
    }
}

fun SharedReaderScreenState.reduce(action: LibraryAction): SharedReaderScreenState {
    return when (action) {
        is LibraryAction.SearchChanged -> copy(searchQuery = action.query)
        is LibraryAction.SortChanged -> copy(sortOrder = action.sortOrder)
        is LibraryAction.FiltersChanged -> copy(libraryFilters = action.filters)
        is LibraryAction.BookSelectionToggled -> {
            val selected = if (action.bookId in selectedBookIds) {
                selectedBookIds - action.bookId
            } else {
                selectedBookIds + action.bookId
            }
            copy(selectedBookIds = selected)
        }
        LibraryAction.SelectionCleared -> copy(selectedBookIds = emptySet())
        is LibraryAction.ShelfSelectionToggled -> {
            val selected = if (action.shelfId in selectedShelfIds) {
                selectedShelfIds - action.shelfId
            } else {
                selectedShelfIds + action.shelfId
            }
            copy(selectedShelfIds = selected)
        }
        LibraryAction.ShelfSelectionCleared -> copy(selectedShelfIds = emptySet())
        is LibraryAction.LibraryPageChanged -> copy(libraryScreenStartPage = action.page)
        is LibraryAction.RecentLimitChanged -> copy(recentFilesLimit = action.limit)
    }
}

fun SharedReaderScreenState.reduce(action: AppAction): SharedReaderScreenState {
    return when (action) {
        is AppAction.BannerShown -> copy(bannerMessage = action.message)
        AppAction.BannerDismissed -> copy(bannerMessage = null)
        is AppAction.NavigationRequested -> this
        is AppAction.AppThemeChanged -> copy(appThemeMode = action.mode)
        is AppAction.AppContrastChanged -> copy(appContrastOption = action.option)
        is AppAction.SyncEnabledChanged -> copy(isSyncEnabled = action.enabled)
        is AppAction.FolderSyncEnabledChanged -> copy(isFolderSyncEnabled = action.enabled)
    }
}
