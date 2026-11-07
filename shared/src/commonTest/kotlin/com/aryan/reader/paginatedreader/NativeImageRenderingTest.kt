package com.aryan.reader.paginatedreader

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `math em and ex resolve against the font like Android`() {
        val density = Density(2f)
        // 2em at 16sp = 32sp = 64px physical = 32dp back in intrinsic space.
        assertEquals(32f, nativeMathSvgDimensionDp("2em", 16f, density))
        // 1ex at 20sp = 0.5 * 20sp = 10sp = 20px physical = 10dp.
        assertEquals(10f, nativeMathSvgDimensionDp("1ex", 20f, density))
        assertEquals(32f, nativeMathSvgDimensionDp(" 2EM ", 16f, density))
    }

    @Test
    fun `math percent and unknown units stay unresolved`() {
        val density = Density(2f)
        // Percent needs a container width the call site has not measured.
        assertNull(nativeMathSvgDimensionDp("25%", 16f, density))
        // px/pt/unitless stay with the dp-space parser, never reach here.
        assertNull(nativeMathSvgDimensionDp("100px", 16f, density))
        assertNull(nativeMathSvgDimensionDp("10pt", 16f, density))
        assertNull(nativeMathSvgDimensionDp("100", 16f, density))
        assertNull(nativeMathSvgDimensionDp("invalid", 16f, density))
        assertNull(nativeMathSvgDimensionDp(null, 16f, density))
        assertNull(nativeMathSvgDimensionDp("0em", 16f, density))
    }
}
