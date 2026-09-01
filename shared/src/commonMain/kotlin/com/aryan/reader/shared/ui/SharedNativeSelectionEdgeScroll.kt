package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Per-frame vertical scroll delta while a selection handle is dragged into the top/bottom edge
 * band of the native vertical reader. Mirrors the Android benchmark
 * (`NativeVerticalReaderScreen.kt`): a 64dp band at each edge; delta scales linearly from
 * ±2dp (band edge) to ±28dp (viewport edge); 0 outside the bands.
 */
internal fun sharedNativeSelectionEdgeScrollDelta(
    pointerY: Float,
    rootHeightPx: Float,
    density: Density
): Float {
    if (rootHeightPx <= 0f) return 0f
    val edgeSizePx = with(density) { 64.dp.toPx() }
    val maxScrollStepPx = with(density) { 28.dp.toPx() }
    val minScrollStepPx = with(density) { 2.dp.toPx() }
    val topDelta = if (pointerY < edgeSizePx) {
        -((((edgeSizePx - pointerY) / edgeSizePx) * maxScrollStepPx).coerceIn(minScrollStepPx, maxScrollStepPx))
    } else {
        0f
    }
    val bottomDelta = if (pointerY > rootHeightPx - edgeSizePx) {
        (((pointerY - (rootHeightPx - edgeSizePx)) / edgeSizePx) * maxScrollStepPx).coerceIn(minScrollStepPx, maxScrollStepPx)
    } else {
        0f
    }
    return topDelta + bottomDelta
}

internal fun sharedNativeSelectionIsInEdgeBand(delta: Float): Boolean = abs(delta) > 0.5f