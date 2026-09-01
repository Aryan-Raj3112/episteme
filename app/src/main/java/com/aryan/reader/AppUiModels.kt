package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
import com.aryan.reader.shared.reconcileAvailableBooks
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.toggleSelection
import com.aryan.reader.shared.PdfSplitWorkspaceState
import java.util.Date

typealias BannerMessage = com.aryan.reader.shared.BannerMessage
typealias UserData = com.aryan.reader.shared.UserData
typealias AppThemeMode = com.aryan.reader.shared.AppThemeMode
typealias AppContrastOption = com.aryan.reader.shared.AppContrastOption
typealias AppFontPreference = com.aryan.reader.shared.AppFontPreference
typealias AppFontPreferenceKind = com.aryan.reader.shared.AppFontPreferenceKind
typealias CustomAppTheme = com.aryan.reader.shared.CustomAppTheme
typealias AppAppearanceState = com.aryan.reader.shared.AppAppearanceState
typealias AppReaderSessionState = com.aryan.reader.shared.AppReaderSessionState
typealias AppTabState = com.aryan.reader.shared.AppTabState
typealias AppShelfState = com.aryan.reader.shared.AppShelfState
typealias AppPinState = com.aryan.reader.shared.AppPinState
typealias LibraryState = com.aryan.reader.shared.LibraryState

data class DeviceItem(val deviceId: String, val deviceName: String, val lastSeen: Date?)

data class DeviceLimitReachedState(
    val isLimitReached: Boolean = false,
    val registeredDevices: List<DeviceItem> = emptyList()
)

data class ReaderScreenState(
    val selectedPdfUri: Uri? = null,
    val selectedEpubBook: EpubBook? = null,
    val selectedEpubUri: Uri? = null,
    val readerSession: AppReaderSessionState = AppReaderSessionState(),
    /** The Android PDF split workspace uses a portable shared model for future iOS adoption. */
    val pdfSplitWorkspace: PdfSplitWorkspaceState = PdfSplitWorkspaceState(),
    val tabState: AppTabState = AppTabState(),
    val shelfState: AppShelfState = AppShelfState(),
    val pinState: AppPinState = AppPinState(),
    val libraryState: LibraryState = LibraryState(recentLimit = 0),
    val isLoading: Boolean = false,
    val isTemporaryExternalOpen: Boolean = false,
    val errorMessage: String? = null,
    val contextualActionItems: Set<RecentFileItem> = emptySet(),
    val renderMode: RenderMode = RenderMode.VERTICAL_SCROLL,
    val initialLocator: Locator? = null,
    val initialCfi: String? = null,
    val initialBookmarksJson: String? = null,
    val initialHighlightsJson: String? = null,
    val initialPageInBook: Int? = null,
    val initialPageInBookIsExplicit: Boolean = false,
    val isOpeningFromTtsNotification: Boolean = false,
    /**
     * Resolved TTS-audiobook target whose playback sheet should be presented
     * after its notification was tapped. Cleared by the consuming surface.
     */
    val pendingAudiobookTtsPlayerTarget: RecentFileItem? = null,
    val shelves: List<Shelf> = emptyList(),
    val mainScreenStartPage: Int = 0,
    /** Android-only section selected inside the experimental unified library. */
    val unifiedLibrarySection: Int = 0,
    /** Android-only display preference for the experimental unified library. */
    val unifiedLibraryListView: Boolean = false,
    val booksAvailableForAdding: List<RecentFileItem> = emptyList(),
    val contextualActionShelfIds: Set<String> = emptySet(),
    val currentUser: UserData? = null,
    val isAuthMenuExpanded: Boolean = false,
    val isProUser: Boolean = false,
    val credits: Int = 0,
    val isSyncEnabled: Boolean = false,
    val isFolderSyncEnabled: Boolean = false,
    val bannerMessage: BannerMessage? = null,
    val deviceLimitState: DeviceLimitReachedState = DeviceLimitReachedState(),
    val isReplacingDevice: Boolean = false,
    val isRequestingDrivePermission: Boolean = false,
    val downloadingBookIds: Set<String> = emptySet(),
    val uploadingBookIds: Set<String> = emptySet(),
    val syncedFolders: List<SyncedFolder> = emptyList(),
    val lastFolderScanTime: Long? = null,
    val hasUnreadFeedback: Boolean = false,
    val isSearchActive: Boolean = false,
    val isRefreshing: Boolean = false,
    val reflowProgress: Float? = null,
    val recentFiles: List<RecentFileItem> = emptyList(),
    val allRecentFiles: List<RecentFileItem> = emptyList(),
    val rawLibraryFiles: List<RecentFileItem> = emptyList(),
    val openTabs: List<RecentFileItem> = emptyList(),
    val showExternalFileSavePromptFor: String? = null,
    val externalFileBehavior: String = "ASK",
    val useStrictFileFilter: Boolean = false,
    val usePdfFileNameAsDisplayName: Boolean = false,
    val appAppearance: AppAppearanceState = AppAppearanceState(),
    val allTags: List<TagEntity> = emptyList(),
    val showTagSelectionDialogFor: Set<String> = emptySet(),
    val showAddSelectedToShelfDialogFor: Set<String> = emptySet(),
    val isScreenCaptureProtectionEnabled: Boolean = false,
) {
    val selectedBookId: String? get() = readerSession.bookId
    val selectedFileType: FileType? get() = readerSession.fileType
    val isTabsEnabled: Boolean get() = tabState.isEnabled
    val openTabIds: List<String> get() = tabState.openBookIds
    val activeTabBookId: String? get() = tabState.activeBookId
    val viewingShelfId: String? get() = shelfState.viewingShelfId
    val isAddingBooksToShelf: Boolean get() = shelfState.isAddingBooks
    val showCreateShelfDialog: Boolean get() = shelfState.showCreateDialog
    val createShelfSelectedBookIds: Set<String> get() = shelfState.createShelfBookIds
    val showRenameShelfDialogFor: String? get() = shelfState.renameDialogShelfId
    val showDeleteShelfDialogFor: String? get() = shelfState.deleteDialogShelfId
    val addBooksSource: AddBooksSource get() = shelfState.addBooksSource
    val booksSelectedForAdding: Set<String> get() = shelfState.selectedBookIdsForAdding
    val pinnedHomeBookIds: Set<String> get() = pinState.homeBookIds
    val pinnedLibraryBookIds: Set<String> get() = pinState.libraryBookIds
    val searchQuery: String get() = libraryState.searchQuery
    val sortOrder: SortOrder get() = libraryState.sortOrder
    val libraryFilters: LibraryFilters get() = libraryState.filters
    val libraryScreenStartPage: Int get() = libraryState.libraryPage
    val recentFilesLimit: Int get() = libraryState.recentLimit
    val appThemeMode: AppThemeMode get() = appAppearance.themeMode
    val appContrastOption: AppContrastOption get() = appAppearance.contrastOption
    val appTextDimFactorLight: Float get() = appAppearance.textDimFactorLight
    val appTextDimFactorDark: Float get() = appAppearance.textDimFactorDark
    val appSeedColor: androidx.compose.ui.graphics.Color? get() = appAppearance.seedColor
    val appFontPreference: AppFontPreference get() = appAppearance.fontPreference
    val customAppThemes: List<CustomAppTheme> get() = appAppearance.customThemes
}

internal fun setTabsEnabled(current: ReaderScreenState, enabled: Boolean): ReaderScreenState {
    val reduced = current.tabState.reduce(SharedAppAction.TabsEnabledChanged(enabled))
    if (enabled) return current.copy(tabState = reduced)

    val activeTab = current.activeTabBookId
    return current.copy(
        tabState = reduced.copy(
            openBookIds = if (activeTab == null) emptyList() else listOf(activeTab),
            activeBookId = activeTab,
        ),
    )
}

internal fun openBookTab(
    current: ReaderScreenState,
    availableBookIds: Set<String>,
    bookId: String,
): ReaderScreenState {
    val reconciled = reconcileTabState(current, availableBookIds)
    return current.copy(tabState = reconciled.tabState.reduce(SharedAppAction.BookTabOpened(bookId)))
}

internal fun closeBookTab(
    current: ReaderScreenState,
    availableBookIds: Set<String>,
    bookId: String,
): ReaderScreenState {
    val reconciled = reconcileTabState(current, availableBookIds)
    return current.copy(tabState = reconciled.tabState.reduce(SharedAppAction.BookTabClosed(bookId)))
}

internal fun closeAllTabs(current: ReaderScreenState): ReaderScreenState =
    current.copy(tabState = current.tabState.reduce(SharedAppAction.AllTabsClosed))

internal fun reconcileTabState(
    current: ReaderScreenState,
    availableBookIds: Set<String>,
): ReaderScreenState {
    val reconciled = current.tabState.reconcileAvailableBooks(availableBookIds)
    return if (
        current.openTabIds == reconciled.openBookIds &&
        current.activeTabBookId == reconciled.activeBookId
    ) {
        current
    } else {
        current.copy(tabState = reconciled)
    }
}

internal fun reduceLibraryAction(
    current: ReaderScreenState,
    projectedState: ReaderScreenState,
    action: SharedLibraryAction,
): ReaderScreenState {
    val rawBooks = projectedState.rawLibraryFiles.ifEmpty { current.rawLibraryFiles }
    val androidBooksById = (rawBooks + current.contextualActionItems).associateBy { it.bookId }
    val reduced = current.libraryState.copy(
        selectedBookIds = current.contextualActionItems.mapTo(linkedSetOf()) { it.bookId },
        selectedShelfIds = current.contextualActionShelfIds,
    ).reduce(action)
    return current.copy(
        libraryState = reduced,
        contextualActionItems = reduced.selectedBookIds.mapNotNullTo(mutableSetOf()) { androidBooksById[it] },
        contextualActionShelfIds = reduced.selectedShelfIds,
    )
}

internal fun togglePinsForSelectedBooks(
    current: ReaderScreenState,
    isHome: Boolean,
): ReaderScreenState {
    val selectedIds = current.contextualActionItems.mapTo(linkedSetOf()) { it.bookId }
    if (selectedIds.isEmpty()) return current
    val reduced = current.pinState.toggleSelection(selectedIds, isHome)
    return current.copy(
        pinState = reduced,
        libraryState = current.libraryState.reduce(SharedLibraryAction.SelectionCleared),
        contextualActionItems = emptySet(),
    )
}

internal fun replaceBookSelectionWithVisibleBooks(
    current: ReaderScreenState,
    projectedState: ReaderScreenState,
    visibleBooks: Collection<RecentFileItem>,
): ReaderScreenState {
    val visibleIds = visibleBooks.mapTo(linkedSetOf()) { it.bookId }
    val selectedIds = current.contextualActionItems.mapTo(linkedSetOf()) { it.bookId }
    val action = if (visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)) {
        SharedLibraryAction.SelectionCleared
    } else {
        SharedLibraryAction.BookSelectionReplaced(visibleIds)
    }
    return reduceLibraryAction(current, projectedState, action)
}
