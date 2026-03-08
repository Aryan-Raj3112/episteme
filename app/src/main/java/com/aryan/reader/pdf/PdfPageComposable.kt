// PdfPageComposable
@file:Suppress(
    "RemoveRedundantQualifierName", "COMPOSE_APPLIER_CALL_MISMATCH", "UnusedVariable", "unused"
)
package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.set
import com.aryan.reader.R
import com.aryan.reader.SearchResult
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrResult
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor
import android.graphics.Paint as NativePaint

enum class Handle {
    START, END
}

enum class AnnotationType {
    INK, TEXT
}

enum class InkType {
    PEN, HIGHLIGHTER, HIGHLIGHTER_ROUND, ERASER, FOUNTAIN_PEN, PENCIL, TEXT
}

data class PdfPoint(val x: Float, val y: Float, val timestamp: Long = 0L)

data class PdfTile(val bitmap: Bitmap, val renderRect: Rect, val tileId: Int)

enum class LinkSource {
    ANNOTATION, TEXT_CONTENT
}

data class PageLink(
    val highlightBounds: Rect,
    val tapBounds: Rect,
    val url: String?,
    val destPageIdx: Int?,
    val source: LinkSource
)

object PdfInkGeometry {
    fun calculateFountainPenPoints(
        points: List<PdfPoint>, baseWidth: Float, pageWidth: Float, pageHeight: Float
    ): Pair<List<Offset>, List<Offset>> {
        if (points.size < 2) return Pair(emptyList(), emptyList())

        if (points.size % 50 == 0) {
            Timber.tag("FountainPenDebug").d(
                "Calculate Points: PWidth=$pageWidth, PHeight=$pageHeight, BaseW=$baseWidth, Pts=${points.size}"
            )
        }

        val leftSide = mutableListOf<Offset>()
        val rightSide = mutableListOf<Offset>()

        val computedWidths = FloatArray(points.size)
        computedWidths[0] = baseWidth

        val velocityFactor = 300f

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]

            val dxNorm = p2.x - p1.x
            val dyNorm = p2.y - p1.y
            val aspect = if (pageWidth > 0 && pageHeight > 0) pageHeight / pageWidth else 1f
            val distNorm = sqrt(dxNorm * dxNorm + (dyNorm * aspect) * (dyNorm * aspect))

            val timeDelta = (p2.timestamp - p1.timestamp).coerceAtLeast(1)
            val velocityNorm = distNorm / timeDelta

            val targetWidth = (baseWidth * (1f / (1f + velocityNorm * velocityFactor))).coerceIn(
                baseWidth * 0.2f, baseWidth * 1.4f
            )

            computedWidths[i] = computedWidths[i - 1] * 0.6f + targetWidth * 0.4f

            if (i < 5) {
                Timber.tag("FountainPenDebug").v(
                    "Pt[$i]: dt=$timeDelta, velNorm=$velocityNorm, width=${computedWidths[i]} (base=$baseWidth)"
                )
            }
        }

        for (i in 0 until points.size - 1) {
            val pCurrent = points[i]
            val pNext = points[i + 1]

            val curX = pCurrent.x * pageWidth
            val curY = pCurrent.y * pageHeight
            val nextX = pNext.x * pageWidth
            val nextY = pNext.y * pageHeight

            val angle = atan2(nextY - curY, nextX - curX)
            val normalAngle = angle - (PI / 2f).toFloat()

            val w = computedWidths[i] / 2f

            leftSide.add(Offset((curX + cos(normalAngle) * w), (curY + sin(normalAngle) * w)))
            rightSide.add(Offset((curX - cos(normalAngle) * w), (curY - sin(normalAngle) * w)))
        }

        val lastIdx = points.lastIndex
        val lastP = points[lastIdx]
        val prevP = points[lastIdx - 1]

        val lastX = lastP.x * pageWidth
        val lastY = lastP.y * pageHeight
        val prevX = prevP.x * pageWidth
        val prevY = prevP.y * pageHeight

        val lastAngle = atan2(lastY - prevY, lastX - prevX)
        val lastNormal = lastAngle - (PI / 2f).toFloat()
        val lastW = computedWidths[lastIdx] / 2f

        leftSide.add(Offset((lastX + cos(lastNormal) * lastW), (lastY + sin(lastNormal) * lastW)))
        rightSide.add(Offset((lastX - cos(lastNormal) * lastW), (lastY - sin(lastNormal) * lastW)))

        return Pair(leftSide, rightSide)
    }
}

internal object PdfBitmapPool {
    private val pool = ConcurrentLinkedQueue<Bitmap>()
    private const val MAX_POOL_SIZE = 4

    fun get(size: Int): Bitmap {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            if (b.width == size && b.height == size && !b.isRecycled) {
                iterator.remove()
                b.eraseColor(AndroidColor.TRANSPARENT)
                return b
            }
        }
        return createBitmap(size, size)
    }

    fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled && pool.size < MAX_POOL_SIZE) {
            pool.offer(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    fun clear() {
        while (!pool.isEmpty()) {
            pool.poll()?.recycle()
        }
    }
}

internal object PdfThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private data class CacheEntry(val bitmap: Bitmap, val sizeKb: Int)

    private val memoryCache = object : LruCache<Int, CacheEntry>(cacheSize) {
        override fun sizeOf(key: Int, entry: CacheEntry): Int {
            return entry.sizeKb
        }
    }

    fun get(pageIndex: Int): Bitmap? {
        return memoryCache.get(pageIndex)?.bitmap?.takeUnless { it.isRecycled }
    }

    fun put(pageIndex: Int, bitmap: Bitmap) {
        if (get(pageIndex) == null) {
            val sizeKb = (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
            memoryCache.put(pageIndex, CacheEntry(bitmap, sizeKb))
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

@Stable
data class StableHolder<T>(val item: T)

@Stable
data class PageStaticData(
    val bitmap: StableHolder<Bitmap?>,
    val tiles: StableHolder<List<PdfTile>>,
    val effectiveScale: Float,
    val centeringOffsetX: Float,
    val centeringOffsetY: Float,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val targetWidth: Int,
    val targetHeight: Int,
    val colorFilter: StableHolder<ColorFilter?>,
    val isDarkMode: Boolean
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
    val pageIndex: Int
)

@Suppress("unused")
@Composable
internal fun PdfPageComposable(
    pdfDocument: StableHolder<PdfDocumentKt>,
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
    onSingleTap: () -> Unit,
    isProUser: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
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
    visualScaleProvider: () -> Float = { 1f },
    clearSelectionTrigger: Long = 0L,
    onTtsHighlightCenterCalculated: ((Float) -> Unit)? = null,
    onSearchHighlightCenterCalculated: ((Float) -> Unit)? = null,
    isDarkMode: Boolean = false,
    onDoubleTap: ((Offset) -> Unit)? = null,
    isEditMode: Boolean = false,
    drawingState: PdfDrawingState? = null,
    pageAnnotations: () -> List<PdfAnnotation> = { emptyList() },
    onDrawStart: (PdfPoint) -> Unit = {},
    onDraw: (PdfPoint) -> Unit = {},
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
    isStylusOnlyMode: Boolean = false,
    isHighlighterSnapEnabled: Boolean = false
) {
    SideEffect { Timber.tag("PdfDrawPerf").v("PdfPageComposable Recompose: Page $pageIndex") }
    val pdfDocumentItem = pdfDocument.item
    var bitmapState by remember { mutableStateOf(PdfThumbnailCache.get(pageIndex)) }
    var currentRenderedPageId by remember { mutableStateOf<String?>(null) }

    val targetPageId = remember(virtualPage, pageIndex) {
        when (virtualPage) {
            is VirtualPage.BlankPage -> "BLANK_${virtualPage.id}"
            is VirtualPage.PdfPage -> "PDF_${virtualPage.pdfIndex}"
            null -> "PDF_$pageIndex"
        }
    }
    var isLoadingPage by remember { mutableStateOf(true) }
    var pageErrorMessage by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    LocalContext.current
    val viewConfiguration = LocalViewConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    Timber.d(
        "PdfPageComposable recompose: page=$pageIndex, isScrolling=$isScrolling, visualScale=$visualScaleProvider"
    )

    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var ocrRipplePosition by remember { mutableStateOf<Offset?>(null) }

    var isTransforming by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    val effectiveScale = if (isZoomEnabled && !isVerticalScroll) scale else externalScale
    val effectiveOffset = if (isZoomEnabled && !isVerticalScroll) offset else Offset.Zero

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

    var tiles by remember { mutableStateOf<List<PdfTile>>(emptyList()) }
    val tileSizeDp = 256.dp
    val tileSizePx = with(LocalDensity.current) { tileSizeDp.toPx().toInt() }

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

    val selectionCharRange = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var activeDraggingHandle by remember { mutableStateOf<Handle?>(null) }
    var selectedWordScreenRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val startHandleContentPosition = remember { mutableStateOf<Offset?>(null) }
    val endHandleContentPosition = remember { mutableStateOf<Offset?>(null) }

    var actualBitmapWidthPx by remember { mutableIntStateOf(0) }
    var actualBitmapHeightPx by remember { mutableIntStateOf(0) }
    var currentPageRotation by remember { mutableIntStateOf(0) }

    val canvasWidthPx = remember { mutableFloatStateOf(0f) }
    val canvasHeightPx = remember { mutableFloatStateOf(0f) }

    val colorFilter = remember(isDarkMode) {
        if (isDarkMode) {
            val colorMatrix = floatArrayOf(
                -1f,
                0f,
                0f,
                0f,
                255f,
                0f,
                -1f,
                0f,
                0f,
                255f,
                0f,
                0f,
                -1f,
                0f,
                255f,
                0f,
                0f,
                0f,
                1f,
                0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        } else {
            null
        }
    }

    val backgroundColor = remember(isDarkMode, isVerticalScroll) {
        if (isDarkMode) {
            Color(0xFF2A2A2A)
        } else {
            if (isVerticalScroll) Color.White else Color.Black
        }
    }

    val centeringOffsetX by remember(canvasWidthPx.floatValue, actualBitmapWidthPx) {
        derivedStateOf { (canvasWidthPx.floatValue - actualBitmapWidthPx) / 2f }
    }
    val centeringOffsetY by remember(canvasHeightPx.floatValue, actualBitmapHeightPx) {
        derivedStateOf { (canvasHeightPx.floatValue - actualBitmapHeightPx) / 2f }
    }

    LaunchedEffect(centeringOffsetX, centeringOffsetY, pageIndex) {
        Timber.d(
            "PdfPageComposable Page $pageIndex | Centering Offset: x=$centeringOffsetX, y=$centeringOffsetY"
        )
    }

    var showMagnifier by remember { mutableStateOf(false) }
    var magnifierBitmapCenterTarget by remember { mutableStateOf(Offset.Zero) }
    val magnifierZoomFactor = 2.0f

    var customMenuState by remember { mutableStateOf<CustomPdfMenuState?>(null) }

    val inputScale = if (isZoomEnabled && !isVerticalScroll) scale else 1f
    val inputOffset = if (isZoomEnabled && !isVerticalScroll) offset else Offset.Zero

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

    DisposableEffect(Unit) {
        onDispose {
            val currentBitmap = bitmapState
            val cachedBitmap = PdfThumbnailCache.get(pageIndex)
            if (currentBitmap != null && !currentBitmap.isRecycled && currentBitmap !== cachedBitmap) {
                currentBitmap.recycle()
            }
        }
    }

    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current

    // OCR
    var ocrVisionTextForSelection by remember { mutableStateOf<OcrResult?>(null) }
    var isPerformingOcrForSelection by remember { mutableStateOf(false) }
    var selectionMethodUsed by remember { mutableStateOf(PdfSelectionMethod.PDFIUM) }
    var ocrSelectionSymbolIndices by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var allOcrSymbolsForSelection by remember { mutableStateOf<List<OcrSymbolInfo>>(emptyList()) }

    var highlightedTextScreenRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val ttsHighlightColor = Color(0xFFFFECB3).copy(alpha = 0.4f)

    var allTextPageHighlightRects by remember { mutableStateOf<List<Rect>>(emptyList()) }

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

    var pageLinks by remember { mutableStateOf<List<PageLink>>(emptyList()) }
    val linkHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val linkVerticalPaddingPx = remember(density) { with(density) { 10.dp.toPx().toInt() } }

    LaunchedEffect(pageIndex) {
        scale = 1f
        offset = Offset.Zero
        onScaleChanged(1f)
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
        var tempPage: PdfPageKt? = null

        try {
            withContext(Dispatchers.IO) {
                tempPage = pdfDocumentItem.openPage(pdfPageIndex)
                tempPage.openTextPage().use { textPage ->
                    val charCount = textPage.textPageCountChars()
                    if (charCount > 0) {
                        val pdfRectsF =
                            textPage.textPageGetRectsForRanges(intArrayOf(0, charCount))?.map {
                                it.rect
                            } ?: emptyList()

                        if (pdfRectsF.isNotEmpty()) {
                            val mappedScreenRects = pdfRectsF.mapNotNull { pdfRectF ->
                                tempPage.mapRectToDevice(
                                    startX = 0,
                                    startY = 0,
                                    sizeX = actualBitmapWidthPx,
                                    sizeY = actualBitmapHeightPx,
                                    rotate = currentPageRotation,
                                    coords = pdfRectF
                                ).takeIf { it.width() > 0 && it.height() > 0 }
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

    LaunchedEffect(pageIndex, pdfDocumentItem, actualBitmapWidthPx, actualBitmapHeightPx, virtualPage) {
        if (!isPdfPage) {
            if (pageLinks.isNotEmpty()) pageLinks = emptyList()
            return@LaunchedEffect
        }

        if (actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0) {
            if (pageLinks.isNotEmpty()) pageLinks = emptyList()
            return@LaunchedEffect
        }
        Timber.d(
            "LaunchedEffect: Starting link fetch for page $pageIndex. Bitmap size: ${actualBitmapWidthPx}x${actualBitmapHeightPx}"
        )

        withContext(Dispatchers.IO) {
            val allLinks = mutableListOf<PageLink>()
            try {
                pdfDocumentItem.openPage(pdfPageIndex).use { page ->
                    try {
                        val annotationLinks = page.getPageLinks()
                        Timber.d("Method 1 (getPageLinks) returned ${annotationLinks.size} links.")
                        if (annotationLinks.isNotEmpty()) {
                            val mappedAnnotationLinks = annotationLinks.mapNotNull { link ->
                                val uri = link.uri
                                val destPageIdx = link.destPageIdx
                                val bounds = link.bounds

                                if (uri != null || (destPageIdx != null && destPageIdx >= 0)) {
                                    val deviceRect = page.mapRectToDevice(
                                        startX = 0,
                                        startY = 0,
                                        sizeX = actualBitmapWidthPx,
                                        sizeY = actualBitmapHeightPx,
                                        rotate = currentPageRotation,
                                        coords = bounds
                                    )

                                    Timber.d(
                                        "[Method 1] Link '$uri' | PDF Bounds: $bounds | Mapped Device Rect: $deviceRect"
                                    )

                                    if (deviceRect.width() > 0 && deviceRect.height() > 0) {
                                        val tapRect = Rect(
                                            deviceRect.left,
                                            deviceRect.top - linkVerticalPaddingPx,
                                            deviceRect.right,
                                            deviceRect.bottom + linkVerticalPaddingPx
                                        )
                                        Timber.d(
                                            "[Method 1] Padded Tap Rect: $tapRect (Padding: $linkVerticalPaddingPx)"
                                        )
                                        PageLink(
                                            highlightBounds = deviceRect,
                                            tapBounds = tapRect,
                                            url = uri,
                                            destPageIdx = destPageIdx,
                                            source = LinkSource.ANNOTATION
                                        )
                                    } else null
                                } else null
                            }
                            allLinks.addAll(mappedAnnotationLinks)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error fetching annotation links")
                    }

                    // --- METHOD 2: Get links detected within the text content
                    // ---
                    try {
                        page.openTextPage().use { textPage ->
                            textPage.loadWebLink().use { webLinks ->
                                val webLinkCount = webLinks.countWebLinks()
                                Timber.d("Method 2 (loadWebLink) returned $webLinkCount links.")
                                for (linkIndex in 0 until webLinkCount) {
                                    val rawUrl = webLinks.getURL(linkIndex, 2048)
                                    val url = rawUrl?.substringBefore('\u0000')

                                    if (url.isNullOrBlank()) continue

                                    val rectCount = webLinks.countRects(linkIndex)
                                    for (rectIndex in 0 until rectCount) {
                                        val pdfRect = webLinks.getRect(linkIndex, rectIndex)
                                        val deviceRect = page.mapRectToDevice(
                                            startX = 0,
                                            startY = 0,
                                            sizeX = actualBitmapWidthPx,
                                            sizeY = actualBitmapHeightPx,
                                            rotate = currentPageRotation,
                                            coords = pdfRect
                                        )
                                        Timber.d(
                                            "[Method 2] Link '$url' | PDF Rect: $pdfRect | Mapped Device Rect: $deviceRect"
                                        )

                                        if (deviceRect.width() > 0 && deviceRect.height() > 0) {
                                            val tapRect = Rect(
                                                deviceRect.left,
                                                deviceRect.top - linkVerticalPaddingPx,
                                                deviceRect.right,
                                                deviceRect.bottom + linkVerticalPaddingPx
                                            )
                                            Timber.d(
                                                "[Method 2] Padded Tap Rect: $tapRect (Padding: $linkVerticalPaddingPx)"
                                            )
                                            allLinks.add(
                                                PageLink(
                                                    highlightBounds = deviceRect,
                                                    tapBounds = tapRect,
                                                    url = url,
                                                    destPageIdx = null,
                                                    source = LinkSource.TEXT_CONTENT
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error fetching web links from text page")
                    }

                    pageLinks = allLinks
                    Timber.d(
                        "Finished fetching. Stored a total of ${allLinks.size} links in state."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to open page $pdfPageIndex for link fetching")
                pageLinks = emptyList()
            }
        }
    }

    LaunchedEffect(isVisible, pageIndex) {
        if (!isVisible && !isVerticalScroll) {
            if (bitmapState != null) {
                Timber.d("Page $pageIndex hidden. Releasing bitmap to save memory.")
                val old = bitmapState
                bitmapState = null
                @Suppress("ControlFlowWithEmptyBody") if (old != null && old !== PdfThumbnailCache.get(pageIndex)) { }
            }
        }
    }

    LaunchedEffect(
        effectiveScale,
        effectiveOffset,
        actualBitmapWidthPx,
        actualBitmapHeightPx,
        canvasWidthPx.floatValue,
        canvasHeightPx.floatValue,
        isVerticalScroll,
        isScrolling,
        virtualPage
    ) {
        if (effectiveScale <= 1f) {
            if (tiles.isNotEmpty()) {
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

        if (actualBitmapWidthPx == 0 || actualBitmapHeightPx == 0 || screenWidth == 0f || screenHeight == 0f) return@LaunchedEffect

        var page: PdfPageKt? = null

        if (!isPdfPage) {
            if (tiles.isNotEmpty()) {
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

            snapshotFlow { visibleScreenRect() }.collectLatest { currentVisibleRect ->
                val tileCalcStart = System.nanoTime()
                if (!isActive) return@collectLatest

                if (isScrolling && effectiveScale > 1f) {
                    return@collectLatest
                }

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

                    pxTl =
                        (((0 - effectiveOffset.x) - pivotX) / effectiveScale + pivotX) - centeringOffsetX
                    pyTl =
                        (((0 - effectiveOffset.y) - pivotY) / effectiveScale + pivotY) - centeringOffsetY
                    pxBr =
                        (((screenWidth - effectiveOffset.x) - pivotX) / effectiveScale + pivotX) - centeringOffsetX
                    pyBr =
                        (((screenHeight - effectiveOffset.y) - pivotY) / effectiveScale + pivotY) - centeringOffsetY
                }

                val visibleBitmapRect = Rect(pxTl.toInt(), pyTl.toInt(), pxBr.toInt(), pyBr.toInt())
                val inset = if (effectiveScale > 2f) 0 else -tileSizePx
                visibleBitmapRect.inset(inset, inset)

                val requiredTileIds = mutableSetOf<Int>()
                val cols = (actualBitmapWidthPx + tileSizePx - 1) / tileSizePx
                val startCol = (visibleBitmapRect.left / tileSizePx).coerceAtLeast(0)
                val endCol =
                    ((visibleBitmapRect.right + tileSizePx - 1) / tileSizePx).coerceAtMost(cols)
                val startRow = (visibleBitmapRect.top / tileSizePx).coerceAtLeast(0)
                val endRow =
                    ((visibleBitmapRect.bottom + tileSizePx - 1) / tileSizePx).coerceAtMost(
                        (actualBitmapHeightPx + tileSizePx - 1) / tileSizePx
                    )

                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        requiredTileIds.add(row * cols + col)
                    }
                }

                val currentTileIds = tiles.map { it.tileId }.toSet()

                val duration = (System.nanoTime() - tileCalcStart) / 1_000_000f
                if (duration > 2f) {
                    Timber.tag("PdfPerformance").d(
                        "Page $pageIndex | Tile Calc took ${duration}ms | Tiles Needed: ${requiredTileIds.size}"
                    )
                }

                if (requiredTileIds != currentTileIds) {

                    val tilesToRenderIds = requiredTileIds - currentTileIds
                    val tilesToRecycleIds = currentTileIds - requiredTileIds

                    if (tilesToRecycleIds.isNotEmpty()) {
                        val (tilesToRecycle, tilesToKeep) = tiles.partition { it.tileId in tilesToRecycleIds }
                        tiles = tilesToKeep
                        withContext(Dispatchers.IO) {
                            tilesToRecycle.forEach { PdfBitmapPool.recycle(it.bitmap) }
                        }
                    }

                    if (tilesToRenderIds.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            tilesToRenderIds.forEach { tileId ->
                                if (!isActive) return@forEach

                                yield()

                                val row = tileId / cols
                                val col = tileId % cols
                                val tileRect = Rect(
                                    col * tileSizePx,
                                    row * tileSizePx,
                                    (col + 1) * tileSizePx,
                                    (row + 1) * tileSizePx
                                )
                                val tileRenderSize =
                                    (tileSizePx * effectiveScale).toInt().coerceAtLeast(1)

                                val tileBitmap = PdfBitmapPool.get(tileRenderSize)

                                val fullPageRenderWidth =
                                    (actualBitmapWidthPx * effectiveScale).toInt()
                                val fullPageRenderHeight =
                                    (actualBitmapHeightPx * effectiveScale).toInt()
                                val tileRenderX = (col * tileSizePx * effectiveScale).toInt()
                                val tileRenderY = (row * tileSizePx * effectiveScale).toInt()

                                page.renderPageBitmap(
                                    bitmap = tileBitmap,
                                    startX = -tileRenderX,
                                    startY = -tileRenderY,
                                    drawSizeX = fullPageRenderWidth,
                                    drawSizeY = fullPageRenderHeight,
                                    renderAnnot = false
                                )

                                val newTile = PdfTile(tileBitmap, tileRect, tileId)
                                withContext(Dispatchers.Main) {
                                    if (isActive) {
                                        tiles = tiles + newTile
                                    } else {
                                        PdfBitmapPool.recycle(tileBitmap)
                                    }
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

    var searchFocusedRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var searchAllRects by remember { mutableStateOf<List<Rect>>(emptyList()) }

    var keyboardAdjustmentOriginalOffset by remember { mutableStateOf<Float?>(null) }

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
                pdfDocumentItem.openPage(pdfPageIndex).use { page ->
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
        if (symbols.isEmpty()) return -1

        val containingSymbolIndex = symbols.indexOfFirst {
            it.symbol.boundingBox?.contains(x.toInt(), y.toInt()) == true
        }
        if (containingSymbolIndex != -1) return containingSymbolIndex

        var minDistanceSq = Float.MAX_VALUE
        var closestSymbolIndex = -1

        symbols.forEachIndexed { index, info ->
            info.symbol.boundingBox?.let { box ->
                val distSq = (x - box.exactCenterX()).pow(2) + (y - box.exactCenterY()).pow(2)
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq
                    closestSymbolIndex = index
                }
            }
        }
        return closestSymbolIndex
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
        doc: PdfDocumentKt,
        pageIdx: Int,
        charRange: Pair<Int, Int>?,
        currentBitmapWidth: Int,
        currentBitmapHeight: Int,
        rotation: Int,
        providedPage: PdfPageKt? = null,
        providedTextPage: PdfTextPageKt? = null
    ) {
        if (charRange == null || currentBitmapWidth == 0 || currentBitmapHeight == 0) {
            selectedWordScreenRects = emptyList()
            startHandleContentPosition.value = null
            endHandleContentPosition.value = null
            return
        }

        var localPage: PdfPageKt? = null
        var localTextPage: PdfTextPageKt? = null

        try {
            val pageToUse: PdfPageKt
            val textPageToUse: PdfTextPageKt

            if (providedPage != null && providedTextPage != null) {
                pageToUse = providedPage
                textPageToUse = providedTextPage
            } else {
                localPage = doc.openPage(pageIdx)
                localTextPage = localPage.openTextPage()
                pageToUse = localPage
                textPageToUse = localTextPage
            }

            val (startIndex, endIndex) = charRange
            if (startIndex >= endIndex) {
                selectedWordScreenRects = emptyList()
                startHandleContentPosition.value = null
                endHandleContentPosition.value = null
                return
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
            } else {
                selectedWordScreenRects = emptyList()
                startHandleContentPosition.value = null
                endHandleContentPosition.value = null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating selection visuals for page $pageIdx, range $charRange: $e")
            selectedWordScreenRects = emptyList()
            startHandleContentPosition.value = null
            endHandleContentPosition.value = null
        } finally {
            if (providedPage == null && providedTextPage == null) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
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

                    if (selectedTool == InkType.ERASER) {
                        eraserPosition = down.position
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

                        coroutineScope.launch(Dispatchers.IO) {
                            var pageForDrag: PdfPageKt? = null
                            var textPageForDrag: PdfTextPageKt? = null

                            try {
                                if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                                    if (isPdfPage) {
                                        pageForDrag = pdfDocumentItem.openPage(
                                            pdfPageIndex
                                        )
                                        textPageForDrag = pageForDrag.openTextPage()
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
                                                        val (start, end) = currentRange
                                                        when (activeDraggingHandle) {
                                                            Handle.START -> {
                                                                if (targetSymbolIndex >= end - 1) {
                                                                    activeDraggingHandle =
                                                                        Handle.END
                                                                    ocrSelectionSymbolIndices =
                                                                        Pair(
                                                                            end - 1,
                                                                            targetSymbolIndex + 1
                                                                        )
                                                                } else {
                                                                    ocrSelectionSymbolIndices =
                                                                        Pair(
                                                                            targetSymbolIndex, end
                                                                        )
                                                                }
                                                            }

                                                            Handle.END -> {
                                                                if (targetSymbolIndex + 1 <= start + 1) {
                                                                    activeDraggingHandle =
                                                                        Handle.START
                                                                    ocrSelectionSymbolIndices =
                                                                        Pair(
                                                                            targetSymbolIndex,
                                                                            start + 1
                                                                        )
                                                                } else {
                                                                    ocrSelectionSymbolIndices =
                                                                        Pair(
                                                                            start,
                                                                            targetSymbolIndex + 1
                                                                        )
                                                                }
                                                            }

                                                            else -> {}
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
                                            if (pageForDrag != null && textPageForDrag != null) {
                                                val touchInContentCoords =
                                                    screenToContentCoordinates(
                                                        dragPosition
                                                    )

                                                val pdfCoords = pageForDrag.mapDeviceCoordsToPage(
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
                                                    textPageForDrag.textPageGetCharIndexAtPos(
                                                        x = pdfCoords.x.toDouble(),
                                                        y = pdfCoords.y.toDouble(),
                                                        xTolerance = charTolerance,
                                                        yTolerance = charTolerance
                                                    )

                                                if (charIndexForUpdate == -1 && activeDraggingHandle != null) {
                                                    val pageWidthPdfUnits =
                                                        pageForDrag.getPageWidthPoint()
                                                    val wideSearchXTolerance =
                                                        pageWidthPdfUnits.toDouble()
                                                    var ySearchCoordinate = pdfCoords.y.toDouble()

                                                    val currentRange = selectionCharRange.value
                                                    if (currentRange != null) {
                                                        val pageTotalChars =
                                                            textPageForDrag.textPageCountChars()
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
                                                                    textPageForDrag.textPageGetCharBox(
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
                                                            textPageForDrag.textPageGetCharIndexAtPos(
                                                                x = pdfCoords.x.toDouble(),
                                                                y = ySearchCoordinate,
                                                                xTolerance = wideSearchXTolerance,
                                                                yTolerance = wideSearchYTolerance
                                                            )
                                                    }
                                                }

                                                if (charIndexForUpdate != -1) {
                                                    val pageCharCount =
                                                        textPageForDrag.textPageCountChars()

                                                    val currentRange = selectionCharRange.value
                                                    if (currentRange != null) {
                                                        val (currentStart, currentEnd) = currentRange
                                                        var newRange: Pair<Int, Int>? = null
                                                        var newHandle = activeDraggingHandle

                                                        when (activeDraggingHandle) {
                                                            Handle.START -> {
                                                                val newStart =
                                                                    charIndexForUpdate.coerceIn(
                                                                        0, pageCharCount - 1
                                                                    )
                                                                if (newStart >= currentEnd - 1 && pageCharCount > 0) {
                                                                    val tempOldEndCharIndex =
                                                                        (currentEnd - 1).coerceAtLeast(
                                                                            0
                                                                        )
                                                                    newHandle = Handle.END
                                                                    newRange = Pair(
                                                                        tempOldEndCharIndex,
                                                                        (newStart + 1).coerceAtMost(
                                                                            pageCharCount
                                                                        )
                                                                    )
                                                                } else {
                                                                    newRange = Pair(
                                                                        newStart, currentEnd
                                                                    )
                                                                }
                                                            }

                                                            Handle.END -> {
                                                                val newEnd =
                                                                    (charIndexForUpdate + 1).coerceIn(
                                                                        1, pageCharCount
                                                                    )
                                                                if (newEnd <= currentStart + 1 && pageCharCount > 0) {
                                                                    newHandle = Handle.START
                                                                    newRange = Pair(
                                                                        (newEnd - 1).coerceAtLeast(
                                                                            0
                                                                        ),
                                                                        (currentStart + 1).coerceAtMost(
                                                                            pageCharCount
                                                                        )
                                                                    )
                                                                } else {
                                                                    newRange = Pair(
                                                                        currentStart, newEnd
                                                                    )
                                                                }
                                                            }

                                                            else -> {}
                                                        }

                                                        if (newRange != null) {
                                                            if (newRange.first >= newRange.second) {
                                                                if (pageCharCount > 0) {
                                                                    val fixStart = min(
                                                                        newRange.first,
                                                                        newRange.second - 1
                                                                    ).coerceIn(
                                                                        0, pageCharCount - 1
                                                                    )
                                                                    val fixEnd =
                                                                        (fixStart + 1).coerceAtMost(
                                                                            pageCharCount
                                                                        )
                                                                    newRange =
                                                                        if (fixStart < fixEnd) Pair(
                                                                            fixStart, fixEnd
                                                                        )
                                                                        else null
                                                                } else {
                                                                    newRange = null
                                                                }
                                                            }
                                                        }

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

                        if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                            if (selectionCharRange.value != null && selectedWordScreenRects.isNotEmpty()) {
                                val currentRange = selectionCharRange.value!!
                                val firstRect = selectedWordScreenRects.first()
                                coroutineScope.launch {
                                    var pageForMenu: PdfPageKt? = null
                                    var textPageForMenu: PdfTextPageKt? = null
                                    try {
                                        pageForMenu = pdfDocumentItem.openPage(
                                            pdfPageIndex
                                        )
                                        textPageForMenu = pageForMenu.openTextPage()
                                        val text = textPageForMenu.textPageGetText(
                                            currentRange.first,
                                            currentRange.second - currentRange.first
                                        )
                                        if (!text.isNullOrBlank()) {
                                            customMenuState = CustomPdfMenuState(
                                                selectedText = text,
                                                anchorRect = firstRect,
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
                                    customMenuState = CustomPdfMenuState(
                                        selectedText = selectedText,
                                        anchorRect = firstRect,
                                        charRange = Pair(-1, -1)
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
                    } else {
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        try {
                            withTimeout(longPressTimeout) {
                                waitForUpOrCancellation()
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            down.consume()
                            Timber.d(
                                "PointerInput: Long press detected at screen position ${down.position}"
                            )
                            selectionCharRange.value = null
                            ocrSelectionSymbolIndices = null
                            customMenuState = null
                            showMagnifier = false

                            coroutineScope.launch {
                                var tempPage: PdfPageKt? = null
                                var tempTextPage: PdfTextPageKt? = null
                                var ocrAttemptedForThisPress = false
                                try {
                                    if (!isPdfPage) return@launch

                                    tempPage = pdfDocumentItem.openPage(pdfPageIndex)
                                    tempTextPage = tempPage.openTextPage()

                                    val touchInContentCoords = screenToContentCoordinates(
                                        down.position
                                    )
                                    Timber.d(
                                        "Long press: initial touch in content coords: $touchInContentCoords"
                                    )

                                    if (touchInContentCoords.x < 0 || touchInContentCoords.x > actualBitmapWidthPx || touchInContentCoords.y < 0 || touchInContentCoords.y > actualBitmapHeightPx) {
                                        Timber.d(
                                            "Long press: Touch point outside bitmap bounds."
                                        )
                                        return@launch
                                    }

                                    val pdfCoords = tempPage.mapDeviceCoordsToPage(
                                        startX = 0,
                                        startY = 0,
                                        sizeX = actualBitmapWidthPx,
                                        sizeY = actualBitmapHeightPx,
                                        rotate = currentPageRotation,
                                        deviceX = touchInContentCoords.x.toInt(),
                                        deviceY = touchInContentCoords.y.toInt()
                                    )
                                    val charTolerance = 5.0
                                    val charIndex = tempTextPage.textPageGetCharIndexAtPos(
                                        x = pdfCoords.x.toDouble(),
                                        y = pdfCoords.y.toDouble(),
                                        xTolerance = charTolerance,
                                        yTolerance = charTolerance
                                    )

                                    var pdfiumSelectionSuccessful = false
                                    if (charIndex != -1) {
                                        val pageCharCount = tempTextPage.textPageCountChars()
                                        val wordBoundaries = findWordBoundaries(
                                            tempTextPage, charIndex, pageCharCount
                                        )

                                        if (wordBoundaries != null) {
                                            selectionMethodUsed = PdfSelectionMethod.PDFIUM
                                            selectionCharRange.value = wordBoundaries
                                            updateSelectionVisuals(
                                                pdfDocumentItem,
                                                pdfPageIndex,
                                                selectionCharRange.value,
                                                actualBitmapWidthPx,
                                                actualBitmapHeightPx,
                                                currentPageRotation,
                                                providedPage = tempPage,
                                                providedTextPage = tempTextPage
                                            )
                                            if (selectionCharRange.value != null && selectedWordScreenRects.isNotEmpty()) {
                                                val currentRange = selectionCharRange.value!!
                                                val text = tempTextPage.textPageGetText(
                                                    currentRange.first,
                                                    currentRange.second - currentRange.first
                                                )
                                                val firstRect = selectedWordScreenRects.first()
                                                if (!text.isNullOrBlank()) {
                                                    customMenuState = CustomPdfMenuState(
                                                        selectedText = text,
                                                        anchorRect = firstRect,
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

                                                        val menuAnchorContentRect =
                                                            foundElement.boundingBox!!
                                                        customMenuState = CustomPdfMenuState(
                                                            selectedText = foundElement.text,
                                                            anchorRect = menuAnchorContentRect,
                                                            charRange = Pair(
                                                                -1, -1
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
                                            pageErrorMessage =
                                                "OCR selection error: ${e.localizedMessage}"
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
                                    pageErrorMessage = "Selection error: ${e.localizedMessage}"
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
                scale,
                offset,
                customMenuState,
                selectionCharRange.value,
                pageLinks,
                centeringOffsetX,
                centeringOffsetY,
                isZoomEnabled,
                isVerticalScroll,
                isEditMode,
                selectedTool,
                isStylusOnlyMode
            ) {
                val isTapDetectionAllowed = !isEditMode ||
                        selectedTool == InkType.TEXT ||
                        isStylusOnlyMode

                if (!isTapDetectionAllowed) return@pointerInput

                detectTapGestures(onTap = { tapOffset ->
                    Timber.d(
                        "PdfPageComposable: onTap detected at $tapOffset. isVerticalScroll=$isVerticalScroll"
                    )

                    val tapInContentCoords = screenToContentCoordinates(tapOffset)
                    val tapXInBitmap = tapInContentCoords.x
                    val tapYInBitmap = tapInContentCoords.y
                    Timber.d(
                        "detectTapGestures: Tap at bitmap coords (${tapXInBitmap.toInt()}, ${tapYInBitmap.toInt()})"
                    )

                    val clickedLink = pageLinks.firstOrNull { link ->
                        link.tapBounds.contains(
                            tapXInBitmap.toInt(), tapYInBitmap.toInt()
                        )
                    }

                    if (clickedLink != null) {
                        Timber.d(
                            "PdfPageComposable: Link clicked. Ignoring selection logic."
                        )
                        if (clickedLink.destPageIdx != null && clickedLink.destPageIdx >= 0) {
                            onInternalLinkClicked(clickedLink.destPageIdx)
                        } else if (clickedLink.url != null) {
                            onLinkClicked(clickedLink.url)
                        }
                        return@detectTapGestures
                    }

                    val wasMenuVisible = customMenuState != null
                    val wasSelectionVisible =
                        selectionCharRange.value != null || ocrSelectionSymbolIndices != null

                    Timber.d(
                        "PdfPageComposable: State check - MenuVisible=$wasMenuVisible, SelectionVisible=$wasSelectionVisible"
                    )

                    if (wasMenuVisible || wasSelectionVisible) {
                        Timber.d(
                            "PdfPageComposable: Clearing selection/menu."
                        )
                        customMenuState = null
                        selectionCharRange.value = null
                        ocrSelectionSymbolIndices = null
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
                    } else {
                        Timber.d(
                            "PdfPageComposable: No selection active. Calling onSingleTap()."
                        )
                        currentOnSingleTap()
                    }
                }, onDoubleTap = { tapOffset ->
                    if (isZoomEnabled && !isVerticalScroll) {
                        if (actualBitmapWidthPx == 0) return@detectTapGestures
                        coroutineScope.launch {
                            val startScale = scale
                            val targetScale = if (startScale > 1.1f) 1f else 2.5f
                            val startOffset = offset
                            val targetOffsetUnbounded = if (targetScale <= 1.1f) {
                                Offset.Zero
                            } else {
                                val ratio = targetScale / startScale
                                val screenCenter = Offset(
                                    size.width / 2f, size.height / 2f
                                )
                                startOffset * ratio + (tapOffset - screenCenter) * (1 - ratio)
                            }

                            val contentWidth = actualBitmapWidthPx * targetScale
                            val contentHeight = actualBitmapHeightPx * targetScale
                            val maxOffsetX = (contentWidth - size.width).coerceAtLeast(0f) / 2f
                            val maxOffsetY = (contentHeight - size.height).coerceAtLeast(0f) / 2f

                            val targetOffset = Offset(
                                x = targetOffsetUnbounded.x.coerceIn(
                                    -maxOffsetX, maxOffsetX
                                ), y = targetOffsetUnbounded.y.coerceIn(
                                    -maxOffsetY, maxOffsetY
                                )
                            )

                            Animatable(0f).animateTo(
                                1f, animationSpec = tween(
                                    durationMillis = 300
                                )
                            ) {
                                val progress = value
                                scale = lerp(
                                    startScale, targetScale, progress
                                )
                                offset = lerp(
                                    startOffset, targetOffset, progress
                                )
                                onScaleChanged(scale)
                            }
                            if (scale <= 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                                onScaleChanged(scale)
                            }
                        }
                    } else if (isVerticalScroll && currentOnDoubleTap != null) {
                        currentOnDoubleTap!!(tapOffset)
                    }
                })
            }
            .pointerInput(
                actualBitmapWidthPx,
                actualBitmapHeightPx,
                activeDraggingHandle,
                isZoomEnabled,
                isVerticalScroll,
                isEditMode,
                onTwoFingerSwipe,
                isScrollLocked
            ) {
                if (!isZoomEnabled || isVerticalScroll || actualBitmapWidthPx == 0 || activeDraggingHandle != null) return@pointerInput

                val decay = splineBasedDecay<Float>(this)
                val velocityTracker = VelocityTracker()
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    @Suppress("UnusedVariable", "Unused") val down =
                        awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.resetTracking()

                    var mode = 0
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    var swipeAccumulatorX = 0f

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        val pointerCount = event.changes.size

                        val currentCentroid = event.calculateCentroid(useCurrent = true)
                        if (pointerCount > 0 && currentCentroid != Offset.Unspecified) {
                            velocityTracker.addPosition(
                                event.changes[0].uptimeMillis, currentCentroid
                            )
                        }

                        if (!canceled) {
                            val rawPanChange = event.calculatePan()
                            val panChange = if (isScrollLocked) Offset(0f, rawPanChange.y) else rawPanChange
                            val zoomChange = event.calculateZoom()

                            if (scale > 1f) {
                                if (mode == 0) {
                                    if (pointerCount == 1) {
                                        accumulatedPan += panChange
                                        if (accumulatedPan.getDistance() > touchSlop) {
                                            mode = 1
                                        }
                                    } else if (pointerCount > 1) {
                                        accumulatedZoom *= zoomChange
                                        accumulatedPan += panChange

                                        val zoomDiff = abs(accumulatedZoom - 1f)
                                        val panDist = accumulatedPan.getDistance()

                                        if (zoomDiff > 0.05f) {
                                            mode = 2
                                        } else if (panDist > touchSlop) {
                                            mode = 1
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
                                    offset = Offset(newX, newY)

                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                } else if (mode == 2 && pointerCount > 1) {
                                    val oldScale = scale
                                    val newScale = (scale * zoomChange).coerceIn(1f, 4f)

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

                                        if (scale < 1.05f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                            onScaleChanged(scale)
                                        } else {
                                            onScaleChanged(scale)
                                        }
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
                                            1f, 4f
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

                    if (mode == 1 && scale > 1f) {
                        val velocity = velocityTracker.calculateVelocity()
                        val contentWidth = actualBitmapWidthPx * scale
                        val contentHeight = actualBitmapHeightPx * scale
                        val maxOffsetX = (contentWidth - size.width).coerceAtLeast(0f) / 2f
                        val maxOffsetY = (contentHeight - size.height).coerceAtLeast(0f) / 2f

                        val startX = offset.x
                        val startY = offset.y

                        coroutineScope.launch {
                            coroutineScope {
                                launch {
                                    if (!isScrollLocked) {
                                        Animatable(startX).animateDecay(
                                            velocity.x, decay
                                        ) {
                                            val newX = value.coerceIn(
                                                -maxOffsetX, maxOffsetX
                                            )
                                            offset = offset.copy(x = newX)
                                        }
                                    }
                                }
                                launch {
                                    Animatable(startY).animateDecay(
                                        velocity.y, decay
                                    ) {
                                        val newY = value.coerceIn(
                                            -maxOffsetY, maxOffsetY
                                        )
                                        offset = offset.copy(y = newY)
                                    }
                                }
                            }
                        }
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

                        val dragPointerId = down.id
                        val startPos = down.position
                        var dragStarted = false
                        val touchSlop = viewConfiguration.touchSlop

                        if (selectedTool == InkType.ERASER) {
                            eraserPosition = down.position
                        }

                        while (true) {
                            val event = awaitPointerEvent()

                            if (event.changes.size > 1) {
                                if (dragStarted) {
                                    drawingState?.onDrawCancel()
                                }
                                eraserPosition = null
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

                                    onDrawStart(PdfPoint(normX, normY))
                                    onDrawEnd()
                                } else {
                                    onDrawEnd()
                                }
                                eraserPosition = null
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
                                            PdfPoint(startNormX, startNormY)
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
                                        onDraw(PdfPoint(currNormX, currNormY))

                                        if (selectedTool == InkType.ERASER) {
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
                                    onDraw(PdfPoint(currNormX, currNormY))

                                    if (selectedTool == InkType.ERASER) {
                                        eraserPosition = change.position
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                } finally {
                    eraserPosition = null
                }
            }, contentAlignment = Alignment.Center
    ) {
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

        LaunchedEffect(
            this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight, pageIndex
        ) {
            Timber.d(
                "PdfPageComposable Page $pageIndex | Constraints: maxWidth=${this@BoxWithConstraints.maxWidth}, maxHeight=${this@BoxWithConstraints.maxHeight}"
            )
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
                            pdfDocumentItem.openPage(pdfPageIndex).use { page ->
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
                            }
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
                isVisible
            ) {
                if (!isVisible && !isVerticalScroll) return@LaunchedEffect

                val viewContainerWidthPx = with(density) { currentContainerMaxWidth.toPx().toInt() }
                val viewContainerHeightPx =
                    with(density) { currentContainerMaxHeight.toPx().toInt() }

                Timber.d(
                    "PdfPageComposable Page $pageIndex | viewContainerPx: ${viewContainerWidthPx}x${viewContainerHeightPx}"
                )

                if (viewContainerWidthPx <= 0 || viewContainerHeightPx <= 0) {
                    if (bitmapState == null) isLoadingPage = true
                    Timber.d(
                        "PdfPageComposable: viewContainer dimensions invalid ($viewContainerWidthPx x $viewContainerHeightPx), waiting."
                    )
                    return@LaunchedEffect
                }

                if (!isPdfPage) {
                    val blankPage = virtualPage as? VirtualPage.BlankPage
                    val pageAspect = if (blankPage != null && blankPage.width > 0 && blankPage.height > 0) {
                        blankPage.width.toFloat() / blankPage.height.toFloat()
                    } else {
                        1f / 1.414f
                    }

                    var scaledWidth = viewContainerWidthPx
                    var scaledHeight = (scaledWidth / pageAspect).toInt()

                    if (scaledHeight > viewContainerHeightPx) {
                        scaledHeight = viewContainerHeightPx
                        scaledWidth = (scaledHeight * pageAspect).toInt()
                    }

                    if (scaledWidth == actualBitmapWidthPx &&
                        scaledHeight == actualBitmapHeightPx &&
                        bitmapState != null &&
                        currentRenderedPageId == targetPageId
                    ) {
                        isLoadingPage = false
                        return@LaunchedEffect
                    }

                    actualBitmapWidthPx = scaledWidth
                    actualBitmapHeightPx = scaledHeight
                    currentPageRotation = 0

                    val bitmap = PdfBitmapPool.get(maxOf(scaledWidth, scaledHeight))

                    bitmap.eraseColor(android.graphics.Color.WHITE)

                    val finalBitmap =
                        if (bitmap.width != scaledWidth || bitmap.height != scaledHeight) {
                            val scaled = bitmap.scale(scaledWidth, scaledHeight, false)
                            if (scaled !== bitmap) PdfBitmapPool.recycle(bitmap)
                            scaled.eraseColor(android.graphics.Color.WHITE)
                            scaled
                        } else {
                            bitmap
                        }

                    val old = bitmapState
                    if (old != null && old !== finalBitmap) {
                        if (old !== PdfThumbnailCache.get(pageIndex)) {
                            old.recycle()
                        }
                    }
                    bitmapState = finalBitmap

                    if (old != null && old !== finalBitmap) {
                        if (old !== PdfThumbnailCache.get(pageIndex)) old.recycle()
                    }
                    currentRenderedPageId = targetPageId

                    isLoadingPage = false
                    return@LaunchedEffect
                }

                coroutineScope.launch {
                    try {
                        val renderResult = withContext(Dispatchers.IO) {
                            val rawPageCount = pdfDocumentItem.getPageCount()
                            if (pdfPageIndex >= rawPageCount) {
                                Timber.w(
                                    "PdfPageComposable: Index $pdfPageIndex out of bounds (count $rawPageCount). Waiting for layout update."
                                )
                                return@withContext null
                            }
                            val page = pdfDocumentItem.openPage(pdfPageIndex)
                            val rotation = page.getPageRotation()
                            val screenDpi = (density.density * 160).roundToInt()
                            val originalWidthPdfUnits = page.getPageWidth(screenDpi)
                            val originalHeightPdfUnits = page.getPageHeight(screenDpi)

                            if (originalWidthPdfUnits <= 0 || originalHeightPdfUnits <= 0) {
                                page.close()
                                throw Exception("Invalid page dimensions")
                            }

                            val aspectRatio =
                                originalWidthPdfUnits.toFloat() / originalHeightPdfUnits.toFloat()
                            var scaledWidth = viewContainerWidthPx
                            var scaledHeight = (scaledWidth / aspectRatio).toInt()

                            if (scaledHeight > viewContainerHeightPx) {
                                scaledHeight = viewContainerHeightPx
                                scaledWidth = (scaledHeight * aspectRatio).toInt()
                            }

                            if (scaledWidth == actualBitmapWidthPx &&
                                scaledHeight == actualBitmapHeightPx &&
                                bitmapState != null &&
                                currentRenderedPageId == targetPageId
                            ) {
                                page.close()
                                return@withContext null
                            }

                            Timber.d(
                                "Rendering page $pageIndex at ${scaledWidth}x${scaledHeight}"
                            )
                            val newBitmap = createBitmap(scaledWidth, scaledHeight)
                            page.renderPageBitmap(
                                newBitmap, 0, 0, scaledWidth, scaledHeight, false
                            )
                            page.close()

                            Triple(newBitmap, rotation, Pair(scaledWidth, scaledHeight))
                        }

                        if (renderResult != null) {
                            val (newBitmap, rotation, dims) = renderResult

                            actualBitmapWidthPx = dims.first
                            actualBitmapHeightPx = dims.second
                            currentPageRotation = rotation

                            val old = bitmapState

                            bitmapState = newBitmap
                            currentRenderedPageId = targetPageId

                            withContext(Dispatchers.IO) {
                                if (old != null && old !== newBitmap && !old.isRecycled) {
                                    val cached = PdfThumbnailCache.get(pageIndex)
                                    if (old !== cached) {
                                        old.recycle()
                                    }
                                }

                                val thumbWidth = newBitmap.width / 2
                                val thumbHeight = newBitmap.height / 2
                                if (thumbWidth > 0 && thumbHeight > 0) {
                                    PdfThumbnailCache.put(
                                        pageIndex, newBitmap.scale(thumbWidth, thumbHeight)
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        pageErrorMessage = "Error processing page: ${e.localizedMessage}"
                    } finally {
                        isLoadingPage = false
                    }
                }
            }

            when {
                isLoadingPage -> {
                    if (placeholderBitmap != null && !placeholderBitmap.isRecycled) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (!placeholderBitmap.isRecycled) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val bitmapWidth = placeholderBitmap.width
                                val bitmapHeight = placeholderBitmap.height

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
                                    image = placeholderBitmap.asImageBitmap(),
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

                    val staticData = remember(
                        stableBitmapState,
                        stableTiles,
                        effectiveScale,
                        centeringOffsetX,
                        centeringOffsetY,
                        canvasWidthPx.floatValue,
                        canvasHeightPx.floatValue,
                        actualBitmapWidthPx,
                        actualBitmapHeightPx,
                        stableColorFilter,
                        isDarkMode
                    ) {
                        Timber.tag("PdfDrawPerf").v(
                            "STATIC DATA GENERATED: Scale=$effectiveScale, Tiles=${stableTiles.item.size}"
                        )
                        PageStaticData(
                            bitmap = stableBitmapState,
                            tiles = stableTiles,
                            effectiveScale = effectiveScale,
                            centeringOffsetX = centeringOffsetX,
                            centeringOffsetY = centeringOffsetY,
                            canvasWidth = canvasWidthPx.floatValue,
                            canvasHeight = canvasHeightPx.floatValue,
                            targetWidth = actualBitmapWidthPx,
                            targetHeight = actualBitmapHeightPx,
                            colorFilter = stableColorFilter,
                            isDarkMode = isDarkMode
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
                        searchAllColor
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
                        offset = offset,
                        startHandlePos = startHandleContentPosition.value,
                        endHandlePos = endHandleContentPosition.value,
                        teardropWidthPx = teardropWidthPxState.value,
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
                        onSelectAll = {
                            customMenuState = null
                            coroutineScope.launch {
                                if (!isPdfPage) return@launch

                                if (selectionMethodUsed == PdfSelectionMethod.PDFIUM) {
                                    var page: PdfPageKt? = null
                                    var textPage: PdfTextPageKt? = null
                                    try {
                                        page = pdfDocumentItem.openPage(pdfPageIndex)
                                        textPage = page.openTextPage()
                                        val charCount = textPage.textPageCountChars()
                                        if (charCount > 0) {
                                            selectionCharRange.value = Pair(0, charCount)
                                            updateSelectionVisuals(
                                                pdfDocumentItem,
                                                pdfPageIndex,
                                                selectionCharRange.value,
                                                actualBitmapWidthPx,
                                                actualBitmapHeightPx,
                                                currentPageRotation,
                                                providedPage = page,
                                                providedTextPage = textPage
                                            )
                                            if (selectedWordScreenRects.isNotEmpty()) {
                                                val fullText =
                                                    textPage.textPageGetText(0, charCount)
                                                if (!fullText.isNullOrBlank()) {
                                                    customMenuState = CustomPdfMenuState(
                                                        selectedText = fullText,
                                                        anchorRect = selectedWordScreenRects.first(),
                                                        charRange = selectionCharRange.value!!
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to select all")
                                    } finally {
                                        withContext(Dispatchers.IO) {
                                            textPage?.close()
                                            page?.close()
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
                                                customMenuState = CustomPdfMenuState(
                                                    selectedText = fullText,
                                                    anchorRect = selectedWordScreenRects.first(),
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
                        isScrolling = isScrolling,
                        isEditMode = isEditMode,
                        selectedTool = selectedTool,
                        eraserPosition = eraserPosition,
                        richTextController = richTextController,
                        textBoxes = textBoxes,
                        selectedTextBoxId = selectedTextBoxId,
                        onTextBoxChange = onTextBoxChange,
                        onTextBoxSelect = onTextBoxSelect,
                        onTextBoxDragStart = onTextBoxDragStart,
                        onTextBoxDrag = onTextBoxDrag,
                        onTextBoxDragEnd = onTextBoxDragEnd,
                        onDragPageTurn = onDragPageTurn,
                        draggingBoxId = draggingBoxId
                    )
                }

                else -> {
                    Text(
                        text = "Unable to display page ${pageIndex + 1}.",
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrProcessingIndicator(position: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "ocr_indicator_transition")
    val animatedRadius by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 120f, animationSpec = infiniteRepeatable(
            animation = tween(1200), repeatMode = RepeatMode.Restart
        ), label = "ocr_radius"
    )
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0f, animationSpec = infiniteRepeatable(
            animation = tween(1200), repeatMode = RepeatMode.Restart
        ), label = "ocr_alpha"
    )

    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = color.copy(alpha = animatedAlpha),
            radius = animatedRadius,
            center = position,
            style = Stroke(width = (4.dp * animatedAlpha).toPx())
        )
    }
}

@Composable
private fun PdfBitmapLayer(
    bitmapState: Bitmap?,
    tiles: List<PdfTile>,
    effectiveScale: Float,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    @Suppress("unused") canvasWidth: Float,
    @Suppress("unused") canvasHeight: Float,
    targetWidth: Int,
    targetHeight: Int,
    colorFilter: ColorFilter? = null,
    isDarkMode: Boolean = false
) {
    SideEffect {
        Timber.tag("PdfDrawPerf")
            .v("BITMAP LAYER: SideEffect (Scale: $effectiveScale, Tiles: ${tiles.size})")
    }
    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer()) {
        val drawStart = System.nanoTime()
        Timber.tag("PdfDrawPerf").v("BITMAP LAYER: Canvas Draw Start (Tiles: ${tiles.size})")
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            if (bitmapState != null && !bitmapState.isRecycled) {
                val dstW = if (targetWidth > 0) targetWidth else bitmapState.width
                val dstH = if (targetHeight > 0) targetHeight else bitmapState.height
                val srcSize = IntSize(bitmapState.width, bitmapState.height)
                val dstSize = IntSize(dstW, dstH)

                // 1. Draw Base Bitmap (with Dark Mode filter if active)
                drawImage(
                    image = bitmapState.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = srcSize,
                    dstOffset = IntOffset.Zero,
                    dstSize = dstSize,
                    colorFilter = colorFilter
                )

                // 3. Draw Tiles
                if (effectiveScale > 1f) {
                    tiles.forEach { tile ->
                        if (!tile.bitmap.isRecycled) {
                            drawImage(
                                image = tile.bitmap.asImageBitmap(),
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(tile.bitmap.width, tile.bitmap.height),
                                dstOffset = IntOffset(tile.renderRect.left, tile.renderRect.top),
                                dstSize = IntSize(
                                    tile.renderRect.width(), tile.renderRect.height()
                                ),
                                colorFilter = colorFilter
                            )
                        }
                    }
                }
            }
        }
        val drawTime = (System.nanoTime() - drawStart) / 1_000_000f
        Timber.tag("PdfDrawPerf").v("BITMAP LAYER: Canvas draw finished in ${drawTime}ms")
    }
}

@Composable
private fun PdfHighlightsLayer(
    pageLinks: List<PageLink>,
    showAllTextHighlights: Boolean,
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    mergedAllTextPageHighlightRects: List<Rect>,
    mergedTtsHighlightRects: List<Rect>,
    mergedSearchFocusedRects: List<Rect>,
    mergedSearchAllRects: List<Rect>,
    searchHighlightMode: SearchHighlightMode,
    ocrHoverHighlights: List<RectF>,
    mergedSelectionRects: List<Rect>,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    linkHighlightColor: Color,
    scrimColorForTextHighlight: Color,
    allTextPageHighlightColor: Color,
    ttsHighlightColor: Color,
    selectionHighlightColor: Color
) {
    Timber.d("PdfHighlightsLayer Recompose")
    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer()) {
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            fun isVisible(r: Rect): Boolean {
                val left = r.left + centeringOffsetX
                val right = r.right + centeringOffsetX
                val bottom = r.bottom + centeringOffsetY
                return left < size.width && right < size.height && right > 0 && bottom > 0
            }

            // 1. Page Links
            pageLinks.forEach { link ->
                if (isVisible(link.highlightBounds)) {
                    drawRect(
                        color = linkHighlightColor, topLeft = Offset(
                            link.highlightBounds.left.toFloat(), link.highlightBounds.top.toFloat()
                        ), size = Size(
                            link.highlightBounds.width().toFloat(),
                            link.highlightBounds.height().toFloat()
                        )
                    )
                }
            }

            // 2. Search Results - BACKGROUND (Yellow)
            if (searchHighlightMode == SearchHighlightMode.ALL) {
                val yellowColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)
                mergedSearchAllRects.forEach { rect ->
                    if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                        val inflated = RectF(rect)
                        inflated.inset(-3f, -3f) // padding
                        drawRect(
                            color = yellowColor,
                            topLeft = Offset(inflated.left, inflated.top),
                            size = Size(inflated.width(), inflated.height())
                        )
                    }
                }
            }

            // 3. Search Results - FOCUSED (Orange + Border)
            val focusedColor = Color(0xFFFF6D00).copy(alpha = 0.4f)
            val focusedStroke = Color(0xFFFF6D00).copy(alpha = 0.9f)
            mergedSearchFocusedRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    val inflated = RectF(rect)
                    inflated.inset(-5f, -5f) // Extra padding for focus

                    // Fill
                    drawRect(
                        color = focusedColor,
                        topLeft = Offset(inflated.left, inflated.top),
                        size = Size(inflated.width(), inflated.height())
                    )
                    // Border
                    drawRect(
                        color = focusedStroke,
                        topLeft = Offset(inflated.left, inflated.top),
                        size = Size(inflated.width(), inflated.height()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 4. Scrim for Text Highlights
            if (showAllTextHighlights && actualBitmapWidthPx > 0 && actualBitmapHeightPx > 0 && scrimColorForTextHighlight.alpha > 0f) {
                with(drawContext.canvas.nativeCanvas) {
                    val checkPoint = saveLayer(null, null)
                    drawRect(
                        color = scrimColorForTextHighlight, topLeft = Offset.Zero, size = Size(
                            actualBitmapWidthPx.toFloat(), actualBitmapHeightPx.toFloat()
                        )
                    )
                    mergedAllTextPageHighlightRects.forEach { rect ->
                        if (rect.width() > 0 && rect.height() > 0) {
                            drawRect(
                                color = Color.Transparent,
                                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                                size = Size(rect.width().toFloat(), rect.height().toFloat()),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                    restoreToCount(checkPoint)
                }
            }

            // 5. All Text Highlights (Overlay)
            mergedAllTextPageHighlightRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    drawRect(
                        color = allTextPageHighlightColor,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat())
                    )
                }
            }

            // 6. TTS Highlights
            mergedTtsHighlightRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    drawRect(
                        color = ttsHighlightColor,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat())
                    )
                }
            }

            // 7. OCR Hover
            ocrHoverHighlights.forEach { rectF ->
                val left = rectF.left * actualBitmapWidthPx
                val top = rectF.top * actualBitmapHeightPx
                val width = rectF.width() * actualBitmapWidthPx
                val height = rectF.height() * actualBitmapHeightPx
                val absLeft = left + centeringOffsetX
                val absTop = top + centeringOffsetY
                if (absLeft < size.width && absTop < size.height && (absLeft + width) > 0 && (absTop + height) > 0) {
                    drawRect(
                        color = Color(0xFFFFAB00).copy(alpha = 0.5f), // Generic highlight color
                        topLeft = Offset(left, top), size = Size(width, height)
                    )
                }
            }

            // 8. User Selection
            mergedSelectionRects.forEach { lineRect ->
                if (lineRect.width() > 0 && lineRect.height() > 0 && isVisible(lineRect)) {
                    drawRect(
                        color = selectionHighlightColor,
                        topLeft = Offset(lineRect.left.toFloat(), lineRect.top.toFloat()),
                        size = Size(lineRect.width().toFloat(), lineRect.height().toFloat())
                    )
                }
            }
        }
    }
}

internal object PdfTextureGenerator {
    private var noiseBitmap: Bitmap? = null

    fun getNoiseTexture(): Bitmap {
        if (noiseBitmap == null) {
            val size = 256
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val isGrain = Math.random() > 0.4
                    if (isGrain) {
                        val alpha = (Math.random() * 100 + 100).toInt()
                        bitmap[x, y] = AndroidColor.argb(alpha, 0, 0, 0)
                    } else {
                        bitmap[x, y] = AndroidColor.TRANSPARENT
                    }
                }
            }
            noiseBitmap = bitmap
        }
        return noiseBitmap!!
    }
}

internal sealed interface AnnotationRenderData {
    data class Standard(
        val path: Path,
        val color: Color,
        val strokeWidth: Float,
        val cap: StrokeCap,
        val blendMode: BlendMode
    ) : AnnotationRenderData

    data class Fountain(val path: Path, val color: Color) : AnnotationRenderData

    data class Pencil(
        val path: android.graphics.Path,
        val color: Color,
        val strokeWidth: Float,
        val velocityAlpha: Float
    ) : AnnotationRenderData
}

internal object PdfAnnotationRenderHelper {
    fun createRenderData(annot: PdfAnnotation, widthPx: Int, heightPx: Int): AnnotationRenderData? {

        if (annot.points.isEmpty()) return null

        if (annot.points.size == 1) {
            val p = annot.points[0]
            val x = p.x * widthPx
            val y = p.y * heightPx

            val path = if (annot.inkType == InkType.PENCIL) android.graphics.Path() else Path()

            if (path is android.graphics.Path) {
                path.moveTo(x, y)
                path.lineTo(x, y)
                return AnnotationRenderData.Pencil(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    velocityAlpha = 1.0f
                )
            } else if (path is Path) {
                if (annot.inkType == InkType.FOUNTAIN_PEN) {
                    val radius = (annot.strokeWidth * widthPx) / 2f
                    path.addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = Offset(x, y), radius = radius
                        )
                    )
                    return AnnotationRenderData.Fountain(path = path, color = annot.color)
                }

                path.moveTo(x, y)
                path.lineTo(x, y)

                val cap = when (annot.inkType) {
                    InkType.HIGHLIGHTER -> StrokeCap.Butt
                    InkType.HIGHLIGHTER_ROUND -> StrokeCap.Round
                    else -> StrokeCap.Round
                }

                val blendMode = BlendMode.SrcOver

                return AnnotationRenderData.Standard(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    cap = cap,
                    blendMode = blendMode
                )
            }
        }

        val result = when (annot.inkType) {
            InkType.PENCIL -> {
                val path = android.graphics.Path()
                val first = annot.points[0]
                path.moveTo(first.x * widthPx, first.y * heightPx)
                var totalDist = 0f
                for (i in 1 until annot.points.size) {
                    val p0 = annot.points[i - 1]
                    val p1 = annot.points[i]
                    val p0x = p0.x * widthPx
                    val p0y = p0.y * heightPx
                    val p1x = p1.x * widthPx
                    val p1y = p1.y * heightPx
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    val dx = p1x - p0x
                    val dy = p1y - p0y
                    totalDist += sqrt(dx * dx + dy * dy)

                    if (i == 1) path.lineTo(midX, midY)
                    else path.quadTo(p0x, p0y, midX, midY)
                }
                val last = annot.points.last()
                path.lineTo(last.x * widthPx, last.y * heightPx)

                val duration =
                    (annot.points.last().timestamp - annot.points.first().timestamp).coerceAtLeast(1)
                val velocity = totalDist / duration
                val velocityAlphaFactor = (1f - (velocity - 0.2f) / 1.8f).coerceIn(0.4f, 1.0f)

                AnnotationRenderData.Pencil(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    velocityAlpha = velocityAlphaFactor
                )
            }

            InkType.FOUNTAIN_PEN -> {
                val baseStrokeWidth = annot.strokeWidth * widthPx
                val path = Path()

                val (leftSide, rightSide) = PdfInkGeometry.calculateFountainPenPoints(
                    annot.points, baseStrokeWidth, widthPx.toFloat(), heightPx.toFloat()
                )

                if (leftSide.isNotEmpty()) {
                    path.moveTo(leftSide[0].x, leftSide[0].y)

                    for (i in 1 until leftSide.size) {
                        path.lineTo(leftSide[i].x, leftSide[i].y)
                    }

                    for (i in rightSide.size - 1 downTo 0) {
                        path.lineTo(rightSide[i].x, rightSide[i].y)
                    }

                    path.close()
                }

                AnnotationRenderData.Fountain(path = path, color = annot.color)
            }

            else -> {
                val path = Path()
                val first = annot.points[0]
                path.moveTo(first.x * widthPx, first.y * heightPx)
                for (i in 1 until annot.points.size) {
                    val p0 = annot.points[i - 1]
                    val p1 = annot.points[i]
                    val p0x = p0.x * widthPx
                    val p0y = p0.y * heightPx
                    val p1x = p1.x * widthPx
                    val p1y = p1.y * heightPx
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    if (i == 1) path.lineTo(midX, midY)
                    else path.quadraticTo(p0x, p0y, midX, midY)
                }
                val last = annot.points.last()
                path.lineTo(last.x * widthPx, last.y * heightPx)

                val blendMode = when (annot.inkType) {
                    InkType.HIGHLIGHTER, InkType.HIGHLIGHTER_ROUND -> BlendMode.Multiply
                    else -> BlendMode.SrcOver
                }

                val cap = when (annot.inkType) {
                    InkType.HIGHLIGHTER -> StrokeCap.Butt
                    InkType.HIGHLIGHTER_ROUND -> StrokeCap.Round
                    else -> StrokeCap.Round
                }

                AnnotationRenderData.Standard(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    cap = cap,
                    blendMode = blendMode
                )
            }
        }
        return result
    }
}

@Composable
private fun PdfAnnotationLayer(
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    annotationsProvider: () -> List<PdfAnnotation>,
    drawingState: PdfDrawingState?,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    pageIndex: Int
) {
    SideEffect { Timber.tag("PdfDrawPerf").v("ANNOT LAYER: Recomposing (Page $pageIndex)") }
    val staticAnnotations = annotationsProvider()
    val currentAnnotation = remember(drawingState, pageIndex) {
        derivedStateOf {
            val annot = drawingState?.currentAnnotation
            val result = if (annot?.pageIndex == pageIndex) annot else null
            if (drawingState != null) {
                Timber.tag("PdfDrawPerf").v(
                    "DerivedState Calc Page $pageIndex: Global=${annot?.pageIndex} -> Result=${result != null}"
                )
            }
            result
        }
    }.value

    SideEffect {
        Timber.tag("PdfDrawPerf").v(
            "ANNOT LAYER: State Check Page $pageIndex | AnnotHash: ${currentAnnotation?.hashCode()} | AnnotPoints: ${currentAnnotation?.points?.size}"
        )
    }

    val staticRenderData = remember(staticAnnotations, actualBitmapWidthPx, actualBitmapHeightPx) {
        staticAnnotations.mapNotNull { annot ->
            PdfAnnotationRenderHelper.createRenderData(
                annot, actualBitmapWidthPx, actualBitmapHeightPx
            )
        }
    }

    val activeRenderData = remember(
        currentAnnotation,
        currentAnnotation?.points?.size,
        actualBitmapWidthPx,
        actualBitmapHeightPx
    ) {
        if (currentAnnotation != null) {
            Timber.tag("PdfDrawPerf").v(
                "ANNOT LAYER: Generating active path for ${currentAnnotation.points.size} points"
            )
        }
        currentAnnotation?.let { annot ->
            PdfAnnotationRenderHelper.createRenderData(
                annot, actualBitmapWidthPx, actualBitmapHeightPx
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val drawStart = System.nanoTime()
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            fun drawData(data: AnnotationRenderData) {
                when (data) {
                    is AnnotationRenderData.Standard -> {
                        drawPath(
                            path = data.path, color = data.color, style = Stroke(
                                width = data.strokeWidth, cap = data.cap, join = StrokeJoin.Round
                            ), blendMode = data.blendMode
                        )
                    }

                    is AnnotationRenderData.Fountain -> {
                        drawPath(
                            path = data.path,
                            color = data.color,
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }

                    is AnnotationRenderData.Pencil -> {
                        val texture = PdfTextureGenerator.getNoiseTexture()
                        drawIntoCanvas { canvas ->
                            val paint = NativePaint().apply {
                                isAntiAlias = true
                                style = NativePaint.Style.STROKE
                                strokeCap = NativePaint.Cap.ROUND
                                strokeJoin = NativePaint.Join.ROUND
                                strokeWidth = data.strokeWidth
                                shader = BitmapShader(
                                    texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
                                )
                                colorFilter = PorterDuffColorFilter(
                                    data.color.toArgb(), PorterDuff.Mode.SRC_IN
                                )
                                alpha = (data.color.alpha * data.velocityAlpha * 255).toInt()
                            }
                            canvas.nativeCanvas.drawPath(data.path, paint)
                        }
                    }
                }
            }

            staticRenderData.forEach { drawData(it) }
            activeRenderData?.let { drawData(it) }
        }
        val drawDuration = (System.nanoTime() - drawStart) / 1_000_000f
        Timber.tag("PdfDrawPerf").v(
            "ANNOT DRAW: Canvas draw took ${drawDuration}ms. Points: ${currentAnnotation?.points?.size ?: 0}"
        )
    }
}

@Composable
private fun PdfPageStaticLayer(data: PageStaticData) {
    SideEffect {
        Timber.tag("PdfDrawPerf").v(
            "STATIC LAYER: Composition (Should not happen during draw). DataHash: ${data.hashCode()}"
        )
    }
    Timber.tag("PdfDrawPerf").v("STATIC LAYER: Recomposing")

    PdfBitmapLayer(
        bitmapState = data.bitmap.item,
        tiles = data.tiles.item,
        effectiveScale = data.effectiveScale,
        centeringOffsetX = data.centeringOffsetX,
        centeringOffsetY = data.centeringOffsetY,
        canvasWidth = data.canvasWidth,
        canvasHeight = data.canvasHeight,
        targetWidth = data.targetWidth,
        targetHeight = data.targetHeight,
        colorFilter = data.colorFilter.item,
        isDarkMode = data.isDarkMode
    )
}

@Composable
private fun PdfPageSelectionsLayer(
    pageLinks: List<PageLink>,
    showAllTextHighlights: Boolean,
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    mergedAllTextPageHighlightRects: List<Rect>,
    mergedTtsHighlightRects: List<Rect>,
    mergedSearchFocusedRects: List<Rect>,
    mergedSearchAllRects: List<Rect>,
    searchHighlightMode: SearchHighlightMode,
    ocrHoverHighlights: List<RectF>,
    mergedSelectionRects: List<Rect>,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    linkHighlightColor: Color,
    scrimColorForTextHighlight: Color,
    allTextPageHighlightColor: Color,
    ttsHighlightColor: Color,
    selectionHighlightColor: Color
) {
    SideEffect { Timber.tag("PdfDrawPerf").v("SELECTIONS LAYER: Recomposing") }
    val highlightStart = System.nanoTime()

    PdfHighlightsLayer(
        pageLinks = pageLinks,
        showAllTextHighlights = showAllTextHighlights,
        actualBitmapWidthPx = actualBitmapWidthPx,
        actualBitmapHeightPx = actualBitmapHeightPx,
        mergedAllTextPageHighlightRects = mergedAllTextPageHighlightRects,
        mergedTtsHighlightRects = mergedTtsHighlightRects,
        mergedSearchFocusedRects = mergedSearchFocusedRects,
        mergedSearchAllRects = mergedSearchAllRects,
        searchHighlightMode = searchHighlightMode,
        ocrHoverHighlights = ocrHoverHighlights,
        mergedSelectionRects = mergedSelectionRects,
        centeringOffsetX = centeringOffsetX,
        centeringOffsetY = centeringOffsetY,
        linkHighlightColor = linkHighlightColor,
        scrimColorForTextHighlight = scrimColorForTextHighlight,
        allTextPageHighlightColor = allTextPageHighlightColor,
        ttsHighlightColor = ttsHighlightColor,
        selectionHighlightColor = selectionHighlightColor
    )

    val highlightTime = (System.nanoTime() - highlightStart) / 1_000_000f
    if (highlightTime > 1f) {
        SideEffect {
            Timber.tag("PdfPerformance")
                .v("PdfHighlightsLayer composition/draw took ${highlightTime}ms")
        }
    }
}

@Composable
private fun PdfPageRenderer(
    staticData: PageStaticData,
    selectionData: PageSelectionData,
    totalPages: Int,
    annotationsProvider: () -> List<PdfAnnotation>,
    drawingState: PdfDrawingState?,
    onCanvasSizeChanged: (Float, Float) -> Unit,
    scale: Float,
    offset: Offset,
    startHandlePos: Offset?,
    endHandlePos: Offset?,
    teardropWidthPx: Float,
    teardropHeightPx: Float,
    activeDraggingHandle: Handle?,
    showMagnifier: Boolean,
    magnifierCenterTarget: Offset,
    magnifierZoomFactor: Float,
    menuState: CustomPdfMenuState?,
    onMenuDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onAiDefine: (String) -> Unit,
    onSelectAll: () -> Unit,
    onShowUpsellDialog: () -> Unit,
    isProUser: Boolean,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    centeringPaddingTop: Dp,
    centeringPaddingEnd: Dp,
    isPerformingOcr: Boolean,
    ocrRipplePos: Offset?,
    layoutCoordinates: LayoutCoordinates?,
    contentToScreenCoordinates: (Offset) -> Offset,
    density: Density,
    isVerticalScroll: Boolean,
    isScrolling: Boolean,
    isEditMode: Boolean,
    selectedTool: InkType,
    eraserPosition: Offset?,
    richTextController: RichTextController?,
    textBoxes: List<PdfTextBox>,
    selectedTextBoxId: String?,
    onTextBoxChange: (PdfTextBox) -> Unit,
    onTextBoxSelect: (String) -> Unit,
    onTextBoxDragStart: (PdfTextBox, Offset, Offset) -> Unit,
    onTextBoxDrag: (Offset) -> Unit,
    onTextBoxDragEnd: () -> Unit,
    onDragPageTurn: (Int) -> Unit,
    draggingBoxId: String? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }) {

            // Layer 1: The Heavy Bitmap
            Box(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()) {
                PdfPageStaticLayer(data = staticData)
            }

            // Layer 2: The Lightweight Highlights
            PdfPageSelectionsLayer(
                pageLinks = selectionData.pageLinks.item,
                showAllTextHighlights = selectionData.showAllTextHighlights,
                actualBitmapWidthPx = selectionData.actualBitmapWidthPx,
                actualBitmapHeightPx = selectionData.actualBitmapHeightPx,
                mergedAllTextPageHighlightRects = selectionData.mergedAllTextPageHighlightRects.item,
                mergedTtsHighlightRects = selectionData.mergedTtsHighlightRects.item,
                mergedSearchFocusedRects = selectionData.mergedSearchFocusedRects.item,
                mergedSearchAllRects = selectionData.mergedSearchAllRects.item,
                searchHighlightMode = selectionData.searchHighlightMode,
                ocrHoverHighlights = selectionData.ocrHoverHighlights.item,
                mergedSelectionRects = selectionData.mergedSelectionRects.item,
                centeringOffsetX = selectionData.centeringOffsetX,
                centeringOffsetY = selectionData.centeringOffsetY,
                linkHighlightColor = selectionData.linkHighlightColor,
                scrimColorForTextHighlight = selectionData.scrimColorForTextHighlight,
                allTextPageHighlightColor = selectionData.allTextPageHighlightColor,
                ttsHighlightColor = selectionData.ttsHighlightColor,
                selectionHighlightColor = selectionData.selectionHighlightColor
            )

            // Layer 3: Annotations (Drawing)
            if (staticData.targetWidth > 0 && staticData.targetHeight > 0) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()) {
                    PdfAnnotationLayer(
                        actualBitmapWidthPx = staticData.targetWidth,
                        actualBitmapHeightPx = staticData.targetHeight,
                        annotationsProvider = annotationsProvider,
                        drawingState = drawingState,
                        centeringOffsetX = staticData.centeringOffsetX,
                        centeringOffsetY = staticData.centeringOffsetY,
                        pageIndex = selectionData.pageIndex
                    )
                }

                if (richTextController != null) {
                    val density = LocalDensity.current
                    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val targetW = staticData.targetWidth.toFloat()
                    val targetH = staticData.targetHeight.toFloat()

                    LaunchedEffect(targetW, targetH, density) {
                        if (targetW > 0 && targetH > 0) {
                            richTextController.updateLayoutConfig(
                                targetW, targetH, density, textMeasurer
                            )
                        }
                    }

                    val isEditable = isEditMode && selectedTool == InkType.TEXT
                    val hasContent = richTextController.pageLayouts.any {
                        it.pageIndex == selectionData.pageIndex
                    }

                    if (isEditable || hasContent) {
                        PdfRichTextLayer(
                            pageIndex = selectionData.pageIndex,
                            controller = richTextController,
                            pageWidth = staticData.targetWidth.toFloat(),
                            pageHeight = staticData.targetHeight.toFloat(),
                            isTextEditingEnabled = isEditable && selectedTextBoxId == null,
                            centeringOffsetX = staticData.centeringOffsetX,
                            centeringOffsetY = staticData.centeringOffsetY,
                            isDarkMode = staticData.isDarkMode,
                            isScrolling = isScrolling
                        )
                    }
                }

                // Text Boxes
                textBoxes.forEach { box ->
                    val isDraggingThisBox = (box.id == draggingBoxId)
                    val boxAlpha = if (isDraggingThisBox) 0f else 1f

                    key(box.id) {
                        ResizableTextBox(
                            box = box,
                            isSelected = (box.id == selectedTextBoxId),
                            isEditMode = isEditMode,
                            isDarkMode = staticData.isDarkMode,
                            pageWidthPx = staticData.targetWidth.toFloat(),
                            pageHeightPx = staticData.targetHeight.toFloat(),
                            handlePosition = HandlePosition.AUTO,
                            onBoundsChanged = { newBounds ->
                                onTextBoxChange(box.copy(relativeBounds = newBounds))
                            },
                            onTextChanged = { newText ->
                                onTextBoxChange(box.copy(text = newText))
                            },
                            onSelect = { onTextBoxSelect(box.id) },
                            onDragStart = { touchOffset ->
                                if (isVerticalScroll) {
                                    val topLeft = Offset(
                                        box.relativeBounds.left * staticData.targetWidth,
                                        box.relativeBounds.top * staticData.targetHeight
                                    )
                                    onTextBoxDragStart(box, topLeft, touchOffset)
                                } else {
                                    onTextBoxDragStart(box, Offset.Zero, touchOffset)
                                }
                            },
                            onDrag = { delta, currentBounds ->
                                if (isVerticalScroll) {
                                    onTextBoxDrag(delta)
                                } else {
                                    onTextBoxDrag(delta)

                                    val width = staticData.targetWidth
                                    val edgeThreshold = 60f
                                    if (width > 0) {
                                        if (currentBounds.left < edgeThreshold && delta.x < 0) {
                                            onDragPageTurn(-1)
                                        } else if (currentBounds.right > width - edgeThreshold && delta.x > 0) {
                                            onDragPageTurn(1)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                onTextBoxDragEnd()
                            },
                            onDragCancel = {
                                onTextBoxDragEnd()
                            },
                            modifier = Modifier
                                .zIndex(10f)
                                .offset {
                                    IntOffset(
                                        staticData.centeringOffsetX.roundToInt(),
                                        staticData.centeringOffsetY.roundToInt()
                                    )
                                }
                                .alpha(boxAlpha)
                        )
                    }
                }

                // Layer 4: Page Number Indicator
                if (totalPages > 0) {
                    val pageNumColor = if (staticData.isDarkMode) {
                        Color.White
                    } else {
                        Color.Black
                    }

                    Box(modifier = Modifier
                        .offset {
                            IntOffset(
                                x = staticData.centeringOffsetX.toInt(),
                                y = staticData.centeringOffsetY.toInt()
                            )
                        }
                        .size(width = with(density) {
                            staticData.targetWidth.toDp()
                        }, height = with(density) {
                            staticData.targetHeight.toDp()
                        })) {
                        Text(
                            text = "${selectionData.pageIndex + 1}\\$totalPages",
                            color = pageNumColor.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                        )
                    }
                }
            }

            // Capture size for coordinate conversions
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (staticData.canvasWidth != size.width || staticData.canvasHeight != size.height) {
                    onCanvasSizeChanged(size.width, size.height)
                }
            }
        }

        val teardropPainter = painterResource(id = R.drawable.teardrop)

        if (isEditMode && selectedTool == InkType.ERASER && eraserPosition != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radiusPx = 8.dp.toPx()

                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radiusPx,
                    center = eraserPosition
                )

                drawCircle(
                    color = Color.Black,
                    radius = radiusPx,
                    center = eraserPosition,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val handleColor = Color.Blue
            val tiltAngleDegrees = 30f

            startHandlePos?.let { contentPos ->
                val position = contentToScreenCoordinates(contentPos)
                translate(left = position.x - teardropWidthPx / 2, top = position.y) {
                    rotate(degrees = tiltAngleDegrees, pivot = Offset(teardropWidthPx / 2f, 0f)) {
                        with(teardropPainter) {
                            draw(
                                size = Size(teardropWidthPx, teardropHeightPx),
                                colorFilter = ColorFilter.tint(handleColor)
                            )
                        }
                    }
                }
            }
            endHandlePos?.let { contentPos ->
                val position = contentToScreenCoordinates(contentPos)
                translate(left = position.x - teardropWidthPx / 2, top = position.y) {
                    rotate(degrees = -tiltAngleDegrees, pivot = Offset(teardropWidthPx / 2f, 0f)) {
                        with(teardropPainter) {
                            draw(
                                size = Size(teardropWidthPx, teardropHeightPx),
                                colorFilter = ColorFilter.tint(handleColor)
                            )
                        }
                    }
                }
            }
        }

        if (showMagnifier && activeDraggingHandle != null && staticData.bitmap.item != null) {

            val handleContentPos = when (activeDraggingHandle) {
                Handle.START -> startHandlePos
                Handle.END -> endHandlePos
            }

            handleContentPos?.let { contentPos ->
                val pos = contentToScreenCoordinates(contentPos)

                val magnifierWidth = 120.dp
                val magnifierHeight = 60.dp
                val magnifierOffsetAboveHandle = 24.dp

                with(density) {
                    val magnifierWidthPx = magnifierWidth.toPx()
                    val magnifierHeightPx = magnifierHeight.toPx()
                    val magnifierOffsetAboveHandlePx = magnifierOffsetAboveHandle.toPx()
                    val effectiveScale = staticData.effectiveScale

                    val modifier: Modifier
                    val effectiveZoomFactor: Float

                    if (isVerticalScroll && effectiveScale > 1f) {
                        val yOffsetPixels =
                            pos.y - (magnifierHeightPx + magnifierOffsetAboveHandlePx) / effectiveScale
                        val xOffsetPixels = pos.x - (magnifierWidthPx / 2) / effectiveScale

                        modifier =
                            Modifier
                                .offset(x = xOffsetPixels.toDp(), y = yOffsetPixels.toDp())
                                .graphicsLayer(
                                    scaleX = 1f / effectiveScale,
                                    scaleY = 1f / effectiveScale,
                                    transformOrigin = TransformOrigin(0f, 0f)
                                )

                        effectiveZoomFactor = effectiveScale * 1.25f
                    } else {
                        val xOffsetVal = pos.x - magnifierWidthPx / 2
                        val yOffsetVal = pos.y - magnifierHeightPx - magnifierOffsetAboveHandlePx

                        modifier = Modifier.offset(x = xOffsetVal.toDp(), y = yOffsetVal.toDp())
                        effectiveZoomFactor = magnifierZoomFactor
                    }

                    MagnifierComposable(
                        sourceBitmap = staticData.bitmap.item.asImageBitmap(),
                        tiles = if (effectiveScale > 1f) staticData.tiles.item else emptyList(),
                        currentScale = effectiveScale,
                        magnifierCenterOnBitmap = magnifierCenterTarget,
                        magnifierWidth = magnifierWidth,
                        magnifierHeight = magnifierHeight,
                        zoomFactor = effectiveZoomFactor,
                        selectionRectsInBitmapCoords = selectionData.mergedSelectionRects.item,
                        highlightColor = Color(0x6633B5E5),
                        colorFilter = staticData.colorFilter.item,
                        modifier = modifier
                    )
                }
            }
        }

        if (menuState != null) {
            BackHandler(enabled = true, onBack = onMenuDismiss)
        }
        menuState?.let { state ->
            if (state.anchorRect.width() > 0 || state.anchorRect.height() > 0) {
                val popupPositionProvider =
                    remember(state.anchorRect, density, offset, scale, layoutCoordinates) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize
                            ): IntOffset {
                                val coords = layoutCoordinates ?: return IntOffset.Zero
                                val menuAnchorContentRect = state.anchorRect
                                val topLeftLocal = contentToScreenCoordinates(
                                    Offset(
                                        menuAnchorContentRect.left.toFloat(),
                                        menuAnchorContentRect.top.toFloat()
                                    )
                                )
                                val bottomRightLocal = contentToScreenCoordinates(
                                    Offset(
                                        menuAnchorContentRect.right.toFloat(),
                                        menuAnchorContentRect.bottom.toFloat()
                                    )
                                )
                                val topLeftWindow = coords.localToWindow(topLeftLocal)
                                val bottomRightWindow = coords.localToWindow(bottomRightLocal)

                                val windowCenterX = (topLeftWindow.x + bottomRightWindow.x) / 2
                                val windowTopY = topLeftWindow.y
                                val windowBottomY = bottomRightWindow.y
                                val xInWindow = windowCenterX - popupContentSize.width / 2
                                var yInWindow =
                                    windowTopY - popupContentSize.height - with(density) { 8.dp.toPx() }

                                if (yInWindow < 0) {
                                    yInWindow = windowBottomY + with(density) { 8.dp.toPx() }
                                }

                                val finalX = xInWindow.toInt().coerceIn(
                                    0, windowSize.width - popupContentSize.width
                                )
                                val finalY = yInWindow.toInt().coerceIn(
                                    0, windowSize.height - popupContentSize.height
                                )

                                return IntOffset(finalX, finalY)
                            }
                        }
                    }

                PdfSelectionMenuPopup(
                    menuState = state,
                    popupPositionProvider = popupPositionProvider,
                    onCopy = onCopy,
                    onAiDefine = onAiDefine,
                    onSelectAll = onSelectAll
                )
            }
        }

        BookmarkButton(
            isBookmarked = isBookmarked,
            onClick = onBookmarkClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = centeringPaddingTop, end = centeringPaddingEnd)
        )

        if (isPerformingOcr && ocrRipplePos != null) {
            OcrProcessingIndicator(position = ocrRipplePos)
        }
    }
}

@Stable
class PdfDrawingState {
    var currentAnnotation by mutableStateOf<PdfAnnotation?>(null)
        private set
    private val currentPoints = mutableListOf<PdfPoint>()

    fun onDrawStart(pageIndex: Int, point: PdfPoint, type: InkType, color: Color, width: Float) {
        currentPoints.clear()
        currentPoints.add(point)
        currentAnnotation = PdfAnnotation(
            type = AnnotationType.INK,
            inkType = type,
            pageIndex = pageIndex,
            points = currentPoints.toList(),
            color = color,
            strokeWidth = width
        )
    }

    fun onDraw(point: PdfPoint) {
        currentPoints.add(point)
        currentAnnotation = currentAnnotation?.copy(points = currentPoints.toList())
    }

    fun onDrawCancel() {
        currentAnnotation = null
        currentPoints.clear()
    }

    fun onDrawEnd(): PdfAnnotation? {
        val finalAnnot = currentAnnotation
        currentAnnotation = null
        currentPoints.clear()
        return finalAnnot
    }

    fun updateDrag(point: PdfPoint) {
        if (currentPoints.isNotEmpty()) {
            val start = currentPoints.first()
            currentPoints.clear()
            currentPoints.add(start)
            currentPoints.add(point)
            currentAnnotation = currentAnnotation?.copy(points = currentPoints.toList())
        }
    }
}

@Composable
fun PdfRichTextLayer(
    pageIndex: Int,
    controller: RichTextController,
    pageWidth: Float,
    pageHeight: Float,
    isTextEditingEnabled: Boolean,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    isDarkMode: Boolean,
    isScrolling: Boolean
) {
    val density = LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    LaunchedEffect(pageWidth, pageHeight, density) {
        if (pageWidth > 0 && pageHeight > 0) {
            controller.updateLayoutConfig(pageWidth, pageHeight, density, textMeasurer)
        }
    }

    val pageLayout = remember(controller.pageLayouts, pageIndex) {
        controller.pageLayouts.find { it.pageIndex == pageIndex }
    }

    val marginX = pageWidth * 0.1f
    val marginY = pageHeight * 0.08f
    val editorWidth = pageWidth - (marginX * 2)
    val editorHeight = pageHeight - (marginY * 2)

    val editorWidthDp = with(density) { editorWidth.toDp() }
    val editorHeightDp = with(density) { editorHeight.toDp() }

    Box(modifier = Modifier
        .offset {
            IntOffset(
                (centeringOffsetX + marginX).roundToInt(), (centeringOffsetY + marginY).roundToInt()
            )
        }
        .size(editorWidthDp, editorHeightDp)
        .graphicsLayer()
        .then(
            if (isTextEditingEnabled) {
                Modifier.pointerInput(pageIndex) {
                    detectTapGestures { tapOffset ->
                        controller.handleTapOnPage(pageIndex, tapOffset)
                    }
                }
            } else {
                Modifier
            }
        )
    ) {
        val textToRender = if (controller.activePageIndex == pageIndex) {
            controller.localTextFieldValue.annotatedString
        } else {
            pageLayout?.visibleText
        }

        if (textToRender != null) {
            val measureResult = remember(textToRender, editorWidth, density) {
                textMeasurer.measure(
                    text = textToRender,
                    style = TextStyle(fontSize = 16.sp),
                    constraints = Constraints(maxWidth = editorWidth.toInt()),
                    density = density
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                measureResult.multiParagraph.paint(drawContext.canvas)
            }

            if (isTextEditingEnabled) {
                val tfv = controller.editingValue
                val selection = tfv.selection

                @Suppress("ControlFlowWithEmptyBody") if (controller.activePageIndex == pageIndex) {
                    val localStart = selection.start.coerceIn(0, textToRender.length)
                    val localEnd = selection.end.coerceIn(0, textToRender.length)

                    if (localStart != localEnd) {
                        val selectionPath = measureResult.getPathForRange(localStart, localEnd)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawPath(selectionPath, Color(0xFFB3D7FF).copy(alpha = 0.5f))
                        }
                    }

                    if (selection.collapsed && controller.isCursorVisible) {
                        val alpha = if (isScrolling) {
                            1f
                        } else {
                            val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                            infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                                label = "cursorAlpha"
                            ).value
                        }

                        val cursorRect = measureResult.getCursorRect(localStart)
                        val cursorColor = if (isDarkMode) Color.White else Color.Black
                        val styleFontSize = controller.currentStyle.fontSize
                        val cursorHeight = if (styleFontSize.isSpecified) {
                            with(density) { styleFontSize.toPx() } * 1.2f
                        } else {
                            cursorRect.height
                        }

                        val centerY = cursorRect.center.y
                        val newTop = centerY - (cursorHeight / 2f)
                        val newBottom = centerY + (cursorHeight / 2f)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = cursorColor.copy(alpha = alpha),
                                start = Offset(cursorRect.left, newTop),
                                end = Offset(cursorRect.left, newBottom),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                } else if (pageLayout != null) { }
            }
        }
    }
}