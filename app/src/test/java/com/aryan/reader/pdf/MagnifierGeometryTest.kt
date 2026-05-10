package com.aryan.reader.pdf

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MagnifierGeometryTest {

    @Test
    fun `base bitmap sample maps displayed page coordinates into rendered source pixels`() {
        val source = MagnifierContentSource(
            sourceWidth = 300,
            sourceHeight = 600,
            contentLeft = 0f,
            contentTop = 0f,
            contentWidth = 200f,
            contentHeight = 400f
        )

        val sample = requireNotNull(
            calculateMagnifierSampleGeometry(
                centerContentX = 50f,
                centerContentY = 200f,
                contentSource = source,
                magnifierWidthPx = 120f,
                magnifierHeightPx = 60f,
                zoomFactor = 2f
            )
        )

        assertEquals(30, sample.srcLeft)
        assertEquals(278, sample.srcTop)
        assertEquals(90, sample.srcWidth)
        assertEquals(45, sample.srcHeight)
    }

    @Test
    fun `selection rect uses same rendered source transform as magnifier crop`() {
        val source = MagnifierContentSource(
            sourceWidth = 300,
            sourceHeight = 600,
            contentLeft = 0f,
            contentTop = 0f,
            contentWidth = 200f,
            contentHeight = 400f
        )
        val sample = requireNotNull(
            calculateMagnifierSampleGeometry(
                centerContentX = 50f,
                centerContentY = 200f,
                contentSource = source,
                magnifierWidthPx = 120f,
                magnifierHeightPx = 60f,
                zoomFactor = 2f
            )
        )

        val mapped = mapContentRectToMagnifier(
            contentRect = Rect(40, 190, 70, 210),
            contentSource = source,
            sample = sample
        )

        assertEquals(40f, mapped.left, 0.01f)
        assertEquals(100f, mapped.right, 0.01f)
        assertEquals(29.33f, mapped.centerY(), 0.05f)
    }

    @Test
    fun `tile sample uses tile local source scale`() {
        val source = MagnifierContentSource(
            sourceWidth = 512,
            sourceHeight = 512,
            contentLeft = 100f,
            contentTop = 200f,
            contentWidth = 256f,
            contentHeight = 256f
        )

        val sample = requireNotNull(
            calculateMagnifierSampleGeometry(
                centerContentX = 228f,
                centerContentY = 328f,
                contentSource = source,
                magnifierWidthPx = 120f,
                magnifierHeightPx = 60f,
                zoomFactor = 2f
            )
        )

        assertEquals(196, sample.srcLeft)
        assertEquals(226, sample.srcTop)
        assertEquals(120, sample.srcWidth)
        assertEquals(60, sample.srcHeight)
    }
}
