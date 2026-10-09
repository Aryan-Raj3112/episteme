package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedDropdownMenuPopupSpaceTest {
    private fun rect(left: Int, top: Int, right: Int, bottom: Int) =
        IntRect(left, top, right, bottom)

    @Test
    fun shiftsAnchorByThePopupWindowInset() {
        // A 1080x2400 device with a 74px status bar and 63px navigation bar: the popup window is
        // positioned at the visible display frame, so its content origin is (0, 74).
        val space = sharedDropdownMenuPopupSpace(
            anchorBounds = rect(943, 85, 1069, 211),
            containerSize = IntSize(1080, 2400),
            insets = SharedDropdownMenuInsets(left = 0, top = 74, right = 0, bottom = 63),
        )

        assertEquals(rect(943, 11, 1069, 137), space.anchor)
        assertEquals(IntSize(1080, 2263), space.drawableSize)
    }

    @Test
    fun keepsAnchorAndDrawableAreaUnchangedWithoutInsets() {
        val space = sharedDropdownMenuPopupSpace(
            anchorBounds = rect(40, 20, 140, 68),
            containerSize = IntSize(1080, 2400),
            insets = SharedDropdownMenuInsets(left = 0, top = 0, right = 0, bottom = 0),
        )

        assertEquals(rect(40, 20, 140, 68), space.anchor)
        assertEquals(IntSize(1080, 2400), space.drawableSize)
    }

    @Test
    fun subtractsHorizontalInsetsForCutoutAndNavigationBars() {
        val space = sharedDropdownMenuPopupSpace(
            anchorBounds = rect(40, 20, 140, 68),
            containerSize = IntSize(1000, 2000),
            insets = SharedDropdownMenuInsets(left = 30, top = 0, right = 10, bottom = 0),
        )

        assertEquals(rect(10, 20, 110, 68), space.anchor)
        assertEquals(IntSize(960, 2000), space.drawableSize)
    }

    @Test
    fun neverProducesANegativeDrawableArea() {
        // Guards a host window smaller than the insets it reports (possible transiently while the
        // IME animates): placement math would otherwise invert and put the menu off-screen.
        val space = sharedDropdownMenuPopupSpace(
            anchorBounds = rect(0, 0, 10, 10),
            containerSize = IntSize(100, 100),
            insets = SharedDropdownMenuInsets(left = 60, top = 60, right = 60, bottom = 60),
        )

        assertEquals(IntSize(0, 0), space.drawableSize)
    }
}