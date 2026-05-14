package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedImportPlannerTest {

    @Test
    fun `plan classifies importable duplicate and unsupported files`() {
        val plan = SharedImportPlanner.plan(
            files = listOf(
                ImportedBookFile(name = "existing.epub", uriString = null, localPath = "/books/existing.epub", size = 1L),
                ImportedBookFile(name = "new.md", uriString = null, localPath = "/books/new.md", size = 2L),
                ImportedBookFile(name = "archive.zip", uriString = null, localPath = "/books/archive.zip", size = 3L),
                ImportedBookFile(name = "new.md", uriString = null, localPath = "/books/new.md", size = 2L)
            ),
            existingBookIds = setOf("/books/existing.epub"),
            platform = ReaderPlatform.DESKTOP,
            nowMillis = 100L
        )

        assertEquals(
            listOf(
                SharedImportDecisionStatus.DUPLICATE,
                SharedImportDecisionStatus.IMPORTABLE,
                SharedImportDecisionStatus.UNSUPPORTED,
                SharedImportDecisionStatus.DUPLICATE
            ),
            plan.decisions.map { it.status }
        )
        assertEquals(listOf("/books/new.md"), plan.importedBooks.map { it.id })
        assertEquals(listOf("existing.epub", "new.md", "new.md"), plan.supportedFiles.map { it.name })
        assertEquals(FileType.MD, plan.importedBooks.single().type)
        assertEquals(101L, plan.importedBooks.single().timestamp)
        assertEquals(null, plan.importedBooks.single().sourceFolder)
        assertFalse(plan.importedBooks.single().isRecent)
        assertEquals(1, plan.importedCount)
        assertEquals(2, plan.duplicateCount)
        assertEquals(1, plan.unsupportedCount)
    }

    @Test
    fun `plan uses uri as stable id when local path is absent`() {
        val plan = SharedImportPlanner.plan(
            files = listOf(
                ImportedBookFile(name = "scan.pdf", uriString = "content://scan", localPath = null, size = 4L, sourceFolder = "content://folder")
            ),
            existingBookIds = emptySet(),
            platform = ReaderPlatform.ANDROID,
            nowMillis = 5L
        )

        val book = plan.importedBooks.single()
        assertEquals("content://scan", book.id)
        assertEquals("content://scan", book.path)
        assertEquals("content://folder", book.sourceFolder)
    }

    @Test
    fun `plan prefers prepared file id over storage path`() {
        val plan = SharedImportPlanner.plan(
            files = listOf(
                ImportedBookFile(
                    name = "novel.epub",
                    uriString = null,
                    localPath = "/app/books/copied.epub",
                    size = 4L,
                    id = "content-sha"
                )
            ),
            existingBookIds = emptySet(),
            platform = ReaderPlatform.DESKTOP,
            nowMillis = 5L
        )

        val book = plan.importedBooks.single()
        assertEquals("content-sha", book.id)
        assertEquals("/app/books/copied.epub", book.path)
        assertEquals(null, book.sourceFolder)
    }

    @Test
    fun `feedback prefers imported duplicate unsupported then failed outcomes`() {
        val imported = SharedImportPlanner.feedbackForCounts(
            counts = SharedImportOutcomeCounts(addedCount = 2, duplicateCount = 1, unsupportedCount = 1),
            importedMessage = "imported",
            duplicateMessage = "duplicate",
            unsupportedMessage = "unsupported",
            failedMessage = "failed"
        )
        val duplicate = SharedImportPlanner.feedbackForCounts(
            counts = SharedImportOutcomeCounts(duplicateCount = 1),
            importedMessage = "imported",
            duplicateMessage = "duplicate",
            unsupportedMessage = "unsupported",
            failedMessage = "failed"
        )
        val unsupported = SharedImportPlanner.feedbackForCounts(
            counts = SharedImportOutcomeCounts(unsupportedCount = 1),
            importedMessage = "imported",
            duplicateMessage = "duplicate",
            unsupportedMessage = "unsupported",
            failedMessage = "failed"
        )

        assertEquals("imported", imported.message)
        assertFalse(imported.isError)
        assertEquals("duplicate", duplicate.message)
        assertFalse(duplicate.isError)
        assertEquals("unsupported", unsupported.message)
        assertTrue(unsupported.isError)
    }
}
