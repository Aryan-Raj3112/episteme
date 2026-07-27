package com.aryan.reader.shared

/**
 * Converts a persisted library snapshot into the state consumed by the shared
 * mobile home and library screens.
 */
fun SharedLibrarySnapshot.toSharedMobileReaderState(): SharedReaderScreenState {
    val base = SharedReaderScreenState(
        syncedFolders = syncedFolders,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        useStrictFileFilter = useStrictFileFilter,
        externalFileBehavior = externalFileBehavior,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
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
            state = base,
            booksFromStore = books,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            tags = tags,
        )
    )
}

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
        )
    }
    val shelfRefs = persistentShelves.flatMap { shelf ->
        shelf.directBooks.mapIndexed { index, book ->
            BookShelfRef(bookId = book.id, shelfId = shelf.id, addedAt = book.timestamp + index)
        }
    }
    return SharedLibrarySnapshot(
        books = rawLibraryBooks,
        bookTombstones = cloudBookTombstones,
        shelfRecords = shelfRecords,
        shelfRefs = shelfRefs,
        tags = allTags,
        customFonts = customFonts,
        syncedFolders = syncedFolders,
        recentFilesLimit = recentFilesLimit.takeIf { it > 0 } ?: 12,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        useStrictFileFilter = useStrictFileFilter,
        externalFileBehavior = externalFileBehavior,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
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
