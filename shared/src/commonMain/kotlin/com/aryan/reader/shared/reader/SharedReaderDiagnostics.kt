package com.aryan.reader.shared.reader

internal const val SharedReaderDiagnosticsProperty = "episteme.desktop.diagnostics"

internal expect val SharedReaderDiagnosticsEnabled: Boolean

internal inline fun logSharedReaderDiagnostic(tag: String, message: () -> String) {
    if (SharedReaderDiagnosticsEnabled) {
        println("$tag ${message()}")
    }
}
