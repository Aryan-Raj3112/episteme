import CryptoKit
import Foundation

/// Drive naming + metadata for cloud-folder sync.
///
/// Android is the absolute benchmark and is NOT changed. This mirrors
/// `app/src/main/java/com/aryan/reader/data/CloudFolderDriveProtocol.kt`
/// byte-for-byte: `seg` is SHA-256 hex of trimmed UTF-8 truncated to 32
/// chars; manifest/content object names, `appProperties` keys, and the
/// `sha256:hex` content-hash spelling must match or iOS cannot read Android's
/// objects (and vice versa). Manifest JSON itself always goes through the
/// shared `encodeCloudFolderManifest` codec — never hand-rolled here.
enum CloudFolderDriveProtocol {
    static let manifestNodeID = "manifest"

    static let keySchema = "cloudFolderSchema"
    static let keyRootID = "cloudFolderRootId"
    static let keyNodeID = "cloudFolderNodeId"
    static let keyRevision = "cloudFolderRevision"
    static let keyContentHash = "cloudFolderContentHash"
    static let keyContentSize = "cloudFolderContentSize"

    /// `seg`: SHA-256 hex (lowercase) of trimmed UTF-8, first 32 chars.
    static func segment(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.trimmingCharacters(in: .whitespacesAndNewlines).utf8))
        return digest.map { String(format: "%02x", $0) }.joined().prefix(32).lowercased()
    }

    static func manifestPrefix(rootID: String) -> String {
        "cloud-folder-v1-manifest-\(segment(rootID))"
    }

    /// New writes must use this exact name.
    static func manifestName(rootID: String, revision: Int64, manifestHash: String) -> String {
        "\(manifestPrefix(rootID: rootID))-r\(max(revision, 0))-\(segment(manifestHash)).json"
    }

    /// New writes must use this exact name.
    static func contentName(rootID: String, nodeID: String, contentHash: String, revision: Int64) -> String {
        "cloud-folder-v1-content-\(segment(rootID))-\(segment(nodeID))-r\(max(revision, 0))-\(segment(contentHash))"
    }

    /// `appProperties` for a manifest object. Never stores the relative path.
    static func manifestMetadata(rootID: String, revision: Int64, manifestHash: String, sizeBytes: Int) -> [String: String] {
        [
            keySchema: "1",
            keyRootID: rootID.trimmingCharacters(in: .whitespacesAndNewlines),
            keyNodeID: manifestNodeID,
            keyRevision: String(max(revision, 0)),
            keyContentHash: canonicalHash(manifestHash) ?? "",
            keyContentSize: String(max(sizeBytes, 0)),
        ]
    }

    /// `appProperties` for a content object.
    static func contentMetadata(
        rootID: String,
        nodeID: String,
        revision: Int64,
        contentHash: String?,
        sizeBytes: Int64
    ) -> [String: String] {
        var metadata = [
            keySchema: "1",
            keyRootID: rootID.trimmingCharacters(in: .whitespacesAndNewlines),
            keyNodeID: nodeID.trimmingCharacters(in: .whitespacesAndNewlines),
            keyRevision: String(max(revision, 0)),
        ]
        if let hash = canonicalHash(contentHash), !hash.isEmpty {
            metadata[keyContentHash] = hash
        }
        metadata[keyContentSize] = String(max(sizeBytes, 0))
        return metadata
    }

    /// Lowercase `sha256:hex` canonical form (or bare hex accepted as-is).
    static func canonicalHash(_ hash: String?) -> String? {
        guard let raw = hash?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !raw.isEmpty else { return nil }
        if raw.hasPrefix("sha256:") { return raw }
        return raw
    }

    static func isSHA256(_ hash: String?) -> Bool {
        guard let canonical = canonicalHash(hash) else { return false }
        let hex = canonical.hasPrefix("sha256:") ? String(canonical.dropFirst(7)) : canonical
        return hex.count == 64 && hex.allSatisfy { $0.isHexDigit && ($0.isNumber || "abcdef".contains($0)) }
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    /// Strict match for current objects: schema + root + node + revision, plus
    /// hash/size when the expectation carries them.
    static func metadataMatches(
        _ properties: [String: String],
        rootID: String,
        nodeID: String,
        revision: Int64,
        contentHash: String? = nil,
        sizeBytes: Int64? = nil
    ) -> Bool {
        guard properties[keySchema] == "1",
              properties[keyRootID] == rootID,
              properties[keyNodeID] == nodeID,
              properties[keyRevision] == String(max(revision, 0)) else { return false }
        if let expected = canonicalHash(contentHash), !expected.isEmpty {
            guard canonicalHash(properties[keyContentHash]) == expected else { return false }
        }
        if let expectedSize = sizeBytes {
            guard properties[keyContentSize] == String(max(expectedSize, 0)) else { return false }
        }
        return true
    }

    /// Drive `q` escaping for name literals.
    static func queryLiteral(_ value: String) -> String {
        "'\(value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'"))'"
    }
}
