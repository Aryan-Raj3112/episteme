package com.aryan.reader.shared.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.DockLocation
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.SharedSummaryCache
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.reader.DefaultPdfReaderSettings
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.pdf.SharedPdfOcrLanguage
import com.aryan.reader.shared.pdf.buildPdfAiHubRecapText
import com.aryan.reader.shared.pdf.PDF_AI_HUB_MAX_CHARS
import com.aryan.reader.shared.pdf.PDF_AI_HUB_RECAP_PAGE_WINDOW
import com.aryan.reader.shared.PdfToolbarPreferences
import com.aryan.reader.shared.isPdfReaderToolEnabledDuringTts
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.resolveReaderTheme
import com.aryan.reader.pdf.shouldShowPdfAnnotationExportChoice
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfDrawerCapabilities
import com.aryan.reader.shared.pdf.PdfDrawerSection
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfTtsSessionPlanner
import com.aryan.reader.shared.pdf.shouldStopPdfTtsForManualPageTurn
import com.aryan.reader.shared.pdf.shouldStopPdfTtsForNavigation
import com.aryan.reader.shared.pdf.PdfAutoScrollProfile
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.initialSharedPdfReaderState
import com.aryan.reader.shared.pdf.PdfNavigationReason
import com.aryan.reader.shared.pdf.pdfDrawerSections
import com.aryan.reader.shared.pdf.PdfChromeMotionDurationMillis
import com.aryan.reader.shared.pdf.animatesPagination
import com.aryan.reader.shared.pdf.PdfZoomCamera
import com.aryan.reader.shared.pdf.PdfZoomPoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAnnotationHighlighterTools
import com.aryan.reader.shared.pdf.SharedPdfAnnotationPenTools
import com.aryan.reader.shared.pdf.isPdfTextDockTopAnchored
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockInBottomHalf
import com.aryan.reader.shared.pdf.sharedPdfPopupMaxHeightDp
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockSticky
import com.aryan.reader.shared.pdf.pdfTextDockKeyboardLiftPx
import com.aryan.reader.shared.pdf.pdfTextDockRestingBottomPadding
import com.aryan.reader.shared.pdf.sharedPdfAnnotationDockTopYPx
import com.aryan.reader.shared.pdf.shouldShowPdfTextDock
import com.aryan.reader.shared.pdf.toSharedPdfRichSpanStyle
import com.aryan.reader.shared.pdf.SharedPdfRichListType
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfDemoAnnotations
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfInkSelection
import com.aryan.reader.shared.pdf.SharedPdfSelectionTransform
import com.aryan.reader.shared.pdf.applySharedPdfSelectionTransform
import com.aryan.reader.shared.pdf.findSharedPdfSelectionHandleHit
import com.aryan.reader.shared.pdf.sharedPdfSelectionHandlePositions
import com.aryan.reader.shared.pdf.sharedPdfSelectionTouchSlopPx
import com.aryan.reader.shared.pdf.sharedPdfSelectionUnionBounds
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.toAnnotation
import com.aryan.reader.shared.pdf.withStyle
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfReaderGlobalResource
import com.aryan.reader.shared.pdf.SharedPdfReaderHostConfig
import com.aryan.reader.shared.pdf.SharedPdfReaderSessionKey
import com.aryan.reader.shared.pdf.SharedPdfExportSnapshot
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfVirtualPage
import com.aryan.reader.shared.pdf.buildSharedPdfVirtualPageLayout
import com.aryan.reader.shared.pdf.sharedPdfPdfPageIndexAt
import com.aryan.reader.shared.pdf.sharedPdfDisplayIndexFor
import com.aryan.reader.shared.pdf.sharedPdfNearestPdfPageIndex
import com.aryan.reader.shared.pdf.sharedPdfResolveInkStrokeOwner
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfPageRangeLabel
import com.aryan.reader.shared.pdf.SharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.pdf.sharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.pdf.loadSharedPdfCustomFontFamilies
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.mobilePdfSystemBarsVisibility
import com.aryan.reader.shared.reader.shouldPadPdfVerticalContentBelowStatusBar
import com.aryan.reader.shared.reader.captureCurrentPdfHistoryPage
import com.aryan.reader.shared.reader.capturePdfJumpHistoryOrigin
import com.aryan.reader.shared.SystemUiMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class SharedMobilePdfNativeAction {
    DICTIONARY_SETTINGS,
    SHARE,
    SHARE_ANNOTATED,
    SHARE_ORIGINAL,
    SAVE_COPY,
    PRINT,
    TEXT_VIEW,
}

data class SharedMobilePdfReflowUiState(
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val hasReflowBook: Boolean = false,
)

/**
 * Compatibility entry point for the original full-screen reader.
 *
 * The actual implementation lives in [SharedMobilePdfReaderHost] so a split
 * workspace can mount the same reader with a distinct session key and focus
 * ownership without duplicating reader behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobilePdfReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onOpenSplit: (() -> Unit)? = null,
    onNativePdfAction: (BookItem, SharedMobilePdfNativeAction, password: String?, SharedPdfExportSnapshot) -> Unit,
    pdfReflowUiState: SharedMobilePdfReflowUiState = SharedMobilePdfReflowUiState(),
    pdfTabsEnabled: Boolean = false,
    openPdfTabs: List<BookItem> = emptyList(),
    activePdfTabBookId: String? = null,
    availablePdfTabBooks: List<BookItem> = emptyList(),
    pdfTopTabStripVisible: Boolean = true,
    onPdfTopTabStripVisibilityChange: (Boolean) -> Unit = {},
    onOpenPdfTab: (BookItem) -> Unit = {},
    onClosePdfTab: (BookItem) -> Unit = {},
    onBookInfoChange: (BookItem) -> Unit = {},
    knownTags: List<Tag> = emptyList(),
    pdfToolbarPreferences: PdfToolbarPreferences = PdfToolbarPreferences(),
    onPdfToolbarPreferencesChange: (PdfToolbarPreferences) -> Unit = {},
    ocrLanguage: SharedPdfOcrLanguage = SharedPdfOcrLanguage.LATIN,
    onOcrLanguageChange: (SharedPdfOcrLanguage) -> Unit = {},
    readerBrightness: Float? = null,
    readerCustomBrightness: Float = com.aryan.reader.shared.DefaultReaderCustomBrightness,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    readerAiAvailable: Boolean = false,
    readerExtrasState: ReaderExtrasState = ReaderExtrasState(),
    cloudTts: SharedMobileEpubCloudTts? = null,
    cloudTtsModeEnabled: Boolean = false,
    onCloudTtsModeChange: (Boolean) -> Unit = {},
    cloudTtsVoiceId: String = com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID,
    onCloudTtsVoiceChange: (String) -> Unit = {},
    onClearCloudTtsCache: () -> Unit = {},
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onAiResultDismiss: () -> Unit = {},
    onOpenAiHub: () -> Unit = {},
    onTtsError: ((String) -> Unit)? = null,
    onClipboardError: ((String) -> Unit)? = null,
    onPasswordProtectedPrint: (String) -> Unit = {},
    initialReaderState: SharedPdfReaderState? = null,
    readerDefaultSettings: ReaderSettings = DefaultPdfReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    onPdfHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit = {},
    pdfHighlighterSnapEnabled: Boolean = false,
    onPdfHighlighterSnapChange: (Boolean) -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onImportFont: () -> Unit = {},
    initialKeepScreenOn: Boolean = false,
    onKeepScreenOnPreferenceChange: (Boolean) -> Unit = {},
    initialStylusOnlyMode: Boolean = false,
    onStylusOnlyModePreferenceChange: (Boolean) -> Unit = {},
    initialPageSliderVisible: Boolean = false,
    onPageSliderVisibilityPreferenceChange: (Boolean) -> Unit = {},
    initialTtsOverlaySize: SharedPdfTtsOverlaySize = SharedPdfTtsOverlaySize.LARGE,
    onTtsOverlaySizePreferenceChange: (SharedPdfTtsOverlaySize) -> Unit = {},
    onReaderStateChange: (SharedPdfReaderState) -> Unit = {},
    pdfAutoScrollGlobalProfile: PdfAutoScrollProfile = PdfAutoScrollProfile(),
    onPdfAutoScrollGlobalProfileChange: (PdfAutoScrollProfile) -> Unit = {},
    initialPdfAutoScrollMusicianMode: Boolean = false,
    onPdfAutoScrollMusicianModeChange: (Boolean) -> Unit = {},
    initialPdfAutoScrollUseSlider: Boolean = false,
    onPdfAutoScrollUseSliderChange: (Boolean) -> Unit = {},
    onPdfAutoScrollBookChange: (BookItem) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (hidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit = { _, _, _, _ -> },
    onSystemUiRelease: () -> Unit = {},
    modifier: Modifier = Modifier,
    summaryCache: SharedSummaryCache? = null,
    aiCredits: Int? = null,
    initialShowTopToolbar: Boolean = true,
    onShowTopToolbarChange: (Boolean) -> Unit = {},
    initialShowBottomToolbar: Boolean = true,
    onShowBottomToolbarChange: (Boolean) -> Unit = {},
    isPdfExportBusy: Boolean = false,
    isDebugBuild: Boolean = false,
) {
    SharedMobilePdfReaderHost(
        book = book,
        onBack = onBack,
        onOpenSplit = onOpenSplit,
        onNativePdfAction = onNativePdfAction,
        pdfReflowUiState = pdfReflowUiState,
        pdfTabsEnabled = pdfTabsEnabled,
        openPdfTabs = openPdfTabs,
        activePdfTabBookId = activePdfTabBookId,
        availablePdfTabBooks = availablePdfTabBooks,
        pdfTopTabStripVisible = pdfTopTabStripVisible,
        onPdfTopTabStripVisibilityChange = onPdfTopTabStripVisibilityChange,
        onOpenPdfTab = onOpenPdfTab,
        onClosePdfTab = onClosePdfTab,
        onBookInfoChange = onBookInfoChange,
        knownTags = knownTags,
        pdfToolbarPreferences = pdfToolbarPreferences,
        onPdfToolbarPreferencesChange = onPdfToolbarPreferencesChange,
        initialShowTopToolbar = initialShowTopToolbar,
        onShowTopToolbarChange = onShowTopToolbarChange,
        initialShowBottomToolbar = initialShowBottomToolbar,
        onShowBottomToolbarChange = onShowBottomToolbarChange,
        isPdfExportBusy = isPdfExportBusy,
        ocrLanguage = ocrLanguage,
        onOcrLanguageChange = onOcrLanguageChange,
        readerBrightness = readerBrightness,
        readerCustomBrightness = readerCustomBrightness,
        onReaderBrightnessChange = onReaderBrightnessChange,
        readerScreenOrientationMode = readerScreenOrientationMode,
        onReaderScreenOrientationModeChange = onReaderScreenOrientationModeChange,
        onApplyReaderScreenOrientation = onApplyReaderScreenOrientation,
        readerTtsReplacementPreferences = readerTtsReplacementPreferences,
        onReaderTtsReplacementPreferencesChange = onReaderTtsReplacementPreferencesChange,
        readerAiAvailable = readerAiAvailable,
        readerExtrasState = readerExtrasState,
        cloudTts = cloudTts,
        cloudTtsModeEnabled = cloudTtsModeEnabled,
        onCloudTtsModeChange = onCloudTtsModeChange,
        cloudTtsVoiceId = cloudTtsVoiceId,
        onCloudTtsVoiceChange = onCloudTtsVoiceChange,
        onClearCloudTtsCache = onClearCloudTtsCache,
        onAiAction = onAiAction,
        onAiResultDismiss = onAiResultDismiss,
        onOpenAiHub = onOpenAiHub,
        summaryCache = summaryCache,
        aiCredits = aiCredits,
        onTtsError = onTtsError,
        onClipboardError = onClipboardError,
        onPasswordProtectedPrint = onPasswordProtectedPrint,
        initialReaderState = initialReaderState,
        readerDefaultSettings = readerDefaultSettings,
        onReaderDefaultSettingsChange = onReaderDefaultSettingsChange,
        pdfHighlighterPalette = pdfHighlighterPalette,
        onPdfHighlighterPaletteChange = onPdfHighlighterPaletteChange,
        pdfHighlighterSnapEnabled = pdfHighlighterSnapEnabled,
        onPdfHighlighterSnapChange = onPdfHighlighterSnapChange,
        customReaderThemes = customReaderThemes,
        onCustomReaderThemesChange = onCustomReaderThemesChange,
        customFonts = customFonts,
        onImportFont = onImportFont,
        initialKeepScreenOn = initialKeepScreenOn,
        onKeepScreenOnPreferenceChange = onKeepScreenOnPreferenceChange,
        initialStylusOnlyMode = initialStylusOnlyMode,
        onStylusOnlyModePreferenceChange = onStylusOnlyModePreferenceChange,
        initialPageSliderVisible = initialPageSliderVisible,
        onPageSliderVisibilityPreferenceChange = onPageSliderVisibilityPreferenceChange,
        initialTtsOverlaySize = initialTtsOverlaySize,
        onTtsOverlaySizePreferenceChange = onTtsOverlaySizePreferenceChange,
        onReaderStateChange = onReaderStateChange,
        pdfAutoScrollGlobalProfile = pdfAutoScrollGlobalProfile,
        onPdfAutoScrollGlobalProfileChange = onPdfAutoScrollGlobalProfileChange,
        initialPdfAutoScrollMusicianMode = initialPdfAutoScrollMusicianMode,
        onPdfAutoScrollMusicianModeChange = onPdfAutoScrollMusicianModeChange,
        initialPdfAutoScrollUseSlider = initialPdfAutoScrollUseSlider,
        onPdfAutoScrollUseSliderChange = onPdfAutoScrollUseSliderChange,
        onPdfAutoScrollBookChange = onPdfAutoScrollBookChange,
        onKeepScreenOnChange = onKeepScreenOnChange,
        onSystemUiAppearanceChange = onSystemUiAppearanceChange,
        onSystemUiRelease = onSystemUiRelease,
        modifier = modifier,
        hostConfig = SharedPdfReaderHostConfig.fullScreen(book.id),
        isDebugBuild = isDebugBuild,
    )
}

/**
 * Pane-safe shared PDF reader implementation.
 *
 * Split hosts should provide a session id from the workspace pane and update
 * [SharedPdfReaderHostConfig.isFocused] as focus moves between documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobilePdfReaderHost(
    book: BookItem,
    onBack: () -> Unit,
    onOpenSplit: (() -> Unit)? = null,
    onNativePdfAction: (BookItem, SharedMobilePdfNativeAction, password: String?, SharedPdfExportSnapshot) -> Unit,
    pdfReflowUiState: SharedMobilePdfReflowUiState = SharedMobilePdfReflowUiState(),
    pdfTabsEnabled: Boolean = false,
    openPdfTabs: List<BookItem> = emptyList(),
    activePdfTabBookId: String? = null,
    availablePdfTabBooks: List<BookItem> = emptyList(),
    pdfTopTabStripVisible: Boolean = true,
    onPdfTopTabStripVisibilityChange: (Boolean) -> Unit = {},
    onOpenPdfTab: (BookItem) -> Unit = {},
    onClosePdfTab: (BookItem) -> Unit = {},
    onBookInfoChange: (BookItem) -> Unit = {},
    knownTags: List<Tag> = emptyList(),
    pdfToolbarPreferences: PdfToolbarPreferences = PdfToolbarPreferences(),
    onPdfToolbarPreferencesChange: (PdfToolbarPreferences) -> Unit = {},
    ocrLanguage: SharedPdfOcrLanguage = SharedPdfOcrLanguage.LATIN,
    onOcrLanguageChange: (SharedPdfOcrLanguage) -> Unit = {},
    readerBrightness: Float? = null,
    readerCustomBrightness: Float = com.aryan.reader.shared.DefaultReaderCustomBrightness,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    readerAiAvailable: Boolean = false,
    readerExtrasState: ReaderExtrasState = ReaderExtrasState(),
    cloudTts: SharedMobileEpubCloudTts? = null,
    cloudTtsModeEnabled: Boolean = false,
    onCloudTtsModeChange: (Boolean) -> Unit = {},
    cloudTtsVoiceId: String = com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID,
    onCloudTtsVoiceChange: (String) -> Unit = {},
    onClearCloudTtsCache: () -> Unit = {},
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onAiResultDismiss: () -> Unit = {},
    onOpenAiHub: () -> Unit = {},
    onTtsError: ((String) -> Unit)? = null,
    onClipboardError: ((String) -> Unit)? = null,
    onPasswordProtectedPrint: (String) -> Unit = {},
    initialReaderState: SharedPdfReaderState? = null,
    readerDefaultSettings: ReaderSettings = DefaultPdfReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    onPdfHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit = {},
    pdfHighlighterSnapEnabled: Boolean = false,
    onPdfHighlighterSnapChange: (Boolean) -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onImportFont: () -> Unit = {},
    initialKeepScreenOn: Boolean = false,
    onKeepScreenOnPreferenceChange: (Boolean) -> Unit = {},
    initialStylusOnlyMode: Boolean = false,
    onStylusOnlyModePreferenceChange: (Boolean) -> Unit = {},
    initialPageSliderVisible: Boolean = false,
    onPageSliderVisibilityPreferenceChange: (Boolean) -> Unit = {},
    initialTtsOverlaySize: SharedPdfTtsOverlaySize = SharedPdfTtsOverlaySize.LARGE,
    onTtsOverlaySizePreferenceChange: (SharedPdfTtsOverlaySize) -> Unit = {},
    onReaderStateChange: (SharedPdfReaderState) -> Unit = {},
    onReaderSessionStateChange: (SharedPdfReaderSessionKey, SharedPdfReaderState) -> Unit = { _, _ -> },
    pdfAutoScrollGlobalProfile: PdfAutoScrollProfile = PdfAutoScrollProfile(),
    onPdfAutoScrollGlobalProfileChange: (PdfAutoScrollProfile) -> Unit = {},
    initialPdfAutoScrollMusicianMode: Boolean = false,
    onPdfAutoScrollMusicianModeChange: (Boolean) -> Unit = {},
    initialPdfAutoScrollUseSlider: Boolean = false,
    onPdfAutoScrollUseSliderChange: (Boolean) -> Unit = {},
    onPdfAutoScrollBookChange: (BookItem) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (hidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit = { _, _, _, _ -> },
    onSystemUiRelease: () -> Unit = {},
    modifier: Modifier = Modifier,
    hostConfig: SharedPdfReaderHostConfig = SharedPdfReaderHostConfig.fullScreen(book.id),
    initialShowTopToolbar: Boolean = true,
    onShowTopToolbarChange: (Boolean) -> Unit = {},
    initialShowBottomToolbar: Boolean = true,
    onShowBottomToolbarChange: (Boolean) -> Unit = {},
    isPdfExportBusy: Boolean = false,
    /** True when this reader is mounted inside a split-workspace pane. */
    isSplitPane: Boolean = false,
    summaryCache: SharedSummaryCache? = null,
    aiCredits: Int? = null,
    /**
     * Android parity (PdfToolbars includeDebugActions = BuildConfig.DEBUG):
     * debug builds expose the "Try Episteme" demo artwork action.
     */
    isDebugBuild: Boolean = false,
) {
    val readerSessionKey = hostConfig.sessionKey
    val ownsSystemUi = hostConfig.owns(SharedPdfReaderGlobalResource.SYSTEM_UI)
    val ownsKeepScreenOn = hostConfig.owns(SharedPdfReaderGlobalResource.KEEP_SCREEN_ON)
    val ownsKeyboardCommands = hostConfig.owns(SharedPdfReaderGlobalResource.KEYBOARD_COMMANDS)
    val ownsTts = hostConfig.owns(SharedPdfReaderGlobalResource.TTS)
    val ownsGlobalModal = hostConfig.owns(SharedPdfReaderGlobalResource.GLOBAL_MODAL)
    val ownsNativeAction = hostConfig.owns(SharedPdfReaderGlobalResource.NATIVE_ACTION)
    val customPdfFontFamilies = remember(customFonts) {
        loadSharedPdfCustomFontFamilies(customFonts)
    }
    key(readerSessionKey) {
        val pdfCardTitle = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current)
        val initialPage = book.lastPageIndex?.coerceAtLeast(0) ?: 0
    var readerState by remember(readerSessionKey, initialReaderState) {
        mutableStateOf(
            initialSharedPdfReaderState(
                persistedState = initialReaderState,
                defaults = readerDefaultSettings,
                initialPageIndex = initialPage,
                highlighterPalette = pdfHighlighterPalette,
                isHighlighterSnapEnabled = pdfHighlighterSnapEnabled,
            )
        )
    }
    // Android intentionally starts every PDF session distraction-free.
    var showChrome by remember(readerSessionKey) { mutableStateOf(false) }
    // Android benchmark (PdfViewerScreen.kt:439-440, PdfPreferences
    // PDF_TOP/BOTTOM_TOOLBAR_VISIBLE_KEY, default true): per-bar visibility.
    var showTopToolbar by remember(readerSessionKey, initialShowTopToolbar) {
        mutableStateOf(initialShowTopToolbar)
    }
    var showBottomToolbar by remember(readerSessionKey, initialShowBottomToolbar) {
        mutableStateOf(initialShowBottomToolbar)
    }
    var showReaderOptions by remember(readerSessionKey) { mutableStateOf(false) }
    var showThemePanel by remember(readerSessionKey) { mutableStateOf(false) }
    var showPageSlider by remember(readerSessionKey) { mutableStateOf(initialPageSliderVisible) }
    // Android parity (load/saveReaderTtsOverlaySize): the player size persists
    // across sessions instead of resetting to LARGE every open.
    var ttsOverlaySize by remember(readerSessionKey, initialTtsOverlaySize) {
        mutableStateOf(initialTtsOverlaySize)
    }
    var showFileInformation by remember(readerSessionKey) { mutableStateOf(false) }
    var showBrightnessSheet by remember(readerSessionKey) { mutableStateOf(false) }
    var showScreenOrientationSheet by remember(readerSessionKey) { mutableStateOf(false) }
    var showOcrLanguageDialog by remember(readerSessionKey) { mutableStateOf(false) }
    var showToolbarCustomization by remember(readerSessionKey) { mutableStateOf(false) }
    var showTtsSettingsSheet by remember(readerSessionKey) { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember(readerSessionKey) { mutableStateOf(false) }
    var showNewPdfTabSheet by remember(readerSessionKey) { mutableStateOf(false) }
    var showAiHub by remember(readerSessionKey) { mutableStateOf(false) }
    var aiCacheRevision by remember(readerSessionKey) { mutableIntStateOf(0) }
    var pendingSummarySave by remember(readerSessionKey) { mutableStateOf<Triple<String, Int, String>?>(null) }
    var pendingExternalLink by remember(readerSessionKey) { mutableStateOf<String?>(null) }
    var pdfPassword by remember(readerSessionKey) { mutableStateOf<String?>(null) }
    var pdfPasswordDraft by remember(readerSessionKey) { mutableStateOf("") }
    var showShareFormatChoice by remember(readerSessionKey) { mutableStateOf(false) }
    var showSaveFormatChoice by remember(readerSessionKey) { mutableStateOf(false) }
    var showVerticalPageGap by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.pdfVerticalPageGapVisible)
    }
    var showPageNumberOverlay by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.pdfPageNumberOverlayVisible)
    }
    var systemUiMode by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.systemUiMode.toSharedMobilePdfSystemUiMode())
    }
    var rightToLeftPagination by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.rightToLeftPagination)
    }
    var useTwoPageSpread by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE)
    }
    var firstPageStandaloneInSpread by remember(readerSessionKey) {
        mutableStateOf(readerDefaultSettings.pdfFirstPageStandaloneInSpread)
    }
    var globalTextureTransparency by remember(readerSessionKey) {
        mutableStateOf(1f - readerDefaultSettings.textureAlpha.coerceIn(0f, 1f))
    }
    var keepScreenOn by remember(readerSessionKey) { mutableStateOf(initialKeepScreenOn) }
    var isStylusOnlyMode by remember(readerSessionKey) { mutableStateOf(initialStylusOnlyMode) }
    // Android-parity annotation dock chrome (benchmark: PdfViewerScreen.kt dock
    // location / minimize / tool-settings popup). Floating offset is px-based
    // like Android's dockOffset; sticky TOP/BOTTOM docks ignore it.
    var annotationDockLocation by remember(readerSessionKey) { mutableStateOf(DockLocation.BOTTOM) }
    var annotationDockOffset by remember(readerSessionKey) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isAnnotationDockDragging by remember(readerSessionKey) { mutableStateOf(false) }
    var annotationSnapPreview by remember(readerSessionKey) { mutableStateOf<DockLocation?>(null) }
    var isAnnotationDockMinimized by remember(readerSessionKey) { mutableStateOf(false) }
    var showAnnotationToolSettings by remember(readerSessionKey) { mutableStateOf(false) }
    // Android parity (AnnotationSettingsRepository.selectedTool survives edit-mode
    // toggles): shared edit mode IS the selected tool (NONE = off), so the last
    // non-NONE tool is remembered here and restored when edit mode reopens —
    // e.g. highlighter stays selected after closing + reopening edit mode.
    var lastEditTool by remember(readerSessionKey, initialReaderState) {
        mutableStateOf(
            initialReaderState?.selectedTool?.takeIf { it != PdfInkTool.NONE } ?: PdfInkTool.PEN
        )
    }
    // Android-parity text dock placement (benchmark: PdfViewerScreen text dock
    // chrome): draggable TOP / BOTTOM / FLOATING like the pen dock, kept
    // in-memory like the pen dock above (Android persists both to prefs).
    var textDockLocation by remember(readerSessionKey) { mutableStateOf(DockLocation.BOTTOM) }
    var textDockOffset by remember(readerSessionKey) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isTextDockDragging by remember(readerSessionKey) { mutableStateOf(false) }
    var textDockMeasuredOffset by remember(readerSessionKey) { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var autoScrollModeActive by remember(readerSessionKey) { mutableStateOf(false) }
    var autoScrollPlaying by remember(readerSessionKey) { mutableStateOf(false) }
    var autoScrollTemporarilyPaused by remember(readerSessionKey) { mutableStateOf(false) }
    var autoScrollIsLocal by remember(readerSessionKey) { mutableStateOf(book.pdfAutoScrollIsLocal) }
    var autoScrollProfile by remember(readerSessionKey) {
        mutableStateOf(
            if (book.pdfAutoScrollIsLocal && book.pdfAutoScrollLocalSpeed != null) {
                PdfAutoScrollProfile(
                    speed = book.pdfAutoScrollLocalSpeed,
                    minSpeed = book.pdfAutoScrollLocalMinSpeed ?: 0.1f,
                    maxSpeed = book.pdfAutoScrollLocalMaxSpeed ?: 10f,
                ).sanitized()
            } else {
                pdfAutoScrollGlobalProfile.sanitized()
            }
        )
    }
    var autoScrollMusicianMode by remember(readerSessionKey) { mutableStateOf(initialPdfAutoScrollMusicianMode) }
    var autoScrollUseSlider by remember(readerSessionKey) { mutableStateOf(initialPdfAutoScrollUseSlider) }
    var autoScrollCollapsed by remember(readerSessionKey) { mutableStateOf(false) }
    var autoScrollInteractionToken by remember(readerSessionKey) { mutableStateOf(0) }
    var autoScrollPauseDurationMillis by remember(readerSessionKey) { mutableStateOf(300L) }
    var tapToTurnPages by remember(readerSessionKey, readerDefaultSettings.tapToNavigateEnabled) {
        mutableStateOf(readerDefaultSettings.tapToNavigateEnabled)
    }
    var pageTurnAnimationEnabled by remember(readerSessionKey, readerDefaultSettings.pageTurnAnimationEnabled) {
        mutableStateOf(readerDefaultSettings.pageTurnAnimationEnabled)
    }
    var pdfSliderScrubbingPage by remember(readerSessionKey) { mutableStateOf<Int?>(null) }
    var showAllTextHighlights by remember(readerSessionKey) { mutableStateOf(false) }
    var isAllTextHighlightLoading by remember(readerSessionKey) { mutableStateOf(false) }
    fun toggleAllTextHighlights() {
        if (!showAllTextHighlights && !isAllTextHighlightLoading) {
            showAllTextHighlights = true
            isAllTextHighlightLoading = true
        } else if (showAllTextHighlights) {
            showAllTextHighlights = false
            isAllTextHighlightLoading = false
        }
    }
    val sanitizedPdfToolbarPreferences = pdfToolbarPreferences.sanitized(SharedMobilePdfAvailableTools)
    val visiblePdfTools = sanitizedPdfToolbarPreferences.toolOrder
        .filter(sanitizedPdfToolbarPreferences::isVisible)
        .filter { it != PdfReaderTool.AI_FEATURES || readerAiAvailable }
    val pdfTopTools = visiblePdfTools.filter { it.supportsToolbarPlacement && !sanitizedPdfToolbarPreferences.isBottom(it) }
    val pdfBottomTools = visiblePdfTools.filter { it.supportsToolbarPlacement && sanitizedPdfToolbarPreferences.isBottom(it) }

    fun updateAutoScrollProfile(profile: PdfAutoScrollProfile) {
        val sanitized = profile.sanitized()
        autoScrollProfile = sanitized
        if (autoScrollIsLocal) {
            onPdfAutoScrollBookChange(
                book.copy(
                    pdfAutoScrollIsLocal = true,
                    pdfAutoScrollLocalSpeed = sanitized.speed,
                    pdfAutoScrollLocalMinSpeed = sanitized.minSpeed,
                    pdfAutoScrollLocalMaxSpeed = sanitized.maxSpeed,
                )
            )
        } else {
            onPdfAutoScrollGlobalProfileChange(sanitized)
        }
    }

    fun setAutoScrollLocalMode(local: Boolean) {
        if (local == autoScrollIsLocal) return
        autoScrollIsLocal = local
        if (local) {
            val profile = if (book.pdfAutoScrollLocalSpeed != null) {
                PdfAutoScrollProfile(
                    book.pdfAutoScrollLocalSpeed,
                    book.pdfAutoScrollLocalMinSpeed ?: 0.1f,
                    book.pdfAutoScrollLocalMaxSpeed ?: 10f,
                ).sanitized()
            } else {
                autoScrollProfile
            }
            autoScrollProfile = profile
            onPdfAutoScrollBookChange(
                book.copy(
                    pdfAutoScrollIsLocal = true,
                    pdfAutoScrollLocalSpeed = profile.speed,
                    pdfAutoScrollLocalMinSpeed = profile.minSpeed,
                    pdfAutoScrollLocalMaxSpeed = profile.maxSpeed,
                )
            )
        } else {
            autoScrollProfile = pdfAutoScrollGlobalProfile.sanitized()
            onPdfAutoScrollBookChange(book.copy(pdfAutoScrollIsLocal = false))
        }
    }

    LaunchedEffect(readerSessionKey, pdfAutoScrollGlobalProfile, autoScrollIsLocal) {
        if (!autoScrollIsLocal) autoScrollProfile = pdfAutoScrollGlobalProfile.sanitized()
    }

    val latestOwnsSystemUi = rememberUpdatedState(ownsSystemUi)
    val latestApplyReaderScreenOrientation = rememberUpdatedState(onApplyReaderScreenOrientation)
    LaunchedEffect(readerSessionKey, readerScreenOrientationMode, ownsSystemUi) {
        if (ownsSystemUi) latestApplyReaderScreenOrientation.value(readerScreenOrientationMode)
    }
    DisposableEffect(readerSessionKey) {
        onDispose {
            if (latestOwnsSystemUi.value) {
                latestApplyReaderScreenOrientation.value(ReaderScreenOrientationMode.FOLLOW_SYSTEM)
            }
        }
    }
    var pdfZoomCamera by remember(readerSessionKey, initialReaderState) {
        mutableStateOf(
            initialReaderState?.takeIf { it.isScrollLocked }?.let {
                PdfZoomCamera(it.lockedZoomScale, PdfZoomPoint(it.lockedZoomOffsetX, it.lockedZoomOffsetY))
            } ?: PdfZoomCamera()
        )
    }
    var navigationRequestPage by remember(readerSessionKey) { mutableStateOf(readerState.pageIndex) }
    var navigationRequestToken by remember(readerSessionKey) { mutableStateOf(0) }
    var navigationCenterFraction by remember(readerSessionKey) { mutableStateOf(0.5f) }
    var navigationReason by remember(readerSessionKey) { mutableStateOf(PdfNavigationReason.INITIAL) }
    var jumpHistory by remember(readerSessionKey) { mutableStateOf(SharedPdfJumpHistory()) }
    val pdfTts = rememberSharedMobileEpubLocalTts()
    val cloudTtsState = cloudTts?.state ?: readerExtrasState.cloudTts
    val cloudTtsAvailable = cloudTts != null && cloudTtsState.isAvailable &&
        pdfTts.state == SharedMobileEpubLocalTtsState.IDLE
    LaunchedEffect(pdfTts.errorMessage, ownsTts) {
        if (ownsTts) pdfTts.errorMessage?.let { message -> onTtsError?.invoke(message) }
    }
    var ttsPageIndex by remember(readerSessionKey) { mutableStateOf(readerState.pageIndex) }
    var pendingTtsStart by remember(readerSessionKey) { mutableStateOf<Int?>(null) }
    var pendingTtsStartAtLastChunk by remember(readerSessionKey) { mutableStateOf(false) }
    var pendingTtsPlayWhenReady by remember(readerSessionKey) { mutableStateOf(true) }
    var ttsHighlightBounds by remember(readerSessionKey) { mutableStateOf<List<PdfPageBounds>>(emptyList()) }
    var lastTtsCompletionCount by remember(readerSessionKey) { mutableStateOf(pdfTts.completionCount) }
    var hasOwnedTts by remember(readerSessionKey) { mutableStateOf(false) }
    LaunchedEffect(readerSessionKey, ownsTts) {
        if (ownsTts) {
            hasOwnedTts = true
        } else if (hasOwnedTts) {
            // A pane that loses focus must not keep driving the process-global
            // speech engine after another pane becomes the owner.
            pdfTts.stop()
            cloudTts?.stop()
            hasOwnedTts = false
            pendingTtsStart = null
            pendingTtsStartAtLastChunk = false
            ttsHighlightBounds = emptyList()
        }
    }
    val ttsTextSession = rememberPdfTextPageSession(book, ttsPageIndex, pdfPassword)
    var searchResults by remember(readerSessionKey) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var tableOfContents by remember(readerSessionKey) { mutableStateOf<List<PdfTocEntry>>(emptyList()) }
    var noteAnnotationId by remember(readerSessionKey) { mutableStateOf<String?>(null) }
    var isSearchInProgress by remember(readerSessionKey) { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(readerSessionKey, ownsGlobalModal) {
        if (!ownsGlobalModal) {
            showReaderOptions = false
            showThemePanel = false
            showFileInformation = false
            showBrightnessSheet = false
            showScreenOrientationSheet = false
            showOcrLanguageDialog = false
            showToolbarCustomization = false
            showTtsSettingsSheet = false
            showTtsReplacementsSheet = false
            showNewPdfTabSheet = false
            showShareFormatChoice = false
            pendingExternalLink = null
            noteAnnotationId = null
        }
    }
    val pdfReaderFocusRequester = remember(readerSessionKey) { FocusRequester() }
    val pdfVerticalScrollController = remember(readerSessionKey) { SharedMobilePdfVerticalScrollController() }
    val pdfPaginationPositionController = remember(readerSessionKey) {
        SharedMobilePdfPaginationPositionController()
    }
    LaunchedEffect(readerSessionKey) {
        runCatching { pdfReaderFocusRequester.requestFocus() }
    }
    var richTextDocumentJson by remember(readerSessionKey) { mutableStateOf(initialReaderState?.richTextDocumentJson.orEmpty()) }
    // Android parity (RichTextSystem): the shared controller applies imported
    // font families to rich text and explicitly shows the software keyboard
    // when editing focus is requested; without the latter the iOS keyboard
    // never opens (focus alone is not enough there).
    val pdfKeyboardController = LocalSoftwareKeyboardController.current
    // Ref (not a remember key): recreating the controller on font import
    // would drop unsaved in-progress edits, so the font lookup always reads
    // the latest map through this holder instead.
    val latestCustomPdfFontFamilies = rememberUpdatedState(customPdfFontFamilies)
    val latestPdfKeyboardController = rememberUpdatedState(pdfKeyboardController)
    val richTextController = remember(readerSessionKey, initialReaderState) {
        SharedPdfRichTextController(
            scope = scope,
            initialDocument = SharedPdfRichTextSerializer.decode(initialReaderState?.richTextDocumentJson.orEmpty()),
            onDocumentChange = { document ->
                richTextDocumentJson = SharedPdfRichTextSerializer.encode(document)
            },
            styleForFontPath = { style, fontPath ->
                val families = latestCustomPdfFontFamilies.value
                style.copy(fontFamily = families[fontPath] ?: sharedPdfFontFamily(fontPath, families))
            },
            onEditingFocusRequested = { latestPdfKeyboardController.value?.show() },
        )
    }
    fun publishReaderState(state: SharedPdfReaderState) {
        if (hostConfig.acceptsCallback(readerSessionKey)) {
            onReaderStateChange(state)
            onReaderSessionStateChange(readerSessionKey, state)
        }
    }
    val closeReader = {
        if (ownsGlobalModal) {
            scope.saveAndCloseSharedMobilePdfReader(
                richTextController,
                readerState,
                { richTextDocumentJson },
                ::publishReaderState,
                onBack,
            )
        }
    }
    // A saved reverse-mode selection must not affect normal PDF themes. Keeping
    // this effective value at the host boundary also prevents the shared Android
    // and iOS renderers from doing work for an inactive option.
    val effectiveReverseColorMode = if (readerState.themeId == "reverse") {
        readerState.reverseColorMode
    } else {
        PdfReverseColorMode.RGB
    }
    // Document metadata must not follow the visible page. A newly requested page starts with
    // SharedMobilePdfPageRender's loading value (pageCount = 1); using that transient value here
    // used to collapse the list/pager to page zero every time the user changed pages.
    val documentRender = rememberSharedMobilePdfPageRender(
        book,
        0,
        password = pdfPassword,
        reverseColorMode = effectiveReverseColorMode,
        preserveImageColors = readerState.preserveImageColors,
    )
    val pageCount = if (documentRender.bitmap != null || documentRender.errorMessage != null) {
        documentRender.pageCount.coerceAtLeast(1)
    } else {
        readerState.pageCount.coerceAtLeast(1)
    }
    val virtualLayout = remember(readerSessionKey, pageCount, readerState.blankPageInsertions) {
        buildSharedPdfVirtualPageLayout(pageCount, readerState.blankPageInsertions)
    }
    val displayPageCount = virtualLayout.size
    val currentPdfIndex = readerState.currentNearestPdfPageIndex ?: 0
    val currentPageRender = rememberSharedMobilePdfPageRender(
        book,
        currentPdfIndex,
        password = pdfPassword,
        reverseColorMode = effectiveReverseColorMode,
        preserveImageColors = readerState.preserveImageColors,
    )
    val isCurrentPageBlank = (virtualLayout.getOrNull(readerState.pageIndex) as? SharedPdfVirtualPage.BlankPage) != null
    val prefetchedTtsPageIndex = (ttsPageIndex + 1).coerceAtMost(pageCount - 1)
    val prefetchedTtsTextSession = rememberPdfTextPageSession(book, prefetchedTtsPageIndex, pdfPassword)
    val activeTheme = remember(readerSessionKey, readerState.themeId, customReaderThemes) {
        resolveReaderTheme(readerState.themeId, BuiltInPdfReaderThemes, customReaderThemes)
            ?: BuiltInPdfReaderThemes.first()
    }
    val systemBarColor = MaterialTheme.colorScheme.surface
    // A split-workspace pane never owns the system bars; the workspace host
    // keeps the status bar visible so its controls stay below it, matching
    // Android where the split screen keeps both system bars on screen.
    val hideSystemUi = if (isSplitPane) {
        false
    } else {
        when (systemUiMode) {
            SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> false
            SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> !showChrome
            SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> true
        }
    }
    val edgeToEdgeSystemUi = !isSplitPane && systemUiMode != SharedMobilePdfSystemUiMode.ALWAYS_SHOW
    val density = LocalDensity.current
    val copiedTextLabel = readerString("clip_label_copied_text", "Copied Text")
    val copiedLinkLabel = readerString("clip_label_copied_link", "Copied Link")
    val clipboardErrorMessage = readerString("error_copy_to_clipboard", "Could not copy to clipboard")
    val passwordPrintBlockedMessage = readerString(
        "error_print_password_protected",
        "Password protected PDF files cannot be printed"
    )

    fun copyToClipboard(text: String, label: String = copiedTextLabel): SharedClipboardResult {
        val result = writeSharedClipboard(label = label, text = text)
        if (!result.success) onClipboardError?.invoke(clipboardErrorMessage)
        return result
    }
    val systemNavigationInset = with(density) {
        WindowInsets.safeDrawing.getBottom(density).toDp()
    }
    val effectiveBottomSystemInset = if (isSplitPane) {
        systemNavigationInset
    } else {
        when (systemUiMode) {
            SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> systemNavigationInset
            SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> if (showChrome) systemNavigationInset else 0.dp
            SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> 0.dp
        }
    }
    val pdfBottomChromePadding = sharedMobilePdfBottomChromePadding(effectiveBottomSystemInset, isSplitPane)
    // Android parity: standard top/bottom bars hide while annotating
    // (benchmark: showStandardBars = showBars && !isEditMode). The floating
    // annotation dock overlay owns edit-mode chrome instead.
    val isPdfEditMode = readerState.selectedTool != PdfInkTool.NONE
    // Android parity: the text dock observes the IME (resting above the
    // keyboard, floating-bar lift) exactly like the benchmark.
    // WindowInsets.isImeVisible is Android-only in this CMP version, so
    // visibility is derived from a non-zero IME inset instead — equivalent
    // for open/closed and gracefully false where IME insets are unsupported.
    val isPdfImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isJumpHistoryVisible = showChrome && !readerState.isSearchActive && !isPdfEditMode && jumpHistory.hasJumpTargets
    val isPdfTtsPlayingOrLoading =
        pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING || pendingTtsStart != null ||
            cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused
    val pdfSliderBottomPadding = sharedMobilePdfSliderBottomPadding(pdfBottomChromePadding, isJumpHistoryVisible)
    // In Always Show mode vertical content is anchored below the status bar
    // so the first page never draws underneath it. In Sync with Menus the
    // content stays edge-to-edge under the status bar so showing the menu
    // does not push the page down. Split panes already sit below the
    // workspace toolbar, so they never pad. Pagination is unchanged.
    val pdfVerticalContentBelowStatusBar = shouldPadPdfVerticalContentBelowStatusBar(
        mode = systemUiMode.toReaderSystemUiMode(),
        standardReaderChromeVisible = showChrome,
        isVerticalMode = readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL,
        isSplitPane = isSplitPane,
    )
    val latestSystemUiAppearanceChange = rememberUpdatedState(onSystemUiAppearanceChange)
    val latestSystemUiRelease = rememberUpdatedState(onSystemUiRelease)
    LaunchedEffect(readerSessionKey, hideSystemUi, systemBarColor, edgeToEdgeSystemUi, ownsSystemUi) {
        if (ownsSystemUi) {
            latestSystemUiAppearanceChange.value(
                hideSystemUi,
                systemBarColor.luminance() < 0.5f,
                systemBarColor.toArgb().toLong(),
                edgeToEdgeSystemUi
            )
        }
    }
    DisposableEffect(readerSessionKey) {
        onDispose {
            if (latestOwnsSystemUi.value) {
                latestSystemUiRelease.value()
            }
        }
    }
    var canvasSize by remember(readerSessionKey) { mutableStateOf(IntSize.Zero) }
    val activeStroke = remember(readerSessionKey, readerState.pageIndex) { mutableStateListOf<PdfPagePoint>() }
    // Single-stroke ownership (Android parity: one drawingState at a time).
    // The pdf page index that owns the in-flight stroke, or null when no
    // stroke is in flight. Any visible page may claim it, which is what
    // allows free drawing like Android; a second concurrent claim is
    // rejected so points from two pages can never interleave in one stroke.
    var activeStrokeOwnerPdfPage by remember(readerSessionKey) { mutableStateOf<Int?>(null) }
    // Android parity (PdfViewerScreen erasedAnnotationsFromStroke): ink hit by
    // the current erase drag, grouped per page. Live-removed immediately for
    // eraser feedback; recorded as ONE undo step on stroke end.
    val erasedInStroke = remember(readerSessionKey) { mutableStateMapOf<Int, MutableList<SharedPdfAnnotation>>() }
    // SELECT-tool state (benchmark: PdfViewerScreen inkSelection + gesture
    // snapshots + lasso trail). Snapshots hold gesture-start annotations for
    // absolute (drift-free) transforms; preview holds the live-dragged
    // versions rendered over the stored list until commit or revert.
    var inkSelection by remember(readerSessionKey) { mutableStateOf(SharedPdfInkSelection()) }
    var inkSelectionSnapshots by remember(readerSessionKey) { mutableStateOf<Map<String, SharedPdfAnnotation>>(emptyMap()) }
    var inkSelectionPreview by remember(readerSessionKey) { mutableStateOf<Map<String, SharedPdfAnnotation>>(emptyMap()) }
    var inkLassoPageIndex by remember(readerSessionKey) { mutableStateOf<Int?>(null) }
    var inkLassoTrail by remember(readerSessionKey) { mutableStateOf<List<Offset>>(emptyList()) }
    var activeSelectionRotation by remember(readerSessionKey) { mutableStateOf<Float?>(null) }
    // True while the screen-level handle overlay runs a transform session.
    // Pages stand down for that gesture (set at grab time, cleared when the
    // session ends, so a second finger mid-drag cannot start a rival page
    // session the way Android's single stream structurally prevents).
    var handleTransformInFlight by remember(readerSessionKey) { mutableStateOf(false) }
    // Page surface window rects keyed by PDF page index: anchors the floating
    // selection edit bar under the zoom transform.
    val selectionPageWindowRects = remember(readerSessionKey) { mutableStateMapOf<Int, Rect>() }
    var readerContainerWindowRect by remember(readerSessionKey) { mutableStateOf(Rect.Zero) }
    var readerContainerSize by remember(readerSessionKey) { mutableStateOf(IntSize.Zero) }

    // Declared with state (above its first caller in dock clicks): drops the
    // box/handles/edit bar and any in-flight gesture data.
    fun clearInkSelection() {
        pdfInkSelectionLog { "state.clear" }
        inkSelection = SharedPdfInkSelection()
        inkSelectionSnapshots = emptyMap()
        inkSelectionPreview = emptyMap()
        inkLassoPageIndex = null
        inkLassoTrail = emptyList()
        activeSelectionRotation = null
    }
    var textStyle by remember(readerSessionKey) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var textDraft by remember(readerSessionKey) { mutableStateOf<SharedPdfTextDraft?>(null) }
    // Android parity: minimized dock stops all annotation input
    // (isDrawingActive = isEditMode && !isDockMinimized).
    val isRichTextEditingEnabled =
        readerState.selectedTool == PdfInkTool.TEXT && textDraft == null && !isAnnotationDockMinimized
    val readerStateForPages =
        if (isAnnotationDockMinimized) readerState.copy(selectedTool = PdfInkTool.NONE) else readerState

    fun dispatch(action: SharedPdfReaderAction) {
        readerState = readerState.reduce(action)
    }

    // Android parity (PdfViewerScreen onGenerateDemoAnnotations + onImportSvg
    // demo_art.svg): debug-only "Try Episteme!" ink artwork on the current
    // pdf page. Each stroke dispatches AnnotationAdded so undo/redo + sidecar
    // persistence behave like hand-drawn ink.
    fun drawDebugDemoAnnotations() {
        val targetPage = readerState.currentNearestPdfPageIndex ?: currentPdfIndex
        val demo = SharedPdfDemoAnnotations.generate(
            pageIndex = targetPage,
            baseTimestamp = currentTimestamp(),
        )
        demo.forEach { dispatch(SharedPdfReaderAction.AnnotationAdded(it)) }
    }

    fun stopPdfTtsSession() {
        if (ownsTts) pdfTts.stop()
        if (ownsTts) cloudTts?.stop()
        pendingTtsStart = null
        pendingTtsStartAtLastChunk = false
        ttsHighlightBounds = emptyList()
    }

    fun dispatchNativePdfAction(
        action: SharedMobilePdfNativeAction,
        snapshot: SharedPdfExportSnapshot,
    ) {
        if (ownsNativeAction) onNativePdfAction(book, action, pdfPassword, snapshot)
    }

    fun normalizedPdfHistoryPage(pageIndex: Int): Int {
        if (readerState.displayMode != PdfDisplayMode.PAGINATION) return pageIndex
        return PdfSpreadLayout.normalizePageIndex(
            pageIndex = pageIndex,
            pageCount = displayPageCount,
            settings = ReaderSettings(
                pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
                pdfFirstPageStandaloneInSpread = firstPageStandaloneInSpread,
            ),
        )
    }

    /**
     * Captures the rendered viewport synchronously for jump history.
     *
     * The normal page callbacks are collected asynchronously. Reading the
     * renderer controller here avoids recording the previous callback value
     * when a manual scroll is followed immediately by a jump.
     */
    fun currentPdfHistoryPage(): Int {
        val rendered = if (readerState.displayMode == PdfDisplayMode.PAGINATION) {
            pdfPaginationPositionController.currentPageIndex()
        } else {
            pdfVerticalScrollController.currentPageIndex()
        }
        return captureCurrentPdfHistoryPage(
            renderedCurrentPage = rendered,
            fallbackCurrentPage = readerState.pageIndex,
            pageCount = displayPageCount,
            normalizePage = ::normalizedPdfHistoryPage,
        ) ?: 0
    }

    fun navigateToPage(
        pageIndex: Int,
        recordHistory: Boolean = true,
        centerFraction: Float = 0.5f,
        reason: PdfNavigationReason = PdfNavigationReason.PAGE_SLIDER
    ) {
        val target = pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        if (shouldStopPdfTtsForNavigation(
                readerState.displayMode == PdfDisplayMode.PAGINATION,
                reason, target != readerState.pageIndex, isPdfTtsPlayingOrLoading,
            )) {
            stopPdfTtsSession()
        }
        if (recordHistory) {
            capturePdfJumpHistoryOrigin(
                renderedCurrentPage = if (readerState.displayMode == PdfDisplayMode.PAGINATION) {
                    pdfPaginationPositionController.currentPageIndex()
                } else {
                    pdfVerticalScrollController.currentPageIndex()
                },
                fallbackCurrentPage = readerState.pageIndex,
                targetPage = target,
                pageCount = displayPageCount,
                normalizeCurrent = ::normalizedPdfHistoryPage,
                normalizeTarget = ::normalizedPdfHistoryPage,
            )?.let { origin ->
                jumpHistory = jumpHistory.record(
                    origin.currentPageIndex,
                    origin.targetPageIndex,
                    displayPageCount,
                )
            }
        }
        dispatch(SharedPdfReaderAction.GoToPage(target))
        navigationRequestPage = target
        navigationCenterFraction = centerFraction.coerceIn(0f, 1f)
        navigationReason = reason
        navigationRequestToken++
    }

    fun requestTts(
        pageIndex: Int = readerState.pageIndex,
        startCharIndex: Int = 0,
        startAtLastChunk: Boolean = false,
        playWhenReady: Boolean = true
    ) {
        if (!ownsTts) return
        cloudTts?.stop()
        val target = pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        pdfTts.prepare()
        ttsPageIndex = sharedPdfPdfPageIndexAt(virtualLayout, target)
            ?: sharedPdfNearestPdfPageIndex(virtualLayout, target)
            ?: target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pendingTtsStart = startCharIndex.coerceAtLeast(0)
        pendingTtsStartAtLastChunk = startAtLastChunk
        pendingTtsPlayWhenReady = playWhenReady
        navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.TTS)
    }

    fun toggleCloudTts() {
        val controller = cloudTts ?: return
        if (!ownsTts) return
        when {
            cloudTtsState.isPlaying || cloudTtsState.isLoading -> controller.pause()
            cloudTtsState.isPaused -> controller.resume()
            else -> {
                val session = ttsTextSession ?: return
                pdfTts.stop()
                val source = session.textForRange(0, session.pageCharCount).orEmpty()
                val planned = PdfTtsSessionPlanner.page(ttsPageIndex, source, 0)
                if (planned.chunks.isNotEmpty()) {
                    controller.start(planned.chunks, pdfCardTitle, book.id)
                }
            }
        }
    }

    fun stopPdfTtsForManualPagination() {
        if (shouldStopPdfTtsForManualPageTurn(
                readerState.displayMode == PdfDisplayMode.PAGINATION, true, isPdfTtsPlayingOrLoading,
            )) {
            stopPdfTtsSession()
        }
    }

    fun toggleDisplayMode() {
        navigationRequestPage = readerState.pageIndex
        navigationRequestToken++
        dispatch(SharedPdfReaderAction.DisplayModeToggled)
    }

    fun navigateToSearchResult(resultIndex: Int) {
        val result = searchResults.getOrNull(resultIndex) ?: return
        readerState = readerState.reduce(SharedPdfReaderAction.GoToSearchResult(resultIndex, searchResults))
        navigateToPage(
            sharedPdfDisplayIndexFor(virtualLayout, result.pageIndex),
            centerFraction = result.boundsList.centerYFraction(),
            reason = PdfNavigationReason.SEARCH_RESULT,
        )
    }

    fun insertBlankPageAtCurrentPosition() {
        val insertAt = readerState.pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        val aspectRatio = currentPageRender.aspectRatio.coerceIn(0.1f, 10f)
        dispatch(
            SharedPdfReaderAction.InsertBlankPageAt(
                displayIndex = insertAt,
                widthPx = 1000f,
                heightPx = 1000f / aspectRatio,
                id = "blank_${currentTimestamp()}"
            )
        )
        navigationRequestPage = readerState.pageIndex
        navigationCenterFraction = 0.5f
        navigationReason = PdfNavigationReason.PAGE_SLIDER
        navigationRequestToken++
    }

    fun deleteBlankPageAtCurrentPosition() {
        dispatch(SharedPdfReaderAction.DeleteBlankPageAt(readerState.pageIndex))
        navigationRequestPage = readerState.pageIndex
        navigationCenterFraction = 0.5f
        navigationReason = PdfNavigationReason.PAGE_SLIDER
        navigationRequestToken++
    }

    // Mirrors Android's onInsertTextBox: a fixed default box (0.4 x 0.1 at 0.3, 0.45) on the
    // current page, styled with the current text style. isManuallySized keeps the bounds fixed
    // while typing, matching Android's fixed PdfTextBox bounds.
    fun insertTextBox() {
        val pageIndex = readerState.currentNearestPdfPageIndex ?: 0
        textDraft = SharedPdfTextDraft(
            id = "ios_pdf_textbox_${currentTimestamp()}_${readerState.annotations.size}",
            pageIndex = pageIndex,
            bounds = PdfPageBounds(left = 0.3f, top = 0.45f, right = 0.7f, bottom = 0.55f),
            text = "",
            style = textStyle,
            createdAt = currentTimestamp(),
            isManuallySized = true
        )
    }

    fun startEditingTextBox(annotation: SharedPdfAnnotation) {
        val annotationStyle = annotation.sharedPdfTextStyle()
        textStyle = annotationStyle
        textDraft = SharedPdfTextDraft(
            id = annotation.id,
            pageIndex = annotation.pageIndex,
            bounds = annotation.bounds ?: PdfPageBounds(left = 0.3f, top = 0.45f, right = 0.7f, bottom = 0.55f),
            text = annotation.text,
            style = annotationStyle,
            createdAt = annotation.createdAt,
            isManuallySized = true
        )
    }

    fun updateTextDraft(draft: SharedPdfTextDraft) {
        textDraft = draft
    }

    // Mirrors Android's single-tap deselect: an empty box is removed, a non-empty one is kept.
    // New boxes are added; boxes that already exist as annotations are updated (or deleted when
    // their text was cleared, matching Android removing the empty box from the document).
    fun dismissTextDraft() {
        val draft = textDraft ?: return
        textDraft = null
        val isExisting = readerState.annotations.any { it.id == draft.id }
        if (SharedPdfTextAnnotationDefaults.normalizeTextDraft(draft.text).isBlank()) {
            if (isExisting) dispatch(SharedPdfReaderAction.AnnotationDeleted(draft.id))
            return
        }
        val annotation = draft.toAnnotation()
        if (isExisting) {
            dispatch(SharedPdfReaderAction.AnnotationUpdated(annotation))
        } else {
            dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        }
    }

    fun updatePdfHighlighterPalette(palette: SharedPdfHighlighterPalette) {
        val sanitized = palette.sanitized()
        dispatch(SharedPdfReaderAction.HighlighterPaletteChanged(sanitized.colors))
        onPdfHighlighterPaletteChange(sanitized)
    }

    fun updatePdfHighlighterSnap(enabled: Boolean) {
        dispatch(SharedPdfReaderAction.HighlighterSnapChanged(enabled))
        onPdfHighlighterSnapChange(enabled)
    }

    // Android parity (AnnotationSettingsRepository.updateSelectedTool): switching
    // tools only changes the selected tool (plus last-pen/highlighter recall in
    // the reducer). Per-tool color/width persist in toolConfigs and are recalled
    // by ToolSelected itself — never reset to defaults here, or the toolsettings
    // selection would not stick across switches.
    fun setTool(tool: PdfInkTool) {
        if (tool != PdfInkTool.TEXT && readerState.selectedTool == PdfInkTool.TEXT && textDraft != null) {
            dismissTextDraft()
        }
        if (tool != PdfInkTool.NONE) {
            lastEditTool = tool
        }
        dispatch(SharedPdfReaderAction.ToolSelected(tool))
    }

    // Android parity (edit toggle keeps the tool): opening edit mode restores
    // the last tool instead of always resetting to PEN; closing clears to NONE
    // but keeps lastEditTool for the next open.
    fun toggleEditMode() {
        setTool(if (readerState.selectedTool == PdfInkTool.NONE) lastEditTool else PdfInkTool.NONE)
    }

    // Android-parity annotation dock interactions (benchmark:
    // PdfViewerScreen.kt onToolClick + onClose). TEXT selects directly without
    // settings; re-tapping the active pen/highlighter/eraser toggles settings;
    // switching tools while settings are open re-opens them after 250ms so the
    // popup follows the new tool instead of sticking to the old one.
    fun onAnnotationDockToolClick(clicked: PdfInkTool) {
        if (clicked == PdfInkTool.TEXT) {
            setTool(PdfInkTool.TEXT)
            showAnnotationToolSettings = false
            clearInkSelection()
            return
        }
        // Android parity (AnnotationDock SELECT branch): activates directly,
        // no settings popup, keeps the current selection.
        if (clicked == PdfInkTool.SELECT) {
            setTool(PdfInkTool.SELECT)
            showAnnotationToolSettings = false
            return
        }
        if (readerState.selectedTool == clicked) {
            showAnnotationToolSettings = !showAnnotationToolSettings
            return
        }
        if (showAnnotationToolSettings) {
            showAnnotationToolSettings = false
            scope.launch {
                delay(250)
                setTool(clicked)
                clearInkSelection()
                showAnnotationToolSettings = true
            }
        } else {
            setTool(clicked)
            clearInkSelection()
        }
    }

    fun closeAnnotationDock() {
        showAnnotationToolSettings = false
        isAnnotationDockMinimized = false
        setTool(PdfInkTool.NONE)
        showChrome = true
    }

    val annotationDockPenColor: Color = run {
        val lastPen = readerState.lastActivePenTool
        val argb = if (readerState.selectedTool in SharedPdfAnnotationPenTools) {
            readerState.selectedColorArgb
        } else {
            readerState.toolConfigs[lastPen]?.colorArgb
                ?: SharedPdfAnnotationDefaults.configFor(lastPen).colorArgb
        }
        Color(argb)
    }
    val annotationDockHighlighterColor: Color = run {
        val lastHighlighter = readerState.lastActiveHighlighterTool
        val argb = if (readerState.selectedTool in SharedPdfAnnotationHighlighterTools) {
            readerState.selectedColorArgb
        } else {
            readerState.toolConfigs[lastHighlighter]?.colorArgb
                ?: SharedPdfAnnotationDefaults.configFor(lastHighlighter).colorArgb
        }
        Color(argb)
    }

    // Flushes one page's staged erase batch as a single undo step. Runs on
    // every stroke end (ink or eraser) so a batch left by an aborted erase
    // gesture still commits, mirroring onDrawEnd running both blocks.
    fun commitEraseBatch(pageIndex: Int) {
        val batch = erasedInStroke[pageIndex]?.toList().orEmpty()
        erasedInStroke.remove(pageIndex)
        if (batch.isNotEmpty()) {
            dispatch(SharedPdfReaderAction.EraserStrokeCommitted(mapOf(pageIndex to batch)))
        }
    }

    fun finishInkStroke(pageIndex: Int, eraserOverride: Boolean = false) {
        // The gesture is over: release stroke ownership on every path below.
        activeStrokeOwnerPdfPage = null
        // Android parity: minimized dock stops drawing
        // (isDrawingActive = isEditMode && !isDockMinimized).
        if (isAnnotationDockMinimized && !eraserOverride) {
            activeStroke.clear()
            return
        }
        val effectiveTool = if (eraserOverride) PdfInkTool.ERASER else readerState.selectedTool
        // Android parity (PdfViewerScreen onDrawEnd): an erase stroke never
        // creates ink. The live-removed batch is recorded as ONE undo step;
        // an empty batch preserves redo via the reducer's no-op guard.
        if (effectiveTool == PdfInkTool.ERASER || eraserOverride) {
            activeStroke.clear()
            commitEraseBatch(pageIndex)
            return
        }
        if (activeStroke.size < 2 || effectiveTool == PdfInkTool.NONE || effectiveTool == PdfInkTool.TEXT) {
            activeStroke.clear()
            commitEraseBatch(pageIndex)
            return
        }
        val annotation = SharedPdfAnnotation(
            id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.INK,
            tool = effectiveTool,
            points = activeStroke.toList(),
            colorArgb = readerState.selectedColorArgb,
            strokeWidth = readerState.strokeWidth,
            createdAt = currentTimestamp()
        )
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        activeStroke.clear()
        commitEraseBatch(pageIndex)
    }

    // Android parity (onDrawStartStable): a stroke beginning while the
    // tool-settings popup is open only dismisses the popup — the touch draws
    // nothing. A stroke starting on another page while one is already in
    // flight is likewise swallowed so points from two pages can never
    // interleave in the single shared stroke list. Returns true when the
    // stroke was swallowed.
    fun onInkStrokeStart(pageIndex: Int): Boolean {
        if (showAnnotationToolSettings) {
            showAnnotationToolSettings = false
            activeStroke.clear()
            activeStrokeOwnerPdfPage = null
            return true
        }
        if (sharedPdfResolveInkStrokeOwner(activeStrokeOwnerPdfPage, pageIndex) != pageIndex) {
            return true
        }
        activeStrokeOwnerPdfPage = pageIndex
        return false
    }

    // Releases a claim made in [onInkStrokeStart] when the gesture ends
    // without [finishInkStroke] (cancelled or swallowed), so a stale owner
    // can never block later strokes.
    fun onInkStrokeEnd(pageIndex: Int) {
        if (activeStrokeOwnerPdfPage == pageIndex) activeStrokeOwnerPdfPage = null
    }

    // Android parity (onDrawStable eraser branch): live-removes hit ink and
    // stages it in the per-page stroke batch for the end-of-stroke commit.
    // Unknown ids (already removed by an earlier move event's stale snapshot)
    // are ignored so a fast drag never double-counts.
    fun onEraseAnnotations(pageIndex: Int, annotationIds: Set<String>) {
        if (annotationIds.isEmpty() || isAnnotationDockMinimized) return
        val present = readerState.annotations.filter { it.pageIndex == pageIndex && it.id in annotationIds }
        if (present.isEmpty()) return
        val stagedIds = erasedInStroke.values.flatten().mapTo(mutableSetOf()) { it.id }
        val fresh = present.filterNot { it.id in stagedIds }
        if (fresh.isEmpty()) return
        erasedInStroke.getOrPut(pageIndex) { mutableListOf() }.addAll(fresh)
        dispatch(SharedPdfReaderAction.AnnotationsRemovedLive(fresh.mapTo(mutableSetOf()) { it.id }))
    }

    // SELECT-tool handlers (benchmark: PdfViewerScreen selection block).
    /** Selected annotations with the live transform preview applied. */
    fun selectedInkAnnotations(): List<SharedPdfAnnotation> {
        val page = inkSelection.pageIndex ?: return emptyList()
        val preview = inkSelectionPreview
        return readerState.annotations
            .filter { it.pageIndex == page && it.id in inkSelection.selectedIds }
            .map { preview[it.id] ?: it }
    }

    fun onSelectionTap(pageIndex: Int, annotationId: String?) {
        pdfInkSelectionLog { "state.tap page=$pageIndex id=$annotationId" }
        when {
            annotationId == null -> clearInkSelection()
            annotationId.isEmpty() -> Unit
            else -> {
                inkSelection = SharedPdfInkSelection(pageIndex, setOf(annotationId))
                inkSelectionSnapshots = emptyMap()
                inkSelectionPreview = emptyMap()
                inkLassoPageIndex = null
                inkLassoTrail = emptyList()
                activeSelectionRotation = null
            }
        }
    }

    fun onSelectionLasso(pageIndex: Int, annotationIds: Set<String>) {
        pdfInkSelectionLog { "state.lasso page=$pageIndex ids=$annotationIds" }
        inkSelection = SharedPdfInkSelection(pageIndex, annotationIds)
        inkSelectionSnapshots = emptyMap()
        inkSelectionPreview = emptyMap()
        inkLassoPageIndex = null
        inkLassoTrail = emptyList()
        activeSelectionRotation = null
    }

    fun onSelectionLassoProgress(pageIndex: Int, trail: List<Offset>?) {
        if (trail == null) {
            inkLassoPageIndex = null
            inkLassoTrail = emptyList()
        } else {
            inkLassoPageIndex = pageIndex
            inkLassoTrail = trail
        }
    }

    fun onSelectionTransformStart(pageIndex: Int) {
        val selected = readerState.annotations
            .filter { it.pageIndex == pageIndex && it.id in inkSelection.selectedIds }
        pdfInkSelectionLog { "state.transformStart page=$pageIndex snapshots=${selected.size}" }
        inkSelectionSnapshots = selected.associateBy { it.id }
        inkSelectionPreview = inkSelectionSnapshots
    }

    fun onSelectionTransformUpdate(
        pageIndex: Int,
        transform: SharedPdfSelectionTransform,
        aspectRatio: Float,
    ) {
        val snapshots = inkSelectionSnapshots
        if (snapshots.isEmpty()) return
        val updated = applySharedPdfSelectionTransform(
            annotations = snapshots.values.toList(),
            ids = inkSelection.selectedIds,
            transform = transform,
            pageAspectRatio = aspectRatio,
        )
        inkSelectionPreview = updated.associateBy { it.id }
        activeSelectionRotation = (transform as? SharedPdfSelectionTransform.Rotate)?.angleDegrees
    }

    fun onSelectionTransformEnd(commit: Boolean) {
        pdfInkSelectionLog {
            "state.transformEnd commit=$commit snapshots=${inkSelectionSnapshots.size} " +
                "preview=${inkSelectionPreview.size}"
        }
        val snapshots = inkSelectionSnapshots
        inkSelectionSnapshots = emptyMap()
        activeSelectionRotation = null
        if (commit && snapshots.isNotEmpty()) {
            val before = snapshots.values.toList()
            val after = before.map { inkSelectionPreview[it.id] ?: it }
            inkSelectionPreview = emptyMap()
            dispatch(SharedPdfReaderAction.SelectionTransformCommitted(before, after))
        } else {
            inkSelectionPreview = emptyMap()
        }
    }

    fun duplicateInkSelection() {
        val page = inkSelection.pageIndex ?: return
        val selected = selectedInkAnnotations()
        if (selected.isEmpty()) return
        val stamp = currentTimestamp()
        val copies = selected.mapIndexed { index, annotation ->
            annotation.copy(
                id = "shared_pdf_selection_copy_${stamp}_${index}_${annotation.id}",
                points = annotation.points.map { point ->
                    point.copy(
                        x = (point.x + 0.03f).coerceIn(0f, 1f),
                        y = (point.y + 0.03f).coerceIn(0f, 1f),
                    )
                },
            )
        }
        dispatch(SharedPdfReaderAction.SelectionDuplicatedCommitted(copies))
        inkSelection = SharedPdfInkSelection(page, copies.map { it.id }.toSet())
        inkSelectionSnapshots = emptyMap()
        inkSelectionPreview = emptyMap()
    }

    fun deleteInkSelection() {
        val selected = selectedInkAnnotations()
        if (selected.isEmpty()) return
        dispatch(SharedPdfReaderAction.SelectionDeletedCommitted(selected))
        clearInkSelection()
    }

    /**
     * Captures the gesture-start snapshot on the first style tick so slider
     * drags and spectrum swipes preview live and commit once (benchmark:
     * Android `selectionStyleSnapshot` + `StyleChange` on finish).
     */
    fun selectionStyleLive(map: (SharedPdfAnnotation) -> SharedPdfAnnotation) {
        if (inkSelectionSnapshots.isEmpty()) {
            val page = inkSelection.pageIndex ?: return
            if (inkSelection.selectedIds.isEmpty()) return
            inkSelectionSnapshots = readerState.annotations
                .filter { it.pageIndex == page }
                .associateBy { it.id }
        }
        inkSelectionPreview = inkSelectionSnapshots.values.map { annotation ->
            if (annotation.id in inkSelection.selectedIds) map(annotation) else annotation
        }.associateBy { it.id }
    }

    fun selectionStyleCommit() {
        val snapshots = inkSelectionSnapshots
        inkSelectionSnapshots = emptyMap()
        if (snapshots.isNotEmpty()) {
            val after = snapshots.values.map { inkSelectionPreview[it.id] ?: it }
            inkSelectionPreview = emptyMap()
            dispatch(SharedPdfReaderAction.SelectionTransformCommitted(snapshots.values.toList(), after))
        } else {
            inkSelectionPreview = emptyMap()
        }
    }

    /** Drops an uncommitted style preview (spectrum dismissed without save). */
    fun revertSelectionStyle() {
        inkSelectionSnapshots = emptyMap()
        inkSelectionPreview = emptyMap()
    }

    fun selectionColorLive(color: Color) {
        val rgb = color.toArgb() and 0x00FFFFFF
        selectionStyleLive { annotation ->
            // Android parity: recoloring a highlighter keeps its alpha.
            val keepAlpha = annotation.tool == PdfInkTool.HIGHLIGHTER ||
                annotation.tool == PdfInkTool.HIGHLIGHTER_ROUND
            val newArgb = if (keepAlpha) {
                (annotation.colorArgb and 0xFF000000.toInt()) or rgb
            } else {
                color.toArgb()
            }
            annotation.copy(colorArgb = newArgb)
        }
    }

    fun addTextHighlight(
        pageIndex: Int,
        range: com.aryan.reader.shared.pdf.PdfTextSelectionRange,
        text: String,
        bounds: List<PdfPageBounds>,
        colorArgb: Int,
        style: HighlightStyle,
        openNote: Boolean
    ) {
        val annotation = SharedPdfAnnotation(
            id = "ios_pdf_highlight_${currentTimestamp()}_${readerState.annotations.size}",
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            boundsList = bounds,
            text = text,
            colorArgb = colorArgb,
            highlightStyle = style,
            strokeWidth = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).strokeWidth,
            rangeStartIndex = range.start,
            rangeEndIndex = range.end,
            createdAt = currentTimestamp()
        )
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        if (openNote) {
            noteAnnotationId = annotation.id
        }
    }

    LaunchedEffect(readerSessionKey, pageCount) {
        if (readerState.pageCount != pageCount) {
            readerState = readerState.copy(pageCount = pageCount).coerced()
        }
    }

    // Android parity: tool-settings popup only applies to pen/highlighter/
    // eraser. Exiting edit mode or switching to TEXT always dismisses it, and
    // minimizing dismisses it so a dimmed dock never sits under an open popup.
    LaunchedEffect(readerSessionKey, readerState.selectedTool, isAnnotationDockMinimized) {
        if (readerState.selectedTool == PdfInkTool.NONE ||
            readerState.selectedTool == PdfInkTool.TEXT ||
            isAnnotationDockMinimized
        ) {
            showAnnotationToolSettings = false
        }
        if (isAnnotationDockMinimized) {
            activeStroke.clear()
            activeStrokeOwnerPdfPage = null
        }
    }

    // The stroke list above is keyed on the current page, so a page turn wipes
    // it; the owner must reset together or later strokes stay blocked.
    LaunchedEffect(readerSessionKey, readerState.pageIndex) {
        activeStrokeOwnerPdfPage = null
    }

    // Android parity (PdfViewerScreen LaunchedEffect(isEditMode)): leaving
    // SELECT mode drops the box/handles/edit bar so they never linger over
    // the reader after back nav, toolbar toggle, or dock close.
    LaunchedEffect(readerSessionKey, readerState.selectedTool) {
        if (readerState.selectedTool != PdfInkTool.SELECT) {
            clearInkSelection()
        }
    }

    // Mirrors Android's auto page management for the flowing rich text document
    // (PdfViewerScreen lines ~2208-2360): text overflowing the last page appends blank pages,
    // trailing auto-added blank pages with no content are pruned. Manual blank pages are kept.
    val highestRequiredTextPageIndex by remember(readerSessionKey, richTextController.pageLayouts) {
        derivedStateOf { richTextController.pageLayouts.maxOfOrNull { it.pageIndex } ?: -1 }
    }
    fun hasRichTextOnPage(pageIndex: Int): Boolean {
        return richTextController.pageLayouts.any {
            it.pageIndex == pageIndex && it.visibleText.isNotBlank()
        }
    }
    LaunchedEffect(readerSessionKey, highestRequiredTextPageIndex, displayPageCount, richTextController.pageLayouts) {
        if (richTextController.pageLayouts.isEmpty() || highestRequiredTextPageIndex < 0) return@LaunchedEffect
        delay(500)
        val requiredPages = highestRequiredTextPageIndex + 1
        if (requiredPages > displayPageCount) {
            val aspectRatio = currentPageRender.aspectRatio.coerceIn(0.1f, 10f)
            dispatch(
                SharedPdfReaderAction.InsertBlankPageAt(
                    displayIndex = displayPageCount,
                    widthPx = 1000f,
                    heightPx = 1000f / aspectRatio,
                    id = "auto_blank_${currentTimestamp()}",
                    wasManuallyAdded = false
                )
            )
        } else {
            while (true) {
                val layout = readerState.virtualPageLayout
                val lastIndex = layout.lastIndex
                if (lastIndex < 0) break
                val lastPage = layout[lastIndex] as? SharedPdfVirtualPage.BlankPage ?: break
                if (lastPage.insertion.wasManuallyAdded) break
                if (lastIndex <= highestRequiredTextPageIndex) break
                if (hasRichTextOnPage(lastIndex)) break
                dispatch(SharedPdfReaderAction.DeleteBlankPageAt(lastIndex))
            }
        }
    }

    LaunchedEffect(readerSessionKey, readerState, richTextDocumentJson) {
        val state = readerState.copy(richTextDocumentJson = richTextDocumentJson)
        publishReaderState(state)
    }

    LaunchedEffect(readerSessionKey, autoScrollInteractionToken) {
        if (autoScrollInteractionToken == 0 || !autoScrollPlaying) return@LaunchedEffect
        autoScrollTemporarilyPaused = true
        delay(autoScrollPauseDurationMillis)
        autoScrollTemporarilyPaused = false
    }

    LaunchedEffect(readerSessionKey, readerState.displayMode) {
        if (readerState.displayMode != PdfDisplayMode.VERTICAL_SCROLL) {
            autoScrollModeActive = false
            autoScrollPlaying = false
            autoScrollTemporarilyPaused = false
        }
    }

    LaunchedEffect(readerSessionKey, readerState.searchQuery) {
        val query = readerState.searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearchInProgress = false
            return@LaunchedEffect
        }
        delay(300)
        isSearchInProgress = true
        searchResults = withContext(Dispatchers.Default) {
            searchSharedMobilePdf(book, query, pdfPassword)
        }
        isSearchInProgress = false
    }

    LaunchedEffect(readerSessionKey, book.path, pdfPassword, documentRender.openError) {
        tableOfContents = if (documentRender.openError == null) {
            withContext(Dispatchers.Default) { loadSharedMobilePdfOutline(book, pdfPassword) }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(readerSessionKey, ttsTextSession, pendingTtsStart, ttsPageIndex, ownsTts) {
        if (!ownsTts) return@LaunchedEffect
        val start = pendingTtsStart ?: return@LaunchedEffect
        val session = ttsTextSession ?: return@LaunchedEffect
        val source = session.textForRange(0, session.pageCharCount).orEmpty()
        val planned = PdfTtsSessionPlanner.page(ttsPageIndex, source, start)
        if (planned.chunks.isEmpty()) {
            val next = PdfTtsSessionPlanner.nextPage(ttsPageIndex, pageCount)
            if (next == null) {
                pendingTtsStart = null
                pdfTts.stop()
            } else {
                ttsPageIndex = next
                pendingTtsStart = 0
                navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, next), recordHistory = false, reason = PdfNavigationReason.TTS)
            }
        } else {
            pendingTtsStart = null
            pdfTts.start(
                chunks = planned.chunks,
                bookTitle = pdfCardTitle,
                bookId = book.id,
                startChunkIndex = if (pendingTtsStartAtLastChunk) planned.chunks.lastIndex else 0,
                playWhenReady = pendingTtsPlayWhenReady
            )
            pendingTtsStartAtLastChunk = false
        }
    }

    LaunchedEffect(readerSessionKey, pdfTts.progress.currentChunk, ttsTextSession, ttsPageIndex, ownsTts) {
        if (!ownsTts) return@LaunchedEffect
        val session = ttsTextSession
        val range = PdfTtsSessionPlanner.highlightRange(pdfTts.progress.currentChunk, session?.pageCharCount ?: 0)
        ttsHighlightBounds = if (session != null && range != null) {
            session.rectsForRangeNormalized(range.start, range.length)
        } else {
            emptyList()
        }
        if (range != null) {
            navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex), recordHistory = false, centerFraction = ttsHighlightBounds.centerYFraction(), reason = PdfNavigationReason.TTS)
        }
    }

    LaunchedEffect(readerSessionKey, pdfTts.completionCount, ownsTts) {
        if (!ownsTts) return@LaunchedEffect
        if (pdfTts.completionCount == lastTtsCompletionCount) return@LaunchedEffect
        lastTtsCompletionCount = pdfTts.completionCount
        val next = PdfTtsSessionPlanner.nextPage(ttsPageIndex, pageCount)
        if (next != null) {
            ttsPageIndex = next
            navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, next), recordHistory = false, reason = PdfNavigationReason.TTS)
            val prefetched = prefetchedTtsTextSession.takeIf { prefetchedTtsPageIndex == next }
            val source = prefetched?.textForRange(0, prefetched.pageCharCount).orEmpty()
            val planned = PdfTtsSessionPlanner.page(next, source)
            if (planned.chunks.isNotEmpty()) {
                pendingTtsStart = null
                pdfTts.start(planned.chunks, pdfCardTitle, bookId = book.id)
            } else {
                pendingTtsStart = 0
            }
        } else {
            pdfTts.stop()
            ttsHighlightBounds = emptyList()
        }
    }

    val latestOwnsKeepScreenOn = rememberUpdatedState(ownsKeepScreenOn)
    val latestKeepScreenOnChange = rememberUpdatedState(onKeepScreenOnChange)
    LaunchedEffect(readerSessionKey, keepScreenOn, ownsKeepScreenOn) {
        if (ownsKeepScreenOn) latestKeepScreenOnChange.value(keepScreenOn)
    }

    LaunchedEffect(readerSessionKey, isStylusOnlyMode) {
        onStylusOnlyModePreferenceChange(isStylusOnlyMode)
    }

    DisposableEffect(readerSessionKey) {
        onDispose {
            if (latestOwnsKeepScreenOn.value) latestKeepScreenOnChange.value(false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SharedMobilePdfReaderDrawer(
                book = book,
                state = readerState,
                activeTheme = activeTheme,
                textureAlpha = 1f - globalTextureTransparency,
                tableOfContents = tableOfContents,
                onGoToPage = { page ->
                    navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, page), reason = PdfNavigationReason.TABLE_OF_CONTENTS)
                    scope.launch { drawerState.close() }
                },
                onEditNote = { annotation ->
                    noteAnnotationId = annotation.id
                    scope.launch { drawerState.close() }
                },
                onDeleteHighlight = { dispatch(SharedPdfReaderAction.AnnotationDeleted(it.id)) },
                onToggleBookmark = { dispatch(SharedPdfReaderAction.BookmarkToggled(currentPdfIndex, createdAt = currentTimestamp())) },
                onRenameBookmark = { pageIndex, label -> dispatch(SharedPdfReaderAction.BookmarkRenamed(pageIndex, label)) },
                onDeleteBookmark = { pageIndex -> dispatch(SharedPdfReaderAction.BookmarkDeleted(pageIndex)) },
                onGoToDisplayPage = { displayIndex ->
                    navigateToPage(displayIndex, reason = PdfNavigationReason.TABLE_OF_CONTENTS)
                    scope.launch { drawerState.close() }
                },
                pdfPassword = pdfPassword,
                tabsEnabled = pdfTabsEnabled,
                tabs = openPdfTabs,
                activeTabBookId = activePdfTabBookId,
                isTopTabStripVisible = pdfTopTabStripVisible,
                onTopTabStripVisibilityChange = onPdfTopTabStripVisibilityChange,
                onOpenTab = {
                    onOpenPdfTab(it)
                    scope.launch { drawerState.close() }
                },
                onCloseTab = onClosePdfTab,
                onNewTab = {
                    scope.launch { drawerState.close() }
                    showNewPdfTabSheet = true
                },
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = showChrome && !isPdfEditMode && showTopToolbar,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { -it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { -it } + fadeOut(tween(PdfChromeMotionDurationMillis))
                ) {
                    Column {
                        SharedMobilePdfReaderTopBar(
                            title = pdfCardTitle,
                            pageIndex = readerState.pageIndex,
                            pageLabel = sharedMobilePdfPageLabel(
                                readerState.pageIndex,
                                displayPageCount,
                                useTwoPageSpread && readerState.displayMode == PdfDisplayMode.PAGINATION,
                                firstPageStandaloneInSpread
                            ),
                            pageCount = displayPageCount,
                            displayMode = readerState.displayMode,
                            isSearchActive = readerState.isSearchActive,
                            searchQuery = readerState.searchQuery,
                            isBookmarked = readerState.bookmarks.any { it.pageIndex == currentPdfIndex },
                            onBack = closeReader,
                            onOpenSplit = onOpenSplit,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                            onSearchQueryChange = { query ->
                                dispatch(SharedPdfReaderAction.SearchChanged(query))
                            },
                            onCloseSearch = { dispatch(SharedPdfReaderAction.SearchClosed) },
                            onToggleBookmark = {
                                dispatch(SharedPdfReaderAction.BookmarkToggled(currentPdfIndex, createdAt = currentTimestamp()))
                            },
                            onToggleDisplayMode = ::toggleDisplayMode,
                            rightToLeftPagination = rightToLeftPagination,
                            onRightToLeftPaginationChange = {
                                rightToLeftPagination = it
                                onReaderDefaultSettingsChange(readerDefaultSettings.copy(rightToLeftPagination = it))
                            },
                            onTheme = { if (ownsGlobalModal) showThemePanel = true },
                            onVisualOptions = { if (ownsGlobalModal) showReaderOptions = !showReaderOptions },
                            tapToTurnPages = tapToTurnPages,
                            onToggleTapToTurnPages = {
                                val enabled = !tapToTurnPages
                                tapToTurnPages = enabled
                                onReaderDefaultSettingsChange(
                                    readerDefaultSettings.copy(tapToNavigateEnabled = enabled)
                                )
                            },
                            pageTurnAnimationEnabled = pageTurnAnimationEnabled,
                            onTogglePageTurnAnimation = {
                                val enabled = !pageTurnAnimationEnabled
                                pageTurnAnimationEnabled = enabled
                                onReaderDefaultSettingsChange(
                                    readerDefaultSettings.copy(pageTurnAnimationEnabled = enabled)
                                )
                            },
                            isScrollLocked = readerState.isScrollLocked,
                            onToggleScrollLock = {
                                dispatch(
                                    SharedPdfReaderAction.ScrollLockChanged(
                                        locked = !readerState.isScrollLocked,
                                        zoomScale = pdfZoomCamera.scale,
                                        offsetX = pdfZoomCamera.offset.x,
                                        offsetY = pdfZoomCamera.offset.y
                                    )
                                )
                            },
                            keepScreenOn = keepScreenOn,
                            onToggleKeepScreenOn = {
                                keepScreenOn = !keepScreenOn
                                onKeepScreenOnPreferenceChange(keepScreenOn)
                            },
                            autoScrollEnabled = autoScrollModeActive,
                            onToggleAutoScroll = {
                                autoScrollModeActive = true
                                autoScrollPlaying = true
                                autoScrollTemporarilyPaused = false
                                showChrome = !autoScrollMusicianMode
                            },
                            showAllTextHighlights = showAllTextHighlights,
                            isAllTextHighlightLoading = isAllTextHighlightLoading,
                            onToggleHighlights = ::toggleAllTextHighlights,
                            onHighlighterTool = { setTool(readerState.lastActiveHighlighterTool) },
                            onEditMode = ::toggleEditMode,
                            onShowSlider = {
                                // Android parity: opening the slider reveals the
                                // chrome (it renders in the bottom-chrome zone).
                                val opening = !showPageSlider
                                showPageSlider = opening
                                if (opening) showChrome = true
                                onPageSliderVisibilityPreferenceChange(showPageSlider)
                            },
                            onToggleTts = {
                                if (cloudTtsAvailable) {
                                    toggleCloudTts()
                                } else if (ownsTts) when (pdfTts.state) {
                                    SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                    SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                    SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                                }
                            },
                            onVoiceSettings = { if (ownsGlobalModal && ownsTts) showTtsSettingsSheet = true },
                            onWordReplacements = { if (ownsGlobalModal && ownsTts) showTtsReplacementsSheet = true },
                            onNativeAction = { action ->
                                if (ownsNativeAction) {
                                    if (action == SharedMobilePdfNativeAction.PRINT && pdfPassword != null) {
                                        // Android parity (print-blocked banner):
                                        // a transient host banner, not a modal
                                        // dialog, using the benchmark copy.
                                        onPasswordProtectedPrint(passwordPrintBlockedMessage)
                                    } else if (action == SharedMobilePdfNativeAction.SHARE && shouldShowPdfAnnotationExportChoice(
                                            sidecarsReady = true,
                                            inkAnnotationCounts = readerState.annotations
                                                .filter { it.kind == PdfAnnotationKind.INK }
                                                .groupBy { it.pageIndex }
                                                .values
                                                .map { it.size },
                                            textBoxCount = readerState.annotations.count { it.kind == PdfAnnotationKind.TEXT },
                                            highlightCount = readerState.annotations.count { it.kind == PdfAnnotationKind.HIGHLIGHT },
                                        )
                                    ) {
                                        showShareFormatChoice = true
                                    } else if (action == SharedMobilePdfNativeAction.SAVE_COPY) {
                                        showSaveFormatChoice = true
                                    } else {
                                        dispatchNativePdfAction(action, SharedPdfExportSnapshot(readerState.copy(richTextDocumentJson = richTextDocumentJson), richTextController.pageLayouts))
                                    }
                                }
                            },
                            onAiAction = { feature, text -> onAiAction(feature, text) },
                            onOpenAiHub = { showAiHub = true; onOpenAiHub() },
                            aiAvailable = readerAiAvailable,
                            ocrLanguage = ocrLanguage,
                            onOcrLanguage = { if (ownsGlobalModal) showOcrLanguageDialog = true },
                            isCurrentPageBlank = isCurrentPageBlank,
                            onInsertBlankPage = ::insertBlankPageAtCurrentPosition,
                            onDeleteBlankPage = ::deleteBlankPageAtCurrentPosition,
                            pdfReflowUiState = pdfReflowUiState,
                            isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                            onFileInformation = { if (ownsGlobalModal) showFileInformation = true },
                            onBrightness = { if (ownsGlobalModal) showBrightnessSheet = true },
                            onScreenOrientation = { if (ownsGlobalModal) showScreenOrientationSheet = true },
                            topTools = pdfTopTools,
                            toolbarPreferences = sanitizedPdfToolbarPreferences,
                            onCustomizeToolbar = { if (ownsGlobalModal) showToolbarCustomization = true },
                            // Panes already sit below the workspace toolbar, so
                            // only the topmost pane's bar should inset for the
                            // status bar; a second, stacked pane would otherwise
                            // double-pad its toolbar.
                            applySystemBarInsets = !isSplitPane && mobilePdfSystemBarsVisibility(
                                systemUiMode.toReaderSystemUiMode(),
                                showChrome,
                            ).statusBarsVisible,
                            isDebugBuild = isDebugBuild,
                            onDrawDebugDemoAnnotations = ::drawDebugDemoAnnotations,
                        )
                        if (
                            pdfTabsEnabled &&
                            pdfTopTabStripVisible &&
                            openPdfTabs.isNotEmpty() &&
                            !readerState.isSearchActive
                        ) {
                            SharedMobilePdfReaderTabStrip(
                                tabs = openPdfTabs,
                                activeBookId = activePdfTabBookId,
                                onOpenTab = onOpenPdfTab,
                                onCloseTab = onClosePdfTab,
                                onNewTab = { showNewPdfTabSheet = true },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = showChrome && !readerState.isSearchActive && !isPdfEditMode && showBottomToolbar,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis))
                ) {
                    SharedMobilePdfReaderBottomBar(
                        state = readerState,
                        tools = pdfBottomTools,
                        onShowSlider = {
                            val opening = !showPageSlider
                            showPageSlider = opening
                            if (opening) showChrome = true
                        },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onToolSelected = ::setTool,
                        editModeOpenTool = lastEditTool,
                        ttsState = if (cloudTtsAvailable) {
                            if (cloudTtsState.isPlaying) SharedMobileEpubLocalTtsState.SPEAKING
                            else if (cloudTtsState.isPaused || cloudTtsState.isLoading) SharedMobileEpubLocalTtsState.PAUSED
                            else SharedMobileEpubLocalTtsState.IDLE
                        } else pdfTts.state,
                        isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                        onToggleTts = {
                            if (cloudTtsAvailable) {
                                toggleCloudTts()
                            } else if (ownsTts) when (pdfTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                            }
                        },
                        onTheme = { if (ownsGlobalModal) showThemePanel = true },
                        onBrightness = { if (ownsGlobalModal) showBrightnessSheet = true },
                        onToggleScrollLock = {
                            dispatch(
                                SharedPdfReaderAction.ScrollLockChanged(
                                    locked = !readerState.isScrollLocked,
                                    zoomScale = pdfZoomCamera.scale,
                                    offsetX = pdfZoomCamera.offset.x,
                                    offsetY = pdfZoomCamera.offset.y
                                )
                            )
                        },
                        onScreenOrientation = { if (ownsGlobalModal) showScreenOrientationSheet = true },
                        onDictionary = { dispatchNativePdfAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS, SharedPdfExportSnapshot(readerState)) },
                        onOpenAiHub = { showAiHub = true; onOpenAiHub() },
                        aiAvailable = readerAiAvailable,
                        showAllTextHighlights = showAllTextHighlights,
                        isAllTextHighlightLoading = isAllTextHighlightLoading,
                        onToggleHighlights = ::toggleAllTextHighlights,
                        applySystemBarInsets = !isSplitPane && systemUiMode != SharedMobilePdfSystemUiMode.ALWAYS_HIDE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { _ ->
            // SELECT host bundle (single param per page-container layer).
            // Function references are stable; the page detector reads state
            // through updated-state so live previews never cancel in-flight
            // gestures. Built below, after the selection geometry it needs.
            // Edit-bar anchor inputs: selection union bounds in the content
            // box's px, mapped from the selection page's window rect under
            // the zoom transform.
            val selectionEditState = remember(inkSelection, readerState.annotations, inkSelectionPreview) {
                val selected = selectedInkAnnotations()
                SharedPdfSelectionEditState(
                    selected = selected,
                    union = sharedPdfSelectionUnionBounds(selected),
                    color = selected.map { it.colorArgb and 0x00FFFFFF }.distinct().singleOrNull()?.let {
                        Color(selected.first().colorArgb)
                    },
                    thickness = selected.firstOrNull()?.strokeWidth,
                    thicknessRange = selected.firstOrNull()?.tool?.sharedPdfStrokeWidthRange()
                        ?: (0.001f..0.015f),
                )
            }
            val selectionBarRect = remember(
                selectionEditState.union,
                inkSelection.pageIndex,
                selectionPageWindowRects,
                readerContainerWindowRect,
            ) {
                val page = inkSelection.pageIndex
                val union = selectionEditState.union
                val surface = if (page != null) selectionPageWindowRects[page] else null
                if (page == null || union == null || surface == null ||
                    surface.width <= 0f || surface.height <= 0f
                ) {
                    null
                } else {
                    Rect(
                        surface.left + union.left * surface.width - readerContainerWindowRect.left,
                        surface.top + union.top * surface.height - readerContainerWindowRect.top,
                        surface.left + union.right * surface.width - readerContainerWindowRect.left,
                        surface.top + union.bottom * surface.height - readerContainerWindowRect.top,
                    )
                }
            }
            // Handle-overlay ownership + geometry. The overlay installs touch
            // handling ONLY while it owns a live selection page (SELECT tool,
            // non-empty selection with union bounds on a laid-out page);
            // otherwise it composes nothing and stays out of the hit path
            // entirely — an always-on full-screen detector starves the pages
            // and scroll beneath it on iOS. Sized to the selection page plus
            // a grab margin (the rotate handle floats above the box) and
            // positioned in container space; touches anywhere else never hit
            // it, so tap / scroll / lasso on other pages work untouched.
            val selectionViewTouchSlop = sharedPdfSelectionTouchSlopPx(
                // Android parity (scaledTouchSlop ≈ 8dp): floor the SELECT
                // slop so taps can't degrade into phantom moves on platforms
                // reporting a tiny slop (tap-to-select parity).
                platformTouchSlopPx = LocalViewConfiguration.current.touchSlop,
                minTouchSlopPx = with(density) { 8.dp.toPx() },
            )
            val selectionOverlayMarginPx = with(density) { 72.dp.toPx() }
            val selectionOverlayPage = inkSelection.pageIndex
            val selectionOverlaySurface = selectionOverlayPage?.let { selectionPageWindowRects[it] }
            val selectionOverlayOwnsPage = readerStateForPages.selectedTool == PdfInkTool.SELECT &&
                !inkSelection.isEmpty &&
                selectionEditState.union != null &&
                selectionOverlayPage != null &&
                selectionOverlaySurface != null &&
                selectionOverlaySurface.width > 0f &&
                selectionOverlaySurface.height > 0f
            val overlaySurfaceRect = if (selectionOverlayOwnsPage) selectionOverlaySurface else null
            val selectionOverlayOrigin = if (overlaySurfaceRect != null) {
                Offset(
                    overlaySurfaceRect.left - readerContainerWindowRect.left - selectionOverlayMarginPx,
                    overlaySurfaceRect.top - readerContainerWindowRect.top - selectionOverlayMarginPx,
                )
            } else {
                Offset.Zero
            }
            val selectionOverlaySizePx = if (overlaySurfaceRect != null) {
                IntSize(
                    (overlaySurfaceRect.width + selectionOverlayMarginPx * 2).roundToInt(),
                    (overlaySurfaceRect.height + selectionOverlayMarginPx * 2).roundToInt(),
                )
            } else {
                IntSize.Zero
            }
            val selectionHost = remember(
                inkSelection,
                inkLassoPageIndex,
                inkLassoTrail,
                inkSelectionPreview,
                activeSelectionRotation,
                handleTransformInFlight,
                selectionOverlayOwnsPage,
                selectionOverlayPage,
            ) {
                SharedPdfInkSelectionPageHost(
                    selection = inkSelection,
                    lassoPageIndex = inkLassoPageIndex,
                    lassoTrail = inkLassoTrail,
                    previewById = inkSelectionPreview,
                    activeRotationDegrees = activeSelectionRotation,
                    onTap = ::onSelectionTap,
                    onLasso = ::onSelectionLasso,
                    onLassoProgress = ::onSelectionLassoProgress,
                    onTransformStart = ::onSelectionTransformStart,
                    onTransformUpdate = ::onSelectionTransformUpdate,
                    onTransformEnd = ::onSelectionTransformEnd,
                    isHandleTransformInFlight = handleTransformInFlight,
                    overlayOwnedPageIndex = if (selectionOverlayOwnsPage) selectionOverlayPage else null,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sharedMobilePdfViewerBackground(activeTheme, readerState.displayMode))
                    .onGloballyPositioned { readerContainerWindowRect = it.boundsInWindow() }
                    .onSizeChanged { readerContainerSize = it }
                    .focusRequester(pdfReaderFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (!ownsKeyboardCommands) return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (sharedPdfKeyboardNavigationAction(event.key, readerState.displayMode)) {
                            SharedPdfKeyboardNavigationAction.NEXT_PAGE -> {
                                navigateToPage(readerState.pageIndex + 1, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE -> {
                                navigateToPage(readerState.pageIndex - 1, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.FIRST_PAGE -> {
                                navigateToPage(0, recordHistory = false, reason = PdfNavigationReason.INITIAL)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.LAST_PAGE -> {
                                navigateToPage(displayPageCount - 1, recordHistory = false, reason = PdfNavigationReason.INITIAL)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.SCROLL_DOWN -> {
                                scope.launch { runCatching { pdfVerticalScrollController.scrollByViewportFraction(0.9f) } }
                                true
                            }
                            SharedPdfKeyboardNavigationAction.SCROLL_UP -> {
                                scope.launch { runCatching { pdfVerticalScrollController.scrollByViewportFraction(-0.9f) } }
                                true
                            }
                            SharedPdfKeyboardNavigationAction.NONE -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    SharedMobilePdfVerticalPages(
                        book = book,
                        pdfPassword = pdfPassword,
                        state = readerStateForPages,
                        activeTheme = activeTheme,
                        reverseColorMode = effectiveReverseColorMode,
                        preserveImageColors = readerState.preserveImageColors,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = displayPageCount,
                        virtualLayout = virtualLayout,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        navigationCenterFraction = navigationCenterFraction,
                        showPageGap = showVerticalPageGap,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        ttsPageIndex = ttsPageIndex.takeIf { pdfTts.isSessionActive || pendingTtsStart != null },
                        ttsHighlightBounds = ttsHighlightBounds,
                        activeStroke = activeStroke,
                        activeStrokeOwnerPdfPage = activeStrokeOwnerPdfPage,
                        customFontFamilies = customPdfFontFamilies,
                        highlighterSnapEnabled = readerState.isHighlighterSnapEnabled,
                        isStylusOnlyMode = isStylusOnlyMode,
                        verticalScrollController = pdfVerticalScrollController,
                        autoScrollPlaying = autoScrollPlaying,
                        autoScrollTemporarilyPaused = autoScrollTemporarilyPaused,
                        autoScrollSpeed = autoScrollProfile.speed,
                        autoScrollMusicianMode = autoScrollMusicianMode && autoScrollModeActive,
                        onAutoScrollInteraction = { durationMillis ->
                            autoScrollPauseDurationMillis = durationMillis
                            autoScrollInteractionToken++
                        },
                        onVisiblePageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = { page, eraserOverride -> finishInkStroke(page, eraserOverride) },
                        onInkStrokeEnd = ::onInkStrokeEnd,
                        eraserStrokeWidth = readerState.toolConfigs[PdfInkTool.ERASER]?.strokeWidth
                            ?: SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth,
                        onInkStrokeStart = ::onInkStrokeStart,
                        onEraseAnnotations = ::onEraseAnnotations,
                        selectionHost = selectionHost,
                        onPageSurfaceWindowRectChanged = { page, rect -> selectionPageWindowRects[page] = rect },
                        onExternalLink = { url -> if (ownsGlobalModal) pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
                        onAiDefine = if (readerAiAvailable) {
                            { text -> onAiAction(ReaderAiFeature.DEFINE, text) }
                        } else null,
                        onClipboardError = onClipboardError,
                        onReadAloud = { page, charIndex -> requestTts(sharedPdfDisplayIndexFor(virtualLayout, page), charIndex) },
                        userScrollEnabled = !readerState.isScrollLocked,
                        isScrollLocked = readerState.isScrollLocked,
                        zoomCamera = pdfZoomCamera,
                        onZoomCameraChanged = { pdfZoomCamera = it },
                        textDraft = textDraft,
                        onTextDraftChange = ::updateTextDraft,
                        onTextPageTap = { annotation ->
                            if (annotation != null) startEditingTextBox(annotation) else dismissTextDraft()
                        },
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        showAllTextHighlights = showAllTextHighlights,
                        onAllTextHighlightsLoadingChange = { isAllTextHighlightLoading = it },
                        onToggleChrome = {
                            if (!(autoScrollMusicianMode && autoScrollModeActive)) showChrome = !showChrome
                        },
                        modifier = Modifier.fillMaxSize().then(
                            if (pdfVerticalContentBelowStatusBar) {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                            } else {
                                Modifier
                            }
                        )
                    )
                } else {
                    SharedMobilePdfPaginatedPages(
                        book = book,
                        pdfPassword = pdfPassword,
                        state = readerStateForPages,
                        activeTheme = activeTheme,
                        reverseColorMode = effectiveReverseColorMode,
                        preserveImageColors = readerState.preserveImageColors,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = displayPageCount,
                        virtualLayout = virtualLayout,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        animateNavigation = navigationReason.animatesPagination(),
                        useTwoPageSpread = useTwoPageSpread,
                        firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                        rightToLeftPagination = rightToLeftPagination,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        ttsPageIndex = ttsPageIndex.takeIf { pdfTts.isSessionActive || pendingTtsStart != null },
                        ttsHighlightBounds = ttsHighlightBounds,
                        activeStroke = activeStroke,
                        activeStrokeOwnerPdfPage = activeStrokeOwnerPdfPage,
                        customFontFamilies = customPdfFontFamilies,
                        highlighterSnapEnabled = readerState.isHighlighterSnapEnabled,
                        isStylusOnlyMode = isStylusOnlyMode,
                        tapToTurnPages = tapToTurnPages,
                        pageTurnAnimationEnabled = pageTurnAnimationEnabled && readerState.displayMode == PdfDisplayMode.PAGINATION,
                        positionController = pdfPaginationPositionController,
                        onExternalLink = { url -> if (ownsGlobalModal) pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
                        onAiDefine = if (readerAiAvailable) {
                            { text -> onAiAction(ReaderAiFeature.DEFINE, text) }
                        } else null,
                        onClipboardError = onClipboardError,
                        onReadAloud = { page, charIndex -> requestTts(sharedPdfDisplayIndexFor(virtualLayout, page), charIndex) },
                        userScrollEnabled = !readerState.isScrollLocked,
                        isScrollLocked = readerState.isScrollLocked,
                        zoomCamera = pdfZoomCamera,
                        onZoomCameraChanged = { pdfZoomCamera = it },
                        textDraft = textDraft,
                        onTextDraftChange = ::updateTextDraft,
                        onTextPageTap = { annotation ->
                            if (annotation != null) startEditingTextBox(annotation) else dismissTextDraft()
                        },
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        showAllTextHighlights = showAllTextHighlights,
                        onAllTextHighlightsLoadingChange = { isAllTextHighlightLoading = it },
                        onPageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onManualPageTurnStarted = ::stopPdfTtsForManualPagination,
                        onToggleChrome = { showChrome = !showChrome },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = { page, eraserOverride -> finishInkStroke(page, eraserOverride) },
                        onInkStrokeEnd = ::onInkStrokeEnd,
                        eraserStrokeWidth = readerState.toolConfigs[PdfInkTool.ERASER]?.strokeWidth
                            ?: SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth,
                        onInkStrokeStart = ::onInkStrokeStart,
                        onEraseAnnotations = ::onEraseAnnotations,
                        selectionHost = selectionHost,
                        onPageSurfaceWindowRectChanged = { page, rect -> selectionPageWindowRects[page] = rect },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AnimatedVisibility(
                    // Android parity (shouldRenderReaderSlider): the slider
                    // belongs to visible bottom chrome and never shows during
                    // search.
                    visible = showChrome && showPageSlider && !readerState.isSearchActive,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfPageSlider(
                        pageIndex = readerState.pageIndex,
                        pageCount = displayPageCount,
                        onPageChange = {
                            navigateToPage(
                                pageIndex = it,
                                recordHistory = false,
                                reason = PdfNavigationReason.PAGE_SLIDER,
                            )
                        },
                        onScrubPreview = { pdfSliderScrubbingPage = it },
                        // Android parity (PdfViewerScreen slider): full-width
                        // bare bar with inner 12/6.dp padding owned by the
                        // slider itself. No outer horizontal inset — the bar
                        // sits above the jump bar via bottom padding only.
                        modifier = Modifier
                            .padding(bottom = pdfSliderBottomPadding)
                    )
                }
                pdfSliderScrubbingPage?.let { scrubPage ->
                    SharedMobilePdfPageScrubbingOverlay(
                        label = sharedPdfPageRangeLabel(
                            sharedMobilePdfPageLabel(
                                scrubPage,
                                displayPageCount,
                                useTwoPageSpread && readerState.displayMode == PdfDisplayMode.PAGINATION,
                                firstPageStandaloneInSpread
                            ),
                            displayPageCount
                        )
                    )
                }
                AnimatedVisibility(
                    visible = isJumpHistoryVisible,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfJumpHistoryBar(
                        history = jumpHistory,
                        onBack = {
                            val refreshedHistory = jumpHistory.updateCurrentLocation(
                                currentPageIndex = currentPdfHistoryPage(),
                                pageCount = displayPageCount,
                            )
                            val target = refreshedHistory.backPage ?: return@SharedMobilePdfJumpHistoryBar
                            jumpHistory = refreshedHistory.stepBack()
                            navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                        },
                        onForward = {
                            val refreshedHistory = jumpHistory.updateCurrentLocation(
                                currentPageIndex = currentPdfHistoryPage(),
                                pageCount = displayPageCount,
                            )
                            val target = refreshedHistory.forwardPage ?: return@SharedMobilePdfJumpHistoryBar
                            jumpHistory = refreshedHistory.stepForward()
                            navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                        },
                        onClear = { jumpHistory = jumpHistory.clear() },
                        modifier = Modifier.padding(bottom = pdfBottomChromePadding)
                    )
                }
                val ttsBottomPadding by animateDpAsState(
                    targetValue = if (showChrome) {
                        56.dp + 16.dp + effectiveBottomSystemInset
                    } else {
                        16.dp + effectiveBottomSystemInset
                    },
                    animationSpec = tween(PdfChromeMotionDurationMillis),
                    label = "PdfTtsBottomPadding"
                )
                AnimatedVisibility(
                    visible = pdfTts.isSessionActive || pendingTtsStart != null,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfTtsControls(
                        tts = pdfTts,
                        pageIndex = ttsPageIndex,
                        pageCount = pageCount,
                        chunkIndex = pdfTts.progress.currentChunkIndex,
                        chunkCount = pdfTts.progress.chunks.size,                        onPauseResume = {
                            if (ownsTts) {
                                if (pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING) pdfTts.pause() else pdfTts.resume()
                            }
                        },
                        onPreviousPage = {
                            if (ownsTts) {
                                if (pdfTts.progress.currentChunkIndex > 0) {
                                    pdfTts.skipPrevious()
                                } else if (ttsPageIndex > 0) {
                                    requestTts(
                                        pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex - 1),
                                        startAtLastChunk = true,
                                        playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                    )
                                }
                            }
                        },
                        onNextPage = {
                            if (ownsTts) {
                                if (pdfTts.progress.currentChunkIndex < pdfTts.progress.chunks.lastIndex) {
                                    pdfTts.skipNext()
                                } else if (ttsPageIndex < pageCount - 1) {
                                    requestTts(
                                        pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex + 1),
                                        playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                    )
                                }
                            }
                        },
                        onLocate = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex), recordHistory = false, centerFraction = ttsHighlightBounds.centerYFraction(), reason = PdfNavigationReason.TTS) },
                        onStop = {
                            if (ownsTts) pdfTts.stop()
                            pendingTtsStart = null
                            pendingTtsStartAtLastChunk = false
                            ttsHighlightBounds = emptyList()
                        },
                        overlaySize = ttsOverlaySize,
                        onOverlaySizeChange = {
                            ttsOverlaySize = it
                            onTtsOverlaySizePreferenceChange(it)
                        },
                        modifier = Modifier.padding(bottom = ttsBottomPadding)
                    )
                }
                // Selection handle overlay: present ONLY while it owns the
                // selection page (see above). It owns all nine handles plus —
                // on a handle miss — the page's tap / move / lasso dispatch
                // (the shared detector, in container space), so the selection
                // page is handled in exactly one place and nothing below it
                // needs the touch. Slop is raw screen px, no zoom compensation
                // needed; a handle hit consumes the down in the Initial pass.
                val latestHandleUnion by rememberUpdatedState(selectionEditState.union)
                val latestHandleSelPage by rememberUpdatedState(inkSelection.pageIndex)
                val latestHandlePageRects by rememberUpdatedState(selectionPageWindowRects)
                val latestHandleContainer by rememberUpdatedState(readerContainerWindowRect)
                val latestHandleTool by rememberUpdatedState(readerStateForPages.selectedTool)
                val latestHandleStylusOnly by rememberUpdatedState(isStylusOnlyMode)
                val latestHandleOrigin by rememberUpdatedState(selectionOverlayOrigin)
                val latestTouchSlopPx by rememberUpdatedState(selectionViewTouchSlop)
                val latestSelectionZoom by rememberUpdatedState(pdfZoomCamera.scale)
                val latestReaderAnnotations by rememberUpdatedState(readerState.annotations)
                if (selectionOverlayOwnsPage) {
                    val overlayWidthDp = with(density) { selectionOverlaySizePx.width.toDp() }
                    val overlayHeightDp = with(density) { selectionOverlaySizePx.height.toDp() }
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    selectionOverlayOrigin.x.roundToInt(),
                                    selectionOverlayOrigin.y.roundToInt(),
                                )
                            }
                            .size(overlayWidthDp, overlayHeightDp)
                            .pointerInput(inkSelection.pageIndex) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    if (latestHandleTool != PdfInkTool.SELECT) return@awaitEachGesture
                                    if (latestHandleStylusOnly && down.type == PointerType.Touch) {
                                        pdfInkSelectionLog { "overlay.skip reason=stylus-only-touch" }
                                        return@awaitEachGesture
                                    }
                                    val union = latestHandleUnion
                                    if (union == null) return@awaitEachGesture
                                    val selPage = latestHandleSelPage ?: return@awaitEachGesture
                                    val container = latestHandleContainer
                                    if (container == Rect.Zero) return@awaitEachGesture
                                    val surface = latestHandlePageRects[selPage] ?: return@awaitEachGesture
                                    if (surface.width <= 0f || surface.height <= 0f) return@awaitEachGesture
                                    val origin = latestHandleOrigin
                                    val downContainer = down.position + origin
                                    val pageLocal = Rect(
                                        surface.left - container.left,
                                        surface.top - container.top,
                                        surface.right - container.left,
                                        surface.bottom - container.top,
                                    )
                                    val aspect = (pageLocal.width / pageLocal.height)
                                        .takeIf { it.isFinite() && it > 0f } ?: 1f
                                    fun toNormPos(containerPos: Offset): PdfPagePoint {
                                        return PdfPagePoint(
                                            x = ((containerPos.x - pageLocal.left) / pageLocal.width).coerceIn(0f, 1f),
                                            y = ((containerPos.y - pageLocal.top) / pageLocal.height).coerceIn(0f, 1f),
                                        )
                                    }
                                    val slopPx = 20.dp.toPx()
                                    val handles = sharedPdfSelectionHandlePositions(union).mapValues { (_, norm) ->
                                        Offset(
                                            pageLocal.left + norm.x * pageLocal.width - origin.x,
                                            pageLocal.top + norm.y * pageLocal.height - origin.y,
                                        )
                                    }
                                    val hitHandle = findSharedPdfSelectionHandleHit(
                                        handles, down.position, slopPx
                                    )
                                    pdfInkSelectionLog {
                                        "overlay.down container=(${downContainer.x.roundToInt()}," +
                                            "${downContainer.y.roundToInt()}) slopPx=${slopPx.roundToInt()} " +
                                            "hit=$hitHandle"
                                    }
                                    if (hitHandle != null) {
                                        down.consume()
                                        handleTransformInFlight = true
                                        try {
                                            runSharedSelectionTransformSession(
                                                handle = hitHandle,
                                                selectionBounds = union,
                                                startNorm = toNormPos(downContainer),
                                                downId = down.id,
                                                pageAspectRatio = aspect,
                                                onTransformStart = { onSelectionTransformStart(selPage) },
                                                onTransformUpdate = { onSelectionTransformUpdate(selPage, it, aspect) },
                                                onTransformEnd = ::onSelectionTransformEnd,
                                                toNorm = { toNormPos(it + origin) },
                                            )
                                        } finally {
                                            handleTransformInFlight = false
                                        }
                                        return@awaitEachGesture
                                    }
                                    // Handle miss: own this page's tap / move /
                                    // lasso here with the shared dispatch (the
                                    // page's own block stands down via
                                    // overlayOwnedPageIndex, so each gesture is
                                    // handled exactly once).
                                    pdfInkSelectionLog { "overlay.delegate page=$selPage" }
                                    runSharedPdfInkSelectionForDown(
                                        down = down,
                                        pageIndex = selPage,
                                        pageSizePx = IntSize(
                                            pageLocal.width.roundToInt(),
                                            pageLocal.height.roundToInt(),
                                        ),
                                        pageAspectRatio = aspect,
                                        touchSlopPx = latestTouchSlopPx,
                                        zoomProvider = { 1f },
                                        annotationsProvider = {
                                            latestReaderAnnotations.filter {
                                                it.pageIndex == selPage && it.kind == PdfAnnotationKind.INK
                                            }
                                        },
                                        selectionBoundsProvider = { latestHandleUnion },
                                        onTapResult = { tappedPage, annotationId ->
                                            onSelectionTap(tappedPage, annotationId)
                                        },
                                        onLassoResult = { lassoPage, annotationIds ->
                                            onSelectionLasso(lassoPage, annotationIds)
                                        },
                                        onLassoProgress = { trail ->
                                            val zoom = latestSelectionZoom.coerceAtLeast(0.01f)
                                            val surfaceNow = latestHandlePageRects[selPage]
                                            val containerNow = latestHandleContainer
                                            val originNow = latestHandleOrigin
                                            if (surfaceNow == null || surfaceNow.width <= 0f ||
                                                surfaceNow.height <= 0f
                                            ) {
                                                onSelectionLassoProgress(selPage, trail?.let { emptyList() })
                                            } else {
                                                val left = surfaceNow.left - containerNow.left
                                                val top = surfaceNow.top - containerNow.top
                                                onSelectionLassoProgress(selPage, trail?.map { overlayPos ->
                                                    val c = overlayPos + originNow
                                                    Offset((c.x - left) / zoom, (c.y - top) / zoom)
                                                })
                                            }
                                        },
                                        onTransformStart = { transformPage ->
                                            onSelectionTransformStart(transformPage)
                                        },
                                        onTransformUpdate = { transformPage, transform, transformAspect ->
                                            onSelectionTransformUpdate(transformPage, transform, transformAspect)
                                        },
                                        onTransformEnd = ::onSelectionTransformEnd,
                                        toNorm = { overlayPos -> toNormPos(overlayPos + latestHandleOrigin) },
                                    )
                                }
                            }
                    )
                }
                // Floating selection edit bar (benchmark: Android
                // PdfInkSelectionEditBar): HSV entry opens the spectrum
                // directly, slider edits thickness live, copy duplicates
                // in place. Gated on the effective (unminimized) tool and
                // anchored below the selection, fully clamped on-screen.
                if (readerStateForPages.selectedTool == PdfInkTool.SELECT &&
                    !inkSelection.isEmpty &&
                    selectionBarRect != null &&
                    selectionEditState.selected.isNotEmpty() &&
                    readerContainerSize.width > 0 &&
                    readerContainerSize.height > 0
                ) {
                    val barThickness = selectionEditState.thickness
                    if (barThickness != null) {
                        val styleHasHighlighter = remember(selectionEditState.selected) {
                            selectionEditState.selected.any {
                                it.tool == PdfInkTool.HIGHLIGHTER || it.tool == PdfInkTool.HIGHLIGHTER_ROUND
                            }
                        }
                        val stylePalette = remember(
                            styleHasHighlighter,
                            readerState.penPalette,
                            readerState.highlighterPalette,
                        ) {
                            if (styleHasHighlighter) readerState.highlighterPalette else readerState.penPalette
                        }
                        SharedPdfInkSelectionEditBar(
                            selectionWindowRect = selectionBarRect,
                            containerSizePx = readerContainerSize,
                            selectedColor = selectionEditState.color,
                            selectionPalette = stylePalette,
                            onPaletteChange = { next ->
                                if (styleHasHighlighter) {
                                    dispatch(SharedPdfReaderAction.HighlighterPaletteChanged(next))
                                } else {
                                    dispatch(SharedPdfReaderAction.PenPaletteChanged(next))
                                }
                            },
                            onColorLive = ::selectionColorLive,
                            onColorReverted = ::revertSelectionStyle,
                            thickness = barThickness,
                            thicknessRange = selectionEditState.thicknessRange,
                            onThicknessChange = { next ->
                                selectionStyleLive { it.copy(strokeWidth = next) }
                            },
                            onThicknessChangeFinished = { selectionStyleCommit() },
                            canDuplicate = true,
                            onDuplicate = ::duplicateInkSelection,
                            onDelete = ::deleteInkSelection,
                        )
                    }
                }
                if (cloudTts != null) {
                AnimatedVisibility(
                        visible = cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused,
                        enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                        exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        SharedMobileEpubCloudTtsControls(
                            tts = cloudTts,
                            onLocate = {
                                cloudTtsState.progress.currentChunk?.pageIndex?.let { page ->
                                    navigateToPage(
                                        sharedPdfDisplayIndexFor(virtualLayout, page),
                                        recordHistory = false,
                                        reason = PdfNavigationReason.TTS,
                                    )
                                }
                            },
                            modifier = Modifier.padding(bottom = ttsBottomPadding),
                        )
                    }
                }
                // Android parity (PdfViewerScreen scroll-to-TTs FAB): in vertical
                // mode while TTS is active on an off-screen page, a transient
                // button jumps back to the reading page. The in-overlay locate
                // action stays for the on-screen case.
                val isPdfTtsPageBelow = ttsPageIndex > currentPdfIndex
                AnimatedVisibility(
                    visible = readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL &&
                        (pdfTts.isSessionActive || pendingTtsStart != null || cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused) &&
                        ttsPageIndex != currentPdfIndex,
                    enter = fadeIn(animationSpec = tween(PdfChromeMotionDurationMillis)),
                    exit = fadeOut(animationSpec = tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ttsBottomPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            navigateToPage(
                                sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex),
                                recordHistory = false,
                                reason = PdfNavigationReason.TTS
                            )
                        },
                        shape = CircleShape,
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(
                            imageVector = if (isPdfTtsPageBelow) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = readerString("content_desc_scroll_to_reading_page", "Scroll to reading page")
                        )
                    }
                }
                AnimatedVisibility(
                    visible = autoScrollModeActive && readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SharedMobilePdfAutoScrollControls(
                        isPlaying = autoScrollPlaying,
                        isTemporarilyPaused = autoScrollTemporarilyPaused,
                        profile = autoScrollProfile,
                        isLocalMode = autoScrollIsLocal,
                        isMusicianMode = autoScrollMusicianMode,
                        useSlider = autoScrollUseSlider,
                        isCollapsed = autoScrollCollapsed,
                        onPlayPause = {
                            autoScrollPlaying = !autoScrollPlaying
                            autoScrollTemporarilyPaused = false
                        },
                        onProfileChange = ::updateAutoScrollProfile,
                        onLocalModeChange = ::setAutoScrollLocalMode,
                        onMusicianModeChange = {
                            autoScrollMusicianMode = it
                            onPdfAutoScrollMusicianModeChange(it)
                            if (it) showChrome = false
                        },
                        onUseSliderChange = {
                            autoScrollUseSlider = it
                            onPdfAutoScrollUseSliderChange(it)
                        },
                        onCollapsedChange = { autoScrollCollapsed = it },
                        onScrollToTop = {
                            autoScrollPauseDurationMillis = 1_000L
                            autoScrollInteractionToken++
                            navigateToPage(0, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                        },
                        onClose = {
                            autoScrollModeActive = false
                            autoScrollPlaying = false
                            autoScrollTemporarilyPaused = false
                            // Android parity (PdfViewerScreen auto-scroll
                            // onClose): closing restores the chrome.
                            showChrome = true
                        },
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = ttsBottomPadding + if (pdfTts.isSessionActive || pendingTtsStart != null) 76.dp else 0.dp,
                        ),
                    )
                }
                AnimatedVisibility(
                    // Android parity: the panel shows for blank queries too
                    // (it renders its own "Enter a search term" prompt).
                    visible = readerState.isSearchActive && readerState.showSearchResultsPanel
                ) {
                    SharedMobilePdfSearchResultsPanel(
                        query = readerState.searchQuery,
                        results = searchResults,
                        activeResultIndex = readerState.activeSearchResultIndex,
                        isSearching = isSearchInProgress,
                        onResultClick = { index ->
                            navigateToSearchResult(index)
                            dispatch(SharedPdfReaderAction.SearchResultsPanelToggled)
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                    )
                }
                AnimatedVisibility(
                    visible = readerState.isSearchActive && !readerState.showSearchResultsPanel && searchResults.isNotEmpty(),
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfSearchNavigationPill(
                        activeIndex = readerState.activeSearchResultIndex,
                        resultCount = searchResults.size,
                        highlightMode = readerState.searchHighlightMode,
                        onToggleHighlightMode = {
                            dispatch(SharedPdfReaderAction.SearchHighlightModeToggled)
                        },
                        onPrevious = { navigateToSearchResult(readerState.activeSearchResultIndex - 1) },
                        onNext = { navigateToSearchResult(readerState.activeSearchResultIndex + 1) },
                        onShowResults = { dispatch(SharedPdfReaderAction.SearchResultsPanelToggled) },
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                    )
                }
                // Android parity (PdfViewerScreen hiddenRichTextInputEnabled =
                // edit mode + TEXT tool + no legacy box selected): the 1dp
                // invisible field that owns the IME for flowing text. Without
                // it the controller's focus requests hit an unattached node
                // (the FocusRequester warning) and the keyboard never opens.
                SharedPdfRichTextHiddenInput(
                    controller = richTextController,
                    enabled = isPdfEditMode && readerState.selectedTool == PdfInkTool.TEXT && textDraft == null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(start = 16.dp, bottom = 120.dp)
                )
                // Android-parity floating annotation dock (benchmark:
                // PdfViewerScreen.kt edit-mode chrome + AnnotationDock.kt).
                // Replaces the old bottom-bar-embedded interaction dock: close,
                // minimize, stylus-only, pen / highlighter / text / eraser,
                // undo + redo, draggable TOP / BOTTOM / FLOATING with the
                // tool-settings popup on the opposite side of the dock.
                if (isPdfEditMode) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val boxMaxWidthPx = with(density) { maxWidth.toPx() }
                        val boxMaxHeightPx = with(density) { maxHeight.toPx() }
                        val annotationDockHeight = 56.dp
                        val annotationDockHeightPx = with(density) { annotationDockHeight.toPx() }
                        val isSticky = isSharedPdfAnnotationDockSticky(
                            annotationDockLocation,
                            isAnnotationDockDragging,
                        )
                        val dockTopYPx = sharedPdfAnnotationDockTopYPx(
                            annotationDockLocation,
                            annotationDockOffset.y,
                            boxMaxHeightPx,
                            annotationDockHeightPx,
                        )
                        val popupAboveDock = isSharedPdfAnnotationDockInBottomHalf(
                            dockTopYPx,
                            annotationDockHeightPx,
                            boxMaxHeightPx,
                        )
                        val popupAlign = if (popupAboveDock) Alignment.BottomCenter else Alignment.TopCenter
                        val popupMargin = 16.dp
                        // Android parity: the sticky dock carries system-inset
                        // padding (home-indicator / status bar) below/above its
                        // 56dp bar, which the raw dockTopY math doesn't include.
                        // Without it the inset eats the margin on iOS and the
                        // popup sits flush against (or overlaps) the dock.
                        val statusTopDp = with(density) {
                            WindowInsets.safeDrawing.getTop(density).toDp()
                        }
                        val popupDockBottomInset =
                            if (isSticky && annotationDockLocation == DockLocation.BOTTOM) effectiveBottomSystemInset else 0.dp
                        val popupDockTopInset =
                            if (isSticky && annotationDockLocation == DockLocation.TOP) statusTopDp else 0.dp
                        val popupTopPad = if (!popupAboveDock) {
                            with(density) { (dockTopYPx + annotationDockHeightPx).toDp() } + popupDockTopInset + popupMargin
                        } else {
                            0.dp
                        }
                        val popupBottomPad = if (popupAboveDock) {
                            with(density) { (boxMaxHeightPx - dockTopYPx).toDp() } + popupDockBottomInset + popupMargin
                        } else {
                            0.dp
                        }
                        // Hoisted: AnimatedVisibility content has its own
                        // receiver, so BoxWithConstraints maxHeight isn't
                        // visible inside the popup call below.
                        val popupMaxHeight = sharedPdfPopupMaxHeightDp(maxHeight.value.roundToInt()).dp
                        Box(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showAnnotationToolSettings,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(popupAlign)
                                    .padding(top = popupTopPad, bottom = popupBottomPad)
                                    .testTag("ToolSettingsPopup"),
                            ) {
                                SharedPdfAndroidToolSettingsPopup(
                                    selectedTool = readerState.selectedTool,
                                    selectedColor = readerState.selectedColorArgb,
                                    strokeWidth = readerState.strokeWidth,
                                    actualToolConfigs = readerState.toolConfigs,
                                    penPalette = readerState.penPalette,
                                    highlighterPalette = readerState.highlighterPalette,
                                    onToolSelected = { setTool(it) },
                                    onColorSelected = { dispatch(SharedPdfReaderAction.ColorSelected(it)) },
                                    onStrokeWidthChange = { dispatch(SharedPdfReaderAction.StrokeWidthChanged(it)) },
                                    onPaletteChange = { nextPalette ->
                                        if (readerState.selectedTool in SharedPdfAnnotationHighlighterTools) {
                                            updatePdfHighlighterPalette(
                                                SharedPdfHighlighterPalette(nextPalette),
                                            )
                                        } else {
                                            dispatch(SharedPdfReaderAction.PenPaletteChanged(nextPalette))
                                        }
                                    },
                                    isHighlighterSnapEnabled = readerState.isHighlighterSnapEnabled,
                                    onHighlighterSnapChange = ::updatePdfHighlighterSnap,
                                    // Android parity (ToolSettingsPopup
                                    // maxPopupHeight): cap against the reader
                                    // height so the popup scrolls instead of
                                    // overflowing on small screens.
                                    maxHeight = popupMaxHeight,
                                )
                            }

                            annotationSnapPreview?.let { location ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(annotationDockHeight)
                                        .align(
                                            if (location == DockLocation.TOP) Alignment.TopCenter
                                            else Alignment.BottomCenter,
                                        )
                                        .background(Color.Black),
                                )
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                val dragModifier =
                                    if (isAnnotationDockDragging || annotationDockLocation == DockLocation.FLOATING) {
                                        Modifier.offset {
                                            IntOffset(
                                                annotationDockOffset.x.roundToInt(),
                                                annotationDockOffset.y.roundToInt(),
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                val alignModifier = when {
                                    isAnnotationDockDragging || annotationDockLocation == DockLocation.FLOATING -> Modifier
                                    annotationDockLocation == DockLocation.TOP -> Modifier.align(Alignment.TopCenter)
                                    annotationDockLocation == DockLocation.BOTTOM -> Modifier.align(Alignment.BottomCenter)
                                    else -> Modifier
                                }
                                val widthModifier =
                                    if ((annotationDockLocation == DockLocation.TOP || annotationDockLocation == DockLocation.BOTTOM) && !isAnnotationDockDragging) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier.padding(horizontal = 16.dp)
                                    }
                                val paddingModifier =
                                    if ((annotationDockLocation == DockLocation.TOP || annotationDockLocation == DockLocation.BOTTOM) && !isAnnotationDockDragging) {
                                        Modifier.padding(
                                            bottom = if (annotationDockLocation == DockLocation.BOTTOM) effectiveBottomSystemInset else 0.dp,
                                            top = if (annotationDockLocation == DockLocation.TOP) statusTopDp else 0.dp,
                                        )
                                    } else {
                                        Modifier.padding(vertical = 16.dp)
                                    }
                                Box(
                                    modifier = Modifier
                                        .then(alignModifier)
                                        .then(dragModifier)
                                        .pointerInput(annotationDockLocation, isAnnotationDockMinimized) {
                                            val onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit = {
                                                isAnnotationDockDragging = true
                                                val startX = (boxMaxWidthPx / 2) - (size.width / 2)
                                                if (annotationDockLocation == DockLocation.BOTTOM) {
                                                    annotationDockOffset = androidx.compose.ui.geometry.Offset(
                                                        startX,
                                                        boxMaxHeightPx - annotationDockHeightPx - 50f,
                                                    )
                                                } else if (annotationDockLocation == DockLocation.TOP) {
                                                    annotationDockOffset = androidx.compose.ui.geometry.Offset(startX, 50f)
                                                }
                                            }
                                            val onDrag: (
                                                androidx.compose.ui.input.pointer.PointerInputChange,
                                                androidx.compose.ui.geometry.Offset,
                                            ) -> Unit = { change, dragAmount ->
                                                change.consume()
                                                annotationDockOffset += dragAmount
                                                val topSnapThreshold = 150f
                                                val bottomSnapThreshold = boxMaxHeightPx - 250f
                                                annotationSnapPreview = when {
                                                    annotationDockOffset.y < topSnapThreshold -> DockLocation.TOP
                                                    annotationDockOffset.y > bottomSnapThreshold -> DockLocation.BOTTOM
                                                    else -> null
                                                }
                                            }
                                            val onDragEnd: () -> Unit = {
                                                isAnnotationDockDragging = false
                                                if (annotationSnapPreview != null) {
                                                    annotationDockLocation = annotationSnapPreview!!
                                                    annotationSnapPreview = null
                                                } else {
                                                    annotationDockLocation = DockLocation.FLOATING
                                                    val safeX = annotationDockOffset.x.coerceIn(
                                                        0f,
                                                        boxMaxWidthPx - 100f,
                                                    )
                                                    val safeY = annotationDockOffset.y.coerceIn(
                                                        0f,
                                                        boxMaxHeightPx - annotationDockHeightPx,
                                                    )
                                                    annotationDockOffset = androidx.compose.ui.geometry.Offset(safeX, safeY)
                                                }
                                            }
                                            val onDragCancel: () -> Unit = {
                                                isAnnotationDockDragging = false
                                                annotationSnapPreview = null
                                            }
                                            if (annotationDockLocation == DockLocation.FLOATING) {
                                                detectDragGestures(
                                                    onDragStart = onDragStart,
                                                    onDrag = onDrag,
                                                    onDragEnd = onDragEnd,
                                                    onDragCancel = onDragCancel,
                                                )
                                            } else {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = onDragStart,
                                                    onDrag = onDrag,
                                                    onDragEnd = onDragEnd,
                                                    onDragCancel = onDragCancel,
                                                )
                                            }
                                        },
                                ) {
                                    SharedPdfAndroidAnnotationDock(
                                        selectedTool = readerState.selectedTool,
                                        activePenColor = annotationDockPenColor,
                                        activeHighlighterColor = annotationDockHighlighterColor,
                                        lastPenTool = readerState.lastActivePenTool,
                                        lastHighlighterTool = readerState.lastActiveHighlighterTool,
                                        isStylusOnlyMode = isStylusOnlyMode,
                                        onToggleStylusOnlyMode = { isStylusOnlyMode = !isStylusOnlyMode },
                                        onToolClick = ::onAnnotationDockToolClick,
                                        // Android + desktop parity: undo pops the
                                        // global history stack (draw or erase),
                                        // enabled while anything is undoable —
                                        // NOT "delete last on page" (that pushed
                                        // a new Remove and wiped redo).
                                        onUndo = { dispatch(SharedPdfReaderAction.UndoAnnotationEdit) },
                                        onRedo = { dispatch(SharedPdfReaderAction.RedoAnnotationEdit) },
                                        onClose = ::closeAnnotationDock,
                                        canUndo = readerState.canUndoAnnotationEdit,
                                        canRedo = readerState.canRedoAnnotationEdit,
                                        isSticky = isSticky,
                                        modifier = Modifier
                                            .then(widthModifier)
                                            .then(paddingModifier),
                                        isMinimized = isAnnotationDockMinimized,
                                        onToggleMinimize = { isAnnotationDockMinimized = !isAnnotationDockMinimized },
                                    )
                                }
                            }
                        }
                    }
                }
                // Android-parity draggable text formatting dock (benchmark:
                // PdfViewerScreen.kt text dock chrome + TextAnnotationDock.kt).
                // Composed AFTER the pen dock so it draws above it (the reported
                // "text toolbar under main toolbar" bug was the old order).
                // Bottom-docked it clears the pen dock via resting padding,
                // top-docked it stacks below the pen dock, floating it rides
                // above the keyboard. Drag/long-press lives on the bar only —
                // the container stays touch-transparent for controls beneath.
                if (shouldShowPdfTextDock(isPdfEditMode, readerState.selectedTool == PdfInkTool.TEXT) && !readerState.isSearchActive) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val textBoxMaxWidthPx = with(density) { maxWidth.toPx() }
                        val textBoxMaxHeightPx = with(density) { maxHeight.toPx() }
                        val textDockBarHeight = 48.dp
                        val textDockHeightPx = with(density) { textDockBarHeight.toPx() }
                        val textDockFloatPad = 16.dp
                        val textDockFloatPadPx = with(density) { textDockFloatPad.toPx() }
                        // Resting clearance keys off the PEN dock (benchmark:
                        // bottomPadding clears the pen dock when closed, 0 when
                        // the keyboard is open since insets position it).
                        val textBottomPadding = pdfTextDockRestingBottomPadding(
                            isImeVisible = isPdfImeVisible,
                            dockLocation = annotationDockLocation,
                            isDockMinimized = isAnnotationDockMinimized,
                        )
                        val textPopupsBelowBar = isPdfTextDockTopAnchored(textDockLocation, isTextDockDragging)
                        val isTextStickyBottom = textDockLocation == DockLocation.BOTTOM && !isTextDockDragging
                        val isTextFloating = isTextDockDragging || textDockLocation == DockLocation.FLOATING
                        // A floating (or in-drag) bar parked where the keyboard
                        // opens rides up to sit just above it, like bottom-dock.
                        val textDockLiftPx = pdfTextDockKeyboardLiftPx(
                            isImeVisible = isPdfImeVisible,
                            isFloating = isTextFloating,
                            dockBottomPx = textDockOffset.y + textDockFloatPadPx + textDockHeightPx,
                            keyboardTopPx = textBoxMaxHeightPx - WindowInsets.ime.getBottom(density),
                        )
                        Box(modifier = Modifier.fillMaxSize()) {
                            val textDragModifier =
                                if (isTextFloating) {
                                    Modifier.offset {
                                        IntOffset(
                                            textDockOffset.x.roundToInt(),
                                            (textDockOffset.y - textDockLiftPx).roundToInt()
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            val textAlignModifier = when {
                                isTextFloating -> Modifier
                                textPopupsBelowBar -> Modifier.align(Alignment.TopCenter)
                                else -> Modifier.align(Alignment.BottomCenter)
                            }
                            val textWidthModifier =
                                if ((textDockLocation == DockLocation.TOP || textDockLocation == DockLocation.BOTTOM) && !isTextDockDragging) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.padding(horizontal = 16.dp)
                                }
                            val textInsetsModifier =
                                if (isTextStickyBottom) {
                                    Modifier.windowInsetsPadding(
                                        if (isPdfImeVisible) WindowInsets.ime.union(WindowInsets.navigationBars)
                                        else WindowInsets.navigationBars
                                    )
                                } else {
                                    Modifier
                                }
                            // A top-docked text bar stacks below a top-docked
                            // pen dock (shared 56.dp dock height), mirroring
                            // how the bottom bar clears it via resting padding.
                            val textStatusTopDp = with(density) {
                                WindowInsets.safeDrawing.getTop(density).toDp()
                            }
                            val textPaddingModifier = when {
                                isTextStickyBottom -> Modifier.padding(bottom = textBottomPadding)
                                textPopupsBelowBar -> Modifier.padding(
                                    top = (if (annotationDockLocation == DockLocation.TOP && !isAnnotationDockDragging) 56.dp else 0.dp) + textStatusTopDp
                                )
                                else -> Modifier.padding(vertical = textDockFloatPad)
                            }
                            val textDockDragGesture = Modifier.pointerInput(textDockLocation) {
                                val onDragStart: (Offset) -> Unit = {
                                    isTextDockDragging = true
                                    // Seed from the last measured position so
                                    // picking the bar up never teleports it:
                                    // sticky positions carry inset/resting
                                    // offsets a formula can't reproduce.
                                    val measured = textDockMeasuredOffset
                                    if (measured != null) {
                                        textDockOffset = measured
                                    } else {
                                        val startX = (textBoxMaxWidthPx / 2) - (size.width / 2)
                                        if (textDockLocation == DockLocation.BOTTOM) {
                                            textDockOffset = Offset(
                                                startX, textBoxMaxHeightPx - textDockHeightPx - 50f
                                            )
                                        } else if (textDockLocation == DockLocation.TOP) {
                                            textDockOffset = Offset(startX, 50f)
                                        }
                                    }
                                }
                                val onDrag: (
                                    androidx.compose.ui.input.pointer.PointerInputChange,
                                    Offset
                                ) -> Unit = { change, dragAmount ->
                                    change.consume()
                                    textDockOffset += dragAmount
                                }
                                val onDragEnd: () -> Unit = {
                                    isTextDockDragging = false
                                    val topSnapThreshold = 150f
                                    // Bottom snaps only at/below the docked band
                                    // (resting bar position minus a grab margin):
                                    // dropping the bar over the pen dock stays
                                    // floating instead, exactly like the benchmark.
                                    val bottomSnapThreshold = textBoxMaxHeightPx -
                                        with(density) { effectiveBottomSystemInset.toPx() } -
                                        with(density) { textBottomPadding.toPx() } -
                                        textDockHeightPx -
                                        with(density) { 24.dp.toPx() }
                                    textDockLocation = when {
                                        textDockOffset.y < topSnapThreshold -> DockLocation.TOP
                                        textDockOffset.y > bottomSnapThreshold -> DockLocation.BOTTOM
                                        else -> DockLocation.FLOATING
                                    }
                                    if (textDockLocation == DockLocation.FLOATING) {
                                        val safeX = textDockOffset.x.coerceIn(
                                            0f, textBoxMaxWidthPx - 100f
                                        )
                                        val safeY = textDockOffset.y.coerceIn(
                                            0f, textBoxMaxHeightPx - textDockHeightPx
                                        )
                                        textDockOffset = Offset(safeX, safeY)
                                    }
                                }
                                val onDragCancel: () -> Unit = {
                                    isTextDockDragging = false
                                }
                                if (textDockLocation == DockLocation.FLOATING) {
                                    detectDragGestures(
                                        onDragStart = onDragStart,
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel
                                    )
                                } else {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = onDragStart,
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .then(textAlignModifier)
                                    .then(textDragModifier)
                                    .onGloballyPositioned {
                                        if (!isTextDockDragging) textDockMeasuredOffset = it.positionInParent()
                                    }
                                    .then(textWidthModifier)
                                    .then(textInsetsModifier)
                                    .then(textPaddingModifier)
                            ) {
                                val activeTextStyle = textDraft?.style ?: textStyle
                                SharedMobilePdfTextDock(
                                    style = activeTextStyle,
                                    onStyleChange = { newStyle ->
                                        // Android parity (TextAnnotationDock
                                        // onUpdateStyle): the tool default is
                                        // always persisted; live edits go to
                                        // the open draft, otherwise to the
                                        // flowing rich-text selection/cursor
                                        // (which also re-requests the keyboard,
                                        // keeping it up while formatting).
                                        textStyle = newStyle
                                        val draft = textDraft
                                        if (draft != null) {
                                            updateTextDraft(draft.withStyle(newStyle, canvasSize))
                                        } else {
                                            richTextController.updateCurrentStyle(
                                                newStyle.toSharedPdfRichSpanStyle(),
                                                newStyle.fontPath,
                                                newStyle.fontName
                                            )
                                        }
                                    },
                                    onInsertTextBox = ::insertTextBox,
                                    customFonts = customFonts,
                                    customFontFamilies = customPdfFontFamilies,
                                    onImportFont = onImportFont,
                                    dragGestureModifier = textDockDragGesture,
                                    popupsBelowBar = textPopupsBelowBar,
                                    onPopupStateChange = { richTextController.showCursorOverride = !it },
                                    // Paragraph row is flowing-rich-text only;
                                    // open drafts stay on the single-row bar.
                                    paragraphState = if (textDraft != null) {
                                        null
                                    } else {
                                        richTextController.richParagraphUiState()
                                    },
                                    onNumberedListClick = {
                                        richTextController.toggleRichListType(SharedPdfRichListType.NUMBERED)
                                    },
                                    onBulletedListClick = {
                                        richTextController.toggleRichListType(SharedPdfRichListType.BULLET)
                                    },
                                    onAlignmentSelected = {
                                        richTextController.setRichParagraphAlignment(it)
                                    },
                                )
                            }
                        }
                    }
                }
                // Android parity (ReflowProgressOverlay): a non-blocking top
                // strip instead of a fullscreen scrim, so reading continues
                // during generation. Gated on chrome like showStandardBars.
                AnimatedVisibility(
                    visible = showChrome && !readerState.isSearchActive && pdfReflowUiState.isGenerating,
                    enter = slideInVertically(animationSpec = tween(PdfChromeMotionDurationMillis)) { -it } + fadeIn(animationSpec = tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(animationSpec = tween(PdfChromeMotionDurationMillis)) { -it } + fadeOut(animationSpec = tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    SharedMobilePdfReflowProgressOverlay(progress = pdfReflowUiState.progress)
                }
            }
        }
        pendingExternalLink?.let { url ->
            AlertDialog(
                onDismissRequest = { pendingExternalLink = null },
                title = { Text(readerString("dialog_external_link_title", "Open external link?")) },
                text = {
                    Text(
                        readerString(
                            "desc_external_link_warning",
                            "This PDF wants to open an external link:\n\n%1\$s",
                            url,
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openSharedMobileExternalUrl(url)
                            pendingExternalLink = null
                        }
                    ) {
                        Text(readerString("action_visit", "Visit"))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                copyToClipboard(url, copiedLinkLabel)
                                pendingExternalLink = null
                            }
                        ) {
                            Text(readerString("action_copy", "Copy"))
                        }
                        TextButton(onClick = { pendingExternalLink = null }) {
                            Text(readerString("action_cancel", "Cancel"))
                        }
                    }
                },
            )
        }
        if (showThemePanel) {
            SharedMobilePdfThemePanel(
                settings = readerDefaultSettings.copy(
                    themeId = readerState.themeId,
                    textureAlpha = 1f - globalTextureTransparency,
                    pdfReverseColorMode = readerState.reverseColorMode,
                    pdfPreserveImageColors = readerState.preserveImageColors,
                ),
                customThemes = customReaderThemes,
                onCustomThemesChange = onCustomReaderThemesChange,
                onSettingsChange = { settings ->
                    globalTextureTransparency = 1f - settings.textureAlpha.coerceIn(0f, 1f)
                    if (settings.themeId != readerState.themeId) {
                        settings.themeId?.let { dispatch(SharedPdfReaderAction.ThemeChanged(it)) }
                        showThemePanel = false
                    }
                    if (settings.pdfReverseColorMode != readerState.reverseColorMode) {
                        dispatch(SharedPdfReaderAction.ReverseColorModeChanged(settings.pdfReverseColorMode))
                    }
                    if (settings.pdfPreserveImageColors != readerState.preserveImageColors) {
                        dispatch(SharedPdfReaderAction.PreserveImageColorsChanged(settings.pdfPreserveImageColors))
                    }
                    onReaderDefaultSettingsChange(settings)
                },
                onDismiss = { showThemePanel = false }
            )
        }
        if (showReaderOptions) {
            // Android benchmark (PdfSettingsSheets.kt:150-214 PdfVisualOptionsSheet):
            // the shared sheet owns toolbar toggles + localized copy; the
            // right-to-left row is mobile-only (shown via the optional callback).
            SharedPdfVisualOptionsSheet(
                displayMode = readerState.displayMode,
                systemUiMode = systemUiMode.toReaderSystemUiMode(),
                pageSpreadMode = if (useTwoPageSpread) {
                    ReaderPageSpreadMode.TWO_PAGE
                } else {
                    ReaderPageSpreadMode.SINGLE
                },
                firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                showVerticalPageGap = showVerticalPageGap,
                showPageNumberOverlay = showPageNumberOverlay,
                showTopToolbar = showTopToolbar,
                showBottomToolbar = showBottomToolbar,
                onPageSpreadModeChange = {
                    val twoPage = it == ReaderPageSpreadMode.TWO_PAGE
                    useTwoPageSpread = twoPage
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(
                            pageSpreadMode = if (twoPage) {
                                ReaderPageSpreadMode.TWO_PAGE
                            } else {
                                ReaderPageSpreadMode.SINGLE
                            }
                        )
                    )
                },
                onFirstPageStandaloneInSpreadChange = {
                    firstPageStandaloneInSpread = it
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(pdfFirstPageStandaloneInSpread = it)
                    )
                },
                onSystemUiModeChange = { mode ->
                    systemUiMode = mode.toSharedMobilePdfSystemUiMode()
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(systemUiMode = mode)
                    )
                },
                onShowVerticalPageGapChange = {
                    showVerticalPageGap = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfVerticalPageGapVisible = it))
                },
                onShowPageNumberOverlayChange = {
                    showPageNumberOverlay = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfPageNumberOverlayVisible = it))
                },
                onShowTopToolbarChange = { visible ->
                    showTopToolbar = visible
                    onShowTopToolbarChange(visible)
                },
                onShowBottomToolbarChange = { visible ->
                    showBottomToolbar = visible
                    onShowBottomToolbarChange(visible)
                },
                rightToLeftPagination = rightToLeftPagination,
                onRightToLeftPaginationChange = {
                    rightToLeftPagination = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(rightToLeftPagination = it))
                },
                maxSheetHeight = 680.dp,
                labels = SharedPdfVisualOptionsLabels(
                    title = readerString("menu_visual_options", "Visual Options"),
                    close = readerString("action_close", "Close"),
                    systemUi = readerString("visual_options_system_ui", "System UI (Status & Navigation Bars)"),
                    systemUiDescription = readerString(
                        "visual_options_system_ui_desc",
                        "Control the visibility of the device's system bars."
                    ),
                    systemUiOptions = mapOf(
                        SystemUiMode.DEFAULT to readerString("label_always_show", "Always Show"),
                        SystemUiMode.SYNC to readerString("label_sync_with_menus", "Sync with Menus"),
                        SystemUiMode.HIDDEN to readerString("label_always_hide", "Always Hide"),
                    ),
                    toolbars = readerString("visual_options_toolbars", "Toolbars"),
                    topToolbar = readerString("visual_options_top_toolbar", "Show top toolbar"),
                    topToolbarDescription = readerString(
                        "visual_options_top_toolbar_desc",
                        "Hide it to read with only the bottom tools"
                    ),
                    bottomToolbar = readerString("visual_options_bottom_toolbar", "Show bottom toolbar"),
                    bottomToolbarDescription = readerString(
                        "visual_options_bottom_toolbar_desc",
                        "Hide it to read with only the top tools"
                    ),
                    pageLayout = readerString("visual_options_page_layout", "Page layout"),
                    pageSpread = readerString("visual_options_pdf_page_spread", "PDF page spread"),
                    spreadOptions = mapOf(
                        ReaderPageSpreadMode.SINGLE to readerString(
                            "visual_options_pdf_spread_single",
                            "Single page"
                        ),
                        ReaderPageSpreadMode.TWO_PAGE to readerString(
                            "visual_options_pdf_spread_two",
                            "Two pages"
                        ),
                    ),
                    firstPageAlone = readerString(
                        "visual_options_pdf_first_page_alone",
                        "First page alone"
                    ),
                    firstPageAloneDescription = readerString(
                        "visual_options_pdf_first_page_alone_desc",
                        "Starts facing-page spreads after the cover page."
                    ),
                    removePageGap = readerString(
                        "visual_options_remove_page_gap",
                        "Remove gap between pages"
                    ),
                    removePageGapDescription = readerString(
                        "visual_options_remove_page_gap_desc",
                        "Applies to vertical reading and two-page spreads."
                    ),
                    hidePageNumberOverlay = readerString(
                        "visual_options_hide_page_number_overlay",
                        "Hide page number overlay"
                    ),
                    hidePageNumberOverlayDescription = readerString(
                        "visual_options_hide_page_number_overlay_desc",
                        "Removes the small page count label from each page."
                    ),
                    rightToLeft = readerString(
                        "menu_right_to_left_pagination",
                        "Paginated (right-to-left)"
                    ),
                    rightToLeftDescription = readerString(
                        "visual_options_pdf_right_to_left",
                        "Use right-to-left page order and edge navigation."
                    ),
                ),
                onDismiss = { showReaderOptions = false }
            )
        }
        if (readerExtrasState.aiResult.hasContent) {
            SharedReaderAiResultSheet(
                result = readerExtrasState.aiResult,
                onDismiss = { pendingSummarySave = null; onAiResultDismiss() },
            )
        }
    }

    noteAnnotationId?.let { annotationId ->
        val annotation = readerState.annotations.firstOrNull { it.id == annotationId }
        if (annotation != null) {
            SharedMobilePdfAnnotationBottomSheet(
                annotation = annotation,
                onUpdate = { dispatch(SharedPdfReaderAction.AnnotationUpdated(it)) },
                onDelete = {
                    dispatch(SharedPdfReaderAction.AnnotationDeleted(annotationId))
                    noteAnnotationId = null
                },
                onReadAloud = {
                    requestTts(
                        pageIndex = annotation.pageIndex,
                        startCharIndex = annotation.rangeStartIndex ?: 0,
                    )
                    noteAnnotationId = null
                },
                onClipboardError = onClipboardError,
                onDismiss = { noteAnnotationId = null }
            )
        } else {
            noteAnnotationId = null
        }
    }
    if (showFileInformation) {
        SharedBookInfoDialog(
            book = book,
            knownTags = knownTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            canEditEmbeddedMetadata = false,
            canRenameDisplayName = true,
            canRestoreEmbeddedMetadata = false,
            onDismiss = { showFileInformation = false },
            onSave = { updated ->
                onBookInfoChange(updated)
                showFileInformation = false
            },
            onRestore = {},
        )
    }
    if (showAiHub) {
        // Fixed-size window of text sessions, newest-first, opened only while
        // the hub is presented; disposal closes them on dismiss.
        val hubBasePage = readerState.pageIndex
        val hubPageTitle = readerString("pdf_page_label", "Page %1\$d", hubBasePage + 1)
        val hubBookTitle = book.title?.takeIf { it.isNotBlank() } ?: book.displayName
        val hubPageSessions = (0 until PDF_AI_HUB_RECAP_PAGE_WINDOW).map { back ->
            rememberPdfTextPageSession(book, (hubBasePage - back).coerceAtLeast(0), pdfPassword)
        }
        val hubCacheEntries = remember(readerSessionKey, aiCacheRevision) {
            summaryCache?.getAllSummaries(hubBookTitle).orEmpty()
        }
        SharedMobileAiHubSheet(
            sectionTitle = hubPageTitle,
            cachedSummary = hubCacheEntries.firstOrNull { it.sectionIndex == hubBasePage },
            cacheEntries = hubCacheEntries,
            showCacheTab = summaryCache != null,
            credits = aiCredits,
            onGenerateSummary = {
                showAiHub = false
                hubPageSessions.firstOrNull()?.let { session ->
                    session.textForRange(0, session.pageCharCount)
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            pendingSummarySave = Triple(hubBookTitle, hubBasePage, hubPageTitle)
                            onAiAction(ReaderAiFeature.SUMMARIZE, it.take(PDF_AI_HUB_MAX_CHARS))
                        }
                }
            },
            onGenerateRecap = {
                showAiHub = false
                val pages = hubPageSessions.map { session ->
                    session?.let { it.textForRange(0, it.pageCharCount) }
                }
                buildPdfAiHubRecapText(pages)?.let { onAiAction(ReaderAiFeature.RECAP, it) }
            },
            onDeleteCached = { entry ->
                summaryCache?.deleteSummary(entry.bookTitle, entry.sectionIndex)
                aiCacheRevision++
            },
            onClearCache = {
                summaryCache?.clearBookCache(hubBookTitle)
                aiCacheRevision++
            },
            onDismiss = { showAiHub = false },
        )
    }
    LaunchedEffect(readerExtrasState.aiResult) {
        val pending = pendingSummarySave ?: return@LaunchedEffect
        val result = readerExtrasState.aiResult
        if (result.isLoading) return@LaunchedEffect
        pendingSummarySave = null
        if (result.errorMessage == null &&
            result.text.isNotBlank() &&
            result.title == ReaderAiFeature.SUMMARIZE.displayName
        ) {
            summaryCache?.saveSummary(pending.first, pending.second, pending.third, result.text)
            aiCacheRevision++
        }
    }
    if (documentRender.openError == SharedMobilePdfOpenError.PASSWORD_REQUIRED) {
        SharedMobilePdfPasswordDialog(
            labels = SharedMobilePdfPasswordLabels(
                title = readerString("title_password_protected", "Password Protected"),
                description = readerString(
                    "desc_password_protected",
                    "This document is encrypted. Please enter the password to view it."
                ),
                password = readerString("password", "Password"),
                incorrectPassword = readerString("error_incorrect_password", "Incorrect password"),
                showPassword = readerString("content_desc_show_password", "Show password"),
                hidePassword = readerString("content_desc_hide_password", "Hide password"),
                open = readerString("action_open", "Open"),
                cancel = readerString("action_cancel", "Cancel"),
            ),
            // Android benchmark (PdfViewerScreen.kt:4705): explicit error state —
            // a password was tried and rejected, not merely "a draft exists".
            isError = pdfPassword != null,
            onDismiss = closeReader,
            onConfirm = { password ->
                pdfPasswordDraft = password
                pdfPassword = password
            },
        )
    }
    if (showShareFormatChoice) {
        SharedMobileDocumentFormatDialog(
            title = readerString("share_chooser_title", "Share PDF"),
            description = readerString("desc_choose_format_share", "Choose format to share:"),
            annotatedLabel = readerString("action_with_annotations", "With Annotations"),
            originalLabel = readerString("action_original", "Original"),
            cancelLabel = readerString("action_cancel", "Cancel"),
            onAnnotated = {
                showShareFormatChoice = false
                dispatchNativePdfAction(SharedMobilePdfNativeAction.SHARE_ANNOTATED, SharedPdfExportSnapshot(readerState.copy(richTextDocumentJson = richTextDocumentJson), richTextController.pageLayouts))
            },
            onOriginal = {
                showShareFormatChoice = false
                dispatchNativePdfAction(SharedMobilePdfNativeAction.SHARE_ORIGINAL, SharedPdfExportSnapshot(readerState.copy(richTextDocumentJson = richTextDocumentJson), richTextController.pageLayouts))
            },
            onDismiss = { showShareFormatChoice = false },
        )
    }
    // Android benchmark (PdfViewerScreen.kt:5130-5150): Save Copy offers the
    // same annotated/original choice instead of exporting blindly. "Original"
    // strips reader-owned content so the host exports the source bytes
    // (SharedPdfExportMode.ORIGINAL), exactly like an unannotated document.
    if (showSaveFormatChoice) {
        SharedMobileDocumentFormatDialog(
            title = readerString("title_save_to_device", "Save to Device"),
            description = readerString("desc_choose_format_save", "Choose format to save:"),
            annotatedLabel = readerString("action_with_annotations", "With Annotations"),
            originalLabel = readerString("action_original", "Original"),
            cancelLabel = readerString("action_cancel", "Cancel"),
            onAnnotated = {
                showSaveFormatChoice = false
                dispatchNativePdfAction(
                    SharedMobilePdfNativeAction.SAVE_COPY,
                    SharedPdfExportSnapshot(readerState.copy(richTextDocumentJson = richTextDocumentJson), richTextController.pageLayouts)
                )
            },
            onOriginal = {
                showSaveFormatChoice = false
                dispatchNativePdfAction(
                    SharedMobilePdfNativeAction.SAVE_COPY,
                    SharedPdfExportSnapshot(
                        readerState.copy(
                            annotations = emptyList(),
                            blankPageInsertions = emptyList(),
                            richTextDocumentJson = ""
                        )
                    )
                )
            },
            onDismiss = { showSaveFormatChoice = false },
        )
    }
    // Android benchmark (PdfViewerScreen.kt:5171-5199): blocking scrim while
    // the host prepares the export file.
    if (isPdfExportBusy) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp), strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = readerString("msg_preparing_pdf", "Preparing PDF…"),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
    if (showBrightnessSheet) {
        SharedMobileReaderBrightnessSheet(
            brightness = readerBrightness,
            rememberedCustomBrightness = readerCustomBrightness,
            onBrightnessChange = onReaderBrightnessChange,
            onDismiss = { showBrightnessSheet = false },
        )
    }
    if (showScreenOrientationSheet) {
        SharedMobileReaderScreenOrientationSheet(
            selectedMode = readerScreenOrientationMode,
            onModeSelected = onReaderScreenOrientationModeChange,
            onDismiss = { showScreenOrientationSheet = false },
        )
    }
    if (showOcrLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showOcrLanguageDialog = false },
            title = { Text("OCR language") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SharedPdfOcrLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOcrLanguageChange(language)
                                    showOcrLanguageDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = language == ocrLanguage,
                                onClick = {
                                    onOcrLanguageChange(language)
                                    showOcrLanguageDialog = false
                                },
                            )
                            Text(language.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOcrLanguageDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showToolbarCustomization) {
        SharedMobilePdfToolbarCustomizationSheet(
            preferences = sanitizedPdfToolbarPreferences,
            availableTools = SharedMobilePdfAvailableTools,
            onPreferencesChange = onPdfToolbarPreferencesChange,
            onDismiss = { showToolbarCustomization = false },
        )
    }
    if (showTtsReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showTtsReplacementsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp)
            ) {
                SharedReaderTtsReplacementControls(
                    preferences = readerTtsReplacementPreferences,
                    bookId = book.id,
                    onPreferencesChange = onReaderTtsReplacementPreferencesChange,
                )
            }
        }
    }
    if (showTtsSettingsSheet) {
        SharedMobileReaderTtsSettingsSheet(
            tts = pdfTts,
            onDismiss = { showTtsSettingsSheet = false },
            cloudTts = cloudTts,
            cloudTtsModeEnabled = cloudTtsModeEnabled,
            onCloudTtsModeChange = onCloudTtsModeChange,
            cloudTtsVoiceId = cloudTtsVoiceId,
            onCloudTtsVoiceChange = onCloudTtsVoiceChange,
            onClearCloudTtsCache = onClearCloudTtsCache,
        )
    }
    if (showNewPdfTabSheet) {
        ModalBottomSheet(onDismissRequest = { showNewPdfTabSheet = false }) {
            val openIds = openPdfTabs.mapTo(mutableSetOf()) { it.id }
            val candidates = availablePdfTabBooks
                .filter { it.type == FileType.PDF && it.id !in openIds }
                .sortedByDescending { it.timestamp }
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    readerString("title_add_pdf_to_tab", "Add PDF to tab"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                if (candidates.isEmpty()) {
                    Text(
                        readerString("msg_no_other_pdfs_found", "No other PDFs found"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(candidates, key = { "new_pdf_tab_${it.id}" }) { candidate ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        candidate.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = candidate.author?.let { author ->
                                    { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                },
                                modifier = Modifier.clickable {
                                    showNewPdfTabSheet = false
                                    onOpenPdfTab(candidate)
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SharedMobilePdfReaderTabStrip(
    tabs: List<BookItem>,
    activeBookId: String?,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues(),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(tabs, key = { "pdf_reader_tab_${it.id}" }) { tab ->
            val selected = tab.id == activeBookId
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .height(if (selected) 48.dp else 36.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onOpenTab(tab) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onCloseTab(tab) }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = readerString("close_tab", "Close tab"),
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                }
            }
        }
        item {
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).size(36.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = readerString("content_desc_new_tab", "New tab"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfReaderTopBar(
    title: String,
    pageIndex: Int,
    pageLabel: String,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    isSearchActive: Boolean,
    searchQuery: String,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onOpenSplit: (() -> Unit)? = null,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onTheme: () -> Unit,
    onVisualOptions: () -> Unit,
    tapToTurnPages: Boolean,
    onToggleTapToTurnPages: () -> Unit,
    pageTurnAnimationEnabled: Boolean,
    onTogglePageTurnAnimation: () -> Unit,
    isScrollLocked: Boolean,
    onToggleScrollLock: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    autoScrollEnabled: Boolean,
    onToggleAutoScroll: () -> Unit,
    showAllTextHighlights: Boolean,
    isAllTextHighlightLoading: Boolean,
    onToggleHighlights: () -> Unit,
    onHighlighterTool: () -> Unit,
    onEditMode: () -> Unit,
    onShowSlider: () -> Unit,
    onToggleTts: () -> Unit,
    onVoiceSettings: () -> Unit,
    onWordReplacements: () -> Unit,
    onNativeAction: (SharedMobilePdfNativeAction) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onOpenAiHub: () -> Unit = {},
    aiAvailable: Boolean = false,
    ocrLanguage: SharedPdfOcrLanguage,
    onOcrLanguage: () -> Unit,
    isCurrentPageBlank: Boolean = false,
    onInsertBlankPage: () -> Unit = {},
    onDeleteBlankPage: () -> Unit = {},
    pdfReflowUiState: SharedMobilePdfReflowUiState,
    onFileInformation: () -> Unit,
    onBrightness: () -> Unit,
    onScreenOrientation: () -> Unit,
    isTtsPlayingOrLoading: Boolean,
    rightToLeftPagination: Boolean = false,
    onRightToLeftPaginationChange: (Boolean) -> Unit = {},
    topTools: List<PdfReaderTool>,
    toolbarPreferences: PdfToolbarPreferences,
    onCustomizeToolbar: () -> Unit,
    applySystemBarInsets: Boolean,
    isDebugBuild: Boolean = false,
    onDrawDebugDemoAnnotations: () -> Unit = {},
) {
    val searchFocusRequester = remember { FocusRequester() }
    val searchKeyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHiddenToolsExpanded by remember { mutableStateOf(false) }
    var showReadingModeExpanded by remember { mutableStateOf(false) }
    var showTtsSettingsExpanded by remember { mutableStateOf(false) }
    var showFileActionsExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applySystemBarInsets) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    } else {
                        Modifier
                    }
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("tooltip_back", "Back"))
            }
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text(readerString("desktop_type_to_search_pdf", "Search PDF")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        // Android benchmark (AndroidSearchUi.kt:271-282): a clear
                        // affordance empties the query; closing is separate.
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) onSearchQueryChange("") else onCloseSearch()
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = if (searchQuery.isNotBlank()) {
                                    readerString("action_clear_search", "Clear search")
                                } else {
                                    readerString("content_desc_close_search", "Close search")
                                }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        // Android benchmark (:247-268): IME Search confirms the
                        // query — search is live, so dismiss the keyboard.
                        onSearch = { searchKeyboardController?.hide() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                )
            } else {
                Text(
                    text = sharedPdfPageRangeLabel(pageLabel, pageCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .weight(0.3f)
                )
                // Arrangement cannot right-align content inside a horizontally
                // scrollable Row, so the scroll row is wrapped in a Box that
                // aligns it to the bar's end edge: icons hug the right when
                // they fit and scroll from the start when they overflow.
                Box(
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        topTools.forEach { tool ->
                            when (tool) {
                                PdfReaderTool.DICTIONARY -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) }) { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }
                                PdfReaderTool.SPLIT_VIEW -> if (onOpenSplit != null) SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onOpenSplit) { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                                PdfReaderTool.THEME -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onTheme) { Icon(Icons.Default.Palette, contentDescription = null) }
                                PdfReaderTool.BRIGHTNESS -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = null) }
                                PdfReaderTool.LOCK_PANNING -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool, isScrollLocked = isScrollLocked), onToggleScrollLock) { Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null) }
                                PdfReaderTool.SLIDER -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onShowSlider, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(SharedReaderIcons.Slider, contentDescription = null) }
                                PdfReaderTool.TOC -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onOpenDrawer, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Menu, contentDescription = null) }
                                PdfReaderTool.SEARCH -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onSearch, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Search, contentDescription = null) }
                                PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfTopToolButton(
                                    sharedPdfReaderToolLabel(tool),
                                    onClick = onToggleHighlights,
                                    isActive = showAllTextHighlights,
                                    isLoading = isAllTextHighlightLoading,
                                ) { Icon(SharedReaderIcons.HighlightText, contentDescription = null) }
                                PdfReaderTool.EDIT_MODE -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onEditMode) { Icon(Icons.Default.Edit, contentDescription = null) }
                                PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool, isTtsPlayingOrLoading = isTtsPlayingOrLoading), onToggleTts, isActive = isTtsPlayingOrLoading) { Icon(if (isTtsPlayingOrLoading) Icons.Default.Close else SharedReaderIcons.TextToSpeech, contentDescription = null) }
                                PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }
                                PdfReaderTool.AI_FEATURES -> if (aiAvailable) SharedMobilePdfTopToolButton(sharedPdfReaderToolLabel(tool), onOpenAiHub) { Icon(Icons.Default.Ai, contentDescription = null) }
                                else -> Unit
                            }
                        }
                        if (isDebugBuild) {
                            // Android parity (PdfToolbars includeDebugActions):
                            // debug-only "Try Episteme!" demo artwork.
                            SharedMobilePdfTopToolButton(
                                readerString("tooltip_demo_annotations", "Demo annotations"),
                                onDrawDebugDemoAnnotations,
                            ) { Icon(Icons.Filled.BugReport, contentDescription = null) }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = {
                    showHiddenToolsExpanded = false
                    showReadingModeExpanded = false
                    showFileActionsExpanded = false
                    showMoreMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = readerString("tooltip_more_options", "PDF options"))
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                SharedMobilePdfOverflowItem(
                    readerString("title_customize_toolbar", "Customize Toolbar"),
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onCustomizeToolbar()
                    }
                )
                val hiddenToolbarTools = toolbarPreferences.toolOrder.filter {
                    it in SharedMobilePdfAvailableTools &&
                        it.supportsToolbarPlacement &&
                        !toolbarPreferences.isVisible(it)
                }
                if (hiddenToolbarTools.isNotEmpty()) SharedMobilePdfOverflowItem(
                    readerString("toolbar_hidden_tools_menu", "Hidden tools"),
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded }
                )
                if (showHiddenToolsExpanded) {
                    hiddenToolbarTools.forEach { tool ->
                        val closeMenuAndRun: (() -> Unit) -> Unit = { action ->
                            showHiddenToolsExpanded = false
                            showMoreMenu = false
                            action()
                        }
                        when (tool) {
                            PdfReaderTool.DICTIONARY -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), leadingIcon = { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }, onClick = { closeMenuAndRun { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) } })
                            PdfReaderTool.SPLIT_VIEW -> if (onOpenSplit != null) SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }, onClick = { closeMenuAndRun(onOpenSplit) })
                            PdfReaderTool.THEME -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), onClick = { closeMenuAndRun(onTheme) })
                            PdfReaderTool.BRIGHTNESS -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), leadingIcon = { Icon(SharedReaderIcons.Contrast, contentDescription = null) }, onClick = { closeMenuAndRun(onBrightness) })
                            PdfReaderTool.LOCK_PANNING -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool, isScrollLocked = isScrollLocked), onClick = { closeMenuAndRun(onToggleScrollLock) })
                            PdfReaderTool.SLIDER -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onShowSlider) })
                            PdfReaderTool.TOC -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onOpenDrawer) })
                            PdfReaderTool.SEARCH -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onSearch) })
                            PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfOverflowItem(
                                sharedPdfReaderToolLabel(tool),
                                leadingIcon = {
                                    if (isAllTextHighlightLoading) {
                                        CircularProgressIndicator(Modifier.size(20.dp))
                                    } else {
                                        Icon(
                                            SharedReaderIcons.HighlightText,
                                            contentDescription = null,
                                            tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                },
                                onClick = { closeMenuAndRun(onToggleHighlights) }
                            )
                            PdfReaderTool.EDIT_MODE -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), onClick = { closeMenuAndRun(onEditMode) })
                            PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfOverflowItem(sharedPdfReaderToolTitle(tool), onClick = { closeMenuAndRun(onToggleTts) })
                            PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), leadingIcon = { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }, onClick = { closeMenuAndRun(onScreenOrientation) })
                            PdfReaderTool.AI_FEATURES -> if (aiAvailable) SharedMobilePdfOverflowItem(sharedPdfReaderToolLabel(tool), leadingIcon = { Icon(Icons.Default.Ai, contentDescription = null) }, onClick = { closeMenuAndRun(onOpenAiHub) })
                            else -> Unit
                        }
                    }
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.FILE_INFO)) SharedMobilePdfOverflowItem(
                    sharedPdfReaderToolTitle(PdfReaderTool.FILE_INFO),
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onFileInformation()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.OCR_LANGUAGE)) SharedMobilePdfOverflowItem(
                    "${readerString("menu_ocr_language", "OCR Language")}: ${ocrLanguage.displayName}",
                    onClick = { showMoreMenu = false; onOcrLanguage() }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.VISUAL_OPTIONS)) SharedMobilePdfOverflowItem(
                    sharedPdfReaderToolTitle(PdfReaderTool.VISUAL_OPTIONS),
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onVisualOptions()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) SharedMobilePdfOverflowItem(
                    readerString("menu_change_reading_mode", "Change Reading Mode"),
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showReadingModeExpanded = !showReadingModeExpanded }
                )
                if (showReadingModeExpanded && toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) {
                    SharedMobilePdfOverflowItem(
                        readerString("menu_reading_mode_vertical", "Vertical"),
                        enabled = !isTtsPlayingOrLoading,
                        trailingIcon = { if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) },
                        onClick = {
                            if (displayMode != PdfDisplayMode.VERTICAL_SCROLL) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                    SharedMobilePdfOverflowItem(
                        readerString("menu_reading_mode_paginated", "Paginated (left-to-right)"),
                        enabled = !isTtsPlayingOrLoading,
                        trailingIcon = { if (displayMode == PdfDisplayMode.PAGINATION && !rightToLeftPagination) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) },
                        onClick = {
                            if (displayMode != PdfDisplayMode.PAGINATION || rightToLeftPagination) {
                                onRightToLeftPaginationChange(false)
                                if (displayMode != PdfDisplayMode.PAGINATION) onToggleDisplayMode()
                            }
                            showMoreMenu = false
                        }
                    )
                    SharedMobilePdfOverflowItem(
                        readerString("menu_right_to_left_pagination", "Paginated (right-to-left)"),
                        enabled = !isTtsPlayingOrLoading,
                        trailingIcon = { if (displayMode == PdfDisplayMode.PAGINATION && rightToLeftPagination) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) },
                        onClick = {
                            if (displayMode != PdfDisplayMode.PAGINATION || !rightToLeftPagination) {
                                onRightToLeftPaginationChange(true)
                                if (displayMode != PdfDisplayMode.PAGINATION) onToggleDisplayMode()
                            }
                            showMoreMenu = false
                        }
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.TAP_TO_TURN)) SharedMobilePdfOverflowItem(
                    readerString("menu_tap_to_turn_pages", "Tap to Turn Pages"),
                    enabled = displayMode == PdfDisplayMode.PAGINATION,
                    trailingIcon = { if (tapToTurnPages) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Enabled")) },
                    onClick = {
                        showMoreMenu = false
                        onToggleTapToTurnPages()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.PAGE_TURN_ANIM)) SharedMobilePdfOverflowItem(
                    readerString("menu_realistic_page_turns", "Realistic Page Turns"),
                    enabled = displayMode == PdfDisplayMode.PAGINATION,
                    trailingIcon = { if (pageTurnAnimationEnabled) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Enabled")) },
                    onClick = {
                        showMoreMenu = false
                        onTogglePageTurnAnimation()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.KEEP_SCREEN_ON)) SharedMobilePdfOverflowItem(
                    readerString("menu_keep_screen_on", "Keep Screen On"),
                    trailingIcon = { if (keepScreenOn) Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Enabled")) },
                    onClick = {
                        showMoreMenu = false
                        onToggleKeepScreenOn()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.AUTO_SCROLL)) SharedMobilePdfOverflowItem(
                    readerString("menu_auto_scroll", "Auto Scroll"),
                    enabled = !isTtsPlayingOrLoading && displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                    onClick = {
                        showMoreMenu = false
                        onToggleAutoScroll()
                    }
                )
                val showVoiceSettings = PdfReaderTool.TTS_SETTINGS in SharedMobilePdfAvailableTools &&
                    toolbarPreferences.isVisible(PdfReaderTool.TTS_SETTINGS)
                val showWordReplacements = PdfReaderTool.TTS_REPLACEMENTS in SharedMobilePdfAvailableTools &&
                    toolbarPreferences.isVisible(PdfReaderTool.TTS_REPLACEMENTS)
                if (showVoiceSettings || showWordReplacements) SharedMobilePdfOverflowItem(
                    readerString("menu_tts_settings", "TTS Settings"),
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded }
                )
                if (showTtsSettingsExpanded) {
                    if (showVoiceSettings) SharedMobilePdfOverflowItem(
                        readerString("menu_tts_voice_settings", "TTS Voice Settings"),
                        enabled = !isTtsPlayingOrLoading,
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onVoiceSettings()
                        },
                    )
                    if (showWordReplacements) SharedMobilePdfOverflowItem(
                        readerString("menu_tts_word_replacements", "TTS Word Replacements"),
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onWordReplacements()
                        },
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.BOOKMARK)) SharedMobilePdfOverflowItem(
                    readerString(
                        if (isBookmarked) "menu_remove_bookmark" else "menu_bookmark_this_page",
                        if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                    ),
                    onClick = {
                        showMoreMenu = false
                        onToggleBookmark()
                    }
                )
                if (PdfReaderTool.PAGE_MANAGEMENT in SharedMobilePdfAvailableTools && toolbarPreferences.isVisible(PdfReaderTool.PAGE_MANAGEMENT)) {
                    SharedMobilePdfOverflowItem(
                        readerString("menu_insert_blank_page", "Insert Blank Page"),
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onInsertBlankPage()
                        }
                    )
                    if (isCurrentPageBlank) {
                        SharedMobilePdfOverflowItem(
                            "${readerString("action_delete", "Delete")} blank page",
                            isError = true,
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteBlankPage()
                            }
                        )
                    }
                }
                if (PdfReaderTool.REFLOW in SharedMobilePdfAvailableTools && toolbarPreferences.isVisible(PdfReaderTool.REFLOW)) SharedMobilePdfOverflowItem(
                    when {
                        pdfReflowUiState.isGenerating -> "Generating Text View…"
                        pdfReflowUiState.hasReflowBook -> "Open Text View"
                        else -> "Generate Text View"
                    },
                    enabled = !pdfReflowUiState.isGenerating,
                    leadingIcon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onNativeAction(SharedMobilePdfNativeAction.TEXT_VIEW)
                    }
                )
                if (listOf(PdfReaderTool.SHARE, PdfReaderTool.SAVE_COPY, PdfReaderTool.PRINT).any(toolbarPreferences::isVisible)) SharedMobilePdfOverflowItem(
                    readerString("menu_share_save_print", "Share, Save or Print"),
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showFileActionsExpanded = !showFileActionsExpanded }
                )
                if (showFileActionsExpanded) {
                    if (toolbarPreferences.isVisible(PdfReaderTool.SHARE)) SharedMobilePdfOverflowItem(sharedPdfReaderToolTitle(PdfReaderTool.SHARE), leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SHARE) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.SAVE_COPY)) SharedMobilePdfOverflowItem(sharedPdfReaderToolTitle(PdfReaderTool.SAVE_COPY), leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SAVE_COPY) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.PRINT)) SharedMobilePdfOverflowItem(sharedPdfReaderToolTitle(PdfReaderTool.PRINT), leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.PRINT) })
                }
                if (isDebugBuild) {
                    // Android parity (PdfToolbars import_svg/demo_annotations):
                    // debug-only "Try Episteme!" demo artwork.
                    SharedMobilePdfOverflowItem(
                        readerString("tooltip_demo_annotations", "Demo annotations"),
                        leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onDrawDebugDemoAnnotations()
                        }
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun sharedPdfReaderToolTitle(tool: PdfReaderTool): String = when (tool) {
    PdfReaderTool.DICTIONARY -> readerString("content_desc_dictionary_settings", "Dictionary Settings")
    PdfReaderTool.SPLIT_VIEW -> readerString("tool_split_view", "Split View")
    PdfReaderTool.THEME -> readerString("tooltip_theme", "Theme")
    PdfReaderTool.BRIGHTNESS -> readerString("tool_brightness", "Brightness")
    PdfReaderTool.LOCK_PANNING -> readerString("tooltip_lock_pan", "Lock Panning")
    PdfReaderTool.FILE_INFO -> readerString("file_information", "File Information")
    PdfReaderTool.VISUAL_OPTIONS -> readerString("menu_visual_options", "Visual Options")
    PdfReaderTool.TAP_TO_TURN -> readerString("menu_tap_to_turn_pages", "Tap to Turn Pages")
    PdfReaderTool.PAGE_TURN_ANIM -> readerString("menu_realistic_page_turns", "Realistic Page Turns")
    PdfReaderTool.SLIDER -> readerString("tool_navigation_slider", "Navigation Slider")
    PdfReaderTool.TOC -> readerString("tool_sidebar", "Sidebar")
    PdfReaderTool.SEARCH -> readerString("tooltip_search", "Search")
    PdfReaderTool.HIGHLIGHT_ALL -> readerString("tool_highlight_selectable_text", "Highlight selectable text")
    PdfReaderTool.AI_FEATURES -> readerString("tooltip_ai", "AI Features")
    PdfReaderTool.EDIT_MODE -> readerString("tool_edit_mode", "Edit Mode")
    PdfReaderTool.TTS_CONTROLS -> readerString("tool_tts_controls", "TTS Controls")
    PdfReaderTool.OCR_LANGUAGE -> readerString("menu_ocr_language", "OCR Language")
    PdfReaderTool.READING_MODE -> readerString("tool_reading_mode", "Reading Mode")
    PdfReaderTool.KEEP_SCREEN_ON -> readerString("menu_keep_screen_on", "Keep Screen On")
    PdfReaderTool.SCREEN_ORIENTATION -> readerString("menu_screen_orientation", "Screen Orientation")
    PdfReaderTool.AUTO_SCROLL -> readerString("menu_auto_scroll", "Auto Scroll")
    PdfReaderTool.TTS_SETTINGS -> readerString("menu_tts_voice_settings", "TTS Voice Settings")
    PdfReaderTool.TTS_REPLACEMENTS -> readerString("menu_tts_word_replacements", "TTS Word Replacements")
    PdfReaderTool.BOOKMARK -> readerString("content_desc_bookmark", "Bookmark")
    PdfReaderTool.PAGE_MANAGEMENT -> readerString("tool_page_management", "Page Management")
    PdfReaderTool.REFLOW -> readerString("tool_text_view_reflow", "Text View (Reflow)")
    PdfReaderTool.SHARE -> readerString("action_share", "Share")
    PdfReaderTool.SAVE_COPY -> readerString("action_save_copy_to_device", "Save copy to device")
    PdfReaderTool.PRINT -> readerString("action_print", "Print")
}

@Composable
private fun sharedPdfReaderToolLabel(
    tool: PdfReaderTool,
    isScrollLocked: Boolean = false,
    isTtsPlayingOrLoading: Boolean = false,
): String = when (tool) {
    PdfReaderTool.SPLIT_VIEW -> readerString("pdf_split_reader_open", "Open in split reader")
    PdfReaderTool.LOCK_PANNING -> readerString(
        if (isScrollLocked) "tooltip_unlock_pan" else "tooltip_lock_pan",
        if (isScrollLocked) "Unlock Panning" else "Lock Panning",
    )
    PdfReaderTool.TTS_CONTROLS -> readerString(
        if (isTtsPlayingOrLoading) "tooltip_tts_stop" else "tooltip_tts_start",
        if (isTtsPlayingOrLoading) "Stop Text-to-Speech" else "Start Text-to-Speech",
    )
    else -> sharedPdfReaderToolTitle(tool)
}

@Composable
private fun SharedMobilePdfTopToolButton(
    label: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label },
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            CompositionLocalProvider(
                LocalContentColor provides if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
            ) {
                icon()
            }
        }
    }
}

private val SharedMobilePdfAvailableTools = setOf(
    PdfReaderTool.DICTIONARY,
    PdfReaderTool.SPLIT_VIEW,
    PdfReaderTool.THEME,
    PdfReaderTool.BRIGHTNESS,
    PdfReaderTool.LOCK_PANNING,
    PdfReaderTool.FILE_INFO,
    PdfReaderTool.VISUAL_OPTIONS,
    PdfReaderTool.TAP_TO_TURN,
    PdfReaderTool.PAGE_TURN_ANIM,
    PdfReaderTool.SLIDER,
    PdfReaderTool.TOC,
    PdfReaderTool.SEARCH,
    PdfReaderTool.HIGHLIGHT_ALL,
    PdfReaderTool.EDIT_MODE,
    PdfReaderTool.TTS_CONTROLS,
    PdfReaderTool.OCR_LANGUAGE,
    PdfReaderTool.TTS_SETTINGS,
    PdfReaderTool.TTS_REPLACEMENTS,
    PdfReaderTool.READING_MODE,
    PdfReaderTool.KEEP_SCREEN_ON,
    PdfReaderTool.SCREEN_ORIENTATION,
    PdfReaderTool.AUTO_SCROLL,
    PdfReaderTool.BOOKMARK,
    PdfReaderTool.PAGE_MANAGEMENT,
    PdfReaderTool.SHARE,
    PdfReaderTool.SAVE_COPY,
    PdfReaderTool.PRINT,
    PdfReaderTool.REFLOW,
    PdfReaderTool.AI_FEATURES,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfToolbarCustomizationSheet(
    preferences: PdfToolbarPreferences,
    availableTools: Set<PdfReaderTool>,
    onPreferencesChange: (PdfToolbarPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var localHiddenTools by remember { mutableStateOf(preferences.hiddenToolIds) }
    var flatItems by remember {
        mutableStateOf(buildSharedPdfToolbarItems(preferences, availableTools))
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberSharedToolbarDragDropState(
        lazyListState = lazyListState,
        flatItems = { flatItems },
        onFlatItemsChange = { flatItems = it },
    )

    val commitDragDrop = {
        val next = buildSharedPdfToolbarCommit(flatItems, localHiddenTools, availableTools)
        localHiddenTools = next.hiddenToolIds
        onPreferencesChange(next)
    }

    val resetToDefault = {
        val defaults = PdfToolbarPreferences()
        localHiddenTools = defaults.hiddenToolIds
        flatItems = buildSharedPdfToolbarItems(defaults, availableTools)
        onPreferencesChange(defaults.sanitized(availableTools))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
        ) {
            SharedToolbarCustomizationHeader(
                title = "Customize Toolbar",
                onReset = resetToDefault,
                onDismiss = onDismiss,
            )
            SharedToolbarDragDropList(
                flatItems = flatItems,
                dragDropState = dragDropState,
                emptyPlaceholderTitle = "Drop tools here",
                moreMenuTitle = "More Menu",
                toolRow = { item, isDragging ->
                    val tool = item.toolId?.let(PdfReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarDragRow(
                            title = sharedPdfReaderToolTitle(tool),
                            isDragging = isDragging,
                            leadingIcon = { SharedPdfToolbarDragIcon(tool) },
                            onDragStart = { dragDropState.onDragStart(item.id) },
                            onDrag = { dragDropState.onDrag(it) },
                            onDragEnd = {
                                dragDropState.onDragEnd()
                                flatItems = sanitizeSharedToolbarPlaceholders(flatItems)
                                commitDragDrop()
                            },
                        )
                    }
                },
                moreToolRow = { item ->
                    val tool = item.toolId?.let(PdfReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarMoreVisibilityRow(
                            title = sharedPdfReaderToolTitle(tool),
                            visible = !localHiddenTools.contains(tool.id),
                            onToggle = {
                                val next = if (localHiddenTools.contains(tool.id)) {
                                    localHiddenTools - tool.id
                                } else {
                                    localHiddenTools + tool.id
                                }
                                localHiddenTools = next
                                onPreferencesChange(preferences.copy(hiddenToolIds = next).sanitized(availableTools))
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SharedMobilePdfReflowProgressOverlay(progress: Float) {
    // Android parity (PdfToolbars.ReflowProgressOverlay): a top strip card
    // with title, percent and linear progress — non-blocking.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = readerString("generating_text_view", "Generating Text View…"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfOverflowItem(
    text: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            leadingIcon?.invoke()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (!enabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            } else if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            trailingIcon?.invoke()
        }
    }
    HorizontalDivider()
}

private enum class SharedMobilePdfSystemUiMode(val label: String) {
    ALWAYS_SHOW("Always Show"),
    SYNC_WITH_MENUS("Sync with Menus"),
    ALWAYS_HIDE("Always Hide")
}

private fun SystemUiMode.toSharedMobilePdfSystemUiMode(): SharedMobilePdfSystemUiMode = when (this) {
    SystemUiMode.DEFAULT -> SharedMobilePdfSystemUiMode.ALWAYS_SHOW
    SystemUiMode.SYNC -> SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS
    SystemUiMode.HIDDEN -> SharedMobilePdfSystemUiMode.ALWAYS_HIDE
}

private fun SharedMobilePdfSystemUiMode.toReaderSystemUiMode(): SystemUiMode = when (this) {
    SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> SystemUiMode.DEFAULT
    SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> SystemUiMode.SYNC
    SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> SystemUiMode.HIDDEN
}

@Composable
private fun SharedMobilePdfReaderBottomBar(
    state: SharedPdfReaderState,
    tools: List<PdfReaderTool>,
    onShowSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    // Android parity: reopening edit mode restores the last tool instead of
    // always resetting to PEN (see toggleEditMode above).
    editModeOpenTool: PdfInkTool = PdfInkTool.PEN,
    ttsState: SharedMobileEpubLocalTtsState,
    isTtsPlayingOrLoading: Boolean,
            onToggleTts: () -> Unit,
            onTheme: () -> Unit,
            onBrightness: () -> Unit,
            onToggleScrollLock: () -> Unit,
    onScreenOrientation: () -> Unit,
    onDictionary: () -> Unit,
    onOpenAiHub: () -> Unit = {},
    aiAvailable: Boolean = false,
            showAllTextHighlights: Boolean = false,
            isAllTextHighlightLoading: Boolean = false,
            onToggleHighlights: () -> Unit = {},
            applySystemBarInsets: Boolean,
            modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .then(
                    if (applySystemBarInsets) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tools.forEach { tool ->
                    when (tool) {
                        PdfReaderTool.SLIDER -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onShowSlider) { Icon(SharedReaderIcons.Slider, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.TOC -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.SEARCH -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfBottomToolButton(
                            selected = showAllTextHighlights,
                            onClick = onToggleHighlights,
                        ) {
                            if (isAllTextHighlightLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(SharedReaderIcons.HighlightText, contentDescription = sharedPdfReaderToolTitle(tool))
                            }
                        }
                        PdfReaderTool.EDIT_MODE -> SharedMobilePdfBottomToolButton(
                            selected = state.selectedTool != PdfInkTool.NONE,
                            onClick = { onToolSelected(if (state.selectedTool == PdfInkTool.NONE) editModeOpenTool else PdfInkTool.NONE) },
                        ) { Icon(Icons.Default.Edit, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfBottomToolButton(onClick = onToggleTts) {
                            Icon(
                                if (ttsState != SharedMobileEpubLocalTtsState.IDLE) Icons.Default.Close else SharedReaderIcons.TextToSpeech,
                                contentDescription = sharedPdfReaderToolLabel(
                                    tool,
                                    isTtsPlayingOrLoading = ttsState != SharedMobileEpubLocalTtsState.IDLE,
                                ),
                            )
                        }
                        PdfReaderTool.DICTIONARY -> SharedMobilePdfBottomToolButton(onClick = onDictionary) { Icon(SharedReaderIcons.Dictionary, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.THEME -> SharedMobilePdfBottomToolButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.BRIGHTNESS -> SharedMobilePdfBottomToolButton(onClick = onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.LOCK_PANNING -> SharedMobilePdfBottomToolButton(onClick = onToggleScrollLock) { Icon(if (state.isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = sharedPdfReaderToolLabel(tool, isScrollLocked = state.isScrollLocked)) }
                        PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfBottomToolButton(onClick = onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        PdfReaderTool.AI_FEATURES -> if (aiAvailable) SharedMobilePdfBottomToolButton(onClick = onOpenAiHub) { Icon(Icons.Default.Ai, contentDescription = sharedPdfReaderToolTitle(tool)) }
                        else -> Unit
                    }
                }
            }
            // Android parity: annotation tools live in the floating
            // SharedPdfAndroidAnnotationDock overlay (see edit-mode chrome
            // below), not embedded in the standard bottom bar. The benchmark
            // hides standard bars entirely while annotating.
        }
    }
}

@Composable
private fun SharedMobilePdfBottomToolButton(
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .widthIn(min = 44.dp)
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun SharedMobilePdfReaderDrawer(
    book: BookItem,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float = 0f,
    tableOfContents: List<PdfTocEntry>,
    onGoToPage: (Int) -> Unit,
    onEditNote: (SharedPdfAnnotation) -> Unit,
    onDeleteHighlight: (SharedPdfAnnotation) -> Unit,
    onToggleBookmark: () -> Unit,
    onRenameBookmark: (Int, String) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    onGoToDisplayPage: (Int) -> Unit,
    pdfPassword: String?,
    tabsEnabled: Boolean,
    tabs: List<BookItem>,
    activeTabBookId: String?,
    isTopTabStripVisible: Boolean,
    onTopTabStripVisibilityChange: (Boolean) -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sections = remember(tabsEnabled, tabs.isNotEmpty()) {
        pdfDrawerSections(
            PdfDrawerCapabilities(
                tabsEnabled = tabsEnabled,
                hasOpenTabs = tabs.isNotEmpty(),
            ),
        )
    }
    val pagerState = rememberPagerState(initialPage = 0) { sections.size }
    LaunchedEffect(sections.size) {
        val maxPage = sections.size - 1
        if (pagerState.currentPage > maxPage) pagerState.scrollToPage(maxPage.coerceAtLeast(0))
    }
    ModalDrawerSheet(modifier = Modifier.width(348.dp)) {
        Column(Modifier.fillMaxSize()) {
            // Android parity (PdfNavigationDrawerContent): scrollable tabs with
            // single-line labels so long titles (Chapters/Bookmarks/Highlights)
            // scroll instead of wrapping.
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceAtMost(sections.lastIndex),
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                readerString(section.stringKey, section.fallbackLabel),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (sections.getOrNull(page)) {
                    PdfDrawerSection.CHAPTERS -> SharedMobilePdfChaptersDrawerPage(
                        entries = tableOfContents,
                        currentPageIndex = state.currentNearestPdfPageIndex ?: 0,
                        onGoToPage = onGoToPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfDrawerSection.PAGES -> SharedMobilePdfPagesDrawerPage(
                        book = book,
                        state = state,
                        activeTheme = activeTheme,
                        textureAlpha = textureAlpha,
                        pdfPassword = pdfPassword,
                        onGoToPage = onGoToDisplayPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfDrawerSection.BOOKMARKS -> SharedMobilePdfBookmarksDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfDrawerSection.HIGHLIGHTS -> SharedMobilePdfAnnotationsDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onEditNote = onEditNote,
                        onDeleteHighlight = onDeleteHighlight,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfDrawerSection.TABS -> SharedMobilePdfTabsDrawerPage(
                        tabs = tabs,
                        activeTabBookId = activeTabBookId,
                        isTopTabStripVisible = isTopTabStripVisible,
                        onTopTabStripVisibilityChange = onTopTabStripVisibilityChange,
                        onOpenTab = onOpenTab,
                        onCloseTab = onCloseTab,
                        onNewTab = onNewTab,
                        modifier = Modifier.fillMaxSize(),
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfTabsDrawerPage(
    tabs: List<BookItem>,
    activeTabBookId: String?,
    isTopTabStripVisible: Boolean,
    onTopTabStripVisibilityChange: (Boolean) -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text(readerString("tabs", "Tabs")) },
            supportingContent = { Text("${tabs.size}") },
            trailingContent = {
                TextButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(readerString("content_desc_new_tab", "New tab"))
                }
            },
        )
        ListItem(
            headlineContent = { Text(readerString("pdf_show_top_tab_strip", "Show top tab strip")) },
            trailingContent = {
                Switch(
                    checked = isTopTabStripVisible,
                    onCheckedChange = onTopTabStripVisibilityChange,
                )
            },
            modifier = Modifier.clickable {
                onTopTabStripVisibilityChange(!isTopTabStripVisible)
            },
        )
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(tabs, key = { "pdf_drawer_tab_${it.id}" }) { tab ->
                ListItem(
                    headlineContent = {
                        Text(
                            tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = tab.author?.let { author ->
                        { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = if (tab.id == activeTabBookId) {
                        { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Selected")) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { onCloseTab(tab) }) {
                            Icon(Icons.Default.Close, contentDescription = readerString("close_tab", "Close tab"))
                        }
                    },
                    modifier = Modifier.clickable { onOpenTab(tab) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SharedMobilePdfPagesDrawerPage(
    book: BookItem,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float = 0f,
    pdfPassword: String?,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPageCount = state.displayPageCount
    val pageRows = remember(displayPageCount) { sharedPdfThumbnailRows(displayPageCount) }
    val currentRowIndex = sharedPdfThumbnailRowFor(state.pageIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pageBackground = sharedMobilePdfPageBackground(activeTheme)
    val effectiveReverseColorMode = if (state.themeId == "reverse") {
        state.reverseColorMode
    } else {
        PdfReverseColorMode.RGB
    }
    val textureBitmap = sharedMobilePdfTextureBitmap(activeTheme)
    val textureBlendMode = sharedMobilePdfThumbnailBlendMode(activeTheme)
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        if (currentRowIndex in pageRows.indices) {
                            listState.animateScrollToItem(currentRowIndex)
                        }
                    }
                }
            ) {
                Text("Locate")
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 12.dp)
            ) {
                items(pageRows, key = { it.firstOrNull() ?: 0 }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { pageIdx ->
                            val isBlank = state.virtualPageLayout.getOrNull(pageIdx) is SharedPdfVirtualPage.BlankPage
                            val pdfPage = sharedPdfPdfPageIndexAt(state.virtualPageLayout, pageIdx)
                            val isCurrent = state.pageIndex == pageIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.707f)
                                    .background(
                                        pageBackground,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onGoToPage(pageIdx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isBlank && pdfPage != null) {
                                    val thumbnail = rememberSharedMobilePdfPageThumbnail(
                                        book = book,
                                        pageIndex = pdfPage,
                                        password = pdfPassword,
                                        reverseColorMode = effectiveReverseColorMode,
                                        preserveImageColors = state.themeId == "reverse" && state.preserveImageColors,
                                    )
                                    thumbnail.bitmap?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = readerString("desktop_pdf_page_content_desc", "PDF page %1\$d", pageIdx + 1),
                                            contentScale = ContentScale.Fit,
                                            colorFilter = sharedMobilePdfThumbnailColorFilter(
                                                activeTheme,
                                                effectiveReverseColorMode,
                                                thumbnail.rasterizedReverseColorMode
                                            ),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (textureBitmap != null && textureAlpha > 0f) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawRect(
                                                    brush = ShaderBrush(
                                                        ImageShader(
                                                            textureBitmap,
                                                            TileMode.Repeated,
                                                            TileMode.Repeated
                                                        )
                                                    ),
                                                    alpha = textureAlpha.coerceIn(0f, 1f),
                                                    blendMode = textureBlendMode
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "${pageIdx + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
            SharedMobileLazyListScrollbar(
                state = listState,
                itemCount = pageRows.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfChaptersDrawerPage(
    entries: List<PdfTocEntry>,
    currentPageIndex: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        SharedMobilePdfEmptyDrawerPage("No chapters found in this PDF.", modifier)
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val parentIndices: Set<Int> = remember(entries) {
        entries.indices.filter { index ->
            entries.getOrNull(index + 1)?.nestLevel?.let { it > entries[index].nestLevel } == true
        }.toSet()
    }
    var expandedIndices by remember(entries) { mutableStateOf(parentIndices) }
    var query by remember(entries) { mutableStateOf("") }
    val activeIndex = entries.indexOfLast { it.pageIndex <= currentPageIndex }
    val visibleEntries = if (query.isNotBlank()) {
        entries.mapIndexedNotNull { index, entry ->
            (index to entry).takeIf { entry.title.contains(query.trim(), ignoreCase = true) }
        }
    } else {
        buildList {
            val visibleAtLevel = BooleanArray(65)
            visibleAtLevel[0] = true
            entries.forEachIndexed { index, entry ->
                val level = entry.nestLevel.coerceIn(0, visibleAtLevel.lastIndex)
                if (visibleAtLevel[level]) add(index to entry)
                if (level < visibleAtLevel.lastIndex) {
                    visibleAtLevel[level + 1] = visibleAtLevel[level] && index in expandedIndices
                }
            }
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = readerString("content_desc_clear_query", "Clear search")) } }
            } else null,
            placeholder = { Text("Search chapters") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { expandedIndices = parentIndices }) { Text("Expand all") }
            TextButton(onClick = { expandedIndices = emptySet() }) { Text("Collapse all") }
            TextButton(onClick = {
                val target = visibleEntries.indexOfFirst { it.first == activeIndex }
                if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
            }) { Text("Locate") }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(visibleEntries, key = { (index, entry) ->
                    "${entry.pageIndex}_${entry.nestLevel}_${entry.title}_$index"
                }) { (index, entry) ->
                    val hasChildren = index in parentIndices
                    val expanded = index in expandedIndices
                    NavigationDrawerItem(
                        icon = if (hasChildren) {
                            {
                                IconButton(
                                    onClick = {
                                        expandedIndices = if (expanded) expandedIndices - index else expandedIndices + index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = readerString(
                                            if (expanded) "content_desc_collapse" else "content_desc_expand",
                                            if (expanded) "Collapse chapter" else "Expand chapter",
                                        )
                                    )
                                }
                            }
                        } else null,
                        label = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        selected = index == activeIndex,
                        onClick = { onGoToPage(entry.pageIndex) },
                        badge = { Text("${entry.pageIndex + 1}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(
                            start = (4 + entry.nestLevel.coerceAtMost(6) * 16).dp,
                            end = 4.dp,
                            top = 2.dp,
                            bottom = 2.dp
                        )
                    )
                }
            }
            SharedMobileLazyListScrollbar(
                state = listState,
                itemCount = visibleEntries.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedMobileLazyListScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 1) return
    val visibleCount = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if (visibleCount >= itemCount) return
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(1) }
    val thumbFraction = (visibleCount.toFloat() / itemCount).coerceIn(0.08f, 1f)
    val maxFirst = (itemCount - visibleCount).coerceAtLeast(1)
    val progress = (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Canvas(
        modifier = modifier
            .width(12.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(itemCount, visibleCount) {
                detectDragGestures { change, _ ->
                    val target = ((change.position.y / trackHeightPx) * maxFirst).toInt().coerceIn(0, maxFirst)
                    scope.launch { state.scrollToItem(target) }
                }
            }
    ) {
        val thumbHeight = size.height * thumbFraction
        val thumbTop = (size.height - thumbHeight) * progress
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(size.width - 4.dp.toPx(), thumbTop),
            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
    }
}

@Composable
private fun SharedMobilePdfEmptyDrawerPage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SharedMobilePdfBookmarksDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    onRenameBookmark: (Int, String) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.bookmarks.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var menuBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    var renameBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    var deleteBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
    ) {
        items(state.bookmarks.sortedBy { it.pageIndex }, key = { "bookmark_${it.pageIndex}_${it.createdAt}" }) { bookmark ->
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                label = { Text(bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" }) },
                selected = bookmark.pageIndex == (state.currentNearestPdfPageIndex ?: 0),
                onClick = { onGoToPage(bookmark.pageIndex) },
                badge = {
                    Box {
                        IconButton(onClick = { menuBookmark = bookmark }) { Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options_bookmark", "Bookmark options"), modifier = Modifier.size(18.dp)) }
                        DropdownMenu(expanded = menuBookmark?.pageIndex == bookmark.pageIndex, onDismissRequest = { menuBookmark = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { renameBookmark = bookmark; menuBookmark = null })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { deleteBookmark = bookmark; menuBookmark = null })
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
    renameBookmark?.let { bookmark ->
        var label by remember(bookmark.pageIndex, bookmark.createdAt) { mutableStateOf(bookmark.label) }
        AlertDialog(
            onDismissRequest = { renameBookmark = null },
            title = { Text("Rename Bookmark") },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRenameBookmark(bookmark.pageIndex, label); renameBookmark = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameBookmark = null }) { Text("Cancel") } }
        )
    }
    deleteBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteBookmark = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("This bookmark will be removed from the document.") },
            confirmButton = { TextButton(onClick = { onDeleteBookmark(bookmark.pageIndex); deleteBookmark = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteBookmark = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedMobilePdfAnnotationsDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    onEditNote: (SharedPdfAnnotation) -> Unit,
    onDeleteHighlight: (SharedPdfAnnotation) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlights = state.annotations.filter { it.kind == PdfAnnotationKind.HIGHLIGHT }
    if (highlights.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No highlights yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var notesOnly by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
    val filtered = highlights.filter { !notesOnly || !it.note.isNullOrBlank() }
        .sortedWith(compareBy({ it.pageIndex }, { it.createdAt }, { it.id }))
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !notesOnly, onClick = { notesOnly = false }, label = { Text("All") })
            FilterChip(selected = notesOnly, onClick = { notesOnly = true }, label = { Text("With notes") })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.id }) { annotation ->
                var menuExpanded by remember(annotation.id) { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        Text(annotation.text.ifBlank { "Highlighted section" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(Color(annotation.colorArgb), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("Page ${annotation.pageIndex + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (annotation.comments.any { it.contents.isNotBlank() }) {
                                    Spacer(Modifier.width(8.dp)); Text("${annotation.comments.count { it.contents.isNotBlank() }} comments", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), modifier = Modifier.fillMaxWidth()) {
                                    Text(note, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Highlight options") }
                            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (annotation.note.isNullOrBlank()) "Add note" else "Edit note") },
                                    onClick = { menuExpanded = false; onEditNote(annotation) }
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; deleteTarget = annotation })
                            }
                        }
                    },
                    modifier = Modifier.clickable { onGoToPage(annotation.pageIndex) }
                )
                HorizontalDivider()
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete highlight?") },
            text = { Text("This highlight, its note, and its comments will be removed.") },
            confirmButton = { TextButton(onClick = { onDeleteHighlight(target); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedMobilePdfSearchResultsPanel(
    query: String,
    results: List<SharedPdfSearchResult>,
    activeResultIndex: Int,
    isSearching: Boolean,
    onResultClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            query.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Enter a search term", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No results for “${query.trim()}”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(results.size) { index ->
                    val result = results[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == activeResultIndex) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onResultClick(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${result.pageIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 28.dp)
                        )
                        Text(
                            text = result.preview.ifBlank { "Match on page ${result.pageIndex + 1}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfSearchNavigationPill(
    activeIndex: Int,
    resultCount: Int,
    highlightMode: SearchHighlightMode,
    onToggleHighlightMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleHighlightMode) {
                Icon(
                    if (highlightMode == SearchHighlightMode.ALL) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = readerString("content_desc_toggle_search_highlights", "Toggle search highlights"),
                    tint = if (highlightMode == SearchHighlightMode.ALL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
            IconButton(onClick = onPrevious, enabled = activeIndex > 0) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("tooltip_prev_result", "Previous result"))
            }
            TextButton(onClick = onShowResults) {
                Text(
                    if (activeIndex in 0 until resultCount) "${activeIndex + 1} of $resultCount"
                    else "$resultCount results"
                )
            }
            IconButton(onClick = onNext, enabled = activeIndex < resultCount - 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("tooltip_next_result", "Next result"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfThemePanel(
    settings: ReaderSettings,
    customThemes: List<ReaderTheme>,
    onCustomThemesChange: (List<ReaderTheme>) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item {
                SharedReaderThemeControls(
                    settings = settings,
                    builtInThemes = BuiltInPdfReaderThemes,
                    customThemes = customThemes,
                    onCustomThemesChange = onCustomThemesChange,
                    onSettingsChange = onSettingsChange,
                    showPdfColorOptions = true,
                    onPdfReverseColorModeChange = { mode ->
                        onSettingsChange(settings.copy(pdfReverseColorMode = mode))
                    },
                    onPdfPreserveImageColorsChange = { enabled ->
                        onSettingsChange(settings.copy(pdfPreserveImageColors = enabled))
                    },
                )
            }
        }
    }
}
