package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the mobile library exposes its top-level destinations. */
enum class SharedLibraryNavMode {
    /** Compact/mobile: the destinations live in the existing modal drawer. */
    DRAWER,

    /** Duo inner display: the destinations live in a permanent leading rail. */
    RAIL,
}

/**
 * Pure layout decisions for iPhone Duo, kept out of composables so they are unit-testable
 * without a device or a Compose runtime.
 *
 * Every function is the identity of the pre-Duo behaviour when
 * [SharedDeviceLayout.isDuoInnerDisplay] is false. That is the mechanism that guarantees this
 * work cannot change the phone, tablet or desktop layouts.
 */
object DuoLayoutPolicy {

    /** Below this available width even a Duo inner display stays with the drawer. */
    val RailMinWidth: Dp = 600.dp

    /** Duo inner display: a permanent side rail instead of the hamburger drawer. */
    fun navMode(layout: SharedDeviceLayout, availableWidth: Dp): SharedLibraryNavMode {
        return if (layout.isDuoInnerDisplay && availableWidth >= RailMinWidth) {
            SharedLibraryNavMode.RAIL
        } else {
            SharedLibraryNavMode.DRAWER
        }
    }

    /**
     * Cell size on the Duo inner display.
     *
     * The inner display is wide, so the width classes would pick the *expanded* band and
     * stretch each cover to ~190dp. On a 7.6" panel that reads as oversized, so Duo uses
     * a smaller adaptive minimum: the same maths, a denser grid (~5 covers across the
     * unfolded inner display instead of ~4). It stays below the shared *medium* minimum
     * because the Duo is wide but physically small.
     */
    val DuoBookCellMin: Dp = 130.dp

    /**
     * The lazy-grid cell configuration.
     *
     * Non-Duo returns exactly what the shared width classes returned before (Fixed on
     * compact, Adaptive above). The Duo inner display uses a capped adaptive cell, so its
     * column count still follows the available width in every pose.
     */
    fun gridCells(layout: SharedDeviceLayout, availableWidth: Dp): GridCells {
        if (!layout.isDuoInnerDisplay) {
            return sharedMobileWidthClassForWidth(availableWidth).bookGridCells()
        }
        return GridCells.Adaptive(minSize = DuoBookCellMin)
    }
}
