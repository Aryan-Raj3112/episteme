package com.aryan.reader.shared.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Exact Android library Shelves tab with platform shelf rows/covers injected. */
@Composable
fun <ShelfItem> SharedAndroidLibraryShelves(
    shelves: List<ShelfItem>,
    selectedShelfIds: Set<String>,
    shelfId: (ShelfItem) -> String,
    shelfName: (ShelfItem) -> String,
    isVisibleTagShelf: (ShelfItem) -> Boolean,
    isVisibleRootShelf: (ShelfItem) -> Boolean,
    browseByTagTitle: String,
    tagIcon: Painter,
    onShelfClick: (ShelfItem) -> Unit,
    shelfRow: @Composable (ShelfItem, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagShelves = remember(shelves) { shelves.filter(isVisibleTagShelf) }
    val visibleShelves = remember(shelves) { shelves.filter(isVisibleRootShelf) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tagShelves.isNotEmpty() && selectedShelfIds.isEmpty()) {
            item("browse_by_tag") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(browseByTagTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tagShelves.forEach { shelf ->
                            FilterChip(
                                selected = false,
                                onClick = { onShelfClick(shelf) },
                                label = { Text(shelfName(shelf)) },
                                leadingIcon = { Icon(tagIcon, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
        }
        items(visibleShelves, key = shelfId) { shelf -> shelfRow(shelf, shelfId(shelf) in selectedShelfIds) }
    }
}
