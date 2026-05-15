package com.aryan.reader.shared.reader

internal const val SharedReaderDiagnosticsProperty = "episteme.desktop.diagnostics"
internal const val SharedReaderDiagnosticsTagsProperty = "episteme.desktop.diagnostics.tags"

internal expect val SharedReaderDiagnosticsEnabled: Boolean
internal expect fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean

internal inline fun logSharedReaderDiagnostic(tag: String, message: () -> String) {
    if (SharedReaderDiagnosticsEnabled && isSharedReaderDiagnosticTagEnabled(tag)) {
        println("$tag ${message()}")
    }
}
