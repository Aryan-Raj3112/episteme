package com.aryan.reader.shared.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.SortOrder

data class SharedAndroidShelfScreenStrings(
    val back: String,
    val closeSearch: String,
    val clearQuery: String,
    val searchPlaceholder: String,
    val sortDescription: String,
    val selectedDescription: String,
    val searchShelfDescription: String,
    val moreOptionsDescription: String,
    val renameShelf: String,
    val deleteShelf: String,
    val addBooks: String,
    val emptyShelf: String,
    val noResults: (String) -> String,
    val foldersSection: String,
    val filesSection: String,
    val addToShelfTitle: (String) -> String,
    val addCount: (Int) -> String,
    val noUnshelvedBooks: String,
    val allBooksInShelf: String,
    val sortLabels: Map<SortOrder, String>,
    val sourceLabels: Map<AddBooksSource, String>,
)

/** Android-parity shelf detail presentation with platform book/shelf rows injected. */
@Composable
fun <ShelfItem, BookItem> SharedAndroidShelfDetailScreen(
    shelfId: String,
    shelfName: String,
    shelfSubtitle: String,
    isFolderShelf: Boolean,
    canMutateShelf: Boolean,
    childShelves: List<ShelfItem>,
    directBooks: List<BookItem>,
    selectedCount: Int,
    sortOrder: SortOrder,
    strings: SharedAndroidShelfScreenStrings,
    childKey: (ShelfItem) -> Any,
    bookKey: (BookItem) -> Any,
    childMatchesQuery: (ShelfItem, String) -> Boolean,
    bookMatchesQuery: (BookItem, String) -> Boolean,
    onSortOrderChange: (SortOrder) -> Unit,
    onBack: () -> Unit,
    onAddBooks: () -> Unit,
    onRenameShelf: () -> Unit,
    onDeleteShelf: () -> Unit,
    contextualTopBar: @Composable () -> Unit,
    childRow: @Composable (ShelfItem) -> Unit,
    bookRow: @Composable (BookItem) -> Unit,
    sortIcon: @Composable () -> Unit,
    platformBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    searchFieldTestTag: String = "ShelfSearchTextField",
    sortButtonTestTag: String = "ShelfSortButton",
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember(shelfId) { mutableStateOf(false) }
    var searchQuery by remember(shelfId) { mutableStateOf("") }
    val focusRequester = remember(shelfId) { FocusRequester() }
    var fieldValue by remember(isSearchActive, shelfId) { mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length))) }
    val query = searchQuery.trim()
    val visibleShelves = remember(childShelves, query) { if (query.isBlank()) childShelves else childShelves.filter { childMatchesQuery(it, query) } }
    val visibleBooks = remember(directBooks, query) { if (query.isBlank()) directBooks else directBooks.filter { bookMatchesQuery(it, query) } }

    LaunchedEffect(searchQuery) {
        if (fieldValue.text != searchQuery) fieldValue = fieldValue.copy(text = searchQuery, selection = TextRange(searchQuery.length))
    }
    LaunchedEffect(isSearchActive) { if (isSearchActive) focusRequester.requestFocus() }
    fun clearSearch() { searchQuery = ""; fieldValue = TextFieldValue("", TextRange.Zero) }
    fun closeSearch() { isSearchActive = false; clearSearch() }
    platformBackHandler(isSearchActive, ::closeSearch)

    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                selectedCount > 0 -> contextualTopBar()
                isSearchActive -> Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::closeSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.closeSearch) }
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { fieldValue = it; searchQuery = it.text },
                            placeholder = { Text(strings.searchPlaceholder) },
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp).focusRequester(focusRequester).testTag(searchFieldTestTag),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) IconButton(onClick = ::clearSearch) { Icon(Icons.Default.Close, strings.clearQuery) }
                            },
                        )
                    }
                }
                else -> SharedMobileTopAppBar(
                    title = {
                        Column {
                            Text(shelfName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(shelfSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.back) } },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag(sortButtonTestTag)) {
                                sortIcon(); Spacer(Modifier.width(8.dp)); Text(strings.sortLabels.getValue(sortOrder))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(strings.sortLabels.getValue(order)) },
                                        onClick = { onSortOrderChange(order); showSortMenu = false },
                                        trailingIcon = { if (order == sortOrder) Icon(Icons.Default.Check, strings.selectedDescription) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, strings.searchShelfDescription) }
                        if (canMutateShelf) Box {
                            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, strings.moreOptionsDescription) }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(text = { Text(strings.renameShelf) }, onClick = { onRenameShelf(); showMoreMenu = false })
                                DropdownMenuItem(text = { Text(strings.deleteShelf) }, onClick = { onDeleteShelf(); showMoreMenu = false })
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (canMutateShelf && selectedCount == 0) ExtendedFloatingActionButton(
                onClick = onAddBooks,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(strings.addBooks) },
            )
        },
    ) { padding ->
        if (visibleShelves.isEmpty() && visibleBooks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) strings.emptyShelf else strings.noResults(query), style = MaterialTheme.typography.bodyLarge)
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (visibleShelves.isNotEmpty()) {
                if (isFolderShelf) item("folders_section") { SharedShelfSectionLabel(strings.foldersSection) }
                items(visibleShelves, key = childKey) { childRow(it) }
            }
            if (visibleBooks.isNotEmpty() && isFolderShelf && visibleShelves.isNotEmpty()) {
                item("files_spacer") { Spacer(Modifier.height(4.dp)) }
                item("files_section") { SharedShelfSectionLabel(strings.filesSection) }
            }
            items(visibleBooks, key = bookKey) { bookRow(it) }
        }
    }
}

/** Exact Android add-books-to-shelf presentation with platform book rows injected. */
@Composable
fun <BookItem> SharedAndroidAddBooksModeScreen(
    shelfName: String,
    books: List<BookItem>,
    selectedCount: Int,
    source: AddBooksSource,
    sortOrder: SortOrder,
    strings: SharedAndroidShelfScreenStrings,
    bookKey: (BookItem) -> Any,
    onSortOrderChange: (SortOrder) -> Unit,
    onSourceChange: (AddBooksSource) -> Unit,
    onBack: () -> Unit,
    onAddSelectedBooks: () -> Unit,
    bookRow: @Composable (BookItem) -> Unit,
    sortIcon: @Composable () -> Unit,
    sortButtonTestTag: String = "AddBooksSortButton",
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                SharedMobileTopAppBar(
                    title = { Text(strings.addToShelfTitle(shelfName)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.back) } },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag(sortButtonTestTag)) {
                                sortIcon(); Spacer(Modifier.width(8.dp)); Text(strings.sortLabels.getValue(sortOrder))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(strings.sortLabels.getValue(order)) },
                                        onClick = { onSortOrderChange(order); showSortMenu = false },
                                        trailingIcon = { if (order == sortOrder) Icon(Icons.Default.Check, strings.selectedDescription) },
                                    )
                                }
                            }
                        }
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddBooksSource.entries.forEach { candidate ->
                        FilterChip(selected = candidate == source, onClick = { onSourceChange(candidate) }, label = { Text(strings.sourceLabels.getValue(candidate)) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedCount > 0) ExtendedFloatingActionButton(
                text = { Text(strings.addCount(selectedCount)) },
                icon = { Icon(Icons.Default.Check, strings.addBooks) },
                onClick = onAddSelectedBooks,
            )
        },
    ) { padding ->
        if (books.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(if (source == AddBooksSource.UNSHELVED) strings.noUnshelvedBooks else strings.allBooksInShelf, style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { items(books, key = bookKey) { bookRow(it) } }
    }
}

@Composable
private fun SharedShelfSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
