/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import android.content.Context
import android.widget.TextView
import timber.log.Timber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import com.aryan.reader.BrightnessSlider
import com.aryan.reader.ColorComparePill
import com.aryan.reader.HexInput
import com.aryan.reader.R
import com.aryan.reader.RgbInputColumn
import com.aryan.reader.SpectrumBox
import com.aryan.reader.readerModalMaxHeightDp
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderLocator

private const val BOOKMARK_PREFS_NAME = "epub_reader_bookmarks"

typealias Bookmark = com.aryan.reader.shared.EpubBookmark
typealias HighlightColor = com.aryan.reader.shared.HighlightColor
typealias HighlightStyle = com.aryan.reader.shared.HighlightStyle
typealias UserHighlight = com.aryan.reader.shared.UserHighlight

fun escapeJsString(value: String): String {
    return com.aryan.reader.shared.escapeJsString(value)
}

private val DefaultHighlightPaletteArgb: List<Int>
    get() = listOf(
        HighlightColor.YELLOW.color.toArgb(),
        HighlightColor.GREEN.color.toArgb(),
        HighlightColor.BLUE.color.toArgb(),
        HighlightColor.RED.color.toArgb()
    )

fun saveHighlightPalette(context: Context, palette: List<Int>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val argbs = sanitizeHighlightPalette(palette).joinToString(",") { it.toString() }
    prefs.edit {
        putString("highlight_palette_argbs", argbs)
        remove("highlight_palette_ids")
    }
}

fun loadHighlightPalette(context: Context): List<Int> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedArgbs = prefs.getString("highlight_palette_argbs", null)
    if (savedArgbs != null) {
        val list = savedArgbs.split(",").mapNotNull { it.toIntOrNull() }
        if (list.size == 4) return list
    }
    val savedIds = prefs.getString("highlight_palette_ids", null)
    if (savedIds != null) {
        val list = savedIds.split(",").mapNotNull { id ->
            HighlightColor.entries.find { it.id == id }?.color?.toArgb()
        }
        if (list.size == 4) return list
    }
    return DefaultHighlightPaletteArgb
}

fun savePreferredHighlightStyle(context: Context, style: HighlightStyle) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString("preferred_highlight_style", style.id) }
}

fun loadPreferredHighlightStyle(context: Context): HighlightStyle {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return HighlightStyle.fromId(prefs.getString("preferred_highlight_style", null))
}

private fun sanitizeHighlightPalette(palette: List<Int>): List<Int> {
    return palette.takeIf { it.size == 4 } ?: DefaultHighlightPaletteArgb
}

internal fun legacyHighlightColorForArgb(argb: Int): HighlightColor {
    return HighlightColor.entries.firstOrNull { it.color.toArgb() == argb } ?: HighlightColor.YELLOW
}

internal fun legacyHighlightColorOrNull(argb: Int): HighlightColor? {
    return HighlightColor.entries.firstOrNull { it.color.toArgb() == argb }
}

internal fun highlightColorTag(argb: Int): String {
    return legacyHighlightColorOrNull(argb)?.id ?: "custom_${argb.toUInt().toString(16)}"
}

internal fun highlightColorFromToken(token: String): Pair<HighlightColor, Int?> {
    val trimmed = token.trim()
    val parsedArgb = trimmed.toIntOrNull()
        ?: trimmed.removePrefix("#").takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16)?.let { value ->
            if (trimmed.removePrefix("#").length == 6) (0xFF000000 or value).toInt() else value.toInt()
        }
    if (parsedArgb != null) {
        return legacyHighlightColorForArgb(parsedArgb) to parsedArgb
    }
    val legacy = HighlightColor.entries.find { it.id == trimmed } ?: HighlightColor.YELLOW
    return legacy to null
}

// --- Persistence Helpers ---

fun loadBookmarks(context: Context, bookTitle: String, chapters: List<EpubChapter>, bookmarksJson: String?): Set<Bookmark> {
    val stringSetToParse: Collection<String> = if (bookmarksJson != null) {
        return EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson, chapters.map { it.title })
    } else {
        val prefs = context.getSharedPreferences(BOOKMARK_PREFS_NAME, Context.MODE_PRIVATE)
        val key = "bookmarks_cfi_${bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")}"
        prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    return EpubAnnotationSerializer.parseBookmarkEntries(stringSetToParse, chapters.map { it.title })
}

fun saveHighlightsToPrefs(context: Context, bookTitle: String, highlights: List<UserHighlight>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    prefs.edit { putString(key, EpubAnnotationSerializer.highlightsToJson(highlights)) }
}

fun loadHighlightsFromPrefs(context: Context, bookTitle: String): List<UserHighlight> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    val jsonString = prefs.getString(key, "[]") ?: "[]"
    return EpubAnnotationSerializer.parseHighlightsJson(jsonString)
}

fun parseHighlightsJson(jsonString: String?): List<UserHighlight> {
    return EpubAnnotationSerializer.parseHighlightsJson(jsonString)
}

fun highlightsToJson(highlights: List<UserHighlight>): String {
    return EpubAnnotationSerializer.highlightsToJson(highlights)
}

fun bookmarksToJson(bookmarks: Collection<Bookmark>): String {
    return EpubAnnotationSerializer.bookmarksToJson(bookmarks)
}

fun clearHighlightsFromPrefs(context: Context, bookTitle: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    prefs.edit { remove(key) }
}

// --- Logic Helpers ---

fun processAndAddHighlight(
    newCfi: String,
    newText: String,
    newColor: HighlightColor,
    chapterIndex: Int,
    currentList: MutableList<UserHighlight>,
    locator: ReaderLocator = ReaderLocator.fromLegacy(
        chapterIndex = chapterIndex,
        cfi = newCfi,
        textQuote = newText
    ),
    newColorArgb: Int? = null,
    newStyle: HighlightStyle = HighlightStyle.BACKGROUND
): String {
    return EpubAnnotationSerializer.processAndAddHighlight(
        newCfi = newCfi,
        newText = newText,
        newColor = newColor,
        chapterIndex = chapterIndex,
        currentList = currentList,
        locator = locator,
        newColorArgb = newColorArgb,
        newStyle = newStyle
    )
}

// --- UI Components ---

@Composable
fun BookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isBookmarked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.bookmark),
                contentDescription = stringResource(R.string.content_desc_bookmark_icon),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpectrumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp
) {
    val rainbowColors = listOf(
        Color.Red, Color(0xFFFF7F00), Color.Yellow, Color.Green,
        Color.Blue, Color(0xFF4B0082), Color(0xFF8B00FF)
    )

    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.sweepGradient(rainbowColors),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun PaletteManagerDialog(
    currentPalette: List<Int>,
    initialSelection: Int = 0,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentColors by remember { mutableStateOf(sanitizeHighlightPalette(currentPalette)) }
    var selectedSlot by remember { mutableIntStateOf(initialSelection.coerceIn(0, currentColors.lastIndex)) }

    val initialActiveColor = Color(currentColors[selectedSlot])
    val initialHsv = remember(initialActiveColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialActiveColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    LaunchedEffect(selectedSlot) {
        val color = Color(currentColors[selectedSlot])
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val currentColor by remember {
        derivedStateOf {
            Color(android.graphics.Color.HSVToColor(255, floatArrayOf(hue, saturation, value)))
        }
    }

    LaunchedEffect(currentColor) {
        val next = currentColors.toMutableList()
        next[selectedSlot] = currentColor.toArgb()
        currentColors = next
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
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
                    currentColors.forEachIndexed { index, argb ->
                        val slotColor = Color(argb)
                        val isSelected = selectedSlot == index
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(slotColor)
                                .clickable { selectedSlot = index }
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
                        oldColor = Color(currentPalette.getOrNull(selectedSlot) ?: DefaultHighlightPaletteArgb[selectedSlot]),
                        newColor = currentColor,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.theme_color_hex), color = Color.Gray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                    TextButton(onClick = { updateFromColor(Color(DefaultHighlightPaletteArgb[selectedSlot])) }) {
                        Text(stringResource(R.string.action_reset), color = Color(0xFFFF5252))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel), color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(sanitizeHighlightPalette(currentColors)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun highlightStyleIconRes(style: HighlightStyle): Int {
    return when (style) {
        HighlightStyle.BACKGROUND -> R.drawable.font_background
        HighlightStyle.UNDERLINE -> R.drawable.format_underlined
        HighlightStyle.WAVY_UNDERLINE -> R.drawable.format_underlined_squiggle
        HighlightStyle.STRIKETHROUGH -> R.drawable.strikethrough
    }
}

@Composable
private fun HighlightStyleRow(
    selectedStyle: HighlightStyle,
    effectiveText: Color,
    onStyleSelect: (HighlightStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HighlightStyle.entries.forEach { style ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedStyle == style) effectiveText.copy(alpha = 0.16f)
                        else effectiveText.copy(alpha = 0.06f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedStyle == style) effectiveText.copy(alpha = 0.55f) else effectiveText.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onStyleSelect(style) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = highlightStyleIconRes(style)),
                    contentDescription = style.id,
                    tint = effectiveText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationBottomSheet(
    highlight: UserHighlight,
    effectiveBg: Color,
    effectiveText: Color,
    activeHighlightPalette: List<Int>,
    onColorChange: (Int) -> Unit,
    onStyleChange: (HighlightStyle) -> Unit,
    onOpenPaletteManager: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember { mutableStateOf(highlight.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = effectiveBg, // Matches user theme
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Top: Highlight Style + Colors
            HighlightStyleRow(
                selectedStyle = highlight.style,
                effectiveText = effectiveText,
                onStyleSelect = {
                    savePreferredHighlightStyle(context, it)
                    onStyleChange(it)
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )
            HighlightColorRow(
                activeHighlightPalette = activeHighlightPalette,
                selectedColorArgb = highlight.colorArgb ?: highlight.color.color.toArgb(),
                onColorSelect = onColorChange,
                onOpenPaletteManager = onOpenPaletteManager,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Middle: Elegant Highlight Snippet Card
            Surface(
                color = highlight.effectiveColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, highlight.effectiveColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Left colored accent bar
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(highlight.effectiveColor)
                    )
                    Text(
                        text = "\"${highlight.text}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = effectiveText.copy(alpha = 0.9f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomSheetToolButton(icon = R.drawable.copy, label = stringResource(R.string.action_copy), onClick = onCopy, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.dictionary, label = stringResource(R.string.label_dict), onClick = onDictionary, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.translate, label = stringResource(R.string.dict_translate), onClick = onTranslate, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.search, label = stringResource(R.string.action_search), onClick = onSearch, effectiveText = effectiveText)
            }

            Spacer(Modifier.height(16.dp))

            // Note TextField
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text(stringResource(R.string.placeholder_add_note), color = effectiveText.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = effectiveText.copy(alpha = 0.3f),
                    focusedTextColor = effectiveText,
                    unfocusedTextColor = effectiveText
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
                Button(
                    onClick = { onSave(noteText) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_save_note))
                }
            }
        }
    }
}

@Composable
private fun BottomSheetToolButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    effectiveText: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = effectiveText.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effectiveText.copy(alpha = 0.8f)
        )
    }
}

private class MenuActionItem(
    val iconRes: Int? = null,
    val imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val label: String,
    val onClick: () -> Unit,
    val isError: Boolean = false
)

@Composable
fun HighlightColorRow(
    modifier: Modifier = Modifier,
    activeHighlightPalette: List<Int>,
    selectedColorArgb: Int? = null,
    onColorSelect: (Int) -> Unit,
    onOpenPaletteManager: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeHighlightPalette.forEach { argb ->
            val swatchColor = Color(argb)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(28.dp)
                    .testTag("HighlightColor_${highlightColorTag(argb)}")
                    .clip(CircleShape)
                    .background(swatchColor)
                    .clickable {
                        Timber.d("HighlightColorRow: Color clicked -> ${highlightColorTag(argb)}")
                        onColorSelect(argb)
                    }
                    .border(
                        width = if (selectedColorArgb == argb) 3.dp else 1.dp,
                        color = if (selectedColorArgb == argb) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha=0.3f),
                        shape = CircleShape
                    )
            ) {
                if (selectedColorArgb == argb) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.content_desc_selected),
                        tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (onOpenPaletteManager != null) {
            Spacer(modifier = Modifier.width(6.dp))
            SpectrumButton(
                onClick = onOpenPaletteManager,
                size = 28.dp
            )
        }
    }
}

@Composable
fun PaginatedTextSelectionMenu(
    onCopy: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onHighlight: ((Int, HighlightStyle) -> Unit)?,
    onNote: ((HighlightStyle) -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onTts: (() -> Unit)?,
    @Suppress("unused") isProUser: Boolean,
    @Suppress("unused") isOss: Boolean,
    activeHighlightPalette: List<Int> = emptyList(),
    onOpenPaletteManager: (() -> Unit)? = null,
    existingNote: String? = null,
    selectedColorArgb: Int? = null,
    selectedStyle: HighlightStyle = HighlightStyle.BACKGROUND
) {
    val context = LocalContext.current
    var selectedHighlightStyle by remember(selectedStyle) {
        mutableStateOf(if (selectedStyle == HighlightStyle.BACKGROUND) loadPreferredHighlightStyle(context) else selectedStyle)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp)) {
            // 1. Highlight style and colors row
            if (onHighlight != null) {
                HighlightStyleRow(
                    selectedStyle = selectedHighlightStyle,
                    effectiveText = MaterialTheme.colorScheme.onSurface,
                    onStyleSelect = {
                        selectedHighlightStyle = it
                        savePreferredHighlightStyle(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp)
                )
                HighlightColorRow(
                    activeHighlightPalette = activeHighlightPalette,
                    selectedColorArgb = selectedColorArgb,
                    onColorSelect = { onHighlight(it, selectedHighlightStyle) },
                    onOpenPaletteManager = onOpenPaletteManager
                )
                HorizontalDivider()
            }

            // 2. Improved Comment/Note View
            if (!existingNote.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.label_note),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.label_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = existingNote,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            val actions = mutableListOf<MenuActionItem>()
            actions.add(MenuActionItem(iconRes = R.drawable.copy, label = stringResource(R.string.action_copy), onClick = onCopy))
            if (onTts != null) {
                actions.add(MenuActionItem(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = stringResource(R.string.label_speak), onClick = onTts))
            }
            actions.add(MenuActionItem(iconRes = R.drawable.dictionary, label = stringResource(R.string.label_dict), onClick = onDictionary))
            actions.add(MenuActionItem(iconRes = R.drawable.translate, label = stringResource(R.string.dict_translate), onClick = onTranslate))
            actions.add(MenuActionItem(iconRes = R.drawable.search, label = stringResource(R.string.action_search), onClick = onSearch))

            if (onNote != null) {
                val noteLabel = if (existingNote.isNullOrBlank()) stringResource(R.string.label_note) else stringResource(R.string.label_edit)
                actions.add(MenuActionItem(imageVector = Icons.Default.Edit, label = noteLabel, onClick = { onNote(selectedHighlightStyle) }))
            }

            if (onSelectAll != null) {
                actions.add(MenuActionItem(iconRes = R.drawable.select_all, label = stringResource(R.string.select_all), onClick = onSelectAll))
            }
            if (onDelete != null) {
                actions.add(MenuActionItem(imageVector = Icons.Default.Delete, label = stringResource(R.string.action_remove), onClick = onDelete, isError = true))
            }

            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val tint = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { action.onClick() }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (action.imageVector != null) {
                                    Icon(imageVector = action.imageVector, contentDescription = action.label, tint = tint, modifier = Modifier.size(22.dp))
                                } else if (action.iconRes != null) {
                                    Icon(painter = painterResource(id = action.iconRes), contentDescription = action.label, tint = tint, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = action.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootnoteBottomSheet(
    htmlContent: String,
    effectiveBg: Color,
    effectiveText: Color,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp * 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = effectiveBg,
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.label_note),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = effectiveText.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, effectiveText.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            TextView(context).apply {
                                setTextColor(effectiveText.toArgb())
                                textSize = 16f
                                setLineSpacing(0f, 1.4f)

                                isVerticalScrollBarEnabled = false
                                movementMethod = null
                            }
                        },
                        update = { textView ->
                            textView.text = HtmlCompat.fromHtml(
                                htmlContent,
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            ).trimEnd()
                        }
                    )
                }
            }
        }
    }
}
