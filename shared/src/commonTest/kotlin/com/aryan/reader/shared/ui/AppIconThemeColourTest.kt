package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconThemeColourTest {

    @Test
    fun `stops keep their lightness and adopt the theme hue`() {
        val primary = Color(0xFF3F51B5)   // indigo
        val (hue, saturation) = primary.hueAndSaturation()

        // the artwork's pale upper band and deep lower band, in the brand blue
        val pale = Color(0xFFE7EFFB)
        val deep = Color(0xFF3F5786)

        val themedPale = pale.inHueOf(primary)
        val themedDeep = deep.inHueOf(primary)

        // the pale-to-deep ladder survives, so the fan keeps its reading...
        assertEquals(true, themedDeep.hslLightness() < themedPale.hslLightness())
        // ...and both stops sit on the theme's hue and saturation. The tolerance is
        // the width of `Color`'s 8-bit sRGB components, not slack in the repaint:
        // a stop round-trips through RGB on its way back out of `Color.hsl`.
        assertEquals(hue, themedPale.hueAndSaturation().first, 1f)
        assertEquals(saturation, themedPale.hueAndSaturation().second, 0.02f)
        assertEquals(hue, themedDeep.hueAndSaturation().first, 1f)
        assertEquals(saturation, themedDeep.hueAndSaturation().second, 0.02f)
    }

    @Test
    fun `a stop's transparency survives the repaint`() {
        // the shade layer's stops are translucent; that is part of the artwork
        val shaded = Color(0xFF1332A0).inHueOf(Color(0xFFE91E63), alpha = 0.17f)
        assertEquals(0.17f, shaded.alpha, 0.005f)
    }

    @Test
    fun `a grey theme keeps the ladder but drops the hue`() {
        val primary = Color(0xFF808080)
        val pale = Color(0xFFE7EFFB).inHueOf(primary)
        val deep = Color(0xFF3F5786).inHueOf(primary)
        assertEquals(0f, pale.hueAndSaturation().second, 0.01f)
        assertEquals(0f, deep.hueAndSaturation().second, 0.01f)
        // the lightness ladder is the artwork's, not the theme's
        assertEquals(Color(0xFFE7EFFB).hslLightness(), pale.hslLightness(), 0.01f)
        assertEquals(Color(0xFF3F5786).hslLightness(), deep.hslLightness(), 0.01f)
    }
}
