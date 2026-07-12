package com.aryan.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
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
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(UnifiedLibrarySection.HOME) }
    var selectedShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(UnifiedLibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showLibraryControls by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }

    val filePicker = rememberFilePickerLauncher(viewModel::onFilesSelected)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addSyncedFolder)
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
    val continueReading = remember(uiState.rawLibraryFiles) {
        findContinueReadingBook(uiState.rawLibraryFiles)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            UnifiedLibraryDrawer(
                currentSection = section,
                onSectionSelected = { destination ->
                    selectedShelfId = null
                    section = destination
                    scope.launch { drawerState.close() }
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
                UnifiedLibraryTopBar(
                    section = section,
                    selectedShelf = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBackFromShelf = { selectedShelfId = null },
                    profileMenuExpanded = showProfileMenu,
                    onProfileMenuExpandedChange = { showProfileMenu = it },
                    uiState = uiState,
                    onSignIn = {
                        context.findActivity()?.let(viewModel::signIn)
                        showProfileMenu = false
                    },
                    onSettingsClick = {
                        showProfileMenu = false
                        navController.navigateIfReady(AppDestinations.SETTINGS_SCREEN_ROUTE)
                    },
                    onSignOut = {
                        showProfileMenu = false
                        viewModel.signOut()
                    }
                )
            },
            floatingActionButton = {
                when (section) {
                    UnifiedLibrarySection.HOME -> ExtendedFloatingActionButton(
                        onClick = { filePicker.launch(if (uiState.useStrictFileFilter) MainViewModel.SUPPORTED_MIME_TYPES else arrayOf("*/*")) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.unified_library_import)) }
                    )
                    UnifiedLibrarySection.SHELVES -> if (selectedShelfId == null) {
                        ExtendedFloatingActionButton(
                            onClick = viewModel::showCreateShelfDialog,
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.fab_new_shelf)) }
                        )
                    }
                    UnifiedLibrarySection.FOLDERS -> ExtendedFloatingActionButton(
                        onClick = { folderPicker.launch(null) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.unified_library_add_folder)) }
                    )
                    UnifiedLibrarySection.CATALOGS -> Unit
                }
            }
        ) { padding ->
            when (section) {
                UnifiedLibrarySection.HOME -> UnifiedLibraryHome(
                    modifier = Modifier.padding(padding),
                    books = visibleBooks,
                    continueReading = continueReading,
                    filter = filter,
                    query = query,
                    isSearchVisible = isSearchVisible,
                    sortOrder = uiState.sortOrder,
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
                    isLoading = uiState.isLoading,
                    onScan = viewModel::scanSyncedFolder,
                    onSyncMetadata = viewModel::syncFolderMetadata,
                    onToggleLocalSync = { folder, enabled -> viewModel.setFolderLocalSyncEnabled(folder, enabled) },
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
    if (uiState.showCreateShelfDialog) {
        UnifiedCreateShelfDialog(viewModel::createShelf, viewModel::dismissCreateShelfDialog)
    }
    CustomTopBanner(bannerMessage = uiState.bannerMessage)
}

private enum class UnifiedLibrarySection { HOME, SHELVES, FOLDERS, CATALOGS }

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
        Text(stringResource(R.string.unified_library_drawer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
        HorizontalDivider()
        UnifiedLibraryDestination(stringResource(R.string.unified_library_home), currentSection == UnifiedLibrarySection.HOME) { onSectionSelected(UnifiedLibrarySection.HOME) }
        UnifiedLibraryDestination(stringResource(R.string.tab_shelves), currentSection == UnifiedLibrarySection.SHELVES) { onSectionSelected(UnifiedLibrarySection.SHELVES) }
        UnifiedLibraryDestination(stringResource(R.string.tab_folders), currentSection == UnifiedLibrarySection.FOLDERS) { onSectionSelected(UnifiedLibrarySection.FOLDERS) }
        if (!BuildConfig.IS_OFFLINE) {
            UnifiedLibraryDestination(stringResource(R.string.tab_catalogs), currentSection == UnifiedLibrarySection.CATALOGS) { onSectionSelected(UnifiedLibrarySection.CATALOGS) }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.unified_library_appearance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
        NavigationDrawerItem(icon = { Icon(Icons.Default.Palette, null) }, label = { Text(stringResource(R.string.app_theme_title)) }, selected = false, onClick = onThemeClick, modifier = Modifier.padding(horizontal = 12.dp))
        NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(stringResource(R.string.settings)) }, selected = false, onClick = onSettingsClick, modifier = Modifier.padding(horizontal = 12.dp))
        NavigationDrawerItem(icon = { Icon(painterResource(R.drawable.fonts), null) }, label = { Text(stringResource(R.string.drawer_custom_fonts)) }, selected = false, onClick = onFontsClick, modifier = Modifier.padding(horizontal = 12.dp))
        if (BuildConfig.FLAVOR == "oss" && !BuildConfig.IS_OFFLINE) {
            NavigationDrawerItem(icon = { Icon(painterResource(R.drawable.ai), null) }, label = { Text(stringResource(R.string.ai_settings_title)) }, selected = false, onClick = onAiSettingsClick, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun UnifiedLibraryDestination(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(label) }, selected = selected, onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
}

@Composable
private fun UnifiedLibraryTopBar(
    section: UnifiedLibrarySection,
    selectedShelf: Shelf?,
    onMenuClick: () -> Unit,
    onBackFromShelf: () -> Unit,
    profileMenuExpanded: Boolean,
    onProfileMenuExpandedChange: (Boolean) -> Unit,
    uiState: ReaderScreenState,
    onSignIn: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOut: () -> Unit,
) {
    Surface(shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedShelf != null) {
                IconButton(onClick = onBackFromShelf) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.unified_library_back_to_shelves)) }
            } else {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, stringResource(R.string.unified_library_drawer_title)) }
            }
            Text(
                text = selectedShelf?.name ?: when (section) {
                    UnifiedLibrarySection.HOME -> stringResource(R.string.unified_library_home)
                    UnifiedLibrarySection.SHELVES -> stringResource(R.string.tab_shelves)
                    UnifiedLibrarySection.FOLDERS -> stringResource(R.string.tab_folders)
                    UnifiedLibrarySection.CATALOGS -> stringResource(R.string.tab_catalogs)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(onClick = { onProfileMenuExpandedChange(true) }, modifier = Modifier.testTag("UnifiedLibraryProfile")) {
                    UnifiedProfileAvatar(uiState)
                }
                DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { onProfileMenuExpandedChange(false) }) {
                    if (BuildConfig.FLAVOR == "pro" && uiState.currentUser == null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.drawer_sign_in)) }, onClick = onSignIn)
                    } else {
                        uiState.currentUser?.displayName?.let { name -> DropdownMenuItem(text = { Text(name) }, onClick = {}) }
                        DropdownMenuItem(text = { Text(stringResource(R.string.unified_library_manage_account)) }, onClick = onSettingsClick)
                        if (uiState.currentUser != null) DropdownMenuItem(text = { Text(stringResource(R.string.drawer_sign_out)) }, onClick = onSignOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedProfileAvatar(uiState: ReaderScreenState) {
    when {
        BuildConfig.FLAVOR != "pro" -> AsyncImage(model = R.mipmap.ic_launcher, contentDescription = stringResource(R.string.content_desc_app_icon), modifier = Modifier.size(32.dp).clip(CircleShape))
        !uiState.currentUser?.photoUrl.isNullOrBlank() -> AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(uiState.currentUser?.photoUrl).crossfade(true).build(), contentDescription = stringResource(R.string.content_desc_profile_picture), contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
        else -> Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
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
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        continueReading?.let { UnifiedContinueReadingCard(it, { onBookClick(it) }, Modifier.padding(top = 16.dp)) }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.unified_library_your_books), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${books.size} ${if (books.size == 1) "book" else "books"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSearchToggle) { Icon(Icons.Default.Search, stringResource(R.string.unified_library_search_books)) }
            AssistChip(onClick = onControlsClick, label = { Text(stringResource(sortOrder.labelRes)) })
        }
        if (isSearchVisible) {
            OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("UnifiedLibrarySearch"), placeholder = { Text(stringResource(R.string.unified_library_search_books)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        }
        FlowRow(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UnifiedLibraryFilter.entries.forEach { option -> FilterChip(selected = filter == option, onClick = { onFilterChange(option) }, label = { Text(stringResource(option.labelRes)) }) }
        }
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.unified_library_no_books), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            // The header remains visible; only a large collection scrolls.
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).padding(top = 16.dp), contentPadding = PaddingValues(bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(books, key = { it.bookId }) { item ->
                    RecentFileCard(item = item, isSelected = item.bookId in selectedBookIds, modifier = Modifier.fillMaxWidth(), onClick = { onBookClick(item) }, onLongClick = { onBookLongClick(item) }, isDownloading = item.bookId in downloadingBookIds, usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName)
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
private fun UnifiedFoldersSection(modifier: Modifier, folders: List<SyncedFolder>, isLoading: Boolean, onScan: () -> Unit, onSyncMetadata: () -> Unit, onToggleLocalSync: (SyncedFolder, Boolean) -> Unit, onRemove: (SyncedFolder) -> Unit) {
    if (folders.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.unified_library_no_folders), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onScan, enabled = !isLoading, modifier = Modifier.weight(1f)) { Text(if (isLoading) stringResource(R.string.scanning) else stringResource(R.string.scan_all)) }
                OutlinedButton(onClick = onSyncMetadata, enabled = !isLoading, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_meta)) }
            }
        }
        items(folders, key = { it.uriString }) { folder ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    Text(if (folder.localSyncEnabled) stringResource(R.string.menu_disable_folder_local_sync) else stringResource(R.string.folder_local_sync_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onToggleLocalSync(folder, !folder.localSyncEnabled) }) { Text(if (folder.localSyncEnabled) stringResource(R.string.action_disable) else stringResource(R.string.action_enable)) }
                        TextButton(onClick = { onRemove(folder) }) { Text(stringResource(R.string.menu_remove_folder)) }
                    }
                }
            }
        }
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
    Surface(modifier = modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(30.dp)).testTag("UnifiedLibraryContinueReading").combinedClickable(onClick = onClick, onLongClick = {}), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 6.dp) {
        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(18.dp))) { ThemedBookCover(item = item, modifier = Modifier.fillMaxSize(), contentDescription = item.displayName, contentScale = ContentScale.Crop) }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.unified_library_continue_reading).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(item.cardTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.progress_complete, progress.toInt()), color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f))
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.inverseOnSurface, trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_read))
        }
    }
}

internal enum class UnifiedLibraryFilter(val labelRes: Int) { ALL(R.string.unified_library_all), READING(R.string.unified_library_reading), FINISHED(R.string.unified_library_finished), UNREAD(R.string.unified_library_unread) }

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
