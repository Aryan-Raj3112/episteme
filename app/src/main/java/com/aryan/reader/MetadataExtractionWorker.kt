// MetadataExtractionWorker.kt
package com.aryan.reader

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.pdf.PdfCoverGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MetadataExtractionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)
    private val epubParser = EpubParser(appContext)
    private val mobiParser = MobiParser(appContext)
    private val pdfCoverGenerator = PdfCoverGenerator(appContext)

    companion object {
        const val WORK_NAME = "MetadataExtractionWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val hasLegacy = prefs.contains("synced_folder_uri")
        val hasNew = prefs.contains("synced_folders_list_json")

        if (!hasLegacy && !hasNew) {
            Timber.tag("MetadataWorker").w("No folders linked. Stopping.")
            return@withContext Result.success()
        }

        try {
            val filesToProcess = recentFilesRepository.getFolderBooksWithoutCovers()

            if (filesToProcess.isEmpty()) {
                return@withContext Result.success()
            }

            Timber.tag("MetadataWorker").i("Starting background metadata extraction for ${filesToProcess.size} books.")

            filesToProcess.forEach { item ->
                if (isStopped) return@forEach

                if (item.sourceFolderUri == null) return@forEach

                try {
                    val uri = item.uriString?.toUri() ?: return@forEach
                    val type = item.type

                    var coverPath: String? = null
                    var title: String? = null
                    var author: String? = null

                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        when (type) {
                            FileType.EPUB -> {
                                val book = epubParser.createEpubBook(
                                    inputStream = inputStream,
                                    originalBookNameHint = item.displayName,
                                    parseContent = false
                                )
                                title = book.title.takeIf { it.isNotBlank() && it != "content" }
                                author = book.author.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                                book.coverImage?.let { coverPath = recentFilesRepository.saveCoverToCache(it, uri) }
                            }
                            FileType.MOBI -> {
                                val book = mobiParser.createMobiBook(inputStream, item.displayName)
                                book?.let {
                                    title = it.title.takeIf { t -> t.isNotBlank() && t != "content" }
                                    author = it.author.takeIf { a -> a.isNotBlank() && !a.equals("Unknown", ignoreCase = true) }
                                    it.coverImage?.let { img -> coverPath = recentFilesRepository.saveCoverToCache(img, uri) }
                                }
                            }
                            FileType.PDF -> {
                                pdfCoverGenerator.generateCover(uri)?.let {
                                    coverPath = recentFilesRepository.saveCoverToCache(it, uri)
                                }
                                title = item.displayName
                            }
                            else -> {
                                title = item.displayName
                            }
                        }
                    }

                    if (coverPath != null || title != null || author != null) {
                        val updatedItem = item.copy(
                            coverImagePath = coverPath ?: item.coverImagePath,
                            title = title ?: item.title ?: item.displayName,
                            author = author ?: item.author
                        )
                        recentFilesRepository.addRecentFile(updatedItem)
                        Timber.tag("MetadataWorker").d("Updated local metadata for: ${item.displayName}")

                    }

                } catch (e: Exception) {
                    Timber.tag("MetadataWorker").e(e, "Failed to extract metadata for ${item.displayName}")
                }
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Metadata extraction failed")
            return@withContext Result.failure()
        }
    }
}