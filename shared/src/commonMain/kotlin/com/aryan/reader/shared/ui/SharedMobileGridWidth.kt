package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive book-grid width classes shared by the mobile Home and Library grids.
 *
 * Breakpoints follow the Material3 WindowSizeClass standard so every platform
 * agrees without a platform API: compact below 600dp, medium from 600dp,
 * expanded from 840dp. Android maps its `WindowWidthSizeClass` onto these;
 * iOS derives them from the actual available width via `BoxWithConstraints`,
 * which also keeps split-view and slide-over correct.
 */
enum class SharedAndroidHomeWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Upper bound of the compact band (phones, portrait and landscape). */
val SharedMobileCompactMaxWidth: Dp = 600.dp

/** Lower bound of the expanded band (large tablets, landscape). */
val SharedMobileExpandedMinWidth: Dp = 840.dp

/** Horizontal spacing used by every mobile book grid. */
val SharedMobileBookGridSpacing: Dp = 16.dp

/** Minimum cell width backing the medium band. */
val SharedMobileMediumBookCellMin: Dp = 140.dp

/** Minimum cell width backing the expanded band. */
val SharedMobileExpandedBookCellMin: Dp = 160.dp

/**
 * Maps an available width to its width class. Pure so it stays unit-testable
 * and identical on Android and iOS.
 */
fun sharedMobileWidthClassForWidth(availableWidth: Dp): SharedAndroidHomeWidthClass = when {
    availableWidth < SharedMobileCompactMaxWidth -> SharedAndroidHomeWidthClass.COMPACT
    availableWidth < SharedMobileExpandedMinWidth -> SharedAndroidHomeWidthClass.MEDIUM
    else -> SharedAndroidHomeWidthClass.EXPANDED
}

/**
 * Android-benchmark cells for lazy book grids. Phones keep the exact
 * historical `Fixed(3)` layout; larger screens switch to `Adaptive`, which
 * adds columns as the screen grows and therefore caps the card size instead
 * of stretching three huge cards across a tablet.
 */
fun SharedAndroidHomeWidthClass.bookGridCells(): GridCells = when (this) {
    SharedAndroidHomeWidthClass.COMPACT -> GridCells.Fixed(3)
    SharedAndroidHomeWidthClass.MEDIUM -> GridCells.Adaptive(minSize = SharedMobileMediumBookCellMin)
    SharedAndroidHomeWidthClass.EXPANDED -> GridCells.Adaptive(minSize = SharedMobileExpandedBookCellMin)
}

/**
 * Column count for non-lazy chunked-row grids (used where a lazy grid cannot
 * nest inside an outer scrolling column). Mirrors the [GridCells.Adaptive]
 * math so both grid styles agree; compact always returns 3 to preserve the
 * existing phone layout.
 */
fun SharedAndroidHomeWidthClass.bookGridColumns(availableWidth: Dp): Int {
    if (this == SharedAndroidHomeWidthClass.COMPACT) return 3
    val minCell = if (this == SharedAndroidHomeWidthClass.MEDIUM) {
        SharedMobileMediumBookCellMin
    } else {
        SharedMobileExpandedBookCellMin
    }
    return ((availableWidth + SharedMobileBookGridSpacing) / (minCell + SharedMobileBookGridSpacing))
        .toInt()
        .coerceAtLeast(3)
}
