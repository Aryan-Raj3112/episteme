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

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.aryan.reader.opds.OpdsViewModel
import com.aryan.reader.opds.OpdsEntry
import com.aryan.reader.opds.OpdsCatalog
import org.jsoup.Jsoup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.opds.OpdsAcquisition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun getBookCountString(count: Int): String {
    return pluralStringResource(id = R.plurals.book_count, count, count)
}

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val isContextualModeActive = selectedItems.isNotEmpty()
    val selectedShelves = uiState.contextualActionShelfNames
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    val sortOrder = uiState.sortOrder
    val shelves = uiState.shelves
    val rawLibraryFiles = uiState.rawLibraryFiles
    val pagerState = rememberPagerState(
        initialPage = uiState.libraryScreenStartPage,
        pageCount = { 4 }
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
            viewModel.showBanner(context.getString(R.string.error_folder_selection_unsupported), isError = true)
        }
    }

    val pickFileLauncher = rememberFilePickerLauncher { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        viewModel.onFilesSelected(uris)
    }

    val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        viewModel.onFilesSelected(uris)
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
                viewModel.showBanner(context.getString(R.string.error_no_file_manager), isError = true)
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
            rawLibraryFiles = rawLibraryFiles,
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
            onEditFolderFiltersClick = { folder, filters -> viewModel.updateFolderFilters(folder, filters) },
            syncedFolders = uiState.syncedFolders,
            onRemoveFolderClick = { folder -> viewModel.removeSyncedFolder(folder) },
            onDisconnectSyncFolderClick = viewModel::disconnectAllSyncedFolders,
            downloadingBookIds = uiState.downloadingBookIds,
            lastFolderScanTime = uiState.lastFolderScanTime,
            isLoading = uiState.isLoading,
            isRefreshing = uiState.isRefreshing,
            onOpdsBookDownloaded = { uri, title ->
                viewModel.showBanner(context.getString(R.string.banner_downloaded, title))
                viewModel.onFileSelected(uri, isFromRecent = false)
            },
            onStreamOpdsBook = { entry, catalog ->
                viewModel.streamOpdsBook(
                    bookId = entry.id,
                    title = entry.title,
                    urlTemplate = entry.pseUrlTemplate!!,
                    pageCount = entry.pseCount!!,
                    catalogId = catalog?.id
                )
            },
            onDeleteCatalogStreams = viewModel::deleteStreamedBooksForCatalog
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
                    },
                    onUpdateName = { newName ->
                        viewModel.updateCustomName(item.bookId, newName)
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
                    },
                    onUpdateName = { newName ->
                        viewModel.updateCustomName(item.bookId, newName)
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
    rawLibraryFiles: List<RecentFileItem>,
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
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onDisconnectSyncFolderClick: () -> Unit,
    downloadingBookIds: Set<String>,
    lastFolderScanTime: Long?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    syncedFolders: List<SyncedFolder>,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onOpdsBookDownloaded: (Uri, String) -> Unit,
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
) {
    val isBookContextualModeActive = selectedItems.isNotEmpty()
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }
    val tabTitles = listOf(
        stringResource(R.string.tab_all_books),
        stringResource(R.string.tab_shelves),
        stringResource(R.string.tab_folders),
        stringResource(R.string.tab_catalogs)
    )
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
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
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
                    CustomTopAppBar(title = { Text(stringResource(R.string.library_title)) },
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
                                    label = { Text(stringResource(R.string.filter_types, libraryFilters.fileTypes.joinToString { it.name })) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.sourceFolders.isNotEmpty()) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(sourceFolders = emptySet())) },
                                    label = { Text(stringResource(R.string.filter_folders, libraryFilters.sourceFolders.size)) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.readStatus != ReadStatusFilter.ALL) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(readStatus = ReadStatusFilter.ALL)) },
                                    label = { Text(stringResource(R.string.filter_status, libraryFilters.readStatus.displayName)) },
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
                                text = { Text(stringResource(R.string.fab_add_file)) },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Add file") },
                                onClick = onSelectFileClick,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    1 -> {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.fab_new_shelf)) },
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
                            Text(stringResource(R.string.no_results_found, searchQuery))
                        }
                    } else if (recentFiles.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.your_library_empty),
                            message = stringResource(R.string.library_empty_desc),
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
                        allRecentFiles = rawLibraryFiles,
                        onAddFolderClick = onSelectSyncFolderClick,
                        onRemoveFolderClick = onRemoveFolderClick,
                        onEditFolderFiltersClick = onEditFolderFiltersClick,
                        onScanNowClick = onScanNowClick,
                        onSyncMetadataClick = onSyncMetadataClick,
                        isLoading = isLoading || isRefreshing
                    )
                }
                3 -> {
                    OpdsTab(
                        localLibraryFiles = rawLibraryFiles,
                        onBookDownloaded = onOpdsBookDownloaded,
                        onReadBook = onItemClick,
                        onStreamBook = onStreamOpdsBook,
                        onDeleteCatalogStreams = onDeleteCatalogStreams
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
        title = { Text(stringResource(R.string.create_new_shelf)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.shelf_name_hint)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
                                        text = { Text(stringResource(R.string.menu_rename_shelf)) },
                                        onClick = {
                                            onRenameShelf()
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_delete_shelf)) },
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
                    text = { Text(stringResource(R.string.fab_add_books)) }
                )
            }
        }
    ) { paddingValues ->
        if (shelf.books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.shelf_empty), style = MaterialTheme.typography.bodyLarge)
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
                title = { Text(stringResource(R.string.add_to_shelf, shelfName)) },
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
                    text = { Text(stringResource(R.string.fab_add_count, selectedBookUris.size)) },
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
                    text = if (currentSource == AddBooksSource.UNSHELVED) stringResource(R.string.no_unshelved_books) else stringResource(R.string.all_books_in_shelf),
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
        FileType.EPUB, FileType.MOBI, FileType.FB2, FileType.MD, FileType.TXT, FileType.HTML, FileType.CBZ, FileType.CBR, FileType.CB7, FileType.DOCX, FileType.ODT, FileType.FODT -> R.drawable.epub_placeholder
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
                    val isOpdsStream = item.uriString?.startsWith("opds-pse://") == true
                    if (isOpdsStream) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "OPDS Stream",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
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
                        text = item.customName ?: item.title ?: item.displayName,
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

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileTypeBadge(type = item.type, overlay = false)

                    item.progressPercentage?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
        title = { Text(stringResource(R.string.menu_rename_shelf)) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text(stringResource(R.string.shelf_name_hint)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textFieldValue.text) },
                enabled = textFieldValue.text.isNotBlank() && textFieldValue.text != initialName
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.dialog_delete_shelf)) },
        text = { Text(stringResource(R.string.dialog_delete_shelf_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
    val bookStr = pluralStringResource(id = R.plurals.book_word, count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_remove_from_shelf)) },
        text = { Text(stringResource(R.string.dialog_remove_from_shelf_desc, count, bookStr, shelfName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun DeleteShelvesConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shelfStr = pluralStringResource(id = R.plurals.shelf_count, count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_shelves, shelfStr)) },
        text = { Text(stringResource(R.string.dialog_delete_shelves_desc, count, shelfStr)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FolderSyncScreen(
    syncedFolders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    isLoading: Boolean
) {
    var editingFolder by remember { mutableStateOf<SyncedFolder?>(null) }

    Scaffold(
        floatingActionButton = {
            if (syncedFolders.size < 10) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.fab_add_folder)) },
                    icon = { Icon(Icons.Default.Add, "Add") },
                    onClick = onAddFolderClick
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
            if (syncedFolders.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
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
                        Text(if (isLoading) stringResource(R.string.scanning) else stringResource(R.string.scan_all))
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = onSyncMetadataClick,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(painterResource(id = R.drawable.sync), null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_meta))
                    }
                }
            } else {
                EmptyState(
                    title = stringResource(R.string.sync_local_folders),
                    message = stringResource(R.string.sync_folders_desc),
                    onSelectFileClick = onAddFolderClick,
                    primaryButtonText = stringResource(R.string.action_select_folder),
                    modifier = Modifier.fillMaxSize()
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(syncedFolders, key = { it.uriString }) { folder ->
                    FolderCard(
                        folder = folder,
                        allRecentFiles = allRecentFiles,
                        onRemoveClick = onRemoveFolderClick,
                        onEditFiltersClick = { editingFolder = folder }
                    )
                }
            }
        }
    }

    if (editingFolder != null) {
        EditFolderFiltersDialog(
            folder = editingFolder!!,
            onConfirm = { newFilters ->
                onEditFolderFiltersClick(editingFolder!!, newFilters)
                editingFolder = null
            },
            onDismiss = { editingFolder = null }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FolderCard(
    folder: SyncedFolder,
    allRecentFiles: List<RecentFileItem>,
    onRemoveClick: (SyncedFolder) -> Unit,
    onEditFiltersClick: (SyncedFolder) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val lastScanText = if (folder.lastScanTime == 0L) stringResource(R.string.never) else dateFormat.format(Date(folder.lastScanTime))

    val folderFiles = remember(allRecentFiles, folder.uriString) {
        allRecentFiles.filter { it.sourceFolderUri == folder.uriString }
    }
    val totalBooks = folderFiles.size
    val countsByType = remember(folderFiles) {
        folderFiles.groupBy { it.type }.mapValues { it.value.size }
    }

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
                            text = { Text(stringResource(R.string.menu_edit_filters)) },
                            onClick = {
                                showMenu = false
                                onEditFiltersClick(folder)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove_folder)) },
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

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.last_sync),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = lastScanText, style = MaterialTheme.typography.bodySmall)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.books_count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = totalBooks.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (countsByType.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    countsByType.forEach { (type, count) ->
                        AssistChip(
                            onClick = { },
                            label = { Text(stringResource(R.string.folder_filter_count, type.name, count)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditFolderFiltersDialog(
    folder: SyncedFolder,
    onConfirm: (Set<FileType>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTypes by remember { mutableStateOf(folder.allowedFileTypes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.filter_file_types),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.filter_file_types_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileType.entries.forEach { type ->
                        val isSelected = type in selectedTypes
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTypes = if (isSelected) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                            },
                            label = {
                                Text(
                                    text = type.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onConfirm(selectedTypes) },
                enabled = selectedTypes.isNotEmpty(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
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
            Text(stringResource(R.string.filter_library), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.filter_file_type), style = MaterialTheme.typography.titleMedium)
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

            Text(stringResource(R.string.filter_source_folder), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = "IN_APP_STORAGE" in currentFilters.sourceFolders,
                    onClick = {
                        val newSet = if ("IN_APP_STORAGE" in currentFilters.sourceFolders) currentFilters.sourceFolders - "IN_APP_STORAGE" else currentFilters.sourceFolders + "IN_APP_STORAGE"
                        currentFilters = currentFilters.copy(sourceFolders = newSet)
                              },
                    label = { Text(stringResource(R.string.filter_in_app_storage)) }
                )
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

            Text(stringResource(R.string.filter_read_status), style = MaterialTheme.typography.titleMedium)
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
                    Text(stringResource(R.string.clear_all))
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = { onApply(currentFilters); onDismiss() }) {
                    Text(stringResource(R.string.action_apply))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun OpdsTab(
    localLibraryFiles: List<RecentFileItem>,
    onBookDownloaded: (Uri, String) -> Unit,
    onReadBook: (RecentFileItem) -> Unit,
    onStreamBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    opdsViewModel: OpdsViewModel = viewModel()
) {
    val uiState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val downloadingState by opdsViewModel.downloadingState.collectAsStateWithLifecycle()
    val downloadingEntries by opdsViewModel.downloadingEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedEntry by remember { mutableStateOf<OpdsEntry?>(null) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var editingCatalog by remember { mutableStateOf<OpdsCatalog?>(null) }
    var catalogToDelete by remember { mutableStateOf<OpdsCatalog?>(null) }

    BackHandler(enabled = uiState.isViewingCatalog) {
        opdsViewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!uiState.isViewingCatalog) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.catalogs, key = { it.id }) { catalog ->
                        OpdsCatalogCard(
                            catalog = catalog,
                            onClick = { opdsViewModel.openCatalog(catalog) },
                            onEdit = if (catalog.isDefault) null else {
                                {
                                    editingCatalog = catalog
                                    showCatalogDialog = true
                                }
                            },
                            onDelete = if (catalog.isDefault) null else {
                                { catalogToDelete = catalog }
                            })
                    }
                }

                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.fab_add_catalog)) },
                    icon = { Icon(Icons.Default.Add, "Add") },
                    onClick = {
                        editingCatalog = null
                        showCatalogDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        } else {
            // Screen 2: Viewing a specific feed/catalog
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var showSearch by remember { mutableStateOf(false) }
                        var query by remember { mutableStateOf("") }

                        val searchFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(showSearch) {
                            if (showSearch) {
                                delay(100)
                                searchFocusRequester.requestFocus()
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                IconButton(onClick = {
                                    if (showSearch) {
                                        showSearch = false
                                        query = ""
                                    } else {
                                        opdsViewModel.navigateBack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }

                                if (showSearch) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        placeholder = { Text(stringResource(R.string.search_catalog_placeholder)) },
                                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
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
                                            IconButton(onClick = {
                                                if (query.isNotBlank()) {
                                                    opdsViewModel.search(query)
                                                    showSearch = false
                                                    query = ""
                                                }
                                            }) {
                                                Icon(Icons.Default.Search, "Search")
                                            }
                                        },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                        ),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onSearch = {
                                                if (query.isNotBlank()) {
                                                    opdsViewModel.search(query)
                                                    showSearch = false
                                                    query = ""
                                                }
                                            })
                                    )
                                } else {
                                    Text(
                                        text = uiState.currentFeed?.title ?: stringResource(R.string.status_loading),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    if (uiState.searchUrlTemplate != null) {
                                        IconButton(onClick = { showSearch = true }) {
                                            Icon(Icons.Default.Search, "Search")
                                        }
                                    }
                                }
                            }

                            if (uiState.isLoading) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }

                    if (uiState.currentFeed?.entries?.isEmpty() == true && !uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.feed_empty))
                        }
                    } else {
                        val facets = uiState.currentFeed?.facets ?: emptyList()
                        if (facets.isNotEmpty()) {
                            val groups = facets.groupBy { it.group }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groups.forEach { (groupName, groupFacets) ->
                                    item(key = groupName) {
                                        var expanded by remember { mutableStateOf(false) }
                                        val activeFacet = groupFacets.find { it.isActive }
                                            ?: groupFacets.firstOrNull()

                                        Box {
                                            FilterChip(
                                                selected = activeFacet?.isActive == true,
                                                onClick = { expanded = true },
                                                label = { Text(stringResource(R.string.filter_facet, groupName, activeFacet?.title ?: stringResource(R.string.action_select))) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        null
                                                    )
                                                })
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }) {
                                                groupFacets.forEach { facet ->
                                                    DropdownMenuItem(
                                                        text = { Text(facet.title) },
                                                        onClick = {
                                                            expanded = false
                                                            opdsViewModel.openFeedUrl(facet.url)
                                                        },
                                                        trailingIcon = if (facet.isActive) {
                                                            { Icon(Icons.Default.Check, null) }
                                                        } else null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val entries = uiState.currentFeed?.entries ?: emptyList()
                            itemsIndexed(
                                entries,
                                key = { index, item -> "${item.id}_$index" }) { index, entry ->

                                if (index == entries.lastIndex) {
                                    LaunchedEffect(index) { opdsViewModel.loadNextPage() }
                                }

                                if (entry.isNavigation) {
                                    OpdsNavigationCard(entry) { opdsViewModel.openFeedUrl(it) }
                                } else {
                                    OpdsBookCard(
                                        entry = entry,
                                        localLibraryFiles = localLibraryFiles,
                                        downloadState = downloadingState[entry.id],
                                        onDownloadClick = { acquisition ->
                                            opdsViewModel.downloadBook(
                                                entry, acquisition, context
                                            ) { downloadedUri ->
                                                onBookDownloaded(downloadedUri, entry.title)
                                            }
                                        },
                                        onReadClick = onReadBook,
                                        onStreamClick = {
                                            onStreamBook(
                                                entry,
                                                uiState.currentCatalog
                                            )
                                        },
                                        onClick = { selectedEntry = entry })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error Banner overlay
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                delay(4000)
                opdsViewModel.clearError()
            }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    .padding(bottom = 70.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (selectedEntry != null) {
            OpdsBookDetailsSheet(
                entry = selectedEntry!!,
                localLibraryFiles = localLibraryFiles,
                downloadState = downloadingState[selectedEntry!!.id],
                onDownloadFormat = { acquisition ->
                    opdsViewModel.downloadBook(selectedEntry!!, acquisition, context) { downloadedUri ->
                        onBookDownloaded(downloadedUri, selectedEntry!!.title)
                    }
                },
                onReadClick = onReadBook,
                onStreamClick = { selectedEntry?.let { onStreamBook(it, uiState.currentCatalog) } },
                onAuthorOrCategoryClick = { url, fallbackName ->
                    if (url != null) opdsViewModel.openFeedUrl(url)
                    else opdsViewModel.search(fallbackName)
                    selectedEntry = null
                },
                onDismiss = { selectedEntry = null }
            )
        }
    }

    // Dynamic Add/Edit Dialog
    if (showCatalogDialog) {
        var newTitle by remember(editingCatalog) { mutableStateOf(editingCatalog?.title ?: "") }
        var newUrl by remember(editingCatalog) { mutableStateOf(editingCatalog?.url ?: "") }
        var newUsername by remember(editingCatalog) { mutableStateOf(editingCatalog?.username ?: "") }
        var newPassword by remember(editingCatalog) { mutableStateOf(editingCatalog?.password ?: "") }

        val isEditMode = editingCatalog != null

        AlertDialog(
            onDismissRequest = {
                showCatalogDialog = false
                editingCatalog = null
            },
            title = { Text(if (isEditMode) stringResource(R.string.edit_catalog) else stringResource(R.string.add_opds_catalog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.catalog_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(stringResource(R.string.url)) },
                        placeholder = { Text(stringResource(R.string.url_placeholder)) },
                        singleLine = true
                    )
                    Text(stringResource(R.string.auth_optional),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isEditMode) {
                            opdsViewModel.updateCatalog(editingCatalog!!.id, newTitle, newUrl, newUsername, newPassword)
                        } else {
                            opdsViewModel.addCatalog(newTitle, newUrl, newUsername, newPassword)
                        }
                        showCatalogDialog = false
                        editingCatalog = null
                    },
                    enabled = newTitle.isNotBlank() && newUrl.isNotBlank()
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCatalogDialog = false
                    editingCatalog = null
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (catalogToDelete != null) {
        val streamedBooksCount = localLibraryFiles.count { it.uriString?.contains("catalogId=${catalogToDelete!!.id}") == true }
        AlertDialog(
            onDismissRequest = { catalogToDelete = null },
            title = { Text(stringResource(R.string.delete_catalog)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_catalog_desc, catalogToDelete!!.title))
                    if (streamedBooksCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.delete_catalog_warning, streamedBooksCount),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        opdsViewModel.removeCatalog(catalogToDelete!!.id)
                        if (streamedBooksCount > 0) {
                            onDeleteCatalogStreams(catalogToDelete!!.id)
                        }
                        catalogToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { catalogToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
fun OpdsCatalogCard(catalog: OpdsCatalog, onClick: () -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(catalog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (catalog.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(stringResource(R.string.preset_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(catalog.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
fun OpdsNavigationCard(entry: OpdsEntry, onClick: (String) -> Unit) {
    Surface(
        onClick = { entry.navigationUrl?.let { onClick(it) } },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                entry.summary?.let {
                    val cleanSummary = remember(it) { Jsoup.parse(it).text() }
                    Text(cleanSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun OpdsBookCard(
    entry: OpdsEntry,
    localLibraryFiles: List<RecentFileItem>,
    downloadState: OpdsViewModel.DownloadState?,
    onDownloadClick: (OpdsAcquisition) -> Unit,
    onReadClick: (RecentFileItem) -> Unit,
    onStreamClick: () -> Unit,
    onClick: () -> Unit
) {
    val libraryItem = remember(entry, localLibraryFiles) {
        localLibraryFiles.find { it.title.equals(entry.title, ignoreCase = true) || it.displayName.equals(entry.title, ignoreCase = true) }
    }
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }
    var showFormatMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = entry.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 70.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                entry.summary?.let {
                    val cleanSummary = remember(it) { Jsoup.parse(it).text() }
                    Text(cleanSummary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (libraryItem != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onReadClick(libraryItem) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_read))
                    }
                } else if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.status_downloading), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            if (progress != null) {
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (progress != null) {
                            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.isStreamable) {
                            FilledTonalButton(
                                onClick = onStreamClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(painterResource(id = R.drawable.play), null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_stream))
                            }
                        }

                        Box {
                            FilledTonalButton(
                                onClick = {
                                    if (uniqueAcquisitions.size == 1) {
                                        onDownloadClick(uniqueAcquisitions.first())
                                    } else if (uniqueAcquisitions.size > 1) {
                                        showFormatMenu = true
                                    }
                                },
                                enabled = uniqueAcquisitions.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                if (uniqueAcquisitions.isEmpty()) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_unavailable))
                                } else {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_download))
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showFormatMenu,
                            onDismissRequest = { showFormatMenu = false }
                        ) {
                            uniqueAcquisitions.forEach { acq ->
                                DropdownMenuItem(
                                    text = { Text(acq.formatName) },
                                    onClick = {
                                        showFormatMenu = false
                                        onDownloadClick(acq)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBookDetailsSheet(
    entry: OpdsEntry,
    localLibraryFiles: List<RecentFileItem>,
    downloadState: OpdsViewModel.DownloadState?,
    onDownloadFormat: (OpdsAcquisition) -> Unit,
    onReadClick: (RecentFileItem) -> Unit,
    onStreamClick: () -> Unit,
    onAuthorOrCategoryClick: (String?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val libraryItem = remember(entry, localLibraryFiles) {
        localLibraryFiles.find { it.title.equals(entry.title, ignoreCase = true) || it.displayName.equals(entry.title, ignoreCase = true) }
    }
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )

                    if (entry.authors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.authors.forEach { author ->
                                Text(
                                    text = author.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onAuthorOrCategoryClick(author.url, author.name)
                                    }
                                )
                            }
                        }
                    }

                    entry.series?.takeIf { it.isNotBlank() }?.let { series ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val seriesText = if (!entry.seriesIndex.isNullOrBlank()) "$series #${entry.seriesIndex}" else series
                        Text(
                            text = seriesText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onAuthorOrCategoryClick(null, series)
                            }
                        )
                    }
                }
            }

            if (libraryItem != null) {
                androidx.compose.material3.Button(
                    onClick = {
                        onDismiss()
                        onReadClick(libraryItem)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Read")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_read), fontWeight = FontWeight.Bold)
                }
            }

            if (isDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.status_downloading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        if (progress != null) {
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress != null) {
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                    }
                }
            } else if (uniqueAcquisitions.isNotEmpty() || entry.isStreamable) {
                if (entry.isStreamable) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onStreamClick()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(painterResource(id = R.drawable.play), null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_stream_now), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uniqueAcquisitions.isNotEmpty()) {
                    Text(stringResource(R.string.download_format),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uniqueAcquisitions.forEach { acq ->
                            FilledTonalButton(onClick = { onDownloadFormat(acq) }) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(acq.formatName, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.no_supported_formats), color = MaterialTheme.colorScheme.error)
            }

            if (entry.categories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entry.categories.distinct().forEach { category ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { onAuthorOrCategoryClick(null, category) }
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            val hasSecondaryMeta = !entry.publisher.isNullOrBlank() || !entry.published.isNullOrBlank() || !entry.language.isNullOrBlank()
            if (hasSecondaryMeta) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        entry.publisher?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.publisher), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        entry.published?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.published), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val cleanDate = it.substringBefore("T")
                                Text(cleanDate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                        entry.language?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it.uppercase(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (!entry.summary.isNullOrBlank()) {
                Text(stringResource(R.string.synopsis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val cleanSummary = remember(entry.summary) {
                    val preProcessed = entry.summary
                        .replace("<br>", "\n")
                        .replace("</p>", "\n\n")
                    Jsoup.parse(preProcessed).text().trim()
                }

                Text(
                    text = cleanSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}