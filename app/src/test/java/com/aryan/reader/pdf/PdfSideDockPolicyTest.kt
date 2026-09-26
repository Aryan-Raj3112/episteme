package com.aryan.reader.pdf

import com.aryan.reader.shared.DockLocation
import com.aryan.reader.shared.pdf.isPdfTextDockSideDocked
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockInLeftHalf
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockSide
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockSticky
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockVertical
import com.aryan.reader.shared.pdf.resolveSharedPdfDockSnapLocation
import com.aryan.reader.shared.pdf.sharedPdfAnnotationDockLeftXPx
import com.aryan.reader.shared.pdf.sharedPdfWheelBaseAngleDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelClampRotationDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelNormalizeDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelRotationForDragDy
import com.aryan.reader.shared.pdf.sharedPdfWheelRotationRangeDeg
import com.aryan.reader.shared.pdf.sharedPdfWheelToolCenterPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Side-edge docking for the PDF annotation + text toolbars.
 *
 * The bars snap to LEFT/RIGHT as well as TOP/BOTTOM/FLOATING and render
 * there as vertical semi-circle-capped bars flush to the edge.
 */
class PdfSideDockPolicyTest {

    @Test
    fun `side docks are sticky and vertical`() {
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.LEFT, isDragging = false))
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.RIGHT, isDragging = false))
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.LEFT, isDragging = true))
        assertTrue(isSharedPdfAnnotationDockSide(DockLocation.LEFT))
        assertTrue(isSharedPdfAnnotationDockSide(DockLocation.RIGHT))
        assertTrue(isSharedPdfAnnotationDockVertical(DockLocation.RIGHT))
        assertFalse(isSharedPdfAnnotationDockSide(DockLocation.TOP))
        assertFalse(isSharedPdfAnnotationDockSide(DockLocation.FLOATING))
    }

    @Test
    fun `side dock left x hugs the edge`() {
        assertEquals(0f, sharedPdfAnnotationDockLeftXPx(DockLocation.LEFT, 400f, 1000f, 56f))
        assertEquals(944f, sharedPdfAnnotationDockLeftXPx(DockLocation.RIGHT, 400f, 1000f, 56f))
        assertEquals(400f, sharedPdfAnnotationDockLeftXPx(DockLocation.FLOATING, 400f, 1000f, 56f))
        assertTrue(isSharedPdfAnnotationDockInLeftHalf(0f, 56f, 1000f))
        assertFalse(isSharedPdfAnnotationDockInLeftHalf(944f, 56f, 1000f))
    }

    @Test
    fun `snap resolver prefers side edges`() {
        assertEquals(DockLocation.LEFT, resolveSharedPdfDockSnapLocation(50f, 50f, 1000f, 1000f))
        assertEquals(DockLocation.RIGHT, resolveSharedPdfDockSnapLocation(900f, 50f, 1000f, 1000f))
        assertEquals(DockLocation.TOP, resolveSharedPdfDockSnapLocation(500f, 50f, 1000f, 1000f))
        assertEquals(DockLocation.BOTTOM, resolveSharedPdfDockSnapLocation(500f, 900f, 1000f, 1000f))
        assertNull(resolveSharedPdfDockSnapLocation(500f, 500f, 1000f, 1000f))
    }

    @Test
    fun `snap resolver docks where the dock touches most`() {
        // Wide horizontal bar (400x56) in the top-left corner touches the
        // top along 400px but the side along 56px -> TOP (not LEFT).
        assertEquals(
            DockLocation.TOP,
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 50f, dockOffsetY = 50f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 400f, dockHeightPx = 56f,
            ),
        )
        // Same bar in the bottom-left corner -> BOTTOM.
        assertEquals(
            DockLocation.BOTTOM,
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 50f, dockOffsetY = 900f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 400f, dockHeightPx = 56f,
            ),
        )
        // Tall side wheel (96x192) in the top-left corner touches the side
        // along 192px but the top along 96px -> LEFT.
        assertEquals(
            DockLocation.LEFT,
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 50f, dockOffsetY = 50f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 96f, dockHeightPx = 192f,
            ),
        )
        // Mid-height against the right edge stays reachable -> RIGHT.
        assertEquals(
            DockLocation.RIGHT,
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 850f, dockOffsetY = 500f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 96f, dockHeightPx = 192f,
            ),
        )
        // Center touches nothing -> floating.
        assertNull(
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 400f, dockOffsetY = 500f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 200f, dockHeightPx = 56f,
            ),
        )
    }

    @Test
    fun `text dock mirrors side detection`() {
        assertTrue(isPdfTextDockSideDocked(DockLocation.LEFT))
        assertTrue(isPdfTextDockSideDocked(DockLocation.RIGHT))
        assertFalse(isPdfTextDockSideDocked(DockLocation.TOP))
        assertFalse(isPdfTextDockSideDocked(DockLocation.FLOATING))
    }

    @Test
    fun `wheel arc centers tools and scrolls large arcs end to end`() {
        assertEquals(-80f, sharedPdfWheelBaseAngleDeg(0, 5))
        assertEquals(0f, sharedPdfWheelBaseAngleDeg(2, 5))
        assertEquals(80f, sharedPdfWheelBaseAngleDeg(4, 5))
        // 4 tools span 120 degrees: fits, no scroll.
        assertEquals(0f, sharedPdfWheelRotationRangeDeg(4))
        // 10 tools span 360 degrees: 106 degrees each way (ends stop 16
        // degrees before the rim so icons are never cut off), clamped.
        assertEquals(106f, sharedPdfWheelRotationRangeDeg(10))
        assertEquals(106f, sharedPdfWheelClampRotationDeg(200f, 10))
        assertEquals(-106f, sharedPdfWheelClampRotationDeg(-200f, 10))
        // Angles wrap so full-circle tools stay placeable.
        assertEquals(-74f, sharedPdfWheelNormalizeDeg(286f))
        assertEquals(170f, sharedPdfWheelNormalizeDeg(-190f))
        // Dragging down spins tools downward.
        assertTrue(sharedPdfWheelRotationForDragDy(0f, 64f, 64f, 10) > 0f)
        // 0 degrees points inward, mirrored across edges.
        val left = sharedPdfWheelToolCenterPx(0f, 64f, 96f, 192f, isLeft = true)
        val right = sharedPdfWheelToolCenterPx(0f, 64f, 96f, 192f, isLeft = false)
        assertEquals(64f, left.x)
        assertEquals(96f - 64f, right.x)
        assertEquals(left.y, right.y)
    }
}
