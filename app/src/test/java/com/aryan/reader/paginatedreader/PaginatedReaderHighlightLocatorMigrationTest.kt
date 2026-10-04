package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a newly created highlight is allowed to claim about where it is.
 *
 * The migration makes the chapter's laid-out text the only thing that decides a highlight's position, so
 * a highlight must not arrive carrying a position computed any other way. This is checked at the one
 * place every creation path funnels through, because there are two of them — the paginated surface and the
 * native vertical one — and they both call the same function. A bug here is written twice, once per
 * surface, which is how the two surfaces came to disagree about a highlight's position in the first place.
 */
class PaginatedReaderHighlightLocatorMigrationTest {

    /**
     * A selection carrying the values a real one has: offsets within the block, and a block start that is
     * the parser's per-element counter rather than a position in the chapter.
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
    fun `a created highlight records no chapter range for someone else to trust`() {
        val locator = selection(startBlockCharOffset = 7_000).toSharedHighlightLocator(
            chapterIndex = 3,
            cfi = "/4/8:12|/4/8:27"
        )

        // These three are what a renderer would have used to place the highlight, and they are exactly
        // what used to hold a number assembled from an element-relative start plus a character offset
        // within the block. A stored chapter range with no chapter behind it cannot be repaired by
        // arithmetic later, because nothing can tell that 7_012 was never a position in any chapter.
        assertNull(locator.startOffset)
        assertNull(locator.endOffset)
        assertNull(locator.charOffset)
    }

    @Test
    fun `a created highlight records only what the selection actually knows`() {
        val locator = selection(text = "a selected phrase", blockIndex = 4).toSharedHighlightLocator(
            chapterIndex = 3,
            cfi = "/4/8:12|/4/8:27"
        )

        assertEquals(3, locator.chapterIndex)
        assertEquals(4, locator.blockIndex)
        assertEquals("a selected phrase", locator.textQuote)
        assertEquals("/4/8:12|/4/8:27", locator.cfi)
        // The page is a hint about where this was on screen, not a position, and it shifts with font and
        // margin settings. It is kept because something may use it as one, but nothing may use it as an
        // anchor.
        assertEquals(3, locator.pageIndex)
    }

    @Test
    fun `a selection with no block cannot claim one`() {
        val locator = selection(blockIndex = -1).toSharedHighlightLocator(
            chapterIndex = 0,
            cfi = "/4/8:0|/4/8:5"
        )

        // -1 means the surface could not say which block this was. Storing it would be a position that
        // names nothing, which is the same failure as storing a range that names nothing.
        assertNull(locator.blockIndex)
    }
}