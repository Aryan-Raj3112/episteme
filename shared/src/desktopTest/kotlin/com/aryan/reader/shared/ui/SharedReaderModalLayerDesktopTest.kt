package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReaderModalLayerDesktopTest {

    @Test
    fun `chrome layer remains visible when owner window is showing but not focused`() {
        assertTrue(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = false
            )
        )
    }

    @Test
    fun `chrome layer hides when owner window is unavailable`() {
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = false,
                ownerDisplayable = true,
                ownerMinimized = false
            )
        )
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = false,
                ownerMinimized = false
            )
        )
        assertFalse(
            sharedReaderModalChromeLayerVisible(
                ownerShowing = true,
                ownerDisplayable = true,
                ownerMinimized = true
            )
        )
    }
}
