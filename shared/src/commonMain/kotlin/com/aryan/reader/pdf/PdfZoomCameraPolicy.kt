package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

/**
 * Shared spread zoom camera plus the page's placement inside the zoomed Row,
 * captured by the pager-page caller (the only place that knows both).
 */
data class PdfSpreadCameraContext(
    val scale: Float,
    val offset: Offset,
    val rowWidth: Float,
    val rowHeight: Float,
    val pageLeftInRow: Float,
    val pageTopInRow: Float,
)

/**
 * Visible page-local rect under the shared spread zoom camera (Android 2-page
 * paginated mode).
 *
 * The spread zooms by transforming the whole page Row:
 * `graphicsLayer { scaleX = s; translationX/Y = cameraOffset }` — a scale
 * around the Row's center followed by a translation. A page rendered at
 * `(pageLeftInRow, pageTopInRow)` with its bitmap centered via
 * `(centeringOffsetX, centeringOffsetY)` must tile the region that the on-screen
 * viewport maps back to through that transform. Per-page zoom inversion cannot
 * do this: the spread passes the page `offset = Zero`, which resolves every
 * pan/zoom position to the page center (the "only the center goes high-res"
 * bug).
 */
fun pdfSpreadVisiblePageRect(
    cameraScale: Float,
    cameraOffset: Offset,
    rowWidth: Float,
    rowHeight: Float,
    pageLeftInRow: Float,
    pageTopInRow: Float,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
): Rect {
    val scale = cameraScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val rowCenterX = rowWidth / 2f
    val rowCenterY = rowHeight / 2f
    fun mapToPage(edge: Float, rowCenter: Float, cameraOffsetComponent: Float, pageOrigin: Float, centering: Float): Float {
        val rowPoint = ((edge - cameraOffsetComponent) - rowCenter) / scale + rowCenter
        return rowPoint - pageOrigin - centering
    }
    val left = mapToPage(0f, rowCenterX, cameraOffset.x, pageLeftInRow, centeringOffsetX)
    val right = mapToPage(rowWidth, rowCenterX, cameraOffset.x, pageLeftInRow, centeringOffsetX)
    val top = mapToPage(0f, rowCenterY, cameraOffset.y, pageTopInRow, centeringOffsetY)
    val bottom = mapToPage(rowHeight, rowCenterY, cameraOffset.y, pageTopInRow, centeringOffsetY)
    return Rect(left, top, right, bottom)
}

fun pdfSpreadVisiblePageRect(
    camera: PdfSpreadCameraContext,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
): Rect = pdfSpreadVisiblePageRect(
    cameraScale = camera.scale,
    cameraOffset = camera.offset,
    rowWidth = camera.rowWidth,
    rowHeight = camera.rowHeight,
    pageLeftInRow = camera.pageLeftInRow,
    pageTopInRow = camera.pageTopInRow,
    centeringOffsetX = centeringOffsetX,
    centeringOffsetY = centeringOffsetY,
)

fun pdfZoomIndicatorPercent(scale: Float): Int {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (safeScale * 100f).roundToInt()
}

fun shouldShowPdfZoomIndicator(percentage: Int): Boolean = percentage != 100
