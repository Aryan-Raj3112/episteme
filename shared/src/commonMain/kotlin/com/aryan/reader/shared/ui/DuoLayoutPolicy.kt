package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the mobile library exposes its top-level destinations. */
enum class SharedLibraryNavMode {
    /** Compact/mobile: the destinations live in the existing modal drawer. */
    DRAWER,

    /** Duo inner display: the destinations live in a permanent rail on the control edge. */
    RAIL,
}

/**
 * Pure layout decisions for the foldable iPhone, kept out of composables so they are
 * unit-testable without a device or a Compose runtime.
 *
 * Every function is the identity of the pre-Duo behaviour when
 * [SharedDeviceLayout.isDuoInnerDisplay] is false. That is the mechanism that guarantees
 * this work cannot change the phone, tablet or desktop layouts.
 */
object DuoLayoutPolicy {

    /** Below this available width even a Duo inner display stays with the drawer. */
    val RailMinWidth: Dp = 600.dp

    /**
     * Base rail width.
     *
     * Apple's UI Kit sizes the display's vertical bar at 84pt: 12 leading padding, a 48pt
     * control column, 24 trailing. Our rail reproduces that so its controls sit on the
     * same axis as the system's status ring above them. The rail still widens if the
     * platform reserves a wider strip than this.
     */
    val RailWidth: Dp = 84.dp

    /** Minimum space between the system status area and the rail's first item. */
    val RailTopGap: Dp = 8.dp

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

    /** DRAWER everywhere except the Duo inner display when it is wide enough for a rail. */
    fun navMode(layout: SharedDeviceLayout, availableWidth: Dp): SharedLibraryNavMode {
        return if (layout.isDuoInnerDisplay && availableWidth >= RailMinWidth) {
            SharedLibraryNavMode.RAIL
        } else {
            SharedLibraryNavMode.DRAWER
        }
    }

    /** True when the rail is drawn — i.e. the layout is [SharedLibraryNavMode.RAIL]. */
    fun railVisible(layout: SharedDeviceLayout, availableWidth: Dp): Boolean {
        return navMode(layout, availableWidth) == SharedLibraryNavMode.RAIL
    }

    /**
     * The rail's width: the Apple baseline, widened to cover the platform's reserved strip
     * if that is wider, so app content can never end up underneath the system status.
     */
    fun railWidth(layout: SharedDeviceLayout, availableWidth: Dp): Dp {
        if (!layout.isDuoInnerDisplay) return 0.dp
        return maxOf(RailWidth, availableWidth * layout.controlColumnWidthFraction)
    }

    /**
     * Space above the rail's first item, so it starts below the system's Dynamic Island /
     * status area instead of under it.
     */
    fun railTopInset(layout: SharedDeviceLayout, availableHeight: Dp): Dp {
        if (!layout.isDuoInnerDisplay) return 0.dp
        return maxOf(RailTopGap, availableHeight * layout.controlColumnTopFraction)
    }

    /** The rail follows the platform's control column edge (trailing, or leading in the
     *  left half of Split View). */
    fun railOnTrailing(layout: SharedDeviceLayout): Boolean {
        return layout.controlColumnOnTrailing
    }

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
