package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-value guard for the B3 lift.
 *
 * These expectations were recorded from Android's own
 * `PdfInkGeometry.calculateFountainPenPoints` *before* it became a delegate to
 * shared. They exist so the migration is provably output-identical rather than
 * merely "believed to be" — if shared's edge maths ever drifts from Android's
 * benchmark, the ink a user draws on a PDF page would silently change shape.
 */
class PdfFountainPenGeometryParityTest {

    @Test
    fun `delegate reproduces the recorded Android geometry`() {
        val points = listOf(
            PdfPoint(0.10f, 0.50f, 0L),
            PdfPoint(0.20f, 0.52f, 10L),
            PdfPoint(0.35f, 0.60f, 18L),
            PdfPoint(0.50f, 0.55f, 40L),
            PdfPoint(0.62f, 0.40f, 44L)
        )

        val (left, right) = PdfInkGeometry.calculateFountainPenPoints(
            points = points,
            baseWidth = 4f,
            pageWidth = 600f,
            pageHeight = 800f
        )

        assertOffsets(
            left,
            listOf(
                60.515324f to 398.06754f,
                120.80837f to 414.86322f,
                209.59511f to 479.08902f,
                299.27524f to 439.56516f,
                371.42795f to 319.65677f
            )
        )
        assertOffsets(
            right,
            listOf(
                59.484676f to 401.93246f,
                119.19163f to 417.13678f,
                210.40489f to 480.91104f,
                300.72476f to 440.43484f,
                372.57205f to 320.34323f
            )
        )
    }

    @Test
    fun `delegate returns one edge per point and none for a single point`() {
        val (left, right) = PdfInkGeometry.calculateFountainPenPoints(
            points = listOf(
                PdfPoint(0.1f, 0.5f, 0L),
                PdfPoint(0.2f, 0.52f, 10L),
                PdfPoint(0.35f, 0.6f, 18L)
            ),
            baseWidth = 6f,
            pageWidth = 600f,
            pageHeight = 800f
        )
        assertEquals(3, left.size)
        assertEquals(3, right.size)

        val (singleLeft, singleRight) = PdfInkGeometry.calculateFountainPenPoints(
            points = listOf(PdfPoint(0.5f, 0.5f, 0L)),
            baseWidth = 4f,
            pageWidth = 600f,
            pageHeight = 800f
        )
        assertTrue(singleLeft.isEmpty())
        assertTrue(singleRight.isEmpty())
    }

    @Test
    fun `delegate stays finite for a degenerate zero-width page`() {
        val (left, right) = PdfInkGeometry.calculateFountainPenPoints(
            points = listOf(PdfPoint(0.1f, 0.5f, 0L), PdfPoint(0.9f, 0.5f, 30L)),
            baseWidth = 4f,
            pageWidth = 0f,
            pageHeight = 800f
        )

        assertEquals(2, left.size)
        assertTrue((left + right).all { it.x.isFinite() && it.y.isFinite() })
    }

    private fun assertOffsets(actual: List<Offset>, expected: List<Pair<Float, Float>>) {
        assertEquals(expected.size, actual.size)
        actual.zip(expected).forEachIndexed { index, (offset, pair) ->
            assertEquals("x[$index]", pair.first, offset.x, 1e-4f)
            assertEquals("y[$index]", pair.second, offset.y, 1e-4f)
        }
    }
}
