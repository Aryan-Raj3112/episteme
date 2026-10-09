package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfMagnifierGeometryTest {
    private val source = MagnifierContentSource(300, 600, 0f, 0f, 200f, 400f)

    @Test
    fun `sample and selection transforms preserve Android source scaling`() {
        val sample = requireNotNull(calculateMagnifierSampleGeometry(50f, 200f, source, 120f, 60f, 2f))
        assertEquals(MagnifierSampleGeometry(30, 278, 90, 45, 120f / 90f, 60f / 45f), sample)
        val mapped = mapContentBoundsToMagnifier(40f, 190f, 70f, 210f, source, sample)
        assertEquals(40f, mapped.left, 0.01f)
        assertEquals(100f, mapped.right, 0.01f)
        assertEquals(29.33f, mapped.center.y, 0.05f)
    }

    @Test
    fun `invalid geometry remains non renderable`() {
        assertEquals(null, calculateMagnifierSampleGeometry(0f, 0f, source.copy(sourceWidth = 0), 120f, 60f, 2f))
    }

    /**
     * A high-res tile is sampled in the tile's own pixels, not the base page's: the tile is denser
     * than the region it covers, so the same lens width takes fewer source pixels. Both hosts reach
     * this through `MagnifierTileSource.contentRect`, so the scale has to come from the content
     * rect's extent rather than the page's.
     */
    @Test
    fun `tile sample uses the tile local source scale`() {
        val tile = MagnifierContentSource(
            sourceWidth = 512,
            sourceHeight = 512,
            contentLeft = 100f,
            contentTop = 200f,
            contentWidth = 256f,
            contentHeight = 256f
        )

        val sample = requireNotNull(
            calculateMagnifierSampleGeometry(228f, 328f, tile, 120f, 60f, 2f)
        )

        assertEquals(196, sample.srcLeft)
        assertEquals(226, sample.srcTop)
        assertEquals(120, sample.srcWidth)
        assertEquals(60, sample.srcHeight)
    }
}
