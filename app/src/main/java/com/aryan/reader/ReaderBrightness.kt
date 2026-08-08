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
import com.aryan.reader.shared.ui.SharedReaderBrightnessLabels
import com.aryan.reader.shared.ui.SharedReaderBrightnessSheet
import kotlin.math.roundToInt

private const val READER_PREFS_NAME = "reader_prefs"
private const val PREF_READER_BRIGHTNESS_USE_SYSTEM = "reader_brightness_use_system"
private const val PREF_READER_BRIGHTNESS_VALUE = "reader_brightness_value"
typealias ReaderBrightnessSettings = com.aryan.reader.shared.ReaderBrightnessSettings

internal fun normalizeReaderBrightness(brightness: Float): Float {
    return com.aryan.reader.shared.normalizeReaderBrightness(brightness)
}

internal fun stepReaderBrightness(brightness: Float, percentDelta: Int): Float {
    return com.aryan.reader.shared.stepReaderBrightness(brightness, percentDelta)
}

fun loadReaderBrightnessSettings(context: Context): ReaderBrightnessSettings {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return ReaderBrightnessSettings(
        useSystemBrightness = prefs.getBoolean(PREF_READER_BRIGHTNESS_USE_SYSTEM, true),
        customBrightness = prefs.getFloat(PREF_READER_BRIGHTNESS_VALUE, com.aryan.reader.shared.DefaultReaderCustomBrightness)
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
    SharedReaderBrightnessSheet(
        settings = settings,
        onSettingsChange = onSettingsChange,
        labels = SharedReaderBrightnessLabels(
            title = stringResource(R.string.reader_brightness_title),
            done = stringResource(R.string.action_done),
            system = stringResource(R.string.reader_brightness_system),
            systemDescription = stringResource(R.string.reader_brightness_system_desc),
            custom = stringResource(R.string.reader_brightness_custom),
            customDescription = stringResource(R.string.reader_brightness_custom_desc),
            percent = stringResource(R.string.reader_brightness_percent, (settings.safeCustomBrightness * 100f).roundToInt()),
            decrease = stringResource(R.string.content_desc_decrease),
            increase = stringResource(R.string.content_desc_increase)
        ),
        onDismiss = onDismiss
    )
}

private fun Window.setReaderBrightness(brightness: Float) {
    attributes = attributes.apply {
        screenBrightness = brightness
    }
}
