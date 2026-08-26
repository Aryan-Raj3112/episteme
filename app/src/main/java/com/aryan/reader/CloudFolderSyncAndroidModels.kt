package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderSyncFolderOption
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
        val binding = mappedRootId?.let(deviceBindings::get)
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
            fileCount = repositoryStat?.fileCount ?: sizes.size,
            totalBytes = repositoryStat?.totalBytes ?: sizes.sum(),
            isAvailable = mappedRootId != null,
            materializationMode = binding?.materializationMode
                ?: CloudFolderMaterializationMode.LOCAL_MIRROR,
            isBoundLocally = binding?.localUri?.isNotBlank() == true || mappedRootId != null,
            isRemote = false,
            isSelectable = mappedRootId != null,
            lastError = binding?.lastError,
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
            )
        }
        .filterNot { it.normalizedRootId in localRootIds }
        .toList()

    return (localOptions + remoteOptions)
        .distinctBy { it.normalizedRootId }
}
