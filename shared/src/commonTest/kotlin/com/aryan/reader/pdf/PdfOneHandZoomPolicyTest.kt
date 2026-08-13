package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfOneHandZoomPolicyTest {
    @Test
    fun `second tap classification preserves Android timing and movement precedence`() {
        assertEquals(PdfSecondTapZoomAction.QUICK_DOUBLE_TAP, classifyPdfSecondTapZoomAction(40, 30f, 8f))
        assertEquals(PdfSecondTapZoomAction.ONE_HAND_ZOOM, classifyPdfSecondTapZoomAction(100, 8f, 8f))
        assertEquals(PdfSecondTapZoomAction.HELD_NO_MOVEMENT, classifyPdfSecondTapZoomAction(130, 1f, 8f))
    }

    @Test
    fun `scale and camera math preserve Android pivot behavior`() {
        assertEquals(2f, pdfOneHandZoomScale(1f, 240f, 240f, 1f, 4f))
        assertEquals(1f, pdfOneHandZoomScale(2f, -240f, 240f, 1f, 4f))
        val viewport = Size(1000f, 1000f)
        assertEquals(
            Offset(200f, 100f),
            centeredPdfCameraOffsetForScaleChange(1f, 2f, Offset.Zero, Offset(300f, 400f), viewport, viewport),
        )
        assertEquals(Offset(-300f, -400f), topLeftPdfPanForScaleChange(1f, 2f, Offset.Zero, Offset(300f, 400f)))
    }
}
