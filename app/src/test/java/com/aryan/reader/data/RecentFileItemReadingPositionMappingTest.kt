package com.aryan.reader.data

import com.aryan.reader.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFileItemReadingPositionMappingTest {

    @Test
    fun `recent file entity mapping preserves epub cfi locator and progress fields`() {
        val item = recentFileItem()

        val roundTripped = item.toRecentFileEntity().toRecentFileItem()

        assertEquals(item.lastPositionCfi, roundTripped.lastPositionCfi)
        assertEquals(item.lastChapterIndex, roundTripped.lastChapterIndex)
        assertEquals(item.locatorBlockIndex, roundTripped.locatorBlockIndex)
        assertEquals(item.locatorCharOffset, roundTripped.locatorCharOffset)
        assertEquals(item.progressPercentage, roundTripped.progressPercentage)
        assertEquals(item.fileContentModifiedTimestamp, roundTripped.fileContentModifiedTimestamp)
        assertEquals(item.readingPositionModifiedTimestamp, roundTripped.readingPositionModifiedTimestamp)
    }

    @Test
    fun `cloud metadata mapping preserves epub cfi locator and progress fields`() {
        val item = recentFileItem()

        val roundTripped = item.toBookMetadata().toRecentFileItem()

        assertEquals(item.lastPositionCfi, roundTripped.lastPositionCfi)
        assertEquals(item.lastChapterIndex, roundTripped.lastChapterIndex)
        assertEquals(item.locatorBlockIndex, roundTripped.locatorBlockIndex)
        assertEquals(item.locatorCharOffset, roundTripped.locatorCharOffset)
        assertEquals(item.progressPercentage, roundTripped.progressPercentage)
        assertEquals(item.fileContentModifiedTimestamp, roundTripped.fileContentModifiedTimestamp)
        assertEquals(item.readingPositionModifiedTimestamp, roundTripped.readingPositionModifiedTimestamp)
    }

    @Test
    fun `cloud metadata mapping preserves non epub display rename as custom name`() {
        val item = recentFileItem().copy(
            type = FileType.PDF,
            customName = "Reader Display Name"
        )

        val roundTripped = item.toBookMetadata().toRecentFileItem()

        assertEquals("Reader Display Name", roundTripped.customName)
        assertEquals("One.epub", roundTripped.displayName)
    }

    @Test
    fun `recent file entity mapping preserves original metadata snapshot`() {
        val item = recentFileItem().copy(
            title = "Edited One",
            author = "Edited Author",
            seriesName = "Edited Series",
            seriesIndex = 2.0,
            description = "Edited summary",
            originalTitle = "Original One",
            originalAuthor = "Original Author",
            originalSeriesName = "Original Series",
            originalSeriesIndex = 1.0,
            originalDescription = "Original summary"
        )

        val roundTripped = item.toRecentFileEntity().toRecentFileItem()

        assertEquals("Original One", roundTripped.originalTitle)
        assertEquals("Original Author", roundTripped.originalAuthor)
        assertEquals("Original Series", roundTripped.originalSeriesName)
        assertEquals(1.0, roundTripped.originalSeriesIndex)
        assertEquals("Original summary", roundTripped.originalDescription)
    }

    @Test
    fun `recent file entity mapping seeds original metadata when first stored`() {
        val entity = recentFileItem().copy(
            seriesName = "Series",
            seriesIndex = 1.0,
            description = "Summary"
        ).toRecentFileEntity()

        assertEquals("One", entity.originalTitle)
        assertEquals("Author", entity.originalAuthor)
        assertEquals("Series", entity.originalSeriesName)
        assertEquals(1.0, entity.originalSeriesIndex)
        assertEquals("Summary", entity.originalDescription)
    }

    private fun recentFileItem(): RecentFileItem {
        return RecentFileItem(
            bookId = "book-1",
            uriString = "content://books/one",
            type = FileType.EPUB,
            displayName = "One.epub",
            timestamp = 1_000L,
            title = "One",
            author = "Author",
            lastChapterIndex = 4,
            lastPositionCfi = "/4/2/6:88",
            locatorBlockIndex = 30,
            locatorCharOffset = 88,
            progressPercentage = 61.5f,
            lastModifiedTimestamp = 2_000L,
            readingPositionModifiedTimestamp = 1_900L,
            fileContentModifiedTimestamp = 3_000L,
            bookmarksJson = """[{"cfi":"/4/2"}]""",
            highlightsJson = """[{"cfi":"/4/2/6:88"}]"""
        )
    }
}
