package com.aryan.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfRichCursorFollowTest {

    private fun window(
        screenHeight: Float = 2000f,
        ime: Float = 800f,
        dock: Float = 144f,
        topSafe: Float = 240f,
        pad: Float = 36f,
    ) = PdfCursorFollowWindow(
        screenHeightPx = screenHeight,
        imeBottomPx = ime,
        dockReservePx = dock,
        topSafePx = topSafe,
        caretPaddingPx = pad,
    )

    @Test
    fun `visible cursor needs no shift`() {
        // Cursor at doc 500, zoom 1, pan 0 -> screen 500..540, window 276..1056.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 500f,
            cursorBottomDocPx = 540f,
            zoom = 1f,
            panY = 0f,
            window = window(),
        )
        assertEquals(0f, shift, 0.001f)
    }

    @Test
    fun `cursor under keyboard shifts above toolbar with padding`() {
        // Screen bottom 2000, keyboard 800, dock 144 -> visible bottom 1056.
        // Cursor at screen 1100..1140 -> shift to 1056-36-1140 = -120.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 1100f,
            cursorBottomDocPx = 1140f,
            zoom = 1f,
            panY = 0f,
            window = window(),
        )
        assertEquals(-120f, shift, 0.001f)
    }

    @Test
    fun `no dock reserve shifts only above keyboard`() {
        // Visible bottom 1200, cursor 1240..1280 -> 1200-36-1280 = -116.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 1240f,
            cursorBottomDocPx = 1280f,
            zoom = 1f,
            panY = 0f,
            window = window(dock = 0f),
        )
        assertEquals(-116f, shift, 0.001f)
    }

    @Test
    fun `cursor above top safe area shifts down`() {
        // Cursor 100..140, top safe 240 + pad 36 -> target 276-100 = +176.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 100f,
            cursorBottomDocPx = 140f,
            zoom = 1f,
            panY = 0f,
            window = window(),
        )
        assertEquals(176f, shift, 0.001f)
    }

    @Test
    fun `zoom scales doc coords before comparing`() {
        // Doc 500..520 at zoom 2, pan -100 -> screen 900..940, visible.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 500f,
            cursorBottomDocPx = 520f,
            zoom = 2f,
            panY = -100f,
            window = window(),
        )
        assertEquals(0f, shift, 0.001f)
    }

    @Test
    fun `existing pan is accounted for`() {
        // Doc 1500..1540, pan -500 -> screen 1000..1040, just inside 1020 limit.
        // 1040 > 1056-36=1020 -> shift 1020-1040 = -20.
        val shift = pdfCursorFollowShift(
            cursorTopDocPx = 1500f,
            cursorBottomDocPx = 1540f,
            zoom = 1f,
            panY = -500f,
            window = window(),
        )
        assertEquals(-20f, shift, 0.001f)
    }
}
