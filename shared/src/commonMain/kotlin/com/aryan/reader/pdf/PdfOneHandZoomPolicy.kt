package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.pow

const val PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS = 90L
const val PDF_ONE_HAND_ZOOM_DRAG_DISTANCE_FOR_DOUBLE_DP = 240f

enum class PdfSecondTapZoomAction {
    QUICK_DOUBLE_TAP,
    ONE_HAND_ZOOM,
    HELD_NO_MOVEMENT,
}

fun classifyPdfSecondTapZoomAction(
    pressDurationMillis: Long,
    totalDragY: Float,
    movementSlopPx: Float,
    holdTimeoutMillis: Long = PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS,
): PdfSecondTapZoomAction = when {
    pressDurationMillis < holdTimeoutMillis -> PdfSecondTapZoomAction.QUICK_DOUBLE_TAP
    abs(totalDragY) >= movementSlopPx -> PdfSecondTapZoomAction.ONE_HAND_ZOOM
    else -> PdfSecondTapZoomAction.HELD_NO_MOVEMENT
}

fun pdfOneHandZoomScale(
    startScale: Float,
    totalDragY: Float,
    dragDistanceForDoublePx: Float,
    minScale: Float,
    maxScale: Float,
): Float {
    val safeStart = startScale.takeIf { it.isFinite() && it > 0f } ?: minScale
    val safeDistance = dragDistanceForDoublePx.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (safeStart * 2f.pow(totalDragY / safeDistance)).coerceIn(minScale, maxScale)
}

fun clampCenteredPdfCameraOffset(
    scale: Float,
    offset: Offset,
    viewportSize: Size,
    contentSize: Size,
): Offset {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f || scale <= 1f) return Offset.Zero
    val maxOffsetX = ((contentSize.width * scale) - viewportSize.width).coerceAtLeast(0f) / 2f
    val maxOffsetY = ((contentSize.height * scale) - viewportSize.height).coerceAtLeast(0f) / 2f
    return Offset(offset.x.coerceIn(-maxOffsetX, maxOffsetX), offset.y.coerceIn(-maxOffsetY, maxOffsetY))
}

fun centeredPdfCameraOffsetForScaleChange(
    previousScale: Float,
    nextScale: Float,
    previousOffset: Offset,
    pivot: Offset,
    viewportSize: Size,
    contentSize: Size,
): Offset {
    val safePreviousScale = previousScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val ratio = nextScale / safePreviousScale
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    return clampCenteredPdfCameraOffset(
        nextScale,
        previousOffset * ratio + (pivot - viewportCenter) * (1f - ratio),
        viewportSize,
        contentSize,
    )
}

fun topLeftPdfPanForScaleChange(
    previousScale: Float,
    nextScale: Float,
    previousPan: Offset,
    pivot: Offset,
): Offset {
    val safePreviousScale = previousScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val contentPivot = (pivot - previousPan) / safePreviousScale
    return pivot - (contentPivot * nextScale)
}
