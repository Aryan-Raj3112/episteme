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

private const val READER_SCREEN_ORIENTATION_PREFS_NAME = "epub_reader_settings"
private const val READER_SCREEN_ORIENTATION_KEY = "reader_screen_orientation_mode"

enum class ReaderScreenOrientationMode(
    val id: Int,
    val title: String
) {
    FOLLOW_SYSTEM(0, "Follow system"),
    PORTRAIT(1, "Portrait"),
    LANDSCAPE(2, "Landscape")
}

fun saveReaderScreenOrientationMode(context: Context, mode: ReaderScreenOrientationMode) {
    val prefs = context.getSharedPreferences(READER_SCREEN_ORIENTATION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putInt(READER_SCREEN_ORIENTATION_KEY, mode.id) }
}

fun loadReaderScreenOrientationMode(context: Context): ReaderScreenOrientationMode {
    val prefs = context.getSharedPreferences(READER_SCREEN_ORIENTATION_PREFS_NAME, Context.MODE_PRIVATE)
    val id = prefs.getInt(READER_SCREEN_ORIENTATION_KEY, ReaderScreenOrientationMode.FOLLOW_SYSTEM.id)
    return ReaderScreenOrientationMode.entries.find { it.id == id } ?: ReaderScreenOrientationMode.FOLLOW_SYSTEM
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReaderScreenOrientationMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreenOrientationSheet(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.visual_options_screen_orientation),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.visual_options_screen_orientation_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReaderScreenOrientationPicker(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected
            )
        }
    }
}

private tailrec fun Context.findReaderOrientationActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findReaderOrientationActivity()
        else -> null
    }
}
