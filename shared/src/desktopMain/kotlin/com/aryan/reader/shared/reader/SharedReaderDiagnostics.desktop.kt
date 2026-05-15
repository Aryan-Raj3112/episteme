package com.aryan.reader.shared.reader

private val SharedReaderDiagnosticTags: Set<String> =
    System.getProperty(SharedReaderDiagnosticsTagsProperty)
        .orEmpty()
        .split(',', ';', ' ', '\t', '\n')
        .mapNotNull { rawTag ->
            rawTag.trim()
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        }
        .toSet()

internal actual val SharedReaderDiagnosticsEnabled: Boolean =
    System.getProperty(SharedReaderDiagnosticsProperty)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true ||
        SharedReaderDiagnosticTags.isNotEmpty()

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean {
    if (SharedReaderDiagnosticTags.isEmpty()) return true
    return "*" in SharedReaderDiagnosticTags || tag.lowercase() in SharedReaderDiagnosticTags
}
