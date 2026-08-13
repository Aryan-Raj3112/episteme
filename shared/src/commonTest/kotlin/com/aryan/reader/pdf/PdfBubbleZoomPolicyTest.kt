package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfBubbleZoomPolicyTest {
    @Test
    fun `prefetch order preserves Android center next previous priority`() {
        assertEquals(listOf(10, 11, 9), buildPdfBubblePrefetchOrder(10, 100))
        assertEquals(listOf(0, 1), buildPdfBubblePrefetchOrder(-4, 5))
        assertEquals(listOf(4, 3), buildPdfBubblePrefetchOrder(99, 5))
        assertEquals(emptyList(), buildPdfBubblePrefetchOrder(0, 0))
    }

    @Test
    fun `dynamic zoom preserves target fractions and clamps`() {
        assertEquals(2f, computeDynamicBubbleZoomFactor(300f, 80f, 1000f, 500f))
        assertEquals(1.5f, computeDynamicBubbleZoomFactor(0f, 80f, 1000f, 500f))
        assertEquals(4.25f, computeDynamicBubbleZoomFactor(10f, 10f, 1000f, 500f))
    }

    @Test
    fun `render scale preserves Android byte and dimension caps`() {
        assertEquals(2f, safePdfBitmapRenderScale(500f, 500f, 2f))
        val limited = safePdfBitmapRenderScale(4000f, 4000f, 4f)
        assertTrue(limited < 4f)
        assertTrue(4000f * limited <= PDF_MAX_DRAW_BITMAP_DIMENSION_PX + 0.01f)
        assertEquals(1f, safePdfBitmapRenderScale(0f, 500f, 2f))
    }
}
