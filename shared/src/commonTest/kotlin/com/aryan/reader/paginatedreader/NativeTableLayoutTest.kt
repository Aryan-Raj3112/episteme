package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the colspan-to-weight mapping both native stacks share (B1 step 3,
 * table block kind). Only the mapping is shared — width distribution stays
 * per-stack.
 */
class NativeTableLayoutTest {
    @Test
    fun `colspan maps to the same row weight`() {
        assertEquals(1f, nativeTableCellWeight(1))
        assertEquals(2f, nativeTableCellWeight(2))
        assertEquals(3f, nativeTableCellWeight(3))
    }

    @Test
    fun `non-positive spans weigh one`() {
        assertEquals(1f, nativeTableCellWeight(0))
        assertEquals(1f, nativeTableCellWeight(-2))
    }
}
