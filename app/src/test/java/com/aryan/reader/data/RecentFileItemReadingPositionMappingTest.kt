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
            bookmarksJson = """[{"cfi":"/4/2"}]""",
            highlightsJson = """[{"cfi":"/4/2/6:88"}]"""
        )
    }
}
