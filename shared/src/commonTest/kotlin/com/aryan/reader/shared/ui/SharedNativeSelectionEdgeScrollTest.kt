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
}