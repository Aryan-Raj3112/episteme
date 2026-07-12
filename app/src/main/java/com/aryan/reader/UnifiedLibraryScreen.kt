package com.aryan.reader

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.AnnotationExportFormat
import kotlinx.coroutines.launch

/** Android-only successor experiment for the separate Home and Library destinations. */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedLibraryScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val accountDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(UnifiedLibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showLibraryControls by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showPermanentDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSelectedBookInfo by rememberSaveable { mutableStateOf(false) }
    var selectedBookForInfo by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingSaveOriginalItem by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingAnnotationExportText by remember { mutableStateOf<String?>(null) }
    var showAnnotationExportFormatDialogFor by remember { mutableStateOf<RecentFileItem?>(null) }

    val filePicker = rememberFilePickerLauncher(viewModel::onFilesSelected)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addSyncedFolder)
    }
    val saveOriginalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination ->
        val item = pendingSaveOriginalItem
        pendingSaveOriginalItem = null
        if (destination != null && item?.uriString != null) {
            viewModel.saveOriginalFile(item.uriString.toUri(), destination)
        }
    }
    val saveMarkdownAnnotationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AnnotationExportFormat.MARKDOWN.mimeType)
    ) { destination ->
        val contents = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (destination != null && contents != null) viewModel.saveAnnotationExport(contents, destination)
    }
    val saveTextAnnotationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AnnotationExportFormat.TEXT.mimeType)
    ) { destination ->
        val contents = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (destination != null && contents != null) viewModel.saveAnnotationExport(contents, destination)
    }

    fun exportAnnotations(item: RecentFileItem, format: AnnotationExportFormat) {
        viewModel.prepareAnnotationExport(item, format) { prepared ->
            pendingAnnotationExportText = prepared.contents
            when (format) {
                AnnotationExportFormat.MARKDOWN -> saveMarkdownAnnotationsLauncher.launch(prepared.fileName)
                AnnotationExportFormat.TEXT -> saveTextAnnotationsLauncher.launch(prepared.fileName)
            }
        }
    }

    fun shareOriginal(item: RecentFileItem) {
        val sourceUri = item.uriString?.toUri() ?: return
        if (!item.canExportOriginalFile()) return
        scope.launch {
            viewModel.shareOriginalFile(
                activityContext = context,
                sourceUri = sourceUri,
                fileType = item.type,
                filename = item.suggestedOriginalFileName()
            )
        }
    }
    val visibleBooks = remember(uiState.rawLibraryFiles, uiState.libraryFilters, filter, query, uiState.sortOrder) {
        sortFiles(
            filterUnifiedLibraryBooks(
                applyLibraryFilters(uiState.rawLibraryFiles, uiState.libraryFilters),
                filter,
                query
            ),
            uiState.sortOrder
        )
    }
    val section = UnifiedLibrarySection.fromPersisted(uiState.unifiedLibrarySection)
    // Do not cache this: reader position writes replace the matching library item.
    // Use the unfiltered collection so an active library filter never hides resume.
    val continueReading = findContinueReadingBook(uiState.rawLibraryFiles)
    val advancedFilterCount = uiState.libraryFilters.selectedFilterCount()
    val selectedItems = uiState.contextualActionItems

    BackHandler(enabled = selectedItems.isNotEmpty() || selectedShelfId != null) {
        if (selectedItems.isNotEmpty()) {
            viewModel.clearContextualAction()
        } else {
            selectedShelfId = null
        }
    }

    // Material drawers are leading by default. Mirroring only the outer drawer
    // makes this account surface open from the right while the app content stays LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = accountDrawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val accountContext = LocalContext.current
                    AppDrawerContent(
                        uiState = uiState,
                        onSignInClick = {
                            accountContext.findActivity()?.let(viewModel::signIn)
                                ?: viewModel.showBanner("Unable to start sign in on this screen.", isError = true)
                            scope.launch { accountDrawerState.close() }
                        },
                        onSignOutClick = { showSignOutConfirmation = true },
                        onSyncToggle = viewModel::setSyncEnabled,
                        onUpgradeClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(AppDestinations.PRO_SCREEN_ROUTE)
                        },
                        onSyncUpsellClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(AppDestinations.PRO_SCREEN_ROUTE)
                        },
                        onFontsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(AppDestinations.FONTS_SCREEN_ROUTE)
                        },
                        onAiSettingsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(AppDestinations.AI_SETTINGS_SCREEN_ROUTE)
                        },
                        onSettingsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(AppDestinations.SETTINGS_SCREEN_ROUTE)
                        },
                        navController = navController,
                        onFolderSyncToggle = viewModel::setFolderSyncEnabled,
                        onAboutClick = {
                            scope.launch { accountDrawerState.close() }
                            showAboutDialog = true
                        },
                        showFonts = false,
                        showAiSettings = false,
                        showSupportProject = true
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            UnifiedLibraryDrawer(
                currentSection = section,
                onSectionSelected = { destination ->
                    selectedShelfId = null
                    scope.launch {
                        drawerState.close()
                        viewModel.setUnifiedLibrarySection(destination.persistedValue)
                    }
                },
                onThemeClick = {
                    scope.launch { drawerState.close() }
                    showThemeSheet = true
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigateIfReady(AppDestinations.SETTINGS_SCREEN_ROUTE)
                },
                onFontsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigateIfReady(AppDestinations.FONTS_SCREEN_ROUTE)
                },
                onAiSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigateIfReady(AppDestinations.AI_SETTINGS_SCREEN_ROUTE)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                if (selectedItems.isNotEmpty()) {
                    ContextualTopAppBar(
                        selectedItemCount = selectedItems.size,
                        onNavIconClick = viewModel::clearContextualAction,
                        onInfoClick = selectedItems.singleOrNull()?.let { item ->
                            {
                                selectedBookForInfo = item
                                showSelectedBookInfo = true
                            }
                        },
                        onTagClick = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
                        onAddToShelfClick = { viewModel.openAddSelectedToShelf(selectedItems.map { it.bookId }.toSet()) },
                        onPinClick = { viewModel.togglePinForContextualItems(isHome = false) },
                        onSelectAllClick = viewModel::selectAllLibraryFiles,
                        onSaveClick = selectedItems.singleOrNull()
                            ?.takeIf { it.canExportOriginalFile() }
                            ?.let { item ->
                                {
                                    pendingSaveOriginalItem = item
                                    saveOriginalLauncher.launch(item.suggestedOriginalFileName())
                                }
                            },
                        onShareClick = selectedItems.singleOrNull()
                            ?.takeIf { it.canExportOriginalFile() }
                            ?.let { item -> { shareOriginal(item) } },
                        onExportAnnotationsClick = selectedItems.singleOrNull()
                            ?.let { item -> { showAnnotationExportFormatDialogFor = item } },
                        onDeleteClick = { showPermanentDeleteConfirmation = true },
                        compactSelectionActions = true,
                        overflowDeleteLabelRes = R.string.action_delete,
                        onClearSelectionClick = viewModel::clearContextualAction
                    )
                } else {
                    UnifiedLibraryTopBar(
                        section = section,
                        selectedShelf = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackFromShelf = { selectedShelfId = null },
                        uiState = uiState,
                        onAccountClick = { scope.launch { accountDrawerState.open() } }
                    )
                }
            },
            floatingActionButton = {
                when (section) {
                    UnifiedLibrarySection.HOME -> FloatingActionButton(
                        onClick = { filePicker.launch(if (uiState.useStrictFileFilter) MainViewModel.SUPPORTED_MIME_TYPES else arrayOf("*/*")) },
                        modifier = Modifier.testTag("UnifiedLibraryImport")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.unified_library_import))
                    }
                    UnifiedLibrarySection.SHELVES -> if (selectedShelfId == null) {
                        ExtendedFloatingActionButton(
                            onClick = viewModel::showCreateShelfDialog,
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.fab_new_shelf)) }
                        )
                    }
                    UnifiedLibrarySection.FOLDERS -> Unit
                    UnifiedLibrarySection.CATALOGS -> Unit
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val direction = if (targetState.persistedValue > initialState.persistedValue) 1 else -1
                    (fadeIn() + slideInHorizontally { direction * it / 5 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -direction * it / 5 })
                },
                label = "UnifiedLibrarySharedAxis"
            ) { displayedSection ->
            when (displayedSection) {
                UnifiedLibrarySection.HOME -> UnifiedLibraryHome(
                    modifier = Modifier.padding(padding),
                    books = visibleBooks,
                    continueReading = continueReading,
                    filter = filter,
                    query = query,
                    isSearchVisible = isSearchVisible,
                    sortOrder = uiState.sortOrder,
                    advancedFilterCount = advancedFilterCount,
                    selectedBookIds = uiState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                    onFilterChange = { filter = it },
                    onQueryChange = { query = it },
                    onSearchToggle = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) query = ""
                    },
                    onControlsClick = { showLibraryControls = true },
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress
                )
                UnifiedLibrarySection.SHELVES -> UnifiedShelvesSection(
                    modifier = Modifier.padding(padding),
                    shelves = uiState.shelves,
                    selectedShelfId = selectedShelfId,
                    selectedBookIds = uiState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                    onShelfSelected = { selectedShelfId = it.id },
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress
                )
                UnifiedLibrarySection.FOLDERS -> UnifiedFoldersSection(
                    modifier = Modifier.padding(padding),
                    folders = uiState.syncedFolders,
                    allRecentFiles = uiState.rawLibraryFiles,
                    isLoading = uiState.isLoading,
                    onAddFolder = { folderPicker.launch(null) },
                    onScan = viewModel::scanSyncedFolder,
                    onSyncMetadata = viewModel::syncFolderMetadata,
                    onToggleLocalSync = viewModel::setFolderLocalSyncEnabled,
                    onEditFolderFilters = viewModel::updateFolderFilters,
                    onRemove = viewModel::removeSyncedFolder
                )
                UnifiedLibrarySection.CATALOGS -> if (!BuildConfig.IS_OFFLINE) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        OpdsTab(
                            localLibraryFiles = uiState.rawLibraryFiles,
                            onBookDownloaded = { uri: Uri, title: String ->
                                viewModel.showBanner(context.getString(R.string.banner_downloaded, title))
                                viewModel.onFileSelected(uri, isFromRecent = false)
                            },
                            onReadBook = viewModel::onRecentFileClicked,
                            onStreamBook = { entry, catalog ->
                                viewModel.streamOpdsBook(entry.id, entry.title, entry.pseUrlTemplate!!, entry.pseCount!!, catalog?.id)
                            },
                            onDeleteCatalogStreams = viewModel::deleteStreamedBooksForCatalog
                        )
                    }
                }
            }
            }
        }
    }
            }
        }
    }

    if (showLibraryControls) {
        UnifiedLibraryControlsSheet(
            currentFilter = filter,
            currentSortOrder = uiState.sortOrder,
            onFilterChanged = { filter = it },
            onSortChanged = viewModel::setSortOrder,
            onAdvancedFiltersClick = {
                showLibraryControls = false
                showAdvancedFilters = true
            },
            onDismiss = { showLibraryControls = false }
        )
    }
    if (showAdvancedFilters) {
        LibraryFilterSheet(
            filters = uiState.libraryFilters,
            allTags = uiState.allTags,
            syncedFolders = uiState.syncedFolders,
            onApply = { filters ->
                filter = filters.readStatus.toUnifiedLibraryFilter()
                viewModel.updateLibraryFilters(filters)
            },
            onDismiss = { showAdvancedFilters = false }
        )
    }
    if (showThemeSheet) {
        AppThemeBottomSheet(
            uiState = uiState,
            onThemeModeChanged = viewModel::setAppThemeMode,
            onContrastOptionChanged = viewModel::setAppContrastOption,
            onTextDimFactorLightChanged = viewModel::setAppTextDimFactorLight,
            onTextDimFactorDarkChanged = viewModel::setAppTextDimFactorDark,
            onSeedColorChanged = viewModel::setAppSeedColor,
            onCustomThemeAdded = viewModel::addCustomAppTheme,
            onCustomThemeDeleted = viewModel::deleteCustomAppTheme,
            onDismiss = { showThemeSheet = false }
        )
    }
    if (showSignOutConfirmation) {
        SignOutConfirmationDialog(
            onConfirm = {
                showSignOutConfirmation = false
                viewModel.signOut()
            },
            onDismiss = { showSignOutConfirmation = false }
        )
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showPermanentDeleteConfirmation) {
        DeleteConfirmationDialog(
            count = selectedItems.size,
            onConfirm = {
                viewModel.deleteContextualItemsPermanently()
                showPermanentDeleteConfirmation = false
            },
            onDismiss = { showPermanentDeleteConfirmation = false },
            isPermanentDelete = true,
            containsFolderItems = selectedItems.any { it.sourceFolderUri != null }
        )
    }
    showAnnotationExportFormatDialogFor?.let { item ->
        AlertDialog(
            onDismissRequest = { showAnnotationExportFormatDialogFor = null },
            title = { Text(stringResource(R.string.dialog_export_annotations_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showAnnotationExportFormatDialogFor = null
                        exportAnnotations(item, AnnotationExportFormat.MARKDOWN)
                    }) { Text(stringResource(R.string.export_annotations_markdown)) }
                    TextButton(onClick = {
                        showAnnotationExportFormatDialogFor = null
                        exportAnnotations(item, AnnotationExportFormat.TEXT)
                    }) { Text(stringResource(R.string.export_annotations_text)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAnnotationExportFormatDialogFor = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    HydratedFileInfoDialog(
        item = selectedBookForInfo,
        isVisible = showSelectedBookInfo,
        uiState = uiState,
        viewModel = viewModel,
        onDismiss = {
            showSelectedBookInfo = false
            selectedBookForInfo = null
        },
        onOpenTags = { bookId -> viewModel.openTagSelection(setOf(bookId)) }
    )
    if (uiState.showCreateShelfDialog) {
        UnifiedCreateShelfDialog(viewModel::createShelf, viewModel::dismissCreateShelfDialog)
    }
    CustomTopBanner(bannerMessage = uiState.bannerMessage)
}

private enum class UnifiedLibrarySection(val persistedValue: Int) {
    HOME(0),
    SHELVES(1),
    FOLDERS(2),
    CATALOGS(3);

    companion object {
        fun fromPersisted(value: Int): UnifiedLibrarySection =
            entries.firstOrNull { it.persistedValue == value } ?: HOME
    }
}

@Composable
private fun UnifiedLibraryDrawer(
    currentSection: UnifiedLibrarySection,
    onSectionSelected: (UnifiedLibrarySection) -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(bottomEnd = 28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        AsyncImage(
                            model = R.mipmap.ic_launcher,
                            contentDescription = stringResource(R.string.content_desc_app_icon),
                            modifier = Modifier.size(44.dp).padding(4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.unified_library_drawer_title), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
            )
            UnifiedLibraryDestination(stringResource(R.string.unified_library_home), currentSection == UnifiedLibrarySection.HOME, { Icon(Icons.Default.Home, null) }) { onSectionSelected(UnifiedLibrarySection.HOME) }
            UnifiedLibraryDestination(stringResource(R.string.tab_shelves), currentSection == UnifiedLibrarySection.SHELVES, { Icon(Icons.AutoMirrored.Filled.LibraryBooks, null) }) { onSectionSelected(UnifiedLibrarySection.SHELVES) }
            UnifiedLibraryDestination(stringResource(R.string.tab_folders), currentSection == UnifiedLibrarySection.FOLDERS, { Icon(Icons.Default.Folder, null) }) { onSectionSelected(UnifiedLibrarySection.FOLDERS) }
            if (!BuildConfig.IS_OFFLINE) {
                UnifiedLibraryDestination(stringResource(R.string.tab_catalogs), currentSection == UnifiedLibrarySection.CATALOGS, { Icon(painterResource(R.drawable.cloud), null) }) { onSectionSelected(UnifiedLibrarySection.CATALOGS) }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            Text(stringResource(R.string.unified_library_appearance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp))
            NavigationDrawerItem(icon = { Icon(Icons.Default.Palette, null) }, label = { Text(stringResource(R.string.app_theme_title)) }, selected = false, onClick = onThemeClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(stringResource(R.string.settings)) }, selected = false, onClick = onSettingsClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            NavigationDrawerItem(icon = { Icon(painterResource(R.drawable.fonts), null) }, label = { Text(stringResource(R.string.drawer_custom_fonts)) }, selected = false, onClick = onFontsClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            if (BuildConfig.FLAVOR == "oss" && !BuildConfig.IS_OFFLINE) {
                NavigationDrawerItem(icon = { Icon(painterResource(R.drawable.ai), null) }, label = { Text(stringResource(R.string.ai_settings_title)) }, selected = false, onClick = onAiSettingsClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun UnifiedLibraryDestination(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = icon,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun UnifiedLibraryTopBar(
    section: UnifiedLibrarySection,
    selectedShelf: Shelf?,
    onMenuClick: () -> Unit,
    onBackFromShelf: () -> Unit,
    uiState: ReaderScreenState,
    onAccountClick: () -> Unit,
) {
    Surface(shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedShelf != null) {
                IconButton(onClick = onBackFromShelf) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.unified_library_back_to_shelves)) }
            } else {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, stringResource(R.string.unified_library_drawer_title)) }
            }
            val title = selectedShelf?.name ?: when (section) {
                    UnifiedLibrarySection.HOME -> null
                    UnifiedLibrarySection.SHELVES -> stringResource(R.string.tab_shelves)
                    UnifiedLibrarySection.FOLDERS -> stringResource(R.string.tab_folders)
                    UnifiedLibrarySection.CATALOGS -> stringResource(R.string.tab_catalogs)
                }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onAccountClick, modifier = Modifier.testTag("UnifiedLibraryProfile")) {
                UnifiedProfileAvatar(uiState)
            }
        }
    }
}

@Composable
private fun UnifiedProfileAvatar(uiState: ReaderScreenState) {
    when {
        BuildConfig.FLAVOR != "pro" -> AsyncImage(model = R.mipmap.ic_launcher, contentDescription = stringResource(R.string.content_desc_app_icon), modifier = Modifier.size(32.dp).clip(CircleShape))
        !uiState.currentUser?.photoUrl.isNullOrBlank() -> AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(uiState.currentUser.photoUrl).crossfade(true).build(), contentDescription = stringResource(R.string.content_desc_profile_picture), contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
        else -> Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = stringResource(R.string.content_desc_profile),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnifiedLibraryHome(
    modifier: Modifier,
    books: List<RecentFileItem>,
    continueReading: RecentFileItem?,
    filter: UnifiedLibraryFilter,
    query: String,
    isSearchVisible: Boolean,
    sortOrder: SortOrder,
    advancedFilterCount: Int,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onFilterChange: (UnifiedLibraryFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onControlsClick: () -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
) {
    if (isSearchVisible) {
        UnifiedLibrarySearchResults(
            modifier = modifier,
            books = books,
            query = query,
            selectedBookIds = selectedBookIds,
            downloadingBookIds = downloadingBookIds,
            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            onQueryChange = onQueryChange,
            onClose = onSearchToggle,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick
        )
        return
    }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        continueReading?.let { UnifiedContinueReadingCard(it, { onBookClick(it) }, Modifier.padding(top = 16.dp)) }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.unified_library_your_books), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${books.size} ${if (books.size == 1) "book" else "books"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSearchToggle) { Icon(Icons.Default.Search, stringResource(R.string.unified_library_search_books)) }
            BadgedBox(
                badge = {
                    if (advancedFilterCount > 0) {
                        Badge { Text(advancedFilterCount.toString()) }
                    }
                }
            ) {
                AssistChip(onClick = onControlsClick, label = { Text(stringResource(sortOrder.labelRes)) })
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            FlowRow(
                modifier = Modifier.padding(8.dp).animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnifiedLibraryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text(stringResource(option.labelRes)) }
                    )
                }
            }
        }
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.unified_library_no_books), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            // The header remains visible; only a large collection scrolls.
            Surface(
                modifier = Modifier.weight(1f).padding(top = 16.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 16.dp, 12.dp, 96.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(books, key = { it.bookId }) { item ->
                        RecentFileCard(item = item, isSelected = item.bookId in selectedBookIds, modifier = Modifier.fillMaxWidth(), onClick = { onBookClick(item) }, onLongClick = { onBookLongClick(item) }, isDownloading = item.bookId in downloadingBookIds, usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedLibrarySearchResults(
    modifier: Modifier,
    books: List<RecentFileItem>,
    query: String,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("UnifiedLibrarySearch"),
                placeholder = { Text(stringResource(R.string.unified_library_search_books)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.action_close))
            }
        }
        Text(
            text = if (query.isBlank()) stringResource(R.string.unified_library_your_books) else "${books.size} ${if (books.size == 1) "result" else "results"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        if (books.isEmpty() && query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_results_found, query), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(books, key = { it.bookId }) { item ->
                    RecentFileCard(
                        item = item,
                        isSelected = item.bookId in selectedBookIds,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onBookClick(item) },
                        onLongClick = { onBookLongClick(item) },
                        isDownloading = item.bookId in downloadingBookIds,
                        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedShelvesSection(
    modifier: Modifier,
    shelves: List<Shelf>,
    selectedShelfId: String?,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onShelfSelected: (Shelf) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
) {
    val selectedShelf = shelves.find { it.id == selectedShelfId }
    if (selectedShelf == null) {
        val visibleShelves = remember(shelves) { shelves.filter { it.type != ShelfType.TAG && it.parentShelfId == null } }
        if (visibleShelves.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.unified_library_no_shelves), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(visibleShelves, key = { it.id }) { shelf ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onShelfSelected(shelf) }, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${shelf.bookCount} ${if (shelf.bookCount == 1) "book" else "books"}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(selectedShelf.directBooks, key = { it.bookId }) { item -> RecentFileCard(item, item.bookId in selectedBookIds, Modifier.fillMaxWidth(), onClick = { onBookClick(item) }, onLongClick = { onBookLongClick(item) }, isDownloading = item.bookId in downloadingBookIds, usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName) }
        }
    }
}

@Composable
private fun UnifiedFoldersSection(
    modifier: Modifier,
    folders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    isLoading: Boolean,
    onAddFolder: () -> Unit,
    onScan: () -> Unit,
    onSyncMetadata: () -> Unit,
    onToggleLocalSync: (SyncedFolder, Boolean, Boolean) -> Unit,
    onEditFolderFilters: (SyncedFolder, Set<FileType>) -> Unit,
    onRemove: (SyncedFolder) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FolderSyncScreen(
            syncedFolders = folders,
            allRecentFiles = allRecentFiles,
            onAddFolderClick = onAddFolder,
            onRemoveFolderClick = onRemove,
            onFolderLocalSyncChange = onToggleLocalSync,
            onEditFolderFiltersClick = onEditFolderFilters,
            onScanNowClick = onScan,
            onSyncMetadataClick = onSyncMetadata,
            isLoading = isLoading
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedLibraryControlsSheet(currentFilter: UnifiedLibraryFilter, currentSortOrder: SortOrder, onFilterChanged: (UnifiedLibraryFilter) -> Unit, onSortChanged: (SortOrder) -> Unit, onAdvancedFiltersClick: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.unified_library_sort_filter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.filter_read_status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { UnifiedLibraryFilter.entries.forEach { option -> FilterChip(selected = currentFilter == option, onClick = { onFilterChanged(option) }, label = { Text(stringResource(option.labelRes)) }) } }
            Text(stringResource(R.string.content_desc_sort), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SortOrder.entries.forEach { order -> FilterChip(selected = currentSortOrder == order, onClick = { onSortChanged(order) }, label = { Text(stringResource(order.labelRes)) }) } }
            OutlinedButton(onClick = onAdvancedFiltersClick, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(stringResource(R.string.filter_library)) }
        }
    }
}

@Composable
private fun UnifiedCreateShelfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.create_new_shelf)) }, text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.shelf_name_hint)) }, singleLine = true) }, confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun UnifiedContinueReadingCard(item: RecentFileItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress = (item.progressPercentage ?: 0f).coerceIn(0f, 100f)
    val coverTone = generatedBookCoverColor(item)
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(shape)
            .testTag("UnifiedLibraryContinueReading")
            .combinedClickable(onClick = onClick, onLongClick = {}),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.horizontalGradient(
                    0f to coverTone.copy(alpha = 0.74f),
                    0.48f to coverTone.copy(alpha = 0.28f),
                    1f to MaterialTheme.colorScheme.inverseSurface
                )
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(94.dp).fillMaxHeight()) {
                    ThemedBookCover(
                        item = item,
                        modifier = Modifier
                            .size(94.dp, 146.dp)
                            .align(Alignment.CenterStart)
                            .shadow(10.dp, RoundedCornerShape(18.dp), clip = true),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.unified_library_continue_reading).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(item.cardTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.cardAuthor(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f))
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileTypeBadge(type = item.type, overlay = true, compact = true)
                        if (item.sourceFolderUri != null) {
                            Text(
                                text = "· Local folder",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.66f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.progress_complete, progress.toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.82f))
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f))
                }
            }
        }
    }
}

internal enum class UnifiedLibraryFilter(val labelRes: Int) { ALL(R.string.unified_library_all), READING(R.string.unified_library_reading), FINISHED(R.string.unified_library_finished), UNREAD(R.string.unified_library_unread) }

private fun LibraryFilters.selectedFilterCount(): Int =
    fileTypes.size + sourceFolders.size + tagIds.size + if (readStatus == ReadStatusFilter.ALL) 0 else 1

internal fun ReadStatusFilter.toUnifiedLibraryFilter(): UnifiedLibraryFilter = when (this) {
    ReadStatusFilter.ALL -> UnifiedLibraryFilter.ALL
    ReadStatusFilter.UNREAD -> UnifiedLibraryFilter.UNREAD
    ReadStatusFilter.IN_PROGRESS -> UnifiedLibraryFilter.READING
    ReadStatusFilter.COMPLETED -> UnifiedLibraryFilter.FINISHED
}

internal fun findContinueReadingBook(books: List<RecentFileItem>): RecentFileItem? = books.filter { (it.progressPercentage ?: 0f) in 0.01f..<100f }.maxByOrNull { maxOf(it.readingPositionModifiedTimestamp, it.timestamp) } ?: books.maxByOrNull { it.timestamp }

internal fun filterUnifiedLibraryBooks(books: List<RecentFileItem>, filter: UnifiedLibraryFilter, query: String): List<RecentFileItem> {
    val normalizedQuery = query.trim()
    return books.filter { book ->
        val progress = book.progressPercentage ?: 0f
        val matchesFilter = when (filter) { UnifiedLibraryFilter.ALL -> true; UnifiedLibraryFilter.READING -> progress in 0.01f..<100f; UnifiedLibraryFilter.FINISHED -> progress >= 100f; UnifiedLibraryFilter.UNREAD -> progress <= 0f }
        matchesFilter && (normalizedQuery.isBlank() || listOf(book.displayName, book.title, book.author).any { it?.contains(normalizedQuery, ignoreCase = true) == true })
    }
}
