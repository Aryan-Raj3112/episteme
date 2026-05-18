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
        assertEquals(0.05f, defaults.copy(customBrightness = 0f).safeCustomBrightness, 0.0001f)
        assertEquals(1f, defaults.copy(customBrightness = 2f).safeCustomBrightness, 0.0001f)
    }
}
