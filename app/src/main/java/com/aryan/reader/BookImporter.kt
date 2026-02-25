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
package com.aryan.reader

import android.content.Context
import android.net.Uri
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import androidx.core.net.toUri

private const val BOOKS_DIR = "books"

class BookImporter(private val context: Context) {

    private val booksDir = File(context.filesDir, BOOKS_DIR)

    init {
        if (!booksDir.exists()) {
            booksDir.mkdirs()
        }
    }

    /**
     * Imports a book from a source URI into the app's private storage.
     * @param sourceUri The content or file URI of the book to import.
     * @return The [File] object for the imported book, or null if the import failed.
     */
    suspend fun importBook(sourceUri: Uri): File? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val fileExtension = getFileExtension(sourceUri)
            val destinationFileName = "${UUID.randomUUID()}.$fileExtension"
            val destinationFile = File(booksDir, destinationFileName)

            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw Exception("Could not open input stream for URI: $sourceUri")

            outputStream = FileOutputStream(destinationFile)

            inputStream.copyTo(outputStream)

            Timber.i("Successfully imported book to: ${destinationFile.absolutePath}")
            return@withContext destinationFile

        } catch (e: Exception) {
            Timber.e(e, "Failed to import book from URI: $sourceUri")
            return@withContext null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    /**
     * Deletes a book file from the app's private storage using its URI string.
     * @param uriString The string representation of the file's URI.
     * @return True if the file was successfully deleted, false otherwise.
     */
    suspend fun deleteBookByUriString(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(uriString.toUri().path ?: return@withContext false)
            if (file.exists() && file.parentFile?.name == BOOKS_DIR) {
                val deleted = file.delete()
                if (deleted) {
                    Timber.i("Successfully deleted book file: ${file.path}")
                } else {
                    Timber.w("Failed to delete book file: ${file.path}")
                }
                return@withContext deleted
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting book file for URI string: $uriString")
        }
        return@withContext false
    }

    fun createBookFile(fileNameWithExtension: String): File {
        if (!booksDir.exists()) booksDir.mkdirs()
        return File(booksDir, fileNameWithExtension)
    }

    private fun getFileExtension(uri: Uri): String {
        val path = uri.path ?: return "tmp"
        return File(path).extension.lowercase().ifEmpty {
            // Fallback for URIs that don't have a clear extension in the path
            when (context.contentResolver.getType(uri)) {
                "application/pdf" -> "pdf"
                "application/epub+zip" -> "epub"
                else -> "tmp"
            }
        }
    }
}