package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
