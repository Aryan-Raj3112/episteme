/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
// FolderSyncWorker.kt
package com.aryan.reader

import android.content.Context
import android.net.Uri
import timber.log.Timber
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
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
    private val epubParser = EpubParser(appContext)
    private val mobiParser = MobiParser(appContext)
    private val pdfCoverGenerator = PdfCoverGenerator(appContext)

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
    }

    override suspend fun doWork(): Result {
        Timber.d("FolderSyncWorker: Starting Sync Check.")
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(MainViewModel.KEY_SYNCED_FOLDER_URI, null)

        if (folderUriString.isNullOrBlank()) {
            Timber.d("No sync folder configured. Worker stopping.")
            return Result.success()
        }

        val folderUri = folderUriString.toUri()

        return withContext(Dispatchers.IO) {
            try {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        folderUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    Timber.e("Lost permission to sync folder. User needs to re-select.")
                    return@withContext Result.failure()
                }

                val documentTree = DocumentFile.fromTreeUri(appContext, folderUri)
                if (documentTree == null || !documentTree.isDirectory) {
                    Timber.e("Could not read the synced folder URI.")
                    return@withContext Result.failure()
                }

                val currentDiskFiles = mutableListOf<DocumentFile>()
                val fileQueue = ArrayDeque<DocumentFile>()
                documentTree.listFiles().let { fileQueue.addAll(it) }

                while (fileQueue.isNotEmpty()) {
                    val file = fileQueue.removeAt(0)
                    if (file.isDirectory) {
                        file.listFiles().let { fileQueue.addAll(it) }
                    } else if (file.isFile) {
                        val name = file.name ?: ""
                        if (isValidExtension(name)) {
                            currentDiskFiles.add(file)
                        }
                    }
                }

                val foundBookIds = mutableSetOf<String>()
                var addedCount = 0

                for (file in currentDiskFiles) {
                    val bookId = generateFastId(file)
                    foundBookIds.add(bookId)

                    val existingItem = recentFilesRepository.getFileByBookId(bookId)

                    if (existingItem == null) {
                        val type = getFileType(file.name ?: "", file.type)
                        if (type != null) {
                            addBookToDatabase(file.uri, type, bookId, file.name ?: "Unknown", folderUriString)
                            addedCount++
                        }
                    }
                }

                val dbFolderBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)

                val idsToMarkUnavailable = dbFolderBooks
                    .filter { !foundBookIds.contains(it.bookId) }
                    .map { it.bookId }

                if (idsToMarkUnavailable.isNotEmpty()) {
                    Timber.d("Reconciliation: Marking ${idsToMarkUnavailable.size} books as unavailable (file missing from disk).")
                    recentFilesRepository.markAsDeleted(idsToMarkUnavailable)
                }

                // 5. Update Timestamp
                if (addedCount > 0 || idsToMarkUnavailable.isNotEmpty()) {
                    Timber.d("Folder Sync Complete. Added: $addedCount, Removed: ${idsToMarkUnavailable.size}")
                    prefs.edit {
                        putLong(MainViewModel.KEY_LAST_FOLDER_SCAN_TIME, System.currentTimeMillis())
                    }
                }

                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Error during folder sync worker execution.")
                Result.failure()
            }
        }
    }

    private fun generateFastId(file: DocumentFile): String {
        // Robust ID for local files without hashing content
        return "local_${file.name}_${file.length()}_${file.lastModified()}"
    }

    private fun isValidExtension(name: String): Boolean {
        return name.endsWith(".pdf", true) ||
                name.endsWith(".epub", true) ||
                name.endsWith(".mobi", true) ||
                name.endsWith(".azw3", true) ||
                name.endsWith(".md", true)
    }

    private fun getFileType(name: String, mimeType: String?): FileType? {
        return when {
            mimeType == "application/pdf" || name.endsWith(".pdf", true) -> FileType.PDF
            mimeType == "application/epub+zip" || name.endsWith(".epub", true) -> FileType.EPUB
            name.endsWith(".mobi", true) || name.endsWith(".azw3", true) -> FileType.MOBI
            name.endsWith(".md", true) -> FileType.MD
            name.endsWith(".txt", true) -> FileType.TXT
            else -> null
        }
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

        try {
            if (type == FileType.EPUB || type == FileType.MOBI) {
                val book = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        if (type == FileType.EPUB) {
                            epubParser.createEpubBook(
                                inputStream = inputStream,
                                originalBookNameHint = displayName,
                                parseContent = false // Metadata only
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
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata for in-place file: $displayName")
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
            isRecent = false, // Does not show on Home screen automatically
            sourceFolderUri = sourceFolderUri
        )
        recentFilesRepository.addRecentFile(newItem)
    }
}