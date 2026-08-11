package com.aryan.reader

import java.io.File
import com.aryan.reader.shared.PdfAnnotationArtifactInventory
import com.aryan.reader.shared.PdfAnnotationSyncInput
import com.aryan.reader.shared.PdfAnnotationSyncOperation
import com.aryan.reader.shared.PdfCloudAnnotationSyncPlanner

internal typealias AndroidPdfCloudSidecarState = PdfAnnotationArtifactInventory

internal fun androidPdfCloudSidecarInventory(
    inkFile: File?,
    deletedInkFile: File?,
    richTextFile: File,
    layoutFile: File,
    textBoxFile: File,
    highlightFile: File
) = AndroidPdfCloudSidecarState(
    hasInk = inkFile.hasSyncableCloudAnnotationPayload(),
    inkTimestamp = inkFile?.lastModified() ?: 0L,
    hasDeletedInk = deletedInkFile.hasSyncableCloudAnnotationPayload(),
    deletedInkTimestamp = deletedInkFile?.lastModified() ?: 0L,
    hasRichText = richTextFile.hasSyncableCloudAnnotationPayload(),
    richTextTimestamp = richTextFile.lastModified(),
    hasLayout = layoutFile.exists(),
    layoutTimestamp = layoutFile.lastModified(),
    hasTextBoxes = textBoxFile.hasSyncableCloudAnnotationPayload(),
    textBoxesTimestamp = textBoxFile.lastModified(),
    hasHighlights = highlightFile.hasSyncableCloudAnnotationPayload(),
    highlightsTimestamp = highlightFile.lastModified()
)

internal fun shouldUploadLocalPdfCloudAnnotations(
    localSidecars: AndroidPdfCloudSidecarState,
    remoteHasAnnotations: Boolean,
    remoteAnnotationModifiedTimestamp: Long
): Boolean {
    return PdfCloudAnnotationSyncPlanner.plan(
        PdfAnnotationSyncInput(localSidecars, remoteHasAnnotations, remoteAnnotationModifiedTimestamp)
    ) == PdfAnnotationSyncOperation.UPLOAD_LOCAL
}

internal fun shouldDownloadRemotePdfCloudAnnotations(
    localSidecars: AndroidPdfCloudSidecarState,
    localAnnotationsShouldUpload: Boolean,
    remoteHasAnnotations: Boolean,
    remoteAnnotationModifiedTimestamp: Long
): Boolean {
    if (localAnnotationsShouldUpload || !remoteHasAnnotations) return false
    return PdfCloudAnnotationSyncPlanner.plan(
        PdfAnnotationSyncInput(localSidecars, remoteHasAnnotations, remoteAnnotationModifiedTimestamp)
    ) == PdfAnnotationSyncOperation.DOWNLOAD_REMOTE
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
