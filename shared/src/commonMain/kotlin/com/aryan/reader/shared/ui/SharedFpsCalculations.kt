package com.aryan.reader.shared.ui

/**
 * Pure trailing-window FPS math shared by the debug meters (Android
 * `DebugFpsCalculations`, iOS overlay). Counts vsync timestamps inside the
 * trailing 1-second window ending at [nowNanos]; a main-thread stall shows
 * up as a gap, so the count drops.
 */
object SharedFpsCalculations {
    const val WINDOW_NANOS = 1_000_000_000L

    fun fpsInWindow(frameTimes: List<Long>, nowNanos: Long): Int {
        if (frameTimes.isEmpty()) return 0
        val cutoff = nowNanos - WINDOW_NANOS
        var count = 0
        for (i in frameTimes.size - 1 downTo 0) {
            if (frameTimes[i] > cutoff) {
                count++
            } else {
                break
            }
        }
        return count
    }
}
