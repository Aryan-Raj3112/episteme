package com.aryan.reader.shared.pdf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPdfSelectionGeometryTest {

    private fun pt(x: Float, y: Float) = PdfPagePoint(x, y, 0L)

    @Test
    fun boundsOfPoints() {
        assertNull(pdfInkPointsBounds(emptyList()))
        val bounds = pdfInkPointsBounds(listOf(pt(0.2f, 0.3f), pt(0.6f, 0.1f), pt(0.4f, 0.8f)))
        assertNotNull(bounds)
        assertEquals(0.2f, bounds.left)
        assertEquals(0.1f, bounds.top)
        assertEquals(0.6f, bounds.right)
        assertEquals(0.8f, bounds.bottom)
    }

    @Test
    fun unionBounds() {
        assertNull(pdfInkUnionBounds(emptyList()))
        val union = pdfInkUnionBounds(
            listOf(
                PdfPageBounds(0f, 0f, 0.3f, 0.3f),
                PdfPageBounds(0.5f, 0.5f, 0.9f, 0.9f),
            )
        )
        assertNotNull(union)
        assertEquals(0f, union.left)
        assertEquals(0f, union.top)
        assertEquals(0.9f, union.right)
        assertEquals(0.9f, union.bottom)
    }

    @Test
    fun moveClampsToPage() {
        val moved = listOf(pt(0.9f, 0.9f)).movedPdfPointsBy(0.2f, 0.2f)
        assertEquals(1f, moved.first().x)
        assertEquals(1f, moved.first().y)
        val same = listOf(pt(0.1f, 0.1f))
        assertTrue(same.movedPdfPointsBy(0f, 0f) === same)
    }

    @Test
    fun scaleAroundPivot() {
        val points = listOf(pt(0.2f, 0.2f), pt(0.4f, 0.4f))
        val scaled = points.scaledPdfPointsAround(pivotX = 0f, pivotY = 0f, scaleX = 2f, scaleY = 2f)
        assertEquals(0.4f, scaled[0].x, 1e-5f)
        assertEquals(0.8f, scaled[1].x, 1e-5f)
    }

    @Test
    fun rotate90DegreesAroundCenter() {
        val points = listOf(pt(0.6f, 0.5f))
        val rotated = points.rotatedPdfPointsAround(0.5f, 0.5f, 90f, pageAspectRatio = 1f)
        assertEquals(0.5f, rotated.first().x, 1e-5f)
        assertEquals(0.6f, rotated.first().y, 1e-5f)
    }

    @Test
    fun rotateZeroIsIdentity() {
        val points = listOf(pt(0.2f, 0.3f))
        assertTrue(points.rotatedPdfPointsAround(0.5f, 0.5f, 0f) === points)
    }

    @Test
    fun uniformScaleFromCornerDrag() {
        // Doubling the distance from the pivot yields scale 2.
        val scale = pdfUniformScaleForCornerDrag(
            pivotX = 0f, pivotY = 0f,
            startX = 0.25f, startY = 0f,
            currentX = 0.5f, currentY = 0f,
        )
        assertEquals(2f, scale, 1e-5f)
        // Degenerate start distance yields identity.
        assertEquals(
            1f,
            pdfUniformScaleForCornerDrag(0.5f, 0.5f, 0.5f, 0.5f, 0.9f, 0.9f),
            1e-5f,
        )
    }

    @Test
    fun rotationSnap() {
        assertEquals(15f, pdfSnappedRotationDegrees(14f), 1e-5f)
        // Outside threshold stays free.
        assertEquals(10f, pdfSnappedRotationDegrees(10f), 1e-5f)
    }

    @Test
    fun pointInPolygon() {
        val square = listOf(pt(0f, 0f), pt(1f, 0f), pt(1f, 1f), pt(0f, 1f))
        assertTrue(isPdfPointInPolygon(pt(0.5f, 0.5f), square))
        assertFalse(isPdfPointInPolygon(pt(1.5f, 0.5f), square))
        assertFalse(isPdfPointInPolygon(pt(0.5f, 0.5f), listOf(pt(0f, 0f), pt(1f, 0f))))
    }

    @Test
    fun segSegDistance() {
        // Crossing segments touch.
        assertEquals(
            0f,
            pdfSegSegDistSq(0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f),
            1e-6f,
        )
        // Parallel offset by 1.
        assertEquals(
            1f,
            pdfSegSegDistSq(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f),
            1e-6f,
        )
        // Endpoint to segment.
        assertEquals(
            1f,
            pdfSegSegDistSq(2f, 0f, 3f, 0f, 0f, 0f, 1f, 0f),
            1e-6f,
        )
        // Degenerate point to segment.
        assertEquals(
            0.25f,
            pdfSegSegDistSq(0.5f, 0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 0f),
            1e-6f,
        )
    }

    @Test
    fun strokeTouchedByPolyline() {
        val stroke = listOf(pt(0.2f, 0.2f), pt(0.4f, 0.4f))
        // Lasso line crossing the stroke selects it.
        assertTrue(
            pdfIsStrokeTouchedByPolyline(
                stroke,
                listOf(pt(0.1f, 0.4f), pt(0.5f, 0.1f)),
                toleranceNorm = 0.01f,
            )
        )
        // Distant line does not.
        assertFalse(
            pdfIsStrokeTouchedByPolyline(
                stroke,
                listOf(pt(0.7f, 0.7f), pt(0.9f, 0.9f)),
                toleranceNorm = 0.01f,
            )
        )
        // Near-miss within tolerance still counts (finger slop).
        assertTrue(
            pdfIsStrokeTouchedByPolyline(
                stroke,
                listOf(pt(0.3f, 0.32f), pt(0.5f, 0.32f)),
                toleranceNorm = 0.05f,
            )
        )
        // Enclosing without touching does NOT select (no contain rule).
        assertFalse(
            pdfIsStrokeTouchedByPolyline(
                stroke,
                listOf(pt(0f, 0f), pt(0.6f, 0f), pt(0.6f, 0.6f), pt(0f, 0.6f), pt(0f, 0f)),
                toleranceNorm = 0.01f,
            )
        )
        assertFalse(pdfIsStrokeTouchedByPolyline(emptyList(), stroke, 0.01f))
        assertFalse(pdfIsStrokeTouchedByPolyline(stroke, emptyList(), 0.01f))
    }

    @Test
    fun tapHitOnSegment() {
        val line = listOf(pt(0.1f, 0.5f), pt(0.9f, 0.5f))
        assertTrue(pdfIsStrokeTapHit(line, 0.5f, 0.5f, tapSlopNorm = 0.02f, strokeWidthNorm = 0.008f))
        assertFalse(pdfIsStrokeTapHit(line, 0.5f, 0.9f, tapSlopNorm = 0.02f, strokeWidthNorm = 0.008f))
        // Single dot hit.
        assertTrue(
            pdfIsStrokeTapHit(
                listOf(pt(0.5f, 0.5f)), 0.5f, 0.505f,
                tapSlopNorm = 0.02f, strokeWidthNorm = 0.008f,
            )
        )
        assertFalse(pdfIsStrokeTapHit(emptyList(), 0.5f, 0.5f, 0.02f, 0.008f))
    }

    @Test
    fun angleAroundCenter() {
        // Point directly below center reads 90 degrees.
        assertEquals(90f, abs(pdfAngleAroundCenterDegrees(0.5f, 0.5f, 0.5f, 1f)), 1e-4f)
    }

    @Test
    fun moveDeltaPassesThroughWhenInBounds() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        val (dx, dy) = pdfClampedMoveDelta(bounds, 0.1f, -0.1f)
        assertEquals(0.1f, dx, 1e-6f)
        assertEquals(-0.1f, dy, 1e-6f)
    }

    @Test
    fun moveDeltaStopsAtPageEdges() {
        val bounds = PdfPageBounds(0.7f, 0.1f, 0.9f, 0.3f)
        // Over-drag right: clamped so the union right edge lands on 1.
        val (dx, _) = pdfClampedMoveDelta(bounds, 0.5f, 0f)
        assertEquals(0.1f, dx, 1e-6f)
        // Over-drag left/top: clamped so the union stays >= 0.
        val (dx2, dy2) = pdfClampedMoveDelta(bounds, -2f, -2f)
        assertEquals(-0.7f, dx2, 1e-6f)
        assertEquals(-0.1f, dy2, 1e-6f)
    }

    @Test
    fun cappedScalePassesThroughShrink() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        assertEquals(0.5f, pdfCappedUniformScale(0f, 0f, bounds, 0.5f), 1e-6f)
        assertEquals(1f, pdfCappedUniformScale(0f, 0f, bounds, 1f), 1e-6f)
    }

    @Test
    fun cappedScaleStopsGrowthAtPageEdge() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        // Growing around origin would push right/bottom to 0.4 * s <= 1.
        assertEquals(2.5f, pdfCappedUniformScale(0f, 0f, bounds, 10f), 1e-5f)
        // Pass-through drags keep existing behavior.
        assertEquals(-1f, pdfCappedUniformScale(0f, 0f, bounds, -1f), 1e-6f)
    }

    @Test
    fun cappedNonUniformScaleCapsAxesIndependently() {
        val bounds = PdfPageBounds(0.2f, 0.2f, 0.4f, 0.4f)
        // X would hit the edge at 2.5x, Y is free at 1.5x.
        val (sx, sy) = pdfCappedNonUniformScale(0f, 0f, bounds, 10f, 1.5f)
        assertEquals(2.5f, sx, 1e-5f)
        assertEquals(1.5f, sy, 1e-6f)
        // Shrinking passes through on both axes.
        val (sx2, sy2) = pdfCappedNonUniformScale(0f, 0f, bounds, 0.5f, 0.5f)
        assertEquals(0.5f, sx2, 1e-6f)
        assertEquals(0.5f, sy2, 1e-6f)
    }

    @Test
    fun selectionTouchSlopPassesThroughSanePlatformValues() {
        // Android-scale platform slop is kept as-is (tap-to-select parity).
        assertEquals(28f, sharedPdfSelectionTouchSlopPx(28f, 24f), 1e-6f)
        assertEquals(32f, sharedPdfSelectionTouchSlopPx(32f, 24f), 1e-6f)
    }

    @Test
    fun selectionTouchSlopFloorsTinyPlatformValuesToFingerSize() {
        // A tiny platform slop would turn finger jitter into phantom moves
        // (taps swallowed) and shrink hit targets to the stroke half-width.
        assertEquals(24f, sharedPdfSelectionTouchSlopPx(0f, 24f), 1e-6f)
        assertEquals(24f, sharedPdfSelectionTouchSlopPx(2f, 24f), 1e-6f)
        assertEquals(24f, sharedPdfSelectionTouchSlopPx(-4f, 24f), 1e-6f)
        assertEquals(24f, sharedPdfSelectionTouchSlopPx(Float.NaN, 24f), 1e-6f)
        assertEquals(24f, sharedPdfSelectionTouchSlopPx(Float.POSITIVE_INFINITY, 24f), 1e-6f)
    }
}
