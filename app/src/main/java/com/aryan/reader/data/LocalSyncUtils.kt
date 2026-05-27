// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.aryan.reader.ReaderPerfLog
import com.aryan.reader.shared.LOCAL_FOLDER_SIDECAR_HASH_PREFIX
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.localFolderSyncAnnotationFileName
import com.aryan.reader.shared.localFolderSyncAnnotationTempFileName
import com.aryan.reader.shared.localFolderSyncMetadataFileName
import com.aryan.reader.shared.localFolderSyncMetadataTempFileName
import com.aryan.reader.shared.localFolderSyncSidecarStem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

object LocalSyncUtils {
    private const val TAG = "FolderSync"
    private const val ANNOTATION_SUFFIX = "_annotations"
    private const val SYNC_SUBFOLDER_NAME = LOCAL_FOLDER_SYNC_DATA_DIR

    private data class SyncFileEntry(
        val name: String,
        val uri: Uri
    )

    private data class ParsedAnnotationSidecar(
        val bookId: String,
        val timestamp: Long,
        val data: String
    )

    private fun syncSubfolderDocId(rootDocId: String): String {
        return if (rootDocId.endsWith("/$SYNC_SUBFOLDER_NAME")) {
            rootDocId
        } else if (rootDocId.endsWith(":")) {
            rootDocId + SYNC_SUBFOLDER_NAME
        } else {
            "$rootDocId/$SYNC_SUBFOLDER_NAME"
        }
    }

    private fun querySyncSubfolderFiles(context: Context, sourceFolderUri: Uri): List<SyncFileEntry> {
        val start = ReaderPerfLog.nowNanos()
        val resolver = context.contentResolver
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(sourceFolderUri)
        } catch (_: Exception) {
            ReaderPerfLog.w("LocalSync direct query skipped: invalid tree uri=$sourceFolderUri")
            return emptyList()
        }
        val syncDocId = syncSubfolderDocId(rootDocId)
        val syncDirUri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, syncDocId)
        val documentProjection = arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val isSyncDir = try {
            resolver.query(syncDirUri, documentProjection, null, null, null)?.use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } == true
        } catch (_: Exception) {
            false
        }

        if (!isSyncDir) {
            ReaderPerfLog.d(
                "LocalSync direct query sync dir missing rootDocId=$rootDocId syncDocId=$syncDocId"
            )
            return querySyncSubfolderFilesFallback(context, sourceFolderUri, "missing-direct-sync-dir")
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceFolderUri, syncDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val entries = mutableListOf<SyncFileEntry>()
        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeCol)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val name = cursor.getString(nameCol) ?: continue
                    val docId = cursor.getString(idCol) ?: continue
                    entries.add(
                        SyncFileEntry(
                            name = name,
                            uri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, docId)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to query sync subfolder directly")
            return querySyncSubfolderFilesFallback(context, sourceFolderUri, "direct-query-error")
        }
        ReaderPerfLog.d(
            "LocalSync direct query files=${entries.size} elapsed=${ReaderPerfLog.elapsedMs(start)}ms syncDocId=$syncDocId"
        )
        return entries
    }

    private fun querySyncSubfolderFilesFallback(
        context: Context,
        sourceFolderUri: Uri,
        reason: String
    ): List<SyncFileEntry> {
        val start = ReaderPerfLog.nowNanos()
        return try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri)
            val syncDir = rootTree?.findFile(SYNC_SUBFOLDER_NAME)
            if (syncDir == null || !syncDir.isDirectory) {
                ReaderPerfLog.w("LocalSync fallback query found no sync dir reason=$reason uri=$sourceFolderUri")
                emptyList()
            } else {
                val entries = syncDir.listFiles()
                    .asSequence()
                    .filter { it.isFile }
                    .mapNotNull { file ->
                        val name = file.name ?: return@mapNotNull null
                        SyncFileEntry(name = name, uri = file.uri)
                    }
                    .toList()
                ReaderPerfLog.d(
                    "LocalSync fallback query files=${entries.size} elapsed=${ReaderPerfLog.elapsedMs(start)}ms reason=$reason"
                )
                entries
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to query sync subfolder fallback")
            emptyList()
        }
    }

    private fun getOrCreateSyncDir(rootTree: DocumentFile): DocumentFile? {
        val existing = rootTree.findFile(SYNC_SUBFOLDER_NAME)
        if (existing != null && existing.isDirectory) return existing
        if (existing != null && existing.isFile) return null
        return rootTree.createDirectory(SYNC_SUBFOLDER_NAME)
    }

    suspend fun migrateLegacySidecarsToSubfolder(context: Context, rootTree: DocumentFile) = withContext(Dispatchers.IO) {
        try {
            val allRootFiles = rootTree.listFiles()
            val legacyFiles = allRootFiles.filter { file ->
                val name = file.name ?: ""
                file.isFile && (
                        (name.startsWith(".local_") && name.endsWith(".json")) ||
                                (name.startsWith("local_") && name.endsWith(".json")) ||
                                (name.contains(ANNOTATION_SUFFIX))
                        )
            }

            if (legacyFiles.isEmpty()) return@withContext

            Timber.tag(TAG).i("Found ${legacyFiles.size} legacy sidecar files at root. Migrating to $SYNC_SUBFOLDER_NAME...")
            val syncDir = getOrCreateSyncDir(rootTree) ?: return@withContext

            for (legacyFile in legacyFiles) {
                val name = legacyFile.name ?: continue
                try {
                    val targetFile = syncDir.findFile(name) ?: syncDir.createFile("application/json", name)
                    if (targetFile != null) {
                        context.contentResolver.openInputStream(legacyFile.uri)?.use { input ->
                            context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        legacyFile.delete() // Cleanup root file after success
                        Timber.tag(TAG).d("Migrated: $name")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to migrate legacy file: $name")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error migrating legacy sidecars")
        }
    }

    suspend fun saveMetadataToFolder(
        context: Context,
        sourceFolderUri: Uri,
        metadata: FolderBookMetadata
    ) = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext
            val syncDir = getOrCreateSyncDir(rootTree) ?: return@withContext

            val syncFileName = localFolderSyncMetadataFileName(metadata.bookId)
            val existingMeta = resolveAndCleanMetadataConflicts(context, syncDir, metadata.bookId)
            if (existingMeta != null && existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                Timber.tag(TAG).w("ClobberCheck: ABORTING save. Folder has newer data for ${metadata.bookId}.")
                return@withContext
            }

            val tempFileName = uniqueFolderSyncTempName(localFolderSyncMetadataTempFileName(metadata.bookId))
            val tempFile = syncDir.createFile("application/json", tempFileName)
            if (tempFile == null) {
                Timber.tag(TAG).e("Could not create temp metadata file for ${metadata.bookId}")
                return@withContext
            }

            val jsonString = metadata.toJsonString()
            var writeSuccess = false

            try {
                context.contentResolver.openFileDescriptor(tempFile.uri, "rwt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray())
                        fos.flush()
                        try {
                            pfd.fileDescriptor.sync()
                        } catch (_: Exception) {
                        }
                    }
                }
                writeSuccess = true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write temp metadata for ${metadata.bookId}")
                try { tempFile.delete() } catch (_: Exception) {}
                return@withContext
            }

            @Suppress("KotlinConstantConditions") if (writeSuccess) {
                val targetFile = syncDir.findFile(syncFileName)
                if (targetFile != null && targetFile.exists()) {
                    targetFile.delete()
                }

                if (tempFile.renameTo(syncFileName)) {
                    Timber.tag(TAG).d("Atomic save successful: $syncFileName")

                    val absolutePath = getPathFromUri(context, tempFile.uri)
                    if (absolutePath != null) {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(absolutePath),
                            arrayOf("application/json"),
                            null
                        )
                    }
                } else {
                    Timber.tag(TAG).e("Failed to rename temp file to $syncFileName")
                }
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save local metadata to folder.")
        }
    }

    suspend fun saveAnnotationSidecar(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String,
        jsonPayload: String,
        timestamp: Long
    ) = withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("saveAnnotationSidecar called for bookId: $bookId, timestamp: $timestamp")
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext
            val syncDir = getOrCreateSyncDir(rootTree) ?: return@withContext
            val currentBest = resolveAndCleanAnnotationConflicts(context, syncDir, bookId)
            val targetName = localFolderSyncAnnotationFileName(bookId)
            val tempName = uniqueFolderSyncTempName(localFolderSyncAnnotationTempFileName(bookId))

            if (currentBest != null) {
                val (remoteTs, _) = currentBest
                if (remoteTs >= timestamp) {
                    Timber.tag("FolderAnnotationSync").d("AnnotationSync: Remote sidecar (ts=$remoteTs) is newer or same as local (ts=$timestamp). Aborting write.")
                    return@withContext
                }
            }

            val wrapper = JSONObject()
            wrapper.put("version", 1)
            wrapper.put("bookId", bookId)
            wrapper.put("timestamp", timestamp)
            wrapper.put("data", JSONObject(jsonPayload))
            val contentBytes = wrapper.toString().toByteArray()

            val tempFile = syncDir.createFile("application/json", tempName)
            if (tempFile == null) {
                Timber.tag("FolderAnnotationSync").e("Failed to create temp sidecar file.")
                return@withContext
            }

            var writeSuccess = false
            try {
                context.contentResolver.openFileDescriptor(tempFile.uri, "rwt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(contentBytes)
                        fos.flush()
                        try { pfd.fileDescriptor.sync() } catch (_: Exception) {}
                    }
                }
                writeSuccess = true
            } catch (e: Exception) {
                Timber.tag("FolderAnnotationSync").e(e, "Error writing to temp sidecar.")
                try { tempFile.delete() } catch (_: Exception) {}
                return@withContext
            }

            @Suppress("KotlinConstantConditions") if (writeSuccess) {
                val existingMain = syncDir.findFile(targetName)
                if (existingMain != null) {
                    if (!existingMain.delete()) {
                        Timber.tag("FolderAnnotationSync").w("Failed to delete existing sidecar before rename. Attempting rename anyway (might fail on some SAF providers).")
                    }
                }

                if (tempFile.renameTo(targetName)) {
                    Timber.tag("FolderAnnotationSync").d("AnnotationSync: Atomic save successful for $targetName")
                } else {
                    Timber.tag("FolderAnnotationSync").e("AnnotationSync: Failed to rename temp sidecar to $targetName")
                }
            }

        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Failed to save annotation sidecar for $bookId")
        }
    }

    suspend fun preloadAnnotationSidecars(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, Pair<Long, String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Pair<Long, String>>()

        try {
            val parsedSidecars = querySyncSubfolderFiles(context, sourceFolderUri)
                .asSequence()
                .filter { isAnnotationSidecarCandidateName(it.name) }
                .mapNotNull { file ->
                    parseAnnotationSidecar(
                        context = context,
                        file = file,
                        fallbackBookId = extractLegacyAnnotationBookId(file.name)
                    )
                }
                .groupBy { it.bookId }

            for ((bookId, sidecars) in parsedSidecars) {
                val best = sidecars.maxByOrNull { it.timestamp }
                if (best != null) results[bookId] = best.timestamp to best.data
            }
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Error preloading annotation sidecars")
        }

        return@withContext results
    }

    suspend fun getAnnotationSidecar(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext null
            val syncDir = rootTree.findFile(SYNC_SUBFOLDER_NAME) ?: return@withContext null
            val bestFile = resolveAndCleanAnnotationConflicts(context, syncDir, bookId)
            return@withContext bestFile

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read annotation sidecar for $bookId")
        }
        return@withContext null
    }

    private fun resolveAndCleanAnnotationConflicts(
        context: Context,
        syncDir: DocumentFile,
        bookId: String,
        knownFiles: List<DocumentFile>? = null
    ): Pair<Long, String>? {
        val allFiles = knownFiles ?: syncDir.listFiles().asList()

        val candidates = allFiles.mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            if (!isAnnotationSidecarCandidateName(name)) return@mapNotNull null
            parseAnnotationSidecar(
                context = context,
                file = SyncFileEntry(name = name, uri = file.uri),
                fallbackBookId = extractLegacyAnnotationBookId(name)
            )?.takeIf { it.bookId == bookId }?.let { file to it }
        }

        if (candidates.isEmpty()) return null

        var bestTs = -1L
        var bestData: String? = null
        var bestFile: DocumentFile? = null
        val filesToDelete = mutableListOf<DocumentFile>()

        for ((file, sidecar) in candidates) {
            if (sidecar.timestamp > bestTs) {
                if (bestFile != null) {
                    filesToDelete.add(bestFile)
                }
                bestTs = sidecar.timestamp
                bestData = sidecar.data
                bestFile = file
            } else {
                filesToDelete.add(file)
            }
        }

        if (filesToDelete.isNotEmpty()) {
            Timber.tag("FolderAnnotationSync").i("Resolving conflicts for $bookId. Found ${filesToDelete.size} obsolete/conflict files.")
            for (toDelete in filesToDelete) {
                try {
                    Timber.tag("FolderAnnotationSync").v("Deleting loser: ${toDelete.name}")
                    toDelete.delete()
                } catch (_: Exception) {}
            }
        }

        if (bestFile != null) {
            val correctName = localFolderSyncAnnotationFileName(bookId)
            if (bestFile.name != correctName) {
                Timber.tag("FolderAnnotationSync").i("Renaming winner ${bestFile.name} to $correctName")
                val existingTarget = syncDir.findFile(correctName)
                if (existingTarget != null && existingTarget.uri != bestFile.uri) {
                    existingTarget.delete()
                }
                bestFile.renameTo(correctName)
            }
            return Pair(bestTs, bestData!!)
        }

        return null
    }

    /**
     * Helper to attempt to resolve a SAF URI to an absolute filesystem path.
     * This is required because MediaScannerConnection does not accept content:// URIs.
     */
    private fun getPathFromUri(context: Context, uri: Uri): String? {
        try {
            if (DocumentsContract.isDocumentUri(context, uri) && isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    @Suppress("DEPRECATION")
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            }
        } catch (_: Exception) {
            Timber.tag(TAG).w("Could not resolve absolute path for URI: $uri")
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * Reads all candidate files, picks the winner (highest timestamp),
     * and deletes the losers (cleanup).
     */
    private fun resolveAndCleanConflicts(
        context: Context,
        files: List<DocumentFile>,
        bookId: String
    ): FolderBookMetadata? {
        var bestMeta: FolderBookMetadata? = null
        var bestFile: DocumentFile? = null

        // 1. Find the winner
        files.forEach { file ->
            try {
                val jsonString = context.contentResolver.openInputStream(file.uri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                if (jsonString != null) {
                    val meta = FolderBookMetadata.fromJsonString(jsonString)
                    if (meta.bookId == bookId) {
                        if (bestMeta == null || meta.lastModifiedTimestamp > bestMeta.lastModifiedTimestamp) {
                            bestMeta = meta
                            bestFile = file
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to parse conflict file: ${file.name}")
            }
        }

        // 2. Clean up losers
        if (bestMeta != null && bestFile != null) {
            val filesToDelete = files.filter { it.uri != bestFile.uri }

            if (filesToDelete.isNotEmpty()) {
                Timber.tag(TAG).i("Resolving conflicts for $bookId. Winner: ${bestFile.name}. Deleting ${filesToDelete.size} obsolete files.")
                filesToDelete.forEach {
                    try { it.delete() } catch(_: Exception) {}
                }
            }

            val correctName = localFolderSyncMetadataFileName(bookId)
            if (bestFile.name != correctName) {
                Timber.tag(TAG).i("Renaming metadata winner ${bestFile.name} to $correctName")
                bestFile.renameTo(correctName)
            }
        }

        return bestMeta
    }

    private fun resolveAndCleanMetadataConflicts(
        context: Context,
        syncDir: DocumentFile,
        bookId: String
    ): FolderBookMetadata? {
        val hashedStem = localFolderSyncSidecarStem(bookId)
        val candidates = syncDir.listFiles().filter { file ->
            val name = file.name ?: ""
            val normalizedName = name.normalizedSidecarName()
            isMetadataSidecarCandidateName(name) &&
                (
                    normalizedName.matchesJsonSidecarStem(hashedStem) ||
                        normalizedName.matchesJsonSidecarStem(bookId)
                    )
        }
        if (candidates.isEmpty()) return null
        return resolveAndCleanConflicts(context, candidates, bookId)
    }

    private fun extractLegacyAnnotationBookId(name: String?): String? {
        if (name.isNullOrBlank()) return null
        var temp = name
        if (!isAnnotationSidecarCandidateName(temp)) return null
        if (temp.contains(".sync-conflict")) {
            temp = temp.substringBefore(".sync-conflict")
        }
        temp = temp.substringBeforeLast(".json")
        if (temp.endsWith(ANNOTATION_SUFFIX)) {
            temp = temp.substring(0, temp.length - ANNOTATION_SUFFIX.length)
        }
        if (temp.startsWith(".")) {
            temp = temp.substring(1)
        }
        if (temp.startsWith(LOCAL_FOLDER_SIDECAR_HASH_PREFIX)) return null
        return temp.ifBlank { null }
    }

    private fun isMetadataSidecarCandidateName(name: String): Boolean {
        if (name.contains(ANNOTATION_SUFFIX)) return false
        if (name.contains(".tmp") || name.contains(".syncthing.")) return false
        return name.endsWith(".json") || name.contains(".sync-conflict")
    }

    private fun isAnnotationSidecarCandidateName(name: String): Boolean {
        if (!name.contains(ANNOTATION_SUFFIX)) return false
        if (name.contains(".tmp") || name.contains(".syncthing.")) return false
        return name.endsWith(".json") || name.contains(".sync-conflict")
    }

    private fun String.normalizedSidecarName(): String {
        return removePrefix(".")
    }

    private fun String.matchesJsonSidecarStem(stem: String): Boolean {
        return this == "$stem.json" ||
            startsWith("$stem.sync-conflict") ||
            startsWith("$stem.json.sync-conflict")
    }

    private fun uniqueFolderSyncTempName(baseName: String): String {
        val stem = baseName.removeSuffix(".tmp")
        val nonce = "${System.currentTimeMillis()}_${Thread.currentThread().id}_${System.nanoTime().toString(36)}"
        return "$stem.$nonce.tmp"
    }

    private fun parseAnnotationSidecar(
        context: Context,
        file: SyncFileEntry,
        fallbackBookId: String?
    ): ParsedAnnotationSidecar? {
        return try {
            val content = context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readText()
            } ?: return null
            val json = JSONObject(content)
            val bookId = json.optString("bookId").takeIf { it.isNotBlank() }
                ?: fallbackBookId
                ?: return null
            val data = json.optJSONObject("data")?.toString() ?: return null
            ParsedAnnotationSidecar(
                bookId = bookId,
                timestamp = json.optLong("timestamp", 0L),
                data = data
            )
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Error parsing annotation sidecar: ${file.name}")
            null
        }
    }

    suspend fun deleteBookSidecars(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext
            val syncDir = rootTree.findFile(SYNC_SUBFOLDER_NAME) ?: return@withContext
            val hashedStem = localFolderSyncSidecarStem(bookId)
            val hashedAnnotationStem = "$hashedStem$ANNOTATION_SUFFIX"
            val targets = syncDir.listFiles().filter { file ->
                val name = file.name ?: return@filter false
                val normalized = name.normalizedSidecarName()
                normalized.matchesJsonSidecarStem(hashedStem) ||
                    normalized.matchesJsonSidecarStem(bookId) ||
                    normalized.matchesJsonSidecarStem(hashedAnnotationStem) ||
                    normalized.matchesJsonSidecarStem("$bookId$ANNOTATION_SUFFIX")
            }
            targets.forEach {
                try {
                    it.delete()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete folder sidecars for $bookId")
        }
    }

    suspend fun deleteSyncDataFolder(
        context: Context,
        sourceFolderUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext false
            val syncDir = rootTree.findFile(SYNC_SUBFOLDER_NAME) ?: return@withContext true
            if (!syncDir.isDirectory) return@withContext false
            syncDir.delete()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete sync data folder")
            false
        }
    }

    suspend fun getAllFolderMetadata(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, FolderBookMetadata> = withContext(Dispatchers.IO) {
        val finalResults = mutableMapOf<String, FolderBookMetadata>()

        try {
            val allFiles = querySyncSubfolderFiles(context, sourceFolderUri)
            val groupedMetadata = allFiles
                .asSequence()
                .filter { isMetadataSidecarCandidateName(it.name) }
                .mapNotNull { file ->
                    try {
                        val jsonString = context.contentResolver.openInputStream(file.uri)?.use { input ->
                            input.bufferedReader().use { it.readText() }
                        }
                        jsonString?.let(FolderBookMetadata::fromJsonString)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to parse metadata sidecar: ${file.name}")
                        null
                    }
                }
                .groupBy { it.bookId }

            groupedMetadata.forEach { (bookId, metadataRecords) ->
                val winner = metadataRecords.maxByOrNull { it.lastModifiedTimestamp }
                if (winner != null) {
                    finalResults[bookId] = winner
                }
            }

            Timber.tag(TAG).d("getAllFolderMetadata: Read ${finalResults.size}/${groupedMetadata.size} book records from sync data.")
            ReaderPerfLog.d(
                "LocalSync metadata read files=${allFiles.size} groups=${groupedMetadata.size} records=${finalResults.size}"
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning sync data folder for metadata")
            ReaderPerfLog.w("LocalSync metadata read failed uri=$sourceFolderUri")
        }
        return@withContext finalResults
    }

}
