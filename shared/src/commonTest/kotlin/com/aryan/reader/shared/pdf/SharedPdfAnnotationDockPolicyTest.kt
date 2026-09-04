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
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.TOP, isDragging = true))
        assertFalse(isSharedPdfAnnotationDockSticky(DockLocation.FLOATING, isDragging = false))
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
}
