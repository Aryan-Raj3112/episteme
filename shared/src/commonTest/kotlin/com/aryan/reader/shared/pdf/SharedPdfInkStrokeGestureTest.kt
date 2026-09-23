package com.aryan.reader.shared.pdf

import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android parity for the PDF ink gesture (PdfVerticalReader
 * `globalDrawingModifier`): a stroke starts on the down and every pressed
 * position change is fed into it — Android never inspects whether an upstream
 * handler consumed the change, and skipping those changes is what made iOS
 * strokes stop mid-line.
 */
class SharedPdfInkStrokeGestureTest {

    @Test
    fun `pressed position changes are always drawn`() {
        assertTrue(sharedPdfInkStrokeConsumesMove(isPressed = true, positionChanged = true))
        assertFalse(sharedPdfInkStrokeConsumesMove(isPressed = true, positionChanged = false))
        assertFalse(sharedPdfInkStrokeConsumesMove(isPressed = false, positionChanged = true))
    }

    @Test
    fun `stylus only mode ignores finger downs but keeps the pencil`() {
        assertFalse(sharedPdfIsInkDownAllowed(isStylusOnlyMode = true, type = PointerType.Touch))
        assertTrue(sharedPdfIsInkDownAllowed(isStylusOnlyMode = true, type = PointerType.Stylus))
        assertTrue(sharedPdfIsInkDownAllowed(isStylusOnlyMode = false, type = PointerType.Touch))
    }

    @Test
    fun `eraser override follows the pointer type and the stylus shortcut`() {
        assertTrue(sharedPdfIsEraserOverride(PointerType.Eraser, stylusButtonPressed = false))
        assertTrue(sharedPdfIsEraserOverride(PointerType.Stylus, stylusButtonPressed = true))
        assertFalse(sharedPdfIsEraserOverride(PointerType.Stylus, stylusButtonPressed = false))
        // A finger never erases through the stylus shortcut.
        assertFalse(sharedPdfIsEraserOverride(PointerType.Touch, stylusButtonPressed = true))
    }

    @Test
    fun `a stroke stays owned by its page until it ends`() {
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = null, pageIndex = 4))
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 4, pageIndex = 4))
        // A second page can never steal the in-flight stroke's point list.
        assertEquals(4, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 4, pageIndex = 5))
    }
}
