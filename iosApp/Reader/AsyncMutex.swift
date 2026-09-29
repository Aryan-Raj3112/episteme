//
//  AsyncMutex.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import Foundation

/// Minimal async mutual exclusion.
///
/// `NSLock` cannot be held across an `await` (the continuation can resume on a
/// different thread, and holding a lock there risks deadlock), and this codebase
/// has no dependency that provides an actor-based lock. Passes that touch
/// shared mutable state across suspension points — the CloudKit zone shadow,
/// for example — need real serialization rather than "the flag was false when
/// I checked".
///
/// Deliberately not reentrant. Callers must not call back into the same mutex
/// from inside `withLock`.
actor AsyncMutex {
    private var isLocked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func lock() async {
        if !isLocked {
            isLocked = true
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func unlock() {
        if waiters.isEmpty {
            isLocked = false
        } else {
            // Hand ownership straight to the next waiter. `isLocked` stays true
            // so no one else can barge in between the resume and that waiter
            // finishing.
            waiters.removeFirst().resume()
        }
    }

    @discardableResult
    func withLock<T>(_ body: () async throws -> T) async rethrows -> T {
        await lock()
        do {
            let result = try await body()
            unlock()
            return result
        } catch {
            unlock()
            throw error
        }
    }
}
