package com.aryan.reader.shared.reader

import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import com.aryan.reader.shared.ios.IosDiagnosticLogStore

internal actual val SharedReaderDiagnosticsEnabled: Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(SharedReaderDiagnosticsProperty)

// Android benchmark: Enabled || Log.isLoggable(tag, DEBUG) — opt-in per tag.
// No Log.isLoggable equivalent on iOS; gate purely on the shared enabled flag.
internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean =
    SharedReaderDiagnosticsEnabled

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    IosDiagnosticLogStore.record(tag, message)
    NSLog("[%@] %@", tag, message)
}
