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
        @Volatile
        private var INSTANCE: PdfTextDatabase? = null

        fun getDatabase(context: Context): PdfTextDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfTextDatabase::class.java,
                    "pdf_text_cache_db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}