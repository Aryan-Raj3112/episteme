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
    fun `turn stack order keeps the curled sheet above the set sliding in`() {
        // Exact Android zIndex is -offset; the bucket must agree on every pair that
        // decides what draws on top (the two turning slots, and the sheet against the
        // off-screen neighbour resting a page behind).
        val offsets = listOf(-2f, -1.5f, -1f, -0.75f, -0.25f, 0f, 0.25f, 0.75f, 1f, 1.5f, 2f)
        fun signOf(value: Float): Int = if (value < 0f) -1 else if (value > 0f) 1 else 0
        for (first in offsets) {
            for (second in offsets) {
                if (signOf(first) == signOf(second)) continue
                // -offset ordering: a larger zIndex draws on top.
                val exact = (-first).compareTo(-second)
                val bucketed = pdfPagerTurnStackOrder(first).compareTo(pdfPagerTurnStackOrder(second))
                assertEquals(exact, bucketed, "offsets $first vs $second must keep the exact draw order")
            }
        }
    }

    @Test
    fun `turn stack order only changes when a slot crosses the turn origin`() {
        // A forward turn sweeps the outgoing sheet from 0 to -1 and the incoming page
        // from 1 to 0: the bucket changes exactly twice, not once per frame.
        assertEquals(0f, pdfPagerTurnStackOrder(0f))
        assertEquals(1f, pdfPagerTurnStackOrder(-0.01f))
        assertEquals(1f, pdfPagerTurnStackOrder(-0.999f))
        assertEquals(-1f, pdfPagerTurnStackOrder(0.001f))
        assertEquals(-1f, pdfPagerTurnStackOrder(1f))
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
