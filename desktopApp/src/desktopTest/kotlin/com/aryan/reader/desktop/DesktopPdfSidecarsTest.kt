package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopPdfSidecarsTest {
    @Test
    fun `pdf sidecar keys avoid String hashCode collisions`() {
        val first = desktopPdfDocumentKey("C:/Books/Aa.pdf")
        val second = desktopPdfDocumentKey("C:/Books/BB.pdf")

        assertTrue("C:/Books/Aa.pdf".hashCode() == "C:/Books/BB.pdf".hashCode())
        assertNotEquals(first, second)
    }
}
