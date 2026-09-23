package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfExportFilenamesTest {
    @Test
    fun suggestedNameMatchesAndroidBenchmark() {
        assertEquals(
            "Book_1234.pdf",
            suggestSharedPdfExportFilename("Book.pdf", isAnnotated = false, randomSuffix = 1234),
        )
        assertEquals(
            "Book_annotated_1234.pdf",
            suggestSharedPdfExportFilename("Book.pdf", isAnnotated = true, randomSuffix = 1234),
        )
    }

    @Test
    fun suggestedNameSanitizesAndTruncatesLikeAndroid() {
        assertEquals(
            "My_Book__1234.pdf",
            suggestSharedPdfExportFilename("My Book!.pdf", isAnnotated = false, randomSuffix = 1234),
        )
        assertEquals(
            "Document_1234.pdf",
            suggestSharedPdfExportFilename(null, isAnnotated = false, randomSuffix = 1234),
        )
        val long = "a".repeat(100) + ".pdf"
        val suggested = suggestSharedPdfExportFilename(long, isAnnotated = false, randomSuffix = 1234)
        assertTrue(suggested.startsWith("a".repeat(50)))
        assertTrue(suggested.endsWith("_1234.pdf"))
    }

    @Test
    fun sanitizeMatchesAndroidShareArtifacts() {
        assertEquals("book.pdf", sanitizeSharedPdfExportFilename("book.pdf"))
        assertEquals("my_file_.pdf", sanitizeSharedPdfExportFilename("my file?.pdf"))
        assertEquals("...", sanitizeSharedPdfExportFilename("..."))
        assertEquals("shared-file", sanitizeSharedPdfExportFilename("."))
        assertEquals("shared-file", sanitizeSharedPdfExportFilename(".."))
        assertEquals("shared-file", sanitizeSharedPdfExportFilename("   "))
    }

    @Test
    fun pdfDateStringMatchesAndroidSimpleDateFormat() {
        assertEquals("", sharedPdfDateString(0L))
        assertEquals("", sharedPdfDateString(-1L))
        // 2024-01-15 10:30:45 UTC
        assertEquals("D:20240115103045Z", sharedPdfDateString(1705314645000L))
        // Epoch + 1ms still formats as epoch start (seconds resolution, like SimpleDateFormat).
        assertEquals("D:19700101000000Z", sharedPdfDateString(1L))
    }
}
