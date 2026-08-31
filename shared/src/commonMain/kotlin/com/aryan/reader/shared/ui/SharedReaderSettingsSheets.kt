package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderBrightnessSettings
import com.aryan.reader.shared.MinimumReaderCustomBrightness
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.normalizeReaderBrightness
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.stepReaderBrightness
import kotlin.math.roundToInt

data class SharedReaderVisualOptionsLabels(
    val title: String,
    val close: String,
    val systemUi: String,
    val systemUiDescription: String,
    val systemUiOptions: Map<SystemUiMode, String>,
    val progressBar: String,
    val progressBarDescription: String,
    val pageInfoOptions: Map<PageInfoMode, String>,
    val progressBarPosition: String,
    val pageInfoPositionOptions: Map<PageInfoPosition, String>,
    val seamlessChapter: String,
    val seamlessChapterDescription: String,
    val pullDistance: String,
    val shortDistance: String,
    val longDistance: String,
    val hideImages: String,
    val hideImagesDescription: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderVisualOptionsSheet(
    systemUiMode: SystemUiMode,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    pageInfoMode: PageInfoMode,
    onPageInfoModeChange: (PageInfoMode) -> Unit,
    pageInfoPosition: PageInfoPosition,
    onPageInfoPositionChange: (PageInfoPosition) -> Unit,
    pullToTurnEnabled: Boolean,
    onPullToTurnChange: (Boolean) -> Unit,
    pullToTurnMultiplier: Float,
    onPullToTurnMultiplierChange: (Float) -> Unit,
    hideImages: Boolean,
    onHideImagesChange: (Boolean) -> Unit,
    maxSheetHeight: Dp,
    labels: SharedReaderVisualOptionsLabels,
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
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = labels.close)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(labels.systemUi, style = MaterialTheme.typography.titleMedium)
            Text(labels.systemUiDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SharedReaderOptionSegmentedControl(SystemUiMode.entries, systemUiMode, onSystemUiModeChange) {
                labels.systemUiOptions.getValue(it)
            }
            Spacer(Modifier.height(24.dp))
            Text(labels.progressBar, style = MaterialTheme.typography.titleMedium)
            Text(labels.progressBarDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SharedReaderOptionSegmentedControl(PageInfoMode.entries, pageInfoMode, onPageInfoModeChange) {
                labels.pageInfoOptions.getValue(it)
            }
            Spacer(Modifier.height(16.dp))
            Text(labels.progressBarPosition, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SharedReaderOptionSegmentedControl(PageInfoPosition.entries, pageInfoPosition, onPageInfoPositionChange) {
                labels.pageInfoPositionOptions.getValue(it)
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPullToTurnChange(!pullToTurnEnabled) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(labels.seamlessChapter, style = MaterialTheme.typography.titleMedium)
                            Text(labels.seamlessChapterDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(checked = !pullToTurnEnabled, onCheckedChange = { onPullToTurnChange(!it) })
                    }
                    AnimatedVisibility(visible = pullToTurnEnabled) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Text(labels.pullDistance, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(labels.shortDistance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = pullToTurnMultiplier,
                                    onValueChange = onPullToTurnMultiplierChange,
                                    valueRange = 0.5f..2.0f,
                                    steps = 14,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                )
                                Text(labels.longDistance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHideImagesChange(!hideImages) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(labels.hideImages, style = MaterialTheme.typography.titleMedium)
                        Text(labels.hideImagesDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = hideImages, onCheckedChange = onHideImagesChange)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun <T> SharedReaderOptionSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    getLabel: (T) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onOptionSelected(option) }.padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getLabel(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class SharedReaderOrientationLabels(
    val title: String,
    val close: String,
    val description: String,
    val options: Map<ReaderScreenOrientationMode, String>
)

@Composable
fun SharedReaderScreenOrientationPicker(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    labels: Map<ReaderScreenOrientationMode, String>,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        SharedReaderOptionSegmentedControl(ReaderScreenOrientationMode.entries, selectedMode, onModeSelected) {
            labels.getValue(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderScreenOrientationSheet(
    selectedMode: ReaderScreenOrientationMode,
    onModeSelected: (ReaderScreenOrientationMode) -> Unit,
    labels: SharedReaderOrientationLabels,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = labels.close) }
            }
            Spacer(Modifier.height(16.dp))
            Text(labels.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SharedReaderScreenOrientationPicker(selectedMode, onModeSelected, labels.options)
        }
    }
}

data class SharedReaderBrightnessLabels(
    val title: String,
    val done: String,
    val system: String,
    val systemDescription: String,
    val custom: String,
    val customDescription: String,
    val percent: String,
    val decrease: String,
    val increase: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderBrightnessSheet(
    settings: ReaderBrightnessSettings,
    onSettingsChange: (ReaderBrightnessSettings) -> Unit,
    labels: SharedReaderBrightnessLabels,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text(labels.done) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(labels.system, style = MaterialTheme.typography.titleMedium)
                    Text(labels.systemDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.useSystemBrightness,
                    onCheckedChange = { onSettingsChange(settings.copy(useSystemBrightness = it)) }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(labels.custom, style = MaterialTheme.typography.titleMedium)
                    Text(
                        labels.percent,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SharedReaderBrightnessControl(settings, onSettingsChange, labels)
                Text(labels.customDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SharedReaderBrightnessControl(
    settings: ReaderBrightnessSettings,
    onSettingsChange: (ReaderBrightnessSettings) -> Unit,
    labels: SharedReaderBrightnessLabels
) {
    val brightness = settings.safeCustomBrightness
    fun update(value: Float) = onSettingsChange(
        settings.copy(useSystemBrightness = false, customBrightness = normalizeReaderBrightness(value))
    )
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        IconButton(
            onClick = { update(stepReaderBrightness(brightness, -1)) },
            enabled = brightness > MinimumReaderCustomBrightness,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = labels.decrease, modifier = Modifier.size(18.dp))
        }
        ReaderMinimalSlider(
            value = brightness,
            onValueChange = ::update,
            valueRange = MinimumReaderCustomBrightness..1f,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            thumbColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { update(stepReaderBrightness(brightness, 1)) },
            enabled = brightness < 1f,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = labels.increase, modifier = Modifier.size(18.dp))
        }
    }
}

data class SharedPdfVisualOptionsLabels(
    val title: String,
    val close: String,
    val systemUi: String,
    val systemUiDescription: String,
    val systemUiOptions: Map<SystemUiMode, String>,
    val toolbars: String,
    val topToolbar: String,
    val topToolbarDescription: String,
    val bottomToolbar: String,
    val bottomToolbarDescription: String,
    val pageLayout: String,
    val pageSpread: String,
    val spreadOptions: Map<ReaderPageSpreadMode, String>,
    val firstPageAlone: String,
    val firstPageAloneDescription: String,
    val removePageGap: String,
    val removePageGapDescription: String,
    val hidePageNumberOverlay: String,
    val hidePageNumberOverlayDescription: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPdfVisualOptionsSheet(
    displayMode: PdfDisplayMode,
    systemUiMode: SystemUiMode,
    pageSpreadMode: ReaderPageSpreadMode,
    firstPageStandaloneInSpread: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    showTopToolbar: Boolean,
    showBottomToolbar: Boolean,
    onPageSpreadModeChange: (ReaderPageSpreadMode) -> Unit,
    onFirstPageStandaloneInSpreadChange: (Boolean) -> Unit,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
    onShowTopToolbarChange: (Boolean) -> Unit,
    onShowBottomToolbarChange: (Boolean) -> Unit,
    maxSheetHeight: Dp,
    labels: SharedPdfVisualOptionsLabels,
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
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = labels.close) }
            }
            Spacer(Modifier.height(16.dp))
            Text(labels.systemUi, style = MaterialTheme.typography.titleMedium)
            Text(labels.systemUiDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SharedReaderOptionSegmentedControl(SystemUiMode.entries, systemUiMode, onSystemUiModeChange) {
                labels.systemUiOptions.getValue(it)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(labels.toolbars, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            // At least one bar must remain: the reader options are only
            // reachable through the bars themselves.
            SharedPdfVisualOptionSwitchRow(labels.topToolbar, labels.topToolbarDescription, showTopToolbar) { visible ->
                if (visible || showBottomToolbar) onShowTopToolbarChange(visible)
            }
            SharedPdfVisualOptionSwitchRow(labels.bottomToolbar, labels.bottomToolbarDescription, showBottomToolbar) { visible ->
                if (visible || showTopToolbar) onShowBottomToolbarChange(visible)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(labels.pageLayout, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (displayMode == PdfDisplayMode.PAGINATION) {
                Text(labels.pageSpread, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SharedReaderOptionSegmentedControl(ReaderPageSpreadMode.entries, pageSpreadMode, onPageSpreadModeChange) {
                    labels.spreadOptions.getValue(it)
                }
                if (pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE) {
                    Spacer(Modifier.height(8.dp))
                    SharedPdfVisualOptionSwitchRow(
                        labels.firstPageAlone,
                        labels.firstPageAloneDescription,
                        firstPageStandaloneInSpread,
                        onFirstPageStandaloneInSpreadChange
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            SharedPdfVisualOptionSwitchRow(labels.removePageGap, labels.removePageGapDescription, !showVerticalPageGap) {
                onShowVerticalPageGapChange(!it)
            }
            SharedPdfVisualOptionSwitchRow(labels.hidePageNumberOverlay, labels.hidePageNumberOverlayDescription, !showPageNumberOverlay) {
                onShowPageNumberOverlayChange(!it)
            }
        }
    }
}

@Composable
private fun SharedPdfVisualOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SharedReaderFormatPreview(
    fontFamily: FontFamily,
    fontSizeMultiplier: Float,
    fontWeight: Int,
    letterSpacing: Float,
    lineHeightMultiplier: Float,
    previewText: String = "The art of reading, perfected",
    sampleText: String = "0123456789  ·  Aa Bb Cc"
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = previewText,
                fontFamily = fontFamily,
                fontSize = (18f * fontSizeMultiplier).sp,
                fontWeight = fontWeight.takeIf { it > 0 }?.let(::FontWeight),
                letterSpacing = letterSpacing.em,
                lineHeight = (22f * fontSizeMultiplier * lineHeightMultiplier).sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sampleText,
                fontFamily = fontFamily,
                fontSize = (13f * fontSizeMultiplier).sp,
                fontWeight = fontWeight.takeIf { it > 0 }?.let(::FontWeight),
                letterSpacing = letterSpacing.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SharedReaderFormatStepperRow(
    label: String,
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onClick: () -> Unit,
    decreaseContentDescription: String = "Decrease $label",
    increaseContentDescription: String = "Increase $label"
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDecrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = decreaseContentDescription)
            }
            IconButton(onClick = onIncrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = increaseContentDescription)
            }
        }
    }
}

@Composable
fun SharedReaderFormatAdjustmentDialog(
    title: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    originalWeightDescription: String?,
    doneLabel: String,
    resetLabel: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(valueLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Slider(value = value.coerceIn(valueRange), onValueChange = onValueChange, valueRange = valueRange)
                originalWeightDescription?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(doneLabel) } },
        dismissButton = { TextButton(onClick = onReset) { Text(resetLabel) } }
    )
}

data class SharedReaderTextFormatSheetLabels(
    val local: String,
    val global: String,
    val localDescription: String,
    val globalDescription: String,
    val selectMode: String,
    val reset: String,
    val close: String,
    val fontAlignmentSection: String,
    val typographySection: String,
    val layoutSpacingSection: String,
    val selectFontFamily: String,
    val fontPreview: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderTextFormatSheet(
    isVisible: Boolean,
    isLocalMode: Boolean,
    onLocalModeToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    maxSheetHeight: Dp,
    previewFontFamily: FontFamily,
    currentFontSize: Float,
    currentFontWeight: Int,
    currentLetterSpacing: Float,
    currentLineHeight: Float,
    currentFontName: String,
    onFontOptionClick: () -> Unit,
    labels: SharedReaderTextFormatSheetLabels,
    alignmentControl: @Composable () -> Unit,
    typographyControls: @Composable () -> Unit,
    layoutControls: @Composable () -> Unit
) {
    if (!isVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Box {
                    var showModeMenu by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showModeMenu = true }.padding(4.dp)
                    ) {
                        Text(
                            if (isLocalMode) labels.local else labels.global,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = labels.selectMode, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(labels.global, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(labels.globalDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { onLocalModeToggle(false); showModeMenu = false },
                            trailingIcon = { if (!isLocalMode) Icon(Icons.Default.Check, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(labels.local, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(labels.localDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { onLocalModeToggle(true); showModeMenu = false },
                            trailingIcon = { if (isLocalMode) Icon(Icons.Default.Check, contentDescription = null) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text(labels.reset) }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, labels.close, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SharedReaderFormatPreview(previewFontFamily, currentFontSize, currentFontWeight, currentLetterSpacing, currentLineHeight)
            Spacer(Modifier.height(20.dp))
            SharedReaderFormatSectionLabel(labels.fontAlignmentSection, bottomPadding = 8.dp)
            Surface(
                onClick = onFontOptionClick,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = labels.selectFontFamily }
            ) {
                Row(Modifier.padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            labels.fontPreview,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            currentFontName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            alignmentControl()
            Spacer(Modifier.height(24.dp))
            SharedReaderFormatSectionLabel(labels.typographySection, bottomPadding = 8.dp)
            typographyControls()
            Spacer(Modifier.height(20.dp))
            SharedReaderFormatSectionLabel(labels.layoutSpacingSection, bottomPadding = 12.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { layoutControls() }
        }
    }
}

@Composable
private fun SharedReaderFormatSectionLabel(text: String, bottomPadding: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = bottomPadding)
    )
}
