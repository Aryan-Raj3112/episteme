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
