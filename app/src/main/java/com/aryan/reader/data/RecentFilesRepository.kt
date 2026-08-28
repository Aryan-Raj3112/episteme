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
// RecentFilesRepository.kt
package com.aryan.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.FileType
import com.aryan.reader.ReaderPerfLog
import com.aryan.reader.SyncedFolderPrefs
import com.aryan.reader.cloudSyncPreview
import com.aryan.reader.cloudSyncTraceSummary
import com.aryan.reader.logCloudAnnotationSyncTrace
import com.aryan.reader.logCloudSyncTrace
import com.aryan.reader.scaledToCanvasLimit
import timber.log.Timber
import com.aryan.reader.BookImporter
import com.aryan.reader.AuthRepository
import com.aryan.reader.CloudFolderAppStoragePrefs
import com.aryan.reader.CloudFolderMetadataSyncScheduler
import com.aryan.reader.cloudFolderAppRootDirectory
import com.aryan.reader.cloudFolderLogD
import com.aryan.reader.cloudFolderSafeId
import com.aryan.reader.cloudFolderSafeUri
import com.aryan.reader.cloudFolderSidecarPayloadInfo
import com.aryan.reader.cloudFolderLogW
import com.aryan.reader.toLogFields
import com.aryan.reader.cloudSyncAnnotationSummary
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.pdf.PdfRichTextRepository
import com.aryan.reader.epub.ImportedFileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import org.json.JSONObject
import org.json.JSONArray
import androidx.room.withTransaction

private const val COVER_CACHE_DIR = "cover_cache"
private const val DIRECT_EMBEDDED_COVER_MAX_BYTES = 8L * 1024L * 1024L
private const val EMBEDDED_COVER_MAX_DIMENSION = 1200
private val EMBEDDED_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

class RecentFilesRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
) :
    AndroidBookStore,
    AndroidFolderMirrorStore,
    AndroidBookArtifactStore,
    AndroidLegacyMigrationStore {

    private val recentFileDao = database.recentFileDao()
    private val coverCacheDir = File(context.filesDir, COVER_CACHE_DIR)
    private val bookImporter = BookImporter(context)

    private val pdfAnnotationRepository = PdfAnnotationRepository(context)
    private val pdfRichTextRepository = PdfRichTextRepository(context)
    private val pageLayoutRepository = PageLayoutRepository(context)
    private val pdfTextBoxRepository = PdfTextBoxRepository(context)
    private val pdfHighlightRepository = com.aryan.reader.pdf.data.PdfHighlightRepository(context)
    private val pdfTextRepository by lazy { PdfTextRepository(context) }
    private val bookCacheDao by lazy { BookCacheDatabase.getDatabase(context).bookCacheDao() }
    private val authRepository by lazy { AuthRepository(context) }

    init {
        if (!coverCacheDir.exists()) {
            coverCacheDir.mkdirs()
        }
    }

    override fun getRecentFilesFlow(): Flow<List<RecentFileItem>> {
        return recentFileDao.getRecentFiles().map { entities ->
            entities.map { it.toRecentFileItem() }
        }
    }

    override suspend fun getFileByBookId(bookId: String): RecentFileItem? = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFileByBookId(bookId)?.toRecentFileItem()
    }

    override suspend fun getFileByUri(uriString: String): RecentFileItem? = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFileByUri(uriString)?.toRecentFileItem()
    }

    override suspend fun getFilesBySourceFolder(sourceFolderUri: String): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFilesBySourceFolder(sourceFolderUri).map { it.toRecentFileItem() }
    }

    override suspend fun getAllFilesForSync(): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getAllFiles().map { it.toRecentFileItem() }
    }

    override suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        // Keep the database cleanup together so a successful remote clear
        // cannot leave orphaned library metadata behind.
        database.withTransaction {
            recentFileDao.clearAll()
            database.customFontDao().clearAll()
            database.audiobookDao().clearAll()
            database.bookTtsListeningProgressDao().clearAll()
            database.shelfDao().clearAllBookShelfCrossRefs()
            database.shelfDao().clearAll()
            database.tagDao().clearAllBookTagCrossRefs()
            database.tagDao().clearAll()
            database.pendingFolderAnnotationExportDao().clearAll()
        }

        // These paths are private app-owned storage. In particular, do not
        // enumerate or delete arbitrary URI-backed files selected by users.
        context.filesDir.listFiles()?.forEach { file ->
            if (AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry(file.name, file.isDirectory)) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
        context.cacheDir.listFiles()?.forEach { file ->
            if (AndroidCloudCleanupPlan.shouldDeleteCacheEntry(file.name, file.isDirectory)) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }

        // Room-backed caches are separate databases and are not covered by
        // the AppDatabase transaction above.
        bookCacheDao.clearAllCache()
        pdfTextRepository.clearAllText()
        pdfTextBoxRepository.clearAll()
        pdfHighlightRepository.clearAll()

        // Keep the cache directory available for the next import.
        listOf("books", "custom_fonts", "audiobooks", COVER_CACHE_DIR, "derived").forEach { directoryName ->
            File(context.filesDir, directoryName).mkdirs()
        }
        Timber.d("Cleared all local book data, sidecars, and cover cache.")
    }

    override suspend fun addRecentFile(item: RecentFileItem) = withContext(Dispatchers.IO) {
        Timber.d("SyncDebug: addRecentFile called for bookId: ${item.bookId}")
        Timber.d("SyncDebug:   -> Incoming item: title='${item.title}', uri='${item.uriString}', isAvailable=${item.isAvailable}, isDeleted=${item.isDeleted}, isRecent=${item.isRecent}")
        val existingItem = recentFileDao.getFileByBookId(item.bookId)
        Timber.d("SyncDebug:   -> Existing item found: ${existingItem != null}")
        if (existingItem != null) {
            Timber.d("SyncDebug:   -> Existing item details: title='${existingItem.title}', uri='${existingItem.uriString}', isAvailable=${existingItem.isAvailable}, isRecent=${existingItem.isRecent}")
        }

        val entityToInsert = if (existingItem != null) {
            val folderFileChanged = item.sourceFolderUri != null &&
                existingItem.sourceFolderUri == item.sourceFolderUri &&
                ((item.fileSize > 0L && item.fileSize != existingItem.fileSize) ||
                    (item.fileContentModifiedTimestamp > 0L &&
                        item.fileContentModifiedTimestamp != existingItem.fileContentModifiedTimestamp))
            val embeddedMetadataFileChanged =
                (item.fileSize > 0L && existingItem.fileSize > 0L && item.fileSize != existingItem.fileSize) ||
                    (item.fileContentModifiedTimestamp > 0L &&
                        existingItem.fileContentModifiedTimestamp > 0L &&
                        item.fileContentModifiedTimestamp != existingItem.fileContentModifiedTimestamp)
            val keepExistingEmbeddedMetadata = item.type == FileType.EPUB &&
                existingItem.type == FileType.EPUB &&
                !embeddedMetadataFileChanged &&
                existingItem.hasEmbeddedMetadataChanges()

            val incomingEntity = item.toRecentFileEntity()
            val incomingReadingTimestamp = item.effectiveReadingPositionModifiedTimestamp()
            val existingReadingTimestamp = existingItem.readingPositionModifiedTimestamp.takeIf { it > 0L }
                ?: existingItem.lastModifiedTimestamp.takeIf {
                    existingItem.lastChapterIndex != null ||
                        existingItem.lastPage != null ||
                        !existingItem.lastPositionCfi.isNullOrBlank() ||
                        existingItem.locatorBlockIndex != null ||
                        existingItem.locatorCharOffset != null ||
                        (existingItem.progressPercentage ?: 0f) > 0f
                }
                ?: 0L
            val incomingReadingWins = incomingReadingTimestamp >= existingReadingTimestamp
            incomingEntity.copy(
                uriString = existingItem.uriString ?: item.uriString,
                dateAddedTimestamp = existingItem.dateAddedTimestamp.takeIf { it > 0L }
                    ?: item.dateAddedTimestamp.takeIf { it > 0L }
                    ?: item.timestamp,
                isAvailable = existingItem.isAvailable || item.isAvailable,
                coverImagePath = if (folderFileChanged) {
                    item.coverImagePath
                } else {
                    item.coverImagePath ?: existingItem.coverImagePath
                },
                title = if (folderFileChanged) {
                    item.title ?: item.displayName.substringBeforeLast('.', item.displayName)
                } else if (keepExistingEmbeddedMetadata) {
                    existingItem.title
                } else {
                    item.title ?: existingItem.title
                },
                author = if (folderFileChanged) {
                    item.author
                } else if (keepExistingEmbeddedMetadata) {
                    existingItem.author
                } else {
                    item.author ?: existingItem.author
                },
                lastChapterIndex = if (incomingReadingWins) item.lastChapterIndex ?: existingItem.lastChapterIndex else existingItem.lastChapterIndex,
                lastPage = if (incomingReadingWins) item.lastPage ?: existingItem.lastPage else existingItem.lastPage,
                lastPositionCfi = if (incomingReadingWins) item.lastPositionCfi ?: existingItem.lastPositionCfi else existingItem.lastPositionCfi,
                locatorBlockIndex = if (incomingReadingWins) item.locatorBlockIndex ?: existingItem.locatorBlockIndex else existingItem.locatorBlockIndex,
                locatorCharOffset = if (incomingReadingWins) item.locatorCharOffset ?: existingItem.locatorCharOffset else existingItem.locatorCharOffset,
                bookmarks = item.bookmarksJson ?: existingItem.bookmarks,
                progressPercentage = if (incomingReadingWins) item.progressPercentage ?: existingItem.progressPercentage else existingItem.progressPercentage,
                isRecent = item.isRecent,
                isDeleted = item.isDeleted,
                sourceFolderUri = item.sourceFolderUri ?: existingItem.sourceFolderUri,
                highlights = item.highlightsJson ?: existingItem.highlights,
                customName = when {
                    item.customName != null -> item.customName
                    item.lastModifiedTimestamp > existingItem.lastModifiedTimestamp -> null
                    else -> existingItem.customName
                },
                fileSize = if (item.fileSize > 0) item.fileSize else existingItem.fileSize,
                fileContentModifiedTimestamp = if (item.fileContentModifiedTimestamp > 0) item.fileContentModifiedTimestamp else existingItem.fileContentModifiedTimestamp,
                seriesName = if (folderFileChanged) {
                    item.seriesName
                } else if (keepExistingEmbeddedMetadata) {
                    existingItem.seriesName
                } else {
                    item.seriesName ?: existingItem.seriesName
                },
                seriesIndex = if (folderFileChanged) {
                    item.seriesIndex
                } else if (keepExistingEmbeddedMetadata) {
                    existingItem.seriesIndex
                } else {
                    item.seriesIndex ?: existingItem.seriesIndex
                },
                description = if (folderFileChanged) {
                    item.description
                } else if (keepExistingEmbeddedMetadata) {
                    existingItem.description
                } else {
                    item.description ?: existingItem.description
                },
                originalTitle = if (folderFileChanged) item.originalTitle ?: item.title else existingItem.originalTitle ?: item.originalTitle ?: item.title,
                originalAuthor = if (folderFileChanged) item.originalAuthor ?: item.author else existingItem.originalAuthor ?: item.originalAuthor ?: item.author,
                originalSeriesName = if (folderFileChanged) item.originalSeriesName ?: item.seriesName else existingItem.originalSeriesName ?: item.originalSeriesName ?: item.seriesName,
                originalSeriesIndex = if (folderFileChanged) item.originalSeriesIndex ?: item.seriesIndex else existingItem.originalSeriesIndex ?: item.originalSeriesIndex ?: item.seriesIndex,
                originalDescription = if (folderFileChanged) item.originalDescription ?: item.description else existingItem.originalDescription ?: item.originalDescription ?: item.description,
                folderTextMetadataParsed = if (folderFileChanged) {
                    item.folderTextMetadataParsed
                } else {
                    item.folderTextMetadataParsed || existingItem.folderTextMetadataParsed
                },
                folderCoverMetadataParsed = if (folderFileChanged) {
                    item.folderCoverMetadataParsed
                } else {
                    item.folderCoverMetadataParsed || existingItem.folderCoverMetadataParsed
                },
                readingPositionModifiedTimestamp = maxOf(incomingReadingTimestamp, existingReadingTimestamp)
            )
        } else {
            item.toRecentFileEntity()
        }

        Timber.d("SyncDebug:   -> Final entity to insert: uri='${entityToInsert.uriString}', isAvailable=${entityToInsert.isAvailable}, isDeleted=${entityToInsert.isDeleted}, isRecent=${entityToInsert.isRecent}")
        logCloudSyncTrace {
            "android.db.upsert book=${item.bookId} ${item.cloudSyncTraceSummary("incoming")} " +
                "existingTs=${existingItem?.lastModifiedTimestamp} existingPage=${existingItem?.lastPage} " +
                "existingReadTs=${existingItem?.readingPositionModifiedTimestamp} " +
                "existingChapter=${existingItem?.lastChapterIndex} finalTs=${entityToInsert.lastModifiedTimestamp} " +
                "finalReadTs=${entityToInsert.readingPositionModifiedTimestamp} " +
                "finalPage=${entityToInsert.lastPage} finalChapter=${entityToInsert.lastChapterIndex} " +
                "finalBlock=${entityToInsert.locatorBlockIndex} finalChar=${entityToInsert.locatorCharOffset} " +
                "finalProgress=${entityToInsert.progressPercentage} finalCfi=${entityToInsert.lastPositionCfi.cloudSyncPreview()} " +
                "finalBookmarks=${entityToInsert.bookmarks.cloudSyncAnnotationSummary()} " +
                "finalHighlights=${entityToInsert.highlights.cloudSyncAnnotationSummary()}"
        }
        recentFileDao.insertOrUpdateFile(entityToInsert)
        Timber.d("Added/Updated recent file in DB: ${item.displayName}")
    }

    private fun RecentFileEntity.hasEmbeddedMetadataChanges(): Boolean {
        val hasOriginalMetadata = listOf(originalTitle, originalAuthor, originalSeriesName, originalDescription)
            .any { !it.isNullOrBlank() } || originalSeriesIndex != null
        return (hasOriginalMetadata && (
            metadataValueChanged(title, originalTitle) ||
                metadataValueChanged(author, originalAuthor) ||
                metadataValueChanged(seriesName, originalSeriesName) ||
                seriesIndex != originalSeriesIndex ||
                metadataValueChanged(description, originalDescription)
            ))
    }

    private fun metadataValueChanged(current: String?, original: String?): Boolean {
        return current.orEmpty().trim() != original.orEmpty().trim()
    }

    override suspend fun updateUserEditableMetadata(
        bookId: String,
        metadata: BookMetadataEdit,
        fileSize: Long,
        fileContentModifiedTimestamp: Long,
        coverImagePath: String?
    ) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateUserEditableMetadata(
            bookId = bookId,
            title = metadata.title,
            author = metadata.author,
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            description = metadata.description,
            coverImagePath = coverImagePath,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            timestamp = currentTime
        )
        Timber.d("Updated user-editable metadata for $bookId")
    }

    override suspend fun updateCustomName(bookId: String, customName: String?) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateCustomName(bookId, customName, currentTime)
        Timber.d("Updated custom display name for $bookId")
    }

    override suspend fun restoreOriginalMetadata(
        bookId: String,
        fileSize: Long,
        fileContentModifiedTimestamp: Long,
        coverImagePath: String?
    ) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.restoreOriginalMetadata(bookId, coverImagePath, fileSize, fileContentModifiedTimestamp, currentTime)
        Timber.d("Restored original metadata for $bookId")
    }

    override suspend fun updateHighlights(bookId: String, highlightsJson: String) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateHighlights(bookId, highlightsJson, currentTime)
        Timber.d("Updated highlights for $bookId")
    }

    override suspend fun syncLocalMetadataToFolder(bookId: String, force: Boolean): Boolean = withContext(Dispatchers.IO) {
        val entity = recentFileDao.getFileByBookId(bookId) ?: return@withContext true
        val folderUriString = entity.sourceFolderUri

        if (folderUriString != null) {
            val appStorageRoot = appStorageRootForFolderUri(folderUriString)
            if (appStorageRoot == null && !isLocalFolderSyncEnabled(folderUriString)) {
                Timber.d("SyncDebug: Folder sync disabled for $folderUriString. Skipping metadata sidecar.")
                return@withContext true
            }

            val hasProgress = (entity.progressPercentage != null && entity.progressPercentage > 0f)
            val hasBookmarks = !entity.bookmarks.isNullOrEmpty() && entity.bookmarks != "[]"
            val hasHighlights = !entity.highlights.isNullOrEmpty() && entity.highlights != "[]"
            val isDirty = entity.isRecent || hasProgress || hasBookmarks || hasHighlights

            if (!force && !isDirty) {
                Timber.d("SyncDebug: Book $bookId is 'Clean' (Unread/Not Recent). Skipping JSON creation.")
                return@withContext true
            }

            Timber.d("Syncing metadata to local folder for book: $bookId")

            val metadata = FolderBookMetadata(
                bookId = entity.bookId,
                title = entity.title,
                author = entity.author,
                displayName = entity.displayName,
                type = entity.type.name,
                lastChapterIndex = entity.lastChapterIndex,
                lastPage = entity.lastPage,
                lastPositionCfi = entity.lastPositionCfi,
                progressPercentage = entity.progressPercentage ?: 0f,
                isRecent = entity.isRecent,
                lastModifiedTimestamp = entity.lastModifiedTimestamp,
                bookmarksJson = entity.bookmarks,
                locatorBlockIndex = entity.locatorBlockIndex,
                locatorCharOffset = entity.locatorCharOffset,
                customName = entity.customName,
                highlightsJson = entity.highlights,
                seriesName = entity.seriesName,
                seriesIndex = entity.seriesIndex,
                description = entity.description,
                originalTitle = entity.originalTitle,
                originalAuthor = entity.originalAuthor,
                originalSeriesName = entity.originalSeriesName,
                originalSeriesIndex = entity.originalSeriesIndex,
                originalDescription = entity.originalDescription
            )
            // Keep the exact canonical bytes available for the durable cloud
            // wake-up diagnostics.  The sidecar writer still owns the atomic
            // commit; this value is never uploaded directly from here.
            val metadataJson = metadata.toJsonString()
            val accountId = authRepository.getSignedInUser()?.uid?.trim()
                ?.takeIf { it.isNotBlank() }
            val rootId = accountId?.let { id ->
                CloudFolderAppStoragePrefs.rootIdForUri(context, id, folderUriString)
            }
            cloudFolderLogD(
                "event=metadata_sidecar_write_start root=${cloudFolderSafeId(rootId ?: folderUriString)} " +
                    "book=${cloudFolderSafeId(bookId)} source=${cloudFolderSafeUri(folderUriString.toUri())} " +
                    "schema=${metadata.schemaVersion} bytes=${metadataJson.toByteArray(Charsets.UTF_8).size} " +
                    "hash=${cloudFolderSidecarPayloadInfo(metadataJson)?.sha256 ?: "none"} force=$force",
            )

            val saved = if (appStorageRoot != null) {
                LocalSyncUtils.saveMetadataToAppStorage(appStorageRoot, metadata)
            } else {
                LocalSyncUtils.saveMetadataToFolder(
                    context = context,
                    sourceFolderUri = folderUriString.toUri(),
                    metadata = metadata,
                )
            }
            if (saved) {
                CloudFolderMetadataSyncScheduler.onSidecarCommitted(
                    context = context,
                    sourceFolderUri = folderUriString,
                    bookId = bookId,
                    kind = CloudFolderMetadataSyncScheduler.METADATA_KIND,
                    payload = metadataJson,
                )
            }
            cloudFolderLogD(
                "event=metadata_sidecar_write_end root=${cloudFolderSafeId(rootId ?: folderUriString)} " +
                    "book=${cloudFolderSafeId(bookId)} result=${if (saved) "success" else "failure"} " +
                    "source=${cloudFolderSafeUri(folderUriString.toUri())}",
            )
            return@withContext saved
        }
        true
    }

    override suspend fun syncLocalAnnotationsToFolder(bookId: String): Boolean = withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("syncLocalAnnotationsToFolder called for bookId: $bookId")
        val entity = recentFileDao.getFileByBookId(bookId) ?: run {
            Timber.tag("FolderAnnotationSync").w("Entity not found for bookId: $bookId")
            return@withContext false
        }
        val folderUriString = entity.sourceFolderUri ?: run {
            Timber.tag("FolderAnnotationSync").w("sourceFolderUri is null for bookId: $bookId")
            return@withContext false
        }
        val appStorageRoot = appStorageRootForFolderUri(folderUriString)
        if (appStorageRoot == null && !isLocalFolderSyncEnabled(folderUriString)) {
            Timber.tag("FolderAnnotationSync").d("Folder sync disabled for $folderUriString. Skipping annotation sidecar.")
            return@withContext false
        }

        val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId)
        val deletedInkFile = pdfAnnotationRepository.getDeletedAnnotationsFileForSync(bookId)
        val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
        val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
        val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)
        val highlightFile = pdfHighlightRepository.getFileForSync(bookId)

        val hasInk = inkFile?.exists() == true
        val hasDeletedInk = deletedInkFile?.exists() == true
        val hasRichText = richTextFile.exists()
        val hasLayout = layoutFile.exists()
        val hasTextBoxes = textBoxFile.exists()
        val hasHighlights = highlightFile.exists()

        Timber.tag("FolderAnnotationSync").d("File checks -> hasInk: $hasInk, hasRichText: $hasRichText, hasLayout: $hasLayout, hasTextBoxes: $hasTextBoxes, hasHighlights: $hasHighlights")
        Timber.d(
            "android.folder.export candidates book=$bookId hasRichText=$hasRichText " +
                "richBytes=${if (hasRichText) richTextFile.length() else 0L} folder=$folderUriString"
        )

        val existingSidecarBeforeClear = if (
            !hasInk && !hasDeletedInk && !hasRichText && !hasLayout && !hasTextBoxes && !hasHighlights
        ) {
            if (appStorageRoot != null) {
                LocalSyncUtils.getAnnotationSidecarFromAppStorage(appStorageRoot, bookId)
            } else {
                LocalSyncUtils.getAnnotationSidecar(
                    context = context,
                    sourceFolderUri = folderUriString.toUri(),
                    bookId = bookId,
                )
            }
        } else {
            null
        }
        val existingSidecarHasAnnotationState = existingSidecarBeforeClear?.second?.let { payload ->
            val hasExplicitPayload = SharedPdfAnnotationSidecarCodec.hasExplicitAnnotationPayload(payload)
            val hasDeletionTombstones =
                SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(payload).isNotEmpty()
            val alreadyCleared =
                SharedPdfAnnotationSidecarCodec.hasExplicitEmptyAnnotationPayload(payload)
            (hasExplicitPayload || hasDeletionTombstones) && !alreadyCleared
        } == true

        if (!hasInk && !hasDeletedInk && !hasRichText && !hasLayout && !hasTextBoxes && !hasHighlights) {
            if (!existingSidecarHasAnnotationState) {
                Timber.tag("FolderAnnotationSync").d(
                    "No annotations found locally for bookId: $bookId and no remote annotation state needs clearing."
                )
                return@withContext true
            }
            Timber.tag("FolderAnnotationSync").i(
                "Local annotation payload is empty; committing explicit clear for bookId=$bookId"
            )
        }

        val bundleJson = JSONObject()

        fun putJsonSafe(key: String, file: File) {
            try {
                val content = file.readText().trim()
                if (key == "text") {
                    Timber.d(
                        "android.folder.export.readRichText book=$bookId rawLen=${content.length} file=${file.absolutePath}"
                    )
                }
                if (content.startsWith("[")) {
                    bundleJson.put(key, JSONArray(content))
                } else if (content.startsWith("{")) {
                    bundleJson.put(key, JSONObject(content))
                }
            } catch (e: Exception) {
                if (key == "text") {
                    Timber
                        .e(e, "android.folder.export.richTextParseFailed book=$bookId")
                }
                Timber.tag("FolderAnnotationSync").e(e, "Error parsing $key file")
            }
        }

        if (hasInk) putJsonSafe("ink", inkFile)
        if (hasDeletedInk) {
            putJsonSafe(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS, deletedInkFile)
        }
        if (hasRichText) putJsonSafe("text", richTextFile)
        if (hasLayout) putJsonSafe("layout", layoutFile)
        if (hasTextBoxes) putJsonSafe("textBoxes", textBoxFile)
        if (hasHighlights) putJsonSafe("highlights", highlightFile)

        val tsInk = if(hasInk) inkFile.lastModified() else 0L
        val tsDeletedInk = if (hasDeletedInk) deletedInkFile.lastModified() else 0L
        val tsText = if(hasRichText) richTextFile.lastModified() else 0L
        val tsLayout = if(hasLayout) layoutFile.lastModified() else 0L
        val tsBox = if(hasTextBoxes) textBoxFile.lastModified() else 0L
        val tsHighlight = if(hasHighlights) highlightFile.lastModified() else 0L

        val hasLocalPayload =
            hasInk || hasDeletedInk || hasRichText || hasLayout || hasTextBoxes || hasHighlights
        val maxFileTs = maxOf(tsInk, tsDeletedInk, tsText, tsLayout, tsBox, tsHighlight)
        val nextClearTimestamp = if (!hasLocalPayload) {
            existingSidecarBeforeClear?.first?.let { previousTimestamp ->
                if (previousTimestamp == Long.MAX_VALUE) Long.MAX_VALUE else previousTimestamp + 1L
            } ?: 0L
        } else {
            0L
        }
        val finalTs = maxOf(maxFileTs, System.currentTimeMillis(), nextClearTimestamp)

        Timber.tag("FolderAnnotationSync").d("Pushing annotation bundle for $bookId to folder. finalTs=$finalTs")

        val canonicalBundleJson = if (!hasLocalPayload) {
            SharedPdfAnnotationSidecarCodec.clearAllAnnotationsDataJson(
                previousDataJson = existingSidecarBeforeClear?.second,
                deletedAt = finalTs,
            )
        } else {
            SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(bundleJson.toString())
        }
        if (hasRichText) {
            Timber.d(
                "android.folder.export.saveSidecar book=$bookId timestamp=$finalTs canonicalLen=${canonicalBundleJson.length}"
            )
        }

        val annotationPayloadInfo = cloudFolderSidecarPayloadInfo(canonicalBundleJson)
        val annotationRootId = authRepository.getSignedInUser()?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> CloudFolderAppStoragePrefs.rootIdForUri(context, id, folderUriString) }
        cloudFolderLogD(
            "event=annotation_sidecar_write_start root=${cloudFolderSafeId(annotationRootId ?: folderUriString)} " +
                "book=${cloudFolderSafeId(bookId)} source=${cloudFolderSafeUri(folderUriString.toUri())} " +
                "${annotationPayloadInfo.toLogFields()} finalTs=$finalTs",
        )

        val saved = if (appStorageRoot != null) {
            LocalSyncUtils.saveAnnotationSidecarToAppStorage(
                root = appStorageRoot,
                bookId = bookId,
                jsonPayload = canonicalBundleJson,
                timestamp = finalTs,
            )
        } else {
            LocalSyncUtils.saveAnnotationSidecar(
                context = context,
                sourceFolderUri = folderUriString.toUri(),
                bookId = bookId,
                jsonPayload = canonicalBundleJson,
                timestamp = finalTs,
            )
        }

        cloudFolderLogD(
            "event=annotation_sidecar_write_end root=${cloudFolderSafeId(annotationRootId ?: folderUriString)} " +
                "book=${cloudFolderSafeId(bookId)} result=${if (saved) "success" else "failure"} " +
                "source=${cloudFolderSafeUri(folderUriString.toUri())} " +
                "${annotationPayloadInfo.toLogFields()}",
        )
        if (!saved) return@withContext false

        CloudFolderMetadataSyncScheduler.onSidecarCommitted(
            context = context,
            sourceFolderUri = folderUriString,
            bookId = bookId,
            kind = CloudFolderMetadataSyncScheduler.ANNOTATIONS_KIND,
            payload = canonicalBundleJson,
        )

        val savedSidecar = if (appStorageRoot != null) {
            LocalSyncUtils.getAnnotationSidecarFromAppStorage(appStorageRoot, bookId)
        } else {
            LocalSyncUtils.getAnnotationSidecar(
                context = context,
                sourceFolderUri = folderUriString.toUri(),
                bookId = bookId,
            )
        }
        if (savedSidecar != null && savedSidecar.second != canonicalBundleJson) {
            importAnnotationBundle(
                bookId = bookId,
                jsonString = savedSidecar.second,
                lastModifiedTimestamp = savedSidecar.first
            )
        }
        savedSidecar != null
    }

    override suspend fun importAnnotationBundle(
        bookId: String,
        jsonString: String,
        lastModifiedTimestamp: Long?
    ) = withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("importAnnotationBundle: Processing bundle for $bookId")
        try {
            val bundle = JSONObject(
                SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(jsonString)
            )
            logCloudAnnotationSyncTrace {
                "android.repository.import_bundle book=$bookId remoteTs=${lastModifiedTimestamp ?: 0L} " +
                    "rawBytes=${jsonString.length} keys=${bundle.keys().asSequence().toList()}"
            }
            Timber.d(
                "android.folder.import.bundle book=$bookId rawLen=${jsonString.length} " +
                    "hasRichText=${bundle.has("text")} keys=${bundle.keys().asSequence().toList()}"
            )

            fun writeSafe(key: String, file: File?) {
                if (file != null && bundle.has(key)) {
                    file.parentFile?.mkdirs()
                    val contentStr = bundle.get(key).toString()
                    file.writeJsonAtomically(contentStr)
                    lastModifiedTimestamp?.takeIf { it > 0L }?.let(file::setLastModified)
                    logCloudAnnotationSyncTrace {
                        "android.repository.import_write key=$key book=$bookId bytes=${contentStr.length} " +
                            "path=${file.absolutePath.cloudSyncPreview(140)} ts=${file.lastModified()}"
                    }
                    if (key == "text") {
                        Timber.d(
                            "android.folder.import.writeRichText book=$bookId rawLen=${contentStr.length} file=${file.absolutePath}"
                        )
                    }
                    Timber.tag("FolderAnnotationSync").v("   -> Updated $key file (${contentStr.length} chars)")
                } else if (file != null) {
                    logCloudAnnotationSyncTrace {
                        "android.repository.import_missing_key key=$key book=$bookId " +
                            "path=${file.absolutePath.cloudSyncPreview(140)} exists=${file.exists()}"
                    }
                }
            }

            // 1. Ink
            val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId) ?: File(
                context.filesDir, "annotations/annotation_$bookId.json"
            )
            writeSafe("ink", inkFile)
            writeSafe(
                SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS,
                File(context.filesDir, "annotations/deleted_annotation_$bookId.json")
            )

            // 2. Text
            writeSafe("text", pdfRichTextRepository.getFileForSync(bookId))

            // 3. Layout
            writeSafe("layout", pageLayoutRepository.getLayoutFile(bookId))

            // 4. Text Boxes
            writeSafe("textBoxes", pdfTextBoxRepository.getFileForSync(bookId))

            // 5. Highlights
            writeSafe("highlights", pdfHighlightRepository.getFileForSync(bookId))

            Timber.tag("FolderAnnotationSync").i("Successfully imported annotation bundle for $bookId from folder.")
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Failed to import annotation bundle for $bookId")
        }
    }

    override suspend fun deleteFilesBySourceFolder(folderUriString: String) = withContext(Dispatchers.IO) {
        val start = ReaderPerfLog.nowNanos()
        val filesToRemove = recentFileDao.getFilesBySourceFolder(folderUriString)
        recentFileDao.deleteFilesBySourceFolder(folderUriString)
        ReaderPerfLog.i(
            "FolderRemove db delete books=${filesToRemove.size} elapsed=${ReaderPerfLog.elapsedMs(start)}ms folder=$folderUriString"
        )

        filesToRemove.forEach { item ->
            cleanupLocalBookArtifacts(item, "detached folder book")
        }
    }

    override suspend fun updateEpubReadingPosition(uriString: String, locator: Locator, cfiForWebView: String?, progress: Float) = withContext(Dispatchers.IO) {
        val item = recentFileDao.getFileByUri(uriString)
        if (item != null) {
            val currentTime = System.currentTimeMillis()
            val operation = com.aryan.reader.cloudFolderOperationId("reader-position", item.bookId, currentTime)
            val correlation = com.aryan.reader.cloudFolderSyncCorrelationId("reader-position", item.bookId, currentTime)
            cloudFolderLogD(
                "event=room_position_write_start operation=$operation correlation=$correlation " +
                    "book=${cloudFolderSafeId(item.bookId)} kind=epub " +
                    "beforeReadTs=${item.toRecentFileItem().effectiveReadingPositionModifiedTimestamp()} " +
                    "chapter=${locator.chapterIndex} block=${locator.blockIndex} char=${locator.charOffset} " +
                    "progress=$progress",
            )
            recentFileDao.updateEpubReadingPosition(
                bookId = item.bookId,
                cfi = cfiForWebView,
                chapterIndex = locator.chapterIndex,
                blockIndex = locator.blockIndex,
                charOffset = locator.charOffset,
                progress = progress,
                timestamp = currentTime
            )
            val updated = recentFileDao.getFileByBookId(item.bookId)
            cloudFolderLogD(
                "event=room_position_write_end operation=$operation correlation=$correlation " +
                    "book=${cloudFolderSafeId(item.bookId)} kind=epub result=${if (updated != null) "success" else "missing"} " +
                    "afterReadTs=${updated?.toRecentFileItem()?.effectiveReadingPositionModifiedTimestamp() ?: 0L} " +
                    "afterChapter=${updated?.lastChapterIndex ?: "none"} afterBlock=${updated?.locatorBlockIndex ?: "none"} " +
                    "afterChar=${updated?.locatorCharOffset ?: "none"} afterProgress=${updated?.progressPercentage ?: "none"}",
            )
            Timber.d("Updated EPUB reading position for ${item.bookId} to Locator: $locator, Progress: $progress%")
        } else {
            cloudFolderLogW(
                "event=room_position_write_end book=${cloudFolderSafeId(uriString)} kind=epub " +
                    "result=skipped reason=book_not_found",
            )
        }
    }

    override suspend fun getFolderBooksNeedingTextMetadata(
        sourceFolderUri: String?,
        limit: Int
    ): List<RecentFileItem> = withContext(Dispatchers.IO) {
        val queryLimit = limit.coerceAtLeast(1)
        val entities = if (sourceFolderUri.isNullOrBlank()) {
            recentFileDao.getFolderBooksNeedingTextMetadata(queryLimit)
        } else {
            recentFileDao.getFolderBooksNeedingTextMetadata(sourceFolderUri, queryLimit)
        }
        return@withContext entities.map { it.toRecentFileItem() }
    }

    override suspend fun hasFolderBooksNeedingTextMetadata(sourceFolderUri: String?): Boolean = withContext(Dispatchers.IO) {
        val count = if (sourceFolderUri.isNullOrBlank()) {
            recentFileDao.countFolderBooksNeedingTextMetadata()
        } else {
            recentFileDao.countFolderBooksNeedingTextMetadata(sourceFolderUri)
        }
        return@withContext count > 0
    }

    override suspend fun updateExtractedMetadata(items: List<RecentFileItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.chunked(300).forEach { chunk ->
            database.withTransaction {
                chunk.forEach { item ->
                    recentFileDao.updateExtractedMetadata(
                        bookId = item.bookId,
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        author = item.author,
                        seriesName = item.seriesName,
                        seriesIndex = item.seriesIndex,
                        description = item.description,
                        fileSize = item.fileSize,
                        fileContentModifiedTimestamp = item.fileContentModifiedTimestamp,
                        textMetadataParsed = item.folderTextMetadataParsed,
                        coverMetadataParsed = item.folderCoverMetadataParsed
                    )
                }
            }
        }
        Timber.tag(ReaderPerfLog.TAG).d("Metadata extraction batch updated ${items.size} rows.")
    }

    override suspend fun detachAllFolderBooks() = withContext(Dispatchers.IO) {
        recentFileDao.detachAllFolderBooks()
        Timber.d("Detached all folder books. They are now standard local files.")
    }

    private fun isLocalFolderSyncEnabled(folderUriString: String): Boolean {
        val prefs = context.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        return SyncedFolderPrefs.isLocalSyncEnabled(
            jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null),
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null),
            folderUriString = folderUriString
        )
    }

    private fun appStorageRootForFolderUri(folderUriString: String): File? {
        val accountId = authRepository.getSignedInUser()?.uid?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val rootId = CloudFolderAppStoragePrefs.rootIdForUri(
            context = context,
            accountId = accountId,
            uriString = folderUriString,
        ) ?: return null
        return runCatching { cloudFolderAppRootDirectory(context.filesDir, rootId) }
            .getOrNull()
            ?.takeIf { it.isDirectory }
    }

    override suspend fun updateBookmarks(bookId: String, bookmarksJson: String) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val before = recentFileDao.getFileByBookId(bookId)
        val operation = com.aryan.reader.cloudFolderOperationId("reader-bookmarks", bookId, currentTime)
        val correlation = com.aryan.reader.cloudFolderSyncCorrelationId("reader-bookmarks", bookId, currentTime)
        cloudFolderLogD(
            "event=room_bookmarks_write_start operation=$operation correlation=$correlation " +
                "book=${cloudFolderSafeId(bookId)} beforeTs=${before?.lastModifiedTimestamp ?: 0L} " +
                "beforeBytes=${before?.bookmarks?.toByteArray(Charsets.UTF_8)?.size ?: 0} " +
                "afterBytes=${bookmarksJson.toByteArray(Charsets.UTF_8).size}",
        )
        recentFileDao.updateBookmarks(bookId, bookmarksJson, currentTime)
        val after = recentFileDao.getFileByBookId(bookId)
        cloudFolderLogD(
            "event=room_bookmarks_write_end operation=$operation correlation=$correlation " +
                "book=${cloudFolderSafeId(bookId)} result=${if (after != null) "success" else "missing"} " +
                "afterTs=${after?.lastModifiedTimestamp ?: 0L}",
        )
        Timber.d("Updated bookmarks for $bookId")
    }

    override suspend fun updatePdfReadingPosition(uriString: String, page: Int, progress: Float) = withContext(Dispatchers.IO) {
        val item = recentFileDao.getFileByUri(uriString)
        if (item != null) {
            val currentTime = System.currentTimeMillis()
            val operation = com.aryan.reader.cloudFolderOperationId("reader-position", item.bookId, currentTime)
            val correlation = com.aryan.reader.cloudFolderSyncCorrelationId("reader-position", item.bookId, currentTime)
            cloudFolderLogD(
                "event=room_position_write_start operation=$operation correlation=$correlation " +
                    "book=${cloudFolderSafeId(item.bookId)} kind=pdf " +
                    "beforeReadTs=${item.toRecentFileItem().effectiveReadingPositionModifiedTimestamp()} " +
                    "page=$page progress=$progress",
            )
            recentFileDao.updatePdfReadingPosition(item.bookId, page, progress, currentTime)
            val updated = recentFileDao.getFileByBookId(item.bookId)
            cloudFolderLogD(
                "event=room_position_write_end operation=$operation correlation=$correlation " +
                    "book=${cloudFolderSafeId(item.bookId)} kind=pdf result=${if (updated != null) "success" else "missing"} " +
                    "afterReadTs=${updated?.toRecentFileItem()?.effectiveReadingPositionModifiedTimestamp() ?: 0L} " +
                    "afterPage=${updated?.lastPage ?: "none"} afterProgress=${updated?.progressPercentage ?: "none"}",
            )
            Timber.tag("PdfPositionDebug").i("Repository: Executed DB update for ${item.bookId} to Page $page, Progress $progress% at TS: $currentTime")
            logCloudSyncTrace {
                "android.repository.pdf_position_update book=${item.bookId} page=$page progress=$progress ts=$currentTime"
            }
        } else {
            cloudFolderLogW(
                "event=room_position_write_end book=${cloudFolderSafeId(uriString)} kind=pdf " +
                    "result=skipped reason=book_not_found",
            )
            Timber.tag("PdfPositionDebug").e("Repository: DB Update Failed! No recent file found matching URI: $uriString")
        }
    }

    @Suppress("unused")
    override suspend fun makeBookAvailable(bookId: String, internalUri: Uri) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateBookAvailability(bookId, internalUri.toString(), currentTime)
        Timber.d("Made book available locally: $bookId at URI $internalUri")
    }

    override suspend fun markAsNotRecent(bookIds: List<String>) = withContext(Dispatchers.IO) {
        bookIds.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                Timber.d("DeleteDebug: DAO - Marking ${chunk.size} items as not recent.")
                recentFileDao.markAsNotRecent(chunk, System.currentTimeMillis())
            }
        }
    }

    override suspend fun markAsDeleted(bookIds: List<String>) = withContext(Dispatchers.IO) {
        bookIds.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                recentFileDao.markAsDeleted(chunk, System.currentTimeMillis())
                Timber.d("DeleteDebug: DAO - Marked ${chunk.size} items as deleted.")
            }
        }
    }

    override suspend fun deleteFilePermanently(bookIds: List<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isEmpty()) return@withContext

        bookIds.chunked(900).forEach { chunk ->
            val itemsToRemove = chunk.mapNotNull { recentFileDao.getFileByBookId(it) }

            if (itemsToRemove.isNotEmpty()) {
                Timber.d("DeleteDebug: DAO - Permanently deleting ${itemsToRemove.size} files.")
                itemsToRemove.forEach { item ->
                    try {
                        item.uriString?.let { bookImporter.deleteBookByUriString(it) }
                    } catch (e: Exception) {
                        Timber.w("DeleteDebug: Physical file deletion failed (likely already gone) for ${item.bookId}: ${e.message}")
                    }

                    cleanupLocalBookArtifacts(item, "permanent deletion")
                }
                recentFileDao.deleteFilePermanently(itemsToRemove.map { it.bookId })
                Timber.d("Permanently removed recent files from DB.")
            } else {
                Timber.w("DeleteDebug: DAO - Files not found for permanent deletion.")
            }
        }
    }

    /**
     * Finalize a worker-owned deletion only when the claimed local generation
     * is still present. The conditional claim/DELETE prevents a concurrent
     * re-import or edit from being removed by a stale worker completion.
     */
    internal suspend fun deleteFilePermanentlyIfCloudDeleteGenerationMatches(
        bookId: String,
        generation: CloudBookLocalGeneration,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = recentFileDao.getFileByBookId(bookId) ?: return@withContext true
        if (!current.toRecentFileItem().matchesCloudBookLocalGeneration(generation)) {
            return@withContext false
        }
        if (
            recentFileDao.claimForCloudDelete(
                bookId = bookId,
                lastModifiedTimestamp = generation.lastModifiedTimestamp,
                timestamp = generation.timestamp,
                fileContentModifiedTimestamp = generation.fileContentModifiedTimestamp,
                fileSize = generation.fileSize,
                uriString = generation.uriString,
            ) == 0
        ) {
            return@withContext false
        }

        // Re-read after the conditional claim. If an edit/re-import replaced
        // the row, do not touch its physical URI or cached artifacts.
        val claimed = recentFileDao.getFileByBookId(bookId)
            ?: return@withContext true
        if (!claimed.toRecentFileItem().matchesCloudBookLocalGeneration(generation)) {
            return@withContext false
        }
        try {
            claimed.uriString?.let { uri ->
                try {
                    bookImporter.deleteBookByUriString(uri)
                } catch (error: Exception) {
                    Timber.w(
                        error,
                        "Cloud-delete physical file cleanup failed (likely already gone) for $bookId",
                    )
                }
            }
            cleanupLocalBookArtifacts(claimed, "cloud-delete finalization")
        } catch (error: Exception) {
            Timber.e(error, "Cloud-delete artifact cleanup failed for $bookId")
            return@withContext false
        }
        recentFileDao.deleteCloudDeleteClaimedGeneration(
            bookId = bookId,
            lastModifiedTimestamp = generation.lastModifiedTimestamp,
            timestamp = generation.timestamp,
            fileContentModifiedTimestamp = generation.fileContentModifiedTimestamp,
            fileSize = generation.fileSize,
            uriString = generation.uriString,
        ) == 1
    }

    /**
     * Finalize a cloud-delete recovery without touching user-owned storage.
     *
     * A WorkManager retry can race a re-import between a generation read and
     * physical URI cleanup. Keep this operation entirely inside one Room
     * transaction: the row is hidden and removed only when every generation
     * field still matches the worker's claim. The source file and derived
     * artifacts are intentionally left in place; a later orphan sweeper or
     * manual cleanup can reclaim them once no newer local incarnation uses
     * the URI.
     */
    internal suspend fun removeCloudDeleteGenerationFromDatabase(
        bookId: String,
        generation: CloudBookLocalGeneration,
    ): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = recentFileDao.getFileByBookId(bookId)
                ?: return@withTransaction true
            if (!current.toRecentFileItem().matchesCloudBookLocalGeneration(generation)) {
                return@withTransaction false
            }
            if (
                recentFileDao.claimForCloudDelete(
                    bookId = bookId,
                    lastModifiedTimestamp = generation.lastModifiedTimestamp,
                    timestamp = generation.timestamp,
                    fileContentModifiedTimestamp = generation.fileContentModifiedTimestamp,
                    fileSize = generation.fileSize,
                    uriString = generation.uriString,
                ) != 1
            ) {
                return@withTransaction false
            }
            recentFileDao.deleteCloudDeleteClaimedGeneration(
                bookId = bookId,
                lastModifiedTimestamp = generation.lastModifiedTimestamp,
                timestamp = generation.timestamp,
                fileContentModifiedTimestamp = generation.fileContentModifiedTimestamp,
                fileSize = generation.fileSize,
                uriString = generation.uriString,
            ) == 1
        }
    }

    private suspend fun cleanupLocalBookArtifacts(item: RecentFileEntity, reason: String) {
        item.coverImagePath?.let { coverPath ->
            runCatching { deleteCachedCover(coverPath) }
                .onFailure { Timber.e(it, "Error deleting cached cover for $reason ${item.bookId}") }
        }

        runCatching { pdfAnnotationRepository.getAnnotationFileForSync(item.bookId)?.delete() }
            .onFailure { Timber.e(it, "Error deleting PDF ink sidecar for $reason ${item.bookId}") }
        runCatching { pdfRichTextRepository.getFileForSync(item.bookId).delete() }
            .onFailure { Timber.e(it, "Error deleting PDF rich text sidecar for $reason ${item.bookId}") }
        runCatching { pageLayoutRepository.getLayoutFile(item.bookId).delete() }
            .onFailure { Timber.e(it, "Error deleting PDF page layout sidecar for $reason ${item.bookId}") }
        runCatching { pdfTextBoxRepository.getFileForSync(item.bookId).delete() }
            .onFailure { Timber.e(it, "Error deleting PDF text box sidecar for $reason ${item.bookId}") }
        runCatching { pdfHighlightRepository.getFileForSync(item.bookId).delete() }
            .onFailure { Timber.e(it, "Error deleting PDF highlight sidecar for $reason ${item.bookId}") }
        runCatching { pdfTextRepository.clearBookText(item.bookId) }
            .onFailure { Timber.e(it, "Error clearing PDF text cache for $reason ${item.bookId}") }
        runCatching { bookCacheDao.deleteEntireBookCache(item.bookId) }
            .onFailure { Timber.e(it, "Error clearing pagination cache for $reason ${item.bookId}") }
        runCatching { ImportedFileCache.clearBookCache(context, item.bookId) }
            .onFailure { Timber.e(it, "Error clearing imported file cache for $reason ${item.bookId}") }
    }

    private fun getCoverCacheDirInternal(): File {
        if (!coverCacheDir.exists()) {
            coverCacheDir.mkdirs()
        }
        return coverCacheDir
    }

    override suspend fun saveCoverToCache(bitmap: Bitmap, uri: Uri): String? = withContext(Dispatchers.IO) {
        val cacheDir = getCoverCacheDirInternal()
        val filename = "cover_${uri.toString().hashCode()}_${System.currentTimeMillis()}.png"
        val file = File(cacheDir, filename)
        var fos: FileOutputStream? = null
        var scaledCopy: Bitmap? = null
        try {
            deleteCoverCacheVariants(uri)
            val bitmapToSave = bitmap.scaledToCanvasLimit(
                maxBytes = 8L * 1024L * 1024L,
                maxDimension = EMBEDDED_COVER_MAX_DIMENSION
            ).also {
                if (it !== bitmap) scaledCopy = it
            }
            fos = FileOutputStream(file)
            bitmapToSave.compress(Bitmap.CompressFormat.PNG, 90, fos)
            Timber.d("Saved cover image to: ${file.absolutePath}")
            return@withContext file.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to save cover image to cache for $uri")
            file.delete()
            return@withContext null
        } finally {
            fos?.close()
            scaledCopy?.recycle()
        }
    }

    override suspend fun saveEmbeddedCoverToCache(bytes: ByteArray, uri: Uri, extension: String): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val safeExtension = extension.lowercase().takeIf { it in EMBEDDED_COVER_EXTENSIONS } ?: "png"
        if (
            bytes.size.toLong() <= DIRECT_EMBEDDED_COVER_MAX_BYTES &&
            bounds.outWidth <= EMBEDDED_COVER_MAX_DIMENSION &&
            bounds.outHeight <= EMBEDDED_COVER_MAX_DIMENSION
        ) {
            return@withContext saveEmbeddedCoverBytesToCache(bytes, uri, safeExtension)
        }

        var sampleSize = 1
        while (
            (bounds.outWidth / sampleSize) > EMBEDDED_COVER_MAX_DIMENSION ||
            (bounds.outHeight / sampleSize) > EMBEDDED_COVER_MAX_DIMENSION
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return@withContext null

        try {
            return@withContext saveCoverToCache(decoded, uri)
        } finally {
            decoded.recycle()
        }
    }

    private fun saveEmbeddedCoverBytesToCache(bytes: ByteArray, uri: Uri, extension: String): String? {
        val cacheDir = getCoverCacheDirInternal()
        val filename = "cover_${uri.toString().hashCode()}_${System.currentTimeMillis()}.$extension"
        val file = File(cacheDir, filename)
        return try {
            deleteCoverCacheVariants(uri)
            FileOutputStream(file).use { output -> output.write(bytes) }
            Timber.d("Saved embedded cover image to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to save embedded cover image to cache for $uri")
            file.delete()
            null
        }
    }

    private fun deleteCoverCacheVariants(uri: Uri) {
        val exactPrefix = "cover_${uri.toString().hashCode()}."
        val versionedPrefix = "cover_${uri.toString().hashCode()}_"
        getCoverCacheDirInternal().listFiles()
            ?.filter { it.isFile && (it.name.startsWith(exactPrefix) || it.name.startsWith(versionedPrefix)) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun deleteCachedCover(filePath: String): Boolean {
        val file = File(filePath)
        val deleted = file.delete()
        if (deleted) {
            Timber.d("Deleted cached cover: $filePath")
        } else {
            Timber.w("Failed to delete cached cover: $filePath")
        }
        return deleted
    }

    override suspend fun migrateBookIdLocally(oldId: String, newId: String) = withContext(Dispatchers.IO) {
        val oldEntity = recentFileDao.getFileByBookId(oldId) ?: return@withContext
        val newEntity = oldEntity.copy(bookId = newId)
        recentFileDao.insertOrUpdateFile(newEntity)
        recentFileDao.deleteFilePermanently(listOf(oldId))

        fun renameSafely(oldFile: File?, newFile: File?) {
            if (oldFile != null && oldFile.exists() && newFile != null) {
                if (newFile.exists()) newFile.delete()
                oldFile.renameTo(newFile)
            }
        }

        renameSafely(
            pdfAnnotationRepository.getAnnotationFileForSync(oldId) ?: File(context.filesDir, "annotations/annotation_$oldId.json"),
            pdfAnnotationRepository.getAnnotationFileForSync(newId) ?: File(context.filesDir, "annotations/annotation_$newId.json")
        )
        renameSafely(pdfRichTextRepository.getFileForSync(oldId), pdfRichTextRepository.getFileForSync(newId))
        renameSafely(pageLayoutRepository.getLayoutFile(oldId), pageLayoutRepository.getLayoutFile(newId))
        renameSafely(pdfTextBoxRepository.getFileForSync(oldId), pdfTextBoxRepository.getFileForSync(newId))
        renameSafely(pdfHighlightRepository.getFileForSync(oldId), pdfHighlightRepository.getFileForSync(newId))

        ImportedFileCache.clearTemporaryBookDirs(context, oldId)
        ImportedFileCache.clearTemporaryBookDirs(context, newId)
        val oldCache = ImportedFileCache.activeBookDir(context, oldId)
        val newCache = ImportedFileCache.activeBookDir(context, newId)
        if (oldCache.exists()) {
            if (newCache.exists()) newCache.deleteRecursively()
            oldCache.renameTo(newCache)
        }
        Timber.tag("SyncMigration").d("Migrated local sidecars from $oldId to $newId")
    }

    override suspend fun clearLocalCachesForBook(bookId: String) = withContext(Dispatchers.IO) {
        try {
            recentFileDao.getFileByBookId(bookId)?.coverImagePath?.let { deleteCachedCover(it) }
            pdfRichTextRepository.getFileForSync(bookId).delete()
            pageLayoutRepository.getLayoutFile(bookId).delete()
            ImportedFileCache.clearBookCache(context, bookId)
            Timber.d("Cleared local caches for modified book: $bookId")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing caches for $bookId")
        }
    }

    override suspend fun addRecentFiles(items: List<RecentFileItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.chunked(900).forEach { chunk ->
            val entities = chunk.map { it.toRecentFileEntity() }
            recentFileDao.insertOrUpdateFiles(entities)
        }
        Timber.d("Batch inserted/updated ${items.size} recent files in DB.")
    }

}
