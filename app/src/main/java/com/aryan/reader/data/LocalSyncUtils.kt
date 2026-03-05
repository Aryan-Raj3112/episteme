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
    // REMOVED: private const val SYNC_DIR_NAME = "episteme"
    private const val TAG = "FolderSync"

    suspend fun saveMetadataToFolder(
        context: Context,
        sourceFolderUri: Uri,
        metadata: FolderBookMetadata
    ) = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext

            // CHANGED: Primary filename now starts with a dot
            val syncFileName = ".${metadata.bookId}.json"
            val legacyVisibleName = "${metadata.bookId}.json"

            val existingHidden = rootTree.findFile(syncFileName)
            val existingVisible = rootTree.findFile(legacyVisibleName)

            val fileToCheck = existingHidden ?: existingVisible

            if (fileToCheck != null && fileToCheck.exists()) {
                try {
                    val existingContent = context.contentResolver.openInputStream(fileToCheck.uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                    if (existingContent != null) {
                        val existingMeta = FolderBookMetadata.fromJsonString(existingContent)
                        if (existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                            Timber.tag(TAG).w("ClobberCheck: ABORTING save. Folder has newer data.")
                            return@withContext
                        }
                    }
                } catch (_: Exception) {}
            }

            var targetFile = existingHidden

            if (targetFile == null) {
                // Migrate: If a visible one existed, we'll replace it with hidden
                if (existingVisible != null && existingVisible.exists()) {
                    try { existingVisible.delete() } catch (_: Exception) {}
                }
                targetFile = rootTree.createFile("application/json", syncFileName)
            }

            if (targetFile == null) {
                Timber.tag(TAG).e("Could not create metadata file for ${metadata.bookId}")
                return@withContext
            }

            val jsonString = metadata.toJsonString()

            try {
                context.contentResolver.openFileDescriptor(targetFile.uri, "rwt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray())
                        fos.flush()
                        try {
                            pfd.fileDescriptor.sync()
                        } catch (_: Exception) {
                            Timber.tag(TAG).w("FileDescriptor sync not supported on this device/filesystem")
                        }
                    }
                }

                val absolutePath = getPathFromUri(context, targetFile.uri)
                if (absolutePath != null) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(absolutePath),
                        arrayOf("application/json"),
                        null
                    )
                }

                Timber.tag(TAG).d("Saved hidden metadata for ${metadata.bookId}")

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write metadata for ${metadata.bookId}")
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
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: run {
                Timber.tag("FolderAnnotationSync").w("Could not get DocumentFile from sourceFolderUri")
                return@withContext
            }
            val sidecarName = ".${bookId}_annotations.json"
            Timber.tag("FolderAnnotationSync").d("sidecarName: $sidecarName")

            val existingFile = rootTree.findFile(sidecarName)
            if (existingFile != null && existingFile.exists()) {
                Timber.tag("FolderAnnotationSync").d("Existing sidecar found at ${existingFile.uri}")
                try {
                    val content = context.contentResolver.openInputStream(existingFile.uri)?.use {
                        it.bufferedReader().readText()
                    }
                    if (content != null) {
                        val json = JSONObject(content)
                        val remoteTs = json.optLong("timestamp", 0L)
                        Timber.tag("FolderAnnotationSync").d("remoteTs: $remoteTs, new timestamp: $timestamp")
                        if (remoteTs >= timestamp) {
                            Timber.tag("FolderAnnotationSync").d("AnnotationSync: Remote sidecar is newer or same. Skipping write.")
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("FolderAnnotationSync").w(e, "AnnotationSync: Failed to check existing sidecar timestamp. Overwriting.")
                }
            } else {
                Timber.tag("FolderAnnotationSync").d("No existing sidecar found, will attempt to create a new one.")
            }

            val wrapper = JSONObject()
            wrapper.put("version", 1)
            wrapper.put("timestamp", timestamp)
            wrapper.put("data", JSONObject(jsonPayload))

            val targetFile = existingFile ?: rootTree.createFile("application/json", sidecarName)

            if (targetFile != null) {
                Timber.tag("FolderAnnotationSync").d("Writing data to targetFile: ${targetFile.uri}")
                context.contentResolver.openFileDescriptor(targetFile.uri, "rwt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(wrapper.toString().toByteArray())
                        fos.flush()
                        try { pfd.fileDescriptor.sync() } catch (_: Exception) {}
                    }
                }
                Timber.tag("FolderAnnotationSync").d("AnnotationSync: Saved sidecar for $bookId (ts=$timestamp)")
            } else {
                Timber.tag("FolderAnnotationSync").e("targetFile is null, failed to create new file in SAF rootTree")
            }

        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Failed to save annotation sidecar for $bookId")
        }
    }

    suspend fun getAnnotationSidecar(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext null
            val sidecarName = ".${bookId}_annotations.json"
            val file = rootTree.findFile(sidecarName)

            if (file != null && file.exists()) {
                val content = context.contentResolver.openInputStream(file.uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@withContext null

                val json = JSONObject(content)
                val ts = json.optLong("timestamp", 0L)
                val data = json.optJSONObject("data")?.toString() ?: return@withContext null

                return@withContext Pair(ts, data)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read annotation sidecar for $bookId")
        }
        return@withContext null
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

            // CHANGED: Scanning rootTree directly
            val allFiles = rootTree.listFiles()

            val groupedFiles = allFiles
                .filter { it.name?.endsWith(".json") == true || it.name?.contains(".sync-conflict") == true }
                .groupBy { file ->
                    var name = file.name ?: ""
                    if (name.startsWith(".")) name = name.substring(1)
                    name = name.substringBefore(".sync-conflict")
                    name.substringBefore(".json")
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