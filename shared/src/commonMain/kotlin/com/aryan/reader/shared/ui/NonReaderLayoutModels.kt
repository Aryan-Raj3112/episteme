package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedFileCapabilities
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
    val toolActions: List<SharedAppToolAction>,
    val showPrimaryNavigation: Boolean
)

fun sharedAppShellModel(
    selectedTab: SharedAppTab,
    aiSettingsAvailable: Boolean,
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard
): SharedAppShellModel {
    val primaryTabs = buildList {
        add(SharedAppTab.HOME)
        add(SharedAppTab.LIBRARY)
        if (featurePolicy.opdsCatalogs) add(SharedAppTab.CATALOGS)
    }
    val selectedPrimaryTab = when (selectedTab) {
        SharedAppTab.SHELVES -> SharedAppTab.LIBRARY
        SharedAppTab.SETTINGS,
        SharedAppTab.CUSTOM_FONTS,
        SharedAppTab.SUPPORT,
        SharedAppTab.FEEDBACK,
        SharedAppTab.ABOUT -> SharedAppTab.HOME
        else -> selectedTab
    }.takeIf { it in primaryTabs } ?: SharedAppTab.HOME
    val toolActions = buildList {
        add(SharedAppToolAction.IMPORT_FILES)
        add(SharedAppToolAction.IMPORT_FOLDER)
        add(SharedAppToolAction.SYNC)
        add(SharedAppToolAction.APP_THEME)
        if (aiSettingsAvailable && featurePolicy.aiAndCloud) add(SharedAppToolAction.AI_SETTINGS)
        add(SharedAppToolAction.CUSTOM_FONTS)
        if (featurePolicy.projectLinks) {
            add(SharedAppToolAction.HELP_FEEDBACK)
            add(SharedAppToolAction.SUPPORT)
        }
        add(SharedAppToolAction.ABOUT)
        add(SharedAppToolAction.TABS_TOGGLE)
    }
    return SharedAppShellModel(
        primaryTabs = primaryTabs,
        selectedPrimaryTab = selectedPrimaryTab,
        toolActions = toolActions,
        showPrimaryNavigation = selectedTab != SharedAppTab.READER
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

internal data class NonReaderLibraryFileTypeGroup(
    val title: String,
    val fileTypes: List<FileType>
)

private val LibraryFileTypeGroupTemplates = listOf(
    NonReaderLibraryFileTypeGroup(
        title = "Books",
        fileTypes = listOf(FileType.EPUB, FileType.MOBI, FileType.FB2)
    ),
    NonReaderLibraryFileTypeGroup(
        title = "Documents",
        fileTypes = listOf(FileType.PDF, FileType.PPTX, FileType.DOCX, FileType.ODT, FileType.FODT)
    ),
    NonReaderLibraryFileTypeGroup(
        title = "Text and web",
        fileTypes = listOf(FileType.MD, FileType.TXT, FileType.HTML)
    ),
    NonReaderLibraryFileTypeGroup(
        title = "Comics",
        fileTypes = listOf(FileType.CBZ, FileType.CBR, FileType.CB7)
    )
)

internal fun nonReaderLibraryFileTypeGroups(
    platform: ReaderPlatform = ReaderPlatform.DESKTOP
): List<NonReaderLibraryFileTypeGroup> {
    val readableTypes = SharedFileCapabilities.readableTypesFor(platform)
    val knownGroupedTypes = LibraryFileTypeGroupTemplates.flatMapTo(mutableSetOf()) { it.fileTypes }
    val grouped = LibraryFileTypeGroupTemplates.mapNotNull { group ->
        val visibleTypes = group.fileTypes.filter { it in readableTypes }
        group.copy(fileTypes = visibleTypes).takeIf { visibleTypes.isNotEmpty() }
    }
    val otherTypes = readableTypes
        .filterNot { it in knownGroupedTypes }
        .sortedBy { it.ordinal }

    return if (otherTypes.isEmpty()) {
        grouped
    } else {
        grouped + NonReaderLibraryFileTypeGroup("Other", otherTypes)
    }
}

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
