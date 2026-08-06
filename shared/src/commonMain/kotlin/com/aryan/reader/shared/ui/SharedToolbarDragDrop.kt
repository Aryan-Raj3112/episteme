package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.PdfToolbarPreferences
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences

enum class SharedToolbarFlatItemType { SECTION_HEADER, TOOL, EMPTY_PLACEHOLDER, MORE_HEADER, MORE_TOOL }

enum class SharedToolbarSection { TOP, BOTTOM, HIDDEN }

data class SharedToolbarFlatItem(
    val id: String,
    val type: SharedToolbarFlatItemType,
    val section: SharedToolbarSection? = null,
    val title: String? = null,
    val toolId: String? = null,
)

private fun sharedToolbarSectionTitle(section: SharedToolbarSection): String = when (section) {
    SharedToolbarSection.TOP -> "Top Bar"
    SharedToolbarSection.BOTTOM -> "Bottom Bar"
    SharedToolbarSection.HIDDEN -> "Hidden Tools"
}

private fun buildSharedToolbarSections(
    topTools: List<Pair<String, String>>,
    bottomTools: List<Pair<String, String>>,
    hiddenTools: List<Pair<String, String>>,
    moreTools: List<Pair<String, String>>,
): List<SharedToolbarFlatItem> {
    val list = mutableListOf<SharedToolbarFlatItem>()
    SharedToolbarSection.entries.forEach { section ->
        val tools = when (section) {
            SharedToolbarSection.TOP -> topTools
            SharedToolbarSection.BOTTOM -> bottomTools
            SharedToolbarSection.HIDDEN -> hiddenTools
        }
        list.add(
            SharedToolbarFlatItem(
                id = "header_${section.name}",
                type = SharedToolbarFlatItemType.SECTION_HEADER,
                section = section,
                title = sharedToolbarSectionTitle(section),
            )
        )
        if (tools.isEmpty()) {
            list.add(
                SharedToolbarFlatItem(
                    id = "empty_${section.name}",
                    type = SharedToolbarFlatItemType.EMPTY_PLACEHOLDER,
                    section = section,
                )
            )
        } else {
            tools.forEach { (toolId, title) ->
                list.add(
                    SharedToolbarFlatItem(
                        id = "tool_$toolId",
                        type = SharedToolbarFlatItemType.TOOL,
                        section = section,
                        title = title,
                        toolId = toolId,
                    )
                )
            }
        }
    }
    list.add(
        SharedToolbarFlatItem(
            id = "more_header",
            type = SharedToolbarFlatItemType.MORE_HEADER,
            title = "More Menu",
        )
    )
    moreTools.forEach { (toolId, title) ->
        list.add(
            SharedToolbarFlatItem(
                id = "more_$toolId",
                type = SharedToolbarFlatItemType.MORE_TOOL,
                title = title,
                toolId = toolId,
            )
        )
    }
    return list
}

fun buildSharedPdfToolbarItems(
    preferences: PdfToolbarPreferences,
    availableTools: Set<PdfReaderTool>,
): List<SharedToolbarFlatItem> {
    val sanitized = preferences.sanitized(availableTools)
    val toolOrder = sanitized.toolOrder.filter { it in availableTools }
    val toolbarTools = toolOrder.filter { it.supportsToolbarPlacement }
    val topTools = toolbarTools
        .filter { !sanitized.bottomToolIds.contains(it.id) && !sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val bottomTools = toolbarTools
        .filter { sanitized.bottomToolIds.contains(it.id) && !sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val hiddenTools = toolbarTools
        .filter { sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val moreTools = toolOrder.filterNot { it.supportsToolbarPlacement }.map { it.id to it.title }
    return buildSharedToolbarSections(topTools, bottomTools, hiddenTools, moreTools)
}

fun buildSharedEpubToolbarItems(
    preferences: ReaderToolbarPreferences,
    toolbarTools: Set<ReaderTool>,
    availableTools: Set<ReaderTool>,
): List<SharedToolbarFlatItem> {
    val sanitized = preferences.sanitized()
    val toolOrder = sanitized.toolOrder.filter { it in availableTools }
    val placementTools = toolOrder.filter { it in toolbarTools }
    val topTools = placementTools
        .filter { !sanitized.bottomToolIds.contains(it.id) && !sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val bottomTools = placementTools
        .filter { sanitized.bottomToolIds.contains(it.id) && !sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val hiddenTools = placementTools
        .filter { sanitized.hiddenToolIds.contains(it.id) }
        .map { it.id to it.title }
    val moreTools = toolOrder.filterNot { it in toolbarTools }.map { it.id to it.title }
    return buildSharedToolbarSections(topTools, bottomTools, hiddenTools, moreTools)
}

fun sanitizeSharedToolbarPlaceholders(list: List<SharedToolbarFlatItem>): List<SharedToolbarFlatItem> {
    val result = mutableListOf<SharedToolbarFlatItem>()
    val sectionMap = mutableMapOf<SharedToolbarSection, MutableList<SharedToolbarFlatItem>>()
    SharedToolbarSection.entries.forEach { sectionMap[it] = mutableListOf() }

    list.forEach { item ->
        if (item.type == SharedToolbarFlatItemType.TOOL) {
            item.section?.let { sectionMap[it]?.add(item) }
        }
    }

    SharedToolbarSection.entries.forEach { section ->
        result.add(
            SharedToolbarFlatItem(
                id = "header_${section.name}",
                type = SharedToolbarFlatItemType.SECTION_HEADER,
                section = section,
                title = sharedToolbarSectionTitle(section),
            )
        )
        val tools = sectionMap[section] ?: emptyList()
        if (tools.isEmpty()) {
            result.add(
                SharedToolbarFlatItem(
                    id = "empty_${section.name}",
                    type = SharedToolbarFlatItemType.EMPTY_PLACEHOLDER,
                    section = section,
                )
            )
        } else {
            result.addAll(tools)
        }
    }

    list.filter { it.type == SharedToolbarFlatItemType.MORE_HEADER || it.type == SharedToolbarFlatItemType.MORE_TOOL }.forEach {
        result.add(it)
    }

    return result
}

fun sharedToolbarMoveItem(
    flatItems: List<SharedToolbarFlatItem>,
    fromKey: String,
    toKey: String,
): List<SharedToolbarFlatItem> {
    val fromIndex = flatItems.indexOfFirst { it.id == fromKey }
    val toIndex = flatItems.indexOfFirst { it.id == toKey }
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return flatItems

    val fromItem = flatItems[fromIndex]
    if (fromItem.type != SharedToolbarFlatItemType.TOOL) return flatItems

    val toItem = flatItems[toIndex]
    if (toItem.type == SharedToolbarFlatItemType.MORE_HEADER || toItem.type == SharedToolbarFlatItemType.MORE_TOOL) {
        return flatItems
    }

    val newList = flatItems.toMutableList()
    val movedItem = newList.removeAt(fromIndex)

    val newToIndex = newList.indexOfFirst { it.id == toKey }
    val insertIndex = if (fromIndex < toIndex) newToIndex + 1 else newToIndex

    newList.add(insertIndex, movedItem)

    var actualSection = movedItem.section
    for (i in insertIndex downTo 0) {
        val item = newList[i]
        if (item.type == SharedToolbarFlatItemType.SECTION_HEADER) {
            actualSection = item.section
            break
        }
    }

    newList[insertIndex] = movedItem.copy(section = actualSection)
    return newList
}

fun buildSharedPdfToolbarCommit(
    flatItems: List<SharedToolbarFlatItem>,
    previousHiddenToolIds: Set<String>,
    availableTools: Set<PdfReaderTool>,
): PdfToolbarPreferences {
    val newHidden = previousHiddenToolIds.filterTo(mutableSetOf()) { toolId ->
        PdfReaderTool.fromId(toolId)?.supportsToolbarPlacement != true
    }
    val newBottom = mutableSetOf<String>()
    val newOrder = mutableListOf<PdfReaderTool>()

    flatItems.forEach { item ->
        if (item.type == SharedToolbarFlatItemType.TOOL) {
            val tool = item.toolId?.let(PdfReaderTool::fromId)
            if (tool != null) {
                newOrder.add(tool)
                if (item.section == SharedToolbarSection.HIDDEN) newHidden.add(tool.id)
                if (item.section == SharedToolbarSection.BOTTOM) newBottom.add(tool.id)
            }
        }
    }

    flatItems.filter { it.type == SharedToolbarFlatItemType.MORE_TOOL }
        .mapNotNull { it.toolId?.let(PdfReaderTool::fromId) }
        .forEach { newOrder.add(it) }

    return PdfToolbarPreferences(
        hiddenToolIds = newHidden,
        toolOrder = newOrder,
        bottomToolIds = newBottom,
    ).sanitized(availableTools)
}

fun buildSharedEpubToolbarCommit(
    flatItems: List<SharedToolbarFlatItem>,
    previousHiddenToolIds: Set<String>,
    toolbarTools: Set<ReaderTool>,
): ReaderToolbarPreferences {
    val newHidden = previousHiddenToolIds.filterTo(mutableSetOf()) { toolId ->
        ReaderTool.fromId(toolId) !in toolbarTools
    }
    val newBottom = mutableSetOf<String>()
    val newOrder = mutableListOf<ReaderTool>()

    flatItems.forEach { item ->
        if (item.type == SharedToolbarFlatItemType.TOOL) {
            val tool = item.toolId?.let(ReaderTool::fromId)
            if (tool != null) {
                newOrder.add(tool)
                if (item.section == SharedToolbarSection.HIDDEN) newHidden.add(tool.id)
                if (item.section == SharedToolbarSection.BOTTOM) newBottom.add(tool.id)
            }
        }
    }

    flatItems.filter { it.type == SharedToolbarFlatItemType.MORE_TOOL }
        .mapNotNull { it.toolId?.let(ReaderTool::fromId) }
        .forEach { newOrder.add(it) }

    return ReaderToolbarPreferences(
        hiddenToolIds = newHidden,
        toolOrder = newOrder,
        bottomToolIds = newBottom,
    ).sanitized()
}

class SharedToolbarDragDropState(
    val lazyListState: LazyListState,
    val onMove: (String, String) -> Unit,
) {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun onDragStart(id: String) {
        draggedItemId = id
        dragOffset = Offset.Zero
    }

    fun onDrag(delta: Offset) {
        val draggedId = draggedItemId ?: return
        dragOffset += delta

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.find { it.key == draggedId } ?: return

        val startY = currentItem.offset + dragOffset.y
        val center = startY + currentItem.size / 2f

        val targetItem = visibleItems.find {
            it.key != draggedId && center >= it.offset && center <= (it.offset + it.size)
        }

        if (targetItem != null) {
            onMove(draggedId, targetItem.key.toString())
            dragOffset = dragOffset.copy(y = dragOffset.y - (targetItem.offset - currentItem.offset))
        }
    }

    fun onDragEnd() {
        draggedItemId = null
        dragOffset = Offset.Zero
    }
}

@Composable
fun SharedToolbarDragRow(
    title: String,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    leadingIcon: @Composable () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon()
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.Menu,
                contentDescription = readerString("content_desc_drag_to_reorder", "Drag to reorder"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .pointerInput(title) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        )
                    },
            )
        }
    }
}

@Composable
fun SharedToolbarMoreVisibilityRow(
    title: String,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (visible) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SharedToolbarDragDropList(
    flatItems: List<SharedToolbarFlatItem>,
    dragDropState: SharedToolbarDragDropState,
    emptyPlaceholderTitle: String,
    moreMenuTitle: String,
    toolRow: @Composable (item: SharedToolbarFlatItem, isDragging: Boolean) -> Unit,
    moreToolRow: @Composable (item: SharedToolbarFlatItem) -> Unit,
) {
    LazyColumn(
        state = dragDropState.lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                    },
            ) {
                when (item.type) {
                    SharedToolbarFlatItemType.SECTION_HEADER -> {
                        Text(
                            text = item.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
                        )
                    }
                    SharedToolbarFlatItemType.EMPTY_PLACEHOLDER -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(vertical = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emptyPlaceholderTitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SharedToolbarFlatItemType.TOOL -> toolRow(item, isDragged)
                    SharedToolbarFlatItemType.MORE_HEADER -> {
                        Text(
                            text = item.title ?: moreMenuTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
                        )
                    }
                    SharedToolbarFlatItemType.MORE_TOOL -> moreToolRow(item)
                }
            }
        }
    }
}

@Composable
fun SharedPdfToolbarDragIcon(tool: PdfReaderTool) {
    val icon: ImageVector = when (tool) {
        PdfReaderTool.DICTIONARY -> Icons.Default.Translate
        PdfReaderTool.THEME -> Icons.Default.Palette
        PdfReaderTool.BRIGHTNESS -> Icons.Default.Tune
        PdfReaderTool.LOCK_PANNING -> Icons.Default.LockOpen
        PdfReaderTool.SLIDER -> Icons.Default.Settings
        PdfReaderTool.TOC -> Icons.Default.Menu
        PdfReaderTool.SEARCH -> Icons.Default.Search
        PdfReaderTool.HIGHLIGHT_ALL -> Icons.Default.SelectAll
        PdfReaderTool.AI_FEATURES -> Icons.Default.Ai
        PdfReaderTool.EDIT_MODE -> Icons.Default.Edit
        PdfReaderTool.TTS_CONTROLS -> Icons.Default.GraphicEq
        PdfReaderTool.SCREEN_ORIENTATION -> Icons.Default.ScreenRotation
        PdfReaderTool.FILE_INFO -> Icons.Default.Info
        PdfReaderTool.BOOKMARK -> Icons.Default.Bookmark
        PdfReaderTool.SHARE -> Icons.Default.Share
        PdfReaderTool.SAVE_COPY -> Icons.Default.Save
        PdfReaderTool.PRINT -> Icons.Default.Print
        else -> Icons.Default.MoreVert
    }
    Icon(icon, contentDescription = tool.title, modifier = Modifier.size(20.dp))
}

@Composable
fun SharedEpubToolbarDragIcon(tool: ReaderTool) {
    val icon: ImageVector = when (tool) {
        ReaderTool.DICTIONARY -> Icons.Default.Translate
        ReaderTool.THEME -> Icons.Default.Palette
        ReaderTool.BRIGHTNESS -> Icons.Default.Tune
        ReaderTool.SLIDER -> Icons.Default.Settings
        ReaderTool.TOC -> Icons.Default.Menu
        ReaderTool.FORMAT -> Icons.Default.TextFields
        ReaderTool.SEARCH -> Icons.Default.Search
        ReaderTool.AI_FEATURES -> Icons.Default.Ai
        ReaderTool.TTS_CONTROLS -> Icons.Default.GraphicEq
        ReaderTool.BOOK_REPLACEMENTS -> Icons.Default.TextFields
        ReaderTool.FILE_INFO -> Icons.Default.Info
        ReaderTool.SCREEN_ORIENTATION -> Icons.Default.ScreenRotation
        ReaderTool.BOOKMARK -> Icons.Default.Bookmark
        else -> Icons.Default.MoreVert
    }
    Icon(icon, contentDescription = tool.title, modifier = Modifier.size(20.dp))
}

@Composable
fun SharedToolbarCustomizationHeader(
    title: String,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(readerString("action_reset", "Reset"))
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
        }
    }
}

@Composable
fun rememberSharedToolbarDragDropState(
    lazyListState: LazyListState,
    flatItems: () -> List<SharedToolbarFlatItem>,
    onFlatItemsChange: (List<SharedToolbarFlatItem>) -> Unit,
): SharedToolbarDragDropState {
    return remember {
        SharedToolbarDragDropState(lazyListState) { fromKey, toKey ->
            onFlatItemsChange(sharedToolbarMoveItem(flatItems(), fromKey, toKey))
        }
    }
}
