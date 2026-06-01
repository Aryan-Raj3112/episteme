package com.aryan.reader

import java.io.File

internal data class AndroidPdfCloudSidecarState(
    val hasInk: Boolean,
    val inkTimestamp: Long,
    val hasDeletedInk: Boolean = false,
    val deletedInkTimestamp: Long = 0L,
    val hasRichText: Boolean,
    val richTextTimestamp: Long,
    val hasLayout: Boolean,
    val layoutTimestamp: Long,
    val hasTextBoxes: Boolean,
    val textBoxesTimestamp: Long,
    val hasHighlights: Boolean,
    val highlightsTimestamp: Long
) {
    val hasAnnotationPayload: Boolean
        get() = hasInk || hasDeletedInk || hasRichText || hasTextBoxes || hasHighlights

    val annotationPayloadTimestamp: Long
        get() = maxOf(
            inkTimestamp.takeIf { hasInk } ?: 0L,
            deletedInkTimestamp.takeIf { hasDeletedInk } ?: 0L,
            richTextTimestamp.takeIf { hasRichText } ?: 0L,
            textBoxesTimestamp.takeIf { hasTextBoxes } ?: 0L,
            highlightsTimestamp.takeIf { hasHighlights } ?: 0L
        )

    val bundleTimestamp: Long
        get() = if (hasAnnotationPayload) {
            maxOf(annotationPayloadTimestamp, layoutTimestamp.takeIf { hasLayout } ?: 0L)
        } else {
            0L
        }
}

internal fun shouldUploadLocalPdfCloudAnnotations(
    localSidecars: AndroidPdfCloudSidecarState,
    remoteHasAnnotations: Boolean,
    remoteAnnotationModifiedTimestamp: Long
): Boolean {
    return localSidecars.hasAnnotationPayload &&
        (!remoteHasAnnotations || localSidecars.annotationPayloadTimestamp > remoteAnnotationModifiedTimestamp)
}

internal fun shouldDownloadRemotePdfCloudAnnotations(
    localSidecars: AndroidPdfCloudSidecarState,
    localAnnotationsShouldUpload: Boolean,
    remoteHasAnnotations: Boolean,
    remoteAnnotationModifiedTimestamp: Long
): Boolean {
    if (localAnnotationsShouldUpload || !remoteHasAnnotations) return false
    return !localSidecars.hasAnnotationPayload ||
        remoteAnnotationModifiedTimestamp > localSidecars.annotationPayloadTimestamp
}

internal fun File?.hasSyncableCloudAnnotationPayload(): Boolean {
    val file = this ?: return false
    if (!file.isFile || file.length() <= 0L) return false
    val trimmed = runCatching { file.readText().trim() }.getOrDefault("")
    return trimmed.isNotBlank() && trimmed != "[]" && trimmed != "{}"
}

internal fun markPdfCloudAnnotationSidecarsSynced(timestamp: Long, vararg files: File?) {
    if (timestamp <= 0L) return
    files.forEach { file ->
        if (file?.exists() == true) {
            file.setLastModified(timestamp)
        }
    }
}

internal fun cloudPdfAnnotationDriveFileName(bookId: String): String = "annotation_$bookId.json"
