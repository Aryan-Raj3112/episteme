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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.aryan.reader.R
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.shared.pdf.AndroidPdfTextDockFontSizes
import com.aryan.reader.shared.pdf.PdfTextDockColorMenuMode as ColorMenuMode
import com.aryan.reader.shared.pdf.PdfTextDockPopup as ActivePopup
import com.aryan.reader.shared.pdf.androidPdfTextDockBuiltInFontPath
import com.aryan.reader.shared.pdf.parsePdfTextDockHexColorOrNull
import com.aryan.reader.shared.pdf.rememberPdfTextDockState
import com.aryan.reader.shared.ui.SharedPdfTextDockColorPickerLabels
import com.aryan.reader.shared.ui.SharedPdfTextDockFontItem
import com.aryan.reader.shared.ui.SharedPdfTextDockFormattingButton
import com.aryan.reader.shared.ui.SharedPdfTextDockPopup
import com.aryan.reader.shared.ui.SharedPdfTextDockFontPanel
import com.aryan.reader.shared.ui.SharedPdfTextDockPopupHost
import com.aryan.reader.shared.ui.SharedPdfTextDockPopupLabels
import com.aryan.reader.shared.ui.SharedPdfTextDockBar
import com.aryan.reader.shared.ui.SharedPdfTextDockBarLabels
import com.aryan.reader.shared.ui.SharedPdfTextDockBarPainters
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TextAnnotationDock(
    currentStyle: SpanStyle,
    textColorPalette: List<Color>,
    onTextColorPaletteChange: (List<Color>) -> Unit,
    backgroundColorPalette: List<Color>,
    onBackgroundColorPaletteChange: (List<Color>) -> Unit,
    onUpdateStyle: (SpanStyle) -> Unit,
    onApplyToSelection: () -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    onInsertTextBox: () -> Unit,
    bottomDockPadding: androidx.compose.ui.unit.Dp = 0.dp,
    popupsBelowBar: Boolean = false,
    customFonts: List<CustomFontEntity> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    currentFontName: String? = null,
    onFontSelected: (String, String?) -> Unit = { _, _ -> }
) {
    val dockState = rememberPdfTextDockState(onPopupStateChange)

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportFont(it) } }
    )

    val fontSizes = AndroidPdfTextDockFontSizes

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        SharedPdfTextDockPopupHost(
            state = dockState,
            bottomDockPadding = bottomDockPadding,
            popupAlignment = if (popupsBelowBar) Alignment.TopCenter else Alignment.BottomCenter,
            popupOffsetY = if (popupsBelowBar) 48.dp + 8.dp else null,
            currentStyle = currentStyle,
            textColorPalette = textColorPalette,
            onTextColorPaletteChange = onTextColorPaletteChange,
            backgroundColorPalette = backgroundColorPalette,
            onBackgroundColorPaletteChange = onBackgroundColorPaletteChange,
            onUpdateStyle = onUpdateStyle,
            onApplyToSelection = onApplyToSelection,
            labels = SharedPdfTextDockPopupLabels(
                fontColor = stringResource(R.string.label_font_color),
                highlightColor = stringResource(R.string.label_highlight_color),
                close = stringResource(R.string.action_close),
                spectrum = SharedPdfTextDockColorPickerLabels(
                    back = stringResource(R.string.action_back), spectrum = stringResource(R.string.label_spectrum),
                    hex = stringResource(R.string.theme_color_hex), red = stringResource(R.string.color_r),
                    green = stringResource(R.string.color_g), blue = stringResource(R.string.color_b), done = stringResource(R.string.action_done),
                ),
            ),
            fontFamilyContent = {
                SharedPdfTextDockFontPanel(
                    presetsLabel = stringResource(R.string.tab_presets), importedLabel = stringResource(R.string.tab_imported),
                    importLabel = stringResource(R.string.action_import), noImportedFontsLabel = stringResource(R.string.msg_no_fonts_imported),
                    onImportClick = { fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf")) },
                    hasImportedFonts = customFonts.isNotEmpty(),
                    presetContent = {
                        LazyColumn {
                            item { FontItem(stringResource(R.string.font_default_system), currentFontName == "Default" || currentFontName == null, FontFamily.Default) {
                                onFontSelected("Default", null); dockState.dismiss()
                            } }
                            items(com.aryan.reader.epubreader.ReaderFont.entries.toTypedArray()) { font ->
                                if (font == com.aryan.reader.epubreader.ReaderFont.ORIGINAL) return@items
                                FontItem(font.displayName, currentFontName == font.displayName,
                                    com.aryan.reader.epubreader.getComposeFontFamily(font, null, LocalContext.current.assets)) {
                                    onFontSelected(font.displayName, androidPdfTextDockBuiltInFontPath(font.displayName)); dockState.dismiss()
                                }
                            }
                        }
                    },
                    importedContent = {
                        LazyColumn { items(customFonts) { font ->
                            val family = remember(font.path) { try { FontFamily(Font(File(font.path))) } catch (_: Exception) { FontFamily.Default } }
                            FontItem(font.displayName, currentFontName == font.displayName, family) { onFontSelected(font.displayName, font.path); dockState.dismiss() }
                        } }
                    },
                )
            },
        )

        val currentDecoration = currentStyle.textDecoration ?: TextDecoration.None
        val isBold = currentStyle.fontWeight == FontWeight.Bold
        val isItalic = currentStyle.fontStyle == FontStyle.Italic
        val isUnderline = currentDecoration.contains(TextDecoration.Underline)
        val isStrike = currentDecoration.contains(TextDecoration.LineThrough)
        SharedPdfTextDockBar(
            fontSize = currentStyle.fontSize.value.toInt(),
            textColor = currentStyle.color.takeIf { it != Color.Unspecified } ?: Color.Black,
            backgroundColor = currentStyle.background.takeUnless { it == Color.Unspecified || it == Color.Transparent } ?: Color.Gray,
            isFontFamilySelected = dockState.popup == ActivePopup.FONT_FAMILY,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            isStrikethrough = isStrike,
            bottomDockPadding = bottomDockPadding,
            labels = SharedPdfTextDockBarLabels(
                selectFontFamily = stringResource(R.string.content_desc_select_font_family),
                selectFontSize = stringResource(R.string.content_desc_select_font_size),
                fontBackground = stringResource(R.string.content_desc_font_background),
                bold = stringResource(R.string.content_desc_bold),
                italic = stringResource(R.string.content_desc_italic),
                underline = stringResource(R.string.content_desc_underline),
                strikethrough = stringResource(R.string.content_desc_strikethrough),
                insertTextBox = stringResource(R.string.content_desc_insert_text_box),
            ),
            painters = SharedPdfTextDockBarPainters(
                fonts = painterResource(R.drawable.fonts),
                background = painterResource(R.drawable.font_background),
                bold = painterResource(R.drawable.format_bold),
                italic = painterResource(R.drawable.format_italic),
                underline = painterResource(R.drawable.format_underlined),
                strikethrough = painterResource(R.drawable.format_strikethrough),
                textBox = painterResource(R.drawable.text_box),
            ),
            onFontFamilyClick = { dockState.togglePopup(ActivePopup.FONT_FAMILY) },
            onFontSizeClick = { dockState.togglePopup(ActivePopup.FONT_SIZE) },
            onTextColorClick = { dockState.showPalettePopup(ActivePopup.COLOR) },
            onBackgroundColorClick = { dockState.showPalettePopup(ActivePopup.BACKGROUND) },
            onBoldClick = {
                onUpdateStyle(currentStyle.copy(fontWeight = if (isBold) FontWeight.Normal else FontWeight.Bold, fontFamily = currentStyle.fontFamily)); onApplyToSelection()
            },
            onItalicClick = {
                onUpdateStyle(currentStyle.copy(fontStyle = if (isItalic) FontStyle.Normal else FontStyle.Italic, fontFamily = currentStyle.fontFamily)); onApplyToSelection()
            },
            onUnderlineClick = {
                val hasStrike = currentDecoration.contains(TextDecoration.LineThrough)
                val next = if (isUnderline) { if (hasStrike) TextDecoration.LineThrough else TextDecoration.None }
                else if (hasStrike) TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)) else TextDecoration.Underline
                onUpdateStyle(currentStyle.copy(textDecoration = next, fontFamily = currentStyle.fontFamily)); onApplyToSelection()
            },
            onStrikethroughClick = {
                val hasUnderline = currentDecoration.contains(TextDecoration.Underline)
                val next = if (isStrike) { if (hasUnderline) TextDecoration.Underline else TextDecoration.None }
                else if (hasUnderline) TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)) else TextDecoration.LineThrough
                onUpdateStyle(currentStyle.copy(textDecoration = next, fontFamily = currentStyle.fontFamily)); onApplyToSelection()
            },
            onInsertTextBox = { Timber.tag("PdfTextBoxDebug").d("Dock: Insert Text Box icon clicked"); onInsertTextBox() },
            textColorIndicator = { color ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-3).dp, Alignment.CenterVertically)) {
                    Text("A", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                    Box(Modifier.width(16.dp).height(2.dp).background(color))
                }
            },
            fontSizePopup = {
                if (dockState.popup == ActivePopup.FONT_SIZE) {
                    DockBubblePopup(onDismissRequest = dockState::dismiss,
                        offsetY = if (popupsBelowBar) 55.dp else (-55).dp,
                        alignment = if (popupsBelowBar) Alignment.BottomCenter else Alignment.TopCenter,
                        focusable = false) {
                        LazyColumn(Modifier.heightIn(max = 200.dp).width(80.dp).background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))) {
                            items(fontSizes) { size ->
                                val selected = currentStyle.fontSize == size
                                Box(Modifier.fillMaxWidth().clickable {
                                    onUpdateStyle(currentStyle.copy(fontSize = size, fontFamily = currentStyle.fontFamily)); onApplyToSelection(); dockState.dismiss()
                                }.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else Color.Transparent).padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(size.value.toInt().toString(), style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White)
                                }
                            }
                        }
                    }
                }
            },
        )
        // Drag-handle affordance: straddles the bar's outer edge (top when
        // bottom-docked/floating, bottom when top-docked) so the bar reads as
        // draggable. Touch-transparent; the dock container owns drag gestures.
        Box(
            modifier = Modifier
                .align(if (popupsBelowBar) Alignment.BottomCenter else Alignment.TopCenter)
                .offset(y = if (popupsBelowBar) 2.dp else (-2).dp)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun FontItem(
    name: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    SharedPdfTextDockFontItem(name, isSelected, fontFamily, stringResource(R.string.content_desc_selected), onClick)
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
    SharedPdfTextDockPopup(onDismissRequest, alignment, offsetPx, focusable, content)
}
