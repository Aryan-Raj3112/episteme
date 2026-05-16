package com.aryan.reader.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.WindowPlacement
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedImportOutcomeCounts
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SmartCollectionDefinition
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.withTtsReplacements
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfVisiblePageLayout
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfBookmarkSerializer
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfSearchEngine
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.currentSharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.mostVisiblePdfPageIndex
import com.aryan.reader.shared.pdf.pdfVerticalPageGapDp
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.toAnnotation
import com.aryan.reader.shared.pdf.updateCurrentSharedPdfTextStyle
import com.aryan.reader.shared.pdf.withBounds
import com.aryan.reader.shared.pdf.withSharedPdfTextStyle
import com.aryan.reader.shared.pdf.withStyle
import com.aryan.reader.shared.pdf.withText
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedEpubPaginationCache
import com.aryan.reader.shared.reader.SharedEpubMetadataEditor
import com.aryan.reader.shared.reader.SharedEpubMetadataUpdate
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.DesktopEpubNativeImage
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import com.aryan.reader.shared.ui.ReaderContentRenderPlan
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.shared.ui.SharedNativeReaderSelectionAction
import com.aryan.reader.shared.ui.SharedNativePaginatedReader
import com.aryan.reader.shared.ui.ReaderWorkspaceShell
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedAppShell
import com.aryan.reader.shared.ui.SharedAppTab
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedAppThemeSettingsDialog
import com.aryan.reader.shared.ui.SharedAboutScreen
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedConfirmDialog
import com.aryan.reader.shared.ui.SharedCustomFontsScreen
import com.aryan.reader.shared.ui.SharedHelpFeedbackScreen
import com.aryan.reader.shared.ui.SharedOpdsScreen
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.SharedPdfAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfAnnotationToolDock
import com.aryan.reader.shared.ui.SharedPdfEmbeddedAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfHighlighterPaletteEditor
import com.aryan.reader.shared.ui.SharedPdfInlineTextEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfPageNumberOverlay
import com.aryan.reader.shared.ui.SharedPdfRichTextHiddenInput
import com.aryan.reader.shared.ui.SharedPdfRichTextLayer
import com.aryan.reader.shared.ui.SharedPdfTextAnnotationDock
import com.aryan.reader.shared.ui.SharedPdfTextBoxEditorOverlay
import com.aryan.reader.shared.ui.SharedReaderScreen
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.SharedReaderThemeControls
import com.aryan.reader.shared.ui.SharedPdfVerticalScrollbar
import com.aryan.reader.shared.ui.SharedReaderVerticalScrollbar
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.ui.pdfReaderWorkspaceModel
import com.aryan.reader.shared.ui.sharedAcceleratedLazyWheelScroll
import com.aryan.reader.shared.ui.sharedPdfEmbeddedHitTest
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun main() {
    launchEpistemeDesktopApplication()
}
private fun desktopEmptyReaderBook(): SharedEpubBook {
    return SharedEpubBook(
        id = "desktop_empty_reader",
        fileName = "",
        title = "No book open",
        chapters = listOf(
            SharedEpubChapter(
                id = "empty",
                title = "No book open",
                plainText = ""
            )
        )
    )
}

private fun SharedLibrarySnapshot.withDesktopDefaults(): SharedLibrarySnapshot {
    return if (appSeedColor == null) {
        copy(appSeedColor = DesktopDefaultAppSeedColor)
    } else {
        this
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpistemeDesktopApp(
    window: Component? = null,
    appWindowPlacement: WindowPlacement,
    readerFullscreen: Boolean,
    onReaderFullscreenChange: (Boolean) -> Unit
) {
    val desktopBuildProfile = remember { currentDesktopBuildProfile() }
    val featurePolicy = desktopBuildProfile.featurePolicy
    val libraryProjector = remember { SharedLibraryStateProjector(DesktopFolderPathResolver) }
    val readerEngine = remember { ReaderEngine() }
    val libraryDatabase = remember { DesktopLibraryDatabase() }
    val desktopBookImporter = remember { DesktopBookImporter() }
    val customFontStore = remember {
        DesktopCustomFontStore(
            googleFontsDownloadAvailable = { featurePolicy.googleFontsDownload }
        )
    }
    val opdsRepository = remember { DesktopOpdsRepository() }
    val opdsController = remember {
        SharedOpdsController(
            repository = opdsRepository,
            idFactory = { UUID.randomUUID().toString() }
        )
    }
    val aiByokStore = remember { DesktopAiByokStore() }
    var aiByokSettings by remember {
        mutableStateOf(aiByokStore.load().withDesktopFeaturePolicy(featurePolicy))
    }
    val desktopAiAdapter = remember {
        DesktopByokAiAdapter(
            settingsProvider = { aiByokSettings.withDesktopFeaturePolicy(featurePolicy) },
            networkAccess = { featurePolicy.networkAccess }
        )
    }
    val desktopTtsAdapter = remember {
        DesktopGeminiCloudTtsAdapter(
            settingsProvider = { aiByokSettings.withDesktopFeaturePolicy(featurePolicy) },
            networkAccess = { featurePolicy.networkAccess }
        )
    }
    val initialLibrarySnapshot = remember { libraryDatabase.load().withDesktopDefaults() }
    val scope = rememberCoroutineScope()
    var webViewRuntimeState by remember { mutableStateOf(DesktopWebViewRuntimeState()) }
    var webViewRuntimeRequested by remember { mutableStateOf(false) }
    var readerCustomTextureIds by remember { mutableStateOf(DesktopReaderTextures.importedTextureIds()) }
    val appWindowFullscreen = appWindowPlacement == WindowPlacement.Fullscreen

    EpistemeDesktopWindowDecorationEffect(
        window = window,
        hideDecoration = readerFullscreen && !appWindowFullscreen
    )
    DesktopReaderFullscreenEffect(
        window = window,
        enabled = readerFullscreen && !appWindowFullscreen
    )

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
            readerDefaultSettings = initialLibrarySnapshot.readerDefaultSettings,
            pdfReaderDefaultSettings = initialLibrarySnapshot.pdfReaderDefaultSettings,
            readerToolbarPreferences = initialLibrarySnapshot.readerToolbarPreferences,
            readerHighlightPalette = initialLibrarySnapshot.readerHighlightPalette,
            pdfHighlighterPalette = initialLibrarySnapshot.pdfHighlighterPalette,
            readerTtsReplacementPreferences = initialLibrarySnapshot.readerTtsReplacementPreferences
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
    var customFonts by remember {
        mutableStateOf(initialLibrarySnapshot.customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() })
    }
    var activeReaderBookId by remember { mutableStateOf<String?>(null) }
    val desktopEpubPaginationCache = remember { SharedEpubPaginationCache() }
    var epubPaginationCacheGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(webViewRuntimeRequested) {
        if (!shouldStartDesktopWebViewRuntime(webViewRuntimeRequested, webViewRuntimeState)) {
            return@LaunchedEffect
        }

        val webViewBundleDir = withContext(Dispatchers.IO) { bundledDesktopWebViewDir() }
        val webViewBundlePresent = withContext(Dispatchers.IO) {
            isBundledDesktopWebViewPresent(webViewBundleDir)
        }
        if (!webViewBundlePresent) {
            webViewRuntimeState = webViewRuntimeState.copy(
                errorMessage = "Bundled embedded webview is missing from ${webViewBundleDir.absolutePath}."
            )
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        installDir(webViewBundleDir)
                        progress {
                            onDownloading {
                                webViewRuntimeState = webViewRuntimeState.copy(downloadProgress = max(it, 0f))
                            }
                            onInitialized {
                                webViewRuntimeState = webViewRuntimeState.copy(initialized = true, errorMessage = null)
                            }
                        }
                        settings {
                            cachePath = File(desktopUserCacheRoot(), "kcef").absolutePath
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
        }.onFailure { error ->
            webViewRuntimeState = webViewRuntimeState.copy(errorMessage = error.message ?: error.toString())
        }
    }
    var readerSession by remember { mutableStateOf(readerEngine.createSession(desktopEmptyReaderBook())) }
    LaunchedEffect(readerSession.reader.book.id, readerSession.reader.settings.readingMode) {
        if (
            readerSession.reader.book.chapters.isNotEmpty() &&
            readerSession.reader.settings.readingMode == ReaderReadingMode.VERTICAL
        ) {
            webViewRuntimeRequested = true
        }
    }
    var readerExtrasState by remember {
        mutableStateOf(
            ReaderExtrasState(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = aiByokSettings.isCloudTtsAvailable
                )
            )
        )
    }
    var activePdfDocument by remember { mutableStateOf<DesktopPdfDocument?>(null) }
    var openingReader by remember { mutableStateOf<DesktopReaderOpening?>(null) }
    var nextReaderOpenRequestId by remember { mutableStateOf(0L) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var showAiByokSettingsDialog by remember { mutableStateOf(false) }
    var showDesktopAppThemeSettingsDialog by remember { mutableStateOf(false) }
    var showClearBookCacheDialog by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookInfoInitiallyEditing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dropImportState by remember { mutableStateOf(DesktopDropImportState()) }
    var opdsState by remember { mutableStateOf(opdsController.state) }
    var readerTtsJob by remember { mutableStateOf<Job?>(null) }

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

    fun persistSnapshot(
        projected: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs,
        fonts: List<CustomFontItem> = customFonts
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                libraryDatabase.save(
                    SharedLibrarySnapshot(
                        books = projected.rawLibraryBooks,
                        shelfRecords = records,
                        shelfRefs = refs,
                        tags = projected.allTags,
                        customFonts = fonts,
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
                        readerDefaultSettings = projected.readerDefaultSettings,
                        pdfReaderDefaultSettings = projected.pdfReaderDefaultSettings,
                        readerToolbarPreferences = projected.readerToolbarPreferences,
                        readerHighlightPalette = projected.readerHighlightPalette,
                        pdfHighlighterPalette = projected.pdfHighlighterPalette,
                        readerTtsReplacementPreferences = projected.readerTtsReplacementPreferences
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

    fun clearDesktopBookCache() {
        scope.launch {
            withContext(Dispatchers.IO) {
                desktopEpubPaginationCache.clearAll()
                SharedJvmBookLoader.clearCache()
            }
            epubPaginationCacheGeneration++
            updateState(state.withBanner("Book cache cleared. EPUB pagination will be recreated on demand."))
        }
    }

    fun updateAiByokSettings(next: ReaderAiByokSettings) {
        if (!featurePolicy.aiAndCloud) return
        val sanitized = next.sanitized()
        logDesktopTts(
            "settings_update keyPresent=${sanitized.geminiKey.isNotBlank()} " +
                "ttsModel=\"${sanitized.ttsModel.desktopTtsPreview()}\" speaker=\"${sanitized.ttsSpeakerId.desktopTtsPreview()}\" " +
                "cloudAvailable=${sanitized.isCloudTtsAvailable}"
        )
        aiByokSettings = sanitized
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                isAvailable = sanitized.isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, sanitized.ttsSpeakerId)
            )
        )
        runCatching { aiByokStore.save(sanitized) }
            .onFailure { error ->
                logDesktopTts("settings_save_failed error=\"${error.desktopTtsSummary()}\"")
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: "AI settings could not be saved securely.")
                }
            }
    }

    fun updateReaderAutoScroll(autoScroll: ReaderAutoScrollState) {
        readerExtrasState = readerExtrasState.copy(autoScroll = autoScroll.sanitized())
    }

    fun currentReaderTtsCacheSummary() =
        if (activeReaderBookId == null) {
            ReaderTtsCacheSummary()
        } else {
            desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)
        }

    fun readerCloudTtsStoppedState(statusMessage: String? = null, errorMessage: String? = null) = ReaderCloudTtsState(
        isAvailable = aiByokSettings.sanitized().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = currentReaderTtsCacheSummary()
    )

    fun openReaderExternalLookup(action: ReaderExternalLookupAction, text: String) {
        if (!featurePolicy.externalLookup) return
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        openExternalUrl(externalLookupUrl(action, normalizedText.take(1800)))
    }

    fun runReaderAiAction(feature: ReaderAiFeature, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        if (!aiByokSettings.sanitized().areReaderAiFeaturesAvailable) return
        readerExtrasState = readerExtrasState.copy(
            aiResult = ReaderAiResultState(
                title = feature.displayName,
                isLoading = true
            )
        )
        scope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> desktopAiAdapter.define(
                    text = normalizedText.take(2400),
                    context = ReaderContextExtractor.currentPageText(readerSession)
                ).let { it.definition to it.error }
                ReaderAiFeature.SUMMARIZE -> desktopAiAdapter.summarize(normalizedText).let { it.summary to it.error }
                ReaderAiFeature.RECAP -> desktopAiAdapter.recap(normalizedText).let { it.recap to it.error }
            }
            readerExtrasState = readerExtrasState.copy(
                aiResult = ReaderAiResultState(
                    title = feature.displayName,
                    text = result.first.orEmpty(),
                    errorMessage = result.second,
                    isLoading = false
                )
            )
        }
    }

    fun syncBookSidecars(book: BookItem) {
        if (book.sourceFolder.isNullOrBlank()) {
            logDesktopFolderSync("bookSidecars.skipNoFolder book=${book.id}")
            return
        }
        logDesktopFolderSync(
            "bookSidecars.request book=${book.id} sourceFolder=\"${book.sourceFolder.orEmpty().folderSyncPreview()}\""
        )
        scope.launch(Dispatchers.IO) {
            DesktopLocalFolderSync.saveBookSidecars(book)
        }
    }

    fun updateActiveBookReadingState(
        pageIndex: Int,
        progress: Float,
        session: ReaderSessionState? = null,
        pdfViewport: SharedPdfReaderViewport? = null
    ) {
        activeReaderBookId?.let { bookId ->
            var updatedBook: BookItem? = null
            var shouldSyncSidecars = false
            val next = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.map { book ->
                    if (book.id == bookId) {
                        val readerPosition = session?.navigationLocator ?: book.readerPosition
                        shouldSyncSidecars = session != null ||
                            book.lastPageIndex != pageIndex ||
                            book.progressPercentage != progress ||
                            book.readerPosition != readerPosition
                        book.copy(
                            progressPercentage = progress,
                            timestamp = System.currentTimeMillis(),
                            isRecent = true,
                            lastPageIndex = pageIndex,
                            readerPosition = readerPosition,
                            readerSettings = session?.reader?.settings ?: book.readerSettings,
                            readerBookmarks = session?.bookmarks ?: book.readerBookmarks,
                            readerHighlights = session?.highlights ?: book.readerHighlights,
                            pdfReaderViewport = pdfViewport ?: book.pdfReaderViewport
                        ).also { updatedBook = it }
                    } else {
                        book
                    }
                }
            )
            updateState(next)
            if (shouldSyncSidecars) {
                updatedBook?.let(::syncBookSidecars)
            }
        }
    }

    fun updateActiveBookReaderSettings(settings: ReaderSettings) {
        activeReaderBookId?.let { bookId ->
            var updatedBook: BookItem? = null
            val next = state.copy(
                rawLibraryBooks = state.rawLibraryBooks.map { book ->
                    if (book.id == bookId) {
                        book.copy(
                            timestamp = System.currentTimeMillis(),
                            isRecent = true,
                            readerSettings = settings
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

    fun importDesktopReaderTexture(settings: ReaderSettings): ReaderSettings? {
        val source = chooseReaderTextureFile() ?: return null
        val textureId = DesktopReaderTextures.importTexture(source) ?: return null
        readerCustomTextureIds = DesktopReaderTextures.importedTextureIds()
        return settings.copy(textureId = textureId)
    }

    fun stopReaderCloudTts() {
        logDesktopTts("reader_stop_requested")
        readerTtsJob?.cancel()
        readerTtsJob = null
        scope.launch {
            desktopTtsAdapter.stop()
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerCloudTtsStoppedState(statusMessage = "Stopped")
            )
        }
    }

    fun pauseResumeReaderCloudTts() {
        val current = readerExtrasState.cloudTts
        if (current.isPaused) {
            scope.launch {
                desktopTtsAdapter.resume()
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerExtrasState.cloudTts.copy(
                        isPaused = false,
                        isPlaying = true,
                        statusMessage = readerExtrasState.cloudTts.progress.currentPositionLabel ?: "Reading"
                    )
                )
            }
        } else if (current.isPlaying) {
            scope.launch {
                desktopTtsAdapter.pause()
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerExtrasState.cloudTts.copy(
                        isPlaying = false,
                        isPaused = true,
                        statusMessage = "Paused"
                    )
                )
            }
        }
    }

    fun clearReaderCloudTtsCache() {
        desktopTtsAdapter.clearBookCacheForSpeaker(readerSession.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                statusMessage = "Voice cache cleared",
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
    }

    fun startReaderCloudTts(readScope: ReaderTtsReadScope, chunks: List<ReaderTtsChunk>) {
        val replacementBookId = activeReaderBookId ?: readerSession.reader.book.title
        val ttsChunks = chunks
            .filter { it.text.isNotBlank() }
            .withTtsReplacements(state.readerTtsReplacementPreferences, replacementBookId)
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "reader_sequence_toggle scope=${readScope.name} chunks=${ttsChunks.size} " +
                "isPlaying=${readerExtrasState.cloudTts.isPlaying} isLoading=${readerExtrasState.cloudTts.isLoading} " +
                "keyPresent=${settings.geminiKey.isNotBlank()} ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" " +
                "available=${desktopTtsAdapter.isAvailable}"
        )
        if (readerExtrasState.cloudTts.isPlaying || readerExtrasState.cloudTts.isLoading || readerExtrasState.cloudTts.isPaused) {
            stopReaderCloudTts()
            return
        }
        if (ttsChunks.isEmpty()) {
            logDesktopTts("reader_sequence_ignored reason=blank_text scope=${readScope.name}")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerExtrasState.cloudTts.copy(
                    errorMessage = "There is no text here to read.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_sequence_blocked reason=adapter_unavailable")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = "Add a Gemini key and select Gemini cloud TTS in AI keys and models.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        val ttsSessionId = System.currentTimeMillis()
        val initialProgress = ReaderTtsProgress(
            sessionId = ttsSessionId,
            scope = readScope,
            chunks = ttsChunks,
            currentChunkIndex = -1
        )
        readerExtrasState = readerExtrasState.copy(
            cloudTts = ReaderCloudTtsState(
                isAvailable = true,
                isLoading = true,
                statusMessage = "Preparing ${readScope.label.lowercase()}",
                progress = initialProgress,
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
        readerTtsJob = scope.launch {
            runCatching {
                logDesktopTts("reader_sequence_start scope=${readScope.name} chunks=${ttsChunks.size}")
                desktopTtsAdapter.speakChunks(readerSession.reader.book.title, readScope, ttsChunks) { index ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("Reader cloud TTS stopped")
                    val chunk = ttsChunks[index]
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    if (readerSession.reader.currentPageIndex != chunk.pageIndex) {
                        val updatedSession = readerEngine.goToPage(readerSession, chunk.pageIndex)
                        readerSession = updatedSession
                        updateActiveBookReadingState(
                            pageIndex = updatedSession.reader.currentPageIndex,
                            progress = updatedSession.reader.progress,
                            session = updatedSession
                        )
                    }
                    readerExtrasState = readerExtrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = true,
                            isPlaying = true,
                            statusMessage = progress.currentPositionLabel ?: "Reading",
                            progress = progress,
                            cacheSummary = currentReaderTtsCacheSummary()
                        )
                    )
                    logDesktopTts(
                        "reader_chunk_start scope=${readScope.name} index=${index + 1}/${ttsChunks.size} " +
                        "page=${chunk.pageIndex + 1} chapter=${chunk.chapterIndex} offsets=${chunk.startOffset}..${chunk.endOffset} " +
                            "sourceCfi=\"${chunk.sourceCfi.orEmpty().logPreview()}\" chars=${chunk.text.length} " +
                            "text=\"${chunk.text.logPreview()}\""
                    )
                }
            }.onFailure { error ->
                logDesktopTts("reader_sequence_failed error=\"${error.desktopTtsSummary()}\"")
                if (error !is kotlinx.coroutines.CancellationException) error.printStackTrace()
                readerExtrasState = if (error is kotlinx.coroutines.CancellationException) {
                    readerExtrasState.copy(
                        cloudTts = readerCloudTtsStoppedState(statusMessage = "Stopped")
                    )
                } else {
                    readerExtrasState.copy(
                        cloudTts = readerCloudTtsStoppedState(errorMessage = error.message ?: "Cloud TTS failed.")
                    )
                }
            }.onSuccess {
                logDesktopTts("reader_sequence_success chunks=${ttsChunks.size}")
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerCloudTtsStoppedState(statusMessage = "Finished")
                )
            }
        }
    }

    fun toggleReaderCloudTts(text: String) {
        val normalizedText = text.trim()
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "reader_toggle textChars=${normalizedText.length} isPlaying=${readerExtrasState.cloudTts.isPlaying} " +
                "isLoading=${readerExtrasState.cloudTts.isLoading} keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" available=${desktopTtsAdapter.isAvailable}"
        )
        if (readerExtrasState.cloudTts.isPlaying || readerExtrasState.cloudTts.isLoading || readerExtrasState.cloudTts.isPaused) {
            stopReaderCloudTts()
            return
        }
        if (normalizedText.isBlank()) {
            logDesktopTts("reader_toggle_ignored reason=blank_text")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerExtrasState.cloudTts.copy(
                    errorMessage = "There is no text on this page to read.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_toggle_blocked reason=adapter_unavailable")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = "Add a Gemini key and select Gemini cloud TTS in AI keys and models.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        val page = readerSession.reader.currentPage
        val selectionChunks = if (page != null) {
            ReaderTtsPlanner.chunksForText(
                text = normalizedText,
                pageIndex = page.pageIndex,
                chapterIndex = page.chapterIndex,
                chapterTitle = page.chapterTitle,
                sourceStartOffset = page.startOffset
            )
        } else {
            ReaderTtsPlanner.chunksForText(
                text = normalizedText,
                pageIndex = readerSession.reader.currentPageIndex,
                chapterIndex = 0,
                chapterTitle = "Selection"
            )
        }
        startReaderCloudTts(ReaderTtsReadScope.PAGE, selectionChunks)
    }

    fun finishImportFiles(
        files: List<ImportedBookFile>,
        failedCount: Int,
        onImported: (List<BookItem>) -> Unit = {}
    ) {
        val importStart = System.currentTimeMillis()
        val existingIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
        val importPlan = SharedImportPlanner.plan(
            files = files,
            existingBookIds = existingIds,
            platform = ReaderPlatform.DESKTOP,
            nowMillis = importStart
        )
        val counts = SharedImportOutcomeCounts(
            addedCount = importPlan.importedCount,
            duplicateCount = importPlan.duplicateCount,
            unsupportedCount = importPlan.unsupportedCount,
            failedCount = failedCount
        )
        if (files.isEmpty() && failedCount > 0) {
            updateState(state.withBanner("Could not import ${failedCount} file(s).", isError = true))
            return
        }
        if (importPlan.supportedFiles.isEmpty() && files.isNotEmpty()) {
            updateState(
                state.withBanner(
                    "No supported desktop reader files were selected. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are supported.",
                    isError = true
                )
            )
            return
        }
        val next = state.copy(rawLibraryBooks = importPlan.importedBooks + state.rawLibraryBooks)
            .let {
                when {
                    counts.addedCount > 0 && (counts.unsupportedCount > 0 || counts.failedCount > 0) -> {
                        val skippedCount = counts.unsupportedCount + counts.failedCount
                        it.withBanner("Imported ${counts.addedCount} file(s). Skipped ${skippedCount} file(s).")
                    }
                    counts.addedCount > 0 -> it.withBanner("Imported ${counts.addedCount} file(s).")
                    counts.duplicateCount > 0 -> it.withBanner("Those files are already in the library.")
                    counts.failedCount > 0 -> it.withBanner("Could not import ${counts.failedCount} file(s).", isError = true)
                    else -> it
                }
            }
        updateState(next)
        onImported(importPlan.importedBooks)
        val targetBookIds = importPlan.importedBooks.mapTo(mutableSetOf()) { it.id }
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

    fun importFiles(files: List<ImportedBookFile>, onImported: (List<BookItem>) -> Unit = {}) {
        if (files.isEmpty()) return
        updateState(state.withBanner("Importing ${files.size} file(s)..."))
        scope.launch {
            val preparedImport = withContext(Dispatchers.IO) {
                desktopBookImporter.prepareImports(files)
            }
            finishImportFiles(
                files = preparedImport.files,
                failedCount = preparedImport.failedCount,
                onImported = onImported
            )
        }
    }

    fun syncLocalFolders(
        targetFolder: File? = null,
        showBanner: Boolean = true,
        metadataOnly: Boolean = false
    ) {
        val mode = if (metadataOnly) "metadata" else "full"
        logDesktopFolderSync(
            "ui.sync.request mode=$mode target=\"${targetFolder?.absolutePath?.folderSyncPreview() ?: "ALL"}\" " +
                "showBanner=$showBanner linkedFolders=${state.syncedFolders.size} books=${state.rawLibraryBooks.size}"
        )
        if (targetFolder == null && state.syncedFolders.isEmpty()) {
            logDesktopFolderSync("ui.sync.skipNoFolders mode=$mode")
            updateState(state.withBanner("No local folders are linked yet.", isError = true))
            return
        }

        val snapshotState = state
        val snapshotShelfRefs = shelfRefs
        if (showBanner) {
            val message = if (metadataOnly) {
                "Folder sync: updating metadata..."
            } else {
                "Folder sync: scanning local folders..."
            }
            updateState(state.withBanner(message))
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopLocalFolderSync.sync(
                    state = snapshotState,
                    shelfRefs = snapshotShelfRefs,
                    targetFolder = targetFolder,
                    metadataOnly = metadataOnly
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
                metadataOnly ->
                    "Folder metadata sync complete."
                else ->
                    "Folder sync complete: ${stats.newBooks} new, ${stats.updatedBooks + stats.remoteMetadataUpdates + metadataStats.updatedBooks} updated, ${stats.removedBooks} removed."
            }
            logDesktopFolderSync(
                "ui.sync.result mode=$mode failed=$failedCount message=\"${message.folderSyncPreview()}\" " +
                    "new=${stats.newBooks} updated=${stats.updatedBooks} remoteUpdates=${stats.remoteMetadataUpdates} " +
                    "removed=${stats.removedBooks} metadataExtracted=${metadataStats.updatedBooks}"
            )
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
                openingReader = null
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                selectedTab = SharedAppTab.HOME
            }
        }
    }

    fun syncFolderMetadata(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = true)
    }

    fun scanSyncedFolders(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = false)
    }

    fun importFolder(folder: File) {
        logDesktopFolderSync("ui.importFolder.request folder=\"${folder.absolutePath.folderSyncPreview()}\"")
        if (!DesktopLocalFolderSync.hasSupportedFiles(folder)) {
            logDesktopFolderSync("ui.importFolder.skipNoSupportedFiles folder=\"${folder.absolutePath.folderSyncPreview()}\"")
            updateState(state.withBanner("That folder does not contain any supported desktop reader files.", isError = true))
            return
        }
        syncLocalFolders(targetFolder = folder)
    }

    fun importCustomFont(file: File?): CustomFontItem? {
        val source = file ?: return null
        return customFontStore.importFont(source)
            .onSuccess { font ->
                customFonts = (customFonts.filterNot { it.id == font.id } + font)
                    .filterNot { it.isDeleted }
                    .sortedBy { it.displayName.lowercase() }
                updateState(state.withBanner("Imported ${font.displayName}."))
            }
            .onFailure { error ->
                updateState(state.withBanner(error.message ?: "Could not import font.", isError = true))
            }
            .getOrNull()
    }

    fun downloadGoogleFont(fontName: String, onComplete: () -> Unit) {
        if (!featurePolicy.googleFontsDownload) {
            updateState(state.withBanner("Google Fonts download is unavailable in this desktop build.", isError = true))
            onComplete()
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                customFontStore.downloadGoogleFont(fontName)
            }
            result
                .onSuccess { font ->
                    customFonts = (customFonts.filterNot { it.id == font.id } + font)
                        .filterNot { it.isDeleted }
                        .sortedBy { it.displayName.lowercase() }
                    updateState(state.withBanner("${font.displayName} downloaded successfully."))
                }
                .onFailure { error ->
                    updateState(state.withBanner(error.message ?: "Could not download $fontName.", isError = true))
                }
            onComplete()
        }
    }

    fun deleteCustomFont(font: CustomFontItem) {
        customFontStore.deleteFont(font)
        customFonts = customFonts.filterNot { it.id == font.id }
        val clearedSettings = state.rawLibraryBooks.map { book ->
            val settings = book.readerSettings
            if (settings?.customFontPath == font.path) {
                book.copy(readerSettings = settings.copy(fontFamily = "Default", customFontPath = null))
            } else {
                book
            }
        }
        if (readerSession.reader.settings.customFontPath == font.path) {
            readerSession = readerEngine.updateSettings(
                readerSession,
                readerSession.reader.settings.copy(fontFamily = "Default", customFontPath = null)
            )
        }
        updateState(state.copy(rawLibraryBooks = clearedSettings).withBanner("Deleted ${font.displayName}."))
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

    fun applyBookMetadataUpdate(updated: BookItem) {
        val result = SharedLibraryEditor.updateBookMetadata(state, shelfRecords, shelfRefs, updated, System.currentTimeMillis())
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
        result.state.rawLibraryBooks.firstOrNull { it.id == updated.id }?.let(::syncBookSidecars)
    }

    fun writeDesktopEpubMetadata(original: BookItem, updated: BookItem): BookItem {
        val file = File(original.path ?: error("Book path is missing."))
        require(file.isFile && file.canWrite()) { "EPUB file is not writable." }
        val backup = File(
            File(desktopUserDataRoot(), "metadata_backups").apply { mkdirs() },
            "${original.id.toDesktopSafeFileName()}.epub"
        )
        val snapshot = SharedEpubMetadataEditor.rewriteInPlace(
            source = file,
            backup = backup,
            update = SharedEpubMetadataUpdate(
                title = updated.title,
                author = updated.author,
                description = updated.description,
                seriesName = updated.seriesName,
                seriesIndex = updated.seriesIndex
            )
        )
        return updated.copy(
            title = snapshot.title ?: updated.title,
            author = snapshot.author,
            description = snapshot.description,
            seriesName = snapshot.seriesName,
            seriesIndex = snapshot.seriesIndex,
            originalTitle = original.originalTitle ?: original.title,
            originalAuthor = original.originalAuthor ?: original.author,
            originalSeriesName = original.originalSeriesName ?: original.seriesName,
            originalSeriesIndex = original.originalSeriesIndex ?: original.seriesIndex,
            originalDescription = original.originalDescription ?: original.description,
            fileSize = file.length(),
            fileContentModifiedTimestamp = file.lastModified()
        )
    }

    fun updateBookMetadata(updated: BookItem) {
        val original = state.rawLibraryBooks.firstOrNull { it.id == updated.id }
        if (original != null && original.type == FileType.EPUB && original.hasEmbeddedMetadataChange(updated)) {
            scope.launch {
                val rewritten = runCatching {
                    withContext(Dispatchers.IO) {
                        writeDesktopEpubMetadata(original, updated)
                    }
                }
                rewritten.onSuccess(::applyBookMetadataUpdate)
                    .onFailure { error ->
                        println("Failed to update EPUB metadata for ${updated.displayName}: ${error.message}")
                        updateState(state.copy(bannerMessage = BannerMessage("Could not update EPUB metadata.")))
                    }
            }
            return
        }

        applyBookMetadataUpdate(updated)
    }

    fun recordBookOpened(bookId: String) {
        val now = System.currentTimeMillis()
        val next = SharedLibraryEditor.markBookOpened(state, bookId, now)
        val openedState = next.reduce(AppAction.BookTabOpened(bookId))
        updateState(openedState)
        openedState.rawLibraryBooks.firstOrNull { it.id == bookId }?.let(::syncBookSidecars)
    }

    fun scheduleOpenedBookMetadataExtraction(book: BookItem) {
        scope.launch {
            val enriched = withContext(Dispatchers.IO) {
                DesktopFolderMetadataExtractor.enrichOpenedBook(book)
            }
            if (enriched == book) return@launch
            updateState(
                state.copy(
                    rawLibraryBooks = state.rawLibraryBooks.map { current ->
                        if (current.id == book.id) {
                            current.withDesktopImportMetadata(enriched = enriched, original = book)
                        } else {
                            current
                        }
                    }
                )
            )
        }
    }

    fun schedulePdfEmbeddedAnnotationsLoad(document: DesktopPdfDocument) {
        scope.launch {
            delay(650L)
            if (activePdfDocument?.handleId != document.handleId) return@launch
            val annotations = withContext(Dispatchers.IO) {
                DesktopPdfium.loadEmbeddedAnnotations(document)
            }
            if (activePdfDocument?.handleId == document.handleId) {
                document.replaceEmbeddedAnnotations(annotations)
            }
        }
    }

    fun exitReaderTo(tab: SharedAppTab) {
        val wasPdfReaderVisible = selectedTab == SharedAppTab.READER &&
            openingReader == null &&
            activePdfDocument != null
        val detachedPdfDocument = activePdfDocument
        openingReader = null
        activePdfDocument = null
        selectedTab = tab
        if (!wasPdfReaderVisible) {
            detachedPdfDocument?.close()
        }
    }

    fun selectAppTab(tab: SharedAppTab) {
        val nextTab = if (tab == SharedAppTab.CATALOGS && !featurePolicy.opdsCatalogs) {
            SharedAppTab.HOME
        } else {
            tab
        }
        if (nextTab == SharedAppTab.SETTINGS) {
            settingsQuery = ""
            settingsDestination = SharedSettingsDestination.ROOT
        }
        if (nextTab != SharedAppTab.READER && (selectedTab == SharedAppTab.READER || activePdfDocument != null)) {
            exitReaderTo(nextTab)
        } else {
            selectedTab = nextTab
        }
    }

    fun applyReaderOpenResult(result: DesktopReaderOpenResult) {
        if (openingReader?.requestId != result.opening.requestId) {
            if (result is DesktopReaderOpenResult.Pdf && activePdfDocument?.handleId != result.document.handleId) {
                result.document.close()
            }
            return
        }

        openingReader = null
        when (result) {
            is DesktopReaderOpenResult.Failure -> {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                selectedTab = result.opening.returnTab
                updateState(state.withBanner(result.message, isError = true))
            }

            is DesktopReaderOpenResult.Pdf -> {
                activePdfDocument?.takeIf { it.handleId != result.document.handleId }?.close()
                activePdfDocument = result.document
                activeReaderBookId = result.book.id
                recordBookOpened(result.book.id)
                selectedTab = SharedAppTab.READER
                if (result.book.type == FileType.PDF) {
                    schedulePdfEmbeddedAnnotationsLoad(result.document)
                }
            }

            is DesktopReaderOpenResult.Text -> {
                activePdfDocument?.close()
                activePdfDocument = null
                readerSession = result.session
                activeReaderBookId = result.book.id
                recordBookOpened(result.book.id)
                selectedTab = SharedAppTab.READER
            }
        }
    }

    fun openReader(book: BookItem) {
        val desktopReaderSurface = SharedFileCapabilities.surfaceFor(book.type, ReaderPlatform.DESKTOP)
        if (openingReader?.bookId == book.id) return
        if (shouldRequestDesktopWebViewRuntime(desktopReaderSurface)) {
            webViewRuntimeRequested = true
        }

        if (desktopReaderSurface == ReaderFeatureSurface.PDF_VIEWER) {
            val path = book.path
            if (path.isNullOrBlank()) {
                updateState(
                    state.withBanner(
                        "This ${SharedFileCapabilities.displayNameFor(book.type)} does not have a local path.",
                        isError = true
                    )
                )
                return
            }
            val streamReference = SharedOpdsStreamUri.parse(path)
            if (streamReference != null && !featurePolicy.opdsCatalogs) {
                updateState(state.withBanner("OPDS streams are unavailable in this desktop build.", isError = true))
                return
            }
            val readerPath = streamReference?.let { path } ?: File(path).absolutePath
            if (activePdfDocument?.path == readerPath) {
                openingReader = null
                activeReaderBookId = book.id
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
        } else if (
            desktopReaderSurface == ReaderFeatureSurface.EPUB_READER ||
            desktopReaderSurface == ReaderFeatureSurface.TEXT_READER
        ) {
            if (activePdfDocument == null && activeReaderBookId == book.id) {
                openingReader = null
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
        } else {
            updateState(
                state.withBanner(
                    "${SharedFileCapabilities.displayNameFor(book.type)} reader support comes later. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are available on desktop."
                )
            )
            return
        }

        scheduleOpenedBookMetadataExtraction(book)

        val opening = DesktopReaderOpening(
            requestId = ++nextReaderOpenRequestId,
            bookId = book.id,
            title = book.cardTitleForMessage(),
            formatLabel = SharedFileCapabilities.displayNameFor(book.type),
            returnTab = selectedTab.takeUnless { it == SharedAppTab.READER } ?: SharedAppTab.LIBRARY
        )
        val readerDefaultSettings = state.readerDefaultSettings
        openingReader = opening
        selectedTab = SharedAppTab.READER

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (desktopReaderSurface) {
                        ReaderFeatureSurface.PDF_VIEWER -> {
                            val path = book.path.orEmpty()
                            val streamReference = SharedOpdsStreamUri.parse(path)
                            val document = if (streamReference != null) {
                                DesktopPdfium.loadOpdsStream(
                                    path = path,
                                    title = book.title?.takeIf { it.isNotBlank() } ?: book.displayName,
                                    reference = streamReference,
                                    catalog = opdsRepository.catalogById(streamReference.catalogId)
                                )
                            } else {
                                val readerFile = File(path)
                                if (book.type == FileType.PDF) {
                                    DesktopPdfium.load(readerFile, loadEmbeddedAnnotations = false)
                                } else {
                                    DesktopPdfium.loadComic(readerFile, book.type)
                                }
                            }
                            DesktopReaderOpenResult.Pdf(opening, book, document)
                        }

                        ReaderFeatureSurface.EPUB_READER,
                        ReaderFeatureSurface.TEXT_READER -> {
                            val path = book.path?.takeIf { it.isNotBlank() } ?: error("Book path is missing.")
                            val loadedBook = SharedJvmBookLoader.load(
                                file = File(path),
                                type = book.type,
                                titleOverride = book.title?.takeIf { it.isNotBlank() },
                                authorOverride = book.author?.takeIf { it.isNotBlank() }
                            )
                            val restoredSettings = resolvedDesktopReaderSettings(book, readerDefaultSettings)
                            val restoredSession = readerEngine.createSession(
                                book = loadedBook,
                                settings = restoredSettings,
                                initialPageIndex = book.lastPageIndex ?: 0,
                                initialLocator = book.readerPosition,
                                bookmarks = book.readerBookmarks,
                                highlights = book.readerHighlights
                            )
                            val restoredProgress = book.progressPercentage
                            val session = if (book.readerPosition == null && book.lastPageIndex == null && restoredProgress != null) {
                                readerEngine.goToProgress(restoredSession, restoredProgress.coerceIn(0f, 100f) / 100f)
                            } else {
                                restoredSession
                            }
                            DesktopReaderOpenResult.Text(opening, book, session)
                        }

                        else -> error("${SharedFileCapabilities.displayNameFor(book.type)} reader support comes later.")
                    }
                }.getOrElse { error ->
                    DesktopReaderOpenResult.Failure(
                        opening = opening,
                        book = book,
                        message = "Could not open ${SharedFileCapabilities.displayNameFor(book.type)}: " +
                            (error.message ?: "unknown error")
                    )
                }
            }
            applyReaderOpenResult(result)
        }
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
                openingReader = null
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                if (nextTabBook != null) {
                    openReader(nextTabBook)
                } else {
                    readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                    selectedTab = SharedAppTab.HOME
                }
            }
        }
    }

    fun closeReaderTab(book: BookItem) {
        val wasActive = activeReaderBookId == book.id
        if (openingReader?.bookId == book.id) {
            openingReader = null
        }
        val remainingIds = state.openTabIds.filterNot { it == book.id }
        updateState(state.reduce(AppAction.BookTabClosed(book.id)))
        if (!wasActive) return

        openingReader = null
        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        val nextBook = remainingIds.lastOrNull()?.let { nextId ->
            state.rawLibraryBooks.firstOrNull { it.id == nextId }
        }
        if (nextBook != null) {
            openReader(nextBook)
        } else {
            readerSession = readerEngine.createSession(desktopEmptyReaderBook())
            selectedTab = SharedAppTab.HOME
        }
    }

    fun closeAllReaderTabs() {
        openingReader = null
        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        readerSession = readerEngine.createSession(desktopEmptyReaderBook())
        selectedTab = SharedAppTab.HOME
        updateState(state.reduce(AppAction.AllTabsClosed))
    }

    fun importAndOpenBook() {
        val file = chooseBookFile() ?: return
        val importedFile = file.toDesktopImportedBookFile()
        val type = importedFile.desktopFileType()
        if (type !in DesktopBookFileTypes) {
            updateState(
                state.withBanner(
                    "No supported desktop reader file was selected. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are supported.",
                    isError = true
                )
            )
            return
        }
        importFiles(listOf(importedFile)) { importedBooks ->
            importedBooks.firstOrNull()?.let(::openReader)
        }
    }

    fun importAndOpenPdf() {
        val file = choosePdfFile() ?: return
        importFiles(listOf(file.toDesktopImportedBookFile())) { importedBooks ->
            importedBooks.firstOrNull()?.let(::openReader)
        }
    }

    fun emitOpds(next: com.aryan.reader.shared.opds.SharedOpdsScreenState) {
        opdsState = next
    }

    fun openOpdsCatalog(catalog: OpdsCatalog) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.openCatalog(catalog, ::emitOpds)
        }
    }

    fun openOpdsFeedUrl(url: String) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.openFeedUrl(url, ::emitOpds)
        }
    }

    fun navigateOpdsBack() {
        scope.launch {
            opdsController.navigateBack(::emitOpds)
        }
    }

    fun searchOpds(query: String) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.search(query, ::emitOpds)
        }
    }

    fun loadNextOpdsPage() {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.loadNextPage(::emitOpds)
        }
    }

    fun removeOpdsCatalog(catalog: OpdsCatalog) {
        emitOpds(opdsController.removeCatalog(catalog.id))
        val streamBookIds = state.rawLibraryBooks
            .filter { book -> SharedOpdsStreamUri.parse(book.path)?.catalogId == catalog.id }
            .mapTo(mutableSetOf()) { it.id }
        if (streamBookIds.isNotEmpty()) {
            if (activeReaderBookId in streamBookIds) {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                selectedTab = SharedAppTab.HOME
            }
            updateState(
                state.copy(
                    rawLibraryBooks = state.rawLibraryBooks.filterNot { it.id in streamBookIds },
                    openTabIds = state.openTabIds.filterNot { it in streamBookIds },
                    activeTabBookId = state.activeTabBookId?.takeUnless { it in streamBookIds }
                ).withBanner("Removed ${streamBookIds.size} streamed OPDS book(s) from that catalog.")
            )
        }
    }

    fun downloadOpdsBook(entry: OpdsEntry, acquisition: OpdsAcquisition) {
        if (!featurePolicy.opdsCatalogs) {
            updateState(state.withBanner("OPDS downloads are unavailable in this desktop build.", isError = true))
            return
        }
        val catalog = opdsState.currentCatalog
        scope.launch {
            emitOpds(opdsController.updateDownloadState(entry.id, SharedOpdsDownloadState(true, 0f)))
            val result = runCatching {
                opdsRepository.downloadBook(entry, acquisition, catalog) { progress ->
                    scope.launch {
                        if (opdsController.state.downloadingState[entry.id]?.isDownloading == true) {
                            emitOpds(opdsController.updateDownloadState(entry.id, SharedOpdsDownloadState(true, progress)))
                        }
                    }
                }
            }
            emitOpds(opdsController.updateDownloadState(entry.id, null))
            result.onSuccess { file ->
                importFiles(listOf(file.toDesktopImportedBookFile()))
                updateState(state.withBanner("Downloaded ${file.name} from OPDS."))
            }.onFailure { error ->
                updateState(
                    state.withBanner(
                        "Could not download ${entry.title}: ${error.message ?: "unknown error"}",
                        isError = true
                    )
                )
            }
        }
    }

    fun streamOpdsBook(entry: OpdsEntry, catalog: OpdsCatalog?) {
        if (!featurePolicy.opdsCatalogs) {
            updateState(state.withBanner("OPDS streams are unavailable in this desktop build.", isError = true))
            return
        }
        val pageCount = entry.pseCount
        val urlTemplate = entry.pseUrlTemplate
        if (pageCount == null || pageCount <= 0 || urlTemplate.isNullOrBlank()) {
            updateState(state.withBanner("This OPDS entry does not expose a readable stream.", isError = true))
            return
        }
        val reference = OpdsStreamReference(
            id = entry.id.ifBlank { "${entry.title}:$urlTemplate" },
            count = pageCount,
            urlTemplate = urlTemplate,
            catalogId = catalog?.id
        )
        val uriString = SharedOpdsStreamUri.build(reference)
        val now = System.currentTimeMillis()
        val streamBook = BookItem(
            id = uriString,
            path = uriString,
            type = FileType.CBZ,
            displayName = entry.title,
            timestamp = now,
            title = entry.title,
            author = entry.author,
            fileSize = 0L
        )
        if (state.rawLibraryBooks.none { it.id == streamBook.id }) {
            updateState(state.copy(rawLibraryBooks = state.rawLibraryBooks + streamBook))
        }
        openReader(streamBook)
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
            scanSyncedFolders(showBanner = false)
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { banner ->
            snackbarHostState.showSnackbar(banner.message)
            updateState(state.reduce(AppAction.BannerDismissed))
        }
    }

    LaunchedEffect(aiByokSettings, activeReaderBookId, readerSession.reader.book.title) {
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                isAvailable = aiByokSettings.isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
    ) {
        EpistemeDesktopWindowChromeEffect(
            window = window,
            captionColor = MaterialTheme.colorScheme.surface,
            textColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.background
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                featurePolicy = featurePolicy,
                onTabSelected = { tab ->
                    selectAppTab(tab)
                },
                onImportFiles = { importFiles(chooseFiles()) },
                onImportFolder = { chooseFolder()?.let(::importFolder) },
                onSyncRequested = {
                    scanSyncedFolders()
                },
                onFolderMetadataSyncRequested = { syncFolderMetadata() },
                onAppThemeModeChange = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onAppContrastOptionChange = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onAppTextDimFactorLightChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onAppTextDimFactorDarkChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onAppSeedColorChange = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomAppThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomAppThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
                onTabsEnabledChange = { enabled ->
                    if (!enabled && (selectedTab == SharedAppTab.READER || activePdfDocument != null || openingReader != null)) {
                        exitReaderTo(SharedAppTab.HOME)
                        activeReaderBookId = null
                    }
                    updateState(state.reduce(AppAction.TabsEnabledChanged(enabled)))
                },
                onAiSettingsRequested = if (featurePolicy.aiAndCloud) {
                    { showAiByokSettingsDialog = true }
                } else {
                    null
                }
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
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onOpenTab = ::openReader,
                            onCloseTab = ::closeReaderTab,
                            onCloseAllTabs = ::closeAllReaderTabs,
                            onRecentLimitChange = { limit -> updateState(state.reduce(LibraryAction.RecentLimitChanged(limit))) },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.HomePinToggled(book.id))) },
                            onOpenSettings = {
                                settingsQuery = ""
                                settingsDestination = SharedSettingsDestination.ROOT
                                selectAppTab(SharedAppTab.SETTINGS)
                            }
                        )

                        SharedAppTab.SETTINGS -> SharedSettingsHub(
                            model = sharedSettingsHubModel(
                                SharedSettingsHubInput(
                                    platform = SharedSettingsPlatform.DESKTOP,
                                    featurePolicy = featurePolicy,
                                    isDebugBuild = false,
                                    isSignedIn = false,
                                    isProUser = true,
                                    syncAvailable = false,
                                    folderSyncAvailable = true,
                                    aiSettingsAvailable = featurePolicy.aiAndCloud,
                                    includeLanguage = false,
                                    includeScreenCaptureProtection = false,
                                    includeExternalFileBehavior = false,
                                    includeStrictFileFilter = false,
                                    includeReaderTabs = false,
                                    includeHideReaderAi = false,
                                    isTabsEnabled = state.isTabsEnabled,
                                    isFolderSyncEnabled = state.isFolderSyncEnabled
                                )
                            ),
                            query = settingsQuery,
                            onQueryChange = { settingsQuery = it },
                            destination = settingsDestination,
                            onDestinationChange = { settingsDestination = it },
                            readerDefaultSettings = state.readerDefaultSettings,
                            onReaderDefaultSettingsChange = { settings ->
                                updateState(state.reduce(AppAction.ReaderDefaultSettingsChanged(settings)))
                            },
                            pdfReaderDefaultSettings = state.pdfReaderDefaultSettings,
                            onPdfReaderDefaultSettingsChange = { settings ->
                                updateState(state.reduce(AppAction.PdfReaderDefaultSettingsChanged(settings)))
                            },
                            readerToolbarPreferences = state.readerToolbarPreferences,
                            onReaderToolbarPreferencesChange = { preferences ->
                                updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                            },
                            ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                            onTtsReplacementPreferencesChange = { preferences ->
                                updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                            },
                            customFonts = customFonts,
                            onPickCustomFont = { importCustomFont(chooseFontFile())?.path },
                            readerCustomTextureIds = readerCustomTextureIds,
                            onImportReaderTexture = ::importDesktopReaderTexture,
                            onAction = { action ->
                                when (action) {
                                    SharedSettingsAction.APP_THEME -> showDesktopAppThemeSettingsDialog = true
                                    SharedSettingsAction.TABS_TOGGLE -> {
                                        if (
                                            state.isTabsEnabled &&
                                            (selectedTab == SharedAppTab.READER || activePdfDocument != null || openingReader != null)
                                        ) {
                                            exitReaderTo(SharedAppTab.HOME)
                                            activeReaderBookId = null
                                        }
                                        updateState(state.reduce(AppAction.TabsEnabledChanged(!state.isTabsEnabled)))
                                    }
                                    SharedSettingsAction.FOLDER_SYNC -> updateState(state.reduce(AppAction.FolderSyncEnabledChanged(!state.isFolderSyncEnabled)))
                                    SharedSettingsAction.AI_SETTINGS -> showAiByokSettingsDialog = true
                                    SharedSettingsAction.CUSTOM_FONTS -> selectAppTab(SharedAppTab.CUSTOM_FONTS)
                                    SharedSettingsAction.HELP_FEEDBACK -> selectAppTab(SharedAppTab.FEEDBACK)
                                    SharedSettingsAction.SUPPORT -> selectAppTab(SharedAppTab.SUPPORT)
                                    SharedSettingsAction.ABOUT -> selectAppTab(SharedAppTab.ABOUT)
                                    SharedSettingsAction.CLEAR_BOOK_CACHE -> showClearBookCacheDialog = true
                                    SharedSettingsAction.CLEAR_REFLOW_CACHE,
                                    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
                                    SharedSettingsAction.TEST_PANEL_DETECTION,
                                    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
                                    SharedSettingsAction.EXPORT_LOGS,
                                    SharedSettingsAction.DEBUG_ACTIONS,
                                    SharedSettingsAction.DEVICE_MANAGEMENT,
                                    SharedSettingsAction.SIGN_IN,
                                    SharedSettingsAction.SIGN_OUT,
                                    SharedSettingsAction.CLOUD_SYNC,
                                    SharedSettingsAction.LANGUAGE,
                                    SharedSettingsAction.RECENT_LIMIT,
                                    SharedSettingsAction.STRICT_FILE_FILTER,
                                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                                    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                                    SharedSettingsAction.HIDE_READER_AI,
                                    SharedSettingsAction.TTS_SETTINGS,
                                    SharedSettingsAction.PDF_READER_DEFAULTS,
                                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                                    SharedSettingsAction.READER_TOOLBAR,
                                    SharedSettingsAction.TTS_REPLACEMENTS,
                                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Unit
                                }
                            }
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
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onSyncFolderMetadata = { syncFolderMetadata() },
                            onScanFolders = { scanSyncedFolders() },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) }
                        )

                        SharedAppTab.SHELVES -> ShelvesScreen(
                            shelves = state.shelves,
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedLibraryBookIds,
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it }
                        )

                        SharedAppTab.CATALOGS -> {
                            if (featurePolicy.opdsCatalogs) {
                                SharedOpdsScreen(
                                    state = opdsState,
                                    localLibraryBooks = state.rawLibraryBooks,
                                    onOpenCatalog = ::openOpdsCatalog,
                                    onOpenFeedUrl = ::openOpdsFeedUrl,
                                    onNavigateBack = ::navigateOpdsBack,
                                    onSearch = ::searchOpds,
                                    onLoadNextPage = ::loadNextOpdsPage,
                                    onAddCatalog = { title, url, username, password ->
                                        emitOpds(opdsController.addCatalog(title, url, username, password))
                                    },
                                    onUpdateCatalog = { id, title, url, username, password ->
                                        emitOpds(opdsController.updateCatalog(id, title, url, username, password))
                                    },
                                    onRemoveCatalog = ::removeOpdsCatalog,
                                    onDownloadBook = ::downloadOpdsBook,
                                    onReadBook = ::openReader,
                                    onStreamBook = ::streamOpdsBook,
                                    onClearError = { emitOpds(opdsController.clearError()) },
                                    coverContent = { entry, modifier ->
                                        DesktopOpdsCoverImage(
                                            entry = entry,
                                            catalog = opdsState.currentCatalog,
                                            modifier = modifier
                                        )
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize())
                            }
                        }

                        SharedAppTab.CUSTOM_FONTS -> SharedCustomFontsScreen(
                            fonts = customFonts,
                            onImportFont = { importCustomFont(chooseFontFile()) },
                            onDeleteFont = ::deleteCustomFont,
                            googleFontsAvailable = featurePolicy.googleFontsDownload,
                            getGoogleFonts = { customFontStore.loadGoogleFontsList() },
                            onDownloadGoogleFont = ::downloadGoogleFont,
                            fontFamilyForPreview = { font -> font.toDesktopPreviewFontFamily() }
                        )

                        SharedAppTab.FEEDBACK -> SharedHelpFeedbackScreen(
                            onOpenGitHubIssues = { openExternalUrl(EpistemeIssuesUrl) },
                            onEmailSupport = {
                                val subject = desktopFeedbackSubject(desktopBuildProfile).urlEncode()
                                openExternalUrl("mailto:$EpistemeSupportEmail?subject=$subject")
                            }
                        )

                        SharedAppTab.SUPPORT -> SharedSupportProjectScreen(
                            onOpenGitHubSponsors = { openExternalUrl(EpistemeGitHubSponsorsUrl) },
                            onOpenPatreon = { openExternalUrl(EpistemePatreonUrl) }
                        )

                        SharedAppTab.ABOUT -> SharedAboutScreen(
                            versionName = desktopAppVersionName(),
                            buildLabel = desktopBuildProfile.buildLabel,
                            onOpenSource = if (featurePolicy.projectLinks) {
                                { openExternalUrl(EpistemeSourceUrl) }
                            } else {
                                null
                            },
                            onOpenIssues = if (featurePolicy.projectLinks) {
                                { openExternalUrl(EpistemeIssuesUrl) }
                            } else {
                                null
                            }
                        )

                        SharedAppTab.READER -> {
                            val opening = openingReader
                            val pdfDocument = activePdfDocument
                            if (opening != null) {
                                DesktopReaderOpeningScreen(
                                    opening = opening,
                                    onReturnToLibrary = {
                                        exitReaderTo(opening.returnTab)
                                    }
                                )
                            } else if (pdfDocument != null) {
                                PdfReaderScreen(
                                    document = pdfDocument,
                                    initialPageIndex = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.lastPageIndex }
                                        ?: 0,
                                    initialViewport = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.pdfReaderViewport },
                                    initialReaderSettings = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId } }
                                        ?.let { book -> resolvedDesktopReaderSettings(book, state.pdfReaderDefaultSettings) }
                                        ?: state.pdfReaderDefaultSettings,
                                    onReturnToLibrary = {
                                        onReaderFullscreenChange(false)
                                        exitReaderTo(SharedAppTab.LIBRARY)
                                    },
                                    onFullscreenChange = onReaderFullscreenChange,
                                    onPageStateChange = { page, progress, viewport ->
                                        updateActiveBookReadingState(page, progress, pdfViewport = viewport)
                                    },
                                    onReaderSettingsChange = ::updateActiveBookReaderSettings,
                                    pdfHighlighterPalette = state.pdfHighlighterPalette,
                                    onPdfHighlighterPaletteChange = { palette ->
                                        updateState(state.reduce(AppAction.PdfHighlighterPaletteChanged(palette)))
                                    },
                                    customTextureIds = readerCustomTextureIds,
                                    onImportTexture = ::importDesktopReaderTexture,
                                    onLocalSidecarsChanged = {
                                        activeReaderBookId
                                            ?.let { bookId -> state.rawLibraryBooks.firstOrNull { it.id == bookId } }
                                            ?.let(::syncBookSidecars)
                                    },
                                    aiByokSettings = aiByokSettings,
                                    aiAdapter = desktopAiAdapter,
                                    ttsAdapter = desktopTtsAdapter,
                                    ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                                    onTtsReplacementPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                                    },
                                    featurePolicy = featurePolicy
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
                                    onReturnToLibrary = {
                                        onReaderFullscreenChange(false)
                                        exitReaderTo(SharedAppTab.LIBRARY)
                                    },
                                    onFullscreenChange = onReaderFullscreenChange,
                                    toolbarPreferences = state.readerToolbarPreferences,
                                    onToolbarPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                                    },
                                    highlightPalette = state.readerHighlightPalette,
                                    onHighlightPaletteChange = { palette ->
                                        updateState(state.reduce(AppAction.ReaderHighlightPaletteChanged(palette)))
                                    },
                                    ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                                    ttsReplacementBookId = activeReaderBookId ?: readerSession.reader.book.title,
                                    onTtsReplacementPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                                    },
                                    onPickCustomFont = {
                                        importCustomFont(chooseFontFile())?.path
                                    },
                                    customFonts = customFonts,
                                    readerExtrasState = readerExtrasState,
                                    aiByokSettings = aiByokSettings,
                                    externalLookupAvailable = featurePolicy.externalLookup,
                                    cloudTtsControlsAvailable = featurePolicy.aiAndCloud,
                                    onExternalLookup = ::openReaderExternalLookup,
                                    onAiAction = ::runReaderAiAction,
                                    onAiResultDismiss = {
                                        readerExtrasState = readerExtrasState.copy(aiResult = ReaderAiResultState())
                                    },
                                    onCloudTtsToggle = ::toggleReaderCloudTts,
                                    onCloudTtsStart = ::startReaderCloudTts,
                                    onCloudTtsPauseResume = ::pauseResumeReaderCloudTts,
                                    onCloudTtsStop = ::stopReaderCloudTts,
                                    onCloudTtsClearCache = ::clearReaderCloudTtsCache,
                                    onAutoScrollChange = ::updateReaderAutoScroll,
                                    readerTextureDataUri = DesktopReaderTextures::dataUriFor,
                                    readerCustomTextureIds = readerCustomTextureIds,
                                    onImportReaderTexture = ::importDesktopReaderTexture,
                                    webViewRuntimeState = webViewRuntimeState,
                                    webViewNetworkAccessEnabled = featurePolicy.networkAccess,
                                    epubPaginationCache = desktopEpubPaginationCache,
                                    epubPaginationCacheGeneration = epubPaginationCacheGeneration
                                )
                            }
                        }
                }
            }
            DesktopDropImportOverlay(dropImportState)
        }

        if (showAiByokSettingsDialog) {
            DesktopAiByokSettingsDialog(
                settings = aiByokSettings,
                secureStorageAvailable = aiByokStore.isSecureStorageAvailable,
                onSettingsChange = ::updateAiByokSettings,
                onDismiss = { showAiByokSettingsDialog = false }
            )
        }

        if (showDesktopAppThemeSettingsDialog) {
            SharedAppThemeSettingsDialog(
                appThemeMode = state.appThemeMode,
                appContrastOption = state.appContrastOption,
                appTextDimFactorLight = state.appTextDimFactorLight,
                appTextDimFactorDark = state.appTextDimFactorDark,
                appSeedColor = state.appSeedColor,
                customAppThemes = state.customAppThemes,
                onThemeModeChanged = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onContrastOptionChanged = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onTextDimFactorLightChanged = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onTextDimFactorDarkChanged = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onSeedColorChanged = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
                onDismiss = { showDesktopAppThemeSettingsDialog = false }
            )
        }

        if (showClearBookCacheDialog) {
            SharedConfirmDialog(
                title = "Clear book cache",
                body = "Delete generated desktop book and EPUB pagination cache files? They will be recreated the next time books are opened.",
                confirmLabel = "Clear",
                onDismiss = { showClearBookCacheDialog = false },
                onConfirm = {
                    clearDesktopBookCache()
                    showClearBookCacheDialog = false
                }
            )
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
            val canEditEmbeddedMetadata = book.type == FileType.EPUB &&
                book.path?.let { File(it).isFile && File(it).canWrite() } == true
            val canRenameDisplayName = book.type != FileType.EPUB
            SharedBookInfoDialog(
                book = book,
                knownTags = state.allTags,
                initiallyEditing = bookInfoInitiallyEditing && (canEditEmbeddedMetadata || canRenameDisplayName),
                canEditEmbeddedMetadata = canEditEmbeddedMetadata,
                canRenameDisplayName = canRenameDisplayName,
                canRestoreEmbeddedMetadata = canEditEmbeddedMetadata,
                onDismiss = {
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                },
                onSave = { updated ->
                    updateBookMetadata(updated)
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                },
                onRestore = { restored ->
                    updateBookMetadata(restored)
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                }
            )
        }
    }
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

@Composable
private fun PdfReaderScreen(
    document: DesktopPdfDocument,
    initialPageIndex: Int,
    initialViewport: SharedPdfReaderViewport? = null,
    initialReaderSettings: ReaderSettings? = null,
    onReturnToLibrary: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
    onPageStateChange: (pageIndex: Int, progress: Float, viewport: SharedPdfReaderViewport) -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit = {},
    pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    onPdfHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit = {},
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    onLocalSidecarsChanged: () -> Unit = {},
    aiByokSettings: ReaderAiByokSettings,
    aiAdapter: DesktopByokAiAdapter,
    ttsAdapter: DesktopGeminiCloudTtsAdapter,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard
) {
    val documentHandleId = document.handleId
    val zoomSpec = remember { DesktopPdfZoomSpec }
    val restoredInitialViewport = remember(documentHandleId, initialViewport) {
        initialViewport?.sanitized(document.pageCount, zoomSpec)
    }
    var pdfReaderSettings by remember(documentHandleId) {
        mutableStateOf(initialReaderSettings.toDesktopPdfReaderSettings())
    }
    var pdfState by remember(documentHandleId) {
        mutableStateOf(
            SharedPdfReaderState.initial(
                pageCount = document.pageCount,
                initialPageIndex = restoredInitialViewport?.pageIndex ?: initialPageIndex,
                zoomSpec = zoomSpec
            ).copy(
                displayMode = restoredInitialViewport?.displayMode ?: DesktopDefaultPdfDisplayMode,
                zoom = restoredInitialViewport?.zoom ?: zoomSpec.clamp(zoomSpec.default)
            )
        )
    }
    var renderedPage by remember(documentHandleId) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderedPageIndex by remember(documentHandleId) { mutableStateOf<Int?>(null) }
    var renderedPageScale by remember(documentHandleId) { mutableStateOf<Float?>(null) }
    var renderError by remember(documentHandleId) { mutableStateOf<String?>(null) }
    var isRendering by remember(documentHandleId) { mutableStateOf(false) }
    var renderJob by remember(documentHandleId) { mutableStateOf<Job?>(null) }
    val zoomAnchorJob = remember(documentHandleId) { AtomicReference<Job?>(null) }
    val zoomCommitJob = remember(documentHandleId) { AtomicReference<Job?>(null) }
    var pdfZoomPreview by remember(documentHandleId) { mutableStateOf<DesktopPdfZoomPreview?>(null) }
    var activeTextDraft by remember(documentHandleId) { mutableStateOf<SharedPdfTextDraft?>(null) }
    var textStyleConfig by remember(documentHandleId) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var pageCanvasSize by remember(documentHandleId) { mutableStateOf(IntSize.Zero) }
    var pdfZoomViewportRootOffset by remember(documentHandleId) { mutableStateOf(Offset.Zero) }
    var paginatedPageRootOffset by remember(documentHandleId) { mutableStateOf(Offset.Zero) }
    val verticalPageRootOffsets = remember(documentHandleId) { mutableStateMapOf<Int, Offset>() }
    val paginatedRenderCache = remember(documentHandleId) { mutableStateMapOf<Int, DesktopPdfCachedPageRender>() }
    var activeStroke by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    var eraserPosition by remember(documentHandleId, pdfState.pageIndex, pdfState.selectedTool) { mutableStateOf<Offset?>(null) }
    var isHighlighterSnapEnabled by remember(documentHandleId) { mutableStateOf(false) }
    var selectionStartIndex by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<Offset?>(null) }
    var activeSelectionHandle by remember(documentHandleId, pdfState.pageIndex) { mutableStateOf<DesktopPdfSelectionHandle?>(null) }
    var pageScrubPreview by remember(documentHandleId) { mutableStateOf<Int?>(null) }
    var pageScrubStartPage by remember(documentHandleId) { mutableStateOf<Int?>(null) }
    var showPdfZoomIndicator by remember(documentHandleId) { mutableStateOf(false) }
    var isPdfZoomIndicatorInitialized by remember(documentHandleId) { mutableStateOf(false) }
    var jumpHistory by remember(documentHandleId) { mutableStateOf(SharedPdfJumpHistory()) }
    var externalLinkDialogUrl by remember(documentHandleId) { mutableStateOf<String?>(null) }
    var pdfExtrasState by remember(documentHandleId) {
        mutableStateOf(
            ReaderExtrasState(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = aiByokSettings.isCloudTtsAvailable,
                    cacheSummary = ttsAdapter.cacheSummary(document.title, aiByokSettings.sanitized().ttsSpeakerId)
                )
            )
        )
    }
    var pdfTtsJob by remember(documentHandleId) { mutableStateOf<Job?>(null) }
    val annotationFile = remember(documentHandleId) { desktopPdfAnnotationFile(document.path) }
    val bookmarkFile = remember(documentHandleId) { desktopPdfBookmarkFile(document.path) }
    val richTextFile = remember(documentHandleId) { desktopPdfRichTextFile(document.path) }
    val searchIndexFile = remember(documentHandleId) { desktopPdfSearchIndexFile(document.path) }
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val pdfScope = rememberCoroutineScope()
    var isFullscreen by remember(documentHandleId) { mutableStateOf(false) }
    val currentPdfFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnPdfFullscreenChange by rememberUpdatedState(onFullscreenChange)
    DisposableEffect(documentHandleId) {
        onDispose {
            renderJob?.cancel()
            pdfTtsJob?.cancel()
            zoomCommitJob.getAndSet(null)?.cancel()
            zoomAnchorJob.getAndSet(null)?.cancel()
            if (currentPdfFullscreen) {
                currentOnPdfFullscreenChange(false)
            }
            document.close()
        }
    }
    var isRichTextMode by remember(documentHandleId) { mutableStateOf(false) }
    var isRichTextLoaded by remember(documentHandleId) { mutableStateOf(false) }
    val richTextController = remember(documentHandleId) {
        SharedPdfRichTextController(
            scope = pdfScope,
            onDocumentChange = { richDocument ->
                if (isRichTextLoaded) {
                    SharedPdfRichTextLog.d(
                        "desktop.documentChange save path=\"${richTextFile.absolutePath.logPreview(160)}\" " +
                            "textLen=${richDocument.text.length} spans=${richDocument.spans.size}"
                    )
                    withContext(Dispatchers.IO) {
                        richTextFile.parentFile?.mkdirs()
                        richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
                    }
                    SharedPdfRichTextLog.d(
                        "desktop.documentChange saved path=\"${richTextFile.absolutePath.logPreview(160)}\" " +
                            "lastModified=${richTextFile.lastModified()}"
                    )
                    onLocalSidecarsChanged()
                } else {
                    SharedPdfRichTextLog.d(
                        "desktop.documentChange ignoredBeforeLoad path=\"${richTextFile.absolutePath.logPreview(160)}\" " +
                            "textLen=${richDocument.text.length} spans=${richDocument.spans.size}"
                    )
                }
            }
        )
    }
    val pageVerticalScrollState = rememberScrollState(
        initial = restoredInitialViewport?.paginatedVerticalScrollOffset ?: 0
    )
    val pageHorizontalScrollState = rememberScrollState(
        initial = restoredInitialViewport?.horizontalScrollOffset ?: 0
    )
    val verticalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredInitialViewport
            ?.takeIf { it.displayMode == PdfDisplayMode.VERTICAL_SCROLL }
            ?.verticalFirstPageIndex
            ?: pdfState.pageIndex,
        initialFirstVisibleItemScrollOffset = restoredInitialViewport
            ?.takeIf { it.displayMode == PdfDisplayMode.VERTICAL_SCROLL }
            ?.verticalFirstPageScrollOffset
            ?: 0
    )
    val pdfReaderFocusRequester = remember(documentHandleId) { FocusRequester() }
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentPdfAnnotations by rememberUpdatedState(pdfState.annotations)
    val currentPdfPageIndex by rememberUpdatedState(pdfState.pageIndex)
    val currentPdfScale by rememberUpdatedState(pdfState.zoom)
    val currentPdfDisplayMode by rememberUpdatedState(pdfState.displayMode)

    LaunchedEffect(isFullscreen, documentHandleId) {
        repeat(if (isFullscreen) 4 else 1) { attempt ->
            delay(if (attempt == 0) 80L else 120L)
            runCatching { pdfReaderFocusRequester.requestFocus() }
        }
    }

    fun clearPdfInteractionState() {
        activeStroke = emptyList()
        eraserPosition = null
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
        activeSelectionHandle = null
    }

    fun dispatchPdf(action: SharedPdfReaderAction) {
        val previousPage = pdfState.pageIndex
        val next = pdfState.reduce(action, zoomSpec)
        pdfState = next
        if (next.pageIndex != previousPage) {
            clearPdfInteractionState()
        }
    }

    fun setPdfFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        onFullscreenChange(enabled)
    }

    fun updatePdfReaderSettings(settings: ReaderSettings) {
        val nextSettings = settings.toDesktopPdfReaderSettings()
        pdfReaderSettings = nextSettings
        onReaderSettingsChange(nextSettings)
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
        SharedPdfRichTextLog.d(
            "desktop.textBox.select id=${annotation.id} page=${annotation.pageIndex} " +
                "richMode=$isRichTextMode textLen=${annotation.text.length}"
        )
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
        SharedPdfRichTextLog.d(
            "desktop.mode.activate page=${pdfState.pageIndex} " +
                "globalLen=${richTextController.globalTextFieldValue.text.length} layouts=${richTextController.pageLayouts.size}"
        )
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
        SharedPdfRichTextLog.d(
            "desktop.mode.deactivate page=${pdfState.pageIndex} save=$save " +
                "activePage=${richTextController.activePageIndex} globalLen=${richTextController.globalTextFieldValue.text.length}"
        )
        isRichTextMode = false
        if (save) {
            pdfScope.launch { richTextController.saveImmediate() }
        } else {
            richTextController.clearSelection()
        }
    }

    fun selectPdfAnnotationTool(tool: PdfInkTool) {
        SharedPdfRichTextLog.d(
            "desktop.tool.select tool=$tool richMode=$isRichTextMode page=${pdfState.pageIndex}"
        )
        val previousTool = pdfState.selectedTool
        deactivateRichTextMode()
        if (tool != PdfInkTool.TEXT) {
            commitActiveTextDraft()
        }
        if (pdfState.isTextSelectionMode) {
            dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(false))
            clearPdfInteractionState()
        }
        if (previousTool != tool) {
            dispatchPdf(SharedPdfReaderAction.ToolSelected(tool))
        }
        if (tool.isDesktopHighlighter && previousTool != tool) {
            pdfHighlighterPalette.sanitized().colors.firstOrNull()?.let { colorArgb ->
                dispatchPdf(SharedPdfReaderAction.ColorSelected(colorArgb))
            }
        }
    }

    val pageIndex = pdfState.pageIndex
    val scale = pdfState.zoom
    val displayMode = pdfState.displayMode
    val zoomControlScale = pdfZoomPreview?.zoom ?: scale
    val shouldShowPdfZoomIndicator = abs(zoomControlScale - 1f) > 0.001f

    LaunchedEffect(zoomControlScale, document.path) {
        if (!isPdfZoomIndicatorInitialized) {
            isPdfZoomIndicatorInitialized = true
            showPdfZoomIndicator = false
            return@LaunchedEffect
        }
        if (shouldShowPdfZoomIndicator) {
            showPdfZoomIndicator = true
            delay(1_500)
            showPdfZoomIndicator = false
        } else {
            showPdfZoomIndicator = false
        }
    }

    fun verticalZoomAnchorItem(anchor: Offset) = verticalListState.layoutInfo.visibleItemsInfo
        .firstOrNull { item ->
            anchor.y >= item.offset.toFloat() && anchor.y <= (item.offset + item.size).toFloat()
        }
        ?: verticalListState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
            when {
                anchor.y < item.offset.toFloat() -> item.offset.toFloat() - anchor.y
                anchor.y > (item.offset + item.size).toFloat() -> anchor.y - (item.offset + item.size).toFloat()
                else -> 0f
            }
        }

    LaunchedEffect(scale, displayMode, pageIndex) {
        val preview = pdfZoomPreview ?: return@LaunchedEffect
        if (
            preview.displayMode != displayMode ||
            (preview.pageIndex != pageIndex && displayMode == PdfDisplayMode.PAGINATION) ||
            abs(preview.baseZoom - scale) > 0.0001f
        ) {
            pdfZoomPreview = null
            zoomCommitJob.getAndSet(null)?.cancel()
        }
    }

    fun applyAnchoredPdfZoom(oldZoom: Float, newZoom: Float, anchor: Offset?) {
        val activePageIndex = currentPdfPageIndex
        val activeDisplayMode = currentPdfDisplayMode
        logPdfZoomPerf {
            "commit_start mode=$activeDisplayMode page=${activePageIndex + 1} old=${oldZoom.formatLogFloat()} " +
                "new=${newZoom.formatLogFloat()} anchor=${anchor.formatLogOffset()} " +
                "renderPage=${renderedPageIndex?.let { it + 1 } ?: "none"} " +
                "renderScale=${renderedPageScale?.formatLogFloat() ?: "none"} " +
                "renderJobActive=${renderJob?.isActive == true}"
        }
        pdfZoomPreview = null
        val viewportRootOffsetAtZoomStart = pdfZoomViewportRootOffset
        val pageRootOffsetAtZoomStart = paginatedPageRootOffset
        val targetHorizontalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(pageHorizontalScrollState.value, it.x, oldZoom, newZoom)
        }
        val targetVerticalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(pageVerticalScrollState.value, it.y, oldZoom, newZoom)
        }
        val targetVerticalItem = if (activeDisplayMode == PdfDisplayMode.VERTICAL_SCROLL && anchor != null) {
            verticalZoomAnchorItem(anchor)
                ?.let { item ->
                    val fallbackOffset = desktopPdfAnchoredLazyItemScrollOffset(
                        itemOffset = item.offset,
                        anchor = anchor.y,
                        oldZoom = oldZoom,
                        newZoom = newZoom
                    )
                    val pageRootOffset = verticalPageRootOffsets[item.index]
                    Triple(item.index, fallbackOffset, pageRootOffset)
                }
        } else {
            null
        }
        dispatchPdf(SharedPdfReaderAction.ZoomChanged(newZoom))
        if (anchor != null) {
            val nextAnchorJob = pdfScope.launch {
                withFrameNanos { }
                when (activeDisplayMode) {
                    PdfDisplayMode.PAGINATION -> {
                        suspend fun correctPageAnchor() {
                            val pageDelta = desktopPdfAnchoredPageScrollDelta(
                                viewportRootOffset = viewportRootOffsetAtZoomStart,
                                oldPageRootOffset = pageRootOffsetAtZoomStart,
                                currentPageRootOffset = paginatedPageRootOffset,
                                anchor = anchor,
                                oldZoom = oldZoom,
                                newZoom = newZoom
                            )
                            if (pageDelta != null) {
                                if (abs(pageDelta.x) > 1) {
                                    pageHorizontalScrollState.scrollTo(
                                        (pageHorizontalScrollState.value + pageDelta.x).coerceAtLeast(
                                            0
                                        )
                                    )
                                }
                                if (abs(pageDelta.y) > 1) {
                                    pageVerticalScrollState.scrollTo(
                                        (pageVerticalScrollState.value + pageDelta.y).coerceAtLeast(
                                            0
                                        )
                                    )
                                }
                            } else if (targetHorizontalScroll != null && targetVerticalScroll != null) {
                                pageHorizontalScrollState.scrollTo(targetHorizontalScroll)
                                pageVerticalScrollState.scrollTo(targetVerticalScroll)
                            }
                        }
                        correctPageAnchor()
                        withFrameNanos { }
                        correctPageAnchor()
                    }

                    PdfDisplayMode.VERTICAL_SCROLL -> {
                        suspend fun correctVerticalAnchor() {
                            val oldPageRootOffset = targetVerticalItem?.third
                            val currentPageRootOffset =
                                targetVerticalItem?.first?.let { verticalPageRootOffsets[it] }
                            val pageDelta =
                                if (oldPageRootOffset != null && currentPageRootOffset != null) {
                                    desktopPdfAnchoredPageScrollDelta(
                                        viewportRootOffset = viewportRootOffsetAtZoomStart,
                                        oldPageRootOffset = oldPageRootOffset,
                                        currentPageRootOffset = currentPageRootOffset,
                                        anchor = anchor,
                                        oldZoom = oldZoom,
                                        newZoom = newZoom
                                    )
                                } else {
                                    null
                                }
                            if (pageDelta != null) {
                                if (abs(pageDelta.x) > 1) {
                                    pageHorizontalScrollState.scrollTo(
                                        (pageHorizontalScrollState.value + pageDelta.x).coerceAtLeast(
                                            0
                                        )
                                    )
                                }
                                if (abs(pageDelta.y) > 1) {
                                    verticalListState.scrollBy(pageDelta.y.toFloat())
                                }
                            } else {
                                targetHorizontalScroll?.let { pageHorizontalScrollState.scrollTo(it) }
                                targetVerticalItem?.let { (itemIndex, scrollOffset, _) ->
                                    verticalListState.scrollToItem(itemIndex, scrollOffset)
                                }
                            }
                        }
                        correctVerticalAnchor()
                        withFrameNanos { }
                        correctVerticalAnchor()
                    }
                }
            }
            zoomAnchorJob.getAndSet(nextAnchorJob)?.cancel()
        }
    }

    fun previewAnchoredPdfZoom(oldZoom: Float, newZoom: Float, anchor: Offset?) {
        val activePageIndex = currentPdfPageIndex
        val activeScale = currentPdfScale
        val activeDisplayMode = currentPdfDisplayMode
        logPdfZoomPerf {
            "preview mode=$activeDisplayMode page=${activePageIndex + 1} old=${oldZoom.formatLogFloat()} " +
                "new=${newZoom.formatLogFloat()} anchor=${anchor.formatLogOffset()} " +
                "hasRender=${renderedPage != null && renderedPageIndex == activePageIndex} " +
                "renderScale=${renderedPageScale?.formatLogFloat() ?: "none"} " +
                "renderJobActive=${renderJob?.isActive == true} cacheKeys=${paginatedRenderCache.keys.sorted().map { it + 1 }}"
        }
        val existingPreview = pdfZoomPreview
        val baseZoom = existingPreview
            ?.takeIf { it.displayMode == activeDisplayMode && it.baseZoom.isFinite() && it.baseZoom > 0f }
            ?.baseZoom
            ?: oldZoom.takeIf { it.isFinite() && it > 0f }
            ?: activeScale
        val previewPageIndex = when (activeDisplayMode) {
            PdfDisplayMode.PAGINATION -> activePageIndex
            PdfDisplayMode.VERTICAL_SCROLL -> anchor?.let(::verticalZoomAnchorItem)?.index ?: activePageIndex
        }
        pdfZoomPreview = DesktopPdfZoomPreview(
            baseZoom = baseZoom,
            zoom = newZoom,
            anchor = anchor,
            displayMode = activeDisplayMode,
            pageIndex = previewPageIndex
        )
        val nextCommitJob = pdfScope.launch {
            delay(DesktopPdfZoomCommitDebounceMillis)
            val preview = pdfZoomPreview ?: return@launch
            pdfZoomPreview = null
            applyAnchoredPdfZoom(preview.baseZoom, preview.zoom, preview.anchor)
        }
        zoomCommitJob.getAndSet(nextCommitJob)?.cancel()
        if (
            activeDisplayMode == PdfDisplayMode.PAGINATION &&
            renderedPage != null &&
            renderedPageIndex == activePageIndex
        ) {
            renderJob?.cancel()
        }
    }

    fun cancelPendingPdfZoomPreview() {
        pdfZoomPreview = null
        zoomCommitJob.getAndSet(null)?.cancel()
    }

    fun cachePaginatedRender(page: Int, renderScale: Float, render: DesktopPdfPageRender) {
        paginatedRenderCache[page] = DesktopPdfCachedPageRender(render, renderScale)
        val activePageIndex = currentPdfPageIndex
        val keepRange =
            (activePageIndex - DesktopPdfPaginationRenderCacheRadius)..(activePageIndex + DesktopPdfPaginationRenderCacheRadius)
        val evictedPages = paginatedRenderCache.keys
            .filter { it !in keepRange }
        evictedPages.forEach { paginatedRenderCache.remove(it) }
        logPdfZoomPerf {
            "cache_put page=${page + 1} scale=${renderScale.formatLogFloat()} " +
                "bitmap=${render.width}x${render.height} current=${activePageIndex + 1} " +
                "keys=${paginatedRenderCache.keys.sorted().map { it + 1 }} evicted=${evictedPages.map { it + 1 }}"
        }
    }

    LaunchedEffect(documentHandleId, pageIndex, displayMode) {
        runCatching { pdfReaderFocusRequester.requestFocus() }
    }

    val searchQuery = pdfState.searchQuery
    val isPdfSearchActive = pdfState.isSearchActive
    val showPdfSearchResultsPanel = pdfState.showSearchResultsPanel
    val activeSearchIndex = pdfState.activeSearchResultIndex
    val searchHighlightMode = pdfState.searchHighlightMode
    val selectedTool = pdfState.selectedTool
    val selectedColor = pdfState.selectedColorArgb
    val strokeWidth = pdfState.strokeWidth
    val pdfHighlighterColors = pdfHighlighterPalette.sanitized().colors
    val isTextSelectionMode = pdfState.isTextSelectionMode
    val bookmarks = pdfState.bookmarks
    val selectedAnnotationId = pdfState.selectedAnnotationId
    val annotations = pdfState.annotations
    val canGoPrevious = pdfState.canGoPrevious
    val canGoNext = pdfState.canGoNext
    val progressPercent = pdfState.progressPercent
    val latestOnPageStateChange by rememberUpdatedState(onPageStateChange)

    fun pdfViewportSnapshot(): SharedPdfReaderViewport {
        val state = pdfState
        return SharedPdfReaderViewport(
            pageIndex = state.pageIndex,
            displayMode = state.displayMode,
            zoom = pdfZoomPreview?.zoom ?: state.zoom,
            horizontalScrollOffset = pageHorizontalScrollState.value,
            paginatedVerticalScrollOffset = pageVerticalScrollState.value,
            verticalFirstPageIndex = verticalListState.firstVisibleItemIndex,
            verticalFirstPageScrollOffset = verticalListState.firstVisibleItemScrollOffset
        ).sanitized(document.pageCount, zoomSpec)
    }

    fun pdfProgressPercentFor(pageIndex: Int): Float {
        return ((pageIndex + 1).toFloat() / document.pageCount.coerceAtLeast(1)) * 100f
    }

    var latestPdfViewport by remember(documentHandleId) {
        mutableStateOf(restoredInitialViewport ?: pdfViewportSnapshot())
    }

    fun persistPdfViewport(viewport: SharedPdfReaderViewport = pdfViewportSnapshot()) {
        latestPdfViewport = viewport
        latestOnPageStateChange(viewport.pageIndex, pdfProgressPercentFor(viewport.pageIndex), viewport)
    }

    val pdfThemeStyle = remember(pdfReaderSettings, displayMode) {
        pdfReaderSettings.toDesktopPdfThemeStyle(displayMode)
    }
    val verticalRenderWindow = remember(pageIndex, document.pageCount) {
        val start = (pageIndex - 1).coerceAtLeast(0)
        val end = (pageIndex + 1).coerceAtMost((document.pageCount - 1).coerceAtLeast(0))
        start..end
    }
    var arePdfAnnotationsLoaded by remember(documentHandleId) { mutableStateOf(false) }
    var arePdfBookmarksLoaded by remember(documentHandleId) { mutableStateOf(false) }
    var indexedSearchPageCount by remember(documentHandleId) { mutableStateOf(document.indexedSearchTextPageCount()) }
    var isSearchIndexing by remember(documentHandleId) { mutableStateOf(false) }
    var searchResults by remember(documentHandleId) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var selectedEmbeddedAnnotationId by remember(documentHandleId) { mutableStateOf<String?>(null) }
    val selectedAnnotation = remember(annotations, selectedAnnotationId) {
        annotations.firstOrNull { it.id == selectedAnnotationId }
    }
    val selectedTextHighlight = selectedAnnotation?.takeIf { it.isDesktopTextSelectionHighlight }
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
    val activePdfTtsChunk = pdfExtrasState.cloudTts.progress.currentChunk

    LaunchedEffect(selectedTool) {
        activeStroke = emptyList()
        eraserPosition = null
    }

    fun updatePdfHighlighterPalette(nextPalette: SharedPdfHighlighterPalette) {
        val previousSlot = pdfHighlighterPalette.sanitized().colors.indexOf(selectedColor)
        val sanitizedPalette = nextPalette.sanitized()
        onPdfHighlighterPaletteChange(sanitizedPalette)
        if (selectedTool.isDesktopHighlighter && selectedColor !in sanitizedPalette.colors) {
            val colorArgb = sanitizedPalette.colors.getOrNull(previousSlot)
                ?: sanitizedPalette.colors.firstOrNull()
            colorArgb?.let { nextSelectedColor ->
                dispatchPdf(SharedPdfReaderAction.ColorSelected(nextSelectedColor))
            }
        }
    }

    fun currentPdfTtsCacheSummary() =
        ttsAdapter.cacheSummary(document.title, aiByokSettings.sanitized().ttsSpeakerId)

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    val pdfPopupActive =
        externalLinkDialogUrl != null ||
            selectedTextHighlight != null ||
            selectedEmbeddedAnnotation != null ||
            pdfExtrasState.aiResult.hasContent ||
            (textSelection != null && selectionMenuOffset != null)
    LaunchedEffect(pdfPopupActive, documentHandleId) {
        if (!pdfPopupActive) {
            delay(120L)
            runCatching { pdfReaderFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(aiByokSettings) {
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = pdfExtrasState.cloudTts.copy(
                isAvailable = aiByokSettings.isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
    }

    LaunchedEffect(documentHandleId) {
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

    LaunchedEffect(documentHandleId, annotations, arePdfAnnotationsLoaded) {
        if (!arePdfAnnotationsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                annotationFile.parentFile?.mkdirs()
                annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
            }
        }
        onLocalSidecarsChanged()
    }

    LaunchedEffect(documentHandleId) {
        isRichTextLoaded = false
        SharedPdfRichTextLog.d(
            "desktop.loadRichText start path=\"${richTextFile.absolutePath.logPreview(160)}\" exists=${richTextFile.exists()}"
        )
        val loadedRichText = withContext(Dispatchers.IO) {
            if (richTextFile.exists()) {
                val raw = richTextFile.readText()
                SharedPdfRichTextLog.d(
                    "desktop.loadRichText read path=\"${richTextFile.absolutePath.logPreview(160)}\" rawLen=${raw.length}"
                )
                SharedPdfRichTextSerializer.decode(raw)
            } else {
                SharedPdfRichDocument()
            }
        }
        SharedPdfRichTextLog.d(
            "desktop.loadRichText decoded textLen=${loadedRichText.text.length} spans=${loadedRichText.spans.size}"
        )
        richTextController.replaceDocument(loadedRichText)
        isRichTextLoaded = true
        SharedPdfRichTextLog.d("desktop.loadRichText ready")
    }

    LaunchedEffect(documentHandleId) {
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

    LaunchedEffect(documentHandleId, bookmarks, arePdfBookmarksLoaded) {
        if (!arePdfBookmarksLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                bookmarkFile.parentFile?.mkdirs()
                bookmarkFile.writeText(SharedPdfBookmarkSerializer.encode(bookmarks))
            }
        }
        onLocalSidecarsChanged()
    }

    LaunchedEffect(documentHandleId) {
        val restoredPageCount = withContext(Dispatchers.IO) {
            restoreDesktopPdfSearchIndex(document, searchIndexFile)
        }
        indexedSearchPageCount = restoredPageCount
        isSearchIndexing = indexedSearchPageCount < document.pageCount
        logPdfZoomPerf {
            "search_index_restore indexed=$indexedSearchPageCount/${document.pageCount} active=$isSearchIndexing"
        }
        withContext(Dispatchers.IO) {
            DesktopPdfium.indexSearchPages(
                document = document,
                onProgress = { indexed, _ ->
                    indexedSearchPageCount = indexed
                    logPdfZoomPerf { "search_index_progress indexed=$indexed/${document.pageCount}" }
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
        logPdfZoomPerf { "search_index_done indexed=$indexedSearchPageCount/${document.pageCount}" }
    }

    LaunchedEffect(documentHandleId, searchQuery, indexedSearchPageCount) {
        val normalizedQuery = searchQuery.trim()
        searchResults = if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                DesktopPdfium.search(document, normalizedQuery)
            }
        }
    }

    fun goToPage(
        target: Int,
        scrollVertical: Boolean = true,
        recordJump: Boolean = false,
        saveRichTextBeforePageChange: Boolean = true
    ) {
        val clampedTarget = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        val currentPage = pdfState.pageIndex
        SharedPdfRichTextLog.d(
            "desktop.goToPage target=$target clamped=$clampedTarget current=$currentPage " +
                "richMode=$isRichTextMode scrollVertical=$scrollVertical recordJump=$recordJump " +
                "saveRich=$saveRichTextBeforePageChange activePage=${richTextController.activePageIndex}"
        )
        if (clampedTarget != currentPage) {
            commitActiveTextDraft()
            if (isRichTextMode && saveRichTextBeforePageChange) {
                SharedPdfRichTextLog.d("desktop.goToPage savingRichTextBeforePageChange from=$currentPage to=$clampedTarget")
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

    fun updatePdfPageScrub(value: Float) {
        if (pageScrubStartPage == null) {
            pageScrubStartPage = pdfState.pageIndex
        }
        val targetPage = value.roundToInt().coerceIn(0, document.pageCount - 1)
        pageScrubPreview = targetPage
        goToPage(targetPage)
    }

    fun finishPdfPageScrub() {
        val startPage = pageScrubStartPage
        val targetPage = pdfState.pageIndex
        if (startPage != null) {
            jumpHistory = jumpHistory.record(
                currentPageIndex = startPage,
                targetPageIndex = targetPage,
                pageCount = document.pageCount
            )
        }
        pageScrubStartPage = null
        pageScrubPreview = null
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
                if (featurePolicy.externalLookup) {
                    externalLinkDialogUrl = url
                }
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

    fun highlightSelection(
        pageIndex: Int,
        selection: DesktopPdfTextSelection,
        canvasSize: IntSize,
        colorArgb: Int = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).colorArgb
    ) {
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
                    colorArgb = SharedPdfAndroidHighlightColors.nearestArgb(colorArgb),
                    rangeStartIndex = selection.startIndex,
                    rangeEndIndex = selection.endIndex,
                    createdAt = now
                )
            )
        )
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
    }

    fun clearSelection() {
        textSelection = null
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        selectionMenuOffset = null
        activeSelectionHandle = null
    }

    fun openPdfExternalLookup(action: ReaderExternalLookupAction, text: String) {
        if (!featurePolicy.externalLookup) return
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        openExternalUrl(externalLookupUrl(action, normalizedText.take(1800)))
    }

    fun currentPdfPageText(maxChars: Int = 8000): String {
        return runCatching { document.textPageData(pageIndex).text.trim().take(maxChars) }.getOrDefault("")
    }

    fun pdfTtsChunksForPages(pageIndices: Iterable<Int>): List<ReaderTtsChunk> {
        val chunks = mutableListOf<ReaderTtsChunk>()
        pageIndices.forEach { targetPage ->
            if (targetPage !in 0 until document.pageCount) return@forEach
            val pageText = runCatching { document.textPageData(targetPage).text }.getOrDefault("")
            ReaderTtsPlanner.chunksForText(
                text = pageText,
                pageIndex = targetPage,
                chapterIndex = 0,
                chapterTitle = "Page ${targetPage + 1}"
            ).forEach { chunk ->
                chunks += chunk.copy(index = chunks.size)
            }
        }
        return chunks
    }

    fun pdfTtsChunksForScope(readScope: ReaderTtsReadScope, startPageIndex: Int = pageIndex): List<ReaderTtsChunk> {
        return when (readScope) {
            ReaderTtsReadScope.PAGE -> pdfTtsChunksForPages(listOf(startPageIndex))
            ReaderTtsReadScope.CHAPTER,
            ReaderTtsReadScope.BOOK -> pdfTtsChunksForPages(startPageIndex until document.pageCount)
        }
    }

    fun pdfTextBeforeCurrentPage(maxChars: Int = 24_000): String {
        val indexedText = document.indexedSearchPages()
            .filter { it.pageIndex <= pageIndex }
            .joinToString("\n\n") { "Page ${it.pageIndex + 1}\n${it.text}" }
            .trim()
        return indexedText.ifBlank { currentPdfPageText(maxChars) }.takeLast(maxChars)
    }

    fun updatePdfAutoScroll(autoScroll: ReaderAutoScrollState) {
        pdfExtrasState = pdfExtrasState.copy(autoScroll = autoScroll.sanitized())
    }

    fun pdfCloudTtsStoppedState(statusMessage: String? = null, errorMessage: String? = null) = ReaderCloudTtsState(
        isAvailable = aiByokSettings.sanitized().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = currentPdfTtsCacheSummary()
    )

    fun runPdfAiAction(feature: ReaderAiFeature, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        if (!aiByokSettings.sanitized().areReaderAiFeaturesAvailable) return
        pdfExtrasState = pdfExtrasState.copy(
            aiResult = ReaderAiResultState(
                title = feature.displayName,
                isLoading = true
            )
        )
        pdfScope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> aiAdapter.define(normalizedText.take(2400), currentPdfPageText()).let { it.definition to it.error }
                ReaderAiFeature.SUMMARIZE -> aiAdapter.summarize(normalizedText).let { it.summary to it.error }
                ReaderAiFeature.RECAP -> aiAdapter.recap(normalizedText).let { it.recap to it.error }
            }
            pdfExtrasState = pdfExtrasState.copy(
                aiResult = ReaderAiResultState(
                    title = feature.displayName,
                    text = result.first.orEmpty(),
                    errorMessage = result.second,
                    isLoading = false
                )
            )
        }
    }

    fun stopPdfCloudTts() {
        logDesktopTts("pdf_stop_requested")
        pdfTtsJob?.cancel()
        pdfTtsJob = null
        pdfScope.launch {
            ttsAdapter.stop()
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = pdfCloudTtsStoppedState(statusMessage = "Stopped")
            )
        }
    }

    fun pauseResumePdfCloudTts() {
        val current = pdfExtrasState.cloudTts
        if (current.isPaused) {
            pdfScope.launch {
                ttsAdapter.resume()
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = pdfExtrasState.cloudTts.copy(
                        isPaused = false,
                        isPlaying = true,
                        statusMessage = pdfExtrasState.cloudTts.progress.currentPositionLabel ?: "Reading"
                    )
                )
            }
        } else if (current.isPlaying) {
            pdfScope.launch {
                ttsAdapter.pause()
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = pdfExtrasState.cloudTts.copy(
                        isPlaying = false,
                        isPaused = true,
                        statusMessage = "Paused"
                    )
                )
            }
        }
    }

    fun clearPdfCloudTtsCache() {
        ttsAdapter.clearBookCacheForSpeaker(document.title, aiByokSettings.sanitized().ttsSpeakerId)
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = pdfExtrasState.cloudTts.copy(
                statusMessage = "Voice cache cleared",
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
    }

    fun startPdfCloudTts(readScope: ReaderTtsReadScope) {
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "pdf_sequence_toggle scope=${readScope.name} startPage=${pageIndex + 1} " +
                "isPlaying=${pdfExtrasState.cloudTts.isPlaying} isLoading=${pdfExtrasState.cloudTts.isLoading} " +
                "keyPresent=${settings.geminiKey.isNotBlank()} ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" " +
                "available=${ttsAdapter.isAvailable}"
        )
        if (pdfExtrasState.cloudTts.isPlaying || pdfExtrasState.cloudTts.isLoading || pdfExtrasState.cloudTts.isPaused) {
            stopPdfCloudTts()
            return
        }
        if (!ttsAdapter.isAvailable) {
            logDesktopTts("pdf_sequence_blocked reason=adapter_unavailable")
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = "Add a Gemini key and select Gemini cloud TTS in AI keys and models.",
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        val ttsSessionId = System.currentTimeMillis()
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = ReaderCloudTtsState(
                isAvailable = true,
                isLoading = true,
                statusMessage = "Preparing ${readScope.label.lowercase()}",
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
        val noTextMessage = "There is no text here to read."
        pdfTtsJob = pdfScope.launch {
            var completedChunkCount = 0
            runCatching {
                val ttsChunks = withContext(Dispatchers.IO) {
                    pdfTtsChunksForScope(readScope, pageIndex)
                        .filter { it.text.isNotBlank() }
                        .withTtsReplacements(ttsReplacementPreferences, document.path)
                }
                if (ttsChunks.isEmpty()) {
                    logDesktopTts("pdf_sequence_ignored reason=blank_text scope=${readScope.name}")
                    throw IllegalStateException(noTextMessage)
                }
                val initialProgress = ReaderTtsProgress(
                    sessionId = ttsSessionId,
                    scope = readScope,
                    chunks = ttsChunks,
                    currentChunkIndex = -1
                )
                logDesktopTts("pdf_sequence_start scope=${readScope.name} chunks=${ttsChunks.size}")
                ttsAdapter.speakChunks(document.title, readScope, ttsChunks) { index ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("PDF cloud TTS stopped")
                    val chunk = ttsChunks[index]
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    if (chunk.pageIndex != pdfState.pageIndex) {
                        goToPage(chunk.pageIndex, recordJump = false)
                    }
                    pdfExtrasState = pdfExtrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = true,
                            isPlaying = true,
                            statusMessage = progress.currentPositionLabel ?: "Reading",
                            progress = progress,
                            cacheSummary = currentPdfTtsCacheSummary()
                        )
                    )
                    logDesktopTts(
                        "pdf_chunk_start scope=${readScope.name} index=${index + 1}/${ttsChunks.size} " +
                            "page=${chunk.pageIndex + 1} offsets=${chunk.startOffset}..${chunk.endOffset} chars=${chunk.text.length}"
                    )
                    completedChunkCount = index + 1
                }
            }.onFailure { error ->
                logDesktopTts("pdf_sequence_failed error=\"${error.desktopTtsSummary()}\"")
                if (error !is kotlinx.coroutines.CancellationException && error.message != noTextMessage) error.printStackTrace()
                pdfExtrasState = if (error is kotlinx.coroutines.CancellationException) {
                    pdfExtrasState.copy(
                        cloudTts = pdfCloudTtsStoppedState(statusMessage = "Stopped")
                    )
                } else {
                    pdfExtrasState.copy(
                        cloudTts = pdfCloudTtsStoppedState(errorMessage = error.message ?: "Cloud TTS failed.")
                    )
                }
            }.onSuccess {
                logDesktopTts("pdf_sequence_success chunks=$completedChunkCount")
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = pdfCloudTtsStoppedState(statusMessage = "Finished")
                )
            }
        }
    }

    fun togglePdfCloudTts(text: String) {
        val normalizedText = text.trim()
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "pdf_toggle textChars=${normalizedText.length} isPlaying=${pdfExtrasState.cloudTts.isPlaying} " +
                "isLoading=${pdfExtrasState.cloudTts.isLoading} keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" available=${ttsAdapter.isAvailable}"
        )
        if (pdfExtrasState.cloudTts.isPlaying || pdfExtrasState.cloudTts.isLoading || pdfExtrasState.cloudTts.isPaused) {
            stopPdfCloudTts()
            return
        }
        if (normalizedText.isBlank()) {
            logDesktopTts("pdf_toggle_ignored reason=blank_text")
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = pdfExtrasState.cloudTts.copy(
                    errorMessage = "There is no text on this page to read.",
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        if (!ttsAdapter.isAvailable) {
            logDesktopTts("pdf_toggle_blocked reason=adapter_unavailable")
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = "Add a Gemini key and select Gemini cloud TTS in AI keys and models.",
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        val selectionChunks = ReaderTtsPlanner.chunksForText(
            text = normalizedText,
            pageIndex = pageIndex,
            chapterIndex = 0,
            chapterTitle = "Page ${pageIndex + 1}"
        ).withTtsReplacements(ttsReplacementPreferences, document.path)
        if (selectionChunks.isEmpty()) {
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = pdfExtrasState.cloudTts.copy(
                    errorMessage = "There is no text on this page to read.",
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        pdfTtsJob = null
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = pdfExtrasState.cloudTts.copy(cacheSummary = currentPdfTtsCacheSummary())
        )
        pdfTtsJob = pdfScope.launch {
            val initialProgress = ReaderTtsProgress(
                sessionId = System.currentTimeMillis(),
                scope = ReaderTtsReadScope.PAGE,
                chunks = selectionChunks,
                currentChunkIndex = -1
            )
            runCatching {
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = ReaderCloudTtsState(
                        isAvailable = true,
                        isLoading = true,
                        statusMessage = "Preparing selection",
                        progress = initialProgress,
                        cacheSummary = currentPdfTtsCacheSummary()
                    )
                )
                ttsAdapter.speakChunks(document.title, ReaderTtsReadScope.PAGE, selectionChunks) { index ->
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    pdfExtrasState = pdfExtrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = true,
                            isPlaying = true,
                            statusMessage = progress.currentPositionLabel ?: "Reading",
                            progress = progress,
                            cacheSummary = currentPdfTtsCacheSummary()
                        )
                    )
                }
            }.onFailure { error ->
                logDesktopTts("pdf_job_failed error=\"${error.desktopTtsSummary()}\"")
                if (error !is kotlinx.coroutines.CancellationException) error.printStackTrace()
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = if (error is kotlinx.coroutines.CancellationException) {
                        pdfCloudTtsStoppedState(statusMessage = "Stopped")
                    } else {
                        pdfCloudTtsStoppedState(errorMessage = error.message ?: "Cloud TTS failed.")
                    }
                )
            }.onSuccess {
                logDesktopTts("pdf_job_success")
                pdfExtrasState = pdfExtrasState.copy(
                    cloudTts = pdfCloudTtsStoppedState(statusMessage = "Finished")
                )
            }
        }
    }

    fun updateAnnotation(annotation: SharedPdfAnnotation) {
        dispatchPdf(SharedPdfReaderAction.AnnotationUpdated(annotation))
    }

    fun deleteAnnotation(annotationId: String) {
        dispatchPdf(SharedPdfReaderAction.AnnotationDeleted(annotationId))
    }

    fun goToAnnotation(annotation: SharedPdfAnnotation) {
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
        selectedEmbeddedAnnotationId = null
        goToPage(annotation.pageIndex, recordJump = true)
    }

    fun selectAnnotation(annotation: SharedPdfAnnotation?) {
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(annotation?.id))
        annotation?.let { goToPage(it.pageIndex, recordJump = true) }
    }

    fun goToEmbeddedAnnotation(annotation: SharedPdfEmbeddedAnnotation) {
        dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
        selectedEmbeddedAnnotationId = null
        goToPage(annotation.pageIndex, recordJump = true)
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

    LaunchedEffect(documentHandleId, document.pageCount) {
        jumpHistory = jumpHistory.pruned(document.pageCount)
    }

    LaunchedEffect(documentHandleId, document.pageCount) {
        snapshotFlow { pdfViewportSnapshot() }
            .distinctUntilChanged()
            .collectLatest { viewport ->
                latestPdfViewport = viewport
                delay(DesktopPdfViewportPersistDebounceMillis)
                persistPdfViewport(viewport)
            }
    }

    DisposableEffect(documentHandleId) {
        onDispose {
            persistPdfViewport()
        }
    }

    var pendingInitialViewportRestore by remember(documentHandleId) { mutableStateOf(restoredInitialViewport) }
    LaunchedEffect(documentHandleId, displayMode) {
        if (displayMode == PdfDisplayMode.VERTICAL_SCROLL && pageIndex in 0 until document.pageCount) {
            if (pendingInitialViewportRestore?.displayMode == PdfDisplayMode.VERTICAL_SCROLL) return@LaunchedEffect
            verticalListState.scrollToItem(pageIndex)
        }
    }

    LaunchedEffect(
        documentHandleId,
        pendingInitialViewportRestore,
        displayMode,
        renderedPageIndex,
        renderedPageScale
    ) {
        val viewport = pendingInitialViewportRestore ?: return@LaunchedEffect
        if (viewport.displayMode != displayMode) {
            pendingInitialViewportRestore = null
            return@LaunchedEffect
        }
        when (viewport.displayMode) {
            PdfDisplayMode.PAGINATION -> {
                if (renderedPageIndex != viewport.pageIndex) return@LaunchedEffect
                withFrameNanos { }
                pageHorizontalScrollState.scrollTo(viewport.horizontalScrollOffset)
                pageVerticalScrollState.scrollTo(viewport.paginatedVerticalScrollOffset)
                pendingInitialViewportRestore = null
                latestPdfViewport = viewport
            }

            PdfDisplayMode.VERTICAL_SCROLL -> {
                withFrameNanos { }
                verticalListState.scrollToItem(
                    viewport.verticalFirstPageIndex,
                    viewport.verticalFirstPageScrollOffset
                )
                pageHorizontalScrollState.scrollTo(viewport.horizontalScrollOffset)
                pendingInitialViewportRestore = null
                latestPdfViewport = viewport
            }
        }
    }

    fun selectPdfPanMode() {
        SharedPdfRichTextLog.d(
            "desktop.tool.select tool=${PdfInkTool.NONE} richMode=$isRichTextMode page=${pdfState.pageIndex}"
        )
        deactivateRichTextMode()
        commitActiveTextDraft()
        clearPdfInteractionState()
        if (pdfState.isTextSelectionMode) {
            dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(false))
        }
        dispatchPdf(SharedPdfReaderAction.ToolSelected(PdfInkTool.NONE))
    }

    LaunchedEffect(pdfExtrasState.autoScroll.sanitized(), pageIndex, canGoNext, displayMode) {
        val autoScroll = pdfExtrasState.autoScroll.sanitized()
        if (!autoScroll.enabled) return@LaunchedEffect
        if (!canGoNext) {
            updatePdfAutoScroll(autoScroll.copy(enabled = false))
            return@LaunchedEffect
        }
        val delayMs = (180_000f / autoScroll.speed).roundToInt().coerceIn(1_200, 12_000)
        delay(delayMs.toLong())
        goToPage(pageIndex + 1)
    }

    LaunchedEffect(documentHandleId, displayMode, verticalListState) {
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

    LaunchedEffect(documentHandleId, pageIndex, scale, displayMode) {
        renderJob?.cancel()
        if (displayMode != PdfDisplayMode.PAGINATION) {
            isRendering = false
            renderError = null
            renderedPage = null
            renderedPageIndex = null
            renderedPageScale = null
            return@LaunchedEffect
        }
        logPdfZoomPerf {
            "render_effect page=${pageIndex + 1} scale=${scale.formatLogFloat()} " +
                "existingPage=${renderedPageIndex?.let { it + 1 } ?: "none"} " +
                "existingScale=${renderedPageScale?.formatLogFloat() ?: "none"} " +
                "searchIndexing=$isSearchIndexing indexed=$indexedSearchPageCount/${document.pageCount} " +
                "cacheKeys=${paginatedRenderCache.keys.sorted().map { it + 1 }}"
        }
        if (renderedPageIndex != pageIndex) {
            paginatedRenderCache[pageIndex]?.let { cached ->
                logPdfZoomPerf {
                    "cache_hit page=${pageIndex + 1} scale=${cached.scale.formatLogFloat()} " +
                        "bitmap=${cached.render.width}x${cached.render.height}"
                }
                renderedPage = cached.render
                renderedPageIndex = pageIndex
                renderedPageScale = cached.scale
                renderError = null
                isRendering = false
            }
        }
        val hasPageRender = renderedPage != null && renderedPageIndex == pageIndex
        if (!hasPageRender) {
            logPdfZoomPerf { "cache_miss page=${pageIndex + 1}; showing spinner until first render" }
            renderedPage = null
            renderedPageIndex = null
            renderedPageScale = null
            isRendering = true
        }
        renderJob = launch {
            val pageSize = document.pageSizes.getOrNull(pageIndex)
            if (pageSize == null) {
                renderedPage = null
                renderedPageIndex = null
                renderedPageScale = null
                renderError = "Failed to render page."
                isRendering = false
                return@launch
            }
            val safeScale = zoomSpec.safeRenderScale(
                pageSize.width,
                pageSize.height, scale
            )
            val isOpeningRender = paginatedRenderCache.isEmpty() && !hasPageRender
            val firstRenderScale = desktopPdfPaginationFirstRenderScale(
                requestedScale = safeScale,
                hasPageRender = hasPageRender,
                isOpeningRender = isOpeningRender
            )
            logPdfZoomPerf {
                "render_plan page=${pageIndex + 1} requestedScale=${scale.formatLogFloat()} " +
                    "safeScale=${safeScale.formatLogFloat()} firstScale=${firstRenderScale.formatLogFloat()} " +
                    "hasRender=$hasPageRender opening=$isOpeningRender"
            }

            suspend fun renderAt(renderScale: Float, delayMillis: Long, showSpinner: Boolean): Boolean {
                logPdfZoomPerf {
                    "render_scheduled page=${pageIndex + 1} renderScale=${renderScale.formatLogFloat()} " +
                        "requestedScale=${scale.formatLogFloat()} delayMs=$delayMillis showSpinner=$showSpinner " +
                        "hasPageRender=$hasPageRender"
                }
                delay(delayMillis)
                if (showSpinner) {
                    isRendering = true
                }
                renderError = null
                val startedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        DesktopPdfium.renderPage(document, pageIndex, renderScale)
                    }
                }
                val elapsedMs = System.currentTimeMillis() - startedAt
                if (currentPdfPageIndex != pageIndex || currentPdfScale != scale ||
                    currentPdfDisplayMode != PdfDisplayMode.PAGINATION
                ) {
                    logPdfZoomPerf {
                        "render_stale page=${pageIndex + 1} renderScale=${renderScale.formatLogFloat()} " +
                            "elapsedMs=$elapsedMs currentPage=${currentPdfPageIndex + 1} " +
                            "currentScale=${currentPdfScale.formatLogFloat()} mode=$currentPdfDisplayMode"
                    }
                    return false
                }
                result.getOrNull()?.let { render ->
                    cachePaginatedRender(pageIndex, renderScale, render)
                    renderedPage = render
                    renderedPageIndex = pageIndex
                    renderedPageScale = renderScale
                }
                renderError = result.exceptionOrNull()?.message
                    ?: if (renderedPage == null || renderedPageIndex != pageIndex) "Failed to render page." else null
                logPdfZoomPerf {
                    "render_end page=${pageIndex + 1} renderScale=${renderScale.formatLogFloat()} " +
                        "requestedScale=${scale.formatLogFloat()} elapsedMs=$elapsedMs success=${result.isSuccess} " +
                        "error=${result.exceptionOrNull()?.message?.logPreview() ?: "none"}"
                }
                renderedPage?.let { render ->
                    logPdfSelection(
                        "render page=${pageIndex + 1} " +
                            "requestedScale=${scale.formatLogFloat()} renderScale=${renderScale.formatLogFloat()} " +
                            "safeScale=${safeScale.formatLogFloat()} " +
                            "pageSize=${pageSize.width.formatLogFloat()}x${pageSize.height.formatLogFloat()} " +
                            "bitmap=${render.width}x${render.height} capped=${safeScale < zoomSpec.clamp(
                                scale
                            )}"
                    )
                }
                isRendering = false
                return result.isSuccess && renderedPageIndex == pageIndex
            }

            suspend fun prefetchPage(pageToPrefetch: Int) {
                if (pageToPrefetch !in 0 until document.pageCount) return
                val cached = paginatedRenderCache[pageToPrefetch]
                if (
                    cached != null &&
                    cached.scale >= DesktopPdfPaginationFastFirstRenderMaxScale - DesktopPdfRenderScaleTolerance
                ) {
                    logPdfZoomPerf {
                        "prefetch_skip_cached page=${pageToPrefetch + 1} scale=${cached.scale.formatLogFloat()}"
                    }
                    return
                }
                val prefetchPageSize = document.pageSizes.getOrNull(pageToPrefetch) ?: return
                val prefetchScale = zoomSpec.safeRenderScale(
                    prefetchPageSize.width,
                    prefetchPageSize.height,
                    DesktopPdfPaginationFastFirstRenderMaxScale
                )
                logPdfZoomPerf {
                    "prefetch_start page=${pageToPrefetch + 1} scale=${prefetchScale.formatLogFloat()} " +
                        "current=${pageIndex + 1}"
                }
                val startedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        DesktopPdfium.renderPage(document, pageToPrefetch, prefetchScale)
                    }
                }
                val elapsedMs = System.currentTimeMillis() - startedAt
                if (currentPdfPageIndex != pageIndex || currentPdfScale != scale ||
                    currentPdfDisplayMode != PdfDisplayMode.PAGINATION ||
                    pdfZoomPreview != null
                ) {
                    logPdfZoomPerf {
                        "prefetch_stale page=${pageToPrefetch + 1} elapsedMs=$elapsedMs " +
                            "currentPage=${currentPdfPageIndex + 1} currentScale=${currentPdfScale.formatLogFloat()} " +
                            "mode=$currentPdfDisplayMode preview=${pdfZoomPreview != null}"
                    }
                    return
                }
                result.getOrNull()?.let { render ->
                    cachePaginatedRender(pageToPrefetch, prefetchScale, render)
                }
                logPdfZoomPerf {
                    "prefetch_end page=${pageToPrefetch + 1} scale=${prefetchScale.formatLogFloat()} " +
                        "elapsedMs=$elapsedMs success=${result.isSuccess} " +
                        "error=${result.exceptionOrNull()?.message?.logPreview() ?: "none"}"
                }
            }

            val existingScale = renderedPageScale
            val needsFirstRender = !hasPageRender ||
                existingScale == null ||
                abs(existingScale - firstRenderScale) > DesktopPdfRenderScaleTolerance
            if (needsFirstRender) {
                renderAt(
                    renderScale = firstRenderScale,
                    delayMillis = if (hasPageRender) DesktopPdfZoomRenderDebounceMillis else 45L,
                    showSpinner = !hasPageRender
                )
            }
            delay(DesktopPdfPaginationPrefetchDelayMillis)
            if (currentPdfPageIndex == pageIndex && currentPdfScale == scale &&
                currentPdfDisplayMode == PdfDisplayMode.PAGINATION &&
                pdfZoomPreview == null
            ) {
                prefetchPage(pageIndex + 1)
                prefetchPage(pageIndex - 1)
            }
        }
    }

    val pdfWorkspaceModel = pdfReaderWorkspaceModel(
        state = pdfState,
        displayMode = displayMode,
        hasContents = document.toc.isNotEmpty(),
        hasBookmarks = bookmarks.isNotEmpty(),
        hasAnnotations = sortedAnnotations.isNotEmpty(),
        hasEmbeddedComments = sortedEmbeddedAnnotations.isNotEmpty(),
        searchActive = isPdfSearchActive || searchQuery.isNotBlank(),
        annotationEditing = activeTextDraft != null ||
            selectedAnnotation != null ||
            selectedTool != PdfInkTool.NONE ||
            isTextSelectionMode,
        richTextEditing = isRichTextMode,
        loading = isRendering || isSearchIndexing,
        errorMessage = renderError,
        extrasState = pdfExtrasState,
        aiAvailable = featurePolicy.aiAndCloud && aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
        cloudTtsAvailable = featurePolicy.aiAndCloud && aiByokSettings.sanitized().isCloudTtsAvailable,
        externalLookupAvailable = featurePolicy.externalLookup
    )

    fun handlePdfReaderKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (isFullscreen && event.key == Key.Escape) {
            setPdfFullscreen(false)
            return true
        }
        val isEditingTextAnnotation =
            activeTextDraft != null ||
                (selectedTool == PdfInkTool.TEXT && selectedAnnotation?.kind == PdfAnnotationKind.TEXT)
        if ((isEditingTextAnnotation || isRichTextMode) && !event.isCtrlPressed) {
            return false
        }
        fun scrollVertically(delta: Float): Boolean {
            pdfScope.launch {
                if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    verticalListState.scrollBy(delta)
                } else {
                    pageVerticalScrollState.scrollBy(delta)
                }
            }
            return true
        }
        return when {
            event.key == Key.DirectionLeft -> {
                goToPage(pageIndex - 1)
                true
            }
            event.key == Key.DirectionRight -> {
                goToPage(pageIndex + 1)
                true
            }
            event.key == Key.DirectionUp -> scrollVertically(-96f)
            event.key == Key.DirectionDown -> scrollVertically(96f)
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
            event.isCtrlPressed && event.key == Key.F -> {
                dispatchPdf(SharedPdfReaderAction.SearchOpened)
                true
            }
            event.isCtrlPressed && event.key == Key.Equals -> {
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f))
                true
            }
            event.isCtrlPressed && event.key == Key.Minus -> {
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f))
                true
            }
            else -> false
        }
    }

    fun handlePdfReaderAwtKeyEvent(event: AwtKeyEvent): Boolean {
        if (event.id != AwtKeyEvent.KEY_PRESSED) return false
        if (isFullscreen && event.keyCode == AwtKeyEvent.VK_ESCAPE) {
            setPdfFullscreen(false)
            return true
        }
        val isEditingTextAnnotation =
            activeTextDraft != null ||
                (selectedTool == PdfInkTool.TEXT && selectedAnnotation?.kind == PdfAnnotationKind.TEXT)
        if ((isEditingTextAnnotation || isRichTextMode) && !event.isControlDown) {
            return false
        }
        fun scrollVertically(delta: Float): Boolean {
            pdfScope.launch {
                if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    verticalListState.scrollBy(delta)
                } else {
                    pageVerticalScrollState.scrollBy(delta)
                }
            }
            return true
        }
        return when (event.keyCode) {
            AwtKeyEvent.VK_LEFT -> {
                goToPage(pageIndex - 1)
                true
            }
            AwtKeyEvent.VK_RIGHT -> {
                goToPage(pageIndex + 1)
                true
            }
            AwtKeyEvent.VK_UP -> scrollVertically(-96f)
            AwtKeyEvent.VK_DOWN -> scrollVertically(96f)
            AwtKeyEvent.VK_PAGE_UP -> {
                goToPage(pageIndex - 1)
                true
            }
            AwtKeyEvent.VK_PAGE_DOWN -> {
                goToPage(pageIndex + 1)
                true
            }
            AwtKeyEvent.VK_HOME -> {
                goToPage(0)
                true
            }
            AwtKeyEvent.VK_END -> {
                goToPage(document.pageCount - 1)
                true
            }
            AwtKeyEvent.VK_F -> {
                if (!event.isControlDown) return false
                dispatchPdf(SharedPdfReaderAction.SearchOpened)
                true
            }
            AwtKeyEvent.VK_EQUALS,
            AwtKeyEvent.VK_PLUS,
            AwtKeyEvent.VK_ADD -> {
                if (!event.isControlDown) return false
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f))
                true
            }
            AwtKeyEvent.VK_MINUS,
            AwtKeyEvent.VK_SUBTRACT -> {
                if (!event.isControlDown) return false
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f))
                true
            }
            else -> false
        }
    }

    DesktopReaderFullscreenKeyEffect(
        enabled = isFullscreen && !pdfPopupActive,
        onKeyPressed = { event -> handlePdfReaderAwtKeyEvent(event) }
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PdfNavigationSidebar() {
        val tabs = listOf("TOC", "Annotations", "Bookmarks", "Pages")
        var selectedTabIndex by remember(documentHandleId) { mutableStateOf(0) }
        val navigationScope = rememberCoroutineScope()
        val pdfTocParentIndices = remember(document.toc) { desktopPdfTocParentIndices(document.toc) }
        var expandedPdfTocEntryIndices by remember(documentHandleId, document.toc) {
            mutableStateOf(pdfTocParentIndices)
        }

        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> {
                        if (document.toc.isEmpty()) {
                            DesktopPdfNavigationEmpty("No table of contents")
                        } else {
                            val tocListState = rememberLazyListState()
                            val visibleTocItems by remember(document.toc) {
                                derivedStateOf { desktopVisiblePdfTocEntries(document.toc, expandedPdfTocEntryIndices) }
                            }
                            val currentOriginalIndex = remember(document.toc, pageIndex) {
                                document.toc.indexOfLast { it.pageIndex <= pageIndex }
                                    .takeIf { it >= 0 }
                                    ?: document.toc.indexOfFirst { it.pageIndex == pageIndex }.takeIf { it >= 0 }
                            }
                            fun locateCurrentTocEntry() {
                                val originalIndex = currentOriginalIndex ?: return
                                navigationScope.launch {
                                    expandedPdfTocEntryIndices = expandedPdfTocEntryIndices +
                                        desktopPdfTocAncestorIndices(document.toc, originalIndex)
                                    repeat(4) {
                                        val visibleIndex = visibleTocItems.indexOfFirst { it.first == originalIndex }
                                        if (visibleIndex >= 0) {
                                            tocListState.animateScrollToItem(visibleIndex)
                                            return@launch
                                        }
                                        delay(30)
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(onClick = { expandedPdfTocEntryIndices = pdfTocParentIndices }) {
                                        Text("Expand all")
                                    }
                                    TextButton(onClick = { expandedPdfTocEntryIndices = emptySet() }) {
                                        Text("Collapse all")
                                    }
                                    TextButton(onClick = ::locateCurrentTocEntry, enabled = currentOriginalIndex != null) {
                                        Text("Locate")
                                    }
                                }
                                HorizontalDivider()
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    LazyColumn(
                                        state = tocListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .sharedAcceleratedLazyWheelScroll(tocListState)
                                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            visibleTocItems,
                                            key = { (index, entry) -> "nav_toc_${index}_${entry.pageIndex}_${entry.nestLevel}" }
                                        ) { (originalIndex, entry) ->
                                            val nextItem = document.toc.getOrNull(originalIndex + 1)
                                            val hasChildren = nextItem != null && nextItem.nestLevel > entry.nestLevel
                                            val isExpanded = originalIndex in expandedPdfTocEntryIndices
                                            DesktopPdfTocTreeItem(
                                                entry = entry,
                                                selected = originalIndex == currentOriginalIndex,
                                                hasChildren = hasChildren,
                                                isExpanded = isExpanded,
                                                onToggleExpand = {
                                                    expandedPdfTocEntryIndices = if (isExpanded) {
                                                        expandedPdfTocEntryIndices - originalIndex
                                                    } else {
                                                        expandedPdfTocEntryIndices + originalIndex
                                                    }
                                                },
                                                onClick = { goToPage(entry.pageIndex, recordJump = true) }
                                            )
                                        }
                                    }
                                    SharedReaderVerticalScrollbar(
                                        listState = tocListState,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        if (sortedAnnotations.isEmpty() && sortedEmbeddedAnnotations.isEmpty()) {
                            DesktopPdfNavigationEmpty("No annotations yet")
                        } else {
                            val annotationsListState = rememberLazyListState()
                            var annotationMenuExpandedFor by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
                            var embeddedAnnotationMenuExpandedFor by remember { mutableStateOf<SharedPdfEmbeddedAnnotation?>(null) }
                            var deleteAnnotationConfirmFor by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = annotationsListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedAcceleratedLazyWheelScroll(annotationsListState)
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sortedAnnotations, key = { "nav_annotation_${it.id}" }) { annotation ->
                                        Surface(
                                            color = if (annotation.id == selectedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { goToAnnotation(annotation) }
                                                        .padding(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text(annotation.desktopLabel(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("Page ${annotation.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                    annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                                        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Box {
                                                    IconButton(onClick = { annotationMenuExpandedFor = annotation }) {
                                                        Icon(Icons.Default.MoreVert, contentDescription = "Annotation options")
                                                    }
                                                    DropdownMenu(
                                                        expanded = annotationMenuExpandedFor == annotation,
                                                        onDismissRequest = { annotationMenuExpandedFor = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(if (annotation.note.isNullOrBlank() && annotation.kind != PdfAnnotationKind.TEXT) "Add note" else "Edit") },
                                                            onClick = {
                                                                annotationMenuExpandedFor = null
                                                                selectAnnotation(annotation)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Delete") },
                                                            onClick = {
                                                                annotationMenuExpandedFor = null
                                                                deleteAnnotationConfirmFor = annotation
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    items(sortedEmbeddedAnnotations, key = { "nav_embedded_${it.id}" }) { annotation ->
                                        Surface(
                                            color = if (annotation.id == selectedEmbeddedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { goToEmbeddedAnnotation(annotation) }
                                                        .padding(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text(annotation.author.ifBlank { "PDF comment" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("Page ${annotation.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                    annotation.contents.takeIf { it.isNotBlank() }?.let { contents ->
                                                        Text(contents, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Box {
                                                    IconButton(onClick = { embeddedAnnotationMenuExpandedFor = annotation }) {
                                                        Icon(Icons.Default.MoreVert, contentDescription = "Comment options")
                                                    }
                                                    DropdownMenu(
                                                        expanded = embeddedAnnotationMenuExpandedFor == annotation,
                                                        onDismissRequest = { embeddedAnnotationMenuExpandedFor = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Open comment") },
                                                            onClick = {
                                                                embeddedAnnotationMenuExpandedFor = null
                                                                selectEmbeddedAnnotation(annotation)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                SharedReaderVerticalScrollbar(
                                    listState = annotationsListState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                            deleteAnnotationConfirmFor?.let { annotation ->
                                AlertDialog(
                                    onDismissRequest = { deleteAnnotationConfirmFor = null },
                                    title = { Text("Delete annotation?") },
                                    text = { Text("This removes the annotation from this PDF.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                deleteAnnotationConfirmFor = null
                                                deleteAnnotation(annotation.id)
                                            }
                                        ) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { deleteAnnotationConfirmFor = null }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    2 -> {
                        if (bookmarks.isEmpty()) {
                            DesktopPdfNavigationEmpty("No bookmarks yet")
                        } else {
                            val bookmarksListState = rememberLazyListState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = bookmarksListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedAcceleratedLazyWheelScroll(bookmarksListState)
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(bookmarks, key = { "nav_bookmark_${it.pageIndex}" }) { bookmark ->
                                        Surface(
                                            color = if (bookmark.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().clickable { goToPage(bookmark.pageIndex, recordJump = true) }
                                        ) {
                                            Text(
                                                bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" },
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                                SharedReaderVerticalScrollbar(
                                    listState = bookmarksListState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                    3 -> {
                        val pageRows = remember(document.pageCount) { (0 until document.pageCount).chunked(3) }
                        val pagesListState = rememberLazyListState()
                        val currentRowIndex = pageIndex / 3
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(
                                    onClick = {
                                        navigationScope.launch {
                                            pagesListState.animateScrollToItem(currentRowIndex.coerceIn(0, pageRows.lastIndex.coerceAtLeast(0)))
                                        }
                                    }
                                ) {
                                    Text("Locate")
                                }
                            }
                            HorizontalDivider()
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(
                                    state = pagesListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedAcceleratedLazyWheelScroll(pagesListState)
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(pageRows, key = { row -> row.firstOrNull() ?: 0 }) { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEach { page ->
                                                DesktopPdfThumbnailTile(
                                                    document = document,
                                                    pageIndex = page,
                                                    selected = page == pageIndex,
                                                    onClick = { goToPage(page, recordJump = true) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(3 - row.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                                SharedReaderVerticalScrollbar(
                                    listState = pagesListState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PdfBottomChrome() {
        val chromeBackground = MaterialTheme.colorScheme.surface
        val chromeContent = MaterialTheme.colorScheme.onSurface
        val sliderActive = MaterialTheme.colorScheme.primary
        val sliderInactive = MaterialTheme.colorScheme.surfaceVariant
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = chromeBackground,
            contentColor = chromeContent,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DesktopPdfJumpHistoryControls(
                    visible = !isPdfSearchActive,
                    backPage = jumpHistory.backPage,
                    forwardPage = jumpHistory.forwardPage,
                    onBack = ::goBackInJumpHistory,
                    onForward = ::goForwardInJumpHistory,
                    onClear = { jumpHistory = jumpHistory.clear() }
                )
                if (!isPdfSearchActive && jumpHistory.hasJumpTargets) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { goToPage(pageIndex - 1) }, enabled = canGoPrevious) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous page",
                            tint = chromeContent.copy(alpha = if (canGoPrevious) 0.78f else 0.32f)
                        )
                    }
                    Text(
                        "Page ${pageIndex + 1} of ${document.pageCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = chromeContent.copy(alpha = 0.72f)
                    )
                    if (document.pageCount > 1) {
                        ReaderMinimalSlider(
                            value = pageIndex.toFloat(),
                            onValueChange = ::updatePdfPageScrub,
                            onValueChangeFinished = ::finishPdfPageScrub,
                            valueRange = 0f..(document.pageCount - 1).toFloat(),
                            activeColor = sliderActive,
                            inactiveColor = sliderInactive,
                            thumbColor = sliderActive,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        "${progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = chromeContent.copy(alpha = 0.72f)
                    )
                    IconButton(onClick = { goToPage(pageIndex + 1) }, enabled = canGoNext) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next page",
                            tint = chromeContent.copy(alpha = if (canGoNext) 0.78f else 0.32f)
                        )
                    }
                }
            }
        }
    }

    ReaderWorkspaceShell(
        model = pdfWorkspaceModel,
        title = document.title,
        subtitle = "${document.formatLabel} - Page ${pageIndex + 1} of ${document.pageCount}",
        progressLabel = "${progressPercent.toInt()}%",
        onReturnToLibrary = onReturnToLibrary?.let { returnToLibrary ->
            {
                persistPdfViewport()
                returnToLibrary()
            }
        },
        isFullscreen = isFullscreen,
        onFullscreenChange = ::setPdfFullscreen,
        isBookmarked = bookmarks.any { it.pageIndex == pageIndex },
        onToggleBookmark = { toggleBookmark(pageIndex) },
        onSearchAction = { dispatchPdf(SharedPdfReaderAction.SearchOpened) },
        topSearchBar = if (isPdfSearchActive) {
            {
                DesktopPdfSearchTopBar(
                    query = searchQuery,
                    showResultsPanel = showPdfSearchResultsPanel,
                    onQueryChange = { dispatchPdf(SharedPdfReaderAction.SearchChanged(it)) },
                    onClose = { dispatchPdf(SharedPdfReaderAction.SearchClosed) },
                    onToggleResults = { dispatchPdf(SharedPdfReaderAction.SearchResultsPanelToggled) }
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .focusRequester(pdfReaderFocusRequester)
            .onPreviewKeyEvent(::handlePdfReaderKeyEvent)
            .focusable(),
        leftSidebar = { _ -> PdfNavigationSidebar() },
        rightInspector = {
            var selectedPdfInspectorTab by remember(documentHandleId) { mutableStateOf(DesktopPdfInspectorTab.VIEW) }
            val viewInspectorListState = rememberLazyListState()
            val markupInspectorListState = rememberLazyListState()
            val assistInspectorListState = rememberLazyListState()
            val pdfInspectorListState = when (selectedPdfInspectorTab) {
                DesktopPdfInspectorTab.VIEW -> viewInspectorListState
                DesktopPdfInspectorTab.MARKUP -> markupInspectorListState
                DesktopPdfInspectorTab.ASSIST -> assistInspectorListState
            }
            Surface(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("PDF tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ScrollableTabRow(
                            selectedTabIndex = selectedPdfInspectorTab.ordinal,
                            edgePadding = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DesktopPdfInspectorTab.values().forEach { tab ->
                                Tab(
                                    selected = selectedPdfInspectorTab == tab,
                                    onClick = { selectedPdfInspectorTab = tab },
                                    text = {
                                        Text(
                                            tab.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = pdfInspectorListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedAcceleratedLazyWheelScroll(pdfInspectorListState, multiplier = 2.8f)
                                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (selectedPdfInspectorTab) {
                                DesktopPdfInspectorTab.VIEW -> {
                                    item {
                                        DesktopPdfInspectorSection("Reading") {
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
                                    }
                                    item {
                                        DesktopPdfInspectorSection("Position") {
                                            Text("Page ${pageIndex + 1} of ${document.pageCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (document.pageCount > 1) {
                                                ReaderMinimalSlider(
                                                    value = pageIndex.toFloat(),
                                                    onValueChange = ::updatePdfPageScrub,
                                                    onValueChangeFinished = ::finishPdfPageScrub,
                                                    valueRange = 0f..(document.pageCount - 1).toFloat()
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        DesktopPdfInspectorSection("Appearance") {
                                            SharedReaderThemeControls(
                                                settings = pdfReaderSettings,
                                                builtInThemes = BuiltInPdfReaderThemes,
                                                customTextureIds = customTextureIds,
                                                onImportTexture = onImportTexture,
                                                onSettingsChange = ::updatePdfReaderSettings
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text("Visual options", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            DesktopPdfVisualOptionSwitch(
                                                title = "Remove gap between pages",
                                                description = "Applies to vertical reading mode.",
                                                checked = !pdfReaderSettings.pdfVerticalPageGapVisible,
                                                onCheckedChange = { removeGap ->
                                                    updatePdfReaderSettings(
                                                        pdfReaderSettings.copy(pdfVerticalPageGapVisible = !removeGap)
                                                    )
                                                }
                                            )
                                            DesktopPdfVisualOptionSwitch(
                                                title = "Hide page number overlay",
                                                description = "Removes the small page count label from each page.",
                                                checked = !pdfReaderSettings.pdfPageNumberOverlayVisible,
                                                onCheckedChange = { hideOverlay ->
                                                    updatePdfReaderSettings(
                                                        pdfReaderSettings.copy(pdfPageNumberOverlayVisible = !hideOverlay)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        DesktopPdfInspectorSection("Zoom") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = {
                                                    cancelPendingPdfZoomPreview()
                                                    dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f))
                                                }) {
                                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                                                }
                                                Text("${(zoomControlScale * 100).toInt()}%", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                                IconButton(onClick = {
                                                    cancelPendingPdfZoomPreview()
                                                    dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f))
                                                }) {
                                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                                                }
                                            }
                                            Slider(
                                                value = zoomControlScale,
                                                onValueChange = {
                                                    cancelPendingPdfZoomPreview()
                                                    dispatchPdf(SharedPdfReaderAction.ZoomChanged(it))
                                                },
                                                valueRange = zoomSpec.min..zoomSpec.max
                                            )
                                        }
                                    }
                                }
                                DesktopPdfInspectorTab.MARKUP -> {
                                    item {
                                        DesktopPdfInspectorSection("Interaction") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                FilterChip(
                                                    selected = !isTextSelectionMode && selectedTool == PdfInkTool.NONE && !isRichTextMode,
                                                    onClick = ::selectPdfPanMode,
                                                    label = { Text("Pan") }
                                                )
                                                FilterChip(
                                                    selected = isTextSelectionMode,
                                                    onClick = {
                                                        val enabled = !isTextSelectionMode
                                                        if (enabled) {
                                                            deactivateRichTextMode()
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
                                            }
                                        }
                                    }
                                    item {
                                        DesktopPdfInspectorSection("Annotation tools") {
                                            SharedPdfAnnotationToolDock(
                                                selectedTool = selectedTool,
                                                selectedColor = selectedColor,
                                                strokeWidth = strokeWidth,
                                                tools = DesktopPdfAnnotationTools,
                                                highlighterPalette = pdfHighlighterColors,
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
                                    }
                                    item {
                                        DesktopPdfInspectorSection("Highlighter palette") {
                                            SharedPdfHighlighterPaletteEditor(
                                                palette = pdfHighlighterPalette,
                                                onPaletteChange = ::updatePdfHighlighterPalette
                                            )
                                        }
                                    }
                                    if (isRichTextMode || selectedTool == PdfInkTool.TEXT) {
                                        item {
                                            DesktopPdfInspectorSection("Text style") {
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
                                    }
                                }
                                DesktopPdfInspectorTab.ASSIST -> {
                                    item {
                                        DesktopPdfExtrasPanel(
                                            pageText = currentPdfPageText(),
                                            recapText = pdfTextBeforeCurrentPage(),
                                            extrasState = pdfExtrasState,
                                            aiByokSettings = aiByokSettings,
                                            externalLookupAvailable = featurePolicy.externalLookup,
                                            cloudTtsFeatureAvailable = featurePolicy.aiAndCloud,
                                            onExternalLookup = ::openPdfExternalLookup,
                                            onAiAction = ::runPdfAiAction,
                                            onCloudTtsStart = ::startPdfCloudTts,
                                            onCloudTtsPauseResume = ::pauseResumePdfCloudTts,
                                            onCloudTtsStop = ::stopPdfCloudTts,
                                            onCloudTtsClearCache = ::clearPdfCloudTtsCache,
                                            onAutoScrollChange = ::updatePdfAutoScroll,
                                            ttsReplacementPreferences = ttsReplacementPreferences,
                                            ttsReplacementBookId = document.path,
                                            onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                                        )
                                    }
                                }
                            }
                        }
                        SharedReaderVerticalScrollbar(
                            listState = pdfInspectorListState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        },
        bottomBar = { PdfBottomChrome() },
        fullscreenBottomBar = {
            DesktopPdfFullscreenBottomChrome(
                pageIndex = pageIndex,
                pageCount = document.pageCount,
                showJumpHistory = !isPdfSearchActive,
                jumpBackPage = jumpHistory.backPage,
                jumpForwardPage = jumpHistory.forwardPage,
                onPrevious = { goToPage(pageIndex - 1) },
                onNext = { goToPage(pageIndex + 1) },
                onPageScrub = ::updatePdfPageScrub,
                onPageScrubFinished = ::finishPdfPageScrub,
                onJumpBack = ::goBackInJumpHistory,
                onJumpForward = ::goForwardInJumpHistory,
                onClearJumpHistory = { jumpHistory = jumpHistory.clear() }
            )
        }
    ) {
        SharedPdfRichTextHiddenInput(
            controller = richTextController,
            enabled = isRichTextMode,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
                .zIndex(10f)
        )
        DesktopPdfSearchOverlay(
            isSearchActive = isPdfSearchActive,
            showResultsPanel = showPdfSearchResultsPanel,
            query = searchQuery,
            results = searchResults,
            activeSearchIndex = activeSearchIndex,
            highlightMode = searchHighlightMode,
            isIndexing = isSearchIndexing,
            indexedPageCount = indexedSearchPageCount,
            pageCount = document.pageCount,
            onResultClick = { index ->
                goToSearchResult(index)
                dispatchPdf(SharedPdfReaderAction.SearchResultsPanelToggled)
            },
            onShowResults = { dispatchPdf(SharedPdfReaderAction.SearchResultsPanelToggled) },
            onPrevious = { goToSearchResult(activeSearchIndex - 1) },
            onNext = { goToSearchResult(activeSearchIndex + 1) },
            onToggleHighlightMode = { dispatchPdf(SharedPdfReaderAction.SearchHighlightModeToggled) }
        )
        if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
            val verticalPageGap = pdfVerticalPageGapDp(
                isPageGapVisible = pdfReaderSettings.pdfVerticalPageGapVisible,
                defaultGap = DesktopDefaultPdfVerticalPageGap
            )
            val verticalViewportBackground = desktopPdfVerticalViewportBackgroundColor(
                pageBackgroundColor = pdfThemeStyle.pageBackgroundColor,
                gapBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                isPageGapVisible = pdfReaderSettings.pdfVerticalPageGapVisible
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(verticalViewportBackground, RoundedCornerShape(8.dp))
                    .onGloballyPositioned { coordinates ->
                        pdfZoomViewportRootOffset = coordinates.positionInRoot()
                    }
                    .desktopPdfZoomGestures(
                        currentZoom = scale,
                        zoomSpec = zoomSpec,
                        onZoomChanged = ::previewAnchoredPdfZoom
                    )
            ) {
                LazyColumn(
                        state = verticalListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(pageHorizontalScrollState)
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(verticalPageGap),
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
                                activeTtsChunk = activePdfTtsChunk,
                                searchQuery = searchQuery,
                                isTextSelectionMode = isTextSelectionMode,
                                selectedAnnotationId = selectedAnnotationId,
                                selectedEmbeddedAnnotationId = selectedEmbeddedAnnotationId,
                                selectedTool = selectedTool,
                                selectedColor = selectedColor,
                                highlighterPalette = pdfHighlighterColors,
                                strokeWidth = strokeWidth,
                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                activeTextDraft = activeTextDraft,
                                richTextController = richTextController,
                                isRichTextMode = isRichTextMode,
                                readerAiFeaturesAvailable = aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
                                cloudTtsAvailable = aiByokSettings.sanitized().isCloudTtsAvailable,
                                externalLookupAvailable = featurePolicy.externalLookup,
                                themeStyle = pdfThemeStyle,
                                shouldRender = verticalPageIndex in verticalRenderWindow,
                                zoomPreview = pdfZoomPreview?.takeIf {
                                    it.displayMode == PdfDisplayMode.VERTICAL_SCROLL
                                },
                                zoomViewportRootOffset = pdfZoomViewportRootOffset,
                                showPageNumberOverlay = pdfReaderSettings.pdfPageNumberOverlayVisible,
                                onSelectPage = {
                                    goToPage(
                                        target = it,
                                        scrollVertical = false,
                                        saveRichTextBeforePageChange = !isRichTextMode
                                    )
                                },
                                onCopySelection = ::copySelection,
                                onHighlightSelection = ::highlightSelection,
                                onExternalSearchSelection = { openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, it.text) },
                                onHighlighterPaletteChange = ::updatePdfHighlighterPalette,
                                onDefineSelection = { runPdfAiAction(ReaderAiFeature.DEFINE, it.text) },
                                onSpeakSelection = { togglePdfCloudTts(it.text) },
                                onEmbeddedAnnotationSelected = ::selectEmbeddedAnnotation,
                                onAnnotationSelected = ::selectAnnotation,
                                onLinkActivated = ::activatePdfLink,
                                onAnnotationAdded = { dispatchPdf(SharedPdfReaderAction.AnnotationAdded(it)) },
                                onAnnotationUpdated = ::updateAnnotation,
                                onAnnotationsChanged = { dispatchPdf(SharedPdfReaderAction.AnnotationsChanged(it)) },
                                onTextAnnotationSelected = ::selectTextAnnotation,
                                onTextDraftStarted = ::startActiveTextDraft,
                                onTextDraftChanged = ::updateActiveTextDraft,
                                onTextDraftBoundsChanged = ::updateActiveTextDraftBounds,
                                onPan = { delta ->
                                    pdfScope.launch {
                                        pageHorizontalScrollState.scrollBy(-delta.x)
                                        verticalListState.scrollBy(-delta.y)
                                    }
                                },
                                onPagePositioned = { page, offset ->
                                    verticalPageRootOffsets[page] = offset
                                }
                            )
                        }
                    }
                    SharedPdfVerticalScrollbar(
                        listState = verticalListState,
                        pageCount = document.pageCount,
                        currentPage = pageIndex,
                        isDarkMode = verticalViewportBackground.luminance() < 0.5f,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                    DesktopPdfPageScrubOverlay(
                        pageIndex = pageScrubPreview,
                        pageCount = document.pageCount
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pdfThemeStyle.viewerBackgroundColor, RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coordinates ->
                            pdfZoomViewportRootOffset = coordinates.positionInRoot()
                        }
                        .desktopPdfZoomGestures(
                            currentZoom = scale,
                            zoomSpec = zoomSpec,
                            onZoomChanged = ::previewAnchoredPdfZoom
                        )
                        .horizontalScroll(pageHorizontalScrollState)
                        .verticalScroll(pageVerticalScrollState)
                        .padding(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val currentPageRender = renderedPage.takeIf { renderedPageIndex == pageIndex }
                    when {
                    currentPageRender != null -> {
                        val pageSize = document.pageSizes.getOrNull(pageIndex)
                        if (pageSize == null) {
                            Text("Failed to render page.", color = MaterialTheme.colorScheme.error)
                            return@Box
                        }
                        val pageDisplayScale = zoomSpec.clamp(scale)
                        val pageWidthDp = with(density) { (pageSize.width * pageDisplayScale).toDp() }
                        val pageHeightDp = with(density) { (pageSize.height * pageDisplayScale).toDp() }
                        val pageRenderScale = currentPageRender.width / pageSize.width
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
                        val ttsHighlightBounds: List<PdfPageBounds> = remember(
                            document.path,
                            activePdfTtsChunk,
                            pageIndex,
                            pageCanvasSize
                        ) {
                            val chunk = activePdfTtsChunk?.takeIf { it.pageIndex == pageIndex }
                            if (chunk == null || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0 || chunk.endOffset <= chunk.startOffset) {
                                emptyList()
                            } else {
                                DesktopPdfium.textRectsForRange(
                                    document = document,
                                    pageIndex = pageIndex,
                                    startIndex = chunk.startOffset,
                                    endIndex = chunk.endOffset - 1,
                                    viewportWidth = pageCanvasSize.width,
                                    viewportHeight = pageCanvasSize.height
                                ).map { it.toPdfPageBounds() }
                                    .filter { it.right > it.left && it.bottom > it.top }
                                    .mergePdfBoundsByLine()
                            }
                        }
                        val pageZoomPreview = pdfZoomPreview?.takeIf {
                            it.displayMode == PdfDisplayMode.PAGINATION &&
                                it.pageIndex == pageIndex
                        }
                        Box(
                            modifier = Modifier
                                .size(pageWidthDp, pageHeightDp)
                                .onGloballyPositioned { coordinates ->
                                    paginatedPageRootOffset = coordinates.positionInRoot()
                                }
                                .onSizeChanged { size ->
                                    if (pageCanvasSize != size) {
                                        logPdfSelection(
                                            "layout page=${pageIndex + 1} " +
                                                "canvas=${size.formatLogSize()} bitmap=${currentPageRender.width}x${currentPageRender.height} " +
                                                "requestedScale=${scale.formatLogFloat()} displayScale=${pageDisplayScale.formatLogFloat()} " +
                                                "renderScale=${pageRenderScale.formatLogFloat()}"
                                        )
                                    }
                                    pageCanvasSize = size
                                }
                                .desktopPdfZoomPreviewLayer(
                                    preview = pageZoomPreview,
                                    currentZoom = scale,
                                    viewportRootOffset = pdfZoomViewportRootOffset,
                                    pageRootOffset = paginatedPageRootOffset,
                                    pageCanvasSize = pageCanvasSize
                                )
                                .background(pdfThemeStyle.pageBackgroundColor, RoundedCornerShape(2.dp))
                                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, selectedTool, isRichTextMode) {
                                    if (isRichTextMode) return@pointerInput
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val point = event.changes.firstOrNull()?.position ?: continue
                                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                                val highlightHit = if (selectedTool != PdfInkTool.TEXT && selectedTool != PdfInkTool.ERASER) {
                                                    currentPdfAnnotations.asReversed().firstOrNull {
                                                        it.isDesktopTextSelectionHighlight &&
                                                            it.pageIndex == pageIndex &&
                                                            it.sharedPdfHitTest(point, pageCanvasSize)
                                                    }
                                                } else {
                                                    null
                                                }
                                                if (highlightHit != null) {
                                                    selectAnnotation(highlightHit)
                                                    clearPdfInteractionState()
                                                    event.changes.forEach { it.consume() }
                                                    continue
                                                }
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
                                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, isRichTextMode) {
                                    if (isRichTextMode || !isTextSelectionMode) return@pointerInput
                                    detectTapGestures(
                                        onLongPress = { point ->
                                            val selection = document.wordSelectionAt(pageIndex, point, pageCanvasSize)
                                            if (selection != null) {
                                                selectionStartIndex = null
                                                selectionEndIndex = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                                activeSelectionHandle = null
                                                textSelection = selection
                                                selectionMenuOffset = selection.menuAnchor(pageCanvasSize, point)
                                                logPdfSelection(
                                                    "long_press page=${pageIndex + 1} " +
                                                        "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                        "range=${selection.startIndex}..${selection.endIndex} " +
                                                        "chars=${selection.text.length} " +
                                                        "text=\"${selection.text.logPreview()}\""
                                                )
                                            }
                                        }
                                    )
                                }
                                .pointerInput(pageIndex, selectedTool, isTextSelectionMode, isRichTextMode) {
                                    if (isRichTextMode || isTextSelectionMode || selectedTool != PdfInkTool.NONE) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                        val pointerId = down.id
                                        var dragStarted = false
                                        var dragDistance = 0f
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                                ?: return@awaitEachGesture
                                            if (change.changedToUp()) {
                                                return@awaitEachGesture
                                            }
                                            if (!change.positionChanged()) continue
                                            val delta = change.positionChange()
                                            if (!dragStarted) {
                                                dragDistance += delta.getDistance()
                                                if (dragDistance <= viewConfiguration.touchSlop) {
                                                    continue
                                                }
                                                dragStarted = true
                                                change.consume()
                                                continue
                                            }
                                            pdfScope.launch {
                                                pageHorizontalScrollState.scrollBy(-delta.x)
                                                pageVerticalScrollState.scrollBy(-delta.y)
                                            }
                                            change.consume()
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
                                    pageCanvasSize, currentPageRender.width,
                                    currentPageRender.height
                                ) {
                                    if (isRichTextMode) return@pointerInput
                                    if (isTextSelectionMode) {
                                        var latestSelectionDragPoint: Offset? = null
                                        var lastSelectionPreviewAt = 0L
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                latestSelectionDragPoint = start
                                                lastSelectionPreviewAt = 0L
                                                selectionMenuOffset = null
                                                val existingSelection = textSelection
                                                val handle = existingSelection?.handleAt(start, pageCanvasSize)
                                                activeSelectionHandle = handle
                                                val hit = document.charHitAt(pageIndex, start, pageCanvasSize)
                                                if (handle != null && existingSelection != null) {
                                                    selectionStartHit = null
                                                    selectionStartIndex = when (handle) {
                                                        DesktopPdfSelectionHandle.START -> existingSelection.endIndex
                                                        DesktopPdfSelectionHandle.END -> existingSelection.startIndex
                                                    }
                                                    selectionEndHit = hit
                                                    selectionEndIndex = hit?.index ?: when (handle) {
                                                        DesktopPdfSelectionHandle.START -> existingSelection.startIndex
                                                        DesktopPdfSelectionHandle.END -> existingSelection.endIndex
                                                    }
                                                } else {
                                                    selectionStartHit = hit
                                                    selectionStartIndex = hit?.index
                                                    selectionEndHit = null
                                                    selectionEndIndex = null
                                                    textSelection = null
                                                }
                                                logPdfSelection(
                                                    "drag_start page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${currentPageRender.width}x${currentPageRender.height} " +
                                                        "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                                        "handle=${handle?.name ?: "none"} " +
                                                        hit.formatLogHit("start")
                                                )
                                            },
                                            onDrag = { change, _ ->
                                                latestSelectionDragPoint = change.position
                                                val now = System.currentTimeMillis()
                                                if (lastSelectionPreviewAt == 0L ||
                                                    now - lastSelectionPreviewAt >= DesktopPdfSelectionPreviewThrottleMillis
                                                ) {
                                                    lastSelectionPreviewAt = now
                                                    val startIndex = selectionStartIndex
                                                    val hit = document.charHitAt(pageIndex, change.position, pageCanvasSize)
                                                    selectionEndHit = hit
                                                    val endIndex = hit?.index
                                                    val previousEndIndex = selectionEndIndex
                                                    selectionEndIndex = endIndex
                                                    if (endIndex != previousEndIndex || textSelection == null) {
                                                        textSelection = if (startIndex != null && endIndex != null) {
                                                            document.selectionPreviewBetweenIndexes(
                                                                pageIndex = pageIndex,
                                                                startIndex = startIndex,
                                                                endIndex = endIndex,
                                                                canvasSize = pageCanvasSize
                                                            )
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                }
                                                change.consume()
                                            },
                                            onDragEnd = {
                                                val finalHit = latestSelectionDragPoint
                                                    ?.let { document.charHitAt(pageIndex, it, pageCanvasSize) }
                                                    ?: selectionEndHit
                                                if (finalHit != null) {
                                                    selectionEndHit = finalHit
                                                    selectionEndIndex = finalHit.index
                                                }
                                                val startIndex = selectionStartIndex
                                                val endIndex = selectionEndIndex
                                                val selection = if (startIndex != null && endIndex != null) {
                                                    document.selectionBetweenIndexes(
                                                        pageIndex = pageIndex,
                                                        startIndex = startIndex,
                                                        endIndex = endIndex,
                                                        canvasSize = pageCanvasSize,
                                                        useNativeBounds = true
                                                    )
                                                } else {
                                                    textSelection?.takeIf { it.text.isNotBlank() }
                                                }
                                                textSelection = selection
                                                selectionMenuOffset = selection?.menuAnchor(
                                                    pageCanvasSize,
                                                    finalHit?.point ?: selectionEndHit?.point ?: selectionStartHit?.point
                                                )
                                                logPdfSelection(
                                                    "drag_end page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${currentPageRender.width}x${currentPageRender.height} " +
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
                                                activeSelectionHandle = null
                                                latestSelectionDragPoint = null
                                                lastSelectionPreviewAt = 0L
                                            },
                                            onDragCancel = {
                                                logPdfSelection(
                                                    "drag_cancel page=${pageIndex + 1} " +
                                                        "canvas=${pageCanvasSize.formatLogSize()} bitmap=${currentPageRender.width}x${currentPageRender.height} " +
                                                        "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                                        selectionStartHit.formatLogHit("start") + " " +
                                                        selectionEndHit.formatLogHit("end")
                                                )
                                                selectionStartIndex = null
                                                selectionEndIndex = null
                                                selectionStartHit = null
                                                selectionEndHit = null
                                                activeSelectionHandle = null
                                                latestSelectionDragPoint = null
                                                lastSelectionPreviewAt = 0L
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
                                    } else if (selectedTool != PdfInkTool.NONE) {
                                        var eraserPreviousPoint: Offset? = null
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                            val start = down.position
                                            if (selectedTool == PdfInkTool.ERASER) {
                                                eraserPosition = start
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

                                            val pointerId = down.id
                                            var dragStarted = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size > 1) {
                                                    eraserPreviousPoint = null
                                                    eraserPosition = null
                                                    activeStroke = emptyList()
                                                    return@awaitEachGesture
                                                }
                                                val change = event.changes.firstOrNull { it.id == pointerId }
                                                    ?: run {
                                                        eraserPreviousPoint = null
                                                        eraserPosition = null
                                                        activeStroke = emptyList()
                                                        return@awaitEachGesture
                                                    }
                                                if (change.changedToUp()) {
                                                    change.consume()
                                                    if (selectedTool != PdfInkTool.ERASER && activeStroke.isNotEmpty()) {
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
                                                    eraserPreviousPoint = null
                                                    eraserPosition = null
                                                    activeStroke = emptyList()
                                                    return@awaitEachGesture
                                                }
                                                if (!change.positionChanged()) continue
                                                val distance = (change.position - start).getDistance()
                                                if (selectedTool != PdfInkTool.ERASER && !dragStarted && distance <= viewConfiguration.touchSlop) continue
                                                dragStarted = true
                                                if (selectedTool == PdfInkTool.ERASER) {
                                                    val point = change.position
                                                    eraserPosition = point
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
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                        ) {
                            DesktopPdfThemedPageImage(
                                bitmap = currentPageRender.image,
                                contentDescription = "PDF page ${pageIndex + 1}",
                                themeStyle = pdfThemeStyle,
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
                            PdfSearchHighlightOverlay(
                                bounds = ttsHighlightBounds,
                                canvasSize = pageCanvasSize,
                                color = Color(0x887DD3FC)
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
                                selectedAnnotationId = selectedAnnotationId,
                                eraserPosition = eraserPosition,
                                showEraserIndicator = selectedTool == PdfInkTool.ERASER,
                                eraserStrokeWidth = strokeWidth
                            )
                            PdfTextSelectionHandles(
                                selection = textSelection,
                                canvasSize = pageCanvasSize,
                                activeHandle = activeSelectionHandle
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
                            if (pdfReaderSettings.pdfPageNumberOverlayVisible) {
                                SharedPdfPageNumberOverlay(
                                    pageIndex = pageIndex,
                                    pageCount = document.pageCount
                                )
                            }
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
                                highlighterPalette = pdfHighlighterColors,
                                onHighlighterPaletteChange = ::updatePdfHighlighterPalette,
                                onCopy = {
                                    textSelection?.let(::copySelection)
                                    clearSelection()
                                },
                                onHighlight = { colorArgb ->
                                    textSelection?.let { selection ->
                                        highlightSelection(pageIndex, selection, pageCanvasSize, colorArgb)
                                    }
                                    clearSelection()
                                },
                                onSearch = {
                                    textSelection?.let { openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, it.text) }
                                    clearSelection()
                                },
                                onDefine = {
                                    textSelection?.let { runPdfAiAction(ReaderAiFeature.DEFINE, it.text) }
                                    clearSelection()
                                },
                                onSpeak = {
                                    textSelection?.let { togglePdfCloudTts(it.text) }
                                    clearSelection()
                                },
                                showDefine = aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
                                showSpeak = aiByokSettings.sanitized().isCloudTtsAvailable,
                                showSearch = featurePolicy.externalLookup,
                                onClear = ::clearSelection
                            )
                        }
                    }
                    isRendering -> CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    renderError != null -> Text(renderError ?: "Failed to render page.", color = MaterialTheme.colorScheme.error)
                }
                DesktopPdfPageScrubOverlay(
                    pageIndex = pageScrubPreview,
                    pageCount = document.pageCount
                )
            }
        }
        AnimatedVisibility(
            visible = showPdfZoomIndicator,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DesktopPdfZoomPercentageIndicator(
                percentage = (zoomControlScale * 100).roundToInt(),
                onResetZoomClick = {
                    cancelPendingPdfZoomPreview()
                    dispatchPdf(SharedPdfReaderAction.ZoomChanged(1f))
                }
            )
        }
        when {
            selectedTextHighlight != null -> {
                DesktopReaderBottomSheet(
                    title = selectedTextHighlight.desktopSheetTitle(),
                    onDismiss = { dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null)) }
                ) {
                    DesktopPdfAnnotationEditor(
                        annotation = selectedTextHighlight,
                        onUpdate = ::updateAnnotation,
                        onDelete = { deleteAnnotation(selectedTextHighlight.id) },
                        onClose = { dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null)) },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(selectedTextHighlight.text))
                            dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
                        },
                        showSearch = featurePolicy.externalLookup,
                        highlighterPalette = pdfHighlighterColors,
                        onHighlighterPaletteChange = ::updatePdfHighlighterPalette,
                        onSearch = {
                            openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, selectedTextHighlight.text)
                            dispatchPdf(SharedPdfReaderAction.AnnotationSelected(null))
                        }
                    )
                }
            }
            selectedEmbeddedAnnotation != null -> {
                DesktopReaderBottomSheet(
                    title = "PDF comment",
                    onDismiss = { selectedEmbeddedAnnotationId = null }
                ) {
                    DesktopPdfEmbeddedAnnotationPanel(
                        annotation = selectedEmbeddedAnnotation,
                        onCopy = { clipboardManager.setText(AnnotatedString(selectedEmbeddedAnnotation.threadText())) },
                        onClose = { selectedEmbeddedAnnotationId = null }
                    )
                }
            }
            pdfExtrasState.aiResult.hasContent -> {
                DesktopReaderAiResultSheet(
                    result = pdfExtrasState.aiResult,
                    onDismiss = { pdfExtrasState = pdfExtrasState.copy(aiResult = ReaderAiResultState()) }
                )
            }
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
    activeTtsChunk: ReaderTtsChunk?,
    searchQuery: String,
    isTextSelectionMode: Boolean,
    selectedAnnotationId: String?,
    selectedEmbeddedAnnotationId: String?,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    highlighterPalette: List<Int>,
    strokeWidth: Float,
    isHighlighterSnapEnabled: Boolean,
    activeTextDraft: SharedPdfTextDraft?,
    richTextController: SharedPdfRichTextController,
    isRichTextMode: Boolean,
    readerAiFeaturesAvailable: Boolean,
    cloudTtsAvailable: Boolean,
    externalLookupAvailable: Boolean,
    themeStyle: DesktopPdfThemeStyle,
    shouldRender: Boolean,
    zoomPreview: DesktopPdfZoomPreview?,
    zoomViewportRootOffset: Offset,
    showPageNumberOverlay: Boolean = true,
    onSelectPage: (Int) -> Unit,
    onCopySelection: (DesktopPdfTextSelection) -> Unit,
    onHighlightSelection: (Int, DesktopPdfTextSelection, IntSize, Int) -> Unit,
    onExternalSearchSelection: (DesktopPdfTextSelection) -> Unit,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    onDefineSelection: (DesktopPdfTextSelection) -> Unit,
    onSpeakSelection: (DesktopPdfTextSelection) -> Unit,
    onEmbeddedAnnotationSelected: (SharedPdfEmbeddedAnnotation) -> Unit,
    onAnnotationSelected: (SharedPdfAnnotation?) -> Unit,
    onLinkActivated: (DesktopPdfLinkTarget) -> Unit,
    onAnnotationAdded: (SharedPdfAnnotation) -> Unit,
    onAnnotationUpdated: (SharedPdfAnnotation) -> Unit,
    onAnnotationsChanged: (List<SharedPdfAnnotation>) -> Unit,
    onTextAnnotationSelected: (SharedPdfAnnotation) -> Unit,
    onTextDraftStarted: (Int, Offset, IntSize) -> Unit,
    onTextDraftChanged: (String, IntSize) -> Unit,
    onTextDraftBoundsChanged: (PdfPageBounds) -> Unit,
    onPan: (Offset) -> Unit,
    onPagePositioned: (Int, Offset) -> Unit
) {
    val documentHandleId = document.handleId
    val density = LocalDensity.current
    var renderedPage by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderError by remember(documentHandleId, pageIndex) { mutableStateOf<String?>(null) }
    var isRendering by remember(documentHandleId, pageIndex) { mutableStateOf(true) }
    var pageCanvasSize by remember(documentHandleId, pageIndex) { mutableStateOf(IntSize.Zero) }
    var pageRootOffset by remember(documentHandleId, pageIndex) { mutableStateOf(Offset.Zero) }
    var selectionStartIndex by remember(documentHandleId, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(documentHandleId, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(documentHandleId, pageIndex) { mutableStateOf<Offset?>(null) }
    var activeSelectionHandle by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfSelectionHandle?>(null) }
    var activeStroke by remember(documentHandleId, pageIndex, selectedTool) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    var eraserPosition by remember(documentHandleId, pageIndex, selectedTool) { mutableStateOf<Offset?>(null) }
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentAnnotations by rememberUpdatedState(annotations)

    fun clearSelection() {
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
        activeSelectionHandle = null
    }

    fun clearInteractionState() {
        clearSelection()
        activeStroke = emptyList()
        eraserPosition = null
    }

    LaunchedEffect(documentHandleId, pageIndex, scale, shouldRender) {
        if (!shouldRender) {
            renderedPage = null
            renderError = null
            isRendering = false
            clearInteractionState()
            return@LaunchedEffect
        }
        val hasPageRender = renderedPage != null
        if (!hasPageRender) {
            isRendering = true
        }
        renderError = null
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        if (pageSize == null) {
            renderedPage = null
            renderError = "Failed to render page."
            isRendering = false
            return@LaunchedEffect
        }
        delay(if (hasPageRender) DesktopPdfZoomRenderDebounceMillis else 45L)
        isRendering = true
        val safeScale = zoomSpec.safeRenderScale(pageSize.width, pageSize.height, scale)
        val result = withContext(Dispatchers.IO) {
            runCatching { DesktopPdfium.renderPage(document, pageIndex, safeScale) }
        }
        result.getOrNull()?.let { renderedPage = it }
        renderError = result.exceptionOrNull()?.message
            ?: if (renderedPage == null) "Failed to render page." else null
        isRendering = false
    }

    LaunchedEffect(isTextSelectionMode) {
        if (!isTextSelectionMode) {
            clearSelection()
        } else {
            activeStroke = emptyList()
            eraserPosition = null
        }
    }

    LaunchedEffect(selectedTool) {
        activeStroke = emptyList()
        eraserPosition = null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        val placeholderScale = zoomSpec.clamp(scale)
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
                .onGloballyPositioned { coordinates ->
                    val rootOffset = coordinates.positionInRoot()
                    pageRootOffset = rootOffset
                    onPagePositioned(pageIndex, rootOffset)
                }
                .onSizeChanged { pageCanvasSize = it }
                .desktopPdfDocumentZoomPreviewLayer(
                    preview = zoomPreview,
                    currentZoom = scale,
                    viewportRootOffset = zoomViewportRootOffset,
                    pageRootOffset = pageRootOffset
                )
                .background(themeStyle.pageBackgroundColor, RoundedCornerShape(2.dp))
                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, selectedTool, isRichTextMode) {
                    if (isRichTextMode) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val point = event.changes.firstOrNull()?.position ?: continue
                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                val highlightHit = if (selectedTool != PdfInkTool.TEXT && selectedTool != PdfInkTool.ERASER) {
                                    currentAnnotations.asReversed().firstOrNull {
                                        it.isDesktopTextSelectionHighlight &&
                                            it.pageIndex == pageIndex &&
                                            it.sharedPdfHitTest(point, pageCanvasSize)
                                    }
                                } else {
                                    null
                                }
                                if (highlightHit != null) {
                                    onSelectPage(pageIndex)
                                    onAnnotationSelected(highlightHit)
                                    clearInteractionState()
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
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
                .pointerInput(pageIndex, pageCanvasSize, isTextSelectionMode, isRichTextMode) {
                    if (isRichTextMode || !isTextSelectionMode) return@pointerInput
                    detectTapGestures(
                        onLongPress = { point ->
                            val selection = document.wordSelectionAt(pageIndex, point, pageCanvasSize)
                            if (selection != null) {
                                onSelectPage(pageIndex)
                                selectionStartIndex = null
                                selectionEndIndex = null
                                selectionStartHit = null
                                selectionEndHit = null
                                activeSelectionHandle = null
                                textSelection = selection
                                selectionMenuOffset = selection.menuAnchor(pageCanvasSize, point)
                                logPdfSelection(
                                    "long_press page=${pageIndex + 1} " +
                                        "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                        "range=${selection.startIndex}..${selection.endIndex} " +
                                        "chars=${selection.text.length} " +
                                        "text=\"${selection.text.logPreview()}\""
                                )
                            }
                        }
                    )
                }
                .pointerInput(pageIndex, selectedTool, isTextSelectionMode, isRichTextMode) {
                    if (isRichTextMode || isTextSelectionMode || selectedTool != PdfInkTool.NONE) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                        val pointerId = down.id
                        var dragStarted = false
                        var dragDistance = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: return@awaitEachGesture
                            if (change.changedToUp()) {
                                return@awaitEachGesture
                            }
                            if (!change.positionChanged()) continue
                            val delta = change.positionChange()
                            if (!dragStarted) {
                                dragDistance += delta.getDistance()
                                if (dragDistance <= viewConfiguration.touchSlop) {
                                    continue
                                }
                                dragStarted = true
                                change.consume()
                                continue
                            }
                            onPan(delta)
                            change.consume()
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
                            var latestSelectionDragPoint: Offset? = null
                            var lastSelectionPreviewAt = 0L
                            detectDragGestures(
                                onDragStart = { start ->
                                    latestSelectionDragPoint = start
                                    lastSelectionPreviewAt = 0L
                                    onSelectPage(pageIndex)
                                    activeStroke = emptyList()
                                    selectionMenuOffset = null
                                    val existingSelection = textSelection
                                    val handle = existingSelection?.handleAt(start, pageCanvasSize)
                                    activeSelectionHandle = handle
                                    val hit = document.charHitAt(pageIndex, start, pageCanvasSize)
                                    if (handle != null && existingSelection != null) {
                                        selectionStartHit = null
                                        selectionStartIndex = when (handle) {
                                            DesktopPdfSelectionHandle.START -> existingSelection.endIndex
                                            DesktopPdfSelectionHandle.END -> existingSelection.startIndex
                                        }
                                        selectionEndHit = hit
                                        selectionEndIndex = hit?.index ?: when (handle) {
                                            DesktopPdfSelectionHandle.START -> existingSelection.startIndex
                                            DesktopPdfSelectionHandle.END -> existingSelection.endIndex
                                        }
                                    } else {
                                        selectionStartHit = hit
                                        selectionStartIndex = hit?.index
                                        selectionEndHit = null
                                        selectionEndIndex = null
                                        textSelection = null
                                    }
                                    logPdfSelection(
                                        "drag_start page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
                                            "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                            "handle=${handle?.name ?: "none"} " +
                                            hit.formatLogHit("start")
                                    )
                                },
                                onDrag = { change, _ ->
                                    latestSelectionDragPoint = change.position
                                    val now = System.currentTimeMillis()
                                    if (lastSelectionPreviewAt == 0L ||
                                        now - lastSelectionPreviewAt >= DesktopPdfSelectionPreviewThrottleMillis
                                    ) {
                                        lastSelectionPreviewAt = now
                                        val startIndex = selectionStartIndex
                                        val hit = document.charHitAt(pageIndex, change.position, pageCanvasSize)
                                        selectionEndHit = hit
                                        val endIndex = hit?.index
                                        val previousEndIndex = selectionEndIndex
                                        selectionEndIndex = endIndex
                                        if (endIndex != previousEndIndex || textSelection == null) {
                                            textSelection = if (startIndex != null && endIndex != null) {
                                                document.selectionPreviewBetweenIndexes(
                                                    pageIndex = pageIndex,
                                                    startIndex = startIndex,
                                                    endIndex = endIndex,
                                                    canvasSize = pageCanvasSize
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    val finalHit = latestSelectionDragPoint
                                        ?.let { document.charHitAt(pageIndex, it, pageCanvasSize) }
                                        ?: selectionEndHit
                                    if (finalHit != null) {
                                        selectionEndHit = finalHit
                                        selectionEndIndex = finalHit.index
                                    }
                                    val startIndex = selectionStartIndex
                                    val endIndex = selectionEndIndex
                                    val selection = if (startIndex != null && endIndex != null) {
                                        document.selectionBetweenIndexes(
                                            pageIndex = pageIndex,
                                            startIndex = startIndex,
                                            endIndex = endIndex,
                                            canvasSize = pageCanvasSize,
                                            useNativeBounds = true
                                        )
                                    } else {
                                        textSelection?.takeIf { it.text.isNotBlank() }
                                    }
                                    textSelection = selection
                                    selectionMenuOffset = selection?.menuAnchor(
                                        pageCanvasSize,
                                        finalHit?.point ?: selectionEndHit?.point ?: selectionStartHit?.point
                                    )
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
                                    activeSelectionHandle = null
                                    latestSelectionDragPoint = null
                                    lastSelectionPreviewAt = 0L
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
                                    activeSelectionHandle = null
                                    latestSelectionDragPoint = null
                                    lastSelectionPreviewAt = 0L
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
                        } else if (selectedTool != PdfInkTool.NONE) {
                            var eraserPreviousPoint: Offset? = null
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                val start = down.position
                                onSelectPage(pageIndex)
                                clearInteractionState()
                                if (selectedTool == PdfInkTool.ERASER) {
                                    eraserPosition = start
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

                                val pointerId = down.id
                                var dragStarted = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) {
                                        eraserPreviousPoint = null
                                        eraserPosition = null
                                        activeStroke = emptyList()
                                        return@awaitEachGesture
                                    }
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: run {
                                            eraserPreviousPoint = null
                                            eraserPosition = null
                                            activeStroke = emptyList()
                                            return@awaitEachGesture
                                        }
                                    if (change.changedToUp()) {
                                        change.consume()
                                        if (selectedTool != PdfInkTool.ERASER && activeStroke.isNotEmpty()) {
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
                                        eraserPreviousPoint = null
                                        eraserPosition = null
                                        activeStroke = emptyList()
                                        return@awaitEachGesture
                                    }
                                    if (!change.positionChanged()) continue
                                    val distance = (change.position - start).getDistance()
                                    if (selectedTool != PdfInkTool.ERASER && !dragStarted && distance <= viewConfiguration.touchSlop) continue
                                    dragStarted = true
                                    if (selectedTool == PdfInkTool.ERASER) {
                                        val point = change.position
                                        eraserPosition = point
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
                                    change.consume()
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                !shouldRender -> {
                    Text("Page ${pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                    val ttsHighlightBounds: List<PdfPageBounds> = remember(
                        document.path,
                        activeTtsChunk,
                        pageIndex,
                        pageCanvasSize
                    ) {
                        val chunk = activeTtsChunk?.takeIf { it.pageIndex == pageIndex }
                        if (chunk == null || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0 || chunk.endOffset <= chunk.startOffset) {
                            emptyList()
                        } else {
                            DesktopPdfium.textRectsForRange(
                                document = document,
                                pageIndex = pageIndex,
                                startIndex = chunk.startOffset,
                                endIndex = chunk.endOffset - 1,
                                viewportWidth = pageCanvasSize.width,
                                viewportHeight = pageCanvasSize.height
                            ).map { it.toPdfPageBounds() }
                                .filter { it.right > it.left && it.bottom > it.top }
                                .mergePdfBoundsByLine()
                        }
                    }

                    DesktopPdfThemedPageImage(
                        bitmap = pageRender.image,
                        contentDescription = "PDF page ${pageIndex + 1}",
                        themeStyle = themeStyle,
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
                    PdfSearchHighlightOverlay(
                        bounds = ttsHighlightBounds,
                        canvasSize = pageCanvasSize,
                        color = Color(0x887DD3FC)
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
                        selectedAnnotationId = selectedAnnotationId,
                        eraserPosition = eraserPosition,
                        showEraserIndicator = selectedTool == PdfInkTool.ERASER,
                        eraserStrokeWidth = strokeWidth
                    )
                    PdfTextSelectionHandles(
                        selection = textSelection,
                        canvasSize = pageCanvasSize,
                        activeHandle = activeSelectionHandle
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
                    if (showPageNumberOverlay) {
                        SharedPdfPageNumberOverlay(
                            pageIndex = pageIndex,
                            pageCount = document.pageCount
                        )
                    }
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
                        highlighterPalette = highlighterPalette,
                        onHighlighterPaletteChange = onHighlighterPaletteChange,
                        onCopy = {
                            textSelection?.let(onCopySelection)
                            clearSelection()
                        },
                        onHighlight = { colorArgb ->
                            textSelection?.let { onHighlightSelection(pageIndex, it, pageCanvasSize, colorArgb) }
                            clearSelection()
                        },
                        onSearch = {
                            textSelection?.let(onExternalSearchSelection)
                            clearSelection()
                        },
                        onDefine = {
                            textSelection?.let(onDefineSelection)
                            clearSelection()
                        },
                        onSpeak = {
                            textSelection?.let(onSpeakSelection)
                            clearSelection()
                        },
                        showDefine = readerAiFeaturesAvailable,
                        showSpeak = cloudTtsAvailable,
                        showSearch = externalLookupAvailable,
                        onClear = ::clearSelection
                    )
                }
                isRendering -> CircularProgressIndicator()
                renderError != null -> Text(renderError ?: "Failed to render page.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onReturnToLibrary: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String?,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onPickCustomFont: () -> String?,
    customFonts: List<CustomFontItem>,
    readerExtrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    externalLookupAvailable: Boolean,
    cloudTtsControlsAvailable: Boolean,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onAiResultDismiss: () -> Unit,
    onCloudTtsToggle: (String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    readerTextureDataUri: (String) -> String?,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    webViewRuntimeState: DesktopWebViewRuntimeState,
    webViewNetworkAccessEnabled: Boolean,
    epubPaginationCache: SharedEpubPaginationCache,
    epubPaginationCacheGeneration: Int
) {
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val paginationCacheWriteScope = rememberCoroutineScope()
    val measuredPaginator = remember(
        textMeasurer,
        density,
        session.reader.settings.fontFamily,
        session.reader.settings.customFontPath,
        epubPaginationCache,
        paginationCacheWriteScope
    ) {
        SharedMeasuredEpubPaginator(
            textMeasurer = textMeasurer,
            density = density,
            fontFamily = session.reader.settings.toDesktopReaderFontFamily(),
            pageCache = epubPaginationCache,
            cacheWriteScope = paginationCacheWriteScope
        )
    }
    var readerViewport by remember(session.reader.book.id) { mutableStateOf(ReaderViewportSpec(0, 0)) }
    val paginationLayoutSignature = session.reader.settings.layoutSignature()
    val paginationContentSignature = remember(session.reader.book) {
        session.reader.book.desktopPaginationContentSignature()
    }
    val paginationDensitySignature = DesktopEpubPaginationDensity(
        density = density.density,
        fontScale = density.fontScale
    )
    val measuredPaginationRequest = remember(
        session.reader.book.id,
        paginationContentSignature,
        paginationLayoutSignature,
        readerViewport,
        paginationDensitySignature,
        epubPaginationCacheGeneration
    ) {
        if (session.reader.settings.readingMode == ReaderReadingMode.PAGINATED && readerViewport.isSpecified) {
            DesktopEpubPaginationRequest(
                bookId = session.reader.book.id,
                chapterSignature = paginationContentSignature,
                layoutSignature = paginationLayoutSignature,
                viewport = readerViewport,
                density = paginationDensitySignature,
                cacheGeneration = epubPaginationCacheGeneration
            )
        } else {
            null
        }
    }
    var completedMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    var runningMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    val paginatedLayoutReady = session.reader.settings.readingMode != ReaderReadingMode.PAGINATED ||
        (measuredPaginationRequest != null && completedMeasuredPaginationRequest == measuredPaginationRequest)
    val latestSession by rememberUpdatedState(session)
    val latestOnSessionChange by rememberUpdatedState(onSessionChange)
    var externalLinkDialogUrl by remember { mutableStateOf<String?>(null) }
    var lastHandledLink by remember { mutableStateOf<DesktopEpubHandledLink?>(null) }
    var isFullscreen by remember(session.reader.book.id) { mutableStateOf(false) }
    val currentReaderFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnReaderFullscreenChange by rememberUpdatedState(onFullscreenChange)

    fun setReaderFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        onFullscreenChange(enabled)
    }

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    fun handleReaderFullscreenAwtKeyEvent(event: AwtKeyEvent): Boolean {
        val action = event.desktopReaderKeyNavigationOrNull(fullscreen = isFullscreen) ?: return false
        val currentSession = latestSession
        when (action) {
            DesktopReaderKeyNavigation.NEXT -> latestOnSessionChange(currentSession.reduce(ReaderAction.NextPage, readerEngine))
            DesktopReaderKeyNavigation.PREVIOUS -> latestOnSessionChange(currentSession.reduce(ReaderAction.PreviousPage, readerEngine))
            DesktopReaderKeyNavigation.FIRST -> latestOnSessionChange(currentSession.reduce(ReaderAction.JumpToPage(0), readerEngine))
            DesktopReaderKeyNavigation.LAST -> latestOnSessionChange(currentSession.reduce(ReaderAction.JumpToPage(currentSession.reader.pages.lastIndex), readerEngine))
            DesktopReaderKeyNavigation.SEARCH -> latestOnSessionChange(currentSession.reduce(ReaderAction.SearchOpened, readerEngine))
            DesktopReaderKeyNavigation.NEXT_SEARCH -> latestOnSessionChange(currentSession.reduce(ReaderAction.JumpToNextSearchResult, readerEngine))
            DesktopReaderKeyNavigation.EXIT_FULLSCREEN -> if (isFullscreen) setReaderFullscreen(false)
        }
        return true
    }
    DesktopReaderFullscreenKeyEffect(
        enabled = isFullscreen && externalLinkDialogUrl == null,
        onKeyPressed = { event -> handleReaderFullscreenAwtKeyEvent(event) }
    )

    LaunchedEffect(session.reader.settings.readingMode) {
        if (session.reader.settings.readingMode != ReaderReadingMode.PAGINATED) {
            completedMeasuredPaginationRequest = null
            runningMeasuredPaginationRequest = null
        }
    }

    DisposableEffect(session.reader.book.id) {
        onDispose {
            if (currentReaderFullscreen) {
                currentOnReaderFullscreenChange(false)
            }
        }
    }

    LaunchedEffect(
        measuredPaginationRequest,
        measuredPaginator
    ) {
        val request = measuredPaginationRequest ?: return@LaunchedEffect
        if (completedMeasuredPaginationRequest == request) {
            logEpubPagination(
                "reflow_skip reason=request_already_measured book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx}"
            )
            return@LaunchedEffect
        }
        delay(280L)
        val settings = latestSession.reader.settings
        if (settings.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
        if (settings.layoutSignature() != request.layoutSignature) return@LaunchedEffect
        runningMeasuredPaginationRequest = request
        try {
            val reflowStartSession = latestSession
            val reflowStartRequestId = reflowStartSession.navigationRequestId
            val reflowAnchor = readerEngine.reflowAnchorFor(reflowStartSession)
            logEpubPagination(
                "reflow_start book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                    "spread=${settings.pageSpreadMode} font=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                    "margins=${settings.resolvedHorizontalMargin}x${settings.resolvedVerticalMargin} " +
                    "pageWidthSetting=${settings.pageWidth} oldPages=${reflowStartSession.reader.pages.size} " +
                    "anchorPage=${reflowAnchor?.pageIndex} anchorOffsets=${reflowAnchor?.startOffset}..${reflowAnchor?.endOffset}"
            )
            val pages = measuredPaginator.paginate(
                book = session.reader.book,
                settings = settings,
                viewport = request.viewport
            )
            val layoutChanged = pages.isNotEmpty() && !latestSession.reader.pages.samePageLayoutAs(pages)
            logEpubPagination(
                "reflow_result book=\"${session.reader.book.title.logPreview()}\" pages=${pages.size} " +
                    "layoutChanged=$layoutChanged currentPages=${latestSession.reader.pages.size}"
            )
            if (layoutChanged) {
                latestOnSessionChange(
                    readerEngine.replacePages(
                        state = latestSession,
                        pages = pages,
                        reflowAnchor = reflowAnchor,
                        navigationRequestIdAtReflowStart = reflowStartRequestId
                    )
                )
            }
            if (pages.isNotEmpty()) {
                completedMeasuredPaginationRequest = request
            }
        } finally {
            if (runningMeasuredPaginationRequest == request) {
                runningMeasuredPaginationRequest = null
            }
        }
    }

    val handleDesktopSelectionAction: (DesktopReaderSelectionAction, String) -> Unit = { action, text ->
        val settings = aiByokSettings.sanitized()
        when (action) {
            DesktopReaderSelectionAction.DEFINE -> {
                if (settings.areReaderAiFeaturesAvailable) onAiAction(ReaderAiFeature.DEFINE, text)
            }
            DesktopReaderSelectionAction.SPEAK -> {
                if (settings.isCloudTtsAvailable) onCloudTtsToggle(text)
            }
            DesktopReaderSelectionAction.SEARCH -> onExternalLookup(ReaderExternalLookupAction.SEARCH, text)
        }
    }
    val nativeSelectionActions = buildSet {
        val settings = aiByokSettings.sanitized()
        if (settings.areReaderAiFeaturesAvailable) add(SharedNativeReaderSelectionAction.DEFINE)
        if (externalLookupAvailable) add(SharedNativeReaderSelectionAction.SEARCH)
        if (settings.isCloudTtsAvailable) add(SharedNativeReaderSelectionAction.SPEAK)
    }
    val handleNativeSelectionAction: (SharedNativeReaderSelectionAction, String) -> Unit = { action, text ->
        when (action) {
            SharedNativeReaderSelectionAction.DEFINE ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.DEFINE, text)
            SharedNativeReaderSelectionAction.SPEAK ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SPEAK, text)
            SharedNativeReaderSelectionAction.SEARCH ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SEARCH, text)
        }
    }
    val handleDesktopEpubLinkClicked: (DesktopEpubLinkClick) -> Unit = { link ->
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
                    if (externalLookupAvailable) {
                        externalLinkDialogUrl = target.url
                    }
                }
                is ReaderLinkTarget.Internal -> {
                    logEpubLink(
                        "resolved_internal chapter=${target.locator.chapterIndex} " +
                            "page=${target.locator.pageIndex} offset=${target.locator.startOffset}"
                    )
                    onSessionChange(readerEngine.jumpToLocator(session, target.locator))
                }
                ReaderLinkTarget.Ignored -> {
                    logEpubLink("resolved_ignored href=\"${link.href.logPreview()}\"")
                }
            }
        }
    }

    SharedReaderScreen(
        session = session,
        readerEngine = readerEngine,
        onSessionChange = onSessionChange,
        onReturnToLibrary = onReturnToLibrary,
        isFullscreen = isFullscreen,
        onFullscreenChange = ::setReaderFullscreen,
        toolbarPreferences = toolbarPreferences,
        onToolbarPreferencesChange = onToolbarPreferencesChange,
        highlightPalette = highlightPalette,
        onHighlightPaletteChange = onHighlightPaletteChange,
        ttsReplacementPreferences = ttsReplacementPreferences,
        ttsReplacementBookId = ttsReplacementBookId,
        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
        onPickCustomFont = onPickCustomFont,
        customFonts = customFonts,
        readerExtrasState = readerExtrasState,
        aiByokSettings = aiByokSettings,
        externalLookupAvailable = externalLookupAvailable,
        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
        onExternalLookup = onExternalLookup,
        onAiAction = onAiAction,
        onAiResultDismiss = onAiResultDismiss,
        onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
        onCloudTtsStart = onCloudTtsStart,
        onCloudTtsPauseResume = onCloudTtsPauseResume,
        onCloudTtsStop = onCloudTtsStop,
        onCloudTtsClearCache = onCloudTtsClearCache,
        onAutoScrollChange = onAutoScrollChange,
        readerTextureDataUri = readerTextureDataUri,
        readerCustomTextureIds = readerCustomTextureIds,
        onImportReaderTexture = onImportReaderTexture
    ) { renderPlan, onVisiblePageChanged, onHighlightSelected ->
        Surface(
            color = renderPlan.background,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { size ->
                    val next = ReaderViewportSpec(size.width, size.height)
                    logReaderGap(
                        "desktop_epub_reader_surface size=${size.width}x${size.height} " +
                            "mode=${session.reader.settings.readingMode} " +
                            "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)}"
                    )
                    if (next != readerViewport) {
                        logEpubPagination(
                            "viewport_changed width=${next.widthPx} height=${next.heightPx} " +
                                "previous=${readerViewport.widthPx}x${readerViewport.heightPx}"
                        )
                        readerViewport = next
                    }
                }
        ) {
            if (renderPlan is ReaderContentRenderPlan.NativePaginatedPages && !paginatedLayoutReady) {
                DesktopEpubPaginationPreparing(
                    active = runningMeasuredPaginationRequest != null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                when (renderPlan) {
                    is ReaderContentRenderPlan.WebDocument -> {
                        if (webViewRuntimeState.initialized) {
                            DesktopEpubWebView(
                                html = renderPlan.html,
                                appearanceScript = renderPlan.appearanceScript,
                                navigationTarget = renderPlan.navigationTarget,
                                highlights = renderPlan.highlights,
                                onHighlightCreated = { highlight ->
                                    onSessionChange(session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine))
                                },
                                onHighlightSelected = onHighlightSelected,
                                isFullscreen = isFullscreen,
                                onKeyboardNavigation = { action ->
                                    when (action) {
                                        DesktopReaderKeyNavigation.NEXT -> onSessionChange(session.reduce(ReaderAction.NextPage, readerEngine))
                                        DesktopReaderKeyNavigation.PREVIOUS -> onSessionChange(session.reduce(ReaderAction.PreviousPage, readerEngine))
                                        DesktopReaderKeyNavigation.FIRST -> onSessionChange(session.reduce(ReaderAction.JumpToPage(0), readerEngine))
                                        DesktopReaderKeyNavigation.LAST -> onSessionChange(session.reduce(ReaderAction.JumpToPage(session.reader.pages.lastIndex), readerEngine))
                                        DesktopReaderKeyNavigation.SEARCH -> onSessionChange(session.reduce(ReaderAction.SearchOpened, readerEngine))
                                        DesktopReaderKeyNavigation.NEXT_SEARCH -> onSessionChange(session.reduce(ReaderAction.JumpToNextSearchResult, readerEngine))
                                        DesktopReaderKeyNavigation.EXIT_FULLSCREEN -> if (isFullscreen) setReaderFullscreen(false)
                                    }
                                },
                                onSelectionAction = handleDesktopSelectionAction,
                                onLinkClicked = handleDesktopEpubLinkClicked,
                                onVisiblePageChanged = onVisiblePageChanged,
                                networkAccessEnabled = webViewNetworkAccessEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DesktopWebViewRuntimeIndicator(
                                state = webViewRuntimeState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    is ReaderContentRenderPlan.NativePaginatedPages -> {
                        SharedNativePaginatedReader(
                            renderPlan = renderPlan,
                            readerFontFamily = renderPlan.settings.toDesktopReaderFontFamily(),
                            searchHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            onVisiblePageChanged = onVisiblePageChanged,
                            enabledSelectionActions = nativeSelectionActions,
                            onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
                            onSelectionAction = handleNativeSelectionAction,
                            onHighlightCreated = { highlight ->
                                onSessionChange(session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine))
                            },
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = { link ->
                                handleDesktopEpubLinkClicked(link.toDesktopEpubLinkClick())
                            },
                            imageContent = { image, imageModifier ->
                                DesktopEpubNativeImage(
                                    image = image,
                                    modifier = imageModifier
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopEpubWebView(
    html: String,
    appearanceScript: String,
    navigationTarget: ReaderContentNavigationTarget,
    highlights: List<UserHighlight>,
    onHighlightCreated: (UserHighlight) -> Unit,
    onHighlightSelected: (String) -> Unit,
    isFullscreen: Boolean,
    onKeyboardNavigation: (DesktopReaderKeyNavigation) -> Unit,
    onSelectionAction: (DesktopReaderSelectionAction, String) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    networkAccessEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val latestOnHighlightCreated by rememberUpdatedState(onHighlightCreated)
    val latestOnHighlightSelected by rememberUpdatedState(onHighlightSelected)
    val latestOnKeyboardNavigation by rememberUpdatedState(onKeyboardNavigation)
    val latestOnSelectionAction by rememberUpdatedState(onSelectionAction)
    val latestOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val latestOnVisiblePageChanged by rememberUpdatedState(onVisiblePageChanged)
    val scope = rememberCoroutineScope()
    val linkRequestInterceptor = remember(scope, networkAccessEnabled) {
        object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult {
                if (!networkAccessEnabled && request.url.isRemoteNetworkUrl()) {
                    logEpubLink("request_blocked_offline url=\"${request.url.logPreview()}\"")
                    return WebRequestInterceptResult.Reject
                }
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
                val highlight = EpubAnnotationSerializer.parseHighlightJsonLenient(message.params)
                if (highlight == null) {
                    logEpubSelectionDebug("highlight_parse_failed params=${message.params.logPreview(900)}")
                } else {
                    scope.launch { latestOnHighlightCreated(highlight) }
                }
            }
        }
        val highlightSelectionHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerHighlightClicked"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                message.params.readerHighlightClickOrNull()?.let { highlightClick ->
                    scope.launch { latestOnHighlightSelected(highlightClick.highlightId) }
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
        val selectionActionHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerSelectionAction"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                val selectionAction = message.params.readerSelectionActionOrNull()
                if (selectionAction != null) {
                    scope.launch { latestOnSelectionAction(selectionAction.action, selectionAction.text) }
                }
            }
        }
        val keyNavigationHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerKeyNavigation"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                message.params.readerKeyNavigationOrNull()?.let { action ->
                    scope.launch { latestOnKeyboardNavigation(action) }
                }
            }
        }
        val ttsHighlightLogHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerTtsHighlightLog"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                logDesktopTts("epub_highlight_js ${message.params.logPreview(500)}")
            }
        }
        val selectionDebugLogHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerSelectionDebugLog"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                logEpubSelectionDebug(message.params.readerSelectionDebugMessageOrNull() ?: message.params.logPreview(900))
            }
        }
        val paginationLayoutLogHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerPaginationLayoutLog"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                logEpubPagination(message.params.readerPaginationLogMessageOrNull() ?: message.params.logPreview(900))
            }
        }
        val gapLayoutLogHandler = object : IJsMessageHandler {
            override fun methodName(): String = "readerGapLayoutLog"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                logReaderGap(message.params.readerPaginationLogMessageOrNull() ?: message.params.logPreview(900))
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
        bridge.register(highlightSelectionHandler)
        bridge.register(positionHandler)
        bridge.register(selectionActionHandler)
        bridge.register(keyNavigationHandler)
        bridge.register(ttsHighlightLogHandler)
        bridge.register(selectionDebugLogHandler)
        bridge.register(paginationLayoutLogHandler)
        bridge.register(gapLayoutLogHandler)
        bridge.register(linkHandler)
        onDispose {
            bridge.unregister(highlightHandler)
            bridge.unregister(highlightSelectionHandler)
            bridge.unregister(positionHandler)
            bridge.unregister(selectionActionHandler)
            bridge.unregister(keyNavigationHandler)
            bridge.unregister(ttsHighlightLogHandler)
            bridge.unregister(selectionDebugLogHandler)
            bridge.unregister(paginationLayoutLogHandler)
            bridge.unregister(gapLayoutLogHandler)
            bridge.unregister(linkHandler)
        }
    }

    val state = remember {
        WebViewState(
            WebContent.Data(
                data = html,
                baseUrl = null,
                encoding = "utf-8",
                mimeType = "text/html",
                historyUrl = null
            )
        )
    }

    LaunchedEffect(html) {
        navigator.loadHtml(
            html = html,
            baseUrl = null,
            mimeType = "text/html",
            encoding = "utf-8",
            historyUrl = null
        )
    }

    Box(modifier = modifier) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            captureBackPresses = false,
            navigator = navigator,
            webViewJsBridge = bridge
        )

        LaunchedEffect(state.loadingState) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            navigator.evaluateJavaScript(
                """
                (function () {
                  if (window.readerDesktopKeyNavigationInstalled) return;
                  window.readerDesktopKeyNavigationInstalled = true;
                  document.addEventListener('keydown', function (event) {
                    var target = event.target;
                    var tag = target && target.tagName ? target.tagName.toLowerCase() : '';
                    if (target && (target.isContentEditable || tag === 'input' || tag === 'textarea' || tag === 'select')) return;
                    var action = null;
                    if (event.ctrlKey && (event.key === 'f' || event.key === 'F')) action = 'search';
                    else if (event.ctrlKey && (event.key === 'g' || event.key === 'G')) action = 'nextSearch';
                    else if (event.key === 'ArrowRight' || event.key === 'PageDown') action = 'next';
                    else if (event.key === 'ArrowLeft' || event.key === 'PageUp') action = 'previous';
                    else if (event.key === 'Home') action = 'first';
                    else if (event.key === 'End') action = 'last';
                    else if (event.key === 'Escape' && window.readerDesktopFullscreen) action = 'exitFullscreen';
                    if (!action || !window.kmpJsBridge || !window.kmpJsBridge.callNative) return;
                    event.preventDefault();
                    event.stopPropagation();
                    window.kmpJsBridge.callNative('readerKeyNavigation', JSON.stringify({ action: action }));
                  }, true);
                })();
                """.trimIndent()
            )
        }

        LaunchedEffect(isFullscreen, state.loadingState) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerDesktopFullscreen = ${if (isFullscreen) "true" else "false"};")
        }

        LaunchedEffect(html, state.loadingState) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerPaginationLayoutLog && window.readerPaginationLayoutLog('desktop_finished');")
        }

        LaunchedEffect(appearanceScript, state.loadingState) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            navigator.evaluateJavaScript(appearanceScript)
        }

        LaunchedEffect(
            navigationTarget.autoScroll,
            navigationTarget.readingMode,
            state.loadingState
        ) {
            if (navigationTarget.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            val autoScroll = navigationTarget.autoScroll.sanitized()
            val command = if (autoScroll.enabled) {
                "window.readerAutoScroll && window.readerAutoScroll.start(${autoScroll.speed});"
            } else {
                "window.readerAutoScroll && window.readerAutoScroll.stop();"
            }
            navigator.evaluateJavaScript(command)
        }

        LaunchedEffect(
            navigationTarget.requestId,
            navigationTarget.readingMode,
            state.loadingState
        ) {
            if (navigationTarget.readingMode != ReaderReadingMode.VERTICAL) return@LaunchedEffect
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            val locator = navigationTarget.locator ?: return@LaunchedEffect
            navigator.evaluateJavaScript("window.readerScrollToLocator && window.readerScrollToLocator(${locator.toReaderLocatorJson()});")
        }

        LaunchedEffect(
            navigationTarget.ttsRequestId,
            navigationTarget.ttsLocator,
            navigationTarget.readingMode,
            state.loadingState
        ) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            val locator = navigationTarget.ttsLocator
            val command = if (locator == null) {
                logDesktopTts(
                    "epub_highlight_command clear mode=${navigationTarget.readingMode} request=${navigationTarget.ttsRequestId}"
                )
                "window.readerSetTtsLocator && window.readerSetTtsLocator(null, false);"
            } else {
                val follow = navigationTarget.readingMode == ReaderReadingMode.VERTICAL
                logDesktopTts(
                    "epub_highlight_command set mode=${navigationTarget.readingMode} request=${navigationTarget.ttsRequestId} " +
                        "follow=$follow chapter=${locator.chapterIndex} page=${locator.pageIndex} " +
                        "offsets=${locator.startOffset}..${locator.endOffset} cfi=\"${locator.cfi.orEmpty().logPreview()}\" " +
                        "text=\"${locator.textQuote.orEmpty().logPreview()}\""
                )
                "window.readerSetTtsLocator && window.readerSetTtsLocator(${locator.toReaderLocatorJson()}, $follow);"
            }
            navigator.evaluateJavaScript(command)
        }

        LaunchedEffect(highlights, state.loadingState) {
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
