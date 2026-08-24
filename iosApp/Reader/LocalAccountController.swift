import AuthenticationServices
import Combine
import CryptoKit
import Foundation
import os
import ReaderShared
import Security
import UIKit

#if canImport(FirebaseAuth)
import FirebaseAuth
#endif

#if canImport(FirebaseCore)
import FirebaseCore
#endif

#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Native authentication boundary for the shared iOS UI.
///
/// Firebase owns the Episteme account. Google Sign-In separately requests
/// `drive.appdata`, because a Firebase Google credential alone cannot authorize
/// Google Drive sync.
enum LocalCloudDataClearResult {
    case confirmationRequired
    case authorizationRequired
    case inProgress
    case unavailable
    case cleared(deletedDriveFileCount: Int, localCleanupInvoked: Bool)
    case failed(String)
}

@MainActor
final class LocalAccountController: NSObject, ObservableObject {
    private enum Provider {
        static let apple = "APPLE"
        static let google = "GOOGLE"
    }

    private static let googleDriveScope = "https://www.googleapis.com/auth/drive.appdata"
    private static let syncDeviceIDKey = "reader.ios.cloudSyncDeviceId.v1"
    private static let cloudSyncOutboxKey = "reader.ios.cloudSyncOutbox.v1"
    private static let cloudShelfObservationsKey = "reader.ios.cloudShelfObservations.v1"
    private static let cloudFontObservationsKey = "reader.ios.cloudFontObservations.v1"
    private static let cloudSyncRetryBaseDelay: TimeInterval = 5
    private static let cloudSyncRetryMaxDelay: TimeInterval = 15 * 60

    private let syncLogger = Logger(subsystem: "com.aryan.reader", category: "CloudSync")
    private var cloudSyncRetryTask: Task<Void, Never>?
    private var cloudSyncInFlight = false
    private var cloudSyncGeneration = 0
    private var cloudDataClearInFlight = false
    private var localCloudDataClearHandler: (() -> Void)?

    private weak var bridge: ReaderIosBridge?
    private var appleNonce: String?
    private var googleDriveAuthorized = false
    private var lastRegisteredDeviceRows: [[String: Any]] = []

#if canImport(FirebaseAuth)
    private var authStateHandle: AuthStateDidChangeListenerHandle?
#endif

    private enum CloudSyncOperation: String, Codable {
        case pull
        case push
    }

    private struct CloudSyncOutboxItem: Codable {
        let snapshotJSON: String
        let operation: CloudSyncOperation
        var attempt: Int
        var nextAttemptAt: Date
    }

    private struct CloudShelfObservation: Codable {
        var name: String
        var modifiedAt: Int64
        var bookIDs: [String]
        var isDeleted: Bool
    }

    private struct CloudFontObservation: Codable {
        var displayName: String
        var fileName: String
        var fileExtension: String
        var timestamp: Int64
        var isDeleted: Bool
    }

    /// A PDF sidecar is transported as an appDataFolder file, while this
    /// compact representation is carried through the shared snapshot merge
    /// boundary.  The timestamp is the Drive modified time when available;
    /// the payload also carries its own reader-state clock for conflict
    /// resolution.
    private struct CloudPdfSidecar {
        let bookId: String
        let timestamp: Int64
        let data: String
    }

    private struct DownloadedCloudPdfSidecar {
        let bookId: String
        let timestamp: Int64
        let bytes: Int
        let data: String
    }

    func attach(to bridge: ReaderIosBridge) {
        guard self.bridge !== bridge else { return }
        detachAuthObserver()
        self.bridge = bridge
        bridge.setAuthHandlers(
            authenticate: { [weak self] provider in
                Task { @MainActor in await self?.authenticate(provider: provider) }
            },
            signOut: { [weak self] in
                self?.signOut()
            }
        )
        bridge.setCloudSyncHandlers(
            sync: { [weak self] snapshotJSON in
                Task { @MainActor in await self?.syncCloudSnapshot(localJSON: snapshotJSON) }
            },
            upload: { [weak self] snapshotJSON in
                Task { @MainActor in await self?.uploadMergedCloudSnapshot(snapshotJSON) }
            }
        )
        let currentDeviceID = cloudSyncDeviceID()
        bridge.setDeviceManagementHandlers(
            refresh: { [weak self, weak bridge] in
                Task { @MainActor in
                    guard let self, let bridge else { return }
                    let devices = await self.registeredDevices()
                    self.lastRegisteredDeviceRows = devices
                    let ids = devices.compactMap { $0["deviceId"] as? String }
                    let names = devices.map { $0["deviceName"] as? String ?? "" }
                    let lastSeen = devices.map {
                        String(Int64(($0["lastSeenEpochMillis"] as? NSNumber)?.doubleValue ?? 0))
                    }
                    bridge.updateRegisteredDevices(
                        deviceIds: ids,
                        deviceNames: names,
                        lastSeenEpochMillis: lastSeen,
                        status: devices.isEmpty ? "device_empty" : nil
                    )
                }
            },
            revoke: { [weak self, weak bridge] deviceID in
                Task { @MainActor in
                    guard let self, let bridge else { return }
                    if deviceID == currentDeviceID {
                        let devices = self.lastRegisteredDeviceRows
                        bridge.updateRegisteredDevices(
                            deviceIds: devices.compactMap { $0["deviceId"] as? String },
                            deviceNames: devices.map { $0["deviceName"] as? String ?? "" },
                            lastSeenEpochMillis: devices.map {
                                String(Int64(($0["lastSeenEpochMillis"] as? NSNumber)?.doubleValue ?? 0))
                            },
                            status: "device_active"
                        )
                        return
                    }
                    let revoked = await self.revokeDevice(deviceID: deviceID)
                    if revoked {
                        let devices = await self.registeredDevices()
                        self.lastRegisteredDeviceRows = devices
                        bridge.updateRegisteredDevices(
                            deviceIds: devices.compactMap { $0["deviceId"] as? String },
                            deviceNames: devices.map { $0["deviceName"] as? String ?? "" },
                            lastSeenEpochMillis: devices.map {
                                String(Int64(($0["lastSeenEpochMillis"] as? NSNumber)?.doubleValue ?? 0))
                            },
                            status: "device_revoked"
                        )
                    } else {
                        let devices = self.lastRegisteredDeviceRows
                        bridge.updateRegisteredDevices(
                            deviceIds: devices.compactMap { $0["deviceId"] as? String },
                            deviceNames: devices.map { $0["deviceName"] as? String ?? "" },
                            lastSeenEpochMillis: devices.map {
                                String(Int64(($0["lastSeenEpochMillis"] as? NSNumber)?.doubleValue ?? 0))
                            },
                            status: "device_revoke_failed"
                        )
                    }
                }
            }
        )
        bridge.setCloudLocalDataClearHandler { [weak self, weak bridge] in
            Task { @MainActor in
                guard let self, let bridge else { return }
                let result = await self.clearCloudAndLocalData(confirmedByUser: true)
                switch result {
                case .confirmationRequired:
                    bridge.completeCloudLocalDataClear(
                        success: false,
                        message: "clear_confirmation_required"
                    )
                case .authorizationRequired:
                    bridge.completeCloudLocalDataClear(
                        success: false,
                        message: "clear_authorization_required"
                    )
                case .inProgress:
                    bridge.completeCloudLocalDataClear(
                        success: false,
                        message: "clear_in_progress"
                    )
                case .unavailable:
                    bridge.completeCloudLocalDataClear(
                        success: false,
                        message: "clear_unavailable"
                    )
                case .cleared(let deletedDriveFileCount, let localCleanupInvoked):
                    bridge.completeCloudLocalDataClear(
                        success: localCleanupInvoked,
                        message: localCleanupInvoked
                            ? "clear_cleared|\(deletedDriveFileCount)"
                            : "clear_local_cleanup_unavailable"
                    )
                case .failed(let message):
                    bridge.completeCloudLocalDataClear(
                        success: false,
                        message: "clear_failed|\(message)"
                    )
                }
            }
        }
        observeAccount()
        if !cloudSyncInFlight {
            scheduleCloudSyncRetryIfNeeded()
        }
    }

    func handleOpenURL(_ url: URL) -> Bool {
#if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
#else
        return false
#endif
    }

    /// Installs the app-boundary cleanup hook for the confirmation-gated
    /// clear-data action. The hook owns iOS library stores, imported content,
    /// reader sessions, and local preferences; this controller owns the
    /// account-scoped Drive/Firestore deletion. Keeping this boundary explicit
    /// prevents a cloud retry from deleting local data twice.
    func setLocalCloudDataClearHandler(_ handler: @escaping () -> Void) {
        localCloudDataClearHandler = handler
    }

    /// Clears the same account data as Android's destructive cloud/local
    /// action. Callers must obtain an explicit user confirmation first. Cloud
    /// deletion completes before the local hook runs, so a transient network
    /// failure never silently destroys the only local copy.
    func clearCloudAndLocalData(confirmedByUser: Bool) async -> LocalCloudDataClearResult {
        guard confirmedByUser else { return .confirmationRequired }
        guard !cloudDataClearInFlight, !cloudSyncInFlight else { return .inProgress }
#if canImport(FirebaseAuth) && canImport(FirebaseCore) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
        guard
            FirebaseApp.app() != nil,
            let uid = Auth.auth().currentUser?.uid
        else {
            return .authorizationRequired
        }
        guard googleDriveAuthorized else { return .authorizationRequired }

        cloudDataClearInFlight = true
        cloudSyncGeneration += 1
        let generation = cloudSyncGeneration
        clearCloudSyncOutbox()
        defer { cloudDataClearInFlight = false }

        do {
            let accessToken = try await googleDriveAccessToken()
            let driveFiles = try await listDriveFiles(accessToken: accessToken)
            for file in driveFiles {
                try await deleteDriveFile(fileID: file.id, accessToken: accessToken)
            }
            guard generation == cloudSyncGeneration, Auth.auth().currentUser?.uid == uid else {
                return .authorizationRequired
            }
            try await deleteCloudFirestoreData(uid: uid)
            let localCleanupInvoked = localCloudDataClearHandler != nil
            if let localCloudDataClearHandler {
                localCloudDataClearHandler()
            } else {
                syncLogger.error("cloud_sync.clear_local_hook_missing")
            }
            syncLogger.info(
                "cloud_sync.clear_success driveFiles=\(driveFiles.count) localCleanupInvoked=\(localCleanupInvoked)"
            )
            return .cleared(
                deletedDriveFileCount: driveFiles.count,
                localCleanupInvoked: localCleanupInvoked
            )
        } catch {
            syncLogger.error(
                "cloud_sync.clear_failed error=\(error.localizedDescription, privacy: .public)"
            )
            return .failed(error.localizedDescription)
        }
#else
        return .unavailable
#endif
    }

    /// Android uses this name for the same destructive settings action. Keep
    /// it as the app-facing alias while retaining the explicit confirmation
    /// argument at the native boundary.
    func deleteAllCloudAndLocalData(confirmedByUser: Bool) async -> LocalCloudDataClearResult {
        await clearCloudAndLocalData(confirmedByUser: confirmedByUser)
    }

    private func authenticate(provider: String) async {
        switch provider {
        case Provider.apple:
            beginAppleSignIn()
        case Provider.google:
            await beginGoogleSignIn()
        default:
            publish(status: "Unsupported sign-in provider.")
        }
    }

    private func beginAppleSignIn() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publish(status: "GoogleService-Info.plist is missing from the iOS target.")
            return
        }
        let nonce = Self.randomNonce()
        appleNonce = nonce
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
#else
        publish(status: "Apple login is ready for Firebase, but FirebaseAuth is not added to the iOS target yet.")
#endif
    }

    private func beginGoogleSignIn() async {
#if canImport(FirebaseAuth) && canImport(FirebaseCore) && canImport(GoogleSignIn)
        guard FirebaseApp.app() != nil else {
            publish(status: "GoogleService-Info.plist is missing from the iOS target.")
            return
        }
        guard let presenter = Self.presentingViewController() else {
            publish(status: "Could not present Google sign-in.")
            return
        }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter,
                hint: nil,
                additionalScopes: [Self.googleDriveScope]
            )
            guard let idToken = result.user.idToken?.tokenString else {
                publish(status: "Google did not return an ID token.")
                return
            }
            googleDriveAuthorized = result.user.grantedScopes?.contains(Self.googleDriveScope) == true
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            try await signInOrLink(
                credential: credential,
                providerID: "google.com",
                providerLabel: "Google"
            )
        } catch {
            publish(status: Self.userFacingAuthError(error, provider: "Google"))
        }
#else
        publish(status: "Google login needs FirebaseAuth, GoogleSignIn, and GoogleService-Info.plist in the iOS target.")
#endif
    }

#if canImport(FirebaseAuth)
    private func signInOrLink(
        credential: AuthCredential,
        providerID: String,
        providerLabel: String
    ) async throws {
        let auth = Auth.auth()
        if let user = auth.currentUser {
            if user.providerData.contains(where: { $0.providerID == providerID }) {
                try await user.reauthenticate(with: credential)
                publish(status: "\(providerLabel) authorization refreshed.")
                return
            }
            do {
                try await user.link(with: credential)
                publish(status: "\(providerLabel) linked.")
            } catch {
                let code = AuthErrorCode(_bridgedNSError: error as NSError)
                if code == .credentialAlreadyInUse || code == .accountExistsWithDifferentCredential {
                    publish(
                        status: "\(providerLabel) belongs to another Episteme account. Nothing was changed; secure account merge is required."
                    )
                    return
                }
                throw error
            }
        } else {
            try await auth.signIn(with: credential)
            publish(status: "Signed in with \(providerLabel).")
        }
    }
#endif

    private func observeAccount() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publish(status: "Add GoogleService-Info.plist to enable Apple and Google login.")
            return
        }
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in self?.publish(status: nil) }
        }
        restoreGoogleDriveAuthorization()
#else
        publish(status: "Add the iOS Firebase configuration to enable Apple and Google login.")
#endif
    }

    private func restoreGoogleDriveAuthorization() {
#if canImport(GoogleSignIn)
        Task {
            do {
                let user = try await GIDSignIn.sharedInstance.restorePreviousSignIn()
                googleDriveAuthorized = user.grantedScopes?.contains(Self.googleDriveScope) == true
                publish(status: nil)
            } catch {
                googleDriveAuthorized = false
                publish(status: nil)
            }
        }
#endif
    }

    private func signOut() {
        cloudSyncGeneration += 1
        clearCloudSyncOutbox()
        UserDefaults.standard.removeObject(forKey: Self.cloudShelfObservationsKey)
        UserDefaults.standard.removeObject(forKey: Self.cloudFontObservationsKey)
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        if let uid = Auth.auth().currentUser?.uid {
            Task { @MainActor in
                await self.unregisterDevice(uid: uid)
            }
        }
#endif
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            googleDriveAuthorized = false
            publish(status: "Signed out.")
            return
        }
        do {
            try Auth.auth().signOut()
#if canImport(GoogleSignIn)
            GIDSignIn.sharedInstance.signOut()
#endif
            googleDriveAuthorized = false
            publish(status: "Signed out.")
        } catch {
            publish(status: "Sign out failed: \(error.localizedDescription)")
        }
#else
        googleDriveAuthorized = false
        publish(status: "Signed out.")
#endif
    }

    private func scheduleCloudSyncRetryIfNeeded() {
        cloudSyncRetryTask?.cancel()
        guard loadCloudSyncOutbox() != nil else { return }
        cloudSyncRetryTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                // The outbox can be replaced while this task is asleep (for example, a
                // newer local edit arriving during an in-flight sync). Always reload it
                // before dispatching so a stale snapshot is never replayed.
                guard let item = self.loadCloudSyncOutbox() else { return }
                let delay = max(0, item.nextAttemptAt.timeIntervalSinceNow)
                if delay > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                    continue
                }
                guard !Task.isCancelled else { return }
                switch item.operation {
                case .pull:
                    await self.syncCloudSnapshot(localJSON: item.snapshotJSON)
                case .push:
                    await self.uploadMergedCloudSnapshot(item.snapshotJSON)
                }
                return
            }
        }
    }

    private func loadCloudSyncOutbox() -> CloudSyncOutboxItem? {
        guard let data = UserDefaults.standard.data(forKey: Self.cloudSyncOutboxKey) else {
            return nil
        }
        return try? JSONDecoder().decode(CloudSyncOutboxItem.self, from: data)
    }

    private func saveCloudSyncOutbox(
        snapshotJSON: String,
        operation: CloudSyncOperation,
        previousAttempt: Int
    ) {
        let attempt = min(previousAttempt + 1, 12)
        let delay = min(
            Self.cloudSyncRetryMaxDelay,
            Self.cloudSyncRetryBaseDelay * pow(2, Double(max(0, attempt - 1)))
        )
        let item = CloudSyncOutboxItem(
            snapshotJSON: snapshotJSON,
            operation: operation,
            attempt: attempt,
            nextAttemptAt: Date().addingTimeInterval(delay)
        )
        if let data = try? JSONEncoder().encode(item) {
            UserDefaults.standard.set(data, forKey: Self.cloudSyncOutboxKey)
        }
        syncLogger.error(
            "cloud_sync.outbox_saved operation=\(operation.rawValue, privacy: .public) attempt=\(attempt) bytes=\(snapshotJSON.utf8.count) retrySeconds=\(Int(delay))"
        )
        if !cloudSyncInFlight {
            scheduleCloudSyncRetryIfNeeded()
        }
    }

    private func clearCloudSyncOutbox() {
        UserDefaults.standard.removeObject(forKey: Self.cloudSyncOutboxKey)
        cloudSyncRetryTask?.cancel()
        cloudSyncRetryTask = nil
    }

    /// iOS keeps shelf state in the shared snapshot rather than Room. Track a
    /// compact local observation so renames, membership edits, and deletions
    /// still get Android-compatible clocks/tombstones on the next sync.
    private func snapshotWithCloudShelfClocks(_ snapshotJSON: String) -> String {
        guard
            let data = snapshotJSON.data(using: .utf8),
            var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
            let shelves = root["shelves"] as? [[String: Any]]
        else { return snapshotJSON }

        let previous: [String: CloudShelfObservation] = {
            guard let stored = UserDefaults.standard.data(forKey: Self.cloudShelfObservationsKey) else {
                return [:]
            }
            return (try? JSONDecoder().decode([String: CloudShelfObservation].self, from: stored)) ?? [:]
        }()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let refs = root["bookShelfRefs"] as? [[String: Any]] ?? []
        let bookIDsByShelf = Dictionary(grouping: refs) { $0["shelfId"] as? String ?? "" }
            .mapValues { values in
                values.compactMap { $0["bookId"] as? String }.sorted()
            }
        var current = previous
        var activeIDs = Set<String>()
        var preparedShelves: [[String: Any]] = []

        for shelf in shelves {
            guard
                let id = shelf["id"] as? String,
                let name = shelf["name"] as? String,
                !id.isEmpty,
                (shelf["isSmart"] as? Bool ?? false) == false
            else {
                preparedShelves.append(shelf)
                continue
            }
            activeIDs.insert(id)
            let deleted = shelf["isDeleted"] as? Bool ?? false
            let previousObservation = previous[id]
            var prepared = shelf
            var clock = numericInt64(shelf["modifiedAt"])
            if clock == 0 { clock = numericInt64(shelf["lastModifiedTimestamp"]) }
            let bookIDs = bookIDsByShelf[id] ?? []
            let changed = previousObservation.map {
                $0.name != name || $0.bookIDs != bookIDs
            } ?? false
            if clock <= 0 {
                clock = if let previousObservation, !previousObservation.isDeleted, !changed, !deleted {
                    previousObservation.modifiedAt
                } else {
                    max(now, (previousObservation?.modifiedAt ?? 0) + 1)
                }
            } else if !deleted && changed && clock <= (previousObservation?.modifiedAt ?? 0) {
                clock = max(now, (previousObservation?.modifiedAt ?? 0) + 1)
            }
            prepared["modifiedAt"] = clock
            prepared["isDeleted"] = deleted
            preparedShelves.append(prepared)
            current[id] = CloudShelfObservation(
                name: name,
                modifiedAt: clock,
                bookIDs: bookIDs,
                isDeleted: deleted
            )
        }

        // A shelf removed from the local shared state is retained as a
        // tombstone. This mirrors Android's Room `isDeleted` row and prevents
        // another device's older copy from resurrecting it.
        for (id, observation) in previous where !activeIDs.contains(id) {
            let deletedAt = observation.isDeleted
                ? observation.modifiedAt
                : max(now, observation.modifiedAt + 1)
            preparedShelves.append([
                "id": id,
                "name": observation.name,
                "isSmart": false,
                "modifiedAt": deletedAt,
                "isDeleted": true,
            ])
            current[id] = CloudShelfObservation(
                name: observation.name,
                modifiedAt: deletedAt,
                bookIDs: observation.bookIDs,
                isDeleted: true
            )
        }

        root["shelves"] = preparedShelves
        if let encoded = try? JSONEncoder().encode(current) {
            UserDefaults.standard.set(encoded, forKey: Self.cloudShelfObservationsKey)
        }
        guard let encodedRoot = try? JSONSerialization.data(withJSONObject: root) else {
            return snapshotJSON
        }
        return String(data: encodedRoot, encoding: .utf8) ?? snapshotJSON
    }

    private func snapshotWithCloudFontTombstones(_ snapshotJSON: String) -> String {
        guard
            let data = snapshotJSON.data(using: .utf8),
            var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
            let fonts = root["customFonts"] as? [[String: Any]]
        else { return snapshotJSON }

        let previous: [String: CloudFontObservation] = {
            guard let stored = UserDefaults.standard.data(forKey: Self.cloudFontObservationsKey) else {
                return [:]
            }
            return (try? JSONDecoder().decode([String: CloudFontObservation].self, from: stored)) ?? [:]
        }()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var current = previous
        var activeIDs = Set<String>()
        var preparedFonts: [[String: Any]] = []

        for font in fonts {
            guard
                let id = font["id"] as? String,
                let displayName = font["displayName"] as? String,
                let fileName = font["fileName"] as? String,
                !id.isEmpty,
                !fileName.isEmpty
            else {
                preparedFonts.append(font)
                continue
            }
            activeIDs.insert(id)
            let deleted = font["isDeleted"] as? Bool ?? false
            let previousObservation = previous[id]
            var prepared = font
            var clock = numericInt64(font["timestamp"])
            if clock <= 0 {
                clock = max(now, (previousObservation?.timestamp ?? 0) + 1)
            }
            prepared["timestamp"] = clock
            prepared["isDeleted"] = deleted
            prepared["path"] = font["path"] as? String ?? ""
            preparedFonts.append(prepared)
            current[id] = CloudFontObservation(
                displayName: displayName,
                fileName: fileName,
                fileExtension: font["fileExtension"] as? String ?? URL(fileURLWithPath: fileName).pathExtension,
                timestamp: clock,
                isDeleted: deleted
            )
        }

        // Font deletion is represented as a Firestore tombstone until the
        // remote metadata and Drive content have both observed it.
        for (id, observation) in previous where !activeIDs.contains(id) {
            let deletedAt = observation.isDeleted
                ? observation.timestamp
                : max(now, observation.timestamp + 1)
            preparedFonts.append([
                "id": id,
                "displayName": observation.displayName,
                "fileName": observation.fileName,
                "fileExtension": observation.fileExtension,
                "timestamp": deletedAt,
                "isDeleted": true,
                "path": "",
            ])
            current[id] = CloudFontObservation(
                displayName: observation.displayName,
                fileName: observation.fileName,
                fileExtension: observation.fileExtension,
                timestamp: deletedAt,
                isDeleted: true
            )
        }

        root["customFonts"] = preparedFonts
        if let encoded = try? JSONEncoder().encode(current) {
            UserDefaults.standard.set(encoded, forKey: Self.cloudFontObservationsKey)
        }
        guard let encodedRoot = try? JSONSerialization.data(withJSONObject: root) else {
            return snapshotJSON
        }
        return String(data: encodedRoot, encoding: .utf8) ?? snapshotJSON
    }

    private func preparedCloudSnapshot(_ snapshotJSON: String) -> String {
        snapshotWithLocalPdfSidecars(
            snapshotWithCloudFontTombstones(snapshotWithCloudShelfClocks(snapshotJSON))
        )
    }

    /// Include the durable sidecar mirror even while the shared reader is
    /// still restoring its in-memory snapshot.  Once ReaderIosApp starts
    /// carrying `pdfSidecars` itself this remains a compatibility fallback;
    /// the two payloads are merged by their shared codec before transport.
    private func snapshotWithLocalPdfSidecars(_ snapshotJSON: String) -> String {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return snapshotJSON }

        let pdfBookIDs = Set<String>(
            (root["books"] as? [[String: Any]] ?? []).compactMap { book -> String? in
                guard
                    let id = book["id"] as? String,
                    (book["type"] as? String)?.uppercased() == "PDF",
                    (book["sourceFolder"] is NSNull) || book["sourceFolder"] == nil,
                    (book["path"] as? String)?.hasPrefix("opds-pse://") != true
                else { return nil }
                return id
            }
        )
        guard !pdfBookIDs.isEmpty else { return snapshotJSON }

        var sidecars = Dictionary(
            uniqueKeysWithValues: parseCloudPdfSidecars(snapshotJSON).map { ($0.bookId, $0) }
        )
        for bookID in pdfBookIDs {
            guard let local = localPdfSidecar(bookId: bookID) else { continue }
            if let snapshotSidecar = sidecars[bookID] {
                let preferSnapshot = snapshotSidecar.timestamp >= local.timestamp
                let mergedData = mergePdfSidecarData(
                    localData: local.data,
                    remoteData: snapshotSidecar.data,
                    preferRemoteOnConflict: preferSnapshot
                )
                sidecars[bookID] = CloudPdfSidecar(
                    bookId: bookID,
                    timestamp: max(local.timestamp, snapshotSidecar.timestamp),
                    data: mergedData
                )
            } else {
                sidecars[bookID] = local
            }
        }
        guard !sidecars.isEmpty else { return snapshotJSON }
        return injectPdfSidecars(
            snapshotJSON,
            sidecars: sidecars.values.sorted { $0.bookId < $1.bookId }
        )
    }

    private func parseCloudPdfSidecars(_ snapshotJSON: String) -> [CloudPdfSidecar] {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
            let rawSidecars = root["pdfSidecars"] as? [[String: Any]]
        else { return [] }
        return rawSidecars.compactMap { raw -> CloudPdfSidecar? in
            guard
                let bookId = raw["bookId"] as? String,
                !bookId.isEmpty,
                let sidecarData = raw["data"] as? String,
                !sidecarData.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            else { return nil }
            return CloudPdfSidecar(
                bookId: bookId,
                timestamp: numericInt64(raw["timestamp"]),
                data: sidecarData
            )
        }
    }

    private func localPdfSidecar(bookId: String) -> CloudPdfSidecar? {
        guard !bookId.isEmpty else { return nil }
        let store = IosPdfCloudSidecarStore.shared
        guard
            let rawData = store.read(bookId: bookId),
            !rawData.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            SharedPdfCloudSidecarCodec.shared.isCompatiblePayload(rawDataJson: rawData)
        else { return nil }

        let payload = SharedPdfCloudSidecarCodec.shared.decode(
            rawDataJson: rawData,
            fallbackPageCount: 1,
            fallbackPageIndex: 0
        )
        let fileTimestamp = store.sidecarPath(bookId: bookId)
            .flatMap { path in
                (try? URL(fileURLWithPath: path).resourceValues(forKeys: [.contentModificationDateKey]))?
                    .contentModificationDate?
                    .timeIntervalSince1970
            }
            .map { Int64($0 * 1000) } ?? 0
        return CloudPdfSidecar(
            bookId: bookId,
            timestamp: max(payload?.modifiedTimestamp ?? 0, fileTimestamp),
            data: rawData
        )
    }

    private func mergePdfSidecarData(
        localData: String,
        remoteData: String,
        preferRemoteOnConflict: Bool
    ) -> String {
        SharedPdfCloudSidecarCodec.shared.merge(
            localDataJson: localData,
            remoteDataJson: remoteData,
            preferRemoteOnConflict: preferRemoteOnConflict,
            fallbackPageCount: 1,
            fallbackPageIndex: 0
        )
    }

    private func writePdfSidecar(
        bookId: String,
        data: String,
        modifiedTimestamp: Int64
    ) -> Bool {
        let store = IosPdfCloudSidecarStore.shared
        guard store.write(bookId: bookId, payloadJson: data) else { return false }
        guard modifiedTimestamp > 0,
              let path = store.sidecarPath(bookId: bookId) else { return true }
        try? FileManager.default.setAttributes(
            [.modificationDate: Date(timeIntervalSince1970: Double(modifiedTimestamp) / 1000)],
            ofItemAtPath: path
        )
        return true
    }

    private func injectPdfSidecars(
        _ snapshotJSON: String,
        sidecars: [CloudPdfSidecar]
    ) -> String {
        guard
            let data = snapshotJSON.data(using: .utf8),
            var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return snapshotJSON }
        root["pdfSidecars"] = sidecars
            .sorted { $0.bookId < $1.bookId }
            .map { sidecar in
                [
                    "bookId": sidecar.bookId,
                    "timestamp": sidecar.timestamp,
                    "data": sidecar.data,
                ]
            }
        guard let encoded = try? JSONSerialization.data(withJSONObject: root) else {
            return snapshotJSON
        }
        return String(data: encoded, encoding: .utf8) ?? snapshotJSON
    }

    private func numericInt64(_ value: Any?) -> Int64 {
        (value as? NSNumber)?.int64Value ?? 0
    }

    private func syncCloudSnapshot(localJSON: String) async {
#if canImport(GoogleSignIn)
        guard !cloudDataClearInFlight else {
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud data is being cleared."
            )
            return
        }
        if cloudSyncInFlight {
            let previousAttempt = loadCloudSyncOutbox()?.attempt ?? 0
            saveCloudSyncOutbox(
                snapshotJSON: localJSON,
                operation: .pull,
                previousAttempt: previousAttempt
            )
            return
        }
        let preparedLocalJSON = preparedCloudSnapshot(localJSON)
        let generation = cloudSyncGeneration
        cloudSyncInFlight = true
        defer {
            cloudSyncInFlight = false
            if loadCloudSyncOutbox() != nil {
                scheduleCloudSyncRetryIfNeeded()
            }
        }
        let startedAt = Date()
        do {
            let accessToken = try await googleDriveAccessToken()
            guard generation == cloudSyncGeneration else { return }
            let remoteJSON = try await syncFirestoreBookMetadata(localJSON: preparedLocalJSON)
            try await deleteCloudBookContentsForTombstones(
                snapshotJSON: preparedLocalJSON,
                accessToken: accessToken
            )
            try await deleteCloudPdfSidecarsForTombstones(
                snapshotJSON: preparedLocalJSON,
                accessToken: accessToken
            )
            // A remote tombstone can win even when this device has not yet
            // recorded the deletion locally. Remove its Drive payload before
            // applying the remote snapshot so stale content cannot be
            // resurrected by a later retry.
            try await deleteCloudBookContentsForTombstones(
                snapshotJSON: remoteJSON,
                accessToken: accessToken
            )
            try await deleteCloudPdfSidecarsForTombstones(
                snapshotJSON: remoteJSON,
                accessToken: accessToken
            )
            try await deleteCloudFontContentsForTombstones(
                snapshotJSON: remoteJSON,
                accessToken: accessToken
            )
            let downloaded = try await downloadMissingCloudBooks(
                localJSON: preparedLocalJSON,
                remoteJSON: remoteJSON,
                accessToken: accessToken
            )
            let downloadedFonts = try await downloadMissingCloudFonts(
                localJSON: preparedLocalJSON,
                remoteJSON: remoteJSON,
                accessToken: accessToken
            )
            let downloadedSidecars = try await downloadMissingCloudPdfSidecars(
                localJSON: preparedLocalJSON,
                remoteJSON: remoteJSON,
                accessToken: accessToken
            )
            guard generation == cloudSyncGeneration else { return }
            let hydratedRemoteJSON = injectPdfSidecars(
                injectDownloadedFontPaths(
                    remoteJSON,
                    pathsByFontID: downloadedFonts
                ),
                sidecars: downloadedSidecars.map {
                    CloudPdfSidecar(bookId: $0.bookId, timestamp: $0.timestamp, data: $0.data)
                }
            )
            clearCloudSyncOutbox()
            syncLogger.info(
                "cloud_sync.pull_success bytes=\(preparedLocalJSON.utf8.count) remoteBytes=\(hydratedRemoteJSON.utf8.count) books=\(downloaded.count) fonts=\(downloadedFonts.count) pdfSidecars=\(downloadedSidecars.count) elapsedMs=\(Int(Date().timeIntervalSince(startedAt) * 1000))"
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: hydratedRemoteJSON,
                downloadedBookIds: downloaded.map(\.id),
                downloadedBookPaths: downloaded.map(\.path),
                status: downloaded.isEmpty
                    ? "Cloud reading progress downloaded."
                    : "Downloaded \(downloaded.count) cloud book(s)."
            )
        } catch {
            guard generation == cloudSyncGeneration else { return }
            let previousAttempt = loadCloudSyncOutbox()?.attempt ?? 0
            saveCloudSyncOutbox(
                snapshotJSON: preparedLocalJSON,
                operation: .pull,
                previousAttempt: previousAttempt
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud sync failed: \(error.localizedDescription)"
            )
        }
#else
        bridge?.completeCloudSync(
            remoteSnapshotJson: nil,
            downloadedBookIds: [],
            downloadedBookPaths: [],
            status: "Google Sign-In is required for cloud sync."
        )
#endif
    }

    private func uploadMergedCloudSnapshot(_ snapshotJSON: String) async {
#if canImport(GoogleSignIn)
        guard !cloudDataClearInFlight else {
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud data is being cleared."
            )
            return
        }
        if cloudSyncInFlight {
            let previousAttempt = loadCloudSyncOutbox()?.attempt ?? 0
            saveCloudSyncOutbox(
                snapshotJSON: snapshotJSON,
                operation: .push,
                previousAttempt: previousAttempt
            )
            return
        }
        let preparedSnapshotJSON = preparedCloudSnapshot(snapshotJSON)
        let generation = cloudSyncGeneration
        cloudSyncInFlight = true
        defer {
            cloudSyncInFlight = false
            if loadCloudSyncOutbox() != nil {
                scheduleCloudSyncRetryIfNeeded()
            }
        }
        let startedAt = Date()
        do {
            let accessToken = try await googleDriveAccessToken()
            guard generation == cloudSyncGeneration else { return }
            let uploadedSidecars = try await uploadCloudPdfSidecars(
                snapshotJSON: preparedSnapshotJSON,
                accessToken: accessToken
            )
            try await uploadCloudBookContents(
                snapshotJSON: preparedSnapshotJSON,
                accessToken: accessToken
            )
            try await uploadCloudFontContents(
                snapshotJSON: preparedSnapshotJSON,
                accessToken: accessToken
            )
            _ = try await syncFirestoreBookMetadata(localJSON: preparedSnapshotJSON)
            try await deleteCloudBookContentsForTombstones(
                snapshotJSON: preparedSnapshotJSON,
                accessToken: accessToken
            )
            try await deleteCloudPdfSidecarsForTombstones(
                snapshotJSON: preparedSnapshotJSON,
                accessToken: accessToken
            )
            guard generation == cloudSyncGeneration else { return }
            clearCloudSyncOutbox()
            syncLogger.info(
                "cloud_sync.push_success bytes=\(preparedSnapshotJSON.utf8.count) pdfSidecars=\(uploadedSidecars.count) elapsedMs=\(Int(Date().timeIntervalSince(startedAt) * 1000))"
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud reading progress is up to date."
            )
        } catch {
            guard generation == cloudSyncGeneration else { return }
            let previousAttempt = loadCloudSyncOutbox()?.attempt ?? 0
            saveCloudSyncOutbox(
                snapshotJSON: preparedSnapshotJSON,
                operation: .push,
                previousAttempt: previousAttempt
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud upload failed: \(error.localizedDescription)"
            )
        }
#endif
    }

    /// The stable installation identifier is also needed by the settings
    /// bridge when Firebase modules are unavailable in a target.
    private func cloudSyncDeviceID() -> String {
        if let existing = UserDefaults.standard.string(forKey: Self.syncDeviceIDKey) {
            return existing
        }
        let value = "ios-\(UUID().uuidString.lowercased())"
        UserDefaults.standard.set(value, forKey: Self.syncDeviceIDKey)
        return value
    }

#if canImport(FirebaseFirestore) && canImport(FirebaseAuth)
    private func syncFirestoreBookMetadata(localJSON: String) async throws -> String {
        guard let uid = Auth.auth().currentUser?.uid else {
            throw CloudSyncError.firebaseAccountRequired
        }
        do {
            try await registerCurrentDevice(uid: uid)
        } catch {
            syncLogger.error("cloud_sync.device_register_failed error=\(error.localizedDescription, privacy: .public)")
        }
        guard
            let data = localJSON.data(using: .utf8),
            var localRoot = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let localBooks = localRoot["books"] as? [[String: Any]]
        else {
            throw CloudSyncError.invalidSnapshot
        }

        let localShelfRecords = localRoot["shelves"] as? [[String: Any]] ?? []
        let localShelfRefs = localRoot["bookShelfRefs"] as? [[String: Any]] ?? []
        let localFonts = localRoot["customFonts"] as? [[String: Any]] ?? []

        let collection = Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("books")
        let query = try await collection.getDocuments()
        let remoteDocuments = Dictionary(
            uniqueKeysWithValues: query.documents.map { ($0.documentID, $0.data()) }
        )
        var effectiveDocuments = remoteDocuments
        let localTombstones = localRoot["bookTombstones"] as? [[String: Any]] ?? []
        let localByID = Dictionary(
            uniqueKeysWithValues: localBooks.compactMap { book -> (String, [String: Any])? in
                guard let id = book["id"] as? String else { return nil }
                return (id, book)
            }
        )

        for (bookID, localBook) in localByID {
            guard cloudBookIsFirestoreSyncable(localBook) else { continue }
            let localFields = firestoreFields(from: localBook)
            let localTimestamp = numericInt64(localFields["lastModifiedTimestamp"])
            let remoteTimestamp = numericInt64(remoteDocuments[bookID]?["lastModifiedTimestamp"])
            let localDeleteTimestamp = localTombstones
                .filter { $0["bookId"] as? String == bookID }
                .map { numericInt64($0["deletedAt"]) }
                .max() ?? 0
            guard localTimestamp > localDeleteTimestamp else { continue }
            if remoteDocuments[bookID] == nil || localTimestamp > remoteTimestamp {
                try await collection.document(bookID).setData(localFields)
                effectiveDocuments[bookID] = localFields
            }
        }

        for tombstone in localTombstones {
            guard let bookID = tombstone["bookId"] as? String else { continue }
            let deletedAt = numericInt64(tombstone["deletedAt"])
            let remoteTimestamp = numericInt64(effectiveDocuments[bookID]?["lastModifiedTimestamp"])
            guard effectiveDocuments[bookID] == nil || deletedAt > remoteTimestamp else { continue }
            let fields: [String: Any] = [
                "bookId": bookID,
                "type": tombstone["type"] as? String ?? "",
                "isDeleted": true,
                "lastModifiedTimestamp": deletedAt,
                "readingPositionModifiedTimestamp": 0,
                "annotationModifiedTimestamp": 0,
                "originDeviceId": cloudSyncDeviceID(),
            ]
            try await collection.document(bookID).setData(fields, merge: true)
            effectiveDocuments[bookID] = (effectiveDocuments[bookID] ?? [:])
                .merging(fields) { _, new in new }
        }

        let shelfMerge = try await syncFirestoreShelves(
            uid: uid,
            localRecords: localShelfRecords,
            localRefs: localShelfRefs,
            syncableBookIDs: Set(localByID.keys).union(effectiveDocuments.keys)
        )
        let mergedFonts = try await syncFirestoreFonts(
            uid: uid,
            localFonts: localFonts
        )

        let remoteBooks = effectiveDocuments.values.compactMap { fields -> [String: Any]? in
            guard (fields["isDeleted"] as? Bool) != true else { return nil }
            return snapshotBook(fromFirestore: fields)
        }
        localRoot["books"] = remoteBooks
        localRoot["shelves"] = shelfMerge.records
        localRoot["bookShelfRefs"] = shelfMerge.refs
        localRoot["customFonts"] = mergedFonts
        // PDF sidecar bytes are transported through Drive, not Firestore.
        // Do not echo the local snapshot's sidecars as if they were remote;
        // the Drive phase injects only the remote files that it observed.
        localRoot["pdfSidecars"] = []
        localRoot["bookTombstones"] = effectiveDocuments.values.compactMap { fields -> [String: Any]? in
            guard
                (fields["isDeleted"] as? Bool) == true,
                let bookID = fields["bookId"] as? String
            else {
                return nil
            }
            return [
                "bookId": bookID,
                "type": nullableJSONValue(fields["type"]),
                "deletedAt": numericInt64(fields["lastModifiedTimestamp"]),
            ]
        }
        let remoteData = try JSONSerialization.data(withJSONObject: localRoot)
        guard let remoteJSON = String(data: remoteData, encoding: .utf8) else {
            throw CloudSyncError.invalidSnapshot
        }
        return remoteJSON
    }

    private struct ShelfMergeResult {
        let records: [[String: Any]]
        let refs: [[String: Any]]
    }

    private func syncFirestoreShelves(
        uid: String,
        localRecords: [[String: Any]],
        localRefs: [[String: Any]],
        syncableBookIDs: Set<String>
    ) async throws -> ShelfMergeResult {
        let collection = Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("shelves")
        let query = try await collection.getDocuments()
        var effective: [String: [String: Any]] = [:]
        for document in query.documents {
            var fields = document.data()
            if fields["shelfId"] == nil { fields["shelfId"] = document.documentID }
            guard let shelfID = fields["shelfId"] as? String, !shelfID.isEmpty else { continue }
            let currentClock = numericInt64(effective[shelfID]?["lastModifiedTimestamp"])
            let candidateClock = numericInt64(fields["lastModifiedTimestamp"])
            if effective[shelfID] == nil || candidateClock >= currentClock {
                effective[shelfID] = fields
            }
        }
        let localRefsByShelf = Dictionary(grouping: localRefs) {
            $0["shelfId"] as? String ?? ""
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        for local in localRecords {
            guard
                let shelfID = local["id"] as? String,
                !(local["isSmart"] as? Bool ?? false),
                !shelfID.isEmpty
            else { continue }
            let refs = localRefsByShelf[shelfID] ?? []
            let bookIDs = refs.compactMap { $0["bookId"] as? String }
                .filter { syncableBookIDs.contains($0) }
            let localClock = numericInt64(local["modifiedAt"])
            let remoteClock = numericInt64(effective[shelfID]?["lastModifiedTimestamp"])
            let isDeleted = local["isDeleted"] as? Bool ?? false
            let shouldUpload = effective[shelfID] == nil ||
                (localClock > 0 && localClock > remoteClock)
            guard shouldUpload else { continue }
            let clock = localClock > 0 ? localClock : max(now, remoteClock + 1)
            let fields: [String: Any] = [
                "shelfId": shelfID,
                "name": local["name"] as? String ?? shelfID,
                "bookIds": bookIDs,
                "lastModifiedTimestamp": clock,
                "isDeleted": isDeleted,
                "originDeviceId": cloudSyncDeviceID(),
            ]
            try await collection.document(shelfID).setData(fields, merge: false)
            effective[shelfID] = fields
        }

        var records: [[String: Any]] = []
        var refs: [[String: Any]] = []
        for fields in effective.values {
            guard let shelfID = fields["shelfId"] as? String, !shelfID.isEmpty else { continue }
            let isDeleted = fields["isDeleted"] as? Bool ?? false
            records.append([
                "id": shelfID,
                "name": fields["name"] as? String ?? shelfID,
                "isSmart": false,
                "modifiedAt": numericInt64(fields["lastModifiedTimestamp"]),
                "isDeleted": isDeleted,
            ])
            guard !isDeleted else { continue }
            let bookIDs = fields["bookIds"] as? [String] ?? []
            let clock = numericInt64(fields["lastModifiedTimestamp"])
            refs.append(contentsOf: bookIDs.enumerated().map { index, bookID in
                [
                    "bookId": bookID,
                    "shelfId": shelfID,
                    "addedAt": clock > 0 ? clock + Int64(index) : Int64(index),
                ]
            })
        }
        return ShelfMergeResult(
            records: records.sorted { ($0["id"] as? String ?? "") < ($1["id"] as? String ?? "") },
            refs: refs.sorted {
                let left = ($0["shelfId"] as? String ?? "", numericInt64($0["addedAt"]))
                let right = ($1["shelfId"] as? String ?? "", numericInt64($1["addedAt"]))
                return left.0 == right.0 ? left.1 < right.1 : left.0 < right.0
            }
        )
    }

    private func syncFirestoreFonts(
        uid: String,
        localFonts: [[String: Any]]
    ) async throws -> [[String: Any]] {
        let collection = Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("fonts")
        let query = try await collection.getDocuments()
        var effective = Dictionary(uniqueKeysWithValues: query.documents.map { document in
            var fields = document.data()
            if fields["id"] == nil { fields["id"] = document.documentID }
            return (document.documentID, fields)
        })
        for local in localFonts {
            guard let fontID = local["id"] as? String, !fontID.isEmpty else { continue }
            let localTimestamp = numericInt64(local["timestamp"])
            let remoteTimestamp = numericInt64(effective[fontID]?["timestamp"])
            // Font metadata uses the same last-writer-wins clock as Android.
            // This also allows a newer local font to supersede an older remote
            // tombstone instead of getting stuck behind `isDeleted`.
            let shouldUpload = effective[fontID] == nil || localTimestamp > remoteTimestamp
            guard shouldUpload else { continue }
            let clock = localTimestamp > 0
                ? localTimestamp
                : max(Int64(Date().timeIntervalSince1970 * 1000), remoteTimestamp + 1)
            let fields: [String: Any] = [
                "id": fontID,
                "displayName": local["displayName"] as? String ?? fontID,
                "fileName": local["fileName"] as? String ?? "",
                "fileExtension": local["fileExtension"] as? String ?? "",
                "timestamp": clock,
                "isDeleted": local["isDeleted"] as? Bool ?? false,
            ]
            try await collection.document(fontID).setData(fields, merge: false)
            effective[fontID] = fields
        }
        return effective.values.compactMap { fields in
            guard let id = fields["id"] as? String, !id.isEmpty else { return nil }
            let localPath = localFonts.first(where: { $0["id"] as? String == id })?["path"] as? String
            return [
                "id": id,
                "displayName": fields["displayName"] as? String ?? id,
                "fileName": fields["fileName"] as? String ?? "",
                "fileExtension": fields["fileExtension"] as? String ?? "",
                "timestamp": numericInt64(fields["timestamp"]),
                "isDeleted": fields["isDeleted"] as? Bool ?? false,
                // Paths are device-local and are never written to Firestore.
                // Keep this device's path in the returned snapshot so a
                // metadata-only sync cannot make an installed font disappear.
                "path": localPath ?? "",
            ]
        }.sorted { ($0["id"] as? String ?? "") < ($1["id"] as? String ?? "") }
    }

    private func firestoreFields(from book: [String: Any]) -> [String: Any] {
        let position = book["readerPosition"] as? [String: Any]
        let readingTimestamp = numericInt64(book["readingPositionModifiedTimestamp"])
        let metadataTimestamp = numericInt64(book["metadataModifiedTimestamp"])
        let baseTimestamp = numericInt64(book["timestamp"])
        let highlights = book["readerHighlights"] as? [Any] ?? []
        let bookmarks = book["readerBookmarks"] as? [[String: Any]] ?? []
        let isPdf = (book["type"] as? String)?.uppercased() == "PDF"
        let localSidecar = isPdf ? localPdfSidecar(bookId: book["id"] as? String ?? "") : nil
        let legacyHasAnnotations = !highlights.isEmpty || !bookmarks.isEmpty
        let annotationTimestamp = max(
            numericInt64(book["annotationModifiedTimestamp"]),
            localSidecar?.timestamp ?? 0
        )
        let hasAnnotations = legacyHasAnnotations || localSidecar != nil ||
            (book["hasAnnotations"] as? Bool ?? false)
        return [
            "bookId": book["id"] as? String ?? "",
            "title": nullableFirestoreValue(book["title"]),
            "author": nullableFirestoreValue(book["author"]),
            "displayName": book["displayName"] as? String ?? "",
            "type": book["type"] as? String ?? "",
            "lastPositionCfi": nullableFirestoreValue(stablePositionCFI(position)),
            "lastChapterIndex": nullableFirestoreValue(position?["chapterIndex"]),
            "locatorBlockIndex": nullableFirestoreValue(position?["blockIndex"]),
            "locatorCharOffset": nullableFirestoreValue(position?["charOffset"]),
            "lastPage": nullableFirestoreValue(book["lastPageIndex"]),
            "progressPercentage": nullableFirestoreValue(book["progressPercentage"]),
            "isRecent": book["isRecent"] as? Bool ?? true,
            "isDeleted": false,
            "lastModifiedTimestamp": max(baseTimestamp, readingTimestamp, metadataTimestamp, annotationTimestamp),
            "readingPositionModifiedTimestamp": readingTimestamp,
            "annotationModifiedTimestamp": annotationTimestamp,
            "bookmarksJson": jsonStringValue(androidBookmarkPayload(bookmarks)),
            "originDeviceId": cloudSyncDeviceID(),
            "hasAnnotations": hasAnnotations,
            "fileContentModifiedTimestamp": numericInt64(book["fileContentModifiedTimestamp"]),
            "customName": NSNull(),
            "highlightsJson": jsonStringValue(highlights),
            "seriesName": nullableFirestoreValue(book["seriesName"]),
            "seriesIndex": nullableFirestoreValue(book["seriesIndex"]),
            "description": nullableFirestoreValue(book["description"]),
            "originalTitle": nullableFirestoreValue(book["originalTitle"] ?? book["title"]),
            "originalAuthor": nullableFirestoreValue(book["originalAuthor"] ?? book["author"]),
            "originalSeriesName": nullableFirestoreValue(book["originalSeriesName"] ?? book["seriesName"]),
            "originalSeriesIndex": nullableFirestoreValue(book["originalSeriesIndex"] ?? book["seriesIndex"]),
            "originalDescription": nullableFirestoreValue(book["originalDescription"] ?? book["description"]),
        ]
    }

    private func snapshotBook(fromFirestore fields: [String: Any]) -> [String: Any]? {
        guard
            let id = fields["bookId"] as? String,
            let displayName = fields["displayName"] as? String,
            let type = fields["type"] as? String
        else {
            return nil
        }
        let cfi = fields["lastPositionCfi"] as? String
        let chapterIndex = optionalInt(fields["lastChapterIndex"])
        let pageIndex = optionalInt(fields["lastPage"])
        let blockIndex = optionalInt(fields["locatorBlockIndex"])
        let charOffset = optionalInt(fields["locatorCharOffset"])
        var position: [String: Any] = [:]
        if let cfi { position["cfi"] = cfi }
        if let chapterIndex { position["chapterIndex"] = chapterIndex }
        if let pageIndex { position["pageIndex"] = pageIndex }
        if let blockIndex { position["blockIndex"] = blockIndex }
        if let charOffset { position["charOffset"] = charOffset }

        return [
            "id": id,
            "path": NSNull(),
            "type": type,
            "displayName": displayName,
            "timestamp": numericInt64(fields["lastModifiedTimestamp"]),
            "annotationModifiedTimestamp": numericInt64(fields["annotationModifiedTimestamp"]),
            "hasAnnotations": fields["hasAnnotations"] as? Bool ?? false,
            "title": nullableJSONValue(fields["title"]),
            "author": nullableJSONValue(fields["author"]),
            "description": nullableJSONValue(fields["description"]),
            "originalTitle": nullableJSONValue(fields["originalTitle"]),
            "originalAuthor": nullableJSONValue(fields["originalAuthor"]),
            "originalSeriesName": nullableJSONValue(fields["originalSeriesName"]),
            "originalSeriesIndex": nullableJSONValue(fields["originalSeriesIndex"]),
            "originalDescription": nullableJSONValue(fields["originalDescription"]),
            "progressPercentage": nullableJSONValue(fields["progressPercentage"]),
            "isRecent": fields["isRecent"] as? Bool ?? true,
            "isAvailable": false,
            "fileSize": 0,
            "fileContentModifiedTimestamp": numericInt64(fields["fileContentModifiedTimestamp"]),
            "metadataModifiedTimestamp": numericInt64(fields["lastModifiedTimestamp"]),
            "sourceFolder": NSNull(),
            "folderTextMetadataParsed": false,
            "seriesName": nullableJSONValue(fields["seriesName"]),
            "seriesIndex": nullableJSONValue(fields["seriesIndex"]),
            "tags": [],
            "lastPageIndex": pageIndex.map(NSNumber.init(value:)) ?? NSNull(),
            "readerPosition": position.isEmpty ? NSNull() : position,
            "readerSettings": NSNull(),
            "readerFormatIsLocal": false,
            "readerLocalFormatSettings": NSNull(),
            "readerAutoScrollIsLocal": false,
            "readerAutoScrollLocalSpeed": NSNull(),
            "readerBookmarks": sharedBookmarks(from: fields["bookmarksJson"] as? String),
            "readerHighlights": jsonArray(from: fields["highlightsJson"] as? String),
            "pdfReaderViewport": NSNull(),
            "readingPositionModifiedTimestamp": numericInt64(fields["readingPositionModifiedTimestamp"]),
            "titleSortKey": nullableJSONValue(fields["customName"]),
        ]
    }

    private func cloudBookIsFirestoreSyncable(_ book: [String: Any]) -> Bool {
        guard (book["sourceFolder"] is NSNull) || book["sourceFolder"] == nil else { return false }
        guard let path = book["path"] as? String, !path.hasPrefix("opds-pse://") else { return false }
        guard let displayName = book["displayName"] as? String else { return false }
        return !CloudBook.manualOnlyExtensions.contains(
            URL(fileURLWithPath: displayName).pathExtension.lowercased()
        )
    }

    private func stablePositionCFI(_ position: [String: Any]?) -> String? {
        if let cfi = position?["cfi"] as? String, !cfi.isEmpty { return cfi }
        guard let chapter = optionalInt(position?["chapterIndex"]) else { return nil }
        if
            let block = optionalInt(position?["blockIndex"]),
            let character = optionalInt(position?["charOffset"])
        {
            return "android-locator:\(chapter):\(block):\(character)"
        }
        if let page = optionalInt(position?["pageIndex"]) {
            return "desktop:\(chapter):\(page)"
        }
        return nil
    }

    private func androidBookmarkPayload(_ bookmarks: [[String: Any]]) -> [String] {
        bookmarks.compactMap { bookmark in
            var android = bookmark
            let locator = bookmark["locator"] as? [String: Any]
            android["cfi"] = stablePositionCFI(locator) ?? bookmark["id"] as? String ?? ""
            android["chapterTitle"] = bookmark["chapterTitle"] as? String ?? ""
            android["snippet"] = bookmark["preview"] as? String ?? ""
            android["chapterIndex"] = optionalInt(locator?["chapterIndex"]) ?? 0
            guard let data = try? JSONSerialization.data(withJSONObject: android) else { return nil }
            return String(data: data, encoding: .utf8)
        }
    }

    private func sharedBookmarks(from raw: String?) -> [[String: Any]] {
        guard
            let entries = jsonArray(from: raw) as? [String]
        else {
            return []
        }
        return entries.compactMap { entry in
            guard
                let data = entry.data(using: .utf8),
                let bookmark = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            else {
                return nil
            }
            let page = max((bookmark["pageInChapter"] as? NSNumber)?.intValue ?? 1, 1) - 1
            return [
                "id": bookmark["cfi"] as? String ?? UUID().uuidString,
                "pageIndex": page,
                "chapterTitle": bookmark["chapterTitle"] as? String ?? "",
                "preview": bookmark["snippet"] as? String ?? "",
                "label": nullableJSONValue(bookmark["label"]),
                "locator": bookmark["locator"] as? [String: Any] ?? [:],
            ]
        }
    }

    private func jsonArray(from raw: String?) -> [Any] {
        guard
            let raw,
            let data = raw.data(using: .utf8),
            let array = try? JSONSerialization.jsonObject(with: data) as? [Any]
        else {
            return []
        }
        return array
    }

    private func jsonStringValue(_ value: Any) -> Any {
        guard
            let data = try? JSONSerialization.data(withJSONObject: value),
            let string = String(data: data, encoding: .utf8)
        else {
            return NSNull()
        }
        return string
    }

    private func nullableFirestoreValue(_ value: Any?) -> Any {
        value is NSNull || value == nil ? NSNull() : value!
    }

    private func nullableJSONValue(_ value: Any?) -> Any {
        value is NSNull || value == nil ? NSNull() : value!
    }

    private func optionalInt(_ value: Any?) -> Int? {
        (value as? NSNumber)?.intValue
    }

    private func registerCurrentDevice(uid: String) async throws {
        let deviceID = cloudSyncDeviceID()
        let deviceName = UIDevice.current.name.isEmpty
            ? "iPhone \(UIDevice.current.model)"
            : UIDevice.current.name
        let fields: [String: Any] = [
            "deviceName": deviceName,
            "lastSeen": FieldValue.serverTimestamp(),
            "status": "active",
            "platform": "ios",
            "originDeviceId": deviceID,
        ]
        try await Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("devices")
            .document(deviceID)
            .setData(fields, merge: true)
    }

    private func unregisterDevice(uid: String) async {
#if canImport(FirebaseFirestore)
        guard FirebaseApp.app() != nil else { return }
        do {
            try await Firestore.firestore()
                .collection("users")
                .document(uid)
                .collection("devices")
                .document(cloudSyncDeviceID())
                .delete()
        } catch {
            syncLogger.error("cloud_sync.device_unregister_failed error=\(error.localizedDescription, privacy: .public)")
        }
#endif
    }

    /// Mirrors Android's clear-cloud scope: user book and shelf metadata are
    /// removed, while profile/purchase data and the account itself remain.
    /// Drive content (books, fonts, and PDF sidecars) is deleted by the
    /// confirmation-gated caller before this method runs.
    private func deleteCloudFirestoreData(uid: String) async throws {
        let database = Firestore.firestore()
        for collectionName in ["books", "shelves"] {
            let snapshot = try await database
                .collection("users")
                .document(uid)
                .collection(collectionName)
                .getDocuments()
            let references = snapshot.documents.map(\.reference)
            for chunk in stride(from: 0, to: references.count, by: 450) {
                let batch = database.batch()
                for reference in references[chunk..<min(chunk + 450, references.count)] {
                    batch.deleteDocument(reference)
                }
                try await batch.commit()
            }
        }
    }

    /// Read-only device management API for the iOS settings host. The shared
    /// UI can opt into these handlers without coupling itself to Firebase.
    func registeredDevices() async -> [[String: Any]] {
#if canImport(FirebaseFirestore) && canImport(FirebaseAuth)
        guard let uid = Auth.auth().currentUser?.uid else { return [] }
        do {
            let snapshot = try await Firestore.firestore()
                .collection("users")
                .document(uid)
                .collection("devices")
                .whereField("status", isEqualTo: "active")
                .getDocuments()
            return snapshot.documents.map { document in
                [
                    "deviceId": document.documentID,
                    "deviceName": document.get("deviceName") as? String ?? document.documentID,
                    "lastSeenEpochMillis": ((document.get("lastSeen") as? Timestamp)?.dateValue().timeIntervalSince1970 ?? 0) * 1000,
                ]
            }
        } catch {
            syncLogger.error("cloud_sync.device_list_failed error=\(error.localizedDescription, privacy: .public)")
            return []
        }
#else
        return []
#endif
    }

    func revokeDevice(deviceID: String) async -> Bool {
#if canImport(FirebaseFirestore) && canImport(FirebaseAuth)
        guard let uid = Auth.auth().currentUser?.uid, !deviceID.isEmpty else { return false }
        do {
            try await Firestore.firestore()
                .collection("users")
                .document(uid)
                .collection("devices")
                .document(deviceID)
                .setData(["status": "revoked", "originDeviceId": cloudSyncDeviceID()], merge: true)
            return true
        } catch {
            syncLogger.error("cloud_sync.device_revoke_failed error=\(error.localizedDescription, privacy: .public)")
            return false
        }
#else
        return false
#endif
    }
#else
    private func syncFirestoreBookMetadata(localJSON: String) async throws -> String {
        throw CloudSyncError.firestoreUnavailable
    }

    private func unregisterDevice(uid: String) async {}
#endif

#if canImport(GoogleSignIn)
    private struct DriveFile: Decodable {
        let id: String
        let name: String
        let modifiedTime: String?
    }

    private struct DriveFileList: Decodable {
        let files: [DriveFile]
        let nextPageToken: String?
    }

    private struct CloudBook {
        let id: String
        let type: String
        let displayName: String
        let path: String?
        let sourceFolder: String?
        let fileContentModifiedTimestamp: Int64
        let annotationModifiedTimestamp: Int64
        let hasAnnotations: Bool

        var driveFileName: String? {
            guard sourceFolder == nil, path?.hasPrefix("opds-pse://") != true else { return nil }
            guard !Self.isManualOnly(displayName) else { return nil }
            guard let ext = Self.primaryExtension[type] else { return nil }
            return "\(id).\(ext)"
        }

        fileprivate static let primaryExtension: [String: String] = [
            "PDF": "pdf", "EPUB": "epub", "MOBI": "mobi", "TXT": "txt",
            "MD": "md", "HTML": "html", "FB2": "fb2", "FODT": "fodt",
            "CBZ": "cbz", "CBR": "cbr", "CB7": "cb7", "CBT": "cbt",
            "DOCX": "docx", "ODT": "odt", "PPTX": "pptx",
        ]

        fileprivate static let manualOnlyExtensions: Set<String> = [
            "json", "xml", "yaml", "yml", "toml", "csv", "tsv", "js", "ts",
            "kt", "kts", "swift", "java", "c", "cc", "cpp", "h", "hpp",
            "py", "rb", "rs", "go", "sh", "zsh", "css",
        ]

        private static func isManualOnly(_ name: String) -> Bool {
            manualOnlyExtensions.contains(
                URL(fileURLWithPath: name).pathExtension.lowercased()
            )
        }
    }

    private struct DownloadedCloudBook {
        let id: String
        let path: String
    }

    private struct CloudFont {
        let id: String
        let displayName: String
        let fileName: String
        let fileExtension: String
        let timestamp: Int64
        let isDeleted: Bool
        let path: String?

        var safeFileName: String {
            let candidate = URL(fileURLWithPath: fileName).lastPathComponent
            return candidate.isEmpty ? "font-\(id).\(fileExtension)" : candidate
        }
    }

    private func googleDriveAccessToken() async throws -> String {
        guard googleDriveAuthorized, let user = GIDSignIn.sharedInstance.currentUser else {
            throw CloudSyncError.googleAuthorizationRequired
        }
        let refreshedUser = try await user.refreshTokensIfNeeded()
        guard refreshedUser.grantedScopes?.contains(Self.googleDriveScope) == true else {
            googleDriveAuthorized = false
            publish(status: nil)
            throw CloudSyncError.googleAuthorizationRequired
        }
        return refreshedUser.accessToken.tokenString
    }

    private func listDriveFiles(accessToken: String) async throws -> [DriveFile] {
        var files: [DriveFile] = []
        var pageToken: String?
        var seenPageTokens = Set<String>()
        repeat {
            var components = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
            components.queryItems = [
                URLQueryItem(name: "spaces", value: "appDataFolder"),
                URLQueryItem(name: "q", value: "trashed = false"),
                URLQueryItem(name: "fields", value: "nextPageToken,files(id,name,modifiedTime)"),
                URLQueryItem(name: "pageSize", value: "1000"),
            ]
            if let pageToken {
                components.queryItems?.append(URLQueryItem(name: "pageToken", value: pageToken))
            }
            let data = try await driveRequest(
                url: components.url!,
                method: "GET",
                accessToken: accessToken
            )
            let page = try JSONDecoder().decode(DriveFileList.self, from: data)
            files.append(contentsOf: page.files)
            guard let next = page.nextPageToken?.trimmingCharacters(in: .whitespacesAndNewlines), !next.isEmpty else {
                pageToken = nil
                break
            }
            guard seenPageTokens.insert(next).inserted else {
                throw CloudSyncError.invalidDrivePageToken
            }
            pageToken = next
        } while pageToken != nil
        return files
    }

    private func parseCloudBooks(_ snapshotJSON: String) throws -> [CloudBook] {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let books = root["books"] as? [[String: Any]]
        else {
            throw CloudSyncError.invalidSnapshot
        }
        return books.compactMap { book in
            guard
                let id = book["id"] as? String,
                let type = book["type"] as? String,
                let displayName = book["displayName"] as? String
            else {
                return nil
            }
            return CloudBook(
                id: id,
                type: type,
                displayName: displayName,
                path: book["path"] as? String,
                sourceFolder: book["sourceFolder"] as? String,
                fileContentModifiedTimestamp: (book["fileContentModifiedTimestamp"] as? NSNumber)?.int64Value ?? 0,
                annotationModifiedTimestamp: numericInt64(book["annotationModifiedTimestamp"]),
                hasAnnotations: book["hasAnnotations"] as? Bool ?? false
            )
        }
    }

    private func downloadMissingCloudBooks(
        localJSON: String,
        remoteJSON: String,
        accessToken: String
    ) async throws -> [DownloadedCloudBook] {
        let localBooks = try parseCloudBooks(localJSON)
        let localByID = Dictionary(uniqueKeysWithValues: localBooks.map { ($0.id, $0) })
        let remoteBooks = try parseCloudBooks(remoteJSON)
            .filter { remote in
                guard remote.driveFileName != nil else { return false }
                guard let local = localByID[remote.id] else { return true }
                let localURL = local.path.flatMap(resolveCloudBookPath)
                let localExists = localURL.map {
                    FileManager.default.fileExists(atPath: $0.path)
                } ?? false
                return !localExists ||
                    remote.fileContentModifiedTimestamp > local.fileContentModifiedTimestamp
            }
        guard !remoteBooks.isEmpty else { return [] }
        let filesByName = driveFilesByName(
            try await listDriveFiles(accessToken: accessToken)
        )
        let imports = try cloudImportsDirectory()
        var downloaded: [DownloadedCloudBook] = []

        for book in remoteBooks {
            guard
                let driveName = book.driveFileName,
                let driveFile = filesByName[driveName]
            else {
                continue
            }
            let safeName = driveName.replacingOccurrences(of: "/", with: "_")
            let destination = imports.appendingPathComponent(safeName)
            let temporary = imports.appendingPathComponent(".\(safeName).\(UUID().uuidString).download")
            do {
                let data = try await downloadDriveFile(
                    fileID: driveFile.id,
                    accessToken: accessToken
                )
                try data.write(to: temporary, options: .atomic)
                if FileManager.default.fileExists(atPath: destination.path) {
                    try FileManager.default.removeItem(at: destination)
                }
                try FileManager.default.moveItem(at: temporary, to: destination)
                if book.fileContentModifiedTimestamp > 0 {
                    try? FileManager.default.setAttributes(
                        [.modificationDate: Date(timeIntervalSince1970: Double(book.fileContentModifiedTimestamp) / 1000)],
                        ofItemAtPath: destination.path
                    )
                }
                downloaded.append(DownloadedCloudBook(id: book.id, path: destination.path))
            } catch {
                try? FileManager.default.removeItem(at: temporary)
                throw error
            }
        }
        return downloaded
    }

    private func uploadCloudBookContents(
        snapshotJSON: String,
        accessToken: String
    ) async throws {
        let books = try parseCloudBooks(snapshotJSON)
        let driveFiles = try await listDriveFiles(accessToken: accessToken)
        let filesByName = driveFilesByName(driveFiles)

        for book in books {
            guard
                let driveName = book.driveFileName,
                let storedPath = book.path,
                let localURL = resolveCloudBookPath(storedPath),
                FileManager.default.fileExists(atPath: localURL.path)
            else {
                continue
            }
            let localModified = (
                try? localURL.resourceValues(forKeys: [.contentModificationDateKey])
                    .contentModificationDate?
                    .timeIntervalSince1970
            ) ?? 0
            let remoteFile = filesByName[driveName]
            let remoteModified = remoteFile?.modifiedTime
                .flatMap(Self.driveDateFormatter.date(from:))?
                .timeIntervalSince1970 ?? 0
            if remoteFile != nil && remoteModified >= localModified {
                continue
            }
            let content = try Data(contentsOf: localURL, options: .mappedIfSafe)
            try await uploadDriveFile(
                content,
                name: driveName,
                fileID: remoteFile?.id,
                accessToken: accessToken
            )
        }
    }

    private func parseCloudFonts(_ snapshotJSON: String) throws -> [CloudFont] {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let fonts = root["customFonts"] as? [[String: Any]]
        else {
            throw CloudSyncError.invalidSnapshot
        }
        return fonts.compactMap { font in
            guard
                let id = font["id"] as? String,
                let fileName = font["fileName"] as? String,
                !id.isEmpty,
                !fileName.isEmpty
            else { return nil }
            return CloudFont(
                id: id,
                displayName: font["displayName"] as? String ?? id,
                fileName: fileName,
                fileExtension: font["fileExtension"] as? String ?? URL(fileURLWithPath: fileName).pathExtension,
                timestamp: numericInt64(font["timestamp"]),
                isDeleted: font["isDeleted"] as? Bool ?? false,
                path: font["path"] as? String
            )
        }
    }

    private func cloudFontsDirectory() throws -> URL {
        let root = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("Fonts", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    private func downloadMissingCloudFonts(
        localJSON: String,
        remoteJSON: String,
        accessToken: String
    ) async throws -> [String: String] {
        let localFonts = try parseCloudFonts(localJSON)
        let localByID = Dictionary(uniqueKeysWithValues: localFonts.map { ($0.id, $0) })
        let remoteFonts = try parseCloudFonts(remoteJSON).filter { !$0.isDeleted }
        guard !remoteFonts.isEmpty else { return [:] }
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        let destinationDirectory = try cloudFontsDirectory()
        var downloaded: [String: String] = [:]
        for font in remoteFonts {
            let destination = destinationDirectory.appendingPathComponent(font.safeFileName)
            let local = localByID[font.id]
            let localExists = local?.path.flatMap { FileManager.default.fileExists(atPath: $0) } ?? false
            if localExists && font.timestamp <= (local?.timestamp ?? 0) { continue }
            guard let driveFile = filesByName[font.safeFileName] ?? filesByName[font.fileName] else {
                continue
            }
            let temporary = destinationDirectory.appendingPathComponent(".\(font.safeFileName).\(UUID().uuidString).download")
            do {
                let data = try await downloadDriveFile(fileID: driveFile.id, accessToken: accessToken)
                try data.write(to: temporary, options: .atomic)
                if FileManager.default.fileExists(atPath: destination.path) {
                    try FileManager.default.removeItem(at: destination)
                }
                try FileManager.default.moveItem(at: temporary, to: destination)
                if font.timestamp > 0 {
                    try? FileManager.default.setAttributes(
                        [.modificationDate: Date(timeIntervalSince1970: Double(font.timestamp) / 1000)],
                        ofItemAtPath: destination.path
                    )
                }
                downloaded[font.id] = destination.path
            } catch {
                try? FileManager.default.removeItem(at: temporary)
                throw error
            }
        }
        syncLogger.info("cloud_sync.font_downloads count=\(downloaded.count)")
        return downloaded
    }

    private func uploadCloudFontContents(
        snapshotJSON: String,
        accessToken: String
    ) async throws {
        let fonts = try parseCloudFonts(snapshotJSON)
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        for font in fonts {
            if font.isDeleted {
                if let file = filesByName[font.safeFileName] ?? filesByName[font.fileName] {
                    try await deleteDriveFile(fileID: file.id, accessToken: accessToken)
                }
                continue
            }
            guard
                let path = font.path,
                FileManager.default.fileExists(atPath: path)
            else { continue }
            let localURL = URL(fileURLWithPath: path)
            let data = try Data(contentsOf: localURL, options: .mappedIfSafe)
            let remote = filesByName[font.safeFileName] ?? filesByName[font.fileName]
            let localModified = (
                try? localURL.resourceValues(forKeys: [.contentModificationDateKey])
                    .contentModificationDate?
                    .timeIntervalSince1970
            ) ?? (Double(font.timestamp) / 1000)
            let remoteModified = remote?.modifiedTime
                .flatMap(Self.driveDateFormatter.date(from:))?
                .timeIntervalSince1970 ?? 0
            if remote != nil && remoteModified >= localModified {
                continue
            }
            try await uploadDriveFile(
                data,
                name: font.safeFileName,
                fileID: remote?.id,
                accessToken: accessToken
            )
        }
    }

    private func pdfSidecarsFromSnapshot(_ snapshotJSON: String) -> [CloudPdfSidecar] {
        let pdfBookIDs = Set(
            (try? parseCloudBooks(snapshotJSON))?.filter {
                $0.type.uppercased() == "PDF" && $0.driveFileName != nil
            }
                .map(\.id) ?? []
        )
        guard !pdfBookIDs.isEmpty else { return [] }
        return parseCloudPdfSidecars(snapshotJSON)
            .filter { pdfBookIDs.contains($0.bookId) }
    }

    private func downloadMissingCloudPdfSidecars(
        localJSON: String,
        remoteJSON: String,
        accessToken: String
    ) async throws -> [DownloadedCloudPdfSidecar] {
        let remoteBooks = try parseCloudBooks(remoteJSON)
            .filter { $0.type.uppercased() == "PDF" && $0.driveFileName != nil }
        guard !remoteBooks.isEmpty else { return [] }

        let localSidecars = Dictionary(
            uniqueKeysWithValues: pdfSidecarsFromSnapshot(localJSON).map { ($0.bookId, $0) }
        )
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        var downloaded: [DownloadedCloudPdfSidecar] = []
        for book in remoteBooks {
            let fileName = SharedPdfCloudSidecarCodec.shared.driveFileName(bookId: book.id)
            guard let driveFile = filesByName[fileName] else { continue }
            let remoteTimestamp = driveModifiedTimestamp(driveFile.modifiedTime)
            let localSidecar = localSidecars[book.id] ?? localPdfSidecar(bookId: book.id)
            let localTimestamp = max(
                localSidecar?.timestamp ?? 0,
                book.annotationModifiedTimestamp
            )
            // If the snapshot already contains a durable local copy, only
            // fetch Drive when the server has a strictly newer revision.
            guard localSidecar == nil || remoteTimestamp > localTimestamp else {
                continue
            }
            let rawData = try await downloadDriveFile(
                fileID: driveFile.id,
                accessToken: accessToken
            )
            guard let remoteData = String(data: rawData, encoding: .utf8),
                  SharedPdfCloudSidecarCodec.shared.isCompatiblePayload(rawDataJson: remoteData)
            else {
                syncLogger.error(
                    "cloud_sync.pdf_sidecar_invalid book=\(book.id, privacy: .public) bytes=\(rawData.count)"
                )
                continue
            }
            let mergedData = localSidecar.map {
                mergePdfSidecarData(
                    localData: $0.data,
                    remoteData: remoteData,
                    preferRemoteOnConflict: true
                )
            } ?? remoteData
            let appliedTimestamp = max(remoteTimestamp, localTimestamp)
            guard writePdfSidecar(
                bookId: book.id,
                data: mergedData,
                modifiedTimestamp: appliedTimestamp
            ) else {
                throw CloudSyncError.sidecarPersistenceFailed(book.id)
            }
            downloaded.append(
                DownloadedCloudPdfSidecar(
                    bookId: book.id,
                    timestamp: appliedTimestamp,
                    bytes: mergedData.utf8.count,
                    data: mergedData
                )
            )
            syncLogger.info(
                "cloud_sync.pdf_sidecar_download book=\(book.id, privacy: .public) remoteTs=\(remoteTimestamp) appliedTs=\(appliedTimestamp) bytes=\(mergedData.utf8.count)"
            )
        }
        return downloaded
    }

    private func uploadCloudPdfSidecars(
        snapshotJSON: String,
        accessToken: String
    ) async throws -> [CloudPdfSidecar] {
        let localSidecars = pdfSidecarsFromSnapshot(snapshotJSON)
        guard !localSidecars.isEmpty else { return [] }
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        var uploaded: [CloudPdfSidecar] = []
        for sidecar in localSidecars {
            let fileName = SharedPdfCloudSidecarCodec.shared.driveFileName(bookId: sidecar.bookId)
            let remoteFile = filesByName[fileName]
            let remoteTimestamp = driveModifiedTimestamp(remoteFile?.modifiedTime)
            guard remoteFile == nil || sidecar.timestamp > remoteTimestamp else {
                syncLogger.debug(
                    "cloud_sync.pdf_sidecar_upload_skip book=\(sidecar.bookId, privacy: .public) localTs=\(sidecar.timestamp) remoteTs=\(remoteTimestamp)"
                )
                continue
            }
            var uploadData = sidecar.data
            if let remoteFile {
                let rawRemoteData = try await downloadDriveFile(
                    fileID: remoteFile.id,
                    accessToken: accessToken
                )
                if let remoteData = String(data: rawRemoteData, encoding: .utf8),
                   SharedPdfCloudSidecarCodec.shared.isCompatiblePayload(rawDataJson: remoteData) {
                    uploadData = mergePdfSidecarData(
                        localData: sidecar.data,
                        remoteData: remoteData,
                        preferRemoteOnConflict: false
                    )
                }
            }
            let response = try await uploadDriveFile(
                Data(uploadData.utf8),
                name: fileName,
                fileID: remoteFile?.id,
                accessToken: accessToken,
                contentType: "application/json"
            )
            let uploadedTimestamp = max(
                driveModifiedTimestamp(response?.modifiedTime),
                max(sidecar.timestamp, remoteTimestamp)
            )
            guard writePdfSidecar(
                bookId: sidecar.bookId,
                data: uploadData,
                modifiedTimestamp: uploadedTimestamp
            ) else {
                throw CloudSyncError.sidecarPersistenceFailed(sidecar.bookId)
            }
            uploaded.append(
                CloudPdfSidecar(
                    bookId: sidecar.bookId,
                    timestamp: uploadedTimestamp,
                    data: uploadData
                )
            )
            syncLogger.info(
                "cloud_sync.pdf_sidecar_upload book=\(sidecar.bookId, privacy: .public) localTs=\(sidecar.timestamp) driveTs=\(uploadedTimestamp) bytes=\(uploadData.utf8.count)"
            )
        }
        return uploaded
    }

    private func injectDownloadedFontPaths(
        _ snapshotJSON: String,
        pathsByFontID: [String: String]
    ) -> String {
        guard
            !pathsByFontID.isEmpty,
            let data = snapshotJSON.data(using: .utf8),
            var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
            var fonts = root["customFonts"] as? [[String: Any]]
        else { return snapshotJSON }
        for index in fonts.indices {
            guard let id = fonts[index]["id"] as? String,
                  let path = pathsByFontID[id] else { continue }
            fonts[index]["path"] = path
        }
        root["customFonts"] = fonts
        guard let encoded = try? JSONSerialization.data(withJSONObject: root) else {
            return snapshotJSON
        }
        return String(data: encoded, encoding: .utf8) ?? snapshotJSON
    }

    private func deleteCloudBookContentsForTombstones(
        snapshotJSON: String,
        accessToken: String
    ) async throws {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let tombstones = root["bookTombstones"] as? [[String: Any]],
            !tombstones.isEmpty
        else {
            return
        }
        let filesByName = driveFilesByName(
            try await listDriveFiles(accessToken: accessToken)
        )
        for tombstone in tombstones {
            guard
                let bookID = tombstone["bookId"] as? String,
                let type = tombstone["type"] as? String,
                let fileExtension = CloudBook.primaryExtension[type],
                let driveFile = filesByName["\(bookID).\(fileExtension)"]
            else {
                continue
            }
            try await deleteDriveFile(fileID: driveFile.id, accessToken: accessToken)
        }
    }

    private func deleteCloudPdfSidecarsForTombstones(
        snapshotJSON: String,
        accessToken: String
    ) async throws {
        guard
            let data = snapshotJSON.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let tombstones = root["bookTombstones"] as? [[String: Any]],
            !tombstones.isEmpty
        else { return }

        let pdfBookIDs = Set(tombstones.compactMap { tombstone -> String? in
            guard
                let bookId = tombstone["bookId"] as? String,
                (tombstone["type"] as? String)?.uppercased() == "PDF"
            else { return nil }
            return bookId
        })
        guard !pdfBookIDs.isEmpty else { return }
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        for bookId in pdfBookIDs {
            let fileName = SharedPdfCloudSidecarCodec.shared.driveFileName(bookId: bookId)
            if let driveFile = filesByName[fileName] {
                try await deleteDriveFile(fileID: driveFile.id, accessToken: accessToken)
            }
            _ = IosPdfCloudSidecarStore.shared.delete(bookId: bookId)
            syncLogger.info("cloud_sync.pdf_sidecar_deleted book=\(bookId, privacy: .public)")
        }
    }

    private func deleteCloudFontContentsForTombstones(
        snapshotJSON: String,
        accessToken: String
    ) async throws {
        let tombstonedFonts = try parseCloudFonts(snapshotJSON).filter(\.isDeleted)
        guard !tombstonedFonts.isEmpty else { return }
        let filesByName = driveFilesByName(try await listDriveFiles(accessToken: accessToken))
        for font in tombstonedFonts {
            guard let driveFile = filesByName[font.safeFileName] ?? filesByName[font.fileName] else {
                continue
            }
            try await deleteDriveFile(fileID: driveFile.id, accessToken: accessToken)
            syncLogger.info("cloud_sync.font_deleted id=\(font.id, privacy: .public)")
        }
    }

    private func resolveCloudBookPath(_ path: String) -> URL? {
        if path.hasPrefix("Imports/") {
            return try? cloudImportsDirectory()
                .appendingPathComponent(String(path.dropFirst("Imports/".count)))
        }
        if path.hasPrefix("Documents/") {
            return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
                .first?
                .appendingPathComponent(String(path.dropFirst("Documents/".count)))
        }
        return path.hasPrefix("/") ? URL(fileURLWithPath: path) : nil
    }

    private func cloudImportsDirectory() throws -> URL {
        let root = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("Imports", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    private static let driveDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let driveDateFormatterWithoutFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private func driveModifiedTimestamp(_ value: String?) -> Int64 {
        guard let value else { return 0 }
        let date = Self.driveDateFormatter.date(from: value)
            ?? Self.driveDateFormatterWithoutFractionalSeconds.date(from: value)
        return date.map { Int64($0.timeIntervalSince1970 * 1000) } ?? 0
    }

    private func driveFilesByName(_ files: [DriveFile]) -> [String: DriveFile] {
        files.reduce(into: [:]) { result, file in
            guard let existing = result[file.name] else {
                result[file.name] = file
                return
            }
            let existingDate = existing.modifiedTime
                .flatMap(Self.driveDateFormatter.date(from:)) ?? .distantPast
            let candidateDate = file.modifiedTime
                .flatMap(Self.driveDateFormatter.date(from:)) ?? .distantPast
            if candidateDate > existingDate {
                result[file.name] = file
            }
        }
    }

    private func downloadDriveFile(fileID: String, accessToken: String) async throws -> Data {
        var components = URLComponents(
            string: "https://www.googleapis.com/drive/v3/files/\(fileID)"
        )!
        components.queryItems = [URLQueryItem(name: "alt", value: "media")]
        return try await driveRequest(
            url: components.url!,
            method: "GET",
            accessToken: accessToken
        )
    }

    private func deleteDriveFile(fileID: String, accessToken: String) async throws {
        let url = URL(string: "https://www.googleapis.com/drive/v3/files/\(fileID)")!
        _ = try await driveRequest(
            url: url,
            method: "DELETE",
            accessToken: accessToken
        )
    }

    private func uploadDriveFile(
        _ content: Data,
        name: String,
        fileID: String?,
        accessToken: String,
        contentType: String = "application/octet-stream"
    ) async throws -> DriveFile? {
        if let fileID {
            var components = URLComponents(
                string: "https://www.googleapis.com/upload/drive/v3/files/\(fileID)"
            )!
            components.queryItems = [
                URLQueryItem(name: "uploadType", value: "media"),
                URLQueryItem(name: "fields", value: "id,name,modifiedTime"),
            ]
            let response = try await driveRequest(
                url: components.url!,
                method: "PATCH",
                accessToken: accessToken,
                contentType: contentType,
                body: content
            )
            return try? JSONDecoder().decode(DriveFile.self, from: response)
        }
        let boundary = "EpistemeContent-\(UUID().uuidString)"
        let metadata = "{\"name\":\(jsonString(name)),\"parents\":[\"appDataFolder\"]}"
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n\(metadata)\r\n".utf8)
        body.append(Data("--\(boundary)\r\nContent-Type: \(contentType)\r\n\r\n".utf8))
        body.append(content)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        var components = URLComponents(string: "https://www.googleapis.com/upload/drive/v3/files")!
        components.queryItems = [
            URLQueryItem(name: "uploadType", value: "multipart"),
            URLQueryItem(name: "fields", value: "id,name,modifiedTime"),
        ]
        let response = try await driveRequest(
            url: components.url!,
            method: "POST",
            accessToken: accessToken,
            contentType: "multipart/related; boundary=\(boundary)",
            body: body
        )
        return try? JSONDecoder().decode(DriveFile.self, from: response)
    }

    private func jsonString(_ value: String) -> String {
        let data = try? JSONSerialization.data(withJSONObject: [value])
        let encoded = data.flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        return String(encoded.dropFirst().dropLast())
    }

    private func driveRequest(
        url: URL,
        method: String,
        accessToken: String,
        contentType: String? = nil,
        body: Data? = nil
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw CloudSyncError.requestFailed(status)
        }
        return data
    }
#endif

    private enum CloudSyncError: LocalizedError {
        case googleAuthorizationRequired
        case firebaseAccountRequired
        case firestoreUnavailable
        case invalidSnapshot
        case invalidDrivePageToken
        case requestFailed(Int)
        case sidecarPersistenceFailed(String)

        var errorDescription: String? {
            switch self {
            case .googleAuthorizationRequired:
                return "Google Drive authorization is required."
            case .firebaseAccountRequired:
                return "A Firebase account is required for metadata sync."
            case .firestoreUnavailable:
                return "Firestore is unavailable in this build."
            case .invalidSnapshot:
                return "The cloud snapshot is invalid."
            case .invalidDrivePageToken:
                return "Google Drive returned a repeated page token."
            case .requestFailed(let status):
                return "Google Drive returned HTTP \(status)."
            case .sidecarPersistenceFailed(let bookId):
                return "Could not persist PDF annotations for \(bookId)."
            }
        }
    }

    private func publish(status: String?) {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publishSignedOut(status: status)
            return
        }
        let user = Auth.auth().currentUser
        let providerIDs = Set(user?.providerData.map(\.providerID) ?? [])
        bridge?.updateAccountState(
            uid: user?.uid,
            displayName: user?.displayName,
            email: user?.email,
            photoUrl: user?.photoURL?.absoluteString,
            appleLinked: providerIDs.contains("apple.com"),
            googleLinked: providerIDs.contains("google.com"),
            googleDriveAuthorized: providerIDs.contains("google.com") && googleDriveAuthorized,
            status: status,
            authToken: nil
        )
        let publishedUID = user?.uid
        user?.getIDTokenForcingRefresh(false) { [weak self] token, _ in
            Task { @MainActor in
                guard let bridge = self?.bridge else { return }
                bridge.updateAccountAuthToken(authToken: token, expectedUid: publishedUID)
            }
        }
#else
        publishSignedOut(status: status)
#endif
    }

    private func publishSignedOut(status: String?) {
        bridge?.updateAccountState(
            uid: nil,
            displayName: nil,
            email: nil,
            photoUrl: nil,
            appleLinked: false,
            googleLinked: false,
            googleDriveAuthorized: false,
            status: status,
            authToken: nil
        )
    }

    private func detachAuthObserver() {
#if canImport(FirebaseAuth)
        if let authStateHandle {
            Auth.auth().removeStateDidChangeListener(authStateHandle)
            self.authStateHandle = nil
        }
#endif
    }

    deinit {
#if canImport(FirebaseAuth)
        if let authStateHandle {
            Auth.auth().removeStateDidChangeListener(authStateHandle)
        }
#endif
    }

    private static func randomNonce(length: Int = 32) -> String {
        precondition(length > 0)
        let characters = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var bytes = [UInt8](repeating: 0, count: 16)
            guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
                fatalError("Unable to generate a secure Sign in with Apple nonce.")
            }
            for byte in bytes where Int(byte) < characters.count {
                result.append(characters[Int(byte)])
                remaining -= 1
                if remaining == 0 { break }
            }
        }
        return result
    }

    private static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func presentingViewController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        return deepestPresentedViewController(from: root)
    }

    private static func deepestPresentedViewController(from root: UIViewController?) -> UIViewController? {
        if let presented = root?.presentedViewController {
            return deepestPresentedViewController(from: presented)
        }
        if let navigation = root as? UINavigationController {
            return deepestPresentedViewController(from: navigation.visibleViewController)
        }
        if let tabs = root as? UITabBarController {
            return deepestPresentedViewController(from: tabs.selectedViewController)
        }
        return root
    }

    private static func userFacingAuthError(_ error: Error, provider: String) -> String {
        let nsError = error as NSError
        if nsError.code == ASAuthorizationError.canceled.rawValue ||
            nsError.code == NSUserCancelledError {
            return "\(provider) sign-in cancelled."
        }
        return "\(provider) sign-in failed: \(error.localizedDescription)"
    }
}

extension LocalAccountController: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        Task { @MainActor in
#if canImport(FirebaseAuth)
            guard
                let appleCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let tokenData = appleCredential.identityToken,
                let idToken = String(data: tokenData, encoding: .utf8),
                let nonce = appleNonce
            else {
                publish(status: "Apple did not return a usable identity token.")
                return
            }
            appleNonce = nil
            let credential = OAuthProvider.appleCredential(
                withIDToken: idToken,
                rawNonce: nonce,
                fullName: appleCredential.fullName
            )
            do {
                try await signInOrLink(
                    credential: credential,
                    providerID: "apple.com",
                    providerLabel: "Apple"
                )
            } catch {
                publish(status: Self.userFacingAuthError(error, provider: "Apple"))
            }
#endif
        }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        Task { @MainActor in
            appleNonce = nil
            publish(status: Self.userFacingAuthError(error, provider: "Apple"))
        }
    }
}

extension LocalAccountController: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let windows = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
            guard let window = windows.first(where: \.isKeyWindow) else {
                fatalError("Sign in with Apple requires an active application window.")
            }
            return window
        }
    }
}
