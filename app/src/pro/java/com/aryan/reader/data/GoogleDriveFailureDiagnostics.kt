package com.aryan.reader.data

import java.util.Locale

/**
 * Provider-owned fields that are safe to retain in diagnostics.  Drive's
 * human-readable message is deliberately excluded because it can contain
 * resource names or account details.
 */
internal data class GoogleDriveStructuredError(
    val domain: String?,
    val reason: String?,
)

internal data class GoogleDriveFailureClassification(
    val statusCategory: String,
    val bodyCategory: String,
    val driveReason: String,
    val driveDomain: String,
)

internal fun classifyGoogleDriveFailure(
    httpStatusCode: Int?,
    responseBody: String?,
    structuredErrors: List<GoogleDriveStructuredError> = emptyList(),
): GoogleDriveFailureClassification {
    val body = responseBody.orEmpty().lowercase(Locale.US)
    // Keep this body-derived category compatible with the previous worker
    // behavior. Structured reasons are exposed separately for diagnosis.
    val bodyCategory = when {
        body.contains("quota") || body.contains("rate limit") ||
            body.contains("userratelimit") || body.contains("dailylimit") -> "quota"
        body.contains("permission") || body.contains("forbidden") ||
            body.contains("insufficientpermissions") -> "permission_denied"
        body.contains("unauthenticated") || body.contains("invalid credential") -> "unauthenticated"
        body.contains("not found") || body.contains("notfound") -> "not_found"
        body.contains("invalid") -> "invalid_data"
        else -> "unknown"
    }
    val statusCategory = when {
        bodyCategory != "unknown" -> bodyCategory
        httpStatusCode == 401 -> "unauthorized"
        httpStatusCode == 403 -> "forbidden"
        httpStatusCode == 404 -> "not_found"
        httpStatusCode == 408 -> "timeout"
        httpStatusCode == 429 -> "quota"
        (httpStatusCode ?: 0) >= 500 -> "network"
        else -> "unknown"
    }
    val first = structuredErrors.firstOrNull { !it.reason.isNullOrBlank() }
    return GoogleDriveFailureClassification(
        statusCategory = statusCategory,
        bodyCategory = bodyCategory,
        driveReason = safeDriveDiagnosticToken(first?.reason),
        driveDomain = safeDriveDiagnosticToken(first?.domain),
    )
}

internal fun safeDriveDiagnosticToken(value: String?, fallback: String = "unknown"): String {
    val normalized = value?.trim().orEmpty()
    if (!normalized.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) return fallback
    return normalized.lowercase(Locale.US)
}

internal fun cloudFolderDriveSizeBucket(sizeBytes: Long): String = when {
    sizeBytes < 0L -> "unknown"
    sizeBytes < 1L * 1024L -> "lt_1kib"
    sizeBytes < 1L * 1024L * 1024L -> "lt_1mib"
    sizeBytes < 5L * 1024L * 1024L -> "lt_5mib"
    sizeBytes < 100L * 1024L * 1024L -> "lt_100mib"
    else -> "gte_100mib"
}

/**
 * Drive's one-shot media request avoids the resumable-session handshake. Keep
 * it bounded to 1 MiB so a transient failure only has to replay a small
 * payload; unknown sizes stay resumable because direct upload needs a known
 * content length.
 */
internal const val CLOUD_FOLDER_DIRECT_UPLOAD_MAX_BYTES: Long = 1L * 1024L * 1024L

internal fun cloudFolderUploadMode(sizeBytes: Long): String =
    if (sizeBytes in 0L..CLOUD_FOLDER_DIRECT_UPLOAD_MAX_BYTES) "multipart" else "resumable"

internal data class RetryAfterDiagnostic(
    val kind: String,
    val seconds: Long?,
)

internal fun parseRetryAfterDiagnostic(
    value: String?,
    nowMillis: Long = System.currentTimeMillis(),
): RetryAfterDiagnostic {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return RetryAfterDiagnostic("none", null)
    normalized.toLongOrNull()?.takeIf { it >= 0L }?.let {
        return RetryAfterDiagnostic("seconds", it)
    }
    val dateMillis = runCatching {
        java.time.ZonedDateTime.parse(normalized, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli()
    }.getOrNull()
    return if (dateMillis != null) {
        RetryAfterDiagnostic("http_date", ((dateMillis - nowMillis).coerceAtLeast(0L) / 1_000L))
    } else {
        RetryAfterDiagnostic("invalid", null)
    }
}
