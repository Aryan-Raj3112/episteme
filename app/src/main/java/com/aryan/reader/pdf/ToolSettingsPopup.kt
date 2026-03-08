/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsPopup(
    selectedTool: InkType,
    activeToolThickness: Float,
    fountainPenColor: Color,
    markerColor: Color,
    pencilColor: Color,
    highlighterColor: Color,
    highlighterRoundColor: Color,
    activePalette: List<Color>,
    onToolTypeChanged: (InkType) -> Unit,
    onColorChanged: (Color) -> Unit,
    onThicknessChanged: (Float) -> Unit,
    onPaletteChange: (List<Color>) -> Unit,
    modifier: Modifier = Modifier,
    isHighlighterSnapEnabled: Boolean = false,
    onSnapToggle: (Boolean) -> Unit = {}
) {
    val isHighlighter = selectedTool == InkType.HIGHLIGHTER || selectedTool == InkType.HIGHLIGHTER_ROUND

    val activeColor = when (selectedTool) {
        InkType.FOUNTAIN_PEN -> fountainPenColor
        InkType.PEN -> markerColor
        InkType.PENCIL -> pencilColor
        InkType.HIGHLIGHTER -> highlighterColor
        InkType.HIGHLIGHTER_ROUND -> highlighterRoundColor
        else -> markerColor
    }

    val currentAlpha = activeColor.alpha

    val safeOnColorChanged: (Color) -> Unit = { newColor ->
        if (isHighlighter) {
            onColorChanged(newColor.copy(alpha = currentAlpha))
        } else {
            onColorChanged(newColor)
        }
    }

    // Thickness settings
    val thicknessRange = if (isHighlighter) 0.01f..0.06f else 0.001f..0.015f
    @Suppress("UnusedExpression") if (isHighlighter) 0.005f else 0.001f

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerSlotIndex by remember { mutableIntStateOf(-1) }

    val currentOnColorChanged by rememberUpdatedState(safeOnColorChanged)

    val selectedPaletteIndex = remember(activePalette, activeColor, isHighlighter) {
        activePalette.indexOfFirst { paletteColor ->
            if (isHighlighter) {
                paletteColor.copy(alpha = 1f) == activeColor.copy(alpha = 1f)
            } else {
                paletteColor == activeColor
            }
        }
    }

    val circleSize = 28.dp

    Surface(
        modifier = modifier
            .width(360.dp)
            .padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (isHighlighter) {
                        PenItem(
                            type = PenType.HIGHLIGHTER,
                            forcedInkType = InkType.HIGHLIGHTER,
                            color = highlighterColor.copy(alpha = 1f),
                            inkColor = highlighterColor,
                            isSelected = selectedTool == InkType.HIGHLIGHTER,
                            strokeWidth = activeToolThickness,
                            onClick = { onToolTypeChanged(InkType.HIGHLIGHTER) },
                            isSnappingEnabled = isHighlighterSnapEnabled
                        )

                        PenItem(
                            type = PenType.HIGHLIGHTER_ROUND,
                            forcedInkType = InkType.HIGHLIGHTER_ROUND,
                            color = highlighterRoundColor.copy(alpha = 1f),
                            inkColor = highlighterRoundColor,
                            isSelected = selectedTool == InkType.HIGHLIGHTER_ROUND,
                            strokeWidth = activeToolThickness,
                            onClick = { onToolTypeChanged(InkType.HIGHLIGHTER_ROUND) },
                            isSnappingEnabled = isHighlighterSnapEnabled
                        )
                    } else {
                        PenItem(
                            type = PenType.FOUNTAIN_PEN,
                            forcedInkType = InkType.FOUNTAIN_PEN,
                            color = fountainPenColor,
                            isSelected = selectedTool == InkType.FOUNTAIN_PEN,
                            strokeWidth = activeToolThickness,
                            onClick = { onToolTypeChanged(InkType.FOUNTAIN_PEN) }
                        )

                        PenItem(
                            type = PenType.MARKER,
                            forcedInkType = InkType.PEN,
                            color = markerColor,
                            isSelected = selectedTool == InkType.PEN,
                            strokeWidth = activeToolThickness,
                            onClick = { onToolTypeChanged(InkType.PEN) }
                        )

                        PenItem(
                            type = PenType.PENCIL,
                            forcedInkType = InkType.PENCIL,
                            color = pencilColor,
                            isSelected = selectedTool == InkType.PENCIL,
                            strokeWidth = activeToolThickness,
                            onClick = { onToolTypeChanged(InkType.PENCIL) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isHighlighter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Straight Line",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isHighlighterSnapEnabled,
                        onCheckedChange = onSnapToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = activeColor.copy(alpha = 1f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF424242)
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // THICKNESS SLIDER
            StyledPropertySlider(
                value = activeToolThickness,
                onValueChange = onThicknessChanged,
                valueRange = thicknessRange, isOpacity = false,
                trackColor = Color(0xFF424242),
                thumbColor = Color(0xFF757575),
                activeColor = activeColor
            )

            // Darkness (Opacity) Slider for Highlighters
            if (isHighlighter) {
                Spacer(Modifier.height(16.dp))

                StyledPropertySlider(
                    value = currentAlpha,
                    onValueChange = { newAlpha ->
                        onColorChanged(activeColor.copy(alpha = newAlpha))
                    },
                    valueRange = 0.1f..1.0f, isOpacity = true,
                    trackColor = activeColor.copy(alpha = 1f),
                    thumbColor = activeColor.copy(alpha = 1f),
                    activeColor = activeColor
                )
            }

            Spacer(Modifier.height(16.dp)) // Reduced spacing

            // --- Color Palette ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    activePalette.take(6).forEachIndexed { index, color ->
                        val isSelected = index == selectedPaletteIndex

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(circleSize)
                                .testTag("Palette_Item_$index")
                                .pointerInput(color) {
                                    detectTapGestures(
                                        onTap = {
                                            currentOnColorChanged(color)
                                        },
                                        onLongPress = {
                                            colorPickerSlotIndex = index
                                            showColorPicker = true
                                        }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = color.copy(alpha = 1f))
                                if (isSelected) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = size.minDimension / 2,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(circleSize)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Spacer(Modifier.width(16.dp))

                // Spectrum / Color Wheel Button
                val rainbowColors = listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(rainbowColors))
                        .clickable {
                            if (selectedPaletteIndex != -1) {
                                colorPickerSlotIndex = selectedPaletteIndex
                                showColorPicker = true
                            }
                        }
                ) {}
            }
        }
    }

    if (showColorPicker && colorPickerSlotIndex != -1) {
        val initialColor = activePalette.getOrElse(colorPickerSlotIndex) { Color.Black }
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { newColor ->
                val mutableList = activePalette.toMutableList()
                if (colorPickerSlotIndex in mutableList.indices) {
                    mutableList[colorPickerSlotIndex] = newColor
                    onPaletteChange(mutableList)
                    if (isHighlighter) {
                        onColorChanged(newColor.copy(alpha = currentAlpha))
                    } else {
                        onColorChanged(newColor)
                    }
                }
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val lockedInitialColor = remember { initialColor }

    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val alpha = 1.0f

    val currentColor by remember {
        derivedStateOf {
            val hsv = floatArrayOf(hue, saturation, value)
            val argb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv)
            Color(argb)
        }
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Spectrum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(20.dp))

                SpectrumBox(
                    hue = hue,
                    saturation = saturation,
                    currentColor = currentColor,
                    onHueSatChanged = { h, s ->
                        hue = h
                        saturation = s
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )

                Spacer(Modifier.height(20.dp))

                BrightnessSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onValueChanged = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorComparePill(
                        oldColor = lockedInitialColor,
                        newColor = currentColor,
                        modifier = Modifier
                            .width(64.dp)
                            .height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Hex",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        HexInput(
                            color = currentColor,
                            onHexChanged = { updateFromColor(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RgbInputColumn(
                            label = "Red",
                            value = currentColor.red,
                            onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(
                            label = "Green",
                            value = currentColor.green,
                            onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(
                            label = "Blue",
                            value = currentColor.blue,
                            onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onColorSelected(currentColor) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumBox(
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
private fun BrightnessSlider(
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
private fun RgbInputColumn(
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
private fun RgbInput(
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
private fun HexInput(
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
private fun ColorComparePill(
    oldColor: Color,
    newColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(
            color = oldColor.copy(alpha = 1f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
        drawRect(
            color = newColor.copy(alpha = 1f),
            topLeft = Offset(size.width / 2, 0f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
    }
}

@Composable
private fun PenItem(
    type: PenType,
    color: Color,
    isSelected: Boolean,
    strokeWidth: Float,
    onClick: () -> Unit,
    forcedInkType: InkType? = null,
    inkColor: Color? = null,
    isSnappingEnabled: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 0.9f, label = "scale"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(100.dp)
            .scale(scale)
            .testTag("SettingsItem_${type.name}")
            .semantics { this.selected = isSelected }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        PenIcon(
            color = color,
            inkColor = inkColor,
            type = type,
            isSelected = isSelected,
            strokeWidth = strokeWidth,
            forcedInkType = forcedInkType,
            modifier = Modifier.fillMaxSize(),
            isSnappingEnabled = isSnappingEnabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledPropertySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>, isOpacity: Boolean,
    trackColor: Color,
    thumbColor: Color,
    activeColor: Color
) {
    val displayValue = remember(value, valueRange) {
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        (fraction * 100).roundToInt().coerceIn(1, 100)
    }
    val onePercentDelta = (valueRange.endInclusive - valueRange.start) / 100f
    val canDecrease = value > valueRange.start + 0.0001f
    val canIncrease = value < valueRange.endInclusive - 0.0001f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Minus Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .testTag("Property_Minus")
                .clickable(enabled = canDecrease) {
                    val newValue = (value - onePercentDelta).coerceAtLeast(valueRange.start)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                color = if (canDecrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Custom Slider
        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(32.dp),
                thumb = {
                    Surface(
                        shape = CircleShape,
                        color = thumbColor,
                        modifier = Modifier
                            .size(26.dp)
                            .padding(2.dp),
                        shadowElevation = 4.dp,
                        border = if (isOpacity) null else androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayValue.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                track = { _ ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    ) {
                        val trackHeight = size.height
                        val cornerRadius = CornerRadius(trackHeight / 2)

                        if (isOpacity) {
                            drawRoundRect(
                                color = Color.Gray,
                                size = size,
                                cornerRadius = cornerRadius
                            )

                            val clipPath = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        rect = androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                                        cornerRadius = cornerRadius
                                    )
                                )
                            }

                            clipPath(clipPath) {
                                val boxSize = 12f
                                val columns = (size.width / boxSize).toInt() + 1
                                val rows = (size.height / boxSize).toInt() + 1

                                for (col in 0 until columns) {
                                    for (row in 0 until rows) {
                                        val color = if ((col + row) % 2 == 0) Color(0xFF555555) else Color(0xFF333333)
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(col * boxSize, row * boxSize),
                                            size = androidx.compose.ui.geometry.Size(boxSize, boxSize)
                                        )
                                    }
                                }
                                drawRect(color = activeColor)
                            }

                        } else {
                            drawRoundRect(
                                color = trackColor.copy(alpha = 0.5f),
                                size = size,
                                cornerRadius = cornerRadius
                            )

                            val dotRadius = 1.5.dp.toPx()
                            val padding = trackHeight / 2
                            val availableWidth = size.width - (padding * 2)
                            val dotCount = 8
                            val spacing = availableWidth / (dotCount - 1)

                            for (i in 0 until dotCount) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.2f),
                                    radius = dotRadius,
                                    center = Offset(padding + (i * spacing), size.height / 2)
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Plus Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .testTag("Property_Plus")
                .clickable(enabled = canIncrease) {
                    val newValue = (value + onePercentDelta).coerceAtMost(valueRange.endInclusive)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (canIncrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}