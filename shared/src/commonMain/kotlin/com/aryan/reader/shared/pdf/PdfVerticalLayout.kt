package com.aryan.reader.shared.pdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

data class PdfVisiblePageLayout(
    val pageIndex: Int,
    val top: Float,
    val bottom: Float
) {
    val visibleHeight: Float
        get() = (bottom - top).coerceAtLeast(0f)
}

fun mostVisiblePdfPageIndex(
    visiblePages: List<PdfVisiblePageLayout>,
    viewportTop: Float,
    viewportBottom: Float,
    fallbackPageIndex: Int
): Int {
    return visiblePages
        .filter { it.visibleHeight > 0f }
        .map { page ->
            val top = maxOf(page.top, viewportTop)
            val bottom = minOf(page.bottom, viewportBottom)
            page to (bottom - top).coerceAtLeast(0f)
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second > 0f }
        ?.first
        ?.pageIndex
        ?: fallbackPageIndex
}

fun pdfVerticalPageGapDp(
    isPageGapVisible: Boolean,
    defaultGap: Dp
): Dp = if (isPageGapVisible) defaultGap else 0.dp

/**
 * Android parity ([calculatePdfVerticalPageLayoutPx] single-page branch):
 * a one-display-page vertical document is centered in the viewport instead of
 * being pinned to the top. The shared [SharedMobilePdfVerticalPages] list
 * (iOS benchmark) uses this to pick [Arrangement.Center]; taller-than-viewport
 * pages are unaffected because a lazy list only applies arrangement to free
 * space.
 */
fun isPdfVerticalSinglePageCentered(displayPageCount: Int): Boolean =
    displayPageCount == 1

data class PdfVerticalPagePlacement(
    val pageIndex: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int
) {
    val bottomPx: Int
        get() = topPx + heightPx
}

data class PdfVerticalPageLayoutResult(
    val pages: List<PdfVerticalPagePlacement>,
    val totalHeightPx: Int
)

fun calculatePdfVerticalPageLayoutPx(
    pageAspectRatios: List<Float>,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    pageGapPx: Int
): PdfVerticalPageLayoutResult {
    val safeWidthPx = viewportWidthPx.coerceAtLeast(0)
    if (pageAspectRatios.isEmpty() || safeWidthPx == 0) {
        return PdfVerticalPageLayoutResult(emptyList(), 0)
    }

    val safeGapPx = pageGapPx.coerceAtLeast(0)
    val safeHeightPx = viewportHeightPx.coerceAtLeast(0)

    fun pageHeightPx(ratio: Float): Int {
        val safeRatio = if (ratio <= 0f) 1f else ratio
        return floor(safeWidthPx.toDouble() / safeRatio.toDouble())
            .toInt()
            .coerceAtLeast(1)
    }

    var currentTopPx = 0
    if (pageAspectRatios.size == 1) {
        val singlePageHeightPx = pageHeightPx(pageAspectRatios[0])
        if (singlePageHeightPx < safeHeightPx) {
            currentTopPx = (safeHeightPx - singlePageHeightPx) / 2
        }
    }

    val pages = pageAspectRatios.mapIndexed { index, ratio ->
        val heightPx = pageHeightPx(ratio)
        val placement = PdfVerticalPagePlacement(
            pageIndex = index,
            topPx = currentTopPx,
            widthPx = safeWidthPx,
            heightPx = heightPx
        )
        currentTopPx += heightPx
        if (index < pageAspectRatios.lastIndex) {
            currentTopPx += safeGapPx
        }
        placement
    }

    return PdfVerticalPageLayoutResult(
        pages = pages,
        totalHeightPx = pages.lastOrNull()?.bottomPx ?: 0
    )
}
