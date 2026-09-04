package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.reader.DefaultPdfReaderSettings
import com.aryan.reader.shared.reader.ReaderSettings

data class SharedText(
    val name: String,
    val fallback: String,
    val args: List<Any?> = emptyList(),
    val quantity: Int? = null,
    val fallbackOther: String = fallback
) {
    fun fallbackMessage(): String {
        val template = if (quantity == null || quantity == 1) fallback else fallbackOther
        return formatSharedTextFallback(template, args)
    }

    companion object {
        fun string(name: String, fallback: String, vararg args: Any?): SharedText {
            return SharedText(name = name, fallback = fallback, args = args.toList())
        }

        fun quantity(
            name: String,
            quantity: Int,
            fallbackOne: String,
            fallbackOther: String,
            vararg args: Any?
        ): SharedText {
            return SharedText(
                name = name,
                fallback = fallbackOne,
                args = args.toList(),
                quantity = quantity,
                fallbackOther = fallbackOther
            )
        }
    }
}

data class BannerMessage(
    val message: String,
    val isError: Boolean = false,
    val isPersistent: Boolean = false,
    val text: SharedText? = null
) {
    companion object {
        fun localized(
            text: SharedText,
            isError: Boolean = false,
            isPersistent: Boolean = false
        ): BannerMessage {
            return BannerMessage(
                message = text.fallbackMessage(),
                isError = isError,
                isPersistent = isPersistent,
                text = text
            )
        }

        fun string(
            name: String,
            fallback: String,
            vararg args: Any?,
            isError: Boolean = false,
            isPersistent: Boolean = false
        ): BannerMessage {
            return localized(
                text = SharedText.string(name, fallback, *args),
                isError = isError,
                isPersistent = isPersistent
            )
        }

        fun quantity(
            name: String,
            quantity: Int,
            fallbackOne: String,
            fallbackOther: String,
            vararg args: Any?,
            isError: Boolean = false,
            isPersistent: Boolean = false
        ): BannerMessage {
            return localized(
                text = SharedText.quantity(name, quantity, fallbackOne, fallbackOther, *args),
                isError = isError,
                isPersistent = isPersistent
            )
        }
    }
}

internal fun formatSharedTextFallback(template: String, args: List<Any?>): String {
    if (args.isEmpty()) return template.replace("%%", "%")

    val percentPlaceholder = "\u0000PERCENT\u0000"
    var sequentialIndex = 0
    var formatted = template.replace("%%", percentPlaceholder)

    formatted = Regex("%(\\d+)\\$[-+#, .(]*\\d*(?:\\.\\d+)?[a-zA-Z]").replace(formatted) { match ->
        val argIndex = match.groupValues[1].toIntOrNull()?.minus(1)
        args.getOrNull(argIndex ?: -1).toSharedTextArgument()
    }

    formatted = Regex("%[-+#, .(]*\\d*(?:\\.\\d+)?[a-zA-Z]").replace(formatted) {
        args.getOrNull(sequentialIndex++).toSharedTextArgument()
    }

    return formatted.replace(percentPlaceholder, "%")
}

private fun Any?.toSharedTextArgument(): String {
    return when (this) {
        null -> ""
        is Float -> trimSharedTextTrailingZeroDecimal(toString())
        is Double -> trimSharedTextTrailingZeroDecimal(toString())
        else -> toString()
    }
}

private fun trimSharedTextTrailingZeroDecimal(value: String): String {
    return value.removeSuffix(".0")
}

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

enum class AppFontPreferenceKind {
    SYSTEM,
    SERIF,
    SANS_SERIF,
    MONOSPACE,
    CUSTOM
}

data class AppFontPreference(
    val kind: AppFontPreferenceKind = AppFontPreferenceKind.SYSTEM,
    val customFontId: String? = null
) {
    fun sanitized(): AppFontPreference {
        return when (kind) {
            AppFontPreferenceKind.CUSTOM -> customFontId
                ?.takeIf { it.isNotBlank() }
                ?.let { copy(customFontId = it) }
                ?: System
            else -> copy(customFontId = null)
        }
    }

    fun referencesCustomFont(fontId: String): Boolean {
        return kind == AppFontPreferenceKind.CUSTOM && customFontId == fontId
    }

    companion object {
        val System = AppFontPreference(AppFontPreferenceKind.SYSTEM)
        val Serif = AppFontPreference(AppFontPreferenceKind.SERIF)
        val SansSerif = AppFontPreference(AppFontPreferenceKind.SANS_SERIF)
        val Monospace = AppFontPreference(AppFontPreferenceKind.MONOSPACE)

        fun custom(customFontId: String): AppFontPreference {
            return AppFontPreference(AppFontPreferenceKind.CUSTOM, customFontId).sanitized()
        }
    }
}

data class AppAppearanceState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val contrastOption: AppContrastOption = AppContrastOption.STANDARD,
    val textDimFactorLight: Float = 1.0f,
    val textDimFactorDark: Float = 1.0f,
    val seedColor: Color? = null,
    val fontPreference: AppFontPreference = AppFontPreference.System,
    val customThemes: List<CustomAppTheme> = emptyList(),
)

data class AppTabState(
    val isEnabled: Boolean = true,
    val openBookIds: List<String> = emptyList(),
    val activeBookId: String? = null,
)

/** Portable shelf-navigation and dialog state; persistence and repository writes remain platform adapters. */
data class AppShelfState(
    val viewingShelfId: String? = null,
    val isAddingBooks: Boolean = false,
    val showCreateDialog: Boolean = false,
    val createShelfBookIds: Set<String> = emptySet(),
    val renameDialogShelfId: String? = null,
    val deleteDialogShelfId: String? = null,
    val addBooksSource: AddBooksSource = AddBooksSource.UNSHELVED,
    val selectedBookIdsForAdding: Set<String> = emptySet(),
)

sealed interface AppShelfAction {
    data class CreateDialogShown(val bookIds: Set<String> = emptySet()) : AppShelfAction
    data object CreateDialogDismissed : AppShelfAction
    data class ShelfOpened(val shelfId: String) : AppShelfAction
    data class RenameDialogChanged(val shelfId: String?) : AppShelfAction
    data class DeleteDialogChanged(val shelfId: String?) : AppShelfAction
    data class ShelfRenameCompleted(val shelfId: String) : AppShelfAction
    data object ShelfDeleted : AppShelfAction
    data object ShelfClosed : AppShelfAction
    data class ParentShelfOpened(val shelfId: String) : AppShelfAction
    data class AddBooksStarted(val source: AddBooksSource = AddBooksSource.UNSHELVED) : AppShelfAction
    data object AddBooksDismissed : AppShelfAction
    data object AddBooksCompleted : AppShelfAction
    data class AddBooksSourceChanged(val source: AddBooksSource) : AppShelfAction
    data class BookForAddingToggled(val bookId: String) : AppShelfAction
}

data class AppPinState(
    val homeBookIds: Set<String> = emptySet(),
    val libraryBookIds: Set<String> = emptySet(),
)

enum class AppReaderSessionPhase {
    CLOSED,
    OPENING,
    READY,
    FAILED,
}

/** Portable app-level lifecycle for opening a reader. Platform document handles stay outside this state. */
data class AppReaderSessionState(
    val bookId: String? = null,
    val fileType: FileType? = null,
    val phase: AppReaderSessionPhase = AppReaderSessionPhase.CLOSED,
    val errorMessage: String? = null,
) {
    val hasActiveReader: Boolean
        get() = bookId != null && fileType != null && phase != AppReaderSessionPhase.CLOSED

    val isLoading: Boolean
        get() = phase == AppReaderSessionPhase.OPENING

    val canRestorePersistedReader: Boolean
        get() = !hasActiveReader
}

sealed interface AppReaderSessionAction {
    data class OpenStarted(val bookId: String, val fileType: FileType) : AppReaderSessionAction
    data class OpenReady(val bookId: String) : AppReaderSessionAction
    data class OpenFailed(
        val bookId: String,
        val message: String?,
        val closeReader: Boolean = false,
    ) : AppReaderSessionAction
    /** Preserves Android's seamless-switch rollback shape until reader routing can replace it atomically. */
    data object SeamlessSwitchFailed : AppReaderSessionAction
    data object Closed : AppReaderSessionAction
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

/**
 * Single source of truth for the blocking device-limit overlay, shared first
 * per AGENTS.md. Android shows `DeviceManagementScreen` when
 * `deviceLimitState.isLimitReached` (SettingsScreen.kt:614-620,
 * HomeScreen.kt:681-687); iOS consumes this predicate for its overlay so both
 * platforms block on the same condition.
 */
fun shouldShowDeviceLimitOverlay(deviceLimitState: DeviceLimitReachedState): Boolean =
    deviceLimitState.isLimitReached

data class SharedReaderScreenState(
    val selectedBookId: String? = null,
    val selectedUriString: String? = null,
    val selectedFileType: FileType? = null,
    val audiobooks: List<SharedAudiobook> = emptyList(),
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
    val cloudBookTombstones: List<CloudBookTombstone> = emptyList(),
    val pinnedHomeBookIds: Set<String> = emptySet(),
    val pinnedLibraryBookIds: Set<String> = emptySet(),
    val libraryFilters: LibraryFilters = LibraryFilters(),
    val recentFilesLimit: Int = 0,
    val isTabsEnabled: Boolean = true,
    val openTabIds: List<String> = emptyList(),
    val openTabs: List<BookItem> = emptyList(),
    val activeTabBookId: String? = null,
    val showExternalFileSavePromptFor: String? = null,
    val externalFileBehavior: String = "ASK",
    val useStrictFileFilter: Boolean = false,
    val usePdfFileNameAsDisplayName: Boolean = false,
    val hideReaderAi: Boolean = false,
    val appLanguageTag: String? = null,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appContrastOption: AppContrastOption = AppContrastOption.STANDARD,
    val appTextDimFactorLight: Float = 1.0f,
    val appTextDimFactorDark: Float = 1.0f,
    val appSeedColor: Color? = null,
    val appFontPreference: AppFontPreference = AppFontPreference.System,
    val customAppThemes: List<CustomAppTheme> = emptyList(),
    val customReaderThemes: List<ReaderTheme> = emptyList(),
    val customFonts: List<CustomFontItem> = emptyList(),
    val readerDefaultSettings: ReaderSettings = ReaderSettings(),
    val pdfReaderDefaultSettings: ReaderSettings = DefaultPdfReaderSettings,
    val allTags: List<Tag> = emptyList(),
    val showTagSelectionDialogFor: Set<String> = emptySet(),
    val readerToolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    val readerHighlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    val pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    val pdfHighlighterSnapEnabled: Boolean = false,
    val readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    val readerBookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences()
)

val AndroidExternalFileBehaviors: Set<String> = setOf("ASK", "KEEP", "DELETE", "TEMPORARY")
val AndroidRecentFilesLimits: List<Int> = listOf(0, 10, 20, 50, 100)

fun normalizedRecentFilesLimit(value: Int): Int {
    if (value <= 0) return 0
    return AndroidRecentFilesLimits.drop(1).minBy { kotlin.math.abs(it - value) }
}

fun normalizedExternalFileBehavior(value: String?): String {
    val normalized = value?.trim()?.uppercase()
    return when (normalized) {
        "COPY" -> "KEEP"
        in AndroidExternalFileBehaviors -> normalized!!
        else -> "ASK"
    }
}
