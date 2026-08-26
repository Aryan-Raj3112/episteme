package com.aryan.reader.shared

import kotlinx.serialization.Serializable

/**
 * Version of the portable folder manifest.  A manifest is intentionally
 * independent from Android SAF URIs (or an iOS security scoped bookmark).
 * Those provider grants belong in [CloudFolderDeviceBinding] and must never
 * be uploaded to the account.
 */
const val CLOUD_FOLDER_MANIFEST_SCHEMA_VERSION = 1

/** Maximum number of logical roots the settings UI should offer by default. */
const val DEFAULT_CLOUD_FOLDER_ROOT_LIMIT = 100

/**
 * Folder selection is opt-in.  A newly created settings value therefore
 * excludes every local folder until the user explicitly selects roots or
 * enables [ALL].
 */
@Serializable
enum class CloudFolderSyncSelectionMode {
    EXCLUDED,
    SELECTED,
    ALL,
}

/** Compatibility alias for callers that use policy rather than mode wording. */
typealias CloudFolderSelectionMode = CloudFolderSyncSelectionMode

/**
 * Account-level folder selection.  The selected IDs are logical cloud root
 * IDs, not local provider URIs.  This lets a second device see the same
 * selection without receiving an unusable URI.
 */
@Serializable
data class CloudFolderSyncSelection(
    val mode: CloudFolderSyncSelectionMode = CloudFolderSyncSelectionMode.EXCLUDED,
    val selectedRootIds: Set<String> = emptySet(),
) {
    fun includes(rootId: String): Boolean {
        val normalizedId = rootId.trim()
        if (normalizedId.isBlank()) return false
        return when (mode) {
            CloudFolderSyncSelectionMode.EXCLUDED -> false
            CloudFolderSyncSelectionMode.SELECTED -> normalizedId in selectedRootIds
            CloudFolderSyncSelectionMode.ALL -> true
        }
    }

    /** Drop stale IDs and make the setting deterministic before persistence. */
    fun normalized(knownRootIds: Collection<String> = emptyList()): CloudFolderSyncSelection {
        val known = knownRootIds.mapTo(linkedSetOf()) { it.trim() }.filterTo(linkedSetOf()) { it.isNotBlank() }
        val selected = selectedRootIds
            .mapTo(linkedSetOf()) { it.trim() }
            .filterTo(linkedSetOf()) { it.isNotBlank() && (known.isEmpty() || it in known) }
        return when (mode) {
            CloudFolderSyncSelectionMode.EXCLUDED -> copy(selectedRootIds = emptySet())
            CloudFolderSyncSelectionMode.SELECTED -> copy(selectedRootIds = selected)
            CloudFolderSyncSelectionMode.ALL -> copy(selectedRootIds = emptySet())
        }
    }

    fun withRootIncluded(rootId: String): CloudFolderSyncSelection {
        val id = rootId.trim()
        if (id.isBlank()) return this
        if (mode == CloudFolderSyncSelectionMode.ALL) return this
        return copy(
            mode = CloudFolderSyncSelectionMode.SELECTED,
            selectedRootIds = selectedRootIds + id,
        ).normalized()
    }

    fun withoutRoot(rootId: String): CloudFolderSyncSelection {
        val id = rootId.trim()
        return when (mode) {
            CloudFolderSyncSelectionMode.ALL -> copy(
                mode = CloudFolderSyncSelectionMode.SELECTED,
                selectedRootIds = emptySet(),
            )
            else -> copy(selectedRootIds = selectedRootIds - id).normalized()
        }
    }

    /**
     * Remove one root while preserving an ALL selection for every known root
     * except that one.  The no-argument form cannot do this safely because it
     * intentionally does not guess the set of roots that may exist remotely.
     */
    fun withoutRoot(rootId: String, knownRootIds: Collection<String>): CloudFolderSyncSelection {
        val id = rootId.trim()
        if (mode != CloudFolderSyncSelectionMode.ALL) return withoutRoot(id)
        val known = knownRootIds.mapTo(linkedSetOf()) { it.trim() }.filterTo(linkedSetOf()) { it.isNotBlank() }
        return CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.SELECTED,
            selectedRootIds = known - id,
        ).normalized(known)
    }

    fun includeAllRoots(): CloudFolderSyncSelection = copy(
        mode = CloudFolderSyncSelectionMode.ALL,
        selectedRootIds = emptySet(),
    )

    fun excludeAllRoots(): CloudFolderSyncSelection = CloudFolderSyncSelection()

    companion object {
        /** Explicitly named so a default cannot accidentally become opt-in. */
        val Default: CloudFolderSyncSelection = CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.EXCLUDED,
            selectedRootIds = emptySet(),
        )
    }
}

typealias CloudFolderSelectionPolicy = CloudFolderSyncSelection
typealias CloudFolderSyncPolicy = CloudFolderSyncSelection

fun defaultCloudFolderSyncSelection(): CloudFolderSyncSelection = CloudFolderSyncSelection.Default

fun shouldCloudFolderBeIncluded(
    selection: CloudFolderSyncSelection,
    rootId: String,
): Boolean = selection.includes(rootId)

@Serializable
enum class CloudFolderNodeKind {
    DIRECTORY,
    FILE,
}

@Serializable
enum class CloudFolderHashAlgorithm {
    SHA256,
}

/** A device-neutral binding.  [localUri] is deliberately not in a manifest. */
@Serializable
enum class CloudFolderPermissionState {
    UNKNOWN,
    GRANTED,
    REVOKED,
}

@Serializable
enum class CloudFolderMaterializationMode {
    CLOUD_ONLY,
    KEEP_OFFLINE,
    LOCAL_MIRROR,
}

/** User-facing summary and scan facts for a logical cloud root. */
@Serializable
data class CloudFolderRootStats(
    val fileCount: Int = 0,
    val directoryCount: Int = 0,
    val totalBytes: Long = 0L,
    val scannedAt: Long = 0L,
    val scanComplete: Boolean = true,
) {
    val itemCount: Int get() = fileCount + directoryCount

    fun sanitized(): CloudFolderRootStats = copy(
        fileCount = fileCount.coerceAtLeast(0),
        directoryCount = directoryCount.coerceAtLeast(0),
        totalBytes = totalBytes.coerceAtLeast(0L),
    )
}

/**
 * A logical root shared by all devices.  It describes a folder, not a local
 * folder grant.  [manifestRevision] is informational; the manifest itself is
 * authoritative for revision checks.
 */
@Serializable
data class CloudFolderRoot(
    val rootId: String,
    val name: String,
    val createdAt: Long = 0L,
    val createdByDeviceId: String = "",
    val updatedAt: Long = createdAt,
    val manifestRevision: Long = 0L,
    val stats: CloudFolderRootStats = CloudFolderRootStats(),
    val isDeleted: Boolean = false,
) {
    val id: String get() = rootId
    val displayName: String get() = name

    fun sanitized(): CloudFolderRoot = copy(
        rootId = rootId.trim(),
        name = name.trim(),
        manifestRevision = manifestRevision.coerceAtLeast(0L),
        stats = stats.sanitized(),
    )
}

/**
 * Per-device state for a logical root.  The local URI is only persisted on
 * the device that owns it; [toPortable] is safe to send to cloud code.
 */
@Serializable
data class CloudFolderDeviceBinding(
    val rootId: String,
    val deviceId: String,
    val localUri: String? = null,
    val permissionState: CloudFolderPermissionState = CloudFolderPermissionState.UNKNOWN,
    val materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
    val lastAcknowledgedRevision: Long = 0L,
    val lastScanAt: Long = 0L,
    val lastError: String? = null,
) {
    /** Portable projection that cannot leak a provider URI or permission state. */
    fun toPortable(): CloudFolderPortableBinding = CloudFolderPortableBinding(
        rootId = rootId,
        deviceId = deviceId,
        materializationMode = materializationMode,
        lastAcknowledgedRevision = lastAcknowledgedRevision.coerceAtLeast(0L),
    )
}

@Serializable
data class CloudFolderPortableBinding(
    val rootId: String,
    val deviceId: String,
    val materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
    val lastAcknowledgedRevision: Long = 0L,
)

/**
 * One logical directory or file.  Node IDs are assigned once and retained by
 * the local inventory across renames.  [cloudFolderNodeId] is only a
 * deterministic bootstrap for providers that do not expose an inventory ID;
 * adapters should persist the returned ID rather than deriving it repeatedly.
 */
@Serializable
data class CloudFolderNode(
    val nodeId: String,
    val rootId: String,
    val relativePath: String,
    val kind: CloudFolderNodeKind,
    /** Lower-case SHA-256 hex, optionally prefixed with `sha256:`. */
    val contentHash: String? = null,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val fileModifiedAt: Long = 0L,
    val revision: Long = 0L,
    val modifiedAt: Long = 0L,
    val modifiedByDeviceId: String = "",
    /** Drive/object ID; never used as logical identity or conflict identity. */
    val contentObjectId: String? = null,
) {
    val isDirectory: Boolean get() = kind == CloudFolderNodeKind.DIRECTORY
    val isFile: Boolean get() = kind == CloudFolderNodeKind.FILE
    val sha256: String? get() = contentHash
    val pathKey: String get() = cloudFolderPathKey(relativePath)

    fun sanitized(): CloudFolderNode = copy(
        nodeId = nodeId.trim(),
        rootId = rootId.trim(),
        relativePath = normalizeCloudFolderRelativePath(relativePath) ?: relativePath.trim(),
        contentHash = canonicalCloudFolderContentHash(contentHash),
        sizeBytes = sizeBytes.coerceAtLeast(0L),
        revision = revision.coerceAtLeast(0L),
    )
}

/** A deletion record retained until every device has observed it. */
@Serializable
data class CloudFolderTombstone(
    val nodeId: String,
    val rootId: String,
    val relativePath: String,
    val kind: CloudFolderNodeKind,
    val deletedAt: Long = 0L,
    val deletedRevision: Long = 0L,
    val deletedByDeviceId: String = "",
    val lastKnownContentHash: String? = null,
    val lastKnownSizeBytes: Long = 0L,
) {
    val pathKey: String get() = cloudFolderPathKey(relativePath)

    fun sanitized(): CloudFolderTombstone = copy(
        nodeId = nodeId.trim(),
        rootId = rootId.trim(),
        relativePath = normalizeCloudFolderRelativePath(relativePath) ?: relativePath.trim(),
        deletedRevision = deletedRevision.coerceAtLeast(0L),
        lastKnownContentHash = canonicalCloudFolderContentHash(lastKnownContentHash),
        lastKnownSizeBytes = lastKnownSizeBytes.coerceAtLeast(0L),
    )
}

typealias CloudFolderNodeTombstone = CloudFolderTombstone

/**
 * Device-neutral cloud state.  It can be written to Drive/Firestore as one
 * immutable revision and reconstructed on another device without that
 * device knowing where the source folder lived.
 */
@Serializable
data class CloudFolderManifest(
    val schemaVersion: Int = CLOUD_FOLDER_MANIFEST_SCHEMA_VERSION,
    val root: CloudFolderRoot,
    val revision: Long = 0L,
    val baseRevision: Long = 0L,
    val generatedAt: Long = 0L,
    val generatedByDeviceId: String = "",
    val nodes: List<CloudFolderNode> = emptyList(),
    val tombstones: List<CloudFolderTombstone> = emptyList(),
) {
    val rootId: String get() = root.rootId

    fun activeNodes(): List<CloudFolderNode> = nodes
        .filter { it.rootId == rootId }
        .map { it.sanitized() }
        .sortedWith(compareBy<CloudFolderNode> { it.pathKey }.thenBy { it.nodeId })

    fun activeFiles(): List<CloudFolderNode> = activeNodes().filter { it.isFile }

    fun activeDirectories(): List<CloudFolderNode> = activeNodes().filter { it.isDirectory }

    fun statistics(scannedAt: Long = generatedAt): CloudFolderRootStats {
        val active = activeNodes()
        return CloudFolderRootStats(
            fileCount = active.count { it.isFile },
            directoryCount = active.count { it.isDirectory },
            totalBytes = active.filter { it.isFile }.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            scannedAt = scannedAt,
            scanComplete = true,
        )
    }

    fun normalized(): CloudFolderManifest {
        val normalizedNodes = nodes.map { it.sanitized() }
        val normalizedTombstones = tombstones.map { it.sanitized() }
        return copy(
            schemaVersion = schemaVersion.coerceAtLeast(1),
            root = root.sanitized(),
            revision = revision.coerceAtLeast(0L),
            baseRevision = baseRevision.coerceAtLeast(0L),
            nodes = normalizedNodes.sortedWith(compareBy<CloudFolderNode> { it.nodeId }.thenBy { it.revision }),
            tombstones = normalizedTombstones.sortedWith(
                compareBy<CloudFolderTombstone> { it.nodeId }.thenBy { it.deletedRevision }
            ),
        )
    }

    /** Update informational root statistics after a successful manifest write. */
    fun withUpdatedRootStats(scannedAt: Long = generatedAt): CloudFolderManifest {
        val stats = statistics(scannedAt)
        return copy(root = root.copy(stats = stats, manifestRevision = revision))
    }

    companion object {
        fun empty(root: CloudFolderRoot): CloudFolderManifest = CloudFolderManifest(root = root)
    }
}

/** Relative paths are slash-separated and never escape the selected root. */
fun normalizeCloudFolderRelativePath(path: String): String? {
    val raw = path.trim().replace('\\', '/')
    if (raw.isBlank() || raw.startsWith('/') || raw.contains('\u0000')) return null
    val segments = raw.split('/')
    if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
    return segments.joinToString("/")
}

/** Case-folded key used for collision detection across Android/iOS providers. */
fun cloudFolderPathKey(path: String): String =
    (normalizeCloudFolderRelativePath(path) ?: path.trim().replace('\\', '/')).lowercase()

/** Canonical form for hashes from providers and content adapters. */
fun canonicalCloudFolderContentHash(hash: String?): String? {
    val value = hash?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return if (value.startsWith("sha256:")) {
        "sha256:${value.removePrefix("sha256:")}"
    } else {
        value
    }
}

fun isCloudFolderSha256(hash: String?): Boolean {
    val canonical = canonicalCloudFolderContentHash(hash) ?: return false
    val hex = canonical.removePrefix("sha256:")
    return hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }
}

/**
 * Deterministic bootstrap ID only.  Once a scanner has an inventory, it must
 * retain the ID so a rename remains a move instead of delete-plus-add.
 */
fun cloudFolderNodeId(rootId: String, relativePath: String): String {
    val normalized = normalizeCloudFolderRelativePath(relativePath) ?: relativePath.trim()
    return "folder_node_${localFolderSyncSha256ShortHex("${rootId.trim()}\u0000$normalized")}" 
}

fun cloudFolderRootId(stableSeed: String): String =
    "folder_root_${localFolderSyncSha256ShortHex(stableSeed.trim())}"

/** Manifest validation is intentionally read-only; adapters decide how to report issues. */
@Serializable
enum class CloudFolderManifestIssueType {
    INVALID_ROOT,
    INVALID_PATH,
    DUPLICATE_NODE_ID,
    DUPLICATE_PATH,
    NEGATIVE_SIZE,
    NODE_ROOT_MISMATCH,
    TOMBSTONE_ROOT_MISMATCH,
    NODE_TOMBSTONE_COLLISION,
}

@Serializable
data class CloudFolderManifestIssue(
    val type: CloudFolderManifestIssueType,
    val nodeId: String? = null,
    val path: String? = null,
)

fun CloudFolderManifest.validationIssues(): List<CloudFolderManifestIssue> {
    val issues = mutableListOf<CloudFolderManifestIssue>()
    if (root.rootId.trim().isBlank() || root.name.trim().isBlank()) {
        issues += CloudFolderManifestIssue(CloudFolderManifestIssueType.INVALID_ROOT)
    }
    val seenIds = mutableSetOf<String>()
    val seenPaths = mutableMapOf<String, String>()
    nodes.forEach { node ->
        if (node.rootId != root.rootId) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.NODE_ROOT_MISMATCH,
                nodeId = node.nodeId,
                path = node.relativePath,
            )
        }
        if (node.nodeId.trim().isBlank() || !seenIds.add(node.nodeId)) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.DUPLICATE_NODE_ID,
                nodeId = node.nodeId,
                path = node.relativePath,
            )
        }
        if (normalizeCloudFolderRelativePath(node.relativePath) == null) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.INVALID_PATH,
                nodeId = node.nodeId,
                path = node.relativePath,
            )
        }
        if (node.sizeBytes < 0L) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.NEGATIVE_SIZE,
                nodeId = node.nodeId,
                path = node.relativePath,
            )
        }
        val pathKey = cloudFolderPathKey(node.relativePath)
        val previous = seenPaths.put(pathKey, node.nodeId)
        if (previous != null && previous != node.nodeId) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.DUPLICATE_PATH,
                nodeId = node.nodeId,
                path = node.relativePath,
            )
        }
    }
    val nodeIds = nodes.mapTo(mutableSetOf(), CloudFolderNode::nodeId)
    tombstones.forEach { tombstone ->
        if (tombstone.rootId != root.rootId) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.TOMBSTONE_ROOT_MISMATCH,
                nodeId = tombstone.nodeId,
                path = tombstone.relativePath,
            )
        }
        if (normalizeCloudFolderRelativePath(tombstone.relativePath) == null) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.INVALID_PATH,
                nodeId = tombstone.nodeId,
                path = tombstone.relativePath,
            )
        }
        if (tombstone.nodeId in nodeIds) {
            issues += CloudFolderManifestIssue(
                type = CloudFolderManifestIssueType.NODE_TOMBSTONE_COLLISION,
                nodeId = tombstone.nodeId,
                path = tombstone.relativePath,
            )
        }
    }
    return issues
}

@Serializable
enum class CloudFolderSyncDirection {
    NONE,
    LOCAL_TO_CLOUD,
    CLOUD_TO_LOCAL,
}

/** Concrete platform work emitted by the pure planner. */
@Serializable
enum class CloudFolderSyncOperationKind {
    CREATE_REMOTE_DIRECTORY,
    UPLOAD_FILE,
    MOVE_REMOTE,
    UPDATE_REMOTE_METADATA,
    DELETE_REMOTE,
    CREATE_LOCAL_DIRECTORY,
    DOWNLOAD_FILE,
    MOVE_LOCAL,
    UPDATE_LOCAL_METADATA,
    DELETE_LOCAL,
}

@Serializable
data class CloudFolderSyncOperation(
    val nodeId: String,
    val kind: CloudFolderSyncOperationKind,
    val direction: CloudFolderSyncDirection,
    val relativePath: String,
    val previousRelativePath: String? = null,
    val contentHash: String? = null,
    val sizeBytes: Long = 0L,
    val revision: Long = 0L,
)

@Serializable
enum class CloudFolderConflictType {
    CONTENT_CHANGED_BOTH,
    METADATA_CHANGED_BOTH,
    MOVE_CHANGED_BOTH,
    DELETE_VS_UPDATE,
    UPDATE_VS_DELETE,
    TYPE_CHANGED,
    PATH_COLLISION,
    ROOT_METADATA_CHANGED_BOTH,
    ROOT_MISMATCH,
    INVALID_PATH,
    UNAVAILABLE_STATE,
}

@Serializable
data class CloudFolderConflict(
    val conflictId: String,
    val rootId: String,
    val nodeId: String,
    val type: CloudFolderConflictType,
    val relativePath: String,
    val relatedNodeIds: List<String> = emptyList(),
    val baseNode: CloudFolderNode? = null,
    val localNode: CloudFolderNode? = null,
    val remoteNode: CloudFolderNode? = null,
    val baseTombstone: CloudFolderTombstone? = null,
    val localTombstone: CloudFolderTombstone? = null,
    val remoteTombstone: CloudFolderTombstone? = null,
)

@Serializable
enum class CloudFolderConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH,
    DEFER,
}

/**
 * Result of a three-way merge.  When conflicts exist, [mergedManifest] keeps
 * the common-base value for those entries and [canCommit] is false.  This is a
 * deliberate safety invariant: an executor cannot accidentally delete or
 * overwrite a file merely by committing an unresolved candidate.
 */
@Serializable
data class CloudFolderSyncPlan(
    val rootId: String,
    val baseRevision: Long,
    val localRevision: Long,
    val remoteRevision: Long,
    val nextRevision: Long,
    val operations: List<CloudFolderSyncOperation> = emptyList(),
    val conflicts: List<CloudFolderConflict> = emptyList(),
    val mergedManifest: CloudFolderManifest,
) {
    val canCommit: Boolean get() = conflicts.isEmpty()
    val requiresUserAction: Boolean get() = conflicts.isNotEmpty()
    val isNoOp: Boolean get() = operations.isEmpty() && conflicts.isEmpty()

    fun conflict(id: String): CloudFolderConflict? = conflicts.firstOrNull { it.conflictId == id }
}

private sealed interface CloudFolderEntryState {
    data object Absent : CloudFolderEntryState
    data class Active(val node: CloudFolderNode) : CloudFolderEntryState
    data class Deleted(val tombstone: CloudFolderTombstone) : CloudFolderEntryState
}

private fun CloudFolderManifest.entryStates(): Map<String, CloudFolderEntryState> {
    val byId = linkedMapOf<String, CloudFolderEntryState>()
    nodes.forEach { node ->
        if (node.rootId != root.rootId) return@forEach
        val candidate = CloudFolderEntryState.Active(node.sanitized())
        val current = byId[node.nodeId]
        if (current == null || candidate.isNewerThan(current)) byId[node.nodeId] = candidate
    }
    tombstones.forEach { tombstone ->
        if (tombstone.rootId != root.rootId) return@forEach
        val candidate = CloudFolderEntryState.Deleted(tombstone.sanitized())
        val current = byId[tombstone.nodeId]
        if (current == null || candidate.isNewerThan(current)) byId[tombstone.nodeId] = candidate
    }
    return byId
}

private fun CloudFolderEntryState.isNewerThan(other: CloudFolderEntryState): Boolean {
    val thisRevision = when (this) {
        CloudFolderEntryState.Absent -> Long.MIN_VALUE
        is CloudFolderEntryState.Active -> node.revision
        is CloudFolderEntryState.Deleted -> tombstone.deletedRevision
    }
    val otherRevision = when (other) {
        CloudFolderEntryState.Absent -> Long.MIN_VALUE
        is CloudFolderEntryState.Active -> other.node.revision
        is CloudFolderEntryState.Deleted -> other.tombstone.deletedRevision
    }
    // A deletion wins equal-revision ties, preventing stale active data from
    // resurrecting a file when a provider emits both records.
    return thisRevision > otherRevision ||
        (thisRevision == otherRevision && this is CloudFolderEntryState.Deleted && other !is CloudFolderEntryState.Deleted)
}

private fun entryStatesEquivalent(
    first: CloudFolderEntryState,
    second: CloudFolderEntryState,
): Boolean = when {
    first is CloudFolderEntryState.Absent && second is CloudFolderEntryState.Absent -> true
    first is CloudFolderEntryState.Active && second is CloudFolderEntryState.Active ->
        cloudFolderNodesEquivalent(first.node, second.node)
    first is CloudFolderEntryState.Deleted && second is CloudFolderEntryState.Deleted ->
        cloudFolderTombstonesEquivalent(first.tombstone, second.tombstone)
    else -> false
}

/** Equality for merge decisions excludes provider object IDs and wall clocks. */
private fun cloudFolderNodesEquivalent(first: CloudFolderNode, second: CloudFolderNode): Boolean {
    if (first.rootId != second.rootId) return false
    if (first.kind != second.kind) return false
    if (normalizeCloudFolderRelativePath(first.relativePath) != normalizeCloudFolderRelativePath(second.relativePath)) {
        return false
    }
    if (first.kind == CloudFolderNodeKind.DIRECTORY) return true
    val firstHash = canonicalCloudFolderContentHash(first.contentHash)
    val secondHash = canonicalCloudFolderContentHash(second.contentHash)
    if (firstHash != null && secondHash != null) {
        return firstHash == secondHash && first.sizeBytes == second.sizeBytes
    }
    // Hashes are expected from an uploader.  Until one is available, retain
    // timestamp/size checks rather than silently treating changed bytes as
    // equal.
    return firstHash == secondHash &&
        first.sizeBytes == second.sizeBytes &&
        first.fileModifiedAt == second.fileModifiedAt &&
        first.mimeType == second.mimeType
}

private fun cloudFolderTombstonesEquivalent(
    first: CloudFolderTombstone,
    second: CloudFolderTombstone,
): Boolean = first.rootId == second.rootId &&
    first.kind == second.kind &&
    normalizeCloudFolderRelativePath(first.relativePath) == normalizeCloudFolderRelativePath(second.relativePath) &&
    canonicalCloudFolderContentHash(first.lastKnownContentHash) ==
        canonicalCloudFolderContentHash(second.lastKnownContentHash)

private fun CloudFolderEntryState.activeNodeOrNull(): CloudFolderNode? =
    (this as? CloudFolderEntryState.Active)?.node

private fun CloudFolderEntryState.tombstoneOrNull(): CloudFolderTombstone? =
    (this as? CloudFolderEntryState.Deleted)?.tombstone

private fun CloudFolderEntryState.hasInvalidPath(): Boolean = when (this) {
    CloudFolderEntryState.Absent -> false
    is CloudFolderEntryState.Active -> normalizeCloudFolderRelativePath(node.relativePath) == null
    is CloudFolderEntryState.Deleted -> normalizeCloudFolderRelativePath(tombstone.relativePath) == null
}

private fun conflictType(
    local: CloudFolderEntryState,
    remote: CloudFolderEntryState,
): CloudFolderConflictType {
    if (local is CloudFolderEntryState.Active && remote is CloudFolderEntryState.Active) {
        if (local.node.kind != remote.node.kind) return CloudFolderConflictType.TYPE_CHANGED
        val localPath = normalizeCloudFolderRelativePath(local.node.relativePath)
        val remotePath = normalizeCloudFolderRelativePath(remote.node.relativePath)
        if (localPath == null || remotePath == null) return CloudFolderConflictType.INVALID_PATH
        if (localPath != remotePath) return CloudFolderConflictType.MOVE_CHANGED_BOTH
        val localHash = canonicalCloudFolderContentHash(local.node.contentHash)
        val remoteHash = canonicalCloudFolderContentHash(remote.node.contentHash)
        if (localHash != remoteHash || local.node.sizeBytes != remote.node.sizeBytes) {
            return CloudFolderConflictType.CONTENT_CHANGED_BOTH
        }
        return CloudFolderConflictType.METADATA_CHANGED_BOTH
    }
    if (local is CloudFolderEntryState.Deleted && remote is CloudFolderEntryState.Active) {
        return CloudFolderConflictType.DELETE_VS_UPDATE
    }
    if (local is CloudFolderEntryState.Active && remote is CloudFolderEntryState.Deleted) {
        return CloudFolderConflictType.UPDATE_VS_DELETE
    }
    if (local is CloudFolderEntryState.Absent || remote is CloudFolderEntryState.Absent) {
        return CloudFolderConflictType.UNAVAILABLE_STATE
    }
    return CloudFolderConflictType.METADATA_CHANGED_BOTH
}

private fun cloudFolderConflictId(
    rootId: String,
    nodeIds: Collection<String>,
    type: CloudFolderConflictType,
    path: String,
): String = "folder_conflict_${localFolderSyncSha256ShortHex(
    listOf(rootId, nodeIds.sorted().joinToString(","), type.name, path).joinToString("\u0000")
)}"

private fun cloudFolderRootEquivalent(first: CloudFolderRoot, second: CloudFolderRoot): Boolean =
    first.rootId == second.rootId &&
        first.name.trim() == second.name.trim() &&
        first.isDeleted == second.isDeleted &&
        first.createdAt == second.createdAt &&
        first.createdByDeviceId == second.createdByDeviceId

private fun choosePreferredState(
    first: CloudFolderEntryState,
    second: CloudFolderEntryState,
): CloudFolderEntryState {
    if (first is CloudFolderEntryState.Absent) return second
    if (second is CloudFolderEntryState.Absent) return first
    return if (first.isNewerThan(second)) first else second
}

private fun nextCloudFolderRevision(base: Long, local: Long, remote: Long): Long {
    val highest = maxOf(base, local, remote, 0L)
    return if (highest == Long.MAX_VALUE) Long.MAX_VALUE else highest + 1L
}

private fun CloudFolderNode.contentChangedFrom(base: CloudFolderNode?): Boolean {
    if (base == null || kind != base.kind) return true
    if (kind == CloudFolderNodeKind.DIRECTORY) return false
    val thisHash = canonicalCloudFolderContentHash(contentHash)
    val baseHash = canonicalCloudFolderContentHash(base.contentHash)
    if (thisHash != null && baseHash != null) {
        return thisHash != baseHash || sizeBytes != base.sizeBytes
    }
    return thisHash != baseHash || sizeBytes != base.sizeBytes || fileModifiedAt != base.fileModifiedAt
}

private fun operationForChangedState(
    nodeId: String,
    state: CloudFolderEntryState,
    baseline: CloudFolderEntryState,
    direction: CloudFolderSyncDirection,
): CloudFolderSyncOperation? {
    val targetIsCloud = direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD
    val node = state.activeNodeOrNull()
    if (node != null) {
        val baselineNode = baseline.activeNodeOrNull()
        val previousPath = baselineNode?.relativePath
        val pathChanged = previousPath != null &&
            normalizeCloudFolderRelativePath(previousPath) != normalizeCloudFolderRelativePath(node.relativePath)
        val contentChanged = node.contentChangedFrom(baselineNode)
        val kind = when {
            node.kind == CloudFolderNodeKind.DIRECTORY && pathChanged ->
                if (targetIsCloud) CloudFolderSyncOperationKind.MOVE_REMOTE else CloudFolderSyncOperationKind.MOVE_LOCAL
            node.kind == CloudFolderNodeKind.DIRECTORY ->
                if (targetIsCloud) CloudFolderSyncOperationKind.CREATE_REMOTE_DIRECTORY
                else CloudFolderSyncOperationKind.CREATE_LOCAL_DIRECTORY
            pathChanged -> if (targetIsCloud) CloudFolderSyncOperationKind.MOVE_REMOTE else CloudFolderSyncOperationKind.MOVE_LOCAL
            contentChanged || baselineNode == null ->
                if (targetIsCloud) CloudFolderSyncOperationKind.UPLOAD_FILE
                else CloudFolderSyncOperationKind.DOWNLOAD_FILE
            else -> if (targetIsCloud) CloudFolderSyncOperationKind.UPDATE_REMOTE_METADATA
            else CloudFolderSyncOperationKind.UPDATE_LOCAL_METADATA
        }
        return CloudFolderSyncOperation(
            nodeId = nodeId,
            kind = kind,
            direction = direction,
            relativePath = node.relativePath,
            previousRelativePath = previousPath.takeIf { pathChanged },
            contentHash = canonicalCloudFolderContentHash(node.contentHash),
            sizeBytes = node.sizeBytes.coerceAtLeast(0L),
            revision = node.revision,
        )
    }
    val tombstone = state.tombstoneOrNull() ?: return null
    return CloudFolderSyncOperation(
        nodeId = nodeId,
        kind = if (targetIsCloud) CloudFolderSyncOperationKind.DELETE_REMOTE
        else CloudFolderSyncOperationKind.DELETE_LOCAL,
        direction = direction,
        relativePath = tombstone.relativePath,
        contentHash = canonicalCloudFolderContentHash(tombstone.lastKnownContentHash),
        sizeBytes = tombstone.lastKnownSizeBytes.coerceAtLeast(0L),
        revision = tombstone.deletedRevision,
    )
}

private fun CloudFolderSyncOperationKind.sortRank(): Int = when (this) {
    CloudFolderSyncOperationKind.CREATE_REMOTE_DIRECTORY,
    CloudFolderSyncOperationKind.CREATE_LOCAL_DIRECTORY -> 0
    CloudFolderSyncOperationKind.MOVE_REMOTE,
    CloudFolderSyncOperationKind.MOVE_LOCAL -> 1
    CloudFolderSyncOperationKind.UPLOAD_FILE,
    CloudFolderSyncOperationKind.DOWNLOAD_FILE,
    CloudFolderSyncOperationKind.UPDATE_REMOTE_METADATA,
    CloudFolderSyncOperationKind.UPDATE_LOCAL_METADATA -> 2
    CloudFolderSyncOperationKind.DELETE_REMOTE,
    CloudFolderSyncOperationKind.DELETE_LOCAL -> 3
}

private fun CloudFolderSyncOperation.depth(): Int = relativePath.count { it == '/' }

private fun mergeRoot(
    base: CloudFolderRoot,
    local: CloudFolderRoot,
    remote: CloudFolderRoot,
): Pair<CloudFolderRoot, Boolean> {
    val localChanged = !cloudFolderRootEquivalent(base, local)
    val remoteChanged = !cloudFolderRootEquivalent(base, remote)
    return when {
        localChanged && remoteChanged && !cloudFolderRootEquivalent(local, remote) -> base to true
        localChanged -> local to false
        remoteChanged -> remote to false
        else -> local to false
    }
}

/** Pure, deterministic three-way planner shared by Android, iOS, and desktop. */
object CloudFolderSyncPlanner {
    fun plan(
        base: CloudFolderManifest,
        local: CloudFolderManifest,
        remote: CloudFolderManifest,
        nowMillis: Long = maxOf(local.generatedAt, remote.generatedAt),
        deviceId: String = local.generatedByDeviceId,
    ): CloudFolderSyncPlan {
        val normalizedBase = base.normalized()
        val normalizedLocal = local.normalized()
        val normalizedRemote = remote.normalized()
        val rootId = normalizedBase.root.rootId
        val nextRevision = nextCloudFolderRevision(
            normalizedBase.revision,
            normalizedLocal.revision,
            normalizedRemote.revision,
        )
        val rootMismatch = normalizedLocal.root.rootId != rootId || normalizedRemote.root.rootId != rootId
        if (rootMismatch) {
            val conflict = CloudFolderConflict(
                conflictId = cloudFolderConflictId(
                    rootId,
                    listOf(normalizedLocal.root.rootId, normalizedRemote.root.rootId),
                    CloudFolderConflictType.ROOT_MISMATCH,
                    "",
                ),
                rootId = rootId,
                nodeId = rootId,
                type = CloudFolderConflictType.ROOT_MISMATCH,
                relativePath = "",
                relatedNodeIds = listOf(normalizedLocal.root.rootId, normalizedRemote.root.rootId).distinct(),
            )
            return CloudFolderSyncPlan(
                rootId = rootId,
                baseRevision = normalizedBase.revision,
                localRevision = normalizedLocal.revision,
                remoteRevision = normalizedRemote.revision,
                nextRevision = nextRevision,
                conflicts = listOf(conflict),
                mergedManifest = normalizedBase,
            )
        }

        val (mergedRoot, rootConflict) = mergeRoot(
            normalizedBase.root,
            normalizedLocal.root,
            normalizedRemote.root,
        )
        val baseStates = normalizedBase.entryStates()
        val localStates = normalizedLocal.entryStates()
        val remoteStates = normalizedRemote.entryStates()
        val ids = (baseStates.keys + localStates.keys + remoteStates.keys).toSortedSet()
        val mergedStates = linkedMapOf<String, CloudFolderEntryState>()
        val operations = mutableListOf<CloudFolderSyncOperation>()
        val conflicts = mutableListOf<CloudFolderConflict>()

        if (rootConflict) {
            conflicts += CloudFolderConflict(
                conflictId = cloudFolderConflictId(
                    rootId,
                    listOf(rootId),
                    CloudFolderConflictType.ROOT_METADATA_CHANGED_BOTH,
                    "",
                ),
                rootId = rootId,
                nodeId = rootId,
                type = CloudFolderConflictType.ROOT_METADATA_CHANGED_BOTH,
                relativePath = "",
            )
        }

        ids.forEach { nodeId ->
            val baseState = baseStates[nodeId] ?: CloudFolderEntryState.Absent
            val localState = localStates[nodeId] ?: CloudFolderEntryState.Absent
            val remoteState = remoteStates[nodeId] ?: CloudFolderEntryState.Absent
            val localChanged = !entryStatesEquivalent(baseState, localState)
            val remoteChanged = !entryStatesEquivalent(baseState, remoteState)
            val path = localState.activeNodeOrNull()?.relativePath
                ?: remoteState.activeNodeOrNull()?.relativePath
                ?: localState.tombstoneOrNull()?.relativePath
                ?: remoteState.tombstoneOrNull()?.relativePath
                ?: baseState.activeNodeOrNull()?.relativePath
                ?: baseState.tombstoneOrNull()?.relativePath
                ?: ""
            val chosen: CloudFolderEntryState
            val invalidState = listOf(localState, remoteState).firstOrNull { it.hasInvalidPath() }
            if (invalidState != null) {
                chosen = baseState
                val invalidNode = localState.activeNodeOrNull() ?: remoteState.activeNodeOrNull()
                val invalidTombstone = localState.tombstoneOrNull() ?: remoteState.tombstoneOrNull()
                conflicts += CloudFolderConflict(
                    conflictId = cloudFolderConflictId(
                        rootId,
                        listOf(nodeId),
                        CloudFolderConflictType.INVALID_PATH,
                        path,
                    ),
                    rootId = rootId,
                    nodeId = nodeId,
                    type = CloudFolderConflictType.INVALID_PATH,
                    relativePath = path,
                    baseNode = baseState.activeNodeOrNull(),
                    localNode = localState.activeNodeOrNull(),
                    remoteNode = remoteState.activeNodeOrNull(),
                    baseTombstone = baseState.tombstoneOrNull(),
                    localTombstone = localState.tombstoneOrNull(),
                    remoteTombstone = remoteState.tombstoneOrNull(),
                    relatedNodeIds = listOfNotNull(invalidNode?.nodeId, invalidTombstone?.nodeId).distinct(),
                )
            } else when {
                !localChanged && !remoteChanged -> chosen = choosePreferredState(localState, remoteState)
                localChanged && !remoteChanged -> {
                    chosen = localState
                    operationForChangedState(
                        nodeId,
                        localState,
                        baseState,
                        CloudFolderSyncDirection.LOCAL_TO_CLOUD,
                    )?.let(operations::add)
                }
                !localChanged && remoteChanged -> {
                    chosen = remoteState
                    operationForChangedState(
                        nodeId,
                        remoteState,
                        baseState,
                        CloudFolderSyncDirection.CLOUD_TO_LOCAL,
                    )?.let(operations::add)
                }
                entryStatesEquivalent(localState, remoteState) -> chosen = choosePreferredState(localState, remoteState)
                else -> {
                    chosen = baseState
                    val type = conflictType(localState, remoteState)
                    val conflict = CloudFolderConflict(
                        conflictId = cloudFolderConflictId(rootId, listOf(nodeId), type, path),
                        rootId = rootId,
                        nodeId = nodeId,
                        type = type,
                        relativePath = path,
                        baseNode = baseState.activeNodeOrNull(),
                        localNode = localState.activeNodeOrNull(),
                        remoteNode = remoteState.activeNodeOrNull(),
                        baseTombstone = baseState.tombstoneOrNull(),
                        localTombstone = localState.tombstoneOrNull(),
                        remoteTombstone = remoteState.tombstoneOrNull(),
                    )
                    conflicts += conflict
                }
            }
            mergedStates[nodeId] = chosen
        }

        // A path collision can occur when two new node IDs arrive for the
        // same case-folded path.  Never emit uploads/downloads that would
        // overwrite one another; force an explicit user decision instead.
        val activeByPath = mergedStates
            .mapNotNull { (nodeId, state) -> state.activeNodeOrNull()?.let { nodeId to it } }
            .groupBy { (_, node) -> cloudFolderPathKey(node.relativePath) }
        val collisionNodeIds = mutableSetOf<String>()
        activeByPath.forEach { (pathKey, entries) ->
            val distinctIds = entries.map { it.first }.distinct()
            if (distinctIds.size > 1) {
                collisionNodeIds += distinctIds
                val representative = entries.first().second
                val conflict = CloudFolderConflict(
                    conflictId = cloudFolderConflictId(
                        rootId,
                        distinctIds,
                        CloudFolderConflictType.PATH_COLLISION,
                        representative.relativePath,
                    ),
                    rootId = rootId,
                    nodeId = distinctIds.first(),
                    type = CloudFolderConflictType.PATH_COLLISION,
                    relativePath = pathKey,
                    relatedNodeIds = distinctIds,
                    localNode = normalizedLocal.entryStates()[distinctIds.first()]?.activeNodeOrNull(),
                    remoteNode = normalizedRemote.entryStates()[distinctIds.first()]?.activeNodeOrNull(),
                )
                conflicts += conflict
            }
        }
        if (collisionNodeIds.isNotEmpty()) {
            operations.removeAll { it.nodeId in collisionNodeIds }
        }

        val activeNodes = mergedStates
            .filterValues { it is CloudFolderEntryState.Active }
            .values
            .mapNotNull { it.activeNodeOrNull() }
            .filter { it.nodeId !in collisionNodeIds }
            .sortedWith(compareBy<CloudFolderNode> { it.pathKey }.thenBy { it.nodeId })
        val tombstones = mergedStates
            .filterValues { it is CloudFolderEntryState.Deleted }
            .values
            .mapNotNull { it.tombstoneOrNull() }
            .sortedWith(compareBy<CloudFolderTombstone> { it.pathKey }.thenBy { it.nodeId })
        val mergedManifest = CloudFolderManifest(
            schemaVersion = maxOf(
                CLOUD_FOLDER_MANIFEST_SCHEMA_VERSION,
                normalizedBase.schemaVersion,
                normalizedLocal.schemaVersion,
                normalizedRemote.schemaVersion,
            ),
            root = mergedRoot.copy(
                manifestRevision = nextRevision,
                updatedAt = nowMillis,
                stats = CloudFolderRootStats(
                    fileCount = activeNodes.count { it.isFile },
                    directoryCount = activeNodes.count { it.isDirectory },
                    totalBytes = activeNodes.filter { it.isFile }.sumOf { it.sizeBytes.coerceAtLeast(0L) },
                    scannedAt = nowMillis,
                    scanComplete = true,
                ),
            ),
            revision = nextRevision,
            baseRevision = maxOf(normalizedLocal.revision, normalizedRemote.revision),
            generatedAt = nowMillis,
            generatedByDeviceId = deviceId,
            nodes = activeNodes,
            tombstones = tombstones,
        )
        val sortedOperations = operations.sortedWith(
            compareBy<CloudFolderSyncOperation> { it.kind.sortRank() }
                .thenByDescending { if (it.kind == CloudFolderSyncOperationKind.DELETE_LOCAL || it.kind == CloudFolderSyncOperationKind.DELETE_REMOTE) it.depth() else -it.depth() }
                .thenBy { it.relativePath.lowercase() }
                .thenBy { it.nodeId },
        )
        return CloudFolderSyncPlan(
            rootId = rootId,
            baseRevision = normalizedBase.revision,
            localRevision = normalizedLocal.revision,
            remoteRevision = normalizedRemote.revision,
            nextRevision = nextRevision,
            operations = sortedOperations,
            conflicts = conflicts.distinctBy(CloudFolderConflict::conflictId),
            mergedManifest = mergedManifest,
        )
    }
}

fun planCloudFolderSync(
    base: CloudFolderManifest,
    local: CloudFolderManifest,
    remote: CloudFolderManifest,
    nowMillis: Long = maxOf(local.generatedAt, remote.generatedAt),
    deviceId: String = local.generatedByDeviceId,
): CloudFolderSyncPlan = CloudFolderSyncPlanner.plan(base, local, remote, nowMillis, deviceId)
