package com.aryan.reader.desktop

internal const val DesktopDiagnosticsProperty = "episteme.desktop.diagnostics"

internal val DesktopDiagnosticsEnabled: Boolean =
    desktopDiagnosticsFlag(System.getProperty(DesktopDiagnosticsProperty))

internal fun desktopDiagnosticsFlag(rawValue: String?): Boolean {
    return rawValue?.trim()?.equals("true", ignoreCase = true) == true
}

internal inline fun logDesktopDiagnostic(tag: String, message: () -> String) {
    if (DesktopDiagnosticsEnabled) {
        println("$tag ${message()}")
    }
}
