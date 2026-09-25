// DebugFpsOverlay.kt
package com.aryan.reader

import android.content.Context
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure FPS math so it stays accurate and unit-testable without Android.
 *
 * Counts frames whose vsync timestamp falls inside the trailing 1-second
 * window ending at [nowNanos]. A main-thread stall shows up as a gap in
 * timestamps, so the count drops — exactly what a slow-touch / low-fps
 * report looks like.
 */
object DebugFpsCalculations {
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

object DebugFpsPrefs {
    const val PREFS_NAME = "reader_debug_prefs"
    const val KEY_FPS_OVERLAY = "fps_overlay_enabled"

    fun isEnabled(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FPS_OVERLAY, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FPS_OVERLAY, enabled)
            .apply()
    }
}

/**
 * Process-wide toggle so Home > More can flip the overlay that lives at the
 * MainActivity level (visible on every route). Backed by SharedPreferences
 * so the choice survives process death.
 */
object DebugFpsStore {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        _enabled.value = DebugFpsPrefs.isEnabled(context.applicationContext)
        initialized = true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val next = if (BuildConfig.DEBUG) enabled else false
        _enabled.value = next
        DebugFpsPrefs.setEnabled(context.applicationContext, next)
    }
}

@Composable
fun rememberDebugFpsEnabled(): Boolean {
    val context = LocalContext.current
    // Ensure the store reflects persisted prefs before first collection.
    DisposableEffect(context.applicationContext) {
        DebugFpsStore.init(context.applicationContext)
        onDispose { }
    }
    val enabled by DebugFpsStore.enabled.collectAsStateWithLifecycle()
    return enabled
}

/**
 * Accurate debug FPS meter driven by [Choreographer] vsync callbacks.
 *
 * Why Choreographer and not Compose withFrameNanos: withFrameNanos only
 * fires when Compose schedules a frame, and writing state every frame
 * recomposes every frame (the old FpsMonitor did this). Choreographer
 * fires on every display vsync while the callback is registered, so a
 * blocked main thread delays callbacks and the trailing-1s count drops —
 * a true measure of the "touches feel slow" reports. UI state updates at
 * most every 500ms so the meter itself never causes jank.
 *
 * Debug only — call sites must gate with BuildConfig.DEBUG.
 */
@Composable
fun DebugFpsOverlay(
    modifier: Modifier = Modifier,
) {
    var fps by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val frameTimes = ArrayDeque<Long>(256)
        var lastUiUpdateNanos = 0L
        var lastReported = -1
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                choreographer.postFrameCallback(this)
                frameTimes.addLast(frameTimeNanos)
                val cutoff = frameTimeNanos - DebugFpsCalculations.WINDOW_NANOS
                while (frameTimes.size > 1 && frameTimes.first() <= cutoff) {
                    frameTimes.removeFirst()
                }
                // Bound memory on 120/144Hz devices: 1s window never needs more.
                while (frameTimes.size > 360) {
                    frameTimes.removeFirst()
                }
                if (frameTimeNanos - lastUiUpdateNanos >= 500_000_000L) {
                    lastUiUpdateNanos = frameTimeNanos
                    val computed = DebugFpsCalculations.fpsInWindow(frameTimes, frameTimeNanos)
                    if (computed != lastReported) {
                        lastReported = computed
                        fps = computed
                    }
                }
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
        }
    }

    Text(
        text = LocalContext.current.getString(R.string.debug_fps, fps),
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
 * Global overlay container: sits beneath the status bar, top-left, above
 * every nav destination. Non-clickable so touches pass through to the app.
 * Call inside the activity root Box, after NavHost.
 */
@Composable
fun DebugFpsGlobalOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG || !enabled) return
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 8.dp, top = 4.dp)
            .zIndex(10f),
        contentAlignment = Alignment.TopStart
    ) {
        DebugFpsOverlay()
    }
}
