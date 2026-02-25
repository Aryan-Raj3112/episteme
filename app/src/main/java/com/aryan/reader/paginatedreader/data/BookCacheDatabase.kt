/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Dao
abstract class BookCacheDao {

    // --- Book Operations ---
    @Query("SELECT * FROM processed_books WHERE bookId = :bookId")
    abstract suspend fun getProcessedBook(bookId: String): ProcessedBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProcessedBook(book: ProcessedBook)

    @Query("DELETE FROM processed_books WHERE bookId = :bookId")
    abstract suspend fun deleteBook(bookId: String)

    @Query("DELETE FROM processed_books")
    abstract suspend fun clearProcessedBooks()


    // --- Chapter Operations (Internal Raw Access) ---

    @Query("SELECT * FROM processed_chapter_metadata WHERE book_id = :bookId AND chapter_index = :chapterIndex")
    protected abstract suspend fun getChapterMetadata(bookId: String, chapterIndex: Int): ProcessedChapterMetadata?

    @Query("SELECT chunk_data FROM processed_chapter_chunks WHERE book_id = :bookId AND chapter_index = :chapterIndex ORDER BY chunk_index ASC")
    protected abstract suspend fun getChapterChunks(bookId: String, chapterIndex: Int): List<ByteArray>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChapterMetadata(metadata: ProcessedChapterMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChapterChunks(chunks: List<ProcessedChapterChunk>)

    @Query("DELETE FROM processed_chapter_metadata WHERE book_id = :bookId")
    protected abstract suspend fun deleteChapterMetadataForBook(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAnchorIndices(anchors: List<AnchorIndexEntry>)

    @Query("SELECT * FROM anchor_index WHERE bookId = :bookId AND anchorId = :anchorId LIMIT 1")
    abstract suspend fun getAnchorIndex(bookId: String, anchorId: String): AnchorIndexEntry?

    @Query("DELETE FROM anchor_index WHERE bookId = :bookId")
    abstract suspend fun deleteAnchorsForBook(bookId: String)

    @Transaction
    open suspend fun getProcessedChapter(bookId: String, chapterIndex: Int): ProcessedChapter? {
        val metadata = getChapterMetadata(bookId, chapterIndex) ?: return null
        val chunks = getChapterChunks(bookId, chapterIndex)

        if (chunks.isEmpty()) {
            return ProcessedChapter(bookId, chapterIndex, ByteArray(0), metadata.estimatedPageCount)
        }

        val totalSize = chunks.sumOf { it.size }
        val mergedData = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, mergedData, offset, chunk.size)
            offset += chunk.size
        }

        return ProcessedChapter(
            bookId = bookId,
            chapterIndex = chapterIndex,
            contentBlocksProto = mergedData,
            estimatedPageCount = metadata.estimatedPageCount
        )
    }

    @Transaction
    open suspend fun insertProcessedChapters(chapters: List<ProcessedChapter>) {
        @Suppress("LocalVariableName") val CHUNK_SIZE = 900 * 1024

        for (chapter in chapters) {
            val metadata = ProcessedChapterMetadata(
                bookId = chapter.bookId,
                chapterIndex = chapter.chapterIndex,
                estimatedPageCount = chapter.estimatedPageCount
            )
            insertChapterMetadata(metadata)

            val fullData = chapter.contentBlocksProto
            if (fullData.isEmpty()) continue

            val chunks = ArrayList<ProcessedChapterChunk>()
            var offset = 0
            var chunkIndex = 0

            while (offset < fullData.size) {
                val end = (offset + CHUNK_SIZE).coerceAtMost(fullData.size)
                val chunkBytes = fullData.copyOfRange(offset, end)

                chunks.add(
                    ProcessedChapterChunk(
                        bookId = chapter.bookId,
                        chapterIndex = chapter.chapterIndex,
                        chunkIndex = chunkIndex,
                        chunkData = chunkBytes
                    )
                )
                offset = end
                chunkIndex++
            }
            insertChapterChunks(chunks)
        }
    }

    @Transaction
    open suspend fun deleteChaptersForBook(bookId: String) {
        deleteChapterMetadataForBook(bookId)
    }

    @Transaction
    open suspend fun clearProcessedChapters() {
        deleteAllChapterMetadata()
    }

    @Query("DELETE FROM processed_chapter_metadata")
    protected abstract suspend fun deleteAllChapterMetadata()

    @Transaction
    open suspend fun clearAllCache() {
        clearProcessedBooks()
        clearProcessedChapters()
    }

    @Query("SELECT * FROM configuration_cache WHERE bookId = :bookId AND configHash = :configHash")
    abstract suspend fun getConfigurationCache(bookId: String, configHash: Int): ConfigurationCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertConfigurationCache(cache: ConfigurationCache)
}

@Database(
    entities = [
        ProcessedBook::class,
        ProcessedChapterMetadata::class,
        ProcessedChapterChunk::class,
        ConfigurationCache::class,
        AnchorIndexEntry::class
    ],
    version = 6,
    exportSchema = false
)
abstract class BookCacheDatabase : RoomDatabase() {
    abstract fun bookCacheDao(): BookCacheDao

    companion object {
        @Volatile
        private var INSTANCE: BookCacheDatabase? = null

        fun getDatabase(context: Context): BookCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookCacheDatabase::class.java,
                    "book_cache_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}