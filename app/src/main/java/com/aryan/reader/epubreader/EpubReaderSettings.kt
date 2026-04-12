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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.aryan.reader.R
import com.aryan.reader.data.CustomFontEntity
import java.io.File
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

const val SETTINGS_PREFS_NAME = "epub_reader_settings"
private const val TEXT_ALIGN_KEY = "reader_text_align"
private const val FONT_SIZE_KEY = "reader_font_size"
private const val LINE_HEIGHT_KEY = "reader_line_height"
private const val PARAGRAPH_GAP_KEY = "reader_paragraph_gap"
private const val AUTO_SCROLL_SPEED_KEY = "reader_auto_scroll_speed"
private const val FONT_FAMILY_KEY = "reader_font_family"
private const val TAP_TO_NAVIGATE_ENABLED_KEY = "tap_to_navigate_enabled"
private const val VOLUME_SCROLL_ENABLED_KEY = "volume_scroll_enabled"
private const val SYSTEM_UI_MODE_KEY = "reader_system_ui_mode"
private const val PAGE_INFO_MODE_KEY = "reader_page_info_mode"
private const val PULL_TO_TURN_ENABLED_KEY = "reader_pull_to_turn_enabled"

const val DEFAULT_FONT_SIZE_VAL = 1.0f
const val DEFAULT_LINE_HEIGHT_VAL = 1.0f
const val DEFAULT_PARAGRAPH_GAP_VAL = 1.0f
private const val TTS_SPEECH_RATE_KEY = "tts_speech_rate"
private const val TTS_PITCH_KEY = "tts_pitch"

fun saveTtsSpeechRate(context: Context, rate: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(TTS_SPEECH_RATE_KEY, rate) }
}

fun loadTtsSpeechRate(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(TTS_SPEECH_RATE_KEY, 1.0f)
}

fun saveTtsPitch(context: Context, pitch: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(TTS_PITCH_KEY, pitch) }
}

fun loadTtsPitch(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(TTS_PITCH_KEY, 1.0f)
}

enum class ReaderFont(val id: String, val displayName: String, val fontFamilyName: String) {
    ORIGINAL("original", "Original", "Original"),
    MERRIWEATHER("merriweather", "Merriweather", "Merriweather"),
    LATO("lato", "Lato", "Lato"),
    LORA("lora", "Lora", "Lora"),
    ROBOTO_MONO("roboto_mono", "Roboto Mono", "Roboto Mono"),
    LEXEND("lexend", "Lexend", "Lexend")
}

enum class ReaderTextAlign(val id: String, val cssValue: String, val iconResId: Int, val displayName: String) {
    DEFAULT("default", "", R.drawable.format_align_left, "Default"),
    LEFT("left", "left", R.drawable.format_align_left, "Left"),
    JUSTIFY("justify", "justify", R.drawable.format_align_justify, "Justify")
}

enum class SystemUiMode(val id: Int, val title: String) {
    DEFAULT(0, "Always Show"),
    SYNC(1, "Sync with Menus"),
    HIDDEN(2, "Always Hide")
}

enum class PageInfoMode(val id: Int, val title: String) {
    DEFAULT(0, "Always Show"),
    SYNC(1, "Sync with Menus"),
    HIDDEN(2, "Always Hide")
}

data class FormatSettings(
    val fontSize: Float,
    val lineHeight: Float,
    val paragraphGap: Float,
    val font: ReaderFont,
    val customPath: String?,
    val textAlign: ReaderTextAlign
)

private const val FORMAT_IS_LOCAL_PREFIX = "format_is_local_"
private const val LOCAL_FONT_SIZE_PREFIX = "local_font_size_"
private const val LOCAL_LINE_HEIGHT_PREFIX = "local_line_height_"
private const val LOCAL_PARAGRAPH_GAP_PREFIX = "local_paragraph_gap_"
private const val LOCAL_FONT_FAMILY_PREFIX = "local_font_family_"
private const val LOCAL_TEXT_ALIGN_PREFIX = "local_text_align_"

fun loadFormatIsLocal(context: Context, bookId: String): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(FORMAT_IS_LOCAL_PREFIX + bookId, false)
}

fun saveFormatIsLocal(context: Context, bookId: String, isLocal: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(FORMAT_IS_LOCAL_PREFIX + bookId, isLocal) }
}

fun saveLocalReaderSettings(
    context: Context,
    bookId: String,
    fontSize: Float,
    lineHeight: Float,
    paragraphGap: Float,
    fontFamily: ReaderFont,
    customFontPath: String?,
    textAlign: ReaderTextAlign
) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(LOCAL_FONT_SIZE_PREFIX + bookId, fontSize)
        putFloat(LOCAL_LINE_HEIGHT_PREFIX + bookId, lineHeight)
        putFloat(LOCAL_PARAGRAPH_GAP_PREFIX + bookId, paragraphGap)
        if (customFontPath != null) {
            putString(LOCAL_FONT_FAMILY_PREFIX + bookId, "custom|$customFontPath")
        } else {
            putString(LOCAL_FONT_FAMILY_PREFIX + bookId, fontFamily.id)
        }
        putString(LOCAL_TEXT_ALIGN_PREFIX + bookId, textAlign.id)
    }
}

fun saveSystemUiMode(context: Context, mode: SystemUiMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putInt(SYSTEM_UI_MODE_KEY, mode.id) }
}

fun loadSystemUiMode(context: Context): SystemUiMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val id = prefs.getInt(SYSTEM_UI_MODE_KEY, SystemUiMode.DEFAULT.id)
    return SystemUiMode.entries.find { it.id == id } ?: SystemUiMode.DEFAULT
}

fun savePageInfoMode(context: Context, mode: PageInfoMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putInt(PAGE_INFO_MODE_KEY, mode.id) }
}

fun loadPageInfoMode(context: Context): PageInfoMode {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val id = prefs.getInt(PAGE_INFO_MODE_KEY, PageInfoMode.DEFAULT.id)
    return PageInfoMode.entries.find { it.id == id } ?: PageInfoMode.DEFAULT
}

fun savePullToTurn(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PULL_TO_TURN_ENABLED_KEY, enabled) }
}

fun loadPullToTurn(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PULL_TO_TURN_ENABLED_KEY, true)
}

fun loadFormatSettings(context: Context, bookId: String, isLocal: Boolean): FormatSettings {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

    val fontSize = if (isLocal && prefs.contains(LOCAL_FONT_SIZE_PREFIX + bookId)) {
        prefs.getFloat(LOCAL_FONT_SIZE_PREFIX + bookId, DEFAULT_FONT_SIZE_VAL)
    } else {
        prefs.getFloat(FONT_SIZE_KEY, DEFAULT_FONT_SIZE_VAL)
    }

    val lineHeight = if (isLocal && prefs.contains(LOCAL_LINE_HEIGHT_PREFIX + bookId)) {
        prefs.getFloat(LOCAL_LINE_HEIGHT_PREFIX + bookId, DEFAULT_LINE_HEIGHT_VAL)
    } else {
        prefs.getFloat(LINE_HEIGHT_KEY, DEFAULT_LINE_HEIGHT_VAL)
    }

    val paragraphGap = if (isLocal && prefs.contains(LOCAL_PARAGRAPH_GAP_PREFIX + bookId)) {
        prefs.getFloat(LOCAL_PARAGRAPH_GAP_PREFIX + bookId, DEFAULT_PARAGRAPH_GAP_VAL)
    } else {
        prefs.getFloat(PARAGRAPH_GAP_KEY, DEFAULT_PARAGRAPH_GAP_VAL)
    }

    val savedFontVal = if (isLocal && prefs.contains(LOCAL_FONT_FAMILY_PREFIX + bookId)) {
        prefs.getString(LOCAL_FONT_FAMILY_PREFIX + bookId, ReaderFont.ORIGINAL.id) ?: ReaderFont.ORIGINAL.id
    } else {
        prefs.getString(FONT_FAMILY_KEY, ReaderFont.ORIGINAL.id) ?: ReaderFont.ORIGINAL.id
    }

    val (font, customPath) = if (savedFontVal.startsWith("custom|")) {
        Pair(ReaderFont.ORIGINAL, savedFontVal.substringAfter("custom|"))
    } else {
        Pair(ReaderFont.entries.find { it.id == savedFontVal } ?: ReaderFont.ORIGINAL, null)
    }

    val alignId = if (isLocal && prefs.contains(LOCAL_TEXT_ALIGN_PREFIX + bookId)) {
        prefs.getString(LOCAL_TEXT_ALIGN_PREFIX + bookId, ReaderTextAlign.DEFAULT.id)
    } else {
        prefs.getString(TEXT_ALIGN_KEY, ReaderTextAlign.DEFAULT.id)
    }
    val textAlign = ReaderTextAlign.entries.find { it.id == alignId } ?: ReaderTextAlign.DEFAULT

    return FormatSettings(fontSize, lineHeight, paragraphGap, font, customPath, textAlign)
}

fun getComposeFontFamily(
    font: ReaderFont,
    customFontPath: String? = null,
    assetManager: android.content.res.AssetManager? = null
): FontFamily {
    if (customFontPath != null) {
        return try {
            FontFamily(Font(File(customFontPath)))
        } catch (_: Exception) {
            FontFamily.Default
        }
    }

    if (assetManager != null) {
        return try {
            when (font) {
                ReaderFont.ORIGINAL -> FontFamily.Default
                ReaderFont.MERRIWEATHER -> FontFamily(Font("fonts/merriweather.ttf", assetManager))
                ReaderFont.LATO -> FontFamily(Font("fonts/lato.ttf", assetManager))
                ReaderFont.LORA -> FontFamily(Font("fonts/lora.ttf", assetManager))
                ReaderFont.ROBOTO_MONO -> FontFamily(Font("fonts/roboto_mono.ttf", assetManager))
                ReaderFont.LEXEND -> FontFamily(Font("fonts/lexend.ttf", assetManager))
            }
        } catch (_: Exception) {
            FontFamily.Default
        }
    }

    return FontFamily.Default
}

fun saveReaderSettings(
    context: Context,
    fontSize: Float,
    lineHeight: Float,
    paragraphGap: Float,
    fontFamily: ReaderFont,
    customFontPath: String?,
    textAlign: ReaderTextAlign
) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(FONT_SIZE_KEY, fontSize)
        putFloat(LINE_HEIGHT_KEY, lineHeight)
        putFloat(PARAGRAPH_GAP_KEY, paragraphGap)
        if (customFontPath != null) {
            putString(FONT_FAMILY_KEY, "custom|$customFontPath")
        } else {
            putString(FONT_FAMILY_KEY, fontFamily.id)
        }
        putString(TEXT_ALIGN_KEY, textAlign.id)
    }
}

fun saveAutoScrollSpeed(context: Context, speed: Float) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putFloat(AUTO_SCROLL_SPEED_KEY, speed) }
}

fun loadAutoScrollSpeed(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(AUTO_SCROLL_SPEED_KEY, 0.8f)
}

fun saveTapToNavigateSetting(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(TAP_TO_NAVIGATE_ENABLED_KEY, enabled) }
}

fun loadTapToNavigateSetting(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(TAP_TO_NAVIGATE_ENABLED_KEY, false)
}

fun saveVolumeScrollSetting(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(VOLUME_SCROLL_ENABLED_KEY, enabled) }
}

fun loadVolumeScrollSetting(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(VOLUME_SCROLL_ENABLED_KEY, false)
}

@Composable
fun ReaderTextFormatPanel(
    isVisible: Boolean,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentLineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    currentParagraphGap: Float, // NEW
    onParagraphGapChange: (Float) -> Unit, // NEW
    currentFont: ReaderFont,
    currentCustomFontName: String?,
    onFontOptionClick: () -> Unit,
    currentTextAlign: ReaderTextAlign,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onReset: () -> Unit,
    isLocalMode: Boolean,
    onLocalModeToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header Row (Local/Global + Close/Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        var showModeMenu by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showModeMenu = true }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = if (isLocalMode) stringResource(R.string.format_local) else stringResource(R.string.format_global),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.content_desc_select_mode),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.format_global), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.auto_scroll_applies_all_files), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { onLocalModeToggle(false); showModeMenu = false },
                                trailingIcon = { if (!isLocalMode) Icon(Icons.Default.Check, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.format_local), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.auto_scroll_saved_for_file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { onLocalModeToggle(true); showModeMenu = false },
                                trailingIcon = { if (isLocalMode) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(stringResource(R.string.action_reset))
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // FONT & ALIGNMENT SECTION
                Text(
                    text = stringResource(R.string.section_font_alignment),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                // Font Button (Full width)
                Surface(
                    onClick = onFontOptionClick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.label_aa_preview),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = currentCustomFontName ?: currentFont.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Alignment Button (Full width Segmented)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row {
                        ReaderTextAlign.entries.forEach { align ->
                            val isSelected = currentTextAlign == align
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onTextAlignChange(align) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = align.iconResId),
                                    contentDescription = align.displayName,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = align.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // LAYOUT & SPACING SECTION
                Text(
                    text = stringResource(R.string.section_layout_spacing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                // Sliders
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.label_font_size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                        Slider(
                            value = currentFontSize,
                            onValueChange = onFontSizeChange,
                            valueRange = 0.5f..3.0f,
                            steps = 24,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (currentFontSize in 0.99f..1.01f) stringResource(R.string.label_original) else "%.1fx".format(currentFontSize), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                    // Lines
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.label_line_height), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                        Slider(
                            value = currentLineHeight,
                            onValueChange = onLineHeightChange,
                            valueRange = 1.0f..3.0f,
                            steps = 19,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (currentLineHeight <= 1.01f) stringResource(R.string.label_original) else "%.1fx".format(currentLineHeight), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                    // Paragraph Gap
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.label_paragraph_gap), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                        Slider(
                            value = currentParagraphGap,
                            onValueChange = onParagraphGapChange,
                            valueRange = 0.0f..3.0f,
                            steps = 29,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (currentParagraphGap in 0.99f..1.01f) stringResource(R.string.label_original) else "%.1fx".format(currentParagraphGap), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun FontSelectionSheetContent(
    currentFont: ReaderFont,
    currentCustomFontPath: String?,
    onFontSelected: (ReaderFont, String?) -> Unit,
    customFonts: List<CustomFontEntity>,
    onImportFont: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportFont(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.select_font), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text(stringResource(R.string.tab_presets)) })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text(stringResource(R.string.tab_imported)) })
        }

        Box(modifier = Modifier.heightIn(min = 200.dp, max = 400.dp)) {
            when (selectedTabIndex) {
                0 -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(ReaderFont.entries.toTypedArray()) { font ->
                            val isSelected = currentCustomFontPath == null && currentFont == font
                            ListItem(
                                headlineContent = {
                                    Text(font.displayName, fontFamily = getComposeFontFamily(font, null))
                                },
                                trailingContent = {
                                    if (isSelected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.content_desc_selected), tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable { onFontSelected(font, null) },
                                colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else ListItemDefaults.colors()
                            )
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Button(
                                onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.button_import_from_files))
                            }
                        }

                        if (customFonts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.no_imported_fonts_yet),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 32.dp)
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                                items(customFonts) { fontEntity ->
                                    val isSelected = currentCustomFontPath == fontEntity.path
                                    val fontFamily = remember(fontEntity.path) {
                                        try { FontFamily(Font(File(fontEntity.path))) } catch(_:Exception) { FontFamily.Default }
                                    }

                                    ListItem(
                                        headlineContent = {
                                            Text(fontEntity.displayName, fontFamily = fontFamily)
                                        },
                                        trailingContent = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_selected),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        modifier = Modifier.clickable { onFontSelected(ReaderFont.ORIGINAL, fontEntity.path) },
                                        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else ListItemDefaults.colors()
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val REMOVE_EDGE_PADDING_KEY = "reader_remove_edge_padding"

fun saveRemoveEdgePadding(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(REMOVE_EDGE_PADDING_KEY, enabled) }
}

fun loadRemoveEdgePadding(context: Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(REMOVE_EDGE_PADDING_KEY, false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualOptionsSheet(
    systemUiMode: SystemUiMode,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    pageInfoMode: PageInfoMode,
    onPageInfoModeChange: (PageInfoMode) -> Unit,
    pullToTurnEnabled: Boolean,
    onPullToTurnChange: (Boolean) -> Unit,
    removeEdgePadding: Boolean,
    onRemoveEdgePaddingChange: (Boolean) -> Unit,
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.menu_visual_options), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // System UI
            Text(stringResource(R.string.visual_options_system_ui), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.visual_options_system_ui_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            OptionSegmentedControl(
                options = SystemUiMode.entries,
                selectedOption = systemUiMode,
                onOptionSelected = onSystemUiModeChange,
                getLabel = { it.title }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar
            Text(stringResource(R.string.visual_options_progress_bar), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.visual_options_progress_bar_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            OptionSegmentedControl(
                options = PageInfoMode.entries,
                selectedOption = pageInfoMode,
                onOptionSelected = onPageInfoModeChange,
                getLabel = { it.title }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pull to change chapter
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPullToTurnChange(!pullToTurnEnabled) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.visual_options_seamless_chapter), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.visual_options_seamless_chapter_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = !pullToTurnEnabled, onCheckedChange = { onPullToTurnChange(!it) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRemoveEdgePaddingChange(!removeEdgePadding) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.visual_options_edge_padding), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.visual_options_edge_padding_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = removeEdgePadding, onCheckedChange = { onRemoveEdgePaddingChange(it) })
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun <T> OptionSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    getLabel: (T) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getLabel(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}