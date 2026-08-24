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
@file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.opds.OpdsAcquisition
import com.aryan.reader.opds.OpdsCatalog
import com.aryan.reader.opds.OpdsDownloadState
import com.aryan.reader.opds.OpdsEntry
import com.aryan.reader.opds.OpdsRepository
import com.aryan.reader.opds.OpdsViewModel
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.ui.SharedAnnotationExportFormatDialog
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterChips
import com.aryan.reader.shared.ui.SharedMobileLibraryBookListCardFrame
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterDialog
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterLabels
import com.aryan.reader.shared.ui.SharedMobileLibrarySearchTopBar
import com.aryan.reader.shared.ui.SharedMobileLibrarySortControl
import com.aryan.reader.shared.ui.SharedMobileShelfListCardFrame
import com.aryan.reader.shared.ui.sharedAnnotationExportFormatOptions
import com.aryan.reader.shared.opds.SharedOpdsLocalBookMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun getBookCountString(count: Int): String {
    return pluralStringResource(id = R.plurals.book_count, count, count)
}

@UnstableApi
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    val compStart = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        ReaderPerfLog.d("LibraryScreen initial composition ${System.currentTimeMillis() - compStart}ms")
    }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val isContextualModeActive = selectedItems.isNotEmpty()
    val selectedShelves = uiState.contextualActionShelfIds
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    val sortOrder = uiState.sortOrder
    val shelves = uiState.shelves
    val rawLibraryFiles = uiState.rawLibraryFiles
    val tabTitles = remember {
        buildList {
            add(context.getString(R.string.tab_all_books))
            add(context.getString(R.string.tab_shelves))
            add(context.getString(R.string.tab_folders))
            if (!BuildConfig.IS_OFFLINE) {
                add(context.getString(R.string.tab_catalogs))
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = uiState.libraryScreenStartPage,
        pageCount = { tabTitles.size }
    )

    val containsFolderItems = selectedItems.any { it.sourceFolderUri != null }

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
        val mimeTypes = if (uiState.useStrictFileFilter) MainViewModel.SUPPORTED_MIME_TYPES else arrayOf("*/*")
        try {
            pickFileLauncher.launch(mimeTypes)
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
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                viewModel.setLibraryScreenPage(page)
            }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteShelvesDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingSaveOriginalItem by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingAnnotationExportText by remember { mutableStateOf<String?>(null) }
    var showAnnotationExportFormatDialogFor by remember { mutableStateOf<RecentFileItem?>(null) }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingSaveOriginalItem
        pendingSaveOriginalItem = null
        if (uri != null && item?.uriString != null) {
            viewModel.saveOriginalFile(item.uriString.toUri(), uri)
        }
    }

    fun saveOriginalItem(item: RecentFileItem) {
        if (!item.canExportOriginalFile()) return
        pendingSaveOriginalItem = item
        saveOriginalLauncher.launch(item.suggestedOriginalFileName())
    }

    val saveMarkdownAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.MARKDOWN.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    val saveTextAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.TEXT.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    val saveJsonAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.JSON.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    val saveCsvAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.CSV.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    fun exportAnnotationsItem(item: RecentFileItem, format: AnnotationExportFormat) {
        viewModel.prepareAnnotationExport(item, format) { prepared ->
            pendingAnnotationExportText = prepared.contents
            when (format) {
                AnnotationExportFormat.MARKDOWN -> saveMarkdownAnnotationsLauncher.launch(prepared.fileName)
                AnnotationExportFormat.TEXT -> saveTextAnnotationsLauncher.launch(prepared.fileName)
                AnnotationExportFormat.JSON -> saveJsonAnnotationsLauncher.launch(prepared.fileName)
                AnnotationExportFormat.CSV -> saveCsvAnnotationsLauncher.launch(prepared.fileName)
            }
        }
    }

    fun shareOriginalItem(item: RecentFileItem) {
        val uriString = item.uriString ?: return
        if (!item.canExportOriginalFile()) return
        scope.launch {
            viewModel.shareOriginalFile(
                activityContext = context,
                sourceUri = uriString.toUri(),
                fileType = item.type,
                filename = item.suggestedOriginalFileName()
            )
        }
    }

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
            tabTitles = tabTitles,
            recentFiles = uiState.allRecentFiles,
            rawLibraryFiles = rawLibraryFiles,
            shelves = shelves,
            selectedItems = selectedItems,
            selectedShelves = selectedShelves,
            sortOrder = sortOrder,
            libraryFilters = uiState.libraryFilters,
            allTags = uiState.allTags,
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
            onTagClick = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
            onAddToShelfClick = { viewModel.openAddSelectedToShelf(selectedItems.map { it.bookId }.toSet()) },
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
            onSaveClick = selectedItems.singleOrNull()
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { saveOriginalItem(item) } },
            onShareClick = selectedItems.singleOrNull()
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { shareOriginalItem(item) } },
            onExportAnnotationsClick = selectedItems.singleOrNull()
                ?.let { item -> { showAnnotationExportFormatDialogFor = item } },
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
            onFolderLocalSyncChange = viewModel::setFolderLocalSyncEnabled,
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
            onDeleteCatalogStreams = viewModel::deleteStreamedBooksForCatalog,
            onShowBanner = viewModel::showBanner,
            onSettingsClick = { navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.SETTINGS) },
            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
        )


        showAnnotationExportFormatDialogFor?.let { item ->
            SharedAnnotationExportFormatDialog(
                title = stringResource(R.string.dialog_export_annotations_title),
                cancelLabel = stringResource(R.string.action_cancel),
                options = sharedAnnotationExportFormatOptions(
                    markdownLabel = stringResource(R.string.export_annotations_markdown),
                    markdownDescription = stringResource(R.string.export_annotations_markdown_description),
                    textLabel = stringResource(R.string.export_annotations_text),
                    textDescription = stringResource(R.string.export_annotations_text_description),
                    jsonLabel = stringResource(R.string.export_annotations_json),
                    jsonDescription = stringResource(R.string.export_annotations_json_description),
                    csvLabel = stringResource(R.string.export_annotations_csv),
                    csvDescription = stringResource(R.string.export_annotations_csv_description)
                ),
                onDismiss = { showAnnotationExportFormatDialogFor = null },
                onExport = { format ->
                    showAnnotationExportFormatDialogFor = null
                    exportAnnotationsItem(item, format)
                }
            )
        }
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
                allTags = uiState.allTags,
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

        HydratedFileInfoDialog(
            item = itemForInfoDialog,
            isVisible = showInfoDialog,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showInfoDialog = false
                itemForInfoDialog = null
            },
            onOpenTags = { bookId -> viewModel.openTagSelection(setOf(bookId)) }
        )
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@UnstableApi
@Composable
fun ShelfScreen(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val viewingShelfId = uiState.viewingShelfId
    val isAddingBooks = uiState.isAddingBooksToShelf
    val shelves = uiState.shelves
    val sortOrder = uiState.sortOrder
    val showRenameDialogFor = uiState.showRenameShelfDialogFor
    val showDeleteDialogFor = uiState.showDeleteShelfDialogFor
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRemoveFromShelfDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingSaveOriginalItem by remember { mutableStateOf<RecentFileItem?>(null) }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingSaveOriginalItem
        pendingSaveOriginalItem = null
        if (uri != null && item?.uriString != null) {
            viewModel.saveOriginalFile(item.uriString.toUri(), uri)
        }
    }

    fun saveOriginalItem(item: RecentFileItem) {
        if (!item.canExportOriginalFile()) return
        pendingSaveOriginalItem = item
        saveOriginalLauncher.launch(item.suggestedOriginalFileName())
    }


    fun shareOriginalItem(item: RecentFileItem) {
        val uriString = item.uriString ?: return
        if (!item.canExportOriginalFile()) return
        scope.launch {
            viewModel.shareOriginalFile(
                activityContext = context,
                sourceUri = uriString.toUri(),
                fileType = item.type,
                filename = item.suggestedOriginalFileName()
            )
        }
    }

    BackHandler(enabled = true) {
        when {
            selectedItems.isNotEmpty() -> viewModel.clearContextualAction()
            isAddingBooks -> viewModel.dismissAddBooksToShelf()
            else -> viewModel.navigateBackFromShelf()
        }
    }

    val currentShelf = shelves.find { it.id == viewingShelfId }
    val childShelves = remember(shelves, currentShelf) {
        currentShelf?.childShelfIds?.mapNotNull { childId -> shelves.find { it.id == childId } } ?: emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewingShelfId != null && currentShelf != null) {
            if (isAddingBooks) {
                AddBooksModeScreen(
                    shelfName = currentShelf.name,
                    availableBooks = uiState.booksAvailableForAdding,
                    selectedBookUris = uiState.booksSelectedForAdding,
                    currentSource = uiState.addBooksSource,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onSourceChange = viewModel::setAddBooksSource,
                    onBookClick = { item -> viewModel.toggleBookSelectionForAdding(item.bookId) },
                    onBack = viewModel::dismissAddBooksToShelf,
                    onAddSelectedBooks = { viewModel.addBooksToShelf(viewingShelfId) },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
                )
            } else {
                ShelfDetailScreen(
                    shelf = currentShelf,
                    childShelves = childShelves,
                    selectedItems = selectedItems,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onBack = viewModel::navigateBackFromShelf,
                    onAddBooksClick = viewModel::showAddBooksToShelf,
                    onChildShelfClick = viewModel::onShelfClick,
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress,
                    onClearSelection = viewModel::clearContextualAction,
                    onTagClick = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
                    onInfoClick = {
                        if (selectedItems.size == 1) {
                            itemForInfoDialog = selectedItems.first()
                            showInfoDialog = true
                        }
                    },
                    onSaveClick = selectedItems.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { item -> { saveOriginalItem(item) } },
                    onShareClick = selectedItems.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { item -> { shareOriginalItem(item) } },
                    onDeleteClick = { showRemoveFromShelfDialog = true },
                    onRenameShelf = { viewModel.showRenameShelfDialog(currentShelf.id) },
                    onDeleteShelf = { viewModel.showDeleteShelfDialog(currentShelf.id) },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
                )
            }
        }

        if (showRenameDialogFor != null) {
            val shelfToRename = shelves.find { it.id == showRenameDialogFor }
            if (shelfToRename != null) {
                RenameShelfDialog(
                    initialName = shelfToRename.name,
                    onConfirm = { newName -> viewModel.renameShelf(showRenameDialogFor, newName) },
                    onDismiss = viewModel::dismissRenameShelfDialog
                )
            }
        }

        if (showDeleteDialogFor != null) {
            DeleteShelfConfirmationDialog(
                shelfName = shelves.find { it.id == showDeleteDialogFor }?.name ?: "",
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

        HydratedFileInfoDialog(
            item = itemForInfoDialog,
            isVisible = showInfoDialog,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showInfoDialog = false; itemForInfoDialog = null },
            onOpenTags = { bookId -> viewModel.openTagSelection(setOf(bookId)) }
        )
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@Suppress("unused")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreenContent(
    tabTitles: List<String>,
    recentFiles: List<RecentFileItem>,
    rawLibraryFiles: List<RecentFileItem>,
    shelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    selectedShelves: Set<String>,
    sortOrder: SortOrder,
    libraryFilters: LibraryFilters,
    allTags: List<TagEntity>,
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
    onTagClick: () -> Unit,
    onAddToShelfClick: () -> Unit,
    onPinClick: () -> Unit,
    onClearSelection: () -> Unit,
    onItemClick: (RecentFileItem) -> Unit,
    onItemLongClick: (RecentFileItem) -> Unit,
    onInfoClick: () -> Unit,
    onSaveClick: (() -> Unit)?,
    onShareClick: (() -> Unit)?,
    onExportAnnotationsClick: (() -> Unit)?,
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
    onFolderLocalSyncChange: (SyncedFolder, Boolean, Boolean) -> Unit,
    onOpdsBookDownloaded: (Uri, String) -> Unit,
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    onShowBanner: (String) -> Unit,
    onSettingsClick: () -> Unit,
    usePdfFileNameAsDisplayName: Boolean,
) {
    val selectedBookIds = remember(selectedItems) { selectedItems.mapTo(mutableSetOf()) { it.bookId } }
    com.aryan.reader.shared.ui.SharedAndroidLibraryScaffold(
        pagerState = pagerState,
        scope = scope,
        tabTitles = tabTitles,
        hasBookSelection = selectedItems.isNotEmpty(),
        hasShelfSelection = selectedShelves.isNotEmpty(),
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        showAddFileFab = recentFiles.isNotEmpty(),
        strings = com.aryan.reader.shared.ui.SharedAndroidLibraryScaffoldStrings(
            title = stringResource(R.string.library_title),
            searchPlaceholder = stringResource(R.string.search_placeholder),
            closeSearchDescription = stringResource(R.string.content_desc_close_search),
            clearQueryDescription = stringResource(R.string.content_desc_clear_query),
            addFile = stringResource(R.string.fab_add_file),
            newShelf = stringResource(R.string.fab_new_shelf),
        ),
        onSearchQueryChange = onSearchQueryChange,
        onSearchActiveChange = onSearchActiveChange,
        onSelectFile = onSelectFileClick,
        onNewShelf = onNewShelfClick,
        onTabAnimationStarted = { index, title -> ReaderPerfLog.d("LibraryPager click page=$index title=$title") },
        onTabAnimationFinished = { index, start -> ReaderPerfLog.d("LibraryPager settled page=$index elapsed=${ReaderPerfLog.elapsedMs(start)}ms") },
        nowNanos = ReaderPerfLog::nowNanos,
        bookContextualTopBar = {
            ContextualTopAppBar(
                selectedItemCount = selectedItems.size,
                onNavIconClick = onClearSelection,
                onTagClick = onTagClick,
                onAddToShelfClick = onAddToShelfClick,
                onPinClick = onPinClick,
                onInfoClick = onInfoClick,
                onSaveClick = onSaveClick,
                onShareClick = onShareClick,
                onExportAnnotationsClick = onExportAnnotationsClick,
                onDeleteClick = onDeleteClick,
                onSelectAllClick = onSelectAllClick,
                compactSelectionActions = true,
                onClearSelectionClick = onClearSelection,
            )
        },
        shelfContextualTopBar = {
            ContextualTopAppBar(
                selectedItemCount = selectedShelves.size,
                onNavIconClick = onClearShelfSelection,
                onDeleteClick = onDeleteShelves,
            )
        },
        normalTopBarActions = {
            if (pagerState.currentPage == 0) {
                IconButton(onClick = onFilterClick) { Icon(Icons.Default.FilterList, stringResource(R.string.content_desc_filter)) }
                SharedMobileLibrarySortControl(
                    sortOrder = sortOrder,
                    labels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
                    selectedContentDescription = stringResource(R.string.content_desc_selected),
                    onSortOrderChange = onSortOrderChange,
                    modifier = Modifier.testTag("LibrarySortButton"),
                    icon = { Icon(painterResource(R.drawable.sort), stringResource(R.string.content_desc_sort), Modifier.size(20.dp)) },
                )
                IconButton(onClick = { onSearchActiveChange(true) }) { Icon(Icons.Default.Search, stringResource(R.string.action_search)) }
            }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        },
        filterChips = {
            androidx.compose.animation.AnimatedVisibility(visible = libraryFilters.isActive && pagerState.currentPage == 0) {
                val selectedTags = allTags.filter { it.id in libraryFilters.tagIds }
                val tagLabel = when {
                    selectedTags.isEmpty() -> pluralStringResource(R.plurals.tag_count, libraryFilters.tagIds.size, libraryFilters.tagIds.size)
                    selectedTags.size <= 2 -> selectedTags.joinToString { it.name }
                    else -> pluralStringResource(R.plurals.tag_count, selectedTags.size, selectedTags.size)
                }
                SharedMobileLibraryFilterChips(
                    filters = libraryFilters,
                    fileTypesLabel = stringResource(R.string.filter_types, libraryFilters.fileTypes.joinToString { it.name }),
                    foldersLabel = stringResource(R.string.filter_folders, libraryFilters.sourceFolders.size),
                    statusLabel = stringResource(R.string.filter_status, stringResource(libraryFilters.readStatus.labelRes)),
                    tagsLabel = stringResource(R.string.filter_tags, tagLabel),
                    clearContentDescription = stringResource(R.string.action_clear),
                    onRemoveFilters = onRemoveFilter,
                )
            }
        },
        pageContent = { page ->
            when (page) {
                0 -> when {
                    recentFiles.isEmpty() && searchQuery.isNotEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_results_found, searchQuery))
                    }
                    recentFiles.isEmpty() -> EmptyState(
                        title = stringResource(R.string.your_library_empty),
                        message = stringResource(R.string.library_empty_desc),
                        onSelectFileClick = onSelectFileClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recentFiles, key = { it.bookId }) { item ->
                            LibraryListItem(
                                item = item,
                                isSelected = item.bookId in selectedBookIds,
                                isPinned = item.bookId in pinnedLibraryBookIds,
                                onItemClick = { onItemClick(item) },
                                onItemLongClick = { onItemLongClick(item) },
                                isDownloading = item.bookId in downloadingBookIds,
                                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                            )
                        }
                    }
                }
                1 -> ShelvesScreen(shelves, onShelfClick, onShelfLongClick, selectedShelves)
                2 -> FolderSyncScreen(
                    syncedFolders, rawLibraryFiles, onSelectSyncFolderClick, onRemoveFolderClick,
                    onFolderLocalSyncChange, onEditFolderFiltersClick, onScanNowClick, onSyncMetadataClick,
                    isLoading || isRefreshing,
                )
                3 -> if (!BuildConfig.IS_OFFLINE) OpdsTab(
                    localLibraryFiles = rawLibraryFiles,
                    onBookDownloaded = onOpdsBookDownloaded,
                    onReadBook = onItemClick,
                    onStreamBook = onStreamOpdsBook,
                    onDeleteCatalogStreams = onDeleteCatalogStreams,
                    onShowBanner = onShowBanner,
                    syncedFolders = syncedFolders,
                )
            }
        },
    )
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    onShelfClick: (Shelf) -> Unit,
    onShelfLongClick: (Shelf) -> Unit,
    selectedShelves: Set<String>,
) {
    com.aryan.reader.shared.ui.SharedAndroidLibraryShelves(
        shelves = shelves,
        selectedShelfIds = selectedShelves,
        shelfId = { it.id },
        shelfName = { it.name },
        isVisibleTagShelf = { it.type == ShelfType.TAG && it.bookCount > 0 },
        isVisibleRootShelf = { it.type != ShelfType.TAG && (it.type != ShelfType.FOLDER || it.parentShelfId == null) },
        browseByTagTitle = stringResource(R.string.section_browse_by_tag),
        tagIcon = painterResource(R.drawable.tag),
        onShelfClick = onShelfClick,
        shelfRow = { shelf, selected ->
            ShelfListItem(
                shelf = shelf,
                isSelected = selected,
                onItemClick = { onShelfClick(shelf) },
                onItemLongClick = { onShelfLongClick(shelf) },
            )
        },
    )
}

@Composable
private fun CreateShelfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    com.aryan.reader.shared.ui.SharedCreateShelfDialog(
        title = stringResource(R.string.create_new_shelf),
        namePlaceholder = stringResource(R.string.shelf_name_hint),
        createLabel = stringResource(R.string.action_create),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ShelfDetailScreen(
    shelf: Shelf,
    childShelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onBack: () -> Unit,
    onAddBooksClick: () -> Unit,
    onChildShelfClick: (Shelf) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
    onClearSelection: () -> Unit,
    onTagClick: () -> Unit,
    onInfoClick: () -> Unit,
    onSaveClick: (() -> Unit)?,
    onShareClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    onRenameShelf: () -> Unit,
    onDeleteShelf: () -> Unit,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
) {
    val isFolderShelf = shelf.type == ShelfType.FOLDER
    val strings = sharedAndroidShelfScreenStrings()
    com.aryan.reader.shared.ui.SharedAndroidShelfDetailScreen(
        shelfId = shelf.id,
        shelfName = shelf.name,
        shelfSubtitle = when {
            isFolderShelf && shelf.childShelfCount > 0 && shelf.directBookCount > 0 -> stringResource(
                R.string.folder_subtitle_folder_book_counts,
                pluralStringResource(R.plurals.folder_count, shelf.childShelfCount, shelf.childShelfCount),
                getBookCountString(shelf.directBookCount),
            )
            isFolderShelf && shelf.childShelfCount > 0 -> pluralStringResource(R.plurals.folder_count, shelf.childShelfCount, shelf.childShelfCount)
            isFolderShelf -> getBookCountString(shelf.directBookCount)
            else -> getBookCountString(shelf.bookCount)
        },
        isFolderShelf = isFolderShelf,
        canMutateShelf = shelf.type == ShelfType.MANUAL && shelf.id != "unshelved",
        childShelves = childShelves,
        directBooks = shelf.directBooks,
        selectedCount = selectedItems.size,
        sortOrder = sortOrder,
        strings = strings,
        childKey = { it.id },
        bookKey = { it.bookId },
        childMatchesQuery = { child, query ->
            child.name.contains(query, ignoreCase = true) || child.books.any { item -> item.matchesShelfQuery(query, includeTags = false) }
        },
        bookMatchesQuery = { item, query -> item.matchesShelfQuery(query, includeTags = true) },
        onSortOrderChange = onSortOrderChange,
        onBack = onBack,
        onAddBooks = onAddBooksClick,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        contextualTopBar = {
            ContextualTopAppBar(
                selectedItemCount = selectedItems.size,
                onNavIconClick = onClearSelection,
                onTagClick = onTagClick,
                onInfoClick = onInfoClick,
                onSaveClick = onSaveClick,
                onShareClick = onShareClick,
                onDeleteClick = onDeleteClick,
            )
        },
        childRow = { child -> ShelfListItem(child, false, { onChildShelfClick(child) }, {}, showHierarchyIndent = false) },
        bookRow = { item ->
            LibraryListItem(
                item = item,
                isSelected = selectedItems.any { it.bookId == item.bookId },
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookLongClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        sortIcon = { Icon(painterResource(R.drawable.sort), strings.sortDescription, Modifier.size(20.dp)) },
        platformBackHandler = { enabled, onBackHandler -> BackHandler(enabled = enabled, onBack = onBackHandler) },
    )
}

private fun RecentFileItem.matchesShelfQuery(query: String, includeTags: Boolean): Boolean =
    displayName.contains(query, ignoreCase = true) ||
        title?.contains(query, ignoreCase = true) == true ||
        author?.contains(query, ignoreCase = true) == true ||
        (includeTags && tags.any { it.name.contains(query, ignoreCase = true) })

@Composable
private fun sharedAndroidShelfScreenStrings(): com.aryan.reader.shared.ui.SharedAndroidShelfScreenStrings {
    val context = LocalContext.current
    return com.aryan.reader.shared.ui.SharedAndroidShelfScreenStrings(
        back = stringResource(R.string.action_back),
        closeSearch = stringResource(R.string.content_desc_close_search),
        clearQuery = stringResource(R.string.content_desc_clear_query),
        searchPlaceholder = stringResource(R.string.search_placeholder),
        sortDescription = stringResource(R.string.content_desc_sort),
        selectedDescription = stringResource(R.string.content_desc_selected),
        searchShelfDescription = stringResource(R.string.content_desc_search_shelf),
        moreOptionsDescription = stringResource(R.string.content_desc_more_options),
        renameShelf = stringResource(R.string.menu_rename_shelf),
        deleteShelf = stringResource(R.string.menu_delete_shelf),
        addBooks = stringResource(R.string.fab_add_books),
        emptyShelf = stringResource(R.string.shelf_empty),
        noResults = { context.getString(R.string.no_results_found, it) },
        foldersSection = stringResource(R.string.section_folders),
        filesSection = stringResource(R.string.section_files),
        addToShelfTitle = { context.getString(R.string.add_to_shelf, it) },
        addCount = { context.getString(R.string.fab_add_count, it) },
        noUnshelvedBooks = stringResource(R.string.no_unshelved_books),
        allBooksInShelf = stringResource(R.string.all_books_in_shelf),
        sortLabels = SortOrder.entries.associateWith { stringResource(it.labelRes) },
        sourceLabels = AddBooksSource.entries.associateWith { stringResource(it.labelRes) },
    )
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
    usePdfFileNameAsDisplayName: Boolean,
) {
    val strings = sharedAndroidShelfScreenStrings()
    com.aryan.reader.shared.ui.SharedAndroidAddBooksModeScreen(
        shelfName = shelfName,
        books = availableBooks,
        selectedCount = selectedBookUris.size,
        source = currentSource,
        sortOrder = sortOrder,
        strings = strings,
        bookKey = { it.bookId },
        onSortOrderChange = onSortOrderChange,
        onSourceChange = onSourceChange,
        onBack = onBack,
        onAddSelectedBooks = onAddSelectedBooks,
        bookRow = { item ->
            LibraryListItem(
                item = item,
                isSelected = item.bookId in selectedBookUris,
                onItemClick = { onBookClick(item) },
                onItemLongClick = { onBookClick(item) },
                isDownloading = item.bookId in downloadingBookIds,
                usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            )
        },
        sortIcon = { Icon(painterResource(R.drawable.sort), strings.sortDescription, Modifier.size(20.dp)) },
    )
}

@Composable
private fun ShelfCover(shelf: Shelf) {
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
            val topBook = shelf.topBook
            if (topBook != null) {
                ThemedBookCover(
                    item = topBook,
                    contentDescription = stringResource(R.string.content_desc_shelf_cover, shelf.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                EmptyShelfCover(
                    shelfName = shelf.name,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(coverWidth + (horizontalOffset * (booksForCovers.size - 1)))
                    .height(coverHeight)
            ) {
                booksForCovers.forEachIndexed { index, book ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(width = coverWidth, height = coverHeight)
                            .align(Alignment.CenterEnd)
                            .offset(x = -horizontalOffset * index)
                    ) {
                        ThemedBookCover(
                            item = book,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfCover(
    shelfName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shelfName.takeIf { it.isNotBlank() } ?: stringResource(R.string.tab_shelves),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun ShelfListItem(
    shelf: Shelf,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    showHierarchyIndent: Boolean = true,
) {
    val folderIndent = if (showHierarchyIndent && shelf.type == ShelfType.FOLDER) {
        (shelf.depth * 14).dp
    } else {
        0.dp
    }
    SharedMobileShelfListCardFrame(
        isSelected = isSelected,
        contentStartIndent = folderIndent,
        onClick = onItemClick,
        onLongClick = {
            if (shelf.name != "Unshelved") {
                onItemLongClick()
            }
        },
        modifier = Modifier.testTag("ShelfItem_${shelf.id}"),
    ) {
        ShelfCover(shelf = shelf)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (shelf.type) {
                    ShelfType.SMART -> Icons.Default.Star
                    ShelfType.TAG -> Icons.AutoMirrored.Filled.LibraryBooks
                    ShelfType.FOLDER -> Icons.Default.Folder
                    ShelfType.SERIES -> Icons.AutoMirrored.Filled.LibraryBooks
                    ShelfType.MANUAL -> Icons.AutoMirrored.Filled.List
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = shelf.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = getBookCountString(shelf.bookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
@Composable
internal fun LibraryListItem(
    item: RecentFileItem,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    isDownloading: Boolean,
    usePdfFileNameAsDisplayName: Boolean = false,
) {
    SharedMobileLibraryBookListCardFrame(
        isAvailable = item.isAvailable,
        isSelected = isSelected,
        onClick = onItemClick,
        onLongClick = onItemLongClick,
        modifier = Modifier.testTag("LibraryBookItem_${item.bookId}"),
        cover = {
            ThemedBookCover(
                item = item,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier.matchParentSize().background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.content_desc_selected),
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.cardTitle(usePdfFileNameAsDisplayName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.cardAuthor(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sourceFolderUri != null || item.isOpdsStream() || isPinned) {
                FileStatusBadges(item = item, isPinned = isPinned)
            }
        },
        metadata = {
            FileTypeBadge(type = item.type, overlay = false)
            if (item.tags.isNotEmpty()) {
                BookTagChipsRow(
                    tags = item.tags,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!item.isAvailable) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDownloading) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (isDownloading) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.not_available_locally),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = if (isDownloading) {
                                stringResource(R.string.status_downloading)
                            } else {
                                stringResource(R.string.not_available_locally)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        progress = {
            ReadingProgressSection(
                progressPercentage = item.progressPercentage,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
@Composable
private fun RenameShelfDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.aryan.reader.shared.ui.SharedRenameShelfDialog(
        initialName = initialName,
        title = stringResource(R.string.menu_rename_shelf),
        namePlaceholder = stringResource(R.string.shelf_name_hint),
        confirmLabel = stringResource(R.string.action_rename),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DeleteShelfConfirmationDialog(
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.aryan.reader.shared.ui.SharedShelfConfirmationDialog(
        title = stringResource(R.string.dialog_delete_shelf),
        body = stringResource(R.string.dialog_delete_shelf_desc, shelfName),
        confirmLabel = stringResource(R.string.action_delete),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RemoveFromShelfConfirmationDialog(
    count: Int,
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.aryan.reader.shared.ui.SharedShelfConfirmationDialog(
        title = stringResource(R.string.dialog_remove_from_shelf),
        body = pluralStringResource(R.plurals.dialog_remove_from_shelf_desc, count, count, shelfName),
        confirmLabel = stringResource(R.string.action_remove),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
internal fun FolderSyncScreen(
    syncedFolders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean, Boolean) -> Unit,
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val folderStatsByUri = remember(allRecentFiles) {
        allRecentFiles.asSequence()
            .filter { it.sourceFolderUri != null }
            .groupBy { it.sourceFolderUri!! }
            .mapValues { (_, files) ->
                com.aryan.reader.shared.ui.SharedAndroidFolderStats(
                    totalBooks = files.size,
                    countsByType = files.groupingBy { it.type }.eachCount(),
                )
            }
    }
    com.aryan.reader.shared.ui.SharedAndroidFolderSyncScreen(
        folders = syncedFolders,
        statsByFolderUri = folderStatsByUri,
        syncableFileTypes = ANDROID_SYNCABLE_FILE_TYPES.toList(),
        isLoading = isLoading,
        strings = com.aryan.reader.shared.ui.SharedAndroidFolderSyncStrings(
            addFolder = stringResource(R.string.fab_add_folder),
            addDescription = "Add",
            scanning = stringResource(R.string.scanning),
            scanAll = stringResource(R.string.scan_all),
            syncMetadata = stringResource(R.string.sync_meta),
            emptyTitle = stringResource(R.string.sync_local_folders),
            emptyMessage = stringResource(R.string.sync_folders_desc),
            selectFolder = stringResource(R.string.action_select_folder),
            localSyncDisabled = stringResource(R.string.folder_local_sync_disabled),
            optionsDescription = "Options",
            editFilters = stringResource(R.string.menu_edit_filters),
            disableLocalSync = stringResource(R.string.menu_disable_folder_local_sync),
            enableLocalSync = stringResource(R.string.menu_enable_folder_local_sync),
            removeFolder = stringResource(R.string.menu_remove_folder),
            lastSync = stringResource(R.string.last_sync),
            never = stringResource(R.string.never),
            booksCount = stringResource(R.string.books_count),
            filterCount = { type, count -> context.getString(R.string.folder_filter_count, type.name, count) },
            filterFileTypes = stringResource(R.string.filter_file_types),
            filterFileTypesDescription = stringResource(R.string.filter_file_types_desc),
            save = stringResource(R.string.action_save),
            cancel = stringResource(R.string.action_cancel),
            disableDialogTitle = stringResource(R.string.dialog_disable_folder_local_sync_title),
            disableDialogDescription = stringResource(R.string.dialog_disable_folder_local_sync_desc, LOCAL_FOLDER_SYNC_DATA_DIR),
            disableRemoveData = stringResource(R.string.action_disable_remove_sync_data),
            disableKeepData = stringResource(R.string.action_disable_keep_sync_data),
        ),
        onAddFolder = onAddFolderClick,
        onRemoveFolder = onRemoveFolderClick,
        onLocalSyncChange = onFolderLocalSyncChange,
        onFileTypesChange = onEditFolderFiltersClick,
        onScanAll = onScanNowClick,
        onSyncMetadata = onSyncMetadataClick,
        formatLastScan = { dateFormat.format(Date(it)) },
        syncIcon = { Icon(painterResource(R.drawable.sync), null, Modifier.size(18.dp)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    allTags: List<TagEntity>,
    syncedFolders: List<SyncedFolder>,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    SharedMobileLibraryFilterDialog(
        filters = filters,
        allTags = allTags.map { com.aryan.reader.shared.Tag(it.id, it.name, it.color) },
        syncedFolders = syncedFolders,
        readableFileTypes = ANDROID_READABLE_FILE_TYPES,
        fileTypeLabels = ANDROID_READABLE_FILE_TYPES.associateWith { it.name },
        readStatusLabels = ReadStatusFilter.entries.associateWith { stringResource(it.labelRes) },
        labels = SharedMobileLibraryFilterLabels(
            title = stringResource(R.string.filter_library),
            fileType = stringResource(R.string.filter_file_type),
            sourceFolder = stringResource(R.string.filter_source_folder),
            inAppStorage = stringResource(R.string.filter_in_app_storage),
            readStatus = stringResource(R.string.filter_read_status),
            tags = stringResource(R.string.section_tags),
            clearAll = stringResource(R.string.clear_all),
            apply = stringResource(R.string.action_apply),
        ),
        onApply = onApply,
        onDismiss = onDismiss,
    )
}

@Composable
fun OpdsTab(
    localLibraryFiles: List<RecentFileItem>,
    onBookDownloaded: (Uri, String) -> Unit,
    onReadBook: (RecentFileItem) -> Unit,
    onStreamBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    onShowBanner: (String) -> Unit,
    syncedFolders: List<SyncedFolder>,
    opdsViewModel: OpdsViewModel = viewModel()
) {
    val uiState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coverImageLoader = rememberOpdsCoverImageLoader(uiState.currentCatalog)
    val sharedLibraryBooks = remember(localLibraryFiles) {
        localLibraryFiles.map(RecentFileItem::toSharedBookItem)
    }

    BackHandler(enabled = uiState.isViewingCatalog) {
        opdsViewModel.navigateBack()
    }

    com.aryan.reader.shared.ui.SharedOpdsScreen(
        state = uiState,
        localLibraryBooks = sharedLibraryBooks,
        onOpenCatalog = opdsViewModel::openCatalog,
        onOpenFeedUrl = opdsViewModel::openFeedUrl,
        onNavigateBack = opdsViewModel::navigateBack,
        onSearch = opdsViewModel::search,
        onLoadNextPage = opdsViewModel::loadNextPage,
        onAddCatalog = opdsViewModel::addCatalog,
        onUpdateCatalog = opdsViewModel::updateCatalog,
        onRemoveCatalog = { opdsViewModel.removeCatalog(it.id) },
        onDeleteCatalogStreams = onDeleteCatalogStreams,
        onDownloadBook = { entry, acquisition ->
            opdsViewModel.downloadBook(
                entry, acquisition, context,
                onDownloaded = { downloadedUri ->
                    onBookDownloaded(downloadedUri, entry.title)
                },
                onDownloadedToFolder = { folderName ->
                    onShowBanner(context.getString(R.string.banner_downloaded_to_folder, entry.title, folderName))
                }
            )
        },
        onDownloadLocationChange = opdsViewModel::setDownloadLocation,
        syncedFolders = syncedFolders,
        onReadBook = { sharedBook ->
            localLibraryFiles.firstOrNull { it.bookId == sharedBook.id }?.let(onReadBook)
        },
        onStreamBook = onStreamBook,
        onClearError = opdsViewModel::clearError,
        coverContent = { entry, modifier ->
            AsyncImage(
                model = entry.coverUrl,
                contentDescription = null,
                imageLoader = coverImageLoader,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        mobileLayout = true,
    )
}

@Composable
private fun rememberOpdsCoverImageLoader(catalog: OpdsCatalog?): ImageLoader {
    val context = LocalContext.current.applicationContext
    val username = catalog?.username
    val password = catalog?.password
    val imageLoader = remember(context, username, password) {
        ImageLoader.Builder(context)
            .okHttpClient {
                OpdsRepository.sharedHttpClient.newBuilder()
                    .authenticator(OpdsRepository.OpdsAuthenticator(username, password))
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }
    return imageLoader
}
