package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.PdfTextDockColorMenuMode
import com.aryan.reader.shared.pdf.PdfTextDockPopup
import com.aryan.reader.shared.pdf.PdfTextDockState
import com.aryan.reader.shared.pdf.replacePdfTextDockPaletteColor

data class SharedPdfTextDockPopupLabels(
    val fontColor: String,
    val highlightColor: String,
    val close: String,
    val spectrum: SharedPdfTextDockColorPickerLabels,
)

@Composable
fun SharedPdfTextDockPopupHost(
    state: PdfTextDockState,
    bottomDockPadding: Dp,
    currentStyle: SpanStyle,
    textColorPalette: List<Color>,
    onTextColorPaletteChange: (List<Color>) -> Unit,
    backgroundColorPalette: List<Color>,
    onBackgroundColorPaletteChange: (List<Color>) -> Unit,
    onUpdateStyle: (SpanStyle) -> Unit,
    onApplyToSelection: () -> Unit,
    labels: SharedPdfTextDockPopupLabels,
    fontFamilyContent: @Composable () -> Unit,
    // Bottom-docked bars anchor popups above the bar; a top-docked bar passes
    // TopCenter with a positive offset so popups open below it instead.
    popupAlignment: Alignment = Alignment.BottomCenter,
    popupOffsetY: Dp? = null,
) {
    if (state.popup == PdfTextDockPopup.NONE || state.popup == PdfTextDockPopup.FONT_SIZE) return
    // Keep popups non-focusable on mobile: a focusable popup window steals
    // focus from the hidden rich-text / text-box field, dismissing the IME and
    // dropping the dock on phones and tablets. Inner controls (palette taps,
    // spectrum sliders, font rows) still receive touch; the hex field can still
    // gain focus on demand without a focusable window.
    SharedPdfTextDockPopupDp(
        onDismissRequest = state::dismiss,
        alignment = popupAlignment,
        offsetY = popupOffsetY ?: -(bottomDockPadding + 48.dp + 8.dp),
        focusable = false,
    ) {
        when (state.popup) {
            PdfTextDockPopup.FONT_FAMILY -> fontFamilyContent()
            PdfTextDockPopup.COLOR -> SharedPdfTextDockColorPopup(
                state, labels.fontColor, labels.close, labels.spectrum,
                currentStyle.color.takeIf { it != Color.Unspecified } ?: Color.Black,
                textColorPalette, false,
                onPaletteChange = onTextColorPaletteChange,
                onSelected = { color -> onUpdateStyle(currentStyle.copy(color = color, fontFamily = currentStyle.fontFamily)); onApplyToSelection() },
            )
            PdfTextDockPopup.BACKGROUND -> SharedPdfTextDockColorPopup(
                state, labels.highlightColor, labels.close, labels.spectrum,
                currentStyle.background.takeUnless { it == Color.Unspecified } ?: Color.Transparent,
                backgroundColorPalette, true,
                onPaletteChange = onBackgroundColorPaletteChange,
                onSelected = { color -> onUpdateStyle(currentStyle.copy(background = color, fontFamily = currentStyle.fontFamily)); onApplyToSelection() },
            )
            else -> Unit
        }
    }
}

@Composable
private fun SharedPdfTextDockColorPopup(
    state: PdfTextDockState,
    title: String,
    close: String,
    spectrumLabels: SharedPdfTextDockColorPickerLabels,
    currentColor: Color,
    palette: List<Color>,
    showTransparent: Boolean,
    onPaletteChange: (List<Color>) -> Unit,
    onSelected: (Color) -> Unit,
) {
    if (state.colorMenuMode == PdfTextDockColorMenuMode.PALETTE) {
        SharedPdfTextDockPalettePicker(title, close, currentColor, palette, showTransparent,
            onColorSelected = { onSelected(it); state.dismiss() },
            onShowColorPicker = state::showSpectrum,
            onDismiss = state::dismiss)
    } else {
        SharedPdfTextDockColorPicker(
            initialColor = palette.getOrElse(state.paletteSlotIndex) { Color.Black },
            labels = spectrumLabels,
            onBack = state::showPalette,
            onColorSelected = { color ->
                val updated = replacePdfTextDockPaletteColor(palette, state.paletteSlotIndex, color)
                if (updated !== palette) { onPaletteChange(updated); onSelected(color) }
                state.showPalette()
            },
        )
    }
}
