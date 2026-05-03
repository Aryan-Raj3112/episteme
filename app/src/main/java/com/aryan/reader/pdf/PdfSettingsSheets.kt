@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.aryan.reader.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aryan.reader.R
import com.aryan.reader.epubreader.OptionSegmentedControl
import com.aryan.reader.epubreader.SystemUiMode

@Composable
fun PdfCustomizeToolsSheet(
    hiddenTools: Set<String>,
    toolOrder: List<PdfReaderTool>,
    bottomTools: Set<String>,
    onUpdate: (Set<String>) -> Unit,
    onOrderUpdate: (List<PdfReaderTool>) -> Unit,
    onPlacementUpdate: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val reorderableToolbarTools = setOf(
        PdfReaderTool.DICTIONARY,
        PdfReaderTool.THEME,
        PdfReaderTool.LOCK_PANNING,
        PdfReaderTool.SLIDER,
        PdfReaderTool.TOC,
        PdfReaderTool.SEARCH,
        PdfReaderTool.HIGHLIGHT_ALL,
        PdfReaderTool.AI_FEATURES,
        PdfReaderTool.EDIT_MODE,
        PdfReaderTool.TTS_CONTROLS
    )

    val toolbarTools = toolOrder.filter { it in reorderableToolbarTools }
    val sectionBounds = remember { mutableMapOf<PdfToolbarSection, Rect>() }
    val rowBounds = remember { mutableMapOf<PdfReaderTool, Rect>() }
    var draggedTool by remember { mutableStateOf<PdfReaderTool?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    fun sectionFor(tool: PdfReaderTool): PdfToolbarSection = when {
        hiddenTools.contains(tool.name) -> PdfToolbarSection.HIDDEN
        bottomTools.contains(tool.name) -> PdfToolbarSection.BOTTOM
        else -> PdfToolbarSection.TOP
    }

    fun toolsIn(section: PdfToolbarSection): List<PdfReaderTool> =
        toolbarTools.filter { sectionFor(it) == section }

    fun applyDrop(tool: PdfReaderTool, targetSection: PdfToolbarSection, position: Offset) {
        val newHiddenTools = when (targetSection) {
            PdfToolbarSection.HIDDEN -> hiddenTools + tool.name
            else -> hiddenTools - tool.name
        }
        val newBottomTools = when (targetSection) {
            PdfToolbarSection.BOTTOM -> bottomTools + tool.name
            else -> bottomTools - tool.name
        }
        val targetTools = toolsIn(targetSection).filterNot { it == tool }
        val insertIndex = targetTools.indexOfFirst { target ->
            position.y < (rowBounds[target]?.center?.y ?: Float.MAX_VALUE)
        }.let { if (it == -1) targetTools.size else it }
        val newToolbarOrder = toolbarTools.toMutableList().also { it.remove(tool) }
        val anchorTool = targetTools.getOrNull(insertIndex)
        val globalIndex = anchorTool?.let { newToolbarOrder.indexOf(it) } ?: run {
            val lastInSection = targetTools.lastOrNull()
            if (lastInSection != null) newToolbarOrder.indexOf(lastInSection) + 1 else newToolbarOrder.size
        }
        newToolbarOrder.add(globalIndex.coerceIn(0, newToolbarOrder.size), tool)
        onUpdate(newHiddenTools)
        onPlacementUpdate(newBottomTools)
        onOrderUpdate(newToolbarOrder + toolOrder.filter { it !in reorderableToolbarTools })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.title_customize_toolbar),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PdfToolbarSection.entries.forEach { section ->
                        item(section.title) {
                            PdfToolbarDropSection(
                                title = section.title,
                                tools = toolsIn(section),
                                draggedTool = draggedTool,
                                onSectionBounds = { sectionBounds[section] = it },
                                onToolBounds = { tool, bounds -> rowBounds[tool] = bounds },
                                onDragStart = { tool, start ->
                                    draggedTool = tool
                                    dragPosition = start
                                },
                                onDrag = { dragPosition += it },
                                onDragEnd = { tool ->
                                    val targetSection = sectionBounds.entries.firstOrNull { (_, bounds) ->
                                        bounds.contains(dragPosition)
                                    }?.key ?: sectionFor(tool)
                                    applyDrop(tool, targetSection, dragPosition)
                                    draggedTool = null
                                }
                            )
                        }
                    }
                    item("more") {
                        Text(
                            text = "More menu",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                        )
                        toolOrder.filter { it !in reorderableToolbarTools }.forEach { tool ->
                            PdfMoreToolVisibilityRow(
                                title = tool.title,
                                visible = !hiddenTools.contains(tool.name),
                                onToggle = {
                                    if (hiddenTools.contains(tool.name)) onUpdate(hiddenTools - tool.name)
                                    else onUpdate(hiddenTools + tool.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToolbarDropSection(
    title: String,
    tools: List<PdfReaderTool>,
    draggedTool: PdfReaderTool?,
    onSectionBounds: (Rect) -> Unit,
    onToolBounds: (PdfReaderTool, Rect) -> Unit,
    onDragStart: (PdfReaderTool, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (PdfReaderTool) -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onSectionBounds(it.boundsInWindow()) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (tools.isEmpty()) {
                Text(
                    text = "Drop tools here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                tools.forEach { tool ->
                    PdfToolbarDragRow(
                        tool = tool,
                        isDragging = draggedTool == tool,
                        onBounds = { onToolBounds(tool, it) },
                        onDragStart = { onDragStart(tool, it) },
                        onDrag = onDrag,
                        onDragEnd = { onDragEnd(tool) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfToolbarDragRow(
    tool: PdfReaderTool,
    isDragging: Boolean,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onGloballyPositioned {
                bounds = it.boundsInWindow()
                onBounds(it.boundsInWindow())
            }
            .pointerInput(tool) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(bounds?.center ?: Offset.Zero) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfToolPreviewIcon(tool)
            Spacer(Modifier.width(12.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PdfMoreToolVisibilityRow(
    title: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (visible) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class PdfToolbarSection(val title: String) {
    TOP("Top Bar"),
    BOTTOM("Bottom Bar"),
    HIDDEN("Hidden Tools")
}

@Composable
private fun PdfToolPreviewIcon(tool: PdfReaderTool) {
    when (tool) {
        PdfReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.LOCK_PANNING -> Icon(Icons.Default.LockOpen, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SLIDER -> Icon(painterResource(id = R.drawable.slider), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.HIGHLIGHT_ALL -> Icon(painterResource(id = R.drawable.highlight_text), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.EDIT_MODE -> Icon(Icons.Default.Edit, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = tool.title, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PdfVisualOptionsSheet(
    systemUiMode: SystemUiMode,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.menu_visual_options), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.visual_options_system_ui), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.visual_options_system_ui_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OptionSegmentedControl(
                options = SystemUiMode.entries,
                selectedOption = systemUiMode,
                onOptionSelected = onSystemUiModeChange,
                getLabel = { it.title }
            )
        }
    }
}
