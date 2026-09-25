package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedFpsCalculationsTest {

    @Test
    fun `empty frames report zero fps`() {
        assertEquals(0, SharedFpsCalculations.fpsInWindow(emptyList(), 1_000_000_000L))
    }

    @Test
    fun `sixty frames in trailing second report sixty fps`() {
        val interval = 1_000_000_000L / 60
        val now = 10_000_000_000L
        val frames = (0 until 60).map { now - 59 * interval + it * interval }
        assertEquals(60, SharedFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `one-twenty-hertz frames report one-twenty fps`() {
        val interval = 1_000_000_000L / 120
        val now = 10_000_000_000L
        val frames = (0 until 120).map { now - 119 * interval + it * interval }
        assertEquals(120, SharedFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `main thread stall drops fps`() {
        val now = 10_000_000_000L
        val frames = listOf(
            now - 900_000_000L,
            now - 700_000_000L,
            now - 500_000_000L,
            now - 300_000_000L,
            now - 100_000_000L
        )
        assertEquals(5, SharedFpsCalculations.fpsInWindow(frames, now))
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
        assertEquals(2, SharedFpsCalculations.fpsInWindow(frames, now))
    }

    @Test
    fun `fps overlay item is debug-gated and checked`() {
        val items = sharedMobileHomeOverflowItems(
            state = SharedMobileHomeOverflowState(
                tabsEnabled = false,
                screenCaptureProtectionEnabled = false,
                strictFileFilterEnabled = false,
                usePdfFileNameAsDisplayName = false,
                hideReaderAi = false,
                fpsOverlayEnabled = true,
            ),
            capabilities = SharedMobileHomeOverflowCapabilities(fpsOverlay = true),
        )
        val fps = items.first { it.action == SharedMobileHomeOverflowAction.SHOW_FPS_OVERLAY }
        assertEquals(SharedMobileHomeOverflowSection.DEBUG, fps.section)
        assertEquals(true, fps.checked)
    }
}
