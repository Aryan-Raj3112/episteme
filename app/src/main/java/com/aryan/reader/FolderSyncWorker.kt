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
import java.io.File

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
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val jsonString = prefs.getString("synced_folders_list_json", null)
        val folders = mutableListOf<Pair<String, Set<FileType>>>()

        if (jsonString != null) {
            try {
                val array = org.json.JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uri = obj.getString("uri")
                    val allowedFileTypes = mutableSetOf<FileType>()
                    if (obj.has("allowedFileTypes")) {
                        val typesArray = obj.getJSONArray("allowedFileTypes")
                        for (j in 0 until typesArray.length()) {
                            try { allowedFileTypes.add(FileType.valueOf(typesArray.getString(j))) } catch (_: Exception) {}
                        }
                    } else {
                        allowedFileTypes.addAll(FileType.entries)
                    }
                    folders.add(Pair(uri, allowedFileTypes))
                }
            } catch (e: Exception) { Timber.e(e) }
        } else {
            val single = prefs.getString("synced_folder_uri", null)
            if (single != null) folders.add(Pair(single, FileType.entries.toSet()))
        }

        if (folders.isEmpty()) {
            Timber.tag("FolderSync").w("Worker: No folders linked. Aborting.")
            return Result.success()
        }

        Timber.tag("FolderSync").d("Worker: processing ${folders.size} folders.")

        return withContext(Dispatchers.IO) {
            syncMutex.withLock {
                var allSuccess = true

                for ((uriString, allowedTypes) in folders) {
                    val success = performSyncForFolder(uriString, allowedTypes, isMetadataOnly)
                    if (!success) allSuccess = false
                }

                if (jsonString != null) {
                    try {
                        val array = org.json.JSONArray(jsonString)
                        val now = System.currentTimeMillis()
                        for (i in 0 until array.length()) {
                            array.getJSONObject(i).put("lastScanTime", now)
                        }
                        prefs.edit { putString("synced_folders_list_json", array.toString()) }
                    } catch (_: Exception) {}
                }

                if (allSuccess) Result.success() else Result.failure()
            }
        }
    }

    private suspend fun performSyncForFolder(folderUriString: String, allowedFileTypes: Set<FileType>, metadataOnly: Boolean): Boolean {
        if (folderUriString.isBlank()) return true
        val folderUri = folderUriString.toUri()

        try {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                return false
            }

            val documentTree = DocumentFile.fromTreeUri(appContext, folderUri)
            if (documentTree == null || !documentTree.isDirectory) {
                return false
            }

            Timber.tag("FolderSync").d("Phase 1: Importing JSON metadata from folder...")
            val folderMetadataMap = LocalSyncUtils.getAllFolderMetadata(appContext, folderUri)

            Timber.tag("FolderSync").d("Phase 1.5: Preloading annotation sidecars...")
            val preloadedSidecars = LocalSyncUtils.preloadAnnotationSidecars(appContext, documentTree)

            folderMetadataMap.forEach { (bookId, remoteMeta) ->
                val existingItem = recentFilesRepository.getFileByBookId(bookId)

                if (existingItem != null) {
                    if (remoteMeta.lastModifiedTimestamp > existingItem.lastModifiedTimestamp) {
                        Timber.tag("FolderSync").d("Applying remote update for $bookId (Progress: ${remoteMeta.progressPercentage}%)")
                        val itemToUpdate = existingItem.copy(
                            lastChapterIndex = remoteMeta.lastChapterIndex,
                            lastPage = remoteMeta.lastPage,
                            lastPositionCfi = remoteMeta.lastPositionCfi,
                            progressPercentage = remoteMeta.progressPercentage,
                            bookmarksJson = remoteMeta.bookmarksJson,
                            highlightsJson = remoteMeta.highlightsJson,
                            customName = remoteMeta.customName,
                            locatorBlockIndex = remoteMeta.locatorBlockIndex,
                            locatorCharOffset = remoteMeta.locatorCharOffset,
                            lastModifiedTimestamp = remoteMeta.lastModifiedTimestamp,
                            isRecent = remoteMeta.isRecent || existingItem.isRecent,
                            timestamp = if (remoteMeta.isRecent) remoteMeta.lastModifiedTimestamp else existingItem.timestamp
                        )
                        recentFilesRepository.addRecentFile(itemToUpdate)
                    }
                }
            }

            Timber.tag("FolderAnnotationSync").d("Phase 1.5: Checking annotation sidecars for existing local books...")
            val processedBookIds = mutableSetOf<String>()
            val existingFolderBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)

            for (book in existingFolderBooks) {
                processedBookIds.add(book.bookId)

                val sidecarData = preloadedSidecars[book.bookId]

                if (sidecarData != null) {
                    val (remoteTs, jsonPayload) = sidecarData

                    val localFiles = listOf(
                        File(appContext.filesDir, "annotations/annotation_${book.bookId}.json"),
                        File(appContext.filesDir, "pdf_rich_text/text_${book.bookId}.json"),
                        File(appContext.filesDir, "page_layouts/layout_${book.bookId}.json"),
                        File(appContext.filesDir, "pdf_text_boxes/boxes_${book.bookId}.json")
                    )
                    val localTs = localFiles.maxOfOrNull { if (it.exists()) it.lastModified() else 0L } ?: 0L

                    if (remoteTs > (localTs + 1000)) {
                        Timber.tag("FolderAnnotationSync").i(">>> Newer sidecar found for ${book.displayName}. Importing.")
                        recentFilesRepository.importAnnotationBundle(book.bookId, jsonPayload)
                    } else {
                        Timber.tag("FolderAnnotationSync").v("Sidecar for ${book.displayName} is not newer. Skipping.")
                    }
                }
            }

            if (!metadataOnly) {
                Timber.tag("FolderSync").d("Phase 2: Scanning physical files...")
                val currentDiskFiles = mutableListOf<DocumentFile>()
                val fileQueue = ArrayDeque<DocumentFile>()
                documentTree.listFiles().let { fileQueue.addAll(it) }

                while (fileQueue.isNotEmpty()) {
                    if (isStopped) break

                    val file = fileQueue.removeAt(0)
                    if (file.isDirectory) {
                        file.listFiles().let { fileQueue.addAll(it) }
                    } else if (file.isFile) {
                        val name = file.name ?: ""
                        val type = getFileType(name, file.type)
                        if (type != null && type in allowedFileTypes && !name.endsWith(".json") && !name.startsWith(".")) {
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
                        val remoteMeta = folderMetadataMap[stableId]
                        val type = getFileType(file.name ?: "", file.type) ?: FileType.EPUB
                        val placeholderTitle = file.name ?: "Unknown"

                        val newItem = RecentFileItem(
                            bookId = stableId,
                            uriString = file.uri.toString(),
                            type = type,
                            displayName = file.name ?: "Unknown",
                            timestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                            lastModifiedTimestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                            coverImagePath = null,
                            title = remoteMeta?.title ?: placeholderTitle,
                            author = remoteMeta?.author,
                            isAvailable = true,
                            isDeleted = false,
                            isRecent = remoteMeta?.isRecent ?: false,
                            sourceFolderUri = folderUriString,
                            lastChapterIndex = remoteMeta?.lastChapterIndex,
                            lastPage = remoteMeta?.lastPage,
                            lastPositionCfi = remoteMeta?.lastPositionCfi,
                            progressPercentage = remoteMeta?.progressPercentage,
                            bookmarksJson = remoteMeta?.bookmarksJson,
                            highlightsJson = remoteMeta?.highlightsJson,
                            customName = remoteMeta?.customName,
                            locatorBlockIndex = remoteMeta?.locatorBlockIndex,
                            locatorCharOffset = remoteMeta?.locatorCharOffset
                        )

                        recentFilesRepository.addRecentFile(newItem)
                    } else {
                        if (existingItem.isDeleted || !existingItem.isAvailable) {
                            val revived = existingItem.copy(isDeleted = false, isAvailable = true)
                            recentFilesRepository.addRecentFile(revived)
                        }
                    }

                    if (!processedBookIds.contains(stableId)) {
                        val sidecarData = preloadedSidecars[stableId]

                        if (sidecarData != null) {
                            val (remoteTs, jsonPayload) = sidecarData

                            val localFiles = listOf(
                                File(appContext.filesDir, "annotations/annotation_$stableId.json"),
                                File(appContext.filesDir, "pdf_rich_text/text_$stableId.json"),
                                File(appContext.filesDir, "page_layouts/layout_$stableId.json"),
                                File(appContext.filesDir, "pdf_text_boxes/boxes_$stableId.json")
                            )
                            val localTs = localFiles.maxOfOrNull { if (it.exists()) it.lastModified() else 0L } ?: 0L

                            if (remoteTs > (localTs + 1000)) {
                                Timber.tag("FolderAnnotationSync").i(">>> Newer sidecar found for new book $stableId. Importing.")
                                recentFilesRepository.importAnnotationBundle(stableId, jsonPayload)
                            }
                        }
                    }
                }

                if (!isStopped) {
                    val dbFolderBooks = recentFilesRepository.getFilesBySourceFolder(folderUriString)
                    val idsToRemove = dbFolderBooks.filter { !foundBookIds.contains(it.bookId) }.map { it.bookId }

                    if (idsToRemove.isNotEmpty()) {
                        Timber.tag("FolderSync").i("Cleaning up ${idsToRemove.size} missing folder books.")
                        recentFilesRepository.deleteFilePermanently(idsToRemove)
                    }
                }
            }

            if (!isStopped) {
                Timber.tag("FolderSync").i("Folder scan complete. Enqueuing metadata extraction.")
                val metaRequest = OneTimeWorkRequestBuilder<MetadataExtractionWorker>().build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    MetadataExtractionWorker.WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    metaRequest
                )
            }

            return true

        } catch (e: Exception) {
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return false
        }
    }

    private fun getFileType(name: String, mimeType: String?): FileType? {
        return when {
            mimeType == "application/pdf" || name.endsWith(".pdf", true) -> FileType.PDF
            mimeType == "application/epub+zip" || name.endsWith(".epub", true) -> FileType.EPUB
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || name.endsWith(".docx", true) -> FileType.DOCX
            name.endsWith(".mobi", true) || name.endsWith(".azw3", true) -> FileType.MOBI
            name.endsWith(".md", true) -> FileType.MD
            name.endsWith(".txt", true) -> FileType.TXT
            name.endsWith(".html", true) || name.endsWith(".xhtml", true) || name.endsWith(".htm", true) -> FileType.HTML
            else -> null
        }
    }
}