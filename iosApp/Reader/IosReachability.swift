//
//  IosReachability.swift
//  Reader
//
//  Live network reachability for the shared Kotlin AI gates. Android parity
//  (`areReaderAiFeaturesEnabled` offline leg): the shared adapter hides AI
//  entries while offline. NWPathMonitor pushes into the Kotlin bridge; the
//  bridge defaults to reachable until the first update lands.
//

import Foundation
import Network
import ReaderShared

enum IosReachability {
    private static let monitor = NWPathMonitor()
    private static let queue = DispatchQueue(label: "com.aryan.reader.reachability")
    private static var started = false

    /// Idempotent: ContentView's `.task` may re-run on identity refreshes,
    /// but the monitor itself starts once for the process lifetime.
    static func start(bridge: ReaderIosBridge) {
        guard !started else { return }
        started = true
        monitor.pathUpdateHandler = { path in
            bridge.updateReaderAiNetworkAvailable(available: path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }
}
