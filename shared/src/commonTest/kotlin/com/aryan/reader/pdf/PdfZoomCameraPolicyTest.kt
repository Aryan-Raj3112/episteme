package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfZoomCameraPolicyTest {
    @Test
    fun `spread camera and slot sizing preserve Android bounds`() {
        assertEquals(Offset(500f, -400f), clampPdfSpreadCameraOffset(2f, Offset(900f, -900f), 1000f, 800f))
        assertEquals(390f, pdfSpreadPageSlotWidth(800f, 1000f, 20f, 2, 0.75f))
        assertEquals(0f, pdfSpreadPageSlotWidth(0f, 1000f, 20f, 2, 0.75f))
    }

    @Test
    fun `locked camera gates preserve Android restoration behavior`() {
        val saved = Triple(2.25f, -12f, 32f)
        assertEquals(2.25f, initialPdfPageCamera(true, false, true, saved).first)
        assertFalse(shouldReportPdfPageCamera(true, false, true, saved, false))
        assertTrue(shouldReportPdfPageCamera(true, false, true, saved, true))
        assertFalse(shouldResetPdfZoomAfterBubbleZoomCleanup(false, 1.8f, false, true, true))
        assertTrue(shouldResetPdfZoomAfterBubbleZoomCleanup(false, 1.8f, false, true, false))
    }

    @Test
    fun `tile and zoom indicator policy preserves Android thresholds`() {
        assertTrue(shouldRenderPdfHighResTiles(0.82f, 1080, 1600, true, true))
        assertFalse(shouldRenderPdfHighResTiles(1f, 1080, 1600, true, true))
        assertTrue(shouldRenderPdfHighResTiles(1f, 3200, 1600, true, true))
        assertFalse(shouldRenderPdfHighResTiles(1.25f, 1080, 1600, false, false))
        assertEquals(83, pdfZoomIndicatorPercent(0.826f))
        assertFalse(shouldShowPdfZoomIndicator(100))
    }
}
