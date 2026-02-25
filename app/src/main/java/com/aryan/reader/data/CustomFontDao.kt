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
interface CustomFontDao {
    @Query("SELECT * FROM custom_fonts WHERE isDeleted = 0 ORDER BY displayName ASC")
    fun getAllFonts(): Flow<List<CustomFontEntity>>

    @Query("SELECT * FROM custom_fonts WHERE isDeleted = 0")
    suspend fun getAllFontsList(): List<CustomFontEntity>

    @Query("SELECT * FROM custom_fonts")
    suspend fun getAllFontsIncludingDeleted(): List<CustomFontEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFont(font: CustomFontEntity)

    @Query("SELECT * FROM custom_fonts WHERE id = :id")
    suspend fun getFontById(id: String): CustomFontEntity?

    @Query("UPDATE custom_fonts SET isDeleted = 1 WHERE id = :id")
    suspend fun markAsDeleted(id: String)

    @Query("DELETE FROM custom_fonts WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("SELECT * FROM custom_fonts WHERE fileName = :fileName LIMIT 1")
    suspend fun getFontByFileName(fileName: String): CustomFontEntity?
}