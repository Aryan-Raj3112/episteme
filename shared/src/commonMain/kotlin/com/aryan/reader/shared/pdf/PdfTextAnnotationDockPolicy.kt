package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.*
import com.aryan.reader.shared.DockLocation

enum class PdfTextDockColorMenuMode { PALETTE, SPECTRUM }

enum class PdfTextDockPopup { NONE, FONT_SIZE, FONT_FAMILY, COLOR, BACKGROUND }

val AndroidPdfTextDockFontSizes: List<TextUnit> = listOf(12.sp, 14.sp, 16.sp, 18.sp, 20.sp, 24.sp, 30.sp)

fun androidPdfTextDockBuiltInFontPath(fontName: String): String? = when (fontName) {
    "Merriweather" -> "asset:fonts/merriweather.ttf"
    "Lato" -> "asset:fonts/lato.ttf"
    "Lora" -> "asset:fonts/lora.ttf"
    "Roboto Mono" -> "asset:fonts/roboto_mono.ttf"
    "Lexend" -> "asset:fonts/lexend.ttf"
    else -> null
}

fun togglePdfTextDockPopup(current: PdfTextDockPopup, requested: PdfTextDockPopup): PdfTextDockPopup =
    if (current == requested) PdfTextDockPopup.NONE else requested

/**
 * Whether the PDF text formatting dock should be composed.
 *
 * Deliberately NOT gated on IME visibility: physical keyboards, floating /
 * split tablet keyboards, and focus races all report an IME height of 0 while
 * the user still needs the toolbar. The dock anchors to
 * max(IME, navigation bars) when the keyboard is open and falls back to the
 * navigation bars plus [pdfTextDockRestingBottomPadding] when closed.
 */
fun shouldShowPdfTextDock(
    isEditMode: Boolean,
    isTextToolSelected: Boolean,
): Boolean = isEditMode && isTextToolSelected

/**
 * Whether the draggable text dock is settled at the top edge (popups must open
 * below the bar instead of above it). While dragging the bar floats under the
 * finger, so popups keep their default above-bar placement.
 */
fun isPdfTextDockTopAnchored(
    dockLocation: DockLocation,
    isDragging: Boolean,
): Boolean = dockLocation == DockLocation.TOP && !isDragging

/**
 * Lift (px) applied to a floating or in-drag text dock so an open keyboard
 * never covers it, mirroring the bottom-docked behavior of sitting above the
 * keyboard. Zero when the keyboard is closed, when the dock is sticky (inset
 * padding or screen edges already position it), or when the dock already
 * clears the keyboard top.
 */
fun pdfTextDockKeyboardLiftPx(
    isImeVisible: Boolean,
    isFloating: Boolean,
    dockBottomPx: Float,
    keyboardTopPx: Float,
): Float = if (!isImeVisible || !isFloating) {
    0f
} else {
    (dockBottomPx - keyboardTopPx).coerceAtLeast(0f)
}

/**
 * Extra bottom offset applied only while the keyboard is closed so the text
 * dock clears the pen dock (80.dp when the pen dock is stuck to the bottom)
 * or floats minimally (16.dp) otherwise. While the IME is visible the inset
 * padding already positions the dock, so this must be 0.dp to avoid floating
 * above the keyboard on phones and tablets.
 */
fun pdfTextDockRestingBottomPadding(
    isImeVisible: Boolean,
    dockLocation: DockLocation,
    isDockMinimized: Boolean,
): Dp = if (isImeVisible) {
    0.dp
} else if (dockLocation == DockLocation.BOTTOM && !isDockMinimized) {
    80.dp
} else {
    16.dp
}

fun replacePdfTextDockPaletteColor(palette: List<Color>, slot: Int, color: Color): List<Color> =
    if (slot !in palette.indices) palette else palette.toMutableList().apply { this[slot] = color }

fun parsePdfTextDockHexColorOrNull(value: String): Color? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    val parsed = normalized.toULongOrNull(16) ?: return null
    val argb = if (normalized.length == 6) parsed or 0xFF000000u else parsed
    return Color(argb.toInt())
}

data class PdfTextDockHsv(val hue: Float, val saturation: Float, val value: Float)

fun pdfTextDockColorToHsv(color: Color): PdfTextDockHsv {
    val argb = color.toArgb()
    val red = ((argb shr 16) and 0xFF) / 255f
    val green = ((argb shr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val saturation = if (maximum == 0f) 0f else delta / maximum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return PdfTextDockHsv(hue, saturation, maximum)
}

fun pdfTextDockHsvColor(hue: Float, saturation: Float, value: Float): Color =
    Color(Color.hsv(hue, saturation, value).toArgb())

@Stable
class PdfTextDockState internal constructor(
    popup: PdfTextDockPopup,
    colorMenuMode: PdfTextDockColorMenuMode,
    paletteSlotIndex: Int,
) {
    var popup by mutableStateOf(popup)
        private set
    var colorMenuMode by mutableStateOf(colorMenuMode)
        private set
    var paletteSlotIndex by mutableIntStateOf(paletteSlotIndex)
        private set

    fun togglePopup(requested: PdfTextDockPopup) {
        popup = togglePdfTextDockPopup(popup, requested)
    }

    fun showPalettePopup(requested: PdfTextDockPopup) {
        colorMenuMode = PdfTextDockColorMenuMode.PALETTE
        togglePopup(requested)
    }

    fun showSpectrum(slotIndex: Int) {
        paletteSlotIndex = slotIndex
        colorMenuMode = PdfTextDockColorMenuMode.SPECTRUM
    }

    fun showPalette() {
        colorMenuMode = PdfTextDockColorMenuMode.PALETTE
    }

    fun dismiss() {
        popup = PdfTextDockPopup.NONE
    }

    internal fun resetTransientColorState() {
        colorMenuMode = PdfTextDockColorMenuMode.PALETTE
        paletteSlotIndex = -1
    }
}

@Composable
fun rememberPdfTextDockState(
    onPopupStateChange: (Boolean) -> Unit,
): PdfTextDockState {
    val state = remember { PdfTextDockState(PdfTextDockPopup.NONE, PdfTextDockColorMenuMode.PALETTE, -1) }
    LaunchedEffect(state.popup) {
        onPopupStateChange(state.popup != PdfTextDockPopup.NONE)
        if (state.popup == PdfTextDockPopup.NONE) state.resetTransientColorState()
    }
    return state
}

fun pdfTextDockRgbHex(color: Color): String =
    (color.toArgb() and 0xFFFFFF).toUInt().toString(16).uppercase().padStart(6, '0')
