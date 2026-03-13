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
        val workStartTime = System.currentTimeMillis()
        Timber.tag("PdfToHtmlPerf").d("=== ReflowWorker START ===")

        val bookId = inputData.getString(KEY_BOOK_ID) ?: run {
            Timber.tag("PdfToHtmlPerf").e("FAILURE: KEY_BOOK_ID is null")
            return@withContext Result.failure()
        }

        val pdfUriString = inputData.getString(KEY_PDF_URI) ?: run {
            Timber.tag("PdfToHtmlPerf").e("FAILURE: KEY_PDF_URI is null | bookId=$bookId")
            return@withContext Result.failure()
        }
        val originalTitle = inputData.getString(KEY_ORIGINAL_TITLE) ?: "Document"
        val reflowBookId = "${bookId}_reflow"

        Timber.tag("PdfToHtmlPerf").d(
            "Input | bookId=$bookId | reflowBookId=$reflowBookId | pdfUri=$pdfUriString | title=$originalTitle"
        )

        val destFile = File(applicationContext.filesDir, "${bookId}_reflow.html")
        val pdfUri = pdfUriString.toUri()

        Timber.tag("PdfToHtmlPerf").d("Dest: ${destFile.absolutePath} | exists=${destFile.exists()}")

        val genStartTime = System.currentTimeMillis()
        val success = PdfToHtmlGenerator.generateHtmlFile(
            context  = applicationContext,
            pdfUri   = pdfUri,
            destFile = destFile,
            startPage = 1
        ) { progress ->
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        }

        Timber.tag("PdfToHtmlPerf").d(
            "generateHtmlFile done | success=$success | ${System.currentTimeMillis() - genStartTime}ms"
        )

        if (success && destFile.exists()) {
            val fileSizeKB = destFile.length() / 1024
            Timber.tag("PdfToHtmlPerf").d("Output size: ${fileSizeKB}KB")

            val repo = RecentFilesRepository(applicationContext)
            val newItem = RecentFileItem(
                bookId               = reflowBookId,
                uriString            = destFile.toUri().toString(),
                type                 = FileType.HTML,
                displayName          = "$originalTitle (Text View)",
                timestamp            = System.currentTimeMillis(),
                coverImagePath       = null,
                title                = "$originalTitle (Reflow)",
                author               = "Generated",
                isAvailable          = true,
                isRecent             = true,
                lastModifiedTimestamp = System.currentTimeMillis(),
                isDeleted            = false,
                sourceFolderUri      = null
            )

            repo.addRecentFile(newItem)
            setProgressAsync(workDataOf(KEY_PROGRESS to 1.0f))

            val totalTime = System.currentTimeMillis() - workStartTime
            Timber.tag("PdfToHtmlPerf").d("=== ReflowWorker SUCCESS === | ${totalTime}ms")
            return@withContext Result.success()
        } else {
            val totalTime = System.currentTimeMillis() - workStartTime
            Timber.tag("PdfToHtmlPerf").e(
                "=== ReflowWorker FAILURE === | success=$success | fileExists=${destFile.exists()} | ${totalTime}ms"
            )
            return@withContext Result.failure()
        }
    }

    companion object {
        const val WORK_NAME    = "reflow_work"
        const val KEY_BOOK_ID  = "book_id"
        const val KEY_PDF_URI  = "pdf_uri"
        const val KEY_ORIGINAL_TITLE = "original_title"
        const val KEY_PROGRESS = "progress"
    }
}