package com.aryan.reader.shared

/** Portable inventory of PDF sidecars. Platforms only discover and execute these artifacts. */
data class PdfAnnotationArtifactInventory(
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

enum class PdfAnnotationSyncOperation {
    UPLOAD_LOCAL,
    DOWNLOAD_REMOTE,
    NONE
}

data class PdfAnnotationSyncInput(
    val local: PdfAnnotationArtifactInventory,
    val remoteHasAnnotations: Boolean,
    val remoteModifiedTimestamp: Long
)

object PdfCloudAnnotationSyncPlanner {
    fun plan(input: PdfAnnotationSyncInput): PdfAnnotationSyncOperation = when {
        input.local.hasAnnotationPayload &&
            (!input.remoteHasAnnotations ||
                input.local.annotationPayloadTimestamp > input.remoteModifiedTimestamp) ->
            PdfAnnotationSyncOperation.UPLOAD_LOCAL

        input.remoteHasAnnotations &&
            (!input.local.hasAnnotationPayload ||
                input.remoteModifiedTimestamp > input.local.annotationPayloadTimestamp) ->
            PdfAnnotationSyncOperation.DOWNLOAD_REMOTE

        else -> PdfAnnotationSyncOperation.NONE
    }
}
