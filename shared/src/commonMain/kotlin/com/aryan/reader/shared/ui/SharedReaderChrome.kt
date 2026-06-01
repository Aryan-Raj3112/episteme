package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementBookSettings
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementRule
import com.aryan.reader.shared.ReaderTtsReplacementSuggestions
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.readerTextureDisplayName
import com.aryan.reader.shared.resetReaderFormatSettings
import com.aryan.reader.shared.sanitizeCustomReaderThemes
import com.aryan.reader.shared.shouldShowPageWidthFormatControl
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.withHorizontalReaderMargin
import com.aryan.reader.shared.withVerticalReaderMargin
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.appearanceSignature
import com.aryan.reader.shared.reader.isRightToLeftPaginationEnabled
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import com.aryan.reader.shared.reader.readerImageReferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SharedReaderFullscreenFocusRetryDelaysMillis = longArrayOf(80L, 120L, 160L, 240L)

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
    bottomChromeExtraContent: @Composable ColumnScope.() -> Unit = {},
    useDetachedChromeLayer: Boolean = true,
    useDetachedPanelLayer: Boolean = true,
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
    val shouldShowPageInfo = settings.pageInfoMode != PageInfoMode.HIDDEN
    val reserveTopPageInfoSpace =
        settings.readingMode != ReaderReadingMode.VERTICAL &&
            !isFullscreen &&
            shouldShowPageInfo &&
            settings.pageInfoPosition == PageInfoPosition.TOP
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
        onSessionChange(session.reduce(action, readerEngine))
    }
    fun dispatchAll(actions: List<ReaderAction>) {
        onSessionChange(actions.fold(session) { state, action -> state.reduce(action, readerEngine) })
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

@Composable
private fun SharedReaderSearchTopBar(
    session: ReaderSessionState,
    onReaderAction: (ReaderAction) -> Unit
) {
    val focusRequester = remember(session.reader.book.id) { FocusRequester() }

    LaunchedEffect(session.isSearchActive) {
        if (session.isSearchActive) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_close_search_desc", "Exit search and go back to the reader"),
                onClick = { onReaderAction(ReaderAction.SearchClosed) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = readerString("content_desc_close_search", "Close search"))
            }
            SharedStableOutlinedTextField(
                value = session.searchQuery,
                onValueChange = { onReaderAction(ReaderAction.SearchChanged(it)) },
                placeholder = { Text(readerString("search_in_book", "Search in book")) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                trailingIcon = if (session.searchQuery.isNotEmpty()) {
                    {
                        ReaderTooltipIconButton(
                            tooltip = readerString("tooltip_clear_search_desc", "Erase your current search query and start over"),
                            onClick = { onReaderAction(ReaderAction.SearchChanged("")) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = readerString("tooltip_clear_search", "Clear search"))
                        }
                    }
                } else {
                    null
                },
                selectionKey = session.reader.book.id
            )
            val resultsTooltip = if (session.showSearchResultsPanel) {
                readerString("tooltip_hide_results_desc", "Collapse the search results panel")
            } else {
                readerString("tooltip_show_results_desc", "Expand the panel to see all search matches")
            }
            ReaderTooltipIconButton(
                tooltip = resultsTooltip,
                onClick = { onReaderAction(ReaderAction.SearchResultsPanelToggled) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (session.showSearchResultsPanel) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (session.showSearchResultsPanel) {
                        readerString("desktop_hide_search_results", "Hide search results")
                    } else {
                        readerString("desktop_show_search_results", "Show search results")
                    }
                )
            }
    }
}
}

@Composable
private fun BoxScope.SharedReaderSearchOverlay(
    session: ReaderSessionState,
    onResultClick: (Int) -> Unit,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    AnimatedVisibility(
        visible = session.isSearchActive && session.showSearchResultsPanel,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(30f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                session.searchQuery.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(readerString("desktop_type_to_search_book", "Type to search this book"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                session.searchResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(readerString("desktop_no_matches", "No matches"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            readerString("desktop_matches_format", "%1\$d matches", session.searchResults.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        HorizontalDivider()
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = session.searchResults,
                                key = { index, result -> "${result.pageIndex}_${result.matchIndex}_$index" }
                            ) { index, result ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onResultClick(index) },
                                    color = if (index == session.activeSearchResultIndex) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            readerString(
                                                "desktop_pdf_page_author_format",
                                                "Page %1\$d - %2\$s",
                                                result.pageIndex + 1,
                                                result.chapterTitle
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            result.preview,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = session.isSearchActive && !session.showSearchResultsPanel && session.searchResults.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 18.dp)
            .zIndex(31f)
    ) {
        SharedReaderSearchNavigationPill(
            session = session,
            onShowResults = onShowResults,
            onPrevious = onPrevious,
            onNext = onNext
        )
    }
}

@Composable
private fun SharedReaderSearchNavigationPill(
    session: ReaderSessionState,
    onShowResults: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_prev_result_desc", "Jump to the previous search match in the document"),
                onClick = onPrevious,
                enabled = session.canGoToPreviousSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("desktop_previous_search_result", "Previous search result"))
            }
            Text(
                text = if (session.activeSearchResultIndex in session.searchResults.indices) {
                    "${session.activeSearchResultIndex + 1}/${session.searchResults.size}"
                } else {
                    readerString("desktop_matches_format", "%1\$d matches", session.searchResults.size)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onShowResults).padding(horizontal = 8.dp)
            )
            ReaderTooltipIconButton(
                tooltip = readerString("tooltip_next_result_desc", "Jump to the next search match in the document"),
                onClick = onNext,
                enabled = session.canGoToNextSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("desktop_next_search_result", "Next search result"))
            }
        }
    }
}

@Composable
private fun SharedReaderHighlightSheet(
    session: ReaderSessionState,
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    onDismiss: () -> Unit,
    onColorChange: (HighlightColor) -> Unit,
    onOpenPaletteManager: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit
) {
    val locator = highlight.locator.withFallbacks(
        chapterIndex = highlight.chapterIndex,
        cfi = highlight.cfi,
        textQuote = highlight.text
    )
    val chapterTitle = session.reader.book.chapters
        .getOrNull(locator.chapterIndex ?: highlight.chapterIndex)
        ?.title
        ?: readerString("chapter_number_format", "Chapter %1\$d", (locator.chapterIndex ?: highlight.chapterIndex) + 1)
    var noteText by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note.orEmpty()) }

    SharedReaderBottomSheet(
        title = readerString("label_highlight_color", "Highlight"),
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            palette.sanitized().colors.forEach { color ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.color)
                        .clickable { onColorChange(color) },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                width = if (highlight.color == color) 3.dp else 1.dp,
                                color = if (highlight.color == color) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                                },
                                shape = CircleShape
                            )
                    )
                    if (highlight.color == color) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color == HighlightColor.WHITE || color == HighlightColor.YELLOW) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            SharedReaderHighlightPaletteSpectrumButton(
                onClick = onOpenPaletteManager,
                size = 28.dp
            )
        }
        Surface(
            color = highlight.color.color.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, highlight.color.color.copy(alpha = 0.30f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.heightIn(min = 76.dp)) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(highlight.color.color)
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "\"${highlight.text}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SharedReaderBottomSheetToolButton(Icons.Default.ContentCopy, readerString("action_copy", "Copy")) {
                onCopy()
                onDismiss()
            }
            SharedReaderBottomSheetToolButton(Icons.Default.Search, readerString("action_search", "Search")) {
                onSearch()
                onDismiss()
            }
        }
        SharedStableOutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text(readerString("label_note", "Note")) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            selectionKey = highlight.id
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDelete) {
                Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = {
                onSaveNote(noteText)
                onDismiss()
            }) {
                Text(readerString("action_save_note", "Save note"))
            }
        }
    }
}

@Composable
private fun SharedReaderBottomSheetToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SharedReaderAiResultSheet(
    result: ReaderAiResultState,
    onDismiss: () -> Unit
) {
    SharedReaderBottomSheet(
        title = result.title ?: readerString("desktop_ai", "AI"),
        onDismiss = onDismiss
    ) {
        val errorMessage = result.errorMessage
        when {
            result.isLoading && result.text.isBlank() -> Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            else -> {
                if (result.isLoading) {
                    Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SharedMarkdownText(result.text)
            }
        }
    }
}

@Composable
private fun SharedReaderBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    SharedReaderModalLayer(onDismiss = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(40f)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            val sheetHorizontalPadding = 24.dp
            val sheetAvailableWidth = (maxWidth - sheetHorizontalPadding - sheetHorizontalPadding).coerceAtLeast(0.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = sheetHorizontalPadding, vertical = 16.dp)
                    .width(sharedReaderPopupWidth(sheetAvailableWidth))
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderQuickActions(
    toolbarPreferences: ReaderToolbarPreferences,
    bottom: Boolean,
    isBookmarked: Boolean,
    isDarkMode: Boolean,
    isSearchActive: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSearch: () -> Unit,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    session: ReaderSessionState,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsControlsAvailable: Boolean,
    externalLookupAvailable: Boolean
) {
    val tools = readerWorkspaceQuickActionTools(
        toolbarPreferences = toolbarPreferences,
        bottom = bottom,
        aiAvailable = aiByokSettings.areReaderAiFeaturesAvailable,
        cloudTtsAvailable = cloudTtsControlsAvailable && aiByokSettings.isCloudTtsAvailable,
        externalLookupAvailable = externalLookupAvailable
    )
    if (tools.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        tools.forEach { tool ->
            when (tool) {
                ReaderTool.BOOKMARK -> IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = readerString("content_desc_bookmark", "Bookmark")
                    )
                }

                ReaderTool.THEME -> IconButton(onClick = onToggleTheme) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = if (isDarkMode) {
                            readerString("desktop_use_light_theme", "Use light theme")
                        } else {
                            readerString("desktop_use_dark_theme", "Use dark theme")
                        }
                    )
                }

                ReaderTool.SEARCH -> IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = readerString("action_search", "Search")
                    )
                }

                ReaderTool.DICTIONARY -> IconButton(
                    onClick = { onExternalLookup(ReaderExternalLookupAction.DICTIONARY, ReaderContextExtractor.currentPageText(session)) }
                ) {
                    Icon(Icons.Default.Translate, contentDescription = readerString("desktop_external_lookup", "External lookup"))
                }

                ReaderTool.AI_FEATURES -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentPageText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, ReaderContextExtractor.currentPageText(session).take(1200)) }
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = readerString("desktop_define_page", "Define page"))
                    }
                }

                ReaderTool.TTS_CONTROLS -> IconButton(
                    enabled = cloudTtsControlsAvailable && (
                        extrasState.cloudTts.isAvailable ||
                        extrasState.cloudTts.isPlaying ||
                        extrasState.cloudTts.isLoading ||
                        extrasState.cloudTts.isPaused
                    ),
                    onClick = {
                        if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) {
                            onCloudTtsStop()
                        } else {
                            onCloudTtsStart(
                                ReaderTtsReadScope.BOOK,
                                ReaderTtsPlanner.chunksFromCurrentLocation(session)
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) {
                            readerString("desktop_stop_read_aloud", "Stop read aloud")
                        } else {
                            readerString("action_read_aloud", "Read aloud")
                        }
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun SharedReaderControlPanel(
    session: ReaderSessionState,
    toolbarPreferences: ReaderToolbarPreferences,
    appThemeControls: (@Composable () -> Unit)?,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsControlsAvailable: Boolean,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    readerCustomTextureIds: List<String>,
    readerTexturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderAction: (ReaderAction) -> Unit
) {
    val sections = toolbarPreferences.availableReaderControlSections(
        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
        appThemeControlsAvailable = appThemeControls != null
    )
    if (sections.isEmpty()) return
    val defaultSection = sections.first()
    var selectedSection by remember(sections) { mutableStateOf(defaultSection) }
    LaunchedEffect(sections) {
        if (selectedSection !in sections) {
            selectedSection = defaultSection
        }
    }
    val activeSection = selectedSection.takeIf { it in sections } ?: defaultSection

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SharedReaderControlSectionTabs(
            sections = sections,
            activeSection = activeSection,
            onSectionSelected = { selectedSection = it }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SharedUiTokens.contentGap)
        ) {
            item {
                when (activeSection) {
                    ReaderControlSection.APP_THEME -> appThemeControls?.invoke()

                    ReaderControlSection.FORMAT -> SharedReaderFormatControls(
                        settings = session.reader.settings,
                        onPickCustomFont = onPickCustomFont,
                        customFonts = customFonts,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.THEME -> SharedReaderThemeControls(
                        settings = session.reader.settings,
                        customTextureIds = readerCustomTextureIds,
                        onImportTexture = onImportReaderTexture,
                        customThemes = customReaderThemes,
                        onCustomThemesChange = onCustomReaderThemesChange,
                        texturePreviewContent = readerTexturePreviewContent,
                        onSettingsChange = { onReaderAction(ReaderAction.SettingsChanged(it)) }
                    )

                    ReaderControlSection.VISUAL -> SharedReaderVisualOptionsControls(
                        settings = session.reader.settings,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.TTS -> SharedReaderTtsControls(
                        extrasState = extrasState,
                        aiByokSettings = aiByokSettings,
                        toolbarPreferences = toolbarPreferences,
                        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                        onCloudTtsClearCache = onCloudTtsClearCache,
                        onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                        ttsReplacementPreferences = ttsReplacementPreferences,
                        ttsReplacementBookId = ttsReplacementBookId,
                        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                    )

                }
            }
        }
    }
}

@Composable
private fun SharedReaderControlSectionTabs(
    sections: List<ReaderControlSection>,
    activeSection: ReaderControlSection,
    onSectionSelected: (ReaderControlSection) -> Unit
) {
    val activeIndex = sections.indexOf(activeSection).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        sections.forEach { section ->
            Tab(
                selected = activeSection == section,
                onClick = { onSectionSelected(section) },
                icon = {
                    Icon(
                        section.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        section.localizedTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

private enum class ReaderControlSection {
    APP_THEME,
    FORMAT,
    THEME,
    VISUAL,
    TTS
}

private fun ReaderControlSection.icon(): ImageVector {
    return when (this) {
        ReaderControlSection.APP_THEME -> Icons.Default.Palette
        ReaderControlSection.FORMAT -> Icons.Default.TextFields
        ReaderControlSection.THEME -> Icons.Default.Palette
        ReaderControlSection.VISUAL -> Icons.Default.Tune
        ReaderControlSection.TTS -> Icons.AutoMirrored.Filled.VolumeUp
    }
}

@Composable
private fun ReaderControlSection.localizedTitle(): String {
    return when (this) {
        ReaderControlSection.APP_THEME -> readerString("app_theme_title", "App theme")
        ReaderControlSection.FORMAT -> readerString("desktop_typography", "Typography")
        ReaderControlSection.THEME -> readerString("reading_themes", "Reading Themes")
        ReaderControlSection.VISUAL -> readerString("visual_options_title", "Visual")
        ReaderControlSection.TTS -> readerString("menu_tts_settings", "TTS")
    }
}

private fun ReaderToolbarPreferences.availableReaderControlSections(
    cloudTtsControlsAvailable: Boolean,
    appThemeControlsAvailable: Boolean = false
): List<ReaderControlSection> {
    return buildList {
        if (isVisible(ReaderTool.FORMAT)) add(ReaderControlSection.FORMAT)
        if (isVisible(ReaderTool.THEME)) add(ReaderControlSection.THEME)
        if (appThemeControlsAvailable) add(ReaderControlSection.APP_THEME)
        if (isVisible(ReaderTool.VISUAL_OPTIONS) || isVisible(ReaderTool.READING_MODE)) add(ReaderControlSection.VISUAL)
        if (
            (cloudTtsControlsAvailable && (
                isVisible(ReaderTool.TTS_CONTROLS) ||
                    isVisible(ReaderTool.TTS_SETTINGS)
                )) ||
            isVisible(ReaderTool.TTS_REPLACEMENTS)
        ) {
            add(ReaderControlSection.TTS)
        }
    }
}

@Composable
fun SharedReaderFormatControls(
    settings: ReaderSettings,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    onReaderAction(ReaderAction.SettingsChanged(settings.resetReaderFormatSettings()))
                }
            ) {
                Text(readerString("action_reset", "Reset"))
            }
        }

        SharedReaderPanelSection(readerString("section_font_alignment", "Font & Alignment")) {
                val customFontName = settings.customFontPath
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.isNotBlank() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customFontName ?: settings.fontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(readerString("select_font", "Font"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        enabled = onPickCustomFont != null,
                        onClick = {
                            onPickCustomFont?.invoke()?.takeIf { it.isNotBlank() }?.let { path ->
                                onReaderAction(
                                    ReaderAction.SettingsChanged(
                                        settings.copy(
                                            fontFamily = path.substringAfterLast('/').substringAfterLast('\\'),
                                            customFontPath = path
                                        )
                                    )
                                )
                            }
                        }
                    ) {
                        Text(readerString("action_choose", "Choose"))
                    }
                }

                SharedReaderChoiceRow {
                    val fontFamilies = listOf(
                        "Default" to readerString("label_default", "Default"),
                        "Serif" to readerString("font_serif", "Serif"),
                        "Sans" to readerString("font_sans", "Sans"),
                        "Mono" to readerString("font_mono", "Mono")
                    )
                    fontFamilies.forEach { (family, label) ->
                        FilterChip(
                            selected = settings.customFontPath == null && settings.fontFamily == family,
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = family, customFontPath = null))
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                    if (settings.customFontPath != null) {
                        TextButton(
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = "Default", customFontPath = null))
                                )
                            }
                        ) {
                            Text(readerString("action_clear", "Clear"))
                        }
                    }
                }

                val activeCustomFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
                if (activeCustomFonts.isNotEmpty()) {
                    Text(
                        readerString("desktop_imported_fonts", "Imported fonts"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SharedReaderChoiceRow {
                        activeCustomFonts.forEach { font ->
                            FilterChip(
                                selected = settings.customFontPath == font.path,
                                onClick = {
                                    onReaderAction(
                                        ReaderAction.SettingsChanged(
                                            settings.copy(
                                                fontFamily = font.displayName,
                                                customFontPath = font.path
                                            )
                                        )
                                    )
                                },
                                label = { Text(font.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.START,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.START)))
                        },
                        label = { Text(readerString("label_left", "Left")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.RIGHT,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.RIGHT)))
                        },
                        label = { Text(readerString("label_right", "Right")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY)))
                        },
                        label = { Text(readerString("label_justify", "Justify")) }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.CENTER)))
                        },
                        label = { Text(readerString("desktop_align_center", "Center")) }
                    )
                }
            }

            SharedReaderPanelSection(readerString("desktop_layout_spacing", "Layout & Spacing")) {
                SharedReaderSettingSlider(
                    label = readerString("label_font_size", "Font size"),
                    value = settings.fontSize.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(fontSize = value.roundToInt())))
                    },
                    valueRange = 14f..30f,
                    valueLabel = settings.fontSize.toString(),
                    stepSize = 1f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_line_height", "Line height"),
                    value = settings.lineSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(lineSpacing = value)))
                    },
                    valueRange = 1.1f..2.1f,
                    valueLabel = "${settings.lineSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_paragraph_gap", "Paragraph gap"),
                    value = settings.paragraphSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(paragraphSpacing = value)))
                    },
                    valueRange = 0.5f..2.5f,
                    valueLabel = "${settings.paragraphSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_image_size", "Image size"),
                    value = settings.imageScale,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(imageScale = value)))
                    },
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${settings.imageScale.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_horizontal_margin", "Horizontal margin"),
                    value = settings.resolvedHorizontalMargin.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.withHorizontalReaderMargin(value.roundToInt())
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedHorizontalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = readerString("label_vertical_margin", "Vertical margin"),
                    value = settings.resolvedVerticalMargin.toFloat(),
                    onValueChange = { value ->
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.withVerticalReaderMargin(value.roundToInt())
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedVerticalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                if (settings.shouldShowPageWidthFormatControl()) {
                    SharedReaderSettingSlider(
                        label = readerString("desktop_page_width", "Page width"),
                        value = settings.pageWidth.toFloat(),
                        onValueChange = { value ->
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageWidth = value.roundToInt())))
                        },
                        valueRange = 520f..1100f,
                        valueLabel = settings.pageWidth.toString(),
                        stepSize = 20f,
                        formatValue = { it.roundToInt().toString() }
                    )
                }
        }
    }
}

@Composable
fun SharedReaderThemeControls(
    settings: ReaderSettings,
    builtInThemes: List<ReaderTheme> = BuiltInReaderThemes,
    customThemes: List<ReaderTheme> = emptyList(),
    onCustomThemesChange: ((List<ReaderTheme>) -> Unit)? = null,
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)? = null,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val activeCustomThemes = remember(customThemes) { customThemes.sanitizeCustomReaderThemes() }
    val allThemes = remember(builtInThemes, activeCustomThemes) { builtInThemes + activeCustomThemes }
    val selectedTheme = allThemes.firstOrNull { it.id == settings.themeId }
    var textured by remember(settings.themeId, settings.textureId, builtInThemes, activeCustomThemes) {
        mutableStateOf((selectedTheme?.textureId ?: settings.textureId) != null)
    }
    var editingColorTarget by remember { mutableStateOf<ReaderThemeColorTarget?>(null) }
    var themeBuilderState by remember { mutableStateOf<ReaderCustomThemeBuilderState?>(null) }
    val activeBuiltInThemes = builtInThemes.filter { (it.textureId != null) == textured }
    val activeSavedThemes = activeCustomThemes.filter { (it.textureId != null) == textured }
    val visibleCustomTextureIds = remember(customTextureIds, settings.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            settings.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection(readerString("reading_themes", "Reading Themes")) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = !textured,
                    onClick = { textured = false },
                    label = { Text(readerString("desktop_solid", "Solid")) }
                )
                FilterChip(
                    selected = textured,
                    onClick = { textured = true },
                    label = { Text(readerString("theme_textured", "Textured")) }
                )
            }
            activeBuiltInThemes.chunked(3).forEach { rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowThemes.forEach { theme ->
                        SharedReaderThemeChoice(
                            theme = theme,
                            selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                            onSelected = { onSettingsChange(theme.toReaderSettings(settings)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowThemes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            SharedReaderCustomThemeSection(
                activeThemes = activeSavedThemes,
                currentThemeId = settings.themeId,
                canEditThemes = onCustomThemesChange != null,
                onCreateTheme = {
                    themeBuilderState = ReaderCustomThemeBuilderState(
                        initialTheme = null,
                        isTextured = textured
                    )
                },
                onThemeSelected = { theme -> onSettingsChange(theme.toReaderSettings(settings)) },
                onThemeEdit = { theme ->
                    themeBuilderState = ReaderCustomThemeBuilderState(
                        initialTheme = theme,
                        isTextured = theme.textureId != null
                    )
                },
                onThemeDelete = { theme ->
                    val updated = activeCustomThemes.filterNot { it.id == theme.id }.sanitizeCustomReaderThemes()
                    onCustomThemesChange?.invoke(updated)
                    if (settings.themeId == theme.id) {
                        builtInThemes.firstOrNull()?.let { fallback ->
                            onSettingsChange(fallback.toReaderSettings(settings))
                        }
                    }
                }
            )
        }

        SharedReaderPanelSection(readerString("desktop_custom_colors", "Custom colors")) {
            val backgroundColor = settings.readerBackgroundColor(allThemes)
            val textColor = settings.readerTextColor(allThemes)
            Surface(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                color = backgroundColor,
                contentColor = textColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(readerString("desktop_custom_theme_preview", "Custom theme preview"), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(readerString("desktop_page_and_text_colors", "Page and text colors"), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SharedReaderThemeColorButton(
                    label = readerString("desktop_page", "Page"),
                    color = backgroundColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.BACKGROUND },
                    modifier = Modifier.weight(1f)
                )
                SharedReaderThemeColorButton(
                    label = readerString("content_desc_text", "Text"),
                    color = textColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.TEXT },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (textured) {
            SharedReaderPanelSection(readerString("theme_texture", "Texture")) {
                val textureChoices = buildList {
                    add(SharedReaderTextureChoiceModel(textureId = null, label = readerString("label_none", "None")))
                    if (onImportTexture != null) {
                        add(
                            SharedReaderTextureChoiceModel(
                                textureId = null,
                                label = readerString("action_import", "Import"),
                                icon = Icons.Default.Add,
                                isImportAction = true
                            )
                        )
                    }
                    ReaderTexture.entries.forEach { texture ->
                        add(SharedReaderTextureChoiceModel(textureId = texture.id, label = texture.displayName))
                    }
                    visibleCustomTextureIds.forEach { textureId ->
                        add(SharedReaderTextureChoiceModel(textureId = textureId, label = readerTextureDisplayName(textureId)))
                    }
                }
                textureChoices.chunked(3).forEach { rowChoices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowChoices.forEach { choice ->
                            SharedReaderTextureChoice(
                                choice = choice,
                                selected = !choice.isImportAction && settings.textureId == choice.textureId,
                                texturePreviewContent = texturePreviewContent,
                                onSelected = {
                                    if (choice.isImportAction) {
                                        onImportTexture?.invoke(settings)?.let(onSettingsChange)
                                    } else {
                                        onSettingsChange(settings.copy(textureId = choice.textureId))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowChoices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (settings.textureId != null) {
                    SharedReaderSettingSlider(
                        label = readerString("desktop_texture_strength", "Texture strength"),
                        value = settings.textureAlpha.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(textureAlpha = value))
                        },
                        valueRange = 0f..1f,
                        valueLabel = "${(settings.textureAlpha.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        stepSize = 0.01f,
                        formatValue = { "${(it.coerceIn(0f, 1f) * 100).roundToInt()}%" }
                    )
                }
            }
        }
    }

    editingColorTarget?.let { target ->
        val backgroundColor = settings.readerBackgroundColor(allThemes)
        val textColor = settings.readerTextColor(allThemes)
        val initialColor = when (target) {
            ReaderThemeColorTarget.BACKGROUND -> backgroundColor
            ReaderThemeColorTarget.TEXT -> textColor
        }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = target.localizedTitle(),
            onDismiss = { editingColorTarget = null },
            onSave = { color ->
                val nextBackground = if (target == ReaderThemeColorTarget.BACKGROUND) color else backgroundColor
                val nextText = if (target == ReaderThemeColorTarget.TEXT) color else textColor
                onSettingsChange(
                    settings.copy(
                        themeId = ReaderCustomThemeId,
                        darkMode = nextBackground.luminance() < 0.45f,
                        backgroundColorArgb = nextBackground.toArgb().toLong(),
                        textColorArgb = nextText.toArgb().toLong()
                    )
                )
                editingColorTarget = null
            }
        ) { color ->
            val previewBackground = if (target == ReaderThemeColorTarget.BACKGROUND) color else backgroundColor
            val previewText = if (target == ReaderThemeColorTarget.TEXT) color else textColor
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(10.dp),
                color = previewBackground,
                contentColor = previewText,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(readerString("theme_color_live_preview", "Live preview"), fontWeight = FontWeight.Bold)
                    Text(readerString("desktop_page_and_text_colors", "Page and text colors"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    themeBuilderState?.let { builderState ->
        SharedReaderCustomThemeDialog(
            initialTheme = builderState.initialTheme,
            isTexturedMode = builderState.isTextured,
            customThemes = activeCustomThemes,
            customTextureIds = visibleCustomTextureIds,
            onImportTexture = onImportTexture,
            texturePreviewContent = texturePreviewContent,
            onDismiss = { themeBuilderState = null },
            onSave = { theme ->
                val updated = if (builderState.initialTheme != null) {
                    activeCustomThemes.map { if (it.id == theme.id) theme else it }
                } else {
                    activeCustomThemes + theme.copy(
                        id = nextReaderCustomThemeId(activeCustomThemes),
                        isCustom = true
                    )
                }.sanitizeCustomReaderThemes()
                val savedTheme = updated.firstOrNull { it.id == theme.id } ?: updated.lastOrNull() ?: theme
                onCustomThemesChange?.invoke(updated)
                onSettingsChange(savedTheme.toReaderSettings(settings))
                themeBuilderState = null
            }
        )
    }
}

private data class SharedReaderTextureChoiceModel(
    val textureId: String?,
    val label: String,
    val icon: ImageVector? = null,
    val isImportAction: Boolean = false
)

@Composable
private fun SharedReaderTextureChoice(
    choice: SharedReaderTextureChoiceModel,
    selected: Boolean,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelected,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    choice.textureId != null && texturePreviewContent != null -> {
                        texturePreviewContent(choice.textureId, Modifier.fillMaxSize())
                    }
                    choice.textureId != null -> {
                        SharedReaderTextureFallbackPreview(choice.textureId, Modifier.fillMaxSize())
                    }
                    else -> {
                        val previewColor = if (choice.isImportAction) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(previewColor),
                            contentAlignment = Alignment.Center
                        ) {
                            choice.icon?.let { icon ->
                                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Text(
                choice.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedReaderTextureFallbackPreview(
    textureId: String,
    modifier: Modifier = Modifier
) {
    val texture = ReaderTexture.entries.firstOrNull { it.id == textureId }
    val baseColor = when (texture) {
        ReaderTexture.NATURAL_BLACK,
        ReaderTexture.GREY_WASH,
        ReaderTexture.CLASSY_FABRIC,
        ReaderTexture.SLATE -> Color(0xFF2C2C2C)
        ReaderTexture.RETINA_WOOD,
        ReaderTexture.LIGHT_VENEER -> Color(0xFFF0D4AD)
        ReaderTexture.CANVAS -> Color(0xFFE9E2D2)
        ReaderTexture.EINK -> Color(0xFFF3F3EE)
        ReaderTexture.RETRO_INTRO -> Color(0xFFF5DFB6)
        else -> Color(0xFFF7F1E5)
    }
    val accentColor = if (baseColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    baseColor,
                    baseColor.copy(alpha = 0.84f),
                    accentColor
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Aa",
            color = if (baseColor.luminance() > 0.5f) Color(0xFF24231F) else Color(0xFFEDE7DA),
            fontWeight = FontWeight.Bold
        )
    }
}

private const val ReaderCustomThemeId = "custom_reader"

private data class ReaderCustomThemeBuilderState(
    val initialTheme: ReaderTheme?,
    val isTextured: Boolean
)

@Composable
private fun SharedReaderCustomThemeSection(
    activeThemes: List<ReaderTheme>,
    currentThemeId: String?,
    canEditThemes: Boolean,
    onCreateTheme: () -> Unit,
    onThemeSelected: (ReaderTheme) -> Unit,
    onThemeEdit: (ReaderTheme) -> Unit,
    onThemeDelete: (ReaderTheme) -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            readerString("theme_my_themes", "My themes"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (canEditThemes) {
            IconButton(onClick = onCreateTheme, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = readerString("theme_new", "New"))
            }
        }
    }
    if (activeThemes.isEmpty()) {
        Text(
            readerString("theme_no_custom", "No custom themes yet"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        activeThemes.chunked(3).forEach { rowThemes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowThemes.forEach { theme ->
                    SharedReaderThemeChoice(
                        theme = theme,
                        selected = currentThemeId == theme.id,
                        onSelected = { onThemeSelected(theme) },
                        onEdit = if (canEditThemes) ({ onThemeEdit(theme) }) else null,
                        onDelete = if (canEditThemes) ({ onThemeDelete(theme) }) else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowThemes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SharedReaderCustomThemeDialog(
    initialTheme: ReaderTheme?,
    isTexturedMode: Boolean,
    customThemes: List<ReaderTheme>,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    texturePreviewContent: (@Composable (String, Modifier) -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (ReaderTheme) -> Unit
) {
    val defaultName = if (isTexturedMode) {
        readerString("theme_custom_textured_default", "Custom textured")
    } else {
        readerString("theme_custom_solid_default", "Custom solid")
    }
    val dialogCustomTextureIds = remember(customTextureIds, initialTheme?.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            initialTheme?.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }
    var name by remember(initialTheme?.id, isTexturedMode) { mutableStateOf(initialTheme?.name ?: defaultName) }
    var backgroundColor by remember(initialTheme?.id) { mutableStateOf(initialTheme?.backgroundColor ?: Color(0xFFF5F5F5)) }
    var textColor by remember(initialTheme?.id) { mutableStateOf(initialTheme?.textColor ?: Color(0xFF111111)) }
    var textureId by remember(initialTheme?.id, dialogCustomTextureIds) {
        mutableStateOf(initialTheme?.textureId ?: dialogCustomTextureIds.firstOrNull())
    }
    var editingColorTarget by remember { mutableStateOf<ReaderThemeColorTarget?>(null) }
    val contrast = readerThemeContrastRatio(backgroundColor, textColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialTheme == null) {
                    readerString("theme_new", "New theme")
                } else {
                    readerString("theme_edit", "Edit theme")
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SharedStableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(readerString("theme_name", "Theme name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    selectionKey = initialTheme?.id ?: defaultName
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().height(116.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    contentColor = textColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (isTexturedMode && textureId != null) {
                            if (texturePreviewContent != null) {
                                texturePreviewContent(textureId.orEmpty(), Modifier.fillMaxSize())
                            } else {
                                SharedReaderTextureFallbackPreview(textureId.orEmpty(), Modifier.fillMaxSize())
                            }
                            Box(Modifier.fillMaxSize().background(backgroundColor.copy(alpha = 0.54f)))
                        }
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(readerString("theme_preview_quote", "Reading should feel easy."), fontWeight = FontWeight.SemiBold)
                            Text(readerString("theme_preview_author", "Theme preview"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (contrast < 4.5f) {
                    Text(
                        readerString("theme_low_contrast_warning", "Low contrast may be hard to read."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SharedReaderThemeColorButton(
                        label = readerString("theme_page_color", "Page color"),
                        color = backgroundColor,
                        onClick = { editingColorTarget = ReaderThemeColorTarget.BACKGROUND },
                        modifier = Modifier.weight(1f)
                    )
                    SharedReaderThemeColorButton(
                        label = readerString("theme_text_color", "Text color"),
                        color = textColor,
                        onClick = { editingColorTarget = ReaderThemeColorTarget.TEXT },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isTexturedMode) {
                    Text(
                        readerString("theme_select_custom_texture", "Select texture"),
                        style = MaterialTheme.typography.labelMedium
                    )
                    val textureChoices = buildList {
                        if (onImportTexture != null) {
                            add(
                                SharedReaderTextureChoiceModel(
                                    textureId = null,
                                    label = readerString("action_import", "Import"),
                                    icon = Icons.Default.Add,
                                    isImportAction = true
                                )
                            )
                        }
                        ReaderTexture.entries.forEach { texture ->
                            add(SharedReaderTextureChoiceModel(textureId = texture.id, label = texture.displayName))
                        }
                        dialogCustomTextureIds.forEach { importedTextureId ->
                            add(
                                SharedReaderTextureChoiceModel(
                                    textureId = importedTextureId,
                                    label = readerTextureDisplayName(importedTextureId)
                                )
                            )
                        }
                    }
                    textureChoices.chunked(3).forEach { rowChoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            rowChoices.forEach { choice ->
                                SharedReaderTextureChoice(
                                    choice = choice,
                                    selected = !choice.isImportAction && textureId == choice.textureId,
                                    texturePreviewContent = texturePreviewContent,
                                    onSelected = {
                                        if (choice.isImportAction) {
                                            val imported = onImportTexture?.invoke(
                                                ReaderSettings(
                                                    textureId = textureId,
                                                    backgroundColorArgb = backgroundColor.toArgb().toLong(),
                                                    textColorArgb = textColor.toArgb().toLong()
                                                )
                                            )
                                            textureId = imported?.textureId ?: textureId
                                        } else {
                                            textureId = choice.textureId
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowChoices.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ReaderTheme(
                            id = initialTheme?.id ?: nextReaderCustomThemeId(customThemes),
                            name = name.trim().ifBlank { defaultName },
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            isDark = backgroundColor.luminance() < 0.5f,
                            textureId = if (isTexturedMode) textureId else null,
                            isCustom = true
                        )
                    )
                }
            ) {
                Text(readerString("action_save", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )

    editingColorTarget?.let { target ->
        SharedHsvColorPickerDialog(
            initialColor = if (target == ReaderThemeColorTarget.BACKGROUND) backgroundColor else textColor,
            title = target.localizedTitle(),
            onDismiss = { editingColorTarget = null },
            onSave = { color ->
                if (target == ReaderThemeColorTarget.BACKGROUND) {
                    backgroundColor = color
                } else {
                    textColor = color
                }
                editingColorTarget = null
            }
        )
    }
}

private fun nextReaderCustomThemeId(customThemes: List<ReaderTheme>): String {
    val usedIds = customThemes.mapTo(mutableSetOf()) { it.id }
    var index = customThemes.size + 1
    while ("reader_theme_$index" in usedIds) {
        index += 1
    }
    return "reader_theme_$index"
}

private fun readerThemeContrastRatio(color1: Color, color2: Color): Float {
    val l1 = maxOf(color1.luminance(), color2.luminance())
    val l2 = minOf(color1.luminance(), color2.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

private enum class ReaderThemeColorTarget {
    BACKGROUND,
    TEXT
}

@Composable
private fun ReaderThemeColorTarget.localizedTitle(): String {
    return when (this) {
        ReaderThemeColorTarget.BACKGROUND -> readerString("theme_page_color", "Page color")
        ReaderThemeColorTarget.TEXT -> readerString("theme_text_color", "Text color")
    }
}

@Composable
private fun SharedReaderThemeColorButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(10.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                label,
                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ReaderSettings.readerBackgroundColor(themes: List<ReaderTheme>): Color {
    return backgroundColorArgb?.toComposeColor()
        ?: themes.firstOrNull { it.id == themeId }?.backgroundColor?.takeIf { it.isSpecified }
        ?: if (darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
}

private fun ReaderSettings.readerTextColor(themes: List<ReaderTheme>): Color {
    return textColorArgb?.toComposeColor()
        ?: themes.firstOrNull { it.id == themeId }?.textColor?.takeIf { it.isSpecified }
        ?: if (darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
}

@Composable
fun SharedReaderVisualOptionsControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection(readerString("label_reading", "Reading")) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.PAGINATED && !settings.rightToLeftPagination,
                    onClick = {
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(
                                    readingMode = ReaderReadingMode.PAGINATED,
                                    rightToLeftPagination = false
                                )
                            )
                        )
                    },
                    label = { Text(readerString("menu_reading_mode_paginated", "Paginated (left-to-right)")) }
                )
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.PAGINATED && settings.rightToLeftPagination,
                    onClick = {
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(
                                    readingMode = ReaderReadingMode.PAGINATED,
                                    rightToLeftPagination = true
                                )
                            )
                        )
                    },
                    label = { Text(readerString("menu_right_to_left_pagination", "Paginated (right-to-left)")) }
                )
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                    onClick = {
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                    },
                    label = { Text(readerString("menu_reading_mode_vertical", "Vertical")) }
                )
            }
            if (settings.readingMode == ReaderReadingMode.PAGINATED) {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.pageSpreadMode == ReaderPageSpreadMode.SINGLE,
                        onClick = {
                            onReaderAction(
                                ReaderAction.SettingsChanged(settings.copy(pageSpreadMode = ReaderPageSpreadMode.SINGLE))
                            )
                        },
                        label = { Text(readerString("visual_options_pdf_spread_single", "Single page")) }
                    )
                    FilterChip(
                        selected = settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE,
                        onClick = {
                            onReaderAction(
                                ReaderAction.SettingsChanged(settings.copy(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE))
                            )
                        },
                        label = { Text(readerString("visual_options_pdf_spread_two", "Two pages")) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedReaderTtsControls(
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    cloudTtsControlsAvailable: Boolean,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (
            cloudTtsControlsAvailable &&
            (toolbarPreferences.isVisible(ReaderTool.TTS_CONTROLS) || toolbarPreferences.isVisible(ReaderTool.TTS_SETTINGS))
        ) {
            SharedReaderPanelSection(readerString("credits_cloud_tts_title", "Cloud TTS")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            extrasState.cloudTts.isLoading -> readerString("desktop_preparing_audio", "Preparing audio")
                            extrasState.cloudTts.isPaused -> readerString("desktop_paused", "Paused")
                            extrasState.cloudTts.isPlaying -> readerString("label_reading", "Reading")
                            settings.isCloudTtsAvailable -> readerString("desktop_cloud_tts_ready", "Ready")
                            settings.serverBackedReaderAiFeatures -> readerString("desktop_cloud_tts_needs_signed_in_credits", "Needs signed-in credits")
                            else -> readerString("desktop_cloud_tts_needs_gemini", "Needs Gemini key")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    val errorMessage = extrasState.cloudTts.errorMessage?.takeIf { it.isNotBlank() }
                    val statusMessage = extrasState.cloudTts.progress.currentPositionLabel
                        ?: extrasState.cloudTts.statusMessage?.takeIf { it.isNotBlank() }
                    when {
                        errorMessage != null -> Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        statusMessage != null -> Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(readerString("desktop_cloud_tts_voice", "Cloud TTS voice"), fontWeight = FontWeight.SemiBold)
                    if (ttsBusy) {
                        Text(
                            readerString("desktop_stop_reading_change_voices", "Stop reading to change voices."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        ReaderCloudTtsVoices.forEach { voice ->
                            FilterChip(
                                selected = settings.ttsSpeakerId == voice.id,
                                enabled = !ttsBusy,
                                onClick = { onCloudTtsVoiceChange(voice.id) },
                                label = {
                                    Column {
                                        Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            voice.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            )
                        }
                    }
                    val cacheSummary = extrasState.cloudTts.cacheSummary
                    if (cacheSummary.hasCachedAudio) {
                        Text(
                            readerString("desktop_cache_format", "Cache: %1\$s", cacheSummary.currentVoiceLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cacheSummary.hasCurrentVoiceCachedAudio) {
                            TextButton(onClick = onCloudTtsClearCache) {
                                Text(readerString("desktop_clear_voice_cache", "Clear voice cache"))
                            }
                        }
                    }
                }
            }
        }

        if (toolbarPreferences.isVisible(ReaderTool.TTS_REPLACEMENTS)) {
            SharedReaderTtsReplacementControls(
                preferences = ttsReplacementPreferences,
                bookId = ttsReplacementBookId,
                onPreferencesChange = onTtsReplacementPreferencesChange
            )
        }
    }
}

private enum class SharedTtsReplacementScope {
    GLOBAL,
    BOOK
}

@Composable
fun SharedReaderTtsReplacementControls(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    allowBookScope: Boolean = true
) {
    var selectedScope by remember(bookId, allowBookScope) { mutableStateOf(SharedTtsReplacementScope.GLOBAL) }
    val effectiveScope = if (allowBookScope) selectedScope else SharedTtsReplacementScope.GLOBAL
    var editingRuleId by remember(bookId, effectiveScope) { mutableStateOf<String?>(null) }
    var isAddingRule by remember(bookId, effectiveScope) { mutableStateOf(false) }
    val bookSettings = preferences.settingsForBook(bookId)
    val bookRules = preferences.rulesForBook(bookId)

    SharedReaderPanelSection(readerString("menu_tts_word_replacements", "TTS Word Replacements")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(readerString("tts_replacements_replace_only_spoken", "Replace only what is spoken"), fontWeight = FontWeight.SemiBold)
                Text(
                    readerString("tts_replacements_replace_only_spoken_desc", "Reader text, highlights, and locations stay unchanged."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.isEnabled,
                onCheckedChange = { onPreferencesChange(preferences.copy(isEnabled = it)) }
            )
        }

        if (allowBookScope) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.GLOBAL,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.GLOBAL
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text(readerString("tts_replacements_tab_global", "Global")) }
                )
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.BOOK,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.BOOK
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text(readerString("tts_replacements_tab_this_book", "This book")) }
                )
            }
        }

        when (effectiveScope) {
            SharedTtsReplacementScope.GLOBAL -> {
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules + suggestion.asDesktopEditableRule(
                                prefix = "global",
                                existingRules = preferences.globalRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("tts_replacements_add_rule", "Add rule"))
                }
                val editingRule = editingRuleId?.let { id -> preferences.globalRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("global", preferences.globalRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                preferences.globalRules + rule
                            } else {
                                preferences.globalRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.copy(globalRules = updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = preferences.globalRules,
                    emptyText = readerString("tts_replacements_empty_global", "No global replacement rules yet."),
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.copy(
                                globalRules = preferences.globalRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.copy(globalRules = preferences.globalRules.filterNot { it.id == rule.id }))
                    }
                )
            }

            SharedTtsReplacementScope.BOOK -> {
                SharedTtsBookReplacementSettings(
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsInheritedGlobalRules(
                    globalRules = preferences.globalRules,
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            bookRules + suggestion.asDesktopEditableRule(
                                prefix = "book",
                                existingRules = bookRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("tts_replacements_add_book_rule", "Add book rule"))
                }
                val editingRule = editingRuleId?.let { id -> bookRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("book", bookRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                bookRules + rule
                            } else {
                                bookRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.withBookRules(bookId, updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = bookRules,
                    emptyText = readerString("tts_replacements_empty_book", "No book-specific rules yet."),
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.withBookRules(
                                bookId,
                                bookRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.withBookRules(bookId, bookRules.filterNot { it.id == rule.id }))
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementSuggestionsRow(
    onSuggestionClick: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(readerString("tts_replacements_suggestions", "Suggestions"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ReaderTtsReplacementSuggestions.presets.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun SharedTtsBookReplacementSettings(
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(readerString("tts_replacements_use_global_here", "Use global rules here"), modifier = Modifier.weight(1f))
            Switch(
                checked = settings.globalRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(readerString("tts_replacements_enable_book_rules", "Enable book rules"), modifier = Modifier.weight(1f))
            Switch(
                checked = settings.localRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(localRulesEnabled = it)) }
            )
        }
    }
}

@Composable
private fun SharedTtsInheritedGlobalRules(
    globalRules: List<ReaderTtsReplacementRule>,
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(readerString("tts_replacements_inherited_global_rules", "Inherited global rules"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (globalRules.isEmpty()) {
            Text(readerString("tts_replacements_no_global_rules", "No global rules to inherit."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            globalRules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (rule.id in settings.disabledGlobalRuleIds) {
                                readerString("tts_replacements_disabled_for_book", "Disabled for this book")
                            } else {
                                readerString("tts_replacements_allowed_in_book", "Allowed in this book")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.id !in settings.disabledGlobalRuleIds,
                        onCheckedChange = { enabled ->
                            val disabledIds = if (enabled) {
                                settings.disabledGlobalRuleIds - rule.id
                            } else {
                                settings.disabledGlobalRuleIds + rule.id
                            }
                            onSettingsChange(settings.copy(disabledGlobalRuleIds = disabledIds))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementRuleEditor(
    seedRule: ReaderTtsReplacementRule?,
    newRuleId: String,
    onCancel: () -> Unit,
    onSave: (ReaderTtsReplacementRule) -> Unit
) {
    val seedId = seedRule?.id ?: newRuleId
    var from by remember(seedId) { mutableStateOf(seedRule?.from.orEmpty()) }
    var to by remember(seedId) { mutableStateOf(seedRule?.to.orEmpty()) }
    var enabled by remember(seedId) { mutableStateOf(seedRule?.enabled ?: true) }
    var isRegex by remember(seedId) { mutableStateOf(seedRule?.isRegex ?: false) }
    var wholeWord by remember(seedId) { mutableStateOf(seedRule?.wholeWord ?: true) }
    var matchCase by remember(seedId) { mutableStateOf(seedRule?.matchCase ?: false) }
    val defaultPreviewText = readerString("tts_replacements_preview_default", "Dr. Smith met NASA.")
    var previewText by remember(seedId, defaultPreviewText) {
        mutableStateOf(seedRule?.from?.takeIf { it.isNotBlank() } ?: defaultPreviewText)
    }
    val draft = ReaderTtsReplacementRule(
        id = seedId,
        from = from,
        to = to,
        enabled = enabled,
        isRegex = isRegex,
        matchCase = matchCase,
        wholeWord = wholeWord
    )
    val validation = ReaderTtsReplacementEngine.validate(draft)
    val previewOutput = if (validation.isValid) {
        ReaderTtsReplacementEngine.apply(
            text = previewText,
            preferences = ReaderTtsReplacementPreferences(globalRules = listOf(draft.copy(enabled = true)))
        ).text
    } else {
        previewText
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (seedRule == null) {
                    readerString("tts_replacements_new_replacement", "New replacement")
                } else {
                    readerString("tts_replacements_edit_replacement", "Edit replacement")
                },
                fontWeight = FontWeight.SemiBold
            )
            SharedStableOutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text(readerString("tts_replacements_label_replace", "Replace")) },
                modifier = Modifier.fillMaxWidth(),
                isError = !validation.isValid
            )
            if (!validation.isValid && validation.message != null) {
                Text(validation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SharedStableOutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(readerString("tts_replacements_label_speak_as", "Speak as")) },
                modifier = Modifier.fillMaxWidth()
            )
            SharedReaderChoiceRow {
                FilterChip(selected = enabled, onClick = { enabled = !enabled }, label = { Text(readerString("tts_replacements_chip_enabled", "Enabled")) })
                FilterChip(selected = isRegex, onClick = { isRegex = !isRegex }, label = { Text(readerString("tts_replacements_chip_regex", "Regex")) })
                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text(readerString("tts_replacements_chip_whole_word", "Whole word")) })
                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text(readerString("tts_replacements_chip_match_case", "Match case")) })
            }
            SharedStableOutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text(readerString("tts_replacements_label_preview_input", "Preview input")) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(previewOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(readerString("action_cancel", "Cancel")) }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text(readerString("action_save", "Save")) }
            }
        }
    }
}

@Composable
private fun SharedTtsReplacementRuleList(
    rules: List<ReaderTtsReplacementRule>,
    emptyText: String,
    onToggle: (ReaderTtsReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderTtsReplacementRule) -> Unit,
    onDelete: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rules.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rules.forEach { rule ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.desktopSummary(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rule.desktopOptions(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(rule) }) { Text(readerString("action_edit", "Edit")) }
                        TextButton(onClick = { onDelete(rule) }) { Text(readerString("action_delete", "Delete")) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun ReaderTtsReplacementRule.asDesktopEditableRule(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): ReaderTtsReplacementRule {
    return copy(
        id = newSharedReplacementRuleId(prefix, existingRules + this),
        enabled = true
    )
}

@Composable
private fun ReaderTtsReplacementRule.desktopSummary(): String {
    val replacement = to.ifBlank { readerString("tts_replacements_silence", "silence") }
    return readerString("tts_replacements_summary_format", "%1\$s -> %2\$s", from, replacement)
}

@Composable
private fun ReaderTtsReplacementRule.desktopOptions(): String {
    val options = buildList {
        add(
            if (isRegex) {
                readerString("tts_replacements_chip_regex", "Regex")
            } else {
                readerString("tts_replacements_plain_text", "Plain text")
            }
        )
        if (wholeWord) add(readerString("tts_replacements_chip_whole_word", "whole word"))
        if (matchCase) add(readerString("tts_replacements_case_sensitive", "case-sensitive"))
    }
    return options.joinToString(" - ")
}

private fun newSharedReplacementRuleId(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): String {
    val stableSuffix = existingRules.joinToString("|") { it.id }.hashCode().toString().replace("-", "n")
    return "${prefix}_${existingRules.size + 1}_$stableSuffix"
}

@Composable
fun SharedReaderToolbarControls(
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit
) {
    val orderedTools = toolbarPreferences.sanitized().toolOrder
    val toolbarTools = orderedTools.filter { it.category != "Overflow Menu" }
    val moreTools = orderedTools.filter { it.category == "Overflow Menu" }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedToolbarSection(
            title = readerString("toolbar_top_bar", "Top Bar"),
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && !toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_bottom_bar", "Bottom Bar"),
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_more_menu", "More menu"),
            tools = moreTools.filter { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_hidden_tools", "Hidden Tools"),
            tools = orderedTools.filterNot { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
    }
}

@Composable
private fun SharedToolbarSection(
    title: String,
    tools: List<ReaderTool>,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit
) {
    SharedReaderPanelSection(title) {
        if (tools.isEmpty()) {
            Text(readerString("toolbar_no_tools", "No tools"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tools.forEach { tool ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(tool.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = toolbarPreferences.isVisible(tool),
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withVisibility(tool, hidden = toolbarPreferences.isVisible(tool))
                                )
                            },
                            label = { Text(readerString("toolbar_visible", "Visible")) }
                        )
                        FilterChip(
                            selected = toolbarPreferences.isBottom(tool),
                            enabled = tool.category != "Overflow Menu",
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withBottomPlacement(tool, bottom = !toolbarPreferences.isBottom(tool))
                                )
                            },
                            label = { Text(readerString("label_bottom", "Bottom")) }
                        )
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) > 0,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, -1)) }
                        ) {
                            Text(readerString("action_up", "Up"))
                        }
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) in 0 until toolbarPreferences.toolOrder.lastIndex,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, 1)) }
                        ) {
                            Text(readerString("action_down", "Down"))
                        }
                    }
                }
                if (tool != tools.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedReaderPanelSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SharedReaderChoiceRow(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun SharedReaderSettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    stepSize: Float = 0.05f,
    formatValue: ((Float) -> String)? = null,
    debounceMillis: Long = 320L
) {
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(rangeStart, rangeEnd)
        if (stepSize <= 0f) return clamped
        val steps = ((clamped - rangeStart) / stepSize).roundToInt()
        return (rangeStart + steps * stepSize).coerceIn(rangeStart, rangeEnd)
    }

    var draftValue by remember(label, rangeStart, rangeEnd) { mutableFloatStateOf(snap(value)) }
    var pendingCommit by remember(label, rangeStart, rangeEnd) { mutableStateOf<Float?>(null) }
    var isDragging by remember(label, rangeStart, rangeEnd) { mutableStateOf(false) }
    var lastCommitted by remember(label, rangeStart, rangeEnd) { mutableFloatStateOf(snap(value)) }
    val normalizedExternalValue = snap(value)

    LaunchedEffect(normalizedExternalValue) {
        if (pendingCommit == null) {
            draftValue = normalizedExternalValue
            lastCommitted = normalizedExternalValue
        }
    }

    fun commit(next: Float) {
        val snapped = snap(next)
        draftValue = snapped
        if (snapped != lastCommitted) {
            lastCommitted = snapped
            onValueChange(snapped)
        }
    }

    LaunchedEffect(pendingCommit, isDragging) {
        if (isDragging) return@LaunchedEffect
        val pending = pendingCommit ?: return@LaunchedEffect
        delay(debounceMillis)
        commit(pending)
        if (pendingCommit == pending) {
            pendingCommit = null
        }
    }

    fun updateDraft(next: Float) {
        val snapped = snap(next)
        draftValue = snapped
        pendingCommit = snapped
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatValue?.invoke(draftValue) ?: valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    isDragging = false
                    val next = snap(draftValue - stepSize)
                    pendingCommit = null
                    commit(next)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = readerString("desktop_decrease_format", "Decrease %1\$s", label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            ReaderMinimalSlider(
                value = draftValue,
                onValueChange = ::updateDraft,
                onValueChangeStarted = { isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    pendingCommit?.let { commit(it) }
                    pendingCommit = null
                },
                valueRange = valueRange,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                thumbColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    isDragging = false
                    val next = snap(draftValue + stepSize)
                    pendingCommit = null
                    commit(next)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = readerString("desktop_increase_format", "Increase %1\$s", label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedReaderThemeChoice(
    theme: ReaderTheme,
    selected: Boolean,
    onSelected: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val swatch = if (theme.backgroundColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        theme.backgroundColor
    }
    val textColor = if (theme.textColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        theme.textColor
    }
    Column(
        modifier = modifier.clickable(onClick = onSelected),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else swatch,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(32.dp)
                    .background(swatch, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (theme.isCustom && (onEdit != null || onDelete != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = readerString("action_edit", "Edit"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = readerString("action_delete", "Delete"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private const val ReaderGapChromeLogTag = "EpistemeReaderGap"
private const val ReaderChromeWebViewLayoutLogTag = "EpistemeWebViewLayout"
private const val ReaderOpenTraceLogTag = "EpistemeDesktopOpenTrace"
private const val ReaderPositionTraceLogTag = "EpistemeDesktopPositionTrace"
private const val ReaderVerticalRenderedChapterRadius = 2

private fun readerVerticalRenderedChapterRange(chapterIndex: Int, lastChapterIndex: Int): IntRange? {
    if (lastChapterIndex < 0) return null
    val safeChapterIndex = chapterIndex.coerceIn(0, lastChapterIndex)
    return (safeChapterIndex - ReaderVerticalRenderedChapterRadius).coerceAtLeast(0)..
        (safeChapterIndex + ReaderVerticalRenderedChapterRadius).coerceAtMost(lastChapterIndex)
}

private fun logReaderOpenTrace(message: () -> String) {
    logSharedReaderDiagnostic(ReaderOpenTraceLogTag, message)
}

private fun logReaderPositionTrace(message: () -> String) {
    logSharedReaderDiagnostic(ReaderPositionTraceLogTag, message)
}

private fun Long.readerOpenTraceElapsedMs(nowMillis: Long = currentTimestamp()): Long {
    return (nowMillis - this).coerceAtLeast(0L)
}

private fun String.readerOpenTracePreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

private fun ReaderLocator?.readerPositionTraceSummary(maxTextLength: Int = 90): String {
    if (this == null) return "null"
    return "chapter=${chapterIndex ?: "null"} page=${pageIndex ?: "null"} " +
        "offsets=${startOffset ?: "null"}..${endOffset ?: "null"} " +
        "block=${blockIndex ?: "null"} char=${charOffset ?: "null"} " +
        "chapterId=\"${chapterId.orEmpty().readerOpenTracePreview(80)}\" " +
        "href=\"${href.orEmpty().readerOpenTracePreview(120)}\" " +
        "cfi=\"${cfi.orEmpty().readerOpenTracePreview(180)}\" " +
        "text=\"${textQuote.orEmpty().readerOpenTracePreview(maxTextLength)}\""
}

private fun logReaderGapChrome(
    layer: String,
    bounds: Rect,
    details: String = ""
) {
    val message = {
        buildString {
            append("compose_reader layer=")
            append(layer)
            append(" x=")
            append(bounds.left.roundToInt())
            append(" y=")
            append(bounds.top.roundToInt())
            append(" w=")
            append(bounds.width.roundToInt())
            append(" h=")
            append(bounds.height.roundToInt())
            append(" bottom=")
            append(bounds.bottom.roundToInt())
            if (details.isNotBlank()) {
                append(' ')
                append(details)
            }
        }
    }
    logSharedReaderDiagnostic(ReaderGapChromeLogTag, message)
    if (layer == "reader_content_column") {
        logSharedReaderDiagnostic(ReaderChromeWebViewLayoutLogTag, message)
    }
}

@Composable
private fun SharedReaderCompactNavigation(
    session: ReaderSessionState,
    showSlider: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    pageInfoText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageNumberChange: (Int) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { coordinates ->
                logReaderGapChrome(
                    layer = "bottom_nav_row",
                    bounds = coordinates.boundsInWindow(),
                    details = "showSlider=$showSlider pageInfo=${pageInfoText != null} canPrev=$canGoPrevious canNext=$canGoNext"
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderTooltipIconButton(
            tooltip = readerString("desktop_previous_page", "Previous page"),
            enabled = canGoPrevious,
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = readerString("desktop_previous_page", "Previous page"),
                tint = contentColor.copy(alpha = if (canGoPrevious) 0.78f else 0.32f),
                modifier = Modifier.size(22.dp)
            )
        }
        if (showSlider) {
            SharedReaderPageSlider(
                session = session,
                onPageNumberChange = onPageNumberChange,
                contentColor = contentColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                pageInfoText.orEmpty(),
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        ReaderTooltipIconButton(
            tooltip = readerString("desktop_next_page", "Next page"),
            enabled = canGoNext,
            onClick = onNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = readerString("desktop_next_page", "Next page"),
                tint = contentColor.copy(alpha = if (canGoNext) 0.78f else 0.32f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SharedReaderFullscreenNavigation(
    session: ReaderSessionState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageNumberChange: (Int) -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
    onClearJumpHistory: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderSteps = ReaderSpreadLayout.sliderStepCount(totalPages, readerState.settings)
    val sliderMax = sliderSteps.coerceAtLeast(2)
    val currentSliderPosition = ReaderSpreadLayout.sliderPositionForPage(
        pageIndex = readerState.currentPageIndex,
        pageCount = totalPages,
        settings = readerState.settings
    )
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!session.isSearchActive && session.shouldShowJumpHistory) {
                SharedReaderJumpHistoryBar(
                    session = session,
                    onBack = onJumpBack,
                    onForward = onJumpForward,
                    onClear = onClearJumpHistory
                )
                HorizontalDivider(color = contentColor.copy(alpha = 0.14f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_previous_page", "Previous page"),
                    enabled = readerState.canGoPrevious,
                    onClick = onPrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = readerString("desktop_previous_page", "Previous page"),
                        tint = contentColor.copy(alpha = if (readerState.canGoPrevious) 0.78f else 0.32f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                ReaderMinimalSlider(
                    value = currentSliderPosition.toFloat(),
                    onValueChange = { value ->
                        onPageNumberChange(
                            ReaderSpreadLayout.pageNumberForSliderPosition(
                                position = value.roundToInt(),
                                pageCount = totalPages,
                                settings = readerState.settings
                            )
                        )
                    },
                    valueRange = 1f..sliderMax.toFloat(),
                    enabled = sliderSteps > 1,
                    activeColor = contentColor.copy(alpha = 0.68f),
                    inactiveColor = contentColor.copy(alpha = 0.24f),
                    thumbColor = contentColor.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f)
                )
                ReaderTooltipIconButton(
                    tooltip = readerString("desktop_next_page", "Next page"),
                    enabled = readerState.canGoNext,
                    onClick = onNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = readerString("desktop_next_page", "Next page"),
                        tint = contentColor.copy(alpha = if (readerState.canGoNext) 0.78f else 0.32f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedReaderPageSlider(
    session: ReaderSessionState,
    onPageNumberChange: (Int) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val readerState = session.reader
    val totalPages = readerState.pages.size.coerceAtLeast(1)
    val sliderSteps = ReaderSpreadLayout.sliderStepCount(totalPages, readerState.settings)
    val sliderMax = sliderSteps.coerceAtLeast(2)
    val currentSliderPosition = ReaderSpreadLayout.sliderPositionForPage(
        pageIndex = readerState.currentPageIndex,
        pageCount = totalPages,
        settings = readerState.settings
    )
    val pageRangeLabel = ReaderSpreadLayout.pageRangeLabel(readerState.currentPageIndex, totalPages, readerState.settings)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$pageRangeLabel / $totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.72f)
        )
        ReaderMinimalSlider(
            value = currentSliderPosition.toFloat(),
            onValueChange = { value ->
                onPageNumberChange(
                    ReaderSpreadLayout.pageNumberForSliderPosition(
                        position = value.roundToInt(),
                        pageCount = totalPages,
                        settings = readerState.settings
                    )
                )
            },
            valueRange = 1f..sliderMax.toFloat(),
            enabled = sliderSteps > 1,
            activeColor = contentColor.copy(alpha = 0.62f),
            inactiveColor = contentColor.copy(alpha = 0.18f),
            thumbColor = contentColor.copy(alpha = 0.86f),
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedReaderSidebar(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    sections: List<ReaderWorkspaceLeftSection>,
    onGoToChapter: (Int) -> Unit,
    onGoToLocator: (ReaderLocator) -> Unit,
    onGoToBookmark: (ReaderBookmark) -> Unit,
    onDownloadImage: ((ReaderImageReference) -> Unit)?,
    imagePreviewContent: (@Composable (ReaderImageReference, Modifier) -> Unit)?,
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onOpenHighlightPaletteManager: () -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    val tabs = remember(sections) {
        sections
            .filter { it.isReaderNavigationSection() }
            .distinct()
    }
    var selectedSection by remember(tabs) { mutableStateOf(tabs.firstOrNull()) }
    val selectedTabIndex = tabs.indexOf(selectedSection).takeIf { it >= 0 } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        text = {
                            Text(
                                section.readerNavigationTabLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        when (selectedSection) {
            ReaderWorkspaceLeftSection.CONTENTS -> SharedReaderTocTab(
                session = session,
                readerEngine = readerEngine,
                onGoToLocator = onGoToLocator,
                onGoToChapter = onGoToChapter
            )
            ReaderWorkspaceLeftSection.NOTES -> SharedReaderAnnotationsTab(
                session = session,
                onGoToHighlight = onGoToHighlight,
                onEditHighlight = onEditHighlight,
                highlightPalette = highlightPalette,
                onHighlightColorChange = onHighlightColorChange,
                onOpenHighlightPaletteManager = onOpenHighlightPaletteManager,
                onDeleteHighlight = onDeleteHighlight
            )
            ReaderWorkspaceLeftSection.BOOKMARKS -> SharedReaderBookmarksTab(
                session = session,
                onGoToBookmark = onGoToBookmark
            )
            ReaderWorkspaceLeftSection.IMAGES -> SharedReaderImagesTab(
                session = session,
                onGoToImage = onGoToLocator,
                onDownloadImage = onDownloadImage,
                imagePreviewContent = imagePreviewContent
            )
            else -> SharedReaderEmptyNavigation(readerString("desktop_no_navigation_items", "No navigation items"))
        }
    }
}

private fun ReaderWorkspaceLeftSection.isReaderNavigationSection(): Boolean {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS,
        ReaderWorkspaceLeftSection.IMAGES,
        ReaderWorkspaceLeftSection.NOTES,
        ReaderWorkspaceLeftSection.BOOKMARKS -> true
        ReaderWorkspaceLeftSection.PAGES,
        ReaderWorkspaceLeftSection.SEARCH -> false
    }
}

@Composable
private fun SharedReaderTocTab(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onGoToLocator: (ReaderLocator) -> Unit,
    onGoToChapter: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val chapters = session.reader.book.chapters
    val tocEntries = remember(session.reader.book.tableOfContents, chapters) {
        session.reader.book.tableOfContents.ifEmpty {
            chapters.map { chapter ->
                SharedEpubTocEntry(
                    label = chapter.title,
                    href = chapter.baseHref ?: chapter.id,
                    depth = 0
                )
            }
        }
    }
    if (tocEntries.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_table_of_contents", "No table of contents"))
        return
    }

    val allParentIndices = remember(tocEntries) {
        tocEntries.indices.filter { index ->
            val next = tocEntries.getOrNull(index + 1)
            next != null && next.depth > tocEntries[index].depth
        }.toSet()
    }
    var expandedEntryIndices by remember(tocEntries) { mutableStateOf(allParentIndices) }
    val visibleItemInfo by remember(tocEntries) {
        derivedStateOf {
            val result = mutableListOf<Pair<Int, SharedEpubTocEntry>>()
            val visibilityStack = BooleanArray(50) { false }
            visibilityStack[0] = true

            tocEntries.forEachIndexed { index, entry ->
                val depth = entry.depth.coerceIn(0, visibilityStack.lastIndex)
                if (visibilityStack[depth]) {
                    result += index to entry
                    if (depth + 1 < visibilityStack.size) {
                        visibilityStack[depth + 1] = index in expandedEntryIndices
                    }
                } else if (depth + 1 < visibilityStack.size) {
                    visibilityStack[depth + 1] = false
                }
            }
            result
        }
    }
    val currentChapterIndex = session.reader.currentPage?.chapterIndex
    val activeOriginalIndex = remember(tocEntries, chapters, currentChapterIndex) {
        tocEntries.indexOfFirst { entry ->
            val targetChapter = entry.targetChapterIndex(chapters)
            targetChapter == currentChapterIndex
        }.takeIf { it >= 0 } ?: currentChapterIndex?.takeIf { it in tocEntries.indices }
    }

    fun expandParentsFor(originalIndex: Int) {
        var currentDepth = tocEntries.getOrNull(originalIndex)?.depth ?: return
        val nextExpanded = expandedEntryIndices.toMutableSet()
        for (index in originalIndex downTo 0) {
            val entry = tocEntries[index]
            if (entry.depth < currentDepth) {
                nextExpanded += index
                currentDepth = entry.depth
            }
            if (currentDepth == 0) break
        }
        expandedEntryIndices = nextExpanded
    }

    fun locateCurrent() {
        val originalIndex = activeOriginalIndex ?: return
        coroutineScope.launch {
            expandParentsFor(originalIndex)
            repeat(4) {
                val visibleIndex = visibleItemInfo.indexOfFirst { it.first == originalIndex }
                if (visibleIndex >= 0) {
                    listState.animateScrollToItem(visibleIndex)
                    return@launch
                }
                delay(30)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { expandedEntryIndices = allParentIndices }) {
                Text(readerString("action_expand_all", "Expand all"))
            }
            TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                Text(readerString("action_collapse_all", "Collapse all"))
            }
            TextButton(onClick = ::locateCurrent, enabled = activeOriginalIndex != null) {
                Text(readerString("action_locate", "Locate"))
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedAcceleratedLazyWheelScroll(listState)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    visibleItemInfo,
                    key = { _, item -> "${item.first}_${item.second.href}_${item.second.fragmentId.orEmpty()}" }
                ) { _, item ->
                    val (originalIndex, entry) = item
                    val nextItem = tocEntries.getOrNull(originalIndex + 1)
                    val hasChildren = nextItem != null && nextItem.depth > entry.depth
                    val isExpanded = originalIndex in expandedEntryIndices
                    val targetChapterIndex = entry.targetChapterIndex(chapters)
                    val selected = targetChapterIndex == currentChapterIndex

                    SharedReaderTocTreeItem(
                        title = entry.label,
                        pageLabel = targetChapterIndex?.let { readerString("desktop_chapter_short_format", "Ch. %1\$d", it + 1) },
                        depth = entry.depth,
                        isExpanded = isExpanded,
                        hasChildren = hasChildren,
                        isCurrent = selected,
                        onToggleExpand = {
                            expandedEntryIndices = if (isExpanded) {
                                expandedEntryIndices - originalIndex
                            } else {
                                expandedEntryIndices + originalIndex
                            }
                        },
                        onClick = {
                            val chapterIndex = targetChapterIndex
                            if (chapterIndex != null) {
                                val fragment = entry.fragmentId
                                if (fragment.isNullOrBlank()) {
                                    onGoToChapter(chapterIndex)
                                } else {
                                    when (val target = readerEngine.resolveLink(session, "#$fragment", chapterIndex)) {
                                        is ReaderLinkTarget.Internal -> onGoToLocator(target.locator)
                                        else -> onGoToChapter(chapterIndex)
                                    }
                                }
                            }
                        }
                    )
                }
            }
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedReaderTocTreeItem(
    title: String,
    pageLabel: String?,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(start = (depth.coerceAtLeast(0) * 14).dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = hasChildren) { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                title,
                fontWeight = if (isCurrent) FontWeight.Bold else if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pageLabel != null) {
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun SharedEpubTocEntry.targetChapterIndex(
    chapters: List<com.aryan.reader.shared.reader.SharedEpubChapter>
): Int? {
    val targetPath = href.normalizedReaderTocPath()
    return chapters.indexOfFirst { chapter ->
        val chapterPath = chapter.baseHref.orEmpty().normalizedReaderTocPath()
        chapterPath == targetPath ||
            chapterPath.substringAfterLast('/') == targetPath.substringAfterLast('/') ||
            chapter.id == href
    }.takeIf { it >= 0 }
}

private fun String.normalizedReaderTocPath(): String {
    return replace('\\', '/')
        .substringBefore('#')
        .substringBefore('?')
        .trim('/')
}

@Composable
private fun SharedReaderBookmarksTab(
    session: ReaderSessionState,
    onGoToBookmark: (ReaderBookmark) -> Unit
) {
    if (session.bookmarks.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_bookmarks_yet", "No bookmarks yet"))
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedAcceleratedLazyWheelScroll(listState)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(session.bookmarks, key = { it.id }) { bookmark ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGoToBookmark(bookmark) }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(bookmark.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bookmark.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedReaderImagesTab(
    session: ReaderSessionState,
    onGoToImage: (ReaderLocator) -> Unit,
    onDownloadImage: ((ReaderImageReference) -> Unit)?,
    imagePreviewContent: (@Composable (ReaderImageReference, Modifier) -> Unit)?
) {
    val images = remember(session.reader.book, session.reader.pages) {
        session.reader.book.readerImageReferences(session.reader.pages)
    }
    if (images.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("no_images_found", "No images found."))
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedAcceleratedLazyWheelScroll(listState)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGoToImage(image.locator) }
                                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (imagePreviewContent != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.size(width = 48.dp, height = 56.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        imagePreviewContent(image, Modifier.fillMaxSize())
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    image.displayTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(
                                        image.chapterTitle,
                                        image.dimensionLabel,
                                        image.sourceName()
                                    ).joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onDownloadImage != null) {
                                IconButton(
                                    onClick = { onDownloadImage(image) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = readerString("content_desc_download_image", "Download image")
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SharedReaderVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedReaderAnnotationsTab(
    session: ReaderSessionState,
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onOpenHighlightPaletteManager: () -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    if (session.highlights.isEmpty()) {
        SharedReaderEmptyNavigation(readerString("desktop_no_annotations_yet", "No annotations yet"))
    } else {
        val listState = rememberLazyListState()
        var menuExpandedFor by remember { mutableStateOf<UserHighlight?>(null) }
        var deleteConfirmFor by remember { mutableStateOf<UserHighlight?>(null) }
        val colors = highlightPalette.sanitized().colors
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedAcceleratedLazyWheelScroll(listState)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(session.highlights, key = { it.id }) { highlight ->
                        val locator = highlight.locator.withFallbacks(
                            chapterIndex = highlight.chapterIndex,
                            cfi = highlight.cfi,
                            textQuote = highlight.text
                        )
                        val chapterTitle = session.reader.book.chapters
                            .getOrNull(locator.chapterIndex ?: highlight.chapterIndex)
                            ?.title
                            ?: readerString("chapter_number_format", "Chapter %1\$d", (locator.chapterIndex ?: highlight.chapterIndex) + 1)
                        val pageLabel = locator.pageIndex?.let { readerString("pdf_page_short", "Page %1\$d", it + 1) }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onGoToHighlight(highlight) },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(12.dp)
                                                .background(highlight.color.color, RoundedCornerShape(2.dp))
                                        )
                                        Text(
                                            listOfNotNull(chapterTitle, pageLabel).joinToString(" - "),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuExpandedFor = highlight }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_annotation_options", "Annotation options"))
                                        }
                                        DropdownMenu(
                                            expanded = menuExpandedFor == highlight,
                                            onDismissRequest = { menuExpandedFor = null }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(vertical = 8.dp, horizontal = 10.dp)
                                                    .fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                colors.forEach { color ->
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .padding(horizontal = 4.dp)
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(color.color)
                                                            .clickable {
                                                                menuExpandedFor = null
                                                                onHighlightColorChange(highlight, color)
                                                            }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .matchParentSize()
                                                                .border(
                                                                    width = if (highlight.color == color) 3.dp else 1.dp,
                                                                    color = if (highlight.color == color) {
                                                                        MaterialTheme.colorScheme.onSurface
                                                                    } else {
                                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                                                                    },
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                        if (highlight.color == color) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = if (color == HighlightColor.WHITE || color == HighlightColor.YELLOW) Color.Black else Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                SharedReaderHighlightPaletteSpectrumButton(
                                                    onClick = {
                                                        menuExpandedFor = null
                                                        onOpenHighlightPaletteManager()
                                                    },
                                                    size = 28.dp
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (highlight.note.isNullOrBlank()) {
                                                            readerString("menu_add_note", "Add note")
                                                        } else {
                                                            readerString("menu_edit_note", "Edit note")
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    onEditHighlight(highlight)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(readerString("action_delete", "Delete")) },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    deleteConfirmFor = highlight
                                                }
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGoToHighlight(highlight) },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(highlight.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                SharedReaderVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            deleteConfirmFor?.let { highlight ->
                AlertDialog(
                    onDismissRequest = { deleteConfirmFor = null },
                    title = { Text(readerString("desktop_delete_annotation_title", "Delete annotation?")) },
                    text = { Text(readerString("desktop_delete_highlight_desc", "This removes the highlight and its note.")) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteConfirmFor = null
                                onDeleteHighlight(highlight)
                            }
                        ) {
                            Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmFor = null }) {
                            Text(readerString("action_cancel", "Cancel"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedReaderEmptyNavigation(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReaderWorkspaceLeftSection.readerNavigationTabLabel(): String {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS -> readerString("desktop_toc", "TOC")
        ReaderWorkspaceLeftSection.IMAGES -> readerString("tab_images", "Images")
        ReaderWorkspaceLeftSection.NOTES -> readerString("tab_annotations", "Annotations")
        ReaderWorkspaceLeftSection.BOOKMARKS -> readerString("tab_bookmarks", "Bookmarks")
        ReaderWorkspaceLeftSection.PAGES -> readerString("tab_pages", "Pages")
        ReaderWorkspaceLeftSection.SEARCH -> readerString("action_search", "Search")
    }
}

@Composable
private fun SharedReaderHighlightPaletteDialog(
    palette: ReaderHighlightPalette,
    onDismiss: () -> Unit,
    onSave: (ReaderHighlightPalette) -> Unit
) {
    var draftColors by remember(palette) { mutableStateOf(palette.sanitized().colors) }
    var selectedSlotIndex by remember { mutableIntStateOf(0) }

    fun replaceSlot(color: HighlightColor) {
        if (draftColors.isEmpty()) return
        val next = draftColors.toMutableList()
        val slot = selectedSlotIndex.coerceIn(0, next.lastIndex)
        next[slot] = color
        draftColors = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                readerString("dialog_customize_palette", "Customize palette"),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    readerString("palette_tap_slot_to_edit", "Tap a slot to edit it."),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    draftColors.forEachIndexed { index, color ->
                        val selected = index == selectedSlotIndex
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(color.color, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedSlotIndex = index },
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (color == HighlightColor.WHITE) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    readerString("palette_select_color_for_slot", "Select a color for the slot."),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(HighlightColor.entries) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color.color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), CircleShape)
                                .clickable { replaceSlot(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ReaderHighlightPalette(draftColors).sanitized()) }) {
                Text(readerString("action_save", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )
}

@Composable
private fun SharedReaderHighlightPaletteSpectrumButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red,
        Color(0xFFFF7F00),
        Color.Yellow,
        Color.Green,
        Color.Blue,
        Color(0xFF4B0082),
        Color(0xFF8B00FF)
    )
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.sweepGradient(rainbowColors),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private fun Float.formatTwoDecimals(): String {
    val scaled = (this * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

private fun ReaderToolbarPreferences.moveTool(tool: ReaderTool, delta: Int): ReaderToolbarPreferences {
    val order = sanitized().toolOrder.toMutableList()
    val index = order.indexOf(tool)
    if (index < 0) return this
    val target = (index + delta).coerceIn(0, order.lastIndex)
    if (index == target) return this
    val moved = order.removeAt(index)
    order.add(target, moved)
    return withToolOrder(order)
}

private val ReaderSessionState.shouldShowJumpHistory: Boolean
    get() = reader.settings.readingMode != ReaderReadingMode.PAGINATED && jumpHistory.hasJumpTargets

@Composable
private fun SharedReaderJumpHistoryBar(
    session: ReaderSessionState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    val history = session.jumpHistory
    val back = history.backLocator
    val forward = history.forwardLocator
    if (back == null && forward == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onBack,
            enabled = back != null,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = readerString("content_desc_jump_back", "Jump back"))
            Text(
                back?.jumpLabel(session).orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onClear,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_jump_history", "Clear jump history"))
            Text(readerString("action_clear", "Clear"), maxLines = 1)
        }
        TextButton(
            onClick = onForward,
            enabled = forward != null,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                forward?.jumpLabel(session).orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = readerString("content_desc_jump_forward", "Jump forward"))
        }
    }
}

@Composable
private fun ReaderLocator.jumpLabel(session: ReaderSessionState): String {
    val targetPageIndex = pageIndex
    val targetCfi = cfi.orEmpty()
    if (targetPageIndex != null && targetCfi.isBlank()) {
        return readerString("pdf_page_short", "Page %1\$d", targetPageIndex + 1)
    }
    val chapter = chapterIndex
    return if (chapter != null) {
        session.reader.book.chapters.getOrNull(chapter)?.title?.takeIf { it.isNotBlank() }
            ?: readerString("chapter_number_format", "Chapter %1\$d", chapter + 1)
    } else {
        readerString("location", "Location")
    }
}

private fun Long.toComposeColor(): Color {
    val value = this and 0xFFFFFFFFL
    val alpha = ((value shr 24) and 0xFF) / 255f
    val red = ((value shr 16) and 0xFF) / 255f
    val green = ((value shr 8) and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha.takeIf { it > 0f } ?: 1f)
}

@Composable
private fun PaginatedReaderState.pageInfoText(): String {
    val total = pages.size.coerceAtLeast(1)
    val percent = progress.roundToInt().coerceIn(0, 100)
    val mode = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
        readerString("desktop_continuous", "Continuous")
    } else {
        readerString("desktop_page", "Page")
    }
    val current = ReaderSpreadLayout.pageRangeLabel(currentPageIndex, total, settings)
    val chapter = currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
    val pageInfo = readerString("desktop_reader_page_info_format", "%1\$s %2\$s of %3\$d (%4\$d%%)", mode, current, total, percent)
    return listOfNotNull(pageInfo, chapter).joinToString(" - ")
}

private fun PaginatedReaderState.currentPageLocator(): ReaderLocator? {
    val page = currentPage ?: return null
    val chapter = book.chapters.getOrNull(page.chapterIndex)
    return ReaderLocator(
        chapterIndex = page.chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = page.pageIndex,
        startOffset = page.startOffset,
        endOffset = page.endOffset,
        textQuote = page.text.trim().replace(Regex("\\s+"), " ").take(140),
        cfi = "desktop:${page.chapterIndex}:${page.startOffset}:${page.endOffset}"
    )
}
