package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfReaderPersistenceTest {
    @Test
    fun pdfReaderStateKeyIsStableWhenTheImportedPathChanges() {
        val original = pdfBook(id = "stable-book", path = "/old/Book.pdf")
        val moved = original.copy(path = "/new/location/Book.pdf")

        assertEquals(
            original.iosPdfReaderStateDefaultsKeys().first(),
            moved.iosPdfReaderStateDefaultsKeys().first(),
        )
        assertTrue(
            original.iosPdfReaderStateDefaultsKeys().contains(
                "reader_ios_pdf_state_v1_old_book_pdf",
            )
        )
        assertTrue(
            original.iosPdfReaderStateDefaultsKeys().contains(
                "reader_ios_pdf_state_v1_stable_book",
            )
        )
    }

    @Test
    fun legacyPdfBookmarkFieldIsConvertedToSharedPageBookmarks() {
        val raw = """
            {
              "bookmarksJson": [
                {"pageIndex": 4, "title": "Chapter Five", "totalPages": 12},
                {"pageIndex": 1, "title": "Intro", "totalPages": 12},
                {"pageIndex": 4, "title": "Duplicate", "totalPages": 12}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(
                SharedPdfBookmark(pageIndex = 1, label = "Intro", createdAt = 0L),
                SharedPdfBookmark(pageIndex = 4, label = "Chapter Five", createdAt = 0L),
            ),
            decodeLegacyIosPdfBookmarks(raw),
        )
    }

    @Test
    fun legacyPdfBookmarksCanBeFoundInAnOlderLibrarySnapshot() {
        val raw = """
            {
              "books": [
                {
                  "id": "pdf-book",
                  "type": "PDF",
                  "bookmarksJson": "[{\"pageIndex\":2,\"title\":\"Saved\",\"totalPages\":3}]"
                },
                {
                  "id": "epub-book",
                  "type": "EPUB",
                  "bookmarksJson": "[{\"pageIndex\":1,\"title\":\"Ignored\",\"totalPages\":3}]"
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            mapOf(
                "pdf-book" to listOf(
                    SharedPdfBookmark(pageIndex = 2, label = "Saved", createdAt = 0L),
                ),
                "epub-book" to listOf(
                    SharedPdfBookmark(pageIndex = 1, label = "Ignored", createdAt = 0L),
                ),
            ),
            decodeLegacyIosPdfBookmarksByBookId(raw),
        )
    }

    private fun pdfBook(id: String, path: String): BookItem = BookItem(
        id = id,
        path = path,
        type = FileType.PDF,
        displayName = "Book.pdf",
        timestamp = 1L,
    )
}
