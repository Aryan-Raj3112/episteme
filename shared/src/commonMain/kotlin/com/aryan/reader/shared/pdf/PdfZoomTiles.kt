package com.aryan.reader.shared.pdf

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class PdfZoomTileRequest(
    val id: Int,
    val column: Int,
    val row: Int,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val renderScale: Float
) {
    val normalizedBounds: PdfPageBounds
        get() = PdfPageBounds(
            left = leftPx.toFloat() / fullWidthPx,
            top = topPx.toFloat() / fullHeightPx,
            right = (leftPx + widthPx).toFloat() / fullWidthPx,
            bottom = (topPx + heightPx).toFloat() / fullHeightPx
        )
}

fun planPdfZoomTiles(
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: PdfPageBounds,
    baseMaxSidePx: Int = 1600,
    maxRenderedSidePx: Int = 6400,
    tileSizePx: Int = 768
): List<PdfZoomTileRequest> {
    if (zoomScale <= 1.01f || tileSizePx <= 0 || baseMaxSidePx <= 0) return emptyList()
    val aspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val renderScale = (ceil(zoomScale * 4f) / 4f).coerceAtLeast(1f)
    val targetMaxSide = (baseMaxSidePx * renderScale).roundToInt().coerceAtMost(maxRenderedSidePx)
    val (fullWidth, fullHeight) = if (aspect >= 1f) {
        targetMaxSide to (targetMaxSide / aspect).roundToInt().coerceAtLeast(1)
    } else {
        (targetMaxSide * aspect).roundToInt().coerceAtLeast(1) to targetMaxSide
    }
    val columns = ceil(fullWidth.toFloat() / tileSizePx).toInt().coerceAtLeast(1)
    val rows = ceil(fullHeight.toFloat() / tileSizePx).toInt().coerceAtLeast(1)
    val safe = PdfPageBounds(
        left = visibleBounds.left.coerceIn(0f, 1f),
        top = visibleBounds.top.coerceIn(0f, 1f),
        right = visibleBounds.right.coerceIn(0f, 1f),
        bottom = visibleBounds.bottom.coerceIn(0f, 1f)
    )
    if (safe.right <= safe.left || safe.bottom <= safe.top) return emptyList()
    val prefetch = if (renderScale <= 2f) 1 else 0
    val firstColumn = (floor(safe.left * fullWidth / tileSizePx).toInt() - prefetch).coerceIn(0, columns - 1)
    val lastColumn = (floor(((safe.right * fullWidth) - 0.001f) / tileSizePx).toInt() + prefetch).coerceIn(0, columns - 1)
    val firstRow = (floor(safe.top * fullHeight / tileSizePx).toInt() - prefetch).coerceIn(0, rows - 1)
    val lastRow = (floor(((safe.bottom * fullHeight) - 0.001f) / tileSizePx).toInt() + prefetch).coerceIn(0, rows - 1)
    return buildList {
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val left = column * tileSizePx
            val top = row * tileSizePx
            add(
                PdfZoomTileRequest(
                    id = row * columns + column,
                    column = column,
                    row = row,
                    leftPx = left,
                    topPx = top,
                    widthPx = minOf(tileSizePx, fullWidth - left),
                    heightPx = minOf(tileSizePx, fullHeight - top),
                    fullWidthPx = fullWidth,
                    fullHeightPx = fullHeight,
                    renderScale = renderScale
                )
            )
        }
    }
}

fun pdfVerticalDoubleTapTargetScale(scale: Float, fitScale: Float = 1f): Float = when {
    scale < 0.95f -> 1f
    scale < 2.45f -> 2.5f
    else -> fitScale
}

fun pdfZoomIndicatorPercent(scale: Float): Int =
    ((scale.takeIf { it.isFinite() && it > 0f } ?: 1f) * 100f).roundToInt()
