// FolderBookMetadata.kt
package com.aryan.reader.data

import com.aryan.reader.FileType
import com.aryan.reader.shared.SharedFolderBookMetadata

data class FolderBookMetadata(
    val bookId: String,
    val title: String?,
    val author: String?,
    val displayName: String,
    val type: String,
    val lastChapterIndex: Int?,
    val lastPage: Int?,
    val lastPositionCfi: String?,
    val progressPercentage: Float,
    val isRecent: Boolean,
    val lastModifiedTimestamp: Long,
    val bookmarksJson: String?,
    val locatorBlockIndex: Int?,
    val locatorCharOffset: Int?,
    val customName: String?,
    val highlightsJson: String?,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val originalSeriesName: String? = null,
    val originalSeriesIndex: Double? = null,
    val originalDescription: String? = null,
    val schemaVersion: Int = com.aryan.reader.shared.LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION,
    val presentFields: Set<String> = emptySet(),
) {
    fun hasExplicitField(name: String): Boolean =
        schemaVersion >= com.aryan.reader.shared.LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION &&
            name in presentFields

    fun toJsonString(): String {
        return toSharedFolderBookMetadata().toJsonString()
    }

    companion object {
        fun fromJsonString(jsonString: String): FolderBookMetadata {
            return SharedFolderBookMetadata.fromJsonString(jsonString)
                ?.toFolderBookMetadata()
                ?: error("Invalid folder metadata JSON")
        }
    }
}

fun FolderBookMetadata.toSharedFolderBookMetadata(): SharedFolderBookMetadata {
    return SharedFolderBookMetadata(
        bookId = bookId,
        title = title,
        author = author,
        displayName = displayName,
        type = type,
        lastChapterIndex = lastChapterIndex,
        lastPage = lastPage,
        lastPositionCfi = lastPositionCfi,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        lastModifiedTimestamp = lastModifiedTimestamp,
        bookmarksJson = bookmarksJson,
        locatorBlockIndex = locatorBlockIndex,
        locatorCharOffset = locatorCharOffset,
        customName = customName,
        highlightsJson = highlightsJson,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription,
        schemaVersion = schemaVersion,
        presentFields = presentFields,
    )
}

fun SharedFolderBookMetadata.toFolderBookMetadata(): FolderBookMetadata {
    return FolderBookMetadata(
        bookId = bookId,
        title = title,
        author = author,
        displayName = displayName,
        type = type,
        lastChapterIndex = lastChapterIndex,
        lastPage = lastPage,
        lastPositionCfi = lastPositionCfi,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        lastModifiedTimestamp = lastModifiedTimestamp,
        bookmarksJson = bookmarksJson,
        locatorBlockIndex = locatorBlockIndex,
        locatorCharOffset = locatorCharOffset,
        customName = customName,
        highlightsJson = highlightsJson,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription,
        schemaVersion = schemaVersion,
        presentFields = presentFields,
    )
}

fun FolderBookMetadata.toRecentFileItem(uriString: String?, coverPath: String?, sourceFolderUri: String?): RecentFileItem {
    return RecentFileItem(
        bookId = this.bookId,
        uriString = uriString,
        type = try { FileType.valueOf(this.type) } catch (_: Exception) { FileType.EPUB },
        displayName = this.displayName,
        timestamp = System.currentTimeMillis(),
        coverImagePath = coverPath,
        title = this.title ?: this.displayName.substringBeforeLast('.', this.displayName),
        author = this.author,
        lastChapterIndex = this.lastChapterIndex,
        lastPage = this.lastPage,
        lastPositionCfi = this.lastPositionCfi,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isAvailable = true,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        isDeleted = false,
        bookmarksJson = this.bookmarksJson,
        sourceFolderUri = sourceFolderUri,
        customName = this.customName,
        highlightsJson = this.highlightsJson,
        seriesName = this.seriesName,
        seriesIndex = this.seriesIndex,
        description = this.description,
        originalTitle = this.originalTitle,
        originalAuthor = this.originalAuthor,
        originalSeriesName = this.originalSeriesName,
        originalSeriesIndex = this.originalSeriesIndex,
        originalDescription = this.originalDescription
    )
}
