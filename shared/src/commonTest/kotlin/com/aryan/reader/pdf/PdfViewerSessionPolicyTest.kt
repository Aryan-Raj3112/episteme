package com.aryan.reader.pdf

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfViewerSessionPolicyTest {
    @Test
    fun `sidecars and virtual pages require the active ready book`() {
        assertTrue(canUsePdfSidecarsForBook("a", "a", true))
        assertFalse(canUsePdfSidecarsForBook("a", "b", true))
        assertFalse(canUsePdfSidecarsForBook("a", "a", false))
        assertTrue(canManagePdfVirtualPages(true, "a", "a", 1))
        assertFalse(canManagePdfVirtualPages(false, "a", "a", 1))
        assertFalse(canManagePdfVirtualPages(true, "a", "b", 1))
    }

    @Test
    fun `restore persistence and IME padding preserve Android timing`() {
        assertEquals(39, pdfPageToPersist(false, 0, 39))
        assertEquals(42, pdfPageToPersist(true, 42, 39))
        assertTrue(shouldApplyPdfTextDockImePadding(2400, 2400, 900))
        assertFalse(shouldApplyPdfTextDockImePadding(1500, 2400, 900))
        assertFalse(shouldApplyPdfTextDockImePadding(2400, 2400, 0))
    }

    @Test
    fun `eraser and touchpad policies preserve Android values and clamps`() {
        assertEquals(0.08f, resolveEraserStrokeWidth(true, 0.005f, 0.08f))
        assertEquals(0.005f, resolveEraserStrokeWidth(false, 0.005f, 0.08f))
        assertEquals(-148f, pdfTouchpadScrollTargetPanY(-100f, 1f, 48f, -1000f, 0f))
        assertEquals(-1000f, pdfTouchpadScrollTargetPanY(-980f, 1f, 48f, -1000f, 0f))
    }

    @Test
    fun `page change scale and range labels preserve Android mode behavior`() {
        val locked = Triple(2.25f, -12f, 32f)
        assertEquals(2.25f, sharedCurrentPageScaleAfterPdfPageChange(true, true, locked, 1f))
        assertEquals(1f, sharedCurrentPageScaleAfterPdfPageChange(false, true, locked, 2.25f))
        val spread = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
        assertEquals("1-2 / 10", sharedPdfPageRangeText(0, 10, true, spread))
        assertEquals("Pages 1-2 of 10", sharedPdfPageRangeLabel(0, 10, true, spread))
        assertEquals("Page 4 of 10", sharedPdfPageRangeLabel(3, 10, false, spread))
    }
}
