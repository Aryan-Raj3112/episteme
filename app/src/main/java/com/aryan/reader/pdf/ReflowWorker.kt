// ReflowWorker.kt
package com.aryan.reader.pdf

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class ReflowWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return@withContext Result.failure()
        val pdfUriString = inputData.getString(KEY_PDF_URI) ?: return@withContext Result.failure()
        val originalTitle = inputData.getString(KEY_ORIGINAL_TITLE) ?: "Document"
        val reflowBookId = "${bookId}_reflow"

        val destFile = File(applicationContext.filesDir, "${bookId}_reflow.md")
        val pdfUri = pdfUriString.toUri()

        Timber.tag("ReflowWorker").d("Starting background reflow for $originalTitle.")

        // Delegate entire process to Generator (it now handles the loop and progress)
        val success = PdfToMarkdownGenerator.generateMarkdownFile(
            applicationContext,
            pdfUri,
            destFile,
            startPage = 1 // Always start from beginning for full regeneration
        ) { progress ->
            // Report progress
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        }

        if (success && destFile.exists()) {
            Timber.tag("ReflowWorker").d("Reflow complete. Importing to database.")

            val repo = RecentFilesRepository(applicationContext)

            val newItem = RecentFileItem(
                bookId = reflowBookId,
                uriString = destFile.toUri().toString(),
                type = FileType.MD,
                displayName = "$originalTitle (Text View)",
                timestamp = System.currentTimeMillis(),
                coverImagePath = null,
                title = "$originalTitle (Reflow)",
                author = "Generated",
                isAvailable = true,
                isRecent = true,
                lastModifiedTimestamp = System.currentTimeMillis(),
                isDeleted = false,
                sourceFolderUri = null
            )

            repo.addRecentFile(newItem)

            // 100% Progress
            setProgressAsync(workDataOf(KEY_PROGRESS to 1.0f))

            return@withContext Result.success()
        } else {
            Timber.e("Reflow failed or was incomplete.")
            return@withContext Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "reflow_work"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_PDF_URI = "pdf_uri"
        const val KEY_ORIGINAL_TITLE = "original_title"
        const val KEY_PROGRESS = "progress"
    }
}