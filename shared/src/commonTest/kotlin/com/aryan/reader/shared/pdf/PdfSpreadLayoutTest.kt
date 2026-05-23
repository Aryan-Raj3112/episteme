package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfSpreadLayoutTest {

    @Test
    fun `single page mode keeps direct page indexes`() {
        val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.SINGLE)

        assertEquals(3, PdfSpreadLayout.normalizePageIndex(3, pageCount = 10, settings = settings))
        assertEquals(listOf(3), PdfSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals("4", PdfSpreadLayout.pageRangeLabel(3, pageCount = 10, settings = settings))
    }

    @Test
    fun `two page mode pairs pages from the first page by default`() {
        val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)

        assertEquals(2, PdfSpreadLayout.normalizePageIndex(3, pageCount = 10, settings = settings))
        assertEquals(listOf(2, 3), PdfSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals("3-4", PdfSpreadLayout.pageRangeLabel(3, pageCount = 10, settings = settings))
        assertEquals(listOf(4), PdfSpreadLayout.visiblePageIndices(4, pageCount = 5, settings = settings))
        assertEquals("5", PdfSpreadLayout.pageRangeLabel(4, pageCount = 5, settings = settings))
        assertEquals(listOf(0, 2, 4), PdfSpreadLayout.spreadStartPageIndices(pageCount = 5, settings = settings))
    }

    @Test
    fun `right to left pagination reverses only the displayed pdf spread order`() {
        val settings = ReaderSettings(
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
            rightToLeftPagination = true
        )

        assertEquals(listOf(2, 3), PdfSpreadLayout.visiblePageIndices(3, pageCount = 10, settings = settings))
        assertEquals(listOf(3, 2), PdfSpreadLayout.visiblePageIndicesForDisplay(3, pageCount = 10, settings = settings))
        assertEquals(4, PdfSpreadLayout.nextPageIndex(2, pageCount = 10, settings = settings))
        assertEquals(0, PdfSpreadLayout.previousPageIndex(2, pageCount = 10, settings = settings))
    }

    @Test
    fun `two page mode can keep the first page alone`() {
        val settings = ReaderSettings(
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
            pdfFirstPageStandaloneInSpread = true
        )

        assertEquals(listOf(0), PdfSpreadLayout.visiblePageIndices(0, pageCount = 6, settings = settings))
        assertEquals(listOf(1, 2), PdfSpreadLayout.visiblePageIndices(2, pageCount = 6, settings = settings))
        assertEquals(listOf(3, 4), PdfSpreadLayout.visiblePageIndices(4, pageCount = 6, settings = settings))
        assertEquals(listOf(5), PdfSpreadLayout.visiblePageIndices(5, pageCount = 6, settings = settings))
        assertEquals(listOf(0, 1, 3, 5), PdfSpreadLayout.spreadStartPageIndices(pageCount = 6, settings = settings))
    }

    @Test
    fun `two page navigation follows spread boundaries`() {
        val normal = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
        val firstStandalone = ReaderSettings(
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE,
            pdfFirstPageStandaloneInSpread = true
        )

        assertEquals(2, PdfSpreadLayout.nextPageIndex(0, pageCount = 5, settings = normal))
        assertEquals(4, PdfSpreadLayout.nextPageIndex(2, pageCount = 5, settings = normal))
        assertEquals(2, PdfSpreadLayout.previousPageIndex(4, pageCount = 5, settings = normal))
        assertFalse(PdfSpreadLayout.canGoNext(4, pageCount = 5, settings = normal))

        assertEquals(1, PdfSpreadLayout.nextPageIndex(0, pageCount = 6, settings = firstStandalone))
        assertEquals(3, PdfSpreadLayout.nextPageIndex(1, pageCount = 6, settings = firstStandalone))
        assertEquals(1, PdfSpreadLayout.previousPageIndex(3, pageCount = 6, settings = firstStandalone))
        assertTrue(PdfSpreadLayout.canGoPrevious(1, pageCount = 6, settings = firstStandalone))
    }

    @Test
    fun `spread progress uses the visible spread end`() {
        val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)

        assertEquals(80f, PdfSpreadLayout.progressPercent(2, pageCount = 5, settings = settings))
        assertEquals(100f, PdfSpreadLayout.progressPercent(4, pageCount = 5, settings = settings))
    }
}
