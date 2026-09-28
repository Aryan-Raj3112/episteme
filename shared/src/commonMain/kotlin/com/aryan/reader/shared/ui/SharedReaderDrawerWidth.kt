package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Benchmark: Android's reader sheets rely on Material's default
 * [androidx.compose.material3.ModalDrawerSheet] sizing (min 240.dp, capped at
 * 360.dp) and never pass a width modifier, so the sheet is always a clean
 * 360.dp column.
 *
 * iOS needs an explicit width because CMP's drawer applies the same
 * `sizeIn(min 240.dp, max 360.dp)` cap *after* the caller's modifier chain:
 * a fractional constraint like `fillMaxWidth(0.86f)` (the old shared sheet)
 * conflicts with the cap on wide screens — landscape phones and tablets —
 * producing a squeezed, clipped sheet. Scaling the width with the window
 * keeps the fraction-like feel of the old sheet while staying under the
 * material cap, and keeps phones identical to Android's benchmark.
 */
private val SharedReaderDrawerMinWidth = 300.dp
private val SharedReaderDrawerMaxWidth = 340.dp

fun sharedReaderDrawerSheetWidth(maxWidth: Dp): Dp = when {
    // NaN/Infinity/zero guard. Note `<= Dp.Unspecified` is always true under
    // Float's total ordering (NaN sorts above every value), so test isNaN.
    maxWidth.value.isNaN() || maxWidth.value.isInfinite() || maxWidth <= 0.dp -> SharedReaderDrawerMaxWidth
    else -> (maxWidth * 0.86f).coerceIn(SharedReaderDrawerMinWidth, SharedReaderDrawerMaxWidth)
}

/**
 * Material-capped modal drawer sheet for the shared readers.
 *
 * Measures the window first ([BoxWithConstraints]) and hands the sheet an
 * explicit width via [sharedReaderDrawerSheetWidth], avoiding the
 * fraction-vs-cap constraint conflict described above. Content receives a
 * [androidx.compose.foundation.layout.ColumnScope] exactly like
 * [ModalDrawerSheet].
 */
@Composable
fun SharedReaderDrawerSheet(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val sheetWidth = with(density) { sharedReaderDrawerSheetWidth(maxWidth) }
        ModalDrawerSheet(modifier = Modifier.width(sheetWidth)) {
            content()
        }
    }
}
