package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * The HSV colour-picker family: the spectrum, the brightness slider, the hex and RGB inputs, the
 * old/new compare pill, and the [SharedHsvColor] model they all convert through.
 *
 * [SharedHsvColorPickerEditor] composes the spectrum, slider and inputs into an editable body, and
 * [SharedHsvColorPickerDialog] wraps that in a standalone dialog. Lives apart from the app theme
 * settings that first needed it, because every reader surface (EPUB palette, PDF palette, ink
 * colours, reader theme colours) now builds on it rather than copying it.
 */

@Composable
fun SharedHsvSpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val touchPadding = 12.dp
    val rainbowColors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    Box(
        modifier = modifier.pointerInput(Unit) {
            val paddingPx = touchPadding.toPx()
            awaitSharedColorPickerDrag { offset ->
                val activeWidth = size.width.toFloat() - (paddingPx * 2f)
                val activeHeight = size.height.toFloat() - (paddingPx * 2f)
                val selectedHue = ((offset.x - paddingPx) / activeWidth).coerceIn(0f, 1f) * 360f
                val selectedSaturation = ((offset.y - paddingPx) / activeHeight).coerceIn(0f, 1f)
                onHueSatChanged(selectedHue, selectedSaturation)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize().padding(touchPadding).clip(RoundedCornerShape(12.dp))) {
            drawRect(brush = Brush.horizontalGradient(rainbowColors))
            drawRect(brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        }
        Canvas(Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val activeWidth = size.width - (paddingPx * 2f)
            val activeHeight = size.height - (paddingPx * 2f)
            val pointer = Offset(
                paddingPx + (hue / 360f) * activeWidth,
                paddingPx + saturation * activeHeight,
            )
            val pointerRadius = 10.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.25f), pointerRadius + 1.dp.toPx(), Offset(pointer.x, pointer.y + 1.dp.toPx()))
            drawCircle(currentColor.copy(alpha = 1f), pointerRadius, pointer)
            drawCircle(Color.White, pointerRadius, pointer, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun SharedHsvWheel(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    gestureKey: Any? = Unit
) {
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(gestureKey) {
            val paddingPx = touchPadding.toPx()
            awaitSharedColorPickerDrag { offset ->
                val selection = sharedHsvWheelSelection(
                    offsetX = offset.x,
                    offsetY = offset.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    paddingPx = paddingPx
                )
                onHueSatChanged(selection.hue, selection.saturation)
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val wheelRadius = ((min(size.width, size.height) - (paddingPx * 2f)) / 2f).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - wheelRadius, center.y - wheelRadius)
            val wheelSize = Size(wheelRadius * 2f, wheelRadius * 2f)
            val segments = 180
            val sweep = 360f / segments

            repeat(segments) { index ->
                val segmentHue = index * sweep
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.hsv(segmentHue, 1f, 1f)),
                        center = center,
                        radius = wheelRadius
                    ),
                    startAngle = segmentHue,
                    sweepAngle = sweep + 0.8f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = wheelSize
                )
            }

            drawCircle(
                color = Color.Black.copy(alpha = 0.16f),
                radius = wheelRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val wheelRadius = ((min(size.width, size.height) - (paddingPx * 2f)) / 2f).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val angle = hue.normalizedHue().toDouble() * PI / 180.0
            val radius = saturation.coerceIn(0f, 1f) * wheelRadius
            val pointer = Offset(
                x = center.x + (cos(angle).toFloat() * radius),
                y = center.y + (sin(angle).toFloat() * radius)
            )
            val pointerRadius = 10.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pointerRadius + 1.dp.toPx(),
                center = Offset(pointer.x, pointer.y + 1.dp.toPx())
            )
            drawCircle(
                color = currentColor.copy(alpha = 1f),
                radius = pointerRadius,
                center = pointer
            )
            drawCircle(
                color = Color.White,
                radius = pointerRadius,
                center = pointer,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun SharedSpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    gestureKey: Any? = Unit
) {
    val rainbowColors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red
    )
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(gestureKey) {
            val paddingPx = touchPadding.toPx()
            awaitSharedColorPickerDrag { offset ->
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)
                val relativeX = offset.x - paddingPx
                val relativeY = offset.y - paddingPx
                val nextHue = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                val nextSaturation = (relativeY / activeHeight).coerceIn(0f, 1f)
                onHueSatChanged(nextHue, nextSaturation)
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(touchPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            drawRect(brush = Brush.horizontalGradient(rainbowColors))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f))
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val activeWidth = size.width - (paddingPx * 2)
            val activeHeight = size.height - (paddingPx * 2)
            val x = paddingPx + (hue / 360f) * activeWidth
            val y = paddingPx + saturation * activeHeight
            val pointerRadius = 10.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pointerRadius + 1.dp.toPx(),
                center = Offset(x, y + 1.dp.toPx())
            )
            drawCircle(
                color = currentColor.copy(alpha = 1f),
                radius = pointerRadius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = pointerRadius,
                center = Offset(x, y),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun SharedBrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gestureKey: Any? = Unit
) {
    val baseColor = remember(hue, saturation) {
        Color.hsv(hue, saturation, 1f)
    }

    Box(
        modifier = modifier.pointerInput(gestureKey) {
            awaitSharedColorPickerDrag { offset ->
                val nextValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                onValueChanged(nextValue)
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, baseColor)
                )
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(value.coerceIn(0f, 1f) * size.width, size.height / 2)
            )
        }
    }
}

private suspend fun PointerInputScope.awaitSharedColorPickerDrag(
    onPosition: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        onPosition(down.position)
        val pointerId = down.id

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
            onPosition(change.position)
            change.consume()
            if (change.changedToUp() || !change.pressed) {
                return@awaitEachGesture
            }
        }
    }
}

@Composable
fun SharedRgbInputColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = (value.coerceIn(0f, 1f) * 255).roundToInt()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        SharedRgbInput(value = intValue, onValueChange = onValueChange)
    }
}

@Composable
private fun SharedRgbInput(
    value: Int,
    onValueChange: (Float) -> Unit
) {
    var textFieldValue by remember(value) {
        val text = value.coerceIn(0, 255).toString()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { nextValue ->
            val newText = nextValue.text
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                textFieldValue = nextValue
                newText.toIntOrNull()?.let { channel ->
                    onValueChange(channel.coerceIn(0, 255) / 255f)
                }
            }
        },
        textStyle = TextStyle(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        ),
        singleLine = true,
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp)
    )
}

@Composable
fun SharedHexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = color.toSharedHexString().removePrefix("#")
    var textFieldValue by remember(hexValue) {
        mutableStateOf(TextFieldValue(hexValue, TextRange(hexValue.length)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "#",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        BasicTextField(
            value = textFieldValue,
            onValueChange = { nextValue ->
                val newText = nextValue.text
                if (newText.length <= 6) {
                    val uppercased = newText.uppercase()
                    if (uppercased.all { it.isDigit() || it in 'A'..'F' }) {
                        textFieldValue = nextValue.copy(
                            text = uppercased,
                            selection = TextRange(nextValue.selection.end.coerceIn(0, uppercased.length))
                        )
                        if (uppercased.length == 6) {
                            uppercased.toSharedHexColorOrNull()?.let(onHexChanged)
                        }
                    }
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                textAlign = TextAlign.Start,
                fontSize = 13.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .padding(start = 2.dp)
                .width(50.dp)
        )
    }
}

@Composable
fun SharedColorComparePill(
    oldColor: Color,
    newColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(
            color = oldColor.copy(alpha = 1f),
            size = Size(size.width / 2, size.height)
        )
        drawRect(
            color = newColor.copy(alpha = 1f),
            topLeft = Offset(size.width / 2, 0f),
            size = Size(size.width / 2, size.height)
        )
    }
}

data class SharedHsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float
) {
    fun toComposeColor(): Color {
        return Color.hsv(
            hue.normalizedHue(),
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    }
}

internal fun sharedHsvWheelSelection(
    offsetX: Float,
    offsetY: Float,
    width: Float,
    height: Float,
    paddingPx: Float = 0f
): SharedHsvColor {
    val wheelRadius = ((min(width, height) - (paddingPx * 2f)) / 2f).coerceAtLeast(1f)
    val centerX = width / 2f
    val centerY = height / 2f
    val dx = offsetX - centerX
    val dy = offsetY - centerY
    val hue = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat().normalizedHue()
    val saturation = (sqrt(((dx * dx) + (dy * dy)).toDouble()).toFloat() / wheelRadius).coerceIn(0f, 1f)
    return SharedHsvColor(
        hue = hue,
        saturation = saturation,
        value = 1f
    )
}

fun Color.toSharedHsvColor(): SharedHsvColor {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    val saturation = if (maximum == 0f) 0f else delta / maximum
    return SharedHsvColor(
        hue = hue.normalizedHue(),
        saturation = saturation.coerceIn(0f, 1f),
        value = maximum.coerceIn(0f, 1f)
    )
}

internal fun Color.toSharedHexString(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return "#${rgb.toString(16).padStart(6, '0').uppercase()}"
}

internal fun String.toSharedHexColorOrNull(): Color? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
        return null
    }
    val rgb = normalized.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
}

private fun Float.normalizedHue(): Float {
    return ((this % 360f) + 360f) % 360f
}
