package com.aryan.reader

import com.aryan.reader.data.RecentFileItem

data class HomeScreenModel(
    val recentFiles: List<RecentFileItem>,
    val openTabs: List<RecentFileItem>,
    val selectedItems: Set<RecentFileItem>,
    val isContextualModeActive: Boolean,
    val deviceLimitState: DeviceLimitReachedState,
    val isEmpty: Boolean,
    val isLibraryEmpty: Boolean
)

fun ReaderScreenState.toHomeScreenModel(): HomeScreenModel {
    val homeRecentFiles = recentFiles
    return HomeScreenModel(
        recentFiles = homeRecentFiles,
        openTabs = openTabs,
        selectedItems = contextualActionItems,
        isContextualModeActive = contextualActionItems.isNotEmpty(),
        deviceLimitState = deviceLimitState,
        isEmpty = homeRecentFiles.isEmpty() && (!isTabsEnabled || openTabs.isEmpty()),
        isLibraryEmpty = recentFiles.isEmpty()
    )
}

data class LibraryScreenModel(
    val selectedItems: Set<RecentFileItem>,
    val isContextualModeActive: Boolean,
    val selectedShelves: Set<String>,
    val isShelfContextualModeActive: Boolean,
    val sortOrder: SortOrder,
    val shelves: List<Shelf>,
    val rawLibraryFiles: List<RecentFileItem>,
    val containsFolderItemsInSelection: Boolean,
    val isSearchActive: Boolean,
    val searchQuery: String
)

fun ReaderScreenState.toLibraryScreenModel(): LibraryScreenModel {
    return LibraryScreenModel(
        selectedItems = contextualActionItems,
        isContextualModeActive = contextualActionItems.isNotEmpty(),
        selectedShelves = contextualActionShelfIds,
        isShelfContextualModeActive = contextualActionShelfIds.isNotEmpty(),
        sortOrder = sortOrder,
        shelves = shelves,
        rawLibraryFiles = rawLibraryFiles,
        containsFolderItemsInSelection = contextualActionItems.any { it.sourceFolderUri != null },
        isSearchActive = isSearchActive,
        searchQuery = searchQuery
    )
}
