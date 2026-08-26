package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
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
): List<CloudFolderSyncFolderOption> {
    val statsByUri = indexedFiles
        .asSequence()
        .mapNotNull { item ->
            item.sourceFolderUri
                ?.takeIf { it.isNotBlank() }
                ?.let { uri -> uri to item.fileSize.coerceAtLeast(0L) }
        }
        .groupBy({ it.first }, { it.second })

    return folders.map { folder ->
        val sizes = statsByUri[folder.uriString].orEmpty()
        val mappedRootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() }
        val repositoryStat = mappedRootId?.let(repositoryStats::get)
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
        )
    }
}
