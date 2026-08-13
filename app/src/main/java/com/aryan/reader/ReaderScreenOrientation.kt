package com.aryan.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.aryan.reader.shared.ui.SharedReaderOrientationLabels
import com.aryan.reader.shared.ui.SharedReaderScreenOrientationSheet

private const val READER_SCREEN_ORIENTATION_PREFS_NAME = "epub_reader_settings"
private const val READER_SCREEN_ORIENTATION_KEY = "reader_screen_orientation_mode"

typealias ReaderScreenOrientationMode = com.aryan.reader.shared.reader.ReaderScreenOrientationMode

private val ReaderScreenOrientationMode.persistedId: Int
    get() = when (this) {
        ReaderScreenOrientationMode.FOLLOW_SYSTEM -> 0
        ReaderScreenOrientationMode.PORTRAIT -> 1
        ReaderScreenOrientationMode.LANDSCAPE -> 2
    }

fun saveReaderScreenOrientationMode(context: Context, mode: ReaderScreenOrientationMode) {
    val prefs = context.getSharedPreferences(READER_SCREEN_ORIENTATION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putInt(READER_SCREEN_ORIENTATION_KEY, mode.persistedId) }
}

fun loadReaderScreenOrientationMode(context: Context): ReaderScreenOrientationMode {
    val prefs = context.getSharedPreferences(READER_SCREEN_ORIENTATION_PREFS_NAME, Context.MODE_PRIVATE)
    return when (prefs.getInt(READER_SCREEN_ORIENTATION_KEY, 0)) {
        1 -> ReaderScreenOrientationMode.PORTRAIT
        2 -> ReaderScreenOrientationMode.LANDSCAPE
        else -> ReaderScreenOrientationMode.FOLLOW_SYSTEM
    }
}

fun ReaderScreenOrientationMode.toRequestedOrientation(): Int {
    return when (this) {
        ReaderScreenOrientationMode.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ReaderScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        ReaderScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }
}

@Composable
fun ReaderScreenOrientationEffect(mode: ReaderScreenOrientationMode) {
    val context = LocalContext.current
    val activity: Activity? = remember(context) { context.findReaderOrientationActivity() }

    DisposableEffect(activity, mode) {
        if (activity != null) {
            val originalOrientation = activity.requestedOrientation
            activity.requestedOrientation = mode.toRequestedOrientation()

            onDispose {
                activity.requestedOrientation = originalOrientation
            }
        } else {
            onDispose {}
        }
    }
}

@Composable
fun ReaderScreenOrientationPicker(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    com.aryan.reader.shared.ui.SharedReaderScreenOrientationPicker(
        selectedMode = selectedMode,
        onModeSelected = onModeSelected,
        labels = ReaderScreenOrientationMode.entries.associateWith { it.title },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreenOrientationSheet(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    onDismiss: () -> Unit
) {
    SharedReaderScreenOrientationSheet(
        selectedMode = selectedMode,
        onModeSelected = onModeSelected,
        labels = SharedReaderOrientationLabels(
            title = stringResource(R.string.visual_options_screen_orientation),
            close = stringResource(R.string.action_close),
            description = stringResource(R.string.visual_options_screen_orientation_desc),
            options = ReaderScreenOrientationMode.entries.associateWith { it.title }
        ),
        onDismiss = onDismiss
    )
}

private tailrec fun Context.findReaderOrientationActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findReaderOrientationActivity()
        else -> null
    }
}
