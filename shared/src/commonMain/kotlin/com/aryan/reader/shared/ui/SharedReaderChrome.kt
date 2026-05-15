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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.aryan.reader.shared.ReaderAutoScrollState
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
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.readerTextureDisplayName
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.reader.PaginatedReaderState
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
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit = {},
    readerTextureDataUri: (String) -> String? = { null },
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    readerContent: @Composable ColumnScope.(
        renderPlan: ReaderContentRenderPlan,
        onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
        onHighlightSelected: (String) -> Unit
    ) -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val byokSettings = aiByokSettings.sanitized()
    val background = settings.backgroundColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val foreground = settings.textColorArgb?.toComposeColor() ?: if (settings.darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
    val pageInfoText = readerState.pageInfoText()
    val shouldShowPageInfo = settings.pageInfoMode != PageInfoMode.HIDDEN
    val activeTtsProgress = readerExtrasState.cloudTts.progress
    val activeTtsChunk = activeTtsProgress.currentChunk
    val activeTtsLocator = activeTtsChunk?.toLocator()
    val ttsRequestId = activeTtsChunk?.let { activeTtsProgress.sessionId + it.index + 1L } ?: 0L
    val navigationLocator = session.navigationLocator ?: session.activeSearchResult?.locator ?: readerState.currentPageLocator()
    val effectiveCloudTtsAvailable = cloudTtsControlsAvailable && byokSettings.isCloudTtsAvailable
    val readerFocusRequester = remember(session.reader.book.id) { FocusRequester() }
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnFullscreenChange by rememberUpdatedState(onFullscreenChange)
    var selectedHighlightId by remember(session.reader.book.id) { mutableStateOf<String?>(null) }
    var sidebarNavigationHighlightId by remember(session.reader.book.id) { mutableStateOf<String?>(null) }
    val selectedHighlight = remember(session.highlights, selectedHighlightId) {
        session.highlights.firstOrNull { it.id == selectedHighlightId }
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
        extrasState = readerExtrasState,
        aiAvailable = byokSettings.areReaderAiFeaturesAvailable,
        cloudTtsAvailable = effectiveCloudTtsAvailable,
        externalLookupAvailable = externalLookupAvailable
    )

    LaunchedEffect(
        readerExtrasState.autoScroll.sanitized(),
        settings.readingMode,
        readerState.currentPageIndex,
        readerState.canGoNext
    ) {
        val autoScroll = readerExtrasState.autoScroll.sanitized()
        if (!autoScroll.enabled || settings.readingMode != ReaderReadingMode.PAGINATED || !readerState.canGoNext) return@LaunchedEffect
        val delayMs = (180_000f / autoScroll.speed).roundToInt().coerceIn(1_200, 12_000)
        delay(delayMs.toLong())
        dispatch(ReaderAction.NextPage)
    }

    LaunchedEffect(session.reader.book.id, settings.readingMode, readerState.currentPageIndex) {
        runCatching { readerFocusRequester.requestFocus() }
    }

    LaunchedEffect(isFullscreen, session.reader.book.id) {
        repeat(if (isFullscreen) 4 else 1) { attempt ->
            delay(if (attempt == 0) 80L else 120L)
            runCatching { readerFocusRequester.requestFocus() }
        }
    }

    val readerPopupActive = selectedHighlight != null || readerExtrasState.aiResult.hasContent
    LaunchedEffect(readerPopupActive, session.reader.book.id) {
        if (!readerPopupActive) {
            delay(120L)
            runCatching { readerFocusRequester.requestFocus() }
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

                    event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                        dispatch(ReaderAction.NextPage)
                        true
                    }

                    event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
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
                onPickCustomFont = onPickCustomFont,
                customFonts = customFonts,
                extrasState = readerExtrasState,
                aiByokSettings = byokSettings,
                cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                onAiAction = onAiAction,
                onCloudTtsStart = onCloudTtsStart,
                onCloudTtsStop = onCloudTtsStop,
                onAutoScrollChange = onAutoScrollChange,
                ttsReplacementPreferences = ttsReplacementPreferences,
                ttsReplacementBookId = ttsReplacementBookId ?: session.reader.book.title,
                onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                highlightPalette = highlightPalette,
                onHighlightPaletteChange = onHighlightPaletteChange,
                readerCustomTextureIds = readerCustomTextureIds,
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
                shape = RoundedCornerShape(6.dp),
                color = background,
                contentColor = foreground,
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, foreground.copy(alpha = 0.12f))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    val showJumpHistory = !session.isSearchActive && session.shouldShowJumpHistory
                    if (showJumpHistory) {
                        SharedReaderJumpHistoryBar(
                            session = session,
                            onBack = { dispatch(ReaderAction.JumpBack) },
                            onForward = { dispatch(ReaderAction.JumpForward) },
                            onClear = { dispatch(ReaderAction.JumpHistoryCleared) }
                        )
                        HorizontalDivider(color = foreground.copy(alpha = 0.12f))
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
                        contentColor = foreground
                    )
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
                backgroundColor = background,
                contentColor = foreground
            )
        }
    ) {
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
                        details = "mode=${settings.readingMode} columnGap=12 pageInfoTop=${shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.TOP}"
                    )
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isFullscreen && shouldShowPageInfo && settings.pageInfoPosition == PageInfoPosition.TOP) {
                Text(pageInfoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val documentLayoutSignature = settings.layoutSignature()
            val textureDataUri = remember(settings.textureId) {
                settings.textureId?.let(readerTextureDataUri)
            }
            val navigationTarget = ReaderContentNavigationTarget(
                locator = navigationLocator,
                requestId = session.navigationRequestId,
                readingMode = settings.readingMode,
                autoScroll = readerExtrasState.autoScroll.sanitized(),
                ttsLocator = activeTtsLocator,
                ttsRequestId = ttsRequestId
            )
            val renderPlan = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                val appearanceSignature = settings.appearanceSignature()
                val appearanceScript = remember(appearanceSignature, textureDataUri) {
                    ReaderHtmlDocumentBuilder.appearanceUpdateScript(
                        settings = settings,
                        textureDataUri = textureDataUri
                    )
                }
                // Keep the initial locator in the document so its first position report is not the top of the book.
                val html = remember(
                    readerState.book,
                    documentLayoutSignature,
                    session.searchQuery,
                    session.searchOptions,
                    readerState.pages,
                    byokSettings.areReaderAiFeaturesAvailable,
                    effectiveCloudTtsAvailable,
                    externalLookupAvailable
                ) {
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
                        textureDataUri = textureDataUri
                    )
                }
                ReaderContentRenderPlan.WebDocument(
                    html = html,
                    appearanceScript = appearanceScript,
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
                }
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { onReaderAction(ReaderAction.SearchClosed) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
            SharedStableOutlinedTextField(
                value = session.searchQuery,
                onValueChange = { onReaderAction(ReaderAction.SearchChanged(it)) },
                placeholder = { Text("Search in book") },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                trailingIcon = if (session.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onReaderAction(ReaderAction.SearchChanged("")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                selectionKey = session.reader.book.id
            )
            IconButton(
                onClick = { onReaderAction(ReaderAction.SearchResultsPanelToggled) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (session.showSearchResultsPanel) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (session.showSearchResultsPanel) "Hide search results" else "Show search results"
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
                        Text("Type to search this book", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                session.searchResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            "${session.searchResults.size} matches",
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
                                            "Page ${result.pageIndex + 1} - ${result.chapterTitle}",
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
            IconButton(
                onClick = onPrevious,
                enabled = session.canGoToPreviousSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous search result")
            }
            Text(
                text = if (session.activeSearchResultIndex in session.searchResults.indices) {
                    "${session.activeSearchResultIndex + 1}/${session.searchResults.size}"
                } else {
                    "${session.searchResults.size} matches"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onShowResults).padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = onNext,
                enabled = session.canGoToNextSearchResult,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next search result")
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
        ?: "Chapter ${(locator.chapterIndex ?: highlight.chapterIndex) + 1}"
    var noteText by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note.orEmpty()) }

    SharedReaderBottomSheet(
        title = "Highlight",
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            palette.sanitized().colors.forEach { color ->
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(30.dp)
                        .clickable { onColorChange(color) },
                    color = color.color,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(
                        width = if (highlight.color == color) 3.dp else 1.dp,
                        color = if (highlight.color == color) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                        }
                    ),
                    content = {}
                )
            }
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
            SharedReaderBottomSheetToolButton(Icons.Default.ContentCopy, "Copy") {
                onCopy()
                onDismiss()
            }
            SharedReaderBottomSheetToolButton(Icons.Default.Search, "Search") {
                onSearch()
                onDismiss()
            }
        }
        SharedStableOutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Note") },
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
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = {
                onSaveNote(noteText)
                onDismiss()
            }) {
                Text("Save note")
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
        title = result.title ?: "AI",
        onDismiss = onDismiss
    ) {
        val errorMessage = result.errorMessage
        when {
            result.isLoading -> Text("Working...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            else -> SharedMarkdownText(result.text)
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
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
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
                        contentDescription = "Bookmark"
                    )
                }

                ReaderTool.THEME -> IconButton(onClick = onToggleTheme) {
                    Icon(Icons.Default.Palette, contentDescription = if (isDarkMode) "Use light theme" else "Use dark theme")
                }

                ReaderTool.SEARCH -> IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }

                ReaderTool.DICTIONARY -> IconButton(
                    onClick = { onExternalLookup(ReaderExternalLookupAction.DICTIONARY, ReaderContextExtractor.currentPageText(session)) }
                ) {
                    Icon(Icons.Default.Translate, contentDescription = "External lookup")
                }

                ReaderTool.AI_FEATURES -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentPageText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, ReaderContextExtractor.currentPageText(session).take(1200)) }
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Define page")
                    }
                    TextButton(
                        enabled = aiByokSettings.areReaderAiFeaturesAvailable &&
                            ReaderContextExtractor.currentChapterText(session).isNotBlank() &&
                            !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, ReaderContextExtractor.currentChapterText(session)) }
                    ) {
                        Text("Summary")
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
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isLoading || extrasState.cloudTts.isPaused) "Stop read aloud" else "Read aloud")
                }

                ReaderTool.AUTO_SCROLL -> IconButton(
                    onClick = {
                        val autoScroll = extrasState.autoScroll.sanitized()
                        onAutoScrollChange(autoScroll.copy(enabled = !autoScroll.enabled))
                    }
                ) {
                    Icon(Icons.Default.Speed, contentDescription = if (extrasState.autoScroll.enabled) "Stop auto scroll" else "Start auto scroll")
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
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsControlsAvailable: Boolean,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsStop: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderAction: (ReaderAction) -> Unit
) {
    val sections = toolbarPreferences.availableReaderControlSections(session)
    if (sections.isEmpty()) return
    val defaultSection = sections.first()
    var selectedSection by remember(sections) { mutableStateOf(defaultSection) }
    LaunchedEffect(sections) {
        if (selectedSection !in sections) {
            selectedSection = defaultSection
        }
    }
    val activeSection = selectedSection.takeIf { it in sections } ?: defaultSection

    Surface(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Reader controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    sections.forEach { section ->
                        FilterChip(
                            selected = activeSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.title) }
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                when (activeSection) {
                    ReaderControlSection.PAGE -> SharedReaderPageControls(
                        session = session,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.FORMAT -> SharedReaderFormatControls(
                        settings = session.reader.settings,
                        toolbarPreferences = toolbarPreferences,
                        onPickCustomFont = onPickCustomFont,
                        customFonts = customFonts,
                        onReaderAction = onReaderAction
                    )

                    ReaderControlSection.THEME -> SharedReaderThemeControls(
                        settings = session.reader.settings,
                        customTextureIds = readerCustomTextureIds,
                        onImportTexture = onImportReaderTexture,
                        highlightPalette = highlightPalette,
                        onHighlightPaletteChange = onHighlightPaletteChange,
                        onSettingsChange = { onReaderAction(ReaderAction.SettingsChanged(it)) }
                    )

                    ReaderControlSection.EXTRAS -> SharedReaderExtrasControls(
                        session = session,
                        extrasState = extrasState,
                        aiByokSettings = aiByokSettings,
                        toolbarPreferences = toolbarPreferences,
                        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
                        onAiAction = onAiAction,
                        onCloudTtsStart = onCloudTtsStart,
                        onCloudTtsStop = onCloudTtsStop,
                        onAutoScrollChange = onAutoScrollChange,
                        ttsReplacementPreferences = ttsReplacementPreferences,
                        ttsReplacementBookId = ttsReplacementBookId,
                        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                    )

                }
            }
        }
    }
}

private enum class ReaderControlSection(val title: String) {
    PAGE("Page"),
    FORMAT("Format"),
    THEME("Theme"),
    EXTRAS("Extras")
}

private fun ReaderToolbarPreferences.availableReaderControlSections(session: ReaderSessionState): List<ReaderControlSection> {
    return buildList {
        if (session.shouldShowJumpHistory) {
            add(ReaderControlSection.PAGE)
        }
        if (isVisible(ReaderTool.FORMAT) || isVisible(ReaderTool.READING_MODE)) add(ReaderControlSection.FORMAT)
        if (isVisible(ReaderTool.THEME)) add(ReaderControlSection.THEME)
        if (
            isVisible(ReaderTool.AI_FEATURES) ||
            isVisible(ReaderTool.TTS_CONTROLS) ||
            isVisible(ReaderTool.TTS_SETTINGS) ||
            isVisible(ReaderTool.TTS_REPLACEMENTS) ||
            isVisible(ReaderTool.AUTO_SCROLL)
        ) {
            add(ReaderControlSection.EXTRAS)
        }
    }
}

@Composable
private fun SharedReaderPageControls(
    session: ReaderSessionState,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (session.shouldShowJumpHistory) {
            Text("Jump history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SharedReaderJumpHistoryBar(
                session = session,
                onBack = { onReaderAction(ReaderAction.JumpBack) },
                onForward = { onReaderAction(ReaderAction.JumpForward) },
                onClear = { onReaderAction(ReaderAction.JumpHistoryCleared) }
            )
        }
    }
}

@Composable
fun SharedReaderFormatControls(
    settings: ReaderSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    onPickCustomFont: (() -> String?)?,
    customFonts: List<CustomFontItem>,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (toolbarPreferences.isVisible(ReaderTool.READING_MODE)) {
            SharedReaderPanelSection("Reading") {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.readingMode == ReaderReadingMode.PAGINATED,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.PAGINATED)))
                        },
                        label = { Text("Pages") }
                    )
                    FilterChip(
                        selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                        },
                        label = { Text("Vertical") }
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
                            label = { Text("Single page") }
                        )
                        FilterChip(
                            selected = settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE,
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE))
                                )
                            },
                            label = { Text("Two pages") }
                        )
                    }
                }
            }
        }

        if (toolbarPreferences.isVisible(ReaderTool.FORMAT)) {
            SharedReaderPanelSection("Font & Alignment") {
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
                        Text("Font", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Choose")
                    }
                }

                SharedReaderChoiceRow {
                    listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                        FilterChip(
                            selected = settings.customFontPath == null && settings.fontFamily == family,
                            onClick = {
                                onReaderAction(
                                    ReaderAction.SettingsChanged(settings.copy(fontFamily = family, customFontPath = null))
                                )
                            },
                            label = { Text(family) }
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
                            Text("Clear")
                        }
                    }
                }

                val activeCustomFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
                if (activeCustomFonts.isNotEmpty()) {
                    Text(
                        "Imported fonts",
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
                        label = { Text("Left") }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY)))
                        },
                        label = { Text("Justify") }
                    )
                    FilterChip(
                        selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                        onClick = {
                            onReaderAction(ReaderAction.SettingsChanged(settings.copy(textAlign = SharedReaderTextAlign.CENTER)))
                        },
                        label = { Text("Center") }
                    )
                }
            }

            SharedReaderPanelSection("Layout & Spacing") {
                SharedReaderSettingSlider(
                    label = "Font size",
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
                    label = "Line height",
                    value = settings.lineSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(lineSpacing = value)))
                    },
                    valueRange = 1.1f..2.1f,
                    valueLabel = "${settings.lineSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = "Paragraph gap",
                    value = settings.paragraphSpacing,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(paragraphSpacing = value)))
                    },
                    valueRange = 0.5f..2.5f,
                    valueLabel = "${settings.paragraphSpacing.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = "Image size",
                    value = settings.imageScale,
                    onValueChange = { value ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(imageScale = value)))
                    },
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${settings.imageScale.formatTwoDecimals()}x",
                    formatValue = { "${it.formatTwoDecimals()}x" }
                )
                SharedReaderSettingSlider(
                    label = "Horizontal margin",
                    value = settings.resolvedHorizontalMargin.toFloat(),
                    onValueChange = { value ->
                        val nextHorizontal = value.roundToInt()
                        val nextMargin = maxOf(nextHorizontal, settings.resolvedVerticalMargin)
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(horizontalMargin = nextHorizontal, margin = nextMargin)
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedHorizontalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = "Vertical margin",
                    value = settings.resolvedVerticalMargin.toFloat(),
                    onValueChange = { value ->
                        val nextVertical = value.roundToInt()
                        val nextMargin = maxOf(settings.resolvedHorizontalMargin, nextVertical)
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(verticalMargin = nextVertical, margin = nextMargin)
                            )
                        )
                    },
                    valueRange = 0f..160f,
                    valueLabel = settings.resolvedVerticalMargin.toString(),
                    stepSize = 4f,
                    formatValue = { it.roundToInt().toString() }
                )
                SharedReaderSettingSlider(
                    label = "Page width",
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
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    highlightPalette: ReaderHighlightPalette? = null,
    onHighlightPaletteChange: ((ReaderHighlightPalette) -> Unit)? = null,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    var textured by remember(settings.themeId, settings.textureId) { mutableStateOf(settings.textureId != null) }
    var editingColorTarget by remember { mutableStateOf<ReaderThemeColorTarget?>(null) }
    val activeThemes = builtInThemes.filter { (it.textureId != null) == textured }
    val visibleCustomTextureIds = remember(customTextureIds, settings.textureId) {
        buildList {
            addAll(customTextureIds.distinct())
            settings.textureId
                ?.takeIf { it.startsWith(ReaderTextureFilePrefix) && it !in this }
                ?.let(::add)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection("Reading Themes") {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = !textured,
                    onClick = { textured = false },
                    label = { Text("Solid") }
                )
                FilterChip(
                    selected = textured,
                    onClick = { textured = true },
                    label = { Text("Textured") }
                )
            }
            activeThemes.chunked(3).forEach { rowThemes ->
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
        }

        SharedReaderPanelSection("Custom colors") {
            val backgroundColor = settings.readerBackgroundColor(builtInThemes)
            val textColor = settings.readerTextColor(builtInThemes)
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
                    Text("Custom theme preview", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Page and text colors", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SharedReaderThemeColorButton(
                    label = "Page",
                    color = backgroundColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.BACKGROUND },
                    modifier = Modifier.weight(1f)
                )
                SharedReaderThemeColorButton(
                    label = "Text",
                    color = textColor,
                    onClick = { editingColorTarget = ReaderThemeColorTarget.TEXT },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (highlightPalette != null && onHighlightPaletteChange != null) {
            SharedReaderPanelSection("Highlight palette") {
                SharedHighlightPaletteEditor(
                    palette = highlightPalette,
                    onPaletteChange = onHighlightPaletteChange
                )
            }
        }

        if (textured) {
            SharedReaderPanelSection("Texture") {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.textureId == null,
                        onClick = { onSettingsChange(settings.copy(textureId = null)) },
                        label = { Text("None") }
                    )
                    if (onImportTexture != null) {
                        FilterChip(
                            selected = settings.textureId?.startsWith(ReaderTextureFilePrefix) == true,
                            onClick = {
                                onImportTexture(settings)?.let(onSettingsChange)
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            label = { Text("Import") }
                        )
                    }
                    ReaderTexture.entries.forEach { texture ->
                        FilterChip(
                            selected = settings.textureId == texture.id,
                            onClick = { onSettingsChange(settings.copy(textureId = texture.id)) },
                            label = { Text(texture.displayName) }
                        )
                    }
                    visibleCustomTextureIds.forEach { textureId ->
                        FilterChip(
                            selected = settings.textureId == textureId,
                            onClick = { onSettingsChange(settings.copy(textureId = textureId)) },
                            label = { Text(readerTextureDisplayName(textureId)) }
                        )
                    }
                }
                if (settings.textureId != null) {
                    SharedReaderSettingSlider(
                        label = "Texture strength",
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
        val backgroundColor = settings.readerBackgroundColor(builtInThemes)
        val textColor = settings.readerTextColor(builtInThemes)
        val initialColor = when (target) {
            ReaderThemeColorTarget.BACKGROUND -> backgroundColor
            ReaderThemeColorTarget.TEXT -> textColor
        }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = target.title,
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
                    Text("Live preview", fontWeight = FontWeight.Bold)
                    Text("Page and text colors", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private const val ReaderCustomThemeId = "custom_reader"

private enum class ReaderThemeColorTarget(val title: String) {
    BACKGROUND("Page color"),
    TEXT("Text color")
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
        SharedReaderPanelSection("System UI") {
            SharedReaderChoiceRow {
                SystemUiMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.systemUiMode == mode,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(systemUiMode = mode))) },
                        label = { Text(mode.title) }
                    )
                }
            }
        }

        SharedReaderPanelSection("Page Info") {
            SharedReaderChoiceRow {
                PageInfoMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.pageInfoMode == mode,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoMode = mode))) },
                        label = { Text(mode.title) }
                    )
                }
            }
            SharedReaderChoiceRow {
                PageInfoPosition.entries.forEach { position ->
                    FilterChip(
                        selected = settings.pageInfoPosition == position,
                        onClick = { onReaderAction(ReaderAction.SettingsChanged(settings.copy(pageInfoPosition = position))) },
                        label = { Text(position.title) }
                    )
                }
            }
        }

        SharedReaderPanelSection("Chapter Turns") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seamless chapters", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.seamlessChapterNavigation,
                    onCheckedChange = { enabled ->
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(seamlessChapterNavigation = enabled)))
                    }
                )
            }
            SharedReaderSettingSlider(
                label = "Pull distance",
                value = settings.chapterTurnDragMultiplier.coerceIn(0.5f, 2.0f),
                onValueChange = { value ->
                    onReaderAction(ReaderAction.SettingsChanged(settings.copy(chapterTurnDragMultiplier = value)))
                },
                valueRange = 0.5f..2.0f,
                valueLabel = "${settings.chapterTurnDragMultiplier.formatTwoDecimals()}x",
                stepSize = 0.05f,
                formatValue = { "${it.formatTwoDecimals()}x" }
            )
        }
    }
}

@Composable
private fun SharedReaderExtrasControls(
    session: ReaderSessionState,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    cloudTtsControlsAvailable: Boolean,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsStop: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val currentPageText = ReaderContextExtractor.currentPageText(session)
    val currentChapterText = ReaderContextExtractor.currentChapterText(session)
    val recapText = ReaderContextExtractor.textBeforeCurrentLocation(session)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection("Auto Scroll") {
            val autoScroll = extrasState.autoScroll.sanitized()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto scroll", modifier = Modifier.weight(1f))
                Switch(
                    checked = autoScroll.enabled,
                    onCheckedChange = { enabled -> onAutoScrollChange(autoScroll.copy(enabled = enabled)) }
                )
            }
            SharedReaderSettingSlider(
                label = "Speed",
                value = autoScroll.speed,
                onValueChange = { speed -> onAutoScrollChange(autoScroll.copy(speed = speed).sanitized()) },
                valueRange = 12f..160f,
                valueLabel = "${autoScroll.speed.roundToInt()}",
                stepSize = 1f,
                formatValue = { it.roundToInt().toString() }
            )
        }

        if (cloudTtsControlsAvailable) {
            SharedReaderPanelSection("Cloud TTS") {
                val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                extrasState.cloudTts.isLoading -> "Preparing audio"
                                extrasState.cloudTts.isPaused -> "Paused"
                                extrasState.cloudTts.isPlaying -> "Reading"
                                settings.isCloudTtsAvailable -> "Ready"
                                else -> "Needs Gemini key"
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
                    }
                    TextButton(
                        enabled = settings.isCloudTtsAvailable || ttsBusy,
                        onClick = {
                            if (ttsBusy) {
                                onCloudTtsStop()
                            } else {
                                onCloudTtsStart(
                                    ReaderTtsReadScope.BOOK,
                                    ReaderTtsPlanner.chunksFromCurrentLocation(session)
                                )
                            }
                        }
                    ) {
                        Text(if (ttsBusy) "Stop" else "Read")
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

        if (settings.areReaderAiFeaturesAvailable) {
            SharedReaderPanelSection("AI") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    TextButton(
                        enabled = currentPageText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.DEFINE, currentPageText.take(1200)) }
                    ) {
                        Text("Define page")
                    }
                    TextButton(
                        enabled = currentChapterText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, currentChapterText) }
                    ) {
                        Text("Summarize chapter")
                    }
                    TextButton(
                        enabled = recapText.isNotBlank() && !extrasState.aiResult.isLoading,
                        onClick = { onAiAction(ReaderAiFeature.RECAP, recapText) }
                    ) {
                        Text("Recap")
                    }
                }
            }
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

    SharedReaderPanelSection("TTS Word Replacements") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Replace only what is spoken", fontWeight = FontWeight.SemiBold)
                Text(
                    "Reader text, highlights, and locations stay unchanged.",
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
                    label = { Text("Global") }
                )
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.BOOK,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.BOOK
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text("This book") }
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
                    Text("Add rule")
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
                    emptyText = "No global rules yet.",
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
                    Text("Add book rule")
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
                    emptyText = "No book rules yet.",
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
        Text("Suggestions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Use global rules here", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.globalRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enable book rules", modifier = Modifier.weight(1f))
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
        Text("Inherited global rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (globalRules.isEmpty()) {
            Text("No global rules to inherit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            if (rule.id in settings.disabledGlobalRuleIds) "Disabled for this book" else "Enabled for this book",
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
    var previewText by remember(seedId) { mutableStateOf(seedRule?.from?.takeIf { it.isNotBlank() } ?: "Dr. Smith met NASA.") }
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
            Text(if (seedRule == null) "New rule" else "Edit rule", fontWeight = FontWeight.SemiBold)
            SharedStableOutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("Replace") },
                modifier = Modifier.fillMaxWidth(),
                isError = !validation.isValid
            )
            if (!validation.isValid && validation.message != null) {
                Text(validation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SharedStableOutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Speak as") },
                modifier = Modifier.fillMaxWidth()
            )
            SharedReaderChoiceRow {
                FilterChip(selected = enabled, onClick = { enabled = !enabled }, label = { Text("Enabled") })
                FilterChip(selected = isRegex, onClick = { isRegex = !isRegex }, label = { Text("Regex") })
                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text("Whole word") })
                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text("Match case") })
            }
            SharedStableOutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text("Preview") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(previewOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text("Save") }
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
                        TextButton(onClick = { onEdit(rule) }) { Text("Edit") }
                        TextButton(onClick = { onDelete(rule) }) { Text("Delete") }
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

private fun ReaderTtsReplacementRule.desktopSummary(): String {
    val replacement = to.ifBlank { "silence" }
    return "$from -> $replacement"
}

private fun ReaderTtsReplacementRule.desktopOptions(): String {
    val options = buildList {
        add(if (isRegex) "Regex" else "Plain text")
        if (wholeWord) add("whole word")
        if (matchCase) add("case-sensitive")
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
            title = "Top Bar",
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && !toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "Bottom Bar",
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "More Menu",
            tools = moreTools.filter { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = "Hidden Tools",
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
            Text("No tools", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            label = { Text("Visible") }
                        )
                        FilterChip(
                            selected = toolbarPreferences.isBottom(tool),
                            enabled = tool.category != "Overflow Menu",
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withBottomPlacement(tool, bottom = !toolbarPreferences.isBottom(tool))
                                )
                            },
                            label = { Text("Bottom") }
                        )
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) > 0,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, -1)) }
                        ) {
                            Text("Up")
                        }
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) in 0 until toolbarPreferences.toolOrder.lastIndex,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, 1)) }
                        ) {
                            Text("Down")
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
                    contentDescription = "Decrease $label",
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
                    contentDescription = "Increase $label",
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
    }
}

private const val ReaderGapChromeLogTag = "EpistemeReaderGap"

private fun logReaderGapChrome(
    layer: String,
    bounds: Rect,
    details: String = ""
) {
    logSharedReaderDiagnostic(ReaderGapChromeLogTag) {
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
        IconButton(
            enabled = canGoPrevious,
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = "Previous page",
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
        IconButton(
            enabled = canGoNext,
            onClick = onNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = "Next page",
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
                IconButton(
                    enabled = readerState.canGoPrevious,
                    onClick = onPrevious
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous page",
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
                IconButton(
                    enabled = readerState.canGoNext,
                    onClick = onNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next page",
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
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    val tabs = remember(sections) {
        listOf(
            ReaderWorkspaceLeftSection.CONTENTS,
            ReaderWorkspaceLeftSection.NOTES,
            ReaderWorkspaceLeftSection.BOOKMARKS
        ).filter { it in sections }
    }
    var selectedSection by remember(tabs) { mutableStateOf(tabs.firstOrNull()) }
    val selectedTabIndex = tabs.indexOf(selectedSection).takeIf { it >= 0 } ?: 0

    Surface(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp
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
                    onDeleteHighlight = onDeleteHighlight
                )
                ReaderWorkspaceLeftSection.BOOKMARKS -> SharedReaderBookmarksTab(
                    session = session,
                    onGoToBookmark = onGoToBookmark
                )
                else -> SharedReaderEmptyNavigation("No navigation items")
            }
        }
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
        SharedReaderEmptyNavigation("No table of contents")
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
                Text("Expand all")
            }
            TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                Text("Collapse all")
            }
            TextButton(onClick = ::locateCurrent, enabled = activeOriginalIndex != null) {
                Text("Locate")
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
                        pageLabel = targetChapterIndex?.let { "Ch. ${it + 1}" },
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
        SharedReaderEmptyNavigation("No bookmarks yet")
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
private fun SharedReaderAnnotationsTab(
    session: ReaderSessionState,
    onGoToHighlight: (UserHighlight) -> Unit,
    onEditHighlight: (UserHighlight) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightColorChange: (UserHighlight, HighlightColor) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    if (session.highlights.isEmpty()) {
        SharedReaderEmptyNavigation("No annotations yet")
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
                            ?: "Chapter ${(locator.chapterIndex ?: highlight.chapterIndex) + 1}"
                        val pageLabel = locator.pageIndex?.let { "Page ${it + 1}" }
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
                                            Icon(Icons.Default.MoreVert, contentDescription = "Annotation options")
                                        }
                                        DropdownMenu(
                                            expanded = menuExpandedFor == highlight,
                                            onDismissRequest = { menuExpandedFor = null }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                colors.forEach { color ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(color.color)
                                                            .border(
                                                                width = if (highlight.color == color) 3.dp else 1.dp,
                                                                color = if (highlight.color == color) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                                                },
                                                                shape = CircleShape
                                                            )
                                                            .clickable {
                                                                menuExpandedFor = null
                                                                onHighlightColorChange(highlight, color)
                                                            }
                                                    )
                                                }
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(if (highlight.note.isNullOrBlank()) "Add note" else "Edit note") },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    onEditHighlight(highlight)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
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
                    title = { Text("Delete annotation?") },
                    text = { Text("This removes the highlight and its note.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteConfirmFor = null
                                onDeleteHighlight(highlight)
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmFor = null }) {
                            Text("Cancel")
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

private fun ReaderWorkspaceLeftSection.readerNavigationTabLabel(): String {
    return when (this) {
        ReaderWorkspaceLeftSection.CONTENTS -> "TOC"
        ReaderWorkspaceLeftSection.NOTES -> "Annotations"
        ReaderWorkspaceLeftSection.BOOKMARKS -> "Bookmarks"
        ReaderWorkspaceLeftSection.PAGES -> "Pages"
        ReaderWorkspaceLeftSection.SEARCH -> "Search"
    }
}

@Composable
private fun SharedHighlightPaletteEditor(
    palette: ReaderHighlightPalette,
    onPaletteChange: (ReaderHighlightPalette) -> Unit
) {
    val sanitized = palette.sanitized()
    var selectedSlotIndex by remember(sanitized.colors) { mutableIntStateOf(0) }
    val colors = sanitized.colors

    fun replaceSlot(color: HighlightColor) {
        if (colors.isEmpty()) return
        val next = colors.toMutableList()
        val slot = selectedSlotIndex.coerceIn(0, next.lastIndex)
        val previousColor = next[slot]
        val existingIndex = next.indexOf(color)
        if (existingIndex >= 0 && existingIndex != slot) {
            next[existingIndex] = previousColor
        }
        next[slot] = color
        onPaletteChange(ReaderHighlightPalette(colors = next).sanitized())
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap a slot, then pick a color.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            colors.forEachIndexed { index, color ->
                val selected = index == selectedSlotIndex.coerceIn(0, colors.lastIndex)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color.color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { selectedSlotIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        HighlightColor.entries.chunked(7).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                rowColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color.color)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                            .clickable { replaceSlot(color) }
                    )
                }
            }
        }
    }
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
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Jump back")
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
            Icon(Icons.Default.Close, contentDescription = "Clear jump history")
            Text("Clear", maxLines = 1)
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
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Jump forward")
        }
    }
}

private fun ReaderLocator.jumpLabel(session: ReaderSessionState): String {
    val targetPageIndex = pageIndex
    val targetCfi = cfi.orEmpty()
    if (targetPageIndex != null && targetCfi.isBlank()) {
        return "Page ${targetPageIndex + 1}"
    }
    val chapter = chapterIndex
    return if (chapter != null) {
        session.reader.book.chapters.getOrNull(chapter)?.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter + 1}"
    } else {
        "Location"
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

private fun PaginatedReaderState.pageInfoText(): String {
    val total = pages.size.coerceAtLeast(1)
    val percent = progress.roundToInt().coerceIn(0, 100)
    val mode = if (settings.readingMode == ReaderReadingMode.VERTICAL) "Continuous" else "Page"
    val current = ReaderSpreadLayout.pageRangeLabel(currentPageIndex, total, settings)
    val chapter = currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
    return listOfNotNull("$mode $current of $total ($percent%)", chapter).joinToString(" - ")
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
