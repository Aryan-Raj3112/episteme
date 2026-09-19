package com.aryan.reader.shared.pdf

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared-first geometry for ink selection editing (tap-to-edit + lasso).
 *
 * Mobile (Android benchmark, iOS parity) implements selection UI on top of these
 * pure functions. All coordinates are normalized page coords (`PdfPagePoint.x/y`
 * in 0..1). Callers must apply the page aspect-ratio correction when mixing axes
 * (see [sharedPdfSnapHighlighterPoint] for the established pattern).
 */

/** Normalized bounding box of a stroke, or null when there are no points. */
fun pdfInkPointsBounds(points: List<PdfPagePoint>): PdfPageBounds? {
    if (points.isEmpty()) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (point in points) {
        if (point.x < left) left = point.x
        if (point.y < top) top = point.y
        if (point.x > right) right = point.x
        if (point.y > bottom) bottom = point.y
    }
    return PdfPageBounds(left, top, right, bottom)
}

/** Union of several stroke bounds, or null when empty. */
fun pdfInkUnionBounds(bounds: List<PdfPageBounds>): PdfPageBounds? {
    if (bounds.isEmpty()) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (box in bounds) {
        if (box.left < left) left = box.left
        if (box.top < top) top = box.top
        if (box.right > right) right = box.right
        if (box.bottom > bottom) bottom = box.bottom
    }
    return PdfPageBounds(left, top, right, bottom)
}

fun PdfPageBounds.centerX(): Float = (left + right) / 2f

fun PdfPageBounds.centerY(): Float = (top + bottom) / 2f

/** Translate every point by [dx]/[dy], clamped to the 0..1 page. */
fun List<PdfPagePoint>.movedPdfPointsBy(dx: Float, dy: Float): List<PdfPagePoint> {
    if (dx == 0f && dy == 0f) return this
    return map { point ->
        point.copy(
            x = (point.x + dx).coerceIn(0f, 1f),
            y = (point.y + dy).coerceIn(0f, 1f),
        )
    }
}

/**
 * Scale every point around ([pivotX], [pivotY]).
 *
 * Uniform scale (`scaleX == scaleY`) preserves the stroke shape and is what
 * corner handles produce. Non-uniform scale stretches. Results are clamped to
 * the 0..1 page.
 */
fun List<PdfPagePoint>.scaledPdfPointsAround(
    pivotX: Float,
    pivotY: Float,
    scaleX: Float,
    scaleY: Float,
): List<PdfPagePoint> {
    if (scaleX == 1f && scaleY == 1f) return this
    return map { point ->
        point.copy(
            x = (pivotX + (point.x - pivotX) * scaleX).coerceIn(0f, 1f),
            y = (pivotY + (point.y - pivotY) * scaleY).coerceIn(0f, 1f),
        )
    }
}

/**
 * Rotate every point around ([centerX], [centerY]) by [angleDegrees].
 *
 * [pageAspectRatio] (width/height) keeps the rotation visually correct in
 * normalized coords where x and y have different scales.
 */
fun List<PdfPagePoint>.rotatedPdfPointsAround(
    centerX: Float,
    centerY: Float,
    angleDegrees: Float,
    pageAspectRatio: Float = 1f,
): List<PdfPagePoint> {
    if (angleDegrees == 0f) return this
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val radians = angleDegrees * PI.toFloat() / 180f
    val cos = cos(radians)
    val sin = sin(radians)
    return map { point ->
        val dx = (point.x - centerX) * safeAspect
        val dy = point.y - centerY
        val rotatedX = dx * cos - dy * sin
        val rotatedY = dx * sin + dy * cos
        point.copy(
            x = (centerX + rotatedX / safeAspect).coerceIn(0f, 1f),
            y = (centerY + rotatedY).coerceIn(0f, 1f),
        )
    }
}

/** Uniform scale factor from a corner drag: current distance / start distance from the pivot. */fun pdfUniformScaleForCornerDrag(
    pivotX: Float,
    pivotY: Float,
    startX: Float,
    startY: Float,
    currentX: Float,
    currentY: Float,
    pageAspectRatio: Float = 1f,
): Float {
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val startDx = (startX - pivotX) * safeAspect
    val startDy = startY - pivotY
    val currentDx = (currentX - pivotX) * safeAspect
    val currentDy = currentY - pivotY
    val startDist = sqrt(startDx * startDx + startDy * startDy)
    if (startDist < 1e-6f) return 1f
    val currentDist = sqrt(currentDx * currentDx + currentDy * currentDy)
    return (currentDist / startDist).coerceIn(0.1f, 10f)
}

/** Angle in degrees of ([x], [y]) around ([centerX], [centerY]), corrected for aspect. */
fun pdfAngleAroundCenterDegrees(
    centerX: Float,
    centerY: Float,
    x: Float,
    y: Float,
    pageAspectRatio: Float = 1f,
): Float {
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val dx = (x - centerX) * safeAspect
    val dy = y - centerY
    return kotlin.math.atan2(dy, dx) * 180f / PI.toFloat()
}

/** Snap [angleDegrees] to [snapDegrees] increments when within [thresholdDegrees]. */
fun pdfSnappedRotationDegrees(
    angleDegrees: Float,
    snapDegrees: Float = 15f,
    thresholdDegrees: Float = 4f,
): Float {
    if (snapDegrees <= 0f) return angleDegrees
    val snapped = kotlin.math.round(angleDegrees / snapDegrees) * snapDegrees
    return if (abs(angleDegrees - snapped) <= thresholdDegrees) snapped else angleDegrees
}

/** Ray-casting point-in-polygon test on normalized coords. */
fun isPdfPointInPolygon(point: PdfPagePoint, polygon: List<PdfPagePoint>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        if ((yi > point.y) != (yj > point.y)) {
            val intersectX = (xj - xi) * (point.y - yi) / (yj - yi) + xi
            if (point.x < intersectX) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Fraction of [points] inside [polygon]. Lasso selection keeps strokes whose
 * fraction meets [containThreshold] (default majority = GoodNotes behavior).
 */
fun pdfLassoContainFraction(points: List<PdfPagePoint>, polygon: List<PdfPagePoint>): Float {
    if (points.isEmpty() || polygon.size < 3) return 0f
    var inside = 0
    for (point in points) {
        if (isPdfPointInPolygon(point, polygon)) inside++
    }
    return inside.toFloat() / points.size
}

/** Whether a stroke counts as lasso-selected under the contain rule. */
fun pdfIsLassoSelected(
    points: List<PdfPagePoint>,
    polygon: List<PdfPagePoint>,
    containThreshold: Float = 0.5f,
): Boolean {
    if (points.isEmpty() || polygon.size < 3) return false
    return pdfLassoContainFraction(points, polygon) >= containThreshold
}

/** Squared distance from point ([px], [py]) to segment a-b (aspect-corrected y via caller). */
fun pdfDistSqToSegment(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float {
    val bax = bx - ax
    val bay = by - ay
    val lenSq = (bax * bax + bay * bay).coerceAtLeast(1e-6f)
    val t = (((px - ax) * bax + (py - ay) * bay) / lenSq).coerceIn(0f, 1f)
    val dx = px - (ax + bax * t)
    val dy = py - (ay + bay * t)
    return dx * dx + dy * dy
}

/**
 * Tap hit-test for a stroke in normalized coords.
 *
 * Mirrors `SharedPdfInkRenderer.isInkAnnotationHit` threshold semantics:
 * [tapSlopNorm] is the finger slop plus [strokeWidthNorm]/2. Aspect-correct
 * via [pageAspectRatio].
 */
fun pdfIsStrokeTapHit(
    points: List<PdfPagePoint>,
    hitX: Float,
    hitY: Float,
    tapSlopNorm: Float,
    strokeWidthNorm: Float,
    pageAspectRatio: Float = 1f,
): Boolean {
    if (points.isEmpty()) return false
    val safeAspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val threshold = tapSlopNorm + strokeWidthNorm / 2f
    val thresholdSq = threshold * threshold
    val hy = hitY / safeAspect
    if (points.size == 1) {
        val p = points.first()
        val dx = p.x - hitX
        val dy = p.y / safeAspect - hy
        return dx * dx + dy * dy < thresholdSq
    }
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        if (pdfDistSqToSegment(hitX, hy, a.x, a.y / safeAspect, b.x, b.y / safeAspect) < thresholdSq) {
            return true
        }
    }
    return false
}

/**
 * Clamp a selection move so [bounds] — the gesture-start union bounds of the
 * selection — stays inside the 0..1 page. Clamping the delta instead of every
 * moved point keeps the stroke shape intact when the user over-drags past a
 * page edge: the selection slides to the edge and stops instead of smushing
 * against it. The selection stays on its page of origin by design.
 */
fun pdfClampedMoveDelta(bounds: PdfPageBounds, dx: Float, dy: Float): Pair<Float, Float> {
    return dx.coerceIn(-bounds.left, 1f - bounds.right) to
        dy.coerceIn(-bounds.top, 1f - bounds.bottom)
}

/**
 * Cap a uniform [scale] around ([pivotX], [pivotY]) so [bounds] stays inside
 * the 0..1 page. Only growth is capped — shrinking toward the pivot is always
 * in-bounds — and pass-through drags (non-positive scale) keep their existing
 * behavior.
 */
fun pdfCappedUniformScale(
    pivotX: Float,
    pivotY: Float,
    bounds: PdfPageBounds,
    scale: Float,
): Float {
    var maxScale = Float.MAX_VALUE
    val rightSpan = bounds.right - pivotX
    if (rightSpan > 0f) maxScale = minOf(maxScale, (1f - pivotX) / rightSpan)
    val leftSpan = bounds.left - pivotX
    if (leftSpan < 0f) maxScale = minOf(maxScale, (0f - pivotX) / leftSpan)
    val bottomSpan = bounds.bottom - pivotY
    if (bottomSpan > 0f) maxScale = minOf(maxScale, (1f - pivotY) / bottomSpan)
    val topSpan = bounds.top - pivotY
    if (topSpan < 0f) maxScale = minOf(maxScale, (0f - pivotY) / topSpan)
    return scale.coerceAtMost(maxScale)
}
