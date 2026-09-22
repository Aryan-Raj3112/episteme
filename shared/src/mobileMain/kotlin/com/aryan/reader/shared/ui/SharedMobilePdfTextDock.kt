package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // Android parity (TextAnnotationDock): drag/long-press detection owned by
    // the caller and applied to the bar root only, so surrounding empty
    // padding stays touch-transparent for controls beneath.
    dragGestureModifier: Modifier = Modifier,
    // A top-docked bar opens popups below itself instead of above.
    popupsBelowBar: Boolean = false,
    // Android parity: hides the rich-text cursor while a popup is open.
    onPopupStateChange: (Boolean) -> Unit = {},
    // Second-row paragraph controls (lists + alignment). Null hides the row.
    paragraphState: RichParagraphUiState? = null,
    onNumberedListClick: () -> Unit = {},
    onBulletedListClick: () -> Unit = {},
    onAlignmentSelected: (SharedPdfRichTextAlign) -> Unit = {},
) {
    val state = rememberPdfTextDockState(onPopupStateChange)
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

    Box(modifier.fillMaxWidth().then(dragGestureModifier), contentAlignment = Alignment.BottomCenter) {
        SharedPdfTextDockPopupHost(
            state = state, bottomDockPadding = 0.dp, currentStyle = spanStyle,
            popupAlignment = if (popupsBelowBar) Alignment.TopCenter else Alignment.BottomCenter,
            popupOffsetY = if (popupsBelowBar) 48.dp + 8.dp else null,
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
                readerString("content_desc_numbered_list", "Numbered list"), readerString("content_desc_bulleted_list", "Bulleted list"),
                readerString("content_desc_text_alignment", "Text alignment"),
                readerString("content_desc_align_left", "Align left"), readerString("content_desc_align_center", "Align center"),
                readerString("content_desc_align_right", "Align right"),
            ),
            painters = SharedPdfTextDockBarPainters(
                painterResource(Res.drawable.fonts), painterResource(Res.drawable.font_background), painterResource(Res.drawable.format_bold),
                painterResource(Res.drawable.format_italic), painterResource(Res.drawable.format_underlined), painterResource(Res.drawable.strikethrough), painterResource(Res.drawable.text_box),
                painterResource(Res.drawable.format_list_numbered), painterResource(Res.drawable.format_list_bulleted),
                painterResource(Res.drawable.format_align_left), painterResource(Res.drawable.format_align_center), painterResource(Res.drawable.format_align_right),
            ),
            onFontFamilyClick = { state.togglePopup(PdfTextDockPopup.FONT_FAMILY) }, onFontSizeClick = { state.togglePopup(PdfTextDockPopup.FONT_SIZE) },
            onTextColorClick = { state.showPalettePopup(PdfTextDockPopup.COLOR) }, onBackgroundColorClick = { state.showPalettePopup(PdfTextDockPopup.BACKGROUND) },
            onBoldClick = { update(spanStyle.copy(fontWeight = if (style.isBold) FontWeight.Normal else FontWeight.Bold)) },
            onItalicClick = { update(spanStyle.copy(fontStyle = if (style.isItalic) FontStyle.Normal else FontStyle.Italic)) },
            onUnderlineClick = { update(spanStyle.copy(textDecoration = sharedPdfDockDecoration(!style.isUnderline, style.isStrikeThrough))) },
            onStrikethroughClick = { update(spanStyle.copy(textDecoration = sharedPdfDockDecoration(style.isUnderline, !style.isStrikeThrough))) },
            onInsertTextBox = onInsertTextBox,
            paragraphControls = paragraphState?.let { paragraph ->
                SharedPdfTextDockParagraphControls(
                    state = paragraph,
                    onNumberedListClick = onNumberedListClick,
                    onBulletedListClick = onBulletedListClick,
                    onAlignmentClick = { state.togglePopup(PdfTextDockPopup.ALIGNMENT) },
                    onAlignmentSelected = { onAlignmentSelected(it); state.dismiss() },
                )
            },
            alignmentPopup = {
                if (state.popup == PdfTextDockPopup.ALIGNMENT && paragraphState != null) SharedPdfTextDockPopupDp(
                    state::dismiss,
                    if (popupsBelowBar) Alignment.BottomCenter else Alignment.TopCenter,
                    if (popupsBelowBar) 55.dp else (-55).dp,
                ) {
                    SharedPdfTextDockAlignmentPopupContent(
                        selected = paragraphState.alignment,
                        alignLeftPainter = painterResource(Res.drawable.format_align_left),
                        alignCenterPainter = painterResource(Res.drawable.format_align_center),
                        alignRightPainter = painterResource(Res.drawable.format_align_right),
                        alignLeftDescription = readerString("content_desc_align_left", "Align left"),
                        alignCenterDescription = readerString("content_desc_align_center", "Align center"),
                        alignRightDescription = readerString("content_desc_align_right", "Align right"),
                        onSelected = { onAlignmentSelected(it); state.dismiss() },
                    )
                }
            },
            textColorIndicator = { color -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-3).dp)) {
                Text("A", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(16.dp).height(2.dp).background(color))
            } },
            fontSizePopup = {
                if (state.popup == PdfTextDockPopup.FONT_SIZE) SharedPdfTextDockPopupDp(
                    state::dismiss,
                    if (popupsBelowBar) Alignment.BottomCenter else Alignment.TopCenter,
                    if (popupsBelowBar) 55.dp else (-55).dp,
                ) {
                    LazyColumn(Modifier.heightIn(max = 200.dp).width(80.dp).background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                        items(AndroidPdfTextDockFontSizes) { size -> SharedPdfTextDockFontSizeRow(size.value.toInt(), style.fontSize == size.value) {
                            onStyleChange(style.copy(fontSize = size.value)); state.dismiss()
                        } }
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

private fun sharedPdfDockDecoration(underline: Boolean, strike: Boolean): TextDecoration = when {
    underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strike -> TextDecoration.LineThrough
    else -> TextDecoration.None
}
