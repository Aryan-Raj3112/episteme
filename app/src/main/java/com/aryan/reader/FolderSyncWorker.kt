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
import timber.log.Timber
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.aryan.reader.data.LocalSyncUtils

class FolderSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
        const val WORK_NAME_ONETIME = "FolderSyncWorker_OneTime"
        const val KEY_METADATA_ONLY = "key_metadata_only"
        private val syncMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        val isMetadataOnly = inputData.getBoolean(KEY_METADATA_ONLY, false)
        // Check if folder is still linked before starting
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(MainViewModel.KEY_SYNCED_FOLDER_URI)) {
            Timber.tag("FolderSync").w("Worker: Folder unlinked. Aborting work.")
            return Result.success()
        }

        Timber.tag("FolderSync").d("Worker: Request received (MetadataOnly=$isMetadataOnly). Waiting for lock...")

        return withContext(Dispatchers.IO) {
            syncMutex.withLock {
                Timber.tag("FolderSync").d("Worker: Lock acquired. Starting Sync.")
                performSync(isMetadataOnly)
            }
        }
    }

    private suspend fun performSync(metadataOnly: Boolean): Result {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(MainViewModel.KEY_SYNCED_FOLDER_URI, null)

        if (folderUriString.isNullOrBlank()) return Result.success()
        val folderUri = folderUriString.toUri()

        try {
            // Permission checks ...
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

            // PHASE 1: IMPORT METADATA (Always run this to catch updates from other devices)
            Timber.tag("FolderSync").d("Phase 1: Importing JSON metadata from folder...")
            val folderMetadataMap = LocalSyncUtils.getAllFolderMetadata(appContext, folderUri)

            // Apply updates to local DB
            folderMetadataMap.forEach { (bookId, remoteMeta) ->
                val existingItem = recentFilesRepository.getFileByBookId(bookId)

                if (existingItem != null) {
                    // Update local if remote is newer
                    if (remoteMeta.lastModifiedTimestamp > existingItem.lastModifiedTimestamp) {
                        Timber.tag("FolderSync").d("Applying remote update for $bookId (Progress: ${remoteMeta.progressPercentage}%)")
                        val itemToUpdate = existingItem.copy(
                            lastChapterIndex = remoteMeta.lastChapterIndex,
                            lastPage = remoteMeta.lastPage,
                            lastPositionCfi = remoteMeta.lastPositionCfi,
                            progressPercentage = remoteMeta.progressPercentage,
                            bookmarksJson = remoteMeta.bookmarksJson,
                            locatorBlockIndex = remoteMeta.locatorBlockIndex,
                            locatorCharOffset = remoteMeta.locatorCharOffset,
                            lastModifiedTimestamp = remoteMeta.lastModifiedTimestamp,
                            // Crucial: If remote says it's recent, we make it recent locally
                            isRecent = remoteMeta.isRecent || existingItem.isRecent,
                            timestamp = if (remoteMeta.isRecent) remoteMeta.lastModifiedTimestamp else existingItem.timestamp
                        )
                        recentFilesRepository.addRecentFile(itemToUpdate)
                    }
                }
                // We do NOT create new items from JSON alone. We wait for Phase 2 to find the file.
            }

            // PHASE 2: SCAN PHYSICAL FILES (Only if !metadataOnly)
            if (!metadataOnly) {
                Timber.tag("FolderSync").d("Phase 2: Scanning physical files...")
                val currentDiskFiles = mutableListOf<DocumentFile>()
                val fileQueue = ArrayDeque<DocumentFile>()
                documentTree.listFiles().let { fileQueue.addAll(it) }

                while (fileQueue.isNotEmpty()) {
                    if (isStopped) break

                    val file = fileQueue.removeAt(0)
                    if (file.isDirectory) {
                        // REMOVED: if (file.name == ".episteme") continue
                        file.listFiles().let { fileQueue.addAll(it) }
                    } else if (file.isFile) {
                        val name = file.name ?: ""
                        // UPDATED: Ensure we ignore .json files and hidden files during book scan
                        if (isValidExtension(name) && !name.endsWith(".json") && !name.startsWith(".")) {
                            currentDiskFiles.add(file)
                        }
                    }
                }

                val foundBookIds = mutableSetOf<String>()

                for (file in currentDiskFiles) {
                    if (isStopped) break

                    val stableId = "local_${file.name}_${file.length()}"
                    foundBookIds.add(stableId)

                    val existingItem = recentFilesRepository.getFileByBookId(stableId)

                    if (existingItem == null) {
                        // NEW FILE DISCOVERED
                        val remoteMeta = folderMetadataMap[stableId]
                        val type = getFileType(file.name ?: "", file.type) ?: FileType.EPUB
                        val placeholderTitle = file.name ?: "Unknown"

                        val newItem = RecentFileItem(
                            bookId = stableId,
                            uriString = file.uri.toString(),
                            type = type,
                            displayName = file.name ?: "Unknown",
                            // Use remote timestamp if available, else NOW
                            timestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                            lastModifiedTimestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                            coverImagePath = null,
                            title = remoteMeta?.title ?: placeholderTitle,
                            author = remoteMeta?.author,
                            isAvailable = true,
                            isDeleted = false,
                            // If remote says recent, mark it. If new and no remote data, it is NOT recent.
                            isRecent = remoteMeta?.isRecent ?: false,
                            sourceFolderUri = folderUriString,
                            lastChapterIndex = remoteMeta?.lastChapterIndex,
                            lastPage = remoteMeta?.lastPage,
                            lastPositionCfi = remoteMeta?.lastPositionCfi,
                            progressPercentage = remoteMeta?.progressPercentage,
                            bookmarksJson = remoteMeta?.bookmarksJson,
                            locatorBlockIndex = remoteMeta?.locatorBlockIndex,
                            locatorCharOffset = remoteMeta?.locatorCharOffset
                        )

                        // Insert. Note: We do NOT call syncLocalMetadataToFolder here.
                        // It is "Clean" until the user opens it.
                        recentFilesRepository.addRecentFile(newItem)
                    } else {
                        // EXISTING FILE - Just ensure it is marked available
                        if (existingItem.isDeleted || !existingItem.isAvailable) {
                            val revived = existingItem.copy(isDeleted = false, isAvailable = true)
                            recentFilesRepository.addRecentFile(revived)
                        }
                        // Metadata updates were already handled in Phase 1
                    }
                }

                // Cleanup removed files
                if (!isStopped) {
                    val dbFolderBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)
                    val idsToRemove = dbFolderBooks.filter { !foundBookIds.contains(it.bookId) }.map { it.bookId }

                    if (idsToRemove.isNotEmpty()) {
                        Timber.tag("FolderSync").i("Cleaning up ${idsToRemove.size} missing folder books.")
                        recentFilesRepository.deleteFilePermanently(idsToRemove)
                    }
                }
            }

            prefs.edit { putLong(MainViewModel.KEY_LAST_FOLDER_SCAN_TIME, System.currentTimeMillis()) }

            if (!isStopped) {
                Timber.tag("FolderSync").i("Folder scan complete. Enqueuing metadata extraction.")
                val metaRequest = OneTimeWorkRequestBuilder<MetadataExtractionWorker>().build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    MetadataExtractionWorker.WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    metaRequest
                )
            }

            return Result.success()

        } catch (e: Exception) {
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return Result.failure()
        }
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