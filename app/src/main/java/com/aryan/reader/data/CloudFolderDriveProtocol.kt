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
