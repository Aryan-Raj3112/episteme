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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val LATEST_PROCESSING_VERSION = 6

@Entity(tableName = "processed_books")
data class ProcessedBook(
    @PrimaryKey
    val bookId: String,
    val processingVersion: Int,
    val totalPageCountEstimate: Int
)

@Entity(
    tableName = "anchor_index",
    primaryKeys = ["bookId", "anchorId"],
    indices = [Index(value = ["bookId", "anchorId"])]
)
data class AnchorIndexEntry(
    val bookId: String,
    val anchorId: String,
    val chapterIndex: Int,
    val blockIndex: Int
)

data class ProcessedChapter(
    val bookId: String,
    val chapterIndex: Int,
    val contentBlocksProto: ByteArray,
    val estimatedPageCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedChapter
        if (bookId != other.bookId) return false
        if (chapterIndex != other.chapterIndex) return false
        if (!contentBlocksProto.contentEquals(other.contentBlocksProto)) return false
        if (estimatedPageCount != other.estimatedPageCount) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + chapterIndex
        result = 31 * result + contentBlocksProto.contentHashCode()
        result = 31 * result + estimatedPageCount
        return result
    }
}

/**
 * Database Entity: Stores metadata only (small size).
 */
@Entity(tableName = "processed_chapter_metadata", primaryKeys = ["book_id", "chapter_index"])
data class ProcessedChapterMetadata(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "estimated_page_count") val estimatedPageCount: Int
)

/**
 * Database Entity: Stores the blob data in 1MB chunks to avoid CursorWindow limits.
 */
@Entity(
    tableName = "processed_chapter_chunks",
    primaryKeys = ["book_id", "chapter_index", "chunk_index"],
    foreignKeys = [
        ForeignKey(
            entity = ProcessedChapterMetadata::class,
            parentColumns = ["book_id", "chapter_index"],
            childColumns = ["book_id", "chapter_index"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id", "chapter_index"])]
)
data class ProcessedChapterChunk(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
    @ColumnInfo(name = "chunk_data", typeAffinity = ColumnInfo.BLOB) val chunkData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedChapterChunk
        if (bookId != other.bookId) return false
        if (chapterIndex != other.chapterIndex) return false
        if (chunkIndex != other.chunkIndex) return false
        if (!chunkData.contentEquals(other.chunkData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + chapterIndex
        result = 31 * result + chunkIndex
        result = 31 * result + chunkData.contentHashCode()
        return result
    }
}

@Entity(tableName = "configuration_cache", primaryKeys = ["bookId", "configHash"])
data class ConfigurationCache(
    val bookId: String,
    val configHash: Int,
    val chapterPageCounts: String
)