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
    SETTINGS,
    IMPORT_FILES,
    IMPORT_FOLDER,
    SYNC,
    APP_THEME,
    PRO,
    AI_SETTINGS,
    CUSTOM_FONTS,
    HELP_FEEDBACK,
    SUPPORT,
    ABOUT,
    TABS_TOGGLE
}

enum class SharedAppMoreGroup {
    LIBRARY,
    ACCOUNT,
    PREFERENCES,
    HELP
}

data class SharedAppMoreSection(
    val group: SharedAppMoreGroup,
    val actions: List<SharedAppToolAction>
)

data class SharedAppShellModel(
    val primaryTabs: List<SharedAppTab>,
    val primaryActions: List<SharedAppToolAction>,
    val selectedPrimaryTab: SharedAppTab,
    val toolActions: List<SharedAppToolAction>,
    val moreSections: List<SharedAppMoreSection>,
    val showPrimaryNavigation: Boolean
)

data class SharedSidebarSyncToggleModel(
    val visible: Boolean,
    val enabled: Boolean,
    val checked: Boolean
)

fun sharedAppShellModel(
    selectedTab: SharedAppTab,
    aiSettingsAvailable: Boolean,
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard
): SharedAppShellModel {
    val primaryTabs = buildList {
        add(SharedAppTab.LIBRARY)
        if (featurePolicy.opdsCatalogs) add(SharedAppTab.CATALOGS)
        if (featurePolicy.aiAndCloud) add(SharedAppTab.PRO)
    }
    val primaryActions = buildList {
        if (aiSettingsAvailable && featurePolicy.aiAndCloud && !featurePolicy.byokAi) {
            add(SharedAppToolAction.AI_SETTINGS)
        }
    }
    val selectedPrimaryTab = when (selectedTab) {
        SharedAppTab.SHELVES -> SharedAppTab.LIBRARY
        SharedAppTab.SETTINGS,
        SharedAppTab.CUSTOM_FONTS,
        SharedAppTab.SUPPORT,
        SharedAppTab.FEEDBACK,
        SharedAppTab.ABOUT -> SharedAppTab.LIBRARY
        else -> selectedTab
    }.takeIf { it in primaryTabs } ?: SharedAppTab.LIBRARY
    val toolActions = buildList {
        add(SharedAppToolAction.SETTINGS)
        add(SharedAppToolAction.APP_THEME)
        if (aiSettingsAvailable && featurePolicy.aiAndCloud) add(SharedAppToolAction.AI_SETTINGS)
        add(SharedAppToolAction.CUSTOM_FONTS)
        if (featurePolicy.projectLinks) {
            add(SharedAppToolAction.HELP_FEEDBACK)
            add(SharedAppToolAction.SUPPORT)
        }
        add(SharedAppToolAction.ABOUT)
    }
    return SharedAppShellModel(
        primaryTabs = primaryTabs,
        primaryActions = primaryActions,
        selectedPrimaryTab = selectedPrimaryTab,
        toolActions = toolActions,
        moreSections = sharedAppMoreSections(toolActions),
        showPrimaryNavigation = selectedTab != SharedAppTab.READER
    )
}

fun sharedAppMoreSections(actions: List<SharedAppToolAction>): List<SharedAppMoreSection> {
    return listOf(
        SharedAppMoreSection(
            group = SharedAppMoreGroup.PREFERENCES,
            actions = actions.filter {
                it == SharedAppToolAction.SETTINGS ||
                    it == SharedAppToolAction.APP_THEME ||
                    it == SharedAppToolAction.AI_SETTINGS ||
                    it == SharedAppToolAction.CUSTOM_FONTS
            }
        ),
        SharedAppMoreSection(
            group = SharedAppMoreGroup.HELP,
            actions = actions.filter {
                it == SharedAppToolAction.HELP_FEEDBACK ||
                    it == SharedAppToolAction.SUPPORT ||
                    it == SharedAppToolAction.ABOUT
            }
        )
    ).filter { it.actions.isNotEmpty() }
}

fun sharedSidebarSyncToggleModel(
    isSignedIn: Boolean,
    accountAvailable: Boolean,
    syncAvailable: Boolean,
    isProUser: Boolean,
    isSyncEnabled: Boolean,
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard
): SharedSidebarSyncToggleModel {
    val visible = isSignedIn &&
        accountAvailable &&
        syncAvailable &&
        featurePolicy.aiAndCloud
    return SharedSidebarSyncToggleModel(
        visible = visible,
        enabled = visible && isProUser,
        checked = visible && isSyncEnabled
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
    val titleKey: String,
    val titleFallback: String,
    val fileTypes: List<FileType>
)

private val LibraryFileTypeGroupTemplates = listOf(
    NonReaderLibraryFileTypeGroup(
        titleKey = "desktop_file_type_group_books",
        titleFallback = "Books",
        fileTypes = listOf(FileType.EPUB, FileType.MOBI, FileType.FB2)
    ),
    NonReaderLibraryFileTypeGroup(
        titleKey = "desktop_file_type_group_documents",
        titleFallback = "Documents",
        fileTypes = listOf(FileType.PDF, FileType.PPTX, FileType.DOCX, FileType.ODT, FileType.FODT)
    ),
    NonReaderLibraryFileTypeGroup(
        titleKey = "desktop_file_type_group_text_web",
        titleFallback = "Text and web",
        fileTypes = listOf(FileType.MD, FileType.TXT, FileType.HTML)
    ),
    NonReaderLibraryFileTypeGroup(
        titleKey = "desktop_file_type_group_comics",
        titleFallback = "Comics",
        fileTypes = listOf(FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT)
    )
)

private val AndroidLibraryTabs = listOf(
    NonReaderLibraryTab.BOOKS,
    NonReaderLibraryTab.SHELVES,
    NonReaderLibraryTab.FOLDERS
)

private val DesktopLibraryTabs = listOf(
    NonReaderLibraryTab.BOOKS,
    NonReaderLibraryTab.SHELVES,
    NonReaderLibraryTab.FOLDERS,
    NonReaderLibraryTab.UNREAD,
    NonReaderLibraryTab.IN_PROGRESS,
    NonReaderLibraryTab.COMPLETED
)

internal enum class NonReaderLibraryPrimaryAction {
    NEW_SHELF
}

internal enum class NonReaderBookOverflowAction {
    ADD_TO_SHELF
}

internal fun visibleNonReaderLibraryTabs(
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): List<NonReaderLibraryTab> {
    return when (platform) {
        ReaderPlatform.ANDROID -> AndroidLibraryTabs
        ReaderPlatform.DESKTOP -> DesktopLibraryTabs
    }
}

internal fun primaryLibraryActionsForTab(
    tab: NonReaderLibraryTab,
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): List<NonReaderLibraryPrimaryAction> {
    return if (platform == ReaderPlatform.DESKTOP && tab.visibleLibraryTab(platform) == NonReaderLibraryTab.SHELVES) {
        listOf(NonReaderLibraryPrimaryAction.NEW_SHELF)
    } else {
        emptyList()
    }
}

internal fun bookOverflowActionsForPlatform(
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): Set<NonReaderBookOverflowAction> {
    return when (platform) {
        ReaderPlatform.DESKTOP -> setOf(NonReaderBookOverflowAction.ADD_TO_SHELF)
        ReaderPlatform.ANDROID -> emptySet()
    }
}

internal enum class LibraryCommandBarLayout {
    INLINE,
    STACKED
}

internal fun libraryCommandBarLayoutForWidth(
    widthDp: Float,
    platform: ReaderPlatform = ReaderPlatform.DESKTOP
): LibraryCommandBarLayout {
    return if (platform == ReaderPlatform.DESKTOP && widthDp >= 980f) {
        LibraryCommandBarLayout.INLINE
    } else {
        LibraryCommandBarLayout.STACKED
    }
}

internal fun NonReaderLibraryTab.visibleLibraryTab(
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): NonReaderLibraryTab {
    return takeIf { it in visibleNonReaderLibraryTabs(platform) } ?: NonReaderLibraryTab.BOOKS
}

internal fun SharedReaderScreenState.booksForNonReaderLibraryTab(
    tab: NonReaderLibraryTab,
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): List<BookItem> {
    return when (tab.visibleLibraryTab(platform)) {
        NonReaderLibraryTab.UNREAD -> libraryBooks.filter { progressPercentValue(it.progressPercentage) == 0 }
        NonReaderLibraryTab.IN_PROGRESS -> libraryBooks.filter { progressPercentValue(it.progressPercentage) in 1..99 }
        NonReaderLibraryTab.COMPLETED -> libraryBooks.filter { progressPercentValue(it.progressPercentage) >= 100 }
        else -> libraryBooks
    }
}

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
        grouped + NonReaderLibraryFileTypeGroup(
            titleKey = "desktop_file_type_group_other",
            titleFallback = "Other",
            fileTypes = otherTypes
        )
    }
}

fun SharedReaderScreenState.toNonReaderLibraryOrganizationModel(): NonReaderLibraryOrganizationModel {
    val books = organizationBooks()
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

private fun SharedReaderScreenState.organizationBooks(): List<BookItem> {
    if (shelves.isEmpty()) return rawLibraryBooks
    return shelves
        .flatMap { it.books }
        .distinctBy { it.id }
}

private fun LibraryFilters.activeFilterCount(): Int {
    return fileTypes.size +
        sourceFolders.size +
        tagIds.size +
        if (readStatus == ReadStatusFilter.ALL) 0 else 1
}
