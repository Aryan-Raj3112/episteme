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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.aryan.reader.audiobook.ListenOverlayHost
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.ui.AppIcon
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance
import com.aryan.reader.shared.ui.rememberReaderMotionPolicy
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerDestination
import com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel
import com.aryan.reader.shared.ui.SharedAnnotationExportFormatDialog
import com.aryan.reader.shared.ui.sharedAnnotationExportFormatOptions
import com.aryan.reader.shared.ui.activeFilterCount
import com.aryan.reader.shared.ui.matchesMobileUnifiedLibraryFilter
import com.aryan.reader.shared.ui.mobileUnifiedContinueReadingBook
import com.aryan.reader.shared.withPinnedFirst
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
    // Publisher narration outlives the player sheet by design (ListenOverlayHost
    // is process-wide), so the mini player observes it directly: collapsing the
    // sheet must leave a way back, exactly like the TTS service state above.
    val listenOverlayHost = remember(context) { ListenOverlayHost.get(context.applicationContext) }
    val overlayPlayback by listenOverlayHost.playback.collectAsStateWithLifecycle()
    val overlayBook by listenOverlayHost.book.collectAsStateWithLifecycle()
    val overlayChapter by listenOverlayHost.chapterIndex.collectAsStateWithLifecycle()
    val overlayProgress by listenOverlayHost.progress.collectAsStateWithLifecycle()
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
    var selectedTagId by rememberSaveable { mutableStateOf<String?>(null) }
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
    // Content-index search follows the same debounce; the engine publishes
    // matching book ids into uiState for the union in visibleBooks.
    LaunchedEffect(debouncedQuery, uiState.libraryState.searchInsideFiles) {
        viewModel.searchLibraryContent(debouncedQuery)
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
    // The top-bar tab switcher replaces the Home tabs row and covers both readers, which that
    // row never did. Keyed on the two projected lists so closing or switching a tab re-reads them.
    val openTabs = remember(
        uiState.textOpenTabs,
        uiState.openTabs,
        uiState.tabState,
        uiState.usePdfFileNameAsDisplayName,
    ) {
        // Disabling multi-tab reading parks each reader on one tab; the button would then
        // advertise a single "tab" and open an empty panel, so it follows the toggle.
        if (uiState.isTabsEnabled) uiState.homeOpenTabs() else emptyList()
    }
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
    val visibleBooks = remember(
        uiState.rawLibraryFiles,
        uiState.libraryFilters,
        uiState.sortOrder,
        uiState.pinnedLibraryBookIds,
        filter,
        debouncedQuery,
        uiState.libraryState.searchInsideFiles,
        uiState.libraryState.contentSearchResults,
    ) {
        // One projection cache for the whole pass: filtering, the section filter and the sort all
        // map the same books, so the cache builds each SharedBookItem once instead of 3-4 times.
        val projectionCache = projectionCacheFor(uiState.rawLibraryFiles)
        filterUnifiedLibraryBooks(
            applyLibraryFilters(uiState.rawLibraryFiles, uiState.libraryFilters, projectionCache),
            filter,
            debouncedQuery,
            uiState.libraryState.searchInsideFiles,
            uiState.libraryState.contentSearchResults,
            uiState.sortOrder,
            uiState.pinnedLibraryBookIds,
            projectionCache,
        )
    }
    val section = UnifiedLibrarySection.fromPersisted(uiState.unifiedLibrarySection)
    // Leaving Tags keeps the section list, not a stale tag detail, as the landing view.
    LaunchedEffect(section) {
        if (section != UnifiedLibrarySection.TAGS) selectedTagId = null
    }
    // Keyed on the list identity: recomputes exactly when the library changes
    // (including position writes, which replace the item), but skips the O(n)
    // scan on unrelated recompositions (scroll, drawer, keystrokes).
    // Uses the unfiltered collection so an active filter never hides resume.
    val continueReading = remember(uiState.rawLibraryFiles) {
        findContinueReadingBook(uiState.rawLibraryFiles)
    }
    val advancedFilterCount = uiState.libraryFilters.activeFilterCount()
    val selectedItems = uiState.contextualActionItems
    // Selection actions are shelf-scoped while a shelf is open in the Shelves section: delete
    // removes from that shelf instead of deleting the book.
    val isViewingShelf = section == UnifiedLibrarySection.SHELVES && selectedShelfId != null

    fun navigateShelfUp() {
        val parentId = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } }?.parentShelfId
        selectedShelfId = parentId
    }

    val inTagDetail = section == UnifiedLibrarySection.TAGS && selectedTagId != null
    val navigateBack = {
        if (inTagDetail) selectedTagId = null else navigateShelfUp()
    }

    val focusManager = LocalFocusManager.current
    // Keyboard visibility as state: back dismisses the IME first (keeping
    // the query + results) so there is a way to hide the keyboard without
    // leaving search. Reads the IME inset directly so no shared-API change
    // is needed; dialogs still win because they register back later (LIFO).
    val isKeyboardOpen = WindowInsets.isImeVisible
    // System back unwinds library layers innermost-first: selection, then an
    // open keyboard, then an active HOME search (query + results), then shelf
    // navigation. Search must clear before shelf-up so a filtered shelf view
    // doesn't pop away with the query still applied underneath.
    val backAction = nextLibraryBackAction(
        hasSelection = selectedItems.isNotEmpty(),
        isKeyboardOpen = isKeyboardOpen,
        isSearchActive = section == UnifiedLibrarySection.HOME && query.isNotBlank(),
        inShelf = selectedShelfId != null || inTagDetail,
    )
    BackHandler(enabled = backAction != LibraryBackAction.NONE) {
        when (nextLibraryBackAction(
            hasSelection = selectedItems.isNotEmpty(),
            isKeyboardOpen = isKeyboardOpen,
            isSearchActive = section == UnifiedLibrarySection.HOME && query.isNotBlank(),
            inShelf = selectedShelfId != null || inTagDetail,
        )) {
            LibraryBackAction.CLEAR_SELECTION -> viewModel.clearContextualAction()
            LibraryBackAction.HIDE_KEYBOARD -> focusManager.clearFocus()
            LibraryBackAction.CLEAR_SEARCH -> {
                query = ""
                focusManager.clearFocus()
            }
            LibraryBackAction.SHELF_UP -> navigateBack()
            LibraryBackAction.NONE -> Unit
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
    // Wide windows get a permanent rail; phones keep the modal drawer. Both render the same shell
    // below, so navigation parity is structural rather than duplicated.
    val showNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact
    val railModel = remember {
        com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel(
            com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities(
                catalogsAvailable = !BuildConfig.IS_OFFLINE,
                aiSettingsAvailable = !BuildConfig.IS_OFFLINE,
            ),
        )
    }
    com.aryan.reader.shared.ui.SharedAdaptiveLibraryNavigation(
        showRail = showNavigationRail,
        drawerState = drawerState,
        drawerContent = {
            UnifiedLibraryDrawer(
                currentSection = section,
                onSectionSelected = { destination ->
                    selectedShelfId = null
                    selectedTagId = null
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
                },
                currentUser = uiState.currentUser,
                onAccountClick = {
                    scope.launch {
                        drawerState.close()
                        accountDrawerState.open()
                    }
                },
            )
        },
        rail = {
            com.aryan.reader.shared.ui.SharedLibraryNavigationRail(
                destinations = railModel.destinations,
                selectedSection = section,
                appearance = railModel.appearance,
                onDestinationSelected = { destination ->
                    selectedShelfId = null
                    selectedTagId = null
                    viewModel.setUnifiedLibrarySection(destination.section.persistedValue)
                },
                onAppearanceSelected = { appearance ->
                    // The rail is already on screen, so there is no drawer to close first.
                    when (appearance) {
                        com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance.THEME ->
                            showThemeSheet = true
                        com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance.SETTINGS ->
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.SETTINGS)
                        com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance.FONTS ->
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.FONTS)
                        com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance.AI ->
                            navController.navigateIfReady(com.aryan.reader.shared.ui.SharedMobileAppDestination.AI_SETTINGS)
                    }
                },
            )
        },
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
            reduceMotion = rememberReaderMotionPolicy().reduceMotion,
            bottomBar = {
                val activeTtsBook = ttsPlayback.bookId
                    ?.takeIf { ttsPlayback.playbackSource == "AUDIOBOOK_TTS" }
                    ?.let { id -> uiState.rawLibraryFiles.firstOrNull { it.bookId == id } }
                val activeImported = importedPlayback.bookId
                    ?.let { id -> importedAudiobooks.firstOrNull { it.bookId == id } }
                // Narration continues with no TTS service session, so it gets
                // the first slot: the other branches are necessarily idle then.
                val activeOverlayBook = overlayBook
                    ?.takeIf { overlayPlayback.hasBook }
                    ?.let { listenBook ->
                        uiState.rawLibraryFiles.firstOrNull { it.bookId == listenBook.bookId }
                            ?.toTtsAudiobookUiItem(
                                overlayProgress?.takeIf { it.bookId == listenBook.bookId }
                                    ?: bookTtsProgress.firstOrNull { it.bookId == listenBook.bookId }
                            )
                            ?.copy(
                                chapter = "Narration · Chapter ${(overlayChapter.takeIf { it >= 0 } ?: 0) + 1}",
                                narrator = listenBook.mediaOverlay.narrator ?: "Publisher narration",
                            )
                    }
                when {
                    activeOverlayBook != null -> {
                        AudiobookMiniPlayer(
                            item = activeOverlayBook,
                            isPlaying = overlayPlayback.isPlaying,
                            progress = activeOverlayBook.progress,
                            onTogglePlay = { listenOverlayHost.toggle() },
                            onExpand = {
                                audiobookPlayerItem = activeOverlayBook
                            },
                            onStop = { listenOverlayHost.stop() }
                        )
                    }
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
                                    onBeforePlay = {
                                        viewModel.ttsController.stop()
                                        listenOverlayHost.stop()
                                    }
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
            selectionToolbar = if (selectedItems.isEmpty()) {
                null
            } else {
                {
                    com.aryan.reader.shared.ui.SharedSelectionToolbar(
                        selectedCount = selectedItems.size,
                        onSelectAll = viewModel::selectAllLibraryFiles,
                        onPin = { viewModel.togglePinForContextualItems(isHome = false) },
                        onAddToShelf = { viewModel.openAddSelectedToShelf(selectedItems.map { it.bookId }.toSet()) },
                        onTag = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
                        onInfo = selectedItems.singleOrNull()?.let { item ->
                            {
                                selectedBookForInfo = item
                                showSelectedBookInfo = true
                            }
                        },
                        onSave = selectedItems.singleOrNull()
                            ?.takeIf { it.canExportOriginalFile() }
                            ?.let { item ->
                                {
                                    pendingSaveOriginalItem = item
                                    launchDocumentPicker(
                                        onUnavailable = { pendingSaveOriginalItem = null }
                                    ) { saveOriginalLauncher.launch(item.suggestedOriginalFileName()) }
                                }
                            },
                        onShare = selectedItems.singleOrNull()
                            ?.takeIf { it.canExportOriginalFile() }
                            ?.let { item -> { shareOriginal(item) } },
                        onExportAnnotations = selectedItems.singleOrNull()
                            ?.let { item -> { showAnnotationExportFormatDialogFor = item } },
                        // Inside a shelf, "delete" means remove from that shelf; on the
                        // library surface it stays a destructive permanent delete.
                        onDelete = if (isViewingShelf) {
                            { showRemoveSelectedFromShelfConfirmation = true }
                        } else {
                            { showPermanentDeleteConfirmation = true }
                        },
                        onClear = viewModel::clearContextualAction,
                    )
                }
            },
            topBar = {
                if (selectedItems.isNotEmpty()) {
                    // The count and the way out stay up here; everything that acts on the
                    // selection is within thumb reach at the bottom of the screen.
                    com.aryan.reader.shared.ui.SharedSelectionTopBar(
                        selectedCount = selectedItems.size,
                        onClose = viewModel::clearContextualAction,
                    )
                } else {
                    UnifiedLibraryTopBar(
                        section = section,
                        selectedShelf = selectedShelfId?.let { id -> uiState.shelves.find { it.id == id } },
                        onMenuClick = if (showNavigationRail) {
                            null
                        } else {
                            { scope.launch { drawerState.open() } }
                        },
                        onBackFromShelf = navigateBack,
                        titleOverride = if (section == UnifiedLibrarySection.TAGS) {
                            selectedTagId?.let { id -> uiState.allTags.find { it.id == id }?.name }
                        } else null,
                        uiState = uiState,
                        onAccountClick = { scope.launch { accountDrawerState.open() } },
                        searchQuery = if (section == UnifiedLibrarySection.HOME) query else null,
                        onSearchQueryChange = { query = it },
                        searchInsideFiles = uiState.libraryState.searchInsideFiles,
                        onSearchInsideFilesChange = viewModel::setSearchInsideFiles,
                        openTabs = openTabs,
                        onOpenTabSelected = viewModel::switchTab,
                        onOpenTabClosed = viewModel::closeTabFromLibrary,
                        onTabOrderCommitted = viewModel::reorderTabs,
                        onCloseAllTabs = if (openTabs.size > 1) {
                            { showCloseAllTabsDialog = true }
                        } else {
                            null
                        },
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
                    onContentHitClick = { item, hit, hitQuery ->
                        viewModel.openLibrarySearchHit(item, hit, hitQuery)
                    },
                    searchInsideFiles = uiState.libraryState.searchInsideFiles,
                    contentSearchResults = uiState.libraryState.contentSearchResults,
                    isLoading = uiState.isLoading,
                    clearFiltersLabel = stringResource(R.string.action_clear_filters),
                    onClearFilters = {
                        filter = UnifiedLibraryFilter.ALL
                        query = ""
                    },
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
                        // Card tap only opens the player. Playback starts from
                        // a play button (row or sheet), never from opening.
                        audiobookPlayerItem = book
                            .toTtsAudiobookUiItem(
                                bookTtsProgress.firstOrNull { it.bookId == book.bookId }
                            )
                            .copy(autoStart = false)
                    },
                    onPlayTtsClick = { book ->
                        viewModel.ttsController.connect()
                        audiobookPlayerItem = book
                            .toTtsAudiobookUiItem(
                                bookTtsProgress.firstOrNull { it.bookId == book.bookId }
                            )
                            .copy(autoStart = true)
                    },
                    onPlayAudiobookClick = { item ->
                        audiobookPlayerItem = item.copy(autoStart = true)
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
                            sortOrder = uiState.sortOrder,
                            useListView = uiState.unifiedShelfListView,
                            onListViewChange = viewModel::setUnifiedShelfListView,
                            onShelfSelected = { selectedShelfId = it.id },
                            onBreadcrumbNavigate = { shelfId -> selectedShelfId = shelfId },
                            onSortOrderChange = viewModel::setSortOrder,
                            onRenameShelf = { shelfId -> shelfIdPendingRename = shelfId },
                            onDeleteShelf = { shelfId -> shelfIdPendingDelete = shelfId },
                            onAddBooks = { shelfId -> shelfIdAddingBooks = shelfId },
                            onBookClick = viewModel::onRecentFileClicked,
                            onBookLongClick = viewModel::onRecentItemLongPress,
                            )
                    }
                }
                UnifiedLibrarySection.TAGS -> UnifiedTagsSection(
                    modifier = Modifier.padding(padding),
                    tags = uiState.allTags,
                    books = uiState.rawLibraryFiles,
                    selectedTagId = selectedTagId,
                    selectedBookIds = uiState.contextualActionItems.mapTo(mutableSetOf()) { it.bookId },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                    useListView = uiState.unifiedShelfListView,
                    widthSizeClass = widthSizeClass,
                    sortOrder = uiState.sortOrder,
                    onTagSelected = { selectedTagId = it },
                    onListViewChange = viewModel::setUnifiedShelfListView,
                    onSortOrderChange = viewModel::setSortOrder,
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress,
                )
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
                    // An imported audiobook wins over generated listening of
                    // either kind: TTS synthesis and publisher narration both
                    // stop, so two real-audio sources never play at once.
                    com.aryan.reader.shared.SharedListeningHandoff.STOP_TTS -> {
                        viewModel.ttsController.stop()
                        listenOverlayHost.stop()
                    }
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
                viewModel.closeAllTabs(kind = null)
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
    currentUser: com.aryan.reader.shared.UserData?,
    onAccountClick: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        val appName = stringResource(R.string.app_name)
        val destinationLabel = currentSection.drawerLabel()
        // No account system in the open-source edition, so there is no one to be signed in as.
        val isSignedIn = BuildConfig.FLAVOR != "oss" && currentUser != null
        com.aryan.reader.shared.ui.SharedDrawerLayout(
            header = {
                com.aryan.reader.shared.ui.SharedDrawerHeader(
                    title = appName,
                    // The header reports the destination rather than repeating a tagline, so a
                    // glance at the drawer tells the reader where they already are.
                    subtitle = destinationLabel,
                    leading = {
                        AppIcon(
                            contentDescription = stringResource(R.string.content_desc_app_icon),
                            size = 44.dp,
                            shape = RoundedCornerShape(14.dp),
                            plateColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    },
                )
            },
            // The drawer is navigation, not a storefront: it shows who you are once you are
            // signed in, and nothing at all when you are not. The edition without accounts never
            // renders the row.
            showFooterDivider = isSignedIn,
            footer = {
                if (isSignedIn) {
                    com.aryan.reader.shared.ui.SharedDrawerAccountFooter(
                        title = currentUser?.displayName
                            ?: currentUser?.email
                            ?: readerString("desktop_signed_in", "Signed in"),
                        // Only repeat the address when it is not already the title.
                        supporting = if (currentUser?.displayName != null) {
                            currentUser?.email
                        } else {
                            readerString("account_and_sync", "Account & sync")
                        },
                        onClick = onAccountClick,
                        leading = {
                            AndroidAccountAvatar(
                                user = currentUser!!,
                                modifier = Modifier.size(36.dp),
                                contentDescription = stringResource(R.string.content_desc_profile_picture),
                            )
                        },
                    )
                }
                com.aryan.reader.shared.ui.SharedDrawerVersionCaption(
                    text = stringResource(R.string.about_version_name, BuildConfig.VERSION_NAME),
                )
            },
        ) {
            item { com.aryan.reader.shared.ui.SharedDrawerSectionLabel(stringResource(R.string.library_title)) }
            val drawerModel = mobileUnifiedLibraryDrawerModel(
                MobileUnifiedLibraryDrawerCapabilities(
                    catalogsAvailable = !BuildConfig.IS_OFFLINE,
                    aiSettingsAvailable = !BuildConfig.IS_OFFLINE,
                ),
            )
            items(drawerModel.destinations, key = { it.name }) { destination ->
                com.aryan.reader.shared.ui.SharedDrawerItem(
                    icon = { Icon(destination.drawerIcon(), contentDescription = null) },
                    label = stringResource(destination.drawerLabelRes()),
                    selected = currentSection == destination.section,
                    // The pill is the selected signal; a heavier label on top of it was a second
                    // emphasis for the same thing.
                    onClick = { onSectionSelected(destination.section) },
                )
            }

            item { com.aryan.reader.shared.ui.SharedDrawerGroupDivider() }
            item {
                com.aryan.reader.shared.ui.SharedDrawerSectionLabel(
                    stringResource(R.string.unified_library_appearance),
                )
            }
            items(drawerModel.appearance, key = { it.name }) { appearance ->
                com.aryan.reader.shared.ui.SharedDrawerItem(
                    icon = {
                        val res = appearance.drawerIconRes()
                        if (res != null) {
                            Icon(painterResource(res), contentDescription = null)
                        } else {
                            Icon(appearance.drawerVector(), contentDescription = null)
                        }
                    },
                    label = stringResource(appearance.drawerLabelRes()),
                    onClick = when (appearance) {
                        MobileUnifiedLibraryDrawerAppearance.THEME -> onThemeClick
                        MobileUnifiedLibraryDrawerAppearance.SETTINGS -> onSettingsClick
                        MobileUnifiedLibraryDrawerAppearance.FONTS -> onFontsClick
                        MobileUnifiedLibraryDrawerAppearance.AI -> onAiSettingsClick
                    },
                )
            }
        }
    }
}

private fun MobileUnifiedLibraryDrawerDestination.drawerLabelRes(): Int = when (this) {
    MobileUnifiedLibraryDrawerDestination.HOME -> R.string.unified_library_home
    MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS -> R.string.listen_title
    MobileUnifiedLibraryDrawerDestination.SHELVES -> R.string.tab_shelves
    MobileUnifiedLibraryDrawerDestination.TAGS -> R.string.section_tags
    MobileUnifiedLibraryDrawerDestination.FOLDERS -> R.string.tab_folders
    MobileUnifiedLibraryDrawerDestination.CATALOGS -> R.string.tab_catalogs
}

private fun MobileUnifiedLibraryDrawerDestination.drawerIcon(): ImageVector = when (this) {
    MobileUnifiedLibraryDrawerDestination.HOME -> Icons.Default.Home
    MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS -> Icons.AutoMirrored.Filled.VolumeUp
    MobileUnifiedLibraryDrawerDestination.SHELVES -> Icons.AutoMirrored.Filled.LibraryBooks
    MobileUnifiedLibraryDrawerDestination.TAGS -> Icons.Default.Tag
    MobileUnifiedLibraryDrawerDestination.FOLDERS -> Icons.Default.Folder
    MobileUnifiedLibraryDrawerDestination.CATALOGS -> Icons.Default.Cloud
}

private fun MobileUnifiedLibraryDrawerAppearance.drawerLabelRes(): Int = when (this) {
    MobileUnifiedLibraryDrawerAppearance.THEME -> R.string.app_theme_title
    MobileUnifiedLibraryDrawerAppearance.SETTINGS -> R.string.settings
    MobileUnifiedLibraryDrawerAppearance.FONTS -> R.string.drawer_custom_fonts
    MobileUnifiedLibraryDrawerAppearance.AI -> R.string.ai_settings_title
}

/** Drawable-backed icons keep the brand marks the app already ships; the rest use Material glyphs. */
private fun MobileUnifiedLibraryDrawerAppearance.drawerIconRes(): Int? = when (this) {
    MobileUnifiedLibraryDrawerAppearance.FONTS -> R.drawable.fonts
    MobileUnifiedLibraryDrawerAppearance.AI -> R.drawable.ai
    else -> null
}

private fun MobileUnifiedLibraryDrawerAppearance.drawerVector(): ImageVector = when (this) {
    MobileUnifiedLibraryDrawerAppearance.THEME -> Icons.Default.Palette
    else -> Icons.Default.Settings
}

/** The drawer's own name for a section, matching the destinations it lists. */
private fun UnifiedLibrarySection.drawerLabel(): String? = when (this) {
    UnifiedLibrarySection.HOME -> null
    UnifiedLibrarySection.AUDIOBOOKS -> "Listen"
    UnifiedLibrarySection.SHELVES -> "Shelves"
    UnifiedLibrarySection.TAGS -> "Tags"
    UnifiedLibrarySection.FOLDERS -> "Folders"
    UnifiedLibrarySection.CATALOGS -> "Catalogs"
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

/** Innermost-first system-back priority for the unified library. */
internal enum class LibraryBackAction {
    CLEAR_SELECTION,
    HIDE_KEYBOARD,
    CLEAR_SEARCH,
    SHELF_UP,
    NONE,
}

internal fun nextLibraryBackAction(
    hasSelection: Boolean,
    isSearchActive: Boolean,
    inShelf: Boolean,
    isKeyboardOpen: Boolean = false,
): LibraryBackAction = when {
    hasSelection -> LibraryBackAction.CLEAR_SELECTION
    isKeyboardOpen -> LibraryBackAction.HIDE_KEYBOARD
    isSearchActive -> LibraryBackAction.CLEAR_SEARCH
    inShelf -> LibraryBackAction.SHELF_UP
    else -> LibraryBackAction.NONE
}

internal fun filterUnifiedLibraryBooks(
    books: List<RecentFileItem>,
    filter: UnifiedLibraryFilter,
    query: String,
    searchInsideFiles: Boolean = false,
    contentSearchResults: List<SharedGlobalSearchBookResult> = emptyList(),
    sortOrder: SortOrder = SortOrder.RECENT,
    pinnedBookIds: Set<String> = emptySet(),
    mapper: SharedProjectionBookItemCache = projectionCacheFor(books),
): List<RecentFileItem> {
    if (books.isEmpty()) return emptyList()
    val normalizedQuery = query.trim()
    val byId = books.associateBy { it.bookId }
    val sharedBooks = mapper.map(books)
    val metadataBooks = sharedBooks
        .filter { it.matchesMobileUnifiedLibraryFilter(filter, normalizedQuery) }
        .mapNotNull { byId[it.id] }
    val ordered = if (!searchInsideFiles || normalizedQuery.isBlank() || contentSearchResults.isEmpty()) {
        sortFiles(metadataBooks, sortOrder, mapper, byId)
    } else {
        // Content-only books follow metadata hits but still respect the active
        // status filter (blank query = filter check without text matching).
        // Relevance wins inside this group: best content match first, otherwise a
        // strong hit sits below a book the user merely opened last.
        val metadataIds = metadataBooks.mapTo(HashSet()) { it.bookId }
        val contentScores = contentSearchResults.associate { it.bookId to it.score }
        val contentOnly = sharedBooks
            .filter { it.id !in metadataIds && it.matchesMobileUnifiedLibraryFilter(filter, "") }
            .sortedByDescending { contentScores[it.id] ?: Double.NEGATIVE_INFINITY }
            .mapNotNull { byId[it.id] }
        sortFiles(metadataBooks, sortOrder, mapper, byId) + contentOnly
    }
    // Android benchmark: pinned books lead the home/library list. Applied here
    // because Library Beta re-derives the list from raw books instead of using
    // the projector's already ordered `libraryBooks`.
    return ordered.withPinnedFirst(pinnedBookIds) { it.bookId }
}
