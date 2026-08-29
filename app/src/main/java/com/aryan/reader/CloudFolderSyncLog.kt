package com.aryan.reader

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject
import timber.log.Timber

/**
 * The single log tag for the Android direct cloud-folder pipeline.
 *
 * Folder names, provider URIs, account IDs, Drive object IDs, and exception
 * messages can contain user data. Callers should log only the derived values
 * exposed by this file rather than interpolating those values directly.
 */
internal const val CLOUD_FOLDER_SYNC_LOG_TAG = "EpistemeCloudFolderSync"
private val CLOUD_FOLDER_OPERATION_TOKEN_REGEX = Regex("op_[0-9a-f]{16}")
private val CLOUD_FOLDER_CORRELATION_TOKEN_REGEX = Regex("[0-9a-f]{16}")

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

/**
 * A source-set-independent size bucket for worker diagnostics. The Drive
 * adapter has a matching helper in the Pro source set, but workers are shared
 * by OSS and Pro and must not depend on a Pro-only symbol.
 */
internal fun cloudFolderSizeBucket(sizeBytes: Long): String = when {
    sizeBytes < 0L -> "unknown"
    sizeBytes < 1L * 1024L -> "lt_1kib"
    sizeBytes < 1L * 1024L * 1024L -> "lt_1mib"
    sizeBytes < 5L * 1024L * 1024L -> "lt_5mib"
    sizeBytes < 100L * 1024L * 1024L -> "lt_100mib"
    else -> "gte_100mib"
}

/**
 * A deterministic, privacy-safe correlation value for one logical cloud
 * operation.  The input is never logged; only its SHA-256-derived token is
 * returned.  Keeping this in the common logging helper makes it difficult for
 * a future call site to accidentally put an account, URI, path, or book ID in
 * logcat while still allowing two devices to correlate the same generation.
 */
internal fun cloudFolderSyncCorrelationId(vararg values: Any?): String =
    cloudFolderSafeId(
        values.joinToString("\u0000") { value -> value?.toString().orEmpty() },
        length = 16,
    )

internal fun cloudFolderOperationId(vararg values: Any?): String =
    "op_${cloudFolderSyncCorrelationId(*values)}"

/**
 * Preserve IDs produced by [cloudFolderOperationId] and
 * [cloudFolderSyncCorrelationId] while hashing anything else before it can
 * reach a Drive diagnostic. This keeps the low-level Pro Drive adapter safe
 * even when a future caller passes an untrusted value.
 */
internal fun cloudFolderTraceFields(
    operationId: String?,
    correlationId: String?,
): String {
    fun safeToken(value: String?, operation: Boolean): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return "none"
        val expected = if (operation) {
            CLOUD_FOLDER_OPERATION_TOKEN_REGEX
        } else {
            CLOUD_FOLDER_CORRELATION_TOKEN_REGEX
        }
        return normalized.takeIf(expected::matches)
            ?: cloudFolderSafeId(normalized, length = 16)
    }
    return "operation=${safeToken(operationId, operation = true)} " +
        "correlation=${safeToken(correlationId, operation = false)}"
}

/**
 * Compact payload diagnostics for a metadata/annotation sidecar.  Sidecar
 * contents may contain titles, notes, or annotations, so only byte count,
 * schema/version, and a SHA-256 integrity token are exposed.
 */
internal data class CloudFolderSidecarPayloadInfo(
    val bytes: Int,
    val sha256: String,
    val schema: Int?,
)

internal fun cloudFolderSidecarPayloadInfo(payload: String?): CloudFolderSidecarPayloadInfo? {
    val value = payload ?: return null
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    val schema = runCatching {
        JSONObject(value).let { root ->
            when {
                root.has("schemaVersion") -> root.optInt("schemaVersion")
                root.has("version") -> root.optInt("version")
                else -> null
            }
        }
    }.getOrNull()
    return CloudFolderSidecarPayloadInfo(
        bytes = bytes.size,
        sha256 = "sha256:$digest",
        schema = schema,
    )
}

internal fun CloudFolderSidecarPayloadInfo?.toLogFields(): String =
    "schema=${this?.schema ?: "unknown"} bytes=${this?.bytes ?: "unknown"} " +
        "hash=${this?.sha256 ?: "none"}"

internal fun cloudFolderErrorClass(error: Throwable): String =
    error::class.java.simpleName.takeIf { it.isNotBlank() } ?: "Unknown"

/**
 * A bounded, privacy-safe reason string for log events. Exception messages
 * may embed provider URIs or absolute filesystem paths, so both are redacted;
 * the remaining first-party text (bounded stage/reason wording, relative
 * paths) is kept because it is what makes worker failures diagnosable from
 * logcat. Never return stack traces or Drive HTTP bodies here.
 */
internal fun cloudFolderSafeErrorReason(error: Throwable, limit: Int = 160): String {
    val message = cloudFolderErrorChain(error)
        .mapNotNull { it.message }
        .firstOrNull { it.isNotBlank() }
        ?: return "none"
    val uriRedacted = message.replace(CLOUD_FOLDER_URI_REGEX, "<uri>")
    val pathRedacted = uriRedacted.replace(CLOUD_FOLDER_ABSOLUTE_PATH_REGEX) { match ->
        "${match.groupValues[1]}<path>"
    }
    return pathRedacted
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit.coerceAtLeast(16))
        .ifBlank { "none" }
}

private val CLOUD_FOLDER_URI_REGEX = Regex("[A-Za-z][A-Za-z0-9+.-]*://\\S+")
private val CLOUD_FOLDER_ABSOLUTE_PATH_REGEX = Regex("(^|\\s)/[^\\s]+")

private fun cloudFolderErrorChain(error: Throwable): Sequence<Throwable> = sequence {
    var current: Throwable? = error
    val seen = mutableSetOf<Int>()
    while (current != null && seen.add(System.identityHashCode(current))) {
        yield(current)
        current = current.cause
    }
}

/**
 * Identifies failures owned by the cloud-folder transfer pipeline. Keep this
 * narrow: the main library sync must continue surfacing its own failures to
 * the user even though both paths use Drive.
 */
internal fun isCloudFolderTransferFailure(error: Throwable): Boolean =
    cloudFolderErrorChain(error).any {
        it is CloudFolderTransferException || it is CloudFolderDriveException
    }

internal fun cloudFolderErrorStatus(error: Throwable): String {
    cloudFolderErrorChain(error)
        .filterIsInstance<CloudFolderTransferException>()
        .firstOrNull()
        ?.statusCategory
        ?.takeIf { it.isNotBlank() && it != "unknown" }
        ?.let { return it }
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
    cloudFolderErrorChain(error)
        .filterIsInstance<CloudFolderDriveException>()
        .firstOrNull()
        ?.driveReason
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

/** Wrap a transfer failure with only bounded, non-user-data diagnostics. */
internal fun cloudFolderTransferFailure(
    error: Throwable,
    stage: String,
    category: String,
): CloudFolderTransferException {
    if (error is CloudFolderTransferException) return error
    val status = cloudFolderErrorStatus(error)
    return CloudFolderTransferException(
        stage = stage,
        category = category,
        statusCategory = status,
        cause = error,
    )
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

/**
 * A Drive 401/autherror is a stale access token, not proof that the user
 * revoked the app's Drive permission. Token expiry is transient and heals by
 * re-fetching the token, so it must never be treated as a deterministic
 * terminal failure (which skips WorkManager retry) nor abort a long repair
 * halfway through. A genuine revocation surfaces as a permission/scope error
 * instead.
 */
internal fun cloudFolderAuthFailureIsTransient(error: Throwable): Boolean =
    cloudFolderErrorChain(error)
        .filterIsInstance<CloudFolderDriveException>()
        .any {
            it.httpStatusCode == 401 ||
                it.driveReason.equals("autherror", ignoreCase = true) ||
                it.statusCategory.equals("unauthenticated", ignoreCase = true)
        }

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
    val driveDetails = cloudFolderErrorChain(error)
        .filterIsInstance<CloudFolderDriveException>()
        .firstOrNull()
        ?.let {
        " httpStatus=${it.httpStatusCode ?: "none"} bodyCategory=${it.bodyCategory} " +
            "driveReason=${it.driveReason} driveDomain=${it.driveDomain} " +
            "driveCode=${it.driveErrorCode ?: "none"} retryAfter=${it.retryAfterKind}:" +
            "${it.retryAfterSeconds ?: "none"} " +
            "requestId=${it.requestIdHeader ?: "none"}:${it.requestIdHash ?: "none"} " +
            "stage=${it.operationStage ?: "unknown"} operation=${it.operationType ?: "unknown"} " +
            "uploadMode=${it.uploadMode ?: "unknown"} sizeBucket=${it.sizeBucket ?: "unknown"} " +
            "attempt=${it.attempt ?: "unknown"}"
        }.orEmpty()
    val transferDetails = cloudFolderErrorChain(error)
        .filterIsInstance<CloudFolderTransferException>()
        .firstOrNull()
        ?.let { " transferStage=${it.stage} transferCategory=${it.category}" }
        .orEmpty()
    Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG).e(
        "event=$event errorClass=${cloudFolderErrorClass(error)} " +
            "errorStatus=${cloudFolderErrorStatus(error)} " +
            "reason=${cloudFolderSafeErrorReason(error)}$driveDetails$transferDetails$suffix",
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
