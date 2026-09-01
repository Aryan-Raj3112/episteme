package com.aryan.reader.data

import android.content.Context
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CloudBookDeleteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: CloudBookDeleteDao
    private lateinit var recentFileDao: RecentFileDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cloudBookDeleteDao()
        recentFileDao = database.recentFileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rows are account scoped and stale completion cannot remove newer intent`() = runTest {
        dao.upsertVersioned(
            CloudBookDeleteIntentEntity(
                accountId = "account-a",
                bookId = "book",
                type = "PDF",
                requestedAt = 10L,
            ),
        )
        dao.upsertVersioned(
            CloudBookDeleteIntentEntity(
                accountId = "account-b",
                bookId = "book",
                type = "EPUB",
                requestedAt = 11L,
            ),
        )

        assertEquals(1, dao.getByStates("account-a", CloudBookDeleteIntentEntity.WORKABLE_STATES).size)
        assertEquals(1, dao.getByStates("account-b", CloudBookDeleteIntentEntity.WORKABLE_STATES).size)

        assertEquals(1, dao.markTombstonePublished("account-a", "book", 10L))
        dao.upsertVersioned(
            CloudBookDeleteIntentEntity(
                accountId = "account-a",
                bookId = "book",
                type = "EPUB",
                requestedAt = 20L,
            ),
        )

        assertEquals(0, dao.removeIfVersion("account-a", "book", 10L))
        val current = dao.getByStates("account-a", CloudBookDeleteIntentEntity.WORKABLE_STATES).single()
        assertEquals(20L, current.requestedAt)
        assertEquals("EPUB", current.type)
        assertEquals(1, dao.removeIfVersion("account-a", "book", 20L))
        assertTrue(dao.getByStates("account-a", CloudBookDeleteIntentEntity.WORKABLE_STATES).isEmpty())
        assertEquals(1, dao.getByStates("account-b", CloudBookDeleteIntentEntity.WORKABLE_STATES).size)
    }

    @Test
    fun `local claim records generation and quarantine retains the intent`() = runTest {
        val intent = CloudBookDeleteIntentEntity(
            accountId = "account-a",
            bookId = "book-claimed",
            type = "PDF",
            requestedAt = 50L,
        )
        dao.upsertVersioned(intent)

        assertEquals(
            1,
            dao.claimLocalGeneration(
                accountId = intent.accountId,
                bookId = intent.bookId,
                requestedAt = intent.requestedAt,
                lastModifiedTimestamp = 40L,
                timestamp = 39L,
                fileContentModifiedTimestamp = 38L,
                fileSize = 37L,
                uriString = "content://book",
            ),
        )
        val claimed = dao.getByStates(
            intent.accountId,
            listOf(CloudBookDeleteIntentEntity.STATE_LOCAL_CLAIMED),
        ).single()
        assertTrue(claimed.localClaimed)
        assertEquals(40L, claimed.claimedLocalLastModifiedTimestamp)
        assertEquals("content://book", claimed.claimedLocalUriString)

        assertEquals(1, dao.quarantine(intent.accountId, intent.bookId, intent.requestedAt, "newer row"))
        assertEquals(
            "newer row",
            dao.getByStates(intent.accountId, listOf(CloudBookDeleteIntentEntity.STATE_QUARANTINED))
                .single()
                .lastError,
        )
    }

    @Test
    fun `database-only recovery removes exact row but leaves source and preserves newer row`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.filesDir, "books/cloud-delete-recovery-${System.nanoTime()}.epub")
            .apply {
                parentFile?.mkdirs()
                writeText("book")
            }
        val original = RecentFileEntity(
            bookId = "book-recovery",
            uriString = source.toURI().toString(),
            type = FileType.EPUB,
            displayName = "Recovery.epub",
            timestamp = 100L,
            coverImagePath = null,
            title = "Recovery",
            author = null,
            lastChapterIndex = null,
            lastPage = null,
            lastPositionCfi = null,
            progressPercentage = null,
            isRecent = true,
            isAvailable = true,
            lastModifiedTimestamp = 100L,
            isDeleted = false,
            locatorBlockIndex = null,
            locatorCharOffset = null,
            bookmarks = null,
            sourceFolderUri = null,
            isReflowPreferred = false,
            customName = null,
            highlights = null,
            fileSize = source.length(),
            fileContentModifiedTimestamp = 100L,
            seriesName = null,
            seriesIndex = null,
            description = null,
            folderTextMetadataParsed = false,
        )
        recentFileDao.insertOrUpdateFile(original)

        val repository = RecentFilesRepository(context, database)
        val generation = original.toRecentFileItem().cloudBookLocalGeneration()
        assertTrue(repository.removeCloudDeleteGenerationFromDatabase(original.bookId, generation))
        assertNull(recentFileDao.getFileByBookId(original.bookId))
        // Recovery intentionally does not call BookImporter or artifact
        // cleanup; an orphan sweeper can reclaim this after the delete is
        // authoritative and no newer incarnation references it.
        assertTrue(source.exists())

        val newer = original.copy(
            timestamp = 200L,
            lastModifiedTimestamp = 200L,
            fileContentModifiedTimestamp = 200L,
        )
        recentFileDao.insertOrUpdateFile(newer)
        assertFalse(repository.removeCloudDeleteGenerationFromDatabase(original.bookId, generation))
        assertEquals(newer, recentFileDao.getFileByBookId(original.bookId))
        assertTrue(source.exists())
        source.delete()
    }
}
