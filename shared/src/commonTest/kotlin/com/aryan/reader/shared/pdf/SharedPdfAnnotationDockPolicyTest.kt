package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.DockLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfAnnotationDockPolicyTest {
    @Test
    fun `full bar when sticky or not minimized`() {
        assertTrue(isSharedPdfAnnotationDockFullBar(isSticky = true, isMinimized = true))
        assertTrue(isSharedPdfAnnotationDockFullBar(isSticky = true, isMinimized = false))
        assertTrue(isSharedPdfAnnotationDockFullBar(isSticky = false, isMinimized = false))
        assertFalse(isSharedPdfAnnotationDockFullBar(isSticky = false, isMinimized = true))
    }

    @Test
    fun `sticky only for docked locations when not dragging`() {
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.TOP, isDragging = false))
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.BOTTOM, isDragging = false))
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.LEFT, isDragging = false))
        assertTrue(isSharedPdfAnnotationDockSticky(DockLocation.RIGHT, isDragging = false))
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.TOP, isDragging = true))
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.LEFT, isDragging = true))
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.FLOATING, isDragging = false))
    }

    @Test
    fun `side docks are vertical`() {
        assertTrue(isSharedPdfAnnotationDockSide(DockLocation.LEFT))
        assertTrue(isSharedPdfAnnotationDockSide(DockLocation.RIGHT))
        assertTrue(isSharedPdfAnnotationDockVertical(DockLocation.LEFT))
        assertFalse(isSharedPdfAnnotationDockSide(DockLocation.TOP))
        assertFalse(isSharedPdfAnnotationDockSide(DockLocation.BOTTOM))
        assertFalse(isSharedPdfAnnotationDockSide(DockLocation.FLOATING))
    }

    @Test
    fun `minimized stops ink drawing but text tool never draws ink`() {
        assertTrue(isSharedPdfAnnotationDrawingActive(PdfInkTool.PEN, isDockMinimized = false))
        assertFalse(isSharedPdfAnnotationDrawingActive(PdfInkTool.PEN, isDockMinimized = true))
        assertFalse(isSharedPdfAnnotationDrawingActive(PdfInkTool.NONE, isDockMinimized = false))
        assertFalse(isSharedPdfAnnotationDrawingActive(PdfInkTool.TEXT, isDockMinimized = false))
    }

    @Test
    fun `pen group recall matches android dock`() {
        assertEquals(
            PdfInkTool.FOUNTAIN_PEN,
            resolveSharedPdfAnnotationDockToolClick(
                selectedTool = PdfInkTool.HIGHLIGHTER,
                clickedGroup = PdfInkTool.PEN,
                lastPenTool = PdfInkTool.FOUNTAIN_PEN,
                lastHighlighterTool = PdfInkTool.HIGHLIGHTER,
            ),
        )
        // Tapping the active group keeps the current tool so the caller can
        // toggle the settings popup like Android's second-tap behavior.
        assertEquals(
            PdfInkTool.PENCIL,
            resolveSharedPdfAnnotationDockToolClick(
                selectedTool = PdfInkTool.PENCIL,
                clickedGroup = PdfInkTool.PEN,
                lastPenTool = PdfInkTool.PEN,
                lastHighlighterTool = PdfInkTool.HIGHLIGHTER,
            ),
        )
        assertEquals(
            PdfInkTool.TEXT,
            resolveSharedPdfAnnotationDockToolClick(
                selectedTool = PdfInkTool.PEN,
                clickedGroup = PdfInkTool.TEXT,
                lastPenTool = PdfInkTool.PEN,
                lastHighlighterTool = PdfInkTool.HIGHLIGHTER,
            ),
        )
    }

    @Test
    fun `popup opens opposite the dock half`() {
        // 1000px box, 56px dock: bottom-docked center (972) is bottom half.
        assertTrue(isSharedPdfAnnotationDockInBottomHalf(944f, 56f, 1000f))
        // Top-docked center (28) is top half.
        assertFalse(isSharedPdfAnnotationDockInBottomHalf(0f, 56f, 1000f))
        assertEquals(0f, sharedPdfAnnotationDockTopYPx(DockLocation.TOP, 400f, 1000f, 56f))
        assertEquals(944f, sharedPdfAnnotationDockTopYPx(DockLocation.BOTTOM, 400f, 1000f, 56f))
        assertEquals(400f, sharedPdfAnnotationDockTopYPx(DockLocation.FLOATING, 400f, 1000f, 56f))
    }

    @Test
    fun `side dock left x hugs the edge`() {
        assertEquals(0f, sharedPdfAnnotationDockLeftXPx(DockLocation.LEFT, 400f, 1000f, 56f))
        assertEquals(944f, sharedPdfAnnotationDockLeftXPx(DockLocation.RIGHT, 400f, 1000f, 56f))
        assertEquals(400f, sharedPdfAnnotationDockLeftXPx(DockLocation.FLOATING, 400f, 1000f, 56f))
        // Left-half detection drives popup placement for floating bars.
        assertTrue(isSharedPdfAnnotationDockInLeftHalf(0f, 56f, 1000f))
        assertFalse(isSharedPdfAnnotationDockInLeftHalf(944f, 56f, 1000f))
    }

    @Test
    fun `snap resolver prefers side edges over top and bottom`() {
        // 1000x1000 box: parked against the left/right edge snaps sideways
        // even when also near the top.
        assertEquals(
            DockLocation.LEFT,
            resolveSharedPdfDockSnapLocation(50f, 50f, 1000f, 1000f),
        )
        assertEquals(
            DockLocation.RIGHT,
            resolveSharedPdfDockSnapLocation(900f, 50f, 1000f, 1000f),
        )
        assertEquals(
            DockLocation.TOP,
            resolveSharedPdfDockSnapLocation(500f, 50f, 1000f, 1000f),
        )
        assertEquals(
            DockLocation.BOTTOM,
            resolveSharedPdfDockSnapLocation(500f, 900f, 1000f, 1000f),
        )
        assertEquals(
            null,
            resolveSharedPdfDockSnapLocation(500f, 500f, 1000f, 1000f),
        )
    }

    @Test
    fun `snap resolver docks where the dock touches most`() {
        // Wide horizontal bar (400x56) in the top-left corner touches the
        // top along 400px but the side along 56px -> TOP.
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
        assertEquals(
            null,
            resolveSharedPdfDockSnapLocation(
                dockOffsetX = 400f, dockOffsetY = 500f,
                boxWidthPx = 1000f, boxHeightPx = 1000f,
                dockWidthPx = 200f, dockHeightPx = 56f,
            ),
        )
    }
    @Test
    fun `popup max height matches android modal sizing`() {
        // Android ToolSettingsPopup call: fraction 0.8, margin 64, min 240.
        assertEquals(640, sharedPdfPopupMaxHeightDp(800))
        assertEquals(534, sharedPdfPopupMaxHeightDp(667))
        // Small screens fall back to usable height instead of the minimum.
        assertEquals(136, sharedPdfPopupMaxHeightDp(200))
    }
}
