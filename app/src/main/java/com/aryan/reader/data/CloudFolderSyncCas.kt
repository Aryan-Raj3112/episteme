package com.aryan.reader.data

/**
 * The Firestore document is the commit pointer for a cloud-folder root. Drive
 * objects remain immutable; this record only decides which immutable manifest
 * is authoritative and serializes competing writers.
 */
data class CloudFolderManifestHead(
    val rootId: String,
    val revision: Long,
    val manifestDriveFileId: String,
    val manifestHash: String,
)

data class CloudFolderManifestLease(
    val userId: String,
    val rootId: String,
    val token: String,
    val expectedRevision: Long?,
    val revision: Long,
    val previousRevision: Long?,
    val previousManifestDriveFileId: String?,
    val previousManifestHash: String?,
)

sealed interface CloudFolderManifestLeaseResult {
    data class Acquired(val lease: CloudFolderManifestLease) : CloudFolderManifestLeaseResult
    data object Conflict : CloudFolderManifestLeaseResult
    data object Unsupported : CloudFolderManifestLeaseResult
}

