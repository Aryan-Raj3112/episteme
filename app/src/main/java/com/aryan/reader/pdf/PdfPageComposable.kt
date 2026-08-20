// PdfPageComposable
@file:Suppress(
    "RemoveRedundantQualifierName", "COMPOSE_APPLIER_CALL_MISMATCH", "UnusedVariable", "unused"
)
package com.aryan.reader.pdf

import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.PDF_MAX_ZOOM_SCALE
import com.aryan.reader.shared.pdf.pdfZoomRenderScale

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.aryan.reader.R
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrResult
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.PdfSelectionHandle
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfSelectionGeometry
import com.aryan.reader.shared.pdf.PdfTextSelectionEngine
import com.aryan.reader.shared.pdf.PdfTextSelectionRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Handle {
    START, END
}
private fun Throwable.readablePdfErrorDetail(): String {
    return localizedMessage?.takeIf { it.isNotBlank() }
        ?: javaClass.simpleName.takeIf { it.isNotBlank() }
        ?: "Unknown error"
}

private const val PDF_TILE_SIZE_DP = 256
private const val PDF_TARGET_TILE_BITMAP_SIZE_PX = 768
private const val PDF_MAX_VISIBLE_TILE_COUNT = 12
private const val PDF_TILE_IDLE_RENDER_DELAY_MS = 60L
private const val PDF_TILE_RENDER_IDLE_COOLDOWN_MS = 220L
private const val PDF_PAGINATION_PAN_FLING_MIN_VELOCITY = 600f
private const val PDF_PAGINATION_PAN_FLING_MULTIPLIER = 0.72f
private val pdfHighResTileRenderMutex = Mutex()

@Stable
data class StableHolder<T>(val item: T)

@Stable
data class PageStaticData(
    val bitmap: StableHolder<Bitmap?>,
    val tiles: StableHolder<List<PdfTile>>,
    val shouldDrawHighResTiles: Boolean,
    val effectiveScale: Float,
    val centeringOffsetX: Float,
    val centeringOffsetY: Float,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val targetWidth: Int,
    val targetHeight: Int,
    val colorFilter: StableHolder<ColorFilter?>,
    val isDarkMode: Boolean,
    val excludeImages: Boolean,
    val reverseColorMode: PdfReverseColorMode,
    val imageRects: StableHolder<List<android.graphics.Rect>>,
    val textureBitmap: StableHolder<ImageBitmap?>,
    val textureAlpha: Float,
    val textureBlendMode: BlendMode
)

@Stable
data class PageSelectionData(
    val pageLinks: StableHolder<List<PageLink>>,
    val showAllTextHighlights: Boolean,
    val actualBitmapWidthPx: Int,
    val actualBitmapHeightPx: Int,
    val mergedAllTextPageHighlightRects: StableHolder<List<Rect>>,
    val mergedTtsHighlightRects: StableHolder<List<Rect>>,
    val mergedSearchFocusedRects: StableHolder<List<Rect>>,
    val mergedSearchAllRects: StableHolder<List<Rect>>,
    val searchHighlightMode: SearchHighlightMode,
    val ocrHoverHighlights: StableHolder<List<RectF>>,
    val mergedSelectionRects: StableHolder<List<Rect>>,
    val centeringOffsetX: Float,
    val centeringOffsetY: Float,
    val linkHighlightColor: Color,
    val scrimColorForTextHighlight: Color,
    val allTextPageHighlightColor: Color,
    val ttsHighlightColor: Color,
    val selectionHighlightColor: Color,
    val pageIndex: Int,
    val userHighlightScreenRects: StableHolder<List<Pair<PdfUserHighlight, List<Rect>>>>,
    val customHighlightColors: StableHolder<Map<PdfHighlightColor, Color>>
)

@OptIn(FlowPreview::class)
@Suppress("unused")
@Composable
internal fun PdfPageComposable(
    pdfDocument: StableHolder<ReaderDocument>,
    documentKey: String,
    pageIndex: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    virtualPage: VirtualPage? = null,
    onScaleChanged: (Float) -> Unit,
    externalScale: Float = 1f,
    showAllTextHighlights: Boolean,
    onHighlightLoading: (Boolean) -> Unit,
    searchQuery: String = "",
    searchHighlightMode: SearchHighlightMode = SearchHighlightMode.ALL,
    searchResultToHighlight: SearchResult?,
    ocrHoverHighlights: StableHolder<List<RectF>> = StableHolder(emptyList()),
    onPreSingleTap: ((Offset) -> Boolean)? = null,
    onSingleTap: (Offset?) -> Unit,
    isProUser: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslateText: (String) -> Unit,
    onSearchText: (String) -> Unit,
    ttsHighlightData: TtsHighlightData?,
    onLinkClicked: (String) -> Unit,
    onInternalLinkClicked: (Int) -> Unit,
    isBookmarked: Boolean,
    onOcrStateChange: (Boolean) -> Unit,
    onBookmarkClick: () -> Unit,
    onOcrModelDownloading: () -> Unit = {},
    onTwoFingerSwipe: (direction: Int) -> Unit = {},
    placeholderBitmap: Bitmap? = null,
    isZoomEnabled: Boolean = true,
    isScrolling: Boolean = false,
    lazyListState: LazyListState? = null,
    isVerticalScroll: Boolean = false,
    showPageNumberOverlay: Boolean = true,
    visualScaleProvider: () -> Float = { 1f },
    clearSelectionTrigger: Long = 0L,
    resetZoomTrigger: Long = 0L,
    onTtsHighlightCenterCalculated: ((Float) -> Unit)? = null,
    onSearchHighlightCenterCalculated: ((Float) -> Unit)? = null,
    activeTheme: ReaderTheme = ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false),
    activeTextureAlpha: Float = 0.55f,
    excludeImages: Boolean = false,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onDoubleTapDragZoomStart: ((Offset) -> Unit)? = null,
    onDoubleTapDragZoom: ((Offset, Float) -> Unit)? = null,
    onDoubleTapDragZoomEnd: (() -> Unit)? = null,
    isEditMode: Boolean = false,
    drawingState: PdfDrawingState? = null,
    pageAnnotations: () -> List<PdfAnnotation> = { emptyList() },
    onDrawStart: (PdfPoint, Boolean) -> Unit = { _, _ -> },
    onDraw: (PdfPoint, Boolean) -> Unit = { _, _ -> },
    onDrawEnd: () -> Unit = {},
    visibleScreenRect: () -> IntRect? = { null },
    selectedTool: InkType = InkType.PEN,
    richTextController: RichTextController? = null,
    textBoxes: List<PdfTextBox> = emptyList(),
    selectedTextBoxId: String? = null,
    onTextBoxChange: (PdfTextBox) -> Unit = {},
    onTextBoxSelect: (String) -> Unit = {},
    onTextBoxDragStart: (PdfTextBox, Offset, Offset) -> Unit = { _, _, _ -> },
    onTextBoxDrag: (Offset) -> Unit = {},
    onTextBoxDragEnd: () -> Unit = {},
    onDragPageTurn: (Int) -> Unit = {},
    draggingBoxId: String? = null,
    isScrollLocked: Boolean = false,
    isVisible: Boolean = true,
    isActivePage: Boolean = true,
    isBubbleZoomModeActive: Boolean = false,
    isStylusOnlyMode: Boolean = false,
    stylusButtonHovering: Boolean = false,
    isAutoScrollPlaying: Boolean = false,
    isHighlighterSnapEnabled: Boolean = false,
    userHighlights: List<PdfUserHighlight> = emptyList(),
    onHighlightAdd: (Int, Pair<Int, Int>, String, PdfHighlightColor, HighlightStyle) -> Unit = { _,_,_,_,_ -> },
    onHighlightUpdate: (String, PdfHighlightColor, HighlightStyle?) -> Unit = { _,_,_ -> },
    onHighlightDelete: (String) -> Unit = {},
    onNoteRequested: (String?) -> Unit = {},
    onTts: (Int, Int) -> Unit = { _, _ -> },
    activeToolThickness: Float = 0f,
    eraserToolThickness: Float = 0f,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onPaletteClick: (() -> Unit)? = null,
    lockedState: Triple<Float, Float, Float>? = null,
    onZoomAndPanChanged: ((Float, Offset) -> Unit)? = null,
    onDetectBubbles: suspend (Int, Bitmap) -> List<SpeechBubble> = { _, _ -> emptyList() },
    onShowPanelPopup: (Bitmap) -> Unit = {}
) {
    val pdfDocumentItem = pdfDocument.item
    val targetPageId = remember(documentKey, virtualPage, pageIndex) {
        pdfRenderPageId(documentKey, pageIndex, virtualPage)
    }
    var bitmapState by remember(targetPageId) { mutableStateOf(PdfThumbnailCache.get(targetPageId)) }
    var currentRenderedPageId by remember(targetPageId) { mutableStateOf<String?>(null) }
    var isLoadingPage by remember(targetPageId) { mutableStateOf(true) }
    var pageErrorMessage by remember(targetPageId) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val viewConfiguration = LocalViewConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    var isStylusEraserOverride by remember { mutableStateOf(false) }

    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var ocrRipplePosition by remember { mutableStateOf<Offset?>(null) }

    var isTransforming by remember { mutableStateOf(false) }
    var isPaginationPageGestureActive by remember { mutableStateOf(false) }
    var isPageTileRenderIdleCooldownActive by remember { mutableStateOf(false) }
    val initialCamera = initialPdfPageCamera(
        isZoomEnabled = isZoomEnabled,
        isVerticalScroll = isVerticalScroll,
        isScrollLocked = isScrollLocked,
        lockedState = lockedState
    )
    var scale by remember(targetPageId) { mutableFloatStateOf(initialCamera.first) }
    var offset by remember(targetPageId) { mutableStateOf(initialCamera.second) }
    var paginationPanFlingJob by remember { mutableStateOf<Job?>(null) }
    val shouldPauseHighResTileRendering =
        isScrolling ||
            isTransforming ||
            isPaginationPageGestureActive ||
            paginationPanFlingJob != null ||
            isPageTileRenderIdleCooldownActive
    val pageMotionActive =
        isScrolling ||
            isTransforming ||
            isPaginationPageGestureActive ||
            paginationPanFlingJob != null
    var hasAppliedLockedPaginationState by remember(targetPageId) {
        mutableStateOf(initialCamera.second != Offset.Zero || initialCamera.first != 1f)
    }
    val shouldReportCamera = shouldReportPdfPageCamera(
        isZoomEnabled = isZoomEnabled,
        isVerticalScroll = isVerticalScroll,
        isScrollLocked = isScrollLocked,
        lockedState = lockedState,
        hasAppliedLockedState = hasAppliedLockedPaginationState
    )

    LaunchedEffect(scale, offset, shouldReportCamera) {
        if (shouldReportCamera) {
            onZoomAndPanChanged?.invoke(scale, offset)
        }
    }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnPreSingleTap by rememberUpdatedState(onPreSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnDoubleTapDragZoomStart by rememberUpdatedState(onDoubleTapDragZoomStart)
    val currentOnDoubleTapDragZoom by rememberUpdatedState(onDoubleTapDragZoom)
    val currentOnDoubleTapDragZoomEnd by rememberUpdatedState(onDoubleTapDragZoomEnd)

    val effectiveScale = if (isZoomEnabled && !isVerticalScroll) scale else externalScale
    val effectiveOffset = if (isZoomEnabled && !isVerticalScroll) offset else Offset.Zero
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)

    var eraserPosition by remember { mutableStateOf<Offset?>(null) }

    SideEffect {
        if (drawingState?.currentAnnotation?.pageIndex == pageIndex) {
            Timber.tag("PdfDrawPerf").v(
                "PAGE EFFECTIVE SCALE: Page $pageIndex = $effectiveScale (ZoomEnabled=$isZoomEnabled)"
            )
        }
    }

    val isPdfPage = virtualPage == null || virtualPage is VirtualPage.PdfPage
    val pdfPageIndex = (virtualPage as? VirtualPage.PdfPage)?.pdfIndex ?: pageIndex

    var tiles by remember(targetPageId) { mutableStateOf<List<PdfTile>>(emptyList()) }
    val tileSizeDp = PDF_TILE_SIZE_DP.dp
    val tileSizePx = with(LocalDensity.current) { tileSizeDp.toPx().toInt() }
    val latestEffectiveScale by rememberUpdatedState(effectiveScale)
    val latestEffectiveOffset by rememberUpdatedState(effectiveOffset)
    val latestIsScrolling by rememberUpdatedState(isScrolling)
    val latestIsAutoScrollPlaying by rememberUpdatedState(isAutoScrollPlaying)
    val latestShouldPauseHighResTileRendering by rememberUpdatedState(shouldPauseHighResTileRendering)

    LaunchedEffect(isVerticalScroll, pageMotionActive) {
        if (isVerticalScroll) {
            if (isPageTileRenderIdleCooldownActive) {
                isPageTileRenderIdleCooldownActive = false
            }
            return@LaunchedEffect
        }

        if (pageMotionActive) {
            isPageTileRenderIdleCooldownActive = true
        } else if (isPageTileRenderIdleCooldownActive) {
            delay(PDF_TILE_RENDER_IDLE_COOLDOWN_MS)
            isPageTileRenderIdleCooldownActive = false
        }
    }

    SideEffect {
        Timber.tag("PdfDrawPerf")
            .v("PAGE RECOMPOSE: Page $pageIndex (EffectiveScale: $effectiveScale)")
    }

    val teardropWidthDp = 24.dp
    val teardropHeightDp = 24.dp
    val teardropWidthPxState = remember(density) {
        derivedStateOf { with(density) { teardropWidthDp.toPx() / visualScaleProvider() } }
    }
    val teardropHeightPxState = remember(density) {
        derivedStateOf { with(density) { teardropHeightDp.toPx() / visualScaleProvider() } }
    }

    val handleTouchExpansionDp = 8.dp
    val handleTouchWidthPxState = remember(density) {
        derivedStateOf {
            with(density) {
                (teardropWidthDp + handleTouchExpansionDp).toPx() / visualScaleProvider()
            }
        }
    }
    val handleTouchHeightPxState = remember(density) {
        derivedStateOf {
            with(density) {
                (teardropHeightDp + handleTouchExpansionDp).toPx() / visualScaleProvider()
            }
        }
    }

    val selectionCharRange = remember(targetPageId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var activeDraggingHandle by remember(targetPageId) { mutableStateOf<Handle?>(null) }
    var selectedWordScreenRects by remember(targetPageId) { mutableStateOf<List<Rect>>(emptyList()) }
    val startHandleContentPosition = remember(targetPageId) { mutableStateOf<Offset?>(null) }
    val endHandleContentPosition = remember(targetPageId) { mutableStateOf<Offset?>(null) }

    var actualBitmapWidthPx by remember(targetPageId) { mutableIntStateOf(0) }
    var actualBitmapHeightPx by remember(targetPageId) { mutableIntStateOf(0) }
    var currentPageRotation by remember(targetPageId) { mutableIntStateOf(0) }

    val needsTilingNow = shouldRenderPdfHighResTiles(
        effectiveScale = effectiveScale,
        targetWidthPx = actualBitmapWidthPx,
        targetHeightPx = actualBitmapHeightPx,
        isVerticalScroll = isVerticalScroll,
        isActivePage = isActivePage
    )

    val canvasWidthPx = remember { mutableFloatStateOf(0f) }
    val canvasHeightPx = remember { mutableFloatStateOf(0f) }

    val isDarkMode = activeTheme.isDark || activeTheme.id == "reverse"
    // The reverse-color mode only has meaning for the PDF reverse theme. Keep a
    // persisted mode inert while another theme is selected.
    val effectiveReverseColorMode = if (activeTheme.id == "reverse") {
        reverseColorMode
    } else {
        PdfReverseColorMode.RGB
    }
    val colorFilter = remember(activeTheme, effectiveReverseColorMode) {
        when (activeTheme.id) {
            "no_theme", "system" -> null
            "reverse" if (effectiveReverseColorMode == PdfReverseColorMode.RGB) -> {
                val colorMatrix = floatArrayOf(
                    -1f,  0f,  0f,  0f, 255f,
                    0f, -1f,  0f,  0f, 255f,
                    0f,  0f, -1f,  0f, 255f,
                    0f,  0f,  0f,  1f,   0f
                )
                ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
            }
            // The Okular lightness/luma modes are baked into the page and tile
            // bitmaps off the main thread. Applying the legacy theme matrix on
            // top would erase hue/chroma and effectively transform twice.
            "reverse" -> null
            else -> {
                val bgR = activeTheme.backgroundColor.red * 255f
                val bgG = activeTheme.backgroundColor.green * 255f
                val bgB = activeTheme.backgroundColor.blue * 255f

                val fgR = activeTheme.textColor.red * 255f
                val fgG = activeTheme.textColor.green * 255f
                val fgB = activeTheme.textColor.blue * 255f

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

    val backgroundColor = remember(activeTheme, isVerticalScroll) {
        if (activeTheme.id == "no_theme" || activeTheme.id == "system") {
            if (isVerticalScroll) Color.White else Color.Black
        } else if (activeTheme.id == "reverse") {
            if (isVerticalScroll) Color.Black else Color.White
        } else {
            activeTheme.backgroundColor
        }
    }
    val textureBitmap = remember(activeTheme.textureId) {
        loadReaderTextureBitmap(context, activeTheme.textureId)
    }
    val effectiveTextureAlpha = remember(activeTheme.textureId, activeTextureAlpha) {
        if (activeTheme.textureId == null) 0f else activeTextureAlpha.coerceIn(0f, 1f)
    }
    val textureBlendMode = remember(activeTheme.textureId, activeTheme.isDark, activeTheme.id) {
        if (activeTheme.isDark || activeTheme.id == "reverse") BlendMode.Screen else BlendMode.Multiply
    }

    val centeringOffsetX by remember(canvasWidthPx.floatValue, actualBitmapWidthPx) {
        derivedStateOf { (canvasWidthPx.floatValue - actualBitmapWidthPx) / 2f }
    }
    val centeringOffsetY by remember(canvasHeightPx.floatValue, actualBitmapHeightPx) {
        derivedStateOf { (canvasHeightPx.floatValue - actualBitmapHeightPx) / 2f }
    }

    LaunchedEffect(centeringOffsetX, centeringOffsetY, pageIndex) {

    }

    var showMagnifier by remember { mutableStateOf(false) }
    var magnifierBitmapCenterTarget by remember { mutableStateOf(Offset.Zero) }
    val magnifierZoomFactor = 2.0f

    var customMenuState by remember(targetPageId) { mutableStateOf<CustomPdfMenuState?>(null) }

    val inputScale = if (isZoomEnabled && !isVerticalScroll) scale else 1f
    val inputOffset = if (isZoomEnabled && !isVerticalScroll) offset else Offset.Zero
    val latestInputScale by rememberUpdatedState(inputScale)

    val screenToContentCoordinates: (Offset) -> Offset = { screenOffset ->
        val screenCenter = Offset(canvasWidthPx.floatValue / 2f, canvasHeightPx.floatValue / 2f)
        val pCanvas = ((screenOffset - inputOffset - screenCenter) / inputScale) + screenCenter
        val contentOffset = pCanvas - Offset(centeringOffsetX, centeringOffsetY)
        contentOffset
    }

    val contentToScreenCoordinates: (Offset) -> Offset = { contentOffset ->
        val pCanvas = contentOffset + Offset(centeringOffsetX, centeringOffsetY)
        val screenCenter = Offset(canvasWidthPx.floatValue / 2f, canvasHeightPx.floatValue / 2f)
        val screenOffset = (pCanvas - screenCenter) * inputScale + screenCenter + inputOffset
        screenOffset
    }
    val latestScreenToContentCoordinates by rememberUpdatedState(screenToContentCoordinates)
    var isOneHandZooming by remember(targetPageId) { mutableStateOf(false) }
    val latestIsOneHandZooming by rememberUpdatedState(isOneHandZooming)

    var detectedBubbles by remember(targetPageId) { mutableStateOf<List<SpeechBubble>>(emptyList()) }
    var expandedBubbleIndex by remember(targetPageId) { mutableIntStateOf(-1) }
    var animatingBubbleIndex by remember(targetPageId) { mutableIntStateOf(-1) }
    val bubbleExpansionProgress = remember(targetPageId) { Animatable(0f) }
    var isDetectingBubbles by remember(targetPageId) { mutableStateOf(false) }
    var expandedBubbleRender by remember(targetPageId) { mutableStateOf<ExpandedBubbleRender?>(null) }
    val currentDetectedBubbles by rememberUpdatedState(detectedBubbles)
    val currentExpandedBubbleIndex by rememberUpdatedState(expandedBubbleIndex)
    val currentBubbleZoomModeActive by rememberUpdatedState(isBubbleZoomModeActive)
    val bubbleTapSlopPx = with(density) { 18.dp.toPx() }

    LaunchedEffect(expandedBubbleIndex) {
        if (expandedBubbleIndex != -1) {
            if (animatingBubbleIndex != -1 && animatingBubbleIndex != expandedBubbleIndex) {
                bubbleExpansionProgress.animateTo(0f, tween(150))
            }
            animatingBubbleIndex = expandedBubbleIndex
            bubbleExpansionProgress.animateTo(1f, tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        } else {
            bubbleExpansionProgress.animateTo(0f, tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            animatingBubbleIndex = -1
        }
    }

    LaunchedEffect(
        isBubbleZoomModeActive,
        isActivePage,
        isPdfPage,
        pdfPageIndex,
        bitmapState,
        actualBitmapWidthPx,
        actualBitmapHeightPx
    ) {
        Timber.tag("BubbleZoom").d("LaunchedEffect triggered. modeActive=$isBubbleZoomModeActive, activePage=$isActivePage, hasBitmap=${bitmapState != null}, dims=${actualBitmapWidthPx}x${actualBitmapHeightPx}")

        if (isBubbleZoomModeActive && isActivePage && isPdfPage && bitmapState != null && actualBitmapWidthPx > 0 && actualBitmapHeightPx > 0) {
            Timber.tag("BubbleZoom").d("Conditions met. Starting detection...")
            isDetectingBubbles = true
            try {
                val rawBubbles = onDetectBubbles(pdfPageIndex, bitmapState!!)
                Timber.tag("BubbleZoom").d("Detection complete. Found ${rawBubbles.size} raw bubbles.")

                // NEW: Scale bubbles down from render bitmap space to logical screen space
                val scaleX = actualBitmapWidthPx.toFloat() / bitmapState!!.width.toFloat()
                val scaleY = actualBitmapHeightPx.toFloat() / bitmapState!!.height.toFloat()

                val logicalBubbles = rawBubbles.map { b ->
                    b.copy(bounds = android.graphics.RectF(
                        b.bounds.left * scaleX,
                        b.bounds.top * scaleY,
                        b.bounds.right * scaleX,
                        b.bounds.bottom * scaleY
                    ))
                }

                val rowHeight = actualBitmapHeightPx * 0.1f
                detectedBubbles = logicalBubbles.sortedWith(compareBy<SpeechBubble> { (it.bounds.centerY() / rowHeight).roundToInt() }.thenBy { it.bounds.centerX() })
                expandedBubbleIndex = -1

                Timber.tag("BubbleZoom").d("Sorted logical bubbles count: ${detectedBubbles.size}")
            } catch (e: Exception) {
                Timber.tag("BubbleZoom").e(e, "Bubble detection failed with exception")
            } finally {
                isDetectingBubbles = false
            }
        } else {
            Timber.tag("BubbleZoom").d("Conditions NOT met or mode disabled. Clearing bubbles.")
            detectedBubbles = emptyList()
            expandedBubbleIndex = -1
            safeRecyclePdfBitmap(expandedBubbleRender?.bitmap)
            expandedBubbleRender = null
            if (
                shouldResetPdfZoomAfterBubbleZoomCleanup(
                    isBubbleZoomModeActive = isBubbleZoomModeActive,
                    scale = scale,
                    isVerticalScroll = isVerticalScroll,
                    isZoomEnabled = isZoomEnabled,
                    isScrollLocked = isScrollLocked
                )
            ) {
                coroutineScope.launch {
                    Animatable(scale).animateTo(1f, tween(300)) {
                        scale = this.value
                        offset = Offset.Zero
                        onScaleChanged(scale)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        animatingBubbleIndex,
        detectedBubbles,
        actualBitmapWidthPx,
        actualBitmapHeightPx,
        canvasWidthPx.floatValue,
        canvasHeightPx.floatValue,
        isBubbleZoomModeActive,
        isPdfPage,
        pdfPageIndex
    ) {
        val previousRender = expandedBubbleRender
        expandedBubbleRender = null
        safeRecyclePdfBitmap(previousRender?.bitmap)

        if (!isBubbleZoomModeActive || !isPdfPage || animatingBubbleIndex !in detectedBubbles.indices) {
            return@LaunchedEffect
        }

        val bubble = detectedBubbles[animatingBubbleIndex]
        val zoomFactor = computeDynamicBubbleZoomFactor(
            bubbleBounds = bubble.bounds,
            viewportWidth = canvasWidthPx.floatValue.coerceAtLeast(actualBitmapWidthPx.toFloat()),
            viewportHeight = canvasHeightPx.floatValue.coerceAtLeast(actualBitmapHeightPx.toFloat())
        )
        val renderScale = (zoomFactor * 1.2f).coerceAtLeast(1.6f)
        val renderedBubble = renderExpandedBubbleBitmap(
            document = pdfDocumentItem,
            pageIndex = pdfPageIndex,
            bubbleBounds = bubble.bounds,
            pageWidth = actualBitmapWidthPx,
            pageHeight = actualBitmapHeightPx,
            renderScale = renderScale
        )

        if (renderedBubble != null) {
            expandedBubbleRender = ExpandedBubbleRender(
                bitmap = renderedBubble,
                zoomFactor = zoomFactor
            )
        }
    }

    DisposableEffect(targetPageId) {
        onDispose {
            val currentBitmap = bitmapState
            val cachedBitmap = PdfThumbnailCache.get(targetPageId)
            if (currentBitmap != null && !currentBitmap.isRecycled && currentBitmap !== cachedBitmap) {
                safeRecyclePdfBitmap(currentBitmap)
            }
            safeRecyclePdfBitmap(expandedBubbleRender?.bitmap)
        }
    }

    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current

    // OCR
    var ocrVisionTextForSelection by remember(targetPageId) { mutableStateOf<OcrResult?>(null) }
    var isPerformingOcrForSelection by remember { mutableStateOf(false) }
    var selectionMethodUsed by remember { mutableStateOf(PdfSelectionMethod.PDFIUM) }
    var ocrSelectionSymbolIndices by remember(targetPageId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var allOcrSymbolsForSelection by remember(targetPageId) { mutableStateOf<List<OcrSymbolInfo>>(emptyList()) }

    var highlightedTextScreenRects by remember(targetPageId) { mutableStateOf<List<Rect>>(emptyList()) }
    val ttsHighlightColor = Color(0xFFFFECB3).copy(alpha = 0.4f)

    var allTextPageHighlightRects by remember(targetPageId) { mutableStateOf<List<Rect>>(emptyList()) }

    var accumulatedKeyboardOffset by remember { mutableFloatStateOf(0f) }

    val allTextPageHighlightColor = if (isDarkMode) {
        Color(0xFFFFEB3B).copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0f)
    }

    val scrimColorForTextHighlight = if (isDarkMode) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = 0.4f)
    }

    val selectionHighlightColor = Color(0x6633B5E5)
    val mergedSelectionRects =
        remember(selectedWordScreenRects) { mergeRectsIntoLines(selectedWordScreenRects) }
    val mergedTtsHighlightRects =
        remember(highlightedTextScreenRects, centeringOffsetX, centeringOffsetY) {
            mergeRectsIntoLines(highlightedTextScreenRects)
        }
    val mergedAllTextPageHighlightRects =
        remember(allTextPageHighlightRects, centeringOffsetX, centeringOffsetY) {
            mergeRectsIntoLines(allTextPageHighlightRects)
        }

    var searchHighlightRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val searchHighlightColor = Color(0xFFFFAB00).copy(alpha = 0.5f)
    val mergedSearchHighlightRects =
        remember(searchHighlightRects) { mergeRectsIntoLines(searchHighlightRects) }

    var pageLinks by remember(targetPageId) { mutableStateOf<List<PageLink>>(emptyList()) }
    val linkHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val linkVerticalPaddingPx = remember(density) { with(density) { 10.dp.toPx().toInt() } }

    var userHighlightScreenRects by remember { mutableStateOf<List<Pair<PdfUserHighlight, List<Rect>>>>(emptyList()) }

    LaunchedEffect(userHighlights, actualBitmapWidthPx, actualBitmapHeightPx, currentPageRotation, virtualPage) {
        if (!isPdfPage || actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0 || userHighlights.isEmpty()) {
            userHighlightScreenRects = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                pdfDocumentItem.openPage(pdfPageIndex)?.use { page ->
                    val mapped = userHighlights.map { highlight ->
                        val screenRects = highlight.bounds.mapNotNull { pdfRectF ->
                            page.mapRectToDevice(
                                startX = 0,
                                startY = 0,
                                sizeX = actualBitmapWidthPx,
                                sizeY = actualBitmapHeightPx,
                                rotate = currentPageRotation,
                                coords = pdfRectF
                            ).takeIf { it.width() > 0 && it.height() > 0 }
                        }
                        highlight to screenRects
                    }
                    withContext(Dispatchers.Main) {
                        userHighlightScreenRects = mapped
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to map user highlights to screen rects")
            }
        }
    }

    LaunchedEffect(isPerformingOcrForSelection) { onOcrStateChange(isPerformingOcrForSelection) }

    LaunchedEffect(
        showAllTextHighlights, pageIndex, pdfDocumentItem, actualBitmapWidthPx, scale, isScrolling, virtualPage
    ) {
        if (!isPdfPage) {
            if (allTextPageHighlightRects.isNotEmpty()) {
                allTextPageHighlightRects = emptyList()
            }
            return@LaunchedEffect
        }

        if (isScrolling) {
            return@LaunchedEffect
        }

        if (effectiveScale > 1f) {
            if (allTextPageHighlightRects.isNotEmpty()) {
                allTextPageHighlightRects = emptyList()
            }
            return@LaunchedEffect
        }

        if (!showAllTextHighlights) {
            if (allTextPageHighlightRects.isNotEmpty()) {
                allTextPageHighlightRects = emptyList()
            }
            return@LaunchedEffect
        }

        if (actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0) {
            return@LaunchedEffect
        }

        onHighlightLoading(true)

        var rects: List<Rect> = emptyList()
        var pdfiumSucceeded = false
        var tempPage: ReaderPage? = null

        try {
            withContext(Dispatchers.IO) {
                tempPage = pdfDocumentItem.openPage(pdfPageIndex)
                tempPage?.openTextPage()?.use { textPage ->
                    val charCount = textPage.textPageCountChars()
                    if (charCount > 0) {
                        val pdfRectsF =
                            textPage.textPageGetRectsForRanges(intArrayOf(0, charCount))?.map {
                                it.rect
                            } ?: emptyList()

                        if (pdfRectsF.isNotEmpty()) {
                            val mappedScreenRects = pdfRectsF.mapNotNull { pdfRectF ->
                                tempPage?.mapRectToDevice(
                                    startX = 0,
                                    startY = 0,
                                    sizeX = actualBitmapWidthPx,
                                    sizeY = actualBitmapHeightPx,
                                    rotate = currentPageRotation,
                                    coords = pdfRectF
                                )?.takeIf { it.width() > 0 && it.height() > 0 }
                            }
                            if (mappedScreenRects.isNotEmpty()) {
                                rects = mappedScreenRects
                                pdfiumSucceeded = true
                                Timber.d(
                                    "Found ${rects.size} rects for all-text highlight via PDFium."
                                )
                            }
                        } else {
                            Timber.d(
                                "PDFium's textPageGetRectsForRanges returned no rects for page $pageIndex."
                            )
                        }
                    } else {
                        Timber.d("PDFium found 0 characters on page $pageIndex.")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting all-text highlights via PDFium")
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    try {
                        tempPage?.close()
                    } catch (e: Exception) {
                        Timber.w("Error closing tempPage in highlights: ${e.message}")
                    }
                }
            }
        }

        if (!pdfiumSucceeded && bitmapState != null) {
            Timber.d("PDFium failed for all-text highlight, trying OCR.")
            try {
                val visionText =
                    OcrHelper.extractTextFromBitmap(bitmapState!!, onOcrModelDownloading)
                if (visionText != null) {
                    val ocrRects = visionText.textBlocks.flatMap { block ->
                        block.lines.asSequence().mapNotNull { it.boundingBox }
                    }.toList()
                    if (ocrRects.isNotEmpty()) {
                        rects = ocrRects
                        Timber.d("Found ${rects.size} rects for all-text highlight via OCR.")
                    } else {
                        Timber.d("OCR ran but found no text lines for page $pageIndex.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting all-text highlights via OCR")
            }
        }
        allTextPageHighlightRects = rects
        onHighlightLoading(false)
    }

    @Suppress("VariableNeverRead") var embeddedAnnotations by remember(targetPageId) { mutableStateOf<List<EmbeddedAnnotation>>(emptyList()) }
    var standardAnnotScreenRects by remember(targetPageId) { mutableStateOf<List<Pair<EmbeddedAnnotation, Rect>>>(emptyList()) }
    var imageScreenRects by remember(targetPageId) { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }
    val placeholderRenderBitmap = rememberPdfReverseBitmap(
        bitmap = placeholderBitmap,
        mode = effectiveReverseColorMode,
        protectedRects = if (excludeImages) imageScreenRects else emptyList(),
        targetWidth = actualBitmapWidthPx.takeIf { it > 0 } ?: placeholderBitmap?.width ?: 0,
        targetHeight = actualBitmapHeightPx.takeIf { it > 0 } ?: placeholderBitmap?.height ?: 0,
    )

    LaunchedEffect(pageIndex, pdfDocumentItem, actualBitmapWidthPx, actualBitmapHeightPx, virtualPage) {
        if (!isPdfPage || actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0) {
            if (pageLinks.isNotEmpty()) pageLinks = emptyList()
            if (standardAnnotScreenRects.isNotEmpty()) standardAnnotScreenRects = emptyList()
            if (imageScreenRects.isNotEmpty()) imageScreenRects = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val allLinks = mutableListOf<PageLink>()
            var finalDisplayList = emptyList<EmbeddedAnnotation>()
            var mappedAnnots = emptyList<Pair<EmbeddedAnnotation, Rect>>()
            var mappedImageRects = emptyList<android.graphics.Rect>()
            val annotLink = 2

            try {
                pdfDocumentItem.openPage(pdfPageIndex)?.use { pageWrapper ->

                    // 1. Extract Links (Method 1: Annotations)
                    try {
                        val annotationLinks = pageWrapper.getLinks()
                        if (annotationLinks.isNotEmpty()) {
                            val mappedAnnotationLinks = annotationLinks.mapNotNull { link ->
                                val uri = link.uri
                                val destPageIdx = link.destPageIdx
                                val bounds = link.bounds

                                if (isActionablePdfLinkTarget(uri, destPageIdx)) {
                                    val deviceRect = pageWrapper.mapRectToDevice(
                                        startX = 0, startY = 0,
                                        sizeX = actualBitmapWidthPx, sizeY = actualBitmapHeightPx,
                                        rotate = currentPageRotation, coords = bounds
                                    )
                                    buildPdfPageLink(
                                        highlightBounds = deviceRect.toPdfIntBounds(),
                                        verticalTapPaddingPx = linkVerticalPaddingPx,
                                        url = uri,
                                        destPageIdx = destPageIdx,
                                        source = LinkSource.ANNOTATION,
                                    )
                                } else null
                            }
                            allLinks.addAll(mappedAnnotationLinks)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error fetching annotation links")
                    }

                    // 2. Extract Links (Method 2: Text)
                    try {
                        pageWrapper.openTextPage().use { textPage ->
                            textPage.loadWebLink()?.use { webLinks ->
                                val webLinkCount = webLinks.countWebLinks()
                                for (linkIndex in 0 until webLinkCount) {
                                    val rawUrl = webLinks.getURL(linkIndex, 2048)
                                    val url = rawUrl?.substringBefore('\u0000')
                                    if (url.isNullOrBlank()) continue

                                    val rectCount = webLinks.countRects(linkIndex)
                                    for (rectIndex in 0 until rectCount) {
                                        val pdfRect = webLinks.getRect(linkIndex, rectIndex)
                                        val deviceRect = pageWrapper.mapRectToDevice(
                                            0, 0, actualBitmapWidthPx, actualBitmapHeightPx,
                                            currentPageRotation, pdfRect
                                        )
                                        buildPdfPageLink(
                                            highlightBounds = deviceRect.toPdfIntBounds(),
                                            verticalTapPaddingPx = linkVerticalPaddingPx,
                                            url = url,
                                            destPageIdx = null,
                                            source = LinkSource.TEXT_CONTENT,
                                        )?.let(allLinks::add)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error fetching web links")
                    }

                    val nativeExtraction = (pageWrapper as? PdfPageWrapper)?.extractNativePageOverlays(
                        bitmapWidthPx = actualBitmapWidthPx,
                        bitmapHeightPx = actualBitmapHeightPx,
                        pageRotation = currentPageRotation,
                        pageIndex = pageIndex,
                        linkAnnotationSubtype = annotLink
                    )

                    if (nativeExtraction == null) {
                        Timber.tag("PdfCommentDebug").w("Page $pageIndex: Native overlay extraction unavailable for ${pageWrapper::class.java.simpleName}.")
                    } else {
                        if (!nativeExtraction.resolvedNativePointer) {
                            Timber.tag("PdfCommentDebug").w("Page $pageIndex: Failed to resolve native page pointer.")
                        }
                        mappedImageRects = nativeExtraction.imageScreenRects
                        finalDisplayList = nativeExtraction.embeddedAnnotations
                        mappedAnnots = nativeExtraction.annotationScreenRects
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Failed to load links and annotations for page $pdfPageIndex")
                }
            }

            withContext(Dispatchers.Main) {
                pageLinks = allLinks
                embeddedAnnotations = finalDisplayList
                standardAnnotScreenRects = mappedAnnots
                imageScreenRects = mappedImageRects
            }
        }
    }

    LaunchedEffect(isVisible, pageIndex) {
        if (!isVisible && !isVerticalScroll) {
            if (bitmapState != null) {
                Timber.d("Page $pageIndex hidden. Releasing bitmap to save memory.")
                val old = bitmapState
                bitmapState = null
                @Suppress("ControlFlowWithEmptyBody") if (old != null && old !== PdfThumbnailCache.get(targetPageId)) { }
            }
        }
    }

    SideEffect {
        if (effectiveScale > 1f) {
            Timber.tag("PdfZoomDiagnostics").v(
                """
            Page $pageIndex Stats:
            - Internal Scale: $scale | Effective Scale: $effectiveScale
            - Offset: $offset
            - Bitmap Dims: ${actualBitmapWidthPx}x${actualBitmapHeightPx}
            - Canvas Dims: ${canvasWidthPx.floatValue}x${canvasHeightPx.floatValue}
            - Centering: X=$centeringOffsetX, Y=$centeringOffsetY
            """.trimIndent()
            )
        }
    }

    LaunchedEffect(
        needsTilingNow,
        actualBitmapWidthPx,
        actualBitmapHeightPx,
        canvasWidthPx.floatValue,
        canvasHeightPx.floatValue,
        isVerticalScroll,
        virtualPage,
        isActivePage
    ) {
        var lastTileDiagLogMs = 0L
        if (!needsTilingNow) {
            if (tiles.isNotEmpty()) {
                if (isVerticalScroll) {
                    PdfVerticalPerfLog.d(
                        "tile-clear page=$pageIndex reason=tiling-disabled count=${tiles.size} scale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                    )
                }
                val oldTiles = tiles
                tiles = emptyList()
                withContext(Dispatchers.IO) {
                    oldTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                }
            }
            return@LaunchedEffect
        }

        val screenWidth = canvasWidthPx.floatValue
        val screenHeight = canvasHeightPx.floatValue

        if (actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0 || screenWidth == 0f || screenHeight == 0f) {
            if (isVerticalScroll) {
                PdfVerticalPerfLog.w(
                    "tile-skip page=$pageIndex reason=empty-dimensions bitmap=${actualBitmapWidthPx}x$actualBitmapHeightPx screen=${PdfVerticalPerfLog.xy(screenWidth, screenHeight)}"
                )
            }
            return@LaunchedEffect
        }

        var page: ReaderPage? = null

        if (!isPdfPage) {
            if (tiles.isNotEmpty()) {
                if (isVerticalScroll) {
                    PdfVerticalPerfLog.d(
                        "tile-clear page=$pageIndex reason=virtual-page count=${tiles.size}"
                    )
                }
                val oldTiles = tiles
                tiles = emptyList()
                withContext(Dispatchers.IO) {
                    oldTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                }
            }
            return@LaunchedEffect
        }

        try {
            page = withContext(Dispatchers.IO) { pdfDocumentItem.openPage(pdfPageIndex) }
            if (isVerticalScroll) {
                PdfVerticalPerfLog.d(
                    "tile-loop-open page=$pageIndex pdfPage=$pdfPageIndex bitmap=${actualBitmapWidthPx}x$actualBitmapHeightPx " +
                        "screen=${PdfVerticalPerfLog.xy(screenWidth, screenHeight)} scale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                )
            }

            snapshotFlow {
                val rect = visibleScreenRect()
                val observedScale = latestEffectiveScale
                val pauseMarker = if (latestShouldPauseHighResTileRendering) 1 else 0
                if (isVerticalScroll && rect != null) {
                    val qTop = rect.top / (tileSizePx / 2)
                    val qLeft = rect.left / (tileSizePx / 2)
                    val qBottom = rect.bottom / (tileSizePx / 2)
                    val qRight = rect.right / (tileSizePx / 2)
                    listOf(qTop, qLeft, qBottom, qRight, (pdfZoomRenderScale(observedScale) * 10f).roundToInt(), pauseMarker)
                } else if (!isVerticalScroll) {
                    val observedOffset = latestEffectiveOffset
                    val pivotX = screenWidth / 2f
                    val pivotY = screenHeight / 2f
                    val pxTl = (((0 - observedOffset.x) - pivotX) / observedScale + pivotX) - centeringOffsetX
                    val pyTl = (((0 - observedOffset.y) - pivotY) / observedScale + pivotY) - centeringOffsetY
                    val pxBr = (((screenWidth - observedOffset.x) - pivotX) / observedScale + pivotX) - centeringOffsetX
                    val pyBr = (((screenHeight - observedOffset.y) - pivotY) / observedScale + pivotY) - centeringOffsetY

                    val qTop = pyTl.toInt() / (tileSizePx / 2)
                    val qLeft = pxTl.toInt() / (tileSizePx / 2)
                    val qBottom = pyBr.toInt() / (tileSizePx / 2)
                    val qRight = pxBr.toInt() / (tileSizePx / 2)
                    listOf(qTop, qLeft, qBottom, qRight, (pdfZoomRenderScale(observedScale) * 10f).roundToInt(), pauseMarker)
                } else {
                    null
                }
            }.conflate().collectLatest { _ ->

                val tileCalcStart = System.nanoTime()
                if (!isActive) return@collectLatest

                val renderScale = pdfZoomRenderScale(latestEffectiveScale)
                val renderOffset = latestEffectiveOffset
                val logicalTileSizePx = minOf(
                    tileSizePx,
                    (PDF_TARGET_TILE_BITMAP_SIZE_PX / renderScale).toInt().coerceAtLeast(64)
                )

                val currentVisibleRect = visibleScreenRect()

                val pxTl: Float
                val pxBr: Float
                val pyTl: Float
                val pyBr: Float

                if (isVerticalScroll) {
                    if (currentVisibleRect != null) {
                        pxTl = currentVisibleRect.left.toFloat()
                        pyTl = currentVisibleRect.top.toFloat()
                        pxBr = currentVisibleRect.right.toFloat()
                        pyBr = currentVisibleRect.bottom.toFloat()
                    } else {
                        if (tiles.isNotEmpty()) {
                            val oldTiles = tiles
                            tiles = emptyList()
                            withContext(Dispatchers.IO) {
                                oldTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                            }
                        }
                        return@collectLatest
                    }
                } else {
                    val pivotX = screenWidth / 2f
                    val pivotY = screenHeight / 2f

                    pxTl = (((0 - renderOffset.x) - pivotX) / renderScale + pivotX) - centeringOffsetX
                    pyTl = (((0 - renderOffset.y) - pivotY) / renderScale + pivotY) - centeringOffsetY
                    pxBr = (((screenWidth - renderOffset.x) - pivotX) / renderScale + pivotX) - centeringOffsetX
                    pyBr = (((screenHeight - renderOffset.y) - pivotY) / renderScale + pivotY) - centeringOffsetY
                }

                val visibleBitmapRect = Rect(pxTl.toInt(), pyTl.toInt(), pxBr.toInt(), pyBr.toInt())
                val inset = if (renderScale > 2f) 0 else -logicalTileSizePx
                visibleBitmapRect.inset(inset, inset)

                val candidateTileIds = mutableSetOf<Int>()
                val cols = (actualBitmapWidthPx + logicalTileSizePx - 1) / logicalTileSizePx
                val startCol = (visibleBitmapRect.left / logicalTileSizePx).coerceAtLeast(0)
                val endCol = ((visibleBitmapRect.right + logicalTileSizePx - 1) / logicalTileSizePx).coerceAtMost(cols)
                val startRow = (visibleBitmapRect.top / logicalTileSizePx).coerceAtLeast(0)
                val endRow = ((visibleBitmapRect.bottom + logicalTileSizePx - 1) / logicalTileSizePx).coerceAtMost(
                    (actualBitmapHeightPx + logicalTileSizePx - 1) / logicalTileSizePx
                )

                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        candidateTileIds.add(row * cols + col)
                    }
                }
                val visibleCenterX = visibleBitmapRect.exactCenterX()
                val visibleCenterY = visibleBitmapRect.exactCenterY()
                val requiredTileIds = candidateTileIds
                    .sortedBy { tileId ->
                        val row = tileId / cols
                        val col = tileId % cols
                        val dx = (col * logicalTileSizePx + logicalTileSizePx / 2f) - visibleCenterX
                        val dy = (row * logicalTileSizePx + logicalTileSizePx / 2f) - visibleCenterY
                        dx * dx + dy * dy
                    }
                    .take(PDF_MAX_VISIBLE_TILE_COUNT)
                    .toSet()

                val currentTileIds = tiles.map { it.tileId }.toSet()

                val validCurrentTileIds = tiles.filter { it.renderScale == renderScale }.map { it.tileId }.toSet()
                val tilesToRenderIds = requiredTileIds - validCurrentTileIds
                val tilesToRecycleIds = currentTileIds - requiredTileIds

                val duration = (System.nanoTime() - tileCalcStart) / 1_000_000f
                val nowMs = System.currentTimeMillis()
                val shouldLogHighResTile = isVerticalScroll || (!isVerticalScroll && renderScale > 1f)
                val shouldLogTileSample = shouldLogHighResTile && nowMs - lastTileDiagLogMs >= PdfVerticalPerfLog.SAMPLE_INTERVAL_MS
                val tileLogMode = if (isVerticalScroll) "vertical" else "pagination"
                if (shouldLogTileSample) {
                    lastTileDiagLogMs = nowMs
                    PdfVerticalPerfLog.d(
                        "tile-scan mode=$tileLogMode page=$pageIndex scale=${PdfVerticalPerfLog.f(renderScale)} scrolling=$latestIsScrolling pause=$latestShouldPauseHighResTileRendering auto=$latestIsAutoScrollPlaying " +
                            "visible=$visibleBitmapRect required=${requiredTileIds.size} render=${tilesToRenderIds.size} recycle=${tilesToRecycleIds.size} " +
                            "cached=${tiles.size} calcMs=${PdfVerticalPerfLog.f(duration)}"
                    )
                }
                if (duration > 2f) {
                    Timber.tag("PdfPerformance").d(
                        "Page $pageIndex | Tile Calc took ${duration}ms | Tiles Needed: ${requiredTileIds.size}"
                    )
                }

                if (tilesToRecycleIds.isNotEmpty()) {
                    val (tilesToRecycle, tilesToKeep) = tiles.partition { it.tileId in tilesToRecycleIds }
                    tiles = tilesToKeep
                    withContext(Dispatchers.IO) {
                        tilesToRecycle.forEach { PdfBitmapPool.recycle(it.bitmap) }
                    }
                }

                if (latestShouldPauseHighResTileRendering) {
                    if (shouldLogTileSample) {
                        PdfVerticalPerfLog.d(
                            "tile-render-paused mode=$tileLogMode page=$pageIndex reason=motion scale=${PdfVerticalPerfLog.f(renderScale)} " +
                                "missing=${tilesToRenderIds.size} current=${tiles.size}"
                        )
                    }
                    return@collectLatest
                }

                if (requiredTileIds != validCurrentTileIds) {
                    if (tilesToRenderIds.isNotEmpty()) {
                        if (shouldLogHighResTile) {
                            PdfVerticalPerfLog.d(
                                "tile-render-queued mode=$tileLogMode page=$pageIndex missing=${tilesToRenderIds.size} scale=${PdfVerticalPerfLog.f(renderScale)} delay=${PDF_TILE_IDLE_RENDER_DELAY_MS}ms"
                            )
                        }
                        delay(PDF_TILE_IDLE_RENDER_DELAY_MS)
                        if (!isActive) return@collectLatest
                        if (latestShouldPauseHighResTileRendering) {
                            if (shouldLogHighResTile) {
                                PdfVerticalPerfLog.d(
                                    "tile-render-canceled mode=$tileLogMode page=$pageIndex reason=motion-resumed missing=${tilesToRenderIds.size} scale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                )
                            }
                            return@collectLatest
                        }
                        if (pdfZoomRenderScale(latestEffectiveScale) != renderScale) {
                            if (shouldLogHighResTile) {
                                PdfVerticalPerfLog.d(
                                    "tile-render-canceled mode=$tileLogMode page=$pageIndex reason=scale-changed-before-native missing=${tilesToRenderIds.size} queuedScale=${PdfVerticalPerfLog.f(renderScale)} latestScale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                )
                            }
                            return@collectLatest
                        }

                        val renderStartNanos = PdfVerticalPerfLog.nowNanos()
                        val renderedTiles = withContext(Dispatchers.IO) {
                            val newTiles = mutableListOf<PdfTile>()
                            tilesToRenderIds.forEach { tileId ->
                                if (!isActive) return@forEach

                                yield()

                                val row = tileId / cols
                                val col = tileId % cols
                                val tileRect = Rect(
                                    col * logicalTileSizePx,
                                    row * logicalTileSizePx,
                                    minOf((col + 1) * logicalTileSizePx, actualBitmapWidthPx),
                                    minOf((row + 1) * logicalTileSizePx, actualBitmapHeightPx)
                                )
                                val tileRenderScale = renderScale
                                val tileRenderWidth = (tileRect.width() * tileRenderScale).toInt().coerceAtLeast(1)
                                val tileRenderHeight = (tileRect.height() * tileRenderScale).toInt().coerceAtLeast(1)

                                val tileBitmap = PdfBitmapPool.get(tileRenderWidth, tileRenderHeight)

                                val fullPageRenderWidth = (actualBitmapWidthPx * tileRenderScale).toInt()
                                val fullPageRenderHeight = (actualBitmapHeightPx * tileRenderScale).toInt()
                                val tileRenderX = (col * logicalTileSizePx * tileRenderScale).toInt()
                                val tileRenderY = (row * logicalTileSizePx * tileRenderScale).toInt()

                                val tilePage = page
                                if (tilePage == null) {
                                    PdfBitmapPool.recycle(tileBitmap)
                                    return@forEach
                                }

                                var didRenderTile = false
                                val tileRenderWaitStartNanos = PdfVerticalPerfLog.nowNanos()
                                var singleTileStartNanos = tileRenderWaitStartNanos
                                pdfHighResTileRenderMutex.withLock {
                                    val tileRenderWaitMs = PdfVerticalPerfLog.elapsedMs(tileRenderWaitStartNanos)
                                    if (shouldLogHighResTile && tileRenderWaitMs >= 16L) {
                                        PdfVerticalPerfLog.d(
                                            "tile-render-wait mode=$tileLogMode page=$pageIndex tile=$tileId duration=${tileRenderWaitMs}ms"
                                        )
                                    }
                                    if (!isActive) return@withLock
                                    if (latestShouldPauseHighResTileRendering) {
                                        if (shouldLogHighResTile) {
                                            PdfVerticalPerfLog.d(
                                                "tile-render-canceled mode=$tileLogMode page=$pageIndex reason=motion-started-before-native tile=$tileId scale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                            )
                                        }
                                        return@withLock
                                    }
                                    if (pdfZoomRenderScale(latestEffectiveScale) != renderScale) {
                                        if (shouldLogHighResTile) {
                                            PdfVerticalPerfLog.d(
                                                "tile-render-canceled mode=$tileLogMode page=$pageIndex reason=scale-changed-at-native tile=$tileId queuedScale=${PdfVerticalPerfLog.f(renderScale)} latestScale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                            )
                                        }
                                        return@withLock
                                    }
                                    singleTileStartNanos = PdfVerticalPerfLog.nowNanos()
                                    tilePage.renderPageBitmap(
                                        bitmap = tileBitmap,
                                        startX = -tileRenderX,
                                        startY = -tileRenderY,
                                        drawSizeX = fullPageRenderWidth,
                                        drawSizeY = fullPageRenderHeight,
                                        renderAnnot = true
                                    )
                                    didRenderTile = true
                                }
                                if (!didRenderTile) {
                                    PdfBitmapPool.recycle(tileBitmap)
                                    return@forEach
                                }
                                val singleTileMs = PdfVerticalPerfLog.elapsedMs(singleTileStartNanos)
                                if (shouldLogHighResTile && singleTileMs >= 16L) {
                                    PdfVerticalPerfLog.d(
                                        "tile-render-slow mode=$tileLogMode page=$pageIndex tile=$tileId duration=${singleTileMs}ms " +
                                            "tileBitmap=${tileBitmap.width}x${tileBitmap.height} full=${fullPageRenderWidth}x$fullPageRenderHeight scale=${PdfVerticalPerfLog.f(tileRenderScale)}"
                                    )
                                }

                                newTiles += PdfTile(tileBitmap, tileRect, tileId, renderScale)
                            }
                            newTiles
                        }
                        val renderMs = PdfVerticalPerfLog.elapsedMs(renderStartNanos)
                        if (shouldLogHighResTile) {
                            PdfVerticalPerfLog.d(
                                "tile-render-finished mode=$tileLogMode page=$pageIndex requested=${tilesToRenderIds.size} rendered=${renderedTiles.size} " +
                                    "duration=${renderMs}ms scale=${PdfVerticalPerfLog.f(renderScale)} stillScrolling=$latestIsScrolling paused=$latestShouldPauseHighResTileRendering"
                            )
                        }

                        if (!isActive) {
                            withContext(Dispatchers.IO) {
                                renderedTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                            }
                            return@collectLatest
                        }
                        if (renderedTiles.isNotEmpty() && latestShouldPauseHighResTileRendering) {
                            if (shouldLogHighResTile) {
                                PdfVerticalPerfLog.d(
                                    "tile-render-discarded mode=$tileLogMode page=$pageIndex reason=motion-before-commit rendered=${renderedTiles.size} scale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                )
                            }
                            withContext(Dispatchers.IO) {
                                renderedTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                            }
                            return@collectLatest
                        }
                        if (renderedTiles.isNotEmpty() && pdfZoomRenderScale(latestEffectiveScale) != renderScale) {
                            if (shouldLogHighResTile) {
                                PdfVerticalPerfLog.d(
                                    "tile-render-discarded mode=$tileLogMode page=$pageIndex reason=scale-changed-before-commit rendered=${renderedTiles.size} queuedScale=${PdfVerticalPerfLog.f(renderScale)} latestScale=${PdfVerticalPerfLog.f(latestEffectiveScale)}"
                                )
                            }
                            withContext(Dispatchers.IO) {
                                renderedTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                            }
                            return@collectLatest
                        }

                        if (renderedTiles.isNotEmpty()) {
                            val renderedIds = renderedTiles.map { it.tileId }.toSet()
                            val replacedTiles = tiles.filter { it.tileId in renderedIds }
                            tiles = tiles.filterNot { it.tileId in renderedIds } + renderedTiles

                            if (replacedTiles.isNotEmpty()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    replacedTiles.forEach { PdfBitmapPool.recycle(it.bitmap) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Error during tiling process for page $pageIndex")
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    try {
                        page?.close()
                    } catch (e: Exception) {
                        Timber.w("Error closing tile page: ${e.message}")
                    }
                }
            }
        }
    }

    var searchFocusedRects by remember(targetPageId) { mutableStateOf<List<Rect>>(emptyList()) }
    var searchAllRects by remember(targetPageId) { mutableStateOf<List<Rect>>(emptyList()) }

    var keyboardAdjustmentOriginalOffset by remember(targetPageId) { mutableStateOf<Float?>(null) }

    val mergedSearchFocusedRects = remember(searchFocusedRects) { searchFocusedRects }
    val mergedSearchAllRects = remember(searchAllRects) { searchAllRects }

    // Colors
    val searchFocusedColor = Color(0xFFFF6D00).copy(alpha = 0.5f) // Bold Orange
    val searchAllColor = Color(0xFFFFEB3B).copy(alpha = 0.4f) // Standard Yellow

    LaunchedEffect(
        searchQuery, searchResultToHighlight, actualBitmapWidthPx, actualBitmapHeightPx, virtualPage
    ) {
        if (!isPdfPage) {
            searchFocusedRects = emptyList()
            searchAllRects = emptyList()
            return@LaunchedEffect
        }

        if (actualBitmapWidthPx == 0 || searchQuery.isBlank()) {
            searchFocusedRects = emptyList()
            searchAllRects = emptyList()
            return@LaunchedEffect
        }

        val isTargetOnPage = searchResultToHighlight?.locationInSource == pageIndex
        var foundAll: List<Rect> = emptyList()
        var foundFocused: List<Rect> = emptyList()

        withContext(Dispatchers.IO) {
            try {
                pdfDocumentItem.openPage(pdfPageIndex)?.use { page ->
                    page.openTextPage().use { textPage ->
                        val charCount = textPage.textPageCountChars()
                        if (charCount > 0) {
                            val fullText = textPage.textPageGetText(0, charCount)
                            if (!fullText.isNullOrBlank()) {
                                val occurrences = mutableListOf<Int>()
                                try {
                                    val regex = Regex("(?i)\\b${Regex.escape(searchQuery)}")
                                    val matches = regex.findAll(fullText)
                                    matches.forEach { matchResult ->
                                        occurrences.add(matchResult.range.first)
                                    }
                                } catch (_: Exception) {
                                    var lastIndex = -1
                                    while (true) {
                                        lastIndex = fullText.indexOf(
                                            searchQuery, lastIndex + 1, ignoreCase = true
                                        )
                                        if (lastIndex == -1) break
                                        occurrences.add(lastIndex)
                                    }
                                }

                                if (occurrences.isNotEmpty()) {
                                    val queryLen = searchQuery.length
                                    val allRectsRaw = occurrences.flatMap { startIndex ->
                                        textPage.textPageGetRectsForRanges(
                                            intArrayOf(startIndex, queryLen)
                                        )?.map { it.rect } ?: emptyList()
                                    }

                                    foundAll = allRectsRaw.mapNotNull { pdfRectF ->
                                        page.mapRectToDevice(
                                            startX = 0,
                                            startY = 0,
                                            sizeX = actualBitmapWidthPx,
                                            sizeY = actualBitmapHeightPx,
                                            rotate = currentPageRotation,
                                            coords = pdfRectF
                                        ).takeIf {
                                            it.width() > 0 && it.height() > 0
                                        }
                                    }

                                    if (isTargetOnPage) {
                                        val targetIdx =
                                            searchResultToHighlight.occurrenceIndexInLocation
                                        if (targetIdx >= 0 && targetIdx < occurrences.size) {
                                            val startIndex = occurrences[targetIdx]
                                            val focusedRectsRaw =
                                                textPage.textPageGetRectsForRanges(
                                                    intArrayOf(startIndex, queryLen)
                                                )?.map { it.rect } ?: emptyList()

                                            foundFocused = focusedRectsRaw.mapNotNull { pdfRectF ->
                                                page.mapRectToDevice(
                                                    startX = 0,
                                                    startY = 0,
                                                    sizeX = actualBitmapWidthPx,
                                                    sizeY = actualBitmapHeightPx,
                                                    rotate = currentPageRotation,
                                                    coords = pdfRectF
                                                ).takeIf {
                                                    it.width() > 0 && it.height() > 0
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Search Highlight: Failed to get rects for page $pageIndex")
            }
        }

        searchAllRects = foundAll
        searchFocusedRects = foundFocused

        if (foundFocused.isNotEmpty() && onSearchHighlightCenterCalculated != null) {
            val unionRect = RectF()
            if (foundFocused.isNotEmpty()) {
                unionRect.set(foundFocused[0])
                for (i in 1 until foundFocused.size) {
                    unionRect.union(RectF(foundFocused[i]))
                }
                onSearchHighlightCenterCalculated(unionRect.centerY())
            }
        }
    }

    fun findClosestOcrSymbolIndex(symbols: List<OcrSymbolInfo>, x: Float, y: Float): Int {
        return PdfSelectionGeometry.nearestBoundsIndex(
            bounds = symbols.map { info ->
                info.symbol.boundingBox?.let { box ->
                    com.aryan.reader.shared.pdf.PdfPageBounds(
                        box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()
                    )
                }
            },
            pointX = x,
            pointY = y
        )
    }

    fun updateOcrSymbolSelectionRectsAndHandles(indices: Pair<Int, Int>?) {
        if (indices == null || allOcrSymbolsForSelection.isEmpty() || indices.first >= indices.second) {
            selectedWordScreenRects = emptyList()
            startHandleContentPosition.value = null
            endHandleContentPosition.value = null
            return
        }

        val selectedSymbols = allOcrSymbolsForSelection.subList(indices.first, indices.second)
        if (selectedSymbols.isEmpty()) {
            selectedWordScreenRects = emptyList()
            startHandleContentPosition.value = null
            endHandleContentPosition.value = null
            return
        }

        selectedWordScreenRects = selectedSymbols.mapNotNull { it.symbol.boundingBox }

        val firstRect = selectedSymbols.first().symbol.boundingBox!!
        val lastRect = selectedSymbols.last().symbol.boundingBox!!

        startHandleContentPosition.value =
            Offset(firstRect.left.toFloat(), firstRect.bottom.toFloat())
        endHandleContentPosition.value = Offset(lastRect.right.toFloat(), lastRect.bottom.toFloat())
    }

    suspend fun updateSelectionVisuals(
        doc: ReaderDocument,
        pageIdx: Int,
        charRange: Pair<Int, Int>?,
        currentBitmapWidth: Int,
        currentBitmapHeight: Int,
        rotation: Int,
        providedPage: ReaderPage? = null,
        providedTextPage: ReaderTextPage? = null
    ) {
        if (charRange == null || currentBitmapWidth == 0 || currentBitmapHeight == 0) {
            withContext(Dispatchers.Main) {
                selectedWordScreenRects = emptyList()
                startHandleContentPosition.value = null
                endHandleContentPosition.value = null
            }
            return
        }

        withContext(Dispatchers.IO) {
            var localPage: ReaderPage? = null
            var localTextPage: ReaderTextPage? = null

            try {
                val pageToUse: ReaderPage
                val textPageToUse: ReaderTextPage

                if (providedPage != null && providedTextPage != null) {
                    pageToUse = providedPage
                    textPageToUse = providedTextPage
                } else {
                    localPage = doc.openPage(pageIdx)
                    localTextPage = localPage?.openTextPage()
                    if (localPage == null || localTextPage == null) {
                        withContext(Dispatchers.Main) {
                            selectedWordScreenRects = emptyList()
                            startHandleContentPosition.value = null
                            endHandleContentPosition.value = null
                        }
                        return@withContext
                    }
                    pageToUse = localPage
                    textPageToUse = localTextPage
                }

                val (startIndex, endIndex) = charRange
                if (startIndex >= endIndex) {
                    withContext(Dispatchers.Main) {
                        selectedWordScreenRects = emptyList()
                        startHandleContentPosition.value = null
                        endHandleContentPosition.value = null
                    }
                    return@withContext
                }

                val length = endIndex - startIndex
                val wordPdfRectsF =
                    textPageToUse.textPageGetRectsForRanges(intArrayOf(startIndex, length))?.map {
                        it.rect
                    } ?: emptyList()

                if (wordPdfRectsF.isNotEmpty()) {
                    val mappedScreenRects = wordPdfRectsF.mapNotNull { pdfRectF ->
                        val screenRect = pageToUse.mapRectToDevice(
                            startX = 0,
                            startY = 0,
                            sizeX = currentBitmapWidth,
                            sizeY = currentBitmapHeight,
                            rotate = rotation,
                            coords = pdfRectF
                        )
                        if (screenRect.width() > 0 && screenRect.height() > 0) screenRect
                        else {
                            Timber.d(
                                "updateSelectionVisuals: Filtering out invalid screen rect: $screenRect"
                            )
                            null
                        }
                    }

                    withContext(Dispatchers.Main) {
                        selectedWordScreenRects = mappedScreenRects

                        if (mappedScreenRects.isNotEmpty()) {
                            val firstRect = mappedScreenRects.first()
                            val lastRect = mappedScreenRects.last()

                            startHandleContentPosition.value =
                                Offset(firstRect.left.toFloat(), firstRect.bottom.toFloat())
                            endHandleContentPosition.value =
                                Offset(lastRect.right.toFloat(), lastRect.bottom.toFloat())
                        } else {
                            selectedWordScreenRects = emptyList()
                            startHandleContentPosition.value = null
                            endHandleContentPosition.value = null
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        selectedWordScreenRects = emptyList()
                        startHandleContentPosition.value = null
                        endHandleContentPosition.value = null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating selection visuals for page $pageIdx, range $charRange: $e")
                withContext(Dispatchers.Main) {
                    selectedWordScreenRects = emptyList()
                    startHandleContentPosition.value = null
                    endHandleContentPosition.value = null
                }
            } finally {
                if (providedPage == null && providedTextPage == null) {
                    withContext(NonCancellable) {
                        try {
                            localTextPage?.close()
                        } catch (_: Exception) {
                        }
                        try {
                            localPage?.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(clearSelectionTrigger) {
        if (clearSelectionTrigger != 0L) {
            if (customMenuState != null || selectionCharRange.value != null || ocrSelectionSymbolIndices != null) {
                customMenuState = null
                selectionCharRange.value = null
                ocrSelectionSymbolIndices = null
                updateSelectionVisuals(
                    pdfDocumentItem,
                    pdfPageIndex,
                    null,
                    actualBitmapWidthPx,
                    actualBitmapHeightPx,
                    currentPageRotation
                )
            }
        }
    }

    LaunchedEffect(resetZoomTrigger) {
        if (resetZoomTrigger != 0L && scale > 1f && isZoomEnabled && !isVerticalScroll && !isScrollLocked) {
            coroutineScope.launch {
                val startScale = scale
                val startOffset = offset
                Animatable(0f).animateTo(
                    1f, animationSpec = tween(durationMillis = 300)
                ) {
                    val progress = value
                    scale = androidx.compose.ui.util.lerp(
                        startScale, 1f, progress
                    )
                    offset = androidx.compose.ui.geometry.lerp(
                        startOffset, Offset.Zero, progress
                    )
                    onScaleChanged(scale)
                }
                if (scale <= 1.05f) {
                    scale = 1f
                    offset = Offset.Zero
                    onScaleChanged(scale)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates = it }
            .pointerInput(
                pdfDocumentItem,
                pageIndex,
                actualBitmapWidthPx,
                actualBitmapHeightPx,
                currentPageRotation,
                centeringOffsetX,
                centeringOffsetY,
                scale,
                offset,
                isTransforming,
                isVerticalScroll,
                inputScale,
                inputOffset,
                isEditMode
            ) {
                if (isTransforming) return@pointerInput
                if (isEditMode) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragStartedOnHandle = false
                    val shp = startHandleContentPosition.value
                    val ehp = endHandleContentPosition.value
                    val handleTouchWidthPx = handleTouchWidthPxState.value
                    val handleTouchHeightPx = handleTouchHeightPxState.value

                    if (shp != null) {
                        val handleScreenPos = contentToScreenCoordinates(shp)
                        val handleRect = Rect(
                            (handleScreenPos.x - handleTouchWidthPx / 2).toInt(),
                            handleScreenPos.y.toInt(),
                            (handleScreenPos.x + handleTouchWidthPx / 2).toInt(),
                            (handleScreenPos.y + handleTouchHeightPx).toInt()
                        )
                        if (handleRect.contains(
                                down.position.x.toInt(), down.position.y.toInt()
                            )
                        ) {
                            activeDraggingHandle = Handle.START
                            dragStartedOnHandle = true
                            down.consume()
                            customMenuState = null
                            Timber.d("PointerInput: Press on START teardrop handle")
                        }
                    }

                    if (!dragStartedOnHandle && ehp != null) {
                        val handleScreenPos = contentToScreenCoordinates(ehp)
                        val handleRect = Rect(
                            (handleScreenPos.x - handleTouchWidthPx / 2).toInt(),
                            handleScreenPos.y.toInt(),
                            (handleScreenPos.x + handleTouchWidthPx / 2).toInt(),
                            (handleScreenPos.y + handleTouchHeightPx).toInt()
                        )
                        if (handleRect.contains(
                                down.position.x.toInt(), down.position.y.toInt()
                            )
                        ) {
                            activeDraggingHandle = Handle.END
                            dragStartedOnHandle = true
                            down.consume()
                            customMenuState = null
                            Timber.d("PointerInput: Press on END teardrop handle")
                        }
                    }

                    if (dragStartedOnHandle) {
                        Timber.d(
                            "PointerInput: Drag started on handle $activeDraggingHandle"
                        )
                        showMagnifier = true
                        customMenuState = null

                        val rects = selectedWordScreenRects
                        // (keep the magnifier target logic here as
                        // is)
                        if (rects.isNotEmpty()) {
                            val relevantRect = when (activeDraggingHandle) {
                                Handle.START -> rects.first()
                                Handle.END -> rects.last()
                                else -> null
                            }
                            val handlePos = when (activeDraggingHandle) {
                                Handle.START -> startHandleContentPosition.value
                                Handle.END -> endHandleContentPosition.value
                                else -> null
                            }
                            if (relevantRect != null && handlePos != null) {
                                magnifierBitmapCenterTarget = Offset(
                                    x = handlePos.x, y = relevantRect.exactCenterY()
                                )
                            }
                        } else {
                            val initialHandlePos = when (activeDraggingHandle) {
                                Handle.START -> startHandleContentPosition.value
                                Handle.END -> endHandleContentPosition.value
                                else -> null
                            }
                            initialHandlePos?.let { contentPos ->
                                magnifierBitmapCenterTarget = contentPos
                            }
                        }

                        val dragEventChannel = Channel<Offset>(Channel.CONFLATED)

                        val dragWorker = coroutineScope.launch(Dispatchers.IO) {
                            var pageForDrag: ReaderPage? = null
                            var textPageForDrag: ReaderTextPage? = null

                            try {
                                if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                                    if (isPdfPage) {
                                        pageForDrag = pdfDocumentItem.openPage(
                                            pdfPageIndex
                                        )
                                        textPageForDrag = pageForDrag?.openTextPage()
                                    }
                                }

                                for (dragPosition in dragEventChannel) {
                                    if (activeDraggingHandle != null) {
                                        if (selectionMethodUsed == PdfSelectionMethod.OCR) {
                                            if (allOcrSymbolsForSelection.isNotEmpty()) {
                                                val touchInContentCoords =
                                                    screenToContentCoordinates(
                                                        dragPosition
                                                    )
                                                val touchXInBitmap = touchInContentCoords.x
                                                val touchYInBitmap = touchInContentCoords.y

                                                val targetSymbolIndex = findClosestOcrSymbolIndex(
                                                    allOcrSymbolsForSelection,
                                                    touchXInBitmap,
                                                    touchYInBitmap
                                                )

                                                if (targetSymbolIndex != -1) {
                                                    ocrSelectionSymbolIndices?.let { currentRange ->
                                                        val sharedHandle = when (val activeHandle = activeDraggingHandle) {
                                                            Handle.START -> PdfSelectionHandle.START
                                                            Handle.END -> PdfSelectionHandle.END
                                                            null -> null
                                                        }
                                                        sharedHandle?.let { handle ->
                                                            val update = PdfTextSelectionEngine.extendRange(
                                                                pageCharCount = allOcrSymbolsForSelection.size,
                                                                current = PdfTextSelectionRange(
                                                                    currentRange.first,
                                                                    currentRange.second
                                                                ),
                                                                activeHandle = handle,
                                                                newCharIndex = targetSymbolIndex
                                                            )
                                                            activeDraggingHandle = when (update.activeHandle) {
                                                                PdfSelectionHandle.START -> Handle.START
                                                                PdfSelectionHandle.END -> Handle.END
                                                            }
                                                            ocrSelectionSymbolIndices =
                                                                update.range.start to update.range.end
                                                        }
                                                        withContext(
                                                            Dispatchers.Main
                                                        ) {
                                                            updateOcrSymbolSelectionRectsAndHandles(
                                                                ocrSelectionSymbolIndices
                                                            )
                                                        }
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    val currentActiveHandle = activeDraggingHandle
                                                    val currentRects = selectedWordScreenRects
                                                    if (currentRects.isNotEmpty()) {
                                                        val relevantRect =
                                                            when (currentActiveHandle) {
                                                                Handle.START -> currentRects.first()
                                                                Handle.END -> currentRects.last()
                                                                null -> null
                                                            }
                                                        val handleContentPos =
                                                            when (currentActiveHandle) {
                                                                Handle.START -> startHandleContentPosition.value
                                                                Handle.END -> endHandleContentPosition.value
                                                                null -> null
                                                            }
                                                        if (relevantRect != null && handleContentPos != null) {
                                                            magnifierBitmapCenterTarget = Offset(
                                                                x = handleContentPos.x,
                                                                y = relevantRect.exactCenterY()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // PDFIUM Logic
                                            val pDrag = pageForDrag
                                            val tDrag = textPageForDrag
                                            if (pDrag != null && tDrag != null) {
                                                val touchInContentCoords =
                                                    screenToContentCoordinates(
                                                        dragPosition
                                                    )

                                                val pdfCoords = pDrag.mapDeviceCoordsToPage(
                                                    startX = 0,
                                                    startY = 0,
                                                    sizeX = actualBitmapWidthPx,
                                                    sizeY = actualBitmapHeightPx,
                                                    rotate = currentPageRotation,
                                                    deviceX = touchInContentCoords.x.toInt(),
                                                    deviceY = touchInContentCoords.y.toInt()
                                                )
                                                val charTolerance = 10.0

                                                var charIndexForUpdate =
                                                    tDrag.textPageGetCharIndexAtPos(
                                                        x = pdfCoords.x.toDouble(),
                                                        y = pdfCoords.y.toDouble(),
                                                        xTolerance = charTolerance,
                                                        yTolerance = charTolerance
                                                    )

                                                if (charIndexForUpdate == -1 && activeDraggingHandle != null) {
                                                    val pageWidthPdfUnits =
                                                        pDrag.getPageWidthPoint()
                                                    val wideSearchXTolerance =
                                                        pageWidthPdfUnits.toDouble()
                                                    var ySearchCoordinate = pdfCoords.y.toDouble()

                                                    val currentRange = selectionCharRange.value
                                                    if (currentRange != null) {
                                                        val pageTotalChars =
                                                            tDrag.textPageCountChars()
                                                        if (pageTotalChars > 0) {
                                                            val anchorCharIndex =
                                                                if (activeDraggingHandle == Handle.START) {
                                                                    currentRange.first
                                                                } else {
                                                                    (currentRange.second - 1).coerceAtLeast(
                                                                        0
                                                                    )
                                                                }
                                                            if (anchorCharIndex in 0..<pageTotalChars) {
                                                                val anchorCharBox =
                                                                    tDrag.textPageGetCharBox(
                                                                        anchorCharIndex
                                                                    )
                                                                if (anchorCharBox != null) {
                                                                    ySearchCoordinate =
                                                                        ((anchorCharBox.top + anchorCharBox.bottom) / 2.0)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    val wideSearchYTolerance = charTolerance * 1.5
                                                    if (wideSearchXTolerance > 0) {
                                                        charIndexForUpdate =
                                                            tDrag.textPageGetCharIndexAtPos(
                                                                x = pdfCoords.x.toDouble(),
                                                                y = ySearchCoordinate,
                                                                xTolerance = wideSearchXTolerance,
                                                                yTolerance = wideSearchYTolerance
                                                            )
                                                    }
                                                }

                                                if (charIndexForUpdate != -1) {
                                                    val pageCharCount =
                                                        tDrag.textPageCountChars()

                                                    val currentRange = selectionCharRange.value
                                                    if (currentRange != null) {
                                                        val sharedHandle = when (val activeHandle = activeDraggingHandle) {
                                                            Handle.START -> PdfSelectionHandle.START
                                                            Handle.END -> PdfSelectionHandle.END
                                                            null -> null
                                                        }
                                                        val update = sharedHandle?.let {
                                                            PdfTextSelectionEngine.extendRange(
                                                                pageCharCount = pageCharCount,
                                                                current = PdfTextSelectionRange(
                                                                    currentRange.first,
                                                                    currentRange.second
                                                                ),
                                                                activeHandle = it,
                                                                newCharIndex = charIndexForUpdate
                                                            )
                                                        }
                                                        val newHandle = when (update?.activeHandle) {
                                                            PdfSelectionHandle.START -> Handle.START
                                                            PdfSelectionHandle.END -> Handle.END
                                                            null -> activeDraggingHandle
                                                        }
                                                        val newRange = update?.range?.let { it.start to it.end }

                                                        withContext(
                                                            Dispatchers.Main
                                                        ) {
                                                            if (newHandle != null) activeDraggingHandle =
                                                                newHandle
                                                            if (newRange != null) selectionCharRange.value =
                                                                newRange

                                                            updateSelectionVisuals(
                                                                pdfDocumentItem,
                                                                pageIndex,
                                                                selectionCharRange.value,
                                                                actualBitmapWidthPx,
                                                                actualBitmapHeightPx,
                                                                currentPageRotation,
                                                                providedPage = pageForDrag,
                                                                providedTextPage = textPageForDrag
                                                            )

                                                            // Magnifier update
                                                            val currentActiveHandleForMagnifier =
                                                                activeDraggingHandle
                                                            val rects = selectedWordScreenRects
                                                            if (rects.isNotEmpty()) {
                                                                val relevantRect =
                                                                    when (currentActiveHandleForMagnifier) {
                                                                        Handle.START -> rects.first()
                                                                        Handle.END -> rects.last()
                                                                        null -> null
                                                                    }
                                                                val handleContentPosToFollow =
                                                                    when (currentActiveHandleForMagnifier) {
                                                                        Handle.START -> startHandleContentPosition.value
                                                                        Handle.END -> endHandleContentPosition.value
                                                                        null -> null
                                                                    }

                                                                if (relevantRect != null && handleContentPosToFollow != null) {
                                                                    magnifierBitmapCenterTarget =
                                                                        Offset(
                                                                            x = handleContentPosToFollow.x,
                                                                            y = relevantRect.exactCenterY()
                                                                        )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error during handle drag worker")
                            } finally {
                                withContext(Dispatchers.IO) {
                                    textPageForDrag?.close()
                                    pageForDrag?.close()
                                }
                            }
                        }

                        try {
                            drag(down.id) { change ->
                                dragEventChannel.trySend(change.position)
                                change.consume()
                            }
                        } finally {
                            dragEventChannel.close()
                        }
                        dragWorker.invokeOnCompletion {
                            coroutineScope.launch {
                                if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                                    if (selectionCharRange.value != null && selectedWordScreenRects.isNotEmpty()) {
                                        val currentRange = selectionCharRange.value!!
                                        var pageForMenu: ReaderPage? = null
                                        var textPageForMenu: ReaderTextPage? = null
                                        try {
                                            val text = withContext(Dispatchers.IO) {
                                                pageForMenu = pdfDocumentItem.openPage(pdfPageIndex)
                                                textPageForMenu = pageForMenu?.openTextPage()
                                                textPageForMenu?.textPageGetText(
                                                    currentRange.first,
                                                    currentRange.second - currentRange.first
                                                )
                                            }
                                            if (!text.isNullOrBlank()) {
                                                val combinedRect = Rect(selectedWordScreenRects.first())
                                                selectedWordScreenRects.forEach { combinedRect.union(it) }

                                                customMenuState = CustomPdfMenuState(
                                                    selectedText = text,
                                                    anchorRect = combinedRect,
                                                    charRange = currentRange
                                                )
                                                Timber.d(
                                                    "Menu shown after drag. Anchor: ${customMenuState?.anchorRect}"
                                                )
                                            } else {
                                                customMenuState = null
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(
                                                e, "Error fetching text for menu after drag"
                                            )
                                            customMenuState = null
                                        } finally {
                                            withContext(NonCancellable) {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        textPageForMenu?.close()
                                                    } catch (_: Exception) {
                                                    }
                                                    try {
                                                        pageForMenu?.close()
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        customMenuState = null
                                    }
                                } else {
                                    if (ocrSelectionSymbolIndices != null && selectedWordScreenRects.isNotEmpty()) {
                                        val indices = ocrSelectionSymbolIndices!!
                                        val selectedSymbolInfos = allOcrSymbolsForSelection.subList(
                                            indices.first, indices.second
                                        )
                                        if (selectedSymbolInfos.isNotEmpty()) {
                                            val selectedText = buildString {
                                                selectedSymbolInfos.forEachIndexed { index, info ->
                                                    append(info.symbol.text)

                                                    if (index < selectedSymbolInfos.size - 1) {
                                                        val nextInfo = selectedSymbolInfos[index + 1]

                                                        if (info.parentLine !== nextInfo.parentLine) {
                                                            append('\n')
                                                        } else if (info.parentElement !== nextInfo.parentElement) {
                                                            append(' ')
                                                        }
                                                    }
                                                }
                                            }
                                            val firstRect = selectedSymbolInfos.first().symbol.boundingBox!!
                                            val combinedRect = Rect(firstRect)
                                            selectedSymbolInfos.forEach { info -> info.symbol.boundingBox?.let { combinedRect.union(it) } }

                                            customMenuState = CustomPdfMenuState(
                                                selectedText = selectedText,
                                                anchorRect = combinedRect,
                                                charRange = Pair(indices.first, indices.second)
                                            )
                                            Timber.d(
                                                "Menu shown after OCR drag. Anchor: ${customMenuState?.anchorRect}"
                                            )
                                        } else {
                                            customMenuState = null
                                        }
                                    } else {
                                        customMenuState = null
                                    }
                                }
                                activeDraggingHandle = null
                                showMagnifier = false
                                Timber.d(
                                    "PointerInput: Drag on handle completed/cancelled. Menu state: $customMenuState"
                                )
                            }
                        }
                    } else {
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        try {
                            withTimeout(longPressTimeout) {
                                waitForUpOrCancellation()
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            if (latestIsOneHandZooming) {
                                waitForUpOrCancellation()?.consume()
                                return@awaitEachGesture
                            }
                            down.consume()
                            Timber.d(
                                "PointerInput: Long press detected at screen position ${down.position}"
                            )
                            selectionCharRange.value = null
                            ocrSelectionSymbolIndices = null
                            customMenuState = null
                            showMagnifier = false

                            coroutineScope.launch {
                                var tempPage: ReaderPage? = null
                                var tempTextPage: ReaderTextPage? = null
                                var ocrAttemptedForThisPress = false
                                try {
                                    if (!isPdfPage) return@launch

                                    val touchInContentCoords = screenToContentCoordinates(down.position)
                                    Timber.d("Long press: initial touch in content coords: $touchInContentCoords")

                                    if (touchInContentCoords.x < 0 || touchInContentCoords.x > actualBitmapWidthPx || touchInContentCoords.y < 0 || touchInContentCoords.y > actualBitmapHeightPx) {
                                        Timber.d("Long press: Touch point outside bitmap bounds.")
                                        return@launch
                                    }

                                    var pdfiumSelectionSuccessful = false
                                    withContext(Dispatchers.IO) {
                                        tempPage = pdfDocumentItem.openPage(pdfPageIndex)
                                        tempTextPage = tempPage?.openTextPage()

                                        val tPage = tempPage
                                        val tTextPage = tempTextPage
                                        if (tPage != null && tTextPage != null) {
                                            val pdfCoords = tPage.mapDeviceCoordsToPage(
                                                startX = 0,
                                                startY = 0,
                                                sizeX = actualBitmapWidthPx,
                                                sizeY = actualBitmapHeightPx,
                                                rotate = currentPageRotation,
                                                deviceX = touchInContentCoords.x.toInt(),
                                                deviceY = touchInContentCoords.y.toInt()
                                            )
                                            val charTolerance = 5.0
                                            val charIndex = tTextPage.textPageGetCharIndexAtPos(
                                                x = pdfCoords.x.toDouble(),
                                                y = pdfCoords.y.toDouble(),
                                                xTolerance = charTolerance,
                                                yTolerance = charTolerance
                                            )

                                            if (charIndex != -1) {
                                                val pageCharCount = tTextPage.textPageCountChars()
                                                val wordBoundaries = findWordBoundaries(
                                                    tTextPage, charIndex, pageCharCount
                                                )

                                                if (wordBoundaries != null) {
                                                    withContext(Dispatchers.Main) {
                                                        selectionMethodUsed = PdfSelectionMethod.PDFIUM
                                                        selectionCharRange.value = wordBoundaries
                                                    }
                                                    updateSelectionVisuals(
                                                        pdfDocumentItem,
                                                        pdfPageIndex,
                                                        wordBoundaries,
                                                        actualBitmapWidthPx,
                                                        actualBitmapHeightPx,
                                                        currentPageRotation,
                                                        providedPage = tPage,
                                                        providedTextPage = tTextPage
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        if (selectionCharRange.value != null && selectedWordScreenRects.isNotEmpty()) {
                                                            val currentRange = selectionCharRange.value!!
                                                            val text = withContext(Dispatchers.IO) {
                                                                tTextPage.textPageGetText(
                                                                    currentRange.first,
                                                                    currentRange.second - currentRange.first
                                                                )
                                                            }
                                                            if (!text.isNullOrBlank()) {
                                                                val combinedRect = Rect(selectedWordScreenRects.first())
                                                                selectedWordScreenRects.forEach { combinedRect.union(it) }

                                                                customMenuState = CustomPdfMenuState(
                                                                    selectedText = text,
                                                                    anchorRect = combinedRect,
                                                                    charRange = currentRange
                                                                )
                                                                pdfiumSelectionSuccessful = true
                                                                Timber.d(
                                                                    "Long press: PDFIUM selection successful. Menu: ${customMenuState?.anchorRect}"
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!pdfiumSelectionSuccessful && bitmapState != null) {
                                        Timber.d(
                                            "Long press: PDFIUM selection failed or incomplete. Attempting OCR."
                                        )
                                        ocrRipplePosition = down.position
                                        ocrAttemptedForThisPress = true
                                        isPerformingOcrForSelection = true
                                        customMenuState = null
                                        selectionCharRange.value = null
                                        selectedWordScreenRects = emptyList()

                                        try {
                                            val visionText = OcrHelper.extractTextFromBitmap(
                                                bitmapState!!, onOcrModelDownloading
                                            )
                                            ocrVisionTextForSelection = visionText

                                            if (visionText != null && visionText.text.isNotBlank()) {
                                                val symbolInfoList = mutableListOf<OcrSymbolInfo>()
                                                val allElements = mutableListOf<OcrElement>()
                                                visionText.textBlocks.forEach { block ->
                                                    block.lines.forEach { line ->
                                                        line.elements.forEach { element ->
                                                            allElements.add(element)
                                                            element.symbols.forEach { symbol ->
                                                                symbolInfoList.add(
                                                                    OcrSymbolInfo(
                                                                        symbol, element, line
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                allOcrSymbolsForSelection = symbolInfoList

                                                var foundElement: OcrElement? = null
                                                val touchInContentCoords =
                                                    screenToContentCoordinates(
                                                        down.position
                                                    )
                                                val touchXInBitmap = touchInContentCoords.x
                                                val touchYInBitmap = touchInContentCoords.y
                                                for (element in allElements) {
                                                    val box = element.boundingBox
                                                    if (box != null) {
                                                        if (touchXInBitmap >= box.left && touchXInBitmap <= box.right && touchYInBitmap >= box.top && touchYInBitmap <= box.bottom) {
                                                            foundElement = element
                                                            break
                                                        }
                                                    }
                                                }

                                                if (foundElement != null) {
                                                    var symbolStartIndex = 0
                                                    var elementFoundInList = false
                                                    for (element in allElements) {
                                                        if (element === foundElement) {
                                                            elementFoundInList = true
                                                            break
                                                        }
                                                        symbolStartIndex += element.symbols.size
                                                    }

                                                    if (elementFoundInList && foundElement.symbols.isNotEmpty()) {
                                                        val symbolEndIndex =
                                                            symbolStartIndex + foundElement.symbols.size

                                                        selectionMethodUsed = PdfSelectionMethod.OCR
                                                        ocrSelectionSymbolIndices = Pair(
                                                            symbolStartIndex, symbolEndIndex
                                                        )
                                                        updateOcrSymbolSelectionRectsAndHandles(
                                                            ocrSelectionSymbolIndices
                                                        )

                                                        val combinedRect = Rect(selectedWordScreenRects.first())
                                                        selectedWordScreenRects.forEach { combinedRect.union(it) }

                                                        customMenuState = CustomPdfMenuState(
                                                            selectedText = foundElement.text,
                                                            anchorRect = combinedRect,
                                                            charRange = Pair(
                                                                symbolStartIndex, symbolEndIndex
                                                            )
                                                        )
                                                        Timber.d(
                                                            "Long press: OCR selection successful. Menu: ${customMenuState?.anchorRect}"
                                                        )
                                                    } else {
                                                        Timber.d(
                                                            "Long press: OCR word found, but couldn't calculate its symbol indices. Found in list: $elementFoundInList"
                                                        )
                                                        customMenuState = null
                                                        ocrSelectionSymbolIndices = null
                                                        allOcrSymbolsForSelection = emptyList()
                                                        selectedWordScreenRects = emptyList()
                                                        startHandleContentPosition.value = null
                                                        endHandleContentPosition.value = null
                                                    }
                                                } else {
                                                    Timber.d(
                                                        "Long press: OCR successful but no text element at touch point."
                                                    )
                                                    customMenuState = null
                                                    ocrSelectionSymbolIndices = null
                                                    allOcrSymbolsForSelection = emptyList()
                                                    selectedWordScreenRects = emptyList()
                                                    startHandleContentPosition.value = null
                                                    endHandleContentPosition.value = null
                                                }
                                            } else {
                                                Timber.d(
                                                    "Long press: OCR returned no text."
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(
                                                e, "Long press: Error during OCR text selection"
                                            )
                                            pageErrorMessage = context.getString(
                                                R.string.error_ocr_selection,
                                                e.readablePdfErrorDetail()
                                            )
                                        } finally {
                                            isPerformingOcrForSelection = false
                                            ocrRipplePosition = null
                                        }
                                    } else if (!pdfiumSelectionSuccessful) {
                                        Timber.d(
                                            "Long press: PDFIUM selection failed and no bitmap for OCR."
                                        )
                                    }

                                    if (!pdfiumSelectionSuccessful && !ocrAttemptedForThisPress) {
                                        customMenuState = null
                                        selectionCharRange.value = null
                                        selectedWordScreenRects = emptyList()
                                    }
                                } catch (e: Exception) {
                                    Timber.e(
                                        e,
                                        "Error during long press text selection on page $pageIndex"
                                    )
                                    pageErrorMessage = context.getString(
                                        R.string.error_selection,
                                        e.readablePdfErrorDetail()
                                    )
                                    customMenuState = null
                                    selectionCharRange.value = null
                                    selectedWordScreenRects = emptyList()
                                } finally {
                                    withContext(NonCancellable) {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                tempTextPage?.close()
                                            } catch (_: Exception) {
                                            }
                                            try {
                                                tempPage?.close()
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                    if (isPerformingOcrForSelection && !ocrAttemptedForThisPress) isPerformingOcrForSelection =
                                        false
                                }
                            }
                            val longPressInteractionEndEvent = waitForUpOrCancellation()
                            longPressInteractionEndEvent?.consume()
                            Timber.d(
                                "PointerInput: Long press interaction's 'up' event consumed."
                            )
                        }
                    }
                }
            }

            .pointerInput(
                actualBitmapWidthPx,
                actualBitmapHeightPx,
                customMenuState,
                selectionCharRange.value,
                pageLinks,
                centeringOffsetX,
                centeringOffsetY,
                isZoomEnabled,
                isVerticalScroll,
                isEditMode,
                selectedTool,
                isStylusOnlyMode,
                userHighlightScreenRects,
                bubbleTapSlopPx,
                isScrollLocked
            ) {
                val isTapDetectionAllowed = !isEditMode ||
                        selectedTool == InkType.TEXT ||
                        isStylusOnlyMode

                if (!isTapDetectionAllowed) {
                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                        "page.detector.disabled page=$pageIndex vertical=$isVerticalScroll edit=$isEditMode " +
                            "tool=$selectedTool stylusOnly=$isStylusOnlyMode"
                    )
                    return@pointerInput
                }

                val oneHandZoomDistancePx = with(density) {
                    PDF_ONE_HAND_ZOOM_DRAG_DISTANCE_FOR_DOUBLE_DP.dp.toPx()
                }
                var oneHandZoomStartScale = 1f
                var oneHandZoomStartOffset = Offset.Zero

                fun canZoomByDoubleTap(): Boolean {
                    return (isZoomEnabled && !isVerticalScroll && !isScrollLocked && actualBitmapWidthPx > 0) ||
                        (isVerticalScroll && !isScrollLocked && currentOnDoubleTap != null)
                }

                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                    "page.detector.enabled page=$pageIndex vertical=$isVerticalScroll zoomEnabled=$isZoomEnabled " +
                        "scrollLocked=$isScrollLocked bitmap=${actualBitmapWidthPx}x$actualBitmapHeightPx " +
                        "scale=$latestScale offset=$latestOffset hasDoubleTap=${currentOnDoubleTap != null} " +
                        "hasDragZoom=${currentOnDoubleTapDragZoom != null}"
                )

                detectPdfTapAndOneHandZoomGestures(
                    viewConfiguration = viewConfiguration,
                    canStartOneHandZoom = {
                        isZoomEnabled && !isVerticalScroll && !isScrollLocked && actualBitmapWidthPx > 0
                    },
                    canHandleQuickDoubleTap = { canZoomByDoubleTap() },
                    consumeSingleTap = true,
                    onTap = tapDetector@{ tapOffset ->
                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                        "page.onTap page=$pageIndex vertical=$isVerticalScroll offset=$tapOffset"
                    )
                    if (currentOnPreSingleTap?.invoke(tapOffset) == true) {
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "page.onTap.preSingleTapConsumed page=$pageIndex vertical=$isVerticalScroll"
                        )
                        return@tapDetector
                    }

                    val tapInContentCoords = latestScreenToContentCoordinates(tapOffset)
                    val tapXInBitmap = tapInContentCoords.x
                    val tapYInBitmap = tapInContentCoords.y
                    val isWithinContentBounds =
                        tapXInBitmap in 0f..actualBitmapWidthPx.toFloat() &&
                            tapYInBitmap in 0f..actualBitmapHeightPx.toFloat()

                    if (!isWithinContentBounds) {
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "page.onTap.outsideContent page=$pageIndex vertical=$isVerticalScroll " +
                                "bitmapTap=(${tapXInBitmap.toInt()},${tapYInBitmap.toInt()})"
                        )
                        currentOnSingleTap(tapOffset)
                        return@tapDetector
                    }

                    Timber.tag("BubbleZoom").d("Tap inside bounds. modeActive=$currentBubbleZoomModeActive, detectedBubbles=${currentDetectedBubbles.size}, tapPos=($tapXInBitmap, $tapYInBitmap)")

                    if (currentBubbleZoomModeActive && currentDetectedBubbles.isNotEmpty()) {
                        val tappedBubbleIndex = currentDetectedBubbles.indexOfFirst { bubble ->
                            isTapInsideBubble(
                                bubble = bubble,
                                tapX = tapXInBitmap,
                                tapY = tapYInBitmap,
                                hitSlopPx = bubbleTapSlopPx
                            )
                        }

                        Timber.tag("BubbleZoom").d("Tapped bubble index: $tappedBubbleIndex (expandedIndex=$currentExpandedBubbleIndex)")

                        if (tappedBubbleIndex != -1) {
                            expandedBubbleIndex = if (currentExpandedBubbleIndex == tappedBubbleIndex) {
                                -1
                            } else {
                                tappedBubbleIndex
                            }
                            return@tapDetector
                        } else if (currentExpandedBubbleIndex != -1) {
                            expandedBubbleIndex = -1
                            return@tapDetector
                        }
                    }

                    coroutineScope.launch {
                        val nativeResult = withContext(Dispatchers.IO) {
                            try {
                                pdfDocumentItem.openPage(pdfPageIndex)?.use { page ->
                                    val nativeTap = (page as? PdfPageWrapper)?.resolveNativeTap(
                                        documentWrapper = pdfDocumentItem as? PdfDocumentWrapper,
                                        bitmapWidthPx = actualBitmapWidthPx,
                                        bitmapHeightPx = actualBitmapHeightPx,
                                        pageRotation = currentPageRotation,
                                        deviceX = tapInContentCoords.x.toInt(),
                                        deviceY = tapInContentCoords.y.toInt()
                                    ) ?: return@withContext 0

                                    if (!nativeTap.resolvedNativePointer) {
                                        Timber.tag("PdfInteraction").e("Could not find native pointer for page $pdfPageIndex")
                                        return@withContext 0
                                    }

                                    if (nativeTap.linkInfo != null) {
                                        Timber.tag("PdfLinkDiagnostic").i(">>> Native Link Info Extracted: ${nativeTap.linkInfo}")
                                        if (nativeTap.linkInfo.startsWith("URI:")) {
                                            val url = nativeTap.linkInfo.substringAfter("URI:")
                                            withContext(Dispatchers.Main) { onLinkClicked(url) }
                                            return@withContext 1
                                        } else if (nativeTap.linkInfo.startsWith("PAGE:")) {
                                            val targetPage = nativeTap.linkInfo.substringAfter("PAGE:").toIntOrNull()
                                            if (targetPage != null && targetPage >= 0) {
                                                withContext(Dispatchers.Main) { onInternalLinkClicked(targetPage) }
                                                return@withContext 1
                                            }
                                        }
                                    }

                                    if (nativeTap.clickHandled) {
                                        return@withContext 2
                                    }
                                    return@withContext 0
                                } ?: 0
                            } catch (e: Exception) {
                                Timber.tag("PdfInteraction").e(e, "Interaction error")
                                0
                            }
                        }

                        if (nativeResult == 2) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.nativeAction page=$pageIndex vertical=$isVerticalScroll"
                            )
                            Timber.tag("PdfInteraction").i("Action detected. Refreshing page.")
                            tiles = emptyList()
                            bitmapState = null
                            isLoadingPage = true
                            currentRenderedPageId = "ACTION_${System.currentTimeMillis()}"
                            return@launch
                        } else if (nativeResult == 1) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.nativeLink page=$pageIndex vertical=$isVerticalScroll"
                            )
                            return@launch
                        }

                        val annotHitTolerance = with(density) { 24.dp.toPx() } / latestInputScale
                        val hitTolerance = with(density) { 16.dp.toPx() } / latestInputScale

                        Timber.d("detectTapGestures: Tap at bitmap coords (${tapXInBitmap.toInt()}, ${tapYInBitmap.toInt()})")

                        var tappedRect: Rect? = null
                        val hitHighlightPair = userHighlightScreenRects.findLast { pair ->
                            val hit = pair.second.find { r ->
                                val hitLeft = r.left - hitTolerance
                                val hitTop = r.top - hitTolerance
                                val hitRight = r.right + hitTolerance
                                val hitBottom = r.bottom + hitTolerance

                                tapXInBitmap in hitLeft..hitRight && tapYInBitmap >= hitTop && tapYInBitmap <= hitBottom
                            }
                            if (hit != null) {
                                tappedRect = hit
                                true
                            } else false
                        }

                        val standardHit = standardAnnotScreenRects.findLast { (annot, screenRect) ->
                            if (annot.subtype == 2) return@findLast false

                            val left = min(screenRect.left, screenRect.right)
                            val right = max(screenRect.left, screenRect.right)
                            val top = min(screenRect.top, screenRect.bottom)
                            val bottom = max(screenRect.top, screenRect.bottom)

                            val inflatedHitBox = Rect(
                                (left - annotHitTolerance).toInt(),
                                (top - annotHitTolerance).toInt(),
                                (right + annotHitTolerance).toInt(),
                                (bottom + annotHitTolerance).toInt()
                            )

                            inflatedHitBox.contains(tapInContentCoords.x.toInt(), tapInContentCoords.y.toInt())
                        }

                        if (standardHit != null) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.annotationHit page=$pageIndex vertical=$isVerticalScroll"
                            )
                            val (annot, screenRect) = standardHit
                            customMenuState = CustomPdfMenuState(
                                selectedText = annot.contents ?: "No comment",
                                anchorRect = screenRect,
                                charRange = Pair(-1, -1),
                                isComment = true,
                                author = annot.author,
                                annotation = annot
                            )
                            return@launch
                        }

                        if (hitHighlightPair != null && tappedRect != null) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.highlightHit page=$pageIndex vertical=$isVerticalScroll"
                            )
                            val hitHighlight = hitHighlightPair.first
                            onNoteRequested(hitHighlight.id)
                            return@launch
                        }

                        val clickedLink = pageLinks.firstOrNull { link ->
                            link.tapBounds.contains(tapXInBitmap.toInt(), tapYInBitmap.toInt())
                        }

                        if (clickedLink != null) {
                            val destinationPage = clickedLink.destPageIdx
                            val externalUrl = clickedLink.url
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.fallbackLink page=$pageIndex vertical=$isVerticalScroll " +
                                    "dest=$destinationPage url=${externalUrl != null}"
                            )
                            Timber.d("PdfPageComposable: Fallback pageLinks intercepted click.")
                            if (destinationPage != null && destinationPage >= 0) {
                                onInternalLinkClicked(destinationPage)
                            } else if (externalUrl != null) {
                                onLinkClicked(externalUrl)
                            }
                            return@launch
                        }

                        val wasMenuVisible = customMenuState != null
                        val wasSelectionVisible = selectionCharRange.value != null || ocrSelectionSymbolIndices != null

                        if (wasMenuVisible || wasSelectionVisible) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.clearSelectionOrMenu page=$pageIndex vertical=$isVerticalScroll " +
                                    "menu=$wasMenuVisible selection=$wasSelectionVisible"
                            )
                            customMenuState = null
                            selectionCharRange.value = null
                            ocrSelectionSymbolIndices = null
                            updateSelectionVisuals(
                                pdfDocumentItem,
                                pdfPageIndex,
                                null,
                                actualBitmapWidthPx,
                                actualBitmapHeightPx,
                                currentPageRotation,
                            )
                        } else {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.onTap.singleTap page=$pageIndex vertical=$isVerticalScroll"
                            )
                            currentOnSingleTap(tapOffset)
                        }
                    }
                },
                    onQuickDoubleTap = quickDoubleTap@{ tapOffset ->
                    pdfZoomDiagnostic(
                        "page=$pageIndex quickDoubleTap received vertical=$isVerticalScroll " +
                            "local=$tapOffset canZoom=${canZoomByDoubleTap()} scale=$latestScale"
                    )
                    if (!canZoomByDoubleTap()) {
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "page.quickDoubleTap.blocked page=$pageIndex vertical=$isVerticalScroll " +
                                "zoomEnabled=$isZoomEnabled scrollLocked=$isScrollLocked bitmapWidth=$actualBitmapWidthPx"
                        )
                        return@quickDoubleTap
                    }
                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                        "page.quickDoubleTap page=$pageIndex vertical=$isVerticalScroll offset=$tapOffset " +
                            "scale=$latestScale"
                    )
                    if (isZoomEnabled && !isVerticalScroll && !isScrollLocked) {
                        if (actualBitmapWidthPx == 0) return@quickDoubleTap
                        coroutineScope.launch {
                            val startScale = latestScale
                            val targetScale = if (startScale > 1.1f) 1f else 2.5f

                            val startOffset = latestOffset
                            val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                            val contentSize = Size(
                                actualBitmapWidthPx.toFloat(),
                                actualBitmapHeightPx.toFloat()
                            )
                            val targetOffset = if (targetScale <= 1.1f) {
                                Offset.Zero
                            } else {
                                centeredPdfCameraOffsetForScaleChange(
                                    previousScale = startScale,
                                    nextScale = targetScale,
                                    previousOffset = startOffset,
                                    pivot = tapOffset,
                                    viewportSize = viewportSize,
                                    contentSize = contentSize
                                )
                            }

                            try {
                                isTransforming = true
                                Animatable(0f).animateTo(
                                    1f, animationSpec = tween(
                                        durationMillis = 300
                                    )
                                ) {
                                    val progress = value
                                    scale = androidx.compose.ui.util.lerp(
                                        startScale, targetScale, progress
                                    )
                                    offset = androidx.compose.ui.geometry.lerp(
                                        startOffset, targetOffset, progress
                                    )
                                    onScaleChanged(scale)
                                }
                                if (scale <= 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                    onScaleChanged(scale)
                                }
                            } finally {
                                isTransforming = false
                            }
                        }
                    } else if (isVerticalScroll && !isScrollLocked && currentOnDoubleTap != null) {
                        // The vertical camera is expressed in reader-root coordinates, while this
                        // recognizer receives a point local to an individual page.
                        val readerOffset = layoutCoordinates?.localToRoot(tapOffset) ?: tapOffset
                        pdfZoomDiagnostic(
                            "page=$pageIndex verticalDoubleTap forward local=$tapOffset root=$readerOffset " +
                                "hasCoordinates=${layoutCoordinates != null}"
                        )
                        currentOnDoubleTap!!(readerOffset)
                    }
                },
                    onOneHandZoomHoldStart = { _ ->
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "page.oneHandHoldStart page=$pageIndex vertical=$isVerticalScroll " +
                                "scale=$latestScale offset=$latestOffset scrollLocked=$isScrollLocked"
                        )
                        isOneHandZooming = true
                        if (isZoomEnabled && !isVerticalScroll && !isScrollLocked && actualBitmapWidthPx > 0) {
                            paginationPanFlingJob?.cancel()
                            paginationPanFlingJob = null
                            oneHandZoomStartScale = latestScale
                            oneHandZoomStartOffset = latestOffset
                            isPaginationPageGestureActive = true
                        } else if (isVerticalScroll && !isScrollLocked) {
                            currentOnDoubleTapDragZoomStart?.invoke(Offset(size.width / 2f, size.height / 2f))
                        }
                    },
                    onOneHandZoom = { _, totalDragY ->
                        if (isZoomEnabled && !isVerticalScroll && !isScrollLocked && actualBitmapWidthPx > 0) {
                            val pivot = Offset(size.width / 2f, size.height / 2f)
                            val newScale = pdfOneHandZoomScale(
                                startScale = oneHandZoomStartScale,
                                totalDragY = totalDragY,
                                dragDistanceForDoublePx = oneHandZoomDistancePx,
                                minScale = 1f,
                                maxScale = PDF_MAX_ZOOM_SCALE
                            )
                            val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                            val contentSize = Size(
                                actualBitmapWidthPx.toFloat(),
                                actualBitmapHeightPx.toFloat()
                            )
                            scale = newScale
                            offset = centeredPdfCameraOffsetForScaleChange(
                                previousScale = oneHandZoomStartScale,
                                nextScale = newScale,
                                previousOffset = oneHandZoomStartOffset,
                                pivot = pivot,
                                viewportSize = viewportSize,
                                contentSize = contentSize
                            )
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                                "page.oneHandUpdate page=$pageIndex dragY=$totalDragY scale=$newScale offset=$offset"
                            )
                            onScaleChanged(scale)
                        } else if (isVerticalScroll && !isScrollLocked) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                                "page.oneHandUpdate.verticalForward page=$pageIndex dragY=$totalDragY"
                            )
                            currentOnDoubleTapDragZoom?.invoke(Offset(size.width / 2f, size.height / 2f), totalDragY)
                        }
                    },
                    onOneHandZoomEnd = { _ ->
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "page.oneHandEnd page=$pageIndex vertical=$isVerticalScroll scale=$scale offset=$offset"
                        )
                        if (isZoomEnabled && !isVerticalScroll && !isScrollLocked && actualBitmapWidthPx > 0) {
                            if (scale > 1f && scale < 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                                onScaleChanged(scale)
                            }
                            isPaginationPageGestureActive = false
                        } else if (isVerticalScroll && !isScrollLocked) {
                            currentOnDoubleTapDragZoomEnd?.invoke()
                        }
                        isOneHandZooming = false
                    }
                )
            }
            .pointerInput(
                actualBitmapWidthPx,
                actualBitmapHeightPx,
                activeDraggingHandle,
                isZoomEnabled,
                isVerticalScroll,
                isEditMode,
                onTwoFingerSwipe,
                isScrollLocked,
                isOneHandZooming
            ) {
                if (!isZoomEnabled || isVerticalScroll || actualBitmapWidthPx == 0 || activeDraggingHandle != null || isOneHandZooming) return@pointerInput

                val decay = splineBasedDecay<Float>(this)
                val velocityTracker = VelocityTracker()
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    @Suppress("UnusedVariable", "Unused") val down =
                        awaitFirstDown(requireUnconsumed = false)
                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                        "page.panDetector.down page=$pageIndex consumed=${down.isConsumed} " +
                            "scale=$scale offset=$offset scrollLocked=$isScrollLocked"
                    )
                    isPaginationPageGestureActive = true
                    try {
                    paginationPanFlingJob?.cancel()
                    paginationPanFlingJob = null
                    velocityTracker.resetTracking()

                    var mode = 0
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    var swipeAccumulatorX = 0f
                    var velocityAccumulator = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        val pointerCount = event.changes.size

                        if (canceled) {
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "page.panDetector.canceledByConsumed page=$pageIndex mode=$mode scale=$scale " +
                                    "pointerCount=$pointerCount changes=${event.changes.joinToString { change ->
                                        "pressed=${change.pressed},consumed=${change.isConsumed},moved=${change.positionChanged()}"
                                    }}"
                            )
                        }

                        if (!canceled) {
                            val rawPanChange = event.calculatePan()
                            val panChange = if (isScrollLocked && pointerCount == 1) {
                                if (isVerticalScroll) Offset(0f, rawPanChange.y) else Offset.Zero
                            } else {
                                rawPanChange
                            }
                            val zoomChange = event.calculateZoom()

                            if (scale > 1f) {
                                if (mode == 0) {
                                    if (pointerCount == 1) {
                                        accumulatedPan += panChange
                                        if (accumulatedPan.getDistance() > touchSlop) {
                                            mode = 1
                                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                "page.panDetector.modePanSingle page=$pageIndex accumulatedPan=$accumulatedPan scale=$scale"
                                            )
                                            Timber.tag("PdfZoomDebug").d("Mode Change: PAN (Single Pointer)")
                                        }
                                    } else if (pointerCount > 1) {
                                        accumulatedZoom *= zoomChange
                                        accumulatedPan += panChange

                                        val zoomDiff = abs(accumulatedZoom - 1f)
                                        val panDist = accumulatedPan.getDistance()

                                        if (zoomDiff > 0.05f) {
                                            mode = 2
                                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                "page.panDetector.modeZoomMulti page=$pageIndex zoomDiff=$zoomDiff panDist=$panDist scale=$scale"
                                            )
                                            Timber.tag("PdfZoomDebug").d("Mode Change: ZOOM (Multi Pointer)")
                                        } else if (panDist > touchSlop) {
                                            mode = 1
                                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                "page.panDetector.modePanMulti page=$pageIndex zoomDiff=$zoomDiff panDist=$panDist scale=$scale"
                                            )
                                            Timber.tag("PdfZoomDebug").d("Mode Change: PAN (Multi Pointer)")
                                        }
                                    }
                                }

                                if (mode == 1) {
                                    val contentWidth = actualBitmapWidthPx * scale
                                    val contentHeight = actualBitmapHeightPx * scale
                                    val maxOffsetX =
                                        (contentWidth - size.width).coerceAtLeast(0f) / 2f
                                    val maxOffsetY =
                                        (contentHeight - size.height).coerceAtLeast(0f) / 2f

                                    val newX = (offset.x + panChange.x).coerceIn(
                                        -maxOffsetX, maxOffsetX
                                    )
                                    val newY = (offset.y + panChange.y).coerceIn(
                                        -maxOffsetY, maxOffsetY
                                    )
                                    Timber.tag("PdfZoomDebug").v("Panning: Offset $offset -> $newX, $newY (Max: $maxOffsetX, $maxOffsetY)")
                                    offset = Offset(newX, newY)

                                    if (event.changes.isNotEmpty() && panChange != Offset.Zero) {
                                        velocityAccumulator += panChange
                                        velocityTracker.addPosition(
                                            event.changes[0].uptimeMillis,
                                            velocityAccumulator
                                        )
                                    }

                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                } else if (mode == 2 && pointerCount > 1) {
                                    val oldScale = scale
                                    val newScale = (scale * zoomChange).coerceIn(1f, PDF_MAX_ZOOM_SCALE)
                                    Timber.tag("PdfZoomIssue").v("Gesture Scaling: old=$oldScale, new=$newScale, zoomChange=$zoomChange")

                                    val previousCentroid = event.calculateCentroid(useCurrent = false)
                                    if (previousCentroid != Offset.Unspecified) {
                                        val ratio = newScale / oldScale
                                        val screenCenter = Offset(size.width / 2f, size.height / 2f)
                                        val newOffset = offset * ratio + (previousCentroid - screenCenter) * (1 - ratio) + panChange

                                        val contentWidth = actualBitmapWidthPx * newScale
                                        val contentHeight = actualBitmapHeightPx * newScale
                                        val maxOffsetX = (contentWidth - size.width).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = (contentHeight - size.height).coerceAtLeast(0f) / 2f

                                        Timber.tag("PdfZoomDebug").v("Scaling: RawZoom=$zoomChange, Scale=$oldScale->$newScale, Offset=$offset->$newOffset (Clamped Max: $maxOffsetX, $maxOffsetY)")

                                        offset = Offset(
                                            x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                            y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                        scale = newScale
                                        onScaleChanged(scale)
                                    }
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            } else {
                                if (pointerCount > 1) {
                                    if (mode == 0) {
                                        accumulatedZoom *= zoomChange
                                        accumulatedPan += panChange

                                        if (abs(accumulatedZoom - 1f) > 0.05f) {
                                            mode = 2
                                        } else if (accumulatedPan.getDistance() > touchSlop) {
                                            if (abs(accumulatedPan.x) > abs(accumulatedPan.y) * 1.5f) {
                                                mode = 3
                                            }
                                        }
                                    }

                                    if (mode == 2) {
                                        val oldScale = scale
                                        val newScale = (scale * zoomChange).coerceIn(
                                            1f, PDF_MAX_ZOOM_SCALE
                                        )
                                        val previousCentroid = event.calculateCentroid(
                                            useCurrent = false
                                        )

                                        if (previousCentroid != Offset.Unspecified) {
                                            val ratio = newScale / oldScale
                                            val screenCenter = Offset(
                                                size.width / 2f, size.height / 2f
                                            )
                                            val newOffset =
                                                offset * ratio + (previousCentroid - screenCenter) * (1 - ratio) + panChange

                                            val contentWidth = actualBitmapWidthPx * newScale
                                            val contentHeight = actualBitmapHeightPx * newScale
                                            val maxOffsetX =
                                                (contentWidth - size.width).coerceAtLeast(0f) / 2f
                                            val maxOffsetY =
                                                (contentHeight - size.height).coerceAtLeast(0f) / 2f

                                            offset = Offset(
                                                x = newOffset.x.coerceIn(
                                                    -maxOffsetX, maxOffsetX
                                                ), y = newOffset.y.coerceIn(
                                                    -maxOffsetY, maxOffsetY
                                                )
                                            )
                                            scale = newScale
                                            onScaleChanged(scale)
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    } else if (mode == 3) {
                                        swipeAccumulatorX += panChange.x
                                        if (abs(swipeAccumulatorX) > 70f) {
                                            val direction = if (swipeAccumulatorX > 0) -1
                                            else 1
                                            onTwoFingerSwipe(direction)
                                            mode = 4
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })

                    if (scale > 1f && scale < 1.05f) {
                        coroutineScope.launch {
                            try {
                                isTransforming = true
                                val startScale = scale
                                val startOffset = offset
                                Animatable(0f).animateTo(1f) {
                                    scale = lerp(startScale, 1f, value)
                                    offset = lerp(startOffset, Offset.Zero, value)
                                    onScaleChanged(scale)
                                }
                            } finally {
                                isTransforming = false
                            }
                        }
                    } else if (mode == 1 && scale > 1f) {
                        val contentWidth = actualBitmapWidthPx * scale
                        val contentHeight = actualBitmapHeightPx * scale
                        val maxOffsetX = (contentWidth - size.width).coerceAtLeast(0f) / 2f
                        val maxOffsetY = (contentHeight - size.height).coerceAtLeast(0f) / 2f

                        val velocity = velocityTracker.calculateVelocity()
                        val flingX = if (!isScrollLocked && abs(velocity.x) > PDF_PAGINATION_PAN_FLING_MIN_VELOCITY) {
                            velocity.x * PDF_PAGINATION_PAN_FLING_MULTIPLIER
                        } else {
                            0f
                        }
                        val flingY = if (abs(velocity.y) > PDF_PAGINATION_PAN_FLING_MIN_VELOCITY) {
                            velocity.y * PDF_PAGINATION_PAN_FLING_MULTIPLIER
                        } else {
                            0f
                        }

                        if (flingX == 0f && flingY == 0f) {
                            offset = Offset(
                                x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            val startOffset = offset
                            paginationPanFlingJob = coroutineScope.launch {
                                try {
                                    kotlinx.coroutines.coroutineScope {
                                        launch {
                                            if (flingX != 0f) {
                                                Animatable(startOffset.x).animateDecay(flingX, decay) {
                                                    offset = offset.copy(
                                                        x = value.coerceIn(-maxOffsetX, maxOffsetX)
                                                    )
                                                }
                                            }
                                        }
                                        launch {
                                            if (flingY != 0f) {
                                                Animatable(startOffset.y).animateDecay(flingY, decay) {
                                                    offset = offset.copy(
                                                        y = value.coerceIn(-maxOffsetY, maxOffsetY)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    paginationPanFlingJob = null
                                }
                            }
                        }
                    }
                    } finally {
                        isPaginationPageGestureActive = false
                    }
                }
            }
            .pointerInput(
                isEditMode,
                actualBitmapWidthPx,
                actualBitmapHeightPx,
                scale,
                offset,
                isScrolling,
                isVerticalScroll,
                selectedTool,
                isStylusOnlyMode,
                isHighlighterSnapEnabled
            ) {
                val canDraw = isEditMode && selectedTool != InkType.TEXT && !isScrolling && !isVerticalScroll && actualBitmapWidthPx > 0 && actualBitmapHeightPx > 0

                if (!canDraw) {
                    return@pointerInput
                }

                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        Timber.tag("PointerTypeDebug").d("Page $pageIndex: Input Type detected: ${down.type}")

                        if (isStylusOnlyMode && down.type == PointerType.Touch) {
                            return@awaitEachGesture
                        }

                        val buttons = currentEvent.buttons
                        Timber.tag("StylusDebug").d(
                            "Page $pageIndex | Type: ${down.type} | isPrimary: ${buttons.isPrimaryPressed} | isSecondary: ${buttons.isSecondaryPressed} | isTertiary: ${buttons.isTertiaryPressed} | buttonsString: $buttons"
                        )

                        val isEraserOverride = down.type == PointerType.Eraser || (down.type == PointerType.Stylus && (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isPrimaryPressed || stylusButtonHovering))
                        isStylusEraserOverride = isEraserOverride

                        val dragPointerId = down.id
                        val startPos = down.position
                        var dragStarted = false
                        val touchSlop = viewConfiguration.touchSlop

                        if (selectedTool == InkType.ERASER || isEraserOverride) {
                            eraserPosition = down.position
                        }

                        while (true) {
                            val event = awaitPointerEvent()

                            if (event.changes.size > 1) {
                                if (dragStarted) {
                                    drawingState?.onDrawCancel()
                                }
                                eraserPosition = null
                                isStylusEraserOverride = false
                                return@awaitEachGesture
                            }

                            val change = event.changes.firstOrNull {
                                it.id == dragPointerId
                            }
                            if (change == null) return@awaitEachGesture

                            if (change.changedToUp()) {
                                change.consume()
                                if (!dragStarted) {
                                    val contentPos = screenToContentCoordinates(startPos)
                                    val normX =
                                        (contentPos.x / actualBitmapWidthPx).coerceIn(0f, 1f)
                                    val normY =
                                        (contentPos.y / actualBitmapHeightPx).coerceIn(0f, 1f)

                                    onDrawStart(PdfPoint(normX, normY), isEraserOverride)
                                    onDrawEnd()
                                } else {
                                    onDrawEnd()
                                }
                                eraserPosition = null
                                isStylusEraserOverride = false
                                return@awaitEachGesture
                            }

                            if (change.positionChanged()) {
                                val dist = (change.position - startPos).getDistance()

                                if (!dragStarted) {
                                    if (dist > touchSlop) {
                                        dragStarted = true

                                        val startContentPos = screenToContentCoordinates(startPos)
                                        val startNormX =
                                            (startContentPos.x / actualBitmapWidthPx).coerceIn(
                                                0f, 1f
                                            )
                                        val startNormY =
                                            (startContentPos.y / actualBitmapHeightPx).coerceIn(
                                                0f, 1f
                                            )
                                        onDrawStart(
                                            PdfPoint(startNormX, startNormY), isEraserOverride
                                        )

                                        val currContentPos = screenToContentCoordinates(
                                            change.position
                                        )
                                        val currNormX =
                                            (currContentPos.x / actualBitmapWidthPx).coerceIn(
                                                0f, 1f
                                            )
                                        val currNormY =
                                            (currContentPos.y / actualBitmapHeightPx).coerceIn(
                                                0f, 1f
                                            )
                                        onDraw(PdfPoint(currNormX, currNormY), isEraserOverride)

                                        if (selectedTool == InkType.ERASER || isEraserOverride) {
                                            eraserPosition = change.position
                                        }
                                        change.consume()
                                    }
                                } else {
                                    val currContentPos = screenToContentCoordinates(
                                        change.position
                                    )
                                    val currNormX =
                                        (currContentPos.x / actualBitmapWidthPx).coerceIn(0f, 1f)
                                    val currNormY =
                                        (currContentPos.y / actualBitmapHeightPx).coerceIn(0f, 1f)
                                    onDraw(PdfPoint(currNormX, currNormY), isEraserOverride)

                                    if (selectedTool == InkType.ERASER || isEraserOverride) {
                                        eraserPosition = change.position
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                } finally {
                    eraserPosition = null
                    isStylusEraserOverride = false
                }
            }, contentAlignment = Alignment.Center
    ) {
        SideEffect {
            if (effectiveScale > 1f) {
                Timber.tag("PdfZoomDiagnostics").d(
                    "BoxWithConstraints Page $pageIndex: MaxW=$maxWidth, MaxH=$maxHeight"
                )
            }
        }
        val imeInsets = WindowInsets.ime
        val screenHeight = constraints.maxHeight.toFloat()

        val imeBottom = imeInsets.getBottom(density)

        LaunchedEffect(
            richTextController?.cursorPageIndex,
            richTextController?.cursorRectInPage,
            imeBottom,
            pageIndex,
            screenHeight,
            isEditMode,
            selectedTool,
            density
        ) {
            val controller = richTextController ?: return@LaunchedEffect

            Timber.tag("KeyboardAdjust").v(
                "Check: Page=$pageIndex, IME=$imeBottom, EditMode=$isEditMode, OffsetY=${offset.y}"
            )

            if (imeBottom == 0 || !isEditMode || selectedTool != InkType.TEXT) {
                if (keyboardAdjustmentOriginalOffset != null) {
                    Timber.tag("KeyboardAdjust")
                        .i("Restoring original offset: $keyboardAdjustmentOriginalOffset")
                    if (isZoomEnabled && !isVerticalScroll) {
                        offset = offset.copy(y = keyboardAdjustmentOriginalOffset!!)
                    }
                    keyboardAdjustmentOriginalOffset = null
                }
                return@LaunchedEffect
            }

            val cursorPage = controller.cursorPageIndex
            val cursorRect = controller.cursorRectInPage

            if (cursorPage == pageIndex && cursorRect != null) {
                if (keyboardAdjustmentOriginalOffset == null) {
                    keyboardAdjustmentOriginalOffset = offset.y
                    Timber.tag("KeyboardAdjust").i("Snapshot original offset: ${offset.y}")
                }

                val baseOffsetY = keyboardAdjustmentOriginalOffset!!

                val currentScale = if (isZoomEnabled && !isVerticalScroll) scale else externalScale
                val currentOffset =
                    if (isZoomEnabled && !isVerticalScroll) offset.copy(y = baseOffsetY)
                    else Offset.Zero

                val contentHeight = actualBitmapHeightPx * currentScale
                val centeringY = (screenHeight - contentHeight) / 2f

                val cursorTopInPage = cursorRect.top * currentScale
                val cursorBottomInPage = cursorRect.bottom * currentScale

                val cursorScreenTop = centeringY + currentOffset.y + cursorTopInPage
                val cursorScreenBottom = centeringY + currentOffset.y + cursorBottomInPage

                val topSafeBuffer = with(density) { 80.dp.toPx() }

                val visibleBottom = screenHeight - imeBottom

                var targetAdjustment = 0f

                if (cursorScreenBottom > visibleBottom) {
                    targetAdjustment = visibleBottom - cursorScreenBottom
                } else if (cursorScreenTop < topSafeBuffer) {
                    targetAdjustment = topSafeBuffer - cursorScreenTop
                }

                Timber.tag("KeyboardAdjust").v(
                    "CursorBottom=$cursorScreenBottom, VisibleBottom=$visibleBottom, TargetAdj=$targetAdjustment"
                )

                if (kotlin.math.abs(targetAdjustment) > 1f && isZoomEnabled && !isVerticalScroll) {
                    val finalTargetY = baseOffsetY + targetAdjustment

                    if (offset.y != finalTargetY) {
                        Timber.tag("KeyboardAdjust").i("Applying adjustment. NewY=$finalTargetY")
                        offset = offset.copy(y = finalTargetY)
                    }
                } else if (kotlin.math.abs(targetAdjustment) <= 1f && offset.y != baseOffsetY) {
                    Timber.tag("KeyboardAdjust")
                        .i("Cursor visible, restoring base. NewY=$baseOffsetY")
                    offset = offset.copy(y = baseOffsetY)
                }
            }
        }

        var previousLockedViewportSize by remember { mutableStateOf<Pair<Dp, Dp>?>(null) }

        LaunchedEffect(
            pageIndex, this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight,
            isScrollLocked, lockedState
        ) {
            val currentViewportSize = this@BoxWithConstraints.maxWidth to this@BoxWithConstraints.maxHeight
            val previousViewportSize = previousLockedViewportSize
            val orientationChanged = previousViewportSize != null &&
                (previousViewportSize.first > previousViewportSize.second) !=
                (currentViewportSize.first > currentViewportSize.second)
            previousLockedViewportSize = currentViewportSize

            if (isScrollLocked && !isVerticalScroll) {
                if (orientationChanged) {
                    scale = 1f
                    offset = Offset.Zero
                    hasAppliedLockedPaginationState = true
                    Timber.tag("PdfLockDiagnostic").i(
                        "Orientation changed while locked; reset paginated zoom to fit on page $pageIndex"
                    )
                } else if (lockedState != null) {
                    scale = lockedState.first
                    offset = Offset(lockedState.second, lockedState.third)
                    hasAppliedLockedPaginationState = true
                } else {
                    hasAppliedLockedPaginationState = true
                }
                onScaleChanged(scale)
            } else if (!isScrollLocked && !isVerticalScroll) {
                hasAppliedLockedPaginationState = false
                scale = 1f
                offset = Offset.Zero
                onScaleChanged(1f)
            }
        }

        LaunchedEffect(
            pdfDocumentItem,
            pageIndex,
            ttsHighlightData,
            actualBitmapWidthPx,
            actualBitmapHeightPx,
            currentPageRotation,
            lazyListState,
            isVerticalScroll,
            virtualPage
        ) {
            if (scale > 1f) {
                if (highlightedTextScreenRects.isNotEmpty()) highlightedTextScreenRects =
                    emptyList()
                return@LaunchedEffect
            }

            if (ttsHighlightData == null || actualBitmapWidthPx == 0) {
                if (highlightedTextScreenRects.isNotEmpty()) {
                    Timber.d("Highlighting: Clearing highlights for page $pageIndex.")
                    highlightedTextScreenRects = emptyList()
                }
                return@LaunchedEffect
            }

            if (!isPdfPage) {
                if (highlightedTextScreenRects.isNotEmpty()) highlightedTextScreenRects =
                    emptyList()
                return@LaunchedEffect
            }

            try {
                var rects: List<Rect> = emptyList()

                when (ttsHighlightData) {
                    is TtsHighlightData.Pdfium -> {
                        Timber.d(
                            "Highlighting (Pdfium): page $pageIndex, index: ${ttsHighlightData.startIndex}, len: ${ttsHighlightData.length}"
                        )
                        rects = withContext(Dispatchers.IO) {
                            pdfDocumentItem.openPage(pdfPageIndex)?.use { page ->
                                page.openTextPage().use { textPage ->
                                    val pdfRectsF = textPage.textPageGetRectsForRanges(
                                        intArrayOf(
                                            ttsHighlightData.startIndex, ttsHighlightData.length
                                        )
                                    )?.map { it.rect } ?: emptyList()

                                    if (pdfRectsF.isNotEmpty()) {
                                        pdfRectsF.mapNotNull { pdfRectF ->
                                            page.mapRectToDevice(
                                                startX = 0,
                                                startY = 0,
                                                sizeX = actualBitmapWidthPx,
                                                sizeY = actualBitmapHeightPx,
                                                rotate = currentPageRotation,
                                                coords = pdfRectF
                                            ).takeIf {
                                                it.width() > 0 && it.height() > 0
                                            }
                                        }
                                    } else {
                                        emptyList()
                                    }
                                }
                            } ?: emptyList()
                        }
                    }

                    is TtsHighlightData.Ocr -> {
                        val textToHighlight = ttsHighlightData.text
                        Timber.d(
                            "Highlighting (OCR): page $pageIndex, text: \"${textToHighlight.take(50)}...\""
                        )
                        val ocrTextToUse = ocrVisionTextForSelection ?: try {
                            bitmapState?.let {
                                OcrHelper.extractTextFromBitmap(
                                    it, onOcrModelDownloading
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(
                                e, "Highlighting: On-demand OCR for TTS failed."
                            )
                            null
                        }

                        rects = if (ocrTextToUse != null) {
                            findRectsForTextChunkInOcrVisual(ocrTextToUse, textToHighlight)
                        } else {
                            emptyList()
                        }
                    }
                }

                highlightedTextScreenRects = rects

                if (rects.isNotEmpty() && onTtsHighlightCenterCalculated != null) {
                    val unionRect = RectF()
                    unionRect.set(
                        rects[0].left.toFloat(),
                        rects[0].top.toFloat(),
                        rects[0].right.toFloat(),
                        rects[0].bottom.toFloat()
                    )
                    for (i in 1 until rects.size) {
                        val r = rects[i]
                        unionRect.union(
                            r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()
                        )
                    }
                    onTtsHighlightCenterCalculated(unionRect.centerY())
                }
            } catch (e: Exception) {
                Timber.e(e, "Highlighting: Error calculating highlights for page $pageIndex: $e")
                highlightedTextScreenRects = emptyList()
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(backgroundColor)) {
            val currentContainerMaxWidth = this@BoxWithConstraints.maxWidth
            val currentContainerMaxHeight = this@BoxWithConstraints.maxHeight

            val containerWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
            val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.toPx() }

            val centeringOffsetX = (containerWidthPx - actualBitmapWidthPx) / 2f
            val centeringOffsetY = (containerHeightPx - actualBitmapHeightPx) / 2f

            LaunchedEffect(
                pdfDocumentItem,
                pageIndex,
                isPdfPage,
                pdfPageIndex,
                currentContainerMaxWidth,
                currentContainerMaxHeight,
                density,
                virtualPage,
                targetPageId,
                isVisible,
                currentRenderedPageId
            ) {
                if (!isVisible && !isVerticalScroll) return@LaunchedEffect
                val baseRenderEffectStartNanos = PdfVerticalPerfLog.nowNanos()
                pageErrorMessage = null

                val viewContainerWidthPx = with(density) { currentContainerMaxWidth.toPx().toInt() }
                val viewContainerHeightPx =
                    with(density) { currentContainerMaxHeight.toPx().toInt() }

                if (viewContainerWidthPx <= 0 || viewContainerHeightPx <= 0) {
                    if (bitmapState == null) isLoadingPage = true
                    return@LaunchedEffect
                }

                if (!isPdfPage) {
                    val blankPage = virtualPage as? VirtualPage.BlankPage
                    val pageAspect = if (blankPage != null && blankPage.width > 0 && blankPage.height > 0) {
                        blankPage.width.toFloat() / blankPage.height.toFloat()
                    } else {
                        1f / 1.414f
                    }

                    val (scaledWidth, scaledHeight) = if (isVerticalScroll) {
                        viewContainerWidthPx to viewContainerHeightPx
                    } else {
                        var fittedWidth = viewContainerWidthPx
                        var fittedHeight = (fittedWidth / pageAspect).toInt()

                        if (fittedHeight > viewContainerHeightPx) {
                            fittedHeight = viewContainerHeightPx
                            fittedWidth = (fittedHeight * pageAspect).toInt()
                        }

                        fittedWidth to fittedHeight
                    }

                    if (scaledWidth == actualBitmapWidthPx &&
                        scaledHeight == actualBitmapHeightPx &&
                        bitmapState != null &&
                        currentRenderedPageId == targetPageId
                    ) {
                        if (isVerticalScroll) {
                            PdfVerticalPerfLog.d(
                                "base-render-skip page=$pageIndex reason=current virtual=true logical=${scaledWidth}x$scaledHeight"
                            )
                        }
                        isLoadingPage = false
                        return@LaunchedEffect
                    }

                    actualBitmapWidthPx = scaledWidth
                    actualBitmapHeightPx = scaledHeight
                    currentPageRotation = 0

                    val MAX_BASE_DIMEN = 3000

                    val baseRenderScale = 1.5f

                    var baseW = (scaledWidth * baseRenderScale).toInt()
                    var baseH = (scaledHeight * baseRenderScale).toInt()

                    if (baseW > MAX_BASE_DIMEN || baseH > MAX_BASE_DIMEN) {
                        val downScale = MAX_BASE_DIMEN.toFloat() / maxOf(baseW, baseH)
                        baseW = (baseW * downScale).toInt().coerceAtLeast(1)
                        baseH = (baseH * downScale).toInt().coerceAtLeast(1)
                    }

                    val finalBitmap = PdfBitmapPool.get(baseW, baseH)
                    finalBitmap.eraseColor(android.graphics.Color.WHITE)

                    val old = bitmapState
                    if (old != null && old !== finalBitmap) {
                        if (old !== PdfThumbnailCache.get(targetPageId)) {
                            safeRecyclePdfBitmap(old)
                        }
                    }
                    bitmapState = finalBitmap
                    currentRenderedPageId = targetPageId

                    isLoadingPage = false
                    if (isVerticalScroll) {
                        PdfVerticalPerfLog.d(
                            "base-render-finished page=$pageIndex virtual=true logical=${scaledWidth}x$scaledHeight bitmap=${baseW}x$baseH " +
                                "duration=${PdfVerticalPerfLog.elapsedMs(baseRenderEffectStartNanos)}ms"
                        )
                    }
                    return@LaunchedEffect
                }

                kotlinx.coroutines.coroutineScope {
                    var localBitmap: Bitmap? = null
                    try {
                        val renderResult = withContext(Dispatchers.IO) {
                            val rawPageCount = pdfDocumentItem.getPageCount()
                            if (pdfPageIndex >= rawPageCount) {
                                Timber.w(
                                    "PdfPageComposable: Index $pdfPageIndex out of bounds (count $rawPageCount). Waiting for layout update."
                                )
                                return@withContext null
                            }
                            val page = pdfDocumentItem.openPage(pdfPageIndex) ?: return@withContext null
                            try {
                                val rotation = page.getPageRotation()
                                val originalWidthPdfUnits = page.getPageWidthPoint()
                                val originalHeightPdfUnits = page.getPageHeightPoint()

                                if (originalWidthPdfUnits <= 0 || originalHeightPdfUnits <= 0) {
                                    throw Exception("Invalid page dimensions")
                                }

                                val aspectRatio =
                                    originalWidthPdfUnits.toFloat() / originalHeightPdfUnits.toFloat()
                                val (scaledWidth, scaledHeight) = if (isVerticalScroll) {
                                    viewContainerWidthPx to viewContainerHeightPx
                                } else {
                                    var fittedWidth = viewContainerWidthPx
                                    var fittedHeight = (fittedWidth / aspectRatio).toInt()

                                    if (fittedHeight > viewContainerHeightPx) {
                                        fittedHeight = viewContainerHeightPx
                                        fittedWidth = (fittedHeight * aspectRatio).toInt()
                                    }

                                    fittedWidth to fittedHeight
                                }

                                if (scaledWidth == actualBitmapWidthPx &&
                                    scaledHeight == actualBitmapHeightPx &&
                                    bitmapState != null &&
                                    currentRenderedPageId == targetPageId
                                ) {
                                    if (isVerticalScroll) {
                                        PdfVerticalPerfLog.d(
                                            "base-render-skip page=$pageIndex pdfPage=$pdfPageIndex reason=current logical=${scaledWidth}x$scaledHeight"
                                        )
                                    }
                                    return@withContext null
                                }

                                val MAX_BASE_DIMEN = 3000

                                val baseRenderScale = 1.5f

                                var baseW = (scaledWidth * baseRenderScale).toInt()
                                var baseH = (scaledHeight * baseRenderScale).toInt()

                                if (baseW > MAX_BASE_DIMEN || baseH > MAX_BASE_DIMEN) {
                                    val downScale = MAX_BASE_DIMEN.toFloat() / maxOf(baseW, baseH)
                                    baseW = (baseW * downScale).toInt().coerceAtLeast(1)
                                    baseH = (baseH * downScale).toInt().coerceAtLeast(1)
                                }

                                Timber.d(
                                    "Rendering page $pageIndex at ${baseW}x${baseH} (logical: ${scaledWidth}x${scaledHeight})"
                                )
                                if (isVerticalScroll) {
                                    PdfVerticalPerfLog.d(
                                        "base-render-start page=$pageIndex pdfPage=$pdfPageIndex logical=${scaledWidth}x$scaledHeight " +
                                            "bitmap=${baseW}x$baseH rotation=$rotation externalScale=${PdfVerticalPerfLog.f(externalScale)}"
                                    )
                                }
                                val newBitmap = createBitmap(baseW, baseH)
                                localBitmap = newBitmap
                                val nativeRenderStartNanos = PdfVerticalPerfLog.nowNanos()
                                page.renderPageBitmap(
                                    newBitmap,
                                    0, 0,
                                    baseW, baseH,
                                    true
                                )
                                val nativeRenderMs = PdfVerticalPerfLog.elapsedMs(nativeRenderStartNanos)
                                if (isVerticalScroll) {
                                    PdfVerticalPerfLog.d(
                                        "base-render-native page=$pageIndex pdfPage=$pdfPageIndex duration=${nativeRenderMs}ms bitmap=${baseW}x$baseH"
                                    )
                                }

                                Triple(newBitmap, rotation, Pair(scaledWidth, scaledHeight))
                            } finally {
                                page.close()
                            }
                        }

                        if (renderResult != null) {
                            val (newBitmap, rotation, dims) = renderResult

                            actualBitmapWidthPx = dims.first
                            actualBitmapHeightPx = dims.second
                            currentPageRotation = rotation

                            val old = bitmapState

                            bitmapState = newBitmap
                            localBitmap = null // Handed over successfully
                            currentRenderedPageId = targetPageId

                            withContext(Dispatchers.IO) {
                                if (old != null && old !== newBitmap && !old.isRecycled) {
                                    val cached = PdfThumbnailCache.get(targetPageId)
                                    if (old !== cached) {
                                        safeRecyclePdfBitmap(old)
                                    }
                                }

                                val thumbWidth = newBitmap.width / 2
                                val thumbHeight = newBitmap.height / 2
                                if (thumbWidth > 0 && thumbHeight > 0) {
                                    PdfThumbnailCache.put(
                                        targetPageId, newBitmap.scale(thumbWidth, thumbHeight)
                                    )
                                }
                            }
                            if (isVerticalScroll) {
                                PdfVerticalPerfLog.d(
                                    "base-render-finished page=$pageIndex pdfPage=$pdfPageIndex logical=${dims.first}x${dims.second} " +
                                        "bitmap=${newBitmap.width}x${newBitmap.height} rotation=$rotation duration=${PdfVerticalPerfLog.elapsedMs(baseRenderEffectStartNanos)}ms"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (isVerticalScroll) {
                            PdfVerticalPerfLog.w(
                                "base-render-error page=$pageIndex pdfPage=$pdfPageIndex error=${e.readablePdfErrorDetail()}"
                            )
                        }
                        pageErrorMessage = context.getString(
                            R.string.error_processing_page,
                            e.readablePdfErrorDetail()
                        )
                    } finally {
                        isLoadingPage = false
                        safeRecyclePdfBitmap(localBitmap)
                    }
                }
            }

            when {
                isLoadingPage -> {
                    if (placeholderRenderBitmap != null && !placeholderRenderBitmap.isRecycled) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (!placeholderRenderBitmap.isRecycled) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val bitmapWidth = placeholderRenderBitmap.width
                                val bitmapHeight = placeholderRenderBitmap.height

                                if (bitmapWidth <= 0 || bitmapHeight <= 0) return@Canvas

                                val canvasAspectRatio = canvasWidth / canvasHeight
                                val bitmapAspectRatio =
                                    bitmapWidth.toFloat() / bitmapHeight.toFloat()

                                val dstWidth: Float
                                val dstHeight: Float

                                if (bitmapAspectRatio > canvasAspectRatio) {
                                    dstWidth = canvasWidth
                                    dstHeight = dstWidth / bitmapAspectRatio
                                } else {
                                    dstHeight = canvasHeight
                                    dstWidth = dstHeight * bitmapAspectRatio
                                }

                                val dstOffset = Offset(
                                    x = (canvasWidth - dstWidth) / 2f,
                                    y = (canvasHeight - dstHeight) / 2f
                                )

                                drawImage(
                                    image = placeholderRenderBitmap.asImageBitmap(),
                                    dstOffset = IntOffset(
                                        dstOffset.x.roundToInt(), dstOffset.y.roundToInt()
                                    ),
                                    dstSize = IntSize(
                                        dstWidth.roundToInt(), dstHeight.roundToInt()
                                    )
                                )
                            }
                        }
                    } else {
                        // Blank page
                    }
                }

                pageErrorMessage != null -> {
                    Text(
                        text = pageErrorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.Center)
                    )
                }

                bitmapState != null && actualBitmapWidthPx > 0 && actualBitmapHeightPx > 0 -> {
                    val stableBitmapState = remember(bitmapState) { StableHolder(bitmapState) }
                    val stableTiles = remember(tiles) { StableHolder(tiles) }
                    val stableColorFilter = remember(colorFilter) { StableHolder(colorFilter) }
                    val stableImageRects = remember(imageScreenRects) { StableHolder(imageScreenRects) }
                    val shouldDrawHighResTiles = !shouldPauseHighResTileRendering && needsTilingNow
                    LaunchedEffect(shouldDrawHighResTiles, stableTiles.item.size, effectiveScale) {
                        if (needsTilingNow || stableTiles.item.isNotEmpty()) {
                            pdfZoomDiagnostic(
                                "page=$pageIndex tileVisibility draw=$shouldDrawHighResTiles " +
                                    "tiles=${stableTiles.item.size} scale=$effectiveScale " +
                                    "pause=$shouldPauseHighResTileRendering scrolling=$isScrolling " +
                                    "transforming=$isTransforming pageGesture=$isPaginationPageGestureActive " +
                                    "fling=${paginationPanFlingJob != null} cooldown=$isPageTileRenderIdleCooldownActive"
                            )
                        }
                        if (stableTiles.item.isNotEmpty() && shouldDrawHighResTiles) {
                            PdfVerticalPerfLog.d(
                                "tile-display mode=${if (isVerticalScroll) "vertical" else "pagination"} page=$pageIndex " +
                                    "visible=$shouldDrawHighResTiles tiles=${stableTiles.item.size} pause=$shouldPauseHighResTileRendering " +
                                    "scale=${PdfVerticalPerfLog.f(effectiveScale)}"
                            )
                        }
                    }

                    val staticData = remember(
                        stableBitmapState,
                        stableTiles,
                        shouldDrawHighResTiles,
                        effectiveScale,
                        centeringOffsetX,
                        centeringOffsetY,
                        canvasWidthPx.floatValue,
                        canvasHeightPx.floatValue,
                        actualBitmapWidthPx,
                        actualBitmapHeightPx,
                        stableColorFilter,
                        isDarkMode,
                        excludeImages,
                        effectiveReverseColorMode,
                        stableImageRects,
                        textureBitmap,
                        effectiveTextureAlpha,
                        textureBlendMode
                    ) {
                        Timber.tag("PdfDrawPerf").v(
                            "STATIC DATA GENERATED: Scale=$effectiveScale, Tiles=${stableTiles.item.size}"
                        )
                        PageStaticData(
                            bitmap = stableBitmapState,
                            tiles = stableTiles,
                            shouldDrawHighResTiles = shouldDrawHighResTiles,
                            effectiveScale = effectiveScale,
                            centeringOffsetX = centeringOffsetX,
                            centeringOffsetY = centeringOffsetY,
                            canvasWidth = canvasWidthPx.floatValue,
                            canvasHeight = canvasHeightPx.floatValue,
                            targetWidth = actualBitmapWidthPx,
                            targetHeight = actualBitmapHeightPx,
                            colorFilter = stableColorFilter,
                            isDarkMode = isDarkMode,
                            excludeImages = excludeImages,
                            reverseColorMode = effectiveReverseColorMode,
                            imageRects = stableImageRects,
                            textureBitmap = StableHolder(textureBitmap),
                            textureAlpha = effectiveTextureAlpha,
                            textureBlendMode = textureBlendMode
                        )
                    }

                    val selectionData = remember(
                        pageLinks,
                        showAllTextHighlights,
                        actualBitmapWidthPx,
                        actualBitmapHeightPx,
                        mergedAllTextPageHighlightRects,
                        mergedTtsHighlightRects,
                        mergedSearchHighlightRects,
                        ocrHoverHighlights,
                        mergedSelectionRects,
                        centeringOffsetX,
                        centeringOffsetY,
                        linkHighlightColor,
                        scrimColorForTextHighlight,
                        allTextPageHighlightColor,
                        ttsHighlightColor,
                        searchHighlightColor,
                        selectionHighlightColor,
                        pageIndex,
                        mergedSearchFocusedRects,
                        mergedSearchAllRects,
                        searchHighlightMode,
                        searchFocusedColor,
                        searchAllColor,
                        userHighlightScreenRects,
                        customHighlightColors
                    ) {
                        PageSelectionData(
                            pageLinks = StableHolder(pageLinks),
                            showAllTextHighlights = showAllTextHighlights,
                            actualBitmapWidthPx = actualBitmapWidthPx,
                            actualBitmapHeightPx = actualBitmapHeightPx,
                            mergedAllTextPageHighlightRects = StableHolder(
                                mergedAllTextPageHighlightRects
                            ),
                            mergedTtsHighlightRects = StableHolder(mergedTtsHighlightRects),
                            ocrHoverHighlights = ocrHoverHighlights,
                            mergedSelectionRects = StableHolder(mergedSelectionRects),
                            centeringOffsetX = centeringOffsetX,
                            centeringOffsetY = centeringOffsetY,
                            linkHighlightColor = linkHighlightColor,
                            scrimColorForTextHighlight = scrimColorForTextHighlight,
                            allTextPageHighlightColor = allTextPageHighlightColor,
                            ttsHighlightColor = ttsHighlightColor,
                            selectionHighlightColor = selectionHighlightColor,
                            pageIndex = pageIndex,
                            mergedSearchFocusedRects = StableHolder(mergedSearchFocusedRects),
                            mergedSearchAllRects = StableHolder(mergedSearchAllRects),
                            searchHighlightMode = searchHighlightMode,
                            userHighlightScreenRects = StableHolder(userHighlightScreenRects),
                            customHighlightColors = StableHolder(customHighlightColors)
                        )
                    }

                    val centeringPaddingEnd =
                        with(density) { centeringOffsetX.coerceAtLeast(0f).toDp() }
                    val centeringPaddingTop =
                        with(density) { centeringOffsetY.coerceAtLeast(0f).toDp() }

                    PdfPageRenderer(
                        staticData = staticData,
                        selectionData = selectionData,
                        totalPages = totalPages,
                        annotationsProvider = pageAnnotations,
                        drawingState = drawingState,
                        onCanvasSizeChanged = { w, h ->
                            if (canvasWidthPx.floatValue != w || canvasHeightPx.floatValue != h) {
                                canvasWidthPx.floatValue = w
                                canvasHeightPx.floatValue = h
                            }
                        },
                        scale = scale,
                        uiScale = effectiveScale,
                        offset = offset,
                        startHandlePos = startHandleContentPosition.value,
                        endHandlePos = endHandleContentPosition.value,
                        teardropWidthPx = teardropWidthPxState.value,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightUpdate = onHighlightUpdate,
                        onHighlightDelete = onHighlightDelete,
                        onTts = onTts,
                        onNote = onNoteRequested,
                        teardropHeightPx = teardropHeightPxState.value,
                        activeDraggingHandle = activeDraggingHandle,
                        showMagnifier = showMagnifier,
                        magnifierCenterTarget = magnifierBitmapCenterTarget,
                        magnifierZoomFactor = magnifierZoomFactor,
                        menuState = customMenuState,
                        onMenuDismiss = {
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation,
                                )
                            }
                        },
                        onCopy = { textToCopy ->
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation
                                )
                            }
                        },
                        onAiDefine = { textToDefine ->
                            onWordSelectedForAiDefinition(textToDefine.trim())
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation
                                )
                            }
                        },
                        onTranslate = { textToTranslate ->
                            onTranslateText(textToTranslate.trim())
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation
                                )
                            }
                        },
                        onSearch = { textToSearch ->
                            onSearchText(textToSearch.trim())
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation
                                )
                            }
                        },
                        onSelectAll = {
                            customMenuState = null
                            coroutineScope.launch {
                                if (!isPdfPage) return@launch

                                if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                                    var page: ReaderPage? = null
                                    var textPage: ReaderTextPage? = null
                                    try {
                                        val charCount = withContext(Dispatchers.IO) {
                                            page = pdfDocumentItem.openPage(pdfPageIndex)
                                            textPage = page?.openTextPage()
                                            textPage?.textPageCountChars() ?: 0
                                        }

                                        if (charCount > 0) {
                                            selectionCharRange.value = Pair(0, charCount)
                                            val tPage = page
                                            val tTextPage = textPage
                                            updateSelectionVisuals(
                                                pdfDocumentItem,
                                                pdfPageIndex,
                                                selectionCharRange.value,
                                                actualBitmapWidthPx,
                                                actualBitmapHeightPx,
                                                currentPageRotation,
                                                providedPage = tPage,
                                                providedTextPage = tTextPage
                                            )
                                            if (selectedWordScreenRects.isNotEmpty()) {
                                                val fullText = withContext(Dispatchers.IO) {
                                                    tTextPage?.textPageGetText(0, charCount)
                                                }
                                                if (!fullText.isNullOrBlank()) {
                                                    val combinedRect = Rect(selectedWordScreenRects.first())
                                                    selectedWordScreenRects.forEach { combinedRect.union(it) }
                                                    customMenuState = CustomPdfMenuState(
                                                        selectedText = fullText,
                                                        anchorRect = combinedRect,
                                                        charRange = selectionCharRange.value!!
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to select all")
                                    } finally {
                                        withContext(NonCancellable) {
                                            withContext(Dispatchers.IO) {
                                                textPage?.close()
                                                page?.close()
                                            }
                                        }
                                    }
                                } else {
                                    if (allOcrSymbolsForSelection.isNotEmpty()) {
                                        ocrSelectionSymbolIndices =
                                            Pair(0, allOcrSymbolsForSelection.size)
                                        updateOcrSymbolSelectionRectsAndHandles(
                                            ocrSelectionSymbolIndices
                                        )
                                        if (selectedWordScreenRects.isNotEmpty()) {
                                            val fullText = buildString {
                                                allOcrSymbolsForSelection.forEachIndexed { index, info ->
                                                    append(info.symbol.text)
                                                    if (index < allOcrSymbolsForSelection.size - 1) {
                                                        val nextInfo =
                                                            allOcrSymbolsForSelection[index + 1]
                                                        if (info.parentLine !== nextInfo.parentLine) append(
                                                            '\n'
                                                        )
                                                        else if (info.parentElement !== nextInfo.parentElement) append(
                                                            ' '
                                                        )
                                                    }
                                                }
                                            }
                                            if (fullText.isNotBlank()) {
                                                val combinedRect = Rect(selectedWordScreenRects.first())
                                                selectedWordScreenRects.forEach { combinedRect.union(it) }
                                                customMenuState = CustomPdfMenuState(
                                                    selectedText = fullText,
                                                    anchorRect = combinedRect,
                                                    charRange = Pair(-1, -1)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onShowUpsellDialog = {
                            onShowDictionaryUpsellDialog()
                            customMenuState = null
                            selectionCharRange.value = null
                            coroutineScope.launch {
                                updateSelectionVisuals(
                                    pdfDocumentItem,
                                    pdfPageIndex,
                                    null,
                                    actualBitmapWidthPx,
                                    actualBitmapHeightPx,
                                    currentPageRotation
                                )
                            }
                        },
                        isProUser = isProUser,
                        isBookmarked = isBookmarked,
                        onBookmarkClick = onBookmarkClick,
                        centeringPaddingTop = centeringPaddingTop,
                        centeringPaddingEnd = centeringPaddingEnd,
                        isPerformingOcr = isPerformingOcrForSelection,
                        ocrRipplePos = ocrRipplePosition,
                        layoutCoordinates = layoutCoordinates,
                        contentToScreenCoordinates = contentToScreenCoordinates,
                        density = density,
                        isVerticalScroll = isVerticalScroll,
                        showPageNumberOverlay = showPageNumberOverlay,
                        isScrolling = isScrolling,
                        isEditMode = isEditMode,
                        selectedTool = selectedTool,
                        eraserPosition = eraserPosition,
                        isStylusEraserOverride = isStylusEraserOverride,
                        activeToolThickness = activeToolThickness,
                        eraserToolThickness = eraserToolThickness,
                        richTextController = richTextController,
                        textBoxes = textBoxes,
                        selectedTextBoxId = selectedTextBoxId,
                        onTextBoxChange = onTextBoxChange,
                        onTextBoxSelect = onTextBoxSelect,
                        onTextBoxDragStart = onTextBoxDragStart,
                        onTextBoxDrag = onTextBoxDrag,
                        onTextBoxDragEnd = onTextBoxDragEnd,
                        onDragPageTurn = onDragPageTurn,
                        draggingBoxId = draggingBoxId,
                        customHighlightColors = customHighlightColors,
                        onPaletteClick = onPaletteClick,
                        isBubbleZoomModeActive = isBubbleZoomModeActive,
                        isActivePage = isActivePage,
                        isDetectingBubbles = isDetectingBubbles,
                        detectedBubbles = detectedBubbles,
                        animatingBubbleIndex = animatingBubbleIndex,
                        bubbleExpansionProgress = bubbleExpansionProgress.value,
                        expandedBubbleRender = expandedBubbleRender
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.error_unable_to_display_page, pageIndex + 1),
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}
