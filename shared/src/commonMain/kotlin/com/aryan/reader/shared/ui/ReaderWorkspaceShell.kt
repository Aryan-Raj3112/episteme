package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key as keyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ReaderWorkspaceShell(
    model: ReaderWorkspaceModel,
    title: String,
    subtitle: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
    onReturnToLibrary: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onFullscreenChange: ((Boolean) -> Unit)? = null,
    fullscreenExitMessage: String = "Esc to exit",
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onSearchAction: (() -> Unit)? = null,
    fileActions: ReaderWorkspaceFileActionState? = null,
    onShareAction: (() -> Unit)? = null,
    onSaveCopyAction: (() -> Unit)? = null,
    onPrintAction: (() -> Unit)? = null,
    onTextViewAction: (() -> Unit)? = null,
    topSearchBar: (@Composable () -> Unit)? = null,
    useDetachedChromeLayer: Boolean = true,
    useDetachedPanelLayer: Boolean = true,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    fullscreenBottomBar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(onChromeActivity: () -> Unit) -> Unit
) {
    var leftPanelOpen by remember(model.kind, model.panelDefaults.leftOpen) {
        mutableStateOf(model.panelDefaults.leftOpen)
    }
    var rightPanelOpen by remember(model.kind, model.panelDefaults.inspectorOpen) {
        mutableStateOf(model.panelDefaults.inspectorOpen)
    }
    var modalAnchorBounds by remember { mutableStateOf<SharedReaderModalAnchorBounds?>(null) }
    var fullscreenBannerVisible by remember { mutableStateOf(false) }
    var chromeVisible by remember(model.kind) { mutableStateOf(false) }
    var chromeHoverSources by remember(model.kind) { mutableStateOf(emptySet<ReaderChromeHoverSource>()) }
    var chromeRevealTick by remember(model.kind) { mutableStateOf(0L) }
    var lastChromeRevealAt by remember(model.kind) { mutableStateOf(0L) }
    val chromeHovered = chromeHoverSources.isNotEmpty()

    fun revealChrome(eventTimeMillis: Long = 0L) {
        val shouldRefreshDelay = !chromeVisible ||
            eventTimeMillis <= 0L ||
            eventTimeMillis - lastChromeRevealAt >= ReaderChromeRevealThrottleMillis
        chromeVisible = true
        if (shouldRefreshDelay) {
            lastChromeRevealAt = eventTimeMillis
            chromeRevealTick += 1
        }
    }

    fun updateChromeHovered(source: ReaderChromeHoverSource, hovered: Boolean) {
        val nextSources = if (hovered) {
            chromeHoverSources + source
        } else {
            chromeHoverSources - source
        }
        if (nextSources != chromeHoverSources) {
            chromeHoverSources = nextSources
        }
        if (hovered) {
            revealChrome()
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            fullscreenBannerVisible = true
            delay(2_600)
            fullscreenBannerVisible = false
        } else {
            fullscreenBannerVisible = false
        }
    }

    LaunchedEffect(model.kind, model.chrome.forceVisibleReasons) {
        val reasons = model.chrome.forceVisibleReasons
        if (reasons.any { it == "search" }) {
            leftPanelOpen = false
            rightPanelOpen = false
        } else if (reasons.any { it == "rich-text" } && model.inspectorSections.isNotEmpty()) {
            rightPanelOpen = true
        }
    }

    LaunchedEffect(model.kind, isFullscreen) {
        chromeVisible = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) shellConstraints@ {
        val wide = this@shellConstraints.maxWidth >= 1120.dp
        val chromeLockedVisible = topSearchBar != null
        val showChrome = chromeLockedVisible || chromeVisible
        val chromeSuppressedByPanel = leftPanelOpen || rightPanelOpen
        val showTopChrome = showChrome && topSearchBar == null && !isFullscreen && !chromeSuppressedByPanel
        val showBottomChrome = showChrome && !chromeSuppressedByPanel
        LaunchedEffect(wide, leftPanelOpen, rightPanelOpen) {
            if (!wide && leftPanelOpen && rightPanelOpen) {
                rightPanelOpen = false
            }
        }
        LaunchedEffect(showTopChrome, showBottomChrome, topSearchBar != null) {
            var nextSources = chromeHoverSources
            if (topSearchBar == null) {
                nextSources = nextSources - ReaderChromeHoverSource.TopSearch
            }
            if (!showTopChrome) {
                nextSources = nextSources - ReaderChromeHoverSource.TopBar - ReaderChromeHoverSource.FileActionsMenu
            }
            if (!showBottomChrome) {
                nextSources = nextSources - ReaderChromeHoverSource.BottomBar
            }
            if (nextSources != chromeHoverSources) {
                chromeHoverSources = nextSources
            }
        }
        LaunchedEffect(chromeRevealTick, chromeLockedVisible, chromeHovered) {
            if (chromeLockedVisible) {
                chromeVisible = true
                return@LaunchedEffect
            }
            if (chromeRevealTick == 0L) {
                chromeVisible = false
                return@LaunchedEffect
            }
            chromeVisible = true
            if (chromeHovered) {
                return@LaunchedEffect
            }
            delay(ReaderChromeAutoHideDelayMillis)
            if (!chromeHovered) {
                chromeVisible = false
            }
        }

        CompositionLocalProvider(LocalSharedReaderModalAnchorBounds provides modalAnchorBounds) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .readerChromeRevealPointerInput(::revealChrome)
                    .onGloballyPositioned { coordinates ->
                        logReaderGapLayout(
                            layer = "shell_column",
                            bounds = coordinates.boundsInWindow(),
                            details = if (isFullscreen) {
                                "fullscreen=true padding=0 verticalGap=0"
                            } else {
                                "fullscreen=false overlayChrome=true padding=0 verticalGap=0"
                            }
                        )
                    }
            ) {
                val showLeftPanel = leftPanelOpen && model.leftSections.isNotEmpty()
                val showRightPanel = rightPanelOpen && model.inspectorSections.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onGloballyPositioned { coordinates ->
                            logReaderGapLayout("content_slot", coordinates.boundsInWindow())
                            val bounds = coordinates.boundsInWindow()
                            val nextBounds = SharedReaderModalAnchorBounds(
                                leftPx = bounds.left,
                                topPx = bounds.top,
                                widthPx = bounds.width,
                                heightPx = bounds.height
                            )
                            if (modalAnchorBounds != nextBounds) {
                                modalAnchorBounds = nextBounds
                            }
                        }
                ) {
                    content(::revealChrome)
                }
                ReaderWorkspacePanelOverlays(
                    showLeftPanel = showLeftPanel,
                    showRightPanel = showRightPanel,
                    wide = wide,
                    useDetachedPanelLayer = useDetachedPanelLayer,
                    onCloseLeftPanel = { leftPanelOpen = false },
                    onCloseRightPanel = { rightPanelOpen = false },
                    leftSidebar = leftSidebar,
                    rightInspector = rightInspector
                )
                val useDetachedChromeLayerForChrome =
                    useDetachedChromeLayer &&
                    model.kind == ReaderWorkspaceKind.EPUB &&
                    modalAnchorBounds != null
                if (useDetachedChromeLayerForChrome) {
                    if (topSearchBar != null || showTopChrome) {
                        SharedReaderModalLayer(
                            level = SharedReaderModalLevel.ChromeTop,
                            onDismiss = {}
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readerChromeRevealPointerInput(::revealChrome)
                            ) {
                                ReaderWorkspaceChromeOverlay(
                                    showTopBar = showTopChrome,
                                    showBottomBar = false,
                                    topSearchBar = topSearchBar,
                                    title = title,
                                    subtitle = subtitle,
                                    progressLabel = progressLabel,
                                    topActions = model.topActions,
                                    hasLeftPanel = model.leftSections.isNotEmpty(),
                                    hasRightPanel = model.inspectorSections.isNotEmpty(),
                                    leftPanelOpen = leftPanelOpen,
                                    rightPanelOpen = rightPanelOpen,
                                    isBookmarked = isBookmarked,
                                    isFullscreen = isFullscreen,
                                    fileActions = fileActions,
                                    onReturnToLibrary = onReturnToLibrary,
                                    onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                                    onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                                    onToggleBookmark = onToggleBookmark,
                                    onSearchAction = onSearchAction,
                                    onShareAction = onShareAction,
                                    onSaveCopyAction = onSaveCopyAction,
                                    onPrintAction = onPrintAction,
                                    onTextViewAction = onTextViewAction,
                                    onChromeHoverChange = ::updateChromeHovered,
                                    onToggleFullscreen = onFullscreenChange?.let { change -> { change(!isFullscreen) } },
                                    bottomBar = {}
                                )
                            }
                        }
                    }
                    if (showBottomChrome) {
                        SharedReaderModalLayer(
                            level = SharedReaderModalLevel.ChromeBottom,
                            onDismiss = {}
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readerChromeRevealPointerInput(::revealChrome)
                            ) {
                                ReaderWorkspaceChromeOverlay(
                                    showTopBar = false,
                                    showBottomBar = true,
                                    topSearchBar = null,
                                    title = title,
                                    subtitle = subtitle,
                                    progressLabel = progressLabel,
                                    topActions = model.topActions,
                                    hasLeftPanel = model.leftSections.isNotEmpty(),
                                    hasRightPanel = model.inspectorSections.isNotEmpty(),
                                    leftPanelOpen = leftPanelOpen,
                                    rightPanelOpen = rightPanelOpen,
                                    isBookmarked = isBookmarked,
                                    isFullscreen = isFullscreen,
                                    fileActions = fileActions,
                                    onReturnToLibrary = onReturnToLibrary,
                                    onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                                    onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                                    onToggleBookmark = onToggleBookmark,
                                    onSearchAction = onSearchAction,
                                    onShareAction = onShareAction,
                                    onSaveCopyAction = onSaveCopyAction,
                                    onPrintAction = onPrintAction,
                                    onTextViewAction = onTextViewAction,
                                    onChromeHoverChange = ::updateChromeHovered,
                                    onToggleFullscreen = onFullscreenChange?.let { change -> { change(!isFullscreen) } },
                                    bottomBar = {
                                        key(isFullscreen) {
                                            val immersiveBottomBar = fullscreenBottomBar
                                            if (isFullscreen && immersiveBottomBar != null) {
                                                immersiveBottomBar()
                                            } else {
                                                bottomBar()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    ReaderWorkspaceChromeOverlay(
                        showTopBar = showTopChrome,
                        showBottomBar = showBottomChrome,
                        topSearchBar = topSearchBar,
                        title = title,
                        subtitle = subtitle,
                        progressLabel = progressLabel,
                        topActions = model.topActions,
                        hasLeftPanel = model.leftSections.isNotEmpty(),
                        hasRightPanel = model.inspectorSections.isNotEmpty(),
                        leftPanelOpen = leftPanelOpen,
                        rightPanelOpen = rightPanelOpen,
                        isBookmarked = isBookmarked,
                        isFullscreen = isFullscreen,
                        fileActions = fileActions,
                        onReturnToLibrary = onReturnToLibrary,
                        onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                        onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                        onToggleBookmark = onToggleBookmark,
                        onSearchAction = onSearchAction,
                        onShareAction = onShareAction,
                        onSaveCopyAction = onSaveCopyAction,
                        onPrintAction = onPrintAction,
                        onTextViewAction = onTextViewAction,
                        onChromeHoverChange = ::updateChromeHovered,
                        onToggleFullscreen = onFullscreenChange?.let { change -> { change(!isFullscreen) } },
                        bottomBar = {
                            key(isFullscreen) {
                                val immersiveBottomBar = fullscreenBottomBar
                                if (isFullscreen && immersiveBottomBar != null) {
                                    immersiveBottomBar()
                                } else {
                                    bottomBar()
                                }
                            }
                        }
                    )
                }
            }
        }

        ReaderWorkspaceTopBanner(
            bannerMessage = if (fullscreenBannerVisible) BannerMessage(fullscreenExitMessage) else null,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private const val ReaderChromeAutoHideDelayMillis = 2_050L
private const val ReaderChromeRevealThrottleMillis = 120L
private const val ReaderChromeZIndex = 10_000f

private enum class ReaderChromeHoverSource {
    TopSearch,
    TopBar,
    BottomBar,
    FileActionsMenu
}

private fun Modifier.readerChromeRevealPointerInput(
    onReveal: (Long) -> Unit
): Modifier {
    return pointerInput(onReveal) {
        awaitPointerEventScope {
            var lastMovePosition: Offset? = null
            pointerLoop@ while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Move -> {
                        val position = event.changes.firstOrNull()?.position ?: continue@pointerLoop
                        if (!lastMovePosition.isMeaningfulMoveTo(position)) continue@pointerLoop
                        lastMovePosition = position
                        onReveal(event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L)
                    }
                    PointerEventType.Press,
                    PointerEventType.Scroll -> {
                        event.changes.firstOrNull()?.position?.let { lastMovePosition = it }
                        onReveal(event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L)
                    }
                }
            }
        }
    }
}

private fun Modifier.readerChromeHoverPointerInput(
    source: ReaderChromeHoverSource,
    onHoveredChange: (ReaderChromeHoverSource, Boolean) -> Unit
): Modifier {
    return pointerInput(source, onHoveredChange) {
        awaitPointerEventScope {
            var hovered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Enter,
                    PointerEventType.Move,
                    PointerEventType.Press,
                    PointerEventType.Scroll -> {
                        if (!hovered) {
                            hovered = true
                            onHoveredChange(source, true)
                        }
                    }
                    PointerEventType.Exit -> {
                        if (hovered) {
                            hovered = false
                            onHoveredChange(source, false)
                        }
                    }
                }
            }
        }
    }
}

private fun Offset?.isMeaningfulMoveTo(next: Offset): Boolean {
    val previous = this ?: return true
    val dx = next.x - previous.x
    val dy = next.y - previous.y
    return (dx * dx) + (dy * dy) >= 1f
}

@Composable
private fun ReaderWorkspacePanelOverlays(
    showLeftPanel: Boolean,
    showRightPanel: Boolean,
    wide: Boolean,
    useDetachedPanelLayer: Boolean,
    onCloseLeftPanel: () -> Unit,
    onCloseRightPanel: () -> Unit,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit
) {
    if (!showLeftPanel && !showRightPanel) return

    if (showLeftPanel) {
        val panelContent: @Composable () -> Unit = {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .then(if (useDetachedPanelLayer) Modifier else Modifier.zIndex(ReaderChromeZIndex + 1f))
            ) panelConstraints@ {
                val availableWidth = this@panelConstraints.maxWidth
                val leftPanelWidth = if (
                    useDetachedPanelLayer &&
                    sharedReaderModalLayerUsesSizedEdgeWindow(SharedReaderModalLevel.PanelLeft)
                ) {
                    availableWidth
                } else if (wide) {
                    minOf(340.dp, availableWidth)
                } else {
                    minOf(320.dp, availableWidth * 0.92f)
                }
                ReaderWorkspaceOverlayPanel(
                    title = readerString("desktop_reader", "Reader"),
                    edge = ReaderWorkspacePanelEdge.Start,
                    onClose = onCloseLeftPanel,
                    modifier = Modifier.align(Alignment.CenterStart).width(leftPanelWidth)
                ) {
                    leftSidebar(onCloseLeftPanel)
                }
            }
        }
        if (useDetachedPanelLayer) {
            SharedReaderModalLayer(
                level = SharedReaderModalLevel.PanelLeft,
                onDismiss = onCloseLeftPanel
            ) {
                panelContent()
            }
        } else {
            panelContent()
        }
    }
    if (showRightPanel) {
        val panelContent: @Composable () -> Unit = {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .then(if (useDetachedPanelLayer) Modifier else Modifier.zIndex(ReaderChromeZIndex + 1f))
            ) panelConstraints@ {
                val availableWidth = this@panelConstraints.maxWidth
                val rightPanelWidth = if (
                    useDetachedPanelLayer &&
                    sharedReaderModalLayerUsesSizedEdgeWindow(SharedReaderModalLevel.PanelRight)
                ) {
                    availableWidth
                } else if (wide) {
                    minOf(380.dp, availableWidth)
                } else {
                    minOf(360.dp, availableWidth * 0.92f)
                }
                ReaderWorkspaceOverlayPanel(
                    title = readerString("desktop_tools", "Tools"),
                    edge = ReaderWorkspacePanelEdge.End,
                    onClose = onCloseRightPanel,
                    modifier = Modifier.align(Alignment.CenterEnd).width(rightPanelWidth)
                ) {
                    rightInspector()
                }
            }
        }
        if (useDetachedPanelLayer) {
            SharedReaderModalLayer(
                level = SharedReaderModalLevel.PanelRight,
                onDismiss = onCloseRightPanel
            ) {
                panelContent()
            }
        } else {
            panelContent()
        }
    }
}

private enum class ReaderWorkspacePanelEdge {
    Start,
    End
}

@Composable
private fun BoxScope.ReaderWorkspaceChromeOverlay(
    showTopBar: Boolean,
    showBottomBar: Boolean,
    topSearchBar: (@Composable () -> Unit)?,
    title: String,
    subtitle: String,
    progressLabel: String,
    topActions: List<ReaderWorkspaceTopAction>,
    hasLeftPanel: Boolean,
    hasRightPanel: Boolean,
    leftPanelOpen: Boolean,
    rightPanelOpen: Boolean,
    isBookmarked: Boolean,
    isFullscreen: Boolean,
    fileActions: ReaderWorkspaceFileActionState?,
    onReturnToLibrary: (() -> Unit)?,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit,
    onToggleBookmark: (() -> Unit)?,
    onSearchAction: (() -> Unit)?,
    onShareAction: (() -> Unit)?,
    onSaveCopyAction: (() -> Unit)?,
    onPrintAction: (() -> Unit)?,
    onTextViewAction: (() -> Unit)?,
    onChromeHoverChange: (ReaderChromeHoverSource, Boolean) -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    bottomBar: @Composable () -> Unit
) {
    if (topSearchBar != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .readerChromeHoverPointerInput(ReaderChromeHoverSource.TopSearch, onChromeHoverChange)
                .zIndex(ReaderChromeZIndex)
        ) {
            topSearchBar.invoke()
        }
    } else {
        AnimatedVisibility(
            visible = showTopBar,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .readerChromeHoverPointerInput(ReaderChromeHoverSource.TopBar, onChromeHoverChange)
                .zIndex(ReaderChromeZIndex)
        ) {
            ReaderWorkspaceTopChrome(
                modifier = Modifier.fillMaxWidth(),
                title = title,
                subtitle = subtitle,
                progressLabel = progressLabel,
                topActions = topActions,
                hasLeftPanel = hasLeftPanel,
                hasRightPanel = hasRightPanel,
                leftPanelOpen = leftPanelOpen,
                rightPanelOpen = rightPanelOpen,
                isBookmarked = isBookmarked,
                isFullscreen = isFullscreen,
                fileActions = fileActions,
                onReturnToLibrary = onReturnToLibrary,
                onToggleLeftPanel = onToggleLeftPanel,
                onToggleRightPanel = onToggleRightPanel,
                onToggleBookmark = onToggleBookmark,
                onSearchAction = onSearchAction,
                onShareAction = onShareAction,
                onSaveCopyAction = onSaveCopyAction,
                onPrintAction = onPrintAction,
                onTextViewAction = onTextViewAction,
                onChromeHoverChange = onChromeHoverChange,
                onToggleFullscreen = onToggleFullscreen
            )
        }
    }
    AnimatedVisibility(
        visible = showBottomBar,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 980.dp)
            .fillMaxWidth()
            .readerChromeHoverPointerInput(ReaderChromeHoverSource.BottomBar, onChromeHoverChange)
            .zIndex(ReaderChromeZIndex)
    ) {
        bottomBar()
    }
}

private const val ReaderGapLogTag = "EpistemeReaderGap"

private fun logReaderGapLayout(
    layer: String,
    bounds: Rect,
    details: String = ""
) {
    logSharedReaderDiagnostic(ReaderGapLogTag) {
        buildString {
            append("compose_shell layer=")
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
private fun ReaderWorkspaceTopChrome(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    progressLabel: String,
    topActions: List<ReaderWorkspaceTopAction>,
    hasLeftPanel: Boolean,
    hasRightPanel: Boolean,
    leftPanelOpen: Boolean,
    rightPanelOpen: Boolean,
    isBookmarked: Boolean,
    isFullscreen: Boolean,
    fileActions: ReaderWorkspaceFileActionState?,
    onReturnToLibrary: (() -> Unit)?,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit,
    onToggleBookmark: (() -> Unit)?,
    onSearchAction: (() -> Unit)?,
    onShareAction: (() -> Unit)?,
    onSaveCopyAction: (() -> Unit)?,
    onPrintAction: (() -> Unit)?,
    onTextViewAction: (() -> Unit)?,
    onChromeHoverChange: (ReaderChromeHoverSource, Boolean) -> Unit,
    onToggleFullscreen: (() -> Unit)?
) {
    var fileActionsExpanded by remember { mutableStateOf(false) }
    DisposableEffect(fileActionsExpanded) {
        if (fileActionsExpanded) {
            onChromeHoverChange(ReaderChromeHoverSource.FileActionsMenu, true)
        }
        onDispose {
            if (fileActionsExpanded) {
                onChromeHoverChange(ReaderChromeHoverSource.FileActionsMenu, false)
            }
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SharedUiTokens.chromeRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = sharedSubtleBorder(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            onReturnToLibrary?.let { returnToLibrary ->
                IconButton(onClick = returnToLibrary, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("desktop_back_to_library", "Back to library"))
                }
            }
            if (hasLeftPanel) {
                IconButton(onClick = onToggleLeftPanel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = if (leftPanelOpen) "Hide reader navigation" else "Show reader navigation")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ReaderWorkspaceTopAction.SEARCH in topActions && onSearchAction != null) {
                IconButton(onClick = onSearchAction, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Search, contentDescription = readerString("desktop_search_in_reader", "Search in reader"))
                }
            }
            if (ReaderWorkspaceTopAction.BOOKMARK in topActions && onToggleBookmark != null) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) {
                            readerString("menu_remove_bookmark", "Remove bookmark")
                        } else {
                            readerString("menu_bookmark_this_page", "Bookmark this page")
                        }
                    )
                }
            }
            if (
                ReaderWorkspaceTopAction.FILE_ACTIONS in topActions &&
                fileActions?.hasAnyAction == true
            ) {
                Box {
                    IconButton(onClick = { fileActionsExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_pdf_file_actions", "PDF file actions"))
                    }
                    DropdownMenu(
                        expanded = fileActionsExpanded,
                        onDismissRequest = { fileActionsExpanded = false }
                    ) {
                        if (fileActions.canShare && onShareAction != null) {
                            DropdownMenuItem(
                                text = { Text(readerString("action_share", "Share")) },
                                onClick = {
                                    fileActionsExpanded = false
                                    onShareAction()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                        }
                        if (
                            (fileActions.canGenerateTextView ||
                                fileActions.hasGeneratedTextView ||
                                fileActions.isGeneratingTextView) &&
                            onTextViewAction != null
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            fileActions.isGeneratingTextView -> readerString("generating_text_view", "Generating Text View...")
                                            fileActions.hasGeneratedTextView -> readerString("action_open_text_view", "Open Text View")
                                            else -> readerString("action_generate_text_view", "Generate Text View")
                                        }
                                    )
                                },
                                enabled = !fileActions.isGeneratingTextView,
                                onClick = {
                                    fileActionsExpanded = false
                                    onTextViewAction()
                                },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                            )
                        }
                        if (fileActions.canSaveCopy && onSaveCopyAction != null) {
                            DropdownMenuItem(
                                text = { Text(readerString("action_save", "Save")) },
                                onClick = {
                                    fileActionsExpanded = false
                                    onSaveCopyAction()
                                },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                            )
                        }
                        if (fileActions.canPrint && onPrintAction != null) {
                            DropdownMenuItem(
                                text = { Text(readerString("action_print", "Print")) },
                                onClick = {
                                    fileActionsExpanded = false
                                    onPrintAction()
                                },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
                            )
                        }
                    }
                }
            }
            if (ReaderWorkspaceTopAction.FULL_SCREEN in topActions && onToggleFullscreen != null) {
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) {
                            readerString("desktop_exit_full_screen", "Exit full screen")
                        } else {
                            readerString("desktop_enter_full_screen", "Enter full screen")
                        }
                    )
                }
            }
            if (hasRightPanel) {
                IconButton(onClick = onToggleRightPanel, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = if (rightPanelOpen) {
                            readerString("desktop_hide_reader_tools", "Hide reader tools")
                        } else {
                            readerString("desktop_show_reader_tools", "Show reader tools")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderWorkspaceTopBanner(
    bannerMessage: BannerMessage?,
    modifier: Modifier = Modifier
) {
    val bannerText = readerBannerMessage(bannerMessage)
    AnimatedVisibility(
        visible = bannerMessage != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (bannerMessage?.isError == true) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = bannerText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (bannerMessage?.isError == true) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReaderWorkspaceOverlayPanel(
    title: String,
    edge: ReaderWorkspacePanelEdge,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.keyCode == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    content()
                }
            }
            Box(
                modifier = Modifier
                    .align(
                        if (edge == ReaderWorkspacePanelEdge.Start) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        }
                    )
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            )
        }
    }
}
