package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.aryan.reader.shared.generated.resources.*
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.pdf.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun SharedMobilePdfTextDock(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    onInsertTextBox: () -> Unit,
    modifier: Modifier = Modifier,
    customFonts: List<CustomFontItem> = emptyList(),
    customFontFamilies: Map<String, FontFamily> = emptyMap(),
    onImportFont: () -> Unit = {},
) {
    val state = rememberPdfTextDockState {}
    var textPalette by remember { mutableStateOf(SharedPdfTextAnnotationDefaults.textColorPalette.map(::Color)) }
    var backgroundPalette by remember { mutableStateOf(SharedPdfTextAnnotationDefaults.backgroundColorPalette.map(::Color)) }
    val spanStyle = style.toSharedPdfRichSpanStyle()
    fun update(next: SpanStyle) {
        val decoration = next.textDecoration ?: TextDecoration.None
        onStyleChange(style.copy(
            colorArgb = next.color.takeIf { it != Color.Unspecified }?.toArgb() ?: style.colorArgb,
            backgroundColorArgb = next.background.takeIf { it != Color.Unspecified }?.toArgb() ?: style.backgroundColorArgb,
            fontSize = next.fontSize.takeIf { it.isSp }?.value ?: style.fontSize,
            isBold = next.fontWeight == FontWeight.Bold,
            isItalic = next.fontStyle == FontStyle.Italic,
            isUnderline = decoration.contains(TextDecoration.Underline),
            isStrikeThrough = decoration.contains(TextDecoration.LineThrough),
        ))
    }

    val resolvedCustomFontFamilies = remember(customFonts, customFontFamilies) {
        customFontFamilies.ifEmpty { loadSharedPdfCustomFontFamilies(customFonts) }
    }
    val availableCustomFonts = remember(customFonts, resolvedCustomFontFamilies) {
        availableSharedPdfCustomFonts(customFonts, resolvedCustomFontFamilies.keys)
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        SharedPdfTextDockPopupHost(
            state = state, bottomDockPadding = 0.dp, currentStyle = spanStyle,
            textColorPalette = textPalette, onTextColorPaletteChange = { textPalette = it },
            backgroundColorPalette = backgroundPalette, onBackgroundColorPaletteChange = { backgroundPalette = it },
            onUpdateStyle = ::update, onApplyToSelection = {},
            labels = SharedPdfTextDockPopupLabels(
                fontColor = readerString("label_font_color", "Font color"),
                highlightColor = readerString("label_highlight_color", "Highlight color"),
                close = readerString("action_close", "Close"),
                spectrum = SharedPdfTextDockColorPickerLabels(
                    readerString("action_back", "Back"), readerString("label_spectrum", "Spectrum"),
                    readerString("theme_color_hex", "HEX"), "R", "G", "B", readerString("action_done", "Done"),
                ),
            ),
            fontFamilyContent = {
                SharedPdfTextDockFontPanel(
                    presetsLabel = readerString("tab_presets", "Presets"), importedLabel = readerString("tab_imported", "Imported"),
                    importLabel = readerString("action_import", "Import"), noImportedFontsLabel = readerString("msg_no_fonts_imported", "No fonts imported"),
                    onImportClick = onImportFont, hasImportedFonts = availableCustomFonts.isNotEmpty(),
                    presetContent = {
                        LazyColumn { items(SharedPdfTextAnnotationDefaults.fontPresets) { preset ->
                            SharedPdfTextDockFontItem(preset.name, style.fontName == preset.name || preset.name == "Default" && style.fontName == null,
                                sharedPdfFontFamily(preset.fontPath) ?: androidx.compose.ui.text.font.FontFamily.Default,
                                readerString("content_desc_selected", "Selected")) {
                                onStyleChange(style.copy(fontName = preset.name, fontPath = preset.fontPath)); state.dismiss()
                            }
                        } }
                    },
                    importedContent = {
                        availableCustomFonts.forEach { font ->
                            val family = resolvedCustomFontFamilies[font.path]
                                ?: resolvedCustomFontFamilies[font.displayName]
                                ?: FontFamily.Default
                            SharedPdfTextDockFontItem(
                                name = font.displayName,
                                isSelected = style.fontPath == font.path ||
                                    (style.fontPath == null && style.fontName == font.displayName),
                                fontFamily = family,
                                selectedContentDescription = readerString("content_desc_selected", "Selected"),
                                onClick = {
                                    onStyleChange(style.copy(fontName = font.displayName, fontPath = font.path))
                                    state.dismiss()
                                },
                            )
                        }
                    },
                )
            },
        )
        val decoration = spanStyle.textDecoration ?: TextDecoration.None
        SharedPdfTextDockBar(
            fontSize = style.fontSize.toInt(), textColor = Color(style.colorArgb), backgroundColor = Color(style.backgroundColorArgb),
            isFontFamilySelected = state.popup == PdfTextDockPopup.FONT_FAMILY,
            isBold = style.isBold, isItalic = style.isItalic, isUnderline = style.isUnderline, isStrikethrough = style.isStrikeThrough,
            bottomDockPadding = 0.dp,
            labels = SharedPdfTextDockBarLabels(
                readerString("content_desc_select_font_family", "Select font family"), readerString("content_desc_select_font_size", "Select font size"),
                readerString("content_desc_font_background", "Font background"), readerString("content_desc_bold", "Bold"),
                readerString("content_desc_italic", "Italic"), readerString("content_desc_underline", "Underline"),
                readerString("content_desc_strikethrough", "Strikethrough"), readerString("content_desc_insert_text_box", "Insert text box"),
            ),
            painters = SharedPdfTextDockBarPainters(
                painterResource(Res.drawable.fonts), painterResource(Res.drawable.font_background), painterResource(Res.drawable.format_bold),
                painterResource(Res.drawable.format_italic), painterResource(Res.drawable.format_underlined), painterResource(Res.drawable.strikethrough), painterResource(Res.drawable.text_box),
            ),
            onFontFamilyClick = { state.togglePopup(PdfTextDockPopup.FONT_FAMILY) }, onFontSizeClick = { state.togglePopup(PdfTextDockPopup.FONT_SIZE) },
            onTextColorClick = { state.showPalettePopup(PdfTextDockPopup.COLOR) }, onBackgroundColorClick = { state.showPalettePopup(PdfTextDockPopup.BACKGROUND) },
            onBoldClick = { update(spanStyle.copy(fontWeight = if (style.isBold) FontWeight.Normal else FontWeight.Bold)) },
            onItalicClick = { update(spanStyle.copy(fontStyle = if (style.isItalic) FontStyle.Normal else FontStyle.Italic)) },
            onUnderlineClick = { update(spanStyle.copy(textDecoration = sharedPdfDockDecoration(!style.isUnderline, style.isStrikeThrough))) },
            onStrikethroughClick = { update(spanStyle.copy(textDecoration = sharedPdfDockDecoration(style.isUnderline, !style.isStrikeThrough))) },
            onInsertTextBox = onInsertTextBox,
            textColorIndicator = { color -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-3).dp)) {
                Text("A", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(16.dp).height(2.dp).background(color))
            } },
            fontSizePopup = {
                if (state.popup == PdfTextDockPopup.FONT_SIZE) SharedPdfTextDockPopupDp(state::dismiss, Alignment.TopCenter, (-55).dp) {
                    LazyColumn(Modifier.heightIn(max = 200.dp).width(80.dp).background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                        items(AndroidPdfTextDockFontSizes) { size -> SharedPdfTextDockFontSizeRow(size.value.toInt(), style.fontSize == size.value) {
                            onStyleChange(style.copy(fontSize = size.value)); state.dismiss()
                        } }
                    }
                }
            },
        )
    }
}

private fun sharedPdfDockDecoration(underline: Boolean, strike: Boolean): TextDecoration = when {
    underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strike -> TextDecoration.LineThrough
    else -> TextDecoration.None
}
