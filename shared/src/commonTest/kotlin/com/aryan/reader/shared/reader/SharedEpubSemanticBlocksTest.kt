package com.aryan.reader.shared.reader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedEpubSemanticBlocksTest {

    private fun blocks(html: String) = sharedEpubHtmlToSemanticBlocks(html)

    private fun paragraph(text: String, cfi: String? = null, startOffset: Int? = null): SemanticParagraph =
        blocks("<p>$text</p>").single { it is SemanticParagraph && it.text == text }
            .let { it as SemanticParagraph }
            .also {
                if (cfi != null) assertEquals(cfi, it.cfi)
                if (startOffset != null) assertEquals(startOffset, it.startCharOffsetInSource)
            }

    @Test
    fun `paragraphs get android style cfi paths and plain text aligned offsets`() {
        val result = blocks("<p>Hello</p><p>World</p>")
        assertEquals(2, result.size)
        val first = result[0] as SemanticParagraph
        val second = result[1] as SemanticParagraph
        assertEquals("Hello", first.text)
        assertEquals("/4/2", first.cfi)
        assertEquals(0, first.startCharOffsetInSource)
        assertEquals(0, first.blockIndex)
        assertEquals("World", second.text)
        assertEquals("/4/4", second.cfi)
        assertEquals(6, second.startCharOffsetInSource)
        assertEquals(1, second.blockIndex)
    }

    @Test
    fun `single top level element is unwrapped and keeps document cfi`() {
        paragraph("Hello", cfi = "/4/2", startOffset = 0)
    }

    @Test
    fun `paragraph inside wrapper keeps nested cfi`() {
        val result = blocks("<section><p>One</p></section>")
        val first = result[0] as SemanticParagraph
        assertEquals("/4/2/2", first.cfi)
    }

    @Test
    fun `siblings under a wrapper keep nested cfi paths`() {
        val result = blocks("<section><p>One</p><p>Two</p></section>")
        assertEquals("/4/2/2", (result[0] as SemanticParagraph).cfi)
        assertEquals("/4/2/4", (result[1] as SemanticParagraph).cfi)
    }

    @Test
    fun `headers carry levels and cfi`() {
        val result = blocks("<h1>Title</h1><h3>Sub</h3>")
        val title = result[0] as SemanticHeader
        assertEquals(1, title.level)
        assertEquals("Title", title.text)
        assertEquals("/4/2", title.cfi)
        assertEquals(0, title.startCharOffsetInSource)
        val sub = result[1] as SemanticHeader
        assertEquals(3, sub.level)
        assertEquals("/4/4", sub.cfi)
        assertEquals(6, sub.startCharOffsetInSource)
    }

    @Test
    fun `unordered list items get bullet markers and cfi`() {
        val result = blocks("<ul><li>One</li><li>Two</li></ul>")
        val list = result[0] as SemanticList
        assertEquals(false, list.isOrdered)
        assertEquals("/4/2", list.cfi)
        assertEquals(0, list.blockIndex)
        assertEquals(2, list.items.size)
        val one = list.items[0]
        assertEquals("One", one.text)
        assertEquals("•", one.markerText)
        assertEquals("/4/2/2", one.cfi)
        assertEquals(1, one.blockIndex)
        val two = list.items[1]
        assertEquals("Two", two.text)
        assertEquals("/4/2/4", two.cfi)
        assertEquals(2, two.blockIndex)
    }

    @Test
    fun `ordered list with start attribute numbers sequentially`() {
        val result = blocks("<ol start=\"5\"><li>a</li><li>b</li></ol>")
        val list = result[0] as SemanticList
        assertTrue(list.isOrdered)
        assertEquals("5.", list.items[0].markerText)
        assertEquals("6.", list.items[1].markerText)
    }

    @Test
    fun `nested lists flatten into one list with nested markers`() {
        val result = blocks("<ol><li>a<ul><li>b</li></ul></li><li>c</li></ol>")
        val list = result[0] as SemanticList
        assertEquals(3, list.items.size)
        assertEquals("1.", list.items[0].markerText)
        assertEquals("a", list.items[0].text)
        assertEquals("•", list.items[1].markerText)
        assertEquals("b", list.items[1].text)
        assertEquals("/4/2/2/4/2", list.items[1].cfi)
        assertEquals("2.", list.items[2].markerText)
        assertEquals("c", list.items[2].text)
        assertEquals("/4/2/4", list.items[2].cfi)
    }

    @Test
    fun `image block resolves data uri path and intrinsic size`() {
        val result = blocks("<img src=\"data:image/png;base64,AAA\" alt=\"pic\" width=\"120\" height=\"80\"/>")
        val image = result[0] as SemanticImage
        assertEquals("data:image/png;base64,AAA", image.path)
        assertEquals("pic", image.altText)
        assertEquals(120f, image.intrinsicWidth)
        assertEquals(80f, image.intrinsicHeight)
        assertEquals("/4/2", image.cfi)
        assertEquals(0, image.blockIndex)
    }

    @Test
    fun `br inside paragraph becomes newline spacer semantics`() {
        val result = blocks("<p>Line1<br/>Line2</p>")
        val p = result[0] as SemanticParagraph
        assertEquals("Line1\nLine2", p.text)
    }

    @Test
    fun `hr becomes non explicit line break spacer`() {
        val result = blocks("<p>A</p><hr/><p>B</p>")
        assertEquals(3, result.size)
        val spacer = result[1] as SemanticSpacer
        assertEquals(false, spacer.isExplicitLineBreak)
        assertEquals("/4/4", spacer.cfi)
        assertEquals(1, spacer.blockIndex)
    }

    @Test
    fun `table cells carry header flags and colspans`() {
        val result = blocks("<table><tr><th>A</th><td colspan=\"2\">B</td></tr></table>")
        val table = result[0] as SemanticTable
        assertEquals("/4/2", table.cfi)
        assertEquals(1, table.rows.size)
        val cells = table.rows[0]
        assertEquals(2, cells.size)
        val headerCell = cells[0]
        assertTrue(headerCell.isHeader)
        assertEquals(1, headerCell.colspan)
        val headerParagraph = headerCell.content.single { it is SemanticParagraph } as SemanticParagraph
        assertEquals("A", headerParagraph.text)
        assertEquals("/4/2/2/2", headerParagraph.cfi)
        val bodyCell = cells[1]
        assertEquals(false, bodyCell.isHeader)
        assertEquals(2, bodyCell.colspan)
        val bodyParagraph = bodyCell.content.single { it is SemanticParagraph } as SemanticParagraph
        assertEquals("B", bodyParagraph.text)
        assertEquals("/4/2/2/4", bodyParagraph.cfi)
    }

    @Test
    fun `inline formatting produces spans with exact local ranges`() {
        val result = blocks("<p>Hello <b>bold</b> and <i>italic</i></p>")
        val p = result[0] as SemanticParagraph
        assertEquals("Hello bold and italic", p.text)
        val bold = p.spans.single { it.style.spanStyle.fontWeight == FontWeight.Bold }
        assertEquals(6, bold.start)
        assertEquals(10, bold.end)
        assertEquals("b", bold.tag)
        val italic = p.spans.single { it.style.spanStyle.fontStyle == FontStyle.Italic }
        assertEquals(15, italic.start)
        assertEquals(21, italic.end)
        assertEquals("i", italic.tag)
    }

    @Test
    fun `links carry href and element id`() {
        val result = blocks("<p><a href=\"ch2.xhtml#x\" id=\"lnk\">link</a></p>")
        val p = result[0] as SemanticParagraph
        val link = p.spans.single { it.linkHref != null }
        assertEquals("ch2.xhtml#x", link.linkHref)
        assertEquals("a", link.tag)
        assertEquals("lnk", link.elementId)
    }

    @Test
    fun `container text flushes to paragraph with container cfi and id`() {
        val result = blocks("<div id=\"ch1\">A<p>B</p>C</div>")
        assertEquals(3, result.size)
        val a = result[0] as SemanticParagraph
        assertEquals("A", a.text)
        assertEquals("ch1", a.elementId)
        assertEquals("/4/2", a.cfi)
        assertEquals(0, a.startCharOffsetInSource)
        val b = result[1] as SemanticParagraph
        assertEquals("B", b.text)
        assertNull(b.elementId)
        assertEquals("/4/2/4", b.cfi)
        assertEquals(2, b.startCharOffsetInSource)
        val c = result[2] as SemanticParagraph
        assertEquals("C", c.text)
        assertNull(c.elementId)
        assertEquals("/4/2", c.cfi)
        assertEquals(4, c.startCharOffsetInSource)
    }

    @Test
    fun `text nodes between blocks shift cfi indices`() {
        val result = blocks("<div><p>A</p>tail<p>B</p></div>")
        assertEquals(3, result.size)
        assertEquals("/4/2/2", (result[0] as SemanticParagraph).cfi)
        assertEquals("tail", (result[1] as SemanticParagraph).text)
        assertEquals("/4/2", (result[1] as SemanticParagraph).cfi)
        assertEquals("/4/2/6", (result[2] as SemanticParagraph).cfi)
    }

    @Test
    fun `pre blocks preserve whitespace`() {
        val result = blocks("<pre>a  b\n c</pre>")
        val p = result[0] as SemanticParagraph
        assertEquals("a  b\n c", p.text)
        assertEquals("pre-wrap", p.style.whiteSpace)
        assertEquals("/4/2", p.cfi)
    }

    @Test
    fun `math blocks fall back to mathjax placeholder semantics`() {
        val result = blocks("<math><mi>x</mi><mo>+</mo><mn>1</mn></math>")
        val math = result[0] as SemanticMath
        assertEquals("x+1", math.altText)
        assertEquals("/4/2", math.cfi)
        assertEquals(0, math.blockIndex)
    }

    @Test
    fun `blank paragraphs are skipped`() {
        val result = blocks("<p> </p><p>X</p>")
        assertEquals(1, result.size)
        assertEquals("X", (result[0] as SemanticParagraph).text)
        assertEquals(0, result[0].blockIndex)
    }

    @Test
    fun `entities are decoded before offset mapping`() {
        val result = blocks("<p>A &amp; B &#65;</p>")
        assertEquals("A & B A", (result[0] as SemanticParagraph).text)
    }

    @Test
    fun `normalizeWithSourceMapping matches normalizeEpubWhitespace`() {
        val inputs = listOf(
            "Hello  World",
            "a\n b",
            "a \nb",
            "a\n\n  b",
            "a  \n  b",
            "a \t b",
            "\u0000x",
            "  x ",
            "a \n\n b",
            "a\n\n\nb",
            "a\r\nb",
            "a\u000B\u000Cb"
        )
        inputs.forEach { input ->
            val (normalized, mapping) = normalizeWithSourceMapping(input)
            assertEquals(input.normalizeEpubWhitespace(), normalized, "input: [$input]")
            assertEquals(normalized.length, mapping.size, "input: [$input]")
            mapping.forEachIndexed { index, source ->
                assertTrue(source in input.indices, "input: [$input] index $index -> $source")
            }
        }
    }

    @Test
    fun `spans adjust to collapsed text ranges`() {
        val result = blocks("<p>a <b>b</b>  c</p>")
        val p = result[0] as SemanticParagraph
        assertEquals("a b c", p.text)
        val bold = p.spans.single { it.style.spanStyle.fontWeight == FontWeight.Bold }
        assertEquals(2, bold.start)
        assertEquals(3, bold.end)
    }
}