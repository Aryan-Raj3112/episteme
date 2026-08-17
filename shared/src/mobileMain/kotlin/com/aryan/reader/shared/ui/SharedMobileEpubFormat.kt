package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.TabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderFont
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.toSharedReaderFontFamily
import com.aryan.reader.shared.reader.ReaderPageInfo
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.toReaderSettings
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.format_align_justify
import com.aryan.reader.shared.generated.resources.format_align_left
import com.aryan.reader.shared.generated.resources.format_align_right
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.min
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileEpubFormatSheet(
    settings: ReaderSettings,
    isLocalMode: Boolean,
    customFonts: List<CustomFontItem>,
    onImportFont: () -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showModeMenu by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var activeAdjustment by remember { mutableStateOf<SharedMobileReaderFormatAdjustment?>(null) }
    var alignmentChoice by remember(settings.textAlign) {
        mutableStateOf(
            when (settings.textAlign) {
                SharedReaderTextAlign.LEFT -> "Left"
                SharedReaderTextAlign.RIGHT -> "Right"
                SharedReaderTextAlign.JUSTIFY -> "Justify"
                else -> "Default"
            }
        )
    }
    val defaults = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.70f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(Modifier.clickable { showModeMenu = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isLocalMode) "Local Format" else "Global Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select format mode", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        DropdownMenuItem(text = { Column { Text("Global Format", fontWeight = FontWeight.Bold); Text("Applies to all files", style = MaterialTheme.typography.bodySmall) } }, onClick = { onLocalModeChange(false); showModeMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Column { Text("Local Format", fontWeight = FontWeight.Bold); Text("Saved for this file", style = MaterialTheme.typography.bodySmall) } }, onClick = { onLocalModeChange(true); showModeMenu = false })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onSettingsChange(settings.copy(fontSize = defaults.fontSize, fontWeight = defaults.fontWeight, letterSpacing = defaults.letterSpacing, lineSpacing = defaults.lineSpacing, paragraphSpacing = defaults.paragraphSpacing, imageScale = defaults.imageScale, horizontalMargin = defaults.resolvedHorizontalMargin, verticalMargin = defaults.resolvedVerticalMargin, fontFamily = defaults.fontFamily, customFontPath = null, textAlign = defaults.textAlign))
                        alignmentChoice = "Default"
                    }) { Text("Reset") }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("FONT & ALIGNMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Surface(onClick = { showFontSheet = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        Text(settings.fontFamily.takeUnless { it.isBlank() || it == "Default" } ?: "Original", style = MaterialTheme.typography.titleSmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Row {
                    listOf("Default", "Left", "Right", "Justify").forEach { label ->
                        val selected = alignmentChoice == label
                        val iconResource = when (label) {
                            "Right" -> Res.drawable.format_align_right
                            "Justify" -> Res.drawable.format_align_justify
                            else -> Res.drawable.format_align_left
                        }
                        Column(
                            Modifier.fillMaxHeight().weight(1f).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).clickable {
                                alignmentChoice = label
                                onSettingsChange(settings.copy(textAlign = when (label) {
                                    "Left" -> SharedReaderTextAlign.LEFT
                                    "Right" -> SharedReaderTextAlign.RIGHT
                                    "Justify" -> SharedReaderTextAlign.JUSTIFY
                                    else -> SharedReaderTextAlign.START
                                }))
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(iconResource),
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SharedMobileEpubFormatPreview(settings)
            Spacer(Modifier.height(24.dp))
            Text("TYPOGRAPHY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedMobileEpubFormatStepperRow("Font Size", sharedMobileFormatMultiplier(settings.fontSize / defaults.fontSize.toFloat()), { onSettingsChange(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast((defaults.fontSize * 0.5f).roundToInt()))) }, { onSettingsChange(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(defaults.fontSize * 3))) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.FONT_SIZE })
                SharedMobileEpubFormatStepperRow("Font Weight", sharedMobileFormatWeight(settings.fontWeight), { onSettingsChange(settings.copy(fontWeight = sharedMobilePreviousWeight(settings.fontWeight))) }, { onSettingsChange(settings.copy(fontWeight = sharedMobileNextWeight(settings.fontWeight))) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.FONT_WEIGHT })
                SharedMobileEpubFormatStepperRow("Letter Spacing", sharedMobileFormatLetterSpacing(settings.letterSpacing), { onSettingsChange(settings.copy(letterSpacing = sharedMobileStep(settings.letterSpacing, -0.01f, -0.10f, 0.50f, 100f))) }, { onSettingsChange(settings.copy(letterSpacing = sharedMobileStep(settings.letterSpacing, 0.01f, -0.10f, 0.50f, 100f))) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.LETTER_SPACING })
            }
            Spacer(Modifier.height(24.dp))
            Text("LAYOUT & SPACING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedMobileEpubFormatStepperRow("Line Height", sharedMobileFormatMultiplier(settings.lineSpacing / defaults.lineSpacing), { onSettingsChange(settings.copy(lineSpacing = sharedMobileStep(settings.lineSpacing / defaults.lineSpacing, -0.1f, 1f, 3f) * defaults.lineSpacing)) }, { onSettingsChange(settings.copy(lineSpacing = sharedMobileStep(settings.lineSpacing / defaults.lineSpacing, 0.1f, 1f, 3f) * defaults.lineSpacing)) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.LINE_HEIGHT })
                SharedMobileEpubFormatStepperRow("Paragraph Gap", sharedMobileFormatMultiplier(settings.paragraphSpacing / defaults.paragraphSpacing), { onSettingsChange(settings.copy(paragraphSpacing = sharedMobileStep(settings.paragraphSpacing / defaults.paragraphSpacing, -0.1f, 0f, 3f) * defaults.paragraphSpacing)) }, { onSettingsChange(settings.copy(paragraphSpacing = sharedMobileStep(settings.paragraphSpacing / defaults.paragraphSpacing, 0.1f, 0f, 3f) * defaults.paragraphSpacing)) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.PARAGRAPH_GAP })
                SharedMobileEpubFormatStepperRow("Image Size", sharedMobileFormatMultiplier(settings.imageScale / defaults.imageScale), { onSettingsChange(settings.copy(imageScale = sharedMobileStep(settings.imageScale / defaults.imageScale, -0.1f, 0.5f, 2f) * defaults.imageScale)) }, { onSettingsChange(settings.copy(imageScale = sharedMobileStep(settings.imageScale / defaults.imageScale, 0.1f, 0.5f, 2f) * defaults.imageScale)) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.IMAGE_SIZE })
                SharedMobileEpubFormatStepperRow("Horizontal Margin", sharedMobileFormatMargin(settings.resolvedHorizontalMargin / defaults.resolvedHorizontalMargin.toFloat()), { onSettingsChange(settings.copy(horizontalMargin = (sharedMobileStep(settings.resolvedHorizontalMargin / defaults.resolvedHorizontalMargin.toFloat(), -0.1f, 0f, 3f) * defaults.resolvedHorizontalMargin).roundToInt())) }, { onSettingsChange(settings.copy(horizontalMargin = (sharedMobileStep(settings.resolvedHorizontalMargin / defaults.resolvedHorizontalMargin.toFloat(), 0.1f, 0f, 3f) * defaults.resolvedHorizontalMargin).roundToInt())) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN })
                SharedMobileEpubFormatStepperRow("Vertical Margin", sharedMobileFormatMargin(settings.resolvedVerticalMargin / defaults.resolvedVerticalMargin.toFloat()), { onSettingsChange(settings.copy(verticalMargin = (sharedMobileStep(settings.resolvedVerticalMargin / defaults.resolvedVerticalMargin.toFloat(), -0.1f, 0f, 3f) * defaults.resolvedVerticalMargin).roundToInt())) }, { onSettingsChange(settings.copy(verticalMargin = (sharedMobileStep(settings.resolvedVerticalMargin / defaults.resolvedVerticalMargin.toFloat(), 0.1f, 0f, 3f) * defaults.resolvedVerticalMargin).roundToInt())) }, { activeAdjustment = SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN })
            }
        }
    }
    if (showFontSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSheet = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Select Font", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showFontSheet = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                LazyColumn {
                    items(ReaderFont.entries) { font ->
                        NavigationDrawerItem(label = { Text(font.displayName) }, selected = if (font == ReaderFont.ORIGINAL) settings.fontFamily == "Default" || settings.fontFamily == "Original" else settings.fontFamily == font.fontFamilyName, onClick = { onSettingsChange(settings.copy(fontFamily = if (font == ReaderFont.ORIGINAL) "Default" else font.fontFamilyName, customFontPath = null)); showFontSheet = false })
                    }
                    if (customFonts.any { !it.isDeleted }) {
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                        item {
                            Text(
                                "Imported Fonts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }, key = { it.id }) { font ->
                            NavigationDrawerItem(
                                label = { Text(font.displayName) },
                                selected = settings.customFontPath == font.path,
                                onClick = {
                                    onSettingsChange(settings.copy(fontFamily = font.displayName, customFontPath = font.path))
                                    showFontSheet = false
                                }
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = onImportFont,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import Font")
                        }
                    }
                }
            }
        }
    }
    activeAdjustment?.let {
        SharedMobileEpubFormatAdjustmentDialog(
            adjustment = it,
            settings = settings,
            defaults = defaults,
            onSettingsChange = onSettingsChange,
            onDismiss = { activeAdjustment = null }
        )
    }
}

internal enum class SharedMobileReaderFormatAdjustment(val title: String) {
    FONT_SIZE("Font size"),
    FONT_WEIGHT("Font weight"),
    LETTER_SPACING("Letter spacing"),
    LINE_HEIGHT("Line height"),
    PARAGRAPH_GAP("Paragraph gap"),
    IMAGE_SIZE("Image size"),
    HORIZONTAL_MARGIN("Horizontal margin"),
    VERTICAL_MARGIN("Vertical margin")
}

@Composable
internal fun SharedMobileEpubFormatPreview(settings: ReaderSettings) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            val previewWeight = settings.fontWeight.takeIf { it > 0 }?.let(::FontWeight)
            Text(
                "The art of reading, perfected",
                fontFamily = settings.toSharedReaderFontFamily(),
                fontSize = settings.fontSize.sp,
                fontWeight = previewWeight,
                letterSpacing = settings.letterSpacing.em,
                lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "0123456789  ·  Aa Bb Cc",
                fontFamily = settings.toSharedReaderFontFamily(),
                fontSize = (settings.fontSize * 0.72f).sp,
                fontWeight = previewWeight,
                letterSpacing = settings.letterSpacing.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SharedMobileEpubFormatStepperRow(
    label: String,
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDecrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            IconButton(onClick = onIncrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubFormatAdjustmentDialog(
    adjustment: SharedMobileReaderFormatAdjustment,
    settings: ReaderSettings,
    defaults: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val value = when (adjustment) {
        SharedMobileReaderFormatAdjustment.FONT_SIZE -> settings.fontSize / defaults.fontSize.toFloat()
        SharedMobileReaderFormatAdjustment.FONT_WEIGHT -> settings.fontWeight.takeIf { it > 0 }?.toFloat() ?: 400f
        SharedMobileReaderFormatAdjustment.LETTER_SPACING -> settings.letterSpacing
        SharedMobileReaderFormatAdjustment.LINE_HEIGHT -> settings.lineSpacing / defaults.lineSpacing
        SharedMobileReaderFormatAdjustment.PARAGRAPH_GAP -> settings.paragraphSpacing / defaults.paragraphSpacing
        SharedMobileReaderFormatAdjustment.IMAGE_SIZE -> settings.imageScale / defaults.imageScale
        SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN -> settings.resolvedHorizontalMargin / defaults.resolvedHorizontalMargin.toFloat()
        SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN -> settings.resolvedVerticalMargin / defaults.resolvedVerticalMargin.toFloat()
    }
    val range = when (adjustment) {
        SharedMobileReaderFormatAdjustment.FONT_SIZE -> 0.5f..3f
        SharedMobileReaderFormatAdjustment.FONT_WEIGHT -> 100f..1000f
        SharedMobileReaderFormatAdjustment.LETTER_SPACING -> -0.10f..0.50f
        SharedMobileReaderFormatAdjustment.LINE_HEIGHT,
        SharedMobileReaderFormatAdjustment.PARAGRAPH_GAP,
        SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN,
        SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN -> 0f..3f
        SharedMobileReaderFormatAdjustment.IMAGE_SIZE -> 0.5f..2f
    }
    fun update(raw: Float) {
        onSettingsChange(
            when (adjustment) {
                SharedMobileReaderFormatAdjustment.FONT_SIZE -> settings.copy(fontSize = (defaults.fontSize * sharedMobileStep(raw, 0f, 0.5f, 3f)).roundToInt())
                SharedMobileReaderFormatAdjustment.FONT_WEIGHT -> settings.copy(fontWeight = ((raw / 100f).roundToInt() * 100).coerceIn(100, 1000))
                SharedMobileReaderFormatAdjustment.LETTER_SPACING -> settings.copy(letterSpacing = sharedMobileStep(raw, 0f, -0.10f, 0.50f, 100f))
                SharedMobileReaderFormatAdjustment.LINE_HEIGHT -> settings.copy(lineSpacing = defaults.lineSpacing * sharedMobileStep(raw, 0f, 1f, 3f))
                SharedMobileReaderFormatAdjustment.PARAGRAPH_GAP -> settings.copy(paragraphSpacing = defaults.paragraphSpacing * sharedMobileStep(raw, 0f, 0f, 3f))
                SharedMobileReaderFormatAdjustment.IMAGE_SIZE -> settings.copy(imageScale = defaults.imageScale * sharedMobileStep(raw, 0f, 0.5f, 2f))
                SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN -> settings.copy(horizontalMargin = (defaults.resolvedHorizontalMargin * sharedMobileStep(raw, 0f, 0f, 3f)).roundToInt())
                SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN -> settings.copy(verticalMargin = (defaults.resolvedVerticalMargin * sharedMobileStep(raw, 0f, 0f, 3f)).roundToInt())
            }
        )
    }
    val valueLabel = when (adjustment) {
        SharedMobileReaderFormatAdjustment.FONT_WEIGHT -> sharedMobileFormatWeight(settings.fontWeight)
        SharedMobileReaderFormatAdjustment.LETTER_SPACING -> sharedMobileFormatLetterSpacing(settings.letterSpacing)
        SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN -> sharedMobileFormatMargin(value)
        SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN -> sharedMobileFormatMargin(value)
        else -> sharedMobileFormatMultiplier(value)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(adjustment.title) },
        text = {
            Column {
                Text(valueLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Slider(value = value.coerceIn(range), onValueChange = ::update, valueRange = range)
                if (adjustment == SharedMobileReaderFormatAdjustment.FONT_WEIGHT && settings.fontWeight == 0) {
                    Text("Original uses the weight supplied by the book or selected font.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                onSettingsChange(
                    when (adjustment) {
                        SharedMobileReaderFormatAdjustment.FONT_SIZE -> settings.copy(fontSize = defaults.fontSize)
                        SharedMobileReaderFormatAdjustment.FONT_WEIGHT -> settings.copy(fontWeight = defaults.fontWeight)
                        SharedMobileReaderFormatAdjustment.LETTER_SPACING -> settings.copy(letterSpacing = defaults.letterSpacing)
                        SharedMobileReaderFormatAdjustment.LINE_HEIGHT -> settings.copy(lineSpacing = defaults.lineSpacing)
                        SharedMobileReaderFormatAdjustment.PARAGRAPH_GAP -> settings.copy(paragraphSpacing = defaults.paragraphSpacing)
                        SharedMobileReaderFormatAdjustment.IMAGE_SIZE -> settings.copy(imageScale = defaults.imageScale)
                        SharedMobileReaderFormatAdjustment.HORIZONTAL_MARGIN -> settings.copy(horizontalMargin = defaults.resolvedHorizontalMargin)
                        SharedMobileReaderFormatAdjustment.VERTICAL_MARGIN -> settings.copy(verticalMargin = defaults.resolvedVerticalMargin)
                    }
                )
            }) { Text("Reset") }
        }
    )
}

internal fun sharedMobileStep(value: Float, delta: Float, minimum: Float, maximum: Float, precision: Float = 10f): Float =
    com.aryan.reader.shared.stepEpubFormatValue(value, delta, minimum, maximum, precision)

internal fun sharedMobileNextWeight(value: Int): Int = com.aryan.reader.shared.nextEpubFontWeight(value)
internal fun sharedMobilePreviousWeight(value: Int): Int = com.aryan.reader.shared.previousEpubFontWeight(value)
internal fun sharedMobileFormatWeight(value: Int): String = if (value <= 0) "Original" else value.toString()
internal fun sharedMobileFormatLetterSpacing(value: Float): String =
    if (kotlin.math.abs(value) < 0.001f) "Original" else "${if (value > 0f) "+" else ""}${(value * 100).roundToInt() / 100f}em"
internal fun sharedMobileFormatMultiplier(value: Float): String =
    if (value in 0.99f..1.01f) "Original" else "${(value * 10).roundToInt() / 10f}x"
internal fun sharedMobileFormatMargin(value: Float): String =
    when {
        value <= 0.01f -> "None"
        value in 0.99f..1.01f -> "Original"
        else -> "${(value * 10).roundToInt() / 10f}x"
    }

@Composable
internal fun SharedMobileEpubFormatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, allowNone: Boolean = false, onValueChange: (Float) -> Unit) {
    val current = value.coerceIn(range)
    val valueLabel = when { allowNone && current <= 0.01f -> "None"; current in 0.99f..1.01f -> "Orig"; else -> "${((current * 10).roundToInt() / 10f)}x" }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { onValueChange(((current - 0.1f).coerceAtLeast(range.start) * 10).roundToInt() / 10f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary) }
            SharedMobileEpubCustomCanvasSlider(value = current, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
            IconButton(onClick = { onValueChange(((current + 0.1f).coerceAtMost(range.endInclusive) * 10).roundToInt() / 10f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
internal fun SharedMobileEpubCustomCanvasSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.height(24.dp).pointerInput(valueRange) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(offset: Offset) {
                    val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val rawValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange((rawValue * 10f).roundToInt() / 10f)
                }
                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val trackY = (size.height - trackHeight) / 2f
            val corners = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            drawRoundRect(inactiveColor, Offset(0f, trackY), Size(size.width, trackHeight), corners)
            val activeWidth = fraction * size.width
            drawRoundRect(activeColor, Offset(0f, trackY), Size(activeWidth, trackHeight), corners)
            val thumbRadius = 8.dp.toPx()
            drawCircle(activeColor, thumbRadius, Offset(activeWidth.coerceIn(thumbRadius, size.width - thumbRadius), size.height / 2f))
        }
    }
}

@Composable
internal fun SharedMobileEpubSettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(if (value % 1f == 0f) value.toInt().toString() else ((value * 10).toInt() / 10f).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileEpubThemeSheet(
    settings: ReaderSettings,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val allThemes = BuiltInReaderThemes + customReaderThemes
    val selectedTheme = allThemes.firstOrNull { it.id == settings.themeId }
    var selectedTab by remember(settings.themeId) { mutableIntStateOf(if (selectedTheme?.textureId != null) 1 else 0) }
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }
    var showBuilder by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val builtIns = BuiltInReaderThemes.filter { (it.textureId != null) == (selectedTab == 1) }
    val customThemes = customReaderThemes.filter { (it.textureId != null) == (selectedTab == 1) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            Text("Reading Themes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, divider = {}) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Solid Colors", modifier = Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Textured", modifier = Modifier.padding(12.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            if (selectedTab == 1) {
                val transparency = 1f - settings.textureAlpha.coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Texture Transparency", style = MaterialTheme.typography.labelMedium)
                    Text("${(transparency * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = transparency, onValueChange = { onSettingsChange(settings.copy(textureAlpha = 1f - it)) })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                gridItems(builtIns, key = { it.id }) { theme ->
                    SharedMobileEpubThemeGridItem(
                        theme = theme,
                        selected = settings.themeId == theme.id || (settings.themeId == null && theme.id == "system"),
                        textureAlpha = settings.textureAlpha,
                        onSelected = { onSettingsChange(theme.toReaderSettings(settings)) }
                    )
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("My Themes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { editingTheme = null; showBuilder = true }) { Icon(Icons.Default.Add, contentDescription = "New", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                if (customThemes.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text("No custom themes yet. Tap '+' to create one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    gridItems(customThemes, key = { it.id }) { theme ->
                        SharedMobileEpubThemeGridItem(
                            theme = theme,
                            selected = settings.themeId == theme.id,
                            textureAlpha = settings.textureAlpha,
                            onSelected = { onSettingsChange(theme.toReaderSettings(settings)) },
                            onEdit = { editingTheme = theme; showBuilder = true },
                            onDelete = {
                                onCustomReaderThemesChange(customReaderThemes.filterNot { it.id == theme.id })
                                if (settings.themeId == theme.id) BuiltInReaderThemes.first().let { onSettingsChange(it.toReaderSettings(settings)) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showBuilder) {
        SharedReaderCustomThemeDialog(
            initialTheme = editingTheme,
            isTexturedMode = selectedTab == 1,
            customThemes = customReaderThemes,
            customTextureIds = emptyList(),
            onImportTexture = null,
            texturePreviewContent = null,
            onDismiss = { showBuilder = false; editingTheme = null },
            onSave = { saved ->
                val updated = if (editingTheme == null) customReaderThemes + saved else customReaderThemes.map { if (it.id == saved.id) saved else it }
                onCustomReaderThemesChange(updated)
                onSettingsChange(saved.toReaderSettings(settings))
                showBuilder = false
                editingTheme = null
            }
        )
    }
}

@Composable
internal fun SharedMobileEpubThemeGridItem(
    theme: ReaderTheme,
    selected: Boolean,
    textureAlpha: Float,
    onSelected: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val background = if (theme.id == "system") MaterialTheme.colorScheme.surfaceVariant else theme.backgroundColor
    val foreground = if (theme.id == "system") MaterialTheme.colorScheme.onSurfaceVariant else theme.textColor
    val texture = sharedMobileEpubTextureBitmap(theme.textureId)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).background(background, CircleShape)
                .then(texture?.let { bitmap -> Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), alpha = textureAlpha.coerceIn(0f, 1f), blendMode = if (theme.isDark) BlendMode.Screen else BlendMode.Multiply) } } ?: Modifier)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onSelected),
            contentAlignment = Alignment.Center
        ) { Text("Aa", color = foreground, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(theme.name, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onSelected))
        if (onEdit != null && onDelete != null) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Edit, "Edit", Modifier.size(28.dp).clickable(onClick = onEdit).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Delete, "Delete", Modifier.size(28.dp).clickable(onClick = onDelete).padding(6.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubPageInfo(
    chapterTitle: String,
    pageInfo: ReaderPageInfo?,
    progressPercent: Float,
    settings: ReaderSettings,
    modifier: Modifier = Modifier
) {
    val background = settings.readerPageInfoBackgroundColor()
    val foreground = settings.readerTextColor().copy(alpha = 0.8f)
    val texture = sharedMobileEpubTextureBitmap(settings.textureId)
    val clockTime = rememberReaderClockTime()
    Box(
        modifier.fillMaxWidth().height(25.dp).background(background)
            .then(texture?.let { bitmap -> Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), alpha = settings.textureAlpha.coerceIn(0f, 1f), blendMode = if (settings.darkMode) BlendMode.Screen else BlendMode.Multiply) } } ?: Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
            Text(
                pageInfo?.let {
                    "$chapterTitle (${it.currentPageInChapter}/${it.totalPagesInChapter})"
                } ?: chapterTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
            )
            Text(
                clockTime,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                "${formatReaderProgress(progressPercent)}%",
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
    }
}

@Composable
internal fun rememberReaderClockTime(): String {
    var currentTimeMillis by remember { mutableLongStateOf(currentTimestamp()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = currentTimestamp()
            currentTimeMillis = now
            delay(60_000L - now.mod(60_000L))
        }
    }
    return formatSharedMobileClockTime(currentTimeMillis)
}

internal fun formatReaderProgress(progressPercent: Float): String {
    val tenths = kotlin.math.floor(progressPercent.coerceIn(0f, 100f) * 10f).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

internal fun Long.hasDarkReaderBackground(): Boolean {
    fun channel(shift: Int): Double {
        val value = ((this ushr shift) and 0xFF).toDouble() / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0) < 0.5
}

@Composable
internal fun SharedMobileEpubChapterChangeIndicator(direction: String, progress: Float, modifier: Modifier = Modifier) {
    val alpha = (progress * 1.5f).coerceIn(0f, 1f)
    if (alpha <= 0.1f) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 4.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (direction == "previous") Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(20.dp * min(1f, progress + 0.2f))
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (progress >= 1f) if (direction == "previous") "Release for previous chapter" else "Release for next chapter" else "Pull further... (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileEpubVisualOptionsSheet(
    settings: ReaderSettings,
    readerBrightness: Float?,
    readerBrightnessSupported: Boolean,
    onReaderBrightnessChange: (Float?) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Text("System UI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose when the status and navigation bars are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedMobileEpubEnumChoices(SystemUiMode.entries, settings.systemUiMode, { it.title }) { onSettingsChange(settings.copy(systemUiMode = it)) }
            Spacer(Modifier.height(8.dp))
            Text("Progress Bar", style = MaterialTheme.typography.titleMedium)
            Text(
                "Show the current chapter page and reading percentage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedMobileEpubEnumChoices(PageInfoMode.entries, settings.pageInfoMode, { it.title }) { onSettingsChange(settings.copy(pageInfoMode = it)) }
            Text("Progress Bar Position", style = MaterialTheme.typography.titleSmall)
            SharedMobileEpubEnumChoices(PageInfoPosition.entries, settings.pageInfoPosition, { it.title }) { onSettingsChange(settings.copy(pageInfoPosition = it)) }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onSettingsChange(settings.copy(seamlessChapterNavigation = !settings.seamlessChapterNavigation))
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Seamless Chapter Transition", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Turn this off to pull past the edge before changing chapters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = !settings.seamlessChapterNavigation,
                            onCheckedChange = { seamless -> onSettingsChange(settings.copy(seamlessChapterNavigation = !seamless)) }
                        )
                    }
                    if (settings.seamlessChapterNavigation) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(16.dp)) {
                            SharedMobileEpubSettingSlider(
                                "Pull Distance to Change Chapter",
                                settings.chapterTurnDragMultiplier,
                                0.5f..2f,
                                14
                            ) { onSettingsChange(settings.copy(chapterTurnDragMultiplier = it)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SharedMobileEpubReaderImagesToggle(
                hideImages = settings.hideImages,
                onHideImagesChange = { hideImages -> onSettingsChange(settings.copy(hideImages = hideImages)) }
            )
        }
    }
}

@Composable
private fun SharedMobileEpubReaderImagesToggle(
    hideImages: Boolean,
    onHideImagesChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onHideImagesChange(!hideImages) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hide Images", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn this on to read with all images in the book hidden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = hideImages,
                    onCheckedChange = onHideImagesChange
                )
            }
        }
    }
}

@Composable
internal fun <T> SharedMobileEpubEnumChoices(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value), maxLines = 1) }, modifier = Modifier.weight(1f)) }
    }
}
