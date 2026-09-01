package com.aryan.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfJumpHistoryCaptureTest {

    @Test
    fun `settled pager page is used after a completed transition`() {
        assertEquals(
            21,
            authoritativePdfPaginationPageIndex(
                currentPageIndex = 20,
                settledPageIndex = 21,
                isScrollInProgress = false,
            )
        )
    }

    @Test
    fun `current pager page is used while a transition is in progress`() {
        assertEquals(
            21,
            authoritativePdfPaginationPageIndex(
                currentPageIndex = 21,
                settledPageIndex = 19,
                isScrollInProgress = true,
            )
        )
    }

    @Test
    fun `invalid pager snapshots return null`() {
        assertNull(
            authoritativePdfPaginationPageIndex(
                currentPageIndex = -1,
                settledPageIndex = -1,
                isScrollInProgress = false,
            )
        )
    }
}
