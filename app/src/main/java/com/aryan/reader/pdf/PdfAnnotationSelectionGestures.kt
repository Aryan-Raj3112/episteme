// PdfAnnotationSelectionGestures.kt
//
// Single-finger selection state machine for the SELECT tool (vertical reader).
// Runs inside the same arbitration as drawing: single finger selects / moves /
// lassos, multi-finger pans + zooms. Overlay state is kept in document px;
// annotation math stays in normalized page coords.
package com.aryan.reader.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChanged
import com.aryan.reader.pdf.data.PdfAnnotation

internal data class PdfSelectionCamera(val zoom: Float, val panX: Float, val panY: Float)

private fun PdfSelectionCamera.toDoc(screen: Offset): Offset {
    val zoom = zoom.coerceAtLeast(0.01f)
    return Offset((screen.x - panX) / zoom, (screen.y - panY) / zoom)
}

private fun PdfSelectionCamera.toScreen(doc: Offset): Offset {
    return Offset(doc.x * zoom + panX, doc.y * zoom + panY)
}

private fun List<PdfPageLayout>.pageAtDocY(docY: Float): PdfPageLayout? {
    return firstOrNull { page -> docY >= page.y && docY <= (page.y + page.height) }
}

private fun docToNorm(page: PdfPageLayout, doc: Offset): PdfPoint {
    if (page.width <= 0f || page.height <= 0f) return PdfPoint(0f, 0f)
    return PdfPoint(
        x = (doc.x / page.width).coerceIn(0f, 1f),
        y = ((doc.y - page.y) / page.height).coerceIn(0f, 1f),
    )
}

private fun normToDoc(page: PdfPageLayout, norm: PdfPoint): Offset {
    return Offset(norm.x * page.width, page.y + norm.y * page.height)
}

/**
 * Handles the SELECT gesture stream: tap-to-select, drag-to-move, corner /
 * rotate handles, and freeform lasso. Multi-touch cancels the active session
 * (transforms revert, lassos discard) so pinch-zoom always wins.
 */
internal suspend fun PointerInputScope.detectPdfInkSelectionGestures(
    layoutInfo: List<PdfPageLayout>,
    cameraProvider: () -> PdfSelectionCamera,
    touchSlopPx: Float,
    handleSlopPx: Float,
    isStylusOnlyMode: Boolean,
    selection: PdfInkSelection,
    annotationsProvider: (pageIndex: Int) -> List<PdfAnnotation>,
    pageAspectProvider: (pageIndex: Int) -> Float,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onLassoResult: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    onLassoProgress: (docPoints: List<Offset>?) -> Unit,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: PdfSelectionTransform) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (isStylusOnlyMode && down.type == PointerType.Touch) {
            return@awaitEachGesture
        }
        val camera = cameraProvider()
        val downDoc = camera.toDoc(down.position)
        val page = layoutInfo.pageAtDocY(downDoc.y) ?: return@awaitEachGesture
        val aspect = pageAspectProvider(page.index)
        val startNorm = docToNorm(page, downDoc)
        val pageAnnotations = annotationsProvider(page.index)

        // 1. Handles of the active selection win over everything.
        val selectionBounds = if (selection.pageIndex == page.index && !selection.isEmpty) {
            val selected = pageAnnotations.filter { it.id in selection.selectedIds }
            pdfSelectionUnionBounds(selected)
        } else {
            null
        }
        if (selectionBounds != null) {
            val handleDocs = pdfSelectionHandlePositions(selectionBounds).mapValues { (_, norm) ->
                normToDoc(page, norm)
            }
            val handleScreens = handleDocs.mapValues { (_, doc) -> camera.toScreen(doc) }
            val hitHandle = findPdfSelectionHandleHit(handleScreens, down.position, handleSlopPx)
            if (hitHandle != null) {
                runSelectionTransformSession(
                    page = page,
                    handle = hitHandle,
                    selectionBounds = selectionBounds,
                    startNorm = startNorm,
                    downId = down.id,
                    cameraProvider = cameraProvider,
                    pageAspectProvider = pageAspectProvider,
                    onTransformStart = onTransformStart,
                    onTransformUpdate = onTransformUpdate,
                    onTransformEnd = onTransformEnd,
                )
                return@awaitEachGesture
            }
        }

        // 2. Stroke hit: tap selects, drag moves.
        val hit = findPdfTopmostSelectionHit(
            annotations = pageAnnotations,
            normX = startNorm.x,
            normY = startNorm.y,
            pageWidthPx = page.width.coerceAtLeast(1f),
            pageAspectRatio = aspect,
            tapSlopPx = touchSlopPx,
        )
        if (hit != null) {
            runTapOrMoveSession(
                page = page,
                hitId = hit.id,
                startNorm = startNorm,
                downId = down.id,
                touchSlopPx = touchSlopPx,
                cameraProvider = cameraProvider,
                onTapResult = onTapResult,
                onTransformStart = onTransformStart,
                onTransformUpdate = onTransformUpdate,
                onTransformEnd = onTransformEnd,
            )
            return@awaitEachGesture
        }

        // 3. Empty space: tap clears, drag lassos.
        runLassoSession(
            page = page,
            downId = down.id,
            touchSlopPx = touchSlopPx,
            cameraProvider = cameraProvider,
            layoutInfo = layoutInfo,
            pageAnnotations = pageAnnotations,
            onTapResult = onTapResult,
            onLassoResult = onLassoResult,
            onLassoProgress = onLassoProgress,
        )
    }
}

private suspend fun AwaitPointerEventScope.runSelectionTransformSession(
    page: PdfPageLayout,
    handle: PdfSelectionHandle,
    selectionBounds: androidx.compose.ui.geometry.Rect,
    startNorm: PdfPoint,
    downId: androidx.compose.ui.input.pointer.PointerId,
    cameraProvider: () -> PdfSelectionCamera,
    pageAspectProvider: (pageIndex: Int) -> Float,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: PdfSelectionTransform) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
) {
    val pivotNorm = pdfPivotForHandle(handle, selectionBounds).let { PdfPoint(it.x, it.y) }
    val aspect = pageAspectProvider(page.index)
    var cancelled = false
    var started = false
    try {
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (change.positionChanged()) {
                val doc = cameraProvider().toDoc(change.position)
                val currentNorm = docToNorm(page, doc)
                if (!started) {
                    started = true
                    onTransformStart(page.index)
                }
                val transform = if (handle == PdfSelectionHandle.ROTATE) {
                    PdfSelectionTransform.Rotate(
                        centerX = pivotNorm.x,
                        centerY = pivotNorm.y,
                        angleDegrees = pdfRotationForDrag(
                            pivotNorm.x, pivotNorm.y, startNorm, currentNorm, aspect
                        ),
                    )
                } else {
                    PdfSelectionTransform.Scale(
                        pivotX = pivotNorm.x,
                        pivotY = pivotNorm.y,
                        scale = pdfScaleForCornerDrag(pivotNorm, startNorm, currentNorm, aspect),
                    )
                }
                onTransformUpdate(page.index, transform)
                change.consume()
            }
        } while (true)
    } finally {
        if (started) {
            onTransformEnd(!cancelled)
        }
    }
}

private suspend fun AwaitPointerEventScope.runTapOrMoveSession(
    page: PdfPageLayout,
    hitId: String,
    startNorm: PdfPoint,
    downId: androidx.compose.ui.input.pointer.PointerId,
    touchSlopPx: Float,
    cameraProvider: () -> PdfSelectionCamera,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onTransformStart: (pageIndex: Int) -> Unit,
    onTransformUpdate: (pageIndex: Int, transform: PdfSelectionTransform) -> Unit,
    onTransformEnd: (commit: Boolean) -> Unit,
) {
    var moved = false
    var cancelled = false
    try {
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (change.positionChanged()) {
                val doc = cameraProvider().toDoc(change.position)
                val currentNorm = docToNorm(page, doc)
                // Slop is measured in screen px; convert via page width * zoom.
                val camera = cameraProvider()
                val slopNorm = if (page.width > 0f) {
                    touchSlopPx / (page.width * camera.zoom.coerceAtLeast(0.01f))
                } else {
                    0.02f
                }
                val dx = currentNorm.x - startNorm.x
                val dy = currentNorm.y - startNorm.y
                if (!moved && (kotlin.math.abs(dx) > slopNorm || kotlin.math.abs(dy) > slopNorm)) {
                    moved = true
                    onTransformStart(page.index)
                }
                if (moved) {
                    onTransformUpdate(
                        page.index,
                        PdfSelectionTransform.Move(totalDx = dx, totalDy = dy),
                    )
                }
                change.consume()
            }
        } while (true)
    } finally {
        if (cancelled) {
            if (moved) onTransformEnd(false)
        } else if (moved) {
            onTransformEnd(true)
        } else {
            onTapResult(page.index, hitId)
        }
    }
}

private suspend fun AwaitPointerEventScope.runLassoSession(
    page: PdfPageLayout,
    downId: androidx.compose.ui.input.pointer.PointerId,
    touchSlopPx: Float,
    cameraProvider: () -> PdfSelectionCamera,
    layoutInfo: List<PdfPageLayout>,
    pageAnnotations: List<PdfAnnotation>,
    onTapResult: (pageIndex: Int, annotationId: String?) -> Unit,
    onLassoResult: (pageIndex: Int, annotationIds: Set<String>) -> Unit,
    onLassoProgress: (docPoints: List<Offset>?) -> Unit,
) {
    val trail = mutableListOf<Offset>()
    var isLasso = false
    var cancelled = false
    var startScreen = Offset.Zero
    var hasStart = false
    try {
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                cancelled = true
                break
            }
            val change = event.changes.firstOrNull { it.id == downId }
            if (change == null || !change.pressed) break
            if (!hasStart) {
                startScreen = change.position
                trail.add(cameraProvider().toDoc(change.position))
                hasStart = true
            }
            if (change.positionChanged()) {
                val camera = cameraProvider()
                val totalDx = change.position.x - startScreen.x
                val totalDy = change.position.y - startScreen.y
                if (!isLasso &&
                    (kotlin.math.abs(totalDx) > touchSlopPx || kotlin.math.abs(totalDy) > touchSlopPx)
                ) {
                    isLasso = true
                }
                if (isLasso) {
                    val doc = camera.toDoc(change.position)
                    val last = trail.lastOrNull()
                    // Throttle: keep points ~4 screen px apart for cheap hit tests.
                    if (last == null || (doc - last).getDistance() * camera.zoom > 4f) {
                        trail.add(doc)
                        onLassoProgress(trail.toList())
                    }
                }
                change.consume()
            }
        } while (true)
    } finally {
        onLassoProgress(null)
        if (!cancelled) {
            if (isLasso && trail.size >= 3) {
                val lassoNorm = trail.map { docToNorm(page, it) }
                    .map { PdfPoint(it.x, it.y) }
                val ids = findPdfLassoSelectionHits(pageAnnotations, lassoNorm)
                onLassoResult(page.index, ids)
            } else if (!isLasso) {
                onTapResult(page.index, null)
            }
        }
    }
}
