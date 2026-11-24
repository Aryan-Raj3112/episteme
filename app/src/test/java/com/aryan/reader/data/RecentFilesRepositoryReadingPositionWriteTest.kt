package com.aryan.reader.data

import android.content.Context
import com.aryan.reader.paginatedreader.Locator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pins that reading-position writes are addressed by the Room book id and reported by their own
 * affected-row count. A verifying `SELECT *` after the write used to run on every page turn; it is
 * what crashed in docs/crashlytics-triage.md #177 (24 B inside `RecentFileDao_Impl.getFileByBookId`).
 */
class RecentFilesRepositoryReadingPositionWriteTest {

    private lateinit var context: Context
    private lateinit var recentFileDao: RecentFileDao
    private lateinit var repository: RecentFilesRepository

    @Before
    fun setUp() {
        val testRoot = File("build/test-tmp/RecentFilesRepositoryReadingPositionWriteTest/${System.nanoTime()}")
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
    fun `pdf position write uses the book id and never reads the row back`() = runTest {
        coEvery {
            recentFileDao.updatePdfReadingPosition("book-1", 7, 12.5f, any())
        } returns 1

        assertTrue(repository.updatePdfReadingPosition("book-1", page = 7, progress = 12.5f))

        coVerify(exactly = 1) { recentFileDao.updatePdfReadingPosition("book-1", 7, 12.5f, any()) }
        coVerify(exactly = 0) { recentFileDao.getFileByBookId(any()) }
        coVerify(exactly = 0) { recentFileDao.getFileByUri(any()) }
    }

    @Test
    fun `pdf position write reports a missing row without a follow-up query`() = runTest {
        coEvery {
            recentFileDao.updatePdfReadingPosition("missing-book", 1, 0f, any())
        } returns 0

        assertFalse(repository.updatePdfReadingPosition("missing-book", page = 1, progress = 0f))

        coVerify(exactly = 0) { recentFileDao.getFileByBookId(any()) }
        coVerify(exactly = 0) { recentFileDao.getFileByUri(any()) }
    }

    @Test
    fun `epub position write uses the book id and never reads the row back`() = runTest {
        val locator = Locator(chapterIndex = 4, blockIndex = 11, charOffset = 3)
        coEvery {
            recentFileDao.updateEpubReadingPosition(
                bookId = "book-1",
                cfi = "/4/11:3",
                chapterIndex = 4,
                blockIndex = 11,
                charOffset = 3,
                progress = 33f,
                timestamp = any()
            )
        } returns 1

        assertTrue(
            repository.updateEpubReadingPosition(
                bookId = "book-1",
                locator = locator,
                cfiForWebView = "/4/11:3",
                progress = 33f
            )
        )

        coVerify(exactly = 1) {
            recentFileDao.updateEpubReadingPosition(
                bookId = "book-1",
                cfi = "/4/11:3",
                chapterIndex = 4,
                blockIndex = 11,
                charOffset = 3,
                progress = 33f,
                timestamp = any()
            )
        }
        coVerify(exactly = 0) { recentFileDao.getFileByBookId(any()) }
        coVerify(exactly = 0) { recentFileDao.getFileByUri(any()) }
    }

    @Test
    fun `epub position write reports a missing row without a follow-up query`() = runTest {
        val locator = Locator(chapterIndex = 1, blockIndex = 0, charOffset = 0)
        coEvery {
            recentFileDao.updateEpubReadingPosition(
                bookId = "missing-book",
                cfi = null,
                chapterIndex = 1,
                blockIndex = 0,
                charOffset = 0,
                progress = 5f,
                timestamp = any()
            )
        } returns 0

        assertFalse(
            repository.updateEpubReadingPosition(
                bookId = "missing-book",
                locator = locator,
                cfiForWebView = null,
                progress = 5f
            )
        )

        coVerify(exactly = 0) { recentFileDao.getFileByBookId(any()) }
        coVerify(exactly = 0) { recentFileDao.getFileByUri(any()) }
    }
}
