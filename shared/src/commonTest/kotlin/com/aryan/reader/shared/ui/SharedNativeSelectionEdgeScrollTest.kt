package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedNativeSelectionEdgeScrollTest {

    private val density = Density(1f, 1f)

    private fun deltaAt(y: Float, rootHeight: Float = 400f): Float {
        return sharedNativeSelectionEdgeScrollDelta(y, rootHeight, density)
    }

    @Test
    fun `edge scroll delta is zero outside the edge bands`() {
        assertEquals(0f, deltaAt(100f))
        assertEquals(0f, deltaAt(200f))
        assertEquals(0f, deltaAt(330f))
    }

    @Test
    fun `top edge band scrolls up with linear ramp`() {
        assertEquals(-28f, deltaAt(0f))
        assertEquals(-14f, deltaAt(32f))
        assertEquals(-2f, deltaAt(63.9f))
        assertEquals(0f, deltaAt(64f))
    }

    @Test
    fun `bottom edge band scrolls down with linear ramp`() {
        assertEquals(0f, deltaAt(336f))
        assertEquals(2f, deltaAt(336.1f))
        assertEquals(14f, deltaAt(368f))
        assertEquals(28f, deltaAt(400f))
    }

    @Test
    fun `zero height reader produces no scroll`() {
        assertEquals(0f, sharedNativeSelectionEdgeScrollDelta(0f, 0f, density))
        assertEquals(0f, sharedNativeSelectionEdgeScrollDelta(0f, -1f, density))
    }

    @Test
    fun `edge band math scales with density`() {
        val highDensity = Density(2f, 2f)
        assertEquals(-56f, sharedNativeSelectionEdgeScrollDelta(0f, 800f, highDensity))
        assertEquals(-28f, sharedNativeSelectionEdgeScrollDelta(64f, 800f, highDensity))
        assertEquals(28f, sharedNativeSelectionEdgeScrollDelta(736f, 800f, highDensity))
        assertEquals(56f, sharedNativeSelectionEdgeScrollDelta(800f, 800f, highDensity))
    }

    @Test
    fun `edge band detection matches the drag loop threshold`() {
        assertTrue(sharedNativeSelectionIsInEdgeBand(-2f))
        assertTrue(sharedNativeSelectionIsInEdgeBand(28f))
        assertFalse(sharedNativeSelectionIsInEdgeBand(0f))
        assertFalse(sharedNativeSelectionIsInEdgeBand(0.5f))
    }

    /**
     * The two bands are mutually exclusive, as on Android (`NativeVerticalReaderScreen.kt:1401`
     * uses a `when`). They used to be computed independently and summed here, so any viewport
     * shorter than 128dp put the pointer inside both bands and scrolled at roughly twice the
     * Android rate.
     */
    @Test
    fun `overlapping edge bands do not sum and the top band wins`() {
        // 100dp viewport: the bands are 0..64 and 36..100, so 50 is inside both.
        // Android's `when` takes the top branch: -(((64 - 50) / 64) * 28) = -6.125. Summing gave
        // -6.125 + ((50 - 36) / 64 * 28) = -6.125 + 6.125 = 0, i.e. no scroll at all.
        assertEquals(-6.125f, deltaAt(50f, rootHeight = 100f))
        // Past the top band and inside the bottom band: (70 - 36) / 64 * 28 = 14.875.
        assertEquals(14.875f, deltaAt(70f, rootHeight = 100f))
        // A viewport tall enough for the bands not to overlap still has no scroll between them.
        assertEquals(0f, deltaAt(100f, rootHeight = 200f))
        assertEquals(2f, deltaAt(140f, rootHeight = 200f))
    }

    @Test
    fun `a viewport shorter than one band is entirely the top band`() {
        // 40dp viewport: the bottom band starts at -24, so every point is inside it. Summing the
        // two independent ramps made the sum positive (scrolling down) for most of the viewport,
        // so dragging the top handle scrolled the wrong way. Android's `when` takes the top
        // branch for any y < 64dp: -((64 - 39) / 64 * 28) = -10.9375.
        assertEquals(-28f, deltaAt(0f, rootHeight = 40f))
        assertEquals(-10.9375f, deltaAt(39f, rootHeight = 40f))
    }
}