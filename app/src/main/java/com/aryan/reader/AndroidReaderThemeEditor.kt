@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderTheme

import com.aryan.reader.shared.ReaderTexture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.pdf.PdfHighlightColor
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.ui.SharedHsvColor
import com.aryan.reader.shared.ui.toSharedHsvColor
import kotlinx.coroutines.launch
import org.commonmark.node.Text
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderThemePanel(
    isVisible: Boolean,
    currentThemeId: String,
    excludeImages: Boolean = false,
    onExcludeImagesChange: (Boolean) -> Unit = {},
    showExcludeImagesOption: Boolean = false,
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
internal fun TexturePickerSection(
    selectedTextureId: String?,
    selectedTextureAlpha: Float,
    onTextureSelected: (String?) -> Unit,
    onTextureAlphaChange: (Float) -> Unit,
    onImportTexture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.theme_texture), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextureChoice(
                label = stringResource(R.string.theme_texture_none),
                textureId = null,
                selectedTextureId = selectedTextureId,
                onTextureSelected = onTextureSelected,
                modifier = Modifier.weight(1f)
            )
            TextureChoice(
                label = stringResource(R.string.theme_texture_upload),
                textureId = selectedTextureId?.takeIf { it.startsWith(ReaderTextureFilePrefix) },
                selectedTextureId = selectedTextureId,
                onTextureSelected = { onImportTexture() },
                isUpload = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        ReaderTexture.entries.filter { it.androidTextureResourceId() == null }.chunked(2).forEach { rowTextures ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                rowTextures.forEach { texture ->
                    TextureChoice(
                        label = texture.displayName,
                        textureId = texture.id,
                        selectedTextureId = selectedTextureId,
                        onTextureSelected = onTextureSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTextures.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(visible = selectedTextureId != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.theme_texture_transparency), style = MaterialTheme.typography.labelMedium)
                    Text("${((1f - selectedTextureAlpha) * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = 1f - selectedTextureAlpha,
                    onValueChange = { onTextureAlphaChange((1f - it).coerceIn(0f, 1f)) },
                    valueRange = 0f..1f
                )
            }
        }
    }
}

@Composable
internal fun TextureChoice(
    label: String,
    textureId: String?,
    selectedTextureId: String?,
    onTextureSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    isUpload: Boolean = false,
) {
    val context = LocalContext.current
    val textureBitmap = remember(textureId) { loadReaderTextureBitmap(context, textureId) }
    val selected = if (isUpload) selectedTextureId?.startsWith(ReaderTextureFilePrefix) == true else selectedTextureId == textureId
    Surface(
        onClick = { onTextureSelected(textureId) },
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.95f else 0.45f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(textureBitmap?.let { bitmap ->
                    Modifier.drawBehind {
                        drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = 0.6f)
                    }
                } ?: Modifier)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isUpload && selectedTextureId?.startsWith(ReaderTextureFilePrefix) == true) {
                    readerTextureDisplayName(selectedTextureId)
                } else label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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

@Composable
fun HighlightColorPickerDialog(
    initialColors: Map<PdfHighlightColor, Color>,
    initialSelection: PdfHighlightColor = PdfHighlightColor.YELLOW,
    onDismiss: () -> Unit,
    onSave: (Map<PdfHighlightColor, Color>) -> Unit
) {
    var currentColors by remember { mutableStateOf(initialColors) }
    var selectedSlot by remember { mutableStateOf(initialSelection) }

    val initialActiveColor = currentColors[selectedSlot] ?: selectedSlot.color
    val initialHsv = remember(initialActiveColor) { initialActiveColor.toSharedHsvColor() }

    var hue by remember { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var value by remember { mutableFloatStateOf(initialHsv.value) }

    LaunchedEffect(selectedSlot) {
        val color = currentColors[selectedSlot] ?: selectedSlot.color
        val hsv = color.toSharedHsvColor()
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
    }

    val currentColor by remember {
        derivedStateOf {
            SharedHsvColor(hue, saturation, value).toComposeColor()
        }
    }

    LaunchedEffect(currentColor) {
        currentColors = currentColors + (selectedSlot to currentColor)
    }

    fun updateFromColor(color: Color) {
        val hsv = color.toSharedHsvColor()
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val configuration = LocalConfiguration.current
        val maxDialogHeight = readerModalMaxHeightDp(configuration.screenHeightDp).dp

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .heightIn(max = maxDialogHeight)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.highlight_customize_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PdfHighlightColor.entries.forEach { slot ->
                        val slotColor = currentColors[slot] ?: slot.color
                        val isSelected = selectedSlot == slot
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(slotColor)
                                .clickable { selectedSlot = slot }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.content_desc_selected),
                                    tint = if (slotColor.luminance() > 0.5f) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                SpectrumBox(
                    hue = hue,
                    saturation = saturation,
                    currentColor = currentColor,
                    onHueSatChanged = { h, s -> hue = h; saturation = s },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )

                Spacer(Modifier.height(20.dp))

                BrightnessSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onValueChanged = { value = it },
                    modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorComparePill(
                        oldColor = selectedSlot.color,
                        newColor = currentColor,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.theme_color_hex), color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        HexInput(color = currentColor, onHexChanged = { updateFromColor(it) })
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RgbInputColumn(label = stringResource(R.string.color_r), value = currentColor.red,
                            onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_g), value = currentColor.green,
                            onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_b), value = currentColor.blue,
                            onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { updateFromColor(selectedSlot.color) }) {
                        Text(stringResource(R.string.action_reset), color = Color(0xFFFF5252))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel), color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(currentColors) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
