package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaginationMeasurementPolicyTest {
    @Test
    fun textHeightAndMarginsPreserveAndroidRoundingAndCollapseRules() {
        assertEquals(120, measuredTextHeightForPagination(120, 119.2f))
        assertEquals(133, measuredTextHeightForPagination(120, 132.1f))
        assertEquals(0f, effectiveTopMarginPxForPagination(true, 96f))
        assertEquals(96f, effectiveTopMarginPxForPagination(false, 96f))
        assertEquals(0, collapsedVerticalMarginPxForPagination(-12f, -48f))
        assertEquals(20, collapsedVerticalMarginPxForPagination(14.4f, 20.2f))
    }

    @Test
    fun availableWidthPreservesCenteredAndMarginAdjustedBehavior() {
        assertEquals(896f, availableBlockWidthPxForPagination(996, 50f, 50f, false))
        assertEquals(996f, availableBlockWidthPxForPagination(996, 50f, 50f, true))
        assertEquals(0f, availableBlockWidthPxForPagination(50, 40f, 40f, false))
    }

    @Test
    fun svgUnitsPreserveAndroidConversionConstants() {
        val density = Density(2f)
        assertEquals(10f, parseSvgDimension("1ex", 20f, 400, density))
        assertEquals(40f, parseSvgDimension("2em", 20f, 400, density))
        assertEquals(26.66f, parseSvgDimension("10pt", 20f, 400, density)!!, 0.001f)
        assertEquals(100f, parseSvgDimension("25%", 20f, 400, density))
        assertNull(parseSvgDimension("invalid", 20f, 400, density))
    }

    @Test
    fun centeredPaddingAndIntrinsicImageWidthPreserveAndroidDensityRules() {
        val density = Density(2f)
        val style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        assertEquals(40, centeredTextSafetyPaddingPx(style, density))
        assertEquals(0, centeredTextSafetyPaddingPx(style, density, enabled = false))
        assertEquals(120f, intrinsicImageWidthPx(60f, density, 800f))
        assertEquals(800f, intrinsicImageWidthPx(600f, density, 800f))
        assertEquals(0f, intrinsicImageWidthPx(-1f, density, 800f))
    }
}
