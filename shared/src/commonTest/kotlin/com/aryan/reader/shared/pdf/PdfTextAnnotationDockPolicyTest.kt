package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.DockLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfTextAnnotationDockPolicyTest {
    @Test
    fun colorConversionMatchesAndroidDockExpectations() {
        assertEquals(PdfTextDockHsv(0f, 1f, 1f), pdfTextDockColorToHsv(Color.Red))
        assertEquals(PdfTextDockHsv(120f, 1f, 1f), pdfTextDockColorToHsv(Color.Green))
        assertEquals(PdfTextDockHsv(240f, 1f, 1f), pdfTextDockColorToHsv(Color.Blue))
        assertEquals("00FF0A", pdfTextDockRgbHex(Color(0xFF00FF0A)))
        assertEquals(Color(0xFF00FFFF), pdfTextDockHsvColor(180f, 1f, 1f))
    }

    @Test
    fun preservesAndroidFontChoicesAndPopupToggle() {
        assertEquals(listOf(12f, 14f, 16f, 18f, 20f, 24f, 30f), AndroidPdfTextDockFontSizes.map { it.value })
        assertEquals("asset:fonts/roboto_mono.ttf", androidPdfTextDockBuiltInFontPath("Roboto Mono"))
        assertNull(androidPdfTextDockBuiltInFontPath("Unknown"))
        assertEquals(PdfTextDockPopup.COLOR, togglePdfTextDockPopup(PdfTextDockPopup.NONE, PdfTextDockPopup.COLOR))
        assertEquals(PdfTextDockPopup.NONE, togglePdfTextDockPopup(PdfTextDockPopup.COLOR, PdfTextDockPopup.COLOR))
    }

    @Test
    fun importedPdfFontsOnlyExposeResolvedLiveFamilies() {
        val fonts = listOf(
            CustomFontItem("deleted", "Deleted", "deleted.ttf", "ttf", "/deleted.ttf", 1L, isDeleted = true),
            CustomFontItem("missing", "Missing", "missing.ttf", "ttf", "/missing.ttf", 2L),
            CustomFontItem("zeta", "Zeta", "zeta.ttf", "ttf", "/zeta.ttf", 3L),
            CustomFontItem("alpha", "Alpha", "alpha.ttf", "ttf", "/alpha.ttf", 4L),
        )

        val available = availableSharedPdfCustomFonts(
            customFonts = fonts,
            resolvedFamilyKeys = setOf("/zeta.ttf", "alpha"),
        )

        assertEquals(listOf("alpha", "zeta"), available.map { it.id })
    }

    @Test
    fun paletteReplacementChangesOnlyAValidSlot() {
        val palette = listOf(Color.Black, Color.White)
        assertEquals(listOf(Color.Red, Color.White), replacePdfTextDockPaletteColor(palette, 0, Color.Red))
        assertSame(palette, replacePdfTextDockPaletteColor(palette, 3, Color.Red))
    }

    @Test
    fun hexParsingMatchesAndroidOpaqueRgbAndArgbInputs() {
        assertEquals(Color(0xFF12AB34), parsePdfTextDockHexColorOrNull("12ab34"))
        assertEquals(Color(0x8012AB34), parsePdfTextDockHexColorOrNull("#8012AB34"))
        assertNull(parsePdfTextDockHexColorOrNull("xyz"))
        assertNull(parsePdfTextDockHexColorOrNull("12345"))
    }

    @Test
    fun textDockShowsInTextEditModeWithoutRequiringIme() {
        // Physical / floating / split keyboards report no IME height, and focus
        // races deliver insets a frame late: the toolbar must not depend on them.
        assertTrue(shouldShowPdfTextDock(isEditMode = true, isTextToolSelected = true))
        assertFalse(shouldShowPdfTextDock(isEditMode = false, isTextToolSelected = true))
        assertFalse(shouldShowPdfTextDock(isEditMode = true, isTextToolSelected = false))
        assertFalse(shouldShowPdfTextDock(isEditMode = false, isTextToolSelected = false))
    }

    @Test
    fun textDockRestingPaddingClearsPenDockOnlyWhenKeyboardClosed() {        // IME open: inset padding positions the dock, extra must be zero or the
        // bar floats above the keyboard on phones and tablets.
        assertEquals(0.dp, pdfTextDockRestingBottomPadding(true, DockLocation.BOTTOM, false))
        assertEquals(0.dp, pdfTextDockRestingBottomPadding(true, DockLocation.FLOATING, false))
        assertEquals(80.dp, pdfTextDockRestingBottomPadding(false, DockLocation.BOTTOM, false))
        assertEquals(16.dp, pdfTextDockRestingBottomPadding(false, DockLocation.BOTTOM, true))
        assertEquals(16.dp, pdfTextDockRestingBottomPadding(false, DockLocation.TOP, false))
        assertEquals(16.dp, pdfTextDockRestingBottomPadding(false, DockLocation.FLOATING, false))
    }

    @Test
    fun textDockPopupsOpenBelowBarOnlyWhenSettledAtTop() {
        assertTrue(isPdfTextDockTopAnchored(DockLocation.TOP, isDragging = false))
        assertFalse(isPdfTextDockTopAnchored(DockLocation.TOP, isDragging = true))
        assertFalse(isPdfTextDockTopAnchored(DockLocation.BOTTOM, isDragging = false))
        assertFalse(isPdfTextDockTopAnchored(DockLocation.FLOATING, isDragging = false))
    }

    @Test
    fun textDockKeyboardLiftOnlyPushesFloatingDockClearOfKeyboard() {
        assertEquals(0f, pdfTextDockKeyboardLiftPx(false, true, 2200f, 1500f))
        assertEquals(0f, pdfTextDockKeyboardLiftPx(true, false, 2200f, 1500f))
        assertEquals(0f, pdfTextDockKeyboardLiftPx(true, true, 1400f, 1500f))
        assertEquals(700f, pdfTextDockKeyboardLiftPx(true, true, 2200f, 1500f))
    }

    @Test
    fun dockStatePreservesAndroidPopupAndColorEditorTransitions() {
        val state = PdfTextDockState(PdfTextDockPopup.NONE, PdfTextDockColorMenuMode.PALETTE, -1)
        state.togglePopup(PdfTextDockPopup.COLOR)
        assertEquals(PdfTextDockPopup.COLOR, state.popup)
        state.showSpectrum(3)
        assertEquals(PdfTextDockColorMenuMode.SPECTRUM, state.colorMenuMode)
        assertEquals(3, state.paletteSlotIndex)
        state.showPalette()
        assertEquals(PdfTextDockColorMenuMode.PALETTE, state.colorMenuMode)
        state.dismiss()
        state.resetTransientColorState()
        assertEquals(-1, state.paletteSlotIndex)
        state.showPalettePopup(PdfTextDockPopup.BACKGROUND)
        assertEquals(PdfTextDockPopup.BACKGROUND, state.popup)
    }
}
