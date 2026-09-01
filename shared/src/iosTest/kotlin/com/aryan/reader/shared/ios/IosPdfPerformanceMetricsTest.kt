package com.aryan.reader.shared.ios

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IosPdfPerformanceMetricsTest {
    @AfterTest
    fun resetMetrics() {
        IosPdfPerformanceMetrics.reset()
    }

    @Test
    fun keeps_frame_and_render_slow_counts_separate() {
        IosPdfPerformanceMetrics.recordFrame(40)
        IosPdfPerformanceMetrics.recordFrame(16)
        IosPdfPerformanceMetrics.recordRender(
            widthPx = 1024,
            heightPx = 2048,
            estimatedBytes = 8_388_608,
            durationMillis = 48,
            tile = true,
        )

        val snapshot = IosPdfPerformanceMetrics.snapshot()
        assertEquals(2, snapshot.frameCount)
        assertEquals(1, snapshot.slowFrameCount)
        assertEquals(1, snapshot.renderCount)
        assertEquals(1, snapshot.tileRenderCount)
        assertEquals(1, snapshot.slowRenderCount)
        assertEquals(1024, snapshot.lastRenderWidthPx)
        assertEquals(2048, snapshot.lastRenderHeightPx)
        assertEquals(8_388_608, snapshot.peakRenderBytes)
    }
}
