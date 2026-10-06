package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginatorImageSizingTest {
    @Test
    fun `unsized images use intrinsic CSS width and remain within the column`() {
        val density = Density(2f)

        assertEquals(120f, intrinsicImageWidthPx(60f, density, maxWidthPx = 800f))
        assertEquals(800f, intrinsicImageWidthPx(600f, density, maxWidthPx = 800f))
    }

    @Test
    fun `tall images shrink to fit the available height preserving aspect`() {
        val density = Density(2f)
        val portrait = ImageBlock(
            path = "images/portrait.jpg",
            altText = null,
            intrinsicWidth = 400f,
            intrinsicHeight = 1200f,
            blockIndex = 0
        )

        val (widthPx, heightPx) = computeImageRenderSizePx(
            block = portrait,
            density = density,
            maxWidthPx = 800f,
            imageSizeMultiplier = 1f,
            maxHeightPx = 500f
        )

        assertEquals(500f, heightPx, 0.01f)
        assertEquals(500f / 3f, widthPx, 0.01f)
    }

    @Test
    fun `normal images are unaffected by a generous height bound`() {
        val density = Density(2f)
        val landscape = ImageBlock(
            path = "images/landscape.jpg",
            altText = null,
            intrinsicWidth = 800f,
            intrinsicHeight = 400f,
            blockIndex = 0
        )

        val (widthPx, heightPx) = computeImageRenderSizePx(
            block = landscape,
            density = density,
            maxWidthPx = 800f,
            imageSizeMultiplier = 1f,
            maxHeightPx = 500f
        )

        assertEquals(800f, widthPx, 0.01f)
        assertEquals(400f, heightPx, 0.01f)
    }

    /**
     * Parity item B1. Android is the benchmark for the image base width, and shared now calls
     * `intrinsicImageWidthPx` directly instead of using the page width. This pins the Android
     * contract that shared is matching: an image with no CSS `width` takes the `width` **attribute**
     * read as dp, capped at the available width. A small inline logo therefore stays small here —
     * which is exactly what it did not do on iOS.
     */
    @Test
    fun `an image narrower than the column keeps its intrinsic width not the column width`() {
        val density = Density(2f)
        val ornament = ImageBlock(
            path = "images/ornament.png",
            altText = null,
            intrinsicWidth = 60f,
            intrinsicHeight = 20f,
            blockIndex = 0
        )

        val (widthPx, heightPx) = computeImageRenderSizePx(
            block = ornament,
            density = density,
            maxWidthPx = 800f,
            imageSizeMultiplier = 1f
        )

        // 60 units read as 60dp -> 120px at 2x, not the 800px column.
        assertEquals(120f, widthPx, 0.01f)
        assertEquals(40f, heightPx, 0.01f)
    }

    /**
     * The cap is what keeps wide images full-column, so the two cases together are the whole rule:
     * narrower than the column keeps its intrinsic size, wider saturates.
     */
    @Test
    fun `an image wider than the column saturates to the column width`() {
        val density = Density(2f)
        val wide = ImageBlock(
            path = "images/wide.jpg",
            altText = null,
            intrinsicWidth = 2000f,
            intrinsicHeight = 1000f,
            blockIndex = 0
        )

        val (widthPx, _) = computeImageRenderSizePx(
            block = wide,
            density = density,
            maxWidthPx = 800f,
            imageSizeMultiplier = 1f
        )

        assertEquals(800f, widthPx, 0.01f)
    }

    @Test
    fun `default max height preserves legacy width-only sizing`() {
        val density = Density(2f)
        val portrait = ImageBlock(
            path = "images/portrait.jpg",
            altText = null,
            intrinsicWidth = 400f,
            intrinsicHeight = 1200f,
            blockIndex = 0
        )

        val (widthPx, heightPx) = computeImageRenderSizePx(
            block = portrait,
            density = density,
            maxWidthPx = 800f,
            imageSizeMultiplier = 1f
        )

        assertEquals(800f, widthPx, 0.01f)
        assertEquals(2400f, heightPx, 0.01f)
    }
}
