package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedFormattersTest {
    @Test
    fun `pdf filename preference overrides embedded title only for pdf`() {
        val pdf = book(FileType.PDF)
        val epub = book(FileType.EPUB)

        assertEquals("Embedded title", pdf.cardTitle())
        assertEquals("document.pdf", pdf.cardTitle(usePdfFileNameAsDisplayName = true))
        assertEquals("Embedded title", epub.cardTitle(usePdfFileNameAsDisplayName = true))
    }

    @Test
    fun `blank embedded title falls back to filename`() {
        assertEquals("document.pdf", book(FileType.PDF).copy(title = " ").cardTitle())
    }

    @Test
    fun `original file actions require a local non streamed path`() {
        assertTrue(book(FileType.PDF).canExportOriginalFile())
        assertFalse(book(FileType.PDF).copy(path = null).canExportOriginalFile())
        assertFalse(
            book(FileType.PDF)
                .copy(path = "opds-pse://stream?id=remote")
                .canExportOriginalFile()
        )
    }

    private fun book(type: FileType) = BookItem(
        id = "book",
        path = "/books/document.pdf",
        type = type,
        displayName = "document.pdf",
        title = "Embedded title",
        timestamp = 1L,
    )
}
