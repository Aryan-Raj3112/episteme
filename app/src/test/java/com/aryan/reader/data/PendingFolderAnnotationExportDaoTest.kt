package com.aryan.reader.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PendingFolderAnnotationExportDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PendingFolderAnnotationExportDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.pendingFolderAnnotationExportDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `completed old revision cannot clear a newer edit`() = runTest {
        dao.put(pending(revision = 1L))
        dao.put(pending(revision = 2L))

        assertEquals(0, dao.deleteRevision("book", revision = 1L))
        assertEquals(2L, dao.get("book")?.revision)
        assertEquals(1, dao.deleteRevision("book", revision = 2L))
        assertEquals(null, dao.get("book"))
    }

    @Test
    fun `attempt bookkeeping only mutates matching revision`() = runTest {
        dao.put(pending(revision = 2L))

        dao.recordAttempt("book", revision = 1L, attemptedAt = 50L)
        assertEquals(0, dao.get("book")?.attemptCount)
        dao.recordAttempt("book", revision = 2L, attemptedAt = 60L)

        val row = dao.get("book")
        assertNotNull(row)
        assertEquals(1, row?.attemptCount)
        assertEquals(60L, row?.lastAttemptAt)
    }

    private fun pending(revision: Long) = PendingFolderAnnotationExportEntity(
        bookId = "book",
        revision = revision,
        dirtySince = 10L,
        updatedAt = 20L,
        reason = "test",
    )
}
