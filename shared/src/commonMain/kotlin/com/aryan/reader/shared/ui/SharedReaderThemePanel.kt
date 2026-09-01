package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import kotlin.math.roundToInt

data class SharedReaderThemePanelLabels(
    val title: String,
    val solidColors: String,
    val textured: String,
    val textureTransparency: String,
    val preserveImageColors: String,
    val preserveImageColorsDescription: String,
    val presets: String,
    val myThemes: String,
    val newTheme: String,
    val noCustomThemes: String,
    val edit: String,
    val delete: String,
    val preview: String,
    val reverseColors: String = "Reverse colors",
    val reverseColorsDescription: String = "Choose how PDF colors are inverted",
    val reverseRgb: String = "Invert colors",
    val reverseLightness: String = "Invert lightness",
    val reverseLumaSrgb: String = "Invert luma (sRGB linear)",
    val reverseLumaSymmetric: String = "Invert luma (symmetric)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderThemePanel(
    isVisible: Boolean,
    currentThemeId: String,
    excludeImages: Boolean = false,
    onExcludeImagesChange: (Boolean) -> Unit = {},
    showExcludeImagesOption: Boolean = false,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
    onReverseColorModeChange: (PdfReverseColorMode) -> Unit = {},
    showReverseColorOption: Boolean = false,
    customThemes: List<ReaderTheme>,
    builtInThemes: List<ReaderTheme>,
    globalTextureTransparency: Float,
    onGlobalTextureTransparencyChange: (Float) -> Unit,
    onThemeSelected: (String) -> Unit,
    onCustomThemesUpdated: (List<ReaderTheme>) -> Unit,
    onDismiss: () -> Unit,
    labels: SharedReaderThemePanelLabels,
    texturePreview: @Composable (textureId: String, alpha: Float, modifier: Modifier) -> Unit,
    builderContent: @Composable (
        initialTheme: ReaderTheme?,
        isTexturedMode: Boolean,
        globalTextureAlpha: Float,
        onSave: (ReaderTheme) -> Unit,
        onCancel: () -> Unit,
    ) -> Unit,
) {
    if (!isVisible) return
    var showBuilder by remember { mutableStateOf(false) }
    var builderIsTextured by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }
    var selectedTabIndex by remember(currentThemeId, builtInThemes, customThemes) {
        mutableIntStateOf(
            if (
                builtInThemes.find { it.id == currentThemeId }?.textureId != null ||
                customThemes.find { it.id == currentThemeId }?.textureId != null
            ) 1 else 0,
        )
    }
    val plainBuiltInThemes = builtInThemes.filter { it.textureId == null }
    val texturedBuiltInThemes = builtInThemes.filter { it.textureId != null }
    val plainCustomThemes = customThemes.filter { it.textureId == null }
    val texturedCustomThemes = customThemes.filter { it.textureId != null }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        AnimatedContent(targetState = showBuilder, label = "ThemePanelTransition") { isBuilding ->
            if (isBuilding) {
                builderContent(
                    editingTheme,
                    builderIsTextured,
                    1f - globalTextureTransparency,
                    { newTheme ->
                        val updated = if (editingTheme != null) {
                            customThemes.map { if (it.id == newTheme.id) newTheme else it }
                        } else {
                            customThemes + newTheme
                        }
                        onCustomThemesUpdated(updated)
                        onThemeSelected(newTheme.id)
                        showBuilder = false
                        editingTheme = null
                    },
                    {
                        showBuilder = false
                        editingTheme = null
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        labels.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp, top = 8.dp),
                    )
                    TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                        Tab(selectedTabIndex == 0, { selectedTabIndex = 0 }) {
                            Text(labels.solidColors, modifier = Modifier.padding(12.dp))
                        }
                        Tab(selectedTabIndex == 1, { selectedTabIndex = 1 }) {
                            Text(labels.textured, modifier = Modifier.padding(12.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (selectedTabIndex == 1) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(labels.textureTransparency, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${(globalTextureTransparency * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = globalTextureTransparency,
                                onValueChange = onGlobalTextureTransparencyChange,
                                valueRange = 0f..1f,
                            )
                        }
                    }
                    val activeBuiltIns = if (selectedTabIndex == 0) plainBuiltInThemes else texturedBuiltInThemes
                    val activeCustom = if (selectedTabIndex == 0) plainCustomThemes else texturedCustomThemes
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (showReverseColorOption && currentThemeId == "reverse") {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(labels.reverseColors, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        labels.reverseColorsDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                    )
                                    val modes = listOf(
                                        PdfReverseColorMode.RGB to labels.reverseRgb,
                                        PdfReverseColorMode.LIGHTNESS to labels.reverseLightness,
                                        PdfReverseColorMode.LUMA_SRGB_LINEAR to labels.reverseLumaSrgb,
                                        PdfReverseColorMode.LUMA_SYMMETRIC to labels.reverseLumaSymmetric,
                                    )
                                    modes.chunked(2).forEach { rowModes ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowModes.forEach { (mode, label) ->
                                                FilterChip(
                                                    selected = reverseColorMode == mode,
                                                    onClick = { onReverseColorModeChange(mode) },
                                                    label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        if (showExcludeImagesOption) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(labels.preserveImageColors, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(labels.preserveImageColorsDescription, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Switch(excludeImages, onExcludeImagesChange)
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(labels.presets, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        items(activeBuiltIns, key = { it.id }) { theme ->
                            SharedReaderThemeGridItem(
                                theme, currentThemeId, 1f - globalTextureTransparency,
                                onThemeSelected, null, null, labels, texturePreview,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(labels.myThemes, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = {
                                    editingTheme = null
                                    builderIsTextured = selectedTabIndex == 1
                                    showBuilder = true
                                }) {
                                    Icon(Icons.Default.Add, labels.newTheme, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (activeCustom.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(labels.noCustomThemes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(activeCustom, key = { it.id }) { theme ->
                                SharedReaderThemeGridItem(
                                    theme, currentThemeId, 1f - globalTextureTransparency,
                                    onThemeSelected,
                                    {
                                        editingTheme = it
                                        builderIsTextured = selectedTabIndex == 1
                                        showBuilder = true
                                    },
                                    { deleting ->
                                        onCustomThemesUpdated(customThemes.filter { it.id != deleting.id })
                                        if (currentThemeId == deleting.id) onThemeSelected("system")
                                    },
                                    labels, texturePreview,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderThemeGridItem(
    theme: ReaderTheme,
    currentThemeId: String,
    textureAlpha: Float,
    onThemeSelected: (String) -> Unit,
    onEdit: ((ReaderTheme) -> Unit)?,
    onDelete: ((ReaderTheme) -> Unit)?,
    labels: SharedReaderThemePanelLabels,
    texturePreview: @Composable (String, Float, Modifier) -> Unit,
) {
    val selected = currentThemeId == theme.id
    val background = if (theme.id == "system") MaterialTheme.colorScheme.surfaceVariant else theme.backgroundColor
    val foreground = if (theme.id == "system") MaterialTheme.colorScheme.onSurfaceVariant else theme.textColor
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .background(background, CircleShape)
                .clip(CircleShape)
                .border(if (selected) 3.dp else 1.dp, border, CircleShape)
                .clickable { onThemeSelected(theme.id) },
            contentAlignment = Alignment.Center,
        ) {
            theme.textureId?.let { texturePreview(it, textureAlpha, Modifier.matchParentSize()) }
            Text(labels.preview, color = foreground, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { onThemeSelected(theme.id) },
        )
        if (theme.isCustom && onEdit != null && onDelete != null) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, labels.edit, Modifier.size(28.dp).clip(CircleShape).clickable { onEdit(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Delete, labels.delete, Modifier.size(28.dp).clip(CircleShape).clickable { onDelete(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
