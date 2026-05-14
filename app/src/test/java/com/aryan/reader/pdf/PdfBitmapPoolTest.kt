package com.aryan.reader.pdf

import androidx.core.graphics.createBitmap
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfBitmapPoolTest {

    @After
    fun tearDown() {
        PdfBitmapPool.clear()
    }

    @Test
    fun `recycle leaves overflow bitmaps valid for render thread handoff`() {
        PdfBitmapPool.clear()
        val bitmaps = List(6) { createBitmap(8, 8) }

        bitmaps.forEach(PdfBitmapPool::recycle)

        bitmaps.forEach { bitmap ->
            assertFalse(bitmap.isRecycled)
        }
    }

    @Test
    fun `clear drops pooled bitmaps without invalidating external references`() {
        val bitmap = createBitmap(8, 8)

        PdfBitmapPool.recycle(bitmap)
        PdfBitmapPool.clear()

        assertFalse(bitmap.isRecycled)
    }
}
