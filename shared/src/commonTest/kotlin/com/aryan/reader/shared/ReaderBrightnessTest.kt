package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderBrightnessTest {
    @Test
    fun `brightness follows android one percent normalization and bounds`() {
        assertEquals(0.01f, normalizeReaderBrightness(-1f))
        assertEquals(0.76f, normalizeReaderBrightness(0.755f))
        assertEquals(1f, normalizeReaderBrightness(2f))
    }

    @Test
    fun `brightness stepping follows android one percent increments`() {
        assertEquals(0.74f, stepReaderBrightness(0.75f, -1))
        assertEquals(0.76f, stepReaderBrightness(0.75f, 1))
        assertEquals(0.01f, stepReaderBrightness(0.01f, -1))
        assertEquals(1f, stepReaderBrightness(1f, 1))
    }
}
