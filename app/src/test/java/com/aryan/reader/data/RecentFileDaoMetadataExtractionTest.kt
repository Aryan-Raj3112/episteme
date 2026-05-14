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

    private fun recentFileEntity(
        folderTextMetadataParsed: Boolean,
        folderCoverMetadataParsed: Boolean
    ): RecentFileEntity {
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
            lastPositionCfi = null,
            progressPercentage = null,
            isRecent = false,
            isAvailable = true,
            lastModifiedTimestamp = 1_000L,
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
