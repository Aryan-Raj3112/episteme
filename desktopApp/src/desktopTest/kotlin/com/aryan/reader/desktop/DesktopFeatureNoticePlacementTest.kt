package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFeatureNoticePlacementTest {
    @Test
    fun `main notice renders only in the main window`() {
        val placement = desktopFeatureNoticePlacement(readerWindowId = null)

        assertTrue(placement.rendersInMainWindow())
        assertFalse(placement.rendersInReaderWindow("reader-1"))
    }

    @Test
    fun `reader notice renders only in the matching reader window`() {
        val placement = desktopFeatureNoticePlacement(readerWindowId = "reader-1")

        assertFalse(placement.rendersInMainWindow())
        assertTrue(placement.rendersInReaderWindow("reader-1"))
        assertFalse(placement.rendersInReaderWindow("reader-2"))
    }
}
