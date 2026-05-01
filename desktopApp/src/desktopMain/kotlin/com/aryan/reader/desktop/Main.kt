package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.sampleReaderScreenState
import com.aryan.reader.shared.withImportedFiles
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.SharedHomeScreen
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedShelvesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Episteme",
    ) {
        EpistemeDesktopApp()
    }
}

private enum class DesktopTab { HOME, LIBRARY, SHELVES, READER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpistemeDesktopApp() {
    val libraryProjector = remember { SharedLibraryStateProjector() }
    val readerEngine = remember { ReaderEngine() }
    var state by remember {
        val initialState = sampleReaderScreenState()
        mutableStateOf(
            libraryProjector.project(
                SharedLibraryProjectionInput(
                    state = initialState,
                    booksFromStore = initialState.rawLibraryBooks,
                    shelfRecords = emptyList(),
                    shelfRefs = emptyList(),
                    tags = initialState.allTags
                )
            )
        )
    }
    var selectedTab by remember { mutableStateOf(DesktopTab.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(NonReaderLibraryTab.BOOKS) }
    var activeReaderBookId by remember { mutableStateOf<String?>(null) }
    var readerSession by remember { mutableStateOf(readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())) }
    var activePdfDocument by remember { mutableStateOf<DesktopPdfDocument?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun projectState(next: SharedReaderScreenState): SharedReaderScreenState {
        return libraryProjector.project(
            SharedLibraryProjectionInput(
                state = next,
                booksFromStore = next.rawLibraryBooks,
                shelfRecords = emptyList(),
                shelfRefs = emptyList(),
                tags = next.allTags
            )
        )
    }

    fun updateState(next: SharedReaderScreenState) {
        state = projectState(next)
    }

    fun importFiles(files: List<ImportedBookFile>) {
        updateState(state.withImportedFiles(files))
    }

    fun openReader(book: BookItem) {
        if (book.type == FileType.PDF) {
            val path = book.path
            if (path.isNullOrBlank()) {
                updateState(state.withBanner("This PDF does not have a local path.", isError = true))
                return
            }
            activePdfDocument?.close()
            activePdfDocument = null
            val pdf = runCatching {
                DesktopPdfium.load(File(path))
            }.getOrElse { error ->
                updateState(state.withBanner("Could not open PDF: ${error.message ?: "unknown error"}", isError = true))
                return
            }

            activePdfDocument = pdf
            activeReaderBookId = book.id
            selectedTab = DesktopTab.READER
            return
        }

        if (book.type != FileType.EPUB) {
            updateState(state.withBanner("${book.type.name} reader support comes later. EPUB and PDF are available on desktop."))
            return
        }

        val loadedBook = runCatching {
            val path = book.path
            if (path.isNullOrBlank()) {
                SampleReaderBooks.desktopWelcomeBook()
            } else {
                DesktopEpubLoader.load(File(path))
            }
        }.getOrElse { error ->
            updateState(state.withBanner("Could not open EPUB: ${error.message ?: "unknown error"}", isError = true))
            return
        }

        activePdfDocument?.close()
        activePdfDocument = null
        readerSession = readerEngine.createSession(loadedBook, readerSession.reader.settings)
        activeReaderBookId = book.id
        selectedTab = DesktopTab.READER
    }

    fun importAndOpenEpub() {
        val file = chooseEpubFile() ?: return
        importFiles(listOf(file.toImportedBookFile()))
        openReader(
            BookItem(
                id = file.absolutePath,
                path = file.absolutePath,
                type = FileType.EPUB,
                displayName = file.name,
                timestamp = System.currentTimeMillis(),
                title = file.nameWithoutExtension,
                fileSize = file.length()
            )
        )
    }

    fun importAndOpenPdf() {
        val file = choosePdfFile() ?: return
        importFiles(listOf(file.toImportedBookFile()))
        openReader(
            BookItem(
                id = file.absolutePath,
                path = file.absolutePath,
                type = FileType.PDF,
                displayName = file.name,
                timestamp = System.currentTimeMillis(),
                title = file.nameWithoutExtension,
                fileSize = file.length()
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            activePdfDocument?.close()
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { banner ->
            snackbarHostState.showSnackbar(banner.message)
            updateState(state.reduce(AppAction.BannerDismissed))
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF006C4C),
            secondary = Color(0xFF705D49),
            tertiary = Color(0xFF9C4146),
            surface = Color(0xFFFCFCF8),
            surfaceVariant = Color(0xFFE5E8DE)
        )
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.HOME,
                        onClick = { selectedTab = DesktopTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.LIBRARY,
                        onClick = { selectedTab = DesktopTab.LIBRARY },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text("Library") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.SHELVES,
                        onClick = { selectedTab = DesktopTab.SHELVES },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("Shelves") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == DesktopTab.READER,
                        onClick = { selectedTab = DesktopTab.READER },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        label = { Text("Reader") }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            importFiles(chooseFiles())
                        }
                    ) {
                        Icon(Icons.Default.ImportExport, contentDescription = "Import files")
                    }
                    IconButton(
                        onClick = {
                            updateState(state.reduce(AppAction.BannerShown(BannerMessage("Cloud sync is Android-only for now. Desktop sync will need a separate backend adapter."))))
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        DesktopTab.HOME -> HomeScreen(
                            state = state,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = { updateState(state.removeSelectedBooks()) }
                        )

                        DesktopTab.LIBRARY -> LibraryScreen(
                            state = state,
                            selectedLibraryTab = selectedLibraryTab,
                            onLibraryTabChange = { selectedLibraryTab = it },
                            onStateChange = ::updateState,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = { updateState(state.removeSelectedBooks()) }
                        )

                        DesktopTab.SHELVES -> ShelvesScreen(
                            shelves = state.shelves,
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            selectedBookIds = state.selectedBookIds
                        )

                        DesktopTab.READER -> {
                            val pdfDocument = activePdfDocument
                            if (pdfDocument != null) {
                                PdfReaderScreen(
                                    document = pdfDocument,
                                    onOpenPdf = ::importAndOpenPdf,
                                    onOpenEpub = ::importAndOpenEpub,
                                    onProgressChange = { progress ->
                                        activeReaderBookId?.let { bookId ->
                                            updateState(
                                                state.copy(rawLibraryBooks = state.rawLibraryBooks.map { book ->
                                                    if (book.id == bookId) {
                                                        book.copy(progressPercentage = progress, timestamp = System.currentTimeMillis())
                                                    } else {
                                                        book
                                                    }
                                                })
                                            )
                                        }
                                    }
                                )
                            } else {
                                ReaderScreen(
                                    session = readerSession,
                                    readerEngine = readerEngine,
                                    onSessionChange = { updated ->
                                        readerSession = updated
                                        activeReaderBookId?.let { bookId ->
                                            updateState(
                                                state.copy(rawLibraryBooks = state.rawLibraryBooks.map { book ->
                                                    if (book.id == bookId) {
                                                        book.copy(progressPercentage = updated.reader.progress, timestamp = System.currentTimeMillis())
                                                    } else {
                                                        book
                                                    }
                                                })
                                            )
                                        }
                                    },
                                    onOpenEpub = ::importAndOpenEpub,
                                    onOpenPdf = ::importAndOpenPdf
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: SharedReaderScreenState,
    onImportBooks: () -> Unit,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit
) {
    SharedHomeScreen(
        state = state,
        onImportBooks = onImportBooks,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected
    )
}

@Composable
private fun LibraryScreen(
    state: SharedReaderScreenState,
    selectedLibraryTab: NonReaderLibraryTab,
    onLibraryTabChange: (NonReaderLibraryTab) -> Unit,
    onStateChange: (SharedReaderScreenState) -> Unit,
    onImportBooks: () -> Unit,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit
) {
    SharedLibraryScreen(
        state = state,
        selectedTab = selectedLibraryTab,
        onTabChange = onLibraryTabChange,
        onStateChange = onStateChange,
        onImportBooks = onImportBooks,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected
    )
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit
) {
    SharedShelvesScreen(
        shelves = shelves,
        selectedBookIds = selectedBookIds,
        onOpenBook = onRead,
        onToggleSelection = onSelect
    )
}

@Composable
private fun PdfReaderScreen(
    document: DesktopPdfDocument,
    onOpenPdf: () -> Unit,
    onOpenEpub: () -> Unit,
    onProgressChange: (Float) -> Unit
) {
    var pageIndex by remember(document.path) { mutableStateOf(0) }
    var scale by remember(document.path) { mutableStateOf(1.35f) }
    var searchQuery by remember(document.path) { mutableStateOf("") }
    var activeSearchIndex by remember(document.path) { mutableStateOf(-1) }
    var renderedPage by remember(document.path) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderError by remember(document.path) { mutableStateOf<String?>(null) }
    var isRendering by remember(document.path) { mutableStateOf(false) }

    val searchResults = remember(document.path, searchQuery) {
        val normalized = searchQuery.trim()
        if (normalized.isBlank()) {
            emptyList()
        } else {
            document.textPages.mapIndexedNotNull { index, text ->
                val matchIndex = text.indexOf(normalized, ignoreCase = true)
                if (matchIndex < 0) {
                    null
                } else {
                    ReaderPdfSearchResult(index, text.previewAround(matchIndex, normalized.length))
                }
            }
        }
    }

    fun goToPage(target: Int) {
        pageIndex = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
    }

    fun goToSearchResult(targetIndex: Int) {
        if (searchResults.isEmpty()) return
        val normalizedIndex = when {
            targetIndex < 0 -> searchResults.lastIndex
            targetIndex > searchResults.lastIndex -> 0
            else -> targetIndex
        }
        activeSearchIndex = normalizedIndex
        goToPage(searchResults[normalizedIndex].pageIndex)
    }

    LaunchedEffect(document.path, pageIndex) {
        onProgressChange(((pageIndex + 1).toFloat() / document.pageCount.coerceAtLeast(1)) * 100f)
    }

    LaunchedEffect(document.path, pageIndex, scale) {
        isRendering = true
        renderError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                DesktopPdfium.renderPage(document, pageIndex, scale)
            }
        }
        renderedPage = result.getOrNull()
        renderError = result.exceptionOrNull()?.message
            ?: if (renderedPage == null) "Failed to render page." else null
        isRendering = false
    }

    ScreenScaffold(
        title = document.title,
        subtitle = "PDF - Page ${pageIndex + 1} of ${document.pageCount}",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenPdf) {
                    Text("Open PDF")
                }
                TextButton(onClick = onOpenEpub) {
                    Text("Open EPUB")
                }
                Text("${(((pageIndex + 1).toFloat() / document.pageCount.coerceAtLeast(1)) * 100f).toInt()}%")
            }
        }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { goToPage(pageIndex - 1) }, enabled = pageIndex > 0) {
                                Text("Prev")
                            }
                            TextButton(onClick = { goToPage(pageIndex + 1) }, enabled = pageIndex < document.pageCount - 1) {
                                Text("Next")
                            }
                        }
                    }
                    item {
                        Text("Zoom", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.65f..3.0f
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                activeSearchIndex = -1
                            },
                            label = { Text("Find in PDF") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotBlank()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (searchResults.isEmpty()) "No matches" else "${(activeSearchIndex + 1).coerceAtLeast(0)} of ${searchResults.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { goToSearchResult(activeSearchIndex - 1) }, enabled = searchResults.isNotEmpty()) {
                                    Text("Prev")
                                }
                                TextButton(onClick = { goToSearchResult(activeSearchIndex + 1) }, enabled = searchResults.isNotEmpty()) {
                                    Text("Next")
                                }
                            }
                        }
                    }
                    items(searchResults, key = { "${it.pageIndex}_${it.preview}" }) { result ->
                        Surface(
                            color = if (result.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                activeSearchIndex = searchResults.indexOf(result)
                                goToPage(result.pageIndex)
                            }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Page ${result.pageIndex + 1}", fontWeight = FontWeight.SemiBold)
                                Text(result.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFE8E5DC), RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    isRendering -> CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    renderError != null -> Text(renderError ?: "Failed to render page.", color = MaterialTheme.colorScheme.error)
                    renderedPage != null -> Image(
                        bitmap = renderedPage!!.image,
                        contentDescription = "PDF page ${pageIndex + 1}"
                    )
                }
            }
        }
    }
}

private data class ReaderPdfSearchResult(
    val pageIndex: Int,
    val preview: String
)

@Composable
private fun ReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onOpenEpub: () -> Unit,
    onOpenPdf: () -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val background = if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)
    val foreground = if (settings.darkMode) Color(0xFFE7E3D8) else Color(0xFF24231F)
    val searchHighlight = if (settings.darkMode) Color(0xFF675A00) else Color(0xFFFFE36E)
    val textAlign = settings.textAlign.toComposeTextAlign()
    val fontFamily = settings.fontFamily.toComposeFontFamily()
    val verticalListState = rememberLazyListState()

    LaunchedEffect(settings.readingMode, page?.chapterIndex) {
        if (settings.readingMode == ReaderReadingMode.VERTICAL && page != null) {
            verticalListState.animateScrollToItem(page.chapterIndex)
        }
    }

    ScreenScaffold(
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenEpub) {
                    Text("Open EPUB")
                }
                TextButton(onClick = onOpenPdf) {
                    Text("Open PDF")
                }
                Text("${readerState.progress.toInt()}%")
                IconButton(onClick = { onSessionChange(readerEngine.toggleBookmark(session)) }) {
                    Icon(
                        if (session.currentBookmark == null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                        contentDescription = "Bookmark"
                    )
                }
                TextButton(
                    onClick = {
                        onSessionChange(session.copy(reader = readerState.copy(settings = settings.copy(darkMode = !settings.darkMode))))
                    }
                ) {
                    Text(if (settings.darkMode) "Light" else "Dark")
                }
            }
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                            onSessionChange(readerEngine.next(session))
                            true
                        }

                        event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
                            onSessionChange(readerEngine.previous(session))
                            true
                        }

                        event.key == Key.MoveHome -> {
                            onSessionChange(readerEngine.goToPage(session, 0))
                            true
                        }

                        event.key == Key.MoveEnd -> {
                            onSessionChange(readerEngine.goToPage(session, readerState.pages.lastIndex))
                            true
                        }

                        event.isCtrlPressed && event.key == Key.G -> {
                            onSessionChange(readerEngine.nextSearchResult(session))
                            true
                        }

                        else -> false
                    }
                }
                .focusable()
        ) {
            ReaderSidebar(
                session = session,
                onSearchChange = { onSessionChange(readerEngine.search(session, it)) },
                onPreviousSearchResult = { onSessionChange(readerEngine.previousSearchResult(session)) },
                onNextSearchResult = { onSessionChange(readerEngine.nextSearchResult(session)) },
                onGoToChapter = { onSessionChange(readerEngine.goToChapter(session, it)) },
                onGoToPage = { onSessionChange(readerEngine.goToPage(session, it)) }
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderSettingsBar(
                    session = session,
                    readerEngine = readerEngine,
                    onSessionChange = onSessionChange
                )

                Surface(
                    color = background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    SelectionContainer {
                        if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                            LazyColumn(
                                state = verticalListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(PaddingValues(settings.margin.dp)),
                                verticalArrangement = Arrangement.spacedBy(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(readerState.book.chapters.indices.toList()) { index ->
                                    val chapter = readerState.book.chapters[index]
                                    Column(
                                        modifier = Modifier
                                            .width(settings.pageWidth.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            chapter.title,
                                            color = foreground,
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (chapter.semanticBlocks.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                chapter.semanticBlocks.forEach { block ->
                                                    SemanticBlockView(
                                                        block = block,
                                                        foreground = foreground,
                                                        searchQuery = session.searchQuery,
                                                        searchHighlight = searchHighlight,
                                                        fallbackTextAlign = textAlign,
                                                        fallbackFontFamily = fontFamily,
                                                        settings = settings
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = chapter.plainText.highlightQuery(session.searchQuery, searchHighlight),
                                                color = foreground,
                                                textAlign = textAlign,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = settings.fontSize.sp,
                                                    lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                                                    fontFamily = fontFamily
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(PaddingValues(settings.margin.dp)),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = page?.text.orEmpty().highlightQuery(session.searchQuery, searchHighlight),
                                    color = foreground,
                                    textAlign = textAlign,
                                    modifier = Modifier.width(settings.pageWidth.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = settings.fontSize.sp,
                                        lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                                        fontFamily = fontFamily
                                    )
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = if (readerState.pages.size <= 1) 0f else readerState.currentPageIndex.toFloat() / readerState.pages.lastIndex,
                        onValueChange = { progress -> onSessionChange(readerEngine.goToProgress(session, progress)) },
                        enabled = readerState.pages.size > 1
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = readerState.canGoPrevious,
                            onClick = { onSessionChange(readerEngine.previous(session)) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Text("Previous")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                                "Continuous mode - page ${readerState.currentPageIndex + 1} of ${readerState.pages.size}"
                            } else {
                                "Page ${readerState.currentPageIndex + 1} of ${readerState.pages.size}"
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = readerState.canGoNext,
                            onClick = { onSessionChange(readerEngine.next(session)) }
                        ) {
                            Text("Next")
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsBar(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit
) {
    val settings = session.reader.settings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = settings.readingMode == ReaderReadingMode.PAGINATED,
                onClick = {
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(readingMode = ReaderReadingMode.PAGINATED)))
                },
                label = { Text("Pages") }
            )
            FilterChip(
                selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                onClick = {
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                },
                label = { Text("Vertical") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.START,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.START))) },
                label = { Text("Left") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY))) },
                label = { Text("Justify") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.CENTER))) },
                label = { Text("Center") }
            )
            listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                FilterChip(
                    selected = settings.fontFamily == family,
                    onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(fontFamily = family))) },
                    label = { Text(family) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Font ${settings.fontSize}")
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(fontSize = value.toInt())))
                },
                valueRange = 14f..30f,
                modifier = Modifier.width(140.dp)
            )
            Text("Margin ${settings.margin}")
            Slider(
                value = settings.margin.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(margin = value.toInt())))
                },
                valueRange = 16f..112f,
                modifier = Modifier.width(140.dp)
            )
            Text("Spacing ${String.format("%.2f", settings.lineSpacing)}")
            Slider(
                value = settings.lineSpacing,
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(lineSpacing = value)))
                },
                valueRange = 1.1f..2.1f,
                modifier = Modifier.width(140.dp)
            )
            Text("Width ${settings.pageWidth}")
            Slider(
                value = settings.pageWidth.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(pageWidth = value.toInt())))
                },
                valueRange = 520f..1100f,
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

private fun String.highlightQuery(query: String, color: Color): AnnotatedString {
    val normalized = query.trim()
    if (normalized.length < 2) return AnnotatedString(this)

    return buildAnnotatedString {
        append(this@highlightQuery)
        var startIndex = 0
        while (startIndex < this@highlightQuery.length) {
            val index = this@highlightQuery.indexOf(normalized, startIndex, ignoreCase = true)
            if (index < 0) break
            addStyle(
                style = SpanStyle(background = color),
                start = index,
                end = index + normalized.length
            )
            startIndex = index + normalized.length
        }
    }
}

@Composable
private fun SemanticBlockView(
    block: SemanticBlock,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: com.aryan.reader.shared.reader.ReaderSettings
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(
            start = block.style.blockStyle.margin.left.safeDp(),
            top = block.style.blockStyle.margin.top.safeDp(),
            end = block.style.blockStyle.margin.right.safeDp(),
            bottom = block.style.blockStyle.margin.bottom.safeDp()
        )
        .then(
            if (block.style.blockStyle.backgroundColor.isSpecified) {
                Modifier.background(block.style.blockStyle.backgroundColor, RoundedCornerShape(4.dp))
            } else {
                Modifier
            }
        )
        .padding(
            start = block.style.blockStyle.padding.left.safeDp(),
            top = block.style.blockStyle.padding.top.safeDp(),
            end = block.style.blockStyle.padding.right.safeDp(),
            bottom = block.style.blockStyle.padding.bottom.safeDp()
        )

    when (block) {
        is SemanticHeader -> {
            Text(
                text = block.toAnnotatedString(searchQuery, searchHighlight),
                color = foreground,
                modifier = modifier,
                textAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (settings.fontSize * headerScale(block.level)).sp,
                    lineHeight = (settings.fontSize * headerScale(block.level) * settings.lineSpacing).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fallbackFontFamily
                )
            )
        }

        is SemanticParagraph -> SemanticTextView(block, modifier, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
        is SemanticListItem -> SemanticTextView(block, modifier, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
        is SemanticTextBlock -> SemanticTextView(block, modifier, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)

        is SemanticList -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (block.isOrdered) "${index + 1}." else "•", color = foreground)
                        SemanticTextView(
                            block = item,
                            modifier = Modifier.weight(1f),
                            foreground = foreground,
                            searchQuery = searchQuery,
                            searchHighlight = searchHighlight,
                            fallbackTextAlign = fallbackTextAlign,
                            fallbackFontFamily = fallbackFontFamily,
                            settings = settings
                        )
                    }
                }
            }
        }

        is SemanticFlexContainer -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.children.forEach {
                    SemanticBlockView(it, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
                }
            }
        }

        is SemanticWrappingBlock -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SemanticBlockView(block.floatedImage, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
                block.paragraphsToWrap.forEach {
                    SemanticBlockView(it, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
                }
            }
        }

        is SemanticTable -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cell ->
                            Column(modifier = Modifier.weight(cell.colspan.toFloat().coerceAtLeast(1f))) {
                                cell.content.forEach {
                                    SemanticBlockView(it, foreground, searchQuery, searchHighlight, fallbackTextAlign, fallbackFontFamily, settings)
                                }
                            }
                        }
                    }
                }
            }
        }

        is SemanticImage -> {
            Text(
                text = block.altText?.takeIf { it.isNotBlank() } ?: block.path.substringAfterLast('/').substringAfterLast('\\'),
                color = foreground.copy(alpha = 0.7f),
                modifier = modifier,
                style = MaterialTheme.typography.bodySmall
            )
        }

        is SemanticMath -> {
            Text(
                text = block.altText ?: "Equation",
                color = foreground,
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is SemanticSpacer -> Spacer(modifier.height(if (block.isExplicitLineBreak) 8.dp else 16.dp))
    }
}

@Composable
private fun SemanticTextView(
    block: SemanticTextBlock,
    modifier: Modifier,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: com.aryan.reader.shared.reader.ReaderSettings
) {
    Text(
        text = block.toAnnotatedString(searchQuery, searchHighlight),
        color = foreground,
        modifier = modifier,
        textAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineSpacing).sp,
            fontFamily = fallbackFontFamily
        )
    )
}

private fun SemanticTextBlock.toAnnotatedString(query: String, highlightColor: Color): AnnotatedString {
    val normalized = query.trim()
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                addStyle(span.style.spanStyle, start, end)
            }
        }
        if (normalized.length >= 2) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(normalized, startIndex, ignoreCase = true)
                if (index < 0) break
                addStyle(SpanStyle(background = highlightColor), index, index + normalized.length)
                startIndex = index + normalized.length
            }
        }
    }
}

private fun headerScale(level: Int): Float {
    return when (level) {
        1 -> 1.5f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        else -> 1f
    }
}

private fun Dp.safeDp(): Dp = if (isSpecified) this else 0.dp

private fun SharedReaderTextAlign.toComposeTextAlign(): TextAlign {
    return when (this) {
        SharedReaderTextAlign.START -> TextAlign.Start
        SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
        SharedReaderTextAlign.CENTER -> TextAlign.Center
    }
}

private fun String.toComposeFontFamily(): FontFamily {
    return when (this) {
        "Serif" -> FontFamily.Serif
        "Sans" -> FontFamily.SansSerif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

@Composable
private fun ReaderSidebar(
    session: ReaderSessionState,
    onSearchChange: (String) -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onGoToChapter: (Int) -> Unit,
    onGoToPage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(session.reader.book.chapters.indices.toList()) { index ->
                val chapter = session.reader.book.chapters[index]
                val selected = session.reader.currentPage?.chapterIndex == index
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onGoToChapter(index) }
                ) {
                    Text(
                        chapter.title,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (session.bookmarks.isEmpty()) {
                item {
                    Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(session.bookmarks, key = { it.id }) { bookmark ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGoToPage(bookmark.pageIndex) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(bookmark.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bookmark.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = session.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Find in book") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (session.searchQuery.isNotBlank() && session.searchResults.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${session.activeSearchResultIndex + 1} of ${session.searchResults.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPreviousSearchResult) {
                            Text("Prev")
                        }
                        TextButton(onClick = onNextSearchResult) {
                            Text("Next")
                        }
                    }
                }
            }
            if (session.searchQuery.isNotBlank() && session.searchResults.isEmpty()) {
                item {
                    Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(session.searchResults, key = { "${it.pageIndex}_${it.preview}" }) { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGoToPage(result.pageIndex) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Page ${result.pageIndex + 1} - ${result.chapterTitle}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(result.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenScaffold(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

private fun chooseFiles(): List<ImportedBookFile> {
    val dialog = FileDialog(null as Frame?, "Import books", FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }
    return dialog.files.orEmpty().map { it.toImportedBookFile() }
}

private fun chooseEpubFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open EPUB", FileDialog.LOAD).apply {
        file = "*.epub"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

private fun choosePdfFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open PDF", FileDialog.LOAD).apply {
        file = "*.pdf"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

private fun SharedReaderScreenState.removeSelectedBooks(): SharedReaderScreenState {
    if (selectedBookIds.isEmpty()) return this
    return copy(
        rawLibraryBooks = rawLibraryBooks.filterNot { it.id in selectedBookIds },
        selectedBookIds = emptySet(),
        bannerMessage = BannerMessage("Removed selected books from the desktop library.")
    )
}

private fun SharedReaderScreenState.withBanner(message: String, isError: Boolean = false): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message, isError = isError)))
}

private fun File.toImportedBookFile(): ImportedBookFile {
    return ImportedBookFile(
        name = name,
        uriString = null,
        localPath = absolutePath,
        size = length()
    )
}

private fun String.previewAround(index: Int, queryLength: Int): String {
    val start = (index - 70).coerceAtLeast(0)
    val end = (index + queryLength + 100).coerceAtMost(length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < length) "..." else ""
    return prefix + substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
}
