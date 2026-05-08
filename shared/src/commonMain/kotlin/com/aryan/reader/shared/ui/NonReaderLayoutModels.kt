package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.isOpdsStream
import com.aryan.reader.shared.progressPercentValue
import com.aryan.reader.shared.toHomeScreenModel

enum class SharedAppToolAction {
    IMPORT_FILES,
    IMPORT_FOLDER,
    SYNC,
    APP_THEME,
    AI_SETTINGS,
    CUSTOM_FONTS,
    HELP_FEEDBACK,
    SUPPORT,
    ABOUT,
    TABS_TOGGLE
}

data class SharedAppShellModel(
    val primaryTabs: List<SharedAppTab>,
    val selectedPrimaryTab: SharedAppTab,
    val toolActions: List<SharedAppToolAction>
)

fun sharedAppShellModel(
    selectedTab: SharedAppTab,
    aiSettingsAvailable: Boolean
): SharedAppShellModel {
    val primaryTabs = listOf(
        SharedAppTab.HOME,
        SharedAppTab.LIBRARY,
        SharedAppTab.CATALOGS,
        SharedAppTab.READER
    )
    val selectedPrimaryTab = when (selectedTab) {
        SharedAppTab.SHELVES -> SharedAppTab.LIBRARY
        SharedAppTab.CUSTOM_FONTS,
        SharedAppTab.SUPPORT,
        SharedAppTab.FEEDBACK,
        SharedAppTab.ABOUT -> SharedAppTab.HOME
        else -> selectedTab
    }
    val toolActions = buildList {
        add(SharedAppToolAction.IMPORT_FILES)
        add(SharedAppToolAction.IMPORT_FOLDER)
        add(SharedAppToolAction.SYNC)
        add(SharedAppToolAction.APP_THEME)
        if (aiSettingsAvailable) add(SharedAppToolAction.AI_SETTINGS)
        add(SharedAppToolAction.CUSTOM_FONTS)
        add(SharedAppToolAction.HELP_FEEDBACK)
        add(SharedAppToolAction.SUPPORT)
        add(SharedAppToolAction.ABOUT)
        add(SharedAppToolAction.TABS_TOGGLE)
    }
    return SharedAppShellModel(
        primaryTabs = primaryTabs,
        selectedPrimaryTab = selectedPrimaryTab,
        toolActions = toolActions
    )
}

data class NonReaderHomeLayoutModel(
    val continueBook: BookItem?,
    val activeTabs: List<BookItem>,
    val pinnedBooks: List<BookItem>,
    val recentBooks: List<BookItem>,
    val selectedBooks: List<BookItem>,
    val isContextualModeActive: Boolean,
    val isEmpty: Boolean,
    val isLibraryEmpty: Boolean
)

fun SharedReaderScreenState.toNonReaderHomeLayoutModel(): NonReaderHomeLayoutModel {
    val model = toHomeScreenModel()
    val activeTabs = if (isTabsEnabled) model.openTabs else emptyList()
    val continueBook = activeTabs.firstOrNull { it.id == activeTabBookId }
        ?: model.recentBooks.firstOrNull { progressPercentValue(it.progressPercentage) in 1..99 }
        ?: model.recentBooks.firstOrNull()
    val continueId = continueBook?.id
    val pinnedBooks = model.recentBooks
        .filter { it.id in pinnedHomeBookIds && it.id != continueId }
    val recentBooks = model.recentBooks
        .filter { it.id !in pinnedHomeBookIds && it.id != continueId }
    return NonReaderHomeLayoutModel(
        continueBook = continueBook,
        activeTabs = activeTabs,
        pinnedBooks = pinnedBooks,
        recentBooks = recentBooks,
        selectedBooks = model.selectedBooks,
        isContextualModeActive = model.isContextualModeActive,
        isEmpty = continueBook == null && pinnedBooks.isEmpty() && recentBooks.isEmpty() && activeTabs.isEmpty(),
        isLibraryEmpty = model.isLibraryEmpty
    )
}

data class NonReaderLibraryOrganizationModel(
    val allBooksCount: Int,
    val shelfCount: Int,
    val smartShelfCount: Int,
    val tagCount: Int,
    val folderCount: Int,
    val unreadCount: Int,
    val inProgressCount: Int,
    val completedCount: Int,
    val activeFilterCount: Int,
    val availableFileTypes: List<FileType>,
    val hasInAppBooks: Boolean,
    val hasOpdsStreams: Boolean
)

fun SharedReaderScreenState.toNonReaderLibraryOrganizationModel(): NonReaderLibraryOrganizationModel {
    val books = rawLibraryBooks
    val rootFolderCount = shelves.count { it.type == ShelfType.FOLDER && it.parentShelfId == null }
    val tagIds = (allTags.map { it.id } + books.flatMap { book -> book.tags.map { it.id } }).toSet()
    return NonReaderLibraryOrganizationModel(
        allBooksCount = books.size,
        shelfCount = shelves.count { it.type != ShelfType.FOLDER && it.type != ShelfType.TAG && it.type != ShelfType.SMART },
        smartShelfCount = shelves.count { it.type == ShelfType.SMART },
        tagCount = tagIds.size,
        folderCount = maxOf(rootFolderCount, syncedFolders.size),
        unreadCount = books.count { progressPercentValue(it.progressPercentage) == 0 },
        inProgressCount = books.count { progressPercentValue(it.progressPercentage) in 1..99 },
        completedCount = books.count { progressPercentValue(it.progressPercentage) >= 100 },
        activeFilterCount = libraryFilters.activeFilterCount(),
        availableFileTypes = books
            .map { it.type }
            .filterNot { it == FileType.UNKNOWN }
            .distinct()
            .sortedBy { it.ordinal },
        hasInAppBooks = books.any { it.sourceFolder == null && !it.isOpdsStream() },
        hasOpdsStreams = books.any { it.isOpdsStream() }
    )
}

private fun LibraryFilters.activeFilterCount(): Int {
    return fileTypes.size +
        sourceFolders.size +
        tagIds.size +
        if (readStatus == ReadStatusFilter.ALL) 0 else 1
}
