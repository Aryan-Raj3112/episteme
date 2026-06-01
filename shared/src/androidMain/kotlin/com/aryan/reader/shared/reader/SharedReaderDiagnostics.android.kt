package com.aryan.reader.shared.reader

import android.util.Log
import com.aryan.reader.shared.BuildConfig

internal actual val SharedReaderDiagnosticsEnabled: Boolean = BuildConfig.DEBUG

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean {
    if (!BuildConfig.DEBUG) return false
    return tag == SharedEpubCutoffDiagnosticsTag ||
        runCatching { Log.isLoggable(tag, Log.DEBUG) }.getOrDefault(false)
}

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(tag, message)
}
