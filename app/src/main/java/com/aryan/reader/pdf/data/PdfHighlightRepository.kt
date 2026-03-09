// PdfHighlightRepository.kt
package com.aryan.reader.pdf.data

import android.content.Context
import com.aryan.reader.pdf.PdfUserHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class PdfHighlightRepository(private val context: Context) {

    fun getFileForSync(bookId: String): File {
        val safeBookId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "pdf_highlights")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "highlights_$safeBookId.json")
    }

    suspend fun saveHighlights(bookId: String, highlights: List<PdfUserHighlight>) {
        withContext(Dispatchers.IO) {
            try {
                val file = getFileForSync(bookId)
                if (highlights.isEmpty()) {
                    if (file.exists()) file.delete()
                    return@withContext
                }
                file.writeText(HighlightSerializer.toJson(highlights))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save local highlights")
            }
        }
    }

    suspend fun loadHighlights(bookId: String): List<PdfUserHighlight> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFileForSync(bookId)
                if (file.exists()) {
                    HighlightSerializer.fromJson(file.readText())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load local highlights")
                emptyList()
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "pdf_highlights")
        if (dir.exists()) dir.deleteRecursively()
    }
}