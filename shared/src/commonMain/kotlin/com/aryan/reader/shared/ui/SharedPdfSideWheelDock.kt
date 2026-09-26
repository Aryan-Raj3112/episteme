package com.aryan.reader.shared.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.aryan.reader.shared.pdf.sharedPdfWheelIsAngleVisible
import com.aryan.reader.shared.pdf.sharedPdfWheelNormalizeDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelRotationForDragDy
import com.aryan.reader.shared.pdf.sharedPdfWheelToolCenterPx
import kotlin.math.roundToInt

/**
 * Scrollable semi-circle wheel for side-docked (LEFT/RIGHT) PDF toolbars.
 *
 * The background is a thin arc band barely taller than the icons — the
 * straight toolbar curved into an arc — with tools riding the orbit. A
 * vertical drag spins the wheel to bring hidden tools into view (4-5 are
 * visible at a time). Plain drags spin; the dock-move gesture needs a
 * long-press first, so the two never conflict. Pass [spinEnabled] = false
 * while a dock move is in progress.
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
    val latestRotation by rememberUpdatedState(rotationDeg)
    val spinModifier = if (spinEnabled && itemCount > 1) {
        Modifier.pointerInput(itemCount, stepDeg, orbitPx) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onRotationChange(
                    sharedPdfWheelRotationForDragDy(
                        rotationDeg = latestRotation,
                        dragDyPx = dragAmount.y,
                        orbitRadiusPx = orbitPx,
                        itemCount = itemCount,
                        stepDeg = stepDeg,
                    ),
                )
            }
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
