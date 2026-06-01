package com.aryan.reader.data

import android.content.Context
import com.aryan.reader.FileType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class RecentFilesRepositoryReadingPositionMergeTest {

    private lateinit var context: Context
    private lateinit var recentFileDao: RecentFileDao
    private lateinit var repository: RecentFilesRepository

    @Before
    fun setUp() {
        val testRoot = File("build/test-tmp/RecentFilesRepositoryReadingPositionMergeTest/${System.nanoTime()}")
        val filesDir = File(testRoot, "files").apply { mkdirs() }
        val cacheDir = File(testRoot, "cache").apply { mkdirs() }

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir

        recentFileDao = mockk()
        val shelfDao = mockk<ShelfDao>()
        val tagDao = mockk<TagDao>()
        val db = mockk<AppDatabase>()
        every { db.recentFileDao() } returns recentFileDao
        every { db.shelfDao() } returns shelfDao
        every { db.tagDao() } returns tagDao
        every { shelfDao.getAllActiveShelves() } returns flowOf(emptyList())
        every { shelfDao.getAllBookShelfCrossRefs() } returns flowOf(emptyList())
        every { tagDao.getAllTags() } returns flowOf(emptyList())
        every { tagDao.getAllBookTagCrossRefs() } returns flowOf(emptyList())

        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns db

        repository = RecentFilesRepository(context)
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
    }

    @Test
    fun `addRecentFile preserves existing reading position when incoming metadata omits it`() = runTest {
        val inserted = slot<RecentFileEntity>()
        coEvery { recentFileDao.getFileByBookId("book-1") } returns existingEntity()
        coEvery { recentFileDao.insertOrUpdateFile(capture(inserted)) } just Runs

        repository.addRecentFile(
            RecentFileItem(
                bookId = "book-1",
                uriString = "content://new",
                type = FileType.EPUB,
                displayName = "New.epub",
                timestamp = 2_000L,
                isRecent = true
            )
        )

        assertEquals("/4/2/6:44", inserted.captured.lastPositionCfi)
        assertEquals(6, inserted.captured.lastChapterIndex)
        assertEquals(24, inserted.captured.locatorBlockIndex)
        assertEquals(44, inserted.captured.locatorCharOffset)
        assertEquals(71.5f, inserted.captured.progressPercentage)
        coVerify { recentFileDao.insertOrUpdateFile(any()) }
    }

    @Test
    fun `addRecentFile uses incoming reading position when newer metadata includes it`() = runTest {
        val inserted = slot<RecentFileEntity>()
        coEvery { recentFileDao.getFileByBookId("book-1") } returns existingEntity()
        coEvery { recentFileDao.insertOrUpdateFile(capture(inserted)) } just Runs

        repository.addRecentFile(
            RecentFileItem(
                bookId = "book-1",
                uriString = "content://new",
                type = FileType.EPUB,
                displayName = "New.epub",
                timestamp = 2_000L,
                lastChapterIndex = 8,
                lastPositionCfi = "/6/4:12",
                locatorBlockIndex = 31,
                locatorCharOffset = 12,
                progressPercentage = 82f,
                lastModifiedTimestamp = 2_000L,
                readingPositionModifiedTimestamp = 2_000L,
                isRecent = true
            )
        )

        assertEquals("/6/4:12", inserted.captured.lastPositionCfi)
        assertEquals(8, inserted.captured.lastChapterIndex)
        assertEquals(31, inserted.captured.locatorBlockIndex)
        assertEquals(12, inserted.captured.locatorCharOffset)
        assertEquals(82f, inserted.captured.progressPercentage)
        assertEquals(2_000L, inserted.captured.readingPositionModifiedTimestamp)
    }

    @Test
    fun `addRecentFile preserves newer existing reading position when incoming metadata timestamp is newer`() = runTest {
        val inserted = slot<RecentFileEntity>()
        coEvery { recentFileDao.getFileByBookId("book-1") } returns existingEntity().copy(
            readingPositionModifiedTimestamp = 1_800L
        )
        coEvery { recentFileDao.insertOrUpdateFile(capture(inserted)) } just Runs

        repository.addRecentFile(
            RecentFileItem(
                bookId = "book-1",
                uriString = "content://new",
                type = FileType.EPUB,
                displayName = "New.epub",
                timestamp = 3_000L,
                lastChapterIndex = 1,
                lastPositionCfi = "/old/remote",
                locatorBlockIndex = 2,
                locatorCharOffset = 3,
                progressPercentage = 12f,
                lastModifiedTimestamp = 3_000L,
                readingPositionModifiedTimestamp = 1_200L,
                isRecent = true
            )
        )

        assertEquals("/4/2/6:44", inserted.captured.lastPositionCfi)
        assertEquals(6, inserted.captured.lastChapterIndex)
        assertEquals(24, inserted.captured.locatorBlockIndex)
        assertEquals(44, inserted.captured.locatorCharOffset)
        assertEquals(71.5f, inserted.captured.progressPercentage)
        assertEquals(1_800L, inserted.captured.readingPositionModifiedTimestamp)
    }

    @Test
    fun `addRecentFile keeps edited embedded epub metadata when cached parser returns original metadata`() = runTest {
        val inserted = slot<RecentFileEntity>()
        coEvery { recentFileDao.getFileByBookId("book-1") } returns existingEntity().copy(
            author = "Edited Author",
            originalAuthor = "Author",
            fileContentModifiedTimestamp = 5_000L
        )
        coEvery { recentFileDao.insertOrUpdateFile(capture(inserted)) } just Runs

        repository.addRecentFile(
            RecentFileItem(
                bookId = "book-1",
                uriString = "content://new",
                type = FileType.EPUB,
                displayName = "New.epub",
                timestamp = 2_000L,
                title = "Old",
                author = "Author",
                fileContentModifiedTimestamp = 5_000L,
                isRecent = true
            )
        )

        assertEquals("Edited Author", inserted.captured.author)
        assertEquals("Author", inserted.captured.originalAuthor)
    }

    @Test
    fun `addRecentFile clears extracted metadata when folder file size changes`() = runTest {
        val inserted = slot<RecentFileEntity>()
        coEvery { recentFileDao.getFileByBookId("book-1") } returns existingEntity(
            fileSize = 123L,
            folderTextMetadataParsed = true,
            folderCoverMetadataParsed = true,
            coverImagePath = "/covers/old.png"
        )
        coEvery { recentFileDao.insertOrUpdateFile(capture(inserted)) } just Runs

        repository.addRecentFile(
            RecentFileItem(
                bookId = "book-1",
                uriString = "content://new",
                type = FileType.EPUB,
                displayName = "New.epub",
                timestamp = 2_000L,
                sourceFolderUri = "content://folder",
                fileSize = 456L,
                isRecent = true
            )
        )

        assertEquals(456L, inserted.captured.fileSize)
        assertNull(inserted.captured.coverImagePath)
        assertEquals("New", inserted.captured.title)
        assertNull(inserted.captured.author)
        assertNull(inserted.captured.seriesName)
        assertNull(inserted.captured.description)
        assertNull(inserted.captured.originalTitle)
        assertFalse(inserted.captured.folderTextMetadataParsed)
        assertFalse(inserted.captured.folderCoverMetadataParsed)
    }

    private fun existingEntity(
        fileSize: Long = 123L,
        folderTextMetadataParsed: Boolean = true,
        folderCoverMetadataParsed: Boolean = false,
        coverImagePath: String? = "/covers/old.png"
    ): RecentFileEntity {
        return RecentFileEntity(
            bookId = "book-1",
            uriString = "content://old",
            type = FileType.EPUB,
            displayName = "Old.epub",
            timestamp = 1_000L,
            coverImagePath = coverImagePath,
            title = "Old",
            author = "Author",
            lastChapterIndex = 6,
            lastPage = null,
            lastPositionCfi = "/4/2/6:44",
            progressPercentage = 71.5f,
            isRecent = true,
            isAvailable = true,
            lastModifiedTimestamp = 1_500L,
            isDeleted = false,
            locatorBlockIndex = 24,
            locatorCharOffset = 44,
            bookmarks = "bookmarks",
            sourceFolderUri = "content://folder",
            isReflowPreferred = false,
            customName = "Custom",
            highlights = "highlights",
            fileSize = fileSize,
            seriesName = "Series",
            seriesIndex = 1.0,
            description = "Description",
            folderTextMetadataParsed = folderTextMetadataParsed,
            folderCoverMetadataParsed = folderCoverMetadataParsed
        )
    }
}
