package com.aryan.reader.shared.reader

internal const val SharedReaderDiagnosticsProperty = "episteme.desktop.diagnostics"
internal const val SharedReaderDiagnosticsTagsProperty = "episteme.desktop.diagnostics.tags"
internal const val SharedEpubCutoffDiagnosticsTag = "EpistemeEpubCutoff"

internal expect val SharedReaderDiagnosticsEnabled: Boolean
internal expect fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean
internal expect fun writeSharedReaderDiagnostic(tag: String, message: String)

internal inline fun logSharedReaderDiagnostic(tag: String, message: () -> String) {
    if (SharedReaderDiagnosticsEnabled && isSharedReaderDiagnosticTagEnabled(tag)) {
        writeSharedReaderDiagnostic(tag, message())
    }
}
