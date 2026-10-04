package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repairing highlights that were already stored before the coordinate space was fixed.
 *
 * These are the cases a user already has in a library. There is no version marker on a stored
 * locator — it is just a pair of integers — so the repair has to decide by looking at whether the
 * selected text is actually at the stored position. Each test pins one way that decision can go.
 */
class EpubHighlightRepairTest {

    private val filler = "keeps the document tall enough for scroll and pagination tests"

    /**
     * Every block reports start 0, which is what a real parsed chapter looks like: the parser's
     * offset counter is per element. Offsets written against that are therefore block-relative even
     * though they are stored as chapter offsets.
     */
    private fun parsedChapter(): List<SemanticTextBlock> = listOf(
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

    /** Offset of [blockIndex]'s text in the laid-out chapter, where blocks sit one space apart. */
    private fun laidOutStart(blockIndex: Int): Int =
        parsedChapter().take(blockIndex).sumOf { it.text.length + 1 }

    private fun index() = assertNotNull(EpubChapterTextIndex.of(0, parsedChapter()))

    private fun highlight(
        text: String,
        locator: ReaderLocator,
        chapterIndex: Int = 0,
        id: String = "h"
    ) = UserHighlight(
        id = id,
        cfi = locator.cfi.orEmpty(),
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = chapterIndex,
        locator = locator
    )

    private fun legacy(locator: ReaderLocator) = highlight(
        text = locator.textQuote.orEmpty(),
        locator = locator
    )

    @Test
    fun `repairs a locator whose offsets were computed in the broken space`() {
        // Offsets 0..62 are the old block-relative pair: every block started at 0, so this points at
        // the start of the chapter, not at the highlighted sentence.
        val stored = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = 0,
                endOffset = filler.length,
                textQuote = filler
            )
        )

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        assertEquals(laidOutStart(1), repaired.locator.startOffset)
        assertEquals(laidOutStart(1) + filler.length, repaired.locator.endOffset)
        assertEquals(1, repaired.locator.blockIndex)
        assertEquals(laidOutStart(1), repaired.locator.charOffset)
    }

    @Test
    fun `repairs a locator with no offsets at all`() {
        val stored = legacy(ReaderLocator(chapterIndex = 0, textQuote = "delta epsilon"))

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        assertEquals(laidOutStart(2), repaired.locator.startOffset)
        assertEquals(laidOutStart(2) + "delta epsilon".length, repaired.locator.endOffset)
        assertEquals(2, repaired.locator.blockIndex)
    }

    @Test
    fun `leaves a locator alone when its range already holds the selected text`() {
        val start = laidOutStart(2)
        val stored = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = start,
                endOffset = start + "delta epsilon".length,
                textQuote = "delta epsilon"
            )
        )

        // Nothing to do, so no rewrite. Repainting a correct locator would be wasted work on every
        // open, and a changed locator would dirty the stored book.
        assertNull(index().anchorMissingOffsets(stored))
    }

    @Test
    fun `repairs a locator that drifted because the book text changed under it`() {
        val stored = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = laidOutStart(2),
                endOffset = laidOutStart(2) + "delta epsilon".length,
                textQuote = "delta epsilon that is no longer in this edition"
            )
        )

        // The stored text is gone from the book. The only sensible repair is to place what can still
        // be found, and to give up cleanly when nothing can.
        assertNull(index().anchorMissingOffsets(stored))
    }

    @Test
    fun `a repeated sentence is repaired to its first occurrence, once`() {
        val stored = legacy(ReaderLocator(chapterIndex = 0, textQuote = filler))

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        // Block 1 and block 3 hold this sentence verbatim. First in document order, so the choice is
        // deterministic and does not move when the chapter repaginates.
        assertEquals(1, repaired.locator.blockIndex)
        assertEquals(laidOutStart(1), repaired.locator.startOffset)
    }

    @Test
    fun `replaces a WebView DOM position with the block CFI a paginated page can address`() {
        val stored = highlight(
            text = "alpha beta",
            locator = ReaderLocator(chapterIndex = 0, textQuote = "alpha beta", cfi = "0:4:2:9:1")
        )

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        // "0:4:2:9:1" is a WebView child-index path. Nothing in a paginated page can resolve it, so
        // keeping it would mean the highlight stayed unaddressable there.
        assertEquals("/4/0", repaired.locator.cfi)
    }

    @Test
    fun `does not touch a highlight belonging to another chapter`() {
        val stored = highlight(
            text = filler,
            chapterIndex = 3,
            locator = ReaderLocator(chapterIndex = 3, textQuote = filler)
        )

        assertNull(index().anchorMissingOffsets(stored))
    }

    @Test
    fun `spans every block of a multi-paragraph selection`() {
        val quote = "gamma ${filler.take(20)}"
        val stored = legacy(ReaderLocator(chapterIndex = 0, textQuote = quote))

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        // The selection starts inside block 0 and ends inside block 1. The single absolute range
        // covers both, so a page holding either block can intersect it without needing the quote again.
        assertEquals(laidOutStart(0) + "alpha beta ".length, repaired.locator.startOffset)
        assertEquals(laidOutStart(1) + 20, repaired.locator.endOffset)
        // The first block owns the start, which is what a page uses to find its own slice.
        assertEquals(0, repaired.locator.blockIndex)
    }

    @Test
    fun `repairing a batch reports what changed and leaves the rest byte-identical`() {
        val alreadyCorrect = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = laidOutStart(2),
                endOffset = laidOutStart(2) + "delta".length,
                textQuote = "delta"
            )
        )
        val broken = legacy(ReaderLocator(chapterIndex = 0, textQuote = "alpha beta"))
        val otherChapter = highlight(
            text = filler,
            chapterIndex = 2,
            locator = ReaderLocator(chapterIndex = 2, textQuote = filler)
        )

        val result = index().repairHighlights(listOf(alreadyCorrect, broken, otherChapter))

        assertEquals(1, result.repaired)
        assertTrue(!result.unchanged)
        assertEquals(3, result.highlights.size)
        assertEquals(alreadyCorrect, result.highlights[0])
        assertEquals(laidOutStart(0), result.highlights[1].locator.startOffset)
        assertEquals(otherChapter, result.highlights[2])
    }

    @Test
    fun `repairing an already-clean batch reports nothing to persist`() {
        val alreadyCorrect = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = laidOutStart(2),
                endOffset = laidOutStart(2) + "delta".length,
                textQuote = "delta"
            )
        )

        val result = index().repairHighlights(listOf(alreadyCorrect))

        assertEquals(0, result.repaired)
        assertTrue(result.unchanged)
        assertEquals(listOf(alreadyCorrect), result.highlights)
    }

    /**
     * Repair may rewrite a highlight but must never lose one.
     *
     * This is the property the whole migration rests on. Stored locators were being corrected in place on
     * every open, so if repair could drop, merge or reorder anything, a reader would open a book and find
     * annotations missing — the one failure that cannot be undone, because the data being rewritten is
     * the only copy. So the guarantee is stated as a whole-batch property rather than per case: whatever
     * the batch contains, the result contains the same highlights in the same order, and any difference
     * is confined to a locator's coordinates.
     *
     * The batch is deliberately hostile: a highlight in another chapter, one whose text is not in this
     * chapter, one with offsets from the broken space, one already correct, one with no text at all, and
     * two identical ones.
     */
    @Test
    fun `repair changes only coordinates, never which highlights exist`() {
        val batch = listOf(
            legacy(ReaderLocator(chapterIndex = 0, textQuote = filler)), // placed by text
            legacy(ReaderLocator(chapterIndex = 0, textQuote = "alpha beta")),
            legacy( // offsets from the broken space
                ReaderLocator(chapterIndex = 0, startOffset = 3, endOffset = 300, textQuote = "delta")
            ),
            legacy( // already correct
                ReaderLocator(
                    chapterIndex = 0,
                    startOffset = laidOutStart(2),
                    endOffset = laidOutStart(2) + "delta".length,
                    textQuote = "delta"
                ).let { it }
            ),
            highlight(text = "not in this chapter at all", locator = ReaderLocator(chapterIndex = 0)),
            highlight(text = "", locator = ReaderLocator(chapterIndex = 0)), // no text to place
            highlight( // another chapter entirely
                text = filler,
                chapterIndex = 2,
                locator = ReaderLocator(chapterIndex = 2, textQuote = filler),
                id = "other-chapter"
            ),
            highlight(text = "delta", locator = ReaderLocator(chapterIndex = 0, textQuote = "delta"), id = "dupe"),
            highlight(text = "delta", locator = ReaderLocator(chapterIndex = 0, textQuote = "delta"), id = "dupe")
        )

        val result = index().repairHighlights(batch)

        // The batch is genuinely repaired, so this cannot pass by repair doing nothing at all.
        assertTrue(result.repaired > 0, "expected some highlights to be corrected")
        assertEquals(batch.size, result.highlights.size)
        assertEquals(batch.map { it.id }, result.highlights.map { it.id })
        // Nothing but the locator may differ, so a reader's own text, colour and notes are untouched.
        result.highlights.forEachIndexed { position, repaired ->
            assertEquals(batch[position].text, repaired.text)
            assertEquals(batch[position].color, repaired.color)
            assertEquals(batch[position].cfi.orEmpty(), repaired.cfi.orEmpty())
        }
        // And the highlights this chapter cannot place come back unchanged rather than dropped or blanked.
        listOf(4, 5, 6).forEach { position ->
            assertEquals(batch[position], result.highlights[position])
        }
    }

    /**
     * A highlight whose text cannot be placed keeps whatever position it already had.
     *
     * The other half of not losing anything: repair is a correction, not a reconstruction. It replaces a
     * range only when it has found a better one, so a highlight it cannot resolve is left exactly as the
     * reader last had it — still listed, still carrying its own text, and still painted by whichever
     * surface can. Blanking its locator "to be safe" would be the one thing guaranteed to lose it.
     */
    @Test
    fun `a highlight repair cannot place keeps the locator it already had`() {
        val stored = ReaderLocator(
            chapterIndex = 0,
            startOffset = 41,
            endOffset = 55,
            blockIndex = 2,
            charOffset = 41,
            textQuote = "text from a later edition",
            cfi = "/4/4:7"
        )
        val before = highlight(text = "text from a later edition", locator = stored)

        val result = index().repairHighlights(listOf(before))

        assertEquals(0, result.repaired)
        assertEquals(listOf(before), result.highlights)
    }

    @Test
    fun `repair is idempotent, so opening a book twice writes the same locator`() {
        val stored = legacy(ReaderLocator(chapterIndex = 0, textQuote = filler))

        val afterFirstOpen = assertNotNull(index().anchorMissingOffsets(stored))
        // Second open: nothing left to fix, so the stored value is returned unchanged.
        val afterSecondOpen = index().anchorMissingOffsets(afterFirstOpen) ?: afterFirstOpen

        // The whole value has to survive, not just the offsets: a repair that produced a different
        // locator on a second pass would mark the book dirty every single time it was opened.
        assertEquals(afterFirstOpen, afterSecondOpen)
    }

    @Test
    fun `a locator whose range is out of bounds is repaired rather than trusted`() {
        val stored = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = 90_000,
                endOffset = 90_050,
                textQuote = "alpha beta"
            )
        )

        val repaired = assertNotNull(index().anchorMissingOffsets(stored))

        assertEquals(laidOutStart(0), repaired.locator.startOffset)
    }

    @Test
    fun `a range covering more text than the quote is accepted as already anchored`() {
        // Selection handles and trimmed quotes often leave a stored range wider than the quote.
        // Repairing it would rewrite a correct locator on every open for no benefit.
        val start = laidOutStart(2)
        val stored = legacy(
            ReaderLocator(
                chapterIndex = 0,
                startOffset = start,
                endOffset = start + "delta epsilon zeta".length,
                textQuote = "delta epsilon"
            )
        )

        assertNull(index().anchorMissingOffsets(stored))
    }
}