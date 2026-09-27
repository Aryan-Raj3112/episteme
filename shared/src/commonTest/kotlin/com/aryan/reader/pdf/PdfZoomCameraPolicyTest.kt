package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

    @Test
    fun `spread camera at zoom 1 maps the whole page as visible`() {
        val rect = pdfSpreadVisiblePageRect(
            cameraScale = 1f,
            cameraOffset = Offset.Zero,
            rowWidth = 1000f,
            rowHeight = 800f,
            pageLeftInRow = 100f,
            pageTopInRow = 0f,
            centeringOffsetX = 10f,
            centeringOffsetY = 20f,
        )
        // Viewport edges map to page-local coordinates: the full slot minus the
        // page's own offset and centering.
        assertEquals(-110f, rect.left)
        assertEquals(890f, rect.right)
        assertEquals(-20f, rect.top)
        assertEquals(780f, rect.bottom)
    }

    @Test
    fun `spread camera tiles follow the pan instead of the page center`() {
        // Row 1000x800, camera scale 2. graphicsLayer transform:
        // screen = pivot + (content - pivot) * s + translation, so the inverse
        // is content = (screen - translation - pivot) / s + pivot. Panning
        // right (positive translation) shifts content right, revealing the
        // page's LEFT side — the legacy per-page inversion (offset=Zero) would
        // always report the center band regardless.
        fun band(cameraOffsetX: Float): Rect = pdfSpreadVisiblePageRect(
            cameraScale = 2f,
            cameraOffset = Offset(cameraOffsetX, 0f),
            rowWidth = 1000f,
            rowHeight = 800f,
            pageLeftInRow = 100f,
            pageTopInRow = 0f,
            centeringOffsetX = 0f,
            centeringOffsetY = 0f,
        )
        val pannedRight = band(500f)
        // left: (0-500-500)/2+500-100 = -100; right: (1000-500-500)/2+500-100 = 400.
        assertEquals(-100f, pannedRight.left)
        assertEquals(400f, pannedRight.right)
        // Vertical band with no pan: (0-400)/2+400=200 .. (800-400)/2+400=600.
        assertEquals(200f, pannedRight.top)
        assertEquals(600f, pannedRight.bottom)
        // Mirrored pan reveals the right side; the two bands differ, proving
        // the pan reaches the tile region (center-only bug would not).
        val pannedLeft = band(-500f)
        assertEquals(400f, pannedLeft.left)
        assertEquals(900f, pannedLeft.right)
    }

    @Test
    fun `spread context overload matches raw overload`() {
        val camera = PdfSpreadCameraContext(
            scale = 3f,
            offset = Offset(-120f, 40f),
            rowWidth = 1200f,
            rowHeight = 900f,
            pageLeftInRow = 620f,
            pageTopInRow = 0f,
        )
        val viaContext = pdfSpreadVisiblePageRect(camera, centeringOffsetX = 5f, centeringOffsetY = 7f)
        val direct = pdfSpreadVisiblePageRect(
            cameraScale = camera.scale,
            cameraOffset = camera.offset,
            rowWidth = camera.rowWidth,
            rowHeight = camera.rowHeight,
            pageLeftInRow = camera.pageLeftInRow,
            pageTopInRow = camera.pageTopInRow,
            centeringOffsetX = 5f,
            centeringOffsetY = 7f,
        )
        assertEquals(direct.left, viaContext.left)
        assertEquals(direct.top, viaContext.top)
        assertEquals(direct.right, viaContext.right)
        assertEquals(direct.bottom, viaContext.bottom)
    }

    @Test
    fun `spread camera falls back to identity on non-finite scale`() {
        val rect = pdfSpreadVisiblePageRect(
            cameraScale = Float.NaN,
            cameraOffset = Offset(30f, 30f),
            rowWidth = 1000f,
            rowHeight = 800f,
            pageLeftInRow = 100f,
            pageTopInRow = 0f,
            centeringOffsetX = 0f,
            centeringOffsetY = 0f,
        )
        // Identity scale: full viewport band minus page offset and camera translation.
        assertEquals(-130f, rect.left)
        assertEquals(870f, rect.right)
    }
}
