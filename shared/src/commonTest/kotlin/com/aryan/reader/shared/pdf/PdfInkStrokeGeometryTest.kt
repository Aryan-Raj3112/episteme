package com.aryan.reader.shared.pdf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfInkStrokeGeometryTest {
    @Test
    fun `curve passes through every real sample including a reversal`() {
        val points = listOf(point(0f, 0f), point(1f, 0f), point(0.8f, 0f))

        val segments = buildPdfInkCubicSegments(points, scaleX = 1f, scaleY = 1f)

        assertEquals(2, segments.size)
        assertEquals(1f, segments[0].end.x)
        assertEquals(0.8f, segments[1].end.x)
    }

    @Test
    fun `reversal creates a stable cusp instead of overshooting`() {
        val points = listOf(point(0f, 0f), point(1f, 0f), point(0.8f, 0.1f))

        val segments = buildPdfInkCubicSegments(points, scaleX = 1f, scaleY = 1f)

        assertEquals(segments[0].end, segments[0].control2)
        assertEquals(segments[0].end, segments[1].control1)
    }

    @Test
    fun `straight samples remain straight and end at final input`() {
        val points = listOf(point(0f, 0.25f), point(0.5f, 0.25f), point(1f, 0.25f))

        val segments = buildPdfInkCubicSegments(points, scaleX = 100f, scaleY = 200f)

        assertTrue(segments.all { it.control1.y == 50f && it.control2.y == 50f && it.end.y == 50f })
        assertEquals(100f, segments.last().end.x)
    }

    @Test
    fun `fountain pen edges match the Android benchmark geometry`() {
        val points = listOf(
            PdfPagePoint(0.10f, 0.50f, 0L),
            PdfPagePoint(0.20f, 0.52f, 10L),
            PdfPagePoint(0.35f, 0.60f, 18L),
            PdfPagePoint(0.50f, 0.55f, 40L),
            PdfPagePoint(0.62f, 0.40f, 44L)
        )

        val (left, right) = SharedPdfInkRenderer.calculateFountainPenEdges(
            points = points,
            baseWidthPx = 4f,
            pageWidthPx = 600f,
            pageHeightPx = 800f
        )

        assertEquals(5, left.size)
        assertEquals(5, right.size)
        assertOffsets(
            left,
            listOf(60.515324f to 398.06754f, 120.80837f to 414.86322f, 209.59511f to 479.08902f, 299.27524f to 439.56516f, 371.42795f to 319.65677f)
        )
        assertOffsets(
            right,
            listOf(59.484676f to 401.93246f, 119.19163f to 417.13678f, 210.40489f to 480.91104f, 300.72476f to 440.43484f, 372.57205f to 320.34323f)
        )
    }

    @Test
    fun `fountain pen keeps the stroke centred between its two edges`() {
        val points = listOf(
            PdfPagePoint(0.10f, 0.50f, 0L),
            PdfPagePoint(0.20f, 0.52f, 10L),
            PdfPagePoint(0.35f, 0.60f, 18L)
        )

        val (left, right) = SharedPdfInkRenderer.calculateFountainPenEdges(
            points = points,
            baseWidthPx = 6f,
            pageWidthPx = 600f,
            pageHeightPx = 800f
        )

        assertEquals(left.size, right.size)
        left.zip(right).forEachIndexed { index, (l, r) ->
            val centreX = points[index].x * 600f
            val centreY = points[index].y * 800f
            assertEquals(centreX, (l.x + r.x) / 2f, 1e-2f)
            assertEquals(centreY, (l.y + r.y) / 2f, 1e-2f)
        }
    }

    @Test
    fun `fountain pen needs at least two points`() {
        val (left, right) = SharedPdfInkRenderer.calculateFountainPenEdges(
            points = listOf(PdfPagePoint(0.5f, 0.5f, 0L)),
            baseWidthPx = 4f,
            pageWidthPx = 600f,
            pageHeightPx = 800f
        )

        assertTrue(left.isEmpty())
        assertTrue(right.isEmpty())
    }

    @Test
    fun `fountain pen treats a zero page width as square instead of producing NaN`() {
        // Without the aspect-ratio guard a horizontal stroke gives 0/0 -> Infinity,
        // dy * Infinity -> NaN, and every computed coordinate becomes NaN.
        val points = listOf(
            PdfPagePoint(0.10f, 0.50f, 0L),
            PdfPagePoint(0.90f, 0.50f, 30L)
        )

        val (left, right) = SharedPdfInkRenderer.calculateFountainPenEdges(
            points = points,
            baseWidthPx = 4f,
            pageWidthPx = 0f,
            pageHeightPx = 800f
        )

        assertEquals(2, left.size)
        assertTrue((left + right).all { it.x.isFinite() && it.y.isFinite() }, "expected finite coordinates")
    }

    private fun assertOffsets(
        actual: List<androidx.compose.ui.geometry.Offset>,
        expected: List<Pair<Float, Float>>
    ) {
        assertEquals(expected.size, actual.size)
        actual.zip(expected).forEachIndexed { index, (offset, pair) ->
            assertTrue(
                abs(pair.first - offset.x) <= TOLERANCE,
                "x[$index]: expected ${pair.first}, was ${offset.x}"
            )
            assertTrue(
                abs(pair.second - offset.y) <= TOLERANCE,
                "y[$index]: expected ${pair.second}, was ${offset.y}"
            )
        }
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }

    private fun point(x: Float, y: Float) = PdfPagePoint(x, y, 1L)
}
