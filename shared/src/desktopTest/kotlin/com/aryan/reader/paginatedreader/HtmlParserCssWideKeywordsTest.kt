package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Declarations that undo an earlier rule: CSS-wide keywords and literal zero
 * resets. Plain-text styled books ship an overlay of `x: inherit !important`
 * and `x: 0 !important` rules on top of their decorated defaults, so without
 * these the reader keeps the decoration the book explicitly removed.
 */
class HtmlParserCssWideKeywordsTest {

    @Test
    fun `inherited color cancels a hard-coded author color`() {
        val blocks = parse(
            """<html><body><div class="w"><p class="t">text</p></div></body></html>""",
            ".w { color: #336699; } .t { color: #000000 !important; }" +
                " .w .t { color: inherit !important; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(Color(0xFF336699), paragraph.style.spanStyle.color)
    }

    @Test
    fun `inherited color from an uncolored parent leaves the theme in charge`() {
        // The grey-theme case: the author hard-codes black, the plain text layer
        // asks for inheritance, and the reader theme must win.
        val blocks = parse(
            """<html><body><div><p class="t">text</p></div></body></html>""",
            ".t { color: #000000 !important; } .t { color: inherit !important; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(Color.Unspecified, paragraph.style.spanStyle.color)
    }

    @Test
    fun `webkit text fill color behaves like color`() {
        val blocks = parse(
            """<html><body><div class="w"><p class="t">text</p></div></body></html>""",
            ".w { color: #334455; }" +
                " .t { -webkit-text-fill-color: #000000 !important; }" +
                " .w .t { -webkit-text-fill-color: inherit !important; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(Color(0xFF334455), paragraph.style.spanStyle.color)
    }

    @Test
    fun `unset inherits colors but clears a non-inherited padding`() {
        val blocks = parse(
            """<html><body><div class="w"><p class="t">text</p></div></body></html>""",
            ".w { color: #123456; }" +
                " .t { color: #000000 !important; padding: 5px; }" +
                " .w .t { color: unset !important; padding: unset !important; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(Color(0xFF123456), paragraph.style.spanStyle.color)
        assertEquals(BoxBorders(), paragraph.style.blockStyle.padding)
    }

    @Test
    fun `initial resets a color and a padding`() {
        val blocks = parse(
            """<html><body><div class="w"><p class="t">text</p></div></body></html>""",
            ".t { color: #000000 !important; padding: 5px; }" +
                " .t { color: initial !important; padding: initial !important; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(Color.Unspecified, paragraph.style.spanStyle.color)
        assertEquals(BoxBorders(), paragraph.style.blockStyle.padding)
    }

    @Test
    fun `zero padding border and radius reset an earlier decorated rule`() {
        val base = CssParser.parseProperties(
            properties = "padding: 1rem; border: 2px solid #000000; border-radius: 8px; max-width: 80%",
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 1000, maxHeight = 1000),
            onlyImportant = false,
            isDarkTheme = false,
            adaptThemeColors = false
        )
        val reset = CssParser.parseProperties(
            properties = "padding: 0; border: 0; border-radius: 0; max-width: none",
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 1000, maxHeight = 1000),
            onlyImportant = false,
            isDarkTheme = false,
            adaptThemeColors = false
        )
        assertTrue(reset.blockStyle.explicitZero != 0L)
        val merged = base.merge(reset).blockStyle
        assertEquals(BoxBorders(), merged.padding)
        assertNull(merged.borderTop)
        assertEquals(0.dp, merged.borderTopLeftRadius)
        assertEquals(androidx.compose.ui.unit.Dp.Unspecified, merged.maxWidth)
    }

    @Test
    fun `a later concrete value cancels an earlier reset in the same rule`() {
        val style = CssParser.parseProperties(
            properties = "padding: 0; padding: 5px; border: 0; border: 1px solid #000000",
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 1000, maxHeight = 1000),
            onlyImportant = false,
            isDarkTheme = false,
            adaptThemeColors = false
        )
        assertEquals(0L, style.blockStyle.explicitZero)
        assertEquals(5.dp, style.blockStyle.padding.top)
        assertNotNull(style.blockStyle.borderTop)
    }

    @Test
    fun `reset border removes the decorated container from the tree`() {
        val decorated = parse(
            """<html><body><div class="w"><p>text</p></div></body></html>""",
            ".w { border: 2px solid #000000; }"
        )
        assertEquals(1, decorated.size)
        assertTrue(decorated.single() is SemanticFlexContainer)

        val reset = parse(
            """<html><body><div class="w"><p>text</p></div></body></html>""",
            ".w { border: 2px solid #000000; } .w { border: 0 !important; }"
        )
        assertEquals(1, reset.size)
        assertTrue(reset.single() is SemanticParagraph)
    }

    private fun parse(html: String, css: String? = null): List<SemanticBlock> {
        val density = Density(1f)
        val constraints = Constraints(maxWidth = 400, maxHeight = 800)
        val cssRules = if (css == null) {
            OptimizedCssRules()
        } else {
            OptimizedCssRules().merge(
                CssParser.parse(
                    cssContent = css,
                    cssPath = null,
                    baseFontSizeSp = 16f,
                    density = density.density,
                    constraints = constraints,
                    isDarkTheme = false,
                    adaptThemeColors = false
                ).rules
            )
        }
        return htmlToSemanticBlocks(
            html = html,
            cssRules = cssRules,
            textStyle = TextStyle(fontSize = 16.sp),
            chapterAbsPath = "OEBPS/chapter1.xhtml",
            extractionBasePath = "",
            density = density,
            fontFamilyMap = emptyMap(),
            constraints = constraints
        )
    }
}
