// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object LocalSyncUtils {
    private const val SYNC_DIR_NAME = ".episteme"
    private const val TAG = "FolderSync"

    suspend fun saveMetadataToFolder(
        context: Context,
        sourceFolderUri: Uri,
        metadata: FolderBookMetadata
    ) = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext

            val syncDir = getOrCreateSyncDir(rootTree)

            if (syncDir == null) {
                Timber.tag(TAG).e("Could not create/find $SYNC_DIR_NAME directory in $sourceFolderUri")
                return@withContext
            }

            val fileName = "${metadata.bookId}.json"

            // --- NEW: Clobber Protection ---
            // Check for existing file and compare timestamps before overwriting
            val existingFile = syncDir.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                try {
                    val existingContent = context.contentResolver.openInputStream(existingFile.uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }

                    if (existingContent != null) {
                        val existingMeta = FolderBookMetadata.fromJsonString(existingContent)
                        val diff = existingMeta.lastModifiedTimestamp - metadata.lastModifiedTimestamp
                        Timber.tag(TAG).d("ClobberCheck for ${metadata.bookId}: FolderTS=${existingMeta.lastModifiedTimestamp}, IncomingTS=${metadata.lastModifiedTimestamp}, Diff=$diff ms")

                        if (existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                            Timber.tag(TAG).w("ClobberCheck: ABORTING save. Folder has newer data.")
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to read existing metadata for conflict check")
                }

                // If we are here, our local data is newer or equal. Safe to delete and overwrite.
                try {
                    existingFile.delete()
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Failed to delete existing metadata file: ${e.message}")
                }
            }

            val newFile = syncDir.createFile("application/json", fileName)
            if (newFile == null) {
                Timber.tag(TAG).e("Could not create metadata file for ${metadata.bookId}")
                return@withContext
            }

            val jsonString = metadata.toJsonString()

            try {
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    output.write(jsonString.toByteArray())
                }
                Timber.tag(TAG).d("Saved metadata for ${metadata.bookId}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write content to metadata file for ${metadata.bookId}")
                try { newFile.delete() } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save local metadata to folder.")
        }
    }

    /**
     * Reads metadata for a specific book, including resolving any Syncthing conflicts.
     */
    suspend fun getBookMetadata(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ): FolderBookMetadata? = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext null
            val syncDir = findSyncDir(rootTree) ?: return@withContext null

            // Find the main file AND any sync-conflict files for this specific book
            val relatedFiles = syncDir.listFiles().filter { file ->
                val name = file.name ?: ""
                name.startsWith(bookId) && (name.endsWith(".json") || name.contains(".sync-conflict"))
            }

            if (relatedFiles.isEmpty()) return@withContext null

            // Resolve which file has the actual latest progress
            return@withContext resolveBestMetadata(context, relatedFiles)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error resolving book metadata for $bookId")
        }
        return@withContext null
    }

    /**
     * Helper to parse a list of files and return the one with the newest internal timestamp.
     */
    private fun resolveBestMetadata(context: Context, files: List<DocumentFile>): FolderBookMetadata? {
        var bestMeta: FolderBookMetadata? = null

        files.forEach { file ->
            try {
                val jsonString = context.contentResolver.openInputStream(file.uri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                if (jsonString != null) {
                    val meta = FolderBookMetadata.fromJsonString(jsonString)
                    if (bestMeta == null || meta.lastModifiedTimestamp > bestMeta.lastModifiedTimestamp) {
                        bestMeta = meta
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to parse conflict file: ${file.name}")
            }
        }

        if (files.size > 1 && bestMeta != null) {
            Timber.tag(TAG).i("Auto-resolved Syncthing conflict for ${bestMeta.bookId}. Winner TS: ${bestMeta.lastModifiedTimestamp}")
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

            val allFiles = syncDir.listFiles()

            val groupedFiles = allFiles.filter { it.name?.endsWith(".json") == true || it.name?.contains(".sync-conflict") == true }
                .groupBy { file ->
                    val name = file.name ?: ""
                    name.substringBefore(".json").substringBefore(".sync-conflict")
                }

            groupedFiles.forEach { (bookId, files) ->
                val winner = resolveBestMetadata(context, files)
                if (winner != null) {
                    finalResults[bookId] = winner
                }
            }

            Timber.tag(TAG).d("getAllFolderMetadata: Consolidated ${groupedFiles.size} book records from ${allFiles.size} total files.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning .episteme folder")
        }
        return@withContext finalResults
    }

    private fun findSyncDir(root: DocumentFile): DocumentFile? {
        val standardDir = root.findFile(SYNC_DIR_NAME)
        if (standardDir != null && standardDir.isDirectory) return standardDir

        val files = root.listFiles()
        return files.firstOrNull {
            it.isDirectory && (it.name == SYNC_DIR_NAME || it.name?.startsWith("$SYNC_DIR_NAME ") == true || it.name?.startsWith("$SYNC_DIR_NAME(") == true)
        }
    }

    private fun getOrCreateSyncDir(root: DocumentFile): DocumentFile? {
        val existing = findSyncDir(root)
        if (existing != null) return existing
        return root.createDirectory(SYNC_DIR_NAME)
    }
}