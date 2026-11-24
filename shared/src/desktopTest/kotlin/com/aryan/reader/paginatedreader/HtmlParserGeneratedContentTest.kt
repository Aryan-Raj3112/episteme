package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Container-level `::before`/`::after` support. Only inline elements and
 * paragraph bodies used to emit generated content, so every window, bubble and
 * comment box in a book that draws its borders with pseudo-elements lost them.
 */
class HtmlParserGeneratedContentTest {

    @Test
    fun `block pseudo elements become their own blocks around container content`() {
        val blocks = parse(
            html = """
                <div class="wiki"><p>Body text</p></div>
            """.trimIndent(),
            css = """
                .wiki > p { margin: 0; }
                .wiki::before { content: '=====' !important; display: block !important; }
                .wiki::after { content: 'End of note'; display: block !important; }
            """.trimIndent()
        )

        assertEquals(listOf("=====", "Body text", "End of note"), blocks.texts())
        assertEquals("::before", (blocks.first() as SemanticParagraph).spans.single().tag)
        assertEquals("::after", (blocks.last() as SemanticParagraph).spans.single().tag)
    }

    @Test
    fun `inline pseudo element joins the container text run`() {
        val blocks = parse(
            html = """
                <div class="comment"><span class="comment-body">Reply text</span></div>
            """.trimIndent(),
            css = ".comment::before { content: '- '; }"
        )

        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("- Reply text", paragraph.text)
        val generated = paragraph.spans.single { it.tag == "::before" }
        assertEquals(0, generated.start)
        assertEquals(2, generated.end)
    }

    @Test
    fun `inline after pseudo element closes the last text run`() {
        val blocks = parse(
            html = """
                <div class="bubble"><span>Hey</span></div>
            """.trimIndent(),
            css = ".bubble::after { content: ' -' !important; }"
        )

        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("Hey -", paragraph.text)
        val generated = paragraph.spans.single { it.tag == "::after" }
        assertEquals(3, generated.start)
        assertEquals(5, generated.end)
    }

    @Test
    fun `hidden or empty pseudo elements generate nothing`() {
        val blocks = parse(
            html = """
                <div class="box"><p>Only text</p></div>
            """.trimIndent(),
            css = """
                .box::before { content: 'hidden'; display: none !important; }
                .box::after { content: '' !important; }
            """.trimIndent()
        )

        assertEquals(listOf("Only text"), blocks.texts())
    }

    @Test
    fun `pseudo element box does not repaint the container box`() {
        // The window owns its padding/margin/background; generated text inside it
        // must not repeat the box, which browser engines never do.
        val blocks = parse(
            html = """
                <div class="window"><p>Content</p></div>
            """.trimIndent(),
            css = """
                .window { padding: 16px; margin: 10px; background: #223; }
                .window::before { content: 'head'; display: block !important; }
            """.trimIndent()
        )

        val container = blocks.single() as SemanticFlexContainer
        val generated = container.children.first() as SemanticParagraph
        assertEquals("head", generated.text)
        assertEquals(androidx.compose.ui.unit.Dp(0f), generated.style.blockStyle.padding.top)
        assertEquals(androidx.compose.ui.unit.Dp(0f), generated.style.blockStyle.margin.top)
    }

    @Test
    fun `rem based pseudo spacing survives parsing`() {
        val blocks = parse(
            html = """
                <div class="note"><p>Text</p></div>
            """.trimIndent(),
            css = ".note::before { content: 'note'; display: block !important; margin-bottom: 0.6rem; }"
        )

        val generated = blocks.first() as SemanticParagraph
        assertEquals(9.6f, generated.style.blockStyle.margin.bottom.value, 0.001f)
    }

    private fun List<SemanticBlock>.texts(): List<String> = map { (it as SemanticTextBlock).text }

    private fun parse(html: String, css: String): List<SemanticBlock> {
        val cssRules = CssParser.parse(
            cssContent = css,
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules
        return htmlToSemanticBlocks(
            html = html,
            cssRules = cssRules,
            textStyle = TextStyle(fontSize = 16.sp),
            chapterAbsPath = "OEBPS/chapter1.xhtml",
            extractionBasePath = "",
            density = Density(1f),
            fontFamilyMap = emptyMap(),
            constraints = Constraints(maxWidth = 400, maxHeight = 800)
        )
    }
}
