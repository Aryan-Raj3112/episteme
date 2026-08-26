package com.aryan.reader.shared

import kotlinx.serialization.Serializable

/**
 * The small, device-neutral projection used by folder-sync settings screens.
 * A folder option is deliberately not a [SyncedFolder]: a local SAF/bookmark
 * URI is a device binding and must never become the account-level identity.
 */
data class CloudFolderSyncFolderOption(
    val rootId: String,
    val displayName: String,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val isAvailable: Boolean = true,
    /** How this logical root is currently represented on this device. */
    val materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
    /** Whether this device has a persisted binding, including a cloud-only choice. */
    val isBoundLocally: Boolean = false,
    /** True when this option came from a remote manifest rather than a local folder setting. */
    val isRemote: Boolean = false,
    /** Remote roots without an explicit choice are inventory-only and cannot be selected directly. */
    val isSelectable: Boolean = true,
    val lastError: String? = null,
) {
    val normalizedRootId: String get() = rootId.trim()
    val normalizedDisplayName: String get() = displayName.trim().ifBlank { normalizedRootId }

    fun sanitized(): CloudFolderSyncFolderOption = copy(
        rootId = normalizedRootId,
        displayName = normalizedDisplayName,
        fileCount = fileCount.coerceAtLeast(0),
        totalBytes = totalBytes.coerceAtLeast(0L),
    )
}

/** Compact, device-neutral projection used by the conflict section in
 * folder-sync settings.  The full [CloudFolderConflict] remains persisted so
 * a later executor can apply the decision against the exact snapshot. */
data class CloudFolderConflictUiItem(
    val rootId: String,
    val folderName: String,
    val conflictId: String,
    val relativePath: String,
    val type: CloudFolderConflictType,
    val resolution: CloudFolderConflictResolution = CloudFolderConflictResolution.DEFER,
    val baseRevision: Long = 0L,
    val localRevision: Long = 0L,
    val remoteRevision: Long = 0L,
) {
    val normalizedRootId: String get() = rootId.trim()
    val normalizedFolderName: String get() = folderName.trim().ifBlank { normalizedRootId }
    val normalizedPath: String get() = relativePath.trim().ifBlank { "/" }
}

/**
 * Pure state for the Android folder-sync settings surface.  The local-folder
 * library switch is intentionally separate from account-level cloud
 * selection: turning local indexing on must not silently upload anything.
 */
data class CloudFolderSyncSettingsUiState(
    val localFolderIndexingEnabled: Boolean = false,
    val selection: CloudFolderSyncSelection = CloudFolderSyncSelection.Default,
    val folders: List<CloudFolderSyncFolderOption> = emptyList(),
    val conflicts: List<CloudFolderConflictUiItem> = emptyList(),
) {
    val normalizedFolders: List<CloudFolderSyncFolderOption>
        get() = folders
            .map { it.sanitized() }
            .distinctBy { it.normalizedRootId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.normalizedDisplayName })

    val selectedFolderCount: Int
        get() = normalizedFolders.count {
            it.isAvailable && it.isSelectable && selection.includes(it.normalizedRootId)
        }

    val selectedFileCount: Int
        get() = normalizedFolders
            .filter { it.isAvailable && it.isSelectable && selection.includes(it.normalizedRootId) }
            .sumOf { it.fileCount }

    val selectedTotalBytes: Long
        get() = normalizedFolders
            .filter { it.isAvailable && it.isSelectable && selection.includes(it.normalizedRootId) }
            .sumOf { it.totalBytes }

    val unresolvedConflictCount: Int
        get() = conflicts.count { it.resolution == CloudFolderConflictResolution.DEFER }

    fun normalized(): CloudFolderSyncSettingsUiState {
        val options = normalizedFolders
        return copy(
            // Keep account-selected roots that are not bound on this device;
            // they may be remote roots awaiting the incoming-folder prompt.
            selection = selection.normalized(),
            folders = options,
            conflicts = conflicts
                .map { it.copy(rootId = it.normalizedRootId, folderName = it.normalizedFolderName) }
                .distinctBy { it.conflictId }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.normalizedPath }),
        )
    }

    fun withLocalFolderIndexing(enabled: Boolean): CloudFolderSyncSettingsUiState =
        copy(localFolderIndexingEnabled = enabled)

    fun withSelection(next: CloudFolderSyncSelection): CloudFolderSyncSettingsUiState =
        copy(selection = next).normalized()

    fun includeRoot(rootId: String): CloudFolderSyncSettingsUiState =
        withSelection(selection.withRootIncluded(rootId))

    fun excludeRoot(rootId: String): CloudFolderSyncSettingsUiState =
        withSelection(
            selection.withoutRoot(
                rootId = rootId,
                knownRootIds = normalizedFolders.map { it.normalizedRootId },
            )
        )

    fun selectAllRoots(): CloudFolderSyncSettingsUiState =
        withSelection(selection.includeAllRoots())

    fun excludeAllRoots(): CloudFolderSyncSettingsUiState =
        withSelection(selection.excludeAllRoots())
}

/**
 * A portable prompt shown when another device has published a logical root
 * that this device has not bound to a local folder yet.
 */
@Serializable
data class CloudFolderIncomingFolderPrompt(
    val root: CloudFolderRoot,
    val sourceDeviceName: String? = null,
) {
    val rootId: String get() = root.rootId
    val displayName: String get() = root.name
    val fileCount: Int get() = root.stats.fileCount
    val directoryCount: Int get() = root.stats.directoryCount
    val totalBytes: Long get() = root.stats.totalBytes
}

/** How device 2 should materialize a remote logical folder. */
@Serializable
enum class CloudFolderIncomingChoice(
    /** How the selected root is materialized on this device. */
    val materializationMode: CloudFolderMaterializationMode,
    /** Whether the root must be opted into the local sync selection. */
    val shouldIncludeInLocalSyncSelection: Boolean,
) {
    /** Keep the manifest and cloud content available without a local grant. */
    CLOUD_ONLY(
        materializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
        shouldIncludeInLocalSyncSelection = false,
    ),

    /** Download the complete tree into app-managed local storage. */
    DOWNLOAD_ALL(
        materializationMode = CloudFolderMaterializationMode.KEEP_OFFLINE,
        shouldIncludeInLocalSyncSelection = true,
    ),

    /** Ask for a local folder grant and mirror the tree there. */
    BIND_LOCAL_FOLDER(
        materializationMode = CloudFolderMaterializationMode.LOCAL_MIRROR,
        shouldIncludeInLocalSyncSelection = true,
    ),

}
