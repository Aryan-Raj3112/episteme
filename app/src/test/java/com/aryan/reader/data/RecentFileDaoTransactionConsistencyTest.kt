package com.aryan.reader.data

import androidx.room.Room
import androidx.room.Transaction
import com.aryan.reader.FileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression tests for the CursorWindow crash reported by Crashlytics:
 * "Couldn't read row 730, col 0 from CursorWindow" thrown from
 * RecentFileDao.getRecentFiles' row-conversion lambda.
 *
 * The crash happened because the full-library projection spans multiple CursorWindows
 * and Room re-executes the query on every window refill. The query ran without a
 * transaction, so a concurrent write that shrinks the result set (markAsDeleted from
 * sync/cloud deletes, deleteFilePermanently, clearAll during restore) invalidated the
 * cached row count between refills and the iteration landed past the last row.
 *
 * The fix wraps the library queries in a read transaction (@Transaction) so every
 * window refill observes a single consistent snapshot.
 */
@RunWith(RobolectricTestRunner::class)
class RecentFileDaoTransactionConsistencyTest {

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
    fun `library queries are transactional so window refills cannot observe a torn result set`() {
        // @Transaction is CLASS-retention so it is invisible to reflection; read the bytecode.
        val classBytes = RecentFileDao::class.java.getResourceAsStream(
            "RecentFileDao.class"
        )?.readBytes() ?: error("RecentFileDao.class not found on classpath")
        val node = ClassNode().also { ClassReader(classBytes).accept(it, 0) }

        fun transactionAnnotation(methodName: String, descriptor: String) =
            node.methods
                .first { it.name == methodName && it.desc == descriptor }
                .let { method -> method.visibleAnnotations.orEmpty() + method.invisibleAnnotations.orEmpty() }
                .any { it.desc == "Landroidx/room/Transaction;" }

        assertTrue(
            "getRecentFiles must be @Transaction",
            transactionAnnotation("getRecentFiles", "()Lkotlinx/coroutines/flow/Flow;")
        )
        assertTrue(
            "getRecentFilesList must be @Transaction",
            transactionAnnotation("getRecentFilesList", "(I)Ljava/util/List;")
        )
    }

    @Test
    fun `transactional library query returns a consistent non-deleted snapshot ordered by recency`() = runTest {
        val entities = (1..300).map { index ->
            recentFileEntity(
                bookId = "book-$index",
                timestamp = index.toLong()
            )
        }
        dao.insertOrUpdateFiles(entities)
        dao.markAsDeleted(
            bookIds = entities.filter { it.timestamp % 3L == 0L }.map { it.bookId },
            timestamp = 99_999L
        )

        val files = dao.getRecentFiles().first()

        assertEquals(200, files.size)
        assertEquals(files.map { it.timestamp }, files.map { it.timestamp }.sortedDescending())
        assertEquals(
            entities.filter { it.timestamp % 3L != 0L }.map { it.bookId }.toSet(),
            files.map { it.bookId }.toSet()
        )

        val limited = dao.getRecentFilesList(5)
        assertEquals(5, limited.size)
        assertTrue(limited.zipWithNext().all { (previous, next) -> previous.timestamp > next.timestamp })
    }

    private fun recentFileEntity(
        bookId: String,
        timestamp: Long
    ): RecentFileEntity = RecentFileEntity(
        bookId = bookId,
        uriString = "content://books/$bookId",
        type = FileType.EPUB,
        displayName = "$bookId.epub",
        timestamp = timestamp,
        coverImagePath = null,
        title = bookId,
        author = null,
        lastChapterIndex = null,
        lastPage = null,
        lastPositionCfi = null,
        progressPercentage = null,
        isRecent = true,
        isAvailable = true,
        lastModifiedTimestamp = timestamp,
        isDeleted = false,
        locatorBlockIndex = null,
        locatorCharOffset = null,
        bookmarks = null,
        sourceFolderUri = null,
        isReflowPreferred = false,
        customName = null,
        highlights = null,
        fileSize = 0L,
        fileContentModifiedTimestamp = 0L,
        seriesName = null,
        seriesIndex = null,
        description = null,
        folderTextMetadataParsed = false,
        folderCoverMetadataParsed = false
    )
}
