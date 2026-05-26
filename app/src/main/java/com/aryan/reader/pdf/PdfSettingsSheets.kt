@file:OptIn(ExperimentalMaterial3Api::class)

package com.aryan.reader.pdf

import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aryan.reader.epubreader.titleRes
import com.aryan.reader.shared.reader.ReaderPageSpreadMode


enum class PdfFlatItemType { SECTION_HEADER, TOOL, EMPTY_PLACEHOLDER, MORE_HEADER, MORE_TOOL }

data class PdfFlatToolItem(
    val id: String,
    val type: PdfFlatItemType,
    val tool: PdfReaderTool? = null,
    val section: PdfToolbarSection? = null,
    val title: String? = null,
    @StringRes val titleRes: Int? = null
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
        result.add(PdfFlatToolItem("header_${section.name}", PdfFlatItemType.SECTION_HEADER, section = section, titleRes = section.titleRes))
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

internal fun buildPdfToolbarItems(
    hiddenTools: Set<String>,
    toolOrder: List<PdfReaderTool>,
    bottomTools: Set<String>
): List<PdfFlatToolItem> {
    val availableToolOrder = toolOrder.filter(::isPdfReaderToolAvailable)
    val toolbarTools = availableToolOrder.filter(::isPdfToolbarPlacementTool)
    val topTools = toolbarTools.filter { !bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
    val bottomToolsList = toolbarTools.filter { bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
    val hiddenToolsList = toolbarTools.filter { hiddenTools.contains(it.name) }
    val moreTools = availableToolOrder.filterNot(::isPdfToolbarPlacementTool)

    val list = mutableListOf<PdfFlatToolItem>()

    PdfToolbarSection.entries.forEach { section ->
        val tools = when (section) {
            PdfToolbarSection.TOP -> topTools
            PdfToolbarSection.BOTTOM -> bottomToolsList
            PdfToolbarSection.HIDDEN -> hiddenToolsList
        }
        list.add(PdfFlatToolItem("header_${section.name}", PdfFlatItemType.SECTION_HEADER, section = section, titleRes = section.titleRes))
        if (tools.isEmpty()) {
            list.add(PdfFlatToolItem("empty_${section.name}", PdfFlatItemType.EMPTY_PLACEHOLDER, section = section))
        } else {
            tools.forEach { tool ->
                list.add(PdfFlatToolItem("tool_${tool.name}", PdfFlatItemType.TOOL, tool = tool, section = section))
            }
        }
    }

    list.add(PdfFlatToolItem("more_header", PdfFlatItemType.MORE_HEADER, titleRes = R.string.toolbar_more_menu))
    moreTools.forEach { tool ->
        list.add(PdfFlatToolItem("more_${tool.name}", PdfFlatItemType.MORE_TOOL, tool = tool))
    }

    return list
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
    var localHiddenTools by remember { mutableStateOf(hiddenTools) }
    var flatItems by remember {
        mutableStateOf<List<PdfFlatToolItem>>(
            buildPdfToolbarItems(
                hiddenTools = hiddenTools,
                toolOrder = toolOrder,
                bottomTools = bottomTools
            )
        )
    }

    val commitDragDrop = {
        val newHidden = localHiddenTools.filter { toolName ->
            toolOrder.find { it.name == toolName }?.let(::isPdfToolbarPlacementTool) != true
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

    val resetToDefault = {
        val defaultHiddenTools = defaultPdfHiddenTools()
        val defaultToolOrder = defaultPdfToolOrder()
        val defaultBottomTools = defaultPdfBottomTools()

        localHiddenTools = defaultHiddenTools
        flatItems = buildPdfToolbarItems(
            hiddenTools = defaultHiddenTools,
            toolOrder = defaultToolOrder,
            bottomTools = defaultBottomTools
        )
        onUpdate(defaultHiddenTools)
        onPlacementUpdate(defaultBottomTools)
        onOrderUpdate(defaultToolOrder)
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
                    TextButton(onClick = resetToDefault) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_reset))
                    }
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
                                    val titleRes = item.titleRes
                                    Text(
                                        text = if (titleRes != null) stringResource(titleRes) else item.title.orEmpty(),
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
                                        Text(stringResource(R.string.toolbar_drop_tools_here), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    val titleRes = item.titleRes
                                    Text(
                                        text = if (titleRes != null) stringResource(titleRes) else item.title ?: stringResource(R.string.toolbar_more_menu),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                PdfFlatItemType.MORE_TOOL -> {
                                    PdfMoreToolVisibilityRow(
                                        title = stringResource(item.tool!!.titleRes),
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
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.content_desc_drag_to_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp)
                    .clip(CircleShape)
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
                text = stringResource(tool.titleRes),
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

enum class PdfToolbarSection(@StringRes val titleRes: Int) {
    TOP(R.string.toolbar_top_bar),
    BOTTOM(R.string.toolbar_bottom_bar),
    HIDDEN(R.string.toolbar_hidden_tools)
}

@Composable
private fun PdfToolPreviewIcon(tool: PdfReaderTool) {
    val title = stringResource(tool.titleRes)
    when (tool) {
        PdfReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.BRIGHTNESS -> Icon(painterResource(id = R.drawable.contrast), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.LOCK_PANNING -> Icon(Icons.Default.LockOpen, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.FILE_INFO -> Icon(Icons.Default.Info, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SLIDER -> Icon(painterResource(id = R.drawable.slider), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.HIGHLIGHT_ALL -> Icon(painterResource(id = R.drawable.highlight_text), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.EDIT_MODE -> Icon(Icons.Default.Edit, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SCREEN_ORIENTATION -> Icon(Icons.Default.ScreenRotation, contentDescription = title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = title, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PdfVisualOptionsSheet(
    displayMode: DisplayMode,
    systemUiMode: SystemUiMode,
    pageSpreadMode: ReaderPageSpreadMode,
    firstPageStandaloneInSpread: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    onPageSpreadModeChange: (ReaderPageSpreadMode) -> Unit,
    onFirstPageStandaloneInSpreadChange: (Boolean) -> Unit,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
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
                getLabel = { stringResource(it.titleRes) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.visual_options_page_layout), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            if (displayMode == DisplayMode.PAGINATION) {
                Text(
                    stringResource(R.string.visual_options_pdf_page_spread),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OptionSegmentedControl(
                    options = ReaderPageSpreadMode.entries,
                    selectedOption = pageSpreadMode,
                    onOptionSelected = onPageSpreadModeChange,
                    getLabel = {
                        when (it) {
                            ReaderPageSpreadMode.SINGLE -> stringResource(R.string.visual_options_pdf_spread_single)
                            ReaderPageSpreadMode.TWO_PAGE -> stringResource(R.string.visual_options_pdf_spread_two)
                        }
                    }
                )
                if (pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PdfVisualOptionSwitchRow(
                        title = stringResource(R.string.visual_options_pdf_first_page_alone),
                        description = stringResource(R.string.visual_options_pdf_first_page_alone_desc),
                        checked = firstPageStandaloneInSpread,
                        onCheckedChange = onFirstPageStandaloneInSpreadChange
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            PdfVisualOptionSwitchRow(
                title = stringResource(R.string.visual_options_remove_page_gap),
                description = stringResource(R.string.visual_options_remove_page_gap_desc),
                checked = !showVerticalPageGap,
                onCheckedChange = { removeGap ->
                    onShowVerticalPageGapChange(!removeGap)
                }
            )
            PdfVisualOptionSwitchRow(
                title = stringResource(R.string.visual_options_hide_page_number_overlay),
                description = stringResource(R.string.visual_options_hide_page_number_overlay_desc),
                checked = !showPageNumberOverlay,
                onCheckedChange = { hideOverlay ->
                    onShowPageNumberOverlayChange(!hideOverlay)
                }
            )
        }
    }
}

@Composable
private fun PdfVisualOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
