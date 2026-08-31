package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color

class HtmlParserLinkTest {
    @Test
    fun `table colspan is always positive`() {
        val blocks = parse(
            """
            <table><tr><td colspan="0">Zero</td><td colspan="-2">Negative</td></tr></table>
            """.trimIndent()
        )

        val table = blocks.single() as SemanticTable

        assertEquals(listOf(1, 1), table.rows.single().map { it.colspan })
    }

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

    @Test
    fun `generated content decodes css hexadecimal escapes`() {
        val cssRules = CssParser.parse(
            cssContent = """
                p::before { content: "\200c"; }
                p::after { content: "\41!"; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules

        val paragraph = parse(
            html = "<html><body><p>Body</p></body></html>",
            cssRules = cssRules
        ).single() as SemanticParagraph

        assertEquals("\u200cBodyA!", paragraph.text)
    }

    @Test
    fun `chant score preserves neume and lyric as atomic native flow units`() {
        val cssRules = CssParser.parse(
            cssContent = """
                .chant-unit { display: inline-grid; }
                .neume-slot { font-family: "BUMEByzantina"; font-size: 175%; font-feature-settings: "liga" on, "calt" on; }
                .dichrom-neumes { display: none; }
                .lyric-slot { white-space: nowrap; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules
        val blocks = parse(
            html = """
                <html><body><div class="hymn-score-canvas">
                  <div class="chant-unit">
                    <div class="neume-slot dichrom-neumes">𝁕<span style="color: red">𝃬</span></div>
                    <div class="neume-slot compat-neumes">𝁕𝃬</div>
                    <div class="lyric-slot">what</div>
                  </div>
                  <div class="non-breaking"><div class="chant-unit">
                    <div class="neume-slot">𝁇</div><div class="lyric-slot">crowns</div>
                  </div></div>
                </div></body></html>
            """.trimIndent(),
            cssRules = cssRules,
            fontFamilyMap = mapOf("bumebyzantina" to FontFamily.Serif)
        )

        val score = blocks.single() as SemanticFlexContainer
        assertEquals("reader-chant-flow", score.style.blockStyle.display)
        assertEquals(2, score.children.size)
        val firstUnit = score.children.first() as SemanticFlexContainer
        assertEquals("reader-chant-unit", firstUnit.style.blockStyle.display)
        assertEquals(2, firstUnit.children.size)
        val nonBreakingGroup = score.children[1] as SemanticFlexContainer
        assertEquals("reader-chant-nonbreaking", nonBreakingGroup.style.blockStyle.display)
        assertEquals("reader-chant-unit", (nonBreakingGroup.children.single() as SemanticFlexContainer).style.blockStyle.display)
        assertEquals("𝁕𝃬", (firstUnit.children[0] as SemanticParagraph).text)
        assertEquals("what", (firstUnit.children[1] as SemanticParagraph).text)
        assertEquals(FontFamily.Serif, (firstUnit.children[0] as SemanticParagraph).style.spanStyle.fontFamily)
        assertEquals("\"liga\" on, \"calt\" on", (firstUnit.children[0] as SemanticParagraph).style.spanStyle.fontFeatureSettings)
        assertEquals(28.sp, (firstUnit.children[0] as SemanticParagraph).style.fontSize)
        assertTrue((firstUnit.children[0] as SemanticParagraph).spans.any { it.style.spanStyle.color == Color.Red })
    }

    @Test
    fun `nested toc lists preserve one semantic row per link`() {
        val cssRules = CssParser.parse(
            cssContent = """
                .toc-list { list-style-type: none; margin: 0; padding: 0; }
                .nested-toc-list-item { margin-left: 1em; font-size: 0.9em; }
                #toc a { color: #00b0f0; text-decoration: underline; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules
        val blocks = parse(
            html = """
                <nav id="toc" epub:type="toc"><ol class="toc-list">
                  <li><a href="cover.xhtml">Cover</a></li>
                  <li><a href="volume.xhtml">Volume 1</a><ol class="toc-list">
                    <li class="nested-toc-list-item"><a href="chapter1.xhtml">Chapter 1</a></li>
                    <li class="nested-toc-list-item"><a href="chapter2.xhtml">Chapter 2</a></li>
                  </ol></li>
                </ol></nav>
            """.trimIndent(),
            cssRules = cssRules
        )

        val list = blocks.single() as SemanticList
        assertEquals(listOf("Cover", "Volume 1", "Chapter 1", "Chapter 2"), list.items.map { it.text })
        assertTrue(list.items.all { it.markerText == "" })
        assertEquals(
            listOf("cover.xhtml", "volume.xhtml", "chapter1.xhtml", "chapter2.xhtml"),
            list.items.map { item -> item.spans.firstNotNullOf { it.linkHref } }
        )
        assertEquals(16.dp, list.items[2].style.blockStyle.margin.left)
        assertEquals(Color(0xFF00B0F0), list.items[2].spans.first { it.linkHref != null }.style.spanStyle.color)
    }

    @Test
    fun `repeated selector matching stays correct across many elements with cached evaluators`() {
        val cssRules = CssParser.parse(
            cssContent = """
                p.highlight { background: #ffff00; }
                p#special { color: #123456; }
                div > em { font-style: italic; }
                body em.bold { font-weight: bold; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false
        ).rules

        val blocks = parse(
            html = """
                <html><body>
                  <p class="highlight">One <em>italic</em></p>
                  <p id="special">Two <em class="bold">bold-italic</em></p>
                  <p>Three</p>
                  <div><p class="highlight">Four <em>nested</em></p></div>
                </body></html>
            """.trimIndent(),
            cssRules = cssRules
        )

        // Same rule set applied to 8+ elements exercises the compiled-selector reuse path;
        // matching results must be identical to uncached parsing.
        val paragraphs = blocks.filterIsInstance<SemanticParagraph>()
        assertTrue(paragraphs.isNotEmpty())
        val colored = paragraphs.filter { it.style.spanStyle.color == Color(0xFF123456) }
        assertEquals(1, colored.size)
        assertEquals("Two bold-italic", colored.single().text)
        val highlighted = blocks.filterIsInstance<SemanticParagraph>()
            .filter { it.style.blockStyle.backgroundColor == Color(0xFFFFFF00) }
        assertEquals(2, highlighted.size)
        val bold = blocks.filterIsInstance<SemanticParagraph>()
            .flatMap { it.spans }
            .filter { it.style.spanStyle.fontWeight != null }
        assertTrue(bold.isNotEmpty())
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
