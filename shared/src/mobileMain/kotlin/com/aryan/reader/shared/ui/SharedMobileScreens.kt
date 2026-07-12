package com.aryan.reader.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.applyLibraryFilters
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.sortBooks
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.imageResource

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
    isStandardEdition: Boolean = false,
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
                            Text(
                                if (isStandardEdition) "Standard version" else "$credits credits",
                                style = MaterialTheme.typography.labelMedium
                            )
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
                    text = if (isStandardEdition) "Sync account and app settings." else "Sync account, Pro features, and credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                label = {
                    Text(
                        when {
                            isStandardEdition -> "Standard version"
                            isProUser -> "Pro unlocked"
                            else -> "Upgrade to Pro"
                        }
                    )
                },
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
fun SharedMobilePdfReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onNativePdfBridgeNeeded: (BookItem) -> Unit,
    initialReaderState: SharedPdfReaderState? = null,
    onReaderStateChange: (SharedPdfReaderState) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val initialPage = book.lastPageIndex?.coerceAtLeast(0) ?: 0
    var readerState by remember(book.id, initialReaderState) {
        mutableStateOf(
            initialReaderState?.coerced()
                ?: SharedPdfReaderState.initial(pageCount = 1, initialPageIndex = initialPage)
                    .copy(displayMode = PdfDisplayMode.VERTICAL_SCROLL)
        )
    }
    var showChrome by remember(book.id) { mutableStateOf(true) }
    var showReaderOptions by remember(book.id) { mutableStateOf(false) }
    var showThemePanel by remember(book.id) { mutableStateOf(false) }
    var showPageSlider by remember(book.id) { mutableStateOf(false) }
    var showVerticalPageGap by remember(book.id) { mutableStateOf(true) }
    var showPageNumberOverlay by remember(book.id) { mutableStateOf(true) }
    var systemUiMode by remember(book.id) { mutableStateOf(SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS) }
    var useTwoPageSpread by remember(book.id) { mutableStateOf(false) }
    var firstPageStandaloneInSpread by remember(book.id) { mutableStateOf(false) }
    var globalTextureTransparency by remember { mutableStateOf(0f) }
    var keepScreenOn by remember(book.id) { mutableStateOf(false) }
    var autoScrollEnabled by remember(book.id) { mutableStateOf(false) }
    var tapToTurnPages by remember(book.id) { mutableStateOf(true) }
    var isScrollLocked by remember(book.id) { mutableStateOf(false) }
    var navigationRequestPage by remember(book.id) { mutableStateOf(readerState.pageIndex) }
    var navigationRequestToken by remember(book.id) { mutableStateOf(0) }
    var searchResults by remember(book.id) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var isSearchInProgress by remember(book.id) { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Document metadata must not follow the visible page. A newly requested page starts with
    // SharedMobilePdfPageRender's loading value (pageCount = 1); using that transient value here
    // used to collapse the list/pager to page zero every time the user changed pages.
    val documentRender = rememberSharedMobilePdfPageRender(book, 0)
    val pageCount = if (documentRender.bitmap != null || documentRender.errorMessage != null) {
        documentRender.pageCount.coerceAtLeast(1)
    } else {
        readerState.pageCount.coerceAtLeast(1)
    }
    val activeTheme = remember(readerState.themeId) {
        BuiltInPdfReaderThemes.firstOrNull { it.id == readerState.themeId }
            ?: BuiltInPdfReaderThemes.first()
    }
    var canvasSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    val activeStroke = remember(book.id, readerState.pageIndex) { mutableStateListOf<PdfPagePoint>() }

    fun dispatch(action: SharedPdfReaderAction) {
        readerState = readerState.reduce(action)
    }

    fun navigateToPage(pageIndex: Int) {
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        dispatch(SharedPdfReaderAction.GoToPage(target))
        navigationRequestPage = target
        navigationRequestToken++
    }

    fun toggleDisplayMode() {
        navigationRequestPage = readerState.pageIndex
        navigationRequestToken++
        dispatch(SharedPdfReaderAction.DisplayModeToggled)
    }

    fun navigateToSearchResult(resultIndex: Int) {
        if (searchResults.isEmpty()) return
        readerState = readerState.reduce(SharedPdfReaderAction.GoToSearchResult(resultIndex, searchResults))
        val resolvedIndex = readerState.activeSearchResultIndex
        searchResults.getOrNull(resolvedIndex)?.let { result ->
            navigationRequestPage = result.pageIndex
            navigationRequestToken++
        }
    }

    fun activeToolConfig(tool: PdfInkTool) = SharedPdfAnnotationDefaults.configFor(tool)

    fun setTool(tool: PdfInkTool) {
        dispatch(SharedPdfReaderAction.ToolSelected(tool))
        if (tool != PdfInkTool.NONE) {
            activeToolConfig(tool).let { config ->
                dispatch(SharedPdfReaderAction.ColorSelected(config.colorArgb.takeIf { it != 0 } ?: readerState.selectedColorArgb))
                dispatch(SharedPdfReaderAction.StrokeWidthChanged(config.strokeWidth))
            }
        }
    }

    fun finishInkStroke(pageIndex: Int) {
        if (activeStroke.size < 2 || readerState.selectedTool == PdfInkTool.NONE || readerState.selectedTool == PdfInkTool.TEXT) {
            activeStroke.clear()
            return
        }
        val kind = if (readerState.selectedTool == PdfInkTool.HIGHLIGHTER || readerState.selectedTool == PdfInkTool.HIGHLIGHTER_ROUND) {
            PdfAnnotationKind.HIGHLIGHT
        } else {
            PdfAnnotationKind.INK
        }
        val annotation = if (kind == PdfAnnotationKind.HIGHLIGHT) {
            val xs = activeStroke.map { it.x }
            val ys = activeStroke.map { it.y }
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = readerState.selectedTool,
                boundsList = listOf(
                    PdfPageBounds(
                        left = xs.minOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        top = (ys.minOrNull()?.minus(0.015f))?.coerceIn(0f, 1f) ?: 0f,
                        right = xs.maxOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        bottom = (ys.maxOrNull()?.plus(0.015f))?.coerceIn(0f, 1f) ?: 0f
                    )
                ),
                colorArgb = readerState.selectedColorArgb,
                highlightStyle = HighlightStyle.BACKGROUND,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        } else {
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.INK,
                tool = readerState.selectedTool,
                points = activeStroke.toList(),
                colorArgb = readerState.selectedColorArgb,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        }
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        activeStroke.clear()
    }

    LaunchedEffect(pageCount) {
        if (readerState.pageCount != pageCount) {
            readerState = readerState.copy(pageCount = pageCount).coerced()
        }
    }

    LaunchedEffect(readerState) {
        onReaderStateChange(readerState)
    }

    LaunchedEffect(autoScrollEnabled, readerState.pageIndex, pageCount) {
        if (autoScrollEnabled && readerState.canGoNext) {
            delay(3500)
            navigateToPage(readerState.pageIndex + 1)
        }
    }

    LaunchedEffect(readerState.searchQuery) {
        val query = readerState.searchQuery.trim()
        if (query.length < 2) {
            searchResults = emptyList()
            isSearchInProgress = false
            return@LaunchedEffect
        }
        delay(250)
        isSearchInProgress = true
        searchResults = withContext(Dispatchers.Default) {
            searchSharedMobilePdf(book, query)
        }
        isSearchInProgress = false
    }

    LaunchedEffect(keepScreenOn) {
        onKeepScreenOnChange(keepScreenOn)
    }

    DisposableEffect(onKeepScreenOnChange) {
        onDispose { onKeepScreenOnChange(false) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SharedMobilePdfReaderDrawer(
                book = book,
                state = readerState,
                onGoToPage = { page ->
                    navigateToPage(page)
                    scope.launch { drawerState.close() }
                },
                onToggleBookmark = { dispatch(SharedPdfReaderAction.BookmarkToggled(readerState.pageIndex, createdAt = currentTimestamp())) }
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                if (showChrome) {
                    SharedMobilePdfReaderTopBar(
                        title = book.cardTitle(),
                        pageIndex = readerState.pageIndex,
                        pageLabel = sharedMobilePdfPageLabel(
                            readerState.pageIndex,
                            pageCount,
                            useTwoPageSpread && readerState.displayMode == PdfDisplayMode.PAGINATION,
                            firstPageStandaloneInSpread
                        ),
                        pageCount = pageCount,
                        displayMode = readerState.displayMode,
                        isSearchActive = readerState.isSearchActive,
                        searchQuery = readerState.searchQuery,
                        isBookmarked = readerState.bookmarks.any { it.pageIndex == readerState.pageIndex },
                        onBack = onBack,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onSearchQueryChange = { query ->
                            dispatch(SharedPdfReaderAction.SearchChanged(query))
                        },
                        onCloseSearch = { dispatch(SharedPdfReaderAction.SearchClosed) },
                        onToggleBookmark = {
                            dispatch(SharedPdfReaderAction.BookmarkToggled(readerState.pageIndex, createdAt = currentTimestamp()))
                        },
                        onToggleDisplayMode = ::toggleDisplayMode,
                        onTheme = { showThemePanel = true },
                        onVisualOptions = { showReaderOptions = !showReaderOptions },
                        tapToTurnPages = tapToTurnPages,
                        onToggleTapToTurnPages = { tapToTurnPages = !tapToTurnPages },
                        isScrollLocked = isScrollLocked,
                        onToggleScrollLock = { isScrollLocked = !isScrollLocked },
                        keepScreenOn = keepScreenOn,
                        onToggleKeepScreenOn = { keepScreenOn = !keepScreenOn },
                        autoScrollEnabled = autoScrollEnabled,
                        onToggleAutoScroll = { autoScrollEnabled = !autoScrollEnabled },
                        onTextSelection = { dispatch(SharedPdfReaderAction.TextSelectionModeChanged(!readerState.isTextSelectionMode)) },
                        onHighlighterTool = { setTool(readerState.lastActiveHighlighterTool) },
                        onBridgeInfo = { onNativePdfBridgeNeeded(book) }
                    )
                }
            },
            bottomBar = {
                if (showChrome && !readerState.isSearchActive) {
                    SharedMobilePdfReaderBottomBar(
                        state = readerState,
                        onShowSlider = { showPageSlider = !showPageSlider },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onToolSelected = ::setTool,
                        onColorSelected = { dispatch(SharedPdfReaderAction.ColorSelected(it)) },
                        onStrokeWidthChange = { dispatch(SharedPdfReaderAction.StrokeWidthChanged(it)) },
                        onUndo = { dispatch(SharedPdfReaderAction.UndoLastAnnotationOnPage(readerState.pageIndex)) },
                        onRedo = { dispatch(SharedPdfReaderAction.RedoAnnotationEdit) },
                        onClearPage = { dispatch(SharedPdfReaderAction.ClearPageAnnotations(readerState.pageIndex)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sharedMobilePdfViewerBackground(activeTheme, readerState.displayMode))
                    .clickable {
                        when (systemUiMode) {
                            SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> showChrome = true
                            SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> showChrome = !showChrome
                            SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> showChrome = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    SharedMobilePdfVerticalPages(
                        book = book,
                        state = readerState,
                        activeTheme = activeTheme,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = pageCount,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        showPageGap = showVerticalPageGap,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        activeStroke = activeStroke,
                        onVisiblePageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = ::finishInkStroke,
                        userScrollEnabled = !isScrollLocked,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobilePdfPaginatedPages(
                        book = book,
                        state = readerState,
                        activeTheme = activeTheme,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = pageCount,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        useTwoPageSpread = useTwoPageSpread,
                        firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        activeStroke = activeStroke,
                        tapToTurnPages = tapToTurnPages,
                        userScrollEnabled = !isScrollLocked,
                        onPageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onToggleChrome = { showChrome = !showChrome },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = ::finishInkStroke,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showChrome && showPageSlider) {
                    SharedMobilePdfPageSlider(
                        pageIndex = readerState.pageIndex,
                        pageCount = pageCount,
                        onPageChange = ::navigateToPage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
                    )
                }
                if (
                    readerState.isSearchActive &&
                    readerState.showSearchResultsPanel &&
                    readerState.searchQuery.isNotBlank()
                ) {
                    SharedMobilePdfSearchResultsPanel(
                        query = readerState.searchQuery,
                        results = searchResults,
                        activeResultIndex = readerState.activeSearchResultIndex,
                        isSearching = isSearchInProgress,
                        onResultClick = { index ->
                            navigateToSearchResult(index)
                            dispatch(SharedPdfReaderAction.SearchResultsPanelToggled)
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                    )
                }
                if (
                    readerState.isSearchActive &&
                    !readerState.showSearchResultsPanel &&
                    searchResults.isNotEmpty()
                ) {
                    SharedMobilePdfSearchNavigationPill(
                        activeIndex = readerState.activeSearchResultIndex.coerceAtLeast(0),
                        resultCount = searchResults.size,
                        onPrevious = { navigateToSearchResult(readerState.activeSearchResultIndex - 1) },
                        onNext = { navigateToSearchResult(readerState.activeSearchResultIndex + 1) },
                        onShowResults = { dispatch(SharedPdfReaderAction.SearchResultsPanelToggled) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
        if (showThemePanel) {
            SharedMobilePdfThemePanel(
                currentThemeId = readerState.themeId,
                textureTransparency = globalTextureTransparency,
                onTextureTransparencyChange = { globalTextureTransparency = it },
                onThemeSelected = { themeId ->
                    dispatch(SharedPdfReaderAction.ThemeChanged(themeId))
                    showThemePanel = false
                },
                onDismiss = { showThemePanel = false }
            )
        }
        if (showReaderOptions) {
            SharedMobilePdfVisualOptionsSheet(
                displayMode = readerState.displayMode,
                systemUiMode = systemUiMode,
                useTwoPageSpread = useTwoPageSpread,
                firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                showVerticalPageGap = showVerticalPageGap,
                showPageNumberOverlay = showPageNumberOverlay,
                onSystemUiModeChange = { mode ->
                    systemUiMode = mode
                    when (mode) {
                        SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> showChrome = true
                        SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> Unit
                        SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> showChrome = false
                    }
                },
                onTwoPageSpreadChange = { useTwoPageSpread = it },
                onFirstPageStandaloneChange = { firstPageStandaloneInSpread = it },
                onShowVerticalPageGapChange = { showVerticalPageGap = it },
                onShowPageNumberOverlayChange = { showPageNumberOverlay = it },
                onDismiss = { showReaderOptions = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfReaderTopBar(
    title: String,
    pageIndex: Int,
    pageLabel: String,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    isSearchActive: Boolean,
    searchQuery: String,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onTheme: () -> Unit,
    onVisualOptions: () -> Unit,
    tapToTurnPages: Boolean,
    onToggleTapToTurnPages: () -> Unit,
    isScrollLocked: Boolean,
    onToggleScrollLock: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    autoScrollEnabled: Boolean,
    onToggleAutoScroll: () -> Unit,
    onTextSelection: () -> Unit,
    onHighlighterTool: () -> Unit,
    onBridgeInfo: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHiddenToolsExpanded by remember { mutableStateOf(false) }
    var showReadingModeExpanded by remember { mutableStateOf(false) }
    var showTtsSettingsExpanded by remember { mutableStateOf(false) }
    var showFileActionsExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search PDF") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Page $pageLabel of ${pageCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
                SharedMobilePdfTopToolButton(label = "Dictionary", onClick = onBridgeInfo) {
                    Icon(Icons.Default.Book, contentDescription = null)
                }
                SharedMobilePdfTopToolButton(label = "Theme", onClick = onTheme) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                }
                SharedMobilePdfTopToolButton(label = if (isScrollLocked) "Unlock" else "Lock", onClick = onToggleScrollLock) {
                    Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null)
                }
            }
            Box {
                IconButton(onClick = {
                    showHiddenToolsExpanded = false
                    showReadingModeExpanded = false
                    showTtsSettingsExpanded = false
                    showFileActionsExpanded = false
                    showMoreMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "PDF options")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                SharedMobilePdfOverflowItem(
                    "Customize Toolbar",
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onBridgeInfo()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Hidden tools",
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded }
                )
                if (showHiddenToolsExpanded) {
                    SharedMobilePdfOverflowItem("Brightness", leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }, onClick = onBridgeInfo)
                    SharedMobilePdfOverflowItem("Highlight Selectable Text", leadingIcon = { Icon(Icons.Default.Fonts, contentDescription = null) }, onClick = onHighlighterTool)
                    SharedMobilePdfOverflowItem("Screen Orientation", leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }, onClick = onBridgeInfo)
                }
                SharedMobilePdfOverflowItem(
                    "Visual Options",
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onVisualOptions()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Change Reading Mode",
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showReadingModeExpanded = !showReadingModeExpanded }
                )
                if (showReadingModeExpanded) {
                    SharedMobilePdfOverflowItem(
                        "Vertical Scrolling",
                        trailingIcon = { if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.VERTICAL_SCROLL) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                    SharedMobilePdfOverflowItem(
                        "Pagination",
                        trailingIcon = { if (displayMode == PdfDisplayMode.PAGINATION) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.PAGINATION) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                }
                SharedMobilePdfOverflowItem(
                    "Tap to Turn Pages",
                    enabled = displayMode == PdfDisplayMode.PAGINATION,
                    trailingIcon = { if (tapToTurnPages) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleTapToTurnPages()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Keep Screen On",
                    trailingIcon = { if (keepScreenOn) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleKeepScreenOn()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Auto Scroll",
                    enabled = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                    onClick = {
                        showMoreMenu = false
                        onToggleAutoScroll()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "TTS Settings",
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded }
                )
                if (showTtsSettingsExpanded) {
                    SharedMobilePdfOverflowItem("Voice Settings", leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) }, onClick = onBridgeInfo)
                    SharedMobilePdfOverflowItem("Word Replacements", leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) }, onClick = onBridgeInfo)
                }
                SharedMobilePdfOverflowItem(
                    if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                    onClick = {
                        showMoreMenu = false
                        onToggleBookmark()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Insert Blank Page",
                    onClick = {
                        showMoreMenu = false
                        onBridgeInfo()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Generate Text View",
                    leadingIcon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onTextSelection()
                    }
                )
                SharedMobilePdfOverflowItem(
                    "Share, Save or Print",
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showFileActionsExpanded = !showFileActionsExpanded }
                )
                if (showFileActionsExpanded) {
                    SharedMobilePdfOverflowItem("Share", leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }, onClick = onBridgeInfo)
                    SharedMobilePdfOverflowItem("Save Copy to Device", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = onBridgeInfo)
                    SharedMobilePdfOverflowItem("Print", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = onBridgeInfo)
                }
                SharedMobilePdfOverflowItem(
                    "File Information",
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onBridgeInfo()
                    }
                )
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfTopToolButton(
    label: String,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        icon()
    }
}

@Composable
private fun SharedMobilePdfOverflowItem(
    text: String,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            leadingIcon?.invoke()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            trailingIcon?.invoke()
        }
    }
    HorizontalDivider()
}

private enum class SharedMobilePdfSystemUiMode(val label: String) {
    ALWAYS_SHOW("Always Show"),
    SYNC_WITH_MENUS("Sync with Menus"),
    ALWAYS_HIDE("Always Hide")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfVisualOptionsSheet(
    displayMode: PdfDisplayMode,
    systemUiMode: SharedMobilePdfSystemUiMode,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    onSystemUiModeChange: (SharedMobilePdfSystemUiMode) -> Unit,
    onTwoPageSpreadChange: (Boolean) -> Unit,
    onFirstPageStandaloneChange: (Boolean) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("System UI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose when the reader toolbars and system controls are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedMobilePdfSystemUiMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == systemUiMode,
                        onClick = { onSystemUiModeChange(mode) },
                        label = { Text(mode.label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Page Layout", style = MaterialTheme.typography.titleMedium)
            if (displayMode == PdfDisplayMode.PAGINATION) {
                Spacer(Modifier.height(4.dp))
                Text("PDF Page Spread", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useTwoPageSpread,
                        onClick = { onTwoPageSpreadChange(false) },
                        label = { Text("Single") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = useTwoPageSpread,
                        onClick = { onTwoPageSpreadChange(true) },
                        label = { Text("Two Page") },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (useTwoPageSpread) {
                    SharedMobilePdfVisualOptionSwitchRow(
                        title = "First Page Alone",
                        description = "Show the cover by itself before paired pages.",
                        checked = firstPageStandaloneInSpread,
                        onCheckedChange = onFirstPageStandaloneChange
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Remove Page Gap",
                description = "Display adjacent pages without spacing.",
                checked = !showVerticalPageGap,
                onCheckedChange = { onShowVerticalPageGapChange(!it) }
            )
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Hide Page Number Overlay",
                description = "Hide the number shown on each PDF page.",
                checked = !showPageNumberOverlay,
                onCheckedChange = { onShowPageNumberOverlayChange(!it) }
            )
        }
    }
}

@Composable
private fun SharedMobilePdfVisualOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SharedMobilePdfReaderBottomBar(
    state: SharedPdfReaderState,
    onShowSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SharedMobilePdfBottomToolButton(onClick = onShowSlider) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Navigation slider")
                }
                SharedMobilePdfBottomToolButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Contents")
                }
                SharedMobilePdfBottomToolButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                SharedMobilePdfBottomToolButton {
                    Icon(Icons.Default.Ai, contentDescription = "AI features")
                }
                SharedMobilePdfBottomToolButton(
                    selected = state.selectedTool != PdfInkTool.NONE,
                    onClick = {
                        onToolSelected(if (state.selectedTool == PdfInkTool.NONE) PdfInkTool.PEN else PdfInkTool.NONE)
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit mode")
                }
                SharedMobilePdfBottomToolButton {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Text to speech")
                }
            }
            if (state.selectedTool != PdfInkTool.NONE || state.isTextSelectionMode) {
                SharedPdfInteractionDock(
                    isTextSelectionMode = state.isTextSelectionMode,
                    selectedTool = state.selectedTool,
                    selectedColor = state.selectedColorArgb,
                    strokeWidth = state.strokeWidth,
                    toolConfigs = state.toolConfigs,
                    penPalette = state.penPalette,
                    lastActivePenTool = state.lastActivePenTool,
                    lastActiveHighlighterTool = state.lastActiveHighlighterTool,
                    onPanSelected = { onToolSelected(PdfInkTool.NONE) },
                    onTextSelectionSelected = { onToolSelected(PdfInkTool.NONE) },
                    onToolSelected = onToolSelected,
                    onColorSelected = onColorSelected,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onClearPage = onClearPage,
                    canUndo = state.annotations.any { it.pageIndex == state.pageIndex },
                    canRedo = state.canRedoAnnotationEdit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfBottomToolButton(
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .widthIn(min = 44.dp)
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun SharedMobilePdfReaderDrawer(
    book: BookItem,
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    onToggleBookmark: () -> Unit
) {
    var selectedSection by remember { mutableStateOf(SharedMobilePdfDrawerSection.CHAPTERS) }
    ModalDrawerSheet(modifier = Modifier.width(348.dp)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedSection.ordinal) {
                SharedMobilePdfDrawerSection.entries.forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        text = { Text(section.label) }
                    )
                }
            }
            when (selectedSection) {
                SharedMobilePdfDrawerSection.TABS -> SharedMobilePdfPagesDrawerPage(
                    state = state,
                    onGoToPage = onGoToPage,
                    modifier = Modifier.weight(1f)
                )
                SharedMobilePdfDrawerSection.CHAPTERS -> SharedMobilePdfEmptyDrawerPage(
                    text = "Chapters are not available for this book.",
                    modifier = Modifier.weight(1f)
                )
                SharedMobilePdfDrawerSection.BOOKMARKS -> SharedMobilePdfBookmarksDrawerPage(
                    state = state,
                    onGoToPage = onGoToPage,
                    modifier = Modifier.weight(1f)
                )
                SharedMobilePdfDrawerSection.HIGHLIGHTS -> SharedMobilePdfAnnotationsDrawerPage(
                    state = state,
                    onGoToPage = onGoToPage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class SharedMobilePdfDrawerSection(val label: String) {
    TABS("Tabs"),
    CHAPTERS("Chapters"),
    BOOKMARKS("Bookmarks"),
    HIGHLIGHTS("Highlights")
}

@Composable
private fun SharedMobilePdfEmptyDrawerPage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SharedMobilePdfPagesDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
    ) {
        items(state.pageCount.coerceAtLeast(1)) { page ->
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                label = { Text("Page ${page + 1}") },
                selected = page == state.pageIndex,
                onClick = { onGoToPage(page) },
                badge = {
                    if (state.bookmarks.any { it.pageIndex == page }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmarked", modifier = Modifier.size(18.dp))
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfBookmarksDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.bookmarks.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
    ) {
        items(state.bookmarks.sortedBy { it.pageIndex }, key = { "bookmark_${it.pageIndex}_${it.createdAt}" }) { bookmark ->
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                label = { Text(bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" }) },
                selected = bookmark.pageIndex == state.pageIndex,
                onClick = { onGoToPage(bookmark.pageIndex) },
                badge = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfAnnotationsDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.annotations.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No annotations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
    ) {
        items(state.annotations.sortedWith(compareBy({ it.pageIndex }, { it.createdAt }, { it.id })), key = { it.id }) { annotation ->
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                label = {
                    Column {
                        Text("Page ${annotation.pageIndex + 1}")
                        val detail = when {
                            annotation.text.isNotBlank() -> annotation.text
                            annotation.kind == PdfAnnotationKind.HIGHLIGHT -> "Highlight"
                            else -> annotation.tool.name.lowercase().replaceFirstChar { it.titlecase() }
                        }
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                selected = annotation.pageIndex == state.pageIndex,
                onClick = { onGoToPage(annotation.pageIndex) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfSearchResultsPanel(
    query: String,
    results: List<SharedPdfSearchResult>,
    activeResultIndex: Int,
    isSearching: Boolean,
    onResultClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            query.trim().length < 2 -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Enter at least 2 characters", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No results for “${query.trim()}”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(results.size) { index ->
                    val result = results[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == activeResultIndex) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onResultClick(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${result.pageIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 28.dp)
                        )
                        Text(
                            text = result.preview.ifBlank { "Match on page ${result.pageIndex + 1}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfSearchNavigationPill(
    activeIndex: Int,
    resultCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = resultCount > 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous result")
            }
            TextButton(onClick = onShowResults) {
                Text("${activeIndex + 1} of $resultCount")
            }
            IconButton(onClick = onNext, enabled = resultCount > 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next result")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfThemePanel(
    currentThemeId: String,
    textureTransparency: Float,
    onTextureTransparencyChange: (Float) -> Unit,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialTextured = BuiltInPdfReaderThemes.firstOrNull { it.id == currentThemeId }?.textureId != null
    var selectedTabIndex by remember(currentThemeId) { mutableStateOf(if (initialTextured) 1 else 0) }
    var preserveImageColors by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleThemes = remember(selectedTabIndex) {
        BuiltInPdfReaderThemes.filter { theme -> (theme.textureId != null) == (selectedTabIndex == 1) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Reading Themes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                    Text("Solid Colors", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                    Text("Textured", modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (selectedTabIndex == 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Texture Transparency", style = MaterialTheme.typography.labelMedium)
                    Text("${(textureTransparency * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = textureTransparency,
                    onValueChange = onTextureTransparencyChange,
                    valueRange = 0f..1f
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Preserve Image Colors", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Keep images unchanged when a theme is applied", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = preserveImageColors, onCheckedChange = { preserveImageColors = it })
            }
            Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(visibleThemes, key = { it.id }) { theme ->
                    SharedMobilePdfThemeGridItem(
                        theme = theme,
                        selected = currentThemeId == theme.id,
                        textureAlpha = 1f - textureTransparency,
                        onClick = { onThemeSelected(theme.id) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Themes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Add, contentDescription = "New theme", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No custom themes yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfThemeGridItem(
    theme: ReaderTheme,
    selected: Boolean,
    textureAlpha: Float,
    onClick: () -> Unit
) {
    val background = theme.backgroundColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.surfaceVariant
    val foreground = theme.textColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val textureBitmap = sharedMobilePdfTextureBitmap(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(background, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (textureBitmap != null && textureAlpha > 0f) {
                Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                        alpha = textureAlpha.coerceIn(0f, 1f)
                    )
                }
            }
            Text("Aa", color = foreground, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

private fun sharedMobilePdfViewerBackground(theme: ReaderTheme, displayMode: PdfDisplayMode): Color {
    return when (theme.id) {
        "no_theme", "system" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.White else Color.Black
        "reverse" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.Black else Color.White
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

private fun sharedMobilePdfPageBackground(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.White
        "reverse" -> Color.Black
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

private fun sharedMobilePdfPageTextColor(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.Black
        "reverse" -> Color.White
        else -> theme.textColor.takeIf { it.isSpecified } ?: Color.Black
    }
}

private fun sharedMobilePdfColorFilter(theme: ReaderTheme): ColorFilter? {
    if (theme.id == "no_theme" || theme.id == "system") return null
    val matrix = if (theme.id == "reverse") {
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    } else {
        val background = sharedMobilePdfPageBackground(theme)
        val foreground = sharedMobilePdfPageTextColor(theme)
        val backgroundRed = background.red * 255f
        val backgroundGreen = background.green * 255f
        val backgroundBlue = background.blue * 255f
        val foregroundRed = foreground.red * 255f
        val foregroundGreen = foreground.green * 255f
        val foregroundBlue = foreground.blue * 255f
        val deltaRed = (backgroundRed - foregroundRed) / 255f
        val deltaGreen = (backgroundGreen - foregroundGreen) / 255f
        val deltaBlue = (backgroundBlue - foregroundBlue) / 255f
        floatArrayOf(
            deltaRed * 0.2126f, deltaRed * 0.7152f, deltaRed * 0.0722f, 0f, foregroundRed,
            deltaGreen * 0.2126f, deltaGreen * 0.7152f, deltaGreen * 0.0722f, 0f, foregroundGreen,
            deltaBlue * 0.2126f, deltaBlue * 0.7152f, deltaBlue * 0.0722f, 0f, foregroundBlue,
            0f, 0f, 0f, 1f, 0f
        )
    }
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
private fun SharedMobilePdfVerticalPages(
    book: BookItem,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    pageCount: Int,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    showPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    activeStroke: List<PdfPagePoint>,
    onVisiblePageChanged: (Int) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int) -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex.coerceIn(0, pageCount - 1))
    LaunchedEffect(navigationRequestToken, pageCount) {
        val target = navigationRequestPage.coerceIn(0, pageCount - 1)
        if (listState.firstVisibleItemIndex != target) {
            listState.animateScrollToItem(target)
        }
    }
    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { visiblePage ->
                onVisiblePageChanged(visiblePage.coerceIn(0, pageCount - 1))
            }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (showPageGap) 12.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (showPageGap) 12.dp else 0.dp)
    ) {
        items(pageCount) { page ->
            val render = rememberSharedMobilePdfPageRender(book, page)
            SharedMobilePdfPageSurface(
                book = book,
                pageIndex = page,
                pageCount = pageCount,
                pageRender = render,
                activeTheme = activeTheme,
                textureAlpha = textureAlpha,
                showPageNumberOverlay = showPageNumberOverlay,
                searchHighlights = searchResults.filter { it.pageIndex == page }.flatMap { it.boundsList },
                annotations = state.annotations.filter { it.pageIndex == page },
                activeStroke = if (page == state.pageIndex) activeStroke else emptyList(),
                selectedTool = state.selectedTool,
                selectedColorArgb = state.selectedColorArgb,
                strokeWidth = state.strokeWidth,
                onCanvasSizeChanged = onCanvasSizeChanged,
                onFinishInkStroke = onFinishInkStroke,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobilePdfPaginatedPages(
    book: BookItem,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    pageCount: Int,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    activeStroke: List<PdfPagePoint>,
    tapToTurnPages: Boolean,
    userScrollEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val spreadStarts = remember(pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
        sharedMobilePdfSpreadStarts(pageCount, useTwoPageSpread, firstPageStandaloneInSpread)
    }
    fun pagerIndexForPage(pageIndex: Int): Int {
        val target = pageIndex.coerceIn(0, pageCount - 1)
        return spreadStarts.indexOfLast { it <= target }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = pagerIndexForPage(state.pageIndex),
        pageCount = { spreadStarts.size.coerceAtLeast(1) }
    )
    LaunchedEffect(navigationRequestToken, spreadStarts) {
        val requestedPage = if (navigationRequestToken == 0) state.pageIndex else navigationRequestPage
        val target = pagerIndexForPage(requestedPage)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState, spreadStarts) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                onPageChanged(spreadStarts.getOrElse(settledPage) { 0 })
            }
    }
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
        beyondViewportPageCount = 1,
        modifier = modifier
    ) { pagerPage ->
        val spreadPages = remember(pagerPage, spreadStarts, pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
            val start = spreadStarts.getOrElse(pagerPage) { 0 }
            when {
                !useTwoPageSpread -> listOf(start)
                firstPageStandaloneInSpread && start == 0 -> listOf(0)
                else -> listOf(start, start + 1).filter { it in 0 until pageCount }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tapToTurnPages, pagerPage, spreadStarts, state.selectedTool) {
                    if (state.selectedTool != PdfInkTool.NONE) return@pointerInput
                    detectTapGestures { offset ->
                        val edge = size.width * 0.25f
                        when {
                            tapToTurnPages && offset.x < edge && pagerPage > 0 -> scope.launch { pagerState.animateScrollToPage(pagerPage - 1) }
                            tapToTurnPages && offset.x > size.width - edge && pagerPage < spreadStarts.lastIndex -> scope.launch { pagerState.animateScrollToPage(pagerPage + 1) }
                            else -> onToggleChrome()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageGap = if (spreadPages.size > 1) 8.dp else 0.dp
                val viewportHeight = maxHeight
                val slotWidth = (maxWidth - pageGap * (spreadPages.size - 1)) / spreadPages.size
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(pageGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    spreadPages.forEach { pageIndex ->
                        val render = rememberSharedMobilePdfPageRender(book, pageIndex)
                        val aspectRatio = render.aspectRatio.coerceIn(0.1f, 10f)
                        val widthLimited = slotWidth.value / viewportHeight.value <= aspectRatio
                        val fittedWidth = if (widthLimited) slotWidth else viewportHeight * aspectRatio
                        val fittedHeight = fittedWidth / aspectRatio
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            SharedMobilePdfPageSurface(
                                book = book,
                                pageIndex = pageIndex,
                                pageCount = pageCount,
                                pageRender = render,
                                activeTheme = activeTheme,
                                textureAlpha = textureAlpha,
                                showPageNumberOverlay = showPageNumberOverlay,
                                searchHighlights = searchResults.filter { it.pageIndex == pageIndex }.flatMap { it.boundsList },
                                annotations = state.annotations.filter { it.pageIndex == pageIndex },
                                activeStroke = if (pageIndex == state.pageIndex) activeStroke else emptyList(),
                                selectedTool = state.selectedTool,
                                selectedColorArgb = state.selectedColorArgb,
                                strokeWidth = state.strokeWidth,
                                onCanvasSizeChanged = onCanvasSizeChanged,
                                onFinishInkStroke = onFinishInkStroke,
                                // PdfZoomSpec.default controls render resolution, not the
                                // on-screen page scale. Applying it as a graphics transform
                                // enlarged the fitted page by 1.35x and exposed adjacent pages.
                                modifier = Modifier.size(fittedWidth, fittedHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sharedMobilePdfSpreadStarts(
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): List<Int> {
    return PdfSpreadLayout.spreadStartPageIndices(
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    ).ifEmpty { listOf(0) }
}

private fun sharedMobilePdfPageLabel(
    pageIndex: Int,
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): String {
    return PdfSpreadLayout.pageRangeLabel(
        pageIndex = pageIndex,
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    )
}

@Composable
private fun SharedMobilePdfSearchHighlightOverlay(
    bounds: List<PdfPageBounds>,
    modifier: Modifier = Modifier
) {
    if (bounds.isEmpty()) return
    Canvas(modifier) {
        bounds.forEach { item ->
            val left = item.left * size.width
            val top = item.top * size.height
            val right = item.right * size.width
            val bottom = item.bottom * size.height
            if (right > left && bottom > top) {
                drawRect(
                    color = Color(0x66FFEB3B),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${pageIndex + 1}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = if (pageCount > 1) pageIndex.toFloat() else 0f,
                onValueChange = { onPageChange(it.toInt().coerceIn(0, pageCount - 1)) },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f)
            )
            Text("$pageCount", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SharedMobilePdfPageSurface(
    book: BookItem,
    pageIndex: Int,
    pageCount: Int,
    pageRender: SharedMobilePdfPageRender,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    showPageNumberOverlay: Boolean,
    searchHighlights: List<PdfPageBounds>,
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    selectedTool: PdfInkTool,
    selectedColorArgb: Int,
    strokeWidth: Float,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var localCanvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    val textureBitmap = sharedMobilePdfTextureBitmap(activeTheme)
    Surface(
        color = sharedMobilePdfPageBackground(activeTheme),
        contentColor = sharedMobilePdfPageTextColor(activeTheme),
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio(pageRender.aspectRatio)
            .clipToBounds()
            .onSizeChanged {
                localCanvasSize = it
                onCanvasSizeChanged(it)
            }
            .then(
                if (selectedTool == PdfInkTool.NONE) Modifier else Modifier.pointerInput(selectedTool, localCanvasSize, pageIndex) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                (activeStroke as? MutableList<PdfPagePoint>)?.add(offset.toSharedMobilePdfPoint(localCanvasSize))
                            }
                        },
                        onDrag = { change, _ ->
                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                (activeStroke as? MutableList<PdfPagePoint>)?.add(change.position.toSharedMobilePdfPoint(localCanvasSize))
                            }
                        },
                        onDragEnd = { onFinishInkStroke(pageIndex) },
                        onDragCancel = { (activeStroke as? MutableList<PdfPagePoint>)?.clear() }
                    )
                }
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (pageRender.bitmap != null) {
                Image(
                    bitmap = pageRender.bitmap,
                    contentDescription = book.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = sharedMobilePdfColorFilter(activeTheme)
                )
            } else {
                SharedMobilePdfPagePlaceholder(
                    book = book,
                    pageIndex = pageIndex,
                    errorMessage = pageRender.errorMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (textureBitmap != null && textureAlpha > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        brush = ShaderBrush(
                            ImageShader(
                                image = textureBitmap,
                                tileModeX = TileMode.Repeated,
                                tileModeY = TileMode.Repeated
                            )
                        ),
                        alpha = textureAlpha.coerceIn(0f, 1f),
                        blendMode = if (activeTheme.isDark || activeTheme.id == "reverse") BlendMode.Screen else BlendMode.Multiply
                    )
                }
            }
            SharedMobilePdfSearchHighlightOverlay(
                bounds = searchHighlights,
                modifier = Modifier.fillMaxSize()
            )
            SharedPdfAnnotationOverlay(
                annotations = annotations,
                activeStroke = activeStroke,
                canvasSize = localCanvasSize,
                activeTool = selectedTool,
                activeStrokeColorArgb = selectedColorArgb,
                activeStrokeWidth = strokeWidth
            )
            if (showPageNumberOverlay) {
                SharedPdfPageNumberOverlay(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun sharedMobilePdfTextureBitmap(theme: ReaderTheme): ImageBitmap? {
    val resource = when (theme.textureId) {
        "asset:ep_naturalwhite.webp" -> Res.drawable.ep_naturalwhite
        "asset:retina_wood.webp" -> Res.drawable.retina_wood
        "asset:light-veneer.webp" -> Res.drawable.light_veneer
        "asset:grey_wash_wall.webp" -> Res.drawable.grey_wash_wall
        "asset:classy_fabric.webp" -> Res.drawable.classy_fabric
        "asset:retro_intro.webp" -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

@Composable
private fun SharedMobilePdfPagePlaceholder(
    book: BookItem,
    pageIndex: Int,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val margin = size.width * 0.09f
        val lineStart = margin
        val lineEnd = size.width - margin
        val top = size.height * 0.16f
        val path = Path().apply {
            moveTo(lineStart, top)
            lineTo(lineEnd * 0.72f, top)
        }
        drawPath(
            path = path,
            color = Color(0xFF303030),
            style = Stroke(width = 3f)
        )
        repeat(10) { index ->
            val y = top + 44f + index * 34f
            val end = if (index % 4 == 3) lineEnd * 0.72f else lineEnd
            drawLine(
                color = Color(0xFF9E9E9E),
                start = Offset(lineStart, y),
                end = Offset(end, y),
                strokeWidth = 2f
            )
        }
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(lineStart, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width - margin * 2f, size.height * 0.2f)
        )
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Text(
                text = book.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = errorMessage ?: "Rendering PDF page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF616161)
            )
        }
    }
}

private fun Offset.toSharedMobilePdfPoint(size: IntSize): PdfPagePoint {
    return PdfPagePoint(
        x = (x / size.width.toFloat()).coerceIn(0f, 1f),
        y = (y / size.height.toFloat()).coerceIn(0f, 1f),
        timestamp = currentTimestamp()
    )
}

@Composable
fun SharedMobileHomeScreen(
    state: SharedReaderScreenState,
    actions: SharedMobileHomeActions,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val isContextualMode = selectedIds.isNotEmpty()
    var showCreateShelf by remember { mutableStateOf(false) }
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
                        onPin = {
                            state.recentBooks.filter { it.id in selectedIds }.forEach(actions::togglePinned)
                        },
                        onAddToShelf = { showCreateShelf = true },
                        onDelete = actions::deleteSelectedBooks
                    )
                } else {
                    SharedMobileHomeTopBar(
                        onDrawerClick = actions::openDrawer,
                        onSearchClick = actions::openSearch,
                        isSyncEnabled = state.isSyncEnabled || state.syncedFolders.any { it.localSyncEnabled },
                        onRefresh = actions::refresh,
                        onSettingsClick = actions::openSettings,
                        onMoreClick = actions::openMoreActions
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (model.isEmpty) {
                SharedMobileEmptyLibrary(
                    title = if (model.isLibraryEmpty) "Your library is empty" else "No recent files",
                    message = if (model.isLibraryEmpty) {
                        "Select a PDF, EPUB, comic, or document to start reading."
                    } else {
                        "Open books from the library and they will appear here."
                    },
                    actionLabel = "Select file",
                    onAction = actions::importBooks,
                    secondaryActionLabel = if (model.isLibraryEmpty) "Sync folder" else null,
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
                                activeBookId = state.activeTabBookId,
                                onOpenTab = actions::openBook,
                                onCloseTab = actions::closeTab,
                                onCloseAllTabs = actions::closeAllTabs
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
                                onOpenBook = actions::openBook,
                                onLongPressBook = actions::longPressBook,
                                onTogglePinned = actions::togglePinned
                            )
                        }
                    }

                    item(key = "recent") {
                        SharedMobileBookGridSection(
                            title = "Recent files",
                            books = model.recentBooks,
                            selectedBookIds = selectedIds,
                            pinnedBookIds = state.pinnedHomeBookIds,
                            onOpenBook = actions::openBook,
                            onLongPressBook = actions::longPressBook,
                            onTogglePinned = actions::togglePinned
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
                    Button(onClick = actions::importBooks) { Text("Select file") }
                    Button(onClick = actions::navigateToFolderSync) { Text("Sync folder") }
                }
            }
        }
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = "Add selected books to shelf",
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                actions.createShelfFromSelectedBooks(name)
                showCreateShelf = false
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
    onSelectAll: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRemoveFilters: (LibraryFilters) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewShelfClick: () -> Unit = {},
    onOpenShelf: (Shelf) -> Unit = {},
    onLongPressShelf: (Shelf) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onCreateShelf: (String, Set<String>) -> Unit = { _, _ -> },
    onDeleteBooks: (Set<String>) -> Unit = {},
    onDeleteShelves: (Set<String>) -> Unit = {},
    onNavigateShelfBack: () -> Unit = {},
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
    val selectedIds = state.selectedBookIds
    val selectedShelves = state.selectedShelfIds
    val isBookContextualMode = selectedIds.isNotEmpty()
    val isShelfContextualMode = selectedShelves.isNotEmpty() && selectedTab == SharedMobileLibraryTab.SHELVES
    var showFilters by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var showDeleteBooks by remember { mutableStateOf(false) }
    var showDeleteShelves by remember { mutableStateOf(false) }
    val viewedShelf = state.viewingShelfId?.let { id -> state.shelves.firstOrNull { it.id == id } }
    val searchedBooks = remember(state.libraryBooks, state.searchQuery, state.libraryFilters) {
        applyLibraryFilters(
            state.libraryBooks.filteredSharedMobileBooks(state.searchQuery),
            state.libraryFilters
        )
    }
    val sortedSearchedBooks = remember(searchedBooks, state.sortOrder) { sortBooks(searchedBooks, state.sortOrder) }

    if (viewedShelf != null) {
        SharedMobileShelfDetail(
            shelf = viewedShelf,
            selectedBookIds = selectedIds,
            pinnedBookIds = state.pinnedLibraryBookIds,
            onBack = onNavigateShelfBack,
            onOpenBook = onOpenBook,
            onLongPressBook = onLongPressBook,
            onTogglePinned = onTogglePinned,
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
                        onSelectAll = onSelectAll,
                        onPin = {
                            state.libraryBooks.filter { it.id in selectedIds }.forEach(onTogglePinned)
                        },
                        onAddToShelf = { showCreateShelf = true },
                        onDelete = { showDeleteBooks = true }
                    )

                    isShelfContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedShelves.size,
                        onClose = onClearSelection,
                        onSelectAll = {},
                        onPin = {},
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
                                text = { Text(tab.label) }
                            )
                        }
                    }
                    if (selectedTab == SharedMobileLibraryTab.BOOKS && state.libraryFilters.isActive) {
                        SharedMobileLibraryFilterChips(
                            filters = state.libraryFilters,
                            onClearFilters = onClearFilters,
                            onRemoveFilters = onRemoveFilters
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isBookContextualMode && !isShelfContextualMode) {
                when (selectedTab) {
                    SharedMobileLibraryTab.BOOKS -> if (state.libraryBooks.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            text = { Text("Add file") },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = onImportBooks
                        )
                    }

                    SharedMobileLibraryTab.SHELVES -> ExtendedFloatingActionButton(
                        text = { Text("New shelf") },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showCreateShelf = true; onNewShelfClick() }
                    )

                    else -> Unit
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            SharedMobileLibraryTab.BOOKS -> SharedMobileBookList(
                books = sortedSearchedBooks,
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.SHELVES -> SharedMobileShelfList(
                shelves = state.shelves.filter { it.type != ShelfType.FOLDER && it.type != ShelfType.TAG },
                onOpenShelf = onOpenShelf,
                onLongPressShelf = onLongPressShelf,
                selectedShelfIds = state.selectedShelfIds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.FOLDERS -> SharedMobileShelfList(
                shelves = state.shelves.filter { it.type == ShelfType.FOLDER },
                onOpenShelf = onOpenShelf,
                onLongPressShelf = onLongPressShelf,
                emptyTitle = "No folders yet",
                emptyMessage = "Folder sync is not connected on iOS yet.",
                selectedShelfIds = state.selectedShelfIds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                mobileLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
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
            title = if (selectedIds.isEmpty()) "New shelf" else "Add selected books to shelf",
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                onCreateShelf(name, selectedIds)
                showCreateShelf = false
            }
        )
    }
    if (showDeleteBooks) {
        SharedMobileDeleteConfirmationDialog(
            title = "Remove books?",
            body = "Remove ${selectedIds.size} selected book(s) from this iOS library?",
            onDismiss = { showDeleteBooks = false },
            onConfirm = {
                onDeleteBooks(selectedIds)
                showDeleteBooks = false
            }
        )
    }
    if (showDeleteShelves) {
        SharedMobileDeleteConfirmationDialog(
            title = "Delete shelves?",
            body = "Delete ${selectedShelves.size} selected shelf/shelves? Books will remain in the library.",
            onDismiss = { showDeleteShelves = false },
            onConfirm = {
                onDeleteShelves(selectedShelves)
                showDeleteShelves = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileShelfDetail(
    shelf: Shelf,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onBack: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(shelf.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SharedMobileBookList(
            books = sortBooks(shelf.books, SortOrder.RECENT),
            selectedBookIds = selectedBookIds,
            pinnedBookIds = pinnedBookIds,
            onOpenBook = onOpenBook,
            onLongPressBook = onLongPressBook,
            onTogglePinned = onTogglePinned,
            empty = {
                SharedMobileEmptyLibrary(
                    title = "No books in ${shelf.name}",
                    message = "Add books from the Library selection menu.",
                    actionLabel = null,
                    onAction = {},
                    modifier = Modifier.fillMaxSize()
                )
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
private fun SharedMobileLibraryFilterDialog(
    state: SharedReaderScreenState,
    onDismiss: () -> Unit,
    onFiltersChange: (LibraryFilters) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter library") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("File type", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FileType.entries) { type ->
                            FilterChip(
                                selected = type in state.libraryFilters.fileTypes,
                                onClick = {
                                    val next = state.libraryFilters.fileTypes.let { selected ->
                                        if (type in selected) selected - type else selected + type
                                    }
                                    onFiltersChange(state.libraryFilters.copy(fileTypes = next))
                                },
                                label = { Text(type.name) }
                            )
                        }
                    }
                }
                item {
                    Text("Reading status", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReadStatusFilter.entries) { status ->
                            FilterChip(
                                selected = state.libraryFilters.readStatus == status,
                                onClick = { onFiltersChange(state.libraryFilters.copy(readStatus = status)) },
                                label = { Text(status.sharedMobileLabel()) }
                            )
                        }
                    }
                }
                if (state.allTags.isNotEmpty()) {
                    item {
                        Text("Tags", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.allTags, key = { it.id }) { tag ->
                                FilterChip(
                                    selected = tag.id in state.libraryFilters.tagIds,
                                    onClick = {
                                        val next = state.libraryFilters.tagIds.let { selected ->
                                            if (tag.id in selected) selected - tag.id else selected + tag.id
                                        }
                                        onFiltersChange(state.libraryFilters.copy(tagIds = next))
                                    },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { onFiltersChange(LibraryFilters()); onDismiss() }) { Text("Clear") }
        }
    )
}

@Composable
private fun SharedMobileCreateShelfDialog(
    title: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Shelf name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SharedMobileDeleteConfirmationDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

enum class SharedMobileLibraryTab(val label: String) {
    BOOKS("Books"),
    SHELVES("Shelves"),
    FOLDERS("Folders"),
    CATALOGS("Catalogs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileHomeTopBar(
    onDrawerClick: () -> Unit,
    onSearchClick: () -> Unit,
    isSyncEnabled: Boolean,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text("Reader") },
        navigationIcon = {
            IconButton(onClick = onDrawerClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            if (isSyncEnabled) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Sync, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
            }
        }
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
    var showSortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("Library") },
        actions = {
            if (selectedTab == SharedMobileLibraryTab.BOOKS) {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sortOrder.sharedMobileLabel())
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.sharedMobileLabel()) },
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
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
    onAddToShelf: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            IconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, contentDescription = "Pin")
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            if (onAddToShelf != null) {
                IconButton(onClick = onAddToShelf) {
                    Icon(Icons.Default.Add, contentDescription = "Add to shelf")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                }
            }
        }
    )
}

@Composable
private fun SharedMobileLibraryFilterChips(
    filters: LibraryFilters,
    onClearFilters: () -> Unit,
    onRemoveFilters: (LibraryFilters) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filters.fileTypes.isNotEmpty()) {
            item {
                InputChip(
                    selected = true,
                    onClick = { onRemoveFilters(filters.copy(fileTypes = emptySet())) },
                    label = { Text("Types: ${filters.fileTypes.joinToString { it.name }}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        if (filters.sourceFolders.isNotEmpty()) {
            item {
                InputChip(
                    selected = true,
                    onClick = { onRemoveFilters(filters.copy(sourceFolders = emptySet())) },
                    label = { Text("Folders: ${filters.sourceFolders.size}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        if (filters.readStatus != ReadStatusFilter.ALL) {
            item {
                InputChip(
                    selected = true,
                    onClick = { onRemoveFilters(filters.copy(readStatus = ReadStatusFilter.ALL)) },
                    label = { Text("Status: ${filters.readStatus.sharedMobileLabel()}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        if (filters.tagIds.isNotEmpty()) {
            item {
                InputChip(
                    selected = true,
                    onClick = { onRemoveFilters(filters.copy(tagIds = emptySet())) },
                    label = { Text("Tags: ${filters.tagIds.size}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        item {
            InputChip(
                selected = false,
                onClick = onClearFilters,
                label = { Text("Clear") }
            )
        }
    }
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
    selectedShelfIds: Set<String> = emptySet(),
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
                selected = shelf.id in selectedShelfIds,
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
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
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
            if (secondaryActionLabel != null) {
                Button(onClick = onSecondaryAction) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(secondaryActionLabel)
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

private fun List<BookItem>.filteredSharedMobileBooks(query: String): List<BookItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { book ->
        book.displayName.contains(normalized, ignoreCase = true) ||
            book.title?.contains(normalized, ignoreCase = true) == true ||
            book.author?.contains(normalized, ignoreCase = true) == true ||
            book.sourceFolder?.contains(normalized, ignoreCase = true) == true ||
            book.tags.any { it.name.contains(normalized, ignoreCase = true) }
    }
}

private fun SortOrder.sharedMobileLabel(): String {
    return when (this) {
        SortOrder.RECENT -> "Recent"
        SortOrder.DATE_ADDED_NEWEST -> "Newest"
        SortOrder.DATE_ADDED_OLDEST -> "Oldest"
        SortOrder.TITLE_ASC -> "Title A-Z"
        SortOrder.AUTHOR_ASC -> "Author A-Z"
        SortOrder.PERCENT_ASC -> "Progress low"
        SortOrder.PERCENT_DESC -> "Progress high"
        SortOrder.SIZE_ASC -> "Size small"
        SortOrder.SIZE_DESC -> "Size large"
    }
}

private fun ReadStatusFilter.sharedMobileLabel(): String {
    return when (this) {
        ReadStatusFilter.ALL -> "All"
        ReadStatusFilter.UNREAD -> "Unread"
        ReadStatusFilter.IN_PROGRESS -> "In progress"
        ReadStatusFilter.COMPLETED -> "Completed"
    }
}
