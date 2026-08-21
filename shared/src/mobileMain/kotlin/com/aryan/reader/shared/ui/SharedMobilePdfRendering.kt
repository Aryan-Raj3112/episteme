package com.aryan.reader.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.sharedPdfIsInkDownAllowed
import com.aryan.reader.shared.pdf.sharedPdfIsEraserOverride
import com.aryan.reader.shared.sharedPdfStylusBarrelPressed
import com.aryan.reader.shared.pdf.pdfAutoScrollPixelsPerSecond
import com.aryan.reader.shared.pdf.PdfAutoScrollProfile
import com.aryan.reader.shared.pdf.PdfMusicianHoldDurationMillis
import com.aryan.reader.shared.pdf.planPdfMusicianGesture
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.pdfPaginationEdgeTarget
import com.aryan.reader.shared.pdf.centeredPdfPageScrollOffset
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.PdfZoomCamera
import com.aryan.reader.shared.pdf.PdfZoomPoint
import com.aryan.reader.shared.pdf.PdfZoomSize
import com.aryan.reader.shared.pdf.isZoomed
import com.aryan.reader.shared.pdf.pdfDoubleTapTargetScale
import com.aryan.reader.shared.pdf.pdfVerticalDoubleTapTargetScale
import com.aryan.reader.shared.pdf.pdfZoomIndicatorPercent
import com.aryan.reader.shared.pdf.PDF_MAX_ZOOM_SCALE
import com.aryan.reader.shared.pdf.visiblePdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextDragState
import com.aryan.reader.shared.pdf.sharedPdfTextDropBounds
import com.aryan.reader.shared.pdf.containsNormalizedPoint
import com.aryan.reader.shared.pdf.withBounds
import com.aryan.reader.shared.pdf.withText
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfVirtualPage
import com.aryan.reader.shared.pdf.SharedPdfBlankPageInsertion
import com.aryan.reader.shared.pdf.sharedPdfPdfPageIndexAt
import com.aryan.reader.shared.pdf.sharedPdfTextFontSizePx
import com.aryan.reader.shared.pdf.sharedPdfTextPageRelativeFontSize
import com.aryan.reader.shared.pdf.sharedPdfNearestPdfPageIndex
import com.aryan.reader.shared.pdf.sharedPdfHighlightAllColors
import com.aryan.reader.shared.pdf.sharedPdfMergeRectsIntoLines
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt
import kotlin.time.TimeSource

@Composable
internal fun sharedMobilePdfViewerBackground(theme: ReaderTheme, displayMode: PdfDisplayMode): Color {
    return when (theme.id) {
        "no_theme", "system" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) MaterialTheme.colorScheme.surfaceContainer else Color.Black
        "reverse" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.Black else Color.White
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

internal fun sharedMobilePdfPageBackground(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.White
        "reverse" -> Color.Black
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

internal fun sharedMobilePdfPageTextColor(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.Black
        "reverse" -> Color.White
        else -> theme.textColor.takeIf { it.isSpecified } ?: Color.Black
    }
}

internal fun sharedMobilePdfColorFilter(
    theme: ReaderTheme,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
): ColorFilter? {
    if (theme.id == "no_theme" || theme.id == "system") return null
    // Nonlinear Okular modes are applied once by the platform bitmap renderer.
    if (theme.id == "reverse" && reverseColorMode != PdfReverseColorMode.RGB) return null
    val matrix = if (theme.id == "reverse") {
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    } else {
        val background = sharedMobilePdfPageBackground(theme)
        val foreground = sharedMobilePdfPageTextColor(theme)
        val backgroundRed = background.red * 255f
        val backgroundGreen = background.green * 255f
        val backgroundBlue = background.blue * 255f
        val foregroundRed = foreground.red * 255f
        val foregroundGreen = foreground.green * 255f
        val foregroundBlue = foreground.blue * 255f
        val deltaRed = (backgroundRed - foregroundRed) / 255f
        val deltaGreen = (backgroundGreen - foregroundGreen) / 255f
        val deltaBlue = (backgroundBlue - foregroundBlue) / 255f
        floatArrayOf(
            deltaRed * 0.2126f, deltaRed * 0.7152f, deltaRed * 0.0722f, 0f, foregroundRed,
            deltaGreen * 0.2126f, deltaGreen * 0.7152f, deltaGreen * 0.0722f, 0f, foregroundGreen,
            deltaBlue * 0.2126f, deltaBlue * 0.7152f, deltaBlue * 0.0722f, 0f, foregroundBlue,
            0f, 0f, 0f, 1f, 0f
        )
    }
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
internal fun SharedMobilePdfZoomViewport(
    camera: PdfZoomCamera,
    onCameraChanged: (PdfZoomCamera) -> Unit,
    zoomEnabled: Boolean,
    tapGesturesEnabled: Boolean = true,
    maxScale: Float,
    verticalDocumentMode: Boolean = false,
    onSingleTap: (Offset) -> Unit = {},
    onZoomChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PdfZoomCamera) -> Unit
) {
    val latestCamera by rememberUpdatedState(camera)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val oneHandZoomDistancePx = with(LocalDensity.current) { 240.dp.toPx() }
    val scope = rememberCoroutineScope()
    var cameraAnimationJob by remember { mutableStateOf<Job?>(null) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    val zoomPercentage = pdfZoomIndicatorPercent(camera.scale)

    LaunchedEffect(zoomPercentage) {
        showZoomIndicator = camera.isZoomed()
        if (showZoomIndicator) {
            delay(1500)
            showZoomIndicator = false
        }
    }

    fun updateCamera(next: PdfZoomCamera) {
        recordSharedPdfCameraUpdate()
        onCameraChanged(next)
        onZoomChanged(next.scale)
    }

    SharedMobilePdfOrientationZoomResetEffect(viewport, verticalDocumentMode) { updateCamera(PdfZoomCamera()) }

    fun animateCameraTo(target: PdfZoomCamera, durationMillis: Int) {
        cameraAnimationJob?.cancel()
        val start = latestCamera
        cameraAnimationJob = scope.launch {
            try {
                Animatable(0f).animateTo(
                    1f,
                    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
                ) {
                    val progress = value
                    updateCamera(
                        PdfZoomCamera(
                            scale = start.scale + (target.scale - start.scale) * progress,
                            offset = PdfZoomPoint(
                                start.offset.x + (target.offset.x - start.offset.x) * progress,
                                start.offset.y + (target.offset.y - start.offset.y) * progress
                            )
                        )
                    )
                }
                updateCamera(target)
            } finally {
                cameraAnimationJob = null
            }
        }
    }

    fun flingCamera(velocityX: Float, velocityY: Float) {
        val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        if (speed < 600f || viewport.width <= 0 || viewport.height <= 0) return
        recordSharedPdfFling()
        cameraAnimationJob?.cancel()
        val start = latestCamera
        val directionX = velocityX / speed
        val directionY = velocityY / speed
        val viewportSize = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat())
        cameraAnimationJob = scope.launch {
            try {
                Animatable(0f).animateDecay(
                    initialVelocity = speed * 0.72f,
                    animationSpec = exponentialDecay(frictionMultiplier = 2f)
                ) {
                    updateCamera(
                        PdfZoomCamera(
                            scale = start.scale,
                            offset = PdfZoomPoint(
                                start.offset.x + value * directionX,
                                start.offset.y + value * directionY
                            )
                        ).normalized(viewportSize, viewportSize, maxScale = maxScale)
                    )
                }
            } finally {
                cameraAnimationJob = null
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(zoomEnabled, viewport, maxScale, verticalDocumentMode) {
                if (!zoomEnabled) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    recordSharedPdfInteraction()
                    cameraAnimationJob?.cancel()
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)
                    var gestureAccepted = latestCamera.isZoomed() && !verticalDocumentMode
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        event.changes.firstOrNull { it.pressed }?.let {
                            velocityTracker.addPosition(it.uptimeMillis, it.position)
                        }
                        if (pressedCount > 1 && kotlin.math.abs(zoom - 1f) > 0.005f) gestureAccepted = true
                        if (
                            verticalDocumentMode && latestCamera.isZoomed() && pressedCount == 1 &&
                            kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y) * 1.2f
                        ) gestureAccepted = true
                        if (gestureAccepted && viewport.width > 0 && viewport.height > 0) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val pivot = if (centroid == Offset.Unspecified) {
                                Offset(viewport.width / 2f, viewport.height / 2f)
                            } else centroid
                            updateCamera(
                                latestCamera.transformed(
                                    zoomChange = if (pressedCount > 1) zoom else 1f,
                                    panChange = PdfZoomPoint(pan.x, pan.y),
                                    pivot = PdfZoomPoint(pivot.x, pivot.y),
                                    viewport = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat()),
                                    content = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat()),
                                    maxScale = maxScale
                                )
                            )
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (latestCamera.scale <= 1.05f) updateCamera(PdfZoomCamera())
                    if (gestureAccepted && latestCamera.isZoomed()) {
                        velocityTracker.calculateVelocity().let { velocity ->
                            flingCamera(velocity.x, if (verticalDocumentMode) 0f else velocity.y)
                        }
                    }
                }
            }
            .pointerInput(zoomEnabled, tapGesturesEnabled, viewport, verticalDocumentMode, oneHandZoomDistancePx) {
                if (!tapGesturesEnabled) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val firstUp = waitForUpOrCancellation() ?: return@awaitEachGesture
                    var secondDown: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    try {
                        withTimeout(viewConfiguration.doubleTapTimeoutMillis) {
                            while (secondDown == null) {
                                secondDown = awaitPointerEvent().changes.firstOrNull { it.changedToDown() }
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (!firstUp.isConsumed) onSingleTap(firstDown.position)
                        return@awaitEachGesture
                    }
                    val pivot = secondDown?.position ?: firstDown.position
                    val startCamera = latestCamera
                    var latest = pivot
                    var quickUp = false
                    var earlyOneHandZoom = false
                    val movementSlop = maxOf(2f, viewConfiguration.touchSlop * 0.35f)
                    try {
                        withTimeout(90L) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == secondDown?.id }
                                    ?: return@withTimeout
                                latest = change.position
                                val delta = latest - pivot
                                if (zoomEnabled &&
                                    kotlin.math.abs(delta.y) >= movementSlop &&
                                    kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x) * 1.1f
                                ) {
                                    earlyOneHandZoom = true
                                    change.consume()
                                    return@withTimeout
                                }
                                if (change.changedToUp()) {
                                    quickUp = true
                                    change.consume()
                                    return@withTimeout
                                }
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        // Holding the second tap enters Android's one-hand zoom mode.
                    }

                    if (!zoomEnabled) {
                        if (!quickUp) waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    val viewportSize = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat())
                    if (quickUp && !earlyOneHandZoom) {
                        val target = if (verticalDocumentMode) {
                            pdfVerticalDoubleTapTargetScale(startCamera.scale)
                        } else {
                            pdfDoubleTapTargetScale(startCamera.scale)
                        }
                        val targetCamera = if (target <= 1f) PdfZoomCamera()
                            else startCamera.transformed(
                                zoomChange = target / startCamera.scale,
                                panChange = PdfZoomPoint(0f, 0f),
                                pivot = PdfZoomPoint(pivot.x, pivot.y),
                                viewport = viewportSize,
                                content = viewportSize,
                                maxScale = maxScale
                            )
                        animateCameraTo(targetCamera, if (verticalDocumentMode) 400 else 300)
                        return@awaitEachGesture
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == secondDown?.id } ?: break
                        latest = change.position
                        val rawDragY = latest.y - pivot.y
                        val nextScale = com.aryan.reader.shared.pdf.pdfOneHandZoomScale(
                            startScale = startCamera.scale,
                            totalDragY = if (verticalDocumentMode) rawDragY * startCamera.scale else rawDragY,
                            dragDistanceForDoublePx = oneHandZoomDistancePx,
                            maxScale = maxScale
                        )
                        updateCamera(
                            startCamera.transformed(
                                zoomChange = nextScale / startCamera.scale,
                                panChange = PdfZoomPoint(0f, 0f),
                                pivot = PdfZoomPoint(viewport.width / 2f, viewport.height / 2f),
                                viewport = viewportSize,
                                content = viewportSize,
                                maxScale = maxScale
                            )
                        )
                        change.consume()
                        if (change.changedToUp()) break
                    }
                    if (latestCamera.scale <= 1.05f) animateCameraTo(PdfZoomCamera(), 180)
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = camera.scale
                    scaleY = camera.scale
                    translationX = camera.offset.x
                    translationY = camera.offset.y
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            content(camera)
        }
        AnimatedVisibility(
            visible = showZoomIndicator,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable(enabled = zoomEnabled) { animateCameraTo(PdfZoomCamera(), 400) }
            ) {
                Text(
                    text = "$zoomPercentage%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** Lets the reader screen drive the vertical-scroll list (hardware arrow keys) from outside it. */
class SharedMobilePdfVerticalScrollController {
    private var lazyListState: LazyListState? = null

    internal fun attach(state: LazyListState) {
        lazyListState = state
    }

    internal fun detach() {
        lazyListState = null
    }

    suspend fun scrollByViewportFraction(fraction: Float) {
        val state = lazyListState ?: return
        val viewportHeightPx = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
        if (viewportHeightPx <= 0) return
        state.scrollBy(viewportHeightPx * fraction)
    }
}

@Composable
internal fun SharedMobilePdfVerticalPages(
    book: BookItem,
    pdfPassword: String?,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
    textureAlpha: Float,
    pageCount: Int,
    virtualLayout: List<SharedPdfVirtualPage>,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    navigationCenterFraction: Float,
    showPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    ttsPageIndex: Int?,
    ttsHighlightBounds: List<PdfPageBounds>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    verticalScrollController: SharedMobilePdfVerticalScrollController? = null,
    autoScrollPlaying: Boolean,
    autoScrollTemporarilyPaused: Boolean,
    autoScrollSpeed: Float,
    autoScrollMusicianMode: Boolean,
    onAutoScrollInteraction: (Long) -> Unit,
    onVisiblePageChanged: (Int) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    onAiDefine: ((String) -> Unit)? = null,
    userScrollEnabled: Boolean,
    isScrollLocked: Boolean,
    zoomCamera: PdfZoomCamera,
    onZoomCameraChanged: (PdfZoomCamera) -> Unit,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex.coerceIn(0, pageCount - 1))
    val scope = rememberCoroutineScope()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    DisposableEffect(verticalScrollController, listState) {
        verticalScrollController?.attach(listState)
        onDispose { verticalScrollController?.detach() }
    }
    var viewportSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    var musicianLeftHoldProgress by remember(book.id) { mutableStateOf(0f) }
    var musicianRightHoldProgress by remember(book.id) { mutableStateOf(0f) }
    LaunchedEffect(isListDragged) {
        if (isListDragged && autoScrollPlaying) onAutoScrollInteraction(300L)
    }
    LaunchedEffect(autoScrollPlaying, autoScrollTemporarilyPaused, autoScrollSpeed, listState) {
        if (!autoScrollPlaying || autoScrollTemporarilyPaused) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val frame = withFrameNanos { it }
            val deltaSeconds = (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame
            if (deltaSeconds <= 0f || deltaSeconds > 0.1f) continue
            recordSharedPdfFrame((deltaSeconds * 1_000f).toLong())
            listState.scrollBy(pdfAutoScrollPixelsPerSecond(autoScrollSpeed) * deltaSeconds)
        }
    }
    val navigationRender = rememberSharedMobilePdfPageRender(
        book = book,
        pageIndex = sharedPdfPdfPageIndexAt(virtualLayout, navigationRequestPage)
            ?: sharedPdfNearestPdfPageIndex(virtualLayout, navigationRequestPage)
            ?: 0,
        zoomScale = zoomCamera.scale,
        password = pdfPassword,
        reverseColorMode = reverseColorMode,
        preserveImageColors = preserveImageColors,
    )
    LaunchedEffect(navigationRequestToken, pageCount, viewportSize, navigationRender.aspectRatio) {
        if (viewportSize.height <= 0) return@LaunchedEffect
        val target = navigationRequestPage.coerceIn(0, pageCount - 1)
        val pageHeight = (viewportSize.width / navigationRender.aspectRatio.coerceIn(0.1f, 10f)).roundToInt()
        val centeredOffset = centeredPdfPageScrollOffset(
            viewportHeightPx = viewportSize.height,
            pageHeightPx = pageHeight,
            pageFraction = navigationCenterFraction
        )
        listState.animateScrollToItem(
            index = target,
            scrollOffset = centeredOffset
        )
    }
    LaunchedEffect(listState, pageCount) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - center)
            }?.index ?: listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { visiblePage ->
                onVisiblePageChanged(visiblePage.coerceIn(0, pageCount - 1))
            }
    }
    Box(modifier) {
        SharedMobilePdfZoomViewport(
            camera = zoomCamera,
            onCameraChanged = onZoomCameraChanged,
            zoomEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
            tapGesturesEnabled = state.selectedTool == PdfInkTool.NONE || state.selectedTool == PdfInkTool.TEXT || isStylusOnlyMode,
            maxScale = PDF_MAX_ZOOM_SCALE,
            verticalDocumentMode = true,
            onSingleTap = { onToggleChrome() },
            modifier = Modifier.fillMaxSize()
        ) { zoomCamera ->
            val zoomScale = zoomCamera.scale
            LazyColumn(
                state = listState,
                userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
                modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it },
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(if (showPageGap) 8.dp else 0.dp)
            ) {
                items(
                    count = pageCount,
                    key = { page ->
                        when (val virtualPage = virtualLayout[page]) {
                            is SharedPdfVirtualPage.PdfPage -> "pdf-${virtualPage.pdfIndex}"
                            is SharedPdfVirtualPage.BlankPage -> "blank-$page-${virtualPage.insertion.id}"
                        }
                    }
                ) { page ->
                    val pdfPage = sharedPdfPdfPageIndexAt(virtualLayout, page)
                    if (pdfPage == null) {
                        SharedMobilePdfBlankPageSurface(
                            insertion = (virtualLayout[page] as SharedPdfVirtualPage.BlankPage).insertion,
                            displayIndex = page,
                            displayPageCount = pageCount,
                            activeTheme = activeTheme,
                            showPageNumberOverlay = showPageNumberOverlay,
                            richTextController = richTextController,
                            isRichTextEditingEnabled = isRichTextEditingEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val render = rememberSharedMobilePdfPageRender(
                            book, pdfPage, zoomScale, pdfPassword,
                            reverseColorMode, preserveImageColors,
                        )
                        SharedMobilePdfPageSurface(
                        book = book,
                        pdfPassword = pdfPassword,
                        pageIndex = pdfPage,
                        pageCount = pageCount,
                        overlayPageNumber = page + 1,
                        overlayPageCount = pageCount,
                        pageRender = render,
                        zoomCamera = zoomCamera,
                        activeTheme = activeTheme,
                        reverseColorMode = reverseColorMode,
                        preserveImageColors = preserveImageColors,
                        textureAlpha = textureAlpha,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults.filter { it.pageIndex == pdfPage },
                        focusedSearchResult = searchResults.getOrNull(state.activeSearchResultIndex)
                            ?.takeIf { it.pageIndex == pdfPage },
                        searchHighlightMode = state.searchHighlightMode,
                        ttsHighlights = if (ttsPageIndex == pdfPage && !zoomCamera.isZoomed()) ttsHighlightBounds else emptyList(),
                        annotations = state.annotations.filter { it.pageIndex == pdfPage },
                        activeStroke = if (page == state.pageIndex) activeStroke else emptyList(),
                        isStylusOnlyMode = isStylusOnlyMode,
                        selectedTool = state.selectedTool,
                        selectedColorArgb = state.selectedColorArgb,
                        strokeWidth = state.strokeWidth,
                        textDraft = textDraft,
                        onTextDraftChange = onTextDraftChange,
                        onTextPageTap = onTextPageTap,
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        displayPageIndex = page,
                        onExternalLink = onExternalLink,
                        onInternalLink = onInternalLink,
                        onExistingHighlightTap = onExistingHighlightTap,
                        onHighlight = onHighlight,
                        onReadAloud = onReadAloud,
                        onAiDefine = onAiDefine,
                        onCanvasSizeChanged = onCanvasSizeChanged,
                        onFinishInkStroke = onFinishInkStroke,
                            showAllTextHighlights = showAllTextHighlights,
                            onAllTextHighlightsLoadingChange = onAllTextHighlightsLoadingChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (autoScrollMusicianMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 100.dp)
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(book.id) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val start = TimeSource.Monotonic.markNow()
                                while (isActive) {
                                    val elapsedMillis = start.elapsedNow().inWholeMilliseconds
                                    musicianLeftHoldProgress =
                                        (elapsedMillis / PdfMusicianHoldDurationMillis.toFloat()).coerceIn(0f, 1f)
                                    if (elapsedMillis >= PdfMusicianHoldDurationMillis) {
                                        musicianLeftHoldProgress = 0f
                                        longPressTriggered = true
                                        val plan = planPdfMusicianGesture(isRightRegion = false, isLongPress = true)
                                        onAutoScrollInteraction(plan.pauseMillis)
                                        listState.scrollToItem(0)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            musicianLeftHoldProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                val plan = planPdfMusicianGesture(isRightRegion = false, isLongPress = false)
                                onAutoScrollInteraction(plan.pauseMillis)
                                scope.launch { listState.scrollBy(viewportSize.height * plan.relativeViewportDelta) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (musicianLeftHoldProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { musicianLeftHoldProgress },
                        modifier = Modifier.size(48.dp).alpha(0.6f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(Icons.Default.ArrowUpward, null, Modifier.size(24.dp).alpha(0.6f))
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 100.dp)
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(book.id, pageCount) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val start = TimeSource.Monotonic.markNow()
                                while (isActive) {
                                    val elapsedMillis = start.elapsedNow().inWholeMilliseconds
                                    musicianRightHoldProgress =
                                        (elapsedMillis / PdfMusicianHoldDurationMillis.toFloat()).coerceIn(0f, 1f)
                                    if (elapsedMillis >= PdfMusicianHoldDurationMillis) {
                                        musicianRightHoldProgress = 0f
                                        longPressTriggered = true
                                        val plan = planPdfMusicianGesture(isRightRegion = true, isLongPress = true)
                                        onAutoScrollInteraction(plan.pauseMillis)
                                        listState.scrollToItem(pageCount - 1, Int.MAX_VALUE)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            musicianRightHoldProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                val plan = planPdfMusicianGesture(isRightRegion = true, isLongPress = false)
                                onAutoScrollInteraction(plan.pauseMillis)
                                scope.launch { listState.scrollBy(viewportSize.height * plan.relativeViewportDelta) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (musicianRightHoldProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { musicianRightHoldProgress },
                        modifier = Modifier.size(48.dp).alpha(0.6f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(Icons.Default.ArrowDownward, null, Modifier.size(24.dp).alpha(0.6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedMobilePdfPaginatedPages(
    book: BookItem,
    pdfPassword: String?,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
    textureAlpha: Float,
    pageCount: Int,
    virtualLayout: List<SharedPdfVirtualPage>,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    animateNavigation: Boolean,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    rightToLeftPagination: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    ttsPageIndex: Int?,
    ttsHighlightBounds: List<PdfPageBounds>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    tapToTurnPages: Boolean,
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    onAiDefine: ((String) -> Unit)? = null,
    userScrollEnabled: Boolean,
    isScrollLocked: Boolean,
    zoomCamera: PdfZoomCamera,
    onZoomCameraChanged: (PdfZoomCamera) -> Unit,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onManualPageTurnStarted: () -> Unit,
    onToggleChrome: () -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var paginationViewportSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    var pagerWindowRect by remember(book.id) { mutableStateOf<Rect?>(null) }
    val pageSurfaceWindowRects = remember(book.id) { mutableStateMapOf<Int, Rect>() }
    var textDrag by remember(book.id) { mutableStateOf<SharedPdfTextDragState?>(null) }
    val spreadStarts = remember(pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
        sharedMobilePdfSpreadStarts(pageCount, useTwoPageSpread, firstPageStandaloneInSpread)
    }
    fun pagerIndexForPage(pageIndex: Int): Int {
        val target = pageIndex.coerceIn(0, pageCount - 1)
        return spreadStarts.indexOfLast { it <= target }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = pagerIndexForPage(state.pageIndex),
        pageCount = { spreadStarts.size.coerceAtLeast(1) }
    )
    val isPagerDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isPagerDragged) {
        if (isPagerDragged) onManualPageTurnStarted()
    }
    LaunchedEffect(navigationRequestToken, spreadStarts) {
        val requestedPage = if (navigationRequestToken == 0) state.pageIndex else navigationRequestPage
        val target = pagerIndexForPage(requestedPage)
        if (pagerState.currentPage != target) {
            if (animateNavigation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, spreadStarts) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                onPageChanged(spreadStarts.getOrElse(settledPage) { 0 })
            }
    }
    var previousSettledPage by remember(book.id) { mutableStateOf(pagerState.settledPage) }
    LaunchedEffect(pagerState.settledPage, isScrollLocked) {
        if (pagerState.settledPage != previousSettledPage) {
            if (!isScrollLocked) onZoomCameraChanged(PdfZoomCamera())
            previousSettledPage = pagerState.settledPage
        }
    }
    val dragEdgeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    val textDropPaddingPx = with(LocalDensity.current) { 14.dp.toPx() }
    fun startTextDrag(topLeft: Offset, canvasSize: IntSize, displayPage: Int) {
        val draft = textDraft ?: return
        textDrag = SharedPdfTextDragState(
            draftId = draft.id,
            originDisplayPage = displayPage,
            originPdfPage = draft.pageIndex,
            relWidth = draft.bounds.right - draft.bounds.left,
            relHeight = draft.bounds.bottom - draft.bounds.top,
            dragOffset = topLeft,
            originCanvasSize = canvasSize,
            dragWidthPx = (draft.bounds.right - draft.bounds.left) * canvasSize.width * zoomCamera.scale,
            dragHeightPx = (draft.bounds.bottom - draft.bounds.top) * canvasSize.height * zoomCamera.scale,
            dragCameraScale = zoomCamera.scale
        )
    }
    fun updateTextDrag(delta: Offset) {
        val drag = textDrag ?: return
        val pagerRect = pagerWindowRect
        var updated = drag.copy(dragOffset = drag.dragOffset + delta)
        if (pagerRect != null) {
            updated = updated.copy(
                dragOffset = Offset(
                    updated.dragOffset.x.coerceIn(pagerRect.left - drag.dragWidthPx * 0.25f, pagerRect.right - drag.dragWidthPx * 0.25f),
                    updated.dragOffset.y.coerceIn(pagerRect.top - drag.dragHeightPx * 0.5f, pagerRect.bottom - drag.dragHeightPx * 0.5f)
                )
            )
        }
        textDrag = updated
        if (pagerRect == null) return
        val midX = updated.dragOffset.x + updated.dragWidthPx * 0.25f
        val targetPager = when {
            midX <= pagerRect.left + dragEdgeThresholdPx -> (pagerState.currentPage - 1).coerceAtLeast(0)
            midX >= pagerRect.right - dragEdgeThresholdPx -> (pagerState.currentPage + 1).coerceAtMost(spreadStarts.lastIndex)
            else -> return
        }
        if (targetPager != pagerState.currentPage) {
            scope.launch { pagerState.animateScrollToPage(targetPager) }
        }
    }
    fun endTextDrag() {
        val drag = textDrag ?: return
        textDrag = null
        val draft = textDraft?.takeIf { it.id == drag.draftId } ?: return
        val pagerRect = pagerWindowRect ?: return
        val spreadStart = spreadStarts.getOrElse(pagerState.currentPage) { 0 }
        val currentSpreadPages = if (useTwoPageSpread && spreadStart + 1 < pageCount) listOf(spreadStart, spreadStart + 1) else listOf(spreadStart)
        val targetDisplay = if (drag.originDisplayPage in currentSpreadPages) drag.originDisplayPage else currentSpreadPages.first()
        val targetRect = pageSurfaceWindowRects[targetDisplay] ?: return
        val bounds = sharedPdfTextDropBounds(
            dropTopLeft = drag.dragOffset,
            targetRect = targetRect,
            relWidth = drag.relWidth,
            relHeight = drag.relHeight,
            paddingPx = textDropPaddingPx
        )
        val targetPdfPage = sharedPdfNearestPdfPageIndex(virtualLayout, targetDisplay) ?: drag.originPdfPage
        val targetCanvasHeight = (targetRect.height / zoomCamera.scale.coerceAtLeast(0.1f)).coerceAtLeast(1f)
        val fontScale = if (drag.originCanvasSize.height > 0) targetCanvasHeight / drag.originCanvasSize.height else 1f
        val scaledNorm = (draft.style.sharedPdfTextPageRelativeFontSize() * fontScale).coerceIn(0.012f, 0.12f)
        onTextDraftChange(
            draft.copy(
                pageIndex = targetPdfPage,
                bounds = bounds,
                isManuallySized = true,
                style = draft.style.copy(pageRelativeFontSize = scaledNorm)
            )
        )
    }
    fun cancelTextDrag() {
        textDrag = null
    }
    Box(
        modifier = modifier.onGloballyPositioned { pagerWindowRect = it.boundsInWindow() }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE && !zoomCamera.isZoomed(),
            reverseLayout = rightToLeftPagination,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { pagerPage ->
        val spreadPages = remember(pagerPage, spreadStarts, pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
            val start = spreadStarts.getOrElse(pagerPage) { 0 }
            when {
                !useTwoPageSpread -> listOf(start)
                firstPageStandaloneInSpread && start == 0 -> listOf(0)
                else -> listOf(start, start + 1).filter { it in 0 until pageCount }
            }
        }
        SharedMobilePdfZoomViewport(
            camera = zoomCamera,
            onCameraChanged = onZoomCameraChanged,
            zoomEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
            tapGesturesEnabled = state.selectedTool == PdfInkTool.NONE || state.selectedTool == PdfInkTool.TEXT || isStylusOnlyMode,
            maxScale = PDF_MAX_ZOOM_SCALE,
            onSingleTap = { offset ->
                val viewportWidthForTap = paginationViewportSize.width.toFloat()
                val edge = viewportWidthForTap * 0.25f
                when {
                    tapToTurnPages && !zoomCamera.isZoomed() && offset.x < edge ->
                        pdfPaginationEdgeTarget(
                            currentPage = pagerPage,
                            lastPage = spreadStarts.lastIndex,
                            tappedLeftEdge = true,
                            rightToLeft = rightToLeftPagination,
                        )?.let { target ->
                            onManualPageTurnStarted()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    tapToTurnPages && !zoomCamera.isZoomed() && offset.x > viewportWidthForTap - edge ->
                        pdfPaginationEdgeTarget(
                            currentPage = pagerPage,
                            lastPage = spreadStarts.lastIndex,
                            tappedLeftEdge = false,
                            rightToLeft = rightToLeftPagination,
                        )?.let { target ->
                            onManualPageTurnStarted()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    else -> onToggleChrome()
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { paginationViewportSize = it }
        ) { activeZoomCamera ->
          val zoomScale = activeZoomCamera.scale
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageGap = if (spreadPages.size > 1) 8.dp else 0.dp
                val viewportHeight = maxHeight
                val slotWidth = (maxWidth - pageGap * (spreadPages.size - 1)) / spreadPages.size
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(pageGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    spreadPages.forEach { displayPage ->
                        val pdfPage = sharedPdfPdfPageIndexAt(virtualLayout, displayPage)
                        if (pdfPage == null) {
                            val insertion = (virtualLayout[displayPage] as SharedPdfVirtualPage.BlankPage).insertion
                            val aspectRatio = (insertion.widthPx / insertion.heightPx.coerceAtLeast(1f)).coerceIn(0.1f, 10f)
                            val widthLimited = slotWidth.value / viewportHeight.value <= aspectRatio
                            val fittedWidth = if (widthLimited) slotWidth else viewportHeight * aspectRatio
                            val fittedHeight = fittedWidth / aspectRatio
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                SharedMobilePdfBlankPageSurface(
                                    insertion = insertion,
                                    displayIndex = displayPage,
                                    displayPageCount = pageCount,
                                    activeTheme = activeTheme,
                                    showPageNumberOverlay = showPageNumberOverlay,
                                    richTextController = richTextController,
                                    isRichTextEditingEnabled = isRichTextEditingEnabled,
                                    modifier = Modifier.size(fittedWidth, fittedHeight)
                                )
                            }
                        } else {
                            val render = rememberSharedMobilePdfPageRender(
                                book, pdfPage, zoomScale, pdfPassword,
                                reverseColorMode, preserveImageColors,
                            )
                            val aspectRatio = render.aspectRatio.coerceIn(0.1f, 10f)
                            val widthLimited = slotWidth.value / viewportHeight.value <= aspectRatio
                            val fittedWidth = if (widthLimited) slotWidth else viewportHeight * aspectRatio
                            val fittedHeight = fittedWidth / aspectRatio
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                SharedMobilePdfPageSurface(
                                    book = book,
                                    pdfPassword = pdfPassword,
                                    pageIndex = pdfPage,
                                    pageCount = pageCount,
                                    overlayPageNumber = displayPage + 1,
                                    overlayPageCount = pageCount,
                                    pageRender = render,
                                    zoomCamera = activeZoomCamera,
                                    activeTheme = activeTheme,
                                    reverseColorMode = reverseColorMode,
                                    preserveImageColors = preserveImageColors,
                                    textureAlpha = textureAlpha,
                                    showPageNumberOverlay = showPageNumberOverlay,
                                    searchResults = searchResults.filter { it.pageIndex == pdfPage },
                                    focusedSearchResult = searchResults.getOrNull(state.activeSearchResultIndex)
                                        ?.takeIf { it.pageIndex == pdfPage },
                                    searchHighlightMode = state.searchHighlightMode,
                                    ttsHighlights = if (ttsPageIndex == pdfPage && !activeZoomCamera.isZoomed()) ttsHighlightBounds else emptyList(),
                                    annotations = state.annotations.filter { it.pageIndex == pdfPage },
                                    activeStroke = if (displayPage == state.pageIndex) activeStroke else emptyList(),
                                    isStylusOnlyMode = isStylusOnlyMode,
                                    selectedTool = state.selectedTool,
                                    selectedColorArgb = state.selectedColorArgb,
                                    strokeWidth = state.strokeWidth,
                                    textDraft = textDraft,
                                    onTextDraftChange = onTextDraftChange,
                                    onTextPageTap = onTextPageTap,
                                    richTextController = richTextController,
                                    isRichTextEditingEnabled = isRichTextEditingEnabled,
                                    displayPageIndex = displayPage,
                                    onTextDragStart = ::startTextDrag,
                                    onTextDrag = ::updateTextDrag,
                                    onTextDragEnd = ::endTextDrag,
                                    onTextDragCancel = ::cancelTextDrag,
                                    isTextDraftDragging = textDrag?.draftId == textDraft?.id,
                                    containerWindowRect = pagerWindowRect,
                                    onSurfaceWindowRectChanged = { rect -> pageSurfaceWindowRects[displayPage] = rect },
                                    onExternalLink = onExternalLink,
                                    onInternalLink = onInternalLink,
                                    onExistingHighlightTap = onExistingHighlightTap,
                                    onHighlight = onHighlight,
                                    onReadAloud = onReadAloud,
                                    onAiDefine = onAiDefine,
                                    onCanvasSizeChanged = onCanvasSizeChanged,
                                    onFinishInkStroke = onFinishInkStroke,
                                    showAllTextHighlights = showAllTextHighlights,
                                    onAllTextHighlightsLoadingChange = onAllTextHighlightsLoadingChange,
                                    modifier = Modifier.size(fittedWidth, fittedHeight)
                                )
                            }
                        }
          }
        }
    }
}
            }
        }
            textDrag?.let { drag ->
                val draft = textDraft?.takeIf { it.id == drag.draftId } ?: return@let
                val leftInPager = (drag.dragOffset.x - (pagerWindowRect?.left ?: 0f)).roundToInt()
                val topInPager = (drag.dragOffset.y - (pagerWindowRect?.top ?: 0f)).roundToInt()
                val widthDp = with(LocalDensity.current) { drag.dragWidthPx.toDp() }
                val heightDp = with(LocalDensity.current) { drag.dragHeightPx.toDp() }
                Box(
                    modifier = Modifier
                        .zIndex(10f)
                        .offset { IntOffset(leftInPager, topInPager) }
                        .graphicsLayer {
                            scaleX = drag.dragCameraScale
                            scaleY = drag.dragCameraScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .size(widthDp, heightDp)
                        .background(Color(0xFFFFFFFF), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(2.dp))
                ) {
                    Box(Modifier.padding(6.dp)) {
                        Text(
                            text = draft.text,
                            color = Color(0xFF111111),
                            fontSize = with(LocalDensity.current) { draft.style.sharedPdfTextFontSizePx(drag.originCanvasSize).toSp() },
                            fontFamily = sharedPdfFontFamily(draft.style.fontName ?: draft.style.fontPath),
                            textAlign = TextAlign.Start
                        )
                }
            }
        }
    }
}

internal fun sharedMobilePdfSpreadStarts(
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): List<Int> {
    return PdfSpreadLayout.spreadStartPageIndices(
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    ).ifEmpty { listOf(0) }
}

internal fun sharedMobilePdfPageLabel(
    pageIndex: Int,
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): String {
    return PdfSpreadLayout.pageRangeLabel(
        pageIndex = pageIndex,
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    )
}

/**
 * Renders the highlight-all overlay, mirroring Android's PdfHighlightsLayer:
 * merged text-line rects, filled over the text; in light mode a full-page
 * scrim dims everything except the text (punched out with BlendMode.Clear).
 */
@Composable
internal fun SharedMobilePdfAllTextHighlightOverlay(
    bounds: List<PdfPageBounds>,
    isDarkMode: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (bounds.isEmpty()) return
    val merged = remember(bounds) { sharedPdfMergeRectsIntoLines(bounds) }
    val colors = remember(isDarkMode, primaryColor) {
        sharedPdfHighlightAllColors(isDarkMode, primaryColor)
    }
    Canvas(modifier) {
        val canvasSize = size
        if (colors.scrimColor.alpha > 0f) {
            with(drawContext.canvas) {
                saveLayer(Rect(Offset.Zero, canvasSize), Paint())
                drawRect(colors.scrimColor, Offset.Zero, size)
                merged.forEach { item ->
                    val left = item.left * size.width
                    val top = item.top * size.height
                    val right = item.right * size.width
                    val bottom = item.bottom * size.height
                    if (right > left && bottom > top) {
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
                restore()
            }
        }
        merged.forEach { item ->
            val left = item.left * size.width
            val top = item.top * size.height
            val right = item.right * size.width
            val bottom = item.bottom * size.height
            if (right > left && bottom > top) {
                drawRect(colors.rectColor, Offset(left, top), Size(right - left, bottom - top))
            }
        }
    }
}

@Composable
internal fun SharedMobilePdfSearchHighlightOverlay(
    backgroundBounds: List<PdfPageBounds>,
    focusedBounds: List<PdfPageBounds>,
    modifier: Modifier = Modifier
) {
    if (backgroundBounds.isEmpty() && focusedBounds.isEmpty()) return
    val focusedStrokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(modifier) {
        fun drawSearchBounds(
            bounds: List<PdfPageBounds>,
            padding: Float,
            fill: Color,
            border: Color? = null,
        ) {
            bounds.forEach { item ->
                val left = item.left * size.width
                val top = item.top * size.height
                val right = item.right * size.width
                val bottom = item.bottom * size.height
                if (right > left && bottom > top) {
                    val paddedLeft = (left - padding).coerceAtLeast(0f)
                    val paddedTop = (top - padding).coerceAtLeast(0f)
                    val paddedRight = (right + padding).coerceAtMost(size.width)
                    val paddedBottom = (bottom + padding).coerceAtMost(size.height)
                    val paddedSize = androidx.compose.ui.geometry.Size(
                        paddedRight - paddedLeft,
                        paddedBottom - paddedTop,
                    )
                    drawRect(fill, Offset(paddedLeft, paddedTop), paddedSize)
                    if (border != null) {
                        drawRect(
                            color = border,
                            topLeft = Offset(paddedLeft, paddedTop),
                            size = paddedSize,
                            style = Stroke(width = focusedStrokeWidth),
                        )
                    }
                }
            }
        }
        drawSearchBounds(backgroundBounds, 3f, Color(0x66FFEB3B))
        drawSearchBounds(
            bounds = focusedBounds,
            padding = 5f,
            fill = Color(0x66FF6D00),
            border = Color(0xE6FF6D00),
        )
    }
}

@Composable
internal fun SharedMobilePdfTtsHighlightOverlay(
    bounds: List<PdfPageBounds>,
    modifier: Modifier = Modifier
) {
    if (bounds.isEmpty()) return
    Canvas(modifier) {
        bounds.forEach { item ->
            val left = item.left * size.width
            val top = item.top * size.height
            val right = item.right * size.width
            val bottom = item.bottom * size.height
            if (right > left && bottom > top) {
                drawRect(
                    color = Color(0x66FFCC33),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                )
            }
        }
    }
}

@Composable
internal fun SharedMobilePdfJumpHistoryBar(
    history: SharedPdfJumpHistory,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack, enabled = history.backPage != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous jump", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(history.backPage?.let { "Page ${it + 1}" }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Close, contentDescription = "Clear page history", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", maxLines = 1)
            }
            TextButton(onClick = onForward, enabled = history.forwardPage != null, modifier = Modifier.weight(1f)) {
                Text(history.forwardPage?.let { "Page ${it + 1}" }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next jump", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun SharedMobilePdfAutoScrollControls(
    isPlaying: Boolean,
    isTemporarilyPaused: Boolean,
    profile: PdfAutoScrollProfile,
    isLocalMode: Boolean,
    isMusicianMode: Boolean,
    useSlider: Boolean,
    isCollapsed: Boolean,
    onPlayPause: () -> Unit,
    onProfileChange: (PdfAutoScrollProfile) -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onMusicianModeChange: (Boolean) -> Unit,
    onUseSliderChange: (Boolean) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onScrollToTop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sanitized = profile.sanitized()
    var showModeMenu by remember { mutableStateOf(false) }
    var showMinMenu by remember { mutableStateOf(false) }
    var showMaxMenu by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
    Surface(
        modifier = modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        if (isCollapsed) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { onCollapsedChange(false) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Expand auto scroll")
                }
                Box(contentAlignment = Alignment.Center) {
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause auto scroll" else "Resume auto scroll",
                        )
                    }
                    if (isPlaying && isTemporarilyPaused) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.dp)
                    }
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { showModeMenu = true }) {
                            Text(if (isLocalMode) "Local speed" else "Global speed")
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select speed profile")
                        }
                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            DropdownMenuItem(
                                text = { Column { Text("Global speed"); Text("Applies to all files", style = MaterialTheme.typography.bodySmall) } },
                                onClick = { onLocalModeChange(false); showModeMenu = false },
                                trailingIcon = { if (!isLocalMode) Icon(Icons.Default.Check, null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Column { Text("Local speed"); Text("Saved for this file", style = MaterialTheme.typography.bodySmall) } },
                                onClick = { onLocalModeChange(true); showModeMenu = false },
                                trailingIcon = { if (isLocalMode) Icon(Icons.Default.Check, null) },
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onScrollToTop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMusicianModeChange(!isMusicianMode) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            SharedReaderIcons.MusicNote,
                            contentDescription = if (isMusicianMode) "Disable musician mode" else "Enable musician mode",
                            tint = if (isMusicianMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { onUseSliderChange(!useSlider) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap speed controls", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onCollapsedChange(true) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Collapse auto scroll", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Stop auto scroll", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause auto scroll" else "Resume auto scroll")
                        }
                        if (isPlaying && isTemporarilyPaused) {
                            CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 3.dp)
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box {
                                TextButton(onClick = { showMinMenu = true }) { Text("Min ${sanitized.minSpeed}×") }
                                DropdownMenu(expanded = showMinMenu, onDismissRequest = { showMinMenu = false }) {
                                    speedOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("${value}×") },
                                            onClick = { onProfileChange(sanitized.withMinSpeed(value)); showMinMenu = false },
                                        )
                                    }
                                }
                            }
                            Box {
                                TextButton(onClick = { showMaxMenu = true }) { Text("Max ${sanitized.maxSpeed}×") }
                                DropdownMenu(expanded = showMaxMenu, onDismissRequest = { showMaxMenu = false }) {
                                    speedOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("${value}×") },
                                            onClick = { onProfileChange(sanitized.withMaxSpeed(value)); showMaxMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (useSlider) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${sanitized.speed}×", modifier = Modifier.width(48.dp))
                                Slider(
                                    value = sanitized.speed,
                                    onValueChange = { onProfileChange(sanitized.copy(speed = (it * 10f).roundToInt() / 10f).sanitized()) },
                                    valueRange = sanitized.minSpeed..sanitized.maxSpeed.coerceAtLeast(sanitized.minSpeed + 0.1f),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton(onClick = {
                                        onProfileChange(sanitized.copy(speed = (sanitized.speed - 0.1f).coerceAtLeast(sanitized.minSpeed)))
                                    }) { Icon(Icons.Default.Remove, "Slower") }
                                    Text("${sanitized.speed}×", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = {
                                        onProfileChange(sanitized.copy(speed = (sanitized.speed + 0.1f).coerceAtMost(sanitized.maxSpeed)))
                                    }) { Icon(Icons.Default.Add, "Faster") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SharedPdfTtsOverlaySize { LARGE, MEDIUM, SMALL }

@Composable
internal fun SharedMobilePdfTtsControls(
    tts: SharedMobileEpubLocalTts,
    pageIndex: Int,
    pageCount: Int,
    chunkIndex: Int,
    chunkCount: Int,
    onPauseResume: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onLocate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overlaySize by remember { mutableStateOf(SharedPdfTtsOverlaySize.LARGE) }
    var rate by remember(tts.speechRate) { mutableStateOf(tts.speechRate) }
    var pitch by remember(tts.speechPitch) { mutableStateOf(tts.speechPitch) }
    val isSpeaking = tts.state == SharedMobileEpubLocalTtsState.SPEAKING
    val isPreparing = chunkIndex < 0 || chunkCount <= 0
    val canPrevious = !isPreparing && (chunkIndex > 0 || pageIndex > 0)
    val canNext = !isPreparing && (chunkIndex < chunkCount - 1 || pageIndex < pageCount - 1)
    val status = if (isPreparing) "Preparing" else "Part ${chunkIndex + 1} of $chunkCount"

    Surface(
        modifier = modifier
            .widthIn(max = if (overlaySize == SharedPdfTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "PdfTtsOverlayUnified"
        ) { size ->
            when (size) {
                SharedPdfTtsOverlaySize.SMALL -> Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.LARGE }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.MEDIUM }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 36.dp, 20.dp)
                }

                SharedPdfTtsOverlaySize.MEDIUM -> Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onLocate)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onPreviousPage, enabled = canPrevious, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous reading part")
                    }
                    PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 48.dp, 22.dp)
                    IconButton(onClick = onNextPage, enabled = canNext, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipNext, "Next reading part")
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.LARGE }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.SMALL }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, "Collapse TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SharedPdfTtsOverlaySize.LARGE -> Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("Device native", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onLocate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PushPin, "Locate current part", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.MEDIUM }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "Collapse TTS player", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.SMALL }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowRight, "Collapse TTS player", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Stop reading", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelLarge)
                            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPreviousPage, enabled = canPrevious, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Previous reading part")
                        }
                        PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 56.dp, 28.dp)
                        IconButton(onClick = onNextPage, enabled = canNext, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipNext, "Next reading part")
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfTtsParameterSlider("Speed", rate, 0.5f..3f) {
                                rate = it
                                tts.setSpeechParameters(rate, pitch)
                            }
                            PdfTtsParameterSlider("Pitch", pitch, 0.5f..2f) {
                                pitch = it
                                tts.setSpeechParameters(rate, pitch)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfTtsPlayButton(
    isSpeaking: Boolean,
    isPreparing: Boolean,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = !isPreparing,
            modifier = Modifier.size(buttonSize.coerceAtMost(56.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", modifier = Modifier.size(iconSize))
        }
        if (isPreparing) CircularProgressIndicator(Modifier.size(buttonSize), strokeWidth = 2.dp)
    }
}

@Composable
internal fun PdfTtsParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label ${((value * 10).roundToInt() / 10f)}×", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(20.dp))
    }
}

internal fun List<PdfPageBounds>.centerYFraction(): Float {
    if (isEmpty()) return 0.5f
    return ((minOf { it.top } + maxOf { it.bottom }) / 2f).coerceIn(0f, 1f)
}

@Composable
internal fun SharedMobilePdfPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onScrubPreview: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sliderValue by remember(pageCount) { mutableStateOf(pageIndex.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(pageIndex, isScrubbing) {
        if (!isScrubbing) sliderValue = pageIndex.toFloat()
    }
    DisposableEffect(Unit) {
        onDispose { scrubJob?.cancel() }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    scrubJob?.cancel()
                    val target = pageIndex - 1
                    onPageChange(target.coerceIn(0, pageCount - 1))
                    onScrubPreview(null)
                },
                enabled = pageCount > 1 && pageIndex > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Previous page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (pageIndex > 0) 0.9f else 0.32f)
                )
            }
            Slider(
                value = if (pageCount > 1) sliderValue else 0f,
                onValueChange = { next ->
                    isScrubbing = true
                    sliderValue = next
                    onScrubPreview(next.roundToInt().coerceIn(0, pageCount - 1))
                    scrubJob?.cancel()
                    scrubJob = scope.launch {
                        delay(200)
                        onPageChange(next.roundToInt().coerceIn(0, pageCount - 1))
                    }
                },
                onValueChangeFinished = {
                    scrubJob?.cancel()
                    onPageChange(sliderValue.roundToInt().coerceIn(0, pageCount - 1))
                    isScrubbing = false
                    onScrubPreview(null)
                },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    scrubJob?.cancel()
                    val target = pageIndex + 1
                    onPageChange(target.coerceIn(0, pageCount - 1))
                    onScrubPreview(null)
                },
                enabled = pageCount > 1 && pageIndex < pageCount - 1
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Next page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (pageIndex < pageCount - 1) 0.9f else 0.32f)
                )
            }
        }
    }
}

@Composable
internal fun SharedMobilePdfPageScrubbingOverlay(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                SharedReaderIcons.Slider,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun SharedMobilePdfPageSurface(
    book: BookItem,
    pdfPassword: String?,
    pageIndex: Int,
    pageCount: Int,
    pageRender: SharedMobilePdfPageRender,
    zoomCamera: PdfZoomCamera,
    activeTheme: ReaderTheme,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
    textureAlpha: Float,
    showPageNumberOverlay: Boolean,
    overlayPageNumber: Int = pageIndex + 1,
    overlayPageCount: Int = pageCount,
    searchResults: List<SharedPdfSearchResult>,
    focusedSearchResult: SharedPdfSearchResult?,
    searchHighlightMode: SearchHighlightMode,
    ttsHighlights: List<PdfPageBounds>,
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    selectedTool: PdfInkTool,
    selectedColorArgb: Int,
    strokeWidth: Float,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    displayPageIndex: Int,
    onTextDragStart: (Offset, IntSize, Int) -> Unit = { _, _, _ -> },
    onTextDrag: (Offset) -> Unit = {},
    onTextDragEnd: () -> Unit = {},
    onTextDragCancel: () -> Unit = {},
    isTextDraftDragging: Boolean = false,
    containerWindowRect: Rect? = null,
    onSurfaceWindowRectChanged: (Rect) -> Unit = {},
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    onAiDefine: ((String) -> Unit)? = null,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localCanvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var pageSurfaceWindowRect by remember(pageIndex) { mutableStateOf(Rect.Zero) }
    var isEraserOverrideActive by remember(pageIndex) { mutableStateOf(false) }
    var eraserOverridePosition by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    var visiblePageBounds by remember(pageIndex) { mutableStateOf<PdfPageBounds?>(null) }
    val textSession = rememberPdfTextPageSession(book, pageIndex, pdfPassword)
    var allTextHighlightBounds by remember(pageIndex) { mutableStateOf<List<PdfPageBounds>>(emptyList()) }
    LaunchedEffect(showAllTextHighlights, pageIndex, pageRender.bitmap, zoomCamera.scale, textSession) {
        if (zoomCamera.scale > 1f) {
            if (allTextHighlightBounds.isNotEmpty()) allTextHighlightBounds = emptyList()
            return@LaunchedEffect
        }
        if (!showAllTextHighlights) {
            if (allTextHighlightBounds.isNotEmpty()) allTextHighlightBounds = emptyList()
            return@LaunchedEffect
        }
        if (pageRender.bitmap == null) return@LaunchedEffect
        onAllTextHighlightsLoadingChange(true)
        val session = textSession
        val pdfiumBounds = if (session != null && session.pageCharCount > 0) {
            session.rectsForRangeNormalized(0, session.pageCharCount)
        } else {
            emptyList()
        }
        allTextHighlightBounds = if (pdfiumBounds.isNotEmpty()) {
            pdfiumBounds
        } else {
            sharedMobilePdfOcrTextBounds(book, pageIndex, pdfPassword)
        }
        onAllTextHighlightsLoadingChange(false)
    }
    fun searchResultBounds(result: SharedPdfSearchResult): List<PdfPageBounds> {
        return result.boundsList.ifEmpty {
            textSession?.rectsForRangeNormalized(result.matchIndex, result.matchLength).orEmpty()
        }
    }
    val allSearchHighlights = remember(textSession, searchResults, searchHighlightMode) {
        if (searchHighlightMode == SearchHighlightMode.ALL) searchResults.flatMap(::searchResultBounds)
        else emptyList()
    }
    val focusedSearchHighlights = remember(textSession, focusedSearchResult) { focusedSearchResult?.let(::searchResultBounds).orEmpty() }
    val embeddedAnnotations = rememberSharedMobilePdfEmbeddedAnnotations(book, pageIndex, pdfPassword)
    val textureBitmap = sharedMobilePdfTextureBitmap(activeTheme)
    val highResolutionTiles = rememberSharedMobilePdfTileRenders(
        book = book,
        pageIndex = pageIndex,
        pageAspectRatio = pageRender.aspectRatio,
        zoomScale = zoomCamera.scale,
        visibleBounds = visiblePageBounds,
        password = pdfPassword,
        reverseColorMode = reverseColorMode,
        preserveImageColors = preserveImageColors,
    )
    val pageColorFilter = sharedMobilePdfColorFilter(activeTheme, reverseColorMode)
        .takeUnless { pageRender.rasterizedReverseColorMode != null }
    Surface(
        color = sharedMobilePdfPageBackground(activeTheme),
        contentColor = sharedMobilePdfPageTextColor(activeTheme),
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio(pageRender.aspectRatio)
            .clipToBounds()
            .onSizeChanged {
                localCanvasSize = it
                onCanvasSizeChanged(it)
            }
            .onGloballyPositioned { coordinates ->
                val page = coordinates.boundsInWindow()
                pageSurfaceWindowRect = page
                onSurfaceWindowRectChanged(page)
                val viewport = coordinates.findRootCoordinates().boundsInWindow()
                visiblePageBounds = visiblePdfPageBounds(
                    camera = zoomCamera,
                    transformedPageLeft = page.left,
                    transformedPageTop = page.top,
                    transformedPageRight = page.right,
                    transformedPageBottom = page.bottom,
                    viewportLeft = viewport.left,
                    viewportTop = viewport.top,
                    viewportRight = viewport.right,
                    viewportBottom = viewport.bottom
                )
            }
            .then(
                if (selectedTool == PdfInkTool.NONE) Modifier
                else Modifier.pointerInput(selectedTool, localCanvasSize, pageIndex, isStylusOnlyMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!sharedPdfIsInkDownAllowed(isStylusOnlyMode, down.type)) {
                            return@awaitEachGesture
                        }
                        var eraserOverride = sharedPdfIsEraserOverride(down.type, false)
                        val touchSlop = viewConfiguration.touchSlop
                        var dragStarted = false
                        var committed = false
                        var dragSum = Offset.Zero
                        var lastPoint: Offset? = null
                        if (eraserOverride && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                            eraserOverridePosition = down.position
                            isEraserOverrideActive = true
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Android exposes the stylus barrel button on
                                // the pointer event, while iOS exposes the
                                // equivalent Pencil shortcut as shared state.
                                // Check the first in-scope event without adding
                                // a second await before the gesture starts.
                                if (!eraserOverride && down.type == PointerType.Stylus &&
                                    sharedPdfStylusBarrelPressed(event)
                                ) {
                                    eraserOverride = true
                                    if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                        eraserOverridePosition = down.position
                                        isEraserOverrideActive = true
                                    }
                                }
                                if (event.changes.size > 1) {
                                    (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                    return@awaitEachGesture
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (change.changedToUp()) {
                                    change.consume()
                                    if (dragStarted) {
                                        onFinishInkStroke(pageIndex, eraserOverride)
                                        committed = true
                                    } else {
                                        (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                    }
                                    return@awaitEachGesture
                                }
                                if (change.isConsumed) continue
                                if (!dragStarted) {
                                    if (change.positionChanged()) {
                                        dragSum += change.positionChange()
                                        if (dragSum.getDistance() > touchSlop) {
                                            dragStarted = true
                                            (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                                val startPoint = lastPoint ?: change.position
                                                (activeStroke as? MutableList<PdfPagePoint>)?.add(startPoint.toSharedMobilePdfPoint(localCanvasSize))
                                                if (eraserOverride) eraserOverridePosition = startPoint
                                            }
                                            change.consume()
                                        }
                                    }
                                    lastPoint = change.position
                                } else {
                                    if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                        val mutableStroke = activeStroke as? MutableList<PdfPagePoint>
                                        if (mutableStroke != null) {
                                            mutableStroke.add(change.position.toSharedMobilePdfPoint(localCanvasSize))
                                        }
                                        if (eraserOverride) eraserOverridePosition = change.position
                                    }
                                    change.consume()
                                }
                            }
                        } finally {
                            if (!committed) {
                                (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                            }
                            isEraserOverrideActive = false
                            eraserOverridePosition = null
                        }
                    }
                }
            )
            .then(
                if (selectedTool == PdfInkTool.TEXT) {
                    Modifier.pointerInput(
                        pageIndex,
                        localCanvasSize,
                        annotations,
                        textDraft,
                        richTextController,
                        isRichTextEditingEnabled,
                        displayPageIndex
                    ) {
                        detectTapGestures { offset ->
                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                val point = offset.toSharedMobilePdfPoint(localCanvasSize)
                                val hit = annotations.firstOrNull {
                                    it.kind == PdfAnnotationKind.TEXT &&
                                        it.bounds?.containsNormalizedPoint(point.x, point.y) == true
                                }
                                if (hit != null) {
                                    onTextPageTap(hit)
                                    return@detectTapGestures
                                }
                                if (textDraft != null) {
                                    onTextPageTap(null)
                                    return@detectTapGestures
                                }
                                // No box under the tap: place the flowing document cursor
                                // (Android's RichTextLayer tap handling, handled at surface level
                                // so box hit-testing wins on box areas).
                                val controller = richTextController ?: return@detectTapGestures
                                if (!isRichTextEditingEnabled) return@detectTapGestures
                                val marginX = localCanvasSize.width * 0.1f
                                val marginY = localCanvasSize.height * 0.08f
                                val editorWidth = localCanvasSize.width - marginX * 2f
                                val editorHeight = localCanvasSize.height - marginY * 2f
                                val editorLocal = Offset(offset.x - marginX, offset.y - marginY)
                                if (
                                    editorLocal.x >= 0f &&
                                    editorLocal.y >= 0f &&
                                    editorLocal.x <= editorWidth &&
                                    editorLocal.y <= editorHeight
                                ) {
                                    controller.handleTapOnPage(displayPageIndex, editorLocal)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (pageRender.bitmap != null) {
                Image(
                    bitmap = pageRender.bitmap,
                    contentDescription = book.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = pageColorFilter
                )
                if (highResolutionTiles.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        highResolutionTiles.forEach { tile ->
                            val bounds = tile.request.normalizedBounds
                            val left = (bounds.left * size.width).roundToInt()
                            val top = (bounds.top * size.height).roundToInt()
                            val right = (bounds.right * size.width).roundToInt()
                            val bottom = (bounds.bottom * size.height).roundToInt()
                            drawImage(
                                image = tile.bitmap,
                                dstOffset = IntOffset(left, top),
                                dstSize = IntSize((right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1)),
                                colorFilter = if (tile.rasterizedReverseColorMode == null) {
                                    sharedMobilePdfColorFilter(activeTheme, reverseColorMode)
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            } else {
                SharedMobilePdfPagePlaceholder(
                    book = book,
                    pageIndex = pageIndex,
                    errorMessage = pageRender.errorMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (textureBitmap != null && textureAlpha > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        brush = ShaderBrush(
                            ImageShader(
                                image = textureBitmap,
                                tileModeX = TileMode.Repeated,
                                tileModeY = TileMode.Repeated
                            )
                        ),
                        alpha = textureAlpha.coerceIn(0f, 1f),
                        blendMode = if (activeTheme.isDark || activeTheme.id == "reverse") BlendMode.Screen else BlendMode.Multiply
                    )
                }
            }
            SharedMobilePdfSearchHighlightOverlay(
                backgroundBounds = allSearchHighlights,
                focusedBounds = focusedSearchHighlights,
                modifier = Modifier.fillMaxSize()
            )
            if (showAllTextHighlights && allTextHighlightBounds.isNotEmpty()) {
                SharedMobilePdfAllTextHighlightOverlay(
                    bounds = allTextHighlightBounds,
                    isDarkMode = activeTheme.isDark,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SharedMobilePdfTtsHighlightOverlay(
                bounds = ttsHighlights,
                modifier = Modifier.fillMaxSize()
            )
            if (richTextController != null && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                SharedPdfRichTextLayer(
                    pageIndex = displayPageIndex,
                    controller = richTextController,
                    pageWidth = localCanvasSize.width.toFloat(),
                    pageHeight = localCanvasSize.height.toFloat(),
                    isTextEditingEnabled = isRichTextEditingEnabled,
                    isDarkMode = activeTheme.isDark,
                    tapHandlingEnabled = false
                )
            }
            SharedPdfAnnotationOverlay(
                annotations = annotations,
                activeStroke = activeStroke,
                canvasSize = localCanvasSize,
                activeTool = if (isEraserOverrideActive) PdfInkTool.ERASER else selectedTool,
                activeStrokeColorArgb = selectedColorArgb,
                activeStrokeWidth = strokeWidth,
                eraserPosition = eraserOverridePosition,
                showEraserIndicator = isEraserOverrideActive
            )
            SharedMobilePdfEmbeddedAnnotationLayer(embeddedAnnotations, localCanvasSize)
            textDraft?.takeIf { it.pageIndex == pageIndex }?.let { draft ->
                SharedPdfTextBoxEditorOverlay(
                    id = draft.id,
                    text = draft.text,
                    style = draft.style,
                    bounds = draft.bounds,
                    canvasSize = localCanvasSize,
                    onTextChange = { nextText ->
                        onTextDraftChange(draft.withText(nextText, localCanvasSize))
                    },
                    onBoundsChange = { nextBounds ->
                        onTextDraftChange(draft.withBounds(nextBounds))
                    },
                    onGlobalDragStart = {
                        val container = containerWindowRect ?: return@SharedPdfTextBoxEditorOverlay
                        val scale = zoomCamera.scale.coerceAtLeast(0.1f)
                        val boxTopLeftInWindow = Offset(
                            pageSurfaceWindowRect.left + draft.bounds.left * localCanvasSize.width * scale,
                            pageSurfaceWindowRect.top + draft.bounds.top * localCanvasSize.height * scale
                        )
                        onTextDragStart(boxTopLeftInWindow, localCanvasSize, displayPageIndex)
                    },
                    onGlobalDrag = { delta ->
                        onTextDrag(Offset(delta.x * zoomCamera.scale, delta.y * zoomCamera.scale))
                    },
                    onGlobalDragEnd = { onTextDragEnd() },
                    onGlobalDragCancel = { onTextDragCancel() },
                    isDraggingGlobally = isTextDraftDragging,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SharedMobilePdfTextSelectionOverlay(
                book = book,
                pageIndex = pageIndex,
                password = pdfPassword,
                textSession = textSession,
                canvasSize = localCanvasSize,
                selectedTool = selectedTool,
                pageRender = pageRender,
                zoomTiles = highResolutionTiles,
                zoomScale = zoomCamera.scale,
                magnifierColorFilter = sharedMobilePdfColorFilter(activeTheme, reverseColorMode),
                onExternalLink = onExternalLink,
                onInternalLink = onInternalLink,
                existingHighlights = annotations.filter { it.kind == PdfAnnotationKind.HIGHLIGHT },
                onExistingHighlightTap = onExistingHighlightTap,
                onHighlight = { range, text, bounds, color, style, note -> onHighlight(pageIndex, range, text, bounds, color, style, note) },
                onReadAloud = { charIndex -> onReadAloud(pageIndex, charIndex) },
                onAiDefine = onAiDefine,
                modifier = Modifier.fillMaxSize()
            )
            if (showPageNumberOverlay) {
                SharedPdfPageNumberOverlay(
                    pageIndex = overlayPageNumber - 1,
                    pageCount = overlayPageCount,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun SharedMobilePdfBlankPageSurface(
    insertion: SharedPdfBlankPageInsertion,
    displayIndex: Int,
    displayPageCount: Int,
    activeTheme: ReaderTheme,
    showPageNumberOverlay: Boolean,
    richTextController: SharedPdfRichTextController? = null,
    isRichTextEditingEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    var blankCanvasSize by remember(displayIndex) { mutableStateOf(IntSize.Zero) }
    Surface(
        color = sharedMobilePdfPageBackground(activeTheme),
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio((insertion.widthPx / insertion.heightPx.coerceAtLeast(1f)).coerceIn(0.1f, 10f))
            .clipToBounds()
            .onSizeChanged { blankCanvasSize = it }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (
                richTextController != null &&
                blankCanvasSize.width > 0 &&
                blankCanvasSize.height > 0
            ) {
                SharedPdfRichTextLayer(
                    pageIndex = displayIndex,
                    controller = richTextController,
                    pageWidth = blankCanvasSize.width.toFloat(),
                    pageHeight = blankCanvasSize.height.toFloat(),
                    isTextEditingEnabled = isRichTextEditingEnabled,
                    isDarkMode = activeTheme.isDark,
                    tapHandlingEnabled = true
                )
            }
            if (showPageNumberOverlay) {
                SharedPdfPageNumberOverlay(
                    pageIndex = displayIndex,
                    pageCount = displayPageCount,
                    modifier = Modifier.fillMaxSize(),
                    isDarkPage = sharedMobilePdfPageTextColor(activeTheme).luminance() < 0.5f
                )
            }
        }
    }
}

@Composable
internal fun sharedMobilePdfTextureBitmap(theme: ReaderTheme): ImageBitmap? {
    val resource = when (theme.textureId) {
        "asset:ep_naturalwhite.webp" -> Res.drawable.ep_naturalwhite
        "asset:retina_wood.webp" -> Res.drawable.retina_wood
        "asset:light-veneer.webp" -> Res.drawable.light_veneer
        "asset:grey_wash_wall.webp" -> Res.drawable.grey_wash_wall
        "asset:classy_fabric.webp" -> Res.drawable.classy_fabric
        "asset:retro_intro.webp" -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

@Composable
internal fun SharedMobilePdfPagePlaceholder(
    book: BookItem,
    pageIndex: Int,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val margin = size.width * 0.09f
        val lineStart = margin
        val lineEnd = size.width - margin
        val top = size.height * 0.16f
        val path = Path().apply {
            moveTo(lineStart, top)
            lineTo(lineEnd * 0.72f, top)
        }
        drawPath(
            path = path,
            color = Color(0xFF303030),
            style = Stroke(width = 3f)
        )
        repeat(10) { index ->
            val y = top + 44f + index * 34f
            val end = if (index % 4 == 3) lineEnd * 0.72f else lineEnd
            drawLine(
                color = Color(0xFF9E9E9E),
                start = Offset(lineStart, y),
                end = Offset(end, y),
                strokeWidth = 2f
            )
        }
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(lineStart, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width - margin * 2f, size.height * 0.2f)
        )
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Text(
                text = book.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = errorMessage ?: "Rendering PDF page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF616161)
            )
        }
    }
}

internal fun Offset.toSharedMobilePdfPoint(size: IntSize): PdfPagePoint {
    return PdfPagePoint(
        x = (x / size.width.toFloat()).coerceIn(0f, 1f),
        y = (y / size.height.toFloat()).coerceIn(0f, 1f),
        timestamp = currentTimestamp()
    )
}
