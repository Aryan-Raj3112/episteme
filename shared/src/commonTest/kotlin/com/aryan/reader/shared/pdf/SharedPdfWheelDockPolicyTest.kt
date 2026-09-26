package com.aryan.reader.shared.pdf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfWheelDockPolicyTest {
    @Test
    fun `base angles center the arc on the inward normal`() {
        // 5 tools at 25 degrees: -50, -25, 0, 25, 50.
        assertEquals(-50f, sharedPdfWheelBaseAngleDeg(0, 5))
        assertEquals(0f, sharedPdfWheelBaseAngleDeg(2, 5))
        assertEquals(50f, sharedPdfWheelBaseAngleDeg(4, 5))
    }

    @Test
    fun `small arcs do not scroll, large arcs scroll end to end`() {
        // 7 tools span 150 degrees: fits, no scroll.
        assertEquals(0f, sharedPdfWheelRotationRangeDeg(7))
        // 10 tools span 225 degrees: 22.5 degrees each way.
        assertEquals(22.5f, sharedPdfWheelRotationRangeDeg(10))
        assertEquals(22.5f, sharedPdfWheelClampRotationDeg(100f, 10))
        assertEquals(-22.5f, sharedPdfWheelClampRotationDeg(-100f, 10))
        assertEquals(10f, sharedPdfWheelClampRotationDeg(10f, 10))
    }

    @Test
    fun `vertical drag spins the wheel and clamps at the ends`() {
        // Dragging down (positive dy) increases angles (tools move down).
        val orbit = 64f
        val spun = sharedPdfWheelRotationForDragDy(0f, orbit, orbit, 10)
        assertTrue(spun > 0f)
        // A huge drag clamps instead of overshooting.
        assertEquals(
            22.5f,
            sharedPdfWheelRotationForDragDy(0f, 10_000f, orbit, 10),
        )
        // Zero orbit never divides by zero.
        assertEquals(5f, sharedPdfWheelRotationForDragDy(5f, 100f, 0f, 10))
    }

    @Test
    fun `tool centers mirror across edges`() {
        val left = sharedPdfWheelToolCenterPx(0f, 64f, 96f, 192f, isLeft = true)
        val right = sharedPdfWheelToolCenterPx(0f, 64f, 96f, 192f, isLeft = false)
        // 0 degrees points inward: near the arc for LEFT, mirrored for RIGHT.
        assertEquals(64f, left.x)
        assertEquals(96f - 64f, right.x)
        assertEquals(left.y, right.y)
        assertEquals(96f, left.y)
        // +90 degrees sits at the bottom rim for both edges.
        val bottomLeft = sharedPdfWheelToolCenterPx(90f, 64f, 96f, 192f, isLeft = true)
        assertTrue(abs(bottomLeft.y - (96f + 64f)) < 0.01f)
    }

    @Test
    fun `visibility window covers the half disc`() {
        assertTrue(sharedPdfWheelIsAngleVisible(0f))
        assertTrue(sharedPdfWheelIsAngleVisible(90f))
        assertTrue(sharedPdfWheelIsAngleVisible(-95f))
        assertFalse(sharedPdfWheelIsAngleVisible(120f))
        assertFalse(sharedPdfWheelIsAngleVisible(-120f))
    }
}
