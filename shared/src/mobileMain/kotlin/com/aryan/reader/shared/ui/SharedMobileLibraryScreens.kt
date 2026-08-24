package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.SharedBookTtsListeningProgress
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedTtsListenItem
import com.aryan.reader.shared.SharedTtsListenStartPolicy
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.canExportOriginalFile
import com.aryan.reader.shared.booksAvailableForShelfAddition
import com.aryan.reader.shared.buildSharedTtsListenItems
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsDownloadLocation
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.sortBooks
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
private fun MobileUnifiedLibraryDrawerLabel.readerString(): String = when (this) {
    MobileUnifiedLibraryDrawerLabel.HOME -> readerString("unified_library_home", "Home")
    MobileUnifiedLibraryDrawerLabel.AUDIOBOOKS -> readerString("listen_title", "Listen")
    MobileUnifiedLibraryDrawerLabel.SHELVES -> readerString("tab_shelves", "Shelves")
    MobileUnifiedLibraryDrawerLabel.FOLDERS -> readerString("tab_folders", "Folders")
    MobileUnifiedLibraryDrawerLabel.CATALOGS -> readerString("tab_catalogs", "Catalogs")
    MobileUnifiedLibraryDrawerLabel.THEME -> readerString("app_theme_title", "App theme")
    MobileUnifiedLibraryDrawerLabel.SETTINGS -> readerString("settings", "Settings")
    MobileUnifiedLibraryDrawerLabel.FONTS -> readerString("drawer_custom_fonts", "Custom fonts")
    MobileUnifiedLibraryDrawerLabel.AI -> readerString("ai_settings_title", "AI settings")
}

private fun MobileUnifiedLibraryDrawerDestination.icon(): ImageVector = when (this) {
    MobileUnifiedLibraryDrawerDestination.HOME -> Icons.Default.Home
    MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS -> Icons.AutoMirrored.Filled.VolumeUp
    MobileUnifiedLibraryDrawerDestination.SHELVES -> Icons.AutoMirrored.Filled.LibraryBooks
    MobileUnifiedLibraryDrawerDestination.FOLDERS -> Icons.Default.Folder
    MobileUnifiedLibraryDrawerDestination.CATALOGS -> Icons.Default.Cloud
}

private fun MobileUnifiedLibraryDrawerAppearance.icon(): ImageVector = when (this) {
    MobileUnifiedLibraryDrawerAppearance.THEME -> Icons.Default.Palette
    MobileUnifiedLibraryDrawerAppearance.SETTINGS -> Icons.Default.Settings
    MobileUnifiedLibraryDrawerAppearance.FONTS -> Icons.Default.Fonts
    MobileUnifiedLibraryDrawerAppearance.AI -> Icons.Default.Ai
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileUnifiedLibraryScreen(
    state: SharedReaderScreenState,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onUpdateBook: (BookItem) -> Unit,
    onCreateShelf: (String) -> Unit,
    onImportBooks: () -> Unit,
    onAddFolder: () -> Unit,
    onScanFolders: () -> Unit,
    onSyncFolderMetadata: () -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean) -> Unit,
    onFolderFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit,
    onRemoveFolder: (SyncedFolder) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppTheme: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenAccountDrawer: () -> Unit = {},
    onSortOrderChange: (SortOrder) -> Unit = {},
    onFiltersChange: (LibraryFilters) -> Unit = {},
    accountAvatar: @Composable () -> Unit = {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = readerString("content_desc_profile", "Profile"),
            modifier = Modifier.size(32.dp),
        )
    },
    drawerCapabilities: MobileUnifiedLibraryDrawerCapabilities = MobileUnifiedLibraryDrawerCapabilities(),
    catalogContent: @Composable (Modifier) -> Unit,
    initialSection: Int = 0,
    onSectionChange: (Int) -> Unit = {},
    useListView: Boolean = false,
    onListViewChange: (Boolean) -> Unit = {},
    audiobooks: List<SharedAudiobook> = emptyList(),
    audiobookPlayback: SharedAudiobookPlaybackState = SharedAudiobookPlaybackState(),
    onPlayAudiobook: (SharedAudiobook) -> Unit = {},
    onToggleAudiobookPlayback: () -> Unit = {},
    onSeekAudiobook: (Long) -> Unit = {},
    onAudiobookSpeedChange: (Float) -> Unit = {},
    onAudiobookSleepTimer: (Int?) -> Unit = {},
    customSleepTimerMinutes: List<Int> = emptyList(),
    onCustomSleepTimerMinutesChange: (List<Int>) -> Unit = {},
    onStopAudiobookPlayback: () -> Unit = {},
    ttsListenState: SharedBookTtsListenState = SharedBookTtsListenState(),
    ttsProgress: List<SharedBookTtsListeningProgress> = emptyList(),
    ttsChapterTitles: Map<String, List<String>> = emptyMap(),
    onStartTtsListen: (BookItem, SharedTtsListenStartPolicy, Int?) -> Unit = { _, _, _ -> },
    onToggleTtsPlayback: () -> Unit = {},
    onSeekTtsChunk: (Int) -> Unit = {},
    onSeekTtsChapter: (Int) -> Unit = {},
    onTtsSpeedChange: (Float) -> Unit = {},
    onTtsSleepTimer: (Int?) -> Unit = {},
    onStopTtsPlayback: () -> Unit = {},
    /** Dedicated Listen import actions. Null keeps the legacy generic import fallback. */
    onAddAudiobookFile: (() -> Unit)? = null,
    onAddAudiobookMultiple: (() -> Unit)? = null,
    onAddAudiobookFolder: (() -> Unit)? = null,
    onChooseTtsBook: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(MobileUnifiedLibraryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var section by remember(initialSection) {
        mutableStateOf(MobileUnifiedLibrarySection.fromPersisted(initialSection))
    }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var playerBook by remember { mutableStateOf<SharedAudiobook?>(null) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var ttsPlayerItem by remember { mutableStateOf<SharedTtsListenItem?>(null) }
    var showTtsPlayerSheet by remember { mutableStateOf(false) }
    var showAudiobookAddSheet by remember { mutableStateOf(false) }
    val unifiedDrawerState = rememberDrawerState(DrawerValue.Closed)
    val unifiedScope = rememberCoroutineScope()
    val drawerModel = remember(drawerCapabilities) {
        mobileUnifiedLibraryDrawerModel(drawerCapabilities)
    }
    val visibleBooks = remember(
        state.rawLibraryBooks,
        state.libraryFilters,
        state.syncedFolders,
        state.sortOrder,
        filter,
        query,
    ) {
        mobileUnifiedLibraryBooks(
            books = state.rawLibraryBooks,
            filter = filter,
            query = query,
            libraryFilters = state.libraryFilters.withIosFolderFilterIdentities(state.syncedFolders),
            sortOrder = state.sortOrder,
        )
    }
    val continueReading = remember(state.rawLibraryBooks) {
        mobileUnifiedContinueReadingBook(state.rawLibraryBooks)
    }
    val ttsItems = remember(state.rawLibraryBooks, ttsProgress) {
        buildSharedTtsListenItems(state.rawLibraryBooks, ttsProgress)
    }
    val advancedFilterCount = state.libraryFilters.fileTypes.size +
        state.libraryFilters.sourceFolders.size +
        state.libraryFilters.tagIds.size +
        if (state.libraryFilters.readStatus == ReadStatusFilter.ALL) 0 else 1

    fun applyUnifiedLibraryFilters(filters: LibraryFilters) {
        filter = filters.readStatus.toMobileUnifiedLibraryFilter()
        onFiltersChange(filters)
    }

    fun closeDrawerAnd(action: () -> Unit) {
        unifiedScope.launch {
            unifiedDrawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = unifiedDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    readerString("unified_library_drawer_title", "Your library"),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                drawerModel.destinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label.readerString()) },
                        selected = section == destination.section,
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        onClick = {
                            closeDrawerAnd {
                                section = destination.section
                                selectedShelfId = null
                                onSectionChange(section.persistedValue)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Text(
                    readerString("unified_library_appearance", "Appearance"),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                drawerModel.appearance.forEach { appearance ->
                    NavigationDrawerItem(
                        label = { Text(appearance.label.readerString()) },
                        selected = false,
                        icon = { Icon(appearance.icon(), contentDescription = null) },
                        onClick = {
                            closeDrawerAnd {
                                when (appearance) {
                                    MobileUnifiedLibraryDrawerAppearance.THEME -> onOpenAppTheme()
                                    MobileUnifiedLibraryDrawerAppearance.SETTINGS -> onOpenSettings()
                                    MobileUnifiedLibraryDrawerAppearance.FONTS -> onOpenFonts()
                                    MobileUnifiedLibraryDrawerAppearance.AI -> onOpenAiSettings()
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedShelfId?.let { id -> state.shelves.firstOrNull { it.id == id }?.name }
                                ?: when (section) {
                                    MobileUnifiedLibrarySection.HOME -> readerString("nav_unified_library", "Library Beta")
                                    MobileUnifiedLibrarySection.SHELVES -> readerString("tab_shelves", "Shelves")
                                    MobileUnifiedLibrarySection.FOLDERS -> readerString("tab_folders", "Folders")
                                    MobileUnifiedLibrarySection.CATALOGS -> readerString("tab_catalogs", "Catalogs")
                                    MobileUnifiedLibrarySection.AUDIOBOOKS -> readerString("audiobooks_title", "Audiobooks")
                                }
                        )
                        Spacer(Modifier.width(8.dp))
                        if (section == MobileUnifiedLibrarySection.HOME) Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                readerString("unified_library_beta", "BETA"),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedShelfId != null) selectedShelfId = null
                        else unifiedScope.launch { unifiedDrawerState.open() }
                    }) {
                        Icon(
                            if (selectedShelfId != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                            contentDescription = if (selectedShelfId != null) readerString("unified_library_back_to_shelves", "All shelves") else readerString("unified_library_drawer_title", "Your library"),
                        )
                    }
                },
                actions = {
                    if (section == MobileUnifiedLibrarySection.HOME) IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) query = "" }) {
                        Icon(
                            if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = readerString("unified_library_search_books", "Search your books"),
                        )
                    }
                    if (section == MobileUnifiedLibrarySection.HOME) IconButton(onClick = { onListViewChange(!useListView) }) {
                        Icon(
                            if (useListView) Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.FormatListNumbered,
                            contentDescription = if (useListView) {
                                readerString("unified_library_grid_view", "Grid view")
                            } else {
                                readerString("unified_library_list_view", "List view")
                            },
                        )
                    }
                    IconButton(
                        onClick = onOpenAccountDrawer,
                        modifier = Modifier.testTag("UnifiedLibraryProfile"),
                    ) {
                        accountAvatar()
                    }
                },
            )
        },
        floatingActionButton = {
            when {
                section == MobileUnifiedLibrarySection.HOME -> ExtendedFloatingActionButton(
                    onClick = onImportBooks,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("unified_library_import", "Import files")) },
                )
                section == MobileUnifiedLibrarySection.SHELVES && selectedShelfId == null -> ExtendedFloatingActionButton(
                    onClick = { showCreateShelf = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("fab_new_shelf", "New shelf")) },
                )
                section == MobileUnifiedLibrarySection.FOLDERS -> Unit
                section == MobileUnifiedLibrarySection.CATALOGS -> Unit
                section == MobileUnifiedLibrarySection.AUDIOBOOKS -> ExtendedFloatingActionButton(
                    onClick = { showAudiobookAddSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("audiobooks_add", "Add audiobook")) },
                )
            }
        },
        bottomBar = {
            val activeAudiobook = audiobooks.firstOrNull { it.bookId == audiobookPlayback.bookId }
            val activeTts = ttsItems.firstOrNull { it.book.id == ttsListenState.bookId && ttsListenState.connected }
            when {
                activeAudiobook != null -> SharedMobileAudiobookMiniPlayer(
                    audiobook = activeAudiobook,
                    playback = audiobookPlayback,
                    onTogglePlayback = onToggleAudiobookPlayback,
                    onExpand = {
                        playerBook = activeAudiobook
                        showPlayerSheet = true
                    },
                    onStopPlayback = onStopAudiobookPlayback,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
                activeTts != null -> SharedMobileTtsMiniPlayer(
                    item = activeTts,
                    playback = ttsListenState,
                    onTogglePlayback = onToggleTtsPlayback,
                    onExpand = {
                        ttsPlayerItem = activeTts
                        showTtsPlayerSheet = true
                    },
                    onStopPlayback = onStopTtsPlayback,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        },
    ) { padding ->
        if (section == MobileUnifiedLibrarySection.SHELVES) {
            SharedMobileUnifiedShelvesSection(
                shelves = state.shelves,
                selectedShelfId = selectedShelfId,
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                downloadingBookIds = state.downloadingBookIds,
                onShelfSelected = { selectedShelfId = it.id },
                onOpenBook = onOpenBook,
                onLongPressBook = onLongPressBook,
                onTogglePinned = onTogglePinned,
                onShowBookInfo = { infoBook = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.FOLDERS) {
            SharedMobileFolderSyncScreen(
                folders = state.syncedFolders,
                books = state.rawLibraryBooks,
                isLoading = state.isRefreshing,
                onAddFolder = onAddFolder,
                onScanAll = onScanFolders,
                onSyncMetadata = onSyncFolderMetadata,
                onLocalSyncChange = onFolderLocalSyncChange,
                onFileTypesChange = onFolderFileTypesChange,
                onRemoveFolder = onRemoveFolder,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.CATALOGS) {
            catalogContent(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.AUDIOBOOKS) {
            SharedMobileAudiobooksSection(
                audiobooks = audiobooks,
                playback = audiobookPlayback,
                ttsItems = ttsItems,
                ttsPlayback = ttsListenState,
                onAddAudiobook = { showAudiobookAddSheet = true },
                onOpenPlayer = { book ->
                    if (audiobookPlayback.bookId != book.bookId) {
                        onPlayAudiobook(book)
                    }
                    playerBook = book
                    showPlayerSheet = true
                },
                onOpenTtsPlayer = { item, autoStart ->
                    if (autoStart) {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.RESUME, null)
                    }
                    ttsPlayerItem = item
                    showTtsPlayerSheet = true
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(readerString("unified_library_search_books", "Search your books")) },
                    )
                }
            } else {
                continueReading?.let { book ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(readerString("unified_library_continue_reading", "Continue reading").uppercase(), style = MaterialTheme.typography.labelLarge)
                                Text(book.cardTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                book.author?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(readerString("progress_complete", "%1\$d%% complete", (book.progressPercentage ?: 0f).roundToInt()))
                            }
                        }
                    }
                }
            }
            item {
                Text(readerString("unified_library_your_books", "Your books"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(readerQuantityString("book_count", visibleBooks.size, "%1\$d book", "%1\$d books", visibleBooks.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MobileUnifiedLibraryFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(readerString(option.stringKey, option.fallbackLabel)) },
                        )
                    }
                    SharedMobileLibrarySortControl(
                        sortOrder = state.sortOrder,
                        labels = SortOrder.entries.associateWith { it.sharedMobileLabel() },
                        selectedContentDescription = readerString("content_desc_selected", "Selected"),
                        onSortOrderChange = onSortOrderChange,
                        modifier = Modifier.testTag("UnifiedLibrarySortButton"),
                        icon = {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = readerString("content_desc_sort", "Sort"),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    BadgedBox(
                        badge = {
                            if (advancedFilterCount > 0) {
                                Badge { Text(advancedFilterCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(
                            onClick = { showFilters = true },
                            modifier = Modifier.testTag("UnifiedLibraryFilter"),
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = readerString("content_desc_filter", "Filter"),
                                tint = if (advancedFilterCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            if (visibleBooks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        Text(readerString("unified_library_no_books", "No books in this view"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (useListView) {
                items(visibleBooks, key = { it.id }) { book ->
                    SharedMobileLibraryListItem(
                        book = book,
                        selected = book.id in state.selectedBookIds,
                        pinned = book.id in state.pinnedLibraryBookIds,
                        downloading = book.id in state.downloadingBookIds,
                        onClick = {
                            if (state.selectedBookIds.isEmpty()) onOpenBook(book) else onLongPressBook(book)
                        },
                        onLongClick = { onLongPressBook(book) },
                        onTogglePinned = { onTogglePinned(book) },
                        onShowBookInfo = { infoBook = book },
                    )
                }
            } else {
                item {
                    SharedMobileBookGridSection(
                        title = "",
                        books = visibleBooks,
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        downloadingBookIds = state.downloadingBookIds,
                        onOpenBook = { book ->
                            if (state.selectedBookIds.isEmpty()) onOpenBook(book) else onLongPressBook(book)
                        },
                        onLongPressBook = onLongPressBook,
                        onTogglePinned = onTogglePinned,
                        onShowBookInfo = { infoBook = it },
                    )
                }
            }
        }
    }
    }
    if (showFilters) {
        SharedMobileLibraryFilterDialog(
            state = state,
            onDismiss = { showFilters = false },
            onFiltersChange = ::applyUnifiedLibraryFilters,
        )
    }

    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp.takeIf { it > 0L }?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onDismiss = { infoBook = null },
            onSave = { updated -> onUpdateBook(updated); infoBook = null },
            onRestore = { restored -> onUpdateBook(restored); infoBook = null },
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("fab_new_shelf", "New shelf"),
            onDismiss = { showCreateShelf = false },
            onCreate = { name -> onCreateShelf(name); showCreateShelf = false },
        )
    }
    if (showAudiobookAddSheet) {
        SharedMobileAudiobookAddSheet(
            onChooseFile = {
                showAudiobookAddSheet = false
                (onAddAudiobookFile ?: onImportBooks)()
            },
            onChooseMultiple = {
                showAudiobookAddSheet = false
                (onAddAudiobookMultiple ?: onImportBooks)()
            },
            onChooseFolder = {
                showAudiobookAddSheet = false
                (onAddAudiobookFolder ?: onImportBooks)()
            },
            onChooseTtsBook = onChooseTtsBook?.let { choose ->
                {
                    showAudiobookAddSheet = false
                    choose()
                }
            },
            onDismiss = { showAudiobookAddSheet = false },
        )
    }
    if (showPlayerSheet) {
        playerBook?.let { book ->
            SharedMobileAudiobookPlayerSheet(
                audiobook = book,
                playback = audiobookPlayback,
                onTogglePlayback = {
                    if (audiobookPlayback.bookId != book.bookId) {
                        onPlayAudiobook(book)
                    } else {
                        onToggleAudiobookPlayback()
                    }
                },
                onSeek = onSeekAudiobook,
                onSpeedChange = onAudiobookSpeedChange,
                onSleepTimer = onAudiobookSleepTimer,
                customSleepTimerMinutes = customSleepTimerMinutes,
                onCustomSleepTimerMinutesChange = onCustomSleepTimerMinutesChange,
                onStopPlayback = onStopAudiobookPlayback,
                onDismiss = { showPlayerSheet = false },
            )
        }
    }
    if (showTtsPlayerSheet) {
        ttsPlayerItem?.let { item ->
            SharedMobileTtsPlayerSheet(
                item = item,
                playback = ttsListenState,
                chapterTitles = ttsChapterTitles[item.book.id],
                onTogglePlayback = {
                    if (ttsListenState.bookId != item.book.id || !ttsListenState.connected) {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.RESUME, null)
                    } else {
                        onToggleTtsPlayback()
                    }
                },
                onSeekChunk = onSeekTtsChunk,
                onSeekChapter = { index ->
                    if (ttsListenState.bookId == item.book.id && ttsListenState.connected) {
                        onSeekTtsChapter(index)
                    } else {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.CHAPTER, index)
                    }
                },
                onSpeedChange = onTtsSpeedChange,
                onSleepTimer = onTtsSleepTimer,
                customSleepTimerMinutes = customSleepTimerMinutes,
                onCustomSleepTimerMinutesChange = onCustomSleepTimerMinutesChange,
                onStopPlayback = onStopTtsPlayback,
                onDismiss = { showTtsPlayerSheet = false },
            )
        }
    }
}

@Composable
private fun SharedMobileUnifiedShelvesSection(
    shelves: List<Shelf>,
    selectedShelfId: String?,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onShelfSelected: (Shelf) -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedShelf = shelves.firstOrNull { it.id == selectedShelfId }
    if (selectedShelf == null) {
        val visibleShelves = remember(shelves) {
            shelves.filter { it.type != ShelfType.TAG && it.parentShelfId == null }
        }
        if (visibleShelves.isEmpty()) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(readerString("unified_library_no_shelves", "No shelves yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleShelves, key = { it.id }) { shelf ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onShelfSelected(shelf) }) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(readerQuantityString("book_count", shelf.bookCount, "%1\$d book", "%1\$d books", shelf.bookCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp)) {
            item {
                SharedMobileBookGridSection(
                    title = "",
                    books = selectedShelf.directBooks,
                    selectedBookIds = selectedBookIds,
                    pinnedBookIds = pinnedBookIds,
                    downloadingBookIds = downloadingBookIds,
                    onOpenBook = { book -> if (selectedBookIds.isEmpty()) onOpenBook(book) else onLongPressBook(book) },
                    onLongPressBook = onLongPressBook,
                    onTogglePinned = onTogglePinned,
                    onShowBookInfo = onShowBookInfo,
                )
            }
        }
    }
}

private val MobileUnifiedLibraryFilter.stringKey: String
    get() = when (this) {
        MobileUnifiedLibraryFilter.ALL -> "unified_library_all"
        MobileUnifiedLibraryFilter.READING -> "unified_library_reading"
        MobileUnifiedLibraryFilter.FINISHED -> "unified_library_finished"
        MobileUnifiedLibraryFilter.UNREAD -> "unified_library_unread"
    }

private val MobileUnifiedLibraryFilter.fallbackLabel: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private fun ReadStatusFilter.toMobileUnifiedLibraryFilter(): MobileUnifiedLibraryFilter = when (this) {
    ReadStatusFilter.ALL -> MobileUnifiedLibraryFilter.ALL
    ReadStatusFilter.UNREAD -> MobileUnifiedLibraryFilter.UNREAD
    ReadStatusFilter.IN_PROGRESS -> MobileUnifiedLibraryFilter.READING
    ReadStatusFilter.COMPLETED -> MobileUnifiedLibraryFilter.FINISHED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileHomeScreen(
    state: SharedReaderScreenState,
    actions: SharedMobileHomeActions,
    importedCoverPath: String? = null,
    showTopBar: Boolean = true,
    homeOverflowCapabilities: SharedMobileHomeOverflowCapabilities = SharedMobileHomeOverflowCapabilities(),
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val isContextualMode = selectedIds.isNotEmpty()
    var showCreateShelf by remember { mutableStateOf(false) }
    var showAddToShelf by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRemoveFromRecents by remember { mutableStateOf(false) }
    var showCloseAllTabsConfirmation by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
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

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                if (isContextualMode) {
                    SharedMobileContextualTopBar(
                        selectedCount = selectedIds.size,
                        onClose = actions::clearSelection,
                        onSelectAll = actions::selectAll,
                        onPin = actions::toggleSelectedPins,
                        onAddToShelf = { showAddToShelf = true },
                        onTag = { showTagDialog = true },
                        onInfo = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }?.let { book ->
                                { infoBook = book }
                            }
                        },
                        onSave = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { actions.saveBook(book) }
                            }
                        },
                        onShare = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { actions.shareBook(book) }
                            }
                        },
                        onExportAnnotations = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }?.let { book ->
                                { actions.exportAnnotations(book) }
                            }
                        },
                        onDelete = { showRemoveFromRecents = true }
                    )
                } else {
                    SharedMobileHomeTopBar(
                        onDrawerClick = actions::openDrawer,
                        onSettingsClick = actions::openSettings,
                        onAppThemeClick = actions::openAppTheme,
                        onRecentLimitClick = actions::openRecentLimit,
                        isTabsEnabled = state.isTabsEnabled,
                        useStrictFileFilter = state.useStrictFileFilter,
                        usePdfFileNameAsDisplayName = state.usePdfFileNameAsDisplayName,
                        onAboutClick = actions::openAbout,
                        onTabsToggle = actions::toggleTabs,
                        onExternalFileBehaviorClick = actions::openExternalFileBehavior,
                        onStrictFilterToggle = actions::toggleStrictFileFilter,
                        onPdfFileNameToggle = actions::togglePdfFileNameDisplay,
                        onLanguageClick = actions::openLanguage,
                        hideReaderAi = state.hideReaderAi,
                        homeOverflowCapabilities = homeOverflowCapabilities,
                        onToggleReaderAi = actions::toggleReaderAi,
                        onClearReflowCache = actions::clearReflowCache,
                        onExportLogs = actions::exportLogs,
                    )
                }
            }
        }
    ) { padding ->
        val homeContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (model.isEmpty) {
                    SharedMobileEmptyLibrary(
                        title = if (model.isLibraryEmpty) {
                            readerString("library_empty_title", "Your library is empty")
                        } else {
                            readerString("recent_empty_title", "No recent files")
                        },
                        message = if (model.isLibraryEmpty) {
                            readerString("library_empty_desc", "Select a PDF, EPUB, comic, or document to start reading.")
                        } else {
                            readerString("recent_empty_desc", "Open books from the library and they will appear here.")
                        },
                        actionLabel = readerString("select_file", "Select file"),
                        onAction = actions::importBooks,
                        secondaryActionLabel = if (model.isLibraryEmpty) readerString("sync_folder", "Sync folder") else null,
                        onSecondaryAction = actions::navigateToFolderSync,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        if (state.isTabsEnabled && model.activeTabs.isNotEmpty()) {
                            item(key = "tabs") {
                                SharedMobileActiveTabs(
                                    openTabs = model.activeTabs,
                                    onOpenTab = { book ->
                                        when (mobileBookTapIntent(selectedIds)) {
                                            SharedMobileBookTapIntent.OPEN -> actions.openBook(book)
                                            SharedMobileBookTapIntent.TOGGLE_SELECTION -> actions.longPressBook(book)
                                        }
                                    },
                                    onCloseTab = actions::closeTab,
                                    onCloseAllTabs = { showCloseAllTabsConfirmation = true }
                                )
                            }
                        }

                        item(key = "recent") {
                            SharedMobileBookGridSection(
                                title = readerString("recent_files", "Recent files"),
                                books = state.mobileRecentBooks(),
                                selectedBookIds = selectedIds,
                                pinnedBookIds = state.pinnedHomeBookIds,
                                downloadingBookIds = state.downloadingBookIds,
                                onOpenBook = { book ->
                                    when (mobileBookTapIntent(selectedIds)) {
                                        SharedMobileBookTapIntent.OPEN -> actions.openBook(book)
                                        SharedMobileBookTapIntent.TOGGLE_SELECTION -> actions.longPressBook(book)
                                    }
                                },
                                onLongPressBook = { book ->
                                    if (shouldSelectBookOnLongPress(book.id, selectedIds)) {
                                        actions.longPressBook(book)
                                    }
                                },
                                onTogglePinned = actions::togglePinned,
                                onShowBookInfo = { infoBook = it }
                            )
                        }
                    }
                }
                if (!model.isEmpty) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = actions::importBooks) { Text(readerString("select_file", "Select file")) }
                        Button(onClick = actions::navigateToFolderSync) { Text(readerString("sync_folder", "Sync folder")) }
                    }
                }
                if (state.isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        val canRefresh = state.isSyncEnabled || state.syncedFolders.any { it.localSyncEnabled }
        if (canRefresh) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                homeContent()
            }
        } else {
            homeContent()
        }
    }
    if (showRemoveFromRecents) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_remove_from_recents", "Remove from recent files?"),
            body = "Remove ${selectedIds.size} selected book(s) from recent files? They will remain in your library.",
            onDismiss = { showRemoveFromRecents = false },
            onConfirm = {
                actions.removeSelectedBooksFromRecents()
                showRemoveFromRecents = false
            },
        )
    }
    if (showCloseAllTabsConfirmation) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_close_all_tabs", "Close all tabs?"),
            body = readerString(
                "dialog_close_all_tabs_desc",
                "Are you sure you want to close all open tabs?",
            ),
            confirmLabel = readerString("action_close", "Close"),
            emphasizeConfirm = true,
            onDismiss = { showCloseAllTabsConfirmation = false },
            onConfirm = {
                actions.closeAllTabs()
                showCloseAllTabsConfirmation = false
            },
        )
    }
    if (showAddToShelf) {
        SharedAddToShelfDialog(
            shelves = state.shelves.filter { it.type == ShelfType.MANUAL },
            onDismiss = { showAddToShelf = false },
            onCreateShelf = {
                showAddToShelf = false
                showCreateShelf = true
            },
            onShelvesSelected = { shelfIds ->
                actions.addSelectedBooksToShelves(shelfIds)
                showAddToShelf = false
            },
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("desktop_add_to_shelf", "Add selected books to shelf"),
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                actions.createShelfFromSelectedBooks(name)
                showCreateShelf = false
            }
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = state.allTags,
            selectedBookIds = selectedIds,
            books = state.rawLibraryBooks,
            onCreateAndAssign = actions::createAndAssignTag,
            onToggleTag = actions::toggleTagForSelectedBooks,
            onDeleteTag = actions::deleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = actions::importCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                actions.updateBook(it)
                infoBook = null
            },
            onRestore = {
                actions.updateBook(it)
                infoBook = null
            }
        )
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
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSortOrderChange: (SortOrder) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: (Set<String>) -> Unit = { _ -> },
    onFilterClick: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRemoveFilters: (LibraryFilters) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewShelfClick: () -> Unit = {},
    onOpenShelf: (Shelf) -> Unit = {},
    onLongPressShelf: (Shelf) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onToggleSelectedPins: (Set<String>) -> Unit = {},
    onUpdateBook: (BookItem) -> Unit = {},
    onSaveBook: (BookItem) -> Unit = {},
    onShareBook: (BookItem) -> Unit = {},
    onExportAnnotations: (BookItem) -> Unit = {},
    onImportCover: () -> Unit = {},
    importedCoverPath: String? = null,
    onCreateAndAssignTag: (Set<String>, String) -> Unit = { _, _ -> },
    onToggleTagForBooks: (Set<String>, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteTag: (String) -> Unit = {},
    onCreateShelf: (String, Set<String>) -> Unit = { _, _ -> },
    onAddBooksToShelves: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    onRemoveBooksFromShelf: (Shelf, Set<String>) -> Unit = { _, _ -> },
    onDeleteBooks: (Set<String>) -> Unit = {},
    onDeleteShelves: (Set<String>) -> Unit = {},
    onAddFolder: () -> Unit = {},
    onScanFolders: () -> Unit = {},
    onSyncFolderMetadata: () -> Unit = {},
    onFolderLocalSyncChange: (SyncedFolder, Boolean) -> Unit = { _, _ -> },
    onFolderFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit = { _, _ -> },
    onRemoveFolder: (SyncedFolder) -> Unit = {},
    onRenameShelf: (Shelf, String) -> Unit = { _, _ -> },
    onNavigateShelfBack: () -> Unit = {},
    onShelfAddBooksStateChange: (Boolean, AddBooksSource) -> Unit = { _, _ -> },
    onOpenCatalog: (OpdsCatalog) -> Unit = {},
    onOpenFeedUrl: (String) -> Unit = {},
    onOpdsNavigateBack: () -> Unit = {},
    onOpdsSearch: (String) -> Unit = {},
    onOpdsLoadNextPage: () -> Unit = {},
    onAddCatalog: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    onUpdateCatalog: (String, String, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onRemoveCatalog: (OpdsCatalog) -> Unit = {},
    onDeleteCatalogStreams: (String) -> Unit = {},
    onDownloadOpdsBook: (OpdsEntry, OpdsAcquisition) -> Unit = { _, _ -> },
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit = { _, _ -> },
    onClearOpdsError: () -> Unit = {},
    onOpdsDownloadLocationChange: (SharedOpdsDownloadLocation) -> Unit = {},
    opdsCoverContent: (@Composable (OpdsEntry, Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val selectedShelves = state.selectedShelfIds
    val isBookContextualMode = selectedIds.isNotEmpty()
    val isShelfContextualMode = selectedShelves.isNotEmpty() &&
        selectedTab in setOf(SharedMobileLibraryTab.SHELVES, SharedMobileLibraryTab.FOLDERS)
    var showFilters by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var showAddToShelf by remember { mutableStateOf(false) }
    var showDeleteBooks by remember { mutableStateOf(false) }
    var showDeleteShelves by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    val viewedShelf = state.viewingShelfId?.let { id -> state.shelves.firstOrNull { it.id == id } }
    val sortedSearchedBooks = remember(
        state.rawLibraryBooks,
        state.searchQuery,
        state.libraryFilters,
        state.syncedFolders,
        state.sortOrder,
        state.pinnedLibraryBookIds,
    ) {
        state.visibleIosLibraryBooks()
    }

    if (viewedShelf != null) {
        SharedMobileShelfDetail(
            shelf = viewedShelf,
            libraryBooks = state.rawLibraryBooks,
            shelves = state.shelves,
            knownTags = state.allTags,
            sortOrder = state.sortOrder,
            selectedBookIds = selectedIds,
            pinnedBookIds = state.pinnedLibraryBookIds,
            downloadingBookIds = state.downloadingBookIds,
            onBack = {
                viewedShelf.parentShelfId
                    ?.let { parentId -> state.shelves.firstOrNull { it.id == parentId } }
                    ?.let(onOpenShelf)
                    ?: onNavigateShelfBack()
            },
            onOpenChildShelf = onOpenShelf,
            onOpenBook = onOpenBook,
            onLongPressBook = onLongPressBook,
            onTogglePinned = onTogglePinned,
            onClearSelection = onClearSelection,
            onSortOrderChange = onSortOrderChange,
            onCreateAndAssignTag = onCreateAndAssignTag,
            onToggleTagForBooks = onToggleTagForBooks,
            onDeleteTag = onDeleteTag,
            onUpdateBook = onUpdateBook,
            onSaveBook = onSaveBook,
            onShareBook = onShareBook,
            onExportAnnotations = onExportAnnotations,
            onImportCover = onImportCover,
            importedCoverPath = importedCoverPath,
            onRemoveBooks = { ids -> onRemoveBooksFromShelf(viewedShelf, ids) },
            onAddBooks = { ids -> onAddBooksToShelves(ids, setOf(viewedShelf.id)) },
            onRenameShelf = { name -> onRenameShelf(viewedShelf, name) },
            onDeleteShelf = { onDeleteShelves(setOf(viewedShelf.id)) },
            initialIsAddingBooks = state.isAddingBooksToShelf,
            initialAddBooksSource = state.addBooksSource,
            onAddingBooksStateChange = onShelfAddBooksStateChange,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                when {
                    isBookContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedIds.size,
                        onClose = onClearSelection,
                        onSelectAll = {
                            onSelectAll(sortedSearchedBooks.mapTo(linkedSetOf()) { it.id })
                        },
                        onPin = { onToggleSelectedPins(selectedIds) },
                        onAddToShelf = { showAddToShelf = true },
                        onTag = { showTagDialog = true },
                        onShare = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { onShareBook(book) }
                            }
                        },
                        onExportAnnotations = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }?.let { book ->
                                { onExportAnnotations(book) }
                            }
                        },
                        onInfo = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }?.let { book ->
                                { infoBook = book }
                            }
                        },
                        onSave = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { onSaveBook(book) }
                            }
                        },
                        onDelete = { showDeleteBooks = true }
                    )

                    isShelfContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedShelves.size,
                        onClose = onClearSelection,
                        onSelectAll = null,
                        onPin = null,
                        onDelete = { showDeleteShelves = true }
                    )

                    state.isSearchActive -> SharedMobileSearchTopBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = { onSearchActiveChange(false) }
                    )

                    else -> SharedMobileLibraryTopBar(
                        selectedTab = selectedTab,
                        sortOrder = state.sortOrder,
                        isFilterActive = state.libraryFilters.isActive,
                        onFilterClick = { showFilters = true; onFilterClick() },
                        onSortOrderChange = onSortOrderChange,
                        onSearchClick = { onSearchActiveChange(true) },
                        onSettingsClick = onSettingsClick
                    )
                }
                if (!state.isSearchActive && !isBookContextualMode && !isShelfContextualMode) {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        SharedMobileLibraryTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { onTabChange(tab) },
                                text = { Text(tab.sharedMobileLabel()) }
                            )
                        }
                    }
                    if (selectedTab == SharedMobileLibraryTab.BOOKS && state.libraryFilters.isActive) {
                        SharedMobileLibraryFilterChips(
                            filters = state.libraryFilters,
                            fileTypesLabel = readerString(
                                "filter_types",
                                "Types: %1\$s",
                                state.libraryFilters.fileTypes.joinToString { it.name },
                            ),
                            foldersLabel = readerString(
                                "filter_folders",
                                "Folders: %1\$s",
                                state.libraryFilters.sourceFolders.size,
                            ),
                            statusLabel = readerString(
                                "filter_status",
                                "Status: %1\$s",
                                state.libraryFilters.readStatus.sharedMobileLabel(),
                            ),
                            tagsLabel = readerString(
                                "filter_tags",
                                "Tags: %1\$s",
                                state.libraryFilters.tagIds.size,
                            ),
                            clearContentDescription = readerString("action_clear", "Clear"),
                            onRemoveFilters = onRemoveFilters,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isBookContextualMode && !isShelfContextualMode) {
                when (selectedTab) {
                    SharedMobileLibraryTab.BOOKS -> if (sortedSearchedBooks.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            text = { Text(readerString("select_file", "Add file")) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = onImportBooks
                        )
                    }

                    SharedMobileLibraryTab.SHELVES -> ExtendedFloatingActionButton(
                        text = { Text(readerString("fab_new_shelf", "New shelf")) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showCreateShelf = true; onNewShelfClick() }
                    )

                    else -> Unit
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            SharedMobileLibraryTab.BOOKS -> when (
                mobileLibraryBooksState(sortedSearchedBooks.size, state.searchQuery)
            ) {
                SharedMobileLibraryBooksState.CONTENT -> SharedMobileBookList(
                    books = sortedSearchedBooks,
                    selectedBookIds = state.selectedBookIds,
                    pinnedBookIds = state.pinnedLibraryBookIds,
                    downloadingBookIds = state.downloadingBookIds,
                    onOpenBook = { book ->
                        when (mobileBookTapIntent(selectedIds)) {
                            SharedMobileBookTapIntent.OPEN -> onOpenBook(book)
                            SharedMobileBookTapIntent.TOGGLE_SELECTION -> onLongPressBook(book)
                        }
                    },
                    onLongPressBook = onLongPressBook,
                    onTogglePinned = onTogglePinned,
                    onShowBookInfo = { infoBook = it },
                    empty = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )

                SharedMobileLibraryBooksState.SEARCH_NO_RESULTS -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        readerString(
                            "no_results_found",
                            "No results found for \"%1\$s\"",
                            state.searchQuery.trim(),
                        )
                    )
                }

                SharedMobileLibraryBooksState.EMPTY_LIBRARY -> SharedMobileEmptyLibrary(
                    title = readerString("library_empty_title", "Your library is empty"),
                    message = readerString("library_empty_desc", "Select a PDF, EPUB, comic, or document to start reading."),
                    actionLabel = readerString("select_file", "Select file"),
                    onAction = onImportBooks,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            SharedMobileLibraryTab.SHELVES -> SharedMobileShelfList(
                shelves = topLevelMobileShelves(state.shelves),
                onOpenShelf = { shelf ->
                    when (mobileShelfTapIntent(selectedShelves)) {
                        SharedMobileShelfTapIntent.OPEN -> onOpenShelf(shelf)
                        SharedMobileShelfTapIntent.TOGGLE_SELECTION -> onLongPressShelf(shelf)
                    }
                },
                onLongPressShelf = { shelf ->
                    if (
                        shelf.type == ShelfType.MANUAL &&
                        shelf.id != "unshelved" &&
                        shouldSelectShelfOnLongPress(shelf.id, selectedShelves)
                    ) {
                        onLongPressShelf(shelf)
                    }
                },
                selectedShelfIds = state.selectedShelfIds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.FOLDERS -> SharedMobileFolderSyncScreen(
                folders = state.syncedFolders,
                books = state.rawLibraryBooks,
                isLoading = state.isRefreshing,
                onAddFolder = onAddFolder,
                onScanAll = onScanFolders,
                onSyncMetadata = onSyncFolderMetadata,
                onLocalSyncChange = onFolderLocalSyncChange,
                onFileTypesChange = onFolderFileTypesChange,
                onRemoveFolder = onRemoveFolder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.CATALOGS -> {
                val catalogModifier = Modifier.fillMaxSize().padding(padding)
                if (opdsCoverContent == null) {
                    SharedOpdsScreen(
                        state = opdsState, localLibraryBooks = state.rawLibraryBooks,
                        onOpenCatalog = onOpenCatalog, onOpenFeedUrl = onOpenFeedUrl,
                        onNavigateBack = onOpdsNavigateBack, onSearch = onOpdsSearch,
                        onLoadNextPage = onOpdsLoadNextPage, onAddCatalog = onAddCatalog,
                        onUpdateCatalog = onUpdateCatalog, onRemoveCatalog = onRemoveCatalog,
                        onDeleteCatalogStreams = onDeleteCatalogStreams,
                        onDownloadBook = onDownloadOpdsBook, onReadBook = onOpenBook,
                        onStreamBook = onStreamOpdsBook, onClearError = onClearOpdsError,
                        onDownloadLocationChange = onOpdsDownloadLocationChange,
                        syncedFolders = state.syncedFolders,
                        mobileLayout = true, modifier = catalogModifier,
                    )
                } else {
                    SharedOpdsScreen(
                        state = opdsState, localLibraryBooks = state.rawLibraryBooks,
                        onOpenCatalog = onOpenCatalog, onOpenFeedUrl = onOpenFeedUrl,
                        onNavigateBack = onOpdsNavigateBack, onSearch = onOpdsSearch,
                        onLoadNextPage = onOpdsLoadNextPage, onAddCatalog = onAddCatalog,
                        onUpdateCatalog = onUpdateCatalog, onRemoveCatalog = onRemoveCatalog,
                        onDeleteCatalogStreams = onDeleteCatalogStreams,
                        onDownloadBook = onDownloadOpdsBook, onReadBook = onOpenBook,
                        onStreamBook = onStreamOpdsBook, onClearError = onClearOpdsError,
                        onDownloadLocationChange = onOpdsDownloadLocationChange,
                        syncedFolders = state.syncedFolders,
                        coverContent = opdsCoverContent, mobileLayout = true, modifier = catalogModifier,
                    )
                }
            }
        }
    }

    if (showFilters) {
        SharedMobileLibraryFilterDialog(
            state = state,
            onDismiss = { showFilters = false },
            onFiltersChange = onRemoveFilters
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = if (selectedIds.isEmpty()) {
                readerString("fab_new_shelf", "New shelf")
            } else {
                readerString("desktop_add_to_shelf", "Add selected books to shelf")
            },
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                onCreateShelf(name, selectedIds)
                showCreateShelf = false
            }
        )
    }
    if (showAddToShelf) {
        SharedAddToShelfDialog(
            shelves = state.shelves.filter { it.type == ShelfType.MANUAL },
            onDismiss = { showAddToShelf = false },
            onCreateShelf = {
                showAddToShelf = false
                showCreateShelf = true
            },
            onShelvesSelected = { shelfIds ->
                onAddBooksToShelves(selectedIds, shelfIds)
                showAddToShelf = false
            },
        )
    }
    if (showDeleteBooks) {
        val containsFolderBooks = state.rawLibraryBooks.any {
            it.id in selectedIds && it.sourceFolder != null
        }
        SharedMobileDeleteConfirmationDialog(
            title = "Permanently delete ${selectedIds.size} selected book(s)?",
            body = if (containsFolderBooks) {
                "Warning: Some selected items are synced from a local folder. Proceeding will delete the actual files from your device storage.\n\nThis action cannot be undone."
            } else {
                "Permanently delete ${selectedIds.size} selected book(s)? This action cannot be undone."
            },
            confirmLabel = readerString("action_delete", "Delete"),
            emphasizeConfirm = containsFolderBooks,
            onDismiss = { showDeleteBooks = false },
            onConfirm = {
                onDeleteBooks(selectedIds)
                showDeleteBooks = false
            }
        )
    }
    if (showDeleteShelves) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_delete_shelves_title", "Delete shelves?"),
            body = "Delete ${selectedShelves.size} selected shelf/shelves? Books will remain in the library.",
            confirmLabel = readerString("action_delete", "Delete"),
            onDismiss = { showDeleteShelves = false },
            onConfirm = {
                onDeleteShelves(selectedShelves)
                showDeleteShelves = false
            }
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = onImportCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                onUpdateBook(it)
                infoBook = null
            },
            onRestore = {
                onUpdateBook(it)
                infoBook = null
            }
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = state.allTags,
            selectedBookIds = selectedIds,
            books = state.rawLibraryBooks,
            onCreateAndAssign = { name -> onCreateAndAssignTag(selectedIds, name) },
            onToggleTag = { tagId, assign -> onToggleTagForBooks(selectedIds, tagId, assign) },
            onDeleteTag = onDeleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileShelfDetail(
    shelf: Shelf,
    libraryBooks: List<BookItem>,
    shelves: List<Shelf>,
    knownTags: List<Tag>,
    sortOrder: SortOrder,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onBack: () -> Unit,
    onOpenChildShelf: (Shelf) -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onClearSelection: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onCreateAndAssignTag: (Set<String>, String) -> Unit,
    onToggleTagForBooks: (Set<String>, String, Boolean) -> Unit,
    onDeleteTag: (String) -> Unit,
    onUpdateBook: (BookItem) -> Unit,
    onSaveBook: (BookItem) -> Unit,
    onShareBook: (BookItem) -> Unit,
    onExportAnnotations: (BookItem) -> Unit,
    onImportCover: () -> Unit,
    importedCoverPath: String?,
    onRemoveBooks: (Set<String>) -> Unit,
    onAddBooks: (Set<String>) -> Unit,
    onRenameShelf: (String) -> Unit,
    onDeleteShelf: () -> Unit,
    initialIsAddingBooks: Boolean,
    initialAddBooksSource: AddBooksSource,
    onAddingBooksStateChange: (Boolean, AddBooksSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRemoveBooks by remember { mutableStateOf(false) }
    var isAddingBooks by remember(shelf.id) { mutableStateOf(initialIsAddingBooks) }
    var addBooksSource by remember(shelf.id) { mutableStateOf(initialAddBooksSource) }
    var booksSelectedForAdding by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember(shelf.id) { mutableStateOf(false) }
    var searchQuery by remember(shelf.id) { mutableStateOf("") }
    val shelfSearchFocusRequester = remember(shelf.id) { FocusRequester() }
    var showRenameShelf by remember { mutableStateOf(false) }
    var showDeleteShelf by remember { mutableStateOf(false) }
    val selectedShelfBooks = shelf.directBooks.filter { it.id in selectedBookIds }
    val normalizedQuery = searchQuery.trim()
    val childShelves = remember(shelves, shelf.childShelfIds) {
        shelf.childShelfIds.mapNotNull { childId -> shelves.firstOrNull { it.id == childId } }
    }
    val visibleChildShelves = remember(childShelves, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            childShelves
        } else {
            childShelves.filter { child ->
                child.name.contains(normalizedQuery, ignoreCase = true) ||
                    child.books.filteredSharedMobileBooks(normalizedQuery).isNotEmpty()
            }
        }
    }
    val visibleBooks = remember(shelf.directBooks, normalizedQuery, sortOrder) {
        sortBooks(shelf.directBooks.filteredSharedMobileBooks(normalizedQuery), sortOrder)
    }
    LaunchedEffect(isSearchActive, shelf.id) {
        if (isSearchActive) shelfSearchFocusRequester.requestFocus()
    }
    if (isAddingBooks) {
        SharedMobileAddBooksToShelfScreen(
            shelf = shelf,
            libraryBooks = libraryBooks,
            shelves = shelves,
            source = addBooksSource,
            sortOrder = sortOrder,
            selectedBookIds = booksSelectedForAdding,
            downloadingBookIds = downloadingBookIds,
            onSourceChange = {
                addBooksSource = it
                booksSelectedForAdding =
                    mobileAddBooksSelectionAfterSourceChange(booksSelectedForAdding)
                onAddingBooksStateChange(true, it)
            },
            onSortOrderChange = onSortOrderChange,
            onToggleBook = { id ->
                booksSelectedForAdding = if (id in booksSelectedForAdding) {
                    booksSelectedForAdding - id
                } else {
                    booksSelectedForAdding + id
                }
            },
            onBack = {
                isAddingBooks = false
                booksSelectedForAdding = emptySet()
                onAddingBooksStateChange(false, AddBooksSource.UNSHELVED)
            },
            onAddSelectedBooks = {
                onAddBooks(booksSelectedForAdding)
                isAddingBooks = false
                booksSelectedForAdding = emptySet()
                onAddingBooksStateChange(false, AddBooksSource.UNSHELVED)
            },
            modifier = modifier,
        )
        return
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectedShelfBooks.isNotEmpty()) {
                SharedMobileContextualTopBar(
                    selectedCount = selectedShelfBooks.size,
                    onClose = onClearSelection,
                    onSelectAll = null,
                    onPin = null,
                    onTag = { showTagDialog = true },
                    onInfo = selectedShelfBooks.singleOrNull()?.let { book -> { infoBook = book } },
                    onSave = selectedShelfBooks.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { book -> { onSaveBook(book) } },
                    onShare = selectedShelfBooks.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { book -> { onShareBook(book) } },
                    onExportAnnotations = selectedShelfBooks.singleOrNull()?.let { book -> { onExportAnnotations(book) } },
                    onDelete = { showRemoveBooks = true },
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(readerString("search_placeholder", "Search title or author…")) },
                            singleLine = true,
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = readerString("content_desc_clear_query", "Clear query"))
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(shelfSearchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("content_desc_close_search", "Close search"))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (shelf.type == ShelfType.FOLDER && shelf.childShelfCount > 0) {
                                    "${shelf.childShelfCount} folder${if (shelf.childShelfCount == 1) "" else "s"} · ${shelf.directBookCount} book${if (shelf.directBookCount == 1) "" else "s"}"
                                } else {
                                    "${shelf.bookCount} book${if (shelf.bookCount == 1) "" else "s"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Icon(Icons.Default.Sort, contentDescription = readerString("content_desc_sort", "Sort"))
                                Spacer(Modifier.width(8.dp))
                                Text(sortOrder.sharedMobileLabel())
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.sharedMobileLabel()) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (order == sortOrder) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = readerString("content_desc_search_shelf", "Search shelf"))
                        }
                        if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_rename_shelf", "Rename shelf")) },
                                        onClick = {
                                            showMoreMenu = false
                                            showRenameShelf = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_delete_shelf", "Delete shelf")) },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeleteShelf = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved" && selectedShelfBooks.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isAddingBooks = true
                        addBooksSource = AddBooksSource.UNSHELVED
                        booksSelectedForAdding = emptySet()
                        onAddingBooksStateChange(true, AddBooksSource.UNSHELVED)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("fab_add_books", "Add books")) },
                )
            }
        },
    ) { padding ->
        if (visibleChildShelves.isEmpty() && visibleBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (normalizedQuery.isBlank()) {
                    readerString("shelf_empty", "This shelf is empty")
                } else {
                    readerString("no_results_found", "No results found for \"%1\$s\"", normalizedQuery)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (visibleChildShelves.isNotEmpty()) {
                    if (shelf.type == ShelfType.FOLDER) {
                        item("folder_section") {
                            Text(
                                readerString("section_folders", "Folders"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visibleChildShelves, key = { "child_${it.id}" }) { child ->
                        SharedMobileShelfRow(
                            shelf = child,
                            selected = false,
                            onClick = { onOpenChildShelf(child) },
                            onLongClick = {},
                        )
                    }
                }
                if (visibleBooks.isNotEmpty() && shelf.type == ShelfType.FOLDER && visibleChildShelves.isNotEmpty()) {
                    item("file_section") {
                        Text(
                            readerString("section_files", "Files"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(visibleBooks, key = { "book_${it.id}" }) { book ->
                    SharedMobileLibraryListItem(
                        book = book,
                        selected = book.id in selectedBookIds,
                        pinned = book.id in pinnedBookIds,
                        downloading = book.id in downloadingBookIds,
                        onClick = {
                            when (mobileBookTapIntent(selectedBookIds)) {
                                SharedMobileBookTapIntent.OPEN -> onOpenBook(book)
                                SharedMobileBookTapIntent.TOGGLE_SELECTION -> onLongPressBook(book)
                            }
                        },
                        onLongClick = {
                            if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                                onLongPressBook(book)
                            }
                        },
                        onTogglePinned = { onTogglePinned(book) },
                        onShowBookInfo = { infoBook = book },
                    )
                }
            }
        }
    }
    if (showRemoveBooks) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_remove_from_shelf", "Remove from shelf?"),
            body = "Remove ${selectedShelfBooks.size} selected book(s) from \"${shelf.name}\"? The books will remain in the library.",
            onDismiss = { showRemoveBooks = false },
            onConfirm = {
                onRemoveBooks(selectedShelfBooks.mapTo(mutableSetOf()) { it.id })
                showRemoveBooks = false
            },
        )
    }
    if (showRenameShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("dialog_rename_shelf", "Rename shelf"),
            initialName = shelf.name,
            confirmLabel = readerString("action_rename", "Rename"),
            onDismiss = { showRenameShelf = false },
            onCreate = { name ->
                onRenameShelf(name)
                showRenameShelf = false
            },
        )
    }
    if (showDeleteShelf) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_delete_shelf", "Delete shelf?"),
            body = readerString(
                "dialog_delete_shelf_desc",
                "Are you sure you want to delete \"%1\$s\"? All books will be moved to Unshelved.",
                shelf.name,
            ),
            confirmLabel = readerString("action_delete", "Delete"),
            onDismiss = { showDeleteShelf = false },
            onConfirm = {
                onDeleteShelf()
                showDeleteShelf = false
            },
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = knownTags,
            selectedBookIds = selectedShelfBooks.mapTo(mutableSetOf()) { it.id },
            books = selectedShelfBooks,
            onCreateAndAssign = { name ->
                onCreateAndAssignTag(selectedShelfBooks.mapTo(mutableSetOf()) { it.id }, name)
            },
            onToggleTag = { tagId, assign ->
                onToggleTagForBooks(
                    selectedShelfBooks.mapTo(mutableSetOf()) { it.id },
                    tagId,
                    assign,
                )
            },
            onDeleteTag = onDeleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = knownTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = onImportCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                onUpdateBook(it)
                infoBook = null
            },
            onRestore = {
                onUpdateBook(it)
                infoBook = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileAddBooksToShelfScreen(
    shelf: Shelf,
    libraryBooks: List<BookItem>,
    shelves: List<Shelf>,
    source: AddBooksSource,
    sortOrder: SortOrder,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onSourceChange: (AddBooksSource) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleBook: (String) -> Unit,
    onBack: () -> Unit,
    onAddSelectedBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val availableBooks = remember(libraryBooks, shelves, shelf.id, source, sortOrder) {
        sortBooks(
            booksAvailableForShelfAddition(libraryBooks, shelves, shelf.id, source),
            sortOrder,
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(readerString("add_to_shelf", "Add to %1\$s", shelf.name)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("action_back", "Back"))
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(sortOrder.sharedMobileLabel())
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.sharedMobileLabel()) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (order == sortOrder) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddBooksSource.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == source,
                            onClick = { onSourceChange(candidate) },
                            label = {
                                Text(
                                    when (candidate) {
                                        AddBooksSource.UNSHELVED -> readerString("add_books_source_unshelved", "Unshelved")
                                        AddBooksSource.ALL_BOOKS -> readerString("add_books_source_all_books", "All books")
                                    }
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedBookIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddSelectedBooks,
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(readerString("fab_add_count", "Add (%1\$d)", selectedBookIds.size)) },
                )
            }
        },
    ) { padding ->
        if (availableBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (source == AddBooksSource.UNSHELVED) {
                        readerString("no_unshelved_books", "No unshelved books")
                    } else {
                        readerString("all_books_in_shelf", "All books are already in this shelf")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            SharedMobileBookList(
                books = availableBooks,
                selectedBookIds = selectedBookIds,
                pinnedBookIds = emptySet(),
                downloadingBookIds = downloadingBookIds,
                onOpenBook = { onToggleBook(it.id) },
                onLongPressBook = { onToggleBook(it.id) },
                onTogglePinned = {},
                empty = {},
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileTagSelectionSheet(
    allTags: List<Tag>,
    selectedBookIds: Set<String>,
    books: List<BookItem>,
    onCreateAndAssign: (String) -> Unit,
    onToggleTag: (String, Boolean) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var tagPendingDeletion by remember { mutableStateOf<Tag?>(null) }
    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) {
            allTags
        } else {
            allTags.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }
    val exactMatch = allTags.any { it.name.equals(searchQuery.trim(), ignoreCase = true) }
    val selectedBooks = remember(books, selectedBookIds) {
        books.filter { it.id in selectedBookIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                readerString("title_apply_tags", "Apply tags"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(readerString("placeholder_search_create_tag", "Search or create a tag")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (searchQuery.isNotBlank() && !exactMatch) {
                    item("create_tag") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCreateAndAssign(searchQuery.trim())
                                    searchQuery = ""
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                readerString("action_create_tag", "Create \"%1\$s\"", searchQuery.trim()),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(filteredTags, key = { it.id }) { tag ->
                    val tagColor = Color(tag.color ?: 0xFF64B5F6.toInt())
                    val checkedCount = selectedBooks.count { book -> book.tags.any { it.id == tag.id } }
                    val toggleState = when (checkedCount) {
                        0 -> ToggleableState.Off
                        selectedBookIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleTag(tag.id, toggleState != ToggleableState.On) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = toggleState, onClick = null)
                        Surface(
                            shape = CircleShape,
                            color = tagColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(tagColor, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tagPendingDeletion = tag }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = readerString("menu_delete_tag", "Delete tag"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    tagPendingDeletion?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDeletion = null },
            title = { Text(readerString("menu_delete_tag", "Delete tag")) },
            text = {
                Text(
                    readerString(
                        "dialog_delete_tag_desc",
                        "Delete \"%1\$s\"? It will be removed from every book.",
                        tag.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag.id)
                        tagPendingDeletion = null
                    }
                ) {
                    Text(readerString("action_delete", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDeletion = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileLibraryFilterDialog(
    state: SharedReaderScreenState,
    onDismiss: () -> Unit,
    onFiltersChange: (LibraryFilters) -> Unit
) {
    var currentFilters by remember(state.libraryFilters, state.syncedFolders) {
        mutableStateOf(state.libraryFilters.withIosFolderFilterIdentities(state.syncedFolders))
    }
    val readableTypes = remember { SharedFileCapabilities.readableTypesFor(ReaderPlatform.IOS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(readerString("filter_library", "Filter library"), style = MaterialTheme.typography.titleLarge)

            Text(readerString("filter_file_type", "File type"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readableTypes.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                fileTypes = currentFilters.fileTypes.toggleMember(type)
                            )
                        },
                        label = { Text(SharedFileCapabilities.displayNameFor(type)) },
                    )
                }
            }

            Text(readerString("filter_source_folder", "Source folder"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = IN_APP_STORAGE_SOURCE in currentFilters.sourceFolders,
                    onClick = {
                        currentFilters = currentFilters.copy(
                            sourceFolders = currentFilters.sourceFolders.toggleMember(IN_APP_STORAGE_SOURCE)
                        )
                    },
                    label = { Text(readerString("filter_in_app_storage", "In-app storage")) },
                )
                state.syncedFolders.forEach { folder ->
                    FilterChip(
                        selected = currentFilters.sourceFolders.any {
                            it == folder.uriString || it == folder.name
                        },
                        onClick = {
                            currentFilters = currentFilters.toggleIosFolderFilter(folder)
                        },
                        label = { Text(folder.name) },
                    )
                }
            }

            Text(readerString("filter_read_status", "Reading status"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(status.sharedMobileLabel()) },
                    )
                }
            }

            if (state.allTags.isNotEmpty()) {
                Text(readerString("section_tags", "Tags"), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in currentFilters.tagIds,
                            onClick = {
                                currentFilters = currentFilters.copy(
                                    tagIds = currentFilters.tagIds.toggleMember(tag.id)
                                )
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            Color(tag.color ?: 0xFF64B5F6.toInt()),
                                            CircleShape,
                                        )
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) {
                    Text(readerString("clear_all", "Clear all"))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onFiltersChange(currentFilters)
                        onDismiss()
                    }
                ) {
                    Text(readerString("action_apply", "Apply"))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

internal fun <T> Set<T>.toggleMember(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun SharedMobileCreateShelfDialog(
    title: String,
    initialName: String = "",
    fieldLabel: String = "Shelf name",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(fieldLabel) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) } }
    )
}

@Composable
private fun SharedMobileDeleteConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String = "Remove",
    emphasizeConfirm: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (emphasizeConfirm) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) } }
    )
}

enum class SharedMobileLibraryTab {
    BOOKS,
    SHELVES,
    FOLDERS,
    CATALOGS,
}

@Composable
private fun SharedMobileLibraryTab.sharedMobileLabel(): String = when (this) {
    SharedMobileLibraryTab.BOOKS -> readerString("tab_all_books", "All Books")
    SharedMobileLibraryTab.SHELVES -> readerString("tab_shelves", "Shelves")
    SharedMobileLibraryTab.FOLDERS -> readerString("tab_folders", "Folders")
    SharedMobileLibraryTab.CATALOGS -> readerString("tab_catalogs", "Catalogs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileHomeTopBar(
    onDrawerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppThemeClick: () -> Unit,
    onRecentLimitClick: () -> Unit,
    isTabsEnabled: Boolean,
    useStrictFileFilter: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    onAboutClick: () -> Unit,
    onTabsToggle: () -> Unit,
    onExternalFileBehaviorClick: () -> Unit,
    onStrictFilterToggle: () -> Unit,
    onPdfFileNameToggle: () -> Unit,
    onLanguageClick: () -> Unit,
    hideReaderAi: Boolean,
    homeOverflowCapabilities: SharedMobileHomeOverflowCapabilities,
    onToggleReaderAi: () -> Unit,
    onClearReflowCache: () -> Unit,
    onExportLogs: () -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val overflowItems = sharedMobileHomeOverflowItems(
        state = SharedMobileHomeOverflowState(
            tabsEnabled = isTabsEnabled,
            screenCaptureProtectionEnabled = false,
            strictFileFilterEnabled = useStrictFileFilter,
            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            hideReaderAi = hideReaderAi,
        ),
        capabilities = homeOverflowCapabilities,
    )

    @Composable
    fun itemLabel(action: SharedMobileHomeOverflowAction): String = when (action) {
        SharedMobileHomeOverflowAction.ABOUT -> readerString("about_title", "About")
        SharedMobileHomeOverflowAction.TABS_TOGGLE -> readerString(
            "options_enable_multi_tab_reading",
            "Enable multi-tab reading",
        )
        SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION -> readerString(
            "options_screen_capture_protection",
            "Screen capture protection",
        )
        SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR -> readerString(
            "options_external_file_behavior",
            "External file behavior",
        )
        SharedMobileHomeOverflowAction.STRICT_FILE_FILTER -> readerString(
            "options_use_strict_file_filter",
            "Use strict file filter",
        )
        SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME -> readerString(
            "options_use_pdf_filename_display_name",
            "Use PDF filename as display name",
        )
        SharedMobileHomeOverflowAction.LANGUAGE -> readerString("options_language", "Language")
        SharedMobileHomeOverflowAction.TOGGLE_READER_AI -> if (hideReaderAi) {
            readerString("options_show_ai_in_reader", "Show AI in reader")
        } else {
            readerString("options_hide_ai_in_reader", "Hide AI in reader")
        }
        SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE -> readerString(
            "options_clear_book_cache",
            "Clear book cache",
        )
        SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE -> readerString(
            "options_clear_reflow_cache",
            "Clear reflow cache",
        )
        SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION -> readerString(
            "options_test_panel_ml_detection",
            "Test panel detection",
        )
        SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION -> readerString(
            "options_test_speech_bubble_ml_detection",
            "Test speech bubble detection",
        )
        SharedMobileHomeOverflowAction.EXPORT_LOGS -> readerString(
            "options_export_logs_last_lines",
            "Export Logs (Last 5000 lines)",
            5000,
        )
        SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT -> readerString(
            "debug_show_device_management",
            "Device management",
        )
        SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA -> readerString(
            "debug_clear_cloud_local_data",
            "Clear cloud and local data",
        )
    }

    fun onItemClick(action: SharedMobileHomeOverflowAction) {
        when (action) {
            SharedMobileHomeOverflowAction.ABOUT -> onAboutClick()
            SharedMobileHomeOverflowAction.TABS_TOGGLE -> onTabsToggle()
            SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION -> Unit
            SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR -> onExternalFileBehaviorClick()
            SharedMobileHomeOverflowAction.STRICT_FILE_FILTER -> onStrictFilterToggle()
            SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME -> onPdfFileNameToggle()
            SharedMobileHomeOverflowAction.LANGUAGE -> onLanguageClick()
            SharedMobileHomeOverflowAction.TOGGLE_READER_AI -> onToggleReaderAi()
            SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE -> Unit
            SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE -> onClearReflowCache()
            SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION -> Unit
            SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION -> Unit
            SharedMobileHomeOverflowAction.EXPORT_LOGS -> onExportLogs()
            SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT -> Unit
            SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA -> Unit
        }
    }

    CenterAlignedTopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onDrawerClick, modifier = Modifier.testTag("MobileHomeMenu")) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("MobileHomeSettings")) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = onAppThemeClick, modifier = Modifier.testTag("MobileHomeTheme")) {
                Icon(Icons.Default.Palette, contentDescription = readerString("app_theme", "App theme"))
            }
            IconButton(onClick = onRecentLimitClick, modifier = Modifier.testTag("MobileHomeRecentLimit")) {
                Icon(
                    Icons.Default.FormatListNumbered,
                    contentDescription = readerString("options_recent_limit", "Recent files limit"),
                )
            }
            Box {
                IconButton(onClick = { showOptionsMenu = true }, modifier = Modifier.testTag("MobileHomeMore")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                ) {
                    overflowItems.forEachIndexed { index, item ->
                        if (index > 0 && overflowItems[index - 1].section != item.section) {
                            HorizontalDivider()
                        }
                        SharedMobileHomeOption(
                            label = itemLabel(item.action),
                            checked = item.checked,
                            onClick = {
                                showOptionsMenu = false
                                onItemClick(item.action)
                            },
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SharedMobileHomeOption(
    label: String,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = if (checked == true) {
            { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Enabled")) }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileLibraryTopBar(
    selectedTab: SharedMobileLibraryTab,
    sortOrder: SortOrder,
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = { Text(readerString("library_title", "Library")) },
        actions = {
            if (selectedTab == SharedMobileLibraryTab.BOOKS) {
                IconButton(onClick = onFilterClick, modifier = Modifier.testTag("MobileLibraryFilter")) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = readerString("content_desc_filter", "Filter"),
                        tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SharedMobileLibrarySortControl(
                    sortOrder = sortOrder,
                    labels = SortOrder.entries.associateWith { it.sharedMobileLabel() },
                    selectedContentDescription = readerString("content_desc_selected", "Selected"),
                    onSortOrderChange = onSortOrderChange,
                    icon = {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = readerString("content_desc_sort", "Sort"),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                IconButton(onClick = onSearchClick, modifier = Modifier.testTag("MobileLibrarySearch")) {
                    Icon(Icons.Default.Search, contentDescription = readerString("action_search", "Search"))
                }
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("MobileLibrarySettings")) {
                Icon(Icons.Default.Settings, contentDescription = readerString("settings", "Settings"))
            }
        }
    )
}
