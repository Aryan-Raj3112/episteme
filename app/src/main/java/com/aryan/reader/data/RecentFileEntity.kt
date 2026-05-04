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
package com.aryan.reader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.aryan.reader.FileType

@Entity(tableName = "recent_files")
@TypeConverters(FileTypeConverter::class)
data class RecentFileEntity(
    @PrimaryKey val bookId: String,
    val uriString: String?,
    val type: FileType,
    val displayName: String,
    val timestamp: Long,
    val coverImagePath: String?,
    val title: String?,
    val author: String?,
    @ColumnInfo(name = "lastChapterIndex") val lastChapterIndex: Int?,
    val lastPage: Int?,
    @ColumnInfo(name = "lastPositionCfi") val lastPositionCfi: String?,
    @ColumnInfo(name = "progressPercentage") val progressPercentage: Float?,
    @ColumnInfo(defaultValue = "1") val isRecent: Boolean,
    @ColumnInfo(defaultValue = "1") val isAvailable: Boolean,
    val lastModifiedTimestamp: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean,
    val locatorBlockIndex: Int?,
    val locatorCharOffset: Int?,
    val bookmarks: String?,
    @ColumnInfo(defaultValue = "NULL") val sourceFolderUri: String?,
    @ColumnInfo(defaultValue = "0") val isReflowPreferred: Boolean,
    @ColumnInfo(defaultValue = "NULL") val customName: String?,
    @ColumnInfo(defaultValue = "NULL") val highlights: String?,
    @ColumnInfo(name = "fileSize", defaultValue = "0") val fileSize: Long,
    @ColumnInfo(defaultValue = "NULL") val seriesName: String?,
    @ColumnInfo(defaultValue = "NULL") val seriesIndex: Double?,
    @ColumnInfo(defaultValue = "NULL") val description: String?,
    @ColumnInfo(defaultValue = "0") val folderTextMetadataParsed: Boolean
)

data class RecentFileSummary(
    val bookId: String,
    val uriString: String?,
    val type: FileType,
    val displayName: String,
    val timestamp: Long,
    val coverImagePath: String?,
    val title: String?,
    val author: String?,
    @ColumnInfo(name = "lastChapterIndex") val lastChapterIndex: Int?,
    val lastPage: Int?,
    @ColumnInfo(name = "lastPositionCfi") val lastPositionCfi: String?,
    @ColumnInfo(name = "progressPercentage") val progressPercentage: Float?,
    @ColumnInfo(defaultValue = "1") val isRecent: Boolean,
    @ColumnInfo(defaultValue = "1") val isAvailable: Boolean,
    val lastModifiedTimestamp: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean,
    val locatorBlockIndex: Int?,
    val locatorCharOffset: Int?,
    @ColumnInfo(defaultValue = "NULL") val sourceFolderUri: String?,
    @ColumnInfo(defaultValue = "0") val isReflowPreferred: Boolean,
    @ColumnInfo(defaultValue = "NULL") val customName: String?,
    @ColumnInfo(name = "fileSize", defaultValue = "0") val fileSize: Long,
    @ColumnInfo(defaultValue = "NULL") val seriesName: String?,
    @ColumnInfo(defaultValue = "NULL") val seriesIndex: Double?,
    @ColumnInfo(defaultValue = "NULL") val description: String?
)
