package com.aryan.reader.desktop

import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.ui.SharedAppTab

internal val DesktopInitialAppTab = SharedAppTab.LIBRARY

internal fun desktopEmptyReaderBook(): SharedEpubBook {
    val noBookOpen = loadDesktopStringResolver().string("desktop_no_book_open", "No book open")
    return SharedEpubBook(
        id = "desktop_empty_reader",
        fileName = "",
        title = noBookOpen,
        chapters = listOf(
            SharedEpubChapter(
                id = "empty",
                title = noBookOpen,
                plainText = ""
            )
        )
    )
}

internal fun SharedLibrarySnapshot.withDesktopDefaults(): SharedLibrarySnapshot {
    val shouldMigrateReaderDefaults = desktopReaderDefaultsVersion < DesktopReaderDefaultsVersion
    val migratedTextDefaults = if (shouldMigrateReaderDefaults && readerDefaultSettings == ReaderSettings()) {
        DesktopDefaultTextReaderSettings
    } else {
        readerDefaultSettings
    }
    val migratedPdfDefaults = if (shouldMigrateReaderDefaults && pdfReaderDefaultSettings == ReaderSettings(themeId = "no_theme")) {
        DesktopDefaultPdfReaderSettings
    } else {
        pdfReaderDefaultSettings
    }
    val migratedBooks = if (shouldMigrateReaderDefaults) {
        books.map { book ->
            when {
                book.usesDesktopReaderSettingsEngine(DesktopReaderSettingsEngine.TEXT) &&
                    book.readerSettings == ReaderSettings() -> book.copy(readerSettings = migratedTextDefaults)
                book.usesDesktopReaderSettingsEngine(DesktopReaderSettingsEngine.PDF) &&
                    book.readerSettings == ReaderSettings(themeId = "no_theme") -> book.copy(readerSettings = migratedPdfDefaults)
                else -> book
            }
        }
    } else {
        books
    }
    return copy(
        books = migratedBooks,
        appSeedColor = appSeedColor ?: DesktopDefaultAppSeedColor,
        readerDefaultSettings = migratedTextDefaults,
        pdfReaderDefaultSettings = migratedPdfDefaults,
        desktopReaderDefaultsVersion = DesktopReaderDefaultsVersion
    )
}

internal fun SharedLibrarySnapshot.toDesktopReaderScreenState(): SharedReaderScreenState {
    val readableBooks = books.filter { it.type in DesktopReadableFileTypes }
    return SharedReaderScreenState(
        rawLibraryBooks = readableBooks,
        recentFilesLimit = recentFilesLimit,
        allTags = tags.ifEmpty { readableBooks.collectTags() },
        syncedFolders = syncedFolders,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        useStrictFileFilter = useStrictFileFilter,
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
        readerTtsReplacementPreferences = readerTtsReplacementPreferences
    )
}

internal fun SharedLibraryStateProjector.projectDesktopLibraryState(
    state: SharedReaderScreenState,
    shelfRecords: List<ShelfRecord>,
    shelfRefs: List<BookShelfRef>
): SharedReaderScreenState {
    val allBooks = state.rawLibraryBooks
    val visibleBooks = allBooks.filterNot { isDesktopPdfReflowBookId(it.id) }
    val projected = project(
        SharedLibraryProjectionInput(
            state = state,
            booksFromStore = visibleBooks,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            tags = state.allTags.ifEmpty { visibleBooks.collectTags() }
        )
    )
    val booksById = allBooks.associateBy { it.id }
    val openTabs = state.openTabIds.mapNotNull { booksById[it] }
    val openTabIds = openTabs.map { it.id }
    return projected.copy(
        rawLibraryBooks = allBooks,
        openTabs = openTabs,
        openTabIds = openTabIds,
        activeTabBookId = state.activeTabBookId?.takeIf { it in openTabIds }
    )
}

internal fun SharedReaderScreenState.toDesktopLibrarySnapshot(
    shelfRecords: List<ShelfRecord>,
    shelfRefs: List<BookShelfRef>,
    customFonts: List<CustomFontItem>
): SharedLibrarySnapshot {
    return SharedLibrarySnapshot(
        books = rawLibraryBooks,
        shelfRecords = shelfRecords,
        shelfRefs = shelfRefs,
        tags = allTags,
        customFonts = customFonts,
        syncedFolders = syncedFolders,
        recentFilesLimit = recentFilesLimit,
        isTabsEnabled = isTabsEnabled,
        openTabIds = openTabIds,
        activeTabBookId = activeTabBookId,
        pinnedHomeBookIds = pinnedHomeBookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds,
        useStrictFileFilter = useStrictFileFilter,
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
        desktopReaderDefaultsVersion = DesktopReaderDefaultsVersion,
        readerToolbarPreferences = readerToolbarPreferences,
        readerHighlightPalette = readerHighlightPalette,
        pdfHighlighterPalette = pdfHighlighterPalette,
        readerTtsReplacementPreferences = readerTtsReplacementPreferences
    )
}
