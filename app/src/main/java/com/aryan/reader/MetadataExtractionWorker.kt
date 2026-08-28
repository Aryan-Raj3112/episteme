// MetadataExtractionWorker.kt
package com.aryan.reader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.CloudFolderSyncRepository
import com.aryan.reader.pdf.PdfiumCoreProvider
import com.aryan.reader.pdf.PdfiumEngineProvider
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.parseSharedDocumentXmlMetadata
import com.aryan.reader.shared.sharedDocumentMetadataArchivePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

internal const val MAX_METADATA_EXTRACTION_RETRY_ATTEMPTS = 3
internal const val METADATA_EXTRACTION_RETRY_BACKOFF_SECONDS = 30L

class MetadataExtractionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)
    private val contentThumbnailGenerator = ContentThumbnailGenerator(appContext)
    private val authRepository = AuthRepository(appContext)

    companion object {
        const val WORK_NAME = "MetadataExtractionWorker"
        const val KEY_SOURCE_FOLDER_URI = "key_source_folder_uri"
        const val KEY_CLOUD_ROOT_ID = "key_cloud_root_id"
        private const val METADATA_DB_BATCH_SIZE = 100
        private const val METADATA_WORKER_BOOK_BATCH_SIZE = 300
        private const val METADATA_PROGRESS_LOG_EVERY = 250
        private val TEXT_METADATA_TYPES = setOf(
            FileType.PDF,
            FileType.EPUB,
            FileType.MOBI,
            FileType.FB2,
            FileType.ODT,
            FileType.FODT,
            FileType.DOCX
        )
        private val CONTENT_THUMBNAIL_TYPES = SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID) - FileType.EPUB
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workerStart = ReaderPerfLog.nowNanos()
        val sourceFolderUri = inputData.getString(KEY_SOURCE_FOLDER_URI)
        val requestedRootId = inputData.getString(KEY_CLOUD_ROOT_ID)?.trim()
            ?.takeIf { it.isNotBlank() }
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val linkedFolders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null),
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null)
        )
        // App-managed KEEP_OFFLINE folders deliberately live outside the
        // legacy SAF preference. Include only the records for the currently
        // signed-in account so a stale Room row from another account can
        // never make an arbitrary app-private path eligible for extraction.
        val accountId = authRepository.getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val appManagedFolders = accountId?.let { id ->
            CloudFolderAppStoragePrefs.load(appContext, id)
                .map { it.toSyncedFolder(appContext.filesDir) }
        }.orEmpty()
        val enabledFolderUris = metadataExtractionEnabledFolderUris(linkedFolders + appManagedFolders)
        val discoveredRootId = accountId?.let { id ->
            sourceFolderUri?.let { folderUri ->
                CloudFolderAppStoragePrefs.rootIdForUri(
                    context = appContext,
                    accountId = id,
                    uriString = folderUri,
                ) ?: try {
                    CloudFolderSyncRepository(appContext, id)
                        .findBindingForLocalUri(folderUri)
                        ?.rootId
                } catch (_: Exception) {
                    null
                }
            }
        }
        val resolvedRootId = requestedRootId ?: discoveredRootId
        val operation = cloudFolderOperationId(
            "metadata-extraction",
            accountId.orEmpty(),
            resolvedRootId ?: sourceFolderUri.orEmpty(),
            runAttemptCount,
        )
        val correlation = cloudFolderSyncCorrelationId(
            "metadata-extraction",
            accountId.orEmpty(),
            resolvedRootId ?: sourceFolderUri.orEmpty(),
        )
        val traceFields = "operation=$operation correlation=$correlation " +
            "account=${cloudFolderSafeId(accountId)} root=${cloudFolderSafeId(resolvedRootId)} " +
            "folder=${cloudFolderSafeUri(sourceFolderUri?.toUri())}"
        cloudFolderLogD("event=metadata_extract_worker_start $traceFields attempt=$runAttemptCount")

        if (requestedRootId != null && discoveredRootId != null && requestedRootId != discoveredRootId) {
            cloudFolderLogW(
                "event=metadata_extract_gate $traceFields result=skipped reason=root_mismatch",
            )
            return@withContext Result.success()
        }

        if (enabledFolderUris.isEmpty()) {
            cloudFolderLogD(
                "event=metadata_extract_gate $traceFields result=skipped reason=no_enabled_folders",
            )
            ReaderPerfLog.d("MetadataWorker skipped: no linked folders with sync enabled")
            return@withContext Result.success()
        }
        if (!sourceFolderUri.isNullOrBlank() && sourceFolderUri !in enabledFolderUris) {
            cloudFolderLogD(
                "event=metadata_extract_gate $traceFields result=skipped reason=folder_disabled",
            )
            ReaderPerfLog.d("MetadataWorker skipped: folder sync disabled folder=$sourceFolderUri")
            return@withContext Result.success()
        }

        try {
            val filesToProcess = recentFilesRepository.getFolderBooksNeedingTextMetadata(
                sourceFolderUri = sourceFolderUri,
                limit = METADATA_WORKER_BOOK_BATCH_SIZE
            ).filter { item ->
                item.sourceFolderUri != null && item.sourceFolderUri in enabledFolderUris
            }

            if (filesToProcess.isEmpty()) {
                cloudFolderLogD(
                    "event=metadata_extract_start $traceFields result=empty",
                )
                ReaderPerfLog.d("MetadataWorker skipped: no metadata pending folder=${sourceFolderUri ?: "ALL"}")
                return@withContext Result.success()
            }

            ReaderPerfLog.i(
                "MetadataWorker start mode=metadata books=${filesToProcess.size} " +
                    "batchLimit=$METADATA_WORKER_BOOK_BATCH_SIZE folder=${sourceFolderUri ?: "ALL"}"
            )
            cloudFolderLogI(
                "event=metadata_extract_start $traceFields candidates=${filesToProcess.size}",
            )

            val pendingUpdates = mutableListOf<RecentFileItem>()
            var processed = 0
            var updated = 0
            var coversUpdated = 0
            var failed = 0

            suspend fun flushUpdates() {
                if (pendingUpdates.isEmpty()) return
                val flushStart = ReaderPerfLog.nowNanos()
                recentFilesRepository.updateExtractedMetadata(pendingUpdates)
                ReaderPerfLog.d(
                    "MetadataWorker DB flush rows=${pendingUpdates.size} elapsed=${ReaderPerfLog.elapsedMs(flushStart)}ms"
                )
                pendingUpdates.clear()
            }

            filesToProcess.forEach { item ->
                if (isStopped) return@forEach

                if (item.sourceFolderUri == null) return@forEach

                var needsTextMetadata = item.type in TEXT_METADATA_TYPES && !item.folderTextMetadataParsed
                var needsEmbeddedCover = false
                var needsContentThumbnail = false

                try {
                    val uri = resolveVerifiedSourceUri(item, accountId, enabledFolderUris)
                        ?: run {
                            failed++
                            cloudFolderLogW(
                                "event=metadata_extract_item result=skipped reason=source_unavailable " +
                                    "book=${cloudFolderSafeId(item.bookId)} " +
                                    "folder=${cloudFolderSafeUri(item.sourceFolderUri?.toUri())}"
                            )
                            return@forEach
                        }
                    if (!isReadableSource(uri)) {
                        failed++
                        cloudFolderLogW(
                            "event=metadata_extract_item result=skipped reason=source_unavailable " +
                                "book=${cloudFolderSafeId(item.bookId)} " +
                                "folder=${cloudFolderSafeUri(item.sourceFolderUri?.toUri())}"
                        )
                        return@forEach
                    }
                    val fileSize = item.fileSize.takeIf { it > 0L } ?: queryFileSize(uri)
                    val existingCoverIsAvailable = item.coverImagePath?.let { File(it).isFile } == true
                    needsEmbeddedCover = EmbeddedEbookMetadataExtractor.canExtractEmbeddedCover(item.type) &&
                        !item.folderCoverMetadataParsed &&
                        !existingCoverIsAvailable
                    needsContentThumbnail = item.type in CONTENT_THUMBNAIL_TYPES &&
                        !item.folderCoverMetadataParsed &&
                        !existingCoverIsAvailable
                    cloudFolderLogD(
                        "event=metadata_extract_item_start $traceFields " +
                            "book=${cloudFolderSafeId(item.bookId)} type=${item.type.name} size=$fileSize " +
                            "textPending=$needsTextMetadata embeddedCoverPending=$needsEmbeddedCover " +
                            "thumbnailPending=$needsContentThumbnail",
                    )

                    val metadata = when (item.type) {
                        FileType.EPUB,
                        FileType.MOBI,
                        FileType.FB2 -> {
                            if (needsTextMetadata || needsEmbeddedCover) {
                                EmbeddedEbookMetadataExtractor.extract(
                                    type = item.type,
                                    displayName = item.displayName,
                                    openStream = { openInputStream(uri) },
                                    extractCover = needsEmbeddedCover
                                ).toTextMetadata()
                            } else {
                                TextMetadata()
                            }
                        }
                        FileType.PDF -> parsePdfTextMetadata(uri)
                        FileType.ODT -> parseZipTextMetadata(uri, requireNotNull(sharedDocumentMetadataArchivePath(item.type)))
                        FileType.FODT -> parseFlatXmlTextMetadata(uri)
                        FileType.DOCX,
                        FileType.PPTX -> parseZipTextMetadata(uri, requireNotNull(sharedDocumentMetadataArchivePath(item.type)))
                        else -> TextMetadata()
                    }

                    val title = sanitizeTitle(metadata.title)
                    val author = sanitizeAuthor(metadata.author)
                    val description = metadata.description?.trim()?.takeIf { it.isNotBlank() }
                    val seriesName = metadata.seriesName?.trim()?.takeIf { it.isNotBlank() }
                    val seriesIndex = metadata.seriesIndex?.takeIf { it > 0.0 }
                    val sizeChanged = fileSize > 0L && fileSize != item.fileSize
                    val titleChanged = title != null && title != item.title
                    val authorChanged = author != null && author != item.author
                    val descriptionChanged = description != null && description != item.description
                    val seriesChanged = seriesName != null && seriesName != item.seriesName
                    val seriesIndexChanged = seriesIndex != null && seriesIndex != item.seriesIndex
                    val coverPath = if (needsEmbeddedCover) {
                        metadata.cover?.let { cover ->
                            recentFilesRepository.saveEmbeddedCoverToCache(cover.bytes, uri, cover.extension)
                        }
                    } else {
                        null
                    } ?: if (needsContentThumbnail) {
                        // Use the canonical, account/root-validated URI here
                        // as well. Passing the original Room URI would allow
                        // a symlink/alternate spelling to bypass the same
                        // source validation used by text extraction.
                        contentThumbnailGenerator.generate(item.copy(uriString = uri.toString()))?.let { thumbnail ->
                            try {
                                recentFilesRepository.saveCoverToCache(thumbnail, uri)
                            } finally {
                                thumbnail.recycle()
                            }
                        }
                    } else {
                        null
                    }
                    val coverChanged = coverPath != null && coverPath != item.coverImagePath
                    val coverMetadataParsed = item.folderCoverMetadataParsed || needsEmbeddedCover || needsContentThumbnail
                    val textMetadataParsed = item.folderTextMetadataParsed || needsTextMetadata

                    if (needsTextMetadata || needsEmbeddedCover || needsContentThumbnail || sizeChanged || titleChanged || authorChanged || descriptionChanged || seriesChanged || seriesIndexChanged || coverChanged) {
                        pendingUpdates.add(
                            item.copy(
                                coverImagePath = coverPath ?: item.coverImagePath,
                                title = title ?: item.title ?: item.displayName,
                                author = author ?: item.author,
                                description = description ?: item.description,
                                seriesName = seriesName ?: item.seriesName,
                                seriesIndex = seriesIndex ?: item.seriesIndex,
                                fileSize = if (fileSize > 0L) fileSize else item.fileSize,
                                folderTextMetadataParsed = textMetadataParsed,
                                folderCoverMetadataParsed = coverMetadataParsed
                            )
                        )
                        if (sizeChanged || titleChanged || authorChanged || descriptionChanged || seriesChanged || seriesIndexChanged || coverChanged) {
                            updated++
                        }
                        if (coverChanged) coversUpdated++
                        if (pendingUpdates.size >= METADATA_DB_BATCH_SIZE) {
                            flushUpdates()
                        }
                    }

                    processed++
                    if (processed % METADATA_PROGRESS_LOG_EVERY == 0) {
                        ReaderPerfLog.d(
                            "MetadataWorker progress mode=metadata processed=$processed updated=$updated covers=$coversUpdated failed=$failed"
                        )
                    }
                    cloudFolderLogD(
                        "event=metadata_extract_item_end $traceFields " +
                            "result=success book=${cloudFolderSafeId(item.bookId)} " +
                            "text=${needsTextMetadata} cover=${needsEmbeddedCover || needsContentThumbnail} " +
                            "coverUpdated=${coverChanged} " +
                            "roomUpdate=${needsTextMetadata || needsEmbeddedCover || needsContentThumbnail || sizeChanged || titleChanged || authorChanged || descriptionChanged || seriesChanged || seriesIndexChanged || coverChanged}",
                    )
                } catch (e: Exception) {
                    failed++
                    cloudFolderLogW(
                        "event=metadata_extract_item_end $traceFields result=failure " +
                            "book=${cloudFolderSafeId(item.bookId)} " +
                            "errorClass=${cloudFolderErrorClass(e)} errorStatus=${cloudFolderErrorStatus(e)}",
                    )
                    Timber.tag("MetadataWorker").e(e, "Failed metadata extraction for ${item.displayName}")
                    // Leave parsed flags unchanged so a transient read/parser failure can retry.
                }
            }

            flushUpdates()

            val nextBatchEnqueued = !isStopped &&
                failed == 0 &&
                filesToProcess.size >= METADATA_WORKER_BOOK_BATCH_SIZE &&
                recentFilesRepository.hasFolderBooksNeedingTextMetadata(sourceFolderUri)
            if (nextBatchEnqueued) {
                enqueueNextBatch(sourceFolderUri, resolvedRootId)
            }

            ReaderPerfLog.i(
                "MetadataWorker finished mode=metadata processed=$processed updated=$updated covers=$coversUpdated failed=$failed " +
                    "nextBatch=$nextBatchEnqueued elapsed=${ReaderPerfLog.elapsedMs(workerStart)}ms folder=${sourceFolderUri ?: "ALL"}"
            )
            cloudFolderLogI(
                "event=metadata_extract_end $traceFields " +
                    "result=${if (failed == 0) "success" else "partial"} processed=$processed " +
                    "updated=$updated covers=$coversUpdated failed=$failed nextBatch=$nextBatchEnqueued " +
                    "durationMs=${ReaderPerfLog.elapsedMs(workerStart)}",
            )

            return@withContext if (shouldRetryMetadataExtraction(failed, runAttemptCount)) {
                ReaderPerfLog.w(
                    "MetadataWorker scheduling retry attempt=${runAttemptCount + 1} failed=$failed " +
                        "folder=${sourceFolderUri ?: "ALL"}"
                )
                cloudFolderLogW(
                    "event=metadata_extract_retry $traceFields attempt=${runAttemptCount + 1} failed=$failed",
                )
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            cloudFolderLogW(
                "event=metadata_extract_end $traceFields result=failure " +
                    "errorClass=${cloudFolderErrorClass(e)} errorStatus=${cloudFolderErrorStatus(e)}",
            )
            Timber.tag("MetadataWorker").e(e, "Metadata extraction failed")
            return@withContext Result.failure()
        }
    }

    /**
     * Resolve a folder book only from a currently linked folder. App-private
     * files additionally have to be descendants of the account-scoped root;
     * this prevents a stale/malformed Room URI from making extraction read an
     * unrelated file after an account switch.
     */
    private fun resolveVerifiedSourceUri(
        item: RecentFileItem,
        accountId: String?,
        enabledFolderUris: Set<String>,
    ): Uri? {
        val sourceFolderUri = item.sourceFolderUri?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (accountId != null) {
            val activeAccount = authRepository.getSignedInUser()?.uid
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (activeAccount != accountId) return null
        }
        if (sourceFolderUri !in enabledFolderUris) return null
        val itemUri = item.uriString?.trim()?.takeIf { it.isNotBlank() }?.toUri() ?: return null
        val sourceUri = sourceFolderUri.toUri()
        if (!isUriInLinkedFolderTree(sourceUri, itemUri)) return null
        if (!itemUri.scheme.equals("file", ignoreCase = true)) {
            return itemUri
        }

        val managedFile = if (accountId != null) {
            CloudFolderAppStoragePrefs.resolveManagedFile(
                context = appContext,
                accountId = accountId,
                sourceFolderUri = sourceFolderUri,
                fileUriString = itemUri.toString(),
            )
        } else {
            null
        }
        val file = managedFile ?: run {
            val sourceRoot = fileFromUri(sourceUri) ?: return null
            val candidate = fileFromUri(itemUri) ?: return null
            val root = runCatching { sourceRoot.canonicalFile }.getOrNull() ?: return null
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
            val prefix = root.path + File.separator
            canonical.takeIf { it.path.startsWith(prefix) }
        } ?: return null
        if (!file.isFile) return null
        return Uri.fromFile(file)
    }

    private fun isUriInLinkedFolderTree(sourceFolderUri: Uri, itemUri: Uri): Boolean {
        val sourceScheme = sourceFolderUri.scheme?.lowercase()
        val itemScheme = itemUri.scheme?.lowercase()
        if (sourceScheme == "file" || itemScheme == "file") {
            return sourceScheme == "file" && itemScheme == "file"
        }
        if (sourceScheme == "content" || itemScheme == "content") {
            if (sourceScheme != "content" || itemScheme != "content") return false
            if (sourceFolderUri.authority != itemUri.authority) return false
            val sourceTreeId = runCatching {
                DocumentsContract.getTreeDocumentId(sourceFolderUri)
            }.getOrNull()
            val itemTreeId = runCatching {
                DocumentsContract.getTreeDocumentId(itemUri)
            }.getOrNull()
            // A document URI created from the selected tree retains the tree
            // ID. If either provider omits it, authority + persisted grant
            // remain the compatibility fallback; never reject valid older
            // provider URI shapes solely because the tree ID is unavailable.
            return sourceTreeId == null || itemTreeId == null || sourceTreeId == itemTreeId
        }
        return sourceScheme == itemScheme
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            fileFromUri(uri)?.takeIf(File::isFile)?.inputStream()
        } else {
            appContext.contentResolver.openInputStream(uri)
        }
    }

    private fun openFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            fileFromUri(uri)?.takeIf(File::isFile)?.let {
                ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        } else {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    private fun isReadableSource(uri: Uri): Boolean {
        return runCatching {
            openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun fileFromUri(uri: Uri): File? {
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        runCatching { File(URI(uri.toString())) }.getOrNull()?.let { return it }
        uri.path?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val rawPath = uri.toString().removePrefix("file:").takeIf { it.isNotBlank() } ?: return null
        val normalizedPath = when {
            rawPath.startsWith("///") -> rawPath.drop(3)
            rawPath.length > 2 && rawPath[0] == '/' && rawPath[2] == ':' -> rawPath.drop(1)
            else -> rawPath
        }
        return runCatching {
            File(URLDecoder.decode(normalizedPath, Charsets.UTF_8.name()))
        }.getOrNull()
    }

    private fun enqueueNextBatch(sourceFolderUri: String?, rootId: String?) {
        val data = androidx.work.Data.Builder().apply {
            if (!sourceFolderUri.isNullOrBlank()) {
                putString(KEY_SOURCE_FOLDER_URI, sourceFolderUri)
            }
            if (!rootId.isNullOrBlank()) {
                putString(KEY_CLOUD_ROOT_ID, rootId)
            }
        }.build()
        val request = OneTimeWorkRequestBuilder<MetadataExtractionWorker>()
            .setInputData(data)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                METADATA_EXTRACTION_RETRY_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        cloudFolderLogD(
            "event=metadata_extract_next_batch folder=${cloudFolderSafeUri(sourceFolderUri?.toUri())}",
        )
        ReaderPerfLog.d("MetadataWorker enqueued next metadata batch folder=${sourceFolderUri ?: "ALL"}")
    }

    private fun queryFileSize(uri: android.net.Uri): Long {
        return try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                fileFromUri(uri)?.takeIf(File::isFile)?.length() ?: 0L
            } else {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    } else {
                        0L
                    }
                } ?: 0L
            }
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Failed to query file size for $uri")
            0L
        }
    }

    private fun parseZipTextMetadata(uri: android.net.Uri, targetEntryName: String): TextMetadata {
        openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == targetEntryName) {
                        val xml = zip.readTextEntry()
                        return parseXmlTextMetadata(xml)
                    }
                    zip.closeEntry()
                }
            }
        }
        return TextMetadata()
    }

    private fun parseFlatXmlTextMetadata(uri: android.net.Uri): TextMetadata {
        val xml = openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: return TextMetadata()
        return parseXmlTextMetadata(xml)
    }

    private suspend fun parsePdfTextMetadata(uri: android.net.Uri): TextMetadata {
        return try {
            openFileDescriptor(uri)?.use { pfd ->
                PdfiumEngineProvider.withPdfium {
                    PdfiumCoreProvider.core.newDocument(pfd).use { pdfDocument ->
                        val meta = pdfDocument.getDocumentMeta()
                        TextMetadata(title = meta.title, author = meta.author)
                    }
                }
            } ?: TextMetadata()
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Failed to extract PDF text metadata")
            TextMetadata()
        }
    }

    private fun parseXmlTextMetadata(xml: String): TextMetadata {
        val metadata = parseSharedDocumentXmlMetadata(xml)
        return TextMetadata(title = metadata.title, author = metadata.author)
    }

    private fun ZipInputStream.readTextEntry(): String {
        return String(readBytes(), Charsets.UTF_8)
    }

    private fun sanitizeTitle(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("content", ignoreCase = true) }
    }

    private fun sanitizeAuthor(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    }

    private fun EmbeddedEbookMetadata.toTextMetadata(): TextMetadata {
        return TextMetadata(
            title = title,
            author = author,
            description = description,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            cover = cover
        )
    }

    private data class TextMetadata(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val seriesName: String? = null,
        val seriesIndex: Double? = null,
        val cover: EmbeddedEbookCover? = null
    )
}

internal fun shouldRetryMetadataExtraction(failedCount: Int, runAttemptCount: Int): Boolean =
    failedCount > 0 && runAttemptCount < MAX_METADATA_EXTRACTION_RETRY_ATTEMPTS

/**
 * Return the folder roots that are currently eligible for local metadata
 * extraction. Keep this as a small pure helper so the worker's account/folder
 * gate remains easy to verify without constructing WorkManager.
 */
internal fun metadataExtractionEnabledFolderUris(
    folders: Iterable<SyncedFolder>,
): Set<String> = folders.asSequence()
    .filter { it.localSyncEnabled }
    .mapNotNull { it.uriString.trim().takeIf(String::isNotBlank) }
    .toSet()
