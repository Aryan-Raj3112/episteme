package com.aryan.reader.shared

/**
 * Shared cloud-folder garbage-collection planner.
 *
 * Android is the absolute benchmark and is NOT changed. Direct port of
 * app/src/main/java/com/aryan/reader/data/CloudFolderSyncGc.kt:24-44 (plus
 * its `CloudFolderDriveObjectRef`/`CloudFolderGarbageCandidate` shapes) so
 * the iOS executor deletes exactly the objects Android would: unreferenced
 * IDs with a known Drive timestamp older than the retention window, in
 * deterministic order. Unknown timestamps are retained conservatively.
 */
data class CloudFolderStoredObjectRef(
    val driveFileId: String,
    val name: String = "",
    val rootId: String = "",
    val nodeId: String = "",
    val revision: Long = 0L,
    val modifiedTimeMillis: Long = 0L,
    val properties: Map<String, String> = emptyMap(),
)

data class CloudFolderGarbageCandidate(
    val objectRef: CloudFolderStoredObjectRef,
)

/** Retention window mirroring `CloudFolderSyncWorker` (30 days). */
const val SHARED_CLOUD_FOLDER_GC_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

fun planSharedCloudFolderGarbageCollection(
    objects: Collection<CloudFolderStoredObjectRef>,
    referencedDriveFileIds: Set<String>,
    nowMillis: Long,
    retentionMillis: Long = SHARED_CLOUD_FOLDER_GC_RETENTION_MILLIS,
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
