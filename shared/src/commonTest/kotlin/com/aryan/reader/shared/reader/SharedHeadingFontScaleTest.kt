package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity item B1. This table existed three times with two different values: Android's
 * `headerFontScale`, and copies in `SharedNativeSelectionMapping.kt` and
 * `SharedMeasuredEpubPaginator.kt` that were wrong at every level below h1 and had no h5
 * case at all — so iOS rendered h2-h5 headings visibly smaller than Android.
 *
 * Android is the benchmark, so the values below are Android's.
 */
class SharedHeadingFontScaleTest {

    @Test
    fun `heading scales match the Android benchmark table`() {
        assertEquals(1.5f, sharedHeadingFontScale(1))
        assertEquals(1.4f, sharedHeadingFontScale(2))
        assertEquals(1.3f, sharedHeadingFontScale(3))
        assertEquals(1.2f, sharedHeadingFontScale(4))
        assertEquals(1.1f, sharedHeadingFontScale(5))
    }

    @Test
    fun `non heading levels are unscaled`() {
        assertEquals(1.0f, sharedHeadingFontScale(0))
        assertEquals(1.0f, sharedHeadingFontScale(6))
        assertEquals(1.0f, sharedHeadingFontScale(-1))
        assertEquals(1.0f, sharedHeadingFontScale(Int.MAX_VALUE))
    }

    @Test
    fun `scales decrease monotonically down to unscaled`() {
        val scales = (1..6).map { sharedHeadingFontScale(it) }
        scales.zipWithNext { higher, lower ->
            assertEquals(true, higher > lower, "expected $higher > $lower for $scales")
        }
        assertEquals(1.0f, scales.last())
    }
}