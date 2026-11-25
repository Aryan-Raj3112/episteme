// LocalSyncUtils.kt
package com.aryan.reader.data

import android.content.ContentResolver
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
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
import timber.log.Timber

object LocalSyncUtils {
    private const val TAG = "FolderSync"
    private const val ANNOTATION_SUFFIX = "_annotations"
    private const val SYNC_SUBFOLDER_NAME = LOCAL_FOLDER_SYNC_DATA_DIR
    private val annotationSidecarWriteMutex = Mutex()

    private data class SyncFileEntry(
        val name: String,
        val uri: Uri,
        val documentId: String = "",
        val mimeType: String = "",
        val lastModified: Long = 0L,
        val size: Long = 0L
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private data class SyncDirHandle(
        val documentUri: Uri,
        val entries: List<SyncFileEntry>
    )

    private data class ParsedAnnotationSidecar(
        val name: String,
        val bookId: String,
        val timestamp: Long,
        val data: String
    )

    private data class ResolvedAnnotationSidecar(
        val timestamp: Long,
        val data: String,
        val files: List<Uri>,
        val hasUnreadableCandidate: Boolean
    )

    private data class MetadataSidecarResolution(
        val canonicalUri: Uri?,
        val metadata: FolderBookMetadata?,
        val losers: List<Uri>
    )

    /**
     * Per-file index entry: the file stamp decides whether the payload has to be read again, and
     * the book id plus payload timestamp let [getAllFolderMetadata] skip books whose metadata the
     * caller already applied (a caller that lost the row still gets a full read).
     */
    private data class SidecarStamp(
        val lastModified: Long,
        val size: Long,
        val bookId: String = "",
        val timestamp: Long = 0L,
    ) {
        fun matches(entry: SyncFileEntry): Boolean =
            lastModified == entry.lastModified && size == entry.size
    }

    private fun syncSubfolderDocId(rootDocId: String): String {
        return if (rootDocId.endsWith("/$SYNC_SUBFOLDER_NAME")) {
            rootDocId
        } else if (rootDocId.endsWith(":")) {
            rootDocId + SYNC_SUBFOLDER_NAME
        } else {
            "$rootDocId/$SYNC_SUBFOLDER_NAME"
        }
    }

    /**
     * Lists one SAF directory with a single [ContentResolver.query].  Every DocumentFile
     * accessor (name, lastModified, size) is a separate provider round trip, so directory
     * contents always come from one cursor whose projection carries everything we need.
     */
    private fun queryChildEntries(
        context: Context,
        sourceFolderUri: Uri,
        parentDocumentId: String,
        includeStats: Boolean
    ): List<SyncFileEntry> {
        val resolver = context.contentResolver
        val projection = if (includeStats) {
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )
        } else {
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
        }
        val entries = ArrayList<SyncFileEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceFolderUri, parentDocumentId)
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedCol = if (includeStats) cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED) else -1
            val sizeCol = if (includeStats) cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE) else -1
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val documentId = cursor.getString(idCol) ?: continue
                entries.add(
                    SyncFileEntry(
                        name = name,
                        uri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, documentId),
                        documentId = documentId,
                        mimeType = cursor.getString(mimeCol) ?: "",
                        lastModified = if (modifiedCol >= 0 && !cursor.isNull(modifiedCol)) {
                            cursor.getLong(modifiedCol)
                        } else {
                            0L
                        },
                        size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    )
                )
            }
        }
        return entries
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

        val entries = try {
            queryChildEntries(context, sourceFolderUri, syncDocId, includeStats = true)
                .filterNot { it.isDirectory }
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

    private fun probeDocumentMimeType(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        return resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    /**
     * Resolves the sync-data directory without touching [DocumentFile]: one probe of the known
     * document id, one root listing only when the probe cannot resolve it, then one child listing.
     */
    private fun ensureSyncDir(
        context: Context,
        sourceFolderUri: Uri,
        createIfMissing: Boolean = true
    ): SyncDirHandle? {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(sourceFolderUri) }.getOrNull()
            ?: return null
        val resolver = context.contentResolver
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, rootDocId)
        val syncDocId = syncSubfolderDocId(rootDocId)
        val syncDirUri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, syncDocId)
        val probedMime = runCatching { probeDocumentMimeType(context, syncDirUri) }.getOrNull()
        val finalDocId = when (probedMime) {
            DocumentsContract.Document.MIME_TYPE_DIR -> syncDocId
            null -> {
                // Not every provider answers a document-id probe (and it can fail transiently), so
                // fall back to one listing of the root before creating anything.
                val existingEntry = runCatching {
                    queryChildEntries(context, sourceFolderUri, rootDocId, includeStats = false)
                        .firstOrNull { it.name == SYNC_SUBFOLDER_NAME }
                }.getOrNull() ?: return null
                when {
                    existingEntry == null -> {
                        if (!createIfMissing) return null
                        val created = runCatching {
                            DocumentsContract.createDocument(
                                resolver,
                                rootDocumentUri,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                SYNC_SUBFOLDER_NAME
                            )
                        }.getOrNull() ?: return null
                        runCatching { DocumentsContract.getDocumentId(created) }.getOrNull() ?: return null
                    }
                    existingEntry.isDirectory -> existingEntry.documentId
                    else -> {
                        Timber.tag(TAG).e("Cannot sync: $SYNC_SUBFOLDER_NAME exists and is not a directory")
                        return null
                    }
                }
            }
            else -> {
                Timber.tag(TAG).e("Cannot sync: $SYNC_SUBFOLDER_NAME exists and is not a directory")
                return null
            }
        }
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceFolderUri, finalDocId)
        val entries = runCatching {
            queryChildEntries(context, sourceFolderUri, finalDocId, includeStats = true)
        }.getOrElse {
            Timber.tag(TAG).e(it, "Failed to list $SYNC_SUBFOLDER_NAME children")
            return null
        }
        return SyncDirHandle(documentUri = documentUri, entries = entries)
    }

    /** True when the document is still listed by the provider (a failed rename may just mean it
     * disappeared between the directory listing and the write). */
    private fun documentStillExists(context: Context, uri: Uri): Boolean =
        runCatching { probeDocumentMimeType(context, uri) }.getOrNull() != null

    private fun readDocumentText(resolver: ContentResolver, uri: Uri): String? =
        runCatching { resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()

    private fun writeDocumentBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "rwt") ?: return false
            descriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                    try {
                        pfd.fileDescriptor.sync()
                    } catch (_: Exception) {
                    }
                }
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to write document $uri")
            false
        }
    }

    private fun renameDocument(context: Context, uri: Uri, newName: String): Uri? =
        runCatching { DocumentsContract.renameDocument(context.contentResolver, uri, newName) }
            .onFailure { Timber.tag(TAG).w(it, "renameDocument failed for $uri") }
            .getOrNull()

    private fun deleteDocument(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .onFailure { Timber.tag(TAG).w(it, "deleteDocument failed for $uri") }
            .getOrDefault(false)

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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val syncDir = ensureSyncDir(context, sourceFolderUri)
                ?: return@withContext false

            val syncFileName = localFolderSyncMetadataFileName(metadata.bookId)
            val resolution = resolveMetadataSidecarConflicts(
                context = context,
                syncDir = syncDir,
                bookId = metadata.bookId
            )
            val existingMeta = resolution.metadata
            if (existingMeta != null && existingMeta.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                // The winner was already renamed to the canonical name; drop the older readable
                // conflict copies so they do not accumulate in the folder.
                resolution.losers.forEach { deleteDocument(context, it) }
                Timber.tag(TAG).w("ClobberCheck: ABORTING save. Folder has newer data for ${metadata.bookId}.")
                // The remote/newer sidecar remains authoritative.  This is a
                // successful no-op, not an I/O failure that should be retried.
                return@withContext true
            }

            val tempFileName = uniqueFolderSyncTempName(localFolderSyncMetadataTempFileName(metadata.bookId))
            val tempUri = runCatching {
                DocumentsContract.createDocument(resolver, syncDir.documentUri, "application/json", tempFileName)
            }.getOrNull()
            if (tempUri == null) {
                Timber.tag(TAG).e("Could not create temp metadata file for ${metadata.bookId}")
                return@withContext false
            }

            if (!writeDocumentBytes(context, tempUri, metadata.toJsonString().toByteArray(Charsets.UTF_8))) {
                Timber.tag(TAG).e("Failed to write temp metadata for ${metadata.bookId}")
                deleteDocument(context, tempUri)
                return@withContext false
            }

            // Keep the old canonical file until the new one is fully written;
            // SAF rename is not guaranteed to be atomic across providers, so
            // preserve a recoverable backup while committing the replacement.
            val canonicalUri = resolution.canonicalUri
                ?: syncDir.entries.firstOrNull { it.name == syncFileName }?.uri
            var backupUri: Uri? = null
            if (canonicalUri != null) {
                val backupName = "$syncFileName.sync-backup-${System.currentTimeMillis()}-${Thread.currentThread().id}.json"
                backupUri = renameDocument(context, canonicalUri, backupName)
                if (backupUri == null && documentStillExists(context, canonicalUri)) {
                    Timber.tag(TAG).e("Could not preserve existing metadata before replacement: $syncFileName")
                    deleteDocument(context, tempUri)
                    return@withContext false
                }
            }

            val installedUri = renameDocument(context, tempUri, syncFileName)
            if (installedUri == null) {
                Timber.tag(TAG).e("Failed to rename temp metadata file to $syncFileName")
                backupUri?.let { renameDocument(context, it, syncFileName) }
                deleteDocument(context, tempUri)
                return@withContext false
            }

            val installed = readDocumentText(resolver, installedUri)
                ?.let { runCatching { FolderBookMetadata.fromJsonString(it) }.getOrNull() }
            if (installed?.bookId != metadata.bookId) {
                Timber.tag(TAG).e("Installed metadata failed validation; restoring previous copy: $syncFileName")
                deleteDocument(context, installedUri)
                backupUri?.let { renameDocument(context, it, syncFileName) }
                return@withContext false
            }

            if (backupUri != null && !deleteDocument(context, backupUri)) {
                // The new canonical copy is valid.  A stale backup is harmless and
                // can be cleaned by a later reconciliation.
                Timber.tag(TAG).w("Metadata committed but backup cleanup failed: $backupUri")
            }
            resolution.losers.filter { it != installedUri }.forEach { deleteDocument(context, it) }

            Timber.tag(TAG).d("Atomic save successful: $syncFileName")
            val absolutePath = getPathFromUri(context, installedUri)
            if (absolutePath != null) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(absolutePath),
                    arrayOf("application/json"),
                    null
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save local metadata to folder.")
            false
        }
    }

    suspend fun saveAnnotationSidecar(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String,
        jsonPayload: String,
        timestamp: Long
    ): Boolean = annotationSidecarWriteMutex.withLock { withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("saveAnnotationSidecar called for bookId: $bookId, timestamp: $timestamp")
        try {
            val resolver = context.contentResolver
            val syncDir = ensureSyncDir(context, sourceFolderUri) ?: return@withContext false
            val currentBest = resolveAnnotationConflicts(context, syncDir, bookId)
            val targetName = localFolderSyncAnnotationFileName(bookId)
            val tempName = uniqueFolderSyncTempName(localFolderSyncAnnotationTempFileName(bookId))

            if (currentBest?.hasUnreadableCandidate == true) {
                Timber.tag("FolderAnnotationSync").w(
                    "Refusing to overwrite $targetName while a matching sidecar is unreadable."
                )
                return@withContext false
            }

            val mergedPayload = currentBest?.let { remote ->
                SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
                    localDataJson = jsonPayload,
                    remoteDataJson = remote.data,
                    preferRemoteOnConflict = remote.timestamp > timestamp
                )
            } ?: jsonPayload
            val mergedTimestamp = maxOf(timestamp, currentBest?.timestamp ?: 0L)

            val wrapper = JSONObject()
            wrapper.put("version", 1)
            wrapper.put("bookId", bookId)
            wrapper.put("timestamp", mergedTimestamp)
            wrapper.put("data", JSONObject(mergedPayload))

            val tempUri = runCatching {
                DocumentsContract.createDocument(resolver, syncDir.documentUri, "application/json", tempName)
            }.getOrNull()
            if (tempUri == null) {
                Timber.tag("FolderAnnotationSync").e("Failed to create temp sidecar file.")
                return@withContext false
            }
            if (!writeDocumentBytes(context, tempUri, wrapper.toString().toByteArray(Charsets.UTF_8))) {
                Timber.tag("FolderAnnotationSync").e("Error writing to temp sidecar.")
                deleteDocument(context, tempUri)
                return@withContext false
            }

            val canonicalUri = syncDir.entries.firstOrNull { it.name == targetName }?.uri
            var backupUri: Uri? = null
            if (canonicalUri != null) {
                val backupName = "$targetName.sync-conflict-episteme-${System.currentTimeMillis()}.json"
                backupUri = renameDocument(context, canonicalUri, backupName)
                if (backupUri == null && documentStillExists(context, canonicalUri)) {
                    Timber.tag("FolderAnnotationSync").e("Could not preserve existing sidecar before replacement.")
                    deleteDocument(context, tempUri)
                    return@withContext false
                }
            }

            val installedUri = renameDocument(context, tempUri, targetName)
            if (installedUri == null) {
                Timber.tag("FolderAnnotationSync").e("AnnotationSync: Failed to rename temp sidecar to $targetName")
                backupUri?.let { renameDocument(context, it, targetName) }
                deleteDocument(context, tempUri)
                return@withContext false
            }

            val installed = readDocumentText(resolver, installedUri)
                ?.let { parseAnnotationSidecarText(it, targetName, bookId) }
            if (installed == null || installed.bookId != bookId) {
                Timber.tag("FolderAnnotationSync").e("Installed sidecar failed validation; restoring previous copy.")
                deleteDocument(context, installedUri)
                backupUri?.let { renameDocument(context, it, targetName) }
                return@withContext false
            }

            currentBest?.files.orEmpty()
                .filter { it != canonicalUri && it != installedUri }
                .forEach { candidate -> deleteDocument(context, candidate) }
            backupUri?.let { deleteDocument(context, it) }
            Timber.tag("FolderAnnotationSync").d("AnnotationSync: merged save successful for $targetName")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Failed to save annotation sidecar for $bookId")
            false
        }
    } }

    /**
     * One book's annotation sidecars. Only the metadata of the files is resolved here; the payload
     * is read by [readAnnotationSidecarPayload] when the caller actually imports this book.
     */
    class AnnotationSidecarHandle internal constructor(
        val bookId: String,
        val timestamp: Long,
        internal val files: List<Pair<String, Uri>>,
    )

    private data class AnnotationIndexEntry(
        val lastModified: Long,
        val size: Long,
        val timestamp: Long,
        val bookId: String,
    )

    /**
     * Resolves the annotation sidecars of a folder without reading every payload.
     *
     * A folder can hold one sidecar per book, so reading and parsing all of them on every folder
     * scan is one provider round trip plus a JSON parse per book even when nothing changed. Parsed
     * timestamps are persisted per folder in cacheDir keyed by the file stamp, so only sidecars
     * whose (mtime, size) changed are read again; a payload read happens only for the books that
     * are actually imported.
     */
    suspend fun preloadAnnotationSidecarIndex(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, AnnotationSidecarHandle> = withContext(Dispatchers.IO) {
        try {
            val syncDir = ensureSyncDir(context, sourceFolderUri, createIfMissing = false)
                ?: return@withContext emptyMap()
            preloadAnnotationSidecarIndex(
                context = context,
                indexKey = sourceFolderUri,
                candidates = syncDir.entries.filter { isAnnotationSidecarCandidateName(it.name) },
            )
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Error preloading annotation sidecars")
            emptyMap()
        }
    }

    /** Local-file variant of [preloadAnnotationSidecarIndex] for app-managed folders. */
    suspend fun preloadAnnotationSidecarIndexFromAppStorage(
        context: Context,
        root: File,
    ): Map<String, AnnotationSidecarHandle> = withContext(Dispatchers.IO) {
        val syncDir = File(root, SYNC_SUBFOLDER_NAME)
        if (!syncDir.isDirectory) return@withContext emptyMap()
        val candidates = syncDir.listFiles().orEmpty()
            .filter { it.isFile && isAnnotationSidecarCandidateName(it.name) }
            .map { file ->
                SyncFileEntry(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    lastModified = file.lastModified(),
                    size = file.length(),
                )
            }
        preloadAnnotationSidecarIndex(
            context = context,
            indexKey = Uri.fromFile(syncDir),
            candidates = candidates,
        )
    }

    private suspend fun preloadAnnotationSidecarIndex(
        context: Context,
        indexKey: Uri,
        candidates: List<SyncFileEntry>,
    ): Map<String, AnnotationSidecarHandle> {
        val entriesByName = candidates.associateBy { it.name }
        if (candidates.isEmpty()) {
            sidecarIndexMutex.withLock { writeAnnotationIndex(context, indexKey, emptyMap()) }
            return emptyMap()
        }

        val cached = sidecarIndexMutex.withLock { readAnnotationIndex(context, indexKey) }
        val next = HashMap<String, AnnotationIndexEntry>(candidates.size)
        val parsedByBook = HashMap<String, MutableList<ParsedAnnotationSidecar>>()
        candidates.forEach { entry ->
            val cachedEntry = cached[entry.name]
            // A provider that reports neither size nor last-modified cannot prove a sidecar is
            // unchanged; read it every time instead of trusting the index.
            val stampUsable = entry.lastModified > 0L || entry.size > 0L
            val resolved = if (
                stampUsable &&
                cachedEntry != null &&
                cachedEntry.bookId.isNotBlank() &&
                cachedEntry.lastModified == entry.lastModified &&
                cachedEntry.size == entry.size
            ) {
                ParsedAnnotationSidecar(
                    name = entry.name,
                    bookId = cachedEntry.bookId,
                    timestamp = cachedEntry.timestamp,
                    data = "",
                )
            } else {
                parseAnnotationSidecar(
                    context = context,
                    file = entry,
                    fallbackBookId = extractLegacyAnnotationBookId(entry.name),
                )
            } ?: return@forEach

            if (stampUsable) {
                next[entry.name] = AnnotationIndexEntry(
                    lastModified = entry.lastModified,
                    size = entry.size,
                    timestamp = resolved.timestamp,
                    bookId = resolved.bookId,
                )
            }
            parsedByBook.getOrPut(resolved.bookId) { mutableListOf() } += resolved
        }

        sidecarIndexMutex.withLock { writeAnnotationIndex(context, indexKey, next) }

        return parsedByBook.mapValues { (bookId, sidecars) ->
            AnnotationSidecarHandle(
                bookId = bookId,
                timestamp = sidecars.maxOf { it.timestamp },
                files = sidecars.mapNotNull { entriesByName[it.name]?.let { entry -> entry.name to entry.uri } },
            )
        }
    }

    /** Reads and merges the payload of a previously indexed annotation sidecar. */
    suspend fun readAnnotationSidecarPayload(
        context: Context,
        handle: AnnotationSidecarHandle
    ): String? = withContext(Dispatchers.IO) {
        val parsed = handle.files.mapNotNull { (name, uri) ->
            readDocumentText(context.contentResolver, uri)
                ?.let { parseAnnotationSidecarText(it, name, extractLegacyAnnotationBookId(name)) }
        }
        mergeParsedAnnotationSidecars(parsed)?.data
    }

    /** Payload-carrying variant used by the sidecar migration, which rewrites every sidecar. */
    suspend fun preloadAnnotationSidecars(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, Pair<Long, String>> = withContext(Dispatchers.IO) {
        preloadAnnotationSidecarIndex(context, sourceFolderUri)
            .mapNotNull { (bookId, handle) ->
                readAnnotationSidecarPayload(context, handle)?.let { bookId to (handle.timestamp to it) }
            }
            .toMap()
    }

    private fun readAnnotationIndex(
        context: Context,
        sourceFolderUri: Uri
    ): Map<String, AnnotationIndexEntry> {
        val file = sidecarIndexFile(context, sourceFolderUri, ANNOTATION_INDEX_DIR)
        if (!file.isFile) return emptyMap()
        return runCatching {
            val entries = JSONObject(file.readText()).optJSONObject("entries") ?: return emptyMap()
            val stamps = HashMap<String, AnnotationIndexEntry>(entries.length())
            entries.keys().forEach { name ->
                val entry = entries.optJSONObject(name) ?: return@forEach
                stamps[name] = AnnotationIndexEntry(
                    lastModified = entry.optLong("mtime", 0L),
                    size = entry.optLong("size", 0L),
                    timestamp = entry.optLong("ts", 0L),
                    bookId = entry.optString("bookId").orEmpty(),
                )
            }
            stamps
        }.getOrElse {
            Timber.tag(TAG).w(it, "Failed to read annotation index for $sourceFolderUri")
            emptyMap()
        }
    }

    private fun writeAnnotationIndex(
        context: Context,
        sourceFolderUri: Uri,
        entries: Map<String, AnnotationIndexEntry>
    ) {
        runCatching {
            val file = sidecarIndexFile(context, sourceFolderUri, ANNOTATION_INDEX_DIR)
            val dir = file.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val json = JSONObject()
            entries.forEach { (name, entry) ->
                json.put(
                    name,
                    JSONObject()
                        .put("mtime", entry.lastModified)
                        .put("size", entry.size)
                        .put("ts", entry.timestamp)
                        .put("bookId", entry.bookId)
                )
            }
            val payload = JSONObject().put("version", 1).put("entries", json).toString()
            val temp = File(dir, "${file.name}.tmp")
            temp.writeText(payload)
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            Timber.tag(TAG).w(it, "Failed to persist annotation index for $sourceFolderUri")
        }
    }

    suspend fun getAnnotationSidecar(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        try {
            val syncDir = ensureSyncDir(context, sourceFolderUri, createIfMissing = false)
                ?: return@withContext null
            val resolved = resolveAnnotationConflicts(context, syncDir, bookId)
                ?: return@withContext null
            if (resolved.hasUnreadableCandidate) return@withContext null
            return@withContext resolved.timestamp to resolved.data
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read annotation sidecar for $bookId")
        }
        return@withContext null
    }

    private fun resolveAnnotationConflicts(
        context: Context,
        syncDir: SyncDirHandle,
        bookId: String
    ): ResolvedAnnotationSidecar? {
        val candidateFiles = syncDir.entries.filter { entry ->
            isAnnotationCandidateForBook(entry.name, bookId)
        }
        if (candidateFiles.isEmpty()) return null
        var hasUnreadableCandidate = false
        val candidates = candidateFiles.mapNotNull { entry ->
            val parsed = parseAnnotationSidecar(
                context = context,
                file = entry,
                fallbackBookId = extractLegacyAnnotationBookId(entry.name)
            )
            if (parsed == null) hasUnreadableCandidate = true
            parsed?.takeIf { it.bookId == bookId }
        }
        val merged = mergeParsedAnnotationSidecars(candidates)
        return ResolvedAnnotationSidecar(
            timestamp = merged?.timestamp ?: 0L,
            data = merged?.data ?: "{}",
            files = candidateFiles.map { it.uri },
            hasUnreadableCandidate = hasUnreadableCandidate || merged == null
        )
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
     * Picks the newest readable metadata sidecar for a book, canonicalises its file name and
     * reports the losing conflict copies.  Cleanup is deferred to the caller so an aborted save
     * never deletes folder data.
     */
    private fun resolveMetadataSidecarConflicts(
        context: Context,
        syncDir: SyncDirHandle,
        bookId: String
    ): MetadataSidecarResolution {
        val hashedStem = localFolderSyncSidecarStem(bookId)
        val candidates = syncDir.entries.filter { entry ->
            val normalizedName = entry.name.normalizedSidecarName()
            isMetadataSidecarCandidateName(entry.name) && (
                normalizedName.matchesJsonSidecarStem(hashedStem) ||
                    normalizedName.matchesJsonSidecarStem(bookId)
                )
        }
        if (candidates.isEmpty()) {
            return MetadataSidecarResolution(null, null, emptyList())
        }

        val readableUris = mutableSetOf<Uri>()
        var bestMeta: FolderBookMetadata? = null
        var bestEntry: SyncFileEntry? = null
        candidates.forEach { entry ->
            val jsonString = readDocumentText(context.contentResolver, entry.uri) ?: return@forEach
            val meta = runCatching { FolderBookMetadata.fromJsonString(jsonString) }
                .onFailure { Timber.tag(TAG).w(it, "Failed to parse conflict file: ${entry.name}") }
                .getOrNull() ?: return@forEach
            if (meta.bookId != bookId) return@forEach
            readableUris.add(entry.uri)
            if (bestMeta == null || meta.lastModifiedTimestamp > bestMeta.lastModifiedTimestamp) {
                bestMeta = meta
                bestEntry = entry
            }
        }
        val winnerMeta = bestMeta ?: return MetadataSidecarResolution(null, null, emptyList())
        val winnerEntry = bestEntry ?: return MetadataSidecarResolution(null, null, emptyList())
        val correctName = localFolderSyncMetadataFileName(bookId)
        var canonicalEntry = winnerEntry
        if (winnerEntry.name != correctName) {
            val renamed = renameDocument(context, winnerEntry.uri, correctName)
            if (renamed != null) {
                Timber.tag(TAG).i("Renaming metadata winner ${winnerEntry.name} to $correctName")
                canonicalEntry = winnerEntry.copy(name = correctName, uri = renamed)
            }
        }
        val losers = candidates
            .filter { it.uri in readableUris && it.uri != canonicalEntry.uri && it.uri != winnerEntry.uri }
            .map { it.uri }
        return MetadataSidecarResolution(canonicalEntry.uri, winnerMeta, losers)
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

    private fun isAnnotationCandidateForBook(name: String, bookId: String): Boolean {
        if (!isAnnotationSidecarCandidateName(name)) return false
        val normalized = name.normalizedSidecarName()
        val hashedStem = "${localFolderSyncSidecarStem(bookId)}$ANNOTATION_SUFFIX"
        val legacyStem = "$bookId$ANNOTATION_SUFFIX"
        return normalized.matchesJsonSidecarStem(hashedStem) ||
            normalized.matchesJsonSidecarStem(legacyStem)
    }

    private fun mergeParsedAnnotationSidecars(
        sidecars: List<ParsedAnnotationSidecar>
    ): ParsedAnnotationSidecar? {
        val bookId = sidecars.firstOrNull()?.bookId ?: return null
        val merged = SharedPdfAnnotationSidecarCodec.mergeAnnotationSnapshots(
            sidecars.map { sidecar ->
                SharedPdfAnnotationSidecarSnapshot(sidecar.name, sidecar.timestamp, sidecar.data)
            }
        ) ?: return null
        return ParsedAnnotationSidecar(merged.name, bookId, merged.timestamp, merged.data)
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
    ): ParsedAnnotationSidecar? =
        readDocumentText(context.contentResolver, file.uri)
            ?.let { parseAnnotationSidecarText(it, file.name, fallbackBookId) }

    private fun parseAnnotationSidecarText(
        content: String,
        name: String,
        fallbackBookId: String?
    ): ParsedAnnotationSidecar? {
        return try {
            val json = JSONObject(content)
            val bookId = json.optString("bookId").takeIf { it.isNotBlank() }
                ?: fallbackBookId
                ?: return null
            val data = json.optJSONObject("data")?.toString() ?: return null
            ParsedAnnotationSidecar(
                name = name,
                bookId = bookId,
                timestamp = json.optLong("timestamp", 0L),
                data = data
            )
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Error parsing annotation sidecar: $name")
            null
        }
    }

    suspend fun deleteBookSidecars(
        context: Context,
        sourceFolderUri: Uri,
        bookId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val syncDir = ensureSyncDir(context, sourceFolderUri, createIfMissing = false)
                ?: return@withContext
            val hashedStem = localFolderSyncSidecarStem(bookId)
            val hashedAnnotationStem = "$hashedStem$ANNOTATION_SUFFIX"
            syncDir.entries
                .filter { entry ->
                    val normalized = entry.name.normalizedSidecarName()
                    normalized.matchesJsonSidecarStem(hashedStem) ||
                        normalized.matchesJsonSidecarStem(bookId) ||
                        normalized.matchesJsonSidecarStem(hashedAnnotationStem) ||
                        normalized.matchesJsonSidecarStem("$bookId$ANNOTATION_SUFFIX")
                }
                .forEach { deleteDocument(context, it.uri) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete folder sidecars for $bookId")
        }
    }

    suspend fun deleteSyncDataFolder(
        context: Context,
        sourceFolderUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(sourceFolderUri) }
                .getOrNull() ?: return@withContext false
            val resolver = context.contentResolver
            val syncDirUri = DocumentsContract.buildDocumentUriUsingTree(
                sourceFolderUri,
                syncSubfolderDocId(rootDocId)
            )
            val mimeType = runCatching { probeDocumentMimeType(context, syncDirUri) }
                .getOrElse {
                    Timber.tag(TAG).e(it, "Failed to probe sync data folder")
                    return@withContext false
                }
            when (mimeType) {
                null -> true
                DocumentsContract.Document.MIME_TYPE_DIR -> deleteDocument(context, syncDirUri)
                else -> false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete sync data folder")
            false
        }
    }

    private const val SIDECAR_INDEX_DIR = "folder_sidecar_index"
    private const val ANNOTATION_INDEX_DIR = "folder_annotation_index"
    private val sidecarIndexMutex = Mutex()

    private fun sidecarIndexFile(
        context: Context,
        sourceFolderUri: Uri,
        directory: String = SIDECAR_INDEX_DIR,
    ): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sourceFolderUri.toString().toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(File(context.cacheDir, directory), "$key.json")
    }

    private fun readSidecarIndex(context: Context, sourceFolderUri: Uri): Map<String, SidecarStamp> {
        val file = sidecarIndexFile(context, sourceFolderUri)
        if (!file.isFile) return emptyMap()
        return runCatching {
            val entries = JSONObject(file.readText()).optJSONObject("entries") ?: return emptyMap()
            val stamps = HashMap<String, SidecarStamp>(entries.length())
            entries.keys().forEach { name ->
                val entry = entries.optJSONObject(name) ?: return@forEach
                stamps[name] = SidecarStamp(
                    lastModified = entry.optLong("mtime", 0L),
                    size = entry.optLong("size", 0L),
                    bookId = entry.optString("bookId").orEmpty(),
                    timestamp = entry.optLong("ts", 0L)
                )
            }
            stamps
        }.getOrElse {
            Timber.tag(TAG).w(it, "Failed to read sidecar index for $sourceFolderUri")
            emptyMap()
        }
    }

    private fun writeSidecarIndex(
        context: Context,
        sourceFolderUri: Uri,
        stamps: Map<String, SidecarStamp>
    ) {
        runCatching {
            val file = sidecarIndexFile(context, sourceFolderUri)
            val dir = file.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val entries = JSONObject()
            stamps.forEach { (name, stamp) ->
                entries.put(
                    name,
                    JSONObject()
                        .put("mtime", stamp.lastModified)
                        .put("size", stamp.size)
                        .put("bookId", stamp.bookId)
                        .put("ts", stamp.timestamp)
                )
            }
            val payload = JSONObject().put("version", 1).put("entries", entries).toString()
            val temp = File(dir, "${file.name}.tmp")
            temp.writeText(payload)
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure { Timber.tag(TAG).w(it, "Failed to persist sidecar index for $sourceFolderUri") }
    }

    /**
     * Metadata sidecars of a SAF folder.
     *
     * [knownLocalTimestamps] maps a book id to the folder-modified timestamp the caller already has
     * on its own row. Unchanged sidecars are only skipped when they cannot beat that value; a book
     * the caller does not know yet (new import, wiped row) is always read, so this stays exactly the
     * set of records the folder sync would act on rather than merely "files that changed".
     */
    suspend fun getAllFolderMetadata(
        context: Context,
        sourceFolderUri: Uri,
        knownLocalTimestamps: Map<String, Long> = emptyMap()
    ): Map<String, FolderBookMetadata> = withContext(Dispatchers.IO) {
        val start = ReaderPerfLog.nowNanos()
        val finalResults = mutableMapOf<String, FolderBookMetadata>()

        try {
            val allFiles = querySyncSubfolderFiles(context, sourceFolderUri)
            val candidates = allFiles.filter { isMetadataSidecarCandidateName(it.name) }
            val cachedStamps = sidecarIndexMutex.withLock { readSidecarIndex(context, sourceFolderUri) }
            val nextStamps = HashMap<String, SidecarStamp>(candidates.size)
            val changed = ArrayList<SyncFileEntry>()
            candidates.forEach { entry ->
                // Providers that report neither size nor last-modified cannot prove a file is
                // unchanged, so such files are always re-read and never cached.
                val stampUsable = entry.lastModified > 0L || entry.size > 0L
                val cached = cachedStamps[entry.name]
                val cacheHit = stampUsable && cached != null &&
                    cached.bookId.isNotBlank() && cached.matches(entry)
                if (cacheHit && cached != null) {
                    nextStamps[entry.name] = cached
                    val knownTimestamp = knownLocalTimestamps[cached.bookId]
                    val alreadyApplied = knownTimestamp != null && cached.timestamp <= knownTimestamp
                    if (!alreadyApplied) changed.add(entry)
                } else {
                    changed.add(entry)
                }
            }

            val groupedMetadata = changed
                .asSequence()
                .mapNotNull { file ->
                    val jsonString = readDocumentText(context.contentResolver, file.uri)
                        ?: return@mapNotNull null
                    runCatching { FolderBookMetadata.fromJsonString(jsonString) }
                        .onFailure { Timber.tag(TAG).w(it, "Failed to parse metadata sidecar: " + file.name) }
                        .getOrNull()
                        ?.also { metadata ->
                            if (file.lastModified > 0L || file.size > 0L) {
                                nextStamps[file.name] = SidecarStamp(
                                    lastModified = file.lastModified,
                                    size = file.size,
                                    bookId = metadata.bookId,
                                    timestamp = metadata.lastModifiedTimestamp,
                                )
                            }
                        }
                }
                .groupBy { it.bookId }

            groupedMetadata.forEach { (bookId, metadataRecords) ->
                metadataRecords.maxByOrNull { it.lastModifiedTimestamp }?.let { winner ->
                    finalResults[bookId] = winner
                }
            }

            sidecarIndexMutex.withLock { writeSidecarIndex(context, sourceFolderUri, nextStamps) }

            Timber.tag(TAG).d(
                "getAllFolderMetadata: read " + changed.size + "/" + candidates.size +
                    " sidecars, " + finalResults.size + " books"
            )
            ReaderPerfLog.d(
                "LocalSync metadata read files=" + candidates.size + " read=" + changed.size +
                    " records=" + finalResults.size + " elapsed=" + ReaderPerfLog.elapsedMs(start) + "ms"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error scanning sync data folder for metadata")
            ReaderPerfLog.w("LocalSync metadata read failed uri=" + sourceFolderUri)
        }
        return@withContext finalResults
    }

    /** Read metadata sidecars from a DOWNLOAD_ALL app-private materialization. */
    suspend fun getAllFolderMetadataFromAppStorage(
        root: File,
    ): Map<String, FolderBookMetadata> = withContext(Dispatchers.IO) {
        val syncDir = File(root, SYNC_SUBFOLDER_NAME)
        if (!syncDir.isDirectory) return@withContext emptyMap()
        val grouped = mutableMapOf<String, MutableList<FolderBookMetadata>>()
        val files = syncDir.listFiles().orEmpty()
        files
            .asSequence()
            .filter { it.isFile && it.name?.let(::isMetadataSidecarCandidateName) == true }
            .forEach { file ->
                runCatching {
                    FolderBookMetadata.fromJsonString(file.readText())
                }.onSuccess { metadata ->
                    grouped.getOrPut(metadata.bookId) { mutableListOf() }.add(metadata)
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to parse app-storage metadata sidecar: ${file.name}")
                }
            }
        grouped.mapValues { (_, records) -> records.maxByOrNull { it.lastModifiedTimestamp }!! }
    }

    /**
     * Migrate a metadata sidecar inside a completed app-private materialization.
     * The normal SAF writer cannot be used for a file:// root, so keep the same
     * newer-sidecar guard and write/validate/replace sequence locally.
     */
    suspend fun saveMetadataToAppStorage(
        root: File,
        metadata: FolderBookMetadata,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val syncDir = File(root, SYNC_SUBFOLDER_NAME)
            if (!syncDir.isDirectory && !syncDir.mkdirs()) return@withContext false
            val candidates = syncDir.listFiles().orEmpty().filter { file ->
                file.isFile && isMetadataCandidateForBook(file.name, metadata.bookId)
            }
            val current = candidates.mapNotNull { file ->
                runCatching { FolderBookMetadata.fromJsonString(file.readText()) }.getOrNull()
            }.maxByOrNull { it.lastModifiedTimestamp }
            if (current != null && current.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
                return@withContext true
            }

            val target = File(syncDir, localFolderSyncMetadataFileName(metadata.bookId))
            val temp = File(syncDir, uniqueFolderSyncTempName(localFolderSyncMetadataTempFileName(metadata.bookId)))
            FileOutputStream(temp).use { output ->
                output.write(metadata.toJsonString().toByteArray(Charsets.UTF_8))
                output.flush()
                runCatching { output.fd.sync() }
            }
            val backup = if (target.exists()) {
                File(syncDir, "${target.name}.sync-backup-${System.nanoTime()}")
                    .takeIf { target.renameTo(it) }
            } else {
                null
            }
            if (!temp.renameTo(target)) {
                backup?.renameTo(target)
                temp.delete()
                return@withContext false
            }
            val installed = runCatching { FolderBookMetadata.fromJsonString(target.readText()) }.getOrNull()
            if (installed?.bookId != metadata.bookId) {
                target.delete()
                backup?.renameTo(target)
                return@withContext false
            }
            candidates.filterNot { it.absolutePath == target.absolutePath }.forEach { it.delete() }
            backup?.delete()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to migrate app-storage metadata sidecar for ${metadata.bookId}")
            false
        }
    }

    /** Read annotation sidecars from a DOWNLOAD_ALL app-private materialization. */
    suspend fun preloadAnnotationSidecarsFromAppStorage(
        root: File,
    ): Map<String, Pair<Long, String>> = withContext(Dispatchers.IO) {
        val syncDir = File(root, SYNC_SUBFOLDER_NAME)
        if (!syncDir.isDirectory) return@withContext emptyMap()
        val parsedSidecars = syncDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.name?.let(::isAnnotationSidecarCandidateName) == true }
            .mapNotNull { file ->
                parseAnnotationSidecarFile(
                    file = file,
                    fallbackBookId = extractLegacyAnnotationBookId(file.name),
                )
            }
            .groupBy { it.bookId }
        parsedSidecars.mapNotNull { (bookId, sidecars) ->
            mergeParsedAnnotationSidecars(sidecars)?.let { merged ->
                bookId to (merged.timestamp to merged.data)
            }
        }.toMap()
    }

    suspend fun getAnnotationSidecarFromAppStorage(
        root: File,
        bookId: String,
    ): Pair<Long, String>? = preloadAnnotationSidecarsFromAppStorage(root)[bookId]

    /** Atomic/validated annotation-sidecar migration for app-private roots. */
    suspend fun saveAnnotationSidecarToAppStorage(
        root: File,
        bookId: String,
        jsonPayload: String,
        timestamp: Long,
    ): Boolean = annotationSidecarWriteMutex.withLock { withContext(Dispatchers.IO) {
        try {
            val syncDir = File(root, SYNC_SUBFOLDER_NAME)
            if (!syncDir.isDirectory && !syncDir.mkdirs()) return@withContext false
            val candidates = syncDir.listFiles().orEmpty().filter { file ->
                file.isFile && isAnnotationCandidateForBook(file.name, bookId)
            }
            var hasUnreadableCandidate = false
            val parsed = candidates.mapNotNull { file ->
                parseAnnotationSidecarFile(file, extractLegacyAnnotationBookId(file.name))
                    ?: run {
                        hasUnreadableCandidate = true
                        null
                    }
            }
            if (hasUnreadableCandidate) return@withContext false
            val current = mergeParsedAnnotationSidecars(parsed)
            val mergedPayload = current?.let { remote ->
                SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
                    localDataJson = jsonPayload,
                    remoteDataJson = remote.data,
                    preferRemoteOnConflict = remote.timestamp > timestamp,
                )
            } ?: jsonPayload
            val mergedTimestamp = maxOf(timestamp, current?.timestamp ?: 0L)
            val wrapper = JSONObject().apply {
                put("version", 1)
                put("bookId", bookId)
                put("timestamp", mergedTimestamp)
                put("data", JSONObject(mergedPayload))
            }
            val target = File(syncDir, localFolderSyncAnnotationFileName(bookId))
            val temp = File(syncDir, uniqueFolderSyncTempName(localFolderSyncAnnotationTempFileName(bookId)))
            FileOutputStream(temp).use { output ->
                output.write(wrapper.toString().toByteArray(Charsets.UTF_8))
                output.flush()
                runCatching { output.fd.sync() }
            }
            val backup = if (target.exists()) {
                File(syncDir, "${target.name}.sync-backup-${System.nanoTime()}")
                    .takeIf { target.renameTo(it) }
            } else {
                null
            }
            if (!temp.renameTo(target)) {
                backup?.renameTo(target)
                temp.delete()
                return@withContext false
            }
            if (parseAnnotationSidecarFile(target, bookId) == null) {
                target.delete()
                backup?.renameTo(target)
                return@withContext false
            }
            candidates.filterNot { it.absolutePath == target.absolutePath }.forEach { it.delete() }
            backup?.delete()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("FolderAnnotationSync").w(error, "Failed to migrate app-storage annotation sidecar for $bookId")
            false
        }
    } }

    suspend fun deleteBookSidecarsFromAppStorage(
        root: File,
        bookId: String,
    ) = withContext(Dispatchers.IO) {
        val syncDir = File(root, SYNC_SUBFOLDER_NAME)
        if (!syncDir.isDirectory) return@withContext
        syncDir.listFiles().orEmpty()
            .filter { file ->
                file.isFile && (
                    isMetadataCandidateForBook(file.name, bookId) ||
                        isAnnotationCandidateForBook(file.name, bookId)
                    )
            }
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun parseAnnotationSidecarFile(
        file: File,
        fallbackBookId: String?,
    ): ParsedAnnotationSidecar? {
        return try {
            val json = JSONObject(file.readText())
            val bookId = json.optString("bookId").takeIf { it.isNotBlank() }
                ?: fallbackBookId
                ?: return null
            val data = json.optJSONObject("data")?.toString() ?: return null
            ParsedAnnotationSidecar(
                name = file.name,
                bookId = bookId,
                timestamp = json.optLong("timestamp", 0L),
                data = data,
            )
        } catch (error: Exception) {
            Timber.tag("FolderAnnotationSync").w(error, "Error parsing app-storage annotation sidecar: ${file.name}")
            null
        }
    }

    private fun isMetadataCandidateForBook(name: String?, bookId: String): Boolean {
        val candidateName = name ?: return false
        if (!isMetadataSidecarCandidateName(candidateName)) return false
        val normalized = candidateName.normalizedSidecarName()
        val hashedStem = localFolderSyncSidecarStem(bookId)
        return normalized.matchesJsonSidecarStem(hashedStem) ||
            normalized.matchesJsonSidecarStem(bookId)
    }

}
