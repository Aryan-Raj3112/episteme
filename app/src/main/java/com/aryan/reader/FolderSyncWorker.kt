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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.pdf.PdfCoverGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class FolderSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)
    private val bookImporter = BookImporter(appContext)
    private val epubParser = EpubParser(appContext)
    private val mobiParser = MobiParser(appContext)
    private val pdfCoverGenerator = PdfCoverGenerator(appContext)

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
    }

    override suspend fun doWork(): Result {
        Timber.d("Worker starting folder sync check.")
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(MainViewModel.KEY_SYNCED_FOLDER_URI, null)

        if (folderUriString.isNullOrBlank()) {
            Timber.d("No sync folder configured. Worker stopping.")
            return Result.success()
        }

        val folderUri = folderUriString.toUri()

        return withContext(Dispatchers.IO) {
            try {
                val documentTree = DocumentFile.fromTreeUri(appContext, folderUri)
                if (documentTree == null || !documentTree.isDirectory) {
                    Timber.e("Could not read the synced folder URI: $folderUriString. Cancelling worker.")
                    WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
                    return@withContext Result.failure()
                }

                val filesToScan = mutableListOf<DocumentFile>()
                val fileQueue = ArrayDeque<DocumentFile>()
                documentTree.listFiles().let { fileQueue.addAll(it) }

                while (fileQueue.isNotEmpty()) {
                    val file = fileQueue.removeAt(0)
                    if (file.isDirectory) {
                        file.listFiles().let { fileQueue.addAll(it) }
                    } else if (file.isFile) {
                        val fileName = file.name ?: ""
                        if (fileName.endsWith(".pdf", true) || fileName.endsWith(".epub", true) || fileName.endsWith(".mobi", true) || fileName.endsWith(".azw3", true)) {
                            filesToScan.add(file)
                        }
                    }
                }

                var importedCount = 0
                for (file in filesToScan) {
                    val importResult = prepareBookForImport(file.uri)
                    if (importResult != null) {
                        val (internalUri, bookId, type) = importResult
                        val displayName = file.name ?: "Unknown File"
                        addBookToDatabase(internalUri, type, bookId, displayName, folderUriString)
                        importedCount++
                    }
                }

                if (importedCount > 0) {
                    Timber.d("Worker successfully imported $importedCount new book(s).")
                } else {
                    Timber.d("Worker found no new books to import.")
                }

                prefs.edit {
                    putLong(
                        MainViewModel.KEY_LAST_FOLDER_SCAN_TIME,
                        System.currentTimeMillis()
                    )
                }

                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Error during folder sync worker execution.")
                Result.failure()
            }
        }
    }

    private suspend fun prepareBookForImport(externalUri: Uri): Triple<Uri, String, FileType>? {
        val type = getFileTypeFromUri(externalUri, appContext) ?: return null

        val hash = FileHasher.calculateSha256 {
            appContext.contentResolver.openInputStream(externalUri)
        } ?: return null

        if (recentFilesRepository.getFileByBookId(hash) != null) {
            return null // Already exists
        }

        val internalFile = bookImporter.importBook(externalUri) ?: return null
        return Triple(internalFile.toUri(), hash, type)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return DocumentFile.fromSingleUri(appContext, uri)?.name
    }

    private suspend fun addBookToDatabase(
        uri: Uri,
        type: FileType,
        bookId: String,
        displayName: String,
        sourceFolderUri: String
    ) {
        var coverPath: String? = null
        var title: String? = null
        var author: String? = null

        if (type == FileType.EPUB || type == FileType.MOBI) {
            val book = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (type == FileType.EPUB) {
                        epubParser.createEpubBook(
                            inputStream = inputStream,
                            originalBookNameHint = displayName
                        )
                    } else {
                        mobiParser.createMobiBook(
                            inputStream = inputStream,
                            originalBookNameHint = displayName
                        )
                    }
                }
            }
            if (book != null) {
                title = book.title.takeIf { it.isNotBlank() } ?: displayName
                author = book.author.takeIf { it.isNotBlank() }
                book.coverImage?.let {
                    coverPath = recentFilesRepository.saveCoverToCache(it, uri)
                }
            }
        } else if (type == FileType.PDF) {
            title = displayName
            pdfCoverGenerator.generateCover(uri)?.let {
                coverPath = recentFilesRepository.saveCoverToCache(it, uri)
            }
        }

        val newItem = RecentFileItem(
            bookId = bookId,
            uriString = uri.toString(),
            type = type,
            displayName = displayName,
            timestamp = System.currentTimeMillis(),
            coverImagePath = coverPath,
            title = title,
            author = author,
            isAvailable = true,
            lastModifiedTimestamp = System.currentTimeMillis(),
            isDeleted = false,
            isRecent = false, // Books from folder sync should not appear on the Home screen
            sourceFolderUri = sourceFolderUri
        )
        recentFilesRepository.addRecentFile(newItem)
        Timber.i("Worker added new book to database: $displayName")
    }

    private fun getFileTypeFromUri(uri: Uri, context: Context): FileType? {
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "application/pdf" -> FileType.PDF
            "application/epub+zip" -> FileType.EPUB
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.amazon.mobi8-ebook" -> FileType.MOBI
            else -> {
                val path = getFileNameFromUri(uri)
                when {
                    path?.endsWith(".pdf", ignoreCase = true) == true -> FileType.PDF
                    path?.endsWith(".epub", ignoreCase = true) == true -> FileType.EPUB
                    path?.endsWith(".mobi", ignoreCase = true) == true -> FileType.MOBI
                    path?.endsWith(".azw3", ignoreCase = true) == true -> FileType.MOBI
                    path?.endsWith(".prc", ignoreCase = true) == true -> FileType.MOBI
                    else -> null
                }
            }
        }
    }
}