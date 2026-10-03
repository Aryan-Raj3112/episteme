package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

data class MagnifierContentSource(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
) {
    val scaleX: Float get() = if (contentWidth > 0f) sourceWidth.toFloat() / contentWidth else 1f
    val scaleY: Float get() = if (contentHeight > 0f) sourceHeight.toFloat() / contentHeight else 1f
    fun sourceX(contentX: Float): Float = (contentX - contentLeft) * scaleX
    fun sourceY(contentY: Float): Float = (contentY - contentTop) * scaleY
}

data class MagnifierSampleGeometry(
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val outputScaleX: Float,
    val outputScaleY: Float,
)

fun calculateMagnifierSampleGeometry(
    centerContentX: Float,
    centerContentY: Float,
    contentSource: MagnifierContentSource,
    magnifierWidthPx: Float,
    magnifierHeightPx: Float,
    zoomFactor: Float,
): MagnifierSampleGeometry? {
    if (contentSource.sourceWidth <= 0 || contentSource.sourceHeight <= 0 ||
        contentSource.contentWidth <= 0f || contentSource.contentHeight <= 0f ||
        magnifierWidthPx <= 0f || magnifierHeightPx <= 0f || zoomFactor <= 0f
    ) return null
    val sourceRectWidth = (magnifierWidthPx / zoomFactor * contentSource.scaleX).coerceAtLeast(1f)
    val sourceRectHeight = (magnifierHeightPx / zoomFactor * contentSource.scaleY).coerceAtLeast(1f)
    val srcLeft = (contentSource.sourceX(centerContentX) - sourceRectWidth / 2f)
        .coerceIn(0f, max(0f, contentSource.sourceWidth.toFloat() - sourceRectWidth))
    val srcTop = (contentSource.sourceY(centerContentY) - sourceRectHeight / 2f)
        .coerceIn(0f, max(0f, contentSource.sourceHeight.toFloat() - sourceRectHeight))
    val left = srcLeft.roundToInt().coerceIn(0, contentSource.sourceWidth - 1)
    val top = srcTop.roundToInt().coerceIn(0, contentSource.sourceHeight - 1)
    val width = (contentSource.sourceWidth - left).coerceAtMost(sourceRectWidth.roundToInt().coerceAtLeast(1)).coerceAtLeast(1)
    val height = (contentSource.sourceHeight - top).coerceAtMost(sourceRectHeight.roundToInt().coerceAtLeast(1)).coerceAtLeast(1)
    return MagnifierSampleGeometry(left, top, width, height, magnifierWidthPx / width, magnifierHeightPx / height)
}

fun mapContentBoundsToMagnifier(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    contentSource: MagnifierContentSource,
    sample: MagnifierSampleGeometry,
): Rect = Rect(
    (contentSource.sourceX(left) - sample.srcLeft) * sample.outputScaleX,
    (contentSource.sourceY(top) - sample.srcTop) * sample.outputScaleY,
    (contentSource.sourceX(right) - sample.srcLeft) * sample.outputScaleX,
    (contentSource.sourceY(bottom) - sample.srcTop) * sample.outputScaleY,
)

/**
 * A high-res zoom tile paired with the region it covers **in content space** (the page's on-screen
 * fit size, i.e. the canvas size at scale 1).
 *
 * Both hosts build tiles in different spaces — Android's `PdfTile.renderRect` is already in
 * content space, while `PdfZoomTileRequest.leftPx` is in full-render space and needs dividing by
 * the render scale — so each host converts on the way in and the magnifier itself only ever sees
 * content space. See `magnifierTileAt`.
 */
data class MagnifierTileSource(
    val bitmap: ImageBitmap,
    val contentRect: Rect,
)

/**
 * The index of the tile covering the magnifier center, or null when there is none.
 *
 * Tiles are only consulted above base scale, matching Android's `currentScale > 1f` gate. Both
 * edges are half-open (`>= left`, `< right`), which is what `android.graphics.Rect.contains` does,
 * so a center landing exactly on a seam resolves to the tile that starts there. A degenerate rect
 * never matches, again matching `Rect.contains`.
 */
fun magnifierTileIndexAt(
    tileContentRects: List<Rect>,
    centerX: Float,
    centerY: Float,
    currentScale: Float,
): Int? {
    if (currentScale <= 1f) return null
    return tileContentRects.indexOfFirst { rect ->
        rect.right > rect.left && rect.bottom > rect.top &&
            centerX >= rect.left && centerX < rect.right &&
            centerY >= rect.top && centerY < rect.bottom
    }.takeIf { it >= 0 }
}
