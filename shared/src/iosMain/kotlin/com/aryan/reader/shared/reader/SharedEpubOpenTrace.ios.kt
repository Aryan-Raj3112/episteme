package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ios.IosDiagnosticLogStore

// Android benchmark: Log.isLoggable(TAG, DEBUG) — opt-in, default off.
// Gate on the same diagnostics flag so release builds pay no trace-string cost.
internal actual fun isSharedEpubOpenTraceEnabled(): Boolean = SharedReaderDiagnosticsEnabled

internal actual fun writeSharedEpubOpenTrace(message: String) {
    IosDiagnosticLogStore.record(SharedEpubOpenTraceTag, message)
    println("[${SharedEpubOpenTraceTag}] $message")
}
