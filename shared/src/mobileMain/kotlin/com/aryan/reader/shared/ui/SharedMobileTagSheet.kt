package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag as TagIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.Tag

/**
 * The one tag apply/delete sheet used by both platforms.
 *
 * Android's `TagSelectionBottomSheet` and iOS's private copy drifted (query trimming,
 * per-row membership scans, icon and test coverage). This is the Android behaviour:
 * tri-state membership over the selected books, a create row for a query that matches
 * no tag exactly, and per-tag deletion with confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileTagSheet(
    allTags: List<Tag>,
    selectedBookIds: Set<String>,
    books: List<BookItem>,
    onCreateAndAssign: (String) -> Unit,
    onToggleTag: (String, Boolean) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var tagPendingDeletion by remember { mutableStateOf<Tag?>(null) }
    val normalizedQuery = searchQuery.trim()
    val filteredTags = remember(allTags, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            allTags
        } else {
            allTags.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val exactMatch = remember(allTags, normalizedQuery) {
        allTags.any { it.name.equals(normalizedQuery, ignoreCase = true) }
    }
    // Count once per sheet instead of scanning every selected book for every row.
    val tagCounts = remember(books, selectedBookIds) {
        val booksById = books.associateBy { it.id }
        buildMap {
            selectedBookIds.forEach { bookId ->
                booksById[bookId]?.tags?.forEach { tag ->
                    put(tag.id, (this[tag.id] ?: 0) + 1)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("SharedTagSheet"),
        ) {
            Text(
                readerString("title_apply_tags", "Apply tags"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TagSheetSearch"),
                placeholder = { Text(readerString("placeholder_search_create_tag", "Search or create a tag")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                if (normalizedQuery.isNotEmpty() && !exactMatch) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCreateAndAssign(normalizedQuery)
                                    searchQuery = ""
                                }
                                .padding(vertical = 12.dp)
                                .testTag("TagSheetCreate"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                readerString("action_create_tag", "Create \"%1\$s\"", normalizedQuery),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(filteredTags, key = { it.id }) { tag ->
                    val tagColor = Color(tag.color ?: 0xFF64B5F6.toInt())
                    val checkedCount = tagCounts[tag.id] ?: 0
                    val toggleState = when (checkedCount) {
                        0 -> ToggleableState.Off
                        selectedBookIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onToggleTag(tag.id, toggleState != ToggleableState.On) }
                            .testTag("TagSheetRow_${tag.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = toggleState, onClick = null)
                        Surface(
                            shape = CircleShape,
                            color = tagColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.TagIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = tagColor,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { tagPendingDeletion = tag },
                            modifier = Modifier.testTag("TagSheetDelete_${tag.id}"),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = readerString("menu_delete_tag", "Delete tag"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    tagPendingDeletion?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDeletion = null },
            title = { Text(readerString("menu_delete_tag", "Delete tag")) },
            text = {
                Text(
                    readerString(
                        "dialog_delete_tag_desc",
                        "Delete \"%1\$s\"? It will be removed from every book.",
                        tag.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag.id)
                        tagPendingDeletion = null
                    }
                ) {
                    Text(readerString("action_delete", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDeletion = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            },
        )
    }
}
