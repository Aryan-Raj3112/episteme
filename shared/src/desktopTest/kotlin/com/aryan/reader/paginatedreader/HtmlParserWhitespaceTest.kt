package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the whitespace-collapse contract of the JVM HTML pipeline after the
 * per-text-node regexes were hoisted to precompiled values: identical output,
 * without paying Pattern.compile per node on node-heavy chapters.
 */
class HtmlParserWhitespaceTest {

    @Test
    fun `default whitespace collapses runs and newlines`() {
        val blocks = parse("<p>Hello   \n\t  World</p>")
        assertEquals("Hello World", (blocks.single() as SemanticParagraph).text)
    }

    @Test
    fun `pre-line preserves newlines but collapses other whitespace runs`() {
        val blocks = parse("<p style=\"white-space:pre-line\">Hello   \n\t  World</p>")
        assertEquals("Hello \n World", (blocks.single() as SemanticParagraph).text)
    }

    private fun parse(html: String): List<SemanticBlock> {
        return htmlToSemanticBlocks(
            html = html,
            cssRules = OptimizedCssRules(),
            textStyle = TextStyle(fontSize = 16.sp),
            chapterAbsPath = "OEBPS/chapter1.xhtml",
            extractionBasePath = "",
            density = Density(1f),
            fontFamilyMap = emptyMap(),
            constraints = Constraints(maxWidth = 400, maxHeight = 800)
        )
    }
}
