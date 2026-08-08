package com.aryan.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedMobilePdfOcrAdapterTest {
    @Test
    fun `ocr bounds normalize and clamp to the rendered page`() {
        val result = sharedNormalizedBounds(-10, 20, 220, 120, 200f, 100f)

        requireNotNull(result)
        assertEquals(0f, result.left)
        assertEquals(0.2f, result.top)
        assertEquals(1f, result.right)
        assertEquals(1f, result.bottom)
    }

    @Test
    fun `empty or invalid rendered bounds are rejected`() {
        assertNull(sharedNormalizedBounds(10, 10, 10, 20, 100f, 100f))
        assertNull(sharedNormalizedBounds(0, 0, 10, 10, 0f, 100f))
    }
}
