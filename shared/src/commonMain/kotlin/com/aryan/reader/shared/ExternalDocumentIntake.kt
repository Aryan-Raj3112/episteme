package com.aryan.reader.shared

/**
 * The small, platform-neutral contract used when another app hands Reader one
 * or more document URIs.  Native code is responsible for extracting URIs and
 * carrying the platform permission flags; this file owns ordering, identity,
 * de-duplication, and capability validation.
 */
enum class ExternalDocumentAction {
    VIEW,
    SEND,
    SEND_MULTIPLE,
}

enum class ExternalDocumentSource {
    DATA,
    EXTRA_STREAM,
    CLIP_DATA,
    MIXED,
}

enum class ExternalDocumentOpenMode {
    OPEN_SINGLE,
    IMPORT_BATCH,
}

data class ExternalDocumentGrantCapabilities(
    val read: Boolean = false,
    val write: Boolean = false,
    val persistable: Boolean = false,
    val prefix: Boolean = false,
)

/** A candidate as extracted from a native intent, in source order. */
data class ExternalDocumentCandidate(
    val uri: String?,
    val displayName: String? = null,
    val mimeType: String? = null,
    val source: ExternalDocumentSource,
)

/** A validated, stable document identity retained in the original order. */
data class ExternalDocumentIdentity(
    val uri: String,
    val displayName: String? = null,
    val mimeType: String? = null,
    val fileType: FileType,
    val source: ExternalDocumentSource,
)

data class ExternalDocumentIntakeRequest(
    val action: ExternalDocumentAction,
    val source: ExternalDocumentSource,
    val documents: List<ExternalDocumentIdentity>,
    val grantCapabilities: ExternalDocumentGrantCapabilities = ExternalDocumentGrantCapabilities(),
    val openMode: ExternalDocumentOpenMode = if (documents.size == 1) {
        ExternalDocumentOpenMode.OPEN_SINGLE
    } else {
        ExternalDocumentOpenMode.IMPORT_BATCH
    },
)

enum class ExternalDocumentRejectionReason {
    BLANK_URI,
    UNSUPPORTED,
    DUPLICATE,
}

data class ExternalDocumentRejection(
    val candidate: ExternalDocumentCandidate,
    val reason: ExternalDocumentRejectionReason,
)

data class ExternalDocumentIntakeResult(
    val request: ExternalDocumentIntakeRequest?,
    val rejections: List<ExternalDocumentRejection> = emptyList(),
) {
    val documents: List<ExternalDocumentIdentity>
        get() = request?.documents.orEmpty()

    val uris: List<String>
        get() = documents.map { it.uri }

    val isBatch: Boolean
        get() = request?.openMode == ExternalDocumentOpenMode.IMPORT_BATCH
}

object SharedExternalDocumentIntake {
    /**
     * Normalizes native candidates without making assumptions about the
     * platform's URI representation.  The first non-blank occurrence wins;
     * later occurrences are reported as duplicates so callers can surface or
     * log a deterministic rejection without changing the accepted order.
     */
    fun normalize(
        action: ExternalDocumentAction,
        candidates: List<ExternalDocumentCandidate>,
        grantCapabilities: ExternalDocumentGrantCapabilities = ExternalDocumentGrantCapabilities(),
        platform: ReaderPlatform = ReaderPlatform.ANDROID,
    ): ExternalDocumentIntakeResult {
        val seenUris = mutableSetOf<String>()
        val accepted = mutableListOf<ExternalDocumentIdentity>()
        val rejections = mutableListOf<ExternalDocumentRejection>()

        candidates.forEach { candidate ->
            val normalizedUri = candidate.uri?.trim().orEmpty()
            if (normalizedUri.isBlank()) {
                rejections += ExternalDocumentRejection(
                    candidate = candidate,
                    reason = ExternalDocumentRejectionReason.BLANK_URI,
                )
                return@forEach
            }
            if (!seenUris.add(normalizedUri)) {
                rejections += ExternalDocumentRejection(
                    candidate = candidate.copy(uri = normalizedUri),
                    reason = ExternalDocumentRejectionReason.DUPLICATE,
                )
                return@forEach
            }

            val displayName = candidate.displayName?.trim()?.takeIf { it.isNotBlank() }
            val fileType = SharedFileCapabilities.resolveFileTypeForMetadata(
                fileName = displayName ?: normalizedUri,
                mimeType = candidate.mimeType,
            )
            if (fileType == null || !SharedFileCapabilities.canOpen(fileType, platform)) {
                rejections += ExternalDocumentRejection(
                    candidate = candidate.copy(uri = normalizedUri, displayName = displayName),
                    reason = ExternalDocumentRejectionReason.UNSUPPORTED,
                )
                return@forEach
            }

            accepted += ExternalDocumentIdentity(
                uri = normalizedUri,
                displayName = displayName,
                mimeType = candidate.mimeType?.trim()?.takeIf { it.isNotBlank() },
                fileType = fileType,
                source = candidate.source,
            )
        }

        if (accepted.isEmpty()) {
            return ExternalDocumentIntakeResult(
                request = null,
                rejections = rejections,
            )
        }

        val source = accepted.first().source.takeIf { candidateSource ->
            accepted.all { it.source == candidateSource }
        } ?: ExternalDocumentSource.MIXED
        val openMode = if (accepted.size == 1) {
            ExternalDocumentOpenMode.OPEN_SINGLE
        } else {
            ExternalDocumentOpenMode.IMPORT_BATCH
        }
        return ExternalDocumentIntakeResult(
            request = ExternalDocumentIntakeRequest(
                action = action,
                source = source,
                documents = accepted,
                grantCapabilities = grantCapabilities,
                openMode = openMode,
            ),
            rejections = rejections,
        )
    }
}
