package com.aryan.reader.paginatedreader.data

import androidx.room.Room
import com.aryan.reader.paginatedreader.Page
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
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
    fun `processed chapters are isolated by style config hash`() = runTest {
        val firstPayload = ByteArray(950 * 1024) { 1 }
        val secondPayload = byteArrayOf(2, 3, 4)

        dao.insertProcessedChapters(
            listOf(
                ProcessedChapter(
                    bookId = "book",
                    chapterIndex = 0,
                    contentBlocksProto = firstPayload,
                    estimatedPageCount = 10,
                    styleConfigHash = 111
                )
            )
        )
        dao.insertProcessedChapters(
            listOf(
                ProcessedChapter(
                    bookId = "book",
                    chapterIndex = 0,
                    contentBlocksProto = secondPayload,
                    estimatedPageCount = 2,
                    styleConfigHash = 222
                )
            )
        )

        val firstCached = dao.getProcessedChapter("book", 0, 111)!!
        val secondCached = dao.getProcessedChapter("book", 0, 222)!!
        assertEquals(111, firstCached.styleConfigHash)
        assertEquals(222, secondCached.styleConfigHash)
        assertArrayEquals(firstPayload, firstCached.contentBlocksProto)
        assertArrayEquals(secondPayload, secondCached.contentBlocksProto)
    }

    @Test
    fun `delete and clear operations remove book chapters anchors and configuration cache`() = runTest {
        dao.insertProcessedBook(ProcessedBook("book", LATEST_PROCESSING_VERSION, 10))
        dao.insertProcessedChapters(
            listOf(ProcessedChapter("book", 0, byteArrayOf(1, 2, 3), estimatedPageCount = 2))
        )
        dao.insertAnchorIndices(listOf(AnchorIndexEntry("book", "anchor", 0, 99)))
        dao.insertConfigurationCache(ConfigurationCache("book", configHash = 123, chapterPageCounts = "0:2"))
        dao.insertPageCache(
            PageCacheEntry(
                bookId = "book",
                configHash = 123,
                chapterIndex = 0,
                processingVersion = LATEST_PROCESSING_VERSION,
                pageCacheVersion = LATEST_PAGE_CACHE_VERSION,
                contentVersion = 456,
                pageCount = 1,
                pagesProto = byteArrayOf(9, 8, 7)
            ),
            pageIndexEntries = listOf(
                PageIndexEntry(
                    bookId = "book",
                    configHash = 123,
                    chapterIndex = 0,
                    pageInChapter = 0,
                    firstBlockIndex = 1,
                    lastBlockIndex = 2,
                    firstTextBlockIndex = 1,
                    firstTextCharOffset = 0,
                    firstTextEndOffset = 10,
                    firstCfi = "/4/2",
                    anchors = "anchor"
                )
            )
        )

        dao.deleteEntireBookCache("book")

        assertNull(dao.getProcessedBook("book"))
        assertNull(dao.getProcessedChapter("book", 0))
        assertNull(dao.getAnchorIndex("book", "anchor"))
        assertNull(dao.getConfigurationCache("book", 123))
        assertNull(dao.getPageCache("book", 123, 0))
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

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `page cache round trips chunked measured pages and page index entries`() = runTest {
        val proto = ProtoBuf
        val pagesProto = proto.encodeToByteArray(listOf(Page(content = emptyList())))
        val largePayload = pagesProto + ByteArray(950 * 1024) { index -> (index % 127).toByte() }
        val entry = PageCacheEntry(
            bookId = "book",
            configHash = 321,
            chapterIndex = 2,
            processingVersion = LATEST_PROCESSING_VERSION,
            pageCacheVersion = LATEST_PAGE_CACHE_VERSION,
            contentVersion = 654,
            pageCount = 1,
            pagesProto = largePayload
        )
        val indexEntry = PageIndexEntry(
            bookId = "book",
            configHash = 321,
            chapterIndex = 2,
            pageInChapter = 0,
            firstBlockIndex = 4,
            lastBlockIndex = 9,
            firstTextBlockIndex = 4,
            firstTextCharOffset = 12,
            firstTextEndOffset = 80,
            firstCfi = "/4/2",
            anchors = "chapter-start"
        )

        dao.insertPageCache(entry, listOf(indexEntry))

        val cached = dao.getPageCache("book", 321, 2)!!
        val cachedIndex = dao.getPageIndexEntries("book", 321, 2)

        assertEquals(LATEST_PAGE_CACHE_VERSION, cached.pageCacheVersion)
        assertEquals(654, cached.contentVersion)
        assertArrayEquals(largePayload, cached.pagesProto)
        assertEquals(listOf(indexEntry), cachedIndex)
    }
}
