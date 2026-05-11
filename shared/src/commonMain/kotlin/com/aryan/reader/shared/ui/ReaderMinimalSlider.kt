package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun ReaderMinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeStarted: (() -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    thumbColor: Color? = null
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive

    fun valueForOffset(offsetX: Float): Float {
        if (widthPx <= 0f || rangeEnd <= rangeStart) return value.coerceIn(rangeStart, rangeEnd)
        val fraction = (offsetX / widthPx).coerceIn(0f, 1f)
        return rangeStart + (rangeEnd - rangeStart) * fraction
    }

    val inputModifier = if (enabled) {
        Modifier.pointerInput(rangeStart, rangeEnd, widthPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onValueChangeStarted?.invoke()
                onValueChange(valueForOffset(down.position.x))
                down.consume()

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    onValueChange(valueForOffset(change.position.x))
                    change.consume()
                }

                onValueChangeFinished?.invoke()
            }
        }
    } else {
        Modifier
    }

    val effectiveActiveColor = activeColor ?: MaterialTheme.colorScheme.primary
    val effectiveInactiveColor = inactiveColor ?: MaterialTheme.colorScheme.surfaceVariant
    val effectiveThumbColor = thumbColor ?: MaterialTheme.colorScheme.primary
    val disabledAlpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(inputModifier)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val range = rangeEnd - rangeStart
            val fraction = if (range > 0f) {
                ((value.coerceIn(rangeStart, rangeEnd) - rangeStart) / range).coerceIn(0f, 1f)
            } else {
                0f
            }
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val centerY = size.height / 2f
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val activeWidth = size.width * fraction

            drawRoundRect(
                color = effectiveInactiveColor.copy(alpha = effectiveInactiveColor.alpha * disabledAlpha),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = effectiveActiveColor.copy(alpha = effectiveActiveColor.alpha * disabledAlpha),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(activeWidth, trackHeight),
                cornerRadius = cornerRadius
            )

            val thumbCenterX = if (size.width <= thumbRadius * 2f) {
                size.width / 2f
            } else {
                activeWidth.coerceIn(thumbRadius, size.width - thumbRadius)
            }
            drawCircle(
                color = effectiveThumbColor.copy(alpha = effectiveThumbColor.alpha * disabledAlpha),
                radius = thumbRadius,
                center = Offset(thumbCenterX, centerY)
            )
        }
    }
}
