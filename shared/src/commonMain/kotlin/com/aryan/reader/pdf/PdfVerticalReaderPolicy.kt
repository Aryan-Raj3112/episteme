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
