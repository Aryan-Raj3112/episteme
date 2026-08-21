package com.aryan.reader.shared.ios

import platform.Foundation.NSLock

/**
 * Bounded, allocation-free counters for the iOS PDF reader. The values are intentionally
 * aggregate rather than per-event so a long reader session cannot grow diagnostic memory.
 */
internal object IosPdfPerformanceMetrics {
    private const val SlowFrameThresholdMillis = 32L
    private val lock = NSLock()

    private var interactionCountValue = 0L
    private var cameraUpdateCountValue = 0L
    private var flingCountValue = 0L
    private var frameCountValue = 0L
    private var slowFrameCountValue = 0L
    private var lastFrameDurationMillisValue = 0L
    private var maxFrameDurationMillisValue = 0L
    private var renderCountValue = 0L
    private var tileRenderCountValue = 0L
    private var slowRenderCountValue = 0L
    private var lastRenderDurationMillisValue = 0L
    private var maxRenderDurationMillisValue = 0L
    private var lastRenderWidthPxValue = 0
    private var lastRenderHeightPxValue = 0
    private var lastRenderBytesValue = 0L
    private var peakRenderBytesValue = 0L

    fun recordInteraction() = lock.withLock {
        interactionCountValue += 1
    }

    fun recordCameraUpdate() = lock.withLock {
        cameraUpdateCountValue += 1
    }

    fun recordFling() = lock.withLock {
        flingCountValue += 1
    }

    fun recordFrame(durationMillis: Long) = lock.withLock {
        val duration = durationMillis.coerceAtLeast(0L)
        frameCountValue += 1
        lastFrameDurationMillisValue = duration
        maxFrameDurationMillisValue = maxOf(maxFrameDurationMillisValue, duration)
        if (duration >= SlowFrameThresholdMillis) slowFrameCountValue += 1
    }

    fun recordRender(
        widthPx: Int,
        heightPx: Int,
        estimatedBytes: Long,
        durationMillis: Long,
        tile: Boolean,
    ) = lock.withLock {
        val duration = durationMillis.coerceAtLeast(0L)
        val bytes = estimatedBytes.coerceAtLeast(0L)
        renderCountValue += 1
        if (tile) tileRenderCountValue += 1
        lastRenderDurationMillisValue = duration
        maxRenderDurationMillisValue = maxOf(maxRenderDurationMillisValue, duration)
        lastRenderWidthPxValue = widthPx.coerceAtLeast(0)
        lastRenderHeightPxValue = heightPx.coerceAtLeast(0)
        lastRenderBytesValue = bytes
        peakRenderBytesValue = maxOf(peakRenderBytesValue, bytes)
        if (duration >= SlowFrameThresholdMillis) slowRenderCountValue += 1
    }

    fun snapshot(): IosPdfPerformanceSnapshot = lock.withLock {
        IosPdfPerformanceSnapshot(
            interactionCount = interactionCountValue,
            cameraUpdateCount = cameraUpdateCountValue,
            flingCount = flingCountValue,
            frameCount = frameCountValue,
            slowFrameCount = slowFrameCountValue,
            lastFrameDurationMillis = lastFrameDurationMillisValue,
            maxFrameDurationMillis = maxFrameDurationMillisValue,
            renderCount = renderCountValue,
            tileRenderCount = tileRenderCountValue,
            slowRenderCount = slowRenderCountValue,
            lastRenderDurationMillis = lastRenderDurationMillisValue,
            maxRenderDurationMillis = maxRenderDurationMillisValue,
            lastRenderWidthPx = lastRenderWidthPxValue,
            lastRenderHeightPx = lastRenderHeightPxValue,
            lastRenderBytes = lastRenderBytesValue,
            peakRenderBytes = peakRenderBytesValue,
        )
    }

    fun reset() = lock.withLock {
        interactionCountValue = 0L
        cameraUpdateCountValue = 0L
        flingCountValue = 0L
        frameCountValue = 0L
        slowFrameCountValue = 0L
        lastFrameDurationMillisValue = 0L
        maxFrameDurationMillisValue = 0L
        renderCountValue = 0L
        tileRenderCountValue = 0L
        slowRenderCountValue = 0L
        lastRenderDurationMillisValue = 0L
        maxRenderDurationMillisValue = 0L
        lastRenderWidthPxValue = 0
        lastRenderHeightPxValue = 0
        lastRenderBytesValue = 0L
        peakRenderBytesValue = 0L
    }

    private inline fun <T> NSLock.withLock(block: () -> T): T {
        lock()
        return try {
            block()
        } finally {
            unlock()
        }
    }
}

internal data class IosPdfPerformanceSnapshot(
    val interactionCount: Long,
    val cameraUpdateCount: Long,
    val flingCount: Long,
    val frameCount: Long,
    val slowFrameCount: Long,
    val lastFrameDurationMillis: Long,
    val maxFrameDurationMillis: Long,
    val renderCount: Long,
    val tileRenderCount: Long,
    val slowRenderCount: Long,
    val lastRenderDurationMillis: Long,
    val maxRenderDurationMillis: Long,
    val lastRenderWidthPx: Int,
    val lastRenderHeightPx: Int,
    val lastRenderBytes: Long,
    val peakRenderBytes: Long,
)
