/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import com.aryan.reader.RenderMode
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.epub.EpubTocEntry
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val scrollbarState by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()

            if (totalItems == 0 || visibleItemsInfo.isEmpty() || viewportHeight <= 0f) {
                return@derivedStateOf null
            }

            val averageItemHeight = visibleItemsInfo.fastSumBy { it.size } / visibleItemsInfo.size.toFloat()
            val estimatedContentHeight = (averageItemHeight * totalItems).coerceAtLeast(viewportHeight)
            val viewportRatio = viewportHeight / estimatedContentHeight

            if (viewportRatio >= 1f) return@derivedStateOf null

            val thumbHeight = (viewportHeight * viewportRatio).coerceIn(80f, viewportHeight / 2)

            val firstItemIndex = listState.firstVisibleItemIndex
            val firstItemOffset = listState.firstVisibleItemScrollOffset
            val currentScrollPixels = (firstItemIndex * averageItemHeight) + firstItemOffset
            val maxScrollPixels = estimatedContentHeight - viewportHeight
            val scrollProgress = (currentScrollPixels / maxScrollPixels).coerceIn(0f, 1f)
            val trackHeight = viewportHeight - thumbHeight
            val thumbOffset = trackHeight * scrollProgress

            ScrollbarCalculations(
                thumbHeight = thumbHeight,
                thumbOffset = thumbOffset,
                contentHeight = estimatedContentHeight,
                viewportHeight = viewportHeight
            )
        }
    }

    val targetAlpha = if (listState.isScrollInProgress || isDragged) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "ScrollbarAlpha"
    )

    if (scrollbarState != null) {
        val state = scrollbarState!!

        val draggableState = rememberDraggableState { delta ->
            val trackHeight = state.viewportHeight - state.thumbHeight
            if (trackHeight > 0) {
                val scrollRatio = delta / trackHeight
                val totalScrollableDistance = state.contentHeight - state.viewportHeight
                val scrollDelta = scrollRatio * totalScrollableDistance

                listState.dispatchRawDelta(scrollDelta)
            }
        }

        Box(
            modifier = modifier
                .width(30.dp)
                .fillMaxHeight()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    interactionSource = interactionSource
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationY = state.thumbOffset
                    }
                    .padding(end = 4.dp)
                    .width(6.dp)
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { state.thumbHeight.toDp() })
                    .alpha(alpha)
                    .background(
                        color = if (isDragged) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(100)
                    )
            )
        }
    }
}

private data class ScrollbarCalculations(
    val thumbHeight: Float,
    val thumbOffset: Float,
    val contentHeight: Float,
    val viewportHeight: Float
)

@Composable
fun EpubReaderDrawerSheet(
    chapters: List<EpubChapter>,
    tableOfContents: List<EpubTocEntry>,
    activeFragmentId: String?,
    bookmarks: Set<Bookmark>,
    userHighlights: List<UserHighlight>,
    currentChapterIndex: Int,
    currentChapterInPaginatedMode: Int?,
    renderMode: RenderMode,
    onNavigateToChapter: (Int) -> Unit,
    onNavigateToTocEntry: (EpubTocEntry) -> Unit,
    onNavigateToBookmark: (Bookmark) -> Unit,
    onNavigateToHighlight: (UserHighlight) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onRenameBookmark: (Bookmark, String) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val drawerPagerState = rememberPagerState(pageCount = { 3 })
        val drawerScope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = drawerPagerState.currentPage) {
                Tab(
                    selected = drawerPagerState.currentPage == 0,
                    onClick = { drawerScope.launch { drawerPagerState.animateScrollToPage(0) } },
                    text = { Text("Chapters") }
                )
                Tab(
                    selected = drawerPagerState.currentPage == 1,
                    onClick = { drawerScope.launch { drawerPagerState.animateScrollToPage(1) } },
                    text = { Text("Bookmarks") }
                )
                Tab(
                    selected = drawerPagerState.currentPage == 2,
                    onClick = { drawerScope.launch { drawerPagerState.animateScrollToPage(2) } },
                    text = { Text("Highlights") }
                )
            }

            HorizontalPager(
                state = drawerPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> ChaptersList(
                        chapters = chapters,
                        tocEntries = tableOfContents,
                        currentChapterIndex = currentChapterIndex,
                        currentChapterInPaginatedMode = currentChapterInPaginatedMode,
                        renderMode = renderMode,
                        onNavigateToTocEntry = onNavigateToTocEntry,
                        onNavigateToChapter = onNavigateToChapter,
                        activeFragmentId = activeFragmentId
                    )
                    1 -> BookmarksList(
                        bookmarks = bookmarks,
                        onNavigateToBookmark = onNavigateToBookmark,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark
                    )
                    2 -> HighlightsList(
                        userHighlights = userHighlights,
                        chapters = chapters,
                        onNavigateToHighlight = onNavigateToHighlight,
                        onDeleteHighlight = onDeleteHighlight
                    )
                }
            }
        }
    }
}

@Composable
private fun ChaptersList(
    chapters: List<EpubChapter>,
    tocEntries: List<EpubTocEntry>,
    currentChapterIndex: Int,
    currentChapterInPaginatedMode: Int?,
    renderMode: RenderMode,
    activeFragmentId: String?,
    onNavigateToTocEntry: (EpubTocEntry) -> Unit,
    onNavigateToChapter: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    val effectiveToc = remember(tocEntries, chapters) {
        tocEntries.ifEmpty {
            chapters.map { EpubTocEntry(it.title, it.absPath, null, it.depth) }
        }
    }

    val currentChapterPath = remember(chapters, currentChapterIndex, currentChapterInPaginatedMode, renderMode) {
        val idx = when (renderMode) {
            RenderMode.PAGINATED -> currentChapterInPaginatedMode ?: -1
            RenderMode.VERTICAL_SCROLL -> currentChapterIndex
        }
        chapters.getOrNull(idx)?.absPath
    }

    val firstEntryForCurrentChapter = remember(effectiveToc, currentChapterPath) {
        val entry = effectiveToc.firstOrNull { it.absolutePath == currentChapterPath }
        Timber.tag("FRAG_NAV_DEBUG").d("Computed First Entry for Chapter: '${entry?.label}' (Path: $currentChapterPath)")
        entry
    }

    val allParentIndices = remember(effectiveToc) {
        effectiveToc.indices.filter { i ->
            val next = effectiveToc.getOrNull(i + 1)
            next != null && next.depth > effectiveToc[i].depth
        }.toSet()
    }

    var expandedEntryIndices by rememberSaveable(effectiveToc) {
        mutableStateOf(allParentIndices)
    }

    val visibleItemInfo = remember(effectiveToc, expandedEntryIndices) {
        val result = mutableListOf<Pair<Int, EpubTocEntry>>()
        val visibilityStack = BooleanArray(50) { false }
        visibilityStack[0] = true

        for (i in effectiveToc.indices) {
            val entry = effectiveToc[i]
            val depth = entry.depth.coerceIn(0, 49)

            if (visibilityStack[depth]) {
                result.add(i to entry)

                val isExpanded = expandedEntryIndices.contains(i)
                if (depth + 1 < visibilityStack.size) {
                    visibilityStack[depth + 1] = isExpanded
                }
            } else {
                if (depth + 1 < visibilityStack.size) {
                    visibilityStack[depth + 1] = false
                }
            }
        }
        result
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight().padding(end = 12.dp)
        ) {
            items(
                items = visibleItemInfo,
                key = { (index, entry) -> "${entry.absolutePath}_${entry.fragmentId}_$index" }
            ) { (originalIndex, entry) ->
                val nextItem = effectiveToc.getOrNull(originalIndex + 1)
                val hasChildren = nextItem != null && nextItem.depth > entry.depth
                val isExpanded = expandedEntryIndices.contains(originalIndex)

                // HIGHLIGHT LOGIC FIXED
                val isCurrentPath = currentChapterPath == entry.absolutePath
                val matchesFragment = entry.fragmentId == activeFragmentId

                // Fallback logic
                val isFallback = activeFragmentId == null && entry == firstEntryForCurrentChapter
                val isHighlighting = isCurrentPath && (matchesFragment || isFallback)

                if (isCurrentPath) {
                    Timber.tag("FRAG_NAV_DEBUG").d("Row: '${entry.label}' | isPathMatch: $isCurrentPath | isFragMatch: $matchesFragment | isFallback: $isFallback")
                }

                if (isCurrentPath) {
                    Timber.tag("FRAG_NAV_DEBUG").d("Entry: '${entry.label}' | ID: ${entry.fragmentId} | Active: $activeFragmentId | Highlight: $isHighlighting")
                }

                TocTreeItem(
                    label = entry.label,
                    depth = entry.depth,
                    isExpanded = isExpanded,
                    hasChildren = hasChildren,
                    isCurrent = isHighlighting,
                    onToggleExpand = {
                        expandedEntryIndices = if (isExpanded) {
                            expandedEntryIndices - originalIndex
                        } else {
                            expandedEntryIndices + originalIndex
                        }
                    },
                    onClick = {
                        if (tocEntries.isEmpty()) {
                            onNavigateToChapter(originalIndex)
                        } else {
                            onNavigateToTocEntry(entry)
                        }
                    }
                )
            }
        }

        VerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun TocTreeItem(
    label: String,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
        label = "TocItemBackground"
    )

    val contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((16 * depth).dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    enabled = hasChildren,
                    onClick = onToggleExpand
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = label,
            style = if (depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
    }
}

@Composable
private fun BookmarksList(
    bookmarks: Set<Bookmark>,
    onNavigateToBookmark: (Bookmark) -> Unit,
    onRenameBookmark: (Bookmark, String) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "You haven't added any bookmarks yet.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    } else {
        var bookmarkMenuExpandedFor by remember { mutableStateOf<Bookmark?>(null) }
        var showDeleteConfirmDialogFor by remember { mutableStateOf<Bookmark?>(null) }
        var showRenameBookmarkDialog by remember { mutableStateOf<Bookmark?>(null) }

        val listState = rememberLazyListState()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 4.dp)
            ) {
                items(
                    items = bookmarks.distinctBy { it.cfi }.sortedBy { it.cfi },
                    key = { it.cfi }
                ) { bookmark ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = bookmark.label?.takeIf { it.isNotBlank() } ?: bookmark.snippet.ifBlank { "Bookmark" },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = bookmark.chapterTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (bookmark.pageInChapter != null && bookmark.totalPagesInChapter != null) {
                                    Text(
                                        text = "Page ${bookmark.pageInChapter} of ${bookmark.totalPagesInChapter}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { bookmarkMenuExpandedFor = bookmark }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options for bookmark"
                                    )
                                }
                                DropdownMenu(
                                    expanded = bookmarkMenuExpandedFor == bookmark,
                                    onDismissRequest = { bookmarkMenuExpandedFor = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            showRenameBookmarkDialog = bookmark
                                            bookmarkMenuExpandedFor = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showDeleteConfirmDialogFor = bookmark
                                            bookmarkMenuExpandedFor = null
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToBookmark(bookmark) }
                    )
                    HorizontalDivider()
                }
            }

            VerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        showRenameBookmarkDialog?.let { bookmarkToRename ->
            var newTitle by remember { mutableStateOf("") }
            val currentName = bookmarkToRename.label?.takeIf { it.isNotBlank() } ?: bookmarkToRename.snippet

            AlertDialog(
                onDismissRequest = { showRenameBookmarkDialog = null },
                title = { Text("Rename Bookmark") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("New Name") },
                        placeholder = {
                            Text(
                                text = currentName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onRenameBookmark(bookmarkToRename, newTitle)
                            }
                            showRenameBookmarkDialog = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameBookmarkDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialogFor = null },
                title = { Text("Delete Bookmark?") },
                text = { Text("Are you sure you want to permanently delete this bookmark?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteBookmark(bookmarkToDelete)
                            showDeleteConfirmDialogFor = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialogFor = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun HighlightsList(
    userHighlights: List<UserHighlight>,
    chapters: List<EpubChapter>,
    onNavigateToHighlight: (UserHighlight) -> Unit,
    onDeleteHighlight: (UserHighlight) -> Unit
) {
    if (userHighlights.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No highlights yet.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    } else {
        var highlightMenuExpandedFor by remember { mutableStateOf<UserHighlight?>(null) }
        var showHighlightDeleteDialogFor by remember { mutableStateOf<UserHighlight?>(null) }

        val listState = rememberLazyListState()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 4.dp)
            ) {
                items(
                    items = userHighlights.sortedBy { it.chapterIndex },
                    key = { it.id }
                ) { highlight ->
                    val chapterTitle = chapters.getOrNull(highlight.chapterIndex)?.title ?: "Unknown Chapter"

                    ListItem(
                        headlineContent = {
                            Text(
                                text = highlight.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(highlight.color.color, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { highlightMenuExpandedFor = highlight }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = highlightMenuExpandedFor == highlight,
                                    onDismissRequest = { highlightMenuExpandedFor = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showHighlightDeleteDialogFor = highlight
                                            highlightMenuExpandedFor = null
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToHighlight(highlight) }
                    )
                    HorizontalDivider()
                }
            }

            VerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        showHighlightDeleteDialogFor?.let { highlightToDelete ->
            AlertDialog(
                onDismissRequest = { showHighlightDeleteDialogFor = null },
                title = { Text("Delete Highlight?") },
                text = { Text("Are you sure you want to permanently delete this highlight?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteHighlight(highlightToDelete)
                            showHighlightDeleteDialogFor = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHighlightDeleteDialogFor = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}