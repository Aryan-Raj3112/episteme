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
