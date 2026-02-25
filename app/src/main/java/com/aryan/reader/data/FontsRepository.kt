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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val FONTS_DIR = "custom_fonts"

class FontsRepository(private val context: Context) {
    private val fontDao = AppDatabase.getDatabase(context).customFontDao()
    private val fontsDir = File(context.filesDir, FONTS_DIR)

    init {
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
    }

    fun getAllFonts(): Flow<List<CustomFontEntity>> = fontDao.getAllFonts()

    suspend fun getAllFontsForSync(): List<CustomFontEntity> = fontDao.getAllFontsIncludingDeleted()

    @Suppress("unused")
    suspend fun getFontById(id: String): CustomFontEntity? = fontDao.getFontById(id)

    // Used when downloading from cloud
    fun getFontFile(fileName: String): File {
        return File(fontsDir, fileName)
    }

    suspend fun addFontFromSync(metadata: FontMetadata) = withContext(Dispatchers.IO) {
        val fontFile = File(fontsDir, metadata.fileName)
        val entity = CustomFontEntity(
            id = metadata.id,
            displayName = metadata.displayName,
            fileName = metadata.fileName,
            fileExtension = metadata.fileExtension,
            path = fontFile.absolutePath,
            timestamp = metadata.timestamp,
            isDeleted = metadata.isDeleted
        )
        fontDao.insertFont(entity)
    }

    suspend fun importFont(uri: Uri): Result<CustomFontEntity> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val originalName = getFileName(uri) ?: "unknown.ttf"
            val extension = originalName.substringAfterLast('.', "").lowercase()

            if (extension !in listOf("ttf", "otf", "woff2")) {
                return@withContext Result.failure(Exception("Unsupported font format. Please use TTF, OTF, or WOFF2."))
            }

            val fontId = UUID.randomUUID().toString()
            val internalFileName = "font_${fontId}.$extension"
            val destinationFile = File(fontsDir, internalFileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            val displayName = originalName.substringBeforeLast('.')

            val entity = CustomFontEntity(
                id = fontId,
                displayName = displayName,
                fileName = internalFileName,
                fileExtension = extension,
                path = destinationFile.absolutePath,
                timestamp = System.currentTimeMillis()
            )

            fontDao.insertFont(entity)
            Timber.d("Imported font: $displayName to ${destinationFile.absolutePath}")

            Result.success(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import font")
            Result.failure(e)
        }
    }

    suspend fun deleteFont(fontId: String) = withContext(Dispatchers.IO) {
        val font = fontDao.getFontById(fontId) ?: return@withContext
        fontDao.markAsDeleted(fontId)

        // We delete the file locally to save space, but keep the DB entry as tombstone for sync
        val file = File(font.path)
        if (file.exists()) {
            file.delete()
        }
        Timber.d("Deleted font locally: ${font.displayName}")
    }

    suspend fun deletePermanently(fontId: String) = withContext(Dispatchers.IO) {
        val font = fontDao.getFontById(fontId)
        font?.let {
            val file = File(it.path)
            if(file.exists()) file.delete()
        }
        fontDao.deletePermanently(fontId)
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
}