package com.aryan.reader.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShelfDaoSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ShelfDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.shelfDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sync shelf query includes manual tombstones but excludes smart shelves`() = runTest {
        dao.insertShelf(ShelfEntity("manual", "Manual", createdAt = 1L, updatedAt = 1L))
        dao.insertShelf(ShelfEntity("deleted", "Deleted", createdAt = 2L, updatedAt = 2L, isDeleted = true))
        dao.insertShelf(ShelfEntity("smart", "Smart", isSmart = true, createdAt = 3L, updatedAt = 3L))

        assertEquals(listOf("deleted", "manual"), dao.getAllUserShelvesForSync().map { it.id }.sorted())
    }

    @Test
    fun `touch shelf advances the timestamp used by shelf sync`() = runTest {
        dao.insertShelf(ShelfEntity("manual", "Manual", createdAt = 1L, updatedAt = 1L))

        dao.touchShelf("manual", 9L)

        assertEquals(9L, dao.getShelfById("manual")!!.updatedAt)
    }
}
