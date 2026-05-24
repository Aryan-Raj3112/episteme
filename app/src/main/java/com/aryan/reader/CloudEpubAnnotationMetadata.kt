package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.RecentFileItem

internal fun RecentFileItem.needsRemoteEpubAnnotationMetadataGuard(): Boolean {
    return type in EPUB_READER_FILE_TYPES &&
        (bookmarksJson.isNullOrBlank() || highlightsJson.isNullOrBlank())
}

internal fun RecentFileItem.mergeRemoteEpubAnnotationMetadata(remote: BookMetadata?): RecentFileItem {
    if (remote == null || remote.isDeleted || type !in EPUB_READER_FILE_TYPES || !remote.isEpubReaderMetadata()) {
        return this
    }
    val nextBookmarks = if (bookmarksJson.isNullOrBlank() && remote.bookmarksJson.hasCloudAnnotationPayload()) {
        remote.bookmarksJson
    } else {
        bookmarksJson
    }
    val nextHighlights = if (highlightsJson.isNullOrBlank() && remote.highlightsJson.hasCloudAnnotationPayload()) {
        remote.highlightsJson
    } else {
        highlightsJson
    }
    if (nextBookmarks == bookmarksJson && nextHighlights == highlightsJson) return this
    return copy(
        bookmarksJson = nextBookmarks,
        highlightsJson = nextHighlights
    )
}

private fun BookMetadata.isEpubReaderMetadata(): Boolean {
    val remoteType = runCatching { FileType.valueOf(type) }.getOrNull() ?: return false
    return remoteType in EPUB_READER_FILE_TYPES
}

internal fun String?.hasCloudAnnotationPayload(): Boolean {
    val normalized = this?.trim().orEmpty()
    return normalized.isNotEmpty() && normalized != "[]"
}

internal fun annotationJsonEquivalentForNoop(existing: String?, incoming: String): Boolean {
    val existingNormalized = existing?.trim().orEmpty()
    val incomingNormalized = incoming.trim()
    if (existingNormalized == incomingNormalized) return true
    return existingNormalized.isAnnotationJsonEmpty() && incomingNormalized.isAnnotationJsonEmpty()
}

private fun String.isAnnotationJsonEmpty(): Boolean {
    return isBlank() || this == "[]"
}
