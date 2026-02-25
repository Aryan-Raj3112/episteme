/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import timber.log.Timber
import com.aryan.reader.BookImporter
import com.aryan.reader.paginatedreader.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val COVER_CACHE_DIR = "cover_cache"

class RecentFilesRepository(context: Context) {

    private val recentFileDao = AppDatabase.getDatabase(context).recentFileDao()
    private val coverCacheDir = File(context.filesDir, COVER_CACHE_DIR)
    private val bookImporter = BookImporter(context)

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
                item.uriString?.let { bookImporter.deleteBookByUriString(it) }
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