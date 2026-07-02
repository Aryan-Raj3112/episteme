package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlParserLinkTest {
    @Test
    fun `block anchor propagates href to paragraph text`() {
        val blocks = parse(
            """
            <html>
              <body>
                <a href="chapter2.xhtml#start"><p>Continue reading</p></a>
              </body>
            </html>
            """.trimIndent()
        )

        val paragraph = blocks.single() as SemanticParagraph
        val linkSpan = paragraph.spans.single { it.linkHref == "chapter2.xhtml#start" }

        assertEquals("Continue reading", paragraph.text)
        assertEquals(0, linkSpan.start)
        assertEquals(paragraph.text.length, linkSpan.end)
    }

    @Test
    fun `block anchor propagates href to heading text`() {
        val blocks = parse(
            """
            <html>
              <body>
                <a href="#details"><h2>Details</h2></a>
              </body>
            </html>
            """.trimIndent()
        )

        val heading = blocks.single() as SemanticHeader

        assertEquals("Details", heading.text)
        assertTrue(heading.spans.any { span ->
            span.linkHref == "#details" &&
                span.start == 0 &&
                span.end == heading.text.length
        })
    }

    @Test
    fun `nested inline spans inherit anchor href`() {
        val blocks = parse(
            """
            <html>
              <body>
                <p><a href="notes.xhtml#n1"><span>note</span></a></p>
              </body>
            </html>
            """.trimIndent()
        )

        val paragraph = blocks.single() as SemanticParagraph

        assertEquals("note", paragraph.text)
        assertTrue(paragraph.spans.any { span ->
            span.tag == "span" &&
                span.linkHref == "notes.xhtml#n1" &&
                span.start == 0 &&
                span.end == paragraph.text.length
        })
    }

    @Test
    fun `namespaced anchor href is treated as link`() {
        val blocks = parse(
            """
            <html>
              <body>
                <p><a xlink:href="appendix.xhtml#more">Appendix</a></p>
              </body>
            </html>
            """.trimIndent()
        )

        val paragraph = blocks.single() as SemanticParagraph

        assertEquals("Appendix", paragraph.text)
        assertTrue(paragraph.spans.any { span ->
            span.linkHref == "appendix.xhtml#more" &&
                span.start == 0 &&
                span.end == paragraph.text.length
        })
    }

    @Test
    fun `css font family resolves onto block and inline span styles`() {
        val cssRules = CssParser.parse(
            cssContent = """
                p { font-family: "BodyFace"; }
                i { font-style: italic; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules

        val blocks = parse(
            html = """
            <html>
              <body>
                <p>plain <i>italic</i></p>
              </body>
            </html>
            """.trimIndent(),
            cssRules = cssRules,
            fontFamilyMap = mapOf("bodyface" to FontFamily.Serif)
        )

        val paragraph = blocks.single() as SemanticParagraph
        val italicSpan = paragraph.spans.single { it.tag == "i" }

        assertEquals(FontFamily.Serif, paragraph.style.spanStyle.fontFamily)
        assertEquals(FontFamily.Serif, italicSpan.style.spanStyle.fontFamily)
        assertEquals(FontStyle.Italic, italicSpan.style.spanStyle.fontStyle)
    }

    @Test
    fun `selector matching cache preserves complex cascade and generated content`() {
        val cssRules = CssParser.parse(
            cssContent = """
                p.note { color: red; }
                .box p.note { color: blue; }
                p.note::before { content: 'Before '; }
                p.note::after { content: ' After'; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules

        val blocks = parse(
            html = """
            <html>
              <body>
                <div class="box"><p class="note">Body <span>child</span></p></div>
              </body>
            </html>
            """.trimIndent(),
            cssRules = cssRules
        )

        val paragraph = blocks.single() as SemanticParagraph

        assertEquals("Before Body child After", paragraph.text)
        assertEquals(androidx.compose.ui.graphics.Color.Blue, paragraph.style.spanStyle.color)
        assertTrue(paragraph.spans.any { it.tag == "::before" && it.start == 0 })
        assertTrue(paragraph.spans.any { it.tag == "::after" && it.end == paragraph.text.length })
    }

    private fun parse(
        html: String,
        cssRules: OptimizedCssRules = OptimizedCssRules(),
        fontFamilyMap: Map<String, FontFamily> = emptyMap()
    ): List<SemanticBlock> {
        return htmlToSemanticBlocks(
            html = html,
            cssRules = cssRules,
            textStyle = TextStyle(fontSize = 16.sp),
            chapterAbsPath = "OEBPS/chapter1.xhtml",
            extractionBasePath = "",
            density = Density(1f),
            fontFamilyMap = fontFamilyMap,
            constraints = Constraints(maxWidth = 400, maxHeight = 800)
        )
    }
}
