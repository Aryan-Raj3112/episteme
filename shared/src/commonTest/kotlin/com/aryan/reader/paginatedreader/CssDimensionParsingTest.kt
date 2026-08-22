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
}
