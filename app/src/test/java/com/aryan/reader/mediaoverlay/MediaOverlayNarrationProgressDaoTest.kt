package com.aryan.reader.mediaoverlay

import androidx.room.Room
import com.aryan.reader.FileType
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.shared.reader.SharedMediaOverlayNarrationProgress
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The row a reopen resumes from.
 *
 * The two properties that matter are both invisible in a screenshot: the position is *replaced* rather
 * than accumulated (a book has one narration position, not a history), and it dies with the book — a
 * re-imported file that reuses the id must not resume into a passage from the old one.
 */
@RunWith(RobolectricTestRunner::class)
class MediaOverlayNarrationProgressDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MediaOverlayNarrationProgressDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.mediaOverlayNarrationProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a book's narration position is replaced rather than accumulated`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))

        dao.upsert(entity("book-1", spineItemIndex = 4, chapterIndex = 9, clipIndex = 12))
        dao.upsert(entity("book-1", spineItemIndex = 4, chapterIndex = 9, clipIndex = 13))

        val saved = dao.get("book-1")
        assertEquals(13, saved?.clipIndex)
        assertEquals(4, saved?.spineItemIndex)
        assertEquals(9, saved?.chapterIndex)
    }

    @Test
    fun `a chapter-less position reads back as chapter-less`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))

        dao.upsert(entity("book-1", spineItemIndex = 2, chapterIndex = null, clipIndex = 7))

        assertNull(dao.get("book-1")?.chapterIndex)
        assertEquals(7, dao.get("book-1")?.clipIndex)
    }

    @Test
    fun `a book's position is absent until it narrates`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))

        assertNull(dao.get("book-1"))
    }

    /**
     * The store is what the session actually talks to, so its mapping is part of the contract: a
     * recorded position must read back as the same position, not a shifted one.
     */
    @Test
    fun `the store round trips a recorded position`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))
        val store = AndroidMediaOverlayNarrationProgressStore(dao)

        store.persist(
            SharedMediaOverlayNarrationProgress(
                bookId = "book-1",
                spineItemIndex = 4,
                chapterIndex = 9,
                clipIndex = 12,
                updatedAt = 1_000L
            )
        )

        assertEquals(
            SharedMediaOverlayNarrationProgress("book-1", 4, 9, 12, 1_000L),
            store.load("book-1")
        )
    }

    @Test
    fun `the store has no position for a book that never narrated`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))
        val store = AndroidMediaOverlayNarrationProgressStore(dao)

        assertNull(store.load("book-1"))
    }

    /** Deleting the book takes its narration position with it, so a reused id cannot resume. */
    @Test
    fun `removing the book drops its narration position`() = runTest {
        db.recentFileDao().insertOrUpdateFile(recentFile("book-1"))
        dao.upsert(entity("book-1", spineItemIndex = 4, chapterIndex = 9, clipIndex = 12))

        db.recentFileDao().deleteFilePermanently(listOf("book-1"))

        assertNull(dao.get("book-1"))
    }

    private fun entity(
        bookId: String,
        spineItemIndex: Int,
        chapterIndex: Int?,
        clipIndex: Int
    ) = MediaOverlayNarrationProgressEntity(
        bookId = bookId,
        spineItemIndex = spineItemIndex,
        chapterIndex = chapterIndex,
        clipIndex = clipIndex,
        updatedAt = 1_000L
    )

    private fun recentFile(bookId: String) = RecentFileEntity(
        bookId = bookId,
        uriString = "content://books/$bookId",
        type = FileType.EPUB,
        displayName = "$bookId.epub",
        timestamp = 1_000L,
        dateAddedTimestamp = 1_000L,
        coverImagePath = null,
        title = bookId,
        author = null,
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
