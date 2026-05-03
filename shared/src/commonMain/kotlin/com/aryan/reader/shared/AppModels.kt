package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color

data class BannerMessage(
    val message: String,
    val isError: Boolean = false,
    val isPersistent: Boolean = false
)

data class ImportResult(
    val uriString: String,
    val bookId: String,
    val type: FileType,
    val bundleId: String? = null
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
    val uriString: String? = null
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
    val seedColor: Color
)

data class DeviceItem(
    val deviceId: String,
    val deviceName: String,
    val lastSeenEpochMillis: Long?
)

data class DeviceLimitReachedState(
    val isLimitReached: Boolean = false,
    val registeredDevices: List<DeviceItem> = emptyList()
)

data class SharedReaderScreenState(
    val selectedBookId: String? = null,
    val selectedUriString: String? = null,
    val selectedFileType: FileType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val renderMode: RenderMode = RenderMode.VERTICAL_SCROLL,
    val sortOrder: SortOrder = SortOrder.RECENT,
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
    val booksAvailableForAdding: List<BookItem> = emptyList(),
    val selectedBookIds: Set<String> = emptySet(),
    val selectedShelfIds: Set<String> = emptySet(),
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
    val recentBooks: List<BookItem> = emptyList(),
    val libraryBooks: List<BookItem> = emptyList(),
    val rawLibraryBooks: List<BookItem> = emptyList(),
    val pinnedHomeBookIds: Set<String> = emptySet(),
    val pinnedLibraryBookIds: Set<String> = emptySet(),
    val libraryFilters: LibraryFilters = LibraryFilters(),
    val recentFilesLimit: Int = 0,
    val isTabsEnabled: Boolean = false,
    val openTabIds: List<String> = emptyList(),
    val openTabs: List<BookItem> = emptyList(),
    val activeTabBookId: String? = null,
    val showExternalFileSavePromptFor: String? = null,
    val externalFileBehavior: String = "ASK",
    val useStrictFileFilter: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appContrastOption: AppContrastOption = AppContrastOption.STANDARD,
    val appTextDimFactorLight: Float = 1.0f,
    val appTextDimFactorDark: Float = 1.0f,
    val appSeedColor: Color? = null,
    val customAppThemes: List<CustomAppTheme> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val showTagSelectionDialogFor: Set<String> = emptySet()
)
