import Foundation
import ReaderShared

/// Transfer flows for `LocalCloudFolderSyncController` (second half):
/// pull, sync (plan → outbox → publish → materialize), deletes, GC,
/// registration, and state publishing. Stage order mirrors Android's
/// `pullRoot` / `syncRoot` / `deleteRootFromCloud` / `runGarbageCollection`.
extension LocalCloudFolderSyncController {

    // MARK: - pull

    func pullRoot(uid: String, store: CloudFolderStore, rootId: String) async throws {
        guard let binding = store.getBinding(rootId: rootId), includes(rootId) else { return }
        let refs = (try? await transport.listManifestRefs()) ?? [:]
        guard let remoteState = try await readRemoteManifest(uid: uid, store: store, rootId: rootId, ref: refs[rootId]) else {
            return
        }
        let remote = remoteState.manifest
        let mode = binding.materializationMode
        if CloudFolderSyncSwiftBridgeKt.isCloudFolderCloudOnly(mode: mode) {
            store.saveManifest(remote)
            return
        }
        if !pullSafe(store: store, binding: binding, remote: remote) {
            // Local edits: hand off to SYNC instead of overwriting, mirroring
            // Android's `handoffToLocalChangeSync`.
            store.markBindingError(rootId: rootId, message: "Local changes need upload before download")
            submit(Request(direction: .sync, rootId: rootId, replace: false))
            return
        }
        if CloudFolderSyncSwiftBridgeKt.isCloudFolderKeepOffline(mode: mode) {
            guard let dir = offlineDirURL(rootId: rootId) else { return }
            if binding.lastAcknowledgedRevision == remote.revision,
               store.getManifest(rootId: rootId)?.revision == remote.revision { return }
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            store.savePendingMaterialization(remote)
            _ = try materialize(manifest: remote, base: store.getManifest(rootId: rootId), dir: dir, mirrorSource: nil)
            store.saveManifest(remote)
            store.clearPendingMaterialization(rootId: rootId)
            saveBindingAck(store: store, binding: binding, revision: remote.revision)
            feedLibraryScan(rootId: rootId, rootName: remote.root.name, dir: dir)
            return
        }
        // LOCAL_MIRROR
        guard let folderName = folderNameForBinding(binding),
              let dir = managedFolderURL(folderName: folderName) else { return }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        store.savePendingMaterialization(remote)
        _ = try materialize(
            manifest: remote,
            base: store.getManifest(rootId: rootId),
            dir: dir,
            mirrorSource: sourceBookmarkURL(folderName: folderName)
        )
        store.saveManifest(remote)
        store.clearPendingMaterialization(rootId: rootId)
        saveBindingAck(store: store, binding: binding, revision: remote.revision)
        feedLibraryScan(rootId: rootId, rootName: folderName, dir: dir)
    }

    private func pullSafe(store: CloudFolderStore, binding: CloudFolderDeviceBinding, remote: CloudFolderManifest) -> Bool {
        guard let dir = localDirForBinding(binding, rootId: binding.rootId),
              let base = store.getManifest(rootId: binding.rootId) else { return true }
        guard let scan = try? scanDirectory(
            dir: dir, rootId: binding.rootId, base: base,
            deviceId: binding.deviceId, nowMillis: Int64(Date().timeIntervalSince1970 * 1000)
        ), scan.complete else { return false }
        let local = CloudFolderLocalManifestKt.buildCloudFolderLocalManifest(
            base: base, scannedNodes: scan.nodes,
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000), deviceId: binding.deviceId
        )
        let plan = CloudFolderSyncKt.planCloudFolderSync(
            base: base, local: local, remote: remote,
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000), deviceId: binding.deviceId
        )
        if !plan.conflicts.isEmpty { return false }
        return !plan.operations.contains {
            CloudFolderSyncSwiftBridgeKt.cloudFolderOperationDirectionName(operation: $0) == "LOCAL_TO_CLOUD"
        }
    }

    // MARK: - sync

    func syncRoot(uid: String, store: CloudFolderStore, rootId: String, direction: Direction) async throws {
        guard includes(rootId), let binding = store.getBinding(rootId: rootId) else { return }
        let deviceId = binding.deviceId
        let now = { Int64(Date().timeIntervalSince1970 * 1000) }
        saveProgress(store: store, rootId: rootId, phase: "SCANNING", totalFiles: 0, totalBytes: 0)
        if CloudFolderSyncSwiftBridgeKt.isCloudFolderCloudOnly(mode: binding.materializationMode) {
            if let remoteState = try? await readRemoteManifest(uid: uid, store: store, rootId: rootId) {
                store.saveManifest(remoteState.manifest)
            }
            saveProgress(store: store, rootId: rootId, phase: "SUCCEEDED", totalFiles: 0, totalBytes: 0)
            return
        }
        guard let dir = localDirForBinding(binding, rootId: rootId) else { return }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let base = store.getManifest(rootId: rootId) ?? emptyManifest(rootId: rootId, name: bindingName(store: store, binding: binding), deviceId: deviceId)
        let scan = try scanDirectory(dir: dir, rootId: rootId, base: base, deviceId: deviceId, nowMillis: now())
        guard scan.complete else {
            store.markBindingError(rootId: rootId, message: scan.error ?? "Local folder scan unavailable")
            throw AbortSync(transient: true)
        }
        let local = CloudFolderLocalManifestKt.buildCloudFolderLocalManifest(
            base: base, scannedNodes: scan.nodes, nowMillis: now(), deviceId: deviceId
        )
        let remoteState = try? await readRemoteManifest(uid: uid, store: store, rootId: rootId)
        let remote = remoteState?.manifest ?? base
        let remoteMissing = remoteState == nil
        var plan = CloudFolderSyncKt.planCloudFolderSync(
            base: base, local: local, remote: remote, nowMillis: now(), deviceId: deviceId
        )
        // Conflicts: stored non-DEFER wins, else deterministic default; a
        // still-conflicted plan surfaces for manual decision and returns.
        let stored = store.getConflictRecords(rootId: rootId)
        var records = CloudFolderSyncSwiftBridgeKt.reconcileCloudFolderConflicts(plan: plan, stored: stored, nowMillis: now())
        if !plan.conflicts.isEmpty {
            let resolutions = CloudFolderSyncSwiftBridgeKt.cloudFolderResolutionsForPlan(plan: plan, stored: records)
            plan = CloudFolderSyncKt.resolveCloudFolderSync(
                base: base, local: local, remote: remote, plan: plan,
                resolutions: resolutions, nowMillis: now(), deviceId: deviceId
            )
        }
        if !plan.conflicts.isEmpty {
            records = CloudFolderSyncSwiftBridgeKt.reconcileCloudFolderConflicts(plan: plan, stored: records, nowMillis: now())
            store.saveConflictRecords(rootId: rootId, records: records)
            store.markBindingError(rootId: rootId, message: "Folder has sync conflicts")
            publishCurrentState()
            return
        }
        store.clearConflicts(rootId: rootId)
        let localOps = plan.operations.filter {
            CloudFolderSyncSwiftBridgeKt.cloudFolderOperationDirectionName(operation: $0) == "LOCAL_TO_CLOUD"
        }
        let cloudOps = plan.operations.filter {
            CloudFolderSyncSwiftBridgeKt.cloudFolderOperationDirectionName(operation: $0) == "CLOUD_TO_LOCAL"
        }
        if direction == .push && !cloudOps.isEmpty {
            // A PUSH observing remote changes hands off to PULL, mirroring Android.
            store.markBindingError(rootId: rootId, message: "Cloud changes need download first")
            submit(Request(direction: .pull, rootId: rootId, replace: true))
            return
        }
        saveProgress(store: store, rootId: rootId, phase: "UPLOADING",
                     totalFiles: localOps.count, totalBytes: localOps.reduce(0) { $0 + $1.sizeBytes })
        let nodeURLs = Dictionary(uniqueKeysWithValues: scanNodesURLs(dir: dir, nodes: scan.nodes))
        for op in localOps {
            store.enqueueOperation(rootId: rootId, accountId: uid, operation: op, sourcePath: nodeURLs[op.nodeId]?.path)
        }
        var uploaded: [String: String] = [:]
        try await drainOutbox(uid: uid, store: store, rootId: rootId, dir: dir, uploaded: &uploaded)
        var published = local
        if !uploaded.isEmpty {
            published = patchObjectIds(manifest: local, uploaded: uploaded)
        }
        let shouldMaterialize = direction == .sync && !cloudOps.isEmpty
        let shouldPublish = !uploaded.isEmpty || remoteMissing || !localOps.isEmpty ||
            !CloudFolderSyncSwiftBridgeKt.cloudFolderRootsEquivalentForPublish(first: published.root, second: remote.root)
        var target = shouldPublish ? published : remote
        if shouldPublish {
            target = try await publishManifest(uid: uid, store: store, rootId: rootId, published: published, initialRemote: remote,
                                                 initialHeadRevision: remoteState?.headRevision, initialHeadHash: remoteState?.headHash,
                                                 initialDriveFileId: remoteState?.driveFileId)
        } else if !shouldMaterialize {
            store.saveManifest(local)
        }
        if shouldMaterialize {
            store.savePendingMaterialization(target)
            _ = try materialize(manifest: target, base: store.getManifest(rootId: rootId), dir: dir,
                                mirrorSource: mirrorSourceForBinding(binding))
            store.saveManifest(target)
            store.clearPendingMaterialization(rootId: rootId)
        }
        saveBindingAck(store: store, binding: binding, revision: target.revision)
        saveProgress(store: store, rootId: rootId, phase: "SUCCEEDED",
                     totalFiles: localOps.count, totalBytes: localOps.reduce(0) { $0 + $1.sizeBytes })
        feedLibraryScanForBinding(binding: binding, rootId: rootId, dir: dir)
        publishCurrentState()
    }

    // MARK: - outbox drain

    private func drainOutbox(uid: String, store: CloudFolderStore, rootId: String, dir: URL, uploaded: inout [String: String]) async throws {
        var completed = 0
        while true {
            let rows = store.claimDueOutbox(rootId: rootId, limit: 500)
            if rows.isEmpty { return }
            for row in rows {
                do {
                    if row.kind == "UPLOAD_FILE" {
                        let fileId = try await uploadRow(store: store, row: row, dir: dir)
                        uploaded[row.nodeId] = fileId
                    }
                    store.completeOutbox(operationId: row.operationId)
                    completed += 1
                    if let progress = store.getProgress(rootId: rootId) {
                        saveProgress(store: store, rootId: rootId, phase: "UPLOADING",
                                     totalFiles: Int(progress.totalFiles), totalBytes: progress.totalBytes,
                                     completedFiles: completed)
                    }
                } catch is UploadSkipped {
                    continue
                } catch {
                    let maxAttempts = 8
                    if row.attempts >= maxAttempts {
                        store.quarantineOutbox(operationId: row.operationId, error: "\(error)")
                    } else {
                        let delayMs = SharedBackgroundSyncPolicyKt.sharedCloudFolderOutboxRetryDelayMs(attempts: Int32(row.attempts))
                        store.failOutbox(operationId: row.operationId, error: "\(error)",
                                         retryAt: Date(timeIntervalSinceNow: Double(delayMs) / 1000))
                    }
                    throw AbortSync(transient: true)
                }
            }
        }
    }

    private func uploadRow(store: CloudFolderStore, row: CloudFolderStore.OutboxRow, dir: URL) async throws -> String {
        let source: URL
        if let explicit = row.sourcePath, !explicit.isEmpty {
            source = URL(fileURLWithPath: explicit)
        } else {
            source = dir.appendingPathComponent(row.relativePath)
        }
        guard FileManager.default.fileExists(atPath: source.path) else {
            // Source gone (stale node): drop without retry, like Android.
            store.completeOutbox(operationId: row.operationId)
            throw UploadSkipped()
        }
        let (hash, size) = try sha256HexOfFile(source)
        guard CloudFolderDriveProtocol.canonicalHash(hash) == CloudFolderDriveProtocol.canonicalHash(row.contentHash),
              size == row.sizeBytes else {
            throw AbortSync(transient: true)
        }
        let expectedName = CloudFolderDriveProtocol.contentName(
            rootID: row.rootId, nodeID: row.nodeId,
            contentHash: hash, revision: row.revision
        )
        if let existing = (try? await transport.findContentObjects(name: expectedName))?.first(where: {
            CloudFolderDriveProtocol.metadataMatches($0.properties, rootID: row.rootId, nodeID: row.nodeId,
                                                     revision: row.revision, contentHash: hash, sizeBytes: size)
        }) {
            return existing.fileId
        }
        return try await transport.uploadContentFromFile(
            name: expectedName, fileURL: source,
            mimeType: mimeType(for: source.lastPathComponent),
            metadata: CloudFolderDriveProtocol.contentMetadata(
                rootID: row.rootId, nodeID: row.nodeId, revision: row.revision,
                contentHash: hash, sizeBytes: size
            )
        )
    }

    private struct UploadSkipped: Error {}

    private func patchObjectIds(manifest: CloudFolderManifest, uploaded: [String: String]) -> CloudFolderManifest {
        guard !uploaded.isEmpty else { return manifest }
        let nodes = manifest.activeNodes().map { node -> CloudFolderNode in
            guard node.isFile, let id = uploaded[node.nodeId] else { return node }
            return CloudFolderSyncSwiftBridgeKt.makeCloudFolderFileNode(
                nodeId: node.nodeId, rootId: node.rootId, relativePath: node.relativePath,
                contentHash: node.contentHash, sizeBytes: node.sizeBytes, mimeType: node.mimeType,
                fileModifiedAt: node.fileModifiedAt, revision: node.revision,
                modifiedAt: node.modifiedAt, modifiedByDeviceId: node.modifiedByDeviceId,
                contentObjectId: id
            )
        }
        return CloudFolderSyncSwiftBridgeKt.makeCloudFolderManifest(
            root: manifest.root, revision: manifest.revision, baseRevision: manifest.baseRevision,
            generatedAt: manifest.generatedAt, generatedByDeviceId: manifest.generatedByDeviceId,
            nodes: nodes, tombstones: manifest.tombstones
        )
    }

    // MARK: - publish (CAS)

    private func publishManifest(
        uid: String, store: CloudFolderStore, rootId: String,
        published: CloudFolderManifest, initialRemote: CloudFolderManifest?,
        initialHeadRevision: Int64? = nil, initialHeadHash: String? = nil,
        initialDriveFileId: String? = nil
    ) async throws -> CloudFolderManifest {
        guard CloudFolderSyncSwiftBridgeKt.isValidCloudFolderManifest(manifest: published) else {
            throw AbortSync(transient: false)
        }
        // A long outbox run can be overtaken: abort so the next pass picks up
        // the newer remote instead of publishing over it (mirrors Android's
        // `assertRemoteSnapshotUnchanged`).
        if initialRemote != nil {
            let current = try? await heads.getHead(uid: uid, rootId: rootId)
            guard current?.revision == initialHeadRevision,
                  current?.manifestHash == initialHeadHash,
                  current?.driveFileId == initialDriveFileId else {
                throw AbortSync(transient: true)
            }
        }
        let lease: CloudFolderLease
        do {
            lease = try await heads.reserve(
                uid: uid, rootId: rootId,
                expectedRevision: initialRemote?.revision,
                revision: published.revision,
                deviceId: store.getBinding(rootId: rootId)?.deviceId ?? ""
            )
        } catch {
            throw AbortSync(transient: true)
        }
        let json = CloudFolderSyncCodecKt.encodeCloudFolderManifest(manifest: published)
        guard let data = json.data(using: .utf8) else { throw AbortSync(transient: false) }
        let hash = "sha256:\(CloudFolderDriveProtocol.sha256Hex(data))"
        let name = CloudFolderDriveProtocol.manifestName(rootID: rootId, revision: published.revision, manifestHash: hash)
        do {
            let fileId = try await transport.uploadManifest(
                name: name, data: data,
                metadata: CloudFolderDriveProtocol.manifestMetadata(
                    rootID: rootId, revision: published.revision, manifestHash: hash, sizeBytes: data.count
                )
            )
            try await heads.commit(uid: uid, lease: lease, driveFileId: fileId, hash: hash)
        } catch {
            await heads.release(uid: uid, lease: lease)
            throw AbortSync(transient: true)
        }
        store.saveManifest(published)
        return published
    }

    // MARK: - materialize

    /// Write the target manifest into `dir`: create dirs, download
    /// missing/changed files (hash-verified, atomic temp + `.bak` rename),
    /// apply tombstones. Returns whether any content changed.
    func materialize(manifest: CloudFolderManifest, base: CloudFolderManifest?, dir: URL, mirrorSource: URL?) throws -> Bool {
        let fileManager = FileManager.default
        var changed = false
        let sorted = manifest.activeNodes().sorted {
            $0.relativePath.count < $1.relativePath.count ||
            ($0.relativePath.count == $1.relativePath.count && $0.relativePath < $1.relativePath)
        }
        for node in sorted {
            let target = dir.appendingPathComponent(node.relativePath)
            if node.isDirectory {
                try fileManager.createDirectory(at: target, withIntermediateDirectories: true)
                if let mirror = mirrorSource {
                    try fileManager.createDirectory(at: mirror.appendingPathComponent(node.relativePath), withIntermediateDirectories: true)
                }
                continue
            }
            guard let objectId = node.contentObjectId, !objectId.isEmpty,
                  let expectedHash = node.contentHash else { continue }
            if fileMatches(url: target, hash: expectedHash, size: node.sizeBytes) { continue }
            let temp = dir.deletingLastPathComponent()
                .appendingPathComponent(".cloud-folder-\(CloudFolderDriveProtocol.segment(node.nodeId).prefix(16)).part")
            try? fileManager.removeItem(at: temp)
            let semaphore = DispatchSemaphore(value: 0)
            var downloadError: Error?
            Task {
                do {
                    try await transport.downloadToFile(fileId: objectId, destination: temp)
                } catch {
                    downloadError = error
                }
                semaphore.signal()
            }
            semaphore.wait()
            if let downloadError { throw downloadError }
            guard fileMatches(url: temp, hash: expectedHash, size: node.sizeBytes) else {
                try? fileManager.removeItem(at: temp)
                throw AbortSync(transient: true)
            }
            try fileManager.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            let backup = target.appendingPathExtension("bak")
            try? fileManager.removeItem(at: backup)
            if fileManager.fileExists(atPath: target.path) {
                try fileManager.moveItem(at: target, to: backup)
            }
            do {
                try fileManager.moveItem(at: temp, to: target)
                try? fileManager.removeItem(at: backup)
            } catch {
                if fileManager.fileExists(atPath: backup.path) {
                    try? fileManager.moveItem(at: backup, to: target)
                }
                throw AbortSync(transient: true)
            }
            if let mirror = mirrorSource {
                let mirrorTarget = mirror.appendingPathComponent(node.relativePath)
                try? fileManager.createDirectory(at: mirrorTarget.deletingLastPathComponent(), withIntermediateDirectories: true)
                try? fileManager.removeItem(at: mirrorTarget)
                try? fileManager.copyItem(at: target, to: mirrorTarget)
            }
            changed = true
        }
        for tombstone in manifest.tombstones {
            let target = dir.appendingPathComponent(tombstone.relativePath)
            guard fileManager.fileExists(atPath: target.path) else { continue }
            if CloudFolderSyncSwiftBridgeKt.cloudFolderTombstoneIsDirectory(tombstone: tombstone) {
                if (try? fileManager.contentsOfDirectory(atPath: target.path))?.isEmpty == true {
                    try? fileManager.removeItem(at: target)
                    changed = true
                }
                continue
            }
            if let knownHash = tombstone.lastKnownContentHash, fileMatches(url: target, hash: knownHash, size: tombstone.lastKnownSizeBytes) {
                try? fileManager.removeItem(at: target)
                if let mirror = mirrorSource {
                    try? fileManager.removeItem(at: mirror.appendingPathComponent(tombstone.relativePath))
                }
                changed = true
            } else {
                throw AbortSync(transient: true)
            }
        }
        return changed
    }

    private func fileMatches(url: URL, hash: String, size: Int64) -> Bool {
        guard let values = try? url.resourceValues(forKeys: [.fileSizeKey]),
              Int64(values.fileSize ?? -1) == size else { return false }
        guard let hashed = try? sha256HexOfFile(url) else { return false }
        return CloudFolderDriveProtocol.canonicalHash(hashed.hash) == CloudFolderDriveProtocol.canonicalHash(hash)
    }

    // MARK: - delete + GC

    func deleteRootEverywhere(uid: String, rootId: String) async {
        guard let store = store(for: uid) else { return }
        if let remoteState = try? await readRemoteManifest(uid: uid, store: store, rootId: rootId) {
            let remote = remoteState.manifest
            let tombstoned = CloudFolderSyncSwiftBridgeKt.makeCloudFolderManifest(
                root: CloudFolderSyncSwiftBridgeKt.makeCloudFolderRoot(
                    rootId: remote.root.rootId, name: remote.root.name,
                    createdAt: remote.root.createdAt, createdByDeviceId: remote.root.createdByDeviceId,
                    updatedAt: Int64(Date().timeIntervalSince1970 * 1000),
                    manifestRevision: remote.revision + 1, isDeleted: true
                ),
                revision: remote.revision + 1, baseRevision: remote.revision,
                generatedAt: Int64(Date().timeIntervalSince1970 * 1000),
                generatedByDeviceId: store.getBinding(rootId: rootId)?.deviceId ?? "",
                nodes: [], tombstones: []
            )
            _ = try? await publishManifest(uid: uid, store: store, rootId: rootId, published: tombstoned, initialRemote: remote,
                                                 initialHeadRevision: remoteState.headRevision, initialHeadHash: remoteState.headHash,
                                                 initialDriveFileId: remoteState.driveFileId)
        } else {
            await heads.deleteHead(uid: uid, rootId: rootId)
        }
        if let dir = offlineDirURL(rootId: rootId) {
            try? FileManager.default.removeItem(at: dir)
        }
        store.clearRootState(rootId: rootId)
        publishCurrentState()
    }

    func removeBindingOnly(uid: String, rootId: String) {
        guard let store = store(for: uid) else { return }
        if let binding = store.getBinding(rootId: rootId),
           CloudFolderSyncSwiftBridgeKt.isCloudFolderKeepOffline(mode: binding.materializationMode),
           let dir = offlineDirURL(rootId: rootId) {
            try? FileManager.default.removeItem(at: dir)
        }
        store.removeBinding(rootId: rootId)
        store.clearTransferState(rootId: rootId)
        publishCurrentState()
    }

    func runGarbageCollection(uid: String, store: CloudFolderStore) async {
        guard let objects = try? await transport.listFiles(query: "trashed=false") else { return }
        let folderObjects = objects.filter {
            $0.properties[CloudFolderDriveProtocol.keySchema] == "1" &&
            !($0.properties[CloudFolderDriveProtocol.keyRootID] ?? "").isEmpty &&
            !($0.properties[CloudFolderDriveProtocol.keyNodeID] ?? "").isEmpty
        }
        var referenced = Set<String>()
        for root in store.getRoots() {
            guard let manifest = store.getManifest(rootId: root.rootId) else { continue }
            for node in manifest.activeFiles() {
                if let id = node.contentObjectId, !id.isEmpty { referenced.insert(id) }
            }
        }
        if let refs = try? await transport.listManifestRefs() {
            referenced.formUnion(refs.values.map { $0.fileId })
        }
        let candidates = CloudFolderSyncGcPlanKt.planSharedCloudFolderGarbageCollection(
            objects: folderObjects.map {
                CloudFolderStoredObjectRef(
                    driveFileId: $0.fileId, name: $0.name,
                    rootId: $0.properties[CloudFolderDriveProtocol.keyRootID] ?? "",
                    nodeId: $0.properties[CloudFolderDriveProtocol.keyNodeID] ?? "",
                    revision: Int64($0.properties[CloudFolderDriveProtocol.keyRevision] ?? "") ?? 0,
                    modifiedTimeMillis: $0.modifiedTimeMillis,
                    properties: $0.properties
                )
            },
            referencedDriveFileIds: referenced,
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000),
            retentionMillis: CloudFolderSyncGcPlanKt.SHARED_CLOUD_FOLDER_GC_RETENTION_MILLIS
        )
        for candidate in candidates {
            let ref = candidate.objectRef
            let expected = CloudFolderDriveObject(
                fileId: ref.driveFileId, name: ref.name,
                modifiedTimeMillis: ref.modifiedTimeMillis,
                sizeBytes: nil, properties: ref.properties
            )
            try? await transport.deleteObjectVerified(expected: expected)
        }
    }

    // MARK: - registration + publish helpers

    /// Mirror of `registerLocalFolder`: ensure a logical root + binding for a
    /// local folder. Root IDs come from Kotlin (`cloudFolderRootId`), which
    /// assigns them at folder creation like Android.
    func ensureLocalFolderBinding(folderName: String, rootId: String, deviceId: String) {
        guard let uid = currentUid(), let store = store(for: uid) else { return }
        if store.getManifest(rootId: rootId) == nil {
            store.saveManifest(emptyManifest(rootId: rootId, name: folderName, deviceId: deviceId))
        }
        if store.getBinding(rootId: rootId) == nil {
            store.saveBinding(CloudFolderSyncSwiftBridgeKt.makeCloudFolderBinding(
                rootId: rootId, deviceId: deviceId, localUri: "ios-local-folder://\(folderName)",
                permissionRaw: "UNKNOWN", materializationRaw: "LOCAL_MIRROR",
                lastAcknowledgedRevision: 0, lastScanAt: 0, lastError: nil
            ))
        }
    }

    func resolveConflict(uid: String, rootId: String, conflictId: String, resolutionRaw: String) {
        guard let store = store(for: uid) else { return }
        var records = store.getConflictRecords(rootId: rootId)
        guard records.contains(where: { $0.conflictId == conflictId }) else { return }
        records = records.map { record in
            guard record.conflictId == conflictId else { return record }
            return CloudFolderSyncSwiftBridgeKt.withCloudFolderConflictResolution(
                record: record,
                resolutionRaw: resolutionRaw,
                nowMillis: Int64(Date().timeIntervalSince1970 * 1000)
            )
        }
        store.saveConflictRecords(rootId: rootId, records: records)
        submit(Request(direction: .sync, rootId: rootId, replace: true))
    }

    // MARK: - small helpers (file-private)

    private func emptyManifest(rootId: String, name: String, deviceId: String) -> CloudFolderManifest {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return CloudFolderSyncSwiftBridgeKt.makeCloudFolderManifest(
            root: CloudFolderSyncSwiftBridgeKt.makeCloudFolderRoot(
                rootId: rootId, name: name, createdAt: now, createdByDeviceId: deviceId,
                updatedAt: now, manifestRevision: 0, isDeleted: false
            ),
            revision: 0, baseRevision: 0, generatedAt: now, generatedByDeviceId: deviceId,
            nodes: [], tombstones: []
        )
    }

    private func localDirForBinding(_ binding: CloudFolderDeviceBinding, rootId: String) -> URL? {
        let mode = binding.materializationMode
        if CloudFolderSyncSwiftBridgeKt.isCloudFolderKeepOffline(mode: mode) {
            return offlineDirURL(rootId: rootId)
        }
        if CloudFolderSyncSwiftBridgeKt.isCloudFolderLocalMirror(mode: mode),
           let name = folderNameForBinding(binding) {
            return managedFolderURL(folderName: name)
        }
        return nil
    }

    private func mirrorSourceForBinding(_ binding: CloudFolderDeviceBinding) -> URL? {
        guard CloudFolderSyncSwiftBridgeKt.isCloudFolderLocalMirror(mode: binding.materializationMode),
              let name = folderNameForBinding(binding) else { return nil }
        return sourceBookmarkURL(folderName: name)
    }

    private func folderNameForBinding(_ binding: CloudFolderDeviceBinding) -> String? {
        // localUri spelling owned by ensureLocalFolderBinding above.
        binding.localUri?.replacingOccurrences(of: "ios-local-folder://", with: "").nilIfEmpty
    }

    private func bindingName(store: CloudFolderStore, binding: CloudFolderDeviceBinding) -> String {
        folderNameForBinding(binding) ?? store.getManifest(rootId: binding.rootId)?.root.name ?? "Cloud folder"
    }

    private func scanNodesURLs(dir: URL, nodes: [CloudFolderNode]) -> [(String, URL)] {
        nodes.filter { $0.isFile }.map { ($0.nodeId, dir.appendingPathComponent($0.relativePath)) }
    }

    private func saveBindingAck(store: CloudFolderStore, binding: CloudFolderDeviceBinding, revision: Int64) {
        store.saveBinding(CloudFolderSyncSwiftBridgeKt.makeCloudFolderBinding(
            rootId: binding.rootId, deviceId: binding.deviceId, localUri: binding.localUri,
            permissionRaw: "GRANTED", materializationRaw: CloudFolderSyncSwiftBridgeKt.cloudFolderMaterializationName(mode: binding.materializationMode),
            lastAcknowledgedRevision: revision,
            lastScanAt: Int64(Date().timeIntervalSince1970 * 1000), lastError: nil
        ))
    }

    private func saveProgress(store: CloudFolderStore, rootId: String, phase: String,
                              totalFiles: Int, totalBytes: Int64, completedFiles: Int = 0) {
        store.saveProgress(CloudFolderSyncSwiftBridgeKt.makeCloudFolderSyncProgress(
            rootId: rootId, phaseRaw: phase,
            completedFiles: Int32(completedFiles), totalFiles: Int32(totalFiles),
            completedBytes: 0, totalBytes: totalBytes,
            updatedAtMillis: Int64(Date().timeIntervalSince1970 * 1000), errorStatus: nil
        ))
    }

    func markRootFailure(store: CloudFolderStore, rootId: String, message: String) {
        if let progress = store.getProgress(rootId: rootId) {
            store.saveProgress(CloudFolderSyncSwiftBridgeKt.makeCloudFolderSyncProgress(
                rootId: rootId, phaseRaw: "FAILED",
                completedFiles: progress.completedFiles, totalFiles: progress.totalFiles,
                completedBytes: progress.completedBytes, totalBytes: progress.totalBytes,
                updatedAtMillis: Int64(Date().timeIntervalSince1970 * 1000), errorStatus: "unknown"
            ))
        }
        store.markBindingError(rootId: rootId, message: message)
        publishCurrentState()
    }

    private func feedLibraryScan(rootId: String, rootName: String, dir: URL) {
        feedLibraryScanWithBridge(rootName: rootName, dir: dir)
    }

    private func feedLibraryScanForBinding(binding: CloudFolderDeviceBinding, rootId: String, dir: URL) {
        feedLibraryScanWithBridge(rootName: folderNameForBinding(binding) ?? rootId, dir: dir)
    }

    private func feedLibraryScanWithBridge(rootName: String, dir: URL) {
        guard let bridge = bridgeProvider?() else { return }
        guard let enumerator = FileManager.default.enumerator(
            at: dir, includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else { return }
        var names: [String] = []
        var paths: [String] = []
        var ids: [String] = []
        var relatives: [String] = []
        var sizes: [String] = []
        var mtimes: [String] = []
        for case let item as URL in enumerator {
            guard let values = try? item.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey]),
                  values.isRegularFile == true else { continue }
            let relative = item.path.replacingOccurrences(of: dir.path + "/", with: "", options: [.anchored])
            names.append(item.lastPathComponent)
            paths.append(item.path)
            ids.append((try? sha256HexOfFile(item).hash) ?? "")
            relatives.append(relative)
            sizes.append(String(values.fileSize ?? 0))
            mtimes.append(String(Int64((values.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000)))
        }
        bridge.recordImportedFolder(
            folderName: rootName, fileNames: names, filePaths: paths, contentIds: ids,
            relativePaths: relatives, fileSizes: sizes,
            lastModifiedTimestamps: mtimes, scanSucceeded: true
        )
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
