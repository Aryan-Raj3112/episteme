import Foundation

#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif

/// Firestore manifest-head CAS for cloud-folder sync.
///
/// Android is the absolute benchmark and is NOT changed. This mirrors the Pro
/// `FirestoreRepository` folder-head methods used by `CloudFolderSyncWorker`
/// (app/src/pro/java/com/aryan/reader/data/FirestoreRepository.kt):
/// collection `users/{uid}/cloudFolderHeads/{rootId}`, fields `schemaVersion`,
/// `rootId`, `state` (COMMITTING|COMMITTED), `revision`, `manifestDriveFileId`,
/// `manifestHash`, `leaseToken`, `leaseRevision`, `leaseExpectedRevision`,
/// `leaseExpiresAt` (10 min), `writerDeviceId`, `updatedAt`. The CAS order is
/// reserve(expectedRev) → Drive upload → commit(driveId, hash), with release
/// restoring the previous head on failure. Readers pin the exact Drive object
/// from the head and verify name + hash before consuming.
struct CloudFolderHead {
    var revision: Int64
    var driveFileId: String
    var manifestHash: String
    var state: String
}

struct CloudFolderLease {
    var rootId: String
    var revision: Int64
    var expectedRevision: Int64?
    var leaseToken: String
    var deviceId: String
    var previousRevision: Int64?
    var previousDriveFileId: String?
    var previousHash: String?
}

enum CloudFolderHeadError: Error {
    case unavailable
    case malformed
    case conflict
    case unsupported
}

final class CloudFolderHeadsService {
    private static let leaseDuration: TimeInterval = 10 * 60

    #if canImport(FirebaseFirestore)
    private func headsCollection(uid: String) -> CollectionReference {
        Firestore.firestore().collection("users").document(uid).collection("cloudFolderHeads")
    }

    private func parseHead(_ data: [String: Any]) throws -> CloudFolderHead {
        guard let revision = (data["revision"] as? NSNumber)?.int64Value,
              revision >= 0,
              let driveFileId = data["manifestDriveFileId"] as? String, !driveFileId.isEmpty,
              let hash = data["manifestHash"] as? String,
              CloudFolderDriveProtocol.isSHA256(hash) else {
            throw CloudFolderHeadError.malformed
        }
        let state = data["state"] as? String ?? "COMMITTED"
        return CloudFolderHead(revision: revision, driveFileId: driveFileId, manifestHash: hash, state: state)
    }

    /// Committed head, or nil when no record exists. A bare COMMITTING record
    /// with no revision is malformed (mirrors Android's throw).
    func getHead(uid: String, rootId: String) async throws -> CloudFolderHead? {
        let snapshot = try await headsCollection(uid: uid).document(rootId).getDocument()
        guard snapshot.exists, let data = snapshot.data() else { return nil }
        if (data["state"] as? String) == "COMMITTING" && data["revision"] == nil {
            throw CloudFolderHeadError.malformed
        }
        return try parseHead(data)
    }

    /// Best-effort legacy bootstrap: create-if-absent so two racers cannot
    /// fork the head. Returns false when a record already exists.
    func bootstrapHead(uid: String, rootId: String, driveFileId: String, revision: Int64, hash: String) async throws -> Bool {
        let ref = headsCollection(uid: uid).document(rootId)
        return try await withCheckedThrowingContinuation { continuation in
            Firestore.firestore().runTransaction({ transaction, errorPointer -> Any? in
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(ref)
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                if snapshot.exists {
                    return false
                }
                transaction.setData([
                    "schemaVersion": 1,
                    "rootId": rootId,
                    "state": "COMMITTED",
                    "revision": revision,
                    "manifestDriveFileId": driveFileId,
                    "manifestHash": hash,
                    "writerDeviceId": "",
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: ref)
                return true
            }, completion: { result, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: (result as? Bool) ?? false)
                }
            })
        }
    }

    /// Reserve the next revision. Throws `.conflict` when another device holds
    /// a live lease or the expected revision no longer matches; `.unsupported`
    /// when the head shape cannot be reserved.
    func reserve(
        uid: String,
        rootId: String,
        expectedRevision: Int64?,
        revision: Int64,
        deviceId: String
    ) async throws -> CloudFolderLease {
        guard revision > (expectedRevision ?? -1) else { throw CloudFolderHeadError.unsupported }
        let ref = headsCollection(uid: uid).document(rootId)
        return try await withCheckedThrowingContinuation { continuation in
            Firestore.firestore().runTransaction({ transaction, errorPointer -> Any? in
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(ref)
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                let data = snapshot.data() ?? [:]
                let currentRevision = (data["revision"] as? NSNumber)?.int64Value
                if let expected = expectedRevision {
                    guard currentRevision == expected else {
                        errorPointer?.pointee = CloudFolderHeadError.conflict as NSError
                        return nil
                    }
                } else if currentRevision != nil {
                    errorPointer?.pointee = CloudFolderHeadError.conflict as NSError
                    return nil
                }
                // A live COMMITTING lease owned by another device blocks us;
                // our own abandoned lease is taken over.
                if (data["state"] as? String) == "COMMITTING",
                   let expires = (data["leaseExpiresAt"] as? Timestamp)?.dateValue(),
                   expires > Date() {
                    let owner = data["writerDeviceId"] as? String ?? ""
                    if owner != deviceId {
                        errorPointer?.pointee = CloudFolderHeadError.conflict as NSError
                        return nil
                    }
                }
                let token = UUID().uuidString
                transaction.setData([
                    "schemaVersion": 1,
                    "rootId": rootId,
                    "state": "COMMITTING",
                    "revision": currentRevision as Any,
                    "manifestDriveFileId": data["manifestDriveFileId"] as Any,
                    "manifestHash": data["manifestHash"] as Any,
                    "leaseToken": token,
                    "leaseRevision": revision,
                    "leaseExpectedRevision": expectedRevision as Any,
                    "leaseExpiresAt": Timestamp(date: Date().addingTimeInterval(Self.leaseDuration)),
                    "writerDeviceId": deviceId,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: ref, merge: true)
                let lease = CloudFolderLease(
                    rootId: rootId,
                    revision: revision,
                    expectedRevision: expectedRevision,
                    leaseToken: token,
                    deviceId: deviceId,
                    previousRevision: currentRevision,
                    previousDriveFileId: data["manifestDriveFileId"] as? String,
                    previousHash: data["manifestHash"] as? String
                )
                return lease
            }, completion: { result, error in
                if let error {
                    continuation.resume(throwing: (error as NSError).code == 0 ? CloudFolderHeadError.conflict : error)
                } else if let lease = result as? CloudFolderLease {
                    continuation.resume(returning: lease)
                } else {
                    continuation.resume(throwing: CloudFolderHeadError.conflict)
                }
            })
        }
    }

    func commit(uid: String, lease: CloudFolderLease, driveFileId: String, hash: String) async throws {
        let ref = headsCollection(uid: uid).document(lease.rootId)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            Firestore.firestore().runTransaction({ transaction, errorPointer -> Any? in
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(ref)
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                let data = snapshot.data() ?? [:]
                guard (data["state"] as? String) == "COMMITTING",
                      (data["leaseToken"] as? String) == lease.leaseToken,
                      (data["leaseRevision"] as? NSNumber)?.int64Value == lease.revision else {
                    errorPointer?.pointee = CloudFolderHeadError.conflict as NSError
                    return nil
                }
                transaction.setData([
                    "schemaVersion": 1,
                    "rootId": lease.rootId,
                    "state": "COMMITTED",
                    "revision": lease.revision,
                    "manifestDriveFileId": driveFileId,
                    "manifestHash": hash,
                    "writerDeviceId": lease.deviceId,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: ref, merge: true)
                return true
            }, completion: { _, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            })
        }
    }

    /// Restore the previous head (or delete when there was none) after a
    /// failed publish, mirroring Android's `releaseCloudFolderManifest`.
    func release(uid: String, lease: CloudFolderLease) async {
        let ref = headsCollection(uid: uid).document(lease.rootId)
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            Firestore.firestore().runTransaction({ transaction, errorPointer -> Any? in
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(ref)
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                let data = snapshot.data() ?? [:]
                guard (data["leaseToken"] as? String) == lease.leaseToken else { return false }
                if let prevRev = lease.previousRevision,
                   let prevId = lease.previousDriveFileId,
                   let prevHash = lease.previousHash {
                    transaction.setData([
                        "state": "COMMITTED",
                        "revision": prevRev,
                        "manifestDriveFileId": prevId,
                        "manifestHash": prevHash,
                        "updatedAt": FieldValue.serverTimestamp(),
                    ], forDocument: ref, merge: true)
                } else {
                    transaction.deleteDocument(ref)
                }
                return true
            }, completion: { _, _ in continuation.resume() })
        }
    }

    func deleteHead(uid: String, rootId: String) async {
        try? await headsCollection(uid: uid).document(rootId).delete()
    }
    #else
    func getHead(uid: String, rootId: String) async throws -> CloudFolderHead? { throw CloudFolderHeadError.unavailable }
    func bootstrapHead(uid: String, rootId: String, driveFileId: String, revision: Int64, hash: String) async throws -> Bool { throw CloudFolderHeadError.unavailable }
    func reserve(uid: String, rootId: String, expectedRevision: Int64?, revision: Int64, deviceId: String) async throws -> CloudFolderLease { throw CloudFolderHeadError.unavailable }
    func commit(uid: String, lease: CloudFolderLease, driveFileId: String, hash: String) async throws { throw CloudFolderHeadError.unavailable }
    func release(uid: String, lease: CloudFolderLease) async {}
    func deleteHead(uid: String, rootId: String) async {}
    #endif
}
