package com.aryan.reader.shared.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SharedSelectionMenuViewport(
    val width: Int,
    val height: Int
)

data class SharedSelectionMenuSize(
    val width: Int,
    val height: Int
)

data class SharedSelectionMenuRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

enum class SharedSelectionMenuPlacement {
    ABOVE,
    BELOW,
    LEFT,
    RIGHT,
    FALLBACK
}

data class SharedSelectionMenuPlacementResult(
    val x: Int,
    val y: Int,
    val placement: SharedSelectionMenuPlacement
)

/**
 * Maps a selection rect in overlay canvas coords to window coords.
 *
 * The overlay lives inside the zoom viewport's scaled layer, so its window
 * rect already reflects scale + pan + Center pivot. Mapping fractionally keeps
 * the menu anchored correctly at any zoom (Android parity via
 * contentToScreen + localToWindow).
 */
fun sharedPdfSelectionWindowRect(
    anchor: SharedSelectionMenuRect,
    canvasWidth: Int,
    canvasHeight: Int,
    overlayLeft: Int,
    overlayTop: Int,
    overlayWidth: Int,
    overlayHeight: Int,
): SharedSelectionMenuRect {
    val canvasW = canvasWidth.takeIf { it > 0 }?.toFloat() ?: 1f
    val canvasH = canvasHeight.takeIf { it > 0 }?.toFloat() ?: 1f
    val boxW = overlayWidth.takeIf { it > 0 }?.toFloat() ?: canvasW
    val boxH = overlayHeight.takeIf { it > 0 }?.toFloat() ?: canvasH
    return SharedSelectionMenuRect(
        left = overlayLeft + anchor.left / canvasW * boxW,
        top = overlayTop + anchor.top / canvasH * boxH,
        right = overlayLeft + anchor.right / canvasW * boxW,
        bottom = overlayTop + anchor.bottom / canvasH * boxH,
    )
}

fun sharedSelectionMenuPlacement(
    viewport: SharedSelectionMenuViewport,
    popup: SharedSelectionMenuSize,
    selection: SharedSelectionMenuRect,
    marginPx: Float,
    gapPx: Float
): SharedSelectionMenuPlacementResult {
    val viewportWidth = viewport.width.coerceAtLeast(0).toFloat()
    val viewportHeight = viewport.height.coerceAtLeast(0).toFloat()
    val popupWidth = popup.width.coerceAtLeast(0).toFloat()
    val popupHeight = popup.height.coerceAtLeast(0).toFloat()
    val margin = marginPx.coerceAtLeast(0f)
    val gap = gapPx.coerceAtLeast(0f)
    val keepClear = selection.normalized().clampedToViewport(viewportWidth, viewportHeight)

    fun centeredX(): Float = keepClear.centerX - popupWidth / 2f
    fun centeredY(): Float = keepClear.centerY - popupHeight / 2f
    fun clamped(x: Float, y: Float): SharedSelectionMenuCandidate {
        return SharedSelectionMenuCandidate(
            x = clampStart(x, popupWidth, viewportWidth, margin),
            y = clampStart(y, popupHeight, viewportHeight, margin)
        )
    }

    val above = clamped(centeredX(), keepClear.top - gap - popupHeight)
    if (above.y + popupHeight <= keepClear.top - gap && above.y >= margin) {
        return above.toResult(SharedSelectionMenuPlacement.ABOVE)
    }

    val below = clamped(centeredX(), keepClear.bottom + gap)
    if (below.y >= keepClear.bottom + gap && below.y + popupHeight <= viewportHeight - margin) {
        return below.toResult(SharedSelectionMenuPlacement.BELOW)
    }

    val leftSpace = keepClear.left - gap - margin
    val rightSpace = viewportWidth - keepClear.right - gap - margin
    val sideCandidates = if (rightSpace >= leftSpace) {
        listOf(
            SharedSelectionMenuPlacement.RIGHT to clamped(keepClear.right + gap, centeredY()),
            SharedSelectionMenuPlacement.LEFT to clamped(keepClear.left - gap - popupWidth, centeredY())
        )
    } else {
        listOf(
            SharedSelectionMenuPlacement.LEFT to clamped(keepClear.left - gap - popupWidth, centeredY()),
            SharedSelectionMenuPlacement.RIGHT to clamped(keepClear.right + gap, centeredY())
        )
    }
    sideCandidates.forEach { (placement, candidate) ->
        val fitsHorizontally = when (placement) {
            SharedSelectionMenuPlacement.LEFT -> candidate.x + popupWidth <= keepClear.left - gap
            SharedSelectionMenuPlacement.RIGHT -> candidate.x >= keepClear.right + gap
            else -> false
        }
        if (fitsHorizontally && candidate.y >= margin && candidate.y + popupHeight <= viewportHeight - margin) {
            return candidate.toResult(placement)
        }
    }

    return listOf(
        above,
        below,
        sideCandidates[0].second,
        sideCandidates[1].second
    ).minWith(
        compareBy<SharedSelectionMenuCandidate> { candidate ->
            candidate.overlapAreaWith(keepClear, popupWidth, popupHeight)
        }.thenBy { candidate ->
            candidate.distanceFrom(keepClear, popupWidth, popupHeight)
        }
    ).toResult(SharedSelectionMenuPlacement.FALLBACK)
}

private data class SharedSelectionMenuCandidate(
    val x: Float,
    val y: Float
) {
    fun toResult(placement: SharedSelectionMenuPlacement): SharedSelectionMenuPlacementResult {
        return SharedSelectionMenuPlacementResult(
            x = x.roundToInt(),
            y = y.roundToInt(),
            placement = placement
        )
    }

    fun overlapAreaWith(
        rect: SharedSelectionMenuRect,
        width: Float,
        height: Float
    ): Float {
        val overlapWidth = min(x + width, rect.right) - max(x, rect.left)
        val overlapHeight = min(y + height, rect.bottom) - max(y, rect.top)
        return overlapWidth.coerceAtLeast(0f) * overlapHeight.coerceAtLeast(0f)
    }

    fun distanceFrom(
        rect: SharedSelectionMenuRect,
        width: Float,
        height: Float
    ): Float {
        val dx = maxOf(rect.left - (x + width), x - rect.right, 0f)
        val dy = maxOf(rect.top - (y + height), y - rect.bottom, 0f)
        return dx * dx + dy * dy
    }
}

private fun SharedSelectionMenuRect.normalized(): SharedSelectionMenuRect {
    return SharedSelectionMenuRect(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom)
    )
}

private val SharedSelectionMenuRect.centerX: Float
    get() = (left + right) / 2f

private val SharedSelectionMenuRect.centerY: Float
    get() = (top + bottom) / 2f

private fun SharedSelectionMenuRect.clampedToViewport(
    viewportWidth: Float,
    viewportHeight: Float
): SharedSelectionMenuRect {
    return SharedSelectionMenuRect(
        left = left.coerceIn(0f, viewportWidth),
        top = top.coerceIn(0f, viewportHeight),
        right = right.coerceIn(0f, viewportWidth),
        bottom = bottom.coerceIn(0f, viewportHeight)
    ).normalized()
}

private fun clampStart(
    preferred: Float,
    popupSize: Float,
    viewportSize: Float,
    margin: Float
): Float {
    if (viewportSize <= 0f || popupSize <= 0f) return 0f
    if (viewportSize <= popupSize) return 0f
    val maxStart = viewportSize - popupSize
    val min = margin.coerceIn(0f, maxStart)
    val max = (viewportSize - popupSize - margin).coerceAtLeast(min)
    return preferred.coerceIn(min, max)
}

/**
 * Android parity (`PdfVerticalReader.kt` ink selection edit bar): prefers ABOVE
 * the selection (fixed clearance for the rotate handle), falls back BELOW only
 * when there is no room above [topInsetPx]. Fully clamped on-screen.
 *
 * Pure math (px-in/px-out) so it is unit-testable; the composable converts dp
 * clearances via density and passes measured [barWidthPx]/[barHeightPx].
 */
fun sharedPdfInkSelectionEditBarOffset(
    selectionLeft: Float,
    selectionTop: Float,
    selectionRight: Float,
    selectionBottom: Float,
    containerWidth: Float,
    containerHeight: Float,
    barWidthPx: Float,
    barHeightPx: Float,
    topInsetPx: Float,
    edgeMarginPx: Float,
    aboveClearancePx: Float,
    belowGapPx: Float,
): Pair<Int, Int> {
    val aboveY = selectionTop - aboveClearancePx
    val belowY = selectionBottom + belowGapPx
    val preferredY = if (aboveY > topInsetPx) aboveY else belowY
    val halfScreen = containerWidth / 2f
    val maxShift = (halfScreen - barWidthPx / 2f - edgeMarginPx).coerceAtLeast(0f)
    val xShift = ((selectionLeft + selectionRight) / 2f - halfScreen)
        .coerceIn(-maxShift, maxShift)
    val x = (halfScreen + xShift - barWidthPx / 2f).coerceIn(
        0f,
        (containerWidth - barWidthPx).coerceAtLeast(0f)
    )
    val topMin = topInsetPx + edgeMarginPx
    val topMax = (containerHeight - barHeightPx - edgeMarginPx).coerceAtLeast(topMin)
    val y = preferredY.coerceIn(topMin, topMax)
    return x.roundToInt() to y.roundToInt()
}
