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
        Timber.tag("PdfToMdPerf").d("=== ReflowWorker START ===")

        val bookId = inputData.getString(KEY_BOOK_ID) ?: run {
            Timber.tag("PdfToMdPerf").e("FAILURE: KEY_BOOK_ID is null")
            return@withContext Result.failure()
        }

        val pdfUriString = inputData.getString(KEY_PDF_URI) ?: run {
            Timber.tag("PdfToMdPerf").e("FAILURE: KEY_PDF_URI is null | bookId=$bookId")
            return@withContext Result.failure()
        }
        val originalTitle = inputData.getString(KEY_ORIGINAL_TITLE) ?: "Document"
        val reflowBookId = "${bookId}_reflow"

        Timber.tag("PdfToMdPerf").d("Input data | bookId=$bookId | reflowBookId=$reflowBookId | pdfUri=$pdfUriString | originalTitle=$originalTitle")

        val destFile = File(applicationContext.filesDir, "${bookId}_reflow.md")
        val pdfUri = pdfUriString.toUri()

        Timber.tag("PdfToMdPerf").d("Dest file path: ${destFile.absolutePath} | exists=${destFile.exists()}")
        Timber.tag("PdfToMdPerf").d("Starting PdfToMarkdownGenerator.generateMarkdownFile...")
        val genStartTime = System.currentTimeMillis()

        val success = PdfToMarkdownGenerator.generateMarkdownFile(
            applicationContext,
            pdfUri,
            destFile,
            startPage = 1
        ) { progress ->
            if ((progress * 10).toInt() % 1 == 0) {
                Timber.tag("PdfToMdPerf").d("Progress: ${(progress * 100).toInt()}%")
            }
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        }

        Timber.tag("PdfToMdPerf").d("generateMarkdownFile completed | success=$success | time=${System.currentTimeMillis() - genStartTime}ms")

        if (success && destFile.exists()) {
            val fileSizeKB = destFile.length() / 1024
            Timber.tag("PdfToMdPerf").d("Reflow SUCCESS | outputFileSize=${fileSizeKB}KB")
            Timber.tag("PdfToMdPerf").d("Starting database import...")
            val dbStartTime = System.currentTimeMillis()

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
            Timber.tag("PdfToMdPerf").d("Database import completed in ${System.currentTimeMillis() - dbStartTime}ms")

            setProgressAsync(workDataOf(KEY_PROGRESS to 1.0f))

            val totalTime = System.currentTimeMillis() - workStartTime
            Timber.tag("PdfToMdPerf").d("=== ReflowWorker SUCCESS === | totalTime=${totalTime}ms | totalTimeSec=${totalTime / 1000}s")
            return@withContext Result.success()
        } else {
            val totalTime = System.currentTimeMillis() - workStartTime
            Timber.tag("PdfToMdPerf").e("=== ReflowWorker FAILURE === | success=$success | fileExists=${destFile.exists()} | totalTime=${totalTime}ms")
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