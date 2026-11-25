package com.aryan.reader.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderTabKind
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.movedItem
import com.aryan.reader.shared.readerTabKind
import kotlin.math.roundToInt

/**
 * One open reader tab, resolved for the library surface that lists every reader.
 *
 * The two engines keep independent tab sets; the panel shows both in one list so the library
 * can answer "what is open?" without the reader strip that used to cost a row of Home.
 */
@Immutable
data class SharedOpenTab(
    val bookId: String,
    val title: String,
    val kind: ReaderTabKind,
    val isActive: Boolean = false,
    /** 1-based chapter number for reflow books, when the stored position has one. */
    val chapterNumber: Int? = null,
    /** 1-based page number for fixed-layout books, when the stored position has one. */
    val pageNumber: Int? = null,
    val progressPercent: Int? = null,
) {
    val isText: Boolean get() = kind == ReaderTabKind.TEXT
}

/**
 * "Tabs" trigger with the open-tab list behind it.
 *
 * Deliberately not a Material `DropdownMenu`: this is a panel, not a list of commands. It keeps
 * the menu's proven popup/placement/scrim plumbing ([SharedDropdownMenu]) so it dismisses the
 * same way on Android and iOS, while the content is a miniature tab switcher.
 *
 * The trigger draws nothing when there are no tabs, so the library bar keeps its width on a
 * single-document session.
 *
 * Each reader's rows stop at three and scroll from there, so the panel's height answers to the
 * bar rather than to how many books are open. Rows can be dragged by their handle into a new
 * order when [onTabOrderCommitted] is supplied; the panel owns the order while the drag is in
 * flight and hands the finished list to the caller once, so both readers' tab strips follow it.
 */
@Composable
fun SharedTabsMenuButton(
    tabs: List<SharedOpenTab>,
    label: String,
    openTabsDescription: String,
    closeTabDescription: String,
    dragToReorderDescription: String = "Drag to reorder",
    onTabSelected: (SharedOpenTab) -> Unit,
    onTabClosed: (SharedOpenTab) -> Unit,
    onTabOrderCommitted: ((kind: ReaderTabKind, orderedBookIds: List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    openCountLabel: String = "${tabs.size} open",
    epubLabel: String = "EPUB",
    pdfLabel: String = "PDF",
    chapterLabel: (Int) -> String = { "Chapter $it" },
    pageLabel: (Int) -> String = { "Page $it" },
    percentLabel: (Int) -> String = { "$it%" },
    closeAllLabel: String? = null,
    onCloseAllTabs: (() -> Unit)? = null,
    cover: @Composable (SharedOpenTab, Modifier) -> Unit = { tab, coverModifier ->
        DefaultOpenTabCover(tab, coverModifier)
    },
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(tabs.isEmpty()) {
        if (tabs.isEmpty()) expanded = false
    }
    if (tabs.isEmpty()) return

    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "tabsCaret",
    )
    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .testTag("HomeTabsButton")
                .semantics { contentDescription = openTabsDescription }
                .clip(CircleShape)
                .clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).rotate(caretRotation),
                )
            }
        }

        SharedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 14.dp),
            scrimColor = Color.Black.copy(alpha = 0.32f),
            horizontalMargin = 12.dp,
            useIntrinsicWidth = false,
        ) {
            SharedOpenTabsMenuContent(
                tabs = tabs,
                label = label,
                openCountLabel = openCountLabel,
                epubLabel = epubLabel,
                pdfLabel = pdfLabel,
                chapterLabel = chapterLabel,
                pageLabel = pageLabel,
                percentLabel = percentLabel,
                closeTabDescription = closeTabDescription,
                dragToReorderDescription = dragToReorderDescription,
                closeAllLabel = closeAllLabel,
                onCloseAllTabs = onCloseAllTabs?.let { callback ->
                    {
                        expanded = false
                        callback()
                    }
                },
                onTabSelected = { tab ->
                    expanded = false
                    onTabSelected(tab)
                },
                onTabClosed = onTabClosed,
                onTabOrderCommitted = onTabOrderCommitted,
                cover = cover,
            )
        }
    }
}

/** Panel body: header, one row per open tab, and an optional close-all action. */
@Composable
fun SharedOpenTabsMenuContent(
    tabs: List<SharedOpenTab>,
    label: String,
    openCountLabel: String,
    epubLabel: String,
    pdfLabel: String,
    closeTabDescription: String,
    onTabSelected: (SharedOpenTab) -> Unit,
    onTabClosed: (SharedOpenTab) -> Unit,
    dragToReorderDescription: String = "Drag to reorder",
    onTabOrderCommitted: ((kind: ReaderTabKind, orderedBookIds: List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    chapterLabel: (Int) -> String = { "Chapter $it" },
    pageLabel: (Int) -> String = { "Page $it" },
    percentLabel: (Int) -> String = { "$it%" },
    closeAllLabel: String? = null,
    onCloseAllTabs: (() -> Unit)? = null,
    cover: @Composable (SharedOpenTab, Modifier) -> Unit = { tab, coverModifier ->
        DefaultOpenTabCover(tab, coverModifier)
    },
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val width = with(density) { windowSize.width.toDp() }
        .minus(24.dp)
        .coerceIn(200.dp, 380.dp)
    val textTabs = tabs.filter { it.kind == ReaderTabKind.TEXT }
    val fixedTabs = tabs.filter { it.kind == ReaderTabKind.FIXED_LAYOUT }
    val showGroupLabels = textTabs.isNotEmpty() && fixedTabs.isNotEmpty()

    Column(
        modifier = modifier
            .width(width)
            .testTag("HomeTabsPanel"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                openCountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (textTabs.isNotEmpty()) {
            OpenTabsGroup(
                tabs = textTabs,
                sectionLabel = epubLabel.takeIf { showGroupLabels },
                positionLabel = { tab -> tab.positionLabel(chapterLabel, pageLabel, percentLabel) },
                closeTabDescription = closeTabDescription,
                dragToReorderDescription = dragToReorderDescription,
                onTabSelected = onTabSelected,
                onTabClosed = onTabClosed,
                onTabOrderCommitted = onTabOrderCommitted?.let { commit ->
                    { ordered -> commit(ReaderTabKind.TEXT, ordered) }
                },
                cover = cover,
            )
        }
        if (fixedTabs.isNotEmpty()) {
            OpenTabsGroup(
                tabs = fixedTabs,
                sectionLabel = pdfLabel.takeIf { showGroupLabels },
                positionLabel = { tab -> tab.positionLabel(chapterLabel, pageLabel, percentLabel) },
                closeTabDescription = closeTabDescription,
                dragToReorderDescription = dragToReorderDescription,
                onTabSelected = onTabSelected,
                onTabClosed = onTabClosed,
                onTabOrderCommitted = onTabOrderCommitted?.let { commit ->
                    { ordered -> commit(ReaderTabKind.FIXED_LAYOUT, ordered) }
                },
                cover = cover,
            )
        }
        if (onCloseAllTabs != null && closeAllLabel != null && tabs.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            TextButton(
                onClick = onCloseAllTabs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("HomeTabsCloseAll"),
            ) {
                Text(
                    closeAllLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun OpenTabsSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** Rows one group shows before it scrolls; the panel must not grow with the library. */
private const val OpenTabsVisibleRows = 3
private val OpenTabRowHeight = 60.dp

/** Carries a scroll that has been applied but whose layout has not been measured yet. */
private class PendingWindowScroll {
    var value = 0f
}

/** The lifted row's centre must reach this close to the window edge before the window scrolls. */
private val OpenTabAutoScrollEdge = 24.dp

/**
 * How far the lifted row's centre is past the window's edge band, in pixels: negative when it is
 * above the band, positive when it is below it and zero while it sits comfortably inside.
 */
internal fun openTabDragEdgeOverflow(
    liftedCenter: Float,
    viewportStart: Int,
    viewportEnd: Int,
    edge: Float,
): Float = when {
    liftedCenter < viewportStart + edge -> liftedCenter - (viewportStart + edge)
    liftedCenter > viewportEnd - edge -> liftedCenter - (viewportEnd - edge)
    else -> 0f
}

/**
 * One reader's tab rows: at most [OpenTabsVisibleRows] are on screen, the rest scroll inside the
 * window, so EPUB and PDF keep independent lengths without the panel growing past the bar.
 *
 * Rows are dragged by their handle with [SharedToolbarDragDropState], the same machinery as the
 * toolbar customiser: the lifted row follows the finger and the rows it passes make room. The
 * order a drag builds lives here until the drag ends and is reported once through
 * [onTabOrderCommitted]: reporting every crossing to the caller left the slot change a few frames
 * behind its offset correction, which read as the lifted row jumping back up and dropping again.
 *
 * The window scrolls only while the lifted row is pushed into the last [OpenTabAutoScrollEdge] of
 * it, and by exactly the overflow of that push, so the rows already on screen never move on their
 * own and the window travels only as far as the finger asks.
 */
@Composable
private fun OpenTabsGroup(
    tabs: List<SharedOpenTab>,
    sectionLabel: String?,
    positionLabel: (SharedOpenTab) -> String?,
    closeTabDescription: String,
    dragToReorderDescription: String,
    onTabSelected: (SharedOpenTab) -> Unit,
    onTabClosed: (SharedOpenTab) -> Unit,
    onTabOrderCommitted: ((orderedBookIds: List<String>) -> Unit)?,
    cover: @Composable (SharedOpenTab, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Set on drag start and owned by the drag until the caller's list has caught up with it.
    var draggedOrder by remember { mutableStateOf<List<String>?>(null) }
    // A keyed lazy list follows its first visible row into that row's new index, so a reorder that
    // crosses the top of the window scrolls the window by a row on its own. The position is pinned
    // back before the next measure so that only the lifted row changes slot.
    var pinnedScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val dragState = remember(listState) {
        SharedToolbarDragDropState(listState) { from, target ->
            draggedOrder?.let { order ->
                val fromIndex = order.indexOf(from)
                val targetIndex = order.indexOf(target)
                if (fromIndex >= 0 && targetIndex >= 0) {
                    val next = order.movedItem(fromIndex, targetIndex)
                    if (next != order) {
                        pinnedScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        draggedOrder = next
                    }
                }
            }
        }
    }
    val commitOrder = rememberUpdatedState(onTabOrderCommitted)
    val canReorder = onTabOrderCommitted != null && tabs.size > 1
    val order = draggedOrder
    val orderedTabs = if (order == null) {
        tabs
    } else {
        val tabsById = tabs.associateBy { it.bookId }
        order.mapNotNull { tabsById[it] }
    }

    // The finished order reaches this composition as a state change a few frames after the drop;
    // until it does, keep drawing the order the drag produced instead of snapping back.
    LaunchedEffect(tabs) {
        val dragged = draggedOrder ?: return@LaunchedEffect
        if (dragState.draggedItemId != null) return@LaunchedEffect
        val openIds = tabs.map { it.bookId }
        if (openIds == dragged || openIds.toSet() != dragged.toSet()) draggedOrder = null
    }

    SideEffect {
        val pin = pinnedScroll ?: return@SideEffect
        pinnedScroll = null
        listState.requestScrollToItem(pin.first, pin.second)
    }

    // Pushing the lifted row into the window's edge scrolls the group by that overflow: the window
    // follows the push one to one, and the row it passes is reordered before the window moves. A
    // scroll lands at once while the layout that shows it waits for the next frame, so what is
    // already pending comes off the next reading instead of being scrolled twice.
    val edge = with(density) { OpenTabAutoScrollEdge.toPx() }
    val pendingScroll = remember { PendingWindowScroll() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { pendingScroll.value = 0f }
    }
    val scrollByPush: (String, Float) -> Unit = { liftedId, dragOffsetY ->
        val info = listState.layoutInfo
        val lifted = info.visibleItemsInfo.find { it.key == liftedId }
        if (lifted != null) {
            val overflow = openTabDragEdgeOverflow(
                liftedCenter = lifted.offset - pendingScroll.value + dragOffsetY + lifted.size / 2f,
                viewportStart = info.viewportStartOffset,
                viewportEnd = info.viewportEndOffset,
                edge = edge,
            )
            if (overflow != 0f) pendingScroll.value += listState.dispatchRawDelta(overflow)
        }
    }

    if (sectionLabel != null) OpenTabsSectionLabel(sectionLabel)
    LazyColumn(
        state = listState,
        // The window must not follow the finger through this group: while a row is lifted the
        // drag owns the list, and it scrolls only where the drag asks it to.
        userScrollEnabled = dragState.draggedItemId == null,
        // A fixed height, not "wrap up to a cap": a wrap-content lazy list shifts its own scroll
        // position when its items are reordered, which made the window jump a row mid-drag.
        modifier = Modifier
            .fillMaxWidth()
            .height(OpenTabRowHeight * minOf(orderedTabs.size, OpenTabsVisibleRows)),
    ) {
        items(orderedTabs, key = { it.bookId }) { tab ->
            val isDragged = dragState.draggedItemId == tab.bookId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isDragged) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationY = dragState.dragOffset.y
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 8.dp.toPx()
                        }
                    },
            ) {
                OpenTabRow(
                    tab = tab,
                    positionLabel = positionLabel(tab),
                    closeTabDescription = closeTabDescription,
                    dragToReorderDescription = dragToReorderDescription,
                    dragEnabled = canReorder,
                    isDragging = isDragged,
                    onSelect = { onTabSelected(tab) },
                    onClose = { onTabClosed(tab) },
                    onDragStart = {
                        draggedOrder = orderedTabs.map { it.bookId }
                        dragState.onDragStart(tab.bookId)
                    },
                    onDrag = { delta ->
                        // The row is reordered against the layout the finger sees, then the window
                        // scrolls by whatever the push left past its edge.
                        dragState.onDrag(delta)
                        scrollByPush(tab.bookId, dragState.dragOffset.y)
                    },
                    onDragEnd = {
                        val finished = draggedOrder
                        dragState.onDragEnd()
                        if (finished != null && finished != tabs.map { it.bookId }) {
                            commitOrder.value?.invoke(finished)
                        } else {
                            draggedOrder = null
                        }
                    },
                    cover = cover,
                )
            }
        }
    }
}

@Composable
private fun OpenTabRow(
    tab: SharedOpenTab,
    positionLabel: String?,
    closeTabDescription: String,
    dragToReorderDescription: String,
    dragEnabled: Boolean,
    isDragging: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    cover: @Composable (SharedOpenTab, Modifier) -> Unit,
) {
    val active = tab.isActive
    // Only the lifted row is tinted, exactly like the toolbar customiser: a tinted row always means
    // "in your hand". The tab a reader is showing is marked by its rail and title instead, so the
    // panel does not look like a list with one selected file.
    val container = if (isDragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = when {
        isDragging -> MaterialTheme.colorScheme.onPrimaryContainer
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OpenTabRowHeight)
            .background(container)
            .clickable { onSelect() }
            .testTag("HomeTab_${tab.bookId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Spacer(Modifier.width(9.dp))
        cover(
            tab,
            Modifier
                .size(width = 30.dp, height = 42.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
            Text(
                tab.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = positionLabel
            if (meta != null) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val progress = tab.progressPercent
            if (progress != null) {
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                            .fillMaxHeight()
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onClose() }
                .testTag("HomeTabClose_${tab.bookId}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = closeTabDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (dragEnabled) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .testTag("HomeTabDrag_${tab.bookId}")
                    .pointerInput(tab.bookId) {
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
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = dragToReorderDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Position plus progress, e.g. "Chapter 2 · 44%". Chapter/page first because it places the
 * reader; the percentage alone does not say where in the book the tab is.
 */
private fun SharedOpenTab.positionLabel(
    chapterLabel: (Int) -> String,
    pageLabel: (Int) -> String,
    percentLabel: (Int) -> String,
): String? {
    val position = when {
        chapterNumber != null -> chapterLabel(chapterNumber)
        pageNumber != null -> pageLabel(pageNumber)
        else -> null
    }
    val percent = progressPercent?.takeIf { it > 0 }?.let(percentLabel)
    return listOfNotNull(position, percent)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

/**
 * The shared library's tab switcher list: text tabs first, then fixed-layout tabs, each in tab
 * order. The projection has already resolved every open tab id against the library, so this only
 * adds presentation state; each reader keeps its own active tab, so both groups can have one.
 */
fun SharedReaderScreenState.openTabsForSwitcher(
    usePdfFileNameAsDisplayName: Boolean = this.usePdfFileNameAsDisplayName,
): List<SharedOpenTab> =
    textOpenTabs.toSharedOpenTabs(activeTextTabBookId, usePdfFileNameAsDisplayName) +
        openTabs.toSharedOpenTabs(activeTabBookId, usePdfFileNameAsDisplayName)

private fun List<BookItem>.toSharedOpenTabs(
    activeBookId: String?,
    usePdfFileNameAsDisplayName: Boolean,
): List<SharedOpenTab> = mapNotNull { item ->
    val kind = item.readerTabKind() ?: return@mapNotNull null
    SharedOpenTab(
        bookId = item.id,
        title = item.cardTitle(usePdfFileNameAsDisplayName),
        kind = kind,
        isActive = item.id == activeBookId,
        chapterNumber = if (kind == ReaderTabKind.TEXT) {
            item.readerPosition?.chapterIndex?.plus(1)?.takeIf { it > 0 }
        } else {
            null
        },
        pageNumber = if (kind == ReaderTabKind.FIXED_LAYOUT) {
            item.lastPageIndex?.plus(1)?.takeIf { it > 0 }
        } else {
            null
        },
        progressPercent = item.progressPercentage
            ?.coerceIn(0f, 100f)
            ?.roundToInt()
            ?.takeIf { it > 0 },
    )
}

@Composable
private fun DefaultOpenTabCover(tab: SharedOpenTab, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (tab.isText) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
