package com.aryan.reader.data

import androidx.room.Room
import com.aryan.reader.FileType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `getAllFilesForSync` is `SELECT *` plus a mapped copy, so it holds every JSON
 * blob column and ~40 fields per row twice over. Maintenance tasks only need
 * the id set, so they use `getAllBookIds()` instead (crashlytics-triage #54).
 * These pin that the narrow query returns exactly the ids the old expression
 * produced — including tombstoned rows, which the sweeper still has to see.
 */
@RunWith(RobolectricTestRunner::class)
class RecentFileDaoBookIdQueryTest {

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
    fun `book ids match the ids the full select produced`() = runTest {
        val rows = (0 until 25).map { index ->
            entity(bookId = "book-$index", uri = "content://books/$index", index = index)
        }
        dao.insertOrUpdateFiles(rows)

        val fromNarrowQuery = dao.getAllBookIds().toSet()
        val fromFullSelect = dao.getAllFiles().map { it.bookId }.toSet()

        assertEquals(fromFullSelect, fromNarrowQuery)
        assertEquals(25, fromNarrowQuery.size)
    }

    @Test
    fun `book ids include tombstoned rows the old expression also returned`() = runTest {
        // The cache sweeper must still recognize ids of soft-deleted books,
        // exactly as getAllFiles() -> map { bookId } did.
        dao.insertOrUpdateFile(entity(bookId = "live", uri = "content://books/live", index = 0))
        dao.insertOrUpdateFile(entity(bookId = "gone", uri = "content://books/gone", index = 1))
        dao.markAsDeleted(listOf("gone"), timestamp = 5_000L)

        val ids = dao.getAllBookIds().toSet()

        assertEquals(setOf("live", "gone"), ids)
        assertEquals(dao.getAllFiles().map { it.bookId }.toSet(), ids)
    }

    @Test
    fun `narrow query is unaffected by heavy blob columns`() = runTest {
        // The point of the narrow query: rows carrying large bookmark and
        // highlight payloads must still resolve, and must not need them read.
        val heavy = "x".repeat(20_000)
        val rows = (0 until 10).map { index ->
            entity(bookId = "heavy-$index", uri = "content://books/$index", index = index)
                .copy(bookmarks = heavy, highlights = heavy, description = heavy)
        }
        dao.insertOrUpdateFiles(rows)

        assertEquals(10, dao.getAllBookIds().size)
        assertTrue(dao.getAllBookIds().all { it.startsWith("heavy-") })
    }

    private fun entity(bookId: String, uri: String, index: Int): RecentFileEntity =
        RecentFileEntity(
            bookId = bookId,
            uriString = uri,
            type = FileType.EPUB,
            displayName = "Book $index.epub",
            timestamp = 1_000L + index,
            coverImagePath = null,
            title = "Book $index",
            author = "Author",
            lastChapterIndex = null,
            lastPage = null,
            lastPositionCfi = null,
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
            folderTextMetadataParsed = false,
        )
}