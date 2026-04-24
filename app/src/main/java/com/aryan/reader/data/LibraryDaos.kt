package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelf(shelf: ShelfEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookShelfCrossRefs(crossRefs: List<BookShelfCrossRef>)

    @Query("SELECT * FROM shelves WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllActiveShelves(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM book_shelf_cross_ref")
    fun getAllBookShelfCrossRefs(): Flow<List<BookShelfCrossRef>>

    @Query("DELETE FROM book_shelf_cross_ref WHERE shelfId = :shelfId AND bookId IN (:bookIds)")
    suspend fun removeBooksFromShelf(shelfId: String, bookIds: List<String>)

    @Query("UPDATE shelves SET isDeleted = 1, updatedAt = :timestamp WHERE id = :shelfId")
    suspend fun markShelfAsDeleted(shelfId: String, timestamp: Long)

    @Query("UPDATE shelves SET name = :newName, updatedAt = :timestamp WHERE id = :shelfId")
    suspend fun updateShelfName(shelfId: String, newName: String, timestamp: Long)

    @Query("SELECT * FROM shelves WHERE id = :shelfId")
    suspend fun getShelfById(shelfId: String): ShelfEntity?

    @Query("SELECT * FROM book_shelf_cross_ref WHERE shelfId = :shelfId")
    suspend fun getCrossRefsForShelf(shelfId: String): List<BookShelfCrossRef>
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTagCrossRef(crossRef: BookTagCrossRef)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM book_tag_cross_ref")
    fun getAllBookTagCrossRefs(): Flow<List<BookTagCrossRef>>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int

    @Query("DELETE FROM book_tag_cross_ref WHERE tagId = :tagId AND bookId = :bookId")
    suspend fun removeTagFromBook(tagId: String, bookId: String)
}
