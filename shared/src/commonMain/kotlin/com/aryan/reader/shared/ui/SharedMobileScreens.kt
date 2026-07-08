package com.aryan.reader.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsScreenState

@Composable
fun SharedMobileAppDrawerContent(
    currentUser: UserData?,
    isProUser: Boolean,
    credits: Int,
    isSyncEnabled: Boolean,
    isFolderSyncEnabled: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onFolderSyncToggle: (Boolean) -> Unit,
    onProClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppThemeClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentUser != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currentUser.displayName ?: currentUser.email ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    currentUser.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("$credits credits", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Sign in with Google") },
                    selected = false,
                    onClick = onSignInClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = "Sync account, Pro features, and credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                label = { Text(if (isProUser) "Pro unlocked" else "Upgrade to Pro") },
                selected = false,
                onClick = onProClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                label = { Text("Sync library") },
                selected = false,
                onClick = { onSyncToggle(!isSyncEnabled) },
                badge = { Switch(checked = isSyncEnabled, onCheckedChange = onSyncToggle) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (isSyncEnabled) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                    label = {
                        Column {
                            Text("Backup local folders")
                            Text(
                                "Keep folder metadata synced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = false,
                    onClick = { onFolderSyncToggle(!isFolderSyncEnabled) },
                    badge = { Switch(checked = isFolderSyncEnabled, onCheckedChange = onFolderSyncToggle) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                label = { Text("App theme") },
                selected = false,
                onClick = onAppThemeClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                label = { Text("Custom fonts") },
                selected = false,
                onClick = onFontsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Ai, contentDescription = null) },
                label = { Text("AI settings") },
                selected = false,
                onClick = onAiSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                label = { Text("Help & Feedback") },
                selected = false,
                onClick = onFeedbackClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (currentUser != null) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    label = { Text("Sign out") },
                    selected = false,
                    onClick = onSignOutClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "Privacy Policy  •  Terms  •  Licenses",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun SharedMobileHomeScreen(
    state: SharedReaderScreenState,
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onOpenTab: (BookItem) -> Unit = onOpenBook,
    onCloseTab: (BookItem) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
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

    if (model.isEmpty) {
        SharedMobileEmptyLibrary(
            title = if (model.isLibraryEmpty) "Your library is empty" else "No recent files",
            message = if (model.isLibraryEmpty) {
                "Select a PDF, EPUB, comic, or document to start reading."
            } else {
                "Open books from the library and they will appear here."
            },
            actionLabel = "Select file",
            onAction = onImportBooks,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.isTabsEnabled && model.activeTabs.isNotEmpty()) {
                item(key = "tabs") {
                    SharedMobileActiveTabs(
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
                    SharedMobileBookGridSection(
                        title = "Pinned",
                        books = model.pinnedBooks,
                        selectedBookIds = selectedIds,
                        pinnedBookIds = state.pinnedHomeBookIds,
                        onOpenBook = onOpenBook,
                        onLongPressBook = onLongPressBook,
                        onTogglePinned = onTogglePinned
                    )
                }
            }

            item(key = "recent") {
                SharedMobileBookGridSection(
                    title = "Recent files",
                    books = model.recentBooks,
                    selectedBookIds = selectedIds,
                    pinnedBookIds = state.pinnedHomeBookIds,
                    onOpenBook = onOpenBook,
                    onLongPressBook = onLongPressBook,
                    onTogglePinned = onTogglePinned
                )
            }
        }
    }
}

@Composable
fun SharedMobileLibraryScreen(
    state: SharedReaderScreenState,
    selectedTab: SharedMobileLibraryTab,
    onTabChange: (SharedMobileLibraryTab) -> Unit,
    opdsState: SharedOpdsScreenState,
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onOpenShelf: (Shelf) -> Unit = {},
    onLongPressShelf: (Shelf) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onOpenCatalog: (OpdsCatalog) -> Unit = {},
    onOpenFeedUrl: (String) -> Unit = {},
    onOpdsNavigateBack: () -> Unit = {},
    onOpdsSearch: (String) -> Unit = {},
    onOpdsLoadNextPage: () -> Unit = {},
    onAddCatalog: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    onUpdateCatalog: (String, String, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onRemoveCatalog: (OpdsCatalog) -> Unit = {},
    onDownloadOpdsBook: (OpdsEntry, OpdsAcquisition) -> Unit = { _, _ -> },
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit = { _, _ -> },
    onClearOpdsError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            SharedMobileLibraryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.label) }
                )
            }
        }

        when (selectedTab) {
            SharedMobileLibraryTab.BOOKS -> SharedMobileBookList(
                books = state.libraryBooks,
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                onOpenBook = onOpenBook,
                onLongPressBook = onLongPressBook,
                onTogglePinned = onTogglePinned,
                empty = {
                    SharedMobileEmptyLibrary(
                        title = "Your library is empty",
                        message = "Select a PDF, EPUB, comic, or document to start reading.",
                        actionLabel = "Select file",
                        onAction = onImportBooks,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            SharedMobileLibraryTab.SHELVES -> SharedMobileShelfList(
                shelves = state.shelves.filter { it.type != ShelfType.FOLDER && it.type != ShelfType.TAG },
                onOpenShelf = onOpenShelf,
                onLongPressShelf = onLongPressShelf,
                modifier = Modifier.fillMaxSize()
            )

            SharedMobileLibraryTab.FOLDERS -> SharedMobileShelfList(
                shelves = state.shelves.filter { it.type == ShelfType.FOLDER },
                onOpenShelf = onOpenShelf,
                onLongPressShelf = onLongPressShelf,
                emptyTitle = "No folders yet",
                emptyMessage = "Folder sync is not connected on iOS yet.",
                modifier = Modifier.fillMaxSize()
            )

            SharedMobileLibraryTab.CATALOGS -> SharedOpdsScreen(
                state = opdsState,
                localLibraryBooks = state.rawLibraryBooks,
                onOpenCatalog = onOpenCatalog,
                onOpenFeedUrl = onOpenFeedUrl,
                onNavigateBack = onOpdsNavigateBack,
                onSearch = onOpdsSearch,
                onLoadNextPage = onOpdsLoadNextPage,
                onAddCatalog = onAddCatalog,
                onUpdateCatalog = onUpdateCatalog,
                onRemoveCatalog = onRemoveCatalog,
                onDownloadBook = onDownloadOpdsBook,
                onReadBook = onOpenBook,
                onStreamBook = onStreamOpdsBook,
                onClearError = onClearOpdsError,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

enum class SharedMobileLibraryTab(val label: String) {
    BOOKS("Books"),
    SHELVES("Shelves"),
    FOLDERS("Folders"),
    CATALOGS("Catalogs")
}

@Composable
private fun SharedMobileActiveTabs(
    openTabs: List<BookItem>,
    activeBookId: String?,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active tabs", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onCloseAllTabs) {
                Icon(Icons.Default.Close, contentDescription = "Close all tabs", tint = MaterialTheme.colorScheme.error)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(openTabs, key = { "tab_${it.id}" }) { tab ->
                InputChip(
                    selected = tab.id == activeBookId,
                    onClick = { onOpenTab(tab) },
                    label = {
                        Text(
                            text = tab.cardTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onCloseTab(tab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedMobileBookGridSection(
    title: String,
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.height((((books.size + 2) / 3).coerceAtLeast(1) * 244).dp)
        ) {
            items(books, key = { it.id }) { book ->
                SharedMobileBookCard(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    onClick = { onOpenBook(book) },
                    onLongClick = { onLongPressBook(book) },
                    onTogglePinned = { onTogglePinned(book) }
                )
            }
        }
    }
}

@Composable
private fun SharedMobileBookList(
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        empty()
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            SharedMobileLibraryListItem(
                book = book,
                selected = book.id in selectedBookIds,
                pinned = book.id in pinnedBookIds,
                onClick = { onOpenBook(book) },
                onLongClick = { onLongPressBook(book) },
                onTogglePinned = { onTogglePinned(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileBookCard(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SharedMobileBookCover(
                book = book,
                selected = selected,
                pinned = pinned,
                onTogglePinned = onTogglePinned,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = book.cardTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.cardAuthor().ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileLibraryListItem(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SharedMobileBookCover(
                book = book,
                selected = selected,
                pinned = pinned,
                onTogglePinned = onTogglePinned,
                modifier = Modifier.size(width = 52.dp, height = 76.dp),
                compact = true
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.cardAuthor(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.type.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            book.progressPercentage?.takeIf { it > 0f }?.coerceIn(0f, 100f)?.toInt()?.let { progress ->
                Text("$progress%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedMobileBookCover(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = fileTypeColor(book.type)
    Surface(
        modifier = modifier,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(if (compact) 24.dp else 38.dp))
            Text(
                text = book.type.name,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            )
            if (pinned) {
                IconButton(
                    onClick = onTogglePinned,
                    modifier = Modifier.align(Alignment.TopStart).size(if (compact) 28.dp else 36.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.48f), contentColor = Color.White) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.padding(5.dp).size(if (compact) 12.dp else 15.dp))
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier
                            .size(if (compact) 32.dp else 48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMobileShelfList(
    shelves: List<Shelf>,
    onOpenShelf: (Shelf) -> Unit,
    onLongPressShelf: (Shelf) -> Unit,
    emptyTitle: String = "No shelves yet",
    emptyMessage: String = "Create shelves to organize your library.",
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedMobileEmptyLibrary(
            title = emptyTitle,
            message = emptyMessage,
            actionLabel = null,
            onAction = {},
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            SharedMobileShelfRow(
                shelf = shelf,
                onClick = { onOpenShelf(shelf) },
                onLongClick = { onLongPressShelf(shelf) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileShelfRow(
    shelf: Shelf,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${shelf.bookCount} books", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedMobileEmptyLibrary(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) {
                Button(onClick = onAction) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFFE53935)
        FileType.EPUB, FileType.MOBI, FileType.FB2 -> Color(0xFF1E88E5)
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT -> Color(0xFF8E24AA)
        FileType.DOCX, FileType.ODT, FileType.FODT -> Color(0xFF3949AB)
        FileType.MD, FileType.TXT, FileType.HTML -> Color(0xFF00897B)
        FileType.PPTX -> Color(0xFFF4511E)
        FileType.UNKNOWN -> Color(0xFF757575)
    }
}
