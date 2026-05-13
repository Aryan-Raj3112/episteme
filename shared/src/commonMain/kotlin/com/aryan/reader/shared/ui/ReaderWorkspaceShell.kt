package com.aryan.reader.shared.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlin.math.roundToInt

@Composable
fun ReaderWorkspaceShell(
    model: ReaderWorkspaceModel,
    title: String,
    subtitle: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
    onReturnToLibrary: (() -> Unit)? = null,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var leftPanelOpen by remember(model.kind, model.panelDefaults.leftOpen) {
        mutableStateOf(model.panelDefaults.leftOpen)
    }
    var rightPanelOpen by remember(model.kind, model.panelDefaults.inspectorOpen) {
        mutableStateOf(model.panelDefaults.inspectorOpen)
    }
    var modalAnchorBounds by remember { mutableStateOf<SharedReaderModalAnchorBounds?>(null) }

    LaunchedEffect(model.kind, model.chrome.forceVisibleReasons) {
        val reasons = model.chrome.forceVisibleReasons
        if (reasons.any { it == "search" || it == "rich-text" } && model.inspectorSections.isNotEmpty()) {
            rightPanelOpen = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 1120.dp
        LaunchedEffect(wide, leftPanelOpen, rightPanelOpen) {
            if (!wide && leftPanelOpen && rightPanelOpen) {
                rightPanelOpen = false
            }
        }

        CompositionLocalProvider(LocalSharedReaderModalAnchorBounds provides modalAnchorBounds) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        logReaderGapLayout(
                            layer = "shell_column",
                            bounds = coordinates.boundsInWindow(),
                            details = "padding=start8 top8 end8 bottom0 verticalGap=6"
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            logReaderGapLayout("top_chrome_slot", coordinates.boundsInWindow())
                        }
                ) {
                    ReaderWorkspaceTopChrome(
                        title = title,
                        subtitle = subtitle,
                        progressLabel = progressLabel,
                        hasLeftPanel = model.leftSections.isNotEmpty(),
                        hasRightPanel = model.inspectorSections.isNotEmpty(),
                        leftPanelOpen = leftPanelOpen,
                        rightPanelOpen = rightPanelOpen,
                        onReturnToLibrary = onReturnToLibrary,
                        onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                        onToggleRightPanel = { rightPanelOpen = !rightPanelOpen }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            logReaderGapLayout("content_slot", coordinates.boundsInWindow())
                        }
                ) {
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
                        showLeftPanel = leftPanelOpen && model.leftSections.isNotEmpty(),
                        showRightPanel = rightPanelOpen && model.inspectorSections.isNotEmpty(),
                        wide = wide,
                        onCloseLeftPanel = { leftPanelOpen = false },
                        onCloseRightPanel = { rightPanelOpen = false },
                        leftSidebar = leftSidebar,
                        rightInspector = rightInspector
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            logReaderGapLayout("bottom_bar_slot", coordinates.boundsInWindow())
                        }
                ) {
                    bottomBar()
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
    onCloseLeftPanel: () -> Unit,
    onCloseRightPanel: () -> Unit,
    leftSidebar: @Composable (closePanel: () -> Unit) -> Unit,
    rightInspector: @Composable () -> Unit
) {
    if (!showLeftPanel && !showRightPanel) return

    Box(Modifier.fillMaxSize()) {
        if (showLeftPanel) {
            ReaderWorkspaceOverlayPanel(
                title = "Reader",
                onClose = onCloseLeftPanel,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(if (wide) 340.dp else 320.dp)
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
                    .width(if (wide) 380.dp else 360.dp)
            ) {
                rightInspector()
            }
        }
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
    title: String,
    subtitle: String,
    progressLabel: String,
    hasLeftPanel: Boolean,
    hasRightPanel: Boolean,
    leftPanelOpen: Boolean,
    rightPanelOpen: Boolean,
    onReturnToLibrary: (() -> Unit)?,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
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
            if (hasRightPanel) {
                IconButton(onClick = onToggleRightPanel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = if (rightPanelOpen) "Hide reader tools" else "Show reader tools")
                }
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
