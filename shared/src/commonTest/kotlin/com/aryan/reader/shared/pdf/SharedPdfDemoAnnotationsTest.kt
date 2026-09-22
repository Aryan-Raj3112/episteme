package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfDemoAnnotationsTest {
    @Test
    fun `try episteme artwork generates ink on requested page`() {
        val annotations = SharedPdfDemoAnnotations.generate(
            pageIndex = 2,
            baseTimestamp = 1_000L,
            idPrefix = "test_demo",
        )

        assertTrue(annotations.isNotEmpty(), "expected Try Episteme strokes")
        assertTrue(annotations.all { it.pageIndex == 2 })
        assertTrue(annotations.all { it.kind == PdfAnnotationKind.INK })
        assertTrue(annotations.all { it.points.size >= 2 })
        // Dots + text strokes + underline (Android parity: 7 dots, 20+ text strokes, 1 underline).
        assertTrue(annotations.size >= 7 + 12 + 1, "unexpected stroke count=${annotations.size}")
        assertTrue(annotations.any { it.tool == PdfInkTool.FOUNTAIN_PEN })
        assertTrue(annotations.any { it.tool == PdfInkTool.PEN })
        // Page-normalized coordinates stay on-page like Android's transform.
        annotations.flatMap { it.points }.forEach { point ->
            assertTrue(point.x in 0f..1f, "x out of bounds: ${point.x}")
            assertTrue(point.y in 0f..1f, "y out of bounds: ${point.y}")
        }
        // Ids are unique per stroke.
        assertEquals(annotations.size, annotations.map { it.id }.toSet().size)
    }

    @Test
    fun `svg path parser splits pen lifts`() {
        val strokes = SharedPdfDemoAnnotations.parseSvgPathStrokes(
            "M 80 115 L 140 115 M 110 115 L 110 175",
        )

        assertEquals(2, strokes.size)
        assertTrue(strokes.all { it.size >= 2 })
    }
}
