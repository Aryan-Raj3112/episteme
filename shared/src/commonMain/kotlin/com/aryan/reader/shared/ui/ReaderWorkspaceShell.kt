package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ReaderWorkspaceShell(
    model: ReaderWorkspaceModel,
    title: String,
    subtitle: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
    onReturnToLibrary: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    leftSidebar: @Composable () -> Unit,
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

    LaunchedEffect(model.kind, model.chrome.forceVisibleReasons) {
        val reasons = model.chrome.forceVisibleReasons
        if ("search" in reasons && model.leftSections.isNotEmpty()) {
            leftPanelOpen = true
        }
        if (reasons.any { it == "annotation" || it == "rich-text" } && model.inspectorSections.isNotEmpty()) {
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

        Column(
            modifier = Modifier.fillMaxSize().padding(start = 8.dp, top = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                topActions = topActions
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (wide && leftPanelOpen && model.leftSections.isNotEmpty()) {
                        leftSidebar()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        content()
                    }
                    if (wide && rightPanelOpen && model.inspectorSections.isNotEmpty()) {
                        rightInspector()
                    }
                }

                if (!wide && leftPanelOpen && model.leftSections.isNotEmpty()) {
                    ReaderWorkspaceOverlayPanel(
                        title = "Reader",
                        onClose = { leftPanelOpen = false },
                        modifier = Modifier.align(Alignment.CenterStart).width(320.dp)
                    ) {
                        leftSidebar()
                    }
                }
                if (!wide && rightPanelOpen && model.inspectorSections.isNotEmpty()) {
                    ReaderWorkspaceOverlayPanel(
                        title = "Tools",
                        onClose = { rightPanelOpen = false },
                        modifier = Modifier.align(Alignment.CenterEnd).width(360.dp)
                    ) {
                        rightInspector()
                    }
                }
            }

            bottomBar()
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
    onToggleRightPanel: () -> Unit,
    topActions: @Composable RowScope.() -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }

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
            onReturnToLibrary?.let { returnToLibrary ->
                TextButton(
                    onClick = returnToLibrary,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Library")
                }
            }
            if (hasRightPanel) {
                IconButton(onClick = onToggleRightPanel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = if (rightPanelOpen) "Hide reader tools" else "Show reader tools")
                }
            }
            Box {
                IconButton(onClick = { overflowOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Reader actions")
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topActions()
                    }
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
