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
    fun `badge hangs just past the highlight end`() {
        val bounds = highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 20f)

        assertNotNull(bounds)
        val size = 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        // Hangs past the end by a fraction of its own size, so the last letter stays legible.
        assertEquals(200f + size * HIGHLIGHT_NOTE_BADGE_END_OVERHANG_FRACTION, bounds.right, 0.001f)
        assertEquals(size, bounds.width, 0.001f)
        assertEquals(bounds.height, bounds.width)
    }

    @Test
    fun `badge straddles the line top by the protrusion fraction`() {
        val lineTop = 100f
        val lineHeight = 20f
        val leading = 8f
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = lineTop,
            lineHeight = lineHeight,
            leadingAbove = leading,
        )

        assertNotNull(bounds)
        val size = lineHeight * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        assertEquals(lineTop - size * HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION, bounds.top, 0.001f)
        // Most of the mark still sits inside the highlight's fill, which is what keeps it attached to
        // it, while enough of it is in the leading above to clear the text.
        assertTrue(bounds.bottom - lineTop > 0f)
        assertTrue(lineTop - bounds.top <= leading + size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION)
    }

    @Test
    fun `badge lifts only by its own sliver when the leading above it is unknown`() {
        // Nothing describes the line above a single-line PDF highlight, and a line box that already
        // covers its own leading has no gap to borrow: the mark takes only the sliver of descender
        // space that the line box above it never fills, never the full protrusion.
        val bounds = highlightNoteBadgeBounds(right = 200f, lineTop = 100f, lineHeight = 20f)

        assertNotNull(bounds)
        val size = 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        assertEquals(100f - size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION, bounds.top, 0.001f)
        assertTrue(size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION < size * HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION)
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
        val size = 24f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        // The 2px that exist plus the sliver, and no more.
        assertEquals(100f - (2f + size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION), bounds.top, 0.001f)
        assertTrue(100f - bounds.top - 2f <= size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION + 0.001f)
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
        val size = 24f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        // A negative gap buys nothing extra: only the sliver remains.
        assertEquals(100f - size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION, bounds.top, 0.001f)
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
        // Anchored to the last line's end, hanging past it by the shared overhang.
        assertEquals(
            180f + 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION * HIGHLIGHT_NOTE_BADGE_END_OVERHANG_FRACTION,
            bounds.right,
            0.001f
        )
        assertTrue(bounds.top < 130f)
        // The previous line ends at 120; the mark crosses that edge only by its own sliver, which is
        // descender space rather than type.
        val size = 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        assertTrue(bounds.top >= 120f - size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION - 0.001f)
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
        assertEquals(
            240f + 20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION * HIGHLIGHT_NOTE_BADGE_END_OVERHANG_FRACTION,
            bounds.right,
            0.01f
        )
        assertEquals(20f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION, bounds.width, 0.01f)
    }

    @Test
    fun `mark takes only its sliver when the caller has no leading to give`() {
        // The Compose text readers fill the whole line box, so they pass 0: the mark borrows nothing
        // from the leading above and stays within its own height either side of the line's top edge.
        val lineTop = 100f
        val bounds = highlightNoteBadgeBounds(
            right = 200f,
            lineTop = lineTop,
            lineHeight = 30f,
            leadingAbove = 0f,
        )

        assertNotNull(bounds)
        val size = 30f * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
        assertEquals(lineTop - size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION, bounds.top, 0.001f)
        assertTrue(bounds.bottom <= lineTop + 30f)
    }
}
