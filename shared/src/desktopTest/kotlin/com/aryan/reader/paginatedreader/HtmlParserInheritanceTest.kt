package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CSS-inherited properties (writing-mode, word-break, word-spacing) must flow
 * from ancestors like browsers propagate them. Without this, vertical chapters
 * whose mode lives on body/html (e.g. `body.p-titlepage { writing-mode:
 * vertical-rl }`) lose it on plain paragraphs and paginate as horizontal.
 */
class HtmlParserInheritanceTest {

    @Test
    fun `body writing mode inherits to plain paragraphs`() {
        val blocks = parse(
            "<html><body><div><p>あいう</p></div></body></html>",
            "body { writing-mode: vertical-rl; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("vertical-rl", paragraph.style.writingMode)
    }

    @Test
    fun `element writing mode wins over inherited`() {
        val blocks = parse(
            "<html><body><p class=\"h\">あいう</p></body></html>",
            "body { writing-mode: vertical-rl; } .h { writing-mode: horizontal-tb; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("horizontal-tb", paragraph.style.writingMode)
    }

    @Test
    fun `word break inherits from body`() {
        val blocks = parse(
            "<html><body><p>あいう</p></body></html>",
            "body { word-break: break-all; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("break-all", paragraph.style.wordBreak)
    }

    @Test
    fun `no author mode leaves writing mode null`() {
        val blocks = parse("<p>あいう</p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertNull(paragraph.style.writingMode)
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
