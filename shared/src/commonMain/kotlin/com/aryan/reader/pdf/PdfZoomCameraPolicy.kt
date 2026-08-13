package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt

fun clampPdfSpreadCameraOffset(
    scale: Float,
    offset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    if (viewportWidth <= 0f || viewportHeight <= 0f || scale <= 1f) return Offset.Zero
    val maxOffsetX = ((viewportWidth * scale) - viewportWidth).coerceAtLeast(0f) / 2f
    val maxOffsetY = ((viewportHeight * scale) - viewportHeight).coerceAtLeast(0f) / 2f
    return Offset(offset.x.coerceIn(-maxOffsetX, maxOffsetX), offset.y.coerceIn(-maxOffsetY, maxOffsetY))
}

fun pdfSpreadPageSlotWidth(
    containerWidth: Float,
    containerHeight: Float,
    pageGap: Float,
    spreadPageCount: Int,
    pageAspectRatio: Float,
): Float {
    if (containerWidth <= 0f || containerHeight <= 0f || spreadPageCount <= 0) return 0f
    val safeGap = pageGap.coerceAtLeast(0f)
    val safeAspectRatio = pageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val availableWidth = (containerWidth - (safeGap * (spreadPageCount - 1))).coerceAtLeast(0f)
    val maxPageWidth = availableWidth / spreadPageCount
    return (containerHeight * safeAspectRatio).coerceAtMost(maxPageWidth).coerceAtLeast(0f)
}

fun activePdfCameraAfterLockPreferenceLoad(
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
): Pair<Float, Offset> = if (isScrollLocked && lockedState != null) {
    lockedState.first to Offset(lockedState.second, lockedState.third)
} else 1f to Offset.Zero

fun shouldReportPdfPageCamera(
    isZoomEnabled: Boolean,
    isVerticalScroll: Boolean,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
    hasAppliedLockedState: Boolean,
): Boolean = !isZoomEnabled || isVerticalScroll || !isScrollLocked || lockedState == null || hasAppliedLockedState

fun initialPdfPageCamera(
    isZoomEnabled: Boolean,
    isVerticalScroll: Boolean,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
): Pair<Float, Offset> = if (isZoomEnabled && !isVerticalScroll && isScrollLocked && lockedState != null) {
    lockedState.first to Offset(lockedState.second, lockedState.third)
} else 1f to Offset.Zero

fun shouldResetPdfZoomAfterBubbleZoomCleanup(
    isBubbleZoomModeActive: Boolean,
    scale: Float,
    isVerticalScroll: Boolean,
    isZoomEnabled: Boolean,
    isScrollLocked: Boolean,
): Boolean = !isBubbleZoomModeActive && scale > 1f && !isVerticalScroll && isZoomEnabled && !isScrollLocked

fun shouldRenderPdfHighResTiles(
    effectiveScale: Float,
    targetWidthPx: Int,
    targetHeightPx: Int,
    isVerticalScroll: Boolean,
    isActivePage: Boolean,
    largePageThresholdPx: Int = 3000,
    verticalScaleTolerance: Float = 0.01f,
): Boolean {
    val hasLargePage = targetWidthPx > largePageThresholdPx || targetHeightPx > largePageThresholdPx
    if (!isVerticalScroll && !isActivePage) return false
    if (hasLargePage) return true
    val safeScale = effectiveScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return if (isVerticalScroll) abs(safeScale - 1f) > verticalScaleTolerance else safeScale > 1f
}

fun pdfZoomIndicatorPercent(scale: Float): Int {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (safeScale * 100f).roundToInt()
}

fun shouldShowPdfZoomIndicator(percentage: Int): Boolean = percentage != 100
