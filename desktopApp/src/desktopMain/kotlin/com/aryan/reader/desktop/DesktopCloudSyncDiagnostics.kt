package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedFileCapabilities

internal const val DesktopCloudSyncLogTag = "EpistemeCloudSync"
internal const val DesktopCloudAnnotationSyncLogTag = "EpistemeCloudAnnotations"

internal fun logDesktopCloudSync(message: () -> String) {
    logDesktopDiagnostic(DesktopCloudSyncLogTag, message)
}

internal fun logDesktopCloudAnnotations(message: () -> String) {
    logDesktopDiagnostic(DesktopCloudAnnotationSyncLogTag, message)
}

internal fun BookItem.desktopCloudSyncSummary(prefix: String = "local"): String {
    val position = readerPosition
    val page = if (type.usesCloudLocatorForDiagnostics()) {
        position?.pageIndex ?: lastPageIndex
    } else {
        lastPageIndex
    }
    return "$prefix{id=$id type=$type ts=$timestamp readTs=${effectiveCloudReadingPositionModifiedTimestamp()} " +
        "contentTs=$fileContentModifiedTimestamp " +
        "page=$page chapter=${position?.chapterIndex} " +
        "block=${position?.blockIndex} char=${position?.charOffset} progress=$progressPercentage " +
        "cfi=${position?.cfi.cloudSyncPreview()} sourceFolder=${sourceFolder != null} " +
        "bookmarks=${readerBookmarks.size} highlights=${readerHighlights.size}}"
}

internal fun DesktopCloudBookMetadata.desktopCloudSyncSummary(prefix: String = "remote"): String {
    return "$prefix{id=$bookId type=$type ts=$lastModifiedTimestamp readTs=${effectiveCloudReadingPositionModifiedTimestamp()} " +
        "annTs=${effectiveCloudAnnotationModifiedTimestamp()} contentTs=$fileContentModifiedTimestamp " +
        "page=$lastPage chapter=$lastChapterIndex block=$locatorBlockIndex char=$locatorCharOffset " +
        "progress=$progressPercentage cfi=${lastPositionCfi.cloudSyncPreview()} deleted=$isDeleted " +
        "recent=$isRecent hasAnnotations=$hasAnnotations bookmarks=${bookmarksJson.cloudSyncAnnotationSummary()} " +
        "highlights=${highlightsJson.cloudSyncAnnotationSummary()}}"
}

internal fun BookItem.hasSameCloudReaderPosition(other: BookItem): Boolean {
    val thisPage = if (type.usesCloudLocatorForDiagnostics()) readerPosition?.pageIndex ?: lastPageIndex else lastPageIndex
    val otherPage = if (other.type.usesCloudLocatorForDiagnostics()) {
        other.readerPosition?.pageIndex ?: other.lastPageIndex
    } else {
        other.lastPageIndex
    }
    val thisProgress = progressPercentage
    val otherProgress = other.progressPercentage
    val progressMatches = when {
        thisProgress == null && otherProgress == null -> true
        thisProgress != null && otherProgress != null -> kotlin.math.abs(thisProgress - otherProgress) < 0.001f
        else -> false
    }
    val locatorMatches = if (type.usesCloudLocatorForDiagnostics() || other.type.usesCloudLocatorForDiagnostics()) {
        readerPosition == other.readerPosition
    } else {
        true
    }
    return thisPage == otherPage &&
        locatorMatches &&
        progressMatches
}

private fun com.aryan.reader.shared.FileType.usesCloudLocatorForDiagnostics(): Boolean {
    return this != FileType.PDF && this != FileType.PPTX && !SharedFileCapabilities.isComicArchive(this)
}

private fun String?.cloudSyncPreview(maxLength: Int = 80): String {
    val value = this ?: return "null"
    return if (value.length <= maxLength) value else value.take(maxLength) + "..."
}

private fun String?.cloudSyncAnnotationSummary(): String {
    val value = this?.trim() ?: return "null"
    return when {
        value.isEmpty() -> "blank"
        value == "[]" -> "empty"
        else -> "present(${value.length})"
    }
}
