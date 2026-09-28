package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReaderOrientationTest {

    @Test
    fun firstViewportNeverCountsAsOrientationFlip() {
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 0,
                previousHeightPx = 0,
                currentWidthPx = 1170,
                currentHeightPx = 2532,
            )
        )
    }

    @Test
    fun portraitToLandscapeCounts() {
        assertTrue(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 1170,
                previousHeightPx = 2532,
                currentWidthPx = 2532,
                currentHeightPx = 1170,
            )
        )
    }

    @Test
    fun landscapeBackToPortraitCounts() {
        assertTrue(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 2532,
                previousHeightPx = 1170,
                currentWidthPx = 1170,
                currentHeightPx = 2532,
            )
        )
    }

    @Test
    fun sameOrientationResizeDoesNotCount() {
        // iPad split divider drag, chrome inset change, keyboard avoidance.
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 1024,
                previousHeightPx = 1366,
                currentWidthPx = 700,
                currentHeightPx = 1366,
            )
        )
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 1366,
                previousHeightPx = 800,
                currentWidthPx = 1366,
                currentHeightPx = 1024,
            )
        )
    }

    @Test
    fun squareIntermediateSizeDoesNotCount() {
        // iOS animates through intermediate sizes; a square frame must not
        // report a flip on its own, and the real flip is still caught.
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 1170,
                previousHeightPx = 2532,
                currentWidthPx = 1200,
                currentHeightPx = 1200,
            )
        )
        assertTrue(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 1200,
                previousHeightPx = 1200,
                currentWidthPx = 2532,
                currentHeightPx = 1170,
            )
        )
    }

    @Test
    fun unusableViewportsDoNotCount() {
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = 2532,
                previousHeightPx = 1170,
                currentWidthPx = 1170,
                currentHeightPx = 0,
            )
        )
        assertFalse(
            sharedReaderViewportFlippedOrientation(
                previousWidthPx = -1,
                previousHeightPx = 1170,
                currentWidthPx = 1170,
                currentHeightPx = 2532,
            )
        )
    }

    @Test
    fun restoreDelayMatchesAndroidOrientationScroll() {
        assertEquals(300L, SharedReaderOrientationRestoreDelayMillis)
    }
}
