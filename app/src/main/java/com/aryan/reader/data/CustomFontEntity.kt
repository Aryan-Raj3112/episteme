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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_fonts")
data class CustomFontEntity(
    @PrimaryKey val id: String, // UUID
    val displayName: String,
    val fileName: String, // The actual filename on disk (e.g., font_uuid.ttf)
    val fileExtension: String, // ttf, otf, woff2
    val path: String, // Absolute path to the file
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)