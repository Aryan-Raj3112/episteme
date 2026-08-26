package com.aryan.reader.data

import com.aryan.reader.shared.CloudFolderManifest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val CLOUD_FOLDER_DRIVE_PREFIX = "cloud-folder-v1"

/** Stable, provider-independent names for the Drive appDataFolder objects. */
internal fun cloudFolderManifestDriveName(rootId: String): String =
    "$CLOUD_FOLDER_DRIVE_PREFIX-manifest-${cloudFolderDriveSegment(rootId)}.json"

internal fun cloudFolderContentDriveName(rootId: String, nodeId: String): String =
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
): Map<String, String> = buildMap {
    put("cloudFolderSchema", "1")
    put("cloudFolderRootId", rootId)
    put("cloudFolderNodeId", nodeId)
    put("cloudFolderRevision", revision.toString())
    relativePath?.takeIf { it.isNotBlank() }?.let { put("cloudFolderRelativePath", it) }
    contentHash?.takeIf { it.isNotBlank() }?.let { put("cloudFolderContentHash", it) }
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
