package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.ui.SharedNativeHighlightPaintPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One highlight, one tone, whichever surface draws it.
 *
 * The WebView painted `#RRGGBB`, which carries no alpha, while the paginated and scrolling readers
 * filled with `renderColor`, which applies the legacy fill alpha. The hue was identical, so the two
 * did not look like different colours — they looked like the same colour at different intensities,
 * which reads as the WebView being wrong rather than as two code paths disagreeing. These tests pin
 * the alpha, because the alpha is the whole difference.
 */
class ReaderHighlightFillColorTest {

    private val legacyAlpha = SharedNativeHighlightPaintPlan.LEGACY_HIGHLIGHT_ALPHA

    private fun palette(color: HighlightColor, argb: Int? = null) = UserHighlight(
        id = "h",
        cfi = "/4/0",
        text = "beta",
        color = color,
        chapterIndex = 0,
        colorArgb = argb
    )

    @Test
    fun `a palette highlight is filled at the legacy alpha`() {
        val css = palette(HighlightColor.YELLOW).fillCssColor()

        assertEquals("rgba(251,192,45,0.4)", css)
    }

    @Test
    fun `a stored opaque colour is tinted like a palette colour`() {
        // The case the user saw. Stored as fully opaque, so the WebView's #RRGGBB painted it as a
        // solid slab while pagination tinted it.
        val stored = palette(HighlightColor.YELLOW, 0xFF3366CC.toInt())
        val paletteEquivalent = palette(HighlightColor.YELLOW)

        fun alphaOf(highlight: UserHighlight) = highlight.fillCssColor().substringAfterLast(',').dropLast(1)

        assertEquals(alphaOf(paletteEquivalent), alphaOf(stored))
        assertTrue(stored.fillCssColor().endsWith(",0.4)"))
    }

    @Test
    fun `a stored colour keeps its own translucency when it has one`() {
        // Half-transparent custom colours are a deliberate choice, not an accident, so they survive.
        val half = palette(HighlightColor.YELLOW, 0x803366CC.toInt())

        assertEquals("rgba(51,102,204,0.502)", half.fillCssColor())
    }

    @Test
    fun `every palette colour produces a valid rgba string`() {
        HighlightColor.entries.forEach { color ->
            val css = palette(color).fillCssColor()
            assertTrue(
                css.matches(Regex("rgba\\(\\d{1,3},\\d{1,3},\\d{1,3},0?\\.\\d+\\)")),
                "$color produced $css"
            )
        }
    }

    @Test
    fun `the css alpha matches the alpha the painters fill with`() {
        // The property that matters: same colour object in, same alpha out. If these ever diverge the
        // surfaces drift again, which is the bug this exists to prevent.
        val highlight = palette(HighlightColor.GREEN, 0xFF388E3C.toInt())
        val painted = highlight.renderColor(legacyAlpha)

        val expected = "rgba(${(painted.red * 255).toInt()},${(painted.green * 255).toInt()}," +
            "${(painted.blue * 255).toInt()},${painted.alpha})"

        assertTrue(
            highlight.fillCssColor().startsWith(expected.substringBeforeLast(',')),
            "${highlight.fillCssColor()} should fill with the same colour as $expected"
        )
    }

    @Test
    fun `a fully transparent colour stays transparent rather than becoming opaque`() {
        val invisible = palette(HighlightColor.YELLOW, 0x003366CC)

        assertEquals("rgba(51,102,204,0.0)", invisible.fillCssColor())
    }

    @Test
    fun `a recolour fills with the same tone as the colour it landed on`() {
        // The recolour path used to format its own `#RRGGBB` for the WebView, which carries no alpha,
        // so recolouring shifted the tone while the same highlight sat correctly tinted everywhere
        // else. The rule that matters is that a recoloured highlight and a freshly created one of the
        // same colour resolve identically.
        HighlightColor.entries.forEach { color ->
            val argb = color.color.toArgb()
            val recoloured = palette(HighlightColor.YELLOW).recoloredTo(argb)
            val created = palette(color, argb)

            assertEquals(
                created.fillCssColor(),
                recoloured.fillCssColor(),
                "recolouring to $color changed the fill"
            )
            assertTrue(recoloured.fillCssColor().endsWith(",0.4)"))
        }
    }

    @Test
    fun `channel rounding stays in range at the extremes`() {
        assertEquals("rgba(255,255,255,1.0)", Color(0xFFFFFFFF).toRgbaCss())
        assertEquals("rgba(0,0,0,0.0)", Color(0x00000000).toRgbaCss())
    }
}