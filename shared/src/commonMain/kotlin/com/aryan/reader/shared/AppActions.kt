package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSearchOptions

sealed interface LibraryAction {
    data class SearchChanged(val query: String) : LibraryAction
    data class SortChanged(val sortOrder: SortOrder) : LibraryAction
    data class FiltersChanged(val filters: LibraryFilters) : LibraryAction
    data class BookSelectionToggled(val bookId: String) : LibraryAction
    data class BookSelectionReplaced(val bookIds: Set<String>) : LibraryAction
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
    data class GoToPageNumber(val pageNumber: Int) : ReaderAction
    data class GoToProgress(val progress: Float) : ReaderAction
    data class GoToChapter(val chapterIndex: Int) : ReaderAction
    data class GoToLocator(val locator: ReaderLocator) : ReaderAction
    data class JumpToPage(val pageIndex: Int) : ReaderAction
    data class JumpToPageNumber(val pageNumber: Int) : ReaderAction
    data class JumpToChapter(val chapterIndex: Int) : ReaderAction
    data class JumpToLocator(val locator: ReaderLocator) : ReaderAction
    data class VisiblePageChanged(val pageIndex: Int, val locator: ReaderLocator? = null) : ReaderAction
    data class GoToSearchResult(val resultIndex: Int) : ReaderAction
    data class JumpToSearchResult(val resultIndex: Int) : ReaderAction
    data object JumpToNextSearchResult : ReaderAction
    data object JumpToPreviousSearchResult : ReaderAction
    data object JumpBack : ReaderAction
    data object JumpForward : ReaderAction
    data object JumpHistoryCleared : ReaderAction
    data class SearchChanged(val query: String) : ReaderAction
    data object SearchOpened : ReaderAction
    data object SearchClosed : ReaderAction
    data object SearchResultsPanelToggled : ReaderAction
    data class SearchOptionsChanged(val options: ReaderSearchOptions) : ReaderAction
    data object NextSearchResult : ReaderAction
    data object PreviousSearchResult : ReaderAction
    data object ToggleBookmark : ReaderAction
    data class ToggleBookmarkAtLocator(
        val locator: ReaderLocator,
        val title: String? = null,
        val preview: String? = null
    ) : ReaderAction
    data class SettingsChanged(val settings: ReaderSettings) : ReaderAction
    data class RenderModeChanged(val renderMode: RenderMode) : ReaderAction
    data class ThemeChanged(val theme: ReaderTheme) : ReaderAction
    data class FormatChanged(val settings: FormatSettings) : ReaderAction
    data class HighlightCreated(val highlight: UserHighlight) : ReaderAction
    data class HighlightUpdated(
        val highlightId: String,
        val color: HighlightColor? = null,
        val note: String? = null
    ) : ReaderAction
    data class HighlightDeleted(val highlightId: String) : ReaderAction
}

sealed interface AppAction {
    data class BannerShown(val message: BannerMessage) : AppAction
    data object BannerDismissed : AppAction
    data class NavigationRequested(val event: NavigationEvent) : AppAction
    data class AppThemeChanged(val mode: AppThemeMode) : AppAction
    data class AppContrastChanged(val option: AppContrastOption) : AppAction
    data class AppTextDimFactorLightChanged(val factor: Float) : AppAction
    data class AppTextDimFactorDarkChanged(val factor: Float) : AppAction
    data class AppSeedColorChanged(val color: Color?) : AppAction
    data class AppFontPreferenceChanged(val preference: AppFontPreference) : AppAction
    data class CustomAppThemeAdded(val theme: CustomAppTheme) : AppAction
    data class CustomAppThemeDeleted(val themeId: String) : AppAction
    data class CustomReaderThemesChanged(val themes: List<ReaderTheme>) : AppAction
    data class SyncEnabledChanged(val enabled: Boolean) : AppAction
    data class FolderSyncEnabledChanged(val enabled: Boolean) : AppAction
    data class TabsEnabledChanged(val enabled: Boolean) : AppAction
    data class BookTabOpened(val bookId: String) : AppAction
    data class BookTabClosed(val bookId: String) : AppAction
    data object AllTabsClosed : AppAction
    data class HomePinToggled(val bookId: String) : AppAction
    data class LibraryPinToggled(val bookId: String) : AppAction
    data class ReaderDefaultSettingsChanged(val settings: ReaderSettings) : AppAction
    data class PdfReaderDefaultSettingsChanged(val settings: ReaderSettings) : AppAction
    data class ReaderToolbarPreferencesChanged(val preferences: ReaderToolbarPreferences) : AppAction
    data class ReaderToolVisibilityChanged(val tool: ReaderTool, val hidden: Boolean) : AppAction
    data class ReaderToolPlacementChanged(val tool: ReaderTool, val bottom: Boolean) : AppAction
    data class ReaderToolOrderChanged(val toolOrder: List<ReaderTool>) : AppAction
    data class ReaderHighlightPaletteChanged(val palette: ReaderHighlightPalette) : AppAction
    data class PdfHighlighterPaletteChanged(val palette: SharedPdfHighlighterPalette) : AppAction
    data class ReaderTtsReplacementPreferencesChanged(
        val preferences: ReaderTtsReplacementPreferences,
    ) : AppAction
}
