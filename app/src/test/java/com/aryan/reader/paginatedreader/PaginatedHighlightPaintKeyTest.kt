package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The identity a block's cached highlight paint is keyed on.
 *
 * The paint cache was keyed on ranges alone, and ranges are ids and offsets — neither of which a
 * recolour touches. So recolouring a highlight left every cache key equal, Compose skipped the
 * block, and the highlight kept its old colour on screen even though the model already held the new
 * one. These pin the one property that fixes it: a change the paint actually depends on has to
 * change the key.
 */
class PaginatedHighlightPaintKeyTest {

    private fun highlight(
        id: String,
        color: HighlightColor = HighlightColor.YELLOW,
        colorArgb: Int? = null,
        style: HighlightStyle = HighlightStyle.BACKGROUND
    ) = UserHighlight(
        id = id,
        cfi = "/4/0",
        text = "beta",
        color = color,
        chapterIndex = 0,
        colorArgb = colorArgb,
        style = style,
        locator = ReaderLocator(chapterIndex = 0, blockIndex = 0, startOffset = 6, endOffset = 10)
    )

    private val ranges = mapOf("h1" to listOf(6..9))

    @Test
    fun `recolouring a highlight changes the paint key`() {
        val before = paginatedHighlightPaintKey(ranges, mapOf("h1" to highlight("h1")))
        val after = paginatedHighlightPaintKey(
            ranges,
            mapOf("h1" to highlight("h1", color = HighlightColor.GREEN))
        )

        assertNotEquals(
            "A recolour must invalidate the cached paint or the old colour stays on screen",
            before,
            after
        )
    }

    @Test
    fun `changing only the custom argb changes the paint key`() {
        // Two custom palette colours share the same named colour, so the name alone cannot tell them
        // apart and the ARGB has to be in the key.
        val before = paginatedHighlightPaintKey(
            ranges,
            mapOf("h1" to highlight("h1", colorArgb = 0xFF112233.toInt()))
        )
        val after = paginatedHighlightPaintKey(
            ranges,
            mapOf("h1" to highlight("h1", colorArgb = 0xFF445566.toInt()))
        )

        assertNotEquals(before, after)
    }

    @Test
    fun `changing the style changes the paint key`() {
        val before = paginatedHighlightPaintKey(ranges, mapOf("h1" to highlight("h1")))
        val after = paginatedHighlightPaintKey(
            ranges,
            mapOf("h1" to highlight("h1", style = HighlightStyle.UNDERLINE))
        )

        assertNotEquals(before, after)
    }

    @Test
    fun `an unchanged highlight keeps the same key`() {
        val highlights = mapOf("h1" to highlight("h1"))

        assertEquals(
            paginatedHighlightPaintKey(ranges, highlights),
            paginatedHighlightPaintKey(ranges, mapOf("h1" to highlight("h1")))
        )
    }

    @Test
    fun `a highlight this block does not paint does not change the key`() {
        // Otherwise every recolour anywhere in the book would rebuild every block on every page.
        val painted = mapOf("h1" to highlight("h1"))
        val withUnrelated = painted + ("h2" to highlight("h2"))

        assertEquals(
            paginatedHighlightPaintKey(ranges, painted),
            paginatedHighlightPaintKey(ranges, withUnrelated)
        )
    }

    @Test
    fun `the key does not depend on map iteration order`() {
        val forward = mapOf("h1" to highlight("h1"), "h2" to highlight("h2", color = HighlightColor.BLUE))
        val reversed = mapOf("h2" to highlight("h2", color = HighlightColor.BLUE), "h1" to highlight("h1"))
        val bothRanges = mapOf("h1" to listOf(0..1), "h2" to listOf(2..3))

        assertEquals(
            paginatedHighlightPaintKey(bothRanges, forward),
            paginatedHighlightPaintKey(bothRanges, reversed)
        )
    }
}