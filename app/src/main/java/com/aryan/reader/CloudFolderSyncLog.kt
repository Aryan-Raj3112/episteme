package com.aryan.reader

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import timber.log.Timber

/**
 * The single log tag for the Android direct cloud-folder pipeline.
 *
 * Folder names, provider URIs, account IDs, Drive object IDs, and exception
 * messages can contain user data. Callers should log only the derived values
 * exposed by this file rather than interpolating those values directly.
 */
internal const val CLOUD_FOLDER_SYNC_LOG_TAG = "EpistemeCloudFolderSync"

internal fun cloudFolderSafeId(value: String?, length: Int = 12): String {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return "none"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    return digest.take(length.coerceIn(8, digest.length))
}

internal fun cloudFolderSafeUri(uri: android.net.Uri?, length: Int = 12): String =
    cloudFolderSafeId(uri?.toString(), length)

internal fun cloudFolderErrorClass(error: Throwable): String =
    error::class.java.simpleName.takeIf { it.isNotBlank() } ?: "Unknown"

private fun cloudFolderErrorChain(error: Throwable): Sequence<Throwable> = sequence {
    var current: Throwable? = error
    val seen = mutableSetOf<Int>()
    while (current != null && seen.add(System.identityHashCode(current))) {
        yield(current)
        current = current.cause
    }
}

internal fun cloudFolderErrorStatus(error: Throwable): String {
    // Drive reports this as HTTP 403, but it is a permanent request-shape
    // failure, not an account permission or transient quota failure.  The
    // offending metadata must be corrected before retrying can succeed.
    if ((error as? CloudFolderDriveException)?.driveReason.equals(
            "propertylengthlimitexceeded",
            ignoreCase = true,
        )
    ) {
        return "invalid_data"
    }
    return cloudFolderErrorStatus(cloudFolderErrorChain(error).mapNotNull { it.message }.joinToString(" "))
}

/** The provider reason is useful in persisted progress, but not for retry policy. */
internal fun cloudFolderPersistedErrorStatus(error: Throwable): String =
    (error as? CloudFolderDriveException)?.driveReason
        ?.takeIf { it.isNotBlank() && it != "unknown" }
        ?: cloudFolderErrorStatus(error)

internal fun cloudFolderErrorStatus(message: String?): String {
    val normalized = message.orEmpty().lowercase(Locale.US)
    return when {
        normalized.contains("permission_denied") ||
            normalized.contains("permission denied") ||
            normalized.contains("missing or insufficient permissions") -> "permission_denied"
        normalized.contains("unauthenticated") ||
            normalized.contains("invalid credentials") ||
            normalized.contains("authentication") -> "unauthenticated"
        normalized.contains("unauthorized") -> "unauthorized"
        normalized.contains("forbidden") ||
            normalized.contains("status code: 403") ||
            normalized.contains("http 403") -> "forbidden"
        normalized.contains("status code: 401") ||
            normalized.contains("http 401") -> "unauthorized"
        normalized.contains("not found") || normalized.contains("404") -> "not_found"
        normalized.contains("timeout") || normalized.contains("timed out") -> "timeout"
        normalized.contains("network") || normalized.contains("unavailable") -> "network"
        normalized.contains("quota") || normalized.contains("rate limit") -> "quota"
        normalized.contains("malformed") || normalized.contains("invalid") -> "invalid_data"
        normalized.contains("unsupported") -> "unsupported"
        else -> "unknown"
    }
}

/** Only failures that are safe to stop retrying without user intervention. */
internal fun cloudFolderFailureIsDeterministic(error: Throwable): Boolean =
    error is SecurityException || cloudFolderErrorStatus(error) in setOf(
        "permission_denied",
        "unauthenticated",
        "forbidden",
        "unauthorized",
        "invalid_data",
        "unsupported",
    )

internal fun cloudFolderLogD(message: String) {
    Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG).d(message)
}

internal fun cloudFolderLogI(message: String) {
    Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG).i(message)
}

internal fun cloudFolderLogW(message: String) {
    Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG).w(message)
}

/** Do not pass [error] to Timber: its stack trace may include a URI or name. */
internal fun cloudFolderLogError(
    event: String,
    error: Throwable,
    details: String = "",
) {
    val suffix = details.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    val driveDetails = (error as? CloudFolderDriveException)?.let {
        " httpStatus=${it.httpStatusCode ?: "none"} bodyCategory=${it.bodyCategory} " +
            "driveReason=${it.driveReason} driveDomain=${it.driveDomain} " +
            "driveCode=${it.driveErrorCode ?: "none"} retryAfter=${it.retryAfterKind}:" +
            "${it.retryAfterSeconds ?: "none"} " +
            "requestId=${it.requestIdHeader ?: "none"}:${it.requestIdHash ?: "none"} " +
            "stage=${it.operationStage ?: "unknown"} operation=${it.operationType ?: "unknown"} " +
            "uploadMode=${it.uploadMode ?: "unknown"} sizeBucket=${it.sizeBucket ?: "unknown"} " +
            "attempt=${it.attempt ?: "unknown"}"
    }.orEmpty()
    Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG).e(
        "event=$event errorClass=${cloudFolderErrorClass(error)} " +
            "errorStatus=${cloudFolderErrorStatus(error)}$driveDetails$suffix",
    )
}

/** Keep durable UI errors short, stable, and independent of provider text. */
internal fun cloudFolderUserFacingError(error: Throwable): String = when (cloudFolderErrorStatus(error)) {
    "forbidden", "permission_denied" -> "Drive denied access"
    "unauthorized", "unauthenticated" -> "Drive authorization expired"
    "quota" -> "Drive quota or rate limit reached"
    "network", "timeout" -> "Network problem; will retry"
    "not_found" -> "Drive item was not found"
    "invalid_data" -> "Drive rejected the folder metadata"
    "unsupported" -> "Drive does not support this item"
    else -> "Cloud upload failed; will retry"
}
