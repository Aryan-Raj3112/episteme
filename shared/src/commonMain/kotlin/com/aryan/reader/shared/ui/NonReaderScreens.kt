package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.isOpdsStream
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

internal enum class BookViewMode {
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
    onExportAnnotations: (BookItem) -> Unit = {},
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onSaveOriginalFile: (BookItem) -> Unit = {},
    onShareOriginalFile: (BookItem) -> Unit = {},
    onTagSelectedBooks: () -> Unit = {},
    onAddSelectedBooksToShelf: () -> Unit = {},
    onOpenTab: (BookItem) -> Unit = onOpenBook,
    onCloseTab: (BookItem) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onRecentLimitChange: (Int) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    platform: ReaderPlatform = ReaderPlatform.ANDROID,
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
    val saveOriginalFileAction = if (NonReaderBookOverflowAction.SAVE_ORIGINAL in bookOverflowActionsForPlatform(platform)) {
        onSaveOriginalFile
    } else {
        null
    }
    val shareOriginalFileAction = if (NonReaderBookOverflowAction.SHARE_ORIGINAL in bookOverflowActionsForPlatform(platform)) {
        onShareOriginalFile
    } else {
        null
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
                onExportAnnotations = selectedBooks.singleOrNull()?.let { book -> { onExportAnnotations(book) } },
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
                            onSaveOriginalFile = saveOriginalFileAction?.let { save -> { save(book) } },
                            onShareOriginalFile = shareOriginalFileAction?.let { share -> { share(book) } },
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
                            onSaveOriginalFile = saveOriginalFileAction,
                            onShareOriginalFile = shareOriginalFileAction,
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
                            onSaveOriginalFile = saveOriginalFileAction,
                            onShareOriginalFile = shareOriginalFileAction,
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
    onExportAnnotations: (BookItem) -> Unit = {},
    onShowBookInfo: (BookItem) -> Unit = {},
    onEditBook: (BookItem) -> Unit = {},
    onSaveOriginalFile: (BookItem) -> Unit = {},
    onShareOriginalFile: (BookItem) -> Unit = {},
    onCreateShelf: () -> Unit = {},
    onCreateShelfWithBooks: (String, Set<String>) -> Unit = { _, _ -> },
    onCreateSmartShelf: () -> Unit = {},
    onRenameShelf: (Shelf) -> Unit = {},
    onDeleteShelf: (Shelf) -> Unit = {},
    onDeleteTag: (Shelf) -> Unit = {},
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
                onExportAnnotations = selectedBooks.singleOrNull()?.let { book -> { onExportAnnotations(book) } },
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
                                onSaveOriginalFile = onSaveOriginalFile,
                                onShareOriginalFile = onShareOriginalFile,
                                onTogglePinned = onTogglePinned,
                                onAddBooksToShelf = onAddBooksToShelf,
                                onManageShelfBooks = onManageShelfBooks,
                                onRenameShelf = onRenameShelf,
                                onDeleteShelf = onDeleteShelf,
                                onDeleteTag = onDeleteTag,
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
                            onSaveOriginalFile = onSaveOriginalFile,
                            onShareOriginalFile = onShareOriginalFile,
                            onTogglePinned = onTogglePinned,
                            onAddBooksToShelf = onAddBooksToShelf,
                            onManageShelfBooks = onManageShelfBooks,
                            onRenameShelf = onRenameShelf,
                            onDeleteShelf = onDeleteShelf,
                            onDeleteTag = onDeleteTag,
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
    onDeleteTag: (Shelf) -> Unit = {},
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
            onDeleteTag = onDeleteTag,
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
    onSaveOriginalFile: (() -> Unit)?,
    onShareOriginalFile: (() -> Unit)?,
    onTogglePinned: () -> Unit
) {
    val canUseOriginalFileActions = !book.isOpdsStream() && book.path != null
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
                    if (canUseOriginalFileActions && onSaveOriginalFile != null) {
                        IconButton(onClick = onSaveOriginalFile) {
                            Icon(Icons.Default.Save, contentDescription = readerString("action_save_copy_to_device", "Save copy to device"))
                        }
                    }
                    if (canUseOriginalFileActions && onShareOriginalFile != null) {
                        IconButton(onClick = onShareOriginalFile) {
                            Icon(Icons.Default.Share, contentDescription = readerString("action_share", "Share"))
                        }
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
    onSaveOriginalFile: ((BookItem) -> Unit)?,
    onShareOriginalFile: ((BookItem) -> Unit)?,
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
                    onSaveOriginalFile = onSaveOriginalFile?.let { save -> { save(book) } },
                    onShareOriginalFile = onShareOriginalFile?.let { share -> { share(book) } },
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
    onInfo: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null
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
                onExportAnnotations?.let { export ->
                    var exportMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { exportMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = readerString("desktop_more", "More"), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                text = { Text(readerString("action_export_annotations", "Export annotations")) },
                                onClick = {
                                    exportMenuExpanded = false
                                    export()
                                }
                            )
                        }
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
    onSaveOriginalFile: (BookItem) -> Unit,
    onShareOriginalFile: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onAddBooksToShelf: (Set<String>) -> Unit,
    onManageShelfBooks: ((Shelf) -> Unit)?,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onDeleteTag: (Shelf) -> Unit,
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
        val saveOriginalFileAction = if (NonReaderBookOverflowAction.SAVE_ORIGINAL in bookOverflowActionsForPlatform(platform)) {
            onSaveOriginalFile
        } else {
            null
        }
        val shareOriginalFileAction = if (NonReaderBookOverflowAction.SHARE_ORIGINAL in bookOverflowActionsForPlatform(platform)) {
            onShareOriginalFile
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
                        onSaveOriginalFile = saveOriginalFileAction,
                        onShareOriginalFile = shareOriginalFileAction,
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
                        onSaveOriginalFile = saveOriginalFileAction,
                        onShareOriginalFile = shareOriginalFileAction,
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
                onSaveOriginalFile = saveOriginalFileAction,
                onShareOriginalFile = shareOriginalFileAction,
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
                onSaveOriginalFile = saveOriginalFileAction,
                onShareOriginalFile = shareOriginalFileAction,
                onTogglePinned = onTogglePinned,
                onAddBooksToShelf = addToShelfFromBookAction,
                onDeleteTag = onDeleteTag,
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
                        onSaveOriginalFile = saveOriginalFileAction,
                        onShareOriginalFile = shareOriginalFileAction,
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
                            onSaveOriginalFile = saveOriginalFileAction,
                            onShareOriginalFile = shareOriginalFileAction,
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
