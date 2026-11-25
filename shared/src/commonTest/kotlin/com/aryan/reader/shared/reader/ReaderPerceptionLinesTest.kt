package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android is the benchmark, but perception lines exist nowhere else yet, so these are the contract
 * both hosts draw from: the defaults stay subtle and off, sanitising never produces a NaN/negative
 * value that would blank the overlay or draw off-screen, and the two rules never cross.
 */
class ReaderPerceptionLinesTest {

    @Test
    fun defaultsAreOffAndInRange() {
        val defaults = ReaderPerceptionLinesSettings()

        assertFalse(defaults.enabled)
        assertEquals(defaults, defaults.sanitized())
        assertTrue(defaults.safeOpacity in MinPerceptionLinesOpacity..MaxPerceptionLinesOpacity)
        assertTrue(defaults.safeLineWidthDp in MinPerceptionLinesLineWidthDp..MaxPerceptionLinesLineWidthDp)
        assertTrue(defaults.safeInsetFraction in MinPerceptionLinesInsetFraction..MaxPerceptionLinesInsetFraction)
        assertEquals(0xFF000000L, defaults.safeColorArgb and 0xFF000000L)
    }

    @Test
    fun outOfRangeValuesAreClampedInsteadOfRejected() {
        val clamped = ReaderPerceptionLinesSettings(
            opacity = 4f,
            lineWidthDp = -3f,
            insetFraction = 0.9f,
        ).sanitized()

        assertEquals(MaxPerceptionLinesOpacity, clamped.opacity)
        assertEquals(MinPerceptionLinesLineWidthDp, clamped.lineWidthDp)
        assertEquals(MaxPerceptionLinesInsetFraction, clamped.insetFraction)
    }

    @Test
    fun notANumberFallsBackToTheDefault() {
        val settings = ReaderPerceptionLinesSettings(
            opacity = Float.NaN,
            lineWidthDp = Float.NEGATIVE_INFINITY,
            insetFraction = Float.NaN,
        ).sanitized()

        assertEquals(DefaultPerceptionLinesOpacity, settings.opacity)
        assertEquals(DefaultPerceptionLinesWidthDp, settings.lineWidthDp)
        assertEquals(DefaultPerceptionLinesInsetFraction, settings.insetFraction)
    }

    @Test
    fun colourIsStoredOpaqueSoAFadedPickCannotMakeTheRulesInvisible() {
        val translucent = ReaderPerceptionLinesSettings(colorArgb = 0x10FF0000L).sanitized()

        assertEquals(0xFFFF0000L, translucent.colorArgb)
        assertEquals(0xFF0000FFL, ReaderPerceptionLinesSettings(colorArgb = 0xFF0000FFL).safeColorArgb)
    }

    @Test
    fun rulesAreSymmetricAndInsideTheReadingArea() {
        val (left, right) = perceptionLineCenterXs(contentWidthPx = 1000f, insetFraction = 0.1f)

        assertEquals(100f, left)
        assertEquals(900f, right)
        // Symmetric: each rail sits the same distance in from its own edge.
        assertEquals(left, 1000f - right)
    }

    @Test
    fun geometryClampsAndNeverCrosses() {
        val (left, right) = perceptionLineCenterXs(contentWidthPx = 1000f, insetFraction = 4f)

        assertEquals(MaxPerceptionLinesInsetFraction, 0.45f)
        assertEquals(450f, left)
        assertEquals(550f, right)
        assertTrue(right > left)
    }

    @Test
    fun zeroWidthAreaDrawsNothing() {
        assertEquals(0f to 0f, perceptionLineCenterXs(contentWidthPx = 0f, insetFraction = 0.1f))
        assertFalse(hasPerceptionLineBand(contentWidthPx = 0f, insetFraction = 0.1f))
        assertTrue(hasPerceptionLineBand(contentWidthPx = 320f, insetFraction = 0.1f))
    }

    @Test
    fun resetRestoresTheThreeSlidersAndKeepsTheColourAndSwitch() {
        val customised = ReaderPerceptionLinesSettings(
            enabled = true,
            colorArgb = 0xFF1976D2L,
            opacity = 0.35f,
            lineWidthDp = 6f,
            insetFraction = 0.3f,
        )
        assertTrue(customised.hasCustomSliderValues)

        val reset = customised.withDefaultSliderValues()

        assertEquals(DefaultPerceptionLinesOpacity, reset.opacity)
        assertEquals(DefaultPerceptionLinesWidthDp, reset.lineWidthDp)
        assertEquals(DefaultPerceptionLinesInsetFraction, reset.insetFraction)
        // The reset is scoped to the sliders: the reader keeps their colour and their on/off choice.
        assertEquals(0xFF1976D2L, reset.colorArgb)
        assertTrue(reset.enabled)
        assertFalse(reset.hasCustomSliderValues)
    }

    @Test
    fun resetIsAPlainNoOpOnDefaultSliders() {
        val defaults = ReaderPerceptionLinesSettings(enabled = true)

        assertFalse(defaults.hasCustomSliderValues)
        assertEquals(defaults, defaults.withDefaultSliderValues())
    }

    @Test
    fun aSingleCustomisedSliderIsEnoughToEnableReset() {
        assertTrue(
            ReaderPerceptionLinesSettings(opacity = 0.2f).hasCustomSliderValues
        )
        assertTrue(
            ReaderPerceptionLinesSettings(lineWidthDp = 7f).hasCustomSliderValues
        )
        assertTrue(
            ReaderPerceptionLinesSettings(insetFraction = 0.44f).hasCustomSliderValues
        )
    }
}
