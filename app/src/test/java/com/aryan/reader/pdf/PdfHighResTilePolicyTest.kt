package com.aryan.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHighResTilePolicyTest {

    @Test
    fun `drawn tiles stay visible while panning`() {
        // Draw must not depend on the motion pause: cached sharp tiles keep
        // showing while panning instead of dropping to low-res.
        assertTrue(shouldDrawPdfHighResTiles(needsTiling = true))
        assertFalse(shouldDrawPdfHighResTiles(needsTiling = false))
    }

    @Test
    fun `tile set freezes during motion`() {
        val plan = planPdfHighResTileUpdate(
            requiredTileIds = setOf(1, 2, 3),
            validCurrentTileIds = setOf(1),
            currentTileIds = setOf(1, 9),
            motionPaused = true,
        )
        assertEquals(emptySet<Int>(), plan.toRender)
        assertEquals(emptySet<Int>(), plan.toRecycle)
    }

    @Test
    fun `idle renders missing tiles and recycles departed ones`() {
        val plan = planPdfHighResTileUpdate(
            requiredTileIds = setOf(1, 2, 3),
            validCurrentTileIds = setOf(1),
            currentTileIds = setOf(1, 9),
            motionPaused = false,
        )
        assertEquals(setOf(2, 3), plan.toRender)
        assertEquals(setOf(9), plan.toRecycle)
    }
}
