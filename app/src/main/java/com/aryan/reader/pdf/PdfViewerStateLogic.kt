package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import com.aryan.reader.shared.reader.ReaderSettings

internal fun currentPageScaleAfterPdfPageChange(
    displayMode: DisplayMode,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
    currentActiveScale: Float
): Float {
    return sharedCurrentPageScaleAfterPdfPageChange(
        isPaginationMode = displayMode == DisplayMode.PAGINATION,
        isScrollLocked = isScrollLocked,
        lockedState = lockedState,
        currentActiveScale = currentActiveScale,
    )
}

internal fun pdfPageRangeText(
    pageIndex: Int,
    pageCount: Int,
    displayMode: DisplayMode,
    settings: ReaderSettings
): String {
    return sharedPdfPageRangeText(pageIndex, pageCount, displayMode == DisplayMode.PAGINATION, settings)
}

internal fun pdfPageRangeLabel(
    pageIndex: Int,
    pageCount: Int,
    displayMode: DisplayMode,
    settings: ReaderSettings
): String {
    return sharedPdfPageRangeLabel(pageIndex, pageCount, displayMode == DisplayMode.PAGINATION, settings)
}
