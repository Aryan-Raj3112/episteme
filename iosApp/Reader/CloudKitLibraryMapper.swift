import Foundation
import ReaderShared

/// Pure-Swift snapshot mapping for CloudKit library sync (Pro).
///
/// No CloudKit import here so the mapping stays testable without entitlements.
/// LWW truth mirrors shared `CloudSyncDecisions` (same comparator Android and
/// the Drive path consume); record/zone names mirror shared
/// `CloudKitLibrarySync` (`LibraryZone`, `BookState`, `BookContent`, ...).
/// Transport (`CloudKitLibraryTransport`) owns CKRecord/CKAsset I/O.
enum CloudKitLibraryMapper {
    struct BookClocks {
        var bookId: String
        var lastModified: Int64
        var readingPositionModified: Int64
        var annotationModified: Int64
        var fileContentModified: Int64
        var sidecarModified: Int64
        var localFileAvailable: Bool
    }

    struct DirtySets {
        var metadataBookIds: Set<String> = []
        var sidecarBookIds: Set<String> = []
        var contentBookIds: Set<String> = []
        var tombstoneBookIds: Set<String> = []

        var isEmpty: Bool {
            metadataBookIds.isEmpty && sidecarBookIds.isEmpty &&
            contentBookIds.isEmpty && tombstoneBookIds.isEmpty
        }
    }

    static func parseBooks(_ snapshotJSON: String) -> [[String: Any]] {
        guard let data = snapshotJSON.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let books = root["books"] as? [[String: Any]] else { return [] }
        return books
    }

    static func parseTombstones(_ snapshotJSON: String) -> [[String: Any]] {
        guard let data = snapshotJSON.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tombstones = root["bookTombstones"] as? [[String: Any]] else { return [] }
        return tombstones
    }

    static func numeric(_ value: Any?) -> Int64 {
        (value as? NSNumber)?.int64Value ?? 0
    }

    /// Hot-vs-cold classification. Metadata uses the shared LWW winner
    /// (sidecar clock folded in); content uses the shared content comparator
    /// so unchanged bytes never trigger an asset save.
    static func classifyDirty(
        localBooks: [[String: Any]],
        remoteStateById: [String: [String: Any]],
        remoteContentModifiedById: [String: Int64],
        localSidecarTimestampById: [String: Int64],
        localFileAvailableById: [String: Bool]
    ) -> DirtySets {
        var sets = DirtySets()
        for book in localBooks {
            guard let bookId = book["id"] as? String, !bookId.isEmpty else { continue }
            let lastModified = max(
                numeric(book["timestamp"]),
                numeric(book["metadataModifiedTimestamp"]),
                numeric(book["readingPositionModifiedTimestamp"]),
                numeric(book["annotationModifiedTimestamp"])
            )
            let sidecarModified = localSidecarTimestampById[bookId]
                ?? numeric(book["annotationModifiedTimestamp"])
            let fileContentModified = numeric(book["fileContentModifiedTimestamp"])
            let remote = remoteStateById[bookId]
            let remoteModified = numeric(remote?["lastModifiedTimestamp"])
            let remoteContentModified = remoteContentModifiedById[bookId]
            if remote == nil || CloudSyncDecisionsKt.shouldUploadLocalCloudBookUpdate(
                localModifiedTimestamp: lastModified,
                remoteModifiedTimestamp: remoteModified,
                localSidecarModifiedTimestamp: sidecarModified
            ) {
                sets.metadataBookIds.insert(bookId)
            }
            if sidecarModified > 0 && (remote == nil || CloudSyncDecisionsKt.shouldUploadLocalCloudBookUpdate(
                localModifiedTimestamp: sidecarModified,
                remoteModifiedTimestamp: remoteModified,
                localSidecarModifiedTimestamp: 0
            )) {
                sets.sidecarBookIds.insert(bookId)
            }
            if CloudSyncDecisionsKt.shouldUploadLocalCloudBookContent(
                localFileAvailable: localFileAvailableById[bookId] ?? false,
                localContentModifiedTimestamp: fileContentModified,
                remoteContentModifiedTimestamp: remoteContentModified.map { KotlinLong(longLong: $0) }
            ) {
                sets.contentBookIds.insert(bookId)
            }
        }
        return sets
    }

    /// Small `BookState` fields. Assets never ride on this record.
    static func bookStateFields(
        book: [String: Any],
        sidecarTimestamp: Int64,
        deviceId: String
    ) -> [String: Any] {
        let position = book["readerPosition"] as? [String: Any]
        return [
            "bookId": book["id"] as? String ?? "",
            "displayName": book["displayName"] as? String ?? "",
            "type": book["type"] as? String ?? "",
            "title": book["title"] ?? NSNull(),
            "author": book["author"] ?? NSNull(),
            "lastPositionCfi": (position?["cfi"] as? String) ?? NSNull(),
            "lastChapterIndex": position?["chapterIndex"] ?? NSNull(),
            "locatorBlockIndex": position?["blockIndex"] ?? NSNull(),
            "locatorCharOffset": position?["charOffset"] ?? NSNull(),
            "lastPage": book["lastPageIndex"] ?? NSNull(),
            "progressPercentage": book["progressPercentage"] ?? NSNull(),
            "isRecent": book["isRecent"] as? Bool ?? true,
            "isDeleted": false,
            "lastModifiedTimestamp": max(
                numeric(book["timestamp"]),
                numeric(book["readingPositionModifiedTimestamp"]),
                numeric(book["metadataModifiedTimestamp"]),
                numeric(book["annotationModifiedTimestamp"]),
                sidecarTimestamp
            ),
            "readingPositionModifiedTimestamp": numeric(book["readingPositionModifiedTimestamp"]),
            "annotationModifiedTimestamp": max(
                numeric(book["annotationModifiedTimestamp"]), sidecarTimestamp
            ),
            "bookmarksJson": jsonString(book["readerBookmarks"] ?? []),
            "highlightsJson": jsonString(book["readerHighlights"] ?? []),
            "hasAnnotations": book["hasAnnotations"] as? Bool ?? false,
            "fileContentModifiedTimestamp": numeric(book["fileContentModifiedTimestamp"]),
            "originDeviceId": deviceId,
        ]
    }

    static func tombstoneFields(bookId: String, deletedAt: Int64, type: String?, deviceId: String) -> [String: Any] {
        [
            "bookId": bookId,
            "type": type ?? "",
            "isDeleted": true,
            "lastModifiedTimestamp": deletedAt,
            "readingPositionModifiedTimestamp": 0,
            "annotationModifiedTimestamp": 0,
            "originDeviceId": deviceId,
        ]
    }

    static func jsonString(_ value: Any) -> String {
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value),
              let string = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return string
    }
}
