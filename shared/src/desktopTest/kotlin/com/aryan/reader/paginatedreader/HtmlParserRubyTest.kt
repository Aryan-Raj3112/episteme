package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlParserRubyTest {

    @Test
    fun `ruby base stays in text and reading becomes annotation`() {
        val blocks = parse("<p>あくびが<ruby><rb>出</rb><rt>で</rt></ruby>る</p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("あくびが出る", paragraph.text)
        assertEquals(listOf(SemanticRuby(4, 5, "で")), paragraph.rubies)
    }

    @Test
    fun `sequential rb rt pairs attach one by one`() {
        val blocks = parse("<p><ruby><rb>昼</rb><rt>ひる</rt><rb>下</rb><rt>さ</rt></ruby>がり</p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("昼下がり", paragraph.text)
        assertEquals(
            listOf(SemanticRuby(0, 1, "ひる"), SemanticRuby(1, 2, "さ")),
            paragraph.rubies
        )
    }

    @Test
    fun `implicit base without rb is supported`() {
        val blocks = parse("<p><ruby>漢字<rt>かんじ</rt></ruby></p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("漢字", paragraph.text)
        assertEquals(listOf(SemanticRuby(0, 2, "かんじ")), paragraph.rubies)
    }

    @Test
    fun `rp fallback parens are skipped`() {
        val blocks = parse("<p><ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp></ruby></p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("漢", paragraph.text)
        assertEquals(listOf(SemanticRuby(0, 1, "かん")), paragraph.rubies)
    }

    @Test
    fun `base without reading stays plain text`() {
        val blocks = parse("<p><ruby>単体</ruby>だよ</p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals("単体だよ", paragraph.text)
        assertTrue(paragraph.rubies.isEmpty())
    }

    @Test
    fun `ruby spans still mark base ranges`() {
        val blocks = parse("<p><ruby><rb>出</rb><rt>で</rt></ruby>る</p>")
        val paragraph = blocks.single() as SemanticParagraph
        val tags = paragraph.spans.map { it.tag }.toSet()
        assertTrue("rb" in tags)
        assertTrue("ruby" in tags)
        assertTrue("rt" !in tags)
    }

    @Test
    fun `author rt font size becomes per-ruby scale`() {
        val blocks = parse(
            "<p>あ<ruby><rb>漢</rb><rt>かん</rt></ruby>い</p>",
            css = "rt { font-size: 70%; }"
        )
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(1, paragraph.rubies.size)
        val ruby = paragraph.rubies.single()
        assertEquals(1, ruby.baseStart)
        assertEquals(2, ruby.baseEnd)
        assertEquals("かん", ruby.reading)
        assertEquals(0.7f, ruby.readingScale ?: Float.NaN, 0.0001f)
    }

    @Test
    fun `missing rt sizing leaves scale null for browser default`() {
        val blocks = parse("<p>あ<ruby><rb>漢</rb><rt>かん</rt></ruby>い</p>")
        val paragraph = blocks.single() as SemanticParagraph
        assertEquals(listOf(SemanticRuby(1, 2, "かん", null)), paragraph.rubies)
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
