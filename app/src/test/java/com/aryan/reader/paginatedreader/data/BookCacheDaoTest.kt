package com.aryan.reader.paginatedreader.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BookCacheDaoTest {

    private lateinit var db: BookCacheDatabase
    private lateinit var dao: BookCacheDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BookCacheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.bookCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `processed chapters round trip empty and chunked proto payloads`() = runTest {
        val largePayload = ByteArray(950 * 1024) { index -> (index % 251).toByte() }
        val chapters = listOf(
            ProcessedChapter(
                bookId = "book",
                chapterIndex = 0,
                contentBlocksProto = ByteArray(0),
                estimatedPageCount = 1
            ),
            ProcessedChapter(
                bookId = "book",
                chapterIndex = 1,
                contentBlocksProto = largePayload,
                estimatedPageCount = 12
            )
        )

        dao.insertProcessedChapters(chapters)

        val empty = dao.getProcessedChapter("book", 0)!!
        val large = dao.getProcessedChapter("book", 1)!!
        assertEquals(1, empty.estimatedPageCount)
        assertEquals(0, empty.contentBlocksProto.size)
        assertEquals(12, large.estimatedPageCount)
        assertArrayEquals(largePayload, large.contentBlocksProto)
    }

    @Test
    fun `delete and clear operations remove book chapters anchors and configuration cache`() = runTest {
        dao.insertProcessedBook(ProcessedBook("book", LATEST_PROCESSING_VERSION, 10))
        dao.insertProcessedChapters(
            listOf(ProcessedChapter("book", 0, byteArrayOf(1, 2, 3), estimatedPageCount = 2))
        )
        dao.insertAnchorIndices(listOf(AnchorIndexEntry("book", "anchor", 0, 99)))
        dao.insertConfigurationCache(ConfigurationCache("book", configHash = 123, chapterPageCounts = "0:2"))

        dao.deleteEntireBookCache("book")

        assertNull(dao.getProcessedBook("book"))
        assertNull(dao.getProcessedChapter("book", 0))
        assertNull(dao.getAnchorIndex("book", "anchor"))
        assertNull(dao.getConfigurationCache("book", 123))
    }

    @Test
    fun `configuration cleanup keeps only the three most recent hashes for a book`() = runTest {
        (1..5).forEach { hash ->
            dao.insertConfigurationCache(ConfigurationCache("book", hash, "0:$hash"))
        }

        dao.cleanupOldConfigurations("book")

        assertNull(dao.getConfigurationCache("book", 1))
        assertNull(dao.getConfigurationCache("book", 2))
        assertEquals("0:3", dao.getConfigurationCache("book", 3)?.chapterPageCounts)
        assertEquals("0:4", dao.getConfigurationCache("book", 4)?.chapterPageCounts)
        assertEquals("0:5", dao.getConfigurationCache("book", 5)?.chapterPageCounts)
    }
}
