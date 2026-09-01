package com.aryan.reader.shared.reader

import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import com.aryan.reader.shared.ios.IosDiagnosticLogStore

internal actual val SharedReaderDiagnosticsEnabled: Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(SharedReaderDiagnosticsProperty)

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean = true

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    IosDiagnosticLogStore.record(tag, message)
    NSLog("[%@] %@", tag, message)
}
