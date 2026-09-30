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
import OSLog

struct ContentView: View {
    private enum ImportKind {
        case books
        case audiobookFile
        case audiobookMultiple
        case audiobookFolder
        case folder
        case fonts
        case cover
    }

    private let bridge = ReaderIosBridge()
    private let audiobookPlayer = AudiobookPlayerController()
    private let cloudFolderSync = LocalCloudFolderSyncController()
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
            onImportAudiobookFile: {
                importKind = .audiobookFile
                isImportPickerPresented = true
            },
            onImportAudiobookMultiple: {
                importKind = .audiobookMultiple
                isImportPickerPresented = true
            },
            onImportAudiobookFolder: {
                importKind = .audiobookFolder
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
        .task {
            // Live reachability for the shared Kotlin AI gates (idempotent;
            // the NWPathMonitor itself starts once per process).
            IosReachability.start(bridge: bridge)
        }
        .fileImporter(
            isPresented: $isImportPickerPresented,
            allowedContentTypes: importKind == .fonts
                ? [.font]
                : (
                    importKind == .folder || importKind == .audiobookFolder
                        ? [.folder]
                        : (
                            importKind == .cover
                                ? [.image]
                                : (importKind == .audiobookFile || importKind == .audiobookMultiple
                                    ? [.audio]
                                    : allowedReaderImportTypes)
                        )
                ),
            allowsMultipleSelection: importKind != .folder &&
                importKind != .audiobookFolder &&
                importKind != .audiobookFile &&
                importKind != .cover
        ) { result in
            switch result {
            case .success(let urls):
                if importKind == .audiobookFolder, let folderURL = urls.first {
                    let scan = copyImportedAudiobookFolderToAppSupport(folderURL)
                    bridge.recordImportedFiles(
                        fileNames: scan.files.map(\.name),
                        filePaths: scan.files.map(\.path),
                        contentIds: scan.files.map(\.contentId),
                        failedCount: scan.succeeded && !scan.files.isEmpty ? 0 : 1,
                        wasCancelled: false,
                        autoOpen: false,
                        enqueueHandoff: true,
                    )
                    return
                }
                if importKind == .folder, let folderURL = urls.first {
                    let folderName = rememberImportedFolder(folderURL, bridge: bridge)
                    scheduleImportedFolderScan(
                        bridge: bridge,
                        folderName: folderName,
                        sourceURL: sourceURLForFolder(named: folderName, fallback: folderURL, bridge: bridge)
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
                        autoOpen: importKind != .audiobookFile && importKind != .audiobookMultiple,
                        enqueueHandoff: true,
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
                    let detail = wasCancelled ? "user_cancelled" : "picker_failed \(folderScanErrorDetail(error))"
                    logFolderScan("picker.failed \(detail)", bridge: bridge)
                    bridge.recordImportedFolder(
                        folderName: "folder",
                        fileNames: [],
                        filePaths: [],
                        contentIds: [],
                        relativePaths: [],
                        fileSizes: [],
                        lastModifiedTimestamps: [],
                        scanSucceeded: false,
                        scanStatusRaw: ImportedFolderScanStatus.unavailable.rawValue,
                        scanDetail: detail
                    )
                } else if importKind == .audiobookFolder {
                    bridge.recordImportedFiles(
                        fileNames: [],
                        filePaths: [],
                        contentIds: [],
                        failedCount: wasCancelled ? 0 : 1,
                        wasCancelled: wasCancelled,
                        autoOpen: false,
                        enqueueHandoff: true,
                    )
                } else {
                    bridge.recordImportedFiles(
                        fileNames: [],
                        filePaths: [],
                        contentIds: [],
                        failedCount: wasCancelled ? 0 : 1,
                        wasCancelled: wasCancelled,
                        autoOpen: importKind != .audiobookFile && importKind != .audiobookMultiple,
                        enqueueHandoff: true
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
#if DEBUG
            bridge.setDebugBuild(enabled: true)
#endif
            // Startup orphan sweep (Android MainViewModel.sweepOrphanedCache
            // parity, temp-only). The pending-external-removal drain already
            // runs in Kotlin startup state; this covers crash-orphaned temp
            // staging that has no owner record.
            sweepStaleTemporaryFiles()
            localStoreKit.attach(to: bridge)
            localAccount.attach(to: bridge)
            // Pure-CloudKit library sync is Pro-gated. Forward StoreKit truth;
            // sync data plane itself needs no login (iCloud identity only).
            localAccount.setProSyncEnabled(localStoreKit.proSyncEnabled)
            localAccount.setLocalCloudDataClearHandler {
                bridge.clearLocalCloudData()
            }
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
            // Lets Kotlin resolve an in-place book ref through the bookmark.
            // Installed before any folder state is read.
            bridge.setFolderBookmarkResolver { folderName in
                resolveImportedFolderPath(folderName)
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
            audiobookPlayer.onPlaybackSessionEnded = {
                bridge.notifyAudiobookSessionEnded()
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
            bridge.setUnifiedDiagnosticsProvider { captureUnifiedLogEntries() }
            // Folder-transfer executor (P0 #4): Swift owns the Drive/Firestore
            // orchestration; Kotlin owns selection/prefs/Compose state. The
            // controller publishes executor state back through the bridge
            // after every pass.
            cloudFolderSync.localAccount = localAccount
            cloudFolderSync.bridgeProvider = { [bridge] in bridge }
            bridge.setCloudFolderSyncRequestHandler { [cloudFolderSync] direction, rootId, replace in
                switch direction {
                case "pull":
                    if let rootId {
                        cloudFolderSync.requestSyncRoot(rootId, direction: .pull, replace: replace.boolValue)
                    } else {
                        cloudFolderSync.requestSyncAllPull(replace: replace.boolValue)
                    }
                case "push":
                    if let rootId {
                        cloudFolderSync.requestSyncRoot(rootId, direction: .push, replace: replace.boolValue)
                    }
                default:
                    if let rootId {
                        cloudFolderSync.requestSyncRoot(rootId, direction: .sync, replace: replace.boolValue)
                    } else {
                        cloudFolderSync.requestSyncAll(replace: replace.boolValue)
                    }
                }
            }
            bridge.setCloudFolderBindHandler { [cloudFolderSync, localAccount] rootId, folderName in
                cloudFolderSync.ensureLocalFolderBinding(
                    folderName: folderName,
                    rootId: rootId,
                    deviceId: localAccount.folderSyncDeviceID()
                )
            }
            bridge.setCloudFolderConflictResolveHandler { [cloudFolderSync, localAccount] rootId, conflictId, resolution in
                guard let uid = localAccount.folderSyncAccountID() else { return }
                cloudFolderSync.resolveConflict(uid: uid, rootId: rootId, conflictId: conflictId, resolutionRaw: resolution)
            }
            bridge.setCloudFolderDeleteHandler { [cloudFolderSync, localAccount] rootId, deleteEverywhere in
                guard let uid = localAccount.folderSyncAccountID() else { return }
                if deleteEverywhere.boolValue {
                    Task { await cloudFolderSync.deleteRootEverywhere(uid: uid, rootId: rootId) }
                } else {
                    cloudFolderSync.removeBindingOnly(uid: uid, rootId: rootId)
                }
            }
            // P0 #1: BG task bodies run the same foreground-only reconciliation
            // Android does (outbox retry + StoreKit re-query, no background
            // purchase queue). The shared retry math lives in
            // SharedBackgroundSyncPolicy; Swift only owns scheduling.
            IosBackgroundSync.refreshHandler = { [localAccount, localStoreKit] in
                await localAccount.handleBackgroundRefresh()
                await localStoreKit.handleBackgroundRefresh()
            }
            IosBackgroundSync.processingHandler = { [localAccount, cloudFolderSync] in
                await localAccount.handleBackgroundRefresh()
                cloudFolderSync.requestSyncAll(replace: false)
                await cloudFolderSync.awaitIdle()
            }
            // CloudKit silent push accelerates sync; it is never required for it.
            // A notification that launches the app fires before this closure
            // exists, so IosPushNotifications completes those with .noData and
            // the BGTaskScheduler/foreground paths handle the work instead.
            // Same pull-only entry point the BG task uses, so the push path
            // cannot diverge from the scheduled one.
            IosPushNotifications.pullHandler = { [localAccount] in
                await localAccount.handleBackgroundRefresh()
            }
            #if DEBUG
            // Verifies the push -> pull -> completion chain on a simulator,
            // where APNs is never delivered. Opt in with the
            // -episteme.simulate-cloudkit-push launch argument.
            if ProcessInfo.processInfo.arguments.contains(IosPushNotifications.simulatePushLaunchArgument) {
                IosPushNotifications.simulateCloudKitPushAfterStartup()
            }
            #endif
        }
        .onChange(of: localStoreKit.proSyncEnabled) { _, isPro in
            localAccount.setProSyncEnabled(isPro)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                IosBackgroundSync.endGrace()
                bridge.updateAppActive(active: true)
                refreshImportedFolders(bridge: bridge)
                // Android parity: Billing re-queries on every foreground/auth
                // emission (no Worker). Reconcile StoreKit + re-arm the cloud
                // outbox on every foreground transition.
                Task { @MainActor in
                    await localStoreKit.handleForegroundResume()
                    localAccount.handleForegroundResume()
                    // Android parity (CloudFolderHeadListenerCoordinator):
                    // foreground-only head wake; other-device folder changes
                    // pull automatically via the debounced listener.
                    cloudFolderSync.startFolderHeadListener()
                }
            } else {
                bridge.updateAppActive(active: false)
                // Give in-flight cloud sync a grace period to finish, mirroring
                // WorkManager continuing after the activity stops. Sync work
                // is idempotent so expiration mid-pass is safe to retry.
                cloudFolderSync.stopFolderHeadListener()
                IosBackgroundSync.beginGrace()
                IosBackgroundSync.scheduleRefresh()
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
        let requestId = UUID().uuidString
        let imported = addToLibrary
            ? copyImportedFileToAppSupport(url, directoryName: "Imports")
            : copyExternalFileToTemporaryStorage(url, requestId: requestId)
        guard let imported else {
            // Android reports this through a toast (error_external_open_failed);
            // surface the same banner in Compose instead of failing silently.
            bridge.reportExternalOpenFailure(fileName: url.lastPathComponent)
            return
        }
        bridge.openExternalFile(
            fileName: imported.name,
            filePath: imported.path,
            contentId: imported.contentId,
            addToLibrary: addToLibrary,
            requestId: requestId,
        )
    }
}

/// A linked folder is read in place, so its bookmark must keep resolving to a
/// URL that can start a security scope.
///
/// Note there is deliberately no `.withSecurityScope` here. That option is
/// `API_UNAVAILABLE(ios)` — `NSURLBookmarkCreationWithSecurityScope` and
/// `NSURLBookmarkResolutionWithSecurityScope` exist only on macOS and
/// Mac Catalyst. On iOS the picker grants an implicit ephemeral scope that
/// `NSURLBookmarkCreationWithoutImplicitSecurityScope` documents as valid
/// "until reboot at the latest", which is what makes an in-place read survive
/// relaunch without the macOS-only option.
private let importedFolderBookmarkOptions: URL.BookmarkCreationOptions = [
    .minimalBookmark
]

private let importedFolderBookmarksKey = "reader.ios.importedFolderBookmarks.v1"

/// Resolves a stored folder bookmark. The resolution must ask for the security
/// scope, and that scope must be held for as long as the caller reads from the
/// URL, which is why every read site goes through `withImportedFolderScope`
/// rather than resolving a URL and using it directly.
private func resolveImportedFolderBookmark(
    _ bookmark: Data,
    folderName: String,
    bridge: ReaderIosBridge? = nil
) throws -> (url: URL, isStale: Bool) {
    var isStale = false
    let url = try URL(
        resolvingBookmarkData: bookmark,
        // `.withSecurityScope` is macOS-only (API_UNAVAILABLE(ios)). On iOS the
        // bookmark carries an implicit ephemeral scope, and
        // `.withoutImplicitStartAccessing` (iOS 14.2+) keeps that scope from
        // being torn down when this call returns, so the caller can decide when
        // to stop it.
        options: [.withoutUI, .withoutImplicitStartAccessing],
        relativeTo: nil,
        bookmarkDataIsStale: &isStale
    )
    if isStale {
        logFolderScan("bookmark.stale folder=\(folderName) reissuing", bridge: bridge)
        updateImportedFolderBookmark(url, folderName: folderName, bridge: bridge)
    }
    return (url, isStale)
}

private func storedImportedFolderBookmark(folderName: String) -> Data? {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    return bookmarks[folderName]
}

/// Shared diagnostics tag with Kotlin's `LOCAL_FOLDER_SCAN_LOG_TAG`. Filter
/// the exported diagnostics for this single string to see the whole
/// folder-scan pipeline (bookmark resolution, security-scoped copy,
/// managed-copy swap, scan consumption).
let localFolderScanLogTag = "LocalFolderScan"
private let localFolderScanLogger = Logger(subsystem: "com.aryan.reader", category: localFolderScanLogTag)

private enum ImportedFolderScanStatus: String {
    case complete = "COMPLETE"
    case partial = "PARTIAL"
    case unavailable = "UNAVAILABLE"
}

/// Single-line pipeline log that lands in both the unified log and the Kotlin
/// diagnostic export (via the bridge), so one tag covers both stores.
func logFolderScan(_ message: String, bridge: ReaderIosBridge?) {
    let singleLine = message.replacingOccurrences(of: "\n", with: " ")
    localFolderScanLogger.info("\(singleLine, privacy: .public)")
    bridge?.logFolderScanDiagnostic(message: singleLine)
}

private func folderScanErrorDetail(_ error: Error) -> String {
    let nsError = error as NSError
    let description = nsError.localizedDescription.replacingOccurrences(of: "\n", with: " ")
    return "domain=\(nsError.domain) code=\(nsError.code) desc=\(String(description.prefix(200)))"
}

@MainActor
private var importedFolderScanTasks: [String: Task<Void, Never>] = [:]

@MainActor
private var importedFolderScanGenerations: [String: Int] = [:]

@discardableResult
private func rememberImportedFolder(_ url: URL, bridge: ReaderIosBridge) -> String {
    let baseName = url.lastPathComponent
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    do {
        let bookmark = try url.bookmarkData(
            options: importedFolderBookmarkOptions,
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
            logFolderScan(
                "remember matched folder=\(matchedFolderName) refreshed=\(shouldRefreshMatch)",
                bridge: bridge
            )
            return matchedFolderName
        }
        let folderName = bridge.availableImportedFolderName(
            preferredName: baseName,
            existingNames: Array(bookmarks.keys)
        )
        bookmarks[folderName] = bookmark
        UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
        logFolderScan("remember created folder=\(folderName) bookmarks=\(bookmarks.count)", bridge: bridge)
        return folderName
    } catch {
        // The managed copy remains usable if a document provider cannot issue a bookmark.
        logFolderScan(
            "remember bookmark_failed folder=\(baseName) \(folderScanErrorDetail(error))",
            bridge: bridge
        )
        return bridge.availableImportedFolderName(
            preferredName: baseName,
            existingNames: Array(bookmarks.keys)
        )
    }
}

private func updateImportedFolderBookmark(_ url: URL, folderName: String, bridge: ReaderIosBridge? = nil) {
    guard let bookmark = try? url.bookmarkData(
        options: importedFolderBookmarkOptions,
        includingResourceValuesForKeys: nil,
        relativeTo: nil
    ) else {
        logFolderScan("bookmark.reissue_failed folder=\(folderName)", bridge: bridge)
        return
    }
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    bookmarks[folderName] = bookmark
    UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
}

/// Captures the process unified log (os_log/NSLog included) for diagnostics
/// export — the app-readable counterpart of Android's `logcat -d -t 5000`.
/// Bounded to the last 24 hours; Kotlin clamps the line count.
private func captureUnifiedLogEntries() -> String? {
    if #available(iOS 15.0, *) {
        return autoreleasepool { () -> String? in
            guard let store = try? OSLogStore(scope: .currentProcessIdentifier) else { return nil }
            // Reverse order yields newest first; cap at Android's logcat export size.
            guard let entries = try? store.getEntries(with: .reverse) else { return nil }
            var lines: [String] = []
            let cutoff = Date().addingTimeInterval(-60 * 60 * 24)
            for case let entry as any OSLogEntry & OSLogEntryWithPayload in entries {
                if entry.date < cutoff { break }
                if lines.count >= 5_000 { break }
                lines.append(entry.composedMessage)
            }
            lines.reverse()
            return lines.isEmpty ? nil : lines.joined(separator: "\n")
        }
    }
    return nil
}

@MainActor
private func refreshImportedFolders(bridge: ReaderIosBridge) {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    logFolderScan("refresh.begin folders=\(bookmarks.count)", bridge: bridge)
    for (folderName, bookmark) in bookmarks {
        let folderURL: URL
        do {
            let resolved = try resolveImportedFolderBookmark(bookmark, folderName: folderName)
            folderURL = resolved.url
        } catch {
            logFolderScan(
                "refresh.resolve_failed folder=\(folderName) \(folderScanErrorDetail(error))",
                bridge: bridge
            )
            recordImportedFolderScan(
                bridge: bridge,
                folderName: folderName,
                scan: ImportedFolderScan(
                    files: [],
                    succeeded: false,
                    status: .unavailable,
                    detail: "resolve_failed \(folderScanErrorDetail(error))"
                )
            )
            continue
        }
        scheduleImportedFolderScan(
            bridge: bridge,
            folderName: folderName,
            sourceURL: folderURL
        )
    }
}

/// Re-resolves the scan URL from the stored bookmark. The picker URL is only
/// guaranteed valid inside the fileImporter callback, while the scan runs
/// detached afterwards; the bookmark created in the same callback is the
/// durable handle.
private func sourceURLForFolder(named folderName: String, fallback: URL, bridge: ReaderIosBridge) -> URL {
    guard let bookmark = storedImportedFolderBookmark(folderName: folderName) else {
        logFolderScan("picker.no_bookmark folder=\(folderName) using_picker_url", bridge: bridge)
        return fallback
    }
    do {
        let resolved = try resolveImportedFolderBookmark(bookmark, folderName: folderName)
        logFolderScan("picker.url_resolved folder=\(folderName) stale=\(resolved.isStale)", bridge: bridge)
        return resolved.url
    } catch {
        logFolderScan(
            "picker.resolve_failed folder=\(folderName) \(folderScanErrorDetail(error)) using_picker_url",
            bridge: bridge
        )
        return fallback
    }
}

/// Folder enumeration, copying, and hashing can be arbitrarily expensive. Keep it off the
/// main actor, while serializing scans for the same folder so an app-activation refresh cannot
/// replace a user-triggered import halfway through. Only the newest completed scan is applied.
@MainActor
private func scheduleImportedFolderScan(
    bridge: ReaderIosBridge,
    folderName: String,
    sourceURL: URL
) {
    let generation = (importedFolderScanGenerations[folderName] ?? 0) &+ 1
    importedFolderScanGenerations[folderName] = generation
    let previousTask = importedFolderScanTasks[folderName]
    let task = Task { @MainActor in
        if let previousTask {
            _ = await previousTask.value
        }
        guard importedFolderScanGenerations[folderName] == generation else { return }
        let scan = await Task.detached(priority: .userInitiated) {
            copyImportedFolderToAppSupport(sourceURL, folderName: folderName)
        }.value
        guard importedFolderScanGenerations[folderName] == generation else { return }
        recordImportedFolderScan(bridge: bridge, folderName: folderName, scan: scan)
        importedFolderScanTasks.removeValue(forKey: folderName)
    }
    importedFolderScanTasks[folderName] = task
}

@MainActor
private func removeImportedFolder(named folderName: String) {
    let pendingTask = importedFolderScanTasks[folderName]
    importedFolderScanGenerations[folderName] = (importedFolderScanGenerations[folderName] ?? 0) &+ 1
    pendingTask?.cancel()

    // Do not remove the managed copy until an in-flight detached scan has finished. Otherwise
    // that scan can atomically replace the folder after the user just deleted it.
    Task { @MainActor in
        if let pendingTask {
            _ = await pendingTask.value
        }
        importedFolderScanTasks.removeValue(forKey: folderName)
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
}

private func deleteImportedFolderFiles(folderName: String, managedPaths: [String]) {
    guard let bookmark = storedImportedFolderBookmark(folderName: folderName),
          let sourceRoot = try? resolveImportedFolderBookmark(bookmark, folderName: folderName).url,
          let appSupport = try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
    ) else {
        return
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
    guard let bookmark = storedImportedFolderBookmark(folderName: folderName) else { return nil }
    guard let sourceRoot = try? resolveImportedFolderBookmark(bookmark, folderName: folderName).url,
          let appSupport = try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
          ) else {
        return nil
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
    guard let bookmark = storedImportedFolderBookmark(folderName: folderName) else { return nil }
    guard let sourceRoot = try? resolveImportedFolderBookmark(bookmark, folderName: folderName).url,
          let appSupport = try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
          ) else {
        return nil
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

private struct ImportedFolderScan: Sendable {
    let files: [ImportedReaderFile]
    let succeeded: Bool
    let status: ImportedFolderScanStatus
    let detail: String

    init(
        files: [ImportedReaderFile],
        succeeded: Bool,
        status: ImportedFolderScanStatus? = nil,
        detail: String = ""
    ) {
        self.files = files
        self.succeeded = succeeded
        self.status = status ?? (succeeded ? .complete : .unavailable)
        self.detail = detail
    }
}

private func recordImportedFolderScan(
    bridge: ReaderIosBridge,
    folderName: String,
    scan: ImportedFolderScan
) {
    logFolderScan(
        "scan.recorded folder=\(folderName) succeeded=\(scan.succeeded) " +
            "status=\(scan.status.rawValue) files=\(scan.files.count) detail=\(scan.detail)",
        bridge: bridge
    )
    bridge.recordImportedFolder(
        folderName: folderName,
        fileNames: scan.files.map(\.name),
        filePaths: scan.files.map(\.path),
        contentIds: scan.files.map(\.contentId),
        relativePaths: scan.files.map(\.relativePath),
        fileSizes: scan.files.map { String($0.fileSize) },
        lastModifiedTimestamps: scan.files.map { String($0.lastModifiedTimestamp) },
        scanSucceeded: scan.succeeded,
        scanStatusRaw: scan.status.rawValue,
        scanDetail: scan.detail
    )
}

private enum FolderScanCopyError: Error {
    case hashFailed(String)

    var detail: String {
        switch self {
        case .hashFailed(let relative):
            return "hash_failed relative=\(relative)"
        }
    }
}

nonisolated private func copyImportedFolderToAppSupport(_ sourceURL: URL, folderName: String? = nil) -> ImportedFolderScan {
    let name = folderName ?? sourceURL.lastPathComponent
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    guard didStartAccessing else {
        localFolderScanLogger.error("scan.access_denied folder=\(name, privacy: .public)")
        return ImportedFolderScan(
            files: [],
            succeeded: false,
            status: .unavailable,
            detail: "access_denied startAccessingSecurityScopedResource=false"
        )
    }
    defer {
        sourceURL.stopAccessingSecurityScopedResource()
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
            .appendingPathComponent(safeLocalFolderName(name), isDirectory: true)
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
        // A single unreadable child must not discard the whole folder (Android
        // marks the same situation PARTIAL). Keep going and let the engine
        // keep books the partial pass could not see.
        var sawChildError = false
        var skippedCount = 0
        var firstSkipDetail = ""
        func noteSkipped(_ detail: String) {
            skippedCount += 1
            if firstSkipDetail.isEmpty { firstSkipDetail = String(detail.prefix(200)) }
        }
        guard let enumerator = fileManager.enumerator(
            at: sourceURL,
            includingPropertiesForKeys: resourceKeys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants],
            errorHandler: { url, error in
                sawChildError = true
                let nsError = error as NSError
                let detail = "child=\(url.lastPathComponent) domain=\(nsError.domain) code=\(nsError.code)"
                if firstSkipDetail.isEmpty { firstSkipDetail = detail }
                localFolderScanLogger.error("scan.child_error folder=\(name, privacy: .public) \(detail, privacy: .public)")
                return true
            }
        ) else {
            try? fileManager.removeItem(at: stagingRoot)
            pendingStagingRoot = nil
            localFolderScanLogger.error("scan.enumerator_nil folder=\(name, privacy: .public)")
            return ImportedFolderScan(files: [], succeeded: false, status: .unavailable, detail: "enumerator_nil")
        }

        var imported: [ImportedReaderFile] = []
        for case let itemURL as URL in enumerator {
            do {
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
                        throw FolderScanCopyError.hashFailed(relativePath)
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
            } catch {
                let detail: String
                if let copyError = error as? FolderScanCopyError {
                    detail = copyError.detail
                } else {
                    detail = folderScanErrorDetail(error)
                }
                noteSkipped(detail)
                localFolderScanLogger.error("scan.file_skipped folder=\(name, privacy: .public) \(detail, privacy: .public)")
            }
        }
        let isPartial = sawChildError || skippedCount > 0
        let status: ImportedFolderScanStatus = isPartial ? .partial : .complete
        let detail = isPartial ? "partial skipped=\(skippedCount) first=\(firstSkipDetail)" : ""
        // Swap the staging copy into place without ever destroying the previous
        // managed copy on failure: move the old directory aside first, so a
        // failed second rename can restore it and "keeping the previous scan"
        // stays true. `replaceItemAt` on a directory can remove the
        // destination and then fail, which deleted books out from under the
        // library rows that still reference them.
        if fileManager.fileExists(atPath: folderRoot.path) {
            let backupRoot = folderRoot.deletingLastPathComponent()
                .appendingPathComponent(".\(folderRoot.lastPathComponent)-\(UUID().uuidString).backup", isDirectory: true)
            do {
                try fileManager.moveItem(at: folderRoot, to: backupRoot)
            } catch {
                try? fileManager.removeItem(at: stagingRoot)
                pendingStagingRoot = nil
                let swapDetail = "swap_backup_failed \(folderScanErrorDetail(error))"
                localFolderScanLogger.error("scan.swap_failed folder=\(name, privacy: .public) \(swapDetail, privacy: .public)")
                return ImportedFolderScan(files: [], succeeded: false, status: .unavailable, detail: swapDetail)
            }
            do {
                try fileManager.moveItem(at: stagingRoot, to: folderRoot)
                pendingStagingRoot = nil
                try? fileManager.removeItem(at: backupRoot)
            } catch {
                let swapDetail = "swap_failed \(folderScanErrorDetail(error))"
                if fileManager.fileExists(atPath: folderRoot.path) {
                    try? fileManager.removeItem(at: folderRoot)
                }
                do {
                    try fileManager.moveItem(at: backupRoot, to: folderRoot)
                    try? fileManager.removeItem(at: stagingRoot)
                    pendingStagingRoot = nil
                    localFolderScanLogger.error("scan.swap_restored folder=\(name, privacy: .public) \(swapDetail, privacy: .public)")
                    return ImportedFolderScan(
                        files: [],
                        succeeded: false,
                        status: .unavailable,
                        detail: "\(swapDetail) restored_previous"
                    )
                } catch {
                    let restoreDetail = "\(swapDetail) restore_failed \(folderScanErrorDetail(error))"
                    localFolderScanLogger.error("scan.swap_failed folder=\(name, privacy: .public) \(restoreDetail, privacy: .public)")
                    return ImportedFolderScan(files: [], succeeded: false, status: .unavailable, detail: restoreDetail)
                }
            }
        } else {
            do {
                try fileManager.moveItem(at: stagingRoot, to: folderRoot)
                pendingStagingRoot = nil
            } catch {
                try? fileManager.removeItem(at: stagingRoot)
                pendingStagingRoot = nil
                let swapDetail = "first_move_failed \(folderScanErrorDetail(error))"
                localFolderScanLogger.error("scan.swap_failed folder=\(name, privacy: .public) \(swapDetail, privacy: .public)")
                return ImportedFolderScan(files: [], succeeded: false, status: .unavailable, detail: swapDetail)
            }
        }
        localFolderScanLogger.info("scan.succeeded folder=\(name, privacy: .public) status=\(status.rawValue, privacy: .public) files=\(imported.count, privacy: .public) skipped=\(skippedCount, privacy: .public)")
        return ImportedFolderScan(files: imported, succeeded: true, status: status, detail: detail)
    } catch {
        if let pendingStagingRoot {
            try? FileManager.default.removeItem(at: pendingStagingRoot)
        }
        let scanDetail = "copy_failed \(folderScanErrorDetail(error))"
        localFolderScanLogger.error("scan.failed folder=\(name, privacy: .public) \(scanDetail, privacy: .public)")
        return ImportedFolderScan(files: [], succeeded: false, status: .unavailable, detail: scanDetail)
    }
}

/// Audiobook folder imports are app-owned files, matching Android's
/// AudiobookImporter.importFolder semantics. They must not become a generic
/// synced folder (which would also import EPUB/PDF files and remove audio on
/// folder-bookmark deletion).
nonisolated private func copyImportedAudiobookFolderToAppSupport(_ sourceURL: URL) -> ImportedFolderScan {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }

    let audiobookExtensions: Set<String> = ["mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac"]
    do {
        let fileManager = FileManager.default
        let appSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let importsRoot = appSupport
            .appendingPathComponent("Imports", isDirectory: true)
            .appendingPathComponent("Audiobooks-\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: importsRoot, withIntermediateDirectories: true)
        let resourceKeys: [URLResourceKey] = [
            .isRegularFileKey,
            .isDirectoryKey,
            .fileSizeKey,
            .contentModificationDateKey,
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
            return ImportedFolderScan(files: [], succeeded: false)
        }

        var imported: [ImportedReaderFile] = []
        for case let itemURL as URL in enumerator {
            let values = try itemURL.resourceValues(forKeys: Set(resourceKeys))
            guard values.isRegularFile == true else { continue }
            guard audiobookExtensions.contains(itemURL.pathExtension.lowercased()) else { continue }
            let relativePath = itemURL.path.replacingOccurrences(
                of: sourceURL.path + "/",
                with: "",
                options: [.anchored]
            )
            let destinationURL = importsRoot.appendingPathComponent(relativePath)
            try fileManager.createDirectory(
                at: destinationURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try fileManager.copyItem(at: itemURL, to: destinationURL)
            guard let contentId = sha256FileId(destinationURL) else {
                try? fileManager.removeItem(at: destinationURL)
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
        guard !enumerationFailed else {
            try? fileManager.removeItem(at: importsRoot)
            return ImportedFolderScan(files: [], succeeded: false)
        }
        return ImportedFolderScan(files: imported, succeeded: true)
    } catch {
        return ImportedFolderScan(files: [], succeeded: false)
    }
}

/// Resolves a linked folder's root path for the Kotlin side, holding the
/// security scope for the duration of the call. Kotlin cannot read the
/// bookmark itself, so this is the bridge that lets an in-place book ref
/// (`ios-folder-book://<folder>/<relative path>`) become a real path.
///
/// The scope is released when this returns. That is correct for enumeration,
/// stat and hashing, but a long-lived read (a PDF keeps its fd for the whole
/// session) must hold a scope token instead — see `IosFolderScopeRegistry`.
nonisolated func resolveImportedFolderPath(_ folderName: String) -> String? {
    guard let bookmark = UserDefaults.standard
        .dictionary(forKey: importedFolderBookmarksKey)?[folderName] as? Data else {
        return nil
    }
    var isStale = false
    guard let url = try? URL(
        resolvingBookmarkData: bookmark,
        // `.withSecurityScope` is macOS-only (API_UNAVAILABLE(ios)); on iOS the
        // bookmark carries an implicit ephemeral scope.
        options: [.withoutUI, .withoutImplicitStartAccessing],
        relativeTo: nil,
        bookmarkDataIsStale: &isStale
    ) else {
        return nil
    }
    guard url.startAccessingSecurityScopedResource() else {
        // A resolved-but-unscoped URL is worse than none: reads through it can
        // silently return nothing, so report the folder as unreachable.
        localFolderScanLogger.error("ref.resolve_access_denied folder=\(folderName, privacy: .public)")
        return nil
    }
    defer { url.stopAccessingSecurityScopedResource() }
    return url.standardizedFileURL.path
}

nonisolated private func safeLocalFolderName(_ name: String) -> String {
    let cleaned = name.replacingOccurrences(of: "/", with: "_").trimmingCharacters(in: .whitespacesAndNewlines)
    return cleaned.isEmpty ? "Imported Folder" : cleaned
}

private struct ImportedReaderFile: Sendable {
    let name: String
    let path: String
    let contentId: String
    let relativePath: String
    let fileSize: Int64
    let lastModifiedTimestamp: Int64
}

nonisolated private func sha256FileId(_ url: URL) -> String? {
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

private func copyExternalFileToTemporaryStorage(_ sourceURL: URL, requestId: String) -> ImportedReaderFile? {
    let fileManager = FileManager.default
    var requestDirectory: URL?
    var keepRequestDirectory = false
    defer {
        if !keepRequestDirectory, let requestDirectory {
            try? fileManager.removeItem(at: requestDirectory)
        }
    }
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }
    do {
        let rootDirectory = fileManager.temporaryDirectory.appendingPathComponent("ExternalOpen", isDirectory: true)
        let safeRequestId = requestId.replacingOccurrences(of: "/", with: "_")
        let directory = rootDirectory.appendingPathComponent(safeRequestId, isDirectory: true)
        requestDirectory = directory
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(uniqueImportedFileName(sourceURL.lastPathComponent))
        let sourceModifiedAt = (try? sourceURL.resourceValues(forKeys: [.contentModificationDateKey]))?
            .contentModificationDate ?? Date()
        try fileManager.copyItem(at: sourceURL, to: destination)
        // `copyItem` keeps the source's modification date, so a book opened from
        // another app landed here with its original (possibly years old) date and
        // the startup orphan sweep (`sweepStaleTemporaryFiles`, 1h threshold)
        // deleted the freshly staged copy. That is why external opens of books
        // that were already imported worked (they resolve to the Application
        // Support copy, which is never swept) while fresh opens failed with a
        // missing-file error once the sweep ran. Android always stages a newly
        // written copy, so stamp the staged file's date and keep the source's
        // date only in the metadata we report.
        try? fileManager.setAttributes([.modificationDate: Date()], ofItemAtPath: destination.path)
        guard let contentId = sha256FileId(destination) else {
            return nil
        }
        let values = try destination.resourceValues(forKeys: [.fileSizeKey])
        keepRequestDirectory = true
        return ImportedReaderFile(
            name: sourceURL.lastPathComponent,
            path: destination.path,
            contentId: contentId,
            relativePath: sourceURL.lastPathComponent,
            fileSize: Int64(values.fileSize ?? 0),
            lastModifiedTimestamp: Int64(sourceModifiedAt.timeIntervalSince1970 * 1000)
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

/// Startup orphan sweep (Android `MainViewModel.sweepOrphanedCache` parity,
/// temp-only): removes crash-orphaned `tmp/ExternalOpen/<requestId>/`
/// staging directories older than 1 hour (Android's
/// `deleteStaleTemporaryBookDirs(1h)` threshold) and PDF share/save staging
/// copies older than 24h (Android `AndroidShareArtifactManager`
/// `DEFAULT_TTL_MILLIS`; the share sheet / document picker has no completion
/// callback, so copies are retained with a bounded TTL instead of immediate
/// deletion).
/// Library storage (`Imports/`, `LocalFolders/`) is never touched: those
/// files ARE the library, and Android only sweeps cache/tmp patterns too.
/// The pending-external-removal drain already runs in Kotlin startup state
/// (`loadPendingIosExternalFileRemoval`), mirroring Android's
/// `cleanupPendingExternalFileRemovals`.
private func sweepStaleTemporaryFiles(maxAge: TimeInterval = 3600) {
    let fileManager = FileManager.default
    let cutoff = Date().addingTimeInterval(-maxAge)
    let shareCutoff = Date().addingTimeInterval(-86400)
    func removeIfStale(_ url: URL, cutoff: Date = Date().addingTimeInterval(-3600)) {
        guard let modified = try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate,
              modified < cutoff else { return }
        try? fileManager.removeItem(at: url)
    }
    let tempRoot = fileManager.temporaryDirectory
    // External-open staging from crashed or expired share intents. No request
    // is in flight at cold start, so any entry older than the cutoff is an
    // orphan; content-hashed library copies live under Application Support.
    let externalOpen = tempRoot.appendingPathComponent("ExternalOpen", isDirectory: true)
    if let entries = try? fileManager.contentsOfDirectory(
        at: externalOpen,
        includingPropertiesForKeys: [.contentModificationDateKey],
        options: [.skipsHiddenFiles]
    ) {
        for entry in entries { removeIfStale(entry, cutoff: cutoff) }
    }
    // Leftover nested staging dirs from an earlier layout
    // (`tmp/shared_files/share-<UUID>/`); flat staging replaced them because
    // `UIActivityViewController` item loading fails for the nested path.
    let shareRoot = tempRoot.appendingPathComponent("shared_files", isDirectory: true)
    if let entries = try? fileManager.contentsOfDirectory(
        at: shareRoot,
        includingPropertiesForKeys: [.contentModificationDateKey],
        options: [.skipsHiddenFiles]
    ) {
        for entry in entries where entry.lastPathComponent.hasPrefix("share-") {
            removeIfStale(entry, cutoff: shareCutoff)
        }
    }
    // One-shot PDF share/save copies (Kotlin `IosShareArtifactManager`): flat
    // `tmp/<base>[_annotated]_NNNN.pdf` plus legacy `tmp/reader-export-*`.
    // The share sheet consumes them immediately, so survivors are crash leftovers.
    // Match the 4-digit suffix without NSRegularExpression: `<name>_NNNN.pdf`.
    func isStagedPdfCopy(_ name: String) -> Bool {
        guard name.hasSuffix(".pdf") else { return false }
        if name.hasPrefix("reader-export-") { return true }
        let stem = name.dropLast(4)
        guard stem.count > 6 else { return false }
        let suffix = stem.suffix(5)
        guard suffix.first == "_" else { return false }
        return suffix.dropFirst().allSatisfy { $0.isNumber }
    }
    if let entries = try? fileManager.contentsOfDirectory(
        at: tempRoot,
        includingPropertiesForKeys: [.contentModificationDateKey],
        options: [.skipsHiddenFiles]
    ) {
        for entry in entries where isStagedPdfCopy(entry.lastPathComponent) {
            removeIfStale(entry, cutoff: shareCutoff)
        }
    }
}

private struct ReaderComposeHost: UIViewControllerRepresentable {
    let bridge: ReaderIosBridge
    @Binding var isSystemUiHidden: Bool
    let onImportBooks: () -> Void
    let onImportAudiobookFile: () -> Void
    let onImportAudiobookMultiple: () -> Void
    let onImportAudiobookFolder: () -> Void
    let onImportFolder: () -> Void
    let onRefreshFolders: () -> Void
    let onImportFonts: () -> Void
    let onImportCover: () -> Void
    let onRemoveFolder: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let composeController = ReaderIosAppKt.readerComposeViewController(
            bridge: bridge,
            onImportBooks: onImportBooks,
            onImportAudiobookFile: onImportAudiobookFile,
            onImportAudiobookMultiple: onImportAudiobookMultiple,
            onImportAudiobookFolder: onImportAudiobookFolder,
            onImportFolder: onImportFolder,
            onRefreshFolders: onRefreshFolders,
            onImportFonts: onImportFonts,
            onImportCover: onImportCover,
            onRemoveFolder: onRemoveFolder
        )
        let hostController = ReaderStatusBarHostController(content: composeController)
        bridge.setSystemUiHandler { statusHidden, navigationHidden, lightContent, backgroundArgb in
            DispatchQueue.main.async {
                isSystemUiHidden = statusHidden.boolValue
            }
            hostController.updateSystemUi(
                statusHidden: statusHidden.boolValue,
                navigationHidden: navigationHidden.boolValue,
                lightContent: lightContent.boolValue,
                backgroundArgb: backgroundArgb.int64Value
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
    private var readerOrientationMode: Int32 = 0
    private var pencilInteraction: UIPencilInteraction?

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
        IosPencilShortcutKt.resetIosPencilEraserOverride()
        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        // The Compose window is always edge-to-edge like Android's
        // enableEdgeToEdge: screens paint their own backgrounds beneath the
        // transparent system bars instead of the host insetting the content.
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        contentController.didMove(toParent: self)

        // UIPencilInteraction is the public UIKit surface for Pencil side
        // gestures. Installing it on the stable host (instead of the Compose
        // view) keeps it alive across reader navigation and split panes.
        let interaction = UIPencilInteraction()
        interaction.delegate = self
        view.addInteraction(interaction)
        pencilInteraction = interaction

        // SwiftUI resolves the scene's status bar appearance before this child
        // is attached, so the first frame renders without any bar content until
        // an explicit appearance update is requested.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }

    override var prefersStatusBarHidden: Bool { hidesStatusBar }
    override var prefersHomeIndicatorAutoHidden: Bool { hidesHomeIndicator }
    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }

    // Android parity: the shared bridge always publishes an explicit style —
    // the app theme drives it on home/library and reader themes drive it while
    // reading — so the bar icons always contrast with the surface beneath.
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

    func updateSystemUi(
        statusHidden: Bool,
        navigationHidden: Bool,
        lightContent: Bool,
        backgroundArgb: Int64
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            hidesStatusBar = statusHidden
            hidesHomeIndicator = navigationHidden
            usesLightStatusBarContent = lightContent
            let bits = UInt64(bitPattern: backgroundArgb)
            let red = CGFloat((bits >> 16) & 0xFF) / 255
            let green = CGFloat((bits >> 8) & 0xFF) / 255
            let blue = CGFloat(bits & 0xFF) / 255
            let alpha = CGFloat((bits >> 24) & 0xFF) / 255
            let themeColor = UIColor(red: red, green: green, blue: blue, alpha: alpha)
            view.backgroundColor = themeColor
            view.window?.backgroundColor = themeColor
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }
}

extension ReaderStatusBarHostController: UIPencilInteractionDelegate {
    func pencilInteractionDidTap(_ interaction: UIPencilInteraction) {
        guard UIPencilInteraction.preferredTapAction == .switchEraser else { return }
        _ = IosPencilShortcutKt.toggleIosPencilEraserOverride()
    }

    @available(iOS 17.5, *)
    func pencilInteraction(
        _ interaction: UIPencilInteraction,
        didReceiveSqueeze squeeze: UIPencilInteraction.Squeeze
    ) {
        guard squeeze.phase == .ended,
              UIPencilInteraction.preferredSqueezeAction == .switchEraser else { return }
        _ = IosPencilShortcutKt.toggleIosPencilEraserOverride()
    }
}

#Preview {
    ContentView()
}
