package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.aryan.reader.ml.SpeechBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

internal data class ExpandedBubbleRender(
    val bitmap: Bitmap,
    val zoomFactor: Float
)

internal fun computeDynamicBubbleZoomFactor(
    bubbleBounds: RectF,
    viewportWidth: Float,
    viewportHeight: Float
): Float {
    if (bubbleBounds.width() <= 0f || bubbleBounds.height() <= 0f) return 1.5f
    val targetWidth = viewportWidth * 0.6f
    val targetHeight = viewportHeight * 0.32f
    return min(targetWidth / bubbleBounds.width(), targetHeight / bubbleBounds.height())
        .coerceIn(1.35f, 4.25f)
}

internal fun isTapInsideBubble(
    bubble: SpeechBubble,
    tapX: Float,
    tapY: Float,
    hitSlopPx: Float
): Boolean {
    val expandedBounds = RectF(bubble.bounds)
    expandedBounds.inset(-hitSlopPx, -hitSlopPx)
    if (!expandedBounds.contains(tapX, tapY)) return false

    val mask = bubble.maskBitmap ?: return true
    if (!bubble.bounds.contains(tapX, tapY)) return true

    val normalizedX = ((tapX - bubble.bounds.left) / bubble.bounds.width()).coerceIn(0f, 0.999f)
    val normalizedY = ((tapY - bubble.bounds.top) / bubble.bounds.height()).coerceIn(0f, 0.999f)
    val maskX = (normalizedX * mask.width).toInt().coerceIn(0, mask.width - 1)
    val maskY = (normalizedY * mask.height).toInt().coerceIn(0, mask.height - 1)
    return AndroidColor.alpha(mask.getPixel(maskX, maskY)) > 24
}

internal suspend fun renderExpandedBubbleBitmap(
    document: ReaderDocument,
    pageIndex: Int,
    bubbleBounds: RectF,
    pageWidth: Int,
    pageHeight: Int,
    renderScale: Float
): Bitmap? = withContext(Dispatchers.IO) {
    if (pageWidth <= 0 || pageHeight <= 0 || bubbleBounds.width() <= 0f || bubbleBounds.height() <= 0f) {
        return@withContext null
    }

    document.openPage(pageIndex)?.use { page ->
        val safeRenderScale = safePdfBitmapRenderScale(
            contentWidth = bubbleBounds.width(),
            contentHeight = bubbleBounds.height(),
            requestedScale = renderScale
        )
        val cropWidth = (bubbleBounds.width() * safeRenderScale).roundToInt().coerceAtLeast(1)
        val cropHeight = (bubbleBounds.height() * safeRenderScale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(cropWidth, cropHeight)

        try {
            page.renderPageBitmap(
                bitmap = bitmap,
                startX = (-bubbleBounds.left * safeRenderScale).roundToInt(),
                startY = (-bubbleBounds.top * safeRenderScale).roundToInt(),
                drawSizeX = (pageWidth * safeRenderScale).roundToInt().coerceAtLeast(cropWidth),
                drawSizeY = (pageHeight * safeRenderScale).roundToInt().coerceAtLeast(cropHeight),
                renderAnnot = true
            )
            bitmap
        } catch (t: Throwable) {
            bitmap.recycle()
            Timber.tag("BubbleZoom").w(t, "Failed to render expanded bubble bitmap for page $pageIndex")
            null
        }
    }
}

internal fun safePdfBitmapRenderScale(
    contentWidth: Float,
    contentHeight: Float,
    requestedScale: Float
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

internal const val PDF_MAX_DRAW_BITMAP_BYTES = 64L * 1024L * 1024L
internal const val PDF_MAX_DRAW_BITMAP_DIMENSION_PX = 4096
