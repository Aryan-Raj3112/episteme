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

@main
struct ReaderApp: App {
    init() {
        // P0 #1 background execution parity (Android WorkManager -> iOS
        // BGTaskScheduler, one-shot only, never periodic). Handlers are set
        // by ContentView once the account/StoreKit controllers exist.
        IosBackgroundSync.register()
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
