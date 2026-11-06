package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical rhythm of the unified library.
 *
 * Every field is a *space* around content. Shrinking type or the book cells on a physically
 * small display makes them harder to read, and the complaint on the cover display was that
 * the grid had no room left, so this only tightens the chrome that surrounds it.
 */
data class SharedAndroidHomeDensity(
    /** True on a display too short for the full card and gaps. */
    val compact: Boolean,
    val searchRowHeight: Dp,
    /** Top margin for the bar on a Duo display; other devices already get it from the inset. */
    val topBarTopPadding: Dp,
    val continueCardTopGap: Dp,
    val continueCardHeight: Dp,
    val continueCoverWidth: Dp,
    val continueCoverHeight: Dp,
    val filterRowTopGap: Dp,
    val gridCardTopGap: Dp,
    val gridContentTopPadding: Dp,
    /**
     * Minimum book cell. Compact drops below the Duo baseline so a short display fits four
     * columns instead of three: at three the cover alone is ~225dp, which is more than the
     * whole grid area once the chrome is taken out.
     */
    val bookCellMin: Dp,
) {
    companion object {
        /** Today's values, and what every non-Duo host and every tall Duo display gets. */
        val Standard = SharedAndroidHomeDensity(
            compact = false,
            searchRowHeight = 64.dp,
            topBarTopPadding = 16.dp,
            continueCardTopGap = 16.dp,
            continueCardHeight = 178.dp,
            continueCoverWidth = 94.dp,
            continueCoverHeight = 146.dp,
            filterRowTopGap = 12.dp,
            gridCardTopGap = 16.dp,
            gridContentTopPadding = 16.dp,
            bookCellMin = DuoLayoutPolicy.DuoBookCellMin,
        )

        /**
         * The Duo cover display held landscape. Recovers roughly 150dp for the grid: the
         * continue-reading card alone was 38% of the display.
         */
        val Compact = SharedAndroidHomeDensity(
            compact = true,
            // The search field's own minimum height is 56dp; below that it clips.
            searchRowHeight = 56.dp,
            topBarTopPadding = 8.dp,
            continueCardTopGap = 8.dp,
            continueCardHeight = 72.dp,
            continueCoverWidth = 36.dp,
            continueCoverHeight = 56.dp,
            filterRowTopGap = 6.dp,
            gridCardTopGap = 10.dp,
            gridContentTopPadding = 10.dp,
            bookCellMin = 112.dp,
        )
    }
}

/** How the mobile library exposes its top-level destinations. */
enum class SharedLibraryNavMode {
    /** Compact/mobile: the destinations live in the existing modal drawer. */
    DRAWER,

    /** Duo inner display in landscape, and the outer display: a permanent rail on the
     *  control edge. */
    RAIL,

    /**
     * Duo inner display held upright. That is the one pose Apple keeps *horizontal* bars
     * for, because there is plenty of vertical space, so the destinations become a row in
     * the top bar instead of a side column.
     */
    HORIZONTAL,
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

    /**
     * Safety floor only: every Duo display is far wider than this, so a Duo always gets
     * the rail. It exists so a hypothetical narrow foldable would keep the drawer.
     */
    val RailMinWidth: Dp = 360.dp

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
     * Below this window height a Duo display cannot afford the standard library chrome.
     *
     * Measured against the Duo's own displays: the cover display held landscape is 466dp
     * tall and is the only pose that qualifies; the same cover display upright is 678dp,
     * the inner display is 669dp in landscape and 951dp upright. Anything in (466, 669)
     * separates them, and the middle of that gap leaves room for the seated and tent poses
     * and for Split View.
     */
    val ShortDisplayHeight: Dp = 560.dp

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
     * DRAWER on every ordinary device; the rail on both Duo displays. Apple's Duo bar runs
     * down the side of the outer display too, so the outer display is not an exception.
     */
    fun navMode(layout: SharedDeviceLayout, availableWidth: Dp): SharedLibraryNavMode {
        if (!layout.isDuoDevice || availableWidth < RailMinWidth) {
            return SharedLibraryNavMode.DRAWER
        }
        // A platform-reserved side column means the display has somewhere to put a rail.
        // The inner display upright has none (its status bar runs across the top), which is
        // exactly Apple's horizontal-bar pose.
        return if (layout.hasControlColumn) {
            SharedLibraryNavMode.RAIL
        } else {
            SharedLibraryNavMode.HORIZONTAL
        }
    }

    /** True when the rail is drawn — i.e. the layout is [SharedLibraryNavMode.RAIL]. */
    fun railVisible(layout: SharedDeviceLayout, availableWidth: Dp): Boolean {
        return navMode(layout, availableWidth) == SharedLibraryNavMode.RAIL
    }

    /** True when the destinations sit in a row in the top bar instead of a side rail. */
    fun horizontalNavVisible(layout: SharedDeviceLayout, availableWidth: Dp): Boolean {
        return navMode(layout, availableWidth) == SharedLibraryNavMode.HORIZONTAL
    }

    /**
     * The rail's width: the Apple baseline, widened to cover the platform's reserved strip
     * if that is wider, so app content can never end up underneath the system status.
     */
    fun railWidth(layout: SharedDeviceLayout, availableWidth: Dp): Dp {
        if (!layout.isDuoDevice) return 0.dp
        return maxOf(RailWidth, availableWidth * layout.controlColumnWidthFraction)
    }

    /**
     * Space above the rail's first item, so it starts below the system's Dynamic Island /
     * status area instead of under it.
     */
    fun railTopInset(layout: SharedDeviceLayout, availableHeight: Dp): Dp {
        if (!layout.isDuoDevice) return 0.dp
        return maxOf(RailTopGap, availableHeight * layout.controlColumnTopFraction)
    }

    /** The rail follows the platform's control column edge (trailing, or leading in the
     *  left half of Split View). */
    fun railOnTrailing(layout: SharedDeviceLayout): Boolean {
        return layout.controlColumnOnTrailing
    }

    /**
     * The library's vertical rhythm for this display.
     *
     * Height is the honest signal here, not the fold: folding is what *produces* the short
     * display, and the same cover display upright is tall enough for the standard chrome.
     * Gating on the fold would shrink a pose that reads fine.
     */
    fun libraryDensity(layout: SharedDeviceLayout, availableHeight: Dp): SharedAndroidHomeDensity {
        if (!layout.isDuoDevice) return SharedAndroidHomeDensity.Standard
        return if (availableHeight < ShortDisplayHeight) {
            SharedAndroidHomeDensity.Compact
        } else {
            SharedAndroidHomeDensity.Standard
        }
    }

    /**
     * Trailing inset for the account sidebar, which opens on the same edge as the system's
     * status column. A display whose column shares that edge keeps the sidebar clear of it;
     * a display with no column on that edge (the upright inner display, whose status runs
     * across the top, or a column on the leading edge) opens the sidebar flush against the
     * edge instead of reserving a column that is not there. Zero on every other device.
     */
    fun accountSidebarEndInset(layout: SharedDeviceLayout, availableWidth: Dp): Dp {
        // The sidebar is always mirrored to the trailing edge. Only a trailing control
        // column is on the same edge as it; when the platform put the column on the leading
        // edge (the cover display held landscape, the left half of Split View) there is
        // nothing over the sidebar to clear.
        if (!layout.hasControlColumn || !layout.controlColumnOnTrailing) return 0.dp
        return railWidth(layout, availableWidth)
    }

    /**
     * Top inset for the account sidebar. Wherever there is no status column the
     * destinations ride in a top strip, so the sidebar starts below that strip rather than
     * covering it. Zero wherever the sidebar already shares the edge with the column.
     */
    fun accountSidebarTopInset(layout: SharedDeviceLayout, availableHeight: Dp): Dp {
        if (layout.hasControlColumn) return 0.dp
        return railTopInset(layout, availableHeight)
    }

    /**
     * The lazy-grid cell configuration.
     *
     * Non-Duo returns exactly what the shared width classes returned before (Fixed on
     * compact, Adaptive above). The Duo inner display uses a capped adaptive cell, so its
     * column count still follows the available width in every pose. A short display gets the
     * denser compact cell instead, whichever class it would otherwise have landed in.
     */
    fun gridCells(
        layout: SharedDeviceLayout,
        availableWidth: Dp,
        density: SharedAndroidHomeDensity = SharedAndroidHomeDensity.Standard,
    ): GridCells {
        // A display too short for the standard chrome cannot afford a three-column row
        // either: the cover alone would be taller than the whole grid area.
        if (density.compact) return GridCells.Adaptive(minSize = density.bookCellMin)
        if (!layout.isDuoInnerDisplay) {
            return sharedMobileWidthClassForWidth(availableWidth).bookGridCells()
        }
        return GridCells.Adaptive(minSize = DuoBookCellMin)
    }
}
