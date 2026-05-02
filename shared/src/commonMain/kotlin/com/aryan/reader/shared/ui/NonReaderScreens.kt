package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.isOpdsStream
import com.aryan.reader.shared.progressPercentValue
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.toHomeScreenModel

enum class NonReaderLibraryTab {
    BOOKS,
    SHELVES,
    FOLDERS
}

@Composable
fun SharedHomeScreen(
    state: SharedReaderScreenState,
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val model = state.toHomeScreenModel()
    NonReaderScreenScaffold(
        title = "Home",
        subtitle = "Recent books and quick access",
        modifier = modifier,
        trailing = {
            Button(onClick = onImportBooks) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import")
            }
        }
    ) {
        if (model.isContextualModeActive) {
            SelectionToolbar(
                count = model.selectedBooks.size,
                onClear = onClearSelection,
                onRemove = onRemoveSelected,
                onTag = onTagSelectedBooks,
                onAddToShelf = onAddSelectedBooksToShelf
            )
        }

        if (model.isEmpty) {
            SharedEmptyState(
                icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(56.dp)) },
                title = "No recent files",
                body = if (model.isLibraryEmpty) "Import a few books to populate your library." else "Open books from the library and they will appear here.",
                actionLabel = "Import books",
                onAction = onImportBooks,
                modifier = Modifier.weight(1f)
            )
        } else {
            BookGrid(
                books = model.recentBooks,
                selectedBookIds = state.selectedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SharedLibraryScreen(
    state: SharedReaderScreenState,
    selectedTab: NonReaderLibraryTab,
    onTabChange: (NonReaderLibraryTab) -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onCreateShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val books = state.libraryBooks
    val shelves = state.shelves
    val folderShelves = remember(shelves) { shelves.filter { it.type == ShelfType.FOLDER } }
    NonReaderScreenScaffold(
        title = "Library",
        subtitle = "Search, sort, filter, and organize local metadata",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SortMenu(sortOrder = state.sortOrder, onSortOrderChange = { onStateChange(state.reduce(LibraryAction.SortChanged(it))) })
                Button(onClick = onCreateShelf) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shelf")
                }
                Button(onClick = onImportBooks) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    ) {
        if (state.selectedBookIds.isNotEmpty()) {
            SelectionToolbar(
                count = state.selectedBookIds.size,
                onClear = onClearSelection,
                onRemove = onRemoveSelected,
                onTag = onTagSelectedBooks,
                onAddToShelf = onAddSelectedBooksToShelf
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NonReaderLibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabChange(tab) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (tab) {
                                NonReaderLibraryTab.BOOKS -> Icons.Default.Book
                                NonReaderLibraryTab.SHELVES -> Icons.AutoMirrored.Filled.LibraryBooks
                                NonReaderLibraryTab.FOLDERS -> Icons.Default.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text(tab.label) }
                )
            }
        }

        when (selectedTab) {
            NonReaderLibraryTab.BOOKS -> {
                LibrarySearchAndFilters(
                    state = state,
                    onStateChange = onStateChange
                )

                if (books.isEmpty()) {
                    SharedEmptyState(
                        icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(56.dp)) },
                        title = if (state.rawLibraryBooks.isEmpty()) "Your library is empty" else "No books match",
                        body = if (state.rawLibraryBooks.isEmpty()) "Import books to begin building your desktop library." else "Adjust search, sort, or filters to see more books.",
                        actionLabel = if (state.rawLibraryBooks.isEmpty()) "Import books" else "Clear filters",
                        onAction = {
                            if (state.rawLibraryBooks.isEmpty()) {
                                onImportBooks()
                            } else {
                                onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters())))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    BookGrid(
                        books = books,
                        selectedBookIds = state.selectedBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            NonReaderLibraryTab.SHELVES -> ShelfCollection(
                shelves = shelves,
                selectedBookIds = state.selectedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
                emptyTitle = "No shelves yet",
                emptyBody = "Series, tags, and imported metadata will appear here.",
                modifier = Modifier.weight(1f)
            )

            NonReaderLibraryTab.FOLDERS -> ShelfCollection(
                shelves = folderShelves,
                selectedBookIds = state.selectedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                emptyTitle = "No folders yet",
                emptyBody = "Imported folder metadata will appear here when available.",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SharedShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onCreateShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NonReaderScreenScaffold(
        title = "Shelves",
        subtitle = "Series, folders, and tags from library metadata",
        modifier = modifier,
        trailing = {
            Button(onClick = onCreateShelf) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shelf")
            }
        }
    ) {
        ShelfCollection(
            shelves = shelves,
            selectedBookIds = selectedBookIds,
            onOpenBook = onOpenBook,
            onToggleSelection = onToggleSelection,
            onShowBookInfo = onShowBookInfo,
            onEditBook = onEditBook,
            onRenameShelf = onRenameShelf,
            onDeleteShelf = onDeleteShelf,
            emptyTitle = "No shelves yet",
            emptyBody = "Add metadata or import folders later to populate shelves.",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NonReaderScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onRemove: () -> Unit,
    onTag: () -> Unit = {},
    onAddToShelf: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTag) {
                Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tag")
            }
            TextButton(onClick = onAddToShelf) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shelf")
            }
            TextButton(onClick = onClear) {
                Text("Clear")
            }
            TextButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove")
            }
        }
    }
}

@Composable
private fun LibrarySearchAndFilters(
    state: SharedReaderScreenState,
    onStateChange: (SharedReaderScreenState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onStateChange(state.reduce(LibraryAction.SearchChanged(it))) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search books, authors, or tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = { Text("Filters") },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            listOf(FileType.PDF, FileType.EPUB, FileType.MOBI, FileType.DOCX, FileType.TXT).forEach { type ->
                FilterChip(
                    selected = type in state.libraryFilters.fileTypes,
                    onClick = {
                        val updated = if (type in state.libraryFilters.fileTypes) state.libraryFilters.fileTypes - type else state.libraryFilters.fileTypes + type
                        onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(fileTypes = updated))))
                    },
                    label = { Text(type.name) }
                )
            }
            ReadStatusFilter.entries.filterNot { it == ReadStatusFilter.ALL }.forEach { status ->
                FilterChip(
                    selected = state.libraryFilters.readStatus == status,
                    onClick = {
                        onStateChange(
                            state.reduce(
                                LibraryAction.FiltersChanged(
                                    state.libraryFilters.copy(
                                        readStatus = if (state.libraryFilters.readStatus == status) ReadStatusFilter.ALL else status
                                    )
                                )
                            )
                        )
                    },
                    label = { Text(status.label) }
                )
            }
            state.allTags.forEach { tag ->
                FilterChip(
                    selected = tag.id in state.libraryFilters.tagIds,
                    onClick = {
                        val updated = if (tag.id in state.libraryFilters.tagIds) state.libraryFilters.tagIds - tag.id else state.libraryFilters.tagIds + tag.id
                        onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(tagIds = updated))))
                    },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(tag.name) }
                )
            }
            if (state.libraryFilters.isActive || state.searchQuery.isNotBlank()) {
                TextButton(onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters()))) }) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookGrid(
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(340.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                selected = book.id in selectedBookIds,
                onOpen = { onOpenBook(book) },
                onToggleSelection = { onToggleSelection(book.id) },
                onShowInfo = { onShowBookInfo(book) },
                onEdit = { onEditBook(book) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookCard(
    book: BookItem,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onToggleSelection)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            BookCover(book = book, selected = selected)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.cardTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = book.cardAuthor(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(onClick = onShowInfo, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onToggleSelection, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (selected) "Clear selection" else "Select"
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(book.type)
                    if (book.sourceFolder != null) {
                        StatusBadge(Icons.Default.Folder, "Folder")
                    }
                    if (book.isOpdsStream()) {
                        StatusBadge(Icons.Default.Cloud, "Stream")
                    }
                }

                ProgressSection(book.progressPercentage)

                if (book.tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(book.tags, key = { it.id }) { tag ->
                            TagChip(tag.name, tag.color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCover(book: BookItem, selected: Boolean) {
    val color = fileTypeColor(book.type)
    Surface(
        modifier = Modifier.size(width = 64.dp, height = 94.dp),
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(7.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(30.dp))
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(3.dp).size(12.dp))
                }
            }
            Text(
                text = book.type.name,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun TypeBadge(type: FileType) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            type.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TagChip(name: String, color: Int?) {
    val tagColor = Color(color ?: 0xFF64B5F6.toInt())
    Surface(
        shape = RoundedCornerShape(50),
        color = tagColor.copy(alpha = 0.14f),
        contentColor = tagColor
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ProgressSection(progressPercentage: Float?) {
    val percent = progressPercentValue(progressPercentage)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun ShelfCollection(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedEmptyState(
            icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = emptyTitle,
            body = emptyBody,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            ShelfSection(
                shelf = shelf,
                selectedBookIds = selectedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf
            )
        }
    }
}

@Composable
private fun ShelfSection(
    shelf: Shelf,
    selectedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (shelf.type) {
                    ShelfType.FOLDER -> Icons.Default.Folder
                    ShelfType.TAG -> Icons.Default.Tag
                    else -> Icons.AutoMirrored.Filled.LibraryBooks
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = {}, label = { Text("${shelf.bookCount}") })
            if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onRenameShelf(shelf) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename shelf", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDeleteShelf(shelf) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete shelf", modifier = Modifier.size(18.dp))
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shelf.books, key = { it.id }) { book ->
                Box(modifier = Modifier.width(360.dp)) {
                    BookCard(
                        book = book,
                        selected = book.id in selectedBookIds,
                        onOpen = { onOpenBook(book) },
                        onToggleSelection = { onToggleSelection(book.id) },
                        onShowInfo = { onShowBookInfo(book) },
                        onEdit = { onEditBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(sortOrder.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

private val NonReaderLibraryTab.label: String
    get() = when (this) {
        NonReaderLibraryTab.BOOKS -> "Books"
        NonReaderLibraryTab.SHELVES -> "Shelves"
        NonReaderLibraryTab.FOLDERS -> "Folders"
    }

private val SortOrder.label: String
    get() = when (this) {
        SortOrder.RECENT -> "Recent"
        SortOrder.TITLE_ASC -> "Title A-Z"
        SortOrder.AUTHOR_ASC -> "Author A-Z"
        SortOrder.PERCENT_ASC -> "Progress low"
        SortOrder.PERCENT_DESC -> "Progress high"
        SortOrder.SIZE_ASC -> "Size small"
        SortOrder.SIZE_DESC -> "Size large"
    }

private val ReadStatusFilter.label: String
    get() = when (this) {
        ReadStatusFilter.ALL -> "All"
        ReadStatusFilter.UNREAD -> "Unread"
        ReadStatusFilter.IN_PROGRESS -> "In progress"
        ReadStatusFilter.COMPLETED -> "Complete"
    }

private fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFF9C4146)
        FileType.EPUB, FileType.MOBI -> Color(0xFF006C4C)
        FileType.DOCX, FileType.ODT, FileType.FODT -> Color(0xFF0F52BA)
        FileType.CBZ, FileType.CBR, FileType.CB7 -> Color(0xFF705D49)
        else -> Color(0xFF5D6B82)
    }
}
