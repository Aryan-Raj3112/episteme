package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface SharedPdfInkRenderData {
    data class Standard(
        val path: Path,
        val color: Color,
        val strokeWidthPx: Float,
        val cap: StrokeCap,
        val blendMode: BlendMode
    ) : SharedPdfInkRenderData

    data class Fountain(
        val path: Path,
        val color: Color
    ) : SharedPdfInkRenderData

    data class Pencil(
        val path: Path,
        val color: Color,
        val strokeWidthPx: Float,
        val velocityAlpha: Float
    ) : SharedPdfInkRenderData
}

object SharedPdfInkRenderer {
    fun createRenderData(
        annotation: SharedPdfAnnotation,
        canvasSize: IntSize
    ): SharedPdfInkRenderData? {
        if (annotation.kind != PdfAnnotationKind.INK || annotation.points.isEmpty()) return null
        val widthPx = canvasSize.width.coerceAtLeast(1).toFloat()
        val heightPx = canvasSize.height.coerceAtLeast(1).toFloat()
        val strokeWidthPx = effectiveStrokeWidthPx(annotation.strokeWidth, widthPx)
        val color = Color(annotation.colorArgb)

        if (annotation.points.size == 1) {
            val point = annotation.points.first()
            val x = point.x * widthPx
            val y = point.y * heightPx
            return when (annotation.tool) {
                PdfInkTool.FOUNTAIN_PEN -> {
                    val path = Path().apply {
                        addOval(Rect(center = Offset(x, y), radius = strokeWidthPx / 2f))
                    }
                    SharedPdfInkRenderData.Fountain(path = path, color = color)
                }
                PdfInkTool.PENCIL -> {
                    val path = Path().apply {
                        moveTo(x, y)
                        lineTo(x, y)
                    }
                    SharedPdfInkRenderData.Pencil(
                        path = path,
                        color = color,
                        strokeWidthPx = strokeWidthPx,
                        velocityAlpha = 1f
                    )
                }
                else -> {
                    val path = Path().apply {
                        moveTo(x, y)
                        lineTo(x, y)
                    }
                    SharedPdfInkRenderData.Standard(
                        path = path,
                        color = color,
                        strokeWidthPx = strokeWidthPx,
                        cap = annotation.tool.strokeCap,
                        blendMode = annotation.tool.blendMode
                    )
                }
            }
        }

        return when (annotation.tool) {
            PdfInkTool.PENCIL -> {
                val path = annotation.points.toSmoothPath(widthPx, heightPx)
                val velocityAlpha = annotation.points.velocityAlpha(widthPx, heightPx)
                SharedPdfInkRenderData.Pencil(
                    path = path,
                    color = color,
                    strokeWidthPx = strokeWidthPx,
                    velocityAlpha = velocityAlpha
                )
            }
            PdfInkTool.FOUNTAIN_PEN -> {
                val (leftSide, rightSide) = calculateFountainPenEdges(
                    points = annotation.points,
                    baseWidthPx = strokeWidthPx,
                    pageWidthPx = widthPx,
                    pageHeightPx = heightPx
                )
                val path = Path()
                if (leftSide.isNotEmpty()) {
                    path.moveTo(leftSide.first().x, leftSide.first().y)
                    leftSide.drop(1).forEach { path.lineTo(it.x, it.y) }
                    rightSide.asReversed().forEach { path.lineTo(it.x, it.y) }
                    path.close()
                }
                SharedPdfInkRenderData.Fountain(path = path, color = color)
            }
            PdfInkTool.PEN,
            PdfInkTool.HIGHLIGHTER,
            PdfInkTool.HIGHLIGHTER_ROUND,
            PdfInkTool.ERASER,
            PdfInkTool.TEXT -> {
                SharedPdfInkRenderData.Standard(
                    path = annotation.points.toSmoothPath(widthPx, heightPx),
                    color = color,
                    strokeWidthPx = strokeWidthPx,
                    cap = annotation.tool.strokeCap,
                    blendMode = annotation.tool.blendMode
                )
            }
        }
    }

    fun effectiveStrokeWidthPx(strokeWidth: Float, canvasSize: IntSize): Float {
        return effectiveStrokeWidthPx(strokeWidth, canvasSize.width.coerceAtLeast(1).toFloat())
    }

    fun effectiveStrokeWidthPx(strokeWidth: Float, pageWidthPx: Float): Float {
        val safeWidth = pageWidthPx.coerceAtLeast(1f)
        return if (strokeWidth <= 1f) {
            (strokeWidth * safeWidth).coerceAtLeast(0.1f)
        } else {
            strokeWidth.coerceAtLeast(0.1f)
        }
    }

    fun effectiveStrokeWidthNorm(strokeWidth: Float, pageWidthPx: Float): Float {
        val safeWidth = pageWidthPx.coerceAtLeast(1f)
        return if (strokeWidth <= 1f) strokeWidth.coerceAtLeast(0.0001f) else strokeWidth / safeWidth
    }

    fun calculateSnappedPoint(
        currentPoint: PdfPagePoint,
        startPoint: PdfPagePoint?,
        pageAspectRatio: Float,
        thresholdDegrees: Double = 10.0
    ): PdfPagePoint {
        if (startPoint == null) return currentPoint
        val safeAspectRatio = pageAspectRatio.takeIf { it > 0f } ?: 1f
        val dx = (currentPoint.x - startPoint.x) * safeAspectRatio
        val dy = currentPoint.y - startPoint.y
        val angleDeg = atan2(dy, dx) * 180 / PI
        val absAngle = abs(angleDeg)
        val isHorizontal = absAngle < thresholdDegrees || abs(absAngle - 180.0) < thresholdDegrees
        val isVertical = abs(absAngle - 90.0) < thresholdDegrees
        return when {
            isHorizontal -> currentPoint.copy(y = startPoint.y)
            isVertical -> currentPoint.copy(x = startPoint.x)
            else -> currentPoint
        }
    }

    fun isAnnotationHit(
        annotation: SharedPdfAnnotation,
        hitPoint: PdfPagePoint,
        pageWidthPx: Float,
        pageAspectRatio: Float,
        eraserStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth,
        lastHitPoint: PdfPagePoint? = null
    ): Boolean {
        return when (annotation.kind) {
            PdfAnnotationKind.HIGHLIGHT,
            PdfAnnotationKind.TEXT -> annotation.allBounds().any { it.contains(hitPoint.x, hitPoint.y) }
            PdfAnnotationKind.INK -> isInkAnnotationHit(
                annotation = annotation,
                hitPoint = hitPoint,
                pageWidthPx = pageWidthPx,
                pageAspectRatio = pageAspectRatio,
                eraserStrokeWidth = eraserStrokeWidth,
                lastHitPoint = lastHitPoint
            )
        }
    }

    private fun isInkAnnotationHit(
        annotation: SharedPdfAnnotation,
        hitPoint: PdfPagePoint,
        pageWidthPx: Float,
        pageAspectRatio: Float,
        eraserStrokeWidth: Float,
        lastHitPoint: PdfPagePoint?
    ): Boolean {
        if (annotation.points.isEmpty()) return false
        val safeAspectRatio = pageAspectRatio.takeIf { it > 0f } ?: 1f
        val eraserWidthNorm = effectiveStrokeWidthNorm(eraserStrokeWidth, pageWidthPx)
        val annotationWidthNorm = effectiveStrokeWidthNorm(annotation.strokeWidth, pageWidthPx)
        val threshold = eraserWidthNorm + annotationWidthNorm / 2f
        val thresholdSq = threshold * threshold

        fun distSqToEraser(px: Float, pyScaled: Float): Float {
            val e1x = hitPoint.x
            val e1yScaled = hitPoint.y / safeAspectRatio
            if (lastHitPoint == null) {
                val dx = px - e1x
                val dy = pyScaled - e1yScaled
                return dx * dx + dy * dy
            }

            val e0x = lastHitPoint.x
            val e0yScaled = lastHitPoint.y / safeAspectRatio
            val ex = e1x - e0x
            val ey = e1yScaled - e0yScaled
            val segmentLenSq = ex * ex + ey * ey
            if (segmentLenSq < 1e-8f) {
                val dx = px - e1x
                val dy = pyScaled - e1yScaled
                return dx * dx + dy * dy
            }

            val t = ((px - e0x) * ex + (pyScaled - e0yScaled) * ey) / segmentLenSq
            val closestX = e0x + ex * t.coerceIn(0f, 1f)
            val closestY = e0yScaled + ey * t.coerceIn(0f, 1f)
            val dx = px - closestX
            val dy = pyScaled - closestY
            return dx * dx + dy * dy
        }

        if (annotation.points.size == 1) {
            val p = annotation.points.first()
            return distSqToEraser(p.x, p.y / safeAspectRatio) < thresholdSq
        }

        for (i in 0 until annotation.points.lastIndex) {
            val a = annotation.points[i]
            val b = annotation.points[i + 1]
            val pax = hitPoint.x - a.x
            val pay = (hitPoint.y - a.y) / safeAspectRatio
            val bax = b.x - a.x
            val bay = (b.y - a.y) / safeAspectRatio
            val segmentLenSq = (bax * bax + bay * bay).coerceAtLeast(1e-6f)
            val t = ((pax * bax + pay * bay) / segmentLenSq).coerceIn(0f, 1f)
            val closestX = bax * t
            val closestY = bay * t
            val dx = pax - closestX
            val dy = pay - closestY
            if (dx * dx + dy * dy < thresholdSq) return true

            if (lastHitPoint != null) {
                if (distSqToEraser(a.x, a.y / safeAspectRatio) < thresholdSq) return true
                if (distSqToEraser(b.x, b.y / safeAspectRatio) < thresholdSq) return true
            }
        }
        return false
    }

    fun calculateFountainPenEdges(
        points: List<PdfPagePoint>,
        baseWidthPx: Float,
        pageWidthPx: Float,
        pageHeightPx: Float
    ): Pair<List<Offset>, List<Offset>> {
        if (points.size < 2) return emptyList<Offset>() to emptyList<Offset>()

        val leftSide = mutableListOf<Offset>()
        val rightSide = mutableListOf<Offset>()
        val computedWidths = FloatArray(points.size)
        computedWidths[0] = baseWidthPx
        val velocityFactor = 300f

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val aspect = if (pageWidthPx > 0f && pageHeightPx > 0f) pageHeightPx / pageWidthPx else 1f
            val scaledDy = dy * aspect
            val distNorm = sqrt(dx * dx + scaledDy * scaledDy)
            val timeDelta = (p1.timestamp - p0.timestamp).coerceAtLeast(1)
            val velocityNorm = distNorm / timeDelta
            val targetWidth = (baseWidthPx * (1f / (1f + velocityNorm * velocityFactor))).coerceIn(
                baseWidthPx * 0.2f,
                baseWidthPx * 1.4f
            )
            computedWidths[i] = computedWidths[i - 1] * 0.6f + targetWidth * 0.4f
        }

        for (i in 0 until points.lastIndex) {
            val current = points[i]
            val next = points[i + 1]
            val currentX = current.x * pageWidthPx
            val currentY = current.y * pageHeightPx
            val nextX = next.x * pageWidthPx
            val nextY = next.y * pageHeightPx
            val angle = atan2(nextY - currentY, nextX - currentX)
            val normalAngle = angle - (PI / 2f).toFloat()
            val halfWidth = computedWidths[i] / 2f
            leftSide += Offset(
                x = currentX + cos(normalAngle) * halfWidth,
                y = currentY + sin(normalAngle) * halfWidth
            )
            rightSide += Offset(
                x = currentX - cos(normalAngle) * halfWidth,
                y = currentY - sin(normalAngle) * halfWidth
            )
        }

        val last = points.last()
        val previous = points[points.lastIndex - 1]
        val lastX = last.x * pageWidthPx
        val lastY = last.y * pageHeightPx
        val previousX = previous.x * pageWidthPx
        val previousY = previous.y * pageHeightPx
        val lastAngle = atan2(lastY - previousY, lastX - previousX)
        val lastNormal = lastAngle - (PI / 2f).toFloat()
        val lastHalfWidth = computedWidths.last() / 2f
        leftSide += Offset(
            x = lastX + cos(lastNormal) * lastHalfWidth,
            y = lastY + sin(lastNormal) * lastHalfWidth
        )
        rightSide += Offset(
            x = lastX - cos(lastNormal) * lastHalfWidth,
            y = lastY - sin(lastNormal) * lastHalfWidth
        )
        return leftSide to rightSide
    }
}

fun PdfInkTool.sharedPdfStrokeWidthRange(): ClosedFloatingPointRange<Float> {
    return when (this) {
        PdfInkTool.HIGHLIGHTER,
        PdfInkTool.HIGHLIGHTER_ROUND -> 0.01f..0.06f
        PdfInkTool.ERASER -> 0.002f..0.10f
        PdfInkTool.TEXT -> 0.01f..0.08f
        PdfInkTool.PEN,
        PdfInkTool.FOUNTAIN_PEN,
        PdfInkTool.PENCIL -> 0.001f..0.015f
    }
}

fun Float.sharedPdfStrokePercent(range: ClosedFloatingPointRange<Float>): Int {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    return (((this - range.start) / span) * 100f).toInt().coerceIn(1, 100)
}

private val PdfInkTool.strokeCap: StrokeCap
    get() = when (this) {
        PdfInkTool.HIGHLIGHTER -> StrokeCap.Butt
        PdfInkTool.HIGHLIGHTER_ROUND -> StrokeCap.Round
        else -> StrokeCap.Round
    }

private val PdfInkTool.blendMode: BlendMode
    get() = when (this) {
        PdfInkTool.HIGHLIGHTER,
        PdfInkTool.HIGHLIGHTER_ROUND -> BlendMode.Multiply
        else -> BlendMode.SrcOver
    }

private fun PdfPageBounds.contains(x: Float, y: Float): Boolean {
    return x in left..right && y in top..bottom
}

private fun SharedPdfAnnotation.allBounds(): List<PdfPageBounds> {
    return boundsList.ifEmpty { listOfNotNull(bounds) }
}

private fun List<PdfPagePoint>.toSmoothPath(widthPx: Float, heightPx: Float): Path {
    val path = Path()
    val first = first()
    path.moveTo(first.x * widthPx, first.y * heightPx)
    for (i in 1 until size) {
        val p0 = this[i - 1]
        val p1 = this[i]
        val p0x = p0.x * widthPx
        val p0y = p0.y * heightPx
        val p1x = p1.x * widthPx
        val p1y = p1.y * heightPx
        val midX = (p0x + p1x) / 2f
        val midY = (p0y + p1y) / 2f
        if (i == 1) {
            path.lineTo(midX, midY)
        } else {
            path.quadraticTo(p0x, p0y, midX, midY)
        }
    }
    val last = last()
    path.lineTo(last.x * widthPx, last.y * heightPx)
    return path
}

private fun List<PdfPagePoint>.velocityAlpha(widthPx: Float, heightPx: Float): Float {
    if (size < 2) return 1f
    var totalDistance = 0f
    for (i in 1 until size) {
        val p0 = this[i - 1]
        val p1 = this[i]
        val dx = (p1.x - p0.x) * widthPx
        val dy = (p1.y - p0.y) * heightPx
        totalDistance += sqrt(dx * dx + dy * dy)
    }
    val duration = (last().timestamp - first().timestamp).coerceAtLeast(1)
    val velocity = totalDistance / duration
    return (1f - (velocity - 0.2f) / 1.8f).coerceIn(0.4f, 1f)
}
