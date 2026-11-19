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
package com.aryan.reader.pdf.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "pdf_search_index")
data class PdfSearchIndex(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int? = null,
    val bookId: String,
    val pageIndex: Int,
    val content: String
)

data class PdfSearchMatch(
    val pageIndex: Int,
    @ColumnInfo(name = "snippet") val snippet: String,
    @ColumnInfo(name = "content") val content: String
)

@Entity(tableName = "pdf_metadata")
data class PdfMetadata(
    @PrimaryKey val bookId: String,
    val totalPages: Int,
    val ratiosJson: String,
    val ocrLanguage: String = "LATIN"
)

@Dao
interface PdfTextDao {
    @Query("""
        SELECT pageIndex, snippet(pdf_search_index, '<b>', '</b>', '...', -1, 15) as snippet, content 
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pdf_search_index MATCH :query 
        ORDER BY pageIndex ASC
    """)
    fun searchBookFlow(bookId: String, query: String): Flow<List<PdfSearchMatch>>

    @Query("""
        SELECT pageIndex, snippet(pdf_search_index, '<b>', '</b>', '...', -1, 15) as snippet, content 
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pdf_search_index MATCH :query 
        ORDER BY pageIndex ASC
    """)
    fun searchBookPagingSource(bookId: String, query: String): PagingSource<Int, PdfSearchMatch>

    @Query("""
        SELECT pageIndex, snippet(pdf_search_index, '<b>', '</b>', '...', -1, 15) as snippet, content 
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pdf_search_index MATCH :query 
        ORDER BY pageIndex ASC
    """)
    suspend fun getAllMatches(bookId: String, query: String): List<PdfSearchMatch>

    @Query("""
        SELECT count(*) 
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pdf_search_index MATCH :query
    """)
    suspend fun countMatches(bookId: String, query: String): Int

    @Query("""
        SELECT pageIndex, content, '' as snippet
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pageIndex >= :minPageIndex AND pdf_search_index MATCH :query
        ORDER BY pageIndex ASC 
        LIMIT 1
    """)
    suspend fun getNextPageWithMatch(bookId: String, query: String, minPageIndex: Int): PdfSearchMatch?

    @Query("""
        SELECT pageIndex, content, '' as snippet
        FROM pdf_search_index 
        WHERE bookId = :bookId AND pageIndex <= :maxPageIndex AND pdf_search_index MATCH :query
        ORDER BY pageIndex DESC 
        LIMIT 1
    """)
    suspend fun getPrevPageWithMatch(bookId: String, query: String, maxPageIndex: Int): PdfSearchMatch?

    @Query("SELECT content FROM pdf_search_index WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun getPageText(bookId: String, pageIndex: Int): String?

    @Query("SELECT pageIndex FROM pdf_search_index WHERE bookId = :bookId")
    suspend fun getIndexedPageIndices(bookId: String): List<Int>

    @Query("DELETE FROM pdf_search_index WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun deletePageText(bookId: String, pageIndex: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageText(entity: PdfSearchIndex)

    @Query("DELETE FROM pdf_search_index WHERE bookId = :bookId")
    suspend fun clearBookText(bookId: String)

    @Query("DELETE FROM pdf_search_index")
    suspend fun deleteAll()
}

@Dao
interface PdfMetaDao {
    @Query("SELECT * FROM pdf_metadata WHERE bookId = :bookId")
    suspend fun getMetadata(bookId: String): PdfMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: PdfMetadata)

    @Query("UPDATE pdf_metadata SET ocrLanguage = :language WHERE bookId = :bookId")
    suspend fun updateLanguage(bookId: String, language: String)
}

@Database(entities = [PdfSearchIndex::class, PdfMetadata::class], version = 5, exportSchema = false)
abstract class PdfTextDatabase : RoomDatabase() {
    abstract fun pdfTextDao(): PdfTextDao
    abstract fun pdfMetaDao(): PdfMetaDao

    companion object {
        private const val DATABASE_NAME = "pdf_text_cache_db"

        @Volatile
        private var INSTANCE: PdfTextDatabase? = null

        /**
         * Set once the singleton's connection has actually been opened at least once.
         *
         * Room opens lazily, so `isOpen` is false for a freshly built instance and cannot by itself
         * distinguish "not used yet" from "was closed". Together they can: opened once + not open =
         * the database was closed under us.
         */
        @Volatile
        private var instanceWasOpened = false

        /**
         * The process-wide cache database; rebuilt if the previous instance was closed.
         *
         * `pdf_text_cache_db` is a rebuildable cache (extracted page text, metadata, FTS index), and
         * SQLite reports corruption by closing the database: Room's default `onCorruption` closes it
         * and deletes the files, after which every later call on that instance throws
         * `IllegalStateException: attempt to re-open an already-closed object` for the rest of the
         * process (docs/crashlytics-triage.md#137). Rebuilding the closed singleton means the cache
         * heals as soon as the failure is tolerated once.
         */
        fun getDatabase(context: Context): PdfTextDatabase {
            INSTANCE?.let { existing ->
                if (!instanceWasOpened || existing.isOpen) return existing
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && (!instanceWasOpened || existing.isOpen)) {
                    return existing
                }
                existing?.let { runCatching { it.close() } }
                instanceWasOpened = false
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfTextDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            instanceWasOpened = true
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Drops the singleton after an unrecoverable cache failure so the next access rebuilds it. */
        internal fun invalidateInstance() {
            synchronized(this) {
                INSTANCE?.let { runCatching { it.close() } }
                INSTANCE = null
                instanceWasOpened = false
            }
        }
    }
}
