package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.epub.CalibreBundleResult
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.Locator
import java.util.Date

data class BannerMessage(val message: String, val isError: Boolean = false, val isPersistent: Boolean = false)

data class ImportResult(
    val internalUri: Uri,
    val bookId: String,
    val type: FileType,
    val bundleResult: CalibreBundleResult? = null
)

data class UserData(
    val uid: String,
    val displayName: String?,
    val photoUrl: String?,
    val email: String?
)

data class NavigationEvent(
    val route: String,
    val bookId: String? = null,
    val uri: Uri? = null
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppContrastOption(val value: Double) {
    STANDARD(0.0),
    MEDIUM(0.5),
    HIGH(1.0)
}

data class CustomAppTheme(
    val id: String,
    val name: String,
    val seedColor: androidx.compose.ui.graphics.Color
)

data class DeviceItem(val deviceId: String, val deviceName: String, val lastSeen: Date?)

data class DeviceLimitReachedState(
    val isLimitReached: Boolean = false,
    val registeredDevices: List<DeviceItem> = emptyList()
)

data class ReaderScreenState(
    val selectedPdfUri: Uri? = null,
    val selectedBookId: String? = null,
    val selectedEpubBook: EpubBook? = null,
    val selectedEpubUri: Uri? = null,
    val selectedFileType: FileType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val contextualActionItems: Set<RecentFileItem> = emptySet(),
    val renderMode: RenderMode = RenderMode.VERTICAL_SCROLL,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val initialLocator: Locator? = null,
    val initialCfi: String? = null,
    val initialBookmarksJson: String? = null,
    val initialHighlightsJson: String? = null,
    val initialPageInBook: Int? = null,
    val shelves: List<Shelf> = emptyList(),
    val viewingShelfId: String? = null,
    val isAddingBooksToShelf: Boolean = false,
    val showCreateShelfDialog: Boolean = false,
    val mainScreenStartPage: Int = 0,
    val libraryScreenStartPage: Int = 0,
    val showRenameShelfDialogFor: String? = null,
    val showDeleteShelfDialogFor: String? = null,
    val addBooksSource: AddBooksSource = AddBooksSource.UNSHELVED,
    val booksSelectedForAdding: Set<String> = emptySet(),
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
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isRefreshing: Boolean = false,
    val reflowProgress: Float? = null,
    val recentFiles: List<RecentFileItem> = emptyList(),
    val allRecentFiles: List<RecentFileItem> = emptyList(),
    val rawLibraryFiles: List<RecentFileItem> = emptyList(),
    val pinnedHomeBookIds: Set<String> = emptySet(),
    val pinnedLibraryBookIds: Set<String> = emptySet(),
    val libraryFilters: LibraryFilters = LibraryFilters(),
    val recentFilesLimit: Int = 0,
    val isTabsEnabled: Boolean = false,
    val openTabIds: List<String> = emptyList(),
    val openTabs: List<RecentFileItem> = emptyList(),
    val activeTabBookId: String? = null,
    val showExternalFileSavePromptFor: String? = null,
    val externalFileBehavior: String = "ASK",
    val useStrictFileFilter: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appContrastOption: AppContrastOption = AppContrastOption.STANDARD,
    val appTextDimFactorLight: Float = 1.0f,
    val appTextDimFactorDark: Float = 1.0f,
    val appSeedColor: androidx.compose.ui.graphics.Color? = null,
    val customAppThemes: List<CustomAppTheme> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val showTagSelectionDialogFor: Set<String> = emptySet(),
)
