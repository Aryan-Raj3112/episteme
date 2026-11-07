package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the list-marker geometry both native stacks render (B1 step 3, list
 * block kind). The slot was already equal; the image scale adopts Android's
 * 0.8 over shared's drifted 0.85.
 */
class NativeListMarkerGeometryTest {
    @Test
    fun `marker slot matches the Android benchmark`() {
        assertEquals(32, NATIVE_LIST_MARKER_AREA_WIDTH_DP)
        assertEquals(8, NATIVE_LIST_MARKER_END_PADDING_DP)
    }

    @Test
    fun `marker image scale is Android's 0_8`() {
        assertEquals(0.8f, NATIVE_LIST_MARKER_IMAGE_SCALE)
    }

    @Test
    fun `marker image size scales the caller's base`() {
        assertEquals(16f * 0.8f, nativeListMarkerImageSizeSp(16f))
        assertEquals(24f * 0.8f, nativeListMarkerImageSizeSp(24f))
        assertEquals(0f, nativeListMarkerImageSizeSp(0f))
    }
}
