package com.aryan.reader.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderLifecycleAction
import com.aryan.reader.shared.ReaderAutoScrollProfile
import com.aryan.reader.shared.ReaderAutoScrollBoundaryAction
import com.aryan.reader.shared.ReaderMusicianGesturePlan
import com.aryan.reader.shared.ReaderMusicianNavigationTarget
import com.aryan.reader.shared.planReaderMusicianGesture
import com.aryan.reader.shared.readerSearchDelayMillis
import com.aryan.reader.shared.readerExternalLookupActionForSelectionId
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsOverlaySize
import com.aryan.reader.shared.readerTtsOverlayAlignmentBias
import com.aryan.reader.shared.ReaderBookReplacementEngine
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.matchingReaderBookmark
import com.aryan.reader.shared.withoutMatchingReaderBookmarks
import com.aryan.reader.shared.readerLifecycleAction
import com.aryan.reader.shared.readerAutoScrollPixelsPerSecond
import com.aryan.reader.shared.readerAutoScrollBoundaryAction
import com.aryan.reader.shared.migrateLegacyIosReaderAutoScrollSpeed
import com.aryan.reader.shared.migrateAndroidEpubFormatSettings
import com.aryan.reader.shared.shouldFollowReaderTtsChunk
import com.aryan.reader.shared.shouldShowEpubPageInfoBar
import com.aryan.reader.shared.toSharedReaderFontFamily
import com.aryan.reader.shared.withTtsReplacements
import com.aryan.reader.shared.withReaderFormatFrom
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderJumpHistory
import com.aryan.reader.shared.reader.captureReaderJumpHistoryOrigin
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.sharedReaderPageInfo
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubPaginationCache
import com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator
import com.aryan.reader.shared.reader.sharedEpubOpenTrace
import com.aryan.reader.shared.reader.sharedEpubOpenTraceElapsedMs
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMark
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMs
import com.aryan.reader.shared.reader.effectiveReaderTocEntries
import com.aryan.reader.shared.reader.findPageIndexForLocator
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reader.readerImageReferences
import com.aryan.reader.shared.reader.readerTocActiveIndex
import com.aryan.reader.shared.reader.pullToTurnEnabled
import com.aryan.reader.shared.reader.seamlessChapterTransitionEnabled
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aryan.reader.shared.reader.mobileEpubSystemBarsVisibility

data class SharedMobileEpubReaderSnapshot(
    val locator: ReaderLocator,
    val settings: ReaderSettings,
    val bookmarks: List<ReaderBookmark>,
    val highlights: List<UserHighlight>,
    val progressPercent: Float,
    val pageIndex: Int,
    val pageCount: Int,
    val formatIsLocal: Boolean,
    val localFormatSettings: ReaderSettings?,
    val autoScrollIsLocal: Boolean,
    val autoScrollLocalSpeed: Float?,
    val autoScrollLocalMinSpeed: Float?,
    val autoScrollLocalMaxSpeed: Float?,
)

/** Outgoing page set while an Android-benchmark realistic page turn animates. */
private data class SharedMobileEpubActivePageTurn(
    val outgoingPages: List<ReaderPage>,
    val direction: Int,
    val touchY: Float?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileEpubReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onReaderStateChange: (SharedMobileEpubReaderSnapshot) -> Unit = {},
    onMetadataLoaded: (title: String, author: String?) -> Unit = { _, _ -> },
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    appIsActive: Boolean = true,
    appLifecycleEventId: Long = 0L,
    initialKeepScreenOn: Boolean = false,
    onKeepScreenOnPreferenceChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (statusHidden: Boolean, navigationHidden: Boolean, lightContent: Boolean, backgroundArgb: Long) -> Unit = { _, _, _, _ -> },
    onSystemUiRelease: () -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onImportFont: () -> Unit = {},
    readerDefaultSettings: ReaderSettings = ReaderSettings(),
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    readerHighlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    onReaderHighlightPaletteChange: (ReaderHighlightPalette) -> Unit = {},
    readerToolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    readerBookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    onReaderBookReplacementPreferencesChange: (ReaderBookReplacementPreferences) -> Unit = {},
    onOpenDictionarySettings: () -> Unit = {},
    readerAiAvailable: Boolean = false,
    readerExtrasState: ReaderExtrasState = ReaderExtrasState(),
    cloudTts: SharedMobileEpubCloudTts? = null,
    cloudTtsModeEnabled: Boolean = false,
    onCloudTtsModeChange: (Boolean) -> Unit = {},
    cloudTtsVoiceId: String = com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID,
    onCloudTtsVoiceChange: (String) -> Unit = {},
    onClearCloudTtsCache: () -> Unit = {},
    initialTtsOverlaySize: ReaderTtsOverlaySize = ReaderTtsOverlaySize.LARGE,
    onTtsOverlaySizePreferenceChange: (ReaderTtsOverlaySize) -> Unit = {},
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onAiResultDismiss: () -> Unit = {},
    onOpenAiHub: () -> Unit = {},
    readerBrightness: Float? = null,
    readerCustomBrightness: Float = com.aryan.reader.shared.DefaultReaderCustomBrightness,
    readerBrightnessSupported: Boolean = false,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerAutoScrollProfile: ReaderAutoScrollProfile = ReaderAutoScrollProfile(),
    onReaderAutoScrollProfileChange: (ReaderAutoScrollProfile) -> Unit = {},
    initialAutoScrollUseSlider: Boolean = false,
    onAutoScrollUseSliderPreferenceChange: (Boolean) -> Unit = {},
    initialAutoScrollMusicianMode: Boolean = false,
    onAutoScrollMusicianModePreferenceChange: (Boolean) -> Unit = {},
    initialPageSliderVisible: Boolean = false,
    onPageSliderVisibilityPreferenceChange: (Boolean) -> Unit = {},
    initialUseNativeVerticalRenderer: Boolean = false,
    onUseNativeVerticalRendererPreferenceChange: (Boolean) -> Unit = {},
    onTtsError: ((String) -> Unit)? = null,
    onClipboardError: ((String) -> Unit)? = null,
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    streamPageLoader: SharedMobileEpubStreamPageLoader? = null,
    modifier: Modifier = Modifier
) {
    val motionPolicy = rememberReaderMotionPolicy()
    remember(book.id) {
        sharedEpubOpenTrace { "readerScreen enter bookId=${book.id} type=${book.type} hasPosition=${book.readerPosition != null}" }
    }
    val loadState = rememberSharedMobileEpubLoadState(book)
    val rawLoadedBook = loadState.book
    val bookReplacementSignature = readerBookReplacementPreferences.signatureForFile(book.id)
    val loadedBook = remember(rawLoadedBook, book.id, bookReplacementSignature) {
        val replacementMark = sharedEpubOpenTraceMark()
        val replaced = rawLoadedBook?.copy(
            chapters = rawLoadedBook.chapters.map { chapter ->
                chapter.copy(
                    plainText = ReaderBookReplacementEngine.apply(
                        chapter.plainText,
                        readerBookReplacementPreferences,
                        book.id
                    ).text,
                    htmlContent = ReaderBookReplacementEngine.applyToHtml(
                        chapter.htmlContent,
                        readerBookReplacementPreferences,
                        book.id
                    )
                )
            }
        )
        sharedEpubOpenTrace { "readerScreen replacements ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(replacementMark))} chapters=${replaced?.chapters?.size ?: 0}" }
        replaced
    }
    val localTts = rememberSharedMobileEpubLocalTts()
    val streamPageUnavailableLabel = readerString("msg_page_unavailable", "Page Unavailable")
    val cloudTtsState = cloudTts?.state ?: readerExtrasState.cloudTts
    val cloudTtsAvailable = cloudTts != null && cloudTtsState.isAvailable && !localTts.isSessionActive
    LaunchedEffect(localTts.errorMessage) {
        localTts.errorMessage?.let { message -> onTtsError?.invoke(message) }
    }
    val activeTtsChunk = localTts.progress.currentChunk
    var detachedTtsChunkIndex by remember(book.id) { mutableStateOf<Int?>(null) }
    val migratedReaderDefaults = readerDefaultSettings.migrateAndroidEpubFormatSettings()
    val storedBookSettings = (book.readerSettings ?: migratedReaderDefaults).migrateAndroidEpubFormatSettings()
    val migratedLocalFormatSettings = book.readerLocalFormatSettings?.migrateAndroidEpubFormatSettings()
    var isLocalFormatMode by remember(book.id) { mutableStateOf(book.readerFormatIsLocal) }
    var localFormatSettings by remember(book.id) { mutableStateOf(migratedLocalFormatSettings) }
    var settings by remember(book.id) {
        mutableStateOf(
            if (book.readerFormatIsLocal) {
                storedBookSettings.withReaderFormatFrom(migratedLocalFormatSettings ?: storedBookSettings)
            } else {
                storedBookSettings.withReaderFormatFrom(migratedReaderDefaults)
            }
        )
    }
    var pages by remember(book.id) { mutableStateOf<List<ReaderPage>>(emptyList()) }
    var measuredPagesApplied by remember(book.id) { mutableStateOf(false) }
    var currentLocator by remember(book.id) { mutableStateOf(book.readerPosition) }
    var currentPageIndex by remember(book.id) { mutableStateOf(book.lastPageIndex ?: 0) }
    var currentChapterIndex by remember(book.id) {
        mutableIntStateOf(book.readerPosition?.chapterIndex?.coerceAtLeast(0) ?: 0)
    }
    val activeCloudTtsChunk = cloudTtsState.progress.currentChunk

    fun planReaderTtsChunks(epub: com.aryan.reader.shared.reader.SharedEpubBook): List<ReaderTtsChunk> {
        val session = ReaderEngine().createSession(
            book = epub,
            settings = settings,
            initialPageIndex = currentPageIndex,
            initialLocator = currentLocator,
        )
        return ReaderTtsPlanner.chunksFromCurrentLocation(session)
            .ifEmpty { ReaderTtsPlanner.chunksForCurrentChapter(session) }
            .withTtsReplacements(readerTtsReplacementPreferences, book.id)
    }

    fun toggleCloudTts() {
        val controller = cloudTts ?: return
        when {
            cloudTtsState.isPlaying || cloudTtsState.isLoading -> controller.pause()
            cloudTtsState.isPaused -> controller.resume()
            else -> loadedBook?.let { epub ->
                localTts.stop()
                val planned = planReaderTtsChunks(epub)
                controller.start(planned, epub.title, book.id)
            }
        }
    }
    var bookmarks by remember(book.id) { mutableStateOf(book.readerBookmarks) }
    var highlights by remember(book.id) { mutableStateOf(book.readerHighlights) }
    var jumpHistory by remember(book.id) { mutableStateOf(ReaderJumpHistory()) }
    var editingHighlight by remember(book.id) { mutableStateOf<UserHighlight?>(null) }
    var showHighlightPaletteManager by remember(book.id) { mutableStateOf(false) }
    // Match Android's distraction-free reader entry; a reader tap reveals chrome.
    var showChrome by remember(book.id) { mutableStateOf(false) }
    var showFormatSheet by remember(book.id) { mutableStateOf(false) }
    var showThemeSheet by remember(book.id) { mutableStateOf(false) }
    var showVisualOptionsSheet by remember(book.id) { mutableStateOf(false) }
    var showSearch by remember(book.id) { mutableStateOf(false) }
    var showSearchResultsPanel by remember(book.id) { mutableStateOf(true) }
    var searchQuery by remember(book.id) { mutableStateOf("") }
    var searchRequestId by remember(book.id) { mutableLongStateOf(0L) }
    var immediateSearchRequestId by remember(book.id) { mutableLongStateOf(-1L) }
    var searchResults by remember(book.id) { mutableStateOf<List<SharedMobileEpubSearchResult>>(emptyList()) }
    var isSearchInProgress by remember(book.id) { mutableStateOf(false) }
    var searchResultIndex by remember(book.id) { mutableIntStateOf(-1) }
    var pullDirection by remember(book.id) { mutableStateOf<String?>(null) }
    var pullProgress by remember(book.id) { mutableStateOf(0f) }
    var showSlider by remember(book.id) { mutableStateOf(initialPageSliderVisible) }
    var showMore by remember(book.id) { mutableStateOf(false) }
    var showAiHub by remember(book.id) { mutableStateOf(false) }
    var showFileInfo by remember(book.id) { mutableStateOf(false) }
    var showCustomizeToolsSheet by remember(book.id) { mutableStateOf(false) }
    var showScreenOrientationSheet by remember(book.id) { mutableStateOf(false) }
    var showBrightnessSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsSettingsSheet by remember(book.id) { mutableStateOf(false) }
    var showBookReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var ttsOverlaySize by remember(book.id, initialTtsOverlaySize) {
        mutableStateOf(initialTtsOverlaySize)
    }
    var pendingExternalLink by remember(book.id) { mutableStateOf<String?>(null) }
    var activeFootnote by remember(book.id) { mutableStateOf<SharedMobileEpubFootnote?>(null) }
    var keepScreenOn by remember(book.id) { mutableStateOf(initialKeepScreenOn) }
    var autoScrollModeActive by remember(book.id) { mutableStateOf(false) }
    var autoScroll by remember(book.id) { mutableStateOf(false) }
    var autoScrollUseSlider by remember { mutableStateOf(initialAutoScrollUseSlider) }
    var autoScrollMusicianMode by remember { mutableStateOf(initialAutoScrollMusicianMode) }
    var autoScrollCollapsed by remember(book.id) { mutableStateOf(false) }
    var autoScrollTemporarilyPaused by remember(book.id) { mutableStateOf(false) }
    var autoScrollPauseRequestId by remember(book.id) { mutableLongStateOf(0L) }
    var autoScrollIsLocal by remember(book.id) { mutableStateOf(book.readerAutoScrollIsLocal) }
    var useNativeVerticalRenderer by remember(book.id) { mutableStateOf(initialUseNativeVerticalRenderer) }
    val nativeVerticalScrollController = remember(book.id) { SharedNativeVerticalScrollController() }
    val nativePaginatedPositionController = remember(book.id) {
        SharedNativePaginatedPositionController()
    }
    val webViewPositionController = remember(book.id) { SharedMobileEpubWebViewController() }
    var autoScrollLocalProfile by remember(book.id) {
        mutableStateOf(
            book.readerAutoScrollLocalSpeed?.let { speed ->
                ReaderAutoScrollProfile(
                    speed = migrateLegacyIosReaderAutoScrollSpeed(speed),
                    minSpeed = book.readerAutoScrollLocalMinSpeed
                        ?.let(::migrateLegacyIosReaderAutoScrollSpeed)
                        ?: readerAutoScrollProfile.minSpeed,
                    maxSpeed = book.readerAutoScrollLocalMaxSpeed
                        ?.let(::migrateLegacyIosReaderAutoScrollSpeed)
                        ?: readerAutoScrollProfile.maxSpeed,
                ).sanitized()
            }
        )
    }
    var autoScrollProfile by remember(book.id) {
        mutableStateOf(
            if (book.readerAutoScrollIsLocal) autoScrollLocalProfile ?: readerAutoScrollProfile.sanitized()
            else readerAutoScrollProfile.sanitized()
        )
    }
    var drawerTab by remember(book.id) { mutableStateOf(0) }
    var selectedTocIndex by remember(book.id) { mutableIntStateOf(-1) }
    var explicitNavigationLocator by remember(book.id) { mutableStateOf<ReaderLocator?>(null) }
    var explicitNavigationFragment by remember(book.id) { mutableStateOf<String?>(null) }
    var explicitNavigationChunkIndex by remember(book.id) { mutableStateOf<Int?>(null) }
    var explicitNavigationChunkHtml by remember(book.id) { mutableStateOf<String?>(null) }
    var navigationRequestId by remember(book.id) { mutableLongStateOf(0L) }
    var commandScript by remember(book.id) { mutableStateOf<String?>(null) }
    var readerViewport by remember(book.id) { mutableStateOf(ReaderViewportSpec(0, 0)) }
    val readerDensity = LocalDensity.current
    val readerTextMeasurer = rememberTextMeasurer()
    val paginationCacheWriteScope = rememberCoroutineScope()
    val epubPaginationCache = remember(book.id) { SharedEpubPaginationCache() }
    val measuredPaginator = remember(
        readerTextMeasurer,
        readerDensity,
        settings.fontFamily,
        settings.customFontPath,
        epubPaginationCache,
        paginationCacheWriteScope
    ) {
        SharedMeasuredEpubPaginator(
            textMeasurer = readerTextMeasurer,
            density = readerDensity,
            fontFamily = settings.toSharedReaderFontFamily(),
            pageCache = epubPaginationCache,
            cacheWriteScope = paginationCacheWriteScope
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val copiedTextLabel = readerString("clip_label_copied_text", "Copied Text")
    val copiedLinkLabel = readerString("clip_label_copied_link", "Copied Link")
    val clipboardErrorMessage = readerString("error_copy_to_clipboard", "Could not copy to clipboard")

    fun copyToClipboard(text: String, label: String = copiedTextLabel): SharedClipboardResult {
        val result = writeSharedClipboard(label = label, text = text)
        if (!result.success) onClipboardError?.invoke(clipboardErrorMessage)
        return result
    }
    val sanitizedToolbarPreferences = readerToolbarPreferences.sanitized()
    val visibleToolbarTools = sanitizedToolbarPreferences.orderedVisibleTools().filter {
        it in SharedMobileEpubCustomizableTools && (it != ReaderTool.AI_FEATURES || readerAiAvailable)
    }
    val mobileBottomToolIds = if (readerToolbarPreferences.bottomToolIds == ReaderToolbarPreferences.defaultBottomToolIds) {
        readerToolbarPreferences.bottomToolIds + ReaderTool.TTS_CONTROLS.id
    } else {
        readerToolbarPreferences.bottomToolIds
    }
    val bottomToolbarTools = visibleToolbarTools.filter { tool ->
        tool in SharedMobileEpubToolbarTools && tool.id in mobileBottomToolIds
    }
    val topToolbarTools = visibleToolbarTools.filter { tool ->
        tool in SharedMobileEpubToolbarTools && tool.id !in mobileBottomToolIds
    }
    val overflowMenuTools = visibleToolbarTools.filterNot { it in SharedMobileEpubToolbarTools }
    val systemBarsVisibility = mobileEpubSystemBarsVisibility(settings.systemUiMode, showChrome)
    val systemUiHidden = !systemBarsVisibility.statusBarsVisible
    val navigationUiHidden = !systemBarsVisibility.navigationBarsVisible

    fun refreshSelectedTocIndex(
        locator: ReaderLocator? = currentLocator,
        activeHref: String? = null,
        activeFragmentId: String? = null
    ) {
        val epub = loadedBook ?: return
        val effectiveLocator = locator ?: ReaderLocator(
            chapterIndex = currentChapterIndex,
            href = epub.chapters.getOrNull(currentChapterIndex)?.baseHref
        )
        readerTocActiveIndex(
            entries = epub.effectiveReaderTocEntries(),
            book = epub,
            locator = effectiveLocator,
            activeHref = activeHref,
            activeFragmentId = activeFragmentId
        )?.let { selectedTocIndex = it }
    }

    DisposableEffect(readerScreenOrientationMode, onApplyReaderScreenOrientation) {
        onApplyReaderScreenOrientation(readerScreenOrientationMode)
        onDispose { onApplyReaderScreenOrientation(ReaderScreenOrientationMode.FOLLOW_SYSTEM) }
    }

    fun openReaderDrawer(tab: Int? = null) {
        tab?.let { drawerTab = it }
        scope.launch {
            if (motionPolicy.animationsEnabled) drawerState.open() else drawerState.snapTo(DrawerValue.Open)
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(loadedBook?.id) {
        loadedBook?.let {
            measuredPagesApplied = false
            jumpHistory = jumpHistory.pruned(it.chapters.size)
            currentChapterIndex = currentChapterIndex.coerceIn(0, it.chapters.lastIndex.coerceAtLeast(0))
            if (selectedTocIndex < 0) {
                refreshSelectedTocIndex()
            }
            onMetadataLoaded(it.title, it.author)
        }
    }

    LaunchedEffect(loadedBook, settings.layoutSignature()) {
        val epub = loadedBook ?: return@LaunchedEffect
        if (pages.isNotEmpty()) delay(180)
        if (measuredPagesApplied) return@LaunchedEffect
        val locator = currentLocator ?: book.readerPosition
        val sessionMark = sharedEpubOpenTraceMark()
        val readerState = withContext(Dispatchers.Default) {
            ReaderEngine().createSession(
                book = epub,
                settings = settings,
                initialPageIndex = currentPageIndex,
                initialLocator = locator
            ).reader
        }
        sharedEpubOpenTrace { "readerScreen estimateSession ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(sessionMark))} pages=${readerState.pages.size}" }
        pages = readerState.pages
        currentPageIndex = readerState.currentPageIndex.coerceIn(0, readerState.pages.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(
        loadedBook,
        settings.layoutSignature(),
        readerViewport,
        measuredPaginator
    ) {
        val epub = loadedBook ?: return@LaunchedEffect
        if (settings.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
        if (!readerViewport.isSpecified) return@LaunchedEffect
        val paginateMark = sharedEpubOpenTraceMark()
        sharedEpubOpenTrace { "readerScreen measuredPaginate start viewport=${readerViewport.widthPx}x${readerViewport.heightPx}" }
        val measuredPages = withContext(Dispatchers.Default) {
            measuredPaginator.paginate(epub, settings, readerViewport)
        }
        sharedEpubOpenTrace { "readerScreen measuredPaginate done pages=${measuredPages.size} ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(paginateMark))}" }
        measuredPagesApplied = true
        val anchor = currentLocator ?: book.readerPosition
        val targetIndex = anchor
            ?.let { measuredPages.findPageIndexForLocator(it) }
            ?.takeIf { it >= 0 }
            ?: currentPageIndex.coerceIn(0, measuredPages.lastIndex.coerceAtLeast(0))
        pages = measuredPages
        currentPageIndex = targetIndex
    }

    LaunchedEffect(keepScreenOn) { onKeepScreenOnChange(keepScreenOn) }
    LaunchedEffect(book.id, showSlider) {
        onPageSliderVisibilityPreferenceChange(showSlider)
    }
    LaunchedEffect(settings.systemUiMode, settings.darkMode, settings.backgroundColorArgb, showChrome) {
        val backgroundArgb = settings.backgroundColorArgb ?: if (settings.darkMode) 0xFF121212L else 0xFFFFFFFFL
        onSystemUiAppearanceChange(
            systemUiHidden,
            navigationUiHidden,
            backgroundArgb.hasDarkReaderBackground(),
            backgroundArgb
        )
    }
    DisposableEffect(book.id) {
        onDispose {
            onKeepScreenOnChange(false)
            onSystemUiRelease()
        }
    }
    LaunchedEffect(autoScroll, autoScrollProfile.speed, autoScrollTemporarilyPaused, useNativeVerticalRenderer) {
        if (useNativeVerticalRenderer) return@LaunchedEffect
        commandScript = if (autoScroll && !autoScrollTemporarilyPaused) {
            sharedMobileEpubAutoScrollStartScript(autoScrollProfile.speed)
        } else {
            SharedMobileEpubAutoScrollStopScript
        }
        navigationRequestId++
    }
    LaunchedEffect(localTts.isSessionActive) {
        if (!localTts.isSessionActive) detachedTtsChunkIndex = null
    }
    LaunchedEffect(loadedBook, searchQuery, searchRequestId, pages) {
        val epub = loadedBook
        val query = searchQuery.trim()
        if (epub == null || query.isBlank()) {
            searchResults = emptyList()
            searchResultIndex = -1
            isSearchInProgress = false
            return@LaunchedEffect
        }
        delay(readerSearchDelayMillis(searchRequestId, immediateSearchRequestId))
        isSearchInProgress = true
        try {
            searchResults = withContext(Dispatchers.Default) { epub.searchMobileEpub(query, pages) }
            searchResultIndex = -1
            showSearchResultsPanel = true
        } finally {
            isSearchInProgress = false
        }
    }

    val pageCount = pages.size.coerceAtLeast(1)
    val liveVerticalLocator = currentLocator.takeIf { settings.readingMode == ReaderReadingMode.VERTICAL }
    val pageInfo = remember(loadedBook, pages, currentPageIndex, liveVerticalLocator) {
        loadedBook?.let { sharedReaderPageInfo(it, pages, currentPageIndex, liveVerticalLocator) }
    }
    val progress = pageInfo?.progressPercent?.toFloat()
        ?: ((currentPageIndex + 1).toFloat() / pageCount) * 100f

    fun currentReaderSnapshot(): SharedMobileEpubReaderSnapshot? {
        val locator = currentLocator ?: return null
        return SharedMobileEpubReaderSnapshot(
            locator = locator,
            settings = settings,
            bookmarks = bookmarks,
            highlights = highlights,
            progressPercent = progress.coerceIn(0f, 100f),
            pageIndex = currentPageIndex,
            pageCount = pageCount,
            formatIsLocal = isLocalFormatMode,
            localFormatSettings = localFormatSettings,
            autoScrollIsLocal = autoScrollIsLocal,
            autoScrollLocalSpeed = autoScrollLocalProfile?.speed,
            autoScrollLocalMinSpeed = autoScrollLocalProfile?.minSpeed,
            autoScrollLocalMaxSpeed = autoScrollLocalProfile?.maxSpeed,
        )
    }

    LaunchedEffect(currentLocator, settings, bookmarks, highlights, currentPageIndex, pageCount, isLocalFormatMode, localFormatSettings, autoScrollIsLocal, autoScrollLocalProfile) {
        delay(220)
        currentReaderSnapshot()?.let(onReaderStateChange)
    }

    fun closeReader() {
        currentReaderSnapshot()?.let(onReaderStateChange)
        onBack()
    }

    fun detachVerticalReaderFromTts() {
        if (
            settings.readingMode == ReaderReadingMode.VERTICAL &&
            localTts.isSessionActive
        ) {
            detachedTtsChunkIndex = activeTtsChunk?.index
        }
    }

    fun temporarilyPauseAutoScroll(durationMillis: Long) {
        if (!autoScrollModeActive || !autoScroll) return
        val requestId = ++autoScrollPauseRequestId
        autoScrollTemporarilyPaused = true
        scope.launch {
            delay(durationMillis)
            if (requestId == autoScrollPauseRequestId && autoScrollModeActive && autoScroll) {
                autoScrollTemporarilyPaused = false
            }
        }
    }

    fun performMusicianGesture(plan: ReaderMusicianGesturePlan) {
        temporarilyPauseAutoScroll(plan.pauseMillis)
        if (useNativeVerticalRenderer && settings.readingMode == ReaderReadingMode.VERTICAL) {
            when (plan.target) {
                ReaderMusicianNavigationTarget.START -> scope.launch { nativeVerticalScrollController.scrollToStart() }
                ReaderMusicianNavigationTarget.END -> scope.launch { nativeVerticalScrollController.scrollToEnd() }
                ReaderMusicianNavigationTarget.RELATIVE -> scope.launch {
                    nativeVerticalScrollController.scrollByViewportFraction(plan.relativeViewportDelta)
                }
            }
        } else {
            commandScript = when (plan.target) {
                ReaderMusicianNavigationTarget.START ->
                    "window.scrollTo({ top: 0, behavior: 'auto' });"
                ReaderMusicianNavigationTarget.END ->
                    "window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'auto' });"
                ReaderMusicianNavigationTarget.RELATIVE ->
                    "window.scrollBy({ top: window.innerHeight * ${plan.relativeViewportDelta}, behavior: '${motionPolicy.webViewScrollBehavior()}' });"
            }
        }
        navigationRequestId++
    }

    fun navigate(
        locator: ReaderLocator,
        fragment: String? = null,
        detachFromTts: Boolean = true,
    ) {
        if (detachFromTts) detachVerticalReaderFromTts()
        val epub = loadedBook
        locator.chapterIndex?.let { chapterIndex ->
            epub?.chapters?.lastIndex?.let { lastIndex ->
                currentChapterIndex = chapterIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
            }
        }
        val targetChapterIndex = locator.chapterIndex?.coerceIn(0, epub?.chapters?.lastIndex ?: 0)
        val targetChunks = if (epub != null && targetChapterIndex != null) {
            ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, targetChapterIndex)
        } else {
            emptyList()
        }
        val targetChunkIndex = when {
            fragment != null -> targetChunks.indexOfFirst { it.containsReaderFragment(fragment) }.takeIf { it >= 0 }
            locator.startOffset != null && epub != null && targetChapterIndex != null && targetChunks.isNotEmpty() -> {
                val textLength = epub.chapters[targetChapterIndex].plainText.length.coerceAtLeast(1)
                ((locator.startOffset.toDouble() / textLength.toDouble()) * targetChunks.size)
                    .toInt()
                    .coerceIn(0, targetChunks.lastIndex)
            }
            else -> null
        }
        explicitNavigationLocator = locator
        explicitNavigationFragment = fragment
        explicitNavigationChunkIndex = targetChunkIndex
        explicitNavigationChunkHtml = targetChunkIndex?.let(targetChunks::getOrNull)
        commandScript = null
        navigationRequestId++
        currentLocator = locator
        locator.pageIndex?.let { currentPageIndex = it.coerceIn(0, pageCount - 1) }
        refreshSelectedTocIndex(locator, activeFragmentId = fragment)
    }

    fun publishCapturedEpubLocator(locator: ReaderLocator?) {
        if (locator == null) return
        currentLocator = locator
        locator.chapterIndex?.let { chapterIndex ->
            currentChapterIndex = chapterIndex.coerceIn(0, loadedBook?.chapters?.lastIndex ?: 0)
        }
        locator.pageIndex?.let { pageIndex ->
            currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
        }
        refreshSelectedTocIndex(locator)
    }

    /**
     * Captures the rendered origin before any explicit jump is recorded.
     * Native vertical rendering reads LazyListState.layoutInfo directly;
     * WebView rendering asks the DOM for its current visible locator and falls
     * back to the latest bridge observation while the page is unavailable.
     */
    fun captureCurrentEpubLocator(onCaptured: (ReaderLocator?) -> Unit) {
        val chapterCount = loadedBook?.chapters?.size ?: 0
        val nativeLocator = if (
            settings.readingMode == ReaderReadingMode.VERTICAL && useNativeVerticalRenderer
        ) {
            nativeVerticalScrollController.currentLocator()
        } else if (settings.readingMode == ReaderReadingMode.PAGINATED) {
            nativePaginatedPositionController.currentLocator()
        } else {
            null
        }
        if (nativeLocator != null) {
            val captured = captureReaderJumpHistoryOrigin(
                renderedCurrentLocator = nativeLocator,
                fallbackCurrentLocator = currentLocator,
                chapterCount = chapterCount,
            )
            publishCapturedEpubLocator(captured)
            onCaptured(captured)
            return
        }
        webViewPositionController.captureCurrentLocator { locator ->
            val captured = captureReaderJumpHistoryOrigin(
                renderedCurrentLocator = locator,
                fallbackCurrentLocator = currentLocator,
                chapterCount = chapterCount,
            )
            publishCapturedEpubLocator(captured)
            onCaptured(captured)
        }
    }

    fun recordJumpAndNavigate(locator: ReaderLocator, fragment: String? = null) {
        val chapterCount = loadedBook?.chapters?.size ?: return
        captureCurrentEpubLocator { current ->
            jumpHistory = jumpHistory.record(
                currentLocator = current,
                targetLocator = locator,
                chapterCount = chapterCount,
            )
            navigate(locator, fragment)
        }
    }

    fun showFootnoteIfAvailable(
        href: String,
        ownerHref: String?,
        sourceChapterIndex: Int? = null,
    ): Boolean {
        val note = loadedBook?.resolveMobileEpubFootnote(
            rawHref = href,
            ownerHref = ownerHref,
            sourceChapterIndex = sourceChapterIndex,
        ) ?: return false
        activeFootnote = note
        return true
    }

    fun navigateChapter(direction: Int) {
        val epub = loadedBook ?: return
        val targetChapterIndex = (currentChapterIndex + direction).coerceIn(0, epub.chapters.lastIndex)
        if (targetChapterIndex == currentChapterIndex) return
        val chapterPages = pages.filter { it.chapterIndex == targetChapterIndex }
        val targetPage = if (direction < 0) chapterPages.lastOrNull() else chapterPages.firstOrNull()
        val locator = targetPage?.toMobileEpubLocator(epub) ?: ReaderLocator(
            chapterIndex = targetChapterIndex,
            chapterId = epub.chapters[targetChapterIndex].id,
            href = epub.chapters[targetChapterIndex].baseHref,
            pageIndex = targetPage?.pageIndex,
            startOffset = targetPage?.startOffset ?: 0,
            endOffset = targetPage?.startOffset ?: 0,
            textQuote = targetPage?.text?.take(120)
        )
        currentChapterIndex = targetChapterIndex
        currentLocator = locator
        currentPageIndex = (targetPage?.pageIndex ?: currentPageIndex).coerceIn(0, pageCount - 1)
        explicitNavigationLocator = if (useNativeVerticalRenderer || direction >= 0) locator else null
        explicitNavigationFragment = null
        explicitNavigationChunkIndex = null
        explicitNavigationChunkHtml = null
        commandScript = if (useNativeVerticalRenderer) {
            null
        } else {
            when {
                direction < 0 -> {
                    val chunks = ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, targetChapterIndex)
                    sharedMobileEpubScrollToEndScript(chunks.lastIndex, chunks.lastOrNull())
                }
                autoScroll -> sharedMobileEpubAutoScrollStartScript(autoScrollProfile.speed)
                else -> null
            }
        }
        navigationRequestId++
        refreshSelectedTocIndex(locator)
    }

    fun navigatePage(direction: Int) {
        val epub = loadedBook ?: return
        if (pages.isEmpty()) return
        val targetPageIndex = when {
            direction < 0 && ReaderSpreadLayout.normalizePageIndex(currentPageIndex, pageCount, settings) > 0 -> ReaderSpreadLayout.previousPageIndex(
                currentPageIndex,
                pageCount,
                settings
            )
            direction > 0 && ReaderSpreadLayout.canGoNext(currentPageIndex, pageCount, settings) ->
                ReaderSpreadLayout.nextPageIndex(currentPageIndex, pageCount, settings)
            else -> return
        }
        pages.getOrNull(targetPageIndex)?.let { page ->
            navigate(page.toMobileEpubLocator(epub))
        }
    }

    LaunchedEffect(appLifecycleEventId, appIsActive) {
        when (
            readerLifecycleAction(
                isActive = appIsActive,
                isTtsActive = localTts.isSessionActive,
                detachedChunkIndex = detachedTtsChunkIndex,
                currentChunkIndex = activeTtsChunk?.index,
            )
        ) {
            ReaderLifecycleAction.SAVE_POSITION -> {
                // Android requests a final CFI on pause. Persist the latest
                // portable locator immediately instead of awaiting the debounce.
                currentReaderSnapshot()?.let(onReaderStateChange)
            }
            ReaderLifecycleAction.LOCATE_TTS -> {
                // Speech may advance while backgrounded. Restore the active
                // chunk unless the user intentionally detached from it.
                val chunk = activeTtsChunk ?: return@LaunchedEffect
                detachedTtsChunkIndex = null
                navigate(chunk.toLocator(), detachFromTts = false)
            }
            ReaderLifecycleAction.NONE -> Unit
        }
    }

    LaunchedEffect(activeTtsChunk?.index, activeTtsChunk?.chapterIndex, activeTtsChunk?.pageIndex) {
        val chunk = activeTtsChunk
        if (chunk == null || loadedBook == null) return@LaunchedEffect
        if (!shouldFollowReaderTtsChunk(detachedTtsChunkIndex, chunk.index)) {
            return@LaunchedEffect
        }
        detachedTtsChunkIndex = null
        // Speech chunks have source offsets and page indices from the same shared planner used
        // by Android. Let them own navigation while reading, so a spoken sentence is always
        // visible in either reader mode.
        navigate(chunk.toLocator(), detachFromTts = false)
    }

    LaunchedEffect(activeCloudTtsChunk?.index, activeCloudTtsChunk?.chapterIndex, activeCloudTtsChunk?.pageIndex) {
        val chunk = activeCloudTtsChunk
        if (chunk == null || loadedBook == null) return@LaunchedEffect
        if (!shouldFollowReaderTtsChunk(detachedTtsChunkIndex, chunk.index)) return@LaunchedEffect
        detachedTtsChunkIndex = null
        navigate(chunk.toLocator(), detachFromTts = false)
    }

    fun navigateSearchResult(result: SharedMobileEpubSearchResult) {
        val epub = loadedBook ?: return
        val chapter = epub.chapters.getOrNull(result.chapterIndex) ?: return
        detachVerticalReaderFromTts()
        captureCurrentEpubLocator { current ->
            jumpHistory = jumpHistory.record(
                currentLocator = current,
                targetLocator = result.locator,
                chapterCount = epub.chapters.size,
            )
            val chunks = ReaderHtmlDocumentBuilder.verticalChapterChunks(epub, result.chapterIndex)
            currentChapterIndex = result.chapterIndex
            currentLocator = result.locator
            result.locator.pageIndex?.let { currentPageIndex = it.coerceIn(0, pageCount - 1) }
            explicitNavigationLocator = result.locator.takeIf {
                settings.readingMode == ReaderReadingMode.PAGINATED || useNativeVerticalRenderer
            }
            explicitNavigationChunkIndex = result.chunkIndex
            explicitNavigationChunkHtml = chunks.getOrNull(result.chunkIndex)
            commandScript = if (settings.readingMode == ReaderReadingMode.VERTICAL && !useNativeVerticalRenderer) {
                sharedMobileEpubSearchNavigationScript(result, searchQuery, chunks.getOrNull(result.chunkIndex))
            } else {
                null
            }
            navigationRequestId++
            showSearchResultsPanel = false
        }
    }

    fun goBackInJumpHistory() {
        val chapterCount = loadedBook?.chapters?.size ?: return
        captureCurrentEpubLocator { current ->
            val refreshedHistory = jumpHistory.updateCurrentLocation(current, chapterCount)
            val target = refreshedHistory.backLocator ?: return@captureCurrentEpubLocator
            jumpHistory = refreshedHistory.stepBack()
            navigate(target)
        }
    }

    fun goForwardInJumpHistory() {
        val chapterCount = loadedBook?.chapters?.size ?: return
        captureCurrentEpubLocator { current ->
            val refreshedHistory = jumpHistory.updateCurrentLocation(current, chapterCount)
            val target = refreshedHistory.forwardLocator ?: return@captureCurrentEpubLocator
            jumpHistory = refreshedHistory.stepForward()
            navigate(target)
        }
    }

    fun speakSelectedText(text: String, locator: ReaderLocator) {
        val epub = loadedBook ?: return
        val chapterIndex = locator.chapterIndex ?: currentChapterIndex
        val startOffset = locator.startOffset ?: 0
        localTts.start(
            chunks = listOf(
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = locator.pageIndex ?: currentPageIndex,
                    chapterIndex = chapterIndex,
                    chapterTitle = epub.chapters.getOrNull(chapterIndex)?.title.orEmpty(),
                    text = text,
                    startOffset = startOffset,
                    endOffset = locator.endOffset ?: startOffset + text.length,
                    sourceCfi = locator.cfi
                )
            ).withTtsReplacements(readerTtsReplacementPreferences, book.id),
            bookTitle = epub.title,
            bookId = book.id,
        )
    }

    fun createNoteForSelection(text: String, locator: ReaderLocator) {
        val chapterIndex = locator.chapterIndex ?: currentChapterIndex
        val startOffset = locator.startOffset ?: 0
        val endOffset = locator.endOffset ?: (startOffset + text.length)
        val timestamp = currentTimestamp()
        val highlight = UserHighlight(
            id = "ios_epub_note_$timestamp",
            cfi = locator.cfi ?: "desktop:$chapterIndex:$startOffset:$endOffset",
            text = text,
            color = readerHighlightPalette.sanitized().colors.first(),
            chapterIndex = chapterIndex,
            locator = locator.withFallbacks(
                chapterIndex = chapterIndex,
                startOffset = startOffset,
                endOffset = endOffset,
                textQuote = text
            )
        )
        highlights = highlights.filterNot { it.cfi == highlight.cfi } + highlight
        editingHighlight = highlight
    }

    fun toggleBookmark() {
        val locator = currentLocator ?: return
        val existing = bookmarks.matchingReaderBookmark(locator, currentPageIndex)
        bookmarks = if (existing != null) {
            bookmarks.withoutMatchingReaderBookmarks(locator, currentPageIndex)
        } else {
            val chapterIndex = locator.chapterIndex?.coerceIn(0, loadedBook?.chapters?.lastIndex ?: 0) ?: 0
            bookmarks + ReaderBookmark(
                id = "ios_epub_bookmark_${currentTimestamp()}",
                pageIndex = currentPageIndex,
                chapterTitle = loadedBook?.chapters?.getOrNull(chapterIndex)?.title ?: "Chapter ${chapterIndex + 1}",
                preview = locator.textQuote.orEmpty().ifBlank { "Page ${currentPageIndex + 1}" },
                locator = locator
            )
        }
    }

    val isBookmarked = bookmarks.matchingReaderBookmark(currentLocator, currentPageIndex) != null

    pendingExternalLink?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingExternalLink = null },
            title = { Text("External Link") },
            text = { Text("You clicked on an external link:\n\n$url\n\nWhat would you like to do?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            openSharedMobileEpubExternalLink(url)
                            pendingExternalLink = null
                        }
                    ) {
                        Text("Open")
                    }
                    TextButton(
                        onClick = {
                            copyToClipboard(url, copiedLinkLabel)
                            pendingExternalLink = null
                        }
                    ) {
                        Text("Copy")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalLink = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    activeFootnote?.let { footnote ->
        SharedMobileEpubFootnoteSheet(
            footnote = footnote,
            settings = settings,
            onCopyText = { copyToClipboard(it) },
            onDismiss = { activeFootnote = null },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.86f)) {
                val drawerPagerState = rememberPagerState(pageCount = { 4 })
                val drawerScope = rememberCoroutineScope()
                LaunchedEffect(drawerTab) {
                    if (drawerTab in 0..3) {
                        if (motionPolicy.animationsEnabled) {
                            drawerPagerState.animateScrollToPage(drawerTab)
                        } else {
                            drawerPagerState.scrollToPage(drawerTab)
                        }
                    }
                }
                Text(
                    loadedBook?.title ?: book.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp)
                )
                ScrollableTabRow(selectedTabIndex = drawerPagerState.currentPage.coerceIn(0, 3), edgePadding = 0.dp, modifier = Modifier.fillMaxWidth()) {
                    listOf("Chapters", "Bookmarks", "Annotations", "Images").forEachIndexed { index, label ->
                        Tab(
                            selected = drawerPagerState.currentPage == index,
                            onClick = {
                                drawerScope.launch {
                                    if (motionPolicy.animationsEnabled) {
                                        drawerPagerState.animateScrollToPage(index)
                                    } else {
                                        drawerPagerState.scrollToPage(index)
                                    }
                                }
                            },
                            text = { Text(label, maxLines = 1) }
                        )
                    }
                }
                HorizontalPager(state = drawerPagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                    when (page) {
                        0 -> SharedMobileEpubToc(
                            epub = loadedBook,
                            selectedIndex = selectedTocIndex,
                            onEntryClick = { index, entry ->
                                selectedTocIndex = index
                                loadedBook?.locatorForTocEntry(entry, pages)?.let { locator ->
                                    recordJumpAndNavigate(locator, entry.fragmentId)
                                    scope.launch { drawerState.close() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> SharedMobileEpubBookmarks(
                            bookmarks = bookmarks,
                            onBookmarkClick = { bookmark ->
                                recordJumpAndNavigate(bookmark.locator)
                                scope.launch { drawerState.close() }
                            },
                            onBookmarkRename = { bookmark, label ->
                                bookmarks = bookmarks.map { existing ->
                                    if (existing.id == bookmark.id) existing.copy(label = label.trim().ifBlank { null }) else existing
                                }
                            },
                            onBookmarkDelete = { bookmark -> bookmarks = bookmarks.filterNot { it.id == bookmark.id } },
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> SharedMobileEpubHighlights(
                            highlights = highlights,
                            chapters = loadedBook?.chapters.orEmpty(),
                            palette = readerHighlightPalette,
                            onHighlightClick = { highlight ->
                                recordJumpAndNavigate(highlight.locator)
                                scope.launch { drawerState.close() }
                            },
                            onHighlightEdit = { editingHighlight = it },
                            onHighlightColorChange = { highlight, color ->
                                highlights = highlights.map { current ->
                                    if (current.id == highlight.id) {
                                        current.copy(color = color, colorArgb = null)
                                    } else {
                                        current
                                    }
                                }
                            },
                            onDeleteHighlight = { highlight ->
                                highlights = highlights.filterNot { it.id == highlight.id }
                                if (editingHighlight?.id == highlight.id) editingHighlight = null
                            },
                            onOpenPaletteManager = { showHighlightPaletteManager = true },
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> SharedMobileEpubImages(
                            images = if (settings.hideImages) {
                                emptyList()
                            } else {
                                loadedBook?.readerImageReferences(pages).orEmpty()
                            },
                            onImageClick = { image ->
                                loadedBook?.chapters?.getOrNull(image.chapterIndex)?.let {
                                    recordJumpAndNavigate(image.locator)
                                    scope.launch { drawerState.close() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize().background(settings.readerBackgroundColor())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        readerViewport = ReaderViewportSpec(size.width, size.height)
                    }
                    .then(
                        if (!systemUiHidden) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        }
                    )
            ) {
                when {
                    loadState.isLoading -> SharedMobileEpubLoading("Opening EPUB…")
                    loadState.errorMessage != null -> SharedMobileEpubError(loadState.errorMessage)
                    loadedBook != null && pages.isEmpty() -> SharedMobileEpubLoading("Preparing book layout…")
                    loadedBook != null -> {
                        if (settings.readingMode == ReaderReadingMode.PAGINATED) {
                            val visiblePages = ReaderSpreadLayout.visiblePageIndicesForDisplay(
                                currentPageIndex,
                                pages.size,
                                settings
                            ).mapNotNull(pages::getOrNull)
                            val paginatedRenderPlan = ReaderContentRenderPlan.NativePaginatedPages(
                                visiblePages = visiblePages,
                                settings = settings,
                                searchQuery = searchQuery,
                                searchOptions = ReaderSearchOptions(),
                                highlightPalette = readerHighlightPalette,
                                background = settings.readerBackgroundColor(),
                                foreground = settings.readerTextColor(),
                                navigationTarget = ReaderContentNavigationTarget(
                                    locator = explicitNavigationLocator ?: currentLocator,
                                    requestId = navigationRequestId,
                                    readingMode = settings.readingMode
                                ),
                                highlights = activeTtsChunk?.let { chunk ->
                                    highlights + chunk.toHighlight(localTts.progress.sessionId)
                                } ?: highlights
                            )
                            // Android-benchmark page turn: single visible-step turns play the realistic
                            // page curl with the same tween(700) the Android pager snap uses; multi-page
                            // jumps (slider, TOC, links, TTS) settle instantly like Android's scrollToPage.
                            var lastTurnedPages by remember(book.id) { mutableStateOf(visiblePages) }
                            var activePageTurn by remember(book.id) { mutableStateOf<SharedMobileEpubActivePageTurn?>(null) }
                            var pageTurnTouchY by remember(book.id) { mutableStateOf<Float?>(null) }
                            val pageTurnFraction = remember(book.id) { Animatable(1f) }

                            LaunchedEffect(visiblePages) {
                                val outgoingPages = lastTurnedPages
                                if (outgoingPages == visiblePages || outgoingPages.isEmpty() || visiblePages.isEmpty()) {
                                    lastTurnedPages = visiblePages
                                    activePageTurn = null
                                    return@LaunchedEffect
                                }
                                val animateTurn = sharedPaginatedTurnShouldAnimate(
                                    animationEnabled = motionPolicy.shouldAnimate(settings.pageTurnAnimationEnabled),
                                    outgoingFirstPageIndex = outgoingPages.minOf { it.pageIndex },
                                    incomingFirstPageIndex = visiblePages.minOf { it.pageIndex },
                                    visiblePageCount = visiblePages.size
                                )
                                if (!animateTurn) {
                                    lastTurnedPages = visiblePages
                                    activePageTurn = null
                                    return@LaunchedEffect
                                }
                                val direction = sharedPaginatedTransitionDirection(
                                    outgoingPages.minOf { it.pageIndex },
                                    visiblePages.minOf { it.pageIndex },
                                    settings.rightToLeftPagination
                                )
                                lastTurnedPages = visiblePages
                                activePageTurn = SharedMobileEpubActivePageTurn(
                                    outgoingPages = outgoingPages,
                                    direction = direction,
                                    touchY = pageTurnTouchY
                                )
                                pageTurnFraction.snapTo(0f)
                                try {
                                    pageTurnFraction.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                                } finally {
                                    activePageTurn = null
                                }
                            }

                            // Android-benchmark drag-to-page: the position follows the finger 1:1
                            // with the curl rendering live, then settles with the pager snap spring.
                            var pageDragActive by remember(book.id) { mutableStateOf(false) }
                            var pageDragDirection by remember(book.id) { mutableIntStateOf(0) }
                            val pageDragPosition = remember(book.id) { mutableFloatStateOf(0f) }
                            var pageDragSettleJob by remember(book.id) { mutableStateOf<Job?>(null) }

                            val canDragForward = ReaderSpreadLayout.canGoNext(currentPageIndex, pageCount, settings)
                            val canDragBackward = ReaderSpreadLayout.normalizePageIndex(currentPageIndex, pageCount, settings) > 0
                            val forwardDragPages = if (canDragForward) {
                                ReaderSpreadLayout.visiblePageIndicesForDisplay(
                                    ReaderSpreadLayout.nextPageIndex(currentPageIndex, pageCount, settings),
                                    pages.size,
                                    settings
                                ).mapNotNull(pages::getOrNull)
                            } else {
                                emptyList()
                            }
                            val backwardDragPages = if (canDragBackward) {
                                ReaderSpreadLayout.visiblePageIndicesForDisplay(
                                    ReaderSpreadLayout.previousPageIndex(currentPageIndex, pageCount, settings),
                                    pages.size,
                                    settings
                                ).mapNotNull(pages::getOrNull)
                            } else {
                                emptyList()
                            }

                            fun settlePageDrag(targetPositionPages: Float, onCompleted: () -> Unit) {
                                pageDragSettleJob?.cancel()
                                if (!motionPolicy.animationsEnabled) {
                                    pageDragPosition.floatValue = targetPositionPages
                                    onCompleted()
                                    return
                                }
                                pageDragSettleJob = scope.launch {
                                    // Android settles drags with the pager snap spring.
                                    val animation = Animatable(pageDragPosition.floatValue)
                                    val result = animation.animateTo(
                                        targetPositionPages,
                                        spring(stiffness = Spring.StiffnessMediumLow)
                                    ) { pageDragPosition.floatValue = value }
                                    if (result.endReason == AnimationEndReason.Finished) {
                                        pageDragSettleJob = null
                                        onCompleted()
                                    }
                                }
                            }

                            val pageDragController = SharedPaginatedPageDragController(
                                isEnabled = { activePageTurn == null && pages.isNotEmpty() },
                                onDragStarted = { touchY ->
                                    val caughtSettle = pageDragSettleJob != null
                                    pageDragSettleJob?.cancel()
                                    pageDragSettleJob = null
                                    pageTurnTouchY = touchY
                                    pageDragActive = true
                                    if (caughtSettle) {
                                        // Android lets the finger catch a settling page mid-flight.
                                        pageDragDirection = when {
                                            pageDragPosition.floatValue > 0f -> 1
                                            pageDragPosition.floatValue < 0f -> -1
                                            else -> 0
                                        }
                                    } else {
                                        pageDragPosition.floatValue = 0f
                                        pageDragDirection = 0
                                    }
                                },
                                onDrag = { rawDragFraction ->
                                    val position = sharedPaginatedDragPositionPages(
                                        rawDragFraction = rawDragFraction,
                                        visiblePageCount = visiblePages.size,
                                        rightToLeftPagination = settings.rightToLeftPagination
                                    )
                                    val clamped = when {
                                        position > 0f && forwardDragPages.isEmpty() -> 0f
                                        position < 0f && backwardDragPages.isEmpty() -> 0f
                                        else -> position
                                    }
                                    pageDragPosition.floatValue = clamped
                                    pageDragDirection = when {
                                        clamped > 0f -> 1
                                        clamped < 0f -> -1
                                        else -> 0
                                    }
                                },
                                onDragReleased = { rawVelocityFraction ->
                                    val dragSign = if (settings.rightToLeftPagination) 1 else -1
                                    val velocityPages = rawVelocityFraction * dragSign * visiblePages.size
                                    when (
                                        sharedPaginatedDragReleaseTarget(
                                            positionPages = pageDragPosition.floatValue,
                                            velocityPagesPerSecond = velocityPages,
                                            visiblePageCount = visiblePages.size,
                                            canDragForward = canDragForward,
                                            canDragBackward = canDragBackward
                                        )
                                    ) {
                                        SharedPaginatedDragRelease.COMMIT_FORWARD -> settlePageDrag(
                                            targetPositionPages = visiblePages.size.toFloat(),
                                            onCompleted = {
                                                lastTurnedPages = forwardDragPages
                                                pageDragActive = false
                                                pageDragDirection = 0
                                                pageDragPosition.floatValue = 0f
                                                // navigate() commits currentPageIndex synchronously,
                                                // so the drag layers and the settled page swap in one frame.
                                                navigatePage(1)
                                            }
                                        )
                                        SharedPaginatedDragRelease.COMMIT_BACKWARD -> settlePageDrag(
                                            targetPositionPages = -visiblePages.size.toFloat(),
                                            onCompleted = {
                                                lastTurnedPages = backwardDragPages
                                                pageDragActive = false
                                                pageDragDirection = 0
                                                pageDragPosition.floatValue = 0f
                                                navigatePage(-1)
                                            }
                                        )
                                        SharedPaginatedDragRelease.CANCEL -> settlePageDrag(
                                            targetPositionPages = 0f,
                                            onCompleted = {
                                                pageDragActive = false
                                                pageDragDirection = 0
                                            }
                                        )
                                    }
                                },
                                onDragCancelled = {
                                    settlePageDrag(
                                        targetPositionPages = 0f,
                                        onCompleted = {
                                            pageDragActive = false
                                            pageDragDirection = 0
                                        }
                                    )
                                }
                            )

                            val activeTurn = activePageTurn
                            val incomingTurnSpec = activeTurn?.let { turn ->
                                SharedPaginatedPageTurnSpec(
                                    offsetForSlot = { slot ->
                                        sharedPaginatedTurnPageOffset(
                                            slotOffsetInSet = slot,
                                            setLeadSlots = visiblePages.size,
                                            turnDistanceSlots = visiblePages.size,
                                            direction = turn.direction,
                                            fraction = pageTurnFraction.value
                                        )
                                    },
                                    touchY = turn.touchY
                                )
                            }
                            val outgoingTurnSpec = activeTurn?.let { turn ->
                                SharedPaginatedPageTurnSpec(
                                    offsetForSlot = { slot ->
                                        sharedPaginatedTurnPageOffset(
                                            slotOffsetInSet = slot,
                                            setLeadSlots = 0,
                                            turnDistanceSlots = visiblePages.size,
                                            direction = turn.direction,
                                            fraction = pageTurnFraction.value
                                        )
                                    },
                                    touchY = turn.touchY
                                )
                            }
                            val dragCurrentSpec = if (pageDragActive) {
                                SharedPaginatedPageTurnSpec(
                                    offsetForSlot = { slot ->
                                        sharedPaginatedTurnPageOffset(
                                            slotOffsetInSet = slot,
                                            setLeadSlots = 0,
                                            turnDistanceSlots = visiblePages.size,
                                            direction = pageDragDirection,
                                            fraction = abs(pageDragPosition.floatValue) / visiblePages.size.coerceAtLeast(1)
                                        )
                                    },
                                    touchY = pageTurnTouchY
                                )
                            } else {
                                null
                            }
                            val dragNeighborSpec = if (pageDragActive) {
                                SharedPaginatedPageTurnSpec(
                                    offsetForSlot = { slot ->
                                        sharedPaginatedTurnPageOffset(
                                            slotOffsetInSet = slot,
                                            setLeadSlots = visiblePages.size,
                                            turnDistanceSlots = visiblePages.size,
                                            direction = pageDragDirection,
                                            fraction = abs(pageDragPosition.floatValue) / visiblePages.size.coerceAtLeast(1)
                                        )
                                    },
                                    touchY = pageTurnTouchY
                                )
                            } else {
                                null
                            }
                            val dragNeighborPages = when {
                                !pageDragActive || pageDragDirection == 0 -> null
                                pageDragDirection > 0 -> forwardDragPages
                                else -> backwardDragPages
                            }
                            val dragOverlayPlan = dragNeighborPages
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { paginatedRenderPlan.copy(visiblePages = it) }

                            // Draw order mirrors the pager z-order: a forward turn or forward drag
                            // keeps the incoming set beneath the curling set, while a backward turn
                            // or drag un-curls the incoming set on top.
                            val overlayFirst = if (pageDragActive) {
                                pageDragDirection > 0
                            } else {
                                (activeTurn?.direction ?: 0) < 0
                            }
                            val turnOverlay: @Composable () -> Unit = {
                                val overlayPlan = when {
                                    dragOverlayPlan != null -> dragOverlayPlan
                                    activeTurn != null -> paginatedRenderPlan.copy(visiblePages = activeTurn.outgoingPages)
                                    else -> null
                                }
                                val overlaySpec = dragNeighborSpec ?: outgoingTurnSpec
                                if (overlayPlan != null && overlaySpec != null) {
                                    SharedNativePaginatedPageTurnOverlay(
                                        renderPlan = overlayPlan,
                                        readerFontFamily = settings.toSharedReaderFontFamily(),
                                        searchHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        pageTurn = overlaySpec
                                    )
                                }
                            }
                            Box(Modifier.fillMaxSize()) {
                                if (overlayFirst) {
                                    turnOverlay()
                                }
                                SharedNativePaginatedReader(
                                    renderPlan = paginatedRenderPlan,
                                    readerFontFamily = settings.toSharedReaderFontFamily(),
                                    searchHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                    onVisiblePageChanged = { pageIndex, locator ->
                                        currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
                                        val page = pages.getOrNull(currentPageIndex)
                                        currentChapterIndex = page?.chapterIndex ?: currentChapterIndex
                                        currentLocator = locator ?: page?.toMobileEpubLocator(loadedBook) ?: currentLocator
                                        refreshSelectedTocIndex()
                                    },
                                    onHighlightCreated = { highlight ->
                                        highlights = highlights.filterNot { it.id == highlight.id } + highlight
                                    },
                                    onHighlightSelected = { id ->
                                        editingHighlight = highlights.firstOrNull { it.id == id }
                                    },
                                    enabledSelectionActions = SharedNativeReaderSelectionAction.entries.toSet(),
                                    onCopyText = { text -> copyToClipboard(text) },
                                    onSelectionAction = { action, text, locator ->
                                        val selectionLocator = locator ?: currentLocator ?: return@SharedNativePaginatedReader
                                        val lookupAction = action.externalLookupActionOrNull()
                                        when {
                                            action == SharedNativeReaderSelectionAction.DEFINE && readerAiAvailable -> onAiAction(ReaderAiFeature.DEFINE, text)
                                            lookupAction != null -> openSharedMobileEpubLookup(lookupAction, text)
                                            action == SharedNativeReaderSelectionAction.SPEAK -> speakSelectedText(text, selectionLocator)
                                            action == SharedNativeReaderSelectionAction.NOTE -> createNoteForSelection(text, selectionLocator)
                                            else -> Unit
                                        }
                                    },
                                    onLinkClicked = { link ->
                                        val sourceChapter = link.chapterIndex ?: currentChapterIndex
                                        val sourceHref = loadedBook.chapters.getOrNull(sourceChapter)?.baseHref
                                        if (showFootnoteIfAvailable(link.href, sourceHref, sourceChapter)) {
                                            Unit
                                        } else if (link.href.isExternalEpubLink()) {
                                            pendingExternalLink = link.href
                                        } else {
                                            loadedBook.locatorForLink(link.href, sourceHref, pages)?.let { (locator, fragment) ->
                                                recordJumpAndNavigate(locator, fragment)
                                            }
                                        }
                                    },
                                    onReaderTap = {
                                        if (!(autoScrollMusicianMode && autoScrollModeActive)) showChrome = !showChrome
                                    },
                                    onReaderHorizontalTap = { horizontalFraction, touchY ->
                                        if (!pageDragActive) {
                                            pageTurnTouchY = touchY
                                            when (sharedPaginatedTapAction(horizontalFraction, settings.tapToNavigateEnabled, settings.rightToLeftPagination)) {
                                                SharedPaginatedTapAction.PREVIOUS_PAGE -> navigatePage(-1)
                                                SharedPaginatedTapAction.NEXT_PAGE -> navigatePage(1)
                                                SharedPaginatedTapAction.TOGGLE_CHROME -> showChrome = !showChrome
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    positionController = nativePaginatedPositionController,
                                    pageTurn = if (pageDragActive) dragCurrentSpec else incomingTurnSpec,
                                    pageDragController = pageDragController
                                )
                                if (!overlayFirst) {
                                    turnOverlay()
                                }
                            }
                        } else if (useNativeVerticalRenderer) {
                        LaunchedEffect(
                            autoScroll,
                            autoScrollTemporarilyPaused,
                            autoScrollPauseRequestId,
                            autoScrollProfile.speed,
                            nativeVerticalScrollController
                        ) {
                            if (!autoScroll || autoScrollTemporarilyPaused) return@LaunchedEffect
                            var previousFrame = withFrameNanos { it }
                            while (currentCoroutineContext().isActive) {
                                val frame = withFrameNanos { it }
                                val deltaSeconds = (frame - previousFrame) / 1_000_000_000f
                                previousFrame = frame
                                if (deltaSeconds <= 0f || deltaSeconds > 0.1f) continue
                                nativeVerticalScrollController.scrollByPixels(
                                    readerAutoScrollPixelsPerSecond(autoScrollProfile.speed) * deltaSeconds
                                )
                                if (!nativeVerticalScrollController.canScrollForward()) {
                                    autoScroll = false
                                    autoScrollTemporarilyPaused = false
                                    autoScrollPauseRequestId++
                                    break
                                }
                            }
                        }
                        SharedNativeVerticalReader(
                            renderPlan = ReaderContentRenderPlan.NativeVerticalPages(
                                book = loadedBook,
                                pages = pages,
                                currentPageIndex = currentPageIndex,
                                settings = settings,
                                searchQuery = searchQuery,
                                searchOptions = ReaderSearchOptions(),
                                highlightPalette = readerHighlightPalette,
                                background = settings.readerBackgroundColor(),
                                foreground = settings.readerTextColor(),
                                navigationTarget = ReaderContentNavigationTarget(
                                    locator = explicitNavigationLocator ?: currentLocator,
                                    requestId = navigationRequestId,
                                    readingMode = settings.readingMode
                                ),
                                highlights = activeTtsChunk?.let { chunk ->
                                    highlights + chunk.toHighlight(localTts.progress.sessionId)
                                } ?: highlights
                            ),
                            readerFontFamily = settings.toSharedReaderFontFamily(),
                            searchHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            onVisiblePageChanged = { pageIndex, locator ->
                                currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
                                val page = pages.getOrNull(currentPageIndex)
                                currentChapterIndex = page?.chapterIndex ?: currentChapterIndex
                                currentLocator = locator ?: page?.toMobileEpubLocator(loadedBook) ?: currentLocator
                                refreshSelectedTocIndex()
                            },
                            onHighlightCreated = { highlight ->
                                highlights = highlights.filterNot { it.id == highlight.id } + highlight
                            },
                            onHighlightSelected = { id ->
                                editingHighlight = highlights.firstOrNull { it.id == id }
                            },
                                enabledSelectionActions = SharedNativeReaderSelectionAction.entries.toSet(),
                            onCopyText = { text -> copyToClipboard(text) },
                            onSelectionAction = { action, text, locator ->
                                val selectionLocator = locator ?: currentLocator ?: return@SharedNativeVerticalReader
                                val lookupAction = action.externalLookupActionOrNull()
                                when {
                                        action == SharedNativeReaderSelectionAction.DEFINE && readerAiAvailable -> onAiAction(ReaderAiFeature.DEFINE, text)
                                        lookupAction != null -> openSharedMobileEpubLookup(lookupAction, text)
                                    action == SharedNativeReaderSelectionAction.SPEAK -> speakSelectedText(text, selectionLocator)
                                    action == SharedNativeReaderSelectionAction.NOTE -> createNoteForSelection(text, selectionLocator)
                                    else -> Unit
                                }
                            },
                            onLinkClicked = { link ->
                                val sourceChapter = link.chapterIndex ?: currentChapterIndex
                                val sourceHref = loadedBook.chapters.getOrNull(sourceChapter)?.baseHref
                                if (showFootnoteIfAvailable(link.href, sourceHref, sourceChapter)) {
                                    Unit
                                } else if (link.href.isExternalEpubLink()) {
                                    pendingExternalLink = link.href
                                } else {
                                    loadedBook.locatorForLink(link.href, sourceHref, pages)?.let { (locator, fragment) ->
                                        recordJumpAndNavigate(locator, fragment)
                                    }
                                }
                            },
                            onReaderTap = {
                                if (!(autoScrollMusicianMode && autoScrollModeActive)) showChrome = !showChrome
                            },
                            imageContent = { image, imageModifier ->
                                if (!settings.hideImages) {
                                    SharedMobileEpubNativeImage(
                                        image = image,
                                        modifier = imageModifier
                                    )
                                }
                            },
                            verticalScrollController = nativeVerticalScrollController,
                            modifier = Modifier.fillMaxSize()
                        )
                        } else {
                        val chapterChunks = remember(loadedBook.id, currentChapterIndex) {
                            val chunksMark = sharedEpubOpenTraceMark()
                            val chunks = ReaderHtmlDocumentBuilder.verticalChapterChunks(loadedBook, currentChapterIndex)
                            sharedEpubOpenTrace {
                                "readerScreen chapterChunks chapterIndex=$currentChapterIndex count=${chunks.size} " +
                                    "chars=${chunks.sumOf { it.length }} ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(chunksMark))}"
                            }
                            chunks
                        }
                        val initialVirtualChunkIndex = remember(loadedBook.id, currentChapterIndex) {
                            val textLength = loadedBook.chapters[currentChapterIndex].plainText.length.coerceAtLeast(1)
                            val offset = currentLocator?.takeIf { it.chapterIndex == currentChapterIndex }?.startOffset ?: 0
                            if (chapterChunks.isEmpty()) 0 else {
                                ((offset.toDouble() / textLength.toDouble()) * chapterChunks.size)
                                    .toInt()
                                    .coerceIn(0, chapterChunks.lastIndex)
                            }
                        }
                        val navigationChunkIndex = explicitNavigationChunkIndex ?: initialVirtualChunkIndex
                        val navigationChunkHtml = explicitNavigationChunkHtml ?: chapterChunks.getOrNull(navigationChunkIndex)
                        // A persisted WebView highlight needs the complete chapter DOM so its
                        // offsets remain stable after reopening the reader. Keep virtualization
                        // for ordinary chapters, where it protects large EPUBs from WebView
                        // memory spikes.
                        val currentChapterHasHighlights = highlights.any { highlight ->
                            (highlight.locator.chapterIndex ?: highlight.chapterIndex) == currentChapterIndex
                        }
                        val initialHtml = remember(
                            loadedBook.id,
                            currentChapterIndex,
                            chapterChunks,
                            highlights,
                            currentChapterHasHighlights
                        ) {
                            val htmlMark = sharedEpubOpenTraceMark()
                            val html = ReaderHtmlDocumentBuilder.verticalDocument(
                                book = loadedBook,
                                settings = settings,
                                highlights = highlights,
                                highlightPalette = readerHighlightPalette,
                                navigationLocator = currentLocator,
                                pages = pages,
                                highlightActionsEnabled = true,
                                readerAiFeaturesEnabled = readerAiAvailable,
                                // This flag controls the selection-menu Speak action. iOS handles
                                // it with local device speech rather than the paid cloud service.
                                cloudTtsEnabled = true,
                                externalLookupEnabled = true,
                                renderedChapterRange = currentChapterIndex..currentChapterIndex,
                                virtualizedChapterChunks = if (currentChapterHasHighlights) {
                                    emptyMap()
                                } else {
                                    mapOf(currentChapterIndex to chapterChunks)
                                },
                                virtualizedInitialChunkIndex = initialVirtualChunkIndex,
                                showChapterTitles = false
                            )
                            sharedEpubOpenTrace {
                                "readerScreen verticalDocument chapterIndex=$currentChapterIndex chars=${html.length} " +
                                    "ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(htmlMark))}"
                            }
                            html
                        }
                        val appearanceScript = remember(settings, pages, currentChapterIndex, loadedBook.id) {
                            ReaderHtmlDocumentBuilder.appearanceUpdateScript(settings) + "\n" +
                                ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(pages) + "\n" +
                                sharedMobileEpubActiveTocScript(loadedBook, currentChapterIndex) + "\n" +
                                "window.readerIosPullEnabled=${settings.pullToTurnEnabled};" +
                                "window.readerIosSeamlessChapter=${settings.seamlessChapterTransitionEnabled};" +
                                "window.readerIosPullMultiplier=${settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2f)};"
                        }
                        val navigationScript = buildList {
                            commandScript?.let(::add)
                            (explicitNavigationLocator ?: currentLocator)?.let { locator ->
                                add(
                                    sharedMobileEpubNavigationScript(
                                        locator = locator,
                                        fragment = explicitNavigationFragment,
                                        targetChunkIndex = navigationChunkIndex,
                                        targetChunkHtml = navigationChunkHtml
                                    )
                                )
                            }
                            add(sharedMobileEpubTtsNavigationScript(activeTtsChunk?.toLocator()))
                        }.joinToString(separator = "\n")
                        SharedMobileEpubWebView(
                            html = initialHtml,
                            contentChunks = chapterChunks,
                            appearanceScript = appearanceScript,
                            navigationScript = navigationScript,
                            navigationRequestId = navigationRequestId,
                            positionController = webViewPositionController,
                            streamPageLoader = streamPageLoader,
                            streamPageUnavailableLabel = streamPageUnavailableLabel,
                            onBridgeMessage = { method, payload ->
                                when (method) {
                                    "readerPointerActivity" -> {
                                        if (!(autoScrollMusicianMode && autoScrollModeActive)) showChrome = !showChrome
                                    }
                                    "readerDragActivity" -> temporarilyPauseAutoScroll(300L)
                                    "readerPositionChanged" -> payload.sharedMobileEpubLocatorOrNull()?.let { position ->
                                        webViewPositionController.updateObservedLocator(position)
                                        val reportedChapter = position.chapterIndex
                                        if (reportedChapter == null || reportedChapter == currentChapterIndex) {
                                            currentLocator = position
                                            currentPageIndex = (position.pageIndex ?: currentPageIndex).coerceIn(0, pageCount - 1)
                                            position.chapterIndex?.let { currentChapterIndex = it }
                                            refreshSelectedTocIndex(position)
                                            commandScript = null
                                        }
                                    }
                                    "readerChapterBoundary" -> when (payload.sharedMobileEpubDirectionOrNull()) {
                                        "previous" -> navigateChapter(-1)
                                        "next" -> navigateChapter(1)
                                    }
                                    "readerAutoScrollChapterEnd" -> {
                                        when (readerAutoScrollBoundaryAction(currentChapterIndex, loadedBook.chapters.size)) {
                                            ReaderAutoScrollBoundaryAction.NEXT_CHAPTER -> {
                                                navigateChapter(1)
                                                temporarilyPauseAutoScroll(1_000L)
                                            }
                                            ReaderAutoScrollBoundaryAction.STOP -> {
                                                autoScroll = false
                                                autoScrollTemporarilyPaused = false
                                                autoScrollPauseRequestId++
                                            }
                                        }
                                    }
                                    "readerChapterPull" -> payload.sharedMobileEpubPullOrNull()?.let { pull ->
                                        pullDirection = pull.first
                                        pullProgress = pull.second
                                    }
                                    "readerActiveTocChanged" -> payload.sharedMobileEpubActiveTocOrNull()?.let { active ->
                                        refreshSelectedTocIndex(
                                            locator = currentLocator,
                                            activeHref = active.href,
                                            activeFragmentId = active.fragmentId
                                        )
                                    }
                                    "readerLinkClicked" -> payload.sharedMobileEpubLinkOrNull()?.let { link ->
                                        if (showFootnoteIfAvailable(link.href, link.chapterHref)) {
                                            Unit
                                        } else if (link.href.isExternalEpubLink()) {
                                            pendingExternalLink = link.href
                                        } else {
                                            loadedBook.locatorForLink(link.href, link.chapterHref, pages)?.let { (locator, fragment) ->
                                                recordJumpAndNavigate(locator, fragment)
                                            }
                                        }
                                    }
                                    "readerHighlightCreated" -> payload.sharedMobileEpubHighlightOrNull()?.let { highlight ->
                                        highlights = highlights
                                            .filterNot { existing -> existing.cfi == highlight.cfi }
                                            .plus(highlight)
                                    }
                                    "readerHighlightClicked" -> payload.sharedMobileEpubHighlightIdOrNull()?.let { id ->
                                        editingHighlight = highlights.firstOrNull { it.id == id }
                                    }
                                    "readerSelectionAction" -> payload.sharedMobileEpubSelectionActionOrNull()?.let { selection ->
                                        val lookupAction = readerExternalLookupActionForSelectionId(selection.action)
                                        when {
                                            selection.action == "define" && readerAiAvailable -> onAiAction(ReaderAiFeature.DEFINE, selection.text)
                                            lookupAction != null -> openSharedMobileEpubLookup(lookupAction, selection.text)
                                            selection.action == "speak" -> {
                                                val locator = selection.locator ?: currentLocator ?: return@let
                                                speakSelectedText(selection.text, locator)
                                            }
                                            selection.action == "note" -> {
                                                val locator = selection.locator ?: return@let
                                                createNoteForSelection(selection.text, locator)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }
                }
            }

                val chapterTitle = loadedBook?.effectiveReaderTocEntries()?.getOrNull(selectedTocIndex)?.label
                    ?: loadedBook?.chapters?.getOrNull(currentChapterIndex)?.title
                    ?: "Chapter ${currentChapterIndex + 1}"
                val pageInfoVisible = shouldShowEpubPageInfoBar(
                    pageInfoMode = settings.pageInfoMode,
                    showReaderChrome = showChrome
                ) && loadedBook != null && pages.isNotEmpty()

                if (pageInfoVisible) {
                    SharedMobileEpubPageInfo(
                        chapterTitle = chapterTitle,
                        pageInfo = pageInfo,
                        progressPercent = progress,
                        settings = settings,
                        modifier = Modifier.align(if (settings.pageInfoPosition == PageInfoPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                            .then(
                                if (
                                    settings.pageInfoPosition == PageInfoPosition.TOP && !systemUiHidden ||
                                    settings.pageInfoPosition == PageInfoPosition.BOTTOM && !navigationUiHidden
                                ) {
                                    Modifier.windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            if (settings.pageInfoPosition == PageInfoPosition.TOP) {
                                                WindowInsetsSides.Top
                                            } else {
                                                WindowInsetsSides.Bottom
                                            }
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .offset(y = if (settings.pageInfoPosition == PageInfoPosition.TOP && showChrome) 55.dp else if (settings.pageInfoPosition == PageInfoPosition.BOTTOM && showChrome) (-45).dp else 0.dp)
                    )
                }
                if (showChrome) {
                    SharedMobileEpubTopBar(
                        title = loadedBook?.title ?: book.displayName,
                        isBookmarked = isBookmarked,
                        topTools = topToolbarTools,
                        overflowTools = overflowMenuTools,
                        showMore = showMore,
                        onShowMoreChange = { showMore = it },
                        onBack = ::closeReader,
                        onTheme = { showThemeSheet = true },
                        onFormat = { showFormatSheet = true },
                        onSearch = { showSearchResultsPanel = true; showSearch = true },
                        onBookmark = ::toggleBookmark,
                        onVisualOptions = { showVisualOptionsSheet = true },
                        onBrightness = { showBrightnessSheet = true },
                        onOpenToc = { openReaderDrawer(0) },
                        onOpenSlider = { showSlider = !showSlider },
                        onFileInfo = { showFileInfo = true },
                        onCustomizeTools = { showCustomizeToolsSheet = true },
                        onScreenOrientation = { showScreenOrientationSheet = true },
                        onTtsReplacements = { showTtsReplacementsSheet = true },
                        onTtsSettings = { showTtsSettingsSheet = true },
                        onBookReplacements = { showBookReplacementsSheet = true },
                        onOpenDictionarySettings = onOpenDictionarySettings,
                        onOpenAiHub = { showAiHub = true; onOpenAiHub() },
                        aiAvailable = readerAiAvailable,
                        readingMode = settings.readingMode,
                        rightToLeftPagination = settings.rightToLeftPagination,
                        useNativeVerticalRenderer = useNativeVerticalRenderer,
                        tapToNavigateEnabled = settings.tapToNavigateEnabled,
                        pageTurnAnimationEnabled = settings.pageTurnAnimationEnabled,
                        onReadingModeChange = { mode ->
                            if (settings.readingMode != mode) {
                                settings = settings.copy(readingMode = mode)
                                showSlider = false
                                autoScrollModeActive = false
                                autoScroll = false
                            }
                        },
                        onUseNativeVerticalRendererChange = { native ->
                            if (useNativeVerticalRenderer != native) {
                                useNativeVerticalRenderer = native
                                onUseNativeVerticalRendererPreferenceChange(native)
                            }
                        },
                        onRightToLeftPaginationChange = { settings = settings.copy(rightToLeftPagination = it) },
                        onTapToNavigateChange = { settings = settings.copy(tapToNavigateEnabled = it) },
                        onPageTurnAnimationChange = { settings = settings.copy(pageTurnAnimationEnabled = it) },
                        toolbarPreferences = sanitizedToolbarPreferences,
                        localTtsState = localTts.state,
                        onLocalTtsToggle = {
                            when (localTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> loadedBook?.let { epub ->
                                    cloudTts?.stop()
                                    val session = ReaderEngine().createSession(
                                        book = epub,
                                        settings = settings,
                                        initialPageIndex = currentPageIndex,
                                        initialLocator = currentLocator
                                    )
                                    localTts.start(
                                        chunks = ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                            .ifEmpty { ReaderTtsPlanner.chunksForCurrentChapter(session) }
                                            .withTtsReplacements(readerTtsReplacementPreferences, book.id),
                                        bookTitle = epub.title,
                                        bookId = book.id,
                                    )
                                }
                                SharedMobileEpubLocalTtsState.SPEAKING -> localTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> localTts.resume()
                            }
                        },
                        onLocalTtsStop = localTts::stop,
                        cloudTtsState = cloudTtsState,
                        cloudTtsAvailable = cloudTtsAvailable,
                        onCloudTtsToggle = ::toggleCloudTts,
                        onCloudTtsStop = cloudTts?.let { controller -> { controller.stop() } } ?: {},
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = {
                            keepScreenOn = it
                            onKeepScreenOnPreferenceChange(it)
                        },
                        autoScroll = autoScrollModeActive,
                        onAutoScrollChange = { active ->
                            autoScrollModeActive = active
                            autoScroll = active
                            if (active && autoScrollMusicianMode) showChrome = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .then(
                                if (!systemUiHidden) {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                } else {
                                    Modifier
                                }
                            )
                    )
                    if (loadedBook != null && pages.isNotEmpty()) {
                        SharedMobileEpubBottomBar(
                            tools = bottomToolbarTools,
                            isBookmarked = isBookmarked,
                            onToc = { openReaderDrawer(0) },
                            onFormat = { showFormatSheet = true },
                            onSearch = { showSearchResultsPanel = true; showSearch = true },
                            onTheme = { showThemeSheet = true },
                            onBookmark = ::toggleBookmark,
                            onVisualOptions = { showVisualOptionsSheet = true },
                            onOpenSlider = { showSlider = !showSlider },
                            onDictionary = onOpenDictionarySettings,
                            onOpenAiHub = { showAiHub = true; onOpenAiHub() },
                            aiAvailable = readerAiAvailable,
                            localTtsState = localTts.state,
                            onLocalTtsToggle = {
                                when (localTts.state) {
                                    SharedMobileEpubLocalTtsState.IDLE -> loadedBook?.let { epub ->
                                        cloudTts?.stop()
                                        val session = ReaderEngine().createSession(
                                            book = epub,
                                            settings = settings,
                                            initialPageIndex = currentPageIndex,
                                            initialLocator = currentLocator
                                        )
                                        localTts.start(
                                            chunks = ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                                .ifEmpty { ReaderTtsPlanner.chunksForCurrentChapter(session) }
                                                .withTtsReplacements(readerTtsReplacementPreferences, book.id),
                                            bookTitle = epub.title,
                                            bookId = book.id,
                                        )
                                    }
                                    SharedMobileEpubLocalTtsState.SPEAKING -> localTts.pause()
                                    SharedMobileEpubLocalTtsState.PAUSED -> localTts.resume()
                                }
                            },
                            cloudTtsState = cloudTtsState,
                            cloudTtsAvailable = cloudTtsAvailable,
                            onCloudTtsToggle = ::toggleCloudTts,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(
                                    if (!navigationUiHidden) {
                                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
                if (localTts.isSessionActive) {
                    SharedMobileEpubTtsControls(
                        tts = localTts,
                        onLocate = {
                            detachedTtsChunkIndex = null
                            activeTtsChunk?.let { navigate(it.toLocator(), detachFromTts = false) }
                        },
                        onOpenSettings = { showTtsSettingsSheet = true },
                        overlaySize = ttsOverlaySize,
                        onOverlaySizeChange = {
                            ttsOverlaySize = it
                            onTtsOverlaySizePreferenceChange(it)
                        },
                        modifier = Modifier
                            .align(BiasAlignment(readerTtsOverlayAlignmentBias(ttsOverlaySize), 1f))
                            .padding(horizontal = 12.dp)
                            .offset(y = if (showChrome) (-52).dp else (-12).dp)
                    )
                }
                if (
                    cloudTts != null &&
                    (cloudTtsState.isLoading || cloudTtsState.isPlaying || cloudTtsState.isPaused)
                ) {
                    SharedMobileEpubCloudTtsControls(
                        tts = cloudTts,
                        onLocate = {
                            detachedTtsChunkIndex = null
                            activeCloudTtsChunk?.let { navigate(it.toLocator(), detachFromTts = false) }
                        },
                        overlaySize = ttsOverlaySize,
                        onOverlaySizeChange = {
                            ttsOverlaySize = it
                            onTtsOverlaySizePreferenceChange(it)
                        },
                        modifier = Modifier
                            .align(BiasAlignment(readerTtsOverlayAlignmentBias(ttsOverlaySize), 1f))
                            .padding(horizontal = 12.dp)
                            .offset(y = if (showChrome) (-52).dp else (-12).dp),
                    )
                }
                if (autoScrollModeActive && settings.readingMode == ReaderReadingMode.VERTICAL) {
                    SharedMobileEpubAutoScrollControls(
                        isPlaying = autoScroll,
                        profile = autoScrollProfile,
                        isLocalMode = autoScrollIsLocal,
                        useSlider = autoScrollUseSlider,
                        isMusicianMode = autoScrollMusicianMode,
                        isCollapsed = autoScrollCollapsed,
                        onPlayPause = { autoScroll = !autoScroll },
                        onSpeedChange = { requested ->
                            val next = autoScrollProfile.copy(speed = requested).sanitized()
                            autoScrollProfile = next
                            if (autoScrollIsLocal) {
                                autoScrollLocalProfile = next
                            } else {
                                onReaderAutoScrollProfileChange(next)
                            }
                        },
                        onMinSpeedChange = { requested ->
                            val next = autoScrollProfile.withMinSpeed(requested)
                            autoScrollProfile = next
                            if (autoScrollIsLocal) autoScrollLocalProfile = next
                            else onReaderAutoScrollProfileChange(next)
                        },
                        onMaxSpeedChange = { requested ->
                            val next = autoScrollProfile.withMaxSpeed(requested)
                            autoScrollProfile = next
                            if (autoScrollIsLocal) autoScrollLocalProfile = next
                            else onReaderAutoScrollProfileChange(next)
                        },
                        onInputModeToggle = {
                            autoScrollUseSlider = !autoScrollUseSlider
                            onAutoScrollUseSliderPreferenceChange(autoScrollUseSlider)
                        },
                        onMusicianModeToggle = {
                            autoScrollMusicianMode = !autoScrollMusicianMode
                            onAutoScrollMusicianModePreferenceChange(autoScrollMusicianMode)
                            if (autoScrollMusicianMode) showChrome = false
                        },
                        onCollapseChange = { autoScrollCollapsed = it },
                        onScrollToTop = {
                            performMusicianGesture(
                                planReaderMusicianGesture(
                                    isRightRegion = false,
                                    isLongPress = true,
                                )
                            )
                        },
                        onLocalModeChange = { useLocal ->
                            if (useLocal != autoScrollIsLocal) {
                                if (useLocal) {
                                    val local = autoScrollLocalProfile ?: autoScrollProfile
                                    autoScrollLocalProfile = local
                                    autoScrollProfile = local
                                } else {
                                    autoScrollLocalProfile = autoScrollProfile
                                    autoScrollProfile = readerAutoScrollProfile.sanitized()
                                }
                                autoScrollIsLocal = useLocal
                            }
                        },
                        onClose = {
                            autoScroll = false
                            autoScrollModeActive = false
                            autoScrollTemporarilyPaused = false
                            showChrome = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .offset(y = if (showChrome) (-52).dp else (-12).dp)
                    )
                }
                if (autoScrollModeActive && autoScrollMusicianMode && settings.readingMode == ReaderReadingMode.VERTICAL) {
                    SharedMobileEpubMusicianOverlay(
                        onGesture = { isRight, isLongPress ->
                            performMusicianGesture(planReaderMusicianGesture(isRight, isLongPress))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val canPullDirection = (pullDirection == "previous" && currentChapterIndex > 0) ||
                    (pullDirection == "next" && currentChapterIndex < (loadedBook?.chapters?.lastIndex ?: -1))
                if (pullProgress > 0.05f && settings.pullToTurnEnabled && canPullDirection) {
                    SharedMobileEpubChapterChangeIndicator(
                        direction = pullDirection.orEmpty(),
                        progress = pullProgress,
                        modifier = Modifier.align(if (pullDirection == "previous") Alignment.TopCenter else Alignment.BottomCenter).padding(8.dp)
                    )
                }
                if (showSearch && loadedBook != null) {
                    SharedMobileEpubSearchOverlay(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            searchRequestId++
                        },
                        onForceSearch = {
                            immediateSearchRequestId = searchRequestId + 1L
                            searchRequestId++
                        },
                        results = searchResults,
                        isSearching = isSearchInProgress,
                        showResults = showSearchResultsPanel,
                        onShowResultsChange = { showSearchResultsPanel = it },
                        onResultClick = { result ->
                            searchResultIndex = searchResults.indexOf(result)
                            navigateSearchResult(result)
                        },
                        onDismiss = {
                            showSearch = false
                            showSearchResultsPanel = true
                            searchQuery = ""
                            searchResults = emptyList()
                            searchResultIndex = -1
                            searchRequestId++
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if ((!showSearch || !showSearchResultsPanel) && searchResultIndex >= 0 && searchResults.isNotEmpty()) {
                    SharedMobileEpubSearchNavigation(
                        current = searchResultIndex,
                        total = searchResults.size,
                        onPrevious = {
                            searchResultIndex = (searchResultIndex - 1).coerceAtLeast(0)
                            navigateSearchResult(searchResults[searchResultIndex])
                        },
                        onNext = {
                            searchResultIndex = (searchResultIndex + 1).coerceAtMost(searchResults.lastIndex)
                            navigateSearchResult(searchResults[searchResultIndex])
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                    )
                }
                if (
                    showChrome &&
                    !showSearch &&
                    jumpHistory.hasJumpTargets
                ) {
                    SharedMobileEpubJumpHistoryBar(
                        backLabel = jumpHistory.backLocator?.mobileEpubJumpLabel(loadedBook),
                        forwardLabel = jumpHistory.forwardLocator?.mobileEpubJumpLabel(loadedBook),
                        onBack = ::goBackInJumpHistory,
                        onForward = ::goForwardInJumpHistory,
                        onClear = { jumpHistory = jumpHistory.clear() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (localTts.isSessionActive || autoScrollModeActive) {
                                    120.dp
                                } else {
                                    52.dp
                                }
                            )
                    )
                }
                if (showChrome && showSlider && !showSearch && pages.isNotEmpty()) {
                        SharedMobileEpubSlider(
                            pageIndex = currentPageIndex,
                            pageCount = pageCount,
                            settings = settings,
                            onPageSelected = { index ->
                                pages.getOrNull(index)?.let {
                                    recordJumpAndNavigate(it.toMobileEpubLocator(loadedBook))
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 60.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
    }

    if (showFormatSheet) {
        SharedMobileEpubFormatSheet(
            settings = settings,
            isLocalMode = isLocalFormatMode,
            customFonts = customFonts,
            onImportFont = onImportFont,
            onLocalModeChange = { useLocal ->
                if (useLocal != isLocalFormatMode) {
                    if (useLocal) {
                        val local = localFormatSettings ?: settings
                        localFormatSettings = local
                        settings = settings.withReaderFormatFrom(local)
                    } else {
                        localFormatSettings = settings
                        settings = settings.withReaderFormatFrom(migratedReaderDefaults)
                    }
                    isLocalFormatMode = useLocal
                }
            },
            onSettingsChange = {
                val next = it
                settings = next
                if (isLocalFormatMode) {
                    localFormatSettings = next
                } else {
                    onReaderDefaultSettingsChange(next)
                }
            },
            onDismiss = { showFormatSheet = false }
        )
    }
    if (showThemeSheet) {
        SharedMobileEpubThemeSheet(
            settings = settings,
            customReaderThemes = customReaderThemes,
            onCustomReaderThemesChange = onCustomReaderThemesChange,
            onSettingsChange = { settings = it },
            onDismiss = { showThemeSheet = false }
        )
    }
    if (showVisualOptionsSheet) {
        SharedMobileEpubVisualOptionsSheet(
            settings = settings,
            onSettingsChange = {
                settings = it
                onReaderDefaultSettingsChange(it)
            },
            onDismiss = { showVisualOptionsSheet = false }
        )
    }
    if (showCustomizeToolsSheet) {
        SharedMobileEpubToolbarCustomizationSheet(
            toolbarPreferences = sanitizedToolbarPreferences,
            onToolbarPreferencesChange = onReaderToolbarPreferencesChange,
            onDismiss = { showCustomizeToolsSheet = false }
        )
    }
    if (showScreenOrientationSheet) {
        SharedMobileReaderScreenOrientationSheet(
            selectedMode = readerScreenOrientationMode,
            onModeSelected = onReaderScreenOrientationModeChange,
            onDismiss = { showScreenOrientationSheet = false }
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
    if (showTtsReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showTtsReplacementsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)
            ) {
                SharedReaderTtsReplacementControls(
                    preferences = readerTtsReplacementPreferences,
                    bookId = book.id,
                    onPreferencesChange = onReaderTtsReplacementPreferencesChange
                )
            }
        }
    }
    if (readerExtrasState.aiResult.hasContent) {
        SharedReaderAiResultSheet(
            result = readerExtrasState.aiResult,
            onDismiss = onAiResultDismiss,
        )
    }
    if (showAiHub) {
        ModalBottomSheet(onDismissRequest = { showAiHub = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("AI features", style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = {
                        showAiHub = false
                        loadedBook?.chapters?.getOrNull(currentChapterIndex)?.plainText
                            ?.takeIf { it.isNotBlank() }
                            ?.let { onAiAction(ReaderAiFeature.SUMMARIZE, it.take(24_000)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Summarize current chapter") }
                TextButton(
                    onClick = {
                        showAiHub = false
                        loadedBook?.let { epub ->
                            val recapText = epub.chapters.take(currentChapterIndex + 1)
                                .joinToString("\n\n") { chapter -> chapter.plainText }
                                .take(24_000)
                            onAiAction(ReaderAiFeature.RECAP, recapText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Recap up to here") }
            }
        }
    }
    if (showTtsSettingsSheet) {
        SharedMobileReaderTtsSettingsSheet(
            tts = localTts,
            onDismiss = { showTtsSettingsSheet = false },
            cloudTts = cloudTts,
            cloudTtsModeEnabled = cloudTtsModeEnabled,
            onCloudTtsModeChange = onCloudTtsModeChange,
            cloudTtsVoiceId = cloudTtsVoiceId,
            onCloudTtsVoiceChange = onCloudTtsVoiceChange,
            onClearCloudTtsCache = onClearCloudTtsCache,
        )
    }
    if (showBookReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showBookReplacementsSheet = false }) {
            SharedMobileEpubBookReplacementControls(
                preferences = readerBookReplacementPreferences,
                bookId = book.id,
                onPreferencesChange = onReaderBookReplacementPreferencesChange,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp)
            )
        }
    }
    if (showHighlightPaletteManager) {
        SharedReaderHighlightPaletteDialog(
            palette = readerHighlightPalette,
            onDismiss = { showHighlightPaletteManager = false },
            onSave = { palette ->
                onReaderHighlightPaletteChange(palette)
                showHighlightPaletteManager = false
            }
        )
    }
    if (showFileInfo) {
        ModalBottomSheet(onDismissRequest = { showFileInfo = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("File Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(loadedBook?.title ?: book.displayName, style = MaterialTheme.typography.titleMedium)
                loadedBook?.author?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${loadedBook?.chapters?.size ?: 0} chapters · $pageCount reader pages")
                Text(book.displayName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    editingHighlight?.let { highlight ->
        SharedMobileEpubHighlightSheet(
            highlight = highlight,
            palette = readerHighlightPalette,
            onUpdate = { updated ->
                highlights = highlights.map { current ->
                    if (current.id == updated.id) updated else current
                }
                editingHighlight = updated
            },
            onDelete = {
                highlights = highlights.filterNot { it.id == highlight.id }
                editingHighlight = null
            },
            onSpeak = { speakSelectedText(highlight.text, highlight.locator) },
            onLookup = { action ->
                openSharedMobileEpubLookup(action, highlight.text)
            },
            onClipboardError = onClipboardError,
            onDismiss = { editingHighlight = null }
        )
    }
}
