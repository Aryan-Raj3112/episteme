package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key as keyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
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
    contentHandlesChromeTap: Boolean = false,
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

    fun toggleChromeFromReaderTap() {
        chromeVisible = readerWorkspaceChromeVisibleAfterReaderTap(
            requestedVisible = chromeVisible,
            lockedVisible = topSearchBar != null,
            forcedVisible = model.chrome.forceVisible
        )
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
            chromeVisible = true
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
        val showChrome = readerWorkspaceChromeVisible(
            requestedVisible = chromeVisible,
            lockedVisible = chromeLockedVisible,
            forcedVisible = model.chrome.forceVisible
        )
        val chromeSuppressedByPanel = rightPanelOpen
        val showTopChrome = showChrome && topSearchBar == null && !isFullscreen && !chromeSuppressedByPanel
        val showBottomChrome = showChrome && !chromeSuppressedByPanel
        val showLeftPanel = readerWorkspaceLeftPanelVisible(
            toggledOpen = leftPanelOpen,
            chromeVisible = showChrome,
            hasNavigationSections = model.leftSections.isNotEmpty()
        )
        val showRightPanel = rightPanelOpen && model.inspectorSections.isNotEmpty()
        val leftChromeExtensionWidth = if (showLeftPanel) {
            readerWorkspaceLeftPanelWidth(
                availableWidth = this@shellConstraints.maxWidth,
                wide = wide
            )
        } else {
            0.dp
        }
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
        LaunchedEffect(chromeLockedVisible, model.chrome.forceVisible) {
            if (chromeLockedVisible || model.chrome.forceVisible) {
                chromeVisible = true
            }
        }
        fun toggleLeftPanel() {
            val nextOpen = !leftPanelOpen
            leftPanelOpen = nextOpen
            if (nextOpen) {
                rightPanelOpen = false
                chromeVisible = true
            }
        }
        fun toggleRightPanel() {
            val nextOpen = !rightPanelOpen
            rightPanelOpen = nextOpen
            if (nextOpen) {
                leftPanelOpen = false
            }
        }

        CompositionLocalProvider(LocalSharedReaderModalAnchorBounds provides modalAnchorBounds) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (contentHandlesChromeTap) {
                                Modifier
                            } else {
                                Modifier.readerChromeTapTogglePointerInput(::toggleChromeFromReaderTap)
                            }
                        )
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
                    content(::toggleChromeFromReaderTap)
                }
                ReaderWorkspacePanelOverlays(
                    showLeftPanel = showLeftPanel,
                    showRightPanel = showRightPanel,
                    wide = wide,
                    leftPanelWidth = leftChromeExtensionWidth,
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
                    ReaderWorkspaceDetachedChromeLayer(
                        targetVisible = topSearchBar != null || showTopChrome,
                        level = SharedReaderModalLevel.ChromeTop
                    ) { layerVisible ->
                        CompositionLocalProvider(LocalSharedReaderModalFocusableOverride provides (topSearchBar != null)) {
                            SharedReaderModalLayer(
                                level = SharedReaderModalLevel.ChromeTop,
                                onDismiss = {}
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    ReaderWorkspaceChromeOverlay(
                                        showTopBar = layerVisible && showTopChrome,
                                        showBottomBar = false,
                                        topSearchBar = topSearchBar.takeIf { layerVisible },
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
                                        leftChromeExtensionWidth = leftChromeExtensionWidth,
                                        fileActions = fileActions,
                                        onReturnToLibrary = onReturnToLibrary,
                                        onToggleLeftPanel = { toggleLeftPanel() },
                                        onToggleRightPanel = { toggleRightPanel() },
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
                    }
                    ReaderWorkspaceDetachedChromeLayer(
                        targetVisible = showBottomChrome,
                        level = SharedReaderModalLevel.ChromeBottom
                    ) { layerVisible ->
                        SharedReaderModalLayer(
                            level = SharedReaderModalLevel.ChromeBottom,
                            onDismiss = {}
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                ReaderWorkspaceChromeOverlay(
                                    showTopBar = false,
                                    showBottomBar = layerVisible && showBottomChrome,
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
                                    leftChromeExtensionWidth = leftChromeExtensionWidth,
                                    fileActions = fileActions,
                                    onReturnToLibrary = onReturnToLibrary,
                                    onToggleLeftPanel = { toggleLeftPanel() },
                                    onToggleRightPanel = { toggleRightPanel() },
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
                        leftChromeExtensionWidth = leftChromeExtensionWidth,
                        fileActions = fileActions,
                        onReturnToLibrary = onReturnToLibrary,
                        onToggleLeftPanel = { toggleLeftPanel() },
                        onToggleRightPanel = { toggleRightPanel() },
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

private const val ReaderChromeZIndex = 10_000f

private enum class ReaderChromeHoverSource {
    TopSearch,
    TopBar,
    BottomBar,
    FileActionsMenu
}

internal fun Modifier.readerChromeTapTogglePointerInput(
    onTap: () -> Unit
): Modifier {
    return pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val pointerId = down.id
            val start = down.position
            val touchSlop = viewConfiguration.touchSlop
            var moved = false
            var consumed = down.isConsumed
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                consumed = consumed || change.isConsumed
                if (!moved && (change.position - start).getDistance() > touchSlop) {
                    moved = true
                }
                if (change.changedToUp() || !change.pressed) {
                    if (!moved && !consumed) {
                        onTap()
                    }
                    return@awaitEachGesture
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

@Composable
private fun ReaderWorkspacePanelOverlays(
    showLeftPanel: Boolean,
    showRightPanel: Boolean,
    wide: Boolean,
    leftPanelWidth: androidx.compose.ui.unit.Dp,
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
                val panelWidth = leftPanelWidth.takeIf { it > 0.dp }
                    ?: readerWorkspaceLeftPanelWidth(
                        availableWidth = this@panelConstraints.maxWidth,
                        wide = wide
                    )
                ReaderWorkspaceOverlayPanel(
                    title = readerString("desktop_reader", "Reader"),
                    edge = ReaderWorkspacePanelEdge.Start,
                    onClose = onCloseLeftPanel,
                    modifier = Modifier.align(Alignment.CenterStart).width(panelWidth)
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

private fun readerWorkspaceLeftPanelWidth(
    availableWidth: androidx.compose.ui.unit.Dp,
    wide: Boolean
): androidx.compose.ui.unit.Dp {
    return if (wide) {
        minOf(340.dp, availableWidth)
    } else {
        minOf(320.dp, availableWidth * 0.92f)
    }.coerceAtLeast(1.dp)
}

private enum class ReaderWorkspacePanelEdge {
    Start,
    End
}

private const val ReaderWorkspaceChromeAnimationMillis = 220
private const val ReaderWorkspaceDetachedChromeEnterDelayMillis = 16L

@Composable
private fun ReaderWorkspaceDetachedChromeLayer(
    targetVisible: Boolean,
    level: SharedReaderModalLevel,
    content: @Composable (layerVisible: Boolean) -> Unit
) {
    var renderLayer by remember(level) { mutableStateOf(false) }
    var layerVisible by remember(level) { mutableStateOf(false) }

    LaunchedEffect(targetVisible) {
        if (targetVisible) {
            renderLayer = true
            delay(ReaderWorkspaceDetachedChromeEnterDelayMillis)
            layerVisible = true
        } else {
            layerVisible = false
            delay(ReaderWorkspaceChromeAnimationMillis.toLong())
            renderLayer = false
        }
    }

    if (renderLayer) {
        content(layerVisible)
    }
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
    leftChromeExtensionWidth: androidx.compose.ui.unit.Dp,
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
                .padding(start = leftChromeExtensionWidth)
                .fillMaxWidth()
                .readerChromeHoverPointerInput(ReaderChromeHoverSource.TopSearch, onChromeHoverChange)
                .zIndex(ReaderChromeZIndex)
        ) {
            topSearchBar.invoke()
        }
    } else {
        AnimatedVisibility(
            visible = showTopBar,
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = ReaderWorkspaceChromeAnimationMillis,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { -it }
            ) + fadeIn(animationSpec = tween(durationMillis = ReaderWorkspaceChromeAnimationMillis)),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = ReaderWorkspaceChromeAnimationMillis,
                    easing = FastOutSlowInEasing
                ),
                targetOffsetY = { -it }
            ) + fadeOut(animationSpec = tween(durationMillis = ReaderWorkspaceChromeAnimationMillis)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = leftChromeExtensionWidth)
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
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = ReaderWorkspaceChromeAnimationMillis,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(durationMillis = ReaderWorkspaceChromeAnimationMillis)),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = ReaderWorkspaceChromeAnimationMillis,
                easing = FastOutSlowInEasing
            ),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(durationMillis = ReaderWorkspaceChromeAnimationMillis)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = leftChromeExtensionWidth)
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
        shape = RoundedCornerShape(0.dp),
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
    val isChromeExtension = edge == ReaderWorkspacePanelEdge.Start
    val panelColor = if (isChromeExtension) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val panelContentColor = MaterialTheme.colorScheme.onSurface
    val dividerColor = if (isChromeExtension) {
        panelContentColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val edgeDividerColor = if (isChromeExtension) {
        panelContentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.keyCode == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(0.dp),
        color = panelColor,
        contentColor = panelContentColor,
        tonalElevation = if (isChromeExtension) 0.dp else 4.dp,
        shadowElevation = if (isChromeExtension) 1.dp else 10.dp,
        border = if (isChromeExtension) sharedSubtleBorder(alpha = 0.55f) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isChromeExtension) 12.dp else 14.dp,
                            top = if (isChromeExtension) 4.dp else 8.dp,
                            end = if (isChromeExtension) 4.dp else 8.dp,
                            bottom = if (isChromeExtension) 4.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(if (isChromeExtension) 36.dp else 34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
                    }
                }
                HorizontalDivider(color = dividerColor)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (isChromeExtension) 8.dp else 10.dp,
                            vertical = if (isChromeExtension) 6.dp else 8.dp
                        )
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
                    .background(edgeDividerColor)
            )
        }
    }
}
