package com.aryan.reader.shared.pdf

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

    private fun point(x: Float, y: Float) = PdfPagePoint(x, y, 1L)
}
