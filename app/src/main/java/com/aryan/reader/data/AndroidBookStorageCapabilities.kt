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
    /**
     * Ids only. For maintenance tasks that need the id set and nothing else:
     * avoids the full-table `SELECT *` plus its mapped copy that
     * [getAllFilesForSync] builds.
     */
    suspend fun getAllBookIdsForMaintenance(): Set<String>
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
    /**
     * Writes the position row addressed by [bookId] (the Room primary key the caller already holds).
     * Returns true when a row was updated. Callers must not need a read-back to learn the outcome:
     * the write path is per page turn, so a verifying `SELECT *` here is a per-turn full-row read.
     */
    suspend fun updateEpubReadingPosition(bookId: String, locator: Locator, cfiForWebView: String?, progress: Float): Boolean
    suspend fun updateBookmarks(bookId: String, bookmarksJson: String)
    /** Writes the PDF position row addressed by [bookId]; see [updateEpubReadingPosition]. */
    suspend fun updatePdfReadingPosition(bookId: String, page: Int, progress: Float): Boolean
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
    /**
     * Persists a generated thumbnail and returns its path. [previousPath] is the path the row
     * currently references and is removed once the replacement is written; pass null when unknown.
     */
    suspend fun saveCoverToCache(bitmap: Bitmap, uri: Uri, previousPath: String? = null): String?
    suspend fun saveEmbeddedCoverToCache(
        bytes: ByteArray,
        uri: Uri,
        extension: String,
        previousPath: String? = null,
    ): String?
    /** Deletes cover files no library row references; returns how many were removed. */
    suspend fun pruneCoverCache(): Int
    suspend fun clearLocalCachesForBook(bookId: String)
}

interface AndroidLegacyMigrationStore {
    suspend fun migrateBookIdLocally(oldId: String, newId: String)
}
