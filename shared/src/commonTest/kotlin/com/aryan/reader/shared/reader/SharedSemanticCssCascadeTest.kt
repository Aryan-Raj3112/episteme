package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android-parity guards for book-CSS styling of the shared semantic model.
 * The iOS paginated path used to ignore book CSS entirely; these pin the
 * cascade (specificity, combinators, inline styles, display:none) and the
 * locator stability (text, CFIs, source offsets) the pagination, search and
 * highlight layers depend on.
 */
class SharedSemanticCssCascadeTest {

    private fun styledBlocks(html: String, vararg cssFiles: Pair<String, String>) =
        sharedEpubHtmlToSemanticBlocks(html, parseSharedSemanticCss(mapOf(*cssFiles)))

    private fun paragraph(html: String, vararg cssFiles: Pair<String, String>): SemanticParagraph {
        val css = if (cssFiles.isEmpty()) SharedSemanticCssSnapshot.EMPTY else parseSharedSemanticCss(mapOf(*cssFiles))
        return sharedEpubHtmlToSemanticBlocks(html, css)
            .single { it is SemanticParagraph } as SemanticParagraph
    }

    @Test
    fun `class selector styles paragraph alignment size and margins`() {
        val block = paragraph(
            """<p class="intro">Hello</p>""",
            "book.css" to ".intro { text-align: center; font-size: 20px; margin-top: 10px; }"
        )

        assertEquals("Hello", block.text)
        assertEquals(TextAlign.Center, block.style.paragraphStyle.textAlign)
        assertEquals(20.sp, block.style.fontSize)
        assertEquals(10.dp, block.style.blockStyle.margin.top)
    }

    @Test
    fun `id beats class beats tag and later source order breaks ties`() {
        val block = paragraph(
            """<p id="lead" class="intro">Hello</p>""",
            "book.css" to """
                p { text-align: left; }
                .intro { text-align: center; }
                #lead { text-align: right; }
                p { margin-top: 4px; }
                p { margin-top: 9px; }
            """.trimIndent()
        )

        assertEquals(TextAlign.End, block.style.paragraphStyle.textAlign)
        assertEquals(9.dp, block.style.blockStyle.margin.top)
    }

    @Test
    fun `descendant and child combinators match`() {
        val descendant = paragraph(
            """<section class="chapter"><div><p>Deep</p></div></section>""",
            "book.css" to ".chapter p { text-align: right; }"
        )
        assertEquals(TextAlign.End, descendant.style.paragraphStyle.textAlign)

        val directChild = paragraph(
            """<section class="chapter"><p>Direct</p></section>""",
            "book.css" to ".chapter > p { text-align: center; }"
        )
        assertEquals(TextAlign.Center, directChild.style.paragraphStyle.textAlign)

        val notDirectChild = paragraph(
            """<section class="chapter"><div><p>Deep</p></div></section>""",
            "book.css" to ".chapter > p { text-align: center; }"
        )
        // No author alignment: the shared fallback keeps the default start.
        assertEquals(TextAlign.Unspecified, notDirectChild.style.paragraphStyle.textAlign)
    }

    @Test
    fun `inline style attribute beats stylesheet rules`() {
        val block = paragraph(
            """<p class="intro" style="text-align: justify;">Hello</p>""",
            "book.css" to ".intro { text-align: center; } #unused { text-align: left; }"
        )

        assertEquals(TextAlign.Justify, block.style.paragraphStyle.textAlign)
    }

    @Test
    fun `child combinator with surrounding spaces stays a child match`() {
        val sibling = styledBlocks(
            """<div><p>One</p></div><p>Two</p>""",
            "book.css" to "div + p { text-align: center; }"
        ).filterIsInstance<SemanticParagraph>()

        assertEquals(TextAlign.Unspecified, sibling[0].style.paragraphStyle.textAlign)
        assertEquals(TextAlign.Center, sibling[1].style.paragraphStyle.textAlign)
    }

    @Test
    fun `author rules win ties against the user agent sheet`() {
        val uaOnly = sharedEpubHtmlToSemanticBlocks(
            "<p>Plain</p>",
            parseSharedSemanticCss(emptyMap())
        ).single() as SemanticParagraph
        // UA `p { margin-top: 1em }` at the nominal 18sp baseline.
        assertEquals(18.dp, uaOnly.style.blockStyle.margin.top)

        val overridden = paragraph(
            "<p>Plain</p>",
            "book.css" to "p { margin-top: 9px; }"
        )
        assertEquals(9.dp, overridden.style.blockStyle.margin.top)
    }

    @Test
    fun `display none drops the block but keeps sibling locators stable`() {
        val plain = sharedEpubHtmlToSemanticBlocks(
            "<p>One</p><p>Two</p>",
            SharedSemanticCssSnapshot.EMPTY
        ).filterIsInstance<SemanticParagraph>()
        val styled = styledBlocks(
            """<p>One</p><p class="hide">Two</p>""",
            "book.css" to ".hide { display: none; }"
        ).filterIsInstance<SemanticParagraph>()

        assertEquals(1, styled.size)
        assertEquals("One", styled.single().text)
        assertEquals(plain[0].cfi, styled[0].cfi)
        assertEquals(plain[0].startCharOffsetInSource, styled[0].startCharOffsetInSource)
        assertEquals(plain[0].blockIndex, styled[0].blockIndex)
    }

    @Test
    fun `styled build keeps text offsets and cfis of unstyled build`() {
        val html = "<h1>Title</h1><p>Hello <b>bold</b> world</p>"
        val plain = sharedEpubHtmlToSemanticBlocks(html, SharedSemanticCssSnapshot.EMPTY)
        val styled = styledBlocks(
            html,
            "book.css" to "h1 { font-size: 30px; } p { margin-top: 12px; } b { color: #ff0000; }"
        )

        assertEquals(plain.map { it.pageTextForCssStability() }, styled.map { it.pageTextForCssStability() })
        assertEquals(plain.map { it.blockCfiForCssStability() }, styled.map { it.blockCfiForCssStability() })
    }

    @Test
    fun `span keeps tag bold while author color applies and normal wins`() {
        val colored = paragraph(
            "<p>Hello <b>bold</b></p>",
            "book.css" to "b { color: #ff0000; }"
        )
        val boldSpan = colored.spans.single { it.tag == "b" }
        assertEquals(FontWeight.Bold, boldSpan.style.spanStyle.fontWeight)
        assertEquals(Color(0xFFFF0000), boldSpan.style.spanStyle.color)

        val normalized = paragraph(
            "<p>Hello <b>bold</b></p>",
            "book.css" to "b { font-weight: normal; }"
        )
        val normalSpan = normalized.spans.single { it.tag == "b" }
        assertEquals(FontWeight.Normal, normalSpan.style.spanStyle.fontWeight)
    }

    @Test
    fun `class styled span carries color without forcing tag semantics`() {
        val block = paragraph(
            """<p>Hello <span class="red">red</span></p>""",
            "book.css" to ".red { color: #00ff00; }"
        )

        val span: SemanticSpan = block.spans.single { it.tag == "span" }
        assertEquals(Color(0xFF00FF00), span.style.spanStyle.color)
    }

    @Test
    fun `hidden inline subtree contributes no text`() {
        val block = paragraph(
            """<p>Visible <span class="hide">hidden</span> tail</p>""",
            "book.css" to ".hide { display: none; }"
        )

        assertEquals("Visible tail", block.text)
        assertTrue(block.spans.none { it.tag == "span" })
    }

    @Test
    fun `nested em font size resolves against the parent`() {
        val block = paragraph(
            """<div class="big"><p>Size <span class="bigger">up</span></p></div>""",
            "book.css" to ".big { font-size: 20px; } .bigger { font-size: 1.5em; }"
        )

        assertEquals(20.sp, block.style.fontSize)
        val span = block.spans.single { it.tag == "span" }
        assertEquals(30.sp, span.style.fontSize)
    }

    @Test
    fun `multiple stylesheets merge and font faces are collected`() {
        val snapshot = parseSharedSemanticCss(
            mapOf(
                "a.css" to ".intro { text-align: center; }",
                "b.css" to "@font-face { font-family: 'Book Serif'; src: url('serif.ttf'); }"
            )
        )

        assertTrue(snapshot.rules.toFlatList().isNotEmpty())
        assertEquals(listOf("book serif"), snapshot.fontFaces.map { it.fontFamily })
        val block = sharedEpubHtmlToSemanticBlocks("""<p class="intro">Hi</p>""", snapshot)
            .single() as SemanticParagraph
        assertEquals(TextAlign.Center, block.style.paragraphStyle.textAlign)
    }

    private fun com.aryan.reader.paginatedreader.SemanticBlock.pageTextForCssStability(): String {
        return when (this) {
            is SemanticParagraph -> "$text|$cfi|$startCharOffsetInSource|$blockIndex"
            is com.aryan.reader.paginatedreader.SemanticHeader -> "$text|$cfi|$startCharOffsetInSource|$blockIndex"
            else -> "${this::class.simpleName}|$blockIndex"
        }
    }

    private fun com.aryan.reader.paginatedreader.SemanticBlock.blockCfiForCssStability(): String? {
        return when (this) {
            is SemanticParagraph -> cfi
            is com.aryan.reader.paginatedreader.SemanticHeader -> cfi
            else -> null
        }
    }
}
