package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.PdfAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationSelectionTest {

    private fun annotation(
        id: String,
        points: List<PdfPoint>,
        inkType: InkType = InkType.PEN,
        strokeWidth: Float = 0.008f,
    ) = PdfAnnotation(
        type = AnnotationType.INK,
        inkType = inkType,
        pageIndex = 0,
        points = points,
        color = Color.Red,
        strokeWidth = strokeWidth,
        id = id,
    )

    private fun line(id: String) = annotation(
        id, listOf(PdfPoint(0.2f, 0.2f), PdfPoint(0.4f, 0.4f))
    )

    @Test
    fun `tap hit finds topmost stroke`() {
        val annotations = listOf(line("a"), line("b"))
        val hit = findPdfTopmostSelectionHit(
            annotations, 0.3f, 0.3f, pageWidthPx = 1000f, pageAspectRatio = 0.7f
        )
        assertEquals("b", hit?.id)
    }

    @Test
    fun `tap miss returns null`() {
        val annotations = listOf(line("a"))
        val hit = findPdfTopmostSelectionHit(
            annotations, 0.9f, 0.9f, pageWidthPx = 1000f, pageAspectRatio = 0.7f
        )
        assertNull(hit)
    }

    @Test
    fun `highlighter tap uses generous bounds`() {
        val highlight = annotation(
            "h",
            listOf(PdfPoint(0.2f, 0.5f), PdfPoint(0.6f, 0.5f)),
            inkType = InkType.HIGHLIGHTER,
            strokeWidth = 0.035f,
        )
        // Just outside the stroke but within slop + half width.
        val hit = findPdfTopmostSelectionHit(
            listOf(highlight), 0.4f, 0.53f, pageWidthPx = 1000f, pageAspectRatio = 0.7f
        )
        assertNotNull(hit)
    }

    @Test
    fun `move transform translates points`() {
        val annotations = listOf(line("a"))
        val moved = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Move(0.1f, 0.1f),
            pageAspectRatio = 0.7f,
        )
        assertEquals(0.3f, moved.first().points.first().x, 1e-5f)
        assertEquals(0.3f, moved.first().points.first().y, 1e-5f)
    }

    @Test
    fun `transform ignores unselected ids`() {
        val annotations = listOf(line("a"), line("b"))
        val moved = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Move(0.1f, 0f),
            pageAspectRatio = 0.7f,
        )
        assertEquals(0.2f, moved[1].points.first().x, 1e-5f)
    }

    @Test
    fun `scale transform grows points and stroke width`() {
        val annotations = listOf(line("a"))
        val scaled = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Scale(pivotX = 0f, pivotY = 0f, scale = 1.5f),
            pageAspectRatio = 0.7f,
        )
        assertEquals(0.3f, scaled.first().points.first().x, 1e-5f)
        assertEquals(0.008f * 1.5f, scaled.first().strokeWidth, 1e-6f)
    }

    @Test
    fun `scale clamps stroke width to tool range`() {
        val annotations = listOf(line("a"))
        val scaled = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Scale(pivotX = 0f, pivotY = 0f, scale = 10f),
            pageAspectRatio = 0.7f,
        )
        assertEquals(0.015f, scaled.first().strokeWidth, 1e-6f)
    }

    @Test
    fun `rotate transform moves points around center`() {
        val annotations = listOf(annotation("a", listOf(PdfPoint(0.6f, 0.5f))))
        val rotated = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Rotate(centerX = 0.5f, centerY = 0.5f, angleDegrees = 90f),
            pageAspectRatio = 1f,
        )
        assertEquals(0.5f, rotated.first().points.first().x, 1e-4f)
        assertEquals(0.6f, rotated.first().points.first().y, 1e-4f)
    }

    @Test
    fun `lasso selects touched strokes`() {
        val touched = line("in")
        val far = annotation("out", listOf(PdfPoint(0.8f, 0.8f), PdfPoint(0.9f, 0.9f)))
        // Open polyline crossing the "in" stroke, nowhere near "out".
        val lasso = listOf(PdfPoint(0.1f, 0.4f), PdfPoint(0.5f, 0.1f))
        val ids = findPdfLassoSelectionHits(listOf(touched, far), lasso, touchToleranceNorm = 0.01f)
        assertEquals(setOf("in"), ids)
    }

    @Test
    fun `lasso enclosing without touching selects nothing`() {
        val inside = line("in")
        val loop = listOf(
            PdfPoint(0f, 0f), PdfPoint(0.6f, 0f), PdfPoint(0.6f, 0.6f), PdfPoint(0f, 0.6f)
        )
        assertTrue(findPdfLassoSelectionHits(listOf(inside), loop, touchToleranceNorm = 0.01f).isEmpty())
    }

    @Test
    fun `union bounds cover all selected`() {
        val union = pdfSelectionUnionBounds(listOf(line("a"), line("b")))
        assertNotNull(union)
        assertTrue(union!!.left <= 0.2f)
        assertTrue(union.right >= 0.4f)
    }

    @Test
    fun `restore by id swaps snapshot versions`() {
        val current = listOf(line("a").copy(strokeWidth = 0.01f))
        val snapshot = listOf(line("a").copy(strokeWidth = 0.005f))
        val restored = restorePdfAnnotationsById(current, snapshot)
        assertEquals(0.005f, restored.first().strokeWidth, 1e-6f)
        assertTrue(restorePdfAnnotationsById(current, emptyList()) === current)
    }

    @Test
    fun `handle positions include corners plus mid-sides plus rotate`() {
        val bounds = Rect(0.2f, 0.2f, 0.6f, 0.6f)
        val handles = pdfSelectionHandlePositions(bounds)
        assertEquals(9, handles.size)
        assertEquals(PdfPoint(0.2f, 0.2f), handles[PdfSelectionHandle.TOP_LEFT])
        assertEquals(PdfPoint(0.6f, 0.6f), handles[PdfSelectionHandle.BOTTOM_RIGHT])
        assertEquals(PdfPoint(0.4f, 0.2f), handles[PdfSelectionHandle.TOP_MIDDLE])
        assertEquals(PdfPoint(0.4f, 0.6f), handles[PdfSelectionHandle.BOTTOM_MIDDLE])
        assertEquals(PdfPoint(0.2f, 0.4f), handles[PdfSelectionHandle.LEFT_MIDDLE])
        assertEquals(PdfPoint(0.6f, 0.4f), handles[PdfSelectionHandle.RIGHT_MIDDLE])
        val rotate = handles.getValue(PdfSelectionHandle.ROTATE)
        assertEquals(0.4f, rotate.x, 1e-5f)
        assertTrue(rotate.y < 0.2f)
    }

    @Test
    fun `edge pivots are opposite middles`() {
        val bounds = Rect(0f, 0f, 1f, 1f)
        assertEquals(PdfPoint(0.5f, 1f), pdfPivotForHandle(PdfSelectionHandle.TOP_MIDDLE, bounds))
        assertEquals(PdfPoint(0.5f, 0f), pdfPivotForHandle(PdfSelectionHandle.BOTTOM_MIDDLE, bounds))
        assertEquals(PdfPoint(1f, 0.5f), pdfPivotForHandle(PdfSelectionHandle.LEFT_MIDDLE, bounds))
        assertEquals(PdfPoint(0f, 0.5f), pdfPivotForHandle(PdfSelectionHandle.RIGHT_MIDDLE, bounds))
    }

    @Test
    fun `edge drag stretches one axis`() {
        val pivot = PdfPoint(0.2f, 0.4f)
        // Right-middle drag doubling the x distance: x scales, y holds.
        val (sx, sy) = pdfScaleXYForEdgeDrag(
            PdfSelectionHandle.RIGHT_MIDDLE,
            pivot,
            startNorm = PdfPoint(0.6f, 0.4f),
            currentNorm = PdfPoint(1.0f, 0.9f),
        )
        assertEquals(2f, sx, 1e-5f)
        assertEquals(1f, sy, 1e-6f)
        // Top-middle drag: y only.
        val (sx2, sy2) = pdfScaleXYForEdgeDrag(
            PdfSelectionHandle.TOP_MIDDLE,
            PdfPoint(0.4f, 0.6f),
            startNorm = PdfPoint(0.4f, 0.2f),
            currentNorm = PdfPoint(0.9f, 0.4f),
        )
        assertEquals(1f, sx2, 1e-6f)
        assertEquals(0.5f, sy2, 1e-5f)
        // Corners are not edge drags.
        assertEquals(1f to 1f, pdfScaleXYForEdgeDrag(
            PdfSelectionHandle.BOTTOM_RIGHT, pivot, pivot, pivot
        ))
    }

    @Test
    fun `non-uniform scale stretches x only and grows width by geometric mean`() {
        val annotations = listOf(line("a"))
        val scaled = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.ScaleNonUniform(pivotX = 0f, pivotY = 0f, scaleX = 2f, scaleY = 1f),
            pageAspectRatio = 0.7f,
        )
        val points = scaled.first().points
        assertEquals(0.4f, points.first().x, 1e-5f)
        assertEquals(0.2f, points.first().y, 1e-5f)
        assertEquals(0.008f * kotlin.math.sqrt(2f), scaled.first().strokeWidth, 1e-6f)
    }

    @Test
    fun `rotation snaps to cardinals within five degrees`() {
        // ~87-degree drag settles on 90.
        assertEquals(
            90f,
            pdfRotationForDrag(
                centerX = 0.5f, centerY = 0.5f,
                startNorm = PdfPoint(0.6f, 0.5f),
                currentNorm = PdfPoint(0.5052f, 0.5999f),
                pageAspectRatio = 1f,
            ),
            0.5f,
        )
        // 80 degrees is outside the window and stays free.
        assertEquals(
            80f,
            pdfRotationForDrag(
                centerX = 0.5f, centerY = 0.5f,
                startNorm = PdfPoint(0.6f, 0.5f),
                currentNorm = PdfPoint(0.5174f, 0.5985f),
                pageAspectRatio = 1f,
            ),
            0.5f,
        )
        // 45 degrees stays free.
        assertEquals(
            45f,
            pdfRotationForDrag(
                centerX = 0.5f, centerY = 0.5f,
                startNorm = PdfPoint(0.6f, 0.5f),
                currentNorm = PdfPoint(0.5707f, 0.5707f),
                pageAspectRatio = 1f,
            ),
            0.5f,
        )
    }

    @Test
    fun `rotation display normalizes to 0 to 359`() {
        assertEquals(48, pdfNormalizeRotationDisplay(48.4f))
        assertEquals(270, pdfNormalizeRotationDisplay(-90f))
        assertEquals(180, pdfNormalizeRotationDisplay(-180f))
        assertEquals(0, pdfNormalizeRotationDisplay(359.6f))
        assertEquals(0, pdfNormalizeRotationDisplay(0f))
    }

    @Test
    fun `pivot is opposite corner`() {
        val bounds = Rect(0f, 0f, 1f, 1f)
        assertEquals(
            PdfPoint(1f, 1f),
            pdfPivotForHandle(PdfSelectionHandle.TOP_LEFT, bounds)
        )
        assertEquals(
            PdfPoint(0f, 0f),
            pdfPivotForHandle(PdfSelectionHandle.BOTTOM_RIGHT, bounds)
        )
    }

    @Test
    fun `empty selection reports empty`() {
        assertTrue(PdfInkSelection().isEmpty)
        assertFalse(PdfInkSelection(0, setOf("a")).isEmpty)
        assertTrue(PdfInkSelection(null, setOf("a")).isEmpty)
    }

    @Test
    fun `move overdrag stops at edge without smushing`() {
        val annotations = listOf(
            annotation("a", listOf(PdfPoint(0.7f, 0.2f), PdfPoint(0.9f, 0.2f)))
        )
        val moved = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Move(0.5f, 0f),
            pageAspectRatio = 0.7f,
        )
        val points = moved.first().points
        // Union right edge lands exactly on 1, shape preserved (no pile-up).
        assertEquals(1f, points.maxOf { it.x }, 1e-5f)
        assertEquals(0.8f, points.minOf { it.x }, 1e-5f)
        assertEquals(0.2f, points[1].x - points[0].x, 1e-5f)
    }

    @Test
    fun `scale overdrag caps growth at edge`() {
        val annotations = listOf(line("a"))
        val scaled = applyPdfSelectionTransform(
            annotations,
            setOf("a"),
            PdfSelectionTransform.Scale(pivotX = 0f, pivotY = 0f, scale = 10f),
            pageAspectRatio = 0.7f,
        )
        val points = scaled.first().points
        // 0.4 * 2.5 = 1: capped, every point still in-bounds.
        assertEquals(1f, points.maxOf { it.x }, 1e-5f)
        assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(0.5f, points[0].x, 1e-5f)
    }
}
