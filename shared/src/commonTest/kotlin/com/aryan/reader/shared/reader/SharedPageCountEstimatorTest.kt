package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPageCountEstimatorTest {
    @Test
    fun `invalid viewport and empty content still produce one page`() {
        assertEquals(1, estimateSharedChapterPageCount(10_000, 0, 800, 16f, 22.4f))
        assertEquals(1, estimateSharedChapterPageCount(0, 400, 800, 16f, 22.4f))
    }

    @Test
    fun `more html and larger text increase the estimate`() {
        val short = estimateSharedChapterPageCount(2_000, 400, 800, 16f, 22.4f)
        val long = estimateSharedChapterPageCount(50_000, 400, 800, 16f, 22.4f)
        val largeText = estimateSharedChapterPageCount(50_000, 400, 800, 28f, 39.2f)

        assertTrue(long > short)
        assertTrue(largeText > long)
    }

    @Test
    fun `benchmark constants retain the existing estimate`() {
        assertEquals(
            27,
            estimateSharedChapterPageCount(
                htmlLength = 50_000,
                viewportWidthPx = 400,
                viewportHeightPx = 800,
                fontSizePx = 16f,
                lineHeightPx = 22.4f,
            ),
        )
    }
}
