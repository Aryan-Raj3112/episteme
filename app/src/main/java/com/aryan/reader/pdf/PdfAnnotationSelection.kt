// PdfAnnotationSelection.kt
//
// Ink selection editing: tap-to-select, bounding box + handles, move / scale /
// rotate transforms, lasso multi-select, style editing, duplicate-in-place.
//
// Geometry lives shared-first in SharedPdfSelectionGeometry.kt; this file adapts
// it to the Android PdfAnnotation model and hosts selection session helpers.
package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.movedPdfPointsBy
import com.aryan.reader.shared.pdf.pdfAngleAroundCenterDegrees
import com.aryan.reader.shared.pdf.pdfCappedNonUniformScale
import com.aryan.reader.shared.pdf.pdfCappedUniformScale
import com.aryan.reader.shared.pdf.pdfClampedMoveDelta
import com.aryan.reader.shared.pdf.pdfDistSqToSegment
import com.aryan.reader.shared.pdf.pdfInkPointsBounds
import com.aryan.reader.shared.pdf.pdfInkUnionBounds
import com.aryan.reader.shared.pdf.pdfIsStrokeTapHit
import com.aryan.reader.shared.pdf.pdfIsStrokeTouchedByPolyline
import com.aryan.reader.shared.pdf.pdfSnappedRotationDegrees
import com.aryan.reader.shared.pdf.pdfUniformScaleForCornerDrag
import com.aryan.reader.shared.pdf.rotatedPdfPointsAround
import com.aryan.reader.shared.pdf.scaledPdfPointsAround
import kotlin.math.abs
import kotlin.math.sqrt

/** Corner + mid-side + rotate handles of the selection bounding box. */
enum class PdfSelectionHandle {
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
internal val PdfSelectionHandle.isEdgeHandle: Boolean
    get() = this == PdfSelectionHandle.TOP_MIDDLE ||
        this == PdfSelectionHandle.BOTTOM_MIDDLE ||
        this == PdfSelectionHandle.LEFT_MIDDLE ||
        this == PdfSelectionHandle.RIGHT_MIDDLE

/** Absolute transform recomputed from the gesture-start snapshot (no drift). */
sealed interface PdfSelectionTransform {
    data class Move(val totalDx: Float, val totalDy: Float) : PdfSelectionTransform
    data class Scale(val pivotX: Float, val pivotY: Float, val scale: Float) : PdfSelectionTransform
    data class ScaleNonUniform(
        val pivotX: Float,
        val pivotY: Float,
        val scaleX: Float,
        val scaleY: Float,
    ) : PdfSelectionTransform
    data class Rotate(val centerX: Float, val centerY: Float, val angleDegrees: Float) : PdfSelectionTransform
}

/** Current ink selection: ids on a single page (mobile scope). */
data class PdfInkSelection(
    val pageIndex: Int? = null,
    val selectedIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = pageIndex == null || selectedIds.isEmpty()
}

internal fun PdfPoint.toShared(): PdfPagePoint = PdfPagePoint(x, y, timestamp)

internal fun PdfPagePoint.toAndroid(): PdfPoint = PdfPoint(x, y, timestamp)

/** Normalized bounds of an ink annotation, or null when it has no points. */
fun pdfSelectionBoundsOf(annotation: PdfAnnotation): Rect? {
    val bounds = pdfInkPointsBounds(annotation.points.map { it.toShared() }) ?: return null
    return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
}

/** Union bounds of the given annotations in normalized page coords. */
fun pdfSelectionUnionBounds(annotations: List<PdfAnnotation>): Rect? {
    val boxes = annotations.mapNotNull { annotation ->
        pdfInkPointsBounds(annotation.points.map { it.toShared() })
    }
    val union = pdfInkUnionBounds(boxes) ?: return null
    return Rect(union.left, union.top, union.right, union.bottom)
}

fun PdfAnnotation.movedSelectionBy(dx: Float, dy: Float): PdfAnnotation {
    if (dx == 0f && dy == 0f) return this
    return copy(points = points.map { it.toShared() }.movedPdfPointsBy(dx, dy).map { it.toAndroid() })
}

fun PdfAnnotation.scaledSelectionAround(pivotX: Float, pivotY: Float, scale: Float): PdfAnnotation {
    if (scale == 1f) return this
    return copy(
        points = points.map { it.toShared() }
            .scaledPdfPointsAround(pivotX, pivotY, scale, scale)
            .map { it.toAndroid() },
        strokeWidth = (strokeWidth * scale).coerceIn(
            pdfSelectionStrokeWidthRangeFor(inkType).start,
            pdfSelectionStrokeWidthRangeFor(inkType).endInclusive,
        ),
    )
}

/**
 * One-axis stretch from a mid-side handle. Stroke width follows the geometric
 * mean: a pure horizontal 2x stretch widens strokes ~1.4x instead of leaving
 * them hairline or doubling them.
 */
fun PdfAnnotation.scaledSelectionAroundXY(
    pivotX: Float,
    pivotY: Float,
    scaleX: Float,
    scaleY: Float,
): PdfAnnotation {
    if (scaleX == 1f && scaleY == 1f) return this
    val widthFactor = sqrt(scaleX * scaleY)
    return copy(
        points = points.map { it.toShared() }
            .scaledPdfPointsAround(pivotX, pivotY, scaleX, scaleY)
            .map { it.toAndroid() },
        strokeWidth = (strokeWidth * widthFactor).coerceIn(
            pdfSelectionStrokeWidthRangeFor(inkType).start,
            pdfSelectionStrokeWidthRangeFor(inkType).endInclusive,
        ),
    )
}

fun PdfAnnotation.rotatedSelectionAround(
    centerX: Float,
    centerY: Float,
    angleDegrees: Float,
    pageAspectRatio: Float,
): PdfAnnotation {
    if (angleDegrees == 0f) return this
    return copy(
        points = points.map { it.toShared() }
            .rotatedPdfPointsAround(centerX, centerY, angleDegrees, pageAspectRatio)
            .map { it.toAndroid() }
    )
}

/** Apply an absolute [transform] to every annotation whose id is in [ids]. */
fun applyPdfSelectionTransform(
    annotations: List<PdfAnnotation>,
    ids: Set<String>,
    transform: PdfSelectionTransform,
    pageAspectRatio: Float,
): List<PdfAnnotation> {
    if (ids.isEmpty()) return annotations
    // Clamp against the gesture-start union bounds so over-dragging past a
    // page edge stops the selection at the edge instead of smushing points.
    val union = pdfSelectionUnionBounds(annotations.filter { it.id in ids })
    val unionBounds = union?.let {
        PdfPageBounds(it.left, it.top, it.right, it.bottom)
    }
    val resolved: PdfSelectionTransform = when (transform) {
        is PdfSelectionTransform.Move -> {
            if (unionBounds != null) {
                val (dx, dy) = pdfClampedMoveDelta(
                    unionBounds, transform.totalDx, transform.totalDy
                )
                PdfSelectionTransform.Move(dx, dy)
            } else {
                transform
            }
        }
        is PdfSelectionTransform.Scale -> {
            if (unionBounds != null) {
                transform.copy(
                    scale = pdfCappedUniformScale(
                        transform.pivotX, transform.pivotY, unionBounds, transform.scale
                    )
                )
            } else {
                transform
            }
        }
        is PdfSelectionTransform.ScaleNonUniform -> {
            if (unionBounds != null) {
                val (scaleX, scaleY) = pdfCappedNonUniformScale(
                    transform.pivotX, transform.pivotY, unionBounds,
                    transform.scaleX, transform.scaleY,
                )
                transform.copy(scaleX = scaleX, scaleY = scaleY)
            } else {
                transform
            }
        }
        is PdfSelectionTransform.Rotate -> transform
    }
    return annotations.map { annotation ->
        if (annotation.id !in ids) return@map annotation
        when (resolved) {
            is PdfSelectionTransform.Move -> annotation.movedSelectionBy(resolved.totalDx, resolved.totalDy)
            is PdfSelectionTransform.Scale -> annotation.scaledSelectionAround(
                resolved.pivotX, resolved.pivotY, resolved.scale
            )
            is PdfSelectionTransform.ScaleNonUniform -> annotation.scaledSelectionAroundXY(
                resolved.pivotX, resolved.pivotY, resolved.scaleX, resolved.scaleY
            )
            is PdfSelectionTransform.Rotate -> annotation.rotatedSelectionAround(
                resolved.centerX, resolved.centerY, resolved.angleDegrees, pageAspectRatio
            )
        }
    }
}

private fun pdfSelectionStrokeWidthRangeFor(inkType: InkType): ClosedFloatingPointRange<Float> {
    return when (inkType) {
        InkType.HIGHLIGHTER, InkType.HIGHLIGHTER_ROUND -> 0.01f..0.06f
        InkType.ERASER -> 0.002f..0.10f
        else -> 0.001f..0.015f
    }
}

/**
 * Topmost (last-drawn) annotation hit by a tap in normalized coords.
 *
 * [tapSlopPx]/[pageWidthPx] give finger tolerance; highlighters use their
 * bounds for a generous target like the eraser does for ink segments.
 */
fun findPdfTopmostSelectionHit(
    annotations: List<PdfAnnotation>,
    normX: Float,
    normY: Float,
    pageWidthPx: Float,
    pageAspectRatio: Float,
    tapSlopPx: Float = 24f,
): PdfAnnotation? {
    if (annotations.isEmpty()) return null
    val safeWidth = pageWidthPx.coerceAtLeast(1f)
    val tapSlopNorm = tapSlopPx / safeWidth
    for (index in annotations.indices.reversed()) {
        val annotation = annotations[index]
        if (annotation.points.isEmpty()) continue
        if (annotation.inkType == InkType.HIGHLIGHTER || annotation.inkType == InkType.HIGHLIGHTER_ROUND) {
            val bounds = pdfSelectionBoundsOf(annotation) ?: continue
            val slop = tapSlopNorm + annotation.strokeWidth / 2f
            if (normX in (bounds.left - slop)..(bounds.right + slop) &&
                normY in (bounds.top - slop)..(bounds.bottom + slop)
            ) {
                return annotation
            }
        } else if (pdfIsStrokeTapHit(
                points = annotation.points.map { it.toShared() },
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
fun findPdfLassoSelectionHits(
    annotations: List<PdfAnnotation>,
    lassoNormPoints: List<PdfPoint>,
    touchToleranceNorm: Float = 0.02f,
): Set<String> {
    if (lassoNormPoints.size < 2) return emptySet()
    val polyline = lassoNormPoints.map { it.toShared() }
    return annotations.filter { annotation ->
        annotation.points.isNotEmpty() &&
            pdfIsStrokeTouchedByPolyline(
                points = annotation.points.map { it.toShared() },
                polyline = polyline,
                toleranceNorm = touchToleranceNorm,
                strokeWidthNorm = annotation.strokeWidth,
            )
    }.map { it.id }.toSet()
}

/** Handle centers in normalized page coords for [bounds] (rotate floats above). */
fun pdfSelectionHandlePositions(bounds: Rect, rotateOffsetNorm: Float = 0.06f): Map<PdfSelectionHandle, PdfPoint> {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    return mapOf(
        PdfSelectionHandle.TOP_LEFT to PdfPoint(bounds.left, bounds.top),
        PdfSelectionHandle.TOP_RIGHT to PdfPoint(bounds.right, bounds.top),
        PdfSelectionHandle.BOTTOM_LEFT to PdfPoint(bounds.left, bounds.bottom),
        PdfSelectionHandle.BOTTOM_RIGHT to PdfPoint(bounds.right, bounds.bottom),
        PdfSelectionHandle.TOP_MIDDLE to PdfPoint(centerX, bounds.top),
        PdfSelectionHandle.BOTTOM_MIDDLE to PdfPoint(centerX, bounds.bottom),
        PdfSelectionHandle.LEFT_MIDDLE to PdfPoint(bounds.left, centerY),
        PdfSelectionHandle.RIGHT_MIDDLE to PdfPoint(bounds.right, centerY),
        PdfSelectionHandle.ROTATE to PdfPoint(centerX, bounds.top - rotateOffsetNorm),
    )
}

/** Opposite corner/middle pivot for a handle drag. */
fun pdfPivotForHandle(handle: PdfSelectionHandle, bounds: Rect): PdfPoint {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    return when (handle) {
        PdfSelectionHandle.TOP_LEFT -> PdfPoint(bounds.right, bounds.bottom)
        PdfSelectionHandle.TOP_RIGHT -> PdfPoint(bounds.left, bounds.bottom)
        PdfSelectionHandle.BOTTOM_LEFT -> PdfPoint(bounds.right, bounds.top)
        PdfSelectionHandle.BOTTOM_RIGHT -> PdfPoint(bounds.left, bounds.top)
        PdfSelectionHandle.TOP_MIDDLE -> PdfPoint(centerX, bounds.bottom)
        PdfSelectionHandle.BOTTOM_MIDDLE -> PdfPoint(centerX, bounds.top)
        PdfSelectionHandle.LEFT_MIDDLE -> PdfPoint(bounds.right, centerY)
        PdfSelectionHandle.RIGHT_MIDDLE -> PdfPoint(bounds.left, centerY)
        PdfSelectionHandle.ROTATE -> PdfPoint(centerX, centerY)
    }
}

/** Which handle (if any) is within [touchSlopPx] of the tap, in screen px space. */
fun findPdfSelectionHandleHit(
    handleScreenPositions: Map<PdfSelectionHandle, androidx.compose.ui.geometry.Offset>,
    tapScreen: androidx.compose.ui.geometry.Offset,
    touchSlopPx: Float,
): PdfSelectionHandle? {
    val slopSq = touchSlopPx * touchSlopPx
    var best: PdfSelectionHandle? = null
    var bestSq = Float.MAX_VALUE
    for ((handle, position) in handleScreenPositions) {
        val dx = position.x - tapScreen.x
        val dy = position.y - tapScreen.y
        val distSq = dx * dx + dy * dy
        if (distSq <= slopSq && distSq < bestSq) {
            bestSq = distSq
            best = handle
        }
    }
    return best
}

/** Uniform scale for a corner drag from gesture-start to current (normalized). */
fun pdfScaleForCornerDrag(
    pivot: PdfPoint,
    startNorm: PdfPoint,
    currentNorm: PdfPoint,
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
fun pdfScaleXYForEdgeDrag(
    handle: PdfSelectionHandle,
    pivot: PdfPoint,
    startNorm: PdfPoint,
    currentNorm: PdfPoint,
): Pair<Float, Float> {
    fun axisScale(start: Float, current: Float, pivotValue: Float): Float {
        val span = start - pivotValue
        if (abs(span) < 1e-6f) return 1f
        return ((current - pivotValue) / span).coerceIn(0.1f, 10f)
    }
    return when (handle) {
        PdfSelectionHandle.LEFT_MIDDLE,
        PdfSelectionHandle.RIGHT_MIDDLE,
        -> axisScale(startNorm.x, currentNorm.x, pivot.x) to 1f
        PdfSelectionHandle.TOP_MIDDLE,
        PdfSelectionHandle.BOTTOM_MIDDLE,
        -> 1f to axisScale(startNorm.y, currentNorm.y, pivot.y)
        else -> 1f to 1f
    }
}

/**
 * Snapped rotation delta for a rotate-handle drag. Snaps to the cardinals
 * (0/90/180/270) within a 10-degree window — e.g. 85..95 settles on 90 —
 * and stays free everywhere else.
 */
fun pdfRotationForDrag(
    centerX: Float,
    centerY: Float,
    startNorm: PdfPoint,
    currentNorm: PdfPoint,
    pageAspectRatio: Float,
): Float {
    val startAngle = pdfAngleAroundCenterDegrees(centerX, centerY, startNorm.x, startNorm.y, pageAspectRatio)
    val currentAngle = pdfAngleAroundCenterDegrees(centerX, centerY, currentNorm.x, currentNorm.y, pageAspectRatio)
    var delta = currentAngle - startAngle
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return pdfSnappedRotationDegrees(delta, snapDegrees = 90f, thresholdDegrees = 10f)
}

/** Aspect-corrected distance check: is ([x],[y]) within [radiusNorm] of segment a-b. */
internal fun pdfPointNearSegmentNorm(
    x: Float,
    y: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    radiusNorm: Float,
    pageAspectRatio: Float,
): Boolean {
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    return pdfDistSqToSegment(x, y / safeAspect, ax, ay / safeAspect, bx, by / safeAspect) <
        radiusNorm * radiusNorm
}

internal fun pdfStrokeLengthNorm(points: List<PdfPoint>, pageAspectRatio: Float): Float {
    if (points.size < 2) return 0f
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    var total = 0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = (points[i].y - points[i - 1].y) / safeAspect
        total += sqrt(dx * dx + dy * dy)
    }
    return total
}
