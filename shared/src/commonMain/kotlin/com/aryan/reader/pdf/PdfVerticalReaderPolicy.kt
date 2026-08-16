package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.aryan.reader.shared.ReaderTheme
import kotlin.math.abs

fun resolvePdfVerticalPageBackgroundColor(activeTheme: ReaderTheme): Color {
    val resolved = when (activeTheme.id) {
        "no_theme", "system" -> Color.White
        "reverse" -> Color.Black
        else -> activeTheme.backgroundColor
    }
    return if (resolved.isSpecified) resolved else Color.White
}

data class PdfLockedOrientationResetCamera(
    val zoom: Float,
    val panX: Float,
    val panY: Float,
)

data class PdfConsumedAxisDelta(
    val position: Float,
    val consumed: Float,
)

/** Applies one animation-frame delta and reports how much was consumed at the bounds. */
fun consumePdfAxisDelta(
    current: Float,
    delta: Float,
    minimum: Float,
    maximum: Float,
): PdfConsumedAxisDelta {
    val position = (current + delta).coerceIn(minimum, maximum)
    return PdfConsumedAxisDelta(position = position, consumed = position - current)
}

/**
 * Stops decay only after a real requested frame was rejected by both axes.
 * AnimationState may emit an initialization frame whose delta is exactly zero;
 * canceling on that frame makes otherwise valid micro-flings end immediately.
 */
fun shouldStopPdfDecayFrame(
    requestedDistanceDelta: Float,
    consumedX: Float,
    consumedY: Float,
    epsilon: Float = 0.01f,
): Boolean =
    abs(requestedDistanceDelta) >= epsilon &&
        abs(consumedX) < epsilon &&
        abs(consumedY) < epsilon

/** Returns only motion beyond touch slop, matching Android drag acquisition semantics. */
fun pdfPanAfterTouchSlop(accumulatedPan: Offset, touchSlop: Float): Offset {
    val distance = accumulatedPan.getDistance()
    val safeSlop = touchSlop.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    if (!distance.isFinite() || distance <= safeSlop || distance == 0f) return Offset.Zero
    val retainedDistance = distance - safeSlop
    return accumulatedPan * (retainedDistance / distance)
}

/** Advancing ownership prevents a canceled animation from finishing as the current owner. */
fun nextPdfVerticalCameraEpoch(currentEpoch: Long): Long =
    if (currentEpoch == Long.MAX_VALUE) 0L else currentEpoch + 1L

/**
 * Once page motion has hidden a selection menu, keep that same menu hidden after motion settles.
 * A new selection resets [suppressedForCurrentSelection] when it installs its new menu state.
 */
fun shouldShowPdfSelectionMenu(
    hasMenu: Boolean,
    isPageMoving: Boolean,
    suppressedForCurrentSelection: Boolean,
): Boolean = hasMenu && !isPageMoving && !suppressedForCurrentSelection

/**
 * Geometry refinement may follow the initial placeholder layout. Only keep treating the camera
 * as fitted while it is still close to the actual fit scale; an absolute threshold breaks
 * landscape documents whose fit scale is below 1.
 */
fun isPdfVerticalZoomNearFit(
    currentZoom: Float,
    fitZoom: Float,
    tolerance: Float = 0.1f,
): Boolean {
    val safeFitZoom = fitZoom.takeIf { it.isFinite() && it > 0f } ?: return false
    val safeCurrentZoom = currentZoom.takeIf { it.isFinite() && it > 0f } ?: return false
    val safeTolerance = tolerance.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    return safeCurrentZoom <= safeFitZoom * (1f + safeTolerance)
}

fun preservedPdfVerticalPanY(
    oldPanY: Float,
    oldZoom: Float,
    newZoom: Float,
    viewportAnchorY: Float,
    oldPageTopY: Float,
    oldPageHeight: Float,
    newPageTopY: Float,
    newPageHeight: Float,
): Float {
    val oldDocumentAnchor = (viewportAnchorY - oldPanY) / oldZoom.coerceAtLeast(0.01f)
    val pageFraction = ((oldDocumentAnchor - oldPageTopY) / oldPageHeight.coerceAtLeast(1f))
        .coerceIn(0f, 1f)
    val newDocumentAnchor = newPageTopY + newPageHeight * pageFraction
    return viewportAnchorY - newDocumentAnchor * newZoom
}

fun calculateLockedOrientationResetCamera(
    pageTopY: Float,
    totalDocHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    headerHeightPx: Float,
    footerHeightPx: Float,
    fitZoom: Float,
): PdfLockedOrientationResetCamera {
    val targetPanY = headerHeightPx - (pageTopY * fitZoom)
    val zoomedDocHeight = totalDocHeight * fitZoom
    val minPanY = if (zoomedDocHeight < (screenHeight - headerHeightPx - footerHeightPx)) {
        headerHeightPx
    } else {
        (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)
    }
    val finalPanY = targetPanY.coerceIn(minPanY, headerHeightPx)

    val zoomedDocWidth = screenWidth * fitZoom
    val targetPanX = if (zoomedDocWidth < screenWidth) {
        (screenWidth - zoomedDocWidth) / 2f
    } else {
        0f
    }

    return PdfLockedOrientationResetCamera(
        zoom = fitZoom,
        panX = targetPanX,
        panY = finalPanY,
    )
}
