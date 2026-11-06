package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.SortOrder

@Composable
fun <S, B> SharedAndroidUnifiedShelves(
    visibleShelves: List<S>,
    selectedShelf: S?,
    selectedBooks: List<B>,
    noShelvesLabel: String,
    shelfKey: (S) -> String,
    shelfName: (S) -> String,
    shelfBookCountLabel: @Composable (S) -> String,
    bookKey: (B) -> String,
    onShelfSelected: (S) -> Unit,
    bookCard: @Composable (B) -> Unit,
    widthClass: SharedAndroidHomeWidthClass = SharedAndroidHomeWidthClass.COMPACT,
    /** Host override (Duo). Null keeps the shared width-class mapping. */
    gridCells: GridCells? = null,
    modifier: Modifier = Modifier,
    /** Nested shelves inside the selected shelf (file-system navigation). */
    childShelves: List<S> = emptyList(),
    /** Lets the host render its own contextual actions for the shelf being viewed. */
    selectedShelfActions: @Composable (S) -> Unit = {},
    childKey: (S) -> String = shelfKey,
    childName: (S) -> String = shelfName,
    childBookCountLabel: @Composable (S) -> String = shelfBookCountLabel,
    onChildShelfSelected: (S) -> Unit = onShelfSelected,
    /** Optional section headers shown when a selected shelf mixes folders and books. */
    foldersSectionLabel: String? = null,
    filesSectionLabel: String? = null,
    emptyShelfLabel: String = noShelvesLabel,
    /** File-manager breadcrumb rendered above the selected shelf content. */
    breadcrumbContent: @Composable () -> Unit = {},
    /**
     * Sort control for the selected shelf's books, matching the retired shelf screens.
     * Hidden while [onSortOrderChange] is null so hosts without shelf sorting are unchanged.
     * The shared library's current sort order drives the projection, so hosts only need to
     * pass their current [SortOrder] and reducer.
     */
    sortOrder: SortOrder = SortOrder.RECENT,
    sortLabels: Map<SortOrder, String> = emptyMap(),
    sortDescription: String = "",
    selectedSortDescription: String = "",
    onSortOrderChange: ((SortOrder) -> Unit)? = null,
) {
    if (selectedShelf == null) {
        if (visibleShelves.isEmpty()) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(noShelvesLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleShelves, key = shelfKey) { shelf ->
                    UnifiedShelfCard(
                        name = shelfName(shelf),
                        countLabel = shelfBookCountLabel(shelf),
                        onClick = { onShelfSelected(shelf) },
                    )
                }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { breadcrumbContent() }
                if (onSortOrderChange != null) {
                    SharedUnifiedShelfSortControl(
                        sortOrder = sortOrder,
                        labels = sortLabels,
                        sortDescription = sortDescription,
                        selectedDescription = selectedSortDescription,
                        onSortOrderChange = onSortOrderChange,
                    )
                }
                selectedShelfActions(selectedShelf)
            }
            if (childShelves.isEmpty() && selectedBooks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyShelfLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = gridCells ?: widthClass.bookGridCells(),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (childShelves.isNotEmpty()) {
                        if (foldersSectionLabel != null && selectedBooks.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                UnifiedShelfSectionLabel(foldersSectionLabel)
                            }
                        }
                        items(childShelves, key = childKey, span = { GridItemSpan(maxLineSpan) }) { child ->
                            UnifiedShelfCard(
                                name = childName(child),
                                countLabel = childBookCountLabel(child),
                                onClick = { onChildShelfSelected(child) },
                            )
                        }
                    }
                    if (selectedBooks.isNotEmpty() && childShelves.isNotEmpty() && filesSectionLabel != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            UnifiedShelfSectionLabel(filesSectionLabel)
                        }
                    }
                    items(selectedBooks, key = bookKey) { bookCard(it) }
                }
            }
        }
    }
}

@Composable
private fun UnifiedShelfCard(
    name: String,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(countLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@Composable
private fun UnifiedShelfSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Sort picker for the selected shelf's books.
 *
 * Mirrors the retired shelf screens: the current order stays visible in the button
 * label and the menu marks the active choice, so the control always answers both
 * "how is this sorted?" and "what can I switch to?".
 */
@Composable
private fun SharedUnifiedShelfSortControl(
    sortOrder: SortOrder,
    labels: Map<SortOrder, String>,
    sortDescription: String,
    selectedDescription: String,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("ShelfSortButton"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = sortDescription,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(labels[sortOrder].orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SharedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(labels[order].orEmpty()) },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                    trailingIcon = {
                        if (order == sortOrder) Icon(Icons.Default.Check, selectedDescription)
                    },
                )
            }
        }
    }
}
