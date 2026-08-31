package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.aryan.reader.shared.ReaderTheme

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

data class PdfFlingVelocity(
    val x: Float,
    val y: Float,
)

/** Resolves independent axis velocities without projecting one axis through the other. */
fun resolvePdfFlingVelocity(
    rawX: Float,
    rawY: Float,
    displacementX: Float,
    displacementY: Float,
    minimumVelocity: Float,
    maximumVelocity: Float,
    allowHorizontal: Boolean,
): PdfFlingVelocity {
    val safeMaximum = maximumVelocity.coerceAtLeast(0f)
    val safeMinimum = minimumVelocity.coerceIn(0f, safeMaximum)
    val x = rawX.coerceIn(-safeMaximum, safeMaximum)
    val y = rawY.coerceIn(-safeMaximum, safeMaximum)
    val xMatchesGesture = displacementX == 0f || x * displacementX > 0f
    val yMatchesGesture = displacementY == 0f || y * displacementY > 0f
    return PdfFlingVelocity(
        x = if (
            allowHorizontal && xMatchesGesture && kotlin.math.abs(x) > safeMinimum
        ) x else 0f,
        y = if (yMatchesGesture && kotlin.math.abs(y) > safeMinimum) y else 0f,
    )
}

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

/**
 * Horizontal camera for a viewport whose width changed (split divider drag,
 * rotation, chrome resize). The vertical re-anchor already clamps panY, but a
 * pan preserved from the old width can sit outside the new horizontal range;
 * the next pinch or fling then clamps abruptly and the view jumps to the left
 * edge. Centers under-zoomed content and clamps the preserved pan into the
 * zoomed document's new range.
 */
fun preservedPdfVerticalPanXAfterViewportResize(
    panX: Float,
    zoom: Float,
    viewportWidth: Float,
): Float {
    val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: return 0f
    val zoomedDocWidth = safeViewportWidth * safeZoom
    return if (zoomedDocWidth <= safeViewportWidth) {
        (safeViewportWidth - zoomedDocWidth) / 2f
    } else {
        panX.takeIf { it.isFinite() }?.coerceIn(-(zoomedDocWidth - safeViewportWidth), 0f) ?: 0f
    }
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
