package com.aryan.reader.shared.reader

internal actual val SharedReaderDiagnosticsEnabled: Boolean = false

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean = false

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) = Unit
