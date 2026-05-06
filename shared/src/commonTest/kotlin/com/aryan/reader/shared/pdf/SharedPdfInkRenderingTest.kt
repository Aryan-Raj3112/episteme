package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfInkRenderingTest {

    @Test
    fun `normalized Android stroke widths scale from page width`() {
        assertEquals(
            expected = 8f,
            actual = SharedPdfInkRenderer.effectiveStrokeWidthPx(0.008f, pageWidthPx = 1_000f),
            absoluteTolerance = 0.0001f
        )
        assertEquals(
            expected = 35f,
            actual = SharedPdfInkRenderer.effectiveStrokeWidthPx(0.035f, pageWidthPx = 1_000f),
            absoluteTolerance = 0.0001f
        )
    }

    @Test
    fun `legacy desktop pixel stroke widths remain usable`() {
        assertEquals(
            expected = 12f,
            actual = SharedPdfInkRenderer.effectiveStrokeWidthPx(12f, pageWidthPx = 1_000f),
            absoluteTolerance = 0.0001f
        )
        assertEquals(
            expected = 0.012f,
            actual = SharedPdfInkRenderer.effectiveStrokeWidthNorm(12f, pageWidthPx = 1_000f),
            absoluteTolerance = 0.0001f
        )
    }

    @Test
    fun `snap helper follows Android horizontal and vertical threshold behavior`() {
        val start = PdfPagePoint(0.2f, 0.2f)
        val horizontal = SharedPdfInkRenderer.calculateSnappedPoint(
            currentPoint = PdfPagePoint(0.8f, 0.215f),
            startPoint = start,
            pageAspectRatio = 1f
        )
        val vertical = SharedPdfInkRenderer.calculateSnappedPoint(
            currentPoint = PdfPagePoint(0.215f, 0.8f),
            startPoint = start,
            pageAspectRatio = 1f
        )

        assertEquals(start.y, horizontal.y)
        assertEquals(start.x, vertical.x)
    }

    @Test
    fun `eraser hit test checks full ink segments instead of only sampled points`() {
        val annotation = SharedPdfAnnotation(
            id = "ink",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            tool = PdfInkTool.PEN,
            points = listOf(PdfPagePoint(0.1f, 0.2f), PdfPagePoint(0.9f, 0.2f)),
            colorArgb = 0xFFFF0000.toInt(),
            strokeWidth = 0.008f
        )

        assertTrue(
            SharedPdfInkRenderer.isAnnotationHit(
                annotation = annotation,
                hitPoint = PdfPagePoint(0.5f, 0.205f),
                pageWidthPx = 1_000f,
                pageAspectRatio = 1f,
                eraserStrokeWidth = 0.01f
            )
        )
        assertFalse(
            SharedPdfInkRenderer.isAnnotationHit(
                annotation = annotation,
                hitPoint = PdfPagePoint(0.5f, 0.4f),
                pageWidthPx = 1_000f,
                pageAspectRatio = 1f,
                eraserStrokeWidth = 0.01f
            )
        )
    }

    @Test
    fun `serializer preserves richer shared text annotation style`() {
        val annotation = SharedPdfAnnotation(
            id = "text",
            pageIndex = 2,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            bounds = PdfPageBounds(0.1f, 0.2f, 0.5f, 0.3f),
            text = "Styled note",
            colorArgb = 0xFF101010.toInt(),
            backgroundArgb = 0x55FFEB3B,
            fontSize = 20f,
            isBold = true,
            isItalic = true,
            isUnderline = true,
            isStrikeThrough = true,
            fontName = "Merriweather",
            fontPath = "asset:fonts/merriweather.ttf"
        )

        val decoded = SharedPdfAnnotationSerializer.decode(
            SharedPdfAnnotationSerializer.encode(listOf(annotation))
        )

        assertEquals(listOf(annotation), decoded)
    }
}
