package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.UserHighlight

/**
 * One highlight's claim on a block: the character ranges it covers, plus who made it.
 *
 * Ranges are block-local and half-open, matching every renderer this plan feeds.
 */
data class PaintableHighlight(
    val highlight: UserHighlight,
    val ranges: List<IntRange>
)

/**
 * Highlights that paint identically, and the ranges to paint once for them.
 *
 * Two highlights belong to one group when they use the same colour and the same style. That pairing is
 * what makes merging safe: the group's ranges cover the same characters, and filling each of them with
 * one colour produces the same image as filling every original range.
 */
data class HighlightPaintGroup(
    val color: Color,
    val style: HighlightStyle,
    /** Disjoint, ascending, never touching. Filling these never paints a character twice. */
    val ranges: List<IntRange>
)

/**
 * What to paint for a block, decided once and consumed by every renderer.
 *
 * Each renderer used to derive this itself and the derivations disagreed, which is why the same two
 * overlapping highlights looked different on Android and iOS. Android filled one path per range, so
 * two translucent fills compounded over the overlap and left it darker than either side; shared filled
 * one span per distinct colour over the merged region, which does not compound. Neither was wrong on
 * its own terms — they were two answers to one question.
 *
 * The plan is that answer. Ranges merge *within* a group, so a group never double-blends, and groups
 * paint in a fixed order so the result does not depend on list order.
 *
 * This says nothing about which highlights are on the page; that is resolved before a plan is built,
 * and the same resolution feeds hit-testing, so what is painted and what is tappable cannot disagree.
 */
class SharedNativeHighlightPaintPlan(
    val groups: List<HighlightPaintGroup>
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    companion object {
        val Empty = SharedNativeHighlightPaintPlan(emptyList())

        /**
         * Builds the plan, merging each group of same-colour, same-style ranges.
         *
         * Empty and out-of-bounds ranges are dropped rather than clamped. A range that names no
         * characters paints nothing, and letting it into a merge would widen the group across a gap
         * the reader never highlighted.
         */
        fun build(paintable: List<PaintableHighlight>): SharedNativeHighlightPaintPlan {
            if (paintable.isEmpty()) return Empty

            val merged = LinkedHashMap<Pair<Color, HighlightStyle>, MutableList<IntRange>>()
            for (entry in paintable) {
                val color = entry.highlight.renderColor(legacyAlpha = LEGACY_HIGHLIGHT_ALPHA)
                val style = entry.highlight.style
                val bucket = merged.getOrPut(color to style) { mutableListOf() }
                for (range in entry.ranges) {
                    if (range.isEmpty()) continue
                    bucket += range
                }
            }

            val groups = merged.entries
                .filter { it.value.isNotEmpty() }
                // Backgrounds before decorations, so a decoration is never buried under a fill, and
                // colour within each so two runs of identical input paint identically.
                .sortedWith(
                    compareBy(
                    { if (it.key.second == HighlightStyle.BACKGROUND) 0 else 1 },
                    { it.key.first.toArgb() }
                )
                )
                .map { (key, ranges) ->
                    HighlightPaintGroup(
                        color = key.first,
                        style = key.second,
                        ranges = mergeRanges(ranges)
                    )
                }
            return SharedNativeHighlightPaintPlan(groups)
        }

        /**
         * Collapses overlapping ranges into the smallest set of disjoint ranges covering the same
         * characters. Ranges that merely touch stay separate, so two adjacent highlights remain two
         * spans and editing one never affects the other.
         */
        private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
            if (ranges.size < 2) return ranges.sortedBy { it.first }
            val sorted = ranges.sortedWith(compareBy({ it.first }, { it.last }))
            val merged = mutableListOf<IntRange>()
            for (range in sorted) {
                val last = merged.lastOrNull()
                // Overlap only. Ranges that merely abut stay separate, so two adjacent highlights
                // remain two fills and editing one never reaches into the other.
                if (last != null && range.first <= last.last) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    merged += range
                }
            }
            return merged
        }

        /**
         * The alpha a highlight is filled at when its stored colour carries none.
         *
         * Android paints legacy highlights at 0.4 and shared at 0.38. Android is the benchmark, so
         * both use its value; without this a highlight with a palette colour is a visible shade
         * lighter on iOS than on Android.
         */
        const val LEGACY_HIGHLIGHT_ALPHA = 0.4f
    }
}