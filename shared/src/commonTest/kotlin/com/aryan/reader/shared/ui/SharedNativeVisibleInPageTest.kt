package com.aryan.reader.shared.ui

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.EpubChapterTextIndex
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Which page shows a highlight has to be the same question the painter asks.
 *
 * Scoping used to be answered from the locator alone — chapter plus a page span that is a min..max
 * across every block on the page — while painting went through the resolver. A highlight could
 * therefore be in scope on a page that had no range for it, and one covering two paragraphs could be
 * in scope on neither. Scoping now defers to the resolver, so the two cannot disagree.
 */
class SharedNativeVisibleInPageTest {

    private val repeated = "the same sentence appears more than once in this chapter"

    /** Every block reports start 0, exactly as a parsed chapter does. */
    private fun chapter(): List<SemanticTextBlock> = listOf(
        paragraph(0, "alpha beta gamma"),
        paragraph(1, repeated),
        paragraph(2, "delta epsilon zeta"),
        paragraph(3, repeated)
    )

    private fun paragraph(blockIndex: Int, text: String) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = "/4/${blockIndex * 2}",
        startCharOffsetInSource = 0,
        blockIndex = blockIndex
    )

    private fun index() = assertNotNull(EpubChapterTextIndex.of(0, chapter()))

    private fun page(pageIndex: Int, vararg blockIndices: Int) = ReaderPage(
        pageIndex = pageIndex,
        chapterIndex = 0,
        chapterTitle = "Chapter",
        text = blockIndices.joinToString(" ") { chapter()[it].text },
        startOffset = 0,
        endOffset = 999,
        semanticBlocks = blockIndices.map { chapter()[it] }
    )

    private fun quoteOnly(text: String, id: String, chapterIndex: Int = 0) = UserHighlight(
        id = id,
        cfi = "0:$chapterIndex:2:1:5:3",
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = chapterIndex,
        locator = ReaderLocator.fromLegacy(
            chapterIndex = chapterIndex,
            cfi = "0:$chapterIndex:2:1:5:3",
            textQuote = text
        )
    )

    @Test
    fun `a repeated sentence shows on the page holding the block it resolved to`() {
        val highlight = quoteOnly(repeated, id = "repeat")

        // Blocks 1 and 3 hold this verbatim. The resolver commits to the first, so only that page
        // shows it — the other page painting a copy of the same words was the ghosting bug.
        assertEquals(
            listOf("repeat"),
            listOf(highlight).visibleInPage(page(0, 1), index()).map { it.id }
        )
        assertTrue(listOf(highlight).visibleInPage(page(1, 3), index()).isEmpty())
    }

    @Test
    fun `a highlight spanning two paragraphs is in scope on both of their pages`() {
        val highlight = quoteOnly("gamma ${repeated.take(18)}", id = "multi")

        // Neither block contains the quote on its own, so no per-page text match could scope this.
        val first = listOf(highlight).visibleInPage(page(0, 0), index())
        val second = listOf(highlight).visibleInPage(page(1, 1), index())

        assertEquals(listOf("multi"), first.map { it.id })
        assertEquals(listOf("multi"), second.map { it.id })
    }

    @Test
    fun `a page whose blocks the highlight misses does not show it`() {
        val highlight = quoteOnly("delta epsilon", id = "d")

        assertTrue(listOf(highlight).visibleInPage(page(0, 0), index()).isEmpty())
        assertTrue(listOf(highlight).visibleInPage(page(1, 3), index()).isEmpty())
    }

    @Test
    fun `a highlight from another chapter is never in scope`() {
        val highlight = quoteOnly(repeated, id = "other", chapterIndex = 1)

        assertTrue(listOf(highlight).visibleInPage(page(0, 1), index()).isEmpty())
    }

    @Test
    fun `a page with no blocks keeps the locator rules rather than dropping every highlight`() {
        // The paginator sometimes emits a page it could not split into blocks. The resolver places
        // highlights per block, so on such a page it can answer nothing and would silently drop
        // them; scoping falls back to the locator rules, which is what painted them before.
        val byPageNumber = UserHighlight(
            id = "byPage",
            cfi = "desktop:0:3:9:14",
            text = repeated,
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                textQuote = repeated,
                cfi = "desktop:0:3:9:14"
            )
        )
        val flat = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "Chapter",
            text = repeated,
            startOffset = 0,
            endOffset = repeated.length
        )

        assertEquals(
            listOf("byPage"),
            listOf(byPageNumber).visibleInPage(flat, index()).map { it.id }
        )
    }

    @Test
    fun `scoping and painting agree for every block of the chapter`() {
        val highlights = listOf(
            quoteOnly(repeated, id = "repeat"),
            quoteOnly("gamma ${repeated.take(18)}", id = "multi"),
            quoteOnly("delta epsilon", id = "d")
        )
        val index = index()

        // The invariant, stated directly: anything scoping admits can paint. Checking every block
        // catches a scoping rule that admits a page the painter would leave blank.
        for (blockIndex in 0..3) {
            val onPage = highlights.visibleInPage(page(0, blockIndex), index)
            val paintable = onPage.filter { highlight ->
                val block = chapter()[blockIndex]
                sharedNativeHighlightRangeForBlock(
                    highlight = highlight,
                    blockCfi = block.cfi,
                    textStartOffset = 0,
                    textLength = block.text.length,
                    text = block.text,
                    blockIndex = blockIndex,
                    chapterTextIndex = index
                ) != null
            }
            assertEquals(
                onPage.map { it.id }.sorted(),
                paintable.map { it.id }.sorted(),
                "block $blockIndex: scoped highlights that cannot paint"
            )
        }
    }

    @Test
    fun `without an index the locator rules still scope a page`() {
        // Desktop has no index. It must keep scoping the way it did rather than show nothing.
        val located = UserHighlight(
            id = "desktop",
            cfi = "/4/0:6",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                blockIndex = 0,
                charOffset = 6,
                textQuote = "beta",
                cfi = "/4/0:6"
            )
        )

        assertEquals(listOf("desktop"), listOf(located).visibleInPage(page(0, 0)).map { it.id })
    }
}