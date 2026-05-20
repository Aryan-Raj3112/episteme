package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfOneHandZoomTest {

    @Test
    fun `downward one hand drag zooms in`() {
        val scale = pdfOneHandZoomScale(
            startScale = 1f,
            totalDragY = 240f,
            dragDistanceForDoublePx = 240f,
            minScale = 1f,
            maxScale = 4f
        )

        assertEquals(2f, scale, 0.0001f)
    }

    @Test
    fun `upward one hand drag zooms out`() {
        val scale = pdfOneHandZoomScale(
            startScale = 2f,
            totalDragY = -240f,
            dragDistanceForDoublePx = 240f,
            minScale = 1f,
            maxScale = 4f
        )

        assertEquals(1f, scale, 0.0001f)
    }

    @Test
    fun `one hand zoom scale clamps to caller bounds`() {
        val zoomedIn = pdfOneHandZoomScale(
            startScale = 3.5f,
            totalDragY = 240f,
            dragDistanceForDoublePx = 240f,
            minScale = 1f,
            maxScale = 4f
        )
        val zoomedOut = pdfOneHandZoomScale(
            startScale = 1.2f,
            totalDragY = -480f,
            dragDistanceForDoublePx = 240f,
            minScale = 1f,
            maxScale = 4f
        )

        assertEquals(4f, zoomedIn, 0.0001f)
        assertEquals(1f, zoomedOut, 0.0001f)
    }

    @Test
    fun `centered camera zoom preserves pivot content point`() {
        val viewport = Size(1000f, 1000f)
        val content = Size(1000f, 1000f)
        val pivot = Offset(300f, 400f)
        val nextOffset = centeredPdfCameraOffsetForScaleChange(
            previousScale = 1f,
            nextScale = 2f,
            previousOffset = Offset.Zero,
            pivot = pivot,
            viewportSize = viewport,
            contentSize = content
        )

        val before = contentPointForCenteredCamera(
            screenPoint = pivot,
            scale = 1f,
            offset = Offset.Zero,
            viewportSize = viewport
        )
        val after = contentPointForCenteredCamera(
            screenPoint = pivot,
            scale = 2f,
            offset = nextOffset,
            viewportSize = viewport
        )

        assertEquals(before.x, after.x, 0.0001f)
        assertEquals(before.y, after.y, 0.0001f)
    }

    @Test
    fun `center pivot zoom keeps camera centered`() {
        val viewport = Size(1000f, 1000f)
        val offset = centeredPdfCameraOffsetForScaleChange(
            previousScale = 1f,
            nextScale = 2f,
            previousOffset = Offset.Zero,
            pivot = Offset(500f, 500f),
            viewportSize = viewport,
            contentSize = viewport
        )

        assertEquals(0f, offset.x, 0.0001f)
        assertEquals(0f, offset.y, 0.0001f)
    }

    @Test
    fun `held second tap without movement is not a zoom action`() {
        val action = classifyPdfSecondTapZoomAction(
            pressDurationMillis = PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS + 40L,
            totalDragY = 1f,
            movementSlopPx = 8f
        )

        assertEquals(PdfSecondTapZoomAction.HELD_NO_MOVEMENT, action)
    }

    private fun contentPointForCenteredCamera(
        screenPoint: Offset,
        scale: Float,
        offset: Offset,
        viewportSize: Size
    ): Offset {
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        return ((screenPoint - offset - center) / scale) + center
    }
}
