package com.aryan.reader.shared.ui

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.EpubChapterTextIndex
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The shared placement path must reach the same answer as the Android one.
 *
 * Before the resolver was threaded through, the shared path intersected a locator's offsets with a
 * block's reported offsets — two different coordinate spaces, because block offsets are
 * element-relative — and fell back to matching text per block, which paints a repeated sentence on
 * every block holding a copy. Both are exercised below through the public entry point, so the tests
 * pin the behaviour rather than the plumbing.
 */
class SharedNativeHighlightPlacementParityTest {

    private val filler = "keeps the document tall enough for scroll and pagination tests"

    /** Every block reports start 0, exactly as a parsed chapter does. */
    private fun chapter(): List<SemanticTextBlock> = listOf(
        paragraph(0, "alpha beta gamma"),
        paragraph(1, filler),
        paragraph(2, "delta epsilon zeta"),
        paragraph(3, filler)
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

    /** Offset of [blockIndex]'s text in the laid-out chapter, blocks one space apart. */
    private fun laidOutStart(blockIndex: Int): Int =
        chapter().take(blockIndex).sumOf { it.text.length + 1 }

    private fun quoteOnly(text: String, id: String = "q") = UserHighlight(
        id = id,
        cfi = "0:2:1:5:3",
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        locator = ReaderLocator.fromLegacy(chapterIndex = 0, cfi = "0:2:1:5:3", textQuote = text)
    )

    private fun rangeIn(
        highlight: UserHighlight,
        blockIndex: Int,
        blockText: String
    ) = sharedNativeHighlightRangeForBlock(
        highlight = highlight,
        blockCfi = "/4/${blockIndex * 2}",
        textStartOffset = 0,
        textLength = blockText.length,
        text = blockText,
        blockIndex = blockIndex,
        chapterTextIndex = index()
    )

    @Test
    fun `a repeated sentence is placed in exactly one block`() {
        val highlight = quoteOnly(filler)

        val first = rangeIn(highlight, 1, filler)
        val second = rangeIn(highlight, 3, filler)

        // Block 1 and block 3 hold this sentence verbatim. Painting both is the ghosting bug; the
        // resolver commits to the first occurrence, so only that block gets a range.
        assertEquals(0, first?.start)
        assertEquals(filler.length, first?.end)
        assertNull(second)
    }

    @Test
    fun `a selection spanning two paragraphs gets a range in each`() {
        val blocks = chapter()
        val quote = "gamma ${filler.take(20)}"
        val highlight = quoteOnly(quote, id = "multi")

        val head = rangeIn(highlight, 0, blocks[0].text)
        val tail = rangeIn(highlight, 1, blocks[1].text)

        // Per-block matching could never do this: no single block contains the quote.
        assertEquals(11, head?.start)
        assertEquals(16, head?.end)
        assertEquals(0, tail?.start)
        assertEquals(20, tail?.end)
    }

    @Test
    fun `a repaired locator is placed by its offsets without a text search`() {
        val blocks = chapter()
        // Offsets from the chapter layout, which is what the repair writes.
        val start = laidOutStart(2)
        val repaired = quoteOnly("delta epsilon", id = "fixed").let {
            it.copy(
                locator = it.locator.copy(
                    startOffset = start,
                    endOffset = start + "delta epsilon".length,
                    blockIndex = 2
                )
            )
        }

        val range = rangeIn(repaired, 2, blocks[2].text)

        assertEquals(0, range?.start)
        assertEquals("delta epsilon".length, range?.end)
    }

    @Test
    fun `a stale locator is not trusted when the chapter text disagrees`() {
        val blocks = chapter()
        // Broken-space offsets: they point at the top of the chapter, not at the selected text.
        val stale = quoteOnly("delta epsilon", id = "stale").let {
            it.copy(locator = it.locator.copy(startOffset = 0, endOffset = 13, blockIndex = 2))
        }

        val range = rangeIn(stale, 2, blocks[2].text)

        // Relocated by text, so the highlight lands on the words rather than at offset 0.
        assertEquals(0, range?.start)
        assertEquals("delta epsilon".length, range?.end)
    }

    @Test
    fun `a block the highlight does not touch gets no range`() {
        val blocks = chapter()

        assertNull(rangeIn(quoteOnly(filler), 2, blocks[2].text))
        assertNull(rangeIn(quoteOnly(filler), 0, blocks[0].text))
    }

    @Test
    fun `ranges are clamped to the block rather than spilling past it`() {
        val blocks = chapter()
        // A stored range that runs past the end of its block must not paint outside it.
        val overlong = quoteOnly("zeta", id = "over").let {
            it.copy(locator = it.locator.copy(startOffset = laidOutStart(2), endOffset = 10_000))
        }

        val range = rangeIn(overlong, 2, blocks[2].text)

        // "delta epsilon zeta": zeta starts at 14 and the range ends with the block at 18.
        assertNotNull(range)
        assertEquals("delta epsilon zeta".length, range.end)
        assertEquals(14, range.start)
    }

    @Test
    fun `the legacy chain still resolves a block-located highlight without an index`() {
        // Desktop has no index and must keep working. Its chain handles a highlight that already
        // knows its block and offset, so that is what is pinned here.
        val blocks = chapter()
        val located = UserHighlight(
            id = "desktop",
            cfi = "/4/2:6",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                blockIndex = 0,
                charOffset = 6,
                textQuote = "beta",
                cfi = "/4/2:6"
            )
        )

        val range = sharedNativeHighlightRangeForBlock(
            highlight = located,
            blockCfi = "/4/0",
            textStartOffset = 0,
            textLength = blocks[0].text.length,
            text = blocks[0].text,
            blockIndex = 0,
            blockCharOffset = 6,
            chapterTextIndex = null
        )

        assertEquals(6, range?.start)
        assertEquals(10, range?.end)
    }

    @Test
    fun `the legacy chain cannot place a quote-only highlight, which is why the resolver is needed`() {
        // Pins why the resolver had to be threaded through rather than left as an Android extra: the
        // shared chain has no text fallback, so a highlight created in a WebView surface — which
        // carries only its text — rendered on nothing at all here.
        val legacy = quoteOnly("beta")

        val range = sharedNativeHighlightRangeForBlock(
            highlight = legacy,
            blockCfi = "/4/0",
            textStartOffset = 0,
            textLength = "alpha beta gamma".length,
            text = "alpha beta gamma",
            blockIndex = 0,
            chapterTextIndex = null
        )

        assertNull(range)
    }
}