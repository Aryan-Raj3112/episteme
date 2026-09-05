package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedMobilePdfThumbnailThemeAndroidTest {
    private val noTheme = ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false)
    private val reverse = ReaderTheme("reverse", "Reverse", Color.Black, Color.White, true)
    private val sepia = ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false)
    private val dark = ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true)

    @Test
    fun `thumbnail background matches selected page background`() {
        assertEquals(Color.White, sharedMobilePdfPageBackground(noTheme))
        assertEquals(Color.Black, sharedMobilePdfPageBackground(reverse))
        assertEquals(Color(0xFFFBF0D9), sharedMobilePdfPageBackground(sepia))
        assertEquals(Color(0xFF121212), sharedMobilePdfPageBackground(dark))
    }

    @Test
    fun `thumbnail applies selected theme filter unless bitmap already baked`() {
        // no_theme never tints, regardless of phone dark mode.
        assertNull(sharedMobilePdfThumbnailColorFilter(noTheme))
        assertNull(
            sharedMobilePdfThumbnailColorFilter(
                noTheme,
                PdfReverseColorMode.RGB,
                PdfReverseColorMode.RGB
            )
        )
        // Themed pages tint via filter when the bitmap is original.
        assertNotNull(sharedMobilePdfThumbnailColorFilter(sepia))
        assertNotNull(sharedMobilePdfThumbnailColorFilter(dark))
        // Reverse RGB is applied via filter like full pages (not baked).
        assertNotNull(
            sharedMobilePdfThumbnailColorFilter(reverse, PdfReverseColorMode.RGB)
        )
        // Nonlinear Okular modes are baked, so no second filter.
        assertNull(
            sharedMobilePdfThumbnailColorFilter(reverse, PdfReverseColorMode.LIGHTNESS)
        )
        // Baked bitmaps (e.g. preserved image rects) must not double-apply.
        assertNull(
            sharedMobilePdfThumbnailColorFilter(
                reverse,
                PdfReverseColorMode.RGB,
                PdfReverseColorMode.RGB
            )
        )
        assertNull(
            sharedMobilePdfThumbnailColorFilter(
                sepia,
                PdfReverseColorMode.RGB,
                PdfReverseColorMode.LIGHTNESS
            )
        )
    }

    @Test
    fun `thumbnail blend mode follows selected theme darkness`() {
        assertEquals(BlendMode.Multiply, sharedMobilePdfThumbnailBlendMode(sepia))
        assertEquals(BlendMode.Multiply, sharedMobilePdfThumbnailBlendMode(noTheme))
        assertEquals(BlendMode.Screen, sharedMobilePdfThumbnailBlendMode(dark))
        assertEquals(BlendMode.Screen, sharedMobilePdfThumbnailBlendMode(reverse))
    }
}
