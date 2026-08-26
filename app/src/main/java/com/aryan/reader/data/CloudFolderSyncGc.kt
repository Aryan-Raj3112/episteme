package com.aryan.reader.data

/** Metadata captured from Drive before considering an immutable object for GC. */
data class CloudFolderDriveObjectRef(
    val driveFileId: String,
    val name: String,
    val rootId: String,
    val nodeId: String,
    val revision: Long,
    val modifiedTimeMillis: Long,
    val properties: Map<String, String>,
)

/** Deterministic, retention-aware deletion candidate. */
data class CloudFolderGarbageCandidate(
    val objectRef: CloudFolderDriveObjectRef,
)

/**
 * Plan only objects that are not reachable from the committed manifests and
 * whose Drive timestamp is known and older than the retention window. Unknown
 * timestamps are retained conservatively.
 */
fun planCloudFolderGarbageCollection(
    objects: Collection<CloudFolderDriveObjectRef>,
    referencedDriveFileIds: Set<String>,
    nowMillis: Long,
    retentionMillis: Long,
): List<CloudFolderGarbageCandidate> {
    require(nowMillis >= 0L) { "GC time must not be negative" }
    require(retentionMillis >= 0L) { "GC retention must not be negative" }
    val cutoff = (nowMillis - retentionMillis).coerceAtLeast(0L)
    return objects.asSequence()
        .filter { it.driveFileId.isNotBlank() && it.driveFileId !in referencedDriveFileIds }
        .filter { it.modifiedTimeMillis > 0L && it.modifiedTimeMillis <= cutoff }
        .map(::CloudFolderGarbageCandidate)
        .sortedWith(
            compareBy<CloudFolderGarbageCandidate> { it.objectRef.rootId }
                .thenBy { it.objectRef.nodeId }
                .thenBy { it.objectRef.revision }
                .thenBy { it.objectRef.driveFileId },
        )
        .toList()
}

