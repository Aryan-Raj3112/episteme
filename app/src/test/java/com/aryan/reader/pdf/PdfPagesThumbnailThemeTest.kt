package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.pdfThumbnailNeedsBakedPreserve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPagesThumbnailThemeTest {

    private val noTheme = ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false)
    private val reverse = ReaderTheme("reverse", "Reverse", Color.Black, Color.White, true)
    private val sepia = ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false)

    @Test
    fun `no_theme tiles stay white with no filter regardless of persisted reverse mode`() {
        // Regression: the drawer used to invert whenever the persisted mode was RGB,
        // so no_theme appeared reversed when the phone was in dark mode.
        assertEquals(Color.White, pdfPagesTileBackground(noTheme))
        assertNull(pdfPagesTileColorFilter(noTheme, PdfReverseColorMode.RGB))
        assertNull(pdfPagesTileColorFilter(noTheme, PdfReverseColorMode.LIGHTNESS))
        assertEquals(
            PdfReverseColorMode.RGB,
            pdfPagesEffectiveReverseMode("no_theme", PdfReverseColorMode.LIGHTNESS)
        )
    }

    @Test
    fun `reverse theme follows effective mode like main pages`() {
        assertEquals(Color.Black, pdfPagesTileBackground(reverse))
        assertNotNull(pdfPagesTileColorFilter(reverse, PdfReverseColorMode.RGB))
        assertNull(pdfPagesTileColorFilter(reverse, PdfReverseColorMode.LIGHTNESS))
    }

    @Test
    fun `selected pdf theme tints tiles`() {
        assertEquals(Color(0xFFFBF0D9), pdfPagesTileBackground(sepia))
        assertNotNull(pdfPagesTileColorFilter(sepia, PdfReverseColorMode.RGB))
    }

    @Test
    fun `preserve images never bakes a negative for non-reverse themes`() {
        // Regression for issue #501 follow-up: with "preserve image colors" on,
        // pages containing images baked an RGB negative and showed as black
        // tiles on white themes. Only reverse+RGB may bake.
        val white = ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false)
        assertFalse(
            pdfThumbnailNeedsBakedPreserve("no_theme", PdfReverseColorMode.RGB, preserveImageColors = true, hasImageRects = true)
        )
        assertFalse(
            pdfThumbnailNeedsBakedPreserve(white.id, PdfReverseColorMode.RGB, preserveImageColors = true, hasImageRects = true)
        )
        assertFalse(
            pdfThumbnailNeedsBakedPreserve("sepia", PdfReverseColorMode.RGB, preserveImageColors = true, hasImageRects = true)
        )
        assertTrue(
            pdfThumbnailNeedsBakedPreserve("reverse", PdfReverseColorMode.RGB, preserveImageColors = true, hasImageRects = true)
        )
    }

    @Test
    fun `display index maps to pdf index and blank pages map to null`() {
        // Drawer rows are display pages: inserted blank pages shift PDF indices.
        val layout = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage("blank-1", 595, 842),
            VirtualPage.PdfPage(1),
        )
        assertEquals(0, pdfPageIndexForDisplayPage(layout, 0))
        assertNull(pdfPageIndexForDisplayPage(layout, 1))
        assertEquals(1, pdfPageIndexForDisplayPage(layout, 2))
        assertNull(pdfPageIndexForDisplayPage(layout, 3))
        assertNull(pdfPageIndexForDisplayPage(layout, -1))
        // No layout provided (legacy): indices pass through.
        assertEquals(499, pdfPageIndexForDisplayPage(emptyList(), 499))
        assertNull(pdfPageIndexForDisplayPage(emptyList(), -1))
    }
}
