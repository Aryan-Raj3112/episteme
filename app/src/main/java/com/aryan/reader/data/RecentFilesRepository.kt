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
import com.aryan.reader.cloudSyncAnnotationSummary
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.pdf.PdfRichTextRepository
import com.aryan.reader.epub.ImportedFileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import androidx.core.content.edit

private const val COVER_CACHE_DIR = "cover_cache"
private const val DIRECT_EMBEDDED_COVER_MAX_BYTES = 8L * 1024L * 1024L
private const val EMBEDDED_COVER_MAX_DIMENSION = 1200
private val EMBEDDED_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

class RecentFilesRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val recentFileDao = database.recentFileDao()
    private val coverCacheDir = File(context.filesDir, COVER_CACHE_DIR)
    private val bookImporter = BookImporter(context)

    private val pdfAnnotationRepository = PdfAnnotationRepository(context)
    private val pdfRichTextRepository = PdfRichTextRepository(context)
    private val pageLayoutRepository = PageLayoutRepository(context)
    private val pdfTextBoxRepository = PdfTextBoxRepository(context)
    private val pdfHighlightRepository = com.aryan.reader.pdf.data.PdfHighlightRepository(context)

    val activeShelvesFlow = database.shelfDao().getAllActiveShelves()
    val shelfCrossRefsFlow = database.shelfDao().getAllBookShelfCrossRefs()
    val tagsFlow = database.tagDao().getAllTags()
    val tagCrossRefsFlow = database.tagDao().getAllBookTagCrossRefs()

    init {
        if (!coverCacheDir.exists()) {
            coverCacheDir.mkdirs()
        }
    }

    fun getRecentFilesFlow(): Flow<List<RecentFileItem>> {
        return recentFileDao.getRecentFiles().map { entities ->
            entities.map { it.toRecentFileItem() }
        }
    }

    suspend fun getFileByBookId(bookId: String): RecentFileItem? = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFileByBookId(bookId)?.toRecentFileItem()
    }

    suspend fun getFileByUri(uriString: String): RecentFileItem? = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFileByUri(uriString)?.toRecentFileItem()
    }

    suspend fun addShelf(shelf: ShelfEntity) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).shelfDao().insertShelf(shelf)
    }

    suspend fun addBooksToShelf(shelfId: String, bookIds: List<String>) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val crossRefs = bookIds.map { BookShelfCrossRef(it, shelfId, timestamp) }
        AppDatabase.getDatabase(context).shelfDao().insertBookShelfCrossRefs(crossRefs)
    }

    suspend fun renameShelf(shelfId: String, newName: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).shelfDao().updateShelfName(shelfId, newName, System.currentTimeMillis())
    }

    suspend fun deleteShelf(shelfId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).shelfDao().markShelfAsDeleted(shelfId, System.currentTimeMillis())
    }

    suspend fun removeBooksFromShelf(shelfId: String, bookIds: List<String>) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).shelfDao().removeBooksFromShelf(shelfId, bookIds)
    }

    suspend fun getFilesBySourceFolder(sourceFolderUri: String): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFilesBySourceFolder(sourceFolderUri).map { it.toRecentFileItem() }
    }

    suspend fun getAllFilesForSync(): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getAllFiles().map { it.toRecentFileItem() }
    }

    suspend fun createTag(tag: TagEntity) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).tagDao().insertTag(tag)
    }

    suspend fun seedTagsIfEmpty(tags: List<TagEntity>) = withContext(Dispatchers.IO) {
        if (tags.isEmpty()) return@withContext
        val tagDao = AppDatabase.getDatabase(context).tagDao()
        if (tagDao.getTagCount() == 0) {
            tagDao.insertTags(tags)
        }
    }

    suspend fun assignTagToBook(bookId: String, tagId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).tagDao().insertBookTagCrossRef(BookTagCrossRef(bookId, tagId))
    }

    suspend fun removeTagFromBook(bookId: String, tagId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).tagDao().removeTagFromBook(tagId, bookId)
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        recentFileDao.clearAll()
        if (coverCacheDir.exists()) {
            coverCacheDir.deleteRecursively()
        }
        coverCacheDir.mkdirs()
        pdfHighlightRepository.clearAll()

        File(context.filesDir, "annotations").deleteRecursively()
        File(context.filesDir, "pdf_rich_text").deleteRecursively()
        File(context.filesDir, "page_layouts").deleteRecursively()
        File(context.filesDir, "pdf_text_boxes").deleteRecursively()

        context.cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("imported_file_") || name.startsWith("temp_") || name.startsWith("sync_bundle_")) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
        Timber.d("Cleared all local book data, sidecars, and cover cache.")
    }

    suspend fun addRecentFile(item: RecentFileItem) = withContext(Dispatchers.IO) {
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

    suspend fun updateUserEditableMetadata(
        bookId: String,
        metadata: BookMetadataEdit,
        fileSize: Long = 0L,
        fileContentModifiedTimestamp: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateUserEditableMetadata(
            bookId = bookId,
            title = metadata.title,
            author = metadata.author,
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            description = metadata.description,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            timestamp = currentTime
        )
        Timber.d("Updated user-editable metadata for $bookId")
    }

    suspend fun restoreOriginalMetadata(
        bookId: String,
        fileSize: Long = 0L,
        fileContentModifiedTimestamp: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.restoreOriginalMetadata(bookId, fileSize, fileContentModifiedTimestamp, currentTime)
        Timber.d("Restored original metadata for $bookId")
    }

    suspend fun updateHighlights(bookId: String, highlightsJson: String) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateHighlights(bookId, highlightsJson, currentTime)
        Timber.d("Updated highlights for $bookId")
    }

    suspend fun syncLocalMetadataToFolder(bookId: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        val entity = recentFileDao.getFileByBookId(bookId) ?: return@withContext
        val folderUriString = entity.sourceFolderUri

        if (folderUriString != null) {
            if (!isLocalFolderSyncEnabled(folderUriString)) {
                Timber.d("SyncDebug: Folder sync disabled for $folderUriString. Skipping metadata sidecar.")
                return@withContext
            }

            val hasProgress = (entity.progressPercentage != null && entity.progressPercentage > 0f)
            val hasBookmarks = !entity.bookmarks.isNullOrEmpty() && entity.bookmarks != "[]"
            val hasHighlights = !entity.highlights.isNullOrEmpty() && entity.highlights != "[]"
            val isDirty = entity.isRecent || hasProgress || hasBookmarks || hasHighlights

            if (!force && !isDirty) {
                Timber.d("SyncDebug: Book $bookId is 'Clean' (Unread/Not Recent). Skipping JSON creation.")
                return@withContext
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

            LocalSyncUtils.saveMetadataToFolder(
                context = context,
                sourceFolderUri = folderUriString.toUri(),
                metadata = metadata
            )
        }
    }

    suspend fun syncLocalAnnotationsToFolder(bookId: String) = withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("syncLocalAnnotationsToFolder called for bookId: $bookId")
        val entity = recentFileDao.getFileByBookId(bookId) ?: run {
            Timber.tag("FolderAnnotationSync").w("Entity not found for bookId: $bookId")
            return@withContext
        }
        val folderUriString = entity.sourceFolderUri ?: run {
            Timber.tag("FolderAnnotationSync").w("sourceFolderUri is null for bookId: $bookId")
            return@withContext
        }
        if (!isLocalFolderSyncEnabled(folderUriString)) {
            Timber.tag("FolderAnnotationSync").d("Folder sync disabled for $folderUriString. Skipping annotation sidecar.")
            return@withContext
        }

        val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId)
        val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
        val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
        val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)
        val highlightFile = pdfHighlightRepository.getFileForSync(bookId)

        val hasInk = inkFile?.exists() == true
        val hasRichText = richTextFile.exists()
        val hasLayout = layoutFile.exists()
        val hasTextBoxes = textBoxFile.exists()
        val hasHighlights = highlightFile.exists()

        Timber.tag("FolderAnnotationSync").d("File checks -> hasInk: $hasInk, hasRichText: $hasRichText, hasLayout: $hasLayout, hasTextBoxes: $hasTextBoxes, hasHighlights: $hasHighlights")
        Timber.d(
            "android.folder.export candidates book=$bookId hasRichText=$hasRichText " +
                "richBytes=${if (hasRichText) richTextFile.length() else 0L} folder=$folderUriString"
        )

        if (!hasInk && !hasRichText && !hasLayout && !hasTextBoxes && !hasHighlights) {
            Timber.tag("FolderAnnotationSync").d("No annotations found locally for bookId: $bookId. Aborting sync.")
            return@withContext
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
        if (hasRichText) putJsonSafe("text", richTextFile)
        if (hasLayout) putJsonSafe("layout", layoutFile)
        if (hasTextBoxes) putJsonSafe("textBoxes", textBoxFile)
        if (hasHighlights) putJsonSafe("highlights", highlightFile)

        val tsInk = if(hasInk) inkFile.lastModified() else 0L
        val tsText = if(hasRichText) richTextFile.lastModified() else 0L
        val tsLayout = if(hasLayout) layoutFile.lastModified() else 0L
        val tsBox = if(hasTextBoxes) textBoxFile.lastModified() else 0L
        val tsHighlight = if(hasHighlights) highlightFile.lastModified() else 0L

        val maxFileTs = maxOf(tsInk, tsText, tsLayout, tsBox, tsHighlight)
        val finalTs = maxOf(maxFileTs, System.currentTimeMillis())

        Timber.tag("FolderAnnotationSync").d("Pushing annotation bundle for $bookId to folder. finalTs=$finalTs")

        val canonicalBundleJson = SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(bundleJson.toString())
        if (hasRichText) {
            Timber.d(
                "android.folder.export.saveSidecar book=$bookId timestamp=$finalTs canonicalLen=${canonicalBundleJson.length}"
            )
        }

        LocalSyncUtils.saveAnnotationSidecar(
            context = context,
            sourceFolderUri = folderUriString.toUri(),
            bookId = bookId,
            jsonPayload = canonicalBundleJson,
            timestamp = finalTs
        )
    }

    suspend fun importAnnotationBundle(
        bookId: String,
        jsonString: String,
        lastModifiedTimestamp: Long? = null
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
                    file.writeText(contentStr)
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

    suspend fun deleteFilesBySourceFolder(folderUriString: String) = withContext(Dispatchers.IO) {
        val start = ReaderPerfLog.nowNanos()
        val filesToRemove = recentFileDao.getFilesBySourceFolder(folderUriString)
        recentFileDao.deleteFilesBySourceFolder(folderUriString)
        ReaderPerfLog.i(
            "FolderRemove db delete books=${filesToRemove.size} elapsed=${ReaderPerfLog.elapsedMs(start)}ms folder=$folderUriString"
        )

        filesToRemove.forEach { item ->
            item.coverImagePath?.let { deleteCachedCover(it) }
            try {
                pdfAnnotationRepository.getAnnotationFileForSync(item.bookId)?.delete()
                pdfRichTextRepository.getFileForSync(item.bookId).delete()
                pageLayoutRepository.getLayoutFile(item.bookId).delete()
                pdfTextBoxRepository.getFileForSync(item.bookId).delete()
                pdfHighlightRepository.getFileForSync(item.bookId).delete()
                ImportedFileCache.clearBookCache(context, item.bookId)
            } catch (e: Exception) {
                Timber.e(e, "Error during local cleanup for detached folder book ${item.bookId}")
            }
        }
    }

    suspend fun updateEpubReadingPosition(uriString: String, locator: Locator, cfiForWebView: String?, progress: Float) = withContext(Dispatchers.IO) {
        val item = recentFileDao.getFileByUri(uriString)
        if (item != null) {
            val currentTime = System.currentTimeMillis()
            recentFileDao.updateEpubReadingPosition(
                bookId = item.bookId,
                cfi = cfiForWebView,
                chapterIndex = locator.chapterIndex,
                blockIndex = locator.blockIndex,
                charOffset = locator.charOffset,
                progress = progress,
                timestamp = currentTime
            )
            Timber.d("Updated EPUB reading position for ${item.bookId} to Locator: $locator, Progress: $progress%")
        }
    }

    suspend fun getFolderBooksNeedingTextMetadata(
        sourceFolderUri: String? = null,
        limit: Int = Int.MAX_VALUE
    ): List<RecentFileItem> = withContext(Dispatchers.IO) {
        val queryLimit = limit.coerceAtLeast(1)
        val entities = if (sourceFolderUri.isNullOrBlank()) {
            recentFileDao.getFolderBooksNeedingTextMetadata(queryLimit)
        } else {
            recentFileDao.getFolderBooksNeedingTextMetadata(sourceFolderUri, queryLimit)
        }
        return@withContext entities.map { it.toRecentFileItem() }
    }

    suspend fun hasFolderBooksNeedingTextMetadata(sourceFolderUri: String? = null): Boolean = withContext(Dispatchers.IO) {
        val count = if (sourceFolderUri.isNullOrBlank()) {
            recentFileDao.countFolderBooksNeedingTextMetadata()
        } else {
            recentFileDao.countFolderBooksNeedingTextMetadata(sourceFolderUri)
        }
        return@withContext count > 0
    }

    suspend fun updateExtractedMetadata(items: List<RecentFileItem>) = withContext(Dispatchers.IO) {
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

    suspend fun detachAllFolderBooks() = withContext(Dispatchers.IO) {
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

    suspend fun updateBookmarks(bookId: String, bookmarksJson: String) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateBookmarks(bookId, bookmarksJson, currentTime)
        Timber.d("Updated bookmarks for $bookId")
    }

    suspend fun updatePdfReadingPosition(uriString: String, page: Int, progress: Float) = withContext(Dispatchers.IO) {
        val item = recentFileDao.getFileByUri(uriString)
        if (item != null) {
            val currentTime = System.currentTimeMillis()
            recentFileDao.updatePdfReadingPosition(item.bookId, page, progress, currentTime)
            Timber.tag("PdfPositionDebug").i("Repository: Executed DB update for ${item.bookId} to Page $page, Progress $progress% at TS: $currentTime")
            logCloudSyncTrace {
                "android.repository.pdf_position_update book=${item.bookId} page=$page progress=$progress ts=$currentTime"
            }
        } else {
            Timber.tag("PdfPositionDebug").e("Repository: DB Update Failed! No recent file found matching URI: $uriString")
        }
    }

    @Suppress("unused")
    suspend fun makeBookAvailable(bookId: String, internalUri: Uri) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateBookAvailability(bookId, internalUri.toString(), currentTime)
        Timber.d("Made book available locally: $bookId at URI $internalUri")
    }

    suspend fun markAsNotRecent(bookIds: List<String>) = withContext(Dispatchers.IO) {
        bookIds.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                Timber.d("DeleteDebug: DAO - Marking ${chunk.size} items as not recent.")
                recentFileDao.markAsNotRecent(chunk, System.currentTimeMillis())
            }
        }
    }

    suspend fun markAsDeleted(bookIds: List<String>) = withContext(Dispatchers.IO) {
        bookIds.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                recentFileDao.markAsDeleted(chunk, System.currentTimeMillis())
                Timber.d("DeleteDebug: DAO - Marked ${chunk.size} items as deleted.")
            }
        }
    }

    suspend fun deleteFilePermanently(bookIds: List<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isEmpty()) return@withContext

        bookIds.chunked(900).forEach { chunk ->
            val itemsToRemove = chunk.mapNotNull { recentFileDao.getFileByBookId(it) }

            if (itemsToRemove.isNotEmpty()) {
                Timber.d("DeleteDebug: DAO - Permanently deleting ${itemsToRemove.size} files.")
                itemsToRemove.forEach { item ->
                    item.coverImagePath?.let { deleteCachedCover(it) }
                    try {
                        item.uriString?.let { bookImporter.deleteBookByUriString(it) }
                    } catch (e: Exception) {
                        Timber.w("DeleteDebug: Physical file deletion failed (likely already gone) for ${item.bookId}: ${e.message}")
                    }

                    try {
                        pdfAnnotationRepository.getAnnotationFileForSync(item.bookId)?.delete()
                        pdfRichTextRepository.getFileForSync(item.bookId).delete()
                        pageLayoutRepository.getLayoutFile(item.bookId).delete()
                        pdfTextBoxRepository.getFileForSync(item.bookId).delete()
                        pdfHighlightRepository.getFileForSync(item.bookId).delete()

                        ImportedFileCache.clearBookCache(context, item.bookId)
                    } catch (e: Exception) {
                        Timber.e(e, "Error during deep cleanup of sidecars for ${item.bookId}: ${e.message}")
                    }
                }
                recentFileDao.deleteFilePermanently(itemsToRemove.map { it.bookId })
                Timber.d("Permanently removed recent files from DB.")
            } else {
                Timber.w("DeleteDebug: DAO - Files not found for permanent deletion.")
            }
        }
    }

    private fun getCoverCacheDirInternal(): File {
        if (!coverCacheDir.exists()) {
            coverCacheDir.mkdirs()
        }
        return coverCacheDir
    }

    suspend fun saveCoverToCache(bitmap: Bitmap, uri: Uri): String? = withContext(Dispatchers.IO) {
        val cacheDir = getCoverCacheDirInternal()
        val filename = "cover_${uri.toString().hashCode()}.png"
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

    suspend fun saveEmbeddedCoverToCache(bytes: ByteArray, uri: Uri, extension: String): String? = withContext(Dispatchers.IO) {
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
        val filename = "cover_${uri.toString().hashCode()}.$extension"
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
        val prefix = "cover_${uri.toString().hashCode()}."
        getCoverCacheDirInternal().listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
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

    suspend fun migrateBookIdLocally(oldId: String, newId: String) = withContext(Dispatchers.IO) {
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

    suspend fun clearLocalCachesForBook(bookId: String) = withContext(Dispatchers.IO) {
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

    suspend fun addRecentFiles(items: List<RecentFileItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.chunked(900).forEach { chunk ->
            val entities = chunk.map { it.toRecentFileEntity() }
            recentFileDao.insertOrUpdateFiles(entities)
        }
        Timber.d("Batch inserted/updated ${items.size} recent files in DB.")
    }

    suspend fun migrateLegacyShelvesToRoom() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_shelves_migrated_to_room", false)) return@withContext

        Timber.i("Starting migration of legacy SharedPreferences shelves to Room DB...")

        val shelfNames = prefs.getStringSet("shelf_names", emptySet()) ?: emptySet()
        if (shelfNames.isEmpty()) {
            prefs.edit { putBoolean("is_shelves_migrated_to_room", true) }
            return@withContext
        }

        val db = AppDatabase.getDatabase(context)
        val shelfDao = db.shelfDao()

        val validBookIds = recentFileDao.getAllFiles().map { it.bookId }.toSet()

        shelfNames.forEach { name ->
            val shelfId = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
            val timestamp = prefs.getLong("shelf_timestamp_$name", System.currentTimeMillis())
            val isDeleted = prefs.getBoolean("shelf_deleted_$name", false)
            val bookIds = prefs.getStringSet("shelf_content_$name", emptySet()) ?: emptySet()

            val shelf = ShelfEntity(
                id = shelfId, name = name, isSmart = false, smartRulesJson = null,
                createdAt = timestamp, updatedAt = timestamp, isDeleted = isDeleted
            )
            shelfDao.insertShelf(shelf)

            val crossRefs = bookIds.filter { it in validBookIds }.map { bookId ->
                BookShelfCrossRef(bookId = bookId, shelfId = shelfId, addedAt = timestamp)
            }

            if (crossRefs.isNotEmpty()) {
                shelfDao.insertBookShelfCrossRefs(crossRefs)
            }
        }

        prefs.edit { putBoolean("is_shelves_migrated_to_room", true) }
        Timber.i("Successfully migrated legacy shelves to Room.")
    }
}
