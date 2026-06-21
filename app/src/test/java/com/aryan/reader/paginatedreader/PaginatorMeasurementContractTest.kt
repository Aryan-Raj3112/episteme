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

    @Test
    fun collapsedVerticalMarginPxForPagination_clampsNegativeFirstMarginToRenderedZero() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = null,
            currentTopMarginPx = -48f
        )

        assertEquals(0, margin)
    }

    @Test
    fun collapsedVerticalMarginPxForPagination_clampsNegativeCollapsedMarginsToRenderedZero() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = -12f,
            currentTopMarginPx = -48f
        )

        assertEquals(0, margin)
    }

    @Test
    fun collapsedVerticalMarginPxForPagination_preservesPositiveCollapsedMargin() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = 14.4f,
            currentTopMarginPx = 20.2f
        )

        assertEquals(20, margin)
    }

    @Test
    fun availableBlockWidthPxForPagination_subtractsRenderedHorizontalMargins() {
        val width = availableBlockWidthPxForPagination(
            containerWidthPx = 996,
            marginLeftPx = 50f,
            marginRightPx = 50f,
            isCenterAligned = false
        )

        assertEquals(896f, width, 0.001f)
    }

    @Test
    fun availableBlockWidthPxForPagination_keepsFullWidthForCenteredBlocks() {
        val width = availableBlockWidthPxForPagination(
            containerWidthPx = 996,
            marginLeftPx = 50f,
            marginRightPx = 50f,
            isCenterAligned = true
        )

        assertEquals(996f, width, 0.001f)
    }
}
