@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import org.commonmark.node.Text
import kotlin.math.roundToInt

@Composable
fun SpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                val paddingPx = touchPadding.toPx()
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)

                fun update(offset: Offset) {
                    val relativeX = offset.x - paddingPx
                    val relativeY = offset.y - paddingPx

                    val h = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                    val s = (relativeY / activeHeight).coerceIn(0f, 1f)
                    onHueSatChanged(h, s)
                }

                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(touchPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(rainbowColors)
            )
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
fun BrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = remember(hue, saturation) {
        Color.hsv(hue, saturation, 1f)
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(offset: Offset) {
                    val v = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChanged(v)
                }
                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, baseColor)
                )
            )

            val x = value * size.width
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, size.height / 2)
            )
        }
    }
}

@Composable
fun RgbInputColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = (value * 255).roundToInt()
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
        RgbInput(value = intValue, onValueChange = onValueChange)
    }
}

@Composable
fun RgbInput(
    value: Int,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        text = value.toString()
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                val intVal = newText.toIntOrNull()
                if (intVal != null) {
                    onValueChange(intVal.coerceIn(0, 255) / 255f)
                }
            }
        },
        textStyle = TextStyle(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp)
    )
}

@Composable
fun HexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = remember(color) {
        String.format("%06X", (0xFFFFFF and color.toArgb()))
    }
    var text by remember(hexValue) { mutableStateOf(hexValue) }

    LaunchedEffect(color) {
        val currentParsed = try {
            Color(("#$text").toColorInt())
        } catch (_: Exception) {
            null
        }
        if (currentParsed?.toArgb() != color.toArgb()) {
            text = String.format("%06X", (0xFFFFFF and color.toArgb()))
        }
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
            value = text,
            onValueChange = { newText ->
                if (newText.length <= 6) {
                    val uppercased = newText.uppercase()
                    if (uppercased.all { it.isDigit() || it in 'A'..'F' }) {
                        text = uppercased
                        if (uppercased.length == 6) {
                            try {
                                val parsedColorInt = "#$uppercased".toColorInt()
                                val newColor = Color(parsedColorInt)
                                onHexChanged(newColor)
                            } catch (_: Exception) {
                            }
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
fun ColorComparePill(
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

internal const val READER_TEXTURE_DIR = "reader_textures"
