package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Sizing for one table cell in [SharedNativeTableRow].
 *
 * Tables previously used `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))`
 * to stretch every cell to the tallest cell. That intrinsic query crashes as soon
 * as any cell contains a `SubcomposeLayout` (for example `BoxWithConstraints`
 * used by [SharedNativeImageBlock], or `Text` with `InlineTextContent` for inline
 * math), because `SubcomposeLayout` does not support intrinsic measurement.
 */
internal sealed interface SharedNativeTableCellSizing {
    data class Fixed(val width: Dp) : SharedNativeTableCellSizing
    data class Weighted(val weight: Float) : SharedNativeTableCellSizing
}

/**
 * Pure width distribution used by [SharedNativeTableRow].
 *
 * @param availableWidthPx width available for cells (total width minus gaps).
 * @param fixedWidthsPx fixed cell widths in px, or null for weighted cells.
 * @param weights weight per cell (only used when the cell is not fixed).
 * @return width per cell in px; weighted widths always sum to
 *   `(availableWidthPx - fixedSum).coerceAtLeast(0)` up to rounding.
 */
internal fun sharedNativeTableCellWidthsPx(
    availableWidthPx: Int,
    fixedWidthsPx: List<Int?>,
    weights: List<Float>,
): List<Int> {
    require(fixedWidthsPx.size == weights.size) {
        "fixedWidths and weights must have the same size"
    }
    if (fixedWidthsPx.isEmpty()) return emptyList()
    val available = availableWidthPx.coerceAtLeast(0)
    val fixedSum = fixedWidthsPx.filterNotNull().sum().coerceAtLeast(0)
    val remaining = (available - fixedSum).coerceAtLeast(0)
    val totalWeight = fixedWidthsPx.indices
        .sumOf { index ->
            if (fixedWidthsPx[index] != null) {
                0.0
            } else {
                weights[index].toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            }
        }
        .takeIf { it > 0.0 } ?: 1.0
    val widths = ArrayList<Int>(fixedWidthsPx.size)
    fixedWidthsPx.forEachIndexed { index, fixed ->
        if (fixed != null) {
            widths += fixed.coerceAtLeast(0)
        } else {
            val weight = weights[index].toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            widths += ((remaining * weight) / totalWeight).roundToInt().coerceAtLeast(0)
        }
    }
    // Absorb integer rounding remainder in the last weighted cell so the row
    // exactly fills the available width, matching Row weight distribution.
    val weightedSum = widths.indices.sumOf { index ->
        if (fixedWidthsPx[index] == null) widths[index] else 0
    }
    val diff = remaining - weightedSum
    if (diff != 0) {
        val lastWeighted = widths.indices.lastOrNull { fixedWidthsPx[it] == null }
        if (lastWeighted != null) {
            widths[lastWeighted] = (widths[lastWeighted] + diff).coerceAtLeast(0)
        }
    }
    return widths
}

/**
 * Equal-height table row without intrinsic measurement.
 *
 * Measures every cell with a normal (non-intrinsic) pass using its distributed
 * width, takes the tallest height, then remeasures every cell at that exact
 * height so backgrounds/borders stretch like `IntrinsicSize.Min` did, without
 * ever querying intrinsics on `SubcomposeLayout` children.
 */
@Composable
internal fun SharedNativeTableRow(
    sizings: List<SharedNativeTableCellSizing>,
    cellGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }
        val gapPx = cellGap.coerceAtLeast(0.dp).roundToPx()
        val totalGapPx = gapPx * (measurables.size - 1).coerceAtLeast(0)
        val maxWidth = constraints.maxWidth

        if (maxWidth == Constraints.Infinity || measurables.size != sizings.size) {
            // Unconstrained or unexpected: fall back to loose measurement.
            // This path never queries intrinsics either.
            val placeables = measurables.map { measurable ->
                measurable.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = if (maxWidth == Constraints.Infinity) Constraints.Infinity else maxWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    ),
                )
            }
            val width = if (maxWidth == Constraints.Infinity) {
                placeables.sumOf { it.width } + totalGapPx
            } else {
                (placeables.sumOf { it.width } + totalGapPx).coerceIn(constraints.minWidth, maxWidth)
            }
            val height = (placeables.maxOfOrNull { it.height } ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            return@Layout layout(width, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, 0)
                    x += placeable.width + gapPx
                }
            }
        }

        val available = (maxWidth - totalGapPx).coerceAtLeast(0)
        val fixedWidthsPx = sizings.map { sizing ->
            when (sizing) {
                is SharedNativeTableCellSizing.Fixed -> sizing.width.roundToPx().coerceAtLeast(0)
                is SharedNativeTableCellSizing.Weighted -> null
            }
        }
        val weights = sizings.map { sizing ->
            when (sizing) {
                is SharedNativeTableCellSizing.Fixed -> 0f
                is SharedNativeTableCellSizing.Weighted -> sizing.weight
            }
        }
        val widths = sharedNativeTableCellWidthsPx(
            availableWidthPx = available,
            fixedWidthsPx = fixedWidthsPx,
            weights = weights,
        )
        // First pass: natural heights at distributed widths (normal measure, safe).
        val maxHeightBound = constraints.maxHeight
        val firstPass = measurables.mapIndexed { index, measurable ->
            val cellWidth = widths.getOrElse(index) { 0 }
            measurable.measure(
                Constraints(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = 0,
                    maxHeight = maxHeightBound,
                ),
            )
        }
        var rowHeight = firstPass.maxOfOrNull { it.height } ?: 0
        if (maxHeightBound != Constraints.Infinity) {
            rowHeight = rowHeight.coerceAtMost(maxHeightBound)
        }
        rowHeight = rowHeight.coerceAtLeast(constraints.minHeight).coerceAtLeast(0)
        // Second pass: stretch every cell to the tallest height so cell
        // backgrounds/borders fill the row, like IntrinsicSize.Min.
        val secondPass = measurables.mapIndexed { index, measurable ->
            val cellWidth = widths.getOrElse(index) { 0 }
            measurable.measure(
                Constraints(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = rowHeight,
                    maxHeight = rowHeight,
                ),
            )
        }
        val contentWidth = widths.sum() + totalGapPx
        val layoutWidth = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = rowHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            var x = 0
            secondPass.forEachIndexed { index, placeable ->
                placeable.placeRelative(x, 0)
                x += widths.getOrElse(index) { placeable.width } + gapPx
            }
        }
    }
}
