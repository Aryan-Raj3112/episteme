package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalCssParsingTest {

    private fun parseAll(declaration: String): CssStyle {
        return CssParser.parseProperties(
            properties = declaration,
            baseFontSizeSp = 18f,
            density = 1f,
            constraints = Constraints(maxWidth = 980, maxHeight = 720),
            onlyImportant = false,
            isDarkTheme = false
        )
    }

    @Test
    fun `writing mode normalizes standard and prefixed forms`() {
        assertEquals("vertical-rl", parseAll("writing-mode: vertical-rl").writingMode)
        assertEquals("vertical-rl", parseAll("-webkit-writing-mode: vertical-rl").writingMode)
        assertEquals("vertical-rl", parseAll("-epub-writing-mode: vertical-rl").writingMode)
        assertEquals("horizontal-tb", parseAll("-webkit-writing-mode: horizontal-tb").writingMode)
        assertTrue(parseAll("writing-mode: vertical-rl").isVerticalWriting())
        assertFalse(parseAll("-webkit-writing-mode: horizontal-tb").isVerticalWriting())
    }

    @Test
    fun `text combine marks tcy runs`() {
        assertTrue(parseAll("text-combine-upright: all").isTateChuYoko())
        assertTrue(parseAll("-webkit-text-combine: horizontal").isTateChuYoko())
        assertFalse(parseAll("text-combine-upright: none").isTateChuYoko())
    }

    @Test
    fun `word break round trips`() {
        assertEquals("break-all", parseAll("word-break: break-all").wordBreak)
        assertEquals("break-all", parseAll("-webkit-word-break: break-all").wordBreak)
        assertEquals("normal", parseAll("-epub-word-break: normal").wordBreak)
    }
}
