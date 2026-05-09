package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test

class CssParserThemeModeTest {

    @Test
    fun parseCanPreserveRawPaintColorsForLayoutCaches() {
        val result = CssParser.parse(
            cssContent = "p { color: #000000; background-color: #ffffff; border-top-width: 1px; border-top-style: solid; border-top-color: #000000; }",
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White,
            adaptThemeColors = false
        )

        val style = result.rules.byTag.getValue("p").single().style

        assertEquals(Color.Black, style.spanStyle.color)
        assertEquals(Color.White, style.blockStyle.backgroundColor)
        assertEquals(Color.Black, style.blockStyle.borderTop?.color)
    }

    @Test
    fun parseStillAdaptsPaintColorsWhenThemeModeIsEnabled() {
        val result = CssParser.parse(
            cssContent = "p { color: #000000; background-color: #ffffff; border-top-width: 1px; border-top-style: solid; border-top-color: #000000; }",
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White,
            adaptThemeColors = true
        )

        val style = result.rules.byTag.getValue("p").single().style

        assertEquals(Color.White, style.spanStyle.color)
        assertEquals(Color.Transparent, style.blockStyle.backgroundColor)
        assertEquals(Color.White, style.blockStyle.borderTop?.color)
    }
}
