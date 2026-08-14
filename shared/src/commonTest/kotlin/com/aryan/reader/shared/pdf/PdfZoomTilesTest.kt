package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfZoomTilesTest {
    @Test
    fun onlyPlansTilesIntersectingVisibleRegionAtHighZoom() {
        val tiles = planPdfZoomTiles(
            pageAspectRatio = 0.75f,
            zoomScale = 4f,
            visibleBounds = PdfPageBounds(0.4f, 0.4f, 0.6f, 0.6f)
        )
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size < 12)
        assertTrue(tiles.all { it.normalizedBounds.right > 0.4f && it.normalizedBounds.left < 0.6f })
    }

    @Test
    fun fitScaleDoesNotRequestHighResolutionTiles() {
        assertTrue(planPdfZoomTiles(0.75f, 1f, PdfPageBounds(0f, 0f, 1f, 1f)).isEmpty())
    }

    @Test
    fun renderDimensionsAreMemoryBounded() {
        val tiles = planPdfZoomTiles(0.75f, 10f, PdfPageBounds(0f, 0f, 1f, 1f))
        assertTrue(tiles.size <= PDF_ZOOM_TILE_MAX_VISIBLE_COUNT)
        assertTrue(tiles.all {
            it.fullWidthPx <= PDF_ZOOM_TILE_MAX_RENDERED_SIDE_PX &&
                it.fullHeightPx <= PDF_ZOOM_TILE_MAX_RENDERED_SIDE_PX
        })
        assertTrue(tiles.all { it.widthPx <= PDF_ZOOM_TILE_SIZE_PX && it.heightPx <= PDF_ZOOM_TILE_SIZE_PX })
        assertTrue(tiles.all { it.renderScale == PDF_MAX_ZOOM_SCALE })
    }

    @Test
    fun rasterQualityUsesStableBucketsAndClampsAtOneThousandPercent() {
        assertEquals(1f, pdfZoomRenderScale(1f))
        assertEquals(4f, pdfZoomRenderScale(3.1f))
        assertEquals(6f, pdfZoomRenderScale(4.01f))
        assertEquals(10f, pdfZoomRenderScale(9f))
        assertEquals(10f, pdfZoomRenderScale(100f))
    }

    @Test
    fun tileCacheIsByteBoundedAndUsesLeastRecentlyUsedEviction() {
        val cache = PdfTileLruCache<String>(maxBytes = 10L)
        cache.put("a", "A", 4L)
        cache.put("b", "B", 4L)
        assertEquals("A", cache.get("a"))

        cache.put("c", "C", 4L)

        assertEquals(null, cache.get("b"))
        assertEquals("A", cache.get("a"))
        assertEquals("C", cache.get("c"))
        assertTrue(cache.byteCount <= 10L)
    }

    @Test
    fun verticalDoubleTapUsesAndroidCycle() {
        assertEquals(2.5f, pdfVerticalDoubleTapTargetScale(0.9f))
        assertEquals(2.5f, pdfVerticalDoubleTapTargetScale(1f))
        assertEquals(1f, pdfVerticalDoubleTapTargetScale(2.5f))
        assertEquals(2.5f, pdfVerticalDoubleTapTargetScale(0.8f, fitScale = 0.8f))
        assertEquals(0.8f, pdfVerticalDoubleTapTargetScale(2.5f, fitScale = 0.8f))
    }

    @Test
    fun zoomIndicatorRoundsLikeAndroid() {
        assertEquals(253, pdfZoomIndicatorPercent(2.526f))
    }
}
