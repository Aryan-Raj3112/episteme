package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun book(type: FileType) = BookItem(
        id = "book",
        path = "/books/document.pdf",
        type = type,
        displayName = "document.pdf",
        title = "Embedded title",
        timestamp = 1L,
    )
}
