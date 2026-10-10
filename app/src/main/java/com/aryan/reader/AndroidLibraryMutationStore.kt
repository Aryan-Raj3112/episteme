package com.aryan.reader

import android.content.Context
import androidx.room.withTransaction
import androidx.core.content.edit
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.LibraryMutationStore
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Room-backed execution adapter; mutation policy and sequencing live in shared. */
internal class AndroidLibraryMutationStore(
    private val context: Context,
) : LibraryMutationStore {
    private val database = AppDatabase.getDatabase(context)
    private val shelfDao = database.shelfDao()
    private val tagDao = database.tagDao()

    val activeShelvesFlow = shelfDao.getAllActiveShelves()
    val shelfCrossRefsFlow = shelfDao.getAllBookShelfCrossRefs()
    val tagsFlow = tagDao.getAllTags()
    val tagCrossRefsFlow = tagDao.getAllBookTagCrossRefs()

    override suspend fun createShelf(record: ShelfRecord, createdAt: Long) {
        withContext(Dispatchers.IO) { shelfDao.insertShelf(record.toShelfEntity(createdAt)) }
    }

    override suspend fun addBooksToShelf(shelfId: String, bookIds: Set<String>) {
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val crossRefs = bookShelfCrossRefsForExistingBooks(shelfId, bookIds, timestamp)
            if (crossRefs.isNotEmpty()) {
                shelfDao.insertBookShelfCrossRefs(crossRefs)
            }
            shelfDao.touchShelf(shelfId, timestamp)
        }
    }

    override suspend fun renameShelf(shelfId: String, name: String) {
        withContext(Dispatchers.IO) { shelfDao.updateShelfName(shelfId, name, System.currentTimeMillis()) }
    }

    override suspend fun deleteShelf(shelfId: String) {
        withContext(Dispatchers.IO) { shelfDao.markShelfAsDeleted(shelfId, System.currentTimeMillis()) }
    }

    override suspend fun removeBooksFromShelf(shelfId: String, bookIds: Set<String>) {
        withContext(Dispatchers.IO) {
            shelfDao.removeBooksFromShelf(shelfId, bookIds.toList())
            shelfDao.touchShelf(shelfId, System.currentTimeMillis())
        }
    }

    override suspend fun createTag(tag: Tag, createdAt: Long) {
        withContext(Dispatchers.IO) { tagDao.insertTag(tag.toTagEntity(createdAt)) }
    }

    override suspend fun assignTagToBooks(tagId: String, bookIds: Set<String>) {
        withContext(Dispatchers.IO) {
            bookIds.forEach { tagDao.insertBookTagCrossRef(BookTagCrossRef(it, tagId)) }
        }
    }

    override suspend fun removeTagFromBooks(tagId: String, bookIds: Set<String>) {
        withContext(Dispatchers.IO) {
            bookIds.forEach { tagDao.removeTagFromBook(tagId, it) }
        }
    }

    override suspend fun deleteTag(tagId: String) {
        withContext(Dispatchers.IO) { tagDao.deleteTag(tagId) }
    }

    suspend fun seedTagsIfEmpty(tags: List<TagEntity>) = withContext(Dispatchers.IO) {
        if (tags.isNotEmpty() && tagDao.getTagCount() == 0) tagDao.insertTags(tags)
    }

    suspend fun applyRemoteShelf(
        shelfId: String,
        name: String,
        bookIds: List<String>,
        timestamp: Long,
        isDeleted: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = shelfDao.getShelfById(shelfId)
            if (existing?.isSmart == true) return@withTransaction false
            shelfDao.insertShelf(
                ShelfEntity(
                    id = shelfId,
                    name = name,
                    createdAt = existing?.createdAt ?: timestamp,
                    updatedAt = timestamp,
                    isDeleted = isDeleted,
                ),
            )
            shelfDao.clearBooksFromShelf(shelfId)
            if (!isDeleted && bookIds.isNotEmpty()) {
                val crossRefs = bookShelfCrossRefsForExistingBooks(shelfId, bookIds, timestamp)
                if (crossRefs.isNotEmpty()) {
                    shelfDao.insertBookShelfCrossRefs(crossRefs)
                }
            }
            true
        }
    }

    /**
     * Cross-refs for [bookIds] that actually have a `recent_files` row.
     *
     * `book_shelf_cross_ref.bookId` is a foreign key into `recent_files(bookId)`, so a
     * cross-ref for a book with no local row is a hard FK violation that aborts the
     * entire insert batch, not just that row. `OnConflictStrategy.IGNORE` does not help:
     * it absorbs primary-key collisions, not referential failures.
     *
     * Callers cannot filter this out themselves. Cloud sync filters with
     * `syncableBookIds`, which is built from BOTH local and remote book metadata
     * (`MainViewModel.performLibrarySync`), so it admits ids this device never
     * materialised -- most reliably a remote-only book that is itself deleted: the book
     * merge deliberately skips inserting it, yet the shelf document still lists it.
     *
     * Re-checking here, at the one place that owns this table, keeps every writer to the
     * same rule. Uses the narrow `SELECT bookId` rather than `SELECT *`
     * (docs/crashlytics-triage.md#54).
     */
    private suspend fun bookShelfCrossRefsForExistingBooks(
        shelfId: String,
        bookIds: Collection<String>,
        timestamp: Long,
    ): List<BookShelfCrossRef> {
        if (bookIds.isEmpty()) return emptyList()
        val existingBookIds = database.recentFileDao().getAllBookIds().toHashSet()
        return bookIds.filter { it in existingBookIds }.map { BookShelfCrossRef(it, shelfId, timestamp) }
    }

    suspend fun migrateLegacyShelves() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_shelves_migrated_to_room", false)) return@withContext

        val shelfNames = prefs.getStringSet("shelf_names", emptySet()).orEmpty()
        if (shelfNames.isEmpty()) {
            prefs.edit { putBoolean("is_shelves_migrated_to_room", true) }
            return@withContext
        }

        shelfNames.forEach { name ->
            val shelfId = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
            val timestamp = prefs.getLong("shelf_timestamp_$name", System.currentTimeMillis())
            shelfDao.insertShelf(
                ShelfEntity(
                    id = shelfId,
                    name = name,
                    isSmart = false,
                    smartRulesJson = null,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    isDeleted = prefs.getBoolean("shelf_deleted_$name", false),
                ),
            )
            val crossRefs = bookShelfCrossRefsForExistingBooks(
                shelfId,
                prefs.getStringSet("shelf_content_$name", emptySet()).orEmpty(),
                timestamp,
            )
            if (crossRefs.isNotEmpty()) shelfDao.insertBookShelfCrossRefs(crossRefs)
        }
        prefs.edit { putBoolean("is_shelves_migrated_to_room", true) }
    }
}
