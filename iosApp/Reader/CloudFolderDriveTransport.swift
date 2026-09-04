import Foundation

/// Drive transport for cloud-folder sync.
///
/// Android is the absolute benchmark and is NOT changed. This mirrors the Pro
/// `GoogleDriveRepository` cloud-folder gateway used by
/// `CloudFolderSyncWorker` (upload/download/list/delete under `appDataFolder`
/// with name + `appProperties` + payload-digest verification before consume).
/// Two deliberate transport differences, both behavior-preserving:
/// - Downloads stream to a temp file (`URLSession.download`) instead of RAM:
///   book files can be hundreds of MB; hashing/verification is identical.
/// - Content uploads use the resumable protocol (initiate + PUT from file)
///   instead of multipart-in-RAM; manifests stay multipart (small).
/// Immutable revisions mean objects are only ever created, never updated.
struct CloudFolderDriveObject {
    var fileId: String
    var name: String
    var modifiedTimeMillis: Int64
    var sizeBytes: Int64?
    var properties: [String: String]
}

enum CloudFolderTransportError: Error {
    /// Retry later (network, 429, 5xx, auth expiry). Outbox retry covers it.
    case transient(String)
    /// Do not retry this operation (bad request, forbidden, corrupt remote).
    case deterministic(String)
    /// The object is gone; callers re-read the remote state.
    case missing(String)
}

final class CloudFolderDriveTransport {
    private let accessToken: () async throws -> String
    private let session: URLSession

    init(accessToken: @escaping () async throws -> String, session: URLSession = .shared) {
        self.accessToken = accessToken
        self.session = session
    }

    // MARK: - primitives

    private static let filesBase = "https://www.googleapis.com/drive/v3/files"
    private static let uploadBase = "https://www.googleapis.com/upload/drive/v3/files"

    private func request(url: URL, method: String, token: String, contentType: String? = nil) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        return request
    }

    private func throwForStatus(_ status: Int, context: String) throws -> Never {
        switch status {
        case 404: throw CloudFolderTransportError.missing(context)
        case 401, 408, 429, 500, 502, 503, 504: throw CloudFolderTransportError.transient("\(context) http=\(status)")
        default: throw CloudFolderTransportError.deterministic("\(context) http=\(status)")
        }
    }

    private func decodeList(_ data: Data) -> (files: [[String: Any]], nextPage: String?) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return ([], nil) }
        return ((json["files"] as? [[String: Any]]) ?? [], json["nextPageToken"] as? String)
    }

    private func parseObject(_ json: [String: Any]) -> CloudFolderDriveObject? {
        guard let fileId = json["id"] as? String, !fileId.isEmpty,
              let name = json["name"] as? String else { return nil }
        var modified: Int64 = 0
        if let modifiedString = json["modifiedTime"] as? String {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            modified = (formatter.date(from: modifiedString) ?? ISO8601DateFormatter().date(from: modifiedString))
                .map { Int64($0.timeIntervalSince1970 * 1000) } ?? 0
        }
        var size: Int64?
        if let sizeString = json["size"] as? String { size = Int64(sizeString) }
        return CloudFolderDriveObject(
            fileId: fileId,
            name: name,
            modifiedTimeMillis: modified,
            sizeBytes: size,
            properties: (json["appProperties"] as? [String: String]) ?? [:]
        )
    }

    /// Paged `files.list` in `appDataFolder`.
    func listFiles(query: String, pageSize: Int = 1000) async throws -> [CloudFolderDriveObject] {
        let token = try await accessToken()
        var results: [CloudFolderDriveObject] = []
        var pageToken: String? = nil
        repeat {
            var components = URLComponents(string: Self.filesBase)!
            var items = [
                URLQueryItem(name: "q", value: query),
                URLQueryItem(name: "spaces", value: "appDataFolder"),
                URLQueryItem(name: "fields", value: "files(id,name,modifiedTime,appProperties,size),nextPageToken"),
                URLQueryItem(name: "pageSize", value: String(pageSize)),
            ]
            if let pageToken { items.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            components.queryItems = items
            let (data, response) = try await session.data(for: request(url: components.url!, method: "GET", token: token))
            guard let http = response as? HTTPURLResponse else {
                throw CloudFolderTransportError.transient("list: no response")
            }
            guard (200..<300).contains(http.statusCode) else { try throwForStatus(http.statusCode, context: "list") }
            let (files, next) = decodeList(data)
            results += files.compactMap(parseObject)
            pageToken = next
        } while pageToken != nil
        return results
    }

    /// All manifest refs grouped by logical root, newest revision wins
    /// (ties: smallest Drive ID), mirroring `listCloudFolderManifestRefs`.
    func listManifestRefs() async throws -> [String: CloudFolderDriveObject] {
        let files = try await listFiles(query: "trashed=false and name contains 'cloud-folder-v1-manifest-'")
        var refs: [String: (object: CloudFolderDriveObject, revision: Int64)] = [:]
        for file in files {
            let props = file.properties
            guard props[CloudFolderDriveProtocol.keySchema] == "1",
                  let rootId = props[CloudFolderDriveProtocol.keyRootID], !rootId.isEmpty,
                  props[CloudFolderDriveProtocol.keyNodeID] == CloudFolderDriveProtocol.manifestNodeID,
                  let revision = Int64(props[CloudFolderDriveProtocol.keyRevision] ?? ""), revision >= 0 else { continue }
            if let current = refs[rootId] {
                if revision > current.revision || (revision == current.revision && file.fileId < current.object.fileId) {
                    refs[rootId] = (file, revision)
                }
            } else {
                refs[rootId] = (file, revision)
            }
        }
        return refs.mapValues { $0.object }
    }

    func findManifestObjects(rootId: String) async throws -> [CloudFolderDriveObject] {
        let files = try await listFiles(
            query: "trashed=false and name contains \(CloudFolderDriveProtocol.queryLiteral(CloudFolderDriveProtocol.manifestPrefix(rootID: rootId)))"
        )
        return files.filter {
            $0.properties[CloudFolderDriveProtocol.keyRootID] == rootId &&
            $0.properties[CloudFolderDriveProtocol.keyNodeID] == CloudFolderDriveProtocol.manifestNodeID
        }
    }

    func findContentObjects(name: String) async throws -> [CloudFolderDriveObject] {
        let files = try await listFiles(
            query: "name=\(CloudFolderDriveProtocol.queryLiteral(name)) and trashed=false",
            pageSize: 100
        )
        return files.sorted { $0.fileId < $1.fileId }
    }

    func getObject(fileId: String) async throws -> CloudFolderDriveObject {
        let token = try await accessToken()
        var components = URLComponents(string: "\(Self.filesBase)/\(fileId)")!
        components.queryItems = [URLQueryItem(name: "fields", value: "id,name,modifiedTime,appProperties,size")]
        let (data, response) = try await session.data(for: request(url: components.url!, method: "GET", token: token))
        guard let http = response as? HTTPURLResponse else {
            throw CloudFolderTransportError.transient("get: no response")
        }
        guard (200..<300).contains(http.statusCode) else { try throwForStatus(http.statusCode, context: "get") }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let object = parseObject(json) else {
            throw CloudFolderTransportError.deterministic("get: undecodable metadata")
        }
        return object
    }

    /// Stream download to `destination` (caller verifies hash + size after).
    func downloadToFile(fileId: String, destination: URL) async throws {
        let token = try await accessToken()
        var components = URLComponents(string: "\(Self.filesBase)/\(fileId)")!
        components.queryItems = [URLQueryItem(name: "alt", value: "media")]
        let (tempURL, response): (URL, URLResponse)
        do {
            (tempURL, response) = try await session.download(for: request(url: components.url!, method: "GET", token: token))
        } catch {
            throw CloudFolderTransportError.transient("download: \(error.localizedDescription)")
        }
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            try? FileManager.default.removeItem(at: tempURL)
            try throwForStatus(status, context: "download")
        }
        try? FileManager.default.removeItem(at: destination)
        do {
            try FileManager.default.moveItem(at: tempURL, to: destination)
        } catch {
            throw CloudFolderTransportError.transient("download stage: \(error.localizedDescription)")
        }
    }

    /// Small multipart create (manifests). Returns the new file ID.
    func uploadManifest(name: String, data: Data, metadata: [String: String]) async throws -> String {
        let token = try await accessToken()
        let boundary = "EpistemeManifest-\(UUID().uuidString)"
        let metaJson = (try? JSONSerialization.data(withJSONObject: ["name": name, "parents": ["appDataFolder"], "appProperties": metadata]))
            ?? Data()
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)
        body.append(metaJson)
        body.append(Data("\r\n--\(boundary)\r\nContent-Type: application/json\r\n\r\n".utf8))
        body.append(data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        var components = URLComponents(string: Self.uploadBase)!
        components.queryItems = [
            URLQueryItem(name: "uploadType", value: "multipart"),
            URLQueryItem(name: "fields", value: "id"),
        ]
        var urlRequest = request(
            url: components.url!, method: "POST", token: token,
            contentType: "multipart/related; boundary=\(boundary)"
        )
        urlRequest.httpBody = body
        let (responseData, response): (Data, URLResponse)
        do {
            (responseData, response) = try await session.data(for: urlRequest)
        } catch {
            throw CloudFolderTransportError.transient("manifest upload: \(error.localizedDescription)")
        }
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let json = try? JSONSerialization.jsonObject(with: responseData) as? [String: Any],
              let fileId = json["id"] as? String, !fileId.isEmpty else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            try throwForStatus(status, context: "manifest upload")
        }
        return fileId
    }

    /// Resumable upload from a local file (book bytes). Returns the new file ID.
    func uploadContentFromFile(name: String, fileURL: URL, mimeType: String?, metadata: [String: String]) async throws -> String {
        let token = try await accessToken()
        let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path)
        let fileSize = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        // 1. Initiate the session.
        var initiateComponents = URLComponents(string: Self.uploadBase)!
        initiateComponents.queryItems = [
            URLQueryItem(name: "uploadType", value: "resumable"),
            URLQueryItem(name: "fields", value: "id"),
        ]
        var initiate = request(
            url: initiateComponents.url!, method: "POST", token: token,
            contentType: "application/json; charset=UTF-8"
        )
        initiate.setValue(String(fileSize), forHTTPHeaderField: "X-Upload-Content-Length")
        initiate.setValue(mimeType ?? "application/octet-stream", forHTTPHeaderField: "X-Upload-Content-Type")
        let metaJson = (try? JSONSerialization.data(withJSONObject: ["name": name, "parents": ["appDataFolder"], "appProperties": metadata])) ?? Data()
        initiate.httpBody = metaJson
        let sessionResponse: (data: Data, response: URLResponse)
        do {
            let (data, response) = try await session.data(for: initiate)
            sessionResponse = (data, response)
        } catch {
            throw CloudFolderTransportError.transient("resumable initiate: \(error.localizedDescription)")
        }
        guard let http = sessionResponse.response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              let sessionURL = (http.allHeaderFields["Location"] as? String).flatMap(URL.init(string:)) else {
            let status = (sessionResponse.response as? HTTPURLResponse)?.statusCode ?? -1
            try throwForStatus(status, context: "resumable initiate")
        }
        // 2. PUT the bytes from disk in one request (valid resumable usage;
        //    outbox retry covers failures, so no chunk resume bookkeeping).
        var put = URLRequest(url: sessionURL)
        put.httpMethod = "PUT"
        put.setValue(mimeType ?? "application/octet-stream", forHTTPHeaderField: "Content-Type")
        put.setValue(String(fileSize), forHTTPHeaderField: "Content-Length")
        let putResponse: (data: Data, response: URLResponse)
        do {
            let (data, response) = try await session.upload(for: put, fromFile: fileURL)
            putResponse = (data, response)
        } catch {
            throw CloudFolderTransportError.transient("resumable put: \(error.localizedDescription)")
        }
        guard let putHttp = putResponse.response as? HTTPURLResponse,
              (200..<300).contains(putHttp.statusCode),
              let json = try? JSONSerialization.jsonObject(with: putResponse.data) as? [String: Any],
              let fileId = json["id"] as? String, !fileId.isEmpty else {
            let status = (putResponse.response as? HTTPURLResponse)?.statusCode ?? -1
            try throwForStatus(status, context: "resumable put")
        }
        return fileId
    }

    /// Delete after re-checking name + properties, mirroring Android's
    /// `deleteCloudFolderObject` gateway guard. Missing counts as success.
    func deleteObjectVerified(expected: CloudFolderDriveObject) async throws {
        let current: CloudFolderDriveObject
        do {
            current = try await getObject(fileId: expected.fileId)
        } catch CloudFolderTransportError.missing {
            return
        }
        guard current.name == expected.name, current.properties == expected.properties else { return }
        let token = try await accessToken()
        let url = URL(string: "\(Self.filesBase)/\(expected.fileId)")!
        do {
            let (_, response) = try await session.data(for: request(url: url, method: "DELETE", token: token))
            guard let http = response as? HTTPURLResponse else {
                throw CloudFolderTransportError.transient("delete: no response")
            }
            // Already gone counts as success.
            if http.statusCode == 404 { return }
            guard (200..<300).contains(http.statusCode) else {
                try throwForStatus(http.statusCode, context: "delete")
            }
        } catch let error as CloudFolderTransportError {
            throw error
        } catch {
            throw CloudFolderTransportError.transient("delete: \(error.localizedDescription)")
        }
    }
}
