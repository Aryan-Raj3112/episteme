package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfPaginationDirectionTest {
    @Test
    fun `left and right edges follow reading direction`() {
        assertEquals(1, pdfPaginationEdgeTarget(2, 4, tappedLeftEdge = true, rightToLeft = false))
        assertEquals(3, pdfPaginationEdgeTarget(2, 4, tappedLeftEdge = false, rightToLeft = false))
        assertEquals(3, pdfPaginationEdgeTarget(2, 4, tappedLeftEdge = true, rightToLeft = true))
        assertEquals(1, pdfPaginationEdgeTarget(2, 4, tappedLeftEdge = false, rightToLeft = true))
    }

    @Test
    fun `edge navigation does not wrap`() {
        assertNull(pdfPaginationEdgeTarget(0, 4, tappedLeftEdge = true, rightToLeft = false))
        assertNull(pdfPaginationEdgeTarget(4, 4, tappedLeftEdge = true, rightToLeft = true))
    }

    @Test
    fun `pdf defaults seed a new session and preserve persisted reading data`() {
        val defaults = ReaderSettings(themeId = "sepia")
        val fresh = initialSharedPdfReaderState(null, defaults, initialPageIndex = 3)
        assertEquals("sepia", fresh.themeId)
        assertEquals(PdfDisplayMode.VERTICAL_SCROLL, fresh.displayMode)

        val bookmark = SharedPdfBookmark(pageIndex = 0, createdAt = 1L)
        val restored = initialSharedPdfReaderState(
            persistedState = SharedPdfReaderState(
                pageIndex = 0,
                pageCount = 4,
                themeId = "no_theme",
                bookmarks = listOf(bookmark),
            ),
            defaults = defaults,
            initialPageIndex = 0,
        )
        assertEquals("sepia", restored.themeId)
        assertEquals(listOf(bookmark), restored.bookmarks)
    }
}
