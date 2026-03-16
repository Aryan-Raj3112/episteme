/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
// LibraryScreen.kt
package com.aryan.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
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
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val containsFolderItems = remember(selectedItems) {
        selectedItems.any { it.sourceFolderUri != null }
    }

    LaunchedEffect(uiState.libraryScreenStartPage) {
        if (pagerState.currentPage != uiState.libraryScreenStartPage) {
            pagerState.animateScrollToPage(uiState.libraryScreenStartPage)
        }
    }

    val scope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }

    val isSearchActive = uiState.isSearchActive
    val searchQuery = uiState.searchQuery

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.addSyncedFolder(it)
        }
    }

    val onSelectSyncFolderClick = {
        try {
            pickFolderLauncher.launch(null)
        } catch (_: android.content.ActivityNotFoundException) {
            viewModel.showBanner("Your device doesn't support folder selection. You can still import files individually.", isError = true)
        }
    }

    val pickFileLauncher = rememberFilePickerLauncher { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        uris.forEach { uri ->
            viewModel.onFileSelected(uri, isFromRecent = false)
        }
    }

    val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
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
        try {
            pickFileLauncher.launch(arrayOf("*/*"))
        } catch (_: android.content.ActivityNotFoundException) {
            Timber.w("OpenDocument picker failed. Falling back to GetMultipleContents.")
            try {
                fallbackFilePickerLauncher.launch("*/*")
            } catch (_: android.content.ActivityNotFoundException) {
                viewModel.showBanner("No file manager found. Please install a file manager app.", isError = true)
            }
        }
    }

    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .collect { page ->
                viewModel.setLibraryScreenPage(page)
            }
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
        viewModel.setSearchActive(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryScreenContent(
            recentFiles = uiState.allRecentFiles,
            shelves = shelves,
            selectedItems = selectedItems,
            selectedShelves = selectedShelves,
            sortOrder = sortOrder,
            libraryFilters = uiState.libraryFilters,
            pinnedLibraryBookIds = uiState.pinnedLibraryBookIds,
            pagerState = pagerState,
            scope = scope,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchActiveChange = viewModel::setSearchActive,
            onSortOrderChange = viewModel::setSortOrder,
            onFilterClick = { showFilterSheet = true },
            onClearFilters = { viewModel.updateLibraryFilters(LibraryFilters()) },
            onRemoveFilter = { viewModel.updateLibraryFilters(it) },
            onPinClick = { viewModel.togglePinForContextualItems(isHome = false) },
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
            onSyncMetadataClick = viewModel::syncFolderMetadata,
            onSelectSyncFolderClick = onSelectSyncFolderClick,
            syncedFolders = uiState.syncedFolders,
            onAddFolderClick = { uri -> viewModel.addSyncedFolder(uri) },
            onRemoveFolderClick = { folder -> viewModel.removeSyncedFolder(folder) },
            onDisconnectSyncFolderClick = viewModel::disconnectAllSyncedFolders,
            downloadingBookIds = uiState.downloadingBookIds,
            lastFolderScanTime = uiState.lastFolderScanTime,
            isLoading = uiState.isLoading,
            isRefreshing = uiState.isRefreshing,
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
                isPermanentDelete = true,
                containsFolderItems = containsFolderItems
            )
        }

        if (showFilterSheet) {
            LibraryFilterSheet(
                filters = uiState.libraryFilters,
                syncedFolders = uiState.syncedFolders,
                onApply = { viewModel.updateLibraryFilters(it) },
                onDismiss = { showFilterSheet = false }
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

@Suppress("unused")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreenContent(
    recentFiles: List<RecentFileItem>,
    shelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    selectedShelves: Set<String>,
    sortOrder: SortOrder,
    libraryFilters: LibraryFilters,
    pinnedLibraryBookIds: Set<String>,
    pagerState: PagerState,
    scope: CoroutineScope,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onFilterClick: () -> Unit,
    onClearFilters: () -> Unit,
    onRemoveFilter: (LibraryFilters) -> Unit,
    onPinClick: () -> Unit,
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
    onSyncMetadataClick: () -> Unit,
    onSelectSyncFolderClick: () -> Unit,
    onDisconnectSyncFolderClick: () -> Unit,
    downloadingBookIds: Set<String>,
    lastFolderScanTime: Long?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    syncedFolders: List<SyncedFolder>,
    onAddFolderClick: (android.net.Uri) -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
) {
    val isBookContextualModeActive = selectedItems.isNotEmpty()
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }
    val tabTitles = listOf("All Books", "Shelves", "Folders")
    val searchFocusRequester = remember { FocusRequester() }

    var textFieldValue by remember(isSearchActive) {
        mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
    }

    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        }
    }

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
                        onPinClick = onPinClick,
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
                        modifier = Modifier.fillMaxWidth()
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
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    onSearchQueryChange(it.text)
                                },
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
                                IconButton(onClick = onFilterClick) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
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
                    androidx.compose.animation.AnimatedVisibility(
                        visible = libraryFilters.isActive && pagerState.currentPage == 0
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (libraryFilters.fileTypes.isNotEmpty()) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(fileTypes = emptySet())) },
                                    label = { Text("Types: ${libraryFilters.fileTypes.joinToString { it.name }}") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.sourceFolders.isNotEmpty()) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(sourceFolders = emptySet())) },
                                    label = { Text("Folders: ${libraryFilters.sourceFolders.size}") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.readStatus != ReadStatusFilter.ALL) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(readStatus = ReadStatusFilter.ALL)) },
                                    label = { Text("Status: ${libraryFilters.readStatus.displayName}") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) }
                                )
                            }
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
                                    isPinned = item.bookId in pinnedLibraryBookIds,
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
                        syncedFolders = syncedFolders,
                        onAddFolderClick = onAddFolderClick,
                        onRemoveFolderClick = onRemoveFolderClick,
                        onScanNowClick = onScanNowClick,
                        onSyncMetadataClick = onSyncMetadataClick,
                        isLoading = isLoading || isRefreshing
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
                ExtendedFloatingActionButton(
                    onClick = onAddBooksClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add books") }
                )
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
                shelf.topBook?.coverImagePath?.let { File(it) } ?: placeholder
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
                        book.coverImagePath?.let { File(it) } ?: placeholder
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
    isPinned: Boolean = false,
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
        item.coverImagePath?.let { File(it) } ?: placeholder
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
                    if (item.sourceFolderUri != null) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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

@Composable
private fun FolderSyncScreen(
    syncedFolders: List<SyncedFolder>,
    onAddFolderClick: (android.net.Uri) -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    isLoading: Boolean
) {
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            onAddFolderClick(it)
        }
    }

    LocalContext.current

    Scaffold(
        floatingActionButton = {
            if (syncedFolders.size < 3) {
                ExtendedFloatingActionButton(
                    text = { Text("Add Folder") },
                    icon = { Icon(Icons.Default.Add, "Add") },
                    onClick = { pickFolderLauncher.launch(null) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Global Actions Header
            if (syncedFolders.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onScanNowClick,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isLoading) "Scanning..." else "Scan All")
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = onSyncMetadataClick,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(painterResource(id = R.drawable.sync), null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Meta")
                    }
                }
            } else {
                EmptyState(
                    title = "Sync Local Folders",
                    message = "Connect local folders to create a live library. Episteme will monitor files and sync progress.",
                    onSelectFileClick = { pickFolderLauncher.launch(null) },
                    primaryButtonText = "Select Folder",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // List of Folders
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                items(syncedFolders, key = { it.uriString }) { folder ->
                    FolderCard(folder, onRemoveFolderClick)
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: SyncedFolder,
    onRemoveClick: (SyncedFolder) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val lastScanText = if (folder.lastScanTime == 0L) "Never" else dateFormat.format(Date(folder.lastScanTime))

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove Folder") },
                            onClick = {
                                showMenu = false
                                onRemoveClick(folder)
                            },
                            colors = androidx.compose.material3.MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Details
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAST SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = lastScanText, style = MaterialTheme.typography.bodySmall)
                }

                // You could add Book Count here if we queried it from DB
                // For now, let's just show Status
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Active", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    syncedFolders: List<SyncedFolder>,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFilters by remember { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Filter Library", style = MaterialTheme.typography.titleLarge)

            Text("File Type", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileType.entries.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            val newSet = if (type in currentFilters.fileTypes) currentFilters.fileTypes - type else currentFilters.fileTypes + type
                            currentFilters = currentFilters.copy(fileTypes = newSet)
                        },
                        label = { Text(type.name) }
                    )
                }
            }

            if (syncedFolders.isNotEmpty()) {
                Text("Source Folder", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    syncedFolders.forEach { folder ->
                        FilterChip(
                            selected = folder.uriString in currentFilters.sourceFolders,
                            onClick = {
                                val newSet = if (folder.uriString in currentFilters.sourceFolders) currentFilters.sourceFolders - folder.uriString else currentFilters.sourceFolders + folder.uriString
                                currentFilters = currentFilters.copy(sourceFolders = newSet)
                            },
                            label = { Text(folder.name) }
                        )
                    }
                }
            }

            Text("Read Status", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(status.displayName) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) {
                    Text("Clear All")
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = { onApply(currentFilters); onDismiss() }) {
                    Text("Apply")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}