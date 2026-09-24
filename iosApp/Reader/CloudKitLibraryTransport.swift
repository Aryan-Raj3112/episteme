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
/// - Reads are delta-based (`CKFetchRecordZoneChangesOperation` + persisted
///   server change token); assets download lazily by hash miss.
/// - Push uses `CKDatabaseSubscription`; no FCM, no polling loop.
///
/// Android is the benchmark and is NOT changed. Drive/Firestore stays dormant.
#if canImport(CloudKit)
final class CloudKitLibraryTransport {
    enum TransportError: Error {
        case unavailable(String)
        case retryAfter(TimeInterval)
        case transient(String)
        case deterministic(String)
    }

    static let zoneName = "LibraryZone"
    private static let changeTokenKey = "reader.ios.cloudkit.libraryZoneToken.v1"
    private static let lastUserKey = "reader.ios.cloudkit.lastUserRecord.v1"

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

    /// Detect Apple-ID rotation: a new silo must wipe tokens/outbox, mirroring
    /// the Firebase uid-switch behavior.
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
            UserDefaults.standard.removeObject(forKey: Self.changeTokenKey)
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

    // MARK: - reads (delta)

    struct ZoneChanges {
        var changed: [CKRecord]
        var deletedRecordNames: Set<String>
        var moreComing: Bool
    }

    func fetchChanges() async throws -> ZoneChanges {
        let token: CKServerChangeToken? = loadToken()
        var changed: [CKRecord] = []
        var deleted: Set<String> = []
        var moreComing = false
        do {
            let (modifications, deletions, changeToken, zoneMoreComing) = try await database.recordZoneChanges(
                inZoneWith: zoneID,
                since: token
            )
            for (_, result) in modifications {
                if case .success(let modification) = result {
                    changed.append(modification.record)
                }
            }
            for deletion in deletions {
                deleted.insert(deletion.recordID.recordName)
            }
            saveToken(changeToken)
            moreComing = zoneMoreComing
            logger.info("cloudkit_sync.fetch_changes changed=\(changed.count) deleted=\(deleted.count) moreComing=\(moreComing) hadToken=\(token != nil)")
        } catch {
            throw mapCKError(error)
        }
        return ZoneChanges(changed: changed, deletedRecordNames: deleted, moreComing: moreComing)
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

    // MARK: - tokens

    private func loadToken() -> CKServerChangeToken? {
        guard let data = UserDefaults.standard.data(forKey: Self.changeTokenKey),
              let token = try? NSKeyedUnarchiver.unarchivedObject(ofClass: CKServerChangeToken.self, from: data) else {
            return nil
        }
        return token
    }

    private func saveToken(_ token: CKServerChangeToken) {
        if let data = try? NSKeyedArchiver.archivedData(withRootObject: token, requiringSecureCoding: true) {
            UserDefaults.standard.set(data, forKey: Self.changeTokenKey)
        }
    }

    func resetToken() {
        UserDefaults.standard.removeObject(forKey: Self.changeTokenKey)
    }

    // MARK: - errors

    func mapCKError(_ error: Error) -> TransportError {
        guard let ckError = error as? CKError else {
            let nsError = error as NSError
            logger.error("cloudkit_sync.error non_ck ns=\(nsError.domain)/\(nsError.code) msg=\(error.localizedDescription, privacy: .public)")
            return .transient(nsError.localizedDescription)
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
        case .changeTokenExpired:
            // The persisted zone token is stale. Drop it so the next pass does
            // a full re-fetch instead of looping on a doomed delta request.
            resetToken()
            return .transient("CloudKit change token expired; full refetch scheduled.")
        case .partialFailure, .networkUnavailable, .networkFailure, .serviceUnavailable,
             .requestRateLimited, .zoneBusy, .operationCancelled, .serverResponseLost,
             .batchRequestFailed, .limitExceeded, .assetFileModified, .assetNotAvailable,
             .accountTemporarilyUnavailable:
            // Retryable: the shared outbox backoff covers these.
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
