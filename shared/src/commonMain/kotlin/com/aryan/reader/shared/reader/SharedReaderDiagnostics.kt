package com.aryan.reader.shared.reader

internal const val SharedReaderDiagnosticsProperty = "episteme.desktop.diagnostics"
internal const val SharedReaderDiagnosticsTagsProperty = "episteme.desktop.diagnostics.tags"
internal const val SharedEpubCutoffDiagnosticsTag = "EpistemeEpubCutoff"
internal const val TxtFormatTraceTag = "TxtFormatTrace"

/**
 * Common tag for TTS-start diagnostics (device, cloud, Listen). Unfiltered:
 * filter the device console for this tag and send the lines when TTS start
 * is slow or frozen. Keep volume to session milestones (start, session
 * activation, first speak, first audio) — never per-chunk spam.
 */
const val ReaderTtsStartTag = "ReaderTtsStart"

internal expect val SharedReaderDiagnosticsEnabled: Boolean
internal expect fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean
internal expect fun writeSharedReaderDiagnostic(tag: String, message: String)

internal inline fun logSharedReaderDiagnostic(tag: String, message: () -> String) {
    if (SharedReaderDiagnosticsEnabled && isSharedReaderDiagnosticTagEnabled(tag)) {
        writeSharedReaderDiagnostic(tag, message())
    }
}
