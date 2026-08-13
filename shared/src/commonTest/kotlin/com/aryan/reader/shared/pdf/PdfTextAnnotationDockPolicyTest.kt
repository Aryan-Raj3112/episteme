package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

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
