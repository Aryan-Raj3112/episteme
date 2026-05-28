package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeVerticalLocationTest {

    @Test
    fun `compat page follows native progress`() {
        assertEquals(0, nativeVerticalCompatPageForProgress(0f, 101))
        assertEquals(50, nativeVerticalCompatPageForProgress(50f, 101))
        assertEquals(100, nativeVerticalCompatPageForProgress(100f, 101))
    }

    @Test
    fun `progress follows compat page`() {
        assertEquals(0f, nativeVerticalProgressForCompatPage(0, 101), 0.001f)
        assertEquals(50f, nativeVerticalProgressForCompatPage(50, 101), 0.001f)
        assertEquals(100f, nativeVerticalProgressForCompatPage(100, 101), 0.001f)
    }

    @Test
    fun `progress target skips zero weight chapter gaps`() {
        val weights = listOf(0, 100, 300, 600)

        assertEquals(1, nativeVerticalProgressToItemIndex(weights, 0f))
        assertEquals(2, nativeVerticalProgressToItemIndex(weights, 25f))
        assertEquals(3, nativeVerticalProgressToItemIndex(weights, 100f))
    }
}
