/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.content.Context
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

private fun getBookCountString(count: Int): String {
    return if (count == 1) "1 book" else "$count books"
}

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val isContextualModeActive = selectedItems.isNotEmpty()
    val selectedShelves = uiState.contextualActionShelfNames
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    val sortOrder = uiState.sortOrder
    val shelves = uiState.shelves
    val pagerState = rememberPagerState(
        initialPage = uiState.libraryScreenStartPage,
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery = uiState.searchQuery

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.setSyncedFolder(it)
        }
    }
    val onSelectSyncFolderClick = { pickFolderLauncher.launch(null) }

    val pickFileLauncher = rememberFilePickerLauncher { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        uris.forEach { uri ->
            viewModel.onFileSelected(uri, isFromRecent = false)
        }
    }

    val onSelectFileClick = {
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setLibraryScreenPage(pagerState.currentPage)
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteShelvesDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }

    BackHandler(enabled = isContextualModeActive) {
        viewModel.clearContextualAction()
    }

    BackHandler(enabled = isShelfContextualModeActive) {
        viewModel.clearShelfContextualAction()
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.onSearchQueryChange("")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryScreenContent(
            recentFiles = uiState.recentFiles,
            shelves = shelves,
            selectedItems = selectedItems,
            selectedShelves = selectedShelves,
            sortOrder = sortOrder,
            pagerState = pagerState,
            scope = scope,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchActiveChange = { active ->
                isSearchActive = active
                if (!active) viewModel.onSearchQueryChange("")
            },
            onSortOrderChange = viewModel::setSortOrder,
            onClearSelection = { viewModel.clearContextualAction() },
            onItemClick = viewModel::onRecentFileClicked,
            onItemLongClick = viewModel::onRecentItemLongPress,
            onInfoClick = {
                if (selectedItems.size == 1) {
                    itemForInfoDialog = selectedItems.first()
                    showInfoDialog = true
                }
            },
            onDeleteClick = { showDeleteConfirmDialog = true },
            onSelectAllClick = { viewModel.selectAllLibraryFiles() },
            onShelfClick = viewModel::onShelfClick,
            onShelfLongClick = viewModel::onShelfLongPress,
            onClearShelfSelection = viewModel::clearShelfContextualAction,
            onDeleteShelves = { showDeleteShelvesDialog = true },
            onNewShelfClick = viewModel::showCreateShelfDialog,
            onSelectFileClick = onSelectFileClick,
            onScanNowClick = viewModel::scanSyncedFolder,
            onSelectSyncFolderClick = onSelectSyncFolderClick,
            onDisconnectSyncFolderClick = viewModel::disconnectSyncedFolder,
            downloadingBookIds = uiState.downloadingBookIds,
            syncedFolderUri = uiState.syncedFolderUri,
            lastFolderScanTime = uiState.lastFolderScanTime,
            isLoading = uiState.isLoading
        )


        if (uiState.showCreateShelfDialog) {
            CreateShelfDialog(
                onConfirm = viewModel::createShelf,
                onDismiss = viewModel::dismissCreateShelfDialog
            )
        }

        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                count = selectedItems.size,
                onConfirm = {
                    viewModel.deleteContextualItemsPermanently()
                    showDeleteConfirmDialog = false
                },
                onDismiss = { showDeleteConfirmDialog = false },
                isPermanentDelete = true
            )
        }
        if (showDeleteShelvesDialog) {
            DeleteShelvesConfirmationDialog(
                count = selectedShelves.size,
                onConfirm = {
                    viewModel.deleteSelectedShelves()
                    showDeleteShelvesDialog = false
                },
                onDismiss = { showDeleteShelvesDialog = false }
            )
        }

        itemForInfoDialog?.let { item ->
            if (showInfoDialog) {
                FileInfoDialog(
                    item = item,
                    onDismiss = {
                        showInfoDialog = false
                        itemForInfoDialog = null
                    }
                )
            }
        }
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@Composable
fun ShelfScreen(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val viewingShelfName = uiState.viewingShelfName
    val isAddingBooks = uiState.isAddingBooksToShelf
    val shelves = uiState.shelves
    val sortOrder = uiState.sortOrder
    val showRenameDialogFor = uiState.showRenameShelfDialogFor
    val showDeleteDialogFor = uiState.showDeleteShelfDialogFor

    var showRemoveFromShelfDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }

    BackHandler(enabled = true) {
        when {
            selectedItems.isNotEmpty() -> viewModel.clearContextualAction()
            isAddingBooks -> viewModel.dismissAddBooksToShelf()
            else -> viewModel.unselectShelf()
        }
    }

    val currentShelf = shelves.find { it.name == viewingShelfName }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewingShelfName != null && currentShelf != null) {
            if (isAddingBooks) {
                AddBooksModeScreen(
                    shelfName = viewingShelfName,
                    availableBooks = uiState.booksAvailableForAdding,
                    selectedBookUris = uiState.booksSelectedForAdding,
                    currentSource = uiState.addBooksSource,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onSourceChange = viewModel::setAddBooksSource,
                    onBookClick = { item -> viewModel.toggleBookSelectionForAdding(item.bookId) },
                    onBack = viewModel::dismissAddBooksToShelf,
                    onAddSelectedBooks = { viewModel.addBooksToShelf(viewingShelfName) },
                    downloadingBookIds = uiState.downloadingBookIds
                )
            } else {
                ShelfDetailScreen(
                    shelf = currentShelf,
                    selectedItems = selectedItems,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onBack = viewModel::unselectShelf,
                    onAddBooksClick = viewModel::showAddBooksToShelf,
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress,
                    onClearSelection = viewModel::clearContextualAction,
                    onInfoClick = {
                        if (selectedItems.size == 1) {
                            itemForInfoDialog = selectedItems.first()
                            showInfoDialog = true
                        }
                    },
                    onDeleteClick = { showRemoveFromShelfDialog = true },
                    onRenameShelf = { viewModel.showRenameShelfDialog(currentShelf.name) },
                    onDeleteShelf = { viewModel.showDeleteShelfDialog(currentShelf.name) },
                    downloadingBookIds = uiState.downloadingBookIds
                )
            }
        }

        if (showRenameDialogFor != null) {
            RenameShelfDialog(
                initialName = showRenameDialogFor,
                onConfirm = { newName -> viewModel.renameShelf(showRenameDialogFor, newName) },
                onDismiss = viewModel::dismissRenameShelfDialog
            )
        }

        if (showDeleteDialogFor != null) {
            DeleteShelfConfirmationDialog(
                shelfName = showDeleteDialogFor,
                onConfirm = { viewModel.deleteShelf(showDeleteDialogFor) },
                onDismiss = viewModel::dismissDeleteShelfDialog
            )
        }

        if (showRemoveFromShelfDialog) {
            RemoveFromShelfConfirmationDialog(
                count = selectedItems.size,
                shelfName = currentShelf?.name ?: "",
                onConfirm = {
                    viewModel.removeContextualItemsFromShelf()
                    showRemoveFromShelfDialog = false
                },
                onDismiss = { showRemoveFromShelfDialog = false }
            )
        }

        itemForInfoDialog?.let { item ->
            if (showInfoDialog) {
                FileInfoDialog(
                    item = item,
                    onDismiss = {
                        showInfoDialog = false
                        itemForInfoDialog = null
                    }
                )
            }
        }
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreenContent(
    recentFiles: List<RecentFileItem>,
    shelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    selectedShelves: Set<String>,
    sortOrder: SortOrder,
    pagerState: PagerState,
    scope: CoroutineScope,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onClearSelection: () -> Unit,
    onItemClick: (RecentFileItem) -> Unit,
    onItemLongClick: (RecentFileItem) -> Unit,
    onInfoClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onShelfClick: (Shelf) -> Unit,
    onShelfLongClick: (Shelf) -> Unit,
    onClearShelfSelection: () -> Unit,
    onDeleteShelves: () -> Unit,
    onNewShelfClick: () -> Unit,
    onSelectFileClick: () -> Unit,
    onScanNowClick: () -> Unit,
    onSelectSyncFolderClick: () -> Unit,
    onDisconnectSyncFolderClick: () -> Unit,
    downloadingBookIds: Set<String>,
    syncedFolderUri: String?,
    lastFolderScanTime: Long?,
    isLoading: Boolean,
) {
    val isBookContextualModeActive = selectedItems.isNotEmpty()
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }
    val tabTitles = listOf("All Books", "Shelves", "Folder")
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (isBookContextualModeActive) {
                    ContextualTopAppBar(
                        selectedItemCount = selectedItems.size,
                        onNavIconClick = onClearSelection,
                        onInfoClick = onInfoClick,
                        onDeleteClick = onDeleteClick,
                        onSelectAllClick = onSelectAllClick
                    )
                } else if (isShelfContextualModeActive && pagerState.currentPage == 1) {
                    ContextualTopAppBar(
                        selectedItemCount = selectedShelves.size,
                        onNavIconClick = onClearShelfSelection,
                        onDeleteClick = onDeleteShelves
                    )
                } else if (isSearchActive) {
                    Surface(
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onSearchActiveChange(false) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search title or author...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .focusRequester(searchFocusRequester),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear query")
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    CustomTopAppBar(
                        title = { Text("Library") },
                        actions = {
                            if (pagerState.currentPage == 0) {
                                Box {
                                    TextButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sort),
                                            contentDescription = "Sort",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(sortOrder.displayName)
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(order.displayName) },
                                                onClick = {
                                                    onSortOrderChange(order)
                                                    showSortMenu = false
                                                },
                                                trailingIcon = {
                                                    if (order == sortOrder) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "Selected"
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onSearchActiveChange(true) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                        }
                    )
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(title) }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isBookContextualModeActive && !isShelfContextualModeActive) {
                when (pagerState.currentPage) {
                    0 -> {
                        if (recentFiles.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                text = { Text("Add file") },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Add file") },
                                onClick = onSelectFileClick,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    1 -> {
                        ExtendedFloatingActionButton(
                            text = { Text("New shelf") },
                            icon = { Icon(Icons.Default.Add, contentDescription = "New shelf") },
                            onClick = onNewShelfClick,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            key = { it }
        ) { page ->
            when (page) {
                0 -> {
                    if (recentFiles.isEmpty() && searchQuery.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found for \"$searchQuery\"")
                        }
                    } else if (recentFiles.isEmpty()) {
                        EmptyState(
                            title = "Your Library is Empty",
                            message = "Select a PDF, EPUB, MOBI, or AZW3 file from your device to get started.",
                            onSelectFileClick = onSelectFileClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentFiles, key = { it.bookId }) { item ->
                                LibraryListItem(
                                    item = item,
                                    isSelected = selectedItems.any { it.bookId == item.bookId },
                                    onItemClick = { onItemClick(item) },
                                    onItemLongClick = { onItemLongClick(item) },
                                    isDownloading = item.bookId in downloadingBookIds
                                )
                            }
                        }
                    }
                }
                1 -> {
                    ShelvesScreen(
                        shelves = shelves,
                        onShelfClick = onShelfClick,
                        onShelfLongClick = onShelfLongClick,
                        selectedShelves = selectedShelves
                    )
                }
                2 -> {
                    FolderSyncScreen(
                        syncedFolderUri = syncedFolderUri,
                        lastScanTime = lastFolderScanTime,
                        onSelectFolderClick = onSelectSyncFolderClick,
                        onScanNowClick = onScanNowClick,
                        onChangeFolderClick = onSelectSyncFolderClick,
                        onDisconnectClick = onDisconnectSyncFolderClick,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    onShelfClick: (Shelf) -> Unit,
    onShelfLongClick: (Shelf) -> Unit,
    selectedShelves: Set<String>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shelves, key = { it.name }) { shelf ->
            ShelfListItem(
                shelf = shelf,
                isSelected = shelf.name in selectedShelves,
                onItemClick = { onShelfClick(shelf) },
                onItemLongClick = { onShelfLongClick(shelf) }
            )
        }
    }
}

@Composable
private fun CreateShelfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Shelf") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Shelf Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
private fun ShelfDetailScreen(
    shelf: Shelf,
    selectedItems: Set<RecentFileItem>,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onBack: () -> Unit,
    onAddBooksClick: () -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
    onClearSelection: () -> Unit,
    onInfoClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameShelf: () -> Unit,
    onDeleteShelf: () -> Unit,
    downloadingBookIds: Set<String>,
) {
    val isContextualModeActive = selectedItems.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            if (isContextualModeActive) {
                ContextualTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onNavIconClick = onClearSelection,
                    onInfoClick = onInfoClick,
                    onDeleteClick = onDeleteClick
                )
            } else {
                CustomTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = shelf.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = getBookCountString(shelf.bookCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sort),
                                    contentDescription = "Sort",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(sortOrder.displayName)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (order == sortOrder) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (shelf.name != "Unshelved") {
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename shelf") },
                                        onClick = {
                                            onRenameShelf()
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete shelf") },
                                        onClick = {
                                            onDeleteShelf()
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (shelf.name != "Unshelved" && !isContextualModeActive) {
                FloatingActionButton(onClick = onAddBooksClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add books")
                }
            }
        }
    ) { paddingValues ->
        if (shelf.books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("This shelf is empty", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shelf.books, key = { it.bookId }) { item ->
                    LibraryListItem(
                        item = item,
                        isSelected = selectedItems.any { it.bookId == item.bookId },
                        onItemClick = { onBookClick(item) },
                        onItemLongClick = { onBookLongClick(item) },
                        isDownloading = item.bookId in downloadingBookIds
                    )
                }
            }
        }
    }
}

@Composable
private fun AddBooksModeScreen(
    shelfName: String,
    availableBooks: List<RecentFileItem>,
    selectedBookUris: Set<String>,
    currentSource: AddBooksSource,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onSourceChange: (AddBooksSource) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBack: () -> Unit,
    onAddSelectedBooks: () -> Unit,
    downloadingBookIds: Set<String>,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            CustomTopAppBar(
                title = { Text("Add to $shelfName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.sort),
                                contentDescription = "Sort",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sortOrder.displayName)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayName) },
                                    onClick = {
                                        onSortOrderChange(order)
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (order == sortOrder) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showSourceMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false }
                        ) {
                            AddBooksSource.entries.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.displayName) },
                                    onClick = {
                                        onSourceChange(source)
                                        showSourceMenu = false
                                    },
                                    trailingIcon = {
                                        if (source == currentSource) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedBookUris.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("ADD (${selectedBookUris.size})") },
                    icon = { Icon(Icons.Default.Check, contentDescription = "Add books") },
                    onClick = onAddSelectedBooks
                )
            }
        }
    ) { paddingValues ->
        if (availableBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentSource == AddBooksSource.UNSHELVED) "No unshelved books to add" else "All books are already in this shelf",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableBooks, key = { it.bookId }) { item ->
                    val isSelected = item.bookId in selectedBookUris
                    LibraryListItem(
                        item = item,
                        isSelected = isSelected,
                        onItemClick = { onBookClick(item) },
                        onItemLongClick = { onBookClick(item) },
                        isDownloading = item.bookId in downloadingBookIds
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfCover(shelf: Shelf) {
    val context = LocalContext.current
    val placeholder = R.drawable.epub_placeholder
    val booksForCovers = shelf.books.take(4).reversed()
    val coverWidth = 52.dp
    val coverHeight = 75.dp
    val horizontalOffset = 12.dp
    val maxWidth = coverWidth + (horizontalOffset * (4 - 1))

    Box(
        modifier = Modifier
            .width(maxWidth)
            .height(coverHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        if (booksForCovers.size <= 1) {
            val imageModel = remember(shelf.topBook?.coverImagePath) {
                shelf.topBook?.coverImagePath?.let { File(it) }?.takeIf { it.exists() } ?: placeholder
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .error(placeholder)
                    .fallback(placeholder)
                    .crossfade(true)
                    .build(),
                contentDescription = "${shelf.name} shelf cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = coverWidth, height = coverHeight)
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier
                    .width(coverWidth + (horizontalOffset * (booksForCovers.size - 1)))
                    .height(coverHeight)
            ) {
                booksForCovers.forEachIndexed { index, book ->
                    val imageModel = remember(book.coverImagePath) {
                        book.coverImagePath?.let { File(it) }?.takeIf { it.exists() } ?: placeholder
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(width = coverWidth, height = coverHeight)
                            .align(Alignment.CenterEnd)
                            .offset(x = -horizontalOffset * index)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .error(placeholder)
                                .fallback(placeholder)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfListItem(
    shelf: Shelf,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = {
                    if (shelf.name != "Unshelved") {
                        onItemLongClick()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfCover(shelf = shelf)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getBookCountString(shelf.bookCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListItem(
    item: RecentFileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    isDownloading: Boolean,
) {
    val context = LocalContext.current
    val placeholder = when (item.type) {
        FileType.PDF -> R.drawable.pdf_placeholder
        FileType.EPUB, FileType.MOBI, FileType.MD, FileType.TXT, FileType.HTML -> R.drawable.epub_placeholder
    }
    val imageModel = remember(item.coverImagePath) {
        item.coverImagePath?.let { File(it) }?.takeIf { it.exists() } ?: placeholder
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (item.isAvailable) 1.0f else 0.8f }
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .error(placeholder)
                    .fallback(placeholder)
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 70.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title ?: item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!item.isAvailable) {
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Not available locally",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item.author?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.progressPercentage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${it.toInt()}% complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameShelfDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(initialName.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Shelf") },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text("Shelf Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textFieldValue.text) },
                enabled = textFieldValue.text.isNotBlank() && textFieldValue.text != initialName
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
private fun DeleteShelfConfirmationDialog(
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Shelf?") },
        text = { Text("Are you sure you want to delete the '$shelfName' shelf? All books will be moved to Unshelved.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RemoveFromShelfConfirmationDialog(
    count: Int,
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val bookStr = if (count == 1) "book" else "books"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from Shelf?") },
        text = { Text("Are you sure you want to remove $count $bookStr from the '$shelfName' shelf? The book(s) will remain in your library and appear under Unshelved.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteShelvesConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shelfStr = if (count == 1) "shelf" else "shelves"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $shelfStr?") },
        text = { Text("Are you sure you want to delete the $count selected $shelfStr? All books within will be moved to Unshelved.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getDisplayPathFromUri(context: Context, uriString: String): String {
    val uri = uriString.toUri()
    val fallbackName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Unknown Folder"

    if (DocumentsContract.isTreeUri(uri) && DocumentsContract.getTreeDocumentId(uri).isNotEmpty()) {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val split = documentId.split(":")
        if (split.size > 1) {
            val type = split[0]
            val path = split[1]
            return when (type) {
                "primary" -> "Internal Storage ▸ $path"
                else -> path
            }
        }
    }
    return fallbackName
}

@Composable
private fun FolderSyncScreen(
    syncedFolderUri: String?,
    lastScanTime: Long?,
    onSelectFolderClick: () -> Unit,
    onScanNowClick: () -> Unit,
    onChangeFolderClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current

    if (syncedFolderUri == null) {
        EmptyState(
            title = "Auto-Sync a Folder",
            message = "Select a folder on your device. Episteme will automatically find and import any new books you add to it. A great way to keep your library in sync with your downloads folder!",
            onSelectFileClick = onSelectFolderClick,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val folderPath = remember(syncedFolderUri) {
            getDisplayPathFromUri(context, syncedFolderUri)
        }

        val lastScanText = remember(lastScanTime) {
            if (lastScanTime == null || lastScanTime == 0L) {
                "Never scanned"
            } else {
                "Last scan: ${
                    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(
                        Date(lastScanTime)
                    )
                }"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderSpecial, // Using a standard icon
                contentDescription = "Synced Folder",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Folder Sync is Active",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth() // Add this modifier
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Monitoring folder:",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = folderPath,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = lastScanText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth() // Add this modifier
                )
            }
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onScanNowClick, enabled = !isLoading) {
                Text("Scan for New Books")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onChangeFolderClick, enabled = !isLoading) {
                Text("Change Folder")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDisconnectClick, enabled = !isLoading) {
                Text("Disconnect Folder")
            }
        }
    }
}