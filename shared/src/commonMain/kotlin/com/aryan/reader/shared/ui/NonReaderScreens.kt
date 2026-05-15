package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.isOpdsStream
import com.aryan.reader.shared.progressPercentValue
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.replaceBookSelectionWithVisibleBooks

enum class NonReaderLibraryTab {
    BOOKS,
    SHELVES,
    SMART_SHELVES,
    TAGS,
    FOLDERS,
    UNREAD,
    IN_PROGRESS,
    COMPLETED
}

private val AndroidLibraryTabs = listOf(
    NonReaderLibraryTab.BOOKS,
    NonReaderLibraryTab.SHELVES,
    NonReaderLibraryTab.FOLDERS
)

internal fun visibleNonReaderLibraryTabs(): List<NonReaderLibraryTab> = AndroidLibraryTabs

private fun NonReaderLibraryTab.visibleLibraryTab(): NonReaderLibraryTab {
    return takeIf { it in AndroidLibraryTabs } ?: NonReaderLibraryTab.BOOKS
}

internal fun SharedReaderScreenState.visibleBooksForLibrarySelection(tab: NonReaderLibraryTab): List<BookItem> {
    return when (tab.visibleLibraryTab()) {
        NonReaderLibraryTab.BOOKS,
        NonReaderLibraryTab.UNREAD,
        NonReaderLibraryTab.IN_PROGRESS,
        NonReaderLibraryTab.COMPLETED -> libraryBooks
        NonReaderLibraryTab.SHELVES -> shelves
            .filter { it.type != ShelfType.FOLDER && it.type != ShelfType.TAG && it.type != ShelfType.SMART }
            .flatMap { it.books }
            .distinctBy { it.id }
        NonReaderLibraryTab.SMART_SHELVES -> shelves
            .filter { it.type == ShelfType.SMART }
            .flatMap { it.books }
            .distinctBy { it.id }
        NonReaderLibraryTab.TAGS -> shelves
            .filter { it.type == ShelfType.TAG && it.bookCount > 0 }
            .flatMap { it.books }
            .distinctBy { it.id }
        NonReaderLibraryTab.FOLDERS -> {
            val currentFolder = viewingShelfId?.let { id -> shelves.firstOrNull { it.id == id && it.type == ShelfType.FOLDER } }
            val folderShelves = currentFolder?.let(::listOf)
                ?: shelves.filter { it.type == ShelfType.FOLDER && it.parentShelfId == null }
            folderShelves.flatMap { it.books }.distinctBy { it.id }
        }
    }
}

private enum class BookViewMode {
    COVERS,
    LIST
}

@Composable
fun SharedHomeScreen(
    state: SharedReaderScreenState,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit = {},
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    onOpenTab: (BookItem) -> Unit = onOpenBook,
    onCloseTab: (BookItem) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onRecentLimitChange: (Int) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    showActiveTabs: Boolean = true,
    modifier: Modifier = Modifier
) {
    val model = state.toNonReaderHomeLayoutModel()
    NonReaderScreenScaffold(
        title = "Home",
        subtitle = "Continue reading and recent books",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Settings")
                }
                RecentLimitMenu(
                    currentLimit = state.recentFilesLimit,
                    onRecentLimitChange = onRecentLimitChange
                )
                OutlinedButton(onClick = onImportFolder) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add folder")
                }
                Button(onClick = onImportBooks) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import files")
                }
            }
        }
    ) {
        if (model.isContextualModeActive) {
            val selectedBooks = model.selectedBooks
            val allSelectedPinned = selectedBooks.isNotEmpty() && selectedBooks.all { it.id in state.pinnedHomeBookIds }
            SelectionToolbar(
                count = selectedBooks.size,
                onClear = onClearSelection,
                onRemove = onRemoveSelected,
                onTag = onTagSelectedBooks,
                onAddToShelf = onAddSelectedBooksToShelf,
                onPin = {
                    selectedBooks
                        .filter { book -> allSelectedPinned || book.id !in state.pinnedHomeBookIds }
                        .forEach(onTogglePinned)
                },
                pinLabel = if (allSelectedPinned) "Unpin" else "Pin",
                onInfo = selectedBooks.singleOrNull()?.let { book -> { onShowBookInfo(book) } }
            )
        }

        if (model.isEmpty) {
            if (model.isLibraryEmpty) {
                LibraryImportEmptyState(
                    onImportBooks = onImportBooks,
                    onImportFolder = onImportFolder,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SharedEmptyState(
                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(56.dp)) },
                    title = "No recent files",
                    body = "Open books from the library and they will appear here.",
                    actionLabel = "Import files",
                    onAction = onImportBooks,
                    secondaryActionLabel = "Add folder",
                    onSecondaryAction = onImportFolder,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                model.continueBook?.let { book ->
                    item(key = "continue_${book.id}") {
                        ContinueReadingCard(
                            book = book,
                            pinned = book.id in state.pinnedHomeBookIds,
                            onOpenBook = { onOpenBook(book) },
                            onShowBookInfo = { onShowBookInfo(book) },
                            onEditBook = { onEditBook(book) },
                            onTogglePinned = { onTogglePinned(book) }
                        )
                    }
                }
                if (showActiveTabs && state.isTabsEnabled && model.activeTabs.isNotEmpty()) {
                    item(key = "tabs") {
                        ActiveTabStrip(
                            openTabs = model.activeTabs,
                            activeBookId = state.activeTabBookId,
                            onOpenTab = onOpenTab,
                            onCloseTab = onCloseTab,
                            onCloseAllTabs = onCloseAllTabs
                        )
                    }
                }
                if (model.pinnedBooks.isNotEmpty()) {
                    item(key = "pinned") {
                        HomeBookShelf(
                            title = "Pinned",
                            books = model.pinnedBooks,
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedHomeBookIds,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned
                        )
                    }
                }
                if (model.recentBooks.isNotEmpty()) {
                    item(key = "recent") {
                        HomeBookShelf(
                            title = "Recent",
                            books = model.recentBooks,
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedHomeBookIds,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned
                        )
                    }
                }
            }
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
    onCreateSmartShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    onImportFolder: () -> Unit = {},
    onSyncFolderMetadata: () -> Unit = {},
    onScanFolders: () -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    useImportEmptyStateWhenLibraryEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    val organization = state.toNonReaderLibraryOrganizationModel()
    val activeLibraryTab = selectedTab.visibleLibraryTab()
    var showFilters by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(BookViewMode.COVERS) }

    fun selectLibraryTab(tab: NonReaderLibraryTab) {
        onTabChange(tab.visibleLibraryTab())
    }

    NonReaderScreenScaffold(
        title = "Library",
        subtitle = "Search, sort, filter, and organize local metadata",
        modifier = modifier
    ) {
        if (state.selectedBookIds.isNotEmpty()) {
            val selectedBooks = state.rawLibraryBooks.filter { it.id in state.selectedBookIds }
            val allSelectedPinned = selectedBooks.isNotEmpty() && selectedBooks.all { it.id in state.pinnedLibraryBookIds }
            val visibleSelectionBooks = state.visibleBooksForLibrarySelection(activeLibraryTab)
            val allVisibleSelected = visibleSelectionBooks.isNotEmpty() &&
                state.selectedBookIds.containsAll(visibleSelectionBooks.map { it.id })
            SelectionToolbar(
                count = state.selectedBookIds.size,
                onClear = onClearSelection,
                onRemove = onRemoveSelected,
                onTag = onTagSelectedBooks,
                onAddToShelf = onAddSelectedBooksToShelf,
                onSelectAll = {
                    onStateChange(state.replaceBookSelectionWithVisibleBooks(visibleSelectionBooks))
                },
                selectAllLabel = if (allVisibleSelected) "Clear visible" else "Select visible",
                onPin = {
                    selectedBooks
                        .filter { book -> allSelectedPinned || book.id !in state.pinnedLibraryBookIds }
                        .forEach(onTogglePinned)
                },
                pinLabel = if (allSelectedPinned) "Unpin" else "Pin",
                onInfo = selectedBooks.singleOrNull()?.let { book -> { onShowBookInfo(book) } }
            )
        }

        if (useImportEmptyStateWhenLibraryEmpty && state.rawLibraryBooks.isEmpty()) {
            LibraryImportEmptyState(
                onImportBooks = onImportBooks,
                onImportFolder = onImportFolder,
                modifier = Modifier.weight(1f)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val useSidebar = maxWidth >= 980.dp
                if (useSidebar) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        LibraryOrganizationSidebar(
                            organization = organization,
                            selectedTab = activeLibraryTab,
                            onTabSelected = ::selectLibraryTab,
                            modifier = Modifier.width(232.dp).fillMaxHeight()
                        )
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LibraryToolbar(
                                state = state,
                                viewMode = viewMode,
                                showFilters = showFilters,
                                onViewModeChange = { viewMode = it },
                                onToggleFilters = { showFilters = !showFilters },
                                onStateChange = onStateChange,
                                onImportBooks = onImportBooks,
                                onImportFolder = onImportFolder,
                                onCreateShelf = onCreateShelf,
                                onCreateSmartShelf = onCreateSmartShelf
                            )
                            LibraryContent(
                                state = state,
                                selectedTab = activeLibraryTab,
                                viewMode = viewMode,
                                showFilters = showFilters,
                                onStateChange = onStateChange,
                                onTabChange = ::selectLibraryTab,
                                onImportBooks = onImportBooks,
                                onImportFolder = onImportFolder,
                                useImportEmptyStateWhenLibraryEmpty = useImportEmptyStateWhenLibraryEmpty,
                                onOpenBook = onOpenBook,
                                onToggleSelection = onToggleSelection,
                                onShowBookInfo = onShowBookInfo,
                                onEditBook = onEditBook,
                                onTogglePinned = onTogglePinned,
                                onRenameShelf = onRenameShelf,
                                onDeleteShelf = onDeleteShelf,
                                onRemoveFolder = onRemoveFolder,
                                onSyncFolderMetadata = onSyncFolderMetadata,
                                onScanFolders = onScanFolders,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryTabStrip(
                            organization = organization,
                            selectedTab = activeLibraryTab,
                            onTabSelected = ::selectLibraryTab
                        )
                        LibraryToolbar(
                            state = state,
                            viewMode = viewMode,
                            showFilters = showFilters,
                            onViewModeChange = { viewMode = it },
                            onToggleFilters = { showFilters = !showFilters },
                            onStateChange = onStateChange,
                            onImportBooks = onImportBooks,
                            onImportFolder = onImportFolder,
                            onCreateShelf = onCreateShelf,
                            onCreateSmartShelf = onCreateSmartShelf
                        )
                        LibraryContent(
                            state = state,
                            selectedTab = activeLibraryTab,
                            viewMode = viewMode,
                            showFilters = showFilters,
                            onStateChange = onStateChange,
                            onTabChange = ::selectLibraryTab,
                            onImportBooks = onImportBooks,
                            onImportFolder = onImportFolder,
                            useImportEmptyStateWhenLibraryEmpty = useImportEmptyStateWhenLibraryEmpty,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned,
                            onRenameShelf = onRenameShelf,
                            onDeleteShelf = onDeleteShelf,
                            onRemoveFolder = onRemoveFolder,
                            onSyncFolderMetadata = onSyncFolderMetadata,
                            onScanFolders = onScanFolders,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String> = emptySet(),
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onCreateShelf: () -> Unit = {},
    onCreateSmartShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NonReaderScreenScaffold(
        title = "Shelves",
        subtitle = "Collections, series, tags, and folders",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCreateShelf) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shelf")
                }
            }
        }
    ) {
        ShelfCollection(
            shelves = shelves,
            selectedBookIds = selectedBookIds,
            pinnedBookIds = pinnedBookIds,
            onOpenBook = onOpenBook,
            onToggleSelection = onToggleSelection,
            onShowBookInfo = onShowBookInfo,
            onEditBook = onEditBook,
            onTogglePinned = onTogglePinned,
            onRenameShelf = onRenameShelf,
            onDeleteShelf = onDeleteShelf,
            onRemoveFolder = onRemoveFolder,
            emptyTitle = "No shelves yet",
            emptyBody = "Add shelves, tags, or folder metadata to organize your library.",
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
            .background(MaterialTheme.colorScheme.background)
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
private fun ContinueReadingCard(
    book: BookItem,
    pinned: Boolean,
    onOpenBook: () -> Unit,
    onShowBookInfo: () -> Unit,
    onEditBook: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BookCoverArt(
                book = book,
                selected = false,
                modifier = Modifier.size(width = 112.dp, height = 164.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Continue reading", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(book.cardTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressSection(book.progressPercentage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpenBook) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Read")
                    }
                    IconButton(onClick = onTogglePinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (pinned) "Unpin" else "Pin",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onShowBookInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = onEditBook) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBookShelf(
    title: String,
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 12.dp)) {
            items(books, key = { it.id }) { book ->
                BookTile(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    selectionModeActive = selectedBookIds.isNotEmpty(),
                    onOpen = { onOpenBook(book) },
                    onToggleSelection = { onToggleSelection(book.id) },
                    onShowInfo = { onShowBookInfo(book) },
                    onEdit = { onEditBook(book) },
                    onTogglePinned = { onTogglePinned(book) },
                    modifier = Modifier.width(168.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onRemove: () -> Unit,
    onTag: () -> Unit = {},
    onAddToShelf: () -> Unit = {},
    onSelectAll: (() -> Unit)? = null,
    selectAllLabel: String = "Select visible",
    onPin: (() -> Unit)? = null,
    pinLabel: String = "Pin",
    onInfo: (() -> Unit)? = null
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
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onInfo?.let { info ->
                    TextButton(onClick = info) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Info")
                    }
                }
                onPin?.let { pin ->
                    TextButton(onClick = pin) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(pinLabel)
                    }
                }
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
                onSelectAll?.let { selectAll ->
                    TextButton(onClick = selectAll) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectAllLabel)
                    }
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
}

@Composable
private fun ActiveTabStrip(
    openTabs: List<BookItem>,
    activeBookId: String?,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active tabs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCloseAllTabs) {
                Text("Close all")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(openTabs, key = { it.id }) { book ->
                val active = book.id == activeBookId
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTab(book) }
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = book.cardTitle(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCloseTab(book) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentLimitMenu(
    currentLimit: Int,
    onRecentLimitChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val normalizedLimit = currentLimit.coerceAtLeast(0)
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (normalizedLimit == 0) "No limit" else "$normalizedLimit")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0, 10, 20, 50, 100).forEach { limit ->
                DropdownMenuItem(
                    text = { Text(if (limit == 0) "No limit" else "$limit files") },
                    onClick = {
                        expanded = false
                        onRecentLimitChange(limit)
                    },
                    trailingIcon = if (normalizedLimit == limit) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryOrganizationSidebar(
    organization: NonReaderLibraryOrganizationModel,
    selectedTab: NonReaderLibraryTab,
    onTabSelected: (NonReaderLibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "Browse",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            visibleNonReaderLibraryTabs().forEach { tab ->
                item {
                    LibraryNavItem(
                        icon = tab.icon,
                        label = tab.label,
                        count = tab.count(organization),
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabStrip(
    organization: NonReaderLibraryOrganizationModel,
    selectedTab: NonReaderLibraryTab,
    onTabSelected: (NonReaderLibraryTab) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleNonReaderLibraryTabs().forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("${tab.label} ${tab.count(organization)}") }
            )
        }
    }
}

@Composable
private fun LibraryNavItem(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LibraryToolbar(
    state: SharedReaderScreenState,
    viewMode: BookViewMode,
    showFilters: Boolean,
    onViewModeChange: (BookViewMode) -> Unit,
    onToggleFilters: () -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onCreateShelf: () -> Unit,
    onCreateSmartShelf: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SharedStableOutlinedTextField(
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
            SortMenu(sortOrder = state.sortOrder, onSortOrderChange = { onStateChange(state.reduce(LibraryAction.SortChanged(it))) })
            OutlinedButton(onClick = { onViewModeChange(if (viewMode == BookViewMode.COVERS) BookViewMode.LIST else BookViewMode.COVERS) }) {
                Icon(if (viewMode == BookViewMode.COVERS) Icons.AutoMirrored.Filled.List else Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (viewMode == BookViewMode.COVERS) "List" else "Covers")
            }
            OutlinedButton(onClick = onToggleFilters) {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showFilters) "Hide filters" else "Filters")
                if (state.libraryFilters.isActive) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            state.libraryFilters.activeFilterBadge(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            OutlinedButton(onClick = onCreateShelf) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shelf")
            }
            OutlinedButton(onClick = onImportFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add folder")
            }
            Button(onClick = onImportBooks) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import files")
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: SharedReaderScreenState,
    selectedTab: NonReaderLibraryTab,
    viewMode: BookViewMode,
    showFilters: Boolean,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onTabChange: (NonReaderLibraryTab) -> Unit = {},
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    useImportEmptyStateWhenLibraryEmpty: Boolean = false,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onSyncFolderMetadata: () -> Unit,
    onScanFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showFilters) {
            LibraryFilterPanel(
                state = state,
                onStateChange = onStateChange
            )
        } else if (state.libraryFilters.isActive || state.searchQuery.isNotBlank()) {
            LibraryFilterSummary(state = state, onStateChange = onStateChange)
        }

        when (selectedTab) {
            NonReaderLibraryTab.BOOKS,
            NonReaderLibraryTab.UNREAD,
            NonReaderLibraryTab.IN_PROGRESS,
            NonReaderLibraryTab.COMPLETED -> {
                val books = state.libraryBooks
                if (books.isEmpty()) {
                    if (state.rawLibraryBooks.isEmpty() && useImportEmptyStateWhenLibraryEmpty) {
                        LibraryImportEmptyState(
                            onImportBooks = onImportBooks,
                            onImportFolder = onImportFolder,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SharedEmptyState(
                            icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(56.dp)) },
                            title = if (state.rawLibraryBooks.isEmpty()) "Your library is empty" else "No books match",
                            body = if (state.rawLibraryBooks.isEmpty()) "Import files into app storage or add a folder from the toolbar." else "Adjust search, sort, or filters to see more books.",
                            actionLabel = if (state.rawLibraryBooks.isEmpty()) "Import files" else "Clear filters",
                            onAction = {
                                if (state.rawLibraryBooks.isEmpty()) {
                                    onImportBooks()
                                } else {
                                    onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters())))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    BookGrid(
                        books = books,
                        viewMode = viewMode,
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        onTogglePinned = onTogglePinned,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            NonReaderLibraryTab.SHELVES -> {
                val tagShelves = state.shelves.filter { it.type == ShelfType.TAG && it.bookCount > 0 }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrowseByTagRow(
                        tagShelves = tagShelves,
                        onTagShelfSelected = { shelf ->
                            val tagId = shelf.id.removePrefix("tag_").takeIf { it.isNotBlank() }
                            if (tagId != null) {
                                onStateChange(
                                    state.reduce(
                                        LibraryAction.FiltersChanged(
                                            state.libraryFilters.copy(tagIds = setOf(tagId))
                                        )
                                    )
                                )
                                onTabChange(NonReaderLibraryTab.BOOKS)
                            }
                        }
                    )
                    ShelfCollection(
                        shelves = state.shelves.filter { it.type != ShelfType.FOLDER && it.type != ShelfType.TAG && it.type != ShelfType.SMART },
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        onTogglePinned = onTogglePinned,
                        onRenameShelf = onRenameShelf,
                        onDeleteShelf = onDeleteShelf,
                        onRemoveFolder = onRemoveFolder,
                        emptyTitle = "No shelves yet",
                        emptyBody = "Manual shelves and series collections will appear here.",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            NonReaderLibraryTab.SMART_SHELVES -> ShelfCollection(
                shelves = state.shelves.filter { it.type == ShelfType.SMART },
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
                emptyTitle = "No smart shelves yet",
                emptyBody = "Create smart shelves to collect books by rules.",
                modifier = Modifier.weight(1f)
            )

            NonReaderLibraryTab.TAGS -> ShelfCollection(
                shelves = state.shelves.filter { it.type == ShelfType.TAG && it.bookCount > 0 },
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                emptyTitle = "No tags yet",
                emptyBody = "Tags added to books will appear here.",
                modifier = Modifier.weight(1f)
            )

            NonReaderLibraryTab.FOLDERS -> {
                val currentFolder = state.viewingShelfId
                    ?.let { id -> state.shelves.firstOrNull { it.id == id && it.type == ShelfType.FOLDER } }
                if (currentFolder != null) {
                    FolderShelfDetail(
                        shelf = currentFolder,
                        childShelves = currentFolder.childShelfIds.mapNotNull { childId ->
                            state.shelves.firstOrNull { it.id == childId }
                        },
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        onTogglePinned = onTogglePinned,
                        onOpenShelf = { shelf -> onStateChange(state.copy(viewingShelfId = shelf.id)) },
                        onBack = { onStateChange(state.copy(viewingShelfId = currentFolder.parentShelfId)) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.syncedFolders.isNotEmpty()) {
                            FolderSyncActionRow(
                                onSyncFolderMetadata = onSyncFolderMetadata,
                                onScanFolders = onScanFolders
                            )
                        }
                        ShelfCollection(
                            shelves = state.shelves.filter { it.type == ShelfType.FOLDER && it.parentShelfId == null },
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedLibraryBookIds,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned,
                            onRemoveFolder = onRemoveFolder,
                            onOpenShelf = { shelf -> onStateChange(state.copy(viewingShelfId = shelf.id)) },
                            emptyTitle = "No folders yet",
                            emptyBody = "Add a folder to read files from that folder in place.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun FolderSyncActionRow(
    onSyncFolderMetadata: () -> Unit,
    onScanFolders: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onSyncFolderMetadata) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sync metadata")
        }
        Button(onClick = onScanFolders) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Full scan")
        }
    }
}

@Composable
private fun LibraryFilterSummary(
    state: SharedReaderScreenState,
    onStateChange: (SharedReaderScreenState) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.searchQuery.isNotBlank()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged(""))) },
                label = { Text("Search: ${state.searchQuery}") },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.fileTypes.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(fileTypes = emptySet())))) },
                label = {
                    Text(
                        "Types: ${
                            state.libraryFilters.fileTypes
                                .sortedBy { it.ordinal }
                                .joinToString { SharedFileCapabilities.displayNameFor(it) }
                        }"
                    )
                },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear file types", modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.sourceFolders.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(sourceFolders = emptySet())))) },
                label = { Text("Sources: ${state.libraryFilters.sourceFolders.size}") },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear sources", modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.readStatus != ReadStatusFilter.ALL) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(readStatus = ReadStatusFilter.ALL)))) },
                label = { Text("Status: ${state.libraryFilters.readStatus.label}") },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear status", modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.tagIds.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(tagIds = emptySet())))) },
                label = { Text("Tags: ${state.libraryFilters.tagIds.size}") },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear tags", modifier = Modifier.size(16.dp)) }
            )
        }
        TextButton(onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters()))) }) {
            Text("Clear all")
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryFilterPanel(
    state: SharedReaderScreenState,
    onStateChange: (SharedReaderScreenState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.libraryFilters.isActive || state.searchQuery.isNotBlank()) {
                    TextButton(onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters()))) }) {
                        Text("Clear")
                    }
                }
            }

            LibraryFilterSection(title = "File type") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nonReaderLibraryFileTypeGroups().forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                group.fileTypes.forEach { type ->
                                    FilterChip(
                                        selected = type in state.libraryFilters.fileTypes,
                                        onClick = {
                                            val updated = state.libraryFilters.fileTypes.toggle(type)
                                            onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(fileTypes = updated))))
                                        },
                                        label = { Text(SharedFileCapabilities.displayNameFor(type)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryFilterSection(title = "Source folder") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = IN_APP_STORAGE_SOURCE in state.libraryFilters.sourceFolders,
                        onClick = {
                            val updated = state.libraryFilters.sourceFolders.toggle(IN_APP_STORAGE_SOURCE)
                            onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(sourceFolders = updated))))
                        },
                        label = { Text("In-app") }
                    )
                    state.syncedFolders.forEach { folder ->
                        FilterChip(
                            selected = folder.uriString in state.libraryFilters.sourceFolders,
                            onClick = {
                                val updated = state.libraryFilters.sourceFolders.toggle(folder.uriString)
                                onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(sourceFolders = updated))))
                            },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(folder.name) }
                        )
                    }
                }
            }

            LibraryFilterSection(title = "Read status") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReadStatusFilter.entries.forEach { status ->
                        FilterChip(
                            selected = state.libraryFilters.readStatus == status,
                            onClick = {
                                onStateChange(
                                    state.reduce(
                                        LibraryAction.FiltersChanged(
                                            state.libraryFilters.copy(readStatus = status)
                                        )
                                    )
                                )
                            },
                            label = { Text(status.label) }
                        )
                    }
                }
            }

            if (state.allTags.isNotEmpty()) {
                LibraryFilterSection(title = "Tags") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.allTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in state.libraryFilters.tagIds,
                                onClick = {
                                    val updated = state.libraryFilters.tagIds.toggle(tag.id)
                                    onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(tagIds = updated))))
                                },
                                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (value in this) this - value else this + value
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookGrid(
    books: List<BookItem>,
    viewMode: BookViewMode,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (viewMode == BookViewMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookListItem(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    selectionModeActive = selectedBookIds.isNotEmpty(),
                    onOpen = { onOpenBook(book) },
                    onToggleSelection = { onToggleSelection(book.id) },
                    onShowInfo = { onShowBookInfo(book) },
                    onEdit = { onEditBook(book) },
                    onTogglePinned = { onTogglePinned(book) }
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookTile(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    selectionModeActive = selectedBookIds.isNotEmpty(),
                    onOpen = { onOpenBook(book) },
                    onToggleSelection = { onToggleSelection(book.id) },
                    onShowInfo = { onShowBookInfo(book) },
                    onEdit = { onEditBook(book) },
                    onTogglePinned = { onTogglePinned(book) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookTile(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    selectionModeActive: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionModeActive) onToggleSelection() else onOpen()
                },
                onLongClick = onToggleSelection
            )
    ) {
        Column {
            Box {
                BookCoverArt(
                    book = book,
                    selected = selected,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (pinned) {
                        OverlayBadge(Icons.Default.PushPin, "Pinned")
                    }
                    if (book.sourceFolder != null) {
                        OverlayBadge(Icons.Default.Folder, "Folder")
                    }
                    if (book.isOpdsStream()) {
                        OverlayBadge(Icons.Default.Cloud, "Stream")
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Book actions")
                    }
                    BookActionMenu(
                        expanded = menuExpanded,
                        pinned = pinned,
                        selected = selected,
                        onDismiss = { menuExpanded = false },
                        onTogglePinned = onTogglePinned,
                        onShowInfo = onShowInfo,
                        onEdit = onEdit,
                        onToggleSelection = onToggleSelection
                    )
                }
                TypeBadge(book.type, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                val percent = progressPercentValue(book.progressPercentage)
                if (percent > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, minLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookListItem(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    selectionModeActive: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionModeActive) onToggleSelection() else onOpen()
                },
                onLongClick = onToggleSelection
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCoverArt(book = book, selected = selected, modifier = Modifier.size(width = 58.dp, height = 84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(book.type)
                    if (pinned) StatusBadge(Icons.Default.PushPin, "Pinned")
                    if (book.sourceFolder != null) StatusBadge(Icons.Default.Folder, "Folder")
                    if (book.isOpdsStream()) StatusBadge(Icons.Default.Cloud, "Stream")
                }
                ProgressSection(book.progressPercentage)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Book actions")
                }
                BookActionMenu(
                    expanded = menuExpanded,
                    pinned = pinned,
                    selected = selected,
                    onDismiss = { menuExpanded = false },
                    onTogglePinned = onTogglePinned,
                    onShowInfo = onShowInfo,
                    onEdit = onEdit,
                    onToggleSelection = onToggleSelection
                )
            }
        }
    }
}

@Composable
private fun BookActionMenu(
    expanded: Boolean,
    pinned: Boolean,
    selected: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onToggleSelection: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
            text = { Text(if (pinned) "Unpin" else "Pin") },
            onClick = {
                onDismiss()
                onTogglePinned()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            text = { Text("Info") },
            onClick = {
                onDismiss()
                onShowInfo()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text("Edit") },
            onClick = {
                onDismiss()
                onEdit()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.List, contentDescription = null) },
            text = { Text(if (selected) "Clear selection" else "Select") },
            onClick = {
                onDismiss()
                onToggleSelection()
            }
        )
    }
}

@Composable
private fun BookCoverArt(
    book: BookItem,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = fileTypeColor(book.type)
    val coverPath = book.coverImagePath?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(34.dp))
            Text(
                text = book.type.name,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
            )
            if (coverPath != null) {
                LocalBookCoverImage(
                    path = coverPath,
                    contentDescription = book.cardTitle(),
                    modifier = Modifier.matchParentSize()
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(8.dp).size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.52f),
        contentColor = Color.White
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.padding(5.dp).size(13.dp))
    }
}

@Composable
private fun TypeBadge(type: FileType, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            type.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun StatusBadge(icon: ImageVector, label: String) {
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
private fun BrowseByTagRow(
    tagShelves: List<Shelf>,
    onTagShelfSelected: (Shelf) -> Unit
) {
    if (tagShelves.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Browse by tag",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tagShelves.forEach { shelf ->
                FilterChip(
                    selected = false,
                    onClick = { onTagShelfSelected(shelf) },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(shelf.name) }
                )
            }
        }
    }
}

@Composable
private fun ShelfCollection(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    onOpenShelf: ((Shelf) -> Unit)? = null,
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            ShelfSection(
                shelf = shelf,
                selectedBookIds = selectedBookIds,
                pinnedBookIds = pinnedBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
                onRemoveFolder = onRemoveFolder,
                onOpenShelf = onOpenShelf
            )
        }
    }
}

@Composable
private fun ShelfSection(
    shelf: Shelf,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onOpenShelf: ((Shelf) -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val openShelf = onOpenShelf
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (openShelf != null) Modifier.clickable { openShelf(shelf) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CollectionCoverStack(shelf)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = shelf.type.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (openShelf != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                    IconButton(onClick = { onRenameShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename shelf", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDeleteShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete shelf", modifier = Modifier.size(18.dp))
                    }
                } else if (shelf.type == ShelfType.FOLDER && shelf.parentShelfId == null) {
                    IconButton(onClick = { onRemoveFolder(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove folder", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (shelf.books.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.books.take(12), key = { it.id }) { book ->
                        BookTile(
                            book = book,
                            selected = book.id in selectedBookIds,
                            pinned = book.id in pinnedBookIds,
                            selectionModeActive = selectedBookIds.isNotEmpty(),
                            onOpen = { onOpenBook(book) },
                            onToggleSelection = { onToggleSelection(book.id) },
                            onShowInfo = { onShowBookInfo(book) },
                            onEdit = { onEditBook(book) },
                            onTogglePinned = { onTogglePinned(book) },
                            modifier = Modifier.width(148.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderShelfDetail(
    shelf: Shelf,
    childShelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onOpenShelf: (Shelf) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(shelf.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (childShelves.isEmpty() && shelf.directBooks.isEmpty()) {
            SharedEmptyState(
                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(56.dp)) },
                title = "Folder is empty",
                body = "No supported files or subfolders are available here.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (childShelves.isNotEmpty()) {
                    item(key = "folders_header") {
                        SectionLabel("Folders")
                    }
                    items(childShelves, key = { it.id }) { childShelf ->
                        FolderShelfListItem(
                            shelf = childShelf,
                            onOpenShelf = { onOpenShelf(childShelf) }
                        )
                    }
                }
                if (shelf.directBooks.isNotEmpty()) {
                    item(key = "files_header") {
                        SectionLabel("Files")
                    }
                    items(shelf.directBooks, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            selected = book.id in selectedBookIds,
                            pinned = book.id in pinnedBookIds,
                            selectionModeActive = selectedBookIds.isNotEmpty(),
                            onOpen = { onOpenBook(book) },
                            onToggleSelection = { onToggleSelection(book.id) },
                            onShowInfo = { onShowBookInfo(book) },
                            onEdit = { onEditBook(book) },
                            onTogglePinned = { onTogglePinned(book) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FolderShelfListItem(
    shelf: Shelf,
    onOpenShelf: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenShelf),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionCoverStack(shelf)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(shelf.subtitleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Shelf.subtitleLabel(): String {
    if (type != ShelfType.FOLDER) return bookCountLabel(bookCount)
    val parts = buildList {
        if (childShelfCount > 0) add(folderCountLabel(childShelfCount))
        if (directBookCount > 0) add(fileCountLabel(directBookCount))
    }
    return parts.ifEmpty { listOf(bookCountLabel(bookCount)) }.joinToString(", ")
}

private fun bookCountLabel(count: Int): String {
    return "$count ${if (count == 1) "book" else "books"}"
}

private fun folderCountLabel(count: Int): String {
    return "$count ${if (count == 1) "folder" else "folders"}"
}

private fun fileCountLabel(count: Int): String {
    return "$count ${if (count == 1) "file" else "files"}"
}

@Composable
private fun CollectionCoverStack(shelf: Shelf) {
    val booksForCovers = collectionCoverStackBooks(shelf)
    if (booksForCovers.isEmpty()) {
        EmptyCollectionCoverStack(shelf)
        return
    }

    val coverWidth = 38.dp
    val coverHeight = 56.dp
    val horizontalOffset = 7.dp
    val stackWidth = coverWidth + (horizontalOffset * (booksForCovers.size - 1))

    Box(
        modifier = Modifier.size(width = 54.dp, height = 66.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(stackWidth)
                .height(coverHeight)
        ) {
            booksForCovers.forEachIndexed { index, book ->
                CollectionCoverBook(
                    book = book,
                    contentDescription = if (booksForCovers.size == 1) shelf.name else null,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .align(Alignment.CenterEnd)
                        .offset(x = -horizontalOffset * index)
                )
            }
        }
    }
}

@Composable
private fun CollectionCoverBook(
    book: BookItem,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val coverPath = book.coverImagePath?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier,
        color = fileTypeColor(book.type),
        contentColor = Color.White,
        shape = RoundedCornerShape(7.dp),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp))
            if (coverPath != null) {
                LocalBookCoverImage(
                    path = coverPath,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun EmptyCollectionCoverStack(shelf: Shelf) {
    Box(Modifier.size(width = 54.dp, height = 66.dp)) {
        val colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.36f)
        )
        colors.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 56.dp)
                    .align(Alignment.Center)
                    .padding(start = (index * 4).dp, top = (index * 2).dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
            )
        }
        Icon(shelf.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}

internal fun collectionCoverStackBooks(shelf: Shelf): List<BookItem> {
    val booksForCovers = shelf.books.take(CollectionCoverStackBookLimit).reversed()
    return if (booksForCovers.size <= 1) {
        listOfNotNull(shelf.topBook)
    } else {
        booksForCovers
    }
}

private const val CollectionCoverStackBookLimit = 4

@Composable
private fun SortMenu(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
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
                    },
                    trailingIcon = if (sortOrder == order) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryImportEmptyState(
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedEmptyState(
        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(56.dp)) },
        title = "Your library is empty",
        body = "Import files into app storage or add a folder to read files in place.",
        actionLabel = "Import files",
        onAction = onImportBooks,
        secondaryActionLabel = "Add folder",
        onSecondaryAction = onImportFolder,
        modifier = modifier
    )
}

@Composable
private fun SharedEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onAction) {
                            Text(actionLabel)
                        }
                        if (secondaryActionLabel != null && onSecondaryAction != null) {
                            OutlinedButton(onClick = onSecondaryAction) {
                                Text(secondaryActionLabel)
                            }
                        }
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
        NonReaderLibraryTab.SMART_SHELVES -> "Smart"
        NonReaderLibraryTab.TAGS -> "Tags"
        NonReaderLibraryTab.FOLDERS -> "Folders"
        NonReaderLibraryTab.UNREAD -> "Unread"
        NonReaderLibraryTab.IN_PROGRESS -> "In progress"
        NonReaderLibraryTab.COMPLETED -> "Complete"
    }

private val NonReaderLibraryTab.icon: ImageVector
    get() = when (this) {
        NonReaderLibraryTab.BOOKS -> Icons.Default.Book
        NonReaderLibraryTab.SHELVES -> Icons.AutoMirrored.Filled.LibraryBooks
        NonReaderLibraryTab.SMART_SHELVES -> Icons.Default.FilterList
        NonReaderLibraryTab.TAGS -> Icons.Default.Tag
        NonReaderLibraryTab.FOLDERS -> Icons.Default.Folder
        NonReaderLibraryTab.UNREAD -> Icons.Default.Book
        NonReaderLibraryTab.IN_PROGRESS -> Icons.AutoMirrored.Filled.MenuBook
        NonReaderLibraryTab.COMPLETED -> Icons.Default.Check
    }

private fun NonReaderLibraryTab.count(organization: NonReaderLibraryOrganizationModel): Int {
    return when (this) {
        NonReaderLibraryTab.BOOKS -> organization.allBooksCount
        NonReaderLibraryTab.SHELVES -> organization.shelfCount
        NonReaderLibraryTab.SMART_SHELVES -> organization.smartShelfCount
        NonReaderLibraryTab.TAGS -> organization.tagCount
        NonReaderLibraryTab.FOLDERS -> organization.folderCount
        NonReaderLibraryTab.UNREAD -> organization.unreadCount
        NonReaderLibraryTab.IN_PROGRESS -> organization.inProgressCount
        NonReaderLibraryTab.COMPLETED -> organization.completedCount
    }
}

private fun NonReaderLibraryTab.readStatusFilter(): ReadStatusFilter? {
    return when (this) {
        NonReaderLibraryTab.UNREAD -> ReadStatusFilter.UNREAD
        NonReaderLibraryTab.IN_PROGRESS -> ReadStatusFilter.IN_PROGRESS
        NonReaderLibraryTab.COMPLETED -> ReadStatusFilter.COMPLETED
        else -> null
    }
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

private val ShelfType.icon: ImageVector
    get() = when (this) {
        ShelfType.FOLDER -> Icons.Default.Folder
        ShelfType.TAG -> Icons.Default.Tag
        ShelfType.SMART -> Icons.Default.FilterList
        else -> Icons.AutoMirrored.Filled.LibraryBooks
    }

private fun LibraryFilters.activeFilterBadge(): String {
    val count = fileTypes.size +
        sourceFolders.size +
        tagIds.size +
        if (readStatus == ReadStatusFilter.ALL) 0 else 1
    return count.toString()
}

private fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFF9C4146)
        FileType.EPUB, FileType.MOBI -> Color(0xFF006C4C)
        FileType.DOCX, FileType.ODT, FileType.FODT, FileType.PPTX -> Color(0xFF0F52BA)
        FileType.CBZ, FileType.CBR, FileType.CB7 -> Color(0xFF705D49)
        else -> Color(0xFF5D6B82)
    }
}
