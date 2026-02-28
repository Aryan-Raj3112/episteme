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
import kotlinx.coroutines.sync.Mutex // Added import
import kotlinx.coroutines.sync.withLock // Added import
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.aryan.reader.data.LocalSyncUtils

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
        const val WORK_NAME_ONETIME = "FolderSyncWorker_OneTime"
        // Prevent concurrent execution of multiple worker instances
        private val syncMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        Timber.tag("FolderSync").d("Worker: Request received. Waiting for lock...")

        return withContext(Dispatchers.IO) {
            // Acquire lock to ensure only ONE sync runs at a time
            syncMutex.withLock {
                Timber.tag("FolderSync").d("Worker: Lock acquired. Starting Sync.")
                performSync()
            }
        }
    }

    private suspend fun performSync(): Result {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(MainViewModel.KEY_SYNCED_FOLDER_URI, null)

        if (folderUriString.isNullOrBlank()) {
            return Result.success()
        }

        val folderUri = folderUriString.toUri()

        try {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                return Result.failure()
            }

            val documentTree = DocumentFile.fromTreeUri(appContext, folderUri)
            if (documentTree == null || !documentTree.isDirectory) {
                return Result.failure()
            }

            val folderMetadataMap = LocalSyncUtils.getAllFolderMetadata(appContext, folderUri)

            val currentDiskFiles = mutableListOf<DocumentFile>()
            val fileQueue = ArrayDeque<DocumentFile>()
            documentTree.listFiles().let { fileQueue.addAll(it) }

            while (fileQueue.isNotEmpty()) {
                val file = fileQueue.removeAt(0)
                if (file.isDirectory) {
                    // Skip the sync directory itself to avoid recursion
                    if (file.name == ".episteme") continue
                    file.listFiles().let { fileQueue.addAll(it) }
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    if (isValidExtension(name)) {
                        currentDiskFiles.add(file)
                    }
                }
            }

            val foundBookIds = mutableSetOf<String>()

            for (file in currentDiskFiles) {
                val stableId = "local_${file.name}_${file.length()}"
                foundBookIds.add(stableId)

                val existingItem = recentFilesRepository.getFileByBookId(stableId)

                if (existingItem == null) {
                    val remoteMeta = folderMetadataMap[stableId]
                    val type = getFileType(file.name ?: "", file.type) ?: FileType.EPUB

                    // FIX: Always extract basic info (Cover/Title/Author) from the file
                    // regardless of whether we have remote metadata or not.
                    val fileInfo = extractFileInfo(file.uri, type, file.name ?: "Unknown")

                    if (remoteMeta != null) {
                        Timber.tag("FolderSync").d("Worker: Importing existing book from Metadata + File: ${file.name}")

                        val tempItem = RecentFileItem(
                            bookId = stableId,
                            uriString = file.uri.toString(),
                            type = type,
                            displayName = file.name ?: "Unknown",
                            // Use Remote Metadata for progress & timestamps
                            timestamp = remoteMeta.lastModifiedTimestamp,
                            lastModifiedTimestamp = remoteMeta.lastModifiedTimestamp,
                            // Use Extracted Info for Cover/Title/Author
                            coverImagePath = fileInfo.coverPath,
                            title = fileInfo.title ?: remoteMeta.title,
                            author = fileInfo.author ?: remoteMeta.author,

                            isAvailable = true,
                            isDeleted = false,
                            isRecent = false, // FIX: Ensure it doesn't clutter Recents
                            sourceFolderUri = folderUriString,
                            lastChapterIndex = remoteMeta.lastChapterIndex,
                            lastPage = remoteMeta.lastPage,
                            lastPositionCfi = remoteMeta.lastPositionCfi,
                            progressPercentage = remoteMeta.progressPercentage,
                            bookmarksJson = remoteMeta.bookmarksJson
                        )
                        recentFilesRepository.addRecentFile(tempItem)
                    } else {
                        // Fresh Import
                        val newItem = RecentFileItem(
                            bookId = stableId,
                            uriString = file.uri.toString(),
                            type = type,
                            displayName = file.name ?: "Unknown",
                            timestamp = System.currentTimeMillis(),
                            coverImagePath = fileInfo.coverPath,
                            title = fileInfo.title ?: file.name,
                            author = fileInfo.author,
                            isAvailable = true,
                            lastModifiedTimestamp = System.currentTimeMillis(),
                            isDeleted = false,
                            isRecent = false,
                            sourceFolderUri = folderUriString
                        )
                        recentFilesRepository.addRecentFile(newItem)
                    }
                } else if (existingItem.isDeleted) {
                    val resurrected = existingItem.copy(isDeleted = false, isAvailable = true)
                    recentFilesRepository.addRecentFile(resurrected)
                }
            }

            // Cleanup missing files
            val dbFolderBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)
            val idsToRemove = dbFolderBooks.filter { !foundBookIds.contains(it.bookId) }.map { it.bookId }
            if (idsToRemove.isNotEmpty()) {
                recentFilesRepository.deleteFilePermanently(idsToRemove)
            }

            // Reconcile Metadata (Write-back)
            val activeDbBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)

            for (localBook in activeDbBooks) {
                val remoteMeta = folderMetadataMap[localBook.bookId]

                if (remoteMeta == null) {
                    Timber.tag("FolderSync").d("SyncDecision: ${localBook.displayName} -> MISSING remote meta. Creating.")
                    recentFilesRepository.syncLocalMetadataToFolder(localBook.bookId)
                }
                else {
                    val diff = localBook.lastModifiedTimestamp - remoteMeta.lastModifiedTimestamp
                    Timber.tag("FolderSync").d("SyncDecision: ${localBook.displayName} | LocalTS: ${localBook.lastModifiedTimestamp} | RemoteTS: ${remoteMeta.lastModifiedTimestamp} | Diff: $diff ms")

                    if (localBook.lastModifiedTimestamp > remoteMeta.lastModifiedTimestamp) {
                        Timber.tag("FolderSync").d("SyncDecision: Local is NEWER. Pushing to folder.")
                        recentFilesRepository.syncLocalMetadataToFolder(localBook.bookId)
                    }
                    else if (remoteMeta.lastModifiedTimestamp > localBook.lastModifiedTimestamp) {
                        Timber.tag("FolderSync").d("SyncDecision: Remote is NEWER. Pulling to DB.")

                        val updatedItem = localBook.copy(
                            lastChapterIndex = remoteMeta.lastChapterIndex,
                            lastPage = remoteMeta.lastPage,
                            lastPositionCfi = remoteMeta.lastPositionCfi,
                            progressPercentage = remoteMeta.progressPercentage,
                            bookmarksJson = remoteMeta.bookmarksJson,
                            locatorBlockIndex = remoteMeta.locatorBlockIndex,
                            locatorCharOffset = remoteMeta.locatorCharOffset,
                            lastModifiedTimestamp = remoteMeta.lastModifiedTimestamp,
                            timestamp = remoteMeta.lastModifiedTimestamp
                        )

                        recentFilesRepository.addRecentFile(updatedItem)
                    } else {
                        Timber.tag("FolderSync").d("SyncDecision: Timestamps are EQUAL. No action.")
                    }
                }
            }

            prefs.edit {
                putLong(MainViewModel.KEY_LAST_FOLDER_SCAN_TIME, System.currentTimeMillis())
            }

            return Result.success()
        } catch (e: Exception) {
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return Result.failure()
        }
    }

    private data class ExtractedInfo(
        val title: String? = null,
        val author: String? = null,
        val coverPath: String? = null
    )

    // ... rest of the file stays the same (extractFileInfo, isValidExtension, getFileType)
    private suspend fun extractFileInfo(uri: Uri, type: FileType, displayName: String): ExtractedInfo {
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
                                parseContent = false
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
                    title = book.title.takeIf { it.isNotBlank() }
                    author = book.author.takeIf { it.isNotBlank() }
                    book.coverImage?.let {
                        coverPath = recentFilesRepository.saveCoverToCache(it, uri)
                    }
                }
            } else if (type == FileType.PDF) {
                // PDF Title extraction is hard without parsing, stick to displayName for now or implement PdfBox metadata reading
                // For now, we focus on cover.
                pdfCoverGenerator.generateCover(uri)?.let {
                    coverPath = recentFilesRepository.saveCoverToCache(it, uri)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract info for file: $displayName")
        }
        return ExtractedInfo(title, author, coverPath)
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
}