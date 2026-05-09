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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val LATEST_PROCESSING_VERSION = 11
const val LATEST_PAGE_CACHE_VERSION = 3

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

data class PageCacheEntry(
    val bookId: String,
    val configHash: Int,
    val chapterIndex: Int,
    val processingVersion: Int,
    val pageCacheVersion: Int,
    val contentVersion: Int,
    val pageCount: Int,
    val pagesProto: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PageCacheEntry
        if (bookId != other.bookId) return false
        if (configHash != other.configHash) return false
        if (chapterIndex != other.chapterIndex) return false
        if (processingVersion != other.processingVersion) return false
        if (pageCacheVersion != other.pageCacheVersion) return false
        if (contentVersion != other.contentVersion) return false
        if (pageCount != other.pageCount) return false
        if (!pagesProto.contentEquals(other.pagesProto)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + configHash
        result = 31 * result + chapterIndex
        result = 31 * result + processingVersion
        result = 31 * result + pageCacheVersion
        result = 31 * result + contentVersion
        result = 31 * result + pageCount
        result = 31 * result + pagesProto.contentHashCode()
        return result
    }
}

@Entity(tableName = "page_cache_metadata", primaryKeys = ["book_id", "config_hash", "chapter_index"])
data class PageCacheMetadata(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "config_hash") val configHash: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "processing_version") val processingVersion: Int,
    @ColumnInfo(name = "page_cache_version") val pageCacheVersion: Int,
    @ColumnInfo(name = "content_version") val contentVersion: Int,
    @ColumnInfo(name = "page_count") val pageCount: Int
)

@Entity(
    tableName = "page_cache_chunks",
    primaryKeys = ["book_id", "config_hash", "chapter_index", "chunk_index"],
    foreignKeys = [
        ForeignKey(
            entity = PageCacheMetadata::class,
            parentColumns = ["book_id", "config_hash", "chapter_index"],
            childColumns = ["book_id", "config_hash", "chapter_index"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id", "config_hash", "chapter_index"])]
)
data class PageCacheChunk(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "config_hash") val configHash: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
    @ColumnInfo(name = "chunk_data", typeAffinity = ColumnInfo.BLOB) val chunkData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PageCacheChunk
        if (bookId != other.bookId) return false
        if (configHash != other.configHash) return false
        if (chapterIndex != other.chapterIndex) return false
        if (chunkIndex != other.chunkIndex) return false
        if (!chunkData.contentEquals(other.chunkData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + configHash
        result = 31 * result + chapterIndex
        result = 31 * result + chunkIndex
        result = 31 * result + chunkData.contentHashCode()
        return result
    }
}

@Entity(
    tableName = "page_index_entries",
    primaryKeys = ["book_id", "config_hash", "chapter_index", "page_in_chapter"],
    foreignKeys = [
        ForeignKey(
            entity = PageCacheMetadata::class,
            parentColumns = ["book_id", "config_hash", "chapter_index"],
            childColumns = ["book_id", "config_hash", "chapter_index"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id", "config_hash", "chapter_index"])]
)
data class PageIndexEntry(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "config_hash") val configHash: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "page_in_chapter") val pageInChapter: Int,
    @ColumnInfo(name = "first_block_index") val firstBlockIndex: Int,
    @ColumnInfo(name = "last_block_index") val lastBlockIndex: Int,
    @ColumnInfo(name = "first_text_block_index") val firstTextBlockIndex: Int?,
    @ColumnInfo(name = "first_text_char_offset") val firstTextCharOffset: Int,
    @ColumnInfo(name = "first_text_end_offset") val firstTextEndOffset: Int,
    @ColumnInfo(name = "first_cfi") val firstCfi: String?,
    @ColumnInfo(name = "anchors") val anchors: String
)
