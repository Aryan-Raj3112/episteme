package com.aryan.reader.shared

/**
 * Shared local-manifest builder for cloud-folder sync.
 *
 * Android is the absolute benchmark and is NOT changed. This is a direct port
 * of `buildLocalManifest` + `localNodesEquivalent` from
 * app/src/main/java/com/aryan/reader/CloudFolderSyncWorker.kt:4053-4158, with
 * the only change being the scan input: Android passes its
 * `CloudFolderSafScanResult` wrapper, here it is a plain list of scanned
 * nodes so Swift (and tests) can use it without Android types. Node-ID
 * retention across scans stays the scanner's job (retain by path key, else
 * `cloudFolderNodeId`), exactly like Android's `nodeIdFor`.
 *
 * The planner (`planCloudFolderSync`) consumes the result; the executor
 * uploads `UPLOAD_FILE` operations before publishing.
 */
fun buildCloudFolderLocalManifest(
    base: CloudFolderManifest,
    scannedNodes: List<CloudFolderNode>,
    nowMillis: Long,
    deviceId: String,
): CloudFolderManifest {
    val previousById = base.activeNodes().associateBy { it.nodeId }
    // Compare through the same logical-metadata stabilization as the node
    // mapping below, so the revision watermark and the emitted nodes can
    // never disagree about whether the snapshot changed.
    val stabilizedNodes = scannedNodes.map { node ->
        previousById[node.nodeId]?.let {
            stabilizedCloudFolderNodeMetadata(scanned = node, committed = it)
        } ?: node
    }
    val scannedIds = stabilizedNodes.mapTo(hashSetOf(), CloudFolderNode::nodeId)
    val changed = stabilizedNodes.any { node ->
        val previous = previousById[node.nodeId]
        previous == null || !cloudFolderLocalNodesEquivalent(previous, node)
    } || previousById.keys.any { it !in scannedIds }
    val nextRevision = if (changed) {
        if (base.revision == Long.MAX_VALUE) Long.MAX_VALUE else base.revision + 1L
    } else {
        base.revision
    }
    val nodes = stabilizedNodes.map { node ->
        val previous = previousById[node.nodeId]
        val same = previous != null && cloudFolderLocalNodesEquivalent(previous, node)
        // A stale mtime or re-guessed MIME type must not strip the only
        // pointer to the uploaded bytes: as long as the authenticated hash
        // and size are unchanged, the provider object ID stays valid and
        // publishing it keeps other devices able to download the content.
        val contentUnchanged = previous != null &&
            previous.sizeBytes == node.sizeBytes &&
            canonicalCloudFolderContentHash(previous.contentHash) ==
                canonicalCloudFolderContentHash(node.contentHash) &&
            canonicalCloudFolderContentHash(node.contentHash) != null
        node.copy(
            revision = if (same) previous!!.revision else nextRevision,
            modifiedAt = nowMillis,
            modifiedByDeviceId = deviceId,
            contentObjectId = if (same || contentUnchanged) previous?.contentObjectId else null,
        )
    }
    val newTombstones = previousById
        .filterKeys { it !in scannedIds }
        .map { (_, previous) ->
            CloudFolderTombstone(
                nodeId = previous.nodeId,
                rootId = base.rootId,
                relativePath = previous.relativePath,
                kind = previous.kind,
                deletedAt = nowMillis,
                deletedRevision = nextRevision,
                deletedByDeviceId = deviceId,
                lastKnownContentHash = previous.contentHash,
                lastKnownSizeBytes = previous.sizeBytes,
            )
        }
    val tombstones = (base.tombstones + newTombstones)
        .filterNot { it.nodeId in scannedIds }
        .distinctBy { it.nodeId }
    val stats = CloudFolderRootStats(
        fileCount = nodes.count { it.isFile },
        directoryCount = nodes.count { it.isDirectory },
        totalBytes = nodes.filter { it.isFile }.sumOf { it.sizeBytes.coerceAtLeast(0L) },
        scannedAt = nowMillis,
        scanComplete = true,
    )
    return CloudFolderManifest(
        root = base.root.copy(
            updatedAt = nowMillis,
            manifestRevision = nextRevision,
            stats = stats,
        ),
        revision = nextRevision,
        baseRevision = base.revision,
        generatedAt = nowMillis,
        generatedByDeviceId = deviceId,
        nodes = nodes,
        tombstones = tombstones,
    ).normalized()
}

/**
 * Local-snapshot equivalence for merge decisions. Sidecars are logical
 * records, not user documents: the shared planner deliberately ignores their
 * provider mtimes and MIME types (atomic temp-then-rename writes always churn
 * both), so a same-byte rewrite must not be promoted into a local edit that
 * every device would then re-publish in a loop.
 */
fun cloudFolderLocalNodesEquivalent(first: CloudFolderNode, second: CloudFolderNode): Boolean {
    if (first.rootId != second.rootId ||
        first.kind != second.kind ||
        first.relativePath != second.relativePath ||
        first.sizeBytes != second.sizeBytes ||
        canonicalCloudFolderContentHash(first.contentHash) !=
            canonicalCloudFolderContentHash(second.contentHash)
    ) {
        return false
    }
    if (isCloudFolderMetadataSidecarPath(first.relativePath)) return true
    return first.mimeType == second.mimeType &&
        first.fileModifiedAt == second.fileModifiedAt
}
