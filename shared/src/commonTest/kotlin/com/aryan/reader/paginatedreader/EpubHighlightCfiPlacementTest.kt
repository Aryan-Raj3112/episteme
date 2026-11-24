package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Placing a highlight where its locator says it is, instead of where its words first appear.
 *
 * Every surface writes a CFI of the form `/<block path>:<offset inside that block>`, and both the
 * paginator and the WebView resolve that same form against the same element numbering. So a locator
 * already knows which copy of a repeated word it covers, and searching the chapter for the words can
 * only destroy that knowledge: it answers with the first copy every time, which is a highlight
 * appearing on an earlier instance of the selected text.
 *
 * A CFI is still a claim, though, so each case here also pins what happens when the claim does not
 * hold: the chapter must not be searched from the top, and a locator that names a block must not be
 * allowed to jump to another block's copy of the same words.
 */
class EpubHighlightCfiPlacementTest {

    private fun paragraph(blockIndex: Int, text: String, start: Int = 0) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = "/4/2/${blockIndex * 2}",
        startCharOffsetInSource = start,
        blockIndex = blockIndex
    )

    private fun index(vararg blocks: SemanticBlock) = assertNotNull(EpubChapterTextIndex.of(0, blocks.toList()))

    private fun highlight(
        cfi: String?,
        text: String,
        blockIndex: Int? = null,
        start: Int? = null,
        end: Int? = null
    ) = UserHighlight(
        id = "h",
        cfi = cfi.orEmpty(),
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            blockIndex = blockIndex,
            textQuote = text,
            cfi = cfi
        )
    )

    @Test
    fun `places a highlight on the copy of the words its cfi names`() {
        val index = index(paragraph(0, "without a doubt, without a care"))
        val secondCopy = "without a doubt, ".length

        val anchor = assertNotNull(
            index.anchorFor(
                highlight(
                    cfi = "/4/2/0:$secondCopy|/4/2/0:${secondCopy + "without".length}",
                    text = "without",
                    blockIndex = 0
                )
            )
        )

        // Not 0: the first copy is a different highlight, made by selecting a different word.
        assertEquals(secondCopy, anchor.segments.single().localStart)
        assertEquals(secondCopy + "without".length, anchor.segments.single().localEnd)
        assertEquals(HighlightAnchorConfidence.Resolved, anchor.confidence)
    }

    @Test
    fun `a cfi that names only the block searches that block, not the chapter`() {
        val index = index(
            paragraph(0, "without one"),
            paragraph(1, "without two"),
            paragraph(2, "without three")
        )

        val anchor = assertNotNull(index.anchorFor(highlight(cfi = "/4/2/2", text = "without", blockIndex = 1)))

        assertEquals(1, anchor.segments.single().blockIndex)
        assertEquals(0, anchor.segments.single().localStart)
    }

    @Test
    fun `a cfi whose offset does not hold the words is not trusted`() {
        val index = index(paragraph(0, "without a doubt, without a care"))
        val care = "without a doubt, without a ".length

        // The stored offsets say the highlight starts at the paragraph's first character, but the
        // words it selected are at the end: the position is stale, and the words win.
        val anchor = assertNotNull(
            index.anchorFor(highlight(cfi = "/4/2/0:0|/4/2/0:4", text = "care", blockIndex = 0))
        )

        assertEquals(care, anchor.segments.single().localStart)
    }

    @Test
    fun `a cfi naming a block this chapter does not have falls back to the words`() {
        val index = index(paragraph(0, "alpha without beta"))

        val anchor = assertNotNull(
            index.anchorFor(highlight(cfi = "/4/9/9:1|/4/9/9:8", text = "without", blockIndex = 7))
        )

        assertEquals(0, anchor.segments.single().blockIndex)
        assertEquals(6, anchor.segments.single().localStart)
    }

    @Test
    fun `a selection reports offsets the page can hold, the stored ones belong to the block`() {
        val text = "0123456789".repeat(6)
        val index = index(paragraph(0, text))

        // The paginator split this paragraph and put the tail on a later page: the page's own offsets
        // are relative to its slice, which starts 40 characters into the paragraph.
        val anchor = assertNotNull(
            index.anchorForSelection(
                startBlockIndex = 0,
                startBlockCfi = "/4/2/0",
                startFragmentSourceStart = 40,
                startLocal = 5,
                endBlockIndex = 0,
                endBlockCfi = "/4/2/0",
                endFragmentSourceStart = 40,
                endLocal = 12
            )
        )

        assertEquals(45, anchor.segments.single().localStart)
        assertEquals(52, anchor.segments.single().localEnd)
        assertEquals(45, anchor.absoluteStart)
        assertEquals(52, anchor.absoluteEnd)
        assertEquals("/4/2/0:45", index.cfiFor(anchor))
    }

    @Test
    fun `a fragment shift is measured against the block's own start offset`() {
        val text = "0123456789".repeat(3)
        val index = index(paragraph(0, text, start = 100))

        val anchor = assertNotNull(
            index.anchorForSelection(
                startBlockIndex = 0,
                startBlockCfi = "/4/2/0",
                startFragmentSourceStart = 110,
                startLocal = 5,
                endBlockIndex = 0,
                endBlockCfi = "/4/2/0",
                endFragmentSourceStart = 110,
                endLocal = 12
            )
        )

        // The fragment starts ten characters into a block that itself starts at 100, so its local 5 is
        // the block's 15 — not 5, and not 105.
        assertEquals(15, anchor.segments.single().localStart)
        assertEquals(22, anchor.segments.single().localEnd)
    }

    @Test
    fun `a selection spanning two blocks is anchored in both`() {
        val index = index(paragraph(0, "alpha beta"), paragraph(1, "gamma delta"))

        val anchor = assertNotNull(
            index.anchorForSelection(
                startBlockIndex = 0,
                startBlockCfi = "/4/2/0",
                startFragmentSourceStart = 0,
                startLocal = 6,
                endBlockIndex = 1,
                endBlockCfi = "/4/2/2",
                endFragmentSourceStart = 0,
                endLocal = 5
            )
        )

        assertEquals(2, anchor.segments.size)
        assertEquals(6, anchor.segments[0].localStart)
        assertEquals("beta", "alpha beta".substring(anchor.segments[0].localStart))
        assertEquals(5, anchor.segments[1].localEnd)
    }

    @Test
    fun `a selection the chapter cannot place is not guessed at`() {
        val index = index(paragraph(0, "alpha beta"))

        assertNull(
            index.anchorForSelection(
                startBlockIndex = 9,
                startBlockCfi = "/9/9/9",
                startFragmentSourceStart = 0,
                startLocal = 0,
                endBlockIndex = 9,
                endBlockCfi = "/9/9/9",
                endFragmentSourceStart = 0,
                endLocal = 4
            )
        )
    }

    @Test
    fun `repair fills in the offsets of the copy the cfi names`() {
        val index = index(paragraph(0, "without a doubt, without a care"))
        val secondCopy = "without a doubt, ".length
        val stored = highlight(
            cfi = "/4/2/0:$secondCopy|/4/2/0:${secondCopy + "without".length}",
            text = "without",
            blockIndex = 0
        )

        val repaired = assertNotNull(index.anchorMissingOffsets(stored))

        assertEquals(secondCopy, repaired.locator.startOffset)
        assertEquals(secondCopy + "without".length, repaired.locator.endOffset)
        assertEquals(0, repaired.locator.blockIndex)
        // Nothing left to fill in, so a second pass neither rewrites nor reports a repair.
        assertNull(index.anchorMissingOffsets(repaired))
    }
}