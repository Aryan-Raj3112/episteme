package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scrollable side-wheel dock policy.
 *
 * Side-docked (LEFT/RIGHT) annotation + text toolbars render as a compact
 * semi-circle wheel protruding from the edge instead of a tall vertical bar:
 * tools sit on a single arc and the wheel spins (vertical drag) to bring
 * hidden tools into view. Pure math, px-in/px-out (angles in degrees).
 */

val SharedPdfSideWheelWidth: Dp = 96.dp
val SharedPdfSideWheelHeight: Dp = 192.dp
val SharedPdfSideWheelOrbitRadius: Dp = 64.dp
val SharedPdfSideWheelButtonSize: Dp = 36.dp
/**
 * Track thickness: the wheel background is a thin arc band barely taller
 * than the icons (like the straight bar curved into an arc), not a bulky
 * filled half-disc.
 */
val SharedPdfSideWheelTrackThickness: Dp = 52.dp

/**
 * Angular step between adjacent tools: 180 / 40 shows 4-5 tools at a time,
 * the rest scroll into view as the wheel spins.
 */
const val SharedPdfSideWheelStepDeg: Float = 40f

/** Half of the visible window: tools with |angle| <= this sit on the arc. */
const val SharedPdfSideWheelVisibleHalfAngleDeg: Float = 90f

/**
 * End inset: scrolling stops while the first/last tool is still fully on
 * the track instead of hanging half-clipped off the rim (roughly the
 * angular half-size of a 36dp button on the 64dp orbit).
 */
const val SharedPdfSideWheelEndInsetDeg: Float = 16f

/** Normalizes any angle into [-180, 180] so wrapped positions stay correct. */
fun sharedPdfWheelNormalizeDeg(angleDeg: Float): Float {
    var normalized = (angleDeg + 180f) % 360f
    if (normalized < 0f) normalized += 360f
    return normalized - 180f
}

/**
 * Effective angular step: the requested [stepDeg] while the tools fit on a
 * partial arc, but an even full-circle distribution (360 / count) once the
 * span would wrap around — otherwise the first and last tools land on the
 * same spot and overlap (e.g. close over redo with 10 tools at 40 degrees).
 */
fun sharedPdfWheelEffectiveStepDeg(
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
): Float {
    if (itemCount <= 1 || stepDeg <= 0f) return stepDeg
    return if ((itemCount - 1) * stepDeg > 360f - stepDeg) 360f / itemCount else stepDeg
}

/** True when the tools wrap the full circle (even distribution, no ends). */
fun sharedPdfWheelIsFullCircle(
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
): Boolean = sharedPdfWheelEffectiveStepDeg(itemCount, stepDeg) < stepDeg - 0.001f

fun sharedPdfWheelBaseAngleDeg(
    index: Int,
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
): Float = (index - (itemCount - 1) / 2f) * sharedPdfWheelEffectiveStepDeg(itemCount, stepDeg)

/**
 * Symmetric scroll range for [rotationDeg]: zero when every tool fits inside
 * the visible half-window, otherwise just enough to bring each end tool
 * onto the track (stopping [SharedPdfSideWheelEndInsetDeg] before the rim
 * so the first/last icon is never cut off). A full circle has no ends, so
 * it allows a half turn — every tool passes through the window.
 */
fun sharedPdfWheelRotationRangeDeg(
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
    endInsetDeg: Float = SharedPdfSideWheelEndInsetDeg,
): Float {
    if (itemCount <= 1) return 0f
    if (sharedPdfWheelIsFullCircle(itemCount, stepDeg)) return 180f
    val span = (itemCount - 1) * stepDeg
    if (span <= visibleHalfAngleDeg * 2f) return 0f
    return (span - visibleHalfAngleDeg * 2f) / 2f + endInsetDeg
}

fun sharedPdfWheelClampRotationDeg(
    rotationDeg: Float,
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
    endInsetDeg: Float = SharedPdfSideWheelEndInsetDeg,
): Float {
    val range = sharedPdfWheelRotationRangeDeg(itemCount, stepDeg, visibleHalfAngleDeg, endInsetDeg)
    return rotationDeg.coerceIn(-range, range)
}

/**
 * Wheel spin for a vertical drag: dragging the arc down by [dragDyPx] moves
 * tools down along the orbit, i.e. increases every tool angle.
 */
fun sharedPdfWheelRotationForDragDy(
    rotationDeg: Float,
    dragDyPx: Float,
    orbitRadiusPx: Float,
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
    endInsetDeg: Float = SharedPdfSideWheelEndInsetDeg,
): Float {
    if (orbitRadiusPx <= 0f) return rotationDeg
    val deltaDeg = dragDyPx / orbitRadiusPx * (180f / PI.toFloat())
    return sharedPdfWheelClampRotationDeg(
        rotationDeg + deltaDeg,
        itemCount,
        stepDeg,
        visibleHalfAngleDeg,
        endInsetDeg,
    )
}

fun sharedPdfWheelIsAngleVisible(
    angleDeg: Float,
    marginDeg: Float = 10f,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
): Boolean = angleDeg >= -visibleHalfAngleDeg - marginDeg &&
    angleDeg <= visibleHalfAngleDeg + marginDeg

/**
 * Tool center inside a [containerWidthPx] x [containerHeightPx] wheel box.
 * 0 degrees points inward (toward the screen center): +x for a LEFT wheel,
 * -x for a RIGHT wheel. Positive angles go down (+y).
 */
fun sharedPdfWheelToolCenterPx(
    angleDeg: Float,
    orbitRadiusPx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    isLeft: Boolean,
): Offset {
    val radians = angleDeg * (PI.toFloat() / 180f)
    val dx = cos(radians) * orbitRadiusPx
    val dy = sin(radians) * orbitRadiusPx
    val centerY = containerHeightPx / 2f
    val centerX = if (isLeft) dx else containerWidthPx - dx
    return Offset(centerX, centerY + dy)
}
