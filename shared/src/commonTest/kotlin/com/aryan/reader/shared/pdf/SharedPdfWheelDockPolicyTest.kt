package com.aryan.reader.shared.pdf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfWheelDockPolicyTest {
    @Test
    fun `base angles center the arc on the inward normal`() {
        // 5 tools at 40 degrees: -80, -40, 0, 40, 80.
        assertEquals(-80f, sharedPdfWheelBaseAngleDeg(0, 5))
        assertEquals(0f, sharedPdfWheelBaseAngleDeg(2, 5))
        assertEquals(80f, sharedPdfWheelBaseAngleDeg(4, 5))
    }

    @Test
    fun `small arcs do not scroll and large arcs scroll end to end`() {
        // 4 tools span 120 degrees: fits, no scroll.
        assertEquals(0f, sharedPdfWheelRotationRangeDeg(4))
        // 9 tools span 320 degrees: 86 degrees each way (ends stop 16
        // degrees before the rim so icons are never cut off).
        assertEquals(86f, sharedPdfWheelRotationRangeDeg(9))
        assertEquals(86f, sharedPdfWheelClampRotationDeg(200f, 9))
        assertEquals(-86f, sharedPdfWheelClampRotationDeg(-200f, 9))
        assertEquals(10f, sharedPdfWheelClampRotationDeg(10f, 9))
    }

    @Test
    fun `full circles distribute evenly and spin a half turn`() {
        // 10 tools at 40 degrees would wrap exactly onto each other (close
        // over redo), so they spread 36 degrees apart instead.
        assertEquals(36f, sharedPdfWheelEffectiveStepDeg(10))
        assertTrue(sharedPdfWheelIsFullCircle(10))
        // Partial arcs keep the requested step.
        assertEquals(40f, sharedPdfWheelEffectiveStepDeg(9))
        assertFalse(sharedPdfWheelIsFullCircle(9))
        assertEquals(30f, sharedPdfWheelEffectiveStepDeg(12))
        // First and last tools no longer coincide.
        val first = sharedPdfWheelBaseAngleDeg(0, 10)
        val last = sharedPdfWheelBaseAngleDeg(9, 10)
        assertEquals(-162f, first)
        assertEquals(162f, last)
        // No ends: a half turn brings every tool through the window.
        assertEquals(180f, sharedPdfWheelRotationRangeDeg(10))
        assertEquals(180f, sharedPdfWheelClampRotationDeg(200f, 10))
        assertEquals(-180f, sharedPdfWheelClampRotationDeg(-200f, 10))
        assertEquals(10f, sharedPdfWheelClampRotationDeg(10f, 10))
    }

    @Test
    fun `angles wrap so full-circle tools stay placeable`() {
        assertEquals(-74f, sharedPdfWheelNormalizeDeg(286f))
        assertEquals(170f, sharedPdfWheelNormalizeDeg(-190f))
        assertEquals(0f, sharedPdfWheelNormalizeDeg(360f))
    }

    @Test
    fun `vertical drag spins the wheel and clamps at the ends`() {
        // Dragging down (positive dy) increases angles (tools move down).
        val orbit = 64f
        val spun = sharedPdfWheelRotationForDragDy(0f, orbit, orbit, 9)
        assertTrue(spun > 0f)
        // A huge drag clamps instead of overshooting (86 for the 9-tool
        // partial arc, 180 for the 10-tool full circle).
        assertEquals(
            86f,
            sharedPdfWheelRotationForDragDy(0f, 10_000f, orbit, 9),
        )
        assertEquals(
            180f,
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
