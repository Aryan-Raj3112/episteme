package com.aryan.reader.shared.pdf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * No-overlap placement for the PDF annotation + text docks.
 *
 * Pure math, px-in/px-out. Both the shared reader and the Android reader use
 * these when a dock lands so two docks never cover each other:
 * - a side wheel dropped onto the other dock's edge slides above or below it
 *   (same-side stacking included — no more pushing the bar to the center);
 * - a side wheel dropped overlapping a top/bottom bar slides down/up clear
 *   of it;
 * - a bar dropped to top/bottom while a side wheel hugs that corner nudges
 *   the wheel clear with a glide (a full-width bar cannot shift instead);
 * - a floating bar dropped onto a side wheel slides out of its band.
 *
 * Rects are compared in the same root coordinate space; pass null when the
 * other dock is hidden so the drop just clamps to the edge.
 */

/** Clamps a side-wheel Y into [edgeTopPx, edgeBottomPx - ownHeightPx]. */
fun sharedPdfClampSideWheelY(
    y: Float,
    ownHeightPx: Float,
    edgeTopPx: Float,
    edgeBottomPx: Float,
): Float {
    val maxY = edgeBottomPx - ownHeightPx
    // Degenerate edge (tiny screens): center instead of crashing coerceIn.
    if (maxY < edgeTopPx) return (edgeTopPx + edgeBottomPx - ownHeightPx) / 2f
    return y.coerceIn(edgeTopPx, maxY)
}

/**
 * Drop Y for a side wheel. When the wheel's edge band does not touch [other]
 * this is just the clamp; otherwise the wheel slides above or below the
 * other dock — above when the drop point is at/above the other's center,
 * below when under it — preferring the drop half and falling back to
 * whichever side fits.
 */
fun resolveSharedPdfSideWheelDropY(
    proposedY: Float,
    ownLeftPx: Float,
    ownWidthPx: Float,
    ownHeightPx: Float,
    edgeTopPx: Float,
    edgeBottomPx: Float,
    gapPx: Float,
    other: Rect?,
): Float {
    val clamped = sharedPdfClampSideWheelY(proposedY, ownHeightPx, edgeTopPx, edgeBottomPx)
    if (other == null) return clamped
    if (ownLeftPx >= other.right || ownLeftPx + ownWidthPx <= other.left) return clamped
    val blockedTop = other.top - gapPx
    val blockedBottom = other.bottom + gapPx
    if (clamped + ownHeightPx <= blockedTop || clamped >= blockedBottom) return clamped
    val above = blockedTop - ownHeightPx
    val below = blockedBottom
    val aboveClamped = sharedPdfClampSideWheelY(above, ownHeightPx, edgeTopPx, edgeBottomPx)
    val belowClamped = sharedPdfClampSideWheelY(below, ownHeightPx, edgeTopPx, edgeBottomPx)
    fun clears(y: Float): Boolean = y + ownHeightPx <= blockedTop || y >= blockedBottom
    val preferAbove = clamped + ownHeightPx / 2f <= other.top + other.height / 2f
    return when {
        preferAbove && clears(aboveClamped) -> aboveClamped
        !preferAbove && clears(belowClamped) -> belowClamped
        clears(aboveClamped) -> aboveClamped
        clears(belowClamped) -> belowClamped
        // Degenerate edge (nothing fits): keep the preferred side, clamped.
        preferAbove -> aboveClamped
        else -> belowClamped
    }
}

/**
 * Drop X for a bar (sticky top/bottom slot or floating) that must not cover
 * a side wheel: when the bar's rect touches the wheel's band (expanded by
 * [gapPx]) it slides out horizontally to whichever side of the band its
 * center is nearer. Untouched drops pass through unchanged.
 */
fun resolveSharedPdfBarDropX(
    proposedX: Float,
    ownTopPx: Float,
    ownWidthPx: Float,
    ownHeightPx: Float,
    rootWidthPx: Float,
    gapPx: Float,
    sideWheel: Rect?,
): Float {
    if (sideWheel == null) return proposedX
    val bandLeft = sideWheel.left - gapPx
    val bandRight = sideWheel.right + gapPx
    val touchesBand = proposedX < bandRight && proposedX + ownWidthPx > bandLeft &&
        ownTopPx < sideWheel.bottom + gapPx && ownTopPx + ownHeightPx > sideWheel.top - gapPx
    if (!touchesBand) return proposedX
    val ownCenterX = proposedX + ownWidthPx / 2f
    val wheelCenterX = sideWheel.left + sideWheel.width / 2f
    val pushed = if (ownCenterX <= wheelCenterX) bandLeft - ownWidthPx else bandRight
    val maxX = rootWidthPx - ownWidthPx
    if (maxX < 0f) return (rootWidthPx - ownWidthPx) / 2f
    return pushed.coerceIn(0f, maxX)
}

/**
 * Nudges an already-docked side wheel clear of a bar band that just landed
 * over it (e.g. a top bar while the wheel hugs that corner): the wheel
 * slides below a top band or above a bottom band, clamped to the edge.
 * Returns the input Y when there is no overlap.
 */
fun resolveSharedPdfSideWheelClearOfBarBand(
    wheelY: Float,
    wheelHeightPx: Float,
    edgeTopPx: Float,
    edgeBottomPx: Float,
    gapPx: Float,
    barFromYPx: Float,
    barToYPx: Float,
): Float {
    if (wheelY + wheelHeightPx <= barFromYPx - gapPx || wheelY >= barToYPx + gapPx) {
        return sharedPdfClampSideWheelY(wheelY, wheelHeightPx, edgeTopPx, edgeBottomPx)
    }
    val wheelCenter = wheelY + wheelHeightPx / 2f
    val barCenter = (barFromYPx + barToYPx) / 2f
    fun clears(y: Float): Boolean =
        y + wheelHeightPx <= barFromYPx - gapPx || y >= barToYPx + gapPx
    // Below a top band / above a bottom band first; when that side has no
    // room (clamping would still overlap), fall back to the other side.
    val preferBelow = wheelCenter > barCenter
    val preferred = sharedPdfClampSideWheelY(
        if (preferBelow) barToYPx + gapPx else barFromYPx - gapPx - wheelHeightPx,
        wheelHeightPx,
        edgeTopPx,
        edgeBottomPx,
    )
    if (clears(preferred)) return preferred
    val fallback = sharedPdfClampSideWheelY(
        if (preferBelow) barFromYPx - gapPx - wheelHeightPx else barToYPx + gapPx,
        wheelHeightPx,
        edgeTopPx,
        edgeBottomPx,
    )
    return if (clears(fallback)) fallback else preferred
}

/** Convenience: rect from a root-space top-left plus pixel size. */
fun sharedPdfDockRect(offset: Offset, widthPx: Float, heightPx: Float): Rect =
    Rect(offset.x, offset.y, offset.x + widthPx, offset.y + heightPx)

/**
 * Settle glide shared by both docks: records [docked] as the stored offset
 * and eases the visuals there from the live position ([current] plus any
 * in-flight glide), so drops — and nudges of the other dock — never jump.
 */
fun launchSharedPdfDockGlide(
    scope: CoroutineScope,
    glide: Animatable<Offset, AnimationVector2D>,
    current: Offset,
    docked: Offset,
    setOffset: (Offset) -> Unit,
    durationMillis: Int = 300,
) {
    val delta = (current + glide.value) - docked
    setOffset(docked)
    scope.launch {
        glide.stop()
        glide.snapTo(delta)
        glide.animateTo(Offset.Zero, tween(durationMillis = durationMillis))
    }
}
