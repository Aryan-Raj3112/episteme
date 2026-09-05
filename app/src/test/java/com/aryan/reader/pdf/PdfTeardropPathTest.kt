package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfTeardropPathTest {

    @Test
    fun `teardrop path fills requested size`() {
        val size = Size(48f, 48f)
        val path = pdfTeardropPath(size)
        val bounds = path.getBounds()

        // Vector source is 960x960 with content spanning x 160..800, y 100..860.
        assertEquals(160f / 960f * 48f, bounds.left, 0.5f)
        assertEquals(800f / 960f * 48f, bounds.right, 0.5f)
        assertEquals(100f / 960f * 48f, bounds.top, 0.5f)
        assertEquals(860f / 960f * 48f, bounds.bottom, 0.5f)
        assertFalse(bounds.isEmpty)
    }

    @Test
    fun `teardrop path scales linearly for high zoom`() {
        val small = pdfTeardropPath(Size(24f, 24f)).getBounds()
        val large = pdfTeardropPath(Size(96f, 96f)).getBounds()

        assertEquals(small.width * 4f, large.width, 0.5f)
        assertEquals(small.height * 4f, large.height, 0.5f)
    }
}
