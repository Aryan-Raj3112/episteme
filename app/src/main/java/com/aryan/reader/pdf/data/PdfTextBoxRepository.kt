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
package com.aryan.reader.pdf.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfTextBoxRepository(private val context: Context) {

    private fun getFile(bookId: String): File {
        val safeBookId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "textboxes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "textboxes_$safeBookId.json")
    }

    suspend fun saveTextBoxes(bookId: String, textBoxes: List<PdfTextBox>) {
        withContext(Dispatchers.IO) {
            if (textBoxes.isEmpty()) {
                val file = getFile(bookId)
                if (file.exists()) file.delete()
                return@withContext
            }
            val json = TextBoxSerializer.toJson(textBoxes)
            getFile(bookId).writeText(json)
        }
    }

    suspend fun loadTextBoxes(bookId: String): List<PdfTextBox> {
        return withContext(Dispatchers.IO) {
            val file = getFile(bookId)
            if (file.exists()) {
                TextBoxSerializer.fromJson(file.readText())
            } else {
                emptyList()
            }
        }
    }

    fun getFileForSync(bookId: String): File {
        return getFile(bookId)
    }

    fun clearAll() {
        val dir = File(context.filesDir, "textboxes")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    fun deleteForBook(bookId: String) {
        val file = getFile(bookId)
        if(file.exists()) file.delete()
    }
}