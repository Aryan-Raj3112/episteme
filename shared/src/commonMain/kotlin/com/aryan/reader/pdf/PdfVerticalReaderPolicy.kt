package com.aryan.reader.pdf

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

/** A queued drag sample is valid only while the gesture that produced it still owns the camera. */
fun shouldApplyPdfVerticalCameraSample(sampleEpoch: Long, activeEpoch: Long): Boolean =
    sampleEpoch == activeEpoch

/** Advancing ownership invalidates every sample queued by the previous gesture or animation. */
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
