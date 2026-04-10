// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

object LocalSyncUtils {
    private const val TAG = "FolderSync"
    private const val ANNOTATION_SUFFIX = "_annotations"
    private const val SYNC_SUBFOLDER_NAME = "EpistemeSyncData"

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

            val syncFileName = ".${metadata.bookId}.json"
            val legacyVisibleName = "${metadata.bookId}.json"

            val existingHidden = syncDir.findFile(syncFileName)
            val existingVisible = syncDir.findFile(legacyVisibleName)
            val fileToCheck = existingHidden ?: existingVisible

            if (fileToCheck != null && fileToCheck.exists()) {
                try {
                    val existingContent = context.contentResolver.openInputStream(fileToCheck.uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                    if (existingContent != null) {
                        val existingMeta = FolderBookMetadata.fromJsonString(existingContent)
                        if (existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                            Timber.tag(TAG).w("ClobberCheck: ABORTING save. Folder has newer data for ${metadata.bookId}.")
                            return@withContext
                        }
                    }
                } catch (_: Exception) {}
            }

            if (existingVisible != null && existingVisible.exists()) {
                try { existingVisible.delete() } catch (_: Exception) {}
            }

            val tempFileName = ".${metadata.bookId}.tmp"
            syncDir.findFile(tempFileName)?.delete()

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
                val targetFile = rootTree.findFile(syncFileName)
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
            val targetName = ".${bookId}${ANNOTATION_SUFFIX}.json"
            val tempName = ".${bookId}${ANNOTATION_SUFFIX}.tmp"
            val tempFile = syncDir.createFile("application/json", tempName)
            val existingMain = syncDir.findFile(targetName)

            if (currentBest != null) {
                val (remoteTs, _) = currentBest
                if (remoteTs >= timestamp) {
                    Timber.tag("FolderAnnotationSync").d("AnnotationSync: Remote sidecar (ts=$remoteTs) is newer or same as local (ts=$timestamp). Aborting write.")
                    return@withContext
                }
            }

            val wrapper = JSONObject()
            wrapper.put("version", 1)
            wrapper.put("timestamp", timestamp)
            wrapper.put("data", JSONObject(jsonPayload))
            val contentBytes = wrapper.toString().toByteArray()

            syncDir.findFile(tempName)?.delete()

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
                val existingMain = rootTree.findFile(targetName)
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
        rootTree: DocumentFile
    ): Map<String, Pair<Long, String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Pair<Long, String>>()

        try {
            val syncDir = rootTree.findFile(SYNC_SUBFOLDER_NAME)
            if (syncDir == null || !syncDir.isDirectory) return@withContext results
            val allFiles = syncDir.listFiles()

            val annotationFiles = allFiles.filter { file ->
                val name = file.name ?: ""
                name.contains(ANNOTATION_SUFFIX) && name.endsWith(".json") && !name.endsWith(".tmp")
            }

            val filesByBookId = annotationFiles.groupBy { file ->
                val name = file.name ?: ""
                var temp = name.substringBeforeLast(".json")
                if (temp.contains(".sync-conflict")) {
                    temp = temp.substringBefore(".sync-conflict")
                }
                if (temp.endsWith(ANNOTATION_SUFFIX)) {
                    temp = temp.substring(0, temp.length - ANNOTATION_SUFFIX.length)
                }
                if (temp.startsWith(".")) {
                    temp = temp.substring(1)
                }
                temp
            }

            filesByBookId.forEach { (bookId, files) ->
                if (bookId.isNotBlank()) {
                    var bestTs = -1L
                    var bestData: String? = null

                    for (file in files) {
                        try {
                            val content = context.contentResolver.openInputStream(file.uri)?.use {
                                it.bufferedReader().readText()
                            } ?: continue

                            val json = JSONObject(content)
                            val ts = json.optLong("timestamp", 0L)
                            val data = json.optJSONObject("data")?.toString()

                            if (data != null && ts > bestTs) {
                                bestTs = ts
                                bestData = data
                            }
                        } catch (e: Exception) {
                            Timber.tag("FolderAnnotationSync").e(e, "Error parsing preloaded file: ${file.name}")
                        }
                    }

                    if (bestData != null) {
                        results[bookId] = Pair(bestTs, bestData)
                    }
                }
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
        bookId: String
    ): Pair<Long, String>? {
        val basePattern = ".${bookId}${ANNOTATION_SUFFIX}"

        val allFiles = syncDir.listFiles()

        val candidates = allFiles.filter { file ->
            val name = file.name ?: ""
            name.startsWith(basePattern) &&
                    name.endsWith(".json") &&
                    !name.endsWith(".tmp") &&
                    !name.contains(".syncthing.")
        }

        if (candidates.isEmpty()) return null

        var bestTs = -1L
        var bestData: String? = null
        var bestFile: DocumentFile? = null
        val filesToDelete = mutableListOf<DocumentFile>()

        for (file in candidates) {
            try {
                val content = context.contentResolver.openInputStream(file.uri)?.use {
                    it.bufferedReader().readText()
                } ?: continue

                val json = JSONObject(content)
                val ts = json.optLong("timestamp", 0L)
                val data = json.optJSONObject("data")?.toString()

                if (data != null) {
                    if (ts > bestTs) {
                        if (bestFile != null) filesToDelete.add(bestFile)

                        bestTs = ts
                        bestData = data
                        bestFile = file
                    } else {
                        filesToDelete.add(file)
                    }
                } else {
                    filesToDelete.add(file)
                }
            } catch (e: Exception) {
                Timber.tag("FolderAnnotationSync").e(e, "Error parsing candidate file: ${file.name}")
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
            val correctName = "${basePattern}.json"
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

            // 3. Migrate Legacy to Hidden if needed
            val winnerName = bestFile.name ?: ""
            if (!winnerName.startsWith(".")) {
                Timber.tag(TAG).i("Migrating legacy file to hidden: $winnerName")
            }
        }

        return bestMeta
    }

    suspend fun getAllFolderMetadata(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, FolderBookMetadata> = withContext(Dispatchers.IO) {
        val finalResults = mutableMapOf<String, FolderBookMetadata>()

        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext finalResults
            val syncDir = rootTree.findFile(SYNC_SUBFOLDER_NAME)
            if (syncDir == null || !syncDir.isDirectory) return@withContext finalResults
            val allFiles = syncDir.listFiles()

            val groupedFiles = allFiles
                .filter {
                    val name = it.name ?: ""
                    (name.endsWith(".json") || name.contains(".sync-conflict")) &&
                            !name.endsWith(".tmp") &&
                            !name.contains(".syncthing.")
                }
                .groupBy { file ->
                    var name = file.name ?: ""
                    if (name.startsWith(".")) name = name.substring(1)
                    if (name.contains(".sync-conflict")) {
                        name.substringBefore(".sync-conflict")
                    } else {
                        name.substringBefore(".json")
                    }
                }

            groupedFiles.forEach { (bookId, files) ->
                val winner = resolveAndCleanConflicts(context, files, bookId)
                if (winner != null) {
                    finalResults[bookId] = winner
                }
            }

            Timber.tag(TAG).d("getAllFolderMetadata: Consolidated ${groupedFiles.size} book records from root.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning root folder for metadata")
        }
        return@withContext finalResults
    }
}