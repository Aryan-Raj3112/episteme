package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    preferDockedChrome: Boolean = false,
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onSearchAction: (() -> Unit)? = null,
    topSearchBar: (@Composable () -> Unit)? = null,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    fullscreenBottomBar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var leftPanelOpen by remember(model.kind, model.panelDefaults.leftOpen) {
        mutableStateOf(model.panelDefaults.leftOpen)
    }
    var rightPanelOpen by remember(model.kind, model.panelDefaults.inspectorOpen) {
        mutableStateOf(model.panelDefaults.inspectorOpen)
    }
    var modalAnchorBounds by remember { mutableStateOf<SharedReaderModalAnchorBounds?>(null) }
    var fullscreenBannerVisible by remember { mutableStateOf(false) }

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) shellConstraints@ {
        val wide = this@shellConstraints.maxWidth >= 1120.dp
        val useOverlaySearch = topSearchBar != null && !preferDockedChrome
        val useDockedSearch = topSearchBar != null && !useOverlaySearch
        val useFloatingChrome = !isFullscreen &&
            !preferDockedChrome &&
            model.chrome.preferAutoHide &&
            !model.chrome.forceVisible &&
            !useOverlaySearch &&
            !leftPanelOpen &&
            !rightPanelOpen
        val showDockedChrome = !useFloatingChrome &&
            !useOverlaySearch &&
            (useDockedSearch || !isFullscreen)
        val shellPadding = if (isFullscreen || useFloatingChrome || useOverlaySearch) 0.dp else 8.dp
        val shellGap = if (isFullscreen || useFloatingChrome || useOverlaySearch) 0.dp else 6.dp
        LaunchedEffect(wide, leftPanelOpen, rightPanelOpen) {
            if (!wide && leftPanelOpen && rightPanelOpen) {
                rightPanelOpen = false
            }
        }

        CompositionLocalProvider(LocalSharedReaderModalAnchorBounds provides modalAnchorBounds) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = shellPadding,
                        top = shellPadding,
                        end = shellPadding
                    )
                    .onGloballyPositioned { coordinates ->
                        logReaderGapLayout(
                            layer = "shell_column",
                            bounds = coordinates.boundsInWindow(),
                            details = if (isFullscreen) {
                                "fullscreen=true padding=0 verticalGap=0"
                            } else if (useFloatingChrome || useOverlaySearch) {
                                "floatingChrome=$useFloatingChrome overlaySearch=$useOverlaySearch padding=0 verticalGap=0"
                            } else {
                                "fullscreen=false padding=start8 top8 end8 bottom0 verticalGap=6"
                            }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(shellGap)
            ) {
                if (showDockedChrome) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                logReaderGapLayout("top_chrome_slot", coordinates.boundsInWindow())
                            }
                    ) {
                        if (useDockedSearch) {
                            topSearchBar?.invoke()
                        } else {
                            ReaderWorkspaceTopChrome(
                                modifier = Modifier.fillMaxWidth(),
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
                                onReturnToLibrary = onReturnToLibrary,
                                onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                                onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                                onToggleBookmark = onToggleBookmark,
                                onSearchAction = onSearchAction,
                                onToggleFullscreen = onFullscreenChange?.let { change -> { change(!isFullscreen) } }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            logReaderGapLayout("content_slot", coordinates.boundsInWindow())
                        }
                ) {
                    val showLeftPanel = leftPanelOpen && model.leftSections.isNotEmpty()
                    val showRightPanel = rightPanelOpen && model.inspectorSections.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onGloballyPositioned { coordinates ->
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
                        content()
                    }
                    ReaderWorkspacePanelOverlays(
                        showLeftPanel = showLeftPanel,
                        showRightPanel = showRightPanel,
                        wide = wide,
                        onCloseLeftPanel = { leftPanelOpen = false },
                        onCloseRightPanel = { rightPanelOpen = false },
                        leftSidebar = leftSidebar,
                        rightInspector = rightInspector
                    )
                }

                if (!useFloatingChrome) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                logReaderGapLayout("bottom_bar_slot", coordinates.boundsInWindow())
                            }
                    ) {
                        key(isFullscreen) {
                            val immersiveBottomBar = fullscreenBottomBar
                            if (isFullscreen && immersiveBottomBar != null) {
                                immersiveBottomBar()
                            } else {
                                bottomBar()
                            }
                        }
                    }
                }
            }

            if (useFloatingChrome) {
                ReaderWorkspaceFloatingChrome(
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
                    onReturnToLibrary = onReturnToLibrary,
                    onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                    onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                    onToggleBookmark = onToggleBookmark,
                    onSearchAction = onSearchAction,
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
            if (useOverlaySearch) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                ) {
                    topSearchBar?.invoke()
                }
            }
        }

        ReaderWorkspaceTopBanner(
            bannerMessage = if (fullscreenBannerVisible) BannerMessage(fullscreenExitMessage) else null,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ReaderWorkspacePanelOverlays(
    showLeftPanel: Boolean,
    showRightPanel: Boolean,
    wide: Boolean,
    onCloseLeftPanel: () -> Unit,
    onCloseRightPanel: () -> Unit,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit
) {
    if (!showLeftPanel && !showRightPanel) return

    SharedReaderModalLayer(
        level = SharedReaderModalLevel.Panel,
        onDismiss = {
            if (showLeftPanel) onCloseLeftPanel()
            if (showRightPanel) onCloseRightPanel()
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) panelConstraints@ {
            val availableWidth = this@panelConstraints.maxWidth
            val leftPanelWidth = if (wide) 340.dp else minOf(320.dp, availableWidth * 0.92f)
            val rightPanelWidth = if (wide) 380.dp else minOf(360.dp, availableWidth * 0.92f)
            if (showLeftPanel) {
                ReaderWorkspaceOverlayPanel(
                    title = "Reader",
                    onClose = onCloseLeftPanel,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(leftPanelWidth)
                ) {
                    leftSidebar(onCloseLeftPanel)
                }
            }
            if (showRightPanel) {
                ReaderWorkspaceOverlayPanel(
                    title = "Tools",
                    onClose = onCloseRightPanel,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(rightPanelWidth)
                ) {
                    rightInspector()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ReaderWorkspaceFloatingChrome(
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
    onReturnToLibrary: (() -> Unit)?,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit,
    onToggleBookmark: (() -> Unit)?,
    onSearchAction: (() -> Unit)?,
    onToggleFullscreen: (() -> Unit)?,
    bottomBar: @Composable () -> Unit
) {
    ReaderWorkspaceTopChrome(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(max = 820.dp)
            .fillMaxWidth(),
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
        onReturnToLibrary = onReturnToLibrary,
        onToggleLeftPanel = onToggleLeftPanel,
        onToggleRightPanel = onToggleRightPanel,
        onToggleBookmark = onToggleBookmark,
        onSearchAction = onSearchAction,
        onToggleFullscreen = onToggleFullscreen
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 980.dp)
            .fillMaxWidth()
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
    onReturnToLibrary: (() -> Unit)?,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit,
    onToggleBookmark: (() -> Unit)?,
    onSearchAction: (() -> Unit)?,
    onToggleFullscreen: (() -> Unit)?
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SharedUiTokens.chromeRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
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
                    Icon(Icons.Default.Search, contentDescription = "Search in reader")
                }
            }
            if (ReaderWorkspaceTopAction.BOOKMARK in topActions && onToggleBookmark != null) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark"
                    )
                }
            }
            if (ReaderWorkspaceTopAction.FULL_SCREEN in topActions && onToggleFullscreen != null) {
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit full screen" else "Enter full screen"
                    )
                }
            }
            if (hasRightPanel) {
                IconButton(onClick = onToggleRightPanel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = if (rightPanelOpen) "Hide reader tools" else "Show reader tools")
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
                    text = bannerMessage?.message.orEmpty(),
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight().padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            content()
        }
    }
}
