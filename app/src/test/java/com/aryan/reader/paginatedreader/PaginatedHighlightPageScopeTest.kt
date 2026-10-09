package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the placement rules the paginated surfaces now share.
 *
 * The cases that matter are the ones the reported bugs came from: a highlight created in a WebView
 * surface carrying no offsets, a sentence repeated across a chapter, and a selection spanning
 * several paragraphs.
 */
class PaginatedHighlightPageScopeTest {

    private val filler = "keeps the document tall enough for scroll and pagination tests"

    // Four paragraphs, one of which repeats [filler] verbatim.
    private fun chapterBlocks(): List<TextContentBlock> {
        val texts = listOf(
            "alpha beta gamma",
            filler,
            "delta epsilon zeta",
            filler
        )
        var cursor = 0
        return texts.mapIndexed { index, text ->
            val block = paragraph(
                text = text,
                cfi = "/4/${index * 2}",
                startOffset = cursor,
                blockIndex = index
            )
            cursor += text.length + 1
            block
        }
    }

    private fun chapterIndex() = EpubChapterTextIndex.of(0, chapterBlocks().toSemanticTextBlocks())

    private fun quoteOnlyHighlight(text: String, id: String = "h"): UserHighlight = UserHighlight(
        id = id,
        cfi = "0:2:1:5:3",
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        style = HighlightStyle.BACKGROUND,
        locator = ReaderLocator.fromLegacy(chapterIndex = 0, cfi = "0:2:1:5:3", textQuote = text)
    )

    private fun rangedHighlight(
        start: Int,
        end: Int,
        text: String,
        chapterIndex: Int = 0
    ) = UserHighlight(
        id = "r",
        cfi = "/4/2:0",
        text = text,
        color = HighlightColor.BLUE,
        chapterIndex = chapterIndex,
        locator = ReaderLocator(
            chapterIndex = chapterIndex,
            startOffset = start,
            endOffset = end,
            textQuote = text,
            cfi = "/4/2:0"
        )
    )

    private fun resolve(
        blocks: List<TextContentBlock>,
        highlights: List<UserHighlight>,
        chapter: Int? = 0
    ) = resolvePaginatedPageHighlights(
        scope = PaginatedPageScope(chapter, blocks),
        highlights = highlights,
        chapterTextIndex = chapterIndex()
    )

    @Test
    fun `ranged highlight paints only the block that contains its offsets`() {
        val blocks = chapterBlocks()
        val start = blocks[0].startCharOffsetInSource + 6

        val resolved = resolve(blocks, listOf(rangedHighlight(start, start + 4, "beta")))

        assertEquals(listOf("r"), resolved.highlights.map { it.id })
        assertEquals(listOf(6 until 10), resolved.rangesForBlock(0)["r"])
        assertTrue(resolved.rangesForBlock(1).isEmpty())
    }

    @Test
    fun `ranged highlight spanning two blocks paints a range in each`() {
        val blocks = chapterBlocks()
        val start = blocks[0].startCharOffsetInSource + 11
        val end = blocks[1].startCharOffsetInSource + 5

        val resolved = resolve(blocks, listOf(rangedHighlight(start, end, "gamma keeps")))

        assertEquals(listOf(11 until 16), resolved.rangesForBlock(0)["r"])
        assertEquals(listOf(0 until 5), resolved.rangesForBlock(1)["r"])
    }

    @Test
    fun `quote-only highlight resolves onto exactly one block when the sentence repeats`() {
        val blocks = chapterBlocks()
        val highlight = quoteOnlyHighlight(filler)

        val firstPage = resolve(listOf(blocks[0], blocks[1]), listOf(highlight))
        val lastPage = resolve(listOf(blocks[2], blocks[3]), listOf(highlight))

        // [filler] appears verbatim in block 1 and block 3. Only the first may paint: matching both
        // is what put the same highlight on two pages of one chapter.
        assertEquals(listOf("h"), firstPage.highlights.map { it.id })
        assertTrue(firstPage.rangesForBlock(1).isNotEmpty())
        assertTrue(lastPage.highlights.isEmpty())
    }

    @Test
    fun `quote-only highlight spanning several paragraphs paints a range in each`() {
        val blocks = chapterBlocks()
        val quote = "${blocks[0].content.text} ${blocks[1].content.text.take(20)}"
        val highlight = quoteOnlyHighlight(quote, id = "multi")

        val resolved = resolve(blocks, listOf(highlight))

        assertEquals(listOf("multi"), resolved.highlights.map { it.id })
        assertTrue(resolved.rangesForBlock(0).getValue("multi").isNotEmpty())
        assertTrue(resolved.rangesForBlock(1).getValue("multi").isNotEmpty())
    }

    @Test
    fun `webview-origin highlight gains absolute offsets so other surfaces can place it`() {
        val blocks = chapterBlocks()
        val created = quoteOnlyHighlight(filler, id = "wv")

        assertTrue(!created.locator.hasTextRange)

        val resolved = requireNotNull(resolveWebViewHighlightAnchor(created, blocks))

        assertTrue(resolved.locator.hasTextRange)
        assertEquals(0, resolved.chapterIndex)
        // The repeated sentence lands in the first block that holds it, once. Offsets come from the
        // chapter layout, not from what the blocks report about themselves.
        val fillerStart = blocks[0].content.text.length + 1
        assertEquals(fillerStart, resolved.locator.startOffset)
        assertEquals(fillerStart + filler.length, resolved.locator.endOffset)
        assertEquals(1, resolved.locator.blockIndex)
    }

    @Test
    fun `anchoring never rewrites a highlight that already has offsets`() {
        val blocks = chapterBlocks()
        val created = quoteOnlyHighlight(filler, id = "wv")
        val anchored = requireNotNull(resolveWebViewHighlightAnchor(created, blocks))

        // Anchoring is a one-way fill-in. Re-running it is a no-op rather than a second re-resolution,
        // so a highlight cannot drift to a different occurrence of repeated text later on.
        assertNull(resolveWebViewHighlightAnchor(anchored, blocks))
    }

    @Test
    fun `anchoring a multi-paragraph selection covers every block it spans`() {
        val blocks = chapterBlocks()
        val quote = "${blocks[0].content.text} ${blocks[1].content.text.take(20)}"
        val created = quoteOnlyHighlight(quote, id = "multi")

        val resolved = requireNotNull(resolveWebViewHighlightAnchor(created, blocks))

        assertEquals(0, resolved.locator.startOffset)
        assertEquals(
            blocks[0].content.text.length + 1 + 20,
            resolved.locator.endOffset
        )
    }

    @Test
    fun `anchoring returns null when the text cannot be placed`() {
        val blocks = chapterBlocks()
        val created = quoteOnlyHighlight("text that is nowhere in the chapter")

        assertNull(resolveWebViewHighlightAnchor(created, blocks))
        assertNull(resolveWebViewHighlightAnchor(created, null))
        assertNull(resolveWebViewHighlightAnchor(null, blocks))
    }

    @Test
    fun `highlight in another chapter never paints`() {
        val blocks = chapterBlocks()
        val highlight = quoteOnlyHighlight(filler).copy(chapterIndex = 1)

        val resolved = resolve(blocks, listOf(highlight))

        assertTrue(resolved.highlights.isEmpty())
    }

    @Test
    fun `quote-only highlight with no resolvable text is dropped rather than guessed`() {
        val blocks = chapterBlocks()
        val highlight = quoteOnlyHighlight("a sentence that is nowhere in this chapter")

        val resolved = resolve(blocks, listOf(highlight))

        assertTrue(resolved.highlights.isEmpty())
    }

    @Test
    fun `page without text blocks shows nothing`() {
        val resolved = resolve(emptyList(), listOf(quoteOnlyHighlight(filler)))

        assertTrue(resolved.highlights.isEmpty())
    }

    @Test
    fun `overlapping highlights both keep their own ranges`() {
        val blocks = chapterBlocks()
        val start = blocks[0].startCharOffsetInSource + 6
        val a = rangedHighlight(start, start + 9, "beta gamma").copy(id = "a")
        val b = rangedHighlight(start + 4, start + 10, "gamma").copy(id = "b")

        val resolved = resolve(blocks, listOf(a, b))

        // Both are reported, each with its own range: neither is dropped for overlapping the other.
        assertEquals(setOf("a", "b"), resolved.highlights.map { it.id }.toSet())
        // "b" is stored as 10..16 and its quote "gamma" is what sits there, leading space included, so the
        // stored range is honoured exactly. "a" is stored as 6..15, one character short of its own
        // ten-character quote, so it is not a range describing this highlight and the quote decides
        // instead: 6..16 is "beta gamma". Neither answer comes from whichever integers happened to be
        // stored; both come from the chapter's text.
        assertEquals(listOf(10 until 16), resolved.rangesForBlock(0).getValue("b"))
        assertEquals(listOf(6 until 16), resolved.rangesForBlock(0).getValue("a"))
    }

    /**
     * A highlight places identically whether its stored offsets are right, wrong, or absent.
     *
     * This is the whole point of the migration. Offsets used to be an input to placement, computed by a
     * second piece of arithmetic over a field that is element-relative and therefore meaningless across
     * blocks; the result was that a 118-character selection stored as a 297-character span satisfied the
     * bounds check against every block on a page and painted most of it. Nothing downstream could object,
     * because the range it produced was well formed — it was simply over the wrong characters.
     *
     * Storing offsets is still worth doing, so this does not assert they are ignored: it asserts they
     * cannot change the answer. Only the chapter layout decides where a highlight goes.
     */
    @Test
    fun storedOffsetsCannotChangeWhereAHighlightIsPainted() {
        val blocks = chapterBlocks()
        val quote = filler
        val blockStart = blocks[1].startCharOffsetInSource

        val correct = UserHighlight(
            id = "correct",
            cfi = "0:2:1:5:3",
            text = quote,
            color = HighlightColor.GREEN,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = blockStart,
                endOffset = blockStart + quote.length,
                textQuote = quote,
                cfi = "0:2:1:5:3"
            )
        )
        val wrong = correct.copy(
            id = "wrong",
            locator = correct.locator.copy(startOffset = 3, endOffset = 300)
        )
        val absent = correct.copy(
            id = "absent",
            locator = ReaderLocator(chapterIndex = 0, textQuote = quote, cfi = "0:2:1:5:3")
        )

        val expected = resolve(blocks, listOf(correct)).rangesForBlock(1).getValue("correct")

        for (candidate in listOf(wrong, absent)) {
            val resolved = resolve(blocks, listOf(candidate))
            assertEquals(
                "highlight ${candidate.id} painted somewhere other than its own words",
                expected,
                resolved.rangesForBlock(1).getValue(candidate.id)
            )
            // And nowhere else. The failure this guards against was a range landing on every block the
            // page happened to contain.
            assertTrue(
                "highlight ${candidate.id} painted on more than one block",
                resolved.rangesByHighlight.getValue(candidate.id).keys.size == 1
            )
        }
    }

    /**
     * Blocks that all report the same start must still place correctly.
     *
     * `startCharOffsetInSource` is computed per HTML element, so a real parsed chapter reports the same
     * value for every block and cannot be used to compare positions across them. The old placement path
     * tested for that by checking the page's blocks had distinct starts, and refused to place anything
     * from offsets when they did not — which meant placement took a different branch depending on how the
     * chapter happened to be parsed. Now the page's blocks are only matched by identity, and the text is
     * laid out by the chapter index, so the same highlight lands in the same place either way.
     */
    @Test
    fun elementRelativeBlockStartsDoNotAffectPlacement() {
        val texts = listOf("alpha beta gamma", "delta epsilon zeta")
        val indistinguishable = texts.mapIndexed { index, text ->
            paragraph(
                text = text,
                cfi = "/4/${index * 2}",
                // Every block claims the chapter starts here, as an element-relative counter does.
                startOffset = 0,
                blockIndex = index
            )
        }
        val highlight = quoteOnlyHighlight("delta epsilon")

        val resolved = resolvePaginatedPageHighlights(
            scope = PaginatedPageScope(chapterIndex = 0, textBlocks = indistinguishable),
            highlights = listOf(highlight),
            chapterTextIndex = EpubChapterTextIndex.of(0, indistinguishable.toSemanticTextBlocks())
        )

        assertEquals(listOf("h"), resolved.highlights.map { it.id })
        assertEquals(listOf(0 until 13), resolved.rangesForBlock(1).getValue("h"))
    }

    /**
     * A chapter index covering only this page must not be able to place a repeated sentence on both.
     *
     * The resolver needs the whole chapter to choose one occurrence. Handing it a page's worth of blocks
     * makes "the page contains a copy" indistinguishable from "this is the occurrence", which is how one
     * highlight ended up on every page of a chapter.
     */
    @Test
    fun pageScopedIndexDoesNotPlaceARepeatedSentenceTwice() {
        val blocks = chapterBlocks()
        val highlight = quoteOnlyHighlight(filler)
        val firstPageOnly = resolvePaginatedPageHighlights(
            scope = PaginatedPageScope(chapterIndex = 0, textBlocks = listOf(blocks[0], blocks[3])),
            highlights = listOf(highlight),
            chapterTextIndex = EpubChapterTextIndex.of(0, listOf(blocks[0], blocks[3]).toSemanticTextBlocks())
        )

        // Whichever copy this page-scope index settles on, it settles on one block of this page and not
        // on a block belonging to the whole chapter as well.
        val painted = firstPageOnly.rangesByHighlight["h"]?.keys?.sorted() ?: emptyList()
        assertTrue("painted on $painted", painted.size == 1)
        assertEquals(listOf(3), painted)
    }

    private fun paragraph(
        text: String,
        cfi: String?,
        startOffset: Int,
        blockIndex: Int
    ) = ParagraphBlock(
        content = AnnotatedString(text),
        textAlign = null,
        style = BlockStyle(),
        elementId = null,
        cfi = cfi,
        startCharOffsetInSource = startOffset,
        endCharOffsetInSource = -1,
        blockIndex = blockIndex
    )
}