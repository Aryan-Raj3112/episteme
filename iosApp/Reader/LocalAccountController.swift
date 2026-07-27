import AuthenticationServices
import Combine
import CryptoKit
import Foundation
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
@MainActor
final class LocalAccountController: NSObject, ObservableObject {
    private enum Provider {
        static let apple = "APPLE"
        static let google = "GOOGLE"
    }

    private static let googleDriveScope = "https://www.googleapis.com/auth/drive.appdata"
    private static let syncDeviceIDKey = "reader.ios.cloudSyncDeviceId.v1"

    private weak var bridge: ReaderIosBridge?
    private var appleNonce: String?
    private var googleDriveAuthorized = false

#if canImport(FirebaseAuth)
    private var authStateHandle: AuthStateDidChangeListenerHandle?
#endif

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
        observeAccount()
    }

    func handleOpenURL(_ url: URL) -> Bool {
#if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
#else
        return false
#endif
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

    private func syncCloudSnapshot(localJSON: String) async {
#if canImport(GoogleSignIn)
        do {
            let accessToken = try await googleDriveAccessToken()
            let remoteJSON = try await syncFirestoreBookMetadata(localJSON: localJSON)
            try await deleteCloudBookContentsForTombstones(
                snapshotJSON: localJSON,
                accessToken: accessToken
            )
            let downloaded = try await downloadMissingCloudBooks(
                localJSON: localJSON,
                remoteJSON: remoteJSON,
                accessToken: accessToken
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: remoteJSON,
                downloadedBookIds: downloaded.map(\.id),
                downloadedBookPaths: downloaded.map(\.path),
                status: downloaded.isEmpty
                    ? "Cloud reading progress downloaded."
                    : "Downloaded \(downloaded.count) cloud book(s)."
            )
        } catch {
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
        do {
            let accessToken = try await googleDriveAccessToken()
            try await uploadCloudBookContents(
                snapshotJSON: snapshotJSON,
                accessToken: accessToken
            )
            _ = try await syncFirestoreBookMetadata(localJSON: snapshotJSON)
            try await deleteCloudBookContentsForTombstones(
                snapshotJSON: snapshotJSON,
                accessToken: accessToken
            )
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud reading progress is up to date."
            )
        } catch {
            bridge?.completeCloudSync(
                remoteSnapshotJson: nil,
                downloadedBookIds: [],
                downloadedBookPaths: [],
                status: "Cloud upload failed: \(error.localizedDescription)"
            )
        }
#endif
    }

#if canImport(FirebaseFirestore) && canImport(FirebaseAuth)
    private func syncFirestoreBookMetadata(localJSON: String) async throws -> String {
        guard let uid = Auth.auth().currentUser?.uid else {
            throw CloudSyncError.firebaseAccountRequired
        }
        guard
            let data = localJSON.data(using: .utf8),
            var localRoot = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let localBooks = localRoot["books"] as? [[String: Any]]
        else {
            throw CloudSyncError.invalidSnapshot
        }

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

        let remoteBooks = effectiveDocuments.values.compactMap { fields -> [String: Any]? in
            guard (fields["isDeleted"] as? Bool) != true else { return nil }
            return snapshotBook(fromFirestore: fields)
        }
        localRoot["books"] = remoteBooks
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

    private func firestoreFields(from book: [String: Any]) -> [String: Any] {
        let position = book["readerPosition"] as? [String: Any]
        let readingTimestamp = numericInt64(book["readingPositionModifiedTimestamp"])
        let metadataTimestamp = numericInt64(book["metadataModifiedTimestamp"])
        let baseTimestamp = numericInt64(book["timestamp"])
        let highlights = book["readerHighlights"] as? [Any] ?? []
        let bookmarks = book["readerBookmarks"] as? [[String: Any]] ?? []
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
            "lastModifiedTimestamp": max(baseTimestamp, readingTimestamp, metadataTimestamp),
            "readingPositionModifiedTimestamp": readingTimestamp,
            "annotationModifiedTimestamp": readingTimestamp,
            "bookmarksJson": jsonStringValue(androidBookmarkPayload(bookmarks)),
            "originDeviceId": cloudSyncDeviceID(),
            "hasAnnotations": !highlights.isEmpty || !bookmarks.isEmpty,
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

    private func numericInt64(_ value: Any?) -> Int64 {
        (value as? NSNumber)?.int64Value ?? 0
    }

    private func optionalInt(_ value: Any?) -> Int? {
        (value as? NSNumber)?.intValue
    }

    private func cloudSyncDeviceID() -> String {
        if let existing = UserDefaults.standard.string(forKey: Self.syncDeviceIDKey) {
            return existing
        }
        let value = "ios-\(UUID().uuidString.lowercased())"
        UserDefaults.standard.set(value, forKey: Self.syncDeviceIDKey)
        return value
    }
#else
    private func syncFirestoreBookMetadata(localJSON: String) async throws -> String {
        throw CloudSyncError.firestoreUnavailable
    }
#endif

#if canImport(GoogleSignIn)
    private struct DriveFile: Decodable {
        let id: String
        let name: String
        let modifiedTime: String?
    }

    private struct DriveFileList: Decodable {
        let files: [DriveFile]
    }

    private struct CloudBook {
        let id: String
        let type: String
        let displayName: String
        let path: String?
        let sourceFolder: String?
        let fileContentModifiedTimestamp: Int64

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
        var components = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
        components.queryItems = [
            URLQueryItem(name: "spaces", value: "appDataFolder"),
            URLQueryItem(name: "q", value: "trashed = false"),
            URLQueryItem(name: "fields", value: "files(id,name,modifiedTime)"),
            URLQueryItem(name: "pageSize", value: "1000"),
        ]
        let data = try await driveRequest(
            url: components.url!,
            method: "GET",
            accessToken: accessToken
        )
        return try JSONDecoder().decode(DriveFileList.self, from: data).files
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
                fileContentModifiedTimestamp: (book["fileContentModifiedTimestamp"] as? NSNumber)?.int64Value ?? 0
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
        accessToken: String
    ) async throws {
        if let fileID {
            var components = URLComponents(
                string: "https://www.googleapis.com/upload/drive/v3/files/\(fileID)"
            )!
            components.queryItems = [URLQueryItem(name: "uploadType", value: "media")]
            _ = try await driveRequest(
                url: components.url!,
                method: "PATCH",
                accessToken: accessToken,
                contentType: "application/octet-stream",
                body: content
            )
            return
        }
        let boundary = "EpistemeContent-\(UUID().uuidString)"
        let metadata = "{\"name\":\(jsonString(name)),\"parents\":[\"appDataFolder\"]}"
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n\(metadata)\r\n".utf8)
        body.append(Data("--\(boundary)\r\nContent-Type: application/octet-stream\r\n\r\n".utf8))
        body.append(content)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        var components = URLComponents(string: "https://www.googleapis.com/upload/drive/v3/files")!
        components.queryItems = [URLQueryItem(name: "uploadType", value: "multipart")]
        _ = try await driveRequest(
            url: components.url!,
            method: "POST",
            accessToken: accessToken,
            contentType: "multipart/related; boundary=\(boundary)",
            body: body
        )
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
        case requestFailed(Int)

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
            case .requestFailed(let status):
                return "Google Drive returned HTTP \(status)."
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
            appleLinked: providerIDs.contains("apple.com"),
            googleLinked: providerIDs.contains("google.com"),
            googleDriveAuthorized: providerIDs.contains("google.com") && googleDriveAuthorized,
            status: status
        )
#else
        publishSignedOut(status: status)
#endif
    }

    private func publishSignedOut(status: String?) {
        bridge?.updateAccountState(
            uid: nil,
            displayName: nil,
            email: nil,
            appleLinked: false,
            googleLinked: false,
            googleDriveAuthorized: false,
            status: status
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
