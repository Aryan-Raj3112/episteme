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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import androidx.compose.ui.text.platform.Font as DesktopFont
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiModelOption
import com.aryan.reader.shared.ReaderAiModelOptions
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
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
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.maskedReaderAiKey
import com.aryan.reader.shared.withTtsReplacements
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
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
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
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SampleReaderBooks
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import com.aryan.reader.shared.ui.ReaderWorkspaceShell
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedAppShell
import com.aryan.reader.shared.ui.SharedAppTab
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedAboutScreen
import com.aryan.reader.shared.ui.SharedBookEditDialog
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedConfirmDialog
import com.aryan.reader.shared.ui.SharedCustomFontsScreen
import com.aryan.reader.shared.ui.SharedHelpFeedbackScreen
import com.aryan.reader.shared.ui.SharedHomeScreen
import com.aryan.reader.shared.ui.SharedLibraryScreen
import com.aryan.reader.shared.ui.SharedMarkdownText
import com.aryan.reader.shared.ui.SharedOpdsScreen
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
import com.aryan.reader.shared.ui.SharedReaderThemeControls
import com.aryan.reader.shared.ui.SharedReaderTtsReplacementControls
import com.aryan.reader.shared.ui.SharedShelvesScreen
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.ui.pdfReaderWorkspaceModel
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
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.JFileChooser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun main() {
    configureComposeSwingInterop()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Episteme",
        ) {
            EpistemeDesktopApp(window)
        }
    }
}

internal const val ComposeInteropBlendingProperty = "compose.interop.blending"
internal const val ComposeInteropBlendingEnabled = "true"

internal fun configureComposeSwingInterop() {
    // Must run before Compose creates the desktop window. Vertical EPUB embeds a Swing-backed
    // JCEF WebView, and current Compose interop can leave a stale black native rectangle after
    // that reader surface is removed unless interop blending is enabled.
    if (System.getProperty(ComposeInteropBlendingProperty).isNullOrBlank()) {
        System.setProperty(ComposeInteropBlendingProperty, ComposeInteropBlendingEnabled)
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
    val customFontStore = remember { DesktopCustomFontStore() }
    val opdsRepository = remember { DesktopOpdsRepository() }
    val opdsController = remember {
        SharedOpdsController(
            repository = opdsRepository,
            idFactory = { UUID.randomUUID().toString() }
        )
    }
    val aiByokStore = remember { DesktopAiByokStore() }
    var aiByokSettings by remember { mutableStateOf(aiByokStore.load()) }
    val desktopAiAdapter = remember {
        DesktopByokAiAdapter { aiByokSettings }
    }
    val desktopTtsAdapter = remember {
        DesktopGeminiCloudTtsAdapter(settingsProvider = { aiByokSettings })
    }
    val initialLibrarySnapshot = remember { libraryDatabase.load() }
    val scope = rememberCoroutineScope()
    var webViewRuntimeState by remember { mutableStateOf(DesktopWebViewRuntimeState()) }
    var readerCustomTextureIds by remember { mutableStateOf(DesktopReaderTextures.importedTextureIds()) }

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
            readerHighlightPalette = initialLibrarySnapshot.readerHighlightPalette,
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
    var readerSession by remember { mutableStateOf(readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())) }
    var readerExtrasState by remember {
        mutableStateOf(
            ReaderExtrasState(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = aiByokSettings.isCloudTtsAvailable,
                    cacheSummary = desktopTtsAdapter.cacheSummary(
                        readerSession.reader.book.title,
                        aiByokSettings.sanitized().ttsSpeakerId
                    )
                )
            )
        )
    }
    var activePdfDocument by remember { mutableStateOf<DesktopPdfDocument?>(null) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var showAiByokSettingsDialog by remember { mutableStateOf(false) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookEditDialogFor by remember { mutableStateOf<BookItem?>(null) }
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
                        readerToolbarPreferences = projected.readerToolbarPreferences,
                        readerHighlightPalette = projected.readerHighlightPalette,
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

    fun updateAiByokSettings(next: ReaderAiByokSettings) {
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
        desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)

    fun readerCloudTtsStoppedState(statusMessage: String? = null, errorMessage: String? = null) = ReaderCloudTtsState(
        isAvailable = aiByokSettings.sanitized().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = currentReaderTtsCacheSummary()
    )

    fun openReaderExternalLookup(action: ReaderExternalLookupAction, text: String) {
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
                if (error is kotlinx.coroutines.CancellationException) {
                    readerExtrasState = readerExtrasState.copy(
                        cloudTts = readerCloudTtsStoppedState(statusMessage = "Stopped")
                    )
                } else {
                    readerExtrasState = readerExtrasState.copy(
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
        val desktopReaderSurface = SharedFileCapabilities.surfaceFor(book.type, ReaderPlatform.DESKTOP)
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
            if (streamReference != null) {
                if (activePdfDocument?.path == path) {
                    activeReaderBookId = book.id
                    recordBookOpened(book.id)
                    selectedTab = SharedAppTab.READER
                    return
                }
                activePdfDocument?.close()
                activePdfDocument = null
                val document = runCatching {
                    DesktopPdfium.loadOpdsStream(
                        path = path,
                        title = book.title?.takeIf { it.isNotBlank() } ?: book.displayName,
                        reference = streamReference,
                        catalog = opdsRepository.catalogById(streamReference.catalogId)
                    )
                }.getOrElse { error ->
                    updateState(
                        state.withBanner(
                            "Could not open OPDS stream: ${error.message ?: "unknown error"}",
                            isError = true
                        )
                    )
                    return
                }
                activePdfDocument = document
                activeReaderBookId = book.id
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
            val readerFile = File(path)
            val readerPath = readerFile.absolutePath
            if (activePdfDocument?.path == readerPath) {
                activeReaderBookId = book.id
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
            activePdfDocument?.close()
            activePdfDocument = null
            val document = runCatching {
                if (book.type == FileType.PDF) {
                    DesktopPdfium.load(readerFile)
                } else {
                    DesktopPdfium.loadComic(readerFile, book.type)
                }
            }.getOrElse { error ->
                updateState(
                    state.withBanner(
                        "Could not open ${SharedFileCapabilities.displayNameFor(book.type)}: " +
                            (error.message ?: "unknown error"),
                        isError = true
                    )
                )
                return
            }

            activePdfDocument = document
            activeReaderBookId = book.id
            recordBookOpened(book.id)
            selectedTab = SharedAppTab.READER
            return
        }

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
            } else {
                SharedJvmBookLoader.load(
                    file = File(path),
                    type = book.type,
                    titleOverride = book.title?.takeIf { it.isNotBlank() },
                    authorOverride = book.author?.takeIf { it.isNotBlank() }
                )
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

    fun importAndOpenBook() {
        val file = chooseBookFile() ?: return
        val importedFile = file.toImportedBookFile()
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
        importFiles(listOf(importedFile))
        openReader(
            BookItem(
                id = file.absolutePath,
                path = file.absolutePath,
                type = type,
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

    fun emitOpds(next: com.aryan.reader.shared.opds.SharedOpdsScreenState) {
        opdsState = next
    }

    fun openOpdsCatalog(catalog: OpdsCatalog) {
        scope.launch {
            opdsController.openCatalog(catalog, ::emitOpds)
        }
    }

    fun openOpdsFeedUrl(url: String) {
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
        scope.launch {
            opdsController.search(query, ::emitOpds)
        }
    }

    fun loadNextOpdsPage() {
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
                readerSession = readerEngine.createSession(SampleReaderBooks.desktopWelcomeBook())
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
                importFiles(listOf(file.toImportedBookFile()))
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
            syncLocalFolders(showBanner = false)
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
                onTabsEnabledChange = { enabled -> updateState(state.reduce(AppAction.TabsEnabledChanged(enabled))) },
                onAiSettingsRequested = { showAiByokSettingsDialog = true }
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

                        SharedAppTab.CATALOGS -> SharedOpdsScreen(
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
                            onClearError = { emitOpds(opdsController.clearError()) }
                        )

                        SharedAppTab.CUSTOM_FONTS -> SharedCustomFontsScreen(
                            fonts = customFonts,
                            onImportFont = { importCustomFont(chooseFontFile()) },
                            onDeleteFont = ::deleteCustomFont,
                            googleFontsAvailable = true,
                            getGoogleFonts = { customFontStore.loadGoogleFontsList() },
                            onDownloadGoogleFont = ::downloadGoogleFont,
                            fontFamilyForPreview = { font -> font.toDesktopPreviewFontFamily() }
                        )

                        SharedAppTab.FEEDBACK -> SharedHelpFeedbackScreen(
                            onOpenGitHubIssues = { openExternalUrl(EpistemeIssuesUrl) },
                            onEmailSupport = {
                                openExternalUrl("mailto:$EpistemeSupportEmail?subject=${EpistemeFeedbackSubject.urlEncode()}")
                            }
                        )

                        SharedAppTab.SUPPORT -> SharedSupportProjectScreen(
                            onOpenGitHubSponsors = { openExternalUrl(EpistemeGitHubSponsorsUrl) },
                            onOpenPatreon = { openExternalUrl(EpistemePatreonUrl) }
                        )

                        SharedAppTab.ABOUT -> SharedAboutScreen(
                            versionName = desktopAppVersionName(),
                            buildLabel = "Desktop build",
                            onOpenSource = { openExternalUrl(EpistemeSourceUrl) },
                            onOpenIssues = { openExternalUrl(EpistemeIssuesUrl) }
                        )

                        SharedAppTab.READER -> {
                            val pdfDocument = activePdfDocument
                            if (pdfDocument != null) {
                                PdfReaderScreen(
                                    document = pdfDocument,
                                    initialPageIndex = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.lastPageIndex }
                                        ?: 0,
                                    initialReaderSettings = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.readerSettings },
                                    onOpenPdf = ::importAndOpenPdf,
                                    onOpenBook = ::importAndOpenBook,
                                    onPageStateChange = { page, progress ->
                                        updateActiveBookReadingState(page, progress)
                                    },
                                    onReaderSettingsChange = ::updateActiveBookReaderSettings,
                                    customTextureIds = readerCustomTextureIds,
                                    onImportTexture = ::importDesktopReaderTexture,
                                    onLocalSidecarsChanged = {
                                        activeReaderBookId
                                            ?.let { bookId -> state.rawLibraryBooks.firstOrNull { it.id == bookId } }
                                            ?.let(::syncBookSidecars)
                                    },
                                    aiByokSettings = aiByokSettings,
                                    aiAdapter = desktopAiAdapter,
                                    ttsAdapter = desktopTtsAdapter
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
                                    onOpenBook = ::importAndOpenBook,
                                    onOpenPdf = ::importAndOpenPdf,
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
                                    onExternalLookup = ::openReaderExternalLookup,
                                    onAiAction = ::runReaderAiAction,
                                    onCloudTtsToggle = ::toggleReaderCloudTts,
                                    onCloudTtsStart = ::startReaderCloudTts,
                                    onCloudTtsPauseResume = ::pauseResumeReaderCloudTts,
                                    onCloudTtsStop = ::stopReaderCloudTts,
                                    onCloudTtsClearCache = ::clearReaderCloudTtsCache,
                                    onAutoScrollChange = ::updateReaderAutoScroll,
                                    readerTextureDataUri = DesktopReaderTextures::dataUriFor,
                                    readerCustomTextureIds = readerCustomTextureIds,
                                    onImportReaderTexture = ::importDesktopReaderTexture,
                                    webViewRuntimeState = webViewRuntimeState
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
            var lastDragState = DesktopDropImportState()

            fun publishDragState(state: DesktopDropImportState) {
                if (state == lastDragState) return
                lastDragState = state
                onDragStateChangeState.value(state)
            }

            val listener = object : DropTargetAdapter() {
                override fun dragEnter(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragOver(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragExit(event: DropTargetEvent) {
                    publishDragState(DesktopDropImportState())
                }

                override fun drop(event: DropTargetDropEvent) {
                    if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.rejectDrop()
                        publishDragState(DesktopDropImportState())
                        return
                    }
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = event.transferable.localDraggedFiles().filter { it.isFile }
                    if (files.isEmpty()) {
                        event.dropComplete(false)
                        publishDragState(DesktopDropImportState())
                        return
                    }

                    onFilesDroppedState.value(files.map { it.toImportedBookFile() })
                    event.dropComplete(true)
                    publishDragState(DesktopDropImportState())
                }

                private fun handleDrag(event: DropTargetDragEvent) {
                    val hasFilePayload = event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    publishDragState(
                        DesktopDropImportState(
                            active = true,
                            hasFilePayload = hasFilePayload
                        )
                    )
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
                publishDragState(DesktopDropImportState())
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

private data class DesktopPdfThemeStyle(
    val theme: ReaderTheme,
    val viewerBackgroundColor: Color,
    val colorFilter: ColorFilter?,
    val textureBitmap: ImageBitmap?,
    val textureAlpha: Float,
    val textureBlendMode: BlendMode
)

@Composable
private fun DesktopPdfThemedPageImage(
    bitmap: ImageBitmap,
    contentDescription: String,
    themeStyle: DesktopPdfThemeStyle,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            colorFilter = themeStyle.colorFilter,
            modifier = Modifier.fillMaxSize()
        )
        val textureBitmap = themeStyle.textureBitmap
        if (textureBitmap != null && themeStyle.textureAlpha > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                    size = size,
                    blendMode = themeStyle.textureBlendMode,
                    alpha = themeStyle.textureAlpha
                )
            }
        }
    }
}

private fun ReaderSettings?.toDesktopPdfReaderSettings(): ReaderSettings {
    val defaults = ReaderSettings(themeId = "no_theme")
    val settings = this ?: defaults
    val themeId = settings.themeId
    val hasPdfTheme = BuiltInPdfReaderThemes.any { it.id == themeId }
    val hasCustomColors = settings.backgroundColorArgb != null && settings.textColorArgb != null
    return settings.copy(
        themeId = when {
            themeId == null -> "no_theme"
            hasPdfTheme || hasCustomColors -> themeId
            else -> "no_theme"
        }
    )
}

private fun ReaderSettings.toDesktopPdfThemeStyle(displayMode: PdfDisplayMode): DesktopPdfThemeStyle {
    val theme = toDesktopPdfTheme()
    val viewerBackground = when (theme.id) {
        "no_theme", "system" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.White else Color.Black
        "reverse" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.Black else Color.White
        else -> theme.backgroundColor.takeIf { it.isSpecified }
            ?: if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.White else Color.Black
    }
    val isDarkTexture = theme.isDark || theme.id == "reverse"
    return DesktopPdfThemeStyle(
        theme = theme,
        viewerBackgroundColor = viewerBackground,
        colorFilter = theme.toDesktopPdfColorFilter(),
        textureBitmap = DesktopReaderTextures.imageBitmapFor(textureId),
        textureAlpha = if (textureId == null) 0f else textureAlpha.coerceIn(0f, 1f),
        textureBlendMode = if (isDarkTexture) BlendMode.Screen else BlendMode.Multiply
    )
}

private fun ReaderSettings.toDesktopPdfTheme(): ReaderTheme {
    BuiltInPdfReaderThemes.firstOrNull { it.id == themeId }?.let { return it }
    val background = backgroundColorArgb?.toComposeColor()
    val text = textColorArgb?.toComposeColor()
    return if (background != null && text != null) {
        ReaderTheme(
            id = themeId ?: "desktop_pdf_custom",
            name = "Custom",
            backgroundColor = background,
            textColor = text,
            isDark = darkMode,
            textureId = textureId,
            isCustom = true
        )
    } else {
        BuiltInPdfReaderThemes.first()
    }
}

private fun ReaderTheme.toDesktopPdfColorFilter(): ColorFilter? {
    return when (id) {
        "no_theme", "system" -> null
        "reverse" -> {
            val colorMatrix = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        }
        else -> {
            if (!backgroundColor.isSpecified || !textColor.isSpecified) return null
            val bgR = backgroundColor.red * 255f
            val bgG = backgroundColor.green * 255f
            val bgB = backgroundColor.blue * 255f
            val fgR = textColor.red * 255f
            val fgG = textColor.green * 255f
            val fgB = textColor.blue * 255f
            val dr = (bgR - fgR) / 255f
            val dg = (bgG - fgG) / 255f
            val db = (bgB - fgB) / 255f
            val lumR = 0.2126f
            val lumG = 0.7152f
            val lumB = 0.0722f
            val colorMatrix = floatArrayOf(
                dr * lumR, dr * lumG, dr * lumB, 0f, fgR,
                dg * lumR, dg * lumG, dg * lumB, 0f, fgG,
                db * lumR, db * lumG, db * lumB, 0f, fgB,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        }
    }
}

private object DesktopReaderTextures {
    private val bytesCache = mutableMapOf<String, ByteArray?>()
    private val dataUriCache = mutableMapOf<String, String?>()
    private val imageCache = mutableMapOf<String, ImageBitmap?>()
    private val importExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun importedTextureIds(): List<String> {
        return readerTextureDirectory()
            .listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) in importExtensions }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.map { ReaderTextureFilePrefix + it.absolutePath }
            .orEmpty()
    }

    fun importTexture(source: File): String? {
        if (!source.isFile) return null
        val extension = source.extension.lowercase(Locale.ROOT)
            .takeIf { it in importExtensions }
            ?: return null
        val safeName = source.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "texture" }
        val directory = readerTextureDirectory().apply { mkdirs() }
        val target = File(directory, "texture_${System.currentTimeMillis()}_$safeName.$extension")
        return runCatching {
            source.copyTo(target, overwrite = false)
            val textureId = ReaderTextureFilePrefix + target.absolutePath
            bytesCache.remove(textureId)
            dataUriCache.remove(textureId)
            imageCache.remove(textureId)
            textureId
        }.getOrNull()
    }

    fun dataUriFor(textureId: String): String? {
        return dataUriCache.getOrPut(textureId) {
            val bytes = bytesFor(textureId) ?: return@getOrPut null
            val extension = textureExtension(textureId)
            "data:${imageMimeTypeForExtension(extension)};base64," +
                Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun imageBitmapFor(textureId: String?): ImageBitmap? {
        val id = textureId ?: return null
        return imageCache.getOrPut(id) {
            val bytes = bytesFor(id) ?: return@getOrPut null
            runCatching {
                ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
            }.getOrNull()
        }
    }

    private fun bytesFor(textureId: String): ByteArray? {
        return bytesCache.getOrPut(textureId) {
            if (textureId.startsWith(ReaderTextureFilePrefix)) {
                File(textureId.removePrefix(ReaderTextureFilePrefix)).takeIf { it.isFile }?.readBytes()
            } else {
                val texture = ReaderTexture.entries.firstOrNull { it.id == textureId } ?: return@getOrPut null
                val classLoader = Thread.currentThread().contextClassLoader ?: DesktopReaderTextures::class.java.classLoader
                classLoader
                    ?.getResourceAsStream(texture.assetPath)
                    ?.use { it.readBytes() }
                    ?: DesktopReaderTextures::class.java.classLoader
                        ?.getResourceAsStream(texture.assetPath)
                        ?.use { it.readBytes() }
            }
        }
    }

    private fun textureExtension(textureId: String): String {
        if (textureId.startsWith(ReaderTextureFilePrefix)) {
            return File(textureId.removePrefix(ReaderTextureFilePrefix)).extension
        }
        return ReaderTexture.entries.firstOrNull { it.id == textureId }
            ?.assetPath
            ?.substringAfterLast('.', "png")
            ?: "png"
    }

    private fun readerTextureDirectory(): File {
        val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
        return File(baseDir, "Episteme/reader_textures")
    }
}

private fun imageMimeTypeForExtension(extension: String): String {
    return when (extension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
}

private fun Long.toComposeColor(): Color {
    return Color(this and 0xFFFFFFFFL)
}

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
    initialReaderSettings: ReaderSettings? = null,
    onOpenPdf: () -> Unit,
    onOpenBook: () -> Unit,
    onPageStateChange: (pageIndex: Int, progress: Float) -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit = {},
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    onLocalSidecarsChanged: () -> Unit = {},
    aiByokSettings: ReaderAiByokSettings,
    aiAdapter: DesktopByokAiAdapter,
    ttsAdapter: DesktopGeminiCloudTtsAdapter
) {
    val zoomSpec = remember { PdfZoomSpec() }
    var pdfReaderSettings by remember(document.path) {
        mutableStateOf(initialReaderSettings.toDesktopPdfReaderSettings())
    }
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
    var pdfExtrasState by remember(document.path) {
        mutableStateOf(
            ReaderExtrasState(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = aiByokSettings.isCloudTtsAvailable,
                    cacheSummary = ttsAdapter.cacheSummary(document.title, aiByokSettings.sanitized().ttsSpeakerId)
                )
            )
        )
    }
    var pdfTtsJob by remember(document.path) { mutableStateOf<Job?>(null) }
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
    val pdfThemeStyle = remember(pdfReaderSettings, displayMode) {
        pdfReaderSettings.toDesktopPdfThemeStyle(displayMode)
    }
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
    val activePdfTtsChunk = pdfExtrasState.cloudTts.progress.currentChunk

    fun currentPdfTtsCacheSummary() =
        ttsAdapter.cacheSummary(document.title, aiByokSettings.sanitized().ttsSpeakerId)

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    LaunchedEffect(aiByokSettings) {
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = pdfExtrasState.cloudTts.copy(
                isAvailable = aiByokSettings.isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
    }

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
        openExternalUrl(externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, selection.text))
    }

    fun openPdfExternalLookup(action: ReaderExternalLookupAction, text: String) {
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
        val startPageIndex = pageIndex
        logDesktopTts(
            "pdf_sequence_toggle scope=${readScope.name} startPage=${startPageIndex + 1} " +
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
                    pdfTtsChunksForScope(readScope, startPageIndex)
                        .filter { it.text.isNotBlank() }
                        .withTtsReplacements(state.readerTtsReplacementPreferences, document.path)
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
                if (error is kotlinx.coroutines.CancellationException) {
                    pdfExtrasState = pdfExtrasState.copy(
                        cloudTts = pdfCloudTtsStoppedState(statusMessage = "Stopped")
                    )
                } else {
                    pdfExtrasState = pdfExtrasState.copy(
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
        ).withTtsReplacements(state.readerTtsReplacementPreferences, document.path)
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

    val pdfWorkspaceModel = pdfReaderWorkspaceModel(
        state = pdfState,
        displayMode = displayMode,
        hasContents = document.toc.isNotEmpty(),
        hasBookmarks = bookmarks.isNotEmpty(),
        hasAnnotations = sortedAnnotations.isNotEmpty(),
        hasEmbeddedComments = sortedEmbeddedAnnotations.isNotEmpty(),
        searchActive = searchQuery.isNotBlank(),
        annotationEditing = activeTextDraft != null ||
            selectedAnnotation != null ||
            selectedTool != PdfInkTool.PEN ||
            !isTextSelectionMode,
        richTextEditing = isRichTextMode,
        loading = isRendering || isSearchIndexing,
        errorMessage = renderError,
        extrasState = pdfExtrasState,
        aiAvailable = aiByokSettings.sanitized().areReaderAiFeaturesAvailable
    )

    fun handlePdfReaderKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isEditingTextAnnotation =
            activeTextDraft != null ||
                (selectedTool == PdfInkTool.TEXT && selectedAnnotation?.kind == PdfAnnotationKind.TEXT)
        if ((isEditingTextAnnotation || isRichTextMode) && !event.isCtrlPressed) {
            return false
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

    @Composable
    fun PdfNavigationSidebar() {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            LazyColumn(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { goToPage(pageIndex - 1) }, enabled = canGoPrevious) {
                            Text("Previous")
                        }
                        TextButton(onClick = { goToPage(pageIndex + 1) }, enabled = canGoNext) {
                            Text("Next")
                        }
                    }
                }
                if (document.pageCount > 1) {
                    item {
                        Text(
                            "Page ${pageIndex + 1} of ${document.pageCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    DesktopPdfJumpHistoryControls(
                        backPage = jumpHistory.backPage,
                        forwardPage = jumpHistory.forwardPage,
                        onBack = ::goBackInJumpHistory,
                        onForward = ::goForwardInJumpHistory,
                        onClear = { jumpHistory = jumpHistory.clear() }
                    )
                }
                item {
                    val isBookmarked = bookmarks.any { it.pageIndex == pageIndex }
                    TextButton(onClick = { toggleBookmark(pageIndex) }) {
                        Text(if (isBookmarked) "Remove bookmark" else "Bookmark page")
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { dispatchPdf(SharedPdfReaderAction.SearchChanged(it)) },
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
                    }
                    items(searchResults, key = { "nav_search_${it.pageIndex}_${it.matchIndex}_${it.preview}" }) { result ->
                        Surface(
                            color = if (result.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                goToSearchResult(searchResults.indexOf(result))
                            }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Page ${result.pageIndex + 1}", fontWeight = FontWeight.SemiBold)
                                Text(result.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (document.toc.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    itemsIndexed(document.toc, key = { index, entry -> "nav_toc_${index}_${entry.pageIndex}_${entry.nestLevel}" }) { _, entry ->
                        Surface(
                            color = if (entry.pageIndex == pageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { goToPage(entry.pageIndex, recordJump = true) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(start = (entry.nestLevel * 12).dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("p. ${entry.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (bookmarks.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
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
                if (sortedAnnotations.isNotEmpty() || sortedEmbeddedAnnotations.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(sortedAnnotations, key = { "nav_annotation_${it.id}" }) { annotation ->
                        Surface(
                            color = if (annotation.id == selectedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectAnnotation(annotation) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(annotation.desktopLabel(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Page ${annotation.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(sortedEmbeddedAnnotations, key = { "nav_embedded_${it.id}" }) { annotation ->
                        Surface(
                            color = if (annotation.id == selectedEmbeddedAnnotationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectEmbeddedAnnotation(annotation) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(annotation.author.ifBlank { "PDF comment" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Page ${annotation.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PdfBottomChrome() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { goToPage(pageIndex - 1) }, enabled = canGoPrevious) {
                    Text("Previous")
                }
                Text("Page ${pageIndex + 1} of ${document.pageCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (document.pageCount > 1) {
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
                        steps = (document.pageCount - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text("${progressPercent.toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { goToPage(pageIndex + 1) }, enabled = canGoNext) {
                    Text("Next")
                }
            }
        }
    }

    ReaderWorkspaceShell(
        model = pdfWorkspaceModel,
        title = document.title,
        subtitle = "${document.formatLabel} - Page ${pageIndex + 1} of ${document.pageCount}",
        progressLabel = "${progressPercent.toInt()}%",
        modifier = Modifier
            .onPreviewKeyEvent(::handlePdfReaderKeyEvent)
            .focusable(),
        topActions = {
            TextButton(onClick = onOpenBook) {
                Text("Open Book")
            }
            TextButton(onClick = onOpenPdf) {
                Text("Open PDF")
            }
        },
        leftSidebar = { PdfNavigationSidebar() },
        rightInspector = {
            Surface(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SharedReaderThemeControls(
                            settings = pdfReaderSettings,
                            builtInThemes = BuiltInPdfReaderThemes,
                            customTextureIds = customTextureIds,
                            onImportTexture = onImportTexture,
                            onSettingsChange = ::updatePdfReaderSettings
                        )
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
                        DesktopPdfExtrasPanel(
                            pageText = currentPdfPageText(),
                            recapText = pdfTextBeforeCurrentPage(),
                            extrasState = pdfExtrasState,
                            aiByokSettings = aiByokSettings,
                            onExternalLookup = ::openPdfExternalLookup,
                            onAiAction = ::runPdfAiAction,
                            onCloudTtsStart = ::startPdfCloudTts,
                            onCloudTtsPauseResume = ::pauseResumePdfCloudTts,
                            onCloudTtsStop = ::stopPdfCloudTts,
                            onCloudTtsClearCache = ::clearPdfCloudTtsCache,
                            onAutoScrollChange = ::updatePdfAutoScroll,
                            ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                            ttsReplacementBookId = document.path,
                            onTtsReplacementPreferencesChange = { preferences ->
                                updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                            }
                        )
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
        },
        bottomBar = { PdfBottomChrome() }
    ) {
        SharedPdfRichTextHiddenInput(
            controller = richTextController,
            enabled = isRichTextMode,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
                .zIndex(10f)
        )
            if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pdfThemeStyle.viewerBackgroundColor, RoundedCornerShape(8.dp))
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
                                activeTtsChunk = activePdfTtsChunk,
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
                                readerAiFeaturesAvailable = aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
                                cloudTtsAvailable = aiByokSettings.sanitized().isCloudTtsAvailable,
                                themeStyle = pdfThemeStyle,
                                shouldRender = verticalPageIndex in verticalRenderWindow,
                                onSelectPage = {
                                    goToPage(
                                        target = it,
                                        scrollVertical = false,
                                        saveRichTextBeforePageChange = !isRichTextMode
                                    )
                                },
                                onCopySelection = ::copySelection,
                                onHighlightSelection = ::highlightSelection,
                                onSearchSelection = ::searchSelection,
                                onWebSearchSelection = { openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, it.text) },
                                onDictionarySelection = { openPdfExternalLookup(ReaderExternalLookupAction.DICTIONARY, it.text) },
                                onDefineSelection = { runPdfAiAction(ReaderAiFeature.DEFINE, it.text) },
                                onSpeakSelection = { togglePdfCloudTts(it.text) },
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
                        .fillMaxSize()
                        .background(pdfThemeStyle.viewerBackgroundColor, RoundedCornerShape(8.dp))
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
                            DesktopPdfThemedPageImage(
                                bitmap = pageRender.image,
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
                                onWebSearch = {
                                    textSelection?.let { openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, it.text) }
                                    selectionMenuOffset = null
                                },
                                onDictionary = {
                                    textSelection?.let { openPdfExternalLookup(ReaderExternalLookupAction.DICTIONARY, it.text) }
                                    selectionMenuOffset = null
                                },
                                onDefine = {
                                    textSelection?.let { runPdfAiAction(ReaderAiFeature.DEFINE, it.text) }
                                    selectionMenuOffset = null
                                },
                                onSpeak = {
                                    textSelection?.let { togglePdfCloudTts(it.text) }
                                    selectionMenuOffset = null
                                },
                                onTranslate = {
                                    textSelection?.let(::translateSelection)
                                    selectionMenuOffset = null
                                },
                                showDefine = aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
                                showSpeak = aiByokSettings.sanitized().isCloudTtsAvailable,
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

@Composable
private fun DesktopAiByokSettingsDialog(
    settings: ReaderAiByokSettings,
    secureStorageAvailable: Boolean,
    onSettingsChange: (ReaderAiByokSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sanitized = settings.sanitized()
    var selectedProvider by remember { mutableStateOf("gemini") }
    var pendingKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI keys and models") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!secureStorageAvailable) {
                    Text(
                        "Secure key storage is unavailable on this operating system. Keys entered here will be used for this session but will not be persisted.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Saved keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DesktopSavedAiKeyRow(
                    label = "Gemini",
                    keyValue = sanitized.geminiKey,
                    onClear = { onSettingsChange(sanitized.copy(geminiKey = "", ttsModel = "")) }
                )
                DesktopSavedAiKeyRow(
                    label = "Groq",
                    keyValue = sanitized.groqKey,
                    onClear = { onSettingsChange(sanitized.copy(groqKey = "")) }
                )

                HorizontalDivider()

                Text("Add or replace key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("gemini" to "Gemini", "groq" to "Groq").forEach { (provider, label) ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = pendingKey,
                    onValueChange = { pendingKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = pendingKey.isNotBlank(),
                    onClick = {
                        val trimmed = pendingKey.trim()
                        val next = when (selectedProvider) {
                            "gemini" -> sanitized.copy(
                                geminiKey = trimmed,
                                ttsModel = sanitized.ttsModel.ifBlank { GEMINI_CLOUD_TTS_MODEL_ID }
                            )
                            "groq" -> sanitized.copy(groqKey = trimmed)
                            else -> sanitized
                        }
                        onSettingsChange(next)
                        pendingKey = ""
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save key")
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show AI in reader", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Matches the Android hide toggle for smart dictionary, summaries, and recaps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !sanitized.hideReaderAiFeatures,
                        onCheckedChange = { enabled ->
                            onSettingsChange(sanitized.copy(hideReaderAiFeatures = !enabled))
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use one model for all features", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Turn this off to choose separate models per reader AI feature.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sanitized.useOneModel,
                        onCheckedChange = { onSettingsChange(sanitized.copy(useOneModel = it)) }
                    )
                }

                if (sanitized.useOneModel) {
                    DesktopAiModelSelector(
                        title = "All AI features",
                        description = "Smart dictionary, summaries, and recaps all use this model.",
                        selectedId = sanitized.modelForAll,
                        onSelected = { onSettingsChange(sanitized.copy(modelForAll = it)) }
                    )
                } else {
                    DesktopAiModelSelector(
                        title = "Smart dictionary",
                        description = "Used when defining selected words or phrases.",
                        selectedId = sanitized.defineModel,
                        onSelected = { onSettingsChange(sanitized.copy(defineModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = "Summaries",
                        description = "Used for EPUB summaries and PDF page summaries.",
                        selectedId = sanitized.summarizeModel,
                        onSelected = { onSettingsChange(sanitized.copy(summarizeModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = "Recaps",
                        description = "Used for story recap generation.",
                        selectedId = sanitized.recapModel,
                        onSelected = { onSettingsChange(sanitized.copy(recapModel = it)) }
                    )
                }

                DesktopAiModelSelector(
                    title = "Cloud TTS",
                    description = "Uses the saved Gemini key. Only $GEMINI_CLOUD_TTS_MODEL is supported for now.",
                    selectedId = sanitized.ttsModel,
                    options = listOf(ReaderAiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                    onSelected = { onSettingsChange(sanitized.copy(ttsModel = it)) }
                )
                Text("Cloud TTS voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderCloudTtsVoices.chunked(3).forEach { rowVoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            rowVoices.forEach { voice ->
                                FilterChip(
                                    selected = sanitized.ttsSpeakerId == voice.id,
                                    onClick = { onSettingsChange(sanitized.copy(ttsSpeakerId = voice.id)) },
                                    label = {
                                        Column {
                                            Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                voice.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DesktopSavedAiKeyRow(
    label: String,
    keyValue: String,
    onClear: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                keyValue.takeIf { it.isNotBlank() }?.let(::maskedReaderAiKey) ?: "No key saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(enabled = keyValue.isNotBlank(), onClick = onClear) {
            Text("Clear")
        }
    }
}

@Composable
private fun DesktopAiModelSelector(
    title: String,
    description: String,
    selectedId: String,
    options: List<ReaderAiModelOption> = ReaderAiModelOptions,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = selectedId.isBlank(),
                onClick = { onSelected("") },
                label = { Text("No model") }
            )
            options.forEach { option ->
                FilterChip(
                    selected = selectedId == option.id,
                    onClick = { onSelected(option.id) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun DesktopPdfExtrasPanel(
    pageText: String,
    recapText: String,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val autoScroll = extrasState.autoScroll.sanitized()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Extras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ReaderExternalLookupAction.entries.forEach { action ->
                FilterChip(
                    selected = false,
                    enabled = pageText.isNotBlank(),
                    onClick = { onExternalLookup(action, pageText) },
                    label = { Text(action.title) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Auto scroll", modifier = Modifier.weight(1f))
            Switch(
                checked = autoScroll.enabled,
                onCheckedChange = { onAutoScrollChange(autoScroll.copy(enabled = it)) }
            )
        }
        Slider(
            value = autoScroll.speed,
            onValueChange = { onAutoScrollChange(autoScroll.copy(speed = it).sanitized()) },
            valueRange = 12f..160f
        )
        val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        extrasState.cloudTts.isLoading -> "Preparing audio"
                        extrasState.cloudTts.isPaused -> "Paused"
                        extrasState.cloudTts.isPlaying -> "Reading"
                        settings.isCloudTtsAvailable -> "Cloud TTS ready"
                        else -> "Cloud TTS needs Gemini"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                extrasState.cloudTts.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                val statusMessage = extrasState.cloudTts.progress.currentPositionLabel
                    ?: extrasState.cloudTts.statusMessage?.takeIf { it.isNotBlank() }
                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                enabled = settings.isCloudTtsAvailable || ttsBusy,
                onClick = {
                    if (ttsBusy) {
                        onCloudTtsStop()
                    } else {
                        onCloudTtsStart(ReaderTtsReadScope.BOOK)
                    }
                }
            ) {
                Text(if (ttsBusy) "Stop" else "Read")
            }
        }
        if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onCloudTtsPauseResume) {
                    Text(if (extrasState.cloudTts.isPaused) "Resume" else "Pause")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(
                enabled = settings.isCloudTtsAvailable && !ttsBusy && pageText.isNotBlank(),
                onClick = { onCloudTtsStart(ReaderTtsReadScope.PAGE) }
            ) {
                Text("Page")
            }
            TextButton(
                enabled = settings.isCloudTtsAvailable && !ttsBusy && pageText.isNotBlank(),
                onClick = { onCloudTtsStart(ReaderTtsReadScope.BOOK) }
            ) {
                Text("From here")
            }
        }
        val cacheSummary = extrasState.cloudTts.cacheSummary
        if (cacheSummary.hasCachedAudio) {
            Text(
                "Cache: ${cacheSummary.currentVoiceLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cacheSummary.hasCurrentVoiceCachedAudio) {
                TextButton(onClick = onCloudTtsClearCache) {
                    Text("Clear voice cache")
                }
            }
        }
        SharedReaderTtsReplacementControls(
            preferences = ttsReplacementPreferences,
            bookId = ttsReplacementBookId,
            onPreferencesChange = onTtsReplacementPreferencesChange
        )
        if (settings.areReaderAiFeaturesAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(
                    enabled = pageText.isNotBlank() && !extrasState.aiResult.isLoading,
                    onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, pageText) }
                ) {
                    Text("Summarize page")
                }
                TextButton(
                    enabled = recapText.isNotBlank() && !extrasState.aiResult.isLoading,
                    onClick = { onAiAction(ReaderAiFeature.RECAP, recapText) }
                ) {
                    Text("Recap")
                }
            }
            if (extrasState.aiResult.hasContent) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val aiErrorMessage = extrasState.aiResult.errorMessage
                        Text(extrasState.aiResult.title ?: "AI", fontWeight = FontWeight.SemiBold)
                        when {
                            extrasState.aiResult.isLoading -> Text("Working...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            aiErrorMessage != null -> Text(aiErrorMessage, color = MaterialTheme.colorScheme.error)
                            else -> SharedMarkdownText(extrasState.aiResult.text)
                        }
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
    activeTtsChunk: ReaderTtsChunk?,
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
    readerAiFeaturesAvailable: Boolean,
    cloudTtsAvailable: Boolean,
    themeStyle: DesktopPdfThemeStyle,
    shouldRender: Boolean,
    onSelectPage: (Int) -> Unit,
    onCopySelection: (DesktopPdfTextSelection) -> Unit,
    onHighlightSelection: (Int, DesktopPdfTextSelection, IntSize) -> Unit,
    onSearchSelection: (DesktopPdfTextSelection) -> Unit,
    onWebSearchSelection: (DesktopPdfTextSelection) -> Unit,
    onDictionarySelection: (DesktopPdfTextSelection) -> Unit,
    onDefineSelection: (DesktopPdfTextSelection) -> Unit,
    onSpeakSelection: (DesktopPdfTextSelection) -> Unit,
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
                        onWebSearch = {
                            textSelection?.let(onWebSearchSelection)
                            selectionMenuOffset = null
                        },
                        onDictionary = {
                            textSelection?.let(onDictionarySelection)
                            selectionMenuOffset = null
                        },
                        onDefine = {
                            textSelection?.let(onDefineSelection)
                            selectionMenuOffset = null
                        },
                        onSpeak = {
                            textSelection?.let(onSpeakSelection)
                            selectionMenuOffset = null
                        },
                        onTranslate = {
                            textSelection?.let(onTranslateSelection)
                            selectionMenuOffset = null
                        },
                        showDefine = readerAiFeaturesAvailable,
                        showSpeak = cloudTtsAvailable,
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
    onWebSearch: () -> Unit,
    onDictionary: () -> Unit,
    onDefine: () -> Unit,
    onSpeak: () -> Unit,
    onTranslate: () -> Unit,
    showDefine: Boolean,
    showSpeak: Boolean,
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
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCopy) { Text("Copy") }
            TextButton(onClick = onHighlight) { Text("Highlight") }
            if (showDefine) TextButton(onClick = onDefine) { Text("Define") }
            if (showSpeak) TextButton(onClick = onSpeak) { Text("Speak") }
            TextButton(onClick = onDictionary) { Text("Dict") }
            TextButton(onClick = onSearch) { Text("Find") }
            TextButton(onClick = onWebSearch) { Text("Web") }
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

private const val PdfSelectionMenuWidthPx = 620f
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
    onOpenBook: () -> Unit,
    onOpenPdf: () -> Unit,
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
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsToggle: (String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    readerTextureDataUri: (String) -> String?,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
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
        onOpenBook = onOpenBook,
        onOpenPdf = onOpenPdf,
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
        onExternalLookup = onExternalLookup,
        onAiAction = onAiAction,
        onCloudTtsStart = onCloudTtsStart,
        onCloudTtsPauseResume = onCloudTtsPauseResume,
        onCloudTtsStop = onCloudTtsStop,
        onCloudTtsClearCache = onCloudTtsClearCache,
        onAutoScrollChange = onAutoScrollChange,
        readerTextureDataUri = readerTextureDataUri,
        readerCustomTextureIds = readerCustomTextureIds,
        onImportReaderTexture = onImportReaderTexture
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
                    onSelectionAction = { action, text ->
                        val settings = aiByokSettings.sanitized()
                        when (action) {
                            DesktopReaderSelectionAction.DEFINE -> {
                                if (settings.areReaderAiFeaturesAvailable) onAiAction(ReaderAiFeature.DEFINE, text)
                            }
                            DesktopReaderSelectionAction.SPEAK -> {
                                if (settings.isCloudTtsAvailable) onCloudTtsToggle(text)
                            }
                            DesktopReaderSelectionAction.DICTIONARY -> onExternalLookup(ReaderExternalLookupAction.DICTIONARY, text)
                            DesktopReaderSelectionAction.TRANSLATE -> onExternalLookup(ReaderExternalLookupAction.TRANSLATE, text)
                            DesktopReaderSelectionAction.SEARCH -> onExternalLookup(ReaderExternalLookupAction.SEARCH, text)
                        }
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
    onSelectionAction: (DesktopReaderSelectionAction, String) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnHighlightCreated by rememberUpdatedState(onHighlightCreated)
    val latestOnSelectionAction by rememberUpdatedState(onSelectionAction)
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
        bridge.register(selectionActionHandler)
        bridge.register(ttsHighlightLogHandler)
        bridge.register(linkHandler)
        onDispose {
            bridge.unregister(highlightHandler)
            bridge.unregister(positionHandler)
            bridge.unregister(selectionActionHandler)
            bridge.unregister(ttsHighlightLogHandler)
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
                navigationTarget.autoScroll,
                navigationTarget.readingMode,
                state.loadingState
            ) {
                if (navigationTarget.readingMode != com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL) return@LaunchedEffect
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
                if (navigationTarget.readingMode != com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL) return@LaunchedEffect
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
                    val follow = navigationTarget.readingMode == com.aryan.reader.shared.reader.ReaderReadingMode.VERTICAL
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

private enum class DesktopReaderSelectionAction {
    DEFINE,
    SPEAK,
    DICTIONARY,
    TRANSLATE,
    SEARCH
}

private data class DesktopReaderSelectionActionPayload(
    val action: DesktopReaderSelectionAction,
    val text: String
)

private fun String.readerSelectionActionOrNull(): DesktopReaderSelectionActionPayload? {
    fun parse(rawJson: String): DesktopReaderSelectionActionPayload? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val text = obj["text"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val action = when (
            obj["action"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?.lowercase()
        ) {
            "define" -> DesktopReaderSelectionAction.DEFINE
            "speak" -> DesktopReaderSelectionAction.SPEAK
            "dictionary" -> DesktopReaderSelectionAction.DICTIONARY
            "translate" -> DesktopReaderSelectionAction.TRANSLATE
            "web-search", "search" -> DesktopReaderSelectionAction.SEARCH
            else -> return@runCatching null
        }
        DesktopReaderSelectionActionPayload(action, text)
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

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
            cfi?.let { add("\"cfi\":${it.toJsonStringLiteral()}") }
            textQuote?.let { add("\"textQuote\":${it.toJsonStringLiteral()}") }
        }
        append(values.joinToString(","))
        append("}")
    }
}

private fun String.toJsonStringLiteral(): String {
    val builder = StringBuilder("\"")
    forEach { char ->
        when (char) {
            '\\' -> builder.append("\\\\")
            '"' -> builder.append("\\\"")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            else -> {
                if (char.code < 0x20) {
                    builder.append("\\u")
                    builder.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(char)
                }
            }
        }
    }
    builder.append('"')
    return builder.toString()
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

private fun CustomFontItem.toDesktopPreviewFontFamily(): FontFamily? {
    return runCatching { FontFamily(DesktopFont(File(path))) }.getOrNull()
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

private fun chooseBookFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open Book", FileDialog.LOAD).apply {
        file = DesktopBookFileDialogPattern
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
        file = "*.ttf;*.otf;*.woff2"
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

private fun chooseReaderTextureFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose reader texture", FileDialog.LOAD).apply {
        file = "*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp"
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
private val DesktopSyncableFileTypes = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)
private val DesktopBookFileTypes = SharedFileCapabilities.all
    .filter { capability ->
        capability.type in DesktopReadableFileTypes && capability.type != FileType.PDF
    }
    .mapTo(mutableSetOf()) { it.type }
private val DesktopBookFileDialogPattern = SharedFileCapabilities.all
    .filter { it.type in DesktopBookFileTypes }
    .flatMap { capability -> capability.extensions.map { extension -> "*.$extension" } }
    .joinToString(";")

private const val EpistemeSourceUrl = "https://github.com/Aryan-Raj3112/episteme"
private const val EpistemeIssuesUrl = "https://github.com/Aryan-Raj3112/episteme/issues"
private const val EpistemeGitHubSponsorsUrl = "https://github.com/sponsors/Aryan-Raj3112"
private const val EpistemePatreonUrl = "https://www.patreon.com/c/epistemereader"
private const val EpistemeSupportEmail = "epistemereader@gmail.com"
private const val EpistemeFeedbackSubject = "Feedback: Episteme Reader"

private fun desktopAppVersionName(): String {
    return EpistemeDesktopAppVersion::class.java.getPackage()?.implementationVersion
        ?.let { "Version $it" }
        ?: "Desktop development build"
}

private object EpistemeDesktopAppVersion

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
            allowedFileTypes = DesktopSyncableFileTypes
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
            val desktop = Desktop.getDesktop()
            if (normalizedUrl.startsWith("mailto:", ignoreCase = true)) {
                desktop.mail(URI(normalizedUrl))
            } else {
                desktop.browse(URI(normalizedUrl))
            }
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
