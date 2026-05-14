package com.aryan.reader.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPdfThemeTest {
    @Test
    fun `desktop pdf defaults to vertical display mode`() {
        assertEquals(PdfDisplayMode.VERTICAL_SCROLL, DesktopDefaultPdfDisplayMode)
        assertEquals(8.dp, DesktopDefaultPdfVerticalPageGap)
    }

    @Test
    fun `page background follows android pdf theme defaults`() {
        val noTheme = ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false)
        val reverse = ReaderTheme("reverse", "Reverse", Color.Black, Color.White, true)
        val sepia = ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false)

        assertEquals(Color.White, desktopPdfPageBackgroundColor(noTheme, PdfDisplayMode.VERTICAL_SCROLL))
        assertEquals(Color.Black, desktopPdfPageBackgroundColor(noTheme, PdfDisplayMode.PAGINATION))
        assertEquals(Color.Black, desktopPdfPageBackgroundColor(reverse, PdfDisplayMode.VERTICAL_SCROLL))
        assertEquals(Color.White, desktopPdfPageBackgroundColor(reverse, PdfDisplayMode.PAGINATION))
        assertEquals(Color(0xFFFBF0D9), desktopPdfPageBackgroundColor(sepia, PdfDisplayMode.VERTICAL_SCROLL))
    }

    @Test
    fun `vertical viewport uses app gap color only when page gaps are visible`() {
        val pageBackground = Color.White
        val gapBackground = Color(0xFFE2E2E2)

        assertEquals(
            gapBackground,
            desktopPdfVerticalViewportBackgroundColor(
                pageBackgroundColor = pageBackground,
                gapBackgroundColor = gapBackground,
                isPageGapVisible = true
            )
        )
        assertEquals(
            pageBackground,
            desktopPdfVerticalViewportBackgroundColor(
                pageBackgroundColor = pageBackground,
                gapBackgroundColor = gapBackground,
                isPageGapVisible = false
            )
        )
    }
}
