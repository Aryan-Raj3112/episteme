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
import android.net.Uri
import androidx.core.net.toUri
import timber.log.Timber
import com.aryan.reader.BookImporter
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.pdf.PdfRichTextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import org.json.JSONObject
import org.json.JSONArray

private const val COVER_CACHE_DIR = "cover_cache"

class RecentFilesRepository(private val context: Context) {

    private val recentFileDao = AppDatabase.getDatabase(context).recentFileDao()
    private val coverCacheDir = File(context.filesDir, COVER_CACHE_DIR)
    private val bookImporter = BookImporter(context)

    private val pdfAnnotationRepository = PdfAnnotationRepository(context)
    private val pdfRichTextRepository = PdfRichTextRepository(context)
    private val pageLayoutRepository = PageLayoutRepository(context)
    private val pdfTextBoxRepository = PdfTextBoxRepository(context)

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

    suspend fun getFilesBySourceFolder(sourceFolderUri: String): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFilesBySourceFolder(sourceFolderUri).map { it.toRecentFileItem() }
    }

    suspend fun getAllFilesForSync(): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getAllFiles().map { it.toRecentFileItem() }
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        recentFileDao.clearAll()
        if (coverCacheDir.exists()) {
            coverCacheDir.deleteRecursively()
        }
        coverCacheDir.mkdirs()
        Timber.d("Cleared all local book data and cover cache.")
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
            item.toRecentFileEntity().copy(
                uriString = existingItem.uriString ?: item.uriString,
                isAvailable = existingItem.isAvailable || item.isAvailable,
                coverImagePath = item.coverImagePath ?: existingItem.coverImagePath,
                title = item.title ?: existingItem.title,
                author = item.author ?: existingItem.author,
                lastChapterIndex = item.lastChapterIndex ?: existingItem.lastChapterIndex,
                lastPage = item.lastPage ?: existingItem.lastPage,
                lastPositionCfi = item.lastPositionCfi ?: existingItem.lastPositionCfi,
                locatorBlockIndex = item.locatorBlockIndex ?: existingItem.locatorBlockIndex,
                locatorCharOffset = item.locatorCharOffset ?: existingItem.locatorCharOffset,
                bookmarks = item.bookmarksJson ?: existingItem.bookmarks,
                progressPercentage = item.progressPercentage ?: existingItem.progressPercentage,
                isRecent = item.isRecent,
                isDeleted = item.isDeleted,
                sourceFolderUri = item.sourceFolderUri ?: existingItem.sourceFolderUri
            )
        } else {
            item.toRecentFileEntity()
        }

        Timber.d("SyncDebug:   -> Final entity to insert: uri='${entityToInsert.uriString}', isAvailable=${entityToInsert.isAvailable}, isDeleted=${entityToInsert.isDeleted}, isRecent=${entityToInsert.isRecent}")
        recentFileDao.insertOrUpdateFile(entityToInsert)
        Timber.d("Added/Updated recent file in DB: ${item.displayName}")
    }

    suspend fun syncLocalMetadataToFolder(bookId: String) = withContext(Dispatchers.IO) {
        val entity = recentFileDao.getFileByBookId(bookId) ?: return@withContext
        val folderUriString = entity.sourceFolderUri

        if (folderUriString != null) {
            val hasProgress = (entity.progressPercentage != null && entity.progressPercentage > 0f)
            val hasBookmarks = !entity.bookmarks.isNullOrEmpty() && entity.bookmarks != "[]"
            val isDirty = entity.isRecent || hasProgress || hasBookmarks

            if (!isDirty) {
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
                locatorCharOffset = entity.locatorCharOffset
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

        val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId)
        val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
        val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
        val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)

        val hasInk = inkFile?.exists() == true
        val hasRichText = richTextFile.exists()
        val hasLayout = layoutFile.exists()
        val hasTextBoxes = textBoxFile.exists()

        Timber.tag("FolderAnnotationSync").d("File checks -> hasInk: $hasInk, hasRichText: $hasRichText, hasLayout: $hasLayout, hasTextBoxes: $hasTextBoxes")

        if (!hasInk && !hasRichText && !hasLayout && !hasTextBoxes) {
            Timber.tag("FolderAnnotationSync").d("No annotations found locally for bookId: $bookId. Aborting sync.")
            return@withContext
        }

        val bundleJson = JSONObject()

        fun putJsonSafe(key: String, file: File) {
            try {
                val content = file.readText().trim()
                if (content.startsWith("[")) {
                    bundleJson.put(key, JSONArray(content))
                } else if (content.startsWith("{")) {
                    bundleJson.put(key, JSONObject(content))
                }
            } catch (e: Exception) {
                Timber.tag("FolderAnnotationSync").e(e, "Error parsing $key file")
            }
        }

        if (hasInk) putJsonSafe("ink", inkFile)
        if (hasRichText) putJsonSafe("text", richTextFile)
        if (hasLayout) putJsonSafe("layout", layoutFile)
        if (hasTextBoxes) putJsonSafe("textBoxes", textBoxFile)

        val tsInk = if(hasInk) inkFile.lastModified() else 0L
        val tsText = if(hasRichText) richTextFile.lastModified() else 0L
        val tsLayout = if(hasLayout) layoutFile.lastModified() else 0L
        val tsBox = if(hasTextBoxes) textBoxFile.lastModified() else 0L

        val maxFileTs = maxOf(tsInk, tsText, tsLayout, tsBox)
        val finalTs = maxOf(maxFileTs, System.currentTimeMillis())

        Timber.tag("FolderAnnotationSync").d("Pushing annotation bundle for $bookId to folder. finalTs=$finalTs")

        LocalSyncUtils.saveAnnotationSidecar(
            context = context,
            sourceFolderUri = folderUriString.toUri(),
            bookId = bookId,
            jsonPayload = bundleJson.toString(),
            timestamp = finalTs
        )
    }

    suspend fun importAnnotationBundle(bookId: String, jsonString: String) = withContext(Dispatchers.IO) {
        Timber.tag("FolderAnnotationSync").d("importAnnotationBundle: Processing bundle for $bookId")
        try {
            val bundle = JSONObject(jsonString)

            fun writeSafe(key: String, file: File?) {
                if (file != null && bundle.has(key)) {
                    file.parentFile?.mkdirs()
                    val contentStr = bundle.get(key).toString()
                    file.writeText(contentStr)
                    Timber.tag("FolderAnnotationSync").v("   -> Updated $key file (${contentStr.length} chars)")
                }
            }

            // 1. Ink
            val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId) ?: File(
                context.filesDir, "annotations/annotation_$bookId.json"
            )
            writeSafe("ink", inkFile)

            // 2. Text
            writeSafe("text", pdfRichTextRepository.getFileForSync(bookId))

            // 3. Layout
            writeSafe("layout", pageLayoutRepository.getLayoutFile(bookId))

            // 4. Text Boxes
            writeSafe("textBoxes", pdfTextBoxRepository.getFileForSync(bookId))

            Timber.tag("FolderAnnotationSync").i("Successfully imported annotation bundle for $bookId from folder.")
        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Failed to import annotation bundle for $bookId")
        }
    }

    suspend fun deleteFilesBySourceFolder(folderUriString: String) = withContext(Dispatchers.IO) {
        recentFileDao.deleteFilesBySourceFolder(folderUriString)
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

    suspend fun getFolderBooksWithoutCovers(): List<RecentFileItem> = withContext(Dispatchers.IO) {
        return@withContext recentFileDao.getFolderBooksWithoutCovers().map { it.toRecentFileItem() }
    }

    suspend fun detachAllFolderBooks() = withContext(Dispatchers.IO) {
        recentFileDao.detachAllFolderBooks()
        Timber.d("Detached all folder books. They are now standard local files.")
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
            Timber.d("Updated PDF reading position for ${item.bookId} to page $page, progress $progress%")
        }
    }

    @Suppress("unused")
    suspend fun makeBookAvailable(bookId: String, internalUri: Uri) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        recentFileDao.updateBookAvailability(bookId, internalUri.toString(), currentTime)
        Timber.d("Made book available locally: $bookId at URI $internalUri")
    }

    suspend fun markAsNotRecent(bookIds: List<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isNotEmpty()) {
            Timber.d("DeleteDebug: DAO - Marking ${bookIds.size} items as not recent.")
            recentFileDao.markAsNotRecent(bookIds, System.currentTimeMillis())
        }
    }

    suspend fun markAsDeleted(bookIds: List<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isNotEmpty()) {
            recentFileDao.markAsDeleted(bookIds, System.currentTimeMillis())
            Timber.d("DeleteDebug: DAO - Marked ${bookIds.size} items as deleted.")
        }
    }

    suspend fun deleteFilePermanently(bookIds: List<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isEmpty()) return@withContext

        val itemsToRemove = bookIds.mapNotNull { recentFileDao.getFileByBookId(it) }

        if (itemsToRemove.isNotEmpty()) {
            Timber.d("DeleteDebug: DAO - Permanently deleting ${itemsToRemove.size} files.")
            itemsToRemove.forEach { item ->
                item.coverImagePath?.let { deleteCachedCover(it) }
                try {
                    item.uriString?.let { bookImporter.deleteBookByUriString(it) }
                } catch (e: Exception) {
                    Timber.w("DeleteDebug: Physical file deletion failed (likely already gone) for ${item.bookId}: ${e.message}")
                }
            }
            recentFileDao.deleteFilePermanently(itemsToRemove.map { it.bookId })
            Timber.d("Permanently removed recent files from DB.")
        } else {
            Timber.w("DeleteDebug: DAO - Files not found for permanent deletion.")
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
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
            Timber.d("Saved cover image to: ${file.absolutePath}")
            return@withContext file.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to save cover image to cache for $uri")
            file.delete()
            return@withContext null
        } finally {
            fos?.close()
        }
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
}