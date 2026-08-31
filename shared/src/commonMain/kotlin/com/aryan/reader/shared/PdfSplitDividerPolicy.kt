package com.aryan.reader.shared

import kotlin.math.abs

/**
 * Maps an absolute pointer position in the complete divider axis to the
 * logical first-pane fraction. The axis includes the divider touch target;
 * the returned fraction describes only the space available to the panes.
 *
 * Fractions are measured from the logical start edge. Android therefore
 * passes `isRtl = true` for a right-to-left layout, while iOS can use the same
 * policy when it wires its native divider.
 */
fun pdfSplitDividerFractionAtAbsolutePosition(
    pointerPositionPx: Float,
    axisSizePx: Int,
    dividerThicknessPx: Int,
    isRtl: Boolean = false,
): Float {
    val axis = axisSizePx.coerceAtLeast(1)
    val divider = dividerThicknessPx.coerceIn(0, (axis - 1).coerceAtLeast(0))
    val paneAxis = (axis - divider).coerceAtLeast(1)
    val pointerFromLogicalStart = if (isRtl) {
        axis.toFloat() - pointerPositionPx - divider / 2f
    } else {
        pointerPositionPx - divider / 2f
    }
    return safeDividerFraction(pointerFromLogicalStart / paneAxis.toFloat())
}

/** State returned by [snapPdfSplitDividerFraction]. */
data class PdfSplitDividerSnapState(
    val fraction: Float,
    val isSnappedToCenter: Boolean,
)

/**
 * Local divider gesture state. Preview movement never mutates the durable
 * workspace; [commit] is the only operation that produces a new committed
 * fraction and [cancel] deliberately restores the last committed position.
 */
data class PdfSplitDividerDragState(
    val committedFraction: Float,
    val previewFraction: Float? = null,
    val wasSnappedToCenter: Boolean = false,
) {
    val displayedFraction: Float
        get() = previewFraction ?: safeDividerFraction(committedFraction)

    fun preview(rawFraction: Float): PdfSplitDividerDragState {
        val snapped = snapPdfSplitDividerFraction(
            rawFraction = rawFraction,
            wasSnappedToCenter = wasSnappedToCenter,
        )
        return copy(
            previewFraction = snapped.fraction,
            wasSnappedToCenter = snapped.isSnappedToCenter,
        )
    }

    fun commit(): PdfSplitDividerDragState {
        return copy(
            committedFraction = displayedFraction,
            previewFraction = null,
            wasSnappedToCenter = false,
        )
    }

    fun cancel(): PdfSplitDividerDragState {
        return copy(
            previewFraction = null,
            wasSnappedToCenter = false,
        )
    }
}

/**
 * Applies a small center snap while dragging. Once engaged, the snap remains
 * active until the pointer leaves the larger exit window; this prevents the
 * divider from flickering around 50/50 while the user makes a fine adjustment.
 */
fun snapPdfSplitDividerFraction(
    rawFraction: Float,
    wasSnappedToCenter: Boolean,
    snapTarget: Float = DefaultPdfSplitDividerFraction,
    enterDistance: Float = DefaultPdfSplitDividerSnapEnterDistance,
    exitDistance: Float = DefaultPdfSplitDividerSnapExitDistance,
): PdfSplitDividerSnapState {
    val safeRaw = safeDividerFraction(rawFraction)
    val safeTarget = safeDividerFraction(snapTarget)
    val safeEnter = enterDistance.coerceAtLeast(0f)
    val safeExit = exitDistance.coerceAtLeast(safeEnter)
    val distance = abs(safeRaw - safeTarget)
    return when {
        wasSnappedToCenter && distance <= safeExit -> {
            PdfSplitDividerSnapState(safeTarget, isSnappedToCenter = true)
        }

        distance <= safeEnter -> {
            PdfSplitDividerSnapState(safeTarget, isSnappedToCenter = true)
        }

        else -> PdfSplitDividerSnapState(safeRaw, isSnappedToCenter = false)
    }
}

private fun safeDividerFraction(value: Float): Float {
    return if (value.isFinite()) {
        value.coerceIn(MinimumPdfSplitDividerFraction, MaximumPdfSplitDividerFraction)
    } else {
        DefaultPdfSplitDividerFraction
    }
}

const val DefaultPdfSplitDividerSnapEnterDistance = 0.02f
const val DefaultPdfSplitDividerSnapExitDistance = 0.045f
