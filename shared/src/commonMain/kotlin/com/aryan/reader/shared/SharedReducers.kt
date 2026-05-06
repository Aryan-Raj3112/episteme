package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderSessionState

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
        is AppAction.TabsEnabledChanged -> copy(
            isTabsEnabled = action.enabled,
            openTabIds = if (action.enabled) openTabIds else emptyList(),
            activeTabBookId = if (action.enabled) activeTabBookId else null
        )
        is AppAction.BookTabOpened -> {
            val bookId = action.bookId.trim()
            if (bookId.isBlank()) {
                this
            } else {
                copy(
                    isTabsEnabled = true,
                    openTabIds = (openTabIds - bookId) + bookId,
                    activeTabBookId = bookId
                )
            }
        }
        is AppAction.BookTabClosed -> {
            val remaining = openTabIds.filterNot { it == action.bookId }
            copy(
                openTabIds = remaining,
                activeTabBookId = if (activeTabBookId == action.bookId) remaining.lastOrNull() else activeTabBookId
            )
        }
        AppAction.AllTabsClosed -> copy(openTabIds = emptyList(), activeTabBookId = null)
        is AppAction.HomePinToggled -> copy(
            pinnedHomeBookIds = if (action.bookId in pinnedHomeBookIds) {
                pinnedHomeBookIds - action.bookId
            } else {
                pinnedHomeBookIds + action.bookId
            }
        )
        is AppAction.LibraryPinToggled -> copy(
            pinnedLibraryBookIds = if (action.bookId in pinnedLibraryBookIds) {
                pinnedLibraryBookIds - action.bookId
            } else {
                pinnedLibraryBookIds + action.bookId
            }
        )
        is AppAction.ReaderToolbarPreferencesChanged -> copy(
            readerToolbarPreferences = action.preferences.sanitized()
        )
        is AppAction.ReaderToolVisibilityChanged -> copy(
            readerToolbarPreferences = readerToolbarPreferences.withVisibility(action.tool, action.hidden)
        )
        is AppAction.ReaderToolPlacementChanged -> copy(
            readerToolbarPreferences = readerToolbarPreferences.withBottomPlacement(action.tool, action.bottom)
        )
        is AppAction.ReaderToolOrderChanged -> copy(
            readerToolbarPreferences = readerToolbarPreferences.withToolOrder(action.toolOrder)
        )
    }
}

fun ReaderSessionState.reduce(action: ReaderAction, readerEngine: ReaderEngine): ReaderSessionState {
    return when (action) {
        ReaderAction.NextPage -> readerEngine.next(this)
        ReaderAction.PreviousPage -> readerEngine.previous(this)
        is ReaderAction.GoToPage -> readerEngine.goToPage(this, action.pageIndex)
        is ReaderAction.GoToProgress -> readerEngine.goToProgress(this, action.progress)
        is ReaderAction.GoToChapter -> readerEngine.goToChapter(this, action.chapterIndex)
        is ReaderAction.GoToSearchResult -> readerEngine.goToSearchResult(this, action.resultIndex)
        is ReaderAction.SearchChanged -> readerEngine.search(this, action.query)
        ReaderAction.NextSearchResult -> readerEngine.nextSearchResult(this)
        ReaderAction.PreviousSearchResult -> readerEngine.previousSearchResult(this)
        ReaderAction.ToggleBookmark -> readerEngine.toggleBookmark(this)
        is ReaderAction.SettingsChanged -> readerEngine.updateSettings(this, action.settings)
        is ReaderAction.RenderModeChanged -> readerEngine.updateSettings(
            this,
            reader.settings.copy(readingMode = action.renderMode.toReaderReadingMode())
        )
        is ReaderAction.ThemeChanged -> readerEngine.updateSettings(this, action.theme.toReaderSettings(reader.settings))
        is ReaderAction.FormatChanged -> readerEngine.updateSettings(this, action.settings.toReaderSettings(reader.settings))
    }
}
