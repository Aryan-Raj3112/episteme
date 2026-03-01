// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.Context
import android.net.Uri
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

            val syncDir = getOrCreateSyncDir(rootTree)

            if (syncDir == null) {
                Timber.tag(TAG).e("Could not create/find $SYNC_DIR_NAME directory in $sourceFolderUri")
                return@withContext
            }

            // Ensure .nomedia exists to prevent gallery clutter
            ensureNoMedia(syncDir)

            // Use hidden filename to avoid "Recents" clutter
            val hiddenFileName = ".${metadata.bookId}.json"
            val legacyFileName = "${metadata.bookId}.json"

            // Check for existing files (Hidden OR Legacy)
            val existingHidden = syncDir.findFile(hiddenFileName)
            val existingLegacy = syncDir.findFile(legacyFileName)

            // Prefer hidden, fallback to legacy for conflict check
            val existingFile = existingHidden ?: existingLegacy

            if (existingFile != null && existingFile.exists()) {
                try {
                    val existingContent = context.contentResolver.openInputStream(existingFile.uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }

                    if (existingContent != null) {
                        val existingMeta = FolderBookMetadata.fromJsonString(existingContent)
                        val diff = existingMeta.lastModifiedTimestamp - metadata.lastModifiedTimestamp

                        // Clobber Protection
                        if (existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                            Timber.tag(TAG).w("ClobberCheck: ABORTING save for ${metadata.bookId}. Folder has newer data.")
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to read existing metadata for conflict check")
                }

                // Delete the existing file (whether hidden or legacy) before writing new one
                try {
                    existingFile.delete()
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Failed to delete existing metadata file: ${e.message}")
                }
            }

            // If we had a legacy file that wasn't the 'existingFile' (edge case), delete it too
            if (existingLegacy != null && existingLegacy.exists()) {
                try { existingLegacy.delete() } catch (_: Exception) {}
            }

            val newFile = syncDir.createFile("application/json", hiddenFileName)
            if (newFile == null) {
                Timber.tag(TAG).e("Could not create metadata file for ${metadata.bookId}")
                return@withContext
            }

            val jsonString = metadata.toJsonString()

            try {
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    output.write(jsonString.toByteArray())
                }
                Timber.tag(TAG).d("Saved metadata for ${metadata.bookId} (Hidden)")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write content to metadata file for ${metadata.bookId}")
                try { newFile.delete() } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save local metadata to folder.")
        }
    }

    suspend fun getBookMetadata(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ): FolderBookMetadata? = withContext(Dispatchers.IO) {
        try {
            val rootTree = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext null
            val syncDir = findSyncDir(rootTree) ?: return@withContext null

            // Find all related files: hidden, legacy, and conflicts
            val relatedFiles = syncDir.listFiles().filter { file ->
                val name = file.name ?: ""
                // Match: .bookId.json, bookId.json, or containing .sync-conflict
                (name.contains(bookId)) && (name.endsWith(".json") || name.contains(".sync-conflict"))
            }

            if (relatedFiles.isEmpty()) return@withContext null

            return@withContext resolveAndCleanConflicts(context, relatedFiles, bookId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error resolving book metadata for $bookId")
        }
        return@withContext null
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
                    // Ensure this file actually belongs to the book (defensive check against partial name matches)
                    if (meta.bookId == bookId) {
                        if (bestMeta == null || meta.lastModifiedTimestamp > bestMeta!!.lastModifiedTimestamp) {
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
            val filesToDelete = files.filter { it.uri != bestFile!!.uri }

            if (filesToDelete.isNotEmpty()) {
                Timber.tag(TAG).i("Resolving conflicts for $bookId. Winner: ${bestFile!!.name}. Deleting ${filesToDelete.size} obsolete files.")
                filesToDelete.forEach {
                    try { it.delete() } catch(_: Exception) {}
                }
            }

            // 3. Migrate Legacy to Hidden if needed
            val winnerName = bestFile!!.name ?: ""
            if (!winnerName.startsWith(".")) {
                Timber.tag(TAG).i("Migrating legacy file to hidden: $winnerName")
                // We can't always rename easily with DocumentFile, so we allow 'saveMetadataToFolder'
                // to handle the actual file swap next time a write happens, OR we could force a rewrite.
                // For now, we leave it. The clutter is reduced by deleting conflicts.
                // The next save operation will create the hidden file and delete this one.
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
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed to create .nomedia file")
            }
        }
    }
}