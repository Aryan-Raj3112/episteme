package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfPageTurnMotionTest {
    @Test
    fun `realistic turn plays only for single pager steps when enabled`() {
        assertTrue(shouldPlayRealisticPdfPageTurn(animationEnabled = true, fromPagerPage = 3, toPagerPage = 4))
        assertTrue(shouldPlayRealisticPdfPageTurn(animationEnabled = true, fromPagerPage = 3, toPagerPage = 2))
        assertFalse(shouldPlayRealisticPdfPageTurn(animationEnabled = false, fromPagerPage = 3, toPagerPage = 4))
        assertFalse(shouldPlayRealisticPdfPageTurn(animationEnabled = true, fromPagerPage = 3, toPagerPage = 3))
        assertFalse(shouldPlayRealisticPdfPageTurn(animationEnabled = true, fromPagerPage = 3, toPagerPage = 5))
        assertFalse(shouldPlayRealisticPdfPageTurn(animationEnabled = true, fromPagerPage = 3, toPagerPage = 0))
    }

    @Test
    fun `paper color follows the pdf theme sheet`() {
        assertEquals(Color.White, pdfPaginatedPagePaperColor(ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, isDark = true)))
        assertEquals(Color.White, pdfPaginatedPagePaperColor(ReaderTheme("no_theme", "None", Color.Black, Color.White, isDark = false)))
        assertEquals(Color.Black, pdfPaginatedPagePaperColor(ReaderTheme("reverse", "Reverse", Color.White, Color.Black, isDark = true)))
        assertEquals(
            Color(0xFFFBF0D9),
            pdfPaginatedPagePaperColor(ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), isDark = false))
        )
        assertEquals(
            Color.White,
            pdfPaginatedPagePaperColor(ReaderTheme("custom", "Custom", Color.Unspecified, Color.Black, isDark = false))
        )
    }
}
