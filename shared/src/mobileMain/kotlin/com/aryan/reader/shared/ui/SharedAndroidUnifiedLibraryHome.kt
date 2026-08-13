package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class SharedAndroidUnifiedHomeStrings(
    val noBooks: String,
    val filterBooks: String,
    val gridView: String,
    val listView: String,
    val filterLabels: Map<MobileUnifiedLibraryFilter, String>,
)

@Composable
fun <T> SharedAndroidUnifiedLibraryHome(
    books: List<T>,
    continueReading: T?,
    filter: MobileUnifiedLibraryFilter,
    sortLabel: String,
    advancedFilterCount: Int,
    useListView: Boolean,
    strings: SharedAndroidUnifiedHomeStrings,
    itemKey: (T) -> String,
    onFilterChange: (MobileUnifiedLibraryFilter) -> Unit,
    onControls: () -> Unit,
    onAdvancedFilters: () -> Unit,
    onListViewChange: (Boolean) -> Unit,
    continueCard: @Composable (T, Modifier) -> Unit,
    bookCard: @Composable (T) -> Unit,
    bookListItem: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        continueReading?.let { continueCard(it, Modifier.padding(top = 16.dp)) }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(MobileUnifiedLibraryFilter.entries) { option ->
                FilterChip(selected = filter == option, onClick = { onFilterChange(option) }, label = { Text(strings.filterLabels.getValue(option)) })
            }
            item { AssistChip(onClick = onControls, label = { Text(sortLabel) }) }
            item {
                BadgedBox(badge = { if (advancedFilterCount > 0) Badge { Text(advancedFilterCount.toString()) } }) {
                    IconButton(onClick = onAdvancedFilters) { Icon(Icons.Default.FilterList, contentDescription = strings.filterBooks) }
                }
            }
            item {
                IconButton(onClick = { onListViewChange(!useListView) }) {
                    Icon(
                        if (useListView) Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.FormatListNumbered,
                        contentDescription = if (useListView) strings.gridView else strings.listView,
                    )
                }
            }
        }
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.noBooks, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Surface(
                Modifier.weight(1f).padding(top = 16.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                if (useListView) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(books, key = itemKey) { bookListItem(it) } }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 16.dp, 12.dp, 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { items(books, key = itemKey) { bookCard(it) } }
                }
            }
        }
    }
}

@Composable
fun <T> SharedAndroidUnifiedLibrarySearch(
    books: List<T>,
    query: String,
    searchPlaceholder: String,
    clearDescription: String,
    closeDescription: String,
    resultLabel: String,
    noResultsLabel: String,
    itemKey: (T) -> String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    bookCard: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("UnifiedLibrarySearch"),
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, clearDescription) }
                },
                singleLine = true,
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, closeDescription) }
        }
        Text(resultLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        if (books.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(noResultsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { items(books, key = itemKey) { bookCard(it) } }
        }
    }
}
