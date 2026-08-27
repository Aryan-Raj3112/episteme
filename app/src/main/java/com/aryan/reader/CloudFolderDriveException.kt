package com.aryan.reader

import java.io.IOException

/**
 * Safe description of a Drive transfer failure. The original HTTP exception
 * remains the cause for retry/debugging, but its message/body is never shown
 * in the UI or emitted through the cloud-folder log tag.
 */
class CloudFolderDriveException(
    val httpStatusCode: Int?,
    val bodyCategory: String,
    val statusCategory: String,
    val driveReason: String = "unknown",
    val driveDomain: String = "unknown",
    val driveErrorCode: Int? = null,
    val retryAfterKind: String = "none",
    val retryAfterSeconds: Long? = null,
    val requestIdHeader: String? = null,
    val requestIdHash: String? = null,
    val operationStage: String? = null,
    val operationType: String? = null,
    val uploadMode: String? = null,
    val sizeBucket: String? = null,
    val attempt: Int? = null,
    cause: Throwable? = null,
) : IOException(
    "Cloud Drive request failed ($statusCategory)",
    cause,
)

/**
 * Safe context for a failure outside the Drive API itself (for example a
 * local temp-file or payload-verification failure).  The message deliberately
 * contains only bounded diagnostic categories; callers must not put paths,
 * filenames, URIs, or Drive IDs in this exception.
 */
class CloudFolderTransferException(
    stage: String,
    category: String,
    statusCategory: String = "unknown",
    cause: Throwable? = null,
) : IOException(
    "Cloud-folder transfer failed (stage=${sanitizeTransferDiagnosticValue(stage)} " +
        "category=${sanitizeTransferDiagnosticValue(category)} status=${sanitizeTransferDiagnosticValue(statusCategory)})",
    cause,
) {
    val stage: String = sanitizeTransferDiagnosticValue(stage)
    val category: String = sanitizeTransferDiagnosticValue(category)
    val statusCategory: String = sanitizeTransferDiagnosticValue(statusCategory)
}

private fun sanitizeTransferDiagnosticValue(value: String): String =
    value.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .take(64)
        .ifBlank { "unknown" }
