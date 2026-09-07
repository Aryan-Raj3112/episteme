package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSelectionMenuPlacementTest {
    @Test
    fun `places menu above selection when there is room`() {
        val result = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(width = 800, height = 600),
            popup = SharedSelectionMenuSize(width = 240, height = 120),
            selection = SharedSelectionMenuRect(left = 300f, top = 300f, right = 360f, bottom = 330f),
            marginPx = 16f,
            gapPx = 12f
        )

        assertEquals(SharedSelectionMenuPlacement.ABOVE, result.placement)
        assertEquals(210, result.x)
        assertEquals(168, result.y)
    }

    @Test
    fun `places menu below selection when above is blocked`() {
        val result = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(width = 800, height = 600),
            popup = SharedSelectionMenuSize(width = 240, height = 120),
            selection = SharedSelectionMenuRect(left = 300f, top = 40f, right = 360f, bottom = 70f),
            marginPx = 16f,
            gapPx = 12f
        )

        assertEquals(SharedSelectionMenuPlacement.BELOW, result.placement)
        assertEquals(210, result.x)
        assertEquals(82, result.y)
    }

    @Test
    fun `places menu on wider side in short landscape viewport`() {
        val result = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(width = 800, height = 320),
            popup = SharedSelectionMenuSize(width = 240, height = 180),
            selection = SharedSelectionMenuRect(left = 300f, top = 120f, right = 380f, bottom = 190f),
            marginPx = 16f,
            gapPx = 12f
        )

        assertEquals(SharedSelectionMenuPlacement.RIGHT, result.placement)
        assertEquals(392, result.x)
        assertEquals(65, result.y)
    }

    @Test
    fun `keeps menu off selected text when a valid side placement exists`() {
        val result = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(width = 800, height = 320),
            popup = SharedSelectionMenuSize(width = 240, height = 180),
            selection = SharedSelectionMenuRect(left = 300f, top = 120f, right = 380f, bottom = 190f),
            marginPx = 16f,
            gapPx = 12f
        )

        assertEquals(0f, result.rect(width = 240, height = 180).overlapAreaWith(
            SharedSelectionMenuRect(left = 300f, top = 120f, right = 380f, bottom = 190f)
        ))
    }

    @Test
    fun `falls back predictably when selection consumes the viewport`() {
        val result = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(width = 320, height = 220),
            popup = SharedSelectionMenuSize(width = 260, height = 180),
            selection = SharedSelectionMenuRect(left = 10f, top = 20f, right = 310f, bottom = 200f),
            marginPx = 16f,
            gapPx = 12f
        )

        assertEquals(SharedSelectionMenuPlacement.FALLBACK, result.placement)
        assertEquals(30, result.x)
        assertEquals(16, result.y)
    }

    @Test
    fun `maps canvas anchor to window without scale`() {
        val result = sharedPdfSelectionWindowRect(
            anchor = SharedSelectionMenuRect(left = 10f, top = 20f, right = 50f, bottom = 40f),
            canvasWidth = 100,
            canvasHeight = 200,
            overlayLeft = 0,
            overlayTop = 0,
            overlayWidth = 100,
            overlayHeight = 200,
        )

        assertEquals(SharedSelectionMenuRect(left = 10f, top = 20f, right = 50f, bottom = 40f), result)
    }

    @Test
    fun `maps canvas anchor to window when zoomed and panned`() {
        // Canvas 100x200 at 2x zoom, overlay origin panned to (30, 40).
        val result = sharedPdfSelectionWindowRect(
            anchor = SharedSelectionMenuRect(left = 10f, top = 20f, right = 50f, bottom = 40f),
            canvasWidth = 100,
            canvasHeight = 200,
            overlayLeft = 30,
            overlayTop = 40,
            overlayWidth = 200,
            overlayHeight = 400,
        )

        assertEquals(SharedSelectionMenuRect(left = 50f, top = 80f, right = 130f, bottom = 120f), result)
    }
}

private fun SharedSelectionMenuPlacementResult.rect(
    width: Int,
    height: Int
): SharedSelectionMenuRect {
    return SharedSelectionMenuRect(
        left = x.toFloat(),
        top = y.toFloat(),
        right = x + width.toFloat(),
        bottom = y + height.toFloat()
    )
}

private fun SharedSelectionMenuRect.overlapAreaWith(other: SharedSelectionMenuRect): Float {
    val overlapWidth = minOf(right, other.right) - maxOf(left, other.left)
    val overlapHeight = minOf(bottom, other.bottom) - maxOf(top, other.top)
    return overlapWidth.coerceAtLeast(0f) * overlapHeight.coerceAtLeast(0f)
}
