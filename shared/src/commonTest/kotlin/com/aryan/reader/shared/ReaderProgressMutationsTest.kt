package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReaderProgressMutationsTest {
    @Test
    fun `pdf progress keeps Android page calculation and empty document fallback`() {
        assertEquals(50f, pdfReadingProgressPercentage(pageIndex = 1, totalPages = 4))
        assertEquals(0f, pdfReadingProgressPercentage(pageIndex = 0, totalPages = 0))
    }
    @Test
    fun `pdf progress change advances reading timestamp`() {
        val book = pdfBook().copy(
            lastPageIndex = 1,
            progressPercentage = 10f,
            readingPositionModifiedTimestamp = 50L,
        )

        val updated = book.withPdfReadingProgress(
            pageIndex = 4,
            progressPercentage = 45f,
            modifiedAt = 100L,
        )

        assertEquals(4, updated.lastPageIndex)
        assertEquals(45f, updated.progressPercentage)
        assertEquals(100L, updated.readingPositionModifiedTimestamp)
    }

    @Test
    fun `pdf progress applied to current edited book preserves metadata edits`() {
        val edited = pdfBook().copy(
            title = "Edited in reader",
            author = "Updated author",
            metadataModifiedTimestamp = 90L,
        )

        val progressed = edited.withPdfReadingProgress(
            pageIndex = 7,
            progressPercentage = 64f,
            modifiedAt = 100L,
        )

        assertEquals("Edited in reader", progressed.title)
        assertEquals("Updated author", progressed.author)
        assertEquals(90L, progressed.metadataModifiedTimestamp)
        assertEquals(7, progressed.lastPageIndex)
        assertEquals(64f, progressed.progressPercentage)
    }

    @Test
    fun `unchanged pdf progress does not create a newer reading update`() {
        val book = pdfBook().copy(
            lastPageIndex = 4,
            progressPercentage = 45f,
            readingPositionModifiedTimestamp = 100L,
        )

        val unchanged = book.withPdfReadingProgress(
            pageIndex = 4,
            progressPercentage = 45f,
            modifiedAt = 200L,
        )

        assertSame(book, unchanged)
        assertEquals(100L, unchanged.readingPositionModifiedTimestamp)
    }

    @Test
    fun `unchanged epub session does not create a false newer reading update`() {
        val book = pdfBook().copy(
            type = FileType.EPUB,
            readerPosition = ReaderLocator(chapterIndex = 2, startOffset = 40),
            progressPercentage = 25f,
            readingPositionModifiedTimestamp = 100L,
        )

        val unchanged = book.withReaderSessionState(book, modifiedAt = 200L)

        assertSame(book, unchanged)
        assertEquals(100L, unchanged.readingPositionModifiedTimestamp)
    }

    @Test
    fun `changed epub session advances timestamp without copying metadata`() {
        val book = pdfBook().copy(
            type = FileType.EPUB,
            title = "Edited title",
            readerPosition = ReaderLocator(chapterIndex = 1),
            readingPositionModifiedTimestamp = 100L,
        )
        val session = book.copy(
            title = "Stale title",
            readerPosition = ReaderLocator(chapterIndex = 2),
            progressPercentage = 60f,
        )

        val updated = book.withReaderSessionState(session, modifiedAt = 200L)

        assertEquals("Edited title", updated.title)
        assertEquals(2, updated.readerPosition?.chapterIndex)
        assertEquals(60f, updated.progressPercentage)
        assertEquals(200L, updated.readingPositionModifiedTimestamp)
    }

    private fun pdfBook() = BookItem(
        id = "pdf",
        path = "/books/pdf.pdf",
        type = FileType.PDF,
        displayName = "pdf.pdf",
        timestamp = 1L,
    )
}
