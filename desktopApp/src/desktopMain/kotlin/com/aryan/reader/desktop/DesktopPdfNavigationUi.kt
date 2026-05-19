package com.aryan.reader.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.ui.SharedReaderVerticalScrollbar
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedAcceleratedLazyWheelScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopPdfJumpHistoryControls(
    visible: Boolean,
    modifier: Modifier = Modifier,
    backPage: Int?,
    forwardPage: Int?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    val hasJumpTargets = backPage != null || forwardPage != null
    AnimatedVisibility(
        visible = visible && hasJumpTargets,
        enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
        exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBack,
                enabled = backPage != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("content_desc_jump_back", "Jump back"),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    backPage?.let { readerString("desktop_pdf_compact_page_number", "p. %1\$d", it + 1) } ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = onClear,
                modifier = Modifier.weight(0.8f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString("desktop_clear_jump_history", "Clear jump history"),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(readerString("action_clear", "Clear"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            TextButton(
                onClick = onForward,
                enabled = forwardPage != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    forwardPage?.let { readerString("desktop_pdf_compact_page_number", "p. %1\$d", it + 1) } ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = readerString("content_desc_jump_forward", "Jump forward"),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun desktopPdfTocParentIndices(toc: List<PdfTocEntry>): Set<Int> {
    return toc.indices.filter { index ->
        val next = toc.getOrNull(index + 1)
        next != null && next.nestLevel > toc[index].nestLevel
    }.toSet()
}

internal fun desktopPdfTocAncestorIndices(
    toc: List<PdfTocEntry>,
    originalIndex: Int
): Set<Int> {
    val targetDepth = toc.getOrNull(originalIndex)?.nestLevel ?: return emptySet()
    val ancestors = mutableSetOf<Int>()
    var currentDepth = targetDepth
    for (index in originalIndex downTo 0) {
        val entry = toc[index]
        if (entry.nestLevel < currentDepth) {
            ancestors += index
            currentDepth = entry.nestLevel
        }
        if (currentDepth == 0) break
    }
    return ancestors
}

internal fun desktopVisiblePdfTocEntries(
    toc: List<PdfTocEntry>,
    expandedIndices: Set<Int>
): List<Pair<Int, PdfTocEntry>> {
    val result = mutableListOf<Pair<Int, PdfTocEntry>>()
    val visibilityStack = BooleanArray(50) { false }
    visibilityStack[0] = true

    toc.forEachIndexed { index, entry ->
        val depth = entry.nestLevel.coerceIn(0, visibilityStack.lastIndex)
        if (visibilityStack[depth]) {
            result += index to entry
            if (depth + 1 < visibilityStack.size) {
                visibilityStack[depth + 1] = index in expandedIndices
            }
        } else if (depth + 1 < visibilityStack.size) {
            visibilityStack[depth + 1] = false
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopPdfNavigationSidebar(
    document: DesktopPdfDocument,
    pageIndex: Int,
    sortedAnnotations: List<SharedPdfAnnotation>,
    sortedEmbeddedAnnotations: List<SharedPdfEmbeddedAnnotation>,
    bookmarks: List<SharedPdfBookmark>,
    selectedAnnotationId: String?,
    selectedEmbeddedAnnotationId: String?,
    onPageSelected: (Int) -> Unit,
    onAnnotationOpened: (SharedPdfAnnotation) -> Unit,
    onAnnotationSelected: (SharedPdfAnnotation) -> Unit,
    onAnnotationDeleted: (SharedPdfAnnotation) -> Unit,
    onEmbeddedAnnotationOpened: (SharedPdfEmbeddedAnnotation) -> Unit,
    onEmbeddedAnnotationSelected: (SharedPdfEmbeddedAnnotation) -> Unit
) {
    val documentHandleId = document.handleId
    val tabs = listOf(
        readerString("desktop_toc", "TOC"),
        readerString("tab_annotations", "Annotations"),
        readerString("tab_bookmarks", "Bookmarks"),
        readerString("tab_pages", "Pages")
    )
    var selectedTabIndex by remember(documentHandleId) { mutableStateOf(0) }
    val navigationScope = rememberCoroutineScope()
    val pdfTocParentIndices = remember(document.toc) { desktopPdfTocParentIndices(document.toc) }
    var expandedPdfTocEntryIndices by remember(documentHandleId, document.toc) {
        mutableStateOf(pdfTocParentIndices)
    }

    Surface(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    if (document.toc.isEmpty()) {
                        DesktopPdfNavigationEmpty(readerString("desktop_no_table_of_contents", "No table of contents"))
                    } else {
                        val tocListState = rememberLazyListState()
                        val visibleTocItems by remember(document.toc, expandedPdfTocEntryIndices) {
                            derivedStateOf { desktopVisiblePdfTocEntries(document.toc, expandedPdfTocEntryIndices) }
                        }
                        val currentOriginalIndex = remember(document.toc, pageIndex) {
                            document.toc.indexOfLast { it.pageIndex <= pageIndex }
                                .takeIf { it >= 0 }
                                ?: document.toc.indexOfFirst { it.pageIndex == pageIndex }.takeIf { it >= 0 }
                        }
                        fun locateCurrentTocEntry() {
                            val originalIndex = currentOriginalIndex ?: return
                            navigationScope.launch {
                                expandedPdfTocEntryIndices = expandedPdfTocEntryIndices +
                                    desktopPdfTocAncestorIndices(document.toc, originalIndex)
                                repeat(4) {
                                    val visibleIndex = visibleTocItems.indexOfFirst { it.first == originalIndex }
                                    if (visibleIndex >= 0) {
                                        tocListState.animateScrollToItem(visibleIndex)
                                        return@launch
                                    }
                                    delay(30)
                                }
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { expandedPdfTocEntryIndices = pdfTocParentIndices }) {
                                    Text(readerString("action_expand_all", "Expand all"))
                                }
                                TextButton(onClick = { expandedPdfTocEntryIndices = emptySet() }) {
                                    Text(readerString("action_collapse_all", "Collapse all"))
                                }
                                TextButton(onClick = ::locateCurrentTocEntry, enabled = currentOriginalIndex != null) {
                                    Text(readerString("action_locate", "Locate"))
                                }
                            }
                            HorizontalDivider()
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(
                                    state = tocListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedAcceleratedLazyWheelScroll(tocListState)
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        visibleTocItems,
                                        key = { (index, entry) -> "nav_toc_${index}_${entry.pageIndex}_${entry.nestLevel}" }
                                    ) { (originalIndex, entry) ->
                                        val nextItem = document.toc.getOrNull(originalIndex + 1)
                                        val hasChildren = nextItem != null && nextItem.nestLevel > entry.nestLevel
                                        val isExpanded = originalIndex in expandedPdfTocEntryIndices
                                        DesktopPdfTocTreeItem(
                                            entry = entry,
                                            selected = originalIndex == currentOriginalIndex,
                                            hasChildren = hasChildren,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                expandedPdfTocEntryIndices = if (isExpanded) {
                                                    expandedPdfTocEntryIndices - originalIndex
                                                } else {
                                                    expandedPdfTocEntryIndices + originalIndex
                                                }
                                            },
                                            onClick = { onPageSelected(entry.pageIndex) }
                                        )
                                    }
                                }
                                SharedReaderVerticalScrollbar(
                                    listState = tocListState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (sortedAnnotations.isEmpty() && sortedEmbeddedAnnotations.isEmpty()) {
                        DesktopPdfNavigationEmpty(readerString("desktop_no_annotations_yet", "No annotations yet"))
                    } else {
                        val annotationsListState = rememberLazyListState()
                        var annotationMenuExpandedFor by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
                        var embeddedAnnotationMenuExpandedFor by remember { mutableStateOf<SharedPdfEmbeddedAnnotation?>(null) }
                        var deleteAnnotationConfirmFor by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = annotationsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedAcceleratedLazyWheelScroll(annotationsListState)
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sortedAnnotations, key = { "nav_annotation_${it.id}" }) { annotation ->
                                    Surface(
                                        color = if (annotation.id == selectedAnnotationId) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onAnnotationOpened(annotation) }
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    annotation.desktopLabel(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    readerString("pdf_page_short", "Page %1\$d", annotation.pageIndex + 1),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                                    Text(
                                                        note,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Box {
                                                IconButton(onClick = { annotationMenuExpandedFor = annotation }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_annotation_options", "Annotation options"))
                                                }
                                                DropdownMenu(
                                                    expanded = annotationMenuExpandedFor == annotation,
                                                    onDismissRequest = { annotationMenuExpandedFor = null }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (annotation.note.isNullOrBlank() &&
                                                                    annotation.kind != PdfAnnotationKind.TEXT
                                                                ) {
                                                                    readerString("menu_add_note", "Add note")
                                                                } else {
                                                                    readerString("action_edit", "Edit")
                                                                }
                                                            )
                                                        },
                                                        onClick = {
                                                            annotationMenuExpandedFor = null
                                                            onAnnotationSelected(annotation)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(readerString("action_delete", "Delete")) },
                                                        onClick = {
                                                            annotationMenuExpandedFor = null
                                                            deleteAnnotationConfirmFor = annotation
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                items(sortedEmbeddedAnnotations, key = { "nav_embedded_${it.id}" }) { annotation ->
                                    Surface(
                                        color = if (annotation.id == selectedEmbeddedAnnotationId) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onEmbeddedAnnotationOpened(annotation) }
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    annotation.author.ifBlank { readerString("desktop_pdf_comment", "PDF comment") },
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    readerString("pdf_page_short", "Page %1\$d", annotation.pageIndex + 1),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                annotation.contents.takeIf { it.isNotBlank() }?.let { contents ->
                                                    Text(
                                                        contents,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Box {
                                                IconButton(onClick = { embeddedAnnotationMenuExpandedFor = annotation }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_comment_options", "Comment options"))
                                                }
                                                DropdownMenu(
                                                    expanded = embeddedAnnotationMenuExpandedFor == annotation,
                                                    onDismissRequest = { embeddedAnnotationMenuExpandedFor = null }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(readerString("desktop_open_comment", "Open comment")) },
                                                        onClick = {
                                                            embeddedAnnotationMenuExpandedFor = null
                                                            onEmbeddedAnnotationSelected(annotation)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            SharedReaderVerticalScrollbar(
                                listState = annotationsListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                        deleteAnnotationConfirmFor?.let { annotation ->
                            AlertDialog(
                                onDismissRequest = { deleteAnnotationConfirmFor = null },
                                title = { Text(readerString("desktop_delete_annotation_title", "Delete annotation?")) },
                                text = { Text(readerString("desktop_delete_annotation_desc", "This removes the annotation from this PDF.")) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            deleteAnnotationConfirmFor = null
                                            onAnnotationDeleted(annotation)
                                        }
                                    ) {
                                        Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { deleteAnnotationConfirmFor = null }) {
                                        Text(readerString("action_cancel", "Cancel"))
                                    }
                                }
                            )
                        }
                    }
                }
                2 -> {
                    if (bookmarks.isEmpty()) {
                        DesktopPdfNavigationEmpty(readerString("desktop_no_bookmarks_yet", "No bookmarks yet"))
                    } else {
                        val bookmarksListState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = bookmarksListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedAcceleratedLazyWheelScroll(bookmarksListState)
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(bookmarks, key = { "nav_bookmark_${it.pageIndex}" }) { bookmark ->
                                    Surface(
                                        color = if (bookmark.pageIndex == pageIndex) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPageSelected(bookmark.pageIndex) }
                                    ) {
                                        Text(
                                            bookmark.label.ifBlank {
                                                readerString("pdf_page_short", "Page %1\$d", bookmark.pageIndex + 1)
                                            },
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                            SharedReaderVerticalScrollbar(
                                listState = bookmarksListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
                3 -> {
                    val pageRows = remember(document.pageCount) { (0 until document.pageCount).chunked(3) }
                    val pagesListState = rememberLazyListState()
                    val currentRowIndex = pageIndex / 3
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = {
                                    navigationScope.launch {
                                        pagesListState.animateScrollToItem(
                                            currentRowIndex.coerceIn(0, pageRows.lastIndex.coerceAtLeast(0))
                                        )
                                    }
                                }
                            ) {
                                Text(readerString("action_locate", "Locate"))
                            }
                        }
                        HorizontalDivider()
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                state = pagesListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedAcceleratedLazyWheelScroll(pagesListState)
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pageRows, key = { row -> row.firstOrNull() ?: 0 }) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { page ->
                                            DesktopPdfThumbnailTile(
                                                document = document,
                                                pageIndex = page,
                                                selected = page == pageIndex,
                                                onClick = { onPageSelected(page) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            SharedReaderVerticalScrollbar(
                                listState = pagesListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopPdfTocTreeItem(
    entry: PdfTocEntry,
    selected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(start = (entry.nestLevel.coerceAtLeast(0) * 14).dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = hasChildren) { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) {
                            readerString("content_desc_collapse", "Collapse")
                        } else {
                            readerString("content_desc_expand", "Expand")
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                entry.title,
                fontWeight = if (selected) FontWeight.Bold else if (entry.nestLevel == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                readerString("desktop_pdf_compact_page_number", "p. %1\$d", entry.pageIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
internal fun DesktopPdfNavigationEmpty(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DesktopPdfThumbnailTile(
    document: DesktopPdfDocument,
    pageIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documentHandleId = document.handleId
    var thumbnail by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderFailed by remember(documentHandleId, pageIndex) { mutableStateOf(false) }
    val pageSize = document.pageSizes.getOrNull(pageIndex)
    val thumbnailScale = remember(pageSize) {
        val width = pageSize?.width?.coerceAtLeast(1f) ?: 612f
        (120f / width).coerceIn(0.08f, 0.35f)
    }

    LaunchedEffect(documentHandleId, pageIndex, thumbnailScale) {
        thumbnail = null
        renderFailed = false
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                DesktopPdfium.renderPage(
                    document = document,
                    pageIndex = pageIndex,
                    scale = thumbnailScale,
                    renderAnnotations = false
                )
            }.getOrNull()
        }
        thumbnail = rendered
        renderFailed = rendered == null
    }

    Surface(
        modifier = modifier.aspectRatio(0.707f).clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val render = thumbnail
            if (render != null) {
                Image(
                    bitmap = render.image,
                    contentDescription = readerString("pdf_page_short", "Page %1\$d", pageIndex + 1),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            } else {
                Text(
                    if (renderFailed) "!" else "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
internal fun DesktopPdfPageScrubOverlay(
    pageIndex: Int?,
    pageCount: Int,
    pageLabel: String? = pageIndex?.let { "Page ${it + 1} of $pageCount" }
) {
    if (pageIndex == null || pageCount <= 0) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Text(
                text = pageLabel ?: readerString(
                    "desktop_pdf_page_of_count",
                    "Page %1\$s of %2\$d",
                    "${pageIndex + 1}",
                    pageCount
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}
