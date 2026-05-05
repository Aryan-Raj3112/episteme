package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfSelectionGeometryTest {

    @Test
    fun `normalizes points against the current viewport size`() {
        val point = PdfSelectionGeometry.normalizedPoint(
            pointX = 50f,
            pointY = 200f,
            viewportWidth = 200,
            viewportHeight = 400
        )

        assertEquals(PdfNormalizedPoint(0.25f, 0.5f), point)
        assertNull(PdfSelectionGeometry.normalizedPoint(50f, 200f, 0, 400))
    }

    @Test
    fun `line fallback picks the nearest character only on a matching line`() {
        val chars = listOf(
            PdfTextCharBounds(index = 1, left = 0.10f, top = 0.10f, right = 0.12f, bottom = 0.13f),
            PdfTextCharBounds(index = 2, left = 0.13f, top = 0.10f, right = 0.15f, bottom = 0.13f),
            PdfTextCharBounds(index = 20, left = 0.10f, top = 0.30f, right = 0.12f, bottom = 0.33f)
        )

        assertEquals(
            2,
            PdfSelectionGeometry.nearestCharOnLine(chars, PdfNormalizedPoint(0.90f, 0.115f))?.index
        )
        assertNull(PdfSelectionGeometry.nearestCharOnLine(chars, PdfNormalizedPoint(0.90f, 0.22f)))
    }

    @Test
    fun `merges text rects by visual line`() {
        val merged = PdfSelectionGeometry.mergeBoundsByLine(
            listOf(
                PdfPageBounds(left = 0.10f, top = 0.10f, right = 0.20f, bottom = 0.13f),
                PdfPageBounds(left = 0.21f, top = 0.101f, right = 0.35f, bottom = 0.131f),
                PdfPageBounds(left = 0.10f, top = 0.20f, right = 0.25f, bottom = 0.23f)
            )
        )

        assertEquals(
            listOf(
                PdfPageBounds(left = 0.10f, top = 0.10f, right = 0.35f, bottom = 0.131f),
                PdfPageBounds(left = 0.10f, top = 0.20f, right = 0.25f, bottom = 0.23f)
            ),
            merged
        )
    }

    @Test
    fun `keeps nearby paragraph lines separate`() {
        val merged = PdfSelectionGeometry.mergeBoundsByLine(
            listOf(
                PdfPageBounds(left = 0.10f, top = 0.10f, right = 0.80f, bottom = 0.13f),
                PdfPageBounds(left = 0.10f, top = 0.118f, right = 0.75f, bottom = 0.148f)
            )
        )

        assertEquals(2, merged.size)
    }
}
