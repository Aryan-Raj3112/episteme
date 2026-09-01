package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.DriveFile
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.sharedCloudBookContentFileName
import java.util.Locale

/**
 * Builds the immutable part of a cloud-book delete before any remote request
 * is made. Keeping this policy pure makes duplicate Drive objects and legacy
 * metadata behavior testable without an Android/Google SDK.
 */
internal fun cloudBookDeletionMetadata(
    tombstone: CloudBookTombstone,
    remote: BookMetadata?,
    nowMillis: Long,
): BookMetadata {
    val fallback = BookMetadata(
        bookId = tombstone.bookId,
        type = tombstone.type.orEmpty(),
    )
    val metadata = remote ?: fallback
    val resolvedType = cloudBookDeletionType(tombstone, remote)?.name ?: FileType.UNKNOWN.name
    return metadata.copy(
        type = resolvedType,
        isDeleted = true,
        lastModifiedTimestamp = maxOf(
            tombstone.deletedAt,
            metadata.lastModifiedTimestamp,
            nowMillis,
        ),
    )
}

internal fun cloudBookDeletionPayloadIds(
    tombstone: CloudBookTombstone,
    remote: BookMetadata?,
    remoteFilesByName: Map<String, List<DriveFile>>,
): List<String> {
    val type = cloudBookDeletionType(tombstone, remote)
    val contentIds = type
        ?.let { sharedCloudBookContentFileName(tombstone.bookId, it) }
        ?.let { remoteFilesByName[it].orEmpty().map(DriveFile::id) }
        .orEmpty()
    val annotationIds = remoteFilesByName[cloudPdfAnnotationDriveFileName(tombstone.bookId)]
        .orEmpty()
        .map(DriveFile::id)
    return (contentIds + annotationIds).distinct()
}

/**
 * Resolve only a known app file type. Unknown values from old/corrupt
 * metadata are deliberately not passed to [FileType.valueOf] by callers.
 */
internal fun cloudBookDeletionType(
    tombstone: CloudBookTombstone,
    remote: BookMetadata?,
): FileType? = sequenceOf(remote?.type, tombstone.type)
    .mapNotNull { value ->
        value?.trim()?.takeIf(String::isNotBlank)
            ?.let { runCatching { FileType.valueOf(it.uppercase(Locale.ROOT)) }.getOrNull() }
    }
    .firstOrNull()
    ?.takeUnless { it == FileType.UNKNOWN }
