package com.aryan.reader.data

import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.canonicalCloudFolderContentHash
import com.aryan.reader.shared.isCloudFolderSha256
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val CLOUD_FOLDER_DRIVE_PREFIX = "cloud-folder-v1"
internal const val CLOUD_FOLDER_MANIFEST_NODE_ID = "manifest"

/**
 * Names are immutable object addresses.  A logical root can therefore have
 * many manifest revisions and a file can be uploaded again after its bytes
 * change without ever updating an object that another device may be reading.
 */
internal fun cloudFolderManifestDrivePrefix(rootId: String): String =
    "$CLOUD_FOLDER_DRIVE_PREFIX-manifest-${cloudFolderDriveSegment(rootId)}"

internal fun cloudFolderManifestDriveName(
    rootId: String,
    revision: Long,
    manifestHash: String,
): String =
    "${cloudFolderManifestDrivePrefix(rootId)}-r${revision.coerceAtLeast(0L)}-${cloudFolderDriveSegment(manifestHash)}.json"

/** Compatibility address for old callers; new writes must use the versioned overload. */
internal fun cloudFolderManifestDriveName(rootId: String): String =
    "${cloudFolderManifestDrivePrefix(rootId)}-r0-legacy.json"

/**
 * Address used by the pre-CAS implementation.  Keep this separate from the
 * compatibility overload above: the overload is retained for callers that
 * used the old helper, while this name identifies bytes that are actually
 * present in existing Drive app-data.
 */
internal fun cloudFolderLegacyManifestDriveName(rootId: String): String =
    "${cloudFolderManifestDrivePrefix(rootId)}.json"

internal fun cloudFolderContentDriveName(
    rootId: String,
    nodeId: String,
    contentHash: String,
    revision: Long,
): String =
    "$CLOUD_FOLDER_DRIVE_PREFIX-content-${cloudFolderDriveSegment(rootId)}-${cloudFolderDriveSegment(nodeId)}-" +
        "r${revision.coerceAtLeast(0L)}-${cloudFolderDriveSegment(contentHash)}"

/** Compatibility address for old callers; new writes must use the versioned overload. */
internal fun cloudFolderContentDriveName(rootId: String, nodeId: String): String =
    cloudFolderContentDriveName(rootId, nodeId, "missing-hash", 0L)

/** Address used by the pre-CAS implementation for file content objects. */
internal fun cloudFolderLegacyContentDriveName(rootId: String, nodeId: String): String =
    "$CLOUD_FOLDER_DRIVE_PREFIX-content-${cloudFolderDriveSegment(rootId)}-${cloudFolderDriveSegment(nodeId)}"

internal fun cloudFolderDriveSegment(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.trim().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(32)

internal fun cloudFolderDriveQueryLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("'", "\\'")

internal fun cloudFolderDriveMetadata(
    rootId: String,
    nodeId: String,
    relativePath: String? = null,
    revision: Long = 0L,
    contentHash: String? = null,
    contentSizeBytes: Long? = null,
): Map<String, String> = buildMap {
    put("cloudFolderSchema", "1")
    put("cloudFolderRootId", rootId)
    put("cloudFolderNodeId", nodeId)
    put("cloudFolderRevision", revision.toString())
    relativePath?.takeIf { it.isNotBlank() }?.let { put("cloudFolderRelativePath", it) }
    contentHash?.takeIf { it.isNotBlank() }?.let { put("cloudFolderContentHash", it) }
    contentSizeBytes?.takeIf { it >= 0L }?.let { put("cloudFolderContentSize", it.toString()) }
}

/**
 * Validate the authenticated identity of a Drive object before consuming its
 * bytes.  Drive IDs are opaque and must never be trusted as logical identity.
 */
internal fun cloudFolderDriveMetadataMatches(
    properties: Map<String, String>,
    rootId: String,
    nodeId: String,
    revision: Long,
    contentHash: String? = null,
    contentSizeBytes: Long? = null,
): Boolean {
    if (properties["cloudFolderSchema"] != "1" ||
        properties["cloudFolderRootId"] != rootId ||
        properties["cloudFolderNodeId"] != nodeId ||
        properties["cloudFolderRevision"] != revision.toString()
    ) {
        return false
    }
    if (contentHash != null) {
        val expectedHash = canonicalCloudFolderContentHash(contentHash)
        val actualHash = canonicalCloudFolderContentHash(properties["cloudFolderContentHash"])
        if (!isCloudFolderSha256(expectedHash) || actualHash != expectedHash || !isCloudFolderSha256(actualHash)) {
            return false
        }
    }
    if (contentSizeBytes != null && properties["cloudFolderContentSize"] != contentSizeBytes.toString()) return false
    return true
}

/** Strict identity check for an immutable pre-CAS manifest object. */
internal fun cloudFolderLegacyManifestMetadataMatches(
    name: String?,
    properties: Map<String, String>,
    rootId: String,
    revision: Long,
): Boolean = name == cloudFolderLegacyManifestDriveName(rootId) &&
    cloudFolderDriveMetadataMatches(
        properties = properties,
        rootId = rootId,
        nodeId = CLOUD_FOLDER_MANIFEST_NODE_ID,
        revision = revision,
    )

/** Strict identity check for an immutable pre-CAS content object. */
internal fun cloudFolderLegacyContentMetadataMatches(
    name: String?,
    properties: Map<String, String>,
    rootId: String,
    nodeId: String,
    revision: Long,
): Boolean = name == cloudFolderLegacyContentDriveName(rootId, nodeId) &&
    cloudFolderDriveMetadataMatches(
        properties = properties,
        rootId = rootId,
        nodeId = nodeId,
        revision = revision,
    )

/**
 * Legacy objects did not always carry content hash/size app-properties.  If
 * they did, those values are still checked; the payload digest remains the
 * final authority for older objects with either property absent.
 */
internal fun cloudFolderLegacyOptionalContentMetadataMatches(
    properties: Map<String, String>,
    expectedContentHash: String,
    expectedSizeBytes: Long,
): Boolean {
    val expectedHash = canonicalCloudFolderContentHash(expectedContentHash)
        ?.takeIf(::isCloudFolderSha256)
        ?: return false
    val storedHash = properties["cloudFolderContentHash"]
    if (storedHash != null && canonicalCloudFolderContentHash(storedHash) != expectedHash) {
        return false
    }
    val storedSize = properties["cloudFolderContentSize"]
    if (storedSize != null && storedSize.toLongOrNull() != expectedSizeBytes) {
        return false
    }
    return expectedSizeBytes >= 0L
}

/** Hash and size derived from the exact bytes downloaded from Drive. */
internal data class CloudFolderManifestPayload(
    val manifest: CloudFolderManifest,
    val contentHash: String,
    val contentSizeBytes: Long,
)

internal fun decodeCloudFolderManifestPayload(payload: ByteArray): CloudFolderManifestPayload? {
    val manifest = runCatching {
        CloudFolderManifestCodec.decode(String(payload, Charsets.UTF_8))
    }.getOrNull() ?: return null
    return CloudFolderManifestPayload(
        manifest = manifest,
        contentHash = sha256CloudFolderBytes(payload),
        contentSizeBytes = payload.size.toLong(),
    )
}

internal fun sha256CloudFolderBytes(bytes: ByteArray): String =
    "sha256:" + MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

/** A successful Drive listing distinguishes a missing manifest from an error. */
sealed interface CloudFolderManifestReadResult {
    data class Found(
        val manifest: CloudFolderManifest,
        val driveFileId: String,
        val modifiedTimeMillis: Long = 0L,
    ) : CloudFolderManifestReadResult

    data object NotFound : CloudFolderManifestReadResult
}

/** Drive metadata needed to discover logical roots before their IDs are known locally. */
data class CloudFolderManifestRef(
    val rootId: String,
    val driveFileId: String,
    val revision: Long = 0L,
    val modifiedTimeMillis: Long = 0L,
)
