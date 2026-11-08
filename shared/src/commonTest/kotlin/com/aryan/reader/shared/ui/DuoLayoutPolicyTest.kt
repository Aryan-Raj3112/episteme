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
    fun `duo displays with a side column get the rail while ordinary devices never do`() {
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
    fun `standard density keeps the pre-Duo numbers`() {
        // Pins the byte-identical guarantee: these are the literals the library used
        // before this work, and every non-Duo host still resolves to them.
        val density = SharedAndroidHomeDensity.Standard
        assertEquals(64.dp, density.searchRowHeight)
        assertEquals(16.dp, density.topBarTopPadding)
        assertEquals(16.dp, density.continueCardTopGap)
        assertEquals(178.dp, density.continueCardHeight)
        assertEquals(94.dp, density.continueCoverWidth)
        assertEquals(146.dp, density.continueCoverHeight)
        assertEquals(12.dp, density.filterRowTopGap)
        assertEquals(16.dp, density.gridCardTopGap)
        assertEquals(16.dp, density.gridContentTopPadding)
        assertEquals(DuoLayoutPolicy.DuoBookCellMin, density.bookCellMin)
    }

    @Test
    fun `compact density also densifies the grid`() {
        // Three columns at the standard cell leave the cover taller than the whole grid
        // area on a 466dp display, so the compact cell has to be smaller.
        assertEquals(
            GridCells.Adaptive(SharedAndroidHomeDensity.Compact.bookCellMin),
            DuoLayoutPolicy.gridCells(duoOuter, 678.dp, SharedAndroidHomeDensity.Compact),
        )
        // Without it, the outer display keeps the shared width classes it had before.
        assertEquals(
            sharedMobileWidthClassForWidth(678.dp).bookGridCells(),
            DuoLayoutPolicy.gridCells(duoOuter, 678.dp),
        )
    }

    @Test
    fun `only the cover display held landscape gets the compact chrome`() {
        // 466dp tall: the one Duo pose with no room for the full continue-reading card.
        assertTrue(DuoLayoutPolicy.libraryDensity(duoOuter, 466.dp).compact)

        // Every tall Duo pose, including the same cover display held upright.
        listOf(669.dp, 678.dp, 951.dp).forEach { height ->
            assertTrue(!DuoLayoutPolicy.libraryDensity(duoOuter, height).compact, "outer height=$height")
            assertTrue(!DuoLayoutPolicy.libraryDensity(duoInner, height).compact, "inner height=$height")
        }

        // An ordinary device keeps today's chrome even in a short window. This is the
        // byte-identical guarantee: the compact density is a Duo capability, not a
        // general short-screen rule.
        assertTrue(!DuoLayoutPolicy.libraryDensity(SharedDeviceLayout.None, 466.dp).compact)
    }

    @Test
    fun `compact chrome gives the grid back most of the display`() {
        val standard = SharedAndroidHomeDensity.Standard
        val compact = SharedAndroidHomeDensity.Compact

        // Spaces only ever shrink.
        assertTrue(compact.searchRowHeight < standard.searchRowHeight)
        assertTrue(compact.topBarTopPadding < standard.topBarTopPadding)
        assertTrue(compact.continueCardTopGap < standard.continueCardTopGap)
        assertTrue(compact.continueCardHeight < standard.continueCardHeight)
        assertTrue(compact.filterRowTopGap < standard.filterRowTopGap)
        assertTrue(compact.gridCardTopGap < standard.gridCardTopGap)
        assertTrue(compact.gridContentTopPadding < standard.gridContentTopPadding)
        assertTrue(compact.bookCellMin < standard.bookCellMin)

        // The search field cannot go below its own 56dp minimum without clipping its text.
        assertTrue(compact.searchRowHeight >= 56.dp)

        // The continue-reading card was the biggest consumer and still gives back the most.
        val recovered = (standard.continueCardHeight + standard.continueCardTopGap) -
            (compact.continueCardHeight + compact.continueCardTopGap)
        assertTrue(recovered >= 100.dp, "recovered=$recovered")

        // The cover must still fit the card's 8dp inner padding, or it would clip.
        listOf(standard, compact).forEach { density ->
            assertTrue(
                density.continueCoverHeight <= density.continueCardHeight - 16.dp,
                "cover overflows at compact=${density.compact}",
            )
        }
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

        // A column on the leading edge is not over the sidebar, which is always mirrored to
        // the trailing edge: the cover display held landscape, and the left half of Split
        // View. Reserving a column there would open a gap that clears nothing.
        val columnOnLeading = SharedDeviceLayout(
            controlColumnWidthFraction = 0.18f,
            controlColumnTopFraction = 0.23f,
            controlColumnOnTrailing = false,
            isDuoDevice = true,
        )
        assertEquals(0.dp, DuoLayoutPolicy.accountSidebarEndInset(columnOnLeading, 678.dp))

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
