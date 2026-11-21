package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.aryan.reader.shared.reader.MaxPerceptionLinesInsetFraction
import com.aryan.reader.shared.reader.MaxPerceptionLinesLineWidthDp
import com.aryan.reader.shared.reader.MaxPerceptionLinesOpacity
import com.aryan.reader.shared.reader.MinPerceptionLinesInsetFraction
import com.aryan.reader.shared.reader.MinPerceptionLinesLineWidthDp
import com.aryan.reader.shared.reader.MinPerceptionLinesOpacity
import com.aryan.reader.shared.reader.ReaderPerceptionLinesSettings
import com.aryan.reader.shared.reader.hasCustomSliderValues
import com.aryan.reader.shared.reader.hasPerceptionLineBand
import com.aryan.reader.shared.reader.perceptionLineCenterXs
import com.aryan.reader.shared.reader.withDefaultSliderValues
import kotlin.math.roundToInt

object SharedPerceptionLinesAxTags {
    const val OVERLAY = "PerceptionLinesOverlay"
    const val SWITCH = "PerceptionLinesSwitch"
    const val OPACITY_SLIDER = "PerceptionLinesOpacity"
    const val WIDTH_SLIDER = "PerceptionLinesWidth"
    const val POSITION_SLIDER = "PerceptionLinesPosition"
    const val CUSTOM_COLOR = "PerceptionLinesCustomColor"
    const val PREVIEW = "PerceptionLinesPreview"
    const val RESET = "PerceptionLinesReset"
}

/** Quick-pick rail colours: neutrals that read on both themes first, then three accents. */
private val SharedPerceptionLinesPalette: List<Int> = listOf(
    0xFF7A7A7A.toInt(),
    0xFF000000.toInt(),
    0xFF9E9E9E.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFD32F2F.toInt(),
    0xFFF9A825.toInt(),
    0xFF1976D2.toInt(),
)

/**
 * The two rules themselves: a decorative layer drawn above the rendered page.
 *
 * It declares no pointer input on purpose — Compose only hit-tests nodes that do, so taps, scrolls,
 * selection handles and long-press lookups pass straight through to the reader underneath. Nothing
 * here feeds measurement or pagination, so toggling it costs a redraw and never a reflow.
 */
@Composable
fun SharedPerceptionLinesOverlay(
    settings: ReaderPerceptionLinesSettings,
    modifier: Modifier = Modifier,
) {
    if (!settings.enabled) return
    val color = Color(settings.safeColorArgb).copy(alpha = settings.safeOpacity)
    val strokeWidthPx = with(LocalDensity.current) { settings.safeLineWidthDp.dp.toPx() }
    val insetFraction = settings.safeInsetFraction
    Canvas(modifier.fillMaxSize().testTag(SharedPerceptionLinesAxTags.OVERLAY)) {
        if (!hasPerceptionLineBand(size.width, insetFraction)) return@Canvas
        val (left, right) = perceptionLineCenterXs(size.width, insetFraction)
        val half = strokeWidthPx / 2f
        drawRect(
            color = color,
            topLeft = Offset(left - half, 0f),
            size = Size(strokeWidthPx, size.height)
        )
        drawRect(
            color = color,
            topLeft = Offset(right - half, 0f),
            size = Size(strokeWidthPx, size.height)
        )
    }
}

private data class SharedPerceptionLinesLabels(
    val title: String,
    val done: String,
    val showLines: String,
    val showLinesDescription: String,
    val color: String,
    val customColor: String,
    val opacity: String,
    val width: String,
    val position: String,
    val preview: String,
    val reset: String,
)

@Composable
private fun sharedPerceptionLinesLabels(): SharedPerceptionLinesLabels = SharedPerceptionLinesLabels(
    title = readerString("menu_perception_lines", "Perception Lines"),
    done = readerString("action_done", "Done"),
    showLines = readerString("perception_lines_show", "Show lines"),
    showLinesDescription = readerString(
        "perception_lines_show_desc",
        "Draw a guide rail inset from each edge of the text."
    ),
    color = readerString("perception_lines_color", "Color"),
    customColor = readerString("perception_lines_custom_color", "Custom color"),
    opacity = readerString("perception_lines_opacity", "Opacity"),
    width = readerString("perception_lines_width", "Line width"),
    position = readerString("perception_lines_position", "Line position"),
    preview = readerString("perception_lines_preview", "Preview"),
    reset = readerString("action_reset", "Reset"),
)

/**
 * Perception-lines settings, reachable from either bar (the tool ships hidden, see
 * [com.aryan.reader.shared.ReaderTool.PERCEPTION_LINES], so a reader adds it first).
 *
 * The switch comes first because the common case is "show it for this hard paragraph and move on";
 * colour, opacity, width and position stay in the same sheet rather than a settings screen, since
 * the whole point is trying the rail position against the current book and theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPerceptionLinesSheet(
    settings: ReaderPerceptionLinesSettings,
    onSettingsChange: (ReaderPerceptionLinesSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = sharedPerceptionLinesLabels()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showColorPicker by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Android benchmark (`SharedMobileEpubFormat`): the header's Reset is a plain text
                    // button. It restores the three slider defaults only and leaves the switch and the
                    // rail colour alone, so it goes dead once they already read the defaults.
                    TextButton(
                        onClick = { onSettingsChange(settings.withDefaultSliderValues()) },
                        enabled = settings.hasCustomSliderValues,
                        modifier = Modifier.testTag(SharedPerceptionLinesAxTags.RESET)
                    ) {
                        Text(labels.reset)
                    }
                    TextButton(onClick = onDismiss) { Text(labels.done) }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(labels.showLines, style = MaterialTheme.typography.titleMedium)
                    Text(
                        labels.showLinesDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onSettingsChange(settings.copy(enabled = it).sanitized()) },
                    modifier = Modifier.testTag(SharedPerceptionLinesAxTags.SWITCH)
                )
            }
            HorizontalDivider()
            SharedPerceptionLinesPreview(settings = settings, label = labels.preview)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(labels.color, style = MaterialTheme.typography.titleMedium)
                SharedTextColorSwatches(
                    palette = SharedPerceptionLinesPalette,
                    selectedArgb = settings.safeColorArgb.toInt(),
                    allowTransparent = false,
                    dark = dark,
                    onColorSelected = { argb ->
                        onSettingsChange(settings.copy(colorArgb = argb.toLong() and 0xFFFFFFFFL).sanitized())
                    }
                )
                SharedPerceptionLinesCustomColorButton(
                    color = Color(settings.safeColorArgb),
                    label = labels.customColor,
                    onClick = { showColorPicker = true }
                )
            }
            HorizontalDivider()
            SharedPerceptionLinesSliderRow(
                label = labels.opacity,
                valueLabel = "${(settings.safeOpacity * 100f).roundToInt()}%",
                value = settings.safeOpacity,
                valueRange = MinPerceptionLinesOpacity..MaxPerceptionLinesOpacity,
                testTag = SharedPerceptionLinesAxTags.OPACITY_SLIDER,
                onValueChange = { onSettingsChange(settings.copy(opacity = it).sanitized()) }
            )
            SharedPerceptionLinesSliderRow(
                label = labels.width,
                valueLabel = "${settings.safeLineWidthDp.roundToInt()} dp",
                value = settings.safeLineWidthDp,
                valueRange = MinPerceptionLinesLineWidthDp..MaxPerceptionLinesLineWidthDp,
                testTag = SharedPerceptionLinesAxTags.WIDTH_SLIDER,
                onValueChange = { onSettingsChange(settings.copy(lineWidthDp = it).sanitized()) }
            )
            SharedPerceptionLinesSliderRow(
                label = labels.position,
                valueLabel = "${(settings.safeInsetFraction * 100f).roundToInt()}%",
                value = settings.safeInsetFraction,
                valueRange = MinPerceptionLinesInsetFraction..MaxPerceptionLinesInsetFraction,
                testTag = SharedPerceptionLinesAxTags.POSITION_SLIDER,
                onValueChange = { onSettingsChange(settings.copy(insetFraction = it).sanitized()) }
            )
        }
    }

    if (showColorPicker) {
        SharedHsvColorPickerDialog(
            initialColor = Color(settings.safeColorArgb),
            title = labels.color,
            onDismiss = { showColorPicker = false },
            onSave = { color ->
                onSettingsChange(
                    settings.copy(colorArgb = color.toArgb().toLong() and 0xFFFFFFFFL).sanitized()
                )
                showColorPicker = false
            }
        )
    }
}

/**
 * Live preview of the rails against sample text.
 *
 * It reuses [SharedPerceptionLinesOverlay] rather than drawing its own geometry, so colour, opacity,
 * width and position cannot drift from what the reader renders: every drag recomposes this box with
 * the new settings and the rails move with it. The rails are forced on here on purpose — the
 * preview answers "what would the lines look like", so it has to show them while the switch is
 * still off. The bars stand in for text so the preview needs no localisable sample copy.
 */
@Composable
private fun SharedPerceptionLinesPreview(
    settings: ReaderPerceptionLinesSettings,
    label: String,
) {
    val previewSettings = remember(settings) { settings.copy(enabled = true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag(SharedPerceptionLinesAxTags.PREVIEW)
        ) {
            // Four ragged "lines of text": enough to judge the band against words, and they stay
            // visible under a wide or a narrow inset.
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                listOf(1f, 0.97f, 0.92f, 0.6f).forEach { fill ->
                    Box(
                        Modifier
                            .fillMaxWidth(fill)
                            .height(6.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
            SharedPerceptionLinesOverlay(settings = previewSettings)
        }
    }
}

@Composable
private fun SharedPerceptionLinesSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    testTag: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ReaderMinimalSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            thumbColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        )
    }
}

/**
 * Opens the shared HSV picker, previewing the current rail colour as a dot so the row matches the
 * fixed swatches above it instead of introducing a second colour widget.
 */
@Composable
private fun SharedPerceptionLinesCustomColorButton(
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(SharedPerceptionLinesAxTags.CUSTOM_COLOR)
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(color, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
