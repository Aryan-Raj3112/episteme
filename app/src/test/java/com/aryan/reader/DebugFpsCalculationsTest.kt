package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugFpsCalculationsTest {

    @Test
    fun `empty frames report zero fps`() {
        assertEquals(0, DebugFpsCalculations.fpsInWindow(emptyList(), 1_000_000_000L))
    }

    @Test
    fun `sixty frames in trailing second report sixty fps`() {
        val interval = 1_000_000_000L / 60
        val now = 10_000_000_000L
        val frames = (0 until 60).map { now - 59 * interval + it * interval }
        assertEquals(60, DebugFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `one-twenty-hz frames in trailing second report one-twenty fps`() {
        val interval = 1_000_000_000L / 120
        val now = 10_000_000_000L
        val frames = (0 until 120).map { now - 119 * interval + it * interval }
        assertEquals(120, DebugFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `main thread stall drops fps`() {
        val now = 10_000_000_000L
        // Only 5 frames in the last second after a ~2s freeze.
        val frames = listOf(
            now - 900_000_000L,
            now - 700_000_000L,
            now - 500_000_000L,
            now - 300_000_000L,
            now - 100_000_000L
        )
        assertEquals(5, DebugFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `stale frames outside window are ignored`() {
        val now = 10_000_000_000L
        val frames = listOf(
            now - 2_000_000_000L,
            now - 1_500_000_000L,
            now - 500_000_000L,
            now - 100_000_000L
        )
        assertEquals(2, DebugFpsCalculations.fpsInWindow(frames, now))
    }
}
