package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.CloudFolderLocalInventory
import com.aryan.reader.data.CloudFolderLocalInventoryState
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderSyncFolderOption
import com.aryan.reader.shared.CloudFolderSyncProgress
import com.aryan.reader.shared.CloudFolderRootStats
import com.aryan.reader.shared.cloudFolderRootId

/**
 * Builds the settings projection from the actual local folder bindings and
 * indexed files.  It intentionally does not use the recent-files limit: the
 * dialog should show the complete inventory for each configured root.
 */
internal fun cloudFolderSyncFolderOptions(
    folders: List<SyncedFolder>,
    indexedFiles: List<RecentFileItem>,
    repositoryStats: Map<String, CloudFolderRootStats> = emptyMap(),
    repositoryRoots: List<CloudFolderRoot> = emptyList(),
    deviceBindings: Map<String, CloudFolderDeviceBinding> = emptyMap(),
    syncProgress: Map<String, CloudFolderSyncProgress> = emptyMap(),
    localInventories: Map<String, CloudFolderLocalInventory> = emptyMap(),
): List<CloudFolderSyncFolderOption> {
    val statsByUri = indexedFiles
        .asSequence()
        .mapNotNull { item ->
            item.sourceFolderUri
                ?.takeIf { it.isNotBlank() }
                ?.let { uri -> uri to item.fileSize.coerceAtLeast(0L) }
        }
        .groupBy({ it.first }, { it.second })

    val localOptions = folders.map { folder ->
        val sizes = statsByUri[folder.uriString].orEmpty()
        val mappedRootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() }
        val repositoryStat = mappedRootId?.let(repositoryStats::get)
        val localInventory = mappedRootId?.let(localInventories::get)
        val binding = mappedRootId?.let(deviceBindings::get)
        val repositoryStatsKnown = repositoryStat?.let { stat ->
            stat.scanComplete && stat.scannedAt > 0L
        } == true
        val localStatsKnown = localInventory?.hasKnownStats == true
        // Indexed rows can be stale/partial while a scan is in progress. The
        // persisted scan watermark is the only completion signal for this
        // device-local fallback inventory.
        val fallbackStatsKnown = folder.lastScanTime > 0L
        CloudFolderSyncFolderOption(
            // A logical root ID is a persisted device-local mapping. Legacy
            // URI-only entries remain visible but unavailable until that
            // mapping is migrated; deriving an account identity from a SAF
            // URI would make device 2 create a different root.
            rootId = mappedRootId
                ?: cloudFolderRootId("legacy-local-binding:${folder.uriString}"),
            displayName = folder.name,
            // Repository manifests are authoritative even when this device
            // has local indexing disabled. Indexed rows remain a fallback for
            // legacy entries that have not completed a cloud scan yet.
            fileCount = localInventory?.fileCount ?: repositoryStat?.fileCount ?: sizes.size,
            totalBytes = localInventory?.totalBytes ?: repositoryStat?.totalBytes ?: sizes.sum(),
            sizeKnown = localInventory?.sizeComplete ?: true,
            hasKnownStats = localStatsKnown || repositoryStatsKnown || fallbackStatsKnown,
            scanComplete = when {
                localInventory != null -> localInventory.state == CloudFolderLocalInventoryState.READY
                repositoryStat != null -> repositoryStat.scanComplete && repositoryStat.scannedAt > 0L
                else -> folder.lastScanTime > 0L
            },
            statsUpdatedAt = localInventory?.scannedAt?.takeIf { it > 0L }
                ?: repositoryStat?.scannedAt?.takeIf { it > 0L } ?: folder.lastScanTime,
            isAvailable = mappedRootId != null,
            materializationMode = binding?.materializationMode
                ?: CloudFolderMaterializationMode.LOCAL_MIRROR,
            isBoundLocally = binding?.localUri?.isNotBlank() == true || mappedRootId != null,
            isRemote = false,
            isSelectable = mappedRootId != null,
            lastError = when {
                localInventory?.state == CloudFolderLocalInventoryState.FAILED ->
                    "Local folder scan unavailable"
                else -> binding?.lastError
            },
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
                // A cloud-only choice is still a device binding: it is a
                // durable management decision even though no local bytes are
                // materialized. This keeps it distinct from an unconfigured
                // remote root in the settings inventory.
                isBoundLocally = binding != null,
                isRemote = true,
                // A remote root needs an explicit choice first. Once the
                // user chose KEEP_OFFLINE or bound a SAF tree, normal folder
                // selection controls subsequent sync passes.
                isSelectable = binding != null &&
                    binding.materializationMode != CloudFolderMaterializationMode.CLOUD_ONLY,
                lastError = binding?.lastError,
                syncProgress = normalizedRoot.rootId.let(syncProgress::get),
            )
        }
        .filterNot { it.normalizedRootId in localRootIds }
        .toList()

    return (localOptions + remoteOptions)
        .distinctBy { it.normalizedRootId }
}
