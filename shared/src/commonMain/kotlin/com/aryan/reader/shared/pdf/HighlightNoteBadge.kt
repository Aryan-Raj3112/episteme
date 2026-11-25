package com.aryan.reader.shared.pdf

/**
 * Geometry for the small "this highlight carries a note" mark.
 *
 * The mark is deliberately not a separate slot in the layout: a highlight that ends mid-line has no
 * free horizontal space at all (the next word is one word-space away), so placing a badge *beside*
 * the highlight would either add a gap that pushes text or paint over a word nobody asked to cover.
 * It borrows from the two regions that are always already there instead:
 *
 *  - horizontally it is **right-aligned with the highlight's end**, so it consumes no width at all;
 *  - vertically it straddles the **top edge** of the highlight's last line, spending most of its
 *    height inside the highlight's own fill and the rest in the leading above that line.
 *
 * The size is a fraction of the line's own height rather than a dp constant, so the mark keeps its
 * relationship to the type it marks at any zoom, font size or PDF scale.
 */
data class HighlightNoteBadgeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/** Badge edge as a fraction of the line height it is pinned to. */
const val HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION: Float = 0.56f

/** Fraction of the badge that reaches above the line's top edge, into the leading. */
const val HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION: Float = 0.6f

/** Fraction of the badge that sits below the line's top edge, inside the highlight's fill. */
const val HIGHLIGHT_NOTE_BADGE_INSIDE_FRACTION: Float =
    1f - HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION

/**
 * How far the mark hangs *past* the highlight's end, as a fraction of its own size.
 *
 * A mark flush with the end sits on the last character's shoulder and reads as part of the word. Set
 * back from the end it covers text the highlight already owns; set past the end it leaves that text
 * legible and sits on the passage's edge, which is where a tag belongs. Small on purpose — enough to
 * clear the last letter's shoulder, not enough to bury the character after the highlight.
 */
const val HIGHLIGHT_NOTE_BADGE_END_OVERHANG_FRACTION: Float = 0.6f

/**
 * How far the mark may reach past the leading it can measure, as a fraction of its own size.
 *
 * A line box is taller than the type in it: the bottom of the box above a highlight is descender
 * space, which most lines never fill. Using a sliver of it lifts the mark clear of the ascenders it
 * is sitting on, which is what makes it read as a tag on top of the line rather than a chip inside
 * it. Bounded on purpose — this is the only amount by which a mark is allowed to cross into the
 * line above, so a descender there is the worst it can meet.
 */
const val HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION: Float = 0.3f

/** A badge smaller than this has no recognisable shape left, so nothing is drawn. */
private const val HIGHLIGHT_NOTE_BADGE_MIN_SIZE_PX: Float = 5f

/**
 * The badge rect for a highlight whose last line ends at [right] with its top edge at [lineTop].
 *
 * [leadingAbove] is the vertical room between that line and the line above it, when the caller can
 * measure it. It bounds the protrusion: the mark spends that much of itself in the leading, then sits
 * deeper inside the fill rather than reaching further up.
 *
 * A caller that cannot measure the leading (a line box that already covers its own) gets
 * [HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION] of the mark's own height as headroom, which is the small lift
 * that makes it read as a tag rather than a chip.
 */
fun highlightNoteBadgeBounds(
    right: Float,
    lineTop: Float,
    lineHeight: Float,
    leadingAbove: Float? = null,
): HighlightNoteBadgeBounds? {
    if (!right.isFinite() || !lineTop.isFinite() || !lineHeight.isFinite()) return null
    if (lineHeight <= 0f) return null

    val size = lineHeight * HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
    if (!size.isFinite() || size < HIGHLIGHT_NOTE_BADGE_MIN_SIZE_PX) return null

    val measurableLeading = leadingAbove?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val protrusion = (size * HIGHLIGHT_NOTE_BADGE_PROTRUSION_FRACTION)
        .coerceAtMost(measurableLeading + size * HIGHLIGHT_NOTE_BADGE_LIFT_FRACTION)
    // Hangs past the end rather than sitting flush on the last character's shoulder.
    val markRight = right + size * HIGHLIGHT_NOTE_BADGE_END_OVERHANG_FRACTION

    return HighlightNoteBadgeBounds(
        left = markRight - size,
        top = lineTop - protrusion,
        right = markRight,
        bottom = lineTop - protrusion + size,
    )
}

/**
 * The badge rect for a highlight expressed as [lines] (`pdfHighlightLines` order, top to bottom).
 *
 * The mark belongs to the highlight's **end**, which is the last line. When the highlight itself
 * spans several lines the line above is known exactly, so the mark is allowed to use precisely the
 * leading that exists between them; for a single-line highlight that leading is left to the
 * fraction above, because nothing in the highlight describes the line above it.
 */
fun pdfHighlightNoteBadgeBounds(lines: List<PdfHighlightLine>): HighlightNoteBadgeBounds? {
    val last = lines.lastOrNull() ?: return null
    val previous = lines.getOrNull(lines.size - 2)
    val leadingAbove = previous?.let { last.top - (it.top + it.height) }
    return highlightNoteBadgeBounds(
        right = last.right,
        lineTop = last.top,
        lineHeight = last.height,
        leadingAbove = leadingAbove,
    )
}

/** [pdfHighlightNoteBadgeBounds] for a highlight still in raw rect form. */
fun pdfHighlightNoteBadgeBoundsForRects(
    rects: List<PdfHighlightRect>
): HighlightNoteBadgeBounds? = pdfHighlightNoteBadgeBounds(pdfHighlightLines(rects))
