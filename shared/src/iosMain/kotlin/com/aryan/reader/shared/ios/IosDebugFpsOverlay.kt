package com.aryan.reader.shared.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.ui.SharedFpsCalculations
import platform.Foundation.NSUserDefaults

/**
 * iOS frame-clock FPS. Android parity: same trailing-1s window math
 * ([SharedFpsCalculations]) and 500ms UI cadence, so numbers are comparable
 * across platforms.
 */

/** NSUserDefaults-backed toggle so the choice survives relaunches. */
object IosDebugFpsStore {
    private const val KEY_FPS_OVERLAY = "fps_overlay_enabled"

    fun isEnabled(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(KEY_FPS_OVERLAY) != null) {
            defaults.boolForKey(KEY_FPS_OVERLAY)
        } else {
            true
        }
    }

    fun setEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = KEY_FPS_OVERLAY)
    }
}

/**
 * Accurate debug FPS meter driven by the Compose frame clock (the portable
 * equivalent of Android's `Choreographer` loop). Frame timestamps are
 * accumulated in locals — never in state — and the displayed value updates
 * at most every 500ms, so the meter itself never causes jank.
 *
 * Debug only — call sites must gate on `bridge.isDebugBuild`.
 */
@Composable
fun IosDebugFpsOverlay(
    modifier: Modifier = Modifier,
) {
    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val frameTimes = ArrayDeque<Long>(256)
        var lastUiUpdateNanos = 0L
        var lastReported = -1
        while (true) {
            withFrameNanos { frameTimeNanos ->
                frameTimes.addLast(frameTimeNanos)
                val cutoff = frameTimeNanos - SharedFpsCalculations.WINDOW_NANOS
                while (frameTimes.size > 1 && frameTimes.first() <= cutoff) {
                    frameTimes.removeFirst()
                }
                while (frameTimes.size > 360) {
                    frameTimes.removeFirst()
                }
                if (frameTimeNanos - lastUiUpdateNanos >= 500_000_000L) {
                    lastUiUpdateNanos = frameTimeNanos
                    val computed = SharedFpsCalculations.fpsInWindow(frameTimes, frameTimeNanos)
                    if (computed != lastReported) {
                        lastReported = computed
                        fps = computed
                    }
                }
            }
        }
    }

    Text(
        text = "FPS: $fps",
        color = Color.Green,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = modifier
            .testTag("DebugFpsOverlay")
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/**
 * Global overlay: beneath the status bar, top-left, above every destination.
 * Non-clickable so touches pass through to the app.
 */
@Composable
fun IosDebugFpsGlobalOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 8.dp, top = 4.dp)
            .zIndex(10f),
        contentAlignment = Alignment.TopStart
    ) {
        IosDebugFpsOverlay()
    }
}
