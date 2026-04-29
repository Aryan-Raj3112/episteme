package com.aryan.reader.pdf

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.R
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import androidx.core.graphics.createBitmap

private const val MAX_FIXED_RECURSION = 128

internal data class PdfBookmark(val pageIndex: Int, val title: String, val totalPages: Int)

internal data class TocEntry(val title: String, val pageIndex: Int, val nestLevel: Int)

/**
 * Patches the library bug where siblings are truncated due to depth-state leakage.
 */
suspend fun PdfDocumentKt.getFixedTableOfContents(): List<Bookmark> {
    val tag = "PdfTocFix"
    Timber.tag(tag).i("Starting Pure Reflection Traversal...")

    return try {
        // 1. Get the 'document' field (PdfDocumentU) from PdfDocumentKt
        val documentField = PdfDocumentKt::class.java.getDeclaredField("document").apply { isAccessible = true }
        val docUInstance = documentField.get(this) ?: return getTableOfContents()

        // 2. Get the 'nativeDocument' field from PdfDocumentU
        val nativeDocField = docUInstance.javaClass.getDeclaredField("nativeDocument").apply { isAccessible = true }
        val nativeDocInstance = nativeDocField.get(docUInstance) ?: return getTableOfContents()

        // 3. Get the native pointer (long) from PdfDocumentU
        val ptrField = docUInstance.javaClass.getDeclaredField("mNativeDocPtr").apply { isAccessible = true }
        val mNativeDocPtr = ptrField.get(docUInstance) as Long

        // 4. Look up native methods using primitive 'long' types (mandatory for JNI)
        val nClass = nativeDocInstance.javaClass
        val lp = Long::class.javaPrimitiveType!! // Shorthand for 'long'

        val getTitleM = nClass.getMethod("getBookmarkTitle", lp)
        val getDestIdxM = nClass.getMethod("getBookmarkDestIndex", lp, lp)
        val getFirstChildM = nClass.getMethod("getFirstChildBookmark", lp, lp)
        val getSiblingM = nClass.getMethod("getSiblingBookmark", lp, lp)

        val topLevel = mutableListOf<Bookmark>()
        val visited = mutableSetOf<Long>()

        /**
         * Corrected traversal: Iterative for siblings, recursive for children.
         */
        fun walk(parentList: MutableList<Bookmark>, startPtr: Long, level: Int) {
            var currentPtr = startPtr
            var itemIndex = 0

            while (currentPtr != 0L) {
                if (visited.contains(currentPtr)) break
                visited.add(currentPtr)

                val title = getTitleM.invoke(nativeDocInstance, currentPtr) as? String ?: "Untitled"
                val pageIdx = getDestIdxM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long

                Timber.tag(tag).v("Lvl $level | Item $itemIndex | Ptr: 0x${java.lang.Long.toHexString(currentPtr)} | $title")

                val bookmark = Bookmark().apply {
                    this.mNativePtr = currentPtr
                    this.title = title
                    this.pageIdx = pageIdx
                }
                parentList.add(bookmark)

                // Recursive dive into children
                val firstChild = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                if (firstChild != 0L && level < MAX_FIXED_RECURSION) {
                    walk(bookmark.children, firstChild, level + 1)
                }

                // Iterative move to next sibling
                currentPtr = getSiblingM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                itemIndex++
            }
        }

        // 5. Start from the root (Pass 0L as primitive long)
        val firstRoot = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, 0L) as Long
        if (firstRoot != 0L) {
            walk(topLevel, firstRoot, 0)
        }

        if (topLevel.isEmpty()) {
            Timber.tag(tag).w("No items found, falling back to library.")
            getTableOfContents()
        } else {
            Timber.tag(tag).i("TOC Successfully Patched! Nodes: ${visited.size}")
            topLevel
        }
    } catch (e: Exception) {
        Timber.tag(tag).e(e, "Reflection traversal critical error.")
        this.getTableOfContents()
    }
}

internal fun flattenToc(bookmarks: List<Bookmark>, level: Int = 0): List<TocEntry> {
    Timber.tag("PdfTocDebug").d("Processing level $level with ${bookmarks.size} items")
    val entries = mutableListOf<TocEntry>()
    for ((index, bookmark) in bookmarks.withIndex()) {
        val title = bookmark.title ?: "Untitled Chapter"
        val childCount = bookmark.children.size

        Timber.tag("PdfTocDebug").d(
            "Lvl $level | Item $index: \"$title\" (Page: ${bookmark.pageIdx}) | Children: $childCount"
        )

        entries.add(
            TocEntry(
                title = title,
                pageIndex = bookmark.pageIdx.toInt(),
                nestLevel = level
            )
        )

        if (childCount > 0) {
            Timber.tag("PdfTocDebug").v("Entering children of \"$title\"")
            entries.addAll(flattenToc(bookmark.children, level + 1))
            Timber.tag("PdfTocDebug").v("Returned to Lvl $level from \"$title\"")
        }
    }
    return entries
}

internal fun loadPdfBookmarksFromJson(bookmarksJson: String?): Set<PdfBookmark> {
    if (bookmarksJson.isNullOrBlank()) return emptySet()
    return try {
        val jsonArray = JSONArray(bookmarksJson)
        (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val json = jsonArray.getJSONObject(i)
                PdfBookmark(
                    pageIndex = json.getInt("pageIndex"),
                    title = json.getString("title"),
                    totalPages = json.getInt("totalPages")
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse bookmark from JSON object")
                null
            }
        }.toSet()
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse bookmarks from JSON string: $bookmarksJson")
        emptySet()
    }
}

@Composable
internal fun PdfTocTreeItem(
    label: String,
    nestLevel: Int,
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((16 * nestLevel).dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = hasChildren, onClick = onToggleExpand),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.content_desc_collapse else R.string.content_desc_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = label,
            style = if (nestLevel == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else if (nestLevel == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfNavigationDrawerContent(
    pdfDocument: ReaderDocument?,
    flatTableOfContents: List<TocEntry>,
    bookmarks: Set<PdfBookmark>,
    userHighlights: List<PdfUserHighlight>,
    currentPage: Int,
    totalPages: Int,
    customHighlightColors: Map<PdfHighlightColor, Color>,
    onPageSelected: (Int) -> Unit,
    onRenameBookmark: (PdfBookmark, String) -> Unit,
    onDeleteBookmark: (PdfBookmark) -> Unit,
    onDeleteHighlight: (PdfUserHighlight) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val drawerPagerState = rememberPagerState(pageCount = { 4 })
    val drawerScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = drawerPagerState.currentPage,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = drawerPagerState.currentPage == 0, onClick = {
                drawerScope.launch { drawerPagerState.animateScrollToPage(0) }
            }, text = { Text(stringResource(R.string.tab_chapters)) })
            Tab(
                selected = drawerPagerState.currentPage == 1,
                onClick = {
                    drawerScope.launch { drawerPagerState.animateScrollToPage(1) }
                },
                text = { Text(stringResource(R.string.tab_bookmarks)) },
                modifier = Modifier.testTag("BookmarksTab")
            )
            Tab(
                selected = drawerPagerState.currentPage == 2,
                onClick = {
                    drawerScope.launch { drawerPagerState.animateScrollToPage(2) }
                },
                text = { Text(stringResource(R.string.tab_highlights)) },
                modifier = Modifier.testTag("HighlightsTab")
            )
            Tab(
                selected = drawerPagerState.currentPage == 3,
                onClick = {
                    drawerScope.launch { drawerPagerState.animateScrollToPage(3) }
                },
                text = { Text(stringResource(R.string.tab_pages)) },
                modifier = Modifier.testTag("PagesTab")
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
                                stringResource(R.string.msg_chapters_not_available),
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
                                    Text(stringResource(R.string.action_expand_all))
                                }
                                TextButton(onClick = { expandedEntryIndices = emptySet() }) {
                                    Text(stringResource(R.string.action_collapse_all))
                                }
                                TextButton(onClick = onScrollToCurrent) {
                                    Text(stringResource(R.string.action_locate))
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
                                stringResource(R.string.no_bookmarks_yet),
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
                                        stringResource(R.string.page_of_pages, bookmark.pageIndex + 1, bookmark.totalPages),
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
                                                contentDescription = stringResource(R.string.content_desc_more_options_bookmark)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = bookmarkMenuExpandedFor == bookmark,
                                            onDismissRequest = {
                                                bookmarkMenuExpandedFor = null
                                            }) {
                                            DropdownMenuItem(text = {
                                                Text(stringResource(R.string.action_rename))
                                            }, onClick = {
                                                showRenameBookmarkDialog = bookmark
                                                bookmarkMenuExpandedFor = null
                                            })
                                            DropdownMenuItem(text = {
                                                Text(stringResource(R.string.action_delete))
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
                            }, title = { Text(stringResource(R.string.dialog_rename_bookmark)) }, text = {
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text(stringResource(R.string.label_new_title)) },
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
                                    }) { Text(stringResource(R.string.action_save)) }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showRenameBookmarkDialog = null
                                    }) { Text(stringResource(R.string.action_cancel)) }
                            })
                        }

                        showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
                            AlertDialog(onDismissRequest = {
                                showDeleteConfirmDialogFor = null
                            }, title = { Text(stringResource(R.string.dialog_delete_bookmark)) }, text = {
                                Text(
                                    stringResource(R.string.dialog_delete_bookmark_desc)
                                )
                            }, confirmButton = {
                                TextButton(
                                    onClick = {
                                        onDeleteBookmark(bookmarkToDelete)
                                        showDeleteConfirmDialogFor = null
                                    }) { Text(stringResource(R.string.action_delete)) }
                            }, dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialogFor = null
                                    }) { Text(stringResource(R.string.action_cancel)) }
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
                                stringResource(R.string.no_highlights_yet),
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
                                    label = { Text(stringResource(R.string.read_status_all)) }
                                )
                                FilterChip(
                                    selected = filterWithNotesOnly,
                                    onClick = { filterWithNotesOnly = true },
                                    label = { Text(stringResource(R.string.filter_with_notes)) }
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
                                                text = highlight.text.ifBlank { stringResource(R.string.msg_highlighted_section_default) },
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
                                                        stringResource(R.string.pdf_page_short, highlight.pageIndex + 1),
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
                                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_options))
                                                }
                                                DropdownMenu(
                                                    expanded = highlightMenuExpanded,
                                                    onDismissRequest = { highlightMenuExpanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(
                                                                    if (highlight.note.isNullOrBlank()) R.string.menu_add_note else R.string.menu_edit_note
                                                                )
                                                            )
                                                        },
                                                        onClick = {
                                                            onNoteRequested(highlight.id)
                                                            highlightMenuExpanded = false
                                                            onCloseDrawer()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.action_delete)) },
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
                                title = { Text(stringResource(R.string.dialog_delete_highlight)) },
                                text = { Text(stringResource(R.string.dialog_delete_highlight_desc)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDeleteHighlight(highlightToDelete)
                                            showDeleteConfirmDialogFor = null
                                        }
                                    ) { Text(stringResource(R.string.action_delete)) }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDeleteConfirmDialogFor = null }
                                    ) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }
                    }
                }
                3 -> { // Pages Page
                    val listState = rememberLazyListState()
                    val pageRows = remember(totalPages) { (0 until totalPages).chunked(3) }

                    val currentRowIndex = currentPage / 3

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = {
                                    drawerScope.launch {
                                        if (currentRowIndex in pageRows.indices) {
                                            listState.animateScrollToItem(currentRowIndex)
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.action_locate))
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
                                items(pageRows, key = { it.firstOrNull() ?: 0 }) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { pageIdx ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(0.707f)
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .border(
                                                        width = if (currentPage == pageIdx) 2.dp else 1.dp,
                                                        color = if (currentPage == pageIdx) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .clickable {
                                                        onCloseDrawer()
                                                        onPageSelected(pageIdx)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                var thumb by remember { mutableStateOf(PdfThumbnailCache.get(pageIdx)) }

                                                LaunchedEffect(pageIdx, pdfDocument) {
                                                    if (thumb == null && pdfDocument != null) {
                                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            try {
                                                                val cached = PdfThumbnailCache.get(pageIdx)
                                                                if (cached != null) {
                                                                    thumb = cached
                                                                } else {
                                                                    pdfDocument.openPage(pageIdx)?.use { p ->
                                                                        val w = p.getPageWidthPoint()
                                                                        val h = p.getPageHeightPoint()
                                                                        val ratio = if (h > 0) w.toFloat() / h.toFloat() else 1f
                                                                        val thumbW = 200
                                                                        val thumbH = (thumbW / ratio).toInt().coerceAtLeast(1)
                                                                        val bmp = createBitmap(thumbW, thumbH)
                                                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                                                        p.renderPageBitmap(bmp, 0, 0, thumbW, thumbH, false)
                                                                        PdfThumbnailCache.put(pageIdx, bmp)
                                                                        thumb = bmp
                                                                    }
                                                                }
                                                            } catch (_: Exception) { }
                                                        }
                                                    }
                                                }

                                                if (thumb != null) {
                                                    Image(
                                                        bitmap = thumb!!.asImageBitmap(),
                                                        contentDescription = stringResource(R.string.pdf_page_short, pageIdx + 1),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }

                                                Text(
                                                    text = "${pageIdx + 1}",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .background(
                                                            Color.Black.copy(alpha = 0.5f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
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
        }
    }
}
