package com.aryan.reader.data

import androidx.room.Room
import com.aryan.reader.FileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentFileDaoReadingPositionTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecentFileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.recentFileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `updateEpubReadingPosition persists cfi locator progress and timestamps`() = runTest {
        dao.insertOrUpdateFile(recentFileEntity())

        dao.updateEpubReadingPosition(
            bookId = "book-1",
            cfi = "/4/2/6:33",
            chapterIndex = 7,
            blockIndex = 42,
            charOffset = 33,
            progress = 58.5f,
            timestamp = 9_000L
        )

        val saved = dao.getFileByUri("content://books/one")!!
        assertEquals("/4/2/6:33", saved.lastPositionCfi)
        assertEquals(7, saved.lastChapterIndex)
        assertEquals(42, saved.locatorBlockIndex)
        assertEquals(33, saved.locatorCharOffset)
        assertEquals(58.5f, saved.progressPercentage)
        assertEquals(9_000L, saved.timestamp)
        assertEquals(9_000L, saved.lastModifiedTimestamp)
        assertEquals(9_000L, saved.readingPositionModifiedTimestamp)
    }

    @Test
    fun `updateEpubReadingPosition can persist locator when webview cfi is unavailable`() = runTest {
        dao.insertOrUpdateFile(recentFileEntity(lastPositionCfi = "/old:1"))

        dao.updateEpubReadingPosition(
            bookId = "book-1",
            cfi = null,
            chapterIndex = 2,
            blockIndex = 9,
            charOffset = 0,
            progress = 12f,
            timestamp = 2_000L
        )

        val saved = dao.getFileByBookId("book-1")!!
        assertNull(saved.lastPositionCfi)
        assertEquals(2, saved.lastChapterIndex)
        assertEquals(9, saved.locatorBlockIndex)
        assertEquals(0, saved.locatorCharOffset)
        assertEquals(12f, saved.progressPercentage)
    }

    @Test
    fun `recent file summary exposes persisted cfi and locator fields for reader restore`() = runTest {
        dao.insertOrUpdateFile(recentFileEntity())
        dao.updateEpubReadingPosition(
            bookId = "book-1",
            cfi = "/6/4:12",
            chapterIndex = 3,
            blockIndex = 21,
            charOffset = 12,
            progress = 44f,
            timestamp = 3_000L
        )

        val item = dao.getRecentFiles().first().single().toRecentFileItem()

        assertEquals("/6/4:12", item.lastPositionCfi)
        assertEquals(3, item.lastChapterIndex)
        assertEquals(21, item.locatorBlockIndex)
        assertEquals(12, item.locatorCharOffset)
        assertEquals(44f, item.progressPercentage)
        assertEquals(3_000L, item.readingPositionModifiedTimestamp)
        assertTrue(item.isRecent)
    }

    private fun recentFileEntity(lastPositionCfi: String? = null): RecentFileEntity {
        return RecentFileEntity(
            bookId = "book-1",
            uriString = "content://books/one",
            type = FileType.EPUB,
            displayName = "One.epub",
            timestamp = 1_000L,
            coverImagePath = null,
            title = "One",
            author = "Author",
            lastChapterIndex = null,
            lastPage = null,
            lastPositionCfi = lastPositionCfi,
            progressPercentage = null,
            isRecent = true,
            isAvailable = true,
            lastModifiedTimestamp = 1_000L,
            isDeleted = false,
            locatorBlockIndex = null,
            locatorCharOffset = null,
            bookmarks = null,
            sourceFolderUri = null,
            isReflowPreferred = false,
            customName = null,
            highlights = null,
            fileSize = 123L,
            seriesName = null,
            seriesIndex = null,
            description = null,
            folderTextMetadataParsed = false
        )
    }
}
