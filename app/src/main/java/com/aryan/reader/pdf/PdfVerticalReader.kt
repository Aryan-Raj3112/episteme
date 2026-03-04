/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// PdfVerticalReader.kt
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH", "VariableNeverRead")

package com.aryan.reader.pdf

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aryan.reader.SearchResult
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SCROLL_BOUNDS_TAG = "PdfScrollBounds"

@Stable
class VerticalPdfReaderState {
    var currentPage by mutableIntStateOf(0)
        internal set

    var firstVisiblePage by mutableIntStateOf(0)
        internal set

    var lastVisiblePage by mutableIntStateOf(0)
        internal set

    internal var scrollToPageHandler: (suspend (Int) -> Unit)? = null

    internal var snapToPageHandler: (suspend (Int) -> Unit)? = null

    suspend fun scrollToPage(pageIndex: Int) {
        scrollToPageHandler?.invoke(pageIndex)
    }

    suspend fun snapToPage(pageIndex: Int) {
        snapToPageHandler?.invoke(pageIndex)
    }
}

@Composable
fun rememberVerticalPdfReaderState(): VerticalPdfReaderState {
    return remember { VerticalPdfReaderState() }
}

private data class PdfPageLayout(
    val index: Int,
    val y: Float,
    val height: Float,
    val width: Float,
    val widthDp: Dp,
    val heightDp: Dp
)

private data class DividerLayout(val y: Float, val width: Float, val height: Float)

@Suppress("UnusedVariable")
@SuppressLint("UnusedBoxWithConstraintsScope", "BinaryOperationInTimber")
@OptIn(FlowPreview::class)
@Composable
internal fun PdfVerticalReader(
    state: VerticalPdfReaderState,
    pdfDocument: StableHolder<PdfDocumentKt>,
    isDarkMode: Boolean,
    totalPages: Int,
    modifier: Modifier = Modifier,
    virtualPages: List<VirtualPage> = emptyList(),
    pageAspectRatios: StableHolder<List<Float>>,
    headerHeight: Dp,
    footerHeight: Dp,
    onZoomChange: (Float) -> Unit,
    onPageClick: () -> Unit,
    showAllTextHighlights: Boolean,
    onHighlightLoading: (Boolean) -> Unit,
    searchQuery: String,
    searchHighlightMode: SearchHighlightMode,
    searchResultToHighlight: SearchResult?,
    isProUser: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    ttsHighlightData: TtsHighlightData?,
    ttsReadingPage: Int?,
    onLinkClicked: (String) -> Unit,
    onInternalLinkClicked: (Int) -> Unit,
    bookmarks: StableHolder<Set<PdfBookmark>>,
    onBookmarkClick: (Int) -> Unit,
    onOcrStateChange: (Boolean) -> Unit,
    onGetOcrSearchRects: suspend (Int, String) -> List<RectF>,
    isEditMode: Boolean = false,
    allAnnotations: () -> Map<Int, List<PdfAnnotation>> = { emptyMap() },
    drawingState: PdfDrawingState,
    onDrawStart: (Int, PdfPoint) -> Unit,
    onDraw: (Int, PdfPoint) -> Unit,
    onDrawEnd: () -> Unit,
    onOcrModelDownloading: () -> Unit = {},
    selectedTool: InkType,
    richTextController: RichTextController? = null,
    textBoxes: List<PdfTextBox> = emptyList(),
    selectedTextBoxId: String? = null,
    onTextBoxChange: (PdfTextBox) -> Unit = {},
    onTextBoxSelect: (String) -> Unit = {},
    bottomContentPaddingPx: Float = 0f,
    topContentPaddingPx: Float = 0f,
    onTextBoxMoved: (String, Int, Rect) -> Unit = { _, _, _ -> },
    isAutoScrollPlaying: Boolean = false,
    isAutoScrollTempPaused: Boolean = false,
    isScrollLocked: Boolean = false,
    autoScrollSpeed: Float = 1.0f,
    onInteractionListener: () -> Unit = {},
    isStylusOnlyMode: Boolean = false
) {
    SideEffect { Timber.tag("PdfDrawPerf").v("LIST: PdfVerticalReader Recomposing.") }
    var globalEraserPosition by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        val imeInsets = WindowInsets.ime
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        val ratios = pageAspectRatios.item
        val bookmarkSet = bookmarks.item

        val scope = rememberCoroutineScope()

        LaunchedEffect(constraints, density) {
            Timber.d(
                "Screen Dims: width=$screenWidth, height=$screenHeight, constraints=$constraints"
            )
        }

        val headerHeightPx = with(density) { headerHeight.toPx() }
        val footerHeightPx = with(density) { footerHeight.toPx() }

        val dividerHeightDp = 8.dp
        val dividerHeightPx = with(density) { dividerHeightDp.toPx() }

        val layoutState = remember(ratios, screenWidth, screenHeight, density) {
            // Return a wrapper object containing both list and total height to ensure
            // atomicity
            data class LayoutResult(val pages: List<PdfPageLayout>, val totalHeight: Float)

            var currentY = 0.0

            // Special case for single page centering
            if (ratios.size == 1) {
                val ratio = ratios[0]
                val safeRatio = if (ratio <= 0f) 1f else ratio
                val pageHeight = screenWidth / safeRatio
                if (pageHeight < screenHeight) {
                    currentY = ((screenHeight - pageHeight) / 2f).toDouble()
                }
            }

            val pages = ratios.mapIndexed { index, ratio ->
                val safeRatio = if (ratio <= 0f) 1f else ratio
                // Use double for calculation
                val pageHeightDouble = screenWidth.toDouble() / safeRatio.toDouble()
                val pageHeight = pageHeightDouble.toFloat()

                val info = PdfPageLayout(
                    index = index,
                    y = currentY.toFloat(),
                    height = pageHeight,
                    width = screenWidth,
                    widthDp = with(density) { screenWidth.toDp() },
                    heightDp = with(density) { pageHeight.toDp() })

                currentY += pageHeightDouble
                if (index < ratios.lastIndex) {
                    currentY += dividerHeightPx
                }
                info
            }

            // Calculate exact total height based on the double accumulator
            val totalH = if (pages.isNotEmpty()) {
                // Determine the bottom of the last element strictly
                val last = pages.last()
                last.y + last.height
            } else {
                0f
            }

            LayoutResult(pages, totalH)
        }

        val layoutInfo = layoutState.pages
        val totalDocHeight = layoutState.totalHeight
        Timber.tag(SCROLL_BOUNDS_TAG)
            .d("Layout Recalculated. Page Count: ${layoutInfo.size}, TotalDocHeight: $totalDocHeight")

        val zoomAnimatable = remember { Animatable(1f) }
        val panXAnimatable = remember { Animatable(0f) }
        val panYAnimatable = remember { Animatable(0f) }

        var previousLayoutPages by remember { mutableStateOf<List<PdfPageLayout>?>(null) }
        val currentScaleProvider = remember(zoomAnimatable) { { zoomAnimatable.value } }

        SideEffect {
            val prevPages = previousLayoutPages
            val currentPages = layoutState.pages

            if (prevPages != null && prevPages.size == currentPages.size) {
                val anchorIndex = state.firstVisiblePage.coerceIn(currentPages.indices)
                val oldY = prevPages[anchorIndex].y
                val newY = currentPages[anchorIndex].y
                val delta = newY - oldY

                if (abs(delta) > 0.1f) {
                    val currentZoom = zoomAnimatable.value
                    val shiftInScreenPixels = delta * currentZoom
                    val targetPanY = panYAnimatable.value - shiftInScreenPixels

                    scope.launch { panYAnimatable.snapTo(targetPanY) }
                }
            }
            previousLayoutPages = currentPages
        }

        fun clampValues(
            targetZoom: Float, targetPanX: Float, targetPanY: Float
        ): Triple<Float, Float, Float> {
            val constrainedZoom = targetZoom.coerceIn(1f, 5f)
            val zoomedDocWidth = screenWidth * constrainedZoom
            val zoomedDocHeight = totalDocHeight * constrainedZoom

            val maxPanX = 0f
            val minPanX = -(zoomedDocWidth - screenWidth).coerceAtLeast(0f)

            val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                headerHeightPx
            )

            val constrainedX = targetPanX.coerceIn(minPanX, maxPanX)
            val constrainedY = targetPanY.coerceIn(minPanY, headerHeightPx)

            // DEDICATED LOG
            Timber.tag("PdfZoomDebug").v("Clamp Internal: Zoom=$constrainedZoom, PanBoundsX=[$minPanX, $maxPanX], PanBoundsY=[$minPanY, $headerHeightPx]")

            return Triple(constrainedZoom, constrainedX, constrainedY)
        }

        fun clampCamera(
            targetZoom: Float, targetPanX: Float, targetPanY: Float
        ): Triple<Float, Float, Float> {
            return clampValues(targetZoom, targetPanX, targetPanY)
        }

        LaunchedEffect(
            totalDocHeight, screenHeight, headerHeightPx, footerHeightPx, zoomAnimatable.value
        ) {
            if (zoomAnimatable.isRunning || panXAnimatable.isRunning || panYAnimatable.isRunning) {
                return@LaunchedEffect
            }

            val currentPanY = panYAnimatable.value
            val currentPanX = panXAnimatable.value
            val currentZoom = zoomAnimatable.value

            val (z, x, y) = clampValues(currentZoom, currentPanX, currentPanY)

            if (y != currentPanY) {
                panYAnimatable.snapTo(y)
            }
            if (x != currentPanX) {
                panXAnimatable.snapTo(x)
            }
        }

        LaunchedEffect(layoutInfo, totalDocHeight, screenHeight, headerHeightPx) {
            val calculateTargetPanY = { index: Int ->
                if (index in layoutInfo.indices) {
                    val targetPage = layoutInfo[index]
                    val currentZoom = zoomAnimatable.value

                    val screenCenterY = screenHeight / 2f
                    val pageCenterY = (targetPage.y + targetPage.height / 2f) * currentZoom

                    val targetPanY = screenCenterY - pageCenterY

                    val zoomedDocHeight = totalDocHeight * currentZoom
                    val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                        headerHeightPx
                    )

                    targetPanY.coerceIn(minPanY, headerHeightPx)
                } else {
                    null
                }
            }

            state.scrollToPageHandler = { index ->
                val clampedPanY = calculateTargetPanY(index)
                if (clampedPanY != null) {
                    panYAnimatable.animateTo(clampedPanY, animationSpec = tween(500))
                }
            }

            state.snapToPageHandler = { index ->
                val clampedPanY = calculateTargetPanY(index)
                if (clampedPanY != null) {
                    panYAnimatable.snapTo(clampedPanY)
                }
            }
        }

        var selectionClearTrigger by remember { mutableLongStateOf(0L) }

        var isFlinging by remember { mutableStateOf(false) }
        var isFastFlinging by remember { mutableStateOf(false) }

        var draggingBoxId by remember { mutableStateOf<String?>(null) }
        var draggingBoxOffset by remember { mutableStateOf(Offset.Zero) }
        var draggingBoxSize by remember { mutableStateOf(Size.Zero) }
        var draggingBoxTouchDelta by remember { mutableStateOf(Offset.Zero) }
        var draggingBoxPageHeight by remember { mutableFloatStateOf(0f) }

        // Auto-scroll logic
        LaunchedEffect(
            draggingBoxId,
            draggingBoxOffset,
            screenHeight,
            bottomContentPaddingPx,
            topContentPaddingPx
        ) {
            if (draggingBoxId != null) {
                while (isActive) {
                    val scrollZone = 50f
                    val topEdge = headerHeightPx + topContentPaddingPx
                    val bottomEdge = screenHeight - bottomContentPaddingPx

                    var scrollDelta = 0f

                    if (draggingBoxOffset.y < topEdge + scrollZone) {
                        val dist = (draggingBoxOffset.y - topEdge).coerceAtMost(scrollZone)
                        val ratio = 1f - (dist / scrollZone).coerceIn(0f, 1f)
                        scrollDelta = -15f * ratio
                    } else if (draggingBoxOffset.y + draggingBoxSize.height > bottomEdge - scrollZone) {
                        val boxBottom = draggingBoxOffset.y + draggingBoxSize.height
                        val dist = (bottomEdge - boxBottom).coerceAtMost(scrollZone)
                        val ratio = 1f - (dist / scrollZone).coerceIn(0f, 1f)
                        scrollDelta = 15f * ratio
                    }

                    if (abs(scrollDelta) > 0.1f) {
                        val currentY = panYAnimatable.value
                        val newPanY = currentY - scrollDelta

                        val zoomedDocHeight = totalDocHeight * zoomAnimatable.value
                        val minPanY =
                            (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                headerHeightPx
                            )

                        val finalPanY = newPanY.coerceIn(minPanY, headerHeightPx)
                        panYAnimatable.snapTo(finalPanY)
                    }
                    delay(16)
                }
            }
        }

        LaunchedEffect(isFlinging) {
            if (isFlinging) {
                isFastFlinging = true
                while (isActive && isFlinging) {
                    val velX = abs(panXAnimatable.velocity)
                    val velY = abs(panYAnimatable.velocity)
                    val totalVelocity = max(velX, velY)

                    isFastFlinging = totalVelocity > 500f
                    delay(50)
                }
            } else {
                isFastFlinging = false
            }
        }

        LaunchedEffect(isAutoScrollPlaying, isAutoScrollTempPaused, autoScrollSpeed, totalDocHeight, screenHeight) {
            if (isAutoScrollPlaying && !isAutoScrollTempPaused) {
                val baseSpeedPxPerSec = 80f
                var lastFrameTime = withFrameNanos { it }

                while (isActive) {
                    val frameTime = withFrameNanos { it }
                    val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = frameTime

                    if (deltaSeconds > 0.1f) continue

                    val pixelMove = (baseSpeedPxPerSec * autoScrollSpeed) * deltaSeconds

                    val currentPanY = panYAnimatable.value
                    val currentZoom = zoomAnimatable.value
                    val zoomedDocHeight = totalDocHeight * currentZoom

                    val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)

                    val newPanY = (currentPanY - pixelMove).coerceIn(minPanY, headerHeightPx)

                    panYAnimatable.snapTo(newPanY)

                    @Suppress("ControlFlowWithEmptyBody") if (newPanY <= minPanY + 0.1f) {
                        // Reached end
                    }
                }
            }
        }

        var highResScale by remember { mutableFloatStateOf(1f) }
        var isInteracting by remember { mutableStateOf(false) }

        LaunchedEffect(isInteracting) {
            if (isInteracting && isAutoScrollPlaying) {
                onInteractionListener()
            }
        }

        LaunchedEffect(isInteracting) {
            Timber.tag("PdfTouchDebug").i("VerticalReader: isInteracting changed to $isInteracting")
        }

        LaunchedEffect(highResScale) {
            Timber.tag("PdfPerformance").i("VerticalReader HighResScale changed to: $highResScale")
        }

        LaunchedEffect(Unit) {
            snapshotFlow { isInteracting || (isFlinging && isFastFlinging) }.collectLatest { isBusy ->
                Timber.tag("PdfDrawPerf").d(
                    "VerticalReader Interaction State: isBusy=$isBusy (Interacting=$isInteracting, Flinging=$isFlinging, Fast=$isFastFlinging)"
                )

                if (isBusy) {
                    highResScale = 1f
                } else {
                    delay(20)
                    val target = zoomAnimatable.value
                    if (highResScale != target) {
                        Timber.tag("PdfDrawPerf")
                            .v("VerticalReader: Updating highResScale to $target")
                    }
                    highResScale = target
                }
            }
        }

        LaunchedEffect(highResScale, zoomAnimatable.value) {
            Timber.tag("PdfDrawPerf")
                .v("VerticalReader Scale: HighRes=$highResScale, Anim=${zoomAnimatable.value}")
        }

        LaunchedEffect(zoomAnimatable.value) {
            if (!isInteracting && !isFlinging && zoomAnimatable.value != highResScale) {
                highResScale = zoomAnimatable.value
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { isInteracting }.collectLatest { interacting ->
                if (interacting) {
                    highResScale = 1f
                } else {
                    delay(350)
                    highResScale = zoomAnimatable.value
                }
            }
        }

        val imeBottom = imeInsets.getBottom(density)

        LaunchedEffect(
            headerHeightPx,
            footerHeightPx,
            totalDocHeight,
            screenHeight,
            imeBottom,
            isEditMode,
            selectedTool,
            zoomAnimatable.value
        ) {
            val currentZoom = zoomAnimatable.value
            val zoomedDocHeight = totalDocHeight * currentZoom

            val isAnimating = zoomAnimatable.isRunning || panYAnimatable.isRunning || panXAnimatable.isRunning

            val isTextEditing = isEditMode && selectedTool == InkType.TEXT && imeBottom > 0
            val effectiveFooterPx = if (isTextEditing) 0f else footerHeightPx
            val extraScrollForIme = if (isTextEditing) imeBottom.toFloat() else 0f

            val minPanY = (screenHeight - effectiveFooterPx - zoomedDocHeight - extraScrollForIme).coerceAtMost(headerHeightPx)
            val minPanX = -(screenWidth * currentZoom - screenWidth).coerceAtLeast(0f)

            if (!isAnimating) {
                panYAnimatable.updateBounds(lowerBound = minPanY, upperBound = headerHeightPx)
                panXAnimatable.updateBounds(lowerBound = minPanX, upperBound = 0f)
            } else {
                panYAnimatable.updateBounds(
                    lowerBound = minOf(panYAnimatable.lowerBound ?: minPanY, minPanY),
                    upperBound = maxOf(panYAnimatable.upperBound ?: headerHeightPx, headerHeightPx)
                )
                panXAnimatable.updateBounds(
                    lowerBound = minOf(panXAnimatable.lowerBound ?: minPanX, minPanX),
                    upperBound = 0f
                )
            }
        }

        LaunchedEffect(
            richTextController?.cursorPageIndex,
            richTextController?.cursorRectInPage,
            imeBottom,
            density,
            isEditMode,
            selectedTool,
            layoutInfo
        ) {
            val controller = richTextController ?: return@LaunchedEffect

            if (imeBottom == 0 || !isEditMode || selectedTool != InkType.TEXT) {
                return@LaunchedEffect
            }

            val pageIndex = controller.cursorPageIndex
            val cursorRect = controller.cursorRectInPage

            if (pageIndex >= 0 && cursorRect != null) {
                val pageLayout = layoutInfo.find { it.index == pageIndex }

                if (pageLayout != null) {
                    val currentPanY = panYAnimatable.value
                    val currentZoom = zoomAnimatable.value

                    val cursorGlobalTopY =
                        (pageLayout.y + cursorRect.top) * currentZoom + currentPanY
                    val cursorGlobalBottomY =
                        (pageLayout.y + cursorRect.bottom) * currentZoom + currentPanY

                    val topSafeBuffer = with(density) { 80.dp.toPx() }

                    val visibleBottom = screenHeight - imeBottom

                    var requiredShift = 0f

                    if (cursorGlobalBottomY > (visibleBottom)) {
                        requiredShift = visibleBottom - cursorGlobalBottomY
                    } else if (cursorGlobalTopY < topSafeBuffer) {
                        requiredShift = topSafeBuffer - cursorGlobalTopY
                    }

                    if (abs(requiredShift) > 10f) {
                        val targetPanY = currentPanY + requiredShift
                        panYAnimatable.snapTo(targetPanY)
                    }
                }
            }
        }

        val onDoubleTapToZoom: (Offset) -> Unit = { tapScreenOffset ->
            val currentZoom = zoomAnimatable.value
            val targetZoom = if (currentZoom < 1.1f) 2.5f else 1f

            val startPanX = panXAnimatable.value
            val startPanY = panYAnimatable.value

            scope.launch {
                zoomAnimatable.stop()
                panXAnimatable.stop()
                panYAnimatable.stop()

                val pivotContentX = (tapScreenOffset.x - startPanX) / currentZoom
                val pivotContentY = (tapScreenOffset.y - startPanY) / currentZoom

                val durationMillis = 400L
                val startTimeNanos = withFrameNanos { it }
                val durationNanos = durationMillis * 1_000_000L

                while (true) {
                    val now = withFrameNanos { it }
                    val elapsedNanos = now - startTimeNanos
                    val progress = (elapsedNanos.toFloat() / durationNanos).coerceIn(0f, 1f)
                    val easedProgress = FastOutSlowInEasing.transform(progress)

                    val nextZoom = androidx.compose.ui.util.lerp(currentZoom, targetZoom, easedProgress)

                    val rawNextPanX = tapScreenOffset.x - (pivotContentX * nextZoom)
                    val rawNextPanY = tapScreenOffset.y - (pivotContentY * nextZoom)

                    val (clampedZoom, clampedX, clampedY) = clampCamera(nextZoom, rawNextPanX, rawNextPanY)

                    panXAnimatable.updateBounds(
                        lowerBound = minOf(panXAnimatable.lowerBound ?: clampedX, clampedX),
                        upperBound = maxOf(panXAnimatable.upperBound ?: clampedX, clampedX)
                    )
                    panYAnimatable.updateBounds(
                        lowerBound = minOf(panYAnimatable.lowerBound ?: clampedY, clampedY),
                        upperBound = maxOf(panYAnimatable.upperBound ?: clampedY, clampedY)
                    )

                    zoomAnimatable.snapTo(clampedZoom)
                    panXAnimatable.snapTo(clampedX)
                    panYAnimatable.snapTo(clampedY)

                    if (progress >= 1f) break
                }

                onZoomChange(zoomAnimatable.value)

                val finalZoom = zoomAnimatable.value
                panXAnimatable.updateBounds(
                    lowerBound = -(screenWidth * finalZoom - screenWidth).coerceAtLeast(0f),
                    upperBound = 0f
                )
                val zDocH = totalDocHeight * finalZoom
                val minScrollY = (screenHeight - footerHeightPx - zDocH).coerceAtMost(headerHeightPx)
                panYAnimatable.updateBounds(lowerBound = minScrollY, upperBound = headerHeightPx)
            }
        }

        val globalDrawingModifier = Modifier.pointerInput(isEditMode, layoutInfo, selectedTool, isStylusOnlyMode) {
            if (!isEditMode) return@pointerInput
            if (selectedTool == InkType.TEXT) return@pointerInput

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                if (isStylusOnlyMode && down.type == PointerType.Touch) {
                    return@awaitEachGesture
                }

                fun getPageAndPoint(screenOffset: Offset): Pair<Int, PdfPoint>? {
                    val zoom = zoomAnimatable.value
                    val panX = panXAnimatable.value
                    val panY = panYAnimatable.value

                    val docX = (screenOffset.x - panX) / zoom
                    val docY = (screenOffset.y - panY) / zoom

                    val pageLayout = layoutInfo.firstOrNull { page ->
                        docY >= page.y && docY <= (page.y + page.height)
                    } ?: return null

                    val localY = docY - pageLayout.y

                    val normX = (docX / pageLayout.width).coerceIn(0f, 1f)
                    val normY = (localY / pageLayout.height).coerceIn(0f, 1f)

                    return pageLayout.index to PdfPoint(normX, normY)
                }

                var isCanceled = false

                try {
                    if (selectedTool == InkType.ERASER) {
                        globalEraserPosition = down.position
                    }

                    val startData = getPageAndPoint(down.position)
                    if (startData != null) {
                        val (pageIndex, point) = startData
                        onDrawStart(pageIndex, point)
                        down.consume()
                    }

                    var lastPageIndex = startData?.first

                    do {
                        val event = awaitPointerEvent()

                        if (event.changes.size > 1) {
                            isCanceled = true
                            drawingState.onDrawCancel()
                            break
                        }

                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break

                        if (change.positionChanged()) {
                            if (selectedTool == InkType.ERASER) {
                                globalEraserPosition = change.position
                            }

                            val dragData = getPageAndPoint(change.position)
                            if (dragData != null) {
                                val (pageIndex, point) = dragData

                                if (pageIndex != lastPageIndex && selectedTool != InkType.ERASER) {
                                    onDrawEnd()
                                    onDrawStart(pageIndex, point)
                                } else {
                                    onDraw(pageIndex, point)
                                }
                                lastPageIndex = pageIndex
                            }
                            change.consume()
                        }
                    } while (true)
                } finally {
                    if (!isCanceled) {
                        onDrawEnd()
                    }
                    globalEraserPosition = null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(globalDrawingModifier)
                .pointerInput(isEditMode, selectedTool, isStylusOnlyMode) {
                    Timber.tag("PdfTouchDebug").v(
                        "VerticalReader: TapPointerInput init. isEditMode=$isEditMode"
                    )

                    val isTapDetectionAllowed = !isEditMode ||
                            selectedTool == InkType.TEXT ||
                            isStylusOnlyMode

                    if (!isTapDetectionAllowed) return@pointerInput

                    detectTapGestures(onTap = {
                        if (!isEditMode) {
                            Timber.tag("PdfTouchDebug").d("VerticalReader: Tap detected")
                            selectionClearTrigger++
                            onPageClick()
                        } else if (selectedTool == InkType.TEXT) {
                            onPageClick()
                        }
                    }, onDoubleTap = { offset ->
                        Timber.tag("PdfTouchDebug").d("VerticalReader: DoubleTap detected")
                        onDoubleTapToZoom(offset)
                    })
                }
                .pointerInput(totalDocHeight, isEditMode, selectedTool, isScrollLocked, isStylusOnlyMode) {
                    val tracker = VelocityTracker()
                    val decay = exponentialDecay<Float>()
                    val touchSlop = viewConfiguration.touchSlop

                    awaitEachGesture {
                        Timber.tag("PdfTouchDebug").v(
                            "VerticalReader: Gesture Loop Start. isEditMode=$isEditMode"
                        )

                        val down = awaitFirstDown(requireUnconsumed = false)

                        Timber.tag("PointerTypeDebug").d("VerticalReader: Input Type detected: ${down.type}")

                        val isDrawingGesture = isEditMode &&
                                selectedTool != InkType.TEXT &&
                                (!isStylusOnlyMode || down.type != PointerType.Touch)

                        if (isDrawingGesture && down.pressed) {
                            val event = awaitPointerEvent()
                            Timber.tag("PdfTouchDebug").v(
                                "VerticalReader: EditMode check. Event changes: ${event.changes.size}"
                            )
                            if (event.changes.size == 1) {
                                Timber.tag("PdfTouchDebug").v(
                                    "VerticalReader: Ignoring single touch in Edit Mode, waiting for gesture end..."
                                )
                                val originalPointerId = down.id
                                do {
                                    val followUp = awaitPointerEvent()
                                    val originalPointer = followUp.changes.firstOrNull {
                                        it.id == originalPointerId
                                    }
                                } while (originalPointer != null && originalPointer.pressed)
                                Timber.tag("PdfTouchDebug").v(
                                    "VerticalReader: Single touch gesture ended, loop will restart."
                                )
                                return@awaitEachGesture
                            }
                        }

                        Timber.tag("PdfTouchDebug").v(
                            "VerticalReader: Proceeding with gesture logic..."
                        )

                        scope.launch {
                            zoomAnimatable.stop()
                            panXAnimatable.stop()
                            panYAnimatable.stop()
                            panXAnimatable.updateBounds(null, null)
                            panYAnimatable.updateBounds(null, null)
                        }

                        tracker.resetTracking()

                        var velocityTrackerAccumulator = Offset.Zero
                        var accumulatedZoom = zoomAnimatable.value
                        var accumulatedPanX = panXAnimatable.value
                        var accumulatedPanY = panYAnimatable.value

                        var totalPanDistance = 0f
                        var panLocked = false
                        var gestureDisambiguationMode = 0
                        var gestureZoomAccumulator = 1f

                        do {
                            val event = awaitPointerEvent()
                            val isMultiTouch = event.changes.size > 1
                            val canceled = event.changes.any { it.isConsumed } && !isMultiTouch

                            if (canceled) {
                                Timber.tag("PdfTouchDebug").v(
                                    "VerticalReader: Event Canceled (Child consumed?)."
                                )
                            }

                            if (!canceled) {
                                if (isEditMode && isMultiTouch) {
                                    drawingState.onDrawCancel()
                                }

                                val zoomChange = event.calculateZoom()
                                val rawPanChange = event.calculatePan()
                                val panChange = if (isScrollLocked) Offset(0f, rawPanChange.y) else rawPanChange

                                val centroid = event.calculateCentroid(useCurrent = false)
                                val panMagnitude = panChange.getDistance()
                                val currentCentroidSize = event.calculateCentroidSize(
                                    useCurrent = true
                                )
                                val previousCentroidSize = currentCentroidSize / zoomChange
                                val spanMagnitude = abs(
                                    currentCentroidSize - previousCentroidSize
                                )

                                totalPanDistance += panMagnitude
                                gestureZoomAccumulator *= zoomChange

                                val isZoomPastSlop = abs(gestureZoomAccumulator - 1f) > 0.05f
                                val isPanPastSlop = totalPanDistance > touchSlop

                                if (gestureDisambiguationMode == 0) {
                                    if (isPanPastSlop || isZoomPastSlop) {
                                        if (spanMagnitude > panMagnitude * 1.5f) {
                                            gestureDisambiguationMode = 2
                                            Timber.tag("PdfTouchDebug").d(
                                                "Locked to ZOOM (Span > Pan * 1.5)"
                                            )
                                        } else {
                                            gestureDisambiguationMode = 1
                                            Timber.tag("PdfTouchDebug").d(
                                                "Locked to PAN (Pan Dominant)"
                                            )
                                        }
                                    }
                                } else if (gestureDisambiguationMode == 1) {
                                    if (spanMagnitude > (panMagnitude * 3f) && spanMagnitude > 4f) {
                                        gestureDisambiguationMode = 2
                                        Timber.tag("PdfTouchDebug").d(
                                            "Breakout: Switching PAN -> ZOOM"
                                        )
                                    }
                                }

                                val isTouchInput = event.changes.all { it.type == PointerType.Touch }
                                val shouldScroll = (panLocked || gestureDisambiguationMode != 0) &&
                                        (!isEditMode || selectedTool == InkType.TEXT || isMultiTouch || (isStylusOnlyMode && isTouchInput))

                                if (shouldScroll) {
                                    if (!isInteracting) {
                                        Timber.tag("PdfTouchDebug").i(
                                            "VerticalReader: Taking control."
                                        )
                                        isInteracting = true
                                    }

                                    panLocked = true
                                    if (zoomChange != 1f || panChange != Offset.Zero) {

                                        var effectiveZoomChange = zoomChange

                                        if (gestureDisambiguationMode == 1) {
                                            effectiveZoomChange = 1f
                                        }

                                        val oldZoom = accumulatedZoom
                                        val rawTargetZoom = oldZoom * effectiveZoomChange
                                        val constrainedZoom = rawTargetZoom.coerceIn(1f, 5f)
                                        val actualZoomFactor = if (oldZoom == 0f) 1f
                                        else constrainedZoom / oldZoom
                                        val rawNewPanX =
                                            (accumulatedPanX + panChange.x) - (centroid.x - accumulatedPanX) * (actualZoomFactor - 1)
                                        val rawNewPanY =
                                            (accumulatedPanY + panChange.y) - (centroid.y - accumulatedPanY) * (actualZoomFactor - 1)
                                        val (finalZoom, finalX, finalY) = clampCamera(
                                            constrainedZoom, rawNewPanX, rawNewPanY
                                        )

                                        accumulatedZoom = finalZoom
                                        accumulatedPanX = finalX
                                        accumulatedPanY = finalY

                                        if (accumulatedZoom != zoomAnimatable.value) {
                                            onZoomChange(accumulatedZoom)
                                        }

                                        scope.launch {
                                            zoomAnimatable.snapTo(accumulatedZoom)
                                            panXAnimatable.snapTo(accumulatedPanX)
                                            panYAnimatable.snapTo(accumulatedPanY)
                                        }

                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }

                                        if (event.changes.isNotEmpty()) {
                                            velocityTrackerAccumulator += panChange

                                            val time = event.changes[0].uptimeMillis
                                            tracker.addPosition(
                                                time, velocityTrackerAccumulator
                                            )
                                        }
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (isInteracting) {
                            Timber.tag("PdfTouchDebug").v("VerticalReader: Interaction ended")
                            isInteracting = false
                        }

                        val validFlingCondition = panLocked

                        if (validFlingCondition) {
                            val velocity = tracker.calculateVelocity()
                            val flingSensitivity = 2.0f
                            val minFlingVelocity = 250f
                            val (finalZoom, finalX, finalY) = clampCamera(
                                accumulatedZoom, accumulatedPanX, accumulatedPanY
                            )

                            scope.launch {
                                isFlinging = true
                                try {
                                    if (accumulatedZoom !in 1f..5f) {
                                        zoomAnimatable.animateTo(
                                            finalZoom, animationSpec = tween(300)
                                        )
                                    }
                                    onZoomChange(zoomAnimatable.targetValue)
                                    val zoomedDocWidth = screenWidth * finalZoom
                                    val zoomedDocHeight = totalDocHeight * finalZoom
                                    val maxPanX = 0f
                                    val minPanX = -(zoomedDocWidth - screenWidth).coerceAtLeast(0f)
                                    val minPanY =
                                        (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                            headerHeightPx
                                        )
                                    Timber.tag(SCROLL_BOUNDS_TAG).i("Fling Logic:")
                                    Timber.tag(SCROLL_BOUNDS_TAG)
                                        .d("- totalDocHeight: $totalDocHeight, zoom: $finalZoom -> zoomedDocHeight: $zoomedDocHeight")
                                    Timber.tag(SCROLL_BOUNDS_TAG)
                                        .d("- Fling bounds set to Y: [$minPanY, $headerHeightPx]")
                                    panXAnimatable.updateBounds(minPanX, maxPanX)
                                    panYAnimatable.updateBounds(
                                        minPanY, headerHeightPx
                                    )

                                    coroutineScope {
                                        launch {
                                            val rawX = velocity.x * flingSensitivity
                                            val flingX = if (abs(rawX) > minFlingVelocity && !isScrollLocked) rawX
                                            else 0f

                                            if (flingX != 0f) panXAnimatable.animateDecay(
                                                flingX, decay
                                            )
                                        }
                                        launch {
                                            val rawY = velocity.y * flingSensitivity
                                            val flingY = if (abs(rawY) > minFlingVelocity) rawY
                                            else 0f

                                            if (flingY != 0f) panYAnimatable.animateDecay(
                                                flingY, decay
                                            )
                                        }
                                    }
                                } finally {
                                    isFlinging = false
                                }
                            }
                        }
                    }
                }) {
            val cachedVisiblePages = remember { mutableStateOf<List<PdfPageLayout>>(emptyList()) }
            val visiblePages by remember(layoutInfo, screenHeight, textBoxes, draggingBoxId) {
                derivedStateOf {
                    val zoom = zoomAnimatable.value
                    val panY = panYAnimatable.value

                    val viewportTop = -panY / zoom
                    val viewportBottom = (-panY + screenHeight) / zoom
                    val buffer = screenHeight * 0.5f

                    val searchTop = viewportTop - buffer
                    val searchBottom = viewportBottom + buffer

                    // Standard visibility logic
                    val baseVisiblePages = if (layoutInfo.isEmpty()) {
                        emptyList()
                    } else {
                        val searchIndex = layoutInfo.binarySearch { page ->
                            if (page.y + page.height < searchTop) -1
                            else if (page.y > searchBottom) 1 else 0
                        }

                        var startIndex = if (searchIndex < 0) -searchIndex - 1
                        else searchIndex

                        startIndex = startIndex.coerceIn(layoutInfo.indices)
                        while (startIndex > 0 && layoutInfo[startIndex - 1].y + layoutInfo[startIndex - 1].height >= searchTop) {
                            startIndex--
                        }

                        val result = mutableListOf<PdfPageLayout>()
                        for (i in startIndex until layoutInfo.size) {
                            val page = layoutInfo[i]
                            if (page.y > searchBottom) break
                            result.add(page)
                        }
                        result
                    }

                    val draggedBox = textBoxes.find { it.id == draggingBoxId }
                    val originPage = if (draggedBox != null) {
                        layoutInfo.find { it.index == draggedBox.pageIndex }
                    } else null

                    val finalPages =
                        if (originPage != null && baseVisiblePages.none { it.index == originPage.index }) {
                            (baseVisiblePages + originPage).sortedBy { it.index }
                        } else {
                            baseVisiblePages
                        }

                    val cached = cachedVisiblePages.value
                    val indicesMatch = cached.size == finalPages.size && cached.indices.all {
                        cached[it].index == finalPages[it].index
                    }

                    if (!indicesMatch) {
                        cachedVisiblePages.value = finalPages
                        Timber.tag("PdfDrawPerf").d(
                            "Vertical Visible Pages Changed: ${finalPages.map { it.index }} (Dragging: ${draggedBox != null})"
                        )
                        finalPages
                    } else {
                        cached
                    }
                }
            }

            LaunchedEffect(visiblePages, screenHeight) {
                snapshotFlow {
                    Pair(
                        panYAnimatable.value, zoomAnimatable.value
                    )
                }.collectLatest { (panY, zoom) ->
                    if (visiblePages.isNotEmpty()) {
                        state.firstVisiblePage = visiblePages.first().index
                        state.lastVisiblePage = visiblePages.last().index

                        val realViewportTop = -panY / zoom
                        val realViewportBottom = (-panY + screenHeight) / zoom

                        val mostVisible = visiblePages.maxByOrNull { page ->
                            val top = max(page.y, realViewportTop)
                            val bottom = min(page.y + page.height, realViewportBottom)
                            max(0f, bottom - top)
                        }

                        if (mostVisible != null && mostVisible.index != state.currentPage) {
                            state.currentPage = mostVisible.index
                        }
                    }
                }
            }

            Layout(
                content = {
                    visiblePages.forEach { page ->
                        key(page.index) {
                            val isBookmarked by remember(bookmarkSet, page.index) {
                                derivedStateOf {
                                    bookmarkSet.any { it.pageIndex == page.index }
                                }
                            }

                            SideEffect {
                                if (page.index == state.currentPage) {
                                    Timber.tag("PdfDrawPerf")
                                        .v("VERTICAL READER: Emitting Page ${page.index}")
                                }
                            }

                            val visibleScreenRectLambda = remember(page, screenWidth, screenHeight) {
                                {
                                    val zoom = zoomAnimatable.value
                                    val panX = panXAnimatable.value
                                    val panY = panYAnimatable.value

                                    val viewportLeft = -panX / zoom
                                    val viewportTop = -panY / zoom
                                    val viewportRight = (-panX + screenWidth) / zoom
                                    val viewportBottom = (-panY + screenHeight) / zoom

                                    val pageLeft = 0f
                                    val pageRight = page.width
                                    val pageTop = page.y
                                    val pageBottom = page.y + page.height

                                    val visibleLeft = max(viewportLeft, pageLeft)
                                    val visibleTop = max(viewportTop, pageTop)
                                    val visibleRight = min(viewportRight, pageRight)
                                    val visibleBottom = min(viewportBottom, pageBottom)

                                    if (visibleLeft < visibleRight && visibleTop < visibleBottom) {
                                        val localLeft = (visibleLeft - pageLeft).toInt()
                                        val localTop = (visibleTop - pageTop).toInt()
                                        val localRight = (visibleRight - pageLeft).toInt()
                                        val localBottom = (visibleBottom - pageTop).toInt()

                                        val result = androidx.compose.ui.unit.IntRect(
                                            left = localLeft,
                                            top = localTop,
                                            right = localRight,
                                            bottom = localBottom
                                        )
                                        result
                                    } else {
                                        null
                                    }
                                }
                            }

                            val pageTtsData =
                                if (ttsReadingPage == page.index) ttsHighlightData else null

                            var ocrHighlightRects by remember {
                                mutableStateOf<List<RectF>>(emptyList())
                            }
                            val stableOcrHighlightRects = remember(ocrHighlightRects) {
                                StableHolder(ocrHighlightRects)
                            }
                            LaunchedEffect(searchResultToHighlight, page.index) {
                                ocrHighlightRects = emptyList()
                                if (searchResultToHighlight != null && searchResultToHighlight.locationInSource == page.index) {
                                    ocrHighlightRects = onGetOcrSearchRects(
                                        page.index, searchResultToHighlight.query
                                    )
                                }
                            }

                            val searchResultForPage =
                                if (searchResultToHighlight?.locationInSource == page.index) {
                                    searchResultToHighlight
                                } else {
                                    null
                                }

                            val onDrawStartLambda = remember(page.index, onDrawStart) {
                                { point: PdfPoint -> onDrawStart(page.index, point) }
                            }

                            val currentOnDraw by rememberUpdatedState(onDraw)
                            val onDrawLambda = remember(page.index) {
                                { point: PdfPoint -> currentOnDraw(page.index, point) }
                            }

                            val onSingleTapLambda = remember(onPageClick) {
                                {
                                    selectionClearTrigger++
                                    onPageClick()
                                }
                            }

                            val onDoubleTapLambda = remember(page, screenWidth, screenHeight) {
                                { localOffset: Offset ->
                                    Timber.tag("PdfZoomDebug").d(
                                        "Page ${page.index} Double Tap: Local=$localOffset, PageY=${page.y}"
                                    )
                                    val contentX = localOffset.x
                                    val contentY = localOffset.y + page.y
                                    val currentZ = zoomAnimatable.value
                                    val panX = panXAnimatable.value
                                    val panY = panYAnimatable.value
                                    val screenX = contentX * currentZ + panX
                                    val screenY = contentY * currentZ + panY
                                    Timber.tag("PdfZoomDebug").d("Mapped to Screen: ($screenX, $screenY)") // Added log
                                    onDoubleTapToZoom(Offset(screenX, screenY))
                                }
                            }

                            val onTtsHighlightCenter: (Float) -> Unit =
                                remember(page.index, ttsReadingPage) {
                                    { highlightCenterY ->
                                        if (page.index == ttsReadingPage && !isInteracting) {
                                            val currentZ = zoomAnimatable.value
                                            val absoluteHighlightY = page.y + highlightCenterY
                                            val targetPanY =
                                                (screenHeight / 2) - (absoluteHighlightY * currentZ)
                                            val zoomedDocHeight = totalDocHeight * currentZ
                                            val minPanY =
                                                (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                                    headerHeightPx
                                                )

                                            val clampedPanY = targetPanY.coerceIn(
                                                minPanY, headerHeightPx
                                            )

                                            scope.launch {
                                                panYAnimatable.animateTo(
                                                    clampedPanY, animationSpec = tween(500)
                                                )
                                            }
                                        }
                                    }
                                }

                            val onSearchHighlightCenter: (Float) -> Unit =
                                remember(page.index, searchResultToHighlight) {
                                    { highlightCenterY ->
                                        if (searchResultToHighlight?.locationInSource == page.index && !isInteracting) {
                                            val currentZ = zoomAnimatable.value
                                            val absoluteHighlightY = page.y + highlightCenterY

                                            val targetPanY =
                                                (screenHeight / 2) - (absoluteHighlightY * currentZ)

                                            val zoomedDocHeight = totalDocHeight * currentZ
                                            val minPanY =
                                                (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                                    headerHeightPx
                                                )

                                            val clampedPanY = targetPanY.coerceIn(
                                                minPanY, headerHeightPx
                                            )

                                            scope.launch {
                                                panYAnimatable.animateTo(
                                                    clampedPanY, animationSpec = tween(500)
                                                )
                                            }
                                        }
                                    }
                                }

                            val pageAnnotationsProvider = remember(page.index, allAnnotations) {
                                { allAnnotations()[page.index] ?: emptyList() }
                            }
                            val virtualPage =
                                if (virtualPages.isNotEmpty()) virtualPages.getOrNull(page.index)
                                else VirtualPage.PdfPage(page.index)

                            Box(modifier = Modifier
                                .layoutId(page)
                                .graphicsLayer {
                                    val z = zoomAnimatable.value
                                    val px = panXAnimatable.value
                                    val py = panYAnimatable.value

                                    scaleX = z
                                    scaleY = z
                                    translationX = px
                                    translationY = page.y * (z - 1f) + py
                                    transformOrigin = TransformOrigin(0f, 0f)

                                    if (page.index < 2 && z > 1.1f) {
                                        Timber.tag("PdfZoomDebug").v("Page ${page.index} Render: TransY=$translationY (PageY=${page.y}, GlobalY=${page.y + translationY})")
                                    }
                                }
                                .clipToBounds()
                                .onGloballyPositioned { coordinates ->
                                    if (page.index == 0) {
                                        val pos = coordinates.positionInWindow()
                                        Timber.d(
                                            "Page 0 Box | GlobalPos: $pos | Size: ${coordinates.size} | PageY: ${page.y}"
                                        )
                                    }
                                }) {
                                PdfPageComposable(
                                    pdfDocument = pdfDocument,
                                    pageIndex = page.index,
                                    virtualPage = virtualPage,
                                    totalPages = totalPages,
                                    isDarkMode = isDarkMode,
                                    externalScale = highResScale,
                                    onScaleChanged = {},
                                    showAllTextHighlights = showAllTextHighlights,
                                    onHighlightLoading = onHighlightLoading,
                                    searchQuery = searchQuery,
                                    searchHighlightMode = searchHighlightMode,
                                    searchResultToHighlight = searchResultForPage,
                                    ocrHoverHighlights = stableOcrHighlightRects,
                                    onSingleTap = onSingleTapLambda,
                                    isProUser = isProUser,
                                    onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialog,
                                    onWordSelectedForAiDefinition = onWordSelectedForAiDefinition,
                                    ttsHighlightData = pageTtsData,
                                    onLinkClicked = onLinkClicked,
                                    onInternalLinkClicked = onInternalLinkClicked,
                                    isBookmarked = isBookmarked,
                                    onOcrStateChange = onOcrStateChange,
                                    onBookmarkClick = { onBookmarkClick(page.index) },
                                    isZoomEnabled = false,
                                    isScrolling = isInteracting || (isFlinging && isFastFlinging),
                                    isVerticalScroll = true,
                                    visualScaleProvider = currentScaleProvider,
                                    onDoubleTap = onDoubleTapLambda,
                                    clearSelectionTrigger = selectionClearTrigger,
                                    onTtsHighlightCenterCalculated = onTtsHighlightCenter,
                                    onSearchHighlightCenterCalculated = onSearchHighlightCenter,
                                    isEditMode = isEditMode,
                                    pageAnnotations = pageAnnotationsProvider,
                                    drawingState = drawingState,
                                    onDrawStart = onDrawStartLambda,
                                    onDraw = onDrawLambda,
                                    onDrawEnd = onDrawEnd,
                                    visibleScreenRect = visibleScreenRectLambda,
                                    onOcrModelDownloading = onOcrModelDownloading,
                                    selectedTool = selectedTool,
                                    richTextController = richTextController,
                                    isStylusOnlyMode = isStylusOnlyMode,
                                    textBoxes = textBoxes.filter { it.pageIndex == page.index },
                                    selectedTextBoxId = selectedTextBoxId,
                                    onTextBoxChange = onTextBoxChange,
                                    onTextBoxSelect = onTextBoxSelect,
                                    onTextBoxDragStart = { box, localTopLeft, touchOffset ->
                                        val currentZoom = zoomAnimatable.value
                                        val panX = panXAnimatable.value
                                        val panY = panYAnimatable.value
                                        val pageScreenY = page.y * currentZoom + panY

                                        val boxScreenX = panX + (localTopLeft.x * currentZoom)
                                        val boxScreenY = pageScreenY + (localTopLeft.y * currentZoom)

                                        draggingBoxSize = Size(
                                            box.relativeBounds.width * page.width * currentZoom,
                                            box.relativeBounds.height * page.height * currentZoom
                                        )
                                        draggingBoxPageHeight = page.height * currentZoom

                                        draggingBoxOffset = Offset(boxScreenX, boxScreenY)
                                        draggingBoxTouchDelta = touchOffset * currentZoom
                                        draggingBoxId = box.id
                                    },
                                    onTextBoxDrag = { dragDelta ->
                                        draggingBoxOffset += dragDelta
                                    },
                                    onTextBoxDragEnd = {
                                        scope.launch {
                                            val boxCenterY =
                                                draggingBoxOffset.y + draggingBoxSize.height / 2f
                                            val currentZoom = zoomAnimatable.value
                                            val panY = panYAnimatable.value
                                            val docY = (boxCenterY - panY) / currentZoom

                                            val targetPage = layoutInfo.minByOrNull {
                                                val pageCenter = it.y + (it.height / 2f)
                                                abs(docY - pageCenter)
                                            }

                                            if (targetPage != null && draggingBoxId != null) {
                                                val panX = panXAnimatable.value
                                                val pageScreenY = targetPage.y * currentZoom + panY

                                                val paddingPx = with(density) { 12.dp.toPx() }
                                                val padRelW =
                                                    if (targetPage.width > 0) paddingPx / (targetPage.width * currentZoom) else 0f
                                                val padRelH =
                                                    if (targetPage.height > 0) paddingPx / (targetPage.height * currentZoom) else 0f

                                                val finalBoxX = draggingBoxOffset.x - panX
                                                val finalBoxY = draggingBoxOffset.y - pageScreenY

                                                val rawRelX =
                                                    (finalBoxX / currentZoom) / targetPage.width
                                                val rawRelY =
                                                    (finalBoxY / currentZoom) / targetPage.height
                                                val relW =
                                                    (draggingBoxSize.width / currentZoom) / targetPage.width
                                                val relH =
                                                    (draggingBoxSize.height / currentZoom) / targetPage.height

                                                val clampedW = relW.coerceAtMost(1f)
                                                val clampedH = relH.coerceAtMost(1f)

                                                val maxRelX =
                                                    (1f - clampedW - padRelW).coerceAtLeast(padRelW)
                                                val maxRelY =
                                                    (1f - clampedH - padRelH).coerceAtLeast(padRelH)

                                                val finalRelX = rawRelX.coerceIn(padRelW, maxRelX)
                                                val finalRelY = rawRelY.coerceIn(padRelH, maxRelY)

                                                val targetScreenX =
                                                    panX + (finalRelX * targetPage.width * currentZoom)
                                                val targetScreenY =
                                                    pageScreenY + (finalRelY * targetPage.height * currentZoom)
                                                val targetOffset = Offset(targetScreenX, targetScreenY)

                                                val startOffset = draggingBoxOffset
                                                Animatable(0f).animateTo(1f) {
                                                    draggingBoxOffset =
                                                        lerp(startOffset, targetOffset, value)
                                                }

                                                val newBounds = Rect(
                                                    finalRelX,
                                                    finalRelY,
                                                    finalRelX + clampedW,
                                                    finalRelY + clampedH
                                                )
                                                onTextBoxMoved(
                                                    draggingBoxId!!,
                                                    targetPage.index,
                                                    newBounds
                                                )
                                            }
                                            draggingBoxId = null
                                        }
                                    },
                                    draggingBoxId = draggingBoxId
                                )
                            }

                            if (page.index < totalPages - 1) {
                                val dividerY = page.y + page.height
                                Box(
                                    modifier = Modifier
                                        .layoutId(
                                            DividerLayout(
                                                dividerY, page.width, dividerHeightPx
                                            )
                                        )
                                        .graphicsLayer {
                                            val z = zoomAnimatable.value
                                            val px = panXAnimatable.value
                                            val py = panYAnimatable.value

                                            scaleX = z
                                            scaleY = z
                                            translationX = px
                                            translationY = dividerY * (z - 1f) + py
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                        ))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { _ -> }) { measurables, constraints ->
                val layoutStart = System.nanoTime()
                Timber.tag("PdfDrawPerf")
                    .v("VERTICAL LAYOUT: Measure Pass (${measurables.size} items)")
                val measureResult = layout(constraints.maxWidth, constraints.maxHeight) {
                    measurables.forEach { measurable ->
                        when (val id = measurable.layoutId) {
                            is PdfPageLayout -> {
                                val placeable = measurable.measure(
                                    Constraints.fixed(
                                        id.width.roundToInt(), id.height.roundToInt()
                                    )
                                )
                                placeable.place(0, id.y.roundToInt())
                            }

                            is DividerLayout -> {
                                val placeable = measurable.measure(
                                    Constraints.fixed(
                                        id.width.roundToInt(), id.height.roundToInt()
                                    )
                                )
                                placeable.place(0, id.y.roundToInt())
                            }
                        }
                    }
                }
                val layoutTime = (System.nanoTime() - layoutStart) / 1_000_000f
                if (layoutTime > 2f) {
                    Timber.tag("PdfPerformance").d(
                        "VerticalReader Layout Measure/Place took ${layoutTime}ms for ${measurables.size} items"
                    )
                }
                measureResult
            }
        }

        val currentZoom = zoomAnimatable.value
        @Suppress("unused") val zoomedDocHeight = totalDocHeight * currentZoom

        val currentZoomVal = zoomAnimatable.value
        val zDocH = totalDocHeight * currentZoomVal
        val minScrollY = (screenHeight - footerHeightPx - zDocH).coerceAtMost(headerHeightPx)
        val totalScrollRange = headerHeightPx - minScrollY

        val scrollProgress = if (abs(totalScrollRange) < 1f) 0f
        else {
            ((headerHeightPx - panYAnimatable.value) / abs(totalScrollRange)).coerceIn(
                0f, 1f
            )
        }

        var isDraggingScrollbar by remember { mutableStateOf(false) }
        var scrollbarVisible by remember { mutableStateOf(false) }
        var lastScrollInteraction by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            var previousValue = panYAnimatable.value
            snapshotFlow { panYAnimatable.value }.collect { newValue ->
                if (abs(newValue - previousValue) > 1f) {
                    lastScrollInteraction = System.currentTimeMillis()
                }
                previousValue = newValue
            }
        }

        LaunchedEffect(lastScrollInteraction, isDraggingScrollbar) {
            if (isDraggingScrollbar) {
                scrollbarVisible = true
            } else {
                if (lastScrollInteraction > 0L) {
                    scrollbarVisible = true
                    delay(5000)
                    scrollbarVisible = false
                }
            }
        }

        val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (scrollbarVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "scrollbarAlpha"
        )

        val samsungBlue = Color(0xFF4285F4)
        val samsungBlueDark = Color(0xFF1976D2)
        val activeThemeColor = if (isDarkMode) samsungBlueDark else samsungBlue
        val scrollbarIdleColor = if (isDarkMode) Color.Gray else Color.DarkGray

        val barColor by animateColorAsState(
            targetValue = if (isDraggingScrollbar) activeThemeColor else scrollbarIdleColor,
            label = "barColor"
        )

        val scrollbarIdleWidth = 4.dp
        val scrollbarActiveWidth = 8.dp
        val barWidth by animateDpAsState(
            targetValue = if (isDraggingScrollbar) scrollbarActiveWidth
            else scrollbarIdleWidth, label = "barWidth"
        )

        val scrollbarIdleHeight = 40.dp
        val scrollbarActiveHeight = 60.dp
        val barHeight by animateDpAsState(
            targetValue = if (isDraggingScrollbar) scrollbarActiveHeight
            else scrollbarIdleHeight, label = "barHeight"
        )

        val barCornerRadius = 100.dp

        var scrollbarTrackHeight by remember { mutableFloatStateOf(0f) }

        @Suppress("unused") var thumbLayoutCoordinates by remember {
            mutableStateOf<LayoutCoordinates?>(
                null
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = headerHeight + 12.dp, bottom = footerHeight + 12.dp)
                .width(48.dp)
                .onGloballyPositioned { coordinates ->
                    scrollbarTrackHeight = coordinates.size.height.toFloat()
                }) {
            val thumbHeightPx = with(density) { barHeight.toPx() }
            val effectiveTrackHeight = if (scrollbarTrackHeight > 0f) scrollbarTrackHeight
            else (screenHeight - headerHeightPx - footerHeightPx)

            val availableSpace = (effectiveTrackHeight - thumbHeightPx).coerceAtLeast(0f)
            val thumbY = (availableSpace * scrollProgress).coerceIn(0f, availableSpace)

            Box(modifier = Modifier
                .offset { IntOffset(x = 0, y = thumbY.toInt()) }
                .align(Alignment.TopEnd)
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .padding(end = 4.dp)
                .alpha(scrollbarAlpha)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = isDraggingScrollbar,
                        enter = fadeIn() + androidx.compose.animation.slideInHorizontally {
                            it / 2
                        },
                        exit = fadeOut() + androidx.compose.animation.slideOutHorizontally {
                            it / 2
                        }) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = activeThemeColor,
                            shadowElevation = 4.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "${state.currentPage + 1}/${totalPages}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .height(barHeight)
                            .width(48.dp)
                            .onGloballyPositioned {}
                            .pointerInput(
                                scrollbarTrackHeight, totalDocHeight, screenHeight
                            ) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false
                                    )

                                    try {
                                        isDraggingScrollbar = true
                                        isInteracting = true
                                        lastScrollInteraction = System.currentTimeMillis()
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull {
                                                it.id == down.id
                                            }

                                            if (change == null || !change.pressed) {
                                                break
                                            }

                                            val deltaY =
                                                change.position.y - change.previousPosition.y

                                            if (deltaY != 0f) {
                                                change.consume()

                                                val currentZoom = zoomAnimatable.value
                                                val zoomedDocHeight = totalDocHeight * currentZoom

                                                // Re-calculate bounds exactly as
                                                // the main logic does
                                                val maxS = headerHeightPx
                                                val minS =
                                                    (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                                        maxS
                                                    )

                                                // Calculate total scrollable track
                                                // space
                                                val trackH =
                                                    if (scrollbarTrackHeight > 0f) scrollbarTrackHeight
                                                    else (screenHeight - headerHeightPx - footerHeightPx)
                                                val thumbH = scrollbarActiveHeight.toPx()
                                                val trackSpace = (trackH - thumbH).coerceAtLeast(1f)

                                                // Calculate raw delta
                                                val dragFraction = deltaY / trackSpace
                                                val totalRange = maxS - minS // This is
                                                // negative,
                                                // e.g. -5000
                                                val scrollDelta = dragFraction * abs(totalRange)

                                                // Apply delta
                                                val rawTargetPanY =
                                                    (panYAnimatable.value - scrollDelta)

                                                // FORCE CLAMP: Ensure we never set
                                                // a value outside bounds
                                                // This prevents the "Gap" where the
                                                // scrollbar pushes past the footer
                                                val clampedTargetPanY = rawTargetPanY.coerceIn(
                                                    minS, maxS
                                                )

                                                scope.launch {
                                                    panYAnimatable.snapTo(
                                                        clampedTargetPanY
                                                    )
                                                }

                                                lastScrollInteraction = System.currentTimeMillis()
                                            }
                                        }
                                    } finally {
                                        isDraggingScrollbar = false
                                        isInteracting = false
                                        lastScrollInteraction = System.currentTimeMillis()
                                    }
                                }
                            }) {
                        Box(
                            modifier = Modifier
                                .size(width = barWidth, height = barHeight)
                                .background(
                                    color = barColor, shape = RoundedCornerShape(barCornerRadius)
                                )
                        )
                    }
                }
            }
        }

        if (isEditMode && selectedTool == InkType.ERASER && globalEraserPosition != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pos = globalEraserPosition!!
                val radiusPx = 8.dp.toPx()

                drawCircle(color = Color.White.copy(alpha = 0.3f), radius = radiusPx, center = pos)

                drawCircle(
                    color = Color.Black,
                    radius = radiusPx,
                    center = pos,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        if (draggingBoxId != null) {
            val draggedBox = textBoxes.find { it.id == draggingBoxId }
            if (draggedBox != null) {
                val currentBoxHeight = draggingBoxSize.height
                val fontScaleRatio =
                    if (currentBoxHeight > 0) draggingBoxPageHeight / currentBoxHeight else 1f

                val boxBottomY = draggingBoxOffset.y + draggingBoxSize.height
                val spaceBelow = screenHeight - boxBottomY
                val overlayHandlePos =
                    if (spaceBelow < with(density) { 60.dp.toPx() }) HandlePosition.TOP else HandlePosition.BOTTOM

                Box(modifier = Modifier
                    .offset {
                        IntOffset(
                            draggingBoxOffset.x.roundToInt(), draggingBoxOffset.y.roundToInt()
                        )
                    }
                    .zIndex(100f)) {
                    ResizableTextBox(
                        box = draggedBox.copy(
                            relativeBounds = Rect(0f, 0f, 1f, 1f),
                            fontSize = draggedBox.fontSize * fontScaleRatio
                        ),
                        isSelected = true,
                        isEditMode = false,
                        isDarkMode = isDarkMode,
                        pageWidthPx = draggingBoxSize.width,
                        pageHeightPx = draggingBoxSize.height,
                        handlePosition = overlayHandlePos,
                        onBoundsChanged = {},
                        onTextChanged = {},
                        onSelect = {},
                        onDragStart = {},
                        onDrag = { _, _ -> },
                        onDragEnd = {})
                }
            }
        }
    }
}