@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ios.IosDiagnosticLogStore
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import platform.Foundation.NSUserDefaults
import platform.posix.fprintf
import platform.posix.stderr

internal actual val SharedReaderDiagnosticsEnabled: Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(SharedReaderDiagnosticsProperty)

// Android benchmark: Enabled || Log.isLoggable(tag, DEBUG) — opt-in per tag.
// No Log.isLoggable equivalent on iOS; gate purely on the shared enabled flag.
internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean =
    SharedReaderDiagnosticsEnabled

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    IosDiagnosticLogStore.record(tag, message)
    // NSLog is a C variadic: Kotlin strings are NOT converted to NSString in C
    // varargs, so "%@" dereferences garbage and crashes (EXC_BAD_ACCESS).
    // Emit the preformatted line with "%s", which only needs a C string.
    memScoped {
        fprintf(stderr, "%s\n", "[$tag] $message".cstr.ptr)
    }
}
