package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
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
