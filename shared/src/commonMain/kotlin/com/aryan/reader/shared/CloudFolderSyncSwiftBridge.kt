package com.aryan.reader.shared

/**
 * Shared helpers for the Swift cloud-folder executor.
 *
 * Android is the absolute benchmark and is NOT changed. Swift cannot rely on
 * Kotlin enum export details (case naming, `name`, default arguments), so
 * every enum crossing or data-class construction the executor needs is
 * wrapped here with stable String boundaries. Logic ports
 * (`reconcileCloudFolderConflicts`, `cloudFolderOutboxOperationId`,
 * pull-gate predicates) are verbatim ports of the cited Android functions.
 */

fun cloudFolderOutboxOperationId(
    operation: CloudFolderSyncOperation,
    accountId: String,
    rootId: String,
): String {
    val material = listOf(
        accountId,
        rootId,
        operation.nodeId,
        operation.kind.name,
        operation.direction.name,
        operation.relativePath,
        operation.previousRelativePath.orEmpty(),
        operation.revision.toString(),
        operation.sourceNodeId.orEmpty(),
    ).joinToString("\u0000")
    return "folder_op_${localFolderSyncSha256Hex(material).take(32)}"
}

fun cloudFolderPhaseName(phase: CloudFolderSyncPhase): String = phase.name

fun cloudFolderPhaseForRaw(raw: String?): CloudFolderSyncPhase =
    CloudFolderSyncPhase.entries.firstOrNull { it.name == raw } ?: CloudFolderSyncPhase.SCANNING

fun cloudFolderOperationKindName(operation: CloudFolderSyncOperation): String =
    operation.kind.name

fun cloudFolderOperationDirectionName(operation: CloudFolderSyncOperation): String =
    operation.direction.name

fun cloudFolderResolutionName(resolution: CloudFolderConflictResolution): String =
    resolution.name

fun cloudFolderResolutionForRaw(raw: String?): CloudFolderConflictResolution =
    CloudFolderConflictResolution.entries.firstOrNull { it.name == raw }
        ?: CloudFolderConflictResolution.DEFER

fun cloudFolderConflictTypeName(conflict: CloudFolderConflict): String =
    conflict.type.name

fun cloudFolderMaterializationName(mode: CloudFolderMaterializationMode): String =
    mode.name

fun cloudFolderMaterializationForRaw(raw: String?): CloudFolderMaterializationMode =
    CloudFolderMaterializationMode.entries.firstOrNull { it.name == raw }
        ?: CloudFolderMaterializationMode.CLOUD_ONLY

fun cloudFolderPermissionName(state: CloudFolderPermissionState): String = state.name

fun isCloudFolderKeepOffline(mode: CloudFolderMaterializationMode): Boolean =
    mode == CloudFolderMaterializationMode.KEEP_OFFLINE

fun isCloudFolderCloudOnly(mode: CloudFolderMaterializationMode): Boolean =
    mode == CloudFolderMaterializationMode.CLOUD_ONLY

fun isCloudFolderLocalMirror(mode: CloudFolderMaterializationMode): Boolean =
    mode == CloudFolderMaterializationMode.LOCAL_MIRROR

fun cloudFolderTombstoneIsDirectory(tombstone: CloudFolderTombstone): Boolean =
    tombstone.kind == CloudFolderNodeKind.DIRECTORY

fun withCloudFolderConflictResolution(
    record: CloudFolderConflictRecord,
    resolutionRaw: String,
    nowMillis: Long,
): CloudFolderConflictRecord = record.copy(
    resolution = cloudFolderResolutionForRaw(resolutionRaw),
    updatedAt = nowMillis,
)

fun makeCloudFolderSyncProgress(
    rootId: String,
    phaseRaw: String,
    completedFiles: Int,
    totalFiles: Int,
    completedBytes: Long,
    totalBytes: Long,
    updatedAtMillis: Long,
    errorStatus: String?,
): CloudFolderSyncProgress = CloudFolderSyncProgress(
    rootId = rootId,
    phase = cloudFolderPhaseForRaw(phaseRaw),
    completedFiles = completedFiles,
    totalFiles = totalFiles,
    completedBytes = completedBytes,
    totalBytes = totalBytes,
    updatedAt = updatedAtMillis,
    errorStatus = errorStatus,
).sanitized()

fun makeCloudFolderBinding(
    rootId: String,
    deviceId: String,
    localUri: String?,
    permissionRaw: String,
    materializationRaw: String,
    lastAcknowledgedRevision: Long,
    lastScanAt: Long,
    lastError: String?,
): CloudFolderDeviceBinding = CloudFolderDeviceBinding(
    rootId = rootId,
    deviceId = deviceId,
    localUri = localUri,
    permissionState = CloudFolderPermissionState.entries.firstOrNull { it.name == permissionRaw }
        ?: CloudFolderPermissionState.UNKNOWN,
    materializationMode = cloudFolderMaterializationForRaw(materializationRaw),
    lastAcknowledgedRevision = lastAcknowledgedRevision.coerceAtLeast(0L),
    lastScanAt = lastScanAt,
    lastError = lastError,
)

fun withCloudFolderBindingError(
    binding: CloudFolderDeviceBinding,
    message: String?,
): CloudFolderDeviceBinding = binding.copy(lastError = message?.take(500))

fun cloudFolderConflictUiItem(
    record: CloudFolderConflictRecord,
    folderName: String,
): CloudFolderConflictUiItem = CloudFolderConflictUiItem(
    rootId = record.rootId,
    folderName = folderName,
    conflictId = record.conflictId,
    relativePath = record.conflict.relativePath,
    type = record.conflict.type,
    resolution = record.resolution,
    baseRevision = record.baseRevision,
    localRevision = record.localRevision,
    remoteRevision = record.remoteRevision,
)

fun makeCloudFolderIncomingPrompt(
    root: CloudFolderRoot,
    sourceDeviceName: String?,
): CloudFolderIncomingFolderPrompt = CloudFolderIncomingFolderPrompt(
    root = root,
    sourceDeviceName = sourceDeviceName,
)

/**
 * Port of `CloudFolderSyncRepository.reconcileConflicts`
 * (app/.../data/CloudFolderSyncRepository.kt:880-939): existing choices
 * survive only when the exact conflict payload and revision triple still
 * match; changed bytes reset the decision to DEFER. An empty plan clears.
 */
fun reconcileCloudFolderConflicts(
    plan: CloudFolderSyncPlan,
    stored: List<CloudFolderConflictRecord>,
    nowMillis: Long,
): List<CloudFolderConflictRecord> {
    if (plan.conflicts.isEmpty()) return emptyList()
    val existing = stored.associateBy(CloudFolderConflictRecord::conflictId)
    return plan.conflicts.distinctBy(CloudFolderConflict::conflictId).map { conflict ->
        val old = existing[conflict.conflictId]
        val sameSnapshot = old != null &&
            old.baseRevision == plan.baseRevision &&
            old.localRevision == plan.localRevision &&
            old.remoteRevision == plan.remoteRevision &&
            old.conflict == conflict
        CloudFolderConflictRecord(
            conflict = conflict,
            baseRevision = plan.baseRevision,
            localRevision = plan.localRevision,
            remoteRevision = plan.remoteRevision,
            resolution = if (sameSnapshot) old!!.resolution else CloudFolderConflictResolution.DEFER,
            createdAt = if (sameSnapshot) old!!.createdAt else nowMillis,
            updatedAt = if (sameSnapshot) old!!.updatedAt else nowMillis,
        )
    }
}

/**
 * Port of the worker's resolution pick
 * (app/.../CloudFolderSyncWorker.kt:1532-1538): a stored non-DEFER choice
 * wins, otherwise the deterministic type default applies. Sync never stalls
 * waiting on a manual choice.
 */
fun cloudFolderResolutionsForPlan(
    plan: CloudFolderSyncPlan,
    stored: List<CloudFolderConflictRecord>,
): Map<String, CloudFolderConflictResolution> {
    val byId = stored.associateBy(CloudFolderConflictRecord::conflictId)
    return plan.conflicts.associate { conflict ->
        val kept = byId[conflict.conflictId]?.resolution
            ?.takeUnless { it == CloudFolderConflictResolution.DEFER }
        conflict.conflictId to (kept ?: conflict.type.defaultResolution())
    }
}

fun shouldPullCloudFolderRoot(
    isDeleted: Boolean,
    isIncluded: Boolean,
    hasBinding: Boolean,
): Boolean = !isDeleted && isIncluded && hasBinding

fun isValidCloudFolderManifest(manifest: CloudFolderManifest): Boolean =
    manifest.validationIssues().isEmpty()

/** Publish-safety compare: only logical root metadata, never revisions/stats. */
fun cloudFolderRootsEquivalentForPublish(
    first: CloudFolderRoot,
    second: CloudFolderRoot,
): Boolean = first.rootId == second.rootId &&
    first.name.trim() == second.name.trim() &&
    first.isDeleted == second.isDeleted &&
    first.createdAt == second.createdAt &&
    first.createdByDeviceId == second.createdByDeviceId

fun makeCloudFolderRoot(
    rootId: String,
    name: String,
    createdAt: Long,
    createdByDeviceId: String,
    updatedAt: Long,
    manifestRevision: Long,
    isDeleted: Boolean,
): CloudFolderRoot = CloudFolderRoot(
    rootId = rootId,
    name = name,
    createdAt = createdAt,
    createdByDeviceId = createdByDeviceId,
    updatedAt = updatedAt,
    manifestRevision = manifestRevision,
    isDeleted = isDeleted,
)

fun makeCloudFolderFileNode(
    nodeId: String,
    rootId: String,
    relativePath: String,
    contentHash: String?,
    sizeBytes: Long,
    mimeType: String?,
    fileModifiedAt: Long,
    revision: Long,
    modifiedAt: Long,
    modifiedByDeviceId: String,
    contentObjectId: String?,
): CloudFolderNode = CloudFolderNode(
    nodeId = nodeId,
    rootId = rootId,
    relativePath = relativePath,
    kind = CloudFolderNodeKind.FILE,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    fileModifiedAt = fileModifiedAt,
    revision = revision,
    modifiedAt = modifiedAt,
    modifiedByDeviceId = modifiedByDeviceId,
    contentObjectId = contentObjectId,
)

fun makeCloudFolderDirectoryNode(
    nodeId: String,
    rootId: String,
    relativePath: String,
    revision: Long,
    modifiedAt: Long,
    modifiedByDeviceId: String,
): CloudFolderNode = CloudFolderNode(
    nodeId = nodeId,
    rootId = rootId,
    relativePath = relativePath,
    kind = CloudFolderNodeKind.DIRECTORY,
    revision = revision,
    modifiedAt = modifiedAt,
    modifiedByDeviceId = modifiedByDeviceId,
)

fun makeCloudFolderTombstone(
    nodeId: String,
    rootId: String,
    relativePath: String,
    isDirectory: Boolean,
    deletedAt: Long,
    deletedRevision: Long,
    deletedByDeviceId: String,
    lastKnownContentHash: String?,
    lastKnownSizeBytes: Long,
): CloudFolderTombstone = CloudFolderTombstone(
    nodeId = nodeId,
    rootId = rootId,
    relativePath = relativePath,
    kind = if (isDirectory) CloudFolderNodeKind.DIRECTORY else CloudFolderNodeKind.FILE,
    deletedAt = deletedAt,
    deletedRevision = deletedRevision,
    deletedByDeviceId = deletedByDeviceId,
    lastKnownContentHash = lastKnownContentHash,
    lastKnownSizeBytes = lastKnownSizeBytes,
)

fun makeCloudFolderManifest(
    root: CloudFolderRoot,
    revision: Long,
    baseRevision: Long,
    generatedAt: Long,
    generatedByDeviceId: String,
    nodes: List<CloudFolderNode>,
    tombstones: List<CloudFolderTombstone>,
): CloudFolderManifest = CloudFolderManifest(
    root = root,
    revision = revision,
    baseRevision = baseRevision,
    generatedAt = generatedAt,
    generatedByDeviceId = generatedByDeviceId,
    nodes = nodes,
    tombstones = tombstones,
)

fun decodeCloudFolderRootsOrNull(json: String): List<CloudFolderRoot>? =
    runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<CloudFolderRoot>>(json)
    }.getOrNull()

fun decodeCloudFolderBindingsOrNull(json: String): Map<String, CloudFolderDeviceBinding>? =
    runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<Map<String, CloudFolderDeviceBinding>>(json)
    }.getOrNull()

fun decodeCloudFolderProgressMapOrNull(json: String): Map<String, CloudFolderSyncProgress>? =
    runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<Map<String, CloudFolderSyncProgress>>(json)
    }.getOrNull()

fun decodeCloudFolderConflictRecordsOrNull(json: String): List<CloudFolderConflictRecord>? =
    runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<CloudFolderConflictRecord>>(json)
    }.getOrNull()

fun shouldQueueCloudFolderPullAfterRemoteChange(
    hasCloudToLocalOperations: Boolean,
    isSelected: Boolean,
    hasBinding: Boolean,
    isSignedIn: Boolean,
    syncEnabled: Boolean,
): Boolean = hasCloudToLocalOperations && isSelected && hasBinding && isSignedIn && syncEnabled
