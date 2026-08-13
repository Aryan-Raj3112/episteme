package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.DefaultReaderCustomBrightness
import com.aryan.reader.shared.MinimumReaderCustomBrightness
import com.aryan.reader.shared.normalizeReaderBrightness
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.stepReaderBrightness
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileReaderBrightnessSheet(
    brightness: Float?,
    rememberedCustomBrightness: Float = DefaultReaderCustomBrightness,
    onBrightnessChange: (Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    val customBrightness = normalizeReaderBrightness(brightness ?: rememberedCustomBrightness)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Brightness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Use system brightness", style = MaterialTheme.typography.titleMedium)
                    Text("Follow your device brightness setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = brightness == null, onCheckedChange = { useSystem ->
                    onBrightnessChange(if (useSystem) null else customBrightness)
                })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Custom brightness", style = MaterialTheme.typography.titleMedium)
                Text("${(customBrightness * 100f).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = { onBrightnessChange(stepReaderBrightness(customBrightness, -1)) },
                    enabled = customBrightness > MinimumReaderCustomBrightness,
                ) { Icon(Icons.Default.Remove, contentDescription = "Decrease brightness") }
                ReaderMinimalSlider(
                    value = customBrightness,
                    onValueChange = { onBrightnessChange(normalizeReaderBrightness(it)) },
                    valueRange = MinimumReaderCustomBrightness..1f,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onBrightnessChange(stepReaderBrightness(customBrightness, 1)) },
                    enabled = customBrightness < 1f,
                ) { Icon(Icons.Default.Add, contentDescription = "Increase brightness") }
            }
            Text("Changing this value switches from system brightness to a reader-only override.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileReaderScreenOrientationSheet(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Screen Orientation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Choose how the reader responds when you rotate your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderScreenOrientationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.title, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
