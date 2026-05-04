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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.data.FolderBookMetadata
import java.io.File
import android.provider.DocumentsContract
import java.security.MessageDigest

class FolderSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
        const val WORK_NAME_ONETIME = "FolderSyncWorker_OneTime"
        const val KEY_METADATA_ONLY = "key_metadata_only"
        const val KEY_TARGET_FOLDER_URI = "key_target_folder_uri"
        private const val SCAN_DB_BATCH_SIZE = 600
        private val syncMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        val workerStart = ReaderPerfLog.nowNanos()
        val isMetadataOnly = inputData.getBoolean(KEY_METADATA_ONLY, false)
        val targetFolderUri = inputData.getString(KEY_TARGET_FOLDER_URI)
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
            ReaderPerfLog.w("FolderSync worker aborted: no linked folders")
            return Result.success()
        }

        val foldersToProcess = if (targetFolderUri.isNullOrBlank()) {
            folders
        } else {
            folders.filter { it.first == targetFolderUri }
        }

        if (foldersToProcess.isEmpty()) {
            ReaderPerfLog.w("FolderSync worker aborted: target folder not linked target=$targetFolderUri")
            return Result.success()
        }

        ReaderPerfLog.d(
            "FolderSync worker start folders=${foldersToProcess.size}/${folders.size} " +
                "target=${targetFolderUri ?: "ALL"} metadataOnly=$isMetadataOnly"
        )

        return withContext(Dispatchers.IO) {
            syncMutex.withLock {
                var allSuccess = true

                for ((uriString, allowedTypes) in foldersToProcess) {
                    val success = performSyncForFolder(uriString, allowedTypes, isMetadataOnly)
                    if (!success) allSuccess = false
                }

                if (jsonString != null) {
                    try {
                        val array = org.json.JSONArray(jsonString)
                        val now = System.currentTimeMillis()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (targetFolderUri.isNullOrBlank() || obj.optString("uri") == targetFolderUri) {
                                obj.put("lastScanTime", now)
                            }
                        }
                        prefs.edit { putString("synced_folders_list_json", array.toString()) }
                    } catch (_: Exception) {}
                }

                val elapsed = ReaderPerfLog.elapsedMs(workerStart)
                ReaderPerfLog.i(
                    "FolderSync worker finished status=${if (allSuccess) "success" else "failure"} " +
                        "folders=${foldersToProcess.size} elapsed=${elapsed}ms"
                )

                if (allSuccess) Result.success() else Result.failure()
            }
        }
    }

    private suspend fun performSyncForFolder(folderUriString: String, allowedFileTypes: Set<FileType>, metadataOnly: Boolean): Boolean {
        if (folderUriString.isBlank()) return true
        val folderUri = folderUriString.toUri()
        val folderStart = ReaderPerfLog.nowNanos()
        var dirsScanned = 0
        var filesSeen = 0
        var supportedBooksSeen = 0
        var newBooks = 0
        var updatedBooks = 0
        var unchangedBooks = 0
        var dbFlushes = 0
        var scanDbFlushes = 0
        var sidecarsImported = 0
        var stoppedForUnlinkedFolder = false

        try {
            if (!isFolderStillLinked(folderUriString)) {
                ReaderPerfLog.w("FolderSync folder skipped: no longer linked folder=$folderUriString")
                return true
            }

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

            ReaderPerfLog.d("FolderSync phase legacy-sidecar-migration skipped")

            val folderMetadataMap = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase metadata-sidecars",
                minLogMs = 25L,
                details = { "metadataOnly=$metadataOnly" }
            ) {
                LocalSyncUtils.getAllFolderMetadata(appContext, folderUri).toMutableMap()
            }
            ReaderPerfLog.d(
                "FolderSync metadata-sidecars records=${folderMetadataMap.size} metadataOnly=$metadataOnly folder=$folderUriString"
            )

            val preloadedSidecars = mutableMapOf<String, Pair<Long, String>>()
            val existingFolderBooks = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase load-existing-db",
                minLogMs = 25L
            ) {
                recentFilesRepository.getFilesBySourceFolder(folderUriString)
            }
            val existingFolderBooksById = existingFolderBooks.associateBy { it.bookId }
            val remoteMetadataUpdates = mutableListOf<RecentFileItem>()

            folderMetadataMap.forEach { (bookId, remoteMeta) ->
                val existingItem = existingFolderBooksById[bookId]

                if (existingItem != null) {
                    if (remoteMeta.lastModifiedTimestamp > existingItem.lastModifiedTimestamp) {
                        Timber.tag("PdfPositionDebug").w("FolderSyncWorker applies remote progress for $bookId | Local Page: ${existingItem.lastPage} -> Remote Page: ${remoteMeta.lastPage}")
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
                        remoteMetadataUpdates.add(itemToUpdate)
                    } else {
                        Timber.tag("PdfPositionDebug").d("FolderSyncWorker: Local meta is newer/equal for $bookId. Ignoring remote. Local Page: ${existingItem.lastPage}")
                    }
                }
            }

            if (remoteMetadataUpdates.isNotEmpty()) {
                recentFilesRepository.addRecentFiles(remoteMetadataUpdates)
                dbFlushes++
                ReaderPerfLog.d(
                    "FolderSync applied remote metadata updates count=${remoteMetadataUpdates.size} folder=$folderUriString"
                )
            }

            if (metadataOnly) {
                sidecarsImported += importAnnotationSidecarsForBooks(
                    folderUri = folderUri,
                    folderUriString = folderUriString,
                    books = existingFolderBooks,
                    phase = "metadata-only"
                )
            }

            if (!metadataOnly) {
                Timber.tag("FolderSync").d("Phase 2: Scanning physical files using raw ContentResolver...")
                val contentResolver = appContext.contentResolver
                val foundBookIds = mutableSetOf<String>()
                val newOrUpdatedItems = mutableListOf<RecentFileItem>()
                val existingItemsMap = existingFolderBooksById.toMutableMap()
                val existingItemsByUri = existingFolderBooks
                    .mapNotNull { item -> item.uriString?.let { uri -> uri to item } }
                    .toMap()
                val legacyItemsByName = existingFolderBooks
                    .asSequence()
                    .filter { it.bookId.startsWith("local_${it.displayName}_") }
                    .groupBy { it.displayName }
                    .mapValues { entry ->
                        ArrayDeque<RecentFileItem>().apply { addAll(entry.value) }
                    }

                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                val dirQueue = ArrayDeque<String>()
                dirQueue.add(rootDocId)

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )

                while (dirQueue.isNotEmpty()) {
                    if (isStopped) break
                    if (!isFolderStillLinked(folderUriString)) {
                        ReaderPerfLog.w("FolderSync folder abort: folder unlinked during scan folder=$folderUriString")
                        stoppedForUnlinkedFolder = true
                        break
                    }
                    val currentDocId = dirQueue.removeFirst()
                    dirsScanned++
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, currentDocId)

                    try {
                        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                            val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                            while (cursor.moveToNext() && !isStopped && !stoppedForUnlinkedFolder) {
                                val docId = cursor.getString(idCol)
                                val name = cursor.getString(nameCol) ?: ""
                                val mimeType = cursor.getString(mimeCol)
                                filesSeen++

                                if (filesSeen % 100 == 0 && !isFolderStillLinked(folderUriString)) {
                                    ReaderPerfLog.w("FolderSync folder abort: folder unlinked after entries=$filesSeen folder=$folderUriString")
                                    stoppedForUnlinkedFolder = true
                                    break
                                }

                                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    if (!name.startsWith(".") && name != "EpistemeSyncData") {
                                        dirQueue.add(docId)
                                    }
                                } else {
                                    val size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                                    val lastModified = if (!cursor.isNull(modCol)) cursor.getLong(modCol) else 0L

                                    val type = getFileType(name, mimeType)
                                    if (type != null && type in allowedFileTypes && !name.endsWith(".json") && !name.startsWith(".")) {
                                        supportedBooksSeen++
                                        val stableId = buildStableBookId(name, rootDocId, docId)
                                        foundBookIds.add(stableId)

                                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                                        val docUriString = docUri.toString()
                                        var existingItem = existingItemsMap[stableId]

                                        if (existingItem != null && existingItem.uriString != docUriString) {
                                            val collidedItem = existingItem
                                            val collidedStableId = computeStableIdForStoredItem(collidedItem, rootDocId)
                                            if (!collidedStableId.isNullOrBlank() && collidedStableId != stableId && collidedStableId != collidedItem.bookId) {
                                                Timber.tag("FolderSync").i("Resolving folder ID collision for ${collidedItem.displayName}: ${collidedItem.bookId} -> $collidedStableId")
                                                migrateFolderBookId(
                                                    folderUriString = folderUriString,
                                                    oldId = collidedItem.bookId,
                                                    newId = collidedStableId,
                                                    folderMetadataMap = folderMetadataMap,
                                                    preloadedSidecars = preloadedSidecars,
                                                    existingItemsMap = existingItemsMap
                                                )
                                                existingItem = existingItemsMap[stableId]
                                            }
                                        }

                                        if (existingItem == null) {
                                            val oldItem = existingItemsByUri[docUriString]?.takeIf { it.bookId != stableId }
                                                ?: legacyItemsByName[name]?.firstOrNull {
                                                    it.bookId != stableId
                                                }
                                            if (oldItem != null) {
                                                val oldId = oldItem.bookId
                                                Timber.tag("FolderSync").i("Migrating book ID for $name from $oldId to $stableId")

                                                migrateFolderBookId(
                                                    folderUriString = folderUriString,
                                                    oldId = oldId,
                                                    newId = stableId,
                                                    folderMetadataMap = folderMetadataMap,
                                                    preloadedSidecars = preloadedSidecars,
                                                    existingItemsMap = existingItemsMap
                                                )
                                                legacyItemsByName[name]?.remove(oldItem)
                                                existingItem = existingItemsMap[stableId]
                                            }
                                        }

                                        if (existingItem == null) {
                                            val remoteMeta = folderMetadataMap[stableId]

                                            val newItem = RecentFileItem(
                                                bookId = stableId,
                                                uriString = docUri.toString(),
                                                type = type,
                                                displayName = name,
                                                timestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                                                lastModifiedTimestamp = remoteMeta?.lastModifiedTimestamp ?: System.currentTimeMillis(),
                                                coverImagePath = null,
                                                title = remoteMeta?.title ?: name,
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
                                                locatorCharOffset = remoteMeta?.locatorCharOffset,
                                                fileSize = size
                                            )
                                            newOrUpdatedItems.add(newItem)
                                            newBooks++
                                        } else {
                                            var needsUpdate = false
                                            var updatedItem = existingItem

                                            if (existingItem.fileSize > 0L && size > 0L && existingItem.fileSize != size) {
                                                Timber.tag("FolderSync").i("File size changed for $name (${existingItem.fileSize} -> $size).")
                                                recentFilesRepository.clearLocalCachesForBook(stableId)
                                                updatedItem = updatedItem.copy(
                                                    fileSize = size,
                                                    lastModifiedTimestamp = lastModified,
                                                    folderTextMetadataParsed = false
                                                )
                                                needsUpdate = true
                                            }

                                            if (updatedItem.isDeleted || !updatedItem.isAvailable) {
                                                updatedItem = updatedItem.copy(isDeleted = false, isAvailable = true)
                                                needsUpdate = true
                                            }

                                            if (updatedItem.uriString != docUri.toString()) {
                                                updatedItem = updatedItem.copy(uriString = docUri.toString())
                                                needsUpdate = true
                                            }

                                            if (needsUpdate) {
                                                newOrUpdatedItems.add(updatedItem)
                                                updatedBooks++
                                            } else {
                                                unchangedBooks++
                                            }
                                        }

                                        val batchLimit = if (scanDbFlushes == 0) 40 else SCAN_DB_BATCH_SIZE
                                        if (newOrUpdatedItems.size >= batchLimit) {
                                            if (!isFolderStillLinked(folderUriString)) {
                                                ReaderPerfLog.w("FolderSync batch dropped: folder unlinked pending=${newOrUpdatedItems.size} folder=$folderUriString")
                                                newOrUpdatedItems.clear()
                                                stoppedForUnlinkedFolder = true
                                                break
                                            }
                                            recentFilesRepository.addRecentFiles(newOrUpdatedItems)
                                            dbFlushes++
                                            scanDbFlushes++
                                            newOrUpdatedItems.clear()
                                        }

                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("FolderSync").e(e, "Failed to query children for docId: $currentDocId")
                    }

                    if (stoppedForUnlinkedFolder) break
                }

                if (!stoppedForUnlinkedFolder && newOrUpdatedItems.isNotEmpty()) {
                    recentFilesRepository.addRecentFiles(newOrUpdatedItems)
                    dbFlushes++
                    scanDbFlushes++
                    newOrUpdatedItems.clear()
                }

                if (!isStopped && !stoppedForUnlinkedFolder) {
                    val idsToRemove = existingItemsMap.keys.filter { it !in foundBookIds }

                    if (idsToRemove.isNotEmpty()) {
                        Timber.tag("FolderSync").i("Cleaning up ${idsToRemove.size} missing folder books.")
                        recentFilesRepository.deleteFilePermanently(idsToRemove)
                    }
                }
            }

            if (!metadataOnly && !isStopped && !stoppedForUnlinkedFolder) {
                val booksForAnnotationSync = ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase load-post-scan-db",
                    minLogMs = 25L
                ) {
                    recentFilesRepository.getFilesBySourceFolder(folderUriString)
                }
                sidecarsImported += importAnnotationSidecarsForBooks(
                    folderUri = folderUri,
                    folderUriString = folderUriString,
                    books = booksForAnnotationSync,
                    phase = "post-scan"
                )
            }

            val elapsed = ReaderPerfLog.elapsedMs(folderStart)
            ReaderPerfLog.i(
                "FolderSync folder finished metadataOnly=$metadataOnly elapsed=${elapsed}ms " +
                    "dirs=$dirsScanned entries=$filesSeen supported=$supportedBooksSeen " +
                    "new=$newBooks updated=$updatedBooks unchanged=$unchangedBooks " +
                    "dbFlushes=$dbFlushes sidecarsImported=$sidecarsImported " +
                    "unlinkedAbort=$stoppedForUnlinkedFolder folder=$folderUriString"
            )

            if (!isStopped && !stoppedForUnlinkedFolder && !metadataOnly) {
                if (recentFilesRepository.hasFolderBooksNeedingTextMetadata(folderUriString)) {
                    ReaderPerfLog.i("FolderSync enqueue text metadata extraction folder=$folderUriString")
                    val metaRequest = OneTimeWorkRequestBuilder<MetadataExtractionWorker>()
                        .setInputData(
                            androidx.work.Data.Builder()
                                .putString(MetadataExtractionWorker.KEY_SOURCE_FOLDER_URI, folderUriString)
                                .build()
                        )
                        .build()
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        MetadataExtractionWorker.WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        metaRequest
                    )
                } else {
                    ReaderPerfLog.d("FolderSync text metadata extraction skipped: no pending books folder=$folderUriString")
                }
            }

            return true

        } catch (e: Exception) {
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return false
        }
    }

    private suspend fun importAnnotationSidecarsForBooks(
        folderUri: android.net.Uri,
        folderUriString: String,
        books: List<RecentFileItem>,
        phase: String
    ): Int {
        if (books.isEmpty()) {
            ReaderPerfLog.d("FolderSync phase annotation-sidecars skipped phase=$phase reason=no-books folder=$folderUriString")
            return 0
        }

        val preloadedSidecars = ReaderPerfLog.measureSuspend(
            name = "FolderSync phase annotation-sidecars",
            minLogMs = 25L,
            details = { "phase=$phase" }
        ) {
            LocalSyncUtils.preloadAnnotationSidecars(appContext, folderUri)
        }

        ReaderPerfLog.d(
            "FolderSync annotation-sidecars records=${preloadedSidecars.size} books=${books.size} phase=$phase folder=$folderUriString"
        )

        if (preloadedSidecars.isEmpty()) return 0

        var imported = 0
        Timber.tag("FolderAnnotationSync").d("Checking annotation sidecars phase=$phase for ${books.size} books...")
        for (book in books) {
            if (isStopped || !isFolderStillLinked(folderUriString)) break

            val sidecarData = preloadedSidecars[book.bookId] ?: continue
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
                imported++
            } else {
                Timber.tag("FolderAnnotationSync").v("Sidecar for ${book.displayName} is not newer. Skipping.")
            }
        }

        ReaderPerfLog.i(
            "FolderSync annotation-sidecars imported=$imported records=${preloadedSidecars.size} phase=$phase folder=$folderUriString"
        )
        return imported
    }

    private fun isFolderStillLinked(folderUriString: String): Boolean {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("synced_folders_list_json", null)
        if (jsonString != null) {
            return try {
                val array = org.json.JSONArray(jsonString)
                (0 until array.length()).any { index ->
                    array.getJSONObject(index).optString("uri") == folderUriString
                }
            } catch (_: Exception) {
                false
            }
        }
        return prefs.getString("synced_folder_uri", null) == folderUriString
    }

    private fun getFileType(name: String, mimeType: String?): FileType? {
        return when (mimeType) {
            "application/pdf" -> FileType.PDF
            "application/epub+zip" -> FileType.EPUB
            "application/vnd.oasis.opendocument.text" -> FileType.ODT
            "application/x-vnd.oasis.opendocument.text-flat-xml" -> FileType.FODT
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileType.DOCX
            "text/html", "application/xhtml+xml" -> FileType.HTML
            else -> resolveFileTypeFromName(name)
        }
    }

    private fun buildStableBookId(name: String, rootDocId: String, docId: String): String {
        val relativePath = buildRelativePath(rootDocId, docId, name)
        if (relativePath.equals(name, ignoreCase = true)) {
            return "local_$name"
        }
        return "local_${name}_${shortHash(relativePath.lowercase())}"
    }

    private fun buildRelativePath(rootDocId: String, docId: String, fallbackName: String): String {
        val rootPath = rootDocId.substringAfter(':', "")
        val docPath = docId.substringAfter(':', "")
        if (docPath.isBlank()) return fallbackName
        val relative = if (rootPath.isNotBlank() && docPath.startsWith(rootPath)) {
            docPath.removePrefix(rootPath).trimStart('/')
        } else {
            docPath.substringAfterLast('/', fallbackName)
        }
        return relative.ifBlank { fallbackName }
    }

    private fun shortHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(12)
    }

    private fun computeStableIdForStoredItem(item: RecentFileItem, rootDocId: String): String? {
        val uriString = item.uriString ?: return null
        return try {
            val docId = DocumentsContract.getDocumentId(uriString.toUri())
            buildStableBookId(item.displayName, rootDocId, docId)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun migrateFolderBookId(
        folderUriString: String,
        oldId: String,
        newId: String,
        folderMetadataMap: MutableMap<String, FolderBookMetadata>,
        preloadedSidecars: MutableMap<String, Pair<Long, String>>,
        existingItemsMap: MutableMap<String, RecentFileItem>
    ) {
        if (oldId == newId) return

        recentFilesRepository.migrateBookIdLocally(oldId, newId)

        val oldMetadata = folderMetadataMap.remove(oldId)
        if (oldMetadata != null && newId !in folderMetadataMap) {
            val migratedMetadata = oldMetadata.copy(bookId = newId)
            LocalSyncUtils.saveMetadataToFolder(appContext, folderUriString.toUri(), migratedMetadata)
            folderMetadataMap[newId] = migratedMetadata
        }

        val oldSidecar = preloadedSidecars.remove(oldId)
        if (oldSidecar != null && newId !in preloadedSidecars) {
            LocalSyncUtils.saveAnnotationSidecar(
                context = appContext,
                sourceFolderUri = folderUriString.toUri(),
                bookId = newId,
                jsonPayload = oldSidecar.second,
                timestamp = oldSidecar.first
            )
            preloadedSidecars[newId] = oldSidecar
        }

        LocalSyncUtils.deleteBookSidecars(appContext, folderUriString.toUri(), oldId)

        existingItemsMap.remove(oldId)
        recentFilesRepository.getFileByBookId(newId)?.let {
            existingItemsMap[newId] = it
        }
    }
}
