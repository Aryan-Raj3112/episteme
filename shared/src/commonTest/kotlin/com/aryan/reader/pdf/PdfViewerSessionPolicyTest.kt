package com.aryan.reader.pdf

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        assertEquals("1-2/10", sharedPdfPageRangeText(0, 10, true, spread))
        assertEquals("1-2/10", sharedPdfPageRangeLabel(0, 10, true, spread))
        assertEquals("4/10", sharedPdfPageRangeLabel(3, 10, false, spread))
    }

    @Test
    fun `document open plan keeps sidecar state for the already loaded book`() {
        // Split panes and password unlocks re-open the SAME book: sidecar state
        // must survive or committed ink strokes vanish and never persist.
        val sameBook = sharedPdfDocumentOpenBookPlan(
            currentBookId = "book-a",
            fastId = "file_100",
            selectedBookId = "book-a",
        )
        assertEquals("book-a", sameBook.bookId)
        assertEquals("book-a", sameBook.migrationTargetBookId)
        assertTrue(sameBook.shouldMigrateLegacyBookId)
        assertFalse(sameBook.shouldResetSidecarState)

        val newBook = sharedPdfDocumentOpenBookPlan(
            currentBookId = "book-a",
            fastId = "file_100",
            selectedBookId = "book-b",
        )
        assertEquals("book-b", newBook.bookId)
        assertEquals("book-b", newBook.migrationTargetBookId)
        assertTrue(newBook.shouldMigrateLegacyBookId)
        assertTrue(newBook.shouldResetSidecarState)

        val fastIdOnly = sharedPdfDocumentOpenBookPlan(
            currentBookId = "book-a",
            fastId = "file_100",
            selectedBookId = null,
        )
        assertEquals("file_100", fastIdOnly.bookId)
        assertNull(fastIdOnly.migrationTargetBookId)
        assertFalse(fastIdOnly.shouldMigrateLegacyBookId)
        assertTrue(fastIdOnly.shouldResetSidecarState)

        val selectedMatchesFastId = sharedPdfDocumentOpenBookPlan(
            currentBookId = "file_100",
            fastId = "file_100",
            selectedBookId = "file_100",
        )
        assertEquals("file_100", selectedMatchesFastId.bookId)
        assertNull(selectedMatchesFastId.migrationTargetBookId)
        assertFalse(selectedMatchesFastId.shouldMigrateLegacyBookId)
        assertFalse(selectedMatchesFastId.shouldResetSidecarState)
    }
}
