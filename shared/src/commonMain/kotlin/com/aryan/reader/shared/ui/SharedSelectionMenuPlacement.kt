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
