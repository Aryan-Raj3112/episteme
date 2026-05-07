package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.AppContrastOption
import com.aryan.reader.shared.AppThemeMode
import com.aryan.reader.shared.CustomAppTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlin.math.roundToInt
import kotlin.random.Random

private val SharedLightColorScheme = lightColorScheme(
    primary = Color(0xFF4C662B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3),
    onPrimaryContainer = Color(0xFF354E16),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7C8),
    onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF1F4E4B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F312A),
    inverseOnSurface = Color(0xFFF1F2E6),
    inversePrimary = Color(0xFFB1D18A),
    surfaceDim = Color(0xFFDADBD0),
    surfaceBright = Color(0xFFF9FAEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8)
)

private val SharedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = Color(0xFFCDEDA3),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331E),
    secondaryContainer = Color(0xFF404A33),
    onSecondaryContainer = Color(0xFFDCE7C8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2E3D8),
    inverseOnSurface = Color(0xFF2F312A),
    inversePrimary = Color(0xFF4C662B),
    surfaceDim = Color(0xFF12140E),
    surfaceBright = Color(0xFF383A32),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E)
)

@Composable
fun SharedAppTheme(
    appThemeMode: AppThemeMode,
    appContrastOption: AppContrastOption,
    appTextDimFactorLight: Float,
    appTextDimFactorDark: Float,
    appSeedColor: Color?,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveSharedAppDarkTheme(appThemeMode, isSystemInDarkTheme())
    val textDimFactor = sharedAppTextDimFactor(darkTheme, appTextDimFactorLight, appTextDimFactorDark)
    val colorScheme = remember(darkTheme, appContrastOption, textDimFactor, appSeedColor) {
        sharedAppColorScheme(
            darkTheme = darkTheme,
            seedColor = appSeedColor,
            contrastLevel = appContrastOption.value,
            textDimFactor = textDimFactor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

fun resolveSharedAppDarkTheme(mode: AppThemeMode, isSystemDark: Boolean): Boolean {
    return when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemDark
    }
}

fun sharedAppTextDimFactor(
    darkTheme: Boolean,
    lightFactor: Float,
    darkFactor: Float
): Float {
    return if (darkTheme) darkFactor else lightFactor
}

fun sharedAppColorScheme(
    darkTheme: Boolean,
    seedColor: Color?,
    contrastLevel: Double,
    textDimFactor: Float
): ColorScheme {
    val baseColorScheme = seedColor?.let {
        dynamicColorScheme(
            seedColor = it,
            isDark = darkTheme,
            contrastLevel = contrastLevel,
            style = PaletteStyle.Fidelity
        )
    } ?: if (darkTheme) {
        SharedDarkColorScheme
    } else {
        SharedLightColorScheme
    }

    return baseColorScheme.withTextDimFactor(textDimFactor)
}

@Composable
fun SharedAppThemeSettingsDialog(
    appThemeMode: AppThemeMode,
    appContrastOption: AppContrastOption,
    appTextDimFactorLight: Float,
    appTextDimFactorDark: Float,
    appSeedColor: Color?,
    customAppThemes: List<CustomAppTheme>,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onContrastOptionChanged: (AppContrastOption) -> Unit,
    onTextDimFactorLightChanged: (Float) -> Unit,
    onTextDimFactorDarkChanged: (Float) -> Unit,
    onSeedColorChanged: (Color?) -> Unit,
    onCustomThemeAdded: (CustomAppTheme) -> Unit,
    onCustomThemeDeleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App theme", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsLabel("Appearance")
                SegmentedControl(
                    values = AppThemeMode.entries,
                    selectedValue = appThemeMode,
                    label = { it.label },
                    onValueSelected = onThemeModeChanged
                )

                SettingsLabel("Contrast")
                SegmentedControl(
                    values = AppContrastOption.entries,
                    selectedValue = appContrastOption,
                    label = { it.label },
                    onValueSelected = onContrastOptionChanged
                )

                if (appThemeMode == AppThemeMode.SYSTEM) {
                    TextBrightnessSlider(
                        label = "Text brightness (Light)",
                        value = appTextDimFactorLight,
                        onValueChange = onTextDimFactorLightChanged
                    )
                    TextBrightnessSlider(
                        label = "Text brightness (Dark)",
                        value = appTextDimFactorDark,
                        onValueChange = onTextDimFactorDarkChanged
                    )
                } else {
                    TextBrightnessSlider(
                        label = "Text brightness",
                        value = if (appThemeMode == AppThemeMode.DARK) appTextDimFactorDark else appTextDimFactorLight,
                        onValueChange = if (appThemeMode == AppThemeMode.DARK) onTextDimFactorDarkChanged else onTextDimFactorLightChanged
                    )
                }

                SettingsLabel("Color scheme")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeSwatch(
                        color = MaterialTheme.colorScheme.primary,
                        selected = appSeedColor == null,
                        label = "Dynamic",
                        onClick = { onSeedColorChanged(null) }
                    )
                    AppThemePresets.forEach { preset ->
                        ThemeSwatch(
                            color = preset.color,
                            selected = appSeedColor == preset.color,
                            label = preset.name,
                            onClick = { onSeedColorChanged(preset.color) }
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsLabel("My themes")
                    IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom theme")
                    }
                }

                if (customAppThemes.isEmpty()) {
                    Text(
                        "No custom themes yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        customAppThemes.forEach { theme ->
                            ThemeSwatch(
                                color = theme.seedColor,
                                selected = appSeedColor == theme.seedColor,
                                label = theme.name,
                                onClick = { onSeedColorChanged(theme.seedColor) },
                                onDelete = { onCustomThemeDeleted(theme.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showCreateDialog) {
        SharedCreateAppThemeDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, color ->
                onCustomThemeAdded(
                    CustomAppTheme(
                        id = Random.nextLong().toString(),
                        name = name.ifBlank { "Custom" },
                        seedColor = color
                    )
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun SettingsLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun <T> SegmentedControl(
    values: List<T>,
    selectedValue: T,
    label: (T) -> String,
    onValueSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        values.forEach { value ->
            val selected = selectedValue == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onValueSelected(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(value),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TextBrightnessSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "A",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Slider(
                value = value.coerceIn(0.3f, 1.0f),
                onValueChange = { onValueChange(it.coerceIn(0.3f, 1.0f)) },
                valueRange = 0.3f..1.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )
            Text(
                "A",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 72.dp)
            )
            if (onDelete != null) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp).clickable(onClick = onDelete)
                )
            }
        }
    }
}

@Composable
private fun SharedCreateAppThemeDialog(
    initialColor: Color = Color(0xFF6750A4),
    onDismiss: () -> Unit,
    onSave: (String, Color) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hsv by remember(initialColor) { mutableStateOf(initialColor.toSharedHsvColor()) }
    val color = hsv.toComposeColor()

    fun updateFromColor(nextColor: Color) {
        hsv = nextColor.toSharedHsvColor()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create theme") },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SharedSpectrumBox(
                    hue = hsv.hue,
                    saturation = hsv.saturation,
                    currentColor = color,
                    onHueSatChanged = { hue, saturation ->
                        hsv = hsv.copy(hue = hue, saturation = saturation)
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )

                SharedBrightnessSlider(
                    hue = hsv.hue,
                    saturation = hsv.saturation,
                    value = hsv.value,
                    onValueChanged = { hsv = hsv.copy(value = it) },
                    modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SharedColorComparePill(
                        oldColor = initialColor,
                        newColor = color,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Hex", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        SharedHexInput(color = color, onHexChanged = { updateFromColor(it) })
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SharedRgbInputColumn(
                            label = "R",
                            value = color.red,
                            onValueChange = { updateFromColor(color.copy(red = it)) },
                            modifier = Modifier.weight(1f)
                        )
                        SharedRgbInputColumn(
                            label = "G",
                            value = color.green,
                            onValueChange = { updateFromColor(color.copy(green = it)) },
                            modifier = Modifier.weight(1f)
                        )
                        SharedRgbInputColumn(
                            label = "B",
                            value = color.blue,
                            onValueChange = { updateFromColor(color.copy(blue = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim().ifBlank { "Custom" }, color) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SharedSpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val paddingPx = touchPadding.toPx()
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)

                fun update(offset: Offset) {
                    val relativeX = offset.x - paddingPx
                    val relativeY = offset.y - paddingPx
                    val nextHue = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                    val nextSaturation = (relativeY / activeHeight).coerceIn(0f, 1f)
                    onHueSatChanged(nextHue, nextSaturation)
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
private fun SharedBrightnessSlider(
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
                    val nextValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChanged(nextValue)
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
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(value.coerceIn(0f, 1f) * size.width, size.height / 2)
            )
        }
    }
}

@Composable
private fun SharedRgbInputColumn(
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
    var text by remember(value) { mutableStateOf(value.coerceIn(0, 255).toString()) }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                text = newText
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
private fun SharedHexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = color.toSharedHexString().removePrefix("#")
    var text by remember(hexValue) { mutableStateOf(hexValue) }

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
private fun SharedColorComparePill(
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

private fun ColorScheme.withTextDimFactor(factor: Float): ColorScheme {
    val dimFactor = factor.coerceIn(0.3f, 1.0f)
    if (dimFactor >= 1.0f) return this
    return copy(
        primary = primary.copy(alpha = dimFactor),
        secondary = secondary.copy(alpha = dimFactor),
        tertiary = tertiary.copy(alpha = dimFactor),
        error = error.copy(alpha = dimFactor),
        primaryContainer = primaryContainer.copy(alpha = dimFactor),
        secondaryContainer = secondaryContainer.copy(alpha = dimFactor),
        tertiaryContainer = tertiaryContainer.copy(alpha = dimFactor),
        errorContainer = errorContainer.copy(alpha = dimFactor),
        outline = outline.copy(alpha = dimFactor),
        outlineVariant = outlineVariant.copy(alpha = dimFactor),
        inversePrimary = inversePrimary.copy(alpha = dimFactor),
        inverseOnSurface = inverseOnSurface.copy(alpha = dimFactor),
        onPrimary = onPrimary.copy(alpha = dimFactor),
        onSecondary = onSecondary.copy(alpha = dimFactor),
        onTertiary = onTertiary.copy(alpha = dimFactor),
        onBackground = onBackground.copy(alpha = dimFactor),
        onSurface = onSurface.copy(alpha = dimFactor),
        onSurfaceVariant = onSurfaceVariant.copy(alpha = dimFactor),
        onError = onError.copy(alpha = dimFactor),
        onPrimaryContainer = onPrimaryContainer.copy(alpha = dimFactor),
        onSecondaryContainer = onSecondaryContainer.copy(alpha = dimFactor),
        onTertiaryContainer = onTertiaryContainer.copy(alpha = dimFactor),
        onErrorContainer = onErrorContainer.copy(alpha = dimFactor)
    )
}

private data class AppThemePreset(
    val name: String,
    val color: Color
)

private val AppThemePresets = listOf(
    AppThemePreset("Ocean", Color(0xFF00668B)),
    AppThemePreset("Mint", Color(0xFF006C4C)),
    AppThemePreset("Rose", Color(0xFF9C4146)),
    AppThemePreset("Sepia", Color(0xFF705D49)),
    AppThemePreset("Amethyst", Color(0xFF9B59B6)),
    AppThemePreset("Amber", Color(0xFFFFC107)),
    AppThemePreset("Sapphire", Color(0xFF0F52BA))
)

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "System"
        AppThemeMode.LIGHT -> "Light"
        AppThemeMode.DARK -> "Dark"
    }

private val AppContrastOption.label: String
    get() = when (this) {
        AppContrastOption.STANDARD -> "Standard"
        AppContrastOption.MEDIUM -> "Medium"
        AppContrastOption.HIGH -> "High"
    }

internal data class SharedHsvColor(
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

internal fun Color.toSharedHsvColor(): SharedHsvColor {
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
