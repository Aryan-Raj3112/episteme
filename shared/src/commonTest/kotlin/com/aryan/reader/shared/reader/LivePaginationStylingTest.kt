package com.aryan.reader.shared.reader

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the sonnet rendering defect (a00e164a.epub):
 * metric-sensitive book CSS (`margin-left: 10%`, `font-size: 175%`, `1em`
 * margins) was resolved once at load with nominal metrics (18sp, density 1,
 * 980x720), so on a phone the verses and headers carried a ~98pt phantom left
 * margin that narrowed the page until the header wrapped and its lines
 * collided. Android re-parses per pagination config with live metrics
 * (`BookPaginator`); pagination here rebuilds chapter blocks with the live
 * styling, and this pins the live values plus locator stability.
 */
class LivePaginationStylingTest {

    private companion object {
        const val SONNET_CSS = """
            h1, h2 { margin-bottom: 1em; text-align: center; }
            p { margin-top: 0.5em; margin-bottom: 0.5em; text-align: justify; }
            h1.chapterTitle { font-size: 175%; text-align: left; margin-left: 10%; }
            p.sonnet { margin-left: 10%; margin-bottom: 2em; }
            p.navBar { text-indent: 0px; text-align: center; }
        """
        const val SONNET_HTML = """
            <p class="navBar"><a href="p152.xhtml#audio">[Previous]</a></p>
            <h1 class="chapterTitle"><span>Sonnet CLII</span></h1>
            <p class="sonnet"><span>In loving thee thou know'st I am forsworn,</span></p>
        """
        // iPhone-class viewport: 1170x2532 physical px at 3x (390x844 pt).
        val PHONE = Constraints(maxWidth = 1170, maxHeight = 2532)
    }

    private fun liveStyling(userFontSp: Float = 20f): LivePaginationStyling {
        val book = SharedEpubBook(
            id = "sonnet",
            fileName = "sonnet.epub",
            title = "Sonnets",
            chapters = emptyList(),
            css = mapOf("Styles/style.css" to SONNET_CSS)
        )
        return livePaginationStyling(
            book = book,
            settings = ReaderSettings(fontSize = userFontSp.toInt(), readingMode = ReaderReadingMode.PAGINATED),
            viewport = ReaderViewportSpec(widthPx = 1170, heightPx = 2532),
            geometry = MeasuredPageGeometry(pageWidthPx = 1170, pageHeightPx = 2532),
            density = 3f
        )
    }

    private fun liveHeader(): SemanticHeader {
        val chapter = SharedEpubChapter(
            id = "ch",
            title = "Sonnet CLII",
            plainText = "Sonnet CLII",
            htmlContent = SONNET_HTML
        )
        return restyleChapterBlocksForPagination(chapter, liveStyling())
            .filterIsInstance<SemanticHeader>().single()
    }

    @Test
    fun `live percent margin resolves against the live viewport`() {
        // 10% of 390pt, not 10% of the nominal 980px load baseline (98.dp).
        assertEquals(39.dp, liveHeader().style.blockStyle.margin.left)
    }

    @Test
    fun `live em sizes resolve against the live native reader size`() {
        // 175% of the native reader size (stored 20 -> 16 * (20/18) native, matching
        // Android's bodyLarge base), not of the nominal 18sp load baseline.
        val native20 = 16f * (20f / 18f)
        assertEquals((native20 * 1.75f).sp, liveHeader().style.fontSize)
        // UA `h1 { margin-bottom: 1em }` at the live native size.
        assertEquals(native20.dp, liveHeader().style.blockStyle.margin.bottom)
    }

    @Test
    fun `nominal load styling keeps the old baseline`() {
        val blocks = sharedEpubHtmlToSemanticBlocks(
            SONNET_HTML,
            parseSharedSemanticCss(mapOf("Styles/style.css" to SONNET_CSS))
        )
        val header = blocks.filterIsInstance<SemanticHeader>().single()
        assertEquals(98.dp, header.style.blockStyle.margin.left)
        assertEquals(31.5.sp, header.style.fontSize)
    }

    @Test
    fun `live restyle keeps text cfis and offsets stable`() {
        val chapter = SharedEpubChapter(
            id = "ch",
            title = "Sonnet CLII",
            plainText = "Sonnet CLII",
            htmlContent = SONNET_HTML
        )
        val load = sharedEpubHtmlToSemanticBlocks(
            SONNET_HTML,
            parseSharedSemanticCss(mapOf("Styles/style.css" to SONNET_CSS))
        )
        val live = restyleChapterBlocksForPagination(chapter, liveStyling())
        assertEquals(load.size, live.size)
        load.zip(live).forEach { (a, b) ->
            assertEquals(a::class, b::class)
            assertEquals(a.blockIndex, b.blockIndex)
        }
        val loadText = load.filterIsInstance<SemanticHeader>().single()
        val liveText = live.filterIsInstance<SemanticHeader>().single()
        assertEquals(loadText.text, liveText.text)
        assertEquals(loadText.cfi, liveText.cfi)
        assertEquals(loadText.startCharOffsetInSource, liveText.startCharOffsetInSource)
    }

    @Test
    fun `live restyle falls back to stored blocks without html`() {
        val stored = listOf(
            SemanticParagraph(
                text = "Cached",
                spans = emptyList(),
                style = com.aryan.reader.paginatedreader.CssStyle(),
                elementId = null,
                cfi = null
            )
        )
        val chapter = SharedEpubChapter(
            id = "ch",
            title = "Cached",
            plainText = "Cached",
            semanticBlocks = stored,
            htmlContent = ""
        )
        assertEquals(stored, restyleChapterBlocksForPagination(chapter, liveStyling()))
    }

    @Test
    fun `text transform applies at parse like the jvm parser`() {
        val css = mapOf("b.css" to "h1.shout { text-transform: uppercase; } p.whisper { text-transform: lowercase; } p.title { text-transform: capitalize; }")
        val blocks = sharedEpubHtmlToSemanticBlocks(
            """<h1 class="shout">Sonnet clii</h1><p class="whisper">LOUD NOISE</p><p class="title">hello brave world</p>""",
            parseSharedSemanticCss(css)
        )
        val header = blocks.filterIsInstance<SemanticHeader>().single()
        assertEquals("SONNET CLII", header.text)
        val paras = blocks.filterIsInstance<SemanticParagraph>()
        assertEquals("loud noise", paras[0].text)
        assertEquals("Hello Brave World", paras[1].text)
    }

    @Test
    fun `text transform keeps source offsets aligned`() {
        val css = mapOf("b.css" to "p.shout { text-transform: uppercase; }")
        val plain = sharedEpubHtmlToSemanticBlocks("<p>Hello</p>", SharedSemanticCssSnapshot.EMPTY)
            .filterIsInstance<SemanticParagraph>().single()
        val styled = sharedEpubHtmlToSemanticBlocks(
            """<p class="shout">Hello</p>""",
            parseSharedSemanticCss(css)
        ).filterIsInstance<SemanticParagraph>().single()
        assertEquals(plain.cfi, styled.cfi)
        assertEquals(plain.startCharOffsetInSource, styled.startCharOffsetInSource)
        assertEquals(plain.text.length, styled.text.length)
        assertTrue(styled.text == "HELLO")
    }
}
