import BackgroundTasks
import UIKit

/// iOS background execution parity.
///
/// Android is the absolute benchmark and is NOT changed. Android uses one-shot
/// WorkManager requests (never periodic) with CONNECTED constraints and
/// EXPONENTIAL backoff:
/// - CloudFolderSyncWorker: CONNECTED + 30s
/// - CloudBookDeleteWorker: CONNECTED + 10s
/// - Head listener: foreground-only, 500ms debounce, background detaches
/// - Purchase reconciliation: foreground-only (no Worker)
/// - No PeriodicWork anywhere
///
/// iOS mirrors that with one-shot BGAppRefreshTask (short retry / head
/// re-check / entitlement re-query) and BGProcessingTask (folder index,
/// upload drain, GC, cleanup). Tasks are re-scheduled only when work remains
/// after a pass — never on a timer — matching Android's REPLACE/KEEP
/// coalescing. In-flight cloud sync also gets a beginBackgroundTask grace
/// period when the app backgrounds, mirroring WorkManager continuing after
/// the UI goes away. The durable retry math lives in shared
/// `SharedBackgroundSyncPolicy` / `IosBackgroundSyncScheduler`; this file
/// only owns BGTaskScheduler submit/cancel + grace bookkeeping.
enum IosBackgroundSync {
    static let refreshTaskId = "com.aryan.reader.sync.refresh"
    static let processingTaskId = "com.aryan.reader.sync.processing"

    private static var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid

    /// Deferred handlers set by ContentView once LocalAccountController /
    /// LocalStoreKitController exist. Registration itself must happen in
    /// ReaderApp.init (before launch finishes); handlers can arrive later.
    static var refreshHandler: (() async -> Void)?
    static var processingHandler: (() async -> Void)?

    /// Call once from ReaderApp.init (BGTaskScheduler registration must happen
    /// before the app finishes launching).
    static func register(
        onRefresh: (() async -> Void)? = nil,
        onProcessing: (() async -> Void)? = nil
    ) {
        if let onRefresh { refreshHandler = onRefresh }
        if let onProcessing { processingHandler = onProcessing }
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: refreshTaskId,
            using: nil
        ) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            // One-shot: re-schedule before running so a crash/suspend still
            // leaves a retry queued, mirroring WorkManager's durable queue.
            scheduleRefresh()
            Task {
                await refreshHandler?()
                refresh.setTaskCompleted(success: true)
            }
            refresh.expirationHandler = {
                refresh.setTaskCompleted(success: false)
            }
        }
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: processingTaskId,
            using: nil
        ) { task in
            guard let processing = task as? BGProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            scheduleProcessing()
            Task {
                await processingHandler?()
                processing.setTaskCompleted(success: true)
            }
            processing.expirationHandler = {
                processing.setTaskCompleted(success: false)
            }
        }
    }

    /// One-shot refresh: outbox retry, head re-check, entitlement re-query.
    /// Mirrors CloudFolderSyncWorker's CONNECTED + 30s backoff window.
    static func scheduleRefresh(earliestBeginSeconds: TimeInterval = 5) {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: earliestBeginSeconds)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// One-shot processing: folder index pass, upload drain, GC, cleanup.
    /// Mirrors the explicit REPLACE triggers (manual refresh, selection
    /// change, foreground resume) — never periodic.
    static func scheduleProcessing(earliestBeginSeconds: TimeInterval = 30, requiresNetwork: Bool = true) {
        let request = BGProcessingTaskRequest(identifier: processingTaskId)
        request.requiresNetworkConnectivity = requiresNetwork
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: earliestBeginSeconds)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancelAll() {
        BGTaskScheduler.shared.cancelAllTaskRequests()
    }

    /// Grace period for in-flight cloud sync when backgrounding, mirroring
    /// WorkManager continuing after the activity stops. The system grants
    /// ~30s; the sync task must be idempotent because expiration can cut it
    /// off mid-pass (Android's resetRunningOutbox covers the same case).
    static func beginGrace() {
        endGrace()
        backgroundTaskId = UIApplication.shared.beginBackgroundTask(withName: "reader.cloud-sync-grace") {
            endGrace()
        }
    }

    static func endGrace() {
        if backgroundTaskId != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTaskId)
            backgroundTaskId = .invalid
        }
    }
}
