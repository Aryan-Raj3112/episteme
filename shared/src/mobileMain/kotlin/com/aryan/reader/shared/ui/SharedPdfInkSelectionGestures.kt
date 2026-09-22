package com.aryan.reader.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfInkSelection
import com.aryan.reader.shared.pdf.SharedPdfSelectionHandle
import com.aryan.reader.shared.pdf.SharedPdfSelectionTransform
import com.aryan.reader.shared.pdf.findSharedPdfLassoSelectionHits
import com.aryan.reader.shared.pdf.findSharedPdfTopmostSelectionHit
import com.aryan.reader.shared.pdf.isEdgeHandle
import com.aryan.reader.shared.pdf.sharedPdfPivotForHandle
import com.aryan.reader.shared.pdf.sharedPdfRotationForDrag
import com.aryan.reader.shared.pdf.sharedPdfScaleForCornerDrag
import com.aryan.reader.shared.pdf.sharedPdfScaleXYForEdgeDrag
import com.aryan.reader.shared.pdf.sharedPdfSelectionDispatchSlopPx

/**
 * Single-finger selection state machine for the SELECT tool (shared mobile
 * reader, benchmark: Android `detectPdfInkSelectionGestures`).
 *
 * Handles are NOT resolved here: the screen-level handle overlay owns all
 * nine handles in container (screen) space — document-level like Android —
 * so grabs work even when a handle overflows onto a neighboring page (the
 * rotate handle floats above the box). It claims the down in the Initial
 * pass; this block therefore sees only unclaimed downs and resolves
 * stroke-tap/drag-to-move, box-interior move, and freeform lasso.
 *
 * Coordinates are page-local px (the same space ink drawing uses via
 * `toSharedMobilePdfPoint`); annotation math stays in normalized page coords.
 * The overlay canvas mounts in this same space, so lasso trails and handles
 * line up with the page content under the zoom transform.
 */
suspend fun PointerInputScope.detectSharedPdfInkSelectionGestures(
    pageIndex: Int,
    pageSizePx: IntSize,
    pageAspectRatio: Float,
    touchSlopPx: Float,
    isStylusOnlyMode: Boolean,
    zoomProvider: () -> Float,
    annotationsProvider: () -> List<SharedPdfAnnotation>,
    /**
     * Union bounds of the current selection on this page, or null when the
     * selection lives elsewhere (or is empty). Powers the box-interior
     * drag-as-move branch below.
     */
    selectionBoundsProvider: () -> com.aryan.reader.shared.pdf.PdfPageBounds?,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onLassoResult: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    onLassoProgress: (pagePoints: List<Offset>?) -> Unit,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: SharedPdfSelectionTransform, aspectRatio: Float) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
    /**
     * True while the screen-level handle overlay runs a transform session.
     * Pages stand down for that gesture (a sibling coroutine, unlike
     * Android's single stream, would otherwise start a rival session with
     * a second finger landing mid-drag).
     */
    handleTransformInFlightProvider: () -> Boolean = { false },
    /**
     * True when the screen-level handle overlay owns this page (it holds an
     * active selection here and covers it, so it runs this same detector in
     * its own space). The page then stands down so the gesture is handled
     * exactly once on platforms where both would otherwise fire.
     */
    overlayOwnsPageProvider: () -> Boolean = { false },
) {
    awaitEachGesture {
        detectSharedPdfInkSelectionGestureOnce(
            pageIndex = pageIndex,
            pageSizePx = pageSizePx,
            pageAspectRatio = pageAspectRatio,
            touchSlopPx = touchSlopPx,
            isStylusOnlyMode = isStylusOnlyMode,
            zoomProvider = zoomProvider,
            annotationsProvider = annotationsProvider,
            selectionBoundsProvider = selectionBoundsProvider,
            onTapResult = onTapResult,
            onLassoResult = onLassoResult,
            onLassoProgress = onLassoProgress,
            onTransformStart = onTransformStart,
            onTransformUpdate = onTransformUpdate,
            onTransformEnd = onTransformEnd,
            handleTransformInFlightProvider = handleTransformInFlightProvider,
            overlayOwnsPageProvider = overlayOwnsPageProvider,
            toNorm = { it.toSharedPdfNormPoint(pageSizePx) },
        )
    }
}

/**
 * One gesture of [detectSharedPdfInkSelectionGestures], extracted so the
 * screen-level handle overlay can own the selection page: after a handle
 * miss it runs this same stroke-tap / box-move / lasso dispatch in its own
 * (container-space) coordinates via [toNorm], instead of the page running a
 * rival copy underneath it.
 */
internal suspend fun AwaitPointerEventScope.detectSharedPdfInkSelectionGestureOnce(
    pageIndex: Int,
    pageSizePx: IntSize,
    pageAspectRatio: Float,
    touchSlopPx: Float,
    isStylusOnlyMode: Boolean,
    zoomProvider: () -> Float,
    annotationsProvider: () -> List<SharedPdfAnnotation>,
    /**
     * Union bounds of the current selection on this page, or null when the
     * selection lives elsewhere (or is empty). Powers the box-interior
     * drag-as-move branch below.
     */
    selectionBoundsProvider: () -> com.aryan.reader.shared.pdf.PdfPageBounds?,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onLassoResult: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    onLassoProgress: (pagePoints: List<Offset>?) -> Unit,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: SharedPdfSelectionTransform, aspectRatio: Float) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
    /**
     * True while the screen-level handle overlay runs a transform session.
     * Pages stand down for that gesture (a sibling coroutine, unlike
     * Android's single stream, would otherwise start a rival session with
     * a second finger landing mid-drag).
     */
    handleTransformInFlightProvider: () -> Boolean = { false },
    /**
     * True when the screen-level handle overlay owns this page (see above);
     * the page then stands down so the gesture is handled exactly once.
     */
    overlayOwnsPageProvider: () -> Boolean = { false },
    toNorm: (Offset) -> PdfPagePoint,
) {
        // Unclaimed only: the screen-level handle overlay consumes handle
        // grabs in the Initial pass before this Main-pass block runs.
        val down = awaitFirstDown(requireUnconsumed = true)
        pdfInkSelectionLog {
            "page.down page=$pageIndex pos=(${down.position.x.roundToInt()},${down.position.y.roundToInt()}) " +
                "size=${pageSizePx.width}x${pageSizePx.height} id=${down.id} type=${down.type}"
        }
        if (overlayOwnsPageProvider()) {
            pdfInkSelectionLog { "page.skip page=$pageIndex reason=overlay-owns-page" }
            return
        }
        if (handleTransformInFlightProvider()) {
            pdfInkSelectionLog { "page.skip page=$pageIndex reason=handle-session-in-flight" }
            return
        }
        if (isStylusOnlyMode && down.type == PointerType.Touch) {
            pdfInkSelectionLog { "page.skip page=$pageIndex reason=stylus-only-touch" }
            return
        }
        if (pageSizePx.width <= 0 || pageSizePx.height <= 0) return
        runSharedPdfInkSelectionForDown(
            down = down,
            pageIndex = pageIndex,
            pageSizePx = pageSizePx,
            pageAspectRatio = pageAspectRatio,
            touchSlopPx = touchSlopPx,
            zoomProvider = zoomProvider,
            annotationsProvider = annotationsProvider,
            selectionBoundsProvider = selectionBoundsProvider,
            onTapResult = onTapResult,
            onLassoResult = onLassoResult,
            onLassoProgress = onLassoProgress,
            onTransformStart = onTransformStart,
            onTransformUpdate = onTransformUpdate,
            onTransformEnd = onTransformEnd,
            toNorm = toNorm,
        )
    }

/**
 * Stroke-tap / box-move / lasso dispatch for an already-acquired [down].
 * Pages reach it via [detectSharedPdfInkSelectionGestureOnce]; the
 * screen-level handle overlay calls it directly on a handle miss so the
 * selection page is owned in one place (no rival copy underneath).
 * Positions are in the caller's scope space, mapped via [toNorm].
 */
internal suspend fun AwaitPointerEventScope.runSharedPdfInkSelectionForDown(
    down: PointerInputChange,
    pageIndex: Int,
    pageSizePx: IntSize,
    pageAspectRatio: Float,
    touchSlopPx: Float,
    zoomProvider: () -> Float,
    annotationsProvider: () -> List<SharedPdfAnnotation>,
    selectionBoundsProvider: () -> com.aryan.reader.shared.pdf.PdfPageBounds?,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onLassoResult: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    onLassoProgress: (pagePoints: List<Offset>?) -> Unit,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: SharedPdfSelectionTransform, aspectRatio: Float) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
    toNorm: (Offset) -> PdfPagePoint,
) {
        val startNorm = toNorm(down.position)
        val pageAnnotations = annotationsProvider()

        // 1. Stroke hit: tap selects, drag moves. The dispatch hit is tight
        // (a fraction of the finger slop) so lassos can start in the gaps
        // between strokes; a down that ends as a tap re-hits generously in
        // the lasso session below, keeping small strokes tappable.
        val dispatchSlopPx = sharedPdfSelectionDispatchSlopPx(touchSlopPx)
        val hit = findSharedPdfTopmostSelectionHit(
            annotations = pageAnnotations,
            normX = startNorm.x,
            normY = startNorm.y,
            pageWidthPx = pageSizePx.width.toFloat(),
            pageAspectRatio = pageAspectRatio,
            tapSlopPx = dispatchSlopPx,
        )
        pdfInkSelectionLog {
            "page.strokeHit page=$pageIndex id=${hit?.id} " +
                "norm=(${startNorm.x.format3()},${startNorm.y.format3()}) tapsSlopPx=$dispatchSlopPx"
        }
        if (hit != null) {
            runSharedTapOrMoveSession(
                hitId = hit.id,
                startNorm = startNorm,
                downId = down.id,
                touchSlopPx = touchSlopPx,
                pageWidthPx = pageSizePx.width.toFloat(),
                zoomProvider = zoomProvider,
                onTapResult = { onTapResult(pageIndex, it) },
                onTransformStart = { onTransformStart(pageIndex) },
                onTransformUpdate = { onTransformUpdate(pageIndex, it, pageAspectRatio) },
                onTransformEnd = onTransformEnd,
                toNorm = toNorm,
            )
            return
        }

        // 2. Inside the selection box but not on a stroke: drag moves the
        // selection, tap keeps it (standard tldraw behavior — only tapping
        // outside the box clears).
        val selectionBounds = selectionBoundsProvider()
        pdfInkSelectionLog {
            "page.box page=$pageIndex bounds=" + if (selectionBounds == null) {
                "null"
            } else {
                "(${selectionBounds.left.format3()},${selectionBounds.top.format3()})-" +
                    "(${selectionBounds.right.format3()},${selectionBounds.bottom.format3()})"
            }
        }
        if (selectionBounds != null &&
            startNorm.x in selectionBounds.left..selectionBounds.right &&
            startNorm.y in selectionBounds.top..selectionBounds.bottom
        ) {
            runSharedTapOrMoveSession(
                hitId = "",
                startNorm = startNorm,
                downId = down.id,
                touchSlopPx = touchSlopPx,
                pageWidthPx = pageSizePx.width.toFloat(),
                zoomProvider = zoomProvider,
                onTapResult = {},
                onTransformStart = { onTransformStart(pageIndex) },
                onTransformUpdate = { onTransformUpdate(pageIndex, it, pageAspectRatio) },
                onTransformEnd = onTransformEnd,
                toNorm = toNorm,
            )
            return
        }

        // 3. Empty space: tap clears, drag lassos. The lasso-start slop is
        // zoom-compensated (screen px -> page-local px) so lassos trigger
        // with the same finger travel at any zoom, like Android. A down that
        // ends as a tap re-hits with the full slop ([touchSlopPx]) so small
        // strokes near the tap still select instead of clearing.
        runSharedLassoSession(
            downId = down.id,
            touchSlopPx = touchSlopPx / zoomProvider().coerceAtLeast(0.01f),
            tapHitSlopPx = touchSlopPx,
            zoomProvider = zoomProvider,
            pageWidthPx = pageSizePx.width.toFloat(),
            pageAspectRatio = pageAspectRatio,
            pageAnnotations = pageAnnotations,
            onTapResult = { hitId -> onTapResult(pageIndex, hitId) },
            onLassoResult = { onLassoResult(pageIndex, it) },
            onLassoProgress = onLassoProgress,
            toNorm = toNorm,
        )
    }

private fun Offset.toSharedPdfNormPoint(pageSizePx: IntSize): PdfPagePoint {
    return PdfPagePoint(
        x = (x / pageSizePx.width.toFloat()).coerceIn(0f, 1f),
        y = (y / pageSizePx.height.toFloat()).coerceIn(0f, 1f),
    )
}

/**
 * Runs one handle-drag transform session. Internal (not private) so the
 * screen-level handle overlay — which owns all handle grabs in container
 * space — can reuse the exact session the pages used to run.
 */
internal suspend fun AwaitPointerEventScope.runSharedSelectionTransformSession(
    handle: SharedPdfSelectionHandle,
    selectionBounds: com.aryan.reader.shared.pdf.PdfPageBounds,
    startNorm: PdfPagePoint,
    downId: PointerId,
    pageAspectRatio: Float,
    onTransformStart: () -> Unit,
    onTransformUpdate: (SharedPdfSelectionTransform) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
    toNorm: (Offset) -> PdfPagePoint,
) {
    val pivotNorm = sharedPdfPivotForHandle(handle, selectionBounds)
    var cancelled = false
    var started = false
    var loggedFirstUpdate = false
    pdfInkSelectionLog {
        "transform.session handle=$handle pivot=(${pivotNorm.x.format3()},${pivotNorm.y.format3()}) " +
            "start=(${startNorm.x.format3()},${startNorm.y.format3()}) aspect=$pageAspectRatio"
    }
    try {
        do {
            val event = awaitPointerEvent()
            // Cancel only on a real second pressed finger (pinch intent,
            // same as the Android benchmark). iOS may deliver the previous
            // gesture's lift — or Pencil hover — inside a new gesture's
            // batches; those report unpressed and must not kill the session,
            // or rapid tap-then-drag/lasso intermittently dies on arrival.
            // The cancelling finger is consumed so a sibling page block
            // (separate coroutine, unlike Android's single stream) cannot
            // start a rival session with it.
            val rivals = event.changes.filter { it.id != downId && it.pressed }
            if (rivals.isNotEmpty()) {
                pdfInkSelectionLog { "transform.cancel handle=$handle rivals=${rivals.size}" }
                rivals.forEach { it.consume() }
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (change.positionChanged()) {
                val currentNorm = toNorm(change.position)
                if (!started) {
                    started = true
                    onTransformStart()
                }
                val transform = if (handle == SharedPdfSelectionHandle.ROTATE) {
                    SharedPdfSelectionTransform.Rotate(
                        centerX = pivotNorm.x,
                        centerY = pivotNorm.y,
                        angleDegrees = sharedPdfRotationForDrag(
                            pivotNorm.x, pivotNorm.y, startNorm, currentNorm, pageAspectRatio
                        ),
                    )
                } else if (handle.isEdgeHandle) {
                    val (scaleX, scaleY) = sharedPdfScaleXYForEdgeDrag(
                        handle, pivotNorm, startNorm, currentNorm
                    )
                    SharedPdfSelectionTransform.ScaleNonUniform(
                        pivotX = pivotNorm.x,
                        pivotY = pivotNorm.y,
                        scaleX = scaleX,
                        scaleY = scaleY,
                    )
                } else {
                    SharedPdfSelectionTransform.Scale(
                        pivotX = pivotNorm.x,
                        pivotY = pivotNorm.y,
                        scale = sharedPdfScaleForCornerDrag(pivotNorm, startNorm, currentNorm, pageAspectRatio),
                    )
                }
                onTransformUpdate(transform)
                if (!loggedFirstUpdate) {
                    loggedFirstUpdate = true
                    pdfInkSelectionLog { "transform.firstUpdate handle=$handle transform=$transform" }
                }
                change.consume()
            }
        } while (true)
    } finally {
        pdfInkSelectionLog { "transform.end handle=$handle started=$started cancelled=$cancelled" }
        if (started) {
            onTransformEnd(!cancelled)
        }
    }
}

private suspend fun AwaitPointerEventScope.runSharedTapOrMoveSession(
    hitId: String,
    startNorm: PdfPagePoint,
    downId: PointerId,
    touchSlopPx: Float,
    pageWidthPx: Float,
    zoomProvider: () -> Float,
    onTapResult: (String) -> Unit,
    onTransformStart: () -> Unit,
    onTransformUpdate: (SharedPdfSelectionTransform) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
    toNorm: (Offset) -> PdfPagePoint,
) {
    var moved = false
    var cancelled = false
    try {
        do {
            val event = awaitPointerEvent()
            // Cancel only on a real second pressed finger (pinch intent,
            // same as the Android benchmark). iOS may deliver the previous
            // gesture's lift — or Pencil hover — inside a new gesture's
            // batches; those report unpressed and must not kill the session,
            // or rapid tap-then-drag/lasso intermittently dies on arrival.
            // The cancelling finger is consumed so a sibling page block
            // (separate coroutine, unlike Android's single stream) cannot
            // start a rival session with it.
            val rivals = event.changes.filter { it.id != downId && it.pressed }
            if (rivals.isNotEmpty()) {
                pdfInkSelectionLog { "move.cancel hitId='$hitId' rivals=${rivals.size}" }
                rivals.forEach { it.consume() }
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (change.positionChanged()) {
                val currentNorm = toNorm(change.position)
                // Slop is measured in screen px; convert via page width * zoom.
                val zoom = zoomProvider().coerceAtLeast(0.01f)
                val slopNorm = touchSlopPx / (pageWidthPx.coerceAtLeast(1f) * zoom)
                val dx = currentNorm.x - startNorm.x
                val dy = currentNorm.y - startNorm.y
                if (!moved && (kotlin.math.abs(dx) > slopNorm || kotlin.math.abs(dy) > slopNorm)) {
                    moved = true
                    pdfInkSelectionLog {
                        "move.start hitId='$hitId' dx=${dx.format3()} dy=${dy.format3()} " +
                            "slopNorm=${slopNorm.format3()} touchSlopPx=$touchSlopPx pageWidthPx=$pageWidthPx zoom=$zoom"
                    }
                    onTransformStart()
                }
                if (moved) {
                    onTransformUpdate(SharedPdfSelectionTransform.Move(totalDx = dx, totalDy = dy))
                }
                change.consume()
            }
        } while (true)
    } finally {
        pdfInkSelectionLog { "move.end hitId='$hitId' moved=$moved cancelled=$cancelled" }
        if (cancelled) {
            if (moved) onTransformEnd(false)
        } else if (moved) {
            onTransformEnd(true)
        } else {
            onTapResult(hitId)
        }
    }
}

private suspend fun AwaitPointerEventScope.runSharedLassoSession(
    downId: PointerId,
    touchSlopPx: Float,
    /** Full finger slop (page px) for the tap re-hit when no lasso starts. */
    tapHitSlopPx: Float,
    pageAspectRatio: Float,
    zoomProvider: () -> Float,
    pageWidthPx: Float,
    pageAnnotations: List<SharedPdfAnnotation>,
    /** Tap id after a generous re-hit at the down point, or null to clear. */
    onTapResult: (String?) -> Unit,
    onLassoResult: (Set<String>) -> Unit,
    onLassoProgress: (List<Offset>?) -> Unit,
    toNorm: (Offset) -> PdfPagePoint,
) {
    val trail = mutableListOf<Offset>()
    var isLasso = false
    var cancelled = false
    var startScreen = Offset.Zero
    var hasStart = false
    // Pinch cancels the session, so zoom is fixed: the start zoom gives a
    // stable screen-px -> normalized tolerance for the touch rule.
    val startZoom = zoomProvider().coerceAtLeast(0.01f)
    val touchToleranceNorm = 14f / (pageWidthPx.coerceAtLeast(1f) * startZoom)
    try {
        do {
            val event = awaitPointerEvent()
            // Cancel only on a real second pressed finger (pinch intent,
            // same as the Android benchmark). iOS may deliver the previous
            // gesture's lift — or Pencil hover — inside a new gesture's
            // batches; those report unpressed and must not kill the session,
            // or rapid tap-then-drag/lasso intermittently dies on arrival.
            // The cancelling finger is consumed so a sibling page block
            // (separate coroutine, unlike Android's single stream) cannot
            // start a rival session with it.
            val rivals = event.changes.filter { it.id != downId && it.pressed }
            if (rivals.isNotEmpty()) {
                pdfInkSelectionLog { "lasso.cancel rivals=${rivals.size}" }
                rivals.forEach { it.consume() }
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (!hasStart) {
                startScreen = change.position
                trail.add(change.position)
                hasStart = true
            }
            if (change.positionChanged()) {
                val totalDx = change.position.x - startScreen.x
                val totalDy = change.position.y - startScreen.y
                if (!isLasso &&
                    (kotlin.math.abs(totalDx) > touchSlopPx || kotlin.math.abs(totalDy) > touchSlopPx)
                ) {
                    isLasso = true
                    pdfInkSelectionLog {
                        "lasso.start total=(${totalDx.roundToInt()},${totalDy.roundToInt()}) " +
                            "touchSlopPx=$touchSlopPx toleranceNorm=${touchToleranceNorm.format3()} " +
                            "pageWidthPx=$pageWidthPx startZoom=$startZoom"
                    }
                }
                if (isLasso) {
                    val last = trail.lastOrNull()
                    // Throttle: keep points ~4 screen px apart for cheap hit tests.
                    if (last == null || (change.position - last).getDistance() * startZoom > 4f) {
                        trail.add(change.position)
                        onLassoProgress(trail.toList())
                    }
                }
                change.consume()
            }
        } while (true)
    } finally {
        onLassoProgress(null)
        pdfInkSelectionLog {
            "lasso.end isLasso=$isLasso trail=${trail.size} cancelled=$cancelled annotations=${pageAnnotations.size}"
        }
        if (!cancelled) {
            if (isLasso && trail.size >= 2) {
                val lassoNorm = trail.map(toNorm)
                val ids = findSharedPdfLassoSelectionHits(
                    pageAnnotations, lassoNorm, touchToleranceNorm
                )
                pdfInkSelectionLog { "lasso.result ids=$ids" }
                onLassoResult(ids)
            } else if (!isLasso) {
                // Tap without a lasso: re-hit generously at the down point
                // (the dispatch hit above is tight so lassos can start near
                // ink). A near miss still selects a small stroke; only a true
                // empty tap clears.
                val downNorm = trail.firstOrNull()?.let(toNorm)
                val tapHit = downNorm?.let {
                    findSharedPdfTopmostSelectionHit(
                        annotations = pageAnnotations,
                        normX = it.x,
                        normY = it.y,
                        pageWidthPx = pageWidthPx,
                        pageAspectRatio = pageAspectRatio,
                        tapSlopPx = tapHitSlopPx,
                    )
                }
                pdfInkSelectionLog { "lasso.tapHit id=${tapHit?.id}" }
                onTapResult(tapHit?.id)
            }
        }
    }
}

/** Whether the SELECT tool draws ink: never (benchmark: `canDraw` excludes SELECT). */
internal fun PdfInkTool.isSharedPdfSelectionTool(): Boolean = this == PdfInkTool.SELECT

/**
 * Dedicated ink-selection diagnostics log with a single greppable tag.
 * Filter logcat / Xcode output for `PdfInkSelection` and attach it when
 * reporting that selection handles, drags, or lassos misbehave.
 */
private const val PDF_INK_SELECTION_LOG_TAG = "PdfInkSelection"
internal fun pdfInkSelectionLog(message: () -> String) {
    println("[$PDF_INK_SELECTION_LOG_TAG] ${message()}")
}

private fun Float.format3(): String {
    return ((this * 1000f).roundToInt() / 1000f).toString()
}

/**
 * One bundle threading SELECT state + callbacks from the reader screen down
 * through the page containers to [SharedMobilePdfPageSurface], so each layer
 * passes a single param. Preview/rotation/trail are read directly (cheap);
 * the gesture detector reads [selection] through a provider so in-flight
 * gestures survive live-preview recompositions.
 */
data class SharedPdfInkSelectionPageHost(
    val selection: SharedPdfInkSelection,
    val lassoPageIndex: Int?,
    val lassoTrail: List<Offset>,
    val previewById: Map<String, SharedPdfAnnotation>,
    val activeRotationDegrees: Float?,
    val onTap: (pageIndex: Int, annotationId: String?) -> Unit,
    val onLasso: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    val onLassoProgress: (pageIndex: Int, trail: List<Offset>?) -> Unit,
    val onTransformStart: (pageIndex: Int) -> Unit,
    val onTransformUpdate: (pageIndex: Int, transform: SharedPdfSelectionTransform, aspectRatio: Float) -> Unit,
    val onTransformEnd: (commit: Boolean) -> Unit,
    /**
     * True while the screen-level handle overlay runs a transform session;
     * pages read it (via updated-state) to stand down for that gesture.
     */
    val isHandleTransformInFlight: Boolean = false,
    /**
     * Selection page (if any) owned by the screen-level handle overlay: it
     * covers that page and runs the detector itself there, so the page's own
     * block stands down and each gesture is handled exactly once.
     */
    val overlayOwnedPageIndex: Int? = null,
)

/** Derived edit-bar inputs for the current selection (recomputed on change). */
data class SharedPdfSelectionEditState(
    val selected: List<SharedPdfAnnotation>,
    val union: com.aryan.reader.shared.pdf.PdfPageBounds?,
    val color: androidx.compose.ui.graphics.Color?,
    val thickness: Float?,
    val thicknessRange: ClosedFloatingPointRange<Float>,
)
