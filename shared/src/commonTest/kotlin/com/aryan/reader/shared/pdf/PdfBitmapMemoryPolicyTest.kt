package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfBitmapMemoryPolicyTest {
    @Test
    fun poolBudgetScalesWithHeapAndIsCapped() {
        assertEquals(8L * 1024L * 1024L, pdfBitmapPoolByteBudget(256L * 1024L * 1024L))
        assertEquals(16L * 1024L * 1024L, pdfBitmapPoolByteBudget(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun poolRejectsBuffersThatWouldExceedByteBudget() {
        val heap = 256L * 1024L * 1024L

        assertTrue(canPoolPdfBitmap(0L, 8L * 1024L * 1024L, heap))
        assertFalse(canPoolPdfBitmap(4L * 1024L * 1024L, 5L * 1024L * 1024L, heap))
        assertFalse(canPoolPdfBitmap(0L, 9L * 1024L * 1024L, heap))
    }
}
