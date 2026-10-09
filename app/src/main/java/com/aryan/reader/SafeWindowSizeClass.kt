package com.aryan.reader

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * Fault-tolerant replacement for [calculateWindowSizeClass].
 *
 * The upstream helper reads the window size through
 * `WindowMetricsCalculator.computeCurrentWindowMetrics(activity)`. On a small
 * number of devices with broken firmware (the framework reports API 34+ but
 * `android.view.WindowMetrics.getDensity()` is missing) that path throws
 * `NoSuchMethodError`. Because this runs directly inside `setContent`, the app
 * died during startup before anything was drawn — the same broken-firmware
 * class as the WorkManager `JobScheduler.forNamespace` crash, which
 * [SafeWorkManager] handles the same way (see crashlytics-triage #55).
 *
 * Instead of catching the `LinkageError` mid-composition (Compose forbids
 * try/catch around composable calls anyway), the missing framework method is
 * *probed* once before the helper is used, and a fallback derived from
 * `LocalConfiguration` is taken when it is absent. That fallback is what
 * material3's own non-Android `calculateWindowSizeClass` does, and it touches no
 * API-34-only framework method. It still reads `LocalConfiguration`, so the size
 * class keeps updating on rotation/resize exactly as before.
 *
 * On healthy devices this is a pass-through to the upstream helper.
 *
 * Note: this stays in the Android app module (not `shared`) because
 * `android.view.WindowMetrics` is an Android-platform API with no common
 * equivalent.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberSafeWindowSizeClass(activity: Activity): WindowSizeClass {
    // Read for its side effect of subscribing to configuration changes, which
    // is how the size class refreshes on rotation — matching upstream.
    val configuration = LocalConfiguration.current
    return if (WindowMetricsAvailability.isBroken()) {
        WindowSizeClass.calculateFromSize(configuration.toSizeClassDp())
    } else {
        calculateWindowSizeClass(activity)
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun Configuration.toSizeClassDp(): DpSize = DpSize(screenWidthDp.dp, screenHeightDp.dp)

/**
 * Whether the platform is missing `WindowMetrics.getDensity()`, which
 * `androidx.window`'s API-34 path calls unconditionally.
 *
 * androidx.window only takes that path when it believes the device is API 34+,
 * so the probe mirrors that exact condition: below 34 the method is legitimately
 * absent and must not be reported as broken.
 */
object WindowMetricsAvailability {
    private const val WINDOW_METRICS_CLASS = "android.view.WindowMetrics"
    private const val GET_DENSITY = "getDensity"

    @Volatile
    private var probeResult: Boolean? = null

    /** Test seam: replaces the reflective lookup so the broken case is reachable. */
    @VisibleForTesting
    @Volatile
    var classProbe: (() -> Result<Any?>)? = null

    fun isBroken(): Boolean {
        probeResult?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val probe = classProbe
            ?: { runCatching { Class.forName(WINDOW_METRICS_CLASS).getMethod(GET_DENSITY) } }
        val broken = probe().isFailure
        if (broken) {
            Timber.e("WindowMetrics.getDensity() is missing on this device; using screen size for layout")
        }
        probeResult = broken
        return broken
    }

    @VisibleForTesting
    fun resetForTests() {
        probeResult = null
    }
}