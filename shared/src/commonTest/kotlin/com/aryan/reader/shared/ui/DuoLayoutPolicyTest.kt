package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Duo gate has to be invisible everywhere except an iPhone Duo inner display. These
 * tests pin that: [SharedDeviceLayout.None] must reproduce the exact pre-Duo layout, and
 * only a real Duo layout may change it.
 */
class DuoLayoutPolicyTest {

    private val duo = SharedDeviceLayout(
        fold = SharedFoldRegion(present = true, active = false, leftFraction = 0.49f, widthFraction = 0.02f),
    )

    private val widths = listOf(360.dp, 466.dp, 599.dp, 600.dp, 626.dp, 678.dp, 839.dp, 840.dp, 890.dp, 1280.dp)

    @Test
    fun `no device layout means a plain single-screen device`() {
        assertTrue(!SharedDeviceLayout.None.isDuoInnerDisplay)
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
    fun `duo rail only on the wide inner display`() {
        // Outer display, portrait (compact) keeps the drawer.
        assertEquals(SharedLibraryNavMode.DRAWER, DuoLayoutPolicy.navMode(duo, 466.dp))
        // Inner display, both orientations, gets the rail.
        assertEquals(SharedLibraryNavMode.RAIL, DuoLayoutPolicy.navMode(duo, 626.dp))
        assertEquals(SharedLibraryNavMode.RAIL, DuoLayoutPolicy.navMode(duo, 890.dp))
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
    fun `duo grid uses a smaller adaptive cell on every width`() {
        // The cell size is width-independent; the column count still follows the width.
        widths.forEach { width ->
            assertEquals(
                GridCells.Adaptive(DuoLayoutPolicy.DuoBookCellMin),
                DuoLayoutPolicy.gridCells(duo, width),
                "width=$width",
            )
        }
        // Smaller than the shared medium band, so the wide inner display gets more covers.
        assertTrue(DuoLayoutPolicy.DuoBookCellMin < SharedMobileMediumBookCellMin)
    }
}
