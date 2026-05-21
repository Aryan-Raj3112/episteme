package com.aryan.reader.desktop

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.math.roundToInt

internal fun desktopPdfPageScrubTarget(
    value: Float,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    settings: ReaderSettings
): Int {
    val clampedPage = value.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    return if (displayMode == PdfDisplayMode.PAGINATION) {
        PdfSpreadLayout.normalizePageIndex(clampedPage, pageCount, settings)
    } else {
        clampedPage
    }
}

internal fun desktopPdfPageScrubCommitTarget(
    previewPage: Int?,
    currentPage: Int,
    pageCount: Int
): Int {
    return (previewPage ?: currentPage).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}
