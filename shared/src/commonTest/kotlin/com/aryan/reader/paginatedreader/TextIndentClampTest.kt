package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Reader pages clip horizontally, so an uncompensated negative first-line indent would
 * paint off the page (Gutenberg `blockquote { text-indent: -2em }`). The parser clamps
 * the first line to the padded content box while preserving hanging indents.
 */
class TextIndentClampTest {

    private fun indentOf(declarations: String): TextIndent {
        val style = CssParser.parseProperties(
            properties = declarations,
            baseFontSizeSp = 16f,
            density = 2f,
            constraints = Constraints(maxWidth = 1080, maxHeight = 1920),
            onlyImportant = false,
            isDarkTheme = false
        )
        return assertNotNull(style.paragraphStyle.textIndent)
    }

    @Test
    fun `uncompensated negative indent clamps to zero`() {
        val indent = indentOf("text-indent: -2em")

        assertEquals(0f, indent.firstLine.value, 0.001f)
        assertEquals(0f, indent.restLine.value, 0.001f)
    }

    @Test
    fun `hanging idiom with compensating padding is preserved`() {
        val indent = indentOf("text-indent: -2em; padding-left: 2em")

        assertEquals(TextUnitType.Em, indent.firstLine.type)
        assertEquals(-2f, indent.firstLine.value, 0.001f)
    }

    @Test
    fun `positive indent is untouched`() {
        val indent = indentOf("text-indent: 2em")

        assertEquals(TextUnitType.Em, indent.firstLine.type)
        assertEquals(2f, indent.firstLine.value, 0.001f)
    }

    @Test
    fun `partial compensation clamps to the padded box`() {
        val indent = indentOf("text-indent: -2em; padding-left: 0.5em")

        // minFirst = -(0.5em) = -16px at density 2 -> -8sp.
        assertEquals(-8f, indent.firstLine.value, 0.001f)
    }
}
