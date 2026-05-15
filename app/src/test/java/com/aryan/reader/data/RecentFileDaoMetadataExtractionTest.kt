package com.aryan.reader.data

import androidx.room.Room
import com.aryan.reader.FileType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            seriesName = null,
            seriesIndex = null,
            description = null,
            fileSize = 0L,
            fileContentModifiedTimestamp = 0L,
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

    @Test
    fun `metadata extraction does not replace user edited metadata`() = runTest {
        dao.insertOrUpdateFile(
            recentFileEntity().copy(
                title = "Edited title",
                author = "Edited author",
                originalTitle = "Original title",
                originalAuthor = "Original author"
            )
        )

        dao.updateExtractedMetadata(
            bookId = "book-1",
            coverImagePath = null,
            title = "Extracted title",
            author = "Extracted author",
            seriesName = null,
            seriesIndex = null,
            description = null,
            fileSize = 0L,
            fileContentModifiedTimestamp = 0L,
            textMetadataParsed = true,
            coverMetadataParsed = false
        )

        val saved = dao.getFileByBookId("book-1")!!
        assertEquals("Edited title", saved.title)
        assertEquals("Edited author", saved.author)
        assertEquals("Original title", saved.originalTitle)
        assertEquals("Original author", saved.originalAuthor)
    }

    @Test
    fun `metadata extraction promotes extracted values before user edits`() = runTest {
        dao.insertOrUpdateFile(
            recentFileEntity().copy(
                title = "book-1.epub",
                author = null,
                originalTitle = "book-1.epub",
                originalAuthor = null
            )
        )

        dao.updateExtractedMetadata(
            bookId = "book-1",
            coverImagePath = null,
            title = "Extracted title",
            author = "Extracted author",
            seriesName = null,
            seriesIndex = null,
            description = null,
            fileSize = 0L,
            fileContentModifiedTimestamp = 0L,
            textMetadataParsed = true,
            coverMetadataParsed = false
        )

        val saved = dao.getFileByBookId("book-1")!!
        assertEquals("Extracted title", saved.title)
        assertEquals("Extracted author", saved.author)
        assertEquals("Extracted title", saved.originalTitle)
        assertEquals("Extracted author", saved.originalAuthor)
    }

    @Test
    fun `restore original metadata restores snapshot and clears display override`() = runTest {
        dao.insertOrUpdateFile(
            recentFileEntity().copy(
                title = "Edited title",
                author = "Edited author",
                seriesName = "Edited series",
                seriesIndex = 2.0,
                description = "Edited summary",
                customName = "Edited display",
                originalTitle = "Original title",
                originalAuthor = "Original author",
                originalSeriesName = "Original series",
                originalSeriesIndex = 1.0,
                originalDescription = "Original summary"
            )
        )

        dao.restoreOriginalMetadata("book-1", fileSize = 0L, fileContentModifiedTimestamp = 0L, timestamp = 9_000L)

        val saved = dao.getFileByBookId("book-1")!!
        assertEquals("Original title", saved.title)
        assertEquals("Original author", saved.author)
        assertEquals("Original series", saved.seriesName)
        assertEquals(1.0, saved.seriesIndex)
        assertEquals("Original summary", saved.description)
        assertNull(saved.customName)
        assertEquals(9_000L, saved.lastModifiedTimestamp)
    }

    @Test
    fun `manual metadata update seeds missing original snapshot from previous values`() = runTest {
        dao.insertOrUpdateFile(
            recentFileEntity().copy(
                title = "Existing title",
                author = "Existing author",
                customName = "Display override",
                originalTitle = null,
                originalAuthor = null
            )
        )

        dao.updateUserEditableMetadata(
            bookId = "book-1",
            title = "Edited title",
            author = "Edited author",
            seriesName = null,
            seriesIndex = null,
            description = null,
            fileSize = 0L,
            fileContentModifiedTimestamp = 0L,
            timestamp = 5_000L
        )

        val saved = dao.getFileByBookId("book-1")!!
        assertEquals("Edited title", saved.title)
        assertEquals("Edited author", saved.author)
        assertEquals("Existing title", saved.originalTitle)
        assertEquals("Existing author", saved.originalAuthor)
        assertNull(saved.customName)
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
            fileContentModifiedTimestamp = 1_234L,
            seriesName = null,
            seriesIndex = null,
            description = null,
            folderTextMetadataParsed = folderTextMetadataParsed,
            folderCoverMetadataParsed = folderCoverMetadataParsed
        )
    }
}
