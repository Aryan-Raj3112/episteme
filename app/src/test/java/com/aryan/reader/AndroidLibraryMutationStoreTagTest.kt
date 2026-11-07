package com.aryan.reader

import androidx.room.Room
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.data.TagEntity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
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
 * `book_tag_cross_ref` has two foreign keys and both can legitimately fail:
 *
 * - `bookId` -> `recent_files(bookId)`: tag assignments arrive from cloud sync and from
 *   `LibraryMutationController` with ids filtered by `syncableBookIds`, which is built from
 *   local AND remote book metadata and therefore admits books this device never materialised.
 *   Before the fix each such row aborted the insert with `SQLiteConstraintException` 787.
 * - `tagId` -> `tags(id)`: a tag can be deleted (locally, or by a remote deletion applied
 *   between the caller reading the tag list and the insert), and `seedTagsIfEmpty` only ever
 *   seeds an empty table.
 *
 * This is the same defect #68 fixed for `book_shelf_cross_ref`; #68's "reopen if" named this
 * table specifically. Runs against a real Room database because the constraint under test is
 * enforced by SQLite, and the store resolves its database through the `AppDatabase` singleton.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidLibraryMutationStoreTagTest {

    private lateinit var db: AppDatabase
    private lateinit var store: AndroidLibraryMutationStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
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
    fun `assigning a tag skips books with no local row instead of violating the foreign key`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("present", 0))
        db.tagDao().insertTag(tag("t"))

        // "ghost" is remote-only: it is in syncableBookIds but never became a recent_files row.
        store.assignTagToBooks("t", setOf("present", "ghost"))

        assertEquals(listOf("present"), storedBookIds())
    }

    @Test
    fun `assigning a tag whose every book is unknown still succeeds without throwing`() = runTest {
        db.tagDao().insertTag(tag("t"))

        store.assignTagToBooks("t", setOf("ghost-a", "ghost-b"))

        assertTrue(storedBookIds().isEmpty())
        // The tag itself must survive: only the unusable refs are dropped.
        assertEquals("t", db.tagDao().getTagId("t"))
    }

    @Test
    fun `assigning an unknown tag drops the refs instead of violating the foreign key`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("present", 0))

        store.assignTagToBooks("never-created", setOf("present"))

        assertTrue(storedBookIds().isEmpty())
    }

    @Test
    fun `assigning a tag keeps every book that does have a local row`() = runTest {
        db.recentFileDao().insertOrUpdateFiles(listOf(entity("a", 0), entity("b", 1), entity("c", 2)))
        db.tagDao().insertTag(tag("t"))

        store.assignTagToBooks("t", setOf("a", "b", "c"))

        assertEquals(setOf("a", "b", "c"), storedBookIds().toSet())
    }

    @Test
    fun `re-assigning the same tag to the same books is idempotent`() = runTest {
        db.recentFileDao().insertOrUpdateFile(entity("a", 0))
        db.tagDao().insertTag(tag("t"))

        store.assignTagToBooks("t", setOf("a"))
        store.assignTagToBooks("t", setOf("a"))

        assertEquals(listOf("a"), storedBookIds())
    }

    @Test
    fun `tombstoned books still count as existing rows`() = runTest {
        // Soft-deleted books keep their recent_files row, so a tag may legitimately reference
        // one. Only a row that is truly absent must be dropped.
        db.recentFileDao().insertOrUpdateFile(entity("tombstone", 0))
        db.recentFileDao().markAsDeleted(listOf("tombstone"), timestamp = 5_000L)
        db.tagDao().insertTag(tag("t"))

        store.assignTagToBooks("t", setOf("tombstone"))

        assertEquals(listOf("tombstone"), storedBookIds())
    }

    private suspend fun storedBookIds(): List<String> =
        db.tagDao().getAllBookTagCrossRefs().first().map { it.bookId }

    private fun tag(id: String): TagEntity = TagEntity(id = id, name = "Tag $id", color = null, createdAt = 1L)

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
