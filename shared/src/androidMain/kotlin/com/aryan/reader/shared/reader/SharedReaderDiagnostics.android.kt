package com.aryan.reader.shared.reader

import android.util.Log

internal actual val SharedReaderDiagnosticsEnabled: Boolean = true

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean {
    return tag == SharedEpubCutoffDiagnosticsTag ||
        runCatching { Log.isLoggable(tag, Log.DEBUG) }.getOrDefault(false)
}

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    Log.d(tag, message)
}
