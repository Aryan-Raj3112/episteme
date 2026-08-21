package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedDiagnosticLogBuffer
import com.aryan.reader.shared.currentTimestamp
import platform.Foundation.NSLock

/**
 * In-process diagnostics retained for the iOS "Export logs" action.
 *
 * iOS does not provide an app-readable equivalent of Android's `logcat -d`.
 * Keeping a bounded, app-owned stream gives support a useful recent trace
 * without requesting private logging entitlements or retaining unbounded
 * reader output in memory.
 */
internal object IosDiagnosticLogStore {
    private val buffer = SharedDiagnosticLogBuffer()
    private val lock = NSLock()

    fun record(tag: String, message: String) {
        lock.lock()
        try {
            buffer.append("${currentTimestamp()} [$tag] $message")
        } finally {
            lock.unlock()
        }
    }

    fun snapshot(): List<String> {
        lock.lock()
        return try {
            buffer.snapshot()
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            buffer.clear()
        } finally {
            lock.unlock()
        }
    }
}
