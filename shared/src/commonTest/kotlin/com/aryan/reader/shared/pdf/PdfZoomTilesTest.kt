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
        val tiles = planPdfZoomTiles(0.75f, 20f, PdfPageBounds(0f, 0f, 1f, 1f))
        assertTrue(tiles.all { it.fullWidthPx <= 6400 && it.fullHeightPx <= 6400 })
        assertTrue(tiles.all { it.widthPx <= 768 && it.heightPx <= 768 })
    }

    @Test
    fun verticalDoubleTapUsesAndroidCycle() {
        assertEquals(1f, pdfVerticalDoubleTapTargetScale(0.9f))
        assertEquals(2.5f, pdfVerticalDoubleTapTargetScale(1f))
        assertEquals(1f, pdfVerticalDoubleTapTargetScale(2.5f))
    }

    @Test
    fun zoomIndicatorRoundsLikeAndroid() {
        assertEquals(253, pdfZoomIndicatorPercent(2.526f))
    }
}
