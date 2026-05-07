package com.aryan.reader.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.zIndex
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
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SmartCollectionDefinition
import com.aryan.reader.shared.SmartField
import com.aryan.reader.shared.SmartOperator
import com.aryan.reader.shared.SmartRule
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfNormalizedPoint
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSelectionGeometry
import com.aryan.reader.shared.pdf.PdfTextCharBounds
import com.aryan.reader.shared.pdf.PdfVisiblePageLayout
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfBookmarkSerializer
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfInkRenderer
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfSearchEngine
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.currentSharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.mostVisiblePdfPageIndex
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.sharedPdfStrokePercent
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import com.aryan.reader.shared.pdf.toAnnotation
import com.aryan.reader.shared.pdf.updateCurrentSharedPdfTextStyle
import com.aryan.reader.shared.pdf.withBounds
import com.aryan.reader.shared.pdf.withSharedPdfTextStyle
import com.aryan.reader.shared.pdf.withStyle
import com.aryan.reader.shared.pdf.withText
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.SharedTextBookFactory
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedAppShell
import com.aryan.reader.shared.ui.SharedAppTab
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedBookEditDialog
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedConfirmDialog
import com.aryan.reader.shared.ui.SharedHomeScreen
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedPdfAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfAnnotationToolDock
import com.aryan.reader.shared.ui.SharedPdfEmbeddedAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfInlineTextEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfPageNumberOverlay
import com.aryan.reader.shared.ui.SharedPdfRichTextHiddenInput
import com.aryan.reader.shared.ui.SharedPdfRichTextLayer
import com.aryan.reader.shared.ui.SharedPdfTextAnnotationDock
import com.aryan.reader.shared.ui.SharedPdfTextBoxEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfTextStyleControls
import com.aryan.reader.shared.ui.SharedReaderScreen
import com.aryan.reader.shared.ui.SharedScreenScaffold
import com.aryan.reader.shared.ui.SharedShelvesScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.ui.sharedPdfEmbeddedHitTest
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import com.aryan.reader.shared.withImportedFiles
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.awt.Container
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.JFileChooser
import kotlin.math.abs
import kotlin.math.max

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Episteme",
    ) {
        EpistemeDesktopApp(window)
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
private fun EpistemeDesktopApp(window: Component? = null) {
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
            appThemeMode = initialLibrarySnapshot.appThemeMode,
            appContrastOption = initialLibrarySnapshot.appContrastOption,
            appTextDimFactorLight = initialLibrarySnapshot.appTextDimFactorLight,
            appTextDimFactorDark = initialLibrarySnapshot.appTextDimFactorDark,
            appSeedColor = initialLibrarySnapshot.appSeedColor,
            customAppThemes = initialLibrarySnapshot.customAppThemes,
            readerToolbarPreferences = initialLibrarySnapshot.readerToolbarPreferences,
            readerHighlightPalette = initialLibrarySnapshot.readerHighlightPalette
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
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookEditDialogFor by remember { mutableStateOf<BookItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dropImportState by remember { mutableStateOf(DesktopDropImportState()) }

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
                        appThemeMode = projected.appThemeMode,
                        appContrastOption = projected.appContrastOption,
                        appTextDimFactorLight = projected.appTextDimFactorLight,
                        appTextDimFactorDark = projected.appTextDimFactorDark,
                        appSeedColor = projected.appSeedColor,
                        customAppThemes = projected.customAppThemes,
                        readerToolbarPreferences = projected.readerToolbarPreferences,
                        readerHighlightPalette = projected.readerHighlightPalette
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
            updateState(
                state.withBanner(
                    "No supported desktop reader files were selected. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are supported.",
                    isError = true
                )
            )
            return
        }
        val skipped = files.size - importableFiles.size
        val existingIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
        val importablePaths = importableFiles
            .mapNotNull { it.localPath ?: it.uriString }
            .toSet()
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
        val targetBookIds = next.rawLibraryBooks
            .asSequence()
            .filter { book ->
                book.id !in existingIds ||
                    book.path in importablePaths ||
                    book.id in importablePaths
            }
            .map { it.id }
            .toSet()
        if (targetBookIds.isEmpty()) return
        val originalTargetBooksById = next.rawLibraryBooks
            .filter { it.id in targetBookIds }
            .associateBy { it.id }

        scope.launch {
            val metadataResult = withContext(Dispatchers.IO) {
                DesktopFolderMetadataExtractor.enrichImportedBooks(
                    books = next.rawLibraryBooks,
                    importedBookIds = targetBookIds
                )
            }
            if (metadataResult.stats.updatedBooks > 0) {
                val enrichedBooksById = metadataResult.books
                    .filter { it.id in targetBookIds }
                    .associateBy { it.id }
                updateState(
                    state.copy(
                        rawLibraryBooks = state.rawLibraryBooks.map { book ->
                            val enriched = enrichedBooksById[book.id] ?: return@map book
                            book.withDesktopImportMetadata(
                                enriched = enriched,
                                original = originalTargetBooksById[book.id]
                            )
                        }
                    )
                )
            }
        }
    }

    fun syncLocalFolders(targetFolder: File? = null, showBanner: Boolean = true) {
        if (targetFolder == null && state.syncedFolders.isEmpty()) {
            updateState(state.withBanner("No local folders are linked yet.", isError = true))
            return
        }

        val snapshotState = state
        val snapshotShelfRefs = shelfRefs
        if (showBanner) {
            updateState(state.withBanner("Folder sync: scanning local folders..."))
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopLocalFolderSync.sync(
                    state = snapshotState,
                    shelfRefs = snapshotShelfRefs,
                    targetFolder = targetFolder
                )
            }
            val failedCount = result.failedFolders.size
            val stats = result.stats
            val metadataStats = result.metadataStats
            val message = when {
                failedCount > 0 && stats.supportedFiles == 0 ->
                    "Folder sync failed for $failedCount folder(s)."
                failedCount > 0 ->
                    "Folder sync finished with $failedCount folder(s) skipped."
                else ->
                    "Folder sync complete: ${stats.newBooks} new, ${stats.updatedBooks + stats.remoteMetadataUpdates + metadataStats.updatedBooks} updated, ${stats.removedBooks} removed."
            }
            val completedState = if (showBanner || failedCount > 0) {
                result.state.withBanner(message, isError = failedCount > 0)
            } else {
                result.state
            }
            activeReaderBookId = activeReaderBookId?.let { result.idMigrations[it] ?: it }
            replaceLibrary(
                completedState,
                refs = result.shelfRefs
            )
            if (activeReaderBookId != null && completedState.rawLibraryBooks.none { it.id == activeReaderBookId }) {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                readerSession = readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())
                selectedTab = SharedAppTab.HOME
            }
        }
    }

    fun importFolder(folder: File) {
        if (!DesktopLocalFolderSync.hasSupportedFiles(folder)) {
            updateState(state.withBanner("That folder does not contain any supported desktop reader files.", isError = true))
            return
        }
        syncLocalFolders(targetFolder = folder)
    }

    fun syncBookSidecars(book: BookItem) {
        if (book.sourceFolder.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            DesktopLocalFolderSync.saveBookSidecars(book)
        }
    }

    fun updateActiveBookReadingState(pageIndex: Int, progress: Float, session: ReaderSessionState? = null) {
        activeReaderBookId?.let { bookId ->
            var updatedBook: BookItem? = null
            val next = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.map { book ->
                    if (book.id == bookId) {
                        book.copy(
                            progressPercentage = progress,
                            timestamp = System.currentTimeMillis(),
                            isRecent = true,
                            lastPageIndex = pageIndex,
                            readerSettings = session?.reader?.settings ?: book.readerSettings,
                            readerBookmarks = session?.bookmarks ?: book.readerBookmarks,
                            readerHighlights = session?.highlights ?: book.readerHighlights
                        ).also { updatedBook = it }
                    } else {
                        book
                    }
                }
            )
            updateState(next)
            updatedBook?.let(::syncBookSidecars)
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

    fun createSmartShelf(name: String, definition: SmartCollectionDefinition) {
        SharedLibraryEditor.createSmartShelf(state, shelfRecords, shelfRefs, name, definition, System.currentTimeMillis())?.let {
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
        result.state.rawLibraryBooks.firstOrNull { it.id == updated.id }?.let(::syncBookSidecars)
    }

    fun recordBookOpened(bookId: String) {
        val now = System.currentTimeMillis()
        val next = SharedLibraryEditor.markBookOpened(state, bookId, now)
        val openedState = next.reduce(AppAction.BookTabOpened(bookId))
        updateState(openedState)
        openedState.rawLibraryBooks.firstOrNull { it.id == bookId }?.let(::syncBookSidecars)
    }

    fun openReader(book: BookItem) {
        if (book.type == FileType.PDF) {
            val path = book.path
            if (path.isNullOrBlank()) {
                updateState(state.withBanner("This PDF does not have a local path.", isError = true))
                return
            }
            val pdfFile = File(path)
            val pdfPath = pdfFile.absolutePath
            if (activePdfDocument?.path == pdfPath) {
                activeReaderBookId = book.id
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
            activePdfDocument?.close()
            activePdfDocument = null
            val pdf = runCatching {
                DesktopPdfium.load(pdfFile)
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

        val desktopReaderSurface = SharedFileCapabilities.surfaceFor(book.type, ReaderPlatform.DESKTOP)
        if (desktopReaderSurface != ReaderFeatureSurface.EPUB_READER && desktopReaderSurface != ReaderFeatureSurface.TEXT_READER) {
            updateState(
                state.withBanner(
                    "${SharedFileCapabilities.displayNameFor(book.type)} reader support comes later. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are available on desktop."
                )
            )
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
            bookmarks = book.readerBookmarks,
            highlights = book.readerHighlights
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

    DesktopFileDropTarget(
        window = window,
        onFilesDropped = ::importFiles,
        onDragStateChange = { dropImportState = it }
    )

    LaunchedEffect(Unit) {
        if (state.syncedFolders.isNotEmpty()) {
            syncLocalFolders(showBanner = false)
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { banner ->
            snackbarHostState.showSnackbar(banner.message)
            updateState(state.reduce(AppAction.BannerDismissed))
        }
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedAppShell(
                selectedTab = selectedTab,
                snackbarHostState = snackbarHostState,
                appThemeMode = state.appThemeMode,
                appContrastOption = state.appContrastOption,
                appTextDimFactorLight = state.appTextDimFactorLight,
                appTextDimFactorDark = state.appTextDimFactorDark,
                appSeedColor = state.appSeedColor,
                customAppThemes = state.customAppThemes,
                isTabsEnabled = state.isTabsEnabled,
                onTabSelected = { selectedTab = it },
                onImportFiles = { importFiles(chooseFiles()) },
                onImportFolder = { chooseFolder()?.let(::importFolder) },
                onSyncRequested = {
                    syncLocalFolders()
                },
                onAppThemeModeChange = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onAppContrastOptionChange = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onAppTextDimFactorLightChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onAppTextDimFactorDarkChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onAppSeedColorChange = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomAppThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomAppThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
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
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
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
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
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
                                    },
                                    onLocalSidecarsChanged = {
                                        activeReaderBookId
                                            ?.let { bookId -> state.rawLibraryBooks.firstOrNull { it.id == bookId } }
                                            ?.let(::syncBookSidecars)
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
                                    toolbarPreferences = state.readerToolbarPreferences,
                                    onToolbarPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                                    },
                                    highlightPalette = state.readerHighlightPalette,
                                    onHighlightPaletteChange = { palette ->
                                        updateState(state.reduce(AppAction.ReaderHighlightPaletteChanged(palette)))
                                    },
                                    onPickCustomFont = {
                                        chooseFontFile()?.toURI()?.toString()
                                    },
                                    webViewRuntimeState = webViewRuntimeState
                                )
                            }
                        }
                }
            }
            DesktopDropImportOverlay(dropImportState)
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

        if (showCreateSmartShelfDialog) {
            SmartShelfDialog(
                onDismiss = { showCreateSmartShelfDialog = false },
                onConfirm = { name, definition ->
                    createSmartShelf(name, definition)
                    showCreateSmartShelfDialog = false
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

private data class DesktopDropImportState(
    val active: Boolean = false,
    val supportedCount: Int = 0,
    val totalFileCount: Int = 0,
    val hasFilePayload: Boolean = false
)

@Composable
private fun DesktopFileDropTarget(
    window: Component?,
    onFilesDropped: (List<ImportedBookFile>) -> Unit,
    onDragStateChange: (DesktopDropImportState) -> Unit
) {
    val onFilesDroppedState = rememberUpdatedState(onFilesDropped)
    val onDragStateChangeState = rememberUpdatedState(onDragStateChange)

    DisposableEffect(window) {
        if (window == null) {
            onDispose { }
        } else {
            val installedTargets = mutableListOf<InstalledDropTarget>()
            var disposed = false
            val listener = object : DropTargetAdapter() {
                override fun dragEnter(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragOver(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragExit(event: DropTargetEvent) {
                    onDragStateChangeState.value(DesktopDropImportState())
                }

                override fun drop(event: DropTargetDropEvent) {
                    if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.rejectDrop()
                        onDragStateChangeState.value(DesktopDropImportState())
                        return
                    }
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = event.transferable.localDraggedFiles().filter { it.isFile }
                    if (files.isEmpty()) {
                        event.dropComplete(false)
                        onDragStateChangeState.value(DesktopDropImportState())
                        return
                    }

                    onFilesDroppedState.value(files.map { it.toImportedBookFile() })
                    event.dropComplete(true)
                    onDragStateChangeState.value(DesktopDropImportState())
                }

                private fun handleDrag(event: DropTargetDragEvent) {
                    val hasFilePayload = event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    val files = event.transferable.localDraggedFiles().filter { it.isFile }
                    val state = files.toDropImportState(active = true, hasFilePayload = hasFilePayload)
                    onDragStateChangeState.value(state)
                    if (hasFilePayload) {
                        event.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        event.rejectDrag()
                    }
                }
            }
            window.installDropTargets(listener, installedTargets)
            EventQueue.invokeLater {
                if (!disposed) {
                    window.installDropTargets(listener, installedTargets)
                }
            }

            onDispose {
                disposed = true
                installedTargets.forEach { installed ->
                    runCatching { installed.dropTarget.removeDropTargetListener(listener) }
                    installed.component.dropTarget = installed.previous
                }
                onDragStateChangeState.value(DesktopDropImportState())
            }
        }
    }
}

private data class InstalledDropTarget(
    val component: Component,
    val previous: DropTarget?,
    val dropTarget: DropTarget
)

private fun Component.installDropTargets(
    listener: DropTargetAdapter,
    installedTargets: MutableList<InstalledDropTarget>
) {
    collectDropTargetComponents()
        .distinct()
        .filterNot { component -> installedTargets.any { it.component == component } }
        .forEach { component ->
            val previous = component.dropTarget
            val target = DropTarget(component, DnDConstants.ACTION_COPY, listener, true)
            installedTargets += InstalledDropTarget(component, previous, target)
        }
}

private fun Component.collectDropTargetComponents(): List<Component> {
    val collected = mutableListOf<Component>()

    fun visit(component: Component) {
        collected += component
        if (component is Container) {
            component.components.forEach(::visit)
        }
    }

    visit(this)
    return collected
}

@Composable
private fun DesktopDropImportOverlay(state: DesktopDropImportState) {
    if (!state.active) return

    val hasSupportedFiles = state.supportedCount > 0
    val title = when {
        hasSupportedFiles -> "Drop to import ${state.supportedCount} file${if (state.supportedCount == 1) "" else "s"}"
        state.hasFilePayload -> "Drop supported files to import"
        else -> "Drop files to import"
    }
    val body = if (hasSupportedFiles) {
        val skipped = state.totalFileCount - state.supportedCount
        if (skipped > 0) {
            "$skipped unsupported file${if (skipped == 1) "" else "s"} will be skipped."
        } else {
            "Release to add to your library."
        }
    } else {
        SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun java.awt.datatransfer.Transferable.localDraggedFiles(): List<File> {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        (getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
            .orEmpty()
            .filterIsInstance<File>()
    }.getOrDefault(emptyList())
}

private fun List<File>.toDropImportState(
    active: Boolean,
    hasFilePayload: Boolean
): DesktopDropImportState {
    val localFiles = filter { it.isFile }
    val supportedCount = localFiles.count { SharedFileCapabilities.fileTypeForName(it.name) in DesktopReadableFileTypes }
    return DesktopDropImportState(
        active = active,
        supportedCount = supportedCount,
        totalFileCount = localFiles.size,
        hasFilePayload = hasFilePayload
    )
}

private fun BookItem.withDesktopImportMetadata(
    enriched: BookItem,
    original: BookItem?
): BookItem {
    fun shouldApplyText(current: String?, originalValue: String?): Boolean {
        return current.isNullOrBlank() || current == originalValue
    }

    return copy(
        title = if (shouldApplyText(title, original?.title)) {
            enriched.title ?: title
        } else {
            title
        },
        author = if (shouldApplyText(author, original?.author)) {
            enriched.author ?: author
        } else {
            author
        },
        fileSize = enriched.fileSize.takeIf { it > 0L } ?: fileSize,
        coverImagePath = coverImagePath?.takeIf { File(it).isFile } ?: enriched.coverImagePath,
        folderTextMetadataParsed = folderTextMetadataParsed || enriched.folderTextMetadataParsed
    )
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
    onCreateSmartShelf: () -> Unit,
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
        onCreateSmartShelf = onCreateSmartShelf,
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
    onCreateSmartShelf: () -> Unit,
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
        onCreateSmartShelf = onCreateSmartShelf,
        onRenameShelf = onRenameShelf,
        onDeleteShelf = onDeleteShelf,
        onRemoveFolder = onRemoveFolder
    )
}

private data class DesktopSmartRuleDraft(
    val field: SmartField = SmartField.TITLE,
    val operator: SmartOperator = SmartOperator.CONTAINS,
    val value: String = ""
) {
    fun toRule(): SmartRule? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return SmartRule(field = field, operator = operator, value = trimmed)
    }
}

@Composable
private fun SmartShelfDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SmartCollectionDefinition) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var matchAll by remember { mutableStateOf(true) }
    var rules by remember { mutableStateOf(listOf(DesktopSmartRuleDraft())) }
    val validRules = rules.mapNotNull { it.toRule() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create smart shelf") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shelf name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = matchAll,
                        onClick = { matchAll = true },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = !matchAll,
                        onClick = { matchAll = false },
                        label = { Text("Any") }
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { rules = rules + DesktopSmartRuleDraft() },
                        enabled = rules.size < 4
                    ) {
                        Text("Add rule")
                    }
                }
                rules.forEachIndexed { index, draft ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmartRuleDropdown(
                                label = "Field",
                                selected = draft.field,
                                options = SmartField.entries.toList(),
                                optionLabel = { it.desktopLabel() },
                                onSelected = { field ->
                                    rules = rules.updateAt(index) {
                                        val operator = smartOperatorsFor(field).first()
                                        copy(field = field, operator = operator, value = "")
                                    }
                                }
                            )
                            SmartRuleDropdown(
                                label = "Operator",
                                selected = draft.operator,
                                options = smartOperatorsFor(draft.field),
                                optionLabel = { it.desktopLabel() },
                                onSelected = { operator ->
                                    rules = rules.updateAt(index) { copy(operator = operator) }
                                }
                            )
                            if (rules.size > 1) {
                                TextButton(onClick = { rules = rules.filterIndexed { i, _ -> i != index } }) {
                                    Text("Remove")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = draft.value,
                            onValueChange = { value -> rules = rules.updateAt(index) { copy(value = value) } },
                            label = { Text(draft.field.valueLabel()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, SmartCollectionDefinition(matchAll = matchAll, rules = validRules))
                },
                enabled = name.isNotBlank() && validRules.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun <T> SmartRuleDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label: ${optionLabel(selected)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun smartOperatorsFor(field: SmartField): List<SmartOperator> {
    return when (field) {
        SmartField.PROGRESS -> listOf(SmartOperator.GREATER_THAN, SmartOperator.LESS_THAN, SmartOperator.EQUALS)
        else -> listOf(SmartOperator.CONTAINS, SmartOperator.EQUALS)
    }
}

private fun SmartField.desktopLabel(): String {
    return when (this) {
        SmartField.TITLE -> "Title"
        SmartField.AUTHOR -> "Author"
        SmartField.PROGRESS -> "Progress"
        SmartField.FILE_TYPE -> "File type"
        SmartField.FOLDER -> "Folder"
        SmartField.TAG -> "Tag"
    }
}

private fun SmartField.valueLabel(): String {
    return when (this) {
        SmartField.PROGRESS -> "Percent"
        SmartField.FILE_TYPE -> "Type, e.g. PDF"
        SmartField.FOLDER -> "Folder path"
        SmartField.TAG -> "Tag name"
        SmartField.TITLE -> "Title text"
        SmartField.AUTHOR -> "Author text"
    }
}

private fun SmartOperator.desktopLabel(): String {
    return when (this) {
        SmartOperator.EQUALS -> "Equals"
        SmartOperator.CONTAINS -> "Contains"
        SmartOperator.GREATER_THAN -> "Greater than"
        SmartOperator.LESS_THAN -> "Less than"
    }
}

private inline fun List<DesktopSmartRuleDraft>.updateAt(
    index: Int,
    transform: DesktopSmartRuleDraft.() -> DesktopSmartRuleDraft
): List<DesktopSmartRuleDraft> {
    return mapIndexed { i, draft -> if (i == index) draft.transform() else draft }
}

private val DesktopPdfAnnotationTools = listOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
    PdfInkTool.TEXT,
    PdfInkTool.ERASER
)

private val PdfInkTool.isDesktopHighlighter: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

private fun List<PdfPagePoint>.withDesktopPdfDragPoint(
    point: Offset,
    canvasSize: IntSize,
    tool: PdfInkTool,
    snapHighlighter: Boolean,
    timestamp: Long
): List<PdfPagePoint> {
    val nextPoint = point.toSharedPdfPoint(canvasSize, timestamp)
    if (snapHighlighter && tool.isDesktopHighlighter && isNotEmpty()) {
        val pageAspectRatio = canvasSize.width.toFloat() / canvasSize.height.coerceAtLeast(1).toFloat()
        return listOf(
            first(),
            SharedPdfInkRenderer.calculateSnappedPoint(
                currentPoint = nextPoint,
                startPoint = first(),
                pageAspectRatio = pageAspectRatio
            )
        )
    }
    return this + nextPoint
}

@Composable
private fun PdfReaderScreen(
    document: DesktopPdfDocument,
    initialPageIndex: Int,
    onOpenPdf: () -> Unit,
    onOpenEpub: () -> Unit,
    onPageStateChange: (pageIndex: Int, progress: Float) -> Unit,
    onLocalSidecarsChanged: () -> Unit = {}
) {
    val zoomSpec = remember { PdfZoomSpec() }
    var pdfState by remember(document.path) {
        val defaultTool = PdfInkTool.PEN
        val defaultToolConfig = SharedPdfAnnotationDefaults.configFor(defaultTool)
        mutableStateOf(
            SharedPdfReaderState.initial(
                pageCount = document.pageCount,
                initialPageIndex = initialPageIndex,
                zoomSpec = zoomSpec
            ).copy(
                isTextSelectionMode = true,
                selectedTool = defaultTool,
                selectedColorArgb = defaultToolConfig.colorArgb,
                strokeWidth = defaultToolConfig.strokeWidth
            )
        )
    }
    var renderedPage by remember(document.path) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderError by remember(document.path) { mutableStateOf<String?>(null) }
    var isRendering by remember(document.path) { mutableStateOf(false) }
    var renderJob by remember(document.path) { mutableStateOf<Job?>(null) }
    var activeTextDraft by remember(document.path) { mutableStateOf<SharedPdfTextDraft?>(null) }
    var textStyleConfig by remember(document.path) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var pageCanvasSize by remember(document.path) { mutableStateOf(IntSize.Zero) }
    var activeStroke by remember(document.path, pdfState.pageIndex) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    var isHighlighterSnapEnabled by remember(document.path) { mutableStateOf(false) }
    var selectionStartIndex by remember(document.path, pdfState.pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(document.path, pdfState.pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(document.path, pdfState.pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(document.path, pdfState.pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(document.path, pdfState.pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(document.path, pdfState.pageIndex) { mutableStateOf<Offset?>(null) }
    var pageScrubPreview by remember(document.path) { mutableStateOf<Int?>(null) }
    var pageScrubStartPage by remember(document.path) { mutableStateOf<Int?>(null) }
    var jumpHistory by remember(document.path) { mutableStateOf(SharedPdfJumpHistory()) }
    var externalLinkDialogUrl by remember(document.path) { mutableStateOf<String?>(null) }
    val annotationFile = remember(document.path) { desktopPdfAnnotationFile(document.path) }
    val bookmarkFile = remember(document.path) { desktopPdfBookmarkFile(document.path) }
    val richTextFile = remember(document.path) { desktopPdfRichTextFile(document.path) }
    val searchIndexFile = remember(document.path) { desktopPdfSearchIndexFile(document.path) }
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val pdfScope = rememberCoroutineScope()
    var isRichTextMode by remember(document.path) { mutableStateOf(false) }
    var isRichTextLoaded by remember(document.path) { mutableStateOf(false) }
    val richTextController = remember(document.path) {
        SharedPdfRichTextController(
            scope = pdfScope,
            onDocumentChange = { richDocument ->
                if (isRichTextLoaded) {
                    withContext(Dispatchers.IO) {
                        richTextFile.parentFile?.mkdirs()
                        richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
                    }
                    onLocalSidecarsChanged()
                }
            }
        )
    }
    val pageVerticalScrollState = rememberScrollState()
    val pageHorizontalScrollState = rememberScrollState()
    val verticalListState = rememberLazyListState(initialFirstVisibleItemIndex = pdfState.pageIndex)
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentPdfAnnotations by rememberUpdatedState(pdfState.annotations)
    val currentPdfPageIndex by rememberUpdatedState(pdfState.pageIndex)

    fun clearPdfInteractionState() {
        activeStroke = emptyList()
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
    }

    fun dispatchPdf(action: SharedPdfReaderAction) {
        val previousPage = pdfState.pageIndex
        val next = pdfState.reduce(action, zoomSpec)
        pdfState = next
        if (next.pageIndex != previousPage) {
            clearPdfInteractionState()
        }
    }

    fun commitActiveTextDraft() {
        val draft = activeTextDraft ?: return
        activeTextDraft = null
        val annotation = draft.toAnnotation()
        if (annotation.text.isNotEmpty()) {
            dispatchPdf(SharedPdfReaderAction.AnnotationAdded(annotation))
        }
    }

    fun persistActiveTextDraftIfReady(draft: SharedPdfTextDraft) {
        val annotation = draft.toAnnotation()
        if (annotation.text.isNotEmpty()) {
            activeTextDraft = null
            textStyleConfig = draft.style
            dispatchPdf(SharedPdfReaderAction.AnnotationAdded(annotation))
        } else {
            activeTextDraft = draft
        }
    }

    fun startActiveTextDraft(pageIndex: Int, anchor: Offset, canvasSize: IntSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        commitActiveTextDraft()
        clearPdfInteractionState()
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
        val now = System.currentTimeMillis()
        activeTextDraft = SharedPdfTextAnnotationDefaults.createDraft(
            id = "text_$now",
            pageIndex = pageIndex,
            anchor = anchor.toSharedPdfPoint(canvasSize, now),
            canvasSize = canvasSize,
            style = textStyleConfig,
            createdAt = now
        )
    }

    fun updateActiveTextDraft(text: String, canvasSize: IntSize) {
        activeTextDraft?.withText(text, canvasSize)?.let(::persistActiveTextDraftIfReady)
    }

    fun updateActiveTextDraftBounds(bounds: PdfPageBounds) {
        activeTextDraft = activeTextDraft?.withBounds(bounds)
    }

    fun activeTextDraftContains(pageIndex: Int, offset: Offset, canvasSize: IntSize): Boolean {
        return activeTextDraft?.containsOffset(pageIndex, offset, canvasSize) == true
    }

    fun updateTextStyleConfig(style: SharedPdfTextStyleConfig) {
        textStyleConfig = style
        val draft = activeTextDraft
        if (draft != null) {
            activeTextDraft = if (draft.pageIndex == pdfState.pageIndex && pageCanvasSize.width > 0 && pageCanvasSize.height > 0) {
                draft.withStyle(style, pageCanvasSize)
            } else {
                draft.copy(style = style)
            }
            return
        }

        val selectedTextAnnotation = pdfState.annotations.firstOrNull {
            it.id == pdfState.selectedAnnotationId && it.kind == PdfAnnotationKind.TEXT
        }
        if (selectedTextAnnotation != null) {
            dispatchPdf(SharedPdfReaderAction.AnnotationUpdated(selectedTextAnnotation.withSharedPdfTextStyle(style)))
        }
    }

    fun selectTextAnnotation(annotation: SharedPdfAnnotation) {
        if (annotation.kind != PdfAnnotationKind.TEXT) return
        if (isRichTextMode) {
            isRichTextMode = false
            pdfScope.launch { richTextController.saveImmediate() }
        }
        commitActiveTextDraft()
        clearPdfInteractionState()
        textStyleConfig = annotation.sharedPdfTextStyle()
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(annotation.id))
    }

    fun activateRichTextMode() {
        commitActiveTextDraft()
        clearPdfInteractionState()
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
        if (pdfState.isTextSelectionMode) {
            dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(false))
        }
        isRichTextMode = true
    }

    fun deactivateRichTextMode(save: Boolean = true) {
        if (!isRichTextMode) return
        isRichTextMode = false
        if (save) {
            pdfScope.launch { richTextController.saveImmediate() }
        } else {
            richTextController.clearSelection()
        }
    }

    fun selectPdfAnnotationTool(tool: PdfInkTool) {
        deactivateRichTextMode()
        if (tool != PdfInkTool.TEXT) {
            commitActiveTextDraft()
        }
        if (tool == PdfInkTool.TEXT && pdfState.isTextSelectionMode) {
            dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(false))
            clearPdfInteractionState()
        }
        dispatchPdf(SharedPdfReaderAction.ToolSelected(tool))
    }

    val pageIndex = pdfState.pageIndex
    val scale = pdfState.zoom
    val displayMode = pdfState.displayMode
    val searchQuery = pdfState.searchQuery
    val activeSearchIndex = pdfState.activeSearchResultIndex
    val searchHighlightMode = pdfState.searchHighlightMode
    val selectedTool = pdfState.selectedTool
    val selectedColor = pdfState.selectedColorArgb
    val strokeWidth = pdfState.strokeWidth
    val isTextSelectionMode = pdfState.isTextSelectionMode
    val bookmarks = pdfState.bookmarks
    val selectedAnnotationId = pdfState.selectedAnnotationId
    val annotations = pdfState.annotations
    val canGoPrevious = pdfState.canGoPrevious
    val canGoNext = pdfState.canGoNext
    val progressPercent = pdfState.progressPercent
    val verticalRenderWindow = remember(pageIndex, document.pageCount) {
        val start = (pageIndex - 1).coerceAtLeast(0)
        val end = (pageIndex + 1).coerceAtMost((document.pageCount - 1).coerceAtLeast(0))
        start..end
    }
    var arePdfAnnotationsLoaded by remember(document.path) { mutableStateOf(false) }
    var arePdfBookmarksLoaded by remember(document.path) { mutableStateOf(false) }
    var indexedSearchPageCount by remember(document.path) { mutableStateOf(document.indexedSearchTextPageCount()) }
    var isSearchIndexing by remember(document.path) { mutableStateOf(false) }
    var searchResults by remember(document.path) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var selectedEmbeddedAnnotationId by remember(document.path) { mutableStateOf<String?>(null) }
    val selectedAnnotation = remember(annotations, selectedAnnotationId) {
        annotations.firstOrNull { it.id == selectedAnnotationId }
    }
    val sortedAnnotations = remember(annotations) {
        annotations.sortedWith(compareBy<SharedPdfAnnotation> { it.pageIndex }.thenBy { it.createdAt })
    }
    val sortedEmbeddedAnnotations = remember(document.embeddedAnnotations) {
        document.embeddedAnnotations.sortedWith(compareBy<SharedPdfEmbeddedAnnotation> { it.pageIndex }.thenBy { it.index })
    }
    val selectedEmbeddedAnnotation = remember(document.embeddedAnnotations, selectedEmbeddedAnnotationId) {
        document.embeddedAnnotations.firstOrNull { it.id == selectedEmbeddedAnnotationId }
    }
    val effectiveTextStyleConfig = remember(activeTextDraft, selectedAnnotation, textStyleConfig) {
        activeTextDraft?.style
            ?: selectedAnnotation?.takeIf { it.kind == PdfAnnotationKind.TEXT }?.sharedPdfTextStyle()
            ?: textStyleConfig
    }

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    LaunchedEffect(document.path) {
        arePdfAnnotationsLoaded = false
        val loadedAnnotations = if (annotationFile.exists()) {
            withContext(Dispatchers.IO) {
                SharedPdfAnnotationSerializer.decode(annotationFile.readText())
            }
        } else {
            emptyList()
        }
        dispatchPdf(SharedPdfReaderAction.AnnotationsLoaded(loadedAnnotations))
        arePdfAnnotationsLoaded = true
    }

    LaunchedEffect(document.path, annotations, arePdfAnnotationsLoaded) {
        if (!arePdfAnnotationsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                annotationFile.parentFile?.mkdirs()
                annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
            }
        }
        onLocalSidecarsChanged()
    }

    LaunchedEffect(document.path) {
        isRichTextLoaded = false
        val loadedRichText = withContext(Dispatchers.IO) {
            if (richTextFile.exists()) {
                SharedPdfRichTextSerializer.decode(richTextFile.readText())
            } else {
                SharedPdfRichDocument()
            }
        }
        richTextController.replaceDocument(loadedRichText)
        isRichTextLoaded = true
    }

    LaunchedEffect(document.path) {
        arePdfBookmarksLoaded = false
        val loadedBookmarks = if (bookmarkFile.exists()) {
            withContext(Dispatchers.IO) {
                SharedPdfBookmarkSerializer.decode(bookmarkFile.readText())
            }
        } else {
            emptyList()
        }
        dispatchPdf(SharedPdfReaderAction.BookmarksLoaded(loadedBookmarks))
        arePdfBookmarksLoaded = true
    }

    LaunchedEffect(document.path, bookmarks, arePdfBookmarksLoaded) {
        if (!arePdfBookmarksLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                bookmarkFile.parentFile?.mkdirs()
                bookmarkFile.writeText(SharedPdfBookmarkSerializer.encode(bookmarks))
            }
        }
        onLocalSidecarsChanged()
    }

    LaunchedEffect(document.path) {
        val restoredPageCount = withContext(Dispatchers.IO) {
            restoreDesktopPdfSearchIndex(document, searchIndexFile)
        }
        indexedSearchPageCount = restoredPageCount
        isSearchIndexing = indexedSearchPageCount < document.pageCount
        withContext(Dispatchers.IO) {
            DesktopPdfium.indexSearchPages(
                document = document,
                onProgress = { indexed, _ ->
                    indexedSearchPageCount = indexed
                },
                shouldContinue = { isActive }
            )
            if (isActive) {
                saveDesktopPdfSearchIndex(document, searchIndexFile)
            }
        }
        if (!isActive) return@LaunchedEffect
        indexedSearchPageCount = document.indexedSearchTextPageCount()
        isSearchIndexing = false
    }

    LaunchedEffect(document.path, searchQuery, indexedSearchPageCount) {
        val normalizedQuery = searchQuery.trim()
        searchResults = if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                DesktopPdfium.search(document, normalizedQuery)
            }
        }
    }

    fun goToPage(target: Int, scrollVertical: Boolean = true, recordJump: Boolean = false) {
        val clampedTarget = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        val currentPage = pdfState.pageIndex
        if (clampedTarget != currentPage) {
            commitActiveTextDraft()
            if (isRichTextMode) {
                pdfScope.launch { richTextController.saveImmediate() }
            }
        }
        if (recordJump) {
            jumpHistory = jumpHistory.record(
                currentPageIndex = currentPage,
                targetPageIndex = clampedTarget,
                pageCount = document.pageCount
            )
        }
        dispatchPdf(SharedPdfReaderAction.GoToPage(clampedTarget))
        if (scrollVertical && displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
            pdfScope.launch {
                verticalListState.scrollToItem(clampedTarget)
            }
        }
    }

    fun goBackInJumpHistory() {
        val targetPage = jumpHistory.backPage ?: return
        jumpHistory = jumpHistory.stepBack()
        goToPage(targetPage)
    }

    fun goForwardInJumpHistory() {
        val targetPage = jumpHistory.forwardPage ?: return
        jumpHistory = jumpHistory.stepForward()
        goToPage(targetPage)
    }

    fun activatePdfLink(target: DesktopPdfLinkTarget) {
        target.destPageIndex
            ?.takeIf { it in 0 until document.pageCount }
            ?.let {
                logPdfLink("activate_internal fromPage=${pageIndex + 1} targetPage=${it + 1}")
                clearPdfInteractionState()
                goToPage(it, recordJump = true)
                return
            }
        target.uri
            ?.takeIf { it.isNotBlank() }
            ?.let {
                val url = it.normalizedExternalUrl()
                logPdfLink("activate_external fromPage=${pageIndex + 1} url=\"${url.logPreview()}\"")
                clearPdfInteractionState()
                externalLinkDialogUrl = url
                return
            }
        logPdfLink(
            "activate_ignored fromPage=${pageIndex + 1} " +
                "dest=${target.destPageIndex} uri=\"${target.uri.orEmpty().logPreview()}\""
        )
    }

    fun toggleBookmark(targetPage: Int) {
        val page = targetPage.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        dispatchPdf(
            SharedPdfReaderAction.BookmarkToggled(
                pageIndex = page,
                label = "Page ${page + 1}",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun copySelection(selection: DesktopPdfTextSelection) {
        selection.text.takeIf { it.isNotBlank() }?.let {
            clipboardManager.setText(AnnotatedString(it))
        }
    }

    fun highlightSelection(pageIndex: Int, selection: DesktopPdfTextSelection, canvasSize: IntSize) {
        val now = System.currentTimeMillis()
        val highlightBounds = DesktopPdfium.textRectsForRange(
            document = document,
            pageIndex = pageIndex,
            startIndex = selection.startIndex,
            endIndex = selection.endIndex,
            viewportWidth = canvasSize.width,
            viewportHeight = canvasSize.height
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
        dispatchPdf(
            SharedPdfReaderAction.AnnotationAdded(
                SharedPdfAnnotation(
                    id = "highlight_${now}",
                    pageIndex = pageIndex,
                    kind = PdfAnnotationKind.HIGHLIGHT,
                    tool = PdfInkTool.HIGHLIGHTER,
                    bounds = highlightBounds.firstOrNull(),
                    boundsList = highlightBounds,
                    text = selection.text,
                    colorArgb = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).colorArgb,
                    rangeStartIndex = selection.startIndex,
                    rangeEndIndex = selection.endIndex,
                    createdAt = now
                )
            )
        )
    }

    fun clearSelection() {
        textSelection = null
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        selectionMenuOffset = null
    }

    fun highlightCurrentSelection() {
        val selection = textSelection ?: return
        highlightSelection(pageIndex, selection, pageCanvasSize)
        clearSelection()
    }

    fun searchSelection(selection: DesktopPdfTextSelection) {
        dispatchPdf(SharedPdfReaderAction.SearchChanged(selection.text.take(120)))
    }

    fun translateSelection(selection: DesktopPdfTextSelection) {
        openExternalUrl("https://translate.google.com/?sl=auto&tl=en&text=${selection.text.urlEncode()}&op=translate")
    }

    fun updateAnnotation(annotation: SharedPdfAnnotation) {
        dispatchPdf(SharedPdfReaderAction.AnnotationUpdated(annotation))
    }

    fun deleteAnnotation(annotationId: String) {
        dispatchPdf(SharedPdfReaderAction.AnnotationDeleted(annotationId))
    }

    fun selectAnnotation(annotation: SharedPdfAnnotation?) {
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(annotation?.id))
        annotation?.let { goToPage(it.pageIndex, recordJump = true) }
    }

    fun selectEmbeddedAnnotation(annotation: SharedPdfEmbeddedAnnotation?) {
        selectedEmbeddedAnnotationId = annotation?.id
        annotation?.let { goToPage(it.pageIndex, recordJump = true) }
    }

    fun goToSearchResult(targetIndex: Int) {
        if (searchResults.isEmpty()) return
        val normalizedIndex = when {
            targetIndex < 0 -> searchResults.lastIndex
            targetIndex > searchResults.lastIndex -> 0
            else -> targetIndex
        }
        val targetPage = searchResults[normalizedIndex].pageIndex
        jumpHistory = jumpHistory.record(
            currentPageIndex = pdfState.pageIndex,
            targetPageIndex = targetPage,
            pageCount = document.pageCount
        )
        if (targetPage != pdfState.pageIndex) {
            commitActiveTextDraft()
        }
        dispatchPdf(SharedPdfReaderAction.GoToSearchResult(targetIndex, searchResults))
        if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
            pdfScope.launch {
                verticalListState.scrollToItem(targetPage)
            }
        }
    }

    LaunchedEffect(document.path, document.pageCount) {
        jumpHistory = jumpHistory.pruned(document.pageCount)
    }

    LaunchedEffect(document.path, pageIndex, progressPercent) {
        onPageStateChange(pageIndex, progressPercent)
    }

    LaunchedEffect(document.path, displayMode) {
        if (displayMode == PdfDisplayMode.VERTICAL_SCROLL && pageIndex in 0 until document.pageCount) {
            verticalListState.scrollToItem(pageIndex)
        }
    }

    LaunchedEffect(document.path, displayMode, verticalListState) {
        if (displayMode != PdfDisplayMode.VERTICAL_SCROLL) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = verticalListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                verticalListState.firstVisibleItemIndex
            } else {
                mostVisiblePdfPageIndex(
                    visiblePages = visibleItems.map { item ->
                        PdfVisiblePageLayout(
                            pageIndex = item.index,
                            top = item.offset.toFloat(),
                            bottom = (item.offset + item.size).toFloat()
                        )
                    },
                    viewportTop = layoutInfo.viewportStartOffset.toFloat(),
                    viewportBottom = layoutInfo.viewportEndOffset.toFloat(),
                    fallbackPageIndex = verticalListState.firstVisibleItemIndex
                )
            }
        }
            .distinctUntilChanged()
            .collect { visiblePage ->
                if (visiblePage in 0 until document.pageCount && visiblePage != currentPdfPageIndex) {
                    goToPage(visiblePage, scrollVertical = false)
                }
            }
    }

    LaunchedEffect(document.path, pageIndex, scale, displayMode) {
        renderJob?.cancel()
        if (displayMode != PdfDisplayMode.PAGINATION) {
            isRendering = false
            renderError = null
            renderedPage = null
            return@LaunchedEffect
        }
        renderJob = launch {
            delay(90)
            isRendering = true
            renderError = null
            val pageSize = document.pageSizes[pageIndex]
            val safeScale = zoomSpec.safeRenderScale(
                pageSize.width,
                pageSize.height, scale
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DesktopPdfium.renderPage(document, pageIndex, safeScale)
                }
            }
            if (pageIndex != pageIndex || scale != scale) {
                return@launch
            }
            renderedPage = result.getOrNull()
            renderError = result.exceptionOrNull()?.message
                ?: if (renderedPage == null) "Failed to render page." else null
            renderedPage?.let { render ->
                logPdfSelection(
                    "render page=${pageIndex + 1} " +
                        "requestedScale=${scale.formatLogFloat()} safeScale=${safeScale.formatLogFloat()} " +
                        "pageSize=${pageSize.width.formatLogFloat()}x${pageSize.height.formatLogFloat()} " +
                        "bitmap=${render.width}x${render.height} capped=${safeScale < zoomSpec.clamp(
                            scale
                        )}"
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
                Text("${progressPercent.toInt()}%")
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedPdfRichTextHiddenInput(
                controller = richTextController,
                enabled = isRichTextMode,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp)
                    .zIndex(10f)
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val isEditingTextAnnotation =
                            activeTextDraft != null ||
                                (selectedTool == PdfInkTool.TEXT && selectedAnnotation?.kind == PdfAnnotationKind.TEXT)
                        if ((isEditingTextAnnotation || isRichTextMode) && !event.isCtrlPressed) {
                            return@onPreviewKeyEvent false
                        }
                        when {
                            event.key == Key.DirectionLeft -> {
                                goToPage(pageIndex - 1)
                                true
                            }
                            event.key == Key.DirectionRight -> {
                                goToPage(pageIndex + 1)
                                true
                            }
                            event.key == Key.DirectionUp && displayMode == PdfDisplayMode.VERTICAL_SCROLL -> {
                                goToPage(pageIndex - 1)
                                true
                            }
                            event.key == Key.DirectionDown && displayMode == PdfDisplayMode.VERTICAL_SCROLL -> {
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
                                dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f))
                                true
                            }
                            event.isCtrlPressed && event.key == Key.Minus -> {
                                dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f))
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
                            FilterChip(
                                selected = displayMode == PdfDisplayMode.PAGINATION,
                                onClick = {
                                    commitActiveTextDraft()
                                    dispatchPdf(SharedPdfReaderAction.DisplayModeChanged(PdfDisplayMode.PAGINATION))
                                },
                                label = { Text("Page") }
                            )
                            FilterChip(
                                selected = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                                onClick = {
                                    commitActiveTextDraft()
                                    dispatchPdf(SharedPdfReaderAction.DisplayModeChanged(PdfDisplayMode.VERTICAL_SCROLL))
                                },
                                label = { Text("Scroll") }
                            )
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { goToPage(0) }, enabled = canGoPrevious) {
                                    Text("First")
                                }
                                TextButton(onClick = { goToPage(pageIndex - 1) }, enabled = canGoPrevious) {
                                    Text("Prev")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { goToPage(pageIndex + 1) }, enabled = canGoNext) {
                                    Text("Next")
                                }
                                TextButton(onClick = { goToPage(document.pageCount - 1) }, enabled = canGoNext) {
                                    Text("Last")
                                }
                            }
                        }
                    }
                    item {
                        DesktopPdfJumpHistoryControls(
                            backPage = jumpHistory.backPage,
                            forwardPage = jumpHistory.forwardPage,
                            onBack = ::goBackInJumpHistory,
                            onForward = ::goForwardInJumpHistory,
                            onClear = { jumpHistory = jumpHistory.clear() }
                        )
                    }
                    if (document.pageCount > 1) {
                        item {
                            Text("Page ${pageIndex + 1} of ${document.pageCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = pageIndex.toFloat(),
                                onValueChange = { value ->
                                    if (pageScrubStartPage == null) {
                                        pageScrubStartPage = pdfState.pageIndex
                                    }
                                    val targetPage = value.toInt().coerceIn(0, document.pageCount - 1)
                                    pageScrubPreview = targetPage
                                    goToPage(targetPage)
                                },
                                onValueChangeFinished = {
                                    val startPage = pageScrubStartPage
                                    val targetPage = currentPdfPageIndex
                                    if (startPage != null) {
                                        jumpHistory = jumpHistory.record(
                                            currentPageIndex = startPage,
                                            targetPageIndex = targetPage,
                                            pageCount = document.pageCount
                                        )
                                    }
                                    pageScrubStartPage = null
                                    pageScrubPreview = null
                                },
                                valueRange = 0f..(document.pageCount - 1).toFloat(),
                                steps = (document.pageCount - 2).coerceAtLeast(0)
                            )
                        }
                    }
                    item {
                        val isBookmarked = bookmarks.any { it.pageIndex == pageIndex }
                        TextButton(onClick = { toggleBookmark(pageIndex) }) {
                            Text(if (isBookmarked) "Remove bookmark" else "Bookmark page")
                        }
                    }
                    if (bookmarks.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(bookmarks, key = { "bookmark_${it.pageIndex}" }) { bookmark ->
                            Surface(
                                color = if (bookmark.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().clickable { goToPage(bookmark.pageIndex, recordJump = true) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { toggleBookmark(bookmark.pageIndex) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                    if (document.toc.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        itemsIndexed(document.toc, key = { index, entry -> "toc_${index}_${entry.pageIndex}_${entry.nestLevel}" }) { _, entry ->
                            Surface(
                                color = if (entry.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().clickable { goToPage(entry.pageIndex, recordJump = true) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = (entry.nestLevel * 12).dp)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        entry.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("p. ${entry.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item {
                        Text("Zoom", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f)) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                            }
                            Text("${(scale * 100).toInt()}%", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f)) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                            }
                        }
                        Slider(
                            value = scale,
                            onValueChange = { dispatchPdf(SharedPdfReaderAction.ZoomChanged(it)) },
                            valueRange = zoomSpec.min..zoomSpec.max
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Annotations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FilterChip(
                            selected = isTextSelectionMode,
                            onClick = {
                                val enabled = !isTextSelectionMode
                                if (enabled) {
                                    deactivateRichTextMode()
                                }
                                if (enabled) {
                                    commitActiveTextDraft()
                                }
                                dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(enabled))
                                if (!enabled) {
                                    clearPdfInteractionState()
                                }
                            },
                            label = { Text("Select text") }
                        )
                        FilterChip(
                            selected = isRichTextMode,
                            onClick = {
                                if (isRichTextMode) {
                                    deactivateRichTextMode()
                                } else {
                                    activateRichTextMode()
                                }
                            },
                            label = { Text("Document text") }
                        )
                        SharedPdfAnnotationToolDock(
                            selectedTool = selectedTool,
                            selectedColor = selectedColor,
                            strokeWidth = strokeWidth,
                            tools = DesktopPdfAnnotationTools,
                            onToolSelected = ::selectPdfAnnotationTool,
                            onColorSelected = { dispatchPdf(SharedPdfReaderAction.ColorSelected(it)) },
                            onStrokeWidthChange = { dispatchPdf(SharedPdfReaderAction.StrokeWidthChanged(it)) },
                            onUndo = {
                                dispatchPdf(SharedPdfReaderAction.UndoLastAnnotationOnPage(pageIndex))
                            },
                            onClearPage = {
                                dispatchPdf(SharedPdfReaderAction.ClearPageAnnotations(pageIndex))
                            },
                            isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                            onHighlighterSnapChange = { isHighlighterSnapEnabled = it }
                        )
                    }
                    selectedAnnotation?.let { annotation ->
                        item {
                            DesktopPdfAnnotationEditor(
                                annotation = annotation,
                                onUpdate = ::updateAnnotation,
                                onDelete = { deleteAnnotation(annotation.id) },
                                onClose = { dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null)) }
                            )
                        }
                    }
                    if (sortedAnnotations.isNotEmpty()) {
                        item {
                            Text("Annotation list", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        items(sortedAnnotations, key = { "annotation_${it.id}" }) { annotation ->
                            Surface(
                                color = if (annotation.id == selectedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().clickable { selectAnnotation(annotation) }
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            annotation.desktopLabel(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { deleteAnnotation(annotation.id) }) {
                                            Text("Delete")
                                        }
                                    }
                                    Text(
                                        "Page ${annotation.pageIndex + 1}${annotation.text.takeIf { it.isNotBlank() }?.let { " - ${it.logPreview(48)}" }.orEmpty()}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    selectedEmbeddedAnnotation?.let { annotation ->
                        item {
                            DesktopPdfEmbeddedAnnotationPanel(
                                annotation = annotation,
                                onCopy = { clipboardManager.setText(AnnotatedString(annotation.threadText())) },
                                onClose = { selectedEmbeddedAnnotationId = null }
                            )
                        }
                    }
                    if (sortedEmbeddedAnnotations.isNotEmpty()) {
                        item {
                            Text("PDF comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        items(sortedEmbeddedAnnotations, key = { "embedded_${it.id}" }) { annotation ->
                            Surface(
                                color = if (annotation.id == selectedEmbeddedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().clickable { selectEmbeddedAnnotation(annotation) }
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            annotation.author.ifBlank { "PDF comment" },
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("p. ${annotation.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        annotation.contents.ifBlank { annotation.replies.firstOrNull()?.contents.orEmpty() }.logPreview(80),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (annotation.replies.isNotEmpty()) {
                                        Text(
                                            "${annotation.replies.size} replies",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isRichTextMode || selectedTool == PdfInkTool.TEXT) {
                        item {
                            SharedPdfTextAnnotationDock(
                                style = if (isRichTextMode) {
                                    richTextController.currentSharedPdfTextStyleConfig()
                                } else {
                                    effectiveTextStyleConfig
                                },
                                onStyleChange = { style ->
                                    if (isRichTextMode) {
                                        richTextController.updateCurrentSharedPdfTextStyle(style)
                                    } else {
                                        updateTextStyleConfig(style)
                                    }
                                }
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
                                dispatchPdf(SharedPdfReaderAction.SearchChanged(it))
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
                                        isSearchIndexing -> {
                                            val progress = "Indexing ${indexedSearchPageCount.coerceAtMost(document.pageCount)}/${document.pageCount}"
                                            if (searchResults.isEmpty()) progress else "${searchResults.size} matches - $progress"
                                        }
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Highlights",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        dispatchPdf(SharedPdfReaderAction.SearchHighlightModeToggled)
                                    },
                                    enabled = searchResults.isNotEmpty()
                                ) {
                                    Text(
                                        when (searchHighlightMode) {
                                            SearchHighlightMode.ALL -> "All"
                                            SearchHighlightMode.FOCUSED -> "Focused"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    items(searchResults, key = { "${it.pageIndex}_${it.matchIndex}_${it.preview}" }) { result ->
                        Surface(
                            color = if (result.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                goToSearchResult(searchResults.indexOf(result))
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

            if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFE8E5DC), RoundedCornerShape(8.dp))
                ) {
                    LazyColumn(
                        state = verticalListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(pageHorizontalScrollState)
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items((0 until document.pageCount).toList(), key = { it }) { verticalPageIndex ->
                            DesktopVerticalPdfPage(
                                document = document,
                                pageIndex = verticalPageIndex,
                                scale = scale,
                                zoomSpec = zoomSpec,
                                annotations = annotations,
                                searchResults = searchResults,
                                activeSearchIndex = activeSearchIndex,
                                searchHighlightMode = searchHighlightMode,
                                searchQuery = searchQuery,
                                isTextSelectionMode = isTextSelectionMode,
                                selectedAnnotationId = selectedAnnotationId,
                                selectedEmbeddedAnnotationId = selectedEmbeddedAnnotationId,
                                selectedTool = selectedTool,
                                selectedColor = selectedColor,
                                strokeWidth = strokeWidth,
                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                activeTextDraft = activeTextDraft,
                                richTextController = richTextController,
                                isRichTextMode = isRichTextMode,
                                shouldRender = verticalPageIndex in verticalRenderWindow,
                                onSelectPage = { goToPage(it, scrollVertical = false) },
                                onCopySelection = ::copySelection,
                                onHighlightSelection = ::highlightSelection,
                                onSearchSelection = ::searchSelection,
                                onTranslateSelection = ::translateSelection,
                                onEmbeddedAnnotationSelected = ::selectEmbeddedAnnotation,
                                onLinkActivated = ::activatePdfLink,
                                onAnnotationAdded = { dispatchPdf(SharedPdfReaderAction.AnnotationAdded(it)) },
                                onAnnotationUpdated = ::updateAnnotation,
                                onAnnotationsChanged = { dispatchPdf(SharedPdfReaderAction.AnnotationsChanged(it)) },
                                onTextAnnotationSelected = ::selectTextAnnotation,
                                onTextDraftStarted = ::startActiveTextDraft,
                                onTextDraftChanged = ::updateActiveTextDraft,
                                onTextDraftBoundsChanged = ::updateActiveTextDraftBounds
                            )
                        }
                    }
                    DesktopPdfPageScrubOverlay(
                        pageIndex = pageScrubPreview,
                        pageCount = document.pageCount
                    )
                }
            } else {
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
                        val pageAnnotations = remember(annotations, pageIndex, pageCanvasSize) {
                            annotations
                                .filter { it.pageIndex == pageIndex }
                                .flatMap { annotation ->
                                    annotation.toRenderablePdfAnnotations(document, pageIndex, pageCanvasSize)
                                }
                        }
                        val selectedTextAnnotationForPage = selectedAnnotation?.takeIf {
                            selectedTool == PdfInkTool.TEXT &&
                                !isTextSelectionMode &&
                                it.kind == PdfAnnotationKind.TEXT &&
                                it.pageIndex == pageIndex
                        }
                        val visiblePageAnnotations = remember(pageAnnotations, selectedTextAnnotationForPage?.id) {
                            pageAnnotations.filterNot {
                                it.kind == PdfAnnotationKind.TEXT && it.id == selectedTextAnnotationForPage?.id
                            }
                        }
                        val pageEmbeddedAnnotations = remember(document.embeddedAnnotations, pageIndex) {
                            document.embeddedAnnotations.filter { it.pageIndex == pageIndex }
                        }
                        val searchHighlightBounds: List<PdfPageBounds> = remember(
                            document.path,
                            searchResults,
                            pageIndex,
                            activeSearchIndex,
                            searchHighlightMode,
                            pageCanvasSize,
                            searchQuery
                        ) {
                            val queryLength = searchQuery.trim().length
                            if (queryLength <= 0 || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) {
                                emptyList()
                            } else {
                                SharedPdfSearchEngine.highlightsForPage(
                                    results = searchResults,
                                    pageIndex = pageIndex,
                                    activeResultIndex = activeSearchIndex,
                                    mode = searchHighlightMode
                                ).flatMap { result ->
                                    val matchLength = result.matchLength.takeIf { it > 0 } ?: queryLength
                                    DesktopPdfium.textRectsForRange(
                                        document = document,
                                        pageIndex = pageIndex,
                                        startIndex = result.matchIndex,
                                        endIndex = result.matchIndex + matchLength - 1,
                                        viewportWidth = pageCanvasSize.width,
                                        viewportHeight = pageCanvasSize.height
                                    ).map { it.toPdfPageBounds() }
                                        .filter { it.right > it.left && it.bottom > it.top }
                                        .mergePdfBoundsByLine()
                                }
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
                                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, selectedTool, isRichTextMode) {
                                    if (isRichTextMode) return@pointerInput
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val point = event.changes.firstOrNull()?.position ?: continue
                                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                                if (selectedTool != PdfInkTool.TEXT) {
                                                    val linkTarget = document.linkAt(pageIndex, point, pageCanvasSize)
                                                    if (linkTarget != null) {
                                                        logPdfLink(
                                                            "tap_hit mode=page page=${pageIndex + 1} " +
                                                                "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                                "textSelection=$isTextSelectionMode target=${linkTarget.formatLogTarget()}"
                                                        )
                                                        activatePdfLink(linkTarget)
                                                        event.changes.forEach { it.consume() }
                                                        continue
                                                    }
                                                }
                                                val embeddedHit = pageEmbeddedAnnotations.findLast {
                                                    it.sharedPdfEmbeddedHitTest(point, pageCanvasSize)
                                                }
                                                if (embeddedHit != null) {
                                                    selectEmbeddedAnnotation(embeddedHit)
                                                    clearPdfInteractionState()
                                                    event.changes.forEach { it.consume() }
                                                } else if (
                                                    currentTextSelection != null &&
                                                    selectionMenuOffset == null
                                                ) {
                                                    selectionMenuOffset = null
                                                    textSelection = null
                                                    selectionStartHit = null
                                                    selectionEndHit = null
                                                }
                                            } else if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
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
                                    isHighlighterSnapEnabled,
                                    textStyleConfig,
                                    activeTextDraft?.id,
                                    isRichTextMode,
                                    pageCanvasSize,
                                    pageRender.width,
                                    pageRender.height
                                ) {
                                    if (isRichTextMode) return@pointerInput
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
                                                when {
                                                    activeTextDraftContains(pageIndex, start, pageCanvasSize) -> Unit
                                                    else -> {
                                                        val textHit = currentPdfAnnotations.textAnnotationHitAt(
                                                            pageIndex = pageIndex,
                                                            point = start,
                                                            canvasSize = pageCanvasSize
                                                        )
                                                        if (textHit != null) {
                                                            selectTextAnnotation(textHit)
                                                        } else {
                                                            startActiveTextDraft(pageIndex, start, pageCanvasSize)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        var eraserPreviousPoint: Offset? = null
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                if (selectedTool == PdfInkTool.ERASER) {
                                                    val annotationSnapshot = currentPdfAnnotations
                                                    val updatedAnnotations = annotationSnapshot.filterNot {
                                                        it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                                            point = start,
                                                            size = pageCanvasSize,
                                                            eraserStrokeWidth = strokeWidth
                                                        )
                                                    }
                                                    if (updatedAnnotations.size != annotationSnapshot.size) {
                                                        dispatchPdf(SharedPdfReaderAction.AnnotationsChanged(updatedAnnotations))
                                                    }
                                                    eraserPreviousPoint = start
                                                } else {
                                                    activeStroke = listOf(start.toSharedPdfPoint(pageCanvasSize, System.currentTimeMillis()))
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                if (selectedTool == PdfInkTool.ERASER) {
                                                    val point = change.position
                                                    val previousPoint = eraserPreviousPoint
                                                    val annotationSnapshot = currentPdfAnnotations
                                                    val updatedAnnotations = annotationSnapshot.filterNot {
                                                        it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                                            point = point,
                                                            size = pageCanvasSize,
                                                            lastPoint = previousPoint,
                                                            eraserStrokeWidth = strokeWidth
                                                        )
                                                    }
                                                    if (updatedAnnotations.size != annotationSnapshot.size) {
                                                        dispatchPdf(SharedPdfReaderAction.AnnotationsChanged(updatedAnnotations))
                                                    }
                                                    eraserPreviousPoint = point
                                                } else {
                                                    activeStroke = activeStroke.withDesktopPdfDragPoint(
                                                        point = change.position,
                                                        canvasSize = pageCanvasSize,
                                                        tool = selectedTool,
                                                        snapHighlighter = isHighlighterSnapEnabled,
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                eraserPreviousPoint = null
                                                if (activeStroke.size > 1) {
                                                    dispatchPdf(
                                                        SharedPdfReaderAction.AnnotationAdded(
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
                                                    )
                                                }
                                                activeStroke = emptyList()
                                            },
                                            onDragCancel = {
                                                eraserPreviousPoint = null
                                                activeStroke = emptyList()
                                            }
                                        )
                                    }
                                }
                        ) {
                            Image(
                                bitmap = pageRender.image,
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                            SharedPdfRichTextLayer(
                                pageIndex = pageIndex,
                                controller = richTextController,
                                pageWidth = pageCanvasSize.width.toFloat(),
                                pageHeight = pageCanvasSize.height.toFloat(),
                                isTextEditingEnabled = isRichTextMode,
                                onPageTapped = {}
                            )
                            PdfSearchHighlightOverlay(
                                bounds = searchHighlightBounds,
                                canvasSize = pageCanvasSize,
                                color = when (searchHighlightMode) {
                                    SearchHighlightMode.ALL -> Color(0x55FDD835)
                                    SearchHighlightMode.FOCUSED -> Color(0x88FF9800)
                                }
                            )
                            PdfTextSelectionOverlay(
                                selection = textSelection,
                                canvasSize = pageCanvasSize
                            )
                            SharedPdfAnnotationOverlay(
                                annotations = visiblePageAnnotations,
                                activeStroke = activeStroke,
                                canvasSize = pageCanvasSize,
                                activeTool = selectedTool,
                                activeStrokeColorArgb = selectedColor,
                                activeStrokeWidth = strokeWidth,
                                selectedAnnotationId = selectedAnnotationId
                            )
                            SharedPdfInlineTextEditorOverlay(
                                draft = activeTextDraft?.takeIf { it.pageIndex == pageIndex },
                                canvasSize = pageCanvasSize,
                                onTextChange = { updateActiveTextDraft(it, pageCanvasSize) },
                                onBoundsChange = ::updateActiveTextDraftBounds
                            )
                            selectedTextAnnotationForPage?.let { annotation ->
                                val bounds = annotation.bounds
                                if (bounds != null && activeTextDraft == null) {
                                    SharedPdfTextBoxEditorOverlay(
                                        id = annotation.id,
                                        text = annotation.text,
                                        style = annotation.sharedPdfTextStyle(),
                                        bounds = bounds,
                                        canvasSize = pageCanvasSize,
                                        onTextChange = { text ->
                                            updateAnnotation(annotation.copy(text = text))
                                        },
                                        onBoundsChange = { nextBounds ->
                                            updateAnnotation(annotation.copy(bounds = nextBounds))
                                        }
                                    )
                                }
                            }
                            SharedPdfEmbeddedAnnotationOverlay(
                                annotations = pageEmbeddedAnnotations,
                                canvasSize = pageCanvasSize,
                                selectedAnnotationId = selectedEmbeddedAnnotationId
                            )
                            SharedPdfPageNumberOverlay(
                                pageIndex = pageIndex,
                                pageCount = document.pageCount
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
                                onCopy = {
                                    textSelection?.let(::copySelection)
                                    clearSelection()
                                },
                                onHighlight = ::highlightCurrentSelection,
                                onSearch = {
                                    textSelection?.let(::searchSelection)
                                    selectionMenuOffset = null
                                },
                                onTranslate = {
                                    textSelection?.let(::translateSelection)
                                    selectionMenuOffset = null
                                },
                                onClear = ::clearSelection
                            )
                        }
                    }
                }
                    DesktopPdfPageScrubOverlay(
                        pageIndex = pageScrubPreview,
                        pageCount = document.pageCount
                    )
            }
        }
    }
}
}
}

@Composable
private fun DesktopPdfJumpHistoryControls(
    backPage: Int?,
    forwardPage: Int?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    val hasJumpTargets = backPage != null || forwardPage != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Jump history",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClear,
                    enabled = hasJumpTargets,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear jump history")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = backPage != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Jump back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        backPage?.let { "Jump back p. ${it + 1}" } ?: "Jump back",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onForward,
                    enabled = forwardPage != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        forwardPage?.let { "Jump forward p. ${it + 1}" } ?: "Jump forward",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Jump forward",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopPdfPageScrubOverlay(
    pageIndex: Int?,
    pageCount: Int
) {
    if (pageIndex == null || pageCount <= 0) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "Page ${pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun DesktopVerticalPdfPage(
    document: DesktopPdfDocument,
    pageIndex: Int,
    scale: Float,
    zoomSpec: PdfZoomSpec,
    annotations: List<SharedPdfAnnotation>,
    searchResults: List<SharedPdfSearchResult>,
    activeSearchIndex: Int,
    searchHighlightMode: SearchHighlightMode,
    searchQuery: String,
    isTextSelectionMode: Boolean,
    selectedAnnotationId: String?,
    selectedEmbeddedAnnotationId: String?,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    isHighlighterSnapEnabled: Boolean,
    activeTextDraft: SharedPdfTextDraft?,
    richTextController: SharedPdfRichTextController,
    isRichTextMode: Boolean,
    shouldRender: Boolean,
    onSelectPage: (Int) -> Unit,
    onCopySelection: (DesktopPdfTextSelection) -> Unit,
    onHighlightSelection: (Int, DesktopPdfTextSelection, IntSize) -> Unit,
    onSearchSelection: (DesktopPdfTextSelection) -> Unit,
    onTranslateSelection: (DesktopPdfTextSelection) -> Unit,
    onEmbeddedAnnotationSelected: (SharedPdfEmbeddedAnnotation) -> Unit,
    onLinkActivated: (DesktopPdfLinkTarget) -> Unit,
    onAnnotationAdded: (SharedPdfAnnotation) -> Unit,
    onAnnotationUpdated: (SharedPdfAnnotation) -> Unit,
    onAnnotationsChanged: (List<SharedPdfAnnotation>) -> Unit,
    onTextAnnotationSelected: (SharedPdfAnnotation) -> Unit,
    onTextDraftStarted: (Int, Offset, IntSize) -> Unit,
    onTextDraftChanged: (String, IntSize) -> Unit,
    onTextDraftBoundsChanged: (PdfPageBounds) -> Unit
) {
    val density = LocalDensity.current
    val pageInteractionSource = remember { MutableInteractionSource() }
    var renderedPage by remember(document.path, pageIndex, scale) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderError by remember(document.path, pageIndex, scale) { mutableStateOf<String?>(null) }
    var isRendering by remember(document.path, pageIndex, scale) { mutableStateOf(true) }
    var pageCanvasSize by remember(document.path, pageIndex, scale) { mutableStateOf(IntSize.Zero) }
    var selectionStartIndex by remember(document.path, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(document.path, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(document.path, pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(document.path, pageIndex) { mutableStateOf<Offset?>(null) }
    var activeStroke by remember(document.path, pageIndex, selectedTool) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentAnnotations by rememberUpdatedState(annotations)

    fun clearSelection() {
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
    }

    fun clearInteractionState() {
        clearSelection()
        activeStroke = emptyList()
    }

    LaunchedEffect(document.path, pageIndex, scale, shouldRender) {
        if (!shouldRender) {
            renderedPage = null
            renderError = null
            isRendering = false
            clearInteractionState()
            return@LaunchedEffect
        }
        isRendering = true
        renderError = null
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        if (pageSize == null) {
            renderedPage = null
            renderError = "Failed to render page."
            isRendering = false
            return@LaunchedEffect
        }
        delay(45)
        val safeScale = zoomSpec.safeRenderScale(pageSize.width, pageSize.height, scale)
        val result = withContext(Dispatchers.IO) {
            runCatching { DesktopPdfium.renderPage(document, pageIndex, safeScale) }
        }
        renderedPage = result.getOrNull()
        renderError = result.exceptionOrNull()?.message
            ?: if (renderedPage == null) "Failed to render page." else null
        isRendering = false
    }

    LaunchedEffect(isTextSelectionMode) {
        if (!isTextSelectionMode) {
            clearSelection()
        } else {
            activeStroke = emptyList()
        }
    }

    LaunchedEffect(selectedTool) {
        activeStroke = emptyList()
    }

    Column(
        modifier = Modifier.clickable(
            interactionSource = pageInteractionSource,
            indication = null,
            onClick = { onSelectPage(pageIndex) }
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        val placeholderScale = pageSize?.let { zoomSpec.safeRenderScale(it.width, it.height, scale) } ?: scale
        val placeholderWidthDp = with(density) { ((pageSize?.width ?: 612f) * placeholderScale).toDp() }
        val placeholderHeightDp = with(density) { ((pageSize?.height ?: 792f) * placeholderScale).toDp() }
        val renderedPageWidth = renderedPage?.width ?: 0
        val renderedPageHeight = renderedPage?.height ?: 0
        val pageRenderScale = if (pageSize != null && pageSize.width > 0f && renderedPageWidth > 0) {
            renderedPageWidth / pageSize.width
        } else {
            placeholderScale
        }
        val pageEmbeddedAnnotations = remember(document.embeddedAnnotations, pageIndex) {
            document.embeddedAnnotations.filter { it.pageIndex == pageIndex }
        }

        Box(
            modifier = Modifier
                .size(placeholderWidthDp, placeholderHeightDp)
                .background(Color.White, RoundedCornerShape(2.dp))
                .onSizeChanged { pageCanvasSize = it }
                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, selectedTool, isRichTextMode) {
                    if (isRichTextMode) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val point = event.changes.firstOrNull()?.position ?: continue
                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                if (selectedTool != PdfInkTool.TEXT) {
                                    val linkTarget = document.linkAt(pageIndex, point, pageCanvasSize)
                                    if (linkTarget != null) {
                                        logPdfLink(
                                            "tap_hit mode=vertical page=${pageIndex + 1} " +
                                                "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                "textSelection=$isTextSelectionMode target=${linkTarget.formatLogTarget()}"
                                        )
                                        onSelectPage(pageIndex)
                                        onLinkActivated(linkTarget)
                                        clearInteractionState()
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }
                                }
                                val embeddedHit = pageEmbeddedAnnotations.findLast {
                                    it.sharedPdfEmbeddedHitTest(point, pageCanvasSize)
                                }
                                if (embeddedHit != null) {
                                    onSelectPage(pageIndex)
                                    onEmbeddedAnnotationSelected(embeddedHit)
                                    clearInteractionState()
                                    event.changes.forEach { it.consume() }
                                } else if (
                                    currentTextSelection != null &&
                                    selectionMenuOffset == null
                                ) {
                                    clearSelection()
                                }
                            } else if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val selection = currentTextSelection
                                if (selection != null) {
                                    onSelectPage(pageIndex)
                                    selectionMenuOffset = point
                                    logPdfSelection(
                                        "menu_open page=${pageIndex + 1} " +
                                            "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                            "range=${selection.startIndex}..${selection.endIndex} " +
                                            "chars=${selection.text.length}"
                                    )
                                    event.changes.forEach { it.consume() }
                                }
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
                    isHighlighterSnapEnabled,
                    activeTextDraft?.id,
                    isRichTextMode,
                    pageCanvasSize,
                    renderedPageWidth,
                    renderedPageHeight
                ) {
                    if (renderedPageWidth > 0 && renderedPageHeight > 0) {
                        if (isRichTextMode) return@pointerInput
                        if (isTextSelectionMode) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    onSelectPage(pageIndex)
                                    activeStroke = emptyList()
                                    selectionMenuOffset = null
                                    val hit = document.charHitAt(pageIndex, start, pageCanvasSize)
                                    selectionStartHit = hit
                                    selectionStartIndex = hit?.index
                                    selectionEndHit = null
                                    selectionEndIndex = null
                                    logPdfSelection(
                                        "drag_start page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
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
                                        )?.also {
                                            textSelection = it
                                            selectionMenuOffset = selectionEndHit?.point ?: selectionStartHit?.point
                                        }
                                    } else {
                                        textSelection
                                    }
                                    logPdfSelection(
                                        "drag_end page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
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
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
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
                                    onSelectPage(pageIndex)
                                    when {
                                        activeTextDraft?.containsOffset(pageIndex, start, pageCanvasSize) == true -> Unit
                                        else -> {
                                            val textHit = currentAnnotations.textAnnotationHitAt(
                                                pageIndex = pageIndex,
                                                point = start,
                                                canvasSize = pageCanvasSize
                                            )
                                            clearInteractionState()
                                            if (textHit != null) {
                                                onTextAnnotationSelected(textHit)
                                            } else {
                                                onTextDraftStarted(pageIndex, start, pageCanvasSize)
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
                            var eraserPreviousPoint: Offset? = null
                            detectDragGestures(
                                onDragStart = { start ->
                                    onSelectPage(pageIndex)
                                    clearInteractionState()
                                    if (selectedTool == PdfInkTool.ERASER) {
                                        val annotationSnapshot = currentAnnotations
                                        val updatedAnnotations = annotationSnapshot.filterNot {
                                            it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                                point = start,
                                                size = pageCanvasSize,
                                                eraserStrokeWidth = strokeWidth
                                            )
                                        }
                                        if (updatedAnnotations.size != annotationSnapshot.size) {
                                            onAnnotationsChanged(updatedAnnotations)
                                        }
                                        eraserPreviousPoint = start
                                    } else {
                                        activeStroke = listOf(
                                            start.toSharedPdfPoint(pageCanvasSize, System.currentTimeMillis())
                                        )
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (selectedTool == PdfInkTool.ERASER) {
                                        val point = change.position
                                        val previousPoint = eraserPreviousPoint
                                        val annotationSnapshot = currentAnnotations
                                        val updatedAnnotations = annotationSnapshot.filterNot {
                                            it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                                point = point,
                                                size = pageCanvasSize,
                                                lastPoint = previousPoint,
                                                eraserStrokeWidth = strokeWidth
                                            )
                                        }
                                        if (updatedAnnotations.size != annotationSnapshot.size) {
                                            onAnnotationsChanged(updatedAnnotations)
                                        }
                                        eraserPreviousPoint = point
                                    } else {
                                        activeStroke = activeStroke.withDesktopPdfDragPoint(
                                            point = change.position,
                                            canvasSize = pageCanvasSize,
                                            tool = selectedTool,
                                            snapHighlighter = isHighlighterSnapEnabled,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                },
                                onDragEnd = {
                                    eraserPreviousPoint = null
                                    if (activeStroke.size > 1) {
                                        onAnnotationAdded(
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
                                onDragCancel = {
                                    eraserPreviousPoint = null
                                    activeStroke = emptyList()
                                }
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                !shouldRender -> {
                    Text("Page ${pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                isRendering -> CircularProgressIndicator()
                renderError != null -> Text(renderError ?: "Failed to render page.", color = MaterialTheme.colorScheme.error)
                renderedPage != null -> {
                    val pageRender = renderedPage!!
                    val pageAnnotations = remember(annotations, pageIndex, pageCanvasSize) {
                        annotations
                            .filter { it.pageIndex == pageIndex }
                            .flatMap { annotation ->
                                annotation.toRenderablePdfAnnotations(document, pageIndex, pageCanvasSize)
                            }
                    }
                    val selectedTextAnnotationForPage = remember(annotations, selectedAnnotationId, selectedTool, isTextSelectionMode, pageIndex) {
                        annotations.firstOrNull {
                            selectedTool == PdfInkTool.TEXT &&
                                !isTextSelectionMode &&
                                it.id == selectedAnnotationId &&
                                it.kind == PdfAnnotationKind.TEXT &&
                                it.pageIndex == pageIndex
                        }
                    }
                    val visiblePageAnnotations = remember(pageAnnotations, selectedTextAnnotationForPage?.id) {
                        pageAnnotations.filterNot {
                            it.kind == PdfAnnotationKind.TEXT && it.id == selectedTextAnnotationForPage?.id
                        }
                    }
                    val searchHighlightBounds: List<PdfPageBounds> = remember(
                        document.path,
                        searchResults,
                        pageIndex,
                        activeSearchIndex,
                        searchHighlightMode,
                        pageCanvasSize,
                        searchQuery
                    ) {
                        val queryLength = searchQuery.trim().length
                        if (queryLength <= 0 || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) {
                            emptyList<PdfPageBounds>()
                        } else {
                            SharedPdfSearchEngine.highlightsForPage(
                                results = searchResults,
                                pageIndex = pageIndex,
                                activeResultIndex = activeSearchIndex,
                                mode = searchHighlightMode
                            ).flatMap { result ->
                                val matchLength = result.matchLength.takeIf { it > 0 } ?: queryLength
                                DesktopPdfium.textRectsForRange(
                                    document = document,
                                    pageIndex = pageIndex,
                                    startIndex = result.matchIndex,
                                    endIndex = result.matchIndex + matchLength - 1,
                                    viewportWidth = pageCanvasSize.width,
                                    viewportHeight = pageCanvasSize.height
                                ).map { it.toPdfPageBounds() }
                                    .filter { it.right > it.left && it.bottom > it.top }
                                    .mergePdfBoundsByLine()
                            }
                        }
                    }

                    Image(
                        bitmap = pageRender.image,
                        contentDescription = "PDF page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedPdfRichTextLayer(
                        pageIndex = pageIndex,
                        controller = richTextController,
                        pageWidth = pageCanvasSize.width.toFloat(),
                        pageHeight = pageCanvasSize.height.toFloat(),
                        isTextEditingEnabled = isRichTextMode,
                        onPageTapped = { onSelectPage(pageIndex) }
                    )
                    PdfSearchHighlightOverlay(
                        bounds = searchHighlightBounds,
                        canvasSize = pageCanvasSize,
                        color = when (searchHighlightMode) {
                            SearchHighlightMode.ALL -> Color(0x55FDD835)
                            SearchHighlightMode.FOCUSED -> Color(0x88FF9800)
                        }
                    )
                    PdfTextSelectionOverlay(
                        selection = textSelection,
                        canvasSize = pageCanvasSize
                    )
                    SharedPdfAnnotationOverlay(
                        annotations = visiblePageAnnotations,
                        activeStroke = activeStroke,
                        canvasSize = pageCanvasSize,
                        activeTool = selectedTool,
                        activeStrokeColorArgb = selectedColor,
                        activeStrokeWidth = strokeWidth,
                        selectedAnnotationId = selectedAnnotationId
                    )
                    SharedPdfInlineTextEditorOverlay(
                        draft = activeTextDraft?.takeIf { it.pageIndex == pageIndex },
                        canvasSize = pageCanvasSize,
                        onTextChange = { onTextDraftChanged(it, pageCanvasSize) },
                        onBoundsChange = { onTextDraftBoundsChanged(it) }
                    )
                    selectedTextAnnotationForPage?.let { annotation ->
                        val bounds = annotation.bounds
                        if (bounds != null && activeTextDraft == null) {
                            SharedPdfTextBoxEditorOverlay(
                                id = annotation.id,
                                text = annotation.text,
                                style = annotation.sharedPdfTextStyle(),
                                bounds = bounds,
                                canvasSize = pageCanvasSize,
                                onTextChange = { text ->
                                    onAnnotationUpdated(annotation.copy(text = text))
                                },
                                onBoundsChange = { nextBounds ->
                                    onAnnotationUpdated(annotation.copy(bounds = nextBounds))
                                }
                            )
                        }
                    }
                    SharedPdfEmbeddedAnnotationOverlay(
                        annotations = pageEmbeddedAnnotations,
                        canvasSize = pageCanvasSize,
                        selectedAnnotationId = selectedEmbeddedAnnotationId
                    )
                    SharedPdfPageNumberOverlay(
                        pageIndex = pageIndex,
                        pageCount = document.pageCount
                    )
                    if (textSelection != null && selectionMenuOffset != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(pageIndex, selectionMenuOffset) {
                                    detectTapGestures {
                                        clearSelection()
                                    }
                                }
                        )
                    }
                    PdfSelectionMenu(
                        selection = textSelection,
                        menuOffset = selectionMenuOffset,
                        canvasSize = pageCanvasSize,
                        onCopy = {
                            textSelection?.let(onCopySelection)
                            clearSelection()
                        },
                        onHighlight = {
                            textSelection?.let { onHighlightSelection(pageIndex, it, pageCanvasSize) }
                            clearSelection()
                        },
                        onSearch = {
                            textSelection?.let(onSearchSelection)
                            selectionMenuOffset = null
                        },
                        onTranslate = {
                            textSelection?.let(onTranslateSelection)
                            selectionMenuOffset = null
                        },
                        onClear = ::clearSelection
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopPdfAnnotationEditor(
    annotation: SharedPdfAnnotation,
    onUpdate: (SharedPdfAnnotation) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Selected ${annotation.desktopLabel()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
            Text(
                "Page ${annotation.pageIndex + 1}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (annotation.kind == PdfAnnotationKind.TEXT) {
                OutlinedTextField(
                    value = annotation.text,
                    onValueChange = { onUpdate(annotation.copy(text = it)) },
                    label = { Text("Text note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                SharedPdfTextStyleControls(
                    style = annotation.sharedPdfTextStyle(),
                    onStyleChange = { onUpdate(annotation.withSharedPdfTextStyle(it)) }
                )
            }
            if (annotation.kind != PdfAnnotationKind.TEXT) {
                val palette = if (
                    annotation.kind == PdfAnnotationKind.HIGHLIGHT ||
                    annotation.tool == PdfInkTool.HIGHLIGHTER ||
                    annotation.tool == PdfInkTool.HIGHLIGHTER_ROUND
                ) {
                    SharedPdfAnnotationDefaults.highlighterPalette
                } else {
                    SharedPdfAnnotationDefaults.penPalette
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { argb ->
                        Surface(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onUpdate(annotation.copy(colorArgb = argb)) },
                            color = Color(argb),
                            shape = RoundedCornerShape(13.dp),
                            content = {}
                        )
                    }
                }
            }
            if (annotation.kind == PdfAnnotationKind.INK) {
                val strokeRange = annotation.tool.sharedPdfStrokeWidthRange()
                val strokeValue = annotation.strokeWidth.coerceIn(strokeRange.start, strokeRange.endInclusive)
                Text("Thickness ${strokeValue.sharedPdfStrokePercent(strokeRange)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = strokeValue,
                    onValueChange = { onUpdate(annotation.copy(strokeWidth = it.coerceAtLeast(0.0001f))) },
                    valueRange = strokeRange
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DesktopPdfEmbeddedAnnotationPanel(
    annotation: SharedPdfEmbeddedAnnotation,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Embedded PDF comment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
            Text(
                "Page ${annotation.pageIndex + 1}${annotation.author.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            DesktopPdfEmbeddedComment(
                author = annotation.author,
                contents = annotation.contents.ifBlank { "No comment" },
                depth = 0
            )
            DesktopPdfEmbeddedReplies(annotation.replies, depth = 1)
            TextButton(onClick = onCopy) {
                Text("Copy thread")
            }
        }
    }
}

@Composable
private fun DesktopPdfEmbeddedReplies(
    replies: List<SharedPdfEmbeddedAnnotation>,
    depth: Int
) {
    replies.forEach { reply ->
        HorizontalDivider()
        DesktopPdfEmbeddedComment(
            author = reply.author,
            contents = reply.contents,
            depth = depth
        )
        if (reply.replies.isNotEmpty()) {
            DesktopPdfEmbeddedReplies(reply.replies, depth + 1)
        }
    }
}

@Composable
private fun DesktopPdfEmbeddedComment(
    author: String,
    contents: String,
    depth: Int
) {
    Column(
        modifier = Modifier.padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            author.ifBlank { "Unknown" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            contents.ifBlank { "No comment" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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

private fun SharedPdfAnnotation.desktopLabel(): String {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT -> "highlight"
        PdfAnnotationKind.INK -> tool.name.lowercase().replace('_', ' ')
        PdfAnnotationKind.TEXT -> "text note"
    }
}

private fun SharedPdfEmbeddedAnnotation.threadText(): String {
    return buildString {
        append(author.ifBlank { "Unknown" })
        append(": ")
        appendLine(contents.ifBlank { "No comment" })
        fun appendReplies(replies: List<SharedPdfEmbeddedAnnotation>, indent: String) {
            replies.forEach { reply ->
                append(indent)
                append(reply.author.ifBlank { "Unknown" })
                append(": ")
                appendLine(reply.contents.ifBlank { "No comment" })
                appendReplies(reply.replies, "$indent  ")
            }
        }
        appendReplies(replies, "  ")
    }.trimEnd()
}

private fun DesktopPdfDocument.linkAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): DesktopPdfLinkTarget? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    return DesktopPdfium.linkAt(
        document = this,
        pageIndex = pageIndex,
        normalizedX = point.x / canvasSize.width,
        normalizedY = point.y / canvasSize.height,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    )
}

@Composable
private fun PdfSearchHighlightOverlay(
    bounds: List<PdfPageBounds>,
    canvasSize: IntSize,
    color: Color
) {
    if (bounds.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        bounds.forEach { rect ->
            drawRect(
                color = color,
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
        chars = textPageData(pageIndex).chars.visiblePdfTextBounds(),
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
    val chars = textPageData(pageIndex).chars
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

    return dynamicBounds.ifEmpty { boundsList.ifEmpty { listOfNotNull(bounds) } }
        .mapIndexed { index, dynamicBounds ->
            copy(
                id = "${id}_line_$index",
                bounds = dynamicBounds
            )
        }
}

private fun SharedPdfTextDraft.containsOffset(
    pageIndex: Int,
    offset: Offset,
    canvasSize: IntSize
): Boolean {
    if (this.pageIndex != pageIndex || canvasSize.width <= 0 || canvasSize.height <= 0) return false
    val left = bounds.left * canvasSize.width
    val right = bounds.right * canvasSize.width
    val top = bounds.top * canvasSize.height
    val bottom = bounds.bottom * canvasSize.height
    return offset.x in left..right && offset.y in top..bottom
}

private fun List<SharedPdfAnnotation>.textAnnotationHitAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): SharedPdfAnnotation? {
    return asReversed().firstOrNull { annotation ->
        annotation.kind == PdfAnnotationKind.TEXT &&
            annotation.pageIndex == pageIndex &&
            annotation.sharedPdfHitTest(point, canvasSize)
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

internal fun desktopPdfAnnotationFile(documentPath: String): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(baseDir, "Episteme/annotations/pdf_$safeName.json")
}

internal fun desktopPdfBookmarkFile(documentPath: String): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(baseDir, "Episteme/annotations/pdf_${safeName}_bookmarks.json")
}

internal fun desktopPdfRichTextFile(documentPath: String): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(baseDir, "Episteme/annotations/pdf_${safeName}_rich_text.json")
}

private fun desktopPdfSearchIndexFile(documentPath: String): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(baseDir, "Episteme/search/pdf_${safeName}_text_index.tsv")
}

private fun restoreDesktopPdfSearchIndex(document: DesktopPdfDocument, indexFile: File): Int {
    val sourceFile = File(document.path)
    val lines = runCatching { indexFile.readLines(Charsets.UTF_8) }.getOrNull() ?: return document.indexedSearchTextPageCount()
    if (lines.firstOrNull() != DesktopPdfSearchIndexHeader) return 0
    val metadata = lines
        .asSequence()
        .drop(1)
        .takeWhile { !it.startsWith("page\t") }
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .toMap()
    val isFresh = metadata["pathHash"] == document.path.hashCode().toString() &&
        metadata["fileSize"] == sourceFile.length().toString() &&
        metadata["lastModified"] == sourceFile.lastModified().toString() &&
        metadata["pageCount"] == document.pageCount.toString()
    if (!isFresh) return 0

    val decoder = Base64.getDecoder()
    lines.asSequence()
        .filter { it.startsWith("page\t") }
        .forEach { line ->
            val parts = line.split('\t', limit = 3)
            val pageIndex = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val text = runCatching {
                String(decoder.decode(parts.getOrNull(2).orEmpty()), Charsets.UTF_8)
            }.getOrDefault("")
            document.cacheSearchTextPage(pageIndex, text)
        }
    return document.indexedSearchTextPageCount()
}

private fun saveDesktopPdfSearchIndex(document: DesktopPdfDocument, indexFile: File) {
    val sourceFile = File(document.path)
    val pages = document.indexedSearchPages()
    if (pages.isEmpty()) return
    val encoder = Base64.getEncoder()
    val payload = buildString {
        appendLine(DesktopPdfSearchIndexHeader)
        appendLine("pathHash\t${document.path.hashCode()}")
        appendLine("fileSize\t${sourceFile.length()}")
        appendLine("lastModified\t${sourceFile.lastModified()}")
        appendLine("pageCount\t${document.pageCount}")
        pages.forEach { page ->
            append("page\t")
            append(page.pageIndex)
            append('\t')
            appendLine(encoder.encodeToString(page.text.toByteArray(Charsets.UTF_8)))
        }
    }
    runCatching {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(payload, Charsets.UTF_8)
    }
}

private const val DesktopPdfSearchIndexHeader = "EpistemePdfSearchIndex\t1"

@Composable
private fun ReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onOpenEpub: () -> Unit,
    onOpenPdf: () -> Unit,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit,
    onPickCustomFont: () -> String?,
    webViewRuntimeState: DesktopWebViewRuntimeState
) {
    var externalLinkDialogUrl by remember { mutableStateOf<String?>(null) }
    var lastHandledLink by remember { mutableStateOf<DesktopEpubHandledLink?>(null) }

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    SharedReaderScreen(
        session = session,
        readerEngine = readerEngine,
        onSessionChange = onSessionChange,
        onOpenEpub = onOpenEpub,
        onOpenPdf = onOpenPdf,
        toolbarPreferences = toolbarPreferences,
        onToolbarPreferencesChange = onToolbarPreferencesChange,
        highlightPalette = highlightPalette,
        onHighlightPaletteChange = onHighlightPaletteChange,
        onPickCustomFont = onPickCustomFont
    ) { html, background, navigationTarget, highlights, onVisiblePageChanged ->
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
                    navigationTarget = navigationTarget,
                    highlights = highlights,
                    onHighlightCreated = { highlight ->
                        onSessionChange(session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine))
                    },
                    onLinkClicked = { link ->
                        val now = System.currentTimeMillis()
                        val last = lastHandledLink
                        if (last != null && last.href == link.href && now - last.handledAtMs < 900L) {
                            logEpubLink(
                                "click_duplicate_ignored source=${link.source} href=\"${link.href.logPreview()}\" " +
                                    "ageMs=${now - last.handledAtMs}"
                            )
                        } else {
                            lastHandledLink = DesktopEpubHandledLink(link.href, now)
                            logEpubLink(
                                "click source=${link.source} href=\"${link.href.logPreview()}\" " +
                                    "chapterIndex=${link.chapterIndex} chapterHref=\"${link.chapterHref.orEmpty().logPreview()}\" " +
                                    "text=\"${link.text.orEmpty().logPreview()}\""
                            )
                            when (val target = readerEngine.resolveLink(session, link.href, link.chapterIndex)) {
                                is ReaderLinkTarget.External -> {
                                    logEpubLink("resolved_external url=\"${target.url.logPreview()}\"")
                                    externalLinkDialogUrl = target.url
                                }
                                is ReaderLinkTarget.Internal -> {
                                    logEpubLink(
                                        "resolved_internal chapter=${target.locator.chapterIndex} " +
                                            "page=${target.locator.pageIndex} offset=${target.locator.startOffset}"
                                    )
                                    onSessionChange(readerEngine.goToLocator(session, target.locator))
                                }
                                ReaderLinkTarget.Ignored -> {
                                    logEpubLink("resolved_ignored href=\"${link.href.logPreview()}\"")
                                }
                            }
                        }
                    },
                    onVisiblePageChanged = onVisiblePageChanged,
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
    navigationTarget: ReaderContentNavigationTarget,
    highlights: List<UserHighlight>,
    onHighlightCreated: (UserHighlight) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnHighlightCreated by rememberUpdatedState(onHighlightCreated)
    val latestOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val latestOnVisiblePageChanged by rememberUpdatedState(onVisiblePageChanged)
    val scope = rememberCoroutineScope()
    val linkRequestInterceptor = remember(scope) {
        object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult {
                if (!request.isForMainFrame) return WebRequestInterceptResult.Allow
                val link = request.url.readerLinkClickFromIntercept() ?: return WebRequestInterceptResult.Allow
                logEpubLink(
                    "request_intercept method=${request.method} redirect=${request.isRedirect} " +
                        "url=\"${request.url.logPreview()}\" href=\"${link.href.logPreview()}\""
                )
                scope.launch {
                    latestOnLinkClicked(link.copy(source = "request"))
                }
                return WebRequestInterceptResult.Reject
            }
        }
    }
    val navigator = rememberWebViewNavigator(requestInterceptor = linkRequestInterceptor)
    val bridge = rememberWebViewJsBridge()

    DisposableEffect(bridge) {
        val highlightHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerHighlightCreated"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                EpubAnnotationSerializer.parseHighlightJsonLenient(message.params)?.let { highlight ->
                    scope.launch { latestOnHighlightCreated(highlight) }
                }
            }
        }
        val positionHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerPositionChanged"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                message.params.readerPositionOrNull()?.let { position ->
                    scope.launch { latestOnVisiblePageChanged(position.pageIndex, position.locator) }
                }
            }
        }
        val linkHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerLinkClicked"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                logEpubLink("bridge_message params=\"${message.params.logPreview()}\"")
                val link = message.params.readerLinkClickOrNull()
                if (link == null) {
                    logEpubLink("bridge_message_ignored reason=parse_failed")
                } else {
                    logEpubLink(
                        "bridge_message_parsed href=\"${link.href.logPreview()}\" " +
                            "chapterIndex=${link.chapterIndex} chapterHref=\"${link.chapterHref.orEmpty().logPreview()}\""
                    )
                    scope.launch { latestOnLinkClicked(link) }
                }
            }
        }
        bridge.register(highlightHandler)
        bridge.register(positionHandler)
        bridge.register(linkHandler)
        onDispose {
            bridge.unregister(highlightHandler)
            bridge.unregister(positionHandler)
            bridge.unregister(linkHandler)
        }
    }

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
                captureBackPresses = false,
                navigator = navigator,
                webViewJsBridge = bridge
            )

            LaunchedEffect(
                navigationTarget.requestId,
                navigationTarget.readingMode,
                state.loadingState
            ) {
                if (navigationTarget.readingMode != com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL) return@LaunchedEffect
                if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
                val locator = navigationTarget.locator ?: return@LaunchedEffect
                navigator.evaluateJavaScript("window.readerScrollToLocator && window.readerScrollToLocator(${locator.toReaderLocatorJson()});")
            }

            LaunchedEffect(highlights, navigationTarget.readingMode, state.loadingState) {
                if (navigationTarget.readingMode != com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL) return@LaunchedEffect
                if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
                navigator.evaluateJavaScript("window.readerApplyHighlights && window.readerApplyHighlights(${EpubAnnotationSerializer.highlightsToJson(highlights)});")
            }

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

private data class DesktopReaderPosition(
    val pageIndex: Int,
    val locator: ReaderLocator?
)

private data class DesktopEpubLinkClick(
    val href: String,
    val chapterIndex: Int?,
    val text: String? = null,
    val chapterId: String? = null,
    val chapterHref: String? = null,
    val source: String = "bridge"
)

private data class DesktopEpubHandledLink(
    val href: String,
    val handledAtMs: Long
)

private fun String.readerPositionOrNull(): DesktopReaderPosition? {
    fun parse(rawJson: String): DesktopReaderPosition? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val pageIndex = obj["pageIndex"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.intOrNull
            ?: return@runCatching null
        val locator = ReaderLocator(
            chapterIndex = obj["chapterIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            pageIndex = pageIndex,
            startOffset = obj["startOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            endOffset = obj["endOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            textQuote = obj["textQuote"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            cfi = obj["cfi"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        )
        DesktopReaderPosition(pageIndex, locator)
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

private fun String.readerLinkClickOrNull(): DesktopEpubLinkClick? {
    fun parse(rawJson: String): DesktopEpubLinkClick? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val href = obj["href"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        DesktopEpubLinkClick(
            href = href,
            chapterIndex = obj["chapterIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            text = obj["text"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            chapterId = obj["chapterId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            chapterHref = obj["chapterHref"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        )
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

private fun String.readerLinkClickFromIntercept(): DesktopEpubLinkClick? {
    val trimmed = trim()
    if (trimmed.startsWith("readerlink:", ignoreCase = true)) {
        logEpubLink("request_intercept_readerlink raw=\"${trimmed.logPreview()}\"")
        val payload = trimmed.substringAfter("?", missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.substringBefore("=").equals("payload", ignoreCase = true) }
            ?.substringAfter("=", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        if (payload == null) {
            logEpubLink("request_intercept_readerlink_ignored reason=missing_payload")
            return null
        }
        val decoded = runCatching {
            URLDecoder.decode(payload, Charsets.UTF_8.name())
        }.getOrElse {
            logEpubLink("request_intercept_payload_decode_failed error=\"${it.message.orEmpty().logPreview()}\"")
            return null
        }
        val link = decoded.readerLinkClickOrNull()?.copy(source = "request")
        if (link == null) {
            logEpubLink("request_intercept_readerlink_ignored reason=parse_failed payload=\"${decoded.logPreview()}\"")
        }
        return link
    }
    return readerHrefFromIntercept()?.let { href ->
        DesktopEpubLinkClick(
            href = href,
            chapterIndex = null,
            source = "request"
        )
    }
}

private fun String.readerHrefFromIntercept(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    if (trimmed.equals("about:blank", ignoreCase = true)) return null
    if (trimmed.startsWith("file:///kcefbrowser/", ignoreCase = true)) return null
    if (trimmed.startsWith("file:/kcefbrowser/", ignoreCase = true)) return null
    if (trimmed.startsWith("file://", ignoreCase = true)) return null
    if (trimmed.startsWith("about:blank#", ignoreCase = true)) return "#${trimmed.substringAfter('#')}"
    if (trimmed.startsWith("data:", ignoreCase = true)) return null
    if (trimmed.startsWith("blob:", ignoreCase = true)) return null
    return trimmed
}

private fun ReaderLocator.toReaderLocatorJson(): String {
    return buildString {
        append("{")
        val values = buildList {
            chapterIndex?.let { add("\"chapterIndex\":$it") }
            pageIndex?.let { add("\"pageIndex\":$it") }
            startOffset?.let { add("\"startOffset\":$it") }
            endOffset?.let { add("\"endOffset\":$it") }
        }
        append(values.joinToString(","))
        append("}")
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

private fun chooseFontFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose font", FileDialog.LOAD).apply {
        file = "*.ttf;*.otf"
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

private val DesktopReadableFileTypes = SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP)

private fun ImportedBookFile.desktopFileType(): FileType {
    return SharedFileCapabilities.fileTypeForName(name)
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

@Composable
private fun DesktopExternalLinkDialog(
    url: String?,
    onDismiss: () -> Unit
) {
    if (url == null) return
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(url) {
        logExternalLink("dialog_show url=\"${url.logPreview()}\"")
        when (withContext(Dispatchers.IO) { showNativeExternalLinkDialog(url) }) {
            DesktopExternalLinkAction.COPY -> {
                logExternalLink("dialog_copy url=\"${url.logPreview()}\"")
                clipboardManager.setText(AnnotatedString(url))
            }
            DesktopExternalLinkAction.OPEN -> {
                logExternalLink("dialog_open url=\"${url.logPreview()}\"")
                openExternalUrl(url)
            }
            DesktopExternalLinkAction.DISMISS -> {
                logExternalLink("dialog_dismiss url=\"${url.logPreview()}\"")
            }
        }
        onDismiss()
    }
}

private enum class DesktopExternalLinkAction {
    COPY,
    OPEN,
    DISMISS
}

private fun showNativeExternalLinkDialog(url: String): DesktopExternalLinkAction {
    val result = AtomicReference(DesktopExternalLinkAction.DISMISS)
    val options = arrayOf("Copy", "Open", "Cancel")
    val showDialog = {
        val pane = JOptionPane(
            "You clicked on an external link:\n\n$url\n\nWhat would you like to do?",
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            options,
            options[1]
        )
        val dialog = pane.createDialog(null as java.awt.Component?, "External Link")
        dialog.isModal = true
        dialog.isAlwaysOnTop = true
        dialog.isVisible = true
        result.set(
            when (pane.value) {
                options[0] -> DesktopExternalLinkAction.COPY
                options[1] -> DesktopExternalLinkAction.OPEN
                else -> DesktopExternalLinkAction.DISMISS
            }
        )
        dialog.dispose()
    }
    if (SwingUtilities.isEventDispatchThread()) {
        showDialog()
    } else {
        SwingUtilities.invokeAndWait { showDialog() }
    }
    return result.get()
}

private fun openExternalUrl(url: String) {
    val normalizedUrl = url.normalizedExternalUrl()
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(normalizedUrl))
            logExternalLink("open_system_browser_success url=\"${normalizedUrl.logPreview()}\"")
        } else {
            logExternalLink("open_system_browser_unavailable url=\"${normalizedUrl.logPreview()}\"")
        }
    }.onFailure { throwable ->
        logExternalLink("open_system_browser_failed url=\"${normalizedUrl.logPreview()}\" error=\"${throwable.message.orEmpty().logPreview()}\"")
    }
}

private fun String.normalizedExternalUrl(): String {
    val trimmed = trim()
    return if (trimmed.startsWith("www.", ignoreCase = true)) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private const val PdfSelectionLogTag = "EpistemePdfSelection"
private const val PdfLinkLogTag = "EpistemePdfLink"
private const val EpubLinkLogTag = "EpistemeEpubLink"
private const val ExternalLinkLogTag = "EpistemeExternalLink"

private fun logPdfSelection(message: String) {
    println("$PdfSelectionLogTag $message")
}

private fun logPdfLink(message: String) {
    println("$PdfLinkLogTag $message")
}

private fun logEpubLink(message: String) {
    println("$EpubLinkLogTag $message")
}

private fun logExternalLink(message: String) {
    println("$ExternalLinkLogTag $message")
}

private fun DesktopPdfLinkTarget.formatLogTarget(): String {
    return "dest=${destPageIndex?.let { it + 1 } ?: "null"} uri=\"${uri.orEmpty().logPreview()}\""
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
