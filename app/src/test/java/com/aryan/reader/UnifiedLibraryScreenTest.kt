package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UnifiedLibraryScreenTest {
    private fun book(name: String, progress: Float?, timestamp: Long = 1L) = RecentFileItem(
        bookId = name,
        uriString = null,
        type = FileType.EPUB,
        displayName = name,
        timestamp = timestamp,
        progressPercentage = progress
    )

    @Test
    fun filtersBooksByReadingStatusAndQuery() {
        val books = listOf(
            book("Unread", 0f),
            book("Reading Kotlin", 42f),
            book("Finished", 100f)
        )

        assertEquals(listOf("Reading Kotlin"), filterUnifiedLibraryBooks(books, UnifiedLibraryFilter.READING, "").map { it.displayName })
        assertEquals(listOf("Finished"), filterUnifiedLibraryBooks(books, UnifiedLibraryFilter.FINISHED, "finish").map { it.displayName })
        assertEquals(listOf("Unread"), filterUnifiedLibraryBooks(books, UnifiedLibraryFilter.UNREAD, "").map { it.displayName })
    }

    @Test
    fun continueReadingPrefersMostRecentlyReadInProgressBook() {
        val older = book("Older", 30f, timestamp = 10L)
        val newer = book("Newer", 60f, timestamp = 20L)

        assertSame(newer, findContinueReadingBook(listOf(older, newer)))
    }

    @Test
    fun advancedReadStatusUsesTheSameBetaFilterState() {
        assertEquals(UnifiedLibraryFilter.ALL, ReadStatusFilter.ALL.toUnifiedLibraryFilter())
        assertEquals(UnifiedLibraryFilter.UNREAD, ReadStatusFilter.UNREAD.toUnifiedLibraryFilter())
        assertEquals(UnifiedLibraryFilter.READING, ReadStatusFilter.IN_PROGRESS.toUnifiedLibraryFilter())
        assertEquals(UnifiedLibraryFilter.FINISHED, ReadStatusFilter.COMPLETED.toUnifiedLibraryFilter())
    }
}
