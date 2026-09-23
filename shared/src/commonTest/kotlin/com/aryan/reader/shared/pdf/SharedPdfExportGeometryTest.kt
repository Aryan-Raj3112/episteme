package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPdfExportGeometryTest {

    @Test
    fun `pdfium rotation codes map to clockwise degrees`() {
        assertEquals(0, sharedPdfRotationDegreesFromPdfiumCode(0))
        assertEquals(90, sharedPdfRotationDegreesFromPdfiumCode(1))
        assertEquals(180, sharedPdfRotationDegreesFromPdfiumCode(2))
        assertEquals(270, sharedPdfRotationDegreesFromPdfiumCode(3))
        assertEquals(0, sharedPdfRotationDegreesFromPdfiumCode(-1))
        assertEquals(0, sharedPdfRotationDegreesFromPdfiumCode(4))
    }

    @Test
    fun `unrotated point conversion matches legacy y-flip`() {
        val point = sharedPdfDisplayPointToMediabox(
            xNorm = 0.25f,
            yNorm = 0f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 0,
        )!!
        assertEquals(153f, point.first, 1e-3f)
        assertEquals(792f, point.second, 1e-3f)
    }

    @Test
    fun `rotated page sizes swap mediabox dimensions for 90 and 270`() {
        assertEquals(612f to 792f, sharedPdfMediaboxSize(612f, 792f, 0))
        assertEquals(612f to 792f, sharedPdfMediaboxSize(612f, 792f, 180))
        assertEquals(612f to 792f, sharedPdfMediaboxSize(792f, 612f, 90))
        assertEquals(612f to 792f, sharedPdfMediaboxSize(792f, 612f, 270))
    }

    @Test
    fun `rotate 180 maps display top-left to mediabox bottom-right`() {
        val point = sharedPdfDisplayPointToMediabox(
            xNorm = 0f,
            yNorm = 0f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 180,
        )!!
        assertEquals(612f, point.first, 1e-3f)
        assertEquals(0f, point.second, 1e-3f)
    }

    @Test
    fun `rotate 90 maps display top-left to mediabox bottom-left`() {
        // Display size is 792×612 for a 612×792 mediabox with /Rotate 90.
        val point = sharedPdfDisplayPointToMediabox(
            xNorm = 0f,
            yNorm = 0f,
            displayWidth = 792f,
            displayHeight = 612f,
            rotationDegrees = 90,
        )!!
        assertEquals(0f, point.first, 1e-3f)
        assertEquals(0f, point.second, 1e-3f)
    }

    @Test
    fun `unrotated raster matrix matches legacy positive-scale placement`() {
        val matrix = sharedPdfRasterImageMatrix(
            left = 0.1f,
            top = 0.08f,
            right = 0.9f,
            bottom = 0.92f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 0,
        )!!
        assertEquals(0.8 * 612.0, matrix.a, 1e-3)
        assertEquals(0.0, matrix.b, 1e-3)
        assertEquals(0.0, matrix.c, 1e-3)
        assertEquals(0.84 * 792.0, matrix.d, 1e-3)
        assertEquals(0.1 * 612.0, matrix.e, 1e-3)
        assertEquals((1.0 - 0.92) * 792.0, matrix.f, 1e-3)
    }

    @Test
    fun `rotate 180 raster matrix flips axes so content stays upright after page rotation`() {
        val matrix = sharedPdfRasterImageMatrix(
            left = 0.1f,
            top = 0.08f,
            right = 0.9f,
            bottom = 0.92f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 180,
        )!!
        assertTrue(matrix.a < 0.0 && matrix.d < 0.0, "expected 180° pre-rotation, got $matrix")
        // Image top (0,1) in mediabox is the inverse of display top-left; the viewer's
        // /Rotate 180 maps it back to display (left, top).
        val imageTopX = matrix.e + matrix.c
        val imageTopY = matrix.f + matrix.d
        val expectedTop = sharedPdfDisplayPointToMediabox(
            xNorm = 0.1f,
            yNorm = 0.08f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 180,
        )!!
        assertEquals(expectedTop.first.toDouble(), imageTopX, 1e-3)
        assertEquals(expectedTop.second.toDouble(), imageTopY, 1e-3)
        // Image (1,0) is the inverse of display bottom-right.
        val imageRightX = matrix.e + matrix.a
        val imageRightY = matrix.f + matrix.b
        val expectedBottomRight = sharedPdfDisplayPointToMediabox(
            xNorm = 0.9f,
            yNorm = 0.92f,
            displayWidth = 612f,
            displayHeight = 792f,
            rotationDegrees = 180,
        )!!
        assertEquals(expectedBottomRight.first.toDouble(), imageRightX, 1e-3)
        assertEquals(expectedBottomRight.second.toDouble(), imageRightY, 1e-3)
    }

    @Test
    fun `display rect maps to a non-empty mediabox rect for every rotation`() {
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val (mediaboxWidth, mediaboxHeight) = sharedPdfMediaboxSize(792f, 612f, rotation)
            val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                mediaboxHeight to mediaboxWidth
            } else {
                mediaboxWidth to mediaboxHeight
            }
            val rect = sharedPdfDisplayRectToMediabox(
                left = 0.15f,
                top = 0.2f,
                right = 0.7f,
                bottom = 0.6f,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                rotationDegrees = rotation,
            )
            assertNotNull(rect, "rotation=$rotation")
            assertTrue(
                rect.right > rect.left && rect.top > rect.bottom,
                "rotation=$rotation rect=$rect",
            )
            assertTrue(
                rect.left >= -1e-3f && rect.right <= mediaboxWidth + 1e-3f,
                "rotation=$rotation rect=$rect mediaboxWidth=$mediaboxWidth",
            )
            assertTrue(
                rect.bottom >= -1e-3f && rect.top <= mediaboxHeight + 1e-3f,
                "rotation=$rotation rect=$rect mediaboxHeight=$mediaboxHeight",
            )
        }
    }

    @Test
    fun `invalid sizes and rotations return null`() {
        assertNull(
            sharedPdfRasterImageMatrix(0.1f, 0.1f, 0.9f, 0.9f, 0f, 792f, 0),
        )
        assertNull(
            sharedPdfDisplayPointToMediabox(0.5f, 0.5f, 612f, 792f, 45),
        )
        assertNull(
            sharedPdfDisplayRectToMediabox(0.5f, 0.5f, 0.5f, 0.6f, 612f, 792f, 0),
        )
    }
}
