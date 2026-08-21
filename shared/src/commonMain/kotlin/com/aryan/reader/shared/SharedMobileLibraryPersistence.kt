package com.aryan.reader.shared

/**
 * Converts a persisted library snapshot into the state consumed by the shared
 * mobile home and library screens.
 */
fun SharedLibrarySnapshot.toSharedMobileReaderState(): SharedReaderScreenState {
    val base = SharedReaderScreenState(
        syncedFolders = syncedFolders,
        audiobooks = audiobooks,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        isFolderSyncEnabled = isFolderSyncEnabled,
        sortOrder = sortOrder,
        libraryFilters = libraryFilters,
        mainScreenStartPage = mainScreenStartPage,
        libraryScreenStartPage = libraryScreenStartPage,
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        useStrictFileFilter = useStrictFileFilter,
        externalFileBehavior = externalFileBehavior,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
        hideReaderAi = hideReaderAi,
        appLanguageTag = appLanguageTag,
        appThemeMode = appThemeMode,
        appContrastOption = appContrastOption,
        appTextDimFactorLight = appTextDimFactorLight,
        appTextDimFactorDark = appTextDimFactorDark,
        appSeedColor = appSeedColor,
        appFontPreference = appFontPreference,
        customAppThemes = customAppThemes,
        customReaderThemes = customReaderThemes,
        customFonts = customFonts,
        readerDefaultSettings = readerDefaultSettings,
        pdfReaderDefaultSettings = pdfReaderDefaultSettings,
        allTags = tags,
        readerToolbarPreferences = readerToolbarPreferences,
        readerHighlightPalette = readerHighlightPalette,
        pdfHighlighterPalette = pdfHighlighterPalette,
        readerTtsReplacementPreferences = readerTtsReplacementPreferences,
        readerBookReplacementPreferences = readerBookReplacementPreferences,
        cloudBookTombstones = bookTombstones,
    )
    return SharedLibraryStateProjector().project(
        SharedLibraryProjectionInput(
            state = base.toLibraryFeatureState(),
            booksFromStore = books,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            tags = tags,
        )
    ).let(base::withLibraryFeatureState)
}

private fun SharedReaderScreenState.toLibraryFeatureState() = LibraryFeatureState(
    sortOrder = sortOrder,
    filters = libraryFilters,
    syncedFolders = syncedFolders,
    pinnedHomeBookIds = pinnedHomeBookIds,
    pinnedLibraryBookIds = pinnedLibraryBookIds,
    recentLimit = recentFilesLimit,
    tabs = AppTabState(isTabsEnabled, openTabIds, activeTabBookId),
)

private fun SharedReaderScreenState.withLibraryFeatureState(feature: LibraryFeatureState) = copy(
    sortOrder = feature.sortOrder,
    shelves = feature.shelves,
    viewingShelfId = feature.viewingShelfId,
    isAddingBooksToShelf = feature.isAddingBooksToShelf,
    addBooksSource = feature.addBooksSource,
    booksSelectedForAdding = feature.selectedBookIdsForAdding,
    selectedBookIds = feature.selectedBookIds,
    selectedShelfIds = feature.selectedShelfIds,
    recentBooks = feature.recentBooks,
    libraryBooks = feature.libraryBooks,
    rawLibraryBooks = feature.rawBooks,
    pinnedHomeBookIds = feature.pinnedHomeBookIds,
    pinnedLibraryBookIds = feature.pinnedLibraryBookIds,
    libraryFilters = feature.filters,
    recentFilesLimit = feature.recentLimit,
    isTabsEnabled = feature.tabs.isEnabled,
    openTabIds = feature.tabs.openBookIds,
    openTabs = feature.openTabs,
    activeTabBookId = feature.tabs.activeBookId,
    booksAvailableForAdding = feature.booksAvailableForAdding,
    allTags = feature.tags,
    syncedFolders = feature.syncedFolders,
)

/**
 * Captures all durable state owned by the shared mobile application shell.
 * Generated folder/tag/series shelves are deliberately excluded because they
 * are rebuilt by [SharedLibraryStateProjector].
 */
fun SharedReaderScreenState.toSharedMobileLibrarySnapshot(): SharedLibrarySnapshot {
    val persistentShelves = shelves.filter { it.type == ShelfType.MANUAL || it.type == ShelfType.SMART }
    val shelfRecords = persistentShelves.map { shelf ->
        ShelfRecord(
            id = shelf.id,
            name = shelf.name,
            isSmart = shelf.type == ShelfType.SMART,
            smartRulesJson = shelf.smartRulesJson,
            modifiedAt = shelf.modifiedAt,
        )
    }
    val shelfRefs = persistentShelves.filter { it.type == ShelfType.MANUAL }.flatMap { shelf ->
        shelf.directBooks.mapIndexed { index, book ->
            BookShelfRef(
                bookId = book.id,
                shelfId = shelf.id,
                addedAt = shelf.directBookAddedAt[book.id] ?: (book.timestamp + index),
            )
        }
    }
    return SharedLibrarySnapshot(
        books = rawLibraryBooks,
        audiobooks = audiobooks,
        bookTombstones = cloudBookTombstones,
        shelfRecords = shelfRecords,
        shelfRefs = shelfRefs,
        tags = allTags,
        customFonts = customFonts,
        syncedFolders = syncedFolders,
        recentFilesLimit = normalizedRecentFilesLimit(recentFilesLimit),
        isTabsEnabled = isTabsEnabled,
        isFolderSyncEnabled = isFolderSyncEnabled,
        sortOrder = sortOrder,
        libraryFilters = libraryFilters,
        mainScreenStartPage = mainScreenStartPage.coerceIn(0, 1),
        libraryScreenStartPage = libraryScreenStartPage.coerceIn(0, 3),
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        useStrictFileFilter = useStrictFileFilter,
        externalFileBehavior = externalFileBehavior,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
        hideReaderAi = hideReaderAi,
        appLanguageTag = appLanguageTag,
        appThemeMode = appThemeMode,
        appContrastOption = appContrastOption,
        appTextDimFactorLight = appTextDimFactorLight,
        appTextDimFactorDark = appTextDimFactorDark,
        appSeedColor = appSeedColor,
        appFontPreference = appFontPreference,
        customAppThemes = customAppThemes,
        customReaderThemes = customReaderThemes,
        readerDefaultSettings = readerDefaultSettings,
        pdfReaderDefaultSettings = pdfReaderDefaultSettings,
        readerToolbarPreferences = readerToolbarPreferences,
        readerHighlightPalette = readerHighlightPalette,
        pdfHighlighterPalette = pdfHighlighterPalette,
        readerTtsReplacementPreferences = readerTtsReplacementPreferences,
        readerBookReplacementPreferences = readerBookReplacementPreferences,
    )
}
