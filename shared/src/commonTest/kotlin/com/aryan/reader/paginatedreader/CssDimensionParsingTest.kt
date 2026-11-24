package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CSS px values are density-independent (matching WebView rendering); converting them must
 * never divide by device pixel ratio, which shrinks absolutely-sized books on dense screens.
 */
class CssDimensionParsingTest {

    @Test
    fun `px font sizes ignore device density`() {
        listOf(1f, 2f, 3.5f).forEach { density ->
            val result = parseCssDimensionToTextUnit("18px", 1080, density)
            assertEquals(TextUnitType.Sp, result.type)
            assertEquals(18f, result.value)
        }
    }

    @Test
    fun `pt sizes convert to sp without density division`() {
        val result = parseCssDimensionToTextUnit("12pt", 1080, 3f)
        assertEquals(TextUnitType.Sp, result.type)
        assertEquals(16f, result.value)
    }

    @Test
    fun `percentage font sizes resolve against base font size when provided`() {
        val result = parseCssDimensionToTextUnit("80%", 1080, 3f, baseFontSizeSp = 20f)
        assertEquals(TextUnitType.Sp, result.type)
        assertEquals(16f, result.value)
    }

    @Test
    fun `dp lengths ignore device density`() {
        listOf(1f, 3f).forEach { density ->
            assertEquals(15.dp, parseCssSizeToDp("15px", 16f, density, 1080))
            assertEquals(15.dp, parseCssSizeToDp("15px", 16f, density, 1080))
        }
    }

    @Test
    fun `parseCssDimension keeps px density independent`() {
        listOf(1f, 3f).forEach { density ->
            assertEquals(24.dp, CssParser.parseCssDimension("24px", 16f, density, 1080))
            assertEquals(24.dp, CssParser.parseCssDimension("24", 16f, density, 1080))
        }
    }

    @Test
    fun `parseCssDimension percentage stays relative to physical container width`() {
        // 10% of a 1080px-wide container at density 2.7 -> 1080 * 0.1 / 2.7 = 40dp
        assertEquals(Dp(40f), CssParser.parseCssDimension("10%", 16f, 2.7f, 1080))
    }

    @Test
    fun `calc expressions treat px as density independent`() {
        // Container 900 physical px at density 3 => 300dp; calc(100% - 30px) => 300 - 30 = 270dp.
        assertEquals(Dp(270f), CssParser.parseCssDimension("calc(100% - 30px)", 16f, 3f, 900))
    }

    /**
     * `rem` also ends with `em`, so it must be matched first. Before that ordering was
     * fixed, "1.2rem" stripped to "1.2r", failed to parse and silently became 0.dp, which
     * erased every rem margin/padding/radius in books that size in rem.
     */
    @Test
    fun `rem lengths resolve against the root font size`() {
        assertEquals(Dp(8f), CssParser.parseCssDimension("0.5rem", 16f, 1f, 1080))
        assertEquals(Dp(19.2f), CssParser.parseCssDimension("1.2rem", 16f, 1f, 1080))
        assertEquals(Dp(8f), CssParser.parseCssSizeToDp("0.5rem", 16f, 1f, 1080))
        // The public helper in StyleUtils must agree with the parser's own conversion.
        assertEquals(Dp(19.2f), parseCssSizeToDp("1.2rem", 16f, 1f, 1080))
    }

    @Test
    fun `em and rem stay distinct units`() {
        // Both resolve against the root size here because the element size is the base
        // size in this expression, but neither may fall through to 0.dp.
        assertEquals(Dp(32f), CssParser.parseCssDimension("2rem", 16f, 1f, 1080))
        assertEquals(Dp(32f), CssParser.parseCssDimension("2em", 16f, 1f, 1080))
        assertEquals(TextUnit(2f, TextUnitType.Em), parseCssDimensionToTextUnit("2rem", 1080, 1f))
    }

    @Test
    fun `calc expressions resolve rem against the root font size`() {
        assertEquals(Dp(20f), CssParser.parseCssDimension("calc(1rem + 4px)", 16f, 1f, 1080))
    }

    /**
     * A bare zero is a valid length in every unit. It used to fall through to
     * [TextUnit.Unspecified], so `letter-spacing: 0 !important` could not cancel an
     * earlier `letter-spacing: 0.375em` and book separators rendered spread out.
     */
    @Test
    fun `unitless zero is a zero length`() {
        assertEquals(0f, parseCssDimensionToTextUnit("0", 1080, 1f).value)
        assertEquals(TextUnitType.Sp, parseCssDimensionToTextUnit("0", 1080, 1f).type)
        assertEquals(0f, CssParser.parseCssDimension("0", 16f, 1f, 1080).value)
        // Nothing else without a unit may be read as a length.
        assertEquals(TextUnit.Unspecified, parseCssDimensionToTextUnit("auto", 1080, 1f))
        assertEquals(TextUnit.Unspecified, parseCssDimensionToTextUnit("1.5", 1080, 1f))
    }
}
