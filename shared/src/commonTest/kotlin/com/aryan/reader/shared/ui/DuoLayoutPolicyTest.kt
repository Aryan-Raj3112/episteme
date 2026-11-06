package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Duo gate has to be invisible everywhere except an iPhone Duo display. These tests
 * pin that: [SharedDeviceLayout.None] must reproduce the exact pre-Duo layout, and only a
 * real Duo layout may change it.
 */
class DuoLayoutPolicyTest {

    /** The Duo inner display: fold division present, wide. */
    private val duoInner = SharedDeviceLayout(
        fold = SharedFoldRegion(present = true, active = false, leftFraction = 0.49f, widthFraction = 0.02f),
        controlColumnWidthFraction = 0.088f,
        controlColumnTopFraction = 0.16f,
        isDuoDevice = true,
    )

    /** The Duo outer display: no fold division, camera/island occlusion only, compact. */
    private val duoOuter = SharedDeviceLayout(
        controlColumnWidthFraction = 0.18f,
        controlColumnTopFraction = 0.257f,
        isDuoDevice = true,
    )

    /** The Duo inner display held upright: fold present, but no side column. */
    private val duoInnerPortrait = SharedDeviceLayout(
        fold = SharedFoldRegion(present = true, active = false, widthFraction = 1f, heightFraction = 0.02f),
        controlColumnWidthFraction = 0f,
        controlColumnTopFraction = 0.086f,
        isDuoDevice = true,
    )

    private val widths = listOf(360.dp, 466.dp, 599.dp, 600.dp, 626.dp, 678.dp, 839.dp, 840.dp, 890.dp, 1280.dp)

    @Test
    fun `no device layout means a plain single-screen device`() {
        assertTrue(!SharedDeviceLayout.None.isDuoInnerDisplay)
        assertTrue(!SharedDeviceLayout.None.isDuoDevice)
        assertTrue(!SharedDeviceLayout.None.fold.present)
    }

    @Test
    fun `non-duo keeps the drawer at every width`() {
        widths.forEach { width ->
            assertEquals(
                SharedLibraryNavMode.DRAWER,
                DuoLayoutPolicy.navMode(SharedDeviceLayout.None, width),
                "width=$width",
            )
        }
    }

    @Test
    fun `non-duo grid is identical to the shared width classes`() {
        widths.forEach { width ->
            assertEquals(
                sharedMobileWidthClassForWidth(width).bookGridCells(),
                DuoLayoutPolicy.gridCells(SharedDeviceLayout.None, width),
                "width=$width",
            )
        }
    }

    @Test
    fun `a regular-width non-duo device is not a Duo`() {
        // An iPad-shaped composition local: regular width, but no fold capability.
        assertEquals(SharedLibraryNavMode.DRAWER, DuoLayoutPolicy.navMode(SharedDeviceLayout.None, 1024.dp))
    }

    @Test
    fun `duo displays with a side column get the rail, ordinary devices never do`() {
        // Apple's Duo bar runs down the side of the outer display too.
        assertEquals(SharedLibraryNavMode.RAIL, DuoLayoutPolicy.navMode(duoOuter, 466.dp))
        assertEquals(SharedLibraryNavMode.RAIL, DuoLayoutPolicy.navMode(duoInner, 626.dp))
        assertEquals(SharedLibraryNavMode.RAIL, DuoLayoutPolicy.navMode(duoInner, 890.dp))
        // Even a wide ordinary device stays on the drawer.
        assertEquals(SharedLibraryNavMode.DRAWER, DuoLayoutPolicy.navMode(SharedDeviceLayout.None, 1280.dp))
    }

    @Test
    fun `upright duo inner display uses the horizontal top bar`() {
        // Apple keeps horizontal bars in this one pose; there is no side column to hold a
        // rail, so the destinations belong in the top bar.
        assertEquals(SharedLibraryNavMode.HORIZONTAL, DuoLayoutPolicy.navMode(duoInnerPortrait, 669.dp))
        assertTrue(!DuoLayoutPolicy.railVisible(duoInnerPortrait, 669.dp))
        assertTrue(DuoLayoutPolicy.horizontalNavVisible(duoInnerPortrait, 669.dp))
    }

    @Test
    fun `non-duo has no rail geometry`() {
        assertEquals(0.dp, DuoLayoutPolicy.railWidth(SharedDeviceLayout.None, 1000.dp))
        assertEquals(0.dp, DuoLayoutPolicy.railTopInset(SharedDeviceLayout.None, 800.dp))
        assertTrue(!DuoLayoutPolicy.railVisible(SharedDeviceLayout.None, 1000.dp))
    }

    @Test
    fun `duo rail covers the control column and clears the status area`() {
        val layout = SharedDeviceLayout(
            fold = SharedFoldRegion(present = true, active = false),
            controlColumnWidthFraction = 0.09f,
            controlColumnTopFraction = 0.16f,
            controlColumnOnTrailing = false,
            isDuoDevice = true,
        )
        // Wider than the 84dp baseline because the platform reserved more.
        assertEquals(90.dp, DuoLayoutPolicy.railWidth(layout, 1000.dp))
        // Starts below the system status area, not at the very top.
        assertEquals(128.dp, DuoLayoutPolicy.railTopInset(layout, 800.dp))
        // Narrower strip than the baseline: the Apple width still wins.
        assertEquals(DuoLayoutPolicy.RailWidth, DuoLayoutPolicy.railWidth(layout, 500.dp))
        assertEquals(false, DuoLayoutPolicy.railOnTrailing(layout))
    }

    @Test
    fun `account sidebar follows the status column instead of assuming one`() {
        // Landscape inner: the status lives in a side column, so the sidebar clears it and
        // still runs the full height.
        assertEquals(
            DuoLayoutPolicy.railWidth(duoInner, 951.dp),
            DuoLayoutPolicy.accountSidebarEndInset(duoInner, 951.dp),
        )
        assertEquals(0.dp, DuoLayoutPolicy.accountSidebarTopInset(duoInner, 669.dp))

        // Upright inner display: no side column, so the sidebar is flush with the trailing
        // edge and starts below the top strip that carries the destinations.
        assertEquals(0.dp, DuoLayoutPolicy.accountSidebarEndInset(duoInnerPortrait, 669.dp))
        assertEquals(
            DuoLayoutPolicy.railTopInset(duoInnerPortrait, 951.dp),
            DuoLayoutPolicy.accountSidebarTopInset(duoInnerPortrait, 951.dp),
        )

        // Every other device keeps its plain edge-to-edge drawer on both axes.
        assertEquals(0.dp, DuoLayoutPolicy.accountSidebarEndInset(SharedDeviceLayout.None, 600.dp))
        assertEquals(0.dp, DuoLayoutPolicy.accountSidebarTopInset(SharedDeviceLayout.None, 800.dp))
    }

    @Test
    fun `duo inner grid uses a smaller adaptive cell on every width`() {
        // The cell size is width-independent; the column count still follows the width.
        widths.forEach { width ->
            assertEquals(
                GridCells.Adaptive(DuoLayoutPolicy.DuoBookCellMin),
                DuoLayoutPolicy.gridCells(duoInner, width),
                "width=$width",
            )
        }
        // Smaller than the shared medium band, so the wide inner display gets more covers.
        assertTrue(DuoLayoutPolicy.DuoBookCellMin < SharedMobileMediumBookCellMin)
    }

    @Test
    fun `duo outer grid keeps the ordinary width-class mapping`() {
        // The outer display is phone-width, so the Duo cell cap must not apply there.
        widths.forEach { width ->
            assertEquals(
                sharedMobileWidthClassForWidth(width).bookGridCells(),
                DuoLayoutPolicy.gridCells(duoOuter, width),
                "width=$width",
            )
        }
    }
}
