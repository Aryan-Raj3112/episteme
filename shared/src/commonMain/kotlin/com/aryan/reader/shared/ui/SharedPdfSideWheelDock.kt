package com.aryan.reader.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.aryan.reader.shared.DockLocation
import com.aryan.reader.shared.pdf.SharedPdfSideWheelButtonSize
import com.aryan.reader.shared.pdf.SharedPdfSideWheelHeight
import com.aryan.reader.shared.pdf.SharedPdfSideWheelOrbitRadius
import com.aryan.reader.shared.pdf.SharedPdfSideWheelStepDeg
import com.aryan.reader.shared.pdf.SharedPdfSideWheelTrackThickness
import com.aryan.reader.shared.pdf.SharedPdfSideWheelWidth
import com.aryan.reader.shared.pdf.sharedPdfWheelBaseAngleDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelClampRotationDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelEffectiveStepDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelIsAngleVisible
import com.aryan.reader.shared.pdf.sharedPdfWheelNormalizeDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelRotationForDragDy
import com.aryan.reader.shared.pdf.sharedPdfWheelToolCenterPx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Exponential friction base per second for wheel flings. */
private const val WheelFlingFrictionPerSec = 6f

/**
 * Scrollable semi-circle wheel for side-docked (LEFT/RIGHT) PDF toolbars.
 *
 * The background is a thin arc band barely taller than the icons — the
 * straight toolbar curved into an arc — with tools riding the orbit. A
 * vertical drag spins the wheel to bring hidden tools into view (4-5 are
 * visible at a time); releasing with velocity flings with decay, then the
 * wheel settles onto the nearest tool step with a spring. Plain drags spin;
 * the dock-move gesture needs a long-press first, so the two never conflict.
 * Pass [spinEnabled] = false while a dock move is in progress.
 */
@Composable
fun SharedPdfSideWheelDock(
    dockLocation: DockLocation,
    backgroundColor: androidx.compose.ui.graphics.Color,
    rotationDeg: Float,
    onRotationChange: (Float) -> Unit,
    itemCount: Int,
    spinEnabled: Boolean = true,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    modifier: Modifier = Modifier,
    itemContent: @Composable BoxScope.(index: Int) -> Unit,
) {
    val isLeft = dockLocation != DockLocation.RIGHT
    val density = LocalDensity.current
    val orbitPx = with(density) { SharedPdfSideWheelOrbitRadius.toPx() }
    val trackPx = with(density) { SharedPdfSideWheelTrackThickness.toPx() }
    val cellHalfPx = with(density) { (SharedPdfSideWheelButtonSize / 2).toPx() }
    // Even full-circle distribution (no overlapping ends); partial arcs keep
    // the requested step.
    val effectiveStep = sharedPdfWheelEffectiveStepDeg(itemCount, stepDeg)
    val latestRotation by rememberUpdatedState(rotationDeg)
    val latestOnRotationChange by rememberUpdatedState(onRotationChange)
    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val tracker = remember(itemCount, stepDeg, orbitPx) { VelocityTracker() }
    fun cancelSpin() {
        flingJob?.cancel()
        flingJob = null
    }
    // Settle spring onto the nearest tool step so the wheel never rests
    // between tools after a drag or fling.
    suspend fun settleFrom(start: Float) {
        val target = sharedPdfWheelClampRotationDeg(
            rotationDeg = (start / effectiveStep).roundToInt() * effectiveStep,
            itemCount = itemCount,
            stepDeg = effectiveStep,
        )
        val anim = Animatable(start)
        anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) {
            latestOnRotationChange(value)
        }
    }
    fun settle() {
        flingJob?.cancel()
        flingJob = scope.launch { settleFrom(latestRotation) }
    }
    // A disabled wheel never keeps stale momentum.
    LaunchedEffect(spinEnabled) {
        if (!spinEnabled) cancelSpin()
    }
    val spinModifier = if (spinEnabled && itemCount > 1) {
        Modifier.pointerInput(itemCount, stepDeg, orbitPx) {
            detectDragGestures(
                onDragStart = {
                    cancelSpin()
                    tracker.resetTracking()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    latestOnRotationChange(
                        sharedPdfWheelRotationForDragDy(
                            rotationDeg = latestRotation,
                            dragDyPx = dragAmount.y,
                            orbitRadiusPx = orbitPx,
                            itemCount = itemCount,
                            stepDeg = effectiveStep,
                        ),
                    )
                },
                onDragEnd = {
                    val velocityYPxPerSec = try {
                        tracker.calculateVelocity().y
                    } catch (_: IllegalStateException) {
                        0f
                    }
                    val angularVelocityDegPerSec = if (orbitPx > 0f) {
                        velocityYPxPerSec / orbitPx * (180f / PI.toFloat())
                    } else {
                        0f
                    }
                    flingJob?.cancel()
                    flingJob = scope.launch {
                        // Frame-locked inertia with exponential friction:
                        // a real fling glides, then the wheel settles onto
                        // the nearest tool step. Stops early at the end
                        // stops so the settle spring runs immediately
                        // instead of after a pinned dead-time.
                        var velocityDegPerSec = angularVelocityDegPerSec.coerceIn(-3000f, 3000f)
                        if (abs(velocityDegPerSec) > 150f) {
                            var rotation = latestRotation
                            var lastFrameNanos = withFrameNanos { it }
                            while (abs(velocityDegPerSec) > 40f) {
                                val frameNanos = withFrameNanos { it }
                                val dtSec = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                                    .coerceIn(0f, 0.05f)
                                lastFrameNanos = frameNanos
                                velocityDegPerSec *= WheelFlingFrictionPerSec.pow(-dtSec)
                                val next = sharedPdfWheelClampRotationDeg(
                                    rotationDeg = rotation + velocityDegPerSec * dtSec,
                                    itemCount = itemCount,
                                    stepDeg = effectiveStep,
                                )
                                // Pinned against an end stop: settle now.
                                val hitEndStop = dtSec > 0f && next == rotation
                                rotation = next
                                latestOnRotationChange(rotation)
                                if (hitEndStop) break
                            }
                        }
                        settleFrom(latestRotation)
                    }
                },
                onDragCancel = { settle() },
            )
        }
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .width(SharedPdfSideWheelWidth)
            .height(SharedPdfSideWheelHeight)
            .then(spinModifier),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        // Thin arc band through the tool orbit: 0 degrees points inward
        // (+x for LEFT, -x for RIGHT). Canvas angles match the tool-angle
        // convention (0 = 3 o'clock, positive clockwise since y grows down).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = if (isLeft) {
                Offset(0f, containerHeightPx / 2f)
            } else {
                Offset(containerWidthPx, containerHeightPx / 2f)
            }
            val ovalTopLeft = Offset(center.x - orbitPx, center.y - orbitPx)
            drawArc(
                color = backgroundColor,
                startAngle = if (isLeft) -90f else 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = ovalTopLeft,
                size = Size(orbitPx * 2f, orbitPx * 2f),
                style = Stroke(width = trackPx, cap = StrokeCap.Round),
            )
        }
        for (index in 0 until itemCount) {
            val angle = sharedPdfWheelNormalizeDeg(
                sharedPdfWheelBaseAngleDeg(index, itemCount, stepDeg) + rotationDeg,
            )
            if (!sharedPdfWheelIsAngleVisible(angle)) continue
            val center = sharedPdfWheelToolCenterPx(
                angleDeg = angle,
                orbitRadiusPx = orbitPx,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                isLeft = isLeft,
            )
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (center.x - cellHalfPx).roundToInt(),
                        (center.y - cellHalfPx).roundToInt(),
                    )
                }.size(SharedPdfSideWheelButtonSize),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                itemContent(index)
            }
        }
    }
}
