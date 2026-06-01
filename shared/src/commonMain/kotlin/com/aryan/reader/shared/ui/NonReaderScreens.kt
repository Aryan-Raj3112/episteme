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
import com.aryan.reader.shared.ReaderPlatform
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

internal fun SharedReaderScreenState.visibleBooksForLibrarySelection(
    tab: NonReaderLibraryTab,
    platform: ReaderPlatform = ReaderPlatform.ANDROID
): List<BookItem> {
    return when (tab.visibleLibraryTab(platform)) {
        NonReaderLibraryTab.BOOKS,
        NonReaderLibraryTab.UNREAD,
        NonReaderLibraryTab.IN_PROGRESS,
        NonReaderLibraryTab.COMPLETED -> booksForNonReaderLibraryTab(tab, platform)
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
    val model = remember(
        state.recentBooks,
        state.openTabs,
        state.openTabIds,
        state.activeTabBookId,
        state.isTabsEnabled,
        state.pinnedHomeBookIds,
        state.selectedBookIds,
        state.rawLibraryBooks
    ) {
        state.toNonReaderHomeLayoutModel()
    }
    NonReaderScreenScaffold(
        title = readerString("nav_home", "Home"),
        subtitle = readerString("desktop_home_subtitle", "Continue reading and recent books"),
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("settings", "Settings"))
                }
                RecentLimitMenu(
                    currentLimit = state.recentFilesLimit,
                    onRecentLimitChange = onRecentLimitChange
                )
                OutlinedButton(onClick = onImportFolder) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("fab_add_folder", "Add folder"))
                }
                Button(onClick = onImportBooks) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("desktop_import_files", "Import files"))
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
                    title = readerString("no_recent_files", "No recent files"),
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
                            title = readerString("pinned", "Pinned"),
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
                            title = readerString("sort_recent", "Recent"),
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
    onCreateShelfWithBooks: (String, Set<String>) -> Unit = { _, _ -> },
    onCreateSmartShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    onAddBooksToShelf: (Set<String>) -> Unit = {},
    onManageShelfBooks: ((Shelf) -> Unit)? = null,
    onImportFolder: () -> Unit = {},
    onSyncFolderMetadata: () -> Unit = {},
    onScanFolders: () -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    platform: ReaderPlatform = ReaderPlatform.ANDROID,
    useImportEmptyStateWhenLibraryEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    val organization = remember(
        state.rawLibraryBooks,
        state.shelves,
        state.allTags,
        state.syncedFolders,
        state.libraryFilters
    ) {
        state.toNonReaderLibraryOrganizationModel()
    }
    val activeLibraryTab = selectedTab.visibleLibraryTab(platform)
    var showFilters by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(BookViewMode.COVERS) }

    fun selectLibraryTab(tab: NonReaderLibraryTab) {
        onTabChange(tab.visibleLibraryTab(platform))
    }

    NonReaderScreenScaffold(
        title = readerString("library_title", "Library"),
        subtitle = readerString("desktop_library_subtitle", "Browse your collection"),
        showHeader = false,
        modifier = modifier
    ) {
        if (state.selectedBookIds.isNotEmpty()) {
            val selectedBooks = state.rawLibraryBooks.filter { it.id in state.selectedBookIds }
            val allSelectedPinned = selectedBooks.isNotEmpty() && selectedBooks.all { it.id in state.pinnedLibraryBookIds }
            val visibleSelectionBooks = state.visibleBooksForLibrarySelection(activeLibraryTab, platform)
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
                            platform = platform,
                            modifier = Modifier.width(SharedUiTokens.sidebarWidth).fillMaxHeight()
                        )
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LibraryToolbar(
                                state = state,
                                selectedTab = activeLibraryTab,
                                viewMode = viewMode,
                                showFilters = showFilters,
                                platform = platform,
                                onViewModeChange = { viewMode = it },
                                onToggleFilters = { showFilters = !showFilters },
                                onStateChange = onStateChange,
                                onImportBooks = onImportBooks,
                                onImportFolder = onImportFolder,
                                onCreateShelf = onCreateShelf
                            )
                            LibraryContent(
                                state = state,
                                selectedTab = activeLibraryTab,
                                viewMode = viewMode,
                                showFilters = showFilters,
                                platform = platform,
                                onStateChange = onStateChange,
                                onTabChange = ::selectLibraryTab,
                                onImportBooks = onImportBooks,
                                onImportFolder = onImportFolder,
                                useImportEmptyStateWhenLibraryEmpty = useImportEmptyStateWhenLibraryEmpty,
                                onCreateShelf = onCreateShelf,
                                onOpenBook = onOpenBook,
                                onToggleSelection = onToggleSelection,
                                onShowBookInfo = onShowBookInfo,
                                onEditBook = onEditBook,
                                onTogglePinned = onTogglePinned,
                                onAddBooksToShelf = onAddBooksToShelf,
                                onManageShelfBooks = onManageShelfBooks,
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
                            onTabSelected = ::selectLibraryTab,
                            platform = platform
                        )
                        LibraryToolbar(
                            state = state,
                            selectedTab = activeLibraryTab,
                            viewMode = viewMode,
                            showFilters = showFilters,
                            platform = platform,
                            onViewModeChange = { viewMode = it },
                            onToggleFilters = { showFilters = !showFilters },
                            onStateChange = onStateChange,
                            onImportBooks = onImportBooks,
                            onImportFolder = onImportFolder,
                            onCreateShelf = onCreateShelf
                        )
                        LibraryContent(
                            state = state,
                            selectedTab = activeLibraryTab,
                            viewMode = viewMode,
                            showFilters = showFilters,
                            platform = platform,
                            onStateChange = onStateChange,
                            onTabChange = ::selectLibraryTab,
                            onImportBooks = onImportBooks,
                            onImportFolder = onImportFolder,
                            useImportEmptyStateWhenLibraryEmpty = useImportEmptyStateWhenLibraryEmpty,
                            onCreateShelf = onCreateShelf,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned,
                            onAddBooksToShelf = onAddBooksToShelf,
                            onManageShelfBooks = onManageShelfBooks,
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
        title = readerString("tab_shelves", "Shelves"),
        subtitle = readerString("desktop_shelves_subtitle", "Collections, series, tags, and folders"),
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCreateShelf) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("fab_new_shelf", "New shelf"))
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
            emptyTitle = readerString("desktop_no_shelves_yet", "No shelves yet"),
            emptyBody = readerString(
                "desktop_shelves_overview_empty_desc",
                "Add shelves, tags, or folder metadata to organize your library."
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NonReaderScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(SharedUiTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SharedUiTokens.contentGap)
    ) {
        if (showHeader) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing()
            }
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
        shape = RoundedCornerShape(SharedUiTokens.surfaceRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = sharedSubtleBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BookCoverArt(
                book = book,
                selected = false,
                modifier = Modifier.size(width = 112.dp, height = 164.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(readerString("action_continue_reading", "Continue reading"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(book.cardTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressSection(book.progressPercentage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpenBook) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(readerString("action_read", "Read"))
                    }
                    IconButton(onClick = onTogglePinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (pinned) "Unpin" else "Pin",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onShowBookInfo) {
                        Icon(Icons.Default.Info, contentDescription = readerString("info", "Info"))
                    }
                    IconButton(onClick = onEditBook) {
                        Icon(Icons.Default.Edit, contentDescription = readerString("action_edit", "Edit"))
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
            Text(readerString("items_selected_count", "%1\$d selected", count), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                        Text(readerString("info", "Info"))
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
                    Text(readerString("content_desc_tag", "Tag"))
                }
                TextButton(onClick = onAddToShelf) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("desktop_add_to_shelf", "Add to shelf"))
                }
                onSelectAll?.let { selectAll ->
                    TextButton(onClick = selectAll) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectAllLabel)
                    }
                }
                TextButton(onClick = onClear) {
                    Text(readerString("action_clear", "Clear"))
                }
                TextButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("action_remove", "Remove"))
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
            Text(readerString("desktop_open_readers", "Open readers"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCloseAllTabs) {
                Text(readerString("close_all_tabs", "Close all"))
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
                                            Icon(Icons.Default.Close, contentDescription = readerString("desktop_close_reader", "Close reader"), modifier = Modifier.size(18.dp))
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
                        { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) }
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
    platform: ReaderPlatform,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SharedUiTokens.surfaceRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = sharedSubtleBorder()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(SharedUiTokens.compactGap),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    readerString("desktop_browse", "Browse"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            visibleNonReaderLibraryTabs(platform).forEach { tab ->
                item {
                    LibraryNavItem(
                        icon = tab.icon,
                        label = tab.label(),
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
    onTabSelected: (NonReaderLibraryTab) -> Unit,
    platform: ReaderPlatform
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleNonReaderLibraryTabs(platform).forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(tab.labelWithCount(organization)) }
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
    selectedTab: NonReaderLibraryTab,
    viewMode: BookViewMode,
    showFilters: Boolean,
    platform: ReaderPlatform,
    onViewModeChange: (BookViewMode) -> Unit,
    onToggleFilters: () -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onCreateShelf: () -> Unit
) {
    BoxWithConstraints {
        val layout = libraryCommandBarLayoutForWidth(maxWidth.value, platform)
        if (layout == LibraryCommandBarLayout.INLINE) {
            DesktopLibraryCommandBar(
                state = state,
                selectedTab = selectedTab,
                viewMode = viewMode,
                showFilters = showFilters,
                platform = platform,
                onViewModeChange = onViewModeChange,
                onToggleFilters = onToggleFilters,
                onStateChange = onStateChange,
                onImportBooks = onImportBooks,
                onImportFolder = onImportFolder,
                onCreateShelf = onCreateShelf
            )
        } else {
            StackedLibraryCommandBar(
                state = state,
                selectedTab = selectedTab,
                viewMode = viewMode,
                showFilters = showFilters,
                platform = platform,
                onViewModeChange = onViewModeChange,
                onToggleFilters = onToggleFilters,
                onStateChange = onStateChange,
                onImportBooks = onImportBooks,
                onImportFolder = onImportFolder,
                onCreateShelf = onCreateShelf
            )
        }
    }
}

@Composable
private fun DesktopLibraryCommandBar(
    state: SharedReaderScreenState,
    selectedTab: NonReaderLibraryTab,
    viewMode: BookViewMode,
    showFilters: Boolean,
    platform: ReaderPlatform,
    onViewModeChange: (BookViewMode) -> Unit,
    onToggleFilters: () -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onCreateShelf: () -> Unit
) {
    val showCreateShelfPrimaryAction = NonReaderLibraryPrimaryAction.NEW_SHELF in
        primaryLibraryActionsForTab(selectedTab, platform)

    Surface(
        shape = RoundedCornerShape(SharedUiTokens.surfaceRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = sharedSubtleBorder(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibrarySearchField(
                state = state,
                onStateChange = onStateChange,
                modifier = Modifier.weight(1f).widthIn(min = 260.dp)
            )
            SortMenu(
                sortOrder = state.sortOrder,
                onSortOrderChange = { onStateChange(state.reduce(LibraryAction.SortChanged(it))) }
            )
            LibraryFilterButton(
                filters = state.libraryFilters,
                showFilters = showFilters,
                onToggleFilters = onToggleFilters
            )
            Button(onClick = onImportBooks) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("desktop_import_files", "Import files"))
            }
            OutlinedButton(onClick = onImportFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("fab_add_folder", "Add folder"))
            }
            if (showCreateShelfPrimaryAction) {
                Button(onClick = onCreateShelf) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("fab_new_shelf", "New shelf"))
                }
            }
            LibraryMoreActionsMenu(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onCreateShelf = onCreateShelf,
                showCreateShelfAction = !showCreateShelfPrimaryAction
            )
        }
    }
}

@Composable
private fun StackedLibraryCommandBar(
    state: SharedReaderScreenState,
    selectedTab: NonReaderLibraryTab,
    viewMode: BookViewMode,
    showFilters: Boolean,
    platform: ReaderPlatform,
    onViewModeChange: (BookViewMode) -> Unit,
    onToggleFilters: () -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onCreateShelf: () -> Unit
) {
    val showCreateShelfPrimaryAction = NonReaderLibraryPrimaryAction.NEW_SHELF in
        primaryLibraryActionsForTab(selectedTab, platform)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibrarySearchField(
            state = state,
            onStateChange = onStateChange,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortMenu(
                sortOrder = state.sortOrder,
                onSortOrderChange = { onStateChange(state.reduce(LibraryAction.SortChanged(it))) }
            )
            LibraryFilterButton(
                filters = state.libraryFilters,
                showFilters = showFilters,
                onToggleFilters = onToggleFilters
            )
            Button(onClick = onImportBooks) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("desktop_import_files", "Import files"))
            }
            OutlinedButton(onClick = onImportFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(readerString("fab_add_folder", "Add folder"))
            }
            if (showCreateShelfPrimaryAction) {
                Button(onClick = onCreateShelf) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("fab_new_shelf", "New shelf"))
                }
            }
            LibraryMoreActionsMenu(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onCreateShelf = onCreateShelf,
                showCreateShelfAction = !showCreateShelfPrimaryAction
            )
        }
    }
}

@Composable
private fun LibrarySearchField(
    state: SharedReaderScreenState,
    onStateChange: (SharedReaderScreenState) -> Unit,
    modifier: Modifier = Modifier
) {
    SharedStableOutlinedTextField(
        value = state.searchQuery,
        onValueChange = { onStateChange(state.reduce(LibraryAction.SearchChanged(it))) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        label = { Text(readerString("library_search_placeholder", "Search books, authors, or tags")) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun LibraryFilterButton(
    filters: LibraryFilters,
    showFilters: Boolean,
    onToggleFilters: () -> Unit
) {
    OutlinedButton(onClick = onToggleFilters) {
        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (showFilters) readerString("desktop_hide_filters", "Hide filters") else readerString("filter_library", "Filters"))
        if (filters.isActive) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    filters.activeFilterBadge(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryMoreActionsMenu(
    viewMode: BookViewMode,
    onViewModeChange: (BookViewMode) -> Unit,
    onCreateShelf: () -> Unit,
    showCreateShelfAction: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = readerString("desktop_more", "More"),
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (viewMode == BookViewMode.COVERS) Icons.AutoMirrored.Filled.List else Icons.Default.Book,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        if (viewMode == BookViewMode.COVERS) {
                            readerString("desktop_list_view", "List")
                        } else {
                            readerString("desktop_cover_view", "Covers")
                        }
                    )
                },
                onClick = {
                    expanded = false
                    onViewModeChange(
                        if (viewMode == BookViewMode.COVERS) BookViewMode.LIST else BookViewMode.COVERS
                    )
                }
            )
            if (showCreateShelfAction) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    text = { Text(readerString("fab_new_shelf", "New shelf")) },
                    onClick = {
                        expanded = false
                        onCreateShelf()
                    }
                )
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
    platform: ReaderPlatform,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onTabChange: (NonReaderLibraryTab) -> Unit = {},
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    useImportEmptyStateWhenLibraryEmpty: Boolean = false,
    onCreateShelf: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onAddBooksToShelf: (Set<String>) -> Unit,
    onManageShelfBooks: ((Shelf) -> Unit)?,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit,
    onSyncFolderMetadata: () -> Unit,
    onScanFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val shelvesById = remember(state.shelves) { state.shelves.associateBy { it.id } }
        val visibleBooks = remember(state.libraryBooks, selectedTab, platform) {
            state.booksForNonReaderLibraryTab(selectedTab, platform)
        }
        val tagShelves = remember(state.shelves) {
            state.shelves.filter { it.type == ShelfType.TAG && it.bookCount > 0 }
        }
        val browseShelves = remember(state.shelves) {
            state.shelves.filter {
                it.type != ShelfType.FOLDER &&
                    it.type != ShelfType.TAG &&
                    it.type != ShelfType.SMART
            }
        }
        val smartShelves = remember(state.shelves) {
            state.shelves.filter { it.type == ShelfType.SMART }
        }
        val rootFolderShelves = remember(state.shelves) {
            state.shelves.filter { it.type == ShelfType.FOLDER && it.parentShelfId == null }
        }
        val addToShelfFromBookAction = if (NonReaderBookOverflowAction.ADD_TO_SHELF in bookOverflowActionsForPlatform(platform)) {
            onAddBooksToShelf
        } else {
            null
        }
        val manageShelfBooksAction = if (platform == ReaderPlatform.DESKTOP) onManageShelfBooks else null
        val showNewShelfPrimaryAction = NonReaderLibraryPrimaryAction.NEW_SHELF in
            primaryLibraryActionsForTab(selectedTab, platform)
        if (showFilters) {
            LibraryFilterPanel(
                state = state,
                platform = platform,
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
                val books = visibleBooks
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
                        onAddToShelf = addToShelfFromBookAction?.let { addToShelf -> { book -> addToShelf(setOf(book.id)) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            NonReaderLibraryTab.SHELVES -> {
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
                        shelves = browseShelves,
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        onTogglePinned = onTogglePinned,
                        onAddBooksToShelf = addToShelfFromBookAction,
                        onManageShelfBooks = manageShelfBooksAction,
                        onRenameShelf = onRenameShelf,
                        onDeleteShelf = onDeleteShelf,
                        onRemoveFolder = onRemoveFolder,
                        onCreateShelf = if (showNewShelfPrimaryAction) onCreateShelf else null,
                        emptyTitle = readerString("desktop_no_shelves_yet", "No shelves yet"),
                        emptyBody = readerString("desktop_no_shelves_desc", "Manual shelves and series collections will appear here."),
                        emptyActionLabel = if (showNewShelfPrimaryAction) readerString("fab_new_shelf", "New shelf") else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            NonReaderLibraryTab.SMART_SHELVES -> ShelfCollection(
                shelves = smartShelves,
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                onAddBooksToShelf = addToShelfFromBookAction,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
                emptyTitle = readerString("desktop_no_smart_shelves_yet", "No smart shelves yet"),
                emptyBody = readerString("desktop_no_smart_shelves_desc", "Create smart shelves to collect books by rules."),
                modifier = Modifier.weight(1f)
            )

            NonReaderLibraryTab.TAGS -> ShelfCollection(
                shelves = tagShelves,
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                onOpenBook = onOpenBook,
                onToggleSelection = onToggleSelection,
                onShowBookInfo = onShowBookInfo,
                onEditBook = onEditBook,
                onTogglePinned = onTogglePinned,
                onAddBooksToShelf = addToShelfFromBookAction,
                emptyTitle = readerString("desktop_no_tags_yet", "No tags yet"),
                emptyBody = readerString("desktop_no_tags_desc", "Tags added to books will appear here."),
                modifier = Modifier.weight(1f)
            )

            NonReaderLibraryTab.FOLDERS -> {
                val currentFolder = state.viewingShelfId
                    ?.let { id -> shelvesById[id]?.takeIf { it.type == ShelfType.FOLDER } }
                if (currentFolder != null) {
                    FolderShelfDetail(
                        shelf = currentFolder,
                        childShelves = currentFolder.childShelfIds.mapNotNull { childId ->
                            shelvesById[childId]
                        },
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        onOpenBook = onOpenBook,
                        onToggleSelection = onToggleSelection,
                        onShowBookInfo = onShowBookInfo,
                        onEditBook = onEditBook,
                        onTogglePinned = onTogglePinned,
                        onAddBooksToShelf = addToShelfFromBookAction,
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
                            shelves = rootFolderShelves,
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedLibraryBookIds,
                            onOpenBook = onOpenBook,
                            onToggleSelection = onToggleSelection,
                            onShowBookInfo = onShowBookInfo,
                            onEditBook = onEditBook,
                            onTogglePinned = onTogglePinned,
                            onAddBooksToShelf = addToShelfFromBookAction,
                            onRemoveFolder = onRemoveFolder,
                            onOpenShelf = { shelf -> onStateChange(state.copy(viewingShelfId = shelf.id)) },
                            emptyTitle = readerString("desktop_no_folders_yet", "No folders yet"),
                            emptyBody = readerString("desktop_no_folders_desc", "Add a folder to read files from that folder in place."),
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
            Text(readerString("desktop_sync_metadata", "Sync metadata"))
        }
        Button(onClick = onScanFolders) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(readerString("desktop_full_scan", "Full scan"))
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
                label = { Text(readerString("desktop_search_filter_format", "Search: %1\$s", state.searchQuery)) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = readerString("tooltip_clear_search", "Clear search"), modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.fileTypes.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(fileTypes = emptySet())))) },
                label = {
                    val fileTypes = state.libraryFilters.fileTypes
                                .sortedBy { it.ordinal }
                                .joinToString { SharedFileCapabilities.displayNameFor(it) }
                    Text(readerString("filter_types", "Types: %1\$s", fileTypes))
                },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_file_types", "Clear file types"), modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.sourceFolders.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(sourceFolders = emptySet())))) },
                label = { Text(readerString("filter_folders", "Folders: %1\$d", state.libraryFilters.sourceFolders.size)) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_sources", "Clear sources"), modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.readStatus != ReadStatusFilter.ALL) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(readStatus = ReadStatusFilter.ALL)))) },
                label = { Text(readerString("filter_status", "Status: %1\$s", state.libraryFilters.readStatus.label())) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_status", "Clear status"), modifier = Modifier.size(16.dp)) }
            )
        }
        if (state.libraryFilters.tagIds.isNotEmpty()) {
            AssistChip(
                onClick = { onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(tagIds = emptySet())))) },
                label = { Text(readerString("filter_tags", "Tags: %1\$s", state.libraryFilters.tagIds.size.toString())) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = readerString("desktop_clear_tags", "Clear tags"), modifier = Modifier.size(16.dp)) }
            )
        }
        TextButton(onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters()))) }) {
            Text(readerString("clear_all", "Clear all"))
        }
    }
}

@Composable
private fun LibraryFilterPanel(
    state: SharedReaderScreenState,
    platform: ReaderPlatform,
    onStateChange: (SharedReaderScreenState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(SharedUiTokens.surfaceRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = sharedSubtleBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(readerString("filter_library", "Filters"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.libraryFilters.isActive || state.searchQuery.isNotBlank()) {
                    TextButton(onClick = { onStateChange(state.reduce(LibraryAction.SearchChanged("")).reduce(LibraryAction.FiltersChanged(LibraryFilters()))) }) {
                        Text(readerString("action_clear", "Clear"))
                    }
                }
            }

            LibraryFilterSection(title = readerString("filter_file_type", "File type")) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    nonReaderLibraryFileTypeGroups(platform).flatMap { it.fileTypes }.forEach { type ->
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

            LibraryFilterSection(title = readerString("filter_source_folder", "Source folder")) {
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
                        label = { Text(readerString("source_in_app", "In-app")) }
                    )
                    state.syncedFolders.forEach { folder ->
                        FilterChip(
                            selected = folder.uriString in state.libraryFilters.sourceFolders,
                            onClick = {
                                val updated = state.libraryFilters.sourceFolders.toggle(folder.uriString)
                                onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(sourceFolders = updated))))
                            },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = {
                                Text(
                                    folder.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                            }
                        )
                    }
                }
            }

            LibraryFilterSection(title = readerString("filter_read_status", "Read status")) {
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
                            label = { Text(status.label()) }
                        )
                    }
                }
            }

            if (state.allTags.isNotEmpty()) {
                LibraryFilterSection(title = readerString("section_tags", "Tags")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.allTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in state.libraryFilters.tagIds,
                                onClick = {
                                    val updated = state.libraryFilters.tagIds.toggle(tag.id)
                                    onStateChange(state.reduce(LibraryAction.FiltersChanged(state.libraryFilters.copy(tagIds = updated))))
                                },
                                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = {
                                    Text(
                                        tag.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
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
    onAddToShelf: ((BookItem) -> Unit)? = null,
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
                    onTogglePinned = { onTogglePinned(book) },
                    onAddToShelf = onAddToShelf?.let { addToShelf -> { addToShelf(book) } }
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    onTogglePinned = { onTogglePinned(book) },
                    onAddToShelf = onAddToShelf?.let { addToShelf -> { addToShelf(book) } }
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
    onAddToShelf: (() -> Unit)? = null,
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
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (pinned) {
                        OverlayBadge(Icons.Default.PushPin, readerString("pinned", "Pinned"))
                    }
                    if (book.sourceFolder != null) {
                        OverlayBadge(Icons.Default.Folder, readerString("desktop_book_badge_folder", "Folder"))
                    }
                    if (book.isOpdsStream()) {
                        OverlayBadge(Icons.Default.Cloud, readerString("action_stream", "Stream"))
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_book_actions", "Book actions"))
                    }
                    BookActionMenu(
                        expanded = menuExpanded,
                        pinned = pinned,
                        selected = selected,
                        onDismiss = { menuExpanded = false },
                        onTogglePinned = onTogglePinned,
                        onShowInfo = onShowInfo,
                        onEdit = onEdit,
                        onToggleSelection = onToggleSelection,
                        onAddToShelf = onAddToShelf
                    )
                }
                TypeBadge(book.type, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                val percent = progressPercentValue(book.progressPercentage)
                if (percent > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    onTogglePinned: () -> Unit,
    onAddToShelf: (() -> Unit)? = null
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
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCoverArt(book = book, selected = selected, modifier = Modifier.size(width = 52.dp, height = 76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(book.type)
                    if (pinned) StatusBadge(Icons.Default.PushPin, readerString("pinned", "Pinned"))
                    if (book.sourceFolder != null) StatusBadge(Icons.Default.Folder, readerString("desktop_book_badge_folder", "Folder"))
                    if (book.isOpdsStream()) StatusBadge(Icons.Default.Cloud, readerString("action_stream", "Stream"))
                }
                ProgressSection(book.progressPercentage)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_book_actions", "Book actions"))
                }
                BookActionMenu(
                    expanded = menuExpanded,
                    pinned = pinned,
                    selected = selected,
                    onDismiss = { menuExpanded = false },
                    onTogglePinned = onTogglePinned,
                    onShowInfo = onShowInfo,
                    onEdit = onEdit,
                    onToggleSelection = onToggleSelection,
                    onAddToShelf = onAddToShelf
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
    onToggleSelection: () -> Unit,
    onAddToShelf: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
            text = { Text(if (pinned) readerString("desktop_unpin", "Unpin") else readerString("desktop_pin", "Pin")) },
            onClick = {
                onDismiss()
                onTogglePinned()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            text = { Text(readerString("info", "Info")) },
            onClick = {
                onDismiss()
                onShowInfo()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text(readerString("action_edit", "Edit")) },
            onClick = {
                onDismiss()
                onEdit()
            }
        )
        if (onAddToShelf != null) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                text = { Text(readerString("desktop_add_to_shelf", "Add to shelf")) },
                onClick = {
                    onDismiss()
                    onAddToShelf()
                }
            )
        }
        DropdownMenuItem(
            leadingIcon = { Icon(if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.List, contentDescription = null) },
            text = { Text(if (selected) readerString("clear_selection", "Clear selection") else readerString("action_select", "Select")) },
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
            Text(readerString("desktop_progress", "Progress"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            readerString("section_browse_by_tag", "Browse by tag"),
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
    onAddBooksToShelf: ((Set<String>) -> Unit)? = null,
    onManageShelfBooks: ((Shelf) -> Unit)? = null,
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onRemoveFolder: (Shelf) -> Unit = {},
    onOpenShelf: ((Shelf) -> Unit)? = null,
    onCreateShelf: (() -> Unit)? = null,
    emptyTitle: String,
    emptyBody: String,
    emptyActionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedEmptyState(
            icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = emptyTitle,
            body = emptyBody,
            actionLabel = emptyActionLabel,
            onAction = onCreateShelf,
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
                onAddBooksToShelf = onAddBooksToShelf,
                onManageShelfBooks = onManageShelfBooks,
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
    onAddBooksToShelf: ((Set<String>) -> Unit)?,
    onManageShelfBooks: ((Shelf) -> Unit)?,
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
                            contentDescription = readerString("desktop_open_folder", "Open folder"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                    if (onManageShelfBooks != null) {
                        OutlinedButton(onClick = { onManageShelfBooks(shelf) }) {
                            Icon(
                                if (shelf.bookCount == 0) Icons.Default.Add else Icons.Default.FormatListNumbered,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (shelf.bookCount == 0) {
                                    readerString("fab_add_books", "Add books")
                                } else {
                                    readerString("desktop_manage_books", "Manage books")
                                }
                            )
                        }
                    }
                    IconButton(onClick = { onRenameShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = readerString("menu_rename_shelf", "Rename shelf"), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDeleteShelf(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = readerString("menu_delete_shelf", "Delete shelf"), modifier = Modifier.size(18.dp))
                    }
                } else if (shelf.type == ShelfType.FOLDER && shelf.parentShelfId == null) {
                    IconButton(onClick = { onRemoveFolder(shelf) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = readerString("menu_remove_folder", "Remove folder"), modifier = Modifier.size(18.dp))
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
                            onAddToShelf = onAddBooksToShelf?.let { addToShelf -> { addToShelf(setOf(book.id)) } },
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
    onAddBooksToShelf: ((Set<String>) -> Unit)? = null,
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("action_back", "Back"))
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
                title = readerString("desktop_folder_empty", "Folder is empty"),
                body = readerString("desktop_folder_empty_desc", "No supported files or subfolders are available here."),
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
                        SectionLabel(readerString("section_folders", "Folders"))
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
                        SectionLabel(readerString("section_files", "Files"))
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
                            onTogglePinned = { onTogglePinned(book) },
                            onAddToShelf = onAddBooksToShelf?.let { addToShelf -> { addToShelf(setOf(book.id)) } }
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
                contentDescription = readerString("desktop_open_folder", "Open folder"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Shelf.subtitleLabel(): String {
    if (type != ShelfType.FOLDER) return bookCountLabel(bookCount)
    return when {
        childShelfCount > 0 && directBookCount > 0 -> readerString(
            "desktop_folder_subtitle_folder_file_counts",
            "%1\$s, %2\$s",
            folderCountLabel(childShelfCount),
            fileCountLabel(directBookCount)
        )
        childShelfCount > 0 -> folderCountLabel(childShelfCount)
        directBookCount > 0 -> fileCountLabel(directBookCount)
        else -> bookCountLabel(bookCount)
    }
}

@Composable
private fun bookCountLabel(count: Int): String {
    return readerQuantityString("book_count", count, "%1\$d book", "%1\$d books", count)
}

@Composable
private fun folderCountLabel(count: Int): String {
    return readerQuantityString("folder_count", count, "%1\$d folder", "%1\$d folders", count)
}

@Composable
private fun fileCountLabel(count: Int): String {
    return readerQuantityString("file_count", count, "%1\$d file", "%1\$d files", count)
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
            Text(sortOrder.label())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label()) },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                    trailingIcon = if (sortOrder == order) {
                        { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected")) }
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
        title = readerString("your_library_empty", "Your library is empty"),
        body = readerString("desktop_library_empty_desc", "Import files into app storage or add a folder to read files in place."),
        actionLabel = readerString("desktop_import_files", "Import files"),
        onAction = onImportBooks,
        secondaryActionLabel = readerString("fab_add_folder", "Add folder"),
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

@Composable
private fun NonReaderLibraryTab.label(): String {
    return when (this) {
        NonReaderLibraryTab.BOOKS -> readerString("tab_all_books", "All Books")
        NonReaderLibraryTab.SHELVES -> readerString("tab_shelves", "Shelves")
        NonReaderLibraryTab.SMART_SHELVES -> readerString("desktop_smart_shelves", "Smart")
        NonReaderLibraryTab.TAGS -> readerString("section_tags", "Tags")
        NonReaderLibraryTab.FOLDERS -> readerString("tab_folders", "Folders")
        NonReaderLibraryTab.UNREAD -> readerString("read_status_unread", "Unread")
        NonReaderLibraryTab.IN_PROGRESS -> readerString("read_status_in_progress", "In progress")
        NonReaderLibraryTab.COMPLETED -> readerString("read_status_completed", "Complete")
    }
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

@Composable
private fun NonReaderLibraryTab.labelWithCount(organization: NonReaderLibraryOrganizationModel): String {
    val count = count(organization)
    return when (this) {
        NonReaderLibraryTab.BOOKS -> readerQuantityString(
            "desktop_library_tab_books_count",
            count,
            "All Books %1\$d",
            "All Books %1\$d",
            count
        )
        NonReaderLibraryTab.SHELVES -> readerQuantityString(
            "desktop_library_tab_shelves_count",
            count,
            "Shelves %1\$d",
            "Shelves %1\$d",
            count
        )
        NonReaderLibraryTab.SMART_SHELVES -> readerString("desktop_library_tab_smart_shelves_count", "Smart %1\$d", count)
        NonReaderLibraryTab.TAGS -> readerQuantityString(
            "desktop_library_tab_tags_count",
            count,
            "Tags %1\$d",
            "Tags %1\$d",
            count
        )
        NonReaderLibraryTab.FOLDERS -> readerQuantityString(
            "desktop_library_tab_folders_count",
            count,
            "Folders %1\$d",
            "Folders %1\$d",
            count
        )
        NonReaderLibraryTab.UNREAD -> readerString("desktop_library_tab_unread_count", "Unread %1\$d", count)
        NonReaderLibraryTab.IN_PROGRESS -> readerString("desktop_library_tab_in_progress_count", "In progress %1\$d", count)
        NonReaderLibraryTab.COMPLETED -> readerString("desktop_library_tab_completed_count", "Complete %1\$d", count)
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

@Composable
private fun SortOrder.label(): String {
    return when (this) {
        SortOrder.RECENT -> readerString("sort_recent", "Recent")
        SortOrder.TITLE_ASC -> readerString("sort_title_az", "Title A-Z")
        SortOrder.AUTHOR_ASC -> readerString("sort_author_az", "Author A-Z")
        SortOrder.PERCENT_ASC -> readerString("sort_percent_asc", "Progress low")
        SortOrder.PERCENT_DESC -> readerString("sort_percent_desc", "Progress high")
        SortOrder.SIZE_ASC -> readerString("sort_size_smallest", "Size small")
        SortOrder.SIZE_DESC -> readerString("sort_size_biggest", "Size large")
    }
}

@Composable
private fun ReadStatusFilter.label(): String {
    return when (this) {
        ReadStatusFilter.ALL -> readerString("filter_all", "All")
        ReadStatusFilter.UNREAD -> readerString("read_status_unread", "Unread")
        ReadStatusFilter.IN_PROGRESS -> readerString("read_status_in_progress", "In progress")
        ReadStatusFilter.COMPLETED -> readerString("read_status_completed", "Complete")
    }
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
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT -> Color(0xFF705D49)
        else -> Color(0xFF5D6B82)
    }
}
