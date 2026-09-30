import Foundation
import OSLog
import ReaderShared

#if canImport(CloudKit)
import CloudKit
#endif

/// CloudKit data-plane for pure library sync (Pro, iOS-only).
///
/// Design mirrors the Drive+Firestore split without the second system:
/// - Hot `BookState` records carry only small fields (position, progress,
///   bookmarks/highlights JSON, clocks). No assets, so page turns save fast.
/// - Cold `BookContent` / `FontContent` records carry `CKAsset` bytes and are
///   written only when `fileContentModifiedTimestamp` wins (shared
///   `CloudSyncDecisions`), so unchanged bytes never pay asset cost.
/// - PDF sidecars are inline record fields (small JSON), not assets.
/// - Reads are incremental `CKFetchRecordZoneChangesOperation` deltas against a
///   persisted server change token, folded into a persisted remote shadow; a
///   pass that finds no changes costs one empty round trip, not a full-library
///   enumeration. `CKAsset` bytes download lazily only for books/fonts actually
///   stale.
/// - Push wakeups: a `CKRecordZoneSubscription` is created, but as of this
///   commit no app delegate consumes the resulting silent push (the
///   `com.apple.Push` capability is not enabled), so the subscription is
///   currently inert. Change detection is user-initiated + `BGTaskScheduler`.
///   No FCM: CloudKit's own subscription is the push channel; FCM would only
///   matter for waking Android, which is on Drive/Firestore, not iCloud.
///
/// Android is the benchmark and is NOT changed. Drive/Firestore stays dormant.
#if canImport(CloudKit)
final class CloudKitLibraryTransport {
    enum TransportError: Error {
        case unavailable(String)
        case retryAfter(TimeInterval)
        case transient(String)
        case deterministic(String)

        /// The associated reason, which `localizedDescription` otherwise drops.
        /// Without it a thrown `TransportError` reaches the log as "The
        /// operation couldn't be completed. (…TransportError error 2.)", which is
        /// how a CloudKit asset failure looked like an unexplained retry loop.
        var reason: String {
            switch self {
            case .unavailable(let detail): return "unavailable: \(detail)"
            case .retryAfter(let seconds): return "retryAfter: \(seconds)s"
            case .transient(let detail): return "transient: \(detail)"
            case .deterministic(let detail): return "deterministic: \(detail)"
            }
        }
    }

    static let zoneName = "LibraryZone"
    private static let lastUserKey = "reader.ios.cloudkit.lastUserRecord.v1"
    /// Per-page cap for the full-zone snapshot. CloudKit caps `resultsLimit`
    /// at 500; anything smaller means more pages.
    private static let snapshotPageSize = 500
    /// Bounded loop so a broken `moreComing` flag can never spin the fetch.
    /// 400 pages × 500 records = 200k records, far beyond any library.
    private static let maxSnapshotPages = 400

    private let container: CKContainer
    private let database: CKDatabase
    /// Common tag for this feature: filter Console with
    /// subsystem `com.aryan.reader`, category `CloudKitSync`, or grep the
    /// `cloudkit_sync.` message prefix. Every line carries both.
    private let logger = Logger(subsystem: "com.aryan.reader", category: "CloudKitSync")

    init(container: CKContainer = .default()) {
        self.container = container
        self.database = container.privateCloudDatabase
    }

    var zoneID: CKRecordZone.ID {
        CKRecordZone.ID(zoneName: Self.zoneName, ownerName: CKCurrentUserDefaultName)
    }

    // MARK: - gates

    /// iCloud account must be available before any data-plane call.
    func requireAccount() async throws {
        let status: CKAccountStatus
        do {
            status = try await container.accountStatus()
        } catch {
            logger.error("cloudkit_sync.account_check_failed error=\(error.localizedDescription, privacy: .public)")
            throw mapCKError(error)
        }
        guard status == .available else {
            logger.error("cloudkit_sync.account_unavailable status=\(String(describing: status), privacy: .public)")
            throw TransportError.deterministic("iCloud account is not available.")
        }
        logger.info("cloudkit_sync.account_ok")
    }

    /// Detect Apple-ID rotation: a new silo must wipe the outbox and the remote
    /// shadow, mirroring the Firebase uid-switch behavior. The shadow matters
    /// most now that reads are incremental — its zone change token belongs to
    /// the previous account's private database and is meaningless in the new
    /// one.
    @MainActor
    func checkUserRotation() async throws -> Bool {
        let recordID = try await container.userRecordID()
        let current = recordID.recordName
        let previous = UserDefaults.standard.string(forKey: Self.lastUserKey)
        if previous == nil {
            UserDefaults.standard.set(current, forKey: Self.lastUserKey)
            logger.info("cloudkit_sync.user_first_seen")
            return false
        }
        if previous != current {
            UserDefaults.standard.set(current, forKey: Self.lastUserKey)
            logger.info("cloudkit_sync.user_rotated")
            return true
        }
        return false
    }

    func ensureZone() async throws {
        do {
            _ = try await database.recordZone(for: zoneID)
            logger.info("cloudkit_sync.zone_exists zone=\(Self.zoneName, privacy: .public)")
        } catch {
            logger.info("cloudkit_sync.zone_creating zone=\(Self.zoneName, privacy: .public)")
            let zone = CKRecordZone(zoneID: zoneID)
            do {
                _ = try await database.save(zone)
                logger.info("cloudkit_sync.zone_created zone=\(Self.zoneName, privacy: .public)")
            } catch let error as CKError where error.code == .serverRejectedRequest {
                logger.error("cloudkit_sync.zone_rejected zone=\(Self.zoneName, privacy: .public)")
                throw TransportError.deterministic("CloudKit zone was rejected.")
            } catch {
                throw mapCKError(error)
            }
        }
    }

    // MARK: - writes (dynamic, batched)

    /// Save small state records. `changedKeys` policy keeps untouched asset
    /// fields off the wire; batch in one modify operation per call.
    /// Per-record partial failures surface as throw (covered by outbox retry);
    /// whole-operation errors map through `mapCKError`.
    func saveStateRecords(_ records: [CKRecord]) async throws {
        guard !records.isEmpty else { return }
        let types = Dictionary(grouping: records, by: \.recordType).mapValues(\.count)
        logger.debug("cloudkit_sync.save_start count=\(records.count) types=\(String(describing: types), privacy: .public)")
        do {
            let result = try await database.modifyRecords(
                saving: records, deleting: [], savePolicy: .changedKeys, atomically: false
            )
            for (id, saveResult) in result.saveResults {
                if case .failure(let error) = saveResult {
                    logger.error("cloudkit_sync.save_record_failed record=\(id.recordName, privacy: .public) error=\(error.localizedDescription, privacy: .public)")
                    throw error
                }
            }
            logger.info("cloudkit_sync.save_ok count=\(records.count)")
        } catch {
            throw mapCKError(error)
        }
    }

    func saveRecords(_ records: [CKRecord]) async throws {
        try await saveStateRecords(records)
    }

    func deleteRecordIDs(_ recordIDs: [CKRecord.ID]) async throws {
        guard !recordIDs.isEmpty else { return }
        do {
            let result = try await database.modifyRecords(
                saving: [], deleting: recordIDs, savePolicy: .changedKeys, atomically: false
            )
            for (id, deleteResult) in result.deleteResults {
                if case .failure(let error) = deleteResult {
                    // Already gone counts as success (idempotent deletes).
                    if (error as? CKError)?.code == .unknownItem { continue }
                    logger.error("cloudkit_sync.delete_record_failed record=\(id.recordName, privacy: .public) error=\(error.localizedDescription, privacy: .public)")
                    throw error
                }
            }
            logger.info("cloudkit_sync.delete_ok count=\(recordIDs.count)")
        } catch {
            throw mapCKError(error)
        }
    }

    func fetchRecord(recordName: String) async throws -> CKRecord? {
        do {
            return try await database.record(for: CKRecord.ID(recordName: recordName, zoneID: zoneID))
        } catch let error as CKError where error.code == .unknownItem {
            return nil
        } catch {
            throw mapCKError(error)
        }
    }

    // MARK: - reads (incremental zone deltas, assets fetched lazily)

    /// One fetch against the library zone.
    struct ZoneDelta {
        /// Records created or updated since the supplied token.
        var records: [CKRecord] = []
        /// Records removed since the supplied token.
        var deletedRecordIDs: [CKRecord.ID] = []
        /// Token the caller persists *after* the pass commits, so a pass that
        /// fails or is superseded replays the same window instead of skipping it.
        var token: CKServerChangeToken?
        /// True when no usable token was supplied, so `records` is the complete
        /// live zone rather than a delta. The caller must then discard any
        /// shadow it had persisted: the records enumerate live state but the
        /// deletions belong to all of history, and folding them into an older
        /// shadow would drop state the shadow still believes exists.
        var isFullSnapshot: Bool = false
    }

    /// Records changed since `token`, or the whole live zone when `token` is
    /// nil.
    ///
    /// Delta reads are the reason this is not a full enumeration every pass:
    /// the previous implementation always passed `since: nil` and paged the
    /// entire zone (up to 400 pages) on *both* pull and push, so a reader-close
    /// push cost a full-library network round trip. CKSyncEngine would fix this
    /// too, but it is a much larger migration and the change token alone gets
    /// steady-state passes down to "only what actually changed".
    ///
    /// Unlike a `CKQuery` this needs no queryable field indexes (an
    /// auto-generated dev schema has none, and `CKQuery` fails there with code
    /// 12 "Field 'recordName' is not marked queryable"). CloudKit query results
    /// never carry asset bytes anyway, so `CKAsset.fileURL` is nil on these
    /// records; content/fonts download lazily via `fetchContentAsset`/
    /// `fetchFontAsset` only when a book/font is actually missing or stale.
    func fetchZoneChanges(since token: CKServerChangeToken?) async throws -> ZoneDelta {
        var delta = ZoneDelta()
        delta.isFullSnapshot = token == nil
        var cursor = token
        var page = 0
        var moreComing = true
        while moreComing && page < Self.maxSnapshotPages {
            page += 1
            do {
                let (modifications, deletions, nextPageToken, zoneMoreComing) = try await database.recordZoneChanges(
                    inZoneWith: zoneID,
                    since: cursor,
                    desiredKeys: nil,
                    resultsLimit: Self.snapshotPageSize
                )
                for (_, result) in modifications {
                    if case .success(let modification) = result {
                        delta.records.append(modification.record)
                    }
                }
                delta.deletedRecordIDs.append(contentsOf: deletions.map(\.recordID))
                cursor = nextPageToken
                moreComing = zoneMoreComing
            } catch {
                throw mapCKError(error)
            }
        }
        if moreComing {
            // Hit the page cap with work still pending. Returning the partial
            // cursor would make the next pass believe it had caught up, so drop
            // the token and let the next call redo the zone from scratch.
            logger.error("cloudkit_sync.fetch_all page_cap_hit pages=\(page)")
            delta.token = nil
            delta.isFullSnapshot = true
        } else {
            delta.token = cursor
        }
        logger.info("cloudkit_sync.fetch_zone_changes records=\(delta.records.count, privacy: .public) deleted=\(delta.deletedRecordIDs.count, privacy: .public) pages=\(page, privacy: .public) full=\(delta.isFullSnapshot, privacy: .public)")
        return delta
    }

    // MARK: - server change token serialization

    /// `CKServerChangeToken` conforms to `NSSecureCoding` and CloudKit documents
    /// it as safe to cache on disk, so it is archived to base64 and carried
    /// inside the caller's baseline file. Keeping it in the *same* file as the
    /// remote shadow is deliberate: the token and the shadow are only valid
    /// together, and two separate writes could be torn by a crash between them
    /// (token advanced, shadow not yet written) and silently skip a delta.
    /// A single atomic file write cannot be torn.
    func archiveChangeToken(_ token: CKServerChangeToken?) -> String? {
        guard let token else { return nil }
        do {
            let data = try NSKeyedArchiver.archivedData(withRootObject: token, requiringSecureCoding: true)
            return data.base64EncodedString()
        } catch {
            logger.error("cloudkit_sync.change_token_archive_failed error=\(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    func unarchiveChangeToken(_ raw: String?) -> CKServerChangeToken? {
        guard let raw, let data = Data(base64Encoded: raw) else { return nil }
        do {
            return try NSKeyedUnarchiver.unarchivedObject(ofClass: CKServerChangeToken.self, from: data)
        } catch {
            // A token that no longer decodes (app update, schema change) is
            // indistinguishable from no token: both mean "re-read the zone".
            logger.error("cloudkit_sync.change_token_decode_failed error=\(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    /// Download one `BookContent` asset (book bytes). Returns the temp file
    /// URL CloudKit materialises, or nil when no such record exists.
    func fetchContentAsset(bookId: String) async throws -> URL? {
        let name = CloudKitLibrarySyncKt.cloudKitBookContentRecordName(bookId: bookId)
        return try await fetchAsset(recordName: name, key: "contentAsset")
    }

    /// Batch `BookContent` asset download by book id. `database.records(for:)`
    /// fetches every record (with its `CKAsset`) in one operation, so a
    /// multi-book pull does one round trip instead of one per book. Maps each
    /// requested id to its materialised asset URL; missing/failed records are
    /// simply absent from the result.
    /// Download book bytes and return paths this app owns.
    ///
    /// A `CKAsset.fileURL` is not a durable reference: CloudKit materializes it
    /// into its own temporary directory and deletes it once the record that owns
    /// it is released. Returning that URL and reading it after the call returns
    /// races the cleanup — the pull path does several more awaits before it
    /// copies the file, and the observed failure was
    /// `The file "….01cf03…" doesn't exist`. The bytes are therefore copied into
    /// an app-owned staging directory here, while the record is still alive.
    /// The caller must call `discardStagedAssets()` when finished.
    func fetchContentAssets(bookIds: [String]) async throws -> [String: URL] {
        guard !bookIds.isEmpty else { return [:] }
        let idToBook = Dictionary(
            uniqueKeysWithValues: bookIds.map {
                (CKRecord.ID(
                    recordName: CloudKitLibrarySyncKt.cloudKitBookContentRecordName(bookId: $0),
                    zoneID: zoneID
                ), $0)
            }
        )
        let result: [String: URL]
        do {
            let fetched = try await database.records(for: Array(idToBook.keys))
            var staged: [String: URL] = [:]
            for (id, outcome) in fetched {
                guard let bookId = idToBook[id],
                      case .success(let record) = outcome,
                      let source = (record["contentAsset"] as? CKAsset)?.fileURL else { continue }
                // `record` stays bound in this scope for the whole copy, which
                // keeps the CKAsset's temp file alive.
                staged[bookId] = try stageAssetSync(source, named: "\(bookId).asset")
            }
            result = staged
        } catch {
            throw mapCKError(error)
        }
        logger.info("cloudkit_sync.fetch_assets requested=\(bookIds.count) got=\(result.count)")
        return result
    }

    /// Copy a CloudKit-materialized asset into app-owned staging.
    ///
    /// `CKAsset.fileURL` can name a file that has not been materialized yet:
    /// CloudKit creates the temp file lazily, so an immediate copy fails with
    /// `NSCocoaErrorDomain/4` ("doesn't exist") even though the record and its
    /// asset are perfectly valid. The observed symptom was one new temp UUID per
    /// retry with nothing ever copied. Poll briefly for the file to appear before
    /// copying, and report a transient failure so the durable outbox retries with
    /// a fresh fetch rather than the caller's backoff being blamed.
    private func stageAsset(_ source: URL, named name: String) async throws -> URL {
        guard await waitForFileToAppear(at: source) else {
            throw TransportError.transient("CloudKit asset was not materialized: \(source.lastPathComponent)")
        }
        let directory = try assetStagingDirectory()
        // Namespaced by pid so two passes (pull and push can overlap) never
        // delete each other's staging files.
        let destination = directory.appendingPathComponent("\(ProcessInfo.processInfo.processIdentifier)-\(name)")
        try? FileManager.default.removeItem(at: destination)
        do {
            try FileManager.default.copyItem(at: source, to: destination)
        } catch {
            throw TransportError.transient("CloudKit asset copy failed: \(error.localizedDescription)")
        }
        return destination
    }

    /// Wait for a lazily-materialized temp file to exist. Returns false if it
    /// never shows up within the budget.
    /// Synchronous variant for callers already holding a strong reference to the
    /// record on the current task.
    private func stageAssetSync(_ source: URL, named name: String) throws -> URL {
        guard FileManager.default.fileExists(atPath: source.path) else {
            logger.error(
                "cloudkit_sync.stage_asset failed=missing name=\(name, privacy: .public)"
            )
            throw TransportError.transient("CloudKit asset was not materialized: \(source.lastPathComponent)")
        }
        let directory = try assetStagingDirectory()
        let destination = directory.appendingPathComponent("\(ProcessInfo.processInfo.processIdentifier)-\(name)")
        try? FileManager.default.removeItem(at: destination)
        do {
            try FileManager.default.copyItem(at: source, to: destination)
        } catch {
            logger.error(
                "cloudkit_sync.stage_asset failed=copy name=\(name, privacy: .public) error=\(error.localizedDescription, privacy: .public)"
            )
            throw TransportError.transient("CloudKit asset copy failed: \(error.localizedDescription)")
        }
        logger.info("cloudkit_sync.stage_asset failed=none name=\(name, privacy: .public)")
        return destination
    }

    /// Wait for a lazily-materialized temp file to exist. Returns false if it
    /// never shows up within the budget.
    private func waitForFileToAppear(at url: URL, timeout: TimeInterval = 3.0) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if FileManager.default.fileExists(atPath: url.path) { return true }
            try? await Task.sleep(nanoseconds: 60_000_000)   // 60ms
        }
        return FileManager.default.fileExists(atPath: url.path)
    }

    private func assetStagingDirectory() throws -> URL {
        let base = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("cloudkit-assets", isDirectory: true)
        try FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    /// Remove every staged asset for this process. Safe to call when nothing was
    /// staged, and safe to call twice.
    func discardStagedAssets() {
        guard let directory = try? assetStagingDirectory() else { return }
        let prefix = "\(ProcessInfo.processInfo.processIdentifier)-"
        let contents = (try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: nil
        )) ?? []
        for url in contents where url.lastPathComponent.hasPrefix(prefix) {
            try? FileManager.default.removeItem(at: url)
        }
    }

    /// Download one `FontContent` asset (font bytes).
    func fetchFontAsset(fontId: String) async throws -> URL? {
        let name = CloudKitLibrarySyncKt.cloudKitLibraryRecordName(
            recordType: CloudKitLibrarySyncKt.CLOUDKIT_RECORD_FONT_CONTENT, id: fontId
        )
        return try await fetchAsset(recordName: name, key: "contentAsset")
    }

    /// Staged for the same reason as [fetchContentAssets]: the returned path
    /// must outlive the `CKRecord` that owns the CloudKit temp file.
    private func fetchAsset(recordName: String, key: String) async throws -> URL? {
        do {
            let record = try await database.record(for: CKRecord.ID(recordName: recordName, zoneID: zoneID))
            guard let source = (record[key] as? CKAsset)?.fileURL else { return nil }
            return try await stageAsset(source, named: "\(recordName.replacingOccurrences(of: ":", with: "-")).asset")
        } catch let error as CKError where error.code == .unknownItem {
            return nil
        } catch {
            throw mapCKError(error)
        }
    }

    // MARK: - subscriptions (push, no FCM)

    func ensureSubscription() async throws {
        let subscriptionID = "library-zone-changes"
        do {
            let existing = try await database.allSubscriptions()
            if existing.contains(where: { $0.subscriptionID == subscriptionID }) {
                logger.debug("cloudkit_sync.subscription_exists id=\(subscriptionID, privacy: .public)")
                return
            }
            let subscription = CKRecordZoneSubscription(
                zoneID: zoneID,
                subscriptionID: subscriptionID
            )
            let info = CKSubscription.NotificationInfo()
            info.shouldSendContentAvailable = true
            subscription.notificationInfo = info
            _ = try await database.save(subscription)
            logger.info("cloudkit_sync.subscription_created id=\(subscriptionID, privacy: .public)")
        } catch {
            logger.error("cloudkit_sync.subscription_failed error=\(error.localizedDescription, privacy: .public)")
            throw mapCKError(error)
        }
    }

    // MARK: - record builders

    func stateRecord(recordName: String, fields: [String: Any]) -> CKRecord {
        let id = CKRecord.ID(recordName: recordName, zoneID: zoneID)
        let record = CKRecord(recordType: CloudKitLibrarySyncKt.CLOUDKIT_RECORD_BOOK_STATE, recordID: id)
        applyFields(record, fields: fields)
        return record
    }

    func genericRecord(recordType: String, recordName: String, fields: [String: Any]) -> CKRecord {
        let id = CKRecord.ID(recordName: recordName, zoneID: zoneID)
        let record = CKRecord(recordType: recordType, recordID: id)
        applyFields(record, fields: fields)
        return record
    }

    func contentRecord(recordName: String, fields: [String: Any], assetURL: URL?) -> CKRecord {
        let id = CKRecord.ID(recordName: recordName, zoneID: zoneID)
        let record = CKRecord(recordType: CloudKitLibrarySyncKt.CLOUDKIT_RECORD_BOOK_CONTENT, recordID: id)
        applyFields(record, fields: fields)
        if let assetURL { record["contentAsset"] = CKAsset(fileURL: assetURL) }
        return record
    }

    private func applyFields(_ record: CKRecord, fields: [String: Any]) {
        for (key, value) in fields {
            switch value {
            case is NSNull: record[key] = nil
            case let number as NSNumber: record[key] = number
            case let string as String: record[key] = string as CKRecordValue
            case let int as Int: record[key] = int as CKRecordValue
            case let int64 as Int64: record[key] = int64 as CKRecordValue
            case let double as Double: record[key] = double as CKRecordValue
            case let bool as Bool: record[key] = bool as CKRecordValue
            case let date as Date: record[key] = date as CKRecordValue
            default: continue
            }
        }
    }

    func recordFields(_ record: CKRecord) -> [String: Any] {
        var out: [String: Any] = [:]
        for key in record.allKeys() {
            if let value = record[key] { out[key] = value }
        }
        return out
    }

    // MARK: - errors

    func mapCKError(_ error: Error) -> TransportError {
        guard let ckError = error as? CKError else {
            let nsError = error as NSError
            // A `TransportError` thrown by our own code (asset staging) carries
            // the real reason in its payload; `localizedDescription` drops it and
            // reports "TransportError error 2" instead, which is how a CloudKit
            // asset failure looked like an unexplained retry loop.
            let reason = (error as? TransportError)?.reason
                ?? "\(nsError.domain)/\(nsError.code): \(nsError.localizedDescription)"
            logger.error("cloudkit_sync.error non_ck ns=\(nsError.domain)/\(nsError.code) reason=\(reason, privacy: .public)")
            return .transient(reason)
        }
        // One authoritative failure line. Raw `code.rawValue` is stable across SDKs
        // (7 requestRateLimited, 14 serverRecordChanged, 19 constraintViolation,
        // 21 changeTokenExpired, 25 quotaExceeded, 4 networkFailure, 6
        // serviceUnavailable, 23 zoneBusy, …). The case-name below is derived
        // from the same header, so the logged integer is the source of truth.
        let retryAfter = ckError.retryAfterSeconds.map { String(Int($0)) } ?? "none"
        let underlying = (ckError.userInfo["CKErrorUnderlyingError"] as? NSError)
            .map { " underlying=\($0.domain)/\($0.code)" } ?? ""
        let line = "code=\(ckError.code.rawValue) retryAfter=\(retryAfter)\(underlying) msg=\(ckError.localizedDescription)"
        logger.error("cloudkit_sync.error \(line.replacingOccurrences(of: "\n", with: " "), privacy: .public)")
        // A server-supplied retry window always wins (rate limit / busy zone).
        if let retryAfter = ckError.retryAfterSeconds, retryAfter > 0 {
            return .retryAfter(retryAfter)
        }
        switch ckError.code {
        case .changeTokenExpired, .partialFailure, .networkUnavailable, .networkFailure, .serviceUnavailable,
             .requestRateLimited, .zoneBusy, .operationCancelled, .serverResponseLost,
             .batchRequestFailed, .limitExceeded, .assetFileModified, .assetNotAvailable,
             .accountTemporarilyUnavailable:
            // Retryable: the shared outbox backoff covers these. Reads are now
            // full-zone queries (no persisted change token), so a stale token
            // can never strand a pass.
            return .transient(ckError.localizedDescription)
        case .internalError, .badContainer, .missingEntitlement, .notAuthenticated,
             .permissionFailure, .quotaExceeded, .unknownItem, .invalidArguments,
             .serverRecordChanged, .serverRejectedRequest, .assetFileNotFound,
             .incompatibleVersion, .constraintViolation, .badDatabase, .zoneNotFound,
             .userDeletedZone:
            // Deterministic: retrying cannot help without a state/schema change.
            // serverRecordChanged self-heals because the next pass re-reads the
            // authoritative state and shared LWW decides; do not loop.
            return .deterministic(ckError.localizedDescription)
        default:
            return .transient(ckError.localizedDescription)
        }
    }

    /// Shared retry math: server `retryAfter` wins, else exponential backoff.
    func retryDelay(attempt: Int, retryAfter: TimeInterval?) -> TimeInterval {
        let retryAfterMs: Int64? = retryAfter.map { Int64($0 * 1000) }
        let ms = CloudKitLibrarySyncKt.cloudKitLibraryRetryDelayMs(
            attempt: Int32(attempt),
            retryAfterMs: retryAfterMs.map { KotlinLong(longLong: $0) }
        )
        return TimeInterval(ms) / 1000
    }
}
#else
/// Fallback when CloudKit is unavailable (simulator slices without the
/// framework). Keeps the Drive path compiling; CloudKit sync reports
/// unavailable through the normal outbox/status channel.
final class CloudKitLibraryTransport {
    enum TransportError: Error {
        case unavailable(String)
        case retryAfter(TimeInterval)
        case transient(String)
        case deterministic(String)
    }

    func requireAccount() async throws {
        throw TransportError.unavailable("CloudKit is unavailable in this build.")
    }

    @MainActor func checkUserRotation() async throws -> Bool { false }
    func ensureZone() async throws {
        throw TransportError.unavailable("CloudKit is unavailable in this build.")
    }
    func retryDelay(attempt: Int, retryAfter: TimeInterval?) -> TimeInterval {
        TimeInterval(CloudKitLibrarySyncKt.cloudKitLibraryRetryDelayMs(attempt: Int32(attempt), retryAfterMs: nil)) / 1000
    }
}
#endif
