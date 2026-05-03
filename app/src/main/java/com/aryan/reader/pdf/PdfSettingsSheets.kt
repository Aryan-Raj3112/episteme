@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
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


enum class PdfFlatItemType { SECTION_HEADER, TOOL, EMPTY_PLACEHOLDER, MORE_HEADER, MORE_TOOL }

data class PdfFlatToolItem(
    val id: String,
    val type: PdfFlatItemType,
    val tool: PdfReaderTool? = null,
    val section: PdfToolbarSection? = null,
    val title: String? = null
)

fun sanitizePdfPlaceholders(list: List<PdfFlatToolItem>): List<PdfFlatToolItem> {
    val result = mutableListOf<PdfFlatToolItem>()
    val sectionMap = mutableMapOf<PdfToolbarSection, MutableList<PdfFlatToolItem>>()
    PdfToolbarSection.entries.forEach { sectionMap[it] = mutableListOf() }

    list.forEach { item ->
        if (item.type == PdfFlatItemType.TOOL) {
            item.section?.let { sectionMap[it]?.add(item) }
        }
    }

    PdfToolbarSection.entries.forEach { section ->
        result.add(PdfFlatToolItem("header_${section.name}", PdfFlatItemType.SECTION_HEADER, section = section, title = section.title))
        val tools = sectionMap[section] ?: emptyList()
        if (tools.isEmpty()) {
            result.add(PdfFlatToolItem("empty_${section.name}", PdfFlatItemType.EMPTY_PLACEHOLDER, section = section))
        } else {
            result.addAll(tools)
        }
    }

    list.filter { it.type == PdfFlatItemType.MORE_HEADER || it.type == PdfFlatItemType.MORE_TOOL }.forEach {
        result.add(it)
    }

    return result
}

class PdfDragDropState(
    val lazyListState: LazyListState,
    val onMove: (String, String) -> Unit
) {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun onDragStart(id: String) { draggedItemId = id; dragOffset = Offset.Zero }
    fun onDrag(delta: Offset) {
        val draggedId = draggedItemId ?: return
        dragOffset += delta
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.find { it.key == draggedId } ?: return
        val center = currentItem.offset + dragOffset.y + currentItem.size / 2f
        val targetItem = visibleItems.find { it.key != draggedId && center >= it.offset && center <= (it.offset + it.size) }
        if (targetItem != null) {
            onMove(draggedId, targetItem.key.toString())
            dragOffset = dragOffset.copy(y = dragOffset.y - (targetItem.offset - currentItem.offset))
        }
    }
    fun onDragEnd() { draggedItemId = null; dragOffset = Offset.Zero }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        PdfReaderTool.DICTIONARY, PdfReaderTool.THEME, PdfReaderTool.LOCK_PANNING,
        PdfReaderTool.SLIDER, PdfReaderTool.TOC, PdfReaderTool.SEARCH,
        PdfReaderTool.HIGHLIGHT_ALL, PdfReaderTool.AI_FEATURES,
        PdfReaderTool.EDIT_MODE, PdfReaderTool.TTS_CONTROLS
    )

    var localHiddenTools by remember { mutableStateOf(hiddenTools) }
    var flatItems by remember {
        mutableStateOf<List<PdfFlatToolItem>>(
            run {
                val toolbarTools = toolOrder.filter { it in reorderableToolbarTools }
                val topTools = toolbarTools.filter { !bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                val bottomToolsList = toolbarTools.filter { bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                val hiddenToolsList = toolbarTools.filter { hiddenTools.contains(it.name) }
                val moreTools = toolOrder.filter { it !in reorderableToolbarTools }

                val list = mutableListOf<PdfFlatToolItem>()

                PdfToolbarSection.entries.forEach { section ->
                    val tools = when(section) {
                        PdfToolbarSection.TOP -> topTools
                        PdfToolbarSection.BOTTOM -> bottomToolsList
                        PdfToolbarSection.HIDDEN -> hiddenToolsList
                    }
                    list.add(PdfFlatToolItem("header_${section.name}", PdfFlatItemType.SECTION_HEADER, section = section, title = section.title))
                    if (tools.isEmpty()) {
                        list.add(PdfFlatToolItem("empty_${section.name}", PdfFlatItemType.EMPTY_PLACEHOLDER, section = section))
                    } else {
                        tools.forEach { tool ->
                            list.add(PdfFlatToolItem("tool_${tool.name}", PdfFlatItemType.TOOL, tool = tool, section = section))
                        }
                    }
                }

                list.add(PdfFlatToolItem("more_header", PdfFlatItemType.MORE_HEADER, title = "More menu"))
                moreTools.forEach { tool ->
                    list.add(PdfFlatToolItem("more_${tool.name}", PdfFlatItemType.MORE_TOOL, tool = tool))
                }
                list
            }
        )
    }

    val commitDragDrop = {
        val newHidden = localHiddenTools.filter { toolName ->
            toolOrder.find { it.name == toolName } !in reorderableToolbarTools
        }.toMutableSet()

        val newBottom = mutableSetOf<String>()
        val newOrder = mutableListOf<PdfReaderTool>()

        flatItems.forEach { item ->
            if (item.type == PdfFlatItemType.TOOL && item.tool != null) {
                newOrder.add(item.tool)
                if (item.section == PdfToolbarSection.HIDDEN) newHidden.add(item.tool.name)
                if (item.section == PdfToolbarSection.BOTTOM) newBottom.add(item.tool.name)
            }
        }

        val moreTools = flatItems.filter { it.type == PdfFlatItemType.MORE_TOOL }.mapNotNull { it.tool }
        newOrder.addAll(moreTools)

        localHiddenTools = newHidden
        onUpdate(newHidden)
        onPlacementUpdate(newBottom)
        onOrderUpdate(newOrder)
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = remember {
        PdfDragDropState(lazyListState) { fromKey, toKey ->
            val fromIndex = flatItems.indexOfFirst { it.id == fromKey }
            val toIndex = flatItems.indexOfFirst { it.id == toKey }
            if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return@PdfDragDropState

            val fromItem = flatItems[fromIndex]
            if (fromItem.type != PdfFlatItemType.TOOL) return@PdfDragDropState

            val toItem = flatItems[toIndex]
            if (toItem.type == PdfFlatItemType.MORE_HEADER || toItem.type == PdfFlatItemType.MORE_TOOL) return@PdfDragDropState

            val newList = flatItems.toMutableList()
            val movedItem = newList.removeAt(fromIndex)

            val newToIndex = newList.indexOfFirst { it.id == toKey }
            val insertIndex = if (fromIndex < toIndex) newToIndex + 1 else newToIndex

            newList.add(insertIndex, movedItem)

            var actualSection = movedItem.section
            for (i in insertIndex downTo 0) {
                val item = newList[i]
                if (item.type == PdfFlatItemType.SECTION_HEADER) {
                    actualSection = item.section
                    break
                }
            }

            newList[insertIndex] = movedItem.copy(section = actualSection)
            flatItems = newList
        }
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
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(flatItems, key = { it.id }) { item ->
                        val isDragged = item.id == dragDropState.draggedItemId
                        val zIndex = if (isDragged) 1f else 0f
                        val elevation = if (isDragged) 8.dp else 0.dp
                        val scale = if (isDragged) 1.03f else 1f
                        val translationY = if (isDragged) dragDropState.dragOffset.y else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isDragged) Modifier else Modifier.animateItem())
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.shadowElevation = elevation.toPx()
                                }
                        ) {
                            when (item.type) {
                                PdfFlatItemType.SECTION_HEADER -> {
                                    Text(
                                        text = item.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                PdfFlatItemType.EMPTY_PLACEHOLDER -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .padding(vertical = 4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Drop tools here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                PdfFlatItemType.TOOL -> {
                                    PdfToolbarDragRow(
                                        tool = item.tool!!,
                                        isDragging = isDragged,
                                        onDragStart = { dragDropState.onDragStart(item.id) },
                                        onDrag = { dragDropState.onDrag(it) },
                                        onDragEnd = {
                                            dragDropState.onDragEnd()
                                            flatItems = sanitizePdfPlaceholders(flatItems).toList()
                                            commitDragDrop()
                                        }
                                    )
                                }
                                PdfFlatItemType.MORE_HEADER -> {
                                    Text(
                                        text = item.title ?: "More menu",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                PdfFlatItemType.MORE_TOOL -> {
                                    PdfMoreToolVisibilityRow(
                                        title = item.tool!!.title,
                                        visible = !localHiddenTools.contains(item.tool.name),
                                        onToggle = {
                                            localHiddenTools = if (localHiddenTools.contains(item.tool.name)) {
                                                localHiddenTools - item.tool.name
                                            } else {
                                                localHiddenTools + item.tool.name
                                            }
                                            onUpdate(localHiddenTools)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToolbarDragRow(
    tool: PdfReaderTool,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfToolPreviewIcon(tool)
            Spacer(Modifier.width(16.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .pointerInput(tool) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    }
            )
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

enum class PdfToolbarSection(val title: String) {
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
