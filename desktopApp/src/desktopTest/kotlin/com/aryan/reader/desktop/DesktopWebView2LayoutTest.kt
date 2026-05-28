package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWebView2LayoutTest {
    @Test
    fun `webview2 host bounds match the awt canvas logical size`() {
        val bounds = desktopWebView2TargetBoundsForCanvas(width = 1440, height = 900)

        assertEquals(DesktopWebView2TargetBounds(x = 0, y = 0, width = 1440, height = 900), bounds)
    }

    @Test
    fun `webview2 host bounds are unavailable before the canvas has size`() {
        assertNull(desktopWebView2TargetBoundsForCanvas(width = 0, height = 900))
        assertNull(desktopWebView2TargetBoundsForCanvas(width = 1440, height = 0))
    }

    @Test
    fun `webview2 awt canvas is not retired while host window is closing`() {
        assertFalse(
            desktopWebView2ShouldRetireAwtCanvas(
                hostWindowClosing = true,
                hostWindowDisplayable = true
            )
        )
        assertFalse(
            desktopWebView2ShouldRetireAwtCanvas(
                hostWindowClosing = false,
                hostWindowDisplayable = false
            )
        )
        assertTrue(
            desktopWebView2ShouldRetireAwtCanvas(
                hostWindowClosing = false,
                hostWindowDisplayable = true
            )
        )
    }
}
