package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedDropdownMenuPlacementTest {
    private val density = Density(density = 2f, fontScale = 1f)

    private fun rect(left: Int, top: Int, right: Int, bottom: Int) =
        IntRect(left, top, right, bottom)

    @Test
    fun placesMenuBelowAnchorWhenThereIsRoom() {
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 200, 140, 240),
            menuSize = IntSize(200, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(IntOffset(40, 240), placement.offset)
    }

    @Test
    fun alignsMenuRightToAnchorRightWhenMenuIsWiderThanAnchor() {
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 200, 140, 240),
            menuSize = IntSize(300, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )
        // leftToAnchorLeft = 40 would overflow 1000? No: 40 + 300 <= 1000, so it wins.
        assertEquals(IntOffset(40, 240), placement.offset)
    }

    @Test
    fun flipsAboveAnchorWhenNotEnoughRoomBelow() {
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 1600, 140, 1640),
            menuSize = IntSize(200, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(1600 - 300, placement.offset.y)
    }

    @Test
    fun keepsMenuInsideWindowWhenAnchorIsNearRightEdge() {
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(900, 200, 980, 240),
            menuSize = IntSize(300, 200),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertTrue(placement.offset.x >= 0, "x=${placement.offset.x}")
        assertTrue(
            placement.offset.x + 300 <= 1000,
            "menu overflows right edge: x=${placement.offset.x}",
        )
    }

    @Test
    fun mirrorsToAnchorRightEdgeInRtl() {
        // Android benchmark: RTL tries the anchor's right edge first, which lands off-screen
        // (x = 140 - 200 = -60), so it falls back to the anchor's left edge.
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 200, 140, 240),
            menuSize = IntSize(200, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Rtl,
        )
        assertEquals(40, placement.offset.x)

        // With room to the right of the anchor's right edge, RTL keeps the right edges flush.
        val wide = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 200, 140, 240),
            menuSize = IntSize(60, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(0.dp, 0.dp),
            density = density,
            layoutDirection = LayoutDirection.Rtl,
        )
        assertEquals(140 - 60, wide.offset.x)
    }

    @Test
    fun appliesContentOffset() {
        val placement = sharedDropdownMenuPlacement(
            anchorBounds = rect(40, 200, 140, 240),
            menuSize = IntSize(200, 300),
            windowSize = IntSize(1000, 2000),
            contentOffset = DpOffset(8.dp, 4.dp),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(IntOffset(40 + 16, 240 + 8), placement.offset)
    }

    @Test
    fun scalesOutFromTheAnchorSide() {
        // Anchor fully to the left of the menu: origin should sit on the menu's left edge.
        val origin = sharedDropdownMenuTransformOrigin(
            anchorBounds = rect(10, 100, 60, 140),
            menuBounds = rect(200, 140, 500, 440),
        )
        assertEquals(TransformOrigin(0f, 0f), origin)
    }

    @Test
    fun scalesOutFromAnchorRightEdgeWhenAnchorOverlapsMenu() {
        // Android benchmark: when the anchor's right edge is left of the menu's right edge
        // the origin tracks the anchor's right edge, not its centre.
        val origin = sharedDropdownMenuTransformOrigin(
            anchorBounds = rect(300, 100, 400, 140),
            menuBounds = rect(200, 140, 500, 440),
        )
        // TransformOrigin packs pivotX/pivotY into a Long, so compare packed values.
        val expected = TransformOrigin((400 - 200).toFloat() / 300f, 0f)
        assertEquals(expected, origin)
    }

    @Test
    fun maxHeightNeverExceedsTheWindow() {
        val height = sharedDropdownMenuMaxHeight(
            anchorBounds = rect(0, 0, 100, 100),
            windowSize = IntSize(400, 800),
            density = density,
        )
        assertTrue(height in 1..800, "height=$height")
    }

    @Test
    fun maxHeightUsesTheRoomierSide() {
        // Anchor pinned near the top: below has ~700px, above has 0.
        val anchorAtTop = sharedDropdownMenuMaxHeight(
            anchorBounds = rect(0, 0, 100, 100),
            windowSize = IntSize(400, 800),
            density = density,
        )
        // Anchor pinned near the bottom: above has ~700px, below has 0.
        val anchorAtBottom = sharedDropdownMenuMaxHeight(
            anchorBounds = rect(0, 700, 100, 800),
            windowSize = IntSize(400, 800),
            density = density,
        )
        assertEquals(anchorAtTop, anchorAtBottom)
    }

    @Test
    fun maxHeightStaysPositiveForAMicroscopicWindow() {
        val height = sharedDropdownMenuMaxHeight(
            anchorBounds = rect(0, 0, 10, 10),
            windowSize = IntSize(20, 20),
            density = density,
        )
        assertTrue(height > 0, "height=$height")
    }
}
