package com.aryan.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.View
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.AudiobookImporter
import com.aryan.reader.audiobook.AudiobookController
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.ui.AppIcon
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerDestination
import com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel
import com.aryan.reader.shared.ui.SharedAnnotationExportFormatDialog
import com.aryan.reader.shared.ui.sharedAnnotationExportFormatOptions
import com.aryan.reader.shared.ui.activeFilterCount
import com.aryan.reader.shared.ui.matchesMobileUnifiedLibraryFilter
import com.aryan.reader.shared.ui.mobileUnifiedContinueReadingBook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

internal typealias UnifiedLibrarySection = com.aryan.reader.shared.ui.MobileUnifiedLibrarySection
internal typealias UnifiedLibraryFilter = com.aryan.reader.shared.ui.MobileUnifiedLibraryFilter

@androidx.annotation.OptIn(UnstableApi::class)
internal fun shouldAutoStartTtsAudiobook(
    requestedBookId: String?,
    playback: com.aryan.reader.tts.TtsPlaybackManager.TtsState
): Boolean = requestedBookId == null ||
    playback.playbackSource != "AUDIOBOOK_TTS" ||
    playback.bookId != requestedBookId

/** Android-only successor experiment for the separate Home and Library destinations. */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedLibraryScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val accountDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val audiobookImporter = remember(context) { AudiobookImporter(context.applicationContext) }
    val importedAudiobooks by remember(context) { AppDatabase.getDatabase(context).audiobookDao().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val bookTtsProgress by remember(context) { AppDatabase.getDatabase(context).bookTtsListeningProgressDao().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val globalAudiobookController = remember(context) { AudiobookController(context) }
    val importedPlayback by globalAudiobookController.state.collectAsStateWithLifecycle()
    val ttsPlayback by viewModel.ttsController.ttsState.collectAsStateWithLifecycle()
    LaunchedEffect(importedAudiobooks.isNotEmpty()) {
        if (importedAudiobooks.isNotEmpty()) globalAudiobookController.connectSession()
    }
    DisposableEffect(globalAudiobookController) { onDispose(globalAudiobookController::release) }
    val activeListeningItemId = when {
        ttsPlayback.playbackSource == "AUDIOBOOK_TTS" ->
            ttsPlayback.bookId?.let { "tts-$it" }

        importedPlayback.bookId != null ->
            importedPlayback.bookId

        else -> null
    }
    var selectedShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(UnifiedLibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    // Filtering + sorting is O(n log n) on Main; debounce keystrokes so each
    // character doesn't pay a full pass. The field itself stays instant —
    // only the list lags up to 300ms behind fast typing.
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }
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
    var showAudiobookAddSheet by rememberSaveable { mutableStateOf(false) }
    var showTtsBookPicker by rememberSaveable {
        mutableStateOf(false)
    }
    var audiobookPlayerItem by remember { mutableStateOf<AudiobookUiItem?>(null) }
    // Shelf CRUD moved here from the retired Library screen: rename, delete, add books,
    // and remove-from-shelf are driven off the ViewModel's shelf dialogs so both the
    // section and its dialogs share one source of truth.
    var shelfIdPendingRename by remember { mutableStateOf<String?>(null) }
    var shelfIdPendingDelete by remember { mutableStateOf<String?>(null) }
    var showRemoveSelectedFromShelfConfirmation by remember { mutableStateOf(false) }
    var shelfIdAddingBooks by remember { mutableStateOf<String?>(null) }
    var showCloseAllTabsDialog by remember { mutableStateOf(false) }
    val canUseCloudFolderSync = uiState.canUseCloudFolderSync()
    val canPullToSync = com.aryan.reader.shared.ui.canPullToSyncLibrary(
        cloudSyncEnabled = uiState.isSyncEnabled,
        foldersWithLocalSyncEnabled = uiState.syncedFolders.map { it.localSyncEnabled },
    )
    var cloudFolderSelection by remember(uiState.currentUser?.uid) {
        mutableStateOf(viewModel.cloudFolderSyncSelection())
    }

    LaunchedEffect(uiState.currentUser?.uid) {
        cloudFolderSelection = viewModel.cloudFolderSyncSelection()
    }
    LaunchedEffect(Unit) {
        CloudFolderSyncEvents.stateChanged.collect {
            cloudFolderSelection = viewModel.cloudFolderSyncSelection()
        }
    }

    // Drive consent is requested by the ViewModel but the intent must be launched by
    // whichever host screen is mounted, so this observer follows Home into Library Beta.
    val drivePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDrivePermissionResult(result.data)
        } else {
            Timber.w("Google Sign In for Drive failed with result code: ${result.resultCode}")
            viewModel.onDrivePermissionFlowCancelled()
        }
    }
    LaunchedEffect(uiState.isRequestingDrivePermission) {
        if (uiState.isRequestingDrivePermission) {
            drivePermissionLauncher.launch(viewModel.getDriveSignInIntent(context))
        }
    }

    val filePicker = rememberFilePickerLauncher(viewModel::onFilesSelected)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addSyncedFolder)
    }
    val audiobookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                audiobookImporter.import(it)
                    .onSuccess { imported -> viewModel.showBanner(context.getString(R.string.audiobooks_imported, imported.book.title ?: imported.book.displayName)) }
                    .onFailure { error -> viewModel.showBanner(error.message ?: context.getString(R.string.audiobooks_import_failed), isError = true) }
            }
        }
    }
    val audiobookMultiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val results = audiobookImporter.importAll(uris)
            val imported = results.count { it.isSuccess }
            val failed = results.size - imported
            viewModel.showBanner("Imported $imported audiobook${if (imported == 1) "" else "s"}${if (failed > 0) "; $failed failed" else ""}", isError = imported == 0)
        }
    }
    val audiobookFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { folderUri -> scope.launch {
            val results = audiobookImporter.importFolder(folderUri)
            val imported = results.count { it.isSuccess }
            val failed = results.size - imported
            viewModel.showBanner("Imported $imported audiobook${if (imported == 1) "" else "s"}${if (failed > 0) "; $failed failed" else ""}", isError = imported == 0)
        } }
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
    val saveJsonAnnotationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AnnotationExportFormat.JSON.mimeType)
    ) { destination ->
        val contents = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (destination != null && contents != null) viewModel.saveAnnotationExport(contents, destination)
    }
    val saveCsvAnnotationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AnnotationExportFormat.CSV.mimeType)
    ) { destination ->
        val contents = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (destination != null && contents != null) viewModel.saveAnnotationExport(contents, destination)
    }

    fun launchDocumentPicker(onUnavailable: () -> Unit = {}, launch: () -> Unit) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
            viewModel.showBanner(context.getString(R.string.document_picker_unavailable), isError = true)
        }
    }

    fun exportAnnotations(item: RecentFileItem, format: AnnotationExportFormat) {
        viewModel.prepareAnnotationExport(item, format) { prepared ->
            pendingAnnotationExportText = prepared.contents
            when (format) {
                AnnotationExportFormat.MARKDOWN -> launchDocumentPicker(
                    onUnavailable = { pendingAnnotationExportText = null }
                ) { saveMarkdownAnnotationsLauncher.launch(prepared.fileName) }
                AnnotationExportFormat.TEXT -> launchDocumentPicker(
                    onUnavailable = { pendingAnnotationExportText = null }
                ) { saveTextAnnotationsLauncher.launch(prepared.fileName) }
                AnnotationExportFormat.JSON -> launchDocumentPicker(
                    onUnavailable = { pendingAnnotationExportText = null }
                ) { saveJsonAnnotationsLauncher.launch(prepared.fileName) }
                AnnotationExportFormat.CSV -> launchDocumentPicker(
                    onUnavailable = { pendingAnnotationExportText = null }
                ) { saveCsvAnnotationsLauncher.launch(prepared.fileName) }
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
    val visibleBooks = remember(uiState.rawLibraryFiles, uiState.libraryFilters, filter, debouncedQuery, uiState.sortOrder) {
        sortFiles(
            filterUnifiedLibraryBooks(
                applyLibraryFilters(uiState.rawLibraryFiles, uiState.libraryFilters),
                filter,
                debouncedQuery
            ),
            uiState.sortOrder
        )
    }
    val section = UnifiedLibrarySection.fromPersisted(uiState.unifiedLibrarySection)
    // Keyed on the list identity: recomputes exactly when the library changes
    // (including position writes, which replace the item), but skips the O(n)
    // scan on unrelated recompositions (scroll, drawer, keystrokes).
    // Uses the unfiltered collection so an active filter never hides resume.
    val continueReading = remember(uiState.rawLibraryFiles) {
        findContinueReadingBook(uiState.rawLibraryFiles)
    }
    val advancedFilterCount = uiState.libraryFilters.activeFilterCount()
    val selectedItems = uiState.contextualActionItems

    fun navigateShelfUp() {
        val parentId = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } }?.parentShelfId
        selectedShelfId = parentId
    }

    BackHandler(enabled = selectedItems.isNotEmpty() || selectedShelfId != null) {
        if (selectedItems.isNotEmpty()) {
            viewModel.clearContextualAction()
        } else {
            navigateShelfUp()
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
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.PRO)
                        },
                        onSyncUpsellClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.PRO)
                        },
                        onFontsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FONTS)
                        },
                        onAiSettingsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.AI_SETTINGS)
                        },
                        onSettingsClick = {
                            scope.launch { accountDrawerState.close() }
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.SETTINGS)
                        },
                        navController = navController,
                        onFolderSyncSettingsClick = {
                            scope.launch {
                                accountDrawerState.close()
                                navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FOLDER_SYNC_SETTINGS)
                            }
                        },
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
                    navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.SETTINGS)
                },
                onFontsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FONTS)
                },
                onAiSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.AI_SETTINGS)
                }
            )
        }
    ) {
        com.aryan.reader.shared.ui.SharedAndroidUnifiedScaffold(
            section = section,
            showingShelf = selectedShelfId != null,
            importDescription = stringResource(R.string.unified_library_import),
            addAudiobookDescription = stringResource(R.string.listen_add),
            newShelfLabel = stringResource(R.string.fab_new_shelf),
            onImport = { launchDocumentPicker { filePicker.launch(if (uiState.useStrictFileFilter) MainViewModel.SUPPORTED_MIME_TYPES else arrayOf("*/*")) } },
            onAddAudiobook = { showAudiobookAddSheet = true },
            onNewShelf = viewModel::showCreateShelfDialog,
            canPullToSync = canPullToSync,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshLibrary,
            bottomBar = {
                val activeTtsBook = ttsPlayback.bookId
                    ?.takeIf { ttsPlayback.playbackSource == "AUDIOBOOK_TTS" }
                    ?.let { id -> uiState.rawLibraryFiles.firstOrNull { it.bookId == id } }
                val activeImported = importedPlayback.bookId
                    ?.let { id -> importedAudiobooks.firstOrNull { it.bookId == id } }
                when {
                    activeTtsBook != null -> {
                        val item = activeTtsBook.toTtsAudiobookUiItem(bookTtsProgress.firstOrNull { it.bookId == activeTtsBook.bookId })
                            .copy(chapter = ttsPlayback.chapterTitle ?: "Listening with TTS")
                        AudiobookMiniPlayer(
                            item = item,
                            isPlaying = ttsPlayback.isPlaying,
                            progress = item.progress,
                            onTogglePlay = {
                                if (
                                    ttsPlayback.isPlaying ||
                                    ttsPlayback.isLoading
                                ) {
                                    viewModel.ttsController.pause()
                                } else {
                                    viewModel.ttsController.resume()
                                }
                            },
                            onExpand = {
                                audiobookPlayerItem = item
                            },
                            onStop = viewModel.ttsController::stop
                        )
                    }
                    activeImported != null -> {
                        val item = activeImported.toUiItem()
                        val liveProgress = if (importedPlayback.durationMs > 0L) {
                            importedPlayback.positionMs.toFloat() /
                                    importedPlayback.durationMs.toFloat()
                        } else {
                            item.progress
                        }
                        AudiobookMiniPlayer(
                            item = item,
                            isPlaying = importedPlayback.isPlaying,
                            progress = liveProgress,
                            onTogglePlay = {
                                globalAudiobookController.togglePlay(
                                    viewModel.ttsController::stop
                                )
                            },
                            onExpand = {
                                audiobookPlayerItem = item
                            },
                            onStop = globalAudiobookController::stop
                        )
                    }
                }
            },
            topBar = {
                // Selection actions are shelf-scoped while a shelf is open in the Shelves
                // section: delete removes from that shelf instead of deleting the book.
                val isViewingShelf = section == UnifiedLibrarySection.SHELVES && selectedShelfId != null
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
                                    launchDocumentPicker(
                                        onUnavailable = { pendingSaveOriginalItem = null }
                                    ) { saveOriginalLauncher.launch(item.suggestedOriginalFileName()) }
                                }
                            },
                        onShareClick = selectedItems.singleOrNull()
                            ?.takeIf { it.canExportOriginalFile() }
                            ?.let { item -> { shareOriginal(item) } },
                        onExportAnnotationsClick = selectedItems.singleOrNull()
                            ?.let { item -> { showAnnotationExportFormatDialogFor = item } },
                        // Inside a shelf, "delete" means remove from that shelf; on the
                        // library surface it stays a destructive permanent delete.
                        onDeleteClick = if (isViewingShelf) {
                            { showRemoveSelectedFromShelfConfirmation = true }
                        } else {
                            { showPermanentDeleteConfirmation = true }
                        },
                        compactSelectionActions = true,
                        overflowDeleteLabelRes = R.string.action_delete,
                        onClearSelectionClick = viewModel::clearContextualAction
                    )
                } else {
                    UnifiedLibraryTopBar(
                        section = section,
                        selectedShelf = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackFromShelf = ::navigateShelfUp,
                        uiState = uiState,
                        onAccountClick = { scope.launch { accountDrawerState.open() } },
                        searchQuery = if (section == UnifiedLibrarySection.HOME) query else null,
                        onSearchQueryChange = { query = it },
                    )
                }
            },
            sectionContent = { displayedSection, padding ->
            when (displayedSection) {
                UnifiedLibrarySection.HOME -> UnifiedLibraryHome(
                    modifier = Modifier.padding(padding),
                    books = visibleBooks,
                    continueReading = continueReading,
                    filter = filter,
                    query = debouncedQuery,
                    sortOrder = uiState.sortOrder,
                    advancedFilterCount = advancedFilterCount,
                    useListView = uiState.unifiedLibraryListView,
                    selectedBookIds = uiState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
                    pinnedBookIds = uiState.pinnedLibraryBookIds,
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                    onFilterChange = { filter = it },
                    onControlsClick = { showLibraryControls = true },
                    onAdvancedFiltersClick = { showAdvancedFilters = true },
                    onListViewChange = viewModel::setUnifiedLibraryListView,
                    widthSizeClass = widthSizeClass,
                    onBookClick = { item ->
                        if (item.type == FileType.AUDIOBOOK) {
                            importedAudiobooks.firstOrNull { it.bookId == item.bookId }?.let { audiobookPlayerItem = it.toUiItem() }
                        } else viewModel.onRecentFileClicked(item)
                    },
                    onBookLongClick = viewModel::onRecentItemLongPress,
                    openTabs = uiState.openTabs,
                    tabsEnabled = uiState.isTabsEnabled,
                    onCloseTab = { viewModel.closeTab(it.bookId) },
                    onCloseAllTabs = { showCloseAllTabsDialog = true },
                )
                UnifiedLibrarySection.AUDIOBOOKS -> AudiobooksLibrarySection(
                    modifier = Modifier.padding(padding),
                    audiobooks = importedAudiobooks,
                    libraryBooks = uiState.rawLibraryFiles,
                    ttsProgress = bookTtsProgress,
                    playback = importedPlayback,
                    ttsPlayback = com.aryan.reader.shared.SharedBookTtsListenState(
                        connected = ttsPlayback.playbackSource == "AUDIOBOOK_TTS",
                        bookId = ttsPlayback.bookId,
                        isPlaying = ttsPlayback.isPlaying,
                        isLoading = ttsPlayback.isLoading,
                    ),
                    activeItemId = activeListeningItemId,
                    onAudiobookClick = { item ->
                        val shouldStart = item.isTts &&
                                shouldAutoStartTtsAudiobook(item.sourceBook?.bookId, ttsPlayback)

                        if (shouldStart) {
                            viewModel.ttsController.connect()
                        }

                        audiobookPlayerItem =
                            if (item.isTts && !shouldStart) item.copy(autoStart = false)
                            else item
                    },
                    onListenWithTtsClick = { book ->
                        val shouldStart = shouldAutoStartTtsAudiobook(book.bookId, ttsPlayback)

                        if (shouldStart) {
                            viewModel.ttsController.connect()
                        }

                        audiobookPlayerItem = book
                            .toTtsAudiobookUiItem(
                                bookTtsProgress.firstOrNull { it.bookId == book.bookId }
                            )
                            .copy(autoStart = shouldStart)
                    },
                    onAddAudiobookClick = {
                        showAudiobookAddSheet = true
                    }
                )
                UnifiedLibrarySection.SHELVES -> {
                    val shelfBeingAddedTo = shelfIdAddingBooks?.let { id -> uiState.shelves.find { it.id == id } }
                    if (shelfBeingAddedTo != null) {
                        UnifiedAddBooksSection(
                            modifier = Modifier.padding(padding),
                            shelf = shelfBeingAddedTo,
                            availableBooks = remember(
                                uiState.rawLibraryFiles,
                                uiState.shelves,
                                shelfBeingAddedTo.id,
                                uiState.addBooksSource,
                            ) {
                                androidBooksAvailableForShelfAddition(
                                    allLibraryBooks = uiState.rawLibraryFiles,
                                    shelves = uiState.shelves,
                                    shelfId = shelfBeingAddedTo.id,
                                    source = uiState.addBooksSource,
                                )
                            },
                            selectedBookIds = uiState.booksSelectedForAdding,
                            addBooksSource = uiState.addBooksSource,
                            sortOrder = uiState.sortOrder,
                            downloadingBookIds = uiState.downloadingBookIds,
                            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                            onSortOrderChange = viewModel::setSortOrder,
                            onSourceChange = viewModel::setAddBooksSource,
                            onBookClick = { viewModel.toggleBookSelectionForAdding(it.bookId) },
                            onAddSelectedBooks = { viewModel.addBooksToShelf(shelfBeingAddedTo.id) },
                            onBack = { shelfIdAddingBooks = null }
                        )
                    } else {
                        UnifiedShelvesSection(
                            modifier = Modifier.padding(padding),
                            shelves = uiState.shelves,
                            selectedShelfId = selectedShelfId,
                            selectedBookIds = uiState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
                            downloadingBookIds = uiState.downloadingBookIds,
                            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                            widthSizeClass = widthSizeClass,
                            onShelfSelected = { selectedShelfId = it.id },
                            onBreadcrumbNavigate = { shelfId -> selectedShelfId = shelfId },
                            onRenameShelf = { shelfId -> shelfIdPendingRename = shelfId },
                            onDeleteShelf = { shelfId -> shelfIdPendingDelete = shelfId },
                            onAddBooks = { shelfId -> shelfIdAddingBooks = shelfId },
                            onBookClick = viewModel::onRecentFileClicked,
                            onBookLongClick = viewModel::onRecentItemLongPress,
                            )
                    }
                }
                UnifiedLibrarySection.FOLDERS -> UnifiedFoldersSection(
                    modifier = Modifier.padding(padding),
                    folders = uiState.syncedFolders,
                    allRecentFiles = uiState.rawLibraryFiles,
                    isLoading = uiState.isLoading,
                    onAddFolder = { launchDocumentPicker { folderPicker.launch(null) } },
                    onScan = viewModel::scanSyncedFolder,
                    onSyncMetadata = viewModel::syncFolderMetadata,
                    onToggleLocalSync = viewModel::setFolderLocalSyncEnabled,
                    onEditFolderFilters = viewModel::updateFolderFilters,
                    onRemove = viewModel::removeSyncedFolder,
                    cloudFolderSelection = cloudFolderSelection.takeIf { canUseCloudFolderSync },
                    cloudSyncEnabled = uiState.isSyncEnabled,
                    isProUser = canUseCloudFolderSync,
                    onCloudFolderSettings = if (canUseCloudFolderSync) {
                        {
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FOLDER_SYNC_SETTINGS)
                        }
                    } else null,
                    onIncomingCloudFolder = if (canUseCloudFolderSync) {
                        { rootId -> viewModel.showIncomingCloudFolderPrompt(rootId) }
                    } else null,
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
                            onDeleteCatalogStreams = viewModel::deleteStreamedBooksForCatalog,
                            onShowBanner = viewModel::showBanner,
                            syncedFolders = uiState.syncedFolders
                        )
                    }
                }
            }
            },
        )
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
    if (showAudiobookAddSheet) {
        AudiobookAddSheet(
            onChooseFile = {
                showAudiobookAddSheet = false
                launchDocumentPicker {
                    audiobookPicker.launch(
                        arrayOf(
                            "audio/*",
                            "application/octet-stream"
                        )
                    )
                }
            },
            onChooseMultiple = {
                showAudiobookAddSheet = false
                launchDocumentPicker {
                    audiobookMultiplePicker.launch(
                        arrayOf(
                            "audio/*",
                            "application/octet-stream"
                        )
                    )
                }
            },
            onChooseFolder = {
                showAudiobookAddSheet = false
                launchDocumentPicker { audiobookFolderPicker.launch(null) }
            },
            onChooseTtsBook = {
                showAudiobookAddSheet = false
                showTtsBookPicker = true
            },
            onDismiss = {
                showAudiobookAddSheet = false
            }
        )
    }
    if (showTtsBookPicker) {
        TtsBookPickerSheet(
            books = uiState.rawLibraryFiles,
            onBookSelected = { book ->
                showTtsBookPicker = false

                val shouldStart =
                    shouldAutoStartTtsAudiobook(
                        book.bookId,
                        ttsPlayback
                    )

                if (shouldStart) {
                    viewModel.ttsController.connect()
                }

                audiobookPlayerItem =
                    book.toTtsAudiobookUiItem(
                        bookTtsProgress.firstOrNull {
                            it.bookId == book.bookId
                        }
                    ).copy(
                        autoStart = shouldStart
                    )
            },
            onDismiss = {
                showTtsBookPicker = false
            }
        )
    }
    audiobookPlayerItem?.let { item ->
        AudiobookPlayerSheet(
            item = item,
            onBeforePlay = {
                when (
                    com.aryan.reader.shared.sharedListeningHandoff(
                        com.aryan.reader.shared.SharedListeningTarget.IMPORTED_AUDIOBOOK,
                    )
                ) {
                    com.aryan.reader.shared.SharedListeningHandoff.STOP_TTS -> viewModel.ttsController.stop()
                    com.aryan.reader.shared.SharedListeningHandoff.STOP_AUDIOBOOK -> Unit
                }
            },
            onDismiss = { audiobookPlayerItem = null },
            getAuthToken = viewModel::getAuthToken,
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
    shelfIdPendingRename?.let { shelfId ->
        val shelf = uiState.shelves.find { it.id == shelfId }
        if (shelf != null) {
            ShelfRenameDialog(
                initialName = shelf.name,
                onConfirm = { newName ->
                    viewModel.renameShelf(shelfId, newName)
                    shelfIdPendingRename = null
                },
                onDismiss = { shelfIdPendingRename = null }
            )
        }
    }
    shelfIdPendingDelete?.let { shelfId ->
        ShelfDeleteDialog(
            shelfName = uiState.shelves.find { it.id == shelfId }?.name ?: "",
            onConfirm = {
                viewModel.deleteShelf(shelfId)
                shelfIdPendingDelete = null
                if (selectedShelfId == shelfId) selectedShelfId = null
            },
            onDismiss = { shelfIdPendingDelete = null }
        )
    }
    if (showCloseAllTabsDialog) {
        LibraryCloseAllTabsDialog(
            onConfirm = {
                viewModel.closeAllTabs()
                showCloseAllTabsDialog = false
            },
            onDismiss = { showCloseAllTabsDialog = false }
        )
    }
    if (showRemoveSelectedFromShelfConfirmation) {
        val shelf = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } }
        ShelfRemoveSelectedDialog(
            count = selectedItems.size,
            shelfName = shelf?.name ?: "",
            onConfirm = {
                viewModel.removeContextualItemsFromShelf()
                showRemoveSelectedFromShelfConfirmation = false
            },
            onDismiss = { showRemoveSelectedFromShelfConfirmation = false }
        )
    }
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
                exportAnnotations(item, format)
            }
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

@Composable
private fun UnifiedLibraryDrawer(
    currentSection: UnifiedLibrarySection,
    onSectionSelected: (UnifiedLibrarySection) -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
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
                    AppIcon(
                        contentDescription = stringResource(R.string.content_desc_app_icon),
                        size = 44.dp,
                        shape = RoundedCornerShape(16.dp),
                        plateColor = MaterialTheme.colorScheme.surface,
                    )
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
            val drawerModel = mobileUnifiedLibraryDrawerModel(
                MobileUnifiedLibraryDrawerCapabilities(
                    catalogsAvailable = !BuildConfig.IS_OFFLINE,
                    aiSettingsAvailable = !BuildConfig.IS_OFFLINE,
                ),
            )
            drawerModel.destinations.forEach { destination ->
                when (destination) {
                    MobileUnifiedLibraryDrawerDestination.HOME -> UnifiedLibraryDestination(
                        stringResource(R.string.unified_library_home),
                        currentSection == destination.section,
                        { Icon(Icons.Default.Home, null) },
                    ) { onSectionSelected(destination.section) }
                    MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS -> UnifiedLibraryDestination(
                        stringResource(R.string.listen_title),
                        currentSection == destination.section,
                        { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                    ) { onSectionSelected(destination.section) }
                    MobileUnifiedLibraryDrawerDestination.SHELVES -> UnifiedLibraryDestination(
                        stringResource(R.string.tab_shelves),
                        currentSection == destination.section,
                        { Icon(Icons.AutoMirrored.Filled.LibraryBooks, null) },
                    ) { onSectionSelected(destination.section) }
                    MobileUnifiedLibraryDrawerDestination.FOLDERS -> UnifiedLibraryDestination(
                        stringResource(R.string.tab_folders),
                        currentSection == destination.section,
                        { Icon(Icons.Default.Folder, null) },
                    ) { onSectionSelected(destination.section) }
                    MobileUnifiedLibraryDrawerDestination.CATALOGS -> UnifiedLibraryDestination(
                        stringResource(R.string.tab_catalogs),
                        currentSection == destination.section,
                        { Icon(painterResource(R.drawable.cloud), null) },
                    ) { onSectionSelected(destination.section) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            Text(stringResource(R.string.unified_library_appearance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp))
            drawerModel.appearance.forEach { appearance ->
                when (appearance) {
                    MobileUnifiedLibraryDrawerAppearance.THEME -> NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Palette, null) },
                        label = { Text(stringResource(R.string.app_theme_title)) },
                        selected = false,
                        onClick = onThemeClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    MobileUnifiedLibraryDrawerAppearance.SETTINGS -> NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(stringResource(R.string.settings)) },
                        selected = false,
                        onClick = onSettingsClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    MobileUnifiedLibraryDrawerAppearance.FONTS -> NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.fonts), null) },
                        label = { Text(stringResource(R.string.drawer_custom_fonts)) },
                        selected = false,
                        onClick = onFontsClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    MobileUnifiedLibraryDrawerAppearance.AI -> NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ai), null) },
                        label = { Text(stringResource(R.string.ai_settings_title)) },
                        selected = false,
                        onClick = onAiSettingsClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
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



internal val UnifiedLibraryFilter.labelRes: Int
    get() = when (this) {
        UnifiedLibraryFilter.ALL -> R.string.unified_library_all
        UnifiedLibraryFilter.READING -> R.string.unified_library_reading
        UnifiedLibraryFilter.FINISHED -> R.string.unified_library_finished
        UnifiedLibraryFilter.UNREAD -> R.string.unified_library_unread
    }

internal fun ReadStatusFilter.toUnifiedLibraryFilter(): UnifiedLibraryFilter = when (this) {
    ReadStatusFilter.ALL -> UnifiedLibraryFilter.ALL
    ReadStatusFilter.UNREAD -> UnifiedLibraryFilter.UNREAD
    ReadStatusFilter.IN_PROGRESS -> UnifiedLibraryFilter.READING
    ReadStatusFilter.COMPLETED -> UnifiedLibraryFilter.FINISHED
}

internal fun findContinueReadingBook(books: List<RecentFileItem>): RecentFileItem? {
    if (books.isEmpty()) return null
    val mapper = SharedProjectionBookItemCache(maxEntries = books.size)
    val picked = mobileUnifiedContinueReadingBook(mapper.map(books)) ?: return null
    return books.firstOrNull { it.bookId == picked.id }
}

internal fun filterUnifiedLibraryBooks(books: List<RecentFileItem>, filter: UnifiedLibraryFilter, query: String): List<RecentFileItem> {
    if (books.isEmpty()) return emptyList()
    val normalizedQuery = query.trim()
    val mapper = SharedProjectionBookItemCache(maxEntries = books.size)
    val byId = books.associateBy { it.bookId }
    return mapper.map(books)
        .filter { it.matchesMobileUnifiedLibraryFilter(filter, normalizedQuery) }
        .mapNotNull { byId[it.id] }
}
