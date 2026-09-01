package com.aryan.reader.shared.pdf

import kotlin.math.abs
import kotlin.math.pow

const val PDF_MIN_ZOOM_SCALE = 1f
const val PDF_MAX_ZOOM_SCALE = 10f
const val PDF_DOUBLE_TAP_ZOOM_SCALE = 2.5f
const val PDF_ZOOM_RENDER_SETTLE_MILLIS = 120L

data class PdfZoomPoint(val x: Float, val y: Float) {
    operator fun plus(other: PdfZoomPoint) = PdfZoomPoint(x + other.x, y + other.y)
    operator fun minus(other: PdfZoomPoint) = PdfZoomPoint(x - other.x, y - other.y)
    operator fun times(value: Float) = PdfZoomPoint(x * value, y * value)
}

data class PdfZoomSize(val width: Float, val height: Float)

fun finitePdfZoomValue(value: Float, fallback: Float = 0f): Float =
    value.takeIf { it.isFinite() } ?: fallback

fun shouldResetPdfZoomForOrientationChange(
    previousViewport: PdfZoomSize?,
    currentViewport: PdfZoomSize,
    isPaginated: Boolean,
): Boolean {
    if (!isPaginated || previousViewport == null) return false
    if (previousViewport.width <= 0f || previousViewport.height <= 0f) return false
    if (currentViewport.width <= 0f || currentViewport.height <= 0f) return false
    return (previousViewport.width > previousViewport.height) !=
        (currentViewport.width > currentViewport.height)
}

data class PdfZoomCamera(
    val scale: Float = 1f,
    val offset: PdfZoomPoint = PdfZoomPoint(0f, 0f)
) {
    fun normalized(
        viewport: PdfZoomSize,
        content: PdfZoomSize,
        minScale: Float = PDF_MIN_ZOOM_SCALE,
        maxScale: Float = PDF_MAX_ZOOM_SCALE
    ): PdfZoomCamera {
        val safeScale = finitePdfZoomValue(scale, minScale).coerceIn(minScale, maxScale)
        if (safeScale <= minScale + PdfZoomEpsilon) return PdfZoomCamera(minScale)
        val maxX = ((content.width * safeScale) - viewport.width).coerceAtLeast(0f) / 2f
        val maxY = ((content.height * safeScale) - viewport.height).coerceAtLeast(0f) / 2f
        return PdfZoomCamera(
            safeScale,
            PdfZoomPoint(
                finitePdfZoomValue(offset.x).coerceIn(-maxX, maxX),
                finitePdfZoomValue(offset.y).coerceIn(-maxY, maxY)
            )
        )
    }

    fun transformed(
        zoomChange: Float,
        panChange: PdfZoomPoint,
        pivot: PdfZoomPoint,
        viewport: PdfZoomSize,
        content: PdfZoomSize,
        minScale: Float = PDF_MIN_ZOOM_SCALE,
        maxScale: Float = PDF_MAX_ZOOM_SCALE
    ): PdfZoomCamera {
        val oldScale = scale.takeIf { it.isFinite() && it > 0f } ?: minScale
        val nextScale = (oldScale * zoomChange).coerceIn(minScale, maxScale)
        val ratio = nextScale / oldScale
        val center = PdfZoomPoint(viewport.width / 2f, viewport.height / 2f)
        return PdfZoomCamera(
            scale = nextScale,
            offset = offset * ratio + (pivot - center) * (1f - ratio) + panChange
        ).normalized(viewport, content, minScale, maxScale)
    }
}

fun pdfOneHandZoomScale(
    startScale: Float,
    totalDragY: Float,
    dragDistanceForDoublePx: Float,
    minScale: Float = PDF_MIN_ZOOM_SCALE,
    maxScale: Float = PDF_MAX_ZOOM_SCALE
): Float {
    val safeStart = startScale.takeIf { it.isFinite() && it > 0f } ?: minScale
    val safeDistance = dragDistanceForDoublePx.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (safeStart * 2f.pow(totalDragY / safeDistance)).coerceIn(minScale, maxScale)
}

fun pdfDoubleTapTargetScale(scale: Float): Float =
    if (scale > 1.1f) PDF_MIN_ZOOM_SCALE else PDF_DOUBLE_TAP_ZOOM_SCALE

fun PdfZoomCamera.isZoomed(minScale: Float = 1f): Boolean = scale > minScale + PdfZoomEpsilon

fun visiblePdfPageBounds(
    camera: PdfZoomCamera,
    transformedPageLeft: Float,
    transformedPageTop: Float,
    transformedPageRight: Float,
    transformedPageBottom: Float,
    viewportLeft: Float,
    viewportTop: Float,
    viewportRight: Float,
    viewportBottom: Float
): PdfPageBounds? {
    val scale = camera.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val centerX = (viewportLeft + viewportRight) / 2f
    val centerY = (viewportTop + viewportBottom) / 2f
    fun inverseX(value: Float) = centerX + (value - centerX - camera.offset.x) / scale
    fun inverseY(value: Float) = centerY + (value - centerY - camera.offset.y) / scale
    val baseLeft = inverseX(transformedPageLeft)
    val baseTop = inverseY(transformedPageTop)
    val baseRight = inverseX(transformedPageRight)
    val baseBottom = inverseY(transformedPageBottom)
    val visibleLeft = maxOf(baseLeft, inverseX(viewportLeft))
    val visibleTop = maxOf(baseTop, inverseY(viewportTop))
    val visibleRight = minOf(baseRight, inverseX(viewportRight))
    val visibleBottom = minOf(baseBottom, inverseY(viewportBottom))
    val width = baseRight - baseLeft
    val height = baseBottom - baseTop
    if (width <= 0f || height <= 0f || visibleRight <= visibleLeft || visibleBottom <= visibleTop) return null
    return PdfPageBounds(
        left = ((visibleLeft - baseLeft) / width).coerceIn(0f, 1f),
        top = ((visibleTop - baseTop) / height).coerceIn(0f, 1f),
        right = ((visibleRight - baseLeft) / width).coerceIn(0f, 1f),
        bottom = ((visibleBottom - baseTop) / height).coerceIn(0f, 1f)
    )
}

private const val PdfZoomEpsilon = 0.001f
