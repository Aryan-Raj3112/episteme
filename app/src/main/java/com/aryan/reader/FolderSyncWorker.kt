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
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.AndroidBookArtifactPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.data.FolderBookMetadata
import com.aryan.reader.data.effectiveReadingPositionModifiedTimestamp
import com.aryan.reader.data.toSharedFolderBookMetadata
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.EpubBookmark
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.LocalFolderScanStatus
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.reader.ReaderBookmark
import java.io.File
import java.util.concurrent.TimeUnit
import android.provider.DocumentsContract

class FolderSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)
    private val pendingAnnotationExports =
        com.aryan.reader.data.AppDatabase.getDatabase(appContext).pendingFolderAnnotationExportDao()

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
        const val WORK_NAME_ONETIME = "FolderSyncWorker_OneTime"
        const val KEY_METADATA_ONLY = "key_metadata_only"
        const val KEY_TARGET_FOLDER_URI = "key_target_folder_uri"
        private const val KEY_CLOUD_ACCOUNT_ID = "key_cloud_account_id"
        private const val CLOUD_INDEX_WORK_PREFIX = "FolderSyncWorker_CloudIndex"
        private val syncMutex = Mutex()

        /** Index a completed app-private cloud materialization after download. */
        fun enqueueCloudFolderIndex(
            context: Context,
            accountId: String,
            rootId: String,
            metadataOnly: Boolean = false,
            localUri: String? = null,
        ) {
            val normalizedAccount = accountId.trim().takeIf { it.isNotBlank() } ?: return
            val normalizedRoot = rootId.trim().takeIf { it.isNotBlank() } ?: return
            val targetUri = localUri?.trim()?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    cloudFolderAppRootDirectory(context.applicationContext.filesDir, normalizedRoot)
                        .toURI()
                        .toString()
                }.getOrNull()
                ?: return
            val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
                .setInputData(
                    androidx.work.Data.Builder()
                        .putBoolean(KEY_METADATA_ONLY, metadataOnly)
                        .putString(KEY_TARGET_FOLDER_URI, targetUri)
                        .putString(KEY_CLOUD_ACCOUNT_ID, normalizedAccount)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$CLOUD_INDEX_WORK_PREFIX:$normalizedRoot",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        val workerStart = ReaderPerfLog.nowNanos()
        val isMetadataOnly = inputData.getBoolean(KEY_METADATA_ONLY, false)
        val targetFolderUri = inputData.getString(KEY_TARGET_FOLDER_URI)
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null)
        val requestedCloudAccount = inputData.getString(KEY_CLOUD_ACCOUNT_ID)?.trim()
            ?.takeIf { it.isNotBlank() }
        val currentAccount = AuthRepository(appContext).getSignedInUser()?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
        if (requestedCloudAccount != null && requestedCloudAccount != currentAccount) {
            ReaderPerfLog.w("FolderSync cloud index aborted: account changed")
            return Result.success()
        }
        val configuredFolders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = jsonString,
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null),
            syncableTypes = ANDROID_SYNCABLE_FILE_TYPES
        )
        val accountId = requestedCloudAccount ?: currentAccount
        val workerOperation = cloudFolderOperationId(
            "folder-index-worker",
            accountId.orEmpty(),
            targetFolderUri.orEmpty(),
            isMetadataOnly,
            runAttemptCount,
        )
        val workerCorrelation = cloudFolderSyncCorrelationId(
            "folder-index-worker",
            accountId.orEmpty(),
            targetFolderUri.orEmpty(),
        )
        cloudFolderLogD(
            "event=folder_index_worker_start operation=$workerOperation correlation=$workerCorrelation " +
                "account=${cloudFolderSafeId(accountId)} target=${cloudFolderSafeUri(targetFolderUri?.toUri())} " +
                "metadataOnly=$isMetadataOnly attempt=$runAttemptCount",
        )
        val appManagedFolders = accountId?.let { id ->
            CloudFolderAppStoragePrefs.load(appContext, id)
                .map { it.toSyncedFolder(appContext.filesDir) }
        }.orEmpty()
        val folders = configuredFolders + appManagedFolders

        if (folders.isEmpty()) {
            cloudFolderLogD(
                "event=folder_index_worker_end operation=$workerOperation correlation=$workerCorrelation " +
                    "result=skipped reason=no_folders",
            )
            ReaderPerfLog.w("FolderSync worker aborted: no linked folders")
            return Result.success()
        }

        val enabledFolders = folders.filter { it.localSyncEnabled }
        val foldersToProcess = if (targetFolderUri.isNullOrBlank()) {
            enabledFolders
        } else {
            enabledFolders.filter { it.uriString == targetFolderUri }
        }

        if (foldersToProcess.isEmpty()) {
            cloudFolderLogD(
                "event=folder_index_worker_end operation=$workerOperation correlation=$workerCorrelation " +
                    "result=skipped reason=no_target_folder",
            )
            ReaderPerfLog.w("FolderSync worker aborted: target folder not linked or disabled target=$targetFolderUri")
            return Result.success()
        }

        cloudFolderLogI(
            "event=folder_index_start operation=$workerOperation correlation=$workerCorrelation " +
                "account=${cloudFolderSafeId(accountId)} folders=${foldersToProcess.size} " +
                "metadataOnly=$isMetadataOnly",
        )

        ReaderPerfLog.d(
            "FolderSync worker start folders=${foldersToProcess.size}/${folders.size} " +
                "target=${targetFolderUri ?: "ALL"} metadataOnly=$isMetadataOnly"
        )

        return withContext(Dispatchers.IO) {
            syncMutex.withLock {
                var allSuccess = true
                val completedScanUris = mutableSetOf<String>()

                for (folderConfig in foldersToProcess) {
                    val outcome = performSyncForFolder(folderConfig, isMetadataOnly)
                    if (!outcome.success) allSuccess = false
                    if (outcome.completedScan) completedScanUris += folderConfig.uriString
                }

                // lastScanTime is a watermark for a complete physical scan,
                // not an attempt.  Never advance it for a failed/partial SAF
                // enumeration, an unlinked folder, or a metadata-only pass.
                if (jsonString != null && completedScanUris.isNotEmpty()) {
                    try {
                        val array = org.json.JSONArray(jsonString)
                        val now = System.currentTimeMillis()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optString("uri") in completedScanUris) {
                                obj.put("lastScanTime", now)
                            }
                        }
                        prefs.edit { putString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, array.toString()) }
                    } catch (_: Exception) {}
                }
                val completedAppRoots = foldersToProcess
                    .filter { it.isAppManaged && it.uriString in completedScanUris }
                if (completedAppRoots.isNotEmpty() && accountId != null) {
                    val now = System.currentTimeMillis()
                    completedAppRoots.forEach { folder ->
                        folder.cloudRootId?.let { rootId ->
                            CloudFolderAppStoragePrefs.updateLastScanTime(
                                context = appContext,
                                accountId = accountId,
                                rootId = rootId,
                                timestamp = now,
                            )
                        }
                    }
                    CloudFolderSyncEvents.notifyStateChanged()
                }

                val elapsed = ReaderPerfLog.elapsedMs(workerStart)
                ReaderPerfLog.i(
                    "FolderSync worker finished status=${if (allSuccess) "success" else "failure"} " +
                        "folders=${foldersToProcess.size} elapsed=${elapsed}ms"
                )
                cloudFolderLogI(
                    "event=folder_index_end operation=$workerOperation correlation=$workerCorrelation " +
                        "result=${if (allSuccess) "success" else "retry"} folders=${foldersToProcess.size} " +
                        "elapsedMs=$elapsed",
                )

                // A provider outage/partial enumeration is transient from the
                // worker's perspective.  Retry so a later complete scan can
                // safely reconcile deletions; never report it as a terminal
                // failure that leaves the folder stale indefinitely.
                if (allSuccess) Result.success() else Result.retry()
            }
        }
    }

    private data class FolderSyncOutcome(
        val success: Boolean,
        val completedScan: Boolean
    )

    private suspend fun performSyncForFolder(
        folderConfig: SyncedFolder,
        metadataOnly: Boolean
    ): FolderSyncOutcome {
        val folderUriString = folderConfig.uriString
        val allowedFileTypes = folderConfig.allowedFileTypes
        if (folderUriString.isBlank()) return FolderSyncOutcome(success = true, completedScan = false)
        val folderUri = folderUriString.toUri()
        val safeRoot = cloudFolderSafeId(folderConfig.cloudRootId ?: folderUriString)
        val operation = cloudFolderOperationId(
            "folder-index",
            inputData.getString(KEY_CLOUD_ACCOUNT_ID).orEmpty(),
            folderConfig.cloudRootId ?: folderUriString,
            metadataOnly,
        )
        val correlation = cloudFolderSyncCorrelationId(
            "folder-index",
            inputData.getString(KEY_CLOUD_ACCOUNT_ID).orEmpty(),
            folderConfig.cloudRootId ?: folderUriString,
        )
        cloudFolderLogD(
            "event=folder_index_folder_start operation=$operation correlation=$correlation " +
                "root=$safeRoot folder=${cloudFolderSafeUri(folderUri)} metadataOnly=$metadataOnly " +
                "appManaged=${folderConfig.isAppManaged}",
        )
        val appStorageRoot = if (folderConfig.isAppManaged) {
            val rootId = folderConfig.cloudRootId?.trim()?.takeIf { it.isNotBlank() }
                ?: return FolderSyncOutcome(success = false, completedScan = false)
            runCatching { cloudFolderAppRootDirectory(appContext.filesDir, rootId) }.getOrNull()
                ?: return FolderSyncOutcome(success = false, completedScan = false)
        } else {
            null
        }
        val folderStart = ReaderPerfLog.nowNanos()
        var dirsScanned = 0
        var filesSeen = 0
        var supportedBooksSeen = 0
        var dbFlushes = 0
        var sidecarsImported = 0
        var stoppedForUnlinkedFolder = false

        try {
            if (!isFolderStillLinked(folderConfig)) {
                ReaderPerfLog.w("FolderSync folder skipped: no longer linked folder=$folderUriString")
                return FolderSyncOutcome(success = true, completedScan = false)
            }

            val documentTree = if (appStorageRoot == null) {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        folderUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    return FolderSyncOutcome(success = false, completedScan = false)
                }
                DocumentFile.fromTreeUri(appContext, folderUri)
                    ?.takeIf { it.isDirectory }
                    ?: return FolderSyncOutcome(success = false, completedScan = false)
            } else {
                if (!appStorageRoot.isDirectory) {
                    // A registry entry is created only after a complete cloud
                    // materialization. A missing directory is therefore a
                    // transient recovery condition, not an empty folder that
                    // should reconcile and delete indexed books.
                    return FolderSyncOutcome(success = false, completedScan = false)
                }
                null
            }

            ReaderPerfLog.d(
                "FolderSync phase legacy-sidecar-migration mapped-to-shared " +
                    "appManaged=${folderConfig.isAppManaged}"
            )

            val folderMetadataMap = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase metadata-sidecars",
                minLogMs = 25L,
                details = { "metadataOnly=$metadataOnly" }
            ) {
                if (appStorageRoot != null) {
                    LocalSyncUtils.getAllFolderMetadataFromAppStorage(appStorageRoot).toMutableMap()
                } else {
                    LocalSyncUtils.getAllFolderMetadata(appContext, folderUri).toMutableMap()
                }
            }
            ReaderPerfLog.d(
                "FolderSync metadata-sidecars records=${folderMetadataMap.size} metadataOnly=$metadataOnly folder=$folderUriString"
            )

            val existingFolderBooks = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase load-existing-db",
                minLogMs = 25L
            ) {
                recentFilesRepository.getFilesBySourceFolder(folderUriString)
            }
            val existingItemsMap = existingFolderBooks.associateBy { it.bookId }.toMutableMap()

            val scanResult = if (metadataOnly) {
                AndroidFolderScanResult(scanStatus = LocalFolderScanStatus.NOT_SCANNED)
            } else {
                ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase scan-folder",
                    minLogMs = 25L
                ) {
                    if (appStorageRoot != null) {
                        scanAppStorageFiles(
                            root = appStorageRoot,
                            folderUriString = folderUriString,
                            allowedFileTypes = allowedFileTypes,
                        )
                    } else {
                        scanFolderFiles(
                            folderUri = folderUri,
                            folderUriString = folderUriString,
                            allowedFileTypes = allowedFileTypes
                        )
                    }
                }
            }
            dirsScanned = scanResult.dirsScanned
            filesSeen = scanResult.filesSeen
            supportedBooksSeen = scanResult.files.size
            stoppedForUnlinkedFolder = scanResult.stoppedForUnlinkedFolder

            if (isStopped || stoppedForUnlinkedFolder) {
                ReaderPerfLog.w(
                    "FolderSync folder aborted before shared engine stopped=$isStopped " +
                        "unlinkedAbort=$stoppedForUnlinkedFolder folder=$folderUriString"
                )
                return FolderSyncOutcome(success = true, completedScan = false)
            }

            if (!metadataOnly && scanResult.scanStatus != LocalFolderScanStatus.COMPLETE) {
                ReaderPerfLog.w(
                    "FolderSync folder scan incomplete; preserving existing DB rows and watermark " +
                        "status=${scanResult.scanStatus} folder=$folderUriString"
                )
                return FolderSyncOutcome(success = false, completedScan = false)
            }

            val nowMillis = System.currentTimeMillis()
            val folder = SyncedFolder(
                uriString = folderUriString,
                name = documentTree?.name ?: folderConfig.name,
                lastScanTime = folderConfig.lastScanTime,
                allowedFileTypes = allowedFileTypes,
                localSyncEnabled = true,
                cloudRootId = folderConfig.cloudRootId,
                isAppManaged = folderConfig.isAppManaged,
            )
            val sharedState = SharedReaderScreenState(
                rawLibraryBooks = existingFolderBooks.map { it.toFolderSyncSharedBookItem() },
                syncedFolders = listOf(folder)
            )
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = sharedState,
                folder = folder,
                files = scanResult.files,
                remoteMetadata = folderMetadataMap.mapValues { it.value.toSharedFolderBookMetadata() },
                nowMillis = nowMillis,
                metadataOnly = metadataOnly,
                scanStatus = scanResult.scanStatus
            )
            cloudFolderLogD(
                "event=folder_index_apply operation=$operation correlation=$correlation root=$safeRoot " +
                    "result=success metadataOnly=$metadataOnly scanStatus=${scanResult.scanStatus.name} " +
                    "scanned=${scanResult.files.size} new=${syncResult.stats.newBooks} " +
                    "updated=${syncResult.stats.updatedBooks} remoteMetadataUpdates=${syncResult.stats.remoteMetadataUpdates} " +
                    "removed=${syncResult.stats.removedBooks} migrations=${syncResult.idMigrations.size}",
            )

            if (syncResult.idMigrations.isNotEmpty()) {
                val preloadedSidecars = ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase migration-sidecars",
                    minLogMs = 25L
                ) {
                    if (appStorageRoot != null) {
                        LocalSyncUtils.preloadAnnotationSidecarsFromAppStorage(appStorageRoot).toMutableMap()
                    } else {
                        LocalSyncUtils.preloadAnnotationSidecars(appContext, folderUri).toMutableMap()
                    }
                }
                val migrationsSucceeded = syncResult.idMigrations.all { (oldId, newId) ->
                    Timber.tag("FolderSync").i("Migrating folder book ID via shared engine $oldId -> $newId")
                    migrateFolderBookId(
                        folderUriString = folderUriString,
                        oldId = oldId,
                        newId = newId,
                        folderMetadataMap = folderMetadataMap,
                        preloadedSidecars = preloadedSidecars,
                        existingItemsMap = existingItemsMap,
                        appStorageRoot = appStorageRoot,
                    )
                }
                if (!migrationsSucceeded) {
                    ReaderPerfLog.w(
                        "FolderSync folder aborted: sidecar migration could not be committed folder=$folderUriString"
                    )
                    return FolderSyncOutcome(success = false, completedScan = false)
                }
            }

            if (!isFolderStillLinked(folderConfig)) {
                ReaderPerfLog.w("FolderSync folder abort: folder unlinked before DB write folder=$folderUriString")
                stoppedForUnlinkedFolder = true
                return FolderSyncOutcome(success = true, completedScan = false)
            }

            val scannedFilesById = scanResult.files.associateBy { it.stableBookId }
            val syncedItems = syncResult.state.rawLibraryBooks.map { book ->
                val existing = existingItemsMap[book.id]
                val metadata = appliedMetadataFor(
                    book = book,
                    existing = existing,
                    metadata = folderMetadataMap[book.id]
                )
                book.toFolderSyncRecentFileItem(
                    existing = existing,
                    appliedMetadata = metadata,
                    scannedFile = scannedFilesById[book.id],
                    nowMillis = nowMillis
                )
            }
            folderMetadataMap.forEach { (bookId, metadata) ->
                val before = existingItemsMap[bookId]
                val after = syncedItems.firstOrNull { it.bookId == bookId } ?: return@forEach
                cloudFolderLogD(
                    "event=folder_metadata_apply operation=$operation correlation=$correlation " +
                        "root=$safeRoot book=${cloudFolderSafeId(bookId)} " +
                        "remoteTs=${metadata.lastModifiedTimestamp} " +
                        "beforeReadTs=${before?.effectiveReadingPositionModifiedTimestamp() ?: 0L} " +
                        "afterReadTs=${after.effectiveReadingPositionModifiedTimestamp()} " +
                        "beforePage=${before?.lastPage ?: "none"} afterPage=${after.lastPage ?: "none"} " +
                        "beforeChapter=${before?.lastChapterIndex ?: "none"} afterChapter=${after.lastChapterIndex ?: "none"} " +
                        "beforeProgress=${before?.progressPercentage ?: "none"} afterProgress=${after.progressPercentage ?: "none"} " +
                        "beforeBookmarks=${before?.bookmarksJson?.let { "present(${it.length})" } ?: "none"} " +
                        "afterBookmarks=${after.bookmarksJson?.let { "present(${it.length})" } ?: "none"}",
                )
            }
            val changedItems = syncedItems.filter { item -> existingItemsMap[item.bookId] != item }

            changedItems
                .filter { item ->
                    val previous = existingItemsMap[item.bookId]
                    previous != null && folderFileContentChanged(previous, item)
                }
                .forEach { item ->
                    Timber.tag("FolderSync").i("File content changed for ${item.displayName}; refreshing extracted metadata.")
                    recentFilesRepository.clearLocalCachesForBook(item.bookId)
                }

            if (changedItems.isNotEmpty()) {
                recentFilesRepository.addRecentFiles(changedItems)
                dbFlushes++
            }

            if (!metadataOnly && syncResult.removedBookIds.isNotEmpty()) {
                Timber.tag("FolderSync").i("Cleaning up ${syncResult.removedBookIds.size} missing folder books.")
                recentFilesRepository.deleteFilePermanently(syncResult.removedBookIds.toList())
            }

            val booksForAnnotationSync = if (metadataOnly) {
                syncedItems
            } else {
                ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase load-post-scan-db",
                    minLogMs = 25L
                ) {
                    recentFilesRepository.getFilesBySourceFolder(folderUriString)
                }
            }
            sidecarsImported += importAnnotationSidecarsForBooks(
                folderUri = folderUri,
                folderUriString = folderUriString,
                books = booksForAnnotationSync,
                phase = if (metadataOnly) "metadata-only" else "post-scan",
                appStorageRoot = appStorageRoot,
                rootId = folderConfig.cloudRootId,
            )
            cloudFolderLogD(
                "event=folder_index_sidecars operation=$operation correlation=$correlation root=$safeRoot " +
                    "phase=${if (metadataOnly) "metadata_only" else "post_scan"} " +
                    "imported=$sidecarsImported",
            )

            val elapsed = ReaderPerfLog.elapsedMs(folderStart)
            ReaderPerfLog.i(
                "FolderSync folder finished metadataOnly=$metadataOnly elapsed=${elapsed}ms " +
                    "dirs=$dirsScanned entries=$filesSeen supported=$supportedBooksSeen " +
                    "new=${syncResult.stats.newBooks} updated=${syncResult.stats.updatedBooks} " +
                    "remoteUpdates=${syncResult.stats.remoteMetadataUpdates} unchanged=${syncResult.stats.unchangedBooks} " +
                    "removed=${syncResult.stats.removedBooks} migrated=${syncResult.stats.migratedBooks} " +
                    "dbFlushes=$dbFlushes sidecarsImported=$sidecarsImported " +
                    "unlinkedAbort=$stoppedForUnlinkedFolder folder=$folderUriString"
            )

            if (!isStopped && !stoppedForUnlinkedFolder && !metadataOnly) {
                if (recentFilesRepository.hasFolderBooksNeedingTextMetadata(folderUriString)) {
                    ReaderPerfLog.i("FolderSync enqueue metadata extraction folder=$folderUriString")
                    val metaRequest = OneTimeWorkRequestBuilder<MetadataExtractionWorker>()
                        .setInputData(
                            androidx.work.Data.Builder()
                                .putString(MetadataExtractionWorker.KEY_SOURCE_FOLDER_URI, folderUriString)
                                .putString(
                                    MetadataExtractionWorker.KEY_CLOUD_ROOT_ID,
                                    folderConfig.cloudRootId.orEmpty(),
                                )
                                .build()
                        )
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            METADATA_EXTRACTION_RETRY_BACKOFF_SECONDS,
                            TimeUnit.SECONDS
                        )
                        .build()
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        MetadataExtractionWorker.WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        metaRequest
                    )
                    cloudFolderLogI(
                        "event=metadata_extraction_enqueue operation=$operation correlation=$correlation " +
                            "root=$safeRoot result=queued reason=folder_index",
                    )
                } else {
                    ReaderPerfLog.d("FolderSync metadata extraction skipped: no pending books folder=$folderUriString")
                }
            }

            return FolderSyncOutcome(
                success = true,
                completedScan = !metadataOnly && scanResult.scanStatus == LocalFolderScanStatus.COMPLETE
            )

        } catch (e: Exception) {
            cloudFolderLogError(
                event = "folder_index_folder_end",
                error = e,
                details = "operation=$operation correlation=$correlation root=$safeRoot result=failure " +
                    "metadataOnly=$metadataOnly",
            )
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return FolderSyncOutcome(success = false, completedScan = false)
        }
    }

    private suspend fun importAnnotationSidecarsForBooks(
        folderUri: android.net.Uri,
        folderUriString: String,
        books: List<RecentFileItem>,
        phase: String,
        appStorageRoot: File? = null,
        rootId: String? = null,
    ): Int {
        val safeRoot = cloudFolderSafeId(rootId ?: folderUriString)
        val operation = cloudFolderOperationId("annotation-import", rootId ?: folderUriString, phase)
        val correlation = cloudFolderSyncCorrelationId("annotation-import", rootId ?: folderUriString)
        if (books.isEmpty()) {
            cloudFolderLogD(
                "event=annotation_sidecar_import operation=$operation correlation=$correlation " +
                    "root=$safeRoot phase=${phase.replace('-', '_')} result=skipped reason=no_books",
            )
            ReaderPerfLog.d("FolderSync phase annotation-sidecars skipped phase=$phase reason=no-books folder=$folderUriString")
            return 0
        }

        val preloadedSidecars = ReaderPerfLog.measureSuspend(
            name = "FolderSync phase annotation-sidecars",
            minLogMs = 25L,
            details = { "phase=$phase" }
        ) {
            if (appStorageRoot != null) {
                LocalSyncUtils.preloadAnnotationSidecarsFromAppStorage(appStorageRoot)
            } else {
                LocalSyncUtils.preloadAnnotationSidecars(appContext, folderUri)
            }
        }

        ReaderPerfLog.d(
            "FolderSync annotation-sidecars records=${preloadedSidecars.size} books=${books.size} phase=$phase folder=$folderUriString"
        )

        if (preloadedSidecars.isEmpty()) {
            cloudFolderLogD(
                "event=annotation_sidecar_import_end operation=$operation correlation=$correlation " +
                    "root=$safeRoot phase=${phase.replace('-', '_')} imported=0 records=0 books=${books.size} " +
                    "result=success",
            )
            return 0
        }

        var imported = 0
        Timber.tag("FolderAnnotationSync").d("Checking annotation sidecars phase=$phase for ${books.size} books...")
        for (book in books) {
            if (isStopped || !isFolderStillLinkedByUri(folderUriString)) break

            val sidecarData = preloadedSidecars[book.bookId] ?: continue
            val pendingLocal = pendingAnnotationExports.get(book.bookId)
            if (pendingLocal != null) {
                cloudFolderLogW(
                    "event=annotation_sidecar_import operation=$operation correlation=$correlation " +
                        "root=$safeRoot book=${cloudFolderSafeId(book.bookId)} phase=${phase.replace('-', '_')} " +
                        "result=deferred reason=local_export_pending revision=${pendingLocal.revision}",
                )
                ReaderPerfLog.w(
                    "FolderSync annotation import deferred: local revision pending " +
                        "book=${book.bookId} revision=${pendingLocal.revision} phase=$phase"
                )
                FolderAnnotationExportWorker.schedulePendingNow(appContext, book.bookId)
                continue
            }
            val (remoteTs, jsonPayload) = sidecarData

            val safeSlashBookId = book.bookId.replace("/", "_")
            val localFiles = listOf(
                File(appContext.filesDir, "annotations/annotation_$safeSlashBookId.json"),
                AndroidBookArtifactPaths.richTextFile(appContext.filesDir, book.bookId),
                File(appContext.filesDir, "page_layouts/layout_$safeSlashBookId.json"),
                File(appContext.filesDir, "textboxes/textboxes_$safeSlashBookId.json"),
                File(appContext.filesDir, "pdf_highlights/highlights_$safeSlashBookId.json")
            )
            val localTs = localFiles.maxOfOrNull { if (it.exists()) it.lastModified() else 0L } ?: 0L

            if (remoteTs > (localTs + 1000)) {
                cloudFolderLogD(
                    "event=annotation_sidecar_import operation=$operation correlation=$correlation " +
                        "root=$safeRoot book=${cloudFolderSafeId(book.bookId)} phase=${phase.replace('-', '_')} " +
                        "result=applied remoteTs=$remoteTs localTs=$localTs bytes=${jsonPayload.toByteArray(Charsets.UTF_8).size}",
                )
                Timber.tag("FolderAnnotationSync").i(">>> Newer sidecar found for ${book.displayName}. Importing.")
                recentFilesRepository.importAnnotationBundle(
                    bookId = book.bookId,
                    jsonString = jsonPayload,
                    lastModifiedTimestamp = remoteTs
                )
                imported++
            } else {
                cloudFolderLogD(
                    "event=annotation_sidecar_import operation=$operation correlation=$correlation " +
                        "root=$safeRoot book=${cloudFolderSafeId(book.bookId)} phase=${phase.replace('-', '_')} " +
                        "result=skipped reason=not_newer remoteTs=$remoteTs localTs=$localTs",
                )
                Timber.tag("FolderAnnotationSync").v("Sidecar for ${book.displayName} is not newer. Skipping.")
            }
        }

        cloudFolderLogI(
            "event=annotation_sidecar_import_end operation=$operation correlation=$correlation " +
                "root=$safeRoot phase=${phase.replace('-', '_')} imported=$imported " +
                "records=${preloadedSidecars.size} books=${books.size}",
        )

        ReaderPerfLog.i(
            "FolderSync annotation-sidecars imported=$imported records=${preloadedSidecars.size} phase=$phase folder=$folderUriString"
        )
        return imported
    }

    private data class AndroidFolderScanResult(
        val files: List<SharedFolderScannedFile> = emptyList(),
        val dirsScanned: Int = 0,
        val filesSeen: Int = 0,
        val stoppedForUnlinkedFolder: Boolean = false,
        val scanStatus: LocalFolderScanStatus = LocalFolderScanStatus.COMPLETE
    )

    /**
     * Scan a completed DOWNLOAD_ALL tree using java.io. App-private roots do
     * not have a DocumentsProvider tree URI, so sending them through the SAF
     * scanner would produce an empty/unavailable result and could reconcile
     * away valid indexed books.
     */
    private fun scanAppStorageFiles(
        root: File,
        folderUriString: String,
        allowedFileTypes: Set<FileType>,
    ): AndroidFolderScanResult {
        val scannedFiles = mutableListOf<SharedFolderScannedFile>()
        val directories = ArrayDeque<Pair<File, String>>()
        directories.add(root to "")
        var dirsScanned = 0
        var filesSeen = 0
        var stoppedForUnlinkedFolder = false
        var scanStatus = LocalFolderScanStatus.COMPLETE

        while (directories.isNotEmpty()) {
            if (isStopped) {
                scanStatus = LocalFolderScanStatus.PARTIAL
                break
            }
            if (!isFolderStillLinkedByUri(folderUriString)) {
                stoppedForUnlinkedFolder = true
                scanStatus = LocalFolderScanStatus.PARTIAL
                break
            }
            val (directory, parentPath) = directories.removeFirst()
            dirsScanned++
            val children = directory.listFiles()
            if (children == null) {
                scanStatus = LocalFolderScanStatus.PARTIAL
                Timber.tag("FolderSync").w("App-private folder could not be listed: ${directory.path}")
                continue
            }
            for (child in children) {
                if (isStopped || stoppedForUnlinkedFolder) break
                filesSeen++
                val name = child.name.orEmpty()
                if (name.isBlank()) {
                    scanStatus = LocalFolderScanStatus.PARTIAL
                    continue
                }
                val relativePath = if (parentPath.isBlank()) name else "$parentPath/$name"
                when {
                    child.isDirectory -> {
                        if (!name.startsWith(".") && name != LOCAL_FOLDER_SYNC_DATA_DIR) {
                            directories.add(child to relativePath)
                        }
                    }
                    child.isFile -> {
                        val type = getFileType(name, null)
                        if (
                            type == null ||
                            type !in allowedFileTypes ||
                            !isLocalFolderSyncEligibleFile(name, null) ||
                            name.endsWith(".json", ignoreCase = true) ||
                            name.startsWith(".")
                        ) {
                            continue
                        }
                        scannedFiles += SharedFolderScannedFile(
                            name = name,
                            // Canonical androidx file URI. File.toURI() emits a
                            // different encoding (single slash + percent-escape)
                            // than Uri.fromFile, which the reader uses when
                            // opening a managed book; URI-keyed Room writes
                            // (reading position on close) must match that form.
                            path = android.net.Uri.fromFile(child).toString(),
                            sourceFolder = folderUriString,
                            relativePath = relativePath,
                            type = type,
                            size = child.length().coerceAtLeast(0L),
                            lastModified = child.lastModified().coerceAtLeast(0L),
                        )
                    }
                    else -> scanStatus = LocalFolderScanStatus.PARTIAL
                }
            }
            if (!isFolderStillLinkedByUri(folderUriString)) {
                stoppedForUnlinkedFolder = true
                scanStatus = LocalFolderScanStatus.PARTIAL
            }
        }

        if (isStopped || stoppedForUnlinkedFolder) scanStatus = LocalFolderScanStatus.PARTIAL
        return AndroidFolderScanResult(
            files = scannedFiles,
            dirsScanned = dirsScanned,
            filesSeen = filesSeen,
            stoppedForUnlinkedFolder = stoppedForUnlinkedFolder,
            scanStatus = scanStatus,
        )
    }

    private fun scanFolderFiles(
        folderUri: android.net.Uri,
        folderUriString: String,
        allowedFileTypes: Set<FileType>
    ): AndroidFolderScanResult {
        Timber.tag("FolderSync").d("Phase 2: Scanning physical files using raw ContentResolver...")
        val contentResolver = appContext.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val dirQueue = ArrayDeque<String>()
        val scannedFiles = mutableListOf<SharedFolderScannedFile>()
        var dirsScanned = 0
        var filesSeen = 0
        var stoppedForUnlinkedFolder = false
        var scanStatus = LocalFolderScanStatus.COMPLETE
        dirQueue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        while (dirQueue.isNotEmpty()) {
            if (isStopped) {
                scanStatus = LocalFolderScanStatus.PARTIAL
                break
            }
            if (!isFolderStillLinked(folderUriString)) {
                ReaderPerfLog.w("FolderSync folder abort: folder unlinked during scan folder=$folderUriString")
                stoppedForUnlinkedFolder = true
                scanStatus = LocalFolderScanStatus.PARTIAL
                break
            }
            val currentDocId = dirQueue.removeFirst()
            dirsScanned++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, currentDocId)

            try {
                val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                if (cursor == null) {
                    scanStatus = LocalFolderScanStatus.PARTIAL
                    Timber.tag("FolderSync").w("Provider returned no cursor for docId: $currentDocId")
                    continue
                }
                cursor.use {
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
                            scanStatus = LocalFolderScanStatus.PARTIAL
                            break
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".") && name != LOCAL_FOLDER_SYNC_DATA_DIR) {
                                dirQueue.add(docId)
                            }
                            continue
                        }

                        val type = getFileType(name, mimeType)
                        if (
                            type == null ||
                            type !in allowedFileTypes ||
                            !isLocalFolderSyncEligibleFile(name, mimeType) ||
                            name.endsWith(".json") ||
                            name.startsWith(".")
                        ) {
                            continue
                        }

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val relativePath = buildRelativePath(rootDocId, docId, name)
                        scannedFiles += SharedFolderScannedFile(
                            name = name,
                            path = docUri.toString(),
                            sourceFolder = folderUriString,
                            relativePath = relativePath,
                            type = type,
                            size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L,
                            lastModified = if (!cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("FolderSync").e(e, "Failed to query children for docId: $currentDocId")
                // A provider can fail after returning valid entries from
                // earlier directories.  That result is not a complete view
                // and must never be used to infer deletions.
                scanStatus = LocalFolderScanStatus.PARTIAL
            }

            if (stoppedForUnlinkedFolder) break
        }

        if (isStopped || stoppedForUnlinkedFolder) {
            scanStatus = LocalFolderScanStatus.PARTIAL
        }

        return AndroidFolderScanResult(
            files = scannedFiles,
            dirsScanned = dirsScanned,
            filesSeen = filesSeen,
            stoppedForUnlinkedFolder = stoppedForUnlinkedFolder,
            scanStatus = if (scanStatus == LocalFolderScanStatus.COMPLETE && !stoppedForUnlinkedFolder && !isStopped) {
                LocalFolderScanStatus.COMPLETE
            } else {
                scanStatus
            }
        )
    }

    private fun RecentFileItem.toFolderSyncSharedBookItem(): SharedBookItem {
        return SharedBookItem(
            id = bookId,
            path = uriString,
            type = type,
            displayName = displayName,
            timestamp = lastModifiedTimestamp,
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            sourceFolder = sourceFolderUri,
            folderTextMetadataParsed = folderTextMetadataParsed,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            lastPageIndex = lastPage,
            readerPosition = readerPositionOrNull(),
            readerBookmarks = parseReaderBookmarks(),
            readerHighlights = EpubAnnotationSerializer.parseHighlightsJson(highlightsJson),
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp
        )
    }

    private fun SharedBookItem.toFolderSyncRecentFileItem(
        existing: RecentFileItem?,
        appliedMetadata: FolderBookMetadata?,
        scannedFile: SharedFolderScannedFile?,
        nowMillis: Long
    ): RecentFileItem {
        val contentChanged = existing != null && folderFileContentChanged(existing, this)
        val localModifiedTimestamp = when {
            appliedMetadata != null -> appliedMetadata.lastModifiedTimestamp
            contentChanged && fileContentModifiedTimestamp > 0L -> fileContentModifiedTimestamp
            timestamp > 0L -> timestamp
            else -> nowMillis
        }
        val legacyPosition = readerPosition
        val mappedBookmarksJson = readerBookmarks.toAndroidBookmarksJson(id)
        val mappedHighlightsJson = readerHighlights
            .takeIf { it.isNotEmpty() }
            ?.let(EpubAnnotationSerializer::highlightsToJson)
        val metadataHas = { name: String -> appliedMetadata?.hasExplicitField(name) == true }
        val bookmarksJson = when {
            appliedMetadata == null && existing != null -> existing.bookmarksJson
            metadataHas("bookmarksJson") -> appliedMetadata?.bookmarksJson
            mappedBookmarksJson != null -> mappedBookmarksJson
            else -> appliedMetadata?.bookmarksJson ?: existing?.bookmarksJson
        }
        val highlightsJson = when {
            appliedMetadata == null && existing != null -> existing.highlightsJson
            metadataHas("highlightsJson") && mappedHighlightsJson == null -> appliedMetadata?.highlightsJson
            mappedHighlightsJson != null -> mappedHighlightsJson
            else -> appliedMetadata?.highlightsJson ?: existing?.highlightsJson
        }
        val lastChapterIndex = when {
            legacyPosition?.chapterIndex != null -> legacyPosition.chapterIndex
            metadataHas("lastChapterIndex") -> appliedMetadata?.lastChapterIndex
            else -> appliedMetadata?.lastChapterIndex ?: existing?.lastChapterIndex
        }
        val lastPage = when {
            legacyPosition?.pageIndex != null -> legacyPosition.pageIndex
            metadataHas("lastPage") -> appliedMetadata?.lastPage
            else -> appliedMetadata?.lastPage ?: existing?.lastPage
        }
        val lastPositionCfi = when {
            legacyPosition?.cfi != null -> legacyPosition.cfi
            metadataHas("lastPositionCfi") -> appliedMetadata?.lastPositionCfi
            else -> appliedMetadata?.lastPositionCfi ?: existing?.lastPositionCfi
        }
        val locatorBlockIndex = when {
            legacyPosition?.blockIndex != null -> legacyPosition.blockIndex
            metadataHas("locatorBlockIndex") -> appliedMetadata?.locatorBlockIndex
            else -> appliedMetadata?.locatorBlockIndex ?: existing?.locatorBlockIndex
        }
        val locatorCharOffset = when {
            legacyPosition?.charOffset != null -> legacyPosition.charOffset
            metadataHas("locatorCharOffset") -> appliedMetadata?.locatorCharOffset
            else -> appliedMetadata?.locatorCharOffset ?: existing?.locatorCharOffset
        }

        return RecentFileItem(
            bookId = id,
            uriString = path,
            type = type,
            displayName = scannedFile?.name ?: existing?.displayName ?: displayName,
            timestamp = when {
                existing == null -> timestamp.takeIf { it > 0L } ?: localModifiedTimestamp
                appliedMetadata?.isRecent == true -> appliedMetadata.lastModifiedTimestamp
                else -> existing.timestamp
            },
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            lastChapterIndex = lastChapterIndex ?: if (metadataHas("lastChapterIndex")) null else lastPageIndex ?: existing?.lastChapterIndex,
            lastPage = lastPage ?: if (metadataHas("lastPage")) null else lastPageIndex ?: existing?.lastPage,
            lastPositionCfi = lastPositionCfi ?: if (metadataHas("lastPositionCfi")) null else existing?.lastPositionCfi,
            locatorBlockIndex = locatorBlockIndex,
            locatorCharOffset = locatorCharOffset,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            isAvailable = true,
            lastModifiedTimestamp = localModifiedTimestamp,
            isDeleted = false,
            bookmarksJson = bookmarksJson,
            sourceFolderUri = sourceFolder,
            isReflowPreferred = existing?.isReflowPreferred ?: false,
            customName = if (metadataHas("customName")) appliedMetadata?.customName else appliedMetadata?.customName ?: existing?.customName,
            highlightsJson = highlightsJson,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            folderTextMetadataParsed = folderTextMetadataParsed,
            folderCoverMetadataParsed = if (contentChanged) false else existing?.folderCoverMetadataParsed ?: false,
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
            tags = existing?.tags.orEmpty()
        )
    }

    private fun appliedMetadataFor(
        book: SharedBookItem,
        existing: RecentFileItem?,
        metadata: FolderBookMetadata?
    ): FolderBookMetadata? {
        if (metadata == null) return null
        val existingModified = existing?.lastModifiedTimestamp ?: Long.MIN_VALUE
        return metadata.takeIf { existing == null || it.lastModifiedTimestamp > existingModified }
    }

    private fun RecentFileItem.readerPositionOrNull(): ReaderLocator? {
        if (lastChapterIndex == null && lastPage == null && lastPositionCfi.isNullOrBlank()) return null
        return ReaderLocator.fromLegacy(
            chapterIndex = lastChapterIndex,
            cfi = lastPositionCfi,
            pageIndex = lastPage
        )
    }

    private fun RecentFileItem.parseReaderBookmarks(): List<ReaderBookmark> {
        return EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson)
            .mapIndexed { index, bookmark ->
                val locator = bookmark.locator.withFallbacks(
                    chapterIndex = bookmark.chapterIndex,
                    cfi = bookmark.cfi,
                    pageIndex = bookmark.pageInChapter?.minus(1),
                    textQuote = bookmark.snippet
                )
                val pageIndex = locator.pageIndex ?: bookmark.pageInChapter?.minus(1) ?: 0
                ReaderBookmark(
                    id = "bookmark_${bookId}_$index",
                    pageIndex = pageIndex.coerceAtLeast(0),
                    chapterTitle = bookmark.chapterTitle,
                    preview = bookmark.snippet,
                    locator = locator
                )
            }
    }

    private fun List<ReaderBookmark>.toAndroidBookmarksJson(bookId: String): String? {
        val bookmarks = mapIndexed { index, bookmark ->
            val locator = bookmark.locator
            val chapterIndex = locator.chapterIndex ?: 0
            val cfi = locator.cfi ?: "android:$bookId:$index:${bookmark.pageIndex}"
            EpubBookmark(
                cfi = cfi,
                chapterTitle = bookmark.chapterTitle,
                label = null,
                snippet = bookmark.preview,
                pageInChapter = bookmark.pageIndex + 1,
                totalPagesInChapter = null,
                chapterIndex = chapterIndex,
                locator = locator.withFallbacks(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    pageIndex = bookmark.pageIndex,
                    textQuote = bookmark.preview
                )
            )
        }
        return bookmarks.takeIf { it.isNotEmpty() }?.let(EpubAnnotationSerializer::bookmarksToJson)
    }

    private fun folderFileContentChanged(previous: RecentFileItem, next: RecentFileItem): Boolean {
        val sizeChanged = previous.fileSize > 0L && next.fileSize > 0L && previous.fileSize != next.fileSize
        val modifiedChanged = next.fileContentModifiedTimestamp > 0L &&
            previous.fileContentModifiedTimestamp != next.fileContentModifiedTimestamp
        return sizeChanged || modifiedChanged
    }

    private fun folderFileContentChanged(previous: RecentFileItem, next: SharedBookItem): Boolean {
        val sizeChanged = previous.fileSize > 0L && next.fileSize > 0L && previous.fileSize != next.fileSize
        val modifiedChanged = next.fileContentModifiedTimestamp > 0L &&
            previous.fileContentModifiedTimestamp != next.fileContentModifiedTimestamp
        return sizeChanged || modifiedChanged
    }

    private fun isFolderStillLinked(folder: SyncedFolder): Boolean {
        if (!folder.isAppManaged) return isFolderStillLinked(folder.uriString)
        val accountId = AuthRepository(appContext).getSignedInUser()?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val rootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return CloudFolderAppStoragePrefs.contains(appContext, accountId, rootId)
    }

    private fun isFolderStillLinkedByUri(folderUriString: String): Boolean {
        return isFolderStillLinked(folderUriString)
    }

    private fun isFolderStillLinked(folderUriString: String): Boolean {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        if (SyncedFolderPrefs.isLocalSyncEnabled(
            jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null),
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null),
            folderUriString = folderUriString,
            syncableTypes = ANDROID_SYNCABLE_FILE_TYPES
        )) return true
        val accountId = AuthRepository(appContext).getSignedInUser()?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return CloudFolderAppStoragePrefs.load(appContext, accountId)
            .any { it.toSyncedFolder(appContext.filesDir).uriString == folderUriString }
    }

    private fun getFileType(name: String, mimeType: String?): FileType? {
        return resolveFileTypeFromMetadata(name, mimeType)
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

    private suspend fun migrateFolderBookId(
        folderUriString: String,
        oldId: String,
        newId: String,
        folderMetadataMap: MutableMap<String, FolderBookMetadata>,
        preloadedSidecars: MutableMap<String, Pair<Long, String>>,
        existingItemsMap: MutableMap<String, RecentFileItem>,
        appStorageRoot: File? = null,
    ): Boolean {
        if (oldId == newId) return true

        recentFilesRepository.migrateBookIdLocally(oldId, newId)

        val oldMetadata = folderMetadataMap.remove(oldId)
        if (oldMetadata != null && newId !in folderMetadataMap) {
            val migratedMetadata = oldMetadata.copy(bookId = newId)
            val saved = if (appStorageRoot != null) {
                LocalSyncUtils.saveMetadataToAppStorage(appStorageRoot, migratedMetadata)
            } else {
                LocalSyncUtils.saveMetadataToFolder(appContext, folderUriString.toUri(), migratedMetadata)
            }
            if (!saved) {
                Timber.tag("FolderSync").w("Failed to migrate metadata sidecar $oldId -> $newId")
                return false
            }
            folderMetadataMap[newId] = migratedMetadata
        }

        val oldSidecar = preloadedSidecars.remove(oldId)
        if (oldSidecar != null && newId !in preloadedSidecars) {
            val saved = if (appStorageRoot != null) {
                LocalSyncUtils.saveAnnotationSidecarToAppStorage(
                    root = appStorageRoot,
                    bookId = newId,
                    jsonPayload = oldSidecar.second,
                    timestamp = oldSidecar.first,
                )
            } else {
                LocalSyncUtils.saveAnnotationSidecar(
                    context = appContext,
                    sourceFolderUri = folderUriString.toUri(),
                    bookId = newId,
                    jsonPayload = oldSidecar.second,
                    timestamp = oldSidecar.first,
                )
            }
            if (!saved) {
                Timber.tag("FolderSync").w("Failed to migrate annotation sidecar $oldId -> $newId")
                return false
            }
            preloadedSidecars[newId] = oldSidecar
        }

        if (appStorageRoot != null) {
            LocalSyncUtils.deleteBookSidecarsFromAppStorage(appStorageRoot, oldId)
        } else {
            LocalSyncUtils.deleteBookSidecars(appContext, folderUriString.toUri(), oldId)
        }

        existingItemsMap.remove(oldId)
        recentFilesRepository.getFileByBookId(newId)?.let {
            existingItemsMap[newId] = it
        }
        return true
    }
}
