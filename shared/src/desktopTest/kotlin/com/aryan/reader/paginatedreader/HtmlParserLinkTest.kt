package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
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
