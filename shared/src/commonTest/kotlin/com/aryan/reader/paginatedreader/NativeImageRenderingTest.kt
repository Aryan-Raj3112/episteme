package com.aryan.reader.paginatedreader

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the image render decisions both native stacks share (B1 step 3, image
 * block kind). All three were byte-identical copies; the tests lock the values
 * so a future edit cannot silently change dark-mode images, placement or
 * scaling on one stack.
 */
class NativeImageRenderingTest {
    @Test
    fun `invert matrix is the exact dark-mode matrix`() {
        val matrix = nativeInvertColorMatrix()
        assertEquals(20, matrix.size)
        assertTrue(
            matrix.contentEquals(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    @Test
    fun `invert matrix returns a fresh copy`() {
        assertTrue(nativeInvertColorMatrix() !== nativeInvertColorMatrix())
    }

    @Test
    fun `alignment follows float then horizontal align`() {
        assertEquals(
            Alignment.CenterEnd,
            nativeImageContentAlignment(BlockStyle(float = "right")),
        )
        assertEquals(
            Alignment.CenterEnd,
            nativeImageContentAlignment(BlockStyle(horizontalAlign = "end")),
        )
        assertEquals(
            Alignment.CenterStart,
            nativeImageContentAlignment(BlockStyle(float = "left")),
        )
        assertEquals(
            Alignment.CenterStart,
            nativeImageContentAlignment(BlockStyle(horizontalAlign = "start")),
        )
        assertEquals(Alignment.Center, nativeImageContentAlignment(BlockStyle()))
    }

    @Test
    fun `scale follows object fit`() {
        assertEquals(ContentScale.Crop, nativeImageContentScale(BlockStyle(objectFit = "cover")))
        assertEquals(ContentScale.FillBounds, nativeImageContentScale(BlockStyle(objectFit = "fill")))
        assertEquals(ContentScale.Fit, nativeImageContentScale(BlockStyle(objectFit = "contain")))
        assertEquals(ContentScale.Fit, nativeImageContentScale(BlockStyle(objectFit = "scale-down")))
        assertEquals(ContentScale.Fit, nativeImageContentScale(BlockStyle()))
    }
}
