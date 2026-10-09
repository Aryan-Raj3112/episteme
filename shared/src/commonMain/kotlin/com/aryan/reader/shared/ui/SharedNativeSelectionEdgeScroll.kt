package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Per-frame vertical scroll delta while a selection handle is dragged into the top/bottom edge
 * band of the native vertical reader. Mirrors the Android benchmark
 * (`NativeVerticalReaderScreen.kt`): a 64dp band at each edge; delta scales linearly from
 * ±2dp (band edge) to ±28dp (viewport edge); 0 outside the bands.
 *
 * The two bands are **mutually exclusive**, as they are on Android. They previously overlapped
 * here and were summed, so a viewport shorter than `2 * 64dp` (a 128dp-tall reader) added both
 * ramps and scrolled roughly twice as fast as Android. The `when` is the Android shape, so the
 * top band is tested first and a pointer inside both bands scrolls up.
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
    val bottomBandStartPx = rootHeightPx - edgeSizePx
    return when {
        pointerY < edgeSizePx ->
            -((((edgeSizePx - pointerY) / edgeSizePx) * maxScrollStepPx).coerceIn(minScrollStepPx, maxScrollStepPx))
        pointerY > bottomBandStartPx ->
            (((pointerY - bottomBandStartPx) / edgeSizePx) * maxScrollStepPx)
                .coerceIn(minScrollStepPx, maxScrollStepPx)
        else -> 0f
    }
}

internal fun sharedNativeSelectionIsInEdgeBand(delta: Float): Boolean = abs(delta) > 0.5f