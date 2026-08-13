package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMacTrackpadMagnificationTest {
    @Test
    fun `positive trackpad magnification zooms in and negative zooms out`() {
        assertTrue(desktopTrackpadMagnificationFactor(0.2) > 1f)
        assertTrue(desktopTrackpadMagnificationFactor(-0.2) < 1f)
    }

    @Test
    fun `invalid or empty magnification leaves zoom unchanged`() {
        assertEquals(1f, desktopTrackpadMagnificationFactor(0.0))
        assertEquals(1f, desktopTrackpadMagnificationFactor(Double.NaN))
        assertEquals(1f, desktopTrackpadMagnificationFactor(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `native magnification is bounded to avoid a single event zoom jump`() {
        assertEquals(
            desktopTrackpadMagnificationFactor(0.25),
            desktopTrackpadMagnificationFactor(2.0)
        )
        assertEquals(
            desktopTrackpadMagnificationFactor(-0.25),
            desktopTrackpadMagnificationFactor(-2.0)
        )
    }
}
