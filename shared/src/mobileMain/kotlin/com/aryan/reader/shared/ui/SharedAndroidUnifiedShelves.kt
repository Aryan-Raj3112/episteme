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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier,
    /** Nested shelves inside the selected shelf (file-system navigation). */
    childShelves: List<S> = emptyList(),
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
    } else if (childShelves.isEmpty() && selectedBooks.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            breadcrumbContent()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyShelfLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            breadcrumbContent()
            LazyVerticalGrid(
                columns = widthClass.bookGridCells(),
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
