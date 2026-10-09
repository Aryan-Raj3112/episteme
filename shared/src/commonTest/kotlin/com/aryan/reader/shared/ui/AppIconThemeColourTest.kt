package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /**
     * A theme primary with a channel pinned at 0 or 255 has a saturation that
     * rounds a hair over one -- the numerator and denominator of the formula are
     * mathematically equal on those colours, and Float can still make them
     * differ in the last bit. `Color.hsl` rejects that with an
     * IllegalArgumentException, which is what crashed the fan's brush on the
     * devices whose Material You primary happened to clip: #B6E2FF is the
     * primary from the crash report, and the other two are the same shape at
     * the ends of the gamut that a tone-80 tint and a tone-40 shade reach.
     */
    @Test
    fun `a primary with a clipped channel repaints instead of throwing`() {
        val clippedPrimaries = listOf(
            Color(0xFFB6E2FF),   // blue at 255 -- the reported crash
            Color(0xFF0037A0),   // blue at 0 -- a dark tone-40 shade
            Color(0xFFFB003C),   // red at 255 -- a saturated tone-80 tint
        )
        val stop = Color(0xFFE7EFFB)

        for (primary in clippedPrimaries) {
            val (hue, saturation) = primary.hueAndSaturation()
            assertTrue(saturation in 0f..1f, "saturation of $primary is $saturation")

            // no exception, and the repaint is the same one the theme asked for:
            // the stop keeps its own lightness and lands on the theme's hue
            val painted = stop.inHueOf(primary)
            assertEquals(Color(0xFFE7EFFB).hslLightness(), painted.hslLightness(), 0.02f)
            assertTrue(hueDistance(hue, painted.hueAndSaturation().first) < 1f)
        }
    }

    /** Hue is circular: 359 degrees and 1 degree are two degrees apart. */
    private fun hueDistance(a: Float, b: Float): Float {
        val direct = abs(a - b)
        return min(direct, 360f - direct)
    }
}
