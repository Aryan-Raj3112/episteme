import Foundation
import ReaderShared

/// File-backed cloud-folder sync store (iOS equivalent of Android's
/// `CloudFolderSyncRepository` + Room tables).
///
/// Android is the absolute benchmark and is NOT changed. Table semantics mirror
/// `CloudFolderSyncPersistence` / `CloudFolderPrivateDatabase`: manifests
/// (roots+nodes+tombstones), bindings, content outbox (PENDING/RUNNING/
/// QUARANTINED + attempts + nextAttemptAt), conflicts, pending
/// materializations, progress, and local inventory. Manifests, bindings, and
/// conflict records always go through the shared codecs so bytes stay
/// identical across platforms; outbox/progress/inventory rows are
/// device-local only and use Swift Codables.
///
/// Crash safety mirrors `IosLibrarySnapshotFileStore`: every write is atomic
/// (temp + `replaceItemAt`), and `resetRunningOutbox` runs on every executor
/// start so a kill mid-pass never strands RUNNING rows.
final class CloudFolderStore {
    struct OutboxRow: Codable {
        var operationId: String
        var rootId: String
        var nodeId: String
        var kind: String
        var direction: String
        var relativePath: String
        var previousRelativePath: String?
        var contentHash: String?
        var sizeBytes: Int64
        var revision: Int64
        var sourceNodeId: String?
        var sourcePath: String?
        var state: String
        var attempts: Int
        var nextAttemptAt: Double
        var lastError: String?
    }

    struct ProgressRow: Codable {
        var rootId: String
        var phase: String
        var completedFiles: Int
        var totalFiles: Int
        var completedBytes: Int64
        var totalBytes: Int64
        var updatedAt: Double
        var errorStatus: String?
    }

    struct InventoryRow: Codable {
        var rootId: String
        var state: String
        var fileCount: Int
        var totalBytes: Int64
        var sizeComplete: Bool
        var scannedAt: Double
        var updatedAt: Double
        var errorStatus: String?
    }

    private let directory: URL
    private let fileManager = FileManager.default
    private let queue = DispatchQueue(label: "com.aryan.reader.cloudfolder.store")

    init?(accountId: String) {
        let digest = CloudFolderDriveProtocol.segment(accountId)
        guard let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        directory = appSupport
            .appendingPathComponent("CloudFolderSync", isDirectory: true)
            .appendingPathComponent(digest, isDirectory: true)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try? fileManager.createDirectory(at: directory.appendingPathComponent("manifests", isDirectory: true), withIntermediateDirectories: true)
    }

    // MARK: - low level

    private func readDictionary(_ name: String) -> [String: String] {
        queue.sync {
            let url = directory.appendingPathComponent(name)
            guard let data = try? Data(contentsOf: url),
                  let dict = try? JSONDecoder().decode([String: String].self, from: data) else { return [:] }
            return dict
        }
    }

    private func writeDictionary(_ name: String, _ dict: [String: String]) {
        queue.sync {
            let url = directory.appendingPathComponent(name)
            guard let data = try? JSONEncoder().encode(dict) else { return }
            writeAtomically(data, to: url)
        }
    }

    private func writeAtomically(_ data: Data, to url: URL) {
        let temp = url.deletingLastPathComponent()
            .appendingPathComponent(".reader-\(UUID().uuidString).tmp")
        do {
            try data.write(to: temp, options: .atomic)
            if fileManager.fileExists(atPath: url.path) {
                _ = try fileManager.replaceItemAt(url, withItemAt: temp)
            } else {
                try fileManager.moveItem(at: temp, to: url)
            }
        } catch {
            try? fileManager.removeItem(at: temp)
        }
    }

    private func manifestURL(_ rootId: String) -> URL {
        directory.appendingPathComponent("manifests", isDirectory: true)
            .appendingPathComponent("\(CloudFolderDriveProtocol.segment(rootId)).json")
    }

    // MARK: - manifests (roots + nodes + tombstones)

    func getManifest(rootId: String) -> CloudFolderManifest? {
        let url = manifestURL(rootId)
        guard let data = try? Data(contentsOf: url),
              let json = String(data: data, encoding: .utf8) else { return nil }
        return CloudFolderSyncCodecKt.decodeCloudFolderManifestOrNull(rawJson: json)
    }

    func saveManifest(_ manifest: CloudFolderManifest) {
        let json = CloudFolderSyncCodecKt.encodeCloudFolderManifest(manifest: manifest)
        guard let data = json.data(using: .utf8) else { return }
        let url = manifestURL(manifest.rootId)
        queue.sync { writeAtomically(data, to: url) }
    }

    func getRoots() -> [CloudFolderRoot] {
        let dir = directory.appendingPathComponent("manifests", isDirectory: true)
        let files = (try? fileManager.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return files.compactMap { url in
            guard let data = try? Data(contentsOf: url),
                  let json = String(data: data, encoding: .utf8),
                  let manifest = CloudFolderSyncCodecKt.decodeCloudFolderManifestOrNull(rawJson: json) else { return nil }
            return manifest.root
        }.sorted { $0.name.lowercased() < $1.name.lowercased() || ($0.name.lowercased() == $1.name.lowercased() && $0.rootId < $1.rootId) }
    }

    func clearRootState(rootId: String) {
        try? fileManager.removeItem(at: manifestURL(rootId))
        removeBinding(rootId: rootId)
        clearTransferState(rootId: rootId)
    }

    // MARK: - bindings

    func getBinding(rootId: String) -> CloudFolderDeviceBinding? {
        guard let json = readDictionary("bindings.json")[rootId] else { return nil }
        return CloudFolderSyncCodecKt.decodeCloudFolderDeviceBindingOrNull(rawJson: json)
    }

    func getBindings() -> [String: CloudFolderDeviceBinding] {
        readDictionary("bindings.json").compactMapValues {
            CloudFolderSyncCodecKt.decodeCloudFolderDeviceBindingOrNull(rawJson: $0)
        }
    }

    func saveBinding(_ binding: CloudFolderDeviceBinding) {
        var dict = readDictionary("bindings.json")
        dict[binding.rootId] = CloudFolderSyncCodecKt.encodeCloudFolderDeviceBinding(binding: binding)
        writeDictionary("bindings.json", dict)
    }

    func removeBinding(rootId: String) {
        var dict = readDictionary("bindings.json")
        dict.removeValue(forKey: rootId)
        writeDictionary("bindings.json", dict)
    }

    func markBindingError(rootId: String, message: String) {
        guard let binding = getBinding(rootId: rootId) else { return }
        saveBinding(CloudFolderSyncSwiftBridgeKt.withCloudFolderBindingError(
            binding: binding,
            message: message
        ))
    }

    // MARK: - pending materializations

    func getPendingMaterialization(rootId: String) -> CloudFolderManifest? {
        guard let json = readDictionary("pending.json")[rootId] else { return nil }
        return CloudFolderSyncCodecKt.decodeCloudFolderManifestOrNull(rawJson: json)
    }

    func savePendingMaterialization(_ manifest: CloudFolderManifest) {
        var dict = readDictionary("pending.json")
        dict[manifest.rootId] = CloudFolderSyncCodecKt.encodeCloudFolderManifest(manifest: manifest)
        writeDictionary("pending.json", dict)
    }

    func clearPendingMaterialization(rootId: String) {
        var dict = readDictionary("pending.json")
        dict.removeValue(forKey: rootId)
        writeDictionary("pending.json", dict)
    }

    // MARK: - content outbox

    private func readOutbox() -> [String: OutboxRow] {
        let url = directory.appendingPathComponent("outbox.json")
        guard let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([String: OutboxRow].self, from: data) else { return [:] }
        return rows
    }

    private func writeOutbox(_ rows: [String: OutboxRow]) {
        let url = directory.appendingPathComponent("outbox.json")
        guard let data = try? JSONEncoder().encode(rows) else { return }
        queue.sync { writeAtomically(data, to: url) }
    }

    func enqueueOperation(
        rootId: String,
        accountId: String,
        operation: CloudFolderSyncOperation,
        sourcePath: String?,
        now: Date = Date()
    ) {
        var rows = readOutbox()
        let id = CloudFolderSyncSwiftBridgeKt.cloudFolderOutboxOperationId(
            operation: operation,
            accountId: accountId,
            rootId: rootId
        )
        rows[id] = OutboxRow(
            operationId: id,
            rootId: rootId,
            nodeId: operation.nodeId,
            kind: CloudFolderSyncSwiftBridgeKt.cloudFolderOperationKindName(operation: operation),
            direction: CloudFolderSyncSwiftBridgeKt.cloudFolderOperationDirectionName(operation: operation),
            relativePath: operation.relativePath,
            previousRelativePath: operation.previousRelativePath,
            contentHash: operation.contentHash,
            sizeBytes: operation.sizeBytes,
            revision: operation.revision,
            sourceNodeId: operation.sourceNodeId,
            sourcePath: sourcePath,
            state: "PENDING",
            attempts: rows[id]?.attempts ?? 0,
            nextAttemptAt: now.timeIntervalSince1970,
            lastError: nil
        )
        writeOutbox(rows)
    }

    /// Claim due rows (PENDING + nextAttemptAt elapsed), marking RUNNING.
    func claimDueOutbox(rootId: String, now: Date = Date(), limit: Int = 500) -> [OutboxRow] {
        var rows = readOutbox()
        let nowSeconds = now.timeIntervalSince1970
        var claimed: [OutboxRow] = []
        for (id, row) in rows.sorted(by: { $0.key < $1.key }) {
            if claimed.count >= limit { break }
            guard row.rootId == rootId, row.state == "PENDING", row.nextAttemptAt <= nowSeconds else { continue }
            var updated = row
            updated.state = "RUNNING"
            updated.attempts += 1
            rows[id] = updated
            claimed.append(updated)
        }
        if !claimed.isEmpty { writeOutbox(rows) }
        return claimed
    }

    func completeOutbox(operationId: String) {
        var rows = readOutbox()
        rows.removeValue(forKey: operationId)
        writeOutbox(rows)
    }

    func failOutbox(operationId: String, error: String, retryAt: Date) {
        var rows = readOutbox()
        guard var row = rows[operationId] else { return }
        row.state = "PENDING"
        row.nextAttemptAt = retryAt.timeIntervalSince1970
        row.lastError = String(error.prefix(500))
        rows[operationId] = row
        writeOutbox(rows)
    }

    func quarantineOutbox(operationId: String, error: String) {
        var rows = readOutbox()
        guard var row = rows[operationId] else { return }
        row.state = "QUARANTINED"
        row.nextAttemptAt = .greatestFiniteMagnitude
        row.lastError = String(error.prefix(500))
        rows[operationId] = row
        writeOutbox(rows)
    }

    /// Every executor run starts by re-queueing interrupted rows, mirroring
    /// Android's `resetRunningOutbox` per run.
    func resetRunningOutbox() {
        var rows = readOutbox()
        var changed = false
        for (id, row) in rows where row.state == "RUNNING" {
            var updated = row
            updated.state = "PENDING"
            rows[id] = updated
            changed = true
        }
        if changed { writeOutbox(rows) }
    }

    // MARK: - conflicts

    func getConflictRecords(rootId: String) -> [CloudFolderConflictRecord] {
        readDictionary("conflicts.json").compactMap { key, json -> CloudFolderConflictRecord? in
            guard key.hasPrefix("\(rootId)/") else { return nil }
            return CloudFolderSyncCodecKt.decodeCloudFolderConflictRecordOrNull(rawJson: json)
        }
    }

    func getAllConflictRecords() -> [CloudFolderConflictRecord] {
        readDictionary("conflicts.json").values.compactMap {
            CloudFolderSyncCodecKt.decodeCloudFolderConflictRecordOrNull(rawJson: $0)
        }
    }

    func saveConflictRecords(rootId: String, records: [CloudFolderConflictRecord]) {
        var dict = readDictionary("conflicts.json")
        dict = dict.filter { !$0.key.hasPrefix("\(rootId)/") }
        for record in records {
            dict["\(rootId)/\(record.conflictId)"] = CloudFolderSyncCodecKt.encodeCloudFolderConflictRecord(record: record)
        }
        writeDictionary("conflicts.json", dict)
    }

    func clearConflicts(rootId: String) {
        var dict = readDictionary("conflicts.json")
        dict = dict.filter { !$0.key.hasPrefix("\(rootId)/") }
        writeDictionary("conflicts.json", dict)
    }

    // MARK: - progress

    func getProgress(rootId: String) -> CloudFolderSyncProgress? {
        guard let data = readDictionary("progress.json")[rootId]?.data(using: .utf8),
              let row = try? JSONDecoder().decode(ProgressRow.self, from: data) else { return nil }
        return CloudFolderSyncSwiftBridgeKt.makeCloudFolderSyncProgress(
            rootId: row.rootId,
            phaseRaw: row.phase,
            completedFiles: Int32(row.completedFiles),
            totalFiles: Int32(row.totalFiles),
            completedBytes: row.completedBytes,
            totalBytes: row.totalBytes,
            updatedAtMillis: Int64(row.updatedAt * 1000),
            errorStatus: row.errorStatus
        )
    }

    func getProgressForAccount() -> [String: CloudFolderSyncProgress] {
        var result: [String: CloudFolderSyncProgress] = [:]
        for rootId in readDictionary("progress.json").keys {
            if let progress = getProgress(rootId: rootId) { result[rootId] = progress }
        }
        return result
    }

    func saveProgress(_ progress: CloudFolderSyncProgress) {
        var dict = readDictionary("progress.json")
        let row = ProgressRow(
            rootId: progress.rootId,
            phase: CloudFolderSyncSwiftBridgeKt.cloudFolderPhaseName(phase: progress.phase),
            completedFiles: Int(progress.completedFiles),
            totalFiles: Int(progress.totalFiles),
            completedBytes: progress.completedBytes,
            totalBytes: progress.totalBytes,
            updatedAt: Double(progress.updatedAt) / 1000,
            errorStatus: progress.errorStatus
        )
        if let data = try? JSONEncoder().encode(row), let json = String(data: data, encoding: .utf8) {
            dict[progress.rootId] = json
            writeDictionary("progress.json", dict)
        }
    }

    func clearProgress(rootId: String) {
        var dict = readDictionary("progress.json")
        dict.removeValue(forKey: rootId)
        writeDictionary("progress.json", dict)
    }

    // MARK: - inventory

    func getInventory(rootId: String) -> InventoryRow? {
        guard let data = readDictionary("inventory.json")[rootId]?.data(using: .utf8),
              let row = try? JSONDecoder().decode(InventoryRow.self, from: data) else { return nil }
        return row
    }

    func saveInventory(_ row: InventoryRow) {
        var dict = readDictionary("inventory.json")
        if let data = try? JSONEncoder().encode(row), let json = String(data: data, encoding: .utf8) {
            dict[row.rootId] = json
            writeDictionary("inventory.json", dict)
        }
    }

    // MARK: - transfer state

    func clearTransferState(rootId: String) {
        var outbox = readOutbox()
        outbox = outbox.filter { $0.value.rootId != rootId }
        writeOutbox(outbox)
        clearConflicts(rootId: rootId)
        clearPendingMaterialization(rootId: rootId)
        clearProgress(rootId: rootId)
        var inventory = readDictionary("inventory.json")
        inventory.removeValue(forKey: rootId)
        writeDictionary("inventory.json", inventory)
    }
}
