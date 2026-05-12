package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedMeasuredEpubPaginatorTest {

    @Test
    fun `two page geometry caps each page to rendered page width on wide viewports`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                margin = 48,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
            ),
            viewport = ReaderViewportSpec(widthPx = 2_400, heightPx = 1_200)
        )

        assertEquals(760, geometry.pageWidthPx)
        assertEquals(1_104, geometry.pageHeightPx)
    }

    @Test
    fun `geometry does not invent minimum page space beyond the rendered viewport`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 80,
                verticalMargin = 120
            ),
            viewport = ReaderViewportSpec(widthPx = 300, heightPx = 220)
        )

        assertEquals(140, geometry.pageWidthPx)
        assertEquals(1, geometry.pageHeightPx)
    }
}
