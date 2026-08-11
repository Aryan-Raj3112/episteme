package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.shouldShowEpubPageInfoBar
import com.aryan.reader.shared.shouldReserveEpubPageInfoBarSpace
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.toReaderReadingMode
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSettingsUpdateMode
import com.aryan.reader.shared.reader.appearanceSignature
import com.aryan.reader.shared.reader.isRightToLeftPaginationEnabled
import com.aryan.reader.shared.reader.layoutSignature
import kotlinx.coroutines.delay

internal val SharedReaderFullscreenFocusRetryDelaysMillis = longArrayOf(80L, 120L, 160L, 240L)

internal fun ReaderSessionState.reduceReaderAction(
    action: ReaderAction,
    readerEngine: ReaderEngine,
    settingsUpdateMode: ReaderSettingsUpdateMode
): ReaderSessionState {
    if (settingsUpdateMode == ReaderSettingsUpdateMode.FULL_REPAGINATE) {
        return reduce(action, readerEngine)
    }
    return when (action) {
        is ReaderAction.SettingsChanged -> readerEngine.updateSettings(this, action.settings, settingsUpdateMode)
        is ReaderAction.RenderModeChanged -> readerEngine.updateSettings(
            this,
            reader.settings.copy(readingMode = action.renderMode.toReaderReadingMode()),
            settingsUpdateMode
        )
        is ReaderAction.ThemeChanged -> readerEngine.updateSettings(
            this,
            action.theme.toReaderSettings(reader.settings),
            settingsUpdateMode
        )
        is ReaderAction.FormatChanged -> readerEngine.updateSettings(
            this,
            action.settings.toReaderSettings(reader.settings),
            settingsUpdateMode
        )
        else -> reduce(action, readerEngine)
    }
}

@Composable
fun SharedScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SharedUiTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SharedUiTokens.contentGap)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

@Composable
fun SharedReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onReturnToLibrary: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
    toolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    appThemeControls: (@Composable () -> Unit)? = null,
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit = {},
    ttsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    ttsReplacementBookId: String? = null,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    onPickCustomFont: (() -> String?)? = null,
    customFonts: List<CustomFontItem> = emptyList(),
    readerExtrasState: ReaderExtrasState = ReaderExtrasState(),
    aiByokSettings: ReaderAiByokSettings = ReaderAiByokSettings(),
    externalLookupAvailable: Boolean = true,
    cloudTtsControlsAvailable: Boolean = true,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit = { _, _ -> },
    onAiAction: (ReaderAiFeature, String) -> Unit = { _, _ -> },
    onAiResultDismiss: () -> Unit = {},
    onCopyText: (String) -> Unit = {},
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit = { _, _ -> },
    onCloudTtsPauseResume: () -> Unit = {},
    onCloudTtsStop: () -> Unit = {},
    onCloudTtsClearCache: () -> Unit = {},
    onCloudTtsVoiceChange: (String) -> Unit = {},
    onOpenAiHub: (() -> Unit)? = null,
    onDownloadReaderImage: ((ReaderImageReference) -> Unit)? = null,
    readerImagePreviewContent: (@Composable (ReaderImageReference, Modifier) -> Unit)? = null,
    readerTextureDataUri: (String) -> String? = { null },
    readerTexturePreviewContent: (@Composable (String, Modifier) -> Unit)? = null,
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    preferNativeVerticalReader: Boolean = false,
    bottomChromeExtraContent: @Composable ColumnScope.() -> Unit = {},
    useDetachedChromeLayer: Boolean = true,
    useDetachedPanelLayer: Boolean = true,
    settingsUpdateMode: ReaderSettingsUpdateMode = ReaderSettingsUpdateMode.FULL_REPAGINATE,
    readerContent: @Composable ColumnScope.(
        renderPlan: ReaderContentRenderPlan,
        onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
        onHighlightSelected: (String) -> Unit,
        onOpenHighlightPaletteManager: () -> Unit,
        onChromeActivity: () -> Unit
    ) -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val byokSettings = aiByokSettings.sanitized()
    val background = settings.backgroundColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val foreground = settings.textColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
    val chromeBarColor = MaterialTheme.colorScheme.surfaceVariant
    val chromeContentColor = MaterialTheme.colorScheme.onSurface
    val pageInfoText = readerState.pageInfoText()
    val shouldShowPageInfo = shouldShowEpubPageInfoBar(settings.pageInfoMode, showReaderChrome = !isFullscreen)
    val reserveTopPageInfoSpace =
        settings.pageInfoPosition == PageInfoPosition.TOP &&
            shouldReserveEpubPageInfoBarSpace(
                pageInfoMode = settings.pageInfoMode,
                showReaderChrome = !isFullscreen,
                isNativeVerticalMode = settings.readingMode == ReaderReadingMode.VERTICAL
            )
    val activeTtsProgress = readerExtrasState.cloudTts.progress
    val activeTtsChunk = activeTtsProgress.currentChunk
    val activeTtsLocator = activeTtsChunk?.toLocator()
    val ttsRequestId = activeTtsChunk?.let { activeTtsProgress.sessionId + it.index + 1L } ?: 0L
    val navigationLocator = session.navigationLocator ?: session.activeSearchResult?.locator ?: readerState.currentPageLocator()
    val effectiveCloudTtsAvailable = cloudTtsControlsAvailable && byokSettings.isCloudTtsAvailable
    val readerFocusRequester = remember(session.reader.book.id) { FocusRequester() }
    var readerFocusRestoreRequest by remember(session.reader.book.id) { mutableIntStateOf(0) }
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnFullscreenChange by rememberUpdatedState(onFullscreenChange)
    var selectedHighlightId by remember(session.reader.book.id) { mutableStateOf<String?>(null) }
    var sidebarNavigationHighlightId by remember(session.reader.book.id) { mutableStateOf<String?>(null) }
    val selectedHighlight = remember(session.highlights, selectedHighlightId) {
        session.highlights.firstOrNull { it.id == selectedHighlightId }
    }
    var showHighlightPaletteManager by remember { mutableStateOf(false) }
    fun openHighlightPaletteManager() {
        showHighlightPaletteManager = true
    }
    fun dispatch(action: ReaderAction) {
        onSessionChange(session.reduceReaderAction(action, readerEngine, settingsUpdateMode))
    }
    fun dispatchAll(actions: List<ReaderAction>) {
        onSessionChange(actions.fold(session) { state, action ->
            state.reduceReaderAction(action, readerEngine, settingsUpdateMode)
        })
    }
    fun setFullscreen(enabled: Boolean) {
        onFullscreenChange(enabled)
    }
    val workspaceModel = epubReaderWorkspaceModel(
        session = session,
        toolbarPreferences = toolbarPreferences,
        appThemeControlsAvailable = appThemeControls != null,
        extrasState = readerExtrasState,
        aiAvailable = byokSettings.areReaderAiFeaturesAvailable,
        cloudTtsAvailable = effectiveCloudTtsAvailable,
        externalLookupAvailable = externalLookupAvailable
    )

    LaunchedEffect(session.reader.book.id, settings.readingMode, readerState.currentPageIndex) {
        runCatching { readerFocusRequester.requestFocus() }
    }

    val readerPopupActive = selectedHighlight != null ||
        showHighlightPaletteManager ||
        readerExtrasState.aiResult.hasContent
    val shouldRestoreReaderFocus = !session.isSearchActive && !readerPopupActive
    val currentShouldRestoreReaderFocus by rememberUpdatedState(shouldRestoreReaderFocus)
    val rightToLeftPaginationActive = settings.isRightToLeftPaginationEnabled()
    fun requestReaderFocusRestore() {
        readerFocusRestoreRequest += 1
    }
    LaunchedEffect(isFullscreen, session.reader.book.id) {
        for (delayMillis in SharedReaderFullscreenFocusRetryDelaysMillis) {
            delay(delayMillis)
            if (currentShouldRestoreReaderFocus) {
                runCatching { readerFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(shouldRestoreReaderFocus, session.reader.book.id) {
        if (shouldRestoreReaderFocus) {
            delay(120L)
            runCatching { readerFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(readerFocusRestoreRequest, session.reader.book.id) {
        if (readerFocusRestoreRequest > 0) {
            delay(140L)
            if (currentShouldRestoreReaderFocus) {
                runCatching { readerFocusRequester.requestFocus() }
            }
        }
    }

    DisposableEffect(session.reader.book.id) {
        onDispose {
            if (currentIsFullscreen) {
                currentOnFullscreenChange(false)
            }
        }
    }

    ReaderWorkspaceShell(
        model = workspaceModel,
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        progressLabel = "${readerState.progress.toInt()}%",
        onReturnToLibrary = onReturnToLibrary,
        isFullscreen = isFullscreen,
        onFullscreenChange = ::setFullscreen,
        isBookmarked = session.currentBookmark != null,
        onToggleBookmark = { dispatch(ReaderAction.ToggleBookmark) },
        onSearchAction = { dispatch(ReaderAction.SearchOpened) },
        onAiHubAction = onOpenAiHub.takeIf { byokSettings.areReaderAiFeaturesAvailable },
        onReadAloudAction = if (effectiveCloudTtsAvailable) {
            {
                if (readerExtrasState.cloudTts.isPlaying ||
                    readerExtrasState.cloudTts.isLoading ||
                    readerExtrasState.cloudTts.isPaused
                ) {
                    onCloudTtsStop()
                } else {
                    onCloudTtsStart(
                        ReaderTtsReadScope.BOOK,
                        ReaderTtsPlanner.chunksFromCurrentLocation(session)
                    )
                }
            }
        } else {
            null
        },
        useDetachedChromeLayer = useDetachedChromeLayer,
        useDetachedPanelLayer = useDetachedPanelLayer,
        contentHandlesChromeTap = true,
        onReaderFocusRestoreRequest = ::requestReaderFocusRestore,
        topSearchBar = if (session.isSearchActive) {
            {
                SharedReaderSearchTopBar(
                    session = session,
                    onReaderAction = { action -> dispatch(action) }
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(readerFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    isFullscreen && event.key == Key.Escape -> {
                        setFullscreen(false)
                        true
                    }

                    event.key == Key.DirectionRight -> {
                        dispatch(if (rightToLeftPaginationActive) ReaderAction.PreviousPage else ReaderAction.NextPage)
                        true
                    }

                    event.key == Key.DirectionLeft -> {
                        dispatch(if (rightToLeftPaginationActive) ReaderAction.NextPage else ReaderAction.PreviousPage)
                        true
                    }

                    event.key == Key.PageDown -> {
                        dispatch(ReaderAction.NextPage)
                        true
                    }

                    event.key == Key.PageUp -> {
                        dispatch(ReaderAction.PreviousPage)
                        true
                    }

                    event.key == Key.MoveHome -> {
                        dispatch(ReaderAction.JumpToPage(0))
                        true
                    }

                    event.key == Key.MoveEnd -> {
                        dispatch(ReaderAction.JumpToPage(readerState.pages.lastIndex))
                        true
                    }

                    event.isCtrlPressed && event.key == Key.G -> {
                        dispatch(ReaderAction.JumpToNextSearchResult)
                        true
                    }

                    event.isCtrlPressed && event.key == Key.F -> {
                        dispatch(ReaderAction.SearchOpened)
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
        leftSidebar = { _ ->
            SharedReaderSidebar(
                session = session,
                readerEngine = readerEngine,
                sections = workspaceModel.leftSections,
                onGoToChapter = { dispatch(ReaderAction.JumpToChapter(it)) },
                onGoToLocator = { dispatch(ReaderAction.JumpToLocator(it)) },
                onGoToBookmark = { dispatch(ReaderAction.JumpToLocator(it.locator)) },
                onDownloadImage = onDownloadReaderImage,
                imagePreviewContent = readerImagePreviewContent,
                onGoToHighlight = {
                    sidebarNavigationHighlightId = it.id
                    selectedHighlightId = null
                    dispatch(ReaderAction.JumpToLocator(it.locator))
                },
                onEditHighlight = {
                    selectedHighlightId = it.id
                },
                highlightPalette = highlightPalette,
                onHighlightColorChange = { highlight, color ->
                    dispatch(ReaderAction.HighlightUpdated(highlight.id, color = color))
                },
                onOpenHighlightPaletteManager = ::openHighlightPaletteManager,
                onDeleteHighlight = {
                    dispatch(ReaderAction.HighlightDeleted(it.id))
                    if (selectedHighlightId == it.id) {
                        selectedHighlightId = null
                    }
                }
            )
        },
        rightInspector = {
            SharedReaderControlPanel(
                session = session,
                toolbarPreferences = toolbarPreferences,
                appThemeControls = appThemeControls,
                onPickCustomFont = onPickCustomFont,
                customFonts = customFonts,
                extrasState = readerExtrasState,
                aiByokSettings = byokSettings,
                cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                ttsReplacementPreferences = ttsReplacementPreferences,
                ttsReplacementBookId = ttsReplacementBookId ?: session.reader.book.title,
                onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                customReaderThemes = customReaderThemes,
                onCustomReaderThemesChange = onCustomReaderThemesChange,
                readerCustomTextureIds = readerCustomTextureIds,
                readerTexturePreviewContent = readerTexturePreviewContent,
                onImportReaderTexture = onImportReaderTexture,
                onReaderAction = { action -> dispatch(action) }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        logReaderGapChrome(
                            layer = "bottom_nav_surface",
                            bounds = coordinates.boundsInWindow(),
                            details = "sliderVisible=${toolbarPreferences.isVisible(ReaderTool.SLIDER)} pageInfoBottom=${shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.BOTTOM}"
                        )
                },
                shape = RoundedCornerShape(0.dp),
                color = chromeBarColor,
                contentColor = chromeContentColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = chromeContentColor.copy(alpha = 0.12f))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        bottomChromeExtraContent()
                        val showJumpHistory = !session.isSearchActive && session.shouldShowJumpHistory
                        if (showJumpHistory) {
                            SharedReaderJumpHistoryBar(
                                session = session,
                                onBack = { dispatch(ReaderAction.JumpBack) },
                                onForward = { dispatch(ReaderAction.JumpForward) },
                                onClear = { dispatch(ReaderAction.JumpHistoryCleared) }
                            )
                            HorizontalDivider(color = chromeContentColor.copy(alpha = 0.12f))
                        }
                        SharedReaderCompactNavigation(
                            session = session,
                            showSlider = toolbarPreferences.isVisible(ReaderTool.SLIDER),
                            canGoPrevious = readerState.canGoPrevious,
                            canGoNext = readerState.canGoNext,
                            pageInfoText = if (shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.BOTTOM) pageInfoText else null,
                            onPrevious = { dispatch(ReaderAction.PreviousPage) },
                            onNext = { dispatch(ReaderAction.NextPage) },
                            onPageNumberChange = { pageNumber -> dispatch(ReaderAction.GoToPageNumber(pageNumber)) },
                            contentColor = chromeContentColor
                        )
                    }
                }
            }
        },
        fullscreenBottomBar = {
            SharedReaderFullscreenNavigation(
                session = session,
                onPrevious = { dispatch(ReaderAction.PreviousPage) },
                onNext = { dispatch(ReaderAction.NextPage) },
                onPageNumberChange = { pageNumber -> dispatch(ReaderAction.GoToPageNumber(pageNumber)) },
                onJumpBack = { dispatch(ReaderAction.JumpBack) },
                onJumpForward = { dispatch(ReaderAction.JumpForward) },
                onClearJumpHistory = { dispatch(ReaderAction.JumpHistoryCleared) },
                backgroundColor = chromeBarColor,
                contentColor = chromeContentColor
            )
        }
    ) { onChromeActivity ->
        LaunchedEffect(sidebarNavigationHighlightId) {
            if (sidebarNavigationHighlightId != null) {
                delay(1_200)
                sidebarNavigationHighlightId = null
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    logReaderGapChrome(
                        layer = "reader_content_column",
                        bounds = coordinates.boundsInWindow(),
                        details = "mode=${settings.readingMode} columnGap=${if (reserveTopPageInfoSpace) 12 else 0} pageInfoTop=$reserveTopPageInfoSpace"
                    )
                },
            verticalArrangement = Arrangement.spacedBy(if (reserveTopPageInfoSpace) 12.dp else 0.dp)
        ) {
            if (reserveTopPageInfoSpace) {
                Text(pageInfoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val textureDataUri = remember(settings.textureId) {
                settings.textureId?.let(readerTextureDataUri)
            }
            val navigationTarget = ReaderContentNavigationTarget(
                locator = navigationLocator,
                requestId = session.navigationRequestId,
                readingMode = settings.readingMode,
                ttsLocator = activeTtsLocator,
                ttsRequestId = ttsRequestId
            )
            val renderPlan = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                if (preferNativeVerticalReader) {
                    ReaderContentRenderPlan.NativeVerticalPages(
                        book = readerState.book,
                        pages = readerState.pages,
                        currentPageIndex = readerState.currentPageIndex,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlightPalette = highlightPalette,
                        background = background,
                        foreground = foreground,
                        navigationTarget = navigationTarget,
                        highlights = session.highlights
                    )
                } else {
                val lastChapterIndex = readerState.book.chapters.lastIndex
                val activeChapterIndex = if (lastChapterIndex >= 0) {
                    readerState.currentPage?.chapterIndex?.takeIf { it in 0..lastChapterIndex }
                        ?: navigationLocator?.chapterIndex?.takeIf { it in 0..lastChapterIndex }
                        ?: 0
                } else {
                    0
                }
                var renderedChapterRange by remember(readerState.book.id, lastChapterIndex, settings.readingMode) {
                    mutableStateOf(readerVerticalRenderedChapterRange(activeChapterIndex, lastChapterIndex))
                }
                LaunchedEffect(readerState.book.id, lastChapterIndex, settings.readingMode) {
                    logReaderPositionTrace {
                        "event=vertical_render_window_init book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                            "mode=${settings.readingMode} activeChapter=$activeChapterIndex " +
                            "range=${renderedChapterRange?.let { "${it.first}..${it.last}" } ?: "all"} " +
                            "page=${readerState.currentPageIndex} pages=${readerState.pages.size}"
                    }
                }
                LaunchedEffect(
                    settings.readingMode,
                    session.navigationRequestId,
                    lastChapterIndex
                ) {
                    if (settings.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
                    val requestedChapterIndex = navigationLocator?.chapterIndex
                        ?.takeIf { it in 0..lastChapterIndex }
                        ?: return@LaunchedEffect
                    val nextRange = readerVerticalRenderedChapterRange(requestedChapterIndex, lastChapterIndex)
                    logReaderPositionTrace {
                        "event=vertical_render_window_navigation_request book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                            "requestId=${session.navigationRequestId} requestedChapter=$requestedChapterIndex " +
                            "previousRange=${renderedChapterRange?.let { "${it.first}..${it.last}" } ?: "all"} " +
                            "nextRange=${nextRange?.let { "${it.first}..${it.last}" } ?: "all"} " +
                            "locator=${navigationLocator.readerPositionTraceSummary()}"
                    }
                    renderedChapterRange = nextRange
                }
                LaunchedEffect(
                    settings.readingMode,
                    activeChapterIndex,
                    readerState.currentPageIndex,
                    lastChapterIndex,
                    renderedChapterRange
                ) {
                    if (settings.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
                    val currentRange = renderedChapterRange ?: return@LaunchedEffect
                    val activeChapterFirstPage = readerState.pages.indexOfFirst { it.chapterIndex == activeChapterIndex }
                    val activeChapterLastPage = readerState.pages.indexOfLast { it.chapterIndex == activeChapterIndex }
                    val nearChapterStart = activeChapterFirstPage < 0 ||
                        readerState.currentPageIndex <= activeChapterFirstPage + 1
                    val nearChapterEnd = activeChapterLastPage < 0 ||
                        readerState.currentPageIndex >= activeChapterLastPage - 1
                    val shouldShiftBackward = activeChapterIndex <= currentRange.first &&
                        currentRange.first > 0 &&
                        nearChapterStart
                    val shouldShiftForward = activeChapterIndex >= currentRange.last &&
                        currentRange.last < lastChapterIndex &&
                        nearChapterEnd
                    if (shouldShiftBackward || shouldShiftForward) {
                        val nextRange = readerVerticalRenderedChapterRange(activeChapterIndex, lastChapterIndex)
                        logReaderPositionTrace {
                            "event=vertical_render_window_passive_shift book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                                "direction=${if (shouldShiftBackward) "backward" else "forward"} " +
                                "activeChapter=$activeChapterIndex page=${readerState.currentPageIndex} " +
                                "chapterPages=${activeChapterFirstPage}..${activeChapterLastPage} " +
                                "previousRange=${currentRange.first}..${currentRange.last} " +
                                "nextRange=${nextRange?.let { "${it.first}..${it.last}" } ?: "all"}"
                        }
                        renderedChapterRange = nextRange
                    } else if (activeChapterIndex <= currentRange.first || activeChapterIndex >= currentRange.last) {
                        logReaderPositionTrace {
                            "event=vertical_render_window_passive_hold book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                                "activeChapter=$activeChapterIndex page=${readerState.currentPageIndex} " +
                                "chapterPages=${activeChapterFirstPage}..${activeChapterLastPage} " +
                                "range=${currentRange.first}..${currentRange.last} " +
                                "nearStart=$nearChapterStart nearEnd=$nearChapterEnd"
                        }
                    }
                }
                val appearanceSignature = settings.appearanceSignature()
                val formatSignature = settings.layoutSignature()
                val appearanceScript = remember(appearanceSignature, formatSignature, textureDataUri, readerState.pages) {
                    val startedAt = currentTimestamp()
                    buildString {
                        append(
                            ReaderHtmlDocumentBuilder.appearanceUpdateScript(
                                settings = settings,
                                textureDataUri = textureDataUri
                            )
                        )
                        append('\n')
                        append(ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(readerState.pages))
                    }.also { script ->
                        logReaderOpenTrace {
                            "event=vertical_appearance_script_built book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                                "durationMs=${startedAt.readerOpenTraceElapsedMs()} scriptChars=${script.length} " +
                                "pages=${readerState.pages.size} mode=${settings.readingMode}"
                        }
                    }
                }
                val highlightPaletteScript = remember(highlightPalette) {
                    ReaderHtmlDocumentBuilder.highlightPaletteUpdateScript(highlightPalette)
                }
                // Keep the initial locator in the document so its first position report is not the top of the book.
                val html = remember(
                    readerState.book,
                    session.searchQuery,
                    session.searchOptions,
                    renderedChapterRange?.first,
                    renderedChapterRange?.last,
                    byokSettings.areReaderAiFeaturesAvailable,
                    effectiveCloudTtsAvailable,
                    externalLookupAvailable
                ) {
                    val startedAt = currentTimestamp()
                    val renderedChapterCount = renderedChapterRange
                        ?.count { it in readerState.book.chapters.indices }
                        ?: readerState.book.chapters.size
                    logReaderOpenTrace {
                        "event=vertical_html_build_start book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                            "chapters=${readerState.book.chapters.size} pages=${readerState.pages.size} " +
                            "renderedChapters=${renderedChapterRange?.let { "${it.first}..${it.last}" } ?: "all"} " +
                            "renderedChapterCount=$renderedChapterCount activeChapter=$activeChapterIndex " +
                            "textChars=${readerState.book.chapters.sumOf { it.plainText.length }} " +
                            "htmlChars=${readerState.book.chapters.sumOf { it.htmlContent.length }} " +
                            "semanticBlocks=${readerState.book.chapters.sumOf { it.semanticBlocks.size }} " +
                            "search=${session.searchQuery.isNotBlank()} hasNavigation=${navigationLocator != null} " +
                            "ai=${byokSettings.areReaderAiFeaturesAvailable} cloudTts=$effectiveCloudTtsAvailable " +
                            "externalLookup=$externalLookupAvailable"
                    }
                    ReaderHtmlDocumentBuilder.verticalDocument(
                        book = readerState.book,
                        settings = settings,
                        searchQuery = session.searchQuery,
                        searchOptions = session.searchOptions,
                        highlights = emptyList(),
                        highlightPalette = highlightPalette,
                        navigationLocator = navigationLocator,
                        pages = readerState.pages,
                        readerAiFeaturesEnabled = byokSettings.areReaderAiFeaturesAvailable,
                        cloudTtsEnabled = effectiveCloudTtsAvailable,
                        externalLookupEnabled = externalLookupAvailable,
                        textureDataUri = textureDataUri,
                        renderedChapterRange = renderedChapterRange
                    ).also { html ->
                        logReaderOpenTrace {
                            "event=vertical_html_build_done book=\"${readerState.book.title.readerOpenTracePreview(120)}\" " +
                                "durationMs=${startedAt.readerOpenTraceElapsedMs()} htmlChars=${html.length} " +
                                "chapters=${readerState.book.chapters.size} renderedChapterCount=$renderedChapterCount " +
                                "renderedChapters=${renderedChapterRange?.let { "${it.first}..${it.last}" } ?: "all"} " +
                                "pages=${readerState.pages.size}"
                        }
                    }
                }
                ReaderContentRenderPlan.WebDocument(
                    html = html,
                    appearanceScript = appearanceScript,
                    highlightPaletteScript = highlightPaletteScript,
                    background = background,
                    foreground = foreground,
                    navigationTarget = navigationTarget,
                    highlights = session.highlights
                )
                }
            } else {
                ReaderContentRenderPlan.NativePaginatedPages(
                    visiblePages = readerState.visiblePages,
                    settings = settings,
                    searchQuery = session.searchQuery,
                    searchOptions = session.searchOptions,
                    highlightPalette = highlightPalette,
                    background = background,
                    foreground = foreground,
                    navigationTarget = navigationTarget,
                    highlights = session.highlights
                )
            }
            readerContent(
                renderPlan,
                { pageIndex, locator -> dispatch(ReaderAction.VisiblePageChanged(pageIndex, locator)) },
                { highlightId ->
                    if (sidebarNavigationHighlightId == highlightId) {
                        sidebarNavigationHighlightId = null
                    } else {
                        selectedHighlightId = highlightId
                    }
                },
                ::openHighlightPaletteManager,
                onChromeActivity
            )
        }
        SharedReaderSearchOverlay(
            session = session,
            onResultClick = { index ->
                dispatchAll(
                    listOf(
                        ReaderAction.JumpToSearchResult(index),
                        ReaderAction.SearchResultsPanelToggled
                    )
                )
            },
            onShowResults = { dispatch(ReaderAction.SearchResultsPanelToggled) },
            onPrevious = { dispatch(ReaderAction.JumpToPreviousSearchResult) },
            onNext = { dispatch(ReaderAction.JumpToNextSearchResult) }
        )
        when {
            selectedHighlight != null -> {
                SharedReaderHighlightSheet(
                    session = session,
                    highlight = selectedHighlight,
                    palette = highlightPalette,
                    onDismiss = { selectedHighlightId = null },
                    onColorChange = { color ->
                        dispatch(ReaderAction.HighlightUpdated(selectedHighlight.id, color = color))
                    },
                    onStyleChange = { style ->
                        dispatch(ReaderAction.HighlightUpdated(selectedHighlight.id, style = style))
                    },
                    onOpenPaletteManager = ::openHighlightPaletteManager,
                    onSaveNote = { note ->
                        dispatch(ReaderAction.HighlightUpdated(selectedHighlight.id, note = note))
                    },
                    onDelete = {
                        dispatch(ReaderAction.HighlightDeleted(selectedHighlight.id))
                        selectedHighlightId = null
                    },
                    onCopy = { onCopyText(selectedHighlight.text) },
                    onSearch = { onExternalLookup(ReaderExternalLookupAction.SEARCH, selectedHighlight.text) }
                )
            }
            readerExtrasState.aiResult.hasContent -> {
                SharedReaderAiResultSheet(
                    result = readerExtrasState.aiResult,
                    onDismiss = onAiResultDismiss
                )
            }
        }
        if (showHighlightPaletteManager) {
            SharedReaderHighlightPaletteDialog(
                palette = highlightPalette,
                onDismiss = { showHighlightPaletteManager = false },
                onSave = { palette ->
                    onHighlightPaletteChange(palette)
                    showHighlightPaletteManager = false
                }
            )
        }
    }
}
