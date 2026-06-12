package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.math.roundToInt

internal fun resolveEraserStrokeWidth(
    isEraserOverride: Boolean,
    activeToolThickness: Float,
    eraserToolThickness: Float
): Float = if (isEraserOverride) eraserToolThickness else activeToolThickness

internal fun canUsePdfSidecarsForBook(
    activeBookId: String?,
    loadedSidecarBookId: String?,
    areSidecarsLoaded: Boolean
): Boolean = activeBookId != null && areSidecarsLoaded && loadedSidecarBookId == activeBookId

internal fun canManagePdfVirtualPages(
    isDocumentReady: Boolean,
    currentBookId: String?,
    loadedPageLayoutBookId: String?,
    virtualPageCount: Int
): Boolean {
    return isDocumentReady &&
        currentBookId != null &&
        loadedPageLayoutBookId == currentBookId &&
        virtualPageCount > 0
}

internal fun currentPageScaleAfterPdfPageChange(
    displayMode: DisplayMode,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
    currentActiveScale: Float
): Float {
    return if (displayMode == DisplayMode.PAGINATION && isScrollLocked) {
        lockedState?.first ?: currentActiveScale
    } else {
        1f
    }
}

internal fun pdfPageRangeText(
    pageIndex: Int,
    pageCount: Int,
    displayMode: DisplayMode,
    settings: ReaderSettings
): String {
    val pageRange = if (displayMode == DisplayMode.PAGINATION) {
        PdfSpreadLayout.pageRangeLabel(pageIndex, pageCount, settings)
    } else {
        "${pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) + 1}"
    }
    return "$pageRange / $pageCount"
}

internal fun pdfPageRangeLabel(
    pageIndex: Int,
    pageCount: Int,
    displayMode: DisplayMode,
    settings: ReaderSettings
): String {
    val pageRange = if (displayMode == DisplayMode.PAGINATION) {
        PdfSpreadLayout.pageRangeLabel(pageIndex, pageCount, settings)
    } else {
        "${pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) + 1}"
    }
    return if ('-' in pageRange) {
        "Pages $pageRange of $pageCount"
    } else {
        "Page $pageRange of $pageCount"
    }
}

internal fun clampPdfSpreadCameraOffset(
    scale: Float,
    offset: Offset,
    viewportWidth: Float,
    viewportHeight: Float
): Offset {
    if (viewportWidth <= 0f || viewportHeight <= 0f || scale <= 1f) return Offset.Zero
    val maxOffsetX = ((viewportWidth * scale) - viewportWidth).coerceAtLeast(0f) / 2f
    val maxOffsetY = ((viewportHeight * scale) - viewportHeight).coerceAtLeast(0f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

internal fun pdfSpreadPageSlotWidth(
    containerWidth: Float,
    containerHeight: Float,
    pageGap: Float,
    spreadPageCount: Int,
    pageAspectRatio: Float
): Float {
    if (containerWidth <= 0f || containerHeight <= 0f || spreadPageCount <= 0) return 0f
    val safeGap = pageGap.coerceAtLeast(0f)
    val safeAspectRatio = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val availableWidth = (containerWidth - (safeGap * (spreadPageCount - 1))).coerceAtLeast(0f)
    val maxPageWidth = availableWidth / spreadPageCount
    val heightFittedPageWidth = containerHeight * safeAspectRatio
    return heightFittedPageWidth.coerceAtMost(maxPageWidth).coerceAtLeast(0f)
}

internal fun activePdfCameraAfterLockPreferenceLoad(
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?
): Pair<Float, Offset> {
    return if (isScrollLocked && lockedState != null) {
        lockedState.first to Offset(lockedState.second, lockedState.third)
    } else {
        1f to Offset.Zero
    }
}

internal fun shouldReportPdfPageCamera(
    isZoomEnabled: Boolean,
    isVerticalScroll: Boolean,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
    hasAppliedLockedState: Boolean
): Boolean {
    return !isZoomEnabled ||
        isVerticalScroll ||
        !isScrollLocked ||
        lockedState == null ||
        hasAppliedLockedState
}

internal fun initialPdfPageCamera(
    isZoomEnabled: Boolean,
    isVerticalScroll: Boolean,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?
): Pair<Float, Offset> {
    return if (isZoomEnabled && !isVerticalScroll && isScrollLocked && lockedState != null) {
        lockedState.first to Offset(lockedState.second, lockedState.third)
    } else {
        1f to Offset.Zero
    }
}

internal fun shouldResetPdfZoomAfterBubbleZoomCleanup(
    isBubbleZoomModeActive: Boolean,
    scale: Float,
    isVerticalScroll: Boolean,
    isZoomEnabled: Boolean,
    isScrollLocked: Boolean
): Boolean {
    return !isBubbleZoomModeActive &&
        scale > 1f &&
        !isVerticalScroll &&
        isZoomEnabled &&
        !isScrollLocked
}

internal fun shouldRenderPdfHighResTiles(
    effectiveScale: Float,
    targetWidthPx: Int,
    targetHeightPx: Int,
    isVerticalScroll: Boolean,
    isActivePage: Boolean,
    largePageThresholdPx: Int = 3000,
    verticalScaleTolerance: Float = 0.01f
): Boolean {
    val hasLargePage = targetWidthPx > largePageThresholdPx || targetHeightPx > largePageThresholdPx
    val isPageEligible = isVerticalScroll || isActivePage
    if (!isPageEligible) return false
    if (hasLargePage) return true

    val safeScale = effectiveScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return if (isVerticalScroll) {
        kotlin.math.abs(safeScale - 1f) > verticalScaleTolerance
    } else {
        safeScale > 1f
    }
}

internal fun pdfZoomIndicatorPercent(scale: Float): Int {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (safeScale * 100f).roundToInt()
}

internal fun shouldShowPdfZoomIndicator(percentage: Int): Boolean = percentage != 100
