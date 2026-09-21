package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shared-first ink selection editing: tap-to-select, bounding box + handles,
 * move / scale / rotate transforms, lasso multi-select, style editing,
 * duplicate-in-place.
 *
 * Benchmark: `app/.../pdf/PdfAnnotationSelection.kt` (Android-only stack).
 * This file adapts the same logic to the shared [SharedPdfAnnotation] model
 * and normalized page coords ([PdfPagePoint] in 0..1) so every mobile
 * platform (Android benchmark, iOS parity) implements selection UI on top of
 * identical math. Pure geometry lives in [SharedPdfSelectionGeometry.kt].
 */

/** Corner + mid-side + rotate handles of the selection bounding box. */
enum class SharedPdfSelectionHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_MIDDLE,
    BOTTOM_MIDDLE,
    LEFT_MIDDLE,
    RIGHT_MIDDLE,
    ROTATE,
}

/** Mid-side handles stretch one axis; corners scale uniformly. */
val SharedPdfSelectionHandle.isEdgeHandle: Boolean
    get() = this == SharedPdfSelectionHandle.TOP_MIDDLE ||
        this == SharedPdfSelectionHandle.BOTTOM_MIDDLE ||
        this == SharedPdfSelectionHandle.LEFT_MIDDLE ||
        this == SharedPdfSelectionHandle.RIGHT_MIDDLE

/** Absolute transform recomputed from the gesture-start snapshot (no drift). */
sealed interface SharedPdfSelectionTransform {
    data class Move(val totalDx: Float, val totalDy: Float) : SharedPdfSelectionTransform
    data class Scale(val pivotX: Float, val pivotY: Float, val scale: Float) : SharedPdfSelectionTransform
    data class ScaleNonUniform(
        val pivotX: Float,
        val pivotY: Float,
        val scaleX: Float,
        val scaleY: Float,
    ) : SharedPdfSelectionTransform
    data class Rotate(val centerX: Float, val centerY: Float, val angleDegrees: Float) : SharedPdfSelectionTransform
}

/** Current ink selection: ids on a single page (mobile scope). */
data class SharedPdfInkSelection(
    val pageIndex: Int? = null,
    val selectedIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = pageIndex == null || selectedIds.isEmpty()
}

/** Normalized bounds of an ink annotation, or null when it has no points. */
fun sharedPdfSelectionBoundsOf(annotation: SharedPdfAnnotation): PdfPageBounds? =
    pdfInkPointsBounds(annotation.points)

/** Union bounds of the given annotations in normalized page coords. */
fun sharedPdfSelectionUnionBounds(annotations: List<SharedPdfAnnotation>): PdfPageBounds? =
    pdfInkUnionBounds(annotations.mapNotNull { pdfInkPointsBounds(it.points) })

fun SharedPdfAnnotation.movedSharedSelectionBy(dx: Float, dy: Float): SharedPdfAnnotation {
    if (dx == 0f && dy == 0f) return this
    return copy(points = points.movedPdfPointsBy(dx, dy))
}

fun SharedPdfAnnotation.scaledSharedSelectionAround(
    pivotX: Float,
    pivotY: Float,
    scale: Float,
): SharedPdfAnnotation {
    if (scale == 1f) return this
    val range = sharedPdfSelectionStrokeWidthRangeFor(tool)
    return copy(
        points = points.scaledPdfPointsAround(pivotX, pivotY, scale, scale),
        strokeWidth = (strokeWidth * scale).coerceIn(range.start, range.endInclusive),
    )
}

/**
 * One-axis stretch from a mid-side handle. Stroke width follows the geometric
 * mean: a pure horizontal 2x stretch widens strokes ~1.4x instead of leaving
 * them hairline or doubling them.
 */
fun SharedPdfAnnotation.scaledSharedSelectionAroundXY(
    pivotX: Float,
    pivotY: Float,
    scaleX: Float,
    scaleY: Float,
): SharedPdfAnnotation {
    if (scaleX == 1f && scaleY == 1f) return this
    val range = sharedPdfSelectionStrokeWidthRangeFor(tool)
    val widthFactor = sqrt(scaleX * scaleY)
    return copy(
        points = points.scaledPdfPointsAround(pivotX, pivotY, scaleX, scaleY),
        strokeWidth = (strokeWidth * widthFactor).coerceIn(range.start, range.endInclusive),
    )
}

fun SharedPdfAnnotation.rotatedSharedSelectionAround(
    centerX: Float,
    centerY: Float,
    angleDegrees: Float,
    pageAspectRatio: Float,
): SharedPdfAnnotation {
    if (angleDegrees == 0f) return this
    return copy(points = points.rotatedPdfPointsAround(centerX, centerY, angleDegrees, pageAspectRatio))
}

/** Apply an absolute [transform] to every annotation whose id is in [ids]. */
fun applySharedPdfSelectionTransform(
    annotations: List<SharedPdfAnnotation>,
    ids: Set<String>,
    transform: SharedPdfSelectionTransform,
    pageAspectRatio: Float,
): List<SharedPdfAnnotation> {
    if (ids.isEmpty()) return annotations
    // Clamp against the gesture-start union bounds so over-dragging past a
    // page edge stops the selection at the edge instead of smushing points.
    val union = sharedPdfSelectionUnionBounds(annotations.filter { it.id in ids })
    val resolved: SharedPdfSelectionTransform = when (transform) {
        is SharedPdfSelectionTransform.Move -> {
            if (union != null) {
                val (dx, dy) = pdfClampedMoveDelta(union, transform.totalDx, transform.totalDy)
                SharedPdfSelectionTransform.Move(dx, dy)
            } else {
                transform
            }
        }
        is SharedPdfSelectionTransform.Scale -> {
            if (union != null) {
                transform.copy(
                    scale = pdfCappedUniformScale(transform.pivotX, transform.pivotY, union, transform.scale)
                )
            } else {
                transform
            }
        }
        is SharedPdfSelectionTransform.ScaleNonUniform -> {
            if (union != null) {
                val (scaleX, scaleY) = pdfCappedNonUniformScale(
                    transform.pivotX, transform.pivotY, union,
                    transform.scaleX, transform.scaleY,
                )
                transform.copy(scaleX = scaleX, scaleY = scaleY)
            } else {
                transform
            }
        }
        is SharedPdfSelectionTransform.Rotate -> transform
    }
    return annotations.map { annotation ->
        if (annotation.id !in ids) return@map annotation
        when (resolved) {
            is SharedPdfSelectionTransform.Move ->
                annotation.movedSharedSelectionBy(resolved.totalDx, resolved.totalDy)
            is SharedPdfSelectionTransform.Scale ->
                annotation.scaledSharedSelectionAround(resolved.pivotX, resolved.pivotY, resolved.scale)
            is SharedPdfSelectionTransform.ScaleNonUniform ->
                annotation.scaledSharedSelectionAroundXY(
                    resolved.pivotX, resolved.pivotY, resolved.scaleX, resolved.scaleY
                )
            is SharedPdfSelectionTransform.Rotate ->
                annotation.rotatedSharedSelectionAround(
                    resolved.centerX, resolved.centerY, resolved.angleDegrees, pageAspectRatio
                )
        }
    }
}

/** Stroke-width clamp range per tool (benchmark: Android `pdfSelectionStrokeWidthRangeFor`). */
fun sharedPdfSelectionStrokeWidthRangeFor(tool: PdfInkTool): ClosedFloatingPointRange<Float> {
    return when (tool) {
        PdfInkTool.HIGHLIGHTER, PdfInkTool.HIGHLIGHTER_ROUND -> 0.01f..0.06f
        PdfInkTool.ERASER -> 0.002f..0.10f
        PdfInkTool.TEXT -> 0.01f..0.08f
        else -> 0.001f..0.015f
    }
}

/**
 * Topmost (last-drawn) ink annotation hit by a tap in normalized coords.
 *
 * [tapSlopPx]/[pageWidthPx] give finger tolerance; highlighters use their
 * bounds for a generous target like the eraser does for ink segments.
 */
fun findSharedPdfTopmostSelectionHit(
    annotations: List<SharedPdfAnnotation>,
    normX: Float,
    normY: Float,
    pageWidthPx: Float,
    pageAspectRatio: Float,
    tapSlopPx: Float = 24f,
): SharedPdfAnnotation? {
    if (annotations.isEmpty()) return null
    val safeWidth = pageWidthPx.coerceAtLeast(1f)
    val tapSlopNorm = tapSlopPx / safeWidth
    for (index in annotations.indices.reversed()) {
        val annotation = annotations[index]
        if (annotation.kind != PdfAnnotationKind.INK || annotation.points.isEmpty()) continue
        if (annotation.tool == PdfInkTool.HIGHLIGHTER || annotation.tool == PdfInkTool.HIGHLIGHTER_ROUND) {
            val bounds = sharedPdfSelectionBoundsOf(annotation) ?: continue
            val slop = tapSlopNorm + annotation.strokeWidth / 2f
            if (normX in (bounds.left - slop)..(bounds.right + slop) &&
                normY in (bounds.top - slop)..(bounds.bottom + slop)
            ) {
                return annotation
            }
        } else if (pdfIsStrokeTapHit(
                points = annotation.points,
                hitX = normX,
                hitY = normY,
                tapSlopNorm = tapSlopNorm,
                strokeWidthNorm = annotation.strokeWidth,
                pageAspectRatio = pageAspectRatio,
            )
        ) {
            return annotation
        }
    }
    return null
}

/**
 * Ids of annotations touched by the lasso polyline. The lasso is an open
 * freehand line (never auto-closed): any stroke the line touches — within
 * [touchToleranceNorm] plus half its width — is selected, like Samsung Notes.
 */
fun findSharedPdfLassoSelectionHits(
    annotations: List<SharedPdfAnnotation>,
    lassoNormPoints: List<PdfPagePoint>,
    touchToleranceNorm: Float = 0.02f,
): Set<String> {
    if (lassoNormPoints.size < 2) return emptySet()
    return annotations.filter { annotation ->
        annotation.kind == PdfAnnotationKind.INK &&
            annotation.points.isNotEmpty() &&
            pdfIsStrokeTouchedByPolyline(
                points = annotation.points,
                polyline = lassoNormPoints,
                toleranceNorm = touchToleranceNorm,
                strokeWidthNorm = annotation.strokeWidth,
            )
    }.map { it.id }.toSet()
}

/** Handle centers in normalized page coords for [bounds] (rotate floats above). */
fun sharedPdfSelectionHandlePositions(
    bounds: PdfPageBounds,
    rotateOffsetNorm: Float = 0.06f,
): Map<SharedPdfSelectionHandle, PdfPagePoint> {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    return mapOf(
        SharedPdfSelectionHandle.TOP_LEFT to PdfPagePoint(bounds.left, bounds.top),
        SharedPdfSelectionHandle.TOP_RIGHT to PdfPagePoint(bounds.right, bounds.top),
        SharedPdfSelectionHandle.BOTTOM_LEFT to PdfPagePoint(bounds.left, bounds.bottom),
        SharedPdfSelectionHandle.BOTTOM_RIGHT to PdfPagePoint(bounds.right, bounds.bottom),
        SharedPdfSelectionHandle.TOP_MIDDLE to PdfPagePoint(centerX, bounds.top),
        SharedPdfSelectionHandle.BOTTOM_MIDDLE to PdfPagePoint(centerX, bounds.bottom),
        SharedPdfSelectionHandle.LEFT_MIDDLE to PdfPagePoint(bounds.left, centerY),
        SharedPdfSelectionHandle.RIGHT_MIDDLE to PdfPagePoint(bounds.right, centerY),
        SharedPdfSelectionHandle.ROTATE to PdfPagePoint(centerX, bounds.top - rotateOffsetNorm),
    )
}

/** Opposite corner/middle pivot for a handle drag. */
fun sharedPdfPivotForHandle(handle: SharedPdfSelectionHandle, bounds: PdfPageBounds): PdfPagePoint {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    return when (handle) {
        SharedPdfSelectionHandle.TOP_LEFT -> PdfPagePoint(bounds.right, bounds.bottom)
        SharedPdfSelectionHandle.TOP_RIGHT -> PdfPagePoint(bounds.left, bounds.bottom)
        SharedPdfSelectionHandle.BOTTOM_LEFT -> PdfPagePoint(bounds.right, bounds.top)
        SharedPdfSelectionHandle.BOTTOM_RIGHT -> PdfPagePoint(bounds.left, bounds.top)
        SharedPdfSelectionHandle.TOP_MIDDLE -> PdfPagePoint(centerX, bounds.bottom)
        SharedPdfSelectionHandle.BOTTOM_MIDDLE -> PdfPagePoint(centerX, bounds.top)
        SharedPdfSelectionHandle.LEFT_MIDDLE -> PdfPagePoint(bounds.right, centerY)
        SharedPdfSelectionHandle.RIGHT_MIDDLE -> PdfPagePoint(bounds.left, centerY)
        SharedPdfSelectionHandle.ROTATE -> PdfPagePoint(centerX, centerY)
    }
}

/** Which handle (if any) is within [touchSlopPx] of the tap, in page px space. */
fun findSharedPdfSelectionHandleHit(
    handlePagePositions: Map<SharedPdfSelectionHandle, Offset>,
    tapPage: Offset,
    touchSlopPx: Float,
): SharedPdfSelectionHandle? {
    val slopSq = touchSlopPx * touchSlopPx
    var best: SharedPdfSelectionHandle? = null
    var bestSq = Float.MAX_VALUE
    for ((handle, position) in handlePagePositions) {
        val dx = position.x - tapPage.x
        val dy = position.y - tapPage.y
        val distSq = dx * dx + dy * dy
        if (distSq <= slopSq && distSq < bestSq) {
            bestSq = distSq
            best = handle
        }
    }
    return best
}

/** Uniform scale for a corner drag from gesture-start to current (normalized). */
fun sharedPdfScaleForCornerDrag(
    pivot: PdfPagePoint,
    startNorm: PdfPagePoint,
    currentNorm: PdfPagePoint,
    pageAspectRatio: Float,
): Float = pdfUniformScaleForCornerDrag(
    pivotX = pivot.x,
    pivotY = pivot.y,
    startX = startNorm.x,
    startY = startNorm.y,
    currentX = currentNorm.x,
    currentY = currentNorm.y,
    pageAspectRatio = pageAspectRatio,
)

/**
 * One-axis stretch for a mid-side handle drag: the dragged axis scales by the
 * finger's distance ratio from the pivot, the other axis stays at 1.
 * Degenerate spans (zero-size selections) hold at 1 instead of exploding.
 */
fun sharedPdfScaleXYForEdgeDrag(
    handle: SharedPdfSelectionHandle,
    pivot: PdfPagePoint,
    startNorm: PdfPagePoint,
    currentNorm: PdfPagePoint,
): Pair<Float, Float> {
    fun axisScale(start: Float, current: Float, pivotValue: Float): Float {
        val span = start - pivotValue
        if (abs(span) < 1e-6f) return 1f
        return ((current - pivotValue) / span).coerceIn(0.1f, 10f)
    }
    return when (handle) {
        SharedPdfSelectionHandle.LEFT_MIDDLE,
        SharedPdfSelectionHandle.RIGHT_MIDDLE,
        -> axisScale(startNorm.x, currentNorm.x, pivot.x) to 1f
        SharedPdfSelectionHandle.TOP_MIDDLE,
        SharedPdfSelectionHandle.BOTTOM_MIDDLE,
        -> 1f to axisScale(startNorm.y, currentNorm.y, pivot.y)
        else -> 1f to 1f
    }
}

/**
 * Snapped rotation delta for a rotate-handle drag. Snaps to the cardinals
 * (0/90/180/270) within a 5-degree window — e.g. 85..95 settles on 90 —
 * and stays free everywhere else.
 */
fun sharedPdfRotationForDrag(
    centerX: Float,
    centerY: Float,
    startNorm: PdfPagePoint,
    currentNorm: PdfPagePoint,
    pageAspectRatio: Float,
): Float {
    val startAngle = pdfAngleAroundCenterDegrees(centerX, centerY, startNorm.x, startNorm.y, pageAspectRatio)
    val currentAngle =
        pdfAngleAroundCenterDegrees(centerX, centerY, currentNorm.x, currentNorm.y, pageAspectRatio)
    var delta = currentAngle - startAngle
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return pdfSnappedRotationDegrees(delta, snapDegrees = 90f, thresholdDegrees = 5f)
}

/** Pill display angle: normalized to 0..359, so -90 reads 270 and 360 reads 0. */
fun sharedPdfNormalizeRotationDisplay(angleDegrees: Float): Int =
    ((angleDegrees.roundToInt() % 360) + 360) % 360
