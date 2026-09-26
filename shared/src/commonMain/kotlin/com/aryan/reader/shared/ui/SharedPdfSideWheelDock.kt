package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.DockLocation
import com.aryan.reader.shared.pdf.SharedPdfSideWheelButtonSize
import com.aryan.reader.shared.pdf.SharedPdfSideWheelHeight
import com.aryan.reader.shared.pdf.SharedPdfSideWheelOrbitRadius
import com.aryan.reader.shared.pdf.SharedPdfSideWheelStepDeg
import com.aryan.reader.shared.pdf.SharedPdfSideWheelWidth
import com.aryan.reader.shared.pdf.sharedPdfWheelBaseAngleDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelIsAngleVisible
import com.aryan.reader.shared.pdf.sharedPdfWheelRotationForDragDy
import com.aryan.reader.shared.pdf.sharedPdfWheelToolCenterPx
import kotlin.math.roundToInt

/**
 * Scrollable semi-circle wheel for side-docked (LEFT/RIGHT) PDF toolbars.
 *
 * Tools sit on a single arc protruding from the screen edge; a vertical drag
 * spins the wheel to bring hidden tools into view. Plain drags spin (the
 * dock-move gesture needs a long-press first, so the two never conflict);
 * pass [spinEnabled] = false while a dock move is in progress.
 */
@Composable
fun SharedPdfSideWheelDock(
    dockLocation: DockLocation,
    backgroundColor: Color,
    rotationDeg: Float,
    onRotationChange: (Float) -> Unit,
    itemCount: Int,
    spinEnabled: Boolean = true,
    stepDeg: Float = SharedPdfSideWheelStepDeg,
    modifier: Modifier = Modifier,
    itemContent: @Composable BoxScope.(index: Int) -> Unit,
) {
    val isLeft = dockLocation != DockLocation.RIGHT
    val shape = if (isLeft) {
        RoundedCornerShape(topEnd = SharedPdfSideWheelWidth, bottomEnd = SharedPdfSideWheelWidth)
    } else {
        RoundedCornerShape(topStart = SharedPdfSideWheelWidth, bottomStart = SharedPdfSideWheelWidth)
    }
    val density = LocalDensity.current
    val orbitPx = with(density) { SharedPdfSideWheelOrbitRadius.toPx() }
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
            .then(spinModifier)
            .clip(shape)
            .background(backgroundColor),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        // Axle hub on the flat edge: sells the rotary affordance.
        Box(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        for (index in 0 until itemCount) {
            val angle = sharedPdfWheelBaseAngleDeg(index, itemCount, stepDeg) + rotationDeg
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
                contentAlignment = Alignment.Center,
            ) {
                itemContent(index)
            }
        }
    }
}
