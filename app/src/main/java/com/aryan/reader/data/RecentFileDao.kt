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
// RecentFileDao.kt
package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Upsert
    suspend fun insertOrUpdateFile(file: RecentFileEntity)

    @Upsert
    suspend fun insertOrUpdateFiles(files: List<RecentFileEntity>)

    @Query("SELECT bookId, uriString, type, displayName, timestamp, coverImagePath, title, author, lastChapterIndex, lastPage, lastPositionCfi, progressPercentage, isRecent, isAvailable, lastModifiedTimestamp, isDeleted, locatorBlockIndex, locatorCharOffset, sourceFolderUri, isReflowPreferred, customName, fileSize, fileContentModifiedTimestamp, seriesName, seriesIndex, description, originalTitle, originalAuthor, originalSeriesName, originalSeriesIndex, originalDescription, readingPositionModifiedTimestamp FROM recent_files WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getRecentFiles(): Flow<List<RecentFileSummary>>

    @Query("SELECT * FROM recent_files WHERE sourceFolderUri = :sourceFolderUri AND isDeleted = 0")
    suspend fun getFilesBySourceFolder(sourceFolderUri: String): List<RecentFileEntity>

    @Query("SELECT * FROM recent_files")
    suspend fun getAllFiles(): List<RecentFileEntity>

    @Query("UPDATE recent_files SET isReflowPreferred = :isPreferred WHERE bookId = :bookId")
    suspend fun updateReflowPreference(bookId: String, isPreferred: Boolean)

    @Query("SELECT bookId, uriString, type, displayName, timestamp, coverImagePath, title, author, lastChapterIndex, lastPage, lastPositionCfi, progressPercentage, isRecent, isAvailable, lastModifiedTimestamp, isDeleted, locatorBlockIndex, locatorCharOffset, sourceFolderUri, isReflowPreferred, customName, fileSize, fileContentModifiedTimestamp, seriesName, seriesIndex, description, originalTitle, originalAuthor, originalSeriesName, originalSeriesIndex, originalDescription, readingPositionModifiedTimestamp FROM recent_files WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFilesList(limit: Int): List<RecentFileSummary>

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

    @Query("DELETE FROM recent_files WHERE sourceFolderUri = :sourceFolderUri")
    suspend fun deleteFilesBySourceFolder(sourceFolderUri: String)

    @Query("SELECT * FROM recent_files WHERE bookId LIKE :prefix || '%'")
    suspend fun getFilesWithIdPrefix(prefix: String): List<RecentFileEntity>

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()

    @Query("UPDATE recent_files SET lastPositionCfi = :cfi, lastChapterIndex = :chapterIndex, locatorBlockIndex = :blockIndex, locatorCharOffset = :charOffset, progressPercentage = :progress, timestamp = :timestamp, lastModifiedTimestamp = :timestamp, readingPositionModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateEpubReadingPosition(bookId: String, cfi: String?, chapterIndex: Int, blockIndex: Int, charOffset: Int, progress: Float, timestamp: Long)

    @Query("UPDATE recent_files SET lastPage = :page, progressPercentage = :progress, timestamp = :timestamp, lastModifiedTimestamp = :timestamp, readingPositionModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updatePdfReadingPosition(bookId: String, page: Int, progress: Float, timestamp: Long)

    @Query("UPDATE recent_files SET bookmarks = :bookmarksJson, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateBookmarks(bookId: String, bookmarksJson: String, timestamp: Long)

    @Query("UPDATE recent_files SET isAvailable = 1, uriString = :uriString, timestamp = :timestamp, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateBookAvailability(bookId: String, uriString: String, timestamp: Long)

    @Query("UPDATE recent_files SET isRecent = 0, lastModifiedTimestamp = :timestamp WHERE bookId IN (:bookIds)")
    suspend fun markAsNotRecent(bookIds: List<String>, timestamp: Long)

    @Query("""
        SELECT * FROM recent_files
        WHERE sourceFolderUri IS NOT NULL
        AND isDeleted = 0
        AND (
            (type IN ('PDF', 'EPUB', 'MOBI', 'FB2', 'ODT', 'FODT', 'DOCX') AND folderTextMetadataParsed = 0)
            OR (type IN ('EPUB', 'MOBI', 'FB2') AND folderCoverMetadataParsed = 0 AND (coverImagePath IS NULL OR coverImagePath = ''))
        )
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getFolderBooksNeedingTextMetadata(limit: Int): List<RecentFileEntity>

    @Query("""
        SELECT * FROM recent_files
        WHERE sourceFolderUri = :sourceFolderUri
        AND isDeleted = 0
        AND (
            (type IN ('PDF', 'EPUB', 'MOBI', 'FB2', 'ODT', 'FODT', 'DOCX') AND folderTextMetadataParsed = 0)
            OR (type IN ('EPUB', 'MOBI', 'FB2') AND folderCoverMetadataParsed = 0 AND (coverImagePath IS NULL OR coverImagePath = ''))
        )
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getFolderBooksNeedingTextMetadata(sourceFolderUri: String, limit: Int): List<RecentFileEntity>

    @Query("""
        SELECT COUNT(*) FROM recent_files
        WHERE sourceFolderUri IS NOT NULL
        AND isDeleted = 0
        AND (
            (type IN ('PDF', 'EPUB', 'MOBI', 'FB2', 'ODT', 'FODT', 'DOCX') AND folderTextMetadataParsed = 0)
            OR (type IN ('EPUB', 'MOBI', 'FB2') AND folderCoverMetadataParsed = 0 AND (coverImagePath IS NULL OR coverImagePath = ''))
        )
    """)
    suspend fun countFolderBooksNeedingTextMetadata(): Int

    @Query("""
        SELECT COUNT(*) FROM recent_files
        WHERE sourceFolderUri = :sourceFolderUri
        AND isDeleted = 0
        AND (
            (type IN ('PDF', 'EPUB', 'MOBI', 'FB2', 'ODT', 'FODT', 'DOCX') AND folderTextMetadataParsed = 0)
            OR (type IN ('EPUB', 'MOBI', 'FB2') AND folderCoverMetadataParsed = 0 AND (coverImagePath IS NULL OR coverImagePath = ''))
        )
    """)
    suspend fun countFolderBooksNeedingTextMetadata(sourceFolderUri: String): Int

    @Query("""
        UPDATE recent_files
        SET
            coverImagePath = COALESCE(:coverImagePath, coverImagePath),
            title = CASE
                WHEN :title IS NOT NULL AND (originalTitle IS NULL OR title IS NULL OR title = originalTitle OR title = displayName)
                THEN :title
                ELSE title
            END,
            author = CASE
                WHEN :author IS NOT NULL AND (originalAuthor IS NULL OR author IS NULL OR author = originalAuthor)
                THEN :author
                ELSE author
            END,
            seriesName = CASE
                WHEN :seriesName IS NOT NULL AND (originalSeriesName IS NULL OR seriesName IS NULL OR seriesName = originalSeriesName)
                THEN :seriesName
                ELSE seriesName
            END,
            seriesIndex = CASE
                WHEN :seriesIndex IS NOT NULL AND (originalSeriesIndex IS NULL OR seriesIndex IS NULL OR seriesIndex = originalSeriesIndex)
                THEN :seriesIndex
                ELSE seriesIndex
            END,
            description = CASE
                WHEN :description IS NOT NULL AND (originalDescription IS NULL OR description IS NULL OR description = originalDescription)
                THEN :description
                ELSE description
            END,
            originalTitle = CASE
                WHEN :title IS NOT NULL AND (originalTitle IS NULL OR originalTitle = title OR originalTitle = displayName)
                THEN :title
                ELSE originalTitle
            END,
            originalAuthor = CASE
                WHEN :author IS NOT NULL AND (originalAuthor IS NULL OR originalAuthor = author)
                THEN :author
                ELSE originalAuthor
            END,
            originalSeriesName = CASE
                WHEN :seriesName IS NOT NULL AND (originalSeriesName IS NULL OR originalSeriesName = seriesName)
                THEN :seriesName
                ELSE originalSeriesName
            END,
            originalSeriesIndex = CASE
                WHEN :seriesIndex IS NOT NULL AND (originalSeriesIndex IS NULL OR originalSeriesIndex = seriesIndex)
                THEN :seriesIndex
                ELSE originalSeriesIndex
            END,
            originalDescription = CASE
                WHEN :description IS NOT NULL AND (originalDescription IS NULL OR originalDescription = description)
                THEN :description
                ELSE originalDescription
            END,
            fileSize = CASE WHEN :fileSize > 0 THEN :fileSize ELSE fileSize END,
            fileContentModifiedTimestamp = CASE WHEN :fileContentModifiedTimestamp > 0 THEN :fileContentModifiedTimestamp ELSE fileContentModifiedTimestamp END,
            folderTextMetadataParsed = CASE WHEN :textMetadataParsed = 1 THEN 1 ELSE folderTextMetadataParsed END,
            folderCoverMetadataParsed = CASE WHEN :coverMetadataParsed = 1 THEN 1 ELSE folderCoverMetadataParsed END
        WHERE bookId = :bookId
    """)
    suspend fun updateExtractedMetadata(
        bookId: String,
        coverImagePath: String?,
        title: String?,
        author: String?,
        seriesName: String?,
        seriesIndex: Double?,
        description: String?,
        fileSize: Long,
        fileContentModifiedTimestamp: Long,
        textMetadataParsed: Boolean,
        coverMetadataParsed: Boolean
    )

    @Query("UPDATE recent_files SET sourceFolderUri = NULL WHERE sourceFolderUri IS NOT NULL")
    suspend fun detachAllFolderBooks()

    @Query("UPDATE recent_files SET highlights = :highlightsJson, lastModifiedTimestamp = :timestamp WHERE bookId = :bookId")
    suspend fun updateHighlights(bookId: String, highlightsJson: String, timestamp: Long)

    @Query("""
        UPDATE recent_files
        SET
            title = :title,
            author = :author,
            seriesName = :seriesName,
            seriesIndex = :seriesIndex,
            description = :description,
            customName = NULL,
            originalTitle = COALESCE(originalTitle, title),
            originalAuthor = COALESCE(originalAuthor, author),
            originalSeriesName = COALESCE(originalSeriesName, seriesName),
            originalSeriesIndex = COALESCE(originalSeriesIndex, seriesIndex),
            originalDescription = COALESCE(originalDescription, description),
            fileSize = CASE WHEN :fileSize > 0 THEN :fileSize ELSE fileSize END,
            fileContentModifiedTimestamp = CASE WHEN :fileContentModifiedTimestamp > 0 THEN :fileContentModifiedTimestamp ELSE fileContentModifiedTimestamp END,
            folderTextMetadataParsed = 1,
            lastModifiedTimestamp = :timestamp
        WHERE bookId = :bookId
    """)
    suspend fun updateUserEditableMetadata(
        bookId: String,
        title: String?,
        author: String?,
        seriesName: String?,
        seriesIndex: Double?,
        description: String?,
        fileSize: Long,
        fileContentModifiedTimestamp: Long,
        timestamp: Long
    )

    @Query("""
        UPDATE recent_files
        SET
            title = COALESCE(originalTitle, displayName),
            author = originalAuthor,
            seriesName = originalSeriesName,
            seriesIndex = originalSeriesIndex,
            description = originalDescription,
            customName = NULL,
            fileSize = CASE WHEN :fileSize > 0 THEN :fileSize ELSE fileSize END,
            fileContentModifiedTimestamp = CASE WHEN :fileContentModifiedTimestamp > 0 THEN :fileContentModifiedTimestamp ELSE fileContentModifiedTimestamp END,
            folderTextMetadataParsed = 1,
            lastModifiedTimestamp = :timestamp
        WHERE bookId = :bookId
    """)
    suspend fun restoreOriginalMetadata(
        bookId: String,
        fileSize: Long,
        fileContentModifiedTimestamp: Long,
        timestamp: Long
    )
}
