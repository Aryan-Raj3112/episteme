package com.aryan.reader

import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

/**
 * Guards the startup-crash hardening for devices whose framework reports
 * API 34+ but lacks `WindowMetrics.getDensity()` (crashlytics-triage #55).
 * Without the probe the app dies inside `setContent` before anything is drawn.
 *
 * The probe result is cached process-wide, so each test resets it to avoid
 * leaking state into other classes in the same JVM.
 */
@RunWith(RobolectricTestRunner::class)
class SafeWindowSizeClassTest {

    @After
    fun tearDown() {
        WindowMetricsAvailability.resetForTests()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
    }

    @Test
    fun `healthy api 34 device uses window metrics`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        // Robolectric's android.jar does declare WindowMetrics.getDensity, so
        // a well-formed device must take the upstream path.
        assertFalse(WindowMetricsAvailability.isBroken())
    }

    @Test
    fun `pre api 34 device is never reported broken even when the class is absent`() {
        // On a real pre-34 device `Class.forName("android.view.WindowMetrics")`
        // fails because the class does not exist. Without the SDK guard the
        // probe would report every old device as broken and silently degrade
        // all of them to screen-size layout — so the absent class is simulated
        // explicitly rather than leaning on Robolectric's android.jar, which
        // declares the method at any SDK and would pass trivially.
        for (sdk in listOf(23, 28, 30, 33)) {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
            WindowMetricsAvailability.resetForTests()
            WindowMetricsAvailability.classProbe = {
                runCatching { throw ClassNotFoundException("android.view.WindowMetrics") }
            }
            try {
                assertFalse("sdk $sdk must not be treated as broken", WindowMetricsAvailability.isBroken())
            } finally {
                WindowMetricsAvailability.classProbe = null
            }
        }
    }

    @Test
    fun `missing getDensity on api 34 is detected`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        WindowMetricsAvailability.classProbe = {
            runCatching { Class.forName("android.view.WindowMetrics").getMethod("notTheRealMethod") }
        }
        try {
            assertTrue(WindowMetricsAvailability.isBroken())
        } finally {
            WindowMetricsAvailability.classProbe = null
        }
    }

    @Test
    fun `probe result is cached across calls`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        var probes = 0
        WindowMetricsAvailability.classProbe = {
            probes++
            runCatching { Class.forName("android.view.WindowMetrics").getMethod("getDensity") }
        }
        try {
            assertFalse(WindowMetricsAvailability.isBroken())
            assertFalse(WindowMetricsAvailability.isBroken())
            assertFalse(WindowMetricsAvailability.isBroken())
            assertEquals("probe must run at most once", 1, probes)
        } finally {
            WindowMetricsAvailability.classProbe = null
        }
    }
}