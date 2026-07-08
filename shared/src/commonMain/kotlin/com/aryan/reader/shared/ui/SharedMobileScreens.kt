package com.aryan.reader.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.sortBooks
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier
) {
    val initialPage = book.lastPageIndex?.coerceAtLeast(0) ?: 0
    var readerState by remember(book.id) {
        mutableStateOf(SharedPdfReaderState.initial(pageCount = 1, initialPageIndex = initialPage))
    }
    var showChrome by remember(book.id) { mutableStateOf(true) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pageRender = rememberSharedMobilePdfPageRender(book, readerState.pageIndex)
    val pageCount = pageRender.pageCount.coerceAtLeast(1)
    var canvasSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    val activeStroke = remember(book.id, readerState.pageIndex) { mutableStateListOf<PdfPagePoint>() }

    fun dispatch(action: SharedPdfReaderAction) {
        readerState = readerState.reduce(action)
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

    fun finishInkStroke() {
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
                pageIndex = readerState.pageIndex,
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
                pageIndex = readerState.pageIndex,
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SharedMobilePdfReaderDrawer(
                book = book,
                state = readerState,
                onGoToPage = { page ->
                    dispatch(SharedPdfReaderAction.GoToPage(page))
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
                        pageCount = pageCount,
                        displayMode = readerState.displayMode,
                        isBookmarked = readerState.bookmarks.any { it.pageIndex == readerState.pageIndex },
                        onBack = onBack,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onToggleBookmark = {
                            dispatch(SharedPdfReaderAction.BookmarkToggled(readerState.pageIndex, createdAt = currentTimestamp()))
                        },
                        onToggleDisplayMode = { dispatch(SharedPdfReaderAction.DisplayModeToggled) },
                        onBridgeInfo = { onNativePdfBridgeNeeded(book) }
                    )
                }
            },
            bottomBar = {
                if (showChrome) {
                    SharedMobilePdfReaderBottomBar(
                        state = readerState,
                        pageCount = pageCount,
                        onPreviousPage = { dispatch(SharedPdfReaderAction.PreviousPage) },
                        onNextPage = { dispatch(SharedPdfReaderAction.NextPage) },
                        onPageSliderChange = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202124))
                    .padding(padding)
                    .clickable { showChrome = !showChrome },
                contentAlignment = Alignment.Center
            ) {
                if (readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    SharedMobilePdfVerticalPages(
                        book = book,
                        state = readerState,
                        pageCount = pageCount,
                        activeStroke = activeStroke,
                        onVisiblePageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = ::finishInkStroke,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobilePdfPageSurface(
                        book = book,
                        pageIndex = readerState.pageIndex,
                        pageCount = pageCount,
                        pageRender = pageRender,
                        annotations = readerState.annotations.filter { it.pageIndex == readerState.pageIndex },
                        activeStroke = activeStroke,
                        selectedTool = readerState.selectedTool,
                        selectedColorArgb = readerState.selectedColorArgb,
                        strokeWidth = readerState.strokeWidth,
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = ::finishInkStroke,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfReaderTopBar(
    title: String,
    pageIndex: Int,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onBridgeInfo: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Page ${pageIndex + 1} of ${pageCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelSmall,
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
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Contents")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark page"
                )
            }
            IconButton(onClick = onToggleDisplayMode) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = if (displayMode == PdfDisplayMode.PAGINATION) "Switch to vertical scroll" else "Switch to paginated reading"
                )
            }
            IconButton(onClick = onBridgeInfo) {
                Icon(Icons.Default.MoreVert, contentDescription = "PDF options")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun SharedMobilePdfReaderBottomBar(
    state: SharedPdfReaderState,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPageSliderChange: (Int) -> Unit,
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
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onPreviousPage, enabled = state.canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                }
                Slider(
                    value = state.pageIndex.toFloat(),
                    onValueChange = { onPageSliderChange(it.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))) },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                    steps = (pageCount - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextPage, enabled = state.canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                }
            }
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
                modifier = Modifier.fillMaxWidth()
            )
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
    ModalDrawerSheet {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.cardTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Page ${state.pageIndex + 1} of ${state.pageCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            NavigationDrawerItem(
                icon = { Icon(if (state.bookmarks.any { it.pageIndex == state.pageIndex }) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) },
                label = { Text(if (state.bookmarks.any { it.pageIndex == state.pageIndex }) "Remove bookmark" else "Bookmark this page") },
                selected = false,
                onClick = onToggleBookmark,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Pages",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
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
            HorizontalDivider()
            Text(
                "${state.annotations.size} annotations",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfVerticalPages(
    book: BookItem,
    state: SharedPdfReaderState,
    pageCount: Int,
    activeStroke: List<PdfPagePoint>,
    onVisiblePageChanged: (Int) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex.coerceIn(0, pageCount - 1))
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onVisiblePageChanged(listState.firstVisibleItemIndex.coerceIn(0, pageCount - 1))
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pageCount) { page ->
            val render = rememberSharedMobilePdfPageRender(book, page)
            SharedMobilePdfPageSurface(
                book = book,
                pageIndex = page,
                pageCount = pageCount,
                pageRender = render,
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

@Composable
private fun SharedMobilePdfPageSurface(
    book: BookItem,
    pageIndex: Int,
    pageCount: Int,
    pageRender: SharedMobilePdfPageRender,
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    selectedTool: PdfInkTool,
    selectedColorArgb: Int,
    strokeWidth: Float,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localCanvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    Surface(
        color = Color.White,
        contentColor = Color.Black,
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio(0.72f)
            .clipToBounds()
            .onSizeChanged {
                localCanvasSize = it
                onCanvasSizeChanged(it)
            }
            .pointerInput(selectedTool, localCanvasSize, pageIndex) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (selectedTool != PdfInkTool.NONE && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                            (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                            (activeStroke as? MutableList<PdfPagePoint>)?.add(offset.toSharedMobilePdfPoint(localCanvasSize))
                        }
                    },
                    onDrag = { change, _ ->
                        if (selectedTool != PdfInkTool.NONE && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                            (activeStroke as? MutableList<PdfPagePoint>)?.add(change.position.toSharedMobilePdfPoint(localCanvasSize))
                        }
                    },
                    onDragEnd = onFinishInkStroke,
                    onDragCancel = { (activeStroke as? MutableList<PdfPagePoint>)?.clear() }
                )
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (pageRender.bitmap != null) {
                Image(
                    bitmap = pageRender.bitmap,
                    contentDescription = book.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                SharedMobilePdfPagePlaceholder(
                    book = book,
                    pageIndex = pageIndex,
                    errorMessage = pageRender.errorMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SharedPdfAnnotationOverlay(
                annotations = annotations,
                activeStroke = activeStroke,
                canvasSize = localCanvasSize,
                activeTool = selectedTool,
                activeStrokeColorArgb = selectedColorArgb,
                activeStrokeWidth = strokeWidth
            )
            SharedPdfPageNumberOverlay(
                pageIndex = pageIndex,
                pageCount = pageCount,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
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
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onDrawerClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToFolderSync: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onOpenTab: (BookItem) -> Unit = onOpenBook,
    onCloseTab: (BookItem) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val isContextualMode = selectedIds.isNotEmpty()
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
            if (isContextualMode) {
                SharedMobileContextualTopBar(
                    selectedCount = selectedIds.size,
                    onClose = onClearSelection,
                    onSelectAll = onSelectAll,
                    onPin = {
                        state.recentBooks.filter { it.id in selectedIds }.forEach(onTogglePinned)
                    }
                )
            } else {
                SharedMobileHomeTopBar(
                    onDrawerClick = onDrawerClick,
                    onSearchClick = onSearchClick,
                    isSyncEnabled = state.isSyncEnabled || state.syncedFolders.any { it.localSyncEnabled },
                    onRefresh = onRefresh,
                    onSettingsClick = onSettingsClick,
                    onMoreClick = onMoreClick
                )
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
                    onAction = onImportBooks,
                    secondaryActionLabel = if (model.isLibraryEmpty) "Sync folder" else null,
                    onSecondaryAction = onNavigateToFolderSync,
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
                                onOpenTab = onOpenTab,
                                onCloseTab = onCloseTab,
                                onCloseAllTabs = onCloseAllTabs
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
                                onOpenBook = onOpenBook,
                                onLongPressBook = onLongPressBook,
                                onTogglePinned = onTogglePinned
                            )
                        }
                    }

                    item(key = "recent") {
                        SharedMobileBookGridSection(
                            title = "Recent files",
                            books = model.recentBooks,
                            selectedBookIds = selectedIds,
                            pinnedBookIds = state.pinnedHomeBookIds,
                            onOpenBook = onOpenBook,
                            onLongPressBook = onLongPressBook,
                            onTogglePinned = onTogglePinned
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
                    Button(onClick = onImportBooks) { Text("Select file") }
                    Button(onClick = onNavigateToFolderSync) { Text("Sync folder") }
                }
            }
        }
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
    val searchedBooks = remember(state.libraryBooks, state.searchQuery) {
        state.libraryBooks.filteredSharedMobileBooks(state.searchQuery)
    }
    val sortedSearchedBooks = remember(searchedBooks, state.sortOrder) { sortBooks(searchedBooks, state.sortOrder) }

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
                        }
                    )

                    isShelfContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedShelves.size,
                        onClose = onClearSelection,
                        onSelectAll = {},
                        onPin = {}
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
                        onFilterClick = onFilterClick,
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
                        onClick = onNewShelfClick
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
    onPin: () -> Unit
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
            IconButton(onClick = {}) {
                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
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
