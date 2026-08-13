package com.aryan.reader.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val PDF_BUBBLE_PREFETCH_RADIUS = 1
const val PDF_MAX_DRAW_BITMAP_BYTES = 64L * 1024L * 1024L
const val PDF_MAX_DRAW_BITMAP_DIMENSION_PX = 4096

fun buildPdfBubblePrefetchOrder(
    currentPage: Int,
    totalPages: Int,
    radius: Int = PDF_BUBBLE_PREFETCH_RADIUS,
): List<Int> {
    if (totalPages <= 0 || radius < 0) return emptyList()
    val current = currentPage.coerceIn(0, totalPages - 1)
    val ordered = LinkedHashSet<Int>()
    ordered += current
    for (distance in 1..radius) {
        val next = current + distance
        val previous = current - distance
        if (next in 0 until totalPages) ordered += next
        if (previous in 0 until totalPages) ordered += previous
    }
    return ordered.toList()
}

fun computeDynamicBubbleZoomFactor(
    bubbleWidth: Float,
    bubbleHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Float {
    if (bubbleWidth <= 0f || bubbleHeight <= 0f) return 1.5f
    val targetWidth = viewportWidth * 0.6f
    val targetHeight = viewportHeight * 0.32f
    return min(targetWidth / bubbleWidth, targetHeight / bubbleHeight).coerceIn(1.35f, 4.25f)
}

fun safePdfBitmapRenderScale(
    contentWidth: Float,
    contentHeight: Float,
    requestedScale: Float,
): Float {
    if (contentWidth <= 0f || contentHeight <= 0f || requestedScale <= 0f) return 1f
    val requestedWidth = contentWidth * requestedScale
    val requestedHeight = contentHeight * requestedScale
    val requestedBytes = requestedWidth.toDouble() * requestedHeight.toDouble() * 4.0
    val byteScale = sqrt(PDF_MAX_DRAW_BITMAP_BYTES.toDouble() / requestedBytes.coerceAtLeast(1.0))
    val dimensionScale = PDF_MAX_DRAW_BITMAP_DIMENSION_PX.toDouble() /
        max(requestedWidth, requestedHeight).toDouble().coerceAtLeast(1.0)
    val limiter = min(1.0, min(byteScale, dimensionScale)).coerceAtLeast(0.01)
    return (requestedScale.toDouble() * limiter).coerceAtLeast(0.01).toFloat()
}
