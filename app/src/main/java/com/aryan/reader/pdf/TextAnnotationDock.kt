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
package com.aryan.reader.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.toColorInt
import com.aryan.reader.R
import com.aryan.reader.data.CustomFontEntity
import timber.log.Timber
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ColorMenuMode {
    PALETTE,
    SPECTRUM
}

private enum class ActivePopup {
    NONE, FONT_SIZE, FONT_FAMILY, COLOR, BACKGROUND
}

private fun getAssetPathForReaderFont(fontName: String): String? {
    return when (fontName) {
        "Merriweather" -> "asset:fonts/merriweather.ttf"
        "Lato" -> "asset:fonts/lato.ttf"
        "Lora" -> "asset:fonts/lora.ttf"
        "Roboto Mono" -> "asset:fonts/roboto_mono.ttf"
        "Lexend" -> "asset:fonts/lexend.ttf"
        else -> null
    }
}

@Composable
fun TextAnnotationDock(
    currentStyle: SpanStyle,
    textColorPalette: List<Color>,
    onTextColorPaletteChange: (List<Color>) -> Unit,
    backgroundColorPalette: List<Color>,
    onBackgroundColorPaletteChange: (List<Color>) -> Unit,
    onUpdateStyle: (SpanStyle) -> Unit,
    onApplyToSelection: () -> Unit,
    onClose: () -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    onInsertTextBox: () -> Unit,
    onClearTextBoxSelection: () -> Unit = {},
    bottomDockPadding: androidx.compose.ui.unit.Dp = 0.dp,
    customFonts: List<CustomFontEntity> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    currentFontName: String? = null,
    onFontSelected: (String, String?) -> Unit = { _, _ -> }
) {
    var activeMenuMode by remember { mutableStateOf(ColorMenuMode.PALETTE) }
    var colorPickerSlotIndex by remember { mutableIntStateOf(-1) }
    var activePopup by remember { mutableStateOf(ActivePopup.NONE) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportFont(it) } }
    )

    LaunchedEffect(activePopup) {
        onPopupStateChange(activePopup != ActivePopup.NONE)
    }

    val fontSizes = listOf(12.sp, 14.sp, 16.sp, 18.sp, 20.sp, 24.sp, 30.sp)
    val focusManager = LocalFocusManager.current

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val spacerHeightPx = max(0, imeBottom - navBottom)
    val spacerHeightDp = with(density) { spacerHeightPx.toDp() }

    val effectiveSpacerHeight = if (spacerHeightDp > 0.dp) {
        spacerHeightDp
    } else {
        bottomDockPadding
    }

    LaunchedEffect(activePopup) {
        if (activePopup == ActivePopup.NONE) {
            activeMenuMode = ColorMenuMode.PALETTE
            colorPickerSlotIndex = -1
        }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        if (activePopup != ActivePopup.NONE && activePopup != ActivePopup.FONT_SIZE) {

            val dockBarHeight = 48.dp
            val margin = 8.dp
            val finalOffsetY = -(effectiveSpacerHeight + dockBarHeight + margin)

            val isFocusable = activeMenuMode == ColorMenuMode.SPECTRUM || activePopup == ActivePopup.FONT_FAMILY

            DockBubblePopup(
                onDismissRequest = { activePopup = ActivePopup.NONE },
                alignment = Alignment.BottomCenter,
                offsetY = finalOffsetY,
                focusable = isFocusable
            ) {
                when (activePopup) {
                    ActivePopup.FONT_FAMILY -> {
                        // Re-using the logic from your EPUB FontSelectionSheetContent but adapted for a Popup
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E1E1E),
                            shadowElevation = 8.dp,
                            modifier = Modifier.width(260.dp)
                        ) {
                            var selectedTabIndex by remember { mutableIntStateOf(0) }
                            Column {
                                TabRow(
                                    selectedTabIndex = selectedTabIndex,
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    divider = {}
                                ) {
                                    Tab(
                                        selected = selectedTabIndex == 0,
                                        onClick = { selectedTabIndex = 0 },
                                        text = { Text("Presets", fontSize = 12.sp) }
                                    )
                                    Tab(
                                        selected = selectedTabIndex == 1,
                                        onClick = { selectedTabIndex = 1 },
                                        text = { Text("Imported", fontSize = 12.sp) }
                                    )
                                }

                                Box(modifier = Modifier.heightIn(max = 300.dp).padding(8.dp)) {
                                    if (selectedTabIndex == 0) {
                                        LazyColumn {
                                            item {
                                                val isSelected = currentFontName == "Default" || currentFontName == null
                                                FontItem(
                                                    name = "Default System Font",
                                                    isSelected = isSelected,
                                                    fontFamily = FontFamily.Default,
                                                    onClick = {
                                                        onFontSelected("Default", null)
                                                        activePopup = ActivePopup.NONE
                                                    }
                                                )
                                            }

                                            items(com.aryan.reader.epubreader.ReaderFont.entries.toTypedArray()) { font ->
                                                if (font == com.aryan.reader.epubreader.ReaderFont.ORIGINAL) return@items
                                                val isSelected = currentFontName == font.displayName
                                                FontItem(
                                                    name = font.displayName,
                                                    isSelected = isSelected,
                                                    fontFamily = com.aryan.reader.epubreader.getComposeFontFamily(font, null, LocalContext.current.assets),
                                                    onClick = {
                                                        val assetPath = getAssetPathForReaderFont(font.displayName)
                                                        onFontSelected(font.displayName, assetPath)
                                                        activePopup = ActivePopup.NONE
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Column {
                                            Button(
                                                onClick = {
                                                    fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf"))
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Import", fontSize = 12.sp)
                                            }

                                            if (customFonts.isEmpty()) {
                                                Text(
                                                    "No fonts imported",
                                                    color = Color.Gray,
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    textAlign = TextAlign.Center,
                                                    fontSize = 12.sp
                                                )
                                            } else {
                                                LazyColumn {
                                                    items(customFonts) { fontEntity ->
                                                        val isSelected = currentFontName == fontEntity.displayName
                                                        val customFamily = remember(fontEntity.path) {
                                                            try { FontFamily(Font(File(fontEntity.path))) } catch(_:Exception) { FontFamily.Default }
                                                        }
                                                        FontItem(
                                                            name = fontEntity.displayName,
                                                            isSelected = isSelected,
                                                            fontFamily = customFamily,
                                                            onClick = {
                                                                onFontSelected(fontEntity.displayName, fontEntity.path)
                                                                activePopup = ActivePopup.NONE
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ActivePopup.COLOR -> {
                        if (activeMenuMode == ColorMenuMode.PALETTE) {
                            ColorPickerBubble(
                                title = "Font color",
                                currentColor = currentStyle.color.takeIf { it != Color.Unspecified }
                                    ?: Color.Black,
                                palette = textColorPalette,
                                showTransparent = false,
                                onColorSelected = { color ->
                                    onUpdateStyle(currentStyle.copy(color = color, fontFamily = currentStyle.fontFamily))
                                    onApplyToSelection()
                                    activePopup = ActivePopup.NONE
                                },
                                onPaletteChanged = onTextColorPaletteChange,
                                onShowColorPicker = { slotIndex ->
                                    colorPickerSlotIndex = slotIndex
                                    activeMenuMode = ColorMenuMode.SPECTRUM
                                },
                                onDismiss = { activePopup = ActivePopup.NONE }
                            )
                        } else {
                            val initialColor =
                                textColorPalette.getOrElse(colorPickerSlotIndex) { Color.Black }
                            ColorPickerSpectrumContent(
                                initialColor = initialColor,
                                onBack = { activeMenuMode = ColorMenuMode.PALETTE },
                                onColorSelected = { newColor ->
                                    val mutableList = textColorPalette.toMutableList()
                                    if (colorPickerSlotIndex in mutableList.indices) {
                                        mutableList[colorPickerSlotIndex] = newColor
                                        onTextColorPaletteChange(mutableList)
                                        onUpdateStyle(currentStyle.copy(color = newColor, fontFamily = currentStyle.fontFamily))
                                        onApplyToSelection()
                                    }
                                    activeMenuMode = ColorMenuMode.PALETTE
                                }
                            )
                        }
                    }
                    ActivePopup.BACKGROUND -> {
                        if (activeMenuMode == ColorMenuMode.PALETTE) {
                            ColorPickerBubble(
                                title = "Highlight",
                                currentColor = when (currentStyle.background) {
                                    Color.Unspecified, Color.Transparent -> Color.Transparent
                                    else -> currentStyle.background
                                },
                                palette = backgroundColorPalette,
                                showTransparent = true,
                                onColorSelected = { color ->
                                    onUpdateStyle(currentStyle.copy(background = color, fontFamily = currentStyle.fontFamily))
                                    onApplyToSelection()
                                    activePopup = ActivePopup.NONE
                                },
                                onPaletteChanged = onBackgroundColorPaletteChange,
                                onShowColorPicker = { slotIndex ->
                                    colorPickerSlotIndex = slotIndex
                                    activeMenuMode = ColorMenuMode.SPECTRUM
                                },
                                onDismiss = { activePopup = ActivePopup.NONE }
                            )
                        } else {
                            val initialColor =
                                backgroundColorPalette.getOrElse(colorPickerSlotIndex) { Color.Black }
                            ColorPickerSpectrumContent(
                                initialColor = initialColor,
                                onBack = { activeMenuMode = ColorMenuMode.PALETTE },
                                onColorSelected = { newColor ->
                                    val mutableList = backgroundColorPalette.toMutableList()
                                    if (colorPickerSlotIndex in mutableList.indices) {
                                        mutableList[colorPickerSlotIndex] = newColor
                                        onBackgroundColorPaletteChange(mutableList)
                                        onUpdateStyle(currentStyle.copy(background = newColor, fontFamily = currentStyle.fontFamily))
                                        onApplyToSelection()
                                    }
                                    activeMenuMode = ColorMenuMode.PALETTE
                                }
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = Color(0xFFF0F0F0),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = activePopup == ActivePopup.FONT_FAMILY,
                            iconRes = R.drawable.fonts,
                            contentDescription = "Select Font Family",
                            onClick = {
                                activePopup =
                                    if (activePopup == ActivePopup.FONT_FAMILY) ActivePopup.NONE else ActivePopup.FONT_FAMILY
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activePopup == ActivePopup.FONT_SIZE) {
                            DockBubblePopup(
                                onDismissRequest = { activePopup = ActivePopup.NONE },
                                offsetY = (-55).dp,
                                alignment = Alignment.TopCenter,
                                focusable = false
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .heightIn(max = 200.dp)
                                        .width(80.dp)
                                        .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                                ) {
                                    items(fontSizes) { size ->
                                        val isSelected = currentStyle.fontSize == size
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onUpdateStyle(currentStyle.copy(fontSize = size, fontFamily = currentStyle.fontFamily))
                                                    onApplyToSelection()
                                                    activePopup = ActivePopup.NONE
                                                }
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${size.value.toInt()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activePopup =
                                        if (activePopup == ActivePopup.FONT_SIZE) ActivePopup.NONE else ActivePopup.FONT_SIZE
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${currentStyle.fontSize.value.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Font Size",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activeMenuMode = ColorMenuMode.PALETTE
                                    activePopup =
                                        if (activePopup == ActivePopup.COLOR) ActivePopup.NONE else ActivePopup.COLOR
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    (-3).dp,
                                    Alignment.CenterVertically
                                )
                            ) {
                                Text(
                                    text = "A",
                                    color = Color.Black,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                        .background(
                                            currentStyle.color.takeIf { it != Color.Unspecified }
                                                ?: Color.Black)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activeMenuMode = ColorMenuMode.PALETTE
                                    activePopup =
                                        if (activePopup == ActivePopup.BACKGROUND) ActivePopup.NONE else ActivePopup.BACKGROUND
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    (0).dp,
                                    Alignment.CenterVertically
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.font_background),
                                    contentDescription = "Font Background",
                                    modifier = Modifier.size(17.dp),
                                    tint = Color.Black
                                )
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                        .background(
                                            if (currentStyle.background == Color.Unspecified || currentStyle.background == Color.Transparent)
                                                Color.Gray else currentStyle.background
                                        )
                                )
                            }
                        }
                    }

                    val isBold = currentStyle.fontWeight == FontWeight.Bold
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(), contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = isBold,
                            iconRes = R.drawable.format_bold,
                            contentDescription = "Bold",
                            onClick = {
                                val newW = if (isBold) FontWeight.Normal else FontWeight.Bold
                                onUpdateStyle(currentStyle.copy(fontWeight = newW, fontFamily = currentStyle.fontFamily))
                                onApplyToSelection()
                            }
                        )
                    }

                    val isItalic = currentStyle.fontStyle == FontStyle.Italic
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(), contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = isItalic,
                            iconRes = R.drawable.format_italic,
                            contentDescription = "Italic",
                            onClick = {
                                val newStyle = if (isItalic) FontStyle.Normal else FontStyle.Italic
                                onUpdateStyle(currentStyle.copy(fontStyle = newStyle, fontFamily = currentStyle.fontFamily))
                                onApplyToSelection()
                            }
                        )
                    }

                    val currentDec = currentStyle.textDecoration ?: TextDecoration.None
                    val isUnderline = currentDec.contains(TextDecoration.Underline)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(), contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = isUnderline,
                            iconRes = R.drawable.format_underlined,
                            contentDescription = "Underline",
                            onClick = {
                                val hasStrike = currentDec.contains(TextDecoration.LineThrough)
                                val newDec = if (isUnderline) {
                                    if (hasStrike) TextDecoration.LineThrough else TextDecoration.None
                                } else {
                                    if (hasStrike) TextDecoration.combine(
                                        listOf(
                                            TextDecoration.Underline,
                                            TextDecoration.LineThrough
                                        )
                                    ) else TextDecoration.Underline
                                }
                                onUpdateStyle(currentStyle.copy(textDecoration = newDec, fontFamily = currentStyle.fontFamily))
                                onApplyToSelection()
                            }
                        )
                    }

                    val isStrike = currentDec.contains(TextDecoration.LineThrough)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(), contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = isStrike,
                            iconRes = R.drawable.format_strikethrough,
                            contentDescription = "Strikethrough",
                            onClick = {
                                val hasUnd = currentDec.contains(TextDecoration.Underline)
                                val newDec = if (isStrike) {
                                    if (hasUnd) TextDecoration.Underline else TextDecoration.None
                                } else {
                                    if (hasUnd) TextDecoration.combine(
                                        listOf(
                                            TextDecoration.Underline,
                                            TextDecoration.LineThrough
                                        )
                                    ) else TextDecoration.LineThrough
                                }
                                onUpdateStyle(currentStyle.copy(textDecoration = newDec, fontFamily = currentStyle.fontFamily))
                                onApplyToSelection()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = false,
                            iconRes = R.drawable.text_box,
                            contentDescription = "Insert Text Box",
                            onClick = {
                                Timber.tag("PdfTextBoxDebug").d("Dock: Insert Text Box icon clicked")
                                onInsertTextBox()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(), contentAlignment = Alignment.Center
                    ) {
                        FormattingIconButton(
                            isSelected = false,
                            iconVector = Icons.Default.Close,
                            contentDescription = "Close",
                            onClick = {
                                focusManager.clearFocus()
                                onClearTextBoxSelection()
                                onClose()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(effectiveSpacerHeight))
        }
    }
}

@Composable
private fun FontItem(
    name: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily),
            maxLines = 1
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DockBubblePopup(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.TopCenter,
    offsetY: androidx.compose.ui.unit.Dp,
    focusable: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val offsetPx = with(density) { offsetY.roundToPx() }

    Popup(
        alignment = alignment,
        offset = IntOffset(0, offsetPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = focusable,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        content()
    }
}

@Composable
private fun ColorPickerBubble(
    title: String,
    currentColor: Color,
    palette: List<Color>,
    showTransparent: Boolean = false,
    onColorSelected: (Color) -> Unit,
    @Suppress("unused") onPaletteChanged: (List<Color>) -> Unit,
    onShowColorPicker: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 8.dp,
        modifier = Modifier.wrapContentWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .width(androidx.compose.foundation.layout.IntrinsicSize.Min)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.width(16.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTransparent) {
                    val isSelected = currentColor == Color.Transparent || currentColor == Color.Unspecified
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(Color.Transparent) },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color(0xFFAAAAAA))
                            drawLine(
                                color = Color(0xFF555555),
                                start = Offset(size.width * 0.2f, size.height * 0.2f),
                                end = Offset(size.width * 0.8f, size.height * 0.8f),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Regular palette colors
                palette.forEachIndexed { index, color ->
                    val isSelected = if (color == Color.White) {
                        currentColor == color || currentColor == Color.Unspecified
                    } else {
                        currentColor == color
                    }

                    ColorCircle(
                        color = color,
                        isSelected = isSelected,
                        onClick = { onColorSelected(color) },
                        onLongClick = { onShowColorPicker(index) }
                    )
                }

                // Spectrum button
                val rainbowColors = listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(rainbowColors))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onShowColorPicker(palette.size.coerceAtMost(4)) },
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (color == Color.White) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                } else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            val checkColor = if (color.luminance() > 0.6f) Color.Black else Color.White
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FormattingIconButton(
    isSelected: Boolean,
    iconRes: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color.Black.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        } else if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = contentDescription,
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledPropertySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isOpacity: Boolean,
    trackColor: Color,
    thumbColor: Color,
    activeColor: Color,
    trackBrush: Brush? = null
) {
    val displayValue = remember(value, valueRange) {
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        (fraction * 100).roundToInt().coerceIn(0, 100)
    }
    val onePercentDelta = (valueRange.endInclusive - valueRange.start) / 100f
    val canDecrease = value > valueRange.start + 0.0001f
    val canIncrease = value < valueRange.endInclusive - 0.0001f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canDecrease) {
                    val newValue = (value - onePercentDelta).coerceAtLeast(valueRange.start)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                color = if (canDecrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(32.dp),
                thumb = {
                    Surface(
                        shape = CircleShape,
                        color = thumbColor,
                        modifier = Modifier
                            .size(26.dp)
                            .padding(2.dp),
                        shadowElevation = 4.dp,
                        border = if (isOpacity) null else androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayValue.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                track = { _ ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    ) {
                        val trackHeight = size.height
                        val cornerRadius = CornerRadius(trackHeight / 2)

                        if (trackBrush != null) {
                            // Draw gradient track
                            drawRoundRect(
                                brush = trackBrush,
                                size = size,
                                cornerRadius = cornerRadius
                            )
                        } else if (isOpacity) {
                            drawRoundRect(
                                color = Color.Gray,
                                size = size,
                                cornerRadius = cornerRadius
                            )

                            val clipPath = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        rect = androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                                        cornerRadius = cornerRadius
                                    )
                                )
                            }

                            clipPath(clipPath) {
                                val boxSize = 12f
                                val columns = (size.width / boxSize).toInt() + 1
                                val rows = (size.height / boxSize).toInt() + 1

                                for (col in 0 until columns) {
                                    for (row in 0 until rows) {
                                        val color = if ((col + row) % 2 == 0) Color(0xFF555555) else Color(0xFF333333)
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(col * boxSize, row * boxSize),
                                            size = androidx.compose.ui.geometry.Size(boxSize, boxSize)
                                        )
                                    }
                                }
                                drawRect(color = activeColor)
                            }

                        } else {
                            drawRoundRect(
                                color = trackColor.copy(alpha = 0.5f),
                                size = size,
                                cornerRadius = cornerRadius
                            )

                            val dotRadius = 1.5.dp.toPx()
                            val padding = trackHeight / 2
                            val availableWidth = size.width - (padding * 2)
                            val dotCount = 8
                            val spacing = availableWidth / (dotCount - 1)

                            for (i in 0 until dotCount) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.2f),
                                    radius = dotRadius,
                                    center = Offset(padding + (i * spacing), size.height / 2)
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canIncrease) {
                    val newValue = (value + onePercentDelta).coerceAtMost(valueRange.endInclusive)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (canIncrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ColorPickerSpectrumContent(
    initialColor: Color,
    onBack: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val lockedInitialColor = remember { initialColor }

    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val alpha = 1.0f

    val currentColor by remember {
        derivedStateOf {
            val hsv = floatArrayOf(hue, saturation, value)
            val argb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv)
            Color(argb)
        }
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2C2C2C),
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Spectrum",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Spectrum Area
            SpectrumBox(
                hue = hue,
                saturation = saturation,
                currentColor = currentColor,
                onHueSatChanged = { h, s ->
                    hue = h
                    saturation = s
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Brightness Slider
            val brightnessGradient = remember(hue, saturation) {
                Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.hsv(hue, saturation, 1f))
                )
            }

            StyledPropertySlider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..1f,
                isOpacity = false,
                trackColor = Color.Transparent,
                thumbColor = currentColor,
                activeColor = currentColor,
                trackBrush = brightnessGradient
            )

            Spacer(Modifier.height(16.dp))

            // RGB and Hex Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ColorComparePill(
                    oldColor = lockedInitialColor,
                    newColor = currentColor,
                    modifier = Modifier
                        .width(48.dp)
                        .height(36.dp)
                )

                // Hex Input
                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Hex",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    HexInput(
                        color = currentColor,
                        onHexChanged = { updateFromColor(it) }
                    )
                }

                // RGB Inputs
                Row(
                    modifier = Modifier.weight(2.5f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RgbInputColumn(
                        label = "R",
                        value = currentColor.red,
                        onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                        modifier = Modifier.weight(1f)
                    )
                    RgbInputColumn(
                        label = "G",
                        value = currentColor.green,
                        onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                        modifier = Modifier.weight(1f)
                    )
                    RgbInputColumn(
                        label = "B",
                        value = currentColor.blue,
                        onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onColorSelected(currentColor) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Done", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                val paddingPx = touchPadding.toPx()
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)

                fun update(offset: Offset) {
                    val relativeX = offset.x - paddingPx
                    val relativeY = offset.y - paddingPx

                    val h = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                    val s = (relativeY / activeHeight).coerceIn(0f, 1f)
                    onHueSatChanged(h, s)
                }

                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(touchPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(rainbowColors)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f))
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val activeWidth = size.width - (paddingPx * 2)
            val activeHeight = size.height - (paddingPx * 2)

            val x = paddingPx + (hue / 360f) * activeWidth
            val y = paddingPx + saturation * activeHeight

            val pointerRadius = 10.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pointerRadius + 1.dp.toPx(),
                center = Offset(x, y + 1.dp.toPx())
            )

            drawCircle(
                color = currentColor.copy(alpha = 1f),
                radius = pointerRadius,
                center = Offset(x, y)
            )

            drawCircle(
                color = Color.White,
                radius = pointerRadius,
                center = Offset(x, y),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
private fun RgbInputColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = (value * 255).roundToInt()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        RgbInput(value = intValue, onValueChange = onValueChange)
    }
}

@Composable
private fun RgbInput(
    value: Int,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        text = value.toString()
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                text = newText
                val intVal = newText.toIntOrNull()
                if (intVal != null) {
                    onValueChange(intVal.coerceIn(0, 255) / 255f)
                }
            }
        },
        textStyle = TextStyle(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp)
    )
}

@Composable
private fun HexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = remember(color) {
        String.format("%06X", (0xFFFFFF and color.toArgb()))
    }
    var text by remember(hexValue) { mutableStateOf(hexValue) }

    LaunchedEffect(color) {
        val currentParsed = try {
            Color(("#$text").toColorInt())
        } catch (_: Exception) {
            null
        }
        if (currentParsed?.toArgb() != color.toArgb()) {
            text = String.format("%06X", (0xFFFFFF and color.toArgb()))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "#",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        BasicTextField(
            value = text,
            onValueChange = { newText ->
                if (newText.length <= 6) {
                    val uppercased = newText.uppercase()
                    if (uppercased.all { it.isDigit() || it in 'A'..'F' }) {
                        text = uppercased
                        if (uppercased.length == 6) {
                            try {
                                val parsedColorInt = "#$uppercased".toColorInt()
                                val newColor = Color(parsedColorInt)
                                onHexChanged(newColor)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                textAlign = TextAlign.Start,
                fontSize = 13.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .padding(start = 2.dp)
                .width(50.dp)
        )
    }
}

@Composable
private fun ColorComparePill(
    oldColor: Color,
    newColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(
            color = oldColor.copy(alpha = 1f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
        drawRect(
            color = newColor.copy(alpha = 1f),
            topLeft = Offset(size.width / 2, 0f),
            size = androidx.compose.ui.geometry.Size(size.width / 2, size.height)
        )
    }
}