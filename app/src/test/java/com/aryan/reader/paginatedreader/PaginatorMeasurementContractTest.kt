package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Test

class PaginatorMeasurementContractTest {
    @Test
    fun measuredTextHeightForPagination_keepsLayoutHeightWhenItContainsLastLineBottom() {
        val measuredHeight = measuredTextHeightForPagination(
            layoutHeightPx = 120,
            lastLineBottomPx = 119.2f
        )

        assertEquals(120, measuredHeight)
    }

    @Test
    fun measuredTextHeightForPagination_usesCeiledLastLineBottomWhenItExceedsLayoutHeight() {
        val measuredHeight = measuredTextHeightForPagination(
            layoutHeightPx = 120,
            lastLineBottomPx = 132.1f
        )

        assertEquals(133, measuredHeight)
    }
}
