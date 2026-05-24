package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.RecentFileItem
import timber.log.Timber

internal const val CloudSyncTraceTag = "EpistemeCloudSync"

internal fun logCloudSyncTrace(message: () -> String) {
    Timber.tag(CloudSyncTraceTag).d(message())
}

internal fun logCloudSyncError(error: Throwable, message: () -> String) {
    Timber.tag(CloudSyncTraceTag).e(error, message())
}

internal fun RecentFileItem.cloudSyncTraceSummary(prefix: String = "local"): String {
    return "$prefix{id=$bookId type=$type ts=$lastModifiedTimestamp contentTs=$fileContentModifiedTimestamp " +
        "page=$lastPage chapter=$lastChapterIndex block=$locatorBlockIndex char=$locatorCharOffset " +
        "progress=$progressPercentage cfi=${lastPositionCfi.cloudSyncPreview()} deleted=$isDeleted recent=$isRecent " +
        "bookmarks=${bookmarksJson.cloudSyncAnnotationSummary()} highlights=${highlightsJson.cloudSyncAnnotationSummary()}}"
}

internal fun BookMetadata.cloudSyncTraceSummary(prefix: String = "remote"): String {
    return "$prefix{id=$bookId type=$type ts=$lastModifiedTimestamp contentTs=$fileContentModifiedTimestamp " +
        "page=$lastPage chapter=$lastChapterIndex block=$locatorBlockIndex char=$locatorCharOffset " +
        "progress=$progressPercentage cfi=${lastPositionCfi.cloudSyncPreview()} deleted=$isDeleted recent=$isRecent " +
        "hasAnnotations=$hasAnnotations bookmarks=${bookmarksJson.cloudSyncAnnotationSummary()} " +
        "highlights=${highlightsJson.cloudSyncAnnotationSummary()}}"
}

internal fun String?.cloudSyncPreview(maxLength: Int = 80): String {
    val value = this ?: return "null"
    return if (value.length <= maxLength) value else value.take(maxLength) + "..."
}

internal fun String?.cloudSyncAnnotationSummary(): String {
    val value = this?.trim() ?: return "null"
    return when {
        value.isEmpty() -> "blank"
        value == "[]" -> "empty"
        else -> "present(${value.length})"
    }
}
