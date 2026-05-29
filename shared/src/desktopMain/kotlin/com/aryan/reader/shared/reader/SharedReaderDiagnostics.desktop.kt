package com.aryan.reader.shared.reader

private const val SharedReaderDiagnosticsEnv = "EPISTEME_DESKTOP_DIAGNOSTICS"
private const val SharedReaderDiagnosticsTagsEnv = "EPISTEME_DESKTOP_DIAGNOSTICS_TAGS"

private val SharedReaderDiagnosticTags: Set<String> =
    listOfNotNull(
        System.getProperty(SharedReaderDiagnosticsTagsProperty),
        System.getenv(SharedReaderDiagnosticsTagsEnv)
    )
        .joinToString(" ")
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
        System.getenv(SharedReaderDiagnosticsEnv)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true ||
        SharedReaderDiagnosticTags.isNotEmpty()

internal actual fun isSharedReaderDiagnosticTagEnabled(tag: String): Boolean {
    if (SharedReaderDiagnosticTags.isEmpty()) return true
    return "*" in SharedReaderDiagnosticTags || tag.lowercase() in SharedReaderDiagnosticTags
}

internal actual fun writeSharedReaderDiagnostic(tag: String, message: String) {
    println("$tag $message")
}
