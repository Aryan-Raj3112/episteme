package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBrightnessSettingsTest {

    @Test
    fun `brightness settings default to system and clamp custom values`() {
        val defaults = ReaderBrightnessSettings()

        assertTrue(defaults.useSystemBrightness)
        assertEquals(0.75f, defaults.safeCustomBrightness, 0.0001f)
        assertEquals(0.01f, defaults.copy(customBrightness = 0f).safeCustomBrightness, 0.0001f)
        assertEquals(0.02f, defaults.copy(customBrightness = 0.02f).safeCustomBrightness, 0.0001f)
        assertEquals(0.23f, defaults.copy(customBrightness = 0.234f).safeCustomBrightness, 0.0001f)
        assertEquals(1f, defaults.copy(customBrightness = 2f).safeCustomBrightness, 0.0001f)
    }

    @Test
    fun `brightness step controls move by one percent and clamp`() {
        assertEquals(0.74f, stepReaderBrightness(0.75f, -1), 0.0001f)
        assertEquals(0.76f, stepReaderBrightness(0.75f, 1), 0.0001f)
        assertEquals(0.01f, stepReaderBrightness(0.01f, -1), 0.0001f)
        assertEquals(1f, stepReaderBrightness(1f, 1), 0.0001f)
    }
}
