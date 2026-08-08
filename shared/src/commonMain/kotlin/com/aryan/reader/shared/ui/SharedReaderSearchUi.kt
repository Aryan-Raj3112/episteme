package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import com.aryan.reader.shared.EpubBookmark
import com.aryan.reader.shared.SearchResult

/** Android-compatible common reader search-result list; platforms only supply localized text. */
@Composable
fun SharedReaderSearchResultsPanel(
    results: List<SearchResult>,
    isSearching: Boolean,
    noResultsText: String,
    resultsCountText: (Int) -> String,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(noResultsText, style = MaterialTheme.typography.bodyLarge)
            }
            else -> Column {
                Text(
                    text = resultsCountText(results.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                    items(results.size) { index ->
                        val result = results[index]
                        ListItem(
                            headlineContent = {
                                Text(result.locationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(result.snippet, style = MaterialTheme.typography.bodyMedium)
                            },
                            modifier = Modifier
                                .clickable { onResultClick(result) }
                                .testTag("SearchResultItem_${result.locationInSource}"),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Android EPUB TOC row moved verbatim into common UI; Android supplies localized semantics. */
@Composable
fun SharedAndroidEpubTocTreeItem(
    label: String,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    collapseDescription: String,
    expandDescription: String,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            Color.Transparent
        },
        label = "TocItemBackground",
    )
    val contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width((16 * depth).dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = hasChildren, onClick = onToggleExpand),
            contentAlignment = Alignment.Center,
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (isExpanded) collapseDescription else expandDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = label,
            style = if (depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) {
                FontWeight.Bold
            } else if (depth == 0) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
    }
}

data class SharedAndroidEpubBookmarkStrings(
    val empty: String,
    val defaultLabel: String,
    val pageOf: (page: Int, total: Int) -> String,
    val moreOptionsDescription: String,
    val renameAction: String,
    val deleteAction: String,
    val renameDialogTitle: String,
    val newNameLabel: String,
    val saveAction: String,
    val cancelAction: String,
    val deleteDialogTitle: String,
    val deleteDialogDescription: String,
)

/** Android EPUB bookmark list and dialogs, with platform localization and scrollbar injected. */
@Composable
fun SharedAndroidEpubBookmarksList(
    bookmarks: Set<EpubBookmark>,
    strings: SharedAndroidEpubBookmarkStrings,
    onNavigateToBookmark: (EpubBookmark) -> Unit,
    onRenameBookmark: (EpubBookmark, String) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
    scrollbar: @Composable BoxScope.(LazyListState) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(strings.empty, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        return
    }

    var bookmarkMenuExpandedFor by remember { mutableStateOf<EpubBookmark?>(null) }
    var showDeleteConfirmDialogFor by remember { mutableStateOf<EpubBookmark?>(null) }
    var showRenameBookmarkDialog by remember { mutableStateOf<EpubBookmark?>(null) }
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 4.dp),
        ) {
            items(
                items = bookmarks.distinctBy { it.cfi }.sortedBy { it.cfi },
                key = { it.cfi },
            ) { bookmark ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = bookmark.label?.takeIf { it.isNotBlank() }
                                ?: bookmark.snippet.ifBlank { strings.defaultLabel },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = bookmark.chapterTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (bookmark.pageInChapter != null && bookmark.totalPagesInChapter != null) {
                                Text(
                                    text = strings.pageOf(bookmark.pageInChapter, bookmark.totalPagesInChapter),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { bookmarkMenuExpandedFor = bookmark }) {
                                Icon(Icons.Default.MoreVert, contentDescription = strings.moreOptionsDescription)
                            }
                            DropdownMenu(
                                expanded = bookmarkMenuExpandedFor == bookmark,
                                onDismissRequest = { bookmarkMenuExpandedFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.renameAction) },
                                    onClick = {
                                        showRenameBookmarkDialog = bookmark
                                        bookmarkMenuExpandedFor = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.deleteAction) },
                                    onClick = {
                                        showDeleteConfirmDialogFor = bookmark
                                        bookmarkMenuExpandedFor = null
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onNavigateToBookmark(bookmark) },
                )
                HorizontalDivider()
            }
        }
        scrollbar(listState)
    }

    showRenameBookmarkDialog?.let { bookmarkToRename ->
        val currentName = bookmarkToRename.label?.takeIf { it.isNotBlank() } ?: bookmarkToRename.snippet
        var newTitle by remember(bookmarkToRename) { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { showRenameBookmarkDialog = null },
            title = { Text(strings.renameDialogTitle) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(strings.newNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) onRenameBookmark(bookmarkToRename, newTitle)
                    showRenameBookmarkDialog = null
                }) { Text(strings.saveAction) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameBookmarkDialog = null }) { Text(strings.cancelAction) }
            },
        )
    }

    showDeleteConfirmDialogFor?.let { bookmarkToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialogFor = null },
            title = { Text(strings.deleteDialogTitle) },
            text = { Text(strings.deleteDialogDescription) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBookmark(bookmarkToDelete)
                    showDeleteConfirmDialogFor = null
                }) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialogFor = null }) { Text(strings.cancelAction) }
            },
        )
    }
}
