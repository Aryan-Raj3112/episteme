package com.aryan.reader

import androidx.room.Room
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.data.ShelfDao
import com.aryan.reader.data.ShelfEntity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `book_shelf_cross_ref.bookId` is a foreign key into `recent_files(bookId)`, so a
 * cross-ref for a book with no local row aborts the whole insert batch with
 * `SQLiteConstraintException` (787). Cloud sync reaches this because it filters shelf
 * book ids with `syncableBookIds`, which is built from local AND remote book metadata
 * and therefore admits books this device never materialised -- reliably a remote-only
 * book that is itself deleted, since the book merge skips inserting it while the shelf
 * document still lists it. See docs/crashlytics-triage.md#68.
 *
 * These run against a real Room database (not mocks) because the constraint being
 * tested is enforced by SQLite, and the store resolves its DB through the
 * `AppDatabase.getDatabase` singleton.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidLibraryMutationStoreShelfTest {

    private lateinit var db: AppDatabase
    private lateinit var shelfDao: ShelfDao
    private lateinit var store: AndroidLibraryMutationStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        shelfDao = db.shelfDao()
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns db
        store = AndroidLibraryMutationStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
        db.close()
    }

    @Test
    fun `remote shelf skips books with no local row instead of violating the foreign key`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("present", 0))
        shelfDao.insertShelf(ShelfEntity(id = "s", name = "S", createdAt = 1L, updatedAt = 1L))

        // "ghost" is remote-only and deleted: it is in syncableBookIds but never became a
        // recent_files row. Before the fix this threw SQLiteConstraintException.
        val applied = store.applyRemoteShelf(
            shelfId = "s",
            name = "S",
            bookIds = listOf("present", "ghost"),
            timestamp = 5L,
            isDeleted = false,
        )

        assertTrue(applied)
        assertEquals(listOf("present"), shelfDao.getCrossRefsForShelf("s").map { it.bookId })
    }

    @Test
    fun `remote shelf whose every book is unknown still applies without throwing`() = runTest {
        val applied = store.applyRemoteShelf(
            shelfId = "fresh",
            name = "Fresh",
            bookIds = listOf("ghost-a", "ghost-b"),
            timestamp = 5L,
            isDeleted = false,
        )

        assertTrue(applied)
        assertTrue(shelfDao.getCrossRefsForShelf("fresh").isEmpty())
        // The shelf itself must still land; only the unusable refs are dropped.
        assertEquals("Fresh", shelfDao.getShelfById("fresh")?.name)
    }

    @Test
    fun `remote shelf keeps every book that does have a local row`() = runTest {
        db.recentFileDao().insertOrUpdateFiles(
            listOf(entity("a", 0), entity("b", 1), entity("c", 2))
        )

        store.applyRemoteShelf(
            shelfId = "full",
            name = "Full",
            bookIds = listOf("a", "b", "c"),
            timestamp = 5L,
            isDeleted = false,
        )

        assertEquals(
            setOf("a", "b", "c"),
            shelfDao.getCrossRefsForShelf("full").map { it.bookId }.toSet(),
        )
    }

    @Test
    fun `remote shelf replaces prior contents and still drops unknown books`() = runTest {
        db.recentFileDao().insertOrUpdateFiles(listOf(entity("a", 0), entity("b", 1)))
        store.applyRemoteShelf("full", "Full", listOf("a", "b"), 5L, isDeleted = false)

        store.applyRemoteShelf("full", "Full", listOf("b", "ghost"), 9L, isDeleted = false)

        assertEquals(listOf("b"), shelfDao.getCrossRefsForShelf("full").map { it.bookId })
    }

    @Test
    fun `smart shelves are not overwritten by remote application`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("a", 0))
        shelfDao.insertShelf(
            ShelfEntity(id = "smart", name = "Smart", isSmart = true, createdAt = 1L, updatedAt = 1L)
        )

        val applied = store.applyRemoteShelf("smart", "Hijack", listOf("a"), 5L, isDeleted = false)

        assertFalse(applied)
        assertEquals("Smart", shelfDao.getShelfById("smart")?.name)
        assertTrue(shelfDao.getCrossRefsForShelf("smart").isEmpty())
    }

    @Test
    fun `deleted remote shelf applies no cross refs even for books that exist`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("a", 0))

        store.applyRemoteShelf("gone", "Gone", listOf("a"), 5L, isDeleted = true)

        assertTrue(shelfDao.getCrossRefsForShelf("gone").isEmpty())
        assertEquals(true, shelfDao.getShelfById("gone")!!.isDeleted)
    }

    @Test
    fun `adding books to a shelf drops unknown ids instead of violating the foreign key`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("present", 0))
        shelfDao.insertShelf(ShelfEntity(id = "s", name = "S", createdAt = 1L, updatedAt = 1L))

        store.addBooksToShelf("s", setOf("present", "ghost"))

        assertEquals(listOf("present"), shelfDao.getCrossRefsForShelf("s").map { it.bookId })
    }

    @Test
    fun `adding books to a shelf still advances the timestamp when every id is unknown`() = runTest {
        shelfDao.insertShelf(ShelfEntity(id = "s", name = "S", createdAt = 1L, updatedAt = 1L))

        store.addBooksToShelf("s", setOf("ghost"))

        assertTrue(shelfDao.getCrossRefsForShelf("s").isEmpty())
        // Dropping the refs must not skip touchShelf: the shelf is still touched so the
        // edit propagates to other devices on the next sync.
        assertTrue(shelfDao.getShelfById("s")!!.updatedAt > 1L)
    }

    @Test
    fun `tombstoned books still count as existing rows`() = runTest {
        // Soft-deleted books keep their recent_files row, so a shelf may legitimately
        // reference one. Only a row that is truly absent must be dropped.
        db.recentFileDao().insertOrUpdateFile(entity("tombstone", 0))
        db.recentFileDao().markAsDeleted(listOf("tombstone"), timestamp = 5_000L)

        store.applyRemoteShelf("t", "T", listOf("tombstone"), 5L, isDeleted = false)

        assertEquals(listOf("tombstone"), shelfDao.getCrossRefsForShelf("t").map { it.bookId })
    }

    private fun entity(bookId: String, index: Int): RecentFileEntity =
        RecentFileEntity(
            bookId = bookId,
            uriString = "content://books/$index",
            type = com.aryan.reader.FileType.EPUB,
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