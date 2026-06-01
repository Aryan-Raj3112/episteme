package com.aryan.reader

import android.util.Log
import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.effectiveAnnotationModifiedTimestamp
import com.aryan.reader.data.effectiveReadingPositionModifiedTimestamp
import timber.log.Timber

internal const val CloudSyncTraceTag = "EpistemeCloudSync"
internal const val CloudAnnotationSyncTraceTag = "EpistemeCloudAnnotations"

internal fun logCloudSyncTrace(message: () -> String) {
    if (!BuildConfig.DEBUG) return
    val text = message()
    Log.d(CloudSyncTraceTag, text)
    Timber.tag(CloudSyncTraceTag).d(text)
}

internal fun logCloudSyncError(error: Throwable, message: () -> String) {
    if (!BuildConfig.DEBUG) return
    val text = message()
    Log.e(CloudSyncTraceTag, text, error)
    Timber.tag(CloudSyncTraceTag).e(error, text)
}

internal fun logCloudAnnotationSyncTrace(message: () -> String) {
    if (!BuildConfig.DEBUG) return
    val text = message()
    Log.d(CloudAnnotationSyncTraceTag, text)
    Timber.tag(CloudAnnotationSyncTraceTag).d(text)
}

internal fun logCloudAnnotationSyncError(error: Throwable, message: () -> String) {
    if (!BuildConfig.DEBUG) return
    val text = message()
    Log.e(CloudAnnotationSyncTraceTag, text, error)
    Timber.tag(CloudAnnotationSyncTraceTag).e(error, text)
}

internal fun RecentFileItem.cloudSyncTraceSummary(prefix: String = "local"): String {
    return "$prefix{id=$bookId type=$type ts=$lastModifiedTimestamp readTs=${effectiveReadingPositionModifiedTimestamp()} " +
        "contentTs=$fileContentModifiedTimestamp " +
        "page=$lastPage chapter=$lastChapterIndex block=$locatorBlockIndex char=$locatorCharOffset " +
        "progress=$progressPercentage cfi=${lastPositionCfi.cloudSyncPreview()} deleted=$isDeleted recent=$isRecent " +
        "bookmarks=${bookmarksJson.cloudSyncAnnotationSummary()} highlights=${highlightsJson.cloudSyncAnnotationSummary()}}"
}

internal fun BookMetadata.cloudSyncTraceSummary(prefix: String = "remote"): String {
    return "$prefix{id=$bookId type=$type ts=$lastModifiedTimestamp readTs=${effectiveReadingPositionModifiedTimestamp()} " +
        "annTs=${effectiveAnnotationModifiedTimestamp()} contentTs=$fileContentModifiedTimestamp " +
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
