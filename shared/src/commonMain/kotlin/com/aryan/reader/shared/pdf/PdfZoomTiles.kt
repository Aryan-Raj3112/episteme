package com.aryan.reader.shared.pdf

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val PDF_ZOOM_TILE_SIZE_PX = 640
const val PDF_ZOOM_TILE_MAX_RENDERED_SIDE_PX = 16_000
const val PDF_ZOOM_TILE_MAX_VISIBLE_COUNT = 12
const val PDF_ZOOM_TILE_CACHE_MAX_BYTES = 24L * 1024L * 1024L

private val PdfZoomRenderScaleBuckets = floatArrayOf(1.5f, 2f, 3f, 4f, 6f, 8f, 10f)

class PdfTileLruCache<T>(private val maxBytes: Long) {
    private data class Entry<T>(val value: T, val bytes: Long)
    private val entries = LinkedHashMap<String, Entry<T>>()
    var byteCount: Long = 0L
        private set
    val size: Int get() = entries.size

    fun get(key: String): T? {
        val entry = entries.remove(key) ?: return null
        entries[key] = entry
        return entry.value
    }

    fun put(key: String, value: T, bytes: Long) {
        entries.remove(key)?.let { byteCount -= it.bytes }
        val safeBytes = bytes.coerceAtLeast(0L)
        entries[key] = Entry(value, safeBytes)
        byteCount += safeBytes
        while (byteCount > maxBytes && entries.isNotEmpty()) {
            val eldest = entries.entries.first()
            byteCount -= eldest.value.bytes
            entries.remove(eldest.key)
        }
    }
}

/** Keeps camera movement continuous while limiting expensive raster-quality transitions. */
fun pdfZoomRenderScale(cameraScale: Float): Float {
    val safe = cameraScale.takeIf { it.isFinite() }?.coerceIn(PDF_MIN_ZOOM_SCALE, PDF_MAX_ZOOM_SCALE)
        ?: PDF_MIN_ZOOM_SCALE
    if (safe <= 1.01f) return PDF_MIN_ZOOM_SCALE
    return PdfZoomRenderScaleBuckets.firstOrNull { it >= safe } ?: PDF_MAX_ZOOM_SCALE
}

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
    maxRenderedSidePx: Int = PDF_ZOOM_TILE_MAX_RENDERED_SIDE_PX,
    tileSizePx: Int = PDF_ZOOM_TILE_SIZE_PX,
    maxTileCount: Int = PDF_ZOOM_TILE_MAX_VISIBLE_COUNT,
): List<PdfZoomTileRequest> {
    if (zoomScale <= 1.01f || tileSizePx <= 0 || baseMaxSidePx <= 0 || maxTileCount <= 0) return emptyList()
    val aspect = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val renderScale = pdfZoomRenderScale(zoomScale)
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
    val candidates = buildList {
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
    if (candidates.size <= maxTileCount) return candidates
    val centerX = (safe.left + safe.right) / 2f
    val centerY = (safe.top + safe.bottom) / 2f
    return candidates.sortedBy { tile ->
        val bounds = tile.normalizedBounds
        val dx = ((bounds.left + bounds.right) / 2f) - centerX
        val dy = ((bounds.top + bounds.bottom) / 2f) - centerY
        dx * dx + dy * dy
    }.take(maxTileCount).sortedBy(PdfZoomTileRequest::id)
}

fun pdfVerticalDoubleTapTargetScale(scale: Float, fitScale: Float = 1f): Float {
    val safeFit = fitScale.takeIf { it.isFinite() && it > 0f } ?: PDF_MIN_ZOOM_SCALE
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: safeFit
    if (safeFit < PDF_MIN_ZOOM_SCALE) {
        val fitToOneMidpoint = (safeFit + PDF_MIN_ZOOM_SCALE) / 2f
        val oneToMaxMidpoint = (PDF_MIN_ZOOM_SCALE + PDF_DOUBLE_TAP_ZOOM_SCALE) / 2f
        return when {
            safeScale < fitToOneMidpoint -> PDF_MIN_ZOOM_SCALE
            safeScale < oneToMaxMidpoint -> PDF_DOUBLE_TAP_ZOOM_SCALE
            else -> safeFit
        }
    }
    return if (safeScale <= safeFit + 0.05f) PDF_DOUBLE_TAP_ZOOM_SCALE else safeFit
}

fun pdfZoomIndicatorPercent(scale: Float): Int =
    ((scale.takeIf { it.isFinite() && it > 0f } ?: 1f) * 100f).roundToInt()
