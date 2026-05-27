package com.aryan.reader

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import kotlin.math.roundToInt

private const val READER_PREFS_NAME = "reader_prefs"
private const val PREF_READER_BRIGHTNESS_USE_SYSTEM = "reader_brightness_use_system"
private const val PREF_READER_BRIGHTNESS_VALUE = "reader_brightness_value"
private const val DEFAULT_CUSTOM_BRIGHTNESS = 0.75f
private const val MIN_CUSTOM_BRIGHTNESS_PERCENT = 1
private const val MAX_CUSTOM_BRIGHTNESS_PERCENT = 100
private const val CUSTOM_BRIGHTNESS_STEP_PERCENT = 1
private const val MIN_CUSTOM_BRIGHTNESS = 0.01f

data class ReaderBrightnessSettings(
    val useSystemBrightness: Boolean = true,
    val customBrightness: Float = DEFAULT_CUSTOM_BRIGHTNESS
) {
    val safeCustomBrightness: Float
        get() = normalizeReaderBrightness(customBrightness)
}

internal fun normalizeReaderBrightness(brightness: Float): Float {
    val percent = (brightness * 100f).roundToInt()
        .coerceIn(MIN_CUSTOM_BRIGHTNESS_PERCENT, MAX_CUSTOM_BRIGHTNESS_PERCENT)
    return percent / 100f
}

internal fun stepReaderBrightness(brightness: Float, percentDelta: Int): Float {
    val currentPercent = (normalizeReaderBrightness(brightness) * 100f).roundToInt()
    val nextPercent = (currentPercent + percentDelta)
        .coerceIn(MIN_CUSTOM_BRIGHTNESS_PERCENT, MAX_CUSTOM_BRIGHTNESS_PERCENT)
    return nextPercent / 100f
}

fun loadReaderBrightnessSettings(context: Context): ReaderBrightnessSettings {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return ReaderBrightnessSettings(
        useSystemBrightness = prefs.getBoolean(PREF_READER_BRIGHTNESS_USE_SYSTEM, true),
        customBrightness = prefs.getFloat(PREF_READER_BRIGHTNESS_VALUE, DEFAULT_CUSTOM_BRIGHTNESS)
            .let(::normalizeReaderBrightness)
    )
}

fun saveReaderBrightnessSettings(context: Context, settings: ReaderBrightnessSettings) {
    context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE).edit {
        putBoolean(PREF_READER_BRIGHTNESS_USE_SYSTEM, settings.useSystemBrightness)
        putFloat(PREF_READER_BRIGHTNESS_VALUE, settings.safeCustomBrightness)
    }
}

@Composable
fun ReaderBrightnessEffect(
    window: Window?,
    settings: ReaderBrightnessSettings
) {
    DisposableEffect(window) {
        val originalBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            window?.setReaderBrightness(originalBrightness)
        }
    }

    LaunchedEffect(window, settings) {
        val brightness = if (settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.safeCustomBrightness
        }
        window?.setReaderBrightness(brightness)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBrightnessSheet(
    settings: ReaderBrightnessSettings,
    onSettingsChange: (ReaderBrightnessSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.reader_brightness_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_done))
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_brightness_system),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.reader_brightness_system_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.useSystemBrightness,
                    onCheckedChange = { useSystem ->
                        onSettingsChange(settings.copy(useSystemBrightness = useSystem))
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.reader_brightness_custom),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.reader_brightness_percent,
                            (settings.safeCustomBrightness * 100f).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ReaderBrightnessControl(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
                Text(
                    text = stringResource(R.string.reader_brightness_custom_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ReaderBrightnessControl(
    settings: ReaderBrightnessSettings,
    onSettingsChange: (ReaderBrightnessSettings) -> Unit
) {
    val brightness = settings.safeCustomBrightness
    val canDecrease = brightness > MIN_CUSTOM_BRIGHTNESS
    val canIncrease = brightness < 1f

    fun updateBrightness(value: Float) {
        onSettingsChange(
            settings.copy(
                useSystemBrightness = false,
                customBrightness = normalizeReaderBrightness(value)
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = {
                updateBrightness(stepReaderBrightness(brightness, -CUSTOM_BRIGHTNESS_STEP_PERCENT))
            },
            enabled = canDecrease,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.content_desc_decrease),
                modifier = Modifier.size(18.dp)
            )
        }
        ReaderMinimalSlider(
            value = brightness,
            onValueChange = ::updateBrightness,
            valueRange = MIN_CUSTOM_BRIGHTNESS..1f,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            thumbColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                updateBrightness(stepReaderBrightness(brightness, CUSTOM_BRIGHTNESS_STEP_PERCENT))
            },
            enabled = canIncrease,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.content_desc_increase),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun Window.setReaderBrightness(brightness: Float) {
    attributes = attributes.apply {
        screenBrightness = brightness
    }
}
