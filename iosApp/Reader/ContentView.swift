//
//  ContentView.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import SwiftUI
import ReaderShared
import UniformTypeIdentifiers
import CryptoKit

struct ContentView: View {
    private enum ImportKind { case books, folder, fonts, cover }

    private let bridge = ReaderIosBridge()
    private let audiobookPlayer = AudiobookPlayerController()
    @StateObject private var localStoreKit = LocalStoreKitController()
    @StateObject private var localAccount = LocalAccountController()
    @Environment(\.scenePhase) private var scenePhase
    @State private var isImportPickerPresented = false
    @State private var importKind: ImportKind = .books
    @State private var isReaderSystemUiHidden = false

    var body: some View {
        ReaderComposeHost(
            bridge: bridge,
            isSystemUiHidden: $isReaderSystemUiHidden,
            onImportBooks: {
                importKind = .books
                isImportPickerPresented = true
            },
            onImportFolder: {
                importKind = .folder
                isImportPickerPresented = true
            },
            onRefreshFolders: {
                refreshImportedFolders(bridge: bridge)
            },
            onImportFonts: {
                importKind = .fonts
                isImportPickerPresented = true
            },
            onImportCover: {
                importKind = .cover
                isImportPickerPresented = true
            },
            onRemoveFolder: { folderName in
                removeImportedFolder(named: folderName)
            }
        )
        .ignoresSafeArea()
        .statusBarHidden(isReaderSystemUiHidden)
        .persistentSystemOverlays(isReaderSystemUiHidden ? .hidden : .visible)
        .fileImporter(
            isPresented: $isImportPickerPresented,
            allowedContentTypes: importKind == .fonts
                ? [.font]
                : (
                    importKind == .folder
                        ? [.folder]
                        : (
                            importKind == .cover
                                ? [.image]
                                : allowedReaderImportTypes
                        )
                ),
            allowsMultipleSelection: importKind != .folder && importKind != .cover
        ) { result in
            switch result {
            case .success(let urls):
                if importKind == .folder, let folderURL = urls.first {
                    let folderName = rememberImportedFolder(folderURL, bridge: bridge)
                    recordImportedFolderScan(
                        bridge: bridge,
                        folderName: folderName,
                        scan: copyImportedFolderToAppSupport(folderURL, folderName: folderName)
                    )
                    return
                }
                let importedFiles = urls.compactMap { url in
                    copyImportedFileToAppSupport(
                        url,
                        directoryName: importKind == .fonts ? "Fonts" : (importKind == .cover ? "Covers" : "Imports")
                    )
                }
                if importKind == .fonts {
                    bridge.recordImportedFonts(fileNames: importedFiles.map(\.name), filePaths: importedFiles.map(\.path))
                } else if importKind == .cover {
                    bridge.recordImportedCover(filePath: importedFiles.first?.path)
                } else {
                    bridge.recordImportedFiles(
                        fileNames: importedFiles.map(\.name),
                        filePaths: importedFiles.map(\.path),
                        contentIds: importedFiles.map(\.contentId),
                        failedCount: Int32(urls.count - importedFiles.count),
                        wasCancelled: false,
                        autoOpen: true
                    )
                }
            case .failure(let error):
                let nsError = error as NSError
                let wasCancelled = error is CancellationError ||
                    (nsError.domain == NSCocoaErrorDomain && nsError.code == NSUserCancelledError)
                if importKind == .fonts {
                    bridge.recordImportedFonts(fileNames: [], filePaths: [])
                } else if importKind == .cover {
                    bridge.recordImportedCover(filePath: nil)
                } else if importKind == .folder {
                    bridge.recordImportedFolder(
                        folderName: "folder",
                        fileNames: [],
                        filePaths: [],
                        contentIds: [],
                        relativePaths: [],
                        fileSizes: [],
                        lastModifiedTimestamps: [],
                        scanSucceeded: false
                    )
                } else {
                    bridge.recordImportedFiles(
                        fileNames: [],
                        filePaths: [],
                        contentIds: [],
                        failedCount: wasCancelled ? 0 : 1,
                        wasCancelled: wasCancelled,
                        autoOpen: true
                    )
                }
            }
        }
        .onOpenURL { url in
            if !localAccount.handleOpenURL(url) {
                handleExternalURL(url)
            }
        }
        .task {
            localStoreKit.attach(to: bridge)
            localAccount.attach(to: bridge)
            let legacyPaths = bridge.importedFilePathsMissingContentId()
            let legacyContentIds = legacyPaths.map {
                sha256FileId(URL(fileURLWithPath: $0)) ?? ""
            }
            bridge.backfillImportedContentIds(
                filePaths: legacyPaths,
                contentIds: legacyContentIds
            )
            bridge.setFolderFileDeletionHandler { folderName, managedPaths in
                deleteImportedFolderFiles(folderName: folderName, managedPaths: managedPaths)
            }
            bridge.setFolderFileReplacementHandler { folderName, managedPath in
                replaceImportedFolderFile(folderName: folderName, managedPath: managedPath)
            }
            bridge.setFolderFileAdditionHandler { folderName, sourcePath, fileName in
                addImportedFolderFile(folderName: folderName, sourcePath: sourcePath, fileName: fileName)
            }
            audiobookPlayer.onPlaybackUpdate = { isPlaying, isLoading, positionMs, durationMs, speed, sleepTimerRemainingMs, error in
                bridge.updateAudiobookPlaybackState(
                    isPlaying: isPlaying,
                    isLoading: isLoading,
                    positionMs: positionMs,
                    durationMs: durationMs,
                    speed: speed,
                    sleepTimerRemainingMs: sleepTimerRemainingMs,
                    error: error
                )
            }
            bridge.setAudiobookPlayHandler { filePath, positionMs, speed in
                audiobookPlayer.play(
                    filePath: filePath,
                    positionMs: positionMs.doubleValue,
                    speed: speed.doubleValue
                )
            }
            bridge.setAudiobookPauseHandler {
                audiobookPlayer.pause()
            }
            bridge.setAudiobookSpeedAndResumeHandler { speed in
                audiobookPlayer.resume(speed: speed.floatValue)
            }
            bridge.setAudiobookSeekHandler { positionMs in
                audiobookPlayer.seek(to: positionMs.doubleValue)
            }
            bridge.setAudiobookSpeedHandler { speed in
                audiobookPlayer.setSpeed(speed.doubleValue)
            }
            bridge.setAudiobookSleepTimerHandler { minutes in
                audiobookPlayer.setSleepTimer(minutes: Int(minutes.intValue))
            }
            bridge.setAudiobookCancelSleepHandler {
                audiobookPlayer.cancelSleepTimer()
            }
            bridge.setAudiobookStopHandler {
                audiobookPlayer.stop()
            }
            bridge.setAudiobookMetadataHandler { filePath, fallbackTitle, completion in
                audiobookPlayer.extractMetadata(
                    filePath: filePath,
                    fallbackTitle: fallbackTitle
                ) { title, author, album, durationMs in
                    completion(title, author, album, KotlinLong(longLong: durationMs))
                }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                bridge.updateAppActive(active: true)
                refreshImportedFolders(bridge: bridge)
            } else {
                bridge.updateAppActive(active: false)
            }
        }
    }

    private var allowedReaderImportTypes: [UTType] {
        guard bridge.usesStrictFileFilter() else { return [.item] }
        let types = bridge.readableFileExtensions().compactMap {
            UTType(filenameExtension: $0)
        }
        return types.isEmpty ? [.item] : types
    }

    private func handleExternalURL(_ url: URL) {
        openExternalURL(url, addToLibrary: bridge.shouldAddExternalFileToLibrary())
    }

    private func openExternalURL(_ url: URL, addToLibrary: Bool) {
        let imported = addToLibrary
            ? copyImportedFileToAppSupport(url, directoryName: "Imports")
            : copyExternalFileToTemporaryStorage(url)
        guard let imported else {
            bridge.recordNativeEvent(message: "Could not open the external file")
            return
        }
        bridge.openExternalFile(
            fileName: imported.name,
            filePath: imported.path,
            contentId: imported.contentId,
            addToLibrary: addToLibrary
        )
    }
}

private let importedFolderBookmarksKey = "reader.ios.importedFolderBookmarks.v1"

@discardableResult
private func rememberImportedFolder(_ url: URL, bridge: ReaderIosBridge) -> String {
    let baseName = url.lastPathComponent
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    do {
        let bookmark = try url.bookmarkData(
            options: [.minimalBookmark],
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )
        var matchedFolderName: String?
        var shouldRefreshMatch = false
        for (folderName, existingBookmark) in bookmarks {
            var isStale = false
            if existingBookmark == bookmark || (try? URL(
                resolvingBookmarkData: existingBookmark,
                options: [.withoutUI],
                relativeTo: nil,
                bookmarkDataIsStale: &isStale
            ).standardizedFileURL) == url.standardizedFileURL {
                matchedFolderName = folderName
                shouldRefreshMatch = isStale
                break
            }
        }
        if let matchedFolderName {
            if shouldRefreshMatch { bookmarks[matchedFolderName] = bookmark }
            UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
            return matchedFolderName
        }
        let folderName = bridge.availableImportedFolderName(
            preferredName: baseName,
            existingNames: Array(bookmarks.keys)
        )
        bookmarks[folderName] = bookmark
        UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
        return folderName
    } catch {
        // The managed copy remains usable if a document provider cannot issue a bookmark.
        return bridge.availableImportedFolderName(
            preferredName: baseName,
            existingNames: Array(bookmarks.keys)
        )
    }
}

private func updateImportedFolderBookmark(_ url: URL, folderName: String) {
    guard let bookmark = try? url.bookmarkData(
        options: [.minimalBookmark],
        includingResourceValuesForKeys: nil,
        relativeTo: nil
    ) else { return }
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    bookmarks[folderName] = bookmark
    UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
}

private func refreshImportedFolders(bridge: ReaderIosBridge) {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    for (folderName, bookmark) in bookmarks {
        var isStale = false
        guard let folderURL = try? URL(
            resolvingBookmarkData: bookmark,
            options: [.withoutUI],
            relativeTo: nil,
            bookmarkDataIsStale: &isStale
        ) else {
            recordImportedFolderScan(
                bridge: bridge,
                folderName: folderName,
                scan: ImportedFolderScan(files: [], succeeded: false)
            )
            continue
        }
        if isStale {
            updateImportedFolderBookmark(folderURL, folderName: folderName)
        }
        recordImportedFolderScan(
            bridge: bridge,
            folderName: folderName,
            scan: copyImportedFolderToAppSupport(folderURL, folderName: folderName)
        )
    }
}

private func removeImportedFolder(named folderName: String) {
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    bookmarks.removeValue(forKey: folderName)
    UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
    guard let appSupport = try? FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
    ) else {
        return
    }
    let managedFolder = appSupport
        .appendingPathComponent("LocalFolders", isDirectory: true)
        .appendingPathComponent(safeLocalFolderName(folderName), isDirectory: true)
    if FileManager.default.fileExists(atPath: managedFolder.path) {
        try? FileManager.default.removeItem(at: managedFolder)
    }
}

private func deleteImportedFolderFiles(folderName: String, managedPaths: [String]) {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    guard let bookmark = bookmarks[folderName] else { return }
    var isStale = false
    guard let sourceRoot = try? URL(
        resolvingBookmarkData: bookmark,
        options: [.withoutUI],
        relativeTo: nil,
        bookmarkDataIsStale: &isStale
    ), let appSupport = try? FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
    ) else {
        return
    }
    if isStale {
        updateImportedFolderBookmark(sourceRoot, folderName: folderName)
    }

    let managedRoot = appSupport
        .appendingPathComponent("LocalFolders", isDirectory: true)
        .appendingPathComponent(safeLocalFolderName(folderName), isDirectory: true)
        .standardizedFileURL
    let sourceRootPath = sourceRoot.standardizedFileURL.path
    let managedRootPath = managedRoot.path
    let didStartAccessing = sourceRoot.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceRoot.stopAccessingSecurityScopedResource()
        }
    }

    for managedPath in managedPaths {
        let managedURL = URL(fileURLWithPath: managedPath).standardizedFileURL
        guard managedURL.path.hasPrefix(managedRootPath + "/") else { continue }
        let relativePath = String(managedURL.path.dropFirst(managedRootPath.count + 1))
        let sourceURL = sourceRoot.appendingPathComponent(relativePath).standardizedFileURL
        guard sourceURL.path.hasPrefix(sourceRootPath + "/") else { continue }
        try? FileManager.default.removeItem(at: sourceURL)
        try? FileManager.default.removeItem(at: managedURL)
    }
}

private func addImportedFolderFile(folderName: String, sourcePath: String, fileName: String) -> String? {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    guard let bookmark = bookmarks[folderName] else { return nil }
    var isStale = false
    guard let sourceRoot = try? URL(
        resolvingBookmarkData: bookmark,
        options: [.withoutUI],
        relativeTo: nil,
        bookmarkDataIsStale: &isStale
    ), let appSupport = try? FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
    ) else {
        return nil
    }
    if isStale {
        updateImportedFolderBookmark(sourceRoot, folderName: folderName)
    }

    let managedRoot = appSupport
        .appendingPathComponent("LocalFolders", isDirectory: true)
        .appendingPathComponent(safeLocalFolderName(folderName), isDirectory: true)
    let sourceURL = URL(fileURLWithPath: sourcePath)
    let uniqueName = uniqueImportedFolderFileName(
        sourceRoot: sourceRoot.standardizedFileURL,
        managedRoot: managedRoot.standardizedFileURL,
        preferredName: fileName
    )

    let didStartAccessing = sourceRoot.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceRoot.stopAccessingSecurityScopedResource()
        }
    }
    do {
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: managedRoot, withIntermediateDirectories: true)
        let managedURL = managedRoot.appendingPathComponent(uniqueName)
        try fileManager.copyItem(at: sourceURL, to: managedURL)
        try? fileManager.copyItem(at: sourceURL, to: sourceRoot.appendingPathComponent(uniqueName))
        return managedURL.path
    } catch {
        try? FileManager.default.removeItem(at: managedRoot.appendingPathComponent(uniqueName))
        return nil
    }
}

private func uniqueImportedFolderFileName(
    sourceRoot: URL,
    managedRoot: URL,
    preferredName: String
) -> String {
    let stem = (preferredName as NSString).deletingPathExtension
    let fileExtension = (preferredName as NSString).pathExtension
    var candidate = preferredName
    var suffix = 1
    func exists(_ url: URL) -> Bool {
        FileManager.default.fileExists(atPath: url.path)
    }
    while exists(sourceRoot.appendingPathComponent(candidate)) || exists(managedRoot.appendingPathComponent(candidate)) {
        candidate = fileExtension.isEmpty
            ? "\(stem)_\(suffix)"
            : "\(stem)_\(suffix).\(fileExtension)"
        suffix += 1
    }
    return candidate
}

private func replaceImportedFolderFile(folderName: String, managedPath: String) -> String? {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    guard let bookmark = bookmarks[folderName] else { return nil }
    var isStale = false
    guard let sourceRoot = try? URL(
        resolvingBookmarkData: bookmark,
        options: [.withoutUI],
        relativeTo: nil,
        bookmarkDataIsStale: &isStale
    ), let appSupport = try? FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
    ) else {
        return nil
    }
    if isStale {
        updateImportedFolderBookmark(sourceRoot, folderName: folderName)
    }

    let managedRoot = appSupport
        .appendingPathComponent("LocalFolders", isDirectory: true)
        .appendingPathComponent(safeLocalFolderName(folderName), isDirectory: true)
        .standardizedFileURL
    let managedURL = URL(fileURLWithPath: managedPath).standardizedFileURL
    guard managedURL.path.hasPrefix(managedRoot.path + "/") else { return nil }
    let relativePath = String(managedURL.path.dropFirst(managedRoot.path.count + 1))
    let sourceURL = sourceRoot.appendingPathComponent(relativePath).standardizedFileURL
    guard sourceURL.path.hasPrefix(sourceRoot.standardizedFileURL.path + "/") else { return nil }

    let didStartAccessing = sourceRoot.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceRoot.stopAccessingSecurityScopedResource()
        }
    }
    let temporaryURL = sourceURL.deletingLastPathComponent()
        .appendingPathComponent(".reader-\(UUID().uuidString).tmp")
    do {
        let fileManager = FileManager.default
        try fileManager.copyItem(at: managedURL, to: temporaryURL)
        _ = try fileManager.replaceItemAt(sourceURL, withItemAt: temporaryURL)
        let values = try sourceURL.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
        let fileSize = Int64(values.fileSize ?? 0)
        let modifiedAt = Int64((values.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000)
        return "\(fileSize)\t\(modifiedAt)"
    } catch {
        try? FileManager.default.removeItem(at: temporaryURL)
        return nil
    }
}

private struct ImportedFolderScan {
    let files: [ImportedReaderFile]
    let succeeded: Bool
}

private func recordImportedFolderScan(
    bridge: ReaderIosBridge,
    folderName: String,
    scan: ImportedFolderScan
) {
    bridge.recordImportedFolder(
        folderName: folderName,
        fileNames: scan.files.map(\.name),
        filePaths: scan.files.map(\.path),
        contentIds: scan.files.map(\.contentId),
        relativePaths: scan.files.map(\.relativePath),
        fileSizes: scan.files.map { String($0.fileSize) },
        lastModifiedTimestamps: scan.files.map { String($0.lastModifiedTimestamp) },
        scanSucceeded: scan.succeeded
    )
}

private func copyImportedFolderToAppSupport(_ sourceURL: URL, folderName: String? = nil) -> ImportedFolderScan {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }

    var pendingStagingRoot: URL?
    do {
        let fileManager = FileManager.default
        let appSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let folderRoot = appSupport
            .appendingPathComponent("LocalFolders", isDirectory: true)
            .appendingPathComponent(safeLocalFolderName(folderName ?? sourceURL.lastPathComponent), isDirectory: true)
        let stagingRoot = folderRoot.deletingLastPathComponent()
            .appendingPathComponent(".\(folderRoot.lastPathComponent)-\(UUID().uuidString).staging", isDirectory: true)
        pendingStagingRoot = stagingRoot
        try fileManager.createDirectory(at: stagingRoot, withIntermediateDirectories: true)

        let resourceKeys: [URLResourceKey] = [
            .isRegularFileKey,
            .isDirectoryKey,
            .fileSizeKey,
            .contentModificationDateKey
        ]
        var enumerationFailed = false
        guard let enumerator = fileManager.enumerator(
            at: sourceURL,
            includingPropertiesForKeys: resourceKeys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants],
            errorHandler: { _, _ in
                enumerationFailed = true
                return false
            }
        ) else {
            try? fileManager.removeItem(at: stagingRoot)
            return ImportedFolderScan(files: [], succeeded: false)
        }

        var imported: [ImportedReaderFile] = []
        for case let itemURL as URL in enumerator {
            let values = try itemURL.resourceValues(forKeys: Set(resourceKeys))
            let relativePath = itemURL.path.replacingOccurrences(
                of: sourceURL.path + "/",
                with: "",
                options: [.anchored]
            )
            let stagingURL = stagingRoot.appendingPathComponent(relativePath)
            let destinationURL = folderRoot.appendingPathComponent(relativePath)
            if values.isDirectory == true {
                try fileManager.createDirectory(at: stagingURL, withIntermediateDirectories: true)
            } else if values.isRegularFile == true {
                try fileManager.createDirectory(
                    at: stagingURL.deletingLastPathComponent(),
                    withIntermediateDirectories: true
                )
                try fileManager.copyItem(at: itemURL, to: stagingURL)
                guard let contentId = sha256FileId(stagingURL) else {
                    try? fileManager.removeItem(at: stagingURL)
                    enumerationFailed = true
                    break
                }
                imported.append(
                    ImportedReaderFile(
                        name: itemURL.lastPathComponent,
                        path: destinationURL.path,
                        contentId: contentId,
                        relativePath: relativePath,
                        fileSize: Int64(values.fileSize ?? 0),
                        lastModifiedTimestamp: Int64(
                            (values.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000
                        )
                    )
                )
            }
        }
        guard !enumerationFailed else {
            try? fileManager.removeItem(at: stagingRoot)
            return ImportedFolderScan(files: [], succeeded: false)
        }
        if fileManager.fileExists(atPath: folderRoot.path) {
            _ = try fileManager.replaceItemAt(folderRoot, withItemAt: stagingRoot)
        } else {
            try fileManager.moveItem(at: stagingRoot, to: folderRoot)
        }
        pendingStagingRoot = nil
        return ImportedFolderScan(files: imported, succeeded: true)
    } catch {
        if let pendingStagingRoot {
            try? FileManager.default.removeItem(at: pendingStagingRoot)
        }
        return ImportedFolderScan(files: [], succeeded: false)
    }
}

private func safeLocalFolderName(_ name: String) -> String {
    let cleaned = name.replacingOccurrences(of: "/", with: "_").trimmingCharacters(in: .whitespacesAndNewlines)
    return cleaned.isEmpty ? "Imported Folder" : cleaned
}

private struct ImportedReaderFile {
    let name: String
    let path: String
    let contentId: String
    let relativePath: String
    let fileSize: Int64
    let lastModifiedTimestamp: Int64
}

private func sha256FileId(_ url: URL) -> String? {
    guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
    defer { try? handle.close() }
    var hasher = SHA256()
    do {
        while true {
            let data = try handle.read(upToCount: 1024 * 1024) ?? Data()
            if data.isEmpty { break }
            hasher.update(data: data)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    } catch {
        return nil
    }
}

private func copyImportedFileToAppSupport(_ sourceURL: URL, directoryName: String) -> ImportedReaderFile? {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }

    do {
        let fileManager = FileManager.default
        let appSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let importsDirectory = appSupport.appendingPathComponent(directoryName, isDirectory: true)
        try fileManager.createDirectory(at: importsDirectory, withIntermediateDirectories: true)

        let fileName = sourceURL.lastPathComponent
        let destinationURL = importsDirectory.appendingPathComponent(uniqueImportedFileName(fileName))
        if fileManager.fileExists(atPath: destinationURL.path) {
            try fileManager.removeItem(at: destinationURL)
        }
        try fileManager.copyItem(at: sourceURL, to: destinationURL)
        guard let contentId = sha256FileId(destinationURL) else {
            try? fileManager.removeItem(at: destinationURL)
            return nil
        }
        let values = try destinationURL.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
        return ImportedReaderFile(
            name: fileName,
            path: destinationURL.path,
            contentId: contentId,
            relativePath: fileName,
            fileSize: Int64(values.fileSize ?? 0),
            lastModifiedTimestamp: Int64(
                (values.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000
            )
        )
    } catch {
        return nil
    }
}

private func copyExternalFileToTemporaryStorage(_ sourceURL: URL) -> ImportedReaderFile? {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }
    do {
        let fileManager = FileManager.default
        let directory = fileManager.temporaryDirectory.appendingPathComponent("ExternalOpen", isDirectory: true)
        if fileManager.fileExists(atPath: directory.path) {
            try fileManager.removeItem(at: directory)
        }
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(uniqueImportedFileName(sourceURL.lastPathComponent))
        try fileManager.copyItem(at: sourceURL, to: destination)
        guard let contentId = sha256FileId(destination) else {
            try? fileManager.removeItem(at: destination)
            return nil
        }
        let values = try destination.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
        return ImportedReaderFile(
            name: sourceURL.lastPathComponent,
            path: destination.path,
            contentId: contentId,
            relativePath: sourceURL.lastPathComponent,
            fileSize: Int64(values.fileSize ?? 0),
            lastModifiedTimestamp: Int64(
                (values.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000
            )
        )
    } catch {
        return nil
    }
}

private func uniqueImportedFileName(_ fileName: String) -> String {
    let source = URL(fileURLWithPath: fileName)
    let baseName = source.deletingPathExtension().lastPathComponent
    let fileExtension = source.pathExtension
    let suffix = UUID().uuidString.prefix(8)
    if fileExtension.isEmpty {
        return "\(baseName)-\(suffix)"
    }
    return "\(baseName)-\(suffix).\(fileExtension)"
}

private struct ReaderComposeHost: UIViewControllerRepresentable {
    let bridge: ReaderIosBridge
    @Binding var isSystemUiHidden: Bool
    let onImportBooks: () -> Void
    let onImportFolder: () -> Void
    let onRefreshFolders: () -> Void
    let onImportFonts: () -> Void
    let onImportCover: () -> Void
    let onRemoveFolder: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let composeController = ReaderIosAppKt.readerComposeViewController(
            bridge: bridge,
            onImportBooks: onImportBooks,
            onImportFolder: onImportFolder,
            onRefreshFolders: onRefreshFolders,
            onImportFonts: onImportFonts,
            onImportCover: onImportCover,
            onRemoveFolder: onRemoveFolder
        )
        let hostController = ReaderStatusBarHostController(content: composeController)
        bridge.setSystemUiHandler { statusHidden, navigationHidden, lightContent, backgroundArgb, edgeToEdge in
            DispatchQueue.main.async {
                isSystemUiHidden = statusHidden.boolValue
            }
            hostController.updateSystemUi(
                statusHidden: statusHidden.boolValue,
                navigationHidden: navigationHidden.boolValue,
                lightContent: lightContent.boolValue,
                backgroundArgb: backgroundArgb.int64Value,
                edgeToEdge: edgeToEdge.boolValue
            )
        }
        bridge.setOrientationHandler { mode in
            hostController.updateReaderOrientation(mode: mode.int32Value)
        }
        return hostController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

private final class ReaderStatusBarHostController: UIViewController {
    private let contentController: UIViewController
    private var hidesStatusBar = false
    private var hidesHomeIndicator = false
    private var usesLightStatusBarContent = false
    private var contentInterfaceStyle: UIUserInterfaceStyle = .unspecified
    private var readerOrientationMode: Int32 = 0
    private let statusBarBackdrop = UIView()
    private let navigationBarBackdrop = UIView()
    private var contentTopToSafeAreaConstraint: NSLayoutConstraint?
    private var contentBottomToSafeAreaConstraint: NSLayoutConstraint?
    private var contentTopToEdgeConstraint: NSLayoutConstraint?
    private var contentBottomToEdgeConstraint: NSLayoutConstraint?

    init(content: UIViewController) {
        self.contentController = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        contentInterfaceStyle = traitCollection.userInterfaceStyle
        contentController.overrideUserInterfaceStyle = contentInterfaceStyle
        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        let topToSafeArea = contentController.view.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor)
        let bottomToSafeArea = contentController.view.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        let topToEdge = contentController.view.topAnchor.constraint(equalTo: view.topAnchor)
        let bottomToEdge = contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        contentTopToSafeAreaConstraint = topToSafeArea
        contentBottomToSafeAreaConstraint = bottomToSafeArea
        contentTopToEdgeConstraint = topToEdge
        contentBottomToEdgeConstraint = bottomToEdge
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            topToSafeArea,
            bottomToSafeArea
        ])
        contentController.didMove(toParent: self)

        statusBarBackdrop.isUserInteractionEnabled = false
        navigationBarBackdrop.isUserInteractionEnabled = false
        statusBarBackdrop.translatesAutoresizingMaskIntoConstraints = false
        navigationBarBackdrop.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusBarBackdrop)
        view.addSubview(navigationBarBackdrop)
        NSLayoutConstraint.activate([
            statusBarBackdrop.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            statusBarBackdrop.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            statusBarBackdrop.topAnchor.constraint(equalTo: view.topAnchor),
            statusBarBackdrop.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            navigationBarBackdrop.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            navigationBarBackdrop.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            navigationBarBackdrop.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            navigationBarBackdrop.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    override var prefersStatusBarHidden: Bool { hidesStatusBar }
    override var prefersHomeIndicatorAutoHidden: Bool { hidesHomeIndicator }
    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }
    override var preferredStatusBarStyle: UIStatusBarStyle {
        usesLightStatusBarContent ? .lightContent : .darkContent
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        switch readerOrientationMode {
        case 1: return .portrait
        case 2: return .landscape
        default: return .all
        }
    }

    func updateReaderOrientation(mode: Int32) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            readerOrientationMode = mode
            setNeedsUpdateOfSupportedInterfaceOrientations()
            guard let windowScene = view.window?.windowScene else { return }
            let preferences = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: supportedInterfaceOrientations)
            windowScene.requestGeometryUpdate(preferences)
        }
    }

    private func updateSystemBarLayout(edgeToEdge: Bool) {
        let safeAreaConstraints = [contentTopToSafeAreaConstraint, contentBottomToSafeAreaConstraint].compactMap { $0 }
        let edgeConstraints = [contentTopToEdgeConstraint, contentBottomToEdgeConstraint].compactMap { $0 }
        NSLayoutConstraint.deactivate(safeAreaConstraints + edgeConstraints)
        NSLayoutConstraint.activate(edgeToEdge ? edgeConstraints : safeAreaConstraints)
        view.setNeedsLayout()
    }

    func updateSystemUi(statusHidden: Bool, navigationHidden: Bool, lightContent: Bool, backgroundArgb: Int64, edgeToEdge: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            hidesStatusBar = statusHidden
            hidesHomeIndicator = navigationHidden
            usesLightStatusBarContent = lightContent
            overrideUserInterfaceStyle = lightContent ? .dark : .light
            contentController.overrideUserInterfaceStyle = contentInterfaceStyle
            let bits = UInt64(bitPattern: backgroundArgb)
            let red = CGFloat((bits >> 16) & 0xFF) / 255
            let green = CGFloat((bits >> 8) & 0xFF) / 255
            let blue = CGFloat(bits & 0xFF) / 255
            let alpha = CGFloat((bits >> 24) & 0xFF) / 255
            let themeColor = UIColor(red: red, green: green, blue: blue, alpha: alpha)
            view.backgroundColor = themeColor
            view.window?.backgroundColor = view.backgroundColor
            statusBarBackdrop.backgroundColor = themeColor
            navigationBarBackdrop.backgroundColor = themeColor
            // In edge-to-edge modes these views would sit above Compose and leave a permanent
            // surface-colored strip after Sync hides the reader chrome. The Compose toolbar
            // itself paints beneath the system bars while visible; when it is hidden the PDF
            // must be allowed to draw all the way to the screen edges.
            statusBarBackdrop.isHidden = edgeToEdge
            navigationBarBackdrop.isHidden = edgeToEdge
            updateSystemBarLayout(edgeToEdge: edgeToEdge)
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }
}

#Preview {
    ContentView()
}
