package com.aryan.reader.desktop

internal const val DesktopDiagnosticsProperty = "episteme.desktop.diagnostics"
private const val DesktopDiagnosticsTagsProperty = "episteme.desktop.diagnostics.tags"
private const val DesktopDiagnosticsEnv = "EPISTEME_DESKTOP_DIAGNOSTICS"
private const val DesktopDiagnosticsTagsEnv = "EPISTEME_DESKTOP_DIAGNOSTICS_TAGS"

private val DesktopDiagnosticTags: Set<String> =
    listOfNotNull(
        System.getProperty(DesktopDiagnosticsTagsProperty),
        System.getenv(DesktopDiagnosticsTagsEnv)
    )
        .joinToString(" ")
        .split(',', ';', ' ', '\t', '\n')
        .mapNotNull { rawTag ->
            rawTag.trim()
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        }
        .toSet()

internal val DesktopDiagnosticsEnabled: Boolean =
    desktopDiagnosticsFlag(System.getProperty(DesktopDiagnosticsProperty)) ||
        desktopDiagnosticsFlag(System.getenv(DesktopDiagnosticsEnv)) ||
        DesktopDiagnosticTags.isNotEmpty()

internal fun desktopDiagnosticsFlag(rawValue: String?): Boolean {
    return rawValue?.trim()?.equals("true", ignoreCase = true) == true
}

private fun isDesktopDiagnosticTagEnabled(tag: String): Boolean {
    if (DesktopDiagnosticTags.isEmpty()) return true
    return "*" in DesktopDiagnosticTags || tag.lowercase() in DesktopDiagnosticTags
}

internal fun logDesktopDiagnostic(tag: String, message: () -> String) {
    if (DesktopDiagnosticsEnabled && isDesktopDiagnosticTagEnabled(tag)) {
        println("$tag ${message()}")
    }
}
