package com.aryan.reader.shared

sealed interface LibraryAction {
    data class SearchChanged(val query: String) : LibraryAction
    data class SortChanged(val sortOrder: SortOrder) : LibraryAction
    data class FiltersChanged(val filters: LibraryFilters) : LibraryAction
    data class BookSelectionToggled(val bookId: String) : LibraryAction
    data object SelectionCleared : LibraryAction
    data class ShelfSelectionToggled(val shelfId: String) : LibraryAction
    data object ShelfSelectionCleared : LibraryAction
    data class LibraryPageChanged(val page: Int) : LibraryAction
    data class RecentLimitChanged(val limit: Int) : LibraryAction
}

sealed interface ReaderAction {
    data object NextPage : ReaderAction
    data object PreviousPage : ReaderAction
    data class GoToPage(val pageIndex: Int) : ReaderAction
    data class GoToProgress(val progress: Float) : ReaderAction
    data class GoToChapter(val chapterIndex: Int) : ReaderAction
    data class SearchChanged(val query: String) : ReaderAction
    data object NextSearchResult : ReaderAction
    data object PreviousSearchResult : ReaderAction
    data object ToggleBookmark : ReaderAction
    data class RenderModeChanged(val renderMode: RenderMode) : ReaderAction
    data class ThemeChanged(val theme: ReaderTheme) : ReaderAction
    data class FormatChanged(val settings: FormatSettings) : ReaderAction
}

sealed interface AppAction {
    data class BannerShown(val message: BannerMessage) : AppAction
    data object BannerDismissed : AppAction
    data class NavigationRequested(val event: NavigationEvent) : AppAction
    data class AppThemeChanged(val mode: AppThemeMode) : AppAction
    data class AppContrastChanged(val option: AppContrastOption) : AppAction
    data class SyncEnabledChanged(val enabled: Boolean) : AppAction
    data class FolderSyncEnabledChanged(val enabled: Boolean) : AppAction
}
