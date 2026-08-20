package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfSplitDividerPolicyTest {
    @Test
    fun absolutePointerMapsToPaneFractionWithoutIncrementalDrift() {
        val first = pdfSplitDividerFractionAtAbsolutePosition(
            pointerPositionPx = 250f,
            axisSizePx = 1_000,
            dividerThicknessPx = 24,
        )
        val samePointerAgain = pdfSplitDividerFractionAtAbsolutePosition(
            pointerPositionPx = 250f,
            axisSizePx = 1_000,
            dividerThicknessPx = 24,
        )

        assertEquals(first, samePointerAgain)
        assertEquals(0.25f, first, absoluteTolerance = 0.001f)
    }

    @Test
    fun absolutePointerUsesLogicalStartInRtl() {
        val ltr = pdfSplitDividerFractionAtAbsolutePosition(
            pointerPositionPx = 250f,
            axisSizePx = 1_000,
            dividerThicknessPx = 24,
        )
        val rtl = pdfSplitDividerFractionAtAbsolutePosition(
            pointerPositionPx = 750f,
            axisSizePx = 1_000,
            dividerThicknessPx = 24,
            isRtl = true,
        )

        assertEquals(ltr, rtl, absoluteTolerance = 0.001f)
    }

    @Test
    fun pointerPositionIsClampedToReadableBounds() {
        assertEquals(
            MinimumPdfSplitDividerFraction,
            pdfSplitDividerFractionAtAbsolutePosition(-100f, 1_000, 24),
        )
        assertEquals(
            MaximumPdfSplitDividerFraction,
            pdfSplitDividerFractionAtAbsolutePosition(2_000f, 1_000, 24),
        )
    }

    @Test
    fun centerSnapHasHysteresis() {
        val engaged = snapPdfSplitDividerFraction(
            rawFraction = 0.53f,
            wasSnappedToCenter = false,
        )
        val held = snapPdfSplitDividerFraction(
            rawFraction = 0.56f,
            wasSnappedToCenter = engaged.isSnappedToCenter,
        )
        val released = snapPdfSplitDividerFraction(
            rawFraction = 0.58f,
            wasSnappedToCenter = held.isSnappedToCenter,
        )

        assertTrue(engaged.isSnappedToCenter)
        assertEquals(DefaultPdfSplitDividerFraction, engaged.fraction)
        assertTrue(held.isSnappedToCenter)
        assertEquals(DefaultPdfSplitDividerFraction, held.fraction)
        assertFalse(released.isSnappedToCenter)
        assertEquals(0.58f, released.fraction)
    }
}
