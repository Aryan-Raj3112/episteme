package com.aryan.reader.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
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
import com.aryan.reader.shared.AppThemeMode
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfNormalizedPoint
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSelectionGeometry
import com.aryan.reader.shared.pdf.PdfTextCharBounds
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.SharedTextBookFactory
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.toFileType
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
import com.aryan.reader.shared.withImportedFiles
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.net.URLEncoder
import javax.swing.JFileChooser
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
    val libraryProjector = remember { SharedLibraryStateProjector(DesktopFolderPathResolver) }
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
        val initialBooks = initialLibrarySnapshot.books.filter { it.type in DesktopReadableFileTypes }
        val initialTags = initialLibrarySnapshot.tags.ifEmpty { initialBooks.collectTags() }
        val initialState = SharedReaderScreenState(
            rawLibraryBooks = initialBooks,
            recentFilesLimit = initialLibrarySnapshot.recentFilesLimit,
            allTags = initialTags,
            syncedFolders = initialLibrarySnapshot.syncedFolders,
            isTabsEnabled = initialLibrarySnapshot.isTabsEnabled,
            openTabIds = initialLibrarySnapshot.openTabIds,
            activeTabBookId = initialLibrarySnapshot.activeTabBookId,
            pinnedHomeBookIds = initialLibrarySnapshot.pinnedHomeBookIds,
            pinnedLibraryBookIds = initialLibrarySnapshot.pinnedLibraryBookIds,
            useStrictFileFilter = initialLibrarySnapshot.useStrictFileFilter,
            appThemeMode = initialLibrarySnapshot.appThemeMode
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
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
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
                        tags = projected.allTags,
                        syncedFolders = projected.syncedFolders,
                        recentFilesLimit = projected.recentFilesLimit,
                        isTabsEnabled = projected.isTabsEnabled,
                        openTabIds = projected.openTabIds,
                        activeTabBookId = projected.activeTabBookId,
                        pinnedHomeBookIds = projected.pinnedHomeBookIds,
                        pinnedLibraryBookIds = projected.pinnedLibraryBookIds,
                        useStrictFileFilter = projected.useStrictFileFilter,
                        appThemeMode = projected.appThemeMode
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
        val importableFiles = files.filter { it.desktopFileType() in DesktopReadableFileTypes }
        if (importableFiles.isEmpty() && files.isNotEmpty()) {
            updateState(state.withBanner("No supported desktop reader files were selected. EPUB, PDF, TXT, MD, and HTML are supported.", isError = true))
            return
        }
        val skipped = files.size - importableFiles.size
        val syncedFolders = mergeSyncedFolders(
            existing = state.syncedFolders,
            folderRoots = importableFiles.mapNotNull { it.sourceFolder }.distinct(),
            nowMillis = System.currentTimeMillis()
        )
        val next = state.withImportedFiles(importableFiles)
            .copy(syncedFolders = syncedFolders)
            .let {
                when {
                    skipped > 0 -> it.withBanner("Imported supported files. Skipped $skipped unsupported file(s).")
                    else -> it
                }
            }
        updateState(next)
    }

    fun importFolder(folder: File) {
        val files = folder.walkTopDown()
            .filter { it.isFile }
            .map { it.toImportedBookFile(sourceFolder = folder.absolutePath) }
            .toList()
        if (files.isEmpty()) {
            updateState(state.withBanner("That folder does not contain any files.", isError = true))
            return
        }
        importFiles(files)
    }

    fun updateActiveBookReadingState(pageIndex: Int, progress: Float, session: ReaderSessionState? = null) {
        activeReaderBookId?.let { bookId ->
            updateState(
                state.copy(rawLibraryBooks = state.rawLibraryBooks.map { book ->
                    if (book.id == bookId) {
                        book.copy(
                            progressPercentage = progress,
                            timestamp = System.currentTimeMillis(),
                            isRecent = true,
                            lastPageIndex = pageIndex,
                            readerSettings = session?.reader?.settings ?: book.readerSettings,
                            readerBookmarks = session?.bookmarks ?: book.readerBookmarks
                        )
                    } else {
                        book
                    }
                })
            )
        }
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

    fun recordBookOpened(bookId: String) {
        val now = System.currentTimeMillis()
        val next = SharedLibraryEditor.markBookOpened(state, bookId, now)
        updateState(next.reduce(AppAction.BookTabOpened(bookId)))
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
            recordBookOpened(book.id)
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
        val restoredSettings = book.readerSettings ?: readerSession.reader.settings
        val restoredSession = readerEngine.createSession(
            book = loadedBook,
            settings = restoredSettings,
            initialPageIndex = book.lastPageIndex ?: 0,
            bookmarks = book.readerBookmarks
        )
        val restoredProgress = book.progressPercentage
        readerSession = if (book.lastPageIndex == null && restoredProgress != null) {
            readerEngine.goToProgress(restoredSession, restoredProgress.coerceIn(0f, 100f) / 100f)
        } else {
            restoredSession
        }
        activeReaderBookId = book.id
        recordBookOpened(book.id)
        selectedTab = SharedAppTab.READER
    }

    fun removeFolder(shelf: Shelf) {
        val removedBookIds = shelf.books.mapTo(mutableSetOf()) { it.id }
        val wasReadingRemovedBook = activeReaderBookId in removedBookIds
        val nextTabBook = state.openTabIds
            .filterNot { it in removedBookIds }
            .lastOrNull()
            ?.let { nextId -> state.rawLibraryBooks.firstOrNull { it.id == nextId } }
        SharedLibraryEditor.removeFolder(state, shelfRecords, shelfRefs, shelf)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
            if (wasReadingRemovedBook) {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                if (nextTabBook != null) {
                    openReader(nextTabBook)
                } else {
                    readerSession = readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())
                    selectedTab = SharedAppTab.HOME
                }
            }
        }
    }

    fun closeReaderTab(book: BookItem) {
        val wasActive = activeReaderBookId == book.id
        val remainingIds = state.openTabIds.filterNot { it == book.id }
        updateState(state.reduce(AppAction.BookTabClosed(book.id)))
        if (!wasActive) return

        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        val nextBook = remainingIds.lastOrNull()?.let { nextId ->
            state.rawLibraryBooks.firstOrNull { it.id == nextId }
        }
        if (nextBook != null) {
            openReader(nextBook)
        } else {
            readerSession = readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())
            selectedTab = SharedAppTab.HOME
        }
    }

    fun closeAllReaderTabs() {
        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        readerSession = readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())
        selectedTab = SharedAppTab.HOME
        updateState(state.reduce(AppAction.AllTabsClosed))
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

    val colorScheme = if (state.appThemeMode == AppThemeMode.DARK) {
        darkColorScheme(
            primary = Color(0xFF70DBB2),
            secondary = Color(0xFFD6C2AD),
            tertiary = Color(0xFFFFB3B7),
            surface = Color(0xFF111411),
            surfaceVariant = Color(0xFF3F493F)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006C4C),
            secondary = Color(0xFF705D49),
            tertiary = Color(0xFF9C4146),
            surface = Color(0xFFFCFCF8),
            surfaceVariant = Color(0xFFE5E8DE)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        SharedAppShell(
            selectedTab = selectedTab,
            snackbarHostState = snackbarHostState,
            appThemeMode = state.appThemeMode,
            isTabsEnabled = state.isTabsEnabled,
            onTabSelected = { selectedTab = it },
            onImportFiles = { importFiles(chooseFiles()) },
            onImportFolder = { chooseFolder()?.let(::importFolder) },
            onSyncRequested = {
                updateState(state.reduce(AppAction.BannerShown(BannerMessage("Cloud sync is Android-only for now. Desktop sync will need a separate backend adapter."))))
            },
            onAppThemeModeChange = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
            onTabsEnabledChange = { enabled -> updateState(state.reduce(AppAction.TabsEnabledChanged(enabled))) }
        ) { tab ->
            when (tab) {
                        SharedAppTab.HOME -> HomeScreen(
                            state = state,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onImportFolder = { chooseFolder()?.let(::importFolder) },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onOpenTab = ::openReader,
                            onCloseTab = ::closeReaderTab,
                            onCloseAllTabs = ::closeAllReaderTabs,
                            onRecentLimitChange = { limit -> updateState(state.reduce(LibraryAction.RecentLimitChanged(limit))) },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.HomePinToggled(book.id))) }
                        )

                        SharedAppTab.LIBRARY -> LibraryScreen(
                            state = state,
                            selectedLibraryTab = selectedLibraryTab,
                            onLibraryTabChange = { selectedLibraryTab = it },
                            onStateChange = ::updateState,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onImportFolder = { chooseFolder()?.let(::importFolder) },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) }
                        )

                        SharedAppTab.SHELVES -> ShelvesScreen(
                            shelves = state.shelves,
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedLibraryBookIds,
                            onShowBookInfo = { bookInfoDialogFor = it },
                            onEditBook = { bookEditDialogFor = it },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it }
                        )

                        SharedAppTab.READER -> {
                            val pdfDocument = activePdfDocument
                            if (pdfDocument != null) {
                                PdfReaderScreen(
                                    document = pdfDocument,
                                    initialPageIndex = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.lastPageIndex }
                                        ?: 0,
                                    onOpenPdf = ::importAndOpenPdf,
                                    onOpenEpub = ::importAndOpenEpub,
                                    onPageStateChange = { page, progress ->
                                        updateActiveBookReadingState(page, progress)
                                    }
                                )
                            } else {
                                ReaderScreen(
                                    session = readerSession,
                                    readerEngine = readerEngine,
                                    onSessionChange = { updated ->
                                        readerSession = updated
                                        updateActiveBookReadingState(
                                            pageIndex = updated.reader.currentPageIndex,
                                            progress = updated.reader.progress,
                                            session = updated
                                        )
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

        folderToRemove?.let { folder ->
            SharedConfirmDialog(
                title = "Remove folder",
                body = "Remove \"${folder.name}\" and its ${folder.bookCount} book(s) from the app? Files on disk will not be deleted.",
                confirmLabel = "Remove",
                onDismiss = { folderToRemove = null },
                onConfirm = {
                    removeFolder(folder)
                    folderToRemove = null
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
    onImportFolder: () -> Unit,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit,
    onRecentLimitChange: (Int) -> Unit,
    onTogglePinned: (BookItem) -> Unit
) {
    SharedHomeScreen(
        state = state,
        onImportBooks = onImportBooks,
        onImportFolder = onImportFolder,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onClearSelection = onClearSelection,
        onRemoveSelected = onRemoveSelected,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf,
        onOpenTab = onOpenTab,
        onCloseTab = onCloseTab,
        onCloseAllTabs = onCloseAllTabs,
        onRecentLimitChange = onRecentLimitChange,
        onTogglePinned = onTogglePinned
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
    onRemoveFolder: (Shelf) -> Unit,
    onTagSelectedBooks: () -> Unit,
    onAddSelectedBooksToShelf: () -> Unit,
    onImportFolder: () -> Unit,
    onTogglePinned: (BookItem) -> Unit
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
        onRemoveFolder = onRemoveFolder,
        onTagSelectedBooks = onTagSelectedBooks,
        onAddSelectedBooksToShelf = onAddSelectedBooksToShelf,
        onImportFolder = onImportFolder,
        onTogglePinned = onTogglePinned
    )
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    onRead: (BookItem) -> Unit,
    onSelect: (String) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    onEditBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onCreateShelf: () -> Unit,
    onRenameShelf: (Shelf) -> Unit,
    onDeleteShelf: (Shelf) -> Unit,
    onRemoveFolder: (Shelf) -> Unit
) {
    SharedShelvesScreen(
        shelves = shelves,
        selectedBookIds = selectedBookIds,
        pinnedBookIds = pinnedBookIds,
        onOpenBook = onRead,
        onToggleSelection = onSelect,
        onShowBookInfo = onShowBookInfo,
        onEditBook = onEditBook,
        onTogglePinned = onTogglePinned,
        onCreateShelf = onCreateShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onRemoveFolder = onRemoveFolder
    )
}

@Composable
private fun PdfReaderScreen(
    document: DesktopPdfDocument,
    initialPageIndex: Int,
    onOpenPdf: () -> Unit,
    onOpenEpub: () -> Unit,
    onPageStateChange: (pageIndex: Int, progress: Float) -> Unit
) {
    var pageIndex by remember(document.path) { mutableStateOf(initialPageIndex.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))) }
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
    var isTextSelectionMode by remember(document.path) { mutableStateOf(false) }
    var selectionStartIndex by remember(document.path, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(document.path, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(document.path, pageIndex) { mutableStateOf<Offset?>(null) }
    val annotations = remember(document.path) { mutableStateListOf<SharedPdfAnnotation>() }
    val annotationFile = remember(document.path) { desktopPdfAnnotationFile(document.path) }
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val pageVerticalScrollState = rememberScrollState()
    val pageHorizontalScrollState = rememberScrollState()
    val currentTextSelection by rememberUpdatedState(textSelection)

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

    LaunchedEffect(document.path, annotations.toList()) {
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
            document.textPages.flatMapIndexed { index, text ->
                val matches = mutableListOf<ReaderPdfSearchResult>()
                var startIndex = 0
                while (startIndex < text.length) {
                    val matchIndex = text.indexOf(normalized, startIndex, ignoreCase = true)
                    if (matchIndex < 0) break
                    matches += ReaderPdfSearchResult(
                        pageIndex = index,
                        preview = text.previewAround(matchIndex, normalized.length),
                        matchIndex = matchIndex
                    )
                    startIndex = matchIndex + normalized.length.coerceAtLeast(1)
                }
                matches
            }
        }
    }

    fun goToPage(target: Int) {
        pageIndex = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        activeStroke = emptyList()
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
    }

    fun copySelection() {
        textSelection?.text?.takeIf { it.isNotBlank() }?.let {
            clipboardManager.setText(AnnotatedString(it))
        }
        selectionMenuOffset = null
    }

    fun highlightSelection() {
        val selection = textSelection ?: return
        val now = System.currentTimeMillis()
        val highlightBounds = DesktopPdfium.textRectsForRange(
            document = document,
            pageIndex = pageIndex,
            startIndex = selection.startIndex,
            endIndex = selection.endIndex,
            viewportWidth = pageCanvasSize.width,
            viewportHeight = pageCanvasSize.height
        ).map { it.toPdfPageBounds() }
            .filter { it.right > it.left && it.bottom > it.top }
            .mergePdfBoundsByLine()
            .ifEmpty { selection.lineBounds }
        logPdfSelection(
            "highlight_create page=${pageIndex + 1} " +
                "range=${selection.startIndex}..${selection.endIndex} " +
                "chars=${selection.text.length} lines=${highlightBounds.size} " +
                "text=\"${selection.text.logPreview()}\""
        )
        logPdfSelection(
            "highlight_store page=${pageIndex + 1} " +
                "range=${selection.startIndex}..${selection.endIndex} " +
                "mode=dynamic_range"
        )
        highlightBounds.forEachIndexed { index, bounds ->
            logPdfSelection(
                "highlight_bound page=${pageIndex + 1} index=$index " +
                    "left=${bounds.left.formatLogFloat()} top=${bounds.top.formatLogFloat()} " +
                    "right=${bounds.right.formatLogFloat()} bottom=${bounds.bottom.formatLogFloat()}"
            )
        }
        annotations.add(
            SharedPdfAnnotation(
                id = "highlight_${now}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = PdfInkTool.HIGHLIGHTER,
                bounds = highlightBounds.firstOrNull(),
                text = selection.text,
                colorArgb = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).colorArgb,
                rangeStartIndex = selection.startIndex,
                rangeEndIndex = selection.endIndex,
                createdAt = now
            )
        )
        textSelection = null
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        selectionMenuOffset = null
    }

    fun searchSelection() {
        val selection = textSelection ?: return
        searchQuery = selection.text.take(120)
        activeSearchIndex = -1
        selectionMenuOffset = null
    }

    fun translateSelection() {
        val selection = textSelection ?: return
        openExternalUrl("https://translate.google.com/?sl=auto&tl=en&text=${selection.text.urlEncode()}&op=translate")
        selectionMenuOffset = null
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
        onPageStateChange(pageIndex, ((pageIndex + 1).toFloat() / document.pageCount.coerceAtLeast(1)) * 100f)
    }

    LaunchedEffect(document.path, pageIndex, scale) {
        renderJob?.cancel()
        val requestedPageIndex = pageIndex
        val requestedScale = scale
        renderJob = launch {
            delay(90)
            isRendering = true
            renderError = null
            val pageSize = document.pageSizes[requestedPageIndex]
            val safeScale = zoomSpec.safeRenderScale(
                pageSize.width,
                pageSize.height,
                requestedScale
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DesktopPdfium.renderPage(document, requestedPageIndex, safeScale)
                }
            }
            if (requestedPageIndex != pageIndex || requestedScale != scale) {
                return@launch
            }
            renderedPage = result.getOrNull()
            renderError = result.exceptionOrNull()?.message
                ?: if (renderedPage == null) "Failed to render page." else null
            renderedPage?.let { render ->
                logPdfSelection(
                    "render page=${requestedPageIndex + 1} " +
                        "requestedScale=${requestedScale.formatLogFloat()} safeScale=${safeScale.formatLogFloat()} " +
                        "pageSize=${pageSize.width.formatLogFloat()}x${pageSize.height.formatLogFloat()} " +
                        "bitmap=${render.width}x${render.height} capped=${safeScale < zoomSpec.clamp(requestedScale)}"
                )
            }
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
                        event.key == Key.PageUp -> {
                            goToPage(pageIndex - 1)
                            true
                        }
                        event.key == Key.PageDown -> {
                            goToPage(pageIndex + 1)
                            true
                        }
                        event.key == Key.MoveHome -> {
                            goToPage(0)
                            true
                        }
                        event.key == Key.MoveEnd -> {
                            goToPage(document.pageCount - 1)
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
                        FilterChip(
                            selected = isTextSelectionMode,
                            onClick = {
                                isTextSelectionMode = !isTextSelectionMode
                                if (!isTextSelectionMode) {
                                    textSelection = null
                                    selectionStartIndex = null
                                    selectionEndIndex = null
                                    selectionStartHit = null
                                    selectionEndHit = null
                                    selectionMenuOffset = null
                                }
                            },
                            label = { Text("Select text") }
                        )
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
                                    when {
                                        searchResults.isEmpty() -> "No matches"
                                        activeSearchIndex in searchResults.indices -> "${activeSearchIndex + 1} of ${searchResults.size}"
                                        else -> "${searchResults.size} matches"
                                    },
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
                    items(searchResults, key = { "${it.pageIndex}_${it.matchIndex}_${it.preview}" }) { result ->
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
                    .horizontalScroll(pageHorizontalScrollState)
                    .verticalScroll(pageVerticalScrollState)
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    isRendering -> CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    renderError != null -> Text(renderError ?: "Failed to render page.", color = MaterialTheme.colorScheme.error)
                    renderedPage != null -> {
                        val pageRender = renderedPage!!
                        val pageWidthDp = with(density) { pageRender.width.toDp() }
                        val pageHeightDp = with(density) { pageRender.height.toDp() }
                        val pageRenderScale = pageRender.width / document.pageSizes[pageIndex].width
                        val pageAnnotations = remember(annotations.toList(), pageIndex, pageCanvasSize) {
                            annotations
                                .filter { it.pageIndex == pageIndex }
                                .flatMap { annotation ->
                                    annotation.toRenderablePdfAnnotations(document, pageIndex, pageCanvasSize)
                                }
                        }
                        Box(
                            modifier = Modifier
                                .size(pageWidthDp, pageHeightDp)
                                .onSizeChanged { size ->
                                    if (pageCanvasSize != size) {
                                        logPdfSelection(
                                            "layout page=${pageIndex + 1} " +
                                                "canvas=${size.formatLogSize()} bitmap=${pageRender.width}x${pageRender.height} " +
                                                "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()}"
                                        )
                                    }
                                    pageCanvasSize = size
                                }
                                .pointerInput(pageIndex, pageCanvasSize) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                                val point = event.changes.firstOrNull()?.position ?: continue
                                                val selection = currentTextSelection
                                                if (selection != null) {
                                                    selectionMenuOffset = point
                                                    logPdfSelection(
                                                        "menu_open page=${pageIndex + 1} " +
                                                            "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                            "range=${selection.startIndex}..${selection.endIndex} " +
                                                            "chars=${selection.text.length}"
                                                    )
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } else if (
                                                event.type == PointerEventType.Press &&
                                                event.buttons.isPrimaryPressed &&
                                                currentTextSelection != null &&
                                                selectionMenuOffset == null
                                            ) {
                                                selectionMenuOffset = null
                                                textSelection = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                            }
                                        }
                                    }
                                }
                                .pointerInput(
                                    pageIndex,
                                    isTextSelectionMode,
                                    selectedTool,
                                    selectedColor,
                                    strokeWidth,
                                    textDraft,
                                    document.textCharsByPage,
                                    pageCanvasSize,
                                    pageRender.width,
                                    pageRender.height
                                ) {
                                    if (isTextSelectionMode) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                selectionMenuOffset = null
                                                val hit = document.charHitAt(pageIndex, start, pageCanvasSize)
                                                selectionStartHit = hit
                                                selectionStartIndex = hit?.index
                                                selectionEndHit = null
                                                selectionEndIndex = null
                                                logPdfSelection(
                                                    "drag_start page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${pageRender.width}x${pageRender.height} " +
                                                        "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                                        hit.formatLogHit("start")
                                                )
                                                textSelection = null
                                            },
                                            onDrag = { change, _ ->
                                                val startIndex = selectionStartIndex
                                                val hit = document.charHitAt(pageIndex, change.position, pageCanvasSize)
                                                selectionEndHit = hit
                                                val endIndex = hit?.index
                                                val previousEndIndex = selectionEndIndex
                                                selectionEndIndex = endIndex
                                                if (endIndex != previousEndIndex || textSelection == null) {
                                                    textSelection = if (startIndex != null && endIndex != null) {
                                                        document.selectionBetweenIndexes(
                                                            pageIndex = pageIndex,
                                                            startIndex = startIndex,
                                                            endIndex = endIndex,
                                                            canvasSize = pageCanvasSize,
                                                            useNativeBounds = false
                                                        )
                                                    } else {
                                                        null
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                val startIndex = selectionStartIndex
                                                val endIndex = selectionEndIndex
                                                val selection = if (startIndex != null && endIndex != null) {
                                                    document.selectionBetweenIndexes(
                                                        pageIndex = pageIndex,
                                                        startIndex = startIndex,
                                                        endIndex = endIndex,
                                                        canvasSize = pageCanvasSize,
                                                        useNativeBounds = true
                                                    )?.also { textSelection = it }
                                                } else {
                                                    textSelection
                                                }
                                                logPdfSelection(
                                                    "drag_end page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${pageRender.width}x${pageRender.height} " +
                                                        "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                                        selectionStartHit.formatLogHit("start") + " " +
                                                        selectionEndHit.formatLogHit("end") + " " +
                                                        "range=${selection?.startIndex}..${selection?.endIndex} " +
                                                        "chars=${selection?.text?.length ?: 0} " +
                                                        "lines=${selection?.lineBounds?.size ?: 0} " +
                                                        "text=\"${selection?.text.orEmpty().logPreview()}\""
                                                )
                                                selectionStartIndex = null
                                                selectionEndIndex = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                            },
                                            onDragCancel = {
                                                logPdfSelection(
                                                    "drag_cancel page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${pageRender.width}x${pageRender.height} " +
                                                        "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                                        selectionStartHit.formatLogHit("start") + " " +
                                                        selectionEndHit.formatLogHit("end")
                                                )
                                                selectionStartIndex = null
                                                selectionEndIndex = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                            }
                                        )
                                    } else if (selectedTool == PdfInkTool.TEXT) {
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
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                            PdfTextSelectionOverlay(
                                selection = textSelection,
                                canvasSize = pageCanvasSize
                            )
                            SharedPdfAnnotationOverlay(
                                annotations = pageAnnotations,
                                activeStroke = activeStroke,
                                canvasSize = pageCanvasSize
                            )
                            if (textSelection != null && selectionMenuOffset != null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(pageIndex, selectionMenuOffset) {
                                            detectTapGestures {
                                                selectionMenuOffset = null
                                                textSelection = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                            }
                                        }
                                )
                            }
                            PdfSelectionMenu(
                                selection = textSelection,
                                menuOffset = selectionMenuOffset,
                                canvasSize = pageCanvasSize,
                                onCopy = ::copySelection,
                                onHighlight = ::highlightSelection,
                                onSearch = ::searchSelection,
                                onTranslate = ::translateSelection,
                                onClear = {
                                    textSelection = null
                                    selectionStartIndex = null
                                    selectionEndIndex = null
                                    selectionStartHit = null
                                    selectionEndHit = null
                                    selectionMenuOffset = null
                                }
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
    val preview: String,
    val matchIndex: Int
)

private data class DesktopPdfTextSelection(
    val text: String,
    val lineBounds: List<PdfPageBounds>,
    val startIndex: Int,
    val endIndex: Int
)

private data class DesktopPdfCharHit(
    val index: Int,
    val source: String,
    val point: Offset,
    val normalized: PdfNormalizedPoint
)

@Composable
private fun PdfTextSelectionOverlay(
    selection: DesktopPdfTextSelection?,
    canvasSize: IntSize
) {
    val bounds = selection?.lineBounds.orEmpty()
    if (bounds.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        bounds.forEach { rect ->
            drawRect(
                color = Color(0x663B82F6),
                topLeft = Offset(rect.left * canvasSize.width, rect.top * canvasSize.height),
                size = androidx.compose.ui.geometry.Size(
                    (rect.right - rect.left) * canvasSize.width,
                    (rect.bottom - rect.top) * canvasSize.height
                )
            )
        }
    }
}

@Composable
private fun PdfSelectionMenu(
    selection: DesktopPdfTextSelection?,
    menuOffset: Offset?,
    canvasSize: IntSize,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onSearch: () -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit
) {
    selection ?: return
    val anchor = menuOffset ?: return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(
            start = anchor.x.coerceIn(
                PdfSelectionMenuMarginPx,
                (canvasSize.width.toFloat() - PdfSelectionMenuWidthPx).coerceAtLeast(PdfSelectionMenuMarginPx)
            ).dp,
            top = anchor.y.coerceIn(
                PdfSelectionMenuMarginPx,
                (canvasSize.height.toFloat() - PdfSelectionMenuHeightPx).coerceAtLeast(PdfSelectionMenuMarginPx)
            ).dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCopy) { Text("Copy") }
            TextButton(onClick = onHighlight) { Text("Highlight") }
            TextButton(onClick = onSearch) { Text("Find") }
            TextButton(onClick = onTranslate) { Text("Translate") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

private fun DesktopPdfDocument.charHitAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): DesktopPdfCharHit? {
    val normalized = PdfSelectionGeometry.normalizedPoint(
        pointX = point.x,
        pointY = point.y,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    ) ?: return null
    val nativeIndex = DesktopPdfium.charIndexAt(
        document = this,
        pageIndex = pageIndex,
        normalizedX = normalized.x,
        normalizedY = normalized.y,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    )
    if (nativeIndex != null) {
        return DesktopPdfCharHit(
            index = nativeIndex,
            source = "native",
            point = point,
            normalized = normalized
        )
    }
    val fallback = PdfSelectionGeometry.nearestCharOnLine(
        chars = textCharsByPage.getOrNull(pageIndex).orEmpty().visiblePdfTextBounds(),
        point = normalized
    ) ?: return null
    return DesktopPdfCharHit(
        index = fallback.index,
        source = "fallback_line",
        point = point,
        normalized = normalized
    )
}

private fun DesktopPdfDocument.selectionBetweenIndexes(
    pageIndex: Int,
    startIndex: Int,
    endIndex: Int,
    canvasSize: IntSize,
    useNativeBounds: Boolean = true
): DesktopPdfTextSelection? {
    val chars = textCharsByPage.getOrNull(pageIndex).orEmpty()
    if (chars.isEmpty() || abs(startIndex - endIndex) < 1) return null
    val firstIndex = minOf(startIndex, endIndex)
    val lastIndex = maxOf(startIndex, endIndex)
    val selectedChars = chars.filter { it.index in firstIndex..lastIndex }
    val text = selectedChars.joinToString("") { it.char.toString() }
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (text.isBlank()) return null
    val fallbackBounds = PdfSelectionGeometry.lineBoundsForChars(selectedChars.visiblePdfTextBounds())
    val nativeBounds = if (useNativeBounds) {
        DesktopPdfium.textRectsForRange(
            document = this,
            pageIndex = pageIndex,
            startIndex = firstIndex,
            endIndex = lastIndex,
            viewportWidth = canvasSize.width,
            viewportHeight = canvasSize.height
        ).map { it.toPdfPageBounds() }
            .filter { it.right > it.left && it.bottom > it.top }
            .mergePdfBoundsByLine()
    } else {
        emptyList()
    }
    return DesktopPdfTextSelection(
        text = text,
        lineBounds = nativeBounds.ifEmpty { fallbackBounds },
        startIndex = firstIndex,
        endIndex = lastIndex
    )
}

private fun DesktopPdfTextRect.toPdfPageBounds(): PdfPageBounds {
    return PdfPageBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

private fun SharedPdfAnnotation.toRenderablePdfAnnotations(
    document: DesktopPdfDocument,
    pageIndex: Int,
    canvasSize: IntSize
): List<SharedPdfAnnotation> {
    val startIndex = rangeStartIndex
    val endIndex = rangeEndIndex
    if (kind != PdfAnnotationKind.HIGHLIGHT || startIndex == null || endIndex == null) {
        return listOf(this)
    }
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return listOf(this)
    }
    val dynamicBounds = DesktopPdfium.textRectsForRange(
        document = document,
        pageIndex = pageIndex,
        startIndex = startIndex,
        endIndex = endIndex,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    ).map { it.toPdfPageBounds() }
        .filter { it.right > it.left && it.bottom > it.top }
        .mergePdfBoundsByLine()

    return dynamicBounds.ifEmpty { listOfNotNull(bounds).ifEmpty { emptyList() } }
        .mapIndexed { index, dynamicBounds ->
            copy(
                id = "${id}_line_$index",
                bounds = dynamicBounds
            )
        }
}

private fun List<PdfPageBounds>.mergePdfBoundsByLine(): List<PdfPageBounds> {
    return PdfSelectionGeometry.mergeBoundsByLine(this)
}

private fun List<DesktopPdfTextChar>.visiblePdfTextBounds(): List<PdfTextCharBounds> {
    return asSequence()
        .filter { it.hasBounds && !it.char.isISOControl() }
        .map { it.toPdfTextCharBounds() }
        .toList()
}

private fun DesktopPdfTextChar.toPdfTextCharBounds(): PdfTextCharBounds {
    return PdfTextCharBounds(
        index = index,
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

private const val PdfSelectionMenuWidthPx = 360f
private const val PdfSelectionMenuHeightPx = 54f
private const val PdfSelectionMenuMarginPx = 6f

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
                items(session.searchResults, key = { "${it.pageIndex}_${it.matchIndex}_${it.preview}" }) { result ->
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

private fun chooseFolder(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun SharedReaderScreenState.withBanner(message: String, isError: Boolean = false): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message, isError = isError)))
}

private val DesktopReadableFileTypes = setOf(FileType.EPUB, FileType.PDF, FileType.TXT, FileType.MD, FileType.HTML)

private fun ImportedBookFile.desktopFileType(): FileType {
    return when (val extension = name.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> FileType.MD
        "xhtml" -> FileType.HTML
        else -> name.toFileType().takeUnless { it == FileType.UNKNOWN && extension.isBlank() } ?: FileType.UNKNOWN
    }
}

private fun mergeSyncedFolders(
    existing: List<SyncedFolder>,
    folderRoots: List<String>,
    nowMillis: Long
): List<SyncedFolder> {
    if (folderRoots.isEmpty()) return existing
    val byRoot = existing.associateBy { it.uriString }.toMutableMap()
    folderRoots.forEach { root ->
        val rootFile = File(root)
        byRoot[root] = SyncedFolder(
            uriString = root,
            name = rootFile.name.takeIf { it.isNotBlank() } ?: root,
            lastScanTime = nowMillis,
            allowedFileTypes = DesktopReadableFileTypes
        )
    }
    return byRoot.values.sortedBy { it.name.lowercase() }
}

private object DesktopFolderPathResolver : SharedFolderPathResolver {
    override fun relativeFolderSegments(item: BookItem): List<String> {
        val sourceFolder = item.sourceFolder ?: return emptyList()
        val bookPath = item.path ?: return emptyList()
        val parentFile = File(bookPath).parentFile ?: return emptyList()
        val paths = runCatching {
            File(sourceFolder).toPath().toAbsolutePath().normalize() to
                parentFile.toPath().toAbsolutePath().normalize()
        }.getOrNull() ?: return emptyList()
        val (root, parent) = paths
        if (!parent.startsWith(root) || parent == root) return emptyList()
        return root.relativize(parent).map { it.toString() }.filter { it.isNotBlank() }
    }
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
        "$this ${units[unitIndex]}"
    } else {
        "${String.format("%.1f", value)} ${units[unitIndex]}"
    }
}

private fun File.toImportedBookFile(sourceFolder: String? = null): ImportedBookFile {
    return ImportedBookFile(
        name = name,
        uriString = null,
        localPath = absolutePath,
        size = length(),
        sourceFolder = sourceFolder
    )
}

private fun openExternalUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private const val PdfSelectionLogTag = "EpistemePdfSelection"

private fun logPdfSelection(message: String) {
    println("$PdfSelectionLogTag $message")
}

private fun String.logPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

private fun Float.formatLogFloat(): String {
    return String.format("%.3f", this)
}

private fun IntSize.formatLogSize(): String {
    return "${width}x${height}"
}

private fun DesktopPdfCharHit?.formatLogHit(prefix: String): String {
    if (this == null) {
        return "${prefix}Index=null ${prefix}Source=none ${prefix}X=null ${prefix}Y=null ${prefix}Nx=null ${prefix}Ny=null"
    }
    return "${prefix}Index=$index ${prefix}Source=$source " +
        "${prefix}X=${point.x.formatLogFloat()} ${prefix}Y=${point.y.formatLogFloat()} " +
        "${prefix}Nx=${normalized.x.formatLogFloat()} ${prefix}Ny=${normalized.y.formatLogFloat()}"
}

private fun String.previewAround(index: Int, queryLength: Int): String {
    val start = (index - 70).coerceAtLeast(0)
    val end = (index + queryLength + 100).coerceAtMost(length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < length) "..." else ""
    return prefix + substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
}
