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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
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
    widthClass: SharedAndroidHomeWidthClass = SharedAndroidHomeWidthClass.COMPACT,
    /** Host override (Duo). Null keeps the shared width-class mapping. */
    gridCells: GridCells? = null,
    /** Host override (Duo). Standard keeps today's spacing on every other device. */
    density: SharedAndroidHomeDensity = SharedAndroidHomeDensity.Standard,
    /** Rendered above the continue-reading card; empty when multi-tab reading is idle. */
    openTabsContent: @Composable () -> Unit = {},
    /**
     * Inside-file content search. When [contentSearchActive] is true the grid
     * is replaced by one row per matching book, expanded to show more of that
     * book's matches on demand.
     */
    contentSearchActive: Boolean = false,
    contentSearchResults: List<SharedGlobalSearchBookResult> = emptyList(),
    contentResultRow: @Composable (T, SharedGlobalSearchBookResult, Boolean, () -> Unit) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val resolvedGridCells = gridCells ?: widthClass.bookGridCells()
    // Expansion lives here so the row composable stays stateless and the host
    // only supplies layout.
    val expandedBooks = remember { mutableStateMapOf<String, Boolean>() }
    val contentResultsById = remember(contentSearchResults) {
        contentSearchResults.associateBy { it.bookId }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        openTabsContent()
        continueReading?.let { continueCard(it, Modifier.padding(top = density.continueCardTopGap)) }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = density.filterRowTopGap),
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
            if (!contentSearchActive) {
                item {
                    IconButton(onClick = { onListViewChange(!useListView) }) {
                        Icon(
                            if (useListView) Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.FormatListNumbered,
                            contentDescription = if (useListView) strings.gridView else strings.listView,
                        )
                    }
                }
            }
        }
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.noBooks, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (contentSearchActive) {
            Surface(
                Modifier.weight(1f).padding(top = density.gridCardTopGap),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, density.gridContentTopPadding, 12.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Iterate the merged book list, not the content results:
                    // books matching metadata only must still appear, just
                    // without the matched-text body.
                    items(books, key = itemKey) { book ->
                        val result = contentResultsById[itemKey(book)]
                        if (result != null) {
                            val expanded = expandedBooks[result.bookId] == true
                            contentResultRow(
                                book,
                                result,
                                expanded,
                                { expandedBooks[result.bookId] = !expanded },
                            )
                        } else {
                            bookListItem(book)
                        }
                    }
                }
            }
        } else {
            Surface(
                Modifier.weight(1f).padding(top = density.gridCardTopGap),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                if (useListView) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, density.gridContentTopPadding, 12.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(books, key = itemKey) { bookListItem(it) } }
                } else {
                    LazyVerticalGrid(
                        columns = resolvedGridCells,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, density.gridContentTopPadding, 12.dp, 96.dp),
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
    showSearchField: Boolean = true,
    itemKey: (T) -> String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    bookCard: @Composable (T) -> Unit,
    widthClass: SharedAndroidHomeWidthClass = SharedAndroidHomeWidthClass.COMPACT,
    /** Host override (Duo). Null keeps the shared width-class mapping. */
    gridCells: GridCells? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    if (showSearchField) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (showSearchField) {
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
        }
        Text(resultLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        if (books.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(noResultsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyVerticalGrid(
                columns = gridCells ?: widthClass.bookGridCells(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { items(books, key = itemKey) { bookCard(it) } }
        }
    }
}
