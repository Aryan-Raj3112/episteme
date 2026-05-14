package com.aryan.reader.data

import androidx.room.Room
import com.aryan.reader.FileType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentFileDaoMetadataExtractionTest {

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
    fun `cover-only ebook metadata candidate is marked attempted after no cover is found`() = runTest {
        dao.insertOrUpdateFile(
            recentFileEntity(
                folderTextMetadataParsed = true,
                folderCoverMetadataParsed = false
            )
        )

        assertEquals(1, dao.countFolderBooksNeedingTextMetadata("content://folder"))

        dao.updateExtractedMetadata(
            bookId = "book-1",
            coverImagePath = null,
            title = null,
            author = null,
            fileSize = 0L,
            textMetadataParsed = false,
            coverMetadataParsed = true
        )

        val saved = dao.getFileByBookId("book-1")!!
        assertFalse(saved.coverImagePath?.isNotBlank() == true)
        assertTrue(saved.folderCoverMetadataParsed)
        assertEquals(0, dao.countFolderBooksNeedingTextMetadata("content://folder"))
    }

    @Test
    fun `metadata candidate query respects batch limit`() = runTest {
        dao.insertOrUpdateFile(recentFileEntity(bookId = "book-1", timestamp = 1_000L))
        dao.insertOrUpdateFile(recentFileEntity(bookId = "book-2", timestamp = 2_000L))

        val pending = dao.getFolderBooksNeedingTextMetadata("content://folder", limit = 1)

        assertEquals(1, pending.size)
        assertEquals("book-2", pending.single().bookId)
    }

    private fun recentFileEntity(
        bookId: String = "book-1",
        timestamp: Long = 1_000L,
        folderTextMetadataParsed: Boolean = false,
        folderCoverMetadataParsed: Boolean = false
    ): RecentFileEntity {
        return RecentFileEntity(
            bookId = bookId,
            uriString = "content://books/$bookId",
            type = FileType.EPUB,
            displayName = "$bookId.epub",
            timestamp = timestamp,
            coverImagePath = null,
            title = "One",
            author = "Author",
            lastChapterIndex = null,
            lastPage = null,
            lastPositionCfi = null,
            progressPercentage = null,
            isRecent = false,
            isAvailable = true,
            lastModifiedTimestamp = timestamp,
            isDeleted = false,
            locatorBlockIndex = null,
            locatorCharOffset = null,
            bookmarks = null,
            sourceFolderUri = "content://folder",
            isReflowPreferred = false,
            customName = null,
            highlights = null,
            fileSize = 123L,
            seriesName = null,
            seriesIndex = null,
            description = null,
            folderTextMetadataParsed = folderTextMetadataParsed,
            folderCoverMetadataParsed = folderCoverMetadataParsed
        )
    }
}
