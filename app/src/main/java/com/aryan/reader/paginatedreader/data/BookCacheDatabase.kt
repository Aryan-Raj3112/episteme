/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

    @Query("SELECT * FROM processed_chapter_metadata WHERE book_id = :bookId AND chapter_index = :chapterIndex AND style_config_hash = :styleConfigHash")
    protected abstract suspend fun getChapterMetadata(bookId: String, chapterIndex: Int, styleConfigHash: Int): ProcessedChapterMetadata?

    @Query("SELECT * FROM processed_chapter_metadata WHERE book_id = :bookId AND chapter_index = :chapterIndex ORDER BY rowid DESC LIMIT 1")
    protected abstract suspend fun getAnyChapterMetadata(bookId: String, chapterIndex: Int): ProcessedChapterMetadata?

    @Query("SELECT chunk_data FROM processed_chapter_chunks WHERE book_id = :bookId AND chapter_index = :chapterIndex AND style_config_hash = :styleConfigHash ORDER BY chunk_index ASC")
    protected abstract suspend fun getChapterChunks(bookId: String, chapterIndex: Int, styleConfigHash: Int): List<ByteArray>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChapterMetadata(metadata: ProcessedChapterMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChapterChunks(chunks: List<ProcessedChapterChunk>)

    @Query("DELETE FROM processed_chapter_metadata WHERE book_id = :bookId")
    protected abstract suspend fun deleteChapterMetadataForBook(bookId: String)

    @Query("DELETE FROM processed_chapter_chunks WHERE book_id = :bookId AND chapter_index = :chapterIndex AND style_config_hash = :styleConfigHash")
    protected abstract suspend fun deleteChapterChunksForChapter(bookId: String, chapterIndex: Int, styleConfigHash: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAnchorIndices(anchors: List<AnchorIndexEntry>)

    @Query("SELECT * FROM anchor_index WHERE bookId = :bookId AND anchorId = :anchorId LIMIT 1")
    abstract suspend fun getAnchorIndex(bookId: String, anchorId: String): AnchorIndexEntry?

    @Query("DELETE FROM anchor_index WHERE bookId = :bookId")
    abstract suspend fun deleteAnchorsForBook(bookId: String)

    @Transaction
    open suspend fun getProcessedChapter(bookId: String, chapterIndex: Int, styleConfigHash: Int? = null): ProcessedChapter? {
        val metadata = if (styleConfigHash == null) {
            getAnyChapterMetadata(bookId, chapterIndex)
        } else {
            getChapterMetadata(bookId, chapterIndex, styleConfigHash)
        } ?: return null
        val chunks = getChapterChunks(bookId, chapterIndex, metadata.styleConfigHash)

        if (chunks.isEmpty()) {
            return ProcessedChapter(bookId, chapterIndex, ByteArray(0), metadata.estimatedPageCount, metadata.styleConfigHash)
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
            estimatedPageCount = metadata.estimatedPageCount,
            styleConfigHash = metadata.styleConfigHash
        )
    }

    @Transaction
    open suspend fun insertProcessedChapters(chapters: List<ProcessedChapter>) {
        @Suppress("LocalVariableName") val CHUNK_SIZE = 900 * 1024

        for (chapter in chapters) {
            val metadata = ProcessedChapterMetadata(
                bookId = chapter.bookId,
                chapterIndex = chapter.chapterIndex,
                estimatedPageCount = chapter.estimatedPageCount,
                styleConfigHash = chapter.styleConfigHash
            )
            deleteChapterChunksForChapter(chapter.bookId, chapter.chapterIndex, chapter.styleConfigHash)
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
                        styleConfigHash = chapter.styleConfigHash,
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

    @Query("DELETE FROM configuration_cache WHERE bookId = :bookId")
    abstract suspend fun deleteConfigurationCacheForBook(bookId: String)

    @Query("DELETE FROM page_cache_metadata WHERE book_id = :bookId")
    protected abstract suspend fun deletePageCacheMetadataForBook(bookId: String)

    @Query("DELETE FROM page_cache_metadata WHERE book_id = :bookId AND config_hash = :configHash AND chapter_index = :chapterIndex")
    protected abstract suspend fun deletePageCacheMetadataForChapter(bookId: String, configHash: Int, chapterIndex: Int)

    @Transaction
    open suspend fun deleteEntireBookCache(bookId: String) {
        deleteBook(bookId)
        deleteChaptersForBook(bookId)
        deleteAnchorsForBook(bookId)
        deleteConfigurationCacheForBook(bookId)
        deletePageCacheMetadataForBook(bookId)
    }

    @Query("DELETE FROM anchor_index")
    abstract suspend fun clearAnchors()

    @Query("DELETE FROM configuration_cache")
    abstract suspend fun clearConfigurationCache()

    @Query("DELETE FROM page_cache_metadata")
    protected abstract suspend fun clearPageCacheMetadata()

    @Transaction
    open suspend fun clearAllCache() {
        clearProcessedBooks()
        clearProcessedChapters()
        clearAnchors()
        clearConfigurationCache()
        clearPageCacheMetadata()
    }

    @Query("SELECT * FROM configuration_cache WHERE bookId = :bookId AND configHash = :configHash")
    abstract suspend fun getConfigurationCache(bookId: String, configHash: Int): ConfigurationCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertConfigurationCache(cache: ConfigurationCache)

    @Query("""
        DELETE FROM configuration_cache 
        WHERE bookId = :bookId AND configHash NOT IN (
            SELECT configHash FROM configuration_cache 
            WHERE bookId = :bookId 
            ORDER BY rowid DESC LIMIT 3
        )
    """)
    abstract suspend fun cleanupOldConfigurations(bookId: String)

    @Query("SELECT * FROM page_cache_metadata WHERE book_id = :bookId AND config_hash = :configHash AND chapter_index = :chapterIndex")
    protected abstract suspend fun getPageCacheMetadata(bookId: String, configHash: Int, chapterIndex: Int): PageCacheMetadata?

    @Query("SELECT chunk_data FROM page_cache_chunks WHERE book_id = :bookId AND config_hash = :configHash AND chapter_index = :chapterIndex ORDER BY chunk_index ASC")
    protected abstract suspend fun getPageCacheChunks(bookId: String, configHash: Int, chapterIndex: Int): List<ByteArray>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPageCacheMetadata(metadata: PageCacheMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPageCacheChunks(chunks: List<PageCacheChunk>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPageIndexEntries(entries: List<PageIndexEntry>)

    @Query("SELECT * FROM page_index_entries WHERE book_id = :bookId AND config_hash = :configHash AND chapter_index = :chapterIndex ORDER BY page_in_chapter ASC")
    abstract suspend fun getPageIndexEntries(bookId: String, configHash: Int, chapterIndex: Int): List<PageIndexEntry>

    @Transaction
    open suspend fun getPageCache(bookId: String, configHash: Int, chapterIndex: Int): PageCacheEntry? {
        val metadata = getPageCacheMetadata(bookId, configHash, chapterIndex) ?: return null
        val chunks = getPageCacheChunks(bookId, configHash, chapterIndex)
        if (chunks.isEmpty()) return null

        val totalSize = chunks.sumOf { it.size }
        val mergedData = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, mergedData, offset, chunk.size)
            offset += chunk.size
        }

        return PageCacheEntry(
            bookId = metadata.bookId,
            configHash = metadata.configHash,
            chapterIndex = metadata.chapterIndex,
            processingVersion = metadata.processingVersion,
            pageCacheVersion = metadata.pageCacheVersion,
            contentVersion = metadata.contentVersion,
            pageCount = metadata.pageCount,
            pagesProto = mergedData
        )
    }

    @Transaction
    open suspend fun insertPageCache(entry: PageCacheEntry, pageIndexEntries: List<PageIndexEntry>) {
        @Suppress("LocalVariableName") val CHUNK_SIZE = 900 * 1024

        deletePageCacheMetadataForChapter(entry.bookId, entry.configHash, entry.chapterIndex)

        insertPageCacheMetadata(
            PageCacheMetadata(
                bookId = entry.bookId,
                configHash = entry.configHash,
                chapterIndex = entry.chapterIndex,
                processingVersion = entry.processingVersion,
                pageCacheVersion = entry.pageCacheVersion,
                contentVersion = entry.contentVersion,
                pageCount = entry.pageCount
            )
        )

        val chunks = ArrayList<PageCacheChunk>()
        var offset = 0
        var chunkIndex = 0
        while (offset < entry.pagesProto.size) {
            val end = (offset + CHUNK_SIZE).coerceAtMost(entry.pagesProto.size)
            chunks.add(
                PageCacheChunk(
                    bookId = entry.bookId,
                    configHash = entry.configHash,
                    chapterIndex = entry.chapterIndex,
                    chunkIndex = chunkIndex,
                    chunkData = entry.pagesProto.copyOfRange(offset, end)
                )
            )
            offset = end
            chunkIndex++
        }
        insertPageCacheChunks(chunks)
        if (pageIndexEntries.isNotEmpty()) {
            insertPageIndexEntries(pageIndexEntries)
        }
    }

    @Query("""
        DELETE FROM page_cache_metadata
        WHERE book_id = :bookId AND config_hash NOT IN (
            SELECT configHash FROM configuration_cache
            WHERE bookId = :bookId
            ORDER BY rowid DESC LIMIT 3
        )
    """)
    abstract suspend fun cleanupOldPageCaches(bookId: String)
}

@Database(
    entities = [
        ProcessedBook::class,
        ProcessedChapterMetadata::class,
        ProcessedChapterChunk::class,
        ConfigurationCache::class,
        AnchorIndexEntry::class,
        PageCacheMetadata::class,
        PageCacheChunk::class,
        PageIndexEntry::class
    ],
    version = 12,
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
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `page_cache_metadata` (
                        `book_id` TEXT NOT NULL,
                        `config_hash` INTEGER NOT NULL,
                        `chapter_index` INTEGER NOT NULL,
                        `processing_version` INTEGER NOT NULL,
                        `page_cache_version` INTEGER NOT NULL,
                        `content_version` INTEGER NOT NULL,
                        `page_count` INTEGER NOT NULL,
                        PRIMARY KEY(`book_id`, `config_hash`, `chapter_index`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `page_cache_chunks` (
                        `book_id` TEXT NOT NULL,
                        `config_hash` INTEGER NOT NULL,
                        `chapter_index` INTEGER NOT NULL,
                        `chunk_index` INTEGER NOT NULL,
                        `chunk_data` BLOB NOT NULL,
                        PRIMARY KEY(`book_id`, `config_hash`, `chapter_index`, `chunk_index`),
                        FOREIGN KEY(`book_id`, `config_hash`, `chapter_index`)
                            REFERENCES `page_cache_metadata`(`book_id`, `config_hash`, `chapter_index`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_page_cache_chunks_book_id_config_hash_chapter_index` ON `page_cache_chunks` (`book_id`, `config_hash`, `chapter_index`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `page_index_entries` (
                        `book_id` TEXT NOT NULL,
                        `config_hash` INTEGER NOT NULL,
                        `chapter_index` INTEGER NOT NULL,
                        `page_in_chapter` INTEGER NOT NULL,
                        `first_block_index` INTEGER NOT NULL,
                        `last_block_index` INTEGER NOT NULL,
                        `first_text_block_index` INTEGER,
                        `first_text_char_offset` INTEGER NOT NULL,
                        `first_text_end_offset` INTEGER NOT NULL,
                        `first_cfi` TEXT,
                        `anchors` TEXT NOT NULL,
                        PRIMARY KEY(`book_id`, `config_hash`, `chapter_index`, `page_in_chapter`),
                        FOREIGN KEY(`book_id`, `config_hash`, `chapter_index`)
                            REFERENCES `page_cache_metadata`(`book_id`, `config_hash`, `chapter_index`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_page_index_entries_book_id_config_hash_chapter_index` ON `page_index_entries` (`book_id`, `config_hash`, `chapter_index`)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `processed_chapter_chunks` RENAME TO `processed_chapter_chunks_old`"
                )
                db.execSQL(
                    "ALTER TABLE `processed_chapter_metadata` RENAME TO `processed_chapter_metadata_old`"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `processed_chapter_metadata` (
                        `book_id` TEXT NOT NULL,
                        `chapter_index` INTEGER NOT NULL,
                        `estimated_page_count` INTEGER NOT NULL,
                        `style_config_hash` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`book_id`, `chapter_index`, `style_config_hash`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `processed_chapter_metadata` (`book_id`, `chapter_index`, `estimated_page_count`, `style_config_hash`)
                    SELECT `book_id`, `chapter_index`, `estimated_page_count`, 0
                    FROM `processed_chapter_metadata_old`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `processed_chapter_chunks` (
                        `book_id` TEXT NOT NULL,
                        `chapter_index` INTEGER NOT NULL,
                        `style_config_hash` INTEGER NOT NULL DEFAULT 0,
                        `chunk_index` INTEGER NOT NULL,
                        `chunk_data` BLOB NOT NULL,
                        PRIMARY KEY(`book_id`, `chapter_index`, `style_config_hash`, `chunk_index`),
                        FOREIGN KEY(`book_id`, `chapter_index`, `style_config_hash`)
                            REFERENCES `processed_chapter_metadata`(`book_id`, `chapter_index`, `style_config_hash`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `processed_chapter_chunks` (`book_id`, `chapter_index`, `style_config_hash`, `chunk_index`, `chunk_data`)
                    SELECT `book_id`, `chapter_index`, 0, `chunk_index`, `chunk_data`
                    FROM `processed_chapter_chunks_old`
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_processed_chapter_chunks_book_id_chapter_index_style_config_hash` ON `processed_chapter_chunks` (`book_id`, `chapter_index`, `style_config_hash`)"
                )
                db.execSQL(
                    "DROP TABLE `processed_chapter_chunks_old`"
                )
                db.execSQL(
                    "DROP TABLE `processed_chapter_metadata_old`"
                )
            }
        }
    }
}
