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

import android.net.Uri
import com.aryan.reader.FileType
import androidx.core.net.toUri

data class RecentFileItem(
    val bookId: String,
    val uriString: String?,
    val type: FileType,
    val displayName: String,
    val timestamp: Long,
    val coverImagePath: String? = null,
    val title: String? = null,
    val author: String? = null,
    val lastChapterIndex: Int? = null,
    val lastPage: Int? = null,
    val lastPositionCfi: String? = null,
    val locatorBlockIndex: Int? = null,
    val locatorCharOffset: Int? = null,
    val progressPercentage: Float? = null,
    val isRecent: Boolean = true,
    val isAvailable: Boolean = true,
    val lastModifiedTimestamp: Long = 0L,
    val isDeleted: Boolean = false,
    val bookmarksJson: String? = null,
    val sourceFolderUri: String? = null
) {
    fun getUri(): Uri? = uriString?.toUri()
}

fun RecentFileEntity.toRecentFileItem(): RecentFileItem {
    return RecentFileItem(
        bookId = this.bookId,
        uriString = this.uriString,
        type = this.type,
        displayName = this.displayName,
        timestamp = this.timestamp,
        coverImagePath = this.coverImagePath,
        title = this.title,
        author = this.author,
        lastChapterIndex = this.lastChapterIndex,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        lastPage = this.lastPage,
        lastPositionCfi = this.lastPositionCfi,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isAvailable = this.isAvailable,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        isDeleted = this.isDeleted,
        bookmarksJson = this.bookmarks,
        sourceFolderUri = this.sourceFolderUri
    )
}

fun RecentFileItem.toRecentFileEntity(): RecentFileEntity {
    return RecentFileEntity(
        bookId = this.bookId,
        uriString = this.uriString,
        type = this.type,
        displayName = this.displayName,
        timestamp = this.timestamp,
        coverImagePath = this.coverImagePath,
        title = this.title,
        author = this.author,
        lastChapterIndex = this.lastChapterIndex,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        lastPage = this.lastPage,
        lastPositionCfi = this.lastPositionCfi,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isAvailable = this.isAvailable,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        isDeleted = this.isDeleted,
        bookmarks = this.bookmarksJson,
        sourceFolderUri = this.sourceFolderUri
    )
}

fun RecentFileItem.toBookMetadata(): BookMetadata {
    return BookMetadata(
        bookId = this.bookId,
        title = this.title,
        author = this.author,
        displayName = this.displayName,
        type = this.type.name,
        lastPositionCfi = this.lastPositionCfi,
        lastChapterIndex = this.lastChapterIndex,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        lastPage = this.lastPage,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isDeleted = this.isDeleted,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        bookmarksJson = this.bookmarksJson,
        hasAnnotations = false
    )
}

fun BookMetadata.toRecentFileItem(): RecentFileItem {
    return RecentFileItem(
        bookId = this.bookId,
        uriString = null,
        type = try { FileType.valueOf(this.type) } catch (_: Exception) { FileType.EPUB },
        displayName = this.displayName,
        timestamp = this.lastModifiedTimestamp,
        coverImagePath = null,
        title = this.title,
        author = this.author,
        lastChapterIndex = this.lastChapterIndex,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        lastPositionCfi = this.lastPositionCfi,
        lastPage = this.lastPage,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isAvailable = false,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        isDeleted = this.isDeleted,
        bookmarksJson = this.bookmarksJson
    )
}