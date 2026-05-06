package com.aryan.reader.shared.pdf

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
