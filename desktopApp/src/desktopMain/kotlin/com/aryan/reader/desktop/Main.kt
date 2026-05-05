package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.withImportedFiles
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.SharedTextBookFactory
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedAppShell
import com.aryan.reader.shared.ui.SharedAppTab
import com.aryan.reader.shared.ui.SharedBookEditDialog
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedConfirmDialog
import com.aryan.reader.shared.ui.SharedHomeScreen
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedPdfAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfAnnotationToolDock
import com.aryan.reader.shared.ui.SharedReaderScreen
import com.aryan.reader.shared.ui.SharedScreenScaffold
import com.aryan.reader.shared.ui.SharedShelvesScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.ui.pageBoundsFromSharedPdfPoint
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import dev.datlag.kcef.KCEF
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.abs
import kotlin.math.max

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Episteme",
    ) {
        EpistemeDesktopApp()
    }
}

private data class DesktopWebViewRuntimeState(
    val initialized: Boolean = false,
    val restartRequired: Boolean = false,
    val downloadProgress: Float = -1f,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpistemeDesktopApp() {
    val libraryProjector = remember { SharedLibraryStateProjector() }
    val readerEngine = remember { ReaderEngine() }
    val libraryDatabase = remember { DesktopLibraryDatabase() }
    val initialLibrarySnapshot = remember { libraryDatabase.load() }
    val scope = rememberCoroutineScope()
    var webViewRuntimeState by remember { mutableStateOf(DesktopWebViewRuntimeState()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            KCEF.init(
                builder = {
                    installDir(File("kcef-bundle"))
                    progress {
                        onDownloading {
                            webViewRuntimeState = webViewRuntimeState.copy(downloadProgress = max(it, 0f))
                        }
                        onInitialized {
                            webViewRuntimeState = webViewRuntimeState.copy(initialized = true, errorMessage = null)
                        }
                    }
                    settings {
                        cachePath = File("cache").absolutePath
                    }
                },
                onError = { error ->
                    webViewRuntimeState = webViewRuntimeState.copy(errorMessage = error?.message ?: error.toString())
                },
                onRestartRequired = {
                    webViewRuntimeState = webViewRuntimeState.copy(restartRequired = true)
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            KCEF.disposeBlocking()
        }
    }

    var shelfRecords by remember { mutableStateOf(initialLibrarySnapshot.shelfRecords) }
    var shelfRefs by remember { mutableStateOf(initialLibrarySnapshot.shelfRefs) }
    var state by remember {
        val initialBooks = initialLibrarySnapshot.books
        val initialTags = initialLibrarySnapshot.tags.ifEmpty { initialBooks.collectTags() }
        val initialState = SharedReaderScreenState(
            rawLibraryBooks = initialBooks,
            recentFilesLimit = 12,
            allTags = initialTags
        )
        mutableStateOf(
            libraryProjector.project(
                SharedLibraryProjectionInput(
                    state = initialState,
                    booksFromStore = initialState.rawLibraryBooks,
                    shelfRecords = shelfRecords,
                    shelfRefs = shelfRefs,
                    tags = initialState.allTags
                )
            )
        )
    }
    var selectedTab by remember { mutableStateOf(SharedAppTab.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(NonReaderLibraryTab.BOOKS) }
    var activeReaderBookId by remember { mutableStateOf<String?>(null) }
    var readerSession by remember { mutableStateOf(readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())) }
    var activePdfDocument by remember { mutableStateOf<DesktopPdfDocument?>(null) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookEditDialogFor by remember { mutableStateOf<BookItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun projectState(
        next: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs
    ): SharedReaderScreenState {
        return libraryProjector.project(
            SharedLibraryProjectionInput(
                state = next,
                booksFromStore = next.rawLibraryBooks,
                shelfRecords = records,
                shelfRefs = refs,
                tags = next.allTags.ifEmpty { next.rawLibraryBooks.collectTags() }
            )
        )
    }

    fun persistSnapshot(projected: SharedReaderScreenState, records: List<ShelfRecord> = shelfRecords, refs: List<BookShelfRef> = shelfRefs) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                libraryDatabase.save(
                    SharedLibrarySnapshot(
                        books = projected.rawLibraryBooks,
                        shelfRecords = records,
                        shelfRefs = refs,
                        tags = projected.allTags
                    )
                )
            }
        }
    }

    fun replaceLibrary(
        next: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs
    ) {
        shelfRecords = records
        shelfRefs = refs
        val projected = projectState(next, records, refs)
        state = projected
        persistSnapshot(projected, records, refs)
    }

    fun updateState(next: SharedReaderScreenState) {
        val projected = projectState(next)
        state = projected
        persistSnapshot(projected)
    }

    fun importFiles(files: List<ImportedBookFile>) {
        updateState(state.withImportedFiles(files))
    }

    fun removeSelectedBooks() {
        SharedLibraryEditor.removeSelectedBooks(state, shelfRecords, shelfRefs)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun createShelf(name: String) {
        SharedLibraryEditor.createShelf(state, shelfRecords, shelfRefs, name, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun renameShelf(shelf: Shelf, name: String) {
        SharedLibraryEditor.renameShelf(state, shelfRecords, shelfRefs, shelf, name)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun deleteShelf(shelf: Shelf) {
        val result = SharedLibraryEditor.deleteShelf(state, shelfRecords, shelfRefs, shelf)
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
    }

    fun addSelectedBooksToShelf(shelfId: String) {
        SharedLibraryEditor.addSelectedBooksToShelf(state, shelfRecords, shelfRefs, shelfId, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun tagSelectedBooks(tagName: String) {
        SharedLibraryEditor.tagSelectedBooks(state, shelfRecords, shelfRefs, tagName, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun updateBookMetadata(updated: BookItem) {
        val result = SharedLibraryEditor.updateBookMetadata(state, shelfRecords, shelfRefs, updated, System.currentTimeMillis())
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
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
            selectedTab = SharedAppTab.READER
            return
        }

        if (book.type !in setOf(FileType.EPUB, FileType.TXT, FileType.MD, FileType.HTML)) {
            updateState(state.withBanner("${book.type.name} reader support comes later. EPUB, PDF, TXT, MD, and HTML are available on desktop."))
            return
        }

        val loadedBook = runCatching {
            val path = book.path
            if (path.isNullOrBlank()) {
                SampleReaderBooks.desktopWelcomeBook()
            } else if (book.type == FileType.EPUB) {
                DesktopEpubLoader.load(File(path))
            } else {
                val file = File(path)
                val raw = file.readText()
                if (book.type == FileType.HTML) {
                    SharedTextBookFactory.fromHtml(
                        id = file.absolutePath,
                        fileName = file.name,
                        title = book.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                        html = raw,
                        author = book.author
                    )
                } else {
                    SharedTextBookFactory.fromPlainText(
                        id = file.absolutePath,
                        fileName = file.name,
                        title = book.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                        plainText = raw,
                        author = book.author
                    )
                }
            }
        }.getOrElse { error ->
            updateState(state.withBanner("Could not open ${book.type.name}: ${error.message ?: "unknown error"}", isError = true))
            return
        }

        activePdfDocument?.close()
        activePdfDocument = null
        readerSession = readerEngine.createSession(loadedBook, readerSession.reader.settings)
        activeReaderBookId = book.id
        selectedTab = SharedAppTab.READER
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
        SharedAppShell(
            selectedTab = selectedTab,
            snackbarHostState = snackbarHostState,
            onTabSelected = { selectedTab = it },
            onImportFiles = { importFiles(chooseFiles()) },
            onSyncRequested = {
                updateState(state.reduce(AppAction.BannerShown(BannerMessage("Cloud sync is Android-only for now. Desktop sync will need a separate backend adapter."))))
            }
        ) { tab ->
            when (tab) {
                        SharedAppTab.HOME -> HomeScreen(
                            state = state,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true }
                        )

                        SharedAppTab.LIBRARY -> LibraryScreen(
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
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true }
                        )

                        SharedAppTab.SHELVES -> ShelvesScreen(
                            shelves = state.shelves,
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            selectedBookIds = state.selectedBookIds,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it }
                        )

                        SharedAppTab.READER -> {
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
                                    onOpenPdf = ::importAndOpenPdf,
                                    webViewRuntimeState = webViewRuntimeState
                                )
                            }
                        }
            }
        }

        if (showCreateShelfDialog) {
            SharedTextInputDialog(
                title = "Create shelf",
                label = "Shelf name",
                initialValue = "",
                confirmLabel = "Create",
                onDismiss = { showCreateShelfDialog = false },
                onConfirm = { name ->
                    createShelf(name)
                    showCreateShelfDialog = false
                }
            )
        }

        shelfToRename?.let { shelf ->
            SharedTextInputDialog(
                title = "Rename shelf",
                label = "Shelf name",
                initialValue = shelf.name,
                confirmLabel = "Rename",
                onDismiss = { shelfToRename = null },
                onConfirm = { name ->
                    renameShelf(shelf, name)
                    shelfToRename = null
                }
            )
        }

        shelfToDelete?.let { shelf ->
            SharedConfirmDialog(
                title = "Delete shelf",
                body = "Delete \"${shelf.name}\"? Books stay in your library.",
                confirmLabel = "Delete",
                onDismiss = { shelfToDelete = null },
                onConfirm = {
                    deleteShelf(shelf)
                    shelfToDelete = null
                }
            )
        }

        if (showAddToShelfDialog) {
            SharedAddToShelfDialog(
                shelves = state.shelves.filter { it.type == ShelfType.MANUAL && it.id != "unshelved" },
                onDismiss = { showAddToShelfDialog = false },
                onCreateShelf = {
                    showAddToShelfDialog = false
                    showCreateShelfDialog = true
                },
                onShelfSelected = { shelf ->
                    addSelectedBooksToShelf(shelf.id)
                    showAddToShelfDialog = false
                }
            )
        }

        if (showTagSelectionDialog) {
            SharedTextInputDialog(
                title = "Tag selected books",
                label = "Tag name",
                initialValue = state.allTags.firstOrNull()?.name.orEmpty(),
                confirmLabel = "Apply",
                onDismiss = { showTagSelectionDialog = false },
                onConfirm = { name ->
                    tagSelectedBooks(name)
                    showTagSelectionDialog = false
                }
            )
        }

        bookInfoDialogFor?.let { book ->
            SharedBookInfoDialog(
                book = book,
                onDismiss = { bookInfoDialogFor = null },
                onEdit = {
                    bookEditDialogFor = book
                    bookInfoDialogFor = null
                }
            )
        }

        bookEditDialogFor?.let { book ->
            SharedBookEditDialog(
                book = book,
                knownTags = state.allTags,
                onDismiss = { bookEditDialogFor = null },
                onSave = { updated ->
                    updateBookMetadata(updated)
                    bookEditDialogFor = null
                }
            )
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
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit
) {
    SharedHomeScreen(
        state = state,
        onImportBooks = onImportBooks,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf
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
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onCreateShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit
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
        onRemoveSelected = onRemoveSelected,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onCreateShelf = onCreateShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf
    )
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onCreateShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit
) {
    SharedShelvesScreen(
        shelves = shelves,
        selectedBookIds = selectedBookIds,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onCreateShelf = onCreateShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf
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
    val zoomSpec = remember { PdfZoomSpec() }
    var scale by remember(document.path) { mutableStateOf(zoomSpec.default) }
    var searchQuery by remember(document.path) { mutableStateOf("") }
    var activeSearchIndex by remember(document.path) { mutableStateOf(-1) }
    var renderedPage by remember(document.path) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderError by remember(document.path) { mutableStateOf<String?>(null) }
    var isRendering by remember(document.path) { mutableStateOf(false) }
    var renderJob by remember(document.path) { mutableStateOf<Job?>(null) }
    var selectedTool by remember(document.path) { mutableStateOf(PdfInkTool.PEN) }
    var selectedColor by remember(document.path) { mutableStateOf(SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).colorArgb) }
    var strokeWidth by remember(document.path) { mutableStateOf(SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth) }
    var textDraft by remember(document.path) { mutableStateOf("") }
    var pageCanvasSize by remember(document.path) { mutableStateOf(IntSize.Zero) }
    var activeStroke by remember(document.path, pageIndex) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    val annotations = remember(document.path) { mutableStateListOf<SharedPdfAnnotation>() }
    val annotationFile = remember(document.path) { desktopPdfAnnotationFile(document.path) }

    LaunchedEffect(document.path) {
        annotations.clear()
        if (annotationFile.exists()) {
            annotations.addAll(
                withContext(Dispatchers.IO) {
                    SharedPdfAnnotationSerializer.decode(annotationFile.readText())
                }
            )
        }
    }

    LaunchedEffect(document.path, annotations.size) {
        val snapshot = annotations.toList()
        withContext(Dispatchers.IO) {
            runCatching {
                annotationFile.parentFile?.mkdirs()
                annotationFile.writeText(SharedPdfAnnotationSerializer.encode(snapshot))
            }
        }
    }

    fun applyTool(tool: PdfInkTool) {
        selectedTool = tool
        val config = SharedPdfAnnotationDefaults.configFor(tool)
        selectedColor = config.colorArgb
        strokeWidth = config.strokeWidth
    }

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
        activeStroke = emptyList()
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
        renderJob?.cancel()
        renderJob = launch {
            delay(90)
            isRendering = true
            renderError = null
            val safeScale = zoomSpec.safeRenderScale(
                document.pageSizes[pageIndex].width,
                document.pageSizes[pageIndex].height,
                scale
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DesktopPdfium.renderPage(document, pageIndex, safeScale)
                }
            }
            renderedPage = result.getOrNull()
            renderError = result.exceptionOrNull()?.message
                ?: if (renderedPage == null) "Failed to render page." else null
            isRendering = false
        }
    }

    SharedScreenScaffold(
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
        Row(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft -> {
                            goToPage(pageIndex - 1)
                            true
                        }
                        event.key == Key.DirectionRight -> {
                            goToPage(pageIndex + 1)
                            true
                        }
                        event.isCtrlPressed && event.key == Key.Equals -> {
                            scale = zoomSpec.clamp(scale + 0.15f)
                            true
                        }
                        event.isCtrlPressed && event.key == Key.Minus -> {
                            scale = zoomSpec.clamp(scale - 0.15f)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scale = zoomSpec.clamp(scale - 0.15f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                            }
                            Text("${(scale * 100).toInt()}%", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { scale = zoomSpec.clamp(scale + 0.15f) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                            }
                        }
                        Slider(
                            value = scale,
                            onValueChange = { scale = zoomSpec.clamp(it) },
                            valueRange = zoomSpec.min..zoomSpec.max
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Annotations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SharedPdfAnnotationToolDock(
                            selectedTool = selectedTool,
                            selectedColor = selectedColor,
                            strokeWidth = strokeWidth,
                            onToolSelected = ::applyTool,
                            onColorSelected = { selectedColor = it },
                            onStrokeWidthChange = { strokeWidth = it },
                            onUndo = {
                                annotations.indexOfLast { it.pageIndex == pageIndex }.takeIf { it >= 0 }?.let {
                                    annotations.removeAt(it)
                                }
                            },
                            onClearPage = {
                                annotations.removeAll { it.pageIndex == pageIndex }
                            }
                        )
                    }
                    if (selectedTool == PdfInkTool.TEXT) {
                        item {
                            OutlinedTextField(
                                value = textDraft,
                                onValueChange = { textDraft = it },
                                label = { Text("Text note") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Click the page to place the note.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                    renderedPage != null -> {
                        val pageRender = renderedPage!!
                        Box(
                            modifier = Modifier
                                .size(pageRender.width.dp, pageRender.height.dp)
                                .onSizeChanged { pageCanvasSize = it }
                                .pointerInput(pageIndex, selectedTool, selectedColor, strokeWidth, textDraft) {
                                    if (selectedTool == PdfInkTool.TEXT) {
                                        detectTapGestures(
                                            onTap = { start ->
                                                val text = textDraft.trim()
                                                if (text.isNotEmpty()) {
                                                    val bounds = pageBoundsFromSharedPdfPoint(start, pageCanvasSize)
                                                    annotations.add(
                                                        SharedPdfAnnotation(
                                                            id = "text_${System.currentTimeMillis()}",
                                                            pageIndex = pageIndex,
                                                            kind = PdfAnnotationKind.TEXT,
                                                            tool = PdfInkTool.TEXT,
                                                            bounds = bounds,
                                                            text = text,
                                                            colorArgb = selectedColor,
                                                            fontSize = 18f,
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                    textDraft = ""
                                                }
                                            }
                                        )
                                    } else {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                if (selectedTool != PdfInkTool.ERASER) {
                                                activeStroke = listOf(start.toSharedPdfPoint(pageCanvasSize, System.currentTimeMillis()))
                                            }
                                            },
                                            onDrag = { change, _ ->
                                                if (selectedTool == PdfInkTool.ERASER) {
                                                    val point = change.position
                                                    annotations.removeAll { it.pageIndex == pageIndex && it.sharedPdfHitTest(point, pageCanvasSize) }
                                                } else {
                                                    activeStroke = activeStroke + change.position.toSharedPdfPoint(pageCanvasSize, System.currentTimeMillis())
                                                }
                                            },
                                            onDragEnd = {
                                                if (activeStroke.size > 1) {
                                                    annotations.add(
                                                        SharedPdfAnnotation(
                                                            id = "ink_${System.currentTimeMillis()}",
                                                            pageIndex = pageIndex,
                                                            kind = PdfAnnotationKind.INK,
                                                            tool = selectedTool,
                                                            points = activeStroke,
                                                            colorArgb = selectedColor,
                                                            strokeWidth = strokeWidth,
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                activeStroke = emptyList()
                                            },
                                            onDragCancel = { activeStroke = emptyList() }
                                        )
                                    }
                                }
                        ) {
                            Image(
                                bitmap = pageRender.image,
                                contentDescription = "PDF page ${pageIndex + 1}"
                            )
                            SharedPdfAnnotationOverlay(
                                annotations = annotations.filter { it.pageIndex == pageIndex },
                                activeStroke = activeStroke,
                                canvasSize = pageCanvasSize
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ReaderPdfSearchResult(
    val pageIndex: Int,
    val preview: String
)

private fun desktopPdfAnnotationFile(documentPath: String): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(baseDir, "Episteme/annotations/pdf_$safeName.json")
}

@Composable
private fun ReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onOpenEpub: () -> Unit,
    onOpenPdf: () -> Unit,
    webViewRuntimeState: DesktopWebViewRuntimeState
) {
    SharedReaderScreen(
        session = session,
        readerEngine = readerEngine,
        onSessionChange = onSessionChange,
        onOpenEpub = onOpenEpub,
        onOpenPdf = onOpenPdf
    ) { html, background ->
        Surface(
            color = background,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (webViewRuntimeState.initialized) {
                DesktopEpubWebView(
                    html = html,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DesktopWebViewRuntimeIndicator(
                    state = webViewRuntimeState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DesktopEpubWebView(
    html: String,
    modifier: Modifier = Modifier
) {
    key(html) {
        val state = rememberWebViewStateWithHTMLData(
            data = html,
            baseUrl = null,
            encoding = "utf-8",
            mimeType = "text/html",
            historyUrl = null
        )

        Box(modifier = modifier) {
            WebView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                captureBackPresses = false
            )

            val loadingState = state.loadingState
            if (loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = { loadingState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DesktopWebViewRuntimeIndicator(
    state: DesktopWebViewRuntimeState,
    modifier: Modifier = Modifier
) {
    val message = when {
        state.errorMessage != null -> "Embedded webview could not start: ${state.errorMessage}"
        state.restartRequired -> "Embedded webview installed. Restart Episteme to finish setup."
        state.downloadProgress >= 0f -> "Downloading embedded webview ${state.downloadProgress.toInt()}%"
        else -> "Preparing embedded webview..."
    }

    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.errorMessage == null && !state.restartRequired) {
                CircularProgressIndicator()
            }
            Text(
                text = message,
                color = if (state.errorMessage == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            if (state.downloadProgress in 0f..100f) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress / 100f },
                    modifier = Modifier.width(260.dp)
                )
            }
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

private fun SharedReaderScreenState.withBanner(message: String, isError: Boolean = false): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message, isError = isError)))
}

private fun List<BookItem>.collectTags(): List<Tag> {
    return flatMap { it.tags }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

private fun BookItem.cardTitleForMessage(): String {
    return title?.takeIf { it.isNotBlank() } ?: displayName
}

private fun Long.toReadableSize(): String {
    if (this <= 0L) return "Unknown"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${this} ${units[unitIndex]}"
    } else {
        "${String.format("%.1f", value)} ${units[unitIndex]}"
    }
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
