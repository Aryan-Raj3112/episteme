package com.aryan.reader.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.AiAdapter
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.SaveMode
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.withTtsReplacements
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.PdfVisiblePageLayout
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfSearchEngine
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.mostVisiblePdfPageIndex
import com.aryan.reader.shared.pdf.pdfVerticalPageGapDp
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.toAnnotation
import com.aryan.reader.shared.pdf.withBounds
import com.aryan.reader.shared.pdf.withSharedPdfTextStyle
import com.aryan.reader.shared.pdf.withStyle
import com.aryan.reader.shared.pdf.withText
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.readerCloudTtsControlsModel
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.ReaderWorkspaceFileActionState
import com.aryan.reader.shared.ui.ReaderWorkspaceShell
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedPdfAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfEmbeddedAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfInlineTextEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfInteractionDock
import com.aryan.reader.shared.ui.SharedPdfPageNumberOverlay
import com.aryan.reader.shared.ui.SharedPdfRichTextHiddenInput
import com.aryan.reader.shared.ui.SharedPdfRichTextLayer
import com.aryan.reader.shared.ui.SharedPdfTextBoxEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfVerticalScrollbar
import com.aryan.reader.shared.ui.SharedReaderTtsOverlayControls
import com.aryan.reader.shared.ui.pdfReaderWorkspaceModel
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedPdfEmbeddedHitTest
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

private val DesktopPdfReaderFullscreenFocusRetryDelaysMillis = longArrayOf(80L, 120L, 160L, 240L)
private const val DesktopPdfPaginationPageTurnAnimationMillis = 140

private data class DesktopPdfPaginatedPageDisplay(
    val pageIndex: Int,
    val render: DesktopPdfPageRender
)

private data class DesktopPdfPendingPaginatedScrollRestore(
    val requestId: Int,
    val pageIndex: Int,
    val zoom: Float,
    val horizontalScroll: Int,
    val verticalScroll: Int
)

internal fun desktopPdfInitialPageIndex(
    requestedPageIndex: Int,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    settings: ReaderSettings
): Int {
    val clampedPageIndex = requestedPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    return if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.normalizePageIndex(clampedPageIndex, pageCount, settings)
    } else {
        clampedPageIndex
    }
}

@Composable
internal fun PdfReaderScreen(
    document: DesktopPdfDocument,
    initialPageIndex: Int,
    initialViewport: SharedPdfReaderViewport? = null,
    initialReaderSettings: ReaderSettings? = null,
    onReturnToLibrary: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
    appThemeControls: (@Composable () -> Unit)? = null,
    onPageStateChange: (pageIndex: Int, progress: Float, viewport: SharedPdfReaderViewport) -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit = {},
    pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    onPdfHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    customTextureIds: List<String> = emptyList(),
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    onLocalSidecarsChanged: () -> Unit = {},
    aiByokSettings: ReaderAiByokSettings,
    aiAdapter: AiAdapter,
    ttsAdapter: DesktopGeminiCloudTtsAdapter,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    summaryCacheStore: DesktopSummaryCacheStore = DesktopSummaryCacheStore(),
    credits: Int = 0,
    showPaidCredits: Boolean = false,
    onAiByokSettingsChange: (ReaderAiByokSettings) -> Unit = {},
    featurePolicy: SharedFeaturePolicy = SharedFeaturePolicy.Standard,
    cloudTtsControlsAvailable: Boolean = true,
    onReaderAiEntitlementRequired: (ReaderAiFeature, String) -> Boolean = { _, _ -> false },
    onCloudTtsEntitlementRequired: () -> Boolean = { false },
    onPaidFeatureError: (String?) -> Unit = {},
    hasReflowFile: Boolean = false,
    isReflowingThisBook: Boolean = false,
    onReflowAction: ((pageIndex: Int) -> Unit)? = null
) {
    val documentHandleId = document.handleId
    val stringResolver = LocalSharedStringResolver.current
    fun pdfString(name: String, fallback: String, vararg args: Any?): String {
        return stringResolver.string(name, fallback, *args)
    }
    val zoomSpec = remember { DesktopPdfZoomSpec }
    val initialDesktopPdfReaderSettings = remember(documentHandleId, initialReaderSettings) {
        initialReaderSettings.toDesktopPdfReaderSettings()
    }
    val initialPdfDisplayMode = initialDesktopPdfReaderSettings.toDesktopPdfDisplayMode()
    val restoredInitialViewport = remember(
        documentHandleId,
        initialViewport,
        initialDesktopPdfReaderSettings,
        initialPdfDisplayMode
    ) {
        initialViewport?.sanitized(document.pageCount, zoomSpec)?.let { viewport ->
            viewport.copy(
                pageIndex = desktopPdfInitialPageIndex(
                    requestedPageIndex = viewport.pageIndex,
                    pageCount = document.pageCount,
                    displayMode = initialPdfDisplayMode,
                    settings = initialDesktopPdfReaderSettings
                )
            )
        }
    }
    val initialPdfPageIndex = remember(
        documentHandleId,
        initialPageIndex,
        restoredInitialViewport,
        initialPdfDisplayMode,
        initialDesktopPdfReaderSettings
    ) {
        desktopPdfInitialPageIndex(
            requestedPageIndex = restoredInitialViewport?.pageIndex ?: initialPageIndex,
            pageCount = document.pageCount,
            displayMode = initialPdfDisplayMode,
            settings = initialDesktopPdfReaderSettings
        )
    }
    var pdfReaderSettings by remember(documentHandleId) {
        mutableStateOf(initialDesktopPdfReaderSettings)
    }
    var pdfState by remember(documentHandleId) {
        mutableStateOf(
            SharedPdfReaderState.initial(
                pageCount = document.pageCount,
                initialPageIndex = initialPdfPageIndex,
                zoomSpec = zoomSpec
            ).copy(
                displayMode = initialPdfDisplayMode,
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
    var pdfZoomSettleSequence by remember(documentHandleId) { mutableIntStateOf(0) }
    var pdfNavigationScrollRestoreSequence by remember(documentHandleId) { mutableIntStateOf(0) }
    var pendingPdfNavigationScrollRestore by remember(documentHandleId) {
        mutableStateOf<DesktopPdfPendingPaginatedScrollRestore?>(null)
    }
    var activeTextDraft by remember(documentHandleId) { mutableStateOf<SharedPdfTextDraft?>(null) }
    var textStyleConfig by remember(documentHandleId) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var pageCanvasSize by remember(documentHandleId) { mutableStateOf(IntSize.Zero) }
    var pdfZoomViewportRootOffset by remember(documentHandleId) { mutableStateOf(Offset.Zero) }
    var pdfZoomViewportSize by remember(documentHandleId) { mutableStateOf(IntSize.Zero) }
    var paginatedPageRootOffset by remember(documentHandleId) { mutableStateOf(Offset.Zero) }
    val paginatedPageRootOffsets = remember(documentHandleId) { mutableStateMapOf<Int, Offset>() }
    val paginatedPageCanvasSizes = remember(documentHandleId) { mutableStateMapOf<Int, IntSize>() }
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
                    isAvailable = cloudTtsControlsAvailable && aiByokSettings.isCloudTtsAvailable,
                    cacheSummary = ttsAdapter.cacheSummary(document.title, aiByokSettings.sanitized().ttsSpeakerId)
                )
            )
        )
    }
    var pdfTtsJob by remember(documentHandleId) { mutableStateOf<Job?>(null) }
    var showPdfAiHub by remember(documentHandleId) { mutableStateOf(false) }
    var pdfAiResultRequestId by remember(documentHandleId) { mutableStateOf(0L) }
    var dismissedPdfAiResultRequestId by remember(documentHandleId) { mutableStateOf<Long?>(null) }
    var pdfHubSummaryResult by remember(documentHandleId) { mutableStateOf<SummarizationResult?>(null) }
    var isPdfHubSummaryLoading by remember(documentHandleId) { mutableStateOf(false) }
    var isPdfTtsOverlayCollapsed by remember(documentHandleId) { mutableStateOf(false) }
    val annotationFile = remember(documentHandleId) { desktopPdfAnnotationFile(document.path) }
    val bookmarkFile = remember(documentHandleId) { desktopPdfBookmarkFile(document.path) }
    val richTextFile = remember(documentHandleId) { desktopPdfRichTextFile(document.path) }
    val searchIndexFile = remember(documentHandleId) { desktopPdfSearchIndexFile(document.path) }
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val pdfScope = rememberCoroutineScope()
    var isFullscreen by remember(documentHandleId) { mutableStateOf(false) }
    var showPdfSaveDialog by remember(documentHandleId) { mutableStateOf(false) }
    var isPdfFileActionLoading by remember(documentHandleId) { mutableStateOf(false) }
    var pdfFileActionNotice by remember(documentHandleId) { mutableStateOf<DesktopPdfFileActionNotice?>(null) }
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
    var pdfReaderFocusRestoreRequest by remember(documentHandleId) { mutableIntStateOf(0) }
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentPdfAnnotations by rememberUpdatedState(pdfState.annotations)
    val currentPdfPageIndex by rememberUpdatedState(pdfState.pageIndex)
    val currentPdfScale by rememberUpdatedState(pdfState.zoom)
    val currentPdfDisplayMode by rememberUpdatedState(pdfState.displayMode)
    val pdfSelectionSheetActive = pdfState.selectedAnnotationId?.let { selectedId ->
        pdfState.annotations.any { it.id == selectedId && it.isDesktopTextSelectionHighlight }
    } == true
    val shouldRestorePdfReaderFocus =
        !pdfState.isSearchActive &&
            !pdfSelectionSheetActive &&
            externalLinkDialogUrl == null &&
            !pdfExtrasState.aiResult.hasContent &&
            activeTextDraft == null &&
            !isRichTextMode &&
            (textSelection == null || selectionMenuOffset == null)
    val currentShouldRestorePdfReaderFocus by rememberUpdatedState(shouldRestorePdfReaderFocus)
    fun requestPdfReaderFocusRestore() {
        pdfReaderFocusRestoreRequest += 1
    }

    LaunchedEffect(isFullscreen, documentHandleId) {
        for (delayMillis in DesktopPdfReaderFullscreenFocusRetryDelaysMillis) {
            delay(delayMillis)
            if (currentShouldRestorePdfReaderFocus) {
                runCatching { pdfReaderFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(shouldRestorePdfReaderFocus, documentHandleId) {
        if (shouldRestorePdfReaderFocus) {
            delay(120L)
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
        val previousAnnotationIds = pdfState.annotations.mapTo(mutableSetOf()) { it.id }
        val next = pdfState.reduce(action, zoomSpec)
        val nextAnnotationIds = next.annotations.mapTo(mutableSetOf()) { it.id }
        val removedAnnotationIds = previousAnnotationIds - nextAnnotationIds
        if (removedAnnotationIds.isNotEmpty()) {
            DesktopCloudSidecarSync.recordAnnotationDeletions(
                documentPath = document.path,
                logBookId = documentHandleId.toString(),
                annotationIds = removedAnnotationIds
            )
        }
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
    }

    val pageIndex = pdfState.pageIndex
    val scale = pdfState.zoom
    val displayMode = pdfState.displayMode
    val rightToLeftPdfPaginationActive = displayMode == PdfDisplayMode.PAGINATION &&
        pdfReaderSettings.rightToLeftPagination
    val isPdfTwoPageSpread = displayMode == PdfDisplayMode.PAGINATION &&
        PdfSpreadLayout.isTwoPageSpreadEnabled(pdfReaderSettings)
    val paginatedSpreadPageIndices: List<Int> = remember(
        pageIndex,
        document.pageCount,
        displayMode,
        pdfReaderSettings.pageSpreadMode,
        pdfReaderSettings.pdfFirstPageStandaloneInSpread
    ) {
        if (displayMode == PdfDisplayMode.PAGINATION) {
            PdfSpreadLayout.visiblePageIndices(pageIndex, document.pageCount, pdfReaderSettings)
        } else {
            listOf(pageIndex.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)))
        }
    }
    val paginatedVisiblePageIndices = remember(
        paginatedSpreadPageIndices,
        rightToLeftPdfPaginationActive
    ) {
        if (rightToLeftPdfPaginationActive) paginatedSpreadPageIndices.asReversed() else paginatedSpreadPageIndices
    }
    val pdfPageLabel = desktopPdfPageLabel(pageIndex, document.pageCount, displayMode, pdfReaderSettings)
    val pdfPageScrubPreviewLabel = pageScrubPreview?.let {
        desktopPdfPageLabel(it, document.pageCount, displayMode, pdfReaderSettings)
    }
    val zoomControlScale = pdfZoomPreview?.zoom ?: scale
    val shouldShowPdfZoomIndicator = abs(zoomControlScale - 1f) > 0.001f

    LaunchedEffect(
        documentHandleId,
        displayMode,
        pdfReaderSettings.pageSpreadMode,
        pdfReaderSettings.pdfFirstPageStandaloneInSpread,
        pageIndex
    ) {
        if (displayMode != PdfDisplayMode.PAGINATION) return@LaunchedEffect
        val normalizedPage = PdfSpreadLayout.normalizePageIndex(pageIndex, document.pageCount, pdfReaderSettings)
        if (normalizedPage != pageIndex) {
            dispatchPdf(SharedPdfReaderAction.GoToPage(normalizedPage))
        }
    }

    LaunchedEffect(documentHandleId, pageIndex) {
        pdfHubSummaryResult = null
        isPdfHubSummaryLoading = false
    }

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

    LaunchedEffect(documentHandleId, displayMode) {
        if (!DesktopDiagnosticsEnabled) return@LaunchedEffect
        snapshotFlow {
            "mode=$displayMode page=${currentPdfPageIndex + 1} scale=${currentPdfScale.formatLogFloat()} " +
                "preview=${pdfZoomPreview != null} h=${pageHorizontalScrollState.value} " +
                "v=${pageVerticalScrollState.value} list=${verticalListState.firstVisibleItemIndex}:" +
                verticalListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { summary ->
                logPdfZoomSettle { "scroll_state seq=$pdfZoomSettleSequence $summary" }
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

    fun paginatedZoomPageRoot(page: Int?): Offset? {
        if (page == null) return null
        return paginatedPageRootOffsets[page]
            ?: paginatedPageRootOffset.takeIf { page == currentPdfPageIndex }
    }

    fun paginatedZoomAnchorPageIndex(anchor: Offset?): Int {
        val activePageIndex = currentPdfPageIndex
        if (!isPdfTwoPageSpread) return activePageIndex
        val rootOffsets = paginatedPageRootOffsets.toMutableMap()
        paginatedSpreadPageIndices.firstOrNull()?.let { firstSpreadPage ->
            rootOffsets.putIfAbsent(firstSpreadPage, paginatedPageRootOffset)
        }
        val pageSizes = paginatedPageCanvasSizes.toMutableMap()
        if (pageCanvasSize.width > 0 && pageCanvasSize.height > 0) {
            pageSizes.putIfAbsent(activePageIndex, pageCanvasSize)
        }
        return desktopPdfSpreadZoomAnchorPageIndex(
            viewportRootOffset = pdfZoomViewportRootOffset,
            anchor = anchor,
            visiblePageIndices = paginatedVisiblePageIndices,
            pageRootOffsets = rootOffsets,
            pageSizes = pageSizes,
            fallbackPageIndex = activePageIndex
        )
    }

    LaunchedEffect(scale, displayMode, pageIndex, isPdfTwoPageSpread, paginatedSpreadPageIndices) {
        val preview = pdfZoomPreview ?: return@LaunchedEffect
        val paginationPreviewPageVisible = if (isPdfTwoPageSpread) {
            preview.pageIndex in paginatedSpreadPageIndices
        } else {
            preview.pageIndex == pageIndex
        }
        if (
            preview.displayMode != displayMode ||
            (!paginationPreviewPageVisible && displayMode == PdfDisplayMode.PAGINATION) ||
            !desktopPdfZoomPreviewMatchesScale(preview, scale)
        ) {
            logPdfZoomSettle {
                "preview_cancel seq=$pdfZoomSettleSequence reason=state_mismatch mode=$displayMode " +
                    "page=${pageIndex + 1} scale=${scale.formatLogFloat()} previewMode=${preview.displayMode} " +
                    "previewPage=${preview.pageIndex?.plus(1) ?: "none"} base=${preview.baseZoom.formatLogFloat()} " +
                    "zoom=${preview.zoom.formatLogFloat()}"
            }
            pdfZoomPreview = null
            zoomCommitJob.getAndSet(null)?.cancel()
        }
    }

    fun applyAnchoredPdfZoom(oldZoom: Float, newZoom: Float, anchor: Offset?) {
        if (pdfZoomSettleSequence == 0) {
            pdfZoomSettleSequence = 1
        }
        val settleSequence = pdfZoomSettleSequence
        val activePageIndex = currentPdfPageIndex
        val activeDisplayMode = currentPdfDisplayMode
        logPdfZoomPerf {
            "commit_start mode=$activeDisplayMode page=${activePageIndex + 1} old=${oldZoom.formatLogFloat()} " +
                "new=${newZoom.formatLogFloat()} anchor=${anchor.formatLogOffset()} " +
                "renderPage=${renderedPageIndex?.let { it + 1 } ?: "none"} " +
                "renderScale=${renderedPageScale?.formatLogFloat() ?: "none"} " +
                "renderJobActive=${renderJob?.isActive == true}"
        }
        val committedPreview = pdfZoomPreview
        val viewportRootOffsetAtZoomStart = committedPreview?.viewportRootOffset ?: pdfZoomViewportRootOffset
        val committedPreviewPageIndex = committedPreview?.pageIndex ?: activePageIndex
        val pageRootOffsetAtZoomStart = committedPreview?.pageRootOffset
            ?: paginatedZoomPageRoot(committedPreviewPageIndex)
            ?: paginatedPageRootOffset
        val pageRootOffsetAtCommitStart = paginatedZoomPageRoot(committedPreviewPageIndex)
            ?: paginatedPageRootOffset
        logPdfZoomSettle {
            "commit_start seq=$settleSequence mode=$activeDisplayMode page=${activePageIndex + 1} " +
                "old=${oldZoom.formatLogFloat()} new=${newZoom.formatLogFloat()} anchor=${anchor.formatLogOffset()} " +
                "preview=${committedPreview != null} previewPage=${committedPreview?.pageIndex?.plus(1) ?: "none"} " +
                "previewBase=${committedPreview?.baseZoom?.formatLogFloat() ?: "none"} " +
                "previewZoom=${committedPreview?.zoom?.formatLogFloat() ?: "none"} " +
                "viewportStart=${viewportRootOffsetAtZoomStart.formatLogOffset()} " +
                "pageStart=${pageRootOffsetAtZoomStart.formatLogOffset()} " +
                "pageNow=${pageRootOffsetAtCommitStart.formatLogOffset()} h=${pageHorizontalScrollState.value} " +
                "v=${pageVerticalScrollState.value} list=${verticalListState.firstVisibleItemIndex}:" +
                "${verticalListState.firstVisibleItemScrollOffset} renderPage=${renderedPageIndex?.plus(1) ?: "none"} " +
                "renderScale=${renderedPageScale?.formatLogFloat() ?: "none"} renderJob=${renderJob?.isActive == true}"
        }
        val rawTargetHorizontalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(pageHorizontalScrollState.value, it.x, oldZoom, newZoom)
        }
        val rawTargetVerticalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(pageVerticalScrollState.value, it.y, oldZoom, newZoom)
        }
        val paginationCommitPrediction: DesktopPdfLayoutScrollPrediction? = if (activeDisplayMode == PdfDisplayMode.PAGINATION) {
            val predictedScale = zoomSpec.clamp(newZoom)
            if (isPdfTwoPageSpread) {
                val predictedSizes = paginatedVisiblePageIndices.mapNotNull { visiblePageIndex ->
                    document.pageSizes.getOrNull(visiblePageIndex)?.let { pageSize ->
                        visiblePageIndex to IntSize(
                            width = (pageSize.width * predictedScale).roundToInt().coerceAtLeast(1),
                            height = (pageSize.height * predictedScale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }.toMap()
                desktopPdfSpreadLayoutPrediction(
                    viewportRootOffset = pdfZoomViewportRootOffset,
                    viewportSize = pdfZoomViewportSize,
                    visiblePageIndices = paginatedVisiblePageIndices,
                    pageCanvasSizes = predictedSizes,
                    horizontalScroll = rawTargetHorizontalScroll ?: pageHorizontalScrollState.value,
                    verticalScroll = rawTargetVerticalScroll ?: pageVerticalScrollState.value,
                    paddingPx = with(density) { 24.dp.toPx() },
                    pageGapPx = with(density) {
                        desktopPdfSpreadPageGapDp(pdfReaderSettings.pdfVerticalPageGapVisible).toPx()
                    }
                )
            } else {
                document.pageSizes.getOrNull(committedPreviewPageIndex)?.let { pageSize ->
                    desktopPdfSinglePageLayoutPrediction(
                        viewportRootOffset = pdfZoomViewportRootOffset,
                        viewportSize = pdfZoomViewportSize,
                        pageCanvasSize = IntSize(
                            width = (pageSize.width * predictedScale).roundToInt().coerceAtLeast(1),
                            height = (pageSize.height * predictedScale).roundToInt().coerceAtLeast(1)
                        ),
                        horizontalScroll = rawTargetHorizontalScroll ?: pageHorizontalScrollState.value,
                        verticalScroll = rawTargetVerticalScroll ?: pageVerticalScrollState.value,
                        paddingPx = with(density) { 24.dp.toPx() }
                    )
                }
            }
        } else {
            null
        }
        val targetHorizontalScroll = rawTargetHorizontalScroll?.let { target ->
            paginationCommitPrediction?.maxHorizontalScroll?.let { maxScroll ->
                target.coerceIn(0, maxScroll)
            } ?: target
        }
        val targetVerticalScroll = rawTargetVerticalScroll?.let { target ->
            paginationCommitPrediction?.maxVerticalScroll?.let { maxScroll ->
                target.coerceIn(0, maxScroll)
            } ?: target
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
                    val pageRootOffset = if (committedPreview?.pageIndex == item.index) {
                        committedPreview.pageRootOffset
                    } else {
                        verticalPageRootOffsets[item.index]
                    }
                    Triple(item.index, fallbackOffset, pageRootOffset)
                }
        } else {
            null
        }
        logPdfZoomSettle {
            "commit_targets seq=$settleSequence mode=$activeDisplayMode targetH=${targetHorizontalScroll ?: "none"} " +
                "targetV=${targetVerticalScroll ?: "none"} rawH=${rawTargetHorizontalScroll ?: "none"} " +
                "rawV=${rawTargetVerticalScroll ?: "none"} predictedMaxH=${paginationCommitPrediction?.maxHorizontalScroll ?: "none"} " +
                "predictedMaxV=${paginationCommitPrediction?.maxVerticalScroll ?: "none"} " +
                "targetItem=${targetVerticalItem?.first?.plus(1) ?: "none"} " +
                "targetItemOffset=${targetVerticalItem?.second ?: "none"} targetItemRoot=${targetVerticalItem?.third.formatLogOffset()}"
        }
        var committedPreviewForClear = committedPreview
        committedPreview?.let { preview ->
            val previewWithCommitTargets = preview.copy(
                commitTargetHorizontalScroll = targetHorizontalScroll,
                commitTargetVerticalScroll = targetVerticalScroll.takeIf {
                    activeDisplayMode == PdfDisplayMode.PAGINATION
                }
            )
            if (pdfZoomPreview == preview) {
                pdfZoomPreview = previewWithCommitTargets
                committedPreviewForClear = previewWithCommitTargets
                logPdfZoomSettle {
                    "preview_commit_targets seq=$settleSequence targetH=${targetHorizontalScroll ?: "none"} " +
                        "targetV=${targetVerticalScroll ?: "none"}"
                }
            }
        }
        dispatchPdf(SharedPdfReaderAction.ZoomChanged(newZoom))
        fun clearCommittedPreview() {
            val matchesCommittedPreview = pdfZoomPreview == committedPreviewForClear
            logPdfZoomSettle {
                "preview_clear seq=$settleSequence match=$matchesCommittedPreview " +
                    "current=${pdfZoomPreview != null} committed=${committedPreview != null}"
            }
            if (matchesCommittedPreview) {
                pdfZoomPreview = null
            }
        }
        logPdfZoomSettle {
            "zoom_dispatched seq=$settleSequence new=${newZoom.formatLogFloat()} h=${pageHorizontalScrollState.value} " +
                "v=${pageVerticalScrollState.value} list=${verticalListState.firstVisibleItemIndex}:" +
                verticalListState.firstVisibleItemScrollOffset
        }
        if (anchor != null) {
            zoomAnchorJob.getAndSet(null)?.cancel()
            val nextAnchorJob = pdfScope.launch(start = CoroutineStart.UNDISPATCHED) {
                when (activeDisplayMode) {
                    PdfDisplayMode.PAGINATION -> {
                        if (targetHorizontalScroll != null || targetVerticalScroll != null) {
                            val beforeH = pageHorizontalScrollState.value
                            val beforeV = pageVerticalScrollState.value
                            targetHorizontalScroll?.let { pageHorizontalScrollState.scrollTo(it) }
                            targetVerticalScroll?.let { pageVerticalScrollState.scrollTo(it) }
                            logPdfZoomSettle {
                                "anchor_pre_scroll seq=$settleSequence mode=pagination beforeH=$beforeH beforeV=$beforeV " +
                                    "targetH=${targetHorizontalScroll ?: "none"} targetV=${targetVerticalScroll ?: "none"} " +
                                    "afterH=${pageHorizontalScrollState.value} afterV=${pageVerticalScrollState.value} " +
                                    "maxH=${pageHorizontalScrollState.maxValue} maxV=${pageVerticalScrollState.maxValue}"
                            }
                        }
                        withFrameNanos { }
                        suspend fun correctPageAnchor(pass: Int) {
                            val beforeH = pageHorizontalScrollState.value
                            val beforeV = pageVerticalScrollState.value
                            val currentRoot = paginatedZoomPageRoot(committedPreviewPageIndex)
                                ?: paginatedPageRootOffset
                            val pageDelta = desktopPdfAnchoredPageScrollDelta(
                                viewportRootOffset = viewportRootOffsetAtZoomStart,
                                oldPageRootOffset = pageRootOffsetAtZoomStart,
                                currentPageRootOffset = currentRoot,
                                anchor = anchor,
                                oldZoom = oldZoom,
                                newZoom = newZoom
                            )
                            val reachableDelta = pageDelta?.let {
                                desktopPdfReachableScrollDelta(
                                    requestedDelta = it,
                                    scrollBounds = DesktopPdfZoomScrollBounds(
                                        currentHorizontalScroll = pageHorizontalScrollState.value,
                                        maxHorizontalScroll = pageHorizontalScrollState.maxValue,
                                        currentVerticalScroll = pageVerticalScrollState.value,
                                        maxVerticalScroll = pageVerticalScrollState.maxValue
                                    )
                                )
                            }
                            logPdfZoomSettle {
                                "anchor_pass seq=$settleSequence pass=$pass mode=pagination beforeH=$beforeH " +
                                    "beforeV=$beforeV delta=${pageDelta.formatLogIntOffset()} " +
                                    "reachable=${reachableDelta.formatLogIntOffset()} " +
                                    "maxH=${pageHorizontalScrollState.maxValue} maxV=${pageVerticalScrollState.maxValue} " +
                                    "rootStart=${pageRootOffsetAtZoomStart.formatLogOffset()} " +
                                    "rootNow=${currentRoot.formatLogOffset()} viewport=${viewportRootOffsetAtZoomStart.formatLogOffset()}"
                            }
                            if (reachableDelta != null) {
                                if (abs(reachableDelta.x) > 1) {
                                    pageHorizontalScrollState.scrollTo(
                                        (pageHorizontalScrollState.value + reachableDelta.x).coerceAtLeast(
                                            0
                                        )
                                    )
                                }
                                if (abs(reachableDelta.y) > 1) {
                                    pageVerticalScrollState.scrollTo(
                                        (pageVerticalScrollState.value + reachableDelta.y).coerceAtLeast(
                                            0
                                        )
                                    )
                                }
                            } else if (targetHorizontalScroll != null && targetVerticalScroll != null) {
                                pageHorizontalScrollState.scrollTo(targetHorizontalScroll)
                                pageVerticalScrollState.scrollTo(targetVerticalScroll)
                            }
                            logPdfZoomSettle {
                                "anchor_pass_end seq=$settleSequence pass=$pass mode=pagination afterH=${pageHorizontalScrollState.value} " +
                                    "afterV=${pageVerticalScrollState.value} delta=${pageDelta.formatLogIntOffset()} " +
                                    "reachable=${reachableDelta.formatLogIntOffset()}"
                            }
                        }
                        correctPageAnchor(pass = 1)
                        withFrameNanos { }
                        correctPageAnchor(pass = 2)
                    }

                    PdfDisplayMode.VERTICAL_SCROLL -> {
                        withFrameNanos { }
                        suspend fun correctVerticalAnchor(pass: Int) {
                            val beforeH = pageHorizontalScrollState.value
                            val beforeItem = verticalListState.firstVisibleItemIndex
                            val beforeItemOffset = verticalListState.firstVisibleItemScrollOffset
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
                            logPdfZoomSettle {
                                "anchor_pass seq=$settleSequence pass=$pass mode=vertical beforeH=$beforeH " +
                                    "beforeList=$beforeItem:$beforeItemOffset delta=${pageDelta.formatLogIntOffset()} " +
                                    "targetItem=${targetVerticalItem?.first?.plus(1) ?: "none"} " +
                                    "oldRoot=${oldPageRootOffset.formatLogOffset()} " +
                                    "currentRoot=${currentPageRootOffset.formatLogOffset()} " +
                                    "viewport=${viewportRootOffsetAtZoomStart.formatLogOffset()}"
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
                            logPdfZoomSettle {
                                "anchor_pass_end seq=$settleSequence pass=$pass mode=vertical afterH=${pageHorizontalScrollState.value} " +
                                    "afterList=${verticalListState.firstVisibleItemIndex}:${verticalListState.firstVisibleItemScrollOffset} " +
                                    "delta=${pageDelta.formatLogIntOffset()}"
                            }
                        }
                        correctVerticalAnchor(pass = 1)
                        withFrameNanos { }
                        correctVerticalAnchor(pass = 2)
                    }
                }
                clearCommittedPreview()
            }
            zoomAnchorJob.set(nextAnchorJob)
        } else {
            val nextAnchorJob = pdfScope.launch {
                withFrameNanos { }
                logPdfZoomSettle {
                    "anchor_skip seq=$settleSequence reason=no_anchor mode=$activeDisplayMode h=${pageHorizontalScrollState.value} " +
                        "v=${pageVerticalScrollState.value} list=${verticalListState.firstVisibleItemIndex}:" +
                        verticalListState.firstVisibleItemScrollOffset
                }
                clearCommittedPreview()
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
        val previewPageIndex = when (activeDisplayMode) {
            PdfDisplayMode.PAGINATION -> paginatedZoomAnchorPageIndex(anchor)
            PdfDisplayMode.VERTICAL_SCROLL -> anchor?.let(::verticalZoomAnchorItem)?.index ?: activePageIndex
        }
        val existingPreview = pdfZoomPreview?.takeIf {
            it.displayMode == activeDisplayMode &&
                it.pageIndex == previewPageIndex &&
                it.baseZoom.isFinite() &&
                it.baseZoom > 0f &&
                abs(it.baseZoom - activeScale) <= 0.0001f
        }
        if (existingPreview == null && currentShouldRestorePdfReaderFocus) {
            runCatching { pdfReaderFocusRequester.requestFocus() }
        }
        if (existingPreview == null) {
            pdfZoomSettleSequence += 1
        }
        val settleSequence = pdfZoomSettleSequence
        val baseZoom = existingPreview
            ?.baseZoom
            ?: oldZoom.takeIf { it.isFinite() && it > 0f }
            ?: activeScale
        val previewPageRootOffset = existingPreview?.pageRootOffset ?: when (activeDisplayMode) {
            PdfDisplayMode.PAGINATION -> paginatedZoomPageRoot(previewPageIndex)
            PdfDisplayMode.VERTICAL_SCROLL -> verticalPageRootOffsets[previewPageIndex]
        }
        pdfZoomPreview = DesktopPdfZoomPreview(
            baseZoom = baseZoom,
            zoom = newZoom,
            anchor = anchor,
            displayMode = activeDisplayMode,
            pageIndex = previewPageIndex,
            viewportRootOffset = existingPreview?.viewportRootOffset ?: pdfZoomViewportRootOffset,
            pageRootOffset = previewPageRootOffset,
            diagnosticSequence = settleSequence
        )
        logPdfZoomSettle {
            "preview_update seq=$settleSequence mode=$activeDisplayMode page=${activePageIndex + 1} " +
                "previewPage=${previewPageIndex + 1} oldEvent=${oldZoom.formatLogFloat()} " +
                "activeScale=${activeScale.formatLogFloat()} base=${baseZoom.formatLogFloat()} " +
                "new=${newZoom.formatLogFloat()} anchor=${anchor.formatLogOffset()} existing=${existingPreview != null} " +
                "viewport=${pdfZoomViewportRootOffset.formatLogOffset()} pageRoot=${previewPageRootOffset.formatLogOffset()} " +
                "h=${pageHorizontalScrollState.value} v=${pageVerticalScrollState.value} " +
                "list=${verticalListState.firstVisibleItemIndex}:${verticalListState.firstVisibleItemScrollOffset} " +
                "renderPage=${renderedPageIndex?.plus(1) ?: "none"} renderScale=${renderedPageScale?.formatLogFloat() ?: "none"}"
        }
        val nextCommitJob = pdfScope.launch {
            delay(DesktopPdfZoomCommitDebounceMillis)
            val preview = pdfZoomPreview ?: return@launch
            logPdfZoomSettle {
                "commit_debounce_fire seq=$settleSequence base=${preview.baseZoom.formatLogFloat()} " +
                    "zoom=${preview.zoom.formatLogFloat()} page=${preview.pageIndex?.plus(1) ?: "none"} " +
                    "anchor=${preview.anchor.formatLogOffset()}"
            }
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
        logPdfZoomSettle {
            "preview_cancel seq=$pdfZoomSettleSequence reason=explicit pending=${pdfZoomPreview != null}"
        }
        pdfZoomPreview = null
        zoomCommitJob.getAndSet(null)?.cancel()
    }

    fun commitPendingPdfZoomPreviewForNavigation(targetPageIndex: Int) {
        val snapshot = desktopPdfNavigationZoomSnapshot(
            preview = pdfZoomPreview,
            currentHorizontalScroll = pageHorizontalScrollState.value,
            currentVerticalScroll = pageVerticalScrollState.value
        ) ?: return
        val committedZoom = zoomSpec.clamp(snapshot.zoom)
        logPdfZoomSettle {
            "preview_navigation_commit seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                "target=${targetPageIndex + 1} zoom=${committedZoom.formatLogFloat()} " +
                "h=${snapshot.horizontalScroll} v=${snapshot.verticalScroll}"
        }
        zoomCommitJob.getAndSet(null)?.cancel()
        zoomAnchorJob.getAndSet(null)?.cancel()
        pdfZoomPreview = null
        dispatchPdf(SharedPdfReaderAction.ZoomChanged(committedZoom))
        if (displayMode == PdfDisplayMode.PAGINATION) {
            pdfNavigationScrollRestoreSequence += 1
            pendingPdfNavigationScrollRestore = DesktopPdfPendingPaginatedScrollRestore(
                requestId = pdfNavigationScrollRestoreSequence,
                pageIndex = targetPageIndex.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)),
                zoom = committedZoom,
                horizontalScroll = snapshot.horizontalScroll,
                verticalScroll = snapshot.verticalScroll
            )
        } else {
            pdfScope.launch {
                pageHorizontalScrollState.scrollTo(snapshot.horizontalScroll)
            }
        }
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
        logPdfZoomSettle {
            "cache_put seq=$pdfZoomSettleSequence page=${page + 1} scale=${renderScale.formatLogFloat()} " +
                "bitmap=${render.width}x${render.height} current=${activePageIndex + 1} " +
                "keys=${paginatedRenderCache.keys.sorted().map { it + 1 }} evicted=${evictedPages.map { it + 1 }}"
        }
    }

    LaunchedEffect(documentHandleId, pageIndex, displayMode, scale) {
        if (currentShouldRestorePdfReaderFocus) {
            runCatching { pdfReaderFocusRequester.requestFocus() }
        }
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
    val canGoPrevious = if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.canGoPrevious(pageIndex, document.pageCount, pdfReaderSettings)
    } else {
        pdfState.canGoPrevious
    }
    val canGoNext = if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.canGoNext(pageIndex, document.pageCount, pdfReaderSettings)
    } else {
        pdfState.canGoNext
    }
    val progressPercent = if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.progressPercent(pageIndex, document.pageCount, pdfReaderSettings)
    } else {
        pdfState.progressPercent
    }
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
        return if (displayMode == PdfDisplayMode.PAGINATION) {
            PdfSpreadLayout.progressPercent(pageIndex, document.pageCount, pdfReaderSettings)
        } else {
            ((pageIndex + 1).toFloat() / document.pageCount.coerceAtLeast(1)) * 100f
        }
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
    val sortedSidebarHighlights = remember(annotations) {
        desktopPdfSidebarHighlights(annotations)
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
    val localPdfFile = remember(document.path, document.formatLabel) {
        File(document.path).takeIf { document.formatLabel == "PDF" && it.isFile }
    }
    val pdfFileActions = remember(localPdfFile, hasReflowFile, isReflowingThisBook, onReflowAction) {
        ReaderWorkspaceFileActionState(
            canSaveCopy = localPdfFile != null,
            canPrint = localPdfFile != null,
            canGenerateTextView = localPdfFile != null && onReflowAction != null,
            hasGeneratedTextView = hasReflowFile,
            isGeneratingTextView = isReflowingThisBook
        )
    }
    val sidecarsReadyForExport = arePdfAnnotationsLoaded && isRichTextLoaded
    val annotationsForExportChoice = remember(annotations, activeTextDraft) {
        val draftAnnotation = activeTextDraft
            ?.toAnnotation()
            ?.takeIf { it.text.isNotBlank() }
        if (draftAnnotation == null) annotations else annotations + draftAnnotation
    }
    val shouldShowAnnotationExportChoice = shouldShowDesktopPdfAnnotationExportChoice(
        sidecarsReady = sidecarsReadyForExport,
        annotations = annotationsForExportChoice,
        richTextPageLayouts = richTextController.pageLayouts
    )

    fun runPdfFileAction(
        successTitle: String,
        action: suspend () -> String
    ) {
        if (isPdfFileActionLoading) return
        pdfScope.launch {
            isPdfFileActionLoading = true
            try {
                val message = action()
                pdfFileActionNotice = DesktopPdfFileActionNotice(
                    title = successTitle,
                    message = message
                )
            } catch (error: Throwable) {
                pdfFileActionNotice = DesktopPdfFileActionNotice(
                    title = pdfString("desktop_pdf_action_failed", "PDF action failed"),
                    message = error.message ?: pdfString(
                        "desktop_pdf_action_failed_desc",
                        "The PDF action could not be completed."
                    ),
                    isError = true
                )
            } finally {
                isPdfFileActionLoading = false
            }
        }
    }

    suspend fun preparePdfAnnotationExport() {
        commitActiveTextDraft()
        if (isRichTextMode) {
            isRichTextMode = false
        }
        richTextController.saveImmediate()
    }

    fun savePdfCopy(mode: SaveMode) {
        val target = chooseSavePdfFile(
            desktopSuggestedPdfFilename(
                originalName = localPdfFile?.name ?: document.title,
                isAnnotated = mode == SaveMode.ANNOTATED
            )
        ) ?: return
        runPdfFileAction(successTitle = pdfString("desktop_pdf_saved", "PDF saved")) {
            if (mode == SaveMode.ANNOTATED) {
                preparePdfAnnotationExport()
            }
            val annotationSnapshot = pdfState.annotations
            val richTextSnapshot = richTextController.pageLayouts
            withContext(Dispatchers.IO) {
                saveDesktopPdfCopy(
                    document = document,
                    target = target,
                    mode = mode,
                    annotations = annotationSnapshot,
                    richTextPageLayouts = richTextSnapshot
                )
            }
            pdfString("desktop_saved_to_path_format", "Saved to %1\$s", target.absolutePath)
        }
    }

    val requestSaveCopy: () -> Unit = {
        if (shouldShowAnnotationExportChoice) {
            showPdfSaveDialog = true
        } else {
            savePdfCopy(SaveMode.ORIGINAL)
        }
    }
    val requestPrint: () -> Unit = {
        runPdfFileAction(successTitle = pdfString("action_print", "Print")) {
            withContext(Dispatchers.IO) {
                printDesktopPdfDocument(document)
            }
            pdfString("desktop_print_dialog_finished", "The print dialog has finished.")
        }
    }

    LaunchedEffect(selectedTool) {
        activeStroke = emptyList()
        eraserPosition = null
    }

    fun updatePdfHighlighterPalette(nextPalette: SharedPdfHighlighterPalette) {
        fun sameRgb(left: Int, right: Int): Boolean = (left and 0x00FFFFFF) == (right and 0x00FFFFFF)

        val previousSlot = pdfHighlighterPalette.sanitized().colors.indexOfFirst { sameRgb(it, selectedColor) }
        val sanitizedPalette = nextPalette.sanitized()
        onPdfHighlighterPaletteChange(sanitizedPalette)
        if (selectedTool.isDesktopHighlighter && sanitizedPalette.colors.none { sameRgb(it, selectedColor) }) {
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
            showPdfAiHub ||
            showPdfSaveDialog ||
            pdfFileActionNotice != null ||
            isPdfFileActionLoading ||
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

    LaunchedEffect(pdfReaderFocusRestoreRequest, documentHandleId) {
        if (pdfReaderFocusRestoreRequest > 0) {
            delay(140L)
            if (currentShouldRestorePdfReaderFocus && !pdfPopupActive) {
                runCatching { pdfReaderFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(aiByokSettings, cloudTtsControlsAvailable) {
        pdfExtrasState = pdfExtrasState.copy(
            cloudTts = pdfExtrasState.cloudTts.copy(
                isAvailable = cloudTtsControlsAvailable && aiByokSettings.isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
    }

    DesktopPdfAnnotationSidecarEffect(
        documentHandleId = documentHandleId,
        annotationFile = annotationFile,
        annotations = annotations,
        annotationsLoaded = arePdfAnnotationsLoaded,
        onAnnotationsLoadedChange = { arePdfAnnotationsLoaded = it },
        onAnnotationsLoaded = { loadedAnnotations ->
            dispatchPdf(SharedPdfReaderAction.AnnotationsLoaded(loadedAnnotations))
        },
        onLocalSidecarsChanged = onLocalSidecarsChanged
    )

    DesktopPdfRichTextSidecarEffect(
        documentHandleId = documentHandleId,
        richTextFile = richTextFile,
        richTextController = richTextController,
        onRichTextLoadedChange = { isRichTextLoaded = it }
    )

    DesktopPdfBookmarkSidecarEffect(
        documentHandleId = documentHandleId,
        bookmarkFile = bookmarkFile,
        bookmarks = bookmarks,
        bookmarksLoaded = arePdfBookmarksLoaded,
        onBookmarksLoadedChange = { arePdfBookmarksLoaded = it },
        onBookmarksLoaded = { loadedBookmarks ->
            dispatchPdf(SharedPdfReaderAction.BookmarksLoaded(loadedBookmarks))
        },
        onLocalSidecarsChanged = onLocalSidecarsChanged
    )

    DesktopPdfSearchIndexSidecarEffect(
        documentHandleId = documentHandleId,
        document = document,
        searchIndexFile = searchIndexFile,
        onIndexedSearchPageCountChange = { indexedSearchPageCount = it },
        onSearchIndexingChange = { isSearchIndexing = it }
    )

    DesktopPdfSearchResultsEffect(
        documentHandleId = documentHandleId,
        document = document,
        searchQuery = searchQuery,
        indexedSearchPageCount = indexedSearchPageCount,
        onSearchResultsChange = { searchResults = it }
    )

    fun goToPage(
        target: Int,
        scrollVertical: Boolean = true,
        recordJump: Boolean = false,
        saveRichTextBeforePageChange: Boolean = true,
        commitPendingZoomPreview: Boolean = true
    ) {
        val boundedTarget = target.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        val clampedTarget = if (displayMode == PdfDisplayMode.PAGINATION) {
            PdfSpreadLayout.normalizePageIndex(boundedTarget, document.pageCount, pdfReaderSettings)
        } else {
            boundedTarget
        }
        val currentPage = pdfState.pageIndex
        val selectingDifferentPageInSpread = displayMode == PdfDisplayMode.PAGINATION &&
            boundedTarget != currentPage
        SharedPdfRichTextLog.d(
            "desktop.goToPage target=$target clamped=$clampedTarget current=$currentPage " +
                "richMode=$isRichTextMode scrollVertical=$scrollVertical recordJump=$recordJump " +
                "saveRich=$saveRichTextBeforePageChange activePage=${richTextController.activePageIndex}"
        )
        if (clampedTarget != currentPage || selectingDifferentPageInSpread) {
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
        if (commitPendingZoomPreview) {
            commitPendingPdfZoomPreviewForNavigation(clampedTarget)
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
        val targetPage = desktopPdfPageScrubTarget(
            value = value,
            pageCount = document.pageCount,
            displayMode = displayMode,
            settings = pdfReaderSettings
        )
        pageScrubPreview = targetPage
    }

    fun finishPdfPageScrub() {
        val startPage = pageScrubStartPage
        val targetPage = desktopPdfPageScrubCommitTarget(
            previewPage = pageScrubPreview,
            currentPage = pdfState.pageIndex,
            pageCount = document.pageCount
        )
        pageScrubStartPage = null
        pageScrubPreview = null
        if (startPage != null) {
            jumpHistory = jumpHistory.record(
                currentPageIndex = startPage,
                targetPageIndex = targetPage,
                pageCount = document.pageCount
            )
        }
        goToPage(targetPage)
    }

    fun previousPdfPageTarget(): Int {
        return if (displayMode == PdfDisplayMode.PAGINATION) {
            PdfSpreadLayout.previousPageIndex(pageIndex, document.pageCount, pdfReaderSettings)
        } else {
            pageIndex - 1
        }
    }

    fun nextPdfPageTarget(): Int {
        return if (displayMode == PdfDisplayMode.PAGINATION) {
            PdfSpreadLayout.nextPageIndex(pageIndex, document.pageCount, pdfReaderSettings)
        } else {
            pageIndex + 1
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
        val annotation = SharedPdfAnnotation(
            id = "highlight_${now}",
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = highlightBounds.firstOrNull(),
            boundsList = highlightBounds,
            text = selection.text,
            colorArgb = SharedPdfHighlighterPalette(listOf(colorArgb)).sanitized().colors.first(),
            rangeStartIndex = selection.startIndex,
            rangeEndIndex = selection.endIndex,
            createdAt = now
        )
        pdfState = pdfState.withDesktopPdfTextSelectionHighlightAdded(annotation, zoomSpec)
        clearPdfInteractionState()
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
                chapterTitle = pdfString("pdf_page_short", "Page %1\$d", targetPage + 1)
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

    fun pdfCloudTtsStoppedState(statusMessage: String? = null, errorMessage: String? = null) = ReaderCloudTtsState(
        isAvailable = cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = currentPdfTtsCacheSummary()
    )

    fun pdfHubBookKey(): String {
        val path = document.path.trim()
        if (path.isNotBlank()) return path
        val title = document.title.trim()
        return if (title.isNotBlank()) title else document.handleId.toString()
    }

    fun pdfHubBookTitle(): String {
        val title = document.title.trim()
        if (title.isNotBlank()) return title
        val fileName = document.path.substringAfterLast('\\').substringAfterLast('/').trim()
        return if (fileName.isNotBlank()) fileName else "PDF"
    }

    fun clearPdfHubSummary() {
        pdfHubSummaryResult = null
        isPdfHubSummaryLoading = false
    }

    fun generatePdfHubSummary(force: Boolean) {
        val pageText = currentPdfPageText(16_000)
        val bookKey = pdfHubBookKey()
        val pageTitle = pdfString("pdf_page_short", "Page %1\$d", pageIndex + 1)
        if (pageText.isBlank()) {
            pdfHubSummaryResult = SummarizationResult(error = pdfString("desktop_no_text_to_summarize", "There is no text to summarize."))
            return
        }
        if (!force) {
            summaryCacheStore.getSummary(bookKey, pageIndex)?.let { cached ->
                pdfHubSummaryResult = SummarizationResult(summary = cached, isCacheHit = true)
                return
            }
        }
        if (onReaderAiEntitlementRequired(ReaderAiFeature.SUMMARIZE, pageText)) return
        isPdfHubSummaryLoading = true
        pdfHubSummaryResult = null
        pdfScope.launch {
            var streamedSummary = ""
            var streamedCost: Double? = null
            var streamedFreeRemaining: Int? = null
            fun updateStreamingSummary(error: String? = null) {
                pdfHubSummaryResult = SummarizationResult(
                    summary = streamedSummary.takeIf { it.isNotBlank() },
                    error = error,
                    cost = streamedCost,
                    freeRemaining = streamedFreeRemaining
                )
            }
            val result = aiAdapter.summarizeStreaming(
                text = pageText,
                onUsageReceived = { cost, freeRemaining ->
                    cost?.let { streamedCost = it }
                    freeRemaining?.let { streamedFreeRemaining = it }
                    updateStreamingSummary()
                },
                onUpdate = { chunk ->
                    streamedSummary += chunk
                    updateStreamingSummary()
                }
            )
            val finalSummary = result.summary?.takeIf { it.isNotBlank() } ?: streamedSummary.takeIf { it.isNotBlank() }
            finalSummary?.let { summary ->
                summaryCacheStore.saveSummary(bookKey, pageIndex, pageTitle, summary)
            }
            pdfHubSummaryResult = result.copy(summary = finalSummary)
            isPdfHubSummaryLoading = false
            onPaidFeatureError(result.error)
        }
    }

    fun isPdfAiResultVisible(requestId: Long): Boolean =
        pdfAiResultRequestId == requestId && dismissedPdfAiResultRequestId != requestId

    fun updatePdfAiResult(requestId: Long, aiResult: ReaderAiResultState) {
        if (isPdfAiResultVisible(requestId)) {
            pdfExtrasState = pdfExtrasState.copy(aiResult = aiResult)
        }
    }

    fun runPdfAiAction(feature: ReaderAiFeature, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        if (!aiByokSettings.sanitized().areReaderAiFeaturesAvailable) return
        if (onReaderAiEntitlementRequired(feature, normalizedText)) return
        pdfAiResultRequestId += 1
        val aiResultRequestId = pdfAiResultRequestId
        dismissedPdfAiResultRequestId = null
        updatePdfAiResult(
            aiResultRequestId,
            ReaderAiResultState(
                title = feature.displayName,
                isLoading = true
            )
        )
        pdfScope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> {
                    var streamedDefinition = ""
                    val definition = aiAdapter.defineStreaming(
                        text = normalizedText.take(2400),
                        context = currentPdfPageText(),
                        onUpdate = { chunk ->
                            streamedDefinition += chunk
                            updatePdfAiResult(
                                aiResultRequestId,
                                ReaderAiResultState(
                                    title = feature.displayName,
                                    text = streamedDefinition,
                                    isLoading = true
                                )
                            )
                        }
                    )
                    (definition.definition?.takeIf { it.isNotBlank() } ?: streamedDefinition) to definition.error
                }
                ReaderAiFeature.SUMMARIZE -> {
                    var streamedSummary = ""
                    var streamedCost: Double? = null
                    var streamedFreeRemaining: Int? = null
                    fun updateStreamingSummary() {
                        val partial = SummarizationResult(
                            summary = streamedSummary.takeIf { it.isNotBlank() },
                            cost = streamedCost,
                            freeRemaining = streamedFreeRemaining
                        )
                        pdfHubSummaryResult = partial
                        updatePdfAiResult(
                            aiResultRequestId,
                            ReaderAiResultState(
                                title = feature.displayName,
                                text = streamedSummary,
                                isLoading = true
                            )
                        )
                    }
                    val summary = aiAdapter.summarizeStreaming(
                        text = normalizedText,
                        onUsageReceived = { cost, freeRemaining ->
                            cost?.let { streamedCost = it }
                            freeRemaining?.let { streamedFreeRemaining = it }
                            updateStreamingSummary()
                        },
                        onUpdate = { chunk ->
                            streamedSummary += chunk
                            updateStreamingSummary()
                        }
                    )
                    val finalSummary = summary.summary?.takeIf { it.isNotBlank() } ?: streamedSummary.takeIf { it.isNotBlank() }
                    finalSummary?.let { generated ->
                        summaryCacheStore.saveSummary(pdfHubBookKey(), pageIndex, "Page ${pageIndex + 1}", generated)
                    }
                    pdfHubSummaryResult = summary.copy(summary = finalSummary)
                    finalSummary to summary.error
                }
                ReaderAiFeature.RECAP -> aiAdapter.recap(normalizedText).let { it.recap to it.error }
            }
            updatePdfAiResult(
                aiResultRequestId,
                ReaderAiResultState(
                    title = feature.displayName,
                    text = result.first.orEmpty(),
                    errorMessage = result.second,
                    isLoading = false
                )
            )
            if (isPdfAiResultVisible(aiResultRequestId)) {
                onPaidFeatureError(result.second)
            }
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
                statusMessage = pdfString("desktop_voice_cache_cleared", "Voice cache cleared"),
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
    }

    fun pdfCloudTtsUnavailableMessage(): String {
        return pdfString(
            "desktop_cloud_tts_signed_in_credits_required_desc",
            "Cloud TTS needs a signed-in account with credits. Pro and credits can only be purchased from the Android app."
        )
    }

    fun pdfReadScopeLabel(readScope: ReaderTtsReadScope): String {
        return when (readScope) {
            ReaderTtsReadScope.PAGE -> pdfString("desktop_page", "Page")
            ReaderTtsReadScope.CHAPTER -> pdfString("chapter", "Chapter")
            ReaderTtsReadScope.BOOK -> pdfString("desktop_from_here", "From here")
        }
    }

    fun startPdfCloudTts(
        readScope: ReaderTtsReadScope,
        startChunkIndex: Int = 0,
        chunksOverride: List<ReaderTtsChunk>? = null,
        restartActive: Boolean = false
    ) {
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "pdf_sequence_toggle scope=${readScope.name} startPage=${pageIndex + 1} " +
                "isPlaying=${pdfExtrasState.cloudTts.isPlaying} isLoading=${pdfExtrasState.cloudTts.isLoading} " +
                "keyPresent=${settings.geminiKey.isNotBlank()} ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" " +
                "available=${ttsAdapter.isAvailable}"
        )
        val ttsActive = pdfExtrasState.cloudTts.isPlaying || pdfExtrasState.cloudTts.isLoading || pdfExtrasState.cloudTts.isPaused
        if (ttsActive && !restartActive) {
            stopPdfCloudTts()
            return
        }
        if (ttsActive) {
            pdfTtsJob?.cancel()
            pdfTtsJob = null
        }
        if (!ttsAdapter.isAvailable) {
            logDesktopTts("pdf_sequence_blocked reason=adapter_unavailable")
            onCloudTtsEntitlementRequired()
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = pdfCloudTtsUnavailableMessage(),
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
                statusMessage = pdfString(
                    "desktop_preparing_scope_format",
                    "Preparing %1\$s",
                    pdfReadScopeLabel(readScope)
                ),
                progress = ReaderTtsProgress(sessionId = ttsSessionId, scope = readScope),
                cacheSummary = currentPdfTtsCacheSummary()
            )
        )
        fun updatePdfTtsSession(transform: (ReaderExtrasState) -> ReaderExtrasState) {
            if (pdfExtrasState.cloudTts.progress.sessionId == ttsSessionId) {
                pdfExtrasState = transform(pdfExtrasState)
            }
        }
        val noTextMessage = pdfString("desktop_no_text_here_to_read", "There is no text here to read.")
        pdfTtsJob = pdfScope.launch {
            var completedChunkCount = 0
            runCatching {
                val ttsChunks = chunksOverride
                    ?.filter { it.text.isNotBlank() }
                    ?: withContext(Dispatchers.IO) {
                        pdfTtsChunksForScope(readScope, pageIndex)
                            .filter { it.text.isNotBlank() }
                            .withTtsReplacements(ttsReplacementPreferences, document.path)
                    }
                if (ttsChunks.isEmpty()) {
                    logDesktopTts("pdf_sequence_ignored reason=blank_text scope=${readScope.name}")
                    throw IllegalStateException(noTextMessage)
                }
                val boundedStartChunkIndex = startChunkIndex.coerceIn(0, ttsChunks.lastIndex)
                val playbackChunks = ttsChunks.drop(boundedStartChunkIndex)
                val initialProgress = ReaderTtsProgress(
                    sessionId = ttsSessionId,
                    scope = readScope,
                    chunks = ttsChunks,
                    currentChunkIndex = boundedStartChunkIndex - 1
                )
                updatePdfTtsSession { extras ->
                    extras.copy(
                        cloudTts = extras.cloudTts.copy(
                            progress = initialProgress,
                            cacheSummary = currentPdfTtsCacheSummary()
                        )
                    )
                }
                logDesktopTts(
                    "pdf_sequence_start scope=${readScope.name} chunks=${ttsChunks.size} " +
                        "startChunk=${boundedStartChunkIndex + 1}"
                )
                ttsAdapter.speakChunks(document.title, readScope, playbackChunks) { relativeIndex ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("PDF cloud TTS stopped")
                    val index = boundedStartChunkIndex + relativeIndex
                    val chunk = ttsChunks[index]
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    if (chunk.pageIndex != pdfState.pageIndex) {
                        goToPage(chunk.pageIndex, recordJump = false)
                    }
                    updatePdfTtsSession { extras ->
                        extras.copy(
                            cloudTts = ReaderCloudTtsState(
                                isAvailable = true,
                                isPlaying = true,
                                statusMessage = progress.currentPositionLabel ?: pdfString("label_reading", "Reading"),
                                progress = progress,
                                cacheSummary = currentPdfTtsCacheSummary()
                            )
                        )
                    }
                    logDesktopTts(
                        "pdf_chunk_start scope=${readScope.name} index=${index + 1}/${ttsChunks.size} " +
                            "page=${chunk.pageIndex + 1} offsets=${chunk.startOffset}..${chunk.endOffset} chars=${chunk.text.length}"
                    )
                    completedChunkCount = index + 1
                }
            }.onFailure { error ->
                logDesktopTts("pdf_sequence_failed error=\"${error.desktopTtsSummary()}\"")
                updatePdfTtsSession { extras ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        extras.copy(
                            cloudTts = pdfCloudTtsStoppedState(statusMessage = pdfString("desktop_stopped", "Stopped"))
                        )
                    } else {
                        onPaidFeatureError(error.message)
                        extras.copy(
                            cloudTts = pdfCloudTtsStoppedState(
                                errorMessage = error.message ?: pdfString("desktop_cloud_tts_failed", "Cloud TTS failed.")
                            )
                        )
                    }
                }
            }.onSuccess {
                logDesktopTts("pdf_sequence_success chunks=$completedChunkCount")
                updatePdfTtsSession { extras ->
                    extras.copy(
                        cloudTts = pdfCloudTtsStoppedState(statusMessage = pdfString("desktop_finished", "Finished"))
                    )
                }
            }
        }
    }

    fun skipPdfCloudTtsChunk(delta: Int) {
        val progress = pdfExtrasState.cloudTts.progress
        if (progress.chunks.isEmpty()) return
        val currentIndex = progress.currentChunkIndex.takeIf { it >= 0 } ?: return
        val targetIndex = (currentIndex + delta).coerceIn(0, progress.chunks.lastIndex)
        if (targetIndex == currentIndex) return
        startPdfCloudTts(
            readScope = progress.scope,
            startChunkIndex = targetIndex,
            chunksOverride = progress.chunks,
            restartActive = true
        )
    }

    fun locatePdfCloudTtsChunk() {
        val chunk = pdfExtrasState.cloudTts.progress.currentChunk ?: return
        goToPage(chunk.pageIndex, recordJump = false)
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
                    errorMessage = pdfString("desktop_no_text_on_page_to_read", "There is no text on this page to read."),
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        if (!ttsAdapter.isAvailable) {
            logDesktopTts("pdf_toggle_blocked reason=adapter_unavailable")
            onCloudTtsEntitlementRequired()
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = pdfCloudTtsUnavailableMessage(),
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        val selectionChunks = ReaderTtsPlanner.chunksForText(
            text = normalizedText,
            pageIndex = pageIndex,
            chapterIndex = 0,
            chapterTitle = pdfString("pdf_page_short", "Page %1\$d", pageIndex + 1)
        ).withTtsReplacements(ttsReplacementPreferences, document.path)
        if (selectionChunks.isEmpty()) {
            pdfExtrasState = pdfExtrasState.copy(
                cloudTts = pdfExtrasState.cloudTts.copy(
                    errorMessage = pdfString("desktop_no_text_on_page_to_read", "There is no text on this page to read."),
                    cacheSummary = currentPdfTtsCacheSummary()
                )
            )
            return
        }
        startPdfCloudTts(
            readScope = ReaderTtsReadScope.PAGE,
            chunksOverride = selectionChunks
        )
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

    fun dismissSelectedTextHighlightSheet() {
        clearPdfInteractionState()
        pdfState = pdfState.withDesktopPdfTextHighlightSheetDismissed(zoomSpec)
        requestPdfReaderFocusRestore()
    }

    fun deleteSelectedTextHighlight(annotation: SharedPdfAnnotation) {
        clearPdfInteractionState()
        pdfState = pdfState.withDesktopPdfTextHighlightSheetDismissed(zoomSpec)
        dispatchPdf(SharedPdfReaderAction.AnnotationDeleted(annotation.id))
        requestPdfReaderFocusRestore()
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
        if (displayMode == PdfDisplayMode.PAGINATION) {
            val normalizedTarget = PdfSpreadLayout.normalizePageIndex(targetPage, document.pageCount, pdfReaderSettings)
            if (normalizedTarget != pdfState.pageIndex) {
                dispatchPdf(SharedPdfReaderAction.GoToPage(normalizedTarget))
            }
        } else if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
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
        isPdfTwoPageSpread,
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
                if (!isPdfTwoPageSpread && renderedPageIndex != viewport.pageIndex) return@LaunchedEffect
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

    LaunchedEffect(
        documentHandleId,
        pendingPdfNavigationScrollRestore?.requestId,
        pageIndex,
        scale,
        displayMode
    ) {
        val restore = pendingPdfNavigationScrollRestore ?: return@LaunchedEffect
        if (
            displayMode != PdfDisplayMode.PAGINATION ||
            restore.pageIndex != pageIndex ||
            abs(restore.zoom - scale) > 0.001f
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        pageHorizontalScrollState.scrollTo(restore.horizontalScroll)
        pageVerticalScrollState.scrollTo(restore.verticalScroll)
        withFrameNanos { }
        pageHorizontalScrollState.scrollTo(restore.horizontalScroll)
        pageVerticalScrollState.scrollTo(restore.verticalScroll)
        logPdfZoomSettle {
            "preview_navigation_restore request=${restore.requestId} page=${pageIndex + 1} " +
                "zoom=${scale.formatLogFloat()} h=${pageHorizontalScrollState.value} " +
                "v=${pageVerticalScrollState.value}"
        }
        if (pendingPdfNavigationScrollRestore == restore) {
            pendingPdfNavigationScrollRestore = null
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

    fun togglePdfTextSelectionMode() {
        val enabled = !isTextSelectionMode
        if (enabled) {
            deactivateRichTextMode()
            commitActiveTextDraft()
        }
        dispatchPdf(SharedPdfReaderAction.TextSelectionModeChanged(enabled))
        if (!enabled) {
            clearPdfInteractionState()
        }
    }

    @Composable
    fun DesktopPdfBottomMarkupDock(modifier: Modifier = Modifier) {
        SharedPdfInteractionDock(
            isTextSelectionMode = isTextSelectionMode,
            selectedTool = selectedTool,
            selectedColor = selectedColor,
            strokeWidth = strokeWidth,
            toolConfigs = pdfState.toolConfigs,
            penPalette = pdfState.penPalette,
            highlighterPalette = pdfHighlighterColors,
            lastActivePenTool = pdfState.lastActivePenTool,
            lastActiveHighlighterTool = pdfState.lastActiveHighlighterTool,
            onPanSelected = ::selectPdfPanMode,
            onTextSelectionSelected = ::togglePdfTextSelectionMode,
            onToolSelected = ::selectPdfAnnotationTool,
            onColorSelected = { dispatchPdf(SharedPdfReaderAction.ColorSelected(it)) },
            onStrokeWidthChange = { dispatchPdf(SharedPdfReaderAction.StrokeWidthChanged(it)) },
            onUndo = { dispatchPdf(SharedPdfReaderAction.UndoAnnotationEdit) },
            onRedo = { dispatchPdf(SharedPdfReaderAction.RedoAnnotationEdit) },
            onClearPage = { dispatchPdf(SharedPdfReaderAction.ClearPageAnnotations(pageIndex)) },
            modifier = modifier,
            allowExpandedSettings = !isPdfSearchActive &&
                activeTextDraft == null &&
                !isRichTextMode &&
                textSelection == null &&
                selectionMenuOffset == null &&
                !pdfSelectionSheetActive &&
                externalLinkDialogUrl == null &&
                !pdfExtrasState.aiResult.hasContent,
            canUndo = pdfState.canUndoAnnotationEdit,
            canRedo = pdfState.canRedoAnnotationEdit,
            canClearPage = annotations.any { it.pageIndex == pageIndex },
            isHighlighterSnapEnabled = isHighlighterSnapEnabled,
            onHighlighterSnapChange = { isHighlighterSnapEnabled = it },
            onHighlighterPaletteChange = { colors ->
                onPdfHighlighterPaletteChange(SharedPdfHighlighterPalette(colors).sanitized())
            },
            onPenPaletteChange = { colors -> dispatchPdf(SharedPdfReaderAction.PenPaletteChanged(colors)) }
        )
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
                    goToPage(visiblePage, scrollVertical = false, commitPendingZoomPreview = false)
                }
            }
    }

    LaunchedEffect(documentHandleId, pageIndex, scale, displayMode, isPdfTwoPageSpread) {
        renderJob?.cancel()
        if (displayMode != PdfDisplayMode.PAGINATION || isPdfTwoPageSpread) {
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
        logPdfZoomSettle {
            "render_effect seq=$pdfZoomSettleSequence page=${pageIndex + 1} scale=${scale.formatLogFloat()} " +
                "existingPage=${renderedPageIndex?.plus(1) ?: "none"} " +
                "existingScale=${renderedPageScale?.formatLogFloat() ?: "none"} " +
                "preview=${pdfZoomPreview != null} h=${pageHorizontalScrollState.value} v=${pageVerticalScrollState.value} " +
                "pageRoot=${paginatedPageRootOffset.formatLogOffset()} canvas=${pageCanvasSize.formatLogSize()}"
        }
        if (renderedPageIndex != pageIndex) {
            paginatedRenderCache[pageIndex]?.let { cached ->
                logPdfZoomPerf {
                    "cache_hit page=${pageIndex + 1} scale=${cached.scale.formatLogFloat()} " +
                        "bitmap=${cached.render.width}x${cached.render.height}"
                }
                logPdfZoomSettle {
                    "cache_hit seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                        "scale=${cached.scale.formatLogFloat()} bitmap=${cached.render.width}x${cached.render.height}"
                }
                renderedPage = cached.render
                renderedPageIndex = pageIndex
                renderedPageScale = cached.scale
                renderError = null
                isRendering = false
            }
        }
        val hasPageRender = renderedPage != null && desktopPdfRenderBelongsToPage(renderedPageIndex, pageIndex)
        if (!hasPageRender) {
            logPdfZoomPerf {
                "cache_miss page=${pageIndex + 1}; stale=${renderedPageIndex?.let { it + 1 } ?: "none"}"
            }
            logPdfZoomSettle {
                "cache_miss seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                    "stale=${renderedPageIndex?.plus(1) ?: "none"}"
            }
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
                renderError = pdfString("desktop_failed_render_page", "Failed to render page.")
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
            logPdfZoomSettle {
                "render_plan seq=$pdfZoomSettleSequence page=${pageIndex + 1} requestedScale=${scale.formatLogFloat()} " +
                    "safeScale=${safeScale.formatLogFloat()} firstScale=${firstRenderScale.formatLogFloat()} " +
                    "hasRender=$hasPageRender opening=$isOpeningRender existingScale=${renderedPageScale?.formatLogFloat() ?: "none"}"
            }

            suspend fun renderAt(renderScale: Float, delayMillis: Long, showSpinner: Boolean): Boolean {
                logPdfZoomPerf {
                    "render_scheduled page=${pageIndex + 1} renderScale=${renderScale.formatLogFloat()} " +
                        "requestedScale=${scale.formatLogFloat()} delayMs=$delayMillis showSpinner=$showSpinner " +
                        "hasPageRender=$hasPageRender"
                }
                logPdfZoomSettle {
                    "render_scheduled seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                        "renderScale=${renderScale.formatLogFloat()} requestedScale=${scale.formatLogFloat()} " +
                        "delayMs=$delayMillis showSpinner=$showSpinner preview=${pdfZoomPreview != null}"
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
                    logPdfZoomSettle {
                        "render_stale seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                            "renderScale=${renderScale.formatLogFloat()} elapsedMs=$elapsedMs " +
                            "currentPage=${currentPdfPageIndex + 1} currentScale=${currentPdfScale.formatLogFloat()} " +
                            "mode=$currentPdfDisplayMode"
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
                    ?: if (renderedPage == null || renderedPageIndex != pageIndex) {
                        pdfString("desktop_failed_render_page", "Failed to render page.")
                    } else {
                        null
                    }
                logPdfZoomPerf {
                    "render_end page=${pageIndex + 1} renderScale=${renderScale.formatLogFloat()} " +
                        "requestedScale=${scale.formatLogFloat()} elapsedMs=$elapsedMs success=${result.isSuccess} " +
                        "error=${result.exceptionOrNull()?.message?.logPreview() ?: "none"}"
                }
                logPdfZoomSettle {
                    "render_end seq=$pdfZoomSettleSequence page=${pageIndex + 1} " +
                        "renderScale=${renderScale.formatLogFloat()} requestedScale=${scale.formatLogFloat()} " +
                        "elapsedMs=$elapsedMs success=${result.isSuccess} bitmap=${renderedPage?.width ?: 0}x${renderedPage?.height ?: 0} " +
                        "h=${pageHorizontalScrollState.value} v=${pageVerticalScrollState.value} " +
                        "pageRoot=${paginatedPageRootOffset.formatLogOffset()} canvas=${pageCanvasSize.formatLogSize()}"
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
                desktopPdfRenderScaleNeedsUpgrade(existingScale, firstRenderScale)
            if (needsFirstRender) {
                renderAt(
                    renderScale = firstRenderScale,
                    delayMillis = if (hasPageRender) DesktopPdfZoomRenderDebounceMillis else 45L,
                    showSpinner = !hasPageRender
                )
            } else {
                logPdfZoomSettle {
                    "render_skip seq=$pdfZoomSettleSequence page=${pageIndex + 1} reason=no_scale_upgrade " +
                        "existingScale=${existingScale?.formatLogFloat() ?: "none"} firstScale=${firstRenderScale.formatLogFloat()}"
                }
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
        hasAnnotations = sortedSidebarHighlights.isNotEmpty(),
        hasEmbeddedComments = sortedEmbeddedAnnotations.isNotEmpty(),
        searchActive = isPdfSearchActive || searchQuery.isNotBlank(),
        annotationEditing = activeTextDraft != null ||
            selectedAnnotation != null ||
            selectedTool != PdfInkTool.NONE,
        richTextEditing = isRichTextMode,
        loading = isRendering || isSearchIndexing || isPdfFileActionLoading || isReflowingThisBook,
        errorMessage = renderError,
        extrasState = pdfExtrasState,
        aiAvailable = featurePolicy.aiAndCloud && aiByokSettings.sanitized().areReaderAiFeaturesAvailable,
        cloudTtsAvailable = cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable,
        externalLookupAvailable = featurePolicy.externalLookup
    )

    fun runPdfKeyCommand(command: DesktopPdfKeyCommand): Boolean {
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

        return when (command) {
            DesktopPdfKeyCommand.EXIT_FULLSCREEN -> {
                setPdfFullscreen(false)
                true
            }
            DesktopPdfKeyCommand.PREVIOUS_PAGE -> {
                goToPage(previousPdfPageTarget())
                true
            }
            DesktopPdfKeyCommand.NEXT_PAGE -> {
                goToPage(nextPdfPageTarget())
                true
            }
            DesktopPdfKeyCommand.SCROLL_UP -> scrollVertically(-96f)
            DesktopPdfKeyCommand.SCROLL_DOWN -> scrollVertically(96f)
            DesktopPdfKeyCommand.FIRST_PAGE -> {
                goToPage(0)
                true
            }
            DesktopPdfKeyCommand.LAST_PAGE -> {
                goToPage(document.pageCount - 1)
                true
            }
            DesktopPdfKeyCommand.SEARCH -> {
                dispatchPdf(SharedPdfReaderAction.SearchOpened)
                true
            }
            DesktopPdfKeyCommand.ZOOM_IN -> {
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(0.15f))
                true
            }
            DesktopPdfKeyCommand.ZOOM_OUT -> {
                cancelPendingPdfZoomPreview()
                dispatchPdf(SharedPdfReaderAction.ZoomBy(-0.15f))
                true
            }
        }
    }

    fun isPdfTextEditingActive(): Boolean {
        return activeTextDraft != null ||
            (selectedTool == PdfInkTool.TEXT && selectedAnnotation?.kind == PdfAnnotationKind.TEXT) ||
            isRichTextMode
    }

    fun handlePdfReaderKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val command = event.desktopPdfKeyCommandOrNull(
            fullscreen = isFullscreen,
            editingText = isPdfTextEditingActive(),
            rightToLeftPagination = rightToLeftPdfPaginationActive
        ) ?: return false
        return runPdfKeyCommand(command)
    }

    fun handlePdfReaderAwtKeyEvent(event: AwtKeyEvent): Boolean {
        val command = event.desktopPdfKeyCommandOrNull(
            fullscreen = isFullscreen,
            editingText = isPdfTextEditingActive(),
            rightToLeftPagination = rightToLeftPdfPaginationActive
        ) ?: return false
        return runPdfKeyCommand(command)
    }

    fun handlePdfReaderFullscreenAwtKeyEvent(event: AwtKeyEvent): Boolean {
        if (isPdfSearchActive) {
            if (event.id == AwtKeyEvent.KEY_PRESSED && isFullscreen && event.keyCode == AwtKeyEvent.VK_ESCAPE) {
                return runPdfKeyCommand(DesktopPdfKeyCommand.EXIT_FULLSCREEN)
            }
            return false
        }
        return handlePdfReaderAwtKeyEvent(event)
    }

    fun handlePdfReaderGlobalShortcutAwtKeyEvent(event: AwtKeyEvent): Boolean {
        if (event.id != AwtKeyEvent.KEY_PRESSED || !event.isControlDown) return false
        return when (event.keyCode) {
            AwtKeyEvent.VK_F -> runPdfKeyCommand(DesktopPdfKeyCommand.SEARCH)
            else -> false
        }
    }

    DesktopReaderKeyDispatcherEffect(
        enabled = !pdfPopupActive,
        allowChromeModalWindows = true,
        onKeyPressed = { event -> handlePdfReaderGlobalShortcutAwtKeyEvent(event) }
    )

    DesktopReaderKeyDispatcherEffect(
        enabled = !pdfPopupActive && !isPdfSearchActive,
        allowPanelModalWindows = true,
        dispatchWhenOwnerWindowActive = false,
        onKeyPressed = { event -> handlePdfReaderAwtKeyEvent(event) }
    )

    DesktopReaderFullscreenKeyEffect(
        enabled = isFullscreen && !pdfPopupActive,
        onKeyPressed = { event -> handlePdfReaderFullscreenAwtKeyEvent(event) }
    )

    ReaderWorkspaceShell(
        model = pdfWorkspaceModel,
        title = document.title,
        subtitle = pdfString("desktop_label_pair_format", "%1\$s - %2\$s", document.formatLabel, pdfPageLabel),
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
        onReadAloudAction = if (cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable) {
            { startPdfCloudTts(ReaderTtsReadScope.BOOK) }
        } else {
            null
        },
        onAiHubAction = if (aiByokSettings.sanitized().areReaderAiFeaturesAvailable) {
            { showPdfAiHub = true }
        } else {
            null
        },
        fileActions = pdfFileActions,
        onSaveCopyAction = requestSaveCopy,
        onPrintAction = requestPrint,
        onTextViewAction = onReflowAction?.let { action -> { action(pdfState.pageIndex) } },
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
        closeRightPanelOnReaderTap = true,
        onReaderFocusRestoreRequest = ::requestPdfReaderFocusRestore,
        leftSidebar = { _ ->
            DesktopPdfNavigationSidebar(
                document = document,
                pageIndex = pageIndex,
                sortedHighlights = sortedSidebarHighlights,
                bookmarks = bookmarks,
                onPageSelected = { page -> goToPage(page, recordJump = true) },
                onAnnotationOpened = ::goToAnnotation,
                onAnnotationSelected = ::selectAnnotation,
                onAnnotationDeleted = { annotation -> deleteAnnotation(annotation.id) }
            )
        },
        rightInspector = {
            DesktopPdfInspectorPanel(
                document = document,
                displayMode = displayMode,
                pdfReaderSettings = pdfReaderSettings,
                appThemeControls = appThemeControls,
                customReaderThemes = customReaderThemes,
                onCustomReaderThemesChange = onCustomReaderThemesChange,
                customTextureIds = customTextureIds,
                onImportTexture = onImportTexture,
                onReaderSettingsChange = ::updatePdfReaderSettings,
                selectedTool = selectedTool,
                isRichTextMode = isRichTextMode,
                pdfHighlighterPalette = pdfHighlighterPalette,
                effectiveTextStyleConfig = effectiveTextStyleConfig,
                richTextController = richTextController,
                pdfExtrasState = pdfExtrasState,
                aiByokSettings = aiByokSettings,
                cloudTtsFeatureAvailable = cloudTtsControlsAvailable,
                ttsReplacementPreferences = ttsReplacementPreferences,
                onDisplayModeSelected = { mode ->
                    commitActiveTextDraft()
                    updatePdfReaderSettings(
                        pdfReaderSettings.copy(readingMode = mode.toDesktopReaderReadingMode())
                    )
                    dispatchPdf(SharedPdfReaderAction.DisplayModeChanged(mode))
                },
                onRichTextModeToggle = {
                    if (isRichTextMode) {
                        deactivateRichTextMode()
                    } else {
                        activateRichTextMode()
                    }
                },
                onHighlighterPaletteChange = ::updatePdfHighlighterPalette,
                onTextStyleChange = ::updateTextStyleConfig,
                onCloudTtsClearCache = ::clearPdfCloudTtsCache,
                onCloudTtsVoiceChange = { voiceId ->
                    onAiByokSettingsChange(aiByokSettings.sanitized().copy(ttsSpeakerId = voiceId))
                },
                onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
            )
        },
        bottomBar = {
            DesktopPdfBottomChrome(
                pageIndex = pageIndex,
                pageCount = document.pageCount,
                pageLabel = pdfPageLabel,
                progressPercent = progressPercent,
                canGoPrevious = canGoPrevious,
                canGoNext = canGoNext,
                showJumpHistory = !isPdfSearchActive,
                jumpBackPage = jumpHistory.backPage,
                jumpForwardPage = jumpHistory.forwardPage,
                onPrevious = { goToPage(previousPdfPageTarget()) },
                onNext = { goToPage(nextPdfPageTarget()) },
                onPageScrub = ::updatePdfPageScrub,
                onPageScrubFinished = ::finishPdfPageScrub,
                onJumpBack = ::goBackInJumpHistory,
                onJumpForward = ::goForwardInJumpHistory,
                onClearJumpHistory = { jumpHistory = jumpHistory.clear() },
                extraContent = {
                    if (cloudTtsControlsAvailable) {
                        val ttsControls = readerCloudTtsControlsModel(pdfExtrasState.cloudTts)
                        if (ttsControls.isVisible) {
                            SharedReaderTtsOverlayControls(
                                settings = aiByokSettings,
                                cloudTts = pdfExtrasState.cloudTts,
                                credits = credits,
                                showCredits = showPaidCredits,
                                isCollapsed = isPdfTtsOverlayCollapsed,
                                onCollapseChange = { isPdfTtsOverlayCollapsed = it },
                                onPauseResume = ::pauseResumePdfCloudTts,
                                onSkipPrevious = { skipPdfCloudTtsChunk(-1) },
                                onSkipNext = { skipPdfCloudTtsChunk(1) },
                                onLocateCurrentChunk = ::locatePdfCloudTtsChunk,
                                onClose = ::stopPdfCloudTts,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                    DesktopPdfBottomMarkupDock(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp)
                    )
                }
            )
        },
        fullscreenBottomBar = {
            DesktopPdfFullscreenBottomChrome(
                pageIndex = pageIndex,
                pageCount = document.pageCount,
                pageLabel = pdfPageLabel,
                canGoPrevious = canGoPrevious,
                canGoNext = canGoNext,
                showJumpHistory = !isPdfSearchActive,
                jumpBackPage = jumpHistory.backPage,
                jumpForwardPage = jumpHistory.forwardPage,
                onPrevious = { goToPage(previousPdfPageTarget()) },
                onNext = { goToPage(nextPdfPageTarget()) },
                onPageScrub = ::updatePdfPageScrub,
                onPageScrubFinished = ::finishPdfPageScrub,
                onJumpBack = ::goBackInJumpHistory,
                onJumpForward = ::goForwardInJumpHistory,
                onClearJumpHistory = { jumpHistory = jumpHistory.clear() },
                extraContent = {
                    if (cloudTtsControlsAvailable) {
                        val ttsControls = readerCloudTtsControlsModel(pdfExtrasState.cloudTts)
                        if (ttsControls.isVisible) {
                            SharedReaderTtsOverlayControls(
                                settings = aiByokSettings,
                                cloudTts = pdfExtrasState.cloudTts,
                                credits = credits,
                                showCredits = showPaidCredits,
                                isCollapsed = isPdfTtsOverlayCollapsed,
                                onCollapseChange = { isPdfTtsOverlayCollapsed = it },
                                onPauseResume = ::pauseResumePdfCloudTts,
                                onSkipPrevious = { skipPdfCloudTtsChunk(-1) },
                                onSkipNext = { skipPdfCloudTtsChunk(1) },
                                onLocateCurrentChunk = ::locatePdfCloudTtsChunk,
                                onClose = ::stopPdfCloudTts,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                    DesktopPdfBottomMarkupDock(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp)
                    )
                }
            )
        }
    ) { _ ->
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
        val pdfViewportBackground = desktopPdfViewportBackgroundColor(
            displayMode = displayMode,
            pageBackgroundColor = pdfThemeStyle.pageBackgroundColor,
            appBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            isVerticalPageGapVisible = pdfReaderSettings.pdfVerticalPageGapVisible
        )
        if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
            val verticalPageGap = pdfVerticalPageGapDp(
                isPageGapVisible = pdfReaderSettings.pdfVerticalPageGapVisible,
                defaultGap = DesktopDefaultPdfVerticalPageGap
            )
            Box(
                modifier = Modifier
                            .fillMaxSize()
                            .background(pdfViewportBackground, RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp))
                            .onSizeChanged { size -> pdfZoomViewportSize = size }
                            .onGloballyPositioned { coordinates ->
                        val rootOffset = coordinates.positionInRoot()
                        if (rootOffset != pdfZoomViewportRootOffset) {
                            logPdfZoomSettle {
                                "viewport_layout seq=$pdfZoomSettleSequence mode=vertical " +
                                    "prev=${pdfZoomViewportRootOffset.formatLogOffset()} next=${rootOffset.formatLogOffset()} " +
                                    "scale=${scale.formatLogFloat()} preview=${pdfZoomPreview != null}"
                            }
                        }
                        pdfZoomViewportRootOffset = rootOffset
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
                            val verticalZoomPreview = pdfZoomPreview?.takeIf {
                                it.displayMode == PdfDisplayMode.VERTICAL_SCROLL
                            }
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
                                cloudTtsAvailable = cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable,
                                externalLookupAvailable = featurePolicy.externalLookup,
                                themeStyle = pdfThemeStyle,
                                shouldRender = verticalPageIndex in verticalRenderWindow,
                                zoomPreview = verticalZoomPreview,
                                zoomPreviewAnchorPageRootOffset = verticalZoomPreview
                                    ?.pageIndex
                                    ?.let { verticalPageRootOffsets[it] },
                                zoomPreviewScrollBounds = verticalZoomPreview?.let {
                                    desktopPdfZoomScrollBoundsWithCommitTargets(
                                        preview = it,
                                        currentHorizontalScroll = pageHorizontalScrollState.value,
                                        maxHorizontalScroll = pageHorizontalScrollState.maxValue
                                    )
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
                                    val previousOffset = verticalPageRootOffsets[page]
                                    if (previousOffset != offset) {
                                        logPdfZoomSettle {
                                            "page_layout seq=$pdfZoomSettleSequence mode=vertical page=${page + 1} " +
                                                "prevRoot=${previousOffset.formatLogOffset()} nextRoot=${offset.formatLogOffset()} " +
                                                "scale=${scale.formatLogFloat()} preview=${verticalZoomPreview != null} " +
                                                "h=${pageHorizontalScrollState.value} list=${verticalListState.firstVisibleItemIndex}:" +
                                                verticalListState.firstVisibleItemScrollOffset
                                        }
                                    }
                                    verticalPageRootOffsets[page] = offset
                                }
                            )
                        }
                    }
                    SharedPdfVerticalScrollbar(
                        listState = verticalListState,
                        pageCount = document.pageCount,
                        currentPage = pageIndex,
                        isDarkMode = pdfViewportBackground.luminance() < 0.5f,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                    DesktopPdfPageScrubOverlay(
                        pageIndex = pageScrubPreview,
                        pageCount = document.pageCount,
                        pageLabel = pdfPageScrubPreviewLabel
                    )
                }
            } else {
                if (isPdfTwoPageSpread) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(pdfViewportBackground, RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp))
                            .onSizeChanged { size -> pdfZoomViewportSize = size }
                            .onGloballyPositioned { coordinates ->
                                val rootOffset = coordinates.positionInRoot()
                                if (rootOffset != pdfZoomViewportRootOffset) {
                                    logPdfZoomSettle {
                                        "viewport_layout seq=$pdfZoomSettleSequence mode=spread " +
                                            "prev=${pdfZoomViewportRootOffset.formatLogOffset()} next=${rootOffset.formatLogOffset()} " +
                                            "scale=${scale.formatLogFloat()} preview=${pdfZoomPreview != null}"
                                    }
                                }
                                pdfZoomViewportRootOffset = rootOffset
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
                        val spreadPageGap = desktopPdfSpreadPageGapDp(
                            isPageGapVisible = pdfReaderSettings.pdfVerticalPageGapVisible
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spreadPageGap, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Top
                        ) {
                            val spreadZoomPreview = pdfZoomPreview?.takeIf {
                                it.displayMode == PdfDisplayMode.PAGINATION
                            }
                            val spreadPredictedPageCanvasSizes = paginatedVisiblePageIndices.mapNotNull { visiblePageIndex ->
                                document.pageSizes.getOrNull(visiblePageIndex)?.let { pageSize ->
                                    val pageDisplayScale = zoomSpec.clamp(scale)
                                    visiblePageIndex to IntSize(
                                        width = (pageSize.width * pageDisplayScale).roundToInt().coerceAtLeast(1),
                                        height = (pageSize.height * pageDisplayScale).roundToInt().coerceAtLeast(1)
                                    )
                                }
                            }.toMap()
                            val spreadLayoutPrediction = spreadZoomPreview?.let {
                                desktopPdfSpreadLayoutPrediction(
                                    viewportRootOffset = pdfZoomViewportRootOffset,
                                    viewportSize = pdfZoomViewportSize,
                                    visiblePageIndices = paginatedVisiblePageIndices,
                                    pageCanvasSizes = spreadPredictedPageCanvasSizes,
                                    horizontalScroll = pageHorizontalScrollState.value,
                                    verticalScroll = pageVerticalScrollState.value,
                                    paddingPx = with(density) { 24.dp.toPx() },
                                    pageGapPx = with(density) { spreadPageGap.toPx() }
                                )
                            }
                            val spreadZoomAnchorPageRootOffset = spreadZoomPreview
                                ?.pageIndex
                                ?.let { spreadLayoutPrediction?.pageRootOffsets?.get(it) ?: paginatedZoomPageRoot(it) }
                            val spreadZoomScrollBounds = spreadZoomPreview?.let {
                                DesktopPdfZoomScrollBounds(
                                    currentHorizontalScroll = pageHorizontalScrollState.value,
                                    maxHorizontalScroll = spreadLayoutPrediction?.maxHorizontalScroll
                                        ?: pageHorizontalScrollState.maxValue,
                                    currentVerticalScroll = pageVerticalScrollState.value,
                                    maxVerticalScroll = spreadLayoutPrediction?.maxVerticalScroll
                                        ?: pageVerticalScrollState.maxValue
                                )
                            }
                            paginatedVisiblePageIndices.forEach { spreadPageIndex ->
                                DesktopVerticalPdfPage(
                                    document = document,
                                    pageIndex = spreadPageIndex,
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
                                    cloudTtsAvailable = cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable,
                                    externalLookupAvailable = featurePolicy.externalLookup,
                                    themeStyle = pdfThemeStyle,
                                    shouldRender = true,
                                    zoomPreview = spreadZoomPreview,
                                    zoomPreviewAnchorPageRootOffset = spreadZoomAnchorPageRootOffset,
                                    zoomPreviewScrollBounds = spreadZoomScrollBounds,
                                    zoomViewportRootOffset = pdfZoomViewportRootOffset,
                                    showPageNumberOverlay = pdfReaderSettings.pdfPageNumberOverlayVisible,
                                    onSelectPage = {
                                        goToPage(
                                            target = it,
                                            saveRichTextBeforePageChange = !isRichTextMode
                                        )
                                    },
                                    onCopySelection = ::copySelection,
                                    onHighlightSelection = ::highlightSelection,
                                    onExternalSearchSelection = {
                                        openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, it.text)
                                    },
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
                                            pageVerticalScrollState.scrollBy(-delta.y)
                                        }
                                    },
                                    onPageSizeChanged = { page, size ->
                                        paginatedPageCanvasSizes[page] = size
                                    },
                                    onPagePositioned = { page, offset ->
                                        paginatedPageRootOffsets[page] = offset
                                        if (page == paginatedSpreadPageIndices.firstOrNull()) {
                                            if (offset != paginatedPageRootOffset) {
                                                logPdfZoomSettle {
                                                    "page_layout seq=$pdfZoomSettleSequence mode=spread page=${page + 1} " +
                                                        "prevRoot=${paginatedPageRootOffset.formatLogOffset()} " +
                                                        "nextRoot=${offset.formatLogOffset()} scale=${scale.formatLogFloat()} " +
                                                        "preview=${spreadZoomPreview != null} h=${pageHorizontalScrollState.value} " +
                                                        "v=${pageVerticalScrollState.value}"
                                                }
                                            }
                                            paginatedPageRootOffset = offset
                                        }
                                    }
                                )
                            }
                        }
                        DesktopPdfPageScrubOverlay(
                            pageIndex = pageScrubPreview,
                            pageCount = document.pageCount,
                            pageLabel = pdfPageScrubPreviewLabel
                        )
                    }
                } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pdfViewportBackground, RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp))
                        .onSizeChanged { size -> pdfZoomViewportSize = size }
                        .onGloballyPositioned { coordinates ->
                            val rootOffset = coordinates.positionInRoot()
                            if (rootOffset != pdfZoomViewportRootOffset) {
                                logPdfZoomSettle {
                                    "viewport_layout seq=$pdfZoomSettleSequence mode=pagination " +
                                        "prev=${pdfZoomViewportRootOffset.formatLogOffset()} next=${rootOffset.formatLogOffset()} " +
                                        "scale=${scale.formatLogFloat()} preview=${pdfZoomPreview != null}"
                                }
                            }
                            pdfZoomViewportRootOffset = rootOffset
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
                    val paginatedPageDisplay = renderedPageIndex
                        ?.takeIf { displayPageIndex ->
                            renderedPage != null && desktopPdfRenderBelongsToPage(displayPageIndex, pageIndex)
                        }
                        ?.let { displayPageIndex ->
                        renderedPage?.let { render ->
                            DesktopPdfPaginatedPageDisplay(
                                pageIndex = displayPageIndex,
                                render = render
                            )
                        }
                    }
                    when {
                    renderError != null && paginatedPageDisplay?.pageIndex != pageIndex -> Text(
                        renderError ?: readerString("desktop_failed_render_page", "Failed to render page."),
                        color = MaterialTheme.colorScheme.error
                    )
                    paginatedPageDisplay != null -> {
                        Crossfade(
                            targetState = paginatedPageDisplay.pageIndex,
                            animationSpec = tween(DesktopPdfPaginationPageTurnAnimationMillis),
                            label = "DesktopPdfPaginatedPage"
                        ) { displayPageIndex ->
                        val displayPageIsCurrent = displayPageIndex == currentPdfPageIndex
                        val pageIndex = displayPageIndex
                        val currentPageRender = if (displayPageIndex == paginatedPageDisplay.pageIndex) {
                            paginatedPageDisplay.render
                        } else {
                            paginatedRenderCache[displayPageIndex]?.render ?: paginatedPageDisplay.render
                        }
                        val pageSize = document.pageSizes.getOrNull(pageIndex)
                        if (pageSize == null) {
                            Text(readerString("desktop_failed_render_page", "Failed to render page."), color = MaterialTheme.colorScheme.error)
                            return@Crossfade
                        }
                        val pageDisplayScale = zoomSpec.clamp(scale)
                        val pageWidthDp = with(density) { (pageSize.width * pageDisplayScale).toDp() }
                        val pageHeightDp = with(density) { (pageSize.height * pageDisplayScale).toDp() }
                        val predictedPageCanvasSize = IntSize(
                            width = (pageSize.width * pageDisplayScale).roundToInt().coerceAtLeast(1),
                            height = (pageSize.height * pageDisplayScale).roundToInt().coerceAtLeast(1)
                        )
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
                        val pageLayoutPrediction = pageZoomPreview?.let {
                            desktopPdfSinglePageLayoutPrediction(
                                viewportRootOffset = pdfZoomViewportRootOffset,
                                viewportSize = pdfZoomViewportSize,
                                pageCanvasSize = predictedPageCanvasSize,
                                horizontalScroll = pageHorizontalScrollState.value,
                                verticalScroll = pageVerticalScrollState.value,
                                paddingPx = with(density) { 24.dp.toPx() }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(pageWidthDp, pageHeightDp)
                                .onGloballyPositioned { coordinates ->
                                    val rootOffset = coordinates.positionInRoot()
                                    if (rootOffset != paginatedPageRootOffset) {
                                        logPdfZoomSettle {
                                            "page_layout seq=$pdfZoomSettleSequence mode=pagination page=${pageIndex + 1} " +
                                                "prevRoot=${paginatedPageRootOffset.formatLogOffset()} " +
                                                "nextRoot=${rootOffset.formatLogOffset()} scale=${scale.formatLogFloat()} " +
                                                "preview=${pageZoomPreview != null} h=${pageHorizontalScrollState.value} " +
                                                "v=${pageVerticalScrollState.value} canvas=${pageCanvasSize.formatLogSize()}"
                                        }
                                    }
                                    paginatedPageRootOffset = rootOffset
                                    paginatedPageRootOffsets[pageIndex] = rootOffset
                                }
                                .onSizeChanged { size ->
                                    if (pageCanvasSize != size) {
                                        logPdfZoomSettle {
                                            "page_size seq=$pdfZoomSettleSequence mode=pagination page=${pageIndex + 1} " +
                                                "prev=${pageCanvasSize.formatLogSize()} next=${size.formatLogSize()} " +
                                                "scale=${scale.formatLogFloat()} preview=${pageZoomPreview != null} " +
                                                "bitmap=${currentPageRender.width}x${currentPageRender.height}"
                                        }
                                        logPdfSelection(
                                            "layout page=${pageIndex + 1} " +
                                                "canvas=${size.formatLogSize()} bitmap=${currentPageRender.width}x${currentPageRender.height} " +
                                                "requestedScale=${scale.formatLogFloat()} displayScale=${pageDisplayScale.formatLogFloat()} " +
                                                "renderScale=${pageRenderScale.formatLogFloat()}"
                                        )
                                    }
                                    pageCanvasSize = size
                                    paginatedPageCanvasSizes[pageIndex] = size
                                }
                                .desktopPdfZoomPreviewLayer(
                                    preview = pageZoomPreview,
                                    currentZoom = scale,
                                    viewportRootOffset = pdfZoomViewportRootOffset,
                                    pageRootOffset = paginatedPageRootOffset,
                                    pageCanvasSize = pageCanvasSize,
                                    commitPageRootOffset = pageLayoutPrediction?.rootOffset,
                                    scrollBounds = pageZoomPreview?.let {
                                        DesktopPdfZoomScrollBounds(
                                            currentHorizontalScroll = pageHorizontalScrollState.value,
                                            maxHorizontalScroll = pageLayoutPrediction?.maxHorizontalScroll
                                                ?: pageHorizontalScrollState.maxValue,
                                            currentVerticalScroll = pageVerticalScrollState.value,
                                            maxVerticalScroll = pageLayoutPrediction?.maxVerticalScroll
                                                ?: pageVerticalScrollState.maxValue
                                        )
                                    }
                                )
                                .background(pdfThemeStyle.pageBackgroundColor, RoundedCornerShape(2.dp))
                                .pointerInput(
                                    pageIndex,
                                    pageCanvasSize,
                                    isTextSelectionMode,
                                    selectedTool,
                                    isRichTextMode,
                                    displayPageIsCurrent
                                ) {
                                    if (!displayPageIsCurrent || isRichTextMode) return@pointerInput
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val point = event.changes.firstOrNull()?.position ?: continue
                                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                                if (isTextSelectionMode) {
                                                    logPdfChromeTap {
                                                        "page_press source=paginated_inline_page page=${pageIndex + 1} " +
                                                            "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                            "consumedBefore=${event.changes.any { it.isConsumed }} " +
                                                            "selectionActive=${currentTextSelection != null} " +
                                                            "selectionMenuOpen=${selectionMenuOffset != null} " +
                                                            "selectedTool=$selectedTool richText=$isRichTextMode"
                                                    }
                                                }
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
                                                    logPdfChromeTap {
                                                        "page_press_consume source=paginated_inline_page page=${pageIndex + 1} " +
                                                            "reason=text_selection_highlight annotation=${highlightHit.id}"
                                                    }
                                                    selectAnnotation(highlightHit)
                                                    clearPdfInteractionState()
                                                    event.changes.forEach { it.consume() }
                                                    continue
                                                }
                                                if (selectedTool != PdfInkTool.TEXT) {
                                                    val linkTarget = document.linkAt(pageIndex, point, pageCanvasSize)
                                                    if (linkTarget != null) {
                                                        logPdfChromeTap {
                                                            "page_press_consume source=paginated_inline_page page=${pageIndex + 1} " +
                                                                "reason=link target=${linkTarget.formatLogTarget()}"
                                                        }
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
                                                    logPdfChromeTap {
                                                        "page_press_consume source=paginated_inline_page page=${pageIndex + 1} " +
                                                            "reason=embedded_annotation annotation=${embeddedHit.id}"
                                                    }
                                                    selectEmbeddedAnnotation(embeddedHit)
                                                    clearPdfInteractionState()
                                                    event.changes.forEach { it.consume() }
                                                } else if (
                                                    currentTextSelection != null &&
                                                    selectionMenuOffset == null
                                                ) {
                                                    logPdfChromeTap {
                                                        "page_press_passthrough source=paginated_inline_page page=${pageIndex + 1} " +
                                                            "action=clear_selection consumed=false"
                                                    }
                                                    selectionMenuOffset = null
                                                    textSelection = null
                                                    selectionStartHit = null
                                                    selectionEndHit = null
                                                } else if (isTextSelectionMode) {
                                                    logPdfChromeTap {
                                                        "page_press_passthrough source=paginated_inline_page page=${pageIndex + 1} " +
                                                            "action=none consumed=false"
                                                    }
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
                                    pageCanvasSize,
                                    isTextSelectionMode,
                                    isRichTextMode,
                                    displayPageIsCurrent
                                ) {
                                    if (!displayPageIsCurrent || isRichTextMode || !isTextSelectionMode) return@pointerInput
                                    detectDesktopPdfTextSelectionLongPress(
                                        source = "paginated_inline_page",
                                        pageIndex = pageIndex
                                    ) { point ->
                                        val selection = document.wordSelectionAt(pageIndex, point, pageCanvasSize)
                                        logPdfChromeTap {
                                            "long_press_selection source=paginated_inline_page page=${pageIndex + 1} " +
                                                "selectionFound=${selection != null} " +
                                                "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()}"
                                        }
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
                                }
                                .pointerInput(
                                    pageIndex,
                                    selectedTool,
                                    isTextSelectionMode,
                                    isRichTextMode,
                                    displayPageIsCurrent
                                ) {
                                    if (!displayPageIsCurrent || isRichTextMode || isTextSelectionMode || selectedTool != PdfInkTool.NONE) return@pointerInput
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
                                    displayPageIsCurrent,
                                    pageCanvasSize, currentPageRender.width,
                                    currentPageRender.height
                                ) {
                                    if (!displayPageIsCurrent || isRichTextMode) return@pointerInput
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
                                contentDescription = readerString("desktop_pdf_page_content_desc", "PDF page %1\$d", pageIndex + 1),
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
                                                logPdfChromeTap {
                                                    "selection_menu_scrim_tap source=paginated_inline_page page=${pageIndex + 1} " +
                                                        "consumedByScrim=true"
                                                }
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
                                showSpeak = cloudTtsControlsAvailable && aiByokSettings.sanitized().isCloudTtsAvailable,
                                showSearch = featurePolicy.externalLookup,
                                onClear = ::clearSelection
                            )
                        }
                        }
                    }
                    isRendering -> CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    renderError != null -> Text(
                        renderError ?: readerString("desktop_failed_render_page", "Failed to render page."),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                DesktopPdfPageScrubOverlay(
                    pageIndex = pageScrubPreview,
                    pageCount = document.pageCount,
                    pageLabel = pdfPageScrubPreviewLabel
                )
                }
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
            showPdfAiHub -> {
                DesktopAiHubSheet(
                    bookKey = pdfHubBookKey(),
                    bookTitle = pdfHubBookTitle(),
                    itemIndex = pageIndex,
                    itemTitle = readerString("pdf_page_short", "Page %1\$d", pageIndex + 1),
                    summaryCacheStore = summaryCacheStore,
                    summaryResult = pdfHubSummaryResult,
                    isSummaryLoading = isPdfHubSummaryLoading,
                    recapResult = null,
                    isRecapLoading = false,
                    recapProgressMessage = null,
                    onGenerateSummary = ::generatePdfHubSummary,
                    onClearSummary = ::clearPdfHubSummary,
                    onGenerateRecap = null,
                    onClearRecap = {},
                    onDismiss = { showPdfAiHub = false },
                    credits = credits,
                    showCredits = showPaidCredits
                )
            }
            selectedTextHighlight != null -> {
                DesktopReaderBottomSheet(
                    title = selectedTextHighlight.desktopSheetTitle(),
                    onDismiss = ::dismissSelectedTextHighlightSheet
                ) {
                    DesktopPdfAnnotationEditor(
                        annotation = selectedTextHighlight,
                        onUpdate = ::updateAnnotation,
                        onDelete = { deleteSelectedTextHighlight(selectedTextHighlight) },
                        onClose = ::dismissSelectedTextHighlightSheet,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(selectedTextHighlight.text))
                            dismissSelectedTextHighlightSheet()
                        },
                        showSearch = featurePolicy.externalLookup,
                        highlighterPalette = pdfHighlighterColors,
                        onHighlighterPaletteChange = ::updatePdfHighlighterPalette,
                        onSearch = {
                            openPdfExternalLookup(ReaderExternalLookupAction.SEARCH, selectedTextHighlight.text)
                            dismissSelectedTextHighlightSheet()
                        }
                    )
                }
            }
            selectedEmbeddedAnnotation != null -> {
                DesktopReaderBottomSheet(
                    title = readerString("desktop_pdf_comment", "PDF comment"),
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
                    onDismiss = {
                        dismissedPdfAiResultRequestId = pdfAiResultRequestId
                        pdfExtrasState = pdfExtrasState.copy(aiResult = ReaderAiResultState())
                    }
                )
            }
        }
        if (isPdfFileActionLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .zIndex(20_000f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showPdfSaveDialog) {
        DesktopPdfExportChoiceDialog(
            title = readerString("title_save_to_device", "Save to device"),
            message = readerString("desktop_choose_pdf_to_save", "Choose which PDF to save."),
            onOriginal = {
                showPdfSaveDialog = false
                savePdfCopy(SaveMode.ORIGINAL)
            },
            onAnnotated = {
                showPdfSaveDialog = false
                savePdfCopy(SaveMode.ANNOTATED)
            },
            onDismiss = { showPdfSaveDialog = false }
        )
    }

    pdfFileActionNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { pdfFileActionNotice = null },
            title = { Text(notice.title) },
            text = {
                Text(
                    text = notice.message,
                    color = if (notice.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(onClick = { pdfFileActionNotice = null }) {
                    Text(readerString("action_ok", "OK"))
                }
            }
        )
    }
}

@Composable
private fun desktopPdfPageLabel(
    pageIndex: Int,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    settings: ReaderSettings
): String {
    val pageRange = if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.pageRangeLabel(pageIndex, pageCount, settings)
    } else {
        "${pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) + 1}"
    }
    return if ('-' in pageRange) {
        readerString("desktop_pdf_pages_of_count", "Pages %1\$s of %2\$d", pageRange, pageCount)
    } else {
        readerString("desktop_pdf_page_of_count", "Page %1\$s of %2\$d", pageRange, pageCount)
    }
}

@Composable
private fun DesktopPdfExportChoiceDialog(
    title: String,
    message: String,
    onOriginal: () -> Unit,
    onAnnotated: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAnnotated) {
                Text(readerString("action_with_annotations", "With annotations"))
            }
        },
        dismissButton = {
            TextButton(onClick = onOriginal) {
                Text(readerString("action_original", "Original"))
            }
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        }
    )
}
