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

import com.aryan.reader.shared.ReaderTheme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.splineBasedDecay
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.pdf.calculatePdfVerticalPageLayoutPx
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.finitePdfZoomValue
import com.aryan.reader.shared.pdf.PDF_MAX_ZOOM_SCALE
import com.aryan.reader.shared.pdf.pdfVerticalDoubleTapTargetScale
import com.aryan.reader.shared.pdf.pdfVerticalPageGapDp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
private const val VERTICAL_TILE_RENDER_IDLE_COOLDOWN_MS = 220L

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
    internal var scrollByHandler: (suspend (Float) -> Unit)? = null
    internal var scrollToTopHandler: (suspend () -> Unit)? = null
    internal var scrollToBottomHandler: (suspend () -> Unit)? = null

    suspend fun scrollToPage(pageIndex: Int) {
        scrollToPageHandler?.invoke(pageIndex)
    }

    suspend fun snapToPage(pageIndex: Int) {
        snapToPageHandler?.invoke(pageIndex)
    }

    suspend fun scrollBy(delta: Float) {
        scrollByHandler?.invoke(delta)
    }

    suspend fun scrollToTop() {
        scrollToTopHandler?.invoke()
    }

    suspend fun scrollToBottom() {
        scrollToBottomHandler?.invoke()
    }
}

@Composable
fun rememberVerticalPdfReaderState(): VerticalPdfReaderState {
    return remember { VerticalPdfReaderState() }
}

private data class PdfPageLayout(
    val index: Int,
    val yPx: Int,
    val heightPx: Int,
    val widthPx: Int,
    val widthDp: Dp,
    val heightDp: Dp
) {
    val y: Float
        get() = yPx.toFloat()

    val height: Float
        get() = heightPx.toFloat()

    val width: Float
        get() = widthPx.toFloat()
}

private data class DividerLayout(val yPx: Int, val widthPx: Int, val heightPx: Int) {
    val y: Float
        get() = yPx.toFloat()

    val width: Float
        get() = widthPx.toFloat()

    val height: Float
        get() = heightPx.toFloat()
}

@Suppress("UnusedVariable")
@SuppressLint("UnusedBoxWithConstraintsScope", "BinaryOperationInTimber")
@OptIn(FlowPreview::class)
@Composable
internal fun PdfVerticalReader(
    modifier: Modifier = Modifier,
    state: VerticalPdfReaderState,
    pdfDocument: StableHolder<ReaderDocument>,
    documentKey: String,
    activeTheme: ReaderTheme,
    activeTextureAlpha: Float = 0.55f,
    excludeImages: Boolean = false,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
    totalPages: Int,
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
    onTranslateText: (String) -> Unit,
    onSearchText: (String) -> Unit,
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
    onDrawStart: (Int, PdfPoint, Boolean) -> Unit,
    onDraw: (Int, PdfPoint, Boolean) -> Unit,
    onDrawEnd: () -> Unit,
    onOcrModelDownloading: () -> Unit = {},
    selectedTool: InkType,
    richTextController: RichTextController? = null,
    textBoxes: List<PdfTextBox> = emptyList(),
    textBoxesByPage: Map<Int, List<PdfTextBox>> = emptyMap(),
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
    isStylusOnlyMode: Boolean = false,
    stylusButtonHovering: Boolean = false,
    isHighlighterSnapEnabled: Boolean = false,
    userHighlights: List<PdfUserHighlight> = emptyList(),
    userHighlightsByPage: Map<Int, List<PdfUserHighlight>> = emptyMap(),
    onHighlightAdd: (Int, Pair<Int, Int>, String, PdfHighlightColor, HighlightStyle) -> Unit = { _,_,_,_,_ -> },
    onHighlightUpdate: (String, PdfHighlightColor, HighlightStyle?) -> Unit = { _,_,_ -> },
    onHighlightDelete: (String) -> Unit = {},
    onNoteRequested: (String?) -> Unit = {},
    onTts: (Int, Int) -> Unit = { _, _ -> },
    activeToolThickness: Float = 0f,
    eraserToolThickness: Float = 0f,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onPaletteClick: () -> Unit = {},
    lockedState: Triple<Float, Float, Float>? = null,
    onZoomAndPanChanged: ((Float, Offset) -> Unit)? = null,
    resetZoomTrigger: Long = 0L,
    isBubbleZoomModeActive: Boolean = false,
    showPageGap: Boolean = true,
    showPageNumberOverlay: Boolean = true,
    onDetectBubbles: suspend (Int, Bitmap) -> List<SpeechBubble> = { _, _ -> emptyList() }
) {
    DisposableEffect(state) {
        onDispose {
            state.scrollToPageHandler = null
            state.snapToPageHandler = null
            state.scrollByHandler = null
            state.scrollToTopHandler = null
            state.scrollToBottomHandler = null
        }
    }
    var globalEraserPosition by remember { mutableStateOf<Offset?>(null) }
    var isStylusEraserOverride by remember { mutableStateOf(false) }
    val isDarkMode = activeTheme.isDark || activeTheme.id == "reverse"
    val effectiveReverseColorMode = if (activeTheme.id == "reverse") {
        reverseColorMode
    } else {
        PdfReverseColorMode.RGB
    }
    val verticalPageBackgroundColor = remember(activeTheme) {
        resolvePdfVerticalPageBackgroundColor(activeTheme)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        val imeInsets = WindowInsets.ime
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        val ratios = pageAspectRatios.item
        val bookmarkSet = bookmarks.item
        val effectiveTextBoxesByPage = remember(textBoxes, textBoxesByPage) {
            textBoxesByPage.ifEmpty { textBoxes.groupBy { it.pageIndex } }
        }
        val effectiveUserHighlightsByPage = remember(userHighlights, userHighlightsByPage) {
            userHighlightsByPage.ifEmpty { userHighlights.groupBy { it.pageIndex } }
        }

        val scope = rememberCoroutineScope()

        LaunchedEffect(constraints, density) {
            Timber.d(
                "Screen Dims: width=$screenWidth, height=$screenHeight, constraints=$constraints"
            )
        }

        val headerHeightPx = with(density) { headerHeight.toPx() }
        val footerHeightPx = with(density) { footerHeight.toPx() }

        val dividerHeightDp = pdfVerticalPageGapDp(showPageGap, 8.dp)
        val dividerHeightPx = with(density) { dividerHeightDp.toPx() }
        val dividerHeightPxInt = dividerHeightPx.roundToInt().coerceAtLeast(0)

        var isFlinging by remember { mutableStateOf(false) }
        var verticalFlingJob by remember { mutableStateOf<Job?>(null) }
        var gestureCameraTakeoverJob by remember { mutableStateOf<Job?>(null) }
        var isFastFlinging by remember { mutableStateOf(false) }
        var isInteracting by remember { mutableStateOf(false) }
        var isZoomAnimating by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var isScrollPreparing by remember { mutableStateOf(false) }
        var scrollTraceGestureId by remember { mutableLongStateOf(0L) }
        val renderedFlingVelocity = remember { FloatArray(2) }
        var isTileRenderIdleCooldownActive by remember { mutableStateOf(false) }

        LaunchedEffect(isDragging, isFlinging) {
            if (isDragging || isFlinging) {
                if (!isTileRenderIdleCooldownActive) {
                    PdfVerticalPerfLog.d("tile-render-cooldown active=true reason=busy")
                }
                isTileRenderIdleCooldownActive = true
            } else if (isTileRenderIdleCooldownActive) {
                delay(VERTICAL_TILE_RENDER_IDLE_COOLDOWN_MS)
                PdfVerticalPerfLog.d(
                    "tile-render-cooldown active=false idleFor=${VERTICAL_TILE_RENDER_IDLE_COOLDOWN_MS}ms"
                )
                isTileRenderIdleCooldownActive = false
            }
        }

        val layoutState = remember(ratios, constraints.maxWidth, constraints.maxHeight, density, showPageGap, dividerHeightPxInt) {
            data class LayoutResult(val pages: List<PdfPageLayout>, val totalHeight: Float)

            val verticalLayout = calculatePdfVerticalPageLayoutPx(
                pageAspectRatios = ratios,
                viewportWidthPx = constraints.maxWidth,
                viewportHeightPx = constraints.maxHeight,
                pageGapPx = dividerHeightPxInt
            )

            val pages = verticalLayout.pages.map { page ->
                PdfPageLayout(
                    index = page.pageIndex,
                    yPx = page.topPx,
                    heightPx = page.heightPx,
                    widthPx = page.widthPx,
                    widthDp = with(density) { page.widthPx.toDp() },
                    heightDp = with(density) { page.heightPx.toDp() }
                )
            }

            LayoutResult(pages, verticalLayout.totalHeightPx.toFloat())
        }

        val layoutInfo = layoutState.pages
        val totalDocHeight = layoutState.totalHeight
        Timber.tag(SCROLL_BOUNDS_TAG)
            .d("Layout Recalculated. Page Count: ${layoutInfo.size}, TotalDocHeight: $totalDocHeight")

        val fitZoom = remember(ratios, screenWidth, screenHeight) {
            if (ratios.isEmpty() || screenWidth == 0f || screenHeight == 0f) 1f
            else {
                val firstRatio = ratios.firstOrNull { it > 0f } ?: 1f
                val baseHeight = screenWidth / firstRatio
                if (screenWidth > screenHeight) {
                    ((screenHeight - 32f) / baseHeight).coerceAtMost(1f)
                } else {
                    1f
                }
            }
        }

        LaunchedEffect(layoutInfo, totalDocHeight, screenWidth, screenHeight, fitZoom, headerHeightPx, footerHeightPx) {
            PdfVerticalPerfLog.i(
                "layout-ready pages=${layoutInfo.size} totalH=${PdfVerticalPerfLog.f(totalDocHeight)} " +
                    "screen=${PdfVerticalPerfLog.xy(screenWidth, screenHeight)} chrome=${PdfVerticalPerfLog.xy(headerHeightPx, footerHeightPx)} " +
                    "fitZoom=${PdfVerticalPerfLog.f(fitZoom)} firstPageH=${PdfVerticalPerfLog.f(layoutInfo.firstOrNull()?.height ?: 0f)}"
            )
        }

        val zoomAnimatable = remember { Animatable(fitZoom) }
        val panXAnimatable = remember { Animatable(if ((screenWidth * fitZoom) < screenWidth) (screenWidth - (screenWidth * fitZoom)) / 2f else 0f) }
        val panYAnimatable = remember { Animatable(0f) }
        var cameraZoom by remember { mutableFloatStateOf(fitZoom) }
        var cameraPanX by remember {
            mutableFloatStateOf(
                if ((screenWidth * fitZoom) < screenWidth) {
                    (screenWidth - (screenWidth * fitZoom)) / 2f
                } else {
                    0f
                }
            )
        }
        var cameraPanY by remember { mutableFloatStateOf(0f) }

        fun commitRenderedCamera(zoom: Float, panX: Float, panY: Float) {
            cameraZoom = zoom
            cameraPanX = panX
            cameraPanY = panY
        }

        var cameraEpoch by remember { mutableLongStateOf(0L) }
        val oneHandZoomDistancePx = with(density) {
            PDF_ONE_HAND_ZOOM_DRAG_DISTANCE_FOR_DOUBLE_DP.dp.toPx()
        }
        var oneHandZoomStartZoom by remember { mutableFloatStateOf(fitZoom) }
        var oneHandZoomStartPan by remember { mutableStateOf(Offset.Zero) }
        var oneHandZoomPivotScreen by remember { mutableStateOf(Offset.Zero) }
        var oneHandZoomFinalCamera by remember { mutableStateOf(Triple(fitZoom, 0f, 0f)) }
        var isVerticalOneHandZooming by remember { mutableStateOf(false) }
        val latestIsVerticalOneHandZooming by rememberUpdatedState(isVerticalOneHandZooming)

        LaunchedEffect(zoomAnimatable, panXAnimatable, panYAnimatable) {
            snapshotFlow {
                Triple(zoomAnimatable.value, panXAnimatable.value, panYAnimatable.value)
            }.collect { (zoom, panX, panY) ->
                // Direct manipulation already writes the rendered camera synchronously.
                // Animations and programmatic moves mirror their frame values here.
                if (!isInteracting && !isFlinging) {
                    commitRenderedCamera(zoom, panX, panY)
                }
            }
        }

        val latestOnZoomAndPanChanged by rememberUpdatedState(onZoomAndPanChanged)
        LaunchedEffect(Unit) {
            snapshotFlow {
                Triple(cameraZoom, cameraPanX, cameraPanY)
            }.collect { (zoom, panX, panY) ->
                latestOnZoomAndPanChanged?.invoke(zoom, Offset(panX, panY))
            }
        }

        LaunchedEffect(isInteracting, isFlinging) {
            if (isInteracting || isFlinging) {
                var previousFrameNanos = withFrameNanos { it }
                while (isActive && (isInteracting || isFlinging)) {
                    val frameNanos = withFrameNanos { it }
                    val frameMs = (frameNanos - previousFrameNanos) / 1_000_000f
                    previousFrameNanos = frameNanos
                    if (frameMs >= 20f) {
                        PdfScrollTrace.d(
                            "FRAME slow=${PdfVerticalPerfLog.f(frameMs)}ms " +
                                "interacting=$isInteracting flinging=$isFlinging " +
                                "camera=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
                        )
                    }
                }
            }
        }

        var isResizing by remember { mutableStateOf(false) }
        var previousScreenWidth by remember { mutableFloatStateOf(0f) }
        var previousScreenHeight by remember { mutableFloatStateOf(0f) }
        var lockedOrientationChangedDuringResize by remember { mutableStateOf(false) }
        val targetPageDuringResize = remember { mutableIntStateOf(-1) }

        if (previousScreenWidth != screenWidth || previousScreenHeight != screenHeight) {
            if (previousScreenWidth > 0f) {
                val previousWasLandscape = previousScreenWidth > previousScreenHeight
                val currentIsLandscape = screenWidth > screenHeight
                isResizing = true
                if (isScrollLocked && previousWasLandscape != currentIsLandscape) {
                    lockedOrientationChangedDuringResize = true
                }
                if (targetPageDuringResize.intValue == -1) {
                    targetPageDuringResize.intValue = state.currentPage
                }
            }
            previousScreenWidth = screenWidth
            previousScreenHeight = screenHeight
        }

        var isInitialLayout by remember { mutableStateOf(true) }
        var previousPageLayout by remember { mutableStateOf<List<PdfPageLayout>>(emptyList()) }
        val currentScaleProvider = remember(zoomAnimatable) { { cameraZoom } }

        var hasRestoredLockedState by remember { mutableStateOf(false) }

        LaunchedEffect(isScrollLocked, lockedState, totalDocHeight, screenWidth, isInteracting, isFlinging) {
            if (!hasRestoredLockedState && isScrollLocked && lockedState != null && totalDocHeight > 0f && screenWidth > 0f && !isInteracting && !isFlinging) {
                val (savedScale, savedPanX, savedPanY) = lockedState

                Timber.tag("PdfLockDiagnostic").i("RESTORING: Scale=$savedScale, X=$savedPanX, Y=$savedPanY")

                val zoomedDocWidth = screenWidth * savedScale
                val minPanX = if (zoomedDocWidth < screenWidth) (screenWidth - zoomedDocWidth) / 2f else -(zoomedDocWidth - screenWidth)
                val maxPanX = if (zoomedDocWidth < screenWidth) minPanX else 0f

                val zoomedDocHeight = totalDocHeight * savedScale
                val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)

                zoomAnimatable.stop()
                panXAnimatable.stop()
                panYAnimatable.stop()

                panXAnimatable.updateBounds(minPanX, maxPanX)
                panYAnimatable.updateBounds(minPanY, headerHeightPx)

                zoomAnimatable.snapTo(savedScale)
                panXAnimatable.snapTo(savedPanX)
                panYAnimatable.snapTo(savedPanY.coerceIn(minPanY, headerHeightPx))

                Timber.tag("PdfLockDiagnostic").d("RESTORE SNAP COMPLETE: Scale=${cameraZoom}, X=${cameraPanX}, Y=${cameraPanY}")

                hasRestoredLockedState = true
            }
        }

        LaunchedEffect(layoutState.pages, isInteracting, isFlinging, isZoomAnimating) {
            // Real page dimensions can arrive while the pointer or decay animation owns the
            // camera. Re-run this effect when motion ends instead of correcting underneath it.
            if (isInteracting || isFlinging || isZoomAnimating) return@LaunchedEffect
            if (!isInitialLayout && isScrollLocked && lockedOrientationChangedDuringResize) {
                val targetPageIdx = if (targetPageDuringResize.intValue != -1) {
                    targetPageDuringResize.intValue
                } else {
                    state.currentPage
                }

                val newLayout = layoutState.pages
                val pageLayout = newLayout.getOrNull(targetPageIdx)

                if (pageLayout != null) {
                    val resetCamera = calculateLockedOrientationResetCamera(
                        pageTopY = pageLayout.y,
                        totalDocHeight = layoutState.totalHeight,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        headerHeightPx = headerHeightPx,
                        footerHeightPx = footerHeightPx,
                        fitZoom = fitZoom
                    )

                    panXAnimatable.updateBounds(null, null)
                    panYAnimatable.updateBounds(null, null)

                    coroutineScope {
                        launch { zoomAnimatable.snapTo(resetCamera.zoom) }
                        launch { panXAnimatable.snapTo(resetCamera.panX) }
                        launch { panYAnimatable.snapTo(resetCamera.panY) }
                    }

                    state.currentPage = targetPageIdx
                    hasRestoredLockedState = true
                    onZoomChange(resetCamera.zoom)
                    onZoomAndPanChanged?.invoke(resetCamera.zoom, Offset(resetCamera.panX, resetCamera.panY))
                    Timber.tag("PdfLockDiagnostic").i(
                        "Orientation changed while locked; reset zoom to fit and kept page $targetPageIdx"
                    )
                }
            } else if (!isInitialLayout && !isScrollLocked) {
                val targetPageIdx = if (targetPageDuringResize.intValue != -1) {
                    targetPageDuringResize.intValue
                } else {
                    state.currentPage
                }

                val newLayout = layoutState.pages
                val pageLayout = newLayout.getOrNull(targetPageIdx)

                if (pageLayout != null) {
                    val currentZoom = cameraZoom
                    val isFit = isPdfVerticalZoomNearFit(currentZoom, fitZoom)
                    val targetZoom = if (isFit) fitZoom else currentZoom
                    val isViewportResize = targetPageDuringResize.intValue != -1
                    val oldAnchorPage = if (!isViewportResize && previousPageLayout.isNotEmpty()) {
                        val oldDocumentCenter = (screenHeight / 2f - cameraPanY) /
                            currentZoom.coerceAtLeast(0.01f)
                        previousPageLayout.firstOrNull {
                            oldDocumentCenter in it.y..(it.y + it.height)
                        } ?: previousPageLayout.getOrNull(targetPageIdx)
                    } else {
                        null
                    }

                    // Geometry refinement must preserve the PDF point currently under the
                    // viewport centre. Snapping to the page top made mixed-size documents
                    // visibly jump when their real aspect ratios arrived.
                    val targetPanY = if (oldAnchorPage != null) {
                        preservedPdfVerticalPanY(
                            oldPanY = cameraPanY,
                            oldZoom = currentZoom,
                            newZoom = targetZoom,
                            viewportAnchorY = screenHeight / 2f,
                            oldPageTopY = oldAnchorPage.y,
                            oldPageHeight = oldAnchorPage.height,
                            newPageTopY = pageLayout.y,
                            newPageHeight = pageLayout.height,
                        )
                    } else {
                        headerHeightPx - (pageLayout.y * targetZoom)
                    }
                    val zoomedDocHeight = layoutState.totalHeight * targetZoom
                    val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)
                    val finalPanY = targetPanY.coerceIn(minPanY, headerHeightPx)

                    val targetPanX = if (isFit) {
                        if ((screenWidth * targetZoom) < screenWidth) {
                            (screenWidth - (screenWidth * targetZoom)) / 2f
                        } else 0f
                    } else {
                        cameraPanX
                    }

                    panXAnimatable.updateBounds(null, null)
                    panYAnimatable.updateBounds(null, null)

                    coroutineScope {
                        launch { zoomAnimatable.snapTo(targetZoom) }
                        launch { panXAnimatable.snapTo(targetPanX) }
                        launch { panYAnimatable.snapTo(finalPanY) }
                    }
                }
            }

            if (!isInitialLayout) {
                delay(50)
                isResizing = false
                lockedOrientationChangedDuringResize = false
                targetPageDuringResize.intValue = -1
            }
            previousPageLayout = layoutState.pages
            isInitialLayout = false
        }

        fun clampValues(
            targetZoom: Float, targetPanX: Float, targetPanY: Float
        ): Triple<Float, Float, Float> {
            val safeFitZoom = finitePdfZoomValue(fitZoom, 1f).coerceIn(0.1f, PDF_MAX_ZOOM_SCALE)
            val constrainedZoom = finitePdfZoomValue(targetZoom, safeFitZoom)
                .coerceIn(safeFitZoom, PDF_MAX_ZOOM_SCALE)
            val safeScreenWidth = finitePdfZoomValue(screenWidth).coerceAtLeast(0f)
            val safeScreenHeight = finitePdfZoomValue(screenHeight).coerceAtLeast(0f)
            val safeDocumentHeight = finitePdfZoomValue(totalDocHeight).coerceAtLeast(0f)
            val safeHeaderHeight = finitePdfZoomValue(headerHeightPx).coerceAtLeast(0f)
            val safeFooterHeight = finitePdfZoomValue(footerHeightPx).coerceAtLeast(0f)
            val zoomedDocWidth = safeScreenWidth * constrainedZoom
            val zoomedDocHeight = safeDocumentHeight * constrainedZoom

            val constrainedX = if (zoomedDocWidth < safeScreenWidth) {
                (safeScreenWidth - zoomedDocWidth) / 2f
            } else {
                val maxPanX = 0f
                val minPanX = -(zoomedDocWidth - safeScreenWidth)
                finitePdfZoomValue(targetPanX).coerceIn(minPanX, maxPanX)
            }

            val minPanY = if (zoomedDocHeight < (safeScreenHeight - safeHeaderHeight - safeFooterHeight)) {
                safeHeaderHeight
            } else {
                (safeScreenHeight - safeFooterHeight - zoomedDocHeight).coerceAtMost(safeHeaderHeight)
            }

            val constrainedY = finitePdfZoomValue(targetPanY, safeHeaderHeight).coerceIn(minPanY, safeHeaderHeight)

            return Triple(constrainedZoom, constrainedX, constrainedY)
        }

        fun clampCamera(
            targetZoom: Float, targetPanX: Float, targetPanY: Float
        ): Triple<Float, Float, Float> {
            return clampValues(targetZoom, targetPanX, targetPanY)
        }

        fun updatePanBoundsForZoom(finalZoom: Float) {
            val safeScreenWidth = finitePdfZoomValue(screenWidth).coerceAtLeast(0f)
            val safeScreenHeight = finitePdfZoomValue(screenHeight).coerceAtLeast(0f)
            val safeDocumentHeight = finitePdfZoomValue(totalDocHeight).coerceAtLeast(0f)
            val safeHeaderHeight = finitePdfZoomValue(headerHeightPx).coerceAtLeast(0f)
            val safeFooterHeight = finitePdfZoomValue(footerHeightPx).coerceAtLeast(0f)
            val safeZoom = finitePdfZoomValue(finalZoom, 1f).coerceAtLeast(0.1f)
            val zoomedDocWidth = safeScreenWidth * safeZoom
            val (finalMinX, finalMaxX) = if (zoomedDocWidth < safeScreenWidth) {
                val centeredX = (safeScreenWidth - zoomedDocWidth) / 2f
                centeredX to centeredX
            } else {
                -(zoomedDocWidth - safeScreenWidth) to 0f
            }
            panXAnimatable.updateBounds(lowerBound = finalMinX, upperBound = finalMaxX)

            val zoomedDocHeight = safeDocumentHeight * safeZoom
            val minPanY = (safeScreenHeight - safeFooterHeight - zoomedDocHeight).coerceAtMost(safeHeaderHeight)
            panYAnimatable.updateBounds(lowerBound = minPanY, upperBound = safeHeaderHeight)
        }

        LaunchedEffect(resetZoomTrigger) {
            if (resetZoomTrigger != 0L && cameraZoom > fitZoom && !isScrollLocked) {
                scope.launch {
                    zoomAnimatable.stop()
                    panXAnimatable.stop()
                    panYAnimatable.stop()

                    val startZoom = cameraZoom
                    val startPanX = cameraPanX
                    val startPanY = cameraPanY

                    val pivotScreenX = screenWidth / 2f
                    val pivotScreenY = screenHeight / 2f

                    val pivotContentX = (pivotScreenX - startPanX) / startZoom
                    val pivotContentY = (pivotScreenY - startPanY) / startZoom

                    val rawNextPanX = pivotScreenX - (pivotContentX * fitZoom)
                    val rawNextPanY = pivotScreenY - (pivotContentY * fitZoom)

                    val (finalZoom, finalX, finalY) = clampCamera(fitZoom, rawNextPanX, rawNextPanY)

                    panXAnimatable.updateBounds(
                        lowerBound = minOf(finitePdfZoomValue(panXAnimatable.lowerBound ?: finalX, finalX), finalX, finitePdfZoomValue(startPanX, finalX)),
                        upperBound = maxOf(finitePdfZoomValue(panXAnimatable.upperBound ?: finalX, finalX), finalX, finitePdfZoomValue(startPanX, finalX))
                    )
                    panYAnimatable.updateBounds(
                        lowerBound = minOf(finitePdfZoomValue(panYAnimatable.lowerBound ?: finalY, finalY), finalY, finitePdfZoomValue(startPanY, finalY)),
                        upperBound = maxOf(finitePdfZoomValue(panYAnimatable.upperBound ?: finalY, finalY), finalY, finitePdfZoomValue(startPanY, finalY))
                    )

                    coroutineScope {
                        launch { zoomAnimatable.animateTo(finalZoom, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                        launch { panXAnimatable.animateTo(finalX, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                        launch { panYAnimatable.animateTo(finalY, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                    }

                    onZoomChange(cameraZoom)

                    updatePanBoundsForZoom(finalZoom)
                }
            }
        }

        LaunchedEffect(
            totalDocHeight, screenHeight, headerHeightPx, footerHeightPx,
            isInteracting, isFlinging, isResizing, isZoomAnimating
        ) {
            if (
                zoomAnimatable.isRunning || panXAnimatable.isRunning || panYAnimatable.isRunning ||
                isInteracting || isFlinging || isResizing || isZoomAnimating
            ) {
                return@LaunchedEffect
            }

            val currentPanY = cameraPanY
            val currentPanX = cameraPanX
            val currentZoom = cameraZoom

            val (z, x, y) = clampValues(currentZoom, currentPanX, currentPanY)

            if (y != currentPanY) {
                panYAnimatable.snapTo(y)
            }
            if (x != currentPanX) {
                if (isScrollLocked) {
                    Timber.tag("PdfLockDiagnostic").d("FORCED SNAP: X=$currentPanX to $x")
                }
                panXAnimatable.snapTo(x)
            }
        }

        LaunchedEffect(layoutInfo, totalDocHeight, screenHeight, headerHeightPx) {
            val calculateTargetPanY = { index: Int ->
                if (index in layoutInfo.indices) {
                    val targetPage = layoutInfo[index]
                    val currentZoom = cameraZoom

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
                Timber.tag("PdfPositionDebug").d("VerticalReader: snapToPage($index) called. ClampedPanY: $clampedPanY")
                if (clampedPanY != null) {
                    panYAnimatable.snapTo(clampedPanY)
                }
            }

            state.scrollByHandler = { delta ->
                val currentZoom = cameraZoom
                val zoomedDocHeight = totalDocHeight * currentZoom

                val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)

                val targetPanY = (cameraPanY - delta).coerceIn(minPanY, headerHeightPx)

                if (abs(targetPanY - cameraPanY) > 0.5f) {
                    panYAnimatable.animateTo(
                        targetValue = targetPanY,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                    )
                }
            }

            state.scrollToTopHandler = {
                panYAnimatable.animateTo(
                    targetValue = headerHeightPx,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }

            state.scrollToBottomHandler = {
                val currentZoom = cameraZoom
                val zoomedDocHeight = totalDocHeight * currentZoom
                val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)
                panYAnimatable.animateTo(
                    targetValue = minPanY,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }
        }

        var selectionClearTrigger by remember { mutableLongStateOf(0L) }
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
                    } else if (draggingBoxOffset.y + (draggingBoxSize.height * cameraZoom) > bottomEdge - scrollZone) {
                        val boxBottom = draggingBoxOffset.y + (draggingBoxSize.height * cameraZoom)
                        val dist = (bottomEdge - boxBottom).coerceAtMost(scrollZone)
                        val ratio = 1f - (dist / scrollZone).coerceIn(0f, 1f)
                        scrollDelta = 15f * ratio
                    }

                    if (abs(scrollDelta) > 0.1f) {
                        val currentY = cameraPanY
                        val newPanY = currentY - scrollDelta

                        val zoomedDocHeight = totalDocHeight * cameraZoom
                        val minPanY =
                            (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                headerHeightPx
                            )

                        val finalPanY = newPanY.coerceIn(minPanY, headerHeightPx)
                        panYAnimatable.snapTo(finalPanY)
                        commitRenderedCamera(cameraZoom, cameraPanX, finalPanY)
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

        LaunchedEffect(
            isAutoScrollPlaying,
            isAutoScrollTempPaused,
            autoScrollSpeed,
            totalDocHeight,
            screenHeight,
            isInteracting,
            isFlinging,
        ) {
            if (isAutoScrollPlaying && !isAutoScrollTempPaused && !isInteracting && !isFlinging) {
                val baseSpeedPxPerSec = 80f
                var lastFrameTime = withFrameNanos { it }

                while (isActive) {
                    val frameTime = withFrameNanos { it }
                    val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = frameTime

                    if (deltaSeconds > 0.1f) continue

                    val pixelMove = (baseSpeedPxPerSec * autoScrollSpeed) * deltaSeconds

                    val currentPanY = cameraPanY
                    val currentZoom = cameraZoom
                    val zoomedDocHeight = totalDocHeight * currentZoom

                    val minPanY = (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)

                    val newPanY = (currentPanY - pixelMove).coerceIn(minPanY, headerHeightPx)

                    panYAnimatable.snapTo(newPanY)
                    commitRenderedCamera(cameraZoom, cameraPanX, newPanY)

                    @Suppress("ControlFlowWithEmptyBody") if (newPanY <= minPanY + 0.1f) {
                        // Reached end
                    }
                }
            }
        }

        var highResScale by remember { mutableFloatStateOf(1f) }

        LaunchedEffect(isInteracting) {
            if (isInteracting && isAutoScrollPlaying) {
                onInteractionListener()
            }
        }

        LaunchedEffect(isInteracting) {
            Timber.tag("PdfTouchDebug").i("VerticalReader: isInteracting changed to $isInteracting")
            PdfVerticalPerfLog.i(
                "interaction-state interacting=$isInteracting dragging=$isDragging flinging=$isFlinging " +
                    "zoom=${PdfVerticalPerfLog.f(cameraZoom)} pan=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
            )
        }

        LaunchedEffect(highResScale) {
            Timber.tag("PdfPerformance").i("VerticalReader HighResScale changed to: $highResScale")
            PdfVerticalPerfLog.i(
                "high-res-scale scale=${PdfVerticalPerfLog.f(highResScale)} zoom=${PdfVerticalPerfLog.f(cameraZoom)} " +
                    "interacting=$isInteracting flinging=$isFlinging fastFlinging=$isFastFlinging"
            )
        }

        LaunchedEffect(Unit) {
            snapshotFlow { isInteracting || isFlinging || isZoomAnimating }.collectLatest { isBusy ->
                Timber.tag("PdfDrawPerf").d(
                    "VerticalReader Interaction State: isBusy=$isBusy (Interacting=$isInteracting, Flinging=$isFlinging, Fast=$isFastFlinging)"
                )
                PdfVerticalPerfLog.d(
                    "render-resolution-gate busy=$isBusy interacting=$isInteracting flinging=$isFlinging " +
                        "fastFlinging=$isFastFlinging highRes=${PdfVerticalPerfLog.f(highResScale)} zoom=${PdfVerticalPerfLog.f(cameraZoom)}"
                )

                if (!isBusy) {
                    delay(50)
                    val target = cameraZoom
                    if (highResScale != target) {
                        Timber.tag("PdfDrawPerf").v("VerticalReader: Updating highResScale to $target")
                        PdfVerticalPerfLog.i(
                            "high-res-scale-update from=${PdfVerticalPerfLog.f(highResScale)} to=${PdfVerticalPerfLog.f(target)} " +
                                "pan=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
                        )
                        highResScale = target
                    }
                }
            }
        }

        LaunchedEffect(highResScale) {
            Timber.tag("PdfDrawPerf")
                .v("VerticalReader Scale: HighRes=$highResScale, Anim=${cameraZoom}")
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
            isInteracting,
            isFlinging,
            isResizing
        ) {
            if (isInteracting || isFlinging || isResizing) return@LaunchedEffect

            val currentZoom = cameraZoom
            val zoomedDocHeight = totalDocHeight * currentZoom
            val zoomedDocWidth = screenWidth * currentZoom

            val isAnimating = zoomAnimatable.isRunning || panYAnimatable.isRunning || panXAnimatable.isRunning

            val isTextEditing = isEditMode && selectedTool == InkType.TEXT && imeBottom > 0
            val effectiveFooterPx = if (isTextEditing) 0f else footerHeightPx
            val extraScrollForIme = if (isTextEditing) imeBottom.toFloat() else 0f

            val minPanY = (screenHeight - effectiveFooterPx - zoomedDocHeight - extraScrollForIme).coerceAtMost(headerHeightPx)

            val minPanX: Float
            val maxPanX: Float
            if (zoomedDocWidth < screenWidth) {
                val centeredX = (screenWidth - zoomedDocWidth) / 2f
                minPanX = centeredX
                maxPanX = centeredX
            } else {
                minPanX = -(zoomedDocWidth - screenWidth)
                maxPanX = 0f
            }

            if (!isAnimating) {
                if (isScrollLocked) {
                    Timber.tag("PdfLockDiagnostic").v("CLAMP CHECK: X=${cameraPanX} | Allowed Range=[$minPanX, $maxPanX]")
                }
                panYAnimatable.updateBounds(lowerBound = minPanY, upperBound = headerHeightPx)
                panXAnimatable.updateBounds(lowerBound = minPanX, upperBound = maxPanX)
            } else {
                panYAnimatable.updateBounds(
                    lowerBound = minOf(panYAnimatable.lowerBound ?: minPanY, minPanY),
                    upperBound = maxOf(panYAnimatable.upperBound ?: headerHeightPx, headerHeightPx)
                )
                panXAnimatable.updateBounds(
                    lowerBound = minOf(panXAnimatable.lowerBound ?: minPanX, minPanX),
                    upperBound = maxOf(panXAnimatable.upperBound ?: maxPanX, maxPanX)
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
            layoutInfo,
            isInteracting,
            isFlinging,
        ) {
            val controller = richTextController ?: return@LaunchedEffect

            if (imeBottom == 0 || !isEditMode || selectedTool != InkType.TEXT || isInteracting || isFlinging) {
                return@LaunchedEffect
            }

            val pageIndex = controller.cursorPageIndex
            val cursorRect = controller.cursorRectInPage

            if (pageIndex >= 0 && cursorRect != null) {
                val pageLayout = layoutInfo.find { it.index == pageIndex }

                if (pageLayout != null) {
                    val currentPanY = cameraPanY
                    val currentZoom = cameraZoom

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
            pdfZoomDiagnostic(
                "vertical camera callback tap=$tapScreenOffset locked=$isScrollLocked " +
                    "zoom=${cameraZoom} pan=(${cameraPanX},${cameraPanY}) fit=$fitZoom"
            )
            if (!isScrollLocked) {
                val currentZoom = cameraZoom

                val targetZoom = pdfVerticalDoubleTapTargetScale(currentZoom, fitZoom)
                pdfZoomDiagnostic("vertical camera target startZoom=$currentZoom targetZoom=$targetZoom")

                val startPanX = cameraPanX
                val startPanY = cameraPanY

                scope.launch {
                    val pendingTakeover = gestureCameraTakeoverJob
                    pendingTakeover?.join()
                    if (gestureCameraTakeoverJob === pendingTakeover) {
                        gestureCameraTakeoverJob = null
                    }
                    isZoomAnimating = true
                    try {
                        zoomAnimatable.stop()
                        panXAnimatable.stop()
                        panYAnimatable.stop()

                        val pivotContentX = (tapScreenOffset.x - startPanX) / currentZoom
                        val pivotContentY = (tapScreenOffset.y - startPanY) / currentZoom

                        val rawNextPanX = tapScreenOffset.x - (pivotContentX * targetZoom)
                        val rawNextPanY = tapScreenOffset.y - (pivotContentY * targetZoom)

                        val (finalZoom, finalX, finalY) = clampCamera(targetZoom, rawNextPanX, rawNextPanY)
                        pdfZoomDiagnostic(
                            "vertical camera clamped zoom=$finalZoom pan=($finalX,$finalY) " +
                                "rawPan=($rawNextPanX,$rawNextPanY)"
                        )

                        panXAnimatable.updateBounds(
                            lowerBound = minOf(finitePdfZoomValue(panXAnimatable.lowerBound ?: finalX, finalX), finalX, finitePdfZoomValue(startPanX, finalX)),
                            upperBound = maxOf(finitePdfZoomValue(panXAnimatable.upperBound ?: finalX, finalX), finalX, finitePdfZoomValue(startPanX, finalX))
                        )
                        panYAnimatable.updateBounds(
                            lowerBound = minOf(finitePdfZoomValue(panYAnimatable.lowerBound ?: finalY, finalY), finalY, finitePdfZoomValue(startPanY, finalY)),
                            upperBound = maxOf(finitePdfZoomValue(panYAnimatable.upperBound ?: finalY, finalY), finalY, finitePdfZoomValue(startPanY, finalY))
                        )

                        coroutineScope {
                            launch { zoomAnimatable.animateTo(finalZoom, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                            launch { panXAnimatable.animateTo(finalX, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                            launch { panYAnimatable.animateTo(finalY, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                        }

                        onZoomChange(cameraZoom)

                        updatePanBoundsForZoom(finalZoom)
                        pdfZoomDiagnostic(
                            "vertical camera animation complete zoom=${cameraZoom} " +
                                "pan=(${cameraPanX},${cameraPanY})"
                        )
                    } finally {
                        isZoomAnimating = false
                        pdfZoomDiagnostic(
                            "vertical camera animation end/cancel zoom=${cameraZoom} " +
                                "pan=(${cameraPanX},${cameraPanY})"
                        )
                    }
                }
            }
        }

        val onDoubleTapDragZoomStart: (Offset) -> Unit = {
            if (!isScrollLocked) {
                verticalFlingJob?.cancel()
                verticalFlingJob = null
                cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    zoomAnimatable.stop()
                    panXAnimatable.stop()
                    panYAnimatable.stop()
                    panXAnimatable.updateBounds(null, null)
                    panYAnimatable.updateBounds(null, null)
                }
                isVerticalOneHandZooming = true
                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                    "vertical.oneHandStart requestedPivot=$it center=(${(screenWidth / 2f).toInt()},${(screenHeight / 2f).toInt()}) " +
                        "zoom=${cameraZoom} pan=(${cameraPanX},${cameraPanY})"
                )
                oneHandZoomPivotScreen = Offset(screenWidth / 2f, screenHeight / 2f)
                oneHandZoomStartZoom = cameraZoom
                oneHandZoomStartPan = Offset(cameraPanX, cameraPanY)
                oneHandZoomFinalCamera = Triple(
                    oneHandZoomStartZoom,
                    oneHandZoomStartPan.x,
                    oneHandZoomStartPan.y,
                )
                isInteracting = true
                isDragging = true
            }
        }

        val onDoubleTapDragZoom: (Offset, Float) -> Unit = { _, totalDragY ->
            if (!isScrollLocked) {
                val screenDragY = totalDragY * oneHandZoomStartZoom
                val targetZoom = pdfOneHandZoomScale(
                    startScale = oneHandZoomStartZoom,
                    totalDragY = screenDragY,
                    dragDistanceForDoublePx = oneHandZoomDistancePx,
                    minScale = fitZoom,
                    maxScale = PDF_MAX_ZOOM_SCALE
                )
                val rawPan = topLeftPdfPanForScaleChange(
                    previousScale = oneHandZoomStartZoom,
                    nextScale = targetZoom,
                    previousPan = oneHandZoomStartPan,
                    pivot = oneHandZoomPivotScreen
                )
                val (finalZoom, finalX, finalY) = clampCamera(targetZoom, rawPan.x, rawPan.y)
                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                    "vertical.oneHandUpdate dragY=$totalDragY screenDragY=$screenDragY " +
                        "targetZoom=$targetZoom finalZoom=$finalZoom pan=($finalX,$finalY)"
                )
                onZoomChange(finalZoom)
                oneHandZoomFinalCamera = Triple(finalZoom, finalX, finalY)
                commitRenderedCamera(finalZoom, finalX, finalY)
            }
        }

        val onDoubleTapDragZoomEnd: () -> Unit = {
            val wasOneHandZooming = isVerticalOneHandZooming
            isVerticalOneHandZooming = false
            if (!isScrollLocked || wasOneHandZooming) {
                cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                val finalCamera = oneHandZoomFinalCamera
                scope.launch {
                    zoomAnimatable.snapTo(finalCamera.first)
                    panXAnimatable.snapTo(finalCamera.second)
                    panYAnimatable.snapTo(finalCamera.third)
                    val currentZoom = finalCamera.first
                    if (currentZoom > fitZoom && currentZoom < fitZoom * 1.05f) {
                        val (finalZoom, finalX, finalY) = clampCamera(
                            fitZoom,
                            cameraPanX,
                            cameraPanY
                        )
                        coroutineScope {
                            launch { zoomAnimatable.animateTo(finalZoom, animationSpec = tween(180, easing = FastOutSlowInEasing)) }
                            launch { panXAnimatable.animateTo(finalX, animationSpec = tween(180, easing = FastOutSlowInEasing)) }
                            launch { panYAnimatable.animateTo(finalY, animationSpec = tween(180, easing = FastOutSlowInEasing)) }
                        }
                        onZoomChange(finalZoom)
                        updatePanBoundsForZoom(finalZoom)
                    } else {
                        updatePanBoundsForZoom(currentZoom)
                    }
                    isInteracting = false
                    isDragging = false
                }
                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                    "vertical.oneHandEnd zoom=${finalCamera.first} pan=(${finalCamera.second},${finalCamera.third})"
                )
            }
        }

        val currentOnPageClick by rememberUpdatedState(onPageClick)
        val currentOnDoubleTapToZoom by rememberUpdatedState(onDoubleTapToZoom)
        val currentOnDoubleTapDragZoomStart by rememberUpdatedState(onDoubleTapDragZoomStart)
        val currentOnDoubleTapDragZoom by rememberUpdatedState(onDoubleTapDragZoom)
        val currentOnDoubleTapDragZoomEnd by rememberUpdatedState(onDoubleTapDragZoomEnd)

        val globalDrawingModifier = Modifier.pointerInput(
            isEditMode,
            layoutInfo,
            selectedTool,
            isStylusOnlyMode,
            isHighlighterSnapEnabled
        ) {
            if (!isEditMode) return@pointerInput
            if (selectedTool == InkType.TEXT) return@pointerInput

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                if (isStylusOnlyMode && down.type == PointerType.Touch) {
                    return@awaitEachGesture
                }

                val buttons = currentEvent.buttons
                Timber.tag("StylusDebug").d(
                    "VerticalReader | Type: ${down.type} | isPrimary: ${buttons.isPrimaryPressed} | isSecondary: ${buttons.isSecondaryPressed} | isTertiary: ${buttons.isTertiaryPressed} | buttonsString: $buttons"
                )

                val isEraserOverride = down.type == PointerType.Eraser ||
                        (down.type == PointerType.Stylus && (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isPrimaryPressed || stylusButtonHovering))
                isStylusEraserOverride = isEraserOverride

                fun getPageAndPoint(screenOffset: Offset): Pair<Int, PdfPoint>? {
                    val zoom = cameraZoom
                    val panX = cameraPanX
                    val panY = cameraPanY

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
                    if (selectedTool == InkType.ERASER || isEraserOverride) {
                        globalEraserPosition = down.position
                    }

                    val startData = getPageAndPoint(down.position)
                    if (startData != null) {
                        val (pageIndex, point) = startData
                        onDrawStart(pageIndex, point, isEraserOverride)
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
                            if (selectedTool == InkType.ERASER || isEraserOverride) {
                                globalEraserPosition = change.position
                            }

                            val dragData = getPageAndPoint(change.position)
                            if (dragData != null) {
                                val (pageIndex, point) = dragData

                                if (pageIndex != lastPageIndex && selectedTool != InkType.ERASER && !isEraserOverride) {
                                    onDrawEnd()
                                    onDrawStart(pageIndex, point, isEraserOverride)
                                } else {
                                    onDraw(pageIndex, point, isEraserOverride)
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
                    isStylusEraserOverride = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (showPageGap) Color.Transparent else verticalPageBackgroundColor)
                // Touchpads send scroll events rather than press-and-drag changes.
                // The custom PDF camera handles the latter itself, so explicitly
                // translate wheel/trackpad deltas into bounded vertical panning.
                .pointerInput(totalDocHeight, screenHeight, headerHeightPx, footerHeightPx) {
                    val scrollStepPx = with(density) { 48.dp.toPx() }
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue

                            val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollDeltaY == 0f) continue

                            val zoomedDocumentHeight = totalDocHeight * cameraZoom
                            val minPanY = (
                                screenHeight - footerHeightPx - zoomedDocumentHeight
                            ).coerceAtMost(headerHeightPx)
                            val targetPanY = pdfTouchpadScrollTargetPanY(
                                currentPanY = cameraPanY,
                                scrollDeltaY = scrollDeltaY,
                                scrollStepPx = scrollStepPx,
                                minPanY = minPanY,
                                maxPanY = headerHeightPx
                            )

                            if (targetPanY != cameraPanY) {
                                verticalFlingJob?.cancel()
                                verticalFlingJob = null
                                cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    panYAnimatable.stop()
                                    panYAnimatable.snapTo(targetPanY)
                                }
                                commitRenderedCamera(cameraZoom, cameraPanX, targetPanY)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .then(globalDrawingModifier)
                // Vertical zoom gestures live here so page tap handlers do not steal
                // alternating double-tap-hold attempts.
                .pointerInput(
                    layoutInfo,
                    isEditMode,
                    selectedTool,
                    isStylusOnlyMode,
                    isScrollLocked
                ) {
                    val isTapDetectionAllowed = !isEditMode ||
                            selectedTool == InkType.TEXT ||
                            isStylusOnlyMode

                    if (!isTapDetectionAllowed) {
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "vertical.rootDetector.disabled edit=$isEditMode tool=$selectedTool stylusOnly=$isStylusOnlyMode"
                        )
                        return@pointerInput
                    }

                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                        "vertical.rootDetector.enabled scrollLocked=$isScrollLocked edit=$isEditMode " +
                            "tool=$selectedTool pages=${layoutInfo.size} zoom=${cameraZoom}"
                    )

                    fun isOverPage(screenOffset: Offset): Boolean {
                        val zoom = cameraZoom.takeIf { it > 0f } ?: fitZoom
                        val docX = (screenOffset.x - cameraPanX) / zoom
                        val docY = (screenOffset.y - cameraPanY) / zoom
                        return layoutInfo.any { page ->
                            docX >= 0f &&
                                docX <= page.width &&
                                docY >= page.y &&
                                docY <= page.y + page.height
                        }
                    }

                    detectPdfTapAndOneHandZoomGestures(
                        viewConfiguration = viewConfiguration,
                        canStartOneHandZoom = { !isScrollLocked },
                        // Visible pages own ordinary double-tap zoom so the tap can be converted
                        // from page-local coordinates exactly once. The root still owns the
                        // double-tap-and-drag gesture, including starts in page gaps.
                        canHandleQuickDoubleTap = { false },
                        consumeSingleTap = false,
                        onTap = { offset ->
                            val overPage = isOverPage(offset)
                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                "vertical.rootTap offset=$offset overPage=$overPage"
                            )
                            if (!overPage) {
                                selectionClearTrigger++
                                currentOnPageClick()
                            }
                        },
                        onQuickDoubleTap = { offset ->
                            pdfZoomDiagnostic("vertical root quickDoubleTap ignored position=$offset")
                        },
                        onOneHandZoomHoldStart = { offset ->
                            if (!isScrollLocked) {
                                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                    "vertical.rootOneHandHoldStart offset=$offset"
                                )
                                currentOnDoubleTapDragZoomStart(offset)
                            }
                        },
                        onOneHandZoom = { offset, totalDragY ->
                            if (!isScrollLocked) {
                                currentOnDoubleTapDragZoom(offset, totalDragY)
                            }
                        },
                        onOneHandZoomEnd = { _ ->
                            if (!isScrollLocked || latestIsVerticalOneHandZooming) {
                                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d("vertical.rootOneHandEnd")
                                currentOnDoubleTapDragZoomEnd()
                            }
                        }
                    )
                }
                .pointerInput(
                    totalDocHeight,
                    isEditMode,
                    selectedTool,
                    isScrollLocked,
                    isStylusOnlyMode,
                    isHighlighterSnapEnabled
                ) {
                    val tracker = VelocityTracker()
                    val decay = splineBasedDecay<Float>(this)
                    val touchSlop = viewConfiguration.touchSlop

                    awaitEachGesture {
                        Timber.tag("PdfTouchDebug").v(
                            "VerticalReader: Gesture Loop Start. isEditMode=$isEditMode"
                        )

                        val down = awaitFirstDown(requireUnconsumed = false)
                        scrollTraceGestureId = nextPdfVerticalCameraEpoch(scrollTraceGestureId)
                        val traceGestureId = scrollTraceGestureId
                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                            "vertical.scrollDetector.down consumed=${down.isConsumed} pos=${down.position} " +
                                "zoom=${cameraZoom} pan=(${cameraPanX},${cameraPanY})"
                        )
                        isInteracting = true
                        isDragging = false
                        // Prepare the lightweight page-rendering path on ACTION_DOWN so its
                        // recomposition does not land on the first visible post-slop movement.
                        // Actual camera ownership and gesture consumption still wait for slop.
                        isScrollPreparing = !isEditMode ||
                            selectedTool == InkType.TEXT ||
                            (isStylusOnlyMode && down.type == PointerType.Touch)
                        val gestureStartNanos = PdfVerticalPerfLog.nowNanos()
                        var gestureLastSampleMs = System.currentTimeMillis()
                        var gestureEventCount = 0
                        var gestureConsumedEventCount = 0
                        var gestureCanceledEventCount = 0
                        var gestureZoomEventCount = 0
                        var gestureMaxPanDelta = 0f
                        var traceLastEventUptime = down.uptimeMillis
                        var tracePreviousEventUptime = down.uptimeMillis
                        PdfVerticalPerfLog.i(
                            "gesture-start type=${down.type} scrollLocked=$isScrollLocked edit=$isEditMode tool=$selectedTool " +
                                "zoom=${PdfVerticalPerfLog.f(cameraZoom)} highRes=${PdfVerticalPerfLog.f(highResScale)} " +
                                "pan=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)} currentPage=${state.currentPage} " +
                                "visible=${state.firstVisiblePage}-${state.lastVisiblePage}"
                        )

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

                        // User input must take ownership of the camera before its starting
                        // values are sampled. Launching this work allowed an in-flight fling
                        // to advance after the samples below, which made the document jump
                        // slightly when a finger interrupted deceleration.
                        val wasFlinging = verticalFlingJob?.isActive == true && isFlinging
                        val interruptedFlingVelocityX = if (wasFlinging) {
                            renderedFlingVelocity[0]
                        } else 0f
                        val interruptedFlingVelocityY = if (wasFlinging) {
                            renderedFlingVelocity[1]
                        } else 0f
                        PdfScrollTrace.d(
                            "g=$traceGestureId DOWN t=${down.uptimeMillis} pos=${PdfVerticalPerfLog.xy(down.position.x, down.position.y)} " +
                                "camera=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)} zoom=${PdfVerticalPerfLog.f(cameraZoom)} " +
                                "interruptActive=$wasFlinging interruptVelocity=${PdfVerticalPerfLog.xy(interruptedFlingVelocityX, interruptedFlingVelocityY)}"
                        )
                        verticalFlingJob?.cancel()
                        verticalFlingJob = null
                        isFlinging = false
                        cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                        val gestureCameraEpoch = cameraEpoch
                        val takeoverStartNanos = PdfVerticalPerfLog.nowNanos()
                        gestureCameraTakeoverJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            // Vertical motion is the primary path. Stop it first so a canceled
                            // decay cannot advance while the new gesture samples its origin.
                            panYAnimatable.stop()
                            panXAnimatable.stop()
                            zoomAnimatable.stop()
                            panXAnimatable.updateBounds(null, null)
                            panYAnimatable.updateBounds(null, null)
                            PdfScrollTrace.d(
                                "g=$traceGestureId TAKEOVER duration=${PdfVerticalPerfLog.elapsedMs(takeoverStartNanos)}ms " +
                                    "camera=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)} epoch=$gestureCameraEpoch"
                            )
                            pdfZoomDiagnostic(
                                "vertical scrollDetector camera takeover complete epoch=$gestureCameraEpoch " +
                                    "zoom=${cameraZoom} pan=(${cameraPanX},${cameraPanY})"
                            )
                        }

                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)

                        var accumulatedZoom = cameraZoom
                        var accumulatedPanX = cameraPanX
                        var accumulatedPanY = cameraPanY
                        val sampledTakeoverPanX = accumulatedPanX
                        val sampledTakeoverPanY = accumulatedPanY
                        var cameraTakeoverRebased = gestureCameraTakeoverJob?.isCompleted != false

                        var totalPanDistance = 0f
                        var netGesturePan = Offset.Zero
                        // A touch that arrests an active fling owns the scroll immediately.
                        // Requiring touch slop again discards the first part of every short,
                        // consecutive swipe and makes those swipes feel as if they do not add up.
                        var panLocked = wasFlinging
                        var gestureDisambiguationMode = if (wasFlinging) 1 else 0
                        var gestureZoomAccumulator = 1f

                        do {
                            val event = awaitPointerEvent()
                            gestureEventCount++
                            // Real pointer history, including the release sample, gives short
                            // flicks the same stable velocity estimation as standard scrollables.
                            event.changes.firstOrNull { it.id == down.id }?.let(tracker::addPointerInputChange)
                            val isMultiTouch = event.changes.size > 1
                            val canceled = event.changes.any { it.isConsumed } && !isMultiTouch

                            if (latestIsVerticalOneHandZooming && !isMultiTouch) {
                                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                                    "vertical.scrollDetector.skipOneHandActive events=$gestureEventCount " +
                                        "changes=${event.changes.joinToString { change ->
                                            "pressed=${change.pressed},consumed=${change.isConsumed},moved=${change.positionChanged()}"
                                        }}"
                                )
                                continue
                            }

                            if (canceled) {
                                gestureCanceledEventCount++
                                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                    "vertical.scrollDetector.canceledByConsumed mode=$gestureDisambiguationMode " +
                                        "events=$gestureEventCount changes=${event.changes.joinToString { change ->
                                            "pressed=${change.pressed},consumed=${change.isConsumed},moved=${change.positionChanged()}"
                                        }}"
                                )
                                Timber.tag("PdfTouchDebug").v(
                                    "VerticalReader: Event Canceled (Child consumed?)."
                                )
                            }

                            if (!canceled) {
                                if (!cameraTakeoverRebased && gestureCameraTakeoverJob?.isCompleted == true) {
                                    // If cancellation needed a dispatcher turn, preserve every
                                    // pointer delta but rebase it onto the decay's exact stop point.
                                    accumulatedPanX += cameraPanX - sampledTakeoverPanX
                                    accumulatedPanY += cameraPanY - sampledTakeoverPanY
                                    cameraTakeoverRebased = true
                                }
                                if (isEditMode && isMultiTouch) {
                                    drawingState.onDrawCancel()
                                }

                                val zoomChange = event.calculateZoom()
                                val rawPanChange = event.calculatePan()
                                val panChange = if (isScrollLocked && !isMultiTouch) Offset(0f, rawPanChange.y) else rawPanChange

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
                                netGesturePan += panChange
                                gestureMaxPanDelta = max(gestureMaxPanDelta, panMagnitude)
                                gestureZoomAccumulator *= zoomChange
                                if (abs(zoomChange - 1f) > 0.001f) {
                                    gestureZoomEventCount++
                                }

                                val isZoomPastSlop = abs(gestureZoomAccumulator - 1f) > 0.05f
                                val isPanPastSlop = netGesturePan.getDistance() > touchSlop
                                var justAcquiredPan = false

                                if (gestureDisambiguationMode == 0) {
                                    if (isPanPastSlop || isZoomPastSlop) {
                                        if (spanMagnitude > panMagnitude * 1.5f) {
                                            gestureDisambiguationMode = 2
                                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                "vertical.scrollDetector.modeZoom span=$spanMagnitude pan=$panMagnitude totalPan=$totalPanDistance"
                                            )
                                            Timber.tag("PdfTouchDebug").d(
                                                "Locked to ZOOM (Span > Pan * 1.5)"
                                            )
                                        } else {
                                            gestureDisambiguationMode = 1
                                            justAcquiredPan = true
                                            Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                                "vertical.scrollDetector.modePan span=$spanMagnitude pan=$panMagnitude totalPan=$totalPanDistance"
                                            )
                                            Timber.tag("PdfTouchDebug").d(
                                                "Locked to PAN (Pan Dominant)"
                                            )
                                        }
                                    }
                                } else if (gestureDisambiguationMode == 1) {
                                    if (spanMagnitude > (panMagnitude * 3f) && spanMagnitude > 4f) {
                                        gestureDisambiguationMode = 2
                                        Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(
                                            "vertical.scrollDetector.modePanToZoom span=$spanMagnitude pan=$panMagnitude"
                                        )
                                        Timber.tag("PdfTouchDebug").d(
                                            "Breakout: Switching PAN -> ZOOM"
                                        )
                                    }
                                }

                                val isTouchInput = event.changes.all { it.type == PointerType.Touch }
                                val shouldScroll = (panLocked || gestureDisambiguationMode != 0) &&
                                        (!isEditMode || selectedTool == InkType.TEXT || isMultiTouch || (isStylusOnlyMode && isTouchInput))
                                var appliedPanForTrace = Offset.Zero

                                if (shouldScroll) {
                                    isScrollPreparing = false
                                    panLocked = true
                                    isDragging = true
                                    val appliedPanChange = if (justAcquiredPan) {
                                        pdfPanAfterTouchSlop(netGesturePan, touchSlop)
                                    } else {
                                        panChange
                                    }
                                    appliedPanForTrace = appliedPanChange
                                    if (zoomChange != 1f || appliedPanChange != Offset.Zero) {

                                        var effectiveZoomChange = zoomChange
                                        if (gestureDisambiguationMode == 1) effectiveZoomChange = 1f

                                        val oldZoom = accumulatedZoom
                                        val rawTargetZoom = oldZoom * effectiveZoomChange
                                        val constrainedZoom = rawTargetZoom.coerceIn(fitZoom, PDF_MAX_ZOOM_SCALE)

                                        val prevCentroid = centroid - appliedPanChange
                                        val contentPivotX = (prevCentroid.x - accumulatedPanX) / oldZoom
                                        val contentPivotY = (prevCentroid.y - accumulatedPanY) / oldZoom

                                        val rawNewPanX = centroid.x - (contentPivotX * constrainedZoom)
                                        val rawNewPanY = centroid.y - (contentPivotY * constrainedZoom)

                                        val (finalZoom, finalX, finalY) = clampCamera(
                                            constrainedZoom, rawNewPanX, rawNewPanY
                                        )

                                        accumulatedZoom = finalZoom
                                        accumulatedPanX = finalX
                                        accumulatedPanY = finalY

                                        if (accumulatedZoom != cameraZoom) {
                                            onZoomChange(accumulatedZoom)
                                        }

                                        // Direct manipulation has one authoritative state: the
                                        // state rendered below. Do not enqueue finger samples behind
                                        // the UI dispatcher; applying them here preserves every
                                        // delta and removes the release-time catch-up correction.
                                        commitRenderedCamera(
                                            accumulatedZoom,
                                            accumulatedPanX,
                                            accumulatedPanY,
                                        )

                                        val consumedChanges = event.changes.count { it.positionChanged() }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                        if (consumedChanges > 0) {
                                            gestureConsumedEventCount++
                                        }

                                        val nowMs = System.currentTimeMillis()
                                        if (nowMs - gestureLastSampleMs >= PdfVerticalPerfLog.SAMPLE_INTERVAL_MS) {
                                            gestureLastSampleMs = nowMs
                                            PdfVerticalPerfLog.d(
                                                "gesture-drag-sample events=$gestureEventCount consumed=$gestureConsumedEventCount " +
                                                    "mode=$gestureDisambiguationMode multi=$isMultiTouch panDelta=${PdfVerticalPerfLog.f(panMagnitude)} " +
                                                    "totalPan=${PdfVerticalPerfLog.f(totalPanDistance)} zoomChange=${PdfVerticalPerfLog.f(zoomChange)} " +
                                                    "zoom=${PdfVerticalPerfLog.f(accumulatedZoom)} pan=${PdfVerticalPerfLog.xy(accumulatedPanX, accumulatedPanY)} " +
                                                    "highRes=${PdfVerticalPerfLog.f(highResScale)}"
                                            )
                                        }
                                    }
                                }
                                val traceChange = event.changes.firstOrNull { it.id == down.id }
                                val traceNow = traceChange?.uptimeMillis ?: traceLastEventUptime
                                val traceEventDeltaMs = traceNow - tracePreviousEventUptime
                                tracePreviousEventUptime = traceNow
                                if (
                                    traceNow - traceLastEventUptime >= PdfScrollTrace.FRAME_SAMPLE_INTERVAL_MS ||
                                    traceChange?.pressed == false
                                ) {
                                    traceLastEventUptime = traceNow
                                    PdfScrollTrace.d(
                                        "g=$traceGestureId MOVE t=$traceNow pressed=${traceChange?.pressed} events=$gestureEventCount " +
                                            "rawPan=${PdfVerticalPerfLog.xy(panChange.x, panChange.y)} applied=${PdfVerticalPerfLog.xy(appliedPanForTrace.x, appliedPanForTrace.y)} " +
                                            "eventDt=${traceEventDeltaMs}ms history=${traceChange?.historical?.size ?: 0} " +
                                            "net=${PdfVerticalPerfLog.xy(netGesturePan.x, netGesturePan.y)} mode=$gestureDisambiguationMode locked=$panLocked " +
                                            "takeoverDone=$cameraTakeoverRebased target=${PdfVerticalPerfLog.xy(accumulatedPanX, accumulatedPanY)} " +
                                            "actual=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
                                    )
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        val gestureDurationMs = PdfVerticalPerfLog.elapsedMs(gestureStartNanos)
                        isScrollPreparing = false

                        if (!cameraTakeoverRebased && gestureCameraTakeoverJob?.isCompleted == true) {
                            accumulatedPanX += cameraPanX - sampledTakeoverPanX
                            accumulatedPanY += cameraPanY - sampledTakeoverPanY
                            cameraTakeoverRebased = true
                        }

                        if (panLocked) {
                            val velocity = tracker.calculateVelocity()
                            val minFlingVelocity = viewConfiguration.minimumFlingVelocity
                            val (finalZoom, finalX, finalY) = clampCamera(
                                accumulatedZoom, accumulatedPanX, accumulatedPanY
                            )
                            // The rendered camera is already at the exact pointer position. Keep the
                            // Animatables synchronized as backing state without a visible correction.
                            cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                            commitRenderedCamera(finalZoom, finalX, finalY)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                zoomAnimatable.snapTo(finalZoom)
                                panXAnimatable.snapTo(finalX)
                                panYAnimatable.snapTo(finalY)
                            }
                            if (isInteracting) {
                                Timber.tag("PdfTouchDebug").v("VerticalReader: Interaction ended")
                                isInteracting = false
                            }
                            isDragging = false
                            val zoomedDocWidth = screenWidth * finalZoom
                            val zoomedDocHeight = totalDocHeight * finalZoom

                            val flingMinX: Float
                            val flingMaxX: Float
                            if (zoomedDocWidth < screenWidth) {
                                val centeredX = (screenWidth - zoomedDocWidth) / 2f
                                flingMinX = centeredX
                                flingMaxX = centeredX
                            } else {
                                flingMinX = -(zoomedDocWidth - screenWidth)
                                flingMaxX = 0f
                            }

                            val minPanY =
                                (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(
                                    headerHeightPx
                                )
                            val resolvedFling = resolvePdfFlingVelocity(
                                rawX = velocity.x,
                                rawY = velocity.y,
                                displacementX = netGesturePan.x,
                                displacementY = netGesturePan.y,
                                minimumVelocity = minFlingVelocity,
                                maximumVelocity = viewConfiguration.maximumFlingVelocity,
                                allowHorizontal = !isScrollLocked,
                            )
                            val flingX = resolvedFling.x
                            val flingY = resolvedFling.y
                            val shouldRunFling = flingX != 0f || flingY != 0f ||
                                accumulatedZoom !in fitZoom..PDF_MAX_ZOOM_SCALE
                            PdfScrollTrace.d(
                                "g=$traceGestureId UP duration=${gestureDurationMs}ms events=$gestureEventCount " +
                                    "path=${PdfVerticalPerfLog.f(totalPanDistance)} net=${PdfVerticalPerfLog.xy(netGesturePan.x, netGesturePan.y)} " +
                                    "releaseVelocity=${PdfVerticalPerfLog.xy(velocity.x, velocity.y)} interrupted=${PdfVerticalPerfLog.xy(interruptedFlingVelocityX, interruptedFlingVelocityY)} " +
                                    "fling=${PdfVerticalPerfLog.xy(flingX, flingY)} min=${PdfVerticalPerfLog.f(minFlingVelocity)} " +
                                    "final=${PdfVerticalPerfLog.xy(finalX, finalY)} actual=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
                            )
                            PdfVerticalPerfLog.i(
                                "gesture-end duration=${gestureDurationMs}ms events=$gestureEventCount consumed=$gestureConsumedEventCount " +
                                    "canceled=$gestureCanceledEventCount zoomEvents=$gestureZoomEventCount maxPanDelta=${PdfVerticalPerfLog.f(gestureMaxPanDelta)} " +
                                    "totalPan=${PdfVerticalPerfLog.f(totalPanDistance)} mode=$gestureDisambiguationMode panLocked=$panLocked shouldRunFling=$shouldRunFling " +
                                    "velocity=${PdfVerticalPerfLog.xy(velocity.x, velocity.y)} fling=${PdfVerticalPerfLog.xy(flingX, flingY)} " +
                                    "zoom=${PdfVerticalPerfLog.f(finalZoom)} pan=${PdfVerticalPerfLog.xy(finalX, finalY)}"
                            )

                            if (shouldRunFling) {
                                // Publish ownership before launching so idle effects cannot run in
                                // the release-to-fling scheduling gap.
                                isFlinging = true
                                val flingCameraEpoch = cameraEpoch
                                verticalFlingJob = scope.launch {
                                    val flingStartNanos = PdfVerticalPerfLog.nowNanos()
                                    var traceLastFlingMs = 0L
                                    PdfVerticalPerfLog.i(
                                        "fling-start fling=${PdfVerticalPerfLog.xy(flingX, flingY)} " +
                                            "boundsX=${PdfVerticalPerfLog.xy(flingMinX, flingMaxX)} boundsY=${PdfVerticalPerfLog.xy(minPanY, headerHeightPx)} " +
                                            "zoomedDocH=${PdfVerticalPerfLog.f(zoomedDocHeight)} highRes=${PdfVerticalPerfLog.f(highResScale)}"
                                    )
                                    try {
                                        // Start decay from the exact final drag position. A pending
                                        // camera update must never land after the fling has begun.
                                        zoomAnimatable.snapTo(finalZoom)
                                        panXAnimatable.snapTo(finalX)
                                        panYAnimatable.snapTo(finalY)
                                        if (accumulatedZoom !in fitZoom..PDF_MAX_ZOOM_SCALE) {
                                            zoomAnimatable.animateTo(
                                                finalZoom, animationSpec = tween(300)
                                            )
                                        }
                                        onZoomChange(zoomAnimatable.targetValue)
                                        Timber.tag(SCROLL_BOUNDS_TAG).i("Fling Logic:")
                                        Timber.tag(SCROLL_BOUNDS_TAG)
                                            .d("- totalDocHeight: $totalDocHeight, zoom: $finalZoom -> zoomedDocHeight: $zoomedDocHeight")
                                        Timber.tag(SCROLL_BOUNDS_TAG)
                                            .d("- Fling bounds set to Y:[$minPanY, $headerHeightPx]")
                                        // Android scroll physics decays each axis independently.
                                        // Local drivers feed their frame values straight into the
                                        // rendered camera, so there is no camera queue or mirror.
                                        // Keeping the axes independent also prevents an X velocity
                                        // clamped at fit zoom from shortening/canceling the Y fling.
                                        coroutineScope {
                                            if (flingX != 0f) {
                                                launch {
                                                    Animatable(finalX).apply {
                                                        updateBounds(flingMinX, flingMaxX)
                                                        animateDecay(flingX, decay) {
                                                            renderedFlingVelocity[0] = this.velocity
                                                            commitRenderedCamera(
                                                                finalZoom,
                                                                value,
                                                                cameraPanY,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (flingY != 0f) {
                                                launch {
                                                    Animatable(finalY).apply {
                                                        updateBounds(minPanY, headerHeightPx)
                                                        animateDecay(flingY, decay) {
                                                            renderedFlingVelocity[1] = this.velocity
                                                            commitRenderedCamera(
                                                                finalZoom,
                                                                cameraPanX,
                                                                value,
                                                            )
                                                            val elapsed = PdfVerticalPerfLog.elapsedMs(
                                                                flingStartNanos
                                                            )
                                                            if (elapsed - traceLastFlingMs >= PdfScrollTrace.FRAME_SAMPLE_INTERVAL_MS) {
                                                                traceLastFlingMs = elapsed
                                                                PdfScrollTrace.d(
                                                                    "g=$traceGestureId FLING t=${elapsed}ms " +
                                                                        "y=${PdfVerticalPerfLog.f(value)} " +
                                                                        "velocity=${PdfVerticalPerfLog.f(this.velocity)} " +
                                                                        "bounds=${PdfVerticalPerfLog.xy(minPanY, headerHeightPx)}"
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Animatables are retained only as programmatic-animation
                                        // drivers. Synchronize them after direct decay, never between
                                        // a fling frame and the rendered camera.
                                        panXAnimatable.updateBounds(null, null)
                                        panYAnimatable.updateBounds(null, null)
                                        panXAnimatable.snapTo(cameraPanX)
                                        panYAnimatable.snapTo(cameraPanY)
                                    } finally {
                                        val endingVelocityX = renderedFlingVelocity[0]
                                        val endingVelocityY = renderedFlingVelocity[1]
                                        PdfVerticalPerfLog.i(
                                            "fling-end duration=${PdfVerticalPerfLog.elapsedMs(flingStartNanos)}ms " +
                                                "zoom=${PdfVerticalPerfLog.f(cameraZoom)} pan=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)} " +
                                                "velocity=${PdfVerticalPerfLog.xy(endingVelocityX, endingVelocityY)}"
                                        )
                                        // A newer gesture/fling owns this flag after advancing the
                                        // camera epoch; an older canceled decay must not clear it.
                                        if (cameraEpoch == flingCameraEpoch) {
                                            isFlinging = false
                                        }
                                        PdfScrollTrace.d(
                                            "g=$traceGestureId END duration=${PdfVerticalPerfLog.elapsedMs(flingStartNanos)}ms " +
                                                "camera=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)} " +
                                                "velocity=${PdfVerticalPerfLog.xy(endingVelocityX, endingVelocityY)} " +
                                                "stillOwner=${cameraEpoch == flingCameraEpoch}"
                                        )
                                        renderedFlingVelocity[0] = 0f
                                        renderedFlingVelocity[1] = 0f
                                    }
                                }
                            } else {
                                panXAnimatable.updateBounds(flingMinX, flingMaxX)
                                panYAnimatable.updateBounds(minPanY, headerHeightPx)
                            }
                        } else {
                            cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                            PdfScrollTrace.d(
                                "g=$traceGestureId UP_NO_SCROLL duration=${gestureDurationMs}ms events=$gestureEventCount " +
                                    "path=${PdfVerticalPerfLog.f(totalPanDistance)} net=${PdfVerticalPerfLog.xy(netGesturePan.x, netGesturePan.y)} " +
                                    "mode=$gestureDisambiguationMode canceled=$gestureCanceledEventCount"
                            )
                            if (isInteracting) {
                                Timber.tag("PdfTouchDebug").v("VerticalReader: Interaction ended")
                                isInteracting = false
                            }
                            isDragging = false
                            PdfVerticalPerfLog.i(
                                "gesture-end duration=${gestureDurationMs}ms events=$gestureEventCount consumed=$gestureConsumedEventCount " +
                                    "canceled=$gestureCanceledEventCount zoomEvents=$gestureZoomEventCount maxPanDelta=${PdfVerticalPerfLog.f(gestureMaxPanDelta)} " +
                                    "totalPan=${PdfVerticalPerfLog.f(totalPanDistance)} mode=$gestureDisambiguationMode panLocked=false no-fling " +
                                    "zoom=${PdfVerticalPerfLog.f(accumulatedZoom)} pan=${PdfVerticalPerfLog.xy(accumulatedPanX, accumulatedPanY)}"
                            )
                        }
                    }
                }) {
            val cachedVisiblePages = remember { mutableStateOf<List<PdfPageLayout>>(emptyList()) }
            val visiblePages by remember(layoutInfo, screenHeight, textBoxes, draggingBoxId) {
                derivedStateOf {
                    val zoom = cameraZoom
                    val panY = cameraPanY

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
                    val layoutMatches = cached.size == finalPages.size && cached.indices.all {
                        val cachedPage = cached[it]
                        val newPage = finalPages[it]
                        cachedPage.index == newPage.index &&
                            cachedPage.yPx == newPage.yPx &&
                            cachedPage.heightPx == newPage.heightPx &&
                            cachedPage.widthPx == newPage.widthPx
                    }

                    if (!layoutMatches) {
                        cachedVisiblePages.value = finalPages
                        Timber.tag("PdfDrawPerf").d(
                            "Vertical Visible Pages Changed: ${finalPages.map { it.index }} (Dragging: ${draggedBox != null})"
                        )
                        PdfVerticalPerfLog.d(
                            "visible-pages pages=${finalPages.map { it.index }} base=${baseVisiblePages.map { it.index }} " +
                                "draggingBox=${draggedBox != null} zoom=${PdfVerticalPerfLog.f(zoom)} panY=${PdfVerticalPerfLog.f(panY)} " +
                                "viewport=${PdfVerticalPerfLog.xy(viewportTop, viewportBottom)} buffered=${PdfVerticalPerfLog.xy(searchTop, searchBottom)}"
                        )
                        finalPages
                    } else {
                        cached
                    }
                }
            }

            LaunchedEffect(visiblePages, screenHeight, isResizing) {
                snapshotFlow {
                    Pair(cameraPanY, cameraZoom)
                }.collectLatest { (panY, zoom) ->
                    if (!isResizing && visiblePages.isNotEmpty()) {
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
                            Timber.tag("PdfPositionDebug").v("VerticalReader: Page changed to ${mostVisible.index} (PanY: $panY)")
                            PdfVerticalPerfLog.d(
                                "current-page-change from=${state.currentPage} to=${mostVisible.index} " +
                                    "viewport=${PdfVerticalPerfLog.xy(realViewportTop, realViewportBottom)} panY=${PdfVerticalPerfLog.f(panY)} zoom=${PdfVerticalPerfLog.f(zoom)}"
                            )
                            state.currentPage = mostVisible.index
                        }
                    }
                }
            }

            Layout(
                content = {
                    visiblePages.forEach { page ->
                        key(documentKey, page.index) {
                            val isBookmarked by remember(bookmarkSet, page.index) {
                                derivedStateOf {
                                    bookmarkSet.any { it.pageIndex == page.index }
                                }
                            }

                            val visibleScreenRectLambda = remember(page, screenWidth, screenHeight) {
                                {
                                    val zoom = cameraZoom
                                    val panX = cameraPanX
                                    val panY = cameraPanY

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
                                { point: PdfPoint, isEraserOverride: Boolean ->
                                    onDrawStart(page.index, point, isEraserOverride)
                                }
                            }

                            val currentOnDraw by rememberUpdatedState(onDraw)
                            val onDrawLambda = remember(page.index) {
                                { point: PdfPoint, isEraserOverride: Boolean ->
                                    currentOnDraw(page.index, point, isEraserOverride)
                                }
                            }

                            val onSingleTapLambda = remember(onPageClick) {
                                { _: Offset? ->
                                    selectionClearTrigger++
                                    onPageClick()
                                }
                            }

                            val onTranslateTextLambda = remember(onTranslateText) {
                                { text: String -> onTranslateText(text) }
                            }

                            val onSearchTextLambda = remember(onSearchText) {
                                { text: String -> onSearchText(text) }
                            }

                            val onTtsHighlightCenter: (Float) -> Unit =
                                remember(page.index, ttsReadingPage) {
                                    { highlightCenterY ->
                                        if (page.index == ttsReadingPage && !isInteracting) {
                                            val currentZ = cameraZoom
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
                                            val currentZ = cameraZoom
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
                            ) {
                                PdfPageComposable(
                                    pdfDocument = pdfDocument,
                                    documentKey = documentKey,
                                    pageIndex = page.index,
                                    virtualPage = virtualPage,
                                    totalPages = totalPages,
                                    activeTheme = activeTheme,
                                    activeTextureAlpha = activeTextureAlpha,
                                    excludeImages = excludeImages,
                                    reverseColorMode = effectiveReverseColorMode,
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
                                    onTranslateText = onTranslateTextLambda,
                                    onSearchText = onSearchTextLambda,
                                    ttsHighlightData = pageTtsData,
                                    onLinkClicked = onLinkClicked,
                                    onInternalLinkClicked = onInternalLinkClicked,
                                    isBookmarked = isBookmarked,
                                    onOcrStateChange = onOcrStateChange,
                                    onBookmarkClick = { onBookmarkClick(page.index) },
                                    isZoomEnabled = false,
                                    isScrolling = isDragging ||
                                        isFlinging ||
                                        isScrollPreparing ||
                                        isTileRenderIdleCooldownActive,
                                    isVerticalScroll = true,
                                    showPageNumberOverlay = showPageNumberOverlay,
                                    isScrollLocked = isScrollLocked,
                                    visualScaleProvider = currentScaleProvider,
                                    onDoubleTap = currentOnDoubleTapToZoom,
                                    onDoubleTapDragZoomStart = currentOnDoubleTapDragZoomStart,
                                    onDoubleTapDragZoom = currentOnDoubleTapDragZoom,
                                    onDoubleTapDragZoomEnd = currentOnDoubleTapDragZoomEnd,
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
                                    stylusButtonHovering = stylusButtonHovering,
                                    isAutoScrollPlaying = isAutoScrollPlaying,
                                    textBoxes = effectiveTextBoxesByPage[page.index].orEmpty(),
                                    selectedTextBoxId = selectedTextBoxId,
                                    onTextBoxChange = onTextBoxChange,
                                    onTextBoxSelect = onTextBoxSelect,
                                    userHighlights = effectiveUserHighlightsByPage[page.index].orEmpty(),
                                    onHighlightAdd = onHighlightAdd,
                                    onHighlightUpdate = onHighlightUpdate,
                                    onHighlightDelete = onHighlightDelete,
                                    onNoteRequested = onNoteRequested,
                                    onTts = onTts,
                                    activeToolThickness = activeToolThickness,
                                    eraserToolThickness = eraserToolThickness,
                                    customHighlightColors = customHighlightColors,
                                    onPaletteClick = onPaletteClick,
                                    onTextBoxDragStart = { box, localTopLeft, touchOffset ->
                                        val currentZoom = cameraZoom
                                        val panX = cameraPanX
                                        val panY = cameraPanY
                                        val pageScreenY = page.y * currentZoom + panY

                                        val boxScreenX = panX + (localTopLeft.x * currentZoom)
                                        val boxScreenY = pageScreenY + (localTopLeft.y * currentZoom)

                                        draggingBoxSize = Size(
                                            box.relativeBounds.width * page.width,
                                            box.relativeBounds.height * page.height
                                        )
                                        draggingBoxPageHeight = page.height

                                        draggingBoxOffset = Offset(boxScreenX, boxScreenY)
                                        draggingBoxTouchDelta = touchOffset * currentZoom
                                        draggingBoxId = box.id
                                    },
                                    onTextBoxDrag = { dragDelta ->
                                        val currentZoom = cameraZoom
                                        val scaledDelta = dragDelta * currentZoom
                                        Timber.tag("PdfTextBoxDebug").v("VerticalReader onTextBoxDrag dragDelta=$dragDelta zoom=$currentZoom scaledDelta=$scaledDelta")
                                        draggingBoxOffset += scaledDelta
                                    },
                                    onTextBoxDragEnd = {
                                        scope.launch {
                                            val boxCenterY =
                                                draggingBoxOffset.y + draggingBoxSize.height / 2f
                                            val currentZoom = cameraZoom
                                            val panY = cameraPanY
                                            val docY = (boxCenterY - panY) / currentZoom

                                            val targetPage = layoutInfo.minByOrNull {
                                                val pageCenter = it.y + (it.height / 2f)
                                                abs(docY - pageCenter)
                                            }

                                            if (targetPage != null && draggingBoxId != null) {
                                                val panX = cameraPanX
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
                                                    draggingBoxSize.width / targetPage.width
                                                val relH =
                                                    draggingBoxSize.height / targetPage.height

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
                                    draggingBoxId = draggingBoxId,
                                    isBubbleZoomModeActive = isBubbleZoomModeActive,
                                    onDetectBubbles = onDetectBubbles
                                )
                            }

                            if (page.index < totalPages - 1 && dividerHeightPxInt > 0) {
                                val dividerYPx = page.yPx + page.heightPx
                                Box(
                                    modifier = Modifier
                                        .layoutId(
                                            DividerLayout(
                                                yPx = dividerYPx,
                                                widthPx = page.widthPx,
                                                heightPx = dividerHeightPxInt
                                            )
                                        )
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                        ))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val z = cameraZoom

                        scaleX = z
                        scaleY = z
                        translationX = cameraPanX
                        translationY = cameraPanY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
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
                                        id.widthPx, id.heightPx
                                    )
                                )
                                placeable.place(0, id.yPx)
                            }

                            is DividerLayout -> {
                                val placeable = measurable.measure(
                                    Constraints.fixed(
                                        id.widthPx, id.heightPx
                                    )
                                )
                                placeable.place(0, id.yPx)
                            }
                        }
                    }
                }
                val layoutTime = (System.nanoTime() - layoutStart) / 1_000_000f
                if (layoutTime > 2f) {
                    Timber.tag("PdfPerformance").d(
                        "VerticalReader Layout Measure/Place took ${layoutTime}ms for ${measurables.size} items"
                    )
                    PdfVerticalPerfLog.d(
                        "compose-layout-slow duration=${PdfVerticalPerfLog.f(layoutTime)}ms items=${measurables.size} " +
                            "visible=${visiblePages.map { it.index }} zoom=${PdfVerticalPerfLog.f(cameraZoom)} " +
                            "pan=${PdfVerticalPerfLog.xy(cameraPanX, cameraPanY)}"
                    )
                }
                measureResult
            }
        }

        var isDraggingScrollbar by remember { mutableStateOf(false) }
        var scrollbarVisible by remember { mutableStateOf(false) }
        var lastScrollInteraction by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            var previousValue = cameraPanY
            snapshotFlow { cameraPanY }.collect { newValue ->
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
        val safeCurrentPage = if (totalPages > 0) state.currentPage.coerceIn(0, totalPages - 1) else 0

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

            Box(modifier = Modifier
                // Read the high-frequency camera state in the placement phase. Reading panY
                // while composing this parent previously invalidated the whole reader for every
                // scroll pixel, even though the document itself uses a graphics layer.
                .offset {
                    val zoomedHeight = totalDocHeight * cameraZoom
                    val minimumY = (
                        screenHeight - footerHeightPx - zoomedHeight
                    ).coerceAtMost(headerHeightPx)
                    val range = headerHeightPx - minimumY
                    val progress = if (abs(range) < 1f) {
                        0f
                    } else {
                        ((headerHeightPx - cameraPanY) / abs(range)).coerceIn(0f, 1f)
                    }
                    IntOffset(
                        x = 0,
                        y = (availableSpace * progress).coerceIn(0f, availableSpace).toInt(),
                    )
                }
                .align(Alignment.TopEnd)
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .padding(end = 4.dp)
                .alpha(scrollbarAlpha)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = isDraggingScrollbar && totalPages > 0,
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
                                text = "${safeCurrentPage + 1}/$totalPages",
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
                                        verticalFlingJob?.cancel()
                                        verticalFlingJob = null
                                        cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            panYAnimatable.stop()
                                        }
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

                                                val currentZoom = cameraZoom
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
                                                    (cameraPanY - scrollDelta)

                                                // FORCE CLAMP: Ensure we never set
                                                // a value outside bounds
                                                // This prevents the "Gap" where the
                                                // scrollbar pushes past the footer
                                                val clampedTargetPanY = rawTargetPanY.coerceIn(
                                                    minS, maxS
                                                )

                                                cameraEpoch = nextPdfVerticalCameraEpoch(cameraEpoch)
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    panYAnimatable.stop()
                                                    panYAnimatable.snapTo(clampedTargetPanY)
                                                }
                                                commitRenderedCamera(
                                                    cameraZoom,
                                                    cameraPanX,
                                                    clampedTargetPanY,
                                                )

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

        val eraserPosition = globalEraserPosition
        if (isEditMode && (selectedTool == InkType.ERASER || isStylusEraserOverride) && eraserPosition != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val eraserStrokeWidth = resolveEraserStrokeWidth(
                    isStylusEraserOverride,
                    activeToolThickness,
                    eraserToolThickness
                )
                val radiusPx = if (eraserStrokeWidth > 0f) {
                    eraserStrokeWidth * screenWidth * cameraZoom
                } else {
                    8.dp.toPx()
                }

                drawCircle(color = Color.White.copy(alpha = 0.3f), radius = radiusPx, center = eraserPosition)

                drawCircle(
                    color = Color.Black,
                    radius = radiusPx,
                    center = eraserPosition,
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

                val currentZoom = cameraZoom
                val boxBottomY = draggingBoxOffset.y + (draggingBoxSize.height * currentZoom)
                val spaceBelow = screenHeight - boxBottomY
                val overlayHandlePos =
                    if (spaceBelow < with(density) { 60.dp.toPx() }) HandlePosition.TOP else HandlePosition.BOTTOM

                Box(modifier = Modifier
                    .offset {
                        IntOffset(
                            draggingBoxOffset.x.roundToInt(), draggingBoxOffset.y.roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = currentZoom
                        scaleY = currentZoom
                        transformOrigin = TransformOrigin(0f, 0f)
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
                        scale = currentZoom,
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
