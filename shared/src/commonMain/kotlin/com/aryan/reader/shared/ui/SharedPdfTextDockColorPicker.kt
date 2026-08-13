package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.parsePdfTextDockHexColorOrNull
import com.aryan.reader.shared.pdf.pdfTextDockColorToHsv
import com.aryan.reader.shared.pdf.pdfTextDockRgbHex
import com.aryan.reader.shared.pdf.pdfTextDockHsvColor
import kotlin.math.roundToInt

data class SharedPdfTextDockColorPickerLabels(
    val back: String,
    val spectrum: String,
    val hex: String,
    val red: String,
    val green: String,
    val blue: String,
    val done: String,
)

@Composable
fun SharedPdfTextDockColorPicker(
    initialColor: Color,
    labels: SharedPdfTextDockColorPickerLabels,
    onBack: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    val lockedInitialColor = remember { initialColor }
    val initialHsv = remember(initialColor) { pdfTextDockColorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var value by remember { mutableFloatStateOf(initialHsv.value) }
    val currentColor by remember { derivedStateOf { pdfTextDockHsvColor(hue, saturation, value) } }

    fun updateFromColor(color: Color) {
        val hsv = pdfTextDockColorToHsv(color)
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
    }

    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF2C2C2C), modifier = Modifier.width(320.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, labels.back, tint = Color.White)
                }
                Text(labels.spectrum, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = Color.White, modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            SharedPdfTextDockSpectrumBox(hue, saturation, currentColor, { h, s -> hue = h; saturation = s },
                Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(16.dp))
            val gradient = remember(hue, saturation) {
                Brush.horizontalGradient(listOf(Color.Black, pdfTextDockHsvColor(hue, saturation, 1f)))
            }
            SharedPdfTextDockGradientSlider(value, { value = it }, currentColor, gradient)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SharedPdfTextDockColorCompare(lockedInitialColor, currentColor, Modifier.width(48.dp).height(36.dp))
                Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(labels.hex, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    SharedPdfTextDockHexInput(currentColor, ::updateFromColor)
                }
                Row(Modifier.weight(2.5f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SharedPdfTextDockRgbInputColumn(labels.red, currentColor.red, { updateFromColor(currentColor.copy(red = it)) }, Modifier.weight(1f))
                    SharedPdfTextDockRgbInputColumn(labels.green, currentColor.green, { updateFromColor(currentColor.copy(green = it)) }, Modifier.weight(1f))
                    SharedPdfTextDockRgbInputColumn(labels.blue, currentColor.blue, { updateFromColor(currentColor.copy(blue = it)) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onColorSelected(currentColor) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(40.dp), contentPadding = PaddingValues(0.dp)) {
                Text(labels.done, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SharedPdfTextDockPalettePicker(
    title: String,
    closeContentDescription: String,
    currentColor: Color,
    palette: List<Color>,
    showTransparent: Boolean = false,
    onColorSelected: (Color) -> Unit,
    onShowColorPicker: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1E1E1E), shadowElevation = 8.dp,
        modifier = Modifier.wrapContentWidth()) {
        Column(Modifier.padding(12.dp).width(IntrinsicSize.Min)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, closeContentDescription, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showTransparent) {
                    val selected = currentColor == Color.Transparent || currentColor == Color.Unspecified
                    Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .3f), androidx.compose.foundation.shape.CircleShape)
                        .clickable { onColorSelected(Color.Transparent) }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color(0xFFAAAAAA)); drawLine(Color(0xFF555555), Offset(size.width * .2f, size.height * .2f), Offset(size.width * .8f, size.height * .8f), 2.dp.toPx())
                        }
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                palette.forEachIndexed { index, color ->
                    val selected = if (color == Color.White) currentColor == color || currentColor == Color.Unspecified else currentColor == color
                    SharedPdfTextDockColorCircle(color, selected, { onColorSelected(color) }, { onShowColorPicker(index) })
                }
                val rainbow = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
                Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Brush.sweepGradient(rainbow))
                    .border(1.dp, Color.White.copy(alpha = .3f), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onShowColorPicker(palette.size.coerceAtMost(4)) })
            }
        }
    }
}

@Composable
private fun SharedPdfTextDockColorCircle(color: Color, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color)
        .then(if (color == Color.White) Modifier.border(1.dp, Color.White.copy(alpha = .3f), androidx.compose.foundation.shape.CircleShape) else Modifier)
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }) }, contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Default.Check, null, tint = if (color.luminance() > .6f) Color.Black else Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SharedPdfTextDockSpectrumBox(hue: Float, saturation: Float, currentColor: Color,
    onChanged: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    val touchPadding = 12.dp
    Box(modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val padding = touchPadding.toPx()
            fun update(offset: Offset) {
                onChanged(((offset.x - padding) / (size.width - padding * 2)).coerceIn(0f, 1f) * 360f,
                    ((offset.y - padding) / (size.height - padding * 2)).coerceIn(0f, 1f))
            }
            update(down.position)
            drag(down.id) { it.consume(); update(it.position) }
        }
    }) {
        Canvas(Modifier.fillMaxSize().padding(touchPadding).clip(RoundedCornerShape(12.dp))) {
            drawRect(Brush.horizontalGradient(colors))
            drawRect(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        }
        Canvas(Modifier.fillMaxSize()) {
            val padding = touchPadding.toPx()
            val center = Offset(padding + hue / 360f * (size.width - padding * 2), padding + saturation * (size.height - padding * 2))
            val radius = 10.dp.toPx()
            drawCircle(Color.Black.copy(alpha = .25f), radius + 1.dp.toPx(), center.copy(y = center.y + 1.dp.toPx()))
            drawCircle(currentColor.copy(alpha = 1f), radius, center)
            drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedPdfTextDockGradientSlider(value: Float, onValueChange: (Float) -> Unit, color: Color, brush: Brush) {
    val percent = (value * 100).roundToInt().coerceIn(0, 100)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SharedPdfTextDockStepButton("—", value > .0001f, 18) { onValueChange((value - .01f).coerceAtLeast(0f)) }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            Slider(value, onValueChange, valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.height(32.dp),
                thumb = { Surface(shape = androidx.compose.foundation.shape.CircleShape, color = color, modifier = Modifier.size(26.dp).padding(2.dp),
                    shadowElevation = 4.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)) {
                    Box(contentAlignment = Alignment.Center) { Text(percent.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                } },
                track = { Canvas(Modifier.fillMaxWidth().height(16.dp)) { drawRoundRect(brush, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)) } })
        }
        Spacer(Modifier.width(4.dp))
        SharedPdfTextDockStepButton("+", value < .9999f, 22) { onValueChange((value + .01f).coerceAtMost(1f)) }
    }
}

@Composable
private fun SharedPdfTextDockStepButton(text: String, enabled: Boolean, fontSize: Int, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = .3f), fontSize = fontSize.sp,
            fontWeight = if (text == "+") FontWeight.Normal else FontWeight.Bold)
    }
}

@Composable
private fun SharedPdfTextDockRgbInputColumn(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 11.sp, maxLines = 1); Spacer(Modifier.height(4.dp))
        var text by remember(value) { mutableStateOf((value * 255).roundToInt().toString()) }
        LaunchedEffect(value) { text = (value * 255).roundToInt().toString() }
        BasicTextField(text, { next -> if (next.length <= 3 && next.all(Char::isDigit)) { text = next; next.toIntOrNull()?.let { onValueChange(it.coerceIn(0, 255) / 255f) } } },
            textStyle = TextStyle(Color.White, fontSize = 13.sp, textAlign = TextAlign.Center), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp)).padding(vertical = 9.dp))
    }
}

@Composable
private fun SharedPdfTextDockHexInput(color: Color, onChanged: (Color) -> Unit) {
    val hex = remember(color) { pdfTextDockRgbHex(color) }
    var text by remember(hex) { mutableStateOf(hex) }
    LaunchedEffect(color) { if (parsePdfTextDockHexColorOrNull(text)?.toArgb() != color.toArgb()) text = pdfTextDockRgbHex(color) }
    Row(Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("#", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        BasicTextField(text, { next -> if (next.length <= 6) { val upper = next.uppercase(); if (upper.all { it.isDigit() || it in 'A'..'F' }) {
            text = upper; if (upper.length == 6) parsePdfTextDockHexColorOrNull(upper)?.let(onChanged)
        } } }, textStyle = TextStyle(Color.White, fontSize = 13.sp, textAlign = TextAlign.Start), singleLine = true,
            cursorBrush = SolidColor(Color.White), modifier = Modifier.padding(start = 2.dp).width(50.dp))
    }
}

@Composable
private fun SharedPdfTextDockColorCompare(oldColor: Color, newColor: Color, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(oldColor.copy(alpha = 1f), size = androidx.compose.ui.geometry.Size(size.width / 2, size.height))
        drawRect(newColor.copy(alpha = 1f), topLeft = Offset(size.width / 2, 0f), size = androidx.compose.ui.geometry.Size(size.width / 2, size.height))
    }
}
