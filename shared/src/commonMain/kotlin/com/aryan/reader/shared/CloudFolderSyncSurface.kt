package com.aryan.reader.shared

/**
 * Shared cloud-folder sync surface projection.
 *
 * Android is the absolute benchmark and is NOT changed by iOS parity work.
 * This file ports the pure projection/selection pieces of Android's folder
 * sync surface into shared code so iOS renders the same policy:
 *
 * - Option projection mirrors `cloudFolderSyncFolderOptions`
 *   (app/src/main/java/com/aryan/reader/CloudFolderSyncAndroidModels.kt:19-132):
 *   local bindings first, remote inventory second, ghost placeholders never
 *   selectable, remote roots selectable only after an explicit KEEP_OFFLINE or
 *   LOCAL_MIRROR binding.
 * - Incoming prompt selection mirrors `refreshCloudFolderSyncState`
 *   (app/src/main/java/com/aryan/reader/MainViewModel.kt:3844-3854,3929-3934):
 *   pending revisions are authoritative, lowest name (case-insensitive) then
 *   rootId wins.
 * - Selection transitions mirror `recordIncomingCloudFolderChoice` and
 *   `setCloudFolderSyncSelection`
 *   (app/src/main/java/com/aryan/reader/MainViewModel.kt:4986-5018,5284-5389):
 *   CLOUD_ONLY never opts into sync selection; DOWNLOAD_ALL and
 *   BIND_LOCAL_FOLDER do; CLOUD_ONLY acknowledges the remote revision
 *   immediately while materializing choices acknowledge after first pull.
 * - Settings tap behavior mirrors Android `SettingsScreen.kt:385-398,445-448`:
 *   FOLDER_SYNC opens the selection surface and nulls the legacy toggle
 *   mutation. iOS must do the same (see IosCloudFolderSyncScreen).
 */

/**
 * Minimal local-folder projection needed for the settings surface.
 * [SyncedFolder] itself is the source; this keeps the function pure and
 * free of platform file-index types.
 */
data class CloudFolderLocalBindingView(
    val uriString: String,
    val name: String,
    val lastScanTime: Long = 0L,
    val cloudRootId: String? = null,
    val isCloudPlaceholder: Boolean = false,
)

fun SyncedFolder.toLocalBindingView(): CloudFolderLocalBindingView =
    CloudFolderLocalBindingView(
        uriString = uriString,
        name = name,
        lastScanTime = lastScanTime,
        cloudRootId = cloudRootId,
        isCloudPlaceholder = isCloudPlaceholder,
    )

/**
 * Build the settings options from local bindings + repository state.
 * Ghost placeholders are excluded from local options (they are inventory-only
 * until materialized) and reappear via the remote inventory when still
 * unbound, exactly like Android.
 */
fun projectCloudFolderSyncOptions(
    localFolders: List<CloudFolderLocalBindingView>,
    repositoryRoots: List<CloudFolderRoot> = emptyList(),
    deviceBindings: Map<String, CloudFolderDeviceBinding> = emptyMap(),
    syncProgress: Map<String, CloudFolderSyncProgress> = emptyMap(),
): List<CloudFolderSyncFolderOption> {
    val localOptions = localFolders
        .filterNot { it.isCloudPlaceholder }
        .map { folder ->
            val mappedRootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() }
            val binding = mappedRootId?.let(deviceBindings::get)
            val fallbackStatsKnown = folder.lastScanTime > 0L
            CloudFolderSyncFolderOption(
                rootId = mappedRootId
                    ?: cloudFolderRootId("legacy-local-binding:${folder.uriString}"),
                displayName = folder.name,
                fileCount = 0,
                totalBytes = 0L,
                sizeKnown = true,
                hasKnownStats = fallbackStatsKnown,
                scanComplete = fallbackStatsKnown,
                statsUpdatedAt = folder.lastScanTime,
                isAvailable = mappedRootId != null,
                materializationMode = binding?.materializationMode
                    ?: CloudFolderMaterializationMode.LOCAL_MIRROR,
                isBoundLocally = binding?.localUri?.isNotBlank() == true || mappedRootId != null,
                isRemote = false,
                isSelectable = mappedRootId != null,
                lastError = binding?.lastError,
                syncProgress = mappedRootId?.let(syncProgress::get),
            )
        }

    val localRootIds = localOptions.mapTo(linkedSetOf()) { it.normalizedRootId }
    val remoteOptions = repositoryRoots
        .asSequence()
        .filterNot { it.isDeleted }
        .map { root ->
            val normalizedRoot = root.sanitized()
            val binding = deviceBindings[normalizedRoot.rootId]
            CloudFolderSyncFolderOption(
                rootId = normalizedRoot.rootId,
                displayName = normalizedRoot.name,
                fileCount = normalizedRoot.stats.fileCount,
                totalBytes = normalizedRoot.stats.totalBytes,
                sizeKnown = true,
                hasKnownStats = normalizedRoot.stats.scanComplete && normalizedRoot.stats.scannedAt > 0L,
                scanComplete = normalizedRoot.stats.scanComplete && normalizedRoot.stats.scannedAt > 0L,
                statsUpdatedAt = normalizedRoot.stats.scannedAt,
                isAvailable = true,
                materializationMode = binding?.materializationMode
                    ?: CloudFolderMaterializationMode.CLOUD_ONLY,
                isBoundLocally = binding != null,
                isRemote = true,
                isSelectable = binding != null &&
                    binding.materializationMode != CloudFolderMaterializationMode.CLOUD_ONLY,
                lastError = binding?.lastError,
                syncProgress = normalizedRoot.rootId.let(syncProgress::get),
            )
        }
        .filterNot { it.normalizedRootId in localRootIds }
        .toList()

    return (localOptions + remoteOptions).distinctBy { it.normalizedRootId }
}

/**
 * Select the single incoming prompt Android would show globally.
 * Pending state is authoritative; ties break by display name then rootId,
 * matching MainViewModel.refreshCloudFolderSyncState.
 */
fun selectCloudFolderIncomingPrompt(
    repositoryRoots: List<CloudFolderRoot>,
    pendingRootIds: Set<String>,
): CloudFolderIncomingFolderPrompt? {
    if (pendingRootIds.isEmpty()) return null
    val promptRoot = repositoryRoots
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { it.rootId in pendingRootIds }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.rootId }))
        .firstOrNull() ?: return null
    return CloudFolderIncomingFolderPrompt(
        root = promptRoot,
        sourceDeviceName = promptRoot.createdByDeviceId.takeIf { it.isNotBlank() },
    )
}

/**
 * Selection after an incoming-folder choice. CLOUD_ONLY stays out of the sync
 * selection; DOWNLOAD_ALL and BIND_LOCAL_FOLDER opt in. Mirrors
 * MainViewModel.recordIncomingCloudFolderChoice.
 */
fun nextSelectionAfterIncomingChoice(
    current: CloudFolderSyncSelection,
    promptRootId: String,
    choice: CloudFolderIncomingChoice,
): CloudFolderSyncSelection =
    if (choice.shouldIncludeInLocalSyncSelection) {
        current.withRootIncluded(promptRootId)
    } else {
        current
    }

/**
 * Whether the choice schedules a pull. Mirrors Android: only materializing
 * choices pull, and only when cloud sync is enabled for the account.
 */
fun shouldPullAfterIncomingChoice(
    choice: CloudFolderIncomingChoice,
    isSyncEnabled: Boolean,
): Boolean = choice.shouldIncludeInLocalSyncSelection && isSyncEnabled

/**
 * Initial acknowledgement revision for a new binding. CLOUD_ONLY acknowledges
 * immediately (no transfer needed); materializing choices start at 0 so the
 * next pull performs the initial download before completing.
 * Mirrors MainViewModel.recordIncomingCloudFolderChoice:5336-5342.
 */
fun initialAcknowledgedRevisionAfterIncomingChoice(
    promptManifestRevision: Long,
    choice: CloudFolderIncomingChoice,
): Long =
    if (choice.materializationMode == CloudFolderMaterializationMode.CLOUD_ONLY) {
        promptManifestRevision
    } else {
        0L
    }
