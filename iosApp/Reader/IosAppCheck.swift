//
//  IosAppCheck.swift
//  Reader
//
//  Firebase App Check wiring: App Attest in release, debug provider in debug.
//  Tokens are pushed into the shared Kotlin bridge (which enforces freshness)
//  on every auth publish/refresh; the worker client reads the cached value.
//  A nil token simply omits the header (server logs the miss while
//  enforcement is off).
//

#if canImport(FirebaseAppCheck)
import Foundation
import ReaderShared
import FirebaseAppCheck

enum IosAppCheck {
    static func install() {
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
    }

    static func token() async -> String? {
        do {
            return try await AppCheck.appCheck().token(forcingRefresh: false).token
        } catch {
            return nil
        }
    }

    /// Fetch a fresh token and publish it to the Kotlin bridge. Runs on the
    /// caller's actor; safe to invoke from the existing @MainActor refresh
    /// continuations.
    static func refresh(bridge: ReaderIosBridge) async {
        bridge.updateAppCheckToken(token: await IosAppCheck.token())
    }
}
#endif
