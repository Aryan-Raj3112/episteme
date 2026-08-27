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
