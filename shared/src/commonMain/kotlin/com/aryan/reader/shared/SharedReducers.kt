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
        is LibraryAction.ShelfSelectionToggled -> copy(
            selectedShelfIds = if (action.shelfId in selectedShelfIds) {
                selectedShelfIds - action.shelfId
            } else {
                selectedShelfIds + action.shelfId
            },
        )
        LibraryAction.ShelfSelectionCleared -> copy(selectedShelfIds = emptySet())
        is LibraryAction.LibraryPageChanged -> copy(libraryPage = action.page)
        is LibraryAction.RecentLimitChanged -> copy(recentLimit = action.limit)
    }
}

fun AppReaderSessionState.reduce(action: AppReaderSessionAction): AppReaderSessionState = when (action) {
    is AppReaderSessionAction.OpenStarted -> AppReaderSessionState(
        bookId = action.bookId,
        fileType = action.fileType,
        phase = AppReaderSessionPhase.OPENING,
    )
    is AppReaderSessionAction.OpenReady -> if (bookId == action.bookId && hasActiveReader) {
        copy(phase = AppReaderSessionPhase.READY, errorMessage = null)
    } else {
        this
    }
    is AppReaderSessionAction.OpenFailed -> if (bookId == action.bookId && hasActiveReader) {
        if (action.closeReader) {
            AppReaderSessionState(
                phase = AppReaderSessionPhase.FAILED,
                errorMessage = action.message,
            )
        } else {
            copy(phase = AppReaderSessionPhase.FAILED, errorMessage = action.message)
        }
    } else {
        this
    }
    AppReaderSessionAction.SeamlessSwitchFailed -> copy(fileType = null)
    AppReaderSessionAction.Closed -> AppReaderSessionState()
}

fun AppShelfState.reduce(action: AppShelfAction): AppShelfState = when (action) {
    is AppShelfAction.CreateDialogShown -> copy(
        showCreateDialog = true,
        createShelfBookIds = SharedLibraryEditor.cleanBookIds(action.bookIds),
    )
    AppShelfAction.CreateDialogDismissed -> copy(
        showCreateDialog = false,
        createShelfBookIds = emptySet(),
    )
    is AppShelfAction.ShelfOpened -> copy(viewingShelfId = action.shelfId)
    is AppShelfAction.RenameDialogChanged -> copy(renameDialogShelfId = action.shelfId)
    is AppShelfAction.DeleteDialogChanged -> copy(deleteDialogShelfId = action.shelfId)
    is AppShelfAction.ShelfRenameCompleted -> copy(
        viewingShelfId = action.shelfId,
        renameDialogShelfId = null,
    )
    AppShelfAction.ShelfDeleted -> copy(
        viewingShelfId = null,
        isAddingBooks = false,
        deleteDialogShelfId = null,
    )
    AppShelfAction.ShelfClosed -> copy(viewingShelfId = null, isAddingBooks = false)
    is AppShelfAction.ParentShelfOpened -> copy(
        viewingShelfId = action.shelfId,
        isAddingBooks = false,
    )
    is AppShelfAction.AddBooksStarted -> copy(
        isAddingBooks = true,
        addBooksSource = action.source,
        selectedBookIdsForAdding = emptySet(),
    )
    AppShelfAction.AddBooksDismissed -> copy(
        isAddingBooks = false,
        selectedBookIdsForAdding = emptySet(),
        addBooksSource = AddBooksSource.UNSHELVED,
    )
    AppShelfAction.AddBooksCompleted -> copy(
        isAddingBooks = false,
        selectedBookIdsForAdding = emptySet(),
    )
    is AppShelfAction.AddBooksSourceChanged -> copy(addBooksSource = action.source)
    is AppShelfAction.BookForAddingToggled -> {
        val bookId = action.bookId.trim()
        if (bookId.isBlank()) this else copy(
            selectedBookIdsForAdding = if (bookId in selectedBookIdsForAdding) {
                selectedBookIdsForAdding - bookId
            } else {
                selectedBookIdsForAdding + bookId
            },
        )
    }
}

fun SharedReaderScreenState.reduce(action: LibraryAction): SharedReaderScreenState {
    val reduced = LibraryState(
        searchQuery = searchQuery,
        sortOrder = sortOrder,
        filters = libraryFilters,
        selectedBookIds = selectedBookIds,
        selectedShelfIds = selectedShelfIds,
        libraryPage = libraryScreenStartPage,
        recentLimit = recentFilesLimit,
    ).reduce(action)
    return copy(
        searchQuery = reduced.searchQuery,
        sortOrder = reduced.sortOrder,
        libraryFilters = reduced.filters,
        selectedBookIds = reduced.selectedBookIds,
        selectedShelfIds = reduced.selectedShelfIds,
        libraryScreenStartPage = reduced.libraryPage,
        recentFilesLimit = reduced.recentLimit,
    )
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
        is AppAction.AppThemeChanged,
        is AppAction.AppContrastChanged,
        is AppAction.AppTextDimFactorLightChanged,
        is AppAction.AppTextDimFactorDarkChanged,
        is AppAction.AppSeedColorChanged,
        is AppAction.AppFontPreferenceChanged,
        is AppAction.CustomAppThemeAdded,
        is AppAction.CustomAppThemeDeleted -> withAppearance(appAppearance().reduce(action))
        is AppAction.CustomReaderThemesChanged -> copy(
            customReaderThemes = action.themes.sanitizeCustomReaderThemes()
        )
        is AppAction.CustomFontsChanged -> copy(customFonts = action.fonts)
        is AppAction.SyncEnabledChanged -> copy(isSyncEnabled = action.enabled)
        is AppAction.FolderSyncEnabledChanged -> copy(isFolderSyncEnabled = action.enabled)
        is AppAction.TabsEnabledChanged,
        is AppAction.BookTabOpened,
        is AppAction.BookTabClosed,
        AppAction.AllTabsClosed -> withTabState(
            state = tabState().reduce(action),
            clearOpenTabs = action == AppAction.AllTabsClosed ||
                (action is AppAction.TabsEnabledChanged && !action.enabled),
        )
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
        is AppAction.PdfHighlighterSnapChanged -> copy(
            pdfHighlighterSnapEnabled = action.enabled
        )
        is AppAction.ReaderTtsReplacementPreferencesChanged -> copy(
            readerTtsReplacementPreferences = action.preferences
        )
        is AppAction.ReaderBookReplacementPreferencesChanged -> copy(
            readerBookReplacementPreferences = action.preferences
        )
    }
}

fun AppTabState.reduce(action: AppAction): AppTabState = when (action) {
    is AppAction.TabsEnabledChanged -> copy(
        isEnabled = action.enabled,
        openBookIds = if (action.enabled) openBookIds else emptyList(),
        activeBookId = if (action.enabled) activeBookId else null,
    )
    is AppAction.BookTabOpened -> {
        val bookId = action.bookId.trim()
        if (bookId.isBlank()) this else copy(
            isEnabled = true,
            openBookIds = openBookIds.distinct().let { if (bookId in it) it else it + bookId },
            activeBookId = bookId,
        )
    }
    is AppAction.BookTabClosed -> {
        val remaining = openBookIds.filterNot { it == action.bookId }
        copy(
            openBookIds = remaining,
            activeBookId = if (activeBookId == action.bookId) remaining.lastOrNull() else activeBookId,
        )
    }
    AppAction.AllTabsClosed -> copy(openBookIds = emptyList(), activeBookId = null)
    else -> this
}

fun AppPinState.toggleSelection(selectedBookIds: Collection<String>, isHome: Boolean): AppPinState {
    val selectedIds = selectedBookIds.mapNotNullTo(linkedSetOf()) { id ->
        id.trim().takeIf(String::isNotEmpty)
    }
    if (selectedIds.isEmpty()) return this
    val current = if (isHome) homeBookIds else libraryBookIds
    val updated = if (selectedIds.all(current::contains)) {
        current - selectedIds
    } else {
        current + selectedIds
    }
    return if (isHome) copy(homeBookIds = updated) else copy(libraryBookIds = updated)
}

fun AppTabState.reconcileAvailableBooks(
    availableBookIds: Set<String>,
    maxOpenTabs: Int = MAX_OPEN_PDF_TABS,
): AppTabState {
    val reconciledOpenIds = openBookIds
        .distinct()
        .filter(availableBookIds::contains)
        .take(maxOpenTabs.coerceAtLeast(0))
    return copy(
        openBookIds = reconciledOpenIds,
        activeBookId = activeBookId?.takeIf(reconciledOpenIds::contains),
    )
}

private fun SharedReaderScreenState.tabState() = AppTabState(
    isEnabled = isTabsEnabled,
    openBookIds = openTabIds,
    activeBookId = activeTabBookId,
)

private fun SharedReaderScreenState.withTabState(state: AppTabState, clearOpenTabs: Boolean) = copy(
    isTabsEnabled = state.isEnabled,
    openTabIds = state.openBookIds,
    activeTabBookId = state.activeBookId,
    openTabs = if (clearOpenTabs) emptyList() else openTabs,
)

private fun SharedReaderScreenState.appAppearance() = AppAppearanceState(
    themeMode = appThemeMode,
    contrastOption = appContrastOption,
    textDimFactorLight = appTextDimFactorLight,
    textDimFactorDark = appTextDimFactorDark,
    seedColor = appSeedColor,
    fontPreference = appFontPreference,
    customThemes = customAppThemes,
)

private fun SharedReaderScreenState.withAppearance(appearance: AppAppearanceState) = copy(
    appThemeMode = appearance.themeMode,
    appContrastOption = appearance.contrastOption,
    appTextDimFactorLight = appearance.textDimFactorLight,
    appTextDimFactorDark = appearance.textDimFactorDark,
    appSeedColor = appearance.seedColor,
    appFontPreference = appearance.fontPreference,
    customAppThemes = appearance.customThemes,
)

fun AppAppearanceState.reduce(action: AppAction): AppAppearanceState {
    return when (action) {
        is AppAction.AppThemeChanged -> copy(themeMode = action.mode)
        is AppAction.AppContrastChanged -> copy(contrastOption = action.option)
        is AppAction.AppTextDimFactorLightChanged -> copy(
            textDimFactorLight = action.factor.coerceIn(0.3f, 1.0f),
        )
        is AppAction.AppTextDimFactorDarkChanged -> copy(
            textDimFactorDark = action.factor.coerceIn(0.3f, 1.0f),
        )
        is AppAction.AppSeedColorChanged -> copy(seedColor = action.color)
        is AppAction.AppFontPreferenceChanged -> copy(fontPreference = action.preference.sanitized())
        is AppAction.CustomAppThemeAdded -> copy(
            customThemes = customThemes.filterNot { it.id == action.theme.id } + action.theme,
            seedColor = action.theme.seedColor,
        )
        is AppAction.CustomAppThemeDeleted -> {
            val updatedThemes = customThemes.filterNot { it.id == action.themeId }
            val shouldClearSeed = seedColor != null && updatedThemes.none { it.seedColor == seedColor }
            copy(customThemes = updatedThemes, seedColor = if (shouldClearSeed) null else seedColor)
        }
        else -> this
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
            note = action.note,
            style = action.style
        )
        is ReaderAction.HighlightDeleted -> readerEngine.deleteHighlight(this, action.highlightId)
    }
}
