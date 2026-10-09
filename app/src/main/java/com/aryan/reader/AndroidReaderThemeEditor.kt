@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderTheme

import com.aryan.reader.shared.ReaderTexture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.pdf.PdfHighlightColor
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.ui.SharedHighlightPaletteSlotDialog
import com.aryan.reader.shared.ReaderTextureFilePrefix
import kotlinx.coroutines.launch
import org.commonmark.node.Text
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderThemePanel(
    isVisible: Boolean,
    currentThemeId: String,
    excludeImages: Boolean = false,
    onExcludeImagesChange: (Boolean) -> Unit = {},
    showExcludeImagesOption: Boolean = false,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
    onReverseColorModeChange: (PdfReverseColorMode) -> Unit = {},
    showReverseColorOption: Boolean = false,
    customThemes: List<ReaderTheme>,
    builtInThemes: List<ReaderTheme> = BuiltInThemes,
    globalTextureTransparency: Float,
    onGlobalTextureTransparencyChange: (Float) -> Unit,
    onThemeSelected: (String) -> Unit,
    onCustomThemesUpdated: (List<ReaderTheme>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    com.aryan.reader.shared.ui.SharedReaderThemePanel(
        isVisible = isVisible,
        currentThemeId = currentThemeId,
        excludeImages = excludeImages,
        onExcludeImagesChange = onExcludeImagesChange,
        showExcludeImagesOption = showExcludeImagesOption,
        reverseColorMode = reverseColorMode,
        onReverseColorModeChange = onReverseColorModeChange,
        showReverseColorOption = showReverseColorOption,
        customThemes = customThemes,
        builtInThemes = builtInThemes,
        globalTextureTransparency = globalTextureTransparency,
        onGlobalTextureTransparencyChange = onGlobalTextureTransparencyChange,
        onThemeSelected = onThemeSelected,
        onCustomThemesUpdated = onCustomThemesUpdated,
        onDismiss = onDismiss,
        labels = com.aryan.reader.shared.ui.SharedReaderThemePanelLabels(
            title = stringResource(R.string.reading_themes),
            solidColors = stringResource(R.string.theme_solid_colors),
            textured = stringResource(R.string.theme_textured),
            textureTransparency = stringResource(R.string.theme_texture_transparency),
            preserveImageColors = stringResource(R.string.theme_preserve_image_colors),
            preserveImageColorsDescription = stringResource(R.string.theme_preserve_image_colors_desc),
            presets = stringResource(R.string.theme_presets),
            myThemes = stringResource(R.string.theme_my_themes),
            newTheme = stringResource(R.string.theme_new),
            noCustomThemes = stringResource(R.string.theme_no_custom),
            edit = stringResource(R.string.action_edit),
            delete = stringResource(R.string.action_delete),
            preview = stringResource(R.string.label_aa_preview),
            reverseColors = stringResource(R.string.pdf_reverse_colors),
            reverseColorsDescription = stringResource(R.string.pdf_reverse_colors_desc),
            reverseRgb = stringResource(R.string.pdf_reverse_rgb),
            reverseLightness = stringResource(R.string.pdf_reverse_lightness),
            reverseLumaSrgb = stringResource(R.string.pdf_reverse_luma_srgb),
            reverseLumaSymmetric = stringResource(R.string.pdf_reverse_luma_symmetric),
        ),
        texturePreview = { textureId, alpha, modifier ->
            val bitmap = remember(textureId) { loadReaderTextureBitmap(context, textureId) }
            Box(
                modifier.then(
                    bitmap?.let {
                        Modifier.drawBehind {
                            drawRect(
                                ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)),
                                blendMode = BlendMode.SrcOver,
                                alpha = alpha,
                            )
                        }
                    } ?: Modifier,
                ),
            )
        },
        builderContent = { initialTheme, isTexturedMode, globalTextureAlpha, onSave, onCancel ->
            ThemeBuilderView(
                initialTheme = initialTheme,
                isTexturedMode = isTexturedMode,
                globalTextureAlpha = globalTextureAlpha,
                onSave = onSave,
                onCancel = onCancel,
            )
        },
    )
}

@Composable
fun ThemeBuilderView(
    initialTheme: ReaderTheme?,
    isTexturedMode: Boolean,
    globalTextureAlpha: Float,
    onSave: (ReaderTheme) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var importedTextures by remember { mutableStateOf(getImportedTextures(context)) }

    com.aryan.reader.shared.ui.SharedReaderThemeBuilder(
        initialTheme = initialTheme,
        isTexturedMode = isTexturedMode,
        globalTextureAlpha = globalTextureAlpha,
        defaultTextureId = importedTextures.firstOrNull(),
        labels = com.aryan.reader.shared.ui.SharedReaderThemeBuilderLabels(
            customTexturedDefault = stringResource(R.string.theme_custom_textured_default),
            customSolidDefault = stringResource(R.string.theme_custom_solid_default),
            newTheme = stringResource(R.string.theme_new),
            editTheme = stringResource(R.string.theme_edit),
            themeName = stringResource(R.string.theme_name),
            previewQuote = stringResource(R.string.theme_preview_quote),
            previewAuthor = stringResource(R.string.theme_preview_author),
            lowContrastWarning = stringResource(R.string.theme_low_contrast_warning),
            pageColor = stringResource(R.string.theme_page_color),
            textColor = stringResource(R.string.theme_text_color),
            cancel = stringResource(R.string.action_cancel),
            save = stringResource(R.string.action_save),
        ),
        newThemeId = { System.currentTimeMillis().toString() },
        onSave = onSave,
        onCancel = onCancel,
        texturePreview = { textureId, alpha, modifier ->
            val bitmap = remember(textureId) { loadReaderTextureBitmap(context, textureId) }
            Box(
                modifier.then(
                    bitmap?.let {
                        Modifier.drawBehind {
                            drawRect(
                                ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)),
                                blendMode = BlendMode.SrcOver,
                                alpha = alpha,
                            )
                        }
                    } ?: Modifier,
                ),
            )
        },
        texturePickerContent = { selectedTextureId, onTextureSelected ->
            val texturePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { importReaderTexture(context, it) }?.let { newId ->
                    importedTextures = getImportedTextures(context)
                    onTextureSelected(newId)
                }
            }
            CustomTexturePickerSection(
                importedTextures = importedTextures,
                selectedTextureId = selectedTextureId,
                onTextureSelected = onTextureSelected,
                onImportTexture = {
                    texturePickerLauncher.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp"))
                },
            )
        },
        colorPickerContent = { target, initialColor, backgroundColor, textColor, onDismiss, onColorChanged ->
            val isBackground = target == com.aryan.reader.shared.ui.SharedReaderThemeColorTarget.BACKGROUND
            ThemeColorPickerDialog(
                initialColor = initialColor,
                title = stringResource(if (isBackground) R.string.theme_page_color else R.string.theme_text_color),
                bgColor = backgroundColor,
                textColor = textColor,
                editingColorType = if (isBackground) "bg" else "text",
                onDismiss = onDismiss,
                onColorChanged = onColorChanged,
            )
        },
    )
}

@Composable
internal fun CustomTexturePickerSection(
    importedTextures: List<String>,
    selectedTextureId: String?,
    onTextureSelected: (String?) -> Unit,
    onImportTexture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.theme_select_custom_texture), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                Surface(
                    onClick = onImportTexture,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_import), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.action_import), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(importedTextures) { tex ->
                val isSelected = tex == selectedTextureId
                val context = LocalContext.current
                val bitmap = remember(tex) { loadReaderTextureBitmap(context, tex) }

                Surface(
                    onClick = { onTextureSelected(tex) },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSelected) 0.95f else 0.45f),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().then(bitmap?.let {
                            Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = 0.6f) }
                        } ?: Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeColorPickerDialog(
    initialColor: Color,
    title: String,
    bgColor: Color,
    textColor: Color,
    editingColorType: String,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit
) {
    val configuration = LocalConfiguration.current
    com.aryan.reader.shared.ui.SharedReaderThemeColorPickerDialog(
        initialColor = initialColor,
        title = title,
        backgroundColor = bgColor,
        textColor = textColor,
        editingBackground = editingColorType == "bg",
        maxDialogHeight = readerModalMaxHeightDp(configuration.screenHeightDp).dp,
        labels = com.aryan.reader.shared.ui.SharedReaderThemeColorPickerLabels(
            livePreview = stringResource(R.string.theme_color_live_preview),
            previewText = stringResource(R.string.theme_color_preview_text),
            hex = stringResource(R.string.theme_color_hex),
            red = stringResource(R.string.color_r),
            green = stringResource(R.string.color_g),
            blue = stringResource(R.string.color_b),
            save = stringResource(R.string.action_save),
        ),
        onDismiss = onDismiss,
        onColorChanged = onColorChanged,
    )
}

/**
 * PDF highlighter palette editor.
 *
 * A thin adapter over the shared [SharedHighlightPaletteSlotDialog], which carries the layout this
 * dialog had: every [PdfHighlightColor] visible while one is edited, hex + RGB inputs, reset of
 * the selected slot to its stock colour, Save / Cancel. The ~190 lines of inline spectrum
 * machinery it used to hold now live in shared, with a single implementation.
 */
@Composable
fun HighlightColorPickerDialog(
    initialColors: Map<PdfHighlightColor, Color>,
    initialSelection: PdfHighlightColor = PdfHighlightColor.YELLOW,
    onDismiss: () -> Unit,
    onSave: (Map<PdfHighlightColor, Color>) -> Unit
) {
    SharedHighlightPaletteSlotDialog(
        title = stringResource(R.string.highlight_customize_title),
        slots = PdfHighlightColor.entries.map { initialColors[it] ?: it.color },
        defaultSlots = PdfHighlightColor.entries.map { it.color },
        initialSelection = PdfHighlightColor.entries.indexOf(initialSelection),
        maxDialogHeight = readerModalMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp,
        onSave = { saved ->
            onSave(PdfHighlightColor.entries.mapIndexed { index, slot -> slot to saved[index] }.toMap())
        },
        onDismiss = onDismiss
    )
}
