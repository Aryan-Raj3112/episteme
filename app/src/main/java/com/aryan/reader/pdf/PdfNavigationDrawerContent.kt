// PdfNavigationDrawerContent.kt
package com.aryan.reader.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfNavigationDrawerContent(
    flatTableOfContents: List<TocEntry>,
    bookmarks: Set<PdfBookmark>,
    userHighlights: List<PdfUserHighlight>,
    currentPage: Int,
    customHighlightColors: Map<PdfHighlightColor, Color>,
    onPageSelected: (Int) -> Unit,
    onRenameBookmark: (PdfBookmark, String) -> Unit,
    onDeleteBookmark: (PdfBookmark) -> Unit,
    onDeleteHighlight: (PdfUserHighlight) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val drawerPagerState = rememberPagerState(pageCount = { 3 })
    val drawerScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = drawerPagerState.currentPage) {
            Tab(selected = drawerPagerState.currentPage == 0, onClick = {
                drawerScope.launch { drawerPagerState.animateScrollToPage(0) }
            }, text = { Text("Chapters") })
            Tab(
                selected = drawerPagerState.currentPage == 1,
                onClick = {
                    drawerScope.launch { drawerPagerState.animateScrollToPage(1) }
                },
                text = { Text("Bookmarks") },
                modifier = Modifier.testTag("BookmarksTab")
            )
            Tab(
                selected = drawerPagerState.currentPage == 2,
                onClick = {
                    drawerScope.launch { drawerPagerState.animateScrollToPage(2) }
                },
                text = { Text("Highlights") },
                modifier = Modifier.testTag("HighlightsTab")
            )
        }

        HorizontalPager(
            state = drawerPagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            when (page) {
                0 -> { // Chapters Page
                    if (flatTableOfContents.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Chapters are not available for this book.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()

                        val allParentIndices = remember(flatTableOfContents) {
                            flatTableOfContents.indices.filter { i ->
                                val next = flatTableOfContents.getOrNull(i + 1)
                                next != null && next.nestLevel > flatTableOfContents[i].nestLevel
                            }.toSet()
                        }

                        var expandedEntryIndices by rememberSaveable(flatTableOfContents) {
                            mutableStateOf(allParentIndices)
                        }

                        val visibleItemInfo by remember(flatTableOfContents) {
                            derivedStateOf {
                                val result = mutableListOf<Pair<Int, TocEntry>>()
                                val visibilityStack = BooleanArray(20) { false }
                                visibilityStack[0] = true

                                for (i in flatTableOfContents.indices) {
                                    val entry = flatTableOfContents[i]
                                    val level = entry.nestLevel.coerceIn(0, 19)

                                    if (visibilityStack[level]) {
                                        result.add(i to entry)
                                        val isExpanded = expandedEntryIndices.contains(i)
                                        if (level + 1 < visibilityStack.size) {
                                            visibilityStack[level + 1] = isExpanded
                                        }
                                    } else {
                                        if (level + 1 < visibilityStack.size) {
                                            visibilityStack[level + 1] = false
                                        }
                                    }
                                }
                                result
                            }
                        }

                        val currentTocEntry by remember(currentPage, flatTableOfContents) {
                            derivedStateOf {
                                flatTableOfContents.lastOrNull { it.pageIndex <= currentPage }
                            }
                        }

                        val onScrollToCurrent = {
                            drawerScope.launch {
                                val targetEntry = currentTocEntry ?: return@launch
                                val targetOriginalIndex = flatTableOfContents.indexOf(targetEntry)
                                if (targetOriginalIndex != -1) {
                                    var currentLevel = targetEntry.nestLevel
                                    val newExpanded = expandedEntryIndices.toMutableSet()

                                    for (i in targetOriginalIndex downTo 0) {
                                        val entry = flatTableOfContents[i]
                                        if (entry.nestLevel < currentLevel) {
                                            newExpanded.add(i)
                                            currentLevel = entry.nestLevel
                                        }
                                        if (currentLevel == 0) break
                                    }

                                    expandedEntryIndices = newExpanded

                                    val visibleIdx = visibleItemInfo.indexOfFirst { it.second == targetEntry }

                                    if (visibleIdx != -1) {
                                        var attempts = 0
                                        while (listState.layoutInfo.totalItemsCount <= visibleIdx && attempts < 10) {
                                            delay(30)
                                            attempts++
                                        }

                                        listState.animateScrollToItem(visibleIdx)
                                    }
                                }
                            }
                            Unit
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { expandedEntryIndices = flatTableOfContents.indices.toSet() }) {
                                    Text("Expand All")
                                }
                                TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                                    Text("Collapse All")
                                }
                                TextButton(onClick = onScrollToCurrent) {
                                    Text("Locate")
                                }
                            }

                            HorizontalDivider()

                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(end = 12.dp)
                                ) {
                                    items(
                                        items = visibleItemInfo,
                                        key = { it.second.title + it.first }
                                    ) { item ->
                                        val (originalIndex, entry) = item

                                        val nextItem = flatTableOfContents.getOrNull(originalIndex + 1)
                                        val hasChildren = nextItem != null && nextItem.nestLevel > entry.nestLevel
                                        val isExpanded = expandedEntryIndices.contains(originalIndex)
                                        val isCurrentChapter = entry == currentTocEntry

                                        PdfTocTreeItem(
                                            label = entry.title,
                                            nestLevel = entry.nestLevel,
                                            isExpanded = isExpanded,
                                            hasChildren = hasChildren,
                                            isCurrent = isCurrentChapter,
                                            onToggleExpand = {
                                                expandedEntryIndices = if (isExpanded) {
                                                    expandedEntryIndices - originalIndex
                                                } else {
                                                    expandedEntryIndices + originalIndex
                                                }
                                            },
                                            onClick = {
                                                onCloseDrawer()
                                                onPageSelected(entry.pageIndex)
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
                    }
                }

                1 -> { // Bookmarks Page
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
                        var bookmarkMenuExpandedFor by remember { mutableStateOf<PdfBookmark?>(null) }
                        var showDeleteConfirmDialogFor by remember { mutableStateOf<PdfBookmark?>(null) }
                        var showRenameBookmarkDialog by remember { mutableStateOf<PdfBookmark?>(null) }

                        val sortedBookmarks = remember(bookmarks) {
                            bookmarks.sortedBy { it.pageIndex }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = sortedBookmarks, key = { index, bookmark ->
                                    "bm_${index}_${bookmark.pageIndex}"
                                }) { _, bookmark ->
                                ListItem(headlineContent = {
                                    Text(
                                        bookmark.title,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }, supportingContent = {
                                    Text(
                                        "Page ${bookmark.pageIndex + 1} of ${bookmark.totalPages}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }, trailingContent = {
                                    Box {
                                        IconButton(
                                            onClick = {
                                                bookmarkMenuExpandedFor = bookmark
                                            }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "More options for bookmark"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = bookmarkMenuExpandedFor == bookmark,
                                            onDismissRequest = {
                                                bookmarkMenuExpandedFor = null
                                            }) {
                                            DropdownMenuItem(text = {
                                                Text("Rename")
                                            }, onClick = {
                                                showRenameBookmarkDialog = bookmark
                                                bookmarkMenuExpandedFor = null
                                            })
                                            DropdownMenuItem(text = {
                                                Text("Delete")
                                            }, onClick = {
                                                showDeleteConfirmDialogFor = bookmark
                                                bookmarkMenuExpandedFor = null
                                            })
                                        }
                                    }
                                }, modifier = Modifier
                                    .clickable {
                                        onCloseDrawer()
                                        onPageSelected(bookmark.pageIndex)
                                    }
                                    .testTag(
                                        "BookmarkItem_${bookmark.pageIndex}"
                                    ))
                                HorizontalDivider()
                            }
                        }

                        showRenameBookmarkDialog?.let { bookmarkToRename ->
                            var newTitle by remember { mutableStateOf("") }

                            AlertDialog(onDismissRequest = {
                                showRenameBookmarkDialog = null
                            }, title = { Text("Rename Bookmark") }, text = {
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text("New Title") },
                                    placeholder = {
                                        Text(
                                            text = bookmarkToRename.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.6f
                                            )
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }, confirmButton = {
                                TextButton(
                                    onClick = {
                                        onRenameBookmark(bookmarkToRename, newTitle)
                                        showRenameBookmarkDialog = null
                                    }) { Text("Save") }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showRenameBookmarkDialog = null
                                    }) { Text("Cancel") }
                            })
                        }

                        showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
                            AlertDialog(onDismissRequest = {
                                showDeleteConfirmDialogFor = null
                            }, title = { Text("Delete Bookmark?") }, text = {
                                Text(
                                    "Are you sure you want to permanently delete this bookmark?"
                                )
                            }, confirmButton = {
                                TextButton(
                                    onClick = {
                                        onDeleteBookmark(bookmarkToDelete)
                                        showDeleteConfirmDialogFor = null
                                    }) { Text("Delete") }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialogFor = null
                                    }) { Text("Cancel") }
                            })
                        }
                    }
                }
                2 -> { // Highlights Page
                    if (userHighlights.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "You haven't added any highlights yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        var showDeleteConfirmDialogFor by remember { mutableStateOf<PdfUserHighlight?>(null) }
                        var filterWithNotesOnly by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !filterWithNotesOnly,
                                    onClick = { filterWithNotesOnly = false },
                                    label = { Text("All") }
                                )
                                FilterChip(
                                    selected = filterWithNotesOnly,
                                    onClick = { filterWithNotesOnly = true },
                                    label = { Text("With Notes") }
                                )
                            }

                            val filteredHighlights = if (filterWithNotesOnly) {
                                userHighlights.filter { !it.note.isNullOrBlank() }
                            } else {
                                userHighlights.toList()
                            }

                            val sortedHighlights = remember(filteredHighlights) {
                                filteredHighlights.sortedBy { it.pageIndex }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(
                                    items = sortedHighlights,
                                    key = { _, highlight -> highlight.id }
                                ) { _, highlight ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = highlight.text.ifBlank { "Highlighted section" },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        supportingContent = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val displayColor = customHighlightColors[highlight.color] ?: highlight.color.color

                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .background(displayColor, CircleShape)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "Page ${highlight.pageIndex + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (!highlight.note.isNullOrBlank()) {
                                                    Spacer(Modifier.height(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = highlight.note,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                            modifier = Modifier.padding(12.dp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        trailingContent = {
                                            Box {
                                                var highlightMenuExpanded by remember { mutableStateOf(false) }
                                                IconButton(onClick = { highlightMenuExpanded = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                                }
                                                DropdownMenu(
                                                    expanded = highlightMenuExpanded,
                                                    onDismissRequest = { highlightMenuExpanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (highlight.note.isNullOrBlank()) "Add Note" else "Edit Note") },
                                                        onClick = {
                                                            onNoteRequested(highlight.id)
                                                            highlightMenuExpanded = false
                                                            onCloseDrawer()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete") },
                                                        onClick = {
                                                            showDeleteConfirmDialogFor = highlight
                                                            highlightMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            onCloseDrawer()
                                            onPageSelected(highlight.pageIndex)
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }

                        showDeleteConfirmDialogFor?.let { highlightToDelete ->
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirmDialogFor = null },
                                title = { Text("Delete Highlight?") },
                                text = { Text("Are you sure you want to permanently delete this highlight?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDeleteHighlight(highlightToDelete)
                                            showDeleteConfirmDialogFor = null
                                        }
                                    ) { Text("Delete") }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDeleteConfirmDialogFor = null }
                                    ) { Text("Cancel") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}