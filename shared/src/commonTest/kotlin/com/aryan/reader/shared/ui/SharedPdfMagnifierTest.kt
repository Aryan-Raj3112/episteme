package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPdfMagnifierTest {

    private val fullPageSource = SharedPdfMagnifierContentSource(
        sourceWidth = 2000,
        sourceHeight = 3000,
        contentLeft = 0f,
        contentTop = 0f,
        contentWidth = 2000f,
        contentHeight = 3000f
    )

    @Test
    fun `sample geometry centers the source rect on the target`() {
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 1000f,
            centerContentY = 1500f,
            contentSource = fullPageSource,
            magnifierWidthPx = 240f,
            magnifierHeightPx = 120f,
            zoomFactor = 2f
        )!!
        assertEquals(1000, sample.srcLeft + sample.srcWidth / 2)
        assertEquals(1500, sample.srcTop + sample.srcHeight / 2)
        assertEquals(120, sample.srcWidth)
        assertEquals(60, sample.srcHeight)
        assertEquals(2f, sample.outputScaleX)
        assertEquals(2f, sample.outputScaleY)
    }

    @Test
    fun `sample geometry clamps at the top-left edge`() {
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 0f,
            centerContentY = 0f,
            contentSource = fullPageSource,
            magnifierWidthPx = 240f,
            magnifierHeightPx = 120f,
            zoomFactor = 2f
        )!!
        assertEquals(0, sample.srcLeft)
        assertEquals(0, sample.srcTop)
        assertTrue(sample.srcWidth in 1..120)
        assertTrue(sample.srcHeight in 1..60)
    }

    @Test
    fun `sample geometry clamps at the bottom-right edge`() {
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 2000f,
            centerContentY = 3000f,
            contentSource = fullPageSource,
            magnifierWidthPx = 240f,
            magnifierHeightPx = 120f,
            zoomFactor = 2f
        )!!
        assertEquals(2000, sample.srcLeft + sample.srcWidth)
        assertEquals(3000, sample.srcTop + sample.srcHeight)
    }

    @Test
    fun `sample geometry rejects degenerate inputs`() {
        assertNull(
            calculateSharedPdfMagnifierSampleGeometry(
                centerContentX = 10f,
                centerContentY = 10f,
                contentSource = fullPageSource,
                magnifierWidthPx = 0f,
                magnifierHeightPx = 120f,
                zoomFactor = 2f
            )
        )
        assertNull(
            calculateSharedPdfMagnifierSampleGeometry(
                centerContentX = 10f,
                centerContentY = 10f,
                contentSource = SharedPdfMagnifierContentSource(0, 0, 0f, 0f, 0f, 0f),
                magnifierWidthPx = 240f,
                magnifierHeightPx = 120f,
                zoomFactor = 2f
            )
        )
        assertNull(
            calculateSharedPdfMagnifierSampleGeometry(
                centerContentX = 10f,
                centerContentY = 10f,
                contentSource = fullPageSource,
                magnifierWidthPx = 240f,
                magnifierHeightPx = 120f,
                zoomFactor = 0f
            )
        )
    }

    @Test
    fun `tile source scales the source rect to tile pixels`() {
        val tile = SharedPdfMagnifierContentSource(
            sourceWidth = 500,
            sourceHeight = 750,
            contentLeft = 500f,
            contentTop = 750f,
            contentWidth = 500f,
            contentHeight = 750f
        )
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 750f,
            centerContentY = 1125f,
            contentSource = tile,
            magnifierWidthPx = 240f,
            magnifierHeightPx = 120f,
            zoomFactor = 2f
        )!!
        assertEquals(190, sample.srcLeft)
        assertEquals(345, sample.srcTop)
    }

    @Test
    fun `content rect maps into magnifier space`() {
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 1000f,
            centerContentY = 1500f,
            contentSource = fullPageSource,
            magnifierWidthPx = 240f,
            magnifierHeightPx = 120f,
            zoomFactor = 2f
        )!!
        val mapped = mapSharedPdfContentRectToMagnifier(
            contentRect = Rect(950f, 1480f, 1050f, 1520f),
            contentSource = fullPageSource,
            sample = sample
        )
        assertEquals(20f, mapped.left)
        assertEquals(20f, mapped.top)
        assertEquals(220f, mapped.right)
        assertEquals(100f, mapped.bottom)
    }

    @Test
    fun `tile lookup picks the tile containing the center`() {
        val requests = listOf(
            tile(0, 0, 0, 100, 100),
            tile(1, 100, 0, 100, 100),
            tile(2, 0, 100, 100, 100),
            tile(3, 100, 100, 100, 100),
        )
        val found = sharedPdfMagnifierTileRequest(
            requests, Offset(150f, 150f), currentScale = 2f,
            contentWidthPx = 200, contentHeightPx = 200
        )!!
        assertEquals(3, found.id)
    }

    @Test
    fun `tile lookup converts the center from content space to tile space`() {
        val requests = listOf(
            tile(0, 0, 0, 100, 100),
            tile(1, 100, 0, 100, 100),
            tile(2, 0, 100, 100, 100),
            tile(3, 100, 100, 100, 100),
        )
        val found = sharedPdfMagnifierTileRequest(
            requests, Offset(75f, 75f), currentScale = 2f,
            contentWidthPx = 100, contentHeightPx = 100
        )!!
        assertEquals(3, found.id)
    }

    @Test
    fun `tile lookup is skipped at base scale and returns null for gaps`() {
        val requests = listOf(tile(0, 0, 0, 100, 100))
        assertNull(
            sharedPdfMagnifierTileRequest(
                requests, Offset(50f, 50f), currentScale = 1f,
                contentWidthPx = 200, contentHeightPx = 200
            )
        )
        assertNull(
            sharedPdfMagnifierTileRequest(
                requests, Offset(150f, 150f), currentScale = 2f,
                contentWidthPx = 200, contentHeightPx = 200
            )
        )
    }

    @Test
    fun `tile center maps into tile-local pixels`() {
        val tile = SharedPdfMagnifierContentSource(
            sourceWidth = 200,
            sourceHeight = 200,
            contentLeft = 100f,
            contentTop = 100f,
            contentWidth = 100f,
            contentHeight = 100f
        )
        assertEquals(50f, tile.sourceX(125f), 0.001f)
        assertEquals(50f, tile.sourceY(125f), 0.001f)
        assertEquals(200f, tile.sourceX(200f), 0.001f)
    }

    @Test
    fun `tile rect converts from tile space into content space`() {
        val rect = sharedPdfMagnifierTileRectInContentSpace(
            request = tile(
                id = 0, leftPx = 1200, topPx = 900, widthPx = 768, heightPx = 768,
                fullWidthPx = 4800, fullHeightPx = 3200
            ),
            contentWidthPx = 1200,
            contentHeightPx = 1600
        )
        assertEquals(300f, rect.left)
        assertEquals(450f, rect.top)
        assertEquals(492f, rect.right)
        assertEquals(834f, rect.bottom)
    }

    @Test
    fun `sample geometry scales the source rect to the display fit`() {
        val display = SharedPdfMagnifierContentSource(
            sourceWidth = 1152,
            sourceHeight = 1600,
            contentLeft = 0f,
            contentTop = 0f,
            contentWidth = 1200f,
            contentHeight = 1600f
        )
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 600f,
            centerContentY = 800f,
            contentSource = display,
            magnifierWidthPx = 360f,
            magnifierHeightPx = 180f,
            zoomFactor = 2f
        )!!
        assertEquals(173, sample.srcWidth)
        assertEquals(90, sample.srcHeight)
    }

    @Test
    fun `sample geometry compensates the denser tile resolution`() {
        val tileSource = SharedPdfMagnifierContentSource(
            sourceWidth = 768,
            sourceHeight = 768,
            contentLeft = 300f,
            contentTop = 450f,
            contentWidth = 192f,
            contentHeight = 192f
        )
        val sample = calculateSharedPdfMagnifierSampleGeometry(
            centerContentX = 600f,
            centerContentY = 833f,
            contentSource = tileSource,
            magnifierWidthPx = 360f,
            magnifierHeightPx = 180f,
            zoomFactor = 2f
        )!!
        assertEquals(720, sample.srcWidth)
        assertEquals(360, sample.srcHeight)
    }

    private fun tile(
        id: Int,
        leftPx: Int,
        topPx: Int,
        widthPx: Int,
        heightPx: Int,
        fullWidthPx: Int = 200,
        fullHeightPx: Int = 200
    ) = com.aryan.reader.shared.pdf.PdfZoomTileRequest(
        id = id,
        column = 0,
        row = 0,
        leftPx = leftPx,
        topPx = topPx,
        widthPx = widthPx,
        heightPx = heightPx,
        fullWidthPx = fullWidthPx,
        fullHeightPx = fullHeightPx,
        renderScale = 2f
    )
}
