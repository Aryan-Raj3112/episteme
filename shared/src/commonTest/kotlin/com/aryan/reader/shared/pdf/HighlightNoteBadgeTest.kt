package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The note mark is the one thing drawn beside a highlight that is allowed to cover text, so where it
 * lands is the whole contract: it must sit at the highlight's own end, spend no room of its own, and
 * never reach the line above.
 */
class HighlightNoteBadgeTest {

    @Test
    fun `badge is right-aligned with the highlight end`() {
        val bounds = highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 20f)

        assertNotNull(bounds)
        assertEquals(200f, bounds.right)
        assertEquals(200f - 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION, bounds.left)
        assertEquals(bounds.height, bounds.width)
    }

    @Test
    fun `badge straddles the line top by the protrusion fraction`() {
        val lineTop = 100f
        val lineHeight = 20f
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = lineTop,
            lineHeight = lineHeight,
            leadingAbove = 8f,
        )

        assertNotNull(bounds)
        val size = lineHeight * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        assertEquals(lineTop - size * HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION, bounds.top)
        // Most of the mark is inside the highlight, which is what keeps it attached to it.
        assertTrue(bounds.bottom - lineTop > lineTop - bounds.top)
    }

    @Test
    fun `badge stays inside the line when the leading above it is unknown`() {
        // Nothing describes the line above a single-line PDF highlight, and a line box that already
        // covers its own leading has no gap to borrow: assuming one would put the mark on the
        // previous line's descenders.
        val bounds = highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 20f)

        assertNotNull(bounds)
        assertEquals(100f, bounds.top)
        assertEquals(100f + 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION, bounds.bottom)
    }

    @Test
    fun `badge never protrudes past the leading that exists`() {
        // A tight setting: only 2px between the highlight's top and the line above it.
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = 100f,
            lineHeight = 24f,
            leadingAbove = 2f,
        )

        assertNotNull(bounds)
        assertEquals(98f, bounds.top)
        assertEquals(2f, 100f - bounds.top)
    }

    @Test
    fun `badge handles a negative leading without reaching further up`() {
        // Overlapping line boxes (a tall inline element in the same paragraph) must not push the mark
        // into the line above; it simply sits deeper inside the highlight.
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = 100f,
            lineHeight = 24f,
            leadingAbove = -6f,
        )

        assertNotNull(bounds)
        assertEquals(100f, bounds.top)
    }

    @Test
    fun `degenerate inputs draw nothing`() {
        assertNull(highlightNoteBadgeBounds(right = Float.NaN, lineTop = 100f, lineHeight = 20f))
        assertNull(highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 0f))
        // A line shorter than the minimum leaves no recognisable glyph, so it is skipped rather than
        // drawn as an unreadable speck.
        assertNull(highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 4f))
    }

    @Test
    fun `multi-line highlight marks its last line`() {
        val lines = listOf(
            PdfHighlightLine(left = 10f, right = 300f, top = 100f, height = 20f),
            PdfHighlightLine(left = 10f, right = 180f, top = 130f, height = 20f),
        )

        val bounds = pdfHighlightNoteBadgeBounds(lines)

        assertNotNull(bounds)
        assertEquals(180f, bounds.right)
        assertTrue(bounds.top < 130f)
        // The previous line ends at 120 and the gap above it exists, so nothing is painted over it.
        assertTrue(bounds.top >= 120f)
    }

    @Test
    fun `no lines means no mark`() {
        assertNull(pdfHighlightNoteBadgeBounds(emptyList()))
        assertNull(pdfHighlightNoteBadgeBoundsForRects(emptyList()))
    }

    @Test
    fun `rect grouping is the same one the stroke painter uses`() {
        // One visual line split into three font runs still yields a single mark, at the right end.
        val rects = listOf(
            PdfHighlightRect.from(10f, 100f, 90f, 120f),
            PdfHighlightRect.from(90f, 100f, 160f, 120f),
            PdfHighlightRect.from(160f, 101f, 240f, 119f),
        )

        val bounds = pdfHighlightNoteBadgeBoundsForRects(rects)

        assertNotNull(bounds)
        assertEquals(240f, bounds.right)
        assertEquals(20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION, bounds.width, 0.01f)
    }

    @Test
    fun `mark sits inside the highlight when the caller has no leading to give`() {
        // The Compose text readers fill the whole line box, so they pass 0: the mark then borrows
        // nothing from above and covers only the leading inside the highlight itself.
        val lineTop = 100f
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = lineTop,
            lineHeight = 30f,
            leadingAbove = 0f,
        )

        assertNotNull(bounds)
        assertEquals(lineTop, bounds.top)
        assertTrue(bounds.bottom <= lineTop + 30f)
    }
}
