package com.aryan.reader.paginatedreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Flex arrangement mappings shared by both native stacks (B1 step 3, flex
 * block kind).
 *
 * Android's paginated flex branch and shared's flex branch carried identical
 * four-way `justifyContent`/`alignItems` tables. Only the tables live here.
 * Deliberately not covered: Android's vertical flex path (which ignores both
 * properties — an Android-internal inconsistency, recorded not ported), the
 * nested-flex third behavior, and chant `FlowRow` (product decision, needs
 * paginator work).
 */

fun nativeFlexRowHorizontalArrangement(
    justifyContent: String?,
    gap: Dp = Dp.Unspecified
): Arrangement.Horizontal =
    if (gap.isSpecified && gap > 0.dp) gapHorizontalArrangement(justifyContent, gap)
    else when (justifyContent) {
        "center" -> Arrangement.Center
        "flex-end" -> Arrangement.End
        "space-between" -> Arrangement.SpaceBetween
        "space-around" -> Arrangement.SpaceAround
        "space-evenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.Start
    }

fun nativeFlexColumnVerticalArrangement(
    justifyContent: String?,
    gap: Dp = Dp.Unspecified
): Arrangement.Vertical =
    if (gap.isSpecified && gap > 0.dp) gapVerticalArrangement(justifyContent, gap)
    else when (justifyContent) {
        "center" -> Arrangement.Center
        "flex-end" -> Arrangement.Bottom
        "space-between" -> Arrangement.SpaceBetween
        "space-around" -> Arrangement.SpaceAround
        "space-evenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.Top
    }

fun nativeFlexRowVerticalAlignment(alignItems: String?): Alignment.Vertical = when (alignItems) {
    "center" -> Alignment.CenterVertically
    "flex-end" -> Alignment.Bottom
    else -> Alignment.Top
}

fun nativeFlexColumnHorizontalAlignment(alignItems: String?): Alignment.Horizontal = when (alignItems) {
    "center" -> Alignment.CenterHorizontally
    "flex-end" -> Alignment.End
    else -> Alignment.Start
}

/**
 * True when `align-self` opts this item out of the container's `stretch`, so it
 * is sized to its content and positioned by [nativeFlexColumnChildAlignment]
 * instead of filling the cross axis.
 */
fun BlockStyle.hasSelfAlignment(): Boolean = when (alignSelf?.lowercase()) {
    null, "auto", "stretch", "baseline" -> false
    else -> true
}

/**
 * `align-self` for one item inside a column flex container. `auto`, `stretch`
 * and `baseline` defer to the container's `align-items`, and an absent
 * `align-items` is CSS `stretch`, which a full-width paragraph already does.
 */
fun nativeFlexColumnChildAlignment(alignSelf: String?, alignItems: String?): Alignment.Horizontal =
    when (alignSelf?.lowercase()) {
        "flex-start", "start", "self-start" -> Alignment.Start
        "flex-end", "end", "self-end" -> Alignment.End
        "center" -> Alignment.CenterHorizontally
        else -> nativeFlexColumnHorizontalAlignment(alignItems)
    }

/** `align-self` for one item inside a row flex container (cross axis is vertical). */
fun nativeFlexRowChildVerticalAlignment(alignSelf: String?, alignItems: String?): Alignment.Vertical =
    when (alignSelf?.lowercase()) {
        "flex-start", "start", "self-start" -> Alignment.Top
        "flex-end", "end", "self-end" -> Alignment.Bottom
        "center" -> Alignment.CenterVertically
        else -> nativeFlexRowVerticalAlignment(alignItems)
    }

/**
 * Leading offset and extra inter-item spacing, in px, left over for a flex line
 * of [count] items after [freeSpace] is distributed the way `justify-content`
 * asks. `gap` is applied separately by the callers, so this only spreads the
 * remaining space.
 */
internal fun distributeFlexFreeSpace(
    justifyContent: String?,
    freeSpace: Int,
    count: Int
): Pair<Int, Int> {
    if (count <= 0) return 0 to 0
    val free = freeSpace.coerceAtLeast(0)
    return when (justifyContent) {
        "flex-end", "end" -> free to 0
        "center" -> (free / 2) to 0
        "space-between" -> 0 to if (count > 1) free / (count - 1) else 0
        "space-around" -> (free / (2 * count)) to (free / count)
        "space-evenly" -> {
            val unit = free / (count + 1)
            unit to unit
        }
        else -> 0 to 0
    }
}

private fun gapVerticalArrangement(justifyContent: String?, gap: Dp): Arrangement.Vertical =
    object : Arrangement.Vertical {
        override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
            val gapPx = gap.roundToPx()
            val gaps = gapPx * (sizes.size - 1).coerceAtLeast(0)
            val (leading, between) =
                distributeFlexFreeSpace(justifyContent, totalSize - sizes.sum() - gaps, sizes.size)
            var position = leading
            sizes.forEachIndexed { index, size ->
                outPositions[index] = position
                position += size + gapPx + between
            }
        }
    }

private fun gapHorizontalArrangement(justifyContent: String?, gap: Dp): Arrangement.Horizontal =
    object : Arrangement.Horizontal {
        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            layoutDirection: LayoutDirection,
            outPositions: IntArray
        ) {
            val gapPx = gap.roundToPx()
            val gaps = gapPx * (sizes.size - 1).coerceAtLeast(0)
            val (leading, between) =
                distributeFlexFreeSpace(justifyContent, totalSize - sizes.sum() - gaps, sizes.size)
            var position = leading
            sizes.forEachIndexed { index, size ->
                outPositions[index] = position
                position += size + gapPx + between
            }
            if (layoutDirection == LayoutDirection.Rtl) {
                sizes.forEachIndexed { index, size ->
                    outPositions[index] = totalSize - outPositions[index] - size
                }
            }
        }
    }
