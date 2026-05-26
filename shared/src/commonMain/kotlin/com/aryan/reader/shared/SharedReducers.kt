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
        is LibraryAction.BookSelectionReplaced -> copy(selectedBookIds = action.bookIds)
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
        is LibraryAction.BookSelectionReplaced -> copy(selectedBookIds = action.bookIds)
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

fun SharedReaderScreenState.replaceBookSelectionWithVisibleBooks(
    visibleBooks: Collection<BookItem>
): SharedReaderScreenState {
    val visibleIds = visibleBooks.mapTo(linkedSetOf()) { it.id }
    val action = if (visibleIds.isNotEmpty() && selectedBookIds.containsAll(visibleIds)) {
        LibraryAction.SelectionCleared
    } else {
        LibraryAction.BookSelectionReplaced(visibleIds)
    }
    return reduce(action)
}

fun SharedReaderScreenState.reduce(action: AppAction): SharedReaderScreenState {
    return when (action) {
        is AppAction.BannerShown -> copy(bannerMessage = action.message)
        AppAction.BannerDismissed -> copy(bannerMessage = null)
        is AppAction.NavigationRequested -> this
        is AppAction.AppThemeChanged -> copy(appThemeMode = action.mode)
        is AppAction.AppContrastChanged -> copy(appContrastOption = action.option)
        is AppAction.AppTextDimFactorLightChanged -> copy(appTextDimFactorLight = action.factor.coerceIn(0.3f, 1.0f))
        is AppAction.AppTextDimFactorDarkChanged -> copy(appTextDimFactorDark = action.factor.coerceIn(0.3f, 1.0f))
        is AppAction.AppSeedColorChanged -> copy(appSeedColor = action.color)
        is AppAction.AppFontPreferenceChanged -> copy(appFontPreference = action.preference.sanitized())
        is AppAction.CustomAppThemeAdded -> {
            val updatedThemes = customAppThemes.filterNot { it.id == action.theme.id } + action.theme
            copy(customAppThemes = updatedThemes, appSeedColor = action.theme.seedColor)
        }
        is AppAction.CustomAppThemeDeleted -> {
            val updatedThemes = customAppThemes.filterNot { it.id == action.themeId }
            val shouldClearSeed = appSeedColor != null && updatedThemes.none { it.seedColor == appSeedColor }
            copy(
                customAppThemes = updatedThemes,
                appSeedColor = if (shouldClearSeed) null else appSeedColor
            )
        }
        is AppAction.CustomReaderThemesChanged -> copy(
            customReaderThemes = action.themes.sanitizeCustomReaderThemes()
        )
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
                val currentTabIds = openTabIds.distinct()
                val nextTabIds = if (bookId in currentTabIds) currentTabIds else currentTabIds + bookId
                copy(
                    isTabsEnabled = true,
                    openTabIds = nextTabIds,
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
        is AppAction.ReaderDefaultSettingsChanged -> copy(
            readerDefaultSettings = action.settings
        )
        is AppAction.PdfReaderDefaultSettingsChanged -> copy(
            pdfReaderDefaultSettings = action.settings
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
        is AppAction.ReaderHighlightPaletteChanged -> copy(
            readerHighlightPalette = action.palette.sanitized()
        )
        is AppAction.PdfHighlighterPaletteChanged -> copy(
            pdfHighlighterPalette = action.palette.sanitized()
        )
        is AppAction.ReaderTtsReplacementPreferencesChanged -> copy(
            readerTtsReplacementPreferences = action.preferences
        )
    }
}

fun ReaderSessionState.reduce(action: ReaderAction, readerEngine: ReaderEngine): ReaderSessionState {
    return when (action) {
        ReaderAction.NextPage -> readerEngine.next(this)
        ReaderAction.PreviousPage -> readerEngine.previous(this)
        is ReaderAction.GoToPage -> readerEngine.goToPage(this, action.pageIndex)
        is ReaderAction.GoToPageNumber -> readerEngine.goToPageNumber(this, action.pageNumber)
        is ReaderAction.GoToProgress -> readerEngine.goToProgress(this, action.progress)
        is ReaderAction.GoToChapter -> readerEngine.goToChapter(this, action.chapterIndex)
        is ReaderAction.GoToLocator -> readerEngine.goToLocator(this, action.locator)
        is ReaderAction.JumpToPage -> readerEngine.jumpToPage(this, action.pageIndex)
        is ReaderAction.JumpToPageNumber -> readerEngine.jumpToPageNumber(this, action.pageNumber)
        is ReaderAction.JumpToChapter -> readerEngine.jumpToChapter(this, action.chapterIndex)
        is ReaderAction.JumpToLocator -> readerEngine.jumpToLocator(this, action.locator)
        is ReaderAction.VisiblePageChanged -> readerEngine.syncVisiblePage(this, action.pageIndex, action.locator)
        is ReaderAction.GoToSearchResult -> readerEngine.goToSearchResult(this, action.resultIndex)
        is ReaderAction.JumpToSearchResult -> readerEngine.jumpToSearchResult(this, action.resultIndex)
        ReaderAction.JumpToNextSearchResult -> readerEngine.jumpToNextSearchResult(this)
        ReaderAction.JumpToPreviousSearchResult -> readerEngine.jumpToPreviousSearchResult(this)
        ReaderAction.JumpBack -> readerEngine.jumpBack(this)
        ReaderAction.JumpForward -> readerEngine.jumpForward(this)
        ReaderAction.JumpHistoryCleared -> readerEngine.clearJumpHistory(this)
        is ReaderAction.SearchChanged -> readerEngine.search(this, action.query)
        ReaderAction.SearchOpened -> readerEngine.openSearch(this)
        ReaderAction.SearchClosed -> readerEngine.closeSearch(this)
        ReaderAction.SearchResultsPanelToggled -> readerEngine.toggleSearchResultsPanel(this)
        is ReaderAction.SearchOptionsChanged -> readerEngine.updateSearchOptions(this, action.options)
        ReaderAction.NextSearchResult -> readerEngine.nextSearchResult(this)
        ReaderAction.PreviousSearchResult -> readerEngine.previousSearchResult(this)
        ReaderAction.ToggleBookmark -> readerEngine.toggleBookmark(this)
        is ReaderAction.ToggleBookmarkAtLocator -> readerEngine.toggleBookmarkAtLocator(
            state = this,
            locator = action.locator,
            chapterTitle = action.title,
            preview = action.preview
        )
        is ReaderAction.SettingsChanged -> readerEngine.updateSettings(this, action.settings)
        is ReaderAction.RenderModeChanged -> readerEngine.updateSettings(
            this,
            reader.settings.copy(readingMode = action.renderMode.toReaderReadingMode())
        )
        is ReaderAction.ThemeChanged -> readerEngine.updateSettings(this, action.theme.toReaderSettings(reader.settings))
        is ReaderAction.FormatChanged -> readerEngine.updateSettings(this, action.settings.toReaderSettings(reader.settings))
        is ReaderAction.HighlightCreated -> readerEngine.upsertHighlight(this, action.highlight)
        is ReaderAction.HighlightUpdated -> readerEngine.updateHighlight(
            state = this,
            highlightId = action.highlightId,
            color = action.color,
            note = action.note
        )
        is ReaderAction.HighlightDeleted -> readerEngine.deleteHighlight(this, action.highlightId)
    }
}
