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

/** Angular step between adjacent tools on the wheel. */
const val SharedPdfSideWheelStepDeg: Float = 25f

/** Half of the visible window: tools with |angle| <= this sit on the arc. */
const val SharedPdfSideWheelVisibleHalfAngleDeg: Float = 90f

fun sharedPdfWheelBaseAngleDeg(
    index: Int,
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
): Float = (index - (itemCount - 1) / 2f) * stepDeg

/**
 * Symmetric scroll range for [rotationDeg]: zero when every tool fits inside
 * the visible half-window, otherwise just enough to scroll each end into view.
 */
fun sharedPdfWheelRotationRangeDeg(
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
): Float {
    if (itemCount <= 1) return 0f
    val span = (itemCount - 1) * stepDeg
    return ((span - visibleHalfAngleDeg * 2f) / 2f).coerceAtLeast(0f)
}

fun sharedPdfWheelClampRotationDeg(
    rotationDeg: Float,
    itemCount: Int,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    visibleHalfAngleDeg: Float = SharedPdfSideWheelVisibleHalfAngleDeg,
): Float {
    val range = sharedPdfWheelRotationRangeDeg(itemCount, stepDeg, visibleHalfAngleDeg)
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
): Float {
    if (orbitRadiusPx <= 0f) return rotationDeg
    val deltaDeg = dragDyPx / orbitRadiusPx * (180f / PI.toFloat())
    return sharedPdfWheelClampRotationDeg(
        rotationDeg + deltaDeg,
        itemCount,
        stepDeg,
        visibleHalfAngleDeg,
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
