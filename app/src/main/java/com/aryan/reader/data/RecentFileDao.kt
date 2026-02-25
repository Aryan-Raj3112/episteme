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
package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFile(file: RecentFileEntity)

    @Query("SELECT * FROM recent_files WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getRecentFiles(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files")
    suspend fun getAllFiles(): List<RecentFileEntity>

    @Query("SELECT * FROM recent_files WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFilesList(limit: Int): List<RecentFileEntity>

    @Query("DELETE FROM recent_files WHERE bookId IN (:bookIds)")
    suspend fun deleteFilePermanently(bookIds: List<String>)

    @Query("UPDATE recent_files SET isDeleted = 1, isAvailable = 0, lastModifiedTimestamp = :timestamp WHERE bookId IN (:bookIds)")
    suspend fun markAsDeleted(bookIds: List<String>, timestamp: Long)

    @Query("SELECT * FROM recent_files WHERE lastModifiedTimestamp > :sinceTimestamp")
    suspend fun getModifiedSince(sinceTimestamp: Long): List<RecentFileEntity>

    @Query("SELECT COUNT(*) FROM recent_files")
    suspend fun count(): Int

    @Query("SELECT * FROM recent_files WHERE bookId = :bookId")
    suspend fun getFileByBookId(bookId: String): RecentFileEntity?

    @Query("SELECT * FROM recent_files WHERE uriString = :uriString")
    suspend fun getFileByUri(uriString: String): RecentFileEntity?

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()

    @Query("UPDATE recent_files SET lastPositionCfi = :cfi, lastChapterIndex = :chapterIndex, locatorBlockIndex = :blockIndex, locatorCharOffset = :charOffset, progressPercentage = :progress, timestamp = :timestamp, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateEpubReadingPosition(bookId: String, cfi: String?, chapterIndex: Int, blockIndex: Int, charOffset: Int, progress: Float, timestamp: Long)

    @Query("UPDATE recent_files SET lastPage = :page, progressPercentage = :progress, timestamp = :timestamp, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updatePdfReadingPosition(bookId: String, page: Int, progress: Float, timestamp: Long)

    @Query("UPDATE recent_files SET bookmarks = :bookmarksJson, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateBookmarks(bookId: String, bookmarksJson: String, timestamp: Long)

    @Query("UPDATE recent_files SET isAvailable = 1, uriString = :uriString, timestamp = :timestamp, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateBookAvailability(bookId: String, uriString: String, timestamp: Long)

    @Query("UPDATE recent_files SET isRecent = 0, lastModifiedTimestamp = :timestamp WHERE bookId IN (:bookIds)")
    suspend fun markAsNotRecent(bookIds: List<String>, timestamp: Long)
}