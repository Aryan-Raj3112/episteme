package com.aryan.reader.shared.ui

import com.aryan.reader.shared.UserHighlight

/**
 * One highlight the reader can select, hit at a character offset.
 *
 * [range] is block-local and inclusive at both ends, matching the layout APIs the callers measure with.
 */
data class HighlightHit(
    val highlight: UserHighlight,
    val range: IntRange
)

/**
 * Every highlight covering a character offset, in the order they were created.
 *
 * Highlights overlap by design — the reader may highlight the same words twice, in different colours —
 * and both hit-testers used to return a single match. Android walked its list backwards and took the
 * first hit; shared took the first annotation. So on an overlap one of the two was unreachable no
 * matter where the reader tapped: on one platform or the other, one was dead. Returning all of them
 * is what makes an overlap navigable.
 *
 * A reading-position band is never returned. It is not the reader's, so there is nothing to open.
 */
fun highlightHitsAt(
    offset: Int,
    highlights: List<PaintableHighlight>
): List<HighlightHit> {
    if (offset < 0) return emptyList()
    val hits = mutableListOf<HighlightHit>()
    for (entry in highlights) {
        if (entry.highlight.isTransientPlaybackBand) continue
        val range = entry.ranges.firstOrNull { offset in it } ?: continue
        hits += HighlightHit(entry.highlight, range)
    }
    return hits
}

/**
 * The single highlight a plain tap opens.
 *
 * The most recently created of those hit wins: it is the one drawn last, so its colour is what the
 * reader sees at that offset, and its identity is what they think they tapped. Ties cannot occur —
 * list order is the order the reader created them in.
 *
 * Callers that can offer the rest use [highlightHitsAt] to show the alternatives; this is only the
 * default.
 */
fun primaryHighlightHitAt(
    offset: Int,
    highlights: List<PaintableHighlight>
): HighlightHit? = highlightHitsAt(offset, highlights).lastOrNull()