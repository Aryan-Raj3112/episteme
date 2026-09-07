package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies inline math spans (`span.math-inline` emitted by the Markdown
 * pipeline) stay inside the paragraph text flow as placeholder-char spans,
 * while display math (`span.math-display`) remains a standalone block.
 */
class HtmlParserInlineMathTest {

    @Test
    fun inlineMathStaysInsideParagraphText() {
        val html = """
            <p>Cost is <span class="math-inline"><svg width="2ex" height="1ex"></svg></span> per unit and <span class="math-inline"><svg width="1ex" height="1ex"></svg></span> extra.</p>
        """.trimIndent()

        val blocks = parse(html)
        val paragraph = blocks.filterIsInstance<SemanticParagraph>().single()

        val placeholders = paragraph.text.count { it.toString() == MATH_PLACEHOLDER_CHAR }
        assertEquals(2, placeholders, "paragraph text must contain one placeholder char per inline equation")
        assertEquals(2, paragraph.spans.count { it.isInlineMath })

        val mathSpan = paragraph.spans.first { it.isInlineMath }
        assertEquals(1, mathSpan.end - mathSpan.start, "math span covers exactly the placeholder char")
        assertTrue(mathSpan.mathSvg!!.contains("<svg"), "span carries the rendered svg")
    }

    @Test
    fun textAroundInlineMathIsPreserved() {
        val html = """
            <p>Before <span class="math-inline"><svg width="1ex" height="1ex"></svg></span> after.</p>
        """.trimIndent()

        val blocks = parse(html)
        val paragraph = blocks.filterIsInstance<SemanticParagraph>().single()

        assertEquals("Before $MATH_PLACEHOLDER_CHAR after.", paragraph.text)
    }

    @Test
    fun displayMathIsStandaloneBlock() {
        val html = """
            <p><span class="math-display"><svg width="10ex" height="5ex"></svg></span></p>
        """.trimIndent()

        val blocks = parse(html)
        val math = blocks.filterIsInstance<SemanticMath>()
        assertNotNull(math.firstOrNull(), "display math becomes a standalone SemanticMath block")
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
