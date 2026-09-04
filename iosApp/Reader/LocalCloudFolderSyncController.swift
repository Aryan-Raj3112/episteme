import CryptoKit
import Foundation
import UniformTypeIdentifiers
import ReaderShared

#if canImport(FirebaseAuth)
import FirebaseAuth
#endif

#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif

/// iOS cloud-folder transfer executor (Android `CloudFolderSyncWorker` parity).
///
/// Android is the absolute benchmark and is NOT changed. Stage order mirrors
/// `syncRoot` / `pullRoot` / `discoverIncomingRoots` / `deleteRootFromCloud` /
/// `runGarbageCollection`: gates → SCANNING progress → remote-manifest read
/// (Firestore head pins the exact Drive object; name + hash verified before
/// consume) → local scan → shared `buildCloudFolderLocalManifest` → shared
/// `planCloudFolderSync` → stored-resolution + default-resolution conflict
/// handling (never stalls) → PUSH-guard handoff → outbox enqueue + drain
/// (8 attempts, `(1<<a)s` delays, quarantine) → CAS publish
/// (reserve → upload → commit, release on failure) → materialize (atomic
/// temp + `.bak` rename, hash-verified) → SUCCEEDED + binding ack +
/// library re-scan feed. One deliberate approximation: `metadataOnly`
/// sidecar wakes run as full scans (same fallback Android uses when targets
/// are unavailable); sidecars still sync as regular files.
///
/// All manifest math (plan/resolve/build/codec/GC) is shared Kotlin; Swift
/// owns Drive/Firestore/file I/O plus the serial coalescing queue
/// (REPLACE drops queued duplicates, KEEP drops new ones — mirroring
/// WorkManager unique-work policy).
final class LocalCloudFolderSyncController {
    enum Direction { case push, pull, sync }

    struct Request {
        var direction: Direction
        var rootId: String?
        var replace: Bool
        var key: String { "\(direction):\(rootId ?? "*")" }
    }

    struct AbortSync: Error {
        var transient: Bool
    }

    weak var localAccount: LocalAccountController?
    var bridgeProvider: (() -> ReaderIosBridge?)?
    /// Serial queue entry for an account-wide PULL (discovery + pull included).
    func requestSyncAllPull(replace: Bool) {
        submit(Request(direction: .pull, rootId: nil, replace: replace))
    }

    /// Publish executor state to Compose through the bridge. Called after
    /// every pass and every direct store mutation.
    func publishCurrentState() {
        guard let uid = currentUid(),
              let store = store(for: uid),
              let bridge = bridgeProvider?() else { return }
        let roots = store.getRoots().filter { !$0.isDeleted }
        let rootsJson = jsonString(roots.map(encodeRoot))
        let bindings = store.getBindings()
        let bindingsJson = jsonString(Dictionary(uniqueKeysWithValues: bindings.map { ($0.key, encodeBinding($0.value)) }))
        let progress = store.getProgressForAccount()
        let progressJson = jsonString(Dictionary(uniqueKeysWithValues: progress.map { ($0.key, encodeProgress($0.value)) }))
        let conflicts = store.getAllConflictRecords().map {
            CloudFolderSyncCodecKt.encodeCloudFolderConflictRecord(record: $0)
        }
        bridge.publishCloudFolderSyncState(
            rootsJson: rootsJson, bindingsJson: bindingsJson,
            progressJson: progressJson, conflictRecordJsons: conflicts
        )
    }

    private func jsonString(_ value: Any) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: value),
              let json = String(data: data, encoding: .utf8) else { return "{}" }
        return json
    }

    private func encodeRoot(_ root: CloudFolderRoot) -> [String: Any] {
        [
            "rootId": root.rootId,
            "name": root.name,
            "createdAt": root.createdAt,
            "createdByDeviceId": root.createdByDeviceId,
            "updatedAt": root.updatedAt,
            "manifestRevision": root.manifestRevision,
            "stats": [
                "fileCount": Int(root.stats.fileCount),
                "directoryCount": Int(root.stats.directoryCount),
                "totalBytes": root.stats.totalBytes,
                "scannedAt": root.stats.scannedAt,
                "scanComplete": root.stats.scanComplete,
            ],
            "isDeleted": root.isDeleted,
        ]
    }

    private func encodeBinding(_ binding: CloudFolderDeviceBinding) -> [String: Any] {
        var dict: [String: Any] = [
            "rootId": binding.rootId,
            "deviceId": binding.deviceId,
            "permissionState": CloudFolderSyncSwiftBridgeKt.cloudFolderPermissionName(state: binding.permissionState),
            "materializationMode": CloudFolderSyncSwiftBridgeKt.cloudFolderMaterializationName(mode: binding.materializationMode),
            "lastAcknowledgedRevision": binding.lastAcknowledgedRevision,
            "lastScanAt": binding.lastScanAt,
        ]
        dict["localUri"] = binding.localUri
        dict["lastError"] = binding.lastError
        return dict
    }

    private func encodeProgress(_ progress: CloudFolderSyncProgress) -> [String: Any] {
        var dict: [String: Any] = [
            "rootId": progress.rootId,
            "phase": CloudFolderSyncSwiftBridgeKt.cloudFolderPhaseName(phase: progress.phase),
            "completedFiles": Int(progress.completedFiles),
            "totalFiles": Int(progress.totalFiles),
            "completedBytes": progress.completedBytes,
            "totalBytes": progress.totalBytes,
            "updatedAt": progress.updatedAt,
        ]
        dict["errorStatus"] = progress.errorStatus
        return dict
    }

    let heads = CloudFolderHeadsService()
    lazy var transport = CloudFolderDriveTransport(accessToken: { [weak self] in
        guard let self, let account = self.localAccount else { throw CloudFolderTransportError.transient("no account") }
        return try await account.folderSyncAccessToken()
    })

    let lock = NSLock()
    var queued: [Request] = []
    var queuedKeys = Set<String>()
    var runningKey: String?
    private var chain: Task<Void, Never>?

    var storeCache: [String: CloudFolderStore] = [:]

    // MARK: - submit

    func submit(_ request: Request) {
        lock.lock()
        if runningKey == request.key || queuedKeys.contains(request.key) {
            if request.replace {
                queued.removeAll { $0.key == request.key }
                queuedKeys.remove(request.key)
            } else {
                lock.unlock()
                return
            }
        }
        queued.append(request)
        queuedKeys.insert(request.key)
        let previous = chain
        chain = Task { [weak self] in
            _ = await previous?.value
            await self?.runNext()
        }
        lock.unlock()
    }

    private func runNext() async {
        lock.lock()
        guard !queued.isEmpty else { lock.unlock(); return }
        let request = queued.removeFirst()
        queuedKeys.remove(request.key)
        runningKey = request.key
        lock.unlock()
        defer {
            lock.lock()
            runningKey = nil
            lock.unlock()
        }
        do {
            try await run(request)
        } catch let abort as AbortSync where abort.transient {
            // WorkManager-style backoff re-run for transient failures.
            let retry = request
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                self?.submit(Request(direction: retry.direction, rootId: retry.rootId, replace: false))
            }
        } catch {
            // Deterministic failures are already recorded on the binding.
        }
        publishCurrentState()
    }

    /// Await queue idle (BG task completion).
    func awaitIdle() async {
        chain?.value
    }

    func requestSyncAll(replace: Bool) {
        submit(Request(direction: .sync, rootId: nil, replace: replace))
    }

    func requestSyncRoot(_ rootId: String, direction: Direction, replace: Bool) {
        submit(Request(direction: direction, rootId: rootId, replace: replace))
    }

    // MARK: - folder-head wake (Android CloudFolderHeadListener parity)

#if canImport(FirebaseFirestore)
    private var folderHeadListener: ListenerRegistration?
    private var folderHeadListenerUid: String?
    private let folderHeadLock = NSLock()
    private var folderHeadPending: [String: Int64] = [:]
    private var folderHeadWork: [String: DispatchWorkItem] = [:]
#endif

    /// Foreground-only Firestore wake on `users/{uid}/cloudFolderHeads`,
    /// mirroring Android's CloudFolderHeadListenerCoordinator: attach on
    /// foreground, detach on background, per-root 500ms debounce, pull only
    /// when the remote revision is still ahead afterwards. ContentView owns
    /// the foreground signal via scenePhase. Executor gates (account/Pro/
    /// sync) re-check inside the worker, like Android's in-worker re-gate;
    /// the BG processing task already runs full discover+pull passes, so this
    /// listener only needs to cover the live foreground case.
    func startFolderHeadListener() {
#if canImport(FirebaseFirestore)
        guard let uid = currentUid(), !uid.isEmpty else { stopFolderHeadListener(); return }
        guard bridgeProvider?()?.cloudFolderSyncEligible() == true else { stopFolderHeadListener(); return }
        if folderHeadListener != nil, folderHeadListenerUid == uid { return }
        stopFolderHeadListener()
        folderHeadListenerUid = uid
        folderHeadListener = Firestore.firestore()
            .collection("users").document(uid).collection("cloudFolderHeads")
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self, let snapshot else { return }
                // Snapshot errors resolve on the next event; revision gating
                // below makes retries safe.
                let changes = snapshot.documentChanges.map { change in
                    (id: change.document.documentID, data: change.document.data())
                }
                for change in changes {
                    self.handleFolderHeadChange(uid: uid, rootId: change.id, data: change.data)
                }
            }
#endif
    }

    func stopFolderHeadListener() {
#if canImport(FirebaseFirestore)
        folderHeadLock.lock()
        let work = Array(folderHeadWork.values)
        folderHeadWork.removeAll()
        folderHeadPending.removeAll()
        folderHeadLock.unlock()
        work.forEach { $0.cancel() }
        folderHeadListener?.remove()
        folderHeadListener = nil
        folderHeadListenerUid = nil
#endif
    }

#if canImport(FirebaseFirestore)
    private func handleFolderHeadChange(uid: String, rootId: String, data: [String: Any]) {
        guard !rootId.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let revision = (data["revision"] as? NSNumber)?.int64Value ?? -1
        guard revision >= 0 else { return }
        if let schema = data["schemaVersion"] as? NSNumber, schema.int64Value != 1 { return }
        let state = ((data["state"] as? String) ?? "").trimmingCharacters(in: .whitespaces).uppercased()
        // A COMMITTING lease record is a normal CAS phase, not a pull signal;
        // unknown states are invalid. (Android: transient skip + validity gate.)
        guard state.isEmpty || state == CloudFolderHeadPullPolicyKt.CLOUD_FOLDER_HEAD_COMMITTED_STATE else { return }
        guard bridgeProvider?()?.cloudFolderSyncEligible() == true else { return }
        guard let store = store(for: uid) else { return }
        let known = max(
            store.getRoots().first(where: { $0.rootId == rootId })?.manifestRevision ?? -1,
            store.getBinding(rootId: rootId)?.lastAcknowledgedRevision ?? -1
        )
        guard CloudFolderHeadPullPolicyKt.shouldScheduleCloudFolderHeadPull(
            remoteRevision: revision,
            knownRevision: known,
            hasBinding: store.getBinding(rootId: rootId) != nil,
            isIncluded: includes(rootId)
        ) else { return }
        folderHeadLock.lock()
        folderHeadPending[rootId] = max(folderHeadPending[rootId] ?? -1, revision)
        folderHeadWork[rootId]?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.folderHeadLock.lock()
            let target = self.folderHeadPending.removeValue(forKey: rootId)
            self.folderHeadWork.removeValue(forKey: rootId)
            self.folderHeadLock.unlock()
            guard let target else { return }
            // Re-read durable knowledge after the debounce: this device's own
            // publish echo can arrive before its manifest write lands, so only
            // a revision still ahead needs work (Android self-echo recheck).
            // A state-read failure must not lose the wake: the worker
            // re-checks account/selection/binding itself.
            guard let store = self.store(for: uid) else {
                self.requestSyncRoot(rootId, direction: .pull, replace: false)
                return
            }
            let fresh = max(
                store.getRoots().first(where: { $0.rootId == rootId })?.manifestRevision ?? -1,
                store.getBinding(rootId: rootId)?.lastAcknowledgedRevision ?? -1
            )
            guard target > fresh else { return }
            self.requestSyncRoot(rootId, direction: .pull, replace: false)
        }
        folderHeadWork[rootId] = work
        folderHeadLock.unlock()
        DispatchQueue.global().asyncAfter(
            deadline: .now() + .milliseconds(Int(CloudFolderHeadPullPolicyKt.CLOUD_FOLDER_HEAD_DEBOUNCE_MILLIS)),
            execute: work
        )
    }
#endif

    // MARK: - context

    private func context() async throws -> (uid: String, store: CloudFolderStore, token: String, deviceId: String) {
        #if canImport(FirebaseAuth)
        guard let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else {
            throw AbortSync(transient: false)
        }
        #else
        throw AbortSync(transient: false)
        #endif
        guard let store = store(for: uid) else { throw AbortSync(transient: false) }
        guard let account = localAccount else { throw AbortSync(transient: false) }
        let deviceId = account.folderSyncDeviceID()
        let token: String
        do {
            token = try await account.folderSyncAccessToken()
        } catch {
            throw AbortSync(transient: true)
        }
        _ = token
        return (uid, store, token, deviceId)
    }

    func store(for uid: String) -> CloudFolderStore? {
        if let cached = storeCache[uid] { return cached }
        guard let store = CloudFolderStore(accountId: uid) else { return nil }
        storeCache[uid] = store
        return store
    }

    private func selection() -> (mode: String, ids: Set<String>) {
        guard let uid = currentUid(),
              let defaults = UserDefaults.standard.dictionary(forKey: "reader.ios.cloudFolderSync.v1.selection_v1_\(CloudFolderDriveProtocol.segment(uid))") as? [String: Any] ??
                (UserDefaults.standard.string(forKey: "reader.ios.cloudFolderSync.v1.selection_v1_\(CloudFolderDriveProtocol.segment(uid))") as String?)
                .flatMap({ $0.data(using: .utf8) })
                .flatMap({ try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }) else {
            return ("EXCLUDED", [])
        }
        let mode = (defaults["mode"] as? String) ?? "EXCLUDED"
        let ids = Set((defaults["selectedRootIds"] as? [String]) ?? [])
        return (mode, ids)
    }

    func includes(_ rootId: String) -> Bool {
        let (mode, ids) = selection()
        switch mode {
        case "ALL": return true
        case "SELECTED": return ids.contains(rootId)
        default: return false
        }
    }

    func currentUid() -> String? {
        #if canImport(FirebaseAuth)
        return Auth.auth().currentUser?.uid
        #else
        return nil
        #endif
    }

    // MARK: - run

    private func run(_ request: Request) async throws {
        let (uid, store, _, _) = try await context()
        guard isEligible() else { return }
        store.resetRunningOutbox()
        do {
            if let rootId = request.rootId {
                switch request.direction {
                case .pull: try await pullRoot(uid: uid, store: store, rootId: rootId)
                case .push, .sync: try await syncRoot(uid: uid, store: store, rootId: rootId, direction: request.direction)
                }
            } else if request.direction == .pull {
                try await discoverAndPull(uid: uid, store: store)
            } else {
                try await discoverIncomingRoots(uid: uid, store: store)
                try await syncSelectedRoots(uid: uid, store: store, direction: request.direction)
            }
        } catch let error as CloudFolderTransportError {
            switch error {
            case .transient: throw AbortSync(transient: true)
            case .deterministic, .missing: throw AbortSync(transient: false)
            }
        } catch let abort as AbortSync {
            throw abort
        } catch {
            throw AbortSync(transient: false)
        }
    }

    private func isEligible() -> Bool {
        guard let bridge = bridgeProvider?() else { return false }
        // Swift mirrors Android's account + Pro + sync gates; the snapshot of
        // these flags is read from the bridge at run start (a second line of
        // defence like Android's in-worker re-gate).
        return bridge.cloudFolderSyncEligible()
    }

    // MARK: - discovery

    private func discoverIncomingRoots(uid: String, store: CloudFolderStore) async throws {
        let refs: [String: CloudFolderDriveObject]
        do {
            refs = try await transport.listManifestRefs()
        } catch {
            // Discovery failure must not block pulls of bound roots.
            return
        }
        for (rootId, ref) in refs {
            if store.getBinding(rootId: rootId) != nil { continue }
            guard let remote = try? await readRemoteManifest(uid: uid, store: store, rootId: rootId, ref: ref) else { continue }
            store.saveManifest(remote.manifest)
            bridgeProvider?()?.noteDiscoveredCloudFolderRoot(rootId: rootId, revision: remote.manifest.revision)
        }
    }

    private func discoverAndPull(uid: String, store: CloudFolderStore) async throws {
        try await discoverIncomingRoots(uid: uid, store: store)
        for root in store.getRoots() where !root.isDeleted {
            guard CloudFolderSyncSwiftBridgeKt.shouldPullCloudFolderRoot(
                isDeleted: root.isDeleted,
                isIncluded: includes(root.rootId),
                hasBinding: store.getBinding(rootId: root.rootId) != nil
            ) else { continue }
            try await pullRoot(uid: uid, store: store, rootId: root.rootId)
        }
    }

    private func syncSelectedRoots(uid: String, store: CloudFolderStore, direction: Direction) async throws {
        var firstError: Error?
        for root in store.getRoots() where !root.isDeleted && includes(root.rootId) {
            do {
                try await syncRoot(uid: uid, store: store, rootId: root.rootId, direction: direction)
            } catch {
                if firstError == nil { firstError = error }
                markRootFailure(store: store, rootId: root.rootId, message: "sync failed")
            }
        }
        if let firstError { throw firstError }
    }

    // MARK: - remote manifest

    struct RemoteManifest {
        var manifest: CloudFolderManifest
        var driveFileId: String
        var headRevision: Int64?
        var headHash: String?
    }

    func readRemoteManifest(
        uid: String,
        store: CloudFolderStore,
        rootId: String,
        ref: CloudFolderDriveObject? = nil
    ) async throws -> RemoteManifest? {
        let head: CloudFolderHead?
        do {
            head = try await heads.getHead(uid: uid, rootId: rootId)
        } catch {
            throw AbortSync(transient: false)
        }
        var candidates: [CloudFolderDriveObject]
        if let ref {
            candidates = [ref]
        } else {
            candidates = (try? await transport.findManifestObjects(rootId: rootId)) ?? []
            if let headId = head?.driveFileId {
                candidates.sort {
                    ($0.fileId == headId ? 0 : 1, $0.modifiedTimeMillis, $0.fileId) <
                    ($1.fileId == headId ? 0 : 1, $1.modifiedTimeMillis, $1.fileId)
                }
            }
        }
        if candidates.isEmpty { return nil }
        var validated: RemoteManifest?
        for candidate in candidates {
            guard let manifest = try? await downloadAndVerifyManifest(candidate, rootId: rootId) else { continue }
            validated = RemoteManifest(manifest: manifest, driveFileId: candidate.fileId)
            break
        }
        guard let remote = validated else {
            throw AbortSync(transient: false)
        }
        if let head {
            guard head.driveFileId == remote.driveFileId,
                  head.revision == remote.manifest.revision,
                  head.manifestHash == manifestHash(of: remote.manifest) else {
                throw AbortSync(transient: false)
            }
            return RemoteManifest(
                manifest: remote.manifest, driveFileId: remote.driveFileId,
                headRevision: head.revision, headHash: head.manifestHash
            )
        } else {
            // Legacy bootstrap: adopt the Drive object via create-if-absent.
            let hash = manifestHash(of: remote.manifest)
            _ = try? await heads.bootstrapHead(
                uid: uid, rootId: rootId,
                driveFileId: remote.driveFileId,
                revision: remote.manifest.revision,
                hash: hash
            )
            let fresh = try? await heads.getHead(uid: uid, rootId: rootId)
            guard let fresh,
                  fresh.driveFileId == remote.driveFileId,
                  fresh.revision == remote.manifest.revision,
                  fresh.manifestHash == hash else {
                throw AbortSync(transient: false)
            }
            return RemoteManifest(
                manifest: remote.manifest, driveFileId: remote.driveFileId,
                headRevision: fresh.revision, headHash: fresh.manifestHash
            )
        }
    }

    private func downloadAndVerifyManifest(_ object: CloudFolderDriveObject, rootId: String) async throws -> CloudFolderManifest {
        let props = object.properties
        guard props[CloudFolderDriveProtocol.keySchema] == "1",
              props[CloudFolderDriveProtocol.keyRootID] == rootId,
              props[CloudFolderDriveProtocol.keyNodeID] == CloudFolderDriveProtocol.manifestNodeID else {
            throw AbortSync(transient: false)
        }
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent(".reader-manifest-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: temp) }
        try await transport.downloadToFile(fileId: object.fileId, destination: temp)
        guard let data = try? Data(contentsOf: temp),
              let json = String(data: data, encoding: .utf8),
              let manifest = CloudFolderSyncCodecKt.decodeCloudFolderManifestOrNull(rawJson: json),
              manifest.rootId == rootId else {
            throw AbortSync(transient: false)
        }
        return manifest
    }

    private func manifestHash(of manifest: CloudFolderManifest) -> String {
        let json = CloudFolderSyncCodecKt.encodeCloudFolderManifest(manifest: manifest)
        return "sha256:\(CloudFolderDriveProtocol.sha256Hex(Data(json.utf8)))"
    }

    // MARK: - local scan

    func managedFolderURL(folderName: String) -> URL? {
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        let cleaned = folderName.replacingOccurrences(of: "/", with: "_").trimmingCharacters(in: .whitespacesAndNewlines)
        let safe = cleaned.isEmpty ? "Imported Folder" : cleaned
        return appSupport.appendingPathComponent("LocalFolders", isDirectory: true).appendingPathComponent(safe, isDirectory: true)
    }

    func offlineDirURL(rootId: String) -> URL? {
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        return appSupport.appendingPathComponent("CloudFolderOffline", isDirectory: true)
            .appendingPathComponent(CloudFolderDriveProtocol.segment(rootId), isDirectory: true)
    }

    func sourceBookmarkURL(folderName: String) -> URL? {
        guard let bookmarks = UserDefaults.standard.dictionary(forKey: "reader.ios.importedFolderBookmarks.v1") as? [String: Data],
              let bookmark = bookmarks[folderName] else { return nil }
        var stale = false
        return try? URL(resolvingBookmarkData: bookmark, options: [.withoutUI], relativeTo: nil, bookmarkDataIsStale: &stale)
    }

    func mimeType(for name: String) -> String? {
        UTType(filenameExtension: (name as NSString).pathExtension.lowercased())?.preferredMIMEType
    }

    func sha256HexOfFile(_ url: URL) throws -> (hash: String, size: Int64) {
        guard let handle = try? FileHandle(forReadingFrom: url) else {
            throw AbortSync(transient: true)
        }
        defer { try? handle.close() }
        var hasher = SHA256()
        var total: Int64 = 0
        while true {
            guard let chunk = try? handle.read(upToCount: 1024 * 1024), !chunk.isEmpty else { break }
            hasher.update(data: chunk)
            total += Int64(chunk.count)
        }
        return ("sha256:" + hasher.finalize().map { String(format: "%02x", $0) }.joined(), total)
    }

    /// Enumerate a local tree into manifest nodes. IDs are retained by path
    /// key from the base manifest, else derived deterministically — mirroring
    /// Android's `nodeIdFor`.
    func scanDirectory(
        dir: URL,
        rootId: String,
        base: CloudFolderManifest?,
        deviceId: String,
        nowMillis: Int64
    ) throws -> (nodes: [CloudFolderNode], complete: Bool, error: String?) {
        let fileManager = FileManager.default
        var isDir: ObjCBool = false
        guard fileManager.fileExists(atPath: dir.path, isDirectory: &isDir), isDir.boolValue else {
            return ([], false, "Local folder is not a directory")
        }
        let baseByPath: [String: CloudFolderNode] = Dictionary(
            uniqueKeysWithValues: (base?.activeNodes() ?? []).map { ($0.pathKey, $0) }
        )
        var nodes: [CloudFolderNode] = []
        var ancestorDirs = Set<String>()
        var complete = true
        var firstError: String?
        guard let enumerator = fileManager.enumerator(
            at: dir,
            includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey, .fileSizeKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return ([], false, "Local folder could not be listed")
        }
        for case let item as URL in enumerator {
            let relative = item.path.replacingOccurrences(of: dir.path + "/", with: "", options: [.anchored])
            let name = item.lastPathComponent
            if name.hasSuffix(".part") || name.hasSuffix(".bak") || name.hasPrefix(".cloud-folder-") {
                continue
            }
            guard let normalized = CloudFolderSyncKt.normalizeCloudFolderRelativePath(path: relative) else {
                complete = false
                firstError = firstError ?? "Local folder returned an unsafe path"
                continue
            }
            // Synthesize missing ancestors so validation always sees explicit
            // directory entries.
            let segments = normalized.split(separator: "/")
            if segments.count > 1 {
                for index in 1..<segments.count {
                    let parent = segments[0..<index].joined(separator: "/")
                    if ancestorDirs.insert(parent).inserted {
                        let key = CloudFolderSyncKt.cloudFolderPathKey(path: parent)
                        let identity = baseByPath[key]
                        nodes.append(CloudFolderSyncSwiftBridgeKt.makeCloudFolderDirectoryNode(
                            nodeId: identity?.nodeId ?? CloudFolderSyncKt.cloudFolderNodeId(rootId: rootId, relativePath: parent),
                            rootId: rootId,
                            relativePath: parent,
                            revision: identity?.revision ?? 0,
                            modifiedAt: nowMillis,
                            modifiedByDeviceId: deviceId
                        ))
                    }
                }
            }
            let values = try? item.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey, .fileSizeKey, .contentModificationDateKey])
            if values?.isDirectory == true {
                ancestorDirs.insert(normalized)
                let key = CloudFolderSyncKt.cloudFolderPathKey(path: normalized)
                let identity = baseByPath[key]
                nodes.append(CloudFolderSyncSwiftBridgeKt.makeCloudFolderDirectoryNode(
                    nodeId: identity?.nodeId ?? CloudFolderSyncKt.cloudFolderNodeId(rootId: rootId, relativePath: normalized),
                    rootId: rootId,
                    relativePath: normalized,
                    revision: identity?.revision ?? 0,
                    modifiedAt: nowMillis,
                    modifiedByDeviceId: deviceId
                ))
            } else if values?.isRegularFile == true {
                let key = CloudFolderSyncKt.cloudFolderPathKey(path: normalized)
                let identity = baseByPath[key]
                let hashed: (hash: String, size: Int64)
                do {
                    hashed = try sha256HexOfFile(item)
                } catch {
                    complete = false
                    firstError = firstError ?? "Local file read failed"
                    continue
                }
                nodes.append(CloudFolderSyncSwiftBridgeKt.makeCloudFolderFileNode(
                    nodeId: identity?.nodeId ?? CloudFolderSyncKt.cloudFolderNodeId(rootId: rootId, relativePath: normalized),
                    rootId: rootId,
                    relativePath: normalized,
                    contentHash: hashed.hash,
                    sizeBytes: hashed.size,
                    mimeType: mimeType(for: name),
                    fileModifiedAt: Int64((values?.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000),
                    revision: identity?.revision ?? 0,
                    modifiedAt: nowMillis,
                    modifiedByDeviceId: deviceId,
                    contentObjectId: identity?.contentObjectId
                ))
            }
        }
        return (nodes, complete, firstError)
    }
}
