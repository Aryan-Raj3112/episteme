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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.PdfReaderTool
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
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfTtsSessionPlanner
import com.aryan.reader.shared.pdf.shouldStopPdfTtsForManualPageTurn
import com.aryan.reader.shared.pdf.shouldStopPdfTtsForNavigation
import com.aryan.reader.shared.pdf.PdfAutoScrollProfile
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.initialSharedPdfReaderState
import com.aryan.reader.shared.pdf.PdfNavigationReason
import com.aryan.reader.shared.pdf.PdfChromeMotionDurationMillis
import com.aryan.reader.shared.pdf.animatesPagination
import com.aryan.reader.shared.pdf.PdfZoomCamera
import com.aryan.reader.shared.pdf.PdfZoomPoint
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
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
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfVirtualPage
import com.aryan.reader.shared.pdf.buildSharedPdfVirtualPageLayout
import com.aryan.reader.shared.pdf.sharedPdfPdfPageIndexAt
import com.aryan.reader.shared.pdf.sharedPdfDisplayIndexFor
import com.aryan.reader.shared.pdf.sharedPdfNearestPdfPageIndex
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfPageRangeLabel
import com.aryan.reader.shared.pdf.SharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.pdf.sharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.SystemUiMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext


enum class SharedMobilePdfNativeAction {
    DICTIONARY_SETTINGS,
    SHARE,
    SAVE_COPY,
    PRINT,
    TEXT_VIEW,
}

data class SharedMobilePdfReflowUiState(
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val hasReflowBook: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobilePdfReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onNativePdfAction: (BookItem, SharedMobilePdfNativeAction, password: String?) -> Unit,
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
    readerBrightness: Float? = null,
    readerCustomBrightness: Float = com.aryan.reader.shared.DefaultReaderCustomBrightness,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    onTtsError: ((String) -> Unit)? = null,
    initialReaderState: SharedPdfReaderState? = null,
    readerDefaultSettings: ReaderSettings = ReaderSettings(themeId = "no_theme"),
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    initialKeepScreenOn: Boolean = false,
    onKeepScreenOnPreferenceChange: (Boolean) -> Unit = {},
    initialStylusOnlyMode: Boolean = false,
    onStylusOnlyModePreferenceChange: (Boolean) -> Unit = {},
    initialPageSliderVisible: Boolean = false,
    onPageSliderVisibilityPreferenceChange: (Boolean) -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    val pdfCardTitle = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current)
    val initialPage = book.lastPageIndex?.coerceAtLeast(0) ?: 0
    var readerState by remember(book.id, initialReaderState) {
        mutableStateOf(
            initialSharedPdfReaderState(
                persistedState = initialReaderState,
                defaults = readerDefaultSettings,
                initialPageIndex = initialPage,
            )
        )
    }
    // Android intentionally starts every PDF session distraction-free.
    var showChrome by remember(book.id) { mutableStateOf(false) }
    var showReaderOptions by remember(book.id) { mutableStateOf(false) }
    var showThemePanel by remember(book.id) { mutableStateOf(false) }
    var showPageSlider by remember(book.id) { mutableStateOf(initialPageSliderVisible) }
    var showFileInformation by remember(book.id) { mutableStateOf(false) }
    var showBrightnessSheet by remember(book.id) { mutableStateOf(false) }
    var showScreenOrientationSheet by remember(book.id) { mutableStateOf(false) }
    var showToolbarCustomization by remember(book.id) { mutableStateOf(false) }
    var showTtsSettingsSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var showNewPdfTabSheet by remember(book.id) { mutableStateOf(false) }
    var pendingExternalLink by remember(book.id) { mutableStateOf<String?>(null) }
    var pdfPassword by remember(book.id) { mutableStateOf<String?>(null) }
    var pdfPasswordDraft by remember(book.id) { mutableStateOf("") }
    var showPasswordProtectedPrintWarning by remember(book.id) { mutableStateOf(false) }
    var showVerticalPageGap by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfVerticalPageGapVisible)
    }
    var showPageNumberOverlay by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfPageNumberOverlayVisible)
    }
    var systemUiMode by remember(book.id) {
        mutableStateOf(readerDefaultSettings.systemUiMode.toSharedMobilePdfSystemUiMode())
    }
    var rightToLeftPagination by remember(book.id) {
        mutableStateOf(readerDefaultSettings.rightToLeftPagination)
    }
    var useTwoPageSpread by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE)
    }
    var firstPageStandaloneInSpread by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfFirstPageStandaloneInSpread)
    }
    var globalTextureTransparency by remember(book.id) {
        mutableStateOf(1f - readerDefaultSettings.textureAlpha.coerceIn(0f, 1f))
    }
    var keepScreenOn by remember(book.id) { mutableStateOf(initialKeepScreenOn) }
    var isStylusOnlyMode by remember(book.id) { mutableStateOf(initialStylusOnlyMode) }
    var autoScrollModeActive by remember(book.id) { mutableStateOf(false) }
    var autoScrollPlaying by remember(book.id) { mutableStateOf(false) }
    var autoScrollTemporarilyPaused by remember(book.id) { mutableStateOf(false) }
    var autoScrollIsLocal by remember(book.id) { mutableStateOf(book.pdfAutoScrollIsLocal) }
    var autoScrollProfile by remember(book.id) {
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
    var autoScrollMusicianMode by remember(book.id) { mutableStateOf(initialPdfAutoScrollMusicianMode) }
    var autoScrollUseSlider by remember(book.id) { mutableStateOf(initialPdfAutoScrollUseSlider) }
    var autoScrollCollapsed by remember(book.id) { mutableStateOf(false) }
    var autoScrollInteractionToken by remember(book.id) { mutableStateOf(0) }
    var autoScrollPauseDurationMillis by remember(book.id) { mutableStateOf(300L) }
    var tapToTurnPages by remember(book.id) { mutableStateOf(true) }
    var pdfSliderScrubbingPage by remember(book.id) { mutableStateOf<Int?>(null) }
    var showAllTextHighlights by remember(book.id) { mutableStateOf(false) }
    var isAllTextHighlightLoading by remember(book.id) { mutableStateOf(false) }
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
    val visiblePdfTools = sanitizedPdfToolbarPreferences.toolOrder.filter(sanitizedPdfToolbarPreferences::isVisible)
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

    LaunchedEffect(pdfAutoScrollGlobalProfile, autoScrollIsLocal) {
        if (!autoScrollIsLocal) autoScrollProfile = pdfAutoScrollGlobalProfile.sanitized()
    }

    DisposableEffect(readerScreenOrientationMode, onApplyReaderScreenOrientation) {
        onApplyReaderScreenOrientation(readerScreenOrientationMode)
        onDispose { onApplyReaderScreenOrientation(ReaderScreenOrientationMode.FOLLOW_SYSTEM) }
    }
    var pdfZoomCamera by remember(book.id, initialReaderState) {
        mutableStateOf(
            initialReaderState?.takeIf { it.isScrollLocked }?.let {
                PdfZoomCamera(it.lockedZoomScale, PdfZoomPoint(it.lockedZoomOffsetX, it.lockedZoomOffsetY))
            } ?: PdfZoomCamera()
        )
    }
    var navigationRequestPage by remember(book.id) { mutableStateOf(readerState.pageIndex) }
    var navigationRequestToken by remember(book.id) { mutableStateOf(0) }
    var navigationCenterFraction by remember(book.id) { mutableStateOf(0.5f) }
    var navigationReason by remember(book.id) { mutableStateOf(PdfNavigationReason.INITIAL) }
    var jumpHistory by remember(book.id) { mutableStateOf(SharedPdfJumpHistory()) }
    val pdfTts = rememberSharedMobileEpubLocalTts()
    LaunchedEffect(pdfTts.errorMessage) {
        pdfTts.errorMessage?.let { message -> onTtsError?.invoke(message) }
    }
    var ttsPageIndex by remember(book.id) { mutableStateOf(readerState.pageIndex) }
    var pendingTtsStart by remember(book.id) { mutableStateOf<Int?>(null) }
    var pendingTtsStartAtLastChunk by remember(book.id) { mutableStateOf(false) }
    var pendingTtsPlayWhenReady by remember(book.id) { mutableStateOf(true) }
    var ttsHighlightBounds by remember(book.id) { mutableStateOf<List<PdfPageBounds>>(emptyList()) }
    var lastTtsCompletionCount by remember(book.id) { mutableStateOf(pdfTts.completionCount) }
    val ttsTextSession = rememberPdfTextPageSession(book, ttsPageIndex, pdfPassword)
    var searchResults by remember(book.id) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var tableOfContents by remember(book.id) { mutableStateOf<List<PdfTocEntry>>(emptyList()) }
    var noteAnnotationId by remember(book.id) { mutableStateOf<String?>(null) }
    var isSearchInProgress by remember(book.id) { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pdfReaderFocusRequester = remember(book.id) { FocusRequester() }
    val pdfVerticalScrollController = remember(book.id) { SharedMobilePdfVerticalScrollController() }
    LaunchedEffect(book.id) {
        runCatching { pdfReaderFocusRequester.requestFocus() }
    }
    var richTextDocumentJson by remember(book.id) { mutableStateOf(initialReaderState?.richTextDocumentJson.orEmpty()) }
    val richTextController = remember(book.id, initialReaderState) {
        SharedPdfRichTextController(
            scope = scope,
            initialDocument = SharedPdfRichTextSerializer.decode(initialReaderState?.richTextDocumentJson.orEmpty()),
            onDocumentChange = { document ->
                richTextDocumentJson = SharedPdfRichTextSerializer.encode(document)
            }
        )
    }
    val closeReader = {
        scope.saveAndCloseSharedMobilePdfReader(richTextController, readerState, { richTextDocumentJson }, onReaderStateChange, onBack)
    }
    // Document metadata must not follow the visible page. A newly requested page starts with
    // SharedMobilePdfPageRender's loading value (pageCount = 1); using that transient value here
    // used to collapse the list/pager to page zero every time the user changed pages.
    val documentRender = rememberSharedMobilePdfPageRender(book, 0, password = pdfPassword)
    val pageCount = if (documentRender.bitmap != null || documentRender.errorMessage != null) {
        documentRender.pageCount.coerceAtLeast(1)
    } else {
        readerState.pageCount.coerceAtLeast(1)
    }
    val virtualLayout = remember(pageCount, readerState.blankPageInsertions) {
        buildSharedPdfVirtualPageLayout(pageCount, readerState.blankPageInsertions)
    }
    val displayPageCount = virtualLayout.size
    val currentPdfIndex = readerState.currentNearestPdfPageIndex ?: 0
    val currentPageRender = rememberSharedMobilePdfPageRender(book, currentPdfIndex, password = pdfPassword)
    val isCurrentPageBlank = (virtualLayout.getOrNull(readerState.pageIndex) as? SharedPdfVirtualPage.BlankPage) != null
    val prefetchedTtsPageIndex = (ttsPageIndex + 1).coerceAtMost(pageCount - 1)
    val prefetchedTtsTextSession = rememberPdfTextPageSession(book, prefetchedTtsPageIndex, pdfPassword)
    val activeTheme = remember(readerState.themeId, customReaderThemes) {
        resolveReaderTheme(readerState.themeId, BuiltInPdfReaderThemes, customReaderThemes)
            ?: BuiltInPdfReaderThemes.first()
    }
    val systemBarColor = MaterialTheme.colorScheme.surface
    val hideSystemUi = when (systemUiMode) {
        SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> false
        SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> !showChrome
        SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> true
    }
    val edgeToEdgeSystemUi = systemUiMode != SharedMobilePdfSystemUiMode.ALWAYS_SHOW
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val systemNavigationInset = with(density) {
        WindowInsets.safeDrawing.getBottom(density).toDp()
    }
    val effectiveBottomSystemInset = when (systemUiMode) {
        SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> systemNavigationInset
        SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> if (showChrome) systemNavigationInset else 0.dp
        SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> 0.dp
    }
    val pdfBottomChromePadding = 56.dp + effectiveBottomSystemInset
    val isJumpHistoryVisible = showChrome && !readerState.isSearchActive && jumpHistory.hasJumpTargets
    val isPdfTtsPlayingOrLoading =
        pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING || pendingTtsStart != null
    val pdfSliderBottomPadding = pdfBottomChromePadding + if (isJumpHistoryVisible) 40.dp else 0.dp
    LaunchedEffect(hideSystemUi, systemBarColor, edgeToEdgeSystemUi) {
        onSystemUiAppearanceChange(
            hideSystemUi,
            systemBarColor.luminance() < 0.5f,
            systemBarColor.toArgb().toLong(),
            edgeToEdgeSystemUi
        )
    }
    DisposableEffect(Unit) {
        onDispose { onSystemUiAppearanceChange(false, false, 0xFFFFFFFFL, false) }
    }
    var canvasSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    val activeStroke = remember(book.id, readerState.pageIndex) { mutableStateListOf<PdfPagePoint>() }
    var textStyle by remember(book.id) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var textDraft by remember(book.id) { mutableStateOf<SharedPdfTextDraft?>(null) }
    val isRichTextEditingEnabled = readerState.selectedTool == PdfInkTool.TEXT && textDraft == null

    fun dispatch(action: SharedPdfReaderAction) {
        readerState = readerState.reduce(action)
    }
    fun stopPdfTtsSession() {
        pdfTts.stop()
        pendingTtsStart = null
        pendingTtsStartAtLastChunk = false
        ttsHighlightBounds = emptyList()
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
        if (recordHistory) jumpHistory = jumpHistory.record(readerState.pageIndex, target, displayPageCount)
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
        textDraft = SharedPdfTextDraft(
            id = annotation.id,
            pageIndex = annotation.pageIndex,
            bounds = annotation.bounds ?: PdfPageBounds(left = 0.3f, top = 0.45f, right = 0.7f, bottom = 0.55f),
            text = annotation.text,
            style = annotation.sharedPdfTextStyle(),
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

    fun activeToolConfig(tool: PdfInkTool) = SharedPdfAnnotationDefaults.configFor(tool)

    fun setTool(tool: PdfInkTool) {
        if (tool != PdfInkTool.TEXT && readerState.selectedTool == PdfInkTool.TEXT && textDraft != null) {
            dismissTextDraft()
        }
        dispatch(SharedPdfReaderAction.ToolSelected(tool))
        if (tool != PdfInkTool.NONE) {
            activeToolConfig(tool).let { config ->
                dispatch(SharedPdfReaderAction.ColorSelected(config.colorArgb.takeIf { it != 0 } ?: readerState.selectedColorArgb))
                dispatch(SharedPdfReaderAction.StrokeWidthChanged(config.strokeWidth))
            }
        }
    }

    fun finishInkStroke(pageIndex: Int, eraserOverride: Boolean = false) {
        val effectiveTool = if (eraserOverride) PdfInkTool.ERASER else readerState.selectedTool
        if (activeStroke.size < 2 || effectiveTool == PdfInkTool.NONE || effectiveTool == PdfInkTool.TEXT) {
            activeStroke.clear()
            return
        }
        val kind = if (effectiveTool == PdfInkTool.HIGHLIGHTER || effectiveTool == PdfInkTool.HIGHLIGHTER_ROUND) {
            PdfAnnotationKind.HIGHLIGHT
        } else {
            PdfAnnotationKind.INK
        }
        val annotation = if (kind == PdfAnnotationKind.HIGHLIGHT) {
            val xs = activeStroke.map { it.x }
            val ys = activeStroke.map { it.y }
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = effectiveTool,
                boundsList = listOf(
                    PdfPageBounds(
                        left = xs.minOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        top = (ys.minOrNull()?.minus(0.015f))?.coerceIn(0f, 1f) ?: 0f,
                        right = xs.maxOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        bottom = (ys.maxOrNull()?.plus(0.015f))?.coerceIn(0f, 1f) ?: 0f
                    )
                ),
                colorArgb = readerState.selectedColorArgb,
                highlightStyle = HighlightStyle.BACKGROUND,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        } else {
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.INK,
                tool = effectiveTool,
                points = activeStroke.toList(),
                colorArgb = readerState.selectedColorArgb,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        }
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        activeStroke.clear()
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

    LaunchedEffect(pageCount) {
        if (readerState.pageCount != pageCount) {
            readerState = readerState.copy(pageCount = pageCount).coerced()
        }
    }

    // Mirrors Android's auto page management for the flowing rich text document
    // (PdfViewerScreen lines ~2208-2360): text overflowing the last page appends blank pages,
    // trailing auto-added blank pages with no content are pruned. Manual blank pages are kept.
    val highestRequiredTextPageIndex by remember(richTextController.pageLayouts) {
        derivedStateOf { richTextController.pageLayouts.maxOfOrNull { it.pageIndex } ?: -1 }
    }
    fun hasRichTextOnPage(pageIndex: Int): Boolean {
        return richTextController.pageLayouts.any {
            it.pageIndex == pageIndex && it.visibleText.isNotBlank()
        }
    }
    LaunchedEffect(highestRequiredTextPageIndex, displayPageCount, richTextController.pageLayouts) {
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

    LaunchedEffect(readerState, richTextDocumentJson) {
        onReaderStateChange(readerState.copy(richTextDocumentJson = richTextDocumentJson))
    }

    LaunchedEffect(autoScrollInteractionToken) {
        if (autoScrollInteractionToken == 0 || !autoScrollPlaying) return@LaunchedEffect
        autoScrollTemporarilyPaused = true
        delay(autoScrollPauseDurationMillis)
        autoScrollTemporarilyPaused = false
    }

    LaunchedEffect(readerState.displayMode) {
        if (readerState.displayMode != PdfDisplayMode.VERTICAL_SCROLL) {
            autoScrollModeActive = false
            autoScrollPlaying = false
            autoScrollTemporarilyPaused = false
        }
    }

    LaunchedEffect(readerState.searchQuery) {
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

    LaunchedEffect(book.path, pdfPassword, documentRender.openError) {
        tableOfContents = if (documentRender.openError == null) {
            withContext(Dispatchers.Default) { loadSharedMobilePdfOutline(book, pdfPassword) }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(ttsTextSession, pendingTtsStart, ttsPageIndex) {
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

    LaunchedEffect(pdfTts.progress.currentChunk, ttsTextSession, ttsPageIndex) {
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

    LaunchedEffect(pdfTts.completionCount) {
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

    LaunchedEffect(keepScreenOn) {
        onKeepScreenOnChange(keepScreenOn)
    }

    LaunchedEffect(isStylusOnlyMode) {
        onStylusOnlyModePreferenceChange(isStylusOnlyMode)
    }

    DisposableEffect(onKeepScreenOnChange) {
        onDispose { onKeepScreenOnChange(false) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SharedMobilePdfReaderDrawer(
                book = book,
                state = readerState,
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
                    visible = showChrome,
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
                            onTheme = { showThemePanel = true },
                            onVisualOptions = { showReaderOptions = !showReaderOptions },
                            tapToTurnPages = tapToTurnPages,
                            onToggleTapToTurnPages = { tapToTurnPages = !tapToTurnPages },
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
                            onEditMode = {
                                setTool(if (readerState.selectedTool == PdfInkTool.NONE) PdfInkTool.PEN else PdfInkTool.NONE)
                            },
                            onShowSlider = {
                                showPageSlider = !showPageSlider
                                onPageSliderVisibilityPreferenceChange(showPageSlider)
                            },
                            onToggleTts = {
                                when (pdfTts.state) {
                                    SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                    SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                    SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                                }
                            },
                            onVoiceSettings = { showTtsSettingsSheet = true },
                            onWordReplacements = { showTtsReplacementsSheet = true },
                            onNativeAction = { action ->
                                if (action == SharedMobilePdfNativeAction.PRINT && pdfPassword != null) {
                                    showPasswordProtectedPrintWarning = true
                                } else {
                                    onNativePdfAction(book, action, pdfPassword)
                                }
                            },
                            isCurrentPageBlank = isCurrentPageBlank,
                            onInsertBlankPage = ::insertBlankPageAtCurrentPosition,
                            onDeleteBlankPage = ::deleteBlankPageAtCurrentPosition,
                            pdfReflowUiState = pdfReflowUiState,
                            isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                            onFileInformation = { showFileInformation = true },
                            onBrightness = { showBrightnessSheet = true },
                            onScreenOrientation = { showScreenOrientationSheet = true },
                            topTools = pdfTopTools,
                            toolbarPreferences = sanitizedPdfToolbarPreferences,
                            onCustomizeToolbar = { showToolbarCustomization = true },
                            applySystemBarInsets = systemUiMode == SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS
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
                    visible = showChrome && !readerState.isSearchActive,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis))
                ) {
                    SharedMobilePdfReaderBottomBar(
                        state = readerState,
                        tools = pdfBottomTools,
                        onShowSlider = { showPageSlider = !showPageSlider },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onToolSelected = ::setTool,
                        onColorSelected = { dispatch(SharedPdfReaderAction.ColorSelected(it)) },
                        onStrokeWidthChange = { dispatch(SharedPdfReaderAction.StrokeWidthChanged(it)) },
                        onUndo = { dispatch(SharedPdfReaderAction.UndoLastAnnotationOnPage(readerState.pageIndex)) },
                        onRedo = { dispatch(SharedPdfReaderAction.RedoAnnotationEdit) },
                        onClearPage = { dispatch(SharedPdfReaderAction.ClearPageAnnotations(readerState.pageIndex)) },
                        isStylusOnlyMode = isStylusOnlyMode,
                        onToggleStylusOnlyMode = { isStylusOnlyMode = !isStylusOnlyMode },
                        ttsState = pdfTts.state,
                        isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                        onToggleTts = {
                            when (pdfTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                            }
                        },
                        onTheme = { showThemePanel = true },
                        onBrightness = { showBrightnessSheet = true },
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
                        onScreenOrientation = { showScreenOrientationSheet = true },
                        onDictionary = { onNativePdfAction(book, SharedMobilePdfNativeAction.DICTIONARY_SETTINGS, pdfPassword) },
                        showAllTextHighlights = showAllTextHighlights,
                        isAllTextHighlightLoading = isAllTextHighlightLoading,
                        onToggleHighlights = ::toggleAllTextHighlights,
                        applySystemBarInsets = systemUiMode == SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sharedMobilePdfViewerBackground(activeTheme, readerState.displayMode))
                    .focusRequester(pdfReaderFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
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
                        state = readerState,
                        activeTheme = activeTheme,
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
                        onExternalLink = { url -> pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobilePdfPaginatedPages(
                        book = book,
                        pdfPassword = pdfPassword,
                        state = readerState,
                        activeTheme = activeTheme,
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
                        isStylusOnlyMode = isStylusOnlyMode,
                        tapToTurnPages = tapToTurnPages,
                        onExternalLink = { url -> pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AnimatedVisibility(
                    visible = showChrome && showPageSlider,
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
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = pdfSliderBottomPadding)
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
                            jumpHistory.backPage?.let { target ->
                                jumpHistory = jumpHistory.stepBack()
                                navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                            }
                        },
                        onForward = {
                            jumpHistory.forwardPage?.let { target ->
                                jumpHistory = jumpHistory.stepForward()
                                navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                            }
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
                        chunkCount = pdfTts.progress.chunks.size,
                        onPauseResume = {
                            if (pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING) pdfTts.pause() else pdfTts.resume()
                        },
                        onPreviousPage = {
                            if (pdfTts.progress.currentChunkIndex > 0) {
                                pdfTts.skipPrevious()
                            } else if (ttsPageIndex > 0) {
                                requestTts(
                                    pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex - 1),
                                    startAtLastChunk = true,
                                    playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                )
                            }
                        },
                        onNextPage = {
                            if (pdfTts.progress.currentChunkIndex < pdfTts.progress.chunks.lastIndex) {
                                pdfTts.skipNext()
                            } else if (ttsPageIndex < pageCount - 1) {
                                requestTts(
                                    pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex + 1),
                                    playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                )
                            }
                        },
                        onLocate = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex), recordHistory = false, centerFraction = ttsHighlightBounds.centerYFraction(), reason = PdfNavigationReason.TTS) },
                        onStop = {
                            pdfTts.stop()
                            pendingTtsStart = null
                            pendingTtsStartAtLastChunk = false
                            ttsHighlightBounds = emptyList()
                        },
                        modifier = Modifier.padding(bottom = ttsBottomPadding)
                    )
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
                        },
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = ttsBottomPadding + if (pdfTts.isSessionActive || pendingTtsStart != null) 76.dp else 0.dp,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = readerState.isSearchActive && readerState.showSearchResultsPanel && readerState.searchQuery.isNotBlank()
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
                AnimatedVisibility(
                    visible = readerState.selectedTool == PdfInkTool.TEXT && !readerState.isSearchActive,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfTextDock(
                        style = textStyle,
                        onStyleChange = { newStyle ->
                            textStyle = newStyle
                            textDraft?.let { draft ->
                                updateTextDraft(draft.withStyle(newStyle, canvasSize))
                            }
                        },
                        onInsertTextBox = ::insertTextBox,
                        onClose = { setTool(PdfInkTool.NONE) },
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = if (showChrome) 96.dp + effectiveBottomSystemInset else 16.dp + effectiveBottomSystemInset
                        )
                    )
                }
                if (pdfReflowUiState.isGenerating) {
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
                                clipboard.setText(AnnotatedString(url))
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
                ),
                customThemes = customReaderThemes,
                onCustomThemesChange = onCustomReaderThemesChange,
                onSettingsChange = { settings ->
                    globalTextureTransparency = 1f - settings.textureAlpha.coerceIn(0f, 1f)
                    if (settings.themeId != readerState.themeId) {
                        settings.themeId?.let { dispatch(SharedPdfReaderAction.ThemeChanged(it)) }
                        showThemePanel = false
                    }
                    onReaderDefaultSettingsChange(settings)
                },
                onDismiss = { showThemePanel = false }
            )
        }
        if (showReaderOptions) {
            SharedMobilePdfVisualOptionsSheet(
                displayMode = readerState.displayMode,
                systemUiMode = systemUiMode,
                useTwoPageSpread = useTwoPageSpread,
                firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                rightToLeftPagination = rightToLeftPagination,
                showVerticalPageGap = showVerticalPageGap,
                showPageNumberOverlay = showPageNumberOverlay,
                onSystemUiModeChange = { mode ->
                    systemUiMode = mode
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(systemUiMode = mode.toReaderSystemUiMode())
                    )
                },
                onTwoPageSpreadChange = {
                    useTwoPageSpread = it
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(
                            pageSpreadMode = if (it) {
                                ReaderPageSpreadMode.TWO_PAGE
                            } else {
                                ReaderPageSpreadMode.SINGLE
                            }
                        )
                    )
                },
                onFirstPageStandaloneChange = {
                    firstPageStandaloneInSpread = it
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(pdfFirstPageStandaloneInSpread = it)
                    )
                },
                onRightToLeftPaginationChange = {
                    rightToLeftPagination = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(rightToLeftPagination = it))
                },
                onShowVerticalPageGapChange = {
                    showVerticalPageGap = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfVerticalPageGapVisible = it))
                },
                onShowPageNumberOverlayChange = {
                    showPageNumberOverlay = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfPageNumberOverlayVisible = it))
                },
                onDismiss = { showReaderOptions = false }
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
    if (documentRender.openError == SharedMobilePdfOpenError.PASSWORD_REQUIRED) {
        AlertDialog(
            onDismissRequest = closeReader,
            title = { Text("Password protected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This PDF is password protected. Enter the password to open it.")
                    OutlinedTextField(
                        value = pdfPasswordDraft,
                        onValueChange = { pdfPasswordDraft = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pdfPassword != null,
                        supportingText = if (pdfPassword != null) {
                            { Text("Incorrect password") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { pdfPassword = pdfPasswordDraft },
                    enabled = pdfPasswordDraft.isNotBlank(),
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = closeReader) { Text("Cancel") }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )
    }
    if (showPasswordProtectedPrintWarning) {
        AlertDialog(
            onDismissRequest = { showPasswordProtectedPrintWarning = false },
            title = { Text("Printing unavailable") },
            text = { Text("Password-protected PDFs cannot be printed.") },
            confirmButton = {
                TextButton(onClick = { showPasswordProtectedPrintWarning = false }) {
                    Text("OK")
                }
            },
        )
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
    isCurrentPageBlank: Boolean = false,
    onInsertBlankPage: () -> Unit = {},
    onDeleteBlankPage: () -> Unit = {},
    pdfReflowUiState: SharedMobilePdfReflowUiState,
    onFileInformation: () -> Unit,
    onBrightness: () -> Unit,
    onScreenOrientation: () -> Unit,
    isTtsPlayingOrLoading: Boolean,
    topTools: List<PdfReaderTool>,
    toolbarPreferences: PdfToolbarPreferences,
    onCustomizeToolbar: () -> Unit,
    applySystemBarInsets: Boolean
) {
    val searchFocusRequester = remember { FocusRequester() }
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
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search PDF") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
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
                        .padding(start = 12.dp)
                        .weight(1f)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    topTools.forEach { tool ->
                        when (tool) {
                            PdfReaderTool.DICTIONARY -> SharedMobilePdfTopToolButton("Dictionary", { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) }) { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }
                            PdfReaderTool.THEME -> SharedMobilePdfTopToolButton("Theme", onTheme) { Icon(Icons.Default.Palette, contentDescription = null) }
                            PdfReaderTool.BRIGHTNESS -> SharedMobilePdfTopToolButton("Brightness", onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = null) }
                            PdfReaderTool.LOCK_PANNING -> SharedMobilePdfTopToolButton(if (isScrollLocked) "Unlock" else "Lock", onToggleScrollLock) { Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null) }
                            PdfReaderTool.SLIDER -> SharedMobilePdfTopToolButton("Navigation Slider", onShowSlider, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(SharedReaderIcons.Slider, contentDescription = null) }
                            PdfReaderTool.TOC -> SharedMobilePdfTopToolButton("Sidebar", onOpenDrawer, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Menu, contentDescription = null) }
                            PdfReaderTool.SEARCH -> SharedMobilePdfTopToolButton("Search", onSearch, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Search, contentDescription = null) }
                            PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfTopToolButton(
                                "Highlight Selectable Text",
                                onClick = onToggleHighlights,
                                isActive = showAllTextHighlights,
                                isLoading = isAllTextHighlightLoading,
                            ) { Icon(SharedReaderIcons.HighlightText, contentDescription = "Highlight all text") }
                            PdfReaderTool.EDIT_MODE -> SharedMobilePdfTopToolButton("Edit Mode", onEditMode) { Icon(Icons.Default.Edit, contentDescription = null) }
                            PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfTopToolButton(if (isTtsPlayingOrLoading) "Stop Reading" else "Read Aloud", onToggleTts, isActive = isTtsPlayingOrLoading) { Icon(if (isTtsPlayingOrLoading) Icons.Default.Close else SharedReaderIcons.TextToSpeech, contentDescription = null) }
                            PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfTopToolButton("Screen Orientation", onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }
                            else -> Unit
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = {
                    showHiddenToolsExpanded = false
                    showReadingModeExpanded = false
                    showTtsSettingsExpanded = false
                    showFileActionsExpanded = false
                    showMoreMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "PDF options")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                SharedMobilePdfOverflowItem(
                    "Customize Toolbar",
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
                    "Hidden tools",
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
                            PdfReaderTool.DICTIONARY -> SharedMobilePdfOverflowItem("Dictionary", leadingIcon = { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }, onClick = { closeMenuAndRun { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) } })
                            PdfReaderTool.THEME -> SharedMobilePdfOverflowItem("Theme", onClick = { closeMenuAndRun(onTheme) })
                            PdfReaderTool.BRIGHTNESS -> SharedMobilePdfOverflowItem("Brightness", leadingIcon = { Icon(SharedReaderIcons.Contrast, contentDescription = null) }, onClick = { closeMenuAndRun(onBrightness) })
                            PdfReaderTool.LOCK_PANNING -> SharedMobilePdfOverflowItem(if (isScrollLocked) "Unlock Panning" else "Lock Panning", onClick = { closeMenuAndRun(onToggleScrollLock) })
                            PdfReaderTool.SLIDER -> SharedMobilePdfOverflowItem("Navigation Slider", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onShowSlider) })
                            PdfReaderTool.TOC -> SharedMobilePdfOverflowItem("Sidebar", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onOpenDrawer) })
                            PdfReaderTool.SEARCH -> SharedMobilePdfOverflowItem("Search", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onSearch) })
                            PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfOverflowItem(
                                "Highlight Selectable Text",
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
                            PdfReaderTool.EDIT_MODE -> SharedMobilePdfOverflowItem("Edit Mode", onClick = { closeMenuAndRun(onEditMode) })
                            PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfOverflowItem("TTS Controls", onClick = { closeMenuAndRun(onToggleTts) })
                            PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfOverflowItem("Screen Orientation", leadingIcon = { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }, onClick = { closeMenuAndRun(onScreenOrientation) })
                            else -> Unit
                        }
                    }
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.VISUAL_OPTIONS)) SharedMobilePdfOverflowItem(
                    "Visual Options",
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onVisualOptions()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) SharedMobilePdfOverflowItem(
                    "Change Reading Mode",
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showReadingModeExpanded = !showReadingModeExpanded }
                )
                if (showReadingModeExpanded && toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) {
                    SharedMobilePdfOverflowItem(
                        "Vertical Scrolling",
                        trailingIcon = { if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.VERTICAL_SCROLL) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                    SharedMobilePdfOverflowItem(
                        "Pagination",
                        trailingIcon = { if (displayMode == PdfDisplayMode.PAGINATION) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.PAGINATION) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.TAP_TO_TURN)) SharedMobilePdfOverflowItem(
                    "Tap to Turn Pages",
                    enabled = displayMode == PdfDisplayMode.PAGINATION,
                    trailingIcon = { if (tapToTurnPages) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleTapToTurnPages()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.KEEP_SCREEN_ON)) SharedMobilePdfOverflowItem(
                    "Keep Screen On",
                    trailingIcon = { if (keepScreenOn) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleKeepScreenOn()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.AUTO_SCROLL)) SharedMobilePdfOverflowItem(
                    "Auto Scroll",
                    enabled = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
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
                    "TTS Settings",
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded }
                )
                if (showTtsSettingsExpanded) {
                    if (showVoiceSettings) SharedMobilePdfOverflowItem(
                        "Voice Settings",
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onVoiceSettings()
                        },
                    )
                    if (showWordReplacements) SharedMobilePdfOverflowItem(
                        "Word Replacements",
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onWordReplacements()
                        },
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.BOOKMARK)) SharedMobilePdfOverflowItem(
                    if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                    onClick = {
                        showMoreMenu = false
                        onToggleBookmark()
                    }
                )
                if (PdfReaderTool.PAGE_MANAGEMENT in SharedMobilePdfAvailableTools && toolbarPreferences.isVisible(PdfReaderTool.PAGE_MANAGEMENT)) {
                    SharedMobilePdfOverflowItem(
                        "Insert Blank Page",
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onInsertBlankPage()
                        }
                    )
                    if (isCurrentPageBlank) {
                        SharedMobilePdfOverflowItem(
                            "Delete Blank Page",
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
                    "Share, Save or Print",
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showFileActionsExpanded = !showFileActionsExpanded }
                )
                if (showFileActionsExpanded) {
                    if (toolbarPreferences.isVisible(PdfReaderTool.SHARE)) SharedMobilePdfOverflowItem("Share", leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SHARE) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.SAVE_COPY)) SharedMobilePdfOverflowItem("Save Copy to Device", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SAVE_COPY) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.PRINT)) SharedMobilePdfOverflowItem("Print", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.PRINT) })
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.FILE_INFO)) SharedMobilePdfOverflowItem(
                    "File Information",
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onFileInformation()
                    }
                )
                }
            }
        }
    }
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
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
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
    PdfReaderTool.THEME,
    PdfReaderTool.BRIGHTNESS,
    PdfReaderTool.LOCK_PANNING,
    PdfReaderTool.FILE_INFO,
    PdfReaderTool.VISUAL_OPTIONS,
    PdfReaderTool.TAP_TO_TURN,
    PdfReaderTool.SLIDER,
    PdfReaderTool.TOC,
    PdfReaderTool.SEARCH,
    PdfReaderTool.HIGHLIGHT_ALL,
    PdfReaderTool.EDIT_MODE,
    PdfReaderTool.TTS_CONTROLS,
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
                            title = tool.title,
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
                            title = tool.title,
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
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true, onClick = {}),
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Generating Text View…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfVisualOptionsSheet(
    displayMode: PdfDisplayMode,
    systemUiMode: SharedMobilePdfSystemUiMode,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    rightToLeftPagination: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    onSystemUiModeChange: (SharedMobilePdfSystemUiMode) -> Unit,
    onTwoPageSpreadChange: (Boolean) -> Unit,
    onFirstPageStandaloneChange: (Boolean) -> Unit,
    onRightToLeftPaginationChange: (Boolean) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("System UI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose when the reader toolbars and system controls are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SharedMobilePdfSegmentedControl(
                options = SharedMobilePdfSystemUiMode.entries,
                selectedOption = systemUiMode,
                onOptionSelected = onSystemUiModeChange,
                label = { it.label }
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Page Layout", style = MaterialTheme.typography.titleMedium)
            if (displayMode == PdfDisplayMode.PAGINATION) {
                Spacer(Modifier.height(4.dp))
                Text("PDF Page Spread", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SharedMobilePdfSegmentedControl(
                    options = listOf(false, true),
                    selectedOption = useTwoPageSpread,
                    onOptionSelected = onTwoPageSpreadChange,
                    label = { if (it) "Two Page" else "Single" }
                )
                if (useTwoPageSpread) {
                    SharedMobilePdfVisualOptionSwitchRow(
                        title = "First Page Alone",
                        description = "Show the cover by itself before paired pages.",
                        checked = firstPageStandaloneInSpread,
                        onCheckedChange = onFirstPageStandaloneChange
                    )
                }
                SharedMobilePdfVisualOptionSwitchRow(
                    title = "Paginated (right-to-left)",
                    description = "Use right-to-left page order and edge navigation.",
                    checked = rightToLeftPagination,
                    onCheckedChange = onRightToLeftPaginationChange,
                )
                Spacer(Modifier.height(12.dp))
            }
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Remove Page Gap",
                description = "Display adjacent pages without spacing.",
                checked = !showVerticalPageGap,
                onCheckedChange = { onShowVerticalPageGapChange(!it) }
            )
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Hide Page Number Overlay",
                description = "Hide the number shown on each PDF page.",
                checked = !showPageNumberOverlay,
                onCheckedChange = { onShowPageNumberOverlayChange(!it) }
            )
            }
        }
    }
@Composable
private fun <T> SharedMobilePdfSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfVisualOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SharedMobilePdfReaderBottomBar(
    state: SharedPdfReaderState,
    tools: List<PdfReaderTool>,
    isStylusOnlyMode: Boolean = false,
    onToggleStylusOnlyMode: (() -> Unit)? = null,
    onShowSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    ttsState: SharedMobileEpubLocalTtsState,
    isTtsPlayingOrLoading: Boolean,
            onToggleTts: () -> Unit,
            onTheme: () -> Unit,
            onBrightness: () -> Unit,
            onToggleScrollLock: () -> Unit,
            onScreenOrientation: () -> Unit,
            onDictionary: () -> Unit,
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
            modifier = Modifier.then(
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
                        PdfReaderTool.SLIDER -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onShowSlider) { Icon(SharedReaderIcons.Slider, contentDescription = "Navigation slider") }
                        PdfReaderTool.TOC -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
                        PdfReaderTool.SEARCH -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfBottomToolButton(
                            selected = showAllTextHighlights,
                            onClick = onToggleHighlights,
                        ) {
                            if (isAllTextHighlightLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(SharedReaderIcons.HighlightText, contentDescription = "Highlight selectable text")
                            }
                        }
                        PdfReaderTool.EDIT_MODE -> SharedMobilePdfBottomToolButton(
                            selected = state.selectedTool != PdfInkTool.NONE,
                            onClick = { onToolSelected(if (state.selectedTool == PdfInkTool.NONE) PdfInkTool.PEN else PdfInkTool.NONE) },
                        ) { Icon(Icons.Default.Edit, contentDescription = "Edit mode") }
                        PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfBottomToolButton(onClick = onToggleTts) {
                            Icon(
                                if (ttsState != SharedMobileEpubLocalTtsState.IDLE) Icons.Default.Close else SharedReaderIcons.TextToSpeech,
                                contentDescription = "Text to speech",
                            )
                        }
                        PdfReaderTool.DICTIONARY -> SharedMobilePdfBottomToolButton(onClick = onDictionary) { Icon(SharedReaderIcons.Dictionary, contentDescription = "Dictionary") }
                        PdfReaderTool.THEME -> SharedMobilePdfBottomToolButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
                        PdfReaderTool.BRIGHTNESS -> SharedMobilePdfBottomToolButton(onClick = onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = "Brightness") }
                        PdfReaderTool.LOCK_PANNING -> SharedMobilePdfBottomToolButton(onClick = onToggleScrollLock) { Icon(if (state.isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (state.isScrollLocked) "Unlock panning" else "Lock panning") }
                        PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfBottomToolButton(onClick = onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = "Screen orientation") }
                        else -> Unit
                    }
                }
            }
            if (state.selectedTool != PdfInkTool.NONE) {
                SharedPdfInteractionDock(
                    isTextSelectionMode = false,
                    isStylusOnlyMode = isStylusOnlyMode,
                    onToggleStylusOnlyMode = onToggleStylusOnlyMode,
                    selectedTool = state.selectedTool,
                    selectedColor = state.selectedColorArgb,
                    strokeWidth = state.strokeWidth,
                    toolConfigs = state.toolConfigs,
                    penPalette = state.penPalette,
                    lastActivePenTool = state.lastActivePenTool,
                    lastActiveHighlighterTool = state.lastActiveHighlighterTool,
                    onPanSelected = { onToolSelected(PdfInkTool.NONE) },
                    onTextSelectionSelected = { onToolSelected(PdfInkTool.NONE) },
                    onToolSelected = onToolSelected,
                    onColorSelected = onColorSelected,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onClearPage = onClearPage,
                    canUndo = state.annotations.any { it.pageIndex == state.pageIndex },
                    canRedo = state.canRedoAnnotationEdit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
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
        buildList {
            add(SharedMobilePdfDrawerSection.CHAPTERS)
            add(SharedMobilePdfDrawerSection.PAGES)
            add(SharedMobilePdfDrawerSection.BOOKMARKS)
            add(SharedMobilePdfDrawerSection.HIGHLIGHTS)
            if (tabsEnabled && tabs.isNotEmpty()) add(SharedMobilePdfDrawerSection.TABS)
        }
    }
    val pagerState = rememberPagerState(initialPage = 0) { sections.size }
    LaunchedEffect(sections.size) {
        val maxPage = sections.size - 1
        if (pagerState.currentPage > maxPage) pagerState.scrollToPage(maxPage.coerceAtLeast(0))
    }
    ModalDrawerSheet(modifier = Modifier.width(348.dp)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage.coerceAtMost(sections.lastIndex)) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(section.label) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (sections.getOrNull(page)) {
                    SharedMobilePdfDrawerSection.CHAPTERS -> SharedMobilePdfChaptersDrawerPage(
                        entries = tableOfContents,
                        currentPageIndex = state.currentNearestPdfPageIndex ?: 0,
                        onGoToPage = onGoToPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.PAGES -> SharedMobilePdfPagesDrawerPage(
                        book = book,
                        state = state,
                        pdfPassword = pdfPassword,
                        onGoToPage = onGoToDisplayPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.BOOKMARKS -> SharedMobilePdfBookmarksDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.HIGHLIGHTS -> SharedMobilePdfAnnotationsDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onEditNote = onEditNote,
                        onDeleteHighlight = onDeleteHighlight,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.TABS -> SharedMobilePdfTabsDrawerPage(
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

private enum class SharedMobilePdfDrawerSection(val label: String) {
    CHAPTERS("Chapters"),
    PAGES("Pages"),
    BOOKMARKS("Bookmarks"),
    HIGHLIGHTS("Highlights"),
    TABS("Tabs"),
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
    pdfPassword: String?,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPageCount = state.displayPageCount
    val pageRows = remember(displayPageCount) { sharedPdfThumbnailRows(displayPageCount) }
    val currentRowIndex = sharedPdfThumbnailRowFor(state.pageIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onGoToPage(pageIdx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isBlank && pdfPage != null) {
                                    val thumbnail = rememberSharedMobilePdfPageThumbnail(
                                        book = book,
                                        pageIndex = pdfPage,
                                        password = pdfPassword,
                                    )
                                    thumbnail.bitmap?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Page ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
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
                { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
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
                                        contentDescription = if (expanded) "Collapse chapter" else "Expand chapter"
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
                        IconButton(onClick = { menuBookmark = bookmark }) { Icon(Icons.Default.MoreVert, contentDescription = "Bookmark options", modifier = Modifier.size(18.dp)) }
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
                    contentDescription = "Toggle search highlights",
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
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous result")
            }
            TextButton(onClick = onShowResults) {
                Text(
                    if (activeIndex in 0 until resultCount) "${activeIndex + 1} of $resultCount"
                    else "$resultCount results"
                )
            }
            IconButton(onClick = onNext, enabled = activeIndex < resultCount - 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next result")
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
                )
            }
        }
    }
}
