package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.AudiobookImporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun audiobookStatusFiltersUsePlaybackProgress() {
        val items = listOf(
            AudiobookUiItem("new", "New", "Author", "Narrator", "Start", 0f, "10 hr"),
            AudiobookUiItem("active", "Active", "Author", "Narrator", "Chapter 2", .4f, "6 hr"),
            AudiobookUiItem("done", "Done", "Author", "Narrator", "Complete", 1f, "Finished")
        )

        assertEquals(listOf("active"), filterAudiobooks(items, AudiobookUiStatus.IN_PROGRESS).map { it.id })
        assertEquals(listOf("new"), filterAudiobooks(items, AudiobookUiStatus.NOT_STARTED).map { it.id })
        assertEquals(listOf("done"), filterAudiobooks(items, AudiobookUiStatus.COMPLETED).map { it.id })
    }

    @Test
    fun audiobookImportRecognizesSupportedAudioExtensionsCaseInsensitively() {
        assertTrue(AudiobookImporter.isSupportedAudiobookFileName("Novel.M4B"))
        assertTrue(AudiobookImporter.isSupportedAudiobookFileName("chapter.opus"))
        assertFalse(AudiobookImporter.isSupportedAudiobookFileName("cover.jpg"))
        assertFalse(AudiobookImporter.isSupportedAudiobookFileName("missing-extension"))
    }

    @Test
    fun audiobookSpeedControlCyclesThroughListeningSpeeds() {
        assertEquals(1.25f, nextAudiobookSpeed(1f))
        assertEquals(.75f, nextAudiobookSpeed(2f))
    }
}
