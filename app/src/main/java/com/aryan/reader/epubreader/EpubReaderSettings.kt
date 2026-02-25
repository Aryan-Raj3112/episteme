/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.edit
import com.aryan.reader.R
import com.aryan.reader.data.CustomFontEntity
import java.io.File

const val SETTINGS_PREFS_NAME = "epub_reader_settings"
private const val TEXT_ALIGN_KEY = "reader_text_align"
private const val FONT_SIZE_KEY = "reader_font_size"
private const val LINE_HEIGHT_KEY = "reader_line_height"
private const val AUTO_SCROLL_SPEED_KEY = "reader_auto_scroll_speed"
private const val FONT_FAMILY_KEY = "reader_font_family"
private const val TAP_TO_NAVIGATE_ENABLED_KEY = "tap_to_navigate_enabled"
private const val VOLUME_SCROLL_ENABLED_KEY = "volume_scroll_enabled"

const val DEFAULT_FONT_SIZE_VAL = 1.0f
const val DEFAULT_LINE_HEIGHT_VAL = 1.6f

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

fun loadFontSelection(context: Context): Pair<ReaderFont, String?> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedVal = prefs.getString(FONT_FAMILY_KEY, ReaderFont.ORIGINAL.id) ?: ReaderFont.ORIGINAL.id

    return if (savedVal.startsWith("custom|")) {
        val path = savedVal.substringAfter("custom|")
        Pair(ReaderFont.ORIGINAL, path)
    } else {
        val font = ReaderFont.entries.find { it.id == savedVal } ?: ReaderFont.ORIGINAL
        Pair(font, null)
    }
}

fun saveReaderSettings(
    context: Context,
    fontSize: Float,
    lineHeight: Float,
    fontFamily: ReaderFont,
    customFontPath: String?,
    textAlign: ReaderTextAlign
) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(FONT_SIZE_KEY, fontSize)
        putFloat(LINE_HEIGHT_KEY, lineHeight)
        if (customFontPath != null) {
            putString(FONT_FAMILY_KEY, "custom|$customFontPath")
        } else {
            putString(FONT_FAMILY_KEY, fontFamily.id)
        }
        putString(TEXT_ALIGN_KEY, textAlign.id)
    }
}

fun loadFontSize(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(FONT_SIZE_KEY, DEFAULT_FONT_SIZE_VAL)
}

fun loadLineHeight(context: Context): Float {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getFloat(LINE_HEIGHT_KEY, DEFAULT_LINE_HEIGHT_VAL)
}

fun loadTextAlign(context: Context): ReaderTextAlign {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val id = prefs.getString(TEXT_ALIGN_KEY, ReaderTextAlign.DEFAULT.id)
    return ReaderTextAlign.entries.find { it.id == id } ?: ReaderTextAlign.DEFAULT
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
    currentFont: ReaderFont,
    currentCustomFontName: String?,
    onFontOptionClick: () -> Unit,
    currentTextAlign: ReaderTextAlign,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Family", style = MaterialTheme.typography.labelLarge)

                    Surface(
                        onClick = onFontOptionClick,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            val displayName = currentCustomFontName ?: currentFont.displayName
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "%.1fx".format(currentFontSize),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentFontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 0.5f..3.0f,
                        steps = 24,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Line Spacing", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "%.1fx".format(currentLineHeight),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentLineHeight,
                        onValueChange = onLineHeightChange,
                        valueRange = 1.0f..2.5f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        var alignmentMenuExpanded by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { alignmentMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = currentTextAlign.iconResId),
                                    contentDescription = "Text Alignment",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = alignmentMenuExpanded,
                            onDismissRequest = { alignmentMenuExpanded = false }
                        ) {
                            ReaderTextAlign.entries.forEach { align ->
                                DropdownMenuItem(
                                    text = { Text(align.displayName) },
                                    leadingIcon = {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = align.iconResId),
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (align == currentTextAlign) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    },
                                    onClick = {
                                        onTextAlignChange(align)
                                        alignmentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    TextButton(onClick = onReset) {
                        Text("Reset Defaults")
                    }
                }
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
            Text("Select Font", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Presets") })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Imported") })
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
                                    if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
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
                                Text("Import from Files")
                            }
                        }

                        if (customFonts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No imported fonts yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 32.dp)
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                                items(customFonts) { fontEntity ->
                                    val isSelected = currentCustomFontPath == fontEntity.path
                                    val fontFamily = remember(fontEntity.path) {
                                        try { FontFamily(androidx.compose.ui.text.font.Font(File(fontEntity.path))) } catch(_:Exception) { FontFamily.Default }
                                    }

                                    ListItem(
                                        headlineContent = {
                                            Text(fontEntity.displayName, fontFamily = fontFamily)
                                        },
                                        trailingContent = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
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