import UIKit
import os

/// CloudKit silent-push wakeups for library sync.
///
/// `CloudKitLibraryTransport` already creates a `CKRecordZoneSubscription` with
/// `shouldSendContentAvailable = true`, and `ReaderInfo.plist` already declares
/// the `remote-notification` background mode. What was missing was the app side:
/// nothing ever called `registerForRemoteNotifications()`, so iOS never gave us
/// a device token and CloudKit had no way to wake the app. The subscription was
/// inert and sync only ran on foreground resume or `BGTaskScheduler`.
///
/// This is deliberately an *accelerator*, never a dependency. CloudKit can
/// decline, coalesce, or throttle a notification, and the app can be launched
/// from a notification long before the sync controllers exist. So:
///
/// - Nothing here is required for sync to work. If registration fails, or the
///   capability is missing, or the entitlement is wrong, sync still runs on
///   foreground resume, on push-triggered BGAppRefreshTask, and on any library
///   mutation. A silent push just makes it start sooner.
/// - Every failure path is logged and swallowed. `registerForRemoteNotifications`
///   is fire-and-forget and has no throwing form; a failed registration is
///   reported to `didFailToRegisterForRemoteNotificationsWithError` and ignored.
/// - The completion handler is always invoked exactly once, well inside the
///   ~30s the system allows, so a slow sync can never get the app throttled or
///   killed for missing its deadline.
/// - The handler is a deferred closure, mirroring `IosBackgroundSync`. A push
///   that launches the app fires before `ContentView` has built
///   `LocalAccountController`; rather than block the launch we complete
///   immediately and let the normal BGTask/foreground path pick the work up.
///
/// Push cannot be exercised on a simulator (APNs is not delivered there).
/// `xcrun simctl push <udid> com.aryan.episteme payload.json` with
/// `{"aps":{"content-available":1}}` does drive this code path, which verifies
/// registration, delegate dispatch and handler wiring - but not real CloudKit
/// delivery, which needs a physical device on the same iCloud account.
enum IosPushNotifications {
    private static let logger = Logger(subsystem: "com.aryan.reader", category: "CloudKitSync")

    /// Leaves headroom under the ~30s silent-push deadline so a slow or wedged
    /// sync reports honestly instead of being killed for overrunning.
    static let handlerTimeoutSeconds: Double = 20

    static var isRegistered = false

    /// Set by ContentView once LocalAccountController exists, same pattern as
    /// `IosBackgroundSync.refreshHandler`. Runs a pull-only pass, which is
    /// exactly what the BGTaskScheduler refresh handler already runs.
    static var pullHandler: (() async -> Void)?

    static func log(_ message: String) {
        logger.info("\(message, privacy: .public)")
    }

    static func register() {
        // Deliberately not gated on anything. A device with no iCloud account or
        // no push entitlement simply fails registration later, and sync carries
        // on unchanged.
        logger.info("cloudkit_push.register_requested")
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Handle one silent notification. Always calls `completionHandler` exactly
    /// once, with `.newData` when a pull ran and `.noData`/`.failed` when it
    /// could not.
    static func handleSilentNotification(
        userInfo: [AnyHashable: Any],
        completion: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        // CloudKit tags its notifications with this key. Anything else (a future
        // FCM message, a VoIP push) is not ours to act on.
        let isCloudKit = userInfo["CKNotificationSubscription"] != nil
        logger.info("cloudkit_push.received isCloudKit=\(isCloudKit, privacy: .public) keys=\(userInfo.keys.count, privacy: .public)")
        guard isCloudKit, let handler = pullHandler else {
            // No handler yet means the controllers are still being built, which
            // happens on a cold launch from a push. BGTaskScheduler and the next
            // foreground resume will sync anyway, so there is nothing to report.
            completion(.noData)
            return
        }
        // Guarded so a pull that finishes after the deadline still cannot call
        // the completion handler twice.
        var finished = false
        let finish: (UIBackgroundFetchResult) -> Void = { result in
            guard !finished else { return }
            finished = true
            completion(result)
        }
        Task {
            // Race the pull against the deadline. The deadline task returns
            // `true` only if the sleep actually elapsed; being cancelled means
            // the pull won the race.
            let deadline = Task { () -> Bool in
                do {
                    try await Task.sleep(nanoseconds: UInt64(handlerTimeoutSeconds * 1_000_000_000))
                    return true
                } catch {
                    return false
                }
            }
            Task {
                await handler()
                deadline.cancel()
            }
            if await deadline.value {
                logger.error("cloudkit_push.pull_timed_out seconds=\(handlerTimeoutSeconds, privacy: .public)")
                finish(.failed)
            } else {
                logger.info("cloudkit_push.pull_completed")
                finish(.newData)
            }
        }
    }

    #if DEBUG
    /// Launch argument that drives the push path without APNs. APNs is not
    /// delivered to simulators at all, and `xcrun simctl push` is rejected by
    /// current simulator runtimes even for a valid alert payload, so this is
    /// the only way to exercise the dispatch chain here:
    /// `handleSilentNotification` -> `pullHandler` -> real pull -> completion.
    /// Compiled out of Release builds.
    static let simulatePushLaunchArgument = "-episteme.simulate-cloudkit-push"

    /// Exercises the full chain *after* the sync controllers exist, which is
    /// the steady-state case (app already running, CloudKit wakes it).
    static func simulateCloudKitPushAfterStartup(delay: Double = 4) {
        Task {
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            logger.info("cloudkit_push.simulate_started")
            handleSilentNotification(userInfo: cloudKitSimulatedPayload) { result in
                logger.info("cloudkit_push.simulate_finished result=\(result.debugName, privacy: .public)")
            }
        }
    }

    /// Exercises the cold-launch case, where the notification arrives before
    /// `ContentView` has installed `pullHandler`. Must complete with `.noData`
    /// rather than hang or block the launch.
    static func simulateColdLaunchPush() {
        logger.info("cloudkit_push.simulate_cold_started handlerInstalled=\(pullHandler != nil, privacy: .public)")
        handleSilentNotification(userInfo: cloudKitSimulatedPayload) { result in
            logger.info("cloudkit_push.simulate_cold_finished result=\(result.debugName, privacy: .public)")
        }
    }

    private static var cloudKitSimulatedPayload: [AnyHashable: Any] {
        [
            "CKNotificationSubscription": [
                "subscriptionID": "debug-simulated-LibraryZone",
                "notificationID": UUID().uuidString,
            ]
        ]
    }
    #endif
}

/// `UIBackgroundFetchResult` is an enum with no CustomStringConvertible, so
/// it cannot be interpolated into an OSLog message directly.
/// `UIBackgroundFetchResult` has no CustomStringConvertible, so it cannot be
/// interpolated into an OSLog message directly.
private extension UIBackgroundFetchResult {
    var debugName: String {
        switch self {
        case .newData: return "newData"
        case .noData: return "noData"
        case .failed: return "failed"
        @unknown default: return "unknown"
        }
    }
}

/// App delegate that owns remote-notification registration.
///
/// Attached to `ReaderApp` via `@UIApplicationDelegateAdaptor`. The app uses
/// the SwiftUI lifecycle, so without this adaptor there is no
/// `UIApplicationDelegate` at all and the push callbacks above would have
/// nowhere to land.
final class ReaderAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosPushNotifications.register()
        return true
    }

    /// CloudKit consumes the token itself once it is registered; the app never
    /// sends anything to APNs, so the token is only logged to confirm the
    /// capability and entitlement are actually working on this build.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        IosPushNotifications.isRegistered = true
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        IosPushNotifications.log("cloudkit_push.registered token=\(token.prefix(16))… len=\(deviceToken.count)")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        IosPushNotifications.isRegistered = false
        // Expected on the simulator and on any build whose provisioning profile
        // lacks the push capability. Never fatal: sync continues on foreground
        // resume and BGTaskScheduler.
        IosPushNotifications.log("cloudkit_push.register_failed error=\((error as NSError).code) \(error.localizedDescription)")
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        IosPushNotifications.handleSilentNotification(
            userInfo: userInfo,
            completion: completionHandler
        )
    }
}
