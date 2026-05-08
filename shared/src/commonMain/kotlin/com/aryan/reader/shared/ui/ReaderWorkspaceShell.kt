package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
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
import kotlinx.coroutines.delay

@Composable
fun ReaderWorkspaceShell(
    model: ReaderWorkspaceModel,
    title: String,
    subtitle: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
    topActions: @Composable RowScope.() -> Unit = {},
    leftSidebar: @Composable () -> Unit,
    rightInspector: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var leftPanelOpen by remember(model.kind) { mutableStateOf(true) }
    var rightPanelOpen by remember(model.kind) { mutableStateOf(true) }
    var chromeVisible by remember(model.kind) { mutableStateOf(true) }
    val forceChrome = model.chrome.forceVisible || leftPanelOpen || rightPanelOpen

    LaunchedEffect(forceChrome, model.chrome.preferAutoHide, model.chrome.forceVisibleReasons) {
        chromeVisible = true
        if (model.chrome.preferAutoHide && !forceChrome) {
            delay(3_200)
            chromeVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 1120.dp
        val showChrome = chromeVisible || forceChrome || !model.chrome.preferAutoHide
        LaunchedEffect(wide, leftPanelOpen, rightPanelOpen) {
            if (!wide && leftPanelOpen && rightPanelOpen) {
                rightPanelOpen = false
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showChrome) {
                ReaderWorkspaceTopChrome(
                    title = title,
                    subtitle = subtitle,
                    progressLabel = progressLabel,
                    wide = wide,
                    leftPanelOpen = leftPanelOpen,
                    rightPanelOpen = rightPanelOpen,
                    onToggleLeftPanel = { leftPanelOpen = !leftPanelOpen },
                    onToggleRightPanel = { rightPanelOpen = !rightPanelOpen },
                    topActions = topActions
                )
            }

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

            if (showChrome) {
                bottomBar()
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clickable { chromeVisible = true }
                )
            }
        }
    }
}

@Composable
private fun ReaderWorkspaceTopChrome(
    title: String,
    subtitle: String,
    progressLabel: String,
    wide: Boolean,
    leftPanelOpen: Boolean,
    rightPanelOpen: Boolean,
    onToggleLeftPanel: () -> Unit,
    onToggleRightPanel: () -> Unit,
    topActions: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onToggleLeftPanel) {
                Icon(Icons.Default.Menu, contentDescription = if (leftPanelOpen) "Hide reader navigation" else "Show reader navigation")
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                topActions()
            }
            IconButton(onClick = onToggleRightPanel) {
                Icon(Icons.Default.Tune, contentDescription = if (rightPanelOpen) "Hide reader tools" else "Show reader tools")
            }
            if (!wide) {
                TextButton(onClick = onToggleRightPanel, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Tools")
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
