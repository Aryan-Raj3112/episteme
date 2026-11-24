package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a newly created highlight claims about where it is.
 *
 * The chapter's laid-out text decides a highlight's position, so a created highlight must arrive
 * carrying that position rather than one computed some other way. The other way is the tempting one:
 * the page knows the character offset it saw, and the block knows where its own text starts, so adding
 * the two looks like a chapter position. It is not — the block's start is the parser's per-element
 * counter, which restarts at every paragraph — and a position that is not one cannot be repaired later,
 * because nothing can tell that the number was never a position.
 *
 * This is checked at the one place both creation paths funnel through, because there are two of them —
 * the paginated surface and the native vertical one — and a bug written here is written twice, once per
 * surface, which is how the two surfaces came to disagree about a highlight's position in the first place.
 */
class PaginatedReaderHighlightLocatorMigrationTest {

    private val paragraphText = "0123456789".repeat(20)

    private fun chapterIndex() = EpubChapterTextIndex.of(
        3,
        listOf(
            SemanticParagraph(
                text = paragraphText,
                spans = emptyList(),
                style = CssStyle(),
                elementId = null,
                cfi = "/4/8",
                startCharOffsetInSource = 0,
                blockIndex = 4
            )
        )
    )

    /**
     * A selection carrying the values a real one has: offsets within the page's slice of the block, and
     * a block start that is that slice's start rather than a position in the chapter.
     */
    private fun selection(
        text: String = "a selected phrase",
        startOffset: Int = 12,
        endOffset: Int = 27,
        startBlockCharOffset: Int = 0,
        blockIndex: Int = 4
    ) = PaginatedSelection(
        startBlockIndex = blockIndex,
        endBlockIndex = blockIndex,
        startBaseCfi = "/4/8",
        endBaseCfi = "/4/8",
        startOffset = startOffset,
        endOffset = endOffset,
        text = text,
        rect = Rect(0f, 0f, 100f, 40f),
        startPageIndex = 3,
        endPageIndex = 3,
        startBlockCharOffset = startBlockCharOffset,
        endBlockCharOffset = startBlockCharOffset
    )

    @Test
    fun `a created highlight stores the chapter position of the words it selected`() {
        val placement = selection(startOffset = 12, endOffset = 27, startBlockCharOffset = 40)
            .toHighlightPlacement(chapterIndex = 3, chapterTextIndex = chapterIndex())

        // The page's 12 is 40 characters into the paragraph, so the highlight starts at 52. Storing 12
        // would put it a paragraph-slice away from its words, and storing only the element would let a
        // renderer choose the paragraph's first copy of the words instead.
        assertEquals("/4/8:52", placement.cfi)
        assertEquals(52, placement.locator.startOffset)
        assertEquals(67, placement.locator.endOffset)
        assertEquals(52, placement.locator.charOffset)
        assertEquals(4, placement.locator.blockIndex)
        assertEquals("a selected phrase", placement.locator.textQuote)
    }

    @Test
    fun `a created highlight keeps the page it was made on as a hint`() {
        val placement = selection().toHighlightPlacement(chapterIndex = 3, chapterTextIndex = chapterIndex())

        // A page is where the highlight was seen on screen, not where it is: it shifts with font and
        // margin settings, so it is kept as a hint and never as an anchor.
        assertEquals(3, placement.locator.pageIndex)
    }

    @Test
    fun `a selection the chapter cannot place keeps the page's own offsets`() {
        val placement = selection().toHighlightPlacement(chapterIndex = 3, chapterTextIndex = null)

        // Nothing can convert page offsets into chapter offsets without the chapter's layout, and a
        // guess would be stored as a position. The unset range leaves the highlight placeable from the
        // CFI and from its words, which the resolver can both do.
        assertEquals("/4/8:12|/4/8:27", placement.cfi)
        assertNull(placement.locator.startOffset)
        assertNull(placement.locator.endOffset)
        assertNull(placement.locator.charOffset)
    }

    @Test
    fun `a selection with no block cannot claim one`() {
        val placement = selection(blockIndex = -1).toHighlightPlacement(chapterIndex = 0, chapterTextIndex = null)

        // -1 means the surface could not say which block this was. Storing it would be a position that
        // names nothing, which is the same failure as storing a range that names nothing.
        assertNull(placement.locator.blockIndex)
    }
}