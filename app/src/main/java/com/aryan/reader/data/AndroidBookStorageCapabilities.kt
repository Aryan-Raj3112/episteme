package com.aryan.reader.data

import android.graphics.Bitmap
import android.net.Uri
import com.aryan.reader.paginatedreader.Locator
import kotlinx.coroutines.flow.Flow

/** Transaction-shaped Android persistence capabilities consumed by feature coordinators. */
interface AndroidBookStore {
    fun getRecentFilesFlow(): Flow<List<RecentFileItem>>
    suspend fun getFileByBookId(bookId: String): RecentFileItem?
    suspend fun getFileByUri(uriString: String): RecentFileItem?
    suspend fun getFilesBySourceFolder(sourceFolderUri: String): List<RecentFileItem>
    suspend fun getAllFilesForSync(): List<RecentFileItem>
    suspend fun addRecentFile(item: RecentFileItem)
    suspend fun addRecentFiles(items: List<RecentFileItem>)
    suspend fun updateUserEditableMetadata(
        bookId: String,
        metadata: BookMetadataEdit,
        fileSize: Long = 0L,
        fileContentModifiedTimestamp: Long = 0L,
        coverImagePath: String? = null,
    )
    suspend fun updateCustomName(bookId: String, customName: String?)
    suspend fun restoreOriginalMetadata(
        bookId: String,
        fileSize: Long = 0L,
        fileContentModifiedTimestamp: Long = 0L,
        coverImagePath: String? = null,
    )
    suspend fun updateHighlights(bookId: String, highlightsJson: String)
    suspend fun updateEpubReadingPosition(uriString: String, locator: Locator, cfiForWebView: String?, progress: Float)
    suspend fun updateBookmarks(bookId: String, bookmarksJson: String)
    suspend fun updatePdfReadingPosition(uriString: String, page: Int, progress: Float)
    suspend fun makeBookAvailable(bookId: String, internalUri: Uri)
    suspend fun markAsNotRecent(bookIds: List<String>)
    suspend fun markAsDeleted(bookIds: List<String>)
    suspend fun deleteFilePermanently(bookIds: List<String>)
}

interface AndroidFolderMirrorStore {
    /** Returns true when the metadata sidecar is present or the operation is a safe no-op. */
    suspend fun syncLocalMetadataToFolder(bookId: String, force: Boolean = false): Boolean
    /** Returns true only after the merged sidecar has been written and read back successfully. */
    suspend fun syncLocalAnnotationsToFolder(bookId: String): Boolean
    suspend fun importAnnotationBundle(bookId: String, jsonString: String, lastModifiedTimestamp: Long? = null)
    suspend fun deleteFilesBySourceFolder(folderUriString: String)
    suspend fun getFolderBooksNeedingTextMetadata(
        sourceFolderUri: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<RecentFileItem>
    suspend fun hasFolderBooksNeedingTextMetadata(sourceFolderUri: String? = null): Boolean
    suspend fun updateExtractedMetadata(items: List<RecentFileItem>)
    suspend fun detachAllFolderBooks()
}

interface AndroidBookArtifactStore {
    suspend fun clearAllLocalData()
    suspend fun saveCoverToCache(bitmap: Bitmap, uri: Uri): String?
    suspend fun saveEmbeddedCoverToCache(bytes: ByteArray, uri: Uri, extension: String): String?
    suspend fun clearLocalCachesForBook(bookId: String)
}

interface AndroidLegacyMigrationStore {
    suspend fun migrateBookIdLocally(oldId: String, newId: String)
}
