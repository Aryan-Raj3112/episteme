// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object LocalSyncUtils {
    private const val SYNC_DIR_NAME = "episteme"
    private const val TAG = "FolderSync"

    suspend fun saveMetadataToFolder(
        context: Context,
        sourceFolderUri: Uri,
        metadata: FolderBookMetadata
    ) = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext
            val syncDir = getOrCreateSyncDir(rootTree) ?: return@withContext

            ensureNoMedia(syncDir)

            val syncFileName = "${metadata.bookId}.json"
            val legacyHiddenName = ".${metadata.bookId}.json"

            val existingVisible = syncDir.findFile(syncFileName)
            val existingHidden = syncDir.findFile(legacyHiddenName)

            val fileToCheck = existingVisible ?: existingHidden

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

            var targetFile = existingVisible

            if (targetFile == null) {
                if (existingHidden != null && existingHidden.exists()) {
                    try { existingHidden.delete() } catch (_: Exception) {}
                }
                targetFile = syncDir.createFile("application/json", syncFileName)
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

                val anchorName = ".sync_anchor"
                val anchorFile = syncDir.findFile(anchorName) ?: syncDir.createFile("text/plain", anchorName)

                if (anchorFile != null) {
                    try {
                        context.contentResolver.openFileDescriptor(anchorFile.uri, "rwt")?.use { pfd ->
                            val timestampMsg = "${System.currentTimeMillis()}"
                            java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                                fos.write(timestampMsg.toByteArray())
                                fos.flush()
                                pfd.fileDescriptor.sync()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to kick anchor file: ${e.message}")
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
                    Timber.tag(TAG).d("Triggered MediaScanner on absolute path: $absolutePath")
                } else {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.uri.path),
                        arrayOf("application/json"),
                        null
                    )
                }

                Timber.tag(TAG).d("Saved and synced metadata for ${metadata.bookId}")

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write metadata for ${metadata.bookId}")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save local metadata to folder.")
        }
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
            val syncDir = findSyncDir(rootTree) ?: return@withContext finalResults

            // Ensure .nomedia exists while scanning
            ensureNoMedia(syncDir)

            val allFiles = syncDir.listFiles()

            // Group files by bookId.
            // Filename formats:
            // 1. hidden: .[bookId].json
            // 2. legacy: [bookId].json
            // 3. conflict: .[bookId].sync-conflict... or [bookId].sync-conflict...
            val groupedFiles = allFiles
                .filter { it.name?.endsWith(".json") == true || it.name?.contains(".sync-conflict") == true }
                .groupBy { file ->
                    var name = file.name ?: ""

                    // Remove leading dot
                    if (name.startsWith(".")) name = name.substring(1)

                    // Remove conflict suffix
                    name = name.substringBefore(".sync-conflict")

                    // Remove extension
                    name.substringBefore(".json")
                }

            groupedFiles.forEach { (bookId, files) ->
                val winner = resolveAndCleanConflicts(context, files, bookId)
                if (winner != null) {
                    finalResults[bookId] = winner
                }
            }

            Timber.tag(TAG).d("getAllFolderMetadata: Consolidated ${groupedFiles.size} book records.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning .episteme folder")
        }
        return@withContext finalResults
    }

    private fun findSyncDir(root: DocumentFile): DocumentFile? {
        // Look for exact match first
        val standardDir = root.findFile(SYNC_DIR_NAME)
        if (standardDir != null && standardDir.isDirectory) return standardDir

        // Fallback search
        val files = root.listFiles()
        return files.firstOrNull {
            it.isDirectory && (it.name == SYNC_DIR_NAME)
        }
    }

    private fun getOrCreateSyncDir(root: DocumentFile): DocumentFile? {
        val existing = findSyncDir(root)
        if (existing != null) return existing
        return root.createDirectory(SYNC_DIR_NAME)
    }

    private fun ensureNoMedia(dir: DocumentFile) {
        if (dir.findFile(".nomedia") == null) {
            try {
                dir.createFile("application/octet-stream", ".nomedia")
            } catch (_: Exception) {
                Timber.tag(TAG).w("Failed to create .nomedia file")
            }
        }
    }
}