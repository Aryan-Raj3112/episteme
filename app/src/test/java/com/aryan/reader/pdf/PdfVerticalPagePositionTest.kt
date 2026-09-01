package com.aryan.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfVerticalPagePositionTest {

    @Test
    fun `full layout maps the camera to a page outside stale visible pages`() {
        val pages = (0..3).map { index ->
            PdfVerticalPagePosition(
                index = index,
                top = index * 110f,
                height = 100f,
            )
        }

        assertEquals(
            3,
            mostVisiblePdfVerticalPageIndex(
                pages = pages,
                panY = -330f,
                zoom = 1f,
                screenHeight = 100f,
            ),
        )
    }

    @Test
    fun `camera mapping converts the viewport through zoom`() {
        val pages = listOf(
            PdfVerticalPagePosition(index = 0, top = 0f, height = 100f),
            PdfVerticalPagePosition(index = 1, top = 110f, height = 100f),
            PdfVerticalPagePosition(index = 2, top = 220f, height = 100f),
        )

        assertEquals(
            1,
            mostVisiblePdfVerticalPageIndex(
                pages = pages,
                panY = -220f,
                zoom = 2f,
                screenHeight = 200f,
            ),
        )
    }
}
