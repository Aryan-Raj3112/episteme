package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
