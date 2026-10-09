//
//  ReaderApp.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import SwiftUI

#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseAppCheck)
import FirebaseAppCheck
#endif

@main
struct ReaderApp: App {
    /// CloudKit silent-push delivery needs a UIApplicationDelegate, which the
    /// SwiftUI lifecycle does not create on its own. Without this adaptor
    /// `registerForRemoteNotifications()` is never called, iOS issues no device
    /// token, and the zone subscription in CloudKitLibraryTransport stays inert
    /// even though `shouldSendContentAvailable` is set and the
    /// `remote-notification` background mode is declared.
    ///
    /// Sync does not depend on any of this: registration failure is logged and
    /// ignored, and library sync still runs on foreground resume, on
    /// BGTaskScheduler, and on every library mutation.
    @UIApplicationDelegateAdaptor(ReaderAppDelegate.self) private var appDelegate

    init() {
        // P0 #1 background execution parity (Android WorkManager -> iOS
        // BGTaskScheduler, one-shot only, never periodic). Handlers are set
        // by ContentView once the account/StoreKit controllers exist.
        IosBackgroundSync.register()
        // Android selection-menu parity: the EPUB reader draws its own
        // selection menu inside the page, so WebKit's native edit menu is
        // suppressed app-wide (see IosEpubEditMenuSuppression).
        IosEpubEditMenuSuppression.install()
#if DEBUG
        // UI tests launch a fresh logical library without deleting user files.
        // Keep this debug-only so production launches never clear persisted
        // preferences or reader sessions.
        if ProcessInfo.processInfo.arguments.contains("-episteme.ui-testing-reset-state"),
           let bundleIdentifier = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleIdentifier)
            UserDefaults.standard.synchronize()
        }
#endif
#if canImport(FirebaseAppCheck)
        // App Check provider must be set before configure() so the first
        // token request is already attested.
        IosAppCheck.install()
#endif
#if canImport(FirebaseCore)
        if FirebaseApp.app() == nil, FirebaseOptions.defaultOptions() != nil {
            FirebaseApp.configure()
        }
#endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
