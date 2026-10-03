package com.aryan.reader.shared.ui

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.ImagePageHeightFraction
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.imagePageHeightBudgetPx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedNativeImageStyleTest {

    private val density = Density(1f, 1f)

    /** A 200x900 portrait image whose CSS width equals its intrinsic width, so nothing but the
     *  height clamp can change the result. */
    private fun tallImage(): SemanticImage =
        image(intrinsicWidth = 200f, intrinsicHeight = 900f, styleWidth = 200.dp)

    private fun image(
        objectFit: String = "contain",
        filter: String? = null,
        intrinsicWidth: Float = 120f,
        intrinsicHeight: Float = 80f,
        styleWidth: Dp = 120.dp
    ): SemanticImage {
        return SemanticImage(
            path = "data:image/png;base64,iVBORw0KGgo=",
            altText = "sample",
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            style = CssStyle(
                blockStyle = BlockStyle(
                    width = styleWidth,
                    height = 80.dp,
                    objectFit = objectFit,
                    filter = filter
                )
            ),
            elementId = null,
            cfi = null,
            blockIndex = 0
        )
    }

    @Test
    fun `shared native image content scale mirrors android object fit mapping`() {
        assertEquals(ContentScale.Crop, image("cover").sharedNativeImageContentScale())
        assertEquals(ContentScale.FillBounds, image("fill").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("contain").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("scale-down").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("none").sharedNativeImageContentScale())
    }

    @Test
    fun `shared native image color matrix applies only for invert 100 percent filters`() {
        assertNull(image("contain", filter = null).sharedNativeImageColorMatrix())
        assertNull(image("contain", filter = "grayscale(100%)").sharedNativeImageColorMatrix())
        assertEquals(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ).toList(),
            image("contain", filter = "invert(100%)").sharedNativeImageColorMatrix()!!.toList()
        )
    }

    /**
     * Parity item B1. Android's `computeImageRenderSizePx` contain-fits a tall image: when the
     * width-fit height exceeds the available height it shrinks the *width* so the aspect ratio
     * survives. Shared returned `width * aspectRatio` with no clamp, so a portrait image overflowed
     * its box on iOS. `maxHeightPx` defaults to unbounded, which is what Android's signature does.
 */
    @Test
    fun `a tall image shrinks in width to fit the available height`() {
        val tall = tallImage()
        val size = { maxHeightPx: Float ->
            sharedNativeImageRenderSizePx(tall, density, maxWidthPx = 400f, imageScale = 1f, maxHeightPx = maxHeightPx)
        }

        // No bound: width-fit, overflowing. This is what shared used to always return.
        assertEquals(200f to 900f, size(Float.MAX_VALUE))

        // 450px tall: contain-fit halves the width and the aspect ratio is preserved.
        assertEquals(100f to 450f, size(450f))

        // Exactly the fitted height is left alone, so the clamp is not off by one.
        assertEquals(200f to 900f, size(900f))

        // A height budget *taller* than the image is also left alone.
        assertEquals(200f to 900f, size(1200f))
    }

    @Test
    fun `the height clamp shrinks width rather than clipping height`() {
        // A 10px height budget on a 200x900 (aspect 4.5) image asks for a 2.2px width. The clamp
        // must produce that pair, not a 200x10 box, so the aspect ratio survives.
        val size = sharedNativeImageRenderSizePx(
            tallImage(),
            density,
            maxWidthPx = 400f,
            imageScale = 1f,
            maxHeightPx = 10f
        )
        assertEquals(10f / 4.5f, size!!.first, 1e-4f)
        assertEquals(10f, size.second, 1e-4f)
    }

    @Test
    fun `the dp variant forwards the height budget in pixels`() {
        val size = { maxHeightPx: Float ->
            sharedNativeImageRenderSizeDp(
                tallImage(),
                density,
                maxWidth = 400.dp,
                imageScale = 1f,
                maxHeightPx = maxHeightPx
            )
        }

        // What Android's `boundedImageMaxHeightDp` falls back to when the box has no max height:
        // no bound at all. `tallImage` has a 200dp CSS width, so that is the base width.
        assertEquals(200.dp to 900.dp, size(Float.MAX_VALUE))
        assertEquals(100.dp to 450.dp, size(450f))
    }

    @Test
    fun `the page height budget is the same number the paginator measures with`() {
        // `imagePageHeightBudgetPx` is the single source for the measure and render bounds. If it
        // drifts, a tall image is measured into one box and rendered into another and overflows.
        assertEquals(602f, imagePageHeightBudgetPx(700))
        // Never collapses to zero, however short the page is.
        assertEquals(24f, imagePageHeightBudgetPx(1))
        assertEquals(24f, imagePageHeightBudgetPx(0))
        assertEquals(24f, imagePageHeightBudgetPx(-40))
    }

    @Test
    fun `the tall map from the parity bug now fits the page budget`() {
        // *The Path to Rome* illustration-20.jpg: 687x2246 at the reader's 370dp page width.
        // Before the budget existed this rendered 370x1209.6dp on a ~700dp page.
        val map = image(intrinsicWidth = 687f, intrinsicHeight = 2246f, styleWidth = Dp.Unspecified)
        val (width, height) = sharedNativeImageRenderSizePx(
            map,
            density,
            maxWidthPx = 370f,
            imageScale = 1f,
            maxHeightPx = imagePageHeightBudgetPx(700)
        )!!
        assertEquals(700f * ImagePageHeightFraction, height, 1e-3f)
        assertEquals(height / (2246f / 687f), width, 1e-3f)
        assertEquals(true, height < 700f)
    }
}
